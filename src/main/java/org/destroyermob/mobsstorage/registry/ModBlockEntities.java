package org.destroyermob.mobsstorage.registry;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlockEntity;
import org.destroyermob.mobsstorage.world.NetworkPortBlockEntity;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MobsStorage.MOD_ID);

    public static final Supplier<BlockEntityType<NetworkInterfaceBlockEntity>> NETWORK_INTERFACE = TYPES.register(
            ModBlocks.NETWORK_INTERFACE_ID,
            () -> BlockEntityType.Builder.of(
                    NetworkInterfaceBlockEntity::new, ModBlocks.NETWORK_INTERFACE.get()).build(null)
    );

    public static final Supplier<BlockEntityType<NetworkPortBlockEntity>> NETWORK_PORT = TYPES.register(
            "network_port",
            () -> BlockEntityType.Builder.of(
                    NetworkPortBlockEntity::new,
                    ModBlocks.NETWORK_INPUT.get(), ModBlocks.NETWORK_OUTPUT.get()).build(null)
    );

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
