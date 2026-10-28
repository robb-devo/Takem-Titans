package com.takem.titans.entity.titan;

import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.TitanHeartEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.CrystalSpikes;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The War Hammer Titan. The body is a puppet: its shifter lies in a crystal cocoon in the ground behind it, tied to the
 * nape by a glowing cord, and the nape cannot be cut while the cocoon holds. It fights from range - lines of crystal
 * spikes ripping up the street toward you, a hammer forged out of the air and dropped on your head, a crown of spikes
 * when you get under it. Break the cocoon and the body drops with its nape bare.
 */
public class WarHammerTitan extends AbstractTitan implements BossTitan, CoreHost {
    private static final BlockState[] CRYSTAL = {
        Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState()
    };
    private static final float CORE_HP = 420.0F;
    private boolean coreBroken;
    private int spikeCooldown = 60;
    private int hammerCooldown = 160;
    private int ringCooldown = 100;
    private boolean spikeLine;
    @Nullable
    private Vec3 hammerAt;
    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("entity.takemtitans.war_hammer_titan"), BossBarColor.WHITE, BossBarOverlay.NOTCHED_10
    );

    public WarHammerTitan(EntityType<? extends WarHammerTitan> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 15.0F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.08 + (double)height * 0.012;
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
        return 0.92F;
    }

    @Override
    protected float widthFactor() {
        return 0.9F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 14.0F * height;
    }

    @Override
    protected double detectionRange() {
        return 160.0;
    }

    @Override
    protected float turnRate() {
        return 9.0F;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected boolean isSluggish(ServerLevel level) {
        return false;
    }

    public boolean sealed() {
        return !this.coreBroken;
    }

    @Override
    public Vec3 corePos(int slot) {
        Vec3 back = this.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(-(6.0 + this.getBbWidth()));
        double x = this.getX() + back.x;
        double z = this.getZ() + back.z;
        int y = this.level() instanceof ServerLevel sl ? CrystalSpikes.groundY(sl, x, z, this.getBlockY()) : this.getBlockY();
        return new Vec3(x, y + 1.0, z);
    }

    @Override
    public void onCoreDestroyed(ServerLevel level, int slot, @Nullable Player by) {
        if (this.coreBroken) {
            return;
        }

        this.coreBroken = true;
        Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
        Fx.send(level, Fx.HARDEN, nape, 0.0F, 0.0F, 300.0);
        level.playSound(null, nape.x, nape.y, nape.z, ModSounds.ARMOR_SHATTER.get(), SoundSource.HOSTILE, 10.0F, 0.6F);
        this.voice(ModSounds.TITAN_ROAR.get(), 10.0F, 0.6F, 80, 3);
        level.sendParticles(ModParticles.BIG_STEAM.get(), nape.x, nape.y, nape.z, 120, 4.0, 4.0, 4.0, 0.15);
        shakeNearby(level, this.position(), 120.0, 0.9F, 24);
        this.knockDown(level, 220);

        for (ServerPlayer p : this.bossBar.getPlayers()) {
            p.displayClientMessage(Component.translatable("message.takemtitans.war_hammer_exposed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
    }

    private void deflect(ServerLevel level, Vec3 at, @Nullable Entity by) {
        level.sendParticles(ModParticles.SPARK.get(), at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 3.0F, 0.6F);
        if (by instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.takemtitans.war_hammer_sealed").withStyle(ChatFormatting.AQUA), true);
        }
    }

    @Override
    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        if (this.sealed() && part.kind == 1 && this.level() instanceof ServerLevel sl) {
            this.deflect(sl, part.position().add(0.0, (double)part.getBbHeight() * 0.5, 0.0), source.getEntity());
            return true;
        }

        return super.hurtPart(part, source, amount);
    }

    @Override
    public boolean traumaHit(float napeDamage, @Nullable Player credit, DamageSource source, boolean knock) {
        return !this.sealed() && super.traumaHit(napeDamage, credit, source, knock);
    }

    @Override
    public void onThunderSpear(Vec3 pos, @Nullable Entity owner) {
        if (this.sealed() && this.level() instanceof ServerLevel sl && pos.distanceTo(this.skeletonPoint(3, TitanSkeleton.NAPE)) < 6.0) {
            this.deflect(sl, pos, owner);
            return;
        }

        super.onThunderSpear(pos, owner);
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        ServerLevel level = (ServerLevel)this.level();
        Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
        boolean onMe = false;
        boolean underMe = false;
        for (Player p : level.players()) {
            if (this.canTarget(p)) {
                onMe |= p.position().distanceTo(nape) < 10.0;
                underMe |= this.horizontalDistanceTo(p) < this.getBbWidth() + 6.0 && p.getY() < this.getY() + 6.0;
            }
        }

        if (this.ringCooldown <= 0 && (onMe || underMe)) {
            this.ringCooldown = this.coreBroken ? 90 : 140;
            this.attackCooldown = 30;
            this.setAction(TitanAction.FLINCH, null);
            if (onMe) {
                this.bloom(level, nape);
            }

            if (underMe) {
                CrystalSpikes.ring(level, this.position(), this.getBbWidth() * 0.5 + 4.0, 12, 12.0F, this);
            }
        } else if (this.hammerCooldown <= 0 && dist < 70.0) {
            this.hammerCooldown = this.coreBroken ? 140 : 220;
            this.attackCooldown = 50;
            this.hammerAt = target.position().add(target.getDeltaMovement().multiply(8.0, 0.0, 8.0));
            this.setAction(TitanAction.ROAR, target);
            Vec3 sky = this.hammerAt.add(0.0, 30.0, 0.0);
            Fx.send(level, Fx.FORGE, sky, 0.0F, 0.0F, 300.0);
            level.playSound(null, sky.x, sky.y, sky.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 8.0F, 0.5F);
            level.playSound(null, this.getX(), this.getEyeY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 6.0F, 0.5F);
        } else if (this.spikeCooldown <= 0 && dist > 8.0 && dist < 90.0) {
            this.spikeCooldown = this.coreBroken ? 60 : 90;
            this.attackCooldown = 30;
            this.spikeLine = true;
            this.setAction(TitanAction.STOMP, target);
        } else if (dist < 14.0) {
            super.chooseAttack(target, dist);
        }
    }

    /** Crystal bursts out of its back and throws everyone on it clear. */
    private void bloom(ServerLevel level, Vec3 nape) {
        Fx.send(level, Fx.HARDEN, nape, 0.0F, 0.0F, 200.0);
        level.playSound(null, nape.x, nape.y, nape.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 6.0F, 0.5F);
        for (Player p : level.players()) {
            if (this.canTarget(p) && p.position().distanceTo(nape) < 11.0) {
                Vec3 away = p.position().subtract(nape);
                away = away.lengthSqr() < 0.01 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
                p.hurt(this.damageSources().mobAttack(this), 8.0F);
                p.setDeltaMovement(away.scale(2.0).add(0.0, 0.6, 0.0));
                p.hurtMarked = true;
            }
        }
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        // it fights from range: closes in when you are far, backs away when you crowd it
        this.turnTowards(target.position(), this.turnRate());
        float yaw = this.getYRot() * 0.017453292F;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        double sp = this.walkSpeed(this.getTitanHeight());
        this.walkVel = dist > 45.0 ? fwd.scale(sp) : (dist < 16.0 ? fwd.scale(-sp * 0.6) : Vec3.ZERO);
    }

    @Override
    protected void tickAction(ServerLevel level, TitanAction action, @Nullable LivingEntity target) {
        if (action == TitanAction.STOMP && this.spikeLine && this.actionTicks == (int)(action.duration * 0.6F)) {
            this.spikeLine = false;
            Vec3 aim = target != null ? target.position().add(target.getDeltaMovement().multiply(6.0, 0.0, 6.0)) : this.position().add(this.getLookAngle().scale(30.0));
            Vec3 from = this.position();
            int lines = this.coreBroken ? 3 : 1;
            for (int i = 0; i < lines; i++) {
                double yaw = (i - (lines - 1) * 0.5) * 0.35;
                Vec3 d = aim.subtract(from);
                Vec3 rot = new Vec3(d.x * Math.cos(yaw) - d.z * Math.sin(yaw), 0.0, d.x * Math.sin(yaw) + d.z * Math.cos(yaw));
                CrystalSpikes.line(level, from, from.add(rot), 11, 3.4, 2, 14.0F, this);
            }

            this.voice(ModSounds.TITAN_ROAR.get(), 5.0F, 0.7F, 30, 1);
        }

        if (action == TitanAction.ROAR && this.hammerAt != null && this.actionTicks == 18) {
            Vec3 at = this.hammerAt;
            this.hammerAt = null;
            FallingSectionEntity h = FallingSectionEntity.hammer(level, at.add(0.0, 30.0, 0.0), CRYSTAL, this.getUUID());
            if (FallingSectionEntity.budgetFor(h.states.length)) {
                h.launch(new Vec3(0.0, -1.1, 0.0), new Vector3f());
                level.addFreshEntity(h);
                level.playSound(null, at.x, at.y + 30.0, at.z, ModSounds.BOULDER_WHOOSH.get(), SoundSource.HOSTILE, 8.0F, 0.5F);
            }
        }

        super.tickAction(level, action, target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel sl && this.sealed() && this.isAlive() && this.tickCount % 20 == 0) {
            List<TitanHeartEntity> cores = sl.getEntitiesOfClass(TitanHeartEntity.class, this.getBoundingBox().inflate(32.0), TitanHeartEntity::isCore);
            if (cores.stream().noneMatch(h -> h.slot == 0)) {
                sl.addFreshEntity(TitanHeartEntity.core(sl, this, 0, CORE_HP));
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level) || this.isDeadOrDying()) {
            return;
        }

        if (this.spikeCooldown > 0) {
            this.spikeCooldown--;
        }

        if (this.hammerCooldown > 0) {
            this.hammerCooldown--;
        }

        if (this.ringCooldown > 0) {
            this.ringCooldown--;
        }

        if (this.sealed()) {
            if (this.tickCount % 3 == 0) {
                // the cord: a thread of light from the nape down into the cocoon
                Vec3 a = this.skeletonPoint(3, TitanSkeleton.NAPE);
                Vec3 b = this.corePos(0);
                for (int i = 0; i < 6; i++) {
                    double t = this.random.nextDouble();
                    Vec3 p = a.lerp(b, t).add(0.0, -Math.sin(t * Math.PI) * 2.0, 0.0);
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }

            float core = 0.0F;
            for (TitanHeartEntity h : level.getEntitiesOfClass(TitanHeartEntity.class, this.getBoundingBox().inflate(32.0), TitanHeartEntity::isCore)) {
                core = Math.max(core, h.health());
            }

            this.bossBar.setProgress(Mth.clamp(core / CORE_HP, 0.0F, 1.0F));
            this.bossBar.setColor(BossBarColor.WHITE);
        } else {
            this.bossBar.setProgress(Mth.clamp(this.napeHealth / this.maxNapeHealth(), 0.0F, 1.0F));
            this.bossBar.setColor(BossBarColor.RED);
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
        tag.putBoolean("CoreBroken", this.coreBroken);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.coreBroken = tag.getBoolean("CoreBroken");
    }
}
