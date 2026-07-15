package org.destroyermob.mobsstorage.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.network.SyncMenuFiltersPayload;

public final class StorageMenuFilterSync {
    private StorageMenuFilterSync() {
    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, createPayload(event.getContainer()));
        }
    }

    public static SyncMenuFiltersPayload createPayload(AbstractContainerMenu menu) {
        Map<List<String>, List<Integer>> groupedSlots = new LinkedHashMap<>();
        for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
            List<String> filters = StorageResolver.labelFor(menu.slots.get(slotIndex).container)
                    .map(LabelData::filters)
                    .orElse(List.of());
            if (!filters.isEmpty()) {
                groupedSlots.computeIfAbsent(filters, ignored -> new ArrayList<>()).add(slotIndex);
            }
        }

        List<SyncMenuFiltersPayload.FilterGroup> groups = groupedSlots.entrySet().stream()
                .map(entry -> new SyncMenuFiltersPayload.FilterGroup(entry.getKey(), entry.getValue()))
                .toList();
        return new SyncMenuFiltersPayload(menu.containerId, groups);
    }
}
