package org.destroyermob.mobsstorage.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.network.OpenLabelEditorPayload;
import org.destroyermob.mobsstorage.network.SaveLabelPayload;
import org.destroyermob.mobsstorage.storage.FilterRules;
import org.destroyermob.mobsstorage.storage.LabelData;

public final class StorageLabelScreen extends Screen {
    private static final int CELL = 20;
    private static final int GRID_COLUMNS = 10;
    private static final int GRID_ROWS = 4;

    private final OpenLabelEditorPayload payload;
    private final List<Item> allItems;
    private EditBox search;
    private MultiLineEditBox filters;
    private Button applyButton;
    private ResourceLocation selectedIcon;
    private boolean alwaysShow;
    private int itemOffset;
    private Component validationMessage = CommonComponents.EMPTY;

    public StorageLabelScreen(OpenLabelEditorPayload payload) {
        super(Component.translatable("screen.mobsstorage.label.title"));
        this.payload = payload;
        this.selectedIcon = payload.data().configured() ? payload.data().icon() : null;
        this.alwaysShow = payload.data().alwaysShow();
        this.allItems = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
    }

    @Override
    protected void init() {
        int left = left();
        this.search = addRenderableWidget(new EditBox(
                this.font, left, 26, contentWidth(), 20,
                Component.translatable("screen.mobsstorage.label.search")
        ));
        this.search.setHint(Component.translatable("screen.mobsstorage.label.search"));
        this.search.setResponder(value -> itemOffset = 0);

        int filterY = gridY() + GRID_ROWS * CELL + 18;
        this.filters = addRenderableWidget(new MultiLineEditBox(
                this.font, left, filterY, contentWidth(), 58,
                Component.translatable("screen.mobsstorage.label.filter_hint"),
                Component.translatable("screen.mobsstorage.label.filters")
        ));
        this.filters.setCharacterLimit(4096);
        this.filters.setValue(String.join("\n", payload.data().filters()));
        this.filters.setValueListener(value -> updateValidation());

        int controlsY = filterY + 64;
        addRenderableWidget(CycleButton.onOffBuilder(alwaysShow).create(
                left, controlsY, 130, 20,
                Component.translatable("screen.mobsstorage.label.always_show"),
                (button, value) -> alwaysShow = value
        ));
        this.applyButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.label.apply"), button -> apply()
        ).bounds(left + contentWidth() - 164, controlsY, 80, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.label.cancel"), button -> onClose()
        ).bounds(left + contentWidth() - 80, controlsY, 80, 20).build());
        updateValidation();
    }

    private void apply() {
        if (selectedIcon == null || !validationMessage.equals(CommonComponents.EMPTY)) {
            return;
        }
        List<String> normalized = FilterRules.normalize(filters.getValue());
        PacketDistributor.sendToServer(new SaveLabelPayload(
                payload.pos(), selectedIcon, normalized, payload.data().face(), alwaysShow
        ));
        onClose();
    }

    private void updateValidation() {
        if (selectedIcon == null) {
            validationMessage = Component.translatable("screen.mobsstorage.label.choose_icon");
        } else {
            validationMessage = FilterRules.validateIcon(selectedIcon)
                    .or(() -> FilterRules.validate(FilterRules.normalize(filters == null ? "" : filters.getValue())))
                    .orElse(CommonComponents.EMPTY);
        }
        if (applyButton != null) {
            applyButton.active = validationMessage.equals(CommonComponents.EMPTY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 9, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.label.filters"), left(), gridY() + GRID_ROWS * CELL + 7, 0xD8D8D8, false);
        renderItemGrid(graphics, mouseX, mouseY);
        if (!validationMessage.equals(CommonComponents.EMPTY)) {
            graphics.drawString(font, validationMessage, left(), height - 13, 0xFF7777, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderItemGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Item> items = filteredItems();
        int start = Math.min(itemOffset, Math.max(0, items.size() - 1));
        for (int visible = 0; visible < GRID_COLUMNS * GRID_ROWS; visible++) {
            int index = start + visible;
            int x = left() + (visible % GRID_COLUMNS) * CELL;
            int y = gridY() + (visible / GRID_COLUMNS) * CELL;
            graphics.fill(x, y, x + 18, y + 18, 0xAA171717);
            if (index >= items.size()) {
                continue;
            }
            Item item = items.get(index);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.equals(selectedIcon)) {
                graphics.renderOutline(x, y, 18, 18, 0xFFFFFFFF);
            }
            ItemStack stack = item.getDefaultInstance();
            graphics.renderItem(stack, x + 1, y + 1);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                graphics.renderTooltip(font, List.of(stack.getHoverName(), Component.literal(id.toString())), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideGrid(mouseX, mouseY)) {
            int column = (int) (mouseX - left()) / CELL;
            int row = (int) (mouseY - gridY()) / CELL;
            int index = itemOffset + row * GRID_COLUMNS + column;
            List<Item> items = filteredItems();
            if (index >= 0 && index < items.size()) {
                selectedIcon = BuiltInRegistries.ITEM.getKey(items.get(index));
                updateValidation();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideGrid(mouseX, mouseY)) {
            int max = Math.max(0, filteredItems().size() - GRID_COLUMNS * GRID_ROWS);
            itemOffset = Math.max(0, Math.min(max, itemOffset - (int) Math.signum(scrollY) * GRID_COLUMNS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private List<Item> filteredItems() {
        if (search == null || search.getValue().isBlank()) {
            return allItems;
        }
        String query = search.getValue().toLowerCase(Locale.ROOT);
        List<Item> result = new ArrayList<>();
        for (Item item : allItems) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.toString().contains(query) || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        return mouseX >= left() && mouseX < left() + GRID_COLUMNS * CELL
                && mouseY >= gridY() && mouseY < gridY() + GRID_ROWS * CELL;
    }

    private int contentWidth() {
        return GRID_COLUMNS * CELL;
    }

    private int left() {
        return (width - contentWidth()) / 2;
    }

    private int gridY() {
        return 50;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
