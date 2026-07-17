package org.destroyermob.mobsstorage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;

public record SelectBundleItemPayload(int containerId, int slotId, int selectedItem)
        implements CustomPacketPayload {
    public static final Type<SelectBundleItemPayload> TYPE =
            new Type<>(MobsStorage.id("select_bundle_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectBundleItemPayload> STREAM_CODEC =
            StreamCodec.ofMember(SelectBundleItemPayload::write, SelectBundleItemPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(slotId);
        buffer.writeVarInt(selectedItem);
    }

    private static SelectBundleItemPayload read(RegistryFriendlyByteBuf buffer) {
        return new SelectBundleItemPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
