package org.destroyermob.mobsstorage.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** An off-screen, extraction-only view of one real nearby storage slot. */
public final class NearbyCraftingSlot extends Slot {
    public NearbyCraftingSlot(NearbyCraftingSources sources, int slot) {
        super(sources, slot, -10_000, -10_000);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return !getItem().isEmpty();
    }

    @Override
    public boolean allowModification(Player player) {
        return mayPickup(player);
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean isHighlightable() {
        return false;
    }
}
