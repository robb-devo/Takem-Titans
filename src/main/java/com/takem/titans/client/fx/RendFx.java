package com.takem.titans.client.fx;

import com.takem.titans.client.ClientEffects;
import com.takem.titans.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Founder's Edge's ordinary swing as it looks. A golden thread flicks from the blade to where you are looking, and
 * there space splits: a jagged seam of white light with a violet rim and a gold haze, sand of the Paths pouring off it.
 * It holds a breath, flickering, then snaps shut on itself in a ring of light.
 */
public final class RendFx {
    static final int GOLD = 16765562;
    static final int VIOLET = 11560703;

    private RendFx() {
    }

    private static double camDist(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 1.0E9 : mc.player.getEyePosition().distanceTo(at);
    }

    public static void rend(ClientLevel level, Vec3 at, Vec3 edge, float half) {
        RandomSource rnd = level.random;
        Vec3 e = edge.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : edge.normalize();
        WorldFx.add(new Rift(at, e, half, rnd));
        WorldFx.add(new WorldFx.Flash(at, 4.5F, 5));
        WorldFx.add(new WorldFx.Starburst(at, 10, 4.0F, 0.18F, 7, GOLD, rnd));

        for (int i = 0; i < 46; i++) {
            double l = (rnd.nextDouble() * 2.0 - 1.0) * half;
            Vec3 p = at.add(e.scale(l));
            Vec3 v = ClientFx.randomDir(rnd).scale(0.15 + rnd.nextDouble() * 0.25);
            int kind = i % 4;
            if (kind == 0) {
                level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, v.x, v.y, v.z);
            } else if (kind == 1) {
                level.addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, v.x * 2.0, v.y * 2.0, v.z * 2.0);
            } else if (kind == 2) {
                // the sand of the Paths, drifting down out of the tear
                level.addAlwaysVisibleParticle(ModParticles.EMBER.get(), p.x, p.y, p.z, v.x * 0.3, -0.04 - rnd.nextDouble() * 0.06, v.z * 0.3);
            } else {
                level.addAlwaysVisibleParticle(ParticleTypes.WITCH, p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }

        level.addAlwaysVisibleParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0.0, 0.0, 0.0);
        double d = camDist(at);
        if (d < 40.0) {
            ClientEffects.shake(0.25F * (float)(1.0 - d / 40.0), 5);
        }
    }

    public static void thread(ClientLevel level, Vec3 from, Vec3 to) {
        RandomSource rnd = level.random;
        WorldFx.add(new Thread(from, to));
        WorldFx.add(new WorldFx.Bolt(from, to, 0.05F, GOLD, rnd));
        ClientEffects.kick(0.6F);
        int n = (int)Mth.clamp(from.distanceTo(to) / 2.0, 4.0, 28.0);
        for (int i = 0; i < n; i++) {
            Vec3 p = from.lerp(to, rnd.nextDouble());
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0.0, 0.01, 0.0);
        }
    }

    public static void snap(ClientLevel level, Vec3 at) {
        RandomSource rnd = level.random;
        WorldFx.add(new EpicFx.Implode(at, 7.0F, 6, GOLD));
        WorldFx.add(new WorldFx.Flash(at, 6.0F, 7));
        WorldFx.add(new WorldFx.ShockRing(at, 11.0F, 10, 0.5F, 0.0F, VIOLET, 0.75F));
        WorldFx.add(new WorldFx.Starburst(at, 14, 6.0F, 0.22F, 9, 16777215, rnd));
        for (int i = 0; i < 30; i++) {
            Vec3 v = ClientFx.randomDir(rnd).scale(0.5 + rnd.nextDouble() * 0.5);
            level.addAlwaysVisibleParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, v.x, v.y, v.z);
        }

        double d = camDist(at);
        if (d < 30.0) {
            ClientEffects.shake(0.35F * (float)(1.0 - d / 30.0), 6);
        }
    }

    /** The tear itself: a jagged seam that opens fast, holds flickering, then closes from the ends in. */
    static final class Rift extends WorldFx.Effect {
        final Vec3[] pts;
        final float half;

        Rift(Vec3 c, Vec3 e, float half, RandomSource rnd) {
            super(14);
            this.half = half;
            int n = 22;
            this.pts = new Vec3[n + 1];
            // the jag is in the plane facing whoever looks at it, so build two cross axes and wander in both
            Vec3 a1 = e.cross(new Vec3(0.0, 1.0, 0.0));
            a1 = a1.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : a1.normalize();
            Vec3 a2 = e.cross(a1).normalize();
            double j1 = 0.0;
            double j2 = 0.0;
            for (int i = 0; i <= n; i++) {
                double l = ((double)i / n * 2.0 - 1.0) * half;
                double taper = Math.sin(Math.PI * i / n);
                j1 = Mth.clamp(j1 + (rnd.nextDouble() - 0.5) * 0.9, -0.9, 0.9);
                j2 = Mth.clamp(j2 + (rnd.nextDouble() - 0.5) * 0.9, -0.9, 0.9);
                this.pts[i] = c.add(e.scale(l)).add(a1.scale(j1 * taper)).add(a2.scale(j2 * taper));
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float t = (float)this.age + pt;
            float open = t < 2.5F ? t / 2.5F : (t < 7.0F ? 1.0F : Mth.clamp(1.0F - (t - 7.0F) / 7.0F, 0.0F, 1.0F));
            if (open <= 0.0F) {
                return;
            }

            // a nervous flicker while it holds
            float flick = 0.85F + 0.15F * Mth.sin(t * 7.3F);
            int n = this.pts.length;
            float[] wHalo = new float[n];
            float[] wRim = new float[n];
            float[] wCore = new float[n];
            float[] aHalo = new float[n];
            float[] aRim = new float[n];
            float[] aCore = new float[n];
            for (int i = 0; i < n; i++) {
                float s = Mth.sin((float)Math.PI * i / (n - 1));
                // closing eats in from both ends
                float reach = t < 7.0F ? 1.0F : open;
                float edgeFade = Mth.clamp((s - (1.0F - reach)) * 4.0F, 0.0F, 1.0F);
                float w = (0.25F + 1.25F * s) * open * flick;
                wHalo[i] = w * 3.2F;
                wRim[i] = w * 1.4F;
                wCore[i] = w * 0.5F;
                aHalo[i] = 0.32F * edgeFade;
                aRim[i] = 0.75F * edgeFade;
                aCore[i] = 1.0F * edgeFade;
            }

            d.ribbon(this.pts, wHalo, 1.0F, 0.82F, 0.4F, aHalo);
            d.ribbon(this.pts, wRim, 0.68F, 0.42F, 1.0F, aRim);
            d.ribbon(this.pts, wCore, 1.0F, 0.98F, 0.92F, aCore);
        }
    }

    /** A thread of gold from the blade to the tear, gone in a few ticks. */
    static final class Thread extends WorldFx.Effect {
        final Vec3 from;
        final Vec3 to;

        Thread(Vec3 from, Vec3 to) {
            super(6);
            this.from = from;
            this.to = to;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float f = 1.0F - ((float)this.age + pt) / (float)this.life;
            Vec3[] pts = {this.from, this.from.lerp(this.to, 0.5), this.to};
            d.ribbon(pts, new float[]{0.05F, 0.12F, 0.2F}, 1.0F, 0.85F, 0.45F, new float[]{0.9F * f, 0.7F * f, 0.9F * f});
            d.ribbon(pts, new float[]{0.18F, 0.4F, 0.6F}, 1.0F, 0.75F, 0.3F, new float[]{0.25F * f, 0.2F * f, 0.3F * f});
        }
    }
}
