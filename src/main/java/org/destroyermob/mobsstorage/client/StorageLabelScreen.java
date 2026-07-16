package org.destroyermob.mobsstorage.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
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
import org.destroyermob.mobsstorage.storage.LabelDisplayMode;

public final class StorageLabelScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 520;
    private static final int PANEL_GAP = 12;
    private static final int CELL = 24;
    private static final int SUGGESTION_ROW_HEIGHT = 16;
    private static final int SUGGESTION_ROWS = 3;
    private static final int FILTER_ROW_HEIGHT = 20;

    private final OpenLabelEditorPayload payload;
    private final List<Item> allItems;
    private final List<String> allItemIds;
    private final List<String> allItemTags;
    private final List<String> allModFilters;
    private final List<String> filterEntries;
    private final List<ItemStack> contents;
    private EditBox search;
    private EditBox filterInput;
    private EditBox storageName;
    private EditBox priorityInput;
    private Button applyButton;
    private CycleButton<Boolean> alwaysShowButton;
    private CycleButton<Boolean> ejectConflictsButton;
    private ResourceLocation selectedIcon;
    private LabelDisplayMode displayMode;
    private boolean alwaysShow;
    private boolean ejectConflicts;
    private int itemOffset;
    private int suggestionOffset;
    private int filterOffset;
    private Component validationMessage = CommonComponents.EMPTY;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private ResourceLocation hoveredItemId;

    public StorageLabelScreen(OpenLabelEditorPayload payload) {
        super(Component.translatable("screen.mobsstorage.label.title"));
        this.payload = payload;
        this.selectedIcon = payload.data().configured() ? payload.data().icon() : null;
        this.displayMode = payload.data().displayMode();
        this.alwaysShow = payload.data().alwaysShow();
        this.filterEntries = new ArrayList<>(payload.data().filters());
        this.contents = payload.contents();
        this.allItems = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
        this.allItemIds = allItems.stream()
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                .toList();
        this.allItemTags = BuiltInRegistries.ITEM.getTagNames()
                .map(tag -> "#" + tag.location())
                .sorted()
                .toList();
        this.allModFilters = allItems.stream()
                .map(item -> "@" + BuiltInRegistries.ITEM.getKey(item).getNamespace())
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int columnWidth = columnWidth();
        this.storageName = addRenderableWidget(new EditBox(
                this.font, left, 28, columnWidth, 20,
                Component.translatable("screen.mobsstorage.label.storage_name")
        ));
        this.storageName.setMaxLength(48);
        this.storageName.setValue(payload.node().name());
        this.storageName.setHint(Component.translatable("screen.mobsstorage.label.storage_name"));
        this.priorityInput = addRenderableWidget(new EditBox(
                this.font, rightColumnLeft(), 28, columnWidth, 20,
                Component.translatable("screen.mobsstorage.label.priority")
        ));
        this.priorityInput.setMaxLength(5);
        this.priorityInput.setValue(Integer.toString(payload.node().priority()));
        this.priorityInput.setHint(Component.translatable("screen.mobsstorage.label.priority"));
        this.priorityInput.setResponder(value -> updateValidation());
        this.search = addRenderableWidget(new EditBox(
                this.font, left, 52, columnWidth, 20,
                Component.translatable("screen.mobsstorage.label.search")
        ));
        this.search.setHint(Component.translatable("screen.mobsstorage.label.search"));
        this.search.setResponder(value -> itemOffset = 0);

        int inputWidth = Math.max(32, columnWidth - 90);
        this.filterInput = addRenderableWidget(new EditBox(
                this.font, rightColumnLeft(), 74, inputWidth, 20,
                Component.translatable("screen.mobsstorage.label.filter_input")
        ));
        this.filterInput.setMaxLength(256);
        this.filterInput.setHint(Component.translatable("screen.mobsstorage.label.filter_input_hint"));
        this.filterInput.setResponder(value -> {
            suggestionOffset = 0;
            updateValidation();
        });
        addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.label.add"), button -> commitInput()
        ).bounds(rightColumnLeft() + inputWidth + 4, 74, 36, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.label.learn_contents"), button -> learnFromContents()
        ).bounds(rightColumnLeft() + inputWidth + 44, 74, 46, 20).build());

        int footerY = footerY();
        int actionWidth = Math.min(72, Math.max(48, (panelWidth() - 20) / 6));
        int settingArea = panelWidth() - actionWidth * 2 - 16;
        int settingWidth = Math.max(40, settingArea / 3);
        addRenderableWidget(CycleButton.builder(StorageLabelScreen::displayModeName)
                .withValues(LabelDisplayMode.values())
                .withInitialValue(displayMode)
                .create(left, footerY, settingWidth, 20,
                        Component.translatable("screen.mobsstorage.label.display_mode"),
                        (button, value) -> {
                            displayMode = value;
                            if (alwaysShowButton != null) {
                                alwaysShowButton.active = value != LabelDisplayMode.CROSSHAIR;
                            }
                        }));
        this.alwaysShowButton = addRenderableWidget(CycleButton.onOffBuilder(alwaysShow).create(
                left + settingWidth + 4, footerY, settingWidth, 20,
                Component.translatable("screen.mobsstorage.label.always_show"),
                (button, value) -> alwaysShow = value
        ));
        this.alwaysShowButton.active = displayMode != LabelDisplayMode.CROSSHAIR;
        this.ejectConflictsButton = addRenderableWidget(CycleButton.<Boolean>builder(
                        value -> Component.translatable(value
                                ? "screen.mobsstorage.label.conflicts_eject"
                                : "screen.mobsstorage.label.conflicts_keep"))
                .withValues(false, true)
                .withInitialValue(ejectConflicts)
                .create(left + (settingWidth + 4) * 2, footerY, settingWidth, 20,
                        Component.translatable("screen.mobsstorage.label.conflicts"),
                        (button, value) -> ejectConflicts = value));
        this.applyButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.label.apply"), button -> apply()
        ).bounds(left + panelWidth() - actionWidth * 2 - 4, footerY, actionWidth, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.label.cancel"), button -> onClose()
        ).bounds(left + panelWidth() - actionWidth, footerY, actionWidth, 20).build());
        clampOffsets();
        updateValidation();
    }

    private void apply() {
        if (!filterInput.getValue().isBlank() && !commitInput()) {
            return;
        }
        updateValidation();
        if (selectedIcon == null || !validationMessage.equals(CommonComponents.EMPTY)) {
            return;
        }
        PacketDistributor.sendToServer(new SaveLabelPayload(
                payload.pos(), selectedIcon, List.copyOf(filterEntries), payload.data().face(), displayMode,
                storageName.getValue(), parsePriority(), alwaysShow, ejectConflicts
        ));
        onClose();
    }

    private void learnFromContents() {
        for (ItemStack stack : contents) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!filterEntries.contains(itemId) && filterEntries.size() < FilterRules.MAX_FILTERS) {
                filterEntries.add(itemId);
            }
        }
        filterOffset = Math.max(0, filterEntries.size() - visibleFilterRows());
        updateValidation();
    }

    private boolean commitInput() {
        String entry = filterInput.getValue().trim();
        if (entry.isEmpty()) {
            return true;
        }
        Optional<Component> error = FilterRules.validate(List.of(entry));
        if (error.isPresent() || filterEntries.size() >= FilterRules.MAX_FILTERS) {
            updateValidation();
            return false;
        }
        if (!filterEntries.contains(entry)) {
            filterEntries.add(entry);
        }
        filterInput.setValue("");
        filterOffset = Math.max(0, filterEntries.size() - visibleFilterRows());
        updateValidation();
        return true;
    }

    private void addSuggestion(String suggestion) {
        if (filterEntries.size() < FilterRules.MAX_FILTERS && !filterEntries.contains(suggestion)) {
            filterEntries.add(suggestion);
            filterOffset = Math.max(0, filterEntries.size() - visibleFilterRows());
        }
        filterInput.setValue("");
        updateValidation();
    }

    private void updateValidation() {
        if (selectedIcon == null) {
            validationMessage = Component.translatable("screen.mobsstorage.label.choose_icon");
        } else {
            Optional<Component> error = FilterRules.validateIcon(selectedIcon)
                    .or(() -> FilterRules.validate(filterEntries));
            if (error.isEmpty() && filterInput != null && !filterInput.getValue().isBlank()) {
                error = FilterRules.validate(List.of(filterInput.getValue().trim()));
            }
            if (error.isEmpty() && filterEntries.size() >= FilterRules.MAX_FILTERS
                    && filterInput != null && !filterInput.getValue().isBlank()) {
                error = Optional.of(Component.translatable(
                        "screen.mobsstorage.label.invalid_entry", "too many entries"));
            }
            if (error.isEmpty() && priorityInput != null && !validPriority()) {
                error = Optional.of(Component.translatable("screen.mobsstorage.label.invalid_priority"));
            }
            validationMessage = error.orElse(CommonComponents.EMPTY);
        }
        if (applyButton != null) {
            applyButton.active = validationMessage.equals(CommonComponents.EMPTY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        int left = panelLeft();
        int right = left + panelWidth();
        graphics.fill(left - 6, 5, right + 6, footerY() + 25, 0xB0101010);
        graphics.renderOutline(left - 6, 5, panelWidth() + 12, footerY() + 20, 0xFF686868);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        renderSelectedIcon(graphics);
        renderItemGrid(graphics, mouseX, mouseY);
        renderSuggestions(graphics, mouseX, mouseY);
        renderFilterList(graphics, mouseX, mouseY);

        if (!validationMessage.equals(CommonComponents.EMPTY)) {
            String message = font.plainSubstrByWidth(validationMessage.getString(), panelWidth());
            graphics.drawString(font, message, left, footerY() - 11, 0xFFFF7777, false);
        } else if (conflictingStacks() > 0) {
            Component warning = Component.translatable("screen.mobsstorage.label.conflict_warning",
                    conflictingStacks(), conflictingItems());
            graphics.drawString(font, font.plainSubstrByWidth(warning.getString(), panelWidth()),
                    left, footerY() - 11, ejectConflicts ? 0xFFFFAA66 : 0xFFFFCC66, false);
        }
        if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(font,
                    List.of(hoveredStack.getHoverName(), Component.literal(hoveredItemId.toString())),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    private int conflictingStacks() {
        if (filterEntries.isEmpty()) return 0;
        return (int) contents.stream()
                .filter(stack -> !FilterRules.matches(stack, filterEntries)).count();
    }

    private int conflictingItems() {
        if (filterEntries.isEmpty()) return 0;
        return contents.stream().filter(stack -> !FilterRules.matches(stack, filterEntries))
                .mapToInt(ItemStack::getCount).sum();
    }

    /** Prevent Screen.render() from invoking Minecraft's post-process blur after the panel is drawn. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void renderSelectedIcon(GuiGraphics graphics) {
        int x = rightColumnLeft();
        graphics.drawString(font, Component.translatable("screen.mobsstorage.label.selected_icon"), x, 53, 0xD8D8D8, false);
        if (selectedIcon == null) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.label.none"), x + 78, 53, 0xAAAAAA, false);
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(selectedIcon);
        graphics.renderFakeItem(item.getDefaultInstance(), x + 76, 48);
        String name = font.plainSubstrByWidth(item.getDescription().getString(), Math.max(30, columnWidth() - 98));
        graphics.drawString(font, name, x + 96, 53, 0xFFFFFF, false);
    }

    private void renderItemGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredStack = ItemStack.EMPTY;
        hoveredItemId = null;
        List<Item> items = filteredItems();
        int columns = gridColumns();
        int slots = columns * gridRows();
        int start = Math.min(itemOffset, Math.max(0, items.size() - 1));
        for (int visible = 0; visible < slots; visible++) {
            int index = start + visible;
            int x = panelLeft() + (visible % columns) * CELL;
            int y = gridY() + (visible / columns) * CELL;
            graphics.fill(x, y, x + CELL - 2, y + CELL - 2, 0xDD202020);
            graphics.renderOutline(x, y, CELL - 2, CELL - 2, 0xFF444444);
            if (index >= items.size()) {
                continue;
            }
            Item item = items.get(index);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.equals(selectedIcon)) {
                graphics.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF4A5E73);
                graphics.renderOutline(x, y, CELL - 2, CELL - 2, 0xFFFFFFFF);
            } else if (mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2) {
                graphics.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, 0x665A7A96);
            }
            ItemStack stack = item.getDefaultInstance();
            graphics.renderFakeItem(stack, x + 3, y + 3);
            if (mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2) {
                hoveredStack = stack;
                hoveredItemId = id;
            }
        }
    }

    private void renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = rightColumnLeft();
        graphics.drawString(font, Component.translatable("screen.mobsstorage.label.suggested_tags"), x, 101, 0xD8D8D8, false);
        List<String> suggestions = matchingSuggestions();
        if (suggestions.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.label.no_suggestions"), x, suggestionsY() + 4, 0x888888, false);
            return;
        }
        int end = Math.min(suggestions.size(), suggestionOffset + SUGGESTION_ROWS);
        for (int index = suggestionOffset; index < end; index++) {
            int y = suggestionsY() + (index - suggestionOffset) * SUGGESTION_ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, x, y, columnWidth(), SUGGESTION_ROW_HEIGHT - 1);
            graphics.fill(x, y, x + columnWidth(), y + SUGGESTION_ROW_HEIGHT - 1,
                    hovered ? 0xCC34516A : 0xAA242424);
            String label = font.plainSubstrByWidth("+ " + suggestions.get(index), columnWidth() - 6);
            graphics.drawString(font, label, x + 3, y + 4, hovered ? 0xFFFFFF : 0xD7E9F6, false);
        }
    }

    private void renderFilterList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = rightColumnLeft();
        graphics.drawString(font, Component.translatable("screen.mobsstorage.label.current_filters"), x, filterLabelY(), 0xD8D8D8, false);
        if (filterEntries.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.label.no_filters"), x, filterListY() + 5, 0x888888, false);
            return;
        }
        int end = Math.min(filterEntries.size(), filterOffset + visibleFilterRows());
        for (int index = filterOffset; index < end; index++) {
            int y = filterListY() + (index - filterOffset) * FILTER_ROW_HEIGHT;
            graphics.fill(x, y, x + columnWidth(), y + FILTER_ROW_HEIGHT - 2, 0xBB242424);
            String entry = font.plainSubstrByWidth(filterEntries.get(index), columnWidth() - 24);
            graphics.drawString(font, entry, x + 4, y + 6, 0xEEEEEE, false);
            boolean removeHovered = inside(mouseX, mouseY, x + columnWidth() - 20, y, 20, FILTER_ROW_HEIGHT - 2);
            graphics.fill(x + columnWidth() - 20, y, x + columnWidth(), y + FILTER_ROW_HEIGHT - 2,
                    removeHovered ? 0xFF9B3C3C : 0xCC4D2A2A);
            graphics.drawCenteredString(font, "x", x + columnWidth() - 10, y + 5, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideGrid(mouseX, mouseY)) {
            int column = (int) (mouseX - panelLeft()) / CELL;
            int row = (int) (mouseY - gridY()) / CELL;
            int index = itemOffset + row * gridColumns() + column;
            List<Item> items = filteredItems();
            if (index >= 0 && index < items.size()) {
                selectedIcon = BuiltInRegistries.ITEM.getKey(items.get(index));
                suggestionOffset = 0;
                updateValidation();
                return true;
            }
        }
        if (button == 0 && insideSuggestions(mouseX, mouseY)) {
            int row = (int) (mouseY - suggestionsY()) / SUGGESTION_ROW_HEIGHT;
            int index = suggestionOffset + row;
            List<String> suggestions = matchingSuggestions();
            if (index >= 0 && index < suggestions.size()) {
                addSuggestion(suggestions.get(index));
                return true;
            }
        }
        if (button == 0 && insideFilterList(mouseX, mouseY)) {
            int row = (int) (mouseY - filterListY()) / FILTER_ROW_HEIGHT;
            int index = filterOffset + row;
            if (index >= 0 && index < filterEntries.size()
                    && mouseX >= rightColumnLeft() + columnWidth() - 20) {
                filterEntries.remove(index);
                clampOffsets();
                updateValidation();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = -(int) Math.signum(scrollY);
        if (insideGrid(mouseX, mouseY)) {
            int max = Math.max(0, filteredItems().size() - gridColumns() * gridRows());
            itemOffset = Math.max(0, Math.min(max, itemOffset + direction * gridColumns()));
            return true;
        }
        if (insideSuggestions(mouseX, mouseY)) {
            int max = Math.max(0, matchingSuggestions().size() - SUGGESTION_ROWS);
            suggestionOffset = Math.max(0, Math.min(max, suggestionOffset + direction));
            return true;
        }
        if (insideFilterList(mouseX, mouseY)) {
            int max = Math.max(0, filterEntries.size() - visibleFilterRows());
            filterOffset = Math.max(0, Math.min(max, filterOffset + direction));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (filterInput != null && filterInput.isFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                return commitInput();
            }
            if (keyCode == InputConstants.KEY_TAB) {
                List<String> suggestions = matchingSuggestions();
                if (!suggestions.isEmpty()) {
                    filterInput.setValue(suggestions.get(Math.min(suggestionOffset, suggestions.size() - 1)));
                    filterInput.moveCursorToEnd(false);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<Item> filteredItems() {
        if (search == null || search.getValue().isBlank()) {
            return allItems;
        }
        String query = search.getValue().toLowerCase(Locale.ROOT);
        List<Item> result = new ArrayList<>();
        for (Item item : allItems) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.toString().contains(query)
                    || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(item);
            }
        }
        return result;
    }

    private List<String> matchingSuggestions() {
        Set<String> selectedTags = new HashSet<>();
        String selectedId = selectedIcon == null ? "" : selectedIcon.toString();
        String selectedMod = selectedIcon == null ? "" : "@" + selectedIcon.getNamespace();
        if (selectedIcon != null) {
            BuiltInRegistries.ITEM.get(selectedIcon).getDefaultInstance().getTags()
                    .map(tag -> "#" + tag.location())
                    .forEach(selectedTags::add);
        }
        String query = filterInput == null ? "" : filterInput.getValue().trim().toLowerCase(Locale.ROOT);
        java.util.stream.Stream<String> candidates;
        if (query.startsWith("#")) {
            candidates = allItemTags.stream();
        } else if (query.startsWith("@")) {
            candidates = allModFilters.stream();
        } else if (query.startsWith("&")) {
            candidates = allItemIds.stream().map(id -> "&" + id);
        } else if (query.startsWith("$")) {
            candidates = java.util.stream.Stream.empty();
        } else {
            candidates = java.util.stream.Stream.of(
                    allItemIds.stream(), allItemTags.stream(), allModFilters.stream())
                    .flatMap(stream -> stream);
        }
        return candidates
                .filter(suggestion -> !filterEntries.contains(suggestion))
                .filter(suggestion -> query.isEmpty() || suggestion.toLowerCase(Locale.ROOT).contains(query))
                .distinct()
                .sorted(Comparator
                        .comparingInt((String suggestion) -> suggestionPriority(
                                suggestion, selectedId, selectedMod, selectedTags))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static int suggestionPriority(
            String suggestion,
            String selectedId,
            String selectedMod,
            Set<String> selectedTags
    ) {
        if (suggestion.equals(selectedId)) {
            return 0;
        }
        if (selectedTags.contains(suggestion)) {
            return 1;
        }
        if (suggestion.equals(selectedMod)) {
            return 2;
        }
        if (suggestion.startsWith("#c:")) {
            return 3;
        }
        if (suggestion.startsWith("@")) {
            return 4;
        }
        if (suggestion.startsWith("#")) {
            return 5;
        }
        return 6;
    }

    private static Component displayModeName(LabelDisplayMode mode) {
        return Component.translatable("screen.mobsstorage.label.display_mode." + mode.getSerializedName());
    }

    private void clampOffsets() {
        itemOffset = Math.max(0, itemOffset);
        suggestionOffset = Math.max(0, Math.min(suggestionOffset,
                Math.max(0, matchingSuggestions().size() - SUGGESTION_ROWS)));
        filterOffset = Math.max(0, Math.min(filterOffset,
                Math.max(0, filterEntries.size() - visibleFilterRows())));
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, panelLeft(), gridY(), gridColumns() * CELL, gridRows() * CELL);
    }

    private boolean insideSuggestions(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, rightColumnLeft(), suggestionsY(), columnWidth(),
                SUGGESTION_ROWS * SUGGESTION_ROW_HEIGHT);
    }

    private boolean insideFilterList(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, rightColumnLeft(), filterListY(), columnWidth(),
                visibleFilterRows() * FILTER_ROW_HEIGHT);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int panelWidth() {
        return Math.max(260, Math.min(PANEL_MAX_WIDTH, width - 16));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int columnWidth() {
        return (panelWidth() - PANEL_GAP) / 2;
    }

    private int rightColumnLeft() {
        return panelLeft() + columnWidth() + PANEL_GAP;
    }

    private int gridColumns() {
        return Math.max(1, columnWidth() / CELL);
    }

    private int gridRows() {
        return Math.max(1, (footerY() - gridY() - 2) / CELL);
    }

    private int gridY() {
        return 76;
    }

    private int suggestionsY() {
        return 112;
    }

    private int filterLabelY() {
        return suggestionsY() + SUGGESTION_ROWS * SUGGESTION_ROW_HEIGHT + 5;
    }

    private int filterListY() {
        return filterLabelY() + 13;
    }

    private int visibleFilterRows() {
        return Math.max(1, (footerY() - filterListY() - 14) / FILTER_ROW_HEIGHT);
    }

    private int footerY() {
        return height - 26;
    }

    private boolean validPriority() {
        try {
            int value = Integer.parseInt(priorityInput.getValue().trim());
            return value >= -9999 && value <= 9999;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private int parsePriority() {
        return validPriority() ? Integer.parseInt(priorityInput.getValue().trim()) : 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
