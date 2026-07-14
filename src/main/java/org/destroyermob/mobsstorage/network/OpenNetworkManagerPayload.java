package org.destroyermob.mobsstorage.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;

public record OpenNetworkManagerPayload(List<NetworkSnapshot> networks) implements CustomPacketPayload {
    public static final Type<OpenNetworkManagerPayload> TYPE = new Type<>(MobsStorage.id("open_network_manager"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNetworkManagerPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenNetworkManagerPayload::write, OpenNetworkManagerPayload::read);

    public OpenNetworkManagerPayload { networks = List.copyOf(networks); }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(networks.size());
        networks.forEach(network -> NetworkSnapshot.write(buffer, network));
    }

    private static OpenNetworkManagerPayload read(RegistryFriendlyByteBuf buffer) {
        int size = Math.min(512, buffer.readVarInt());
        List<NetworkSnapshot> networks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) networks.add(NetworkSnapshot.read(buffer));
        return new OpenNetworkManagerPayload(networks);
    }

    @Override public Type<OpenNetworkManagerPayload> type() { return TYPE; }
}
