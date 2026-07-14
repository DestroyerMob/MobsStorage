package org.destroyermob.mobsstorage.networking;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import org.destroyermob.mobsstorage.storage.LabelData;

public final class StorageNetwork {
    private final UUID id;
    private final UUID owner;
    private String name;
    private boolean publicAccess;
    private final Set<UUID> members;
    private final Map<GlobalPos, NodeInfo> nodes;
    private GlobalPos origin;

    public StorageNetwork(UUID id, UUID owner, String name) {
        this(id, owner, name, false, new LinkedHashSet<>(), new LinkedHashMap<>(), null);
    }

    public StorageNetwork(
            UUID id,
            UUID owner,
            String name,
            boolean publicAccess,
            Set<UUID> members,
            Map<GlobalPos, NodeInfo> nodes,
            GlobalPos origin
    ) {
        this.id = id;
        this.owner = owner;
        this.name = sanitizeName(name);
        this.publicAccess = publicAccess;
        this.members = new LinkedHashSet<>(members);
        this.members.remove(owner);
        this.nodes = new LinkedHashMap<>(nodes);
        this.origin = nodes.containsKey(origin) ? origin : null;
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public String name() { return name; }
    public boolean publicAccess() { return publicAccess; }
    public Set<UUID> members() { return Set.copyOf(members); }
    public Set<GlobalPos> nodes() { return Set.copyOf(nodes.keySet()); }
    public NodeInfo nodeInfo(GlobalPos node) { return nodes.getOrDefault(node, NodeInfo.EMPTY); }
    public Optional<GlobalPos> origin() { return Optional.ofNullable(origin); }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public boolean isMember(UUID player) {
        return isOwner(player) || members.contains(player);
    }

    void setName(String value) { name = sanitizeName(value); }
    void setPublicAccess(boolean value) { publicAccess = value; }
    boolean addMember(UUID player) { return !owner.equals(player) && members.add(player); }
    boolean removeMember(UUID player) { return members.remove(player); }
    boolean addNode(GlobalPos node) { return nodes.putIfAbsent(node, NodeInfo.EMPTY) == null; }

    void updateNode(GlobalPos node, String name, int priority, ResourceLocation icon) {
        if (nodes.containsKey(node)) {
            nodes.put(node, new NodeInfo(NetworkNodeData.sanitizeName(name), priority, icon));
        }
    }

    boolean removeNode(GlobalPos node) {
        if (node.equals(origin)) {
            origin = null;
        }
        return nodes.remove(node) != null;
    }

    void setOrigin(GlobalPos value) {
        origin = nodes.containsKey(value) ? value : null;
    }

    private static String sanitizeName(String value) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isBlank()) {
            return "Storage Network";
        }
        return trimmed.length() > 48 ? trimmed.substring(0, 48) : trimmed;
    }

    public record NodeInfo(String name, int priority, ResourceLocation icon) {
        public static final NodeInfo EMPTY = new NodeInfo("", 0, LabelData.AIR);
    }
}
