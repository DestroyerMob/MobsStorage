package org.destroyermob.mobsstorage.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.networking.NetworkNodeData;
import net.minecraft.world.item.ItemStack;

public record OpenLabelEditorPayload(BlockPos pos, LabelData data, NetworkNodeData node,
                                     boolean installing, List<ItemStack> contents) implements CustomPacketPayload {
    public static final Type<OpenLabelEditorPayload> TYPE = new Type<>(MobsStorage.id("open_label_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLabelEditorPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenLabelEditorPayload::write, OpenLabelEditorPayload::read);

    public OpenLabelEditorPayload {
        contents = contents.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        LabelData.STREAM_CODEC.encode(buffer, data);
        NetworkNodeData.STREAM_CODEC.encode(buffer, node);
        buffer.writeBoolean(installing);
        buffer.writeVarInt(contents.size());
        contents.forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack));
    }

    private static OpenLabelEditorPayload read(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        LabelData data = LabelData.STREAM_CODEC.decode(buffer);
        NetworkNodeData node = NetworkNodeData.STREAM_CODEC.decode(buffer);
        boolean installing = buffer.readBoolean();
        int size = buffer.readVarInt();
        List<ItemStack> contents = new ArrayList<>(Math.min(size, 256));
        for (int index = 0; index < size; index++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            if (index < 256 && !stack.isEmpty()) contents.add(stack);
        }
        return new OpenLabelEditorPayload(pos, data, node, installing, contents);
    }

    @Override
    public Type<OpenLabelEditorPayload> type() {
        return TYPE;
    }
}
