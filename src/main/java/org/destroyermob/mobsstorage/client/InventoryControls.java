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
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.inventory.InventoryProfile;
import org.destroyermob.mobsstorage.network.InventoryActionPayload;
import org.destroyermob.mobsstorage.registry.ModAttachments;

import java.util.List;

public final class InventoryControls {
    private static final String CATEGORY = "key.categories.mobsstorage";
    private static final List<ActionBinding> BINDINGS = List.of(
            binding("sort_item", InventoryActionPayload.Action.SORT_ITEM, Target.HOVERED_MENU),
            binding("sort_category", InventoryActionPayload.Action.SORT_CATEGORY, Target.HOVERED_MENU),
            binding("sort_quantity", InventoryActionPayload.Action.SORT_QUANTITY, Target.HOVERED_MENU),
            binding("consolidate", InventoryActionPayload.Action.CONSOLIDATE, Target.NONE),
            binding("transfer_matching", InventoryActionPayload.Action.TRANSFER_MATCHING, Target.NONE),
            binding("deposit", InventoryActionPayload.Action.DEPOSIT, Target.NONE),
            binding("toggle_lock", InventoryActionPayload.Action.TOGGLE_LOCK, Target.HOVERED_PLAYER),
            binding("toggle_favourite", InventoryActionPayload.Action.TOGGLE_FAVOURITE, Target.HOVERED_PLAYER),
            binding("toggle_hotbar", InventoryActionPayload.Action.TOGGLE_HOTBAR, Target.HOVERED_PLAYER),
            binding("toggle_restock", InventoryActionPayload.Action.TOGGLE_RESTOCK, Target.HOVERED_PLAYER)
    );

    private InventoryControls() {}

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        BINDINGS.forEach(binding -> event.register(binding.key()));
    }

    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        for (ActionBinding binding : BINDINGS) {
            if (!binding.key().matches(event.getKeyCode(), event.getScanCode())) continue;
            if (trigger(screen, binding)) {
                event.setCanceled(true);
            }
            return;
        }
    }

    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        for (ActionBinding binding : BINDINGS) {
            if (!binding.key().matchesMouse(event.getButton())) continue;
            if (trigger(screen, binding)) {
                event.setCanceled(true);
            }
            return;
        }
    }

    private static boolean trigger(AbstractContainerScreen<?> screen, ActionBinding binding) {
        int slotIndex = -1;
        if (binding.target() != Target.NONE) {
            Slot slot = screen.getSlotUnderMouse();
            if (slot == null) return false;
            if (binding.target() == Target.HOVERED_PLAYER) {
                if (!(slot.container instanceof Inventory)) return false;
                slotIndex = slot.getContainerSlot();
            } else {
                slotIndex = screen.getMenu().slots.indexOf(slot);
                if (slotIndex < 0) return false;
            }
        }
        send(screen, binding.action(), slotIndex);
        return true;
    }

    private static ActionBinding binding(String name, InventoryActionPayload.Action action,
                                         Target target) {
        return new ActionBinding(new KeyMapping("key.mobsstorage." + name, InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), CATEGORY), action, target);
    }

    private record ActionBinding(KeyMapping key, InventoryActionPayload.Action action,
                                 Target target) {
    }

    private enum Target { NONE, HOVERED_MENU, HOVERED_PLAYER }

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
