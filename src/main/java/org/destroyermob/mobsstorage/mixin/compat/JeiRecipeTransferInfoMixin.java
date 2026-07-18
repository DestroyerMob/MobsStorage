package org.destroyermob.mobsstorage.mixin.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.destroyermob.mobsstorage.crafting.NearbyCraftingMenuAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.library.transfer.BasicRecipeTransferInfo", remap = false)
public abstract class JeiRecipeTransferInfoMixin {
    @Inject(
            method = "getInventorySlots(Lnet/minecraft/world/inventory/AbstractContainerMenu;Ljava/lang/Object;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false
    )
    private void mobsstorage$includeNearbyStorage(
            AbstractContainerMenu menu, Object recipe, CallbackInfoReturnable<List<Slot>> callback
    ) {
        if (!(menu instanceof NearbyCraftingMenuAccess nearby)) return;
        List<Slot> sources = new ArrayList<>(callback.getReturnValue());
        sources.addAll(nearby.mobsstorage$getNearbyCraftingSlots());
        callback.setReturnValue(sources);
    }
}
