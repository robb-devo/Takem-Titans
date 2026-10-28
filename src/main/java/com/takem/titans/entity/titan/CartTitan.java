package com.takem.titans.entity.titan;

import com.takem.titans.entity.CannonballEntity;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Destruction;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Cart Titan, fitted out for war: a steel plate bolted over its nape and a battery of guns on its back. It never
 * stands still - it keeps its distance and shells you, and when you chase it down it turns and charges through the
 * houses between you. Blades only scratch the plate; spears and cannon fire break it, then the nape is open.
 */
public class CartTitan extends CrawlerTitan implements BossTitan {
    public static final float PLATE_MAX = 520.0F;
    private static final double SHELL_DRAG = 0.99;
    private static final double SHELL_GRAVITY = 0.03;
    private float plate = PLATE_MAX;
    private int gunCooldown = 60;
    private int volleyLeft;
    private int volleyTimer;
    private int chargeTicks;
    private int chargeCooldown = 120;
    private Vec3 chargeDir = Vec3.ZERO;
    private int clang;
    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("entity.takemtitans.cart_titan"), BossBarColor.GREEN, BossBarOverlay.NOTCHED_10
    );

    public CartTitan(EntityType<? extends CartTitan> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 8.0F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.4;
    }

    @Override
    public int variantCount() {
        return 1;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 40.0F * height;
    }

    @Override
    protected double detectionRange() {
        return 150.0;
    }

    @Override
    protected float turnRate() {
        return 14.0F;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected boolean isSluggish(ServerLevel level) {
        return false;
    }

    public boolean plated() {
        return this.plate > 0.0F;
    }

    private void chip(ServerLevel level, Vec3 at, float dmg) {
        this.plate -= dmg;
        level.sendParticles(ModParticles.SPARK.get(), at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.4);
        if (this.clang <= 0) {
            this.clang = 8;
            level.playSound(null, at.x, at.y, at.z, ModSounds.ARMOR_CLANG.get(), SoundSource.HOSTILE, 2.0F, 1.1F + this.random.nextFloat() * 0.3F);
        }

        if (this.plate <= 0.0F) {
            this.plate = 0.0F;
            this.setVariant(1);
            Vec3 c = this.skeletonPoint(3, TitanSkeleton.NAPE);
            level.playSound(null, c.x, c.y, c.z, ModSounds.ARMOR_SHATTER.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
            Fx.send(level, Fx.GLASS_BURST, c.x, c.y, c.z, 6.0F, 0.0F, 0.0F, 0.0F, 200.0);
            this.voice(ModSounds.ABNORMAL_SCREECH.get(), 6.0F, 0.8F, 40, 3);
            this.knockDown(level, 60);
            for (ServerPlayer p : this.bossBar.getPlayers()) {
                p.displayClientMessage(Component.translatable("message.takemtitans.cart_plate_broken").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
    }

    @Override
    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        if (this.plate > 0.0F && part.kind == 1 && this.level() instanceof ServerLevel sl) {
            float dmg = source.is(DamageTypeTags.IS_EXPLOSION) ? amount * 3.0F : amount * 0.35F;
            if (source.getEntity() instanceof ServerPlayer sp && source.getDirectEntity() == sp) {
                dmg *= (float)Mth.clamp(1.0 + 2.0 * OdmServer.data(sp).recentSpeed, 1.0, 4.0);
            }

            this.chip(sl, part.position().add(0.0, (double)part.getBbHeight() * 0.5, 0.0), dmg);
            return true;
        }

        return super.hurtPart(part, source, amount);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.plate > 0.0F && source.is(DamageTypeTags.IS_EXPLOSION) && this.level() instanceof ServerLevel sl) {
            this.chip(sl, this.skeletonPoint(3, TitanSkeleton.NAPE), amount * 2.0F);
        }

        return super.hurt(source, amount);
    }

    @Override
    public boolean traumaHit(float napeDamage, @Nullable Player credit, DamageSource source, boolean knock) {
        if (this.plate > 0.0F && this.level() instanceof ServerLevel sl) {
            this.chip(sl, this.skeletonPoint(3, TitanSkeleton.NAPE), napeDamage * 2.0F);
            if (this.plate > 0.0F) {
                return false;
            }
        }

        return super.traumaHit(napeDamage, credit, source, knock);
    }

    @Override
    public void onThunderSpear(Vec3 pos, @Nullable Entity owner) {
        if (this.plate > 0.0F && this.level() instanceof ServerLevel sl) {
            this.chip(sl, pos, 210.0F);
        } else {
            super.onThunderSpear(pos, owner);
        }
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        if (this.chargeCooldown <= 0 && dist > 18.0 && dist < 80.0 && this.onGround() && this.random.nextInt(3) == 0) {
            this.chargeTicks = 55;
            this.chargeCooldown = 200 + this.random.nextInt(80);
            Vec3 d = target.position().subtract(this.position());
            this.chargeDir = new Vec3(d.x, 0.0, d.z).normalize();
            this.voice(ModSounds.ABNORMAL_SCREECH.get(), 5.0F, 0.7F, 40, 2);
            this.attackCooldown = 20;
        } else if (this.gunCooldown <= 0 && this.volleyLeft == 0 && dist > 12.0 && dist < 120.0) {
            this.volleyLeft = this.plate > 0.0F ? 3 : 5;
            this.volleyTimer = 0;
            this.gunCooldown = 110 + this.random.nextInt(50);
            this.attackCooldown = 10;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CANNON_RELOAD.get(), SoundSource.HOSTILE, 3.0F, 0.8F);
        } else {
            super.chooseAttack(target, dist);
        }
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        if (this.chargeTicks > 0) {
            this.chargeTicks--;
            this.setFlag(8, true);
            this.walkVel = this.chargeDir.scale(this.chargeTicks > 48 ? 0.3 : 1.25);
            this.turnTowards(this.position().add(this.chargeDir.scale(10.0)), 30.0F);
            if (this.level() instanceof ServerLevel sl) {
                this.ram(sl);
            }

            if (this.chargeTicks == 0) {
                this.setFlag(8, false);
            }
        } else if (dist < 20.0 && this.volleyLeft > 0) {
            // keep the guns at range: back off while they fire
            this.setFlag(8, false);
            this.turnTowards(target.position(), this.turnRate());
            float yaw = this.getYRot() * 0.017453292F;
            this.walkVel = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw)).scale(this.walkSpeed(this.getTitanHeight()) * 0.8);
        } else {
            this.setFlag(8, false);
            super.chase(target, dist, sluggish);
        }
    }

    private void ram(ServerLevel level) {
        if (this.tickCount % 3 == 0) {
            Destruction.bodySlam(level, this.position(), this.chargeDir, 4.0, this.getBbWidth() * 0.6, true);
            level.sendParticles(ModParticles.DUST_CLOUD.get(), this.getX(), this.getY() + 0.5, this.getZ(), 5, 2.0, 0.3, 2.0, 0.05);
            shakeNearby(level, this.position(), 30.0, 0.3F, 6);
        }

        for (Entity e : level.getEntities(this, this.getBoundingBox().move(this.chargeDir.scale(2.0)).inflate(1.0), x -> x.isAlive() && !(x instanceof TitanPart))) {
            if (e instanceof FallingSectionEntity || e instanceof DebrisEntity || e instanceof AbstractTitan) {
                continue;
            }

            if (e instanceof LivingEntity l && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                l.setDeltaMovement(this.chargeDir.scale(2.4).add(0.0, 0.9, 0.0));
                l.hurtMarked = true;
                l.hurt(this.damageSources().mobAttack(this), 14.0F);
            }
        }
    }

    /** One mortar shell, lobbed to land where the target will be. Drag and gravity are solved exactly, so it lands true. */
    private void fire(ServerLevel level, LivingEntity target) {
        Vec3 muzzle = this.position().add(0.0, this.getBbHeight() + 4.0, 0.0);
        double dist = Math.sqrt(target.position().subtract(muzzle).horizontalDistanceSqr());
        int T = (int)Mth.clamp(dist / 1.5, 22.0, 60.0);
        double spread = 2.0 + dist * 0.04;
        Vec3 aim = target.position().add(target.getDeltaMovement().multiply(T * 0.7, 0.0, T * 0.7))
            .add((this.random.nextDouble() - 0.5) * spread * 2.0, 0.0, (this.random.nextDouble() - 0.5) * spread * 2.0);
        double s = (1.0 - Math.pow(SHELL_DRAG, T)) / (1.0 - SHELL_DRAG);
        Vec3 d = aim.subtract(muzzle);
        double vy = (d.y + SHELL_GRAVITY / (1.0 - SHELL_DRAG) * (T - s)) / s;
        Vec3 v = new Vec3(d.x / s, vy, d.z / s);
        CannonballEntity shell = new CannonballEntity(level, muzzle.x, muzzle.y, muzzle.z, this, 1);
        shell.setDeltaMovement(v);
        level.addFreshEntity(shell);
        Fx.send(level, Fx.MUZZLE, muzzle.x, muzzle.y, muzzle.z, (float)v.x, (float)v.y, (float)v.z, 0.0F, 200.0);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.CANNON_FIRE.get(), SoundSource.HOSTILE, 5.0F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level) || this.isDeadOrDying()) {
            return;
        }

        if (this.gunCooldown > 0) {
            this.gunCooldown--;
        }

        if (this.chargeCooldown > 0) {
            this.chargeCooldown--;
        }

        if (this.clang > 0) {
            this.clang--;
        }

        LivingEntity target = this.getTarget();
        if (this.volleyLeft > 0 && --this.volleyTimer <= 0) {
            if (target != null && !this.isDowned()) {
                this.fire(level, target);
                this.volleyLeft--;
                this.volleyTimer = 7;
            } else {
                this.volleyLeft = 0;
            }
        }

        if (this.plate > 0.0F && this.tickCount % 10 == 0) {
            Vec3 n = this.skeletonPoint(3, TitanSkeleton.NAPE);
            level.sendParticles(ParticleTypes.SMOKE, n.x, n.y + 1.0, n.z, 2, 0.6, 0.2, 0.6, 0.01);
        }

        this.bossBar.setProgress(this.plate > 0.0F ? this.plate / PLATE_MAX : Mth.clamp(this.napeHealth / this.maxNapeHealth(), 0.0F, 1.0F));
        this.bossBar.setColor(this.plate > 0.0F ? BossBarColor.GREEN : BossBarColor.RED);
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
        tag.putFloat("Plate", this.plate);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Plate")) {
            this.plate = tag.getFloat("Plate");
        }

        this.setVariant(this.plate > 0.0F ? 0 : 1);
    }
}
