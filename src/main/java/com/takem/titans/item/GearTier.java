package com.takem.titans.item;

public enum GearTier {
    STANDARD(1.0, 1.0, 1.0, 1.0, 1.0),
    ADVANCED(1.6, 1.3, 1.35, 1.3, 1.18),
    GODSPEED(3.5, 1.7, 2.1, 1.8, 1.7);

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
}
