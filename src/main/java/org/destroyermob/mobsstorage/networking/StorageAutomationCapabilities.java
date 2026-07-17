package org.destroyermob.mobsstorage.networking;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.destroyermob.mobsstorage.registry.ModBlocks;
import org.destroyermob.mobsstorage.registry.ModTags;
import org.destroyermob.mobsstorage.world.NetworkPortBlockEntity;

public final class StorageAutomationCapabilities {
    private StorageAutomationCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        level instanceof ServerLevel && blockEntity instanceof NetworkPortBlockEntity port
                                ? port.automationHandler() : null,
                ModBlocks.NETWORK_INPUT.get(), ModBlocks.NETWORK_OUTPUT.get());

        Block[] blocks = BuiltInRegistries.BLOCK.stream().toArray(Block[]::new);
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        level instanceof ServerLevel serverLevel && state.is(ModTags.LABELABLE_STORAGE)
                                && NetworkService.findNode(level, pos)
                                .flatMap(node -> StorageNetworkSavedData.get(serverLevel.getServer())
                                        .get(node.networkId()))
                                .isPresent()
                                ? BlockedNetworkStorageItemHandler.INSTANCE : null,
                blocks);
    }
}
