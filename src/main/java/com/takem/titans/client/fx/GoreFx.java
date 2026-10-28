package com.takem.titans.client.fx;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The ugly part of cutting a titan apart. When a limb comes off, a few strands of sinew stay strung between it and the
 * stump - drawn as dark red beads with blood dripping off them - stretch as the limb flies, and tear.
 */
public final class GoreFx {
    private static final DustParticleOptions SINEW = new DustParticleOptions(new Vector3f(0.42F, 0.03F, 0.04F), 1.6F);
    private static final DustParticleOptions RAW = new DustParticleOptions(new Vector3f(0.7F, 0.18F, 0.2F), 1.2F);

    private GoreFx() {
    }

    public static void sinew(ClientLevel level, int gibId, int titanId, int joint, float scale) {
        WorldFx.add(new Sinew(gibId, titanId, joint, scale, level.random));
    }

    /** Where the stump is that a limb came off: the end of the bone it hung from. */
    static Vec3 stump(AbstractTitan t, int joint) {
        return switch (joint) {
            case 6 -> t.skeletonPoint(5, new Vector3f(0.0F, 14.0F, 0.0F));
            case 9 -> t.skeletonPoint(8, new Vector3f(0.0F, 14.0F, 0.0F));
            case 12 -> t.skeletonPoint(11, new Vector3f(0.0F, 10.0F, 0.0F));
            case 14 -> t.skeletonPoint(13, new Vector3f(0.0F, 10.0F, 0.0F));
            default -> t.skeletonPoint(2, new Vector3f(0.0F, -26.0F, 0.0F));
        };
    }

    static final class Sinew extends WorldFx.Effect {
        final int gib;
        final int titan;
        final int joint;
        final float scale;
        final double[] ox = new double[3];
        final double[] oz = new double[3];
        final double[] sag = new double[3];
        final int[] tear = new int[3];

        Sinew(int gib, int titan, int joint, float scale, RandomSource rnd) {
            super(26);
            this.gib = gib;
            this.titan = titan;
            this.joint = joint;
            this.scale = scale;
            for (int i = 0; i < 3; i++) {
                this.ox[i] = (rnd.nextDouble() - 0.5) * scale * 0.5;
                this.oz[i] = (rnd.nextDouble() - 0.5) * scale * 0.5;
                this.sag[i] = 0.3 + rnd.nextDouble() * 0.6;
                this.tear[i] = 8 + rnd.nextInt(14);
            }
        }

        @Override
        void tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }

            Entity g = level.getEntity(this.gib);
            Entity t = level.getEntity(this.titan);
            if (g == null || !(t instanceof AbstractTitan titan)) {
                return;
            }

            RandomSource rnd = level.random;
            Vec3 a = stump(titan, this.joint);
            Vec3 b = g.position().add(0.0, g.getBbHeight() * 0.5, 0.0);
            double len = a.distanceTo(b);
            for (int s = 0; s < 3; s++) {
                Vec3 a1 = a.add(this.ox[s] * 0.5, 0.0, this.oz[s] * 0.5);
                Vec3 b1 = b.add(this.ox[s], 0.0, this.oz[s]);
                boolean torn = this.age >= this.tear[s];
                int n = 7;
                for (int i = 0; i <= n; i++) {
                    double f = (double)i / n;
                    // once torn, each half snaps back towards where it is rooted
                    if (torn && f > 0.3 && f < 0.7) {
                        continue;
                    }

                    double droop = Math.sin(f * Math.PI) * this.sag[s] * Math.min(4.0, len * 0.25) * (torn ? 1.6 : 1.0);
                    Vec3 p = a1.lerp(b1, f).add(0.0, -droop, 0.0);
                    level.addParticle(i % 3 == 0 ? RAW : SINEW, p.x, p.y, p.z, 0.0, 0.0, 0.0);
                    if (rnd.nextInt(5) == 0) {
                        level.addParticle(ModParticles.BLOOD.get(), p.x, p.y, p.z, 0.0, -0.05, 0.0);
                    }
                }
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
        }
    }
}
