package com.takem.titans.entity.npc;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.story.Story;
import com.takem.titans.story.StoryWorld;
import com.takem.titans.world.TownLife;
import com.takem.titans.world.TownPlan;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TownsfolkEntity extends PathfinderMob implements Merchant {
    public static final int CIVILIAN = 0;
    public static final int SOLDIER = 1;
    public static final int COMMANDER = 2;
    public static final int QUARTERMASTER = 3;
    public static final int MIRA = 4;
    public static final int HALE = 5;
    public static final String[] ROLE_NAMES = new String[]{"civilian", "soldier", "commander", "quartermaster", "mira", "hale", "builder", "instructor"};
    public static final int BUILDER = 6;
    @Nullable
    private BlockPos workTarget;
    private int workTicks;
    private int greetCooldown = 100;
    public static final int CIVILIAN_VARIANTS = 6;
    public static final int SOLDIER_VARIANTS = 3;
    private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(TownsfolkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(TownsfolkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_ACTIVITY = SynchedEntityData.defineId(TownsfolkEntity.class, EntityDataSerializers.BYTE);
    public static final int ACT_NONE = 0;
    public static final int ACT_WORK = 1;
    public static final int ACT_TALK = 2;
    public static final int ACT_CARRY = 3;
    public static final int ACT_SWEEP = 4;
    public static final int ACT_CHEER = 5;
    public static final int ACT_COWER = 6;
    public static final int ACT_SALUTE = 7;
    public static final int ACT_BUILD = 8;
    private int plan = 0;
    private int planTicks = 40;
    private int partner = -1;
    private int saluteTicks;
    @Nullable
    private BlockPos home;
    private boolean ambient;
    private int leapCooldown = 40;
    private int screamCooldown;
    private int leaveIn = -1;
    @Nullable
    private Player tradingPlayer;
    @Nullable
    private MerchantOffers offers;
    private int panicTicks;
    @Nullable
    private Vec3 panicFrom;
    private int cheerTicks;
    private int cheerHero = -1;
    private int shoutTicks;
    private int cowerTicks;
    private int chatter = 200;
    private int reactCooldown;

    public TownsfolkEntity(EntityType<? extends TownsfolkEntity> type, Level level) {
        super(type, level);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
    }

    public static Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, 0);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_ACTIVITY, (byte)0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TownsfolkEntity.PanicGoal());
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, AbstractTitan.class, 28.0F, 0.6, 0.85, e -> !(e instanceof AbstractTitan t && t.isDummy()) && !this.isStoryCharacter() && this.getRole() != 1));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.5));
        this.goalSelector.addGoal(4, new TownsfolkEntity.BusyGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.35));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public void setup(int role, int variant, @Nullable BlockPos home, boolean ambient) {
        this.entityData.set(DATA_ROLE, role);
        this.entityData.set(DATA_VARIANT, variant);
        this.home = home;
        this.ambient = ambient;
        if (home != null) {
            this.restrictTo(home, this.isStoryCharacter() ? 4 : 24);
        }

        if (this.isStoryCharacter()) {
            this.setCustomName(Component.translatable("npc.takemtitans." + ROLE_NAMES[role]));
            this.setCustomNameVisible(true);
            this.setPersistenceRequired();
        }

        if (role == 1 || role == 4 || role == 5 || role == 2 || role == 7) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(role == 2 ? ModItems.ACKERMAN_BLADES.get() : ModItems.ODM_BLADES.get()));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        } else if (role == 6) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(variant % 2 == 0 ? Items.MACE : Items.IRON_AXE));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(variant % 3 == 0 ? Items.OAK_PLANKS : Items.STONE_BRICKS));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        } else if (role == 0 && this.random.nextInt(3) == 0) {
            Item[] errands = new Item[]{Items.BREAD, Items.POPPY, Items.BOOK, Items.BUCKET, Items.WHEAT, Items.APPLE, Items.LANTERN, Items.MILK_BUCKET};
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(errands[this.random.nextInt(errands.length)]));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return this.isStoryCharacter() ? distSq < 25600.0 : super.shouldRenderAtSqrDistance(distSq);
    }

    public int activity() {
        return this.entityData.get(DATA_ACTIVITY);
    }

    private boolean busyInPlace() {
        int a = this.plan;
        if (a == 1 || a == 4) {
            return true;
        } else if (a != 2) {
            return false;
        } else {
            if (this.level().getEntity(this.partner) instanceof TownsfolkEntity t && t.distanceToSqr(this) < 7.0) {
                return true;
            }

            return false;
        }
    }

    private void startPlan(ServerLevel level, int act, int ticks) {
        this.plan = act;
        this.planTicks = ticks;

        Item tool = switch (act) {
            case 1 -> new Item[]{Items.IRON_AXE, Items.IRON_HOE, Items.MACE, Items.IRON_SHOVEL}[this.random.nextInt(4)];
            default -> null;
            case 3 -> new Item[]{Items.BARREL, Items.CHEST, Items.HAY_BLOCK, Items.OAK_PLANKS}[this.random.nextInt(4)];
            case 4 -> Items.BRUSH;
        };
        if (tool != null) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
        }

        if (act == 3 || act == 0) {
            for (int i = 0; i < 6; i++) {
                double a = this.random.nextDouble() * 3.141592653589793 * 2.0;
                double d = 10.0 + this.random.nextDouble() * 16.0;
                double x = this.getX() + Math.cos(a) * d;
                double z = this.getZ() + Math.sin(a) * d;
                if (StoryWorld.isTown(x, z) && !TownPlan.isBuilding(Mth.floor(x), Mth.floor(z))) {
                    this.getNavigation().moveTo(x, this.getY(), z, act == 3 ? 0.45 : 0.4);
                    break;
                }
            }
        }
    }

    private void tickActivity(ServerLevel level) {
        boolean danger = this.panicTicks > 0 || this.cowerTicks > 0 || this.nearestTitan(level, 32.0) != null;
        int role = this.getRole();
        if (danger && this.plan != 0) {
            this.plan = 0;
            this.getNavigation().stop();
        }

        if (!danger && (role == 0 || role == 1) && --this.planTicks <= 0) {
            if (role == 1) {
                boolean night = !level.isDay();
                this.setItemSlot(EquipmentSlot.OFFHAND, night ? new ItemStack(Items.LANTERN) : ItemStack.EMPTY);
                this.startPlan(level, 0, 160 + this.random.nextInt(160));
            } else {
                int r = this.random.nextInt(100);
                if (r < 30) {
                    this.startPlan(level, 0, 160 + this.random.nextInt(200));
                } else if (r < 55) {
                    TownsfolkEntity other = null;
                    Iterator ticks = level.getEntitiesOfClass(
                            TownsfolkEntity.class, this.getBoundingBox().inflate(10.0, 3.0, 10.0), tx -> tx != this && tx.getRole() == 0 && tx.plan == 0
                        )
                        .iterator();
                    if (ticks.hasNext()) {
                        TownsfolkEntity t = (TownsfolkEntity)ticks.next();
                        other = t;
                    }

                    if (other != null) {
                        int ticksx = 220 + this.random.nextInt(200);
                        this.partner = other.getId();
                        other.partner = this.getId();
                        this.startPlan(level, 2, ticksx);
                        other.startPlan(level, 2, ticksx);
                    } else {
                        this.startPlan(level, 0, 100);
                    }
                } else if (r < 72) {
                    this.startPlan(level, 1, 200 + this.random.nextInt(200));
                } else if (r < 84) {
                    this.startPlan(level, 4, 160 + this.random.nextInt(160));
                } else {
                    this.startPlan(level, 3, 260);
                }
            }
        }

        switch (this.plan) {
            case 1:
                if (this.tickCount % 14 == 0) {
                    this.swing(InteractionHand.MAIN_HAND);
                    level.playSound(
                        null,
                        this.getX(),
                        this.getY() + 1.0,
                        this.getZ(),
                        SoundEvents.WOOD_HIT,
                        SoundSource.NEUTRAL,
                        0.3F,
                        0.8F + this.random.nextFloat() * 0.4F
                    );
                    Vec3 f = this.getLookAngle().scale(0.8).add(this.position()).add(0.0, 0.6, 0.0);
                    level.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), f.x, f.y, f.z, 3, 0.1, 0.1, 0.1, 0.05
                    );
                }
                break;
            case 2:
                if (level.getEntity(this.partner) instanceof TownsfolkEntity t && t.isAlive()) {
                    if (t.distanceToSqr(this) > 6.0) {
                        if (this.tickCount % 20 == 0) {
                            this.getNavigation().moveTo(t, 0.45);
                        }
                    } else {
                        this.getNavigation().stop();
                        this.getLookControl().setLookAt(t, 30.0F, 30.0F);
                        if (this.random.nextInt(160) == 0) {
                            this.shout("chatter", ChatFormatting.GRAY);
                        }
                    }
                    break;
                }

                this.plan = 0;
                break;
            case 3:
                if (this.getNavigation().isDone() && this.planTicks < 230) {
                    this.plan = 0;
                    this.planTicks = 60;
                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
                break;
            case 4:
                if (this.tickCount % 10 == 0) {
                    level.sendParticles(ParticleTypes.WHITE_ASH, this.getX(), this.getY() + 0.1, this.getZ(), 4, 0.6, 0.05, 0.6, 0.02);
                }
        }

        if (this.planTicks <= 0 && this.plan != 0) {
            this.plan = 0;
        }

        if (this.saluteTicks > 0) {
            this.saluteTicks--;
        }

        int shown = this.cowerTicks > 0
            ? 6
            : (
                this.cheerTicks > 0
                    ? 5
                    : (
                        this.saluteTicks > 0
                            ? 7
                            : (role == 6 ? (this.workTarget != null && this.distanceToSqr(Vec3.atCenterOf(this.workTarget)) < 64.0 ? 8 : 0) : this.plan)
                    )
            );
        if (shown != this.entityData.get(DATA_ACTIVITY)) {
            this.entityData.set(DATA_ACTIVITY, (byte)shown);
        }
    }

    public void celebrate() {
        if (!this.isStoryCharacter()) {
            this.cheerTicks = 120 + this.random.nextInt(60);
            this.shout("cheer", ChatFormatting.GREEN);
        }
    }

    public void setWorkTarget(BlockPos pos) {
        this.workTarget = pos;
    }

    private void tickWork(ServerLevel level) {
        if (this.getRole() == 6 && this.workTarget != null) {
            Vec3 t = Vec3.atCenterOf(this.workTarget);
            double d = this.distanceToSqr(t);
            if (d > 30.0 && this.tickCount % 30 == 0) {
                this.getNavigation().moveTo(t.x, t.y, t.z, 0.6);
            }

            if (d < 64.0) {
                this.getLookControl().setLookAt(t);
                if (++this.workTicks % 11 == 0) {
                    this.swing(InteractionHand.MAIN_HAND);
                    level.playSound(
                        null,
                        this.getX(),
                        this.getY() + 1.0,
                        this.getZ(),
                        this.workTicks % 22 == 0 ? SoundEvents.ANVIL_USE : SoundEvents.WOOD_HIT,
                        SoundSource.NEUTRAL,
                        0.35F,
                        0.9F + this.random.nextFloat() * 0.3F
                    );
                }
            }

            if (this.random.nextInt(260) == 0) {
                this.shout("builder", ChatFormatting.YELLOW);
            }
        }

        if (!this.isStoryCharacter() && --this.greetCooldown <= 0 && TakemConfig.TOWN_REACTIONS.get()) {
            this.greetCooldown = 120;
            Player p = level.getNearestPlayer(this, 4.5);
            if (p != null && !p.isSpectator() && this.nearestTitan(level, 40.0) == null && this.random.nextInt(3) == 0) {
                this.greetCooldown = 900;
                this.getLookControl().setLookAt(p);
                if (this.getRole() == 1) {
                    this.saluteTicks = 30;
                }

                this.shout(this.getRole() == 1 ? "salute" : "greet", ChatFormatting.AQUA);
            }
        }
    }

    public int getRole() {
        return this.entityData.get(DATA_ROLE);
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public boolean isStoryCharacter() {
        int r = this.getRole();
        return r == 2 || r == 3 || r == 4 || r == 5 || r == 7;
    }

    @Nullable
    public BlockPos home() {
        return this.home;
    }

    public void leaveSoon() {
        this.leaveIn = 60;
    }

    private static long lastScream;

    /** One panicked yelp at a time: a crowd fleeing a titan shouldn't become a wall of noise. */
    private static boolean screamSlot(Level level) {
        long now = level.getGameTime();
        if (Math.abs(now - lastScream) < 30) {
            return false;
        }
        lastScream = now;
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide && this.getRole() == 3 && this.tickCount % 6 == 0) {
            this.level()
                .addParticle(
                    ParticleTypes.END_ROD,
                    this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                    this.getY() + (double)this.getBbHeight() + 0.3,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.8,
                    0.0,
                    0.04,
                    0.0
                );
        }

        if (!this.level().isClientSide) {
            ServerLevel level = (ServerLevel)this.level();
            this.fallDistance = this.getRole() != 1 && this.getRole() != 4 ? this.fallDistance : 0.0F;
            if (this.screamCooldown > 0) {
                this.screamCooldown--;
            }

            this.tickLife(level);
            this.tickWork(level);
            this.tickActivity(level);
            if (this.leaveIn > 0 && --this.leaveIn == 0) {
                level.sendParticles(ModParticles.GAS.get(), this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GAS_BURST.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
                this.discard();
            } else {
                if (this.isStoryCharacter() && this.home != null && this.distanceToSqr(Vec3.atBottomCenterOf(this.home)) > 900.0) {
                    this.teleportTo((double)this.home.getX() + 0.5, (double)this.home.getY(), (double)this.home.getZ() + 0.5);
                }

                if (this.tickCount % 10 == 0) {
                    AbstractTitan titan = this.nearestTitan(level, this.getRole() == 1 ? 40.0 : 20.0);
                    if (titan != null) {
                        if (this.getRole() == 0 && TakemConfig.TOWN_REACTIONS.get() && this.panicTicks < 30) {
                            this.react(titan.position(), 2, (double)this.distanceTo(titan));
                        }

                        if (this.getRole() == 0 && this.screamCooldown <= 0 && this.random.nextInt(3) == 0 && screamSlot(level)) {
                            this.screamCooldown = 240 + this.random.nextInt(240);
                            level.playSound(
                                null,
                                this.getX(),
                                this.getEyeY(),
                                this.getZ(),
                                ModSounds.NPC_SCREAM.get(),
                                SoundSource.NEUTRAL,
                                0.8F,
                                0.9F + this.random.nextFloat() * 0.3F
                            );
                        }

                        if (this.getRole() == 1) {
                            this.soldierFight(level, titan);
                        }
                    }
                }
            }
        }
    }

    private void tickLife(ServerLevel level) {
        if (this.reactCooldown > 0) {
            this.reactCooldown--;
        }

        if (this.panicTicks > 0) {
            this.panicTicks--;
        }

        if (this.shoutTicks > 0 && --this.shoutTicks == 0 && !this.isStoryCharacter()) {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
        }

        if (this.cowerTicks > 0) {
            this.cowerTicks--;
            this.getNavigation().stop();
            this.setPose(Pose.CROUCHING);
            if (this.cowerTicks == 0) {
                this.setPose(Pose.STANDING);
            }
        }

        if (this.cheerTicks > 0) {
            this.cheerTicks--;
            if (this.level().getEntity(this.cheerHero) instanceof Player hero) {
                this.getLookControl().setLookAt(hero, 30.0F, 30.0F);
            }

            if (this.onGround() && this.random.nextInt(9) == 0) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.42, 0.0));
                this.hasImpulse = true;
            }

            if (this.tickCount % 6 == 0) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 2.1, this.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
            }
        }

        if (TakemConfig.TOWN_REACTIONS.get() && !this.isStoryCharacter() && --this.chatter <= 0) {
            this.chatter = 500 + this.random.nextInt(900);
            if (this.panicTicks <= 0 && this.cheerTicks <= 0 && level.getNearestPlayer(this, 20.0) != null) {
                boolean night = !level.isDay();
                this.shout(this.getRole() == 1 ? "watch" : (night ? "night" : "chatter"), ChatFormatting.GRAY);
            }
        }
    }

    public void react(Vec3 at, int kind, double dist) {
        if (!this.isStoryCharacter() && this.reactCooldown <= 0) {
            this.reactCooldown = 20;
            if (this.getRole() == 1) {
                if (this.random.nextInt(3) == 0) {
                    this.shout(kind == 3 ? "grief" : "soldier", ChatFormatting.GOLD);
                }
            } else {
                this.panicFrom = at;
                this.panicTicks = Math.max(this.panicTicks, 90 + this.random.nextInt(80));
                this.cheerTicks = 0;
                if ((kind == 0 || kind == 1) && dist < 14.0 && this.random.nextBoolean()) {
                    this.cowerTicks = 18 + this.random.nextInt(14);
                }

                if (this.screamCooldown <= 0 && this.random.nextInt(2) == 0 && screamSlot(this.level())) {
                    this.screamCooldown = 200 + this.random.nextInt(200);
                    this.level()
                        .playSound(
                            null,
                            this.getX(),
                            this.getEyeY(),
                            this.getZ(),
                            ModSounds.NPC_SCREAM.get(),
                            SoundSource.NEUTRAL,
                            0.9F,
                            0.85F + this.random.nextFloat() * 0.35F
                        );
                }

                if (this.random.nextInt(3) == 0) {
                    this.shout(kind == 1 ? "collapse" : (kind == 3 ? "grief" : (kind == 2 ? "spotted" : "panic")), ChatFormatting.RED);
                }
            }
        }
    }

    public void cheer(Player hero, boolean talk) {
        if (!this.isStoryCharacter()) {
            this.panicTicks = Math.min(this.panicTicks, 10);
            this.cheerTicks = 60 + this.random.nextInt(40);
            this.cheerHero = hero.getId();
            if (talk || this.random.nextInt(3) == 0) {
                this.shout("cheer", ChatFormatting.GREEN);
            }
        }
    }

    public void shout(String group, ChatFormatting color) {
        if (!this.isStoryCharacter() && this.shoutTicks <= 0) {
            int n = switch (group) {
                case "panic" -> 8;
                case "collapse" -> 6;
                case "cheer" -> 8;
                case "grief" -> 5;
                case "soldier" -> 7;
                case "spotted" -> 5;
                case "night" -> 4;
                case "watch" -> 5;
                case "builder" -> 8;
                case "builder_start" -> 4;
                case "builder_done" -> 4;
                case "greet" -> 8;
                case "salute" -> 5;
                default -> 10;
            };
            this.setCustomName(Component.translatable("shout.takemtitans." + group + "." + this.random.nextInt(n)).withStyle(color));
            this.setCustomNameVisible(true);
            this.shoutTicks = 60;
        }
    }

    public boolean panicking() {
        return this.panicTicks > 0;
    }

    @Nullable
    private AbstractTitan nearestTitan(ServerLevel level, double range) {
        AbstractTitan best = null;
        double bestD = range * range;

        for (AbstractTitan t : level.getEntitiesOfClass(AbstractTitan.class, this.getBoundingBox().inflate(range))) {
            if (t.isAlive() && !t.isDummy() && !(t instanceof RumblingTitan)) {
                double d = this.distanceToSqr(t);
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
        }

        return best;
    }

    private void soldierFight(ServerLevel level, AbstractTitan titan) {
        this.leapCooldown -= 10;
        TitanPart nape = null;

        for (TitanPart part : titan.getTitanParts()) {
            if (part.kind == 1) {
                nape = part;
            }
        }

        if (nape != null) {
            if (this.random.nextInt(14) == 0) {
                this.shout("soldier", ChatFormatting.GOLD);
            }

            Vec3 target = nape.position().add(0.0, (double)nape.getBbHeight() * 0.5, 0.0);
            if (this.distanceToSqr(target) < 9.0) {
                titan.hurtPart(nape, level.damageSources().mobAttack(this), 18.0F);
                this.swing(InteractionHand.MAIN_HAND);
                level.sendParticles(ModParticles.BLOOD.get(), target.x, target.y, target.z, 12, 0.3, 0.3, 0.3, 0.2);
            } else if (this.leapCooldown <= 0 && this.onGround()) {
                this.leapCooldown = 50 + this.random.nextInt(40);
                Vec3 d = target.subtract(this.position());
                double flat = Math.sqrt(d.x * d.x + d.z * d.z);
                double t = Math.max(10.0, flat / 1.4);
                double vy = (d.y + 0.04 * t * t) / t;
                this.setDeltaMovement(d.x / t, Math.min(2.6, vy), d.z / t);
                this.hasImpulse = true;
                level.sendParticles(ModParticles.GAS.get(), this.getX(), this.getY() + 1.0, this.getZ(), 16, 0.3, 0.3, 0.3, 0.05);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GAS_BURST.get(), SoundSource.NEUTRAL, 1.0F, 1.1F);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return this.isStoryCharacter() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? false : super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel sl && !this.isStoryCharacter()) {
            TownLife.alarm(sl, this.position(), 28.0, 3);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return this.ambient && !this.isStoryCharacter();
    }

    @Override
    public boolean requiresCustomPersistence() {
        return this.isStoryCharacter() || super.requiresCustomPersistence();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        } else {
            if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
                this.getLookControl().setLookAt(player);
                Story.talk(sp, this);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
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
        tag.putInt("Role", this.getRole());
        tag.putInt("Variant", this.getVariant());
        tag.putBoolean("Ambient", this.ambient);
        if (this.home != null) {
            tag.putLong("Home", this.home.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setup(tag.getInt("Role"), tag.getInt("Variant"), tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null, tag.getBoolean("Ambient"));
    }

    public void openShop(ServerPlayer player) {
        this.setTradingPlayer(player);
        this.openTradingScreen(player, Component.translatable("npc.takemtitans.quartermaster.shop"), 1);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = shop();
        }

        return this.offers;
    }

    private static MerchantOffer offer(int badges, ItemStack result) {
        ItemCost a = new ItemCost(ModItems.MERIT_BADGE.get(), Math.min(64, badges));
        Optional<ItemCost> b = badges > 64 ? Optional.of(new ItemCost(ModItems.MERIT_BADGE.get(), badges - 64)) : Optional.empty();
        return new MerchantOffer(a, b, result, 0, 2147483647, 0, 0.0F);
    }

    private static MerchantOffers shop() {
        MerchantOffers o = new MerchantOffers();
        o.add(offer(2, new ItemStack(ModItems.GAS_CANISTER.get(), 4)));
        o.add(offer(2, new ItemStack(ModItems.BLADE_PACK.get(), 2)));
        o.add(offer(5, new ItemStack(ModItems.THUNDER_SPEAR.get(), 2)));
        o.add(offer(3, new ItemStack(ModItems.FLARE_RED.get(), 4)));
        o.add(offer(3, new ItemStack(ModItems.FLARE_GREEN.get(), 4)));
        o.add(offer(4, new ItemStack(ModItems.FLARE_PURPLE.get(), 2)));
        o.add(offer(24, new ItemStack(ModItems.VETERAN_BLADES.get())));
        o.add(offer(36, new ItemStack(ModItems.ADVANCED_ODM_GEAR.get())));
        o.add(offer(24, new ItemStack(ModItems.COLOSSAL_SERUM.get())));
        o.add(offer(20, new ItemStack(ModItems.BEAST_BOULDER.get(), 2)));
        o.add(offer(20, new ItemStack(ModItems.PATHS_SHARD.get())));
        o.add(offer(56, new ItemStack(ModItems.RUMBLING_HORN.get())));
        return o;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.playSound(SoundEvents.VILLAGER_YES, 0.6F, 0.8F);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    public static boolean isNear(AABB box, Level level) {
        return !level.getEntitiesOfClass(TownsfolkEntity.class, box).isEmpty();
    }

    final class BusyGoal extends Goal {
        BusyGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return TownsfolkEntity.this.busyInPlace();
        }

        @Override
        public void start() {
            TownsfolkEntity.this.getNavigation().stop();
        }
    }

    final class PanicGoal extends Goal {
        private int repath;

        PanicGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return TownsfolkEntity.this.panicTicks > 0
                && TownsfolkEntity.this.panicFrom != null
                && TownsfolkEntity.this.cowerTicks <= 0
                && TownsfolkEntity.this.getRole() != 1
                && !TownsfolkEntity.this.isStoryCharacter();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.repath = 0;
        }

        @Override
        public void tick() {
            if (--this.repath <= 0 || TownsfolkEntity.this.getNavigation().isDone()) {
                this.repath = 20;
                Vec3 away = DefaultRandomPos.getPosAway(TownsfolkEntity.this, 16, 6, TownsfolkEntity.this.panicFrom);
                if (away != null) {
                    TownsfolkEntity.this.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
                }
            }
        }

        @Override
        public void stop() {
            TownsfolkEntity.this.getNavigation().stop();
        }
    }
}
