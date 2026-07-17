package org.destroyermob.mobsstorage.client;

import com.mojang.datafixers.util.Either;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.inventory.BundleSelectionService;
import org.destroyermob.mobsstorage.inventory.SelectedBundleTooltip;
import org.destroyermob.mobsstorage.network.SelectBundleItemPayload;

public final class BundleSelectionControls {
    private static int lastContainerId = -1;
    private static int lastSlotId = -1;
    private static double scrollRemainder;

    private BundleSelectionControls() {
    }

    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(SelectedBundleTooltip.class, ClientSelectedBundleTooltip::new);
    }

    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (event.isCanceled()) return;
        Slot slot = selectableBundleSlot(event.getScreen(), event.getScrollDeltaY());
        if (slot == null) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        ItemStack bundle = slot.getItem();
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);

        event.setCanceled(true);
        int containerId = screen.getMenu().containerId;
        if (lastContainerId != containerId || lastSlotId != slot.index) {
            lastContainerId = containerId;
            lastSlotId = slot.index;
            scrollRemainder = 0.0D;
        }
        scrollRemainder += event.getScrollDeltaY();
        int steps = (int) scrollRemainder;
        if (steps == 0) return;
        scrollRemainder -= steps;

        int selected = BundleSelectionService.selectedItem(bundle, contents);
        int target = Math.floorMod(selected - steps, contents.size());
        BundleSelectionService.setSelectedItem(bundle, contents, target);
        slot.setChanged();
        PacketDistributor.sendToServer(new SelectBundleItemPayload(containerId, slot.index, target));
    }

    /**
     * Mouse Tweaks performs wheel transfers from the scroll post-event, even when
     * the corresponding pre-event was cancelled. Its optional compatibility mixin
     * uses this same predicate to keep bundle selection from also moving the bundle.
     */
    public static boolean handlesBundleScroll(Screen screen, double scrollDeltaY) {
        return selectableBundleSlot(screen, scrollDeltaY) != null;
    }

    private static Slot selectableBundleSlot(Screen currentScreen, double scrollDeltaY) {
        if (!(currentScreen instanceof AbstractContainerScreen<?> screen)
                || CarryRulesControls.blocksContainerShortcuts(screen)
                || scrollDeltaY == 0.0D) return null;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null) return null;
        ItemStack bundle = slot.getItem();
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        return bundle.getItem() instanceof BundleItem && bundle.getCount() == 1
                && contents != null && contents.size() >= 2 ? slot : null;
    }

    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        ItemStack bundle = event.getItemStack();
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (!(bundle.getItem() instanceof BundleItem) || contents == null) return;
        int selected = BundleSelectionService.selectedItem(bundle, contents);
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        for (int index = 0; index < elements.size(); index++) {
            TooltipComponent component = elements.get(index).right().orElse(null);
            if (component instanceof BundleTooltip tooltip) {
                elements.set(index, Either.right(new SelectedBundleTooltip(tooltip.contents(), selected)));
            }
        }
    }
}
