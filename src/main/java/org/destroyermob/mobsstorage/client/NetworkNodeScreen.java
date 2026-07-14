package org.destroyermob.mobsstorage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.network.OpenNetworkNodePayload;
import org.destroyermob.mobsstorage.network.SaveNetworkNodePayload;

public final class NetworkNodeScreen extends Screen {
    private final OpenNetworkNodePayload payload;
    private EditBox name;
    private EditBox priority;
    private boolean source;

    public NetworkNodeScreen(OpenNetworkNodePayload payload) {
        super(Component.translatable("screen.mobsstorage.node.title"));
        this.payload = payload;
        this.source = payload.source();
    }

    @Override
    protected void init() {
        int left = width / 2 - 130;
        name = addRenderableWidget(new EditBox(font, left, height / 2 - 42, 260, 20,
                Component.translatable("screen.mobsstorage.node.name")));
        name.setMaxLength(48);
        name.setValue(payload.storageName());
        name.setHint(Component.translatable("screen.mobsstorage.node.name"));
        priority = addRenderableWidget(new EditBox(font, left, height / 2 - 16, 126, 20,
                Component.translatable("screen.mobsstorage.node.priority")));
        priority.setMaxLength(5);
        priority.setValue(Integer.toString(payload.priority()));
        priority.setHint(Component.translatable("screen.mobsstorage.node.priority"));
        CycleButton<Boolean> sourceButton = addRenderableWidget(CycleButton.onOffBuilder(source).create(
                left + 134, height / 2 - 16, 126, 20,
                Component.translatable("screen.mobsstorage.node.source"), (button, value) -> source = value));
        sourceButton.active = payload.canManage();
        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.save"), button -> save(false))
                .bounds(left, height / 2 + 16, 82, 20).build());
        save.active = payload.canManage();
        Button unlink = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.unlink"), button -> save(true))
                .bounds(left + 89, height / 2 + 16, 82, 20).build());
        unlink.active = payload.canManage();
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.close"), button -> onClose())
                .bounds(left + 178, height / 2 + 16, 82, 20).build());
        name.active = payload.canManage();
        priority.active = payload.canManage();
    }

    private void save(boolean unlink) {
        int value;
        try { value = Math.max(-9999, Math.min(9999, Integer.parseInt(priority.getValue().trim()))); }
        catch (NumberFormatException exception) { return; }
        PacketDistributor.sendToServer(new SaveNetworkNodePayload(payload.pos(), name.getValue(), value, source, unlink));
        if (unlink) onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 138;
        int top = height / 2 - 76;
        graphics.fill(left, top, left + 276, top + 132, 0xE0101010);
        graphics.renderOutline(left, top, 276, 132, 0xFF6A6A6A);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF);
        graphics.drawCenteredString(font, payload.networkName(), width / 2, top + 23, 0xAAAAAA);
    }

    @Override public boolean isPauseScreen() { return false; }
}
