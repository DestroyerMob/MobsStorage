package org.destroyermob.mobsstorage.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** The six equipped stacks which are currently inactive. */
public record EquipmentLoadout(boolean combatActive, List<ItemStack> inactive) {
    public static final int SLOT_COUNT = 6;
    public static final EquipmentLoadout EMPTY = new EquipmentLoadout(false, emptySlots());
    public static final Codec<EquipmentLoadout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("combat_active", false).forGetter(EquipmentLoadout::combatActive),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("inactive", List.of())
                    .forGetter(EquipmentLoadout::inactive)
    ).apply(instance, EquipmentLoadout::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentLoadout> STREAM_CODEC =
            StreamCodec.ofMember(EquipmentLoadout::write, EquipmentLoadout::read);

    public EquipmentLoadout {
        List<ItemStack> normalized = new ArrayList<>(SLOT_COUNT);
        for (int index = 0; index < SLOT_COUNT; index++) {
            normalized.add(index < inactive.size() ? inactive.get(index).copy() : ItemStack.EMPTY);
        }
        inactive = List.copyOf(normalized);
    }

    public boolean configured() {
        return combatActive || inactive.stream().anyMatch(stack -> !stack.isEmpty());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(combatActive);
        for (ItemStack stack : inactive) ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
    }

    private static EquipmentLoadout read(RegistryFriendlyByteBuf buffer) {
        boolean combatActive = buffer.readBoolean();
        List<ItemStack> inactive = new ArrayList<>(SLOT_COUNT);
        for (int index = 0; index < SLOT_COUNT; index++) {
            inactive.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        return new EquipmentLoadout(combatActive, inactive);
    }

    private static List<ItemStack> emptySlots() {
        return java.util.Collections.nCopies(SLOT_COUNT, ItemStack.EMPTY);
    }
}
