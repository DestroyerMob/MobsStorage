package org.destroyermob.mobsstorage.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.destroyermob.mobsstorage.MobsStorage;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MobsStorage.MOD_ID);

    public static final Supplier<Item> STORAGE_LABEL = ITEMS.registerSimpleItem(
            "storage_label",
            new Item.Properties().stacksTo(64)
    );

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
