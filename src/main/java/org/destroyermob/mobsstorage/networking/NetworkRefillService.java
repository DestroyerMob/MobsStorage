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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.destroyermob.mobsstorage.inventory.CarryRule;
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
                returnToSource(extracted.sourceSlots(), remainder);
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
        CarryRule rule = ruleSet.rules().get(ruleIndex);
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
                for (NetworkInventoryService.StorageSlot storageSlot
                        : NetworkInventoryService.storageSlots(level, node.pos())) {
                    if (insertedTotal >= wanted) break;
                    ItemStack stored = storageSlot.stack();
                    if (!CarryRuleService.belongsToRule(ruleSet, ruleIndex, stored, tooltipContext)) continue;
                    int capacity = rule.slotted()
                            ? carrySlotCapacity(player, rule.inventorySlot(), stored)
                            : inventoryCapacity(player, stored, reservedEmptySlots);
                    int amount = Math.min(Math.min(wanted - insertedTotal, stored.getCount()), capacity);
                    if (amount <= 0) continue;
                    ItemStack removed = storageSlot.remove(amount);
                    int inserted = rule.slotted()
                            ? insertIntoCarrySlot(player, rule.inventorySlot(), removed)
                            : insertIntoInventory(player, removed, reservedEmptySlots);
                    insertedTotal += inserted;
                    if (!removed.isEmpty()) storageSlot.insert(removed);
                    storageSlot.setChanged();
                }
                if (insertedTotal >= wanted) {
                    player.getInventory().setChanged();
                    return insertedTotal;
                }
            }
        }
        if (insertedTotal > 0) player.getInventory().setChanged();
        return insertedTotal;
    }

    private static int carrySlotCapacity(ServerPlayer player, int inventorySlot, ItemStack candidate) {
        if (inventorySlot < 0 || inventorySlot >= 36) return 0;
        ItemStack stored = player.getInventory().getItem(inventorySlot);
        if (stored.isEmpty()) return candidate.getMaxStackSize();
        if (!ItemStack.isSameItemSameComponents(stored, candidate)) return 0;
        return Math.max(0, stored.getMaxStackSize() - stored.getCount());
    }

    private static int insertIntoCarrySlot(ServerPlayer player, int inventorySlot, ItemStack source) {
        if (source.isEmpty() || inventorySlot < 0 || inventorySlot >= 36) return 0;
        int original = source.getCount();
        ItemStack stored = player.getInventory().getItem(inventorySlot);
        if (stored.isEmpty()) {
            player.getInventory().setItem(inventorySlot,
                    source.split(Math.min(source.getMaxStackSize(), source.getCount())));
        } else if (ItemStack.isSameItemSameComponents(stored, source)) {
            int moved = Math.min(stored.getMaxStackSize() - stored.getCount(), source.getCount());
            stored.grow(moved);
            source.shrink(moved);
        }
        return original - source.getCount();
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
            for (NetworkInventoryService.StorageSlot slot
                    : NetworkInventoryService.storageSlots(level, node.pos()))
                if (sameItemId(sample, slot.stack())) return true;
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
            List<NetworkInventoryService.StorageSlot> storageSlots =
                    NetworkInventoryService.storageSlots(level, node.pos());
            ItemStack result = ItemStack.EMPTY;
            for (NetworkInventoryService.StorageSlot slot : storageSlots) {
                if (wanted <= 0) break;
                ItemStack stored = slot.stack();
                if (!sameItemId(original, stored)) continue;
                int amount = Math.min(wanted, stored.getCount());
                ItemStack removed = slot.remove(amount);
                if (result.isEmpty()) result = removed;
                else result.grow(removed.getCount());
                wanted -= removed.getCount();
                slot.setChanged();
            }
            if (!result.isEmpty()) return new Extracted(result, storageSlots);
        }
        return new Extracted(ItemStack.EMPTY, List.of());
    }

    private static void returnToSource(
            List<NetworkInventoryService.StorageSlot> sourceSlots, ItemStack remainder
    ) {
        for (NetworkInventoryService.StorageSlot slot : sourceSlots) {
            if (remainder.isEmpty()) return;
            ItemStack existing = slot.stack();
            if (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, remainder)) {
                remainder = slot.insert(remainder);
            }
        }
    }

    static boolean sameItemId(ItemStack expected, ItemStack candidate) {
        if (expected.isEmpty() || candidate.isEmpty()) return false;
        ResourceLocation expectedId = BuiltInRegistries.ITEM.getKey(expected.getItem());
        ResourceLocation candidateId = BuiltInRegistries.ITEM.getKey(candidate.getItem());
        return expectedId.equals(candidateId);
    }

    private record PendingRefill(ItemStack original, InteractionHand hand) {}
    private record Extracted(ItemStack stack, List<NetworkInventoryService.StorageSlot> sourceSlots) {}
}
