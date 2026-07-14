package org.destroyermob.mobsstorage.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;

public record OpenNetworkNodePayload(
        BlockPos pos, UUID networkId, String networkName, String storageName,
        int priority, boolean source, boolean canManage
) implements CustomPacketPayload {
    public static final Type<OpenNetworkNodePayload> TYPE = new Type<>(MobsStorage.id("open_network_node"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNetworkNodePayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenNetworkNodePayload::write, OpenNetworkNodePayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUUID(networkId);
        buffer.writeUtf(networkName, 48);
        buffer.writeUtf(storageName, 48);
        buffer.writeInt(priority);
        buffer.writeBoolean(source);
        buffer.writeBoolean(canManage);
    }

    private static OpenNetworkNodePayload read(RegistryFriendlyByteBuf buffer) {
        return new OpenNetworkNodePayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUtf(48),
                buffer.readUtf(48), buffer.readInt(), buffer.readBoolean(), buffer.readBoolean());
    }

    @Override public Type<OpenNetworkNodePayload> type() { return TYPE; }
}
