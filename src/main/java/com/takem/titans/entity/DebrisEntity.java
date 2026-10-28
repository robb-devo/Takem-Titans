package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.LocalFx;
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
    private double corrX;
    private double corrY;
    private double corrZ;
    private int corrTicks;

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
            if (this.level().isClientSide && this.corrTicks > 0) {
                // ease towards where the server says we are instead of jumping there
                this.corrTicks--;
                this.setPos(this.getX() + this.corrX, this.getY() + this.corrY, this.getZ() + this.corrZ);
            }

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
                // A block landing on the street gives back only a little of its fall as a hop (timber more than
                // stone) and friction eats its skid in proportion to how hard it hit. The ground knocks it just
                // slightly off line - it used to be kicked sideways at up to half its fall speed, which sent beams
                // leaping across the street.
                double e = restitution(this.state) * (0.7 + 0.6 * (double)r1);
                hy = kick * e;
                double skid = Math.sqrt(v.x * v.x + v.z * v.z);
                double keep = skid < 1.0E-4 ? 0.0 : Math.max(0.0, 1.0 - 0.5 * (1.0 + e) * kick / skid);
                hx = v.x * keep + ((double)r1 - 0.5) * kick * 0.06;
                hz = v.z * keep + ((double)r2 - 0.5) * kick * 0.06;
                if (this.level().isClientSide) {
                    this.spinRate *= 0.45F;
                    if (kick > 0.3) {
                        LocalFx.debrisImpact(this.level(), this.position(), this.state, kick, v);
                    }
                } else if (kick > 0.6) {
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

    /** How much of a hard landing comes back as a hop: timber springs a little, masonry thuds, wool doesn't at all. */
    private static double restitution(BlockState s) {
        float d = Destruction.density(s);
        if (d < 0.5F) {
            return 0.05;
        } else if (d < 1.0F) {
            return 0.3;
        } else {
            return d >= 3.0F ? 0.2 : 0.14;
        }
    }

    /**
     * Vanilla jumps a plain entity straight to each position the server sends. This block flies on its own copy of the
     * physics in between, so the two only drift a little apart: close the gap over three ticks instead.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        double dx = x - this.getX();
        double dy = y - this.getY();
        double dz = z - this.getZ();
        if (dx * dx + dy * dy + dz * dz > 4.0 || this.life < 2) {
            this.setPos(x, y, z);
            this.corrTicks = 0;
        } else {
            this.corrX = dx / 3.0;
            this.corrY = dy / 3.0;
            this.corrZ = dz / 3.0;
            this.corrTicks = 3;
        }
    }

    private void clientTick() {
        if (this.spinRate == 0.0F && this.life == 1) {
            // tumble end over end about the level axis across the flight, so it can come down flat on a face
            Vec3 dm = this.getDeltaMovement();
            boolean alongX = Math.abs(dm.x) > 1.0E-3 || Math.abs(dm.z) > 1.0E-3 ? Math.abs(dm.x) >= Math.abs(dm.z) : this.hash(9) < 0.5F;
            float sign = (alongX ? dm.x : dm.z) >= 0.0 ? 1.0F : -1.0F;
            this.axisX = alongX ? 0.0F : sign;
            this.axisY = 0.0F;
            this.axisZ = alongX ? -sign : 0.0F;
            this.spinRate = (float)(5.0 + 16.0 * (double)this.hash(11)) * (float)Math.min(1.0, dm.length() * 1.6 + 0.2);
        }

        this.spinO = this.spin;
        if (this.onGround()) {
            // settle onto the nearest face
            float target = (float)Math.round(this.spin / 90.0F) * 90.0F;
            this.spin = this.spin + (target - this.spin) * 0.4F;
            this.spinRate *= 0.5F;
        } else {
            this.spin = this.spin + this.spinRate;
        }

        if (this.spin >= 360.0F) {
            this.spin -= 360.0F;
            this.spinO -= 360.0F;
        }
    }

    private void impactEffects(double kick) {
        ServerLevel sl = (ServerLevel)this.level();
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
            // it breaks up where it lies: a scatter of its own chips and a little of its dust
            Destruction.crumble(sl, this.position().add(0.0, 0.4, 0.0), this.state, 6, 0.08F, 0.8F);
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
