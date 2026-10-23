package com.takem.titans.entity.titan;

import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.story.Training;
import javax.annotation.Nullable;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Training Corps' wooden titan: a 6.5 metre dummy bolted to the ground with a leather pad where the nape would be.
 * It never moves, never bleeds and never fights back. The pad only comes off to a single fast, clean cut: hacking at it
 * from a standstill does nothing, which is the whole lesson.
 */
public class TrainingDummy extends AbstractTitan {
    private float anchorYaw = 180.0F;
    private int hintCooldown;
    private boolean cut;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createDummyAttributes() {
        return createTitanAttributes().add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.ARMOR, 0.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void anchor(float yaw) {
        this.anchorYaw = yaw;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
    }

    @Override
    public boolean isDummy() {
        return true;
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 6.5F;
    }

    @Override
    protected float napeHealthFor(float height) {
        // one cut at roughly 8 m/s with standard blades: a real ODM pass, not a jump-and-swing
        return 1.9F * height;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.0;
    }

    @Override
    public int titanKind() {
        return TitanHit.PURE;
    }

    @Override
    protected boolean canTarget(LivingEntity e) {
        return false;
    }

    @Override
    protected float limbThreshold() {
        // the limbs are bolted on: the drill is about the nape
        return Float.MAX_VALUE;
    }

    @Override
    protected void voice(SoundEvent sound, float volume, float pitch, int hold, int priority) {
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity other) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.setDeltaMovement(0.0, Math.min(0.0, this.getDeltaMovement().y), 0.0);
            this.setYRot(this.anchorYaw);
            this.setYHeadRot(this.anchorYaw);
            this.yBodyRot = this.anchorYaw;
            if (this.hintCooldown > 0) {
                this.hintCooldown--;
            }
        }
    }

    @Override
    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        // damage never adds up: a fresh pad for every cut, so only a single fast one counts
        this.napeHealth = this.maxNapeHealth();
        this.cut = false;
        boolean hit = super.hurtPart(part, source, amount);
        if (hit && this.level() instanceof ServerLevel level) {
            Vec3 at = part.position().add(0.0, part.getBbHeight() * 0.5, 0.0);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()), at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.12);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
            if (part.kind == TitanHit.NAPE && !this.cut && source.getEntity() instanceof ServerPlayer sp && this.hintCooldown <= 0) {
                this.hintCooldown = 30;
                float took = 1.0F - this.napeHealth / this.maxNapeHealth();
                sp.displayClientMessage(Component.translatable(took < 0.6F ? "training.takemtitans.too_slow" : "training.takemtitans.almost", Math.round(took * 100.0F)), true);
            }

            this.napeHealth = this.maxNapeHealth();
        }

        return hit;
    }

    @Override
    protected void killBy(DamageSource source, @Nullable Player player, int damage) {
        ServerLevel level = (ServerLevel)this.level();
        this.cut = true;
        Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()), nape.x, nape.y, nape.z, 40, 0.4, 0.4, 0.4, 0.25);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.defaultBlockState()), nape.x, nape.y, nape.z, 24, 0.5, 0.5, 0.5, 0.3);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()), nape.x, nape.y, nape.z, 30, 0.5, 0.5, 0.5, 0.2);
        level.sendParticles(ParticleTypes.CRIT, nape.x, nape.y, nape.z, 20, 0.3, 0.3, 0.3, 0.6);
        level.playSound(null, nape.x, nape.y, nape.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.6F, 0.8F);
        level.playSound(null, nape.x, nape.y, nape.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, 0.8F, 1.3F);
        this.napeHealth = this.maxNapeHealth();
        this.setAction(TitanAction.FLINCH, null);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(0, damage, TitanHit.NAPE, Math.round(this.getTitanHeight() * 10.0F)));
            sp.displayClientMessage(Component.translatable("training.takemtitans.clean_cut", damage), true);
            Training.onDummyCut(sp);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Anchor", this.anchorYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Anchor")) {
            this.anchorYaw = tag.getFloat("Anchor");
        }
    }
}
