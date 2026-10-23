package com.takem.titans.entity.titan;

import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import javax.annotation.Nullable;
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

public class ArmoredTitan extends AbstractTitan {
    public static final float ARMOR_MAX = 900.0F;
    private float armor = 900.0F;
    private int chargeTicks;
    private int chargeCooldown = 100;
    private Vec3 chargeDir = Vec3.ZERO;
    private int clang;
    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("entity.takemtitans.armored_titan"), BossBarColor.WHITE, BossBarOverlay.NOTCHED_10
    );

    public ArmoredTitan(EntityType<? extends ArmoredTitan> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 15.0F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.1 + (double)height * 0.018;
    }

    @Override
    public int titanKind() {
        return 0;
    }

    @Override
    public int variantCount() {
        return 1;
    }

    public boolean armored() {
        return this.armor > 0.0F;
    }

    @Override
    public float headScale() {
        return 0.95F;
    }

    @Override
    public float headTilt() {
        return 0.3F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 22.0F * height;
    }

    @Override
    protected double detectionRange() {
        return 140.0;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    private float chipFrom(DamageSource src, float amount) {
        if (src.getEntity() instanceof ServerPlayer sp && src.getDirectEntity() == sp) {
            double speed = OdmServer.data(sp).recentSpeed;
            return amount * (float)Mth.clamp(1.0 + 5.0 * Math.max(0.0, speed - 0.2), 1.0, 9.0) * 0.6F;
        }

        return amount * 2.0F;
    }

    private void chip(ServerLevel level, Vec3 at, float dmg) {
        this.armor -= dmg;
        level.sendParticles(ModParticles.SPARK.get(), at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.1);
        if (this.clang <= 0) {
            this.clang = 10;
            level.playSound(null, at.x, at.y, at.z, ModSounds.ARMOR_CLANG.get(), SoundSource.HOSTILE, 2.0F, 0.8F + this.random.nextFloat() * 0.3F);
        }

        if (this.armor <= 0.0F) {
            this.shatter(level);
        }
    }

    private void shatter(ServerLevel level) {
        this.armor = 0.0F;
        this.setVariant(1);
        Vec3 c = this.position().add(0.0, (double)this.getBbHeight() * 0.6, 0.0);
        level.playSound(null, c.x, c.y, c.z, ModSounds.ARMOR_SHATTER.get(), SoundSource.HOSTILE, 8.0F, 0.8F);
        this.voice(ModSounds.TITAN_ROAR.get(), 8.0F, 0.55F, 60, 3);
        Fx.send(level, 23, c.x, c.y, c.z, this.getBbHeight(), 0.0F, 0.0F, 0.0F, 256.0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 3.0, 5.0, 3.0, 0.4);
        level.sendParticles(ModParticles.BIG_STEAM.get(), c.x, c.y, c.z, 60, 3.0, 5.0, 3.0, 0.08);
        shakeNearby(level, c, 80.0, 0.8F, 20);

        for (ServerPlayer p : this.bossBar.getPlayers()) {
            p.displayClientMessage(Component.translatable("message.takemtitans.armor_broken"), true);
        }
    }

    @Override
    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        if (!this.level().isClientSide && !(this.armor <= 0.0F) && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            this.chip((ServerLevel)this.level(), part.position().add(0.0, (double)part.getBbHeight() * 0.5, 0.0), this.chipFrom(source, amount));
            return true;
        } else if (this.armor > 0.0F && this.level() instanceof ServerLevel sl) {
            this.chip(sl, part.position(), amount * 3.0F);
            return true;
        } else {
            return super.hurtPart(part, source, amount);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.armor > 0.0F && this.level() instanceof ServerLevel sl && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (source.getEntity() instanceof Player || source.is(DamageTypeTags.IS_EXPLOSION)) {
                this.chip(
                    sl,
                    this.position().add(0.0, (double)this.getBbHeight() * 0.5, 0.0),
                    source.is(DamageTypeTags.IS_EXPLOSION) ? amount * 3.0F : this.chipFrom(source, amount)
                );
            }

            return false;
        } else {
            return super.hurt(source, amount);
        }
    }

    @Override
    public void onThunderSpear(Vec3 pos, @Nullable Entity owner) {
        if (this.armor > 0.0F && this.level() instanceof ServerLevel sl) {
            this.chip(sl, pos, 170.0F);
        } else {
            super.onThunderSpear(pos, owner);
        }
    }

    @Override
    public boolean traumaHit(float napeDamage, @Nullable Player credit, DamageSource source, boolean knock) {
        if (this.armor > 0.0F && this.level() instanceof ServerLevel sl) {
            this.chip(sl, this.position().add(0.0, (double)this.getBbHeight() * 0.6, 0.0), napeDamage * 2.5F);
            if (this.armor > 0.0F) {
                return false;
            }
        }

        return super.traumaHit(napeDamage, credit, source, knock);
    }

    @Override
    public void slammed(ServerLevel level, float strength) {
        if (this.armor > 0.0F) {
            this.chip(level, this.position().add(0.0, 2.0, 0.0), 260.0F * strength);
        }

        super.slammed(level, strength);
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        if (this.chargeCooldown <= 0 && dist > 16.0 && dist < 90.0 && this.onGround()) {
            this.chargeTicks = 70;
            this.chargeCooldown = 220 + this.random.nextInt(100);
            Vec3 d = target.position().subtract(this.position());
            this.chargeDir = new Vec3(d.x, 0.0, d.z).normalize();
            this.voice(ModSounds.TITAN_ROAR.get(), 7.0F, 0.6F, 50, 2);
            this.setAction(TitanAction.ROAR, target);
            this.attackCooldown = 20;
        } else {
            super.chooseAttack(target, dist);
        }
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        if (this.chargeTicks > 0) {
            this.chargeTicks--;
            this.setFlag(8, true);
            this.walkVel = this.chargeDir.scale(this.chargeTicks > 60 ? 0.3 : 0.95);
            this.turnTowards(this.position().add(this.chargeDir.scale(10.0)), 20.0F);
            if (this.level() instanceof ServerLevel sl) {
                this.ram(sl);
            }

            if (this.chargeTicks == 0) {
                this.setAction(TitanAction.FLINCH, null);
            }
        } else {
            super.chase(target, dist, sluggish);
        }
    }

    private void ram(ServerLevel level) {
        if (this.tickCount % 4 == 0) {
            shakeNearby(level, this.position(), 40.0, 0.35F, 6);
            level.sendParticles(ModParticles.DUST_CLOUD.get(), this.getX(), this.getY() + 0.5, this.getZ(), 6, 3.0, 0.3, 3.0, 0.05);
        }

        for (Entity e : level.getEntities(
            this, this.getBoundingBox().move(this.chargeDir.scale(2.0)).inflate(1.5), x -> x.isAlive() && !(x instanceof TitanPart)
        )) {
            if (!(e instanceof AbstractTitan) && !(e instanceof FallingSectionEntity) && !(e instanceof DebrisEntity)) {
                e.setDeltaMovement(this.chargeDir.scale(2.2).add(0.0, 0.9, 0.0));
                e.hurtMarked = true;
                if (e instanceof LivingEntity) {
                    LivingEntity l = (LivingEntity)e;
                    if (e instanceof Player) {
                        Player p = (Player)e;
                        if (p.isCreative()) {
                            continue;
                        }
                    }

                    l.hurt(this.damageSources().mobAttack(this), 14.0F);
                }
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.chargeCooldown > 0) {
            this.chargeCooldown--;
        }

        if (this.clang > 0) {
            this.clang--;
        }

        this.bossBar.setProgress(this.armor > 0.0F ? this.armor / 900.0F : Mth.clamp(this.napeHealth / this.maxNapeHealth(), 0.0F, 1.0F));
        this.bossBar.setColor(this.armor > 0.0F ? BossBarColor.WHITE : BossBarColor.RED);
        if (this.armor > 0.0F && this.tickCount % 6 == 0 && this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.WHITE_ASH, this.getX(), this.getY() + (double)this.getBbHeight() * 0.5, this.getZ(), 4, 2.0, 4.0, 2.0, 0.0);
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
        tag.putFloat("Armor", this.armor);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Armor")) {
            this.armor = tag.getFloat("Armor");
        }
    }
}
