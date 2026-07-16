package org.destroyermob.mobsstorage.menu;

import net.minecraft.network.chat.Component;

public enum TerminalSortMode {
    NAME,
    QUANTITY,
    MOD;

    public TerminalSortMode next() {
        TerminalSortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Component displayName() {
        return Component.translatable("screen.mobsstorage.terminal.sort." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
