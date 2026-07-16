package org.destroyermob.mobsstorage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.menu.TerminalSortMode;

public record TerminalQueryPayload(int containerId, String query, TerminalSortMode sortMode)
        implements CustomPacketPayload {
    public static final Type<TerminalQueryPayload> TYPE = new Type<>(MobsStorage.id("terminal_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalQueryPayload> STREAM_CODEC =
            StreamCodec.ofMember(TerminalQueryPayload::write, TerminalQueryPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUtf(query, 64);
        buffer.writeEnum(sortMode);
    }

    private static TerminalQueryPayload read(RegistryFriendlyByteBuf buffer) {
        return new TerminalQueryPayload(buffer.readVarInt(), buffer.readUtf(64),
                buffer.readEnum(TerminalSortMode.class));
    }

    @Override public Type<TerminalQueryPayload> type() { return TYPE; }
}
