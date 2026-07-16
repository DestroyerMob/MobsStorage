package org.destroyermob.mobsstorage.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record NetworkSnapshot(
        UUID id,
        String name,
        String ownerName,
        boolean publicAccess,
        boolean owner,
        boolean member,
        boolean selected,
        List<Member> members,
        List<Node> nodes
) {
    public NetworkSnapshot {
        members = List.copyOf(members);
        nodes = List.copyOf(nodes);
    }

    static void write(RegistryFriendlyByteBuf buffer, NetworkSnapshot value) {
        buffer.writeUUID(value.id);
        buffer.writeUtf(value.name, 48);
        buffer.writeUtf(value.ownerName, 32);
        buffer.writeBoolean(value.publicAccess);
        buffer.writeBoolean(value.owner);
        buffer.writeBoolean(value.member);
        buffer.writeBoolean(value.selected);
        buffer.writeVarInt(value.members.size());
        value.members.forEach(member -> member.write(buffer));
        buffer.writeVarInt(value.nodes.size());
        value.nodes.forEach(node -> node.write(buffer));
    }

    static NetworkSnapshot read(RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(48);
        String ownerName = buffer.readUtf(32);
        boolean publicAccess = buffer.readBoolean();
        boolean owner = buffer.readBoolean();
        boolean member = buffer.readBoolean();
        boolean selected = buffer.readBoolean();
        List<Member> members = new ArrayList<>();
        int memberCount = Math.min(256, buffer.readVarInt());
        for (int index = 0; index < memberCount; index++) members.add(Member.read(buffer));
        List<Node> nodes = new ArrayList<>();
        int nodeCount = Math.min(2048, buffer.readVarInt());
        for (int index = 0; index < nodeCount; index++) nodes.add(Node.read(buffer));
        return new NetworkSnapshot(id, name, ownerName, publicAccess, owner, member, selected, members, nodes);
    }

    public record Member(UUID id, String name) {
        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(name, 32);
        }

        static Member read(RegistryFriendlyByteBuf buffer) {
            return new Member(buffer.readUUID(), buffer.readUtf(32));
        }
    }

    public record Node(GlobalPos pos, String name, int priority, ResourceLocation icon,
                       boolean origin, boolean active, boolean loaded, boolean missing) {
        void write(RegistryFriendlyByteBuf buffer) {
            GlobalPos.STREAM_CODEC.encode(buffer, pos);
            buffer.writeUtf(name, 48);
            buffer.writeInt(priority);
            buffer.writeResourceLocation(icon);
            buffer.writeBoolean(origin);
            buffer.writeBoolean(active);
            buffer.writeBoolean(loaded);
            buffer.writeBoolean(missing);
        }

        static Node read(RegistryFriendlyByteBuf buffer) {
            return new Node(GlobalPos.STREAM_CODEC.decode(buffer), buffer.readUtf(48), buffer.readInt(),
                    buffer.readResourceLocation(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean());
        }
    }
}
