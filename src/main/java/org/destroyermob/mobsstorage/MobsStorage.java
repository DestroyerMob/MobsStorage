package org.destroyermob.mobsstorage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.destroyermob.mobsstorage.client.MobsStorageClient;
import org.destroyermob.mobsstorage.network.ModNetworking;
import org.destroyermob.mobsstorage.inventory.InventoryManagementService;
import org.destroyermob.mobsstorage.inventory.CarryRuleService;
import org.destroyermob.mobsstorage.registry.ModAttachments;
import org.destroyermob.mobsstorage.registry.ModBlockEntities;
import org.destroyermob.mobsstorage.registry.ModBlocks;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.registry.ModMenus;
import org.destroyermob.mobsstorage.storage.StorageLabelEvents;
import org.destroyermob.mobsstorage.networking.NetworkRefillService;
import org.destroyermob.mobsstorage.networking.StorageAutomationCapabilities;
import org.destroyermob.mobsstorage.storage.StorageMenuFilterSync;

@Mod(MobsStorage.MOD_ID)
public final class MobsStorage {
    public static final String MOD_ID = "mobsstorage";

    public MobsStorage(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModAttachments.register(modBus);
        ModNetworking.register(modBus);
        modBus.addListener(EventPriority.HIGH, StorageAutomationCapabilities::register);
        modBus.addListener(this::addCreativeTabItems);

        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StorageLabelEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StorageLabelEvents::onBreakBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StorageLabelEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StorageLabelEvents::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StorageLabelEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(StorageMenuFilterSync::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(NetworkRefillService::onItemDestroyed);
        NeoForge.EVENT_BUS.addListener(NetworkRefillService::onItemUsed);
        NeoForge.EVENT_BUS.addListener(NetworkRefillService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(InventoryManagementService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(CarryRuleService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CarryRuleService::onItemPickup);

        if (FMLEnvironment.dist.isClient()) {
            MobsStorageClient.register(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.STORAGE_LABEL.get());
            event.accept(ModItems.NETWORK_WAND.get());
            event.accept(ModItems.NETWORK_INTERFACE.get());
            event.accept(ModItems.NETWORK_INPUT.get());
            event.accept(ModItems.NETWORK_OUTPUT.get());
        }
    }
}
