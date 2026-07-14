package org.destroyermob.mobsstorage.networking;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class StorageNetworkSavedData extends SavedData {
    private static final String FILE_ID = "mobsstorage_networks";
    private static final Factory<StorageNetworkSavedData> FACTORY =
            new Factory<>(StorageNetworkSavedData::new, StorageNetworkSavedData::load);
    private final Map<UUID, StorageNetwork> networks = new LinkedHashMap<>();

    public static StorageNetworkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Collection<StorageNetwork> all() {
        return java.util.List.copyOf(networks.values());
    }

    public Optional<StorageNetwork> get(UUID id) {
        return Optional.ofNullable(networks.get(id));
    }

    public StorageNetwork create(UUID owner, String name) {
        StorageNetwork network = new StorageNetwork(UUID.randomUUID(), owner, name);
        networks.put(network.id(), network);
        setDirty();
        return network;
    }

    public boolean delete(UUID id, UUID requester) {
        StorageNetwork network = networks.get(id);
        if (network == null || !network.isOwner(requester)) {
            return false;
        }
        networks.remove(id);
        setDirty();
        return true;
    }

    public void changed() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StorageNetwork network : networks.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", network.id());
            tag.putUUID("Owner", network.owner());
            tag.putString("Name", network.name());
            tag.putBoolean("Public", network.publicAccess());
            ListTag members = new ListTag();
            for (UUID member : network.members()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("Id", member);
                members.add(memberTag);
            }
            tag.put("Members", members);
            ListTag nodes = new ListTag();
            for (GlobalPos node : network.nodes()) {
                CompoundTag nodeTag = writeGlobalPos(node);
                StorageNetwork.NodeInfo info = network.nodeInfo(node);
                nodeTag.putString("Name", info.name());
                nodeTag.putInt("Priority", info.priority());
                nodeTag.putString("Icon", info.icon().toString());
                nodes.add(nodeTag);
            }
            tag.put("Nodes", nodes);
            network.origin().ifPresent(origin -> tag.put("Origin", writeGlobalPos(origin)));
            list.add(tag);
        }
        root.put("Networks", list);
        return root;
    }

    private static StorageNetworkSavedData load(CompoundTag root, HolderLookup.Provider registries) {
        StorageNetworkSavedData data = new StorageNetworkSavedData();
        for (Tag value : root.getList("Networks", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            if (!tag.hasUUID("Id") || !tag.hasUUID("Owner")) {
                continue;
            }
            Set<UUID> members = new LinkedHashSet<>();
            for (Tag memberValue : tag.getList("Members", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag) memberValue;
                if (member.hasUUID("Id")) {
                    members.add(member.getUUID("Id"));
                }
            }
            Map<GlobalPos, StorageNetwork.NodeInfo> nodes = new LinkedHashMap<>();
            for (Tag nodeValue : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
                CompoundTag nodeTag = (CompoundTag) nodeValue;
                readGlobalPos(nodeTag).ifPresent(node -> {
                    ResourceLocation icon = ResourceLocation.tryParse(nodeTag.getString("Icon"));
                    nodes.put(node, new StorageNetwork.NodeInfo(
                            nodeTag.getString("Name"), nodeTag.getInt("Priority"),
                            icon == null ? org.destroyermob.mobsstorage.storage.LabelData.AIR : icon));
                });
            }
            CompoundTag originTag = tag.contains("Origin", Tag.TAG_COMPOUND)
                    ? tag.getCompound("Origin") : tag.getCompound("Source");
            GlobalPos origin = readGlobalPos(originTag).orElse(null);
            StorageNetwork network = new StorageNetwork(
                    tag.getUUID("Id"), tag.getUUID("Owner"), tag.getString("Name"),
                    tag.getBoolean("Public"), members, nodes, origin);
            data.networks.put(network.id(), network);
        }
        return data;
    }

    private static CompoundTag writeGlobalPos(GlobalPos value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", value.dimension().location().toString());
        tag.putLong("Pos", value.pos().asLong());
        return tag;
    }

    private static Optional<GlobalPos> readGlobalPos(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || !tag.contains("Pos", Tag.TAG_LONG)) {
            return Optional.empty();
        }
        return Optional.of(GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(tag.getLong("Pos"))));
    }
}
