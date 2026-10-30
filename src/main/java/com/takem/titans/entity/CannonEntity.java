package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanSkeleton;
import com.takem.titans.item.CannonItem;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.ShiganshinaLayout;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Entity.MoveFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class CannonEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(CannonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIRED = SynchedEntityData.defineId(CannonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CREW = SynchedEntityData.defineId(CannonEntity.class, EntityDataSerializers.BOOLEAN);
    private int cooldown;
    private int hits;
    private boolean relocated;
    /** The way a gun on a wall ledge faces; it only fires on that side, never back over its own wall. NaN: anywhere. */
    private float homeYaw = Float.NaN;

    public CannonEntity(EntityType<? extends CannonEntity> type, Level level) {
        super(type, level);
    }

    public static CannonEntity create(Level level, Vec3 at, float yaw, int tier, boolean crew) {
        CannonEntity c = new CannonEntity(ModEntities.CANNON.get(), level);
        c.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        c.setTier(tier);
        c.entityData.set(CREW, crew);
        return c;
    }

    @Override
    protected void defineSynchedData(Builder builder) {
        builder.define(TIER, 1);
        builder.define(FIRED, -1000);
        builder.define(CREW, false);
    }

    public int tier() {
        return this.entityData.get(TIER);
    }

    public void setTier(int t) {
        this.entityData.set(TIER, Mth.clamp(t, 1, 3));
    }

    public boolean crew() {
        return this.entityData.get(CREW);
    }

    /** Placed on a gun ledge by the Garrison: it stays there and keeps to the side it faces. */
    public void mountOnLedge() {
        this.relocated = true;
        this.homeYaw = this.getYRot();
    }

    private boolean inArc(AbstractTitan t) {
        if (Float.isNaN(this.homeYaw)) {
            return true;
        }

        double dx = t.getX() - this.getX();
        double dz = t.getZ() - this.getZ();
        double fx = -Mth.sin(this.homeYaw * 0.017453292F);
        double fz = Mth.cos(this.homeYaw * 0.017453292F);
        return dx * fx + dz * fz > 0.35 * Math.sqrt(dx * dx + dz * dz);
    }

    public int reloadTicks() {
        return switch (this.tier()) {
            case 2 -> 60;
            case 3 -> 80;
            default -> 36;
        };
    }

    public float readiness() {
        return Mth.clamp((float)((int)this.level().getGameTime() - this.entityData.get(FIRED)) / (float)this.reloadTicks(), 0.0F, 1.0F);
    }

    public float recoil(float pt) {
        float t = (float)((int)this.level().getGameTime() - this.entityData.get(FIRED)) + pt;
        return t < 0.0F ? 0.0F : Math.max(0.0F, 1.0F - t / 10.0F);
    }

    public double speed() {
        return switch (this.tier()) {
            case 2 -> 3.4;
            case 3 -> 3.8;
            default -> 3.0;
        };
    }

    public double range() {
        return switch (this.tier()) {
            case 2 -> 120.0;
            case 3 -> 150.0;
            default -> 90.0;
        };
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        } else if (this.tier() == 1 && held.is(ModItems.ULTRAHARD_STEEL_INGOT.get()) && held.getCount() >= 4) {
            held.consume(4, player);
            this.upgrade(player, 2);
            return InteractionResult.CONSUME;
        } else if (this.tier() == 2 && held.is(ModItems.TITAN_HEART_SHARD.get()) && held.getCount() >= 2) {
            held.consume(2, player);
            this.upgrade(player, 3);
            return InteractionResult.CONSUME;
        } else if (player.isSecondaryUseActive() && held.isEmpty()) {
            this.entityData.set(CREW, !this.crew());
            player.displayClientMessage(
                Component.translatable(this.crew() ? "message.takemtitans.cannon_crew_on" : "message.takemtitans.cannon_crew_off"), true
            );
            return InteractionResult.CONSUME;
        } else if (!this.isVehicle() && !player.isPassenger()) {
            player.startRiding(this);
            player.displayClientMessage(Component.translatable("message.takemtitans.cannon_manned").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.CONSUME;
        } else {
            return InteractionResult.PASS;
        }
    }

    private void upgrade(Player player, int tier) {
        this.setTier(tier);
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.2F, 0.8F);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.6, 0.4, 0.6, 0.2);
        }

        player.displayClientMessage(
            Component.translatable("message.takemtitans.cannon_upgraded", Component.translatable("item.takemtitans." + itemName(tier))), true
        );
    }

    static String itemName(int tier) {
        return switch (tier) {
            case 2 -> "siege_cannon";
            case 3 -> "thunder_battery";
            default -> "field_cannon";
        };
    }

    public static Item item(int tier) {
        return switch (tier) {
            case 2 -> (CannonItem)ModItems.SIEGE_CANNON.get();
            case 3 -> (CannonItem)ModItems.THUNDER_BATTERY.get();
            default -> (CannonItem)ModItems.FIELD_CANNON.get();
        };
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (this.hasPassenger(passenger)) {
            float yaw = this.getYRot() * 0.017453292F;
            double back = 1.7;
            move.accept(passenger, this.getX() + (double)Mth.sin(yaw) * back, this.getY() + 0.05, this.getZ() - (double)Mth.cos(yaw) * back);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && source.getEntity() instanceof Player p && source.getDirectEntity() == p) {
            if (p.isSecondaryUseActive() || p.isCreative() || ++this.hits >= 6) {
                if (!p.isCreative()) {
                    this.spawnAtLocation(new ItemStack(item(this.tier())));
                }

                this.discard();
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.06, 0.0));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.98, 0.5));
        if (this.getFirstPassenger() instanceof Player p) {
            this.setYRot(p.getYRot());
            this.setXRot(Mth.clamp(p.getXRot(), -40.0F, 25.0F));
            this.yRotO = this.getYRot();
            if (!this.level().isClientSide && (Math.abs(p.zza) > 0.01F || Math.abs(p.xxa) > 0.01F)) {
                float yaw = this.getYRot() * 0.017453292F;
                Vec3 fwd = new Vec3((double)(-Mth.sin(yaw)), 0.0, (double)Mth.cos(yaw));
                Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
                Vec3 step = fwd.scale((double)p.zza * 0.07).add(right.scale((double)(-p.xxa) * 0.06));
                Vec3 probe = this.position().add(step.normalize().scale(1.4));
                BlockPos below = BlockPos.containing(probe.x, this.getY() - 0.5, probe.z);
                if (!this.level().getBlockState(below).getCollisionShape(this.level(), below).isEmpty()) {
                    this.move(MoverType.SELF, step);
                    if (this.tickCount % 6 == 0) {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.3F, 0.6F);
                    }
                }
            }
        }

        if (this.level() instanceof ServerLevel sl) {
            if (this.cooldown > 0) {
                this.cooldown--;
            }

            if (this.crew() && !this.relocated && this.tickCount > 5) {
                this.relocated = true;
                this.toOuterRim(sl);
            }

            if (this.crew() && !this.isVehicle() && this.tickCount % 2 == 0) {
                this.crewTick(sl);
            }
        }
    }

    private void crewTick(ServerLevel sl) {
        if (sl.getNearestPlayer(this, 160.0) != null) {
            AbstractTitan best = null;
            double bestD = 1.7976931348623157E308;
            double range = this.range();

            for (AbstractTitan t : sl.getEntitiesOfClass(
                AbstractTitan.class,
                new AABB(this.position(), this.position()).inflate(range, 80.0, range),
                tx -> tx.isAlive() && !(tx instanceof RumblingTitan) && !tx.isDummy() && this.inArc(tx)
            )) {
                double d = t.distanceToSqr(this);
                if (d < bestD && d > 64.0) {
                    bestD = d;
                    best = t;
                }
            }

            if (best != null) {
                Vec3 aim = best.skeletonPoint(3, TitanSkeleton.NAPE).add(best.getDeltaMovement().scale(Math.sqrt(bestD) / this.speed()));
                float[] yp = solve(this.muzzleBase(), aim, this.speed());
                if (yp != null) {
                    float dy = Mth.wrapDegrees(yp[0] - this.getYRot());
                    this.setYRot(this.getYRot() + Mth.clamp(dy, -8.0F, 8.0F));
                    this.setXRot(Mth.approachDegrees(this.getXRot(), yp[1], 4.0F));
                    if (Math.abs(dy) < 3.0F && this.cooldown <= 0) {
                        this.fire(sl, null);
                        // a gun crew sponges, loads and runs the gun out again between shots: far slower than a soldier
                        // with his own hand on the lanyard
                        this.cooldown = this.reloadTicks() * 2 + 80 + this.random.nextInt(90);
                    }
                }
            }
        }
    }

    private void toOuterRim(ServerLevel sl) {
        double depth = ShiganshinaLayout.wallDepth(this.getX(), this.getZ());
        if (depth >= 0.0 && depth < 8.0) {
            int ring = ShiganshinaLayout.ring(this.getX(), this.getZ());
            double cx = ShiganshinaLayout.ringX(ring);
            double cz = ShiganshinaLayout.ringZ(ring);
            double r = ShiganshinaLayout.ringR(ring);
            Vec3 out = new Vec3(this.getX() - cx, 0.0, this.getZ() - cz);
            if (out.lengthSqr() > 1.0) {
                out = out.normalize();
                double x = cx + out.x * (r + 3.5);
                double z = cz + out.z * (r + 3.5);
                int y = sl.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                if (y > 100) {
                    this.moveTo(x, (double)y, z, (float)(Mth.atan2(out.z, out.x) * 57.29577951308232) - 90.0F, 0.0F);
                }
            }
        }
    }

    @Nullable
    static float[] solve(Vec3 from, Vec3 to, double v) {
        double g = 0.03;
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double h = to.y - from.y;
        double v2 = v * v;
        double disc = v2 * v2 - g * (g * d * d + 2.0 * h * v2);
        if (!(disc < 0.0) && !(d < 0.001)) {
            double theta = Math.atan((v2 - Math.sqrt(disc)) / (g * d));
            float yaw = (float)(Mth.atan2(dz, dx) * 57.29577951308232) - 90.0F;
            return new float[]{yaw, (float)(-theta * 57.29577951308232)};
        } else {
            return null;
        }
    }

    Vec3 muzzleBase() {
        return this.position().add(0.0, 1.35, 0.0);
    }

    Vec3 aimDir() {
        return Vec3.directionFromRotation(this.getXRot(), this.getYRot());
    }

    public void tryFire(ServerPlayer player) {
        if (this.cooldown <= 0 && this.getFirstPassenger() == player && this.level() instanceof ServerLevel sl) {
            this.fire(sl, player);
        }
    }

    private void fire(ServerLevel sl, @Nullable Player shooter) {
        int tier = this.tier();
        this.cooldown = this.reloadTicks();
        this.entityData.set(FIRED, (int)sl.getGameTime());
        Vec3 dir = this.aimDir();
        Vec3 muzzle = this.muzzleBase().add(dir.scale(2.8));
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x).normalize();
        int shells = tier == 3 ? 3 : 1;

        for (int i = 0; i < shells; i++) {
            double off = shells == 1 ? 0.0 : (double)(i - 1) * 0.55;
            Vec3 at = muzzle.add(side.scale(off));
            CannonballEntity ball = new CannonballEntity(sl, at.x, at.y, at.z, shooter, tier);
            Vec3 v = dir.scale(this.speed())
                .add(side.scale(off * 0.03))
                .add((this.random.nextDouble() - 0.5) * 0.04, (this.random.nextDouble() - 0.5) * 0.03, (this.random.nextDouble() - 0.5) * 0.04);
            ball.setDeltaMovement(v);
            sl.addFreshEntity(ball);
        }

        SoundEvent snd = tier == 3 ? ModSounds.CANNON_BATTERY.get() : (tier == 2 ? ModSounds.CANNON_HEAVY.get() : ModSounds.CANNON_FIRE.get());
        sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, snd, SoundSource.BLOCKS, 6.0F + (float)tier * 2.0F, 0.9F + this.random.nextFloat() * 0.15F);
        sl.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, tier, 0.2, 0.2, 0.2, 0.0);
        sl.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 20 * tier, 0.2, 0.2, 0.2, 0.12);

        for (int k = 0; k < 6 + tier * 4; k++) {
            Vec3 p = muzzle.add(dir.scale((double)k * 0.5));
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.03);
            sl.sendParticles(ModParticles.DUST_CLOUD.get(), p.x, p.y, p.z, 1, 0.4, 0.4, 0.4, 0.02);
        }

        AbstractTitan.shakeNearby(sl, muzzle, 24.0 + (double)tier * 8.0, 0.25F + (float)tier * 0.12F, 8);
        if (shooter instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(2, 40 + tier * 20, 8, 0));
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CANNON_RELOAD.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }

        this.setDeltaMovement(this.getDeltaMovement().add(dir.multiply(-0.15 * (double)tier, 0.0, -0.15 * (double)tier)));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setTier(tag.getInt("Tier"));
        this.entityData.set(CREW, tag.getBoolean("Crew"));
        this.relocated = tag.getBoolean("Relocated");
        this.homeYaw = tag.contains("HomeYaw") ? tag.getFloat("HomeYaw") : Float.NaN;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Tier", this.tier());
        tag.putBoolean("Crew", this.crew());
        tag.putBoolean("Relocated", this.relocated);
        if (!Float.isNaN(this.homeYaw)) {
            tag.putFloat("HomeYaw", this.homeYaw);
        }
    }
}
