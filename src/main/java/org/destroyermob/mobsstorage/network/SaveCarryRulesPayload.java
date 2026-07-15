package org.destroyermob.mobsstorage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.inventory.CarryRuleSet;

public record SaveCarryRulesPayload(CarryRuleSet rules) implements CustomPacketPayload {
    public static final Type<SaveCarryRulesPayload> TYPE = new Type<>(MobsStorage.id("save_carry_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveCarryRulesPayload> STREAM_CODEC =
            StreamCodec.ofMember(SaveCarryRulesPayload::write, SaveCarryRulesPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        CarryRuleSet.STREAM_CODEC.encode(buffer, rules);
    }

    private static SaveCarryRulesPayload read(RegistryFriendlyByteBuf buffer) {
        return new SaveCarryRulesPayload(CarryRuleSet.STREAM_CODEC.decode(buffer));
    }

    @Override
    public Type<SaveCarryRulesPayload> type() {
        return TYPE;
    }
}
