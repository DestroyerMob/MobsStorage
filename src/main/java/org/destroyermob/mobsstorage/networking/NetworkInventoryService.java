package org.destroyermob.mobsstorage.networking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.destroyermob.mobsstorage.mixin.CompoundContainerAccessor;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.StorageResolver;

public final class NetworkInventoryService {
    private NetworkInventoryService() {
    }

    public static InsertResult insert(ServerPlayer player, Container current, ItemStack offered) {
        Optional<NetworkNodeData> currentNode = NetworkService.nodeFor(current);
        Optional<StorageNetwork> networkValue = NetworkService.accessibleNetwork(player, current);
        if (currentNode.isEmpty() || networkValue.isEmpty() || offered.isEmpty()) return new InsertResult(0, offered);
        GlobalPos currentPos = globalPos(player, current, currentNode.get());
        return insert(player.server, networkValue.get(), offered, currentPos, false);
    }

    public static Optional<InsertResult> insertAutomated(Container current, ItemStack offered, boolean simulate) {
        return automationContext(current).map(context ->
                insert(context.level().getServer(), context.network(), offered, context.current(), simulate));
    }

    public static Optional<Boolean> acceptsAutomated(Container current, ItemStack stack) {
        return automationContext(current).map(context -> loadedTargets(
                        context.level().getServer(), context.network(), stack, context.current()).stream()
                .flatMap(target -> target.endpoints().stream())
                .anyMatch(endpoint -> canEverPlace(endpoint, stack)));
    }

    /**
     * Returns one stack per distinct item/component combination. Counts are
     * aggregated in one pass so terminal refreshes do not allocate a copy for
     * every occupied physical slot or repeatedly linearly search prior entries.
     */
    public static List<ItemStack> networkContentSummary(Container endpoint) {
        Map<StackKey, ItemStack> summary = new LinkedHashMap<>();
        for (StorageSlot slot : automatedSlots(endpoint).orElse(List.of())) {
            ItemStack stored = slot.stack();
            if (stored.isEmpty()) continue;
            StackKey key = new StackKey(stored);
            ItemStack total = summary.get(key);
            if (total == null) {
                summary.put(key, stored.copy());
            } else {
                total.setCount((int) Math.min(Integer.MAX_VALUE,
                        (long) total.getCount() + stored.getCount()));
            }
        }
        return List.copyOf(summary.values());
    }

    public static int networkSlotCount(Container endpoint) {
        return automatedSlots(endpoint).orElse(List.of()).size();
    }

    public static List<StorageSlot> networkStorageSlots(Container endpoint) {
        return automatedSlots(endpoint).orElse(List.of());
    }

    public static ItemStack extractMatching(
            Container endpoint, ItemStack template, int amount, boolean simulate
    ) {
        if (template.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int moved = 0;
        for (StorageSlot slot : automatedSlots(endpoint).orElse(List.of())) {
            ItemStack stored = slot.stack();
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, template)) {
                int take = Math.min(amount - moved, stored.getCount());
                ItemStack extracted = slot.remove(take, simulate);
                moved += extracted.getCount();
                if (moved >= amount) break;
            }
        }
        return moved == 0 ? ItemStack.EMPTY : template.copyWithCount(moved);
    }

    public static ItemStack extractFirst(Container endpoint, int amount, boolean simulate) {
        return extractFirstMatching(endpoint, amount, simulate, stack -> true);
    }

    public static ItemStack extractFirstMatching(
            Container endpoint, int amount, boolean simulate, Predicate<ItemStack> filter
    ) {
        if (amount <= 0) return ItemStack.EMPTY;
        for (StorageSlot slot : automatedSlots(endpoint).orElse(List.of())) {
            ItemStack stored = slot.stack();
            if (stored.isEmpty() || !filter.test(stored)) continue;
            int moved = Math.min(amount, Math.min(stored.getCount(), stored.getMaxStackSize()));
            return slot.remove(moved, simulate);
        }
        return ItemStack.EMPTY;
    }

    static Optional<List<StorageSlot>> automatedSlots(Container endpoint) {
        return automationContext(endpoint).map(NetworkInventoryService::loadedStorageSlots);
    }

    private static List<StorageSlot> loadedStorageSlots(AutomationContext context) {
        List<StorageSlot> result = new ArrayList<>();
        Set<Object> seenEndpoints = Collections.newSetFromMap(new IdentityHashMap<>());
        List<GlobalPos> nodes = context.network().nodes().stream()
                .sorted(Comparator.comparingInt((GlobalPos pos) -> context.network().nodeInfo(pos).priority())
                        .reversed().thenComparing(GlobalPos::toString))
                .toList();
        for (GlobalPos nodePos : nodes) {
            ServerLevel level = context.level().getServer().getLevel(nodePos.dimension());
            if (level == null || !level.isLoaded(nodePos.pos())) continue;
            Optional<NetworkNodeData> node = NetworkService.findNode(level, nodePos.pos())
                    .filter(value -> value.networkId().equals(context.network().id()));
            if (node.isEmpty()) continue;
            for (BlockEntity blockEntity : StorageResolver.logicalStorage(level, node.get().anchor())) {
                NetworkStorageEndpoint.find(blockEntity).ifPresent(endpoint -> {
                    if (seenEndpoints.add(endpoint.identity())) addSlots(result, endpoint);
                });
            }
        }
        return List.copyOf(result);
    }

    static List<StorageSlot> storageSlots(ServerLevel level, BlockPos pos) {
        List<StorageSlot> result = new ArrayList<>();
        Set<Object> seenEndpoints = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockEntity blockEntity : StorageResolver.logicalStorage(level, pos)) {
            NetworkStorageEndpoint.find(blockEntity).ifPresent(endpoint -> {
                if (seenEndpoints.add(endpoint.identity())) addSlots(result, endpoint);
            });
        }
        return List.copyOf(result);
    }

    private static void addSlots(List<StorageSlot> result, NetworkStorageEndpoint endpoint) {
        for (int slot = 0; slot < endpoint.slots(); slot++) result.add(new StorageSlot(endpoint, slot));
    }

    private static InsertResult insert(
            MinecraftServer server, StorageNetwork network, ItemStack offered, GlobalPos current, boolean simulate
    ) {
        ItemStack remainder = offered.copy();
        List<Target> targets = loadedTargets(server, network, remainder, current);
        int original = remainder.getCount();
        for (Target target : targets) {
            remainder = insertInto(target.endpoints(), remainder, simulate);
            if (remainder.isEmpty()) break;
        }
        return new InsertResult(original - remainder.getCount(), remainder);
    }

    static List<Target> loadedTargets(
            MinecraftServer server, StorageNetwork network, ItemStack stack, GlobalPos current
    ) {
        List<Target> targets = new ArrayList<>();
        Set<Object> seenEndpoints = Collections.newSetFromMap(new IdentityHashMap<>());
        for (GlobalPos nodePos : network.nodes()) {
            ServerLevel level = server.getLevel(nodePos.dimension());
            if (level == null || !level.isLoaded(nodePos.pos())) continue;
            Optional<NetworkNodeData> node = NetworkService.findNode(level, nodePos.pos())
                    .filter(value -> value.networkId().equals(network.id()));
            if (node.isEmpty()) continue;
            List<BlockEntity> storage = StorageResolver.logicalStorage(level, node.get().anchor());
            List<NetworkStorageEndpoint> resolvedEndpoints = storage.stream()
                    .map(NetworkStorageEndpoint::find).flatMap(Optional::stream)
                    .toList();
            if (resolvedEndpoints.isEmpty()) continue;
            Optional<LabelData> label = StorageResolver.findLabel(level, node.get().anchor());
            if (label.isPresent() && !label.get().allows(stack, level)) continue;
            List<NetworkStorageEndpoint> endpoints = resolvedEndpoints.stream()
                    .filter(endpoint -> seenEndpoints.add(endpoint.identity())).toList();
            if (endpoints.isEmpty()) continue;
            boolean explicitFilter = label.filter(value -> !value.filters().isEmpty()).isPresent();
            int tier = explicitFilter ? 0 : nodePos.equals(current) ? 1 : 2;
            targets.add(new Target(nodePos, endpoints, node.get().priority(), tier));
        }
        targets.sort(Comparator.comparingInt(Target::tier)
                .thenComparing(Comparator.comparingInt(Target::priority).reversed())
                .thenComparing(target -> target.pos().toString()));
        return targets;
    }

    private static Optional<AutomationContext> automationContext(Container current) {
        Optional<NetworkNodeData> node = NetworkService.nodeFor(current);
        BlockEntity blockEntity = firstBlockEntity(current);
        if (node.isEmpty() || blockEntity == null || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return StorageNetworkSavedData.get(level.getServer()).get(node.get().networkId())
                .map(network -> new AutomationContext(level, network,
                        GlobalPos.of(level.dimension(), node.get().anchor())));
    }

    private static boolean canEverPlace(NetworkStorageEndpoint endpoint, ItemStack stack) {
        for (int slot = 0; slot < endpoint.slots(); slot++) {
            if (endpoint.insert(slot, stack, true).getCount() < stack.getCount()) return true;
        }
        return false;
    }

    private static ItemStack insertInto(
            List<NetworkStorageEndpoint> endpoints, ItemStack offered, boolean simulate
    ) {
        ItemStack remainder = offered;
        for (NetworkStorageEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.slots() && !remainder.isEmpty(); slot++) {
                ItemStack existing = endpoint.stack(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remainder)) {
                    remainder = endpoint.insert(slot, remainder, simulate);
                }
            }
        }
        for (NetworkStorageEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.slots() && !remainder.isEmpty(); slot++) {
                if (endpoint.stack(slot).isEmpty()) remainder = endpoint.insert(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    static GlobalPos globalPos(ServerPlayer player, Container container, NetworkNodeData node) {
        BlockEntity blockEntity = firstBlockEntity(container);
        return GlobalPos.of(blockEntity == null ? player.serverLevel().dimension() : blockEntity.getLevel().dimension(), node.anchor());
    }

    private static BlockEntity firstBlockEntity(Container container) {
        if (container instanceof BlockEntity blockEntity) return blockEntity;
        if (container instanceof CompoundContainer compound) {
            Container first = ((CompoundContainerAccessor) compound).mobsstorage$getFirst();
            return first instanceof BlockEntity blockEntity ? blockEntity : null;
        }
        return null;
    }

    public record InsertResult(int inserted, ItemStack remainder) {}
    public static final class StorageSlot {
        private final NetworkStorageEndpoint endpoint;
        private final int slot;

        private StorageSlot(NetworkStorageEndpoint endpoint, int slot) {
            this.endpoint = endpoint;
            this.slot = slot;
        }

        public ItemStack stack() {
            return endpoint.stack(slot);
        }

        public ItemStack remove(int amount) {
            return remove(amount, false);
        }

        public ItemStack remove(int amount, boolean simulate) {
            return endpoint.extract(slot, amount, simulate);
        }

        public ItemStack insert(ItemStack stack) {
            return endpoint.insert(slot, stack, false);
        }

        public int slotLimit() {
            return endpoint.slotLimit(slot);
        }

        public void setFromRecipeTransfer(ItemStack replacement) {
            ItemStack existing = stack();
            boolean clearing = replacement.isEmpty();
            boolean reducing = !clearing && ItemStack.isSameItemSameComponents(existing, replacement)
                    && replacement.getCount() <= existing.getCount();
            if (!clearing && !reducing) return;
            int remove = clearing ? existing.getCount() : existing.getCount() - replacement.getCount();
            if (remove > 0) endpoint.extract(slot, remove, false);
            endpoint.changed();
        }

        public void setChanged() {
            endpoint.changed();
        }
    }

    private static final class StackKey {
        private final ItemStack sample;

        private StackKey(ItemStack stack) {
            sample = stack.copyWithCount(1);
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof StackKey other
                    && ItemStack.isSameItemSameComponents(sample, other.sample);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(sample);
        }
    }

    private record AutomationContext(ServerLevel level, StorageNetwork network, GlobalPos current) {}
    record Target(GlobalPos pos, List<NetworkStorageEndpoint> endpoints, int priority, int tier) {}
}
