package org.destroyermob.mobsstorage.storage;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum LabelDisplayMode implements StringRepresentable {
    SURFACE("surface"),
    BILLBOARD("billboard"),
    CROSSHAIR("crosshair");

    public static final Codec<LabelDisplayMode> CODEC = StringRepresentable.fromEnum(LabelDisplayMode::values);

    private final String serializedName;

    LabelDisplayMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
