package org.destroyermob.mobsstorage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.network.OpenNetworkNodePayload;
import org.destroyermob.mobsstorage.network.SaveNetworkNodePayload;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.storage.FilterRules;
import org.destroyermob.mobsstorage.world.NetworkPortBlockEntity;

public final class NetworkNodeScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 224;
    private static final int OUTPUT_PANEL_HEIGHT = 272;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int MUTED = 0xFF606060;
    private static final int SLOT_MUTED = 0xFFE0E0E0;
    private static final int ACTIVE = 0xFF55AA55;
    private static final int WARNING = 0xFFFFD070;
    private final OpenNetworkNodePayload payload;
    private EditBox name;
    private EditBox priority;
    private EditBox outputFilter;
    private Button saveButton;
    private final boolean origin;
    private Component validation = Component.empty();

    public NetworkNodeScreen(OpenNetworkNodePayload payload) {
        super(Component.translatable("screen.mobsstorage.node.title"));
        this.payload = payload;
        this.origin = payload.origin();
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
        priority.setResponder(value -> validateFields());

        if (payload.outputPort()) {
            outputFilter = addRenderableWidget(new EditBox(font, x, panelTop() + 152, innerWidth, 20,
                    Component.translatable("screen.mobsstorage.node.output_filter")));
            outputFilter.setMaxLength(NetworkPortBlockEntity.MAX_FILTER_LENGTH);
            outputFilter.setValue(payload.outputFilter());
            outputFilter.setHint(Component.translatable("screen.mobsstorage.label.filter_input_hint"));
            outputFilter.setResponder(value -> validateFields());
            outputFilter.active = payload.canManage();
        }

        int buttonY = panelBottom() - 32;
        int buttonWidth = (innerWidth - 8) / 3;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.save"), button -> save(false))
                .bounds(x, buttonY, buttonWidth, 20).build());
        saveButton.active = payload.canManage();
        Button unlink = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.unlink"), button -> save(true))
                .bounds(x + buttonWidth + 4, buttonY, buttonWidth, 20).build());
        unlink.active = payload.canManage();
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.node.close"), button -> onClose())
                .bounds(x + (buttonWidth + 4) * 2, buttonY,
                        innerWidth - (buttonWidth + 4) * 2, 20).build());
        name.active = payload.canManage();
        priority.active = payload.canManage();
        validateFields();
    }

    private void validateFields() {
        validation = Component.empty();
        try {
            int value = Integer.parseInt(priority.getValue().trim());
            if (value < -9999 || value > 9999) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            validation = Component.translatable("screen.mobsstorage.label.invalid_priority");
        }
        if (validation.getString().isEmpty() && outputFilter != null
                && !outputFilter.getValue().isBlank()) {
            validation = FilterRules.validate(java.util.List.of(outputFilter.getValue().strip()))
                    .orElse(Component.empty());
        }
        if (saveButton != null) {
            saveButton.active = payload.canManage() && validation.getString().isEmpty();
        }
    }

    private void save(boolean unlink) {
        validateFields();
        if (!validation.getString().isEmpty()) return;
        int value = Integer.parseInt(priority.getValue().trim());
        PacketDistributor.sendToServer(new SaveNetworkNodePayload(
                payload.pos(), name.getValue(), value, unlink,
                outputFilter == null ? "" : outputFilter.getValue().strip()));
        if (unlink) onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        drawRaisedPanel(graphics, panelLeft(), panelTop(), panelWidth(), panelHeight());
        graphics.fill(panelLeft() + 3, panelTop() + 51, panelRight() - 3, panelTop() + 52, PANEL_DARK);
        graphics.fill(panelLeft() + 3, panelTop() + 52, panelRight() - 3, panelTop() + 53, PANEL_LIGHT);
        drawInset(graphics, panelLeft() + 18, originBoxY(), panelWidth() - 36, 33, SLOT);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.renderFakeItem(payload.outputPort()
                ? ModItems.NETWORK_OUTPUT.get().getDefaultInstance()
                : Items.CHEST.getDefaultInstance(), panelLeft() + 16, panelTop() + 17);
        graphics.drawString(font, title, panelLeft() + 40, panelTop() + 14, TEXT, false);
        graphics.drawString(font, payload.networkName(), panelLeft() + 40, panelTop() + 28, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.node.name_label"),
                panelLeft() + 18, panelTop() + 60, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.node.priority_label"),
                panelLeft() + 18, panelTop() + 100, TEXT, false);
        if (payload.outputPort()) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.node.output_filter_label"),
                    panelLeft() + 18, panelTop() + 140, TEXT, false);
            graphics.drawString(font, Component.translatable("screen.mobsstorage.node.output_filter_hint"),
                    panelLeft() + 18, panelTop() + 175, MUTED, false);
        }
        graphics.drawString(font, Component.translatable(origin
                        ? "screen.mobsstorage.node.origin_active" : "screen.mobsstorage.node.origin_inactive"),
                panelLeft() + 26, originBoxY() + 7, origin ? ACTIVE : WARNING, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.node.origin_hint"),
                panelLeft() + 26, originBoxY() + 19, SLOT_MUTED, false);
        if (!validation.getString().isEmpty()) {
            graphics.drawString(font, validation, panelLeft() + 18, panelBottom() - 44, 0xFFFF7777, false);
        }
        if (!payload.canManage()) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.node.owner_only"),
                    panelLeft() + 18, panelBottom() - 44, 0xFFE6B866, false);
        }
    }

    /** Prevent Screen.render() from invoking Minecraft's post-process blur after the panel is drawn. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private int panelWidth() { return Math.min(PANEL_WIDTH, width - 20); }
    private int panelHeight() {
        return Math.min(payload.outputPort() ? OUTPUT_PANEL_HEIGHT : PANEL_HEIGHT, height - 20);
    }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int panelRight() { return panelLeft() + panelWidth(); }
    private int panelBottom() { return panelTop() + panelHeight(); }
    private int originBoxY() { return panelTop() + (payload.outputPort() ? 190 : 141); }
    private static void drawRaisedPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 2, PANEL_LIGHT);
        graphics.fill(x, y, x + 2, y + height, PANEL_LIGHT);
        graphics.fill(x, y + height - 2, x + width, y + height, PANEL_DARK);
        graphics.fill(x + width - 2, y, x + width, y + height, PANEL_DARK);
    }
    private static void drawInset(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, PANEL_LIGHT);
        graphics.fill(x, y, x + width - 1, y + height - 1, PANEL_DARK);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
    }
    @Override public boolean isPauseScreen() { return false; }
}
