package org.destroyermob.mobsstorage.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.level.Level;
import org.destroyermob.mobsstorage.networking.NetworkService;
import org.destroyermob.mobsstorage.storage.StorageResolver;

public final class NetworkWandItem extends Item {
    public NetworkWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !context.isSecondaryUseActive()
                || !StorageResolver.networkEligible(context.getLevel(), context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkService.useWandOnStorage(serverPlayer, context.getClickedPos(), context.getItemInHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                NetworkService.openManager(serverPlayer, wand);
            } else {
                NetworkService.cycleWandMode(serverPlayer, wand);
            }
        }
        return InteractionResultHolder.sidedSuccess(wand, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        NetworkService.selectedNetworkName(stack).ifPresentOrElse(
                name -> tooltip.add(Component.translatable("item.mobsstorage.network_wand.selected", name)),
                () -> tooltip.add(Component.translatable("item.mobsstorage.network_wand.unselected")));
        tooltip.add(Component.translatable("item.mobsstorage.network_wand.mode",
                Component.translatable(NetworkService.wandMode(stack).translationKey())));
        tooltip.add(Component.translatable("item.mobsstorage.network_wand.hint"));
        tooltip.add(Component.translatable("item.mobsstorage.network_wand.manage_hint"));
    }
}
