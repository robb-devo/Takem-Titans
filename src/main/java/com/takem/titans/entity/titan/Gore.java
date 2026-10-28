package com.takem.titans.entity.titan;

import com.takem.titans.entity.TitanGibEntity;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.registry.ModParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public final class Gore {
    private static final BlockParticleOption MEAT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());
    private static final BlockParticleOption GORE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
    private static final BlockParticleOption BONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());
    private static final DustParticleOptions MIST = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.03F), 3.0F);

    private Gore() {
    }

    static Vector3f centre(int joint) {
        return switch (joint) {
            case 2 -> TitanSkeleton.TORSO_CENTER;
            case 3 -> TitanSkeleton.HEAD_CENTER;
            default -> new Vector3f(0.0F, 7.0F, 0.0F);
            case 6, 9 -> TitanSkeleton.FOREARM_CENTER;
            case 12, 14 -> TitanSkeleton.SHIN_CENTER;
        };
    }

    public static void gib(ServerLevel level, AbstractTitan t, int joint, Vec3 vel) {
        Vec3 at = t.skeletonPoint(joint, centre(joint));
        TitanGibEntity g = TitanGibEntity.of(level, t, joint, at, vel);
        level.addFreshEntity(g);
        // strands of sinew stretch between the stump and the limb until they tear
        com.takem.titans.fx.Fx.send(level, com.takem.titans.fx.Fx.SINEW, at.x, at.y, at.z, (float)g.getId(), (float)t.getId(), (float)joint, t.getTitanScale(), 128.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 3.0F, 0.4F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
        splatter(level, at, 50 + (int)(t.getTitanScale() * 10.0F), 0.9);
        screen(level, at, 0.8F);
    }

    public static void spurt(ServerLevel level, Vec3 at, Vec3 dir, int n, double speed) {
        RandomSource r = level.random;

        for (int i = 0; i < n; i++) {
            double s = speed * (0.6 + r.nextDouble() * 0.6);
            level.sendParticles(
                ModParticles.BLOOD.get(),
                at.x,
                at.y,
                at.z,
                0,
                dir.x * s + (r.nextDouble() - 0.5) * 0.25,
                dir.y * s + (r.nextDouble() - 0.5) * 0.2,
                dir.z * s + (r.nextDouble() - 0.5) * 0.25,
                1.0
            );
        }
    }

    public static void chunks(ServerLevel level, Vec3 at, int n, double speed) {
        RandomSource r = level.random;

        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5 + 0.6, r.nextGaussian()).normalize().scale(speed * (0.4 + r.nextDouble()));
            level.sendParticles(i % 5 == 0 ? BONE : (i % 3 == 0 ? GORE : MEAT), at.x, at.y, at.z, 0, d.x, d.y, d.z, 1.0);
        }

        level.sendParticles(MIST, at.x, at.y, at.z, 4 + n / 3, 0.6, 0.6, 0.6, 0.0);
    }

    public static void splatter(ServerLevel level, Vec3 at, int n, double speed) {
        RandomSource r = level.random;

        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6 + 0.4, r.nextGaussian()).normalize().scale(speed * (0.3 + r.nextDouble()));
            level.sendParticles(ModParticles.BLOOD.get(), at.x, at.y, at.z, 0, d.x, d.y, d.z, 1.0);
        }

        level.sendParticles(ModParticles.BIG_STEAM.get(), at.x, at.y, at.z, 6 + n / 10, 0.8, 0.8, 0.8, 0.05);
        chunks(level, at, n / 2, speed * 0.8);
    }

    public static void screen(ServerLevel level, Vec3 at, float strength) {
        for (ServerPlayer p : level.players()) {
            double d = p.getEyePosition().distanceTo(at);
            if (d < 9.0) {
                float k = strength * (float)(1.0 - d / 9.0);
                if (k > 0.15F) {
                    PacketDistributor.sendToPlayer(p, FeedbackPayload.of(16, Math.round(k * 100.0F), level.random.nextInt(1000), 0));
                }
            }
        }
    }

    public static void burst(ServerLevel level, AbstractTitan t, Vec3 from) {
        RandomSource r = level.random;
        Vec3 c = t.skeletonPoint(2, TitanSkeleton.TORSO_CENTER);
        float s = t.getTitanScale();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 6.0F, 0.3F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 3.0F, 0.6F);

        for (int joint : new int[]{2, 3, 5, 8, 11, 13}) {
            Vec3 at = t.skeletonPoint(joint, centre(joint));
            Vec3 out = at.subtract(from);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(r.nextGaussian(), 1.0, r.nextGaussian()) : out.normalize();
            double kick = 0.6 + r.nextDouble() * 0.9;
            Vec3 v = out.scale(kick).add(0.0, 0.5 + r.nextDouble() * 0.7, 0.0);
            level.addFreshEntity(TitanGibEntity.of(level, t, joint, at, v));
            splatter(level, at, 40, 1.2);
        }

        for (int i = 0; i < 3; i++) {
            int joint = new int[]{6, 9, 12, 14}[r.nextInt(4)];
            Vec3 v = new Vec3(r.nextGaussian(), 0.8 + r.nextDouble(), r.nextGaussian()).scale(0.9);
            TitanGibEntity g = TitanGibEntity.of(level, t, joint, c, v);
            level.addFreshEntity(g);
        }

        splatter(level, c, 200, 1.8);
        level.sendParticles(ModParticles.BIG_STEAM.get(), c.x, c.y, c.z, 120, (double)s, (double)s * 1.5, (double)s, 0.15);
        screen(level, c, 1.4F);
    }
}
