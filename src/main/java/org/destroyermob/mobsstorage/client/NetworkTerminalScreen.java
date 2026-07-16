package org.destroyermob.mobsstorage.client;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.menu.NetworkTerminalMenu;
import org.destroyermob.mobsstorage.menu.NetworkTerminalSort;
import org.destroyermob.mobsstorage.network.UpdateTerminalViewPayload;
import org.destroyermob.mobsstorage.storage.FilterRules;

public final class NetworkTerminalScreen extends AbstractContainerScreen<NetworkTerminalMenu> {
    private static final ResourceLocation TEXTURE = MobsStorage.id("textures/gui/crafting_terminal.png");
    private static final int TEXT = 0xFF404040;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SEARCH_X = 7;
    private static final int SEARCH_Y = 17;
    private static final int SEARCH_WIDTH = 162;
    private static final int SEARCH_HEIGHT = 16;
    private static final int CONTROL_X = -22;
    private static final int CONTROL_Y = 36;
    private static final int CONTROL_SIZE = 20;
    private static final int CONTROL_GAP = 2;
    private static final int SCROLL_X = 170;
    private static final int SCROLL_Y = 36;
    private static final int SCROLL_WIDTH = 4;
    private static final int SCROLL_HEIGHT = 109;
    private static final int MIN_THUMB_HEIGHT = 15;
    private static final int SEARCH_DEBOUNCE_TICKS = 4;

    private EditBox search;
    private Button sortButton;
    private Button directionButton;
    private Button clearButton;
    private Button helpButton;
    private NetworkTerminalSort sort = NetworkTerminalSort.ITEM;
    private boolean descending;
    private boolean searchDirty;
    private int searchDebounce;
    private Component searchError = CommonComponents.EMPTY;
    private boolean draggingScrollbar;
    private int requestedScrollRow = -1;

    public NetworkTerminalScreen(NetworkTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 175;
        imageHeight = 300;
        inventoryLabelX = 8;
        inventoryLabelY = 207;
    }

    @Override
    protected void init() {
        String previousQuery = search == null ? "" : search.getValue();
        super.init();

        search = addRenderableWidget(new EditBox(
                font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT,
                Component.translatable("screen.mobsstorage.terminal.search")));
        search.setMaxLength(UpdateTerminalViewPayload.MAX_QUERY_LENGTH);
        search.setHint(Component.translatable("screen.mobsstorage.terminal.search_hint"));
        search.setValue(previousQuery);
        search.setResponder(this::queryChanged);
        updateSearchValidation(previousQuery);

        int controlLeft = leftPos + CONTROL_X;
        int controlTop = topPos + CONTROL_Y;
        sortButton = addRenderableWidget(Button.builder(sortSymbol(), button -> {
            sort = sort.next();
            updateControlLabels();
            sendViewUpdate();
        }).bounds(controlLeft, controlTop, CONTROL_SIZE, CONTROL_SIZE).build());
        directionButton = addRenderableWidget(Button.builder(directionSymbol(), button -> {
            descending = !descending;
            updateControlLabels();
            sendViewUpdate();
        }).bounds(controlLeft, controlTop + CONTROL_SIZE + CONTROL_GAP,
                CONTROL_SIZE, CONTROL_SIZE).build());
        clearButton = addRenderableWidget(Button.builder(Component.literal("x"), button -> {
            search.setValue("");
            setInitialFocus(search);
        }).bounds(controlLeft, controlTop + (CONTROL_SIZE + CONTROL_GAP) * 2,
                CONTROL_SIZE, CONTROL_SIZE).build());
        helpButton = addRenderableWidget(Button.builder(Component.literal("?"), button ->
                setInitialFocus(search)).bounds(
                controlLeft, controlTop + (CONTROL_SIZE + CONTROL_GAP) * 3,
                CONTROL_SIZE, CONTROL_SIZE).build());
        updateControlLabels();
        setInitialFocus(search);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (requestedScrollRow > menu.maxScrollRows() || requestedScrollRow == menu.scrollRow()) {
            requestedScrollRow = -1;
        }
        if (searchDirty && --searchDebounce <= 0) {
            sendViewUpdate();
        }
    }

    private void queryChanged(String query) {
        updateSearchValidation(query);
        searchDirty = true;
        searchDebounce = SEARCH_DEBOUNCE_TICKS;
        if (clearButton != null) clearButton.active = !query.isBlank();
    }

    private void updateSearchValidation(String query) {
        searchError = query.isBlank()
                ? CommonComponents.EMPTY
                : FilterRules.validate(List.of(query.trim())).orElse(CommonComponents.EMPTY);
        if (search != null) search.setTextColor(searchError.equals(CommonComponents.EMPTY) ? 0xE0E0E0 : 0xFF6666);
    }

    private void sendViewUpdate() {
        if (search == null) return;
        searchDirty = false;
        searchDebounce = 0;
        requestedScrollRow = -1;
        PacketDistributor.sendToServer(new UpdateTerminalViewPayload(
                menu.containerId, search.getValue(), sort, descending));
    }

    private void updateControlLabels() {
        if (sortButton != null) sortButton.setMessage(sortSymbol());
        if (directionButton != null) directionButton.setMessage(directionSymbol());
        if (clearButton != null) clearButton.active = search != null && !search.getValue().isBlank();
    }

    private Component sortSymbol() {
        return Component.literal(switch (sort) {
            case ITEM -> "A";
            case QUANTITY -> "#";
            case MOD -> "@";
        });
    }

    private Component directionSymbol() {
        return Component.literal(descending ? "\u2193" : "\u2191");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderControlTooltip(graphics, mouseX, mouseY);
    }

    /** Bypass Screen's post-process blur and render only the normal sharp container backdrop. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0.0F, 0.0F,
                imageWidth, imageHeight, imageWidth, imageHeight);
        drawScrollbar(graphics, leftPos + SCROLL_X, topPos + SCROLL_Y);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SCROLL_WIDTH, y + SCROLL_HEIGHT, DARK);
        graphics.fill(x + 1, y + 1, x + SCROLL_WIDTH - 1, y + SCROLL_HEIGHT - 1, SLOT);
        int thumbHeight = thumbHeight();
        int thumbY = y + thumbOffset(thumbHeight);
        int thumbColor = menu.maxScrollRows() > 0 ? PANEL : 0xFF9B9B9B;
        graphics.fill(x + 1, thumbY, x + SCROLL_WIDTH - 1, thumbY + thumbHeight, thumbColor);
        graphics.fill(x + 1, thumbY, x + SCROLL_WIDTH - 1, thumbY + 1, LIGHT);
        graphics.fill(x + SCROLL_WIDTH - 2, thumbY,
                x + SCROLL_WIDTH - 1, thumbY + thumbHeight, DARK);
        graphics.fill(x + 1, thumbY + thumbHeight - 1,
                x + SCROLL_WIDTH - 1, thumbY + thumbHeight, DARK);
    }

    private int thumbHeight() {
        int totalRows = 6 + menu.maxScrollRows();
        if (totalRows <= 6) return SCROLL_HEIGHT - 2;
        return Math.max(MIN_THUMB_HEIGHT, Math.round((SCROLL_HEIGHT - 2) * (6.0F / totalRows)));
    }

    private int thumbOffset(int thumbHeight) {
        if (menu.maxScrollRows() <= 0) return 1;
        int travel = SCROLL_HEIGHT - 2 - thumbHeight;
        return 1 + Math.round(travel * (menu.scrollRow() / (float) menu.maxScrollRows()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && menu.maxScrollRows() > 0 && overNetworkInventory(mouseX, mouseY)) {
            int current = requestedScrollRow >= 0 ? requestedScrollRow : menu.scrollRow();
            requestScrollRow(current + (scrollY > 0.0D ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overScrollbar(mouseX, mouseY) && menu.maxScrollRows() > 0) {
            draggingScrollbar = true;
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean overNetworkInventory(double mouseX, double mouseY) {
        return mouseX >= leftPos + 7 && mouseX < leftPos + 175
                && mouseY >= topPos + SCROLL_Y && mouseY < topPos + SCROLL_Y + SCROLL_HEIGHT;
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLL_X - 2 && mouseX < leftPos + SCROLL_X + SCROLL_WIDTH + 1
                && mouseY >= topPos + SCROLL_Y && mouseY < topPos + SCROLL_Y + SCROLL_HEIGHT;
    }

    private void scrollFromMouse(double mouseY) {
        int thumbHeight = thumbHeight();
        double travel = SCROLL_HEIGHT - 2 - thumbHeight;
        double relative = mouseY - (topPos + SCROLL_Y + 1) - thumbHeight / 2.0D;
        int target = travel <= 0.0D ? 0 : (int) Math.round(Mth.clamp(relative / travel, 0.0D, 1.0D)
                * menu.maxScrollRows());
        requestScrollRow(target);
    }

    private void requestScrollRow(int row) {
        int target = Mth.clamp(row, 0, menu.maxScrollRows());
        if (minecraft != null && minecraft.gameMode != null && target != menu.scrollRow()) {
            requestedScrollRow = target;
            minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId, NetworkTerminalMenu.SCROLL_TO_BUTTON_BASE + target);
        }
    }

    private void renderControlTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sortButton != null && sortButton.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.mobsstorage.terminal.sort_tooltip",
                    Component.translatable("screen.mobsstorage.terminal.sort." + sort.name().toLowerCase())),
                    mouseX, mouseY);
        } else if (directionButton != null && directionButton.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(descending
                    ? "screen.mobsstorage.terminal.descending"
                    : "screen.mobsstorage.terminal.ascending"), mouseX, mouseY);
        } else if (clearButton != null && clearButton.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.mobsstorage.terminal.clear"), mouseX, mouseY);
        } else if (helpButton != null && helpButton.isHovered()) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("screen.mobsstorage.terminal.help.title"),
                    Component.translatable("screen.mobsstorage.terminal.help.name"),
                    Component.translatable("screen.mobsstorage.terminal.help.mod"),
                    Component.translatable("screen.mobsstorage.terminal.help.id"),
                    Component.translatable("screen.mobsstorage.terminal.help.tag"),
                    Component.translatable("screen.mobsstorage.terminal.help.tooltip"),
                    Component.translatable("screen.mobsstorage.terminal.help.logic")
            ), Optional.empty(), mouseX, mouseY);
        } else if (search != null && search.isHovered() && !searchError.equals(CommonComponents.EMPTY)) {
            graphics.renderTooltip(font, searchError, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String fittedTitle = font.plainSubstrByWidth(title.getString(), 161);
        graphics.drawString(font, fittedTitle, 7, 6, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.terminal.crafting"),
                29, 148, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        if (search != null && !search.getValue().isBlank() && networkSlotsEmpty()) {
            Component message = searchError.equals(CommonComponents.EMPTY)
                    ? Component.translatable("screen.mobsstorage.terminal.no_results")
                    : Component.translatable("screen.mobsstorage.terminal.invalid_search");
            graphics.drawCenteredString(font, message, 87, 85,
                    searchError.equals(CommonComponents.EMPTY) ? TEXT : 0xFFAA3333);
        }
    }

    private boolean networkSlotsEmpty() {
        for (int slot = NetworkTerminalMenu.NETWORK_START; slot < NetworkTerminalMenu.NETWORK_END; slot++) {
            if (menu.getSlot(slot).hasItem()) return false;
        }
        return true;
    }
}
