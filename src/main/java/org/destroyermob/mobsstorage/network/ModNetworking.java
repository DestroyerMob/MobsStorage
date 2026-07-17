package org.destroyermob.mobsstorage.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.destroyermob.mobsstorage.client.MobsStorageClient;
import org.destroyermob.mobsstorage.inventory.InventoryManagementService;
import org.destroyermob.mobsstorage.inventory.CarryRuleService;
import org.destroyermob.mobsstorage.inventory.BundleSelectionService;
import org.destroyermob.mobsstorage.menu.NetworkTerminalMenu;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.StorageLabelService;
import org.destroyermob.mobsstorage.networking.NetworkNodeData;
import org.destroyermob.mobsstorage.networking.NetworkService;
import org.destroyermob.mobsstorage.storage.StorageResolver;

public final class ModNetworking {
    private static final String NETWORK_VERSION = "10";

    private ModNetworking() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ModNetworking::registerPayloads);
    }

    public static void openEditor(ServerPlayer player, net.minecraft.core.BlockPos pos, LabelData data, boolean installing) {
        NetworkNodeData node = StorageResolver.logicalStorage(player.serverLevel(), pos).stream()
                .map(NetworkService::nodeData).findFirst().orElse(NetworkNodeData.EMPTY);
        PacketDistributor.sendToPlayer(player, new OpenLabelEditorPayload(pos, data, node, installing));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();
        registrar.playToClient(OpenLabelEditorPayload.TYPE, OpenLabelEditorPayload.STREAM_CODEC, ModNetworking::handleOpenEditor);
        registrar.playToClient(SyncMenuFiltersPayload.TYPE, SyncMenuFiltersPayload.STREAM_CODEC,
                ModNetworking::handleSyncMenuFilters);
        registrar.playToServer(SaveLabelPayload.TYPE, SaveLabelPayload.STREAM_CODEC, ModNetworking::handleSaveLabel);
        registrar.playToClient(OpenNetworkManagerPayload.TYPE, OpenNetworkManagerPayload.STREAM_CODEC, ModNetworking::handleOpenManager);
        registrar.playToClient(OpenNetworkNodePayload.TYPE, OpenNetworkNodePayload.STREAM_CODEC, ModNetworking::handleOpenNode);
        registrar.playToServer(NetworkActionPayload.TYPE, NetworkActionPayload.STREAM_CODEC, ModNetworking::handleNetworkAction);
        registrar.playToServer(SaveNetworkNodePayload.TYPE, SaveNetworkNodePayload.STREAM_CODEC, ModNetworking::handleSaveNode);
        registrar.playToServer(InventoryActionPayload.TYPE, InventoryActionPayload.STREAM_CODEC, ModNetworking::handleInventoryAction);
        registrar.playToServer(SaveCarryRulesPayload.TYPE, SaveCarryRulesPayload.STREAM_CODEC,
                ModNetworking::handleSaveCarryRules);
        registrar.playToServer(UpdateTerminalViewPayload.TYPE, UpdateTerminalViewPayload.STREAM_CODEC,
                ModNetworking::handleUpdateTerminalView);
        registrar.playToServer(SelectBundleItemPayload.TYPE, SelectBundleItemPayload.STREAM_CODEC,
                ModNetworking::handleSelectBundleItem);
    }

    private static void handleOpenEditor(OpenLabelEditorPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            context.enqueueWork(() -> MobsStorageClient.openEditor(payload));
        }
    }

    private static void handleSyncMenuFilters(SyncMenuFiltersPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            context.enqueueWork(() -> MobsStorageClient.syncMenuFilters(payload));
        }
    }

    private static void handleSaveLabel(SaveLabelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StorageLabelService.save(player, payload);
            }
        });
    }

    private static void handleOpenManager(OpenNetworkManagerPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) context.enqueueWork(() -> MobsStorageClient.openNetworkManager(payload));
    }

    private static void handleOpenNode(OpenNetworkNodePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) context.enqueueWork(() -> MobsStorageClient.openNetworkNode(payload));
    }

    private static void handleNetworkAction(NetworkActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) NetworkService.handleAction(player, payload);
        });
    }

    private static void handleSaveNode(SaveNetworkNodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) NetworkService.saveNode(player, payload);
        });
    }

    private static void handleInventoryAction(InventoryActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) InventoryManagementService.handle(player, payload);
        });
    }

    private static void handleSaveCarryRules(SaveCarryRulesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) CarryRuleService.save(player, payload.rules());
        });
    }

    private static void handleUpdateTerminalView(UpdateTerminalViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu.containerId == payload.containerId()
                    && player.containerMenu instanceof NetworkTerminalMenu menu) {
                menu.updateView(payload.query(), payload.sort(), payload.descending());
            }
        });
    }

    private static void handleSelectBundleItem(SelectBundleItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BundleSelectionService.select(player, payload);
            }
        });
    }
}
