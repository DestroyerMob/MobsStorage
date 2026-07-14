package org.destroyermob.mobsstorage.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.item.NetworkWandItem;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MobsStorage.MOD_ID);

    public static final Supplier<Item> STORAGE_LABEL = ITEMS.registerSimpleItem(
            "storage_label",
            new Item.Properties().stacksTo(64)
    );

    public static final Supplier<Item> NETWORK_WAND = ITEMS.register(
            "network_wand", () -> new NetworkWandItem(new Item.Properties().stacksTo(1)));

    public static final Supplier<BlockItem> NETWORK_INTERFACE = ITEMS.register(
            ModBlocks.NETWORK_INTERFACE_ID,
            () -> new BlockItem(ModBlocks.NETWORK_INTERFACE.get(), new Item.Properties())
    );

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
