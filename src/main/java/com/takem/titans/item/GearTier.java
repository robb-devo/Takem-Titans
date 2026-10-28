package com.takem.titans.item;

public enum GearTier {
    STANDARD(1.0, 1.0, 1.0, 1.0, 1.0),
    ADVANCED(1.6, 1.3, 1.35, 1.3, 1.18),
    /** Ghost steel: every dash phases you out of the world for a heartbeat and leaves afterimages behind. */
    PHANTOM(2.4, 1.5, 1.7, 1.55, 1.42),
    GODSPEED(3.5, 1.7, 2.1, 1.8, 1.7),
    /** Folds space: boost and dash together and you're somewhere else, through a tunnel of light. */
    EVENT_HORIZON(5.0, 1.95, 2.45, 2.05, 1.95);

    public final double gas;
    public final double reel;
    public final double boost;
    public final double dash;
    public final double maxSpeed;

    private GearTier(double gas, double reel, double boost, double dash, double maxSpeed) {
        this.gas = gas;
        this.reel = reel;
        this.boost = boost;
        this.dash = dash;
        this.maxSpeed = maxSpeed;
    }

    /** Fast enough to break the sound barrier: afterburners and the sonic boom. */
    public boolean supersonic() {
        return this == GODSPEED || this == EVENT_HORIZON;
    }

    /** Dashes phase you out: a moment of invulnerability and a trail of afterimages. */
    public boolean phases() {
        return this == PHANTOM || this == EVENT_HORIZON;
    }
}
