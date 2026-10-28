package com.takem.titans.entity.titan;

import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.TitanSpawner;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Female Titan. Fast, clever and mean: she sprints straight through the district, kicks buildings apart, and the
 * moment a scout closes on her nape she floods it with crystal. The crystal holds for a few seconds, glinting, then fades;
 * the fight is baiting the hardening and cutting in the gap behind it. A thunder spear on the crystal cracks it and drops
 * her. Twice, as she weakens, she screams and the titans of the district come running.
 */
public class FemaleTitan extends AbstractTitan implements BossTitan {
    private static final int HARDEN_TICKS = 55;
    private static final double GUARD_RANGE = 8.0;
    private int harden;
    private int hardenCooldown = 60;
    private int sprintTicks;
    private int sprintCooldown = 80;
    private Vec3 sprintDir = Vec3.ZERO;
    private int kickCooldown = 60;
    private int screams;
    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("entity.takemtitans.female_titan"), BossBarColor.YELLOW, BossBarOverlay.NOTCHED_10
    );

    public FemaleTitan(EntityType<? extends FemaleTitan> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 14.0F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.16 + (double)height * 0.02;
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
        return 0.9F;
    }

    @Override
    protected float widthFactor() {
        return 0.88F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 17.0F * height;
    }

    @Override
    protected double detectionRange() {
        return 150.0;
    }

    @Override
    protected float turnRate() {
        return 15.0F;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected boolean isSluggish(ServerLevel level) {
        return false;
    }

    public boolean hardened() {
        return this.harden > 0;
    }

    private Vec3 nape() {
        return this.skeletonPoint(3, TitanSkeleton.NAPE);
    }

    private void harden(ServerLevel level) {
        this.harden = HARDEN_TICKS;
        Vec3 n = this.nape();
        Fx.send(level, Fx.HARDEN, n, 1.0F, 0.0F, 160.0);
        level.playSound(null, n.x, n.y, n.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 4.0F, 0.6F);
        level.playSound(null, n.x, n.y, n.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE, 4.0F, 0.5F);
        this.setVariant(1);
    }

    /** The crystal gives out on its own (the gap to cut in) or is cracked by a spear. */
    private void unharden(ServerLevel level, boolean cracked) {
        this.harden = 0;
        float hp = Mth.clamp(this.napeHealth / this.maxNapeHealth(), 0.0F, 1.0F);
        this.hardenCooldown = (int)(70.0F + 90.0F * hp) + (cracked ? 60 : 0);
        this.setVariant(0);
        Vec3 n = this.nape();
        if (cracked) {
            Fx.send(level, Fx.HARDEN, n, 0.0F, 0.0F, 200.0);
            level.playSound(null, n.x, n.y, n.z, ModSounds.ARMOR_SHATTER.get(), SoundSource.HOSTILE, 6.0F, 1.3F);
            this.voice(ModSounds.TITAN_HURT.get(), 6.0F, 1.3F, 40, 3);
            this.knockDown(level, 70);
            for (ServerPlayer p : this.bossBar.getPlayers()) {
                p.displayClientMessage(Component.translatable("message.takemtitans.crystal_cracked").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            }
        } else {
            level.playSound(null, n.x, n.y, n.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 3.0F, 0.8F);
        }
    }

    private void deflect(ServerLevel level, Vec3 at, @Nullable Entity by) {
        level.sendParticles(ModParticles.SPARK.get(), at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.5);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.15);
        level.playSound(null, at.x, at.y, at.z, ModSounds.ARMOR_CLANG.get(), SoundSource.HOSTILE, 2.0F, 1.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 3.0F, 0.7F);
        if (by instanceof Player p) {
            // the blade skids off the crystal and throws the scout back
            Vec3 away = p.position().subtract(at);
            away = away.lengthSqr() < 0.01 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
            p.push(away.x * 0.9, 0.35, away.z * 0.9);
            p.hurtMarked = true;
            if (p instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.takemtitans.nape_crystal").withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    @Override
    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        if (this.harden > 0 && part.kind == 1 && this.level() instanceof ServerLevel sl) {
            this.deflect(sl, part.position().add(0.0, (double)part.getBbHeight() * 0.5, 0.0), source.getEntity());
            return true;
        }

        return super.hurtPart(part, source, amount);
    }

    @Override
    public boolean traumaHit(float napeDamage, @Nullable Player credit, DamageSource source, boolean knock) {
        if (this.harden > 0) {
            return false;
        }

        return super.traumaHit(napeDamage, credit, source, knock);
    }

    @Override
    public void onThunderSpear(Vec3 pos, @Nullable Entity owner) {
        if (this.harden > 0 && this.level() instanceof ServerLevel sl && pos.distanceTo(this.nape()) < 7.0) {
            this.unharden(sl, true);
            return;
        }

        super.onThunderSpear(pos, owner);
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        Vec3 to = target.position().subtract(this.position());
        Vec3 look = this.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
        boolean ahead = new Vec3(to.x, 0.0, to.z).normalize().dot(look) > 0.5;
        if (this.kickCooldown <= 0 && ahead && dist < (double)this.getTitanScale() * 3.0 && target.getY() < this.getY() + this.getTitanHeight() * 0.6) {
            this.setAction(TitanAction.KICK, target);
            this.kickCooldown = 80 + this.random.nextInt(40);
            this.attackCooldown = 30;
            this.voice(ModSounds.TITAN_ROAR.get(), 4.0F, 1.35F, 30, 1);
        } else if (this.sprintCooldown <= 0 && dist > 26.0 && dist < 140.0 && this.onGround()) {
            this.sprintTicks = 70;
            this.sprintCooldown = 160 + this.random.nextInt(80);
            this.sprintDir = new Vec3(to.x, 0.0, to.z).normalize();
            this.voice(ModSounds.TITAN_ROAR.get(), 6.0F, 1.3F, 40, 2);
            this.attackCooldown = 20;
        } else {
            super.chooseAttack(target, dist);
            this.attackCooldown = Math.min(this.attackCooldown, 18 + this.random.nextInt(14));
        }
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        if (this.sprintTicks > 0) {
            this.sprintTicks--;
            this.setFlag(8, true);
            // she bends her run toward you, a little
            Vec3 want = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize();
            this.sprintDir = this.sprintDir.add(want.subtract(this.sprintDir).scale(0.06)).normalize();
            this.walkVel = this.sprintDir.scale(this.sprintTicks > 62 ? 0.35 : 1.05);
            this.turnTowards(this.position().add(this.sprintDir.scale(10.0)), 25.0F);
            if (this.level() instanceof ServerLevel sl) {
                this.trample(sl);
            }

            if (this.sprintTicks == 0 || dist < 6.0) {
                this.sprintTicks = 0;
                this.setFlag(8, false);
            }
        } else {
            this.setFlag(8, false);
            super.chase(target, dist, sluggish);
        }
    }

    private void trample(ServerLevel level) {
        if (this.tickCount % 3 == 0) {
            Destruction.bodySlam(level, this.position(), this.sprintDir, 4.0, this.getBbWidth() * 0.6, true);
            level.sendParticles(ModParticles.DUST_CLOUD.get(), this.getX(), this.getY() + 0.5, this.getZ(), 4, 2.0, 0.3, 2.0, 0.05);
        }

        for (Entity e : level.getEntities(this, this.getBoundingBox().move(this.sprintDir.scale(2.0)).inflate(1.0), x -> x.isAlive() && !(x instanceof TitanPart))) {
            if (e instanceof LivingEntity l && !(e instanceof AbstractTitan) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                l.setDeltaMovement(this.sprintDir.scale(2.0).add(0.0, 0.8, 0.0));
                l.hurtMarked = true;
                l.hurt(this.damageSources().mobAttack(this), 12.0F);
            }
        }
    }

    @Override
    protected void tickAction(ServerLevel level, TitanAction action, @Nullable LivingEntity target) {
        if (action == TitanAction.KICK && this.actionTicks == (int)(action.duration * 0.55F)) {
            this.kick(level);
        }

        if (action.isSwat() && this.actionTicks == (int)(action.duration * 0.45F)) {
            // her fists are crystal-tipped: a swat goes through a wall like it is paper
            Vec3 hand = this.handPos(action.side());
            Vec3 dir = this.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
            Destruction.bodySlam(level, new Vec3(hand.x, Math.max(this.getY(), hand.y - 3.0), hand.z), dir, 5.0, 2.5, false);
        }

        super.tickAction(level, action, target);
    }

    private void kick(ServerLevel level) {
        Vec3 dir = this.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
        Vec3 foot = this.skeletonPoint(12, new Vector3f(0.0F, 10.0F, 0.0F));
        float s = this.getTitanScale();
        Destruction.bodySlam(level, this.position().add(dir.scale(2.0)), dir, 6.0 + s * 1.6, 3.0 + s * 0.3, true);
        Destruction.crunch(level, foot, 5.0F);
        Fx.send(level, Fx.SONIC_BOOM, foot.x, foot.y, foot.z, (float)dir.x, 0.0F, (float)dir.z, 1.0F, 200.0);
        shakeNearby(level, foot, 60.0, 0.7F, 14);
        AABB reach = new AABB(foot, foot).inflate(s * 1.4 + 2.0, s * 1.2, s * 1.4 + 2.0).move(dir.scale(s));

        for (Entity e : level.getEntities(this, reach, x -> x.isAlive() && !(x instanceof TitanPart))) {
            if (e instanceof FallingSectionEntity || e instanceof DebrisEntity) {
                continue;
            }

            Vec3 fling = dir.scale(3.4).add(0.0, 1.2, 0.0);
            if (e instanceof AbstractTitan t) {
                t.blastKnockDown(80);
                t.hurt(this.damageSources().mobAttack(this), 60.0F);
            } else if (e instanceof LivingEntity l && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                l.hurt(this.damageSources().mobAttack(this), 16.0F + s);
                l.setDeltaMovement(fling);
                l.hurtMarked = true;
            }
        }
    }

    private void scream(ServerLevel level, LivingEntity target) {
        this.screams++;
        this.setAction(TitanAction.ROAR, target);
        this.attackCooldown = 50;
        Vec3 head = this.skeletonPoint(3, TitanSkeleton.MOUTH);
        this.voice(ModSounds.FEMALE_SCREAM.get(), 14.0F, 0.95F + this.random.nextFloat() * 0.1F, 110, 3);
        Fx.send(level, Fx.SCREAM, head, 1.0F + this.screams * 0.3F, 0.0F, 400.0);
        shakeNearby(level, head, 140.0, 0.9F, 36);

        for (ServerPlayer p : this.bossBar.getPlayers()) {
            p.displayClientMessage(Component.translatable("message.takemtitans.female_scream").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }

        // the titans of the district answer the call
        int n = 2 + this.screams;
        for (int i = 0; i < n; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double r = 55.0 + this.random.nextDouble() * 25.0;
            double x = this.getX() + Math.cos(a) * r;
            double z = this.getZ() + Math.sin(a) * r;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            EntityType<? extends AbstractTitan> type = i % 3 == 2 ? ModEntities.ABNORMAL_TITAN.get() : ModEntities.PURE_TITAN.get();
            AbstractTitan t = TitanSpawner.spawnAt(level, type, x, y, z, (float)(Math.toDegrees(a) + 90.0));
            if (t != null) {
                t.setTarget(target);
                Fx.send(level, Fx.EMERGE, new Vec3(x, y, z), t.getTitanScale(), 0.5F, 200.0);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level) || this.isDeadOrDying()) {
            return;
        }

        if (this.hardenCooldown > 0) {
            this.hardenCooldown--;
        }

        if (this.sprintCooldown > 0) {
            this.sprintCooldown--;
        }

        if (this.kickCooldown > 0) {
            this.kickCooldown--;
        }

        Vec3 n = this.nape();
        if (this.harden > 0) {
            if (--this.harden == 0) {
                this.unharden(level, false);
            } else if (this.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, n.x, n.y, n.z, 2, 1.4, 1.0, 1.4, 0.01);
                if (this.harden < 15) {
                    // it is about to give: the glint flickers
                    level.sendParticles(ParticleTypes.WHITE_ASH, n.x, n.y, n.z, 6, 1.6, 1.2, 1.6, 0.0);
                }
            }
        } else if (this.hardenCooldown <= 0 && !this.isDowned() && this.tickCount % 2 == 0) {
            for (Player p : level.players()) {
                if (this.canTarget(p) && p.position().distanceTo(n) < GUARD_RANGE) {
                    this.harden(level);
                    break;
                }
            }
        }

        float hp = Mth.clamp(this.napeHealth / this.maxNapeHealth(), 0.0F, 1.0F);
        LivingEntity target = this.getTarget();
        if (target != null && this.getAction() == TitanAction.NONE && !this.isDowned()
            && (this.screams == 0 && hp < 0.66F || this.screams == 1 && hp < 0.33F)) {
            this.scream(level, target);
        }

        this.bossBar.setProgress(hp);
        this.bossBar.setColor(this.harden > 0 ? BossBarColor.BLUE : BossBarColor.YELLOW);
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
        tag.putInt("Screams", this.screams);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.screams = tag.getInt("Screams");
        this.setVariant(0);
    }
}
