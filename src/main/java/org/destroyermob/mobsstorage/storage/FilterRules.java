package org.destroyermob.mobsstorage.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FilterRules {
    public static final int MAX_FILTERS = 64;

    private FilterRules() {
    }

    public static List<String> normalize(String input) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        input.lines().map(String::trim).filter(line -> !line.isEmpty()).limit(MAX_FILTERS).forEach(values::add);
        return List.copyOf(values);
    }

    public static Optional<Component> validateIcon(ResourceLocation icon) {
        if (!BuiltInRegistries.ITEM.containsKey(icon) || BuiltInRegistries.ITEM.get(icon) == Items.AIR) {
            return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_item", icon));
        }
        return Optional.empty();
    }

    public static Optional<Component> validate(List<String> filters) {
        if (filters.size() > MAX_FILTERS) {
            return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_entry", "too many entries"));
        }
        for (String filter : filters) {
            boolean tag = filter.startsWith("#");
            String raw = tag ? filter.substring(1) : filter;
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null || raw.isBlank()) {
                return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_entry", filter));
            }
            if (tag) {
                TagKey<Item> key = TagKey.create(Registries.ITEM, id);
                if (BuiltInRegistries.ITEM.getTag(key).isEmpty()) {
                    return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_tag", filter));
                }
            } else if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
                return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_item", filter));
            }
        }
        return Optional.empty();
    }

    public static boolean matches(ItemStack stack, List<String> filters) {
        if (stack.isEmpty() || filters.isEmpty()) {
            return true;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String filter : filters) {
            if (filter.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(filter.substring(1));
                if (id != null && stack.is(TagKey.create(Registries.ITEM, id))) {
                    return true;
                }
            } else if (itemId.equals(ResourceLocation.tryParse(filter))) {
                return true;
            }
        }
        return false;
    }
}
