package com.takem.titans.client.fx;

import com.takem.titans.client.ClientEffects;
import com.takem.titans.network.FxPayload;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor;
import net.neoforged.neoforge.client.event.ViewportEvent.RenderFog;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class ColossalFx {
    private static final List<ColossalFx.Emitter> EMITTERS = new ArrayList<>();
    private static final List<ColossalFx.Delayed> DELAYED = new ArrayList<>();
    private static int clock;
    private static float heat;
    private static float heatO;
    private static float heatTarget;
    private static int heatHold;

    private ColossalFx() {
    }

    static void handle(ClientLevel level, FxPayload msg, Vec3 at) {
        switch (msg.kind()) {
            case 9:
                charge(level, at, msg.a());
                break;
            case 10:
                blast(level, at, msg.a());
            case 11:
            default:
                break;
            case 12:
                vaporise(level, at, msg.a());
        }
    }

    public static float skyHeat(float pt) {
        return Mth.lerp(pt, heatO, heat);
    }

    private static void charge(ClientLevel level, Vec3 at, float p) {
        RandomSource rnd = level.random;
        WorldFx.add(new WorldFx.Orb(at.add(0.0, 1.0 + (double)p * 2.0, 0.0), 0.6F + 3.4F * p, 5, 16734750, rnd));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.15, 0.0), 5.0F + 12.0F * p, 8, 0.8F, 0.0F, 16729112, 0.25F + 0.4F * p));
        int n = 8 + (int)(34.0F * p);

        for (int i = 0; i < n; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            if (d.y < 0.0) {
                d = new Vec3(d.x, -d.y * 0.4, d.z);
            }

            double dist = 8.0 + rnd.nextDouble() * 14.0;
            Vec3 from = at.add(d.scale(dist));
            Vec3 v = d.scale(-dist / 16.0);
            Particle q = ClientFx.spawn(
                level,
                rnd.nextInt(3) == 0 ? ModParticles.FIREBALL.get() : ModParticles.STEAM.get(),
                from.x,
                from.y,
                from.z,
                v.x,
                v.y,
                v.z,
                0.8F + rnd.nextFloat()
            );
            if (q != null) {
                q.setLifetime(18);
            }
        }

        heatTarget = Math.max(heatTarget, 0.3F * p * near(at, 400.0));
        heatHold = Math.max(heatHold, 60);
        if (p > 0.6F) {
            ClientEffects.shake(0.15F + (p - 0.6F), 6);
        }
    }

    private static void blast(ClientLevel level, Vec3 at, float radius) {
        RandomSource rnd = level.random;
        float R = radius;
        WorldFx.add(new WorldFx.Flash(at, radius * 7.0F, 12));
        WorldFx.add(new WorldFx.Fireball(at, radius * 1.15F, 6, 90));
        WorldFx.add(new WorldFx.Fireball(at.add(0.0, (double)radius * 0.9, 0.0), radius * 0.7F, 10, 110));
        WorldFx.add(new WorldFx.Pillar(at.add(0.0, (double)(-radius) * 0.3, 0.0), radius * 0.32F, radius * 5.0F, 120));
        WorldFx.add(new WorldFx.ShockRing(at, radius * 5.5F, 50, 3.5F, radius * 0.6F, 16769712, 0.85F));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.5, 0.0), radius * 3.2F, 110, 2.5F, 8.0F, 12103068, 0.6F));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, (double)radius * 2.2, 0.0), radius * 2.4F, 40, 2.0F, 0.0F, 16777215, 0.5F));
        WorldFx.add(new WorldFx.Starburst(at, 46, radius * 3.4F, radius * 0.13F, 26, 16771520, rnd));

        for (int i = 0; i < 14; i++) {
            Vec3 top = at.add(ClientFx.randomDir(rnd).multiply((double)(R * 2.0F), 0.0, (double)(R * 2.0F)))
                .add(0.0, (double)(R * 3.0F) + rnd.nextDouble() * (double)R * 2.0, 0.0);
            WorldFx.add(new WorldFx.Bolt(top, at.add(ClientFx.randomDir(rnd).scale((double)R * 0.4)), 0.6F, 16771496, rnd));
        }

        for (int i = 0; i < 160; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            double sp = 0.4 + rnd.nextDouble() * 1.4;
            ClientFx.spawn(
                level,
                ModParticles.FIREBALL.get(),
                at.x + d.x * (double)R * 0.5,
                at.y + Math.abs(d.y) * (double)R * 0.5,
                at.z + d.z * (double)R * 0.5,
                d.x * sp,
                Math.abs(d.y) * sp + 0.2,
                d.z * sp,
                3.0F + rnd.nextFloat() * 4.0F
            );
        }

        for (int i = 0; i < 400; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            double sp = 0.8 + rnd.nextDouble() * 2.4;
            level.addAlwaysVisibleParticle(ModParticles.EMBER.get(), at.x, at.y + 1.0, at.z, d.x * sp, Math.abs(d.y) * sp + 0.4, d.z * sp);
        }

        ClientFx.dustPlume(level, at, R * 1.8F, 3.0F);
        EMITTERS.add(new ColossalFx.Mushroom(at, R));
        EMITTERS.add(new ColossalFx.CraterSteam(at, R));
        EMITTERS.add(new ColossalFx.FireRain(at, R));
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(at);
        float k = near(at, 900.0);
        if (k > 0.0F) {
            ClientEffects.flash(16773856, Math.min(1.0F, 0.4F + k));
        }

        heatTarget = Math.max(heatTarget, Mth.clamp(1.15F - (float)dist / 800.0F, 0.0F, 1.0F));
        heatHold = 2400;
        int delay = (int)(dist / 17.0);
        float loud = (float)Mth.clamp(1.3 - dist / 900.0, 0.25, 1.0);
        later(delay, () -> {
            boom(at, SoundEvents.GENERIC_EXPLODE.value(), loud, 0.35F);
            boom(at, ModSounds.THUNDER_SPEAR_BLAST.get(), loud, 0.45F);
            boom(at, SoundEvents.LIGHTNING_BOLT_THUNDER, loud, 0.5F);
            boom(at, SoundEvents.WARDEN_SONIC_BOOM, loud, 0.4F);
            boom(at, ModSounds.COLOSSAL_ARRIVAL.get(), loud, 0.7F);
            ClientEffects.shake(Math.min(1.8F, 2.0F * loud), 70);
            ClientEffects.kick(4.0F * loud);
        });
        later(delay + 18, () -> boom(at, SoundEvents.GENERIC_EXPLODE.value(), loud * 0.8F, 0.3F));
        later(delay + 40, () -> boom(at, SoundEvents.LIGHTNING_BOLT_THUNDER, loud * 0.7F, 0.4F));
        later(delay + 70, () -> boom(at, SoundEvents.GENERIC_EXPLODE.value(), loud * 0.5F, 0.25F));
    }

    private static void vaporise(ClientLevel level, Vec3 at, float h) {
        RandomSource rnd = level.random;
        h = Math.max(3.0F, h);
        WorldFx.add(new WorldFx.Fireball(at.add(0.0, (double)h * 0.45, 0.0), h * 0.45F, 3, 30));

        for (int i = 0; (float)i < 40.0F + h * 3.0F; i++) {
            double y = rnd.nextDouble() * (double)h;
            ClientFx.spawn(
                level,
                ModParticles.BIG_STEAM.get(),
                at.x + (rnd.nextDouble() - 0.5) * (double)h * 0.3,
                at.y + y,
                at.z + (rnd.nextDouble() - 0.5) * (double)h * 0.3,
                (rnd.nextDouble() - 0.5) * 0.2,
                0.15 + rnd.nextDouble() * 0.3,
                (rnd.nextDouble() - 0.5) * 0.2,
                1.5F + rnd.nextFloat() * 2.0F
            );
        }

        for (int i = 0; i < 30; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            ClientFx.spawn(level, ModParticles.FIREBALL.get(), at.x, at.y + (double)h * 0.5, at.z, d.x * 0.5, d.y * 0.5 + 0.2, d.z * 0.5, 2.0F);
        }

        ClientFx.bloodBurst(level, at.add(0.0, (double)h * 0.6, 0.0), new Vec3(0.0, 1.0, 0.0), 6.0F);
    }

    static float near(Vec3 at, double range) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return (float)Mth.clamp(1.0 - cam.distanceTo(at) / range, 0.0, 1.0);
    }

    static void later(int ticks, Runnable r) {
        DELAYED.add(new ColossalFx.Delayed(clock + Math.max(0, ticks), r));
    }

    static void boom(Vec3 at, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 dir = at.subtract(cam);
            double d = dir.length();
            Vec3 p = d < 12.0 ? at : cam.add(dir.scale(12.0 / d));
            mc.level.playLocalSound(p.x, p.y, p.z, sound, SoundSource.BLOCKS, 4.0F * volume, pitch, false);
        }
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            EMITTERS.clear();
            DELAYED.clear();
            heatTarget = 0.0F;
            heatO = 0.0F;
            heat = 0.0F;
        } else if (!mc.isPaused()) {
            clock++;
            RandomSource rnd = level.random;

            for (int i = 0; i < EMITTERS.size(); i++) {
                ColossalFx.Emitter e = EMITTERS.get(i);
                e.tick(level, rnd);
                if (++e.age >= e.life) {
                    EMITTERS.remove(i--);
                }
            }

            for (int ix = 0; ix < DELAYED.size(); ix++) {
                ColossalFx.Delayed d = DELAYED.get(ix);
                if (clock >= d.at()) {
                    DELAYED.remove(ix--);
                    d.action().run();
                }
            }

            heatO = heat;
            if (heatHold > 0) {
                heatHold--;
            } else {
                heatTarget = Math.max(0.0F, heatTarget - 5.5555557E-4F);
            }

            heat = heat + (heatTarget - heat) * (heatTarget > heat ? 0.08F : 0.02F);
            if (heat > 0.08F && mc.player != null) {
                Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                int n = (int)(heat * 10.0F);

                for (int ixx = 0; ixx < n; ixx++) {
                    double x = cam.x + (rnd.nextDouble() - 0.5) * 40.0;
                    double z = cam.z + (rnd.nextDouble() - 0.5) * 40.0;
                    double y = cam.y + rnd.nextDouble() * 16.0;
                    level.addParticle(
                        rnd.nextInt(5) == 0 ? ModParticles.EMBER.get() : (rnd.nextBoolean() ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH),
                        x,
                        y,
                        z,
                        0.0,
                        -0.02,
                        0.0
                    );
                }
            }
        }
    }

    @SubscribeEvent
    public static void onFogColor(ComputeFogColor event) {
        float h = skyHeat((float)event.getPartialTick());
        if (!(h <= 0.002F)) {
            float k = h * 0.8F;
            event.setRed(Mth.lerp(k, event.getRed(), 1.0F));
            event.setGreen(Mth.lerp(k, event.getGreen(), 0.42F));
            event.setBlue(Mth.lerp(k, event.getBlue(), 0.16F));
        }
    }

    @SubscribeEvent
    public static void onFog(RenderFog event) {
        float h = skyHeat((float)event.getPartialTick());
        if (!(h <= 0.002F) && event.getMode() == FogMode.FOG_TERRAIN) {
            float far = event.getFarPlaneDistance();
            float smoky = Math.min(far, Math.max(170.0F, far * 0.55F));
            event.setFarPlaneDistance(Mth.lerp(h * 0.5F, far, smoky));
            event.setNearPlaneDistance(Mth.lerp(h * 0.4F, event.getNearPlaneDistance(), far * 0.1F));
            event.setCanceled(true);
        }
    }

    private static final class CraterSteam extends ColossalFx.Emitter {
        final Vec3 c;
        final float R;

        CraterSteam(Vec3 c, float R) {
            super(1200);
            this.c = c;
            this.R = R;
        }

        @Override
        void tick(ClientLevel level, RandomSource rnd) {
            int n = this.age < 300 ? 4 : (this.age % 2 == 0 ? 1 : 0);

            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * 6.2831854820251465;
                double d = Math.sqrt(rnd.nextDouble()) * (double)this.R * 0.85;
                ClientFx.spawn(
                    level,
                    rnd.nextInt(3) == 0 ? ModParticles.BIG_STEAM.get() : ModParticles.STEAM.get(),
                    this.c.x + Math.cos(a) * d,
                    this.c.y - (double)this.R * 0.3,
                    this.c.z + Math.sin(a) * d,
                    0.0,
                    0.15 + rnd.nextDouble() * 0.25,
                    0.0,
                    2.0F + rnd.nextFloat() * 2.0F
                );
            }

            if (rnd.nextInt(3) == 0) {
                double a = rnd.nextDouble() * 6.2831854820251465;
                double d = Math.sqrt(rnd.nextDouble()) * (double)this.R;
                level.addAlwaysVisibleParticle(
                    ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    this.c.x + Math.cos(a) * d,
                    this.c.y - (double)this.R * 0.2,
                    this.c.z + Math.sin(a) * d,
                    0.0,
                    0.08,
                    0.0
                );
            }
        }
    }

    private static record Delayed(int at, Runnable action) {
    }

    private abstract static class Emitter {
        int age;
        final int life;

        Emitter(int life) {
            this.life = life;
        }

        abstract void tick(ClientLevel var1, RandomSource var2);
    }

    private static final class FireRain extends ColossalFx.Emitter {
        final Vec3 c;
        final float R;

        FireRain(Vec3 c, float R) {
            super(260);
            this.c = c;
            this.R = R;
        }

        @Override
        void tick(ClientLevel level, RandomSource rnd) {
            int n = this.age < 120 ? 10 : 3;

            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * 6.2831854820251465;
                double d = Math.sqrt(rnd.nextDouble()) * (double)this.R * 3.0;
                level.addAlwaysVisibleParticle(
                    ModParticles.EMBER.get(),
                    this.c.x + Math.cos(a) * d,
                    this.c.y + (double)this.R * 1.5 + rnd.nextDouble() * (double)this.R,
                    this.c.z + Math.sin(a) * d,
                    (rnd.nextDouble() - 0.5) * 0.2,
                    -0.1,
                    (rnd.nextDouble() - 0.5) * 0.2
                );
            }
        }
    }

    private static final class Mushroom extends ColossalFx.Emitter {
        final Vec3 c;
        final float R;

        Mushroom(Vec3 c, float R) {
            super(300);
            this.c = c;
            this.R = R;
        }

        @Override
        void tick(ClientLevel level, RandomSource rnd) {
            float t = (float)this.age;
            double capH = Math.min((double)this.R * 4.4, 18.0 + (double)t * 1.5);
            double grow = Math.min(1.0, (double)t / 140.0);
            boolean hot = this.age < 70;
            int stem = this.age < 200 ? 5 : 2;
            double low = Math.min(capH * 0.6, (double)this.R * 1.7);

            for (int i = 0; i < stem; i++) {
                boolean ring = i == 0;
                if (!ring || this.age % 3 == 0) {
                    double y = ring ? rnd.nextDouble() * low : low + rnd.nextDouble() * (capH - low);
                    double a = rnd.nextDouble() * 6.2831854820251465;
                    double rad = ring
                        ? (double)this.R * (0.6 + 0.25 * rnd.nextDouble())
                        : (double)this.R * (0.18 + 0.12 * rnd.nextDouble()) * (1.0 + 0.3 * (1.0 - y / capH));
                    Particle p = ClientFx.spawn(
                        level,
                        ModParticles.MUSHROOM.get(),
                        this.c.x + Math.cos(a) * rad,
                        this.c.y + y,
                        this.c.z + Math.sin(a) * rad,
                        -Math.cos(a) * 0.03,
                        0.12 + rnd.nextDouble() * 0.2,
                        -Math.sin(a) * 0.03,
                        ring ? 0.5F + rnd.nextFloat() * 0.4F : 0.9F + rnd.nextFloat() * 0.8F
                    );
                    tint(p, hot || !ring && y < capH * 0.45, rnd);
                }
            }

            int cap = this.age < 220 ? 12 : 3;
            double major = (double)this.R * (0.45 + 0.95 * grow);
            double minor = (double)this.R * (0.35 + 0.25 * grow);

            for (int ix = 0; ix < cap; ix++) {
                double th = rnd.nextDouble() * 6.2831854820251465;
                double ph = rnd.nextDouble() * 6.2831854820251465;
                double rr = major + minor * Math.cos(ph);
                double y = capH + minor * 0.7 * Math.sin(ph);
                double out = Math.cos(ph + 1.5707963705062866) * 0.05;
                Particle p = ClientFx.spawn(
                    level,
                    ModParticles.MUSHROOM.get(),
                    this.c.x + Math.cos(th) * rr,
                    this.c.y + y,
                    this.c.z + Math.sin(th) * rr,
                    Math.cos(th) * (0.06 + out),
                    Math.sin(ph + 1.5707963705062866) * 0.06,
                    Math.sin(th) * (0.06 + out),
                    1.0F + rnd.nextFloat() * 0.9F
                );
                tint(p, hot, rnd);
            }

            if (this.age < 140) {
                double rr = (double)this.R * (1.0 + (double)this.age / 22.0);

                for (int ix = 0; ix < 8; ix++) {
                    double a = rnd.nextDouble() * 6.2831854820251465;
                    ClientFx.spawn(
                        level,
                        ModParticles.DUST_CLOUD.get(),
                        this.c.x + Math.cos(a) * rr,
                        this.c.y + rnd.nextDouble() * 3.0,
                        this.c.z + Math.sin(a) * rr,
                        Math.cos(a) * 0.5,
                        0.03,
                        Math.sin(a) * 0.5,
                        2.5F + rnd.nextFloat() * 2.0F
                    );
                }
            }
        }

        private static void tint(Particle p, boolean hot, RandomSource rnd) {
            if (p != null) {
                if (hot) {
                    float k = rnd.nextFloat() * 0.25F;
                    p.setColor(1.0F, 0.48F + k, 0.18F + k * 0.5F);
                } else {
                    float g = 0.42F + rnd.nextFloat() * 0.25F;
                    p.setColor(g * 1.05F, g * 0.95F, g * 0.88F);
                }
            }
        }
    }
}
