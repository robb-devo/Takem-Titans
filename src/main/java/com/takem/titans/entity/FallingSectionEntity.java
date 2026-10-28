package com.takem.titans.entity;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.fx.LocalFx;
import com.takem.titans.network.SectionDetachPayload;
import com.takem.titans.odm.Grapple;
import com.takem.titans.odm.Tether;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Attractors;
import com.takem.titans.world.BoulderBarrage;
import com.takem.titans.world.Chunks;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.Rebuild;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public class FallingSectionEntity extends Entity implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Quaternionf> ROTATION = SynchedEntityData.defineId(FallingSectionEntity.class, EntityDataSerializers.QUATERNION);
    public static final double GRAVITY = 0.04;
    public static final int MAX_BLOCKS = 4000;
    private static final int MAX_LIFE = 280;
    private static int liveBlocks;
    public float[] ox = new float[0];
    public float[] oy = new float[0];
    public float[] oz = new float[0];
    public BlockState[] states = new BlockState[0];
    private boolean[] gone = new boolean[0];
    public float radius = 1.0F;
    private boolean hinged;
    private Vec3 hinge = Vec3.ZERO;
    private final Vector3f axis = new Vector3f();
    private Vec3 tip = Vec3.ZERO;
    private float angle;
    private float omega;
    private float inertia = 1.0F;
    private Vec3 startCentre = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private final Vector3f spin = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    @Nullable
    private BlockPos hingeBlock;
    private int life;
    private float massFactor = 1.0F;
    private int lastCrunch = -100;
    private int lastCollapse = -100;
    private int floorY = -2147483648;
    private int ghostTicks;
    private int generation;
    private int restTicks;
    /** Pieces broken from the same body share this, and ignore each other while they drift apart. */
    private int family;
    @Nullable
    private LongOpenHashSet cellCache;
    private int cellTick = -1;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private boolean lerping;
    public static final byte KIND_SECTION = 0;
    public static final byte KIND_BOULDER = 1;
    public byte kind = 0;
    /** Felled by a player's axe: timber lands as logs to pick up rather than splinters. */
    public boolean drops;
    /** Collapse generation this piece belongs to; what it knocks loose on landing is the next one (see Destruction.chainGen). */
    public int chainGen;
    @Nullable
    private UUID owner;
    public final Quaternionf rotO = new Quaternionf();
    public final Quaternionf rotR = new Quaternionf();
    public final Tether tether = new Tether();

    public FallingSectionEntity(EntityType<? extends FallingSectionEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public static boolean budgetFor(int blocks) {
        return liveBlocks + blocks <= 26000;
    }

    public static FallingSectionEntity create(
        ServerLevel level, long[] positions, BlockState[] blockStates, int count, @Nullable Vec3 hingeAt, @Nullable Vec3 tipDir, @Nullable BlockPos hingeBlock
    ) {
        FallingSectionEntity e = new FallingSectionEntity(ModEntities.FALLING_SECTION.get(), level);
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;

        for (int i = 0; i < count; i++) {
            sx += (double)BlockPos.getX(positions[i]);
            sy += (double)BlockPos.getY(positions[i]);
            sz += (double)BlockPos.getZ(positions[i]);
        }

        Vec3 cm = new Vec3(sx / (double)count + 0.5, sy / (double)count + 0.5, sz / (double)count + 0.5);
        int minY = 2147483647;

        for (int i = 0; i < count; i++) {
            minY = Math.min(minY, BlockPos.getY(positions[i]));
        }

        e.floorY = minY;
        e.ox = new float[count];
        e.oy = new float[count];
        e.oz = new float[count];
        e.states = new BlockState[count];
        e.gone = new boolean[count];
        float r = 0.0F;

        for (int i = 0; i < count; i++) {
            e.ox[i] = (float)((double)BlockPos.getX(positions[i]) + 0.5 - cm.x);
            e.oy[i] = (float)((double)BlockPos.getY(positions[i]) + 0.5 - cm.y);
            e.oz[i] = (float)((double)BlockPos.getZ(positions[i]) + 0.5 - cm.z);
            e.states[i] = blockStates[i];
            r = Math.max(r, e.ox[i] * e.ox[i] + e.oy[i] * e.oy[i] + e.oz[i] * e.oz[i]);
        }

        e.radius = Mth.sqrt(r) + 1.0F;
        e.setPos(cm.x, cm.y, cm.z);
        e.xo = cm.x;
        e.yo = cm.y;
        e.zo = cm.z;
        e.startCentre = cm;
        e.massFactor = (float)Math.min(10.0, 1.0 + Math.sqrt((double)count) / 2.0);
        RandomSource rnd = level.random;
        e.family = rnd.nextInt();
        if (hingeAt != null && tipDir != null) {
            e.hinged = true;
            e.hinge = hingeAt;
            e.hingeBlock = hingeBlock;
            Vec3 t = new Vec3(tipDir.x, 0.0, tipDir.z).normalize();
            e.tip = t;
            e.axis.set((float)t.z, 0.0F, (float)(-t.x)).normalize();
            double sum = 0.0;

            for (int i = 0; i < count; i++) {
                double wx = cm.x + (double)e.ox[i] - hingeAt.x;
                double wy = cm.y + (double)e.oy[i] - hingeAt.y;
                double wz = cm.z + (double)e.oz[i] - hingeAt.z;
                double along = wx * (double)e.axis.x + wy * (double)e.axis.y + wz * (double)e.axis.z;
                sum += wx * wx + wy * wy + wz * wz - along * along;
            }

            e.inertia = (float)(sum / (double)count + 0.16666666666666666);
            e.omega = 0.004F + rnd.nextFloat() * 0.004F;
        } else {
            float s = 0.002F + rnd.nextFloat() * 0.005F;
            float a = rnd.nextFloat() * 6.2831855F;
            e.spin.set(Mth.cos(a) * s, (rnd.nextFloat() - 0.5F) * 0.004F, Mth.sin(a) * s);
            e.velocity = new Vec3(0.0, -0.02, 0.0);
        }

        e.entityData.set(ROTATION, new Quaternionf());
        return e;
    }

    public FallingSectionEntity launch(Vec3 v, Vector3f tumble) {
        this.hinged = false;
        this.velocity = v;
        this.spin.set(tumble);
        this.ghostTicks = 6;
        return this;
    }

    @Override
    protected void defineSynchedData(Builder builder) {
        builder.define(ROTATION, new Quaternionf());
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            this.clientTick();
        } else if (this.states.length == 0) {
            this.discard();
        } else {
            this.life++;
            Vec3 prev = this.position();
            if (this.hinged && this.tether.active()) {
                this.release();
            }

            if (this.tether.thrown > 0) {
                this.tether.thrown--;
            }

            if (this.hinged) {
                Vec3 r = prev.subtract(this.hinge);
                double lever = r.x * this.tip.x + r.z * this.tip.z;
                float alpha = (float)(0.04 * Math.max(lever, 0.12) / (double)this.inertia);
                // keep the tip from whipping outer beams halfway across the district
                this.omega = Math.min(0.055F, (this.omega + alpha) * 0.998F);
                this.angle = this.angle + this.omega;
                this.rotation.identity().rotateAxis(this.angle, this.axis.x, this.axis.y, this.axis.z);
                Vector3f c = new Vector3f(
                    (float)(this.startCentre.x - this.hinge.x), (float)(this.startCentre.y - this.hinge.y), (float)(this.startCentre.z - this.hinge.z)
                );
                this.rotation.transform(c);
                this.setPos(this.hinge.x + (double)c.x, this.hinge.y + (double)c.y, this.hinge.z + (double)c.z);
                this.velocity = this.position().subtract(prev);
                if (this.angle > 1.2F || this.life % 4 == 0 && this.hingeLost()) {
                    this.release();
                }
            } else {
                this.velocity = this.velocity.add(0.0, -0.04, 0.0).scale(this.kind == 1 ? 1.0 : 0.996);
                Vec3 pull = Attractors.accel(this.level(), prev);
                if (pull != null) {
                    this.velocity = this.velocity.add(pull).scale(0.97);
                    this.spin.mul(1.01F);
                }

                if (this.tether.active()) {
                    this.velocity = Grapple.constrain(this, this.tether, prev, this.velocity, (double)this.radius);
                    if (this.tether.carried) {
                        this.spin.mul(0.85F);
                        this.ghostTicks = Math.max(this.ghostTicks, 1);
                    }
                }

                this.setPos(prev.add(this.velocity));
                float s = this.spin.length();
                if (s > 1.0E-5F) {
                    this.rotation.premul(new Quaternionf().rotateAxis(s, this.spin.x / s, this.spin.y / s, this.spin.z / s)).normalize();
                }

                this.spin.mul(0.997F);
            }

            this.entityData.set(ROTATION, new Quaternionf(this.rotation));
            if (this.kind == 1 && this.life % 20 == 1) {
                ChunkPos c = this.chunkPosition();
                ((ServerLevel)this.level()).getChunkSource().addRegionTicket(Chunks.TICKET, c, 2, c);
            }

            if (!Attractors.swallow(this)) {
                if (this.kind == 0 && !this.hinged && !this.tether.active()) {
                    this.collideSections((ServerLevel)this.level());
                }

                if (this.isRemoved()) {
                    return;
                }

                if (this.ghostTicks > 0) {
                    this.ghostTicks--;
                } else {
                    this.collide();
                }

                if (!this.isRemoved()) {
                    if ((this.life & 1) == 0) {
                        this.hurtEntities();
                    }

                    if (this.life > (this.kind == 1 ? 500 : 280) || this.getY() < (double)(this.level().getMinBuildHeight() - 16)) {
                        this.shatter(null);
                    }
                }
            }
        }
    }

    private boolean hingeLost() {
        return this.hingeBlock != null && this.level().getBlockState(this.hingeBlock).getCollisionShape(this.level(), this.hingeBlock).isEmpty();
    }

    private void release() {
        this.hinged = false;
        Vector3f w = new Vector3f(this.axis).mul(this.omega);
        this.spin.set(w);
        Vec3 r = this.position().subtract(this.hinge);
        this.velocity = clampMotion(
            new Vec3((double)w.y * r.z - (double)w.z * r.y, (double)w.z * r.x - (double)w.x * r.z, (double)w.x * r.y - (double)w.y * r.x)
        );
    }

    /** Outer beams used to inherit tip speed from a long hinge swing and sail across the street. */
    private static Vec3 clampMotion(Vec3 v) {
        double max = 0.85;
        double len = v.length();
        return len > max ? v.scale(max / len) : v;
    }

    private Vec3 pointVelocity(Vector3f o) {
        Vec3 raw;
        if (this.hinged) {
            double rx = this.getX() + (double)o.x - this.hinge.x;
            double ry = this.getY() + (double)o.y - this.hinge.y;
            double rz = this.getZ() + (double)o.z - this.hinge.z;
            double wx = (double)(this.axis.x * this.omega);
            double wy = (double)(this.axis.y * this.omega);
            double wz = (double)(this.axis.z * this.omega);
            raw = new Vec3(wy * rz - wz * ry, wz * rx - wx * rz, wx * ry - wy * rx);
        } else {
            raw = this.velocity
                .add(
                    (double)(this.spin.y * o.z - this.spin.z * o.y),
                    (double)(this.spin.z * o.x - this.spin.x * o.z),
                    (double)(this.spin.x * o.y - this.spin.y * o.x)
                );
        }
        return clampMotion(raw);
    }

    private void collide() {
        ServerLevel sl = (ServerLevel)this.level();
        RandomSource rnd = sl.random;
        int smashed = 0;
        int alive = 0;
        IntArrayList hard = new IntArrayList();
        IntArrayList detach = new IntArrayList();
        Vec3 hardAt = null;
        Vector3f o = new Vector3f();
        MutableBlockPos m = new MutableBlockPos();
        double cx = this.getX();
        double cy = this.getY();
        double cz = this.getZ();
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        BlockState struck = null;

        for (int i = 0; i < this.states.length; i++) {
            if (!this.gone[i]) {
                alive++;
                o.set(this.ox[i], this.oy[i], this.oz[i]);
                this.rotation.transform(o);
                double px = cx + (double)o.x;
                double py = cy + (double)o.y;
                double pz = cz + (double)o.z;
                m.set(Mth.floor(px), Mth.floor(py), Mth.floor(pz));
                BlockState s = sl.getBlockState(m);
                if (s.is(BlockTags.LEAVES) && this.kind == 0) {
                    // foliage in the way is torn through, not landed on
                    Destruction.shred(sl, m.immutable(), s);
                } else if (!s.isAir() && !s.getCollisionShape(sl, m).isEmpty()) {
                    if (this.kind == 1) {
                        BoulderBarrage.impact(this, new Vec3(px, py, pz), this.velocity);
                        return;
                    }

                    Vec3 u = this.pointVelocity(o);
                    double u2 = u.lengthSqr();
                    float h = s.getDestroySpeed(sl, m);
                    boolean ground = m.getY() < this.floorY && isGround(sl, m);
                    boolean thrown = this.tether.thrown > 0;
                    float punch = thrown ? this.massFactor * 1.6F * this.tether.power : this.massFactor;
                    if (!ground
                        && smashed < (thrown ? 320 : 180)
                        && h >= 0.0F
                        && h <= (thrown ? 9.0F : 6.0F)
                        && Destruction.isBreakable(sl, m, s)
                        && (double)punch * u2 >= (double)h * 0.38 + 0.025) {
                        // what the piece smashes through is carried on with it, spraying a little sideways
                        Destruction.smash(
                            sl,
                            m.immutable(),
                            s,
                            u.scale(0.55).add((rnd.nextDouble() - 0.5) * 0.12, 0.02 + rnd.nextDouble() * 0.08, (rnd.nextDouble() - 0.5) * 0.12),
                            0.2F,
                            u2 > 0.6 ? 1 : 0
                        );
                        if (struck == null) {
                            struck = s;
                        }

                        smashed++;
                        sumX += px;
                        sumY += py;
                        sumZ += pz;
                        double loss = 1.0 - Math.min(0.06, ((double)h * 0.35 + 0.15) / ((double)(punch * punch) * 2.2));
                        this.velocity = this.velocity.scale(loss);
                        this.omega *= (float)loss;
                        this.spin.mul((float)loss);
                        if (rnd.nextFloat() < 0.22F) {
                            detach.add(i);
                        }
                    } else {
                        hard.add(i);
                        if (hardAt == null) {
                            hardAt = new Vec3(px, py, pz);
                        }
                    }
                }
            }
        }

        if (alive == 0) {
            this.discard();
        } else {
            if (smashed > 0) {
                Vec3 at = new Vec3(sumX / (double)smashed, sumY / (double)smashed, sumZ / (double)smashed);
                if (this.life - this.lastCrunch > 5) {
                    this.lastCrunch = this.life;
                    Destruction.crunch(sl, at, Math.min(4.0F, 1.0F + (float)smashed / 12.0F));
                    BlockState chip = struck != null ? struck : this.material();
                    Destruction.crumble(sl, at, chip, Math.min(30, 6 + smashed * 2), (float)Math.min(0.45, 0.15 + this.velocity.length() * 0.3), 2.0F);
                    Destruction.dustCloud(sl, at, chip, Math.min(8.0F, 2.0F + (float)smashed / 6.0F), 1.0F);
                }

                if (this.life - this.lastCollapse > 6) {
                    this.lastCollapse = this.life;
                    Destruction.chainGen = this.chainGen;
                    Destruction.requestCollapse(sl, BlockPos.containing(at), Mth.clamp((int)this.radius + 6, 8, 28), 56, 6);
                    Destruction.chainGen = 0;
                }
            }

            if (hard.size() > Math.max(2, alive / 25)) {
                double impact = this.velocity.length() + (double)(this.spin.length() * this.radius);
                if (this.kind == 0 && impact < 0.5 && this.tether.thrown <= 0 && !this.hinged && this.life < 420) {
                    this.restOn(hardAt, hard.size(), alive);
                } else {
                    this.shatter(hardAt);
                }
            } else {
                if (!hard.isEmpty()) {
                    detach.addAll(hard);
                    this.velocity = this.velocity.scale(0.97);
                    this.spin.mul(0.95F);
                    this.omega *= 0.97F;
                }

                if (!detach.isEmpty()) {
                    this.detach(detach);
                }
            }
        }
    }

    /**
     * Falling pieces only touch where their blocks really overlap: a long beam's bounding sphere is mostly empty air,
     * and treating it as solid used to fling neighbouring pieces apart before they ever met. Where blocks do meet, the
     * pieces are eased apart over a few ticks and knock against each other softly - masonry hardly bounces. Pieces that
     * just broke off the same body get a moment to drift apart before they count as separate bodies at all.
     */
    private void collideSections(ServerLevel sl) {
        if ((this.life & 1) != 0 || this.ghostTicks > 0) {
            return;
        }

        for (FallingSectionEntity o : sl.getEntitiesOfClass(
            FallingSectionEntity.class,
            this.getBoundingBox().inflate((double)this.radius * 2.0 + 2.0),
            e -> e != this && e.getId() > this.getId() && e.kind == 0 && !e.hinged && !e.isRemoved()
        )) {
            if (o.ghostTicks > 0 || o.family == this.family && this.life < 24 && o.life < 24) {
                continue;
            }

            Vec3 d = o.position().subtract(this.position());
            double dist = d.length();
            if (dist > (double)(this.radius + o.radius) || dist < 0.001) {
                continue;
            }

            int aliveThis = this.aliveCount();
            int aliveOther = o.aliveCount();
            FallingSectionEntity big = aliveThis >= aliveOther ? this : o;
            FallingSectionEntity small = big == this ? o : this;
            LongOpenHashSet cells = big.cells();
            Vector3f p = new Vector3f();
            int hits = 0;
            double cx = 0.0;
            double cy = 0.0;
            double cz = 0.0;
            int step = small.states.length > 900 ? 3 : 1;

            for (int i = 0; i < small.states.length; i += step) {
                if (!small.gone[i]) {
                    p.set(small.ox[i], small.oy[i], small.oz[i]);
                    small.rotation.transform(p);
                    double wx = small.getX() + (double)p.x;
                    double wy = small.getY() + (double)p.y;
                    double wz = small.getZ() + (double)p.z;
                    if (cells.contains(BlockPos.asLong(Mth.floor(wx), Mth.floor(wy), Mth.floor(wz)))) {
                        hits++;
                        cx += wx;
                        cy += wy;
                        cz += wz;
                    }
                }
            }

            if (hits == 0) {
                continue;
            }

            Vec3 contact = new Vec3(cx / (double)hits, cy / (double)hits, cz / (double)hits);
            Vec3 n = d.scale(1.0 / dist);
            double m1 = (double)Math.max(1, aliveThis);
            double m2 = (double)Math.max(1, aliveOther);
            // ease the overlap out over a few ticks: a shove, never a teleport
            double push = Math.min(0.22, 0.035 * Math.sqrt((double)hits));
            this.setPos(this.position().subtract(n.scale(push * m2 / (m1 + m2))));
            o.setPos(o.position().add(n.scale(push * m1 / (m1 + m2))));
            Vec3 va = this.pointVelocity(contact.subtract(this.position()).toVector3f());
            Vec3 vb = o.pointVelocity(contact.subtract(o.position()).toVector3f());
            double closing = va.subtract(vb).dot(n);
            if (closing > 0.0) {
                // restitution 0.15: masonry and timber thud into each other, they don't bounce
                double j = 1.15 * closing / (1.0 / m1 + 1.0 / m2);
                this.velocity = this.velocity.subtract(n.scale(j / m1));
                o.velocity = o.velocity.add(n.scale(j / m2));
                Vec3 rel = va.subtract(vb);
                Vec3 tangent = rel.subtract(n.scale(rel.dot(n)));
                Vector3f twist = new Vector3f(
                    (float)(n.y * tangent.z - n.z * tangent.y), (float)(n.z * tangent.x - n.x * tangent.z), (float)(n.x * tangent.y - n.y * tangent.x)
                );
                this.spin.add(new Vector3f(twist).mul((float)(0.04 * m2 / (m1 + m2))));
                o.spin.sub(new Vector3f(twist).mul((float)(0.04 * m1 / (m1 + m2))));
                if (closing > 0.3) {
                    Destruction.crunch(sl, contact, (float)Math.min(5.0, 1.0 + closing * 3.0));
                    BlockState chip = small.material();
                    Destruction.crumble(sl, contact, chip, Math.min(24, 4 + hits), (float)Math.min(0.4, closing * 0.35), 1.5F);
                    Destruction.dustCloud(sl, contact, chip, (float)Math.min(5.0, 1.5 + closing * 3.0), 1.0F);
                }

                if (closing > 0.9) {
                    (m1 < m2 ? this : o).shatter(contact);
                    if (this.isRemoved()) {
                        return;
                    }
                }
            }
        }
    }

    /** The blocks this piece fills right now, as world block positions; worked out at most once a tick. */
    private LongOpenHashSet cells() {
        if (this.cellCache == null || this.cellTick != this.life) {
            LongOpenHashSet set = new LongOpenHashSet(this.states.length * 2);
            Vector3f p = new Vector3f();

            for (int i = 0; i < this.states.length; i++) {
                if (!this.gone[i]) {
                    p.set(this.ox[i], this.oy[i], this.oz[i]);
                    this.rotation.transform(p);
                    set.add(BlockPos.asLong(Mth.floor(this.getX() + (double)p.x), Mth.floor(this.getY() + (double)p.y), Mth.floor(this.getZ() + (double)p.z)));
                }
            }

            this.cellCache = set;
            this.cellTick = this.life;
        }

        return this.cellCache;
    }

    /** What this piece is mostly made of, for the colour of its dust and the look of its fragments. */
    public BlockState material() {
        BlockState best = null;
        int bestCount = 0;
        int n = this.states.length;
        int step = Math.max(1, n / 24);

        for (int i = 0; i < n; i += step) {
            BlockState s = this.states[i];
            if (s != null && (this.level().isClientSide || !this.gone[i])) {
                int c = 0;
                for (int j = 0; j < n; j += step) {
                    if (this.states[j] != null && this.states[j].getBlock() == s.getBlock()) {
                        c++;
                    }
                }

                if (c > bestCount) {
                    bestCount = c;
                    best = s;
                }
            }
        }

        return best != null ? best : Blocks.COBBLESTONE.defaultBlockState();
    }

    private void restOn(Vec3 contact, int touching, int alive) {
        Vec3 c = this.position();
        Vec3 r = contact.subtract(c);
        // lift it back by what gravity sank it this tick, no more: a bigger lift made resting pieces bob up and down
        this.setPos(c.x, c.y + Math.min(0.1, Math.max(0.0, -this.velocity.y) + 0.005), c.z);
        this.velocity = new Vec3(this.velocity.x * 0.55, Math.max(0.0, -this.velocity.y * 0.15), this.velocity.z * 0.55);
        float k = 0.01F / Math.max(1.0F, this.inertia);
        this.spin.mul(0.82F).add(new Vector3f((float)(-r.z) * k, 0.0F, (float)r.x * k));
        boolean flat = touching > Math.max(4, alive / 6);
        if (this.velocity.lengthSqr() < 0.005 && (double)this.spin.length() < 0.006 && ++this.restTicks > (flat ? 8 : 30)) {
            this.settle();
        }
    }

    private void settle() {
        ServerLevel sl = (ServerLevel)this.level();
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
        Vector3f o = new Vector3f();
        MutableBlockPos m = new MutableBlockPos();
        int placed = 0;

        for (int i = 0; i < this.states.length; i++) {
            if (!this.gone[i]) {
                o.set(this.ox[i], this.oy[i], this.oz[i]);
                this.rotation.transform(o);
                m.set(Mth.floor(this.getX() + (double)o.x), Mth.floor(this.getY() + (double)o.y + 0.5), Mth.floor(this.getZ() + (double)o.z));
                BlockState here = sl.getBlockState(m);
                if (!here.isAir() && !here.canBeReplaced()) {
                    Destruction.throwOrSettle(sl, Vec3.atCenterOf(m).add(0.0, 1.0, 0.0), this.states[i], Vec3.ZERO, rubble, 0);
                } else {
                    sl.setBlock(m, this.states[i], 3);
                    Rebuild.mark(sl, m);
                    placed++;
                }
            }
        }

        Vec3 at = this.position();
        BlockState mat = this.material();
        Destruction.crunch(sl, at, Math.min(5.0F, 1.0F + (float)placed / 60.0F));
        Destruction.dustCloud(sl, at, mat, Math.min(9.0F, this.radius * 1.2F + 1.0F), 1.2F);
        Destruction.crumble(sl, at, mat, Math.min(24, 4 + placed / 8), 0.1F, this.radius);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 2.0F, 0.5F);
        this.discard();
        // where it came to rest it may overhang a ledge: whatever sticks out unsupported breaks off next
        Destruction.chainGen = this.chainGen;
        Destruction.requestCollapse(sl, BlockPos.containing(at), Mth.clamp((int)this.radius + 6, 8, 24), 24, 6, this.drops, 4);
        Destruction.chainGen = 0;
    }

    private static boolean isGround(ServerLevel sl, MutableBlockPos m) {
        m.move(0, -1, 0);
        boolean solid = !sl.getBlockState(m).getCollisionShape(sl, m).isEmpty();
        m.move(0, 1, 0);
        return solid;
    }

    private void detach(IntArrayList idx) {
        ServerLevel sl = (ServerLevel)this.level();
        RandomSource rnd = sl.random;
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
        Vector3f o = new Vector3f();
        IntArrayList sent = new IntArrayList();

        for (int k = 0; k < idx.size(); k++) {
            int i = idx.getInt(k);
            if (!this.gone[i]) {
                this.gone[i] = true;
                sent.add(i);
                o.set(this.ox[i], this.oy[i], this.oz[i]);
                this.rotation.transform(o);
                Vec3 u = this.pointVelocity(o);
                Vec3 v = clampMotion(
                    u.scale(0.55).add((rnd.nextDouble() - 0.5) * 0.06, rnd.nextDouble() * 0.03, (rnd.nextDouble() - 0.5) * 0.06)
                );
                Destruction.throwOrSettle(sl, this.position().add((double)o.x, (double)o.y, (double)o.z), this.states[i], v, rubble, 0);
            }
        }

        if (!sent.isEmpty()) {
            PacketDistributor.sendToPlayersTrackingEntity(this, new SectionDetachPayload(this.getId(), sent.toIntArray()));
        }
    }

    private void hurtEntities() {
        AABB box = new AABB(
            this.getX() - (double)this.radius,
            this.getY() - (double)this.radius,
            this.getZ() - (double)this.radius,
            this.getX() + (double)this.radius,
            this.getY() + (double)this.radius,
            this.getZ() + (double)this.radius
        );
        Entity holderEntity = this.tether.active() ? this.level().getEntity(this.tether.holder) : null;
        Player thrower = this.tether.thrown > 0 && this.tether.thrower != null ? this.level().getPlayerByUUID(this.tether.thrower) : null;
        if (this.tether.thrown > 0 && this.kind == 0 && (this.life & 3) == 0) {
            this.hitTitanParts(box, thrower);
        }

        List<LivingEntity> victims = this.level()
            .getEntitiesOfClass(LivingEntity.class, box, ex -> ex.isAlive() && !ex.isSpectator() && ex != holderEntity && ex != thrower);
        if (!victims.isEmpty()) {
            Vector3f o = new Vector3f();

            for (LivingEntity e : victims) {
                AABB eb = e.getBoundingBox().inflate(0.45);

                for (int i = 0; i < this.states.length; i += this.states.length > 600 ? 2 : 1) {
                    if (!this.gone[i]) {
                        o.set(this.ox[i], this.oy[i], this.oz[i]);
                        this.rotation.transform(o);
                        if (eb.contains(this.getX() + (double)o.x, this.getY() + (double)o.y, this.getZ() + (double)o.z)) {
                            Vec3 u = this.pointVelocity(o);
                            double sp = u.length();
                            if (!(sp < 0.12)) {
                                if (this.kind == 1) {
                                    BoulderBarrage.impact(this, e.position(), this.velocity);
                                    return;
                                }

                                float dmg = (float)Math.min(60.0, (double)this.massFactor * sp * 9.0);
                                if (thrower != null) {
                                    dmg = Math.min(400.0F, dmg * 3.0F * this.tether.power);
                                }

                                if (e instanceof AbstractTitan) {
                                    dmg *= 0.6F;
                                }

                                DamageSource hitSrc = thrower != null ? this.damageSources().thrown(this, thrower) : this.damageSources().fallingBlock(this);
                                if (e.hurt(hitSrc, dmg)) {
                                    e.push(u.x * 0.6, Math.max(0.1, u.y * 0.3), u.z * 0.6);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void shatter(@Nullable Vec3 at) {
        ServerLevel sl = (ServerLevel)this.level();
        RandomSource rnd = sl.random;
        if (at == null || this.kind != 0 || !this.fracture(at)) {
            float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
            Vector3f o = new Vector3f();
            double minY = 1.7976931348623157E308;
            double speed = 0.0;
            BlockState mat = this.material();
            int count = 0;

            for (int i = 0; i < this.states.length; i++) {
                if (!this.gone[i]) {
                    count++;
                    o.set(this.ox[i], this.oy[i], this.oz[i]);
                    this.rotation.transform(o);
                    Vec3 p = this.position().add((double)o.x, (double)o.y, (double)o.z);
                    Vec3 u = this.pointVelocity(o);
                    speed = Math.max(speed, u.length());
                    // keep rubble close: a little outwards scatter, no beam-flinging across the district
                    double kick = Math.min(0.14, u.length() * 0.18);
                    Vec3 v = clampMotion(
                        u.scale(0.55).add((rnd.nextDouble() - 0.5) * kick, rnd.nextDouble() * kick * 0.25, (rnd.nextDouble() - 0.5) * kick)
                    );
                    if (this.drops && this.states[i].is(BlockTags.LOGS)) {
                        Block.dropResources(this.states[i], sl, BlockPos.containing(p));
                    } else {
                        Destruction.throwOrSettle(sl, p, this.states[i], v, rubble, u.lengthSqr() > 0.5 ? 1 : 0);
                    }
                    minY = Math.min(minY, p.y);
                }
            }

            Vec3 hit = at != null ? at : this.position();
            float size = this.radius * 2.0F;
            Destruction.crunch(sl, hit, Math.min(6.0F, 1.5F + (float)this.states.length / 80.0F));
            sl.playSound(
                null,
                hit.x,
                hit.y,
                hit.z,
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS,
                Math.min(8.0F, 2.0F + (float)this.states.length / 100.0F),
                0.35F
            );
            // fragments, the billow, the surge along the street and the pall: all from this one packet
            Fx.send(sl, Fx.COLLAPSE, hit.x, Math.min(hit.y, minY + 0.5), hit.z, size, (float)speed, (float)Block.getId(mat), (float)count, 320.0);
            if (this.states.length > 120) {
                sl.playSound(
                    null,
                    hit.x,
                    hit.y,
                    hit.z,
                    ModSounds.BUILDING_COLLAPSE.get(),
                    SoundSource.BLOCKS,
                    Math.min(10.0F, 3.0F + (float)this.states.length / 150.0F),
                    0.7F + rnd.nextFloat() * 0.3F
                );
                if (this.states.length > 400) {
                    // the big ones land with a deep, long rumble under the crash
                    sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 10.0F, 0.2F);
                    sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 6.0F, 0.5F);
                }
                Destruction.glassBurst(sl, hit, Math.min(30.0, 8.0 + (double)this.states.length / 40.0));
            }

            AbstractTitan.shakeNearby(sl, hit, 40.0 + (double)size, Math.min(1.0F, 0.25F + (float)this.states.length / 900.0F), 22);
            Destruction.chainGen = this.chainGen;
            Destruction.requestCollapse(sl, BlockPos.containing(hit), Mth.clamp((int)this.radius + 6, 10, 24), 56, 6);
            Destruction.chainGen = 0;
            this.discard();
        }
    }

    private boolean fracture(Vec3 hit) {
        ServerLevel sl = (ServerLevel)this.level();
        RandomSource rnd = sl.random;
        int alive = 0;

        for (boolean g : this.gone) {
            if (!g) {
                alive++;
            }
        }

        if (this.generation < 2 && alive >= 40) {
            BlockState mat = this.material();
            int k = Mth.clamp(alive / 35, 2, 7);
            int[] idx = new int[alive];
            float[] wx = new float[alive];
            float[] wy = new float[alive];
            float[] wz = new float[alive];
            Vector3f o = new Vector3f();
            int n = 0;

            for (int i = 0; i < this.states.length; i++) {
                if (!this.gone[i]) {
                    o.set(this.ox[i], this.oy[i], this.oz[i]);
                    this.rotation.transform(o);
                    idx[n] = i;
                    wx[n] = o.x;
                    wy[n] = o.y;
                    wz[n] = o.z;
                    n++;
                }
            }

            float[][] seed = new float[k][];

            for (int j = 0; j < k; j++) {
                int pick = rnd.nextInt(n);
                seed[j] = new float[]{wx[pick], wy[pick], wz[pick]};
            }

            int[] group = new int[n];
            int[] size = new int[k];

            for (int a = 0; a < n; a++) {
                int best = 0;
                float bd = 3.4028235E38F;

                for (int j = 0; j < k; j++) {
                    float dx = wx[a] - seed[j][0];
                    float dy = wy[a] - seed[j][1];
                    float dz = wz[a] - seed[j][2];
                    float d = dx * dx + dy * dy + dz * dz + rnd.nextFloat() * 1.5F;
                    if (d < bd) {
                        bd = d;
                        best = j;
                    }
                }

                group[a] = best;
                size[best]++;
            }

            float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
            Vec3 c = this.position();

            for (int jx = 0; jx < k; jx++) {
                if (size[jx] != 0) {
                    float sx = 0.0F;
                    float sy = 0.0F;
                    float sz = 0.0F;

                    for (int a = 0; a < n; a++) {
                        if (group[a] == jx) {
                            sx += wx[a];
                            sy += wy[a];
                            sz += wz[a];
                        }
                    }

                    Vector3f cm = new Vector3f(sx / (float)size[jx], sy / (float)size[jx], sz / (float)size[jx]);
                    Vec3 u = this.pointVelocity(cm);
                    Vec3 away = new Vec3(c.x + (double)cm.x - hit.x, 0.0, c.z + (double)cm.z - hit.z);
                    away = away.lengthSqr() < 1.0E-4 ? new Vec3(rnd.nextDouble() - 0.5, 0.0, rnd.nextDouble() - 0.5).normalize() : away.normalize();
                    double speed = u.length();
                    // fractured chunks peel off nearby — not launch like rockets
                    Vec3 v = clampMotion(
                        new Vec3(u.x * 0.45, Math.min(0.18, Math.abs(u.y) * 0.08 + 0.03 + rnd.nextDouble() * 0.04), u.z * 0.45)
                            .add(away.scale(0.04 + Math.min(0.12, speed * 0.12) + rnd.nextDouble() * 0.03))
                    );
                    if (size[jx] >= 12 && budgetFor(size[jx])) {
                        FallingSectionEntity piece = new FallingSectionEntity(ModEntities.FALLING_SECTION.get(), sl);
                        int m = size[jx];
                        piece.ox = new float[m];
                        piece.oy = new float[m];
                        piece.oz = new float[m];
                        piece.states = new BlockState[m];
                        piece.gone = new boolean[m];
                        float r = 0.0F;
                        int q = 0;

                        for (int ax = 0; ax < n; ax++) {
                            if (group[ax] == jx) {
                                piece.ox[q] = wx[ax] - cm.x;
                                piece.oy[q] = wy[ax] - cm.y;
                                piece.oz[q] = wz[ax] - cm.z;
                                piece.states[q] = this.states[idx[ax]];
                                r = Math.max(r, piece.ox[q] * piece.ox[q] + piece.oy[q] * piece.oy[q] + piece.oz[q] * piece.oz[q]);
                                q++;
                            }
                        }

                        piece.radius = Mth.sqrt(r) + 1.0F;
                        Vec3 pos = c.add((double)cm.x, (double)cm.y, (double)cm.z);
                        piece.setPos(pos.x, pos.y, pos.z);
                        piece.xo = pos.x;
                        piece.yo = pos.y;
                        piece.zo = pos.z;
                        piece.startCentre = pos;
                        piece.floorY = this.floorY;
                        piece.massFactor = (float)Math.min(10.0, 1.0 + Math.sqrt((double)m) / 2.0);
                        piece.drops = this.drops;
                        piece.chainGen = this.chainGen;
                        piece.generation = this.generation + 1;
                        piece.family = this.family;
                        piece.entityData.set(ROTATION, new Quaternionf());
                        piece.launch(
                            v,
                            new Vector3f(this.spin).add((rnd.nextFloat() - 0.5F) * 0.06F, (rnd.nextFloat() - 0.5F) * 0.03F, (rnd.nextFloat() - 0.5F) * 0.06F)
                        );
                        piece.ghostTicks = 3;
                        sl.addFreshEntity(piece);
                    } else {
                        for (int axx = 0; axx < n; axx++) {
                            if (group[axx] == jx) {
                                Vec3 p = c.add((double)wx[axx], (double)wy[axx], (double)wz[axx]);
                                Destruction.throwOrSettle(
                                    sl,
                                    p,
                                    this.states[idx[axx]],
                                    clampMotion(
                                        v.add((rnd.nextDouble() - 0.5) * 0.08, rnd.nextDouble() * 0.04, (rnd.nextDouble() - 0.5) * 0.08)
                                    ),
                                    rubble,
                                    0
                                );
                            }
                        }
                    }
                }
            }

            float sz = this.radius * 2.0F;
            Destruction.crunch(sl, hit, Math.min(6.0F, 1.5F + (float)alive / 80.0F));
            sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, Math.min(8.0F, 2.0F + (float)alive / 100.0F), 0.4F);
            Fx.send(sl, Fx.COLLAPSE, hit.x, hit.y, hit.z, sz, (float)this.velocity.length(), (float)Block.getId(mat), (float)alive, 320.0);
            if (alive > 120) {
                sl.playSound(
                    null,
                    hit.x,
                    hit.y,
                    hit.z,
                    ModSounds.BUILDING_COLLAPSE.get(),
                    SoundSource.BLOCKS,
                    Math.min(10.0F, 3.0F + (float)alive / 150.0F),
                    0.7F + rnd.nextFloat() * 0.3F
                );
                if (alive > 400) {
                    // the big ones land with a deep, long rumble under the crash
                    sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 10.0F, 0.2F);
                    sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 6.0F, 0.5F);
                }
                Destruction.glassBurst(sl, hit, Math.min(30.0, 8.0 + (double)alive / 40.0));
            }

            AbstractTitan.shakeNearby(sl, hit, 40.0 + (double)sz, Math.min(1.0F, 0.25F + (float)alive / 900.0F), 22);
            Destruction.chainGen = this.chainGen;
            Destruction.requestCollapse(sl, BlockPos.containing(hit), Mth.clamp((int)this.radius + 6, 10, 24), 56, 6);
            Destruction.chainGen = 0;
            this.discard();
            return true;
        } else {
            return false;
        }
    }

    private void hitTitanParts(AABB box, @Nullable Player thrower) {
        double sp = this.velocity.length();
        if (!(sp < 0.5)) {
            Vector3f o = new Vector3f();
            Set<AbstractTitan> struck = new HashSet<>();

            for (TitanPart part : this.level().getEntitiesOfClass(TitanPart.class, box.inflate(1.0), px -> px.active)) {
                AbstractTitan titan = part.getParent();
                if (!struck.contains(titan) && !titan.isDeadOrDying()) {
                    AABB pb = part.getBoundingBox().inflate(0.6);
                    boolean touching = false;

                    for (int i = 0; i < this.states.length && !touching; i += this.states.length > 400 ? 3 : 1) {
                        if (!this.gone[i]) {
                            o.set(this.ox[i], this.oy[i], this.oz[i]);
                            this.rotation.transform(o);
                            touching = pb.contains(this.getX() + (double)o.x, this.getY() + (double)o.y, this.getZ() + (double)o.z);
                        }
                    }

                    if (touching) {
                        struck.add(titan);
                        float power = (float)Math.min(2000.0, (double)this.massFactor * sp * sp * 6.0 * (double)this.tether.power);
                        DamageSource src = thrower != null ? this.damageSources().thrown(this, thrower) : this.damageSources().fallingBlock(this);
                        titan.impactPart(part, power, thrower, src);
                        ServerLevel sl = (ServerLevel)this.level();
                        Vec3 at = part.position().add(0.0, (double)part.getBbHeight() * 0.5, 0.0);
                        Fx.send(sl, 24, at, Math.min(6.0F, 1.5F + power / 80.0F), 0.0F, 160.0);
                        Destruction.crunch(sl, at, Math.min(5.0F, 1.5F + power / 120.0F));
                        this.velocity = this.velocity.scale(0.55);
                        this.spin.mul(1.4F);
                    }
                }
            }
        }
    }

    public int aliveCount() {
        int n = 0;
        if (this.level().isClientSide) {
            for (BlockState s : this.states) {
                if (s != null) {
                    n++;
                }
            }
        } else {
            for (boolean g : this.gone) {
                if (!g) {
                    n++;
                }
            }
        }

        return n;
    }

    public boolean isHinged() {
        return this.hinged;
    }

    public void shove(Vec3 dv) {
        if (!this.level().isClientSide) {
            if (this.hinged) {
                this.release();
            }

            this.velocity = this.velocity.add(dv);
            if (dv.lengthSqr() > 0.01) {
                this.ghostTicks = Math.max(this.ghostTicks, 2);
                float s = (float)Math.min(0.25, dv.length() * 0.08);
                this.spin.add((this.random.nextFloat() - 0.5F) * s, (this.random.nextFloat() - 0.5F) * s * 0.5F, (this.random.nextFloat() - 0.5F) * s);
            }
        }
    }

    private Quaternionf currentRotation(float pt) {
        if (this.level().isClientSide) {
            return pt >= 1.0F ? new Quaternionf(this.rotR) : new Quaternionf(this.rotO).slerp(this.rotR, pt);
        } else {
            return new Quaternionf(this.rotation);
        }
    }

    public Vec3 localToWorld(Vec3 local, float pt) {
        Vector3f v = new Vector3f((float)local.x, (float)local.y, (float)local.z);
        this.currentRotation(pt).transform(v);
        Vec3 base = this.getPosition(pt);
        return base.add((double)v.x, (double)v.y, (double)v.z);
    }

    public Vec3 worldToLocal(Vec3 world) {
        Vector3f v = new Vector3f((float)(world.x - this.getX()), (float)(world.y - this.getY()), (float)(world.z - this.getZ()));
        this.currentRotation(1.0F).conjugate().transform(v);
        return new Vec3((double)v.x, (double)v.y, (double)v.z);
    }

    @Nullable
    public Vec3 clip(Vec3 from, Vec3 to) {
        Vec3 c = this.position();
        Vec3 seg = to.subtract(from);
        double len2 = seg.lengthSqr();
        double t = len2 < 1.0E-8 ? 0.0 : Mth.clamp(c.subtract(from).dot(seg) / len2, 0.0, 1.0);
        if (from.add(seg.scale(t)).distanceToSqr(c) > (double)((this.radius + 1.0F) * (this.radius + 1.0F))) {
            return null;
        } else {
            Vec3 la = this.worldToLocal(from);
            Vec3 lb = this.worldToLocal(to);
            double best = 1.7976931348623157E308;
            Vec3 hit = null;

            for (int i = 0; i < this.states.length; i++) {
                if (this.states[i] != null && (this.level().isClientSide || !this.gone[i])) {
                    AABB box = new AABB(
                        (double)this.ox[i] - 0.5,
                        (double)this.oy[i] - 0.5,
                        (double)this.oz[i] - 0.5,
                        (double)this.ox[i] + 0.5,
                        (double)this.oy[i] + 0.5,
                        (double)this.oz[i] + 0.5
                    );
                    Optional<Vec3> h = box.clip(la, lb);
                    if (h.isPresent()) {
                        double d = h.get().distanceToSqr(la);
                        if (d < best) {
                            best = d;
                            hit = h.get();
                        }
                    }
                }
            }

            return hit == null ? null : this.localToWorld(hit, 1.0F);
        }
    }

    public static FallingSectionEntity boulder(ServerLevel level, Vec3 at, float rad, @Nullable UUID owner) {
        RandomSource rnd = level.random;
        int R = Mth.ceil(rad);
        List<float[]> pts = new ArrayList<>();

        for (int x = -R; x <= R; x++) {
            for (int y = -R; y <= R; y++) {
                for (int z = -R; z <= R; z++) {
                    if (!((float)(x * x + y * y + z * z) > rad * rad + rnd.nextFloat() * 1.5F)) {
                        pts.add(new float[]{(float)x, (float)y, (float)z});
                    }
                }
            }
        }

        BlockState[] rocks = new BlockState[]{
            Blocks.STONE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            Blocks.TUFF.defaultBlockState(),
            Blocks.MAGMA_BLOCK.defaultBlockState()
        };
        FallingSectionEntity e = new FallingSectionEntity(ModEntities.FALLING_SECTION.get(), level);
        int m = pts.size();
        e.ox = new float[m];
        e.oy = new float[m];
        e.oz = new float[m];
        e.states = new BlockState[m];
        e.gone = new boolean[m];

        for (int i = 0; i < m; i++) {
            float[] p = pts.get(i);
            e.ox[i] = p[0];
            e.oy[i] = p[1];
            e.oz[i] = p[2];
            e.states[i] = rocks[rnd.nextInt(10) < 8 ? rnd.nextInt(4) : 4];
        }

        e.radius = rad + 1.0F;
        e.setPos(at.x, at.y, at.z);
        e.xo = at.x;
        e.yo = at.y;
        e.zo = at.z;
        e.startCentre = at;
        e.massFactor = (float)Math.min(10.0, 1.0 + Math.sqrt((double)m) / 2.0);
        e.kind = 1;
        e.owner = owner;
        e.entityData.set(ROTATION, new Quaternionf());
        return e;
    }

    /**
     * The War Hammer's hammer: a crystal head with a haft standing up out of it, dropped as a boulder so it lands like one
     * (it bursts and throws its crystal about) instead of settling into the street.
     */
    public static FallingSectionEntity hammer(ServerLevel level, Vec3 at, BlockState[] crystal, @Nullable UUID owner) {
        RandomSource rnd = level.random;
        List<float[]> pts = new ArrayList<>();

        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) < 4 || (Math.abs(y + 0.5F) < 1.5F && Math.abs(z) < 2)) {
                        pts.add(new float[]{(float)x, (float)y, (float)z});
                    }
                }
            }
        }

        for (int y = 2; y <= 9; y++) {
            pts.add(new float[]{0.0F, (float)y, 0.0F});
        }

        FallingSectionEntity e = new FallingSectionEntity(ModEntities.FALLING_SECTION.get(), level);
        int m = pts.size();
        e.ox = new float[m];
        e.oy = new float[m];
        e.oz = new float[m];
        e.states = new BlockState[m];
        e.gone = new boolean[m];

        for (int i = 0; i < m; i++) {
            float[] p = pts.get(i);
            e.ox[i] = p[0];
            e.oy[i] = p[1];
            e.oz[i] = p[2];
            e.states[i] = crystal[rnd.nextInt(crystal.length)];
        }

        e.radius = 4.5F;
        e.setPos(at.x, at.y, at.z);
        e.xo = at.x;
        e.yo = at.y;
        e.zo = at.z;
        e.startCentre = at;
        e.massFactor = 10.0F;
        e.kind = 1;
        e.owner = owner;
        e.entityData.set(ROTATION, new Quaternionf());
        return e;
    }

    @Nullable
    public Entity owner() {
        return this.owner != null && this.level() instanceof ServerLevel sl ? sl.getEntity(this.owner) : null;
    }

    public Vec3 velocity() {
        return this.velocity;
    }

    public List<BlockState> takeBlocks(List<Vec3> where) {
        List<BlockState> out = new ArrayList<>();
        Vector3f o = new Vector3f();

        for (int i = 0; i < this.states.length; i++) {
            if (!this.gone[i]) {
                this.gone[i] = true;
                o.set(this.ox[i], this.oy[i], this.oz[i]);
                this.rotation.transform(o);
                if (where != null) {
                    where.add(this.position().add((double)o.x, (double)o.y, (double)o.z));
                }

                out.add(this.states[i]);
            }
        }

        this.discard();
        return out;
    }

    /**
     * Vanilla puts a plain entity at each new position the moment the packet arrives, while the rotation here eases a
     * tick behind - so a toppling wall stepped at 20 Hz and slid against its own spin. Hold the position back to the
     * next tick instead, where it moves in step with the rotation and is drawn smoothly in between.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerping = true;
    }

    @Override
    public double lerpTargetX() {
        return this.lerping ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerping ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerping ? this.lerpZ : this.getZ();
    }

    private void clientTick() {
        this.rotO.set(this.rotR);
        this.rotR.set((Quaternionfc)this.entityData.get(ROTATION));
        if (this.tickCount <= 1) {
            this.rotO.set(this.rotR);
        }

        if (this.lerping) {
            this.lerping = false;
            this.setPos(this.lerpX, this.lerpY, this.lerpZ);
        }

        if (this.kind == 1) {
            double vx = this.getX() - this.xo;
            double vy = this.getY() - this.yo;
            double vz = this.getZ() - this.zo;

            for (int k = 0; k < 3; k++) {
                double f = this.random.nextDouble();
                double px = this.getX() - vx * f + (this.random.nextDouble() - 0.5) * (double)this.radius;
                double py = this.getY() - vy * f + (this.random.nextDouble() - 0.5) * (double)this.radius;
                double pz = this.getZ() - vz * f + (this.random.nextDouble() - 0.5) * (double)this.radius;
                this.level().addAlwaysVisibleParticle(ModParticles.FIREBALL.get(), true, px, py, pz, 0.0, 0.02, 0.0);
            }

            if (this.tickCount % 3 == 0) {
                this.level().addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, this.getX() - vx, this.getY() - vy, this.getZ() - vz, 0.0, 0.05, 0.0);
            }
            this.level()
                .addAlwaysVisibleParticle(
                    ModParticles.EMBER.get(),
                    true,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.3,
                    0.1,
                    (this.random.nextDouble() - 0.5) * 0.3
                );
        } else if (this.states.length > 0) {
            // the broken faces shed chips and dust on the way down, more the faster it goes
            Vec3 v = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
            double speed = v.length();
            if (speed > 0.03 || this.tickCount < 6) {
                Vector3f o = new Vector3f();
                int bits = Math.min(4, 1 + this.states.length / 250);
                float dust = (float)Mth.clamp(speed * 1.2, 0.08, 0.6);

                for (int k = 0; k < bits; k++) {
                    int i = this.random.nextInt(this.states.length);
                    if (this.states[i] != null) {
                        o.set(this.ox[i], this.oy[i], this.oz[i]);
                        this.rotR.transform(o);
                        LocalFx.crumbleOff(
                            this.level(), new Vec3(this.getX() + (double)o.x, this.getY() + (double)o.y, this.getZ() + (double)o.z), this.states[i], v, dust
                        );
                    }
                }
            }
        }
    }

    public void clientDetach(int[] idx) {
        for (int i : idx) {
            if (i >= 0 && i < this.states.length) {
                this.states[i] = null;
            }
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!this.level().isClientSide) {
            liveBlocks = liveBlocks + this.states.length;
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!this.level().isClientSide) {
            liveBlocks = Math.max(0, liveBlocks - this.states.length);
        }
    }

    public static void resetBudget() {
        liveBlocks = 0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(
            this.getX() - (double)this.radius,
            this.getY() - (double)this.radius,
            this.getZ() - (double)this.radius,
            this.getX() + (double)this.radius,
            this.getY() + (double)this.radius,
            this.getZ() + (double)this.radius
        );
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 102400.0;
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
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.states.length);
        buf.writeFloat(this.radius);
        buf.writeByte(this.kind);

        for (int i = 0; i < this.states.length; i++) {
            buf.writeFloat(this.ox[i]);
            buf.writeFloat(this.oy[i]);
            buf.writeFloat(this.oz[i]);
            buf.writeVarInt(Block.getId(this.states[i]));
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        this.radius = buf.readFloat();
        this.kind = buf.readByte();
        this.ox = new float[n];
        this.oy = new float[n];
        this.oz = new float[n];
        this.states = new BlockState[n];
        this.gone = new boolean[n];

        for (int i = 0; i < n; i++) {
            this.ox[i] = buf.readFloat();
            this.oy[i] = buf.readFloat();
            this.oz[i] = buf.readFloat();
            this.states[i] = Block.stateById(buf.readVarInt());
        }
    }
}
