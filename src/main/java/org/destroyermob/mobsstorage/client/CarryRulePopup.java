package org.destroyermob.mobsstorage.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.destroyermob.mobsstorage.inventory.CarryRule;
import org.destroyermob.mobsstorage.inventory.CarryRuleSet;
import org.destroyermob.mobsstorage.network.SaveCarryRulesPayload;
import org.destroyermob.mobsstorage.storage.FilterRules;

/** A small, sharp, paged overlay rendered above the existing container. */
final class CarryRulePopup {
    private static final int PANEL_WIDTH = 144;
    private static final int PANEL_HEIGHT = 96;
    private static final int ACCENT = 0xFFB26DFF;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;

    private final AbstractContainerScreen<?> parent;
    private final int inventorySlot;
    private final int anchorX;
    private final int anchorY;
    private final CarryRuleSet originalRules;
    private final CarryRule migratedLegacyRule;
    private final ItemStack slotStack;
    private final List<AbstractWidget> widgets = new ArrayList<>();

    private ItemStack exactStack;
    private boolean exactMode;
    private String expression;
    private int minimum;
    private int target;
    private int maximum;
    private int page;
    private int selectedCount;
    private int left;
    private int top;
    private int panelWidth;
    private boolean belowSlot;
    private Button saveButton;
    private AbstractWidget focused;
    private EditBox expressionBox;
    private Button previousSuggestionButton;
    private Button suggestionButton;
    private Button nextSuggestionButton;
    private List<String> suggestions = List.of();
    private int suggestionIndex;
    private Component validation = CommonComponents.EMPTY;

    CarryRulePopup(AbstractContainerScreen<?> parent, int inventorySlot,
                   int anchorX, int anchorY, CarryRuleSet rules) {
        this.parent = parent;
        this.inventorySlot = inventorySlot;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.originalRules = rules;
        Minecraft minecraft = Minecraft.getInstance();
        this.slotStack = minecraft.player == null
                ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(inventorySlot).copy();

        CarryRule existing = rules.ruleForSlot(inventorySlot).orElse(null);
        CarryRule legacy = null;
        if (existing == null && !slotStack.isEmpty()) {
            Item.TooltipContext tooltipContext = minecraft.level == null
                    ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(minecraft.level);
            legacy = rules.rules().stream()
                    .filter(rule -> !rule.slotted() && rule.matches(slotStack, tooltipContext))
                    .findFirst().orElse(null);
            existing = legacy;
        }
        migratedLegacyRule = legacy;

        int capacity = slotStack.isEmpty() ? 64 : slotStack.getMaxStackSize();
        if (existing != null) {
            exactMode = existing.exact();
            exactStack = existing.exactStack();
            expression = existing.expression();
            maximum = Math.clamp(existing.maximum(), 0, capacity);
            target = Math.clamp(existing.target(), 0, maximum);
            minimum = Math.clamp(existing.minimum(), 0, target);
        } else {
            exactMode = !slotStack.isEmpty();
            exactStack = slotStack.isEmpty() ? ItemStack.EMPTY : slotStack.copyWithCount(1);
            expression = slotStack.isEmpty() ? ""
                    : BuiltInRegistries.ITEM.getKey(slotStack.getItem()).toString();
            minimum = Math.max(1, capacity / 4);
            target = capacity;
            maximum = capacity;
        }
        rebuildWidgets();
    }

    AbstractContainerScreen<?> parent() {
        return parent;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        graphics.fill(anchorX, anchorY, anchorX + 16, anchorY + 16, 0x553F1D5C);
        graphics.renderOutline(anchorX - 1, anchorY - 1, 18, 18, ACCENT);
        graphics.fill(left + 2, top + 2, left + panelWidth + 2, top + PANEL_HEIGHT + 2, 0xA0000000);
        graphics.fill(left, top, left + panelWidth, top + PANEL_HEIGHT, 0xFF202020);
        graphics.renderOutline(left, top, panelWidth, PANEL_HEIGHT, 0xFF777777);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 3, ACCENT);
        int notchX = Math.clamp(anchorX + 8, left + 5, left + panelWidth - 5);
        int notchY = belowSlot ? top - 2 : top + PANEL_HEIGHT;
        graphics.fill(notchX - 2, notchY, notchX + 2, notchY + 2, ACCENT);

        Font font = Minecraft.getInstance().font;
        if (!slotStack.isEmpty()) graphics.renderItem(slotStack, left + 4, top + 3);
        String itemName = slotStack.isEmpty()
                ? Component.translatable("screen.mobsstorage.carry.empty_short").getString()
                : slotStack.getHoverName().getString();
        String header = Component.translatable(
                "screen.mobsstorage.carry.slot_compact", inventorySlot + 1, itemName).getString();
        graphics.drawString(font, font.plainSubstrByWidth(header, panelWidth - 42),
                left + 22, top + 8, TEXT, false);

        Component pageTitle = Component.translatable(page == 0
                ? "screen.mobsstorage.carry.page.match"
                : "screen.mobsstorage.carry.page.amounts");
        graphics.drawCenteredString(font, pageTitle, left + panelWidth / 2, top + 24,
                validation.equals(CommonComponents.EMPTY) ? MUTED : 0xFFFF7777);

        if (page == 0 && exactMode) {
            graphics.fill(left + 52, top + 38, left + panelWidth - 4, top + 54, 0xFF121212);
            graphics.renderOutline(left + 52, top + 38, panelWidth - 56, 16, 0xFF555555);
            graphics.drawString(font, Component.translatable("screen.mobsstorage.carry.exact_short"),
                    left + 56, top + 42, MUTED, false);
        }
        for (AbstractWidget widget : widgets) widget.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int index = widgets.size() - 1; index >= 0; index--) {
            AbstractWidget widget = widgets.get(index);
            if (!widget.mouseClicked(mouseX, mouseY, button)) continue;
            if (widget == previousSuggestionButton || widget == suggestionButton
                    || widget == nextSuggestionButton) {
                setFocused(expressionBox);
            } else if (widgets.contains(widget)) {
                setFocused(widget);
            }
            return true;
        }
        setFocused(null);
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return focused != null && focused.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return focused != null && focused.mouseReleased(mouseX, mouseY, button);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (page != 0 || exactMode || suggestions.size() < 2
                || mouseX < left || mouseX >= left + panelWidth
                || mouseY < top + 36 || mouseY >= top + 74) {
            return false;
        }
        cycleSuggestion(scrollY > 0.0D ? -1 : 1);
        return true;
    }

    boolean isTyping() {
        return focused instanceof EditBox;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focused == expressionBox && !exactMode) {
            if (keyCode == InputConstants.KEY_TAB) {
                acceptSuggestion();
                return true;
            }
            if (keyCode == InputConstants.KEY_UP) {
                cycleSuggestion(-1);
                return true;
            }
            if (keyCode == InputConstants.KEY_DOWN) {
                cycleSuggestion(1);
                return true;
            }
        }
        if (keyCode == InputConstants.KEY_TAB) {
            focusNext();
            return true;
        }
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                && validation.equals(CommonComponents.EMPTY)) {
            save();
            return true;
        }
        return focused != null && focused.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean charTyped(char codePoint, int modifiers) {
        return focused != null && focused.charTyped(codePoint, modifiers);
    }

    private void rebuildWidgets() {
        widgets.clear();
        focused = null;
        expressionBox = null;
        previousSuggestionButton = null;
        suggestionButton = null;
        nextSuggestionButton = null;
        panelWidth = Math.min(PANEL_WIDTH, parent.width - 8);
        left = Math.clamp(anchorX + 8 - panelWidth / 2,
                4, Math.max(4, parent.width - panelWidth - 4));
        int below = anchorY + 20;
        int above = anchorY - PANEL_HEIGHT - 4;
        if (below + PANEL_HEIGHT <= parent.height - 4) {
            belowSlot = true;
            top = below;
        } else if (above >= 4) {
            belowSlot = false;
            top = above;
        } else {
            belowSlot = parent.height - anchorY >= anchorY;
            top = Math.clamp(belowSlot ? below : above,
                    4, Math.max(4, parent.height - PANEL_HEIGHT - 4));
        }

        add(Button.builder(Component.literal("x"), button -> CarryRulesControls.closePopup())
                .bounds(left + panelWidth - 18, top + 3, 14, 14).build());
        add(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(left + 4, top + 20, 16, 16).build());
        add(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(left + panelWidth - 20, top + 20, 16, 16).build());

        if (page == 0) initMatchPage();
        else initAmountsPage();

        int footerWidth = (panelWidth - 11) / 2;
        Button remove = add(Button.builder(Component.translatable(
                        "screen.mobsstorage.carry.remove"), button -> remove())
                .bounds(left + 4, top + 76, footerWidth, 16).build());
        remove.active = originalRules.ruleForSlot(inventorySlot).isPresent() || migratedLegacyRule != null;
        saveButton = add(Button.builder(Component.translatable(
                        "screen.mobsstorage.carry.save"), button -> save())
                .bounds(left + 7 + footerWidth, top + 76, footerWidth, 16).build());
        updateValidation();
    }

    private void initMatchPage() {
        add(Button.builder(Component.translatable(exactMode
                        ? "screen.mobsstorage.carry.mode.exact_short"
                        : "screen.mobsstorage.carry.mode.filter"), button -> toggleMode())
                .bounds(left + 4, top + 38, 40, 16).build());
        if (!exactMode) {
            expressionBox = add(new EditBox(Minecraft.getInstance().font,
                    left + 47, top + 38, panelWidth - 51, 16,
                    Component.translatable("screen.mobsstorage.carry.expression")));
            expressionBox.setMaxLength(CarryRule.MAX_EXPRESSION_LENGTH);
            expressionBox.setHint(Component.translatable("screen.mobsstorage.label.filter_input_hint"));
            expressionBox.setValue(expression);
            expressionBox.setResponder(value -> {
                expression = value;
                suggestionIndex = 0;
                updateValidation();
                refreshSuggestions();
            });
            previousSuggestionButton = add(Button.builder(Component.literal("<"), button -> cycleSuggestion(-1))
                    .bounds(left + 4, top + 57, 16, 16).build());
            suggestionButton = add(Button.builder(CommonComponents.EMPTY, button -> acceptSuggestion())
                    .bounds(left + 22, top + 57, panelWidth - 44, 16).build());
            nextSuggestionButton = add(Button.builder(Component.literal(">"), button -> cycleSuggestion(1))
                    .bounds(left + panelWidth - 20, top + 57, 16, 16).build());
            refreshSuggestions();
            setFocused(expressionBox);
        }
    }

    private void refreshSuggestions() {
        if (exactMode || expressionBox == null) {
            suggestions = List.of();
        } else {
            suggestions = SuggestionIndex.matching(completionQuery(expression), slotStack);
        }
        suggestionIndex = suggestions.isEmpty() ? 0 : Math.clamp(suggestionIndex, 0, suggestions.size() - 1);
        updateSuggestionWidgets();
    }

    private void cycleSuggestion(int direction) {
        if (suggestions.isEmpty()) return;
        suggestionIndex = Math.floorMod(suggestionIndex + direction, suggestions.size());
        updateSuggestionWidgets();
    }

    private void acceptSuggestion() {
        if (expressionBox == null || suggestions.isEmpty()) return;
        String suggestion = suggestions.get(suggestionIndex);
        int start = completionStart(expression);
        String partial = expression.substring(start);
        boolean negated = partial.startsWith("-");
        expressionBox.setValue(expression.substring(0, start) + (negated ? "-" : "") + suggestion);
        expressionBox.moveCursorToEnd(false);
    }

    private void updateSuggestionWidgets() {
        if (suggestionButton == null) return;
        boolean available = !suggestions.isEmpty();
        previousSuggestionButton.active = available && suggestions.size() > 1;
        nextSuggestionButton.active = available && suggestions.size() > 1;
        suggestionButton.active = available;
        suggestionButton.setMessage(available
                ? Component.literal(suggestions.get(suggestionIndex))
                : Component.translatable("screen.mobsstorage.carry.no_suggestion"));
    }

    private static String completionQuery(String value) {
        String partial = value.substring(completionStart(value)).trim();
        return partial.startsWith("-") ? partial.substring(1) : partial;
    }

    private static int completionStart(String value) {
        for (int index = value.length() - 1; index >= 0; index--) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || character == '|') return index + 1;
        }
        return 0;
    }

    private void initAmountsPage() {
        String[] labels = {"Min", "Target", "Max"};
        int tabWidth = (panelWidth - 14) / 3;
        for (int index = 0; index < labels.length; index++) {
            int countIndex = index;
            Button tab = add(Button.builder(Component.literal(labels[index]), button -> {
                selectedCount = countIndex;
                rebuildWidgets();
            }).bounds(left + 4 + index * (tabWidth + 3), top + 38, tabWidth, 16).build());
            tab.active = selectedCount != index;
        }
        int lower = switch (selectedCount) {
            case 0 -> 0;
            case 1 -> minimum;
            default -> target;
        };
        int upper = switch (selectedCount) {
            case 0 -> target;
            case 1 -> maximum;
            default -> capacity();
        };
        int current = switch (selectedCount) {
            case 0 -> minimum;
            case 1 -> target;
            default -> maximum;
        };
        add(new CountSlider(left + 4, top + 57, panelWidth - 8, 16,
                lower, upper, current, labels[selectedCount], this::setSelectedCount));
    }

    private void changePage(int direction) {
        page = Math.floorMod(page + direction, 2);
        rebuildWidgets();
    }

    private void toggleMode() {
        if (exactMode) {
            exactMode = false;
            if (expression.isBlank() && !exactStack.isEmpty()) {
                expression = BuiltInRegistries.ITEM.getKey(exactStack.getItem()).toString();
            }
        } else {
            Minecraft minecraft = Minecraft.getInstance();
            ItemStack current = minecraft.player == null
                    ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(inventorySlot);
            if (current.isEmpty()) return;
            exactStack = current.copyWithCount(1);
            exactMode = true;
        }
        rebuildWidgets();
    }

    private void setSelectedCount(int value) {
        if (selectedCount == 0) minimum = value;
        else if (selectedCount == 1) target = value;
        else maximum = value;
    }

    private int capacity() {
        return exactStack.isEmpty() ? 64 : exactStack.getMaxStackSize();
    }

    private void save() {
        Optional<CarryRule> rule = buildRule();
        if (rule.isEmpty()) return;
        List<CarryRule> updated = mutableRulesWithoutCurrent();
        if (updated.size() >= CarryRuleSet.MAX_RULES) return;
        updated.add(rule.get());
        PacketDistributor.sendToServer(new SaveCarryRulesPayload(
                new CarryRuleSet(updated, originalRules.reservedEmptySlots())));
        CarryRulesControls.closePopup();
    }

    private void remove() {
        PacketDistributor.sendToServer(new SaveCarryRulesPayload(new CarryRuleSet(
                mutableRulesWithoutCurrent(), originalRules.reservedEmptySlots())));
        CarryRulesControls.closePopup();
    }

    private List<CarryRule> mutableRulesWithoutCurrent() {
        List<CarryRule> updated = new ArrayList<>(originalRules.rules());
        updated.removeIf(rule -> rule.inventorySlot() == inventorySlot);
        if (migratedLegacyRule != null) updated.remove(migratedLegacyRule);
        return updated;
    }

    private Optional<CarryRule> buildRule() {
        if (!validation.equals(CommonComponents.EMPTY)) return Optional.empty();
        CarryRule rule = new CarryRule(inventorySlot, exactMode ? "" : expression,
                exactMode ? exactStack : ItemStack.EMPTY, minimum, target, maximum);
        return rule.valid() ? Optional.of(rule) : Optional.empty();
    }

    private void updateValidation() {
        Optional<Component> error = validate();
        validation = error.orElse(CommonComponents.EMPTY);
        if (expressionBox != null) {
            expressionBox.setTextColor(error.isEmpty() ? 0xE0E0E0 : 0xFF6666);
        }
        if (saveButton != null) saveButton.active = error.isEmpty()
                && mutableRulesWithoutCurrent().size() < CarryRuleSet.MAX_RULES;
    }

    private Optional<Component> validate() {
        if (exactMode) {
            return exactStack.isEmpty()
                    ? Optional.of(Component.translatable("screen.mobsstorage.carry.exact_requires_item"))
                    : Optional.empty();
        }
        if (expression.isBlank()) {
            return Optional.of(Component.translatable("screen.mobsstorage.carry.empty_rule"));
        }
        return FilterRules.validate(List.of(expression.strip()));
    }

    private void focusNext() {
        List<AbstractWidget> focusable = widgets.stream()
                .filter(widget -> widget instanceof EditBox || widget instanceof AbstractSliderButton).toList();
        if (focusable.isEmpty()) return;
        int current = focused == null ? -1 : focusable.indexOf(focused);
        setFocused(focusable.get((current + 1) % focusable.size()));
    }

    private void setFocused(AbstractWidget widget) {
        for (AbstractWidget child : widgets) child.setFocused(child == widget);
        focused = widget;
    }

    private <T extends AbstractWidget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    /** The same contextual ordering and syntax used by the chest-label filter editor. */
    private static final class SuggestionIndex {
        private static final int MAX_RESULTS = 128;
        private static final List<String> ITEM_IDS = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                .sorted()
                .toList();
        private static final List<String> ITEM_TAGS = BuiltInRegistries.ITEM.getTagNames()
                .map(tag -> "#" + tag.location())
                .sorted()
                .toList();
        private static final List<String> MOD_FILTERS = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .map(item -> "@" + BuiltInRegistries.ITEM.getKey(item).getNamespace())
                .distinct()
                .sorted()
                .toList();

        private SuggestionIndex() {
        }

        private static List<String> matching(String rawQuery, ItemStack reference) {
            String query = rawQuery.trim().toLowerCase(Locale.ROOT);
            Set<String> selectedTags = new HashSet<>();
            String selectedId = "";
            String selectedMod = "";
            if (!reference.isEmpty()) {
                var id = BuiltInRegistries.ITEM.getKey(reference.getItem());
                selectedId = id.toString();
                selectedMod = "@" + id.getNamespace();
                reference.getTags().map(tag -> "#" + tag.location()).forEach(selectedTags::add);
            }

            java.util.stream.Stream<String> candidates;
            if (query.startsWith("#")) {
                candidates = ITEM_TAGS.stream();
            } else if (query.startsWith("@")) {
                candidates = MOD_FILTERS.stream();
            } else if (query.startsWith("&")) {
                candidates = ITEM_IDS.stream().map(id -> "&" + id);
            } else if (query.startsWith("$")) {
                candidates = java.util.stream.Stream.empty();
            } else {
                candidates = java.util.stream.Stream.of(
                        ITEM_IDS.stream(), ITEM_TAGS.stream(), MOD_FILTERS.stream())
                        .flatMap(stream -> stream);
            }

            String preferredId = selectedId;
            String preferredMod = selectedMod;
            return candidates
                    .filter(suggestion -> query.isEmpty()
                            || suggestion.toLowerCase(Locale.ROOT).contains(query))
                    .distinct()
                    .sorted(Comparator
                            .comparingInt((String suggestion) -> suggestionPriority(
                                    suggestion, preferredId, preferredMod, selectedTags))
                            .thenComparing(Comparator.naturalOrder()))
                    .limit(MAX_RESULTS)
                    .toList();
        }

        private static int suggestionPriority(
                String suggestion, String selectedId, String selectedMod, Set<String> selectedTags) {
            if (suggestion.equals(selectedId)) return 0;
            if (selectedTags.contains(suggestion)) return 1;
            if (suggestion.equals(selectedMod)) return 2;
            if (suggestion.startsWith("#c:")) return 3;
            if (suggestion.startsWith("@")) return 4;
            if (suggestion.startsWith("#")) return 5;
            return 6;
        }
    }

    private static final class CountSlider extends AbstractSliderButton {
        private final int minimum;
        private final int maximum;
        private final String label;
        private final IntConsumer consumer;

        private CountSlider(int x, int y, int width, int height, int minimum, int maximum,
                            int current, String label, IntConsumer consumer) {
            super(x, y, width, height, CommonComponents.EMPTY,
                    maximum <= minimum ? 0.0D : (current - minimum) / (double) (maximum - minimum));
            this.minimum = minimum;
            this.maximum = Math.max(minimum, maximum);
            this.label = label;
            this.consumer = consumer;
            active = this.maximum > this.minimum;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            consumer.accept(current());
        }

        private int current() {
            return minimum + (int) Math.round(value * (maximum - minimum));
        }
    }
}
