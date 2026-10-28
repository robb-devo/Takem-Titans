package com.takem.titans.entity.titan;

import com.takem.titans.TakemConfig;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.npc.JaegerEntity;
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.BladeTier;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import com.takem.titans.story.Story;
import com.takem.titans.world.Boom;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.KillCam;
import com.takem.titans.world.TownLife;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public abstract class AbstractTitan extends Monster {
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(AbstractTitan.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(AbstractTitan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(AbstractTitan.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(AbstractTitan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(AbstractTitan.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_GORE = SynchedEntityData.defineId(AbstractTitan.class, EntityDataSerializers.BYTE);
    private float trauma;
    public static final int FLAG_RIGHT_ARM_CUT = 1;
    public static final int FLAG_LEFT_ARM_CUT = 2;
    public static final int FLAG_DOWNED = 4;
    public static final int FLAG_RUNNING = 8;
    public static final int FLAG_SLUGGISH = 16;
    public static final int FLAG_HEADLESS = 32;
    public static final int FLAG_RIGHT_LEG_CUT = 64;
    public static final int FLAG_LEFT_LEG_CUT = 128;
    protected final int[] legRegen = new int[2];
    protected final int[] spurt = new int[6];
    private final int[] woundPart = new int[10];
    private final Vec3[] woundAt = new Vec3[10];
    private final int[] woundTtl = new int[10];
    public static final ResourceKey<DamageType> TITAN_BITE = ResourceKey.create(Registries.DAMAGE_TYPE, TakemTitans.id("titan_bite"));
    public static Consumer<AbstractTitan> clientFootstep = t -> {
    };
    public int actionTicks;
    public int dyingTicks;
    private float walkPhase;
    private float walkPhaseO;
    private float walkAmount;
    private float walkAmountO;
    private float runBlend;
    private float runBlendO;
    protected final TitanPart[] parts;
    private final TitanSkeleton.Pose pose = new TitanSkeleton.Pose();
    private final TitanSkeleton.Frames frames = new TitanSkeleton.Frames();
    protected float napeHealth = -1.0F;
    /** How seasoned this titan is (0 = the stragglers a fresh recruit meets .. 4 = what waits beyond the walls late in the story). */
    protected int threat;
    /** Whether this titan has been matched to the progress of the players around it yet. */
    private boolean threatSet;
    protected final float[] armDamage = new float[2];
    protected final float[] legDamage = new float[2];
    protected final int[] armRegen = new int[2];
    protected int downedTicks;
    protected int attackCooldown = 40;
    protected int roarCooldown;
    private int targetScan;
    private int idleSound = 100;
    private int voiceCooldown;
    private int voicePriority;
    private int fleshCooldown;
    private int wanderCooldown;
    @Nullable
    private Vec3 wanderTarget;
    protected Vec3 walkVel = Vec3.ZERO;
    @Nullable
    protected LivingEntity held;
    private boolean storyCounted;
    private final int[] partHitCooldown;
    private int steamTicks;
    private int lastCrushSound = -100;
    private int lastCollapseRequest = -100;

    protected AbstractTitan(EntityType<? extends AbstractTitan> type, Level level) {
        super(type, level);
        this.xpReward = 40;
        this.parts = new TitanPart[]{
            new TitanPart(this, 0, "nape", 1, 0, 3, TitanSkeleton.NAPE, 9.0F, 6.0F),
            new TitanPart(this, 1, "head", 2, 0, 3, TitanSkeleton.HEAD_CENTER, 19.0F, 19.0F),
            new TitanPart(this, 2, "torso", 0, 0, 2, TitanSkeleton.TORSO_CENTER, 21.0F, 27.0F),
            new TitanPart(this, 3, "right_arm", 3, -1, 6, TitanSkeleton.FOREARM_CENTER, 8.0F, 15.0F),
            new TitanPart(this, 4, "right_hand", 3, -1, 7, TitanSkeleton.HAND, 9.0F, 10.0F),
            new TitanPart(this, 5, "left_arm", 3, 1, 9, TitanSkeleton.FOREARM_CENTER, 8.0F, 15.0F),
            new TitanPart(this, 6, "left_hand", 3, 1, 10, TitanSkeleton.HAND, 9.0F, 10.0F),
            new TitanPart(this, 7, "right_thigh", 4, -1, 11, new Vector3f(0.0F, 5.0F, 0.0F), 9.0F, 11.0F),
            new TitanPart(this, 8, "right_shin", 4, -1, 12, TitanSkeleton.SHIN_CENTER, 8.0F, 11.0F),
            new TitanPart(this, 9, "left_thigh", 4, 1, 13, new Vector3f(0.0F, 5.0F, 0.0F), 9.0F, 11.0F),
            new TitanPart(this, 10, "left_shin", 4, 1, 14, TitanSkeleton.SHIN_CENTER, 8.0F, 11.0F)
        };
        this.partHitCooldown = new int[this.parts.length];
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
        if (!level.isClientSide) {
            this.setTitanHeight(this.randomHeight(this.random));
            this.setVariant(this.random.nextInt(this.variantCount()));
        }
    }

    public static Builder createTitanAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 400.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 12.0)
            .add(Attributes.FOLLOW_RANGE, 64.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.STEP_HEIGHT, 2.0)
            .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (this.parts != null) {
            for (int i = 0; i < this.parts.length; i++) {
                this.parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SCALE, 3.0F);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_ACTION, (byte)0);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_FLAGS, (byte)0);
        builder.define(DATA_GORE, (byte)0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SCALE.equals(key)) {
            this.refreshDimensions();

            for (TitanPart part : this.parts) {
                part.rescale(this.getTitanScale());
            }
        } else if (DATA_ACTION.equals(key)) {
            this.actionTicks = 0;
        }
    }

    protected abstract float randomHeight(RandomSource var1);

    protected abstract double walkSpeed(float var1);

    public abstract int titanKind();

    public boolean isCrawler() {
        return false;
    }

    public float headTilt() {
        return 1.0F;
    }

    public float headScale() {
        return 1.0F;
    }

    public int variantCount() {
        return 1;
    }

    protected float napeHealthFor(float height) {
        return 3.4F * height;
    }

    protected double detectionRange() {
        return 48.0;
    }

    protected float turnRate() {
        return 7.0F;
    }

    protected void chooseAttack(LivingEntity target, double dist) {
        if (!this.tryGrab(target) && !this.trySwat(target)) {
            this.tryStomp(target);
        }
    }

    protected float widthFactor() {
        return 0.85F;
    }

    protected float heightFactor() {
        return 4.0F;
    }

    public float getTitanScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public float getTitanHeight() {
        return this.getTitanScale() * 4.0F;
    }

    public void setTitanHeight(float height) {
        this.entityData.set(DATA_SCALE, height / 4.0F);
        this.refreshDimensions();

        for (TitanPart part : this.parts) {
            part.rescale(this.getTitanScale());
        }

        this.napeHealth = this.napeHealthFor(height) * this.threatMul();
        AttributeInstance step = this.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            step.setBaseValue(Mth.clamp((double)height * 0.22, 1.0, 5.0));
        }
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int v) {
        this.entityData.set(DATA_VARIANT, v);
    }

    public TitanAction getAction() {
        return TitanAction.byId(this.entityData.get(DATA_ACTION));
    }

    protected void setAction(TitanAction action, @Nullable Entity target) {
        this.entityData.set(DATA_ACTION, (byte)action.ordinal());
        this.entityData.set(DATA_TARGET, target == null ? -1 : target.getId());
        this.actionTicks = 0;
    }

    @Nullable
    public Entity actionTargetEntity() {
        int id = this.entityData.get(DATA_TARGET);
        return id < 0 ? null : this.level().getEntity(id);
    }

    public boolean hasFlag(int flag) {
        return (this.entityData.get(DATA_FLAGS) & flag) != 0;
    }

    protected void setFlag(int flag, boolean on) {
        byte b = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, (byte)(on ? b | flag : b & ~flag));
    }

    public int goreLevel() {
        return this.entityData.get(DATA_GORE);
    }

    /** Training dummies: wooden practice targets that never move, fight back, bleed or count as a threat. */
    public boolean isDummy() {
        return false;
    }

    public void addTrauma(float amount) {
        this.trauma = Math.min(2000.0F, this.trauma + amount);
    }

    public void blind(int ticks) {
        if (!this.isDeadOrDying()) {
            this.setAction(TitanAction.BLINDED, null);
            this.setTarget(null);
            this.targetScan = ticks;
        }
    }

    public void pin(ServerLevel level, int ticks) {
        if (!this.isDeadOrDying()) {
            this.knockDown(level, ticks);
        }
    }

    public boolean isLegCut(int side) {
        return this.hasFlag(side < 0 ? 64 : 128);
    }

    public boolean isHeadless() {
        return this.hasFlag(32);
    }

    protected void addWound(int partIndex, Vec3 world, float power) {
        int slot = 0;

        for (int i = 1; i < this.woundTtl.length; i++) {
            if (this.woundTtl[i] < this.woundTtl[slot]) {
                slot = i;
            }
        }

        this.woundPart[slot] = partIndex;
        this.woundAt[slot] = this.partLocal(partIndex, world);
        this.woundTtl[slot] = (int)Math.min(600.0F, 160.0F + power * 2.0F);
    }

    private void goreTick(ServerLevel level) {
        if (this.isDummy()) {
            return;
        }

        if (this.dyingTicks > 0) {
            this.trauma = Math.max(this.trauma, 900.0F);
        } else {
            this.trauma *= 0.9985F;
        }

        int g = this.trauma > 700.0F ? 3 : (this.trauma > 260.0F ? 2 : (this.trauma > 50.0F ? 1 : 0));
        if (g != this.entityData.get(DATA_GORE)) {
            this.entityData.set(DATA_GORE, (byte)g);
        }

        if (this.tickCount % 3 == 0) {
            for (int i = 0; i < this.woundTtl.length; i++) {
                if (this.woundTtl[i] > 0 && this.woundAt[i] != null) {
                    this.woundTtl[i] = this.woundTtl[i] - 3;
                    Vec3 p = this.partAnchor(this.woundPart[i], this.woundAt[i], 1.0F);
                    level.sendParticles(
                        ModParticles.BLOOD.get(), p.x, p.y, p.z, 0, (this.random.nextDouble() - 0.5) * 0.1, -0.05, (this.random.nextDouble() - 0.5) * 0.1, 1.0
                    );
                    if (this.woundTtl[i] % 12 == 0) {
                        level.sendParticles(ModParticles.STEAM.get(), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
                    }
                }
            }
        }

        // the heart still beats: a hard pump, a weaker one, then a pause
        int phase = this.tickCount % 14;
        boolean beat = phase < 2 || phase >= 5 && phase < 6;

        for (int ix = 0; ix < this.spurt.length; ix++) {
            if (this.spurt[ix] > 0) {
                this.spurt[ix]--;
                int joint = switch (ix) {
                    case 0 -> 5;
                    case 1 -> 8;
                    case 2 -> 11;
                    case 3 -> 13;
                    case 5 -> 3;
                    default -> 2;
                };
                Vector3f end = switch (ix) {
                    case 0, 1 -> new Vector3f(0.0F, 14.0F, 0.0F);
                    case 2, 3 -> new Vector3f(0.0F, 10.0F, 0.0F);
                    case 5 -> new Vector3f(TitanSkeleton.NAPE);
                    default -> new Vector3f(0.0F, -26.0F, 0.0F);
                };
                Vec3 at = this.skeletonPoint(joint, end);
                if (beat) {
                    Vec3 base = this.skeletonPoint(joint, ix == 5 ? new Vector3f(0.0F, -6.0F, 0.0F) : new Vector3f(0.0F, ix == 4 ? -14.0F : 4.0F, 0.0F));
                    Vec3 dir = at.subtract(base).normalize().add(0.0, ix >= 4 ? 0.7 : 0.35, 0.0);
                    float k = Math.min(1.0F, (float)this.spurt[ix] / 80.0F);
                    float pump = phase < 2 ? 1.0F : 0.55F;
                    Gore.spurt(level, at, dir, (int)((8 + 22.0F * k) * pump), (0.5 + 0.7 * (double)k * (double)this.getTitanScale() * 0.3) * pump);
                    if (phase == 0 && this.random.nextInt(3) == 0) {
                        level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.HOSTILE, 1.6F, 0.4F + this.random.nextFloat() * 0.15F);
                    }
                }

                // the raw end sizzles as it starts to knit
                if (this.spurt[ix] % 5 == 0) {
                    level.sendParticles(ModParticles.STEAM.get(), at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0.03);
                }
            }
        }
    }

    protected void severLeg(int side, ServerLevel level) {
        int i = side < 0 ? 0 : 1;
        this.setFlag(side < 0 ? 64 : 128, true);
        this.legRegen[i] = 700 + this.random.nextInt(200);
        this.spurt[2 + i] = 260;
        this.addTrauma(350.0F);
        Vec3 out = this.getLookAngle().multiply(1.0, 0.0, 1.0).scale(-0.3).add(0.0, 0.25, 0.0);
        Gore.gib(level, this, side < 0 ? 12 : 14, out);
        this.voice(ModSounds.TITAN_HURT.get(), 4.0F, this.voicePitch() * 0.8F, 60, 3);
        this.knockDown(level, 900);
    }

    public boolean isArmCut(int side) {
        return this.hasFlag(side < 0 ? 1 : 2);
    }

    public boolean isDowned() {
        return this.hasFlag(4);
    }

    public float walkPhase(float pt) {
        return Mth.lerp(pt, this.walkPhaseO, this.walkPhase);
    }

    public float walkAmount(float pt) {
        return Mth.lerp(pt, this.walkAmountO, this.walkAmount);
    }

    public float runBlend(float pt) {
        return Mth.lerp(pt, this.runBlendO, this.runBlend);
    }

    public TitanPart[] getTitanParts() {
        return this.parts;
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return this.parts;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        float s = this.getTitanScale();
        float h = this.heightFactor() * s;
        return EntityDimensions.scalable(this.widthFactor() * s, h).withEyeHeight(h * 0.88F);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate((double)this.getTitanHeight() * 0.65, (double)this.getTitanHeight() * 0.1, (double)this.getTitanHeight() * 0.65);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void pushEntities() {
        for (Entity e : this.level().getEntities(this, this.getBoundingBox().deflate((double)this.getBbWidth() * 0.15, 0.0, (double)this.getBbWidth() * 0.15))) {
            if (e != this.held && e.isPushable() && !(e instanceof AbstractTitan)) {
                e.push(this);
            }
        }
    }

    @Override
    public int getMaxHeadYRot() {
        return 70;
    }

    @Override
    public int getMaxHeadXRot() {
        return 50;
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return !this.isPersistenceRequired() && distSq > 12544.0;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (distance > 4.0F && this.level() instanceof ServerLevel sl) {
            this.landed(sl, Math.min(1.5F, distance / 12.0F));
        }

        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        RandomSource r = level.getRandom();
        this.setTitanHeight(this.randomHeight(r));
        this.setVariant(r.nextInt(this.variantCount()));
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND || reason == MobSpawnType.EVENT) {
            this.setPersistenceRequired();
        }

        this.setHealth(this.getMaxHealth());
        return result;
    }

    public static boolean checkTitanSpawnRules(
        EntityType<? extends AbstractTitan> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random
    ) {
        return reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("TitanHeight", this.getTitanHeight());
        tag.putInt("Variant", this.getVariant());
        tag.putFloat("NapeHealth", this.napeHealth);
        tag.putInt("Threat", this.threat);
        tag.putByte("Flags", this.entityData.get(DATA_FLAGS));
        tag.putInt("ArmRegenR", this.armRegen[0]);
        tag.putInt("ArmRegenL", this.armRegen[1]);
        tag.putInt("Downed", this.downedTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.threat = tag.getInt("Threat");
        this.threatSet = tag.contains("Threat");
        if (tag.contains("TitanHeight")) {
            this.setTitanHeight(tag.getFloat("TitanHeight"));
        }

        if (tag.contains("Variant")) {
            this.setVariant(tag.getInt("Variant"));
        }

        if (tag.contains("NapeHealth")) {
            this.napeHealth = tag.getFloat("NapeHealth");
        }

        this.entityData.set(DATA_FLAGS, (byte)(tag.getByte("Flags") & -9));
        this.armRegen[0] = tag.getInt("ArmRegenR");
        this.armRegen[1] = tag.getInt("ArmRegenL");
        this.downedTicks = tag.getInt("Downed");
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel gl) {
            this.goreTick(gl);
        }

        if (this.getAction() != TitanAction.NONE) {
            this.actionTicks++;
        }

        for (int i = 0; i < this.partHitCooldown.length; i++) {
            if (this.partHitCooldown[i] > 0) {
                this.partHitCooldown[i]--;
            }
        }

        if (this.voiceCooldown > 0) {
            this.voiceCooldown--;
        }

        if (this.fleshCooldown > 0) {
            this.fleshCooldown--;
        }

        this.updateParts();
        if (this.level().isClientSide) {
            this.clientEffects();
        }
    }

    private void updateParts() {
        TitanAnimator.pose(this, 1.0F, this.pose, this.frames);
        float s = this.getTitanScale();
        Vector3f tmp = new Vector3f();

        for (TitanPart part : this.parts) {
            part.rescale(s);
            this.frames.point(part.joint, part.local, part.joint == 3 ? this.pose.headScale : 1.0F, tmp);
            Vec3 w = TitanSkeleton.toWorld(tmp, this.getX(), this.getY(), this.getZ(), this.yBodyRot, s);
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
            part.xOld = part.xo;
            part.yOld = part.yo;
            part.zOld = part.zo;
            part.setPos(w.x, w.y - (double)part.getBbHeight() / 2.0, w.z);
            part.active = (part.kind != 3 || !this.isArmCut(part.side)) && this.dyingTicks == 0;
        }
    }

    public Vec3 skeletonPoint(int joint, Vector3f local) {
        Vector3f tmp = this.frames.point(joint, local, joint == 3 ? this.pose.headScale : 1.0F, new Vector3f());
        return TitanSkeleton.toWorld(tmp, this.getX(), this.getY(), this.getZ(), this.yBodyRot, this.getTitanScale());
    }

    public Vec3 handPos(float side) {
        return this.skeletonPoint(side < 0.0F ? 7 : 10, TitanSkeleton.HAND);
    }

    public Vec3 holdPoint(float side) {
        return this.handPos(side);
    }

    public Vec3 partAnchor(int index, Vec3 local, float pt) {
        if (index >= 0 && index < this.parts.length) {
            TitanPart part = this.parts[index];
            Vector3f m = this.frames
                .point(part.joint, new Vector3f((float)local.x, (float)local.y, (float)local.z), part.joint == 3 ? this.pose.headScale : 1.0F, new Vector3f());
            Vec3 w = TitanSkeleton.toWorld(m, this.getX(), this.getY(), this.getZ(), this.yBodyRot, this.getTitanScale());
            return w.add(part.getPosition(pt).subtract(part.position()));
        } else {
            return this.getPosition(pt).add(local);
        }
    }

    public Vec3 partLocal(int index, Vec3 world) {
        TitanPart part = this.parts[Mth.clamp(index, 0, this.parts.length - 1)];
        Vector3f m = TitanAnimator.toModel(world, this, 1.0F);
        new Matrix4f(this.frames.joint[part.joint]).invert().transformPosition(m);
        float sc = part.joint == 3 ? this.pose.headScale : 1.0F;
        return new Vec3((double)(m.x / sc), (double)(m.y / sc), (double)(m.z / sc));
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance()) {
            Vec3 v = this.getDeltaMovement();
            double vy = v.y - 0.08 * (this.isCrawler() ? 1.0 : 0.9);
            double hx;
            double hz;
            if (this.onGround()) {
                hx = Mth.lerp(0.3, v.x, this.walkVel.x);
                hz = Mth.lerp(0.3, v.z, this.walkVel.z);
            } else {
                hx = v.x * 0.99;
                hz = v.z * 0.99;
            }

            this.setDeltaMovement(hx, vy, hz);
            Vec3 before = this.position();
            this.move(MoverType.SELF, this.getDeltaMovement());
            Vec3 after = this.getDeltaMovement();
            this.setDeltaMovement(after.x, this.onGround() ? Math.max(0.0, after.y) * 0.0 : after.y * 0.98, after.z);
            if (this.horizontalCollision && this.walkVel.lengthSqr() > 1.0E-4 && this.level() instanceof ServerLevel sl) {
                this.crush(sl, before);
            }
        }

        this.updateWalk();
    }

    protected void updateWalk() {
        this.walkPhaseO = this.walkPhase;
        this.walkAmountO = this.walkAmount;
        this.runBlendO = this.runBlend;
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        float moved = (float)Math.sqrt(dx * dx + dz * dz);
        float stride = this.getTitanScale() * (this.isCrawler() ? 0.55F : 0.75F);
        this.walkPhase += moved / stride * 3.1415927F * 0.5F;
        float target = this.walkTarget(moved, stride);
        this.walkAmount = this.walkAmount + (target - this.walkAmount) * 0.15F;
        float run = this.hasFlag(8) ? 1.0F : 0.0F;
        this.runBlend = this.runBlend + (run - this.runBlend) * 0.1F;
        int prevStep = (int)Math.floor((double)(this.walkPhaseO / 3.1415927F));
        int step = (int)Math.floor((double)(this.walkPhase / 3.1415927F));
        if (step != prevStep && this.walkAmount > 0.3F) {
            this.footstep();
        }
    }

    protected float walkTarget(float moved, float stride) {
        return Mth.clamp(moved / (stride * 0.12F), 0.0F, 1.0F);
    }

    protected void footstep() {
        float s = this.getTitanScale();
        if (this.level().isClientSide) {
            clientFootstep.accept(this);
            BlockPos below = BlockPos.containing(this.getX(), this.getY() - 0.2, this.getZ());
            BlockState state = this.level().getBlockState(below);
            if (!state.isAir()) {
                for (int i = 0; i < (int)(6.0F * s); i++) {
                    this.level()
                        .addParticle(
                            new BlockParticleOption(ParticleTypes.BLOCK, state),
                            this.getX() + (this.random.nextDouble() - 0.5) * (double)this.getBbWidth() * 1.4,
                            this.getY() + 0.1,
                            this.getZ() + (this.random.nextDouble() - 0.5) * (double)this.getBbWidth() * 1.4,
                            0.0,
                            0.2,
                            0.0
                        );
                }
            }
        } else {
            this.level()
                .playSound(
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    ModSounds.TITAN_STEP.get(),
                    SoundSource.HOSTILE,
                    0.45F + s * 0.25F,
                    Mth.clamp(1.25F - s * 0.08F, 0.45F, 1.3F) + this.random.nextFloat() * 0.1F
                );
        }
    }

    private void crush(ServerLevel level, Vec3 before) {
        if (TakemConfig.TITAN_GRIEFING.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            Vec3 dir = new Vec3(this.walkVel.x, 0.0, this.walkVel.z).normalize();
            double speed = this.walkVel.horizontalDistance();
            AABB box = this.getBoundingBox().move(dir.x * 0.8, 0.0, dir.z * 0.8);
            double top = this.getY() + (double)this.getBbHeight() + 1.0;
            int broken = 0;
            int limit = 16 + (int)(this.getTitanScale() * 8.0F);
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
            Vec3 mid = this.getBoundingBox().getCenter();
            MutableBlockPos m = new MutableBlockPos();

            for (int y = Mth.floor(this.getY() + 0.5); y <= Mth.floor(top) && broken < limit; y++) {
                for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX) && broken < limit; x++) {
                    for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ) && broken < limit; z++) {
                        m.set(x, y, z);
                        if (!this.getBoundingBox().intersects(new AABB(m))) {
                            BlockState state = level.getBlockState(m);
                            if (this.canCrush(level, m, state)) {
                                double lateral = ((double)x + 0.5 - mid.x) * side.x + ((double)z + 0.5 - mid.z) * side.z;
                                double h = ((double)y - this.getY()) / (double)this.getBbHeight();
                                Vec3 v = dir.scale(0.3 + speed * 1.6 + this.random.nextDouble() * 0.45 + h * 0.35)
                                    .add(side.scale(Math.signum(lateral) * this.random.nextDouble() * 0.3))
                                    .add(0.0, 0.12 + this.random.nextDouble() * 0.3, 0.0);
                                Destruction.smash(level, m.immutable(), state, v, 0.2F, v.lengthSqr() > 1.0 ? 1 : 0);
                                broken++;
                            }
                        }
                    }
                }
            }

            if (broken > 0) {
                if (this.tickCount - this.lastCrushSound > 6) {
                    this.lastCrushSound = this.tickCount;
                    Destruction.crunch(level, box.getCenter(), 1.2F + this.getTitanScale() * 0.3F);
                }

                if (this.tickCount - this.lastCollapseRequest > 8) {
                    this.lastCollapseRequest = this.tickCount;
                    Destruction.requestCollapse(
                        level, BlockPos.containing(box.getCenter().x, this.getY() + 1.0, box.getCenter().z), 8 + (int)(this.getTitanScale() * 2.0F), 64, 3
                    );
                }
            }
        }
    }

    protected boolean canCrush(ServerLevel level, BlockPos pos, BlockState state) {
        return Destruction.canSmash(level, pos, state, 3.0F);
    }

    private void clientEffects() {
        float s = this.getTitanScale();

        for (int side = -1; side <= 1; side += 2) {
            if (this.isArmCut(side) && this.random.nextInt(3) == 0) {
                Vec3 p = this.skeletonPoint(side < 0 ? 6 : 9, new Vector3f(0.0F, 0.0F, 0.0F));
                this.level()
                    .addParticle(
                        ModParticles.STEAM.get(), p.x, p.y, p.z, (this.random.nextDouble() - 0.5) * 0.05, 0.08, (this.random.nextDouble() - 0.5) * 0.05
                    );
            }
        }

        if (this.isDowned() && this.random.nextInt(3) == 0) {
            for (int sidex = -1; sidex <= 1; sidex += 2) {
                Vec3 p = this.skeletonPoint(sidex < 0 ? 12 : 14, TitanSkeleton.SHIN_CENTER);
                this.level().addParticle(ModParticles.STEAM.get(), p.x, p.y, p.z, 0.0, 0.06, 0.0);
            }
        }

        if (this.dyingTicks > 0) {
            int n = this.dyingTicks < 40 ? 1 : (this.tickCount % 2 == 0 ? 1 + (int)(s * 0.5F) : 0);

            for (int i = 0; i < n; i++) {
                AABB b = this.getBoundingBox();
                double x = this.getX() + (this.random.nextDouble() - 0.5) * (double)this.getTitanHeight() * 0.5;
                double z = this.getZ() + (this.random.nextDouble() - 0.5) * (double)this.getTitanHeight() * 0.5;
                Vec3 off = new Vec3(x - this.getX(), 0.0, z - this.getZ());
                Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot).scale((double)this.getTitanHeight() * 0.45);
                this.level()
                    .addParticle(
                        this.dyingTicks < 40 ? ModParticles.STEAM.get() : ModParticles.BIG_STEAM.get(),
                        this.getX() + fwd.x + off.x,
                        b.minY + 0.5 + this.random.nextDouble() * (double)s,
                        this.getZ() + fwd.z + off.z,
                        (this.random.nextDouble() - 0.5) * 0.04,
                        0.05 + this.random.nextDouble() * 0.08,
                        (this.random.nextDouble() - 0.5) * 0.04
                    );
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        if (!this.threatSet && this.tickCount % 20 == 5 && !this.isDummy()
            && this.level().getNearestPlayer(this, 160.0) instanceof net.minecraft.server.level.ServerPlayer sp) {
            // story bosses and event titans come at the strength of the player they came for
            this.threatSet = true;
            int tier = com.takem.titans.story.Story.threatTier(sp);
            if (tier > this.threat) {
                boolean fresh = this.napeHealth < 0.0F || this.napeHealth >= this.maxNapeHealth();
                this.threat = tier;
                if (fresh) {
                    this.napeHealth = this.maxNapeHealth();
                }
            }
        }

        super.customServerAiStep();
        if (!this.isDeadOrDying()) {
            ServerLevel level = (ServerLevel)this.level();
            this.tickTimers(level);
            if (this.isDummy()) {
                this.walkVel = Vec3.ZERO;
                this.setTarget(null);
                return;
            }

            LivingEntity target = this.getTarget();
            if (--this.targetScan <= 0) {
                this.targetScan = 10;
                target = this.findTarget(level);
                this.setTarget(target);
            }

            boolean sluggish = this.isSluggish(level);
            this.setFlag(16, sluggish);
            if (target != null) {
                this.getLookControl().setLookAt(target, 12.0F, 30.0F);
            }

            TitanAction action = this.getAction();
            if (action != TitanAction.NONE) {
                this.tickAction(level, action, target);
                if (this.getAction() != TitanAction.NONE) {
                    this.walkVel = this.walkVel.scale(0.6);
                    if (target != null && !this.getAction().isHold() && !this.getAction().isBite()) {
                        this.turnTowards(target.position(), this.turnRate() * 0.5F);
                    }

                    return;
                }
            }

            if (this.isDowned()) {
                this.walkVel = Vec3.ZERO;
            } else {
                if (target != null) {
                    double dist = this.horizontalDistanceTo(target);
                    if (this.attackCooldown <= 0) {
                        this.chooseAttack(target, dist);
                    }

                    if (this.getAction() == TitanAction.NONE) {
                        this.chase(target, dist, sluggish);
                    }
                } else {
                    this.wander(level, sluggish);
                }

                if (--this.idleSound <= 0) {
                    this.idleSound = 280 + this.random.nextInt(420);
                    this.voice(ModSounds.TITAN_GROAN.get(), 1.5F + this.getTitanScale() * 0.4F, this.voicePitch(), 70, 0);
                }
            }
        }
    }

    protected void voice(SoundEvent sound, float volume, float pitch, int hold, int priority) {
        if (this.voiceCooldown <= 0 || priority > this.voicePriority) {
            this.voiceCooldown = hold;
            this.voicePriority = priority;
            this.level().playSound(null, this.getX(), this.getEyeY(), this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
        }
    }

    protected float voicePitch() {
        return Mth.clamp(1.25F - this.getTitanScale() * 0.09F, 0.5F, 1.3F) + (this.random.nextFloat() - 0.5F) * 0.15F;
    }

    private void tickTimers(ServerLevel level) {
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        if (this.roarCooldown > 0) {
            this.roarCooldown--;
        }

        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            if (this.isArmCut(side) && --this.armRegen[i] <= 0) {
                this.setFlag(side < 0 ? 1 : 2, false);
                this.armDamage[i] = 0.0F;
                level.playSound(null, this.getX(), this.getEyeY(), this.getZ(), ModSounds.TITAN_STEAM.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
            }

            if (this.isLegCut(side) && --this.legRegen[i] <= 0) {
                this.setFlag(side < 0 ? 64 : 128, false);
                this.legDamage[i] = 0.0F;
                level.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(), ModSounds.TITAN_STEAM.get(), SoundSource.HOSTILE, 1.5F, 0.7F);
            }

            this.armDamage[i] = Math.max(0.0F, this.armDamage[i] - 0.02F);
            this.legDamage[i] = Math.max(0.0F, this.legDamage[i] - 0.02F);
        }

        if (this.isDowned() && --this.downedTicks <= 0 && !this.isLegCut(-1) && !this.isLegCut(1)) {
            this.setFlag(4, false);
            this.legDamage[0] = this.legDamage[1] = 0.0F;
        }

        if (this.tickCount % 10 == 0) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(this.getMaxHealth() * 0.01F);
            }

            float max = this.maxNapeHealth();
            if (this.napeHealth < max) {
                this.napeHealth = Math.min(max, this.napeHealth + max * 0.01F);
            }
        }

        if (this.held != null && (!this.held.isAlive() || this.held.isRemoved() || this.held instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            this.release(false);
        }
    }

    protected boolean isSluggish(ServerLevel level) {
        return TakemConfig.TITANS_SLEEP_AT_NIGHT.get() && !level.isDay() && !this.getTags().contains(com.takem.titans.world.NightMission.AWAKE);
    }

    @Nullable
    protected LivingEntity findTarget(ServerLevel level) {
        LivingEntity current = this.getTarget();
        double range = this.detectionRange() * (this.hasFlag(16) ? 0.35 : 1.0);
        if (current != null && current.isAlive() && this.canTarget(current) && this.distanceToSqr(current) < range * range * 1.6) {
            return current;
        } else {
            LivingEntity best = null;
            double bestD = range * range;

            for (Player p : level.players()) {
                if (this.canTarget(p)) {
                    double d = this.distanceToSqr(p);
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }

            if (best == null && this.tickCount % 3 == 0) {
                double rj = Math.min(range, 56.0);

                for (JaegerEntity j : level.getEntitiesOfClass(JaegerEntity.class, this.getBoundingBox().inflate(rj))) {
                    if (this.canTarget(j)) {
                        double d = this.distanceToSqr(j);
                        if (d < rj * rj && d < bestD) {
                            bestD = d;
                            best = j;
                        }
                    }
                }
            }

            if (best == null && this.tickCount % 3 == 0) {
                double r = Math.min(range, 48.0);

                for (TownsfolkEntity t : level.getEntitiesOfClass(TownsfolkEntity.class, this.getBoundingBox().inflate(r))) {
                    if (this.canTarget(t)) {
                        double d = this.distanceToSqr(t);
                        if (d < r * r && d < bestD) {
                            bestD = d;
                            best = t;
                        }
                    }
                }
            }

            if (best != null && current == null && this.roarCooldown <= 0 && this.getAction() == TitanAction.NONE) {
                this.spotted(best);
            }

            return best;
        }
    }

    protected boolean canTarget(LivingEntity e) {
        if (e instanceof JaegerEntity j) {
            return j.isAlive() && !j.isSpectator();
        } else if (e instanceof Player p) {
            return !p.isCreative() && !p.isSpectator() && p.isAlive() && (!(p instanceof ServerPlayer sp) || !Story.inCutscene(sp));
        } else {
            return e instanceof TownsfolkEntity t ? t.isAlive() && !t.isStoryCharacter() : false;
        }
    }

    protected void spotted(LivingEntity target) {
        this.roarCooldown = 400;
        if (this.random.nextInt(3) == 0) {
            this.setAction(TitanAction.ROAR, target);
            this.voice(ModSounds.TITAN_ROAR.get(), 2.5F + this.getTitanScale() * 0.5F, this.voicePitch(), 60, 2);
        }
    }

    protected double horizontalDistanceTo(Entity e) {
        double dx = e.getX() - this.getX();
        double dz = e.getZ() - this.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    protected void chase(LivingEntity target, double dist, boolean sluggish) {
        double speed = this.walkSpeed(this.getTitanHeight()) * (1.0 + 0.06 * (double)this.threat) * (sluggish ? 0.35 : 1.0) * (double)(this.isDowned() ? 0 : 1);
        double stop = (double)this.getTitanScale() * 1.4;
        Vec3 dest = target.position();
        this.turnTowards(dest, this.turnRate());
        double sp = speed * Mth.clamp((dist - stop) / 3.0, 0.0, 1.0);
        float yaw = this.getYRot() * 0.017453292F;
        this.walkVel = new Vec3((double)(-Mth.sin(yaw)) * sp, 0.0, (double)Mth.cos(yaw) * sp);
    }

    public void marchTo(Vec3 dest) {
        this.wanderTarget = dest;
        this.wanderCooldown = 900;
    }

    protected void wander(ServerLevel level, boolean sluggish) {
        if (this.wanderTarget == null || --this.wanderCooldown <= 0 || this.position().distanceToSqr(this.wanderTarget) < 9.0) {
            this.wanderCooldown = 200 + this.random.nextInt(300);
            if (this.random.nextInt(3) != 0 && !sluggish) {
                double a = this.random.nextDouble() * 3.141592653589793 * 2.0;
                double r = 12.0 + this.random.nextDouble() * 30.0;
                this.wanderTarget = this.position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
            } else {
                this.wanderTarget = null;
            }
        }

        if (this.wanderTarget == null) {
            this.walkVel = Vec3.ZERO;
        } else {
            this.turnTowards(this.wanderTarget, this.turnRate() * 0.5F);
            double sp = this.walkSpeed(this.getTitanHeight()) * 0.45 * (sluggish ? 0.3 : 1.0);
            float yaw = this.getYRot() * 0.017453292F;
            this.walkVel = new Vec3((double)(-Mth.sin(yaw)) * sp, 0.0, (double)Mth.cos(yaw) * sp);
        }
    }

    protected void turnTowards(Vec3 dest, float rate) {
        double dx = dest.x - this.getX();
        double dz = dest.z - this.getZ();
        if (!(dx * dx + dz * dz < 1.0E-4)) {
            float want = (float)(Mth.atan2(dz, dx) * 180.0 / 3.1415927410125732) - 90.0F;
            this.setYRot(Mth.approachDegrees(this.getYRot(), want, rate));
            this.yBodyRot = Mth.approachDegrees(this.yBodyRot, this.getYRot(), rate);
        }
    }

    protected Vector3f targetModel(LivingEntity target) {
        return TitanAnimator.toModel(target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0), this, 1.0F);
    }

    protected boolean tryGrab(LivingEntity target) {
        if (this.held != null) {
            return false;
        } else {
            Vector3f m = this.targetModel(target);
            float reach = 36.1F;
            int side = m.x < 0.0F ? -1 : 1;
            if (this.isArmCut(side)) {
                side = -side;
            }

            if (this.isArmCut(side)) {
                return false;
            } else {
                Vector3f shoulder = TitanAnimator.stoopedShoulder(side, TitanAnimator.grabStoop(m));
                float d = m.distance(shoulder);
                if (!(d > reach) && !(m.z > 6.0F)) {
                    this.setAction(side < 0 ? TitanAction.GRAB_RIGHT : TitanAction.GRAB_LEFT, target);
                    this.attackCooldown = 30 + this.random.nextInt(25);
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    protected boolean trySwat(LivingEntity target) {
        Vector3f m = this.targetModel(target);
        float reach = 39.899998F;
        Vector3f head = this.frames.point(3, TitanSkeleton.HEAD_CENTER, 1.0F, new Vector3f());
        boolean onBack = m.z > 0.0F && m.distance(head) < reach;
        if (!onBack && this.random.nextInt(4) != 0) {
            return false;
        } else if (m.distance(head) > reach * 1.2F) {
            return false;
        } else {
            int side = m.x < 0.0F ? -1 : 1;
            if (this.isArmCut(side)) {
                side = -side;
            }

            if (this.isArmCut(side)) {
                return false;
            } else {
                this.setAction(side < 0 ? TitanAction.SWAT_RIGHT : TitanAction.SWAT_LEFT, target);
                this.attackCooldown = 25 + this.random.nextInt(20);
                return true;
            }
        }
    }

    protected boolean tryStomp(LivingEntity target) {
        if (!target.onGround()) {
            return false;
        } else {
            double dist = this.horizontalDistanceTo(target);
            if (!(dist > (double)this.getBbWidth() * 1.4) && !(target.getY() > this.getY() + (double)this.getTitanHeight() * 0.2)) {
                this.setAction(TitanAction.STOMP, target);
                this.attackCooldown = 35 + this.random.nextInt(20);
                return true;
            } else {
                return false;
            }
        }
    }

    protected void tickAction(ServerLevel level, TitanAction action, @Nullable LivingEntity target) {
        float k = (float)this.actionTicks / (float)action.duration;
        float side = action.side();
        if (action.isGrab()) {
            if (this.actionTicks == (int)((float)action.duration * 0.38F)) {
                level.playSound(null, this.getX(), this.getEyeY(), this.getZ(), ModSounds.TITAN_GRAB.get(), SoundSource.HOSTILE, 2.0F, this.voicePitch());
            }

            if (k >= 0.45F && k <= 0.66F && this.held == null && !this.isArmCut(side < 0.0F ? -1 : 1)) {
                Vec3 hand = this.handPos(side);
                double r = 0.6749999999999999 * (double)this.getTitanScale() + 0.6;

                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(hand, hand).inflate(r))) {
                    if (!(e instanceof AbstractTitan)
                        && this.canTarget(e)
                        && e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).distanceTo(hand) < r + (double)e.getBbWidth() * 0.5) {
                        this.grab(e, side);
                        return;
                    }
                }
            }
        } else if (action.isHold()) {
            if (this.held == null) {
                this.setAction(TitanAction.NONE, null);
                return;
            }

            if (this.actionTicks % 12 == 0 && this.held instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(2, 25, 6, 0));
            }

            if (this.actionTicks >= action.duration) {
                this.setAction(side < 0.0F ? TitanAction.BITE_RIGHT : TitanAction.BITE_LEFT, this.held);
                return;
            }
        } else if (action.isBite()) {
            if (this.actionTicks == (int)((float)action.duration * 0.5F)) {
                this.bite(level);
            }
        } else if (action.isSwat()) {
            if (k >= 0.35F && k <= 0.62F) {
                Vec3 hand = this.handPos(side);
                double r = 0.7875 * (double)this.getTitanScale() + 0.8;

                for (LivingEntity ex : level.getEntitiesOfClass(LivingEntity.class, new AABB(hand, hand).inflate(r))) {
                    if (!(ex instanceof AbstractTitan) && ex != this.held && ex.invulnerableTime <= 0) {
                        if (ex instanceof Player) {
                            Player p = (Player)ex;
                            if (p.isCreative() || p.isSpectator()) {
                                continue;
                            }
                        }

                        float dmg = (float)(7.0 + (double)this.getTitanScale() * 1.5);
                        if (ex.hurt(this.damageSources().mobAttack(this), dmg)) {
                            Vec3 fling = Vec3.directionFromRotation(0.0F, this.yBodyRot + side * 90.0F)
                                .scale(-1.0)
                                .add(Vec3.directionFromRotation(0.0F, this.yBodyRot))
                                .normalize()
                                .scale(1.2 + (double)this.getTitanScale() * 0.15);
                            ex.push(fling.x, 0.5, fling.z);
                            ex.hurtMarked = true;
                            level.playSound(null, ex.getX(), ex.getY(), ex.getZ(), ModSounds.TITAN_FALL.get(), SoundSource.HOSTILE, 0.8F, 1.6F);
                        }
                    }
                }
            }
        } else if (action == TitanAction.STOMP && this.actionTicks == (int)((float)action.duration * 0.6F)) {
            Vec3 foot = this.skeletonPoint(12, new Vector3f(0.0F, 10.0F, 0.0F));
            double r = (double)this.getTitanScale() * 0.9 + 1.0;
            level.playSound(null, foot.x, foot.y, foot.z, ModSounds.TITAN_STEP.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
            level.sendParticles(ParticleTypes.EXPLOSION, foot.x, foot.y + 0.3, foot.z, 2, r * 0.3, 0.1, r * 0.3, 0.0);

            for (LivingEntity exx : level.getEntitiesOfClass(LivingEntity.class, new AABB(foot, foot).inflate(r, 2.0, r))) {
                if (!(exx instanceof AbstractTitan)) {
                    if (exx instanceof Player) {
                        Player p = (Player)exx;
                        if (p.isCreative() || p.isSpectator()) {
                            continue;
                        }
                    }

                    exx.hurt(this.damageSources().mobAttack(this), (float)(6.0 + (double)this.getTitanScale() * 2.0));
                }
            }

            shakeNearby(level, foot, r * 6.0, 0.5F, 10);
        }

        if (this.actionTicks >= action.duration && !action.isHold()) {
            this.setAction(TitanAction.NONE, null);
        }
    }

    protected void grab(LivingEntity e, float side) {
        this.held = e;
        this.setAction(side < 0.0F ? TitanAction.HOLD_RIGHT : TitanAction.HOLD_LEFT, e);
        this.level().playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.HOOK_HIT_FLESH.get(), SoundSource.HOSTILE, 1.5F, 0.6F);
        if (e instanceof ServerPlayer sp) {
            OdmServer.data(sp).grabbedBy = this.getId();
            OdmServer.data(sp).struggle = 0;
            PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(3, this.getId(), 0, 0));
        }
    }

    public void release(boolean escaped) {
        LivingEntity e = this.held;
        this.held = null;
        if (this.getAction().isHold() || this.getAction().isBite()) {
            this.setAction(TitanAction.NONE, null);
        }

        if (e instanceof ServerPlayer sp) {
            OdmServer.data(sp).grabbedBy = -1;
            PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(3, -1, 0, 0));
            if (escaped) {
                Vec3 out = Vec3.directionFromRotation(0.0F, this.yBodyRot).scale(0.6);
                sp.setDeltaMovement(out.x, 0.6, out.z);
                sp.hurtMarked = true;
            }
        }

        this.attackCooldown = Math.max(this.attackCooldown, 40);
    }

    @Nullable
    public LivingEntity getHeld() {
        return this.held;
    }

    public void cutFree(ServerPlayer player, boolean armed) {
        if (this.held == player) {
            TitanAction a = this.getAction();
            int side = a.side() > 0.0F ? 1 : -1;
            if (armed && !this.isCrawler()) {
                this.severArm(side, (ServerLevel)this.level());
                BladeItem.wear(player.getMainHandItem(), player, 2);
            } else {
                if (this.isCrawler() && armed) {
                    this.napeHealth -= 4.0F;
                    ((ServerLevel)this.level())
                        .sendParticles(ModParticles.BLOOD.get(), player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.4, 0.4, 0.2);
                }

                this.release(true);
            }

            if (this.getHealth() > 0.0F) {
                this.setAction(TitanAction.FLINCH, null);
            }
        }
    }

    protected void bite(ServerLevel level) {
        LivingEntity e = this.held;
        if (e != null) {
            Vec3 mouth = this.skeletonPoint(3, TitanSkeleton.MOUTH);
            level.playSound(null, mouth.x, mouth.y, mouth.z, ModSounds.TITAN_BITE.get(), SoundSource.HOSTILE, 2.5F, this.voicePitch());
            level.sendParticles(ModParticles.BLOOD.get(), mouth.x, mouth.y, mouth.z, 60, 0.5, 0.5, 0.5, 0.25);
            DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TITAN_BITE), this);
            boolean eat = TakemConfig.TITANS_EAT_PLAYERS.get();
            this.release(false);
            e.invulnerableTime = 0;
            e.hurt(src, eat ? 1000.0F : 8.0F);
            if (!eat) {
                Vec3 out = Vec3.directionFromRotation(0.0F, this.yBodyRot).scale(0.8);
                e.setDeltaMovement(out.x, 0.3, out.z);
                e.hurtMarked = true;
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.held != null) {
            TitanAction a = this.getAction();
            float side = a.side() == 0.0F ? -1.0F : a.side();
            Vec3 hand = this.holdPoint(side);
            Vec3 p = hand.subtract(0.0, (double)this.held.getBbHeight() * 0.5, 0.0);
            this.held.fallDistance = 0.0F;
            if (this.held instanceof ServerPlayer sp) {
                if (sp.position().distanceToSqr(p) > 9.0) {
                    sp.connection.teleport(p.x, p.y, p.z, sp.getYRot(), sp.getXRot());
                }
            } else {
                this.held.setPos(p.x, p.y, p.z);
                this.held.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**
     * Titans only die through the nape. Everything that isn't a nape strike (cuts to the body, arrows, fire, stray
     * explosions) wounds, staggers, blinds or cripples them, and they regenerate, but it never wears them down to death.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isDeadOrDying()) {
            return false;
        } else if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        } else if (source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)) {
            return false;
        } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : this.position();
            return this.blastHit(at, amount * 1.5F, source.getEntity() instanceof Player p ? p : null, source);
        } else {
            if (source.getEntity() instanceof Player p && source.getDirectEntity() == p) {
                TitanPart part = this.aimedPart(p);
                if (part != null) {
                    return this.hurtPart(part, source, amount);
                }
            }

            if (source.getEntity() instanceof LivingEntity attacker && this.canTarget(attacker) && this.getTarget() == null) {
                this.setTarget(attacker);
            }

            return false;
        }
    }

    @Nullable
    public TitanPart aimedPart(Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(p.entityInteractionRange() + 2.0));
        TitanPart best = null;
        double bestD = 1.7976931348623157E308;

        for (TitanPart part : this.parts) {
            if (part.active) {
                Optional<Vec3> hit = part.getBoundingBox().clip(eye, end);
                if (hit.isPresent()) {
                    double d = hit.get().distanceToSqr(eye);
                    if (d < bestD) {
                        bestD = d;
                        best = part;
                    }
                }
            }
        }

        return best;
    }

    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isDeadOrDying() && part.active) {
            ServerLevel level = (ServerLevel)this.level();
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                return false;
            } else {
                Entity attacker = source.getEntity();
                Player player = attacker instanceof Player pl ? pl : null;
                boolean melee = attacker != null && source.getDirectEntity() == attacker;
                boolean blade = melee
                    && player != null
                    && player.getMainHandItem().getItem() instanceof BladeItem
                    && !BladeItem.isBroken(player.getMainHandItem());
                if (this.partHitCooldown[part.index] > 0 && melee) {
                    return false;
                } else {
                    this.partHitCooldown[part.index] = 4;
                    OdmPlayerData combat = player instanceof ServerPlayer sp0 ? OdmServer.data(sp0) : null;
                    float power;
                    if (blade) {
                        double speed = combat != null ? combat.recentSpeed : player.getDeltaMovement().length();
                        float speedFactor = (float)Mth.clamp(1.0 + 5.0 * Math.max(0.0, speed - 0.2), 1.0, 9.0);
                        float comboFactor = combat != null ? combat.comboMultiplier() : 1.0F;
                        power = amount * speedFactor * comboFactor * TakemConfig.NAPE_DAMAGE_MULTIPLIER.get().floatValue();
                    } else if (melee) {
                        power = amount * 0.5F;
                    } else {
                        power = amount * 0.25F;
                    }

                    if (combat != null) {
                        power *= Story.damageBonus(combat.level);
                    }

                    if (blade) {
                        power *= Skills.bladeMul(player);
                        power *= Skills.comboArtsMul(player, combat);
                        if (((BladeItem)player.getMainHandItem().getItem()).tier == BladeTier.SERRATED && (part.kind == 3 || part.kind == 4)) {
                            // the teeth bite into limbs: arms come off and legs give way far sooner
                            power *= 1.8F;
                        }
                    }

                    int hitKind = part.kind;
                    if (part.kind == 2 && player != null && this.hitEyes(player, part)) {
                        hitKind = 5;
                    }

                    Vec3 at = part.position().add(0.0, (double)(part.getBbHeight() / 2.0F), 0.0);
                    if (player != null) {
                        Vec3 eye = player.getEyePosition();
                        at = part.getBoundingBox().clip(eye, eye.add(player.getLookAngle().scale(8.0))).orElse(at);
                    }

                    this.cutEffects(level, at, player, power, part.kind == 1 && blade);
                    if (!this.isDummy()) {
                        this.addWound(part.index, at, power);
                        this.addTrauma(power * (blade ? 1.4F : 0.8F));
                    }

                    if (blade && power > 25.0F && !this.isDummy()) {
                        Gore.chunks(level, at, Math.min(30, (int)(power / 8.0F)), 0.5);
                    }

                    if (blade && power > 60.0F && !this.isDummy()) {
                        Gore.splatter(level, at, Math.min(60, (int)(power / 4.0F)), 0.7);
                        Gore.screen(level, at, Math.min(1.0F, power / 300.0F));
                    }

                    if (blade
                        && combat != null
                        && combat.aftershockHits > 0
                        && level.getGameTime() < combat.aftershockUntil
                        && player instanceof ServerPlayer asp) {
                        combat.aftershockHits--;
                        OdmServer.aftershock(asp, at);
                    }

                    if (this.fleshCooldown <= 0) {
                        this.fleshCooldown = 7;
                        level.playSound(
                            null,
                            at.x,
                            at.y,
                            at.z,
                            ModSounds.TITAN_HIT_FLESH.get(),
                            SoundSource.PLAYERS,
                            1.2F + Math.min(1.5F, power / 60.0F),
                            0.85F + this.random.nextFloat() * 0.3F
                        );
                    }

                    if (blade && power > 45.0F) {
                        level.playSound(
                            null,
                            at.x,
                            at.y,
                            at.z,
                            ModSounds.BLADE_IMPACT.get(),
                            SoundSource.PLAYERS,
                            1.5F + Math.min(2.5F, power / 80.0F),
                            0.9F + this.random.nextFloat() * 0.2F
                        );
                    }

                    boolean killed = false;
                    switch (hitKind) {
                        case 1:
                            if (this.napeHealth < 0.0F) {
                                this.napeHealth = this.maxNapeHealth();
                            }

                            float napePower = blade ? power * Skills.napeMul(player) : power * 0.4F;
                            if (blade && Skills.has(player, Skill.EXECUTIONER) && this.napeHealth < this.maxNapeHealth() * 0.35F) {
                                napePower *= 2.0F;
                            }

                            this.napeHealth -= napePower;
                            level.playSound(null, at.x, at.y, at.z, ModSounds.BLADE_NAPE.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
                            power = napePower;
                            if (this.napeHealth <= 0.0F) {
                                killed = true;
                                this.killBy(source, player, (int)napePower);
                            }
                            break;
                        case 2:
                        default:
                            level.playSound(null, at.x, at.y, at.z, ModSounds.BLADE_HIT.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
                            break;
                        case 3:
                            int i = part.side < 0 ? 0 : 1;
                            this.armDamage[i] = this.armDamage[i] + power;
                            if (this.armDamage[i] >= this.limbThreshold() && !this.isArmCut(part.side)) {
                                this.severArm(part.side, level);
                            }

                            level.playSound(null, at.x, at.y, at.z, ModSounds.BLADE_HIT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                            break;
                        case 4:
                            int leg = part.side < 0 ? 0 : 1;
                            this.legDamage[leg] = this.legDamage[leg] + power;
                            if (this.legDamage[leg] >= this.limbThreshold() * 1.7F && !this.isLegCut(part.side) && !(this instanceof ColossalTitan)) {
                                this.severLeg(part.side, level);
                            } else if (this.legDamage[leg] >= this.limbThreshold() && !this.isDowned()) {
                                this.knockDown(level, this.legDamage[0] >= this.limbThreshold() && this.legDamage[1] >= this.limbThreshold() ? 200 : 130);
                            }

                            level.playSound(null, at.x, at.y, at.z, ModSounds.BLADE_HIT.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
                            break;
                        case 5:
                            if (this.getAction() != TitanAction.BLINDED || this.actionTicks > 40) {
                                if (this.held == null) {
                                    this.setAction(TitanAction.BLINDED, null);
                                }

                                this.setTarget(null);
                                this.targetScan = 80;
                                this.voice(ModSounds.TITAN_HURT.get(), 2.5F, this.voicePitch(), 40, 1);
                            }
                    }

                    if (!killed && player instanceof ServerPlayer sp) {
                        PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(0, Math.round(power), hitKind, Math.round(this.getTitanHeight() * 10.0F)));
                    }

                    if (blade && combat != null && player instanceof ServerPlayer sp) {
                        combat.combo = Math.min(99, combat.combo + (killed ? 2 : 1));
                        combat.comboTimer = 0;
                        combat.groundTicks = 0;
                        PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(8, combat.combo, Math.round(combat.comboMultiplier() * 100.0F), 0));
                        if (combat.combo >= Skills.overdriveCombo(sp) && !combat.overdrive() && combat.overdriveCooldown == 0) {
                            OdmServer.startOverdrive(sp, combat);
                        }

                        if (!killed && combat.combo % 4 == 0 && Skills.has(sp, Skill.INCENDIARY_EDGE)) {
                            Boom.Spec spec = new Boom.Spec().radius(3.5).damage(8.0F, 22.0F).fx(4, 2.6F, 0.0F).owner(sp, sp);
                            spec.fire = true;
                            spec.glass = false;
                            spec.volume = 2.5F;
                            spec.knock = 0.6;
                            Boom.explode(level, at, spec);
                        }

                        if (!killed && power > 140.0F) {
                            PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(10, 60, 90, 0));
                        }
                    }

                    if (!killed) {
                        if (player != null && this.canTarget(player)) {
                            this.setTarget(player);
                        }

                        TitanAction a = this.getAction();
                        boolean big = power > Math.max(40.0F, this.limbThreshold() * 0.35F);
                        if (big && this.held == null && !this.isDowned() && !a.isHold() && !a.isBite() && a != TitanAction.LEAP && a != TitanAction.STEAM) {
                            this.setAction(TitanAction.FLINCH, null);
                            if (player != null) {
                                Vec3 push = this.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
                                if (push.lengthSqr() > 1.0E-4) {
                                    this.setDeltaMovement(this.getDeltaMovement().add(push.normalize().scale(Math.min(0.5, (double)power / 400.0))));
                                }
                            }

                            this.voice(ModSounds.TITAN_HURT.get(), 2.0F, this.voicePitch() * 1.1F, 40, 1);
                        } else if (a == TitanAction.NONE && this.random.nextInt(3) == 0) {
                            this.setAction(TitanAction.FLINCH, null);
                        }
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    public boolean traumaHit(float napeDamage, @Nullable Player credit, DamageSource source, boolean knock) {
        if (this.level() instanceof ServerLevel level && !this.isDeadOrDying() && !(napeDamage <= 0.0F)) {
            if (this.napeHealth < 0.0F) {
                this.napeHealth = this.maxNapeHealth();
            }

            this.napeHealth -= napeDamage;
            Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
            if (!this.isDummy()) {
                level.sendParticles(ModParticles.BLOOD.get(), nape.x, nape.y, nape.z, Math.min(60, 6 + (int)(napeDamage / 4.0F)), 0.6, 0.6, 0.6, 0.25);
            }
            if (this.napeHealth <= 0.0F) {
                this.killBy(source, credit, (int)napeDamage);
                return true;
            }

            if (knock && !this.isDowned() && this.held == null) {
                this.knockDown(level, 70 + this.random.nextInt(40));
            } else if (this.getAction() == TitanAction.NONE) {
                this.setAction(TitanAction.FLINCH, null);
            }

            if (credit instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(0, Math.round(napeDamage), 1, Math.round(this.getTitanHeight() * 10.0F)));
                if (this.canTarget(sp)) {
                    this.setTarget(sp);
                }
            }

            return false;
        }

        return false;
    }

    public boolean blastHit(Vec3 at, float power, @Nullable Player credit, DamageSource source) {
        Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
        double reach = 3.0 + (double)this.getTitanScale() * 1.2;
        float share = (float)Mth.clamp(1.2 - nape.distanceTo(at) / (reach * 1.6), 0.06, 1.0);
        return this.traumaHit(power * share, credit, source, power > 35.0F);
    }

    public boolean impactPart(TitanPart part, float power, @Nullable Player credit, DamageSource source) {
        if (this.level() instanceof ServerLevel level && !this.isDeadOrDying() && part.active) {
            switch (part.kind) {
                case 1:
                    return this.traumaHit(power, credit, source, true);
                case 2:
                default:
                    return this.traumaHit(power * 0.12F, credit, source, power > 60.0F);
                case 3:
                    int i = part.side < 0 ? 0 : 1;
                    this.armDamage[i] = this.armDamage[i] + power * 2.0F;
                    if (this.armDamage[i] >= this.limbThreshold() && !this.isArmCut(part.side)) {
                        this.severArm(part.side, level);
                    }

                    return this.traumaHit(power * 0.06F, credit, source, false);
                case 4:
                    if (!this.isDowned()) {
                        this.knockDown(level, 90 + (int)Math.min(120.0F, power));
                    }

                    return this.traumaHit(power * 0.06F, credit, source, false);
            }
        }

        return false;
    }

    public float maxNapeHealth() {
        return this.napeHealthFor(this.getTitanHeight()) * this.threatMul();
    }

    private float threatMul() {
        return 1.0F + 0.35F * (float)this.threat;
    }

    public int threat() {
        return this.threat;
    }

    /**
     * Fits a freshly spawned titan to the progress of the player it came for: small, slow pures for a new recruit;
     * full-grown, tougher and quicker ones once the story moves past the walls.
     */
    public void applyThreat(int tier) {
        this.threatSet = true;
        this.threat = Mth.clamp(tier, 0, 4);
        if (this.isRegularTitan()) {
            float h = this.getTitanHeight();
            float cap = switch (this.threat) {
                case 0 -> 9.0F;
                case 1 -> 12.0F;
                default -> 99.0F;
            };
            float floor = this.threat >= 3 ? 8.0F + this.random.nextFloat() * 3.0F : 0.0F;
            this.setTitanHeight(Mth.clamp(h, Math.min(floor, cap), cap));
        }
        this.napeHealth = this.maxNapeHealth();
    }

    /** Pure, abnormal and crawler titans; shifters and the colossal kinds keep their fixed size. */
    protected boolean isRegularTitan() {
        return this instanceof PureTitan || this instanceof AbnormalTitan && !(this instanceof JawTitan) || this instanceof CrawlerTitan;
    }

    public float napeHealthLeft() {
        return this.napeHealth < 0.0F ? this.maxNapeHealth() : this.napeHealth;
    }

    private void cutEffects(ServerLevel level, Vec3 at, @Nullable Player player, float power, boolean nape) {
        Vec3 look = player != null ? player.getLookAngle() : new Vec3(0.0, 0.0, 1.0);
        Vec3 across = look.cross(new Vec3(0.0, 1.0, 0.0));
        across = across.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : across.normalize();
        Vec3 out = look.scale(-1.0);
        Vec3 from = at.add(out.scale(0.3));
        Vec3 spray = out.add(across.scale((this.random.nextDouble() - 0.5) * 1.2)).add(0.0, 0.35, 0.0);
        if (!this.isDummy()) {
            Fx.send(level, 7, from, (float)spray.x, (float)spray.y, (float)spray.z, 1.5F + Math.min(7.0F, power / 22.0F), 96.0);
        }
        if (player != null) {
            boolean var10000;
            label64: {
                if (player instanceof ServerPlayer sp && OdmServer.isRed(sp, OdmServer.data(sp))) {
                    var10000 = true;
                    break label64;
                }

                var10000 = false;
            }

            float radius = Mth.clamp(1.8F + power / 40.0F, 1.8F, nape ? 6.5F : 5.0F);
            float roll = (float)(this.random.nextBoolean() ? 1 : -1) * (15.0F + this.random.nextFloat() * 55.0F);
            Fx.send(level, 3, at, player.getYRot(), player.getXRot(), roll, var10000 ? -radius : radius, 96.0);
        }

        if (!this.isDummy()) {
            level.sendParticles(ModParticles.STEAM.get(), at.x, at.y, at.z, 6 + (int)Math.min(14.0F, power / 8.0F), 0.3, 0.3, 0.3, 0.03);
        }
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8 + (int)Math.min(24.0F, power / 4.0F), 0.3, 0.3, 0.3, 0.7);
        if (nape) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.6);
            level.sendParticles(ModParticles.SPARK.get(), at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.4);
        }

        if (power > 35.0F) {
            boolean var18;
            label56: {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.65F);
                if (player instanceof ServerPlayer sp && OdmServer.isRed(sp, OdmServer.data(sp))) {
                    var18 = true;
                    break label56;
                }

                var18 = false;
            }

            Fx.send(level, 24, at, power, var18 ? 1.0F : 0.0F, this.isDummy() ? 1.0F : 0.0F, 0.0F, 96.0);
        }

        if (power > 80.0F) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.4F, 0.6F);
        }
    }

    protected float limbThreshold() {
        return this.maxNapeHealth() * 0.6F;
    }

    private boolean hitEyes(Player player, TitanPart head) {
        Vec3 eye = player.getEyePosition();
        Optional<Vec3> hit = head.getBoundingBox().clip(eye, eye.add(player.getLookAngle().scale(10.0)));
        if (hit.isEmpty()) {
            return false;
        } else {
            Vec3 eyes = this.skeletonPoint(3, TitanSkeleton.EYES);
            return hit.get().distanceTo(eyes) < (double)this.getTitanScale() * 0.28;
        }
    }

    protected void severArm(int side, ServerLevel level) {
        int i = side < 0 ? 0 : 1;
        this.setFlag(side < 0 ? 1 : 2, true);
        this.armRegen[i] = 400 + this.random.nextInt(200);
        this.spurt[i] = 220;
        this.addTrauma(350.0F);
        Vec3 sideways = new Vec3(
            Math.cos(Math.toRadians((double)this.getYRot())) * (double)side, 0.0, Math.sin(Math.toRadians((double)this.getYRot())) * (double)side
        );
        Gore.gib(level, this, side < 0 ? 6 : 9, sideways.scale(-0.35).add(0.0, 0.3, 0.0));
        Vec3 p = this.skeletonPoint(side < 0 ? 6 : 9, TitanSkeleton.FOREARM_CENTER);
        level.sendParticles(ModParticles.BLOOD.get(), p.x, p.y, p.z, 80, 0.6, 0.6, 0.6, 0.3);
        level.sendParticles(ModParticles.BIG_STEAM.get(), p.x, p.y, p.z, 10, 0.5, 0.5, 0.5, 0.05);
        this.voice(ModSounds.TITAN_HURT.get(), 3.0F, this.voicePitch(), 45, 2);
        level.playSound(null, p.x, p.y, p.z, ModSounds.TITAN_HIT_FLESH.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
        if (this.held != null) {
            TitanAction a = this.getAction();
            if ((a.isHold() || a.isBite()) && a.side() == (float)side) {
                this.release(true);
            }
        }

        if (this.getAction().side() == (float)side) {
            this.setAction(TitanAction.NONE, null);
        }
    }

    public void slammed(ServerLevel level, float strength) {
        if (strength > 0.45F && !this.isDowned() && this.getTitanHeight() < 20.0F) {
            this.knockDown(level, 60 + (int)(strength * 80.0F));
        } else if (this.getAction() == TitanAction.NONE) {
            this.setAction(TitanAction.FLINCH, null);
        }
    }

    protected void knockDown(ServerLevel level, int ticks) {
        this.setFlag(4, true);
        this.downedTicks = ticks;
        if (this.held != null) {
            this.release(true);
        }

        if (this.getAction() != TitanAction.NONE) {
            this.setAction(TitanAction.NONE, null);
        }

        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_FALL.get(), SoundSource.HOSTILE, 3.0F, 0.8F);
        this.voice(ModSounds.TITAN_HURT.get(), 3.0F, this.voicePitch(), 45, 2);
        shakeNearby(level, this.position(), 30.0, 0.6F, 14);
    }

    protected void killBy(DamageSource source, @Nullable Player player, int damage) {
        ServerLevel level = (ServerLevel)this.level();
        float napeMax = this.maxNapeHealth();
        boolean big = this instanceof ColossalTitan;
        boolean burst = !big && ((float)damage > napeMax * 3.0F || source.is(DamageTypeTags.IS_EXPLOSION) && (float)damage > napeMax * 0.8F);
        boolean behead = !big && !burst && ((float)damage > napeMax * 1.2F || this.random.nextFloat() < 0.5F);
        if (behead) {
            this.setFlag(32, true);
            this.spurt[4] = 320;
            Vec3 back = this.getLookAngle().multiply(1.0, 0.0, 1.0).scale(-0.5).add(0.0, 0.9, 0.0);
            Gore.gib(level, this, 3, back);
        }

        if (this.held != null) {
            this.release(true);
        }

        this.setAction(TitanAction.NONE, null);
        if (player != null) {
            this.setLastHurtByPlayer(player);
        }

        if (player instanceof ServerPlayer storyPlayer && !this.storyCounted) {
            this.storyCounted = true;
            Story.onTitanKill(storyPlayer, this);
        }

        Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
        if (!behead && !burst) {
            // the cut nape keeps fountaining while the body goes down
            this.spurt[5] = 220;
        }

        level.sendParticles(ModParticles.BLOOD.get(), nape.x, nape.y, nape.z, 120, 0.6, 0.6, 0.6, 0.35);
        Vec3 up = this.skeletonPoint(3, TitanSkeleton.NAPE)
            .subtract(this.skeletonPoint(3, new Vector3f(0.0F, 0.0F, 0.0F)))
            .normalize()
            .add(0.0, 1.2, 0.0)
            .normalize();

        for (int k = 0; k < 90; k++) {
            double s = 0.5 + this.random.nextDouble() * 0.9;
            level.sendParticles(
                ModParticles.BLOOD.get(),
                nape.x,
                nape.y,
                nape.z,
                0,
                up.x * s + (this.random.nextDouble() - 0.5) * 0.35,
                up.y * s,
                up.z * s + (this.random.nextDouble() - 0.5) * 0.35,
                1.0
            );
        }

        float var10003;
        float var10004;
        label74: {
            level.sendParticles(ModParticles.BIG_STEAM.get(), nape.x, nape.y, nape.z, 40, 0.9, 0.9, 0.9, 0.09);
            Gore.chunks(level, nape, 70, 1.1);
            Gore.spurt(level, nape, up, 40, 1.3);
            Gore.screen(level, nape, 1.2F);
            level.sendParticles(ParticleTypes.FLASH, nape.x, nape.y, nape.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.playSound(null, nape.x, nape.y, nape.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 2.0F, 0.6F);
            level.playSound(null, nape.x, nape.y, nape.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 2.0F, 0.5F);
            var10003 = this.getTitanHeight();
            if (player instanceof ServerPlayer spr && OdmServer.isRed(spr, OdmServer.data(spr))) {
                var10004 = 1.0F;
                break label74;
            }

            var10004 = 0.0F;
        }

        Fx.send(level, 14, nape, var10003, var10004, 160.0);
        if (player instanceof ServerPlayer sp0 && source.getDirectEntity() == player) {
            KillCam.trigger(sp0);
            PacketDistributor.sendToPlayer(sp0, FeedbackPayload.of(10, 100, 170, 0));
        }

        this.voice(ModSounds.TITAN_DEATH.get(), 3.0F + this.getTitanScale() * 0.5F, this.voicePitch(), 100, 3);
        level.playSound(null, nape.x, nape.y, nape.z, ModSounds.NAPE_THUNDER.get(), SoundSource.PLAYERS, 6.0F, 0.9F + this.random.nextFloat() * 0.2F);
        if (player instanceof ServerPlayer spk) {
            OdmServer.onNapeKill(spk);
            if (Skills.has(spk, Skill.ACKERMAN_AWAKENING) && OdmServer.data(spk).overdrive()) {
                Boom.Spec wave = new Boom.Spec().radius(11.0).damage(18.0F, 30.0F).fx(13, (float)spk.getId(), 0.0F).owner(spk, spk);
                wave.glass = true;
                wave.knock = 2.0;
                wave.volume = 3.0F;
                Boom.explode(level, nape, wave);
            }

            TownLife.titanDown(level, this.position(), spk);
        }

        if (player instanceof ServerPlayer sp) {
            OdmPlayerData data = OdmServer.data(sp);
            data.titanKills++;
            data.bestNapeDamage = Math.max(data.bestNapeDamage, damage);
            PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(1, damage, this.titanKind(), data.titanKills));
        }

        this.setHealth(0.0F);
        this.die(source);
        if (burst) {
            Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : this.position();
            Gore.burst(level, this, from);
            this.discard();
        }
    }

    public void onThunderSpear(Vec3 pos, @Nullable Entity owner) {
        if (this.level() instanceof ServerLevel level && !this.isDeadOrDying()) {
            DamageSource src = this.damageSources().explosion(null, owner);
            Player player = owner instanceof Player p ? p : null;
            double radius = 1.6 + (double)this.getTitanScale() * 0.25;
            Vec3 nape = this.skeletonPoint(3, TitanSkeleton.NAPE);
            if (nape.distanceTo(pos) < radius) {
                this.napeHealth -= 90.0F;
                if (this.napeHealth <= 0.0F) {
                    this.killBy(src, player, 90);
                    return;
                }
            }

            for (int side = -1; side <= 1; side += 2) {
                if (!this.isArmCut(side) && this.skeletonPoint(side < 0 ? 6 : 9, TitanSkeleton.FOREARM_CENTER).distanceTo(pos) < radius * 1.4) {
                    this.severArm(side, level);
                }

                if (!this.isDowned() && this.skeletonPoint(side < 0 ? 12 : 14, TitanSkeleton.SHIN_CENTER).distanceTo(pos) < radius * 1.4) {
                    this.knockDown(level, 160);
                }
            }

            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(0, 90, 0, Math.round(this.getTitanHeight() * 10.0F)));
            }

            return;
        }
    }

    public void vaporise(DamageSource source) {
        if (this.level() instanceof ServerLevel level && !this.isDeadOrDying()) {
            if (this.held != null) {
                this.release(true);
            }

            this.setAction(TitanAction.NONE, null);
            Fx.send(level, 12, this.position(), this.getTitanHeight(), 0.0F, 320.0);
            this.setHealth(0.0F);
            this.die(source);
            this.dyingTicks = Math.max(this.dyingTicks, this.evaporateTicks() - 30);
            return;
        }
    }

    public void blastKnockDown(int ticks) {
        if (this.level() instanceof ServerLevel level && !this.isDowned() && !this.isDeadOrDying()) {
            this.knockDown(level, ticks);
        }
    }

    @Override
    protected void tickDeath() {
        this.dyingTicks++;
        if (this.dyingTicks == 36 && this.level() instanceof ServerLevel sl) {
            this.landed(sl, 1.0F);
        }

        if (this.dyingTicks == 50 && !this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STEAM.get(), SoundSource.HOSTILE, 2.5F, 0.7F);
        }

        if (this.dyingTicks >= this.evaporateTicks() && !this.level().isClientSide && !this.isRemoved()) {
            this.remove(RemovalReason.KILLED);
        }
    }

    public int evaporateTicks() {
        return 260;
    }

    protected void landed(ServerLevel level, float strength) {
        Vec3 fwdDir = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 fwd = fwdDir.scale(this.dyingTicks > 0 ? (double)this.getTitanHeight() * 0.5 : 0.0);
        Vec3 p = this.position().add(fwd);
        level.playSound(null, p.x, p.y, p.z, ModSounds.TITAN_FALL.get(), SoundSource.HOSTILE, 2.0F + this.getTitanScale() * strength, 0.8F);
        BlockState ground = level.getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
        if (!ground.isAir()) {
            level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, ground),
                p.x,
                p.y + 0.3,
                p.z,
                (int)(60.0F * this.getTitanScale() * strength),
                (double)this.getTitanScale() * 0.8,
                0.3,
                (double)this.getTitanScale() * 0.8,
                0.2
            );
            Destruction.far(
                level,
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                p.x,
                p.y + 0.5,
                p.z,
                (int)(6.0F * strength * this.getTitanScale()),
                (double)this.getTitanScale() * 1.2,
                0.3,
                (double)this.getTitanScale() * 1.2,
                0.015,
                128.0
            );
        }

        shakeNearby(level, p, (double)(25.0F + this.getTitanScale() * 6.0F), 0.7F * strength, 16);
        if (this.dyingTicks > 0) {
            Destruction.bodySlam(
                level,
                this.position(),
                fwdDir,
                (double)this.getTitanHeight() * (this.isCrawler() ? 0.6 : 1.0),
                Math.max(1.5, (double)this.getTitanHeight() * 0.17),
                !this.isCrawler()
            );
        } else if (strength >= 0.5F && TakemConfig.TITAN_GRIEFING.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            double r = (double)this.getBbWidth() * 0.5 + (double)strength;
            Destruction.bodySlam(level, this.position().add(fwdDir.scale(-0.5)).add(0.0, -r * 0.8, 0.0), fwdDir, 1.0, r, false);
        }
    }

    public static void shakeNearby(ServerLevel level, Vec3 at, double radius, float strength, int ticks) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(at);
            if (d < radius) {
                float s = strength * (float)(1.0 - d / radius);
                PacketDistributor.sendToPlayer(p, FeedbackPayload.of(2, Math.round(s * 100.0F), ticks, 0));
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.held != null && !this.level().isClientSide) {
            this.release(false);
        }

        super.remove(reason);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (!this.level().isClientSide) {
            this.voice(ModSounds.TITAN_HURT.get(), this.getSoundVolume(), this.voicePitch(), 40, 1);
        }

        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 1.5F + this.getTitanScale() * 0.4F;
    }

    @Override
    public float getVoicePitch() {
        return this.voicePitch();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public boolean isSensitiveToWater() {
        return false;
    }

    @Override
    protected boolean isAffectedByFluids() {
        return !this.isCrawler() && this.getTitanHeight() < 8.0F;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }
}
