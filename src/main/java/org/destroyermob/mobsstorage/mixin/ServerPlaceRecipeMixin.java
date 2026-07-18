package org.destroyermob.mobsstorage.mixin;

import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.destroyermob.mobsstorage.crafting.NearbyCraftingMenuAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {
    @Shadow
    protected RecipeBookMenu<?, ?> menu;

    @Inject(method = "moveItemToGrid", at = @At("RETURN"), cancellable = true)
    private void mobsstorage$pullFromNearbyStorage(
            Slot target, ItemStack wanted, int maxAmount, CallbackInfoReturnable<Integer> callback
    ) {
        if (!(menu instanceof NearbyCraftingMenuAccess nearby)) return;
        int vanillaRemaining = callback.getReturnValue();
        if (vanillaRemaining == 0) return;

        int needed = vanillaRemaining < 0 ? maxAmount : vanillaRemaining;
        ItemStack extracted = nearby.mobsstorage$getNearbyCraftingSources().takeMatching(wanted, needed);
        if (extracted.isEmpty()) return;
        if (target.getItem().isEmpty()) target.set(extracted);
        else {
            target.getItem().grow(extracted.getCount());
            target.setChanged();
        }
        callback.setReturnValue(needed - extracted.getCount());
    }
}
