package com.takem.titans.item;

public enum BladeTier {
    STANDARD(6.0, 1),
    VETERAN(8.5, 2),
    /** Saw-toothed steel from the Armored Titan's hide: rips limbs apart and keeps titans down longer. */
    SERRATED(10.0, 3),
    ACKERMAN(11.5, 3),
    TITANBREAKER(16.0, 6),
    /** The last gift of the Paths: every swing is a wave, every kill echoes through every titan nearby. */
    FOUNDER(24.0, 12);

    public final double damage;
    public final int durability;

    private BladeTier(double damage, int durability) {
        this.damage = damage;
        this.durability = durability;
    }

    /** Blades that throw slash waves on a quick tap of attack. */
    public boolean waves() {
        return this == TITANBREAKER || this == FOUNDER;
    }
}
