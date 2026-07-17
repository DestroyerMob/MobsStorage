package org.destroyermob.mobsstorage.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.destroyermob.mobsstorage.networking.NetworkPortItemHandler;
import org.destroyermob.mobsstorage.registry.ModBlockEntities;
import org.destroyermob.mobsstorage.registry.ModBlocks;
import org.destroyermob.mobsstorage.storage.FilterRules;

public final class NetworkPortBlockEntity extends BlockEntity implements Container {
    public static final int MAX_FILTER_LENGTH = 256;
    private static final String OUTPUT_FILTER_TAG = "OutputFilter";
    private final IItemHandler automationHandler;
    private String outputFilter = "";

    public NetworkPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_PORT.get(), pos, state);
        automationHandler = new NetworkPortItemHandler(this);
    }

    public boolean isInput() {
        return getBlockState().is(ModBlocks.NETWORK_INPUT.get());
    }

    public boolean isOutput() {
        return getBlockState().is(ModBlocks.NETWORK_OUTPUT.get());
    }

    public IItemHandler automationHandler() {
        return automationHandler;
    }

    public String outputFilter() {
        return outputFilter;
    }

    public void setOutputFilter(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > MAX_FILTER_LENGTH) {
            normalized = normalized.substring(0, MAX_FILTER_LENGTH);
        }
        if (outputFilter.equals(normalized)) return;
        outputFilter = normalized;
        setChanged();
    }

    public boolean allowsOutput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (outputFilter.isBlank()) return true;
        Item.TooltipContext context = getLevel() == null
                ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(getLevel());
        return FilterRules.matches(stack, java.util.List.of(outputFilter), context);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outputFilter = tag.getString(OUTPUT_FILTER_TAG);
        if (outputFilter.length() > MAX_FILTER_LENGTH) {
            outputFilter = outputFilter.substring(0, MAX_FILTER_LENGTH);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!outputFilter.isBlank()) tag.putString(OUTPUT_FILTER_TAG, outputFilter);
    }

    @Override public int getContainerSize() { return 0; }
    @Override public boolean isEmpty() { return true; }
    @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) { }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { }
}
