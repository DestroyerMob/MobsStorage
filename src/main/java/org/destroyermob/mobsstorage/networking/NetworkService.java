package org.destroyermob.mobsstorage.networking;

import com.mojang.authlib.GameProfile;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.mixin.CompoundContainerAccessor;
import org.destroyermob.mobsstorage.item.NetworkWandMode;
import org.destroyermob.mobsstorage.network.NetworkActionPayload;
import org.destroyermob.mobsstorage.network.NetworkSnapshot;
import org.destroyermob.mobsstorage.network.OpenNetworkManagerPayload;
import org.destroyermob.mobsstorage.network.OpenNetworkNodePayload;
import org.destroyermob.mobsstorage.network.SaveNetworkNodePayload;
import org.destroyermob.mobsstorage.registry.ModAttachments;
import org.destroyermob.mobsstorage.registry.ModBlocks;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.StorageResolver;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlockEntity;

public final class NetworkService {
    private static final String SELECTED_NETWORK = "MobsStorageSelectedNetwork";
    private static final String SELECTED_NETWORK_NAME = "MobsStorageSelectedNetworkName";
    private static final String WAND_MODE = "MobsStorageWandMode";
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;

    private NetworkService() {
    }

    public static Optional<NetworkNodeData> existingNode(BlockEntity blockEntity) {
        return blockEntity.getExistingData(ModAttachments.NETWORK_NODE)
                .filter(NetworkNodeData::linked)
                .filter(node -> StorageResolver.ownsAnchor(blockEntity, node.anchor()));
    }

    public static NetworkNodeData nodeData(BlockEntity blockEntity) {
        return blockEntity.getData(ModAttachments.NETWORK_NODE);
    }

    public static Optional<NetworkNodeData> findNode(Level level, BlockPos pos) {
        return StorageResolver.logicalStorage(level, pos).stream()
                .map(NetworkService::existingNode).flatMap(Optional::stream).findFirst();
    }

    public static Optional<NetworkNodeData> nodeFor(Container container) {
        if (container instanceof BlockEntity blockEntity) {
            return existingNode(blockEntity);
        }
        if (container instanceof CompoundContainer compound) {
            CompoundContainerAccessor accessor = (CompoundContainerAccessor) compound;
            Optional<NetworkNodeData> first = nodeFor(accessor.mobsstorage$getFirst());
            return first.isPresent() ? first : nodeFor(accessor.mobsstorage$getSecond());
        }
        return Optional.empty();
    }

    public static void updateDetails(ServerLevel level, BlockPos pos, String name, int priority, ResourceLocation icon) {
        List<BlockEntity> storage = StorageResolver.logicalStorage(level, pos);
        if (storage.isEmpty()) return;
        NetworkNodeData old = storage.stream().map(NetworkService::nodeData).findFirst().orElse(NetworkNodeData.EMPTY);
        BlockPos anchor = old.anchor().equals(BlockPos.ZERO) ? pos.immutable() : old.anchor();
        NetworkNodeData updated = new NetworkNodeData(old.networkId(), name, priority, anchor);
        setNodeData(level, storage, updated);
        if (updated.linked()) {
            StorageNetworkSavedData data = StorageNetworkSavedData.get(level.getServer());
            data.get(updated.networkId()).ifPresent(network -> {
                network.updateNode(GlobalPos.of(level.dimension(), anchor), updated.name(), updated.priority(), icon);
                data.changed();
            });
        }
    }

    public static void useWandOnStorage(ServerPlayer player, BlockPos pos, ItemStack wand) {
        ServerLevel level = player.serverLevel();
        if (!StorageResolver.networkEligible(level, pos)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BaseContainerBlockEntity container && !container.canOpen(player)) {
            return;
        }
        Optional<NetworkNodeData> linked = findNode(level, pos);
        StorageNetworkSavedData data = StorageNetworkSavedData.get(player.server);
        if (linked.isPresent()) {
            Optional<StorageNetwork> linkedNetwork = data.get(linked.get().networkId());
            if (linkedNetwork.isEmpty()) {
                NetworkNodeData stale = linked.get();
                setNodeData(level, StorageResolver.logicalStorage(level, pos), new NetworkNodeData(
                        NetworkNodeData.NO_NETWORK, stale.name(), stale.priority(), stale.anchor()));
                linked = Optional.empty();
            }
        }

        switch (wandMode(wand)) {
            case ADD_STORAGE -> addStorage(player, pos, wand, data, linked);
            case SET_ORIGIN -> setOrigin(player, pos, wand, data, linked);
            case CONFIGURE_STORAGE -> configureStorage(player, pos, data, linked);
        }
    }

    public static void openManager(ServerPlayer player, ItemStack wand) {
        UUID selected = selectedNetwork(wand).orElse(NetworkActionPayload.NONE);
        StorageNetworkSavedData data = StorageNetworkSavedData.get(player.server);
        List<NetworkSnapshot> snapshots = data.all().stream()
                .filter(network -> network.isMember(player.getUUID()) || network.publicAccess())
                .sorted(Comparator.comparing((StorageNetwork network) -> !network.isOwner(player.getUUID()))
                        .thenComparing(StorageNetwork::name, String.CASE_INSENSITIVE_ORDER))
                .map(network -> snapshot(player.server, player, network, selected))
                .toList();
        PacketDistributor.sendToPlayer(player, new OpenNetworkManagerPayload(snapshots));
    }

    public static void handleAction(ServerPlayer player, NetworkActionPayload payload) {
        ItemStack wand = findWand(player);
        if (wand.isEmpty()) return;
        StorageNetworkSavedData data = StorageNetworkSavedData.get(player.server);
        UUID playerId = player.getUUID();
        switch (payload.action()) {
            case CREATE -> {
                StorageNetwork created = data.create(playerId, payload.value());
                setSelection(wand, created.id(), created.name());
            }
            case SELECT -> data.get(payload.networkId()).filter(network -> network.isMember(playerId))
                    .ifPresent(network -> setSelection(wand, network.id(), network.name()));
            case CLEAR_SELECTION -> clearSelection(wand);
            case JOIN -> data.get(payload.networkId()).filter(StorageNetwork::publicAccess).ifPresent(network -> {
                if (network.addMember(playerId)) data.changed();
                setSelection(wand, network.id(), network.name());
            });
            case LEAVE -> data.get(payload.networkId()).filter(network -> !network.isOwner(playerId)).ifPresent(network -> {
                if (network.removeMember(playerId)) data.changed();
                if (selectedNetwork(wand).filter(network.id()::equals).isPresent()) clearSelection(wand);
            });
            case TOGGLE_PUBLIC -> data.get(payload.networkId()).filter(network -> network.isOwner(playerId)).ifPresent(network -> {
                network.setPublicAccess(!network.publicAccess());
                data.changed();
            });
            case RENAME -> data.get(payload.networkId()).filter(network -> network.isOwner(playerId)).ifPresent(network -> {
                network.setName(payload.value());
                data.changed();
            });
            case ADD_MEMBER -> data.get(payload.networkId()).filter(network -> network.isOwner(playerId)).ifPresent(network ->
                    player.server.getProfileCache().get(payload.value()).ifPresent(profile -> {
                        if (network.addMember(profile.getId())) data.changed();
                    }));
            case REMOVE_MEMBER -> data.get(payload.networkId()).filter(network -> network.isOwner(playerId)).ifPresent(network -> {
                if (network.removeMember(payload.subjectId())) data.changed();
            });
            case DELETE -> data.get(payload.networkId()).filter(network -> network.isOwner(playerId)).ifPresent(network -> {
                unlinkLoadedNodes(player.server, network);
                data.delete(network.id(), playerId);
                if (selectedNetwork(wand).filter(network.id()::equals).isPresent()) clearSelection(wand);
            });
        }
        selectedNetwork(wand).flatMap(data::get)
                .ifPresent(network -> setSelection(wand, network.id(), network.name()));
        openManager(player, wand);
    }

    public static void saveNode(ServerPlayer player, SaveNetworkNodePayload payload) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(payload.pos()) || !StorageResolver.networkEligible(level, payload.pos())
                || player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_INTERACTION_DISTANCE_SQUARED) return;
        Optional<NetworkNodeData> node = findNode(level, payload.pos());
        if (node.isEmpty()) return;
        StorageNetworkSavedData data = StorageNetworkSavedData.get(player.server);
        Optional<StorageNetwork> networkValue = data.get(node.get().networkId())
                .filter(network -> network.isOwner(player.getUUID()));
        if (networkValue.isEmpty()) return;
        StorageNetwork network = networkValue.get();
        if (payload.unlink()) {
            unlink(level, node.get(), network);
            return;
        }
        ResourceLocation icon = nodeIcon(level, payload.pos());
        updateDetails(level, payload.pos(), payload.name(), payload.priority(), icon);
        data.changed();
        findNode(level, payload.pos()).ifPresent(updated -> openNode(player, payload.pos(), updated, network));
    }

    public static void onStorageBroken(ServerLevel level, BlockPos brokenPos) {
        Optional<NetworkNodeData> nodeValue = findNode(level, brokenPos);
        if (nodeValue.isEmpty() || !nodeValue.get().anchor().equals(brokenPos)) return;
        NetworkNodeData node = nodeValue.get();
        StorageNetworkSavedData data = StorageNetworkSavedData.get(level.getServer());
        data.get(node.networkId()).ifPresent(network -> unlink(level, node, network));
    }

    public static void onStorageJoined(ServerLevel level, BlockPos pos) {
        List<BlockEntity> storage = StorageResolver.logicalStorage(level, pos);
        Optional<NetworkNodeData> linked = storage.stream().map(NetworkService::nodeData)
                .filter(NetworkNodeData::linked).findFirst();
        linked.or(() -> storage.stream().map(NetworkService::nodeData).filter(NetworkNodeData::configured).findFirst())
                .ifPresent(node -> setNodeData(level, storage, node));
    }

    public static void reconcileTopology(ServerLevel level, BlockPos pos) {
        List<BlockEntity> storage = StorageResolver.logicalStorage(level, pos);
        if (storage.isEmpty()) return;
        List<BlockPos> positions = storage.stream().map(BlockEntity::getBlockPos).toList();
        for (BlockEntity blockEntity : storage) {
            Optional<NetworkNodeData> attached = blockEntity.getExistingData(ModAttachments.NETWORK_NODE)
                    .filter(NetworkNodeData::configured);
            if (attached.isEmpty()) continue;
            if (positions.contains(attached.get().anchor())) {
                synchronizeSavedNode(level, attached.get());
                continue;
            }
            if (!level.isLoaded(attached.get().anchor())) continue;
            if (repairIndependentNode(level, blockEntity, attached.get())) continue;
            removeStaleNode(level, blockEntity, attached.get());
        }
    }

    private static boolean repairIndependentNode(
            ServerLevel level, BlockEntity blockEntity, NetworkNodeData stale
    ) {
        BlockPos current = blockEntity.getBlockPos();
        Optional<LabelData> ownLabel = StorageResolver.rawLabel(blockEntity)
                .filter(label -> label.anchor().equals(current));
        if (!stale.linked() || ownLabel.isEmpty()) return false;

        Optional<NetworkNodeData> anchorNode = findNode(level, stale.anchor())
                .filter(node -> node.linked() && node.networkId().equals(stale.networkId())
                        && node.anchor().equals(stale.anchor()));
        if (anchorNode.isEmpty()) return false;

        StorageNetworkSavedData savedData = StorageNetworkSavedData.get(level.getServer());
        Optional<StorageNetwork> networkValue = savedData.get(stale.networkId())
                .filter(network -> network.nodes().contains(GlobalPos.of(level.dimension(), stale.anchor())));
        if (networkValue.isEmpty()) return false;

        StorageNetwork network = networkValue.get();
        NetworkNodeData repaired = new NetworkNodeData(
                stale.networkId(), stale.name(), stale.priority(), current.immutable());
        setNodeData(level, List.of(blockEntity), repaired);
        GlobalPos repairedPos = GlobalPos.of(level.dimension(), current);
        network.addNode(repairedPos);
        network.updateNode(repairedPos, repaired.name(), repaired.priority(), ownLabel.get().icon());

        NetworkNodeData owner = anchorNode.get();
        GlobalPos ownerPos = GlobalPos.of(level.dimension(), owner.anchor());
        network.updateNode(ownerPos, owner.name(), owner.priority(), nodeIcon(level, owner.anchor()));
        savedData.changed();
        return true;
    }

    private static void synchronizeSavedNode(ServerLevel level, NetworkNodeData node) {
        if (!node.linked()) return;
        GlobalPos position = GlobalPos.of(level.dimension(), node.anchor());
        StorageNetworkSavedData savedData = StorageNetworkSavedData.get(level.getServer());
        savedData.get(node.networkId()).filter(network -> network.nodes().contains(position)).ifPresent(network -> {
            StorageNetwork.NodeInfo current = network.nodeInfo(position);
            ResourceLocation icon = nodeIcon(level, node.anchor());
            if (!current.name().equals(node.name()) || current.priority() != node.priority()
                    || !current.icon().equals(icon)) {
                network.updateNode(position, node.name(), node.priority(), icon);
                savedData.changed();
            }
        });
    }

    private static void removeStaleNode(ServerLevel level, BlockEntity blockEntity, NetworkNodeData stale) {
        blockEntity.removeData(ModAttachments.NETWORK_NODE);
        blockEntity.setChanged();
        blockEntity.syncData(ModAttachments.NETWORK_NODE);
        level.invalidateCapabilities(blockEntity.getBlockPos());
        if (!stale.linked()) return;

        StorageNetworkSavedData savedData = StorageNetworkSavedData.get(level.getServer());
        savedData.get(stale.networkId()).ifPresent(network -> {
            boolean anchorStillOwnsNode = level.isLoaded(stale.anchor())
                    && findNode(level, stale.anchor())
                    .filter(node -> node.networkId().equals(stale.networkId()))
                    .isPresent();
            if (!anchorStillOwnsNode
                    && network.removeNode(GlobalPos.of(level.dimension(), stale.anchor()))) {
                savedData.changed();
            }
        });
    }

    public static Optional<StorageNetwork> accessibleNetwork(ServerPlayer player, Container container) {
        return nodeFor(container).flatMap(node -> StorageNetworkSavedData.get(player.server).get(node.networkId()))
                .filter(network -> network.isMember(player.getUUID()));
    }

    private static void addStorage(
            ServerPlayer player,
            BlockPos pos,
            ItemStack wand,
            StorageNetworkSavedData data,
            Optional<NetworkNodeData> linked
    ) {
        Optional<StorageNetwork> selected = selectedOwnedNetwork(player, wand, data);
        if (selected.isEmpty()) return;
        if (linked.isPresent()) {
            data.get(linked.get().networkId()).ifPresentOrElse(
                    network -> message(player, network.id().equals(selected.get().id())
                            ? "item.mobsstorage.network_wand.already_linked"
                            : "item.mobsstorage.network_wand.linked_elsewhere", network.name()),
                    () -> message(player, "item.mobsstorage.network_wand.not_linked"));
            return;
        }
        if (link(player, pos, selected.get())) {
            message(player, "item.mobsstorage.network_wand.storage_added", selected.get().name());
        }
    }

    public static void openTerminal(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof NetworkInterfaceBlockEntity terminal)) return;
        Optional<NetworkNodeData> node = findNode(level, pos);
        if (node.isEmpty()) {
            message(player, "block.mobsstorage.network_interface.not_linked");
            return;
        }
        Optional<StorageNetwork> network = StorageNetworkSavedData.get(player.server).get(node.get().networkId());
        if (network.isEmpty() || !network.get().isMember(player.getUUID())) {
            message(player, "block.mobsstorage.network_interface.no_access");
            return;
        }
        GlobalPos terminalPos = GlobalPos.of(level.dimension(), node.get().anchor());
        if (network.get().origin().isEmpty()) {
            message(player, "block.mobsstorage.network_interface.no_source");
            return;
        }
        if (!withinOriginRange(network.get(), terminalPos)) {
            message(player, "block.mobsstorage.network_interface.out_of_range");
            return;
        }
        player.openMenu(terminal, buffer -> {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(NetworkInventoryService.networkSlotCount(terminal));
        });
    }

    public static boolean canUseTerminal(Player player, NetworkInterfaceBlockEntity terminal) {
        if (!(player instanceof ServerPlayer serverPlayer) || terminal.getLevel() != serverPlayer.level()
                || !terminal.stillValid(player)) return false;
        Optional<NetworkNodeData> node = existingNode(terminal);
        if (node.isEmpty()) return false;
        Optional<StorageNetwork> network = StorageNetworkSavedData.get(serverPlayer.server).get(node.get().networkId())
                .filter(value -> value.isMember(player.getUUID()));
        GlobalPos pos = GlobalPos.of(serverPlayer.serverLevel().dimension(), node.get().anchor());
        return network.filter(value -> withinOriginRange(value, pos)).isPresent();
    }

    static boolean withinOriginRange(StorageNetwork network, GlobalPos endpoint) {
        return network.origin()
                .filter(origin -> origin.dimension().equals(endpoint.dimension()))
                .filter(origin -> Math.abs(origin.pos().getX() - endpoint.pos().getX()) <= NetworkRefillService.RANGE)
                .filter(origin -> Math.abs(origin.pos().getY() - endpoint.pos().getY()) <= NetworkRefillService.RANGE)
                .filter(origin -> Math.abs(origin.pos().getZ() - endpoint.pos().getZ()) <= NetworkRefillService.RANGE)
                .isPresent();
    }

    private static void setOrigin(
            ServerPlayer player,
            BlockPos pos,
            ItemStack wand,
            StorageNetworkSavedData data,
            Optional<NetworkNodeData> linked
    ) {
        Optional<StorageNetwork> selected = selectedOwnedNetwork(player, wand, data);
        if (selected.isEmpty()) return;
        if (linked.isEmpty() || !linked.get().networkId().equals(selected.get().id())) {
            message(player, "item.mobsstorage.network_wand.origin_requires_link", selected.get().name());
            return;
        }
        GlobalPos origin = GlobalPos.of(player.serverLevel().dimension(), linked.get().anchor());
        selected.get().setOrigin(origin);
        data.changed();
        message(player, "item.mobsstorage.network_wand.origin_set", selected.get().name(),
                origin.pos().getX(), origin.pos().getY(), origin.pos().getZ());
    }

    private static void configureStorage(
            ServerPlayer player,
            BlockPos pos,
            StorageNetworkSavedData data,
            Optional<NetworkNodeData> linked
    ) {
        if (linked.isEmpty()) {
            message(player, "item.mobsstorage.network_wand.not_linked");
            return;
        }
        data.get(linked.get().networkId()).filter(network -> network.isMember(player.getUUID()))
                .ifPresentOrElse(network -> openNode(player, pos, linked.get(), network),
                        () -> message(player, "item.mobsstorage.network_wand.no_access"));
    }

    private static Optional<StorageNetwork> selectedOwnedNetwork(
            ServerPlayer player,
            ItemStack wand,
            StorageNetworkSavedData data
    ) {
        Optional<UUID> selected = selectedNetwork(wand);
        if (selected.isEmpty()) {
            message(player, "item.mobsstorage.network_wand.select_first");
            return Optional.empty();
        }
        Optional<StorageNetwork> network = data.get(selected.get()).filter(value -> value.isOwner(player.getUUID()));
        if (network.isEmpty()) {
            clearSelection(wand);
            message(player, "item.mobsstorage.network_wand.selected_unavailable");
        }
        return network;
    }

    private static boolean link(ServerPlayer player, BlockPos pos, StorageNetwork network) {
        ServerLevel level = player.serverLevel();
        List<BlockEntity> storage = StorageResolver.logicalStorage(level, pos);
        if (storage.isEmpty() || storage.stream().map(NetworkService::existingNode).anyMatch(Optional::isPresent)) return false;
        NetworkNodeData old = storage.stream().map(NetworkService::nodeData).findFirst().orElse(NetworkNodeData.EMPTY);
        NetworkNodeData node = new NetworkNodeData(network.id(), old.name(), old.priority(), pos.immutable());
        setNodeData(level, storage, node);
        GlobalPos global = GlobalPos.of(level.dimension(), node.anchor());
        if (network.addNode(global)) {
            ResourceLocation icon = nodeIcon(level, pos);
            network.updateNode(global, node.name(), node.priority(), icon);
            StorageNetworkSavedData.get(player.server).changed();
            return true;
        }
        return false;
    }

    private static void unlink(ServerLevel level, NetworkNodeData node, StorageNetwork network) {
        List<BlockEntity> storage = StorageResolver.logicalStorage(level, node.anchor());
        NetworkNodeData unlinked = new NetworkNodeData(
                NetworkNodeData.NO_NETWORK, node.name(), node.priority(), node.anchor());
        setNodeData(level, storage, unlinked);
        network.removeNode(GlobalPos.of(level.dimension(), node.anchor()));
        StorageNetworkSavedData.get(level.getServer()).changed();
    }

    private static void setNodeData(Level level, List<BlockEntity> storage, NetworkNodeData data) {
        for (BlockEntity blockEntity : storage) {
            blockEntity.setData(ModAttachments.NETWORK_NODE, data);
            blockEntity.setChanged();
            blockEntity.syncData(ModAttachments.NETWORK_NODE);
            level.invalidateCapabilities(blockEntity.getBlockPos());
        }
    }

    private static void openNode(ServerPlayer player, BlockPos clicked, NetworkNodeData node, StorageNetwork network) {
        boolean origin = network.origin().filter(value -> value.equals(
                GlobalPos.of(player.serverLevel().dimension(), node.anchor()))).isPresent();
        PacketDistributor.sendToPlayer(player, new OpenNetworkNodePayload(
                clicked, network.id(), network.name(), node.name(), node.priority(), origin,
                network.isOwner(player.getUUID())));
    }

    private static NetworkSnapshot snapshot(
            MinecraftServer server, ServerPlayer viewer, StorageNetwork network, UUID selected
    ) {
        List<NetworkSnapshot.Member> members = network.members().stream()
                .map(id -> new NetworkSnapshot.Member(id, profileName(server, id)))
                .sorted(Comparator.comparing(NetworkSnapshot.Member::name, String.CASE_INSENSITIVE_ORDER)).toList();
        List<NetworkSnapshot.Node> nodes = network.nodes().stream().map(pos -> {
            StorageNetwork.NodeInfo info = network.nodeInfo(pos);
            String name = info.name().isBlank()
                    ? "Storage " + pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ() : info.name();
            return new NetworkSnapshot.Node(pos, name, info.priority(), info.icon(),
                    network.origin().filter(pos::equals).isPresent());
        }).sorted(Comparator.comparingInt(NetworkSnapshot.Node::priority).reversed()
                .thenComparing(NetworkSnapshot.Node::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return new NetworkSnapshot(network.id(), network.name(), profileName(server, network.owner()),
                network.publicAccess(), network.isOwner(viewer.getUUID()), network.isMember(viewer.getUUID()),
                network.id().equals(selected), members, nodes);
    }

    private static String profileName(MinecraftServer server, UUID id) {
        return server.getProfileCache().get(id).map(GameProfile::getName)
                .orElse(id.toString().substring(0, 8));
    }

    private static void unlinkLoadedNodes(MinecraftServer server, StorageNetwork network) {
        for (GlobalPos node : network.nodes()) {
            ServerLevel level = server.getLevel(node.dimension());
            if (level == null || !level.isLoaded(node.pos())) continue;
            findNode(level, node.pos()).filter(data -> data.networkId().equals(network.id())).ifPresent(data -> {
                NetworkNodeData unlinked = new NetworkNodeData(
                        NetworkNodeData.NO_NETWORK, data.name(), data.priority(), data.anchor());
                setNodeData(level, StorageResolver.logicalStorage(level, node.pos()), unlinked);
            });
        }
    }

    public static Optional<UUID> selectedNetwork(ItemStack wand) {
        if (!wand.is(ModItems.NETWORK_WAND.get())) return Optional.empty();
        CustomData data = wand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag().hasUUID(SELECTED_NETWORK)
                ? Optional.of(data.copyTag().getUUID(SELECTED_NETWORK)) : Optional.empty();
    }

    public static Optional<String> selectedNetworkName(ItemStack wand) {
        if (!wand.is(ModItems.NETWORK_WAND.get())) return Optional.empty();
        net.minecraft.nbt.CompoundTag tag = wand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(SELECTED_NETWORK_NAME) ? Optional.of(tag.getString(SELECTED_NETWORK_NAME)) : Optional.empty();
    }

    public static NetworkWandMode wandMode(ItemStack wand) {
        if (!wand.is(ModItems.NETWORK_WAND.get())) return NetworkWandMode.ADD_STORAGE;
        net.minecraft.nbt.CompoundTag tag = wand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return NetworkWandMode.byName(tag.getString(WAND_MODE));
    }

    public static void cycleWandMode(ServerPlayer player, ItemStack wand) {
        NetworkWandMode mode = wandMode(wand).next();
        CustomData.update(DataComponents.CUSTOM_DATA, wand, tag -> tag.putString(WAND_MODE, mode.serializedName()));
        player.displayClientMessage(Component.translatable("item.mobsstorage.network_wand.mode_changed",
                Component.translatable(mode.translationKey())), true);
    }

    private static void setSelection(ItemStack wand, UUID network, String name) {
        CustomData.update(DataComponents.CUSTOM_DATA, wand, tag -> {
            tag.putUUID(SELECTED_NETWORK, network);
            tag.putString(SELECTED_NETWORK_NAME, name);
        });
    }

    private static void clearSelection(ItemStack wand) {
        CustomData.update(DataComponents.CUSTOM_DATA, wand, tag -> {
            tag.remove(SELECTED_NETWORK);
            tag.remove(SELECTED_NETWORK_NAME);
        });
    }

    private static ItemStack findWand(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.NETWORK_WAND.get())) return stack;
        }
        if (player.getOffhandItem().is(ModItems.NETWORK_WAND.get())) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    private static void message(ServerPlayer player, String key, Object... values) {
        player.displayClientMessage(Component.translatable(key, values), true);
    }

    private static ResourceLocation nodeIcon(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(ModBlocks.NETWORK_INTERFACE.get())) {
            return BuiltInRegistries.ITEM.getKey(ModItems.NETWORK_INTERFACE.get());
        }
        if (level.getBlockState(pos).is(ModBlocks.NETWORK_INPUT.get())) {
            return BuiltInRegistries.ITEM.getKey(ModItems.NETWORK_INPUT.get());
        }
        if (level.getBlockState(pos).is(ModBlocks.NETWORK_OUTPUT.get())) {
            return BuiltInRegistries.ITEM.getKey(ModItems.NETWORK_OUTPUT.get());
        }
        return StorageResolver.findLabel(level, pos).map(LabelData::icon).orElse(LabelData.AIR);
    }
}
