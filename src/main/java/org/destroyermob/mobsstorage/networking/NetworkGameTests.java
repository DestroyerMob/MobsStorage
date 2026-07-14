package org.destroyermob.mobsstorage.networking;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.registry.ModAttachments;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.storage.StorageResolver;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.LabelDisplayMode;

@GameTestHolder(MobsStorage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);
    private static final BlockPos THIRD = new BlockPos(5, 1, 1);

    private NetworkGameTests() {
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkWandRecipeIsDiagonal(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(MobsStorage.id("network_wand")).orElseThrow();
        helper.assertTrue(holder.value() instanceof CraftingRecipe, "Network Wand recipe is not a crafting recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.REDSTONE),
                ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY,
                new ItemStack(Items.STICK), ItemStack.EMPTY, ItemStack.EMPTY));
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.NETWORK_WAND.get()), "Diagonal recipe did not produce a Network Wand");
        helper.assertTrue(!result.isDamageableItem(), "Network Wand unexpectedly has durability");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkRoutesAndRefills(GameTestHelper helper) {
        helper.setBlock(FIRST, Blocks.CHEST);
        helper.setBlock(SECOND, Blocks.CHEST);
        helper.setBlock(THIRD, Blocks.CHEST);
        ChestBlockEntity first = helper.getBlockEntity(FIRST);
        ChestBlockEntity second = helper.getBlockEntity(SECOND);
        ChestBlockEntity third = helper.getBlockEntity(THIRD);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        BlockPos playerPos = helper.absolutePos(FIRST).above();
        player.teleportTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        StorageNetworkSavedData data = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = data.create(player.getUUID(), "Test Network");
        GlobalPos firstPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        GlobalPos secondPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SECOND));
        GlobalPos thirdPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(THIRD));
        network.addNode(firstPos);
        network.addNode(secondPos);
        network.addNode(thirdPos);
        network.updateNode(firstPos, "Input", 0, org.destroyermob.mobsstorage.storage.LabelData.AIR);
        network.updateNode(secondPos, "Supplies", 10, org.destroyermob.mobsstorage.storage.LabelData.AIR);
        network.updateNode(thirdPos, "Overflow", 20, org.destroyermob.mobsstorage.storage.LabelData.AIR);
        network.setSource(firstPos);
        data.changed();
        first.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Input", 0, helper.absolutePos(FIRST)));
        second.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Supplies", 10, helper.absolutePos(SECOND)));
        third.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Overflow", 20, helper.absolutePos(THIRD)));

        ItemStack offered = new ItemStack(Items.COBBLESTONE, 32);
        NetworkInventoryService.InsertResult inserted = NetworkInventoryService.insert(player, first, offered);
        helper.assertTrue(inserted.inserted() == 32, "Network did not accept routed building blocks");
        helper.assertTrue(first.getItem(0).is(Items.COBBLESTONE), "Opened storage was not preferred first");

        StorageResolver.setLabel(helper.getLevel(), List.of(first), label(helper.absolutePos(FIRST), "#c:ingots"));
        StorageResolver.setLabel(helper.getLevel(), List.of(second), label(helper.absolutePos(SECOND), "#c:gems"));
        NetworkInventoryService.InsertResult diamond = NetworkInventoryService.insert(
                player, first, new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(diamond.inserted() == 4, "Filtered network route rejected diamonds");
        helper.assertTrue(second.getItem(0).is(Items.DIAMOND), "Matching filtered storage was not preferred over normal storage");

        first.setItem(0, ItemStack.EMPTY);
        third.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        ItemStack nearlyBroken = new ItemStack(Items.GOLDEN_PICKAXE);
        nearlyBroken.setDamageValue(nearlyBroken.getMaxDamage() - 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, nearlyBroken);
        ItemStack differentlyDamagedReplacement = new ItemStack(Items.GOLDEN_PICKAXE);
        differentlyDamagedReplacement.setDamageValue(7);
        differentlyDamagedReplacement.set(DataComponents.CUSTOM_NAME, Component.literal("Network spare"));
        third.setItem(0, differentlyDamagedReplacement);
        nearlyBroken.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        helper.assertTrue(nearlyBroken.isEmpty(), "Test pickaxe did not break");
        NetworkRefillService.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        helper.assertTrue(player.getMainHandItem().is(Items.GOLDEN_PICKAXE),
                "Broken tool was not restored by registered item ID");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 7,
                "Refill did not pull the differently damaged stored tool");
        helper.assertTrue(Component.literal("Network spare").equals(
                        player.getMainHandItem().get(DataComponents.CUSTOM_NAME)),
                "Refill did not preserve the replacement tool's components");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack namedBread = new ItemStack(Items.BREAD, 16);
        namedBread.set(DataComponents.CUSTOM_NAME, Component.literal("Network lunch"));
        third.setItem(1, namedBread);
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(
                player, new ItemStack(Items.BREAD), 0, ItemStack.EMPTY));
        NetworkRefillService.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        helper.assertTrue(player.getMainHandItem().is(Items.BREAD)
                        && player.getMainHandItem().getCount() == 16,
                "Consumed stack with different components was not restored by registered item ID");
        helper.succeed();
    }

    private static LabelData label(BlockPos anchor, String filter) {
        return new LabelData(net.minecraft.resources.ResourceLocation.withDefaultNamespace("diamond"),
                List.of(filter), Direction.NORTH, LabelDisplayMode.SURFACE, false, anchor);
    }
}
