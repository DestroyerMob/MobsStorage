package org.destroyermob.mobsstorage.networking;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Bidirectional automation view exposed by the Network Interface block. */
final class NetworkInterfaceItemHandler implements IItemHandler {
    private static final int VIRTUAL_SLOT_LIMIT = 64;
    private final Container endpoint;

    NetworkInterfaceItemHandler(Container endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public int getSlots() {
        return slots().size() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        List<NetworkInventoryService.StorageSlot> slots = slots();
        checkSlot(slot, slots.size());
        return slot == slots.size() ? ItemStack.EMPTY : slots.get(slot).stack().copy();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int storageSlots = slots().size();
        checkSlot(slot, storageSlots);
        return NetworkInventoryService.insertAutomated(endpoint, stack, simulate)
                .map(NetworkInventoryService.InsertResult::remainder)
                .orElse(stack);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        List<NetworkInventoryService.StorageSlot> slots = slots();
        checkSlot(slot, slots.size());
        if (slot == slots.size() || amount <= 0) return ItemStack.EMPTY;
        NetworkInventoryService.StorageSlot target = slots.get(slot);
        ItemStack stored = target.stack();
        if (stored.isEmpty()) return ItemStack.EMPTY;
        int moved = Math.min(amount, Math.min(stored.getCount(), stored.getMaxStackSize()));
        return simulate ? stored.copyWithCount(moved) : target.container().removeItem(target.slot(), moved);
    }

    @Override
    public int getSlotLimit(int slot) {
        List<NetworkInventoryService.StorageSlot> slots = slots();
        checkSlot(slot, slots.size());
        return slot == slots.size() ? VIRTUAL_SLOT_LIMIT : slots.get(slot).container().getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int storageSlots = slots().size();
        checkSlot(slot, storageSlots);
        return NetworkInventoryService.acceptsAutomated(endpoint, stack).orElse(false);
    }

    private List<NetworkInventoryService.StorageSlot> slots() {
        return NetworkInventoryService.automatedSlots(endpoint).orElse(List.of());
    }

    private static void checkSlot(int slot, int storageSlots) {
        if (slot < 0 || slot > storageSlots) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range");
        }
    }
}
