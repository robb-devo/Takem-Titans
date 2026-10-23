package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.registry.ModBlocks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags.Blocks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Destruction {
    private static final int MAX_SPAN = 24;
    private static final int MAX_LOAD = 24;
    private static final int MAX_NEW_DEBRIS_PER_TICK = 240;
    private static final int MAX_COLLAPSE_BLOCKS_PER_TICK = 320;
    private static final int MAX_SOUNDS_PER_TICK = 8;
    private static final int MAX_FALLING_PER_COLLAPSE = 6000;
    private static final int MIN_SECTION = 10;
    static boolean DEBUG = Boolean.getBoolean("takemtitans.debugCollapse");
    private static int liveDebris;
    private static long budgetTick = -9223372036854775808L;
    private static int newDebris;
    private static int sounds;
    private static int dust;
    private static final Map<ResourceKey<Level>, Destruction.LevelState> STATES = new HashMap<>();
    private static final int[] NX = new int[]{1, -1, 0, 0, 0, 0};
    private static final int[] NY = new int[]{0, 0, 1, -1, 0, 0};
    private static final int[] NZ = new int[]{0, 0, 0, 0, 1, -1};

    private Destruction() {
    }

    public static void debrisAdded() {
        liveDebris++;
    }

    public static void debrisRemoved() {
        liveDebris = Math.max(0, liveDebris - 1);
    }

    private static void refresh(ServerLevel level) {
        long t = (long)level.getServer().getTickCount();
        if (t != budgetTick) {
            budgetTick = t;
            newDebris = 0;
            sounds = 0;
            dust = 0;
        }
    }

    public static boolean soundBudget(ServerLevel level) {
        refresh(level);
        return sounds++ < 8;
    }

    private static boolean debrisBudget(ServerLevel level) {
        refresh(level);
        return liveDebris < TakemConfig.DEBRIS_LIMIT.get() && newDebris < 240;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        liveDebris = 0;
        FallingSectionEntity.resetBudget();
    }

    public static boolean isAnchor(BlockState s) {
        return s.is(BlockTags.DIRT)
            || s.is(BlockTags.SAND)
            || s.is(BlockTags.BASE_STONE_OVERWORLD)
            || s.is(BlockTags.BASE_STONE_NETHER)
            || s.is(Blocks.ORES)
            || s.is(Blocks.GRAVELS)
            || s.is(Blocks.SANDS)
            || s.is(BlockTags.SNOW)
            || s.is(BlockTags.ICE)
            || s.is(BlockTags.WITHER_IMMUNE)
            || s.is(BlockTags.DRAGON_IMMUNE)
            || s.is(net.minecraft.world.level.block.Blocks.BEDROCK)
            || s.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)
            || s.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN)
            || s.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE);
    }

    public static boolean isStructural(Level level, BlockPos pos, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES)) {
            return false;
        } else if (isAnchor(s)) {
            return false;
        } else if (s.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        } else if (s.getCollisionShape(level, pos).isEmpty()) {
            return false;
        } else if (s.getBlock() instanceof net.minecraft.world.level.block.LanternBlock
            || s.getBlock() instanceof net.minecraft.world.level.block.ChainBlock
            || s.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock
            || s.getBlock() instanceof net.minecraft.world.level.block.BellBlock
            || s.is(ModBlocks.GATE_CONTROL.get())) {
            // lamps, chains and pots hang off a house; they used to rain down from intact eaves on every check
            return false;
        } else {
            // the giant trees come down as whole trees (TreeFall), not piece by piece
            return !s.is(BlockTags.LOGS) || GiantTrees.owner(level, pos, s) == null;
        }
    }

    public static boolean canSmash(Level level, BlockPos pos, BlockState s, float maxHardness) {
        if (isStructural(level, pos, s)) {
            return s.getDestroySpeed(level, pos) <= maxHardness && !Rebuild.resists(level, pos);
        } else {
            // a giant trunk shrugs off a titan walking into it, but a blast or a falling house still bites
            return maxHardness >= 5.0F && s.is(BlockTags.LOGS) && GiantTrees.owner(level, pos, s) != null;
        }
    }

    public static boolean isWallStone(BlockState s) {
        return s.is(ModBlocks.WALL_STONE.get())
            || s.is(ModBlocks.WALL_STONE_BRICKS.get())
            || s.is(ModBlocks.WALL_STONE_TILES.get())
            || s.is(ModBlocks.CHISELED_WALL_STONE.get())
            || s.is(ModBlocks.WALL_STONE_STAIRS.get())
            || s.is(ModBlocks.WALL_STONE_SLAB.get());
    }

    /** Natural ground a big enough blast can dig into. Bedrock and the like stay. */
    public static boolean isTerrain(Level level, BlockPos pos, BlockState s, float maxHardness) {
        if (!isAnchor(s)
            || s.is(BlockTags.WITHER_IMMUNE)
            || s.is(BlockTags.DRAGON_IMMUNE)
            || s.is(net.minecraft.world.level.block.Blocks.BEDROCK)
            || s.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)
            || s.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN)
            || s.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) {
            return false;
        } else {
            float h = s.getDestroySpeed(level, pos);
            return h >= 0.0F && h <= maxHardness;
        }
    }

    /**
     * How much weight a block can carry, in the same units as {@link #density}: a stone pier holds up a few hundred
     * blocks of house, a timber post a bit less, glass and wool next to nothing. Slabs, stairs, fences and the like
     * carry half of what a full block of the same stuff would.
     */
    public static float strength(BlockState s) {
        if (isWallStone(s)) {
            return 8000.0F;
        } else if (isGlass(s)) {
            return 6.0F;
        } else {
            SoundType t = s.getSoundType();
            float base;
            if (t == SoundType.WOOL || t == SoundType.MOSS || t == SoundType.GRASS || t == SoundType.AZALEA_LEAVES || t == SoundType.SNOW) {
                return 6.0F;
            } else if (s.is(BlockTags.LOGS)) {
                base = 280.0F;
            } else if (isWood(s)) {
                base = 200.0F;
            } else if (t == SoundType.METAL || t == SoundType.ANVIL || t == SoundType.NETHERITE_BLOCK || t == SoundType.COPPER) {
                base = 700.0F;
            } else {
                base = 420.0F;
            }

            if (s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                return base;
            } else if (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.WallBlock) {
                return base * 0.5F;
            } else {
                // doors, fences, trapdoors, lanterns and pots hang on the house; they don't hold it up
                return 20.0F;
            }
        }
    }

    /** Leaves don't fly as rubble: they burst into a puff of themselves. */
    public static void shred(ServerLevel level, BlockPos pos, BlockState s) {
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
        Rebuild.mark(level, pos);
        sprinkle(level, pos, s);
        TreeFall.notify(level, pos, s);
    }

    /** A blast scoops the ground out: the top of the crater is thrown, the rest just goes. */
    private static void dig(ServerLevel level, BlockPos pos, BlockState s, Vec3 v) {
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
        Rebuild.mark(level, pos);
        dropUnsupported(level, pos);
        if (level.getBlockState(pos.above()).isAir() && level.random.nextInt(3) == 0) {
            throwBlock(level, pos, s, v, 0.25F, 0);
        } else if (level.random.nextInt(4) == 0) {
            sprinkle(level, pos, s);
        }
    }

    /**
     * Everything a blast reaches that isn't a building: foliage is shredded, the ground at its heart is dug out, and
     * the Wall loses a bite of its face (it takes a lot of these to get through twelve metres of it).
     */
    public static boolean blastOther(ServerLevel level, BlockPos pos, BlockState s, double frac, float maxHardness, Vec3 v) {
        if (s.isAir()) {
            return false;
        } else if (s.is(BlockTags.LEAVES)) {
            shred(level, pos, s);
            return true;
        } else if (frac < 0.62 && isTerrain(level, pos, s, Math.min(maxHardness, 3.0F))) {
            dig(level, pos, s, v);
            return true;
        } else if (frac < 0.45 && maxHardness >= 6.0F && isWallStone(s) && level.random.nextFloat() < 0.75F) {
            smash(level, pos, s, v.scale(0.6), 0.3F, 1);
            return true;
        } else {
            return false;
        }
    }

    private static boolean isGlass(BlockState s) {
        return s.is(Blocks.GLASS_BLOCKS) || s.is(Blocks.GLASS_PANES) || s.getSoundType() == SoundType.GLASS;
    }

    public static float density(BlockState s) {
        SoundType t = s.getSoundType();
        if (isGlass(s)) {
            return 0.5F;
        } else if (t == SoundType.WOOL || t == SoundType.MOSS || t == SoundType.GRASS || t == SoundType.AZALEA_LEAVES) {
            return 0.3F;
        } else if (isWood(s)) {
            return 0.85F;
        } else {
            return t != SoundType.METAL && t != SoundType.ANVIL && t != SoundType.NETHERITE_BLOCK && t != SoundType.COPPER ? 2.2F : 3.5F;
        }
    }

    private static boolean isWood(BlockState s) {
        SoundType t = s.getSoundType();
        return t == SoundType.WOOD || t == SoundType.CHERRY_WOOD || t == SoundType.BAMBOO_WOOD || t == SoundType.NETHER_WOOD;
    }

    public static void smash(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity, float rubbleChance) {
        smash(level, pos, state, velocity, rubbleChance, 0);
    }

    public static void smash(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity, float rubbleChance, int breaks) {
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
        Rebuild.mark(level, pos);
        dropUnsupported(level, pos);
        throwBlock(level, pos, state, velocity, rubbleChance, breaks);
        TreeFall.notify(level, pos, state);
    }

    /**
     * Blocks are removed without neighbour updates (so a blast doesn't cascade into millions of block updates), which
     * used to leave torches, lanterns, flowers, doors and carpets hanging in mid-air. Knock those off by hand.
     */
    public static void dropUnsupported(ServerLevel level, BlockPos pos) {
        MutableBlockPos m = new MutableBlockPos();
        for (Direction dir : Direction.values()) {
            m.setWithOffset(pos, dir);
            BlockState s = level.getBlockState(m);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canSurvive(level, m)) {
                level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
                Rebuild.mark(level, m);
                if (s.getCollisionShape(level, m).isEmpty()) {
                    sprinkle(level, m, s);
                } else {
                    throwBlock(level, m.immutable(), s, new Vec3(0.0, -0.05, 0.0), 0.0F, 0);
                }

                // the top half of a door or tall plant goes with its bottom half
                BlockPos up = m.above();
                BlockState above = level.getBlockState(up);
                if (!above.isAir() && above.getFluidState().isEmpty() && !above.canSurvive(level, up)) {
                    level.setBlock(up, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
                    Rebuild.mark(level, up);
                }
            }
        }
    }

    static void sprinkle(ServerLevel level, BlockPos p, BlockState s) {
        refresh(level);
        if (dust < 300) {
            dust += 2;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.05);
        }
    }

    public static void throwBlock(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity, float rubbleChance) {
        throwBlock(level, pos, state, velocity, rubbleChance, 0);
    }

    public static void throwBlock(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity, float rubbleChance, int breaks) {
        refresh(level);
        if (state.is(BlockTags.LEAVES)) {
            sprinkle(level, pos, state);
        } else if (isGlass(state)) {
            if (dust < 300) {
                dust += 6;
                level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    (double)pos.getX() + 0.5,
                    (double)pos.getY() + 0.5,
                    (double)pos.getZ() + 0.5,
                    6,
                    0.3,
                    0.3,
                    0.3,
                    0.15
                );
            }

            if (soundBudget(level)) {
                level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            }
        } else if (!state.getCollisionShape(level, pos).isEmpty()) {
            ignite(level, pos, state);
            if (debrisBudget(level)) {
                newDebris++;
                level.addFreshEntity(DebrisEntity.create(level, pos, state, velocity, rubbleChance).withBreaks(breaks));
            } else if (dust < 300) {
                dust += 3;
                level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    (double)pos.getX() + 0.5,
                    (double)pos.getY() + 0.5,
                    (double)pos.getZ() + 0.5,
                    3,
                    0.3,
                    0.3,
                    0.3,
                    0.1
                );
            }
        }
    }

    private static void ignite(ServerLevel level, BlockPos pos, BlockState state) {
        if (TakemConfig.DESTRUCTION_FIRES.get()) {
            boolean fiery = state.is(net.minecraft.world.level.block.Blocks.LANTERN)
                || state.is(net.minecraft.world.level.block.Blocks.FURNACE)
                || state.is(net.minecraft.world.level.block.Blocks.SMOKER)
                || state.is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE)
                || state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_LANTERN)
                || state.is(BlockTags.CANDLES);
            if (fiery && !(level.random.nextFloat() > 0.45F)) {
                MutableBlockPos m = pos.mutable();

                for (int k = 0; k < 6; k++) {
                    if (level.getBlockState(m).isAir() && BaseFireBlock.canBePlacedAt(level, m, Direction.UP)) {
                        level.setBlock(m, BaseFireBlock.getState(level, m), 3);
                        Rebuild.mark(level, m);
                        level.sendParticles(ParticleTypes.LAVA, (double)m.getX() + 0.5, (double)m.getY() + 0.5, (double)m.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.0);
                        return;
                    }

                    m.move(0, -1, 0);
                }
            }
        }
    }

    public static void throwOrSettle(ServerLevel level, Vec3 p, BlockState state, Vec3 velocity, float rubbleChance, int breaks) {
        refresh(level);
        if (state.is(BlockTags.LEAVES)) {
            if (dust < 300) {
                dust += 2;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 2, 0.3, 0.3, 0.3, 0.05);
            }
        } else if (isGlass(state)) {
            if (dust < 300) {
                dust += 4;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.15);
            }

            if (soundBudget(level)) {
                level.playSound(null, p.x, p.y, p.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
            }
        } else if (debrisBudget(level)) {
            newDebris++;
            level.addFreshEntity(DebrisEntity.create(level, p, state, velocity, rubbleChance).withBreaks(breaks));
        } else {
            MutableBlockPos m = BlockPos.containing(p).mutable();
            if (rubbleChance > 0.0F && level.random.nextFloat() < rubbleChance * 0.6F && state.isCollisionShapeFullBlock(level, m)) {
                for (int k = 0; k < 24 && m.getY() > level.getMinBuildHeight(); k++) {
                    BlockState below = level.getBlockState(m.below());
                    if (!below.canBeReplaced()) {
                        if (level.getBlockState(m).canBeReplaced() && level.getBlockState(m).getFluidState().isEmpty()) {
                            level.setBlock(m, state, 3);
                            Rebuild.mark(level, m);
                        }

                        return;
                    }

                    m.move(0, -1, 0);
                }
            }

            if (dust < 300) {
                dust += 3;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    public static void blast(ServerLevel level, Vec3 c, double radius) {
        if (TakemConfig.SPEAR_BLASTS_BLOCKS.get()) {
            RandomSource rnd = level.random;
            int r = Mth.ceil(radius);
            BlockPos center = BlockPos.containing(c);
            MutableBlockPos m = new MutableBlockPos();
            int broken = 0;

            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        Vec3 p = Vec3.atCenterOf(m);
                        double d = p.distanceTo(c);
                        if (!(d > radius) && (!(d > radius * 0.7) || !((double)rnd.nextFloat() < (d / radius - 0.7) * 2.5))) {
                            BlockState s = level.getBlockState(m);
                            Vec3 dir = d < 0.01 ? new Vec3(0.0, 1.0, 0.0) : p.subtract(c).scale(1.0 / d);
                            double power = 0.5 + 1.25 * (1.0 - d / radius);
                            Vec3 v = dir.scale(power * (0.75 + rnd.nextDouble() * 0.5)).add(0.0, 0.18 + rnd.nextDouble() * 0.25, 0.0);
                            if (canSmash(level, m, s, 6.0F)) {
                                smash(level, m.immutable(), s, v, 0.12F, power > 1.2 ? 1 : 0);
                                broken++;
                            } else if (blastOther(level, m.immutable(), s, d / radius, 6.0F, v)) {
                                broken++;
                            }
                        }
                    }
                }
            }

            if (broken > 0) {
                far(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, 6, radius * 0.5, radius * 0.4, radius * 0.5, 0.03, 160.0);
                requestCollapse(level, center, 16, 64, 12);
            }
        }
    }

    public static void bodySlam(ServerLevel level, Vec3 feet, Vec3 dir, double length, double radius, boolean sweep) {
        if (TakemConfig.TITAN_GRIEFING.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            RandomSource rnd = level.random;
            Vec3 ax = new Vec3(dir.x, 0.0, dir.z).normalize();
            Vec3 to = feet.add(ax.scale(length));
            int r = Mth.ceil(radius);
            int minX = Mth.floor(Math.min(feet.x, to.x)) - r;
            int maxX = Mth.floor(Math.max(feet.x, to.x)) + r;
            int minZ = Mth.floor(Math.min(feet.z, to.z)) - r;
            int maxZ = Mth.floor(Math.max(feet.z, to.z)) + r;
            double flat = radius * 2.2;
            int minY = Mth.floor(feet.y);
            int maxY = Mth.floor(feet.y + (sweep ? Math.max(flat, length) : flat));
            MutableBlockPos m = new MutableBlockPos();
            int broken = 0;

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        m.set(x, y, z);
                        double px = (double)x + 0.5 - feet.x;
                        double pz = (double)z + 0.5 - feet.z;
                        double t = Mth.clamp(px * ax.x + pz * ax.z, 0.0, length);
                        double ox = px - ax.x * t;
                        double oz = pz - ax.z * t;
                        double side = Math.sqrt(ox * ox + oz * oz);
                        if (!(side > radius * 1.05)) {
                            double h = (double)y + 0.5 - feet.y;
                            double ceiling = sweep ? Math.max(flat, Math.sqrt(Math.max(0.0, length * length - t * t))) : flat;
                            if (!(h > ceiling)) {
                                BlockState s = level.getBlockState(m);
                                if (canSmash(level, m, s, 3.0F)) {
                                    Vec3 out = side > 0.01 ? new Vec3(ox / side, 0.0, oz / side) : ax;
                                    Vec3 v;
                                    if (h > flat) {
                                        v = out.scale(0.25 + rnd.nextDouble() * 0.35)
                                            .add(ax.scale(0.35 + rnd.nextDouble() * 0.3))
                                            .add(0.0, -0.15 - rnd.nextDouble() * 0.25, 0.0);
                                    } else {
                                        v = out.scale(0.35 + rnd.nextDouble() * 0.5).add(ax.scale(0.2)).add(0.0, 0.25 + rnd.nextDouble() * 0.45, 0.0);
                                    }

                                    smash(level, m.immutable(), s, v, 0.25F);
                                    broken++;
                                }
                            }
                        }
                    }
                }
            }

            if (broken > 0) {
                Vec3 mid = feet.add(to).scale(0.5);
                crunch(level, mid, 2.5F);
                requestCollapse(level, BlockPos.containing(mid.x, feet.y + 1.0, mid.z), Mth.clamp((int)(length * 0.5) + 8, 10, 20), 64, 4);
            }
        }
    }

    public static void glassBurst(ServerLevel level, Vec3 c, double radius) {
        RandomSource rnd = level.random;
        int r = Math.min(40, Mth.ceil(radius));
        BlockPos center = BlockPos.containing(c);
        MutableBlockPos m = new MutableBlockPos();
        int broken = 0;
        int fx = 0;

        for (int dy = -r / 2; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r * r) {
                        m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        BlockState s = level.getBlockState(m);
                        if (!s.isAir() && isGlass(s)) {
                            level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
                            Rebuild.mark(level, m);
                            broken++;
                            if (fx < 14 && rnd.nextFloat() < 0.25F) {
                                Fx.send(level, 23, Vec3.atCenterOf(m), 1.5F, 0.0F, 128.0);
                                fx++;
                            }

                            if (broken % 10 == 1 && soundBudget(level)) {
                                level.playSound(null, m, ModSounds.GLASS_SHATTER.get(), SoundSource.BLOCKS, 2.0F, 0.8F + rnd.nextFloat() * 0.4F);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void crunch(ServerLevel level, Vec3 at, float volume) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, volume, 0.5F + level.random.nextFloat() * 0.2F);
        level.playSound(
            null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, volume * 0.6F, 0.45F + level.random.nextFloat() * 0.15F
        );
    }

    public static void requestCollapse(ServerLevel level, BlockPos center, int r, int up, int down) {
        requestCollapse(level, center, r, up, down, false);
    }

    /** {@code drops}: the collapse was set off by a player taking a block out, so felled timber drops as logs. */
    public static void requestCollapse(ServerLevel level, BlockPos center, int r, int up, int down, boolean drops) {
        if (TakemConfig.BUILDING_COLLAPSE.get()) {
            Destruction.LevelState st = STATES.computeIfAbsent(level.dimension(), k -> new Destruction.LevelState());
            long due = level.getGameTime() + 2L;

            for (Destruction.Request q : st.requests) {
                if (q.center.distManhattan(center) <= 5) {
                    q.r = Math.max(q.r, r);
                    q.up = Math.max(q.up, up);
                    q.down = Math.max(q.down, down);
                    q.due = due;
                    q.drops |= drops;
                    return;
                }
            }

            if (st.requests.size() < 96) {
                double scale = TakemConfig.COLLAPSE_SCALE.get();
                st.requests
                    .add(
                        new Destruction.Request(
                            center,
                            Math.min((int)Math.round((double)r * scale), (int)Math.round(32.0 * scale)),
                            Math.min(up, 96),
                            Math.min(down, 16),
                            due,
                            drops
                        )
                    );
            }
        }
    }

    /**
     * Hands a whole body of blocks to the collapse pipeline: it creaks for {@code delay} ticks, then comes down in one
     * piece, pivoting on {@code hinge} towards {@code tip} if one is given. {@code hidden} blocks go with it unseen.
     */
    public static boolean queueSection(
        ServerLevel level,
        long[] positions,
        BlockState[] states,
        long[] hidden,
        @Nullable Vec3 hinge,
        @Nullable Vec3 tip,
        @Nullable BlockPos hingeBlock,
        long[] contacts,
        int delay,
        boolean wood
    ) {
        Destruction.LevelState st = STATES.computeIfAbsent(level.dimension(), k -> new Destruction.LevelState());
        if (positions.length < 10 || !FallingSectionEntity.budgetFor(positions.length + st.pendingBlocks)) {
            return false;
        } else {
            for (long p : positions) {
                st.claimed.add(p);
            }

            for (long p : hidden) {
                st.claimed.add(p);
            }

            st.pending.add(new Destruction.Pending(positions, states, hinge, tip, hingeBlock, contacts, level.getGameTime() + (long)delay, wood, hidden, false));
            st.pendingBlocks += positions.length;
            return true;
        }
    }

    /** Whether this block already belongs to a piece that is about to come down. */
    public static boolean isClaimed(ServerLevel level, long pos) {
        Destruction.LevelState st = STATES.get(level.dimension());
        return st != null && !st.claimed.isEmpty() && st.claimed.contains(pos);
    }

    /** Lets blocks come down one by one as rubble, a few hundred a tick. */
    public static void queueLoose(ServerLevel level, long[] positions, BlockState[] states, Vec3 center, boolean wood) {
        Destruction.LevelState st = STATES.computeIfAbsent(level.dimension(), k -> new Destruction.LevelState());
        Destruction.Job job = new Destruction.Job(positions, states, center);
        job.wood = wood;
        st.jobs.add(job);
    }

    /** Taking a block out of something can bring the rest of it down, whoever does it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            BlockPos pos = event.getPos();
            BlockState s = event.getState();
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
                TreeFall.notify(level, pos, s);
            }

            if (isStructural(level, pos, s) || isAnchor(s)) {
                requestCollapse(level, pos, 14, 40, 6, true);
            }
        }
    }

    /** Weight added to a building has to be carried too, so stacking a storey onto one post brings it down. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled()
            && event.getLevel() instanceof ServerLevel level
            && event.getEntity() instanceof ServerPlayer player
            && !player.isCreative()
            && isStructural(level, event.getPos(), event.getPlacedBlock())) {
            requestCollapse(level, event.getPos(), 14, 32, 10, true);
        }
    }

    /** Creepers, TNT and friends leave buildings hanging in the air otherwise. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && !event.getAffectedBlocks().isEmpty()) {
            Vec3 c = event.getExplosion().center();
            requestCollapse(level, BlockPos.containing(c), 18, 56, 8, false);

            for (BlockPos p : event.getAffectedBlocks()) {
                BlockState s = level.getBlockState(p);
                if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
                    TreeFall.notify(level, p, s);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Destruction.LevelState st = STATES.get(level.dimension());
            if (st != null) {
                long now = level.getGameTime();
                int analysed = 0;

                for (int i = 0; i < st.requests.size() && analysed < 3; i++) {
                    Destruction.Request q = st.requests.get(i);
                    if (q.due <= now) {
                        st.requests.remove(i--);
                        analysed++;
                        Destruction.Job job = analyse(level, q, st);
                        if (job != null) {
                            st.jobs.add(job);
                        }
                    }
                }

                for (int ix = 0; ix < st.pending.size(); ix++) {
                    Destruction.Pending p = st.pending.get(ix);
                    p.age++;
                    if (now < p.due) {
                        creak(level, p);
                    } else {
                        st.pending.remove(ix--);
                        release(level, st, p);
                    }
                }

                int budget = (int)(480.0 * TakemConfig.COLLAPSE_SCALE.get());

                while (budget > 0 && !st.jobs.isEmpty()) {
                    Destruction.Job job = st.jobs.peek();
                    budget -= run(level, job, budget);
                    if (job.index >= job.positions.length) {
                        st.jobs.poll();
                        finish(level, job);
                    }
                }
            }
        }
    }

    private static Destruction.Job analyse(ServerLevel level, Destruction.Request q, Destruction.LevelState lst) {
        boolean anyClaimed = !lst.claimed.isEmpty();
        int minX = q.center.getX() - q.r;
        int maxX = q.center.getX() + q.r;
        int minZ = q.center.getZ() - q.r;
        int maxZ = q.center.getZ() + q.r;
        int minY = Math.max(level.getMinBuildHeight(), q.center.getY() - q.down);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, q.center.getY() + q.up);
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        if (sy <= 1) {
            return null;
        } else {
            int n = sx * sy * sz;
            byte[] kind = new byte[n];
            java.util.BitSet wallCell = new java.util.BitSet(n);
            BlockState[] states = new BlockState[n];
            MutableBlockPos m = new MutableBlockPos();
            int structural = 0;
            int leaves = 0;

            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int wx = minX + x;
                    int wz = minZ + z;
                    boolean loaded = level.hasChunk(wx >> 4, wz >> 4);

                    for (int y = 0; y < sy; y++) {
                        int i = (y * sz + z) * sx + x;
                        if (!loaded) {
                            kind[i] = 1;
                        } else {
                            m.set(wx, minY + y, wz);
                            BlockState s = level.getBlockState(m);
                            if (!s.isAir() && (!anyClaimed || !lst.claimed.contains(m.asLong()))) {
                                if (isStructural(level, m, s)) {
                                    kind[i] = 2;
                                    states[i] = s;
                                    structural++;
                                } else if (s.is(BlockTags.LEAVES)) {
                                    kind[i] = 3;
                                    states[i] = s;
                                    leaves++;
                                } else if (!s.getCollisionShape(level, m).isEmpty() && (isAnchor(s) || s.is(BlockTags.LOGS) || s.getDestroySpeed(level, m) < 0.0F)) {
                                    // only real ground holds things up: a lantern or a flower pot is not a foundation
                                    kind[i] = 1;
                                    if (isWallStone(s)) {
                                        wallCell.set(i);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (structural == 0) {
                return null;
            } else {
                byte[] load = new byte[n];

                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        int above = 0;

                        for (int yx = sy - 1; yx >= 0; yx--) {
                            int i = (yx * sz + z) * sx + x;
                            if (kind[i] == 2) {
                                // the Wall is one monolith: it bridges a breach like an arch instead of shearing off above it
                                load[i] = isWallStone(states[i]) ? 0 : (byte)Math.min(24, above);
                                above++;
                            } else {
                                above = 0;
                            }
                        }
                    }
                }

                byte[] dist = new byte[n];
                Arrays.fill(dist, (byte)127);
                Destruction.IntQueue[] buckets = new Destruction.IntQueue[25];

                for (int b = 0; b <= 24; b++) {
                    buckets[b] = new Destruction.IntQueue();
                }

                for (int yxx = 0; yxx < sy; yxx++) {
                    for (int z = 0; z < sz; z++) {
                        for (int x = 0; x < sx; x++) {
                            int i = (yxx * sz + z) * sx + x;
                            if (kind[i] == 1) {
                                dist[i] = 0;
                                buckets[0].add(i);
                            } else if (kind[i] == 2 && (yxx == 0 || x == 0 || z == 0 || x == sx - 1 || z == sz - 1)) {
                                // the top of the scan is open sky, not support: a roof must rest on something
                                dist[i] = 0;
                                buckets[0].add(i);
                            }
                        }
                    }
                }

                int[] hx = new int[]{1, -1, 0, 0};
                int[] hz = new int[]{0, 0, 1, -1};

                for (int d = 0; d <= 24; d++) {
                    Destruction.IntQueue bq = buckets[d];

                    while (!bq.isEmpty()) {
                        int i = bq.poll();
                        if (dist[i] == d) {
                            int xx = i % sx;
                            int rest = i / sx;
                            int z = rest % sz;
                            int yxx = rest / sz;
                            if (yxx + 1 < sy) {
                                relax(i + sx * sz, d, kind, dist, buckets);
                            }

                            if (yxx > 0) {
                                relax(i - sx * sz, d + 12 + 3 * load[i - sx * sz], kind, dist, buckets);
                            }

                            // a house leaning on the Wall isn't held up by it: the Wall only carries what sits on top of it
                            for (int k = 0; k < 4 && !wallCell.get(i); k++) {
                                int nx = xx + hx[k];
                                int nz = z + hz[k];
                                if (nx >= 0 && nz >= 0 && nx < sx && nz < sz) {
                                    int j = (yxx * sz + nz) * sx + nx;
                                    if (kind[j] == 2) {
                                        relax(j, d + 3 + 3 * load[j], kind, dist, buckets);
                                    }

                                    if (yxx + 1 < sy) {
                                        int ju = j + sx * sz;
                                        if (kind[ju] == 2) {
                                            relax(ju, d + 2 + 3 * load[ju], kind, dist, buckets);
                                        }
                                    }

                                    if (yxx > 0) {
                                        int jd = j - sx * sz;
                                        if (kind[jd] == 2) {
                                            relax(jd, d + 12 + 3 * load[jd], kind, dist, buckets);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                crushOverloaded(level, q, kind, dist, states, sx, sy, sz, minX, minY, minZ);
                int count = 0;

                for (int i = 0; i < n; i++) {
                    if (kind[i] == 2 && dist[i] > 24) {
                        count++;
                    }
                }

                if (DEBUG) {
                    TakemTitans.LOGGER.info("collapse at {} r{} up{}: {} structural, {} fall", new Object[]{q.center, q.r, q.up, structural, count});
                    int cx = q.center.getX() - minX;
                    int cz0 = q.center.getZ() - minZ;
                    int cy0 = q.center.getY() - minY;

                    for (int yxxx = Math.min(sy - 1, cy0 + 18); yxxx >= Math.max(0, cy0 - 2); yxxx--) {
                        StringBuilder sb = new StringBuilder("y" + (minY + yxxx) + ":");

                        for (int zx = Math.max(0, cz0 - 4); zx <= Math.min(sz - 1, cz0 + 4); zx++) {
                            int ix = (yxxx * sz + zx) * sx + cx;
                            sb.append(' ').append(kind[ix] == 0 ? " ." : (kind[ix] == 1 ? " A" : String.format("%2d/%d", Math.min(99, dist[ix]), load[ix])));
                        }

                        TakemTitans.LOGGER.info(sb.toString());
                    }
                }

                if (count == 0) {
                    return null;
                } else {
                    int[] comp = new int[n];
                    List<IntArrayList> groups = new ArrayList<>();
                    groups.add(null);
                    Destruction.IntQueue queue = new Destruction.IntQueue();
                    long now = level.getGameTime();

                    for (int ix = 0; ix < n; ix++) {
                        if (kind[ix] == 2 && dist[ix] > 24 && comp[ix] == 0) {
                            int id = groups.size();
                            IntArrayList members = new IntArrayList();
                            groups.add(members);
                            comp[ix] = id;
                            queue.head = queue.tail = 0;
                            queue.add(ix);

                            while (!queue.isEmpty()) {
                                int c = queue.poll();
                                members.add(c);
                                int xxx = c % sx;
                                int restx = c / sx;
                                int zx = restx % sz;
                                int yxxx = restx / sz;

                                for (int kx = 0; kx < 6; kx++) {
                                    int nx = xxx + NX[kx];
                                    int ny = yxxx + NY[kx];
                                    int nz = zx + NZ[kx];
                                    if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sx && ny < sy && nz < sz) {
                                        int jx = (ny * sz + nz) * sx + nx;
                                        if (kind[jx] == 2 && dist[jx] > 24 && comp[jx] == 0) {
                                            comp[jx] = id;
                                            queue.add(jx);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (leaves > 0) {
                        carryLeaves(kind, dist, comp, groups, sx, sy, sz);
                    }

                    IntArrayList loose = new IntArrayList();

                    for (int id = 1; id < groups.size(); id++) {
                        IntArrayList members = groups.get(id);
                        if (!makeSection(level, lst, members, kind, dist, comp, id, states, sx, sy, sz, minX, minY, minZ, now, q.drops)) {
                            loose.addAll(members);
                        }
                    }

                    if (loose.isEmpty()) {
                        return null;
                    } else {
                        loose.sort(null);
                        int fall = Math.min(loose.size(), 6000);
                        long[] pos = new long[fall];
                        BlockState[] st = new BlockState[fall];
                        boolean wood = false;

                        for (int c = 0; c < fall; c++) {
                            int ixx = loose.getInt(c);
                            int xxx = ixx % sx;
                            int restx = ixx / sx;
                            int zx = restx % sz;
                            int yxxx = restx / sz;
                            pos[c] = BlockPos.asLong(minX + xxx, minY + yxxx, minZ + zx);
                            st[c] = states[ixx];
                            if (!wood && isWood(states[ixx])) {
                                wood = true;
                            }
                        }

                        Destruction.Job job = new Destruction.Job(pos, st, Vec3.atCenterOf(q.center));
                        job.wood = wood;
                        job.drops = q.drops;
                        BlockPos first = BlockPos.of(pos[0]);
                        level.playSound(null, first, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, Math.min(6.0F, 1.5F + (float)fall / 150.0F), 0.4F);
                        if (wood) {
                            level.playSound(null, first, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 3.0F, 0.45F);
                        }

                        return job;
                    }
                }
            }
        }
    }

    /**
     * Leaves belong to the nearest thing they grow from, as in vanilla's leaf distance: a crown goes down with the trunk
     * that fell, but not with a neighbouring tree, and a hedge resting on a wall stays where it is.
     */
    private static void carryLeaves(byte[] kind, byte[] dist, int[] comp, List<IntArrayList> groups, int sx, int sy, int sz) {
        int n = kind.length;
        int sxz = sx * sz;
        Destruction.IntQueue queue = new Destruction.IntQueue();
        boolean anyFalling = false;

        for (int i = 0; i < n; i++) {
            if (kind[i] == 3) {
                dist[i] = 127;
                comp[i] = 0;
            }
        }

        for (int i = 0; i < n; i++) {
            if (kind[i] == 1 || kind[i] == 2) {
                int x = i % sx;
                int z = i / sx % sz;
                int y = i / sxz;
                boolean touches = x + 1 < sx && kind[i + 1] == 3
                    || x > 0 && kind[i - 1] == 3
                    || z + 1 < sz && kind[i + sx] == 3
                    || z > 0 && kind[i - sx] == 3
                    || y + 1 < sy && kind[i + sxz] == 3
                    || y > 0 && kind[i - sxz] == 3;
                if (touches) {
                    queue.add(i);
                    anyFalling |= kind[i] == 2 && dist[i] > 24;
                }
            }
        }

        if (anyFalling) {
            while (!queue.isEmpty()) {
                int c = queue.poll();
                int owner = kind[c] == 3 ? comp[c] : (kind[c] == 2 && dist[c] > 24 ? comp[c] : -1);
                int depth = kind[c] == 3 ? dist[c] : 0;
                if (depth < 7) {
                    int x = c % sx;
                    int z = c / sx % sz;
                    int y = c / sxz;

                    for (int k = 0; k < 6; k++) {
                        int nx = x + NX[k];
                        int ny = y + NY[k];
                        int nz = z + NZ[k];
                        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sx && ny < sy && nz < sz) {
                            int j = (ny * sz + nz) * sx + nx;
                            if (kind[j] == 3 && comp[j] == 0) {
                                comp[j] = owner;
                                dist[j] = (byte)(depth + 1);
                                queue.add(j);
                                if (owner > 0) {
                                    groups.get(owner).add(j);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Follows the weight of every standing block down to the ground: straight down where something can carry it, along
     * the floor to the nearest wall or post where nothing can, and down a roof slope to the eaves. A block asked to carry
     * more than its material can take gives way, so a house left on a single post comes down even though the post itself
     * still reaches the ground.
     */
    private static void crushOverloaded(
        ServerLevel level, Destruction.Request q, byte[] kind, byte[] dist, BlockState[] states, int sx, int sy, int sz, int minX, int minY, int minZ
    ) {
        int sxz = sx * sz;
        float[] carried = new float[kind.length];
        byte[] role = new byte[sxz];
        int[] owner = new int[sxz];
        byte[] steps = new byte[sxz];
        Destruction.IntQueue queue = new Destruction.IntQueue();
        IntOpenHashSet over = null;

        for (int y = sy - 1; y >= 0; y--) {
            int base = y * sxz;
            boolean any = false;

            for (int c = 0; c < sxz; c++) {
                int i = base + c;
                role[c] = ROLE_NONE;
                // everything still standing weighs on something, even the parts the support pass gave up on
                if (kind[i] == 2) {
                    any = true;
                    carried[i] += density(states[i]);
                    int x = c % sx;
                    int z = c / sx;
                    if (x == 0 || z == 0 || x == sx - 1 || z == sz - 1) {
                        // what lies beyond the edge of the survey is assumed to carry its share
                        role[c] = ROLE_EDGE;
                    } else if (y == 0 || kind[i - sxz] == 1) {
                        role[c] = ROLE_GROUND;
                    } else if (bears(kind, dist, states, i - sxz)) {
                        role[c] = ROLE_POST;
                    } else if (bears(kind, dist, states, i - sxz + 1)
                        || bears(kind, dist, states, i - sxz - 1)
                        || bears(kind, dist, states, i - sxz + sx)
                        || bears(kind, dist, states, i - sxz - sx)) {
                        role[c] = ROLE_SLOPE;
                    } else {
                        role[c] = ROLE_SPAN;
                    }
                }
            }

            if (any) {
                queue.head = queue.tail = 0;

                for (int c = 0; c < sxz; c++) {
                    // glass, wool and the like pass on their own weight but nobody leans a floor on them
                    if (role[c] != ROLE_NONE && role[c] != ROLE_SPAN && (role[c] == ROLE_EDGE || strength(states[base + c]) >= 40.0F)) {
                        owner[c] = c;
                        steps[c] = 0;
                        queue.add(c);
                    } else {
                        owner[c] = -1;
                    }
                }

                // a floor hands its weight along to the nearest wall, post or slope on its own level
                while (!queue.isEmpty()) {
                    int c = queue.poll();
                    if (steps[c] < 24) {
                        int x = c % sx;
                        int z = c / sx;

                        for (int k = 0; k < 4; k++) {
                            int nx = x + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                            int nz = z + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                            if (nx >= 0 && nz >= 0 && nx < sx && nz < sz) {
                                int nc = nz * sx + nx;
                                if (role[nc] == ROLE_SPAN && owner[nc] < 0) {
                                    owner[nc] = owner[c];
                                    steps[nc] = (byte)(steps[c] + 1);
                                    queue.add(nc);
                                }
                            }
                        }
                    }
                }

                for (int c = 0; c < sxz; c++) {
                    if (role[c] == ROLE_SPAN && owner[c] >= 0) {
                        carried[base + owner[c]] += carried[base + c];
                    }
                }

                for (int c = 0; c < sxz; c++) {
                    byte r = role[c];
                    if (r == ROLE_POST || r == ROLE_SLOPE || r == ROLE_GROUND) {
                        int i = base + c;
                        float load = carried[i];
                        float str = strength(states[i]);
                        if (lone(kind, c, base, sx, sz)) {
                            // a single free-standing pillar buckles long before a wall of the same stuff would
                            str *= states[i].is(BlockTags.LOGS) || states[i].is(BlockTags.PLANKS) ? 0.2F : 0.1F;
                        }

                        // houses are built with a good margin: only a clear overload gives way, never a column that's merely busy
                        if (load > 40.0F && load > str * 1.6F) {
                            if (DEBUG) {
                                TakemTitans.LOGGER.info(
                                    "overload {} at {} {} {}: carries {} of {} (role {})",
                                    states[i], minX + c % sx, minY + y, minZ + c / sx, load, str, r
                                );
                            }

                            if (over == null) {
                                over = new IntOpenHashSet();
                            }

                            over.add(i);
                        }

                        if (r == ROLE_POST) {
                            carried[i - sxz] += load;
                        } else if (r == ROLE_SLOPE) {
                            int b = i - sxz;
                            int[] side = new int[]{b + 1, b - 1, b + sx, b - sx};
                            int k = 0;

                            for (int j : side) {
                                if (bears(kind, dist, states, j)) {
                                    k++;
                                }
                            }

                            for (int j : side) {
                                if (bears(kind, dist, states, j)) {
                                    carried[j] += load / (float)k;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (over != null) {
            MutableBlockPos m = new MutableBlockPos();
            RandomSource rnd = level.random;
            int crushed = 0;
            BlockPos first = null;

            for (int i : over) {
                // only the bottom of an overloaded run gives way; what stands on it follows on the next pass
                if (i >= sxz && over.contains(i - sxz) || crushed >= 48) {
                    continue;
                }

                int x = i % sx;
                int z = i / sx % sz;
                int y = i / sxz;
                m.set(minX + x, minY + y, minZ + z);
                if (level.getBlockState(m) == states[i]) {
                    BlockPos at = m.immutable();
                    double a = rnd.nextDouble() * Math.PI * 2.0;
                    Vec3 v = new Vec3(Math.cos(a) * 0.18, -0.05, Math.sin(a) * 0.18);
                    smash(level, at, states[i], v, 0.35F, 1);
                    crushed++;
                    if (first == null) {
                        first = at;
                    }
                }
            }

            if (first != null) {
                if (DEBUG) {
                    TakemTitans.LOGGER.info("overloaded: {} blocks crushed near {}", crushed, first);
                }

                crunch(level, Vec3.atCenterOf(first), 2.0F + Math.min(3.0F, crushed * 0.2F));
                far(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5, 2 + crushed / 3, 1.0, 0.6, 1.0, 0.02, 128.0);
                requestCollapse(level, first, Math.max(16, q.r), Math.max(48, q.up), Math.max(6, q.down), q.drops);
            }
        }
    }

    private static final byte ROLE_NONE = 0;
    private static final byte ROLE_POST = 1;
    private static final byte ROLE_SLOPE = 2;
    private static final byte ROLE_GROUND = 3;
    private static final byte ROLE_EDGE = 4;
    private static final byte ROLE_SPAN = 5;

    /** Nothing structural beside it on its own level: a lone post or pier rather than part of a wall. */
    private static boolean lone(byte[] kind, int c, int base, int sx, int sz) {
        int x = c % sx;
        int z = c / sx;
        return (x == 0 || kind[base + c - 1] != 2)
            && (x == sx - 1 || kind[base + c + 1] != 2)
            && (z == 0 || kind[base + c - sx] != 2)
            && (z == sz - 1 || kind[base + c + sx] != 2);
    }

    private static boolean bears(byte[] kind, byte[] dist, BlockState[] states, int i) {
        return i >= 0 && i < kind.length && kind[i] == 2 && strength(states[i]) >= 40.0F;
    }

    private static boolean makeSection(
        ServerLevel level,
        Destruction.LevelState lst,
        IntArrayList members,
        byte[] kind,
        byte[] dist,
        int[] comp,
        int id,
        BlockState[] states,
        int sx,
        int sy,
        int sz,
        int minX,
        int minY,
        int minZ,
        long now,
        boolean drops
    ) {
        int size = members.size();
        int sxz = sx * sz;
        // blocks buried inside the section can't be seen while it falls: they go with it, but aren't drawn
        boolean[] buried = new boolean[size];
        int shown = size;
        if (size > 400) {
            for (int k = 0; k < size; k++) {
                int i = members.getInt(k);
                int x = i % sx;
                int z = i / sx % sz;
                int y = i / sxz;
                if (x > 0 && z > 0 && y > 0 && x < sx - 1 && z < sz - 1 && y < sy - 1
                    && comp[i + 1] == id && comp[i - 1] == id && comp[i + sx] == id && comp[i - sx] == id && comp[i + sxz] == id && comp[i - sxz] == id) {
                    buried[k] = true;
                    shown--;
                }
            }
        }

        if (size >= 10 && shown <= 6000 && FallingSectionEntity.budgetFor(shown + lst.pendingBlocks)) {
            int bx0 = 2147483647;
            int bx1 = -2147483648;
            int by0 = 2147483647;
            int by1 = -2147483648;
            int bz0 = 2147483647;
            int bz1 = -2147483648;
            double cx = 0.0;
            double cy = 0.0;
            double cz = 0.0;
            double kx = 0.0;
            double kz = 0.0;
            int contacts = 0;
            int touchMinY = 2147483647;
            long hingeBlock = -9223372036854775808L;
            long[] contactSample = new long[8];
            int samples = 0;
            boolean wood = false;

            for (int k = 0; k < size; k++) {
                int i = members.getInt(k);
                int x = i % sx;
                int rest = i / sx;
                int z = rest % sz;
                int y = rest / sz;
                bx0 = Math.min(bx0, x);
                bx1 = Math.max(bx1, x);
                by0 = Math.min(by0, y);
                by1 = Math.max(by1, y);
                bz0 = Math.min(bz0, z);
                bz1 = Math.max(bz1, z);
                cx += (double)x;
                cy += (double)y;
                cz += (double)z;
                if (!wood && isWood(states[i])) {
                    wood = true;
                }

                for (int d = 0; d < 6; d++) {
                    if (NY[d] == 0) {
                        int nx = x + NX[d];
                        int ny = y + NY[d];
                        int nz = z + NZ[d];
                        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sx && ny < sy && nz < sz) {
                            int j = (ny * sz + nz) * sx + nx;
                            if (kind[j] == 1 || kind[j] == 2 && dist[j] <= 24) {
                                contacts++;
                                kx += (double)nx;
                                kz += (double)nz;
                                long wp = BlockPos.asLong(minX + nx, minY + ny, minZ + nz);
                                if (samples < contactSample.length) {
                                    contactSample[samples++] = wp;
                                }

                                if (y < touchMinY) {
                                    touchMinY = y;
                                    hingeBlock = wp;
                                }
                            }
                        }
                    }
                }
            }

            if (bx1 - bx0 <= 120 && by1 - by0 <= 120 && bz1 - bz0 <= 120) {
                cx = cx / (double)size + 0.5;
                cy = cy / (double)size + 0.5;
                cz = cz / (double)size + 0.5;
                Vec3 hinge = null;
                Vec3 tip = null;
                BlockPos hb = null;
                if (contacts > 0) {
                    double hx = kx / (double)contacts + 0.5;
                    double hz = kz / (double)contacts + 0.5;
                    double dx = cx - hx;
                    double dz = cz - hz;
                    double dl = Math.sqrt(dx * dx + dz * dz);
                    double half = (double)Math.max(bx1 - bx0, bz1 - bz0) / 2.0 + 0.5;
                    if (dl > 0.3 * half + 0.4) {
                        tip = new Vec3(dx / dl, 0.0, dz / dl);
                        hinge = new Vec3((double)minX + hx + tip.x * 0.5, (double)(minY + touchMinY), (double)minZ + hz + tip.z * 0.5);
                        hb = BlockPos.of(hingeBlock);
                    }
                }

                long[] pos = new long[shown];
                BlockState[] st = new BlockState[shown];
                long[] hidden = new long[size - shown];
                int a = 0;
                int b = 0;

                for (int k = 0; k < size; k++) {
                    int i = members.getInt(k);
                    int x = i % sx;
                    int rest = i / sx;
                    int z = rest % sz;
                    int y = rest / sz;
                    long p = BlockPos.asLong(minX + x, minY + y, minZ + z);
                    lst.claimed.add(p);
                    if (buried[k]) {
                        hidden[b++] = p;
                    } else {
                        pos[a] = p;
                        st[a++] = states[i];
                    }
                }

                long delay = (long)(4 + Math.min(16, size / 45));
                if (DEBUG) {
                    TakemTitans.LOGGER.info("section {} blocks, contacts {}, hinge {} tip {}", new Object[]{size, contacts, hinge, tip});
                }

                lst.pending.add(new Destruction.Pending(pos, st, hinge, tip, hb, Arrays.copyOf(contactSample, samples), now + delay, wood, hidden, drops));
                lst.pendingBlocks += shown;
                return true;
            } else {
                return false;
            }
        } else {
            if (DEBUG) {
                int i0 = members.getInt(0);
                TakemTitans.LOGGER.info(
                    "component of {} falls loose: {} at {} {} {} dist {}",
                    size, states[i0], minX + i0 % sx, minY + i0 / sxz, minZ + i0 / sx % sz, dist[i0]
                );
            }

            return false;
        }
    }

    private static void creak(ServerLevel level, Destruction.Pending p) {
        RandomSource rnd = level.random;
        if (p.age == 1) {
            BlockPos at = BlockPos.of(p.positions[p.positions.length / 2]);
            if (p.wood) {
                level.playSound(null, at, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 2.5F, 0.5F);
                level.playSound(null, at, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
            } else {
                level.playSound(null, at, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 2.5F, 0.45F);
                level.playSound(null, at, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.5F, 0.5F);
            }
        }

        if (p.wood && p.age > 1 && p.age % 12 == 0) {
            BlockPos at = BlockPos.of(p.contacts.length > 0 ? p.contacts[rnd.nextInt(p.contacts.length)] : p.positions[0]);
            level.playSound(null, at, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 2.0F, 0.4F + rnd.nextFloat() * 0.15F);
        }

        if ((p.age & 1) == 0) {
            for (int k = 0; k < 3; k++) {
                int i = rnd.nextInt(p.positions.length);
                BlockPos b = BlockPos.of(p.positions[i]);
                far(
                    level,
                    new BlockParticleOption(ParticleTypes.FALLING_DUST, p.states[i]),
                    (double)b.getX() + 0.5,
                    (double)b.getY() - 0.05,
                    (double)b.getZ() + 0.5,
                    2,
                    0.4,
                    0.0,
                    0.4,
                    0.0,
                    96.0
                );
            }

            for (long c : p.contacts) {
                if (rnd.nextInt(3) == 0) {
                    BlockPos b = BlockPos.of(c);
                    BlockState s = level.getBlockState(b);
                    if (!s.isAir()) {
                        far(
                            level,
                            new BlockParticleOption(ParticleTypes.BLOCK, s),
                            (double)b.getX() + 0.5,
                            (double)b.getY() + 0.5,
                            (double)b.getZ() + 0.5,
                            4,
                            0.4,
                            0.4,
                            0.4,
                            0.05,
                            64.0
                        );
                    }
                }
            }
        }
    }

    private static void release(ServerLevel level, Destruction.LevelState lst, Destruction.Pending p) {
        int n = p.positions.length;
        lst.pendingBlocks = Math.max(0, lst.pendingBlocks - n);
        long[] pos = new long[n];
        BlockState[] st = new BlockState[n];
        int valid = 0;
        MutableBlockPos m = new MutableBlockPos();

        for (int i = 0; i < n; i++) {
            lst.claimed.remove(p.positions[i]);
            m.set(p.positions[i]);
            if (level.getBlockState(m) == p.states[i]) {
                pos[valid] = p.positions[i];
                st[valid] = p.states[i];
                valid++;
            }
        }

        if (DEBUG) {
            TakemTitans.LOGGER.info("release {} of {} valid", valid, n);
        }

        for (long h : p.hidden) {
            lst.claimed.remove(h);
        }

        if (valid != 0) {
            if (!((double)valid < (double)n * 0.7) && valid >= 10) {
                for (int ix = 0; ix < valid; ix++) {
                    m.set(pos[ix]);
                    level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
                    Rebuild.mark(level, m);
                }

                for (long h : p.hidden) {
                    m.set(h);
                    if (!level.getBlockState(m).isAir()) {
                        level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
                        Rebuild.mark(level, m);
                    }
                }

                FallingSectionEntity e = FallingSectionEntity.create(level, pos, st, valid, p.hinge, p.tip, p.hingeBlock);
                e.drops = p.drops;
                level.addFreshEntity(e);
                BlockPos at = BlockPos.of(pos[valid / 2]);
                level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, Math.min(6.0F, 1.5F + (float)valid / 120.0F), 0.35F);
                if (p.wood) {
                    level.playSound(null, at, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 3.0F, 0.4F);
                }

                if (valid > 200) {
                    level.playSound(
                        null,
                        at,
                        ModSounds.BUILDING_COLLAPSE.get(),
                        SoundSource.BLOCKS,
                        Math.min(8.0F, 2.0F + (float)valid / 200.0F),
                        0.55F + level.random.nextFloat() * 0.15F
                    );
                }

                cleanupAround(level, Arrays.copyOf(pos, valid));
            } else {
                Destruction.Job job = new Destruction.Job(Arrays.copyOf(pos, valid), Arrays.copyOf(st, valid), Vec3.atCenterOf(BlockPos.of(pos[0])));
                job.wood = p.wood;
                job.drops = p.drops;
                lst.jobs.add(job);
                // what was buried inside the piece crumbles along with it
                lst.requests.add(new Destruction.Request(BlockPos.of(pos[0]), 16, 64, 8, level.getGameTime() + 2L, p.drops));
            }
        }
    }

    private static void relax(int j, int nd, byte[] kind, byte[] dist, Destruction.IntQueue[] buckets) {
        if (nd <= 24 && kind[j] == 2 && nd < dist[j]) {
            dist[j] = (byte)nd;
            buckets[nd].add(j);
        }
    }

    private static int run(ServerLevel level, Destruction.Job job, int budget) {
        RandomSource rnd = level.random;
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
        int done = 0;
        MutableBlockPos m = new MutableBlockPos();
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int lowestY = 2147483647;

        while (done < budget && job.index < job.positions.length) {
            long p = job.positions[job.index];
            BlockState expected = job.states[job.index];
            job.index++;
            done++;
            m.set(p);
            if (level.getBlockState(m) == expected && expected.is(BlockTags.LEAVES)) {
                shred(level, m.immutable(), expected);
            } else if (level.getBlockState(m) == expected && job.drops && (expected.is(BlockTags.LOGS) || job.positions.length <= 64)) {
                // what a player's own hands brought down lands as something to pick up again
                level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
                Block.dropResources(expected, level, m.immutable());
                dropUnsupported(level, m.immutable());
            } else if (level.getBlockState(m) == expected) {
                Vec3 out = new Vec3((double)m.getX() + 0.5 - job.center.x, 0.0, (double)m.getZ() + 0.5 - job.center.z);
                double ol = out.length();
                out = ol > 0.01 ? out.scale(0.035 / ol) : Vec3.ZERO;
                Vec3 v = new Vec3((rnd.nextDouble() - 0.5) * 0.07 + out.x, -0.02 - rnd.nextDouble() * 0.05, (rnd.nextDouble() - 0.5) * 0.07 + out.z);
                smash(level, m.immutable(), expected, v, rubble);
                sumX += (double)m.getX();
                sumY += (double)m.getY();
                sumZ += (double)m.getZ();
                lowestY = Math.min(lowestY, m.getY());
            }
        }

        if (done > 0 && lowestY != 2147483647) {
            double cx = sumX / (double)done + 0.5;
            double cz = sumZ / (double)done + 0.5;
            far(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, (double)lowestY + 0.5, cz, Math.min(5, 1 + done / 80), 3.0, 1.0, 3.0, 0.02, 160.0);
            far(level, ParticleTypes.POOF, cx, sumY / (double)done, cz, Math.min(30, 4 + done / 12), 3.0, 3.0, 3.0, 0.05, 96.0);
            if (soundBudget(level)) {
                level.playSound(
                    null,
                    cx,
                    sumY / (double)done,
                    cz,
                    job.wood ? SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR : SoundEvents.STONE_BREAK,
                    SoundSource.BLOCKS,
                    2.0F,
                    0.5F + rnd.nextFloat() * 0.2F
                );
            }
        }

        return Math.max(1, done);
    }

    private static void finish(ServerLevel level, Destruction.Job job) {
        cleanupAround(level, job.positions);
        AbstractTitan.shakeNearby(level, job.center, 48.0, Math.min(0.9F, 0.2F + (float)job.positions.length / 1500.0F), 20);
    }

    private static void cleanupAround(ServerLevel level, long[] positions) {
        MutableBlockPos m = new MutableBlockPos();
        int removed = 0;

        for (long p : positions) {
            for (Direction dir : Direction.values()) {
                m.set(p).move(dir);
                BlockState s = level.getBlockState(m);
                if (!s.isAir() && s.getFluidState().isEmpty() && !s.canSurvive(level, m)) {
                    level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 50);
                    Rebuild.mark(level, m);
                    if (++removed > 2000) {
                        return;
                    }
                }
            }
        }
    }

    public static void far(
        ServerLevel level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed, double range
    ) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) < range * range) {
                level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    private static final class IntQueue {
        int[] a = new int[64];
        int head;
        int tail;

        void add(int v) {
            if (this.tail == this.a.length) {
                if (this.head > this.a.length / 2) {
                    System.arraycopy(this.a, this.head, this.a, 0, this.tail - this.head);
                    this.tail = this.tail - this.head;
                    this.head = 0;
                } else {
                    this.a = Arrays.copyOf(this.a, this.a.length * 2);
                }
            }

            this.a[this.tail++] = v;
        }

        int poll() {
            return this.a[this.head++];
        }

        boolean isEmpty() {
            return this.head == this.tail;
        }
    }

    private static final class Job {
        final long[] positions;
        final BlockState[] states;
        final Vec3 center;
        int index;
        boolean wood;
        boolean drops;

        Job(long[] positions, BlockState[] states, Vec3 center) {
            this.positions = positions;
            this.states = states;
            this.center = center;
        }
    }

    private static final class LevelState {
        final List<Destruction.Request> requests = new ArrayList<>();
        final ArrayDeque<Destruction.Job> jobs = new ArrayDeque<>();
        final List<Destruction.Pending> pending = new ArrayList<>();
        final LongOpenHashSet claimed = new LongOpenHashSet();
        int pendingBlocks;
    }

    private static final class Pending {
        final long[] positions;
        final BlockState[] states;
        final Vec3 hinge;
        final Vec3 tip;
        final BlockPos hingeBlock;
        final long[] contacts;
        final long due;
        final boolean wood;
        final long[] hidden;
        final boolean drops;
        int age;

        Pending(
            long[] positions, BlockState[] states, Vec3 hinge, Vec3 tip, BlockPos hingeBlock, long[] contacts, long due, boolean wood, long[] hidden, boolean drops
        ) {
            this.positions = positions;
            this.states = states;
            this.hinge = hinge;
            this.tip = tip;
            this.hingeBlock = hingeBlock;
            this.contacts = contacts;
            this.due = due;
            this.wood = wood;
            this.hidden = hidden;
            this.drops = drops;
        }
    }

    private static final class Request {
        BlockPos center;
        int r;
        int up;
        int down;
        long due;
        boolean drops;

        Request(BlockPos center, int r, int up, int down, long due, boolean drops) {
            this.center = center;
            this.r = r;
            this.up = up;
            this.down = down;
            this.due = due;
            this.drops = drops;
        }
    }
}
