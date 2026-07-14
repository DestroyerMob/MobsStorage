package org.destroyermob.mobsstorage.networking;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.item.NetworkWandMode;
import org.destroyermob.mobsstorage.menu.NetworkTerminalMenu;
import org.destroyermob.mobsstorage.registry.ModAttachments;
import org.destroyermob.mobsstorage.registry.ModBlocks;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.storage.StorageResolver;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.LabelDisplayMode;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlockEntity;

@GameTestHolder(MobsStorage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);
    private static final BlockPos THIRD = new BlockPos(5, 1, 1);
    private static final BlockPos HOPPER = FIRST.south();

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
    public static void networkRoutesAndRefillsWithoutOrigin(GameTestHelper helper) {
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

        StorageResolver.setLabel(helper.getLevel(), List.of(second), label(helper.absolutePos(SECOND), "#c:gems"));
        NetworkInventoryService.InsertResult diamond = NetworkInventoryService.insert(
                player, first, new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(diamond.inserted() == 4, "Filtered network route rejected diamonds");
        helper.assertTrue(second.getItem(0).is(Items.DIAMOND),
                "Matching filtered storage was not preferred over the opened unfiltered storage");

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

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkInterfaceRecipeMatchesDesign(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(MobsStorage.id("network_interface")).orElseThrow();
        helper.assertTrue(holder.value() instanceof CraftingRecipe, "Network Interface recipe is not a crafting recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.CRYING_OBSIDIAN),
                new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.ENDER_PEARL),
                new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.CRYING_OBSIDIAN)));
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.NETWORK_INTERFACE.get()),
                "Four pearls, four crying obsidian, and a crafting table did not produce a Network Interface");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void hopperAutosortsThroughFilteredInput(GameTestHelper helper) {
        helper.setBlock(FIRST, Blocks.CHEST);
        helper.setBlock(SECOND, Blocks.CHEST);
        BlockState hopperState = Blocks.HOPPER.defaultBlockState()
                .setValue(HopperBlock.FACING, Direction.NORTH)
                .setValue(HopperBlock.ENABLED, true);
        helper.setBlock(HOPPER, hopperState);
        ChestBlockEntity input = helper.getBlockEntity(FIRST);
        ChestBlockEntity ingots = helper.getBlockEntity(SECOND);
        HopperBlockEntity hopper = helper.getBlockEntity(HOPPER);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageNetworkSavedData data = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = data.create(player.getUUID(), "Automation Network");
        GlobalPos inputPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        GlobalPos ingotsPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SECOND));
        network.addNode(inputPos);
        network.addNode(ingotsPos);
        network.updateNode(inputPos, "Raw Materials", 0, LabelData.AIR);
        network.updateNode(ingotsPos, "Ingots", 0, LabelData.AIR);
        data.changed();
        input.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Raw Materials", 0, helper.absolutePos(FIRST)));
        ingots.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Ingots", 0, helper.absolutePos(SECOND)));
        StorageResolver.setLabel(helper.getLevel(), List.of(input),
                label(helper.absolutePos(FIRST), "#c:raw_materials"));
        StorageResolver.setLabel(helper.getLevel(), List.of(ingots),
                label(helper.absolutePos(SECOND), "#c:ingots"));

        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            input.setItem(slot, new ItemStack(Items.RAW_IRON, 64));
        }
        hopper.setItem(0, new ItemStack(Items.IRON_INGOT));
        HopperBlockEntity.pushItemsTick(
                helper.getLevel(), helper.absolutePos(HOPPER), hopperState, hopper);

        helper.assertTrue(hopper.getItem(0).isEmpty(), "Hopper did not send the ingot into the network");
        helper.assertTrue(ingots.getItem(0).is(Items.IRON_INGOT),
                "Hopper-routed ingot did not reach the matching filtered storage");
        helper.assertTrue(input.getItem(0).is(Items.RAW_IRON),
                "Automation inserted the ingot into the rejecting input storage");

        ItemStack directRemainder = HopperBlockEntity.addItem(
                hopper, input, new ItemStack(Items.GOLD_INGOT), Direction.SOUTH);
        helper.assertTrue(directRemainder.isEmpty(),
                "Direct vanilla machine transfer did not enter the storage network");
        helper.assertTrue(ingots.getItem(1).is(Items.GOLD_INGOT),
                "Direct vanilla machine transfer did not reach the matching filtered storage");

        hopper.setCooldown(0);
        hopper.setItem(0, new ItemStack(Items.STICK));
        HopperBlockEntity.pushItemsTick(
                helper.getLevel(), helper.absolutePos(HOPPER), hopperState, hopper);
        helper.assertTrue(hopper.getItem(0).is(Items.STICK),
                "Automation consumed an item that no network storage accepts");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkInterfaceIsBidirectionalOrigin(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.NETWORK_INTERFACE.get());
        helper.setBlock(SECOND, Blocks.CHEST);
        helper.setBlock(THIRD, Blocks.CHEST);
        NetworkInterfaceBlockEntity terminal = helper.getBlockEntity(FIRST);
        ChestBlockEntity ingots = helper.getBlockEntity(SECOND);
        ChestBlockEntity rawMaterials = helper.getBlockEntity(THIRD);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerPos = helper.absolutePos(FIRST).above();
        player.teleportTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        StorageNetworkSavedData data = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = data.create(player.getUUID(), "Interface Network");
        GlobalPos terminalPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        GlobalPos ingotsPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SECOND));
        GlobalPos rawPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(THIRD));
        network.addNode(terminalPos);
        network.addNode(ingotsPos);
        network.addNode(rawPos);
        network.updateNode(terminalPos, "Network Interface", 0, MobsStorage.id("network_interface"));
        network.updateNode(ingotsPos, "Ingots", 0, LabelData.AIR);
        network.updateNode(rawPos, "Raw Materials", 0, LabelData.AIR);
        network.setOrigin(terminalPos);
        data.changed();

        terminal.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Network Interface", 0, helper.absolutePos(FIRST)));
        ingots.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Ingots", 0, helper.absolutePos(SECOND)));
        rawMaterials.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Raw Materials", 0, helper.absolutePos(THIRD)));
        StorageResolver.setLabel(helper.getLevel(), List.of(ingots),
                label(helper.absolutePos(SECOND), "#c:ingots"));
        StorageResolver.setLabel(helper.getLevel(), List.of(rawMaterials),
                label(helper.absolutePos(THIRD), "#c:raw_materials"));
        rawMaterials.setItem(0, new ItemStack(Items.RAW_IRON, 5));

        IItemHandler handler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(FIRST), Direction.UP);
        helper.assertTrue(handler != null, "Network Interface did not expose its registered item capability");
        ItemStack remainder = handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 3), false);
        helper.assertTrue(remainder.isEmpty(), "Network Interface did not accept an automated input");
        helper.assertTrue(ingots.getItem(0).is(Items.IRON_INGOT) && ingots.getItem(0).getCount() == 3,
                "Network Interface input did not route to the matching whitelist");

        int rawSlot = -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(Items.RAW_IRON)) {
                rawSlot = slot;
                break;
            }
        }
        helper.assertTrue(rawSlot >= 0, "Network Interface did not expose combined network contents");
        ItemStack extracted = handler.extractItem(rawSlot, 2, false);
        helper.assertTrue(extracted.is(Items.RAW_IRON) && extracted.getCount() == 2,
                "Network Interface did not provide automated output");
        helper.assertTrue(rawMaterials.getItem(0).getCount() == 3,
                "Network Interface output did not remove items from network storage");
        helper.assertTrue(NetworkService.canUseTerminal(player, terminal),
                "Network Interface origin did not authorize its crafting terminal");

        NetworkTerminalMenu menu = new NetworkTerminalMenu(7, player.getInventory(), terminal);
        int visibleRawSlot = -1;
        for (int slot = NetworkTerminalMenu.NETWORK_START; slot < NetworkTerminalMenu.NETWORK_END; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.RAW_IRON)) {
                visibleRawSlot = slot;
                break;
            }
        }
        helper.assertTrue(visibleRawSlot >= 0 && menu.getSlot(visibleRawSlot).getItem().getCount() == 3,
                "Crafting terminal did not display the combined network inventory");

        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 2));
        menu.clicked(NetworkTerminalMenu.NETWORK_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && ingots.getItem(1).is(Items.GOLD_INGOT),
                "Crafting terminal did not deposit carried items through network routing");

        visibleRawSlot = -1;
        for (int slot = NetworkTerminalMenu.NETWORK_START; slot < NetworkTerminalMenu.NETWORK_END; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.RAW_IRON)) {
                visibleRawSlot = slot;
                break;
            }
        }
        helper.assertTrue(visibleRawSlot >= 0, "Raw iron disappeared after the terminal inventory refreshed");
        menu.quickMoveStack(player, visibleRawSlot);
        helper.assertTrue(player.getInventory().countItem(Items.RAW_IRON) == 3,
                "Shift-clicking network storage did not move the stack into the player inventory");
        helper.assertTrue(menu.getSlot(NetworkTerminalMenu.CRAFT_START).getItem().isEmpty(),
                "Shift-clicking network storage incorrectly filled the crafting grid");
        helper.assertTrue(rawMaterials.getItem(0).isEmpty(),
                "Shift-clicked items were not removed from network storage");
        menu.removed(player);

        ingots.clearContent();
        rawMaterials.clearContent();
        List<ItemStack> distinctItems = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .limit(46)
                .map(ItemStack::new)
                .toList();
        for (int index = 0; index < distinctItems.size(); index++) {
            if (index < ingots.getContainerSize()) ingots.setItem(index, distinctItems.get(index));
            else rawMaterials.setItem(index - ingots.getContainerSize(), distinctItems.get(index));
        }
        NetworkTerminalMenu scrollMenu = new NetworkTerminalMenu(8, player.getInventory(), terminal);
        ItemStack firstVisible = scrollMenu.getSlot(NetworkTerminalMenu.NETWORK_START).getItem().copy();
        helper.assertTrue(scrollMenu.maxScrollRows() == 1,
                "Forty-six item types did not create one additional scroll row");
        helper.assertTrue(scrollMenu.clickMenuButton(
                        player, NetworkTerminalMenu.SCROLL_TO_BUTTON_BASE + 1),
                "Terminal rejected a direct scrollbar row request");
        helper.assertTrue(scrollMenu.scrollRow() == 1,
                "Terminal did not move its aggregate inventory by one row");
        helper.assertTrue(!ItemStack.isSameItemSameComponents(firstVisible,
                        scrollMenu.getSlot(NetworkTerminalMenu.NETWORK_START).getItem()),
                "Scrolled terminal inventory did not replace the first visible row");
        scrollMenu.removed(player);
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkKeepsOneOrigin(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageNetwork network = StorageNetworkSavedData.get(helper.getLevel().getServer())
                .create(player.getUUID(), "Origin Test");
        GlobalPos first = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        GlobalPos second = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SECOND));
        network.addNode(first);
        network.addNode(second);
        network.setOrigin(first);
        helper.assertTrue(network.origin().filter(first::equals).isPresent(), "First origin was not stored");
        network.setOrigin(second);
        helper.assertTrue(network.origin().filter(second::equals).isPresent(), "Second origin did not replace the first");
        network.removeNode(second);
        helper.assertTrue(network.origin().isEmpty(), "Removing the origin node did not clear the origin");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkWandCyclesExplicitModes(GameTestHelper helper) {
        ItemStack wand = new ItemStack(ModItems.NETWORK_WAND.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(NetworkService.wandMode(wand) == NetworkWandMode.ADD_STORAGE,
                "New wand did not default to Add to Network mode");
        NetworkService.cycleWandMode(player, wand);
        helper.assertTrue(NetworkService.wandMode(wand) == NetworkWandMode.SET_ORIGIN,
                "Wand did not cycle to Set Network Origin mode");
        NetworkService.cycleWandMode(player, wand);
        helper.assertTrue(NetworkService.wandMode(wand) == NetworkWandMode.CONFIGURE_STORAGE,
                "Wand did not cycle to Configure Storage mode");
        NetworkService.cycleWandMode(player, wand);
        helper.assertTrue(NetworkService.wandMode(wand) == NetworkWandMode.ADD_STORAGE,
                "Wand mode cycle did not wrap around");
        helper.succeed();
    }

    private static LabelData label(BlockPos anchor, String filter) {
        return new LabelData(net.minecraft.resources.ResourceLocation.withDefaultNamespace("diamond"),
                List.of(filter), Direction.NORTH, LabelDisplayMode.SURFACE, false, anchor);
    }
}
