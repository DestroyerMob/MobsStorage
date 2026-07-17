package org.destroyermob.mobsstorage.inventory;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.destroyermob.mobsstorage.networking.NetworkRefillService;
import org.destroyermob.mobsstorage.registry.ModAttachments;

public final class CarryRuleService {
    private CarryRuleService() {
    }

    public static void save(ServerPlayer player, CarryRuleSet rules) {
        if (rules.valid()) player.setData(ModAttachments.CARRY_RULES, rules);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        CarryRuleSet ruleSet = rules(player);
        if (!ruleSet.valid() || ruleSet.rules().isEmpty()) return;
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(player.serverLevel());
        for (int index = 0; index < ruleSet.rules().size(); index++) {
            CarryRule rule = ruleSet.rules().get(index);
            int carried = countForRule(player.getInventory(), ruleSet, index, tooltipContext);
            if (carried < rule.minimum()) {
                NetworkRefillService.refillCarryRule(player, ruleSet, index,
                        rule.target() - carried, ruleSet.reservedEmptySlots());
            }
        }
    }

    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        int reserved = rules(player).reservedEmptySlots();
        if (reserved <= 0 || emptySlots(player.getInventory()) > reserved) return;
        ItemStack pickup = event.getItemEntity().getItem();
        if (blocksPickup(player.getInventory(), reserved, pickup)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    public static int depositableCount(ServerPlayer player, ItemStack stack) {
        return depositableCount(player, rules(player), CarryRule.LEGACY_GLOBAL_SLOT, stack);
    }

    public static int depositableCount(ServerPlayer player, int inventorySlot, ItemStack stack) {
        return depositableCount(player, rules(player), inventorySlot, stack);
    }

    public static int depositableCount(ServerPlayer player, CarryRuleSet ruleSet, ItemStack stack) {
        return depositableCount(player, ruleSet, CarryRule.LEGACY_GLOBAL_SLOT, stack);
    }

    public static int depositableCount(ServerPlayer player, CarryRuleSet ruleSet,
                                       int inventorySlot, ItemStack stack) {
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(player.serverLevel());
        Optional<Integer> ruleIndex = firstMatchingRule(ruleSet, inventorySlot, stack, tooltipContext);
        if (ruleIndex.isEmpty()) return stack.getCount();
        CarryRule rule = ruleSet.rules().get(ruleIndex.get());
        int carried = countForRule(player.getInventory(), ruleSet, ruleIndex.get(), tooltipContext);
        return Math.min(stack.getCount(), Math.max(0, carried - rule.maximum()));
    }

    public static boolean blocksPickup(Inventory inventory, int reservedEmptySlots, ItemStack pickup) {
        return reservedEmptySlots > 0 && emptySlots(inventory) <= reservedEmptySlots
                && mergeCapacity(inventory, pickup) < pickup.getCount();
    }

    public static boolean belongsToRule(CarryRuleSet ruleSet, int ruleIndex, ItemStack stack,
                                        Item.TooltipContext tooltipContext) {
        if (ruleIndex < 0 || ruleIndex >= ruleSet.rules().size()) return false;
        CarryRule rule = ruleSet.rules().get(ruleIndex);
        if (rule.slotted()) return rule.matches(stack, tooltipContext);
        return firstMatchingRule(ruleSet, CarryRule.LEGACY_GLOBAL_SLOT, stack, tooltipContext)
                .filter(index -> index == ruleIndex).isPresent();
    }

    static int countForRule(Inventory inventory, CarryRuleSet ruleSet, int ruleIndex,
                            Item.TooltipContext tooltipContext) {
        if (ruleIndex < 0 || ruleIndex >= ruleSet.rules().size()) return 0;
        CarryRule rule = ruleSet.rules().get(ruleIndex);
        if (rule.slotted()) {
            ItemStack stack = inventory.getItem(rule.inventorySlot());
            return rule.matches(stack, tooltipContext) ? stack.getCount() : 0;
        }
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (belongsToRule(ruleSet, ruleIndex, stack, tooltipContext)) count += stack.getCount();
        }
        return count;
    }

    private static Optional<Integer> firstMatchingRule(CarryRuleSet ruleSet, int inventorySlot, ItemStack stack,
                                                       Item.TooltipContext tooltipContext) {
        if (stack.isEmpty() || !ruleSet.valid()) return Optional.empty();
        if (inventorySlot >= 0) {
            for (int index = 0; index < ruleSet.rules().size(); index++) {
                CarryRule rule = ruleSet.rules().get(index);
                if (rule.inventorySlot() == inventorySlot && rule.matches(stack, tooltipContext)) {
                    return Optional.of(index);
                }
            }
        }
        for (int index = 0; index < ruleSet.rules().size(); index++) {
            CarryRule rule = ruleSet.rules().get(index);
            if (!rule.slotted() && rule.matches(stack, tooltipContext)) return Optional.of(index);
        }
        return Optional.empty();
    }

    private static int emptySlots(Inventory inventory) {
        int empty = 0;
        for (int slot = 0; slot < 36; slot++) if (inventory.getItem(slot).isEmpty()) empty++;
        return empty;
    }

    private static int mergeCapacity(Inventory inventory, ItemStack candidate) {
        int capacity = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stored = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stored, candidate)) {
                capacity += Math.max(0, stored.getMaxStackSize() - stored.getCount());
            }
        }
        return capacity;
    }

    private static CarryRuleSet rules(ServerPlayer player) {
        return player.getExistingData(ModAttachments.CARRY_RULES).orElse(CarryRuleSet.EMPTY);
    }
}
