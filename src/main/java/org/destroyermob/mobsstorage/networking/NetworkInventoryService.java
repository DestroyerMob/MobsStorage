package org.destroyermob.mobsstorage.networking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
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
        StorageNetwork network = networkValue.get();
        GlobalPos currentPos = globalPos(player, current, currentNode.get());
        List<Target> targets = loadedTargets(player, network, offered, currentPos);
        int original = offered.getCount();
        for (Target target : targets) {
            insertInto(target.containers(), offered);
            if (offered.isEmpty()) break;
        }
        return new InsertResult(original - offered.getCount(), offered);
    }

    static List<Target> loadedTargets(
            ServerPlayer player, StorageNetwork network, ItemStack stack, GlobalPos current
    ) {
        List<Target> targets = new ArrayList<>();
        for (GlobalPos nodePos : network.nodes()) {
            ServerLevel level = player.server.getLevel(nodePos.dimension());
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
            int tier = nodePos.equals(current) ? 0 : explicitFilter ? 1 : 2;
            targets.add(new Target(nodePos, containers, node.get().priority(), tier));
        }
        targets.sort(Comparator.comparingInt(Target::tier)
                .thenComparing(Comparator.comparingInt(Target::priority).reversed())
                .thenComparing(target -> target.pos().toString()));
        return targets;
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
    record Target(GlobalPos pos, List<Container> containers, int priority, int tier) {}
}
