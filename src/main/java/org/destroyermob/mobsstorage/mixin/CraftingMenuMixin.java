package org.destroyermob.mobsstorage.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.destroyermob.mobsstorage.crafting.NearbyCraftingMenuAccess;
import org.destroyermob.mobsstorage.crafting.NearbyCraftingSlot;
import org.destroyermob.mobsstorage.crafting.NearbyCraftingSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin extends RecipeBookMenu<CraftingInput, CraftingRecipe>
        implements NearbyCraftingMenuAccess {
    protected CraftingMenuMixin(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Unique
    private NearbyCraftingSources mobsstorage$nearbyCraftingSources;

    @Unique
    private final List<Slot> mobsstorage$nearbyCraftingSlots = new ArrayList<>();

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void mobsstorage$addNearbySources(
            int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo callback
    ) {
        mobsstorage$nearbyCraftingSources = new NearbyCraftingSources(access);
        for (int slot = 0; slot < NearbyCraftingSources.MENU_SLOT_COUNT; slot++) {
            mobsstorage$nearbyCraftingSlots.add(addSlot(new NearbyCraftingSlot(mobsstorage$nearbyCraftingSources, slot)));
        }
    }

    @Inject(method = "fillCraftSlotsStackedContents", at = @At("TAIL"))
    private void mobsstorage$countNearbySources(StackedContents contents, CallbackInfo callback) {
        mobsstorage$nearbyCraftingSources.fillStackedContents(contents);
    }

    @Override
    public NearbyCraftingSources mobsstorage$getNearbyCraftingSources() {
        return mobsstorage$nearbyCraftingSources;
    }

    @Override
    public List<Slot> mobsstorage$getNearbyCraftingSlots() {
        return mobsstorage$nearbyCraftingSlots.stream().filter(Slot::hasItem).toList();
    }
}
