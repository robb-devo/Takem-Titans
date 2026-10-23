package com.takem.titans.entity.titan;

import com.takem.titans.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class CrawlerTitan extends AbstractTitan {
    private boolean lungeHit;

    public CrawlerTitan(EntityType<? extends CrawlerTitan> type, Level level) {
        super(type, level);
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 8.0F + random.nextFloat() * 3.5F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.16 + (double)height * 0.02;
    }

    @Override
    public int titanKind() {
        return 2;
    }

    @Override
    public boolean isCrawler() {
        return true;
    }

    @Override
    public int variantCount() {
        return 2;
    }

    @Override
    public float headScale() {
        return 1.12F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 3.0F * height;
    }

    @Override
    protected double detectionRange() {
        return 40.0;
    }

    @Override
    protected float turnRate() {
        return 10.0F;
    }

    @Override
    protected float widthFactor() {
        return 1.45F;
    }

    @Override
    protected float heightFactor() {
        return 1.55F;
    }

    @Override
    public Vec3 holdPoint(float side) {
        return this.skeletonPoint(3, new Vector3f(TitanSkeleton.MOUTH).add(0.0F, 1.0F, -3.0F));
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        double dy = target.getY() - this.getY();
        if (!(dy > (double)this.getBbHeight() * 1.5)) {
            if (this.onGround() && dist < (double)this.getTitanScale() * 4.5 && dist > (double)this.getBbWidth() * 0.4) {
                this.setAction(TitanAction.LEAP, target);
                this.attackCooldown = 40 + this.random.nextInt(30);
                this.lungeHit = false;
                this.level()
                    .playSound(
                        null, this.getX(), this.getEyeY(), this.getZ(), ModSounds.ABNORMAL_SCREECH.get(), SoundSource.HOSTILE, 2.0F, this.voicePitch() + 0.4F
                    );
            } else if (dist < (double)this.getBbWidth() * 0.9 && this.held == null) {
                this.grabWithJaws(target);
            }
        }
    }

    private void grabWithJaws(LivingEntity target) {
        Vec3 mouth = this.holdPoint(0.0F);
        if (target.position().add(0.0, (double)(target.getBbHeight() / 2.0F), 0.0).distanceTo(mouth) < (double)this.getTitanScale() * 0.8 + 1.0) {
            this.grab(target, -1.0F);
        }

        this.attackCooldown = 25;
    }

    @Override
    protected void tickAction(ServerLevel level, TitanAction action, @Nullable LivingEntity target) {
        if (action == TitanAction.LEAP) {
            int launch = (int)((float)action.duration * 0.3F);
            if (this.actionTicks == launch && target != null) {
                Vec3 d = target.position().subtract(this.position());
                double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
                double t = Mth.clamp(horiz / 1.1, 6.0, 14.0);
                this.setDeltaMovement(d.x / t, 0.45, d.z / t);
                this.walkVel = new Vec3(d.x / t, 0.0, d.z / t);
                this.hasImpulse = true;
            }

            if (this.actionTicks > launch && !this.lungeHit) {
                Vec3 mouth = this.holdPoint(0.0F);
                AABB box = new AABB(mouth, mouth).inflate((double)this.getTitanScale() * 0.7 + 0.8);

                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (!(e instanceof AbstractTitan) && this.canTarget(e)) {
                        this.lungeHit = true;
                        if (this.held == null) {
                            this.grab(e, -1.0F);
                            return;
                        }
                    }
                }
            }

            if (this.actionTicks > launch + 3 && this.onGround()) {
                this.setAction(TitanAction.NONE, null);
                this.walkVel = Vec3.ZERO;
            } else if (this.actionTicks >= action.duration * 2) {
                this.setAction(TitanAction.NONE, null);
            }
        } else {
            super.tickAction(level, action, target);
        }
    }

    @Override
    protected boolean tryStomp(LivingEntity target) {
        return false;
    }
}
