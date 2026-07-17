package org.destroyermob.mobsstorage.mixin;

import net.neoforged.neoforge.client.event.ScreenEvent;
import org.destroyermob.mobsstorage.client.BundleSelectionControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Mouse Tweaks' post-event wheel transfer after bundle selection consumes a scroll. */
@Pseudo
@Mixin(targets = "yalter.mousetweaks.neoforge.MouseTweaksNeo", remap = false)
public abstract class MouseTweaksNeoMixin {
    @Inject(method = "onGuiMouseScrollPost", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void mobsstorage$blockBundleWheelTransfer(
            ScreenEvent.MouseScrolled.Post event, CallbackInfo callback
    ) {
        if (BundleSelectionControls.handlesBundleScroll(event.getScreen(), event.getScrollDeltaY())) {
            callback.cancel();
        }
    }
}
