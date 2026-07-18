package org.destroyermob.mobsstorage.networking;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** A common slot view over vanilla containers and capability-backed modded storage. */
public final class NetworkStorageEndpoint {
    private final BlockEntity owner;
    private final Container container;
    private final IItemHandler itemHandler;

    private NetworkStorageEndpoint(BlockEntity owner, Container container, IItemHandler itemHandler) {
        this.owner = owner;
        this.container = container;
        this.itemHandler = itemHandler;
    }

    static NetworkStorageEndpoint itemHandler(BlockEntity owner, IItemHandler itemHandler) {
        return new NetworkStorageEndpoint(owner, null, itemHandler);
    }

    public static Optional<NetworkStorageEndpoint> find(BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) {
            return Optional.of(new NetworkStorageEndpoint(blockEntity, container, null));
        }
        Level level = blockEntity.getLevel();
        if (level == null) return Optional.empty();
        IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(), (Direction) null);
        if (handler == null) {
            for (Direction direction : Direction.values()) {
                handler = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(), direction);
                if (handler != null) break;
            }
        }
        return handler == null ? Optional.empty()
                : Optional.of(new NetworkStorageEndpoint(blockEntity, null, handler));
    }

    public Object identity() {
        return container != null ? container : itemHandler;
    }

    public int slots() {
        return container != null ? container.getContainerSize() : itemHandler.getSlots();
    }

    public ItemStack stack(int slot) {
        return container != null ? container.getItem(slot) : itemHandler.getStackInSlot(slot);
    }

    public ItemStack insert(int slot, ItemStack offered, boolean simulate) {
        if (offered.isEmpty()) return ItemStack.EMPTY;
        if (itemHandler != null) return itemHandler.insertItem(slot, offered, simulate);
        if (!container.canPlaceItem(slot, offered)) return offered;

        ItemStack existing = container.getItem(slot);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, offered)) return offered;
        int limit = Math.min(container.getMaxStackSize(), offered.getMaxStackSize());
        int capacity = existing.isEmpty() ? limit : Math.max(0, limit - existing.getCount());
        int moved = Math.min(capacity, offered.getCount());
        if (moved <= 0) return offered;

        ItemStack remainder = offered.copy();
        remainder.shrink(moved);
        if (!simulate) {
            if (existing.isEmpty()) container.setItem(slot, offered.copyWithCount(moved));
            else existing.grow(moved);
            container.setChanged();
        }
        return remainder;
    }

    public ItemStack extract(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        if (itemHandler != null) return itemHandler.extractItem(slot, amount, simulate);
        ItemStack stored = container.getItem(slot);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        int moved = Math.min(amount, stored.getCount());
        return simulate ? stored.copyWithCount(moved) : container.removeItem(slot, moved);
    }

    int slotLimit(int slot) {
        return container != null ? container.getMaxStackSize() : itemHandler.getSlotLimit(slot);
    }

    void changed() {
        if (container != null) container.setChanged();
        else owner.setChanged();
    }
}
