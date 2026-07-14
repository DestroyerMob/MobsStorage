package org.destroyermob.mobsstorage.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.destroyermob.mobsstorage.client.MobsStorageClient;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.StorageLabelService;

public final class ModNetworking {
    private static final String NETWORK_VERSION = "1";

    private ModNetworking() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ModNetworking::registerPayloads);
    }

    public static void openEditor(ServerPlayer player, net.minecraft.core.BlockPos pos, LabelData data, boolean installing) {
        PacketDistributor.sendToPlayer(player, new OpenLabelEditorPayload(pos, data, installing));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();
        registrar.playToClient(OpenLabelEditorPayload.TYPE, OpenLabelEditorPayload.STREAM_CODEC, ModNetworking::handleOpenEditor);
        registrar.playToServer(SaveLabelPayload.TYPE, SaveLabelPayload.STREAM_CODEC, ModNetworking::handleSaveLabel);
    }

    private static void handleOpenEditor(OpenLabelEditorPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            context.enqueueWork(() -> MobsStorageClient.openEditor(payload));
        }
    }

    private static void handleSaveLabel(SaveLabelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StorageLabelService.save(player, payload);
            }
        });
    }
}
