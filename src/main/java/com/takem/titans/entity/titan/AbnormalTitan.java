package com.takem.titans.entity.titan;

import com.takem.titans.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AbnormalTitan extends AbstractTitan {
    private int fixation;
    private int stare;
    private boolean leapHit;

    public AbnormalTitan(EntityType<? extends AbnormalTitan> type, Level level) {
        super(type, level);
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 9.0F + random.nextFloat() * 6.0F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.12 + (double)height * 0.026;
    }

    @Override
    public int titanKind() {
        return 1;
    }

    @Override
    public int variantCount() {
        return 3;
    }

    @Override
    public float headScale() {
        return this.getVariant() == 1 ? 0.8F : (this.getVariant() == 2 ? 1.3F : 0.92F);
    }

    @Override
    public float headTilt() {
        return 2.6F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 4.0F * height;
    }

    @Override
    protected double detectionRange() {
        return 72.0;
    }

    @Override
    protected float turnRate() {
        return 11.0F;
    }

    @Override
    protected float widthFactor() {
        return 0.75F;
    }

    @Override
    protected void spotted(LivingEntity target) {
        this.roarCooldown = 300;
        this.setAction(TitanAction.ROAR, target);
        this.level()
            .playSound(
                null,
                this.getX(),
                this.getEyeY(),
                this.getZ(),
                ModSounds.ABNORMAL_SCREECH.get(),
                SoundSource.HOSTILE,
                3.0F + this.getTitanScale() * 0.5F,
                this.voicePitch() + 0.2F
            );
    }

    @Nullable
    @Override
    protected LivingEntity findTarget(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current != null && current.isAlive() && this.canTarget(current) && --this.fixation > 0) {
            return current;
        } else {
            this.fixation = 200 + this.random.nextInt(300);
            Player pick = null;
            int seen = 0;

            for (Player p : level.players()) {
                if (this.canTarget(p) && this.distanceToSqr(p) < this.detectionRange() * this.detectionRange()) {
                    if (this.random.nextInt(++seen) == 0) {
                        pick = p;
                    }
                }
            }

            if (pick != null && current == null && this.roarCooldown <= 0) {
                this.spotted(pick);
            }

            return pick;
        }
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        if (this.stare > 0) {
            this.stare--;
            this.walkVel = Vec3.ZERO;
            this.setFlag(8, false);
            this.turnTowards(target.position(), this.turnRate());
        } else {
            if (this.random.nextInt(400) == 0) {
                this.stare = 30 + this.random.nextInt(40);
            }

            this.setFlag(8, !sluggish);
            super.chase(target, dist, sluggish);
        }
    }

    @Override
    protected void wander(ServerLevel level, boolean sluggish) {
        this.setFlag(8, false);
        super.wander(level, sluggish);
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        boolean airborne = !target.onGround();
        double dy = target.getY() - this.getY();
        if (this.onGround()
            && dist > (double)this.getTitanScale() * 2.0
            && dist < 30.0
            && dy < (double)this.getTitanHeight() * 1.6
            && (airborne || this.random.nextInt(6) == 0)) {
            this.setAction(TitanAction.LEAP, target);
            this.attackCooldown = 60 + this.random.nextInt(40);
            this.leapHit = false;
            this.level()
                .playSound(
                    null, this.getX(), this.getEyeY(), this.getZ(), ModSounds.ABNORMAL_SCREECH.get(), SoundSource.HOSTILE, 3.0F, this.voicePitch() + 0.3F
                );
        } else {
            super.chooseAttack(target, dist);
        }
    }

    @Override
    protected void tickAction(ServerLevel level, TitanAction action, @Nullable LivingEntity target) {
        if (action == TitanAction.LEAP) {
            int launch = (int)((float)action.duration * 0.3F);
            if (this.actionTicks == launch) {
                Vec3 aim = target != null ? target.position().add(target.getDeltaMovement().scale(10.0)) : this.position().add(this.getLookAngle().scale(15.0));
                Vec3 d = aim.subtract(this.position());
                double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
                double t = Mth.clamp(horiz / 1.4, 10.0, 26.0);
                double vy = Mth.clamp((d.y + 0.036 * t * t) / t, 0.6, 2.2);
                this.setDeltaMovement(d.x / t, vy, d.z / t);
                this.walkVel = new Vec3(d.x / t, 0.0, d.z / t);
                this.hasImpulse = true;
            }

            if (this.actionTicks > launch && !this.leapHit) {
                AABB box = this.getBoundingBox().inflate(1.0);

                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (!(e instanceof AbstractTitan) && this.canTarget(e)) {
                        this.leapHit = true;
                        if (this.held == null && this.random.nextBoolean() && !this.isArmCut(-1)) {
                            this.grab(e, -1.0F);
                            return;
                        }

                        e.hurt(this.damageSources().mobAttack(this), 10.0F + this.getTitanScale() * 2.0F);
                        Vec3 push = e.position().subtract(this.position()).normalize().scale(1.4);
                        e.push(push.x, 0.4, push.z);
                        e.hurtMarked = true;
                    }
                }
            }

            if (this.actionTicks > launch + 4 && this.onGround()) {
                this.landed(level, 0.8F);
                this.setAction(TitanAction.NONE, null);
            } else if (this.actionTicks >= action.duration * 3) {
                this.setAction(TitanAction.NONE, null);
            }
        } else {
            super.tickAction(level, action, target);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
