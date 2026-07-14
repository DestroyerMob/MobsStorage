package org.destroyermob.mobsstorage.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.destroyermob.mobsstorage.menu.NetworkTerminalMenu;
import org.destroyermob.mobsstorage.registry.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

public final class NetworkInterfaceBlockEntity extends BlockEntity implements Container, MenuProvider {
    public NetworkInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_INTERFACE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mobsstorage.network_interface");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new NetworkTerminalMenu(containerId, inventory, this);
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
