package com.takem.titans.entity.npc;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.ThunderSpearEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.Gore;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import com.takem.titans.story.Squad;
import com.takem.titans.story.Story;
import com.takem.titans.world.TownLife;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Vector3f;

public class JaegerEntity extends PathfinderMob {
    public static final String[] NAMES = new String[]{
        "Kael Brenner",
        "Ysolde Marr",
        "Tobin Ashgrove",
        "Lienne Dace",
        "Corwin Vale",
        "Sera Lindqvist",
        "Emil Hartung",
        "Nadja Roth",
        "Fenn Calder",
        "Ottilie Brandt",
        "Jorin Wex",
        "Maren Stoll",
        "Aldo Faust",
        "Hilde Varga",
        "Pell Orrin",
        "Greta Lunn"
    };
    public static final int VARIANTS = 4;
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(JaegerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HOOKED = SynchedEntityData.defineId(JaegerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> DATA_ANCHOR = SynchedEntityData.defineId(JaegerEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(JaegerEntity.class, EntityDataSerializers.BOOLEAN);
    @Nullable
    private UUID owner;
    private String jaegerName = "";
    private boolean temporary;
    private int targetId = -1;
    private Vec3 anchor = Vec3.ZERO;
    private boolean hooked;
    private double rope;
    private boolean flying;
    private int attackCooldown;
    private int retarget;
    private int rehook;
    private int side = 1;
    private int heldTicks;
    private int boostSound;
    private int kills;
    private int squadSlot;
    private int jaegerLevel = 1;
    private int spearCooldown = 200;
    private int dodgeCooldown;

    public JaegerEntity(EntityType<? extends JaegerEntity> type, Level level) {
        super(type, level);
        this.side = this.random.nextBoolean() ? 1 : -1;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
        if (this.jaegerName.isEmpty()) {
            this.setup(NAMES[this.random.nextInt(NAMES.length)], this.random.nextInt(4), null, false, 0);
        }

        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    public static Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.34)
            .add(Attributes.FOLLOW_RANGE, 48.0)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_HOOKED, false);
        builder.define(DATA_ANCHOR, new Vector3f());
        builder.define(DATA_FLYING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return JaegerEntity.this.owner == null && super.canUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public void setup(String name, int variant, @Nullable UUID owner, boolean temporary, int slot) {
        this.jaegerName = name;
        this.entityData.set(DATA_VARIANT, Math.floorMod(variant, 4));
        this.owner = owner;
        this.temporary = temporary;
        this.squadSlot = slot;
        this.setCustomName(Component.literal(name).withStyle(owner != null ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        this.setCustomNameVisible(false);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(variant % 3 == 2 ? ModItems.VETERAN_BLADES.get() : ModItems.ODM_BLADES.get()));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.ODM_BLADES.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public boolean isHooked() {
        return this.entityData.get(DATA_HOOKED);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public Vec3 anchorPoint() {
        Vector3f a = this.entityData.get(DATA_ANCHOR);
        return new Vec3((double)a.x, (double)a.y, (double)a.z);
    }

    public String jaegerName() {
        return this.jaegerName;
    }

    @Nullable
    public UUID owner() {
        return this.owner;
    }

    public boolean isTemporary() {
        return this.temporary;
    }

    public int jaegerLevel() {
        return this.jaegerLevel;
    }

    public void setJaegerLevel(int lvl) {
        this.jaegerLevel = Math.max(1, Math.min(10, lvl));
        AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(30.0 + 7.0 * (double)(this.jaegerLevel - 1));
        }

        this.setItemSlot(
            EquipmentSlot.MAINHAND,
            new ItemStack(
                this.jaegerLevel >= 8
                    ? ModItems.TITANBREAKER_BLADES.get()
                    : (
                        this.jaegerLevel >= 6
                            ? ModItems.ACKERMAN_BLADES.get()
                            : (this.jaegerLevel >= 3 ? ModItems.VETERAN_BLADES.get() : ModItems.ODM_BLADES.get())
                    )
            )
        );
        this.setCustomName(
            Component.literal(this.jaegerName + " [" + this.jaegerLevel + "]").withStyle(this.owner != null ? ChatFormatting.GREEN : ChatFormatting.WHITE)
        );
    }

    public int squadSlot() {
        return this.squadSlot;
    }

    public void setOwner(@Nullable UUID owner, int slot) {
        this.owner = owner;
        this.squadSlot = slot;
        this.temporary = false;
        this.setCustomName(Component.literal(this.jaegerName).withStyle(owner != null ? ChatFormatting.GREEN : ChatFormatting.WHITE));
    }

    @Nullable
    public ServerPlayer leader() {
        return this.owner != null && this.level() instanceof ServerLevel sl && sl.getPlayerByUUID(this.owner) instanceof ServerPlayer sp && sp.isAlive()
            ? sp
            : null;
    }

    @Override
    public void travel(Vec3 input) {
        if (this.flying && !this.level().isClientSide) {
            Vec3 v = this.getDeltaMovement();
            this.move(MoverType.SELF, v);
            if (this.horizontalCollision) {
                this.setDeltaMovement(v.multiply(0.3, 1.0, 0.3).add(0.0, 0.15, 0.0));
            }

            this.calculateEntityAnimation(false);
        } else {
            super.travel(input);
        }
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            this.serverFlight((ServerLevel)this.level());
        }

        super.aiStep();
        if (this.level().isClientSide && this.isFlying() && this.tickCount % 2 == 0) {
            Vec3 v = this.getDeltaMovement();
            this.level().addParticle(ModParticles.GAS.get(), this.getX(), this.getY() + 0.9, this.getZ(), -v.x * 0.2, -v.y * 0.2, -v.z * 0.2);
        }
    }

    private void serverFlight(ServerLevel level) {
        this.fallDistance = 0.0F;
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        if (this.rehook > 0) {
            this.rehook--;
        }

        if (this.temporary && this.owner != null && this.leader() == null && this.tickCount > 100) {
            this.discard();
        } else {
            AbstractTitan holder = this.heldBy(level);
            if (holder != null) {
                this.setFlying(false);
                this.unhook();
                if (++this.heldTicks > 20 && this.random.nextInt(40) == 0) {
                    holder.release(true);
                    holder.traumaHit(8.0F, null, level.damageSources().mobAttack(this), false);
                    this.say("escape");
                }
            } else {
                this.heldTicks = 0;
                if (--this.retarget <= 0) {
                    this.retarget = 10;
                    this.targetId = this.findTitan(level);
                }

                ServerPlayer leader = this.leader();
                if (leader != null && this.distanceToSqr(leader) > 9216.0) {
                    Squad.arrive(level, this, leader);
                } else {
                    AbstractTitan titan = this.targetId >= 0 && level.getEntity(this.targetId) instanceof AbstractTitan t && t.isAlive() && !t.isDeadOrDying()
                        ? t
                        : null;
                    if (titan != null) {
                        this.fight(level, titan, leader);
                    } else if (leader != null && !leader.onGround() && this.distanceToSqr(leader) > 64.0) {
                        this.follow(level, leader);
                    } else {
                        if (leader != null && this.distanceToSqr(leader) > 36.0 && !JaegerEntity.OdmHold.holding(leader)) {
                            this.getNavigation().moveTo(leader, 1.1);
                        }

                        this.unhook();
                        if (this.flying && (this.onGround() || this.isInWater())) {
                            this.setFlying(false);
                        }

                        if (this.flying) {
                            Vec3 v = this.getDeltaMovement().add(0.0, -0.06, 0.0).scale(0.98);
                            this.setDeltaMovement(v);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private AbstractTitan heldBy(ServerLevel level) {
        for (AbstractTitan t : level.getEntitiesOfClass(AbstractTitan.class, this.getBoundingBox().inflate(24.0))) {
            if (t.getHeld() == this) {
                return t;
            }
        }

        return null;
    }

    private int findTitan(ServerLevel level) {
        ServerPlayer leader = this.leader();
        double range = 64.0;
        AbstractTitan best = null;
        double bestD = range * range;

        for (AbstractTitan t : level.getEntitiesOfClass(AbstractTitan.class, this.getBoundingBox().inflate(range))) {
            if (t.isAlive()
                && !t.isDeadOrDying()
                && !t.isDummy()
                && !(t instanceof RumblingTitan)
                && t.getTitanHeight() < 30.0F
                && (leader == null || !JaegerEntity.OdmHold.holding(leader) || !(t.distanceToSqr(this) > 1600.0))) {
                double d = this.distanceToSqr(t);
                if (leader != null) {
                    d = Math.min(d, t.distanceToSqr(leader) * 0.8);
                    if (t.getTarget() == leader || t.getHeld() == leader) {
                        d *= 0.25;
                    }
                }

                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
        }

        return best == null ? -1 : best.getId();
    }

    @Nullable
    private static TitanPart nape(AbstractTitan t) {
        for (TitanPart p : t.getTitanParts()) {
            if (p.kind == 1) {
                return p;
            }
        }

        return null;
    }

    private void fight(ServerLevel level, AbstractTitan titan, @Nullable ServerPlayer leader) {
        TitanPart napePart = nape(titan);
        if (napePart != null) {
            Vec3 nape = napePart.position().add(0.0, (double)napePart.getBbHeight() * 0.5, 0.0);
            Vec3 me = this.position().add(0.0, 1.0, 0.0);
            double dist = me.distanceTo(nape);
            if (!this.flying) {
                if (dist > 70.0) {
                    this.getNavigation().moveTo(titan, 1.2);
                    return;
                }

                this.setFlying(true);
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.7, 0.0));
                this.puff(level, 1.0F);
            }

            this.getNavigation().stop();
            if (this.dodgeCooldown > 0) {
                this.dodgeCooldown--;
            }

            if (this.spearCooldown > 0) {
                this.spearCooldown--;
            }

            if (this.dodgeCooldown <= 0 && titan.getTarget() == this && (titan.getAction().isGrab() || titan.getAction().isSwat()) && dist < 14.0) {
                Vec3 away = me.subtract(titan.position()).multiply(1.0, 0.0, 1.0);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3((double)this.side, 0.0, 0.0) : away.normalize();
                this.unhook();
                this.setDeltaMovement(away.scale(1.3).add(0.0, 1.0, 0.0));
                this.dodgeCooldown = 50;
                this.rehook = 10;
                this.puff(level, 1.2F);
                return;
            }

            if (this.jaegerLevel >= 4 && this.spearCooldown <= 0 && dist > 10.0 && dist < 45.0 && this.hasLineOfSight(titan)) {
                ThunderSpearEntity spear = new ThunderSpearEntity(level, this);
                Vec3 dir = nape.subtract(me).normalize();
                spear.setPos(me.x + dir.x, me.y + dir.y, me.z + dir.z);
                spear.shoot(dir.x, dir.y + 0.04, dir.z, 3.2F, 1.2F);
                level.addFreshEntity(spear);
                level.playSound(null, me.x, me.y, me.z, ModSounds.THUNDER_SPEAR_LAUNCH.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
                this.spearCooldown = 420 - this.jaegerLevel * 25;
                this.swing(InteractionHand.MAIN_HAND);
            }

            if ((!this.hooked || this.anchor.distanceTo(nape) > 14.0) && this.rehook <= 0) {
                float yaw = titan.yBodyRot * 0.017453292F;
                Vec3 across = new Vec3((double)Mth.cos(yaw), 0.0, (double)Mth.sin(yaw)).scale((double)this.side);
                Vec3 want = nape.add(0.0, 2.0 + (double)titan.getTitanScale() * 1.5, 0.0).add(across.scale(3.0 + (double)titan.getTitanScale()));
                this.hookTo(level, want);
            }

            Vec3 v = this.getDeltaMovement().add(0.0, -0.06, 0.0);
            if (this.hooked) {
                v = this.rope(v, dist > 5.0);
            }

            if (dist < 18.0 && this.attackCooldown <= 0) {
                Vec3 toward = nape.subtract(me).normalize();
                v = v.add(toward.scale(0.16));
                if (++this.boostSound % 8 == 0) {
                    this.puff(level, 0.6F);
                }
            }

            v = v.scale(0.985);
            double sp = v.length();
            if (sp > 2.3) {
                v = v.scale(2.3 / sp);
            }

            this.setDeltaMovement(v);
            this.lookAt(titan, 30.0F, 30.0F);
            if (dist < 3.2 && this.attackCooldown <= 0) {
                this.cut(level, titan, napePart, nape, leader);
            }
        }
    }

    private void cut(ServerLevel level, AbstractTitan titan, TitanPart napePart, Vec3 nape, @Nullable ServerPlayer leader) {
        float speed = (float)this.getDeltaMovement().length();
        float dmg = (leader != null ? 16.0F : 12.0F) * (1.0F + speed * 0.6F) * (1.0F + 0.15F * (float)(this.jaegerLevel - 1));
        if (leader != null && Skills.has(leader, Skill.LEGION)) {
            dmg *= 1.3F;
        }

        this.swing(InteractionHand.MAIN_HAND);
        this.attackCooldown = 22 + this.random.nextInt(12);
        level.playSound(null, nape.x, nape.y, nape.z, ModSounds.BLADE_NAPE.get(), SoundSource.NEUTRAL, 1.4F, 0.9F + this.random.nextFloat() * 0.2F);
        level.sendParticles(ModParticles.BLOOD.get(), nape.x, nape.y, nape.z, 24, 0.4, 0.4, 0.4, 0.25);
        Fx.send(level, 3, nape, this.getYRot(), this.getXRot(), (this.random.nextFloat() - 0.5F) * 120.0F, 1.6F, 96.0);
        boolean killed = titan.traumaHit(dmg, null, level.damageSources().mobAttack(this), false);
        if (killed || titan.isDeadOrDying()) {
            this.kills++;
            this.say("kill");
            Gore.chunks(level, nape, 20, 0.6);
            if (leader != null) {
                Squad.onKill(leader, this, titan);
            }

            if (leader != null) {
                Story.onTitanKill(leader, titan);
                leader.displayClientMessage(Component.translatable("message.takemtitans.jaeger_kill", this.jaegerName), true);
            }

            TownLife.alarm(level, titan.position(), 1.0, 2);
            this.targetId = -1;
            this.retarget = 0;
        } else if (this.random.nextInt(5) == 0) {
            this.say("fight");
        }

        Vec3 away = this.position().subtract(titan.position()).multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        this.setDeltaMovement(away.scale(0.9).add(0.0, 0.9, 0.0));
        this.side = -this.side;
        this.unhook();
        this.rehook = 8;
    }

    private void follow(ServerLevel level, ServerPlayer leader) {
        if (!this.flying) {
            this.setFlying(true);
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.8, 0.0));
            this.puff(level, 1.0F);
        }

        this.getNavigation().stop();
        Vec3 lv = leader.getDeltaMovement();
        float yaw = leader.getYRot() * 0.017453292F;
        Vec3 right = new Vec3((double)(-Mth.cos(yaw)), 0.0, (double)(-Mth.sin(yaw)));
        double off = (double)((this.squadSlot % 2 == 0 ? 1 : -1) * (3 + this.squadSlot * 2));
        Vec3 slot = leader.position().add(right.scale(off)).add(0.0, 1.0, 0.0).subtract(leader.getLookAngle().multiply(1.0, 0.0, 1.0).scale(4.0));
        if ((!this.hooked || this.anchor.distanceTo(slot) > 26.0) && this.rehook <= 0) {
            this.hookTo(level, slot.add(lv.scale(12.0)).add(0.0, 10.0, 0.0));
        }

        Vec3 v = this.getDeltaMovement().add(0.0, -0.06, 0.0);
        if (this.hooked) {
            v = this.rope(v, this.position().distanceTo(slot) > 6.0);
        }

        Vec3 to = slot.subtract(this.position());
        if (to.length() > 5.0) {
            v = v.add(to.normalize().scale(0.1));
        }

        v = v.scale(0.98);
        double sp = v.length();
        double cap = Math.max(2.4, leader.getDeltaMovement().length() * 1.3);
        if (sp > cap) {
            v = v.scale(cap / sp);
        }

        this.setDeltaMovement(v);
    }

    private Vec3 rope(Vec3 v, boolean reel) {
        Vec3 origin = this.position().add(0.0, 1.0, 0.0);
        Vec3 d = this.anchor.subtract(origin);
        double dist = d.length();
        if (dist < 0.01) {
            return v;
        } else {
            Vec3 dir = d.scale(1.0 / dist);
            if (reel) {
                this.rope = Math.max(2.0, Math.min(this.rope, dist) - 0.9);
                v = v.add(dir.scale(0.13));
            }

            Vec3 next = origin.add(v);
            Vec3 toA = this.anchor.subtract(next);
            double nd = toA.length();
            if (nd > this.rope && nd > 0.01) {
                Vec3 n = toA.scale(1.0 / nd);
                double radial = v.dot(n);
                if (radial < 0.0) {
                    v = v.subtract(n.scale(radial));
                }

                v = v.add(n.scale(Math.min(nd - this.rope, 0.6)));
            }

            if (dist > 80.0) {
                this.unhook();
            }

            return v;
        }
    }

    private void hookTo(ServerLevel level, Vec3 want) {
        Vec3 from = this.position().add(0.0, 1.0, 0.0);
        Vec3 dir = want.subtract(from);
        double len = dir.length();
        if (!(len < 1.0)) {
            Vec3 end = from.add(dir.scale(Math.min(70.0, len + 6.0) / len));
            BlockHitResult hit = level.clip(new ClipContext(from, end, Block.COLLIDER, Fluid.NONE, this));
            Vec3 at = hit.getType() == Type.MISS ? want : hit.getLocation();
            this.anchor = at;
            this.hooked = true;
            this.rope = Math.max(2.0, at.distanceTo(from));
            this.rehook = 10;
            this.entityData.set(DATA_HOOKED, true);
            this.entityData.set(DATA_ANCHOR, new Vector3f((float)at.x, (float)at.y, (float)at.z));
            level.playSound(
                null, this.getX(), this.getY() + 1.0, this.getZ(), ModSounds.HOOK_FIRE.get(), SoundSource.NEUTRAL, 0.7F, 1.0F + this.random.nextFloat() * 0.2F
            );
        }
    }

    private void unhook() {
        if (this.hooked) {
            this.hooked = false;
            this.entityData.set(DATA_HOOKED, false);
        }
    }

    private void setFlying(boolean f) {
        if (this.flying != f) {
            this.flying = f;
            this.entityData.set(DATA_FLYING, f);
            this.setNoGravity(f);
        }
    }

    private void puff(ServerLevel level, float vol) {
        level.sendParticles(ModParticles.GAS.get(), this.getX(), this.getY() + 1.0, this.getZ(), 14, 0.3, 0.3, 0.3, 0.06);
        level.playSound(
            null, this.getX(), this.getY() + 1.0, this.getZ(), ModSounds.GAS_BURST.get(), SoundSource.NEUTRAL, vol, 1.0F + this.random.nextFloat() * 0.2F
        );
    }

    public void say(String group) {
        ServerPlayer leader = this.leader();
        if (leader != null && this.random.nextInt(2) == 0) {
            int n = switch (group) {
                case "kill" -> 6;
                case "fight" -> 6;
                case "escape" -> 3;
                default -> 4;
            };
            leader.sendSystemMessage(
                Component.literal("[" + this.jaegerName + "] ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.translatable("jaeger.takemtitans." + group + "." + this.random.nextInt(n)).withStyle(ChatFormatting.WHITE))
            );
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player p && this.owner != null && p.getUUID().equals(this.owner)) {
            return false;
        }

        return source.getEntity() instanceof JaegerEntity ? false : super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel sl) {
            TownLife.alarm(sl, this.position(), 30.0, 3);
            Squad.onDeath(sl, this);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        } else {
            if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
                this.getLookControl().setLookAt(player);
                Squad.talk(sp, this);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return this.owner == null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("JaegerName", this.jaegerName);
        tag.putInt("Variant", this.getVariant());
        tag.putInt("JaegerLevel", this.jaegerLevel);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setup(tag.getString("JaegerName"), tag.getInt("Variant"), null, false, 0);
        if (tag.contains("JaegerLevel")) {
            this.setJaegerLevel(tag.getInt("JaegerLevel"));
        }
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    static boolean jaegerPatrols() {
        return TakemConfig.JAEGER_PATROLS.get();
    }

    public static final class OdmHold {
        private OdmHold() {
        }

        public static boolean holding(ServerPlayer leader) {
            return OdmServer.data(leader).squadHold;
        }
    }
}
