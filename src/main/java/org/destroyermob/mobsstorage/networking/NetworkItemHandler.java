package org.destroyermob.mobsstorage.networking;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Exposes a linked storage as an insertion point for its entire storage network. */
final class NetworkItemHandler implements IItemHandlerModifiable {
    private static final int VIRTUAL_SLOT_LIMIT = 64;
    private final Container container;
    private final IItemHandlerModifiable delegate;

    NetworkItemHandler(Container container, IItemHandlerModifiable delegate) {
        this.container = container;
        this.delegate = delegate;
    }

    @Override
    public int getSlots() {
        // The empty virtual slot keeps automation from treating a physically full
        // input chest as full while another network storage can still accept items.
        return delegate.getSlots() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return virtual(slot) ? ItemStack.EMPTY : delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        checkSlot(slot);
        return NetworkInventoryService.insertAutomated(container, stack, simulate)
                .map(NetworkInventoryService.InsertResult::remainder)
                .orElseGet(() -> virtual(slot) ? stack : delegate.insertItem(slot, stack, simulate));
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        checkSlot(slot);
        return virtual(slot) ? ItemStack.EMPTY : delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        checkSlot(slot);
        return virtual(slot) ? VIRTUAL_SLOT_LIMIT : delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        checkSlot(slot);
        return NetworkInventoryService.acceptsAutomated(container, stack)
                .orElseGet(() -> !virtual(slot) && delegate.isItemValid(slot, stack));
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        checkSlot(slot);
        if (virtual(slot)) {
            if (!stack.isEmpty()) throw new IllegalArgumentException("Cannot set the virtual network input slot");
            return;
        }
        delegate.setStackInSlot(slot, stack);
    }

    private boolean virtual(int slot) {
        return slot == delegate.getSlots();
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) throw new IllegalArgumentException("Slot " + slot + " not in valid range");
    }
}
