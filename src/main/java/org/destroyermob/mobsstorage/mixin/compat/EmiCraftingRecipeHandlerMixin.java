package org.destroyermob.mobsstorage.mixin.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import org.destroyermob.mobsstorage.crafting.NearbyCraftingMenuAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.emi.emi.handler.CraftingRecipeHandler", remap = false)
public abstract class EmiCraftingRecipeHandlerMixin {
    @Inject(
            method = "getInputSources(Lnet/minecraft/world/inventory/CraftingMenu;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false
    )
    private void mobsstorage$includeNearbyStorage(
            CraftingMenu menu, CallbackInfoReturnable<List<Slot>> callback
    ) {
        if (!(menu instanceof NearbyCraftingMenuAccess nearby)) return;
        List<Slot> sources = new ArrayList<>(callback.getReturnValue());
        sources.addAll(nearby.mobsstorage$getNearbyCraftingSlots());
        callback.setReturnValue(sources);
    }
}
