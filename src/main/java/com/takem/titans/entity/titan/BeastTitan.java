package com.takem.titans.entity.titan;

import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.BoulderBarrage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public class BeastTitan extends AbstractTitan {
    private int throwCooldown = 80;
    private int summonCooldown = 300;
    private int volley;
    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("entity.takemtitans.beast_titan"), BossBarColor.YELLOW, BossBarOverlay.NOTCHED_10
    );

    public BeastTitan(EntityType<? extends BeastTitan> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 17.0F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.08 + (double)height * 0.014;
    }

    @Override
    public int titanKind() {
        return 0;
    }

    @Override
    public int variantCount() {
        return 1;
    }

    @Override
    public float headScale() {
        return 0.85F;
    }

    @Override
    public float headTilt() {
        return 0.2F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 30.0F * height;
    }

    @Override
    protected float limbThreshold() {
        return this.maxNapeHealth() * 0.35F;
    }

    @Override
    protected double detectionRange() {
        return 160.0;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        if (dist < 26.0 && target.onGround()) {
            Vec3 away = this.position().subtract(target.position());
            away = new Vec3(away.x, 0.0, away.z).normalize();
            this.turnTowards(target.position(), this.turnRate());
            this.walkVel = away.scale(this.walkSpeed(this.getTitanHeight()) * 0.5);
            this.setFlag(8, false);
        } else if (dist < 80.0) {
            this.walkVel = Vec3.ZERO;
            this.turnTowards(target.position(), this.turnRate());
        } else {
            super.chase(target, dist, sluggish);
        }
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        if (this.summonCooldown <= 0 && dist < 120.0) {
            this.summonCooldown = 500 + this.random.nextInt(200);
            this.summon((ServerLevel)this.level());
            this.setAction(TitanAction.ROAR, target);
            this.attackCooldown = 40;
        } else if (this.throwCooldown <= 0 && dist > 14.0 && dist < 140.0) {
            this.throwCooldown = 110 + this.random.nextInt(60);
            this.volley = 1 + (dist > 60.0 ? 2 : this.random.nextInt(2));
            this.setAction(this.random.nextBoolean() ? TitanAction.SWAT_RIGHT : TitanAction.SWAT_LEFT, target);
            this.attackCooldown = 30;
        } else {
            super.chooseAttack(target, dist);
        }
    }

    @Override
    protected void tickAction(ServerLevel level, TitanAction action, LivingEntity target) {
        if ((action == TitanAction.SWAT_RIGHT || action == TitanAction.SWAT_LEFT) && this.volley > 0 && target != null) {
            if (this.actionTicks == action.duration / 2) {
                Vec3 hand = this.handPos(action.side());

                for (int i = 0; i < this.volley; i++) {
                    Vec3 lead = target.position().add(target.getDeltaMovement().scale(18.0));
                    Vec3 hit = lead.add((this.random.nextDouble() - 0.5) * 10.0 * (double)i, 0.0, (this.random.nextDouble() - 0.5) * 10.0 * (double)i);
                    BoulderBarrage.throwAt(level, hand.add(0.0, 2.0, 0.0), hit, this, 1.6F + this.random.nextFloat() * 1.2F, 3);
                }

                level.playSound(null, hand.x, hand.y, hand.z, ModSounds.BOULDER_THROW.get(), SoundSource.HOSTILE, 6.0F, 0.7F);
                this.volley = 0;
            }

            if (this.actionTicks < action.duration) {
                this.turnTowards(target.position(), this.turnRate());
                return;
            }
        }

        super.tickAction(level, action, target);
    }

    private void summon(ServerLevel level) {
        this.voice(ModSounds.BEAST_ROAR.get(), 10.0F, 0.9F, 80, 3);
        shakeNearby(level, this.position(), 90.0, 0.5F, 30);
        int near = level.getEntitiesOfClass(AbstractTitan.class, this.getBoundingBox().inflate(70.0)).size();

        for (int i = 0; i < Math.min(3, 9 - near); i++) {
            double a = this.random.nextDouble() * 3.141592653589793 * 2.0;
            double d = 18.0 + this.random.nextDouble() * 18.0;
            int x = Mth.floor(this.getX() + Math.cos(a) * d);
            int z = Mth.floor(this.getZ() + Math.sin(a) * d);
            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            AbstractTitan t = (this.random.nextInt(4) == 0 ? ModEntities.ABNORMAL_TITAN.get() : ModEntities.PURE_TITAN.get()).create(level);
            if (t != null) {
                t.moveTo((double)x + 0.5, (double)y, (double)z + 0.5, this.random.nextFloat() * 360.0F, 0.0F);
                t.finalizeSpawn(level, level.getCurrentDifficultyAt(t.blockPosition()), MobSpawnType.EVENT, null);
                level.addFreshEntity(t);
                level.sendParticles(ModParticles.BIG_STEAM.get(), (double)x + 0.5, (double)(y + 2), (double)z + 0.5, 40, 2.0, 3.0, 2.0, 0.06);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo((double)x + 0.5, (double)y, (double)z + 0.5);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.throwCooldown > 0) {
            this.throwCooldown--;
        }

        if (this.summonCooldown > 0) {
            this.summonCooldown--;
        }

        this.bossBar.setProgress(Mth.clamp(this.napeHealth / this.maxNapeHealth(), 0.0F, 1.0F));
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ThrowCd", this.throwCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.throwCooldown = tag.getInt("ThrowCd");
    }
}
