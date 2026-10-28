package com.takem.titans.client.fx;

import com.takem.titans.client.ClientEffects;
import com.takem.titans.client.gui.ScreenFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * What the late rigs look like. The Phantom rig is cold and quiet: icy wisps and afterimages. The Event Horizon rig is
 * the opposite of quiet: space ripples behind it in expanding violet rings, and a hyperspace jump implodes where you
 * were, races a tunnel of light to where you'll be and bursts open there.
 */
public final class HyperFx {
    public static final int PHANTOM = 9238015;
    public static final int VIOLET = 11560703;
    private static final int WHITE_VIOLET = 15786239;
    private static final DustParticleOptions ICE = new DustParticleOptions(new Vector3f(0.55F, 0.92F, 1.0F), 1.1F);

    private HyperFx() {
    }

    /** Someone else phased: their afterimages, and a cold shimmer where they vanished. */
    public static void phase(ClientLevel level, Vec3 at, int entityId, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entityId != mc.player.getId()) {
            if (level.getEntity(entityId) instanceof Player p) {
                GhostTrail.burst(p, ticks, PHANTOM);
            }

            phaseBurst(level, at.add(0.0, 1.0, 0.0), level.random);
        }
    }

    /** The cold flicker of a phase dash: frost motes snapping out of the air. */
    public static void phaseBurst(ClientLevel level, Vec3 at, RandomSource rnd) {
        for (int i = 0; i < 18; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ICE, at.x + d.x * 0.5, at.y + d.y * 0.8, at.z + d.z * 0.5, d.x * 0.12, d.y * 0.08, d.z * 0.12);
        }
    }

    /** The Phantom rig at speed: icy wisps off the gas jets and, now and then, an afterimage left behind. */
    public static void phantomWake(Player p, Vec3 vel, RandomSource rnd, boolean local) {
        Minecraft mc = Minecraft.getInstance();
        float yaw = p.yBodyRot * 0.017453292F;
        Vec3 back = new Vec3(Math.sin((double)yaw), 0.0, -Math.cos((double)yaw)).scale(0.45);
        Vec3 base = p.position().add(0.0, 0.85, 0.0).add(back);
        Vec3 drift = vel.scale(-0.25);

        for (int i = 0; i < 2; i++) {
            mc.level
                .addParticle(
                    ICE,
                    base.x + (rnd.nextDouble() - 0.5) * 0.5,
                    base.y + (rnd.nextDouble() - 0.5) * 0.4,
                    base.z + (rnd.nextDouble() - 0.5) * 0.5,
                    drift.x,
                    drift.y + 0.01,
                    drift.z
                );
        }

        if (rnd.nextInt(3) == 0) {
            mc.level.addParticle(ParticleTypes.END_ROD, base.x, base.y, base.z, drift.x * 0.6, drift.y * 0.6, drift.z * 0.6);
        }

        if (vel.length() > 1.3 && p.tickCount % (local ? 4 : 3) == 0) {
            GhostTrail.drop(p, PHANTOM, 8, 0.3F);
        }
    }

    /**
     * The Event Horizon rig under boost: no flame, just space bending. Rings of violet light open behind the motor and
     * slide back along the path, faster and closer together the faster you go.
     */
    public static void horizonWake(Player p, Vec3 vel, boolean hot, RandomSource rnd) {
        Minecraft mc = Minecraft.getInstance();
        float yaw = p.yBodyRot * 0.017453292F;
        Vec3 back = new Vec3(Math.sin((double)yaw), 0.0, -Math.cos((double)yaw)).scale(0.55);
        Vec3 base = p.position().add(0.0, 0.85, 0.0).add(back);
        Vec3 dir = vel.lengthSqr() > 1.0E-4 ? vel.normalize() : p.getLookAngle();
        if (p.tickCount % (hot ? 2 : 3) == 0) {
            WorldFx.add(new HyperFx.WakeRing(base, dir, vel.scale(-0.35), hot ? 1.4F : 0.9F, 9));
        }

        for (int i = 0; i < (hot ? 4 : 2); i++) {
            Vec3 d = ClientFx.randomDir(rnd).scale(0.35);
            mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, base.x + d.x, base.y + d.y, base.z + d.z, -dir.x * 0.3, -dir.y * 0.3, -dir.z * 0.3);
        }

        if (rnd.nextInt(2) == 0) {
            mc.level.addParticle(ParticleTypes.END_ROD, base.x, base.y, base.z, -vel.x * 0.3, -vel.y * 0.3, -vel.z * 0.3);
        }

        if (hot && p.tickCount % 2 == 0) {
            Vec3 at = p.position().add(0.0, 0.9, 0.0);
            Vec3 tail = at.subtract(dir.scale(Math.min(7.0, vel.length() * 1.8)));
            WorldFx.add(new WorldFx.RushTrail(tail, at, 1.6F, VIOLET));
        }
    }

    /** A hyperspace jump from {@code from} to {@code to}. */
    public static void jump(ClientLevel level, Vec3 from, Vec3 to, int entityId) {
        RandomSource rnd = level.random;
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && entityId == mc.player.getId();
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        dir = len < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.scale(1.0 / len);
        // where you were folds in on itself
        WorldFx.add(new EpicFx.Implode(from, 7.0F, 10, VIOLET));
        if (!self) {
            WorldFx.add(new WorldFx.Flash(from, 2.5F, 6));
            if (level.getEntity(entityId) instanceof Player p) {
                GhostTrail.drop(p, VIOLET, 16, 0.7F);
            }
        }

        // the tunnel
        WorldFx.add(new HyperFx.Tunnel(from, to, rnd));
        for (int i = 0; i < 50; i++) {
            Vec3 at = from.lerp(to, rnd.nextDouble());
            Vec3 d = ClientFx.randomDir(rnd).scale(0.3 + rnd.nextDouble() * 1.4);
            level.addAlwaysVisibleParticle(
                i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, at.x + d.x, at.y + d.y, at.z + d.z, dir.x * 0.6, dir.y * 0.6, dir.z * 0.6
            );
        }

        // and where you'll be bursts open
        WorldFx.add(new WorldFx.Flash(self ? to.add(dir.scale(4.0)) : to, 3.5F, 7));
        WorldFx.add(new WorldFx.ShockRing(to.add(0.0, -0.8, 0.0), 16.0F, 16, 1.2F, 0.0F, VIOLET, 0.75F));
        WorldFx.add(new WorldFx.SonicRing(to, dir, 9.0F, 12, WHITE_VIOLET));
        WorldFx.add(new WorldFx.Starburst(self ? to.add(dir.scale(5.0)) : to, 22, 9.0F, 0.35F, 12, VIOLET, rnd));

        for (int i = 0; i < 5; i++) {
            WorldFx.add(new WorldFx.Bolt(to, to.add(ClientFx.randomDir(rnd).scale(3.0 + rnd.nextDouble() * 4.0)), 0.07F, VIOLET, rnd));
        }

        for (int i = 0; i < 60; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            double sp = 0.4 + rnd.nextDouble() * 0.7;
            level.addAlwaysVisibleParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WITCH, to.x, to.y, to.z, d.x * sp, d.y * sp, d.z * sp);
        }

        // a ring of dust kicked off the street if the jump ends near the ground
        double ground = DebrisFx.groundY(level, to, 5);
        if (to.y - ground < 4.5) {
            DebrisFx.surge(level, new Vec3(to.x, ground, to.z), 1.5F, 14.0F, 1.2F, DebrisFx.dustColour(null), 26);
        }

        if (self) {
            ClientEffects.warp();
            ScreenFx.warp(1.0F);
        }
    }

    /**
     * The hyperspace tunnel: streaks of light racing from the start to the end of the jump around a white-hot core,
     * with rings spinning along it, all collapsing into the far end within half a second.
     */
    static final class Tunnel extends WorldFx.Effect {
        final Vec3 from;
        final Vec3 to;
        final Vec3 u;
        final Vec3 w;
        final double[] ang;
        final double[] rad;
        final float[] speed;
        final float[] width;
        final boolean[] white;

        Tunnel(Vec3 from, Vec3 to, RandomSource rnd) {
            super(13);
            this.from = from;
            this.to = to;
            Vec3 dir = to.subtract(from).normalize();
            Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            this.u = dir.cross(up).normalize();
            this.w = this.u.cross(dir).normalize();
            int n = 40;
            this.ang = new double[n];
            this.rad = new double[n];
            this.speed = new float[n];
            this.width = new float[n];
            this.white = new boolean[n];

            for (int i = 0; i < n; i++) {
                this.ang[i] = rnd.nextDouble() * Math.PI * 2.0;
                this.rad[i] = 0.4 + rnd.nextDouble() * 2.4;
                this.speed[i] = 0.8F + rnd.nextFloat() * 0.8F;
                this.width[i] = 0.04F + rnd.nextFloat() * 0.1F;
                this.white[i] = rnd.nextInt(3) == 0;
            }
        }

        private Vec3 at(double t, double a, double r) {
            // the tunnel pinches shut at both ends
            double pinch = Math.sin(Mth.clamp(t, 0.0, 1.0) * Math.PI);
            return this.from.lerp(this.to, t).add(this.u.scale(Math.cos(a) * r * pinch)).add(this.w.scale(Math.sin(a) * r * pinch));
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float fade = 1.0F - p;
            // the core: blinding, gone in a few ticks
            if (p < 0.45F) {
                float a = (1.0F - p / 0.45F) * 0.9F;
                d.ribbon(new Vec3[]{this.from, this.to}, new float[]{0.9F, 0.9F}, 0.72F, 0.45F, 1.0F, new float[]{a * 0.6F, a * 0.6F});
                d.ribbon(new Vec3[]{this.from, this.to}, new float[]{0.25F, 0.25F}, 1.0F, 1.0F, 1.0F, new float[]{a, a});
            }

            double twist = (double)p * 2.4;

            for (int i = 0; i < this.ang.length; i++) {
                double head = Math.min(1.0, (double)(p * this.speed[i]) * 2.2);
                double tail = Math.max(0.0, head - 0.45 + (double)p * 0.3);
                if (!(head <= tail)) {
                    int segs = 6;
                    Vec3[] pts = new Vec3[segs + 1];
                    float[] wd = new float[segs + 1];
                    float[] al = new float[segs + 1];

                    for (int k = 0; k <= segs; k++) {
                        double t = Mth.lerp((double)k / (double)segs, tail, head);
                        pts[k] = this.at(t, this.ang[i] + twist * t, this.rad[i]);
                        wd[k] = this.width[i];
                        al[k] = fade * (float)k / (float)segs;
                    }

                    if (this.white[i]) {
                        d.ribbon(pts, wd, 1.0F, 0.95F, 1.0F, al);
                    } else {
                        d.ribbon(pts, wd, 0.7F, 0.42F, 1.0F, al);
                    }
                }
            }

            Vec3 dir = this.to.subtract(this.from).normalize();

            for (int k = 1; k <= 4; k++) {
                double t = (double)k / 5.0 + (double)p * 0.2;
                if (t < 1.0) {
                    double r = 1.6 * Math.sin(t * Math.PI) * (double)(1.0F + p);
                    d.ringIn(this.from.lerp(this.to, t), this.u, this.w, r * 0.7, r, 0.75F, 0.5F, 1.0F, fade * 0.55F, 28);
                }
            }

            d.ringIn(this.to.subtract(dir.scale(0.5)), this.u, this.w, 0.5, 2.8 * (double)(0.4F + p), 1.0F, 0.85F, 1.0F, fade * 0.8F, 36);
        }
    }

    /** A ring of bent space opening behind the Event Horizon rig and sliding back along the path. */
    static final class WakeRing extends WorldFx.Effect {
        final Vec3 c;
        final Vec3 drift;
        final Vec3 u;
        final Vec3 w;
        final float size;

        WakeRing(Vec3 c, Vec3 dir, Vec3 drift, float size, int life) {
            super(life);
            this.c = c;
            this.drift = drift;
            this.size = size;
            Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            this.u = dir.cross(up).normalize();
            this.w = this.u.cross(dir).normalize();
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float t = (float)this.age + pt;
            Vec3 at = this.c.add(this.drift.scale((double)t));
            double r = (double)this.size * (0.35 + 1.1 * (double)p);
            float a = 0.55F * (1.0F - p) * Math.min(1.0F, t * 0.6F);
            d.ringIn(at, this.u, this.w, r * 0.72, r, 0.7F, 0.4F, 1.0F, a, 24);
            d.ringIn(at, this.u, this.w, r * 0.9, r * 1.04, 1.0F, 0.9F, 1.0F, a * 0.8F, 24);
        }
    }
}
