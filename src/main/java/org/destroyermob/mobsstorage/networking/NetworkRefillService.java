package org.destroyermob.mobsstorage.networking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.destroyermob.mobsstorage.storage.StorageResolver;
import org.destroyermob.mobsstorage.inventory.CarryRuleService;
import org.destroyermob.mobsstorage.inventory.CarryRuleSet;

public final class NetworkRefillService {
    public static final int RANGE = 256;
    private static final Map<UUID, List<PendingRefill>> PENDING = new HashMap<>();

    private NetworkRefillService() {
    }

    public static void onItemDestroyed(PlayerDestroyItemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            schedule(player, event.getOriginal(), event.getHand());
        }
    }

    public static void onItemUsed(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            schedule(player, event.getItem(), event.getHand());
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        List<PendingRefill> pending = PENDING.remove(player.getUUID());
        if (pending == null) return;
        for (PendingRefill refill : pending) {
            refill(player, refill.original(), refill.hand());
        }
    }

    public static void schedule(ServerPlayer player, ItemStack original, InteractionHand hand) {
        if (original.isEmpty()) return;
        List<PendingRefill> pending = PENDING.computeIfAbsent(player.getUUID(), unused -> new ArrayList<>());
        boolean duplicate = pending.stream().anyMatch(value -> value.hand() == hand
                && sameItemId(value.original(), original));
        if (!duplicate) pending.add(new PendingRefill(original.copyWithCount(1), hand));
    }

    static boolean refill(ServerPlayer player, ItemStack original, InteractionHand preferredHand) {
        if (hasReplacement(player, original)) return false;
        ItemStack carried = extractFromCarriedStorage(player, original);
        if (!carried.isEmpty()) {
            placeReplacement(player, carried, preferredHand);
            return true;
        }
        StorageNetworkSavedData data = StorageNetworkSavedData.get(player.server);
        for (StorageNetwork network : data.all()) {
            if (!network.isMember(player.getUUID()) || !nearNetwork(player, network)) continue;
            Extracted extracted = extract(player, network, original);
            if (extracted.stack().isEmpty()) continue;
            ItemStack remainder = extracted.stack();
            placeReplacement(player, remainder, preferredHand);
            if (!remainder.isEmpty()) {
                NetworkInventoryService.insertInto(extracted.sourceContainers(), remainder);
            }
            player.getInventory().setChanged();
            return true;
        }
        return false;
    }

    public static Optional<String> restockSource(ServerPlayer player, ResourceLocation itemId) {
        ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        if (sample.isEmpty()) return Optional.empty();
        return StorageNetworkSavedData.get(player.server).all().stream()
                .filter(network -> network.isMember(player.getUUID()) && nearNetwork(player, network))
                .filter(network -> contains(player, network, sample))
                .map(StorageNetwork::name).findFirst();
    }

    public static boolean refillConfiguredSlot(ServerPlayer player, ResourceLocation itemId, int slot) {
        if (slot < 0 || slot >= 36 || !player.getInventory().getItem(slot).isEmpty()) return false;
        ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        for (StorageNetwork network : StorageNetworkSavedData.get(player.server).all()) {
            if (!network.isMember(player.getUUID()) || !nearNetwork(player, network)) continue;
            Extracted extracted = extract(player, network, sample);
            if (!extracted.stack().isEmpty()) {
                player.getInventory().setItem(slot, extracted.stack());
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    public static int refillCarryRule(ServerPlayer player, CarryRuleSet ruleSet, int ruleIndex,
                                      int wanted, int reservedEmptySlots) {
        if (wanted <= 0 || ruleIndex < 0 || ruleIndex >= ruleSet.rules().size()) return 0;
        int insertedTotal = 0;
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(player.serverLevel());
        for (StorageNetwork network : StorageNetworkSavedData.get(player.server).all()) {
            if (!network.isMember(player.getUUID()) || !nearNetwork(player, network)) continue;
            List<GlobalPos> nodes = network.nodes().stream()
                    .sorted(java.util.Comparator.comparingInt((GlobalPos pos) -> network.nodeInfo(pos).priority()).reversed())
                    .toList();
            for (GlobalPos node : nodes) {
                ServerLevel level = player.server.getLevel(node.dimension());
                if (level == null || !level.isLoaded(node.pos())) continue;
                if (NetworkService.findNode(level, node.pos())
                        .filter(value -> value.networkId().equals(network.id())).isEmpty()) continue;
                for (BlockEntity blockEntity : StorageResolver.logicalStorage(level, node.pos())) {
                    if (!(blockEntity instanceof Container container)) continue;
                    boolean changed = false;
                    for (int slot = 0; slot < container.getContainerSize() && insertedTotal < wanted; slot++) {
                        ItemStack stored = container.getItem(slot);
                        if (!CarryRuleService.belongsToRule(ruleSet, ruleIndex, stored, tooltipContext)) continue;
                        int capacity = inventoryCapacity(player, stored, reservedEmptySlots);
                        int amount = Math.min(Math.min(wanted - insertedTotal, stored.getCount()), capacity);
                        if (amount <= 0) continue;
                        ItemStack removed = container.removeItem(slot, amount);
                        int inserted = insertIntoInventory(player, removed, reservedEmptySlots);
                        insertedTotal += inserted;
                        changed |= inserted > 0;
                        if (!removed.isEmpty()) NetworkInventoryService.insertInto(List.of(container), removed);
                    }
                    if (changed) container.setChanged();
                    if (insertedTotal >= wanted) {
                        player.getInventory().setChanged();
                        return insertedTotal;
                    }
                }
            }
        }
        if (insertedTotal > 0) player.getInventory().setChanged();
        return insertedTotal;
    }

    private static int inventoryCapacity(ServerPlayer player, ItemStack candidate, int reservedEmptySlots) {
        int capacity = 0;
        int empty = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stored = player.getInventory().getItem(slot);
            if (stored.isEmpty()) empty++;
            else if (ItemStack.isSameItemSameComponents(stored, candidate)) {
                capacity += Math.max(0, stored.getMaxStackSize() - stored.getCount());
            }
        }
        return capacity + Math.max(0, empty - reservedEmptySlots) * candidate.getMaxStackSize();
    }

    private static int insertIntoInventory(ServerPlayer player, ItemStack source, int reservedEmptySlots) {
        int original = source.getCount();
        for (int slot = 0; slot < 36 && !source.isEmpty(); slot++) {
            ItemStack stored = player.getInventory().getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stored, source)) continue;
            int moved = Math.min(stored.getMaxStackSize() - stored.getCount(), source.getCount());
            if (moved > 0) {
                stored.grow(moved);
                source.shrink(moved);
            }
        }
        int[] emptySlotOrder = new int[36];
        for (int index = 0; index < 27; index++) emptySlotOrder[index] = index + 9;
        for (int index = 27; index < 36; index++) emptySlotOrder[index] = index - 27;
        for (int slot : emptySlotOrder) {
            if (source.isEmpty() || emptyInventorySlots(player) <= reservedEmptySlots) break;
            if (!player.getInventory().getItem(slot).isEmpty()) continue;
            int moved = Math.min(source.getMaxStackSize(), source.getCount());
            player.getInventory().setItem(slot, source.split(moved));
        }
        return original - source.getCount();
    }

    private static int emptyInventorySlots(ServerPlayer player) {
        int empty = 0;
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot).isEmpty()) empty++;
        return empty;
    }

    private static boolean contains(ServerPlayer player, StorageNetwork network, ItemStack sample) {
        for (GlobalPos node : network.nodes()) {
            ServerLevel level = player.server.getLevel(node.dimension());
            if (level == null || !level.isLoaded(node.pos())) continue;
            for (BlockEntity blockEntity : StorageResolver.logicalStorage(level, node.pos())) {
                if (!(blockEntity instanceof Container container)) continue;
                for (int slot = 0; slot < container.getContainerSize(); slot++)
                    if (sameItemId(sample, container.getItem(slot))) return true;
            }
        }
        return false;
    }

    private static ItemStack extractFromCarriedStorage(ServerPlayer player, ItemStack original) {
        for (ItemStack storage : player.getInventory().items) {
            IItemHandler handler = Capabilities.ItemHandler.ITEM.getCapability(storage, null);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stored = handler.getStackInSlot(slot);
                if (sameItemId(original, stored)) return handler.extractItem(slot, original.isStackable()
                        ? stored.getMaxStackSize() : 1, false);
            }
        }
        return ItemStack.EMPTY;
    }

    private static void placeReplacement(ServerPlayer player, ItemStack replacement, InteractionHand preferredHand) {
        if (preferredHand != null && player.getItemInHand(preferredHand).isEmpty())
            player.setItemInHand(preferredHand, replacement.copyAndClear());
        else player.getInventory().add(replacement);
        player.getInventory().setChanged();
    }

    private static boolean hasReplacement(ServerPlayer player, ItemStack original) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (sameItemId(original, player.getInventory().getItem(slot))) return true;
        }
        return sameItemId(original, player.getOffhandItem());
    }

    private static boolean nearNetwork(ServerPlayer player, StorageNetwork network) {
        return network.nodes().stream()
                .filter(node -> node.dimension().equals(player.serverLevel().dimension()))
                .anyMatch(node -> Math.abs(node.pos().getX() - player.getBlockX()) <= RANGE
                        && Math.abs(node.pos().getY() - player.getBlockY()) <= RANGE
                        && Math.abs(node.pos().getZ() - player.getBlockZ()) <= RANGE);
    }

    private static Extracted extract(ServerPlayer player, StorageNetwork network, ItemStack original) {
        List<GlobalPos> nodes = network.nodes().stream()
                .sorted(java.util.Comparator.comparingInt((GlobalPos pos) -> network.nodeInfo(pos).priority()).reversed())
                .toList();
        int wanted = original.isStackable() ? original.getMaxStackSize() : 1;
        for (GlobalPos node : nodes) {
            ServerLevel level = player.server.getLevel(node.dimension());
            if (level == null || !level.isLoaded(node.pos())) continue;
            if (NetworkService.findNode(level, node.pos()).filter(value -> value.networkId().equals(network.id())).isEmpty()) continue;
            List<Container> containers = StorageResolver.logicalStorage(level, node.pos()).stream()
                    .filter(Container.class::isInstance).map(Container.class::cast).toList();
            ItemStack result = ItemStack.EMPTY;
            for (Container container : containers) {
                for (int slot = 0; slot < container.getContainerSize() && wanted > 0; slot++) {
                    ItemStack stored = container.getItem(slot);
                    if (!sameItemId(original, stored)) continue;
                    int amount = Math.min(wanted, stored.getCount());
                    ItemStack removed = container.removeItem(slot, amount);
                    if (result.isEmpty()) result = removed;
                    else result.grow(removed.getCount());
                    wanted -= removed.getCount();
                }
                container.setChanged();
            }
            if (!result.isEmpty()) return new Extracted(result, containers);
        }
        return new Extracted(ItemStack.EMPTY, List.of());
    }

    static boolean sameItemId(ItemStack expected, ItemStack candidate) {
        if (expected.isEmpty() || candidate.isEmpty()) return false;
        ResourceLocation expectedId = BuiltInRegistries.ITEM.getKey(expected.getItem());
        ResourceLocation candidateId = BuiltInRegistries.ITEM.getKey(candidate.getItem());
        return expectedId.equals(candidateId);
    }

    private record PendingRefill(ItemStack original, InteractionHand hand) {}
    private record Extracted(ItemStack stack, List<Container> sourceContainers) {}
}
