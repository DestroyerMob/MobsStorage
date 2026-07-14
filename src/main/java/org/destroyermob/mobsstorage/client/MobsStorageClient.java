package org.destroyermob.mobsstorage.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import org.destroyermob.mobsstorage.network.OpenLabelEditorPayload;

public final class MobsStorageClient {
    private MobsStorageClient() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StorageLabelRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(StorageLabelRenderer::render);
    }

    public static void openEditor(OpenLabelEditorPayload payload) {
        Minecraft.getInstance().setScreen(new StorageLabelScreen(payload));
    }
}
