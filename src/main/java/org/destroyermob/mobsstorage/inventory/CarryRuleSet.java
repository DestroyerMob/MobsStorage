package org.destroyermob.mobsstorage.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record CarryRuleSet(List<CarryRule> rules, int reservedEmptySlots) {
    public static final int MAX_RULES = 16;
    public static final int MAX_RESERVED_SLOTS = 9;
    public static final CarryRuleSet EMPTY = new CarryRuleSet(List.of(), 0);
    public static final Codec<CarryRuleSet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CarryRule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(CarryRuleSet::rules),
            Codec.INT.optionalFieldOf("reserved_empty_slots", 0).forGetter(CarryRuleSet::reservedEmptySlots)
    ).apply(instance, CarryRuleSet::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarryRuleSet> STREAM_CODEC =
            StreamCodec.ofMember(CarryRuleSet::write, CarryRuleSet::read);

    public CarryRuleSet {
        rules = rules == null ? List.of() : rules.stream().limit(MAX_RULES).toList();
        reservedEmptySlots = Math.clamp(reservedEmptySlots, 0, MAX_RESERVED_SLOTS);
    }

    public boolean configured() {
        return !rules.isEmpty() || reservedEmptySlots > 0;
    }

    public boolean valid() {
        return rules.size() <= MAX_RULES && rules.stream().allMatch(CarryRule::valid);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(rules.size());
        rules.forEach(rule -> CarryRule.STREAM_CODEC.encode(buffer, rule));
        buffer.writeVarInt(reservedEmptySlots);
    }

    private static CarryRuleSet read(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_RULES) throw new IllegalArgumentException("Invalid carry rule count: " + size);
        java.util.ArrayList<CarryRule> rules = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) rules.add(CarryRule.STREAM_CODEC.decode(buffer));
        return new CarryRuleSet(rules, buffer.readVarInt());
    }
}
