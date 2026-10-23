package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.WallTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Boom;
import com.takem.titans.world.TunnelDungeon;
import javax.annotation.Nullable;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public class TitanHeartEntity extends Entity {
    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(TitanHeartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_HP = SynchedEntityData.defineId(TitanHeartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(TitanHeartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(TitanHeartEntity.class, EntityDataSerializers.INT);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.15F), 2.0F);
    public int slot = -1;
    private int breedTimer = 260;
    public int flash;

    public TitanHeartEntity(EntityType<? extends TitanHeartEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static TitanHeartEntity tunnel(ServerLevel level, Vec3 at, int slot) {
        TitanHeartEntity h = new TitanHeartEntity(ModEntities.TITAN_HEART.get(), level);
        h.setPos(at);
        h.slot = slot;
        h.entityData.set(MAX_HP, 420.0F);
        h.entityData.set(HP, 420.0F);
        h.entityData.set(SIZE, 3.2F);
        return h;
    }

    public static TitanHeartEntity core(ServerLevel level, WallTitan boss, int slot, float hp) {
        TitanHeartEntity h = new TitanHeartEntity(ModEntities.TITAN_HEART.get(), level);
        h.setPos(boss.corePos(slot));
        h.slot = slot;
        h.entityData.set(OWNER, boss.getId());
        h.entityData.set(MAX_HP, hp);
        h.entityData.set(HP, hp);
        h.entityData.set(SIZE, 4.5F);
        return h;
    }

    @Override
    protected void defineSynchedData(Builder builder) {
        builder.define(HP, 300.0F);
        builder.define(MAX_HP, 300.0F);
        builder.define(SIZE, 3.0F);
        builder.define(OWNER, -1);
    }

    public boolean isCore() {
        return this.entityData.get(OWNER) >= 0;
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    public float health() {
        return this.entityData.get(HP);
    }

    public float healthFraction() {
        return this.entityData.get(HP) / Math.max(1.0F, this.entityData.get(MAX_HP));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.flash > 0) {
            this.flash--;
        }

        float s = this.size();
        if (this.level().isClientSide) {
            if (this.random.nextInt(2) == 0) {
                Vec3 d = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian()).normalize().scale((double)s * 0.5);
                this.level().addParticle(RED, this.getX() + d.x, this.getY() + (double)s * 0.5 + d.y, this.getZ() + d.z, 0.0, 0.0, 0.0);
            }

            if (this.tickCount % 3 == 0) {
                this.level().addParticle(ParticleTypes.CRIMSON_SPORE, this.getX(), this.getY() + (double)s * 0.5, this.getZ(), 0.0, 0.05, 0.0);
            }
        } else if (this.level() instanceof ServerLevel sl) {
            int owner = this.entityData.get(OWNER);
            if (owner >= 0) {
                if (!(sl.getEntity(owner) instanceof WallTitan boss) || !boss.isAlive()) {
                    this.discard();
                    return;
                }

                this.setPos(boss.corePos(this.slot));
            } else if (--this.breedTimer <= 0) {
                this.breedTimer = 420 + this.random.nextInt(200);
                TunnelDungeon.breed(sl, this);
            }

            if (this.tickCount % 24 == 0) {
                sl.playSound(
                    null,
                    this.getX(),
                    this.getY() + (double)s * 0.5,
                    this.getZ(),
                    ModSounds.HEART_BEAT.get(),
                    SoundSource.HOSTILE,
                    2.5F,
                    owner >= 0 ? 0.7F : 0.9F
                );
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isRemoved() && !this.isInvulnerableTo(source)) {
            float dmg = amount;
            Player by = source.getEntity() instanceof Player p ? p : null;
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                dmg = amount * 2.5F;
            } else if (by instanceof ServerPlayer sp && source.getDirectEntity() == sp) {
                double speed = OdmServer.data(sp).recentSpeed;
                dmg = amount * (float)Math.max(1.0, Math.min(8.0, 1.0 + 5.0 * Math.max(0.0, speed - 0.2)));
            }

            this.damage(dmg, by);
            return true;
        } else {
            return false;
        }
    }

    public void damage(float dmg, @Nullable Player by) {
        if (this.level() instanceof ServerLevel sl && !this.isRemoved()) {
            float hp = this.health() - dmg;
            this.entityData.set(HP, Math.max(0.0F, hp));
            this.flash = 6;
            float s = this.size();
            Vec3 c = this.position().add(0.0, (double)s * 0.5, 0.0);
            sl.sendParticles(
                ModParticles.BLOOD.get(), c.x, c.y, c.z, 14 + (int)Math.min(40.0F, dmg / 4.0F), (double)s * 0.3, (double)s * 0.3, (double)s * 0.3, 0.3
            );
            sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, (double)s * 0.3, (double)s * 0.3, (double)s * 0.3, 0.5);
            sl.playSound(null, c.x, c.y, c.z, ModSounds.HEART_HIT.get(), SoundSource.HOSTILE, 2.0F, 0.8F + this.random.nextFloat() * 0.3F);
            if (by instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(0, Math.round(dmg), 1, 0));
            }

            if (hp <= 0.0F) {
                this.shatter(sl, by);
            }
        }
    }

    private void shatter(ServerLevel sl, @Nullable Player by) {
        float s = this.size();
        Vec3 c = this.position().add(0.0, (double)s * 0.5, 0.0);
        sl.playSound(null, c.x, c.y, c.z, ModSounds.HEART_BURST.get(), SoundSource.HOSTILE, 8.0F, 0.8F);
        Fx.send(sl, 23, c.x, c.y, c.z, s * 2.0F, 0.0F, 0.0F, 0.0F, 256.0);
        Fx.send(sl, 7, c, s * 3.0F, 1.0F, 256.0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        sl.sendParticles(ModParticles.BIG_STEAM.get(), c.x, c.y, c.z, 60, (double)s, (double)s, (double)s, 0.15);
        AbstractTitan.shakeNearby(sl, c, 80.0, 0.8F, 20);
        Boom.Spec wave = new Boom.Spec().radius(9.0).damage(10.0F, 140.0F).fx(-1, 0.0F, 0.0F).owner(by, this);
        wave.glass = false;
        wave.collapse = false;
        wave.knock = 2.0;
        wave.volume = 5.0F;
        Boom.explode(sl, c, wave);
        sl.addFreshEntity(new ItemEntity(sl, c.x, c.y, c.z, new ItemStack(ModItems.TITAN_HEART_SHARD.get(), this.isCore() ? 1 : 2)));
        int owner = this.entityData.get(OWNER);
        if (owner >= 0) {
            if (sl.getEntity(owner) instanceof WallTitan boss) {
                boss.onCoreDestroyed(sl, this.slot, by);
            }
        } else {
            TunnelDungeon.onHeartDestroyed(sl, this);
        }

        this.discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 65536.0;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = this.size();
        return EntityDimensions.scalable(s * 0.8F, s);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
