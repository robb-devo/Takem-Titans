package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class ClusterSpearEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(ClusterSpearEntity.class, EntityDataSerializers.BOOLEAN);
    private int fuse = -1;
    private int stuckEntityId = -1;
    private Vec3 stuckOffset = Vec3.ZERO;
    private int stuckPart = -1;
    private int life;

    public ClusterSpearEntity(EntityType<? extends ClusterSpearEntity> type, Level level) {
        super(type, level);
    }

    public ClusterSpearEntity(Level level, LivingEntity owner) {
        this(ModEntities.CLUSTER_SPEAR.get(), level);
        this.setOwner(owner);
    }

    @Override
    protected void defineSynchedData(Builder builder) {
        builder.define(STUCK, false);
    }

    public boolean isStuck() {
        return this.entityData.get(STUCK);
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        if (!this.isStuck()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            Vec3 v = this.getDeltaMovement();
            if (hit.getType() != Type.MISS) {
                this.setPos(hit.getLocation());
                if (!this.level().isClientSide) {
                    this.stick(hit);
                }
            } else {
                this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
                this.setDeltaMovement(v.scale(0.995).add(0.0, -0.025, 0.0));
                this.updateRotation();
            }

            if (this.level().isClientSide) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
                this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), -v.x * 0.1, -v.y * 0.1, -v.z * 0.1);
            } else if (this.life == 22 && !this.isStuck()) {
                this.burst();
                return;
            }
        } else {
            label48:
            if (this.stuckEntityId >= 0) {
                Entity e = this.level().getEntity(this.stuckEntityId);
                if (e instanceof AbstractTitan titan && this.stuckPart >= 0 && !titan.isDeadOrDying()) {
                    this.setPos(titan.partAnchor(this.stuckPart, this.stuckOffset, 1.0F));
                    break label48;
                }

                if (e != null && e.isAlive()) {
                    this.setPos(e.position().add(this.stuckOffset));
                }
            }

            if (!this.level().isClientSide && --this.fuse <= 0) {
                this.burst();
            }
        }

        if (!this.level().isClientSide && this.life > 200) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != this.getOwner();
    }

    private void stick(HitResult hit) {
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
        this.fuse = 16;
        if (hit instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            if (target instanceof TitanPart part) {
                this.stuckPart = part.index;
                this.stuckEntityId = part.getParent().getId();
                this.stuckOffset = part.getParent().partLocal(part.index, hit.getLocation());
            } else {
                this.stuckEntityId = target.getId();
                this.stuckOffset = hit.getLocation().subtract(target.position());
            }
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CHARGE_STICK.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    private void burst() {
        if (this.level() instanceof ServerLevel level) {
            Entity owner = this.getOwner();
            Vec3 at = this.position();
            Vec3 carry = this.isStuck() ? Vec3.ZERO : this.getDeltaMovement().scale(0.55);
            level.playSound(null, at.x, at.y, at.z, ModSounds.CLUSTER_SPLIT.get(), SoundSource.PLAYERS, 4.0F, 0.9F + level.random.nextFloat() * 0.2F);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 24, 0.6, 0.6, 0.6, 0.06);
            int n = 12;

            for (int i = 0; i < n; i++) {
                BombletEntity b = new BombletEntity(level, at.x, at.y + 0.2, at.z, owner);
                double yaw = 6.283185307179586 * (double)i / (double)n + level.random.nextDouble() * 0.3;
                double pitch = this.isStuck() ? 0.25 + level.random.nextDouble() * 0.5 : -0.1 + level.random.nextDouble() * 0.4;
                Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
                Vec3 v = dir.scale(0.45 + level.random.nextDouble() * 0.45).add(carry);
                b.setDeltaMovement(v);
                level.addFreshEntity(b);
            }
        }

        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Fuse", this.fuse);
        tag.putInt("StuckId", this.stuckEntityId);
        tag.putInt("StuckPart", this.stuckPart);
        tag.putDouble("OffX", this.stuckOffset.x);
        tag.putDouble("OffY", this.stuckOffset.y);
        tag.putDouble("OffZ", this.stuckOffset.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.fuse = tag.getInt("Fuse");
        this.stuckEntityId = tag.getInt("StuckId");
        this.stuckPart = tag.getInt("StuckPart");
        this.stuckOffset = new Vec3(tag.getDouble("OffX"), tag.getDouble("OffY"), tag.getDouble("OffZ"));
    }
}
