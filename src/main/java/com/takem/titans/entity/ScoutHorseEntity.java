package com.takem.titans.entity;

import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A Survey Corps horse: saddled, wearing the regiment's green barding, bound to one rider. Blow the whistle and it
 * gallops to you from wherever it is; fire your hooks from the saddle and you launch straight off its back.
 * <p>
 * What an ordinary horse can't do: it builds up into a full charge the longer it runs (up to half again as fast), it
 * smells titans and marks every one within seventy blocks for its rider, it never breaks a leg on a fall, and titan
 * blows only do it half the harm.
 */
public class ScoutHorseEntity extends Horse {
    private static final Variant[] COATS = {Variant.BROWN, Variant.DARK_BROWN, Variant.BLACK, Variant.CHESTNUT, Variant.GRAY};
    private static final int CALL_TICKS = 600;

    private static final net.minecraft.resources.ResourceLocation GALLOP_ID = com.takem.titans.TakemTitans.id("horse_gallop");
    private static final int FULL_GALLOP = 100;

    @Nullable
    private UUID caller;
    private int callTicks;
    private int gallop;
    private int gallopStep = -1;
    private int sidestepCooldown;
    private int cutCooldown;
    private static final net.minecraft.resources.ResourceLocation LEAP_ID = com.takem.titans.TakemTitans.id("horse_leap");

    public ScoutHorseEntity(EntityType<? extends Horse> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractHorse.createBaseHorseAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.34)
            .add(Attributes.JUMP_STRENGTH, 0.95)
            .add(Attributes.SAFE_FALL_DISTANCE, 8.0)
            .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void randomizeAttributes(net.minecraft.util.RandomSource random) {
        // Every regiment horse is bred for speed: no random duds.
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        this.setVariant(COATS[this.random.nextInt(COATS.length)]);
        this.setTamed(true);
        this.setAge(0);
        this.equipSaddle(new ItemStack(Items.SADDLE), null);
        this.setBodyArmorItem(new ItemStack(ModItems.SCOUT_BARDING.get()));
        return out;
    }

    /** Spawns a fresh horse for {@code owner} somewhere behind them, out of sight, and sends it galloping in. */
    @Nullable
    public static ScoutHorseEntity fromStables(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        ScoutHorseEntity horse = ModEntities.SCOUT_HORSE.get().create(level);
        if (horse == null) {
            return null;
        }

        Vec3 at = arrivalPoint(level, owner, 26.0);
        horse.moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
        horse.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        horse.setOwnerUUID(owner.getUUID());
        level.addFreshEntity(horse);
        OdmServer.data(owner).horse = horse.getUUID();
        return horse;
    }

    private static Vec3 arrivalPoint(ServerLevel level, ServerPlayer owner, double dist) {
        Vec3 back = Vec3.directionFromRotation(0.0F, owner.getYRot()).scale(-dist);
        for (int i = 0; i < 8; i++) {
            Vec3 p = owner.position().add(back.yRot((float) (i * Math.PI / 4.0)));
            int x = Mth.floor(p.x), z = Mth.floor(p.z);
            if (level.hasChunkAt(new BlockPos(x, 0, z))) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - owner.getY()) < 12) {
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }

        return owner.position();
    }

    /** The whistle: run to the caller (or come out of the stables near them if it's far away or in another place). */
    public void callTo(ServerPlayer player) {
        if (this.level() != player.level() || this.distanceToSqr(player) > 140.0 * 140.0) {
            Vec3 at = arrivalPoint(player.serverLevel(), player, 26.0);
            this.ejectPassengers();
            if (this.level() != player.level()) {
                this.discard();
                ScoutHorseEntity fresh = fromStables(player);
                if (fresh != null) {
                    fresh.callTo(player);
                }
                return;
            }

            this.teleportTo(at.x, at.y, at.z);
        }

        this.ejectPassengers();
        this.caller = player.getUUID();
        this.callTicks = CALL_TICKS;
        this.playSound(SoundEvents.HORSE_ANGRY, 1.4F, 1.0F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level() instanceof ServerLevel level) {
            this.riderPerks(level);
        }

        if (this.level() instanceof ServerLevel level && this.tickCount % 100 == 0 && this.getOwnerUUID() != null
            && level.getServer().getPlayerList().getPlayer(this.getOwnerUUID()) instanceof ServerPlayer owner
            && !this.getUUID().equals(OdmServer.data(owner).horse) && !this.isVehicle()) {
            // A replacement came from the stables while this one was far away: it goes home
            this.discard();
            return;
        }

        if (this.level() instanceof ServerLevel level && this.caller != null && this.callTicks > 0) {
            Player p = level.getPlayerByUUID(this.caller);
            if (p == null || this.isVehicle() || --this.callTicks <= 0) {
                this.stopCall();
                return;
            }

            double d = this.distanceToSqr(p);
            if (d < 9.0) {
                this.getNavigation().stop();
                this.playSound(SoundEvents.HORSE_BREATHE, 1.0F, 1.0F);
                this.getLookControl().setLookAt(p);
                this.stopCall();
            } else if (this.tickCount % 10 == 0) {
                this.getNavigation().moveTo(p, d > 20.0 * 20.0 ? 3.0 : 2.2);
                if (d > 30.0 * 30.0 && this.tickCount % 40 == 0 && this.getNavigation().isDone()) {
                    // stuck behind a wall or a ruin: jump closer
                    Vec3 at = arrivalPoint(level, (ServerPlayer)p, 12.0);
                    this.teleportTo(at.x, at.y, at.z);
                    level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 12, 0.6, 0.3, 0.6, 0.02);
                }
            }
        }
    }

    private void riderPerks(ServerLevel level) {
        ServerPlayer rider = this.getControllingPassenger() instanceof ServerPlayer p ? p : null;
        double speed = this.getDeltaMovement().horizontalDistance();
        if (rider == null) {
            this.gallop = 0;
        } else if (speed > 0.22 && this.onGround()) {
            this.gallop = Math.min(FULL_GALLOP, this.gallop + 1);
        } else if (speed < 0.1) {
            this.gallop = Math.max(0, this.gallop - 5);
        }

        int step = this.gallop / 20;
        if (step != this.gallopStep) {
            this.gallopStep = step;
            net.minecraft.world.entity.ai.attributes.AttributeInstance move = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (move != null) {
                move.removeModifier(GALLOP_ID);
                if (step > 0) {
                    move.addTransientModifier(
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            GALLOP_ID, 0.1 * step, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                        )
                    );
                }
            }

            // at a gallop the horse also clears what it would otherwise have to stop for
            net.minecraft.world.entity.ai.attributes.AttributeInstance jump = this.getAttribute(Attributes.JUMP_STRENGTH);
            if (jump != null) {
                jump.removeModifier(LEAP_ID);
                if (step > 1) {
                    jump.addTransientModifier(
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            LEAP_ID, 0.06 * (double)step, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                        )
                    );
                }
            }

            if (step == 5 && rider != null) {
                this.playSound(SoundEvents.HORSE_GALLOP, 1.4F, 0.8F);
                rider.displayClientMessage(Component.translatable("message.takemtitans.horse_charge").withStyle(ChatFormatting.GOLD), true);
            }
        }

        if (step >= 3 && this.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.4, 0.05, 0.4, 0.005);
        }

        if (this.sidestepCooldown > 0) {
            this.sidestepCooldown--;
        }

        if (this.cutCooldown > 0) {
            this.cutCooldown--;
        }

        if (rider != null && step >= 2) {
            this.rideBy(level, rider, step);
            this.trample(level, rider, step);
        }

        if (rider != null && this.tickCount % 40 == 0) {
            boolean close = false;
            for (com.takem.titans.entity.titan.AbstractTitan t : level.getEntitiesOfClass(
                com.takem.titans.entity.titan.AbstractTitan.class, this.getBoundingBox().inflate(72.0), t -> t.isAlive() && !t.isDummy()
            )) {
                t.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 60, 0, false, false));
                close |= t.distanceToSqr(this) < 40.0 * 40.0;
            }

            if (close && this.tickCount % 160 == 0) {
                this.playSound(SoundEvents.HORSE_ANGRY, 1.2F, 1.1F);
                rider.displayClientMessage(Component.translatable("message.takemtitans.horse_senses").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /**
     * The old cavalry trick: ride past a titan with your blades out and cut it behind the knee. At a gallop any leg
     * within reach of the rider's blades is slashed, harder the faster the horse runs - a titan with both tendons cut
     * goes down for the nape.
     */
    private void rideBy(ServerLevel level, ServerPlayer rider, int step) {
        if (this.cutCooldown <= 0 && rider.getMainHandItem().getItem() instanceof com.takem.titans.item.BladeItem
            && !com.takem.titans.item.BladeItem.isBroken(rider.getMainHandItem())) {
            net.minecraft.world.phys.AABB reach = rider.getBoundingBox().inflate(3.2, 1.5, 3.2);
            for (com.takem.titans.entity.titan.TitanPart part : level.getEntitiesOfClass(
                com.takem.titans.entity.titan.TitanPart.class, reach, p -> p.active && p.kind == 4
            )) {
                float dmg = 8.0F + 5.0F * (float)step;
                if (part.hurt(level.damageSources().playerAttack(rider), dmg)) {
                    Vec3 at = part.getBoundingBox().getCenter();
                    com.takem.titans.fx.Fx.send(level, 3, at, rider.getYRot(), 0.0F, (this.random.nextFloat() - 0.5F) * 60.0F, 2.2F, 96.0);
                    level.playSound(null, at.x, at.y, at.z, com.takem.titans.registry.ModSounds.BLADE_HIT.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.4F, 0.8F);
                    rider.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    this.cutCooldown = 10;
                    break;
                }
            }
        }
    }

    /** A horse at a gallop does not stop for a wolf, a zombie or a crawling titan's hand: it bowls them over. */
    private void trample(ServerLevel level, ServerPlayer rider, int step) {
        if (step >= 4 && this.tickCount % 4 == 0) {
            Vec3 fwd = this.getDeltaMovement().multiply(1.0, 0.0, 1.0);
            if (fwd.lengthSqr() > 1.0E-4) {
                fwd = fwd.normalize();
                for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    this.getBoundingBox().move(fwd.scale(0.8)).inflate(0.3),
                    e -> e != this && e != rider && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player)
                        && !(e instanceof com.takem.titans.entity.titan.AbstractTitan) && !(e instanceof com.takem.titans.entity.npc.TownsfolkEntity)
                        && !(e instanceof com.takem.titans.entity.npc.JaegerEntity)
                )) {
                    e.hurt(level.damageSources().mobAttack(this), 6.0F);
                    e.push(fwd.x * 1.2, 0.4, fwd.z * 1.2);
                    e.hurtMarked = true;
                }
            }
        }
    }

    /** Dash in the saddle: the horse throws itself sideways out of a grab, a stomp or a falling wall. */
    public void sidestep(ServerPlayer rider, int dir) {
        if (this.sidestepCooldown <= 0 && this.onGround()) {
            this.sidestepCooldown = 50;
            float yaw = this.getYRot() * 0.017453292F;
            Vec3 left = new Vec3(Math.cos((double)yaw), 0.0, Math.sin((double)yaw));
            Vec3 fwd = new Vec3(-Math.sin((double)yaw), 0.0, Math.cos((double)yaw));
            int side = dir == 0 ? (this.random.nextBoolean() ? 1 : -1) : dir;
            this.setDeltaMovement(left.scale(1.15 * (double)side).add(fwd.scale(0.35)).add(0.0, 0.42, 0.0));
            this.hurtMarked = true;
            this.playSound(SoundEvents.HORSE_JUMP, 1.2F, 0.9F);
            this.playSound(SoundEvents.HORSE_BREATHE, 1.0F, 1.2F);
            if (this.level() instanceof ServerLevel sl) {
                com.takem.titans.world.Destruction.dustCloud(sl, this.position(), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2.5F, 0.8F);
            }
            com.takem.titans.odm.OdmServer.data(rider).immuneTicks = Math.max(com.takem.titans.odm.OdmServer.data(rider).immuneTicks, 6);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    private void stopCall() {
        this.caller = null;
        this.callTicks = 0;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // The rider's own blades and blasts don't hurt their horse
        if (source.getEntity() != null && source.getEntity().getUUID().equals(this.getOwnerUUID())) {
            return false;
        }

        if (source.getEntity() instanceof com.takem.titans.entity.titan.AbstractTitan) {
            amount *= 0.5F;
        }

        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level && this.getOwnerUUID() != null
            && level.getServer().getPlayerList().getPlayer(this.getOwnerUUID()) instanceof ServerPlayer owner) {
            OdmPlayerData d = OdmServer.data(owner);
            if (this.getUUID().equals(d.horse)) {
                d.horse = null;
                d.horseReadyAt = level.getGameTime() + 2400L;
                owner.displayClientMessage(Component.translatable("message.takemtitans.horse_fallen").withStyle(ChatFormatting.RED), false);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ScoutHorse", true);
    }
}
