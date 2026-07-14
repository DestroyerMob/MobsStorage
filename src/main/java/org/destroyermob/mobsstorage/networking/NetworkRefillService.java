package org.destroyermob.mobsstorage.networking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.destroyermob.mobsstorage.storage.StorageResolver;

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
        StorageNetworkSavedData data = StorageNetworkSavedData.get(player.server);
        for (StorageNetwork network : data.all()) {
            if (!network.isMember(player.getUUID()) || !inRange(player, network)) continue;
            Extracted extracted = extract(player, network, original);
            if (extracted.stack().isEmpty()) continue;
            ItemStack remainder = extracted.stack();
            if (preferredHand != null && player.getItemInHand(preferredHand).isEmpty()) {
                player.setItemInHand(preferredHand, remainder.copyAndClear());
            } else {
                player.getInventory().add(remainder);
            }
            if (!remainder.isEmpty()) {
                NetworkInventoryService.insertInto(extracted.source(), remainder);
            }
            player.getInventory().setChanged();
            return true;
        }
        return false;
    }

    private static boolean hasReplacement(ServerPlayer player, ItemStack original) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (sameItemId(original, player.getInventory().getItem(slot))) return true;
        }
        return sameItemId(original, player.getOffhandItem());
    }

    private static boolean inRange(ServerPlayer player, StorageNetwork network) {
        return network.source().filter(source -> source.dimension().equals(player.serverLevel().dimension()))
                .filter(source -> Math.abs(source.pos().getX() - player.getBlockX()) <= RANGE)
                .filter(source -> Math.abs(source.pos().getY() - player.getBlockY()) <= RANGE)
                .filter(source -> Math.abs(source.pos().getZ() - player.getBlockZ()) <= RANGE)
                .isPresent();
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
    private record Extracted(ItemStack stack, List<Container> source) {}
}
