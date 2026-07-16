package org.destroyermob.mobsstorage.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;

public record SaveNetworkNodePayload(BlockPos pos, String name, int priority, boolean unlink, String outputFilter)
        implements CustomPacketPayload {
    public static final Type<SaveNetworkNodePayload> TYPE = new Type<>(MobsStorage.id("save_network_node"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveNetworkNodePayload> STREAM_CODEC =
            StreamCodec.ofMember(SaveNetworkNodePayload::write, SaveNetworkNodePayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(name, 48);
        buffer.writeInt(priority);
        buffer.writeBoolean(unlink);
        buffer.writeUtf(outputFilter, 256);
    }

    private static SaveNetworkNodePayload read(RegistryFriendlyByteBuf buffer) {
        return new SaveNetworkNodePayload(buffer.readBlockPos(), buffer.readUtf(48), buffer.readInt(),
                buffer.readBoolean(), buffer.readUtf(256));
    }

    @Override public Type<SaveNetworkNodePayload> type() { return TYPE; }
}
