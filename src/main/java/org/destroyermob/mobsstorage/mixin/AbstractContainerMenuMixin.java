package org.destroyermob.mobsstorage.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.destroyermob.mobsstorage.inventory.InventoryManagementService;
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
            }
        }
    }
}
