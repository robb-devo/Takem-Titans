package com.takem.titans.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takem.titans.client.ClientEffects;
import com.takem.titans.client.ShiftClient;
import com.takem.titans.entity.titan.ShifterTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.item.OdmPistolsItem;
import com.takem.titans.network.FxPayload;
import com.takem.titans.registry.ModParticles;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Effects for the Titan Shift and the ODM pistols (Fx kinds 60-68): the transformation's charge, lightning, blast and
 * fissures; blows, roars and quakes; the cut-free burst and the husk boiling away; and the pistols' muzzle fire, tracers,
 * sparks, blood and scorched bullet holes.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class CombatFx {
    private static final List<CombatFx.Decal> DECALS = new ArrayList<>();

    private CombatFx() {
    }

    public static void handle(ClientLevel level, FxPayload msg, Vec3 at) {
        RandomSource rnd = level.random;
        switch (msg.kind()) {
            case 60 -> {
                ShiftClient.ignite((int)msg.a());
                WorldFx.add(new CombatFx.Charge((int)msg.a(), (int)msg.b()));
            }
            case 61 -> strike(level, at, (int)msg.a(), rnd);
            case 62 -> ShiftClient.action((int)msg.a(), (int)msg.b(), (int)msg.c());
            case 63 -> blow(level, at, msg.a(), msg.b(), (int)msg.c(), rnd);
            case 64 -> roar(level, at, msg.b(), msg.c(), rnd);
            case 65 -> quake(level, at, msg.a(), rnd);
            case 66 -> burst(level, at, (int)msg.a(), msg.b(), rnd);
            case 68 -> shot(level, at, msg.a(), msg.b(), (int)msg.c(), (int)msg.d(), rnd);
            default -> {
            }
        }
    }

    /** How close a point is to the camera, 1 on top of it falling to 0 at the range. */
    private static float near(Vec3 p, double range) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return (float)Mth.clamp(1.0 - cam.distanceTo(p) / range, 0.0, 1.0);
    }

    /** The top of the ground under a point (within a few blocks), for things that lie on it. */
    static double ground(ClientLevel level, double x, double y, double z) {
        MutableBlockPos m = new MutableBlockPos(Mth.floor(x), Mth.floor(y) + 3, Mth.floor(z));
        for (int i = 0; i < 12; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.isAir() && s.isSolid()) {
                return (double)m.getY() + 1.0;
            }
            m.move(0, -1, 0);
        }
        return y;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The Titan Shift
    // ---------------------------------------------------------------------------------------------------------------

    private static void strike(ClientLevel level, Vec3 at, int id, RandomSource rnd) {
        ShiftClient.struck(id);
        Vec3 core = at.add(0.0, 2.0, 0.0);
        WorldFx.add(new CombatFx.Thunder(at, rnd));
        WorldFx.add(new WorldFx.Flash(core, 18.0F, 10));
        WorldFx.add(new WorldFx.Fireball(at.add(0.0, 4.0, 0.0), 6.0F, 4, 14));
        WorldFx.add(new WorldFx.Pillar(at, 3.2F, 120.0F, 18));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.3, 0.0), 40.0F, 24, 2.6F, 1.6F, 0xFFE6B0, 0.95F));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.2, 0.0), 70.0F, 42, 4.0F, 2.4F, 0xFFFFFF, 0.55F));
        WorldFx.add(new WorldFx.Starburst(core.add(0.0, 2.0, 0.0), 26, 36.0F, 1.5F, 14, 0xFFF0C8, rnd));
        WorldFx.add(new CombatFx.Cracks(level, at, 16, 26.0F, 90, rnd));
        // a ring of steam blasted outward, fast enough to clear the middle so the body can be seen forming in it
        for (int i = 0; i < 40; i++) {
            Vec3 dir = new Vec3(rnd.nextGaussian(), rnd.nextDouble() * 0.35, rnd.nextGaussian()).normalize();
            double sp = 1.0 + rnd.nextDouble() * 1.6;
            ClientFx.spawn(level, ModParticles.BIG_STEAM.get(), core.x, core.y, core.z, dir.x * sp, dir.y * sp + 0.05, dir.z * sp, 2.0F + rnd.nextFloat() * 2.0F);
        }
        for (int i = 0; i < 70; i++) {
            level.addAlwaysVisibleParticle(
                ModParticles.EMBER.get(), core.x, core.y, core.z, rnd.nextGaussian() * 0.6, 0.3 + rnd.nextDouble() * 1.2, rnd.nextGaussian() * 0.6
            );
        }
        float k = near(at, 140.0);
        if (k > 0.0F) {
            ClientEffects.flash(0xFFF2C4, 0.55F * k);
            PostFx.burst(at, 1.6F * k, 1.3F * k);
        }
    }

    /** The build-up: sparks crawl over the body and small arcs snap between them, faster and brighter as it comes. */
    static final class Charge extends WorldFx.Effect {
        final int id;

        Charge(int id, int life) {
            super(life);
            this.id = id;
        }

        @Override
        void tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && level.getEntity(this.id) instanceof Player p) {
                float k = (float)this.age / (float)this.life;
                RandomSource r = level.random;
                Vec3 c = p.position().add(0.0, (double)p.getBbHeight() * 0.5, 0.0);
                for (int i = 0; i < 2 + (int)(k * 8.0F); i++) {
                    level.addParticle(
                        ModParticles.SPARK.get(), c.x + (r.nextDouble() - 0.5) * 1.6, c.y + (r.nextDouble() - 0.5) * 2.2, c.z + (r.nextDouble() - 0.5) * 1.6,
                        (r.nextDouble() - 0.5) * 0.2, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.2
                    );
                }
                if (r.nextFloat() < 0.35F + k * 0.6F) {
                    Vec3 a = c.add((r.nextDouble() - 0.5) * 2.0, (r.nextDouble() - 0.5) * 2.4, (r.nextDouble() - 0.5) * 2.0);
                    Vec3 b = c.add((r.nextDouble() - 0.5) * 2.4, (r.nextDouble() - 0.5) * 2.8, (r.nextDouble() - 0.5) * 2.4);
                    WorldFx.add(new WorldFx.Bolt(a, b, 0.08F + k * 0.1F, 0xFFE58A, r));
                }
                if (this.age % 2 == 0) {
                    ClientFx.spawn(level, ModParticles.STEAM.get(), c.x + (r.nextDouble() - 0.5), c.y - 0.6, c.z + (r.nextDouble() - 0.5), 0.0, 0.08, 0.0, 0.6F + k);
                }
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && level.getEntity(this.id) instanceof Player p) {
                float k = this.progress(pt);
                Vec3 c = p.getPosition(pt).add(0.0, (double)p.getBbHeight() * 0.5, 0.0);
                d.sphere(c, 0.9 + 1.6 * (double)k, 1.0F, 0.86F, 0.45F, 0.12F + 0.35F * k * k, 0.0F, 20);
            }
        }
    }

    /** The bolt: several strokes down the same channel in the first few ticks, each throwing forks. */
    static final class Thunder extends WorldFx.Effect {
        final Vec3 at;
        final Vec3 top;
        final RandomSource rnd;

        Thunder(Vec3 at, RandomSource rnd) {
            super(10);
            this.at = at;
            this.rnd = rnd;
            this.top = at.add((rnd.nextDouble() - 0.5) * 16.0, 110.0, (rnd.nextDouble() - 0.5) * 16.0);
        }

        @Override
        void tick() {
            if (this.age < 7 && (this.age < 2 || this.rnd.nextFloat() < 0.6F)) {
                Vec3 hit = this.at.add(0.0, 1.0 + this.rnd.nextDouble() * 2.0, 0.0);
                WorldFx.add(new WorldFx.Bolt(this.top, hit, 2.4F + this.rnd.nextFloat() * 1.4F, 0xFFF1B0, this.rnd));
                for (int i = 0; i < 3; i++) {
                    Vec3 from = this.top.lerp(hit, 0.3 + this.rnd.nextDouble() * 0.5);
                    Vec3 to = from.add((this.rnd.nextDouble() - 0.5) * 30.0, -10.0 - this.rnd.nextDouble() * 20.0, (this.rnd.nextDouble() - 0.5) * 30.0);
                    WorldFx.add(new WorldFx.Bolt(from, to, 0.9F, 0xFFE07A, this.rnd));
                }
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
        }
    }

    /** Glowing fissures racing out across the ground from a blast, cooling as they go. */
    static final class Cracks extends WorldFx.Effect {
        final Vec3[][] lines;
        final float[] widths;

        Cracks(ClientLevel level, Vec3 c, int count, float length, int life, RandomSource rnd) {
            super(life);
            this.lines = new Vec3[count][];
            this.widths = new float[count];
            for (int i = 0; i < count; i++) {
                double ang = ((double)i + rnd.nextDouble() * 0.6) / (double)count * Math.PI * 2.0;
                double len = (double)length * (0.55 + rnd.nextDouble() * 0.45);
                Vec3[] pts = new Vec3[8];
                double x = c.x;
                double z = c.z;
                for (int j = 0; j < pts.length; j++) {
                    if (j > 0) {
                        ang += (rnd.nextDouble() - 0.5) * 0.5;
                        x += Math.cos(ang) * len / (double)(pts.length - 1);
                        z += Math.sin(ang) * len / (double)(pts.length - 1);
                    }
                    pts[j] = new Vec3(x, ground(level, x, c.y, z) + 0.06, z);
                }
                this.lines[i] = pts;
                this.widths[i] = 0.35F + rnd.nextFloat() * 0.35F;
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float grow = Math.min(1.0F, p * 6.0F);
            float heat = (1.0F - p) * (1.0F - p);
            for (int i = 0; i < this.lines.length; i++) {
                Vec3[] pts = this.lines[i];
                int n = Math.max(2, (int)((float)pts.length * grow));
                for (int j = 0; j + 1 < n; j++) {
                    float f = (float)j / (float)pts.length;
                    flat(d, pts[j], pts[j + 1], this.widths[i] * (1.0F - f * 0.7F), 1.0F, 0.62F + 0.3F * heat, 0.25F, heat * (1.0F - f * 0.6F));
                }
            }
        }
    }

    /** A flat strip lying on the ground between two points. */
    static void flat(WorldFx.Draw d, Vec3 a, Vec3 b, float w, float r, float g, float bl, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        side = side.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        side = side.scale((double)w * 0.5);
        d.v(a.add(side), r, g, bl, alpha);
        d.v(a.subtract(side), r, g, bl, alpha);
        d.v(b.subtract(side), r, g, bl, alpha);
        d.v(b.add(side), r, g, bl, alpha);
    }

    /** A shifted body venting: sheets of steam while it's still forming, then wisps off the shoulders. */
    public static void bodySteam(ClientLevel level, ShifterTitan b) {
        RandomSource r = level.random;
        double h = (double)b.getTitanHeight();
        double w = h * 0.22;
        int n = b.forming > 0.0F ? 1 + (int)(b.forming * 3.0F) : (r.nextInt(3) == 0 ? 1 : 0);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            double y = b.forming > 0.0F ? r.nextDouble() * h : h * (0.6 + r.nextDouble() * 0.3);
            double rad = w * (0.6 + r.nextDouble() * 0.5);
            ClientFx.spawn(
                level, ModParticles.BIG_STEAM.get(), b.getX() + Math.cos(ang) * rad, b.getY() + y, b.getZ() + Math.sin(ang) * rad, Math.cos(ang) * 0.12,
                0.1 + r.nextDouble() * 0.15, Math.sin(ang) * 0.12, (float)(0.8 + h * 0.05 * r.nextDouble()) * (0.7F + 0.6F * b.forming)
            );
        }
        if (b.forming > 0.3F && r.nextFloat() < b.forming) {
            level.addParticle(
                ModParticles.EMBER.get(), b.getX() + (r.nextDouble() - 0.5) * w * 2.0, b.getY() + r.nextDouble() * h, b.getZ() + (r.nextDouble() - 0.5) * w * 2.0,
                r.nextGaussian() * 0.2, 0.2, r.nextGaussian() * 0.2
            );
        }
    }

    /** The empty body boiling away: steam pouring off it, and the street jumping when it hits the ground. */
    public static void huskTick(ClientLevel level, ShifterTitan husk) {
        RandomSource r = level.random;
        double h = (double)husk.getTitanHeight();
        float k = Mth.clamp((float)husk.dyingTicks / (float)husk.evaporateTicks(), 0.0F, 1.0F);
        int n = r.nextInt(3) == 0 ? 1 + (int)(2.0F * Mth.sin(k * 3.1415927F)) : 0;
        boolean down = husk.dyingTicks > 26;
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            double rad = r.nextDouble() * (down ? h * 0.5 : h * 0.15);
            double y = down ? r.nextDouble() * 3.0 : r.nextDouble() * h;
            ClientFx.spawn(
                level, ModParticles.BIG_STEAM.get(), husk.getX() + Math.cos(ang) * rad, husk.getY() + y, husk.getZ() + Math.sin(ang) * rad, (r.nextDouble() - 0.5) * 0.08,
                0.15 + r.nextDouble() * 0.25, (r.nextDouble() - 0.5) * 0.08, 1.5F + r.nextFloat() * 2.0F
            );
        }
        if (husk.dyingTicks == 26) {
            quake(level, husk.position(), (float)h * 0.6F, r);
        }
    }

    private static void blow(ClientLevel level, Vec3 at, float strength, float yaw, int hit, RandomSource rnd) {
        Vec3 dir = Vec3.directionFromRotation(0.0F, yaw);
        WorldFx.add(new WorldFx.SonicRing(at, dir, 6.0F * strength + 2.0F, 9, 0xFFFFFF));
        WorldFx.add(new WorldFx.SonicRing(at.add(dir.scale(1.5)), dir, 11.0F * strength, 14, 0xFFEBD0));
        WorldFx.add(new WorldFx.Flash(at, 3.5F * strength, 5));
        WorldFx.add(new WorldFx.ShockRing(new Vec3(at.x, ground(level, at.x, at.y, at.z) + 0.2, at.z), 10.0F * strength, 14, 1.2F, 0.7F, 0xE8D8C0, 0.6F));
        for (int i = 0; i < 26; i++) {
            Vec3 v = dir.scale(0.4 + rnd.nextDouble() * 0.9).add(rnd.nextGaussian() * 0.35, rnd.nextGaussian() * 0.35 + 0.1, rnd.nextGaussian() * 0.35);
            ClientFx.spawn(level, ModParticles.DUST_CLOUD.get(), at.x, at.y, at.z, v.x, v.y, v.z, 2.0F + rnd.nextFloat() * 3.0F);
        }
        for (int i = 0; i < 18; i++) {
            Vec3 v = dir.scale(0.6 + rnd.nextDouble() * 1.4).add(rnd.nextGaussian() * 0.5, rnd.nextDouble() * 0.7, rnd.nextGaussian() * 0.5);
            level.addParticle(ModParticles.SPARK.get(), at.x, at.y, at.z, v.x, v.y, v.z);
        }
        if (hit > 0) {
            for (int i = 0; i < 46; i++) {
                Vec3 v = dir.scale(0.3 + rnd.nextDouble() * 0.9).add(rnd.nextGaussian() * 0.4, rnd.nextDouble() * 0.6, rnd.nextGaussian() * 0.4);
                level.addAlwaysVisibleParticle(ModParticles.BLOOD.get(), at.x, at.y, at.z, v.x, v.y, v.z);
            }
            for (int i = 0; i < 16; i++) {
                ClientFx.spawn(
                    level, ModParticles.BIG_STEAM.get(), at.x, at.y, at.z, rnd.nextGaussian() * 0.25, 0.15 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.25,
                    2.5F + rnd.nextFloat() * 2.0F
                );
            }
            WorldFx.add(new WorldFx.Starburst(at, 12, 7.0F, 0.6F, 6, 0xFFD8B8, rnd));
        }
        float k = near(at, 60.0);
        if (k > 0.0F) {
            PostFx.ripple(at, (hit > 0 ? 1.0F : 0.7F) * k);
            PostFx.kick(0.55F * k, 0.25F * k);
        }
    }

    private static void roar(ClientLevel level, Vec3 mouth, float yaw, float pitch, RandomSource rnd) {
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        WorldFx.add(new CombatFx.RoarWaves(mouth, dir));
        double gy = ground(level, mouth.x, mouth.y - 12.0, mouth.z);
        WorldFx.add(new WorldFx.ShockRing(new Vec3(mouth.x, gy + 0.3, mouth.z), 48.0F, 38, 4.5F, 1.8F, 0xD9C7AE, 0.5F));
        for (int i = 0; i < 40; i++) {
            Vec3 v = dir.scale(0.6 + rnd.nextDouble() * 1.2).add(rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.2, rnd.nextGaussian() * 0.25);
            ClientFx.spawn(level, ModParticles.BIG_STEAM.get(), mouth.x, mouth.y, mouth.z, v.x, v.y, v.z, 2.0F + rnd.nextFloat() * 3.0F);
        }
        for (int i = 0; i < 60; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double rad = 4.0 + rnd.nextDouble() * 4.0;
            ClientFx.spawn(
                level, ModParticles.DUST_CLOUD.get(), mouth.x + Math.cos(ang) * rad, gy + 0.3, mouth.z + Math.sin(ang) * rad, Math.cos(ang) * 0.9, 0.05,
                Math.sin(ang) * 0.9, 3.0F + rnd.nextFloat() * 3.0F
            );
        }
        float k = near(mouth, 90.0);
        if (k > 0.0F) {
            PostFx.ripple(mouth, 1.2F * k);
            PostFx.kick(0.9F * k, 0.7F * k);
        }
    }

    /** The roar made visible: rings of shoved air rolling out of the mouth one after another. */
    static final class RoarWaves extends WorldFx.Effect {
        final Vec3 mouth;
        final Vec3 dir;

        RoarWaves(Vec3 mouth, Vec3 dir) {
            super(26);
            this.mouth = mouth;
            this.dir = dir;
        }

        @Override
        void tick() {
            if (this.age % 3 == 0) {
                float k = (float)this.age / (float)this.life;
                WorldFx.add(new WorldFx.SonicRing(this.mouth.add(this.dir.scale(2.0 + (double)this.age * 0.6)), this.dir, 10.0F + 22.0F * k, 16, 0xFFF4E4));
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
        }
    }

    private static void quake(ClientLevel level, Vec3 at, float radius, RandomSource rnd) {
        double gy = ground(level, at.x, at.y + 1.0, at.z);
        Vec3 c = new Vec3(at.x, gy + 0.25, at.z);
        WorldFx.add(new WorldFx.ShockRing(c, radius * 1.6F, 26, 3.2F, 1.5F, 0xDCC8AC, 0.8F));
        WorldFx.add(new WorldFx.ShockRing(c, radius * 0.8F, 14, 1.6F, 0.8F, 0xFFF1D8, 0.9F));
        WorldFx.add(new CombatFx.Cracks(level, c, 10, radius * 0.75F, 60, rnd));
        WorldFx.add(new WorldFx.Flash(c, radius * 0.25F, 5));
        for (int i = 0; i < 70; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double sp = 0.5 + rnd.nextDouble() * 1.1;
            ClientFx.spawn(
                level, ModParticles.DUST_CLOUD.get(), c.x + Math.cos(ang) * 2.0, c.y, c.z + Math.sin(ang) * 2.0, Math.cos(ang) * sp, 0.05 + rnd.nextDouble() * 0.15,
                Math.sin(ang) * sp, 3.0F + rnd.nextFloat() * 4.0F
            );
        }
        BlockState below = level.getBlockState(BlockPos.containing(c.x, gy - 0.5, c.z));
        if (!below.isAir()) {
            for (int i = 0; i < 60; i++) {
                level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, below), c.x + rnd.nextGaussian() * 3.0, c.y, c.z + rnd.nextGaussian() * 3.0, rnd.nextGaussian() * 0.4,
                    0.5 + rnd.nextDouble() * 0.9, rnd.nextGaussian() * 0.4
                );
            }
        }
        float k = near(c, 100.0);
        if (k > 0.0F) {
            PostFx.ripple(c, k);
            PostFx.kick(0.5F * k, 0.4F * k);
        }
    }

    private static void burst(ClientLevel level, Vec3 nape, int id, float yaw, RandomSource rnd) {
        ShiftClient.burst(id);
        Vec3 back = Vec3.directionFromRotation(0.0F, yaw).scale(-1.0);
        WorldFx.add(new WorldFx.Flash(nape, 7.0F, 8));
        WorldFx.add(new WorldFx.SonicRing(nape, new Vec3(0.0, 1.0, 0.0), 12.0F, 12, 0xFFFFFF));
        WorldFx.add(new WorldFx.ShockRing(nape, 16.0F, 14, 1.2F, 0.6F, 0xFFF4EA, 0.6F));
        // the steam bursts out of the cut sideways and down, away from the one climbing out of it
        for (int i = 0; i < 45; i++) {
            Vec3 v = new Vec3(rnd.nextGaussian(), -Math.abs(rnd.nextGaussian()) * 0.5, rnd.nextGaussian()).normalize().scale(0.8 + rnd.nextDouble() * 1.2).subtract(back.scale(0.4));
            ClientFx.spawn(level, ModParticles.BIG_STEAM.get(), nape.x, nape.y - 1.0, nape.z, v.x, v.y, v.z, 1.5F + rnd.nextFloat() * 2.0F);
        }
        for (int i = 0; i < 60; i++) {
            Vec3 v = back.scale(0.3 + rnd.nextDouble() * 0.6).add(rnd.nextGaussian() * 0.35, 0.4 + rnd.nextDouble() * 0.8, rnd.nextGaussian() * 0.35);
            level.addAlwaysVisibleParticle(ModParticles.BLOOD.get(), nape.x, nape.y, nape.z, v.x, v.y, v.z);
        }
        float k = near(nape, 70.0);
        if (k > 0.0F) {
            PostFx.kick(0.9F * k, 0.6F * k);
            ClientEffects.flash(0xFFFFFF, 0.5F * k);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The ODM pistols
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * A shot as everyone sees it. The pellets are rolled again from the server's seed and traced against this client's
     * world, so the tracers and impacts land where the server's did; the shooter's own muzzle fire is drawn by
     * PistolClient in their hands when they're in first person.
     */
    private static void shot(ClientLevel level, Vec3 eye, float yaw, float pitch, int seed, int flags, RandomSource rnd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean volley = (flags & 4) != 0;
        int barrel = flags & 3;
        Player shooter = level.getNearestPlayer(eye.x, eye.y - 1.62, eye.z, 2.0, e -> true);
        Entity self = shooter != null ? shooter : mc.player;
        boolean firstPerson = shooter == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(look);
        Vec3[] muzzles = new Vec3[volley ? 2 : 1];
        for (int i = 0; i < muzzles.length; i++) {
            int side = volley ? (i == 0 ? 1 : -1) : (barrel % 2 == 0 ? 1 : -1);
            muzzles[i] = firstPerson
                ? eye.add(look.scale(0.9)).add(right.scale(0.34 * (double)side)).add(up.scale(-0.24))
                : eye.add(look.scale(0.95)).add(right.scale(0.42 * (double)side)).add(up.scale(-0.5));
            muzzle(level, muzzles[i], look, volley, !firstPerson, rnd);
        }
        Random r = new Random((long)seed);
        int n = volley ? OdmPistolsItem.VOLLEY_PELLETS : OdmPistolsItem.PELLETS;
        for (int i = 0; i < n; i++) {
            Vec3 dir = OdmPistolsItem.pellet(yaw, pitch, volley ? OdmPistolsItem.VOLLEY_SPREAD : OdmPistolsItem.SPREAD, r);
            Vec3 end = eye.add(dir.scale(OdmPistolsItem.RANGE));
            BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
            Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, self, eye, stop, new AABB(eye, stop).inflate(1.0), e -> OdmPistolsItem.canHit(self, e));
            Vec3 hit = eh != null ? eh.getEntity().getBoundingBox().inflate(0.3).clip(eye, stop).orElse(eh.getEntity().position()) : stop;
            WorldFx.add(new CombatFx.Tracer(muzzles[i % muzzles.length], hit, volley));
            if (eh != null) {
                flesh(level, hit, dir, eh.getEntity() instanceof TitanPart, rnd);
            } else if (bh.getType() == HitResult.Type.BLOCK) {
                impact(level, bh, dir, volley, rnd);
            }
        }
        if (shooter != mc.player) {
            float k = near(eye, 24.0);
            if (k > 0.0F) {
                PostFx.kick(0.15F * k, 0.0F);
            }
        }
    }

    private static void muzzle(ClientLevel level, Vec3 m, Vec3 look, boolean volley, boolean drawFlash, RandomSource rnd) {
        if (drawFlash) {
            WorldFx.add(new CombatFx.Muzzle(m, look, volley ? 1.6F : 0.9F, rnd.nextFloat() * 6.2831855F));
            WorldFx.add(new WorldFx.Flash(m, volley ? 1.6F : 0.6F, 3));
        }
        // powder smoke: a grey puff blown out along the barrel, a whiter wisp rising off it
        for (int i = 0; i < (volley ? 10 : 4); i++) {
            Vec3 v = look.scale(0.05 + rnd.nextDouble() * 0.15).add(rnd.nextGaussian() * 0.02, 0.01 + rnd.nextDouble() * 0.02, rnd.nextGaussian() * 0.02);
            level.addParticle(ParticleTypes.SMOKE, m.x, m.y, m.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < (volley ? 4 : 1); i++) {
            ClientFx.spawn(level, ModParticles.STEAM.get(), m.x, m.y, m.z, look.x * 0.04, 0.03, look.z * 0.04, (volley ? 0.7F : 0.35F) + rnd.nextFloat() * 0.2F);
        }
        for (int i = 0; i < (volley ? 12 : 4); i++) {
            Vec3 v = look.scale(0.5 + rnd.nextDouble() * 0.6).add(rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12);
            level.addParticle(ModParticles.SPARK.get(), m.x, m.y, m.z, v.x, v.y, v.z);
        }
        if (volley) {
            WorldFx.add(new WorldFx.SonicRing(m.add(look.scale(0.6)), look, 3.5F, 7, 0xFFE2B0));
            WorldFx.add(new WorldFx.Fireball(m.add(look.scale(0.8)), 1.3F, 1, 6));
        }
    }

    private static void impact(ClientLevel level, BlockHitResult bh, Vec3 dir, boolean hot, RandomSource rnd) {
        Vec3 p = bh.getLocation();
        Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
        Vec3 refl = dir.subtract(n.scale(2.0 * dir.dot(n)));
        for (int i = 0; i < (hot ? 8 : 5); i++) {
            Vec3 v = refl.scale(0.2 + rnd.nextDouble() * 0.4).add(rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.1 + 0.05, rnd.nextGaussian() * 0.1);
            level.addParticle(ModParticles.SPARK.get(), p.x, p.y, p.z, v.x, v.y, v.z);
        }
        BlockState state = level.getBlockState(bh.getBlockPos());
        if (!state.isAir()) {
            for (int i = 0; i < 4; i++) {
                level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, n.x * 0.12 + rnd.nextGaussian() * 0.06, n.y * 0.12 + 0.1,
                    n.z * 0.12 + rnd.nextGaussian() * 0.06
                );
            }
        }
        ClientFx.spawn(level, ModParticles.DUST_CLOUD.get(), p.x + n.x * 0.15, p.y + n.y * 0.15, p.z + n.z * 0.15, n.x * 0.03, 0.02, n.z * 0.03, 0.35F + rnd.nextFloat() * 0.3F);
        CombatFx.Decal dc = decal(p, n, hot ? 0.2F : 0.1F + rnd.nextFloat() * 0.05F, rnd);
        WorldFx.add(new CombatFx.Glow(dc, hot ? 30 : 12));
        if (hot) {
            WorldFx.add(new WorldFx.Fireball(p.add(n.scale(0.3)), 1.5F, 2, 9));
            for (int i = 0; i < 6; i++) {
                level.addParticle(ModParticles.EMBER.get(), p.x, p.y, p.z, rnd.nextGaussian() * 0.15, 0.1 + rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.15);
            }
        }
    }

    private static void flesh(ClientLevel level, Vec3 p, Vec3 dir, boolean titan, RandomSource rnd) {
        for (int i = 0; i < (titan ? 7 : 4); i++) {
            Vec3 v = dir.scale(0.15 + rnd.nextDouble() * 0.3).add(rnd.nextGaussian() * 0.12, 0.05 + rnd.nextDouble() * 0.15, rnd.nextGaussian() * 0.12);
            level.addAlwaysVisibleParticle(ModParticles.BLOOD.get(), p.x, p.y, p.z, v.x, v.y, v.z);
        }
        if (titan && rnd.nextInt(3) == 0) {
            ClientFx.spawn(level, ModParticles.STEAM.get(), p.x, p.y, p.z, 0.0, 0.06, 0.0, 0.8F);
        }
    }

    /** A round's streak: a bright head racing out along its path, the tail fading behind it. */
    static final class Tracer extends WorldFx.Effect {
        final Vec3 from;
        final Vec3 to;
        final boolean hot;

        Tracer(Vec3 from, Vec3 to, boolean hot) {
            super(hot ? 4 : 3);
            this.from = from;
            this.to = to;
            this.hot = hot;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float head = Math.min(1.0F, 0.2F + p * 1.6F);
            float tail = Math.max(0.0F, head - 0.5F);
            float a = 1.0F - Math.max(0.0F, (p - 0.55F) / 0.45F);
            Vec3 b = this.from.lerp(this.to, (double)head);
            Vec3 t = this.from.lerp(this.to, (double)tail);
            float w = this.hot ? 0.09F : 0.045F;
            d.ribbon(new Vec3[]{t, t.lerp(b, 0.7), b}, new float[]{0.0F, w * 0.7F, w}, 1.0F, this.hot ? 0.55F : 0.9F, this.hot ? 0.2F : 0.6F, new float[]{0.0F, 0.45F * a, a});
        }
    }

    /** Muzzle fire in the world: a star of flame facing the camera and a short tongue of it out along the barrel. */
    static final class Muzzle extends WorldFx.Effect {
        final Vec3 at;
        final Vec3 dir;
        final float size;
        final float spin;

        Muzzle(Vec3 at, Vec3 dir, float size, float spin) {
            super(2);
            this.at = at;
            this.dir = dir;
            this.size = size;
            this.spin = spin;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float a = 1.0F - this.progress(pt) * 0.7F;
            Vec3 toCam = d.cam.subtract(this.at).normalize();
            Vec3 u = toCam.cross(new Vec3(0.0, 1.0, 0.0));
            u = u.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : u.normalize();
            Vec3 w = u.cross(toCam).normalize();
            star(d, this.at, u, w, this.size * 0.55F, 8, this.spin, a);
            Vec3 side = this.dir.cross(toCam);
            side = side.lengthSqr() < 1.0E-6 ? u.scale((double)this.size * 0.22) : side.normalize().scale((double)this.size * 0.22);
            Vec3 tip = this.at.add(this.dir.scale((double)this.size * 1.1));
            d.v(this.at.add(side), 1.0F, 0.85F, 0.5F, 0.9F * a);
            d.v(this.at.subtract(side), 1.0F, 0.85F, 0.5F, 0.9F * a);
            d.v(tip, 1.0F, 0.5F, 0.15F, 0.0F);
            d.v(tip, 1.0F, 0.5F, 0.15F, 0.0F);
        }
    }

    /** A spiky star of fire: kite-shaped spikes, long and short, round a white-hot core, in the plane of u and w. */
    static void star(WorldFx.Draw d, Vec3 c, Vec3 u, Vec3 w, float radius, int spikes, float spin, float alpha) {
        for (int i = 0; i < spikes; i++) {
            double a0 = (double)spin + ((double)i + 0.5) / (double)spikes * Math.PI * 2.0;
            double a1 = (double)spin + (double)i / (double)spikes * Math.PI * 2.0;
            double a2 = (double)spin + ((double)i + 1.0) / (double)spikes * Math.PI * 2.0;
            double len = (double)radius * (i % 2 == 0 ? 1.0 : 0.6);
            double base = (double)radius * 0.22;
            Vec3 tip = c.add(u.scale(Math.cos(a0) * len)).add(w.scale(Math.sin(a0) * len));
            Vec3 b1 = c.add(u.scale(Math.cos(a1) * base)).add(w.scale(Math.sin(a1) * base));
            Vec3 b2 = c.add(u.scale(Math.cos(a2) * base)).add(w.scale(Math.sin(a2) * base));
            d.v(c, 1.0F, 0.97F, 0.85F, alpha);
            d.v(b1, 1.0F, 0.8F, 0.4F, 0.8F * alpha);
            d.v(tip, 1.0F, 0.55F, 0.15F, 0.0F);
            d.v(b2, 1.0F, 0.8F, 0.4F, 0.8F * alpha);
        }
    }

    /**
     * The first-person muzzle fire, drawn in the gun's own space (barrel along -Z): a star facing the eye, two crossed
     * blades of flame out of the barrel; or, while a volley charges, a hot glow gathering in the muzzle.
     */
    public static void handFlash(PoseStack ps, MultiBufferSource buffers, float flash, float charge, float spin) {
        VertexConsumer vc = buffers.getBuffer(WorldFx.GLOW);
        Matrix4f m = ps.last().pose();
        float f = Mth.clamp(flash, 0.0F, 1.0F);
        if (f > 0.0F) {
            float r = 0.55F;
            for (int i = 0; i < 8; i++) {
                double a0 = (double)spin + ((double)i + 0.5) / 8.0 * Math.PI * 2.0;
                double a1 = (double)spin + (double)i / 8.0 * Math.PI * 2.0;
                double a2 = (double)spin + ((double)i + 1.0) / 8.0 * Math.PI * 2.0;
                float len = r * (i % 2 == 0 ? 1.0F : 0.55F);
                v(vc, m, 0.0F, 0.0F, 0.0F, 1.0F, 0.97F, 0.85F, f);
                v(vc, m, (float)Math.cos(a1) * r * 0.2F, (float)Math.sin(a1) * r * 0.2F, 0.0F, 1.0F, 0.8F, 0.4F, 0.8F * f);
                v(vc, m, (float)Math.cos(a0) * len, (float)Math.sin(a0) * len, 0.0F, 1.0F, 0.5F, 0.15F, 0.0F);
                v(vc, m, (float)Math.cos(a2) * r * 0.2F, (float)Math.sin(a2) * r * 0.2F, 0.0F, 1.0F, 0.8F, 0.4F, 0.8F * f);
            }
            for (int k = 0; k < 2; k++) {
                float sx = k == 0 ? 0.13F : 0.0F;
                float sy = k == 0 ? 0.0F : 0.13F;
                v(vc, m, sx, sy, 0.0F, 1.0F, 0.9F, 0.6F, 0.9F * f);
                v(vc, m, -sx, -sy, 0.0F, 1.0F, 0.9F, 0.6F, 0.9F * f);
                v(vc, m, 0.0F, 0.0F, -1.0F, 1.0F, 0.5F, 0.15F, 0.0F);
                v(vc, m, 0.0F, 0.0F, -1.0F, 1.0F, 0.5F, 0.15F, 0.0F);
            }
        }
        if (charge > 0.0F) {
            float g = charge * charge;
            float r = 0.08F + 0.14F * g;
            for (int i = 0; i < 8; i++) {
                double a1 = (double)i / 8.0 * Math.PI * 2.0;
                double a2 = ((double)i + 1.0) / 8.0 * Math.PI * 2.0;
                v(vc, m, 0.0F, 0.0F, 0.0F, 1.0F, 0.75F, 0.35F, 0.9F * g);
                v(vc, m, (float)Math.cos(a1) * r, (float)Math.sin(a1) * r, 0.0F, 1.0F, 0.45F, 0.1F, 0.0F);
                v(vc, m, (float)Math.cos(a2) * r, (float)Math.sin(a2) * r, 0.0F, 1.0F, 0.45F, 0.1F, 0.0F);
                v(vc, m, 0.0F, 0.0F, 0.0F, 1.0F, 0.75F, 0.35F, 0.9F * g);
            }
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, Mth.clamp(a, 0.0F, 1.0F));
    }

    /** A bullet hole: a dark scorched spot on a block face, gone after half a minute. */
    record Decal(Vec3 p, Vec3 u, Vec3 w, float size, long born) {
    }

    private static CombatFx.Decal decal(Vec3 at, Vec3 n, float size, RandomSource rnd) {
        Vec3 t = Math.abs(n.y) > 0.5 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = n.cross(t).normalize();
        Vec3 w = n.cross(u).normalize();
        double a = rnd.nextDouble() * Math.PI;
        Vec3 ru = u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
        Vec3 rw = n.cross(ru).normalize();
        ClientLevel level = Minecraft.getInstance().level;
        CombatFx.Decal dc = new CombatFx.Decal(at.add(n.scale(0.01)), ru, rw, size, level != null ? level.getGameTime() : 0L);
        if (DECALS.size() >= 320) {
            DECALS.remove(0);
        }
        DECALS.add(dc);
        return dc;
    }

    /** The fresh hole's glow, cooling in a few ticks. */
    static final class Glow extends WorldFx.Effect {
        final CombatFx.Decal dc;

        Glow(CombatFx.Decal dc, int life) {
            super(life);
            this.dc = dc;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float a = 1.0F - this.progress(pt);
            a *= a;
            Vec3 c = this.dc.p();
            Vec3 u = this.dc.u().scale((double)this.dc.size() * 1.4);
            Vec3 w = this.dc.w().scale((double)this.dc.size() * 1.4);
            Vec3[] ring = new Vec3[]{c.add(u), c.add(w), c.subtract(u), c.subtract(w)};
            for (int i = 0; i < 4; i++) {
                d.v(c, 1.0F, 0.75F, 0.35F, a);
                d.v(ring[i], 1.0F, 0.4F, 0.1F, 0.0F);
                d.v(ring[(i + 1) % 4], 1.0F, 0.4F, 0.1F, 0.0F);
                d.v(c, 1.0F, 0.75F, 0.35F, a);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || DECALS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            DECALS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        DECALS.removeIf(dc -> now - dc.born() > 600L || now < dc.born());
        Vec3 cam = event.getCamera().getPosition();
        BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.debugQuads());
        Matrix4f m = new PoseStack().last().pose();
        for (CombatFx.Decal dc : DECALS) {
            float a = 0.85F * Mth.clamp((600.0F - (float)(now - dc.born())) / 120.0F, 0.0F, 1.0F);
            quad(vc, m, cam, dc.p(), dc.u().scale((double)dc.size() * 1.9), dc.w().scale((double)dc.size() * 1.9), 0.12F, 0.1F, 0.08F, a * 0.3F);
            quad(vc, m, cam, dc.p(), dc.u().scale((double)dc.size()), dc.w().scale((double)dc.size()), 0.05F, 0.04F, 0.035F, a);
        }
        bs.endBatch(RenderType.debugQuads());
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, Vec3 u, Vec3 w, float r, float g, float b, float a) {
        Vec3[] corners = new Vec3[]{c.add(u).add(w), c.add(u).subtract(w), c.subtract(u).subtract(w), c.subtract(u).add(w)};
        for (Vec3 p : corners) {
            vc.addVertex(m, (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z)).setColor(r, g, b, a);
        }
    }
}
