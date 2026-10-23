package com.takem.titans.entity.titan;

import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ColossalTitan extends AbstractTitan {
    private int steamCooldown = 100;
    private int vanishIn = -1;

    public ColossalTitan(EntityType<? extends ColossalTitan> type, Level level) {
        super(type, level);
    }

    public static Builder createColossalAttributes() {
        return createTitanAttributes().add(Attributes.MAX_HEALTH, 3000.0).add(Attributes.ARMOR, 10.0);
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 62.0F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.06;
    }

    @Override
    public int titanKind() {
        return 3;
    }

    @Override
    public float headTilt() {
        return 0.1F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 360.0F;
    }

    @Override
    protected float limbThreshold() {
        return 160.0F;
    }

    @Override
    protected double detectionRange() {
        return 96.0;
    }

    @Override
    protected float turnRate() {
        return 1.5F;
    }

    @Override
    protected float widthFactor() {
        return 0.6F;
    }

    @Override
    public int evaporateTicks() {
        return 500;
    }

    @Override
    protected boolean isSluggish(ServerLevel level) {
        return false;
    }

    public void setVanishIn(int ticks) {
        this.vanishIn = ticks;
    }

    public void serumRise(int stayTicks) {
        this.hold(stayTicks);
        this.setVanishIn(stayTicks);
        this.setAction(TitanAction.ROAR, null);
    }

    public void startKick() {
        this.setAction(TitanAction.KICK, null);
    }

    public void hold(int ticks) {
        this.attackCooldown = Math.max(this.attackCooldown, ticks);
        this.steamCooldown = Math.max(this.steamCooldown, ticks);
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        this.turnTowards(target.position(), this.turnRate());
        this.walkVel = Vec3.ZERO;
    }

    @Override
    protected void wander(ServerLevel level, boolean sluggish) {
        this.walkVel = Vec3.ZERO;
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
        boolean nearNape = false;

        for (Player p : this.level().players()) {
            if (this.canTarget(p) && p.position().distanceTo(nape) < 22.0) {
                nearNape = true;
            }
        }

        if (nearNape && this.steamCooldown <= 0) {
            this.setAction(TitanAction.STEAM, null);
            this.steamCooldown = 220 + this.random.nextInt(120);
            this.attackCooldown = 40;
            this.level().playSound(null, nape.x, nape.y, nape.z, ModSounds.COLOSSAL_STEAM.get(), SoundSource.HOSTILE, 8.0F, 0.8F);
        } else if (this.trySwat(target)) {
            this.attackCooldown = 50 + this.random.nextInt(30);
        } else {
            this.tryStomp(target);
        }
    }

    @Override
    protected boolean tryGrab(LivingEntity target) {
        return false;
    }

    @Override
    protected void tickAction(ServerLevel level, TitanAction action, @Nullable LivingEntity target) {
        if (action == TitanAction.STEAM) {
            float k = (float)this.actionTicks / (float)action.duration;
            if (k > 0.15F && k < 0.85F && this.actionTicks % 2 == 0) {
                Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
                Vec3 chest = this.skeletonPoint(2, TitanSkeleton.TORSO_CENTER);
                this.far(level, ModParticles.BIG_STEAM.get(), nape.x, nape.y, nape.z, 14, 4.0, 3.0, 4.0, 0.25);
                this.far(level, ModParticles.BIG_STEAM.get(), chest.x, chest.y, chest.z, 10, 6.0, 8.0, 6.0, 0.2);

                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(nape, nape).inflate(26.0))) {
                    if (!(e instanceof AbstractTitan)) {
                        if (e instanceof Player) {
                            Player p = (Player)e;
                            if (p.isCreative() || p.isSpectator()) {
                                continue;
                            }
                        }

                        Vec3 away = e.position().subtract(nape);
                        double d = away.length();
                        if (!(d > 26.0)) {
                            Vec3 push = away.normalize().scale(0.35 * (1.0 - d / 28.0));
                            e.push(push.x, push.y + 0.05, push.z);
                            e.hurtMarked = true;
                            if (this.actionTicks % 10 == 0 && d < 16.0) {
                                e.hurt(level.damageSources().inFire(), 3.0F);
                            }
                        }
                    }
                }
            }
        }

        super.tickAction(level, action, target);
    }

    @Override
    protected void customServerAiStep() {
        if (this.steamCooldown > 0) {
            this.steamCooldown--;
        }

        super.customServerAiStep();
        if (this.vanishIn > 0 && --this.vanishIn == 0 && this.level() instanceof ServerLevel sl) {
            Vec3 c = this.skeletonPoint(2, TitanSkeleton.TORSO_CENTER);
            this.far(sl, ModParticles.BIG_STEAM.get(), c.x, c.y, c.z, 400, 10.0, 25.0, 10.0, 0.3);
            sl.playSound(null, c.x, c.y, c.z, ModSounds.COLOSSAL_STEAM.get(), SoundSource.HOSTILE, 10.0F, 0.6F);
            this.discard();
        }
    }

    private void far(ServerLevel level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) < 90000.0) {
                level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VanishIn", this.vanishIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("VanishIn")) {
            this.vanishIn = tag.getInt("VanishIn");
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
            this.discard();
        }
    }
}
