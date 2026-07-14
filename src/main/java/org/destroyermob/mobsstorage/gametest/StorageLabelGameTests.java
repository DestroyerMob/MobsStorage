package org.destroyermob.mobsstorage.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.CompoundContainer;
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
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.storage.FilterRules;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.StorageLabelService;
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
        return new LabelData(ResourceLocation.withDefaultNamespace("iron_ingot"), filters, Direction.NORTH, false, anchor);
    }
}
