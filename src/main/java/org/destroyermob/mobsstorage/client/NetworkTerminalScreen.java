package org.destroyermob.mobsstorage.client;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.menu.NetworkTerminalMenu;
import org.destroyermob.mobsstorage.menu.NetworkTerminalSort;
import org.destroyermob.mobsstorage.network.UpdateTerminalViewPayload;
import org.destroyermob.mobsstorage.storage.FilterRules;
import org.lwjgl.glfw.GLFW;

public final class NetworkTerminalScreen extends AbstractContainerScreen<NetworkTerminalMenu> {
    private static final ResourceLocation TEXTURE = MobsStorage.id("textures/gui/crafting_terminal.png");
    private static final int TEXT = 0xFF404040;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SEARCH_X = 7;
    private static final int SEARCH_Y = 17;
    private static final int SEARCH_WIDTH = 126;
    private static final int SEARCH_HEIGHT = 16;
    private static final int SORT_X = 135;
    private static final int DIRECTION_X = 152;
    private static final int SYNC_X = -18;
    private static final int CONTROL_SIZE = 16;
    private static final int SCROLL_X = 170;
    private static final int SCROLL_Y = 36;
    private static final int NETWORK_GRID_X = 7;
    private static final int NETWORK_GRID_Y = 36;
    private static final int NETWORK_GRID_WIDTH = 162;
    private static final int NETWORK_GRID_HEIGHT = 109;
    private static final int SCROLL_WIDTH = 4;
    private static final int SCROLL_HEIGHT = 109;
    private static final int MIN_THUMB_HEIGHT = 15;
    private static final int SEARCH_DEBOUNCE_TICKS = 4;
    private static boolean itemBrowserSync;

    private EditBox search;
    private Button sortButton;
    private Button directionButton;
    private Button syncButton;
    private NetworkTerminalSort sort = NetworkTerminalSort.ITEM;
    private boolean descending;
    private boolean searchDirty;
    private int searchDebounce;
    private Component searchError = CommonComponents.EMPTY;
    private boolean draggingScrollbar;
    private int requestedScrollRow = -1;
    private boolean applyingBrowserQuery;
    private String lastBrowserQuery = "";

    public NetworkTerminalScreen(NetworkTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 310;
        inventoryLabelX = 8;
        inventoryLabelY = 217;
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

        syncButton = addRenderableWidget(Button.builder(syncSymbol(), button -> toggleBrowserSync())
                .bounds(leftPos + SYNC_X, topPos + SEARCH_Y, CONTROL_SIZE, CONTROL_SIZE).build());
        syncButton.active = ItemBrowserSearchBridge.available();

        sortButton = addRenderableWidget(Button.builder(sortSymbol(), button -> {
            sort = sort.next();
            updateControlLabels();
            sendViewUpdate();
        }).bounds(leftPos + SORT_X, topPos + SEARCH_Y, CONTROL_SIZE, CONTROL_SIZE).build());
        directionButton = addRenderableWidget(Button.builder(directionSymbol(), button -> {
            descending = !descending;
            updateControlLabels();
            sendViewUpdate();
        }).bounds(leftPos + DIRECTION_X, topPos + SEARCH_Y,
                CONTROL_SIZE, CONTROL_SIZE).build());
        initializeBrowserSync();
        updateControlLabels();
        setInitialFocus(search);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        pullBrowserSearch();
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
        if (itemBrowserSync && !applyingBrowserQuery
                && ItemBrowserSearchBridge.setSearchText(query)) {
            lastBrowserQuery = query;
        }
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
        if (syncButton != null) syncButton.setMessage(syncSymbol());
    }

    private Component syncSymbol() {
        return Component.literal("\u2194").withStyle(itemBrowserSync
                ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private void toggleBrowserSync() {
        if (!ItemBrowserSearchBridge.available()) return;
        itemBrowserSync = !itemBrowserSync;
        if (itemBrowserSync) initializeBrowserSync();
        updateControlLabels();
    }

    private void initializeBrowserSync() {
        Optional<String> browserQuery = ItemBrowserSearchBridge.searchText();
        lastBrowserQuery = browserQuery.orElse("");
        if (!itemBrowserSync || browserQuery.isEmpty()) return;
        if (search.getValue().isBlank() && !lastBrowserQuery.isBlank()) {
            applyBrowserQuery(lastBrowserQuery);
        } else if (ItemBrowserSearchBridge.setSearchText(search.getValue())) {
            lastBrowserQuery = search.getValue();
        }
    }

    private void pullBrowserSearch() {
        if (!itemBrowserSync || search == null) return;
        ItemBrowserSearchBridge.searchText().ifPresent(query -> {
            if (query.equals(lastBrowserQuery)) return;
            lastBrowserQuery = query;
            if (!query.equals(search.getValue())) applyBrowserQuery(query);
        });
    }

    private void applyBrowserQuery(String query) {
        applyingBrowserQuery = true;
        search.setValue(query);
        applyingBrowserQuery = false;
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

    boolean isSearchFocused() {
        return search != null && search.isFocused();
    }

    boolean captureSearchKey(int keyCode, int scanCode, int modifiers) {
        if (!isSearchFocused() || keyCode == GLFW.GLFW_KEY_ESCAPE) return false;
        search.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    boolean captureSearchCharacter(char codePoint, int modifiers) {
        if (!isSearchFocused()) return false;
        search.charTyped(codePoint, modifiers);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return captureSearchKey(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return captureSearchCharacter(codePoint, modifiers)
                || super.charTyped(codePoint, modifiers);
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
        if (button == 0 && search != null && !search.isMouseOver(mouseX, mouseY)) {
            search.setFocused(false);
            if (getFocused() == search) setFocused(null);
        }
        if (button == 0 && overScrollbar(mouseX, mouseY) && menu.maxScrollRows() > 0) {
            draggingScrollbar = true;
            scrollFromMouse(mouseY);
            return true;
        }
        if ((button == 0 || button == 1) && !menu.getCarried().isEmpty()
                && overNetworkDepositArea(mouseX, mouseY)
                && minecraft != null && minecraft.player != null && minecraft.gameMode != null) {
            // Treat the drawn network inventory as one continuous deposit target. This
            // avoids missed clicks on the one-pixel seams between its visual slots.
            minecraft.gameMode.handleInventoryMouseClick(
                    menu.containerId, NetworkTerminalMenu.NETWORK_START, button,
                    ClickType.PICKUP, minecraft.player);
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
        return mouseX >= leftPos + 7 && mouseX < leftPos + imageWidth
                && mouseY >= topPos + SCROLL_Y && mouseY < topPos + SCROLL_Y + SCROLL_HEIGHT;
    }

    private boolean overNetworkDepositArea(double mouseX, double mouseY) {
        return mouseX >= leftPos + NETWORK_GRID_X
                && mouseX < leftPos + NETWORK_GRID_X + NETWORK_GRID_WIDTH
                && mouseY >= topPos + NETWORK_GRID_Y
                && mouseY < topPos + NETWORK_GRID_Y + NETWORK_GRID_HEIGHT;
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
        if (syncButton != null && syncButton.isHovered()) {
            Component tooltip = ItemBrowserSearchBridge.available()
                    ? Component.translatable(itemBrowserSync
                                    ? "screen.mobsstorage.terminal.sync.enabled"
                                    : "screen.mobsstorage.terminal.sync.disabled",
                            ItemBrowserSearchBridge.name())
                    : Component.translatable("screen.mobsstorage.terminal.sync.unavailable");
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        } else if (sortButton != null && sortButton.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.mobsstorage.terminal.sort_tooltip",
                    Component.translatable("screen.mobsstorage.terminal.sort." + sort.name().toLowerCase())),
                    mouseX, mouseY);
        } else if (directionButton != null && directionButton.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(descending
                    ? "screen.mobsstorage.terminal.descending"
                    : "screen.mobsstorage.terminal.ascending"), mouseX, mouseY);
        } else if (search != null && search.isHovered()
                && !search.isFocused() && searchError.equals(CommonComponents.EMPTY)) {
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
