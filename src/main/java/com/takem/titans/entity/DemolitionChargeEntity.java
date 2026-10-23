package com.takem.titans.entity;

import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Boom;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DemolitionChargeEntity extends ThrowableItemProjectile {
    private int fuse = -1;
    private Vec3 stuckAt = Vec3.ZERO;

    public DemolitionChargeEntity(EntityType<? extends DemolitionChargeEntity> type, Level level) {
        super(type, level);
    }

    public DemolitionChargeEntity(Level level, LivingEntity owner) {
        super(ModEntities.DEMOLITION_CHARGE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.DEMOLITION_CHARGE.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.055;
    }

    @Override
    public void tick() {
        if (this.fuse >= 0) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPos(this.stuckAt);
            this.xo = this.stuckAt.x;
            this.yo = this.stuckAt.y;
            this.zo = this.stuckAt.z;
            if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
                int left = this.fuse--;
                int every = left > 40 ? 10 : (left > 20 ? 5 : 2);
                if (left % every == 0) {
                    sl.playSound(
                        null, this.getX(), this.getY(), this.getZ(), ModSounds.CHARGE_BEEP.get(), SoundSource.PLAYERS, 1.2F, 1.0F + (float)(60 - left) * 0.012F
                    );
                    sl.sendParticles(DustParticleOptions.REDSTONE, this.getX(), this.getY() + 0.2, this.getZ(), 3, 0.05, 0.05, 0.05, 0.0);
                }

                if (left <= 0) {
                    this.detonate(sl, this.stuckAt);
                }
            }
        } else {
            super.tick();
            if (this.level().isClientSide && this.tickCount % 2 == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.02, 0.0);
            }

            if (this.tickCount > 400 && !this.level().isClientSide) {
                this.discard();
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        if (this.fuse < 0) {
            super.onHit(result);
            if (this.level() instanceof ServerLevel level && !this.isRemoved() && this.fuse < 0) {
                this.stuckAt = result.getLocation();
                this.fuse = 60;
                this.setNoGravity(true);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CHARGE_STICK.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
            }
        }
    }

    private void detonate(ServerLevel level, Vec3 at) {
        if (!this.isRemoved()) {
            Boom.Spec s = new Boom.Spec().radius(8.5).blocks(7.5, 6.0F).damage(28.0F, 110.0F).fire(10).fx(4, 7.0F, 0.0F).owner(this.getOwner(), this);
            s.knock = 2.0;
            s.volume = 8.0F;
            Boom.explode(level, at, s);
            this.discard();
        }
    }
}
