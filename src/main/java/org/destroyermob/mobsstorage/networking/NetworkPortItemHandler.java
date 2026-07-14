package org.destroyermob.mobsstorage.networking;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.destroyermob.mobsstorage.world.NetworkPortBlockEntity;

/** One-way machine access for a linked network port. */
final class NetworkPortItemHandler implements IItemHandler {
    private static final int INPUT_SLOTS = 1;
    private static final int SLOT_LIMIT = 64;
    private final NetworkPortBlockEntity port;

    NetworkPortItemHandler(NetworkPortBlockEntity port) {
        this.port = port;
    }

    @Override
    public int getSlots() {
        return port.isInput() ? INPUT_SLOTS : outputSlots().size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        checkSlot(slot);
        return port.isInput() ? ItemStack.EMPTY : outputSlots().get(slot).stack().copy();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        checkSlot(slot);
        if (!port.isInput() || stack.isEmpty()) return stack;
        return NetworkInventoryService.insertAutomated(port, stack, simulate)
                .map(NetworkInventoryService.InsertResult::remainder)
                .orElse(stack);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        checkSlot(slot);
        if (!port.isOutput() || amount <= 0) return ItemStack.EMPTY;
        NetworkInventoryService.StorageSlot target = outputSlots().get(slot);
        ItemStack stored = target.stack();
        if (stored.isEmpty()) return ItemStack.EMPTY;
        int moved = Math.min(amount, Math.min(stored.getCount(), stored.getMaxStackSize()));
        return simulate ? stored.copyWithCount(moved) : target.container().removeItem(target.slot(), moved);
    }

    @Override
    public int getSlotLimit(int slot) {
        checkSlot(slot);
        return port.isInput() ? SLOT_LIMIT : outputSlots().get(slot).container().getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        checkSlot(slot);
        return port.isInput() && NetworkInventoryService.acceptsAutomated(port, stack).orElse(false);
    }

    private List<NetworkInventoryService.StorageSlot> outputSlots() {
        return NetworkInventoryService.automatedSlots(port).orElse(List.of());
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range");
        }
    }
}
