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
