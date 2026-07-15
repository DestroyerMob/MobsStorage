package org.destroyermob.mobsstorage.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import org.destroyermob.mobsstorage.client.MobsStorageClient;
import org.destroyermob.mobsstorage.storage.StorageResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void mobsstorage$filterLabelledStorage(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!StorageResolver.allows(container, stack)
                || FMLEnvironment.dist.isClient()
                && !MobsStorageClient.allowsMenuSlot((Slot) (Object) this, stack)) {
            callback.setReturnValue(false);
        }
    }
}
