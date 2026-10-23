package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TitanGibEntity extends Entity {
    public static final int LIFE = 900;
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(TitanGibEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> JOINT = SynchedEntityData.defineId(TitanGibEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(TitanGibEntity.class, EntityDataSerializers.FLOAT);
    public float pitch;
    public float pitchO;
    public float roll;
    public float rollO;
    private float spinP = (this.random.nextFloat() - 0.5F) * 30.0F;
    private float spinR = (this.random.nextFloat() - 0.5F) * 20.0F;
    private boolean landed;

    public TitanGibEntity(EntityType<? extends TitanGibEntity> type, Level level) {
        super(type, level);
    }

    public static TitanGibEntity of(ServerLevel level, AbstractTitan t, int joint, Vec3 at, Vec3 vel) {
        TitanGibEntity g = new TitanGibEntity(ModEntities.TITAN_GIB.get(), level);
        g.entityData.set(SKIN, skinOf(t));
        g.entityData.set(JOINT, joint);
        g.entityData.set(SCALE, t.getTitanScale());
        g.refreshDimensions();
        g.setPos(at.x, at.y, at.z);
        g.setDeltaMovement(vel);
        g.setYRot(t.getYRot() + (g.random.nextFloat() - 0.5F) * 60.0F);
        return g;
    }

    static String skinOf(AbstractTitan t) {
        String path = EntityType.getKey(t.getType()).getPath();
        String name = path.equals("rumbling_titan") ? "wall_titan" : path;

        int n = switch (name) {
            case "pure_titan" -> 6;
            case "abnormal_titan", "wall_titan" -> 3;
            case "crawler_titan", "armored_titan" -> 2;
            default -> 1;
        };
        return name + "_" + Math.floorMod(t.getVariant(), n);
    }

    @Override
    protected void defineSynchedData(Builder builder) {
        builder.define(SKIN, "pure_titan_0");
        builder.define(JOINT, 6);
        builder.define(SCALE, 2.0F);
    }

    public String skin() {
        return this.entityData.get(SKIN);
    }

    public int joint() {
        return this.entityData.get(JOINT);
    }

    public float scale() {
        return this.entityData.get(SCALE);
    }

    public float fade(float pt) {
        float left = 900.0F - ((float)this.tickCount + pt);
        return Math.max(0.0F, Math.min(1.0F, left / 220.0F));
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = this.scale();
        return EntityDimensions.scalable(Math.min(6.0F, s * 0.55F), Math.min(5.0F, s * 0.45F));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SCALE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        boolean wasGround = this.onGround();
        double speed = v.length();
        this.setDeltaMovement(v.add(0.0, -0.06, 0.0));
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.onGround()) {
            Vec3 nv = this.getDeltaMovement();
            this.setDeltaMovement(nv.x * 0.55, Math.abs(v.y) > 0.35 ? -v.y * 0.28 : 0.0, nv.z * 0.55);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.985));
        }

        if (this.level().isClientSide) {
            this.pitchO = this.pitch;
            this.rollO = this.roll;
            if (this.onGround() && !(speed > 0.12)) {
                float target = (float)Math.round((this.pitch - 90.0F) / 180.0F) * 180.0F + 90.0F;
                this.pitch = this.pitch + (target - this.pitch) * 0.3F;
                this.roll = this.roll + ((float)Math.round(this.roll / 180.0F) * 180.0F - this.roll) * 0.3F;
                this.spinP *= 0.6F;
                this.spinR *= 0.6F;
            } else {
                this.pitch = this.pitch + this.spinP;
                this.roll = this.roll + this.spinR;
            }

            float s = this.scale();
            if (speed > 0.1 || this.tickCount < 60) {
                for (int i = 0; i < 2 + (int)(s * 0.5F); i++) {
                    this.level()
                        .addParticle(
                            ModParticles.BLOOD.get(),
                            this.getX() + (this.random.nextDouble() - 0.5) * (double)s * 0.5,
                            this.getY() + this.random.nextDouble() * (double)s * 0.4,
                            this.getZ() + (this.random.nextDouble() - 0.5) * (double)s * 0.5,
                            (this.random.nextDouble() - 0.5) * 0.15,
                            -0.05,
                            (this.random.nextDouble() - 0.5) * 0.15
                        );
                }
            }

            if (this.tickCount > 120 && this.tickCount % (this.tickCount > 680 ? 2 : 9) == 0) {
                this.level()
                    .addParticle(
                        this.tickCount > 680 ? ModParticles.BIG_STEAM.get() : ModParticles.STEAM.get(),
                        this.getX() + (this.random.nextDouble() - 0.5) * (double)s * 0.6,
                        this.getY() + (double)s * 0.2,
                        this.getZ() + (this.random.nextDouble() - 0.5) * (double)s * 0.6,
                        0.0,
                        0.06,
                        0.0
                    );
            }
        } else if (this.level() instanceof ServerLevel sl) {
            if (!wasGround && this.onGround() && speed > 0.3) {
                this.impact(sl, speed);
            }

            if (this.tickCount > 900) {
                this.discard();
            }
        }
    }

    private void impact(ServerLevel sl, double speed) {
        float s = this.scale();
        Vec3 c = this.position();
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, (float)Math.min(4.0, 1.0 + (double)s * 0.4), 0.45F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, (float)Math.min(3.0, 0.8 + (double)s * 0.3), 0.5F);
        if (s > 2.0F) {
            AbstractTitan.shakeNearby(sl, c, 24.0 + (double)s * 4.0, Math.min(0.6F, s * 0.08F), 8);
        }

        int n = (int)Math.min(80.0, 12.0 + (double)s * 8.0 * speed);

        for (int i = 0; i < n; i++) {
            double a = this.random.nextDouble() * 3.141592653589793 * 2.0;
            double sp = 0.2 + this.random.nextDouble() * 0.6;
            sl.sendParticles(ModParticles.BLOOD.get(), c.x, c.y + 0.3, c.z, 0, Math.cos(a) * sp, 0.25 + this.random.nextDouble() * 0.35, Math.sin(a) * sp, 1.0);
        }

        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.3), ex -> !(ex instanceof AbstractTitan))) {
            e.hurt(sl.damageSources().fallingBlock(this), (float)((double)s * 3.0 * speed));
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 40000.0;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
