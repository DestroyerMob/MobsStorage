package org.destroyermob.mobsstorage.client;

import java.util.List;
import java.util.Optional;
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
import org.destroyermob.mobsstorage.registry.ModItems;

public final class NetworkManagerScreen extends Screen {
    private static final int HEADER_HEIGHT = 36;
    private static final int SIDEBAR_WIDTH = 176;
    private static final int NETWORK_ROW_HEIGHT = 28;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_HOVER = 0xFFA0A0A0;
    private static final int SLOT_SELECTED = 0xFFADADAD;
    private static final int TEXT = 0xFF404040;
    private static final int MUTED = 0xFF606060;
    private static final int SLOT_TEXT = 0xFFFFFFFF;
    private static final int SLOT_MUTED = 0xFFE0E0E0;
    private static final int ACTIVE = 0xFF55AA55;
    private static final int WARNING = 0xFFFFD070;
    private static final int ERROR = 0xFFFF7777;

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
        this(payload, Page.STORAGE.ordinal());
    }

    NetworkManagerScreen(OpenNetworkManagerPayload payload, int pageIndex) {
        super(Component.translatable("screen.mobsstorage.network.title"));
        this.networks = payload.networks();
        this.selectedIndex = initialSelection();
        this.page = Page.values()[clamp(pageIndex, 0, Page.values().length - 1)];
    }

    @Override
    protected void init() {
        int createY = panelBottom() - 28;
        int createWidth = sidebarWidth() - 70;
        createName = addRenderableWidget(new EditBox(font, panelLeft() + 8, createY, createWidth, 20,
                Component.translatable("screen.mobsstorage.network.new_name")));
        createName.setMaxLength(48);
        createName.setHint(Component.translatable("screen.mobsstorage.network.new_name"));
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.create"), button ->
                send(NetworkActionPayload.Action.CREATE, NetworkActionPayload.NONE,
                        NetworkActionPayload.NONE, createName.getValue()))
                .bounds(panelLeft() + 12 + createWidth, createY, 50, 20).build());

        NetworkSnapshot selected = selected();
        if (selected == null) return;
        int x = contentLeft();
        int contentWidth = contentWidth();
        int top = detailTop();
        networkName = addRenderableWidget(new EditBox(font, x, top, contentWidth - 62, 20,
                Component.translatable("screen.mobsstorage.network.name")));
        networkName.setMaxLength(48);
        networkName.setValue(selected.name());
        networkName.active = selected.owner();
        Button rename = addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.rename"), button ->
                send(NetworkActionPayload.Action.RENAME, selected.id(), NetworkActionPayload.NONE, networkName.getValue()))
                .bounds(x + contentWidth - 58, top, 58, 20).build());
        rename.active = selected.owner();

        int actionY = top + 24;
        int actionWidth = (contentWidth - 8) / 3;
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

        int tabY = top + 78;
        int tabWidth = (contentWidth - 8) / 3;
        Button storageTab = addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.network.storage_tab"), button -> setPage(Page.STORAGE))
                .bounds(x, tabY, tabWidth, 20).build());
        storageTab.active = page != Page.STORAGE;
        Button diagnosticsTab = addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.network.diagnostics_tab"), button -> setPage(Page.DIAGNOSTICS))
                .bounds(x + tabWidth + 4, tabY, tabWidth, 20).build());
        diagnosticsTab.active = page != Page.DIAGNOSTICS;
        Button accessTab = addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.network.access_tab"), button -> setPage(Page.ACCESS))
                .bounds(x + (tabWidth + 4) * 2, tabY,
                        contentWidth - (tabWidth + 4) * 2, 20).build());
        accessTab.active = page != Page.ACCESS;

        if (page == Page.ACCESS && selected.owner()) {
            int memberY = pageHeadingY() + 23;
            memberName = addRenderableWidget(new EditBox(font, x, memberY, contentWidth - 64, 20,
                    Component.translatable("screen.mobsstorage.network.member_name")));
            memberName.setMaxLength(32);
            memberName.setHint(Component.translatable("screen.mobsstorage.network.member_name"));
            addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.add"), button ->
                    send(NetworkActionPayload.Action.ADD_MEMBER, selected.id(), NetworkActionPayload.NONE, memberName.getValue()))
                    .bounds(x + contentWidth - 60, memberY, 60, 20).build());
        }

        int bottomY = panelBottom() - 28;
        if (page == Page.DIAGNOSTICS) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.network.refresh"), button ->
                    send(NetworkActionPayload.Action.REFRESH, selected.id(), NetworkActionPayload.NONE, ""))
                    .bounds(x, bottomY, 62, 20).build());
        }
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
        renderTransparentBackground(graphics);
        drawPanel(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawForeground(graphics, mouseX, mouseY);
        drawDiagnosticTooltip(graphics, mouseX, mouseY);
    }

    /** Prevent Screen.render() from invoking Minecraft's post-process blur after the panel is drawn. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void drawPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        drawRaisedPanel(graphics, panelLeft(), panelTop(), panelWidth(), panelHeight());
        int dividerX = panelLeft() + sidebarWidth();
        graphics.fill(panelLeft() + 3, panelTop() + HEADER_HEIGHT - 1,
                panelRight() - 3, panelTop() + HEADER_HEIGHT, PANEL_DARK);
        graphics.fill(panelLeft() + 3, panelTop() + HEADER_HEIGHT,
                panelRight() - 3, panelTop() + HEADER_HEIGHT + 1, PANEL_LIGHT);
        graphics.fill(dividerX - 1, panelTop() + HEADER_HEIGHT, dividerX, panelBottom() - 3, PANEL_DARK);
        graphics.fill(dividerX, panelTop() + HEADER_HEIGHT, dividerX + 1, panelBottom() - 3, PANEL_LIGHT);
        drawNetworkCards(graphics, mouseX, mouseY);
        NetworkSnapshot selected = selected();
        if (selected != null) {
            drawInset(graphics, contentLeft(), statusY(), contentWidth(), 26, SLOT);
            if (page == Page.STORAGE) drawStorageCards(graphics, selected, mouseX, mouseY);
            else if (page == Page.DIAGNOSTICS) drawDiagnosticCards(graphics, selected, mouseX, mouseY);
            else drawMemberCards(graphics, selected, mouseX, mouseY);
        }
    }

    private void drawForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        Item wand = ModItems.NETWORK_WAND.get();
        graphics.renderFakeItem(wand.getDefaultInstance(), panelLeft() + 10, panelTop() + 10);
        graphics.drawString(font, title, panelLeft() + 34, panelTop() + 8, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.subtitle"),
                panelLeft() + 34, panelTop() + 20, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.available"),
                panelLeft() + 8, panelTop() + HEADER_HEIGHT + 7, TEXT, false);
        if (networks.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("screen.mobsstorage.network.empty"),
                    panelLeft() + 8, panelTop() + HEADER_HEIGHT + 22, sidebarWidth() - 16, MUTED);
            return;
        }
        drawNetworkText(graphics);
        NetworkSnapshot selected = selected();
        if (selected == null) return;
        String owner = Component.translatable("screen.mobsstorage.network.owner", selected.ownerName()).getString();
        graphics.drawString(font, owner, contentLeft() + 6, statusY() + 5, SLOT_TEXT, false);
        NetworkSnapshot.Node origin = origin(selected);
        graphics.drawString(font, origin == null
                        ? Component.translatable("screen.mobsstorage.network.origin_unset")
                        : Component.translatable("screen.mobsstorage.network.origin", origin.name()),
                contentLeft() + 6, statusY() + 15, origin == null ? WARNING : ACTIVE, false);
        if (page == Page.STORAGE) drawStorageText(graphics, selected);
        else if (page == Page.DIAGNOSTICS) drawDiagnosticsText(graphics, selected);
        else drawMemberText(graphics, selected);
    }

    private void drawNetworkCards(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = networkListTop();
        int rows = visibleNetworkRows();
        for (int visible = 0; visible < rows; visible++) {
            int index = networkOffset + visible;
            if (index >= networks.size()) break;
            int rowY = y + visible * NETWORK_ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, panelLeft() + 8, rowY,
                    sidebarWidth() - 16, NETWORK_ROW_HEIGHT - 3);
            int color = index == selectedIndex ? SLOT_SELECTED : hovered ? SLOT_HOVER : SLOT;
            drawInset(graphics, panelLeft() + 8, rowY, sidebarWidth() - 16, NETWORK_ROW_HEIGHT - 3, color);
            if (networks.get(index).selected()) {
                graphics.fill(panelLeft() + 10, rowY + 2, panelLeft() + 13,
                        rowY + NETWORK_ROW_HEIGHT - 5, ACTIVE);
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
                    panelLeft() + 15, y + 4, SLOT_TEXT, false);
            Component role = Component.translatable(network.owner()
                    ? "screen.mobsstorage.network.role_owner"
                    : network.member() ? "screen.mobsstorage.network.role_member"
                    : "screen.mobsstorage.network.role_public");
            graphics.drawString(font, role, panelLeft() + 15, y + 14,
                    network.selected() ? ACTIVE : SLOT_MUTED, false);
        }
    }

    private void drawStorageCards(GuiGraphics graphics, NetworkSnapshot selected, int mouseX, int mouseY) {
        int listY = pageListTop();
        for (int visible = 0; visible < visiblePageRows(); visible++) {
            int index = nodeOffset + visible;
            if (index >= selected.nodes().size()) break;
            int y = listY + visible * 27;
            boolean hovered = inside(mouseX, mouseY, contentLeft(), y, contentWidth(), 23);
            drawInset(graphics, contentLeft(), y, contentWidth(), 23, hovered ? SLOT_HOVER : SLOT);
            if (selected.nodes().get(index).origin()) {
                graphics.fill(contentLeft() + 2, y + 2, contentLeft() + 5, y + 21, ACTIVE);
            }
        }
    }

    private void drawStorageText(GuiGraphics graphics, NetworkSnapshot selected) {
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.storage_heading"),
                contentLeft(), pageHeadingY(), TEXT, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.storage_hint"),
                    contentLeft(), pageHeadingY() + 11, MUTED, false);
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
                    textX, y + 3, SLOT_TEXT, false);
            String priority = Component.translatable("screen.mobsstorage.network.priority_short", node.priority()).getString();
            graphics.drawString(font, priority, panelRight() - 16 - font.width(priority), y + 7, SLOT_MUTED, false);
            String details = node.origin()
                    ? Component.translatable("screen.mobsstorage.network.origin_badge").getString()
                    : node.pos().pos().getX() + ", " + node.pos().pos().getY() + ", " + node.pos().pos().getZ();
            graphics.drawString(font, details, textX, y + 13, node.origin() ? ACTIVE : SLOT_MUTED, false);
        }
    }

    private void drawDiagnosticCards(GuiGraphics graphics, NetworkSnapshot selected, int mouseX, int mouseY) {
        int listY = pageListTop();
        for (int visible = 0; visible < visiblePageRows(); visible++) {
            int index = nodeOffset + visible;
            if (index >= selected.nodes().size()) break;
            int y = listY + visible * 27;
            boolean hovered = inside(mouseX, mouseY, contentLeft(), y, contentWidth(), 23);
            drawInset(graphics, contentLeft(), y, contentWidth(), 23, hovered ? SLOT_HOVER : SLOT);
            graphics.fill(contentLeft() + 2, y + 2, contentLeft() + 5, y + 21,
                    diagnosticColor(selected.nodes().get(index).status()));
        }
    }

    private void drawDiagnosticsText(GuiGraphics graphics, NetworkSnapshot selected) {
        long issues = selected.nodes().stream().filter(node -> !node.status().healthy()).count();
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.diagnostics_heading"),
                contentLeft(), pageHeadingY(), TEXT, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.diagnostics_summary",
                        selected.nodes().size() - issues, issues),
                contentLeft(), pageHeadingY() + 11, issues == 0 ? ACTIVE : WARNING, false);
        if (selected.nodes().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.mobsstorage.network.no_storages"),
                    contentLeft(), pageListTop() + 4, MUTED, false);
            return;
        }
        for (int visible = 0; visible < visiblePageRows(); visible++) {
            int index = nodeOffset + visible;
            if (index >= selected.nodes().size()) break;
            NetworkSnapshot.Node node = selected.nodes().get(index);
            int y = pageListTop() + visible * 27;
            Item icon = BuiltInRegistries.ITEM.get(node.icon());
            if (icon != Items.AIR) graphics.renderFakeItem(icon.getDefaultInstance(), contentLeft() + 7, y + 3);
            int textX = contentLeft() + 29;
            graphics.drawString(font, font.plainSubstrByWidth(node.name(), contentWidth() - 138),
                    textX, y + 3, SLOT_TEXT, false);
            Component status = Component.translatable("screen.mobsstorage.network.status."
                    + node.status().name().toLowerCase());
            graphics.drawString(font, status, textX, y + 13, diagnosticColor(node.status()), false);
            if (node.totalSlots() > 0) {
                String capacity = Component.translatable("screen.mobsstorage.network.slot_capacity",
                        node.usedSlots(), node.totalSlots()).getString();
                graphics.drawString(font, capacity, panelRight() - 16 - font.width(capacity),
                        y + 7, SLOT_MUTED, false);
            }
        }
    }

    private static int diagnosticColor(NetworkSnapshot.NodeStatus status) {
        return switch (status) {
            case ONLINE -> ACTIVE;
            case UNLOADED -> WARNING;
            case UNAVAILABLE, MISSING, WRONG_NETWORK, NO_ORIGIN, OUT_OF_RANGE -> ERROR;
        };
    }

    private void drawDiagnosticTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        NetworkSnapshot selected = selected();
        if (page != Page.DIAGNOSTICS || selected == null
                || !inside(mouseX, mouseY, contentLeft(), pageListTop(),
                contentWidth(), visiblePageRows() * 27)) return;
        int index = nodeOffset + (mouseY - pageListTop()) / 27;
        if (index < 0 || index >= selected.nodes().size()) return;
        NetworkSnapshot.Node node = selected.nodes().get(index);
        Component location = Component.literal(node.pos().dimension().location() + " · "
                + node.pos().pos().getX() + ", " + node.pos().pos().getY() + ", " + node.pos().pos().getZ());
        Component status = Component.translatable("screen.mobsstorage.network.status."
                + node.status().name().toLowerCase());
        List<Component> lines = node.totalSlots() > 0
                ? List.of(Component.literal(node.name()), status, location,
                Component.translatable("screen.mobsstorage.network.slot_capacity",
                        node.usedSlots(), node.totalSlots()))
                : List.of(Component.literal(node.name()), status, location);
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    private void drawMemberCards(GuiGraphics graphics, NetworkSnapshot selected, int mouseX, int mouseY) {
        int listY = pageListTop();
        for (int visible = 0; visible < visiblePageRows(); visible++) {
            int index = memberOffset + visible;
            if (index >= selected.members().size()) break;
            int y = listY + visible * 24;
            drawInset(graphics, contentLeft(), y, contentWidth(), 20, SLOT);
            if (selected.owner()) {
                boolean hovered = inside(mouseX, mouseY, panelRight() - 32, y, 22, 20);
                drawRaisedControl(graphics, panelRight() - 30, y + 2, 18, 16, hovered);
            }
        }
    }

    private void drawMemberText(GuiGraphics graphics, NetworkSnapshot selected) {
        graphics.drawString(font, Component.translatable("screen.mobsstorage.network.access_heading"),
                contentLeft(), pageHeadingY(), TEXT, false);
        graphics.drawString(font, Component.translatable(selected.publicAccess()
                        ? "screen.mobsstorage.network.public_hint" : "screen.mobsstorage.network.private_hint"),
                contentLeft(), pageHeadingY() + 11, MUTED, false);
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
            graphics.drawString(font, member.name(), contentLeft() + 7, y + 6, SLOT_TEXT, false);
            if (selected.owner()) graphics.drawCenteredString(font, "x", panelRight() - 21, y + 6, 0xFF8B0000);
        }
    }

    private NetworkSnapshot.Node origin(NetworkSnapshot selected) {
        return selected.nodes().stream().filter(NetworkSnapshot.Node::origin).findFirst().orElse(null);
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
        if (selected != null && page != Page.ACCESS) {
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
        nodeOffset = 0;
        memberOffset = 0;
        rebuildWidgets();
    }

    int pageIndex() {
        return page.ordinal();
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

    private int panelWidth() { return Math.min(580, width - 16); }
    private int panelHeight() { return Math.min(330, height - 16); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int panelRight() { return panelLeft() + panelWidth(); }
    private int panelBottom() { return panelTop() + panelHeight(); }
    private int sidebarWidth() { return Math.min(SIDEBAR_WIDTH, Math.max(104, panelWidth() / 3)); }
    private int contentLeft() { return panelLeft() + sidebarWidth() + 10; }
    private int contentWidth() { return panelRight() - contentLeft() - 8; }
    private int detailTop() { return panelTop() + HEADER_HEIGHT + 8; }
    private int statusY() { return detailTop() + 48; }
    private int networkListTop() { return panelTop() + HEADER_HEIGHT + 21; }
    private int visibleNetworkRows() {
        return Math.max(0, (panelBottom() - 34 - networkListTop()) / NETWORK_ROW_HEIGHT);
    }
    private int pageHeadingY() { return detailTop() + 104; }
    private int pageListTop() {
        int base = pageHeadingY() + 24;
        return page == Page.ACCESS && selected() != null && selected().owner() ? base + 24 : base;
    }
    private int visiblePageRows() {
        int row = page == Page.ACCESS ? 24 : 27;
        return Math.max(0, (panelBottom() - 34 - pageListTop()) / row);
    }
    private static void drawRaisedPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 2, PANEL_LIGHT);
        graphics.fill(x, y, x + 2, y + height, PANEL_LIGHT);
        graphics.fill(x, y + height - 2, x + width, y + height, PANEL_DARK);
        graphics.fill(x + width - 2, y, x + width, y + height, PANEL_DARK);
    }
    private static void drawRaisedControl(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        drawRaisedPanel(graphics, x, y, width, height);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, hovered ? 0xFFD6D6D6 : PANEL);
    }
    private static void drawInset(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, PANEL_LIGHT);
        graphics.fill(x, y, x + width - 1, y + height - 1, PANEL_DARK);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
    }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    @Override public boolean isPauseScreen() { return false; }

    private enum Page { STORAGE, DIAGNOSTICS, ACCESS }
}
