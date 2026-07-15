package org.destroyermob.mobsstorage.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record InventoryProfile(Set<Integer> lockedSlots, Set<ResourceLocation> favourites,
                               Map<Integer, ResourceLocation> hotbarPreferences,
                               Map<Integer, ResourceLocation> restockPreferences,
                               Map<Integer, String> restockSources) {
    public static final InventoryProfile EMPTY = new InventoryProfile(Set.of(), Set.of(), Map.of(), Map.of(), Map.of());
    private static final Codec<Preference> PREFERENCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(Preference::slot),
            ResourceLocation.CODEC.fieldOf("item").forGetter(Preference::item)
    ).apply(instance, Preference::new));
    private static final Codec<Source> SOURCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(Source::slot),
            Codec.STRING.fieldOf("network").forGetter(Source::network)
    ).apply(instance, Source::new));
    public static final Codec<InventoryProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().optionalFieldOf("locked_slots", List.of()).forGetter(value -> List.copyOf(value.lockedSlots)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("favourites", List.of()).forGetter(value -> List.copyOf(value.favourites)),
            PREFERENCE_CODEC.listOf().optionalFieldOf("hotbar", List.of()).forGetter(value -> preferences(value.hotbarPreferences)),
            PREFERENCE_CODEC.listOf().optionalFieldOf("restock", List.of()).forGetter(value -> preferences(value.restockPreferences)),
            SOURCE_CODEC.listOf().optionalFieldOf("restock_sources", List.of()).forGetter(value -> sources(value.restockSources))
    ).apply(instance, (locked, favourites, hotbar, restock, sources) -> new InventoryProfile(
            Set.copyOf(locked), Set.copyOf(favourites), preferenceMap(hotbar), preferenceMap(restock), sourceMap(sources))));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryProfile> STREAM_CODEC =
            StreamCodec.ofMember(InventoryProfile::write, InventoryProfile::read);

    public InventoryProfile {
        lockedSlots = Set.copyOf(lockedSlots);
        favourites = Set.copyOf(favourites);
        hotbarPreferences = Map.copyOf(hotbarPreferences);
        restockPreferences = Map.copyOf(restockPreferences);
        restockSources = Map.copyOf(restockSources);
    }

    public boolean configured() {
        return !lockedSlots.isEmpty() || !favourites.isEmpty() || !hotbarPreferences.isEmpty()
                || !restockPreferences.isEmpty();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(lockedSlots.size());
        lockedSlots.forEach(buffer::writeVarInt);
        buffer.writeVarInt(favourites.size());
        favourites.forEach(buffer::writeResourceLocation);
        writePreferences(buffer, hotbarPreferences);
        writePreferences(buffer, restockPreferences);
        buffer.writeVarInt(restockSources.size());
        restockSources.forEach((slot, source) -> { buffer.writeVarInt(slot); buffer.writeUtf(source, 48); });
    }

    private static InventoryProfile read(RegistryFriendlyByteBuf buffer) {
        Set<Integer> locked = new LinkedHashSet<>();
        for (int i = 0, size = buffer.readVarInt(); i < size; i++) locked.add(buffer.readVarInt());
        Set<ResourceLocation> favourites = new LinkedHashSet<>();
        for (int i = 0, size = buffer.readVarInt(); i < size; i++) favourites.add(buffer.readResourceLocation());
        Map<Integer, ResourceLocation> hotbar = readPreferences(buffer);
        Map<Integer, ResourceLocation> restock = readPreferences(buffer);
        Map<Integer, String> sources = new LinkedHashMap<>();
        for (int i = 0, size = buffer.readVarInt(); i < size; i++) sources.put(buffer.readVarInt(), buffer.readUtf(48));
        return new InventoryProfile(locked, favourites, hotbar, restock, sources);
    }

    private static void writePreferences(RegistryFriendlyByteBuf buffer, Map<Integer, ResourceLocation> values) {
        buffer.writeVarInt(values.size());
        values.forEach((slot, item) -> { buffer.writeVarInt(slot); buffer.writeResourceLocation(item); });
    }

    private static Map<Integer, ResourceLocation> readPreferences(RegistryFriendlyByteBuf buffer) {
        Map<Integer, ResourceLocation> values = new LinkedHashMap<>();
        for (int i = 0, size = buffer.readVarInt(); i < size; i++) values.put(buffer.readVarInt(), buffer.readResourceLocation());
        return values;
    }

    private static List<Preference> preferences(Map<Integer, ResourceLocation> values) {
        return values.entrySet().stream().map(entry -> new Preference(entry.getKey(), entry.getValue())).toList();
    }
    private static List<Source> sources(Map<Integer, String> values) {
        return values.entrySet().stream().map(entry -> new Source(entry.getKey(), entry.getValue())).toList();
    }
    private static Map<Integer, ResourceLocation> preferenceMap(List<Preference> values) {
        Map<Integer, ResourceLocation> result = new LinkedHashMap<>(); values.forEach(value -> result.put(value.slot, value.item)); return result;
    }
    private static Map<Integer, String> sourceMap(List<Source> values) {
        Map<Integer, String> result = new LinkedHashMap<>(); values.forEach(value -> result.put(value.slot, value.network)); return result;
    }
    private record Preference(int slot, ResourceLocation item) {}
    private record Source(int slot, String network) {}
}
