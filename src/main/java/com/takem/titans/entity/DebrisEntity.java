package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.odm.Grapple;
import com.takem.titans.odm.Tether;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.world.Attractors;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.Rebuild;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DebrisEntity extends Entity {
    private BlockState state = Blocks.STONE.defaultBlockState();
    private float rubbleChance;
    private int restTicks;
    private int life;
    private int breaks;
    private int punches;
    public float spin;
    public float spinO;
    public float spinRate;
    public float axisX;
    public float axisY;
    public float axisZ;
    public final Tether tether = new Tether();

    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = false;
        this.noCulling = false;
    }

    public static DebrisEntity create(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity, float rubbleChance) {
        DebrisEntity e = new DebrisEntity(ModEntities.DEBRIS.get(), level);
        e.state = state;
        e.rubbleChance = rubbleChance;
        e.setPos((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5);
        e.xo = e.getX();
        e.yo = e.getY();
        e.zo = e.getZ();
        e.setDeltaMovement(velocity);
        return e;
    }

    public static DebrisEntity create(ServerLevel level, Vec3 pos, BlockState state, Vec3 velocity, float rubbleChance) {
        DebrisEntity e = new DebrisEntity(ModEntities.DEBRIS.get(), level);
        e.state = state;
        e.rubbleChance = rubbleChance;
        e.setPos(pos.x, pos.y - 0.5, pos.z);
        e.xo = e.getX();
        e.yo = e.getY();
        e.zo = e.getZ();
        e.setDeltaMovement(velocity);
        return e;
    }

    public BlockState state() {
        return this.state;
    }

    public DebrisEntity withBreaks(int breaks) {
        this.breaks = breaks;
        return this;
    }

    private int maxPunches() {
        float d = Destruction.density(this.state);
        return this.breaks + (d >= 2.0F ? 2 : (d >= 0.8F ? 1 : 0));
    }

    public BlockState getBlockState() {
        return this.state;
    }

    @Override
    protected void defineSynchedData(Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.045;
    }

    @Override
    public void tick() {
        if (this.state.isAir()) {
            this.discard();
        } else {
            this.life++;
            Vec3 v = this.getDeltaMovement().add(0.0, -this.getGravity(), 0.0);
            Vec3 pull = Attractors.accel(this.level(), this.position());
            if (pull != null) {
                v = v.add(pull).scale(0.96);
                if (Attractors.swallow(this)) {
                    return;
                }
            }

            if (!this.level().isClientSide) {
                if (this.tether.thrown > 0) {
                    this.tether.thrown--;
                }

                if (this.tether.active()) {
                    v = Grapple.constrain(this, this.tether, this.position().add(0.0, 0.5, 0.0), v, 0.6);
                    this.restTicks = 0;
                    this.life = Math.min(this.life, 200);
                }
            }

            boolean wet = !this.level().getFluidState(this.blockPosition()).isEmpty();
            if (wet) {
                v = v.scale(0.8);
            }

            if ((this.punches < this.maxPunches() || this.tether.thrown > 0 && this.punches < 6) && this.level() instanceof ServerLevel sl) {
                double v2 = v.lengthSqr();
                if (v2 > (this.tether.thrown > 0 || this.breaks > 0 ? 0.16 : 0.42)) {
                    BlockPos ahead = BlockPos.containing(this.getX() + v.x * 1.2, this.getY() + 0.5 + v.y * 1.2, this.getZ() + v.z * 1.2);
                    BlockState hit = sl.getBlockState(ahead);
                    if (!hit.isAir()) {
                        float h = hit.getDestroySpeed(sl, ahead);
                        if (h >= 0.0F && (double)Destruction.density(this.state) * v2 > (double)h * 0.9 + 0.25 && Destruction.canSmash(sl, ahead, hit, 3.0F)) {
                            Destruction.smash(sl, ahead, hit, v.scale(0.5).add(0.0, 0.05, 0.0), 0.2F);
                            if (this.punches == 0) {
                                Destruction.requestCollapse(sl, ahead, 7, 40, 3);
                            }

                            v = v.scale(0.65);
                            this.punches++;
                        }
                    }
                }
            }

            // The velocity has to be stored before moving: move() only zeroes the axes that collide, so reading it
            // back afterwards gives the post-collision velocity. Without this, gravity never accumulated and debris
            // drifted down at a constant crawl instead of falling.
            this.setDeltaMovement(v);
            this.move(MoverType.SELF, v);
            Vec3 after = this.getDeltaMovement();
            double hx = after.x;
            double hy = after.y;
            double hz = after.z;
            if (this.verticalCollision && v.y < -0.22) {
                float r1 = this.hash(1);
                float r2 = this.hash(2);
                double kick = -v.y;
                hy = kick * (0.18 + 0.12 * (double)r1);
                hx = v.x * 0.55 + ((double)r1 - 0.5) * kick * 0.45;
                hz = v.z * 0.55 + ((double)r2 - 0.5) * kick * 0.45;
                if (!this.level().isClientSide && kick > 0.6) {
                    this.impactEffects(kick);
                }
            } else if (this.onGround()) {
                hx *= 0.55;
                hz *= 0.55;
            } else {
                hx *= 0.988;
                hz *= 0.988;
            }

            if (this.horizontalCollision) {
                if (Math.abs(after.x) < 1.0E-4 && Math.abs(v.x) > 0.05) {
                    hx = -v.x * 0.3;
                }

                if (Math.abs(after.z) < 1.0E-4 && Math.abs(v.z) > 0.05) {
                    hz = -v.z * 0.3;
                }
            }

            this.setDeltaMovement(hx, this.onGround() ? hy : hy * 0.988, hz);
            if (this.level().isClientSide) {
                this.clientTick();
            } else {
                double speed = v.length();
                if (speed > 0.38 && (this.life & 1) == 0) {
                    this.hurtEntities(speed, v);
                }

                if (this.onGround() && hx * hx + hz * hz < 0.004 && Math.abs(hy) < 0.08) {
                    this.restTicks++;
                } else {
                    this.restTicks = 0;
                }

                if (!this.tether.active() && (this.restTicks > 4 || this.life > 400) || this.getY() < (double)(this.level().getMinBuildHeight() - 8)) {
                    this.settle();
                }
            }
        }
    }

    private float hash(int salt) {
        int h = this.getId() * 73856093 ^ (int)Math.floor(this.getX() * 4.0) * 19349663 ^ (int)Math.floor(this.getZ() * 4.0) * 83492791 ^ salt * 2654435;
        h ^= h >>> 13;
        h *= 1540483477;
        h ^= h >>> 15;
        return (float)(h & 65535) / 65535.0F;
    }

    private void clientTick() {
        if (this.spinRate == 0.0F && this.life == 1) {
            float r = this.hash(7) * 6.283F;
            float u = this.hash(9) * 2.0F - 1.0F;
            float s = Mth.sqrt(1.0F - u * u);
            this.axisX = s * Mth.cos(r);
            this.axisY = u;
            this.axisZ = s * Mth.sin(r);
            this.spinRate = (float)(6.0 + 22.0 * (double)this.hash(11)) * (float)Math.min(1.0, this.getDeltaMovement().length() * 1.6 + 0.25);
        }

        this.spinO = this.spin;
        if (this.onGround()) {
            float target = this.spin > 180.0F ? 360.0F : 0.0F;
            this.spin = this.spin + (target - this.spin) * 0.35F;
            this.spinO = this.spin;
            this.spinRate *= 0.5F;
        } else {
            this.spin = this.spin + this.spinRate;
            if (this.spin >= 360.0F) {
                this.spin -= 360.0F;
                this.spinO -= 360.0F;
            }
        }
    }

    private void impactEffects(double kick) {
        ServerLevel sl = (ServerLevel)this.level();
        sl.sendParticles(
            new BlockParticleOption(ParticleTypes.BLOCK, this.state),
            this.getX(),
            this.getY() + 0.2,
            this.getZ(),
            (int)Math.min(10.0, 3.0 + kick * 6.0),
            0.35,
            0.15,
            0.35,
            0.12
        );
        if (Destruction.soundBudget(sl)) {
            sl.playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                this.state.getSoundType().getBreakSound(),
                SoundSource.BLOCKS,
                (float)Math.min(1.6, 0.5 + kick),
                0.6F + this.random.nextFloat() * 0.3F
            );
        }
    }

    private void hurtEntities(double speed, Vec3 v) {
        float dmg = (float)Math.min(14.0, speed * 3.0 * (1.0 + (double)Destruction.density(this.state)));
        Entity holder = this.tether.active() ? this.level().getEntity(this.tether.holder) : null;
        Player thrower = this.tether.thrown > 0 && this.tether.thrower != null ? this.level().getPlayerByUUID(this.tether.thrower) : null;
        DamageSource src = thrower != null ? this.damageSources().thrown(this, thrower) : this.damageSources().fallingBlock(this);
        if (thrower != null) {
            dmg = (float)Math.min(90.0, (double)dmg * 3.0 * (double)this.tether.power);
            Iterator var8 = this.level().getEntitiesOfClass(TitanPart.class, this.getBoundingBox().inflate(0.4), px -> px.active).iterator();
            if (var8.hasNext()) {
                TitanPart part = (TitanPart)var8.next();
                float power = (float)(speed * speed * 6.0 * (1.0 + (double)Destruction.density(this.state)) * (double)this.tether.power);
                part.getParent().impactPart(part, power, thrower, src);
                this.tether.thrown = 0;
                this.setDeltaMovement(v.scale(-0.2));
                return;
            }
        }

        for (LivingEntity e : this.level()
            .getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(0.15),
                ex -> ex.isAlive() && !(ex instanceof AbstractTitan) && !ex.isSpectator() && ex != holder && ex != thrower
            )) {
            if (e.hurt(src, dmg)) {
                e.push(v.x * 0.35, Math.max(0.05, v.y * 0.2), v.z * 0.35);
            }
        }
    }

    private void settle() {
        ServerLevel sl = (ServerLevel)this.level();
        BlockPos pos = this.blockPosition();
        BlockState here = sl.getBlockState(pos);
        boolean placed = false;
        if (this.rubbleChance > 0.0F
            && this.random.nextFloat() < this.rubbleChance
            && canBeRubble(this.state)
            && here.canBeReplaced()
            && here.getFluidState().isEmpty()
            && sl.getBlockState(pos.below()).isFaceSturdy(sl, pos.below(), Direction.UP)
            && sl.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> !(e instanceof AbstractTitan)).isEmpty()) {
            placed = sl.setBlock(pos, this.rubbleState(this.state), 3);
            Rebuild.mark(sl, pos);
        }

        if (!placed) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.state), this.getX(), this.getY() + 0.4, this.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
            if (Destruction.soundBudget(sl) && this.random.nextInt(3) == 0) {
                sl.playSound(null, this.getX(), this.getY(), this.getZ(), this.state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 0.8F);
            }
        }

        this.discard();
    }

    private static boolean canBeRubble(BlockState s) {
        return !(s.getBlock() instanceof StairBlock) && !(s.getBlock() instanceof SlabBlock)
            ? s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !s.hasBlockEntity()
            : true;
    }

    private BlockState rubbleState(BlockState s) {
        if (s.getBlock() instanceof StairBlock && s.hasProperty(StairBlock.FACING)) {
            s = s.setValue(StairBlock.FACING, Plane.HORIZONTAL.getRandomDirection(this.random));
        }

        return s;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!this.level().isClientSide) {
            Destruction.debrisAdded();
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!this.level().isClientSide) {
            Destruction.debrisRemoved();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 25600.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.state = NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), tag.getCompound("BlockState"));
        this.rubbleChance = tag.getFloat("Rubble");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("BlockState", NbtUtils.writeBlockState(this.state));
        tag.putFloat("Rubble", this.rubbleChance);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity, Block.getId(this.state));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.state = Block.stateById(packet.getData());
        this.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
    }
}
