package com.takem.titans.fx;

import com.takem.titans.network.FxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Fx {
    public static final int DUST_PLUME = 0;
    public static final int CHUNK_IMPACT = 1;
    public static final int SHOCK_RING = 2;
    public static final int SLASH_ARC = 3;
    public static final int SPEAR_BLAST = 4;
    public static final int RUSH_TRAIL = 5;
    public static final int CYCLONE = 6;
    public static final int BLOOD_BURST = 7;
    public static final int OVERDRIVE_SPARK = 8;
    public static final int SERUM_CHARGE = 9;
    public static final int COLOSSAL_BLAST = 10;
    public static final int BOLT = 11;
    public static final int VAPORISE = 12;
    public static final int OVERDRIVE_BURST = 13;
    public static final int NAPE_KILL = 14;
    public static final int TITAN_STOMP = 15;
    public static final int EMERGE = 16;
    public static final int HORN = 17;
    public static final int BOULDER_IMPACT = 18;
    public static final int RIFT = 19;
    public static final int SONIC_BOOM = 20;
    public static final int WAVE_CUT = 21;
    public static final int DUST_SURGE = 22;
    public static final int GLASS_BURST = 23;
    public static final int BLADE_IMPACT = 24;
    public static final int METEOR_SLAM = 25;
    /** A falling piece of building lands: a = diameter, b = impact speed, c = block state id of its material, d = blocks. */
    public static final int COLLAPSE = 30;
    /** Fragments breaking off: a = count, b = burst speed (0 = trickle), c = block state id, d = source width. */
    public static final int CRUMBLE = 31;
    /** A phase dash: a = entity id of the dasher, b = ticks of afterimages. */
    public static final int PHASE = 32;
    /** A hyperspace jump from here to (a, b, c); d = entity id of the jumper. */
    public static final int HYPERSPACE = 33;
    /** Severance: a blade of light from here along (a, b, c), d blocks long. */
    public static final int SEVER = 34;
    /** A waystone journey gathering around a player: a = ticks until it leaves. */
    public static final int TRAVEL_CHARGE = 35;
    /** A traveller leaving (here) for (a, b, c). */
    public static final int TRAVEL_OUT = 36;
    /** A traveller arriving here; a = entity id. */
    public static final int TRAVEL_IN = 37;
    /** A waystone woken for the first time. */
    public static final int WAYSTONE_WAKE = 38;
    /** The Female Titan screams; a = power. */
    public static final int SCREAM = 39;
    /** A crystal spike erupts; a = height. */
    public static final int CRYSTAL = 42;
    /** Crystal floods over a nape (a = 1) or splits off it (a = 0). */
    public static final int HARDEN = 43;
    /** A Cart Titan gun fires; a, b, c = barrel direction. */
    public static final int MUZZLE = 44;
    /** The War Hammer forges a hammer in the air. */
    public static final int FORGE = 45;
    /** The Founder's Edge tears space open; a, b, c = the seam's direction, d = half its length. */
    public static final int REND = 46;
    /** The golden thread from the blade to the tear; a, b, c = where the tear is. */
    public static final int REND_THREAD = 47;
    /** A tear snapping shut. */
    public static final int REND_SNAP = 48;
    /** Sinew torn between a stump and a severed limb; a = limb entity, b = titan entity, c = the limb's joint, d = scale. */
    public static final int SINEW = 49;
    /** A block put back by the builders; a = its state id. */
    public static final int REBUILD = 50;

    private Fx() {
    }

    public static void send(ServerLevel level, int kind, double x, double y, double z, float a, float b, float c, float d, double range) {
        FxPayload msg = new FxPayload(kind, x, y, z, a, b, c, d);
        double r2 = range * range;

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) < r2) {
                PacketDistributor.sendToPlayer(p, msg);
            }
        }
    }

    public static void send(ServerLevel level, int kind, Vec3 at, float a, float b, float c, float d, double range) {
        send(level, kind, at.x, at.y, at.z, a, b, c, d, range);
    }

    public static void send(ServerLevel level, int kind, Vec3 at, float a, float b, double range) {
        send(level, kind, at.x, at.y, at.z, a, b, 0.0F, 0.0F, range);
    }
}
