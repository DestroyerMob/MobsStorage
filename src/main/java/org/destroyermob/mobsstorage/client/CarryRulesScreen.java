package org.destroyermob.mobsstorage.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.inventory.CarryRule;
import org.destroyermob.mobsstorage.inventory.CarryRuleSet;
import org.destroyermob.mobsstorage.network.SaveCarryRulesPayload;
import org.destroyermob.mobsstorage.storage.FilterRules;

public final class CarryRulesScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int LIST_WIDTH = 142;
    private static final int ROW_HEIGHT = 22;

    private final List<RuleDraft> drafts = new ArrayList<>();
    private String reservedSlots;
    private int selected;
    private int listOffset;
    private Button saveButton;
    private Component validation = CommonComponents.EMPTY;

    public CarryRulesScreen(CarryRuleSet ruleSet) {
        super(Component.translatable("screen.mobsstorage.carry.title"));
        ruleSet.rules().forEach(rule -> drafts.add(new RuleDraft(rule)));
        reservedSlots = Integer.toString(ruleSet.reservedEmptySlots());
        selected = drafts.isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = 30;
        int footer = height - 28;
        int visibleRows = visibleRows();
        if (selected >= 0) listOffset = Math.clamp(listOffset, Math.max(0, selected - visibleRows + 1), selected);
        listOffset = Math.clamp(listOffset, 0, Math.max(0, drafts.size() - visibleRows));

        for (int row = 0; row < visibleRows && listOffset + row < drafts.size(); row++) {
            int index = listOffset + row;
            RuleDraft draft = drafts.get(index);
            Button entry = Button.builder(ruleName(draft, index), button -> select(index))
                    .bounds(left + 6, top + 17 + row * ROW_HEIGHT, LIST_WIDTH - 12, 20).build();
            entry.active = index != selected;
            addRenderableWidget(entry);
        }

        int controlsY = footer - 23;
        addRenderableWidget(Button.builder(Component.literal("+"), button -> addRule())
                .bounds(left + 6, controlsY, 28, 20).build()).active = drafts.size() < CarryRuleSet.MAX_RULES;
        Button remove = addRenderableWidget(Button.builder(Component.literal("−"), button -> removeRule())
                .bounds(left + 36, controlsY, 28, 20).build());
        remove.active = selected >= 0;
        Button up = addRenderableWidget(Button.builder(Component.literal("↑"), button -> moveRule(-1))
                .bounds(left + 72, controlsY, 28, 20).build());
        up.active = selected > 0;
        Button down = addRenderableWidget(Button.builder(Component.literal("↓"), button -> moveRule(1))
                .bounds(left + 102, controlsY, 28, 20).build());
        down.active = selected >= 0 && selected + 1 < drafts.size();

        if (selected >= 0 && selected < drafts.size()) initEditor(left + LIST_WIDTH + 10, top + 17,
                panelWidth() - LIST_WIDTH - 16, drafts.get(selected));
        initReserved(left + LIST_WIDTH + 10, top + 105);

        int actionWidth = 72;
        this.saveButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.mobsstorage.carry.save"), button -> save())
                .bounds(left + panelWidth() - actionWidth * 2 - 10, footer, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mobsstorage.carry.cancel"), button -> onClose())
                .bounds(left + panelWidth() - actionWidth - 6, footer, actionWidth, 20).build());
        updateValidation();
    }

    private void initEditor(int left, int top, int width, RuleDraft draft) {
        addRenderableWidget(Button.builder(draft.exact.isEmpty()
                        ? Component.translatable("screen.mobsstorage.carry.mode.filter")
                        : Component.translatable("screen.mobsstorage.carry.mode.exact"),
                button -> toggleExact(draft)).bounds(left, top, 74, 20).build());
        if (draft.exact.isEmpty()) {
            EditBox expression = addRenderableWidget(new EditBox(font, left + 78, top, width - 78, 20,
                    Component.translatable("screen.mobsstorage.carry.expression")));
            expression.setMaxLength(CarryRule.MAX_EXPRESSION_LENGTH);
            expression.setHint(Component.translatable("screen.mobsstorage.carry.expression_hint"));
            expression.setValue(draft.expression);
            expression.setResponder(value -> {
                draft.expression = value;
                updateValidation();
            });
        }

        int countsY = top + 43;
        numericBox(left, countsY, 62, draft.minimum, value -> draft.minimum = value);
        numericBox(left + 70, countsY, 62, draft.target, value -> draft.target = value);
        numericBox(left + 140, countsY, 62, draft.maximum, value -> draft.maximum = value);

    }

    private void initReserved(int left, int top) {
        EditBox reserved = addRenderableWidget(new EditBox(font, left, top, 62, 20,
                Component.translatable("screen.mobsstorage.carry.reserved")));
        reserved.setMaxLength(1);
        reserved.setFilter(CarryRulesScreen::isNumericInput);
        reserved.setValue(reservedSlots);
        reserved.setResponder(value -> {
            reservedSlots = value;
            updateValidation();
        });
    }

    private void numericBox(int x, int y, int width, String value, java.util.function.Consumer<String> responder) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, width, 20, CommonComponents.EMPTY));
        box.setMaxLength(4);
        box.setFilter(CarryRulesScreen::isNumericInput);
        box.setValue(value);
        box.setResponder(newValue -> {
            responder.accept(newValue);
            updateValidation();
        });
    }

    private void select(int index) {
        selected = index;
        rebuildWidgets();
    }

    private void addRule() {
        if (drafts.size() >= CarryRuleSet.MAX_RULES) return;
        ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        String expression = held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int stackSize = held.isEmpty() ? 64 : held.getMaxStackSize();
        drafts.add(new RuleDraft(expression, ItemStack.EMPTY, Integer.toString(Math.max(1, stackSize / 4)),
                Integer.toString(stackSize), Integer.toString(stackSize)));
        selected = drafts.size() - 1;
        rebuildWidgets();
    }

    private void removeRule() {
        if (selected < 0 || selected >= drafts.size()) return;
        drafts.remove(selected);
        selected = drafts.isEmpty() ? -1 : Math.min(selected, drafts.size() - 1);
        rebuildWidgets();
    }

    private void moveRule(int direction) {
        int other = selected + direction;
        if (selected < 0 || other < 0 || other >= drafts.size()) return;
        RuleDraft draft = drafts.remove(selected);
        drafts.add(other, draft);
        selected = other;
        rebuildWidgets();
    }

    private void toggleExact(RuleDraft draft) {
        if (!draft.exact.isEmpty()) {
            if (draft.expression.isBlank()) {
                draft.expression = BuiltInRegistries.ITEM.getKey(draft.exact.getItem()).toString();
            }
            draft.exact = ItemStack.EMPTY;
        } else if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            draft.exact = minecraft.player.getMainHandItem().copyWithCount(1);
        } else {
            validation = Component.translatable("screen.mobsstorage.carry.hold_item");
            return;
        }
        rebuildWidgets();
    }

    private void save() {
        Optional<List<CarryRule>> rules = buildRules();
        if (rules.isEmpty()) return;
        CarryRuleSet ruleSet = new CarryRuleSet(rules.get(), parse(reservedSlots));
        PacketDistributor.sendToServer(new SaveCarryRulesPayload(ruleSet));
        onClose();
    }

    private Optional<List<CarryRule>> buildRules() {
        List<CarryRule> rules = new ArrayList<>();
        for (RuleDraft draft : drafts) {
            if (!isNumber(draft.minimum) || !isNumber(draft.target) || !isNumber(draft.maximum)) return Optional.empty();
            CarryRule rule = new CarryRule(draft.expression, draft.exact, parse(draft.minimum),
                    parse(draft.target), parse(draft.maximum));
            if (!rule.valid()) return Optional.empty();
            rules.add(rule);
        }
        return Optional.of(rules);
    }

    private void updateValidation() {
        Optional<Component> error = validateDrafts();
        validation = error.orElse(CommonComponents.EMPTY);
        if (saveButton != null) saveButton.active = error.isEmpty();
    }

    private Optional<Component> validateDrafts() {
        if (!isNumber(reservedSlots) || parse(reservedSlots) > CarryRuleSet.MAX_RESERVED_SLOTS) {
            return Optional.of(Component.translatable("screen.mobsstorage.carry.invalid_reserved"));
        }
        for (RuleDraft draft : drafts) {
            if (!isNumber(draft.minimum) || !isNumber(draft.target) || !isNumber(draft.maximum)) {
                return Optional.of(Component.translatable("screen.mobsstorage.carry.invalid_count"));
            }
            int minimum = parse(draft.minimum);
            int target = parse(draft.target);
            int maximum = parse(draft.maximum);
            if (minimum > target || target > maximum || maximum > CarryRule.MAX_COUNT) {
                return Optional.of(Component.translatable("screen.mobsstorage.carry.invalid_order"));
            }
            if (draft.exact.isEmpty()) {
                if (draft.expression.isBlank()) return Optional.of(Component.translatable("screen.mobsstorage.carry.empty_rule"));
                Optional<Component> filterError = FilterRules.validate(List.of(draft.expression.strip()));
                if (filterError.isPresent()) return filterError;
            }
        }
        return Optional.empty();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int editorLeft = left + LIST_WIDTH + 10;
        int top = 30;
        graphics.fill(left, 8, left + panelWidth(), height - 4, 0xE0101010);
        graphics.renderOutline(left, 8, panelWidth(), height - 12, 0xFF777777);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.rules"), left + 6, top, 0xAAAAAA, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.editor"), editorLeft, top, 0xAAAAAA, false);
        graphics.fill(left + LIST_WIDTH, top, left + LIST_WIDTH + 1, height - 34, 0xFF555555);

        if (selected >= 0 && selected < drafts.size()) {
            RuleDraft draft = drafts.get(selected);
            if (!draft.exact.isEmpty()) {
                int boxLeft = editorLeft + 78;
                int boxWidth = panelWidth() - LIST_WIDTH - 94;
                graphics.fill(boxLeft, top + 17, boxLeft + boxWidth, top + 37, 0xFF202020);
                graphics.renderOutline(boxLeft, top + 17, boxWidth, 20, 0xFF777777);
                graphics.renderItem(draft.exact, boxLeft + 2, top + 19);
                graphics.drawString(font, font.plainSubstrByWidth(draft.exact.getHoverName().getString(), boxWidth - 23),
                        boxLeft + 21, top + 23, 0xFFFFFF, false);
            }
            int labelY = top + 49;
            graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.minimum"), editorLeft, labelY, 0xAAAAAA, false);
            graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.target"), editorLeft + 70, labelY, 0xAAAAAA, false);
            graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.maximum"), editorLeft + 140, labelY, 0xAAAAAA, false);
            graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.order_hint"),
                    editorLeft, top + 132, 0x888888, false);
        } else {
            graphics.drawWordWrap(font, Component.translatable("screen.mobsstorage.carry.empty"),
                    editorLeft, top + 22, panelWidth() - LIST_WIDTH - 20, 0xAAAAAA);
        }
        graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.reserved"), editorLeft, top + 93, 0xAAAAAA, false);
        graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.reserved_hint"),
                editorLeft + 70, top + 95, 0x888888, false);
        if (!validation.equals(CommonComponents.EMPTY)) {
            graphics.drawString(font, validation, editorLeft, height - 40, 0xFF7777, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component ruleName(RuleDraft draft, int index) {
        String name = draft.exact.isEmpty() ? draft.expression : draft.exact.getHoverName().getString();
        if (name.isBlank()) name = Component.translatable("screen.mobsstorage.carry.new_rule").getString();
        return Component.literal((index + 1) + ". " + font.plainSubstrByWidth(name, LIST_WIDTH - 35));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, width - 12);
    }

    private int visibleRows() {
        return Math.max(1, (height - 110) / ROW_HEIGHT);
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isNumber(String value) {
        if (value == null || value.isEmpty() || value.length() > 4) return false;
        for (int index = 0; index < value.length(); index++) if (!Character.isDigit(value.charAt(index))) return false;
        return true;
    }

    private static boolean isNumericInput(String value) {
        return value != null && (value.isEmpty() || isNumber(value));
    }

    private static final class RuleDraft {
        private String expression;
        private ItemStack exact;
        private String minimum;
        private String target;
        private String maximum;

        private RuleDraft(CarryRule rule) {
            this(rule.expression(), rule.exactStack(), Integer.toString(rule.minimum()),
                    Integer.toString(rule.target()), Integer.toString(rule.maximum()));
        }

        private RuleDraft(String expression, ItemStack exact, String minimum, String target, String maximum) {
            this.expression = expression;
            this.exact = exact.isEmpty() ? ItemStack.EMPTY : exact.copyWithCount(1);
            this.minimum = minimum;
            this.target = target;
            this.maximum = maximum;
        }
    }
}
