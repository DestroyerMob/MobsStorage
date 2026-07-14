package org.destroyermob.mobsstorage.registry;

import java.util.function.Supplier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlock;

public final class ModBlocks {
    public static final String NETWORK_INTERFACE_ID = "network_interface";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MobsStorage.MOD_ID);

    public static final Supplier<NetworkInterfaceBlock> NETWORK_INTERFACE = BLOCKS.register(
            NETWORK_INTERFACE_ID,
            () -> new NetworkInterfaceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN)
                    .lightLevel(state -> 7))
    );

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
