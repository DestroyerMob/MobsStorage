package org.destroyermob.mobsstorage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;

public record InventoryActionPayload(Action action, int slot, int containerId) implements CustomPacketPayload {
    public static final Type<InventoryActionPayload> TYPE = new Type<>(MobsStorage.id("inventory_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(InventoryActionPayload::write, InventoryActionPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeEnum(action); buffer.writeVarInt(slot + 1); buffer.writeVarInt(containerId); }
    private static InventoryActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new InventoryActionPayload(buffer.readEnum(Action.class), buffer.readVarInt() - 1, buffer.readVarInt());
    }
    public enum Action { TOGGLE_LOCK, TOGGLE_FAVOURITE, TOGGLE_HOTBAR, TOGGLE_RESTOCK,
        SORT_ITEM, SORT_CATEGORY, SORT_QUANTITY, CONSOLIDATE, TRANSFER_MATCHING, DEPOSIT }
}
