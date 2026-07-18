package org.destroyermob.mobsstorage.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.destroyermob.mobsstorage.networking.NetworkStorageEndpoint;
import org.destroyermob.mobsstorage.registry.ModTags;

/**
 * A fixed-size menu view backed by occupied slots in eligible storage near a crafting table.
 * The fixed size keeps the vanilla client and server menu layouts identical without a custom menu type.
 */
public final class NearbyCraftingSources implements Container, StackedContentsCompatible {
    public static final int RADIUS = 16;
    public static final int MENU_SLOT_COUNT = 512;

    private final NonNullList<ItemStack> clientItems = NonNullList.withSize(MENU_SLOT_COUNT, ItemStack.EMPTY);
    private final List<SourceSlot> serverSlots;

    public NearbyCraftingSources(ContainerLevelAccess access) {
        this.serverSlots = access.evaluate((level, pos) -> level instanceof ServerLevel serverLevel
                ? discover(serverLevel, pos)
                : null).orElse(null);
    }

    public boolean isBacked(int slot) {
        if (slot < 0 || slot >= MENU_SLOT_COUNT) return false;
        return serverSlots == null ? !clientItems.get(slot).isEmpty() : slot < serverSlots.size();
    }

    public ItemStack takeMatching(ItemStack wanted, int amount) {
        if (serverSlots == null || wanted.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        int remaining = amount;
        for (SourceSlot source : serverSlots) {
            ItemStack available = source.endpoint().stack(source.slot());
            if (!ItemStack.isSameItemSameComponents(available, wanted)) continue;
            ItemStack extracted = source.endpoint().extract(source.slot(), remaining, false);
            if (extracted.isEmpty()) continue;
            if (result.isEmpty()) result = extracted;
            else result.grow(extracted.getCount());
            remaining -= extracted.getCount();
            if (remaining <= 0) break;
        }
        return result;
    }

    @Override
    public int getContainerSize() {
        return MENU_SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < MENU_SLOT_COUNT; slot++) {
            if (!getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= MENU_SLOT_COUNT) return ItemStack.EMPTY;
        if (serverSlots == null) return clientItems.get(slot);
        return slot < serverSlots.size()
                ? serverSlots.get(slot).endpoint().stack(serverSlots.get(slot).slot())
                : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || amount <= 0) return ItemStack.EMPTY;
        if (serverSlots == null) {
            ItemStack current = getItem(slot);
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack removed = current.split(Math.min(amount, current.getCount()));
            if (current.isEmpty()) clientItems.set(slot, ItemStack.EMPTY);
            return removed;
        }
        if (slot >= serverSlots.size()) return ItemStack.EMPTY;
        SourceSlot source = serverSlots.get(slot);
        return source.endpoint().extract(source.slot(), amount, false);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        return current.isEmpty() ? ItemStack.EMPTY : removeItem(slot, current.getCount());
    }

    @Override
    public void setItem(int slot, ItemStack replacement) {
        if (slot < 0 || slot >= MENU_SLOT_COUNT) return;
        if (serverSlots == null) {
            clientItems.set(slot, replacement.copy());
            return;
        }
        if (slot >= serverSlots.size()) return;

        SourceSlot source = serverSlots.get(slot);
        ItemStack current = source.endpoint().stack(source.slot());
        if (replacement.isEmpty()) {
            source.endpoint().extract(source.slot(), current.getCount(), false);
        } else if (current.isEmpty()) {
            source.endpoint().insert(source.slot(), replacement.copy(), false);
        } else if (ItemStack.isSameItemSameComponents(current, replacement)) {
            int difference = replacement.getCount() - current.getCount();
            if (difference < 0) source.endpoint().extract(source.slot(), -difference, false);
            else if (difference > 0) {
                source.endpoint().insert(source.slot(), replacement.copyWithCount(difference), false);
            }
        }
    }

    @Override
    public void setChanged() {
        // Physical endpoints mark themselves changed when they are mutated.
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < MENU_SLOT_COUNT; slot++) {
            removeItemNoUpdate(slot);
        }
    }

    @Override
    public void fillStackedContents(StackedContents contents) {
        for (int slot = 0; slot < MENU_SLOT_COUNT; slot++) {
            contents.accountSimpleStack(getItem(slot));
        }
    }

    private static List<SourceSlot> discover(ServerLevel level, BlockPos craftingTable) {
        List<BlockEntity> candidates = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(craftingTable.getX() - RADIUS);
        int maxChunkX = SectionPos.blockToSectionCoord(craftingTable.getX() + RADIUS);
        int minChunkZ = SectionPos.blockToSectionCoord(craftingTable.getZ() - RADIUS);
        int maxChunkZ = SectionPos.blockToSectionCoord(craftingTable.getZ() + RADIUS);
        double radiusSquared = (double) RADIUS * RADIUS;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.distSqr(craftingTable) <= radiusSquared
                            && level.getBlockState(pos).is(ModTags.NETWORK_STORAGE)) {
                        candidates.add(blockEntity);
                    }
                }
            }
        }

        candidates.sort(Comparator
                .comparingDouble((BlockEntity blockEntity) -> blockEntity.getBlockPos().distSqr(craftingTable))
                .thenComparingLong(blockEntity -> blockEntity.getBlockPos().asLong()));

        List<SourceSlot> result = new ArrayList<>();
        Set<Object> seenEndpoints = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockEntity blockEntity : candidates) {
            NetworkStorageEndpoint endpoint = NetworkStorageEndpoint.find(blockEntity).orElse(null);
            if (endpoint == null || !seenEndpoints.add(endpoint.identity())) continue;
            for (int slot = 0; slot < endpoint.slots(); slot++) {
                ItemStack available = endpoint.stack(slot);
                if (available.isEmpty() || endpoint.extract(slot, 1, true).isEmpty()) continue;
                result.add(new SourceSlot(endpoint, slot));
                if (result.size() == MENU_SLOT_COUNT) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    private record SourceSlot(NetworkStorageEndpoint endpoint, int slot) {
    }
}
