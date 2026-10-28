package com.takem.titans.client.fx;

import com.takem.titans.client.ClientEffects;
import com.takem.titans.network.FxPayload;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class EpicFx {
    public static final int PATHS_BLUE = 10479359;
    public static final int PATHS_GOLD = 16770982;
    public static final int FIRE = 16747050;
    private static float tint;
    private static float tintO;
    private static float tintTarget;
    private static int tintHold;
    private static float tr = 0.5F;
    private static float tg = 0.42F;
    private static float tb = 0.33F;

    private EpicFx() {
    }

    public static void tintSky(int rgb, float strength, int holdTicks) {
        float r = (float)(rgb >> 16 & 0xFF) / 255.0F;
        float g = (float)(rgb >> 8 & 0xFF) / 255.0F;
        float b = (float)(rgb & 0xFF) / 255.0F;
        if (tint < 0.05F || strength >= tintTarget) {
            tr = r;
            tg = g;
            tb = b;
        }

        tintTarget = Math.max(tintTarget, strength);
        tintHold = Math.max(tintHold, holdTicks);
    }

    public static float skyTint(float pt) {
        return Mth.lerp(pt, tintO, tint);
    }

    public static Vec3 applyTint(Vec3 c, float pt, float k) {
        float t = skyTint(pt) * k;
        return t <= 0.002F ? c : c.lerp(new Vec3((double)tr, (double)tg, (double)tb), (double)t);
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            tintTarget = 0.0F;
            tintO = 0.0F;
            tint = 0.0F;
        } else if (!mc.isPaused()) {
            tintO = tint;
            if (tintHold > 0) {
                tintHold--;
            } else {
                tintTarget = Math.max(0.0F, tintTarget - 0.0016666667F);
            }

            tint = tint + (tintTarget - tint) * (tintTarget > tint ? 0.05F : 0.02F);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ComputeFogColor event) {
        float t = skyTint((float)event.getPartialTick()) * 0.75F;
        if (!(t <= 0.002F)) {
            event.setRed(Mth.lerp(t, event.getRed(), tr));
            event.setGreen(Mth.lerp(t, event.getGreen(), tg));
            event.setBlue(Mth.lerp(t, event.getBlue(), tb));
        }
    }

    static void handle(ClientLevel level, FxPayload msg, Vec3 at) {
        RandomSource rnd = level.random;
        switch (msg.kind()) {
            case 15:
                stomp(level, at, msg.a(), rnd);
                break;
            case 16:
                emerge(level, at, msg.a(), msg.b(), rnd);
                break;
            case 17:
                horn(level, at, rnd);
                break;
            case 18:
                boulder(level, at, msg.a(), rnd);
                break;
            case 19:
                rift(level, at, msg.a(), (int)msg.b(), msg.c(), rnd);
                break;
            case 20:
                sonicBoom(level, at, new Vec3((double)msg.a(), (double)msg.b(), (double)msg.c()), msg.d(), rnd);
                break;
            case 21:
                waveCut(level, at, new Vec3((double)msg.a(), (double)msg.b(), (double)msg.c()), msg.d(), rnd);
                break;
            case 22:
                dustSurge(level, at, msg.a(), msg.b(), rnd);
                break;
            case 23:
                glass(level, at, msg.a(), rnd);
                break;
            case 24:
                bladeImpact(level, at, msg.a(), msg.b() > 0.5F, msg.c() > 0.5F, rnd);
                break;
            case 25:
                meteorSlam(level, at, msg.a(), msg.b(), msg.c() > 0.5F, rnd);
                break;
            default:
                ColossalFx.handle(level, msg, at);
        }
    }

    static void farBoom(Vec3 at, SoundEvent sound, double minDist, float volume, float pitch) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(at);
        if (!(d < minDist)) {
            float loud = (float)Mth.clamp(1.2 - d / 700.0, 0.15, 1.0) * volume;
            ColossalFx.later((int)(d / 17.0), () -> ColossalFx.boom(at, sound, loud, pitch));
        }
    }

    private static BlockState groundAt(ClientLevel level, Vec3 at) {
        MutableBlockPos m = BlockPos.containing(at.x, at.y - 0.5, at.z).mutable();

        for (int i = 0; i < 4; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.isAir()) {
                return s;
            }

            m.move(0, -1, 0);
        }

        return Blocks.DIRT.defaultBlockState();
    }

    private static void stomp(ClientLevel level, Vec3 at, float R, RandomSource rnd) {
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.4, 0.0), R * 2.6F, 20, 1.8F, 4.0F, 12103068, 0.5F));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.2, 0.0), R * 1.3F, 10, 1.0F, 0.0F, 16773340, 0.4F));
        ClientFx.dustPlume(level, at, R * 1.4F, 2.8F);
        BlockState ground = groundAt(level, at);
        BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, ground);

        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            double d = rnd.nextDouble() * (double)R * 0.6;
            level.addAlwaysVisibleParticle(
                chips,
                at.x + Math.cos(a) * d,
                at.y + 0.3,
                at.z + Math.sin(a) * d,
                Math.cos(a) * rnd.nextDouble() * 0.5,
                0.4 + rnd.nextDouble() * 0.9,
                Math.sin(a) * rnd.nextDouble() * 0.5
            );
        }

        for (int i = 0; i < 54; i++) {
            double a = (double)(6.2831855F * (float)i) / 54.0;
            double sp = 0.45 + rnd.nextDouble() * 0.45;
            ClientFx.spawn(
                level,
                ModParticles.DUST_CLOUD.get(),
                at.x + Math.cos(a) * 2.0,
                at.y + 0.4,
                at.z + Math.sin(a) * 2.0,
                Math.cos(a) * sp,
                0.03,
                Math.sin(a) * sp,
                2.4F + rnd.nextFloat() * 1.2F
            );
        }

        farBoom(at, ModSounds.RUMBLING_STEP.get(), 90.0, 0.9F, 0.6F);
    }

    private static void emerge(ClientLevel level, Vec3 at, float R, float p, RandomSource rnd) {
        ClientFx.dustPlume(level, at, R * (1.1F + p * 0.6F), 2.6F);
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.3, 0.0), R * 2.6F, 16, 1.4F, 3.0F, 12103068, 0.35F));
        BlockState ground = groundAt(level, at.add((double)R * 0.7, 2.0, 0.0));
        BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, ground);

        for (int i = 0; i < 50; i++) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            double d = (double)R * (0.3 + rnd.nextDouble() * 0.5);
            level.addAlwaysVisibleParticle(
                chips, at.x + Math.cos(a) * d, at.y + 0.5, at.z + Math.sin(a) * d, Math.cos(a) * 0.3, 0.6 + rnd.nextDouble() * 1.2, Math.sin(a) * 0.3
            );
        }

        for (int i = 0; i < 14; i++) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            double d = rnd.nextDouble() * (double)R * 0.4;
            ClientFx.spawn(
                level,
                ModParticles.BIG_STEAM.get(),
                at.x + Math.cos(a) * d,
                at.y + 1.0,
                at.z + Math.sin(a) * d,
                0.0,
                0.15 + rnd.nextDouble() * 0.2,
                0.0,
                2.0F + rnd.nextFloat() * 2.0F
            );
        }

        if (ColossalFx.near(at, 180.0) > 0.0F) {
            ClientEffects.shake(0.25F + 0.3F * ColossalFx.near(at, 180.0), 8);
        }
    }

    private static void horn(ClientLevel level, Vec3 at, RandomSource rnd) {
        for (int k = 0; k < 4; k++) {
            int delay = k * 9;
            ColossalFx.later(delay, () -> {
                WorldFx.add(new WorldFx.ShockRing(at.add(0.0, -0.6, 0.0), 60.0F, 34, 2.0F, 6.0F, 16769712, 0.28F));
                WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 4.0, 0.0), 45.0F, 30, 1.5F, 0.0F, 16774364, 0.2F));
            });
        }

        tintSky(9335896, 0.55F, 1500);
        ClientEffects.shake(0.2F, 30);
    }

    private static void boulder(ClientLevel level, Vec3 at, float R, RandomSource rnd) {
        WorldFx.add(new WorldFx.Fireball(at, R * 1.2F, 3, 22));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.3, 0.0), R * 4.5F, 16, 1.4F, R * 0.7F, 16767136, 0.6F));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.5, 0.0), R * 3.0F, 40, 2.0F, 4.0F, 12103068, 0.45F));
        WorldFx.add(new WorldFx.Starburst(at, 14, R * 2.2F, R * 0.12F, 12, 16747050, rnd));

        for (int i = 0; i < 40; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            double sp = 0.2 + rnd.nextDouble() * 0.5;
            ClientFx.spawn(
                level,
                ModParticles.FIREBALL.get(),
                at.x + d.x,
                at.y + Math.abs(d.y),
                at.z + d.z,
                d.x * sp,
                Math.abs(d.y) * sp + 0.1,
                d.z * sp,
                1.0F + rnd.nextFloat() * R / 3.0F
            );
        }

        for (int i = 0; i < 90; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            double sp = 0.5 + rnd.nextDouble() * 1.2;
            level.addAlwaysVisibleParticle(ModParticles.EMBER.get(), at.x, at.y + 0.5, at.z, d.x * sp, Math.abs(d.y) * sp + 0.3, d.z * sp);
        }

        BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, at));

        for (int i = 0; i < 50; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            level.addAlwaysVisibleParticle(chips, at.x, at.y + 0.5, at.z, d.x * 0.8, Math.abs(d.y) * 1.2 + 0.3, d.z * 0.8);
        }

        ClientFx.dustPlume(level, at, R * 2.2F, 2.5F);

        for (int i = 0; i < 10; i++) {
            ClientFx.spawn(
                level,
                ParticleTypes.LARGE_SMOKE,
                at.x + (rnd.nextDouble() - 0.5) * (double)R,
                at.y + rnd.nextDouble() * (double)R,
                at.z + (rnd.nextDouble() - 0.5) * (double)R,
                0.0,
                0.08,
                0.0,
                3.0F
            );
        }

        float k = ColossalFx.near(at, 70.0);
        if (k > 0.0F) {
            ClientEffects.flash(16756848, 0.25F + 0.4F * k);
            ClientEffects.shake(0.3F + 0.5F * k, 10);
        }

        farBoom(at, ModSounds.BOULDER_IMPACT.get(), 60.0, 0.8F, 0.7F);
    }

    private static void rift(ClientLevel level, Vec3 at, float R, int stage, float extra, RandomSource rnd) {
        switch (stage) {
            case 0:
                WorldFx.add(new EpicFx.RiftVortex(at, R, (int)extra));
                WorldFx.add(new WorldFx.Flash(at, R * 3.0F, 10));
                WorldFx.add(new WorldFx.ShockRing(at, R * 4.0F, 20, 1.5F, R, 10479359, 0.6F));
                tintSky(7321816, 0.7F, (int)extra + 200);
                break;
            case 1:
                WorldFx.add(new EpicFx.Implode(at, R * 3.2F, 18, 10479359));

                for (int i = 0; i < 70; i++) {
                    Vec3 d = ClientFx.randomDir(rnd);
                    double dist = (double)R * (1.5 + rnd.nextDouble() * 2.0);
                    Vec3 p = at.add(d.scale(dist));
                    Vec3 in = d.scale(-1.0);
                    Vec3 tan = in.cross(new Vec3(0.0, 1.0, 0.0));
                    Vec3 v = in.scale(dist / 22.0).add(tan.scale(0.35));
                    Particle q = ClientFx.spawn(level, ParticleTypes.END_ROD, p.x, p.y, p.z, v.x, v.y, v.z, 1.0F);
                    if (q != null) {
                        q.setLifetime(20 + rnd.nextInt(10));
                    }
                }

                if (ColossalFx.near(at, 90.0) > 0.0F) {
                    ClientEffects.shake(0.15F + 0.3F * ColossalFx.near(at, 90.0), 6);
                }
                break;
            case 2:
                WorldFx.add(new EpicFx.Implode(at, R * 2.0F, 20, 16777215));
                WorldFx.add(new EpicFx.Implode(at, R * 4.0F, 20, 16770982));
                ClientEffects.shake(0.4F * ColossalFx.near(at, 150.0), 20);
                break;
            default:
                detonate(level, at, R, rnd);
        }
    }

    private static void detonate(ClientLevel level, Vec3 at, float R, RandomSource rnd) {
        WorldFx.add(new WorldFx.Flash(at, R * 9.0F, 14));
        WorldFx.add(new EpicFx.TreeOfLight(at, R * 14.0F, 120, rnd));
        WorldFx.add(new WorldFx.ShockRing(at, R * 6.0F, 40, 3.0F, R * 0.8F, 10479359, 0.8F));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.5, 0.0), R * 4.0F, 90, 2.5F, 6.0F, 12103068, 0.6F));
        WorldFx.add(new WorldFx.Starburst(at, 40, R * 4.0F, R * 0.12F, 24, 10479359, rnd));
        WorldFx.add(new WorldFx.Starburst(at, 20, R * 2.5F, R * 0.1F, 30, 16770982, rnd));

        for (int i = 0; i < 12; i++) {
            Vec3 end = at.add(ClientFx.randomDir(rnd).scale((double)R * (1.5 + rnd.nextDouble() * 2.0)));
            WorldFx.add(new WorldFx.Bolt(at, end, 0.4F, 10479359, rnd));
        }

        for (int i = 0; i < 300; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            double sp = 0.6 + rnd.nextDouble() * 2.0;
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, d.x * sp, d.y * sp, d.z * sp);
        }

        ClientFx.dustPlume(level, at, R * 2.5F, 3.0F);
        float k = ColossalFx.near(at, 500.0);
        if (k > 0.0F) {
            ClientEffects.flash(15268095, Math.min(1.0F, 0.4F + k));
        }

        tintSky(12578815, 0.9F, 400);
        farBoom(at, ModSounds.RIFT_DETONATE.get(), 50.0, 1.0F, 0.8F);
    }

    private static void sonicBoom(ClientLevel level, Vec3 at, Vec3 dir, float speed, RandomSource rnd) {
        Vec3 d = dir.normalize();
        WorldFx.add(new WorldFx.SonicRing(at, d, 6.0F, 12, 16777215));
        WorldFx.add(new WorldFx.SonicRing(at.subtract(d.scale(2.0)), d, 9.0F, 16, 13625599));
        WorldFx.add(new WorldFx.ShockRing(at, 14.0F, 12, 0.8F, 0.0F, 16777215, 0.3F));
        Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = d.cross(up).normalize();
        Vec3 w = u.cross(d).normalize();

        for (int i = 0; i < 48; i++) {
            double a = (double)(6.2831855F * (float)i) / 48.0;
            Vec3 out = u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
            Vec3 p = at.add(out.scale(1.2)).subtract(d.scale(0.5));
            Vec3 v = out.scale(0.25 + rnd.nextDouble() * 0.15).subtract(d.scale(0.35));
            ClientFx.spawn(level, ParticleTypes.CLOUD, p.x, p.y, p.z, v.x, v.y, v.z, 1.0F);
        }

        ClientEffects.shake(0.35F, 8);
    }

    private static final BlockParticleOption WOOD_CHIPS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState());

    private static void bladeImpact(ClientLevel level, Vec3 at, float power, boolean red, boolean wood, RandomSource rnd) {
        float k = Mth.clamp(power / 150.0F, 0.3F, 1.6F);
        int color = red ? 16724016 : 16773824;
        WorldFx.add(new WorldFx.Starburst(at, 10 + (int)(k * 10.0F), 2.5F + k * 4.0F, 0.18F + k * 0.12F, 6, color, rnd));
        WorldFx.add(new WorldFx.Flash(at, 1.5F + k * 2.5F, 4));
        WorldFx.add(new WorldFx.ShockRing(at, 3.0F + k * 5.0F, 8, 0.4F, 0.0F, red ? 16736352 : 16777215, 0.35F));

        for (int i = 0; i < 12 + (int)(k * 20.0F); i++) {
            Vec3 v = ClientFx.randomDir(rnd).scale(0.35 + rnd.nextDouble() * 0.5 * (double)k);
            level.addAlwaysVisibleParticle(ModParticles.SPARK.get(), at.x, at.y, at.z, v.x, v.y, v.z);
        }

        for (int i = 0; i < 10; i++) {
            Vec3 v = ClientFx.randomDir(rnd).scale(0.15 + rnd.nextDouble() * 0.2);
            if (wood) {
                // a training dummy splinters
                level.addParticle(WOOD_CHIPS, at.x, at.y, at.z, v.x * 2.0, v.y * 2.0 + 0.1, v.z * 2.0);
                level.addParticle(WOOD_CHIPS, at.x, at.y, at.z, v.z * 2.0, v.y * 2.0 + 0.1, v.x * 2.0);
            } else {
                ClientFx.spawn(level, ModParticles.BLOOD.get(), at.x, at.y, at.z, v.x, v.y + 0.1, v.z, 1.0F);
                ClientFx.spawn(level, ModParticles.STEAM.get(), at.x, at.y, at.z, v.x * 0.5, v.y * 0.5 + 0.05, v.z * 0.5, 1.0F);
            }
        }

        LocalPlayer me = Minecraft.getInstance().player;
        if (me != null && me.position().distanceTo(at) < 12.0) {
            ClientEffects.shake(Math.min(0.6F, 0.15F + k * 0.3F), 6);
        }
    }

    private static void meteorSlam(ClientLevel level, Vec3 at, float R, float k, boolean red, RandomSource rnd) {
        int hot = red ? 16724016 : 16756832;
        WorldFx.add(new WorldFx.Flash(at.add(0.0, 1.0, 0.0), 3.0F + R * 0.5F, 6));
        WorldFx.add(new WorldFx.Starburst(at.add(0.0, 0.5, 0.0), 18 + (int)(k * 14.0F), 4.0F + R * 0.6F, 0.35F + k * 0.3F, 8, hot, rnd));
        WorldFx.add(new WorldFx.ShockRing(at, R * 2.6F, 18, 1.4F, 0.6F, 16777215, 0.55F));
        WorldFx.add(new WorldFx.ShockRing(at, R * 1.6F, 12, 1.0F, 0.0F, hot, 0.5F));
        WorldFx.add(new WorldFx.ShockRing(at, R * 3.8F, 28, 2.2F, 1.2F, 14207144, 0.25F));
        WorldFx.add(new WorldFx.SonicRing(at.add(0.0, 2.0, 0.0), new Vec3(0.0, 1.0, 0.0), R * 0.9F, 10, 16777215));
        if (k > 0.6F) {
            WorldFx.add(new WorldFx.Pillar(at, 1.5F + k * 1.5F, 25.0F + R * 2.0F, 14));
        }

        BlockPos below = BlockPos.containing(at).below();
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) {
            ground = Blocks.DIRT.defaultBlockState();
        }

        BlockParticleOption blockFx = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        int n = 60 + (int)(k * 120.0F);

        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            double sp = 0.4 + rnd.nextDouble() * (0.6 + (double)k);
            level.addAlwaysVisibleParticle(blockFx, at.x, at.y + 0.3, at.z, Math.cos(a) * sp, 0.3 + rnd.nextDouble() * (0.6 + (double)k), Math.sin(a) * sp);
        }

        for (int i = 0; i < 24 + (int)(k * 40.0F); i++) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            double d = rnd.nextDouble() * (double)R * 0.5;
            ClientFx.spawn(
                level,
                ModParticles.DUST_CLOUD.get(),
                at.x + Math.cos(a) * d,
                at.y + 0.5,
                at.z + Math.sin(a) * d,
                Math.cos(a) * (0.3 + (double)k * 0.4),
                0.04 + rnd.nextDouble() * 0.08,
                Math.sin(a) * (0.3 + (double)k * 0.4),
                2.0F + k
            );
        }

        for (int i = 0; i < 20 + (int)(k * 30.0F); i++) {
            Vec3 v = ClientFx.randomDir(rnd).scale(0.5 + rnd.nextDouble() * (double)k);
            level.addAlwaysVisibleParticle(red ? ModParticles.EMBER.get() : ModParticles.SPARK.get(), at.x, at.y + 0.5, at.z, v.x, Math.abs(v.y) + 0.2, v.z);
        }

        if (k > 0.5F) {
            tintSky(red ? 16728112 : 16767136, 0.25F * k, 10);
        }
    }

    static void megaMeteor(ClientLevel level, Vec3 at, float R, float e, boolean extinction, boolean red, RandomSource rnd) {
        float k = Mth.clamp(e / 80.0F, 0.35F, 6.0F);
        meteorSlam(level, at, R, Math.min(k, 3.0F), red, rnd);
        WorldFx.add(new WorldFx.Fireball(at.add(0.0, (double)R * 0.4, 0.0), R * 0.85F, 8, (int)Math.min(260.0F, 120.0F + e * 0.2F)));
        WorldFx.add(new WorldFx.Pillar(at, 2.0F + k * 2.0F, 40.0F + R * 3.5F, 22 + (int)(k * 4.0F)));
        if (extinction || k > 1.2F) {
            ColossalFx.handle(level, new FxPayload(10, at.x, at.y, at.z, Math.min(R * 0.55F, 220.0F), 0.0F, 0.0F, 0.0F), at);
        }

        if (k > 2.5F) {
            WorldFx.add(new WorldFx.Fireball(at.add(0.0, (double)R * 0.9, 0.0), R * 0.6F, 10, 200));
            WorldFx.add(new WorldFx.ShockRing(at.add(0.0, (double)R * 0.5, 0.0), R * 4.5F, 40, 3.0F, 0.0F, 16756832, 0.3F));

            for (int i = 0; i < 80 + (int)(k * 30.0F); i++) {
                double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
                double sp = 0.8 + rnd.nextDouble() * (double)k * 0.5;
                level.addAlwaysVisibleParticle(
                    ModParticles.FIREBALL.get(), at.x, at.y + 1.0, at.z, Math.cos(a) * sp, 0.8 + rnd.nextDouble() * (double)k * 0.4, Math.sin(a) * sp
                );
            }
        }

        tintSky(extinction ? 16724016 : 16756832, Math.min(0.9F, 0.35F * k), 18 + (int)(k * 6.0F));
        ClientEffects.shake(Math.min(2.2F, 0.5F + k * 0.35F), 30 + (int)(k * 8.0F));
    }

    static void novaFireball(ClientLevel level, Vec3 at, float R, boolean supernova, RandomSource rnd) {
        WorldFx.add(new WorldFx.Flash(at, R * 4.0F, 10));
        WorldFx.add(new WorldFx.Fireball(at.add(0.0, 1.0, 0.0), R * (supernova ? 1.2F : 0.65F), 6, supernova ? 90 : 40));
        WorldFx.add(new WorldFx.ShockRing(at, R * 2.8F, 16, 1.6F, 0.4F, supernova ? 16747050 : 16773340, 0.65F));

        for (int i = 0; i < 40 + (int)(R * (supernova ? 6.0F : 3.0F)); i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            level.addAlwaysVisibleParticle(ModParticles.FIREBALL.get(), at.x, at.y + 0.5, at.z, d.x * 0.5, Math.abs(d.y) * 0.4 + 0.15, d.z * 0.5);
        }

        if (supernova) {
            tintSky(16747050, 0.45F, 24);
            ClientEffects.shake(0.9F, 20);
        }
    }

    static void thermoCloud(ClientLevel level, Vec3 at, float R, RandomSource rnd) {
        float h = Math.max(4.0F, R);
        WorldFx.add(new WorldFx.Fireball(at.add(0.0, (double)h * 0.35, 0.0), h * 0.35F, 4, 35));

        for (int i = 0; i < 50 + (int)(R * 4.0F); i++) {
            ClientFx.spawn(
                level,
                ModParticles.BIG_STEAM.get(),
                at.x + (rnd.nextDouble() - 0.5) * (double)R,
                at.y + rnd.nextDouble() * (double)h,
                at.z + (rnd.nextDouble() - 0.5) * (double)R,
                (rnd.nextDouble() - 0.5) * 0.15,
                0.12 + rnd.nextDouble() * 0.25,
                (rnd.nextDouble() - 0.5) * 0.15,
                2.0F + rnd.nextFloat()
            );
        }
    }

    static void thermoBlast(ClientLevel level, Vec3 at, float R, RandomSource rnd) {
        WorldFx.add(new WorldFx.ShockRing(at, R * 3.2F, 20, 2.0F, 0.5F, 16773340, 0.7F));
        ClientFx.dustPlume(level, at, R * 1.2F, 2.2F);
        ColossalFx.boom(at, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.55F);
        ClientEffects.shake(Math.min(1.0F, 0.25F + R * 0.04F), 14);
    }

    private static void waveCut(ClientLevel level, Vec3 at, Vec3 dir, float R, RandomSource rnd) {
        for (int i = 0; i < 16; i++) {
            Vec3 v = dir.scale(0.4).add(ClientFx.randomDir(rnd).scale(0.5));
            level.addAlwaysVisibleParticle(ModParticles.SPARK.get(), at.x, at.y, at.z, v.x, v.y, v.z);
        }

        ClientFx.spawn(level, ModParticles.DUST_CLOUD.get(), at.x, at.y, at.z, dir.x * 0.1, 0.02, dir.z * 0.1, 1.5F);
    }

    private static void dustSurge(ClientLevel level, Vec3 at, float R, float h, RandomSource rnd) {
        // a rolling wall of dust along the ground, not a glowing ring
        DebrisFx.dustSurge(level, at, R, h);
    }

    private static void glass(ClientLevel level, Vec3 at, float R, RandomSource rnd) {
        DebrisFx.glass(level, at, R);
    }

    static final class Implode extends WorldFx.Effect {
        final Vec3 c;
        final float maxRadius;
        final float r;
        final float g;
        final float b;

        Implode(Vec3 c, float maxRadius, int life, int color) {
            super(life);
            this.c = c;
            this.maxRadius = maxRadius;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F;
            this.b = (float)(color & 0xFF) / 255.0F;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float e = p * p;
            double rad = (double)this.maxRadius * (1.0 - (double)e);
            float a = 0.6F * Mth.sin(p * 3.1415927F);
            d.ring(this.c, Math.max(0.0, rad - 1.5), rad + 1.5, this.r, this.g, this.b, a, 48);
            d.ringIn(this.c, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), Math.max(0.0, rad - 1.0), rad + 1.0, this.r, this.g, this.b, a * 0.6F, 40);
            d.ringIn(this.c, new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 1.0, 0.0), Math.max(0.0, rad - 1.0), rad + 1.0, this.r, this.g, this.b, a * 0.6F, 40);
        }
    }

    static final class RiftVortex extends WorldFx.Effect {
        final Vec3 c;
        final float radius;

        RiftVortex(Vec3 c, float radius, int life) {
            super(life);
            this.c = c;
            this.radius = radius;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float t = (float)this.age + pt;
            float p = this.progress(pt);
            float open = Mth.clamp(t / 20.0F, 0.0F, 1.0F);
            float closing = p > 0.85F ? 1.0F - (p - 0.85F) / 0.15F * 0.85F : 1.0F;
            float flick = 0.85F + 0.15F * Mth.sin(t * 1.7F);
            double core = (double)this.radius * 0.22 * (double)open * (double)closing * (1.0 + 0.08 * (double)Mth.sin(t * 0.9F));
            d.sphere(this.c, core * 2.4, 0.62F, 0.9F, 1.0F, 0.22F * flick, 0.0F, 24);
            d.sphere(this.c, core * 1.4, 0.8F, 0.95F, 1.0F, 0.5F * flick, 0.1F, 24);
            d.sphere(this.c, core * 0.7, 1.0F, 1.0F, 1.0F, 0.95F, 0.5F, 20);
            float spin = t * (0.08F + 0.25F * p * p);
            Vec3 tiltU = new Vec3(1.0, 0.18, 0.0).normalize();
            Vec3 tiltW = new Vec3(0.0, -0.1, 1.0).normalize();
            int arms = 5;

            for (int k = 0; k < arms; k++) {
                int n = 22;
                Vec3[] pts = new Vec3[n];
                float[] w = new float[n];
                float[] a = new float[n];

                for (int i = 0; i < n; i++) {
                    float f = (float)i / (float)(n - 1);
                    double rr = (double)this.radius * (0.25 + 2.2 * (double)f) * (double)open * (double)closing;
                    double ang = (double)(spin + (float)k * 6.2831855F / (float)arms) + (double)f * 3.2;
                    pts[i] = this.c.add(tiltU.scale(Math.cos(ang) * rr)).add(tiltW.scale(Math.sin(ang) * rr));
                    w[i] = (float)((double)this.radius * 0.35 * (1.0 - (double)f * 0.6));
                    a[i] = 0.45F * (1.0F - f) * flick;
                }

                d.ribbon(pts, w, k % 2 == 0 ? 0.62F : 1.0F, k % 2 == 0 ? 0.9F : 0.9F, k % 2 == 0 ? 1.0F : 0.62F, a);
            }

            d.ringIn(
                this.c,
                tiltU,
                tiltW,
                (double)this.radius * 0.5 * (double)open,
                (double)this.radius * 2.6 * (double)open * (double)closing,
                0.62F,
                0.9F,
                1.0F,
                0.25F * flick,
                64
            );
            double beamH = 260.0 * (double)open;
            d.wall(this.c.add(0.0, -2.0, 0.0), core * 0.6, this.c.y - 2.0, this.c.y + beamH, 0.8F, 0.95F, 1.0F, 0.55F * flick, 20);
            d.wall(this.c.add(0.0, -2.0, 0.0), core * 1.4, this.c.y - 2.0, this.c.y + beamH * 0.7, 0.62F, 0.9F, 1.0F, 0.2F * flick, 20);
        }
    }

    static final class TreeOfLight extends WorldFx.Effect {
        final List<EpicFx.TreeOfLight.Branch> branches = new ArrayList<>();

        TreeOfLight(Vec3 base, float height, int life, RandomSource rnd) {
            super(life);
            this.grow(base, new Vec3(0.0, 1.0, 0.0), (double)height * 0.32, height * 0.012F, 0, rnd);
        }

        private void grow(Vec3 from, Vec3 dir, double len, float width, int depth, RandomSource rnd) {
            if (depth <= 5 && this.branches.size() <= 160 && !(width < 0.15F)) {
                int n = 8;
                Vec3[] pts = new Vec3[n];
                Vec3 p = from;
                Vec3 d = dir;

                for (int i = 0; i < n; i++) {
                    pts[i] = p;
                    d = d.add((rnd.nextDouble() - 0.5) * 0.35, (rnd.nextDouble() - 0.3) * 0.15, (rnd.nextDouble() - 0.5) * 0.35).normalize();
                    p = p.add(d.scale(len / (double)(n - 1)));
                }

                this.branches.add(new EpicFx.TreeOfLight.Branch(pts, width));
                int kids = depth == 0 ? 4 : 2 + rnd.nextInt(2);

                for (int k = 0; k < kids; k++) {
                    int at = n / 2 + rnd.nextInt(n / 2);
                    double yaw = rnd.nextDouble() * 6.2831854820251465;
                    double spread = 0.5 + rnd.nextDouble() * 0.6;
                    Vec3 nd = d.add(Math.cos(yaw) * spread, 0.1 + rnd.nextDouble() * 0.4, Math.sin(yaw) * spread).normalize();
                    this.grow(pts[at], nd, len * (0.55 + rnd.nextDouble() * 0.2), width * 0.6F, depth + 1, rnd);
                }
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float t = (float)this.age + pt;
            float p = this.progress(pt);
            float reach = Mth.clamp(t / 14.0F, 0.0F, 1.0F);
            float fade = p < 0.6F ? 1.0F : 1.0F - (p - 0.6F) / 0.4F;
            float flick = 0.9F + 0.1F * Mth.sin(t * 2.3F);

            for (EpicFx.TreeOfLight.Branch b : this.branches) {
                int n = b.pts.length;
                int shown = Math.max(2, Math.round((float)n * reach));
                Vec3[] pts = Arrays.copyOf(b.pts, shown);
                float[] w = new float[shown];
                float[] a = new float[shown];
                float[] wc = new float[shown];
                float[] ac = new float[shown];

                for (int i = 0; i < shown; i++) {
                    float f = (float)i / (float)(n - 1);
                    w[i] = b.width * 3.5F * (1.0F - f * 0.5F);
                    a[i] = 0.28F * fade * flick;
                    wc[i] = b.width * (1.0F - f * 0.5F);
                    ac[i] = 0.9F * fade * flick;
                }

                d.ribbon(pts, w, 0.62F, 0.9F, 1.0F, a);
                d.ribbon(pts, wc, 1.0F, 1.0F, 1.0F, ac);
            }
        }

        private static record Branch(Vec3[] pts, float width) {
        }
    }
}
