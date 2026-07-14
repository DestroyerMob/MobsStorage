package org.destroyermob.mobsstorage.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.destroyermob.mobsstorage.networking.NetworkInventoryService;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlockEntity;
import org.jetbrains.annotations.Nullable;

final class NetworkTerminalView implements Container {
    static final int COLUMNS = 9;
    static final int VISIBLE_ROWS = 5;
    static final int VISIBLE_SIZE = COLUMNS * VISIBLE_ROWS;
    private final NonNullList<ItemStack> items = NonNullList.withSize(VISIBLE_SIZE, ItemStack.EMPTY);
    @Nullable private final NetworkInterfaceBlockEntity endpoint;
    private int scrollRow;
    private int maxScrollRows;

    NetworkTerminalView(@Nullable NetworkInterfaceBlockEntity endpoint) {
        this.endpoint = endpoint;
        refresh();
    }

    void refresh() {
        if (endpoint == null) return;
        List<Entry> entries = new ArrayList<>();
        for (ItemStack stored : NetworkInventoryService.networkContents(endpoint)) {
            Entry match = entries.stream()
                    .filter(entry -> ItemStack.isSameItemSameComponents(entry.sample, stored))
                    .findFirst().orElse(null);
            if (match == null) entries.add(new Entry(stored.copyWithCount(1), stored.getCount()));
            else match.count += stored.getCount();
        }
        entries.sort(Comparator.comparing((Entry entry) ->
                        BuiltInRegistries.ITEM.getKey(entry.sample.getItem()).toString())
                .thenComparing(entry -> entry.sample.getHoverName().getString()));
        List<ItemStack> flattened = new ArrayList<>();
        for (Entry entry : entries) {
            flattened.add(entry.sample.copyWithCount(entry.count));
        }
        int totalRows = (flattened.size() + COLUMNS - 1) / COLUMNS;
        maxScrollRows = Math.max(0, totalRows - VISIBLE_ROWS);
        scrollRow = Math.min(scrollRow, maxScrollRows);
        for (int slot = 0; slot < VISIBLE_SIZE; slot++) {
            int index = scrollRow * COLUMNS + slot;
            items.set(slot, index < flattened.size() ? flattened.get(index) : ItemStack.EMPTY);
        }
    }

    void setScrollRow(int row) {
        scrollRow = Math.max(0, Math.min(row, maxScrollRows));
        refresh();
    }

    int scrollRow() {
        return scrollRow;
    }

    int maxScrollRows() {
        return maxScrollRows;
    }

    @Override public int getContainerSize() { return VISIBLE_SIZE; }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (endpoint == null) return ContainerHelper.removeItem(items, slot, amount);
        ItemStack shown = items.get(slot);
        ItemStack extracted = NetworkInventoryService.extractMatching(endpoint, shown, amount, false);
        refresh();
        return extracted;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, getItem(slot).getCount());
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (endpoint == null) {
            items.set(slot, stack);
            return;
        }
        if (!stack.isEmpty()) NetworkInventoryService.insertAutomated(endpoint, stack, false);
        refresh();
    }

    @Override public void setChanged() { }

    @Override
    public boolean stillValid(Player player) {
        return endpoint == null || endpoint.stillValid(player);
    }

    @Override
    public void clearContent() {
        if (endpoint == null) items.clear();
    }

    private static final class Entry {
        private final ItemStack sample;
        private int count;

        private Entry(ItemStack sample, int count) {
            this.sample = sample;
            this.count = count;
        }
    }
}
