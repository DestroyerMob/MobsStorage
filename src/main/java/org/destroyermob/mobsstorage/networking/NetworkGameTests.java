package org.destroyermob.mobsstorage.networking;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.item.NetworkWandMode;
import org.destroyermob.mobsstorage.inventory.InventoryManagementService;
import org.destroyermob.mobsstorage.inventory.InventoryProfile;
import org.destroyermob.mobsstorage.inventory.CarryRule;
import org.destroyermob.mobsstorage.inventory.CarryRuleSet;
import org.destroyermob.mobsstorage.menu.NetworkTerminalMenu;
import org.destroyermob.mobsstorage.menu.TerminalSortMode;
import org.destroyermob.mobsstorage.network.InventoryActionPayload;
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
    public static void networkRoutesAndRefillsWithinAnchorCoverage(GameTestHelper helper) {
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

        NetworkInventoryService.InsertResult offline = NetworkInventoryService.insert(
                player, first, new ItemStack(Items.COBBLESTONE));
        helper.assertTrue(offline.inserted() == 0 && first.isEmpty(),
                "A network without an anchor still accepted routed insertion");
        network.setOrigin(firstPos);

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
                "The network retained an anchor at the missing double-chest half");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void independentLabelRepairsNeighbourAnchorWithoutOverwritingEitherNode(GameTestHelper helper) {
        helper.setBlock(FIRST, Blocks.CHEST.defaultBlockState());
        helper.setBlock(SECOND, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity firstChest = helper.getBlockEntity(FIRST);
        ChestBlockEntity secondChest = helper.getBlockEntity(SECOND);
        BlockPos first = helper.absolutePos(FIRST);
        BlockPos second = helper.absolutePos(SECOND);
        StorageResolver.setLabel(helper.getLevel(), List.of(firstChest), label(first, "#c:ingots"));
        StorageResolver.setLabel(helper.getLevel(), List.of(secondChest), label(second, "#c:gems"));

        StorageNetworkSavedData savedData = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = savedData.create(helper.makeMockServerPlayerInLevel().getUUID(), "Repair Test");
        GlobalPos firstNode = GlobalPos.of(helper.getLevel().dimension(), first);
        GlobalPos secondNode = GlobalPos.of(helper.getLevel().dimension(), second);
        network.addNode(firstNode);
        network.updateNode(firstNode, "Gems", 0, BuiltInRegistries.ITEM.getKey(Items.DIAMOND));
        firstChest.setData(ModAttachments.NETWORK_NODE,
                new NetworkNodeData(network.id(), "Ingots", 0, first));
        secondChest.setData(ModAttachments.NETWORK_NODE,
                new NetworkNodeData(network.id(), "Gems", 0, first));

        NetworkService.reconcileTopology(helper.getLevel(), first);
        NetworkService.reconcileTopology(helper.getLevel(), second);

        NetworkNodeData repaired = NetworkService.existingNode(secondChest).orElseThrow();
        helper.assertTrue(repaired.anchor().equals(second),
                "An independently labelled chest retained its neighbour's network anchor");
        helper.assertTrue(network.nodes().contains(firstNode) && network.nodes().contains(secondNode),
                "Repair did not preserve the original node and add the independent chest");
        helper.assertTrue(network.nodeInfo(firstNode).name().equals("Ingots")
                        && network.nodeInfo(secondNode).name().equals("Gems"),
                "Repair allowed the stale node name to overwrite its neighbour");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void manualInsertionFiltersLocallyInsteadOfRoutingAcrossNetwork(GameTestHelper helper) {
        helper.setBlock(FIRST, Blocks.CHEST);
        helper.setBlock(SECOND, Blocks.CHEST);
        ChestBlockEntity ingots = helper.getBlockEntity(FIRST);
        ChestBlockEntity gems = helper.getBlockEntity(SECOND);
        BlockPos ingotsPos = helper.absolutePos(FIRST);
        BlockPos gemsPos = helper.absolutePos(SECOND);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageResolver.setLabel(helper.getLevel(), List.of(ingots), label(ingotsPos, "#c:ingots"));
        StorageResolver.setLabel(helper.getLevel(), List.of(gems), label(gemsPos, "#c:gems"));

        StorageNetworkSavedData savedData = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = savedData.create(player.getUUID(), "Local Filtering");
        GlobalPos ingotsNode = GlobalPos.of(helper.getLevel().dimension(), ingotsPos);
        GlobalPos gemsNode = GlobalPos.of(helper.getLevel().dimension(), gemsPos);
        network.addNode(ingotsNode);
        network.addNode(gemsNode);
        ingots.setData(ModAttachments.NETWORK_NODE,
                new NetworkNodeData(network.id(), "Ingots", 0, ingotsPos));
        gems.setData(ModAttachments.NETWORK_NODE,
                new NetworkNodeData(network.id(), "Gems", 0, gemsPos));

        ChestMenu menu = ChestMenu.threeRows(20, player.getInventory(), ingots);
        player.containerMenu = menu;
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(ingots.isEmpty() && gems.isEmpty() && menu.getCarried().is(Items.DIAMOND),
                "Manual insertion routed a rejected item into another network chest");

        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(ingots.getItem(0).is(Items.IRON_INGOT) && menu.getCarried().isEmpty(),
                "Manual insertion did not accept an item allowed by the local chest filter");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void doubleChestInsertionStartsAtLogicalSlotZeroFromEitherAnchor(GameTestHelper helper) {
        BlockPos leftPos = FIRST;
        BlockPos rightPos = FIRST.east();
        BlockState left = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState right = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);
        helper.setBlock(leftPos, left);
        helper.setBlock(rightPos, right);
        ChestBlockEntity leftChest = helper.getBlockEntity(leftPos);
        ChestBlockEntity rightChest = helper.getBlockEntity(rightPos);
        BlockPos leftAnchor = helper.absolutePos(leftPos);
        BlockPos rightAnchor = helper.absolutePos(rightPos);

        helper.assertTrue(StorageResolver.logicalStorage(helper.getLevel(), leftAnchor).getFirst() == rightChest,
                "Left-half lookup did not begin with vanilla double-chest slot zero");
        helper.assertTrue(StorageResolver.logicalStorage(helper.getLevel(), rightAnchor).getFirst() == rightChest,
                "Right-half lookup did not begin with vanilla double-chest slot zero");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageNetworkSavedData savedData = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = savedData.create(player.getUUID(), "Double Chest Ordering");
        GlobalPos leftNodePos = GlobalPos.of(helper.getLevel().dimension(), leftAnchor);
        network.addNode(leftNodePos);
        network.setOrigin(leftNodePos);
        NetworkNodeData leftNode = new NetworkNodeData(network.id(), "Materials", 0, leftAnchor);
        leftChest.setData(ModAttachments.NETWORK_NODE, leftNode);
        rightChest.setData(ModAttachments.NETWORK_NODE, leftNode);
        StorageResolver.setLabel(helper.getLevel(), List.of(leftChest, rightChest), label(leftAnchor, "@minecraft"));
        CompoundContainer opened = new CompoundContainer(leftChest, rightChest);

        NetworkInventoryService.InsertResult fromLeft = NetworkInventoryService.insert(
                player, opened, new ItemStack(Items.COBBLESTONE));
        helper.assertTrue(fromLeft.inserted() == 1 && rightChest.getItem(0).is(Items.COBBLESTONE),
                "A left-anchored label inserted into its physical half instead of logical slot zero");
        helper.assertTrue(leftChest.isEmpty(), "A left-anchored insertion unexpectedly used the lower inventory half");

        rightChest.clearContent();
        network.removeNode(leftNodePos);
        GlobalPos rightNodePos = GlobalPos.of(helper.getLevel().dimension(), rightAnchor);
        network.addNode(rightNodePos);
        network.setOrigin(rightNodePos);
        NetworkNodeData rightNode = new NetworkNodeData(network.id(), "Materials", 0, rightAnchor);
        leftChest.setData(ModAttachments.NETWORK_NODE, rightNode);
        rightChest.setData(ModAttachments.NETWORK_NODE, rightNode);
        StorageResolver.setLabel(helper.getLevel(), List.of(leftChest, rightChest), label(rightAnchor, "@minecraft"));

        NetworkInventoryService.InsertResult fromRight = NetworkInventoryService.insert(
                player, opened, new ItemStack(Items.DIRT));
        helper.assertTrue(fromRight.inserted() == 1 && rightChest.getItem(0).is(Items.DIRT),
                "A right-anchored label did not insert into logical slot zero");
        helper.assertTrue(leftChest.isEmpty(), "A right-anchored insertion unexpectedly used the lower inventory half");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void personalInventoryRulesProtectAndOrganiseItems(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.TORCH, 8));
        player.getInventory().setItem(9, new ItemStack(Items.STICK, 3));
        player.getInventory().setItem(10, new ItemStack(Items.COBBLESTONE, 2));
        player.getInventory().setItem(11, new ItemStack(Items.DIRT, 40));
        player.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 62));
        player.getInventory().setItem(13, new ItemStack(Items.DIAMOND, 4));
        ResourceLocation diamond = BuiltInRegistries.ITEM.getKey(Items.DIAMOND);
        InventoryProfile profile = new InventoryProfile(Set.of(9), Set.of(diamond),
                Map.of(), Map.of(), Map.of());
        profile = InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.CONSOLIDATE, -1, player.containerMenu.containerId), profile);
        helper.assertTrue(player.getInventory().getItem(9).is(Items.STICK)
                        && player.getInventory().getItem(9).getCount() == 3,
                "Consolidation moved a locked slot");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(stack ->
                        stack.is(Items.COBBLESTONE) && stack.getCount() == 64),
                "Consolidation did not merge partial stacks");

        profile = InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.SORT_QUANTITY, -1, player.containerMenu.containerId), profile);
        helper.assertTrue(player.getInventory().getItem(10).is(Items.COBBLESTONE)
                        && player.getInventory().getItem(10).getCount() == 64,
                "Quantity sorting did not put the largest stack first");

        SimpleContainer chest = new SimpleContainer(27);
        player.containerMenu = ChestMenu.threeRows(17, player.getInventory(), chest);
        InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.DEPOSIT, -1, player.containerMenu.containerId), profile);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.TORCH), "Deposit moved the protected hotbar");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(stack -> stack.is(Items.DIAMOND)),
                "Deposit moved a favourite item");
        helper.assertTrue(player.getInventory().getItem(9).is(Items.STICK), "Deposit moved a locked slot");
        helper.assertTrue(chest.hasAnyMatching(stack -> stack.is(Items.COBBLESTONE)),
                "Deposit did not move ordinary main-inventory items");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void carryRuleRefillPreservesReservedSlots(GameTestHelper helper) {
        helper.setBlock(FIRST, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(FIRST);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        for (int slot = 1; slot < 34; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        BlockPos playerPos = helper.absolutePos(FIRST).above();
        player.teleportTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);

        StorageNetworkSavedData data = StorageNetworkSavedData.get(helper.getLevel().getServer());
        StorageNetwork network = data.create(player.getUUID(), "Carry Rule Test");
        GlobalPos nodePos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        network.addNode(nodePos);
        network.setOrigin(nodePos);
        data.changed();
        chest.setData(ModAttachments.NETWORK_NODE, new NetworkNodeData(
                network.id(), "Supplies", 0, helper.absolutePos(FIRST)));

        CarryRule rule = new CarryRule("minecraft:iron_ingot", ItemStack.EMPTY, 16, 32, 64);
        CarryRuleSet rules = new CarryRuleSet(List.of(rule), 2);
        int inserted = NetworkRefillService.refillCarryRule(player, rules, 0, 24, 2);
        helper.assertTrue(inserted == 24 && player.getInventory().getItem(0).getCount() == 32,
                "Carry rule did not top the existing stack up to its target");
        helper.assertTrue(chest.getItem(0).getCount() == 40,
                "Carry rule refill removed the wrong amount from network storage");
        int empty = 0;
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot).isEmpty()) empty++;
        helper.assertTrue(empty == 2, "Carry rule refill consumed a reserved empty slot");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void inventoryScrollSwapsSlotsAndWholeHotbars(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().selected = 4;
        player.getInventory().setItem(4, new ItemStack(Items.TORCH));
        player.getInventory().setItem(31, new ItemStack(Items.DIAMOND));

        InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.SWAP_VERTICAL_SLOT, 31,
                player.containerMenu.containerId), InventoryProfile.EMPTY);
        helper.assertTrue(player.getInventory().getItem(4).is(Items.DIAMOND)
                        && player.getInventory().getItem(31).is(Items.TORCH),
                "Vertical scrolling did not swap the selected hotbar column");

        for (int column = 0; column < 9; column++) {
            player.getInventory().setItem(column, new ItemStack(Items.STICK));
            player.getInventory().setItem(18 + column, new ItemStack(Items.COBBLESTONE));
        }
        InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.SWAP_HOTBAR, 18,
                player.containerMenu.containerId), InventoryProfile.EMPTY);
        helper.assertTrue(java.util.stream.IntStream.range(0, 9).allMatch(column ->
                        player.getInventory().getItem(column).is(Items.COBBLESTONE)
                                && player.getInventory().getItem(18 + column).is(Items.STICK)),
                "Hotbar scrolling did not swap the complete inventory row");

        player.getInventory().setItem(0, new ItemStack(Items.DIRT));
        player.getInventory().setItem(9, new ItemStack(Items.APPLE));
        InventoryProfile locked = new InventoryProfile(Set.of(0), Set.of(), Map.of(), Map.of(), Map.of());
        InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.SWAP_HOTBAR, 9,
                player.containerMenu.containerId), locked);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.APPLE)
                        && player.getInventory().getItem(9).is(Items.DIRT),
                "A locked slot incorrectly prevented a whole-hotbar scroll");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void sortingTargetsHoveredInventorySectionAndItemHandler(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.DIRT));
        chest.setItem(1, new ItemStack(Items.DIAMOND));
        chest.setItem(2, new ItemStack(Items.COBBLESTONE));
        ChestMenu chestMenu = ChestMenu.threeRows(18, player.getInventory(), chest);
        player.containerMenu = chestMenu;

        InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.SORT_ITEM, 0, chestMenu.containerId), InventoryProfile.EMPTY);
        helper.assertTrue(chest.getItem(0).is(Items.COBBLESTONE)
                        && chest.getItem(1).is(Items.DIAMOND) && chest.getItem(2).is(Items.DIRT),
                "Hover-targeted sorting did not sort the opened chest");

        player.getInventory().setItem(0, new ItemStack(Items.DIRT));
        player.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE));
        player.getInventory().setItem(9, new ItemStack(Items.STICK));
        int hotbarMenuSlot = findMenuSlot(chestMenu, player.getInventory(), 0);
        InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.SORT_ITEM, hotbarMenuSlot, chestMenu.containerId), InventoryProfile.EMPTY);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.COBBLESTONE)
                        && player.getInventory().getItem(1).is(Items.DIRT),
                "Hover-targeted sorting did not sort the hotbar independently");
        helper.assertTrue(player.getInventory().getItem(9).is(Items.STICK),
                "Hotbar sorting moved an item from the main player inventory");

        ItemStackHandler backpack = new ItemStackHandler(3);
        backpack.setStackInSlot(0, new ItemStack(Items.DIRT));
        backpack.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        backpack.setStackInSlot(2, new ItemStack(Items.COBBLESTONE));
        ItemStackHandler unrelatedHandler = new ItemStackHandler(2);
        unrelatedHandler.setStackInSlot(0, new ItemStack(Items.EMERALD));
        unrelatedHandler.setStackInSlot(1, new ItemStack(Items.STICK));
        HandlerMenu handlerMenu = new HandlerMenu(19, backpack, unrelatedHandler);
        player.containerMenu = handlerMenu;

        InventoryManagementService.apply(player, new InventoryActionPayload(
                InventoryActionPayload.Action.SORT_ITEM, 0, handlerMenu.containerId), InventoryProfile.EMPTY);
        helper.assertTrue(backpack.getStackInSlot(0).is(Items.COBBLESTONE)
                        && backpack.getStackInSlot(1).is(Items.DIAMOND)
                        && backpack.getStackInSlot(2).is(Items.DIRT),
                "Hover-targeted sorting did not sort a NeoForge item-handler inventory");
        helper.assertTrue(unrelatedHandler.getStackInSlot(0).is(Items.EMERALD)
                        && unrelatedHandler.getStackInSlot(1).is(Items.STICK),
                "Item-handler sorting mixed a separate modded inventory into the hovered backpack");
        helper.succeed();
    }

    private static int findMenuSlot(AbstractContainerMenu menu, net.minecraft.world.Container container,
                                    int containerSlot) {
        for (int index = 0; index < menu.slots.size(); index++) {
            var slot = menu.slots.get(index);
            if (slot.container == container && slot.getContainerSlot() == containerSlot) return index;
        }
        throw new IllegalStateException("Container slot is not present in menu");
    }

    private static int findSlot(AbstractContainerMenu menu, int start, net.minecraft.world.item.Item item) {
        for (int index = start; index < menu.slots.size(); index++) {
            if (menu.getSlot(index).getItem().is(item)) return index;
        }
        return -1;
    }

    private static final class HandlerMenu extends AbstractContainerMenu {
        private HandlerMenu(int containerId, IItemHandler primary, IItemHandler secondary) {
            super(null, containerId);
            for (int slot = 0; slot < primary.getSlots(); slot++) addSlot(new SlotItemHandler(primary, slot, 0, 0));
            for (int slot = 0; slot < secondary.getSlots(); slot++) addSlot(new SlotItemHandler(secondary, slot, 0, 0));
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
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
        network.setOrigin(inputPos);
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
        network.setOrigin(outputPos);
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
    public static void networkInterfacesUseAnchorRangeAndCraftWithoutDuplication(GameTestHelper helper) {
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
                "Interface within anchor range could not open its crafting terminal");
        helper.assertTrue(NetworkService.canUseTerminal(player, secondTerminal),
                "A second linked interface within anchor range was not functional");
        helper.assertFalse(NetworkService.withinAnchorRange(network, GlobalPos.of(
                        helper.getLevel().dimension(), ingotsPos.pos().offset(NetworkCoverage.RADIUS + 1, 0, 0))),
                "Interface anchor range extended beyond 256 blocks");

        NetworkTerminalMenu menu = new NetworkTerminalMenu(7, player.getInventory(), terminal);
        menu.setQuery("raw iron", TerminalSortMode.NAME);
        helper.assertTrue(menu.getSlot(NetworkTerminalMenu.NETWORK_START).getItem().is(Items.RAW_IRON),
                "Terminal search did not find a stored item by display name");
        menu.setQuery("", TerminalSortMode.QUANTITY);
        int visibleRawSlot = -1;
        for (int slot = NetworkTerminalMenu.NETWORK_START; slot < NetworkTerminalMenu.NETWORK_END; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.RAW_IRON)) {
                visibleRawSlot = slot;
                break;
            }
        }
        helper.assertTrue(visibleRawSlot >= 0 && menu.getSlot(visibleRawSlot).getItem().getCount() == 5,
                "Crafting terminal did not display the combined network inventory");
        helper.assertFalse(menu.getSlot(visibleRawSlot).mayPickup(player),
                "Recipe transfer could still consume an aggregate display slot");

        menu.extractExact(player, visibleRawSlot, 2);
        helper.assertTrue(menu.getCarried().is(Items.RAW_IRON) && menu.getCarried().getCount() == 2
                        && count(Items.RAW_IRON, rawMaterials) == 3,
                "Terminal exact withdrawal did not remove the requested amount");
        menu.clicked(NetworkTerminalMenu.NETWORK_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && count(Items.RAW_IRON, rawMaterials) == 5,
                "Terminal did not return the exact-withdrawal test stack to the network");

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

        NetworkTerminalMenu fullTransferMenu = new NetworkTerminalMenu(21, player.getInventory(), terminal);
        int physicalRawSlot = findSlot(fullTransferMenu, NetworkTerminalMenu.INGREDIENT_INDEX_START, Items.RAW_IRON);
        helper.assertTrue(physicalRawSlot >= 0, "Recipe transfer could not see the physical raw-iron network slot");
        Slot fullSource = fullTransferMenu.getSlot(physicalRawSlot);
        helper.assertTrue(fullSource.mayPickup(player),
                "Recipe transfer could not consume a physical network slot");
        ItemStack fullIngredient = fullSource.getItem().copy();
        fullSource.setByPlayer(ItemStack.EMPTY);
        fullSource.onTake(player, fullIngredient);
        fullTransferMenu.getSlot(NetworkTerminalMenu.CRAFT_START).setByPlayer(fullIngredient);
        helper.assertTrue(count(Items.RAW_IRON, ingots, rawMaterials) == 0
                        && fullTransferMenu.getSlot(NetworkTerminalMenu.CRAFT_START).getItem().getCount() == 5,
                "Whole-stack recipe transfer filled the crafting grid without removing network items");
        fullTransferMenu.removed(player);
        helper.assertTrue(count(Items.RAW_IRON, ingots, rawMaterials) == 5,
                "Closing after whole-stack recipe transfer duplicated or lost ingredients");

        NetworkTerminalMenu partialTransferMenu = new NetworkTerminalMenu(22, player.getInventory(), terminal);
        physicalRawSlot = findSlot(partialTransferMenu, NetworkTerminalMenu.INGREDIENT_INDEX_START, Items.RAW_IRON);
        Slot partialSource = partialTransferMenu.getSlot(physicalRawSlot);
        ItemStack originalSource = partialSource.getItem().copy();
        ItemStack partialIngredient = originalSource.copyWithCount(2);
        partialSource.getItem().shrink(2);
        partialSource.onTake(player, originalSource);
        partialTransferMenu.getSlot(NetworkTerminalMenu.CRAFT_START).setByPlayer(partialIngredient);
        helper.assertTrue(count(Items.RAW_IRON, ingots, rawMaterials) == 3
                        && partialTransferMenu.getSlot(NetworkTerminalMenu.CRAFT_START).getItem().getCount() == 2,
                "Partial-stack recipe transfer filled the crafting grid without reducing the backing slot");
        partialTransferMenu.removed(player);
        helper.assertTrue(count(Items.RAW_IRON, ingots, rawMaterials) == 5,
                "Closing after partial-stack recipe transfer duplicated or lost ingredients");

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
        ItemStack physicalSource = scrollMenu.getSlot(NetworkTerminalMenu.INGREDIENT_INDEX_START).getItem().copy();
        helper.assertTrue(scrollMenu.maxScrollRows() == 1,
                "Forty-six item types did not create one additional scroll row");
        helper.assertTrue(!physicalSource.isEmpty(),
                "Physical network slots were not exposed to crafting recipe transfer");
        scrollMenu.setCarried(ItemStack.EMPTY);
        scrollMenu.clicked(NetworkTerminalMenu.INGREDIENT_INDEX_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(ItemStack.isSameItemSameComponents(physicalSource, scrollMenu.getCarried()),
                "Crafting transfer index could not pull a physical network item");
        scrollMenu.clicked(NetworkTerminalMenu.CRAFT_START, 0, ClickType.PICKUP, player);
        scrollMenu.removed(player);
        helper.assertTrue(countAll(ingots, rawMaterials) == 46,
                "Physical crafting transfer duplicated or lost a network item");

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
    public static void networkKeepsOneAnchor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageNetwork network = StorageNetworkSavedData.get(helper.getLevel().getServer())
                .create(player.getUUID(), "Anchor Test");
        GlobalPos first = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        GlobalPos second = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SECOND));
        network.addNode(first);
        network.addNode(second);
        network.setOrigin(first);
        helper.assertTrue(network.origin().filter(first::equals).isPresent(), "First anchor was not stored");
        network.setOrigin(second);
        helper.assertTrue(network.origin().filter(second::equals).isPresent(), "Second anchor did not replace the first");
        network.removeNode(second);
        helper.assertTrue(network.origin().isEmpty(), "Removing the anchor node did not clear the anchor");
        helper.succeed();
    }

    @GameTest(template = "storage_labels", timeoutTicks = 20)
    public static void networkCoverageIsSphericalAndDimensionBound(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageNetwork network = StorageNetworkSavedData.get(helper.getLevel().getServer())
                .create(player.getUUID(), "Coverage Test");
        GlobalPos anchor = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FIRST));
        network.addNode(anchor);
        network.setOrigin(anchor);

        helper.assertTrue(NetworkCoverage.contains(network, GlobalPos.of(anchor.dimension(),
                        anchor.pos().offset(NetworkCoverage.RADIUS, 0, 0))),
                "Network coverage excluded its spherical boundary");
        helper.assertFalse(NetworkCoverage.contains(network, GlobalPos.of(anchor.dimension(),
                        anchor.pos().offset(NetworkCoverage.RADIUS, NetworkCoverage.RADIUS, 0))),
                "Network coverage still used an axis-aligned cube");
        helper.assertFalse(NetworkCoverage.contains(network, GlobalPos.of(Level.NETHER,
                        anchor.pos())),
                "Network coverage crossed dimensions without a dimensional anchor");
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
                "Wand did not cycle to Set Network Anchor mode");
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
