package org.destroyermob.mobsstorage.client;

import net.neoforged.neoforge.client.event.ScreenEvent;

/** Gives the focused terminal search field priority over container and mod keybinds. */
final class TerminalInputCapture {
    private TerminalInputCapture() {
    }

    static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof NetworkTerminalScreen screen
                && screen.captureSearchKey(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (event.getScreen() instanceof NetworkTerminalScreen screen
                && screen.captureSearchCharacter(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }
}
