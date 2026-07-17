package org.destroyermob.mobsstorage.inventory;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.BundleContents;

public record SelectedBundleTooltip(BundleContents contents, int selectedItem) implements TooltipComponent {
    public SelectedBundleTooltip {
        selectedItem = contents.isEmpty() ? -1 : Math.clamp(selectedItem, 0, contents.size() - 1);
    }
}
