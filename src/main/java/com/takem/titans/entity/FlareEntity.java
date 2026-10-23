package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbnormalTitan;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.item.FlareColor;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModBlocks;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.network.PacketDistributor;

public class FlareEntity extends Projectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(FlareEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BURST = SynchedEntityData.defineId(FlareEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int BURN_TICKS = 46;
    public static final int LINGER_TICKS = 40;
    private int age;

    public FlareEntity(EntityType<? extends FlareEntity> type, Level level) {
        super(type, level);
    }

    public FlareEntity(Level level, LivingEntity owner, FlareColor color) {
        this(ModEntities.FLARE.get(), level);
        this.setOwner(owner);
        this.entityData.set(COLOR, color.ordinal());
    }

    @Override
    protected void defineSynchedData(Builder builder) {
        builder.define(COLOR, 0);
        builder.define(BURST, false);
    }

    public FlareColor color() {
        return FlareColor.byIndex(this.entityData.get(COLOR));
    }

    public boolean hasBurst() {
        return this.entityData.get(BURST);
    }

    public int age() {
        return this.age;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        Vec3 v = this.getDeltaMovement();
        if (!this.hasBurst()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, x$0 -> this.canHitEntity(x$0));
            if (hit.getType() == Type.BLOCK) {
                this.setPos(hit.getLocation());
                this.setDeltaMovement(Vec3.ZERO);
                if (!this.level().isClientSide) {
                    this.burst();
                }
            } else {
                this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
                this.setDeltaMovement(v.scale(0.985).add(0.0, -0.018, 0.0));
            }

            this.updateRotation();
            if (this.level().isClientSide) {
                this.trailParticles();
            } else if (this.age >= 46) {
                this.burst();
            }
        } else {
            this.setDeltaMovement(v.scale(0.8));
            this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
            if (!this.level().isClientSide && this.age > 86) {
                this.discard();
            }
        }

        if (this.age > 400) {
            this.discard();
        }
    }

    private void trailParticles() {
        int rgb = this.color().rgb;
        double r = (double)(rgb >> 16 & 0xFF) / 255.0;
        double g = (double)(rgb >> 8 & 0xFF) / 255.0;
        double b = (double)(rgb & 0xFF) / 255.0;
        Vec3 v = this.getDeltaMovement();

        for (int i = 0; i < 3; i++) {
            double t = (double)i / 3.0;
            this.level()
                .addAlwaysVisibleParticle(ModParticles.FLARE_SMOKE.get(), true, this.getX() - v.x * t, this.getY() - v.y * t, this.getZ() - v.z * t, r, g, b);
        }

        if (this.random.nextInt(2) == 0) {
            this.level()
                .addParticle(
                    ParticleTypes.FIREWORK, this.getX(), this.getY(), this.getZ(), this.random.nextGaussian() * 0.03, -0.05, this.random.nextGaussian() * 0.03
                );
        }
    }

    private void burst() {
        if (!this.hasBurst()) {
            this.entityData.set(BURST, true);
            this.age = 46;
            ServerLevel level = (ServerLevel)this.level();
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.FLARE_BURN.get(), SoundSource.PLAYERS, 6.0F, 1.0F);
            FlareColor color = this.color();
            Vec3 pos = this.position();
            switch (color) {
                case RED:
                    revealTitans(level, pos, 128.0, false, 400);
                    break;
                case BLACK:
                    revealTitans(level, pos, 200.0, true, 600);
                    break;
                case GREEN:
                    for (ServerPlayer p : level.players()) {
                        if (p.distanceToSqr(pos) < 160000.0) {
                            PacketDistributor.sendToPlayer(p, FeedbackPayload.of(4, (int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z)));
                        }
                    }
                    break;
                case YELLOW:
                    level.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 120, 2.0, 2.0, 2.0, 0.25);
                    level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);

                    for (Player p : level.getEntitiesOfClass(Player.class, new AABB(pos, pos).inflate(48.0, 96.0, 48.0))) {
                        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
                    }
                    break;
                case PURPLE:
                    this.dropSupplies(level, pos);
            }
        }
    }

    private static void revealTitans(ServerLevel level, Vec3 pos, double radius, boolean abnormalOnly, int ticks) {
        AABB box = new AABB(pos, pos).inflate(radius, 160.0, radius);

        for (AbstractTitan titan : level.getEntitiesOfClass(AbstractTitan.class, box)) {
            if (!abnormalOnly || titan instanceof AbnormalTitan) {
                titan.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
            }
        }
    }

    private void dropSupplies(ServerLevel level, Vec3 pos) {
        if (this.getOwner() instanceof ServerPlayer owner) {
            OdmPlayerData data = OdmServer.data(owner);
            long now = level.getGameTime();
            if (now < data.supplyDropReadyAt) {
                owner.displayClientMessage(Component.translatable("message.takemtitans.supply_cooldown", (data.supplyDropReadyAt - now) / 20L), true);
                return;
            }

            data.supplyDropReadyAt = now + 3600L;
        }

        BlockPos ground = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(pos));
        BlockPos drop = ground.above(Math.min(40, Math.max(12, (int)(pos.y - (double)ground.getY()) - 4)));
        if (level.getBlockState(drop).isAir()) {
            FallingBlockEntity crate = FallingBlockEntity.fall(level, drop, ModBlocks.SUPPLY_STATION.get().defaultBlockState());
            crate.setHurtsEntities(1.0F, 10);
        }
    }

    @Override
    protected void onHit(HitResult result) {
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 262144.0;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Color", this.entityData.get(COLOR));
        tag.putInt("Age", this.age);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(COLOR, tag.getInt("Color"));
        this.age = tag.getInt("Age");
    }
}
