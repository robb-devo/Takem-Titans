package com.takem.titans.entity.titan;

import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Ghoul: a small, gaunt, bone-white titan that runs on all fours and does not sleep. It does not walk towards you,
 * it comes in bursts - a scuttling sprint faster than a horse, zig-zagging, up and over whatever is in the way - then it
 * freezes dead still, twitching, head cocked, and comes again. Close enough, it springs. It is silent until it has
 * seen you; then it shrieks.
 */
public class GhoulTitan extends CrawlerTitan {
    private int burst;
    private int freeze = 20;
    private float weave;
    private boolean shrieked;

    public GhoulTitan(EntityType<? extends GhoulTitan> type, Level level) {
        super(type, level);
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 4.5F + random.nextFloat() * 1.2F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.5;
    }

    @Override
    public int variantCount() {
        return 1;
    }

    @Override
    public float headScale() {
        return 0.95F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 4.0F * height;
    }

    @Override
    protected double detectionRange() {
        return 120.0;
    }

    @Override
    protected float turnRate() {
        return 30.0F;
    }

    @Override
    protected boolean isSluggish(ServerLevel level) {
        return false;
    }

    @Override
    protected float widthFactor() {
        return 1.2F;
    }

    @Override
    protected void spotted(LivingEntity target) {
        // no roar: it only shrieks, once
        this.roarCooldown = 400;
        if (!this.shrieked) {
            this.shrieked = true;
            this.level().playSound(null, this.getX(), this.getEyeY(), this.getZ(), ModSounds.GHOUL_SHRIEK.get(), SoundSource.HOSTILE, 3.5F, 0.9F + this.random.nextFloat() * 0.2F);
            this.level().playSound(null, this.getX(), this.getEyeY(), this.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.5F, 0.6F);
            this.freeze = 12;
        }
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        RandomSource rnd = this.random;
        Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
        Vec3 dir = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : to.normalize();
        if (this.freeze > 0) {
            // dead still, but twitching: the head snaps round in little jerks
            this.freeze--;
            this.walkVel = Vec3.ZERO;
            this.setFlag(8, false);
            if (rnd.nextInt(4) == 0) {
                this.setYRot(this.getYRot() + (rnd.nextFloat() - 0.5F) * 50.0F);
                this.yHeadRot = this.getYRot() + (rnd.nextFloat() - 0.5F) * 80.0F;
            }

            if (this.freeze == 0) {
                this.burst = 10 + rnd.nextInt(10);
                this.weave = (rnd.nextFloat() - 0.5F) * 2.0F;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.45F);
            }

            return;
        }

        if (this.burst > 0) {
            this.burst--;
            this.setFlag(8, true);
            // a zig-zag that tightens as it closes in
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x).scale(this.weave * Mth.sin(this.burst * 0.6F) * Mth.clamp((float)dist / 40.0F, 0.0F, 1.0F));
            Vec3 run = dir.add(side).normalize().scale(1.25 + this.threat * 0.05);
            this.walkVel = run;
            this.turnTowards(this.position().add(run.scale(10.0)), 45.0F);
            if (this.horizontalCollision) {
                // straight up the wall and over
                Vec3 v = this.getDeltaMovement();
                this.setDeltaMovement(v.x, Math.max(v.y, 0.55), v.z);
            }

            if (this.tickCount % 3 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 1.4F, 0.5F + rnd.nextFloat() * 0.2F);
            }

            if (this.burst == 0) {
                this.freeze = dist < 24.0 ? 4 + rnd.nextInt(6) : 8 + rnd.nextInt(14);
            }

            return;
        }

        this.freeze = 6;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.level() instanceof ServerLevel sl && this.tickCount % 4 == 0 && !this.isDeadOrDying()) {
            // thin steam off its back, and a cold white ash
            sl.sendParticles(ModParticles.STEAM.get(), this.getX(), this.getY() + this.getBbHeight() * 0.8, this.getZ(), 1, 0.4, 0.1, 0.4, 0.01);
            if (this.burst > 0) {
                sl.sendParticles(ParticleTypes.WHITE_ASH, this.getX(), this.getY() + 0.5, this.getZ(), 6, 0.8, 0.3, 0.8, 0.02);
            }
        }
    }
}
