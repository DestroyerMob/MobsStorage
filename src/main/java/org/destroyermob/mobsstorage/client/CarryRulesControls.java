package org.destroyermob.mobsstorage.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.destroyermob.mobsstorage.inventory.CarryRule;
import org.destroyermob.mobsstorage.inventory.CarryRuleSet;
import org.destroyermob.mobsstorage.registry.ModAttachments;

public final class CarryRulesControls {
    private static final KeyMapping OPEN = new KeyMapping("key.mobsstorage.carry_rules",
            InputConstants.Type.KEYSYM, InputConstants.KEY_C, "key.categories.mobsstorage");
    private static final int INDICATOR = 0xFFB26DFF;
    private static CarryRulePopup popup;

    private CarryRulesControls() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof NetworkTerminalScreen screen && screen.isSearchFocused()) return;
        if (popup != null && popup.parent() != event.getScreen()) popup = null;
        if (popup != null) {
            if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
                closePopup();
            } else if (matches(event.getKeyCode(), event.getScanCode()) && !popup.isTyping()) {
                closePopup();
            } else {
                popup.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            }
            event.setCanceled(true);
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !matches(event.getKeyCode(), event.getScanCode())) return;
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null || !(hovered.container instanceof Inventory)) return;
        int inventorySlot = hovered.getContainerSlot();
        if (inventorySlot < 0 || inventorySlot > CarryRule.MAX_INVENTORY_SLOT) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        int anchorX = screen.getGuiLeft() + hovered.x;
        int anchorY = screen.getGuiTop() + hovered.y;
        popup = new CarryRulePopup(screen, inventorySlot, anchorX, anchorY,
                minecraft.player.getData(ModAttachments.CARRY_RULES));
        event.setCanceled(true);
    }

    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (popup == null || popup.parent() != event.getScreen()) return;
        popup.charTyped(event.getCodePoint(), event.getModifiers());
        event.setCanceled(true);
    }

    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (popup == null || popup.parent() != event.getScreen()) return;
        popup.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
        event.setCanceled(true);
    }

    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (popup == null || popup.parent() != event.getScreen()) return;
        popup.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
        event.setCanceled(true);
    }

    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (popup == null || popup.parent() != event.getScreen()) return;
        popup.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(),
                event.getDragX(), event.getDragY());
        event.setCanceled(true);
    }

    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (popup == null || popup.parent() != event.getScreen()) return;
        popup.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY());
        event.setCanceled(true);
    }

    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (popup != null && popup.parent() != screen) popup = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        CarryRuleSet rules = minecraft.player.getData(ModAttachments.CARRY_RULES);
        GuiGraphics graphics = event.getGuiGraphics();
        for (Slot slot : screen.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) continue;
            if (rules.ruleForSlot(slot.getContainerSlot()).isEmpty()) continue;
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            graphics.fill(x + 6, y, x + 10, y + 2, INDICATOR);
        }

        if (popup != null) {
            popup.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
            return;
        }

        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null || !(hovered.container instanceof Inventory)) return;
        int inventorySlot = hovered.getContainerSlot();
        Component status = rules.ruleForSlot(inventorySlot)
                .<Component>map(rule -> Component.translatable(
                        "screen.mobsstorage.carry.hover_summary", OPEN.getTranslatedKeyMessage(),
                        rule.minimum(), rule.target(), rule.maximum()))
                .orElseGet(() -> Component.translatable(
                        "screen.mobsstorage.carry.hover_configure", OPEN.getTranslatedKeyMessage()));
        graphics.drawString(minecraft.font, status, screen.getGuiLeft(), screen.getGuiTop() - 21,
                rules.ruleForSlot(inventorySlot).isPresent() ? INDICATOR : 0xFFAAAAAA, true);
    }

    static boolean matches(int keyCode, int scanCode) {
        return OPEN.matches(keyCode, scanCode);
    }

    static boolean blocksContainerShortcuts(Object screen) {
        return popup != null && popup.parent() == screen;
    }

    static void closePopup() {
        popup = null;
    }
}
