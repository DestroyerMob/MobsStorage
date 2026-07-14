package org.destroyermob.mobsstorage.registry;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlockEntity;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MobsStorage.MOD_ID);

    public static final Supplier<BlockEntityType<NetworkInterfaceBlockEntity>> NETWORK_INTERFACE = TYPES.register(
            ModBlocks.NETWORK_INTERFACE_ID,
            () -> BlockEntityType.Builder.of(
                    NetworkInterfaceBlockEntity::new, ModBlocks.NETWORK_INTERFACE.get()).build(null)
    );

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
