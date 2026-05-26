package com.foxablazeultimate.client;

public final class ClientCrystalLockState {

    private static volatile boolean raphaelCrystalLocked;

    private ClientCrystalLockState() {}

    public static boolean isRaphaelCrystalLocked() {
        return raphaelCrystalLocked;
    }

    public static void setRaphaelCrystalLocked(boolean locked) {
        raphaelCrystalLocked = locked;
    }
}
