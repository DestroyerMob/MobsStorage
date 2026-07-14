package org.destroyermob.mobsstorage.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.network.NetworkActionPayload;
import org.destroyermob.mobsstorage.network.NetworkSnapshot;
import org.destroyermob.mobsstorage.network.OpenNetworkManagerPayload;
import org.destroyermob.mobsstorage.registry.ModItems;

public final class NetworkManagerScreen extends Screen {
    private static final int HEADER_HEIGHT = 46;
    private static final int SIDEBAR_WIDTH = 190;
    private static final int NETWORK_ROW_HEIGHT = 32;
    private static final int CARD = 0xEE242B31;
    private static final int CARD_HOVER = 0xEE303941;
    private static final int CARD_SELECTED = 0xFF294B5C;
    private static final int ACCENT = 0xFF71C7E8;
    private static final int MUTED = 0xFF9BA8B0;

    private final List<NetworkSnapshot> networks;
    private int selectedIndex;
    private int networkOffset;
    private int memberOffset;
    private int nodeOffset;
    private Page page = Page.STORAGE;
    private EditBox createName;
    private EditBox networkName;
    private EditBox memberName;

    public NetworkManagerScreen(OpenNetworkManagerPayload payload) {
        this(payload, false);
    }

    NetworkManagerScreen(OpenNetworkManagerPayload payload, boolean accessPage) {
        super(Component.translatable("screen.mobsstorage.network.title"));
        this.networks = payload.networks();
        this.selectedIndex = initialSelection();
        this.page = accessPage ? Page.ACCESS : Page.STORAGE;
    }

    @Override
    protected void init() {
        int createY = panelBottom() - 32;
        int createWidth = sidebarWidth() - 70;
        createName = addRenderableWidget(new EditBox(font, panelLeft() + 10, createY, createWidth, 20,
                Component.translatable("screen.mobsstorage.network.new_name")));
        createName.setMaxLength(48);
        createName.setHint(Component.translatable("screen.mobsstorage.network.new_name"));
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.create"), button ->
                send(NetworkActionPayload.Action.CREATE, NetworkActionPayload.NONE,
                        NetworkActionPayload.NONE, createName.getValue()))
                .bounds(panelLeft() + 14 + createWidth, createY, 56, 20).build());

        NetworkSnapshot selected = selected();
        if (selected == null) return;
        int x = contentLeft();
        int contentWidth = contentWidth();
        int top = panelTop() + HEADER_HEIGHT + 10;
        networkName = addRenderableWidget(new EditBox(font, x, top, contentWidth - 68, 20,
                Component.translatable("screen.mobsstorage.network.name")));
        networkName.setMaxLength(48);
        networkName.setValue(selected.name());
        networkName.active = selected.owner();
        Button rename = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.rename"), button ->
                send(NetworkActionPayload.Action.RENAME, selected.id(), NetworkActionPayload.NONE, networkName.getValue()))
                .bounds(x + contentWidth - 64, top, 64, 20).build());
        rename.active = selected.owner();

        int actionY = top + 26;
        int actionWidth = Math.max(58, (contentWidth - 8) / 3);
        String selectKey = selected.member()
                ? selected.selected() ? "screen.mobsstorage.network.selected" : "screen.mobsstorage.network.select"
                : "screen.mobsstorage.network.join";
        Button select = addRenderableWidget(Button.builder(Component.translatable(selectKey), button ->
                send(selected.member() ? NetworkActionPayload.Action.SELECT : NetworkActionPayload.Action.JOIN,
                        selected.id(), NetworkActionPayload.NONE, ""))
                .bounds(x, actionY, actionWidth, 20).build());
        select.active = !selected.selected();
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.clear_selection"), button ->
                send(NetworkActionPayload.Action.CLEAR_SELECTION, NetworkActionPayload.NONE,
                        NetworkActionPayload.NONE, ""))
                .bounds(x + actionWidth + 4, actionY, actionWidth, 20).build());
        Button visibility = addRenderableWidget(Button.builder(Component.translatable(
                        selected.publicAccess() ? "screen.mobsstorage.network.public" : "screen.mobsstorage.network.private"), button ->
                        send(NetworkActionPayload.Action.TOGGLE_PUBLIC, selected.id(), NetworkActionPayload.NONE, ""))
                .bounds(x + (actionWidth + 4) * 2, actionY,
                        contentWidth - (actionWidth + 4) * 2, 20).build());
        visibility.active = selected.owner();

        int tabY = top + 102;
        int tabWidth = (contentWidth - 4) / 2;
        Button storageTab = addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.network.storage_tab"), button -> setPage(Page.STORAGE))
                .bounds(x, tabY, tabWidth, 20).build());
        storageTab.active = page != Page.STORAGE;
        Button accessTab = addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.network.access_tab"), button -> setPage(Page.ACCESS))
                .bounds(x + tabWidth + 4, tabY, tabWidth, 20).build());
        accessTab.active = page != Page.ACCESS;

        if (page == Page.ACCESS && selected.owner()) {
            int memberY = pageHeadingY() + 29;
            memberName = addRenderableWidget(new EditBox(font, x, memberY, contentWidth - 64, 20,
                    Component.translatable("screen.mobsstorage.network.member_name")));
            memberName.setMaxLength(32);
            memberName.setHint(Component.translatable("screen.mobsstorage.network.member_name"));
            addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.add"), button ->
                    send(NetworkActionPayload.Action.ADD_MEMBER, selected.id(), NetworkActionPayload.NONE, memberName.getValue()))
                    .bounds(x + contentWidth - 60, memberY, 60, 20).build());
        }

        int bottomY = panelBottom() - 32;
        if (selected.owner()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.delete"), button ->
                    send(NetworkActionPayload.Action.DELETE, selected.id(), NetworkActionPayload.NONE, ""))
                    .bounds(panelRight() - 72, bottomY, 62, 20).build());
        } else if (selected.member()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.leave"), button ->
                    send(NetworkActionPayload.Action.LEAVE, selected.id(), NetworkActionPayload.NONE, ""))
                    .bounds(panelRight() - 72, bottomY, 62, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawForeground(graphics, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(panelLeft() - 3, panelTop() - 3, panelRight() + 3, panelBottom() + 3, 0x80000000);
        graphics.fill(panelLeft(), panelTop(), panelRight(), panelBottom(), 0xF0191E22);
        graphics.fill(panelLeft(), panelTop(), panelRight(), panelTop() + HEADER_HEIGHT, 0xFF20272C);
        graphics.fill(panelLeft(), panelTop() + HEADER_HEIGHT, panelLeft() + sidebarWidth(), panelBottom(), 0xFF14191D);
        graphics.fill(panelLeft() + sidebarWidth(), panelTop() + HEADER_HEIGHT,
                panelLeft() + sidebarWidth() + 1, panelBottom(), 0xFF394249);
        graphics.fill(panelLeft(), panelTop(), panelRight(), panelTop() + 2, ACCENT);
        drawNetworkCards(graphics, mouseX, mouseY);
        NetworkSnapshot selected = selected();
        if (selected != null) {
            int statusY = panelTop() + HEADER_HEIGHT + 66;
            graphics.fill(contentLeft(), statusY, panelRight() - 10, statusY + 30, 0xCC20282D);
            graphics.renderOutline(contentLeft(), statusY, contentWidth(), 30, 0xFF3C4A52);
            if (page == Page.STORAGE) drawStorageCards(graphics, selected, mouseX, mouseY);
            else drawMemberCards(graphics, selected, mouseX, mouseY);
        }
    }

    private void drawForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        Item wand = ModItems.NETWORK_WAND.get();
        graphics.renderFakeItem(wand.getDefaultInstance(), panelLeft() + 12, panelTop() + 14);
        graphics.drawString(font, title, panelLeft() + 36, panelTop() + 12, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.subtitle"),
                panelLeft() + 36, panelTop() + 25, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.available"),
                panelLeft() + 10, panelTop() + HEADER_HEIGHT + 10, 0xFFDCE6EA, false);
        if (networks.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("screen.mobsstorage.network.empty"),
                    panelLeft() + 10, panelTop() + HEADER_HEIGHT + 32, sidebarWidth() - 20, MUTED);
            return;
        }
        drawNetworkText(graphics);
        NetworkSnapshot selected = selected();
        if (selected == null) return;
        int statusY = panelTop() + HEADER_HEIGHT + 66;
        String owner = Component.translatable("screen.mobsstorage.network.owner", selected.ownerName()).getString();
        graphics.drawString(font, owner, contentLeft() + 8, statusY + 6, 0xFFE3EBEF, false);
        graphics.drawString(font, refillStatus(selected), contentLeft() + 8, statusY + 17,
                refillReady(selected) ? 0xFF76D69A : 0xFFE6B866, false);
        if (page == Page.STORAGE) drawStorageText(graphics, selected);
        else drawMemberText(graphics, selected);
    }

    private void drawNetworkCards(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = networkListTop();
        int rows = visibleNetworkRows();
        for (int visible = 0; visible < rows; visible++) {
            int index = networkOffset + visible;
            if (index >= networks.size()) break;
            int rowY = y + visible * NETWORK_ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, panelLeft() + 8, rowY, sidebarWidth() - 16, NETWORK_ROW_HEIGHT - 4);
            int color = index == selectedIndex ? CARD_SELECTED : hovered ? CARD_HOVER : CARD;
            graphics.fill(panelLeft() + 8, rowY, panelLeft() + sidebarWidth() - 8,
                    rowY + NETWORK_ROW_HEIGHT - 4, color);
            if (networks.get(index).selected()) {
                graphics.fill(panelLeft() + 8, rowY, panelLeft() + 11, rowY + NETWORK_ROW_HEIGHT - 4, ACCENT);
            }
        }
    }

    private void drawNetworkText(GuiGraphics graphics) {
        for (int visible = 0; visible < visibleNetworkRows(); visible++) {
            int index = networkOffset + visible;
            if (index >= networks.size()) break;
            NetworkSnapshot network = networks.get(index);
            int y = networkListTop() + visible * NETWORK_ROW_HEIGHT;
            graphics.drawString(font, font.plainSubstrByWidth(network.name(), sidebarWidth() - 30),
                    panelLeft() + 15, y + 5, 0xFFF4F7F8, false);
            Component role = Component.translatable(network.owner()
                    ? "screen.mobsstorage.network.role_owner"
                    : network.member() ? "screen.mobsstorage.network.role_member"
                    : "screen.mobsstorage.network.role_public");
            graphics.drawString(font, role, panelLeft() + 15, y + 17,
                    network.selected() ? ACCENT : MUTED, false);
        }
    }

    private void drawStorageCards(GuiGraphics graphics, NetworkSnapshot selected, int mouseX, int mouseY) {
        int listY = pageListTop();
        for (int visible = 0; visible < visiblePageRows(); visible++) {
            int index = nodeOffset + visible;
            if (index >= selected.nodes().size()) break;
            int y = listY + visible * 27;
            boolean hovered = inside(mouseX, mouseY, contentLeft(), y, contentWidth(), 23);
            graphics.fill(contentLeft(), y, panelRight() - 10, y + 23, hovered ? CARD_HOVER : CARD);
            if (selected.nodes().get(index).source()) {
                graphics.fill(contentLeft(), y, contentLeft() + 3, y + 23, 0xFF76D69A);
            }
        }
    }

    private void drawStorageText(GuiGraphics graphics, NetworkSnapshot selected) {
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.storage_heading"),
                contentLeft(), pageHeadingY(), 0xFFDCE6EA, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.storage_hint"),
                contentLeft(), pageHeadingY() + 12, MUTED, false);
        if (selected.nodes().isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("screen.mobsstorage.network.no_storages"),
                    contentLeft(), pageListTop() + 4, contentWidth(), MUTED);
            return;
        }
        for (int visible = 0; visible < visiblePageRows(); visible++) {
            int index = nodeOffset + visible;
            if (index >= selected.nodes().size()) break;
            NetworkSnapshot.Node node = selected.nodes().get(index);
            int y = pageListTop() + visible * 27;
            Item icon = BuiltInRegistries.ITEM.get(node.icon());
            if (icon != Items.AIR) graphics.renderFakeItem(icon.getDefaultInstance(), contentLeft() + 5, y + 3);
            int textX = contentLeft() + 27;
            graphics.drawString(font, font.plainSubstrByWidth(node.name(), contentWidth() - 116),
                    textX, y + 4, 0xFFF1F4F5, false);
            String priority = Component.translatable("screen.mobsstorage.network.priority_short", node.priority()).getString();
            graphics.drawString(font, priority, panelRight() - 16 - font.width(priority), y + 8, MUTED, false);
            String details = node.source()
                    ? Component.translatable("screen.mobsstorage.network.source_badge").getString()
                    : node.pos().pos().getX() + ", " + node.pos().pos().getY() + ", " + node.pos().pos().getZ();
            graphics.drawString(font, details, textX, y + 14, node.source() ? 0xFF76D69A : MUTED, false);
        }
    }

    private void drawMemberCards(GuiGraphics graphics, NetworkSnapshot selected, int mouseX, int mouseY) {
        int listY = pageListTop();
        for (int visible = 0; visible < visiblePageRows(); visible++) {
            int index = memberOffset + visible;
            if (index >= selected.members().size()) break;
            int y = listY + visible * 24;
            graphics.fill(contentLeft(), y, panelRight() - 10, y + 20, CARD);
            if (selected.owner()) {
                boolean hovered = inside(mouseX, mouseY, panelRight() - 32, y, 22, 20);
                graphics.fill(panelRight() - 32, y, panelRight() - 10, y + 20,
                        hovered ? 0xFF8F4141 : 0xFF593333);
            }
        }
    }

    private void drawMemberText(GuiGraphics graphics, NetworkSnapshot selected) {
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.access_heading"),
                contentLeft(), pageHeadingY(), 0xFFDCE6EA, false);
        graphics.drawString(font, Component.translatable(selected.publicAccess()
                        ? "screen.mobsstorage.network.public_hint" : "screen.mobsstorage.network.private_hint"),
                contentLeft(), pageHeadingY() + 12, MUTED, false);
        int startY = pageListTop();
        if (selected.members().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.network.no_members"),
                    contentLeft(), startY + 4, MUTED, false);
            return;
        }
        for (int visible = 0; visible < visiblePageRows(); visible++) {
            int index = memberOffset + visible;
            if (index >= selected.members().size()) break;
            int y = pageListTop() + visible * 24;
            NetworkSnapshot.Member member = selected.members().get(index);
            graphics.drawString(font, member.name(), contentLeft() + 7, y + 6, 0xFFF1F4F5, false);
            if (selected.owner()) graphics.drawCenteredString(font, "x", panelRight() - 21, y + 6, 0xFFFFFFFF);
        }
    }

    private Component refillStatus(NetworkSnapshot selected) {
        NetworkSnapshot.Node source = selected.nodes().stream().filter(NetworkSnapshot.Node::source).findFirst().orElse(null);
        if (source == null) return Component.translatable("screen.mobsstorage.network.refill_no_source");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !source.pos().dimension().equals(minecraft.level.dimension())) {
            return Component.translatable("screen.mobsstorage.network.refill_other_dimension");
        }
        int dx = Math.abs(source.pos().pos().getX() - minecraft.player.getBlockX());
        int dy = Math.abs(source.pos().pos().getY() - minecraft.player.getBlockY());
        int dz = Math.abs(source.pos().pos().getZ() - minecraft.player.getBlockZ());
        int distance = Math.max(dx, Math.max(dy, dz));
        return distance <= 256
                ? Component.translatable("screen.mobsstorage.network.refill_ready", distance)
                : Component.translatable("screen.mobsstorage.network.refill_out_of_range", distance);
    }

    private boolean refillReady(NetworkSnapshot selected) {
        NetworkSnapshot.Node source = selected.nodes().stream().filter(NetworkSnapshot.Node::source).findFirst().orElse(null);
        Minecraft minecraft = Minecraft.getInstance();
        if (source == null || minecraft.player == null || minecraft.level == null
                || !source.pos().dimension().equals(minecraft.level.dimension())) return false;
        GlobalPos pos = source.pos();
        return Math.abs(pos.pos().getX() - minecraft.player.getBlockX()) <= 256
                && Math.abs(pos.pos().getY() - minecraft.player.getBlockY()) <= 256
                && Math.abs(pos.pos().getZ() - minecraft.player.getBlockZ()) <= 256;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inside(mouseX, mouseY, panelLeft() + 8, networkListTop(),
                sidebarWidth() - 16, visibleNetworkRows() * NETWORK_ROW_HEIGHT)) {
            int index = networkOffset + (int) ((mouseY - networkListTop()) / NETWORK_ROW_HEIGHT);
            if (index >= 0 && index < networks.size()) {
                selectedIndex = index;
                memberOffset = 0;
                nodeOffset = 0;
                rebuildWidgets();
                return true;
            }
        }
        NetworkSnapshot selected = selected();
        if (button == 0 && page == Page.ACCESS && selected != null && selected.owner()
                && inside(mouseX, mouseY, panelRight() - 32, pageListTop(), 22, visiblePageRows() * 24)) {
            int index = memberOffset + (int) ((mouseY - pageListTop()) / 24);
            if (index >= 0 && index < selected.members().size()) {
                send(NetworkActionPayload.Action.REMOVE_MEMBER, selected.id(), selected.members().get(index).id(), "");
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = -(int) Math.signum(scrollY);
        NetworkSnapshot selected = selected();
        if (mouseX < panelLeft() + sidebarWidth()) {
            networkOffset = clamp(networkOffset + direction, 0, Math.max(0, networks.size() - visibleNetworkRows()));
            return true;
        }
        if (selected != null && page == Page.STORAGE) {
            nodeOffset = clamp(nodeOffset + direction, 0, Math.max(0, selected.nodes().size() - visiblePageRows()));
            return true;
        }
        if (selected != null) {
            memberOffset = clamp(memberOffset + direction, 0, Math.max(0, selected.members().size() - visiblePageRows()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void setPage(Page value) {
        page = value;
        rebuildWidgets();
    }

    boolean accessPage() {
        return page == Page.ACCESS;
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

    private int panelWidth() { return Math.min(620, width - 20); }
    private int panelHeight() { return Math.min(410, height - 20); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int panelRight() { return panelLeft() + panelWidth(); }
    private int panelBottom() { return panelTop() + panelHeight(); }
    private int sidebarWidth() { return Math.min(SIDEBAR_WIDTH, Math.max(112, panelWidth() / 3)); }
    private int contentLeft() { return panelLeft() + sidebarWidth() + 12; }
    private int contentWidth() { return panelRight() - contentLeft() - 10; }
    private int networkListTop() { return panelTop() + HEADER_HEIGHT + 24; }
    private int visibleNetworkRows() {
        return Math.max(1, (panelBottom() - 44 - networkListTop()) / NETWORK_ROW_HEIGHT);
    }
    private int pageHeadingY() { return panelTop() + HEADER_HEIGHT + 134; }
    private int pageListTop() {
        int base = pageHeadingY() + 29;
        return page == Page.ACCESS && selected() != null && selected().owner() ? base + 28 : base;
    }
    private int visiblePageRows() {
        int row = page == Page.STORAGE ? 27 : 24;
        return Math.max(1, (panelBottom() - 42 - pageListTop()) / row);
    }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    @Override public boolean isPauseScreen() { return false; }

    private enum Page { STORAGE, ACCESS }
}
