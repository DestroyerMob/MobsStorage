package org.destroyermob.mobsstorage.networking;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.destroyermob.mobsstorage.world.NetworkPortBlockEntity;

/** One-way machine access for a linked network port. */
public final class NetworkPortItemHandler implements IItemHandler {
    private static final int INPUT_SLOTS = 1;
    private static final int SLOT_LIMIT = 64;
    private final NetworkPortBlockEntity port;
    private List<NetworkInventoryService.StorageSlot> cachedOutputSlots = List.of();
    private long cachedOutputTick = Long.MIN_VALUE;

    public NetworkPortItemHandler(NetworkPortBlockEntity port) {
        this.port = port;
    }

    @Override
    public int getSlots() {
        return port.isInput() ? INPUT_SLOTS : outputSlots().size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        checkSlot(slot);
        if (port.isInput()) return ItemStack.EMPTY;
        ItemStack stack = outputSlots().get(slot).stack();
        return port.allowsOutput(stack) ? stack.copy() : ItemStack.EMPTY;
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
        if (!port.allowsOutput(stored)) return ItemStack.EMPTY;
        int moved = Math.min(amount, Math.min(stored.getCount(), stored.getMaxStackSize()));
        return target.remove(moved, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        checkSlot(slot);
        return port.isInput() ? SLOT_LIMIT : outputSlots().get(slot).slotLimit();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        checkSlot(slot);
        return port.isInput() && NetworkInventoryService.acceptsAutomated(port, stack).orElse(false);
    }

    private List<NetworkInventoryService.StorageSlot> outputSlots() {
        long gameTime = port.getLevel() == null ? Long.MIN_VALUE : port.getLevel().getGameTime();
        if (cachedOutputTick != gameTime) {
            cachedOutputSlots = NetworkInventoryService.automatedSlots(port).orElse(List.of());
            cachedOutputTick = gameTime;
        }
        return cachedOutputSlots;
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range");
        }
    }
}
