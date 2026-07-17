package org.destroyermob.mobsstorage.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;

public final class FilterRules {
    public static final int MAX_FILTERS = 64;
    private static final Map<String, Expression> COMPILED = new ConcurrentHashMap<>();

    private FilterRules() {
    }

    public static List<String> normalize(String input) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        input.lines().map(String::trim).filter(line -> !line.isEmpty()).limit(MAX_FILTERS).forEach(values::add);
        return List.copyOf(values);
    }

    public static Optional<Component> validateIcon(ResourceLocation icon) {
        if (!BuiltInRegistries.ITEM.containsKey(icon) || BuiltInRegistries.ITEM.get(icon) == Items.AIR) {
            return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_item", icon));
        }
        return Optional.empty();
    }

    public static Optional<Component> validate(List<String> filters) {
        if (filters.size() > MAX_FILTERS) {
            return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_entry", "too many entries"));
        }
        for (String filter : filters) {
            Expression expression;
            try {
                expression = compile(filter);
            } catch (IllegalArgumentException exception) {
                return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_entry", filter));
            }
            for (List<Term> group : expression.alternatives()) {
                for (Term term : group) {
                    if (term.type() == TermType.ITEM_ID) {
                        ResourceLocation id = ResourceLocation.tryParse(term.value());
                        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)
                                || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
                            return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_item", term.value()));
                        }
                    } else if (term.type() == TermType.EXACT_TAG) {
                        ResourceLocation id = ResourceLocation.tryParse(term.value());
                        if (id == null || BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).isEmpty()) {
                            return Optional.of(Component.translatable("screen.mobsstorage.label.invalid_tag", "#" + term.value()));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean matches(ItemStack stack, List<String> filters) {
        return matches(stack, filters, Item.TooltipContext.EMPTY);
    }

    public static boolean matches(ItemStack stack, List<String> filters, Item.TooltipContext tooltipContext) {
        if (stack.isEmpty() || filters.isEmpty()) {
            return true;
        }
        MatchContext context = new MatchContext(stack, tooltipContext);
        for (String filter : filters) {
            try {
                if (compile(filter).matches(context)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Saved filters are validated before commit; malformed external data safely matches nothing.
            }
        }
        return false;
    }

    private static Expression compile(String source) {
        return COMPILED.computeIfAbsent(source, FilterRules::parse);
    }

    private static Expression parse(String source) {
        List<String> tokens = tokenize(source.trim());
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("empty expression");
        }
        List<List<Term>> alternatives = new ArrayList<>();
        List<Term> current = new ArrayList<>();
        for (String token : tokens) {
            if (token.equals("|")) {
                if (current.isEmpty()) {
                    throw new IllegalArgumentException("empty alternative");
                }
                alternatives.add(List.copyOf(current));
                current.clear();
            } else {
                current.add(parseTerm(token));
            }
        }
        if (current.isEmpty()) {
            throw new IllegalArgumentException("empty alternative");
        }
        alternatives.add(List.copyOf(current));
        return new Expression(List.copyOf(alternatives));
    }

    private static List<String> tokenize(String source) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (!quoted && (Character.isWhitespace(character) || character == '|')) {
                if (!token.isEmpty()) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
                if (character == '|') {
                    tokens.add("|");
                }
            } else {
                token.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("unterminated quote");
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    private static Term parseTerm(String source) {
        boolean negated = source.startsWith("-");
        String token = negated ? source.substring(1) : source;
        if (token.isBlank()) {
            throw new IllegalArgumentException("empty term");
        }
        char prefix = token.charAt(0);
        String value;
        TermType type;
        if (prefix == '#') {
            value = token.substring(1);
            type = value.contains(":") ? TermType.EXACT_TAG : TermType.TAG_SEARCH;
        } else if (prefix == '@') {
            value = token.substring(1);
            type = TermType.MOD;
        } else if (prefix == '&') {
            value = token.substring(1);
            type = TermType.RESOURCE;
        } else if (prefix == '$') {
            value = token.substring(1);
            type = TermType.TOOLTIP;
        } else {
            value = token;
            type = value.contains(":") ? TermType.ITEM_ID : TermType.NAME;
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("empty prefixed term");
        }
        if ((type == TermType.ITEM_ID || type == TermType.EXACT_TAG) && ResourceLocation.tryParse(value) == null) {
            throw new IllegalArgumentException("malformed resource location");
        }
        return new Term(negated, type, value);
    }

    private enum TermType {
        ITEM_ID,
        EXACT_TAG,
        TAG_SEARCH,
        MOD,
        RESOURCE,
        TOOLTIP,
        NAME
    }

    private record Term(boolean negated, TermType type, String value) {
        boolean matches(MatchContext context) {
            boolean result = switch (type) {
                case ITEM_ID -> context.itemId().equals(ResourceLocation.tryParse(value));
                case EXACT_TAG -> context.stack().is(TagKey.create(
                        Registries.ITEM, ResourceLocation.parse(value)));
                case TAG_SEARCH -> context.stack().getTags()
                        .anyMatch(tag -> tag.location().toString().toLowerCase(Locale.ROOT).contains(value));
                case MOD -> context.modText().contains(value);
                case RESOURCE -> context.itemId().toString().toLowerCase(Locale.ROOT).contains(value);
                case TOOLTIP -> context.tooltipText().contains(value);
                case NAME -> context.nameText().contains(value)
                        || context.itemId().getPath().toLowerCase(Locale.ROOT).contains(value);
            };
            return negated ? !result : result;
        }
    }

    private record Expression(List<List<Term>> alternatives) {
        boolean matches(MatchContext context) {
            for (List<Term> group : alternatives) {
                if (group.stream().allMatch(term -> term.matches(context))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class MatchContext {
        private final ItemStack stack;
        private final ResourceLocation itemId;
        private final Item.TooltipContext tooltipContext;
        private String nameText;
        private String tooltipText;
        private String modText;

        private MatchContext(ItemStack stack, Item.TooltipContext tooltipContext) {
            this.stack = stack;
            this.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            this.tooltipContext = tooltipContext;
        }

        ItemStack stack() {
            return stack;
        }

        ResourceLocation itemId() {
            return itemId;
        }

        String nameText() {
            if (nameText == null) {
                nameText = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            }
            return nameText;
        }

        String tooltipText() {
            if (tooltipText == null) {
                try {
                    tooltipText = stack.getTooltipLines(tooltipContext, null, TooltipFlag.NORMAL).stream()
                            .map(Component::getString)
                            .map(text -> text.toLowerCase(Locale.ROOT))
                            .reduce("", (left, right) -> left + "\n" + right);
                } catch (RuntimeException exception) {
                    tooltipText = nameText();
                }
            }
            return tooltipText;
        }

        String modText() {
            if (modText == null) {
                String displayName = ModList.get().getModContainerById(itemId.getNamespace())
                        .map(container -> container.getModInfo().getDisplayName())
                        .orElse("");
                modText = (itemId.getNamespace() + " " + displayName).toLowerCase(Locale.ROOT);
            }
            return modText;
        }
    }
}
