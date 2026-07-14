package org.destroyermob.mobsstorage.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.destroyermob.mobsstorage.registry.ModBlockEntities;
import org.destroyermob.mobsstorage.registry.ModBlocks;

public final class NetworkPortBlockEntity extends BlockEntity implements Container {
    public NetworkPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_PORT.get(), pos, state);
    }

    public boolean isInput() {
        return getBlockState().is(ModBlocks.NETWORK_INPUT.get());
    }

    public boolean isOutput() {
        return getBlockState().is(ModBlocks.NETWORK_OUTPUT.get());
    }

    @Override public int getContainerSize() { return 0; }
    @Override public boolean isEmpty() { return true; }
    @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) { }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { }
}
