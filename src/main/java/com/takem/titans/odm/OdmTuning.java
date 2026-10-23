package com.takem.titans.odm;

public final class OdmTuning {
    public static final double HOOK_SPEED = 8.0;
    public static final double HOOK_RETRACT_SPEED = 8.0;
    public static final double MIN_ROPE = 1.6;
    public static final double ROPE_SNAP_MARGIN = 24.0;
    public static final double ROPE_ORIGIN_Y = 0.95;
    public static final double LAUNCHER_SIDE_OFFSET = 0.36;
    public static final double DUAL_SPREAD = Math.toRadians(2.2);
    public static final double REEL_SPEED = 0.95;
    public static final double REEL_ACCEL = 0.125;
    public static final double REEL_SOFT_DISTANCE = 5.0;
    public static final double DUAL_REEL_FACTOR = 0.72;
    public static final double TAKEOFF_LIFT = 0.42;
    public static final double GRAVITY_SCALE = 0.85;
    public static final double DRAG_LINEAR = 0.004;
    public static final double DRAG_QUADRATIC = 0.019;
    public static final double AIR_CONTROL = 0.022;
    public static final double AIR_CONTROL_HOOKED = 0.016;
    public static final double BOOST_ACCEL = 0.092;
    public static final double BOOST_INPUT_BIAS = 0.85;
    public static final double DASH_IMPULSE = 0.95;
    public static final int DASH_COOLDOWN = 16;
    public static final double MAX_SPEED = 3.4;
    public static final double ARRIVAL_BRAKE = 0.11;
    public static final double ARRIVAL_MIN_SPEED = 0.32;
    public static final double MAX_ROPE_CORRECTION = 0.7;
    public static final double IMPACT_SAFE_SPEED = 1.55;
    public static final double IMPACT_DAMAGE_PER_SPEED = 11.0;
    public static final int COST_HOOK_FIRE = 6;
    public static final double COST_REEL_PER_TICK = 1.0;
    public static final double COST_BOOST_PER_TICK = 3.0;
    public static final int COST_DASH = 50;
    public static final int RELOAD_TICKS = 14;
    public static final double BLADE_REACH_BONUS = 1.75;
    public static final float COMBO_STEP = 0.12F;
    public static final int COMBO_MAX_STEPS = 10;
    public static final int COMBO_TIMEOUT = 70;
    public static final int COMBO_GROUND_GRACE = 12;
    public static final int SPIN_TICKS = 10;
    public static final int SPIN_COOLDOWN = 28;
    public static final int COST_SPIN = 70;
    public static final double SPIN_RADIUS = 3.4;
    public static final float SPIN_DAMAGE_MULT = 1.3F;
    public static final int CYCLONE_TICKS = 22;
    public static final int CYCLONE_HIT_INTERVAL = 4;
    public static final int COST_CYCLONE = 90;
    public static final double CYCLONE_RADIUS = 4.4;
    public static final float CYCLONE_DAMAGE_MULT = 0.9F;
    public static final int RUSH_CHARGE_TICKS = 12;
    public static final int RUSH_TICKS = 7;
    public static final double RUSH_SPEED = 3.1;
    public static final double RUSH_EXIT_SPEED = 1.35;
    public static final int RUSH_COOLDOWN = 30;
    public static final int COST_RUSH = 60;
    public static final double RUSH_RADIUS = 2.4;
    public static final float RUSH_DAMAGE_MULT = 1.6F;
    public static final int OVERDRIVE_COMBO = 10;
    public static final int OVERDRIVE_TICKS = 240;
    public static final int OVERDRIVE_COOLDOWN = 300;
    public static final float OVERDRIVE_DAMAGE = 1.5F;
    public static final double OVERDRIVE_BOOST = 1.3;

    private OdmTuning() {
    }

    public static double toKmh(double blocksPerTick) {
        return blocksPerTick * 72.0;
    }
}
