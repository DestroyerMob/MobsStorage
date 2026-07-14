package org.destroyermob.mobsstorage.networking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.destroyermob.mobsstorage.mixin.CompoundContainerAccessor;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.StorageResolver;

public final class NetworkInventoryService {
    private NetworkInventoryService() {
    }

    public static boolean handleMenuClick(
            AbstractContainerMenu menu, int slotId, int button, ClickType clickType, ServerPlayer player
    ) {
        if (slotId < 0 || slotId >= menu.slots.size()) return false;
        Slot clicked = menu.slots.get(slotId);
        if (clickType == ClickType.PICKUP && !menu.getCarried().isEmpty()) {
            return routeCarried(menu, clicked.container, button == 1 ? 1 : menu.getCarried().getCount(), player);
        }
        if (clickType == ClickType.SWAP && NetworkService.nodeFor(clicked.container).isPresent()) {
            int inventorySlot = button == 40 ? 40 : button;
            if (inventorySlot < 0 || inventorySlot >= player.getInventory().getContainerSize()) return false;
            ItemStack source = player.getInventory().getItem(inventorySlot);
            if (source.isEmpty()) return false;
            int inserted = insert(player, clicked.container, source.copy()).inserted();
            if (inserted <= 0) return false;
            source.shrink(inserted);
            if (source.isEmpty()) player.getInventory().setItem(inventorySlot, ItemStack.EMPTY);
            player.getInventory().setChanged();
            menu.broadcastChanges();
            return true;
        }
        if (clickType == ClickType.QUICK_MOVE && clicked.container instanceof Inventory && clicked.hasItem()) {
            Optional<Container> target = menu.slots.stream().map(slot -> slot.container)
                    .filter(container -> !(container instanceof Inventory))
                    .filter(container -> NetworkService.accessibleNetwork(player, container).isPresent())
                    .findFirst();
            if (target.isEmpty()) return false;
            ItemStack source = clicked.getItem();
            int inserted = insert(player, target.get(), source.copy()).inserted();
            if (inserted <= 0) return false;
            source.shrink(inserted);
            if (source.isEmpty()) clicked.setByPlayer(ItemStack.EMPTY);
            clicked.setChanged();
            menu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static boolean routeCarried(
            AbstractContainerMenu menu, Container target, int requested, ServerPlayer player
    ) {
        if (NetworkService.accessibleNetwork(player, target).isEmpty()) return false;
        ItemStack carried = menu.getCarried();
        ItemStack offered = carried.copyWithCount(Math.min(requested, carried.getCount()));
        int inserted = insert(player, target, offered).inserted();
        if (inserted <= 0) return false;
        carried.shrink(inserted);
        menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        menu.broadcastChanges();
        return true;
    }

    public static InsertResult insert(ServerPlayer player, Container current, ItemStack offered) {
        Optional<NetworkNodeData> currentNode = NetworkService.nodeFor(current);
        Optional<StorageNetwork> networkValue = NetworkService.accessibleNetwork(player, current);
        if (currentNode.isEmpty() || networkValue.isEmpty() || offered.isEmpty()) return new InsertResult(0, offered);
        GlobalPos currentPos = globalPos(player, current, currentNode.get());
        return insert(player.server, networkValue.get(), offered, currentPos, false);
    }

    public static Optional<InsertResult> insertAutomated(Container current, ItemStack offered, boolean simulate) {
        return automationContext(current).map(context ->
                insert(context.level().getServer(), context.network(), offered, context.current(), simulate));
    }

    public static Optional<Boolean> acceptsAutomated(Container current, ItemStack stack) {
        return automationContext(current).map(context -> loadedTargets(
                        context.level().getServer(), context.network(), stack, context.current()).stream()
                .flatMap(target -> target.containers().stream())
                .anyMatch(container -> canEverPlace(container, stack)));
    }

    public static List<ItemStack> networkContents(Container endpoint) {
        return automatedSlots(endpoint).orElse(List.of()).stream()
                .map(StorageSlot::stack)
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    public static int networkSlotCount(Container endpoint) {
        return automatedSlots(endpoint).orElse(List.of()).size();
    }

    public static ItemStack extractMatching(
            Container endpoint, ItemStack template, int amount, boolean simulate
    ) {
        if (template.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int moved = 0;
        for (StorageSlot slot : automatedSlots(endpoint).orElse(List.of())) {
            ItemStack stored = slot.stack();
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, template)) {
                int take = Math.min(amount - moved, stored.getCount());
                if (!simulate) slot.container().removeItem(slot.slot(), take);
                moved += take;
                if (moved >= amount) break;
            }
        }
        return moved == 0 ? ItemStack.EMPTY : template.copyWithCount(moved);
    }

    public static ItemStack extractFirst(Container endpoint, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        for (StorageSlot slot : automatedSlots(endpoint).orElse(List.of())) {
            ItemStack stored = slot.stack();
            if (stored.isEmpty()) continue;
            int moved = Math.min(amount, Math.min(stored.getCount(), stored.getMaxStackSize()));
            return simulate ? stored.copyWithCount(moved) : slot.container().removeItem(slot.slot(), moved);
        }
        return ItemStack.EMPTY;
    }

    static Optional<List<StorageSlot>> automatedSlots(Container endpoint) {
        return automationContext(endpoint).map(NetworkInventoryService::loadedStorageSlots);
    }

    private static List<StorageSlot> loadedStorageSlots(AutomationContext context) {
        List<StorageSlot> result = new ArrayList<>();
        List<GlobalPos> nodes = context.network().nodes().stream()
                .sorted(Comparator.comparingInt((GlobalPos pos) -> context.network().nodeInfo(pos).priority())
                        .reversed().thenComparing(GlobalPos::toString))
                .toList();
        for (GlobalPos nodePos : nodes) {
            ServerLevel level = context.level().getServer().getLevel(nodePos.dimension());
            if (level == null || !level.isLoaded(nodePos.pos())) continue;
            Optional<NetworkNodeData> node = NetworkService.findNode(level, nodePos.pos())
                    .filter(value -> value.networkId().equals(context.network().id()));
            if (node.isEmpty()) continue;
            for (BlockEntity blockEntity : StorageResolver.logicalStorage(level, node.get().anchor())) {
                if (!(blockEntity instanceof Container container)) continue;
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    result.add(new StorageSlot(container, slot));
                }
            }
        }
        return List.copyOf(result);
    }

    private static InsertResult insert(
            MinecraftServer server, StorageNetwork network, ItemStack offered, GlobalPos current, boolean simulate
    ) {
        ItemStack remainder = offered.copy();
        List<Target> targets = loadedTargets(server, network, remainder, current);
        int original = remainder.getCount();
        for (Target target : targets) {
            if (simulate) simulateInsertInto(target.containers(), remainder);
            else insertInto(target.containers(), remainder);
            if (remainder.isEmpty()) break;
        }
        return new InsertResult(original - remainder.getCount(), remainder);
    }

    static List<Target> loadedTargets(
            MinecraftServer server, StorageNetwork network, ItemStack stack, GlobalPos current
    ) {
        List<Target> targets = new ArrayList<>();
        for (GlobalPos nodePos : network.nodes()) {
            ServerLevel level = server.getLevel(nodePos.dimension());
            if (level == null || !level.isLoaded(nodePos.pos())) continue;
            Optional<NetworkNodeData> node = NetworkService.findNode(level, nodePos.pos())
                    .filter(value -> value.networkId().equals(network.id()));
            if (node.isEmpty()) continue;
            List<BlockEntity> storage = StorageResolver.logicalStorage(level, node.get().anchor());
            List<Container> containers = storage.stream().filter(Container.class::isInstance)
                    .map(Container.class::cast).toList();
            if (containers.isEmpty()) continue;
            Optional<LabelData> label = StorageResolver.findLabel(level, node.get().anchor());
            if (label.isPresent() && !label.get().allows(stack, level)) continue;
            boolean explicitFilter = label.filter(value -> !value.filters().isEmpty()).isPresent();
            int tier = explicitFilter ? 0 : nodePos.equals(current) ? 1 : 2;
            targets.add(new Target(nodePos, containers, node.get().priority(), tier));
        }
        targets.sort(Comparator.comparingInt(Target::tier)
                .thenComparing(Comparator.comparingInt(Target::priority).reversed())
                .thenComparing(target -> target.pos().toString()));
        return targets;
    }

    private static Optional<AutomationContext> automationContext(Container current) {
        Optional<NetworkNodeData> node = NetworkService.nodeFor(current);
        BlockEntity blockEntity = firstBlockEntity(current);
        if (node.isEmpty() || blockEntity == null || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return StorageNetworkSavedData.get(level.getServer()).get(node.get().networkId())
                .map(network -> new AutomationContext(level, network,
                        GlobalPos.of(level.dimension(), node.get().anchor())));
    }

    private static boolean canEverPlace(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.canPlaceItem(slot, stack)) return true;
        }
        return false;
    }

    private static void simulateInsertInto(List<Container> containers, ItemStack stack) {
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                ItemStack existing = container.getItem(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)
                        && container.canPlaceItem(slot, stack)) {
                    int capacity = Math.min(container.getMaxStackSize(), existing.getMaxStackSize()) - existing.getCount();
                    stack.shrink(Math.min(capacity, stack.getCount()));
                }
            }
        }
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, stack)) {
                    int capacity = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                    stack.shrink(Math.min(capacity, stack.getCount()));
                }
            }
        }
    }

    static void insertInto(List<Container> containers, ItemStack stack) {
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                ItemStack existing = container.getItem(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)
                        && container.canPlaceItem(slot, stack)) {
                    int capacity = Math.min(container.getMaxStackSize(), existing.getMaxStackSize()) - existing.getCount();
                    int moved = Math.min(capacity, stack.getCount());
                    if (moved > 0) {
                        existing.grow(moved);
                        stack.shrink(moved);
                    }
                }
            }
        }
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, stack)) {
                    int moved = Math.min(stack.getCount(), Math.min(container.getMaxStackSize(), stack.getMaxStackSize()));
                    container.setItem(slot, stack.split(moved));
                }
            }
        }
        containers.forEach(Container::setChanged);
    }

    static GlobalPos globalPos(ServerPlayer player, Container container, NetworkNodeData node) {
        BlockEntity blockEntity = firstBlockEntity(container);
        return GlobalPos.of(blockEntity == null ? player.serverLevel().dimension() : blockEntity.getLevel().dimension(), node.anchor());
    }

    private static BlockEntity firstBlockEntity(Container container) {
        if (container instanceof BlockEntity blockEntity) return blockEntity;
        if (container instanceof CompoundContainer compound) {
            Container first = ((CompoundContainerAccessor) compound).mobsstorage$getFirst();
            return first instanceof BlockEntity blockEntity ? blockEntity : null;
        }
        return null;
    }

    public record InsertResult(int inserted, ItemStack remainder) {}
    record StorageSlot(Container container, int slot) {
        ItemStack stack() {
            return container.getItem(slot);
        }
    }
    private record AutomationContext(ServerLevel level, StorageNetwork network, GlobalPos current) {}
    record Target(GlobalPos pos, List<Container> containers, int priority, int tier) {}
}
