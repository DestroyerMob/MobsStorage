package org.destroyermob.mobsstorage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.destroyermob.mobsstorage.menu.NetworkTerminalMenu;
import org.destroyermob.mobsstorage.menu.TerminalSortMode;
import org.destroyermob.mobsstorage.network.TerminalExtractPayload;
import org.destroyermob.mobsstorage.network.TerminalQueryPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NetworkTerminalScreen extends AbstractContainerScreen<NetworkTerminalMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int TEXT = 0xFF404040;
    private static final int SCROLL_X = 171;
    private static final int SCROLL_Y = 24;
    private static final int SCROLL_WIDTH = 6;
    private static final int SCROLL_HEIGHT = 88;
    private static final int MIN_THUMB_HEIGHT = 15;
    private boolean draggingScrollbar;
    private int requestedScrollRow = -1;
    private EditBox search;
    private EditBox amount;
    private Button sortButton;
    private TerminalSortMode sortMode = TerminalSortMode.NAME;

    public NetworkTerminalScreen(NetworkTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 274;
        imageHeight = 224;
        inventoryLabelX = 8;
        inventoryLabelY = 130;
    }

    @Override
    protected void init() {
        super.init();
        search = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 4, 102, 16,
                Component.translatable("screen.mobsstorage.terminal.search")));
        search.setMaxLength(64);
        search.setHint(Component.translatable("screen.mobsstorage.terminal.search"));
        search.setResponder(value -> sendQuery());
        sortButton = addRenderableWidget(Button.builder(sortMode.displayName(), button -> {
            sortMode = sortMode.next();
            button.setMessage(sortMode.displayName());
            sendQuery();
        }).bounds(leftPos + 114, topPos + 4, 63, 16).build());
        amount = addRenderableWidget(new EditBox(font, leftPos + 184, topPos + 92, 82, 16,
                Component.translatable("screen.mobsstorage.terminal.amount")));
        amount.setMaxLength(3);
        amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        amount.setHint(Component.translatable("screen.mobsstorage.terminal.amount"));
    }

    private void sendQuery() {
        if (search != null) {
            PacketDistributor.sendToServer(new TerminalQueryPayload(
                    menu.containerId, search.getValue(), sortMode));
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (requestedScrollRow > menu.maxScrollRows() || requestedScrollRow == menu.scrollRow()) {
            requestedScrollRow = -1;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Bypass Screen's post-process blur and render only the normal sharp container backdrop. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);

        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, x + 8 + column * 18, y + 24 + row * 18);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                drawSlot(graphics, x + 184 + column * 18, y + 34 + row * 18);
            }
        }
        drawSlot(graphics, x + 248, y + 52);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, x + 8 + column * 18, y + 142 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, x + 8 + column * 18, y + 200);
        }

        drawScrollbar(graphics, x + SCROLL_X, y + SCROLL_Y);
        graphics.fill(x + 179, y + 18, x + 180, y + 112, DARK);
        graphics.fill(x + 180, y + 18, x + 181, y + 112, LIGHT);
        graphics.drawString(font, Component.literal(">"), x + 233, y + 57, TEXT, false);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 2, LIGHT);
        graphics.fill(x, y, x + 2, y + height, LIGHT);
        graphics.fill(x, y + height - 2, x + width, y + height, DARK);
        graphics.fill(x + width - 2, y, x + width, y + height, DARK);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, LIGHT);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, DARK);
        graphics.fill(x, y, x + 16, y + 16, SLOT);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SCROLL_WIDTH, y + SCROLL_HEIGHT, DARK);
        graphics.fill(x + 1, y + 1, x + SCROLL_WIDTH - 1, y + SCROLL_HEIGHT - 1, SLOT);
        int thumbHeight = thumbHeight();
        int thumbY = y + thumbOffset(thumbHeight);
        int thumbColor = menu.maxScrollRows() > 0 ? PANEL : 0xFF9B9B9B;
        graphics.fill(x + 1, thumbY, x + SCROLL_WIDTH - 1, thumbY + thumbHeight, thumbColor);
        graphics.fill(x + 1, thumbY, x + SCROLL_WIDTH - 1, thumbY + 1, LIGHT);
        graphics.fill(x + SCROLL_WIDTH - 2, thumbY, x + SCROLL_WIDTH - 1, thumbY + thumbHeight, DARK);
        graphics.fill(x + 1, thumbY + thumbHeight - 1, x + SCROLL_WIDTH - 1, thumbY + thumbHeight, DARK);
    }

    private int thumbHeight() {
        int totalRows = 5 + menu.maxScrollRows();
        if (totalRows <= 5) return SCROLL_HEIGHT - 2;
        return Math.max(MIN_THUMB_HEIGHT, Math.round((SCROLL_HEIGHT - 2) * (5.0F / totalRows)));
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
        if (button == 0 && amount != null && !amount.getValue().isBlank()) {
            net.minecraft.world.inventory.Slot slot = getSlotUnderMouse();
            int slotId = slot == null ? -1 : menu.slots.indexOf(slot);
            if (slotId >= NetworkTerminalMenu.NETWORK_START && slotId < NetworkTerminalMenu.NETWORK_END) {
                try {
                    int requested = Integer.parseInt(amount.getValue());
                    if (requested > 0) {
                        PacketDistributor.sendToServer(new TerminalExtractPayload(
                                menu.containerId, slotId, requested));
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
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
        return mouseX >= leftPos + 7 && mouseX < leftPos + SCROLL_X + SCROLL_WIDTH
                && mouseY >= topPos + 23 && mouseY < topPos + SCROLL_Y + SCROLL_HEIGHT + 1;
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLL_X && mouseX < leftPos + SCROLL_X + SCROLL_WIDTH
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

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.mobsstorage.terminal.crafting"), 184, 22, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.terminal.status",
                        menu.usedSlots(), menu.totalSlots(), menu.loadedNodes(), menu.activeNodes()),
                8, 116, TEXT, false);
        if (menu.activeNodes() < menu.totalNodes()) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.terminal.offline",
                            menu.totalNodes() - menu.activeNodes()), 184, 116, 0xFF9A5B20, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }
}
