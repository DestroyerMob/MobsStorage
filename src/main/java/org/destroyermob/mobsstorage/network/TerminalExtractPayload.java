package org.destroyermob.mobsstorage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;

public record TerminalExtractPayload(int containerId, int slot, int amount) implements CustomPacketPayload {
    public static final Type<TerminalExtractPayload> TYPE = new Type<>(MobsStorage.id("terminal_extract"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalExtractPayload> STREAM_CODEC =
            StreamCodec.ofMember(TerminalExtractPayload::write, TerminalExtractPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(slot);
        buffer.writeVarInt(amount);
    }

    private static TerminalExtractPayload read(RegistryFriendlyByteBuf buffer) {
        return new TerminalExtractPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override public Type<TerminalExtractPayload> type() { return TYPE; }
}
