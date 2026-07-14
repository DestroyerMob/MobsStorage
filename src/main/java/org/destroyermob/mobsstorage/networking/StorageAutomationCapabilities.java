package org.destroyermob.mobsstorage.networking;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.destroyermob.mobsstorage.registry.ModTags;
import org.destroyermob.mobsstorage.registry.ModBlocks;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlockEntity;

public final class StorageAutomationCapabilities {
    private StorageAutomationCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        level instanceof ServerLevel && blockEntity instanceof NetworkInterfaceBlockEntity terminal
                                ? new NetworkInterfaceItemHandler(terminal) : null,
                ModBlocks.NETWORK_INTERFACE.get());
        // Tags are data-driven and may include storage blocks from other mods, so
        // attach a conditional provider to every registered block. Returning null
        // leaves each block's normal item handler untouched when it is not linked.
        Block[] blocks = BuiltInRegistries.BLOCK.stream().toArray(Block[]::new);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, StorageAutomationCapabilities::create, blocks);
    }

    private static IItemHandlerModifiable create(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            Direction side
    ) {
        if (!(level instanceof ServerLevel serverLevel) || !state.is(ModTags.LABELABLE_STORAGE)) return null;
        Container container = resolveContainer(level, pos, state, blockEntity);
        if (container == null) return null;
        boolean linked = NetworkService.nodeFor(container)
                .flatMap(node -> StorageNetworkSavedData.get(serverLevel.getServer()).get(node.networkId()))
                .isPresent();
        if (!linked) return null;
        IItemHandlerModifiable delegate = container instanceof WorldlyContainer worldly
                ? new SidedInvWrapper(worldly, side)
                : new InvWrapper(container);
        return new NetworkItemHandler(container, delegate);
    }

    private static Container resolveContainer(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            BlockState state,
            BlockEntity blockEntity
    ) {
        if (state.getBlock() instanceof ChestBlock chest) {
            Container combined = ChestBlock.getContainer(chest, state, level, pos, true);
            if (combined != null) return combined;
        }
        return blockEntity instanceof Container container ? container : null;
    }
}
