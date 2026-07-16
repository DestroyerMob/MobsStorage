package org.destroyermob.mobsstorage.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.component.DataComponentPatch;
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
    private final NonNullList<ItemStack> ingredientItems;
    private final List<NetworkInventoryService.StorageSlot> ingredientSlots;
    private final Container ingredientIndex = new IngredientIndex();
    @Nullable private final NetworkInterfaceBlockEntity endpoint;
    private int scrollRow;
    private int maxScrollRows;
    private String query = "";
    private TerminalSortMode sortMode = TerminalSortMode.NAME;
    private int loadedNodes;
    private int activeNodes;
    private int totalNodes;
    private int usedSlots;
    private int totalSlots;

    NetworkTerminalView(@Nullable NetworkInterfaceBlockEntity endpoint, int ingredientIndexSize) {
        this.endpoint = endpoint;
        this.ingredientItems = NonNullList.withSize(Math.max(0, ingredientIndexSize), ItemStack.EMPTY);
        this.ingredientSlots = endpoint == null
                ? List.of() : NetworkInventoryService.networkStorageSlots(endpoint);
        refresh();
    }

    void refresh() {
        if (endpoint == null) return;
        NetworkInventoryService.NetworkView view = NetworkInventoryService.networkView(endpoint);
        loadedNodes = view.loadedNodes();
        activeNodes = view.activeNodes();
        totalNodes = view.totalNodes();
        usedSlots = view.usedSlots();
        totalSlots = view.totalSlots();
        Map<StackKey, Entry> grouped = new LinkedHashMap<>();
        for (NetworkInventoryService.StorageSlot slot : view.slots()) {
            ItemStack stored = slot.stack();
            if (stored.isEmpty()) continue;
            StackKey key = new StackKey(stored.getItem(), stored.getComponentsPatch());
            Entry entry = grouped.computeIfAbsent(key, unused -> new Entry(stored.copyWithCount(1), 0));
            entry.count += stored.getCount();
        }
        List<Entry> entries = new ArrayList<>(grouped.values());
        if (!query.isBlank()) {
            String normalized = query.toLowerCase(Locale.ROOT);
            entries.removeIf(entry -> !matches(entry.sample, normalized));
        }
        entries.sort(comparator());
        List<ItemStack> flattened = new ArrayList<>();
        for (Entry entry : entries) {
            flattened.add(entry.sample.copyWithCount((int) Math.min(Integer.MAX_VALUE, entry.count)));
        }
        int totalRows = (flattened.size() + COLUMNS - 1) / COLUMNS;
        maxScrollRows = Math.max(0, totalRows - VISIBLE_ROWS);
        scrollRow = Math.min(scrollRow, maxScrollRows);
        int visibleStart = scrollRow * COLUMNS;
        for (int slot = 0; slot < VISIBLE_SIZE; slot++) {
            int index = visibleStart + slot;
            items.set(slot, index < flattened.size() ? flattened.get(index) : ItemStack.EMPTY);
        }
    }

    void setScrollRow(int row) {
        scrollRow = Math.max(0, Math.min(row, maxScrollRows));
        refresh();
    }

    void setQuery(String value, TerminalSortMode mode) {
        query = value == null ? "" : value.strip();
        if (query.length() > 64) query = query.substring(0, 64);
        sortMode = mode == null ? TerminalSortMode.NAME : mode;
        scrollRow = 0;
        refresh();
    }

    int loadedNodes() { return loadedNodes; }
    int activeNodes() { return activeNodes; }
    int totalNodes() { return totalNodes; }
    int usedSlots() { return usedSlots; }
    int totalSlots() { return totalSlots; }

    private boolean matches(ItemStack stack, String normalized) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return id.contains(normalized) || name.contains(normalized)
                || id.substring(0, id.indexOf(':')).contains(normalized);
    }

    private Comparator<Entry> comparator() {
        Comparator<Entry> name = Comparator.comparing((Entry entry) ->
                        entry.sample.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> BuiltInRegistries.ITEM.getKey(entry.sample.getItem()).toString());
        return switch (sortMode) {
            case NAME -> name;
            case QUANTITY -> Comparator.comparingLong((Entry entry) -> entry.count).reversed().thenComparing(name);
            case MOD -> Comparator.comparing((Entry entry) ->
                            BuiltInRegistries.ITEM.getKey(entry.sample.getItem()).getNamespace())
                    .thenComparing(name);
        };
    }

    int scrollRow() {
        return scrollRow;
    }

    int maxScrollRows() {
        return maxScrollRows;
    }

    Container ingredientIndex() {
        return ingredientIndex;
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
        // Aggregate network slots are display-only. All deposits are handled by
        // NetworkTerminalMenu so vanilla slot bookkeeping cannot write a shown
        // stack back into storage and duplicate it.
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
        private long count;

        private Entry(ItemStack sample, long count) {
            this.sample = sample;
            this.count = count;
        }
    }

    private record StackKey(net.minecraft.world.item.Item item, DataComponentPatch components) {}

    private final class IngredientIndex implements Container {
        @Override public int getContainerSize() { return ingredientItems.size(); }
        @Override
        public boolean isEmpty() {
            if (endpoint == null) return ingredientItems.stream().allMatch(ItemStack::isEmpty);
            return ingredientSlots.stream().allMatch(slot -> slot.stack().isEmpty());
        }

        @Override
        public ItemStack getItem(int slot) {
            if (endpoint == null) return ingredientItems.get(slot);
            return slot < ingredientSlots.size() ? ingredientSlots.get(slot).stack() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (endpoint == null) return ContainerHelper.removeItem(ingredientItems, slot, amount);
            if (slot >= ingredientSlots.size()) return ItemStack.EMPTY;
            ItemStack extracted = ingredientSlots.get(slot).remove(amount);
            refresh();
            return extracted;
        }

        @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, getItem(slot).getCount()); }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (endpoint == null) ingredientItems.set(slot, stack);
            else if (slot < ingredientSlots.size()) ingredientSlots.get(slot).setFromRecipeTransfer(stack);
        }

        @Override
        public void setChanged() {
            if (endpoint != null) ingredientSlots.forEach(NetworkInventoryService.StorageSlot::setChanged);
        }
        @Override public boolean stillValid(Player player) { return NetworkTerminalView.this.stillValid(player); }
        @Override public void clearContent() { if (endpoint == null) ingredientItems.clear(); }
    }
}
