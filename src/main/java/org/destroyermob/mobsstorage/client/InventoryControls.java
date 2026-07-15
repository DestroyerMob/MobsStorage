package org.destroyermob.mobsstorage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.inventory.InventoryProfile;
import org.destroyermob.mobsstorage.network.InventoryActionPayload;
import org.destroyermob.mobsstorage.registry.ModAttachments;
import org.lwjgl.glfw.GLFW;

public final class InventoryControls {
    private InventoryControls() {}

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        int x = Math.max(2, screen.getGuiLeft() - 23);
        int y = screen.getGuiTop();
        add(event, screen, x, y, "A", "screen.mobsstorage.inventory.sort_item", InventoryActionPayload.Action.SORT_ITEM);
        add(event, screen, x, y + 22, "C", "screen.mobsstorage.inventory.sort_category", InventoryActionPayload.Action.SORT_CATEGORY);
        add(event, screen, x, y + 44, "#", "screen.mobsstorage.inventory.sort_quantity", InventoryActionPayload.Action.SORT_QUANTITY);
        add(event, screen, x, y + 66, "+", "screen.mobsstorage.inventory.consolidate", InventoryActionPayload.Action.CONSOLIDATE);
        add(event, screen, x, y + 88, "=", "screen.mobsstorage.inventory.transfer_matching", InventoryActionPayload.Action.TRANSFER_MATCHING);
        add(event, screen, x, y + 110, "D", "screen.mobsstorage.inventory.deposit", InventoryActionPayload.Action.DEPOSIT);
        event.addListener(Button.builder(Component.literal("?"), button -> {})
                .bounds(x, y + 132, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.mobsstorage.inventory.shortcuts"))).build());
    }

    private static void add(ScreenEvent.Init.Post event, AbstractContainerScreen<?> screen, int x, int y,
                            String text, String tooltip, InventoryActionPayload.Action action) {
        event.addListener(Button.builder(Component.literal(text), button -> send(screen, action, -1))
                .bounds(x, y, 20, 20).tooltip(Tooltip.create(Component.translatable(tooltip))).build());
    }

    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || (event.getModifiers() & GLFW.GLFW_MOD_ALT) == 0) return;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !(slot.container instanceof Inventory)) return;
        InventoryActionPayload.Action action = switch (event.getKeyCode()) {
            case GLFW.GLFW_KEY_L -> InventoryActionPayload.Action.TOGGLE_LOCK;
            case GLFW.GLFW_KEY_F -> InventoryActionPayload.Action.TOGGLE_FAVOURITE;
            case GLFW.GLFW_KEY_H -> InventoryActionPayload.Action.TOGGLE_HOTBAR;
            case GLFW.GLFW_KEY_N -> InventoryActionPayload.Action.TOGGLE_RESTOCK;
            default -> null;
        };
        if (action != null) {
            send(screen, action, slot.getContainerSlot());
            event.setCanceled(true);
        }
    }

    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        InventoryProfile profile = minecraft.player.getData(ModAttachments.INVENTORY_PROFILE);
        GuiGraphics graphics = event.getGuiGraphics();
        for (Slot slot : screen.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) continue;
            int index = slot.getContainerSlot();
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            if (profile.lockedSlots().contains(index)) graphics.fill(x, y, x + 3, y + 16, 0xCCEF5350);
            if (!slot.getItem().isEmpty() && profile.favourites().contains(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slot.getItem().getItem())))
                graphics.fill(x + 13, y, x + 16, y + 3, 0xFFFFD54F);
            if (profile.hotbarPreferences().containsKey(index)) graphics.fill(x, y + 13, x + 3, y + 16, 0xFF4FC3F7);
            if (profile.restockPreferences().containsKey(index)) graphics.fill(x + 13, y + 13, x + 16, y + 16,
                    profile.restockSources().containsKey(index) ? 0xFF66BB6A : 0xFF777777);
        }
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered != null && hovered.container instanceof Inventory) {
            int index = hovered.getContainerSlot();
            String source = profile.restockSources().get(index);
            if (profile.restockPreferences().containsKey(index)) {
                Component status = source == null
                        ? Component.translatable("screen.mobsstorage.inventory.restock_unavailable")
                        : Component.translatable("screen.mobsstorage.inventory.restock_network", source);
                graphics.drawString(minecraft.font, status, screen.getGuiLeft(), screen.getGuiTop() - 11,
                        source == null ? 0xAAAAAA : 0x66DD77, true);
            }
        }
    }

    private static void send(AbstractContainerScreen<?> screen, InventoryActionPayload.Action action, int slot) {
        PacketDistributor.sendToServer(new InventoryActionPayload(action, slot, screen.getMenu().containerId));
    }
}
