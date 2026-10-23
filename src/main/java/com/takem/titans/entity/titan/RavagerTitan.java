package com.takem.titans.entity.titan;

import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.TownLife;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class RavagerTitan extends AbstractTitan {
    private int rampageTicks;
    private int rampageCooldown = 120;
    private int quakeCooldown = 200;
    private Vec3 rampageDir = Vec3.ZERO;
    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("entity.takemtitans.ravager_titan"), BossBarColor.RED, BossBarOverlay.NOTCHED_12
    );

    public RavagerTitan(EntityType<? extends RavagerTitan> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 22.0F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.1 + (double)height * 0.016;
    }

    @Override
    public int titanKind() {
        return 1;
    }

    @Override
    public int variantCount() {
        return 1;
    }

    @Override
    public float headScale() {
        return 1.1F;
    }

    @Override
    public float headTilt() {
        return 1.4F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 34.0F * height;
    }

    @Override
    protected float limbThreshold() {
        return this.maxNapeHealth() * 0.3F;
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
    protected void chooseAttack(LivingEntity target, double dist) {
        if (this.rampageCooldown <= 0 && dist > 14.0 && dist < 110.0 && this.onGround()) {
            this.rampageTicks = 90;
            this.rampageCooldown = 200 + this.random.nextInt(120);
            Vec3 d = target.position().subtract(this.position());
            this.rampageDir = new Vec3(d.x, 0.0, d.z).normalize();
            this.voice(ModSounds.ABNORMAL_SCREECH.get(), 9.0F, 0.55F, 60, 3);
            this.setAction(TitanAction.ROAR, target);
            this.attackCooldown = 20;
        } else if (this.quakeCooldown <= 0 && dist < 40.0 && this.onGround() && this.level() instanceof ServerLevel sl) {
            this.quakeCooldown = 260 + this.random.nextInt(120);
            this.quake(sl);
            this.attackCooldown = 30;
        } else {
            super.chooseAttack(target, dist);
        }
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        if (this.rampageTicks > 0) {
            this.rampageTicks--;
            this.setFlag(8, true);
            if (this.rampageTicks % 25 == 0) {
                Vec3 d = target.position().subtract(this.position());
                Vec3 want = new Vec3(d.x, 0.0, d.z).normalize();
                this.rampageDir = this.rampageDir.lerp(want, 0.45).normalize();
            }

            this.walkVel = this.rampageDir.scale(this.rampageTicks > 75 ? 0.3 : 1.15);
            this.turnTowards(this.position().add(this.rampageDir.scale(10.0)), 14.0F);
            if (this.level() instanceof ServerLevel sl) {
                this.plough(sl);
            }
        } else {
            super.chase(target, dist, sluggish);
        }
    }

    private void plough(ServerLevel level) {
        if (this.tickCount % 3 == 0) {
            shakeNearby(level, this.position(), 60.0, 0.45F, 6);
            level.sendParticles(ModParticles.DUST_CLOUD.get(), this.getX(), this.getY() + 0.5, this.getZ(), 8, 4.0, 0.4, 4.0, 0.06);
            Destruction.bodySlam(level, this.position(), this.rampageDir, 5.0, 4.5, true);
        }

        if (this.tickCount % 10 == 0) {
            TownLife.alarm(level, this.position(), 60.0, 2);
        }

        for (Entity e : level.getEntities(
            this, this.getBoundingBox().move(this.rampageDir.scale(3.0)).inflate(2.0), x -> x.isAlive() && !(x instanceof TitanPart)
        )) {
            if (e instanceof FallingSectionEntity fs) {
                fs.shove(this.rampageDir.scale(1.6).add(0.0, 0.8, 0.0));
            } else if (!(e instanceof AbstractTitan) && !(e instanceof DebrisEntity)) {
                e.setDeltaMovement(this.rampageDir.scale(2.6).add(0.0, 1.0, 0.0));
                e.hurtMarked = true;
                if (e instanceof LivingEntity) {
                    LivingEntity l = (LivingEntity)e;
                    if (e instanceof Player) {
                        Player p = (Player)e;
                        if (p.isCreative()) {
                            continue;
                        }
                    }

                    l.hurt(this.damageSources().mobAttack(this), 16.0F);
                }
            }
        }
    }

    private void quake(ServerLevel level) {
        Vec3 c = this.position();
        this.voice(ModSounds.TITAN_ROAR.get(), 9.0F, 0.5F, 60, 3);
        level.playSound(null, c.x, c.y, c.z, ModSounds.RUMBLING_QUAKE.get(), SoundSource.HOSTILE, 8.0F, 0.8F);
        Fx.send(level, 2, c, 46.0F, 1.6F, 2.0F, 0.0F, 300.0);
        Fx.send(level, 22, c.x, c.y, c.z, 40.0F, 6.0F, 0.0F, 0.0F, 300.0);
        shakeNearby(level, c, 120.0, 1.1F, 40);
        TownLife.alarm(level, c, 90.0, 0);
        RandomSource r = this.random;

        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double d = 10.0 + r.nextDouble() * 26.0;
            Destruction.requestCollapse(level, BlockPos.containing(c.x + Math.cos(a) * d, c.y + 2.0, c.z + Math.sin(a) * d), 14, 64, 4);
        }

        for (Entity e : level.getEntities(this, this.getBoundingBox().inflate(30.0, 6.0, 30.0), x -> x.isAlive() && x.onGround())) {
            if (!(e instanceof AbstractTitan) && !(e instanceof TitanPart)) {
                double d = e.position().distanceTo(c);
                double f = Mth.clamp(1.0 - d / 30.0, 0.0, 1.0);
                e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.6 + f, 0.0));
                e.hurtMarked = true;
                if (e instanceof LivingEntity) {
                    LivingEntity l = (LivingEntity)e;
                    if (e instanceof Player) {
                        Player p = (Player)e;
                        if (p.isCreative()) {
                            continue;
                        }
                    }

                    l.hurt(this.damageSources().mobAttack(this), (float)(4.0 + 10.0 * f));
                }
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.rampageCooldown > 0) {
            this.rampageCooldown--;
        }

        if (this.quakeCooldown > 0) {
            this.quakeCooldown--;
        }

        this.bossBar.setProgress(Mth.clamp(this.napeHealthLeft() / this.maxNapeHealth(), 0.0F, 1.0F));
        if (this.tickCount % 5 == 0 && this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ModParticles.STEAM.get(), this.getX(), this.getY() + (double)this.getBbHeight() * 0.8, this.getZ(), 2, 2.0, 2.0, 2.0, 0.02);
        }
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
        tag.putInt("RampageCd", this.rampageCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.rampageCooldown = tag.getInt("RampageCd");
    }
}
