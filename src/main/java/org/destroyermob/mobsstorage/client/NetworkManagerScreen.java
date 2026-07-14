package org.destroyermob.mobsstorage.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.network.NetworkActionPayload;
import org.destroyermob.mobsstorage.network.NetworkSnapshot;
import org.destroyermob.mobsstorage.network.OpenNetworkManagerPayload;

public final class NetworkManagerScreen extends Screen {
    private static final int ROW = 24;
    private final List<NetworkSnapshot> networks;
    private int selectedIndex;
    private int networkOffset;
    private int memberOffset;
    private int nodeOffset;
    private EditBox createName;
    private EditBox networkName;
    private EditBox memberName;

    public NetworkManagerScreen(OpenNetworkManagerPayload payload) {
        super(Component.translatable("screen.mobsstorage.network.title"));
        this.networks = payload.networks();
        this.selectedIndex = initialSelection();
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int split = detailLeft();
        int rightWidth = detailWidth();
        int leftWidth = leftColumnWidth();
        int bottom = panelBottom() - 26;
        int createButtonWidth = Math.min(56, Math.max(44, leftWidth / 3));
        createName = addRenderableWidget(new EditBox(font, left + 4, bottom,
                leftWidth - createButtonWidth - 8, 20,
                Component.translatable("screen.mobsstorage.network.new_name")));
        createName.setMaxLength(48);
        createName.setHint(Component.translatable("screen.mobsstorage.network.new_name"));
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.create"), button ->
                send(NetworkActionPayload.Action.CREATE, NetworkActionPayload.NONE,
                        NetworkActionPayload.NONE, createName.getValue()))
                .bounds(left + leftWidth - createButtonWidth, bottom, createButtonWidth, 20).build());

        NetworkSnapshot selected = selected();
        if (selected == null) return;
        networkName = addRenderableWidget(new EditBox(font, split, 30, Math.max(60, rightWidth - 64), 20,
                Component.translatable("screen.mobsstorage.network.name")));
        networkName.setMaxLength(48);
        networkName.setValue(selected.name());
        Button rename = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.rename"), button ->
                send(NetworkActionPayload.Action.RENAME, selected.id(), NetworkActionPayload.NONE, networkName.getValue()))
                .bounds(split + rightWidth - 60, 30, 60, 20).build());
        rename.active = selected.owner();

        String selectKey = selected.member()
                ? selected.selected() ? "screen.mobsstorage.network.selected" : "screen.mobsstorage.network.select"
                : "screen.mobsstorage.network.join";
        int controlWidth = Math.max(48, (rightWidth - 8) / 3);
        Button select = addRenderableWidget(Button.builder(Component.translatable(selectKey), button ->
                send(selected.member() ? NetworkActionPayload.Action.SELECT : NetworkActionPayload.Action.JOIN,
                        selected.id(), NetworkActionPayload.NONE, ""))
                .bounds(split, 54, controlWidth, 20).build());
        select.active = !selected.selected();
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.clear_selection"), button ->
                send(NetworkActionPayload.Action.CLEAR_SELECTION, NetworkActionPayload.NONE,
                        NetworkActionPayload.NONE, ""))
                .bounds(split + controlWidth + 4, 54, controlWidth, 20).build());
        Button visibility = addRenderableWidget(Button.builder(
                Component.translatable(selected.publicAccess()
                        ? "screen.mobsstorage.network.public" : "screen.mobsstorage.network.private"), button ->
                        send(NetworkActionPayload.Action.TOGGLE_PUBLIC, selected.id(), NetworkActionPayload.NONE, ""))
                .bounds(split + (controlWidth + 4) * 2, 54,
                        Math.max(48, rightWidth - (controlWidth + 4) * 2), 20).build());
        visibility.active = selected.owner();

        memberName = addRenderableWidget(new EditBox(font, split, 92, Math.max(60, rightWidth - 64), 20,
                Component.translatable("screen.mobsstorage.network.member_name")));
        memberName.setMaxLength(32);
        memberName.setHint(Component.translatable("screen.mobsstorage.network.member_name"));
        memberName.active = selected.owner();
        Button addMember = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.add"), button ->
                send(NetworkActionPayload.Action.ADD_MEMBER, selected.id(), NetworkActionPayload.NONE, memberName.getValue()))
                .bounds(split + rightWidth - 60, 92, 60, 20).build());
        addMember.active = selected.owner();

        if (selected.owner()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.delete"), button ->
                    send(NetworkActionPayload.Action.DELETE, selected.id(), NetworkActionPayload.NONE, ""))
                    .bounds(split + rightWidth - 60, bottom, 60, 20).build());
        } else if (selected.member()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.leave"), button ->
                    send(NetworkActionPayload.Action.LEAVE, selected.id(), NetworkActionPayload.NONE, ""))
                    .bounds(split + rightWidth - 60, bottom, 60, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft() - 5, 7, panelLeft() + panelWidth() + 5, panelBottom() + 5, 0xE0101010);
        graphics.renderOutline(panelLeft() - 5, 7, panelWidth() + 10, panelBottom() - 2, 0xFF6A6A6A);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        renderNetworks(graphics, mouseX, mouseY);
        NetworkSnapshot selected = selected();
        if (selected != null) renderDetails(graphics, selected, mouseX, mouseY);
    }

    private void renderNetworks(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelLeft();
        int rowWidth = leftColumnWidth();
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.available"), x + 4, 32, 0xD8D8D8, false);
        int rows = visibleNetworkRows();
        for (int visible = 0; visible < rows; visible++) {
            int index = networkOffset + visible;
            if (index >= networks.size()) break;
            NetworkSnapshot network = networks.get(index);
            int y = 45 + visible * ROW;
            boolean hovered = inside(mouseX, mouseY, x, y, rowWidth, ROW - 2);
            graphics.fill(x, y, x + rowWidth, y + ROW - 2,
                    index == selectedIndex ? 0xEE3C5870 : hovered ? 0xCC303A42 : 0xBB222222);
            String marker = network.selected() ? "* " : "";
            graphics.drawString(font, font.plainSubstrByWidth(marker + network.name(), rowWidth - 10), x + 5, y + 5, 0xFFFFFF, false);
            graphics.drawString(font, network.owner() ? "Owner" : network.member() ? "Member" : "Public",
                    x + 5, y + 14, 0xAAAAAA, false);
        }
    }

    private void renderDetails(GuiGraphics graphics, NetworkSnapshot selected, int mouseX, int mouseY) {
        int x = detailLeft();
        int rightWidth = detailWidth();
        graphics.drawString(font, "Owner: " + selected.ownerName(), x, 78, 0xAAAAAA, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.members"), x, 118, 0xD8D8D8, false);
        int memberRows = 3;
        for (int visible = 0; visible < memberRows; visible++) {
            int index = memberOffset + visible;
            if (index >= selected.members().size()) break;
            NetworkSnapshot.Member member = selected.members().get(index);
            int y = 130 + visible * 17;
            graphics.fill(x, y, x + rightWidth, y + 15, 0xAA242424);
            graphics.drawString(font, member.name(), x + 4, y + 4, 0xEEEEEE, false);
            if (selected.owner()) {
                graphics.fill(x + rightWidth - 18, y, x + rightWidth, y + 15,
                        inside(mouseX, mouseY, x + rightWidth - 18, y, 18, 15) ? 0xFF963A3A : 0xCC542828);
                graphics.drawCenteredString(font, "x", x + rightWidth - 9, y + 3, 0xFFFFFF);
            }
        }
        int nodesLabelY = 186;
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.storages"), x, nodesLabelY, 0xD8D8D8, false);
        int rows = Math.max(1, (panelBottom() - 30 - (nodesLabelY + 12)) / 22);
        for (int visible = 0; visible < rows; visible++) {
            int index = nodeOffset + visible;
            if (index >= selected.nodes().size()) break;
            NetworkSnapshot.Node node = selected.nodes().get(index);
            int y = nodesLabelY + 12 + visible * 22;
            graphics.fill(x, y, x + rightWidth, y + 20, 0xAA242424);
            Item icon = BuiltInRegistries.ITEM.get(node.icon());
            if (icon != Items.AIR) graphics.renderFakeItem(icon.getDefaultInstance(), x + 2, y + 2);
            String suffix = "  P" + node.priority() + (node.source() ? "  [Source]" : "");
            graphics.drawString(font, font.plainSubstrByWidth(node.name() + suffix, rightWidth - 25),
                    x + 22, y + 6, 0xEEEEEE, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inside(mouseX, mouseY, panelLeft(), 45, leftColumnWidth(), visibleNetworkRows() * ROW)) {
            int index = networkOffset + (int) ((mouseY - 45) / ROW);
            if (index >= 0 && index < networks.size()) {
                selectedIndex = index;
                memberOffset = 0;
                nodeOffset = 0;
                rebuildWidgets();
                return true;
            }
        }
        NetworkSnapshot selected = selected();
        if (button == 0 && selected != null && selected.owner()) {
            int x = detailLeft();
            int rightWidth = detailWidth();
            if (inside(mouseX, mouseY, x, 130, rightWidth, 51)) {
                int index = memberOffset + (int) ((mouseY - 130) / 17);
                if (index < selected.members().size() && mouseX >= x + rightWidth - 18) {
                    send(NetworkActionPayload.Action.REMOVE_MEMBER, selected.id(), selected.members().get(index).id(), "");
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = -(int) Math.signum(scrollY);
        NetworkSnapshot selected = selected();
        if (mouseX < detailLeft()) {
            networkOffset = clamp(networkOffset + direction, 0, Math.max(0, networks.size() - visibleNetworkRows()));
            return true;
        }
        if (selected != null && mouseY < 186) {
            memberOffset = clamp(memberOffset + direction, 0, Math.max(0, selected.members().size() - 3));
            return true;
        }
        if (selected != null) {
            int rows = Math.max(1, (panelBottom() - 30 - 198) / 22);
            nodeOffset = clamp(nodeOffset + direction, 0, Math.max(0, selected.nodes().size() - rows));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void send(NetworkActionPayload.Action action, java.util.UUID network, java.util.UUID subject, String value) {
        PacketDistributor.sendToServer(new NetworkActionPayload(action, network, subject, value));
    }

    private NetworkSnapshot selected() {
        return selectedIndex >= 0 && selectedIndex < networks.size() ? networks.get(selectedIndex) : null;
    }

    private int initialSelection() {
        for (int index = 0; index < networks.size(); index++) if (networks.get(index).selected()) return index;
        return networks.isEmpty() ? -1 : 0;
    }

    private int visibleNetworkRows() { return Math.max(1, (panelBottom() - 76) / ROW); }
    private int panelWidth() { return Math.max(280, Math.min(560, width - 12)); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelBottom() { return height - 12; }
    private int leftColumnWidth() { return Math.min(190, Math.max(112, panelWidth() / 3)); }
    private int detailLeft() { return panelLeft() + leftColumnWidth() + 8; }
    private int detailWidth() { return panelWidth() - leftColumnWidth() - 8; }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    @Override public boolean isPauseScreen() { return false; }
}
