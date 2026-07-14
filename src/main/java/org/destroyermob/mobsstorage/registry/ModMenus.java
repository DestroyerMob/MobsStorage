package org.destroyermob.mobsstorage.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.menu.NetworkTerminalMenu;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, MobsStorage.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<NetworkTerminalMenu>> NETWORK_TERMINAL =
            MENUS.register("network_terminal", () -> IMenuTypeExtension.create(NetworkTerminalMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
