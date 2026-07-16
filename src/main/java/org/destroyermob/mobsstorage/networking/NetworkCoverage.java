package org.destroyermob.mobsstorage.networking;

import net.minecraft.core.GlobalPos;

/** Shared coverage rules for every storage-network operation. */
public final class NetworkCoverage {
    public static final int RADIUS = 256;
    private static final long RADIUS_SQUARED = (long) RADIUS * RADIUS;

    private NetworkCoverage() {
    }

    public static boolean contains(StorageNetwork network, GlobalPos position) {
        return network.origin().filter(anchor -> contains(anchor, position)).isPresent();
    }

    public static boolean contains(GlobalPos anchor, GlobalPos position) {
        if (!anchor.dimension().equals(position.dimension())) return false;
        long dx = (long) anchor.pos().getX() - position.pos().getX();
        long dy = (long) anchor.pos().getY() - position.pos().getY();
        long dz = (long) anchor.pos().getZ() - position.pos().getZ();
        return dx * dx + dy * dy + dz * dz <= RADIUS_SQUARED;
    }
}
