package org.destroyermob.mobsstorage.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.networking.NetworkNodeData;

public record OpenLabelEditorPayload(BlockPos pos, LabelData data, NetworkNodeData node, boolean installing) implements CustomPacketPayload {
    public static final Type<OpenLabelEditorPayload> TYPE = new Type<>(MobsStorage.id("open_label_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLabelEditorPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenLabelEditorPayload::write, OpenLabelEditorPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        LabelData.STREAM_CODEC.encode(buffer, data);
        NetworkNodeData.STREAM_CODEC.encode(buffer, node);
        buffer.writeBoolean(installing);
    }

    private static OpenLabelEditorPayload read(RegistryFriendlyByteBuf buffer) {
        return new OpenLabelEditorPayload(buffer.readBlockPos(), LabelData.STREAM_CODEC.decode(buffer),
                NetworkNodeData.STREAM_CODEC.decode(buffer), buffer.readBoolean());
    }

    @Override
    public Type<OpenLabelEditorPayload> type() {
        return TYPE;
    }
}
