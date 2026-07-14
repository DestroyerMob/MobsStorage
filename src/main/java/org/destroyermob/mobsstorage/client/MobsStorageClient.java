package org.destroyermob.mobsstorage.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import org.destroyermob.mobsstorage.network.OpenLabelEditorPayload;
import org.destroyermob.mobsstorage.network.OpenNetworkManagerPayload;
import org.destroyermob.mobsstorage.network.OpenNetworkNodePayload;

public final class MobsStorageClient {
    private MobsStorageClient() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StorageLabelRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(StorageLabelRenderer::render);
        NeoForge.EVENT_BUS.addListener(StorageLabelRenderer::renderHud);
    }

    public static void openEditor(OpenLabelEditorPayload payload) {
        Minecraft.getInstance().setScreen(new StorageLabelScreen(payload));
    }

    public static void openNetworkManager(OpenNetworkManagerPayload payload) {
        Minecraft.getInstance().setScreen(new NetworkManagerScreen(payload));
    }

    public static void openNetworkNode(OpenNetworkNodePayload payload) {
        Minecraft.getInstance().setScreen(new NetworkNodeScreen(payload));
    }
}
