package org.destroyermob.mobsstorage.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.network.InventoryActionPayload;

public final class LoadoutControls {
    private static final KeyMapping TOGGLE = new KeyMapping("key.mobsstorage.toggle_loadout",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.mobsstorage");

    private LoadoutControls() {}

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean activated = false;
        while (TOGGLE.consumeClick()) activated = true;
        if (!activated || minecraft.player == null || minecraft.screen != null) return;
        PacketDistributor.sendToServer(new InventoryActionPayload(InventoryActionPayload.Action.TOGGLE_LOADOUT,
                -1, minecraft.player.containerMenu.containerId));
    }
}
