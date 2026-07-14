package org.destroyermob.mobsstorage.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.destroyermob.mobsstorage.storage.StorageResolver;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin {
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return StorageResolver.allows((BaseContainerBlockEntity) (Object) this, stack);
    }
}
