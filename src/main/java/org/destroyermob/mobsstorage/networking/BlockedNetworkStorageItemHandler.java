package org.destroyermob.mobsstorage.networking;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Prevents machines from bypassing the dedicated network input/output ports. */
final class BlockedNetworkStorageItemHandler implements IItemHandler {
    static final BlockedNetworkStorageItemHandler INSTANCE = new BlockedNetworkStorageItemHandler();

    private BlockedNetworkStorageItemHandler() {
    }

    @Override public int getSlots() { return 0; }
    @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
    @Override public int getSlotLimit(int slot) { return 0; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
}
