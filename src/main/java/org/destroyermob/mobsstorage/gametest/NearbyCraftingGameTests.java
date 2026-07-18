package org.destroyermob.mobsstorage.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.crafting.NearbyCraftingMenuAccess;

@GameTestHolder(MobsStorage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NearbyCraftingGameTests {
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);
    private static final BlockPos STORAGE = new BlockPos(3, 1, 1);

    private NearbyCraftingGameTests() {
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void vanillaRecipeBookPullsFromNearbyStorage(GameTestHelper helper) {
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        helper.setBlock(STORAGE, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(STORAGE);
        chest.setItem(0, new ItemStack(Items.GLASS, 5));
        chest.setItem(1, new ItemStack(Items.NETHER_STAR));
        chest.setItem(2, new ItemStack(Items.OBSIDIAN, 3));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
        player.containerMenu = menu;
        RecipeHolder<?> beacon = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("beacon")).orElseThrow();
        player.awardRecipes(List.of(beacon));

        menu.handlePlacement(false, beacon, player);

        helper.assertTrue(menu.getSlot(0).getItem().is(Items.BEACON),
                "The vanilla recipe book did not fill a beacon from nearby storage");
        helper.assertTrue(count(chest, Items.GLASS) == 0
                        && count(chest, Items.NETHER_STAR) == 0
                        && count(chest, Items.OBSIDIAN) == 0,
                "Recipe-book placement did not remove its physical chest ingredients");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void recipeTransferSourcesMutatePhysicalStorage(GameTestHelper helper) {
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        helper.setBlock(STORAGE, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(STORAGE);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 2));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CraftingMenu menu = new CraftingMenu(2, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
        Slot source = ((NearbyCraftingMenuAccess) menu).mobsstorage$getNearbyCraftingSlots().stream()
                .filter(slot -> slot.getItem().is(Items.DIAMOND))
                .findFirst().orElseThrow();

        ItemStack transferred = source.safeTake(1, Integer.MAX_VALUE, player);

        helper.assertTrue(transferred.is(Items.DIAMOND) && transferred.getCount() == 1,
                "A recipe-transfer source could not take the requested ingredient");
        helper.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 1,
                "A recipe-transfer source did not mutate its physical chest slot");
        helper.succeed();
    }

    private static int count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount();
        }
        return count;
    }
}
