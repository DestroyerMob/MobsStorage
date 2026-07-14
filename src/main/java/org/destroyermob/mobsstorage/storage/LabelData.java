package org.destroyermob.mobsstorage.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record LabelData(
        ResourceLocation icon,
        List<String> filters,
        Direction face,
        boolean alwaysShow,
        BlockPos anchor
) {
    public static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("air");
    public static final LabelData EMPTY = new LabelData(AIR, List.of(), Direction.NORTH, false, BlockPos.ZERO);

    public static final Codec<LabelData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("icon").forGetter(LabelData::icon),
            Codec.STRING.listOf().fieldOf("filters").forGetter(LabelData::filters),
            Direction.CODEC.fieldOf("face").forGetter(LabelData::face),
            Codec.BOOL.fieldOf("always_show").forGetter(LabelData::alwaysShow),
            BlockPos.CODEC.fieldOf("anchor").forGetter(LabelData::anchor)
    ).apply(instance, LabelData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LabelData> STREAM_CODEC =
            StreamCodec.ofMember(LabelData::write, LabelData::read);

    public LabelData {
        filters = List.copyOf(filters);
    }

    public boolean configured() {
        return !AIR.equals(icon);
    }

    public boolean allows(ItemStack stack) {
        return !configured() || FilterRules.matches(stack, filters);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(icon);
        buffer.writeVarInt(filters.size());
        filters.forEach(filter -> buffer.writeUtf(filter, 256));
        buffer.writeEnum(face);
        buffer.writeBoolean(alwaysShow);
        buffer.writeBlockPos(anchor);
    }

    private static LabelData read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation icon = buffer.readResourceLocation();
        int size = Math.min(buffer.readVarInt(), FilterRules.MAX_FILTERS);
        java.util.ArrayList<String> filters = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            filters.add(buffer.readUtf(256));
        }
        return new LabelData(icon, filters, buffer.readEnum(Direction.class), buffer.readBoolean(), buffer.readBlockPos());
    }
}
