package org.destroyermob.mobsstorage.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.storage.FilterRules;
import org.destroyermob.mobsstorage.storage.LabelDisplayMode;

public record SaveLabelPayload(
        BlockPos pos,
        ResourceLocation icon,
        List<String> filters,
        Direction face,
        LabelDisplayMode displayMode,
        String storageName,
        int priority,
        boolean alwaysShow
) implements CustomPacketPayload {
    public static final Type<SaveLabelPayload> TYPE = new Type<>(MobsStorage.id("save_label"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveLabelPayload> STREAM_CODEC =
            StreamCodec.ofMember(SaveLabelPayload::write, SaveLabelPayload::read);

    public SaveLabelPayload {
        filters = List.copyOf(filters);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeResourceLocation(icon);
        buffer.writeVarInt(filters.size());
        filters.forEach(filter -> buffer.writeUtf(filter, 256));
        buffer.writeEnum(face);
        buffer.writeEnum(displayMode);
        buffer.writeUtf(storageName, 48);
        buffer.writeInt(priority);
        buffer.writeBoolean(alwaysShow);
    }

    private static SaveLabelPayload read(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        ResourceLocation icon = buffer.readResourceLocation();
        int encodedSize = buffer.readVarInt();
        int size = Math.min(encodedSize, FilterRules.MAX_FILTERS + 1);
        List<String> filters = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            filters.add(buffer.readUtf(256));
        }
        for (int index = size; index < encodedSize; index++) {
            buffer.readUtf(256);
        }
        Direction face = buffer.readEnum(Direction.class);
        LabelDisplayMode displayMode = buffer.readEnum(LabelDisplayMode.class);
        return new SaveLabelPayload(pos, icon, filters, face, displayMode,
                buffer.readUtf(48), buffer.readInt(), buffer.readBoolean());
    }

    @Override
    public Type<SaveLabelPayload> type() {
        return TYPE;
    }
}
