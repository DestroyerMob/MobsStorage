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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
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
import org.destroyermob.mobsstorage.world.NetworkPortBlockEntity;

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
    public static void singleDoubleSingleChestTopologyDoesNotKeepStaleAnchors(GameTestHelper helper) {
        BlockPos first = FIRST;
        BlockPos second = FIRST.east();
        BlockState left = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState right = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);
        helper.setBlock(first, left);
        helper.setBlock(second, right);
        ChestBlockEntity firstChest = helper.getBlockEntity(first);
        ChestBlockEntity secondChest = helper.getBlockEntity(second);
        BlockPos anchor = helper.absolutePos(first);

        LabelData label = label(anchor, "#c:ingots");
        StorageResolver.setLabel(helper.getLevel(), List.of(firstChest, secondChest), label);
        StorageNetworkSavedData savedData = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = savedData.create(helper.makeMockServerPlayerInLevel().getUUID(), "Topology Test");
        GlobalPos networkAnchor = GlobalPos.of(helper.getLevel().dimension(), anchor);
        network.addNode(networkAnchor);
        network.setOrigin(networkAnchor);
        NetworkNodeData node = new NetworkNodeData(network.id(), "Ingots", 4, anchor);
        firstChest.setData(ModAttachments.NETWORK_NODE, node);
        secondChest.setData(ModAttachments.NETWORK_NODE, node);

        helper.setBlock(second, Blocks.AIR);
        helper.setBlock(first, Blocks.CHEST.defaultBlockState());
        StorageResolver.reconcileLabelTopology(helper.getLevel(), anchor);
        NetworkService.reconcileTopology(helper.getLevel(), anchor);
        firstChest = helper.getBlockEntity(first);
        helper.assertTrue(StorageResolver.existingLabel(firstChest).isPresent(),
                "The surviving anchor chest lost its label after returning to single");
        helper.assertTrue(NetworkService.existingNode(firstChest).isPresent(),
                "The surviving anchor chest lost its network after returning to single");

        helper.setBlock(first, left);
        helper.setBlock(second, right);
        firstChest = helper.getBlockEntity(first);
        secondChest = helper.getBlockEntity(second);
        StorageResolver.setLabel(helper.getLevel(), List.of(firstChest, secondChest), label);
        NetworkService.onStorageJoined(helper.getLevel(), anchor);
        helper.setBlock(first, Blocks.AIR);
        helper.setBlock(second, Blocks.CHEST.defaultBlockState());
        BlockPos survivor = helper.absolutePos(second);
        StorageResolver.reconcileLabelTopology(helper.getLevel(), survivor);
        NetworkService.reconcileTopology(helper.getLevel(), survivor);
        secondChest = helper.getBlockEntity(second);

        helper.assertTrue(secondChest.getExistingData(ModAttachments.STORAGE_LABEL).isEmpty(),
                "The mirrored label retained a missing double-chest anchor");
        helper.assertTrue(secondChest.getExistingData(ModAttachments.NETWORK_NODE).isEmpty(),
                "The mirrored network node retained a missing double-chest anchor");
        helper.assertFalse(network.nodes().contains(networkAnchor),
                "The network retained the missing double-chest anchor");
        helper.assertTrue(network.origin().isEmpty(),
                "The network source retained the missing double-chest anchor");
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

        CraftingRecipe inputRecipe = (CraftingRecipe) helper.getLevel().getRecipeManager()
                .byKey(MobsStorage.id("network_input")).orElseThrow().value();
        ItemStack inputResult = inputRecipe.assemble(CraftingInput.of(2, 2, List.of(
                new ItemStack(ModItems.NETWORK_INTERFACE.get()), new ItemStack(Items.HOPPER),
                new ItemStack(Items.BLUE_DYE), ItemStack.EMPTY)), helper.getLevel().registryAccess());
        helper.assertTrue(inputResult.is(ModItems.NETWORK_INPUT.get()), "Input recipe did not produce a Network Input");

        CraftingRecipe outputRecipe = (CraftingRecipe) helper.getLevel().getRecipeManager()
                .byKey(MobsStorage.id("network_output")).orElseThrow().value();
        ItemStack output = outputRecipe.assemble(CraftingInput.of(2, 2, List.of(
                new ItemStack(ModItems.NETWORK_INTERFACE.get()), new ItemStack(Items.HOPPER),
                new ItemStack(Items.ORANGE_DYE), ItemStack.EMPTY)), helper.getLevel().registryAccess());
        helper.assertTrue(output.is(ModItems.NETWORK_OUTPUT.get()), "Output recipe did not produce a Network Output");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void hopperAutosortsThroughFilteredInput(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.NETWORK_INPUT.get());
        helper.setBlock(SECOND, Blocks.CHEST);
        helper.setBlock(THIRD, Blocks.CHEST);
        BlockState hopperState = Blocks.HOPPER.defaultBlockState()
                .setValue(HopperBlock.FACING, Direction.NORTH)
                .setValue(HopperBlock.ENABLED, true);
        helper.setBlock(HOPPER, hopperState);
        NetworkPortBlockEntity input = helper.getBlockEntity(FIRST);
        ChestBlockEntity rawMaterials = helper.getBlockEntity(SECOND);
        ChestBlockEntity ingots = helper.getBlockEntity(THIRD);
        HopperBlockEntity hopper = helper.getBlockEntity(HOPPER);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageNetworkSavedData data = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = data.create(player.getUUID(), "Automation Network");
        GlobalPos inputPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        GlobalPos rawPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SECOND));
        GlobalPos ingotsPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(THIRD));
        network.addNode(inputPos);
        network.addNode(rawPos);
        network.addNode(ingotsPos);
        network.updateNode(inputPos, "Machine Input", 0, MobsStorage.id("network_input"));
        network.updateNode(rawPos, "Raw Materials", 0, LabelData.AIR);
        network.updateNode(ingotsPos, "Ingots", 0, LabelData.AIR);
        data.changed();
        input.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Machine Input", 0, helper.absolutePos(FIRST)));
        rawMaterials.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Raw Materials", 0, helper.absolutePos(SECOND)));
        ingots.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Ingots", 0, helper.absolutePos(THIRD)));
        StorageResolver.setLabel(helper.getLevel(), List.of(rawMaterials),
                label(helper.absolutePos(SECOND), "#c:raw_materials"));
        StorageResolver.setLabel(helper.getLevel(), List.of(ingots),
                label(helper.absolutePos(THIRD), "#c:ingots"));

        for (int slot = 0; slot < rawMaterials.getContainerSize(); slot++) {
            rawMaterials.setItem(slot, new ItemStack(Items.RAW_IRON, 64));
        }
        hopper.setItem(0, new ItemStack(Items.IRON_INGOT));
        HopperBlockEntity.pushItemsTick(
                helper.getLevel(), helper.absolutePos(HOPPER), hopperState, hopper);

        helper.assertTrue(hopper.getItem(0).isEmpty(), "Hopper did not send the ingot into the network");
        helper.assertTrue(ingots.getItem(0).is(Items.IRON_INGOT),
                "Hopper-routed ingot did not reach the matching filtered storage");
        helper.assertTrue(rawMaterials.getItem(0).is(Items.RAW_IRON),
                "Automation inserted the ingot into the rejecting input storage");

        ItemStack directRemainder = HopperBlockEntity.addItem(
                hopper, ingots, new ItemStack(Items.IRON_INGOT), Direction.SOUTH);
        helper.assertTrue(directRemainder.is(Items.IRON_INGOT),
                "A linked chest still acted as a network-wide machine input");
        helper.assertTrue(ingots.getItem(0).getCount() == 1,
                "Machine input directly modified linked chest storage");

        IItemHandler chestHandler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(THIRD), Direction.UP);
        helper.assertTrue(chestHandler != null && chestHandler.getSlots() == 0,
                "Linked chest still exposed machine item access");

        IItemHandler inputHandler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(FIRST), Direction.UP);
        helper.assertTrue(inputHandler != null && inputHandler.getSlots() == 1,
                "Network Input did not expose its one-way machine capability");
        ItemStack capabilityRemainder = inputHandler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 2), false);
        helper.assertTrue(capabilityRemainder.isEmpty() && ingots.getItem(1).getCount() == 2,
                "Network Input capability did not sort items into filtered storage");
        helper.assertTrue(inputHandler.extractItem(0, 1, false).isEmpty(),
                "Network Input unexpectedly allowed machine extraction");

        hopper.setCooldown(0);
        hopper.setItem(0, new ItemStack(Items.STICK));
        HopperBlockEntity.pushItemsTick(
                helper.getLevel(), helper.absolutePos(HOPPER), hopperState, hopper);
        helper.assertTrue(hopper.getItem(0).is(Items.STICK),
                "Automation consumed an item that no network storage accepts");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkOutputOnlyExtractsFromNetwork(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.NETWORK_OUTPUT.get());
        helper.setBlock(SECOND, Blocks.CHEST);
        NetworkPortBlockEntity output = helper.getBlockEntity(FIRST);
        ChestBlockEntity storage = helper.getBlockEntity(SECOND);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageNetwork network = StorageNetworkSavedData.get(helper.getLevel().getServer())
                .create(player.getUUID(), "Output Network");
        GlobalPos outputPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        GlobalPos storagePos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SECOND));
        network.addNode(outputPos);
        network.addNode(storagePos);
        network.updateNode(outputPos, "Machine Output", 0, MobsStorage.id("network_output"));
        network.updateNode(storagePos, "Storage", 0, LabelData.AIR);
        output.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Machine Output", 0, helper.absolutePos(FIRST)));
        storage.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Storage", 0, helper.absolutePos(SECOND)));
        storage.setItem(0, new ItemStack(Items.REDSTONE, 5));

        IItemHandler handler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(FIRST), Direction.DOWN);
        helper.assertTrue(handler != null, "Network Output did not expose its machine capability");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND), false).is(Items.DIAMOND),
                "Network Output unexpectedly allowed machine insertion");
        int redstoneSlot = -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(Items.REDSTONE)) {
                redstoneSlot = slot;
                break;
            }
        }
        helper.assertTrue(redstoneSlot >= 0, "Network Output did not expose stored items");
        ItemStack extracted = handler.extractItem(redstoneSlot, 2, false);
        helper.assertTrue(extracted.is(Items.REDSTONE) && extracted.getCount() == 2,
                "Network Output did not extract from network storage");
        helper.assertTrue(storage.getItem(0).getCount() == 3,
                "Network Output extraction did not remove the stored items");

        BlockPos hopperBelow = FIRST.below();
        BlockState hopperState = Blocks.HOPPER.defaultBlockState()
                .setValue(HopperBlock.FACING, Direction.DOWN)
                .setValue(HopperBlock.ENABLED, true);
        helper.setBlock(hopperBelow, hopperState);
        HopperBlockEntity hopper = helper.getBlockEntity(hopperBelow);
        helper.assertTrue(HopperBlockEntity.suckInItems(helper.getLevel(), hopper),
                "Vanilla hopper did not pull through the Network Output");
        helper.assertTrue(hopper.getItem(0).is(Items.REDSTONE) && storage.getItem(0).getCount() == 2,
                "Hopper output did not transfer exactly one network item");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkInterfacesUseSourceRangeAndCraftWithoutDuplication(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.NETWORK_INTERFACE.get());
        helper.setBlock(SECOND, Blocks.CHEST);
        helper.setBlock(THIRD, Blocks.CHEST);
        helper.setBlock(HOPPER, ModBlocks.NETWORK_INTERFACE.get());
        NetworkInterfaceBlockEntity terminal = helper.getBlockEntity(FIRST);
        NetworkInterfaceBlockEntity secondTerminal = helper.getBlockEntity(HOPPER);
        ChestBlockEntity ingots = helper.getBlockEntity(SECOND);
        ChestBlockEntity rawMaterials = helper.getBlockEntity(THIRD);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerPos = helper.absolutePos(FIRST).above();
        player.teleportTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        StorageNetworkSavedData data = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = data.create(player.getUUID(), "Interface Network");
        GlobalPos terminalPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        GlobalPos secondTerminalPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(HOPPER));
        GlobalPos ingotsPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SECOND));
        GlobalPos rawPos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(THIRD));
        network.addNode(terminalPos);
        network.addNode(secondTerminalPos);
        network.addNode(ingotsPos);
        network.addNode(rawPos);
        network.updateNode(terminalPos, "Network Interface", 0, MobsStorage.id("network_interface"));
        network.updateNode(secondTerminalPos, "Second Interface", 0, MobsStorage.id("network_interface"));
        network.updateNode(ingotsPos, "Ingots", 0, LabelData.AIR);
        network.updateNode(rawPos, "Raw Materials", 0, LabelData.AIR);
        network.setOrigin(ingotsPos);
        data.changed();

        terminal.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Network Interface", 0, helper.absolutePos(FIRST)));
        secondTerminal.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Second Interface", 0, helper.absolutePos(HOPPER)));
        ingots.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Ingots", 0, helper.absolutePos(SECOND)));
        rawMaterials.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Raw Materials", 0, helper.absolutePos(THIRD)));
        StorageResolver.setLabel(helper.getLevel(), List.of(ingots),
                label(helper.absolutePos(SECOND), "#c:ingots"));
        StorageResolver.setLabel(helper.getLevel(), List.of(rawMaterials),
                label(helper.absolutePos(THIRD), "#c:raw_materials"));
        rawMaterials.setItem(0, new ItemStack(Items.RAW_IRON, 5));

        IItemHandler interfaceHandler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(FIRST), Direction.UP);
        helper.assertTrue(interfaceHandler == null,
                "Network Interface still exposed network-wide machine automation");
        helper.assertTrue(NetworkService.canUseTerminal(player, terminal),
                "Interface within source range could not open its crafting terminal");
        helper.assertTrue(NetworkService.canUseTerminal(player, secondTerminal),
                "A second linked interface within source range was not functional");
        helper.assertFalse(NetworkService.withinOriginRange(network, GlobalPos.of(
                        helper.getLevel().dimension(), ingotsPos.pos().offset(NetworkRefillService.RANGE + 1, 0, 0))),
                "Interface source range extended beyond 256 blocks");

        NetworkTerminalMenu menu = new NetworkTerminalMenu(7, player.getInventory(), terminal);
        int visibleRawSlot = -1;
        for (int slot = NetworkTerminalMenu.NETWORK_START; slot < NetworkTerminalMenu.NETWORK_END; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.RAW_IRON)) {
                visibleRawSlot = slot;
                break;
            }
        }
        helper.assertTrue(visibleRawSlot >= 0 && menu.getSlot(visibleRawSlot).getItem().getCount() == 5,
                "Crafting terminal did not display the combined network inventory");

        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 2));
        menu.clicked(NetworkTerminalMenu.NETWORK_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && count(Items.GOLD_INGOT, ingots) == 2,
                "Crafting terminal did not deposit carried items through network routing");

        visibleRawSlot = -1;
        for (int slot = NetworkTerminalMenu.NETWORK_START; slot < NetworkTerminalMenu.NETWORK_END; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.RAW_IRON)) {
                visibleRawSlot = slot;
                break;
            }
        }
        helper.assertTrue(visibleRawSlot >= 0, "Raw iron disappeared after the terminal inventory refreshed");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(visibleRawSlot, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.RAW_IRON) && menu.getCarried().getCount() == 5,
                "Terminal did not explicitly extract the displayed aggregate stack");
        menu.clicked(NetworkTerminalMenu.CRAFT_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getSlot(NetworkTerminalMenu.CRAFT_START).getItem().getCount() == 5,
                "Extracted network item did not enter the crafting grid");
        menu.removed(player);
        helper.assertTrue(count(Items.RAW_IRON, ingots, rawMaterials) == 5,
                "Closing the terminal duplicated items pulled into the crafting grid");

        ingots.clearContent();
        rawMaterials.clearContent();
        StorageResolver.clearLabel(helper.getLevel(), List.of(ingots));
        StorageResolver.clearLabel(helper.getLevel(), List.of(rawMaterials));
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
        ItemStack offPage = scrollMenu.getSlot(NetworkTerminalMenu.INGREDIENT_INDEX_START).getItem().copy();
        helper.assertTrue(scrollMenu.maxScrollRows() == 1,
                "Forty-six item types did not create one additional scroll row");
        helper.assertTrue(!offPage.isEmpty(),
                "Off-page network contents were not exposed to crafting recipe transfer");
        scrollMenu.setCarried(ItemStack.EMPTY);
        scrollMenu.clicked(NetworkTerminalMenu.INGREDIENT_INDEX_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(ItemStack.isSameItemSameComponents(offPage, scrollMenu.getCarried()),
                "Crafting transfer index could not pull an off-page network item");
        scrollMenu.clicked(NetworkTerminalMenu.CRAFT_START, 0, ClickType.PICKUP, player);
        scrollMenu.removed(player);
        helper.assertTrue(countAll(ingots, rawMaterials) == 46,
                "Off-page crafting transfer duplicated or lost a network item");

        NetworkTerminalMenu pageMenu = new NetworkTerminalMenu(9, player.getInventory(), terminal);
        helper.assertTrue(pageMenu.clickMenuButton(
                        player, NetworkTerminalMenu.SCROLL_TO_BUTTON_BASE + 1),
                "Terminal rejected a direct scrollbar row request");
        helper.assertTrue(pageMenu.scrollRow() == 1,
                "Terminal did not move its aggregate inventory by one row");
        helper.assertTrue(!ItemStack.isSameItemSameComponents(firstVisible,
                        pageMenu.getSlot(NetworkTerminalMenu.NETWORK_START).getItem()),
                "Scrolled terminal inventory did not replace the first visible row");
        pageMenu.removed(player);
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

    private static int count(net.minecraft.world.item.Item item, ChestBlockEntity... containers) {
        int total = 0;
        for (ChestBlockEntity container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(item)) total += stack.getCount();
            }
        }
        return total;
    }

    private static int countAll(ChestBlockEntity... containers) {
        int total = 0;
        for (ChestBlockEntity container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                total += container.getItem(slot).getCount();
            }
        }
        return total;
    }
}
