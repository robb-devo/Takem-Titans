package com.takem.titans.client.fx;

import com.takem.titans.client.ClientEffects;
import com.takem.titans.client.gui.ScreenFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The shifter bosses: the Female Titan's scream and crystal, the War Hammer's spikes and forged hammer, the Cart
 * Titan's cannon. Everything here is a handful of world effects and a burst of vanilla particles, nothing that lingers.
 */
public final class BossFx {
    static final int CRYSTAL = 13691903;
    static final int ICE = 10477055;
    static final int GOLD = 16767392;

    private BossFx() {
    }

    private static double distToCamera(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 1.0E9 : mc.player.position().distanceTo(at);
    }

    /** The Female Titan screams: rings of sound roll out over the district and the sky turns hot for a moment. */
    public static void scream(ClientLevel level, Vec3 at, float power) {
        RandomSource rnd = level.random;
        for (int i = 0; i < 3; i++) {
            WorldFx.add(new WorldFx.ShockRing(at.add(0.0, -6.0 + i * 3.0, 0.0), 60.0F + 25.0F * i * power, 26 + i * 6, 2.4F, 5.0F, 16773087, 0.32F - i * 0.07F));
        }

        WorldFx.add(new WorldFx.Flash(at, 18.0F * power, 10));
        EpicFx.tintSky(16758608, 0.5F * power, 40);
        DebrisFx.dustSurge(level, at.add(0.0, -12.0, 0.0), 40.0F * power, 4.0F);
        double d = distToCamera(at);
        if (d < 160.0) {
            float k = (float)(1.0 - d / 160.0);
            ClientEffects.shake(1.2F * k, 40);
            ScreenFx.warp(0.6F * k);
        }

        for (int i = 0; i < 30; i++) {
            Vec3 v = ClientFx.randomDir(rnd).scale(1.2);
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, v.x, Math.abs(v.y) * 0.3, v.z);
        }
    }

    /** A crystal spike rips out of the ground: a pale flash at its root and splinters off the tip. */
    public static void crystal(ClientLevel level, Vec3 at, float height) {
        RandomSource rnd = level.random;
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.2, 0.0), 4.0F + height * 0.6F, 10, 0.8F, 1.5F, CRYSTAL, 0.55F));
        WorldFx.add(new WorldFx.Starburst(at.add(0.0, height, 0.0), 8, 3.0F + height * 0.2F, 0.18F, 8, ICE, rnd));
        WorldFx.add(new WorldFx.Flash(at.add(0.0, height * 0.5, 0.0), 2.0F + height * 0.4F, 5));
        DebrisFx.dustSurge(level, at, 3.0F + height * 0.5F, 1.4F);

        for (int i = 0; i < 14; i++) {
            level.addAlwaysVisibleParticle(
                ParticleTypes.END_ROD, at.x + (rnd.nextDouble() - 0.5) * 2.0, at.y + rnd.nextDouble() * height, at.z + (rnd.nextDouble() - 0.5) * 2.0,
                (rnd.nextDouble() - 0.5) * 0.2, 0.15 + rnd.nextDouble() * 0.3, (rnd.nextDouble() - 0.5) * 0.2
            );
        }
    }

    /** Crystal floods over a titan's nape (or splits off it when a = 0). */
    public static void harden(ClientLevel level, Vec3 at, boolean forming) {
        RandomSource rnd = level.random;
        if (forming) {
            WorldFx.add(new EpicFx.Implode(at, 7.0F, 10, CRYSTAL));
            WorldFx.add(new WorldFx.Flash(at, 5.0F, 6));
        } else {
            WorldFx.add(new WorldFx.Starburst(at, 18, 6.0F, 0.3F, 12, CRYSTAL, rnd));
            WorldFx.add(new WorldFx.Flash(at, 9.0F, 8));
            WorldFx.add(new WorldFx.ShockRing(at, 14.0F, 12, 0.6F, 0.0F, ICE, 0.6F));
        }

        for (int i = 0; i < 24; i++) {
            Vec3 v = ClientFx.randomDir(rnd).scale(forming ? -0.25 : 0.6);
            Vec3 p = forming ? at.add(v.scale(-12.0)) : at;
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /** The War Hammer forges its hammer out of the air above you: light pours together into a falling crystal. */
    public static void forge(ClientLevel level, Vec3 at) {
        RandomSource rnd = level.random;
        WorldFx.add(new EpicFx.Implode(at, 14.0F, 18, CRYSTAL));
        WorldFx.add(new WorldFx.Pillar(at.add(0.0, -40.0, 0.0), 2.5F, 80.0F, 22));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, -40.0, 0.0), 8.0F, 30, 0.5F, 0.0F, CRYSTAL, 0.9F));

        for (int i = 0; i < 40; i++) {
            Vec3 v = ClientFx.randomDir(rnd);
            Vec3 p = at.add(v.scale(10.0));
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, -v.x * 0.5, -v.y * 0.5, -v.z * 0.5);
        }
    }

    /** One of the Cart Titan's guns goes off. */
    public static void muzzle(ClientLevel level, Vec3 at, Vec3 dir) {
        RandomSource rnd = level.random;
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : dir.normalize();
        WorldFx.add(new WorldFx.Flash(at, 4.0F, 4));
        WorldFx.add(new WorldFx.SonicRing(at, d, 3.5F, 7, GOLD));

        for (int i = 0; i < 12; i++) {
            Vec3 v = d.scale(0.4 + rnd.nextDouble() * 0.5).add(ClientFx.randomDir(rnd).scale(0.15));
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, v.x, v.y, v.z);
            if (i < 5) {
                level.addAlwaysVisibleParticle(ParticleTypes.FLAME, at.x, at.y, at.z, v.x * 1.5, v.y * 1.5, v.z * 1.5);
            }
        }
    }
}
