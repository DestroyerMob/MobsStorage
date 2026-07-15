package org.destroyermob.mobsstorage.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.destroyermob.mobsstorage.network.OpenLabelEditorPayload;
import org.destroyermob.mobsstorage.network.OpenNetworkManagerPayload;
import org.destroyermob.mobsstorage.network.OpenNetworkNodePayload;
import org.destroyermob.mobsstorage.network.SyncMenuFiltersPayload;
import org.destroyermob.mobsstorage.registry.ModMenus;

public final class MobsStorageClient {
    private static SyncMenuFiltersPayload menuFilters = new SyncMenuFiltersPayload(-1, List.of());

    private MobsStorageClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(MobsStorageClient::registerMenuScreens);
        modBus.addListener(InventoryControls::registerKeyMappings);
        modBus.addListener(CarryRulesControls::registerKeyMappings);
        modBus.addListener(InventoryScrollControls::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(StorageLabelRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(StorageLabelRenderer::render);
        NeoForge.EVENT_BUS.addListener(StorageLabelRenderer::renderHud);
        NeoForge.EVENT_BUS.addListener(InventoryScrollControls::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(InventoryScrollControls::onClientTick);
        NeoForge.EVENT_BUS.addListener(InventoryControls::onKey);
        NeoForge.EVENT_BUS.addListener(InventoryControls::onMouse);
        NeoForge.EVENT_BUS.addListener(InventoryControls::onRender);
        NeoForge.EVENT_BUS.addListener(CarryRulesControls::onWorldKey);
        NeoForge.EVENT_BUS.addListener(CarryRulesControls::onScreenKey);
        NeoForge.EVENT_BUS.addListener(CarryRulesControls::onScreenInit);
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.NETWORK_TERMINAL.get(), NetworkTerminalScreen::new);
    }

    public static void openEditor(OpenLabelEditorPayload payload) {
        Minecraft.getInstance().setScreen(new StorageLabelScreen(payload));
    }

    public static void openNetworkManager(OpenNetworkManagerPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean accessPage = minecraft.screen instanceof NetworkManagerScreen screen && screen.accessPage();
        minecraft.setScreen(new NetworkManagerScreen(payload, accessPage));
    }

    public static void openNetworkNode(OpenNetworkNodePayload payload) {
        Minecraft.getInstance().setScreen(new NetworkNodeScreen(payload));
    }

    public static void syncMenuFilters(SyncMenuFiltersPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu.containerId == payload.containerId()) {
            menuFilters = payload;
        }
    }

    public static boolean allowsMenuSlot(Slot slot, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return true;
        }
        var menu = minecraft.player.containerMenu;
        if (menu.containerId != menuFilters.containerId()
                || slot.index < 0
                || slot.index >= menu.slots.size()
                || menu.slots.get(slot.index) != slot) {
            return true;
        }
        return menuFilters.allows(slot.index, stack, Item.TooltipContext.of(minecraft.level));
    }
}
