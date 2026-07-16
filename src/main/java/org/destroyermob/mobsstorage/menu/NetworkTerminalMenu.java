package org.destroyermob.mobsstorage.menu;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.destroyermob.mobsstorage.networking.NetworkInventoryService;
import org.destroyermob.mobsstorage.networking.NetworkService;
import org.destroyermob.mobsstorage.registry.ModMenus;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlockEntity;
import org.jetbrains.annotations.Nullable;

public final class NetworkTerminalMenu extends AbstractContainerMenu {
    public static final int SCROLL_TO_BUTTON_BASE = 1000;
    public static final int NETWORK_START = 0;
    public static final int NETWORK_END = NETWORK_START + NetworkTerminalView.VISIBLE_SIZE;
    public static final int RESULT_SLOT = NETWORK_END;
    public static final int CRAFT_START = RESULT_SLOT + 1;
    public static final int CRAFT_END = CRAFT_START + 9;
    public static final int PLAYER_START = CRAFT_END;
    public static final int PLAYER_END = PLAYER_START + 27;
    public static final int HOTBAR_START = PLAYER_END;
    public static final int HOTBAR_END = HOTBAR_START + 9;
    public static final int INGREDIENT_INDEX_START = HOTBAR_END;

    private final NetworkTerminalView network;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private final ContainerData scrollData = new SimpleContainerData(2);
    @Nullable private final NetworkInterfaceBlockEntity terminal;

    public NetworkTerminalMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, readClientData(data));
    }

    private NetworkTerminalMenu(int containerId, Inventory playerInventory, ClientData data) {
        this(containerId, playerInventory, null,
                ContainerLevelAccess.create(playerInventory.player.level(), data.pos()), data.ingredientSlots());
    }

    public NetworkTerminalMenu(int containerId, Inventory playerInventory, NetworkInterfaceBlockEntity terminal) {
        this(containerId, playerInventory, terminal,
                ContainerLevelAccess.create(terminal.getLevel(), terminal.getBlockPos()),
                NetworkInventoryService.networkSlotCount(terminal));
    }

    private NetworkTerminalMenu(int containerId, Inventory playerInventory,
                                @Nullable NetworkInterfaceBlockEntity terminal, ContainerLevelAccess access,
                                int ingredientSlots) {
        super(ModMenus.NETWORK_TERMINAL.get(), containerId);
        this.player = playerInventory.player;
        this.terminal = terminal;
        this.access = access;
        this.network = new NetworkTerminalView(terminal, ingredientSlots);

        for (int row = 0; row < NetworkTerminalView.VISIBLE_ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new NetworkSlot(network, column + row * 9, 8 + column * 18,
                        37 + row * 18, false));
            }
        }

        addSlot(new ResultSlot(playerInventory.player, craftSlots, resultSlots, 0, 120, 174));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(craftSlots, column + row * 3, 29 + column * 18, 160 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 218 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 276));
        }
        for (int slot = 0; slot < ingredientSlots; slot++) {
            addSlot(new NetworkSlot(network.ingredientIndex(), slot, -10000, -10000, true));
        }

        scrollData.set(0, network.scrollRow());
        scrollData.set(1, network.maxScrollRows());
        addDataSlots(scrollData);
        updateCraftingResult(null);
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == craftSlots) updateCraftingResult(null);
    }

    private void updateCraftingResult(@Nullable RecipeHolder<CraftingRecipe> preferredRecipe) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Level level = player.level();
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> match = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level, preferredRecipe);
        if (match.isPresent()) {
            RecipeHolder<CraftingRecipe> recipe = match.get();
            if (resultSlots.setRecipeUsed(level, serverPlayer, recipe)) {
                ItemStack assembled = recipe.value().assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) result = assembled;
            }
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                containerId, incrementStateId(), RESULT_SLOT, result));
    }

    @Override
    public void broadcastChanges() {
        if (terminal != null) {
            network.refresh();
            scrollData.set(0, network.scrollRow());
            scrollData.set(1, network.maxScrollRows());
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int targetRow;
        if (id == 0) targetRow = network.scrollRow() - 1;
        else if (id == 1) targetRow = network.scrollRow() + 1;
        else if (id >= SCROLL_TO_BUTTON_BASE) targetRow = id - SCROLL_TO_BUTTON_BASE;
        else return false;
        network.setScrollRow(targetRow);
        scrollData.set(0, network.scrollRow());
        scrollData.set(1, network.maxScrollRows());
        broadcastFullState();
        return true;
    }

    public int scrollRow() {
        return scrollData.get(0);
    }

    public int maxScrollRows() {
        return Math.max(0, scrollData.get(1));
    }

    public void updateView(String query, NetworkTerminalSort sort, boolean descending) {
        if (terminal == null) return;
        network.setView(query, sort, descending);
        scrollData.set(0, network.scrollRow());
        scrollData.set(1, network.maxScrollRows());
        broadcastFullState();
    }

    @Override
    public boolean stillValid(Player player) {
        return terminal == null || NetworkService.canUseTerminal(player, terminal);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (terminal != null && networkSlot(slotId) && clickType == ClickType.PICKUP && !getCarried().isEmpty()) {
            ItemStack carried = getCarried();
            int requested = button == 1 ? 1 : carried.getCount();
            ItemStack offered = carried.copyWithCount(requested);
            int inserted = NetworkInventoryService.insertAutomated(terminal, offered, false)
                    .map(NetworkInventoryService.InsertResult::inserted)
                    .orElse(0);
            if (inserted > 0) {
                carried.shrink(inserted);
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                broadcastChanges();
            }
            return;
        }
        if (terminal != null && networkSlot(slotId) && clickType == ClickType.PICKUP && getCarried().isEmpty()) {
            Slot slot = getSlot(slotId);
            ItemStack shown = slot.getItem();
            if (!shown.isEmpty()) {
                int requested = button == 1 ? (shown.getCount() + 1) / 2 : shown.getCount();
                ItemStack extracted = slot.remove(requested);
                if (!extracted.isEmpty()) {
                    setCarried(extracted);
                    slot.onTake(player, extracted);
                    broadcastChanges();
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        if (networkSlot(index)) {
            ItemStack shown = slot.getItem().copy();
            ItemStack extracted = slot.remove(shown.getCount());
            if (extracted.isEmpty()) return ItemStack.EMPTY;
            int extractedCount = extracted.getCount();
            moveItemStackTo(extracted, PLAYER_START, HOTBAR_END, true);
            returnRemainder(extracted, player);
            return extractedCount == extracted.getCount() ? ItemStack.EMPTY : shown.copyWithCount(extractedCount);
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == RESULT_SLOT) {
            access.execute((level, pos) -> stack.getItem().onCraftedBy(stack, level, player));
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_END, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, original);
        } else if (index >= CRAFT_START && index < CRAFT_END
                || index >= PLAYER_START && index < HOTBAR_END) {
            int inserted = insertIntoNetwork(stack);
            if (inserted <= 0) {
                if (index >= PLAYER_START && index < PLAYER_END) {
                    if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) return ItemStack.EMPTY;
                } else if (index >= HOTBAR_START && index < HOTBAR_END) {
                    if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                stack.shrink(inserted);
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (index == RESULT_SLOT) player.drop(stack, false);
        return original;
    }

    private int insertIntoNetwork(ItemStack stack) {
        if (terminal == null || stack.isEmpty()) return 0;
        return NetworkInventoryService.insertAutomated(terminal, stack.copy(), false)
                .map(NetworkInventoryService.InsertResult::inserted)
                .orElse(0);
    }

    private boolean networkSlot(int slot) {
        return slot >= NETWORK_START && slot < NETWORK_END
                || slot >= INGREDIENT_INDEX_START && slot < slots.size();
    }

    private static ClientData readClientData(RegistryFriendlyByteBuf data) {
        return new ClientData(data.readBlockPos(), data.readVarInt());
    }

    private void returnRemainder(ItemStack stack, Player player) {
        if (stack.isEmpty()) return;
        if (terminal != null) {
            stack = NetworkInventoryService.insertAutomated(terminal, stack, false)
                    .map(NetworkInventoryService.InsertResult::remainder)
                    .orElse(stack);
        }
        if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        for (int slot = 0; slot < craftSlots.getContainerSize(); slot++) {
            returnRemainder(craftSlots.removeItemNoUpdate(slot), player);
        }
        resultSlots.clearContent();
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    private static final class NetworkSlot extends Slot {
        private final boolean recipeTransferSource;

        private NetworkSlot(Container container, int slot, int x, int y, boolean recipeTransferSource) {
            super(container, slot, x, y);
            this.recipeTransferSource = recipeTransferSource;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return recipeTransferSource;
        }
    }

    private record ClientData(BlockPos pos, int ingredientSlots) { }
}
