package org.destroyermob.mobsstorage.item;

import java.util.Locale;

public enum NetworkWandMode {
    ADD_STORAGE,
    SET_ORIGIN,
    CONFIGURE_STORAGE;

    public NetworkWandMode next() {
        NetworkWandMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "item.mobsstorage.network_wand.mode." + serializedName();
    }

    public static NetworkWandMode byName(String value) {
        if (value != null) {
            for (NetworkWandMode mode : values()) {
                if (mode.serializedName().equals(value)) {
                    return mode;
                }
            }
        }
        return ADD_STORAGE;
    }
}
