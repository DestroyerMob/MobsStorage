package org.destroyermob.mobsstorage.networking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record NetworkNodeData(UUID networkId, String name, int priority, BlockPos anchor) {
    public static final UUID NO_NETWORK = new UUID(0L, 0L);
    public static final NetworkNodeData EMPTY = new NetworkNodeData(NO_NETWORK, "", 0, BlockPos.ZERO);
    public static final Codec<NetworkNodeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("network", NO_NETWORK).forGetter(NetworkNodeData::networkId),
            Codec.STRING.optionalFieldOf("name", "").forGetter(NetworkNodeData::name),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(NetworkNodeData::priority),
            BlockPos.CODEC.optionalFieldOf("anchor", BlockPos.ZERO).forGetter(NetworkNodeData::anchor)
    ).apply(instance, NetworkNodeData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkNodeData> STREAM_CODEC =
            StreamCodec.ofMember(NetworkNodeData::write, NetworkNodeData::read);

    public NetworkNodeData {
        name = sanitizeName(name);
        priority = Math.max(-9999, Math.min(9999, priority));
    }

    public boolean linked() {
        return !NO_NETWORK.equals(networkId);
    }

    public boolean configured() {
        return linked() || !name.isBlank() || priority != 0;
    }

    public NetworkNodeData withDetails(String newName, int newPriority) {
        return new NetworkNodeData(networkId, newName, newPriority, anchor);
    }

    public static String sanitizeName(String value) {
        String trimmed = value == null ? "" : value.strip();
        return trimmed.length() > 48 ? trimmed.substring(0, 48) : trimmed;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(networkId);
        buffer.writeUtf(name, 48);
        buffer.writeInt(priority);
        buffer.writeBlockPos(anchor);
    }

    private static NetworkNodeData read(RegistryFriendlyByteBuf buffer) {
        return new NetworkNodeData(buffer.readUUID(), buffer.readUtf(48), buffer.readInt(), buffer.readBlockPos());
    }
}
