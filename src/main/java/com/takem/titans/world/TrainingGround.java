package com.takem.titans.world;

import com.takem.titans.entity.titan.TrainingDummy;
import com.takem.titans.registry.ModBlocks;
import com.takem.titans.registry.ModEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Training Corps yard on the lot east of the arrival street (x 34..53, z 13..32): a fenced dirt yard with a
 * 15 metre gantry to hook into, two wooden titans to cut, the instructor's shed with a supply station, and benches for
 * the recruits waiting their turn. Generated column by column like the rest of the town, so it also rebuilds itself.
 */
public final class TrainingGround {
    public static final int X0 = 34;
    public static final int Z0 = 13;
    public static final int GANTRY_Y = 82;
    public static final Vec3 CENTER = new Vec3(44.0, 67.0, 24.0);
    public static final Vec3 INSTRUCTOR = new Vec3(44.5, 67.0, 16.5);
    public static final Vec3 GATE = new Vec3(44.0, 67.0, 10.5);
    public static final BlockPos SUPPLY = new BlockPos(X0 + 16, 67, Z0 + 1);
    private static final Vec3[] DUMMIES = new Vec3[]{new Vec3(40.5, 67.0, 23.5), new Vec3(47.5, 67.0, 23.5)};

    private TrainingGround() {
    }

    public static boolean inYard(double x, double z, double margin) {
        return x >= X0 - margin && x < X0 + 20 + margin && z >= Z0 - margin && z < Z0 + 20 + margin;
    }

    static void column(ColumnBuffer col, int lx, int lz) {
        double r = WorldNoise.rand(col.x, 7, col.z, 313);
        if (lx == 0 || lx == 19 || lz == 0 || lz == 19) {
            edge(col, lx, lz, r);
            return;
        }

        boolean pole = (lx == 2 || lx == 17) && (lz == 5 || lz == 14);
        boolean pad = lz >= 9 && lz <= 11 && (lx >= 5 && lx <= 7 || lx >= 12 && lx <= 14);
        boolean apron = lz <= 3 && lx >= 8 && lx <= 11;
        boolean path = lx >= 9 && lx <= 10;
        boolean shed = lx >= 14 && lx <= 18 && lz <= 3;
        if (pole) {
            col.set(66, Blocks.COBBLESTONE.defaultBlockState());
        } else if (shed) {
            col.set(66, Blocks.SPRUCE_PLANKS.defaultBlockState());
        } else if (pad) {
            boolean rim = lx == 5 || lx == 7 || lx == 12 || lx == 14 || lz == 9 || lz == 11;
            col.set(66, rim ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
        } else if (apron) {
            col.set(66, Blocks.POLISHED_ANDESITE.defaultBlockState());
        } else if (path) {
            col.set(66, r < 0.85 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
        } else {
            // churned-up drill ground, in patches rather than speckles
            double patch = WorldNoise.rand(Math.floorDiv(col.x, 3), 9, Math.floorDiv(col.z, 3), 314);
            BlockState ground = patch < 0.45
                ? Blocks.DIRT_PATH.defaultBlockState()
                : (patch < 0.75 ? Blocks.COARSE_DIRT.defaultBlockState() : (patch < 0.9 ? Blocks.GRAVEL.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState()));
            col.set(66, ground);
            if (patch >= 0.45 && patch < 0.9 && r > 0.9) {
                col.set(67, Blocks.SHORT_GRASS.defaultBlockState());
            }
        }

        if (pole) {
            col.fill(67, GANTRY_Y, log(Blocks.STRIPPED_SPRUCE_LOG, Axis.Y));
            col.set(GANTRY_Y + 1, Blocks.LANTERN.defaultBlockState());
        } else if ((lz == 5 || lz == 14) && lx > 2 && lx < 17) {
            col.set(GANTRY_Y, log(Blocks.STRIPPED_SPRUCE_LOG, Axis.X));
            if (lx == 6 || lx == 13) {
                col.fill(GANTRY_Y - 2, GANTRY_Y - 1, Blocks.CHAIN.defaultBlockState());
                col.set(GANTRY_Y - 3, log(Blocks.HAY_BLOCK, Axis.Y));
            } else if (lx == (lz == 5 ? 9 : 10)) {
                col.set(GANTRY_Y - 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        } else if ((lx == 2 || lx == 17) && lz > 5 && lz < 14) {
            col.set(GANTRY_Y, log(Blocks.STRIPPED_SPRUCE_LOG, Axis.Z));
        }

        if (shed) {
            boolean post = (lx == 14 || lx == 18) && (lz == 1 || lz == 3);
            if (post) {
                col.fill(67, 69, log(Blocks.STRIPPED_SPRUCE_LOG, Axis.Y));
            } else if (lz == 1 && lx == 16) {
                col.set(67, ModBlocks.SUPPLY_STATION.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
            } else if (lz == 1 && (lx == 15 || lx == 17)) {
                col.set(67, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            } else if (lz == 3 && lx == 16) {
                col.set(69, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }

            col.set(70, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }

        if (lz == 18 && (lx >= 3 && lx <= 7 || lx >= 12 && lx <= 16)) {
            col.set(67, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        }

        if (lx == 1 && lz >= 16 || lx == 2 && lz == 18) {
            col.set(67, log(Blocks.HAY_BLOCK, lz == 17 ? Axis.X : Axis.Y));
            if (lx == 1 && lz == 18) {
                col.set(68, log(Blocks.HAY_BLOCK, Axis.Z));
            }
        }

        if (lx == 18 && lz >= 16) {
            col.set(67, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            if (lz == 18) {
                col.set(68, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            }
        }
    }

    /** The border: a stone kerb with a spruce fence, lamps on the corners and lantern-hung arches over both gates. */
    private static void edge(ColumnBuffer col, int lx, int lz, double r) {
        boolean northGate = lz == 0 && lx >= 8 && lx <= 11;
        boolean westGate = lx == 0 && lz >= 8 && lz <= 11;
        boolean pillar = lz == 0 && (lx == 7 || lx == 12) || lx == 0 && (lz == 7 || lz == 12);
        if (northGate || westGate) {
            col.set(66, Blocks.POLISHED_ANDESITE.defaultBlockState());
            col.set(70, log(Blocks.STRIPPED_SPRUCE_LOG, northGate ? Axis.X : Axis.Z));
            int along = northGate ? lx : lz;
            if (along == 8 || along == 11) {
                col.set(69, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }

            return;
        }

        col.set(66, r < 0.75 ? Blocks.STONE_BRICKS.defaultBlockState() : (r < 0.9 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState()));
        if (pillar) {
            col.fill(67, 68, Blocks.STONE_BRICKS.defaultBlockState());
            col.set(69, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            col.set(70, log(Blocks.STRIPPED_SPRUCE_LOG, lz == 0 ? Axis.X : Axis.Z));
            col.set(71, Blocks.LANTERN.defaultBlockState());
            return;
        }

        col.set(67, fence(wall(lx, lz - 1), wall(lx + 1, lz), wall(lx, lz + 1), wall(lx - 1, lz)));
        if ((lx == 0 || lx == 19) && (lz == 0 || lz == 19)) {
            col.fill(68, 69, Blocks.SPRUCE_FENCE.defaultBlockState());
            col.set(70, Blocks.LANTERN.defaultBlockState());
        }
    }

    /** Whether the border has something a fence can join at this lot position (fence or pillar, not a gate gap). */
    private static boolean wall(int lx, int lz) {
        if (lx < 0 || lz < 0 || lx > 19 || lz > 19) {
            return false;
        } else if (lx != 0 && lx != 19 && lz != 0 && lz != 19) {
            return false;
        } else {
            return !(lz == 0 && lx >= 8 && lx <= 11) && !(lx == 0 && lz >= 8 && lz <= 11);
        }
    }

    private static BlockState fence(boolean n, boolean e, boolean s, boolean w) {
        return Blocks.SPRUCE_FENCE
            .defaultBlockState()
            .setValue(CrossCollisionBlock.NORTH, n)
            .setValue(CrossCollisionBlock.EAST, e)
            .setValue(CrossCollisionBlock.SOUTH, s)
            .setValue(CrossCollisionBlock.WEST, w);
    }

    private static BlockState log(Block b, Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** Keeps the yard standing and its two dummies on their pads. Cheap enough to run once a second near the yard. */
    public static void upkeep(ServerLevel level) {
        ensureBuilt(level);

        for (Vec3 at : DUMMIES) {
            BlockPos pos = BlockPos.containing(at);
            if (!level.isLoaded(pos) || !level.areEntitiesLoaded(ChunkPos.asLong(pos))) {
                continue;
            }

            List<TrainingDummy> found = level.getEntitiesOfClass(TrainingDummy.class, new AABB(pos).inflate(5.0, 4.0, 5.0));

            for (int i = 1; i < found.size(); i++) {
                found.get(i).discard();
            }

            if (found.isEmpty()) {
                TrainingDummy dummy = ModEntities.TRAINING_DUMMY.get().create(level);
                if (dummy != null) {
                    dummy.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
                    dummy.anchor(180.0F);
                    level.addFreshEntity(dummy);
                }
            } else if (found.get(0).position().distanceToSqr(at) > 0.5) {
                found.get(0).teleportTo(at.x, at.y, at.z);
            }
        }
    }

    /**
     * Worlds made before 2.0 have a house on this lot. The first time someone comes by, the lot is rebuilt from the
     * generator so every world gets the yard; the supply station doubles as the marker that it is done.
     */
    static void ensureBuilt(ServerLevel level) {
        if (!level.isLoaded(SUPPLY) || level.getBlockState(SUPPLY).is(ModBlocks.SUPPLY_STATION.get())) {
            return;
        }

        if (!(level.getChunkSource().getGenerator() instanceof ShiganshinaChunkGenerator gen)) {
            return;
        }

        TownPlan.Lot lot = TownPlan.lotAt(X0, Z0);
        if (lot == null || lot.kind != TownPlan.LotKind.TRAINING) {
            return;
        }

        int[][] corners = new int[][]{{X0, Z0}, {X0 + 19, Z0}, {X0, Z0 + 19}, {X0 + 19, Z0 + 19}};

        for (int[] c : corners) {
            if (!level.isLoaded(new BlockPos(c[0], 67, c[1]))) {
                return;
            }
        }

        ColumnBuffer col = new ColumnBuffer(level.getMinBuildHeight(), level.getHeight());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = X0; x < X0 + 20; x++) {
            for (int z = Z0; z < Z0 + 20; z++) {
                col.reset(x, z);
                gen.buildColumn(col, false);

                for (int y = 60; y <= 112; y++) {
                    BlockState want = col.get(y);
                    pos.set(x, y, z);
                    if (level.getBlockState(pos) != want) {
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }
}
