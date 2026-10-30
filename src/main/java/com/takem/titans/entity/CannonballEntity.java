package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Boom;
import com.takem.titans.world.ShiganshinaLayout;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class CannonballEntity extends ThrowableItemProjectile {
    public static final double GRAVITY = 0.03;
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(CannonballEntity.class, EntityDataSerializers.INT);
    @Nullable
    private Vec3 origin;

    public CannonballEntity(EntityType<? extends CannonballEntity> type, Level level) {
        super(type, level);
    }

    public CannonballEntity(Level level, double x, double y, double z, @Nullable Entity owner, int tier) {
        super(ModEntities.CANNONBALL.get(), x, y, z, level);
        this.origin = new Vec3(x, y, z);
        if (owner != null) {
            this.setOwner(owner);
        }

        this.entityData.set(TIER, tier);
    }

    @Override
    protected void defineSynchedData(Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, 1);
    }

    public int tier() {
        return this.entityData.get(TIER);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CANNON_SHELL.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target)
            && !(target instanceof CannonEntity)
            && !(target instanceof CannonballEntity)
            && (!(target instanceof Player p) || !(p.getVehicle() instanceof CannonEntity));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            Vec3 v = this.getDeltaMovement();

            for (int i = 0; i < 3; i++) {
                double f = (double)i / 3.0;
                this.level()
                    .addParticle(
                        this.tier() == 3 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE,
                        this.getX() - v.x * f,
                        this.getY() - v.y * f,
                        this.getZ() - v.z * f,
                        0.0,
                        0.0,
                        0.0
                    );
            }

            if (this.tier() >= 2) {
                this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        } else if (this.tickCount > 300) {
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        // a shell flies clear of the gun's own wall, its gun ledge and parapet, and whatever is right at the muzzle;
        // hitting them would blow up on the gun itself every shot
        if (result instanceof BlockHitResult bhr && this.origin != null) {
            double flown = bhr.getLocation().distanceTo(this.origin);
            if (flown < 3.0
                || flown < 12.0
                    && (ShiganshinaLayout.isWall((double)bhr.getBlockPos().getX() + 0.5, (double)bhr.getBlockPos().getZ() + 0.5)
                        || com.takem.titans.world.Destruction.isWallStone(this.level().getBlockState(bhr.getBlockPos())))) {
                return;
            }
        }

        super.onHit(result);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            int tier;
            Vec3 at;
            tier = this.tier();
            Player credit = this.getOwner() instanceof Player p ? p : null;
            at = result.getLocation();
            float power = tier == 1 ? 80.0F : (tier == 2 ? 130.0F : 95.0F);
            label55:
            if (result instanceof EntityHitResult ehr) {
                Entity e = ehr.getEntity();
                if (e instanceof TitanPart part && part.getParent() != null) {
                    part.getParent().impactPart(part, power, credit, level.damageSources().explosion(this, this.getOwner()));
                    break label55;
                }

                if (e instanceof AbstractTitan t) {
                    t.blastHit(at, power, credit, level.damageSources().explosion(this, this.getOwner()));
                } else if (e instanceof TitanHeartEntity heart) {
                    heart.damage(power, credit);
                }
            }

            Boom.Spec s = new Boom.Spec().owner(this.getOwner(), this);
            switch (tier) {
                case 1:
                    s.radius(3.6).blocks(1.6, 3.0F).damage(14.0F, 25.0F).fx(4, 2.6F, 0.0F);
                    s.knock = 1.2;
                    s.volume = 3.5F;
                    s.sound = ModSounds.BOMBLET_POP.get();
                    break;
                case 2:
                    s.radius(7.5).blocks(3.8, 6.0F).damage(26.0F, 70.0F).fire(8).fx(4, 6.0F, 0.0F);
                    s.knock = 2.0;
                    s.volume = 6.0F;
                    break;
                default:
                    s.radius(6.5).blocks(3.2, 6.0F).damage(22.0F, 60.0F).fire(5).fx(4, 5.5F, 0.0F);
                    s.knock = 1.8;
                    s.volume = 5.5F;
                    Fx.send(level, 11, at, 24.0F, 0.0F, 256.0);
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(at.x, at.y, at.z);
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
            }

            if (ShiganshinaLayout.distToWall(at.x, at.z) < 3.0) {
                s.blockRadius = 0.0;
            }

            Boom.explode(level, at, s);
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Tier", this.tier());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(TIER, Math.max(1, tag.getInt("Tier")));
    }
}
