package com.takem.titans.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Brings houses generated before 2.0 up to the new exterior: eaves, gable verges, shutters, window boxes, door roofs
 * and the upright timber studs. Each town chunk is visited once, a couple per tick, and only fills in pieces where
 * the old house left air (or plain plaster, for the studs) next to a standing wall, so ruins and player builds are
 * left alone.
 */
@EventBusSubscriber(modid = "takemtitans")
public final class TownRetrofit {
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static final int PER_TICK = 2;
    private static ColumnBuffer buffer;

    private TownRetrofit() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.getChunkSource().getGenerator() instanceof ShiganshinaChunkGenerator) {
            ChunkPos cp = event.getChunk().getPos();
            if (touchesTown(cp)) {
                Done done = Done.get(level);
                long key = cp.toLong();
                if (event.isNewChunk()) {
                    // generated with the new code already
                    if (done.chunks.add(key)) {
                        done.setDirty();
                    }
                } else if (!done.chunks.contains(key) && QUEUED.add(key)) {
                    QUEUE.add(key);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!QUEUE.isEmpty() && event.getLevel() instanceof ServerLevel level && level.getChunkSource().getGenerator() instanceof ShiganshinaChunkGenerator) {
            Done done = Done.get(level);

            for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
                long key = QUEUE.poll();
                QUEUED.remove(key);
                ChunkPos cp = new ChunkPos(key);
                if (level.hasChunk(cp.x, cp.z)) {
                    retrofit(level, cp);
                    done.chunks.add(key);
                    done.setDirty();
                }
            }
        }
    }

    private static boolean touchesTown(ChunkPos cp) {
        int x0 = cp.getMinBlockX();
        int z0 = cp.getMinBlockZ();
        int[][] probes = new int[][]{{0, 0}, {15, 0}, {0, 15}, {15, 15}, {8, 8}};

        for (int[] p : probes) {
            if (ShiganshinaLayout.region(x0 + p[0] + 0.5, z0 + p[1] + 0.5) == ShiganshinaLayout.Region.DISTRICT) {
                return true;
            }
        }

        return false;
    }

    private static void retrofit(ServerLevel level, ChunkPos cp) {
        if (buffer == null || buffer.minY != level.getMinBuildHeight()) {
            buffer = new ColumnBuffer(level.getMinBuildHeight(), level.getHeight());
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
            for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                if (ShiganshinaLayout.region(x + 0.5, z + 0.5) != ShiganshinaLayout.Region.DISTRICT || TownPlan.lotAt(x, z) == null) {
                    continue;
                }

                if (TrainingGround.inYard(x, z, 0.0)) {
                    // the yard rebuilds itself as a whole
                    continue;
                }

                buffer.reset(x, z);
                TownPlan.column(buffer);
                boolean inside = TownPlan.isBuilding(x, z);
                int top = Math.min(buffer.top(), level.getMaxBuildHeight() - 1);

                for (int y = 67; y <= top; y++) {
                    BlockState want = buffer.get(y);
                    if (want.isAir()) {
                        continue;
                    }

                    pos.set(x, y, z);
                    BlockState have = level.getBlockState(pos);
                    if (have == want) {
                        continue;
                    }

                    boolean place = inside
                        ? want.getBlock() instanceof RotatedPillarBlock && isPlainWall(level, pos, have)
                        : have.isAir() && isExteriorPiece(want, y) && anchored(level, pos);
                    if (place) {
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    /** A stud only replaces solid wall fill, never a window, door, beam or anything a player put there. */
    private static boolean isPlainWall(ServerLevel level, BlockPos pos, BlockState have) {
        return !have.isAir() && !have.is(BlockTags.LOGS) && have.isCollisionShapeFullBlock(level, pos) && have.canOcclude();
    }

    private static boolean isExteriorPiece(BlockState want, int y) {
        Block b = want.getBlock();
        if (b instanceof TrapDoorBlock || b instanceof StairBlock || b instanceof FlowerPotBlock) {
            return true;
        } else if (b instanceof LanternBlock) {
            return want.getValue(LanternBlock.HANGING);
        } else {
            // the ridge and fill blocks of a gable verge; trees and lamps below the eaves are left as they are
            return y > 70 && !(b instanceof LeavesBlock) && !want.is(BlockTags.LOGS);
        }
    }

    private static boolean anchored(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!level.getBlockState(pos.relative(d)).isAir()) {
                return true;
            }
        }

        return !level.getBlockState(pos.below()).isAir() || !level.getBlockState(pos.above()).isAir();
    }

    static final class Done extends SavedData {
        final LongOpenHashSet chunks = new LongOpenHashSet();

        static Done get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(Done::new, Done::load, null), "takemtitans_exteriors");
        }

        private static Done load(CompoundTag tag, Provider registries) {
            Done d = new Done();

            for (long k : tag.getLongArray("Chunks")) {
                d.chunks.add(k);
            }

            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, Provider registries) {
            tag.putLongArray("Chunks", this.chunks.toLongArray());
            return tag;
        }
    }
}
