package com.takem.titans.item;

/**
 * Upgrades fitted into an ODM rig. A rig has room for one (Standard, Advanced), two (Phantom, Godspeed) or three
 * (Event Horizon); the fitted ones are kept on the gear itself as a bitmask.
 */
public enum GearMod {
    /** Recovers spent gas: everything costs 30% less. */
    RECYCLER,
    /** A heavier winch motor: reels in 20% harder. */
    WINCH,
    /** A lighter frame: a third more air control and a little less weight to fall with. */
    FRAME,
    /** Longer cable spools: hooks reach a quarter further. */
    LONG_HOOKS,
    /** Barbed heads: a hook that strikes a titan tears it and slows it down. */
    BARBS,
    /** A second gas chamber: the dash recharges a third faster. */
    TWIN_CHAMBER;

    public int bit() {
        return 1 << this.ordinal();
    }

    public String id() {
        return this.name().toLowerCase();
    }

    public static int slots(GearTier tier) {
        return switch (tier) {
            case PHANTOM, GODSPEED -> 2;
            case EVENT_HORIZON -> 3;
            default -> 1;
        };
    }
}
