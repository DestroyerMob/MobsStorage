package org.destroyermob.mobsstorage.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.storage.FilterRules;

public record SyncMenuFiltersPayload(int containerId, List<FilterGroup> groups) implements CustomPacketPayload {
    private static final int MAX_GROUPS = 64;
    private static final int MAX_MENU_SLOTS = 4096;
    public static final Type<SyncMenuFiltersPayload> TYPE = new Type<>(MobsStorage.id("sync_menu_filters"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMenuFiltersPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncMenuFiltersPayload::write, SyncMenuFiltersPayload::read);

    public SyncMenuFiltersPayload {
        groups = List.copyOf(groups);
    }

    public boolean allows(int slot, ItemStack stack, Item.TooltipContext tooltipContext) {
        for (FilterGroup group : groups) {
            if (group.slots().contains(slot)) {
                return FilterRules.matches(stack, group.filters(), tooltipContext);
            }
        }
        return true;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(groups.size());
        for (FilterGroup group : groups) {
            buffer.writeVarInt(group.filters().size());
            group.filters().forEach(filter -> buffer.writeUtf(filter, 256));
            buffer.writeVarInt(group.slots().size());
            group.slots().forEach(buffer::writeVarInt);
        }
    }

    private static SyncMenuFiltersPayload read(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        int groupCount = boundedSize(buffer.readVarInt(), MAX_GROUPS, "filter groups");
        List<FilterGroup> groups = new ArrayList<>(groupCount);
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            int filterCount = boundedSize(buffer.readVarInt(), FilterRules.MAX_FILTERS, "filters");
            List<String> filters = new ArrayList<>(filterCount);
            for (int filterIndex = 0; filterIndex < filterCount; filterIndex++) {
                filters.add(buffer.readUtf(256));
            }
            int slotCount = boundedSize(buffer.readVarInt(), MAX_MENU_SLOTS, "menu slots");
            List<Integer> slots = new ArrayList<>(slotCount);
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                slots.add(buffer.readVarInt());
            }
            groups.add(new FilterGroup(filters, slots));
        }
        return new SyncMenuFiltersPayload(containerId, groups);
    }

    private static int boundedSize(int size, int maximum, String description) {
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + description + " count: " + size);
        }
        return size;
    }

    @Override
    public Type<SyncMenuFiltersPayload> type() {
        return TYPE;
    }

    public record FilterGroup(List<String> filters, List<Integer> slots) {
        public FilterGroup {
            filters = List.copyOf(filters);
            slots = List.copyOf(slots);
        }
    }
}
