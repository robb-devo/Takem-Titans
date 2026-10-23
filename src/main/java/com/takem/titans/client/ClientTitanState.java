package com.takem.titans.client;

public final class ClientTitanState {
    private static int grabbedBy = -1;
    public static int struggleClicks;
    public static int grabTicks;

    private ClientTitanState() {
    }

    public static boolean isGrabbed() {
        return grabbedBy >= 0;
    }

    public static int grabbedBy() {
        return grabbedBy;
    }

    public static void setGrabbed(int titanId) {
        if (titanId >= 0 && grabbedBy < 0) {
            struggleClicks = 0;
            grabTicks = 0;
        }

        grabbedBy = titanId;
    }

    public static void reset() {
        grabbedBy = -1;
        struggleClicks = 0;
        grabTicks = 0;
    }
}
