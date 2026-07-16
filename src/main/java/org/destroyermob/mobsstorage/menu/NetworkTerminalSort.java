package org.destroyermob.mobsstorage.menu;

public enum NetworkTerminalSort {
    ITEM,
    QUANTITY,
    MOD;

    public int id() {
        return ordinal();
    }

    public NetworkTerminalSort next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static NetworkTerminalSort byId(int id) {
        NetworkTerminalSort[] values = values();
        return values[Math.floorMod(id, values.length)];
    }
}
