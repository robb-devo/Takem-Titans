package com.takem.titans.client.fx;

import com.takem.titans.client.ClientEffects;
import com.takem.titans.client.gui.ScreenFx;
import com.takem.titans.item.FounderEdge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Supernova as it looks. While it charges, lightning is dragged out of the air into the blade, the sky darkens to
 * violet, and where you are looking a thin pillar of light marks the spot. Let go and a beam stands up out of that spot
 * into the sky, the light of the whole district is drawn into it, and it goes off: a white star swelling out of the
 * ground, rings of shock rolling away across the city, a column of light into the clouds, lightning raining down in
 * rings that walk outwards. Built from a handful of light shapes, not from particles, so it costs next to nothing.
 */
public final class SeverFx {
    static final int VIOLET = 11560703;
    static final int GOLD = 16765562;
    static final int WHITE = 16777215;

    private SeverFx() {
    }

    private static Vec3 blade(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        return p.getEyePosition().add(look.scale(0.7)).add(side.scale(0.4)).add(0.0, -0.4, 0.0);
    }

    /**
     * One tick of the overcharge, {@code f} 0..1 of the way there; {@code full} once it is ready and only waits for you
     * to let go, {@code t} ticks of charge so far.
     */
    public static void charging(Player p, float f, boolean full, int t) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        RandomSource rnd = p.getRandom();
        Vec3 hand = blade(p);
        Vec3 body = p.position().add(0.0, 1.0, 0.0);
        float k = full ? 1.0F : f;
        // a few motes drawn in onto the blade
        for (int i = 0; i < 2 + (int)(k * 3.0F); i++) {
            Vec3 o = ClientFx.randomDir(rnd).scale(2.0 + 3.0 * k);
            level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, body.x + o.x, body.y + o.y, body.z + o.z, -o.x * 0.12, -o.y * 0.12, -o.z * 0.12);
        }

        // lightning dragged out of the air into the blade
        if (t % (full ? 3 : 5) == 0) {
            Vec3 src = body.add(ClientFx.randomDir(rnd).scale(4.0 + 5.0 * k)).add(0.0, 2.0 * k, 0.0);
            WorldFx.add(new WorldFx.Bolt(src, hand, 0.04F + 0.07F * k, rnd.nextBoolean() ? VIOLET : GOLD, rnd));
        }

        if (t % 8 == 0) {
            WorldFx.add(new EpicFx.Implode(body, 3.0F + 5.0F * k, 8, VIOLET));
        }

        EpicFx.tintSky(VIOLET, 0.12F + 0.5F * k, 8);
        if (t % 3 == 0) {
            ClientEffects.shake(0.05F + 0.25F * k, 4);
        }

        if (t % 6 == 0) {
            ScreenFx.phase(0.12F + 0.25F * k);
            level.playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.5F + 1.3F * k, false);
        }

        // where it will go off
        HitResult aim = p.pick(FounderEdge.NOVA_RANGE, 1.0F, false);
        Vec3 at = aim.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(FounderEdge.NOVA_RANGE)) : aim.getLocation();
        WorldFx.add(new SeverFx.Mark(at, k, full));
        if (full && t % 10 == 0) {
            WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.2, 0.0), 10.0F, 10, 0.4F, 0.0F, VIOLET, 0.7F));
            level.playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.4F, 1.8F, false);
        }
    }

    /** The moment it is ready. */
    public static void ready(Player p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        RandomSource rnd = p.getRandom();
        Vec3 c = p.position().add(0.0, 1.0, 0.0);
        WorldFx.add(new WorldFx.Flash(c, 4.0F, 7));
        WorldFx.add(new WorldFx.ShockRing(p.position().add(0.0, 0.1, 0.0), 14.0F, 14, 0.8F, 1.5F, VIOLET, 0.8F));
        for (int i = 0; i < 4; i++) {
            Vec3 sky = c.add((rnd.nextDouble() - 0.5) * 16.0, 18.0 + rnd.nextDouble() * 10.0, (rnd.nextDouble() - 0.5) * 16.0);
            WorldFx.add(new WorldFx.Bolt(sky, blade(p), 0.12F, i % 2 == 0 ? VIOLET : WHITE, rnd));
        }

        ClientEffects.flash(VIOLET, 0.4F);
        ClientEffects.shake(0.5F, 10);
    }

    /** Let go: the Supernova at {@code at}, going off {@code delay} ticks from now. */
    public static void nova(ClientLevel level, Vec3 at, int delay) {
        WorldFx.add(new SeverFx.Nova(at, Math.max(4, delay)));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && FounderEdge.holds(mc.player) && mc.player.position().distanceTo(at) < FounderEdge.NOVA_RANGE + 10.0) {
            ClientEffects.kick(3.0F);
            ScreenFx.warp(0.6F);
        }
    }

    private static double camDist(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 1.0E9 : mc.player.getEyePosition().distanceTo(at);
    }

    private static float r(int c) {
        return (float)(c >> 16 & 0xFF) / 255.0F;
    }

    private static float g(int c) {
        return (float)(c >> 8 & 0xFF) / 255.0F;
    }

    private static float b(int c) {
        return (float)(c & 0xFF) / 255.0F;
    }

    /** The aim while charging: a thin pillar of light where it will go off, and a ring on the ground. Lives a tick. */
    static final class Mark extends WorldFx.Effect {
        final Vec3 at;
        final float k;
        final boolean full;

        Mark(Vec3 at, float k, boolean full) {
            super(2);
            this.at = at;
            this.k = k;
            this.full = full;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float a = (0.25F + 0.45F * this.k) * (this.full ? 0.85F + 0.15F * Mth.sin((float)(System.currentTimeMillis() % 100000L) * 0.02F) : 1.0F);
            Vec3[] beam = {this.at, this.at.add(0.0, 30.0 + 50.0 * this.k, 0.0)};
            d.ribbon(beam, new float[]{0.25F + 0.4F * this.k, 0.05F}, 0.8F, 0.6F, 1.0F, new float[]{a, 0.0F});
            d.ribbon(beam, new float[]{1.2F + 1.6F * this.k, 0.2F}, 0.7F, 0.45F, 1.0F, new float[]{a * 0.3F, 0.0F});
            double rr = 2.0 + 5.0 * this.k;
            d.ring(this.at.add(0.0, 0.15, 0.0), rr - 0.25, rr, 0.75F, 0.55F, 1.0F, a, 40);
        }
    }

    /** The whole Supernova: its light gathering, the blast, the afterglow, all on one timeline. */
    static final class Nova extends WorldFx.Effect {
        final Vec3 at;
        final int delay;

        Nova(Vec3 at, int delay) {
            super(delay + 70);
            this.at = at;
            this.delay = delay;
        }

        @Override
        void tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }

            RandomSource rnd = level.random;
            int t = this.age;
            if (t == 1) {
                // the light of the whole district drawn into the spot
                WorldFx.add(new EpicFx.Implode(this.at, 46.0F, this.delay, VIOLET));
                WorldFx.add(new EpicFx.Implode(this.at, 30.0F, this.delay, GOLD));
                EpicFx.tintSky(VIOLET, 0.8F, this.delay + 10);
            }

            if (t < this.delay && t % 2 == 0) {
                Vec3 sky = this.at.add((rnd.nextDouble() - 0.5) * 70.0, 25.0 + rnd.nextDouble() * 40.0, (rnd.nextDouble() - 0.5) * 70.0);
                WorldFx.add(new WorldFx.Bolt(sky, this.at, 0.12F, rnd.nextBoolean() ? VIOLET : WHITE, rnd));
            }

            if (t == this.delay) {
                this.blast(level, rnd);
            }

            int u = t - this.delay;
            if (u > 0 && u <= 18 && u % 6 == 0) {
                // rings of lightning walking outwards
                double r = 14.0 * (u / 6);
                for (int i = 0; i < 7; i++) {
                    double a = i * Math.PI * 2.0 / 7.0 + rnd.nextDouble() * 0.5;
                    Vec3 g = this.at.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
                    Vec3 sky = g.add((rnd.nextDouble() - 0.5) * 10.0, 60.0 + rnd.nextDouble() * 30.0, (rnd.nextDouble() - 0.5) * 10.0);
                    WorldFx.add(new WorldFx.Bolt(sky, g, 0.22F, i % 2 == 0 ? WHITE : VIOLET, rnd));
                }

                WorldFx.add(new WorldFx.ShockRing(this.at.add(0.0, 0.3, 0.0), (float)r + 10.0F, 12, 1.0F, 1.0F, VIOLET, 0.5F));
            }
        }

        private void blast(ClientLevel level, RandomSource rnd) {
            Vec3 c = this.at;
            WorldFx.add(new WorldFx.Flash(c, 46.0F, 16));
            WorldFx.add(new WorldFx.ShockRing(c.add(0.0, 0.4, 0.0), 150.0F, 34, 4.0F, 6.0F, WHITE, 0.55F));
            WorldFx.add(new WorldFx.ShockRing(c.add(0.0, 0.4, 0.0), 95.0F, 26, 2.5F, 2.5F, VIOLET, 0.7F));
            WorldFx.add(new WorldFx.ShockRing(c.add(0.0, 0.4, 0.0), 55.0F, 18, 1.5F, 0.0F, GOLD, 0.8F));
            WorldFx.add(new WorldFx.SonicRing(c.add(0.0, 4.0, 0.0), new Vec3(0.0, 1.0, 0.0), 70.0F, 20, WHITE));
            WorldFx.add(new WorldFx.Starburst(c, 28, 80.0F, 1.0F, 18, WHITE, rnd));
            WorldFx.add(new WorldFx.Starburst(c, 16, 45.0F, 0.7F, 14, VIOLET, rnd));
            WorldFx.add(new WorldFx.Pillar(c, 5.0F, 320.0F, 40));
            for (int i = 0; i < 10; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double d = 6.0 + rnd.nextDouble() * 40.0;
                Vec3 g = c.add(Math.cos(a) * d, 0.0, Math.sin(a) * d);
                WorldFx.add(new WorldFx.Bolt(c.add(0.0, 90.0 + rnd.nextDouble() * 40.0, 0.0), g, 0.3F, i % 3 == 0 ? VIOLET : WHITE, rnd));
            }

            for (int i = 0; i < 3; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.FLASH, c.x, c.y + i * 6.0, c.z, 0.0, 0.0, 0.0);
            }

            EpicFx.tintSky(WHITE, 1.0F, 12);
            DebrisFx.dustSurge(level, c, 60.0F, 8.0F);
            double dist = camDist(c);
            float near = (float)Mth.clamp(1.0 - dist / 260.0, 0.0, 1.0);
            if (near > 0.0F) {
                ClientEffects.shake(2.2F * near, 50);
                ClientEffects.flash(WHITE, Math.min(1.0F, 0.4F + near));
                ScreenFx.warp(1.2F * near);
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float t = (float)this.age + pt;
            float gather = Mth.clamp(t / this.delay, 0.0F, 1.0F);
            float u = t - this.delay;
            if (u < 0.0F) {
                // a beam stands up out of the spot and thickens as the light pours in; the core glows brighter
                Vec3[] beam = {this.at, this.at.add(0.0, 260.0, 0.0)};
                float w = 0.3F + 2.2F * gather * gather;
                d.ribbon(beam, new float[]{w, w * 0.4F}, 1.0F, 1.0F, 1.0F, new float[]{0.9F, 0.0F});
                d.ribbon(beam, new float[]{w * 4.0F, w * 1.5F}, r(VIOLET), g(VIOLET), b(VIOLET), new float[]{0.35F, 0.0F});
                d.sphere(this.at, 0.6 + 4.0 * gather * gather, 1.0F, 0.95F, 1.0F, 0.85F, 0.3F, 24);
                double ring = 50.0 * (1.0 - gather) + 2.0;
                d.ring(this.at.add(0.0, 0.2, 0.0), ring - 0.8, ring, r(GOLD), g(GOLD), b(GOLD), 0.7F * gather, 64);
                d.ring(this.at.add(0.0, 0.25, 0.0), ring * 0.6 - 0.5, ring * 0.6, r(VIOLET), g(VIOLET), b(VIOLET), 0.8F * gather, 48);
                return;
            }

            // the star: swells out of the ground in a heartbeat, then burns down to an ember
            float p = Mth.clamp(u / 16.0F, 0.0F, 1.0F);
            float swell = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
            float fade = Mth.clamp(1.0F - u / 60.0F, 0.0F, 1.0F);
            double R = 6.0 + 34.0 * swell;
            d.sphere(this.at, R, 1.0F, 0.97F, 0.92F, 0.6F * (1.0F - p) * (1.0F - p) + 0.05F * fade, 0.0F, 40);
            d.sphere(this.at, R * 0.55, 1.0F, 1.0F, 1.0F, 0.85F * (1.0F - p) + 0.2F * fade, 0.25F, 32);
            d.sphere(this.at, R * 1.3, r(VIOLET), g(VIOLET), b(VIOLET), 0.25F * (1.0F - p), 0.0F, 32);
            // the column into the clouds, thick at first, then a thread
            Vec3[] col = {this.at.add(0.0, -4.0, 0.0), this.at.add(0.0, 400.0, 0.0)};
            float cw = 10.0F * fade * fade + 0.4F;
            d.ribbon(col, new float[]{cw, cw * 0.3F}, 1.0F, 1.0F, 1.0F, new float[]{0.8F * fade, 0.0F});
            d.ribbon(col, new float[]{cw * 3.0F, cw}, r(VIOLET), g(VIOLET), b(VIOLET), new float[]{0.3F * fade, 0.0F});
            // an ember left glowing in the crater
            d.sphere(this.at, 2.5 + 2.0 * fade, 1.0F, 0.8F, 0.45F, 0.6F * fade, 0.2F, 20);
        }
    }
}
