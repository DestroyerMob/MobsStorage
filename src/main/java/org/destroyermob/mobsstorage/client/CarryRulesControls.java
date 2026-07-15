package org.destroyermob.mobsstorage.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.destroyermob.mobsstorage.registry.ModAttachments;

public final class CarryRulesControls {
    private static final KeyMapping OPEN = new KeyMapping("key.mobsstorage.carry_rules",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.mobsstorage");

    private CarryRulesControls() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    public static void onWorldKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() == InputConstants.PRESS && minecraft.screen == null
                && OPEN.matches(event.getKey(), event.getScanCode())) open();
    }

    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>
                && OPEN.matches(event.getKeyCode(), event.getScanCode())) {
            open();
            event.setCanceled(true);
        }
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        int buttonWidth = 82;
        int x = Math.min(screen.width - buttonWidth - 4, screen.getGuiLeft() + screen.getXSize() + 4);
        int y = screen.getGuiTop();
        event.addListener(Button.builder(Component.translatable("screen.mobsstorage.carry.open"), button -> open())
                .bounds(x, y, buttonWidth, 20).build());
    }

    private static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new CarryRulesScreen(minecraft.player.getData(ModAttachments.CARRY_RULES)));
        }
    }
}
