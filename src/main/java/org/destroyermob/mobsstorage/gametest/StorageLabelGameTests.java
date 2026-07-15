package org.destroyermob.mobsstorage.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.inventory.CarryRule;
import org.destroyermob.mobsstorage.inventory.CarryRuleService;
import org.destroyermob.mobsstorage.inventory.CarryRuleSet;
import org.destroyermob.mobsstorage.network.SyncMenuFiltersPayload;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.storage.FilterRules;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.LabelDisplayMode;
import org.destroyermob.mobsstorage.storage.StorageLabelService;
import org.destroyermob.mobsstorage.storage.StorageMenuFilterSync;
import org.destroyermob.mobsstorage.storage.StorageResolver;

@GameTestHolder(MobsStorage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StorageLabelGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);

    private StorageLabelGameTests() {
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void recipeProducesFourLabels(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(MobsStorage.id("storage_label")).orElseThrow();
        helper.assertTrue(holder.value() instanceof CraftingRecipe, "Storage label recipe is not a crafting recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.NAME_TAG)));
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.STORAGE_LABEL.get()), "Recipe did not produce Storage Labels");
        helper.assertTrue(result.getCount() == 4, "Recipe did not produce four labels");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void exactTagsAndEmptyFiltersMatch(GameTestHelper helper) {
        helper.assertTrue(FilterRules.matches(new ItemStack(Items.IRON_INGOT), List.of("minecraft:iron_ingot")), "Exact item ID did not match");
        helper.assertTrue(FilterRules.matches(new ItemStack(Items.IRON_INGOT), List.of("#c:ingots", "#c:gems")), "Common ingot tag did not match");
        helper.assertTrue(FilterRules.matches(new ItemStack(Items.DIAMOND), List.of("#c:ingots", "#c:gems")), "Common gem tag did not match");
        helper.assertFalse(FilterRules.matches(new ItemStack(Items.STICK), List.of("#c:ingots", "#c:gems")), "Unlisted stick matched whitelist");
        helper.assertTrue(FilterRules.matches(new ItemStack(Items.STICK), List.of()), "Empty whitelist did not allow all items");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void searchExpressionsMatch(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        helper.assertTrue(FilterRules.matches(iron, List.of("@minecraft")), "Mod-id search did not match");
        helper.assertTrue(FilterRules.matches(iron, List.of("&minecraft:iron")), "Resource-id search did not match");
        helper.assertTrue(FilterRules.matches(iron, List.of("#ingots")), "Tag-name search did not match");
        helper.assertTrue(FilterRules.matches(iron, List.of("@minecraft ingot")), "AND search did not match");
        helper.assertFalse(FilterRules.matches(new ItemStack(Items.STICK), List.of("@minecraft ingot")),
                "AND search accepted a partial match");
        helper.assertTrue(FilterRules.matches(new ItemStack(ModItems.STORAGE_LABEL.get()),
                List.of("@minecraft | @mobsstorage")), "OR search did not match");
        helper.assertTrue(FilterRules.matches(new ItemStack(ModItems.STORAGE_LABEL.get()),
                List.of("-@minecraft @mobsstorage")), "Negated search did not match");
        ItemStack named = new ItemStack(Items.STICK);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Warehouse Token"));
        helper.assertTrue(FilterRules.matches(named, List.of("$warehouse")), "Tooltip search did not match");
        helper.assertTrue(FilterRules.validate(List.of("@minecraft", "#c:ingots", "iron")).isEmpty(),
                "Valid search expressions failed validation");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void carryRulesUseExactComponentsAndSafeDepositMaximums(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        ItemStack namedStick = new ItemStack(Items.STICK, 3);
        namedStick.set(DataComponents.CUSTOM_NAME, Component.literal("Survey marker"));
        ItemStack exactSample = namedStick.copyWithCount(1);
        CarryRule exact = new CarryRule("", exactSample, 1, 1, 1);
        CarryRule broad = new CarryRule("minecraft:stick", ItemStack.EMPTY, 8, 16, 16);
        CarryRuleSet rules = new CarryRuleSet(List.of(exact, broad), 0);
        player.getInventory().setItem(9, namedStick);
        player.getInventory().setItem(10, new ItemStack(Items.STICK, 20));

        helper.assertTrue(exact.matches(namedStick, Item.TooltipContext.of(helper.getLevel())),
                "Exact carry rule did not match the captured components");
        helper.assertFalse(exact.matches(new ItemStack(Items.STICK), Item.TooltipContext.of(helper.getLevel())),
                "Exact carry rule matched a plain stack with different components");
        helper.assertTrue(CarryRuleService.depositableCount(player, rules, namedStick) == 2,
                "Safe deposit did not retain the exact rule maximum");
        helper.assertTrue(CarryRuleService.depositableCount(player, rules, player.getInventory().getItem(10)) == 4,
                "Safe deposit did not classify later stacks by first-match priority");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void reservedSlotsAllowMergesButRejectNewPickupStacks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        for (int slot = 0; slot < 34; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 60));
        helper.assertTrue(CarryRuleService.blocksPickup(player.getInventory(), 2, new ItemStack(Items.DIAMOND)),
                "Reserved empty slots allowed a pickup that needed a new slot");
        helper.assertFalse(CarryRuleService.blocksPickup(
                        player.getInventory(), 2, new ItemStack(Items.COBBLESTONE, 4)),
                "Reserved empty slots blocked a pickup that fit an existing stack");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void applyingFilterEjectsOnlyDisallowedContents(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        chest.setItem(1, new ItemStack(Items.STICK, 2));
        LabelData data = label(helper.absolutePos(CHEST), List.of("#c:ingots"));
        StorageResolver.setLabel(helper.getLevel(), List.of(chest), data);
        helper.assertTrue(chest.canPlaceItem(0, new ItemStack(Items.IRON_INGOT)), "Container hook rejected an allowed item");
        helper.assertFalse(chest.canPlaceItem(0, new ItemStack(Items.STICK)), "Container hook accepted a disallowed item");
        StorageLabelService.ejectDisallowed(helper.getLevel(), List.of(chest), data, Vec3.atCenterOf(helper.absolutePos(CHEST)));
        helper.assertTrue(chest.getItem(0).is(Items.IRON_INGOT), "Allowed ingots were ejected");
        helper.assertTrue(chest.getItem(1).isEmpty(), "Disallowed sticks remained in the chest");
        helper.assertItemEntityCountIs(Items.STICK, CHEST, 1.5D, 2);
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void menuFilterSyncRejectsInvalidItemsBeforeServerCorrection(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        StorageResolver.setLabel(helper.getLevel(), List.of(chest),
                label(helper.absolutePos(CHEST), List.of("#c:ingots")));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ChestMenu menu = ChestMenu.threeRows(7, player.getInventory(), chest);

        SyncMenuFiltersPayload payload = StorageMenuFilterSync.createPayload(menu);
        Item.TooltipContext context = Item.TooltipContext.of(helper.getLevel());
        helper.assertTrue(payload.groups().size() == 1 && payload.groups().getFirst().slots().size() == 27,
                "Menu filter sync did not identify every labelled chest slot");
        helper.assertTrue(payload.allows(0, new ItemStack(Items.IRON_INGOT), context),
                "Synced menu filter rejected an allowed item");
        helper.assertFalse(payload.allows(0, new ItemStack(Items.STICK), context),
                "Synced menu filter accepted a disallowed item");
        helper.assertTrue(payload.allows(27, new ItemStack(Items.STICK), context),
                "Synced menu filter leaked into the player's inventory slots");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void oneLabelFiltersBothHalvesOfDoubleChest(GameTestHelper helper) {
        BlockPos left = CHEST;
        BlockPos right = CHEST.east();
        BlockState leftState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState rightState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);
        helper.setBlock(left, leftState);
        helper.setBlock(right, rightState);
        ChestBlockEntity leftChest = helper.getBlockEntity(left);
        ChestBlockEntity rightChest = helper.getBlockEntity(right);
        LabelData data = label(helper.absolutePos(left), List.of("minecraft:iron_ingot"));
        StorageResolver.setLabel(helper.getLevel(), List.of(leftChest, rightChest), data);
        CompoundContainer compound = new CompoundContainer(leftChest, rightChest);
        helper.assertTrue(StorageResolver.allows(compound, new ItemStack(Items.IRON_INGOT)), "Allowed item was rejected by double chest");
        helper.assertFalse(StorageResolver.allows(compound, new ItemStack(Items.STICK)), "Disallowed item passed double-chest filter");
        helper.assertTrue(StorageResolver.logicalStorage(helper.getLevel(), helper.absolutePos(left)).size() == 2, "Double chest did not resolve both halves");
        StorageResolver.clearLabel(helper.getLevel(), List.of(leftChest, rightChest));
        helper.assertTrue(StorageResolver.existingLabel(leftChest).isEmpty() && StorageResolver.existingLabel(rightChest).isEmpty(), "Removing label did not clear both halves");
        helper.succeed();
    }

    private static LabelData label(BlockPos anchor, List<String> filters) {
        return new LabelData(ResourceLocation.withDefaultNamespace("iron_ingot"), filters, Direction.NORTH,
                LabelDisplayMode.SURFACE, false, anchor);
    }
}
