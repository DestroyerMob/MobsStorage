package org.destroyermob.mobsstorage.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.destroyermob.mobsstorage.networking.NetworkInventoryService;
import org.destroyermob.mobsstorage.networking.NetworkService;
import org.destroyermob.mobsstorage.world.NetworkPortBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void mobsstorage$routeDirectAutomation(
            Container source,
            Container destination,
            ItemStack stack,
            Direction direction,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        if (destination instanceof NetworkPortBlockEntity port && port.isInput()) {
            NetworkInventoryService.insertAutomated(port, stack, false)
                    .ifPresent(result -> callback.setReturnValue(result.remainder()));
            return;
        }
        if (NetworkService.nodeFor(destination).isPresent()) {
            callback.setReturnValue(stack);
            return;
        }
        if (source instanceof NetworkPortBlockEntity port && port.isOutput()) {
            return;
        }
        if (NetworkService.nodeFor(source).isPresent()) {
            callback.setReturnValue(stack);
        }
    }

    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void mobsstorage$pullFromOutputPort(
            Level level, Hopper hopper, CallbackInfoReturnable<Boolean> callback
    ) {
        BlockPos sourcePos = BlockPos.containing(
                hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());
        if (!(level.getBlockEntity(sourcePos) instanceof NetworkPortBlockEntity port) || !port.isOutput()) {
            return;
        }
        ItemStack extracted = NetworkInventoryService.extractFirst(port, 1, false);
        if (extracted.isEmpty()) {
            callback.setReturnValue(false);
            return;
        }
        int original = extracted.getCount();
        ItemStack remainder = HopperBlockEntity.addItem(port, hopper, extracted, Direction.DOWN);
        if (!remainder.isEmpty()) {
            NetworkInventoryService.insertAutomated(port, remainder, false);
        }
        callback.setReturnValue(remainder.getCount() < original);
    }
}
