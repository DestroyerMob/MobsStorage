package org.destroyermob.mobsstorage.client;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.ControllerInputs;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.bind.InputBindingLayer;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.api.event.ControlifyEvents;
import dev.isxander.controlify.bindings.BindContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class BeyondControlsIntegration implements ControlifyEntrypoint {
    private static final Component CATEGORY = Component.translatable("key.categories.mobsstorage");
    private static final int CONTEXT_PRIORITY = 200;
    private static final int LAYER_PRIORITY = 300;

    private static InputBindingSupplier sortItem;
    private static InputBindingSupplier inventoryLayer;
    private static InputBindingLayer inventoryActions;
    private static InputBindingSupplier verticalPrevious;
    private static InputBindingSupplier verticalNext;
    private static InputBindingSupplier hotbarPrevious;
    private static InputBindingSupplier hotbarNext;
    private static InputBindingSupplier horizontalPrevious;
    private static InputBindingSupplier horizontalNext;

    @Override
    public void onControlifyPreInit(PreInitContext context) {
        sortItem = context.bindings().registerBinding(builder -> builder
                .id("fabric-key-binding-api-v1", "key.mobsstorage.sort_item")
                .name(Component.translatable("key.mobsstorage.sort_item"))
                .category(CATEGORY)
                .defaultInput(ControllerInputs.button("right_stick"))
                .allowedContexts(BindContext.CONTAINER)
                .priority(CONTEXT_PRIORITY)
                .addKeyCorrelation(InventoryControls.sortItemKey()));

        inventoryLayer = context.bindings().registerBinding(builder -> builder
                .id("mobsstorage", "inventory_layer")
                .name(Component.translatable("control.mobsstorage.inventory_layer"))
                .category(CATEGORY)
                .defaultInput(ControllerInputs.button("right_stick"))
                .allowedContexts(BindContext.IN_GAME)
                // Observe the modifier without owning it, so right-stick still
                // crouches. Only the second button in a layer chord has priority.
                .priority(0));

        inventoryActions = InputBindingLayer.whileHeld(
                ResourceLocation.fromNamespaceAndPath("mobsstorage", "inventory_actions"),
                LAYER_PRIORITY,
                inventoryLayer
        );

        verticalPrevious = layerBinding(context, "vertical_previous", "left_shoulder");
        verticalNext = layerBinding(context, "vertical_next", "right_shoulder");
        hotbarPrevious = layerBinding(context, "hotbar_previous", "dpad_up");
        hotbarNext = layerBinding(context, "hotbar_next", "dpad_down");
        horizontalPrevious = layerBinding(context, "horizontal_previous", "dpad_left");
        horizontalNext = layerBinding(context, "horizontal_next", "dpad_right");
    }

    private InputBindingSupplier layerBinding(PreInitContext context, String path, String button) {
        return context.bindings().registerBinding(builder -> builder
                .id("mobsstorage", path)
                .name(Component.translatable("control.mobsstorage." + path))
                .category(CATEGORY)
                .defaultInput(ControllerInputs.button(button))
                .allowedContexts(BindContext.IN_GAME)
                .layer(inventoryActions));
    }

    @Override
    public void onControlifyInit(InitContext context) {
        ControlifyEvents.ACTIVE_CONTROLLER_TICKED.register(event -> {
            var controller = event.controller();
            if (sortItem.on(controller).justPressed()) InventoryControls.triggerControllerSort();

            step(controller, verticalPrevious, InventoryScrollControls.Mode.VERTICAL_SLOT, -1);
            step(controller, verticalNext, InventoryScrollControls.Mode.VERTICAL_SLOT, 1);
            step(controller, hotbarPrevious, InventoryScrollControls.Mode.HOTBAR, -1);
            step(controller, hotbarNext, InventoryScrollControls.Mode.HOTBAR, 1);
            step(controller, horizontalPrevious, InventoryScrollControls.Mode.HORIZONTAL_SLOT, -1);
            step(controller, horizontalNext, InventoryScrollControls.Mode.HORIZONTAL_SLOT, 1);

            if (inventoryLayer.on(controller).justReleased()) {
                InventoryScrollControls.finishControllerLayer();
            }
        });
    }

    private static void step(dev.isxander.controlify.controller.ControllerEntity controller,
                             InputBindingSupplier binding, InventoryScrollControls.Mode mode, int direction) {
        if (binding.on(controller).justPressed()) {
            InventoryScrollControls.controllerStep(mode, direction);
        }
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlify) {
    }
}
