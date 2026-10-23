package com.takem.titans.entity.titan;

import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.TitanHeartEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Chunks;
import com.takem.titans.world.ShiganshinaLayout;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class WallTitan extends ColossalTitan {
    public static final int CORES = 4;
    private static final float CORE_HP = 260.0F;
    private int coresLeft = 4;
    private int coreMask;
    private int throwCooldown = 160;
    private int emerge = 0;
    private boolean exposedRoared;
    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("entity.takemtitans.wall_titan"), BossBarColor.YELLOW, BossBarOverlay.NOTCHED_6
    );

    public WallTitan(EntityType<? extends WallTitan> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 58.0F;
    }

    @Override
    public int variantCount() {
        return 3;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.09;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 520.0F;
    }

    public boolean sealed() {
        return this.coresLeft > 0;
    }

    public Vec3 corePos(int slot) {
        Vec3 p = switch (slot) {
            case 0 -> this.skeletonPoint(12, new Vector3f(0.0F, 0.5F, -3.5F));
            case 1 -> this.skeletonPoint(14, new Vector3f(0.0F, 0.5F, -3.5F));
            case 2 -> this.skeletonPoint(2, new Vector3f(0.0F, -15.0F, -7.5F));
            default -> this.skeletonPoint(2, new Vector3f(0.0F, -21.0F, 7.5F));
        };
        return p.subtract(0.0, 2.0, 0.0);
    }

    @Override
    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        if (this.sealed() && part.kind == 1 && this.level() instanceof ServerLevel sl) {
            this.deflect(sl, part.position().add(0.0, (double)part.getBbHeight() * 0.5, 0.0), source.getEntity());
            return true;
        } else {
            return super.hurtPart(part, source, amount);
        }
    }

    @Override
    public boolean traumaHit(float napeDamage, @Nullable Player credit, DamageSource source, boolean knock) {
        if (this.sealed() && this.level() instanceof ServerLevel sl) {
            TitanHeartEntity c = this.nearestCore(sl, this.skeletonPoint(2, TitanSkeleton.TORSO_CENTER));
            if (c != null) {
                c.damage(napeDamage * 0.35F, credit);
            }

            return false;
        } else {
            return super.traumaHit(napeDamage, credit, source, knock);
        }
    }

    @Override
    public void onThunderSpear(Vec3 pos, @Nullable Entity owner) {
        if (this.sealed() && this.level() instanceof ServerLevel sl) {
            TitanHeartEntity c = this.nearestCore(sl, pos);
            if (c != null && c.position().distanceTo(pos) < 10.0) {
                c.damage(120.0F, owner instanceof Player p ? p : null);
            } else {
                this.deflect(sl, pos, owner);
            }
        } else {
            super.onThunderSpear(pos, owner);
        }
    }

    @Override
    public void slammed(ServerLevel level, float strength) {
        if (this.sealed()) {
            TitanHeartEntity c = this.nearestCore(level, this.position());
            if (c != null) {
                c.damage(90.0F * strength, null);
            }
        } else {
            super.slammed(level, strength);
        }
    }

    @Nullable
    private TitanHeartEntity nearestCore(ServerLevel sl, Vec3 at) {
        TitanHeartEntity best = null;

        for (TitanHeartEntity h : sl.getEntitiesOfClass(TitanHeartEntity.class, this.getBoundingBox().inflate(12.0), TitanHeartEntity::isCore)) {
            if (best == null || h.distanceToSqr(at) < best.distanceToSqr(at)) {
                best = h;
            }
        }

        return best;
    }

    private void deflect(ServerLevel sl, Vec3 at, @Nullable Entity by) {
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, 1.0, 1.0, 1.0, 0.4);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.8, 0.8, 0.8, 0.1);
        sl.playSound(null, at.x, at.y, at.z, ModSounds.ARMOR_CLANG.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
        if (by instanceof ServerPlayer sp && this.tickCount % 5 == 0) {
            sp.displayClientMessage(Component.translatable("message.takemtitans.wall_sealed", this.coresLeft).withStyle(ChatFormatting.GOLD), true);
        }
    }

    public void onCoreDestroyed(ServerLevel sl, int slot, @Nullable Player by) {
        if ((this.coreMask & 1 << slot) == 0) {
            this.coreMask |= 1 << slot;
            this.coresLeft = 4 - Integer.bitCount(this.coreMask);
            this.voice(ModSounds.TITAN_HURT.get(), 8.0F, 0.5F, 60, 3);
            shakeNearby(sl, this.position(), 120.0, 0.9F, 24);

            for (ServerPlayer p : this.bossBar.getPlayers()) {
                p.displayClientMessage(Component.translatable("message.takemtitans.core_down", this.coresLeft).withStyle(ChatFormatting.GOLD), true);
            }

            if (slot <= 1 && this.coresLeft > 0) {
                this.setAction(TitanAction.FLINCH, null);
            }

            if (this.coresLeft == 0) {
                this.expose(sl);
            }
        }
    }

    private void expose(ServerLevel sl) {
        Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
        sl.playSound(null, nape.x, nape.y, nape.z, ModSounds.ARMOR_SHATTER.get(), SoundSource.HOSTILE, 10.0F, 0.5F);
        this.voice(ModSounds.TITAN_ROAR.get(), 10.0F, 0.45F, 80, 3);
        Fx.send(sl, 23, nape.x, nape.y, nape.z, 30.0F, 0.0F, 0.0F, 0.0F, 400.0);
        sl.sendParticles(ModParticles.BIG_STEAM.get(), nape.x, nape.y, nape.z, 200, 6.0, 6.0, 6.0, 0.2);
        this.knockDown(sl, 340);

        for (ServerPlayer p : this.bossBar.getPlayers()) {
            p.displayClientMessage(Component.translatable("message.takemtitans.wall_exposed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
    }

    @Override
    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        this.turnTowards(target.position(), this.turnRate());
        this.walkVel = dist > 40.0 ? this.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(this.walkSpeed(this.getTitanHeight())) : Vec3.ZERO;
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        if (this.throwCooldown <= 0 && dist > 25.0 && this.level() instanceof ServerLevel sl) {
            this.throwChunk(sl, target);
            this.throwCooldown = this.sealed() ? 170 + this.random.nextInt(80) : 110 + this.random.nextInt(60);
            this.attackCooldown = 50;
        } else {
            super.chooseAttack(target, dist);
        }
    }

    private void throwChunk(ServerLevel sl, LivingEntity target) {
        Vec3 hand = this.skeletonPoint(this.random.nextBoolean() ? 7 : 10, TitanSkeleton.HAND);
        Vec3 to = target.position().add(target.getDeltaMovement().scale(18.0));
        double dist = hand.distanceTo(to);
        double t = Mth.clamp(dist / 1.7, 20.0, 60.0);
        Vec3 v = new Vec3((to.x - hand.x) / t, (to.y - hand.y) / t + 0.02 * t, (to.z - hand.z) / t);
        FallingSectionEntity rock = FallingSectionEntity.boulder(sl, hand, 2.4F + this.random.nextFloat(), null);
        rock.launch(v, Chunks.tumble(this.random, 0.3F));
        sl.addFreshEntity(rock);
        sl.playSound(null, hand.x, hand.y, hand.z, ModSounds.BOULDER_THROW.get(), SoundSource.HOSTILE, 6.0F, 0.6F);
        this.voice(ModSounds.TITAN_GROAN.get(), 6.0F, 0.5F, 30, 1);
    }

    private void breakOut(ServerLevel sl) {
        RandomSource r = this.random;
        Vec3 c = this.position();
        sl.playSound(null, c.x, c.y + 30.0, c.z, ModSounds.COLOSSAL_ARRIVAL.get(), SoundSource.HOSTILE, 12.0F, 0.7F);
        sl.playSound(null, c.x, c.y + 30.0, c.z, ModSounds.RUMBLING_QUAKE.get(), SoundSource.HOSTILE, 12.0F, 0.8F);
        shakeNearby(sl, c, 220.0, 1.6F, 80);
        Fx.send(sl, 16, c, 30.0F, 1.0F, 400.0);
        Fx.send(sl, 22, c.x, c.y, c.z, 70.0F, 12.0F, 0.0F, 0.0F, 400.0);
        double inward = Math.atan2(-1100.0 - c.z, 0.0 - c.x);

        for (int i = 0; i < 60; i++) {
            double a = inward + (r.nextDouble() - 0.5) * 2.4;
            double d = 6.0 + r.nextDouble() * 14.0;
            BlockPos at = BlockPos.containing(c.x + Math.cos(a) * d, c.y + 4.0 + r.nextDouble() * 44.0, c.z + Math.sin(a) * d);
            if (ShiganshinaLayout.isWall((double)at.getX() + 0.5, (double)at.getZ() + 0.5)) {
                FallingSectionEntity lump = Chunks.rip(sl, at, 2 + r.nextInt(2), 1, 8);
                if (lump != null) {
                    Vec3 out = new Vec3(-Math.cos(a), 0.0, -Math.sin(a));
                    lump.launch(out.scale(0.8 + r.nextDouble() * 1.4).add(0.0, 0.5 + r.nextDouble() * 1.2, 0.0), Chunks.tumble(r, 0.4F));
                    sl.addFreshEntity(lump);
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel sl) {
            if (this.emerge == 0 && this.tickCount > 20) {
                this.emerge = 1;
                this.breakOut(sl);
            }

            if (this.sealed() && this.tickCount % 20 == 0) {
                List<TitanHeartEntity> cores = sl.getEntitiesOfClass(TitanHeartEntity.class, this.getBoundingBox().inflate(16.0), TitanHeartEntity::isCore);

                for (int i = 0; i < 4; i++) {
                    int slot = i;
                    if ((this.coreMask & 1 << i) == 0 && cores.stream().noneMatch(h -> h.slot == slot)) {
                        sl.addFreshEntity(TitanHeartEntity.core(sl, this, i, 260.0F));
                    }
                }
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.level() instanceof ServerLevel sl) {
            if (this.throwCooldown > 0) {
                this.throwCooldown--;
            }

            if (this.tickCount % 6 == 0) {
                Vec3 sh = this.skeletonPoint(this.random.nextBoolean() ? 5 : 8, new Vector3f(0.0F, 0.0F, 0.0F));
                sl.sendParticles(ModParticles.DUST_CLOUD.get(), sh.x, sh.y, sh.z, 2, 2.0, 1.0, 2.0, 0.02);
                sl.sendParticles(
                    new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()), sh.x, sh.y, sh.z, 8, 3.0, 2.0, 3.0, 0.0
                );
            }

            if (this.sealed() && this.tickCount % 8 == 0) {
                Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
                sl.sendParticles(ParticleTypes.END_ROD, nape.x, nape.y, nape.z, 3, 1.2, 1.2, 1.2, 0.02);
            }

            float total = 0.0F;

            for (TitanHeartEntity h : sl.getEntitiesOfClass(TitanHeartEntity.class, this.getBoundingBox().inflate(16.0), TitanHeartEntity::isCore)) {
                total += h.health();
            }

            if (this.sealed()) {
                this.bossBar.setProgress(Mth.clamp(total / 1040.0F, 0.0F, 1.0F));
                this.bossBar.setColor(BossBarColor.YELLOW);
            } else {
                this.bossBar.setProgress(Mth.clamp(this.napeHealth / this.maxNapeHealth(), 0.0F, 1.0F));
                this.bossBar.setColor(BossBarColor.RED);
                if (!this.exposedRoared && !this.isDowned()) {
                    this.exposedRoared = true;
                }
            }
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
        tag.putInt("CoreMask", this.coreMask);
        tag.putInt("Emerge", this.emerge);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.coreMask = tag.getInt("CoreMask");
        this.coresLeft = 4 - Integer.bitCount(this.coreMask);
        this.emerge = tag.getInt("Emerge");
    }
}
