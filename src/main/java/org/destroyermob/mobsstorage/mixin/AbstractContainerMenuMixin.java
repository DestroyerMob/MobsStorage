package org.destroyermob.mobsstorage.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.destroyermob.mobsstorage.inventory.InventoryManagementService;
import org.destroyermob.mobsstorage.networking.NetworkInventoryService;
import org.destroyermob.mobsstorage.networking.NetworkService;
import org.destroyermob.mobsstorage.storage.StorageResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void mobsstorage$protectQuickDeposit(
            int slotId, int button, ClickType clickType, Player player, CallbackInfo callback
    ) {
        if (player instanceof ServerPlayer serverPlayer) {
            AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
            if (clickType == ClickType.QUICK_MOVE
                    && InventoryManagementService.blocksQuickDeposit(serverPlayer, menu, slotId)) {
                callback.cancel();
                return;
            }
            if (routeRejectedInsertion(serverPlayer, menu, slotId, button, clickType)) {
                callback.cancel();
            }
        }
    }

    private static boolean routeRejectedInsertion(
            ServerPlayer player, AbstractContainerMenu menu, int slotId, int button, ClickType clickType
    ) {
        if (slotId < 0 || slotId >= menu.slots.size()) return false;
        if (clickType == ClickType.PICKUP && (button == 0 || button == 1)) {
            return routeCarriedStack(player, menu, menu.slots.get(slotId), button == 0);
        }
        if (clickType == ClickType.QUICK_MOVE) {
            return routeQuickMove(player, menu, menu.slots.get(slotId));
        }
        return false;
    }

    private static boolean routeCarriedStack(
            ServerPlayer player, AbstractContainerMenu menu, Slot target, boolean wholeStack
    ) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || target.container instanceof Inventory
                || StorageResolver.allows(target.container, carried)) {
            return false;
        }
        int offeredCount = wholeStack ? carried.getCount() : 1;
        NetworkInventoryService.InsertResult result = NetworkInventoryService.insert(
                player, target.container, carried.copyWithCount(offeredCount));
        if (result.inserted() <= 0) return false;
        carried.shrink(result.inserted());
        menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        return true;
    }

    private static boolean routeQuickMove(ServerPlayer player, AbstractContainerMenu menu, Slot source) {
        ItemStack stack = source.getItem();
        if (!(source.container instanceof Inventory) || stack.isEmpty() || !source.mayPickup(player)) return false;
        Container openedStorage = menu.slots.stream()
                .map(slot -> slot.container)
                .filter(container -> !(container instanceof Inventory))
                .filter(container -> !StorageResolver.allows(container, stack))
                .filter(container -> NetworkService.nodeFor(container).isPresent())
                .findFirst()
                .orElse(null);
        if (openedStorage == null) return false;
        NetworkInventoryService.InsertResult result = NetworkInventoryService.insert(
                player, openedStorage, stack.copy());
        if (result.inserted() <= 0) return false;
        // The packet handler broadcasts the menu after this cancelled click. Mutating the player stack here
        // avoids firing a second attachment-backed inventory change while still producing the vanilla sync.
        stack.shrink(result.inserted());
        return true;
    }
}
