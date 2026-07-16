package org.destroyermob.mobsstorage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.menu.NetworkTerminalSort;

public record UpdateTerminalViewPayload(
        int containerId,
        String query,
        NetworkTerminalSort sort,
        boolean descending
) implements CustomPacketPayload {
    public static final int MAX_QUERY_LENGTH = 128;
    public static final Type<UpdateTerminalViewPayload> TYPE =
            new Type<>(MobsStorage.id("update_terminal_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateTerminalViewPayload> STREAM_CODEC =
            StreamCodec.ofMember(UpdateTerminalViewPayload::write, UpdateTerminalViewPayload::read);

    public UpdateTerminalViewPayload {
        query = query == null ? "" : query.trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        sort = sort == null ? NetworkTerminalSort.ITEM : sort;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUtf(query, MAX_QUERY_LENGTH);
        buffer.writeVarInt(sort.id());
        buffer.writeBoolean(descending);
    }

    private static UpdateTerminalViewPayload read(RegistryFriendlyByteBuf buffer) {
        return new UpdateTerminalViewPayload(
                buffer.readVarInt(),
                buffer.readUtf(MAX_QUERY_LENGTH),
                NetworkTerminalSort.byId(buffer.readVarInt()),
                buffer.readBoolean());
    }

    @Override
    public Type<UpdateTerminalViewPayload> type() {
        return TYPE;
    }
}
