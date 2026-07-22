package org.destroyermob.mobsstorage.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.destroyermob.mobsstorage.network.InventoryActionPayload;
import org.destroyermob.mobsstorage.networking.NetworkInventoryService;
import org.destroyermob.mobsstorage.networking.NetworkService;
import org.destroyermob.mobsstorage.networking.NetworkRefillService;
import org.destroyermob.mobsstorage.registry.ModAttachments;

public final class InventoryManagementService {
    private InventoryManagementService() {}

    public static void handle(ServerPlayer player, InventoryActionPayload payload) {
        if (player.containerMenu.containerId != payload.containerId()) return;
        InventoryProfile profile = apply(player, payload, player.getData(ModAttachments.INVENTORY_PROFILE));
        setProfile(player, profile);
    }

    public static InventoryProfile apply(ServerPlayer player, InventoryActionPayload payload, InventoryProfile profile) {
        InventoryActionPayload.Action action = payload.action();
        if (action == InventoryActionPayload.Action.TOGGLE_LOCK) {
            profile = toggleLock(player, profile, payload.slot());
        } else if (action == InventoryActionPayload.Action.TOGGLE_FAVOURITE) {
            profile = toggleFavourite(player, profile, payload.slot());
        } else if (action == InventoryActionPayload.Action.TOGGLE_HOTBAR) {
            profile = togglePreference(player, profile, payload.slot(), true);
        } else if (action == InventoryActionPayload.Action.TOGGLE_RESTOCK) {
            profile = togglePreference(player, profile, payload.slot(), false);
        } else if (action == InventoryActionPayload.Action.SORT_ITEM) {
            sort(player, profile, payload.slot(), Comparator.comparing(InventoryManagementService::itemId));
        } else if (action == InventoryActionPayload.Action.SORT_CATEGORY) {
            sort(player, profile, payload.slot(), Comparator.comparingInt(InventoryManagementService::category)
                    .thenComparing(InventoryManagementService::itemId));
        } else if (action == InventoryActionPayload.Action.SORT_QUANTITY) {
            sort(player, profile, payload.slot(), Comparator.comparingInt(ItemStack::getCount).reversed()
                    .thenComparing(InventoryManagementService::itemId));
        } else if (action == InventoryActionPayload.Action.CONSOLIDATE) {
            consolidate(player.getInventory(), movableSlots(profile));
        } else if (action == InventoryActionPayload.Action.TRANSFER_MATCHING) {
            transfer(player, profile, true);
        } else if (action == InventoryActionPayload.Action.DEPOSIT) {
            transfer(player, profile, false);
        } else if (action == InventoryActionPayload.Action.SWAP_VERTICAL_SLOT) {
            swapVerticalSlot(player, payload.slot());
        } else if (action == InventoryActionPayload.Action.SWAP_HOTBAR) {
            swapHotbar(player, payload.slot());
        } else if (action == InventoryActionPayload.Action.SWAP_HORIZONTAL_SLOT) {
            swapHorizontalSlot(player, payload.slot());
        } else if (action == InventoryActionPayload.Action.TOGGLE_LOADOUT) {
            toggleLoadout(player);
        }
        applyHotbarPreferences(player, profile);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return profile;
    }

    public static boolean blocksQuickDeposit(ServerPlayer player, AbstractContainerMenu menu, int slotId) {
        if (slotId < 0 || slotId >= menu.slots.size()) return false;
        Slot slot = menu.slots.get(slotId);
        if (!(slot.container instanceof Inventory) || slot.getItem().isEmpty()) return false;
        InventoryProfile profile = player.getExistingData(ModAttachments.INVENTORY_PROFILE)
                .orElse(InventoryProfile.EMPTY);
        return profile.lockedSlots().contains(slot.getContainerSlot())
                || profile.favourites().contains(BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        InventoryProfile profile = player.getData(ModAttachments.INVENTORY_PROFILE);
        applyHotbarPreferences(player, profile);
        Map<Integer, String> sources = new LinkedHashMap<>();
        profile.restockPreferences().forEach((slot, item) -> {
            NetworkRefillService.restockSource(player, item).ifPresent(name -> sources.put(slot, name));
            if (validInventorySlot(slot) && player.getInventory().getItem(slot).isEmpty()) {
                NetworkRefillService.refillConfiguredSlot(player, item, slot);
            }
        });
        if (!sources.equals(profile.restockSources())) {
            setProfile(player, new InventoryProfile(profile.lockedSlots(), profile.favourites(),
                    profile.hotbarPreferences(), profile.restockPreferences(), sources));
        }
    }

    private static InventoryProfile toggleLock(ServerPlayer player, InventoryProfile profile, int slot) {
        if (!validInventorySlot(slot)) return profile;
        Set<Integer> values = new LinkedHashSet<>(profile.lockedSlots());
        if (!values.remove(slot)) values.add(slot);
        return new InventoryProfile(values, profile.favourites(), profile.hotbarPreferences(),
                profile.restockPreferences(), profile.restockSources());
    }

    private static InventoryProfile toggleFavourite(ServerPlayer player, InventoryProfile profile, int slot) {
        if (!validInventorySlot(slot)) return profile;
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) return profile;
        Set<ResourceLocation> values = new LinkedHashSet<>(profile.favourites());
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!values.remove(item)) values.add(item);
        return new InventoryProfile(profile.lockedSlots(), values, profile.hotbarPreferences(),
                profile.restockPreferences(), profile.restockSources());
    }

    private static InventoryProfile togglePreference(ServerPlayer player, InventoryProfile profile, int slot, boolean hotbar) {
        if (!validInventorySlot(slot) || hotbar && slot > 8) return profile;
        Map<Integer, ResourceLocation> values = new LinkedHashMap<>(hotbar ? profile.hotbarPreferences() : profile.restockPreferences());
        if (values.containsKey(slot)) values.remove(slot);
        else {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) return profile;
            values.put(slot, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return hotbar
                ? new InventoryProfile(profile.lockedSlots(), profile.favourites(), values, profile.restockPreferences(), profile.restockSources())
                : new InventoryProfile(profile.lockedSlots(), profile.favourites(), profile.hotbarPreferences(), values, Map.of());
    }

    private static void sort(ServerPlayer player, InventoryProfile profile, int hoveredMenuSlot,
                             Comparator<ItemStack> comparator) {
        if (hoveredMenuSlot >= 0 && hoveredMenuSlot < player.containerMenu.slots.size()) {
            sortSlots(player, profile, player.containerMenu.slots.get(hoveredMenuSlot), comparator);
            return;
        }

        // Retain the original main-inventory target for server-side callers without a hovered menu slot.
        Inventory inventory = player.getInventory();
        List<Integer> slots = movableSlots(profile);
        consolidate(inventory, slots);
        List<ItemStack> stacks = slots.stream().map(inventory::getItem).filter(stack -> !stack.isEmpty())
                .sorted(comparator).toList();
        slots.forEach(slot -> inventory.setItem(slot, ItemStack.EMPTY));
        for (int index = 0; index < stacks.size(); index++) inventory.setItem(slots.get(index), stacks.get(index));
    }

    private static void sortSlots(ServerPlayer player, InventoryProfile profile, Slot hovered,
                                  Comparator<ItemStack> comparator) {
        List<Slot> section = player.containerMenu.slots.stream()
                .filter(slot -> sameInventorySection(hovered, slot))
                .toList();
        List<ItemStack> sectionStacks = section.stream().map(Slot::getItem)
                .filter(stack -> !stack.isEmpty()).toList();
        List<Slot> sortable = section.stream()
                .filter(slot -> !isLockedPlayerSlot(player, profile, slot))
                .filter(slot -> slot.getItem().isEmpty() || slot.mayPickup(player))
                .filter(slot -> sectionStacks.stream().allMatch(slot::mayPlace))
                .toList();
        if (sortable.size() < 2) return;

        List<ItemStack> original = sortable.stream().map(slot -> slot.getItem().copy()).toList();
        List<ItemStack> ordered = consolidateCopies(original);
        ordered.sort(comparator);
        if (!fits(sortable, ordered)) {
            ordered = original.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy)
                    .sorted(comparator).toList();
            if (!fits(sortable, ordered)) return;
        }

        for (int index = 0; index < sortable.size(); index++) {
            Slot slot = sortable.get(index);
            slot.set(index < ordered.size() ? ordered.get(index) : ItemStack.EMPTY);
            slot.setChanged();
        }
    }

    private static boolean sameInventorySection(Slot hovered, Slot candidate) {
        if (hovered.container instanceof Inventory inventory) {
            return candidate.container == inventory
                    && playerInventorySection(candidate.getContainerSlot()) == playerInventorySection(hovered.getContainerSlot());
        }
        if (hovered instanceof SlotItemHandler hoveredHandler) {
            return candidate instanceof SlotItemHandler candidateHandler
                    && candidateHandler.getItemHandler() == hoveredHandler.getItemHandler();
        }
        return candidate.container == hovered.container;
    }

    private static int playerInventorySection(int slot) {
        if (slot >= 0 && slot <= 8) return 0;
        if (slot >= 9 && slot <= 35) return 1;
        if (slot >= 36 && slot <= 39) return 2;
        if (slot == 40) return 3;
        return slot + 100;
    }

    private static boolean isLockedPlayerSlot(ServerPlayer player, InventoryProfile profile, Slot slot) {
        return slot.container == player.getInventory() && profile.lockedSlots().contains(slot.getContainerSlot());
    }

    private static List<ItemStack> consolidateCopies(List<ItemStack> original) {
        List<ItemStack> compact = new ArrayList<>();
        for (ItemStack originalStack : original) {
            if (originalStack.isEmpty()) continue;
            ItemStack remaining = originalStack.copy();
            for (ItemStack target : compact) {
                if (!ItemStack.isSameItemSameComponents(target, remaining)
                        || target.getCount() >= target.getMaxStackSize()) continue;
                int moved = Math.min(target.getMaxStackSize() - target.getCount(), remaining.getCount());
                target.grow(moved);
                remaining.shrink(moved);
                if (remaining.isEmpty()) break;
            }
            if (!remaining.isEmpty()) compact.add(remaining);
        }
        return compact;
    }

    private static boolean fits(List<Slot> slots, List<ItemStack> stacks) {
        if (stacks.size() > slots.size()) return false;
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            Slot slot = slots.get(index);
            if (!slot.mayPlace(stack) || stack.getCount() > slot.getMaxStackSize(stack)) return false;
        }
        return true;
    }

    private static void consolidate(Inventory inventory, List<Integer> slots) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack target = inventory.getItem(slots.get(i));
            if (target.isEmpty() || target.getCount() >= target.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                ItemStack source = inventory.getItem(slots.get(j));
                if (!ItemStack.isSameItemSameComponents(target, source)) continue;
                int moved = Math.min(target.getMaxStackSize() - target.getCount(), source.getCount());
                target.grow(moved); source.shrink(moved);
                if (source.isEmpty()) inventory.setItem(slots.get(j), ItemStack.EMPTY);
                if (target.getCount() >= target.getMaxStackSize()) break;
            }
        }
        List<ItemStack> compact = slots.stream().map(inventory::getItem).filter(stack -> !stack.isEmpty()).toList();
        slots.forEach(slot -> inventory.setItem(slot, ItemStack.EMPTY));
        for (int i = 0; i < compact.size(); i++) inventory.setItem(slots.get(i), compact.get(i));
    }

    private static void transfer(ServerPlayer player, InventoryProfile profile, boolean matchingOnly) {
        Inventory inventory = player.getInventory();
        List<Slot> targets = player.containerMenu.slots.stream().filter(slot -> slot.container != inventory).toList();
        if (targets.isEmpty()) return;
        Container openedContainer = targets.getFirst().container;
        boolean networked = NetworkService.accessibleNetwork(player, openedContainer).isPresent();
        Set<ResourceLocation> matching = new HashSet<>();
        targets.stream().map(Slot::getItem).filter(stack -> !stack.isEmpty())
                .forEach(stack -> matching.add(BuiltInRegistries.ITEM.getKey(stack.getItem())));
        for (int index = 9; index < 36; index++) {
            if (profile.lockedSlots().contains(index)) continue;
            ItemStack source = inventory.getItem(index);
            if (source.isEmpty() || profile.favourites().contains(BuiltInRegistries.ITEM.getKey(source.getItem()))) continue;
            if (matchingOnly && !matching.contains(BuiltInRegistries.ITEM.getKey(source.getItem()))) continue;
            int offeredCount = matchingOnly ? source.getCount()
                    : CarryRuleService.depositableCount(player, index, source);
            if (offeredCount <= 0) continue;
            ItemStack offered = source.copyWithCount(offeredCount);
            if (networked) {
                int inserted = NetworkInventoryService.insert(player, openedContainer, offered).inserted();
                source.shrink(inserted);
            } else {
                insertIntoSlots(targets, offered);
                source.shrink(offeredCount - offered.getCount());
            }
            if (source.isEmpty()) inventory.setItem(index, ItemStack.EMPTY);
        }
    }

    private static void insertIntoSlots(List<Slot> targets, ItemStack source) {
        for (Slot target : targets) {
            ItemStack stored = target.getItem();
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, source) && target.mayPlace(source)) {
                int moved = Math.min(Math.min(target.getMaxStackSize(source), stored.getMaxStackSize()) - stored.getCount(), source.getCount());
                if (moved > 0) { stored.grow(moved); source.shrink(moved); target.setChanged(); }
            }
        }
        for (Slot target : targets) {
            if (source.isEmpty()) break;
            if (target.getItem().isEmpty() && target.mayPlace(source)) {
                int moved = Math.min(target.getMaxStackSize(source), source.getCount());
                target.setByPlayer(source.split(moved)); target.setChanged();
            }
        }
    }

    private static void applyHotbarPreferences(ServerPlayer player, InventoryProfile profile) {
        Inventory inventory = player.getInventory();
        profile.hotbarPreferences().forEach((target, item) -> {
            if (target < 0 || target > 8 || profile.lockedSlots().contains(target) || !inventory.getItem(target).isEmpty()) return;
            for (int source = 9; source < 36; source++) {
                if (profile.lockedSlots().contains(source)) continue;
                ItemStack stack = inventory.getItem(source);
                if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                    inventory.setItem(target, stack); inventory.setItem(source, ItemStack.EMPTY); break;
                }
            }
        });
    }

    private static void swapVerticalSlot(ServerPlayer player, int target) {
        Inventory inventory = player.getInventory();
        int source = inventory.selected;
        if (source < 0 || source > 8 || target < 9 || target >= 36 || target % 9 != source) return;
        stopUsingSelectedItem(player);
        swap(inventory, source, target);
    }

    private static void swapHotbar(ServerPlayer player, int rowStart) {
        if (rowStart != 9 && rowStart != 18 && rowStart != 27) return;
        Inventory inventory = player.getInventory();
        stopUsingSelectedItem(player);
        for (int column = 0; column < 9; column++) swap(inventory, column, rowStart + column);
    }

    private static void swapHorizontalSlot(ServerPlayer player, int target) {
        Inventory inventory = player.getInventory();
        int source = inventory.selected;
        if (source < 0 || source > 8 || target < 0 || target > 8 || target == source) return;
        stopUsingSelectedItem(player);
        swap(inventory, source, target);
    }

    private static void stopUsingSelectedItem(ServerPlayer player) {
        if (player.getUsedItemHand() == InteractionHand.MAIN_HAND) player.stopUsingItem();
    }

    private static void swap(Inventory inventory, int first, int second) {
        ItemStack firstStack = inventory.getItem(first);
        ItemStack secondStack = inventory.getItem(second);
        inventory.setItem(first, secondStack);
        inventory.setItem(second, firstStack);
    }

    private static void toggleLoadout(ServerPlayer player) {
        EquipmentLoadout loadout = player.getData(ModAttachments.EQUIPMENT_LOADOUT);
        List<ItemStack> equipped = equipped(player);
        player.stopUsingItem();
        setEquipped(player, loadout.inactive());
        player.setData(ModAttachments.EQUIPMENT_LOADOUT,
                new EquipmentLoadout(!loadout.combatActive(), equipped));
        player.displayClientMessage(Component.translatable(loadout.combatActive()
                ? "message.mobsstorage.loadout.utility"
                : "message.mobsstorage.loadout.combat"), true);
    }

    private static List<ItemStack> equipped(ServerPlayer player) {
        return List.of(
                player.getMainHandItem().copy(),
                player.getOffhandItem().copy(),
                player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getItemBySlot(EquipmentSlot.CHEST).copy(),
                player.getItemBySlot(EquipmentSlot.LEGS).copy(),
                player.getItemBySlot(EquipmentSlot.FEET).copy());
    }

    private static void setEquipped(ServerPlayer player, List<ItemStack> stacks) {
        Inventory inventory = player.getInventory();
        inventory.setItem(inventory.selected, stacks.get(0).copy());
        player.setItemSlot(EquipmentSlot.OFFHAND, stacks.get(1).copy());
        player.setItemSlot(EquipmentSlot.HEAD, stacks.get(2).copy());
        player.setItemSlot(EquipmentSlot.CHEST, stacks.get(3).copy());
        player.setItemSlot(EquipmentSlot.LEGS, stacks.get(4).copy());
        player.setItemSlot(EquipmentSlot.FEET, stacks.get(5).copy());
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EquipmentLoadout loadout = player.getExistingData(ModAttachments.EQUIPMENT_LOADOUT)
                .orElse(EquipmentLoadout.EMPTY);
        for (ItemStack stack : loadout.inactive()) {
            if (!stack.isEmpty()) event.getDrops().add(new ItemEntity(
                    player.level(), player.getX(), player.getY(), player.getZ(), stack.copy()));
        }
        player.setData(ModAttachments.EQUIPMENT_LOADOUT, EquipmentLoadout.EMPTY);
    }

    private static List<Integer> movableSlots(InventoryProfile profile) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 9; slot < 36; slot++) if (!profile.lockedSlots().contains(slot)) slots.add(slot);
        return slots;
    }
    private static String itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static int category(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) return 0;
        if (stack.getItem() instanceof DiggerItem || stack.getItem() instanceof SwordItem || stack.getItem() instanceof ProjectileWeaponItem) return 1;
        if (stack.getItem() instanceof ArmorItem) return 2;
        if (stack.has(DataComponents.FOOD)) return 3;
        return 4;
    }
    private static boolean validInventorySlot(int slot) { return slot >= 0 && slot < 36; }
    private static void setProfile(ServerPlayer player, InventoryProfile profile) {
        player.setData(ModAttachments.INVENTORY_PROFILE, profile);
    }
}
