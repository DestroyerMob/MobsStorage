package org.destroyermob.mobsstorage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.destroyermob.mobsstorage.client.MobsStorageClient;
import org.destroyermob.mobsstorage.network.ModNetworking;
import org.destroyermob.mobsstorage.registry.ModAttachments;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.storage.StorageLabelEvents;

@Mod(MobsStorage.MOD_ID)
public final class MobsStorage {
    public static final String MOD_ID = "mobsstorage";

    public MobsStorage(IEventBus modBus, ModContainer container) {
        ModItems.register(modBus);
        ModAttachments.register(modBus);
        ModNetworking.register(modBus);
        modBus.addListener(this::addCreativeTabItems);

        NeoForge.EVENT_BUS.addListener(StorageLabelEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(StorageLabelEvents::onBreakBlock);
        NeoForge.EVENT_BUS.addListener(StorageLabelEvents::onBlockPlaced);

        if (FMLEnvironment.dist.isClient()) {
            MobsStorageClient.register();
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.STORAGE_LABEL.get());
        }
    }
}
