package org.destroyermob.mobsstorage.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;

public record NetworkActionPayload(Action action, UUID networkId, UUID subjectId, String value)
        implements CustomPacketPayload {
    public static final UUID NONE = new UUID(0L, 0L);
    public static final Type<NetworkActionPayload> TYPE = new Type<>(MobsStorage.id("network_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(NetworkActionPayload::write, NetworkActionPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(action);
        buffer.writeUUID(networkId);
        buffer.writeUUID(subjectId);
        buffer.writeUtf(value, 48);
    }

    private static NetworkActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new NetworkActionPayload(buffer.readEnum(Action.class), buffer.readUUID(), buffer.readUUID(), buffer.readUtf(48));
    }

    @Override public Type<NetworkActionPayload> type() { return TYPE; }

    public enum Action {
        CREATE, SELECT, CLEAR_SELECTION, JOIN, LEAVE, TOGGLE_PUBLIC, RENAME, ADD_MEMBER, REMOVE_MEMBER, DELETE
    }
}
