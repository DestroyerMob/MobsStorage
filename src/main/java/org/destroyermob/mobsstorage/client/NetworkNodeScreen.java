package org.destroyermob.mobsstorage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.network.OpenNetworkNodePayload;
import org.destroyermob.mobsstorage.network.SaveNetworkNodePayload;

public final class NetworkNodeScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 224;
    private static final int ACCENT = 0xFF71C7E8;
    private static final int MUTED = 0xFF9BA8B0;
    private final OpenNetworkNodePayload payload;
    private EditBox name;
    private EditBox priority;
    private boolean source;
    private Component validation = Component.empty();

    public NetworkNodeScreen(OpenNetworkNodePayload payload) {
        super(Component.translatable("screen.mobsstorage.node.title"));
        this.payload = payload;
        this.source = payload.source();
    }

    @Override
    protected void init() {
        int x = panelLeft() + 18;
        int innerWidth = panelWidth() - 36;
        name = addRenderableWidget(new EditBox(font, x, panelTop() + 72, innerWidth, 20,
                Component.translatable("screen.mobsstorage.node.name")));
        name.setMaxLength(48);
        name.setValue(payload.storageName());
        name.setHint(Component.translatable("screen.mobsstorage.node.name_hint"));

        priority = addRenderableWidget(new EditBox(font, x, panelTop() + 112, 128, 20,
                Component.translatable("screen.mobsstorage.node.priority")));
        priority.setMaxLength(5);
        priority.setValue(Integer.toString(payload.priority()));
        priority.setHint(Component.translatable("screen.mobsstorage.node.priority"));
        priority.setResponder(value -> validatePriority());

        CycleButton<Boolean> sourceButton = addRenderableWidget(CycleButton.onOffBuilder(source).create(
                x + 140, panelTop() + 112, innerWidth - 140, 20,
                Component.translatable("screen.mobsstorage.node.source"), (button, value) -> source = value));
        sourceButton.active = payload.canManage();

        int buttonY = panelBottom() - 32;
        int buttonWidth = (innerWidth - 8) / 3;
        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.save"), button -> save(false))
                .bounds(x, buttonY, buttonWidth, 20).build());
        save.active = payload.canManage();
        Button unlink = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.unlink"), button -> save(true))
                .bounds(x + buttonWidth + 4, buttonY, buttonWidth, 20).build());
        unlink.active = payload.canManage();
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.close"), button -> onClose())
                .bounds(x + (buttonWidth + 4) * 2, buttonY,
                        innerWidth - (buttonWidth + 4) * 2, 20).build());
        name.active = payload.canManage();
        priority.active = payload.canManage();
        validatePriority();
    }

    private void validatePriority() {
        validation = Component.empty();
        try {
            int value = Integer.parseInt(priority.getValue().trim());
            if (value < -9999 || value > 9999) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            validation = Component.translatable("screen.mobsstorage.label.invalid_priority");
        }
    }

    private void save(boolean unlink) {
        validatePriority();
        if (!validation.getString().isEmpty()) return;
        int value = Integer.parseInt(priority.getValue().trim());
        PacketDistributor.sendToServer(new SaveNetworkNodePayload(payload.pos(), name.getValue(), value, source, unlink));
        if (unlink) onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft() - 3, panelTop() - 3, panelRight() + 3, panelBottom() + 3, 0x80000000);
        graphics.fill(panelLeft(), panelTop(), panelRight(), panelBottom(), 0xF0191E22);
        graphics.fill(panelLeft(), panelTop(), panelRight(), panelTop() + 52, 0xFF20272C);
        graphics.fill(panelLeft(), panelTop(), panelRight(), panelTop() + 2, ACCENT);
        graphics.fill(panelLeft() + 18, panelTop() + 141, panelRight() - 18, panelTop() + 174, 0xCC20282D);
        graphics.renderOutline(panelLeft() + 18, panelTop() + 141, panelWidth() - 36, 33, 0xFF3C4A52);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.renderFakeItem(Items.CHEST.getDefaultInstance(), panelLeft() + 16, panelTop() + 17);
        graphics.drawString(font, title, panelLeft() + 40, panelTop() + 14, 0xFFFFFFFF, false);
        graphics.drawString(font, payload.networkName(), panelLeft() + 40, panelTop() + 28, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.node.name_label"),
                panelLeft() + 18, panelTop() + 60, 0xFFDCE6EA, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.node.priority_label"),
                panelLeft() + 18, panelTop() + 100, 0xFFDCE6EA, false);
        graphics.drawString(font, Component.translatable(source
                        ? "screen.mobsstorage.node.source_active" : "screen.mobsstorage.node.source_inactive"),
                panelLeft() + 26, panelTop() + 148, source ? 0xFF76D69A : 0xFFE6B866, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.node.source_hint"),
                panelLeft() + 26, panelTop() + 160, MUTED, false);
        if (!validation.getString().isEmpty()) {
            graphics.drawString(font, validation, panelLeft() + 18, panelBottom() - 44, 0xFFFF7777, false);
        }
        if (!payload.canManage()) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.node.owner_only"),
                    panelLeft() + 18, panelBottom() - 44, 0xFFE6B866, false);
        }
    }

    private int panelWidth() { return Math.min(PANEL_WIDTH, width - 20); }
    private int panelHeight() { return Math.min(PANEL_HEIGHT, height - 20); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int panelRight() { return panelLeft() + panelWidth(); }
    private int panelBottom() { return panelTop() + panelHeight(); }
    @Override public boolean isPauseScreen() { return false; }
}
