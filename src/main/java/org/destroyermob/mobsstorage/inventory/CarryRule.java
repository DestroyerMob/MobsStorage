package org.destroyermob.mobsstorage.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.destroyermob.mobsstorage.storage.FilterRules;

public record CarryRule(int inventorySlot, String expression, ItemStack exactStack,
                        int minimum, int target, int maximum) {
    public static final int LEGACY_GLOBAL_SLOT = -1;
    public static final int MAX_INVENTORY_SLOT = 35;
    public static final int MAX_EXPRESSION_LENGTH = 128;
    public static final int MAX_COUNT = 9999;
    public static final Codec<CarryRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("slot", LEGACY_GLOBAL_SLOT).forGetter(CarryRule::inventorySlot),
            Codec.STRING.optionalFieldOf("expression", "").forGetter(CarryRule::expression),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("exact", ItemStack.EMPTY).forGetter(CarryRule::exactStack),
            Codec.INT.fieldOf("minimum").forGetter(CarryRule::minimum),
            Codec.INT.fieldOf("target").forGetter(CarryRule::target),
            Codec.INT.fieldOf("maximum").forGetter(CarryRule::maximum)
    ).apply(instance, CarryRule::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarryRule> STREAM_CODEC =
            StreamCodec.ofMember(CarryRule::write, CarryRule::read);

    /** Backward-compatible constructor for legacy global rules and existing integrations. */
    public CarryRule(String expression, ItemStack exactStack, int minimum, int target, int maximum) {
        this(LEGACY_GLOBAL_SLOT, expression, exactStack, minimum, target, maximum);
    }

    public CarryRule {
        inventorySlot = Math.clamp(inventorySlot, LEGACY_GLOBAL_SLOT, MAX_INVENTORY_SLOT);
        expression = expression == null ? "" : expression.strip();
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            expression = expression.substring(0, MAX_EXPRESSION_LENGTH);
        }
        exactStack = exactStack == null || exactStack.isEmpty()
                ? ItemStack.EMPTY : exactStack.copyWithCount(1);
        minimum = Math.clamp(minimum, 0, MAX_COUNT);
        target = Math.clamp(target, 0, MAX_COUNT);
        maximum = Math.clamp(maximum, 0, MAX_COUNT);
    }

    public boolean exact() {
        return !exactStack.isEmpty();
    }

    public boolean slotted() {
        return inventorySlot >= 0;
    }

    public boolean valid() {
        if (minimum > target || target > maximum) return false;
        return exact() || !expression.isBlank()
                && FilterRules.validate(List.of(expression)).isEmpty();
    }

    public boolean matches(ItemStack stack, Item.TooltipContext tooltipContext) {
        if (stack.isEmpty()) return false;
        if (exact()) return ItemStack.isSameItemSameComponents(exactStack, stack);
        return !expression.isBlank() && FilterRules.matches(stack, List.of(expression), tooltipContext);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(inventorySlot + 1);
        buffer.writeUtf(expression, MAX_EXPRESSION_LENGTH);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, exactStack);
        buffer.writeVarInt(minimum);
        buffer.writeVarInt(target);
        buffer.writeVarInt(maximum);
    }

    private static CarryRule read(RegistryFriendlyByteBuf buffer) {
        return new CarryRule(buffer.readVarInt() - 1, buffer.readUtf(MAX_EXPRESSION_LENGTH),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
    }
}
