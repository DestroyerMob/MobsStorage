package org.destroyermob.mobsstorage.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import org.destroyermob.mobsstorage.network.SelectBundleItemPayload;
import org.destroyermob.mobsstorage.registry.ModDataComponents;

public final class BundleSelectionService {
    private BundleSelectionService() {
    }

    public static int selectedItem(ItemStack bundle, BundleContents contents) {
        if (contents.isEmpty()) return -1;
        return Math.clamp(bundle.getOrDefault(ModDataComponents.BUNDLE_SELECTED_ITEM.get(), 0),
                0, contents.size() - 1);
    }

    public static void select(ServerPlayer player, SelectBundleItemPayload payload) {
        if (player.containerMenu.containerId != payload.containerId()
                || payload.slotId() < 0 || payload.slotId() >= player.containerMenu.slots.size()) return;
        Slot slot = player.containerMenu.slots.get(payload.slotId());
        ItemStack bundle = slot.getItem();
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (!(bundle.getItem() instanceof BundleItem) || bundle.getCount() != 1
                || contents == null || contents.isEmpty() || !slot.allowModification(player)) return;

        setSelectedItem(bundle, contents, payload.selectedItem());
        slot.setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void onItemStackedOnOther(ItemStackedOnOtherEvent event) {
        if (event.getClickAction() != ClickAction.SECONDARY) return;
        ItemStack carried = event.getCarriedItem();
        ItemStack stacked = event.getStackedOnItem();

        if (isBundle(carried)) {
            handleCarriedBundle(event, carried, stacked);
        } else if (isBundle(stacked) && event.getSlot().allowModification(event.getPlayer())) {
            handleStackedBundle(event, stacked, carried);
        }
    }

    private static void handleCarriedBundle(ItemStackedOnOtherEvent event, ItemStack bundle, ItemStack stacked) {
        if (stacked.isEmpty()) {
            ItemStack extracted = removeSelected(bundle);
            if (!extracted.isEmpty()) {
                ItemStack remainder = event.getSlot().safeInsert(extracted);
                if (!remainder.isEmpty()) insert(bundle, remainder);
                playRemove(event);
            }
        } else if (stacked.canFitInsideContainerItems()) {
            BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
            if (contents != null) {
                BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
                int inserted = mutable.tryTransfer(event.getSlot(), event.getPlayer());
                BundleContents updated = mutable.toImmutable();
                bundle.set(DataComponents.BUNDLE_CONTENTS, updated);
                setSelectedItem(bundle, updated, 0);
                if (inserted > 0) playInsert(event);
            }
        }
        event.setCanceled(true);
    }

    private static void handleStackedBundle(ItemStackedOnOtherEvent event, ItemStack bundle, ItemStack carried) {
        if (carried.isEmpty()) {
            ItemStack extracted = removeSelected(bundle);
            if (!extracted.isEmpty()) {
                event.getCarriedSlotAccess().set(extracted);
                playRemove(event);
            }
        } else {
            int inserted = insert(bundle, carried);
            if (inserted > 0) playInsert(event);
        }
        event.setCanceled(true);
    }

    public static ItemStack removeSelected(ItemStack bundle) {
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || contents.isEmpty()) return ItemStack.EMPTY;
        int selected = selectedItem(bundle, contents);
        List<ItemStack> items = contents.itemCopyStream().collect(ArrayList::new, List::add, List::addAll);
        ItemStack extracted = items.remove(selected);
        BundleContents updated = new BundleContents(items);
        bundle.set(DataComponents.BUNDLE_CONTENTS, updated);
        setSelectedItem(bundle, updated, Math.min(selected, updated.size() - 1));
        return extracted;
    }

    private static int insert(ItemStack bundle, ItemStack source) {
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) return 0;
        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        int inserted = mutable.tryInsert(source);
        BundleContents updated = mutable.toImmutable();
        bundle.set(DataComponents.BUNDLE_CONTENTS, updated);
        setSelectedItem(bundle, updated, 0);
        return inserted;
    }

    public static void setSelectedItem(ItemStack bundle, BundleContents contents, int selected) {
        if (contents.isEmpty()) {
            bundle.remove(ModDataComponents.BUNDLE_SELECTED_ITEM.get());
        } else {
            bundle.set(ModDataComponents.BUNDLE_SELECTED_ITEM.get(),
                    Math.clamp(selected, 0, contents.size() - 1));
        }
    }

    private static boolean isBundle(ItemStack stack) {
        return stack.getCount() == 1 && stack.getItem() instanceof BundleItem
                && stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    private static void playRemove(ItemStackedOnOtherEvent event) {
        event.getPlayer().playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F,
                0.8F + event.getPlayer().level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsert(ItemStackedOnOtherEvent event) {
        event.getPlayer().playSound(SoundEvents.BUNDLE_INSERT, 0.8F,
                0.8F + event.getPlayer().level().getRandom().nextFloat() * 0.4F);
    }
}
