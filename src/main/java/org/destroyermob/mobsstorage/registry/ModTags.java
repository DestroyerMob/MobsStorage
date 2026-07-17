package org.destroyermob.mobsstorage.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.destroyermob.mobsstorage.MobsStorage;

public final class ModTags {
    public static final TagKey<Block> LABELABLE_STORAGE =
            TagKey.create(Registries.BLOCK, MobsStorage.id("labelable_storage"));
    public static final TagKey<Block> NETWORK_STORAGE =
            TagKey.create(Registries.BLOCK, MobsStorage.id("network_storage"));

    private ModTags() {
    }
}
