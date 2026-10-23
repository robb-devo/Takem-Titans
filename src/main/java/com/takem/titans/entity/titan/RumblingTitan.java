package com.takem.titans.entity.titan;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModBlocks;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Chunks;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.Rebuild;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class RumblingTitan extends ColossalTitan {
    public static final int RISE_TICKS = 110;
    public static final double SPEED = 0.42;
    private static final Vector3f FOOT = new Vector3f(0.0F, 9.5F, -2.0F);
    private static final int UPDATE = 50;
    private static final int THROWN_PER_TICK = 70;
    private static final int CLEARED_PER_TICK = 1600;
    private int phase;
    private int phaseTicks;
    private int marchLeft = 900;
    private double riseFrom;
    private double riseTo;
    private Vec3 dir = new Vec3(0.0, 0.0, 1.0);
    private boolean breaks = true;
    private int smashed;
    private int lastCrunch;

    public RumblingTitan(EntityType<? extends RumblingTitan> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(Vec3 direction, int marchTicks, boolean breaksBlocks) {
        this.dir = new Vec3(direction.x, 0.0, direction.z).normalize();
        this.marchLeft = marchTicks;
        this.breaks = breaksBlocks;
        this.riseTo = this.getY();
        this.riseFrom = this.getY() - (double)this.getTitanHeight() * 0.9;
        this.setPos(this.getX(), this.riseFrom, this.getZ());
        float yaw = yawOf(this.dir);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    private static float yawOf(Vec3 d) {
        return (float)(Mth.atan2(-d.x, d.z) * 180.0 / 3.1415927410125732);
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 54.0F + random.nextFloat() * 14.0F;
    }

    @Override
    public int variantCount() {
        return 3;
    }

    public boolean isMarching() {
        return this.phase == 1;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.discard();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void vaporise(DamageSource source) {
    }

    @Override
    public void blastKnockDown(int ticks) {
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    protected float walkTarget(float moved, float stride) {
        return this.phase == 1 ? 1.0F : super.walkTarget(moved, stride);
    }

    @Override
    public void travel(Vec3 input) {
        this.updateWalk();
    }

    @Override
    protected void customServerAiStep() {
        if (this.level() instanceof ServerLevel level) {
            this.phaseTicks++;
            float var4 = yawOf(this.dir);
            this.setYRot(var4);
            this.yBodyRot = var4;
            this.yHeadRot = var4;
            this.setXRot(0.0F);
            this.setDeltaMovement(Vec3.ZERO);
            if (this.tickCount % 20 == 0) {
                ChunkPos c = this.chunkPosition();
                level.getChunkSource().addRegionTicket(Chunks.TICKET, c, 3, c);
            }

            if (this.phase == 0) {
                this.rise(level);
            } else {
                this.march(level);
            }

            if (this.tickCount % 14 == 0) {
                Vec3 c = this.skeletonPoint(2, TitanSkeleton.TORSO_CENTER);
                Destruction.far(
                    level,
                    ModParticles.BIG_STEAM.get(),
                    c.x,
                    c.y,
                    c.z,
                    6,
                    (double)this.getBbWidth() * 0.5,
                    (double)this.getTitanHeight() * 0.15,
                    (double)this.getBbWidth() * 0.5,
                    0.08,
                    400.0
                );
            }
        }
    }

    private void rise(ServerLevel level) {
        RandomSource rnd = this.random;
        double k = Math.min(1.0, (double)this.phaseTicks / 110.0);
        double e = 1.0 - Math.pow(1.0 - k, 2.0);
        this.setPos(this.getX(), Mth.lerp(e, this.riseFrom, this.riseTo), this.getZ());
        Vec3 base = new Vec3(this.getX(), this.riseTo, this.getZ());
        if (this.phaseTicks == 1) {
            level.playSound(null, base.x, base.y, base.z, ModSounds.RUMBLING_EMERGE.get(), SoundSource.HOSTILE, 14.0F, 0.85F + rnd.nextFloat() * 0.2F);
        }

        if (this.phaseTicks % 6 == 0) {
            Fx.send(level, 16, base, this.getBbWidth() * 1.3F, (float)k, 500.0);
        }

        if (this.phaseTicks % 10 == 0) {
            AbstractTitan.shakeNearby(level, base, 220.0, 0.45F, 14);
        }

        if (this.breaks && this.phaseTicks % 2 == 0) {
            this.burstGround(level, base, k);
        }

        if (k > 0.15 && this.phaseTicks % 2 == 0) {
            BlockState rock = rnd.nextInt(3) == 0
                ? ModBlocks.WALL_STONE.get().defaultBlockState()
                : (rnd.nextBoolean() ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState());
            Vec3 from = this.skeletonPoint(rnd.nextBoolean() ? 5 : 8, new Vector3f(0.0F, 2.0F, 0.0F));
            if (from.y > this.riseTo + 2.0) {
                Vec3 out = from.subtract(this.getX(), from.y, this.getZ()).normalize();
                Destruction.throwOrSettle(level, from, rock, out.scale(0.3 + rnd.nextDouble() * 0.4).add(0.0, 0.1, 0.0), 0.3F, 2);
            }
        }

        if (this.phaseTicks >= 110) {
            this.phase = 1;
            this.phaseTicks = 0;
            level.playSound(
                null, this.getX(), this.getEyeY(), this.getZ(), ModSounds.TITAN_ROAR.get(), SoundSource.HOSTILE, 12.0F, 0.42F + rnd.nextFloat() * 0.08F
            );
        }
    }

    private void burstGround(ServerLevel level, Vec3 base, double k) {
        RandomSource rnd = this.random;
        double r = (double)this.getBbWidth() * 0.5 + 2.0 + k * 2.0;
        int R = Mth.ceil(r);
        int top = Mth.floor(base.y) + 2;
        int bottom = Mth.floor(base.y) - 4;
        int thrown = 0;
        int cleared = 0;
        MutableBlockPos m = new MutableBlockPos();

        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt((double)(dx * dx + dz * dz));
                if (!(d > r) && !(rnd.nextFloat() < 0.4F)) {
                    for (int y = bottom; y <= top && cleared < 240; y++) {
                        m.set(Mth.floor(base.x) + dx, y, Mth.floor(base.z) + dz);
                        BlockState s = level.getBlockState(m);
                        if (!s.isAir() && s.getFluidState().isEmpty() && !(s.getDestroySpeed(level, m) < 0.0F)) {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                            Rebuild.mark(level, m);
                            cleared++;
                            if (thrown < 26 && rnd.nextFloat() < 0.35F && !s.getCollisionShape(level, m).isEmpty()) {
                                Vec3 out = new Vec3((double)dx, 0.0, (double)dz).normalize();
                                Vec3 v = out.scale(0.3 + rnd.nextDouble() * 0.6).add(0.0, 0.7 + rnd.nextDouble() * 1.1, 0.0);
                                Destruction.throwOrSettle(level, Vec3.atCenterOf(m), s, v, 0.35F, 1);
                                thrown++;
                            }
                        }
                    }
                }
            }
        }

        if (this.phaseTicks % 12 == 0) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            BlockPos at = BlockPos.containing(base.x + Math.cos(a) * (r + 1.5), base.y - 1.0, base.z + Math.sin(a) * (r + 1.5));
            FallingSectionEntity lump = Chunks.rip(level, at, 2 + rnd.nextInt(2), 1, 10);
            if (lump != null) {
                lump.launch(new Vec3(Math.cos(a) * 0.5, 1.0 + rnd.nextDouble() * 0.6, Math.sin(a) * 0.5), Chunks.tumble(rnd, 0.3F));
                level.addFreshEntity(lump);
            }
        }
    }

    private void march(ServerLevel level) {
        Vec3 next = this.position().add(this.dir.scale(0.42));
        int g = groundY(level, Mth.floor(next.x), Mth.floor(next.z), Mth.floor(this.getY()));
        double y = this.getY() + Mth.clamp(((double)g - this.getY()) * 0.2, -0.5, 0.5);
        this.setPos(next.x, y, next.z);
        if (this.breaks) {
            this.bulldoze(level);
        }

        this.trample(level);
        if (this.phaseTicks % 40 == 0) {
            level.playSound(
                null, this.getX(), this.getEyeY(), this.getZ(), ModSounds.TITAN_GROAN.get(), SoundSource.HOSTILE, 6.0F, 0.35F + this.random.nextFloat() * 0.1F
            );
        }

        if (--this.marchLeft <= 0) {
            this.vanish(level);
        }
    }

    public static int groundY(ServerLevel level, int x, int z, int fallback) {
        int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int min = Math.max(level.getMinBuildHeight(), y - 96);
        MutableBlockPos m = new MutableBlockPos(x, y, z);

        while (y > min) {
            if (isGround(level.getBlockState(m))) {
                return y + 1;
            }

            m.setY(--y);
        }

        return fallback;
    }

    public static boolean isGround(BlockState s) {
        return s.is(BlockTags.DIRT)
            || s.is(BlockTags.SAND)
            || s.is(BlockTags.BASE_STONE_OVERWORLD)
            || s.is(net.neoforged.neoforge.common.Tags.Blocks.GRAVELS)
            || s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)
            || s.is(Blocks.FARMLAND)
            || s.is(Blocks.DIRT_PATH)
            || s.is(Blocks.BEDROCK)
            || s.is(Blocks.CLAY)
            || s.is(Blocks.PACKED_MUD)
            || s.is(BlockTags.TERRACOTTA);
    }

    private void bulldoze(ServerLevel level) {
        RandomSource rnd = this.random;
        double half = (double)this.getBbWidth() * 0.5;
        double front0 = half - 1.5;
        double front1 = half + 0.42 + 2.0;
        double lat = half + 5.0;
        Vec3 side = new Vec3(-this.dir.z, 0.0, this.dir.x);
        LongOpenHashSet cols = new LongOpenHashSet();

        for (double a = front0; a <= front1; a += 0.6) {
            for (double l = -lat; l <= lat; l += 0.6) {
                cols.add(BlockPos.asLong(Mth.floor(this.getX() + this.dir.x * a + side.x * l), 0, Mth.floor(this.getZ() + this.dir.z * a + side.z * l)));
            }
        }

        int y0 = Mth.floor(this.getY());
        int y1 = Mth.floor(this.getY() + (double)this.getBbHeight() + 3.0);
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
        int thrown = 0;
        int cleared = 0;
        int topHit = y0;
        MutableBlockPos m = new MutableBlockPos();
        LongIterator tries = cols.iterator();

        while (tries.hasNext()) {
            long col = tries.next();
            int x = BlockPos.getX(col);
            int z = BlockPos.getZ(col);
            double lateral = ((double)x + 0.5 - this.getX()) * side.x + ((double)z + 0.5 - this.getZ()) * side.z;

            for (int y = y0; y <= y1 && cleared < 1600; y++) {
                m.set(x, y, z);
                BlockState s = level.getBlockState(m);
                if (!s.isAir() && (s.getFluidState().isEmpty() || !s.getCollisionShape(level, m).isEmpty()) && !(s.getDestroySpeed(level, m) < 0.0F)) {
                    cleared++;
                    topHit = Math.max(topHit, y);
                    if (!s.getCollisionShape(level, m).isEmpty() && thrown < 70 && !(rnd.nextFloat() < 0.35F)) {
                        double h = ((double)y - this.getY()) / (double)this.getBbHeight();
                        Vec3 v = this.dir
                            .scale(0.9 + rnd.nextDouble() * 1.3 + h * 0.6)
                            .add(side.scale(Math.signum(lateral) * (0.2 + rnd.nextDouble() * 0.7)))
                            .add(0.0, 0.3 + rnd.nextDouble() * 0.8 + h * 0.4, 0.0);
                        Destruction.smash(level, m.immutable(), s, v, rubble, 2);
                        thrown++;
                    } else {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                        Rebuild.mark(level, m);
                        if (rnd.nextInt(10) == 0) {
                            level.sendParticles(
                                new BlockParticleOption(ParticleTypes.BLOCK, s), (double)x + 0.5, (double)y + 0.5, (double)z + 0.5, 4, 0.4, 0.4, 0.4, 0.2
                            );
                        }
                    }
                }
            }
        }

        this.smashed = Math.max(0, this.smashed - 4) + cleared;
        if (cleared > 0) {
            if (this.tickCount - this.lastCrunch > 5) {
                this.lastCrunch = this.tickCount;
                Vec3 at = this.position().add(this.dir.scale(front1)).add(0.0, (double)Math.min(20, topHit - y0) * 0.5, 0.0);
                Destruction.crunch(level, at, 4.0F);
                if (cleared > 120) {
                    level.playSound(null, at.x, at.y, at.z, ModSounds.BUILDING_COLLAPSE.get(), SoundSource.BLOCKS, 5.0F, 0.6F + rnd.nextFloat() * 0.3F);
                }
            }

            if (this.tickCount % 10 == 0) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 p = this.position().add(this.dir.scale(front1 * 0.6)).add(side.scale((double)s * (lat + 3.0)));
                    Destruction.requestCollapse(level, BlockPos.containing(p.x, this.getY() + 1.0, p.z), 14, 64, 4);
                }
            }
        }

        if (this.smashed > 60 && this.tickCount % 5 == 0) {
            for (int triesx = 0; triesx < 3; triesx++) {
                double a = front1 + 1.0 + rnd.nextDouble() * 3.0;
                double l = (rnd.nextDouble() * 2.0 - 1.0) * lat;
                int yx = y0 + 1 + rnd.nextInt(Math.max(2, Math.min(40, topHit - y0 + 4)));
                BlockPos at = BlockPos.containing(this.getX() + this.dir.x * a + side.x * l, (double)yx, this.getZ() + this.dir.z * a + side.z * l);
                FallingSectionEntity piece = Chunks.rip(level, at, 2 + rnd.nextInt(2), 0, 12);
                if (piece != null) {
                    Vec3 v = this.dir
                        .scale(1.0 + rnd.nextDouble() * 0.9)
                        .add(side.scale(Math.signum(l) * rnd.nextDouble() * 0.5))
                        .add(0.0, 0.55 + rnd.nextDouble() * 0.7, 0.0);
                    piece.launch(v, Chunks.tumble(rnd, 0.35F));
                    level.addFreshEntity(piece);
                    break;
                }
            }
        }
    }

    private void trample(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(4.0, 0.0, 4.0).expandTowards(this.dir.x * 5.0, 0.0, this.dir.z * 5.0);
        Vec3 side = new Vec3(-this.dir.z, 0.0, this.dir.x);
        DamageSource src = this.damageSources().mobAttack(this);

        for (Entity e : level.getEntities(this, box, Entity::isAlive)) {
            if (!(e instanceof TitanPart) && !(e instanceof RumblingTitan) && !(e instanceof FallingSectionEntity) && !(e instanceof DebrisEntity)) {
                if (e instanceof ItemEntity) {
                    e.discard();
                } else if (e instanceof AbstractTitan titan) {
                    titan.vaporise(src);
                } else {
                    double lateral = (e.getX() - this.getX()) * side.x + (e.getZ() - this.getZ()) * side.z;
                    Vec3 push = this.dir.scale(1.5).add(side.scale(Math.signum(lateral) * 0.7)).add(0.0, 0.75, 0.0);
                    if (e instanceof Player) {
                        Player p = (Player)e;
                        if (p.isSpectator()) {
                            continue;
                        }
                    }

                    e.setDeltaMovement(push);
                    e.hurtMarked = true;
                    e.fallDistance = 0.0F;
                    if (e instanceof Player) {
                        Player p = (Player)e;
                        if (p.isCreative()) {
                            continue;
                        }
                    }

                    if (e instanceof LivingEntity living) {
                        living.hurt(src, 45.0F);
                    }
                }
            }
        }
    }

    @Override
    protected void footstep() {
        if (this.level().isClientSide) {
            super.footstep();
        } else if (this.level() instanceof ServerLevel level && this.phase == 1) {
            RandomSource rnd = this.random;
            Vec3 r = this.skeletonPoint(12, FOOT);
            Vec3 l = this.skeletonPoint(14, FOOT);
            Vec3 foot = r.y < l.y ? r : l;
            int gy = groundY(level, Mth.floor(foot.x), Mth.floor(foot.z), Mth.floor(this.getY()));
            Vec3 at = new Vec3(foot.x, (double)gy, foot.z);
            level.playSound(null, at.x, at.y, at.z, ModSounds.RUMBLING_STEP.get(), SoundSource.HOSTILE, 14.0F, 0.75F + rnd.nextFloat() * 0.2F);
            Fx.send(level, 15, at, 11.0F, 0.0F, 400.0);
            AbstractTitan.shakeNearby(level, at, 170.0, 0.75F, 16);
            if (this.breaks) {
                this.crater(level, at, 4.5 + rnd.nextDouble());
            }

            DamageSource src = this.damageSources().mobAttack(this);

            for (Entity e : level.getEntities(this, new AABB(at, at).inflate(14.0, 6.0, 14.0), Entity::isAlive)) {
                if (!(e instanceof TitanPart) && !(e instanceof RumblingTitan) && !(e instanceof FallingSectionEntity)) {
                    if (e instanceof Player) {
                        Player p = (Player)e;
                        if (p.isSpectator()) {
                            continue;
                        }
                    }

                    double d = Math.sqrt(e.distanceToSqr(at.x, e.getY(), at.z));
                    if (!(d > 14.0)) {
                        double f = 1.0 - d / 14.0;
                        Vec3 out = d < 0.5 ? Vec3.ZERO : new Vec3(e.getX() - at.x, 0.0, e.getZ() - at.z).normalize();
                        if (e instanceof AbstractTitan) {
                            AbstractTitan titan = (AbstractTitan)e;
                            if (d < 6.0) {
                                titan.vaporise(src);
                            } else {
                                titan.blastKnockDown(80);
                            }
                        } else {
                            e.setDeltaMovement(e.getDeltaMovement().add(out.scale(0.8 * f)).add(0.0, 0.5 + 0.9 * f, 0.0));
                            e.hurtMarked = true;
                            if (e instanceof Player) {
                                Player p = (Player)e;
                                if (p.isCreative()) {
                                    continue;
                                }
                            }

                            if (e instanceof LivingEntity living) {
                                living.hurt(src, d < 4.0 ? 400.0F : (float)(30.0 * f));
                            }
                        }
                    }
                }
            }

            return;
        }
    }

    private void crater(ServerLevel level, Vec3 at, double radius) {
        RandomSource rnd = this.random;
        int R = Mth.ceil(radius);
        int cx = Mth.floor(at.x);
        int cy = Mth.floor(at.y);
        int cz = Mth.floor(at.z);
        MutableBlockPos m = new MutableBlockPos();

        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt((double)(dx * dx + dz * dz));
                if (!(d > radius)) {
                    int depth = (int)Math.round(2.4 * (1.0 - d / radius * (d / radius)));

                    for (int y = cy - depth; y <= cy + 3; y++) {
                        m.set(cx + dx, y, cz + dz);
                        BlockState s = level.getBlockState(m);
                        if (!s.isAir() && !(s.getDestroySpeed(level, m) < 0.0F) && (s.getFluidState().isEmpty() || !s.getCollisionShape(level, m).isEmpty())) {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                            Rebuild.mark(level, m);
                            if (rnd.nextFloat() < 0.14F && !s.getCollisionShape(level, m).isEmpty()) {
                                Vec3 out = d < 0.5 ? new Vec3(0.0, 1.0, 0.0) : new Vec3((double)dx / d, 0.0, (double)dz / d);
                                Vec3 v = out.scale(0.4 + rnd.nextDouble() * 0.6).add(0.0, 0.6 + rnd.nextDouble() * 0.8, 0.0);
                                Destruction.throwOrSettle(level, Vec3.atCenterOf(m), s, v, 0.2F, 0);
                            }
                        }
                    }

                    m.set(cx + dx, cy - depth - 1, cz + dz);
                    BlockState floor = level.getBlockState(m);
                    if (isGround(floor) && floor.getDestroySpeed(level, m) >= 0.0F) {
                        float roll = rnd.nextFloat();
                        level.setBlock(
                            m,
                            roll < 0.45F
                                ? Blocks.COARSE_DIRT.defaultBlockState()
                                : (roll < 0.8F ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState()),
                            50
                        );
                    }
                }
            }
        }

        Destruction.requestCollapse(level, BlockPos.containing(at.x, at.y + 1.0, at.z), 10, 48, 3);
    }

    private void vanish(ServerLevel level) {
        Vec3 c = this.skeletonPoint(2, TitanSkeleton.TORSO_CENTER);
        Destruction.far(
            level,
            ModParticles.BIG_STEAM.get(),
            c.x,
            c.y,
            c.z,
            260,
            (double)this.getBbWidth(),
            (double)this.getTitanHeight() * 0.4,
            (double)this.getBbWidth(),
            0.3,
            500.0
        );
        Fx.send(level, 12, this.position(), this.getTitanHeight(), 0.0F, 500.0);
        level.playSound(null, c.x, c.y, c.z, ModSounds.COLOSSAL_STEAM.get(), SoundSource.HOSTILE, 12.0F, 0.55F);
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RumblingPhase", this.phase);
        tag.putInt("RumblingTicks", this.phaseTicks);
        tag.putInt("MarchLeft", this.marchLeft);
        tag.putDouble("RiseFrom", this.riseFrom);
        tag.putDouble("RiseTo", this.riseTo);
        tag.putDouble("DirX", this.dir.x);
        tag.putDouble("DirZ", this.dir.z);
        tag.putBoolean("Breaks", this.breaks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.phase = tag.getInt("RumblingPhase");
        this.phaseTicks = tag.getInt("RumblingTicks");
        if (tag.contains("MarchLeft")) {
            this.marchLeft = tag.getInt("MarchLeft");
        }

        this.riseFrom = tag.getDouble("RiseFrom");
        this.riseTo = tag.getDouble("RiseTo");
        if (tag.contains("DirX")) {
            this.dir = new Vec3(tag.getDouble("DirX"), 0.0, tag.getDouble("DirZ"));
        }

        if (this.dir.lengthSqr() < 1.0E-4) {
            this.dir = new Vec3(0.0, 0.0, 1.0);
        }

        this.dir = this.dir.normalize();
        if (tag.contains("Breaks")) {
            this.breaks = tag.getBoolean("Breaks");
        }
    }
}
