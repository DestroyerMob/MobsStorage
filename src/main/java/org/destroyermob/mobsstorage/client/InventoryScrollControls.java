package org.destroyermob.mobsstorage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.network.InventoryActionPayload;

/**
 * Provides deferred inventory-row scrolling while the player is outside a menu.
 * The client only previews the requested move; the server performs one validated
 * swap when the modifier chord is released.
 */
public final class InventoryScrollControls {
    private static final int HOTBAR_POSITION = 3;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SLOT_SPACING = 20;
    private static final int VERTICAL_BAR_LENGTH = 2 + HOTBAR_POSITION * SLOT_SPACING;
    private static final ResourceLocation HOTBAR_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static Preview preview;
    private static double scrollRemainder;

    private InventoryScrollControls() {
    }

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(MobsStorage.id("inventory_scroll"), InventoryScrollControls::renderHud);
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!canStart(minecraft) || !Screen.hasControlDown()) return;

        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) return;
        event.setCanceled(true);

        Mode mode = Screen.hasShiftDown() ? Mode.HOTBAR : Mode.VERTICAL_SLOT;
        int selected = minecraft.player.getInventory().selected;
        if (preview == null || preview.mode != mode || preview.hotbarSlot != selected) {
            preview = new Preview(mode, selected);
            scrollRemainder = 0.0D;
        }

        scrollRemainder += delta;
        int steps = (int) scrollRemainder;
        if (steps != 0) {
            preview.position = Math.floorMod(preview.position - steps, 4);
            scrollRemainder -= steps;
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (preview == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!canContinue(minecraft)) {
            clear();
            return;
        }

        boolean chordHeld = Screen.hasControlDown()
                && (preview.mode != Mode.HOTBAR || Screen.hasShiftDown());
        if (!chordHeld) commit(minecraft);
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (preview == null || !canContinue(minecraft) || minecraft.options.hideGui) return;

        graphics.pose().pushPose();
        if (preview.mode == Mode.VERTICAL_SLOT) renderVertical(graphics, minecraft);
        else renderHotbar(graphics, minecraft);
        graphics.pose().popPose();
    }

    private static boolean canStart(Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null && minecraft.screen == null
                && minecraft.player.isAlive() && !minecraft.player.isSpectator();
    }

    private static boolean canContinue(Minecraft minecraft) {
        return canStart(minecraft) && minecraft.player.getInventory().selected == preview.hotbarSlot;
    }

    private static void commit(Minecraft minecraft) {
        Preview finished = preview;
        clear();
        if (finished.position == HOTBAR_POSITION) return;

        int rowStart = 9 + finished.position * 9;
        InventoryActionPayload.Action action;
        int target;
        if (finished.mode == Mode.VERTICAL_SLOT) {
            action = InventoryActionPayload.Action.SWAP_VERTICAL_SLOT;
            target = rowStart + finished.hotbarSlot;
        } else {
            action = InventoryActionPayload.Action.SWAP_HOTBAR;
            target = rowStart;
        }
        PacketDistributor.sendToServer(new InventoryActionPayload(
                action, target, minecraft.player.containerMenu.containerId));
    }

    private static void renderVertical(GuiGraphics graphics, Minecraft minecraft) {
        Inventory inventory = minecraft.player.getInventory();
        int hotbarLeft = graphics.guiWidth() / 2 - HOTBAR_WIDTH / 2;
        int hotbarTop = graphics.guiHeight() - HOTBAR_HEIGHT;
        int x = hotbarLeft + preview.hotbarSlot * SLOT_SPACING;
        int y = hotbarTop - VERTICAL_BAR_LENGTH;

        RenderSystem.enableBlend();
        graphics.pose().pushPose();
        graphics.pose().translate(x + HOTBAR_HEIGHT, y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(90.0F));
        graphics.blitSprite(HOTBAR_SPRITE, HOTBAR_WIDTH, HOTBAR_HEIGHT,
                0, 0, 0, 0, VERTICAL_BAR_LENGTH, HOTBAR_HEIGHT);
        graphics.pose().popPose();
        if (preview.position != HOTBAR_POSITION) {
            int selectedY = y + preview.position * SLOT_SPACING;
            graphics.blitSprite(HOTBAR_SELECTION_SPRITE, x - 1, selectedY - 1, 24, 23);
        }
        RenderSystem.disableBlend();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        for (int position = 0; position < HOTBAR_POSITION; position++) {
            int slot = 9 + position * 9 + preview.hotbarSlot;
            renderItem(graphics, minecraft, inventory.getItem(slot),
                    x + 3, y + position * SLOT_SPACING + 3);
        }
        graphics.pose().popPose();
    }

    private static void renderHotbar(GuiGraphics graphics, Minecraft minecraft) {
        Inventory inventory = minecraft.player.getInventory();
        int rowStart = preview.position == HOTBAR_POSITION ? 0 : 9 + preview.position * 9;
        int x = graphics.guiWidth() / 2 - HOTBAR_WIDTH / 2;
        int y = graphics.guiHeight() - 46;

        RenderSystem.enableBlend();
        graphics.blitSprite(HOTBAR_SPRITE, x, y, HOTBAR_WIDTH, HOTBAR_HEIGHT);
        graphics.blitSprite(HOTBAR_SELECTION_SPRITE,
                x - 1 + preview.hotbarSlot * SLOT_SPACING, y - 1, 24, 23);
        RenderSystem.disableBlend();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        for (int column = 0; column < 9; column++) {
            renderItem(graphics, minecraft, inventory.getItem(rowStart + column),
                    x + 3 + column * SLOT_SPACING, y + 3);
        }
        graphics.pose().popPose();
    }

    private static void renderItem(GuiGraphics graphics, Minecraft minecraft, ItemStack stack,
                                   int x, int y) {
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(minecraft.font, stack, x, y);
        }
    }

    private static void clear() {
        preview = null;
        scrollRemainder = 0.0D;
    }

    private enum Mode { VERTICAL_SLOT, HOTBAR }

    private static final class Preview {
        private final Mode mode;
        private final int hotbarSlot;
        private int position = HOTBAR_POSITION;

        private Preview(Mode mode, int hotbarSlot) {
            this.mode = mode;
            this.hotbarSlot = hotbarSlot;
        }
    }
}
