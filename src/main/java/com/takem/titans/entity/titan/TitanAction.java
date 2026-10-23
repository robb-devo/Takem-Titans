package com.takem.titans.entity.titan;

public enum TitanAction {
    NONE(0),
    GRAB_RIGHT(26),
    GRAB_LEFT(26),
    HOLD_RIGHT(64),
    HOLD_LEFT(64),
    BITE_RIGHT(22),
    BITE_LEFT(22),
    SWAT_RIGHT(22),
    SWAT_LEFT(22),
    STOMP(24),
    LEAP(34),
    ROAR(44),
    STEAM(90),
    KICK(50),
    BLINDED(80),
    FLINCH(12);

    public final int duration;
    private static final TitanAction[] VALUES = values();

    private TitanAction(int duration) {
        this.duration = duration;
    }

    public static TitanAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public float side() {
        return switch (this) {
            case GRAB_RIGHT, HOLD_RIGHT, BITE_RIGHT, SWAT_RIGHT -> -1.0F;
            case GRAB_LEFT, HOLD_LEFT, BITE_LEFT, SWAT_LEFT -> 1.0F;
            default -> 0.0F;
        };
    }

    public boolean isGrab() {
        return this == GRAB_RIGHT || this == GRAB_LEFT;
    }

    public boolean isHold() {
        return this == HOLD_RIGHT || this == HOLD_LEFT;
    }

    public boolean isBite() {
        return this == BITE_RIGHT || this == BITE_LEFT;
    }

    public boolean isSwat() {
        return this == SWAT_RIGHT || this == SWAT_LEFT;
    }

    public boolean isBusy() {
        return this != NONE && this != FLINCH;
    }
}
