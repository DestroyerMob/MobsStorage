package org.destroyermob.mobsstorage.mixin;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.destroyermob.mobsstorage.networking.NetworkRefillService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Unique private ItemStack mobsstorage$beforeDamage = ItemStack.EMPTY;
    @Unique private ServerPlayer mobsstorage$damagingPlayer;
    @Unique private InteractionHand mobsstorage$damagedHand;

    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD")
    )
    private void mobsstorage$captureToolBeforeBreak(
            int amount, ServerLevel level, LivingEntity entity, Consumer<Item> onBreak, CallbackInfo callback
    ) {
        ItemStack self = (ItemStack) (Object) this;
        mobsstorage$beforeDamage = ItemStack.EMPTY;
        mobsstorage$damagingPlayer = null;
        mobsstorage$damagedHand = null;
        if (!(entity instanceof ServerPlayer player) || self.isEmpty()) {
            return;
        }
        if (player.getMainHandItem() == self) {
            mobsstorage$damagedHand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem() == self) {
            mobsstorage$damagedHand = InteractionHand.OFF_HAND;
        } else {
            return;
        }
        mobsstorage$beforeDamage = self.copy();
        mobsstorage$damagingPlayer = player;
    }

    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("RETURN")
    )
    private void mobsstorage$scheduleReplacementAfterBreak(
            int amount, ServerLevel level, LivingEntity entity, Consumer<Item> onBreak, CallbackInfo callback
    ) {
        ItemStack self = (ItemStack) (Object) this;
        if (mobsstorage$damagingPlayer != null && !mobsstorage$beforeDamage.isEmpty() && self.isEmpty()) {
            NetworkRefillService.schedule(
                    mobsstorage$damagingPlayer, mobsstorage$beforeDamage, mobsstorage$damagedHand);
        }
        mobsstorage$beforeDamage = ItemStack.EMPTY;
        mobsstorage$damagingPlayer = null;
        mobsstorage$damagedHand = null;
    }
}
