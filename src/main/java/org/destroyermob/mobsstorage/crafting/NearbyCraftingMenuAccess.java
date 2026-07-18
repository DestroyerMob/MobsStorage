package org.destroyermob.mobsstorage.crafting;

import java.util.List;
import net.minecraft.world.inventory.Slot;

/** Added to vanilla crafting-table menus so recipe helpers can discover nearby storage. */
public interface NearbyCraftingMenuAccess {
    NearbyCraftingSources mobsstorage$getNearbyCraftingSources();

    List<Slot> mobsstorage$getNearbyCraftingSlots();
}
