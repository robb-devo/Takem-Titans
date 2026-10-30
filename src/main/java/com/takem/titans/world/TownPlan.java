package com.takem.titans.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import com.takem.titans.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

public final class TownPlan {
    static final int CELL = 26;
    static final int LOT = 20;
    static final int X0 = 8;
    static final int Z0 = -91;
    static final double PLAZA_X = 0.0;
    static final double PLAZA_Z = -2.0;
    static final double PLAZA_R = 26.0;
    static final double GATE_SQUARE_Z = 88.0;
    static final double GATE_SQUARE_R = 34.0;
    static final TownPlan.Palette[] PALETTES = new TownPlan.Palette[]{
        new TownPlan.Palette(
            ModBlocks.PLASTER.get().defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.STONE_BRICKS.defaultBlockState(),
            ModBlocks.ROOF_TILES.get().defaultBlockState(),
            ModBlocks.ROOF_TILES_STAIRS.get().defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.DARK_OAK_DOOR
        ),
        new TownPlan.Palette(
            ModBlocks.PLASTER_CREAM.get().defaultBlockState(),
            Blocks.STRIPPED_SPRUCE_LOG,
            Blocks.COBBLESTONE.defaultBlockState(),
            ModBlocks.ROOF_TILES_SLATE.get().defaultBlockState(),
            ModBlocks.ROOF_TILES_SLATE_STAIRS.get().defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(),
            Blocks.SPRUCE_DOOR
        ),
        new TownPlan.Palette(
            ModBlocks.PLASTER.get().defaultBlockState(),
            Blocks.STRIPPED_OAK_LOG,
            Blocks.ANDESITE.defaultBlockState(),
            ModBlocks.ROOF_TILES_DARK.get().defaultBlockState(),
            ModBlocks.ROOF_TILES_DARK_STAIRS.get().defaultBlockState(),
            Blocks.BIRCH_PLANKS.defaultBlockState(),
            Blocks.OAK_DOOR
        ),
        new TownPlan.Palette(
            Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.NETHER_BRICKS.defaultBlockState(),
            Blocks.NETHER_BRICK_STAIRS.defaultBlockState(),
            Blocks.DARK_OAK_PLANKS.defaultBlockState(),
            Blocks.DARK_OAK_DOOR
        ),
        new TownPlan.Palette(
            Blocks.BIRCH_PLANKS.defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
            Blocks.MUD_BRICKS.defaultBlockState(),
            Blocks.MUD_BRICK_STAIRS.defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(),
            Blocks.SPRUCE_DOOR
        ),
        new TownPlan.Palette(
            ModBlocks.PLASTER_CREAM.get().defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.POLISHED_ANDESITE.defaultBlockState(),
            ModBlocks.ROOF_TILES.get().defaultBlockState(),
            ModBlocks.ROOF_TILES_STAIRS.get().defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.OAK_DOOR
        ),
        new TownPlan.Palette(
            Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.BRICKS.defaultBlockState(),
            Blocks.BRICK_STAIRS.defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(),
            Blocks.DARK_OAK_DOOR
        )
    };
    static final TownPlan.Palette[] TROST_PALETTES = new TownPlan.Palette[]{
        new TownPlan.Palette(
            ModBlocks.PLASTER_OCHRE.get().defaultBlockState(),
            Blocks.STRIPPED_OAK_LOG,
            Blocks.CUT_SANDSTONE.defaultBlockState(),
            ModBlocks.ROOF_TILES.get().defaultBlockState(),
            ModBlocks.ROOF_TILES_STAIRS.get().defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.OAK_DOOR
        ),
        new TownPlan.Palette(
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.BRICKS.defaultBlockState(),
            Blocks.BRICK_STAIRS.defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(),
            Blocks.SPRUCE_DOOR
        ),
        new TownPlan.Palette(
            ModBlocks.PLASTER.get().defaultBlockState(),
            Blocks.STRIPPED_MANGROVE_LOG,
            Blocks.STONE_BRICKS.defaultBlockState(),
            ModBlocks.ROOF_TILES_DARK.get().defaultBlockState(),
            ModBlocks.ROOF_TILES_DARK_STAIRS.get().defaultBlockState(),
            Blocks.MANGROVE_PLANKS.defaultBlockState(),
            Blocks.MANGROVE_DOOR
        ),
        new TownPlan.Palette(
            ModBlocks.PLASTER_CREAM.get().defaultBlockState(),
            Blocks.STRIPPED_SPRUCE_LOG,
            Blocks.GRANITE.defaultBlockState(),
            ModBlocks.ROOF_TILES_SLATE.get().defaultBlockState(),
            ModBlocks.ROOF_TILES_SLATE_STAIRS.get().defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(),
            Blocks.SPRUCE_DOOR
        ),
        new TownPlan.Palette(
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.POLISHED_GRANITE.defaultBlockState(),
            Blocks.POLISHED_GRANITE_STAIRS.defaultBlockState(),
            Blocks.DARK_OAK_PLANKS.defaultBlockState(),
            Blocks.DARK_OAK_DOOR
        )
    };
    static final TownPlan.Palette[] MITRAS_PALETTES = new TownPlan.Palette[]{
        new TownPlan.Palette(
            Blocks.QUARTZ_BLOCK.defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.SMOOTH_STONE.defaultBlockState(),
            Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(),
            Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS.defaultBlockState(),
            Blocks.DARK_OAK_PLANKS.defaultBlockState(),
            Blocks.DARK_OAK_DOOR
        ),
        new TownPlan.Palette(
            Blocks.CALCITE.defaultBlockState(),
            Blocks.STRIPPED_SPRUCE_LOG,
            Blocks.POLISHED_ANDESITE.defaultBlockState(),
            Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.DARK_PRISMARINE_STAIRS.defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(),
            Blocks.SPRUCE_DOOR
        ),
        new TownPlan.Palette(
            Blocks.WHITE_CONCRETE.defaultBlockState(),
            Blocks.STRIPPED_BIRCH_LOG,
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(),
            Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS.defaultBlockState(),
            Blocks.BIRCH_PLANKS.defaultBlockState(),
            Blocks.BIRCH_DOOR
        ),
        new TownPlan.Palette(
            Blocks.SMOOTH_QUARTZ.defaultBlockState(),
            Blocks.DARK_OAK_LOG,
            Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            Blocks.DEEPSLATE_TILES.defaultBlockState(),
            Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(),
            Blocks.DARK_OAK_PLANKS.defaultBlockState(),
            Blocks.DARK_OAK_DOOR
        ),
        new TownPlan.Palette(
            Blocks.CALCITE.defaultBlockState(),
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.SMOOTH_STONE.defaultBlockState(),
            Blocks.PRISMARINE_BRICKS.defaultBlockState(),
            Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.OAK_DOOR
        )
    };
    static final TownPlan.Palette ROYAL = new TownPlan.Palette(
        Blocks.QUARTZ_BRICKS.defaultBlockState(),
        Blocks.QUARTZ_PILLAR,
        Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(),
        Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(),
        Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS.defaultBlockState(),
        Blocks.DARK_OAK_PLANKS.defaultBlockState(),
        Blocks.DARK_OAK_DOOR
    );
    static final TownPlan.Palette STONE = new TownPlan.Palette(
        Blocks.STONE_BRICKS.defaultBlockState(),
        Blocks.STRIPPED_DARK_OAK_LOG,
        Blocks.STONE_BRICKS.defaultBlockState(),
        Blocks.DEEPSLATE_TILES.defaultBlockState(),
        Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(),
        Blocks.SPRUCE_PLANKS.defaultBlockState(),
        Blocks.SPRUCE_DOOR
    );
    private static final Map<Long, TownPlan.Lot> LOTS = new ConcurrentHashMap<>();
    private static final TownPlan.Lot NO_LOT = new TownPlan.Lot(0, 0, TownPlan.LotKind.NONE, 0);
    private static final Block[] CARPETS = new Block[]{
        Blocks.RED_CARPET,
        Blocks.GREEN_CARPET,
        Blocks.BLUE_CARPET,
        Blocks.BROWN_CARPET,
        Blocks.CYAN_CARPET,
        Blocks.PURPLE_CARPET,
        Blocks.ORANGE_CARPET,
        Blocks.GRAY_CARPET
    };
    private static final Block[] POTS = new Block[]{
        Blocks.POTTED_RED_TULIP, Blocks.POTTED_FERN, Blocks.POTTED_POPPY, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_AZURE_BLUET, Blocks.POTTED_SPRUCE_SAPLING
    };

    private TownPlan() {
    }

    static TownPlan.Lot lotAt(int x, int z) {
        if (ShiganshinaLayout.inArena((double)x + 0.5, (double)z + 0.5)) {
            return null;
        }

        int ax = x >= 0 ? x : -x;
        int side = x >= 0 ? 1 : -1;
        if (ax < 8) {
            return null;
        } else {
            int rx = ax - 8;
            int i = rx / 26;
            if (rx % 26 >= 20) {
                return null;
            } else {
                int off = ShiganshinaLayout.districtOffset((double)z);
                int rz = z - (-91 + off);
                if (rz < 0 && off != -1100) {
                    return null;
                } else {
                    int j = Math.floorDiv(rz, 26);
                    if (Math.floorMod(rz, 26) >= 20) {
                        return null;
                    } else {
                        long key = (long)(side + 1) << 60 ^ (long)(i & 1048575) << 40 ^ (long)(j & 1048575) << 20 ^ (long)(off & 1048575);
                        TownPlan.Lot lot = LOTS.computeIfAbsent(key, k -> makeLot(side, i, j, off));
                        return lot == NO_LOT ? null : lot;
                    }
                }
            }
        }
    }

    private static boolean lotValid(int x0, int z0) {
        int[][] corners = new int[][]{{x0, z0}, {x0 + 20 - 1, z0}, {x0, z0 + 20 - 1}, {x0 + 20 - 1, z0 + 20 - 1}};

        for (int[] c : corners) {
            if (ShiganshinaLayout.region((double)c[0] + 0.5, (double)c[1] + 0.5) != ShiganshinaLayout.Region.DISTRICT) {
                return false;
            }

            if (ShiganshinaLayout.distToWall((double)c[0] + 0.5, (double)c[1] + 0.5) < 8.0) {
                return false;
            }
        }

        return true;
    }

    private static double townCentreDist(ShiganshinaLayout.Bulge b, int x0, int z0) {
        double nx = b.cx();
        double nz = b.cz() - -1100.0;
        double len = Math.sqrt(nx * nx + nz * nz);
        double tx = b.cx() + nx / len * b.r() * 0.45;
        double tz = b.cz() + nz / len * b.r() * 0.45;
        return rectDist(x0, z0, tx, tz);
    }

    private static double rectDist(int x0, int z0, double px, double pz) {
        double cx = Math.max((double)x0, Math.min(px, (double)(x0 + 20)));
        double cz = Math.max((double)z0, Math.min(pz, (double)(z0 + 20)));
        return Math.hypot(px - cx, pz - cz);
    }

    private static TownPlan.Lot makeLot(int side, int i, int j, int off) {
        int x0 = side > 0 ? 8 + i * 26 : -(8 + i * 26 + 20) + 1;
        int z0 = -91 + off + j * 26;
        ShiganshinaLayout.Bulge bulge = ShiganshinaLayout.bulgeAt((double)(x0 + 10), (double)(z0 + 10));
        boolean outer = bulge != null && bulge.ring() >= 6;
        boolean mitras = off == -1100 && !outer;
        int seed = WorldNoise.hash(side, i, j, mitras ? 6161 : (outer ? 7171 + bulge.ring() : (off != 0 ? 5151 : 4242)));
        if (!lotValid(x0, z0)) {
            return NO_LOT;
        } else if (outer && townCentreDist(bulge, x0, z0) < 1.0) {
            TownPlan.Lot square = new TownPlan.Lot(x0, z0, TownPlan.LotKind.PLAZA, seed);
            square.local = true;
            return square;
        } else if (mitras && rectDist(x0, z0 - off, 0.0, -2.0) < 26.0) {
            TownPlan.Lot palace = new TownPlan.Lot(x0, z0, TownPlan.LotKind.HOUSES, seed);
            Direction door = side > 0 ? Direction.WEST : Direction.EAST;
            boolean middle = rectDist(x0, z0 - off, 0.0, -2.0) < 9.0;
            if (middle) {
                palace.buildings.add(new TownPlan.Building(x0 + 2, z0 + 2, 16, 16, 17, TownPlan.Roof.HIP, ROYAL, door, 8, -1, -1, 3.0F, true));
            } else {
                palace.buildings.add(new TownPlan.Building(x0 + 1, z0 + 1, 18, 18, 8, TownPlan.Roof.HIP, ROYAL, door, 9, -1, -1, 1.5F, true));
            }

            return palace;
        } else if (outer || !(rectDist(x0, z0 - off, 0.0, -2.0) < 26.0) || side > 0 && i == 1) {
            if (!mitras && rectDist(x0, z0 - off, 0.0, 88.0) < 34.0) {
                return new TownPlan.Lot(x0, z0, TownPlan.LotKind.GATE_SQUARE, seed);
            } else {
                TownPlan.Lot lot = new TownPlan.Lot(x0, z0, TownPlan.LotKind.HOUSES, seed);
                double r = WorldNoise.rand(seed, 1, 0, 7);
                TownPlan.Palette[] pals = mitras
                    ? MITRAS_PALETTES
                    : (outer ? (bulge.zone() == 2 ? MITRAS_PALETTES : (bulge.ring() == 6 ? TROST_PALETTES : PALETTES)) : (off != 0 ? TROST_PALETTES : PALETTES));
                TownPlan.Palette pal = pals[Math.floorMod(seed, pals.length)];
                TownPlan.Palette pal2 = pals[Math.floorMod(seed >> 5, pals.length)];
                TownPlan.Palette pal3 = pals[Math.floorMod(seed >> 9, pals.length)];
                int f = 3 + Math.floorMod(seed >> 3, 4) + (mitras ? 2 : (outer && bulge.zone() == 2 ? 1 : 0));
                if (mitras && WorldNoise.rand(seed, 2, 0, 9) < 0.12) {
                    TownPlan.Lot estate = new TownPlan.Lot(x0, z0, TownPlan.LotKind.GARDEN, seed);
                    estate.buildings
                        .add(new TownPlan.Building(x0 + 3, z0 + 3, 14, 12, 4, TownPlan.Roof.HIP, pal, Direction.SOUTH, 7, x0 + 5, z0 + 5, 1.5F, false));
                    return estate;
                } else if ((!mitras || side <= 0 || i != 2 || j != 0) && (!mitras || side >= 0 || i != 2 || j != 6)) {
                    if (!mitras && off == 0 && side > 0 && i == 1 && j == 4) {
                        // the Training Corps yard, one street east of where recruits arrive
                        return new TownPlan.Lot(x0, z0, TownPlan.LotKind.TRAINING, seed);
                    } else if (!mitras && side > 0 && i == 1 && j == 3) {
                        lot.buildings.add(new TownPlan.Building(x0 + 1, z0 + 7, 6, 6, 10, TownPlan.Roof.HIP, STONE, Direction.WEST, 2, -1, -1, 3.0F, true));
                        lot.buildings
                            .add(new TownPlan.Building(x0 + 7, z0 + 3, 12, 14, 4, TownPlan.Roof.GABLE_X, STONE, Direction.WEST, 2, -1, -1, 1.0F, true));
                        return lot;
                    } else if (!mitras && i == 0 && j == 1) {
                        TownPlan.Palette p = side > 0 ? STONE : PALETTES[1];
                        lot.buildings
                            .add(
                                new TownPlan.Building(
                                    x0 + 1,
                                    z0 + 1,
                                    18,
                                    14,
                                    side > 0 ? 3 : 2,
                                    TownPlan.Roof.HIP,
                                    p,
                                    side > 0 ? Direction.WEST : Direction.EAST,
                                    7,
                                    x0 + 4,
                                    z0 + 4,
                                    1.0F,
                                    false
                                )
                            );
                        return lot;
                    } else {
                        if (r < 0.25) {
                            Direction door = Direction.NORTH;
                            int f1 = 4 + Math.floorMod(seed >> 2, 4);
                            int f2 = 4 + Math.floorMod(seed >> 6, 4);
                            int f3 = 4 + Math.floorMod(seed >> 8, 4);
                            lot.buildings
                                .add(new TownPlan.Building(x0 + 1, z0 + 1, 6, 18, f1, TownPlan.Roof.GABLE_Z, pal, door, 3, x0 + 3, z0 + 12, 1.0F, false));
                            lot.buildings.add(new TownPlan.Building(x0 + 7, z0 + 1, 6, 18, f2, TownPlan.Roof.GABLE_Z, pal2, door, 3, -1, -1, 1.0F, false));
                            lot.buildings
                                .add(new TownPlan.Building(x0 + 13, z0 + 1, 6, 18, f3, TownPlan.Roof.GABLE_Z, pal3, door, 2, x0 + 15, z0 + 6, 1.0F, false));
                        } else if (r < 0.45) {
                            lot.buildings
                                .add(
                                    new TownPlan.Building(x0 + 1, z0 + 1, 18, 8, f, TownPlan.Roof.GABLE_X, pal, Direction.NORTH, 9, x0 + 5, z0 + 3, 1.0F, false)
                                );
                            lot.buildings
                                .add(
                                    new TownPlan.Building(
                                        x0 + 1,
                                        z0 + 11,
                                        18,
                                        8,
                                        f + Math.floorMod(seed >> 11, 2),
                                        TownPlan.Roof.GABLE_X,
                                        pal2,
                                        Direction.SOUTH,
                                        8,
                                        x0 + 14,
                                        z0 + 15,
                                        1.0F,
                                        false
                                    )
                                );
                        } else if (r < 0.58) {
                            lot.buildings
                                .add(
                                    new TownPlan.Building(
                                        x0 + 1, z0 + 1, 18, 18, f + 1, TownPlan.Roof.HIP, pal, Direction.NORTH, 9, x0 + 13, z0 + 5, 1.0F, false
                                    )
                                );
                        } else if (r < 0.78) {
                            boolean stone = Math.floorMod(seed >> 6, 3) == 0;
                            int tf = 9 + Math.floorMod(seed >> 4, 4);
                            boolean east = Math.floorMod(seed >> 12, 2) == 0;
                            int tx = east ? x0 + 11 : x0 + 1;
                            lot.buildings
                                .add(
                                    new TownPlan.Building(
                                        tx, z0 + 1, 8, 8, tf, TownPlan.Roof.HIP, stone ? STONE : pal3, Direction.NORTH, 3, -1, -1, 2.0F, stone
                                    )
                                );
                            lot.buildings
                                .add(
                                    new TownPlan.Building(
                                        east ? x0 + 1 : x0 + 10,
                                        z0 + 1,
                                        9,
                                        18,
                                        f,
                                        TownPlan.Roof.GABLE_Z,
                                        pal,
                                        Direction.NORTH,
                                        4,
                                        east ? x0 + 4 : x0 + 13,
                                        z0 + 12,
                                        1.0F,
                                        false
                                    )
                                );
                            lot.buildings
                                .add(
                                    new TownPlan.Building(
                                        tx,
                                        z0 + 10,
                                        8,
                                        9,
                                        Math.max(2, f - 1),
                                        TownPlan.Roof.GABLE_X,
                                        pal2,
                                        east ? Direction.EAST : Direction.WEST,
                                        4,
                                        -1,
                                        -1,
                                        1.0F,
                                        false
                                    )
                                );
                        } else {
                            if (r < 0.92) {
                                lot.buildings
                                    .add(
                                        new TownPlan.Building(
                                            x0 + 1, z0 + 1, 18, 8, f + 1, TownPlan.Roof.GABLE_X, pal, Direction.NORTH, 4, x0 + 15, z0 + 3, 1.0F, false
                                        )
                                    );
                                lot.buildings
                                    .add(new TownPlan.Building(x0 + 1, z0 + 9, 8, 10, f, TownPlan.Roof.GABLE_Z, pal2, Direction.WEST, 5, -1, -1, 1.0F, false));
                                return lot;
                            }

                            lot = new TownPlan.Lot(x0, z0, TownPlan.LotKind.GARDEN, seed);
                            lot.buildings
                                .add(
                                    new TownPlan.Building(x0 + 3, z0 + 3, 9, 7, 2, TownPlan.Roof.GABLE_X, pal, Direction.SOUTH, 4, x0 + 5, z0 + 4, 1.0F, false)
                                );
                        }

                        return lot;
                    }
                } else {
                    lot.buildings.add(new TownPlan.Building(x0 + 1, z0 + 7, 6, 6, 14, TownPlan.Roof.HIP, ROYAL, Direction.WEST, 2, -1, -1, 3.0F, true));
                    lot.buildings.add(new TownPlan.Building(x0 + 7, z0 + 3, 12, 14, 6, TownPlan.Roof.GABLE_X, ROYAL, Direction.WEST, 2, -1, -1, 1.0F, true));
                    return lot;
                }
            }
        } else {
            return new TownPlan.Lot(x0, z0, TownPlan.LotKind.PLAZA, seed);
        }
    }

    public static void column(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        if (Landmarks.column(col)) {
            return;
        }

        TownPlan.Lot lot = lotAt(x, z);
        if (lot == null) {
            street(col);
            TownPlan.Bridge over = bridgeAt(x, z, null);
            if (over != null) {
                bridge(col, over, x, z);
            }
        } else {
            int lx = x - lot.x0;
            int lz = z - lot.z0;
            switch (lot.kind) {
                case PLAZA:
                    if (!lot.local && StreetLife.inCentralPlaza(x, z)) {
                        StreetLife.centralPlaza(col);
                    } else {
                        plaza(col, lot);
                    }
                    return;
                case GATE_SQUARE:
                    plaza(col, lot);
                    StreetLife.market(col, lx, lz, lot.x0 >= 0 ? 1 : -1);
                    return;
                case TRAINING:
                    TrainingGround.column(col, lx, lz);
                    return;
            }

            TownPlan.Building ring = null;

            for (TownPlan.Building b : lot.buildings) {
                if (b.contains(x, z)) {
                    building(col, b);
                    TownPlan.Bridge door = bridgeAt(x, z, lot);
                    if (door != null && door.door(x, z)) {
                        col.set(BRIDGE_Y + 1, Blocks.AIR.defaultBlockState());
                        col.set(BRIDGE_Y + 2, Blocks.AIR.defaultBlockState());
                    }

                    return;
                }

                if (ring == null && x >= b.x0 - 1 && x <= b.x0 + b.w && z >= b.z0 - 1 && z <= b.z0 + b.d) {
                    ring = b;
                }
            }

            boolean edge = lx == 0 || lz == 0 || lx == 19 || lz == 19;
            double r = WorldNoise.rand(x, 3, z, 99);
            if (edge) {
                col.set(66, r < 0.7 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
                if ((lx == 0 || lx == 19) && (lz == 0 || lz == 19) && Math.floorMod(lot.seed, 3) != 0) {
                    lamp(col);
                }
            } else {
                col.set(66, Blocks.GRASS_BLOCK.defaultBlockState());
                if (lot.kind == TownPlan.LotKind.GARDEN && lx == 15 && lz == 14) {
                    smallTree(col);
                } else if (r < 0.25) {
                    col.set(67, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (r < 0.3) {
                    col.set(67, r < 0.27 ? Blocks.POPPY.defaultBlockState() : Blocks.DANDELION.defaultBlockState());
                } else if (r > 0.97) {
                    col.set(67, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.valueOf(true)));
                }
            }

            if (ring != null) {
                exterior(col, ring, x - ring.x0, z - ring.z0, edge);
            }

            TownPlan.Bridge over = bridgeAt(x, z, lot);
            if (over != null && over.deck(x, z)) {
                bridge(col, over, x, z);
            }
        }
    }

    /** Floor of the walkways: the first-floor level inside the houses, so a walkway steps straight in. */
    static final int BRIDGE_Y = 70;

    /**
     * The walkway touching this column, if any: one from its own lot (across an alley, or out of it), or one coming in
     * from the lot to the west or the north, or one over the street from the lot just west or north of it.
     */
    @Nullable
    static TownPlan.Bridge bridgeAt(int x, int z, @Nullable TownPlan.Lot here) {
        TownPlan.Lot[] owners = here != null
            ? new TownPlan.Lot[]{here, lotAt(x - 26, z), lotAt(x, z - 26)}
            : new TownPlan.Lot[]{lotAt(x - 6, z), lotAt(x, z - 6)};

        for (TownPlan.Lot l : owners) {
            if (l != null) {
                for (TownPlan.Bridge b : bridges(l)) {
                    if (b.deck(x, z) || b.door(x, z)) {
                        return b;
                    }
                }
            }
        }

        return null;
    }

    /** The walkways a lot owns: across alleys between its own houses, and over the side streets to its east and south. */
    private static List<TownPlan.Bridge> bridges(TownPlan.Lot lot) {
        List<TownPlan.Bridge> out = lot.bridges;
        if (out == null) {
            out = new ArrayList<>();
            if (lot.kind == TownPlan.LotKind.HOUSES || lot.kind == TownPlan.LotKind.GARDEN) {
                List<TownPlan.Building> bs = lot.buildings;

                for (int i = 0; i < bs.size(); i++) {
                    for (int j = i + 1; j < bs.size(); j++) {
                        span(out, bs.get(i), bs.get(j), lot.seed * 31 + i * 7 + j, 0.6);
                    }
                }

                TownPlan.Lot east = lotAt(lot.x0 + 36, lot.z0 + 10);
                if (east != null && east.x0 == lot.x0 + 26 && east.z0 == lot.z0) {
                    TownPlan.Building a = edge(lot, 1, true);
                    TownPlan.Building b = edge(east, -1, true);
                    if (a != null && b != null) {
                        span(out, a, b, lot.seed * 17 + 3, 0.3);
                    }
                }

                TownPlan.Lot south = lotAt(lot.x0 + 10, lot.z0 + 36);
                if (south != null && south.z0 == lot.z0 + 26 && south.x0 == lot.x0) {
                    TownPlan.Building a = edge(lot, 1, false);
                    TownPlan.Building b = edge(south, -1, false);
                    if (a != null && b != null) {
                        span(out, a, b, lot.seed * 13 + 5, 0.3);
                    }
                }
            }

            lot.bridges = out;
        }

        return out;
    }

    /** The house standing furthest east (dir 1) or west (dir -1) on a lot - or south / north when not alongX. */
    @Nullable
    private static TownPlan.Building edge(TownPlan.Lot lot, int dir, boolean alongX) {
        TownPlan.Building best = null;

        for (TownPlan.Building b : lot.buildings) {
            int key = alongX ? (dir > 0 ? b.x0 + b.w : -b.x0) : (dir > 0 ? b.z0 + b.d : -b.z0);
            int bestKey = best == null ? Integer.MIN_VALUE : (alongX ? (dir > 0 ? best.x0 + best.w : -best.x0) : (dir > 0 ? best.z0 + best.d : -best.z0));
            if (key > bestKey) {
                best = b;
            }
        }

        return best;
    }

    /** A walkway between two houses if they face each other across a gap of two to eight blocks and both have an upper floor. */
    private static void span(List<TownPlan.Bridge> out, TownPlan.Building a, TownPlan.Building b, int seed, double chance) {
        if (a.floors >= 2 && b.floors >= 2 && !(WorldNoise.rand(seed, 4, 0, 77) >= chance)) {
            for (int axis = 0; axis < 2; axis++) {
                boolean alongX = axis == 0;
                TownPlan.Building lo = (alongX ? a.x0 < b.x0 : a.z0 < b.z0) ? a : b;
                TownPlan.Building hi = lo == a ? b : a;
                int end = alongX ? lo.x0 + lo.w : lo.z0 + lo.d;
                int start = alongX ? hi.x0 : hi.z0;
                int gap = start - end;
                // the centre line keeps the three-wide deck clear of both houses' corners
                int from = Math.max(alongX ? lo.z0 : lo.x0, alongX ? hi.z0 : hi.x0) + 2;
                int to = Math.min(alongX ? lo.z0 + lo.d : lo.x0 + lo.w, alongX ? hi.z0 + hi.d : hi.x0 + hi.w) - 3;
                if (gap >= 2 && gap <= 8 && to >= from) {
                    out.add(new TownPlan.Bridge(alongX, end, start - 1, (from + to) / 2, lo.pal));
                    return;
                }
            }
        }
    }

    /**
     * One column of a walkway: a plank floor with a timber beam and a railing along each side, and a lantern hung under
     * the middle. It has no roof on purpose - an open span is light enough to carry itself, and only comes down when one
     * of its houses does.
     */
    private static void bridge(ColumnBuffer col, TownPlan.Bridge br, int x, int z) {
        if (br.deck(x, z)) {
            int a = br.alongX ? x : z;
            boolean side = (br.alongX ? z : x) != br.c;
            Axis axis = br.alongX ? Axis.X : Axis.Z;
            col.set(BRIDGE_Y, side ? log(br.pal.timber(), axis) : br.pal.floor());
            col.set(BRIDGE_Y + 1, side ? railing(br.pal.timber(), br.alongX) : Blocks.AIR.defaultBlockState());
            col.set(BRIDGE_Y + 2, Blocks.AIR.defaultBlockState());
            if (!side && a == (br.a0 + br.a1) / 2) {
                col.set(BRIDGE_Y - 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.valueOf(true)));
            }
        }
    }

    private static BlockState railing(Block timber, boolean alongX) {
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(timber).getPath();
        Block fence = name.contains("dark_oak")
            ? Blocks.DARK_OAK_FENCE
            : (name.contains("spruce") ? Blocks.SPRUCE_FENCE : (name.contains("birch") ? Blocks.BIRCH_FENCE : Blocks.OAK_FENCE));
        BlockState s = fence.defaultBlockState();
        return alongX
            ? s.setValue(CrossCollisionBlock.EAST, Boolean.valueOf(true)).setValue(CrossCollisionBlock.WEST, Boolean.valueOf(true))
            : s.setValue(CrossCollisionBlock.NORTH, Boolean.valueOf(true)).setValue(CrossCollisionBlock.SOUTH, Boolean.valueOf(true));
    }

    /**
     * What hangs off a house on the outside, in the one-block ring around its walls: roof eaves and gable verges,
     * shutters beside the upper windows, window boxes with flowers under them, and a small roof over the front door.
     */
    private static void exterior(ColumnBuffer col, TownPlan.Building b, int bx, int bz, boolean street) {
        boolean outX = bx < 0 || bx >= b.w;
        boolean outZ = bz < 0 || bz >= b.d;
        int top = b.wallTop();
        TownPlan.Palette pal = b.pal;
        eaves(col, b, bx, bz, outX, outZ, top);
        if (outX && outZ) {
            return;
        }

        Direction side = outZ ? (bz < 0 ? Direction.NORTH : Direction.SOUTH) : (bx < 0 ? Direction.WEST : Direction.EAST);
        int along = outZ ? bx : bz;
        int len = outZ ? b.w : b.d;
        int door = Math.min(b.doorAlong, len - 2);
        int look = WorldNoise.hash(b.x0, b.floors, b.z0, 2121);
        boolean inner = along > 0 && along < len - 1;
        if (street && inner && !b.solidWalls) {
            if (side == b.doorSide && b.floors >= 2 && Math.floorMod(look, 3) == 0) {
                // a shop: a striped awning along the ground floor and its wares out on the pavement
                Block stripe = AWNING_STRIPES[Math.floorMod(look >> 3, AWNING_STRIPES.length)];
                if (!jettied(b)) {
                    setIfAir(col, 70, (along & 1) == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : stripe.defaultBlockState());
                }
                if (Math.abs(along - door) == 3 || Math.abs(along - door) == 4 && Math.floorMod(look >> 5, 2) == 0) {
                    setIfAir(col, 67, Math.floorMod(along + look, 3) == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
                    setIfAir(col, 68, POTS[Math.floorMod(along * 7 + look, POTS.length)].defaultBlockState());
                }
            } else if (Math.abs(along - door) >= 2 && WorldNoise.rand(col.x, 9, col.z, 4343) < 0.05) {
                // a barrel or two left by the wall
                setIfAir(col, 67, Blocks.BARREL.defaultBlockState());
            }

            if (b.floors >= 4 && Math.floorMod(look >> 7, 3) != 0) {
                // a narrow balcony in front of the middle window of one upper floor
                int k = 2 + Math.floorMod(look >> 9, b.floors - 3);
                int mid = middleWindow(len, b);
                if (mid > 0 && Math.abs(along - mid) <= 1) {
                    int floor = 66 + 4 * k;
                    boolean alongX = side.getAxis() == Axis.Z;
                    setIfAir(col, floor, slabOf(pal.timber).setValue(SlabBlock.TYPE, SlabType.TOP));
                    setIfAir(col, floor + 1, railing(pal.timber, alongX));
                    if (along == mid) {
                        setIfAir(col, floor - 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                    }
                }
            }
        }
        if (side == b.doorSide && Math.abs(along - door) <= 1 && !jettied(b)) {
            setIfAir(col, 69, pal.roofStairs.setValue(StairBlock.FACING, side.getOpposite()));
            if (along == door + 1) {
                setIfAir(col, 68, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }

        if (!b.solidWalls && len > 4) {
            BlockState trapdoor = shutterFor(pal.timber).defaultBlockState().setValue(TrapDoorBlock.FACING, side);

            // each house keeps to one style, so a street reads as a row of different houses rather than one busy wall:
            // shutters on every window, only under the roof, or none; flower boxes or bare sills
            int shutters = Math.floorMod(look >> 11, 5);
            boolean boxes = Math.floorMod(look >> 14, 5) < 2;

            for (int k = 1; k < b.floors; k++) {
                int floor = 66 + 4 * k;
                boolean shut = shutters >= 3 || shutters == 2 && k == b.floors - 1;
                if (windowSlot(along - 1, len, b) || windowSlot(along + 1, len, b)) {
                    if (shut) {
                        BlockState shutter = trapdoor.setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.BOTTOM);
                        setIfAir(col, floor + 2, shutter);
                        setIfAir(col, floor + 3, shutter);
                    }
                } else if (windowSlot(along, len, b)) {
                    setIfAir(col, floor + 1, trapdoor.setValue(TrapDoorBlock.HALF, Half.TOP));
                    double r = WorldNoise.rand(col.x, floor, col.z, 77);
                    if (boxes && r < 0.75) {
                        setIfAir(col, floor + 2, POTS[Math.floorMod((int)(r * 1000.0), POTS.length)].defaultBlockState());
                    }
                }
            }
        }

        if (street && inner && !b.solidWalls) {
            // one moulding where the timber storeys stand on the stone ground floor (a jetty is its own), and the rafter
            // ends under the eaves
            BlockState beam = stairsOf(pal.timber).setValue(StairBlock.FACING, side.getOpposite()).setValue(StairBlock.HALF, Half.TOP);
            if (b.floors >= 2 && !(jettied(b) && side == b.doorSide)) {
                setIfAir(col, 70, beam);
            }

            boolean verge = b.roof == TownPlan.Roof.GABLE_X ? outX : b.roof == TownPlan.Roof.GABLE_Z && outZ;
            if (!verge && (along & 1) == 0) {
                setIfAir(col, top - 1, beam);
            }
        }
    }

    private static void eaves(ColumnBuffer col, TownPlan.Building b, int bx, int bz, boolean outX, boolean outZ, int top) {
        TownPlan.Palette pal = b.pal;
        boolean verge = b.roof == TownPlan.Roof.GABLE_X ? outX && !outZ : b.roof == TownPlan.Roof.GABLE_Z && outZ && !outX;
        if (!verge) {
            // eaves: the roof runs one step down past the wall instead of stopping flush with it
            Direction up = outZ ? (bz < 0 ? Direction.SOUTH : Direction.NORTH) : (bx < 0 ? Direction.EAST : Direction.WEST);
            setIfAir(col, top, pal.roofStairs.setValue(StairBlock.FACING, up));
        } else {
            // verge: the roof profile continues one block past the gable wall
            int cbx = Math.max(0, Math.min(bx, b.w - 1));
            int cbz = Math.max(0, Math.min(bz, b.d - 1));
            int n = b.roof == TownPlan.Roof.GABLE_X ? b.d : b.w;
            int c = b.roof == TownPlan.Roof.GABLE_X ? cbz : cbx;
            int e = Math.min(c, n - 1 - c);
            int maxE = (n - 1) / 2;
            Direction up = b.roof == TownPlan.Roof.GABLE_X
                ? (c < n - 1 - c ? Direction.SOUTH : Direction.NORTH)
                : (c < n - 1 - c ? Direction.EAST : Direction.WEST);
            int surface = top + 1 + Math.round((float)e * b.pitch);
            if (e >= maxE) {
                setIfAir(col, surface, pal.roof);
            } else {
                setIfAir(col, surface, pal.roofStairs.setValue(StairBlock.FACING, up));
                if (b.pitch > 1.0F) {
                    for (int y = surface - Math.round(b.pitch) + 1; y < surface; y++) {
                        setIfAir(col, y, pal.roof);
                    }
                }
            }
        }
    }

    /** A window in this bay; houses with crosses keep every other bay for a cross. */
    private static boolean windowSlot(int along, int len, TownPlan.Building b) {
        return along > 0 && along < len - 1 && along % 3 == 1 && len > 4 && (timberStyle(b) != 1 || along / 3 % 2 == 0);
    }

    /** The window slot nearest the middle of a wall, or -1. */
    private static int middleWindow(int len, TownPlan.Building b) {
        int best = -1;
        for (int a = 1; a < len - 1; a++) {
            if (windowSlot(a, len, b) && (best < 0 || Math.abs(2 * a - (len - 1)) < Math.abs(2 * best - (len - 1)))) {
                best = a;
            }
        }

        return best;
    }

    private static final Block[] AWNING_STRIPES = new Block[]{
        Blocks.RED_WOOL, Blocks.GREEN_WOOL, Blocks.BLUE_WOOL, Blocks.BROWN_WOOL, Blocks.ORANGE_WOOL, Blocks.CYAN_WOOL
    };

    private static BlockState slabOf(Block timber) {
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(timber).getPath();
        if (name.contains("dark_oak")) {
            return Blocks.DARK_OAK_SLAB.defaultBlockState();
        } else if (name.contains("mangrove")) {
            return Blocks.MANGROVE_SLAB.defaultBlockState();
        } else if (name.contains("birch")) {
            return Blocks.BIRCH_SLAB.defaultBlockState();
        } else {
            return name.contains("quartz") ? Blocks.QUARTZ_SLAB.defaultBlockState() : Blocks.SPRUCE_SLAB.defaultBlockState();
        }
    }

    private static void setIfAir(ColumnBuffer col, int y, BlockState state) {
        if (col.isAir(y)) {
            col.set(y, state);
        }
    }

    private static Block shutterFor(Block timber) {
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(timber).getPath();
        if (name.contains("dark_oak")) {
            return Blocks.DARK_OAK_TRAPDOOR;
        } else if (name.contains("mangrove")) {
            return Blocks.MANGROVE_TRAPDOOR;
        } else {
            return name.contains("birch") ? Blocks.BIRCH_TRAPDOOR : Blocks.SPRUCE_TRAPDOOR;
        }
    }

    private static void street(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        if (StreetLife.inCentralPlaza(x, z)) {
            StreetLife.centralPlaza(col);
            return;
        }

        double r = WorldNoise.rand(x, 1, z, 5);
        BlockState s;
        if (Math.abs(x) < 8) {
            if (Math.abs(x) <= 1) {
                s = Blocks.POLISHED_ANDESITE.defaultBlockState();
            } else {
                s = r < 0.75
                    ? Blocks.STONE_BRICKS.defaultBlockState()
                    : (r < 0.9 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            }
        } else {
            s = r < 0.6
                ? Blocks.COBBLESTONE.defaultBlockState()
                : (
                    r < 0.85
                        ? Blocks.ANDESITE.defaultBlockState()
                        : (r < 0.95 ? Blocks.STONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState())
                );
        }

        if (ShiganshinaLayout.distToWall((double)x + 0.5, (double)z + 0.5) < 7.0 && Math.abs(x) >= 8) {
            s = r < 0.7 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            col.set(66, s);
        } else if (StreetLife.gateMarket(col) || StreetLife.wallCamp(col)) {
            // built there
        } else {
            col.set(66, s);
            if (Math.abs(x) == 7 && Math.floorMod(z, 16) == 0) {
                lamp(col);
            }

            if (Math.abs(x) < 8) {
                StreetLife.avenue(col);
            }
        }
    }

    private static void plaza(ColumnBuffer col, TownPlan.Lot lot) {
        int x = col.x;
        int z = col.z;
        double dx = lot.local ? (double)x + 0.5 - (double)(lot.x0 + 10) : (double)x + 0.5 - 0.0;
        double dz = lot.local ? (double)z + 0.5 - (double)(lot.z0 + 10) : (double)z + 0.5 - -2.0 - (double)ShiganshinaLayout.districtOffset((double)z);
        double d = Math.sqrt(dx * dx + dz * dz);
        boolean ring = (int)d % 6 == 0;
        col.set(66, ring ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
        if (lot.kind == TownPlan.LotKind.PLAZA && d < 5.5) {
            if (d >= 4.5) {
                col.set(67, Blocks.STONE_BRICK_WALL.defaultBlockState());
            } else if (d < 1.0) {
                col.fill(67, 69, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                col.set(70, Blocks.LANTERN.defaultBlockState());
            } else {
                col.set(66, Blocks.WATER.defaultBlockState());
                col.set(65, Blocks.STONE_BRICKS.defaultBlockState());
            }
        }

        if (WorldNoise.rand(x, 2, z, 31) < 0.006 && d > 7.0) {
            lamp(col);
        }
    }

    static void lamp(ColumnBuffer col) {
        col.fill(67, 69, Blocks.DARK_OAK_FENCE.defaultBlockState());
        col.set(70, Blocks.LANTERN.defaultBlockState());
    }

    private static void smallTree(ColumnBuffer col) {
        col.fill(67, 70, Blocks.OAK_LOG.defaultBlockState());
        col.fill(71, 72, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.valueOf(true)));
    }

    private static BlockState log(Block b, Axis axis) {
        return b.defaultBlockState().hasProperty(RotatedPillarBlock.AXIS)
            ? b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis)
            : b.defaultBlockState();
    }

    private static BlockState pane(boolean alongX) {
        BlockState s = Blocks.GLASS_PANE.defaultBlockState();
        return alongX
            ? s.setValue(CrossCollisionBlock.EAST, Boolean.valueOf(true)).setValue(CrossCollisionBlock.WEST, Boolean.valueOf(true))
            : s.setValue(CrossCollisionBlock.NORTH, Boolean.valueOf(true)).setValue(CrossCollisionBlock.SOUTH, Boolean.valueOf(true));
    }

    private static void building(ColumnBuffer col, TownPlan.Building b) {
        int bx = col.x - b.x0;
        int bz = col.z - b.z0;
        boolean ex = bx == 0 || bx == b.w - 1;
        boolean ez = bz == 0 || bz == b.d - 1;
        boolean perim = ex || ez;
        boolean corner = ex && ez;
        TownPlan.Palette pal = b.pal;
        int top = b.wallTop();
        Direction wallSide = null;
        int along = 0;
        int len = 0;
        if (perim && !corner) {
            if (bz == 0) {
                wallSide = Direction.NORTH;
            } else if (bz == b.d - 1) {
                wallSide = Direction.SOUTH;
            } else if (bx == 0) {
                wallSide = Direction.WEST;
            } else {
                wallSide = Direction.EAST;
            }

            boolean alongX = wallSide.getAxis() == Axis.Z;
            along = alongX ? bx : bz;
            len = alongX ? b.w : b.d;
        }

        boolean wallAlongX = wallSide != null && wallSide.getAxis() == Axis.Z;
        boolean jetty = jettied(b);
        // the jettied front: its ground floor stands one block in, the storeys above run out over the street
        boolean front = jetty && wallSide == b.doorSide;
        boolean frontCorner = jetty && corner && depthFrom(b, bx, bz, b.doorSide) == 0;
        int frontLen = b.doorSide.getAxis() == Axis.Z ? b.w : b.d;
        int door = Math.min(b.doorAlong, (perim ? len : frontLen) - 2);
        int style = timberStyle(b);
        col.set(66, perim ? baseAt(pal, col.x, 66, col.z) : pal.floor);

        for (int y = 67; y <= top; y++) {
            int ly = y - 66;
            int fy = ly % 4;
            if (perim) {
                BlockState s;
                if (!b.solidWalls) {
                    if (front && ly <= 3) {
                        // under the jetty: open, with a carved bracket under every other joist
                        boolean bracket = ly == 3 && (along & 1) == 0 && along != door && along != door + 1;
                        s = bracket ? stairsOf(pal.timber).setValue(StairBlock.FACING, wallSide.getOpposite()).setValue(StairBlock.HALF, Half.TOP) : AIR;
                        if (ly == 3 && along == door + 1) {
                            s = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
                        }
                    } else if (frontCorner && ly <= 4) {
                        s = log(pal.timber, Axis.Y);
                    } else if (ly <= 4 && !front) {
                        s = baseAt(pal, col.x, y, col.z);
                    } else if (corner) {
                        s = log(pal.timber, Axis.Y);
                    } else if (fy == 0) {
                        s = log(pal.timber, wallAlongX ? Axis.X : Axis.Z);
                    } else if (along % 3 == 0 && along > 0 && along < len - 1) {
                        // upright studs between the windows: a proper half-timbered frame, not just floor beams
                        s = log(pal.timber, Axis.Y);
                    } else {
                        s = infill(pal, style, along, fy, len, b);
                    }
                } else {
                    s = corner || fy == 0 && ly > 4 ? log(pal.timber, Axis.Y) : pal.base;
                    if (corner) {
                        s = pal.base;
                    }
                }

                if (wallSide != null && !(front && ly <= 3)) {
                    boolean windowSlot = windowSlot(along, len, b);
                    if (b.solidWalls) {
                        windowSlot = along > 1 && along < len - 2 && along % 4 == 2;
                    }

                    if (windowSlot && (fy == 2 || fy == 3) && y < top) {
                        s = pane(wallAlongX);
                    }

                    if (wallSide == b.doorSide && along == door && (ly == 1 || ly == 2)) {
                        s = pal.door
                            .defaultBlockState()
                            .setValue(DoorBlock.FACING, wallSide)
                            .setValue(DoorBlock.HALF, ly == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
                    }
                }

                col.set(y, s);
            } else if (fy == 0) {
                col.set(y, pal.floor);
            }
        }

        if (perim) {
            col.set(top, corner ? log(pal.timber, Axis.Y) : log(pal.timber, wallAlongX ? Axis.X : Axis.Z));
        }

        if (b.solidWalls && perim) {
            col.set(top, pal.base);
        }

        if (!perim) {
            furnish(col, b, bx, bz);
            if (jetty && depthFrom(b, bx, bz, b.doorSide) == 1) {
                insetWall(col, b, alongWall(bx, bz, b.doorSide), frontLen, door);
            }
        }

        roof(col, b, bx, bz, top);
    }

    /** The ground floor wall a jetty stands out over: stone, windows, and the door. */
    private static void insetWall(ColumnBuffer col, TownPlan.Building b, int along, int len, int door) {
        Direction side = b.doorSide;
        boolean alongX = side.getAxis() == Axis.Z;
        for (int y = 67; y <= 69; y++) {
            BlockState s = baseAt(b.pal, col.x, y, col.z);
            if (along == door && y <= 68) {
                s = b.pal.door
                    .defaultBlockState()
                    .setValue(DoorBlock.FACING, side)
                    .setValue(DoorBlock.HALF, y == 67 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
            } else if (windowSlot(along, len, b) && y == 68 && Math.abs(along - door) > 1) {
                s = pane(alongX);
            }

            col.set(y, s);
        }
    }

    /**
     * Whether a house has a jettied front: timber houses of two storeys or more, on two in three plots, as long as the
     * front is wide enough to carry it.
     */
    static boolean jettied(TownPlan.Building b) {
        int frontLen = b.doorSide.getAxis() == Axis.Z ? b.w : b.d;
        return !b.solidWalls && b.floors >= 2 && frontLen >= 5 && Math.floorMod(WorldNoise.hash(b.x0, 5, b.z0, 3131), 3) != 0;
    }

    /**
     * How the panels between the studs are filled: 0 plain render, 1 St. Andrew's crosses in the panels between the
     * windows, 2 a chevron under every window. Crosses and chevrons need plaster that comes with braces.
     */
    private static int timberStyle(TownPlan.Building b) {
        if (b.solidWalls || bracesFor(b.pal, false) == null) {
            return 0;
        } else {
            return Math.floorMod(WorldNoise.hash(b.x0, 7, b.z0, 5151), 3);
        }
    }

    private static BlockState infill(TownPlan.Palette pal, int style, int along, int fy, int len, TownPlan.Building b) {
        int col = along % 3;
        if (style == 1 && !windowSlot(along - col + 1, len, b) && fy >= 2) {
            // an X across the upper two rows of a panel without a window
            boolean leftHalf = col == 1;
            boolean upper = fy == 3;
            return bracesFor(pal, leftHalf == upper);
        } else if (style == 2 && fy == 1 && col != 0) {
            // a chevron in the parapet under the window: up from the left post, down to the right one
            return bracesFor(pal, col == 2);
        } else {
            return pal.plaster;
        }
    }

    /** The brace that goes with this plaster: "/" or, mirrored, "\"; null when the plaster has none. */
    @Nullable
    static BlockState bracesFor(TownPlan.Palette pal, boolean mirrored) {
        if (pal.plaster.is(ModBlocks.PLASTER.get())) {
            return (mirrored ? ModBlocks.PLASTER_BRACE_MIRRORED : ModBlocks.PLASTER_BRACE).get().defaultBlockState();
        } else if (pal.plaster.is(ModBlocks.PLASTER_CREAM.get())) {
            return (mirrored ? ModBlocks.PLASTER_CREAM_BRACE_MIRRORED : ModBlocks.PLASTER_CREAM_BRACE).get().defaultBlockState();
        } else {
            return null;
        }
    }

    /** How far (bx, bz) lies in from a house's wall on that side: 0 in the wall itself. */
    static int depthFrom(TownPlan.Building b, int bx, int bz, Direction side) {
        return switch (side) {
            case NORTH -> bz;
            case SOUTH -> b.d - 1 - bz;
            case WEST -> bx;
            default -> b.w - 1 - bx;
        };
    }

    static int alongWall(int bx, int bz, Direction side) {
        return side.getAxis() == Axis.Z ? bx : bz;
    }

    /** A plinth laid by hand: mostly the palette's stone, here and there a cracked, mossy or odd one. */
    private static BlockState baseAt(TownPlan.Palette pal, int x, int y, int z) {
        double r = WorldNoise.rand(x, y, z, 6262);
        if (pal.base.is(Blocks.STONE_BRICKS)) {
            return r < 0.12 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : (r < 0.2 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : pal.base);
        } else if (pal.base.is(Blocks.COBBLESTONE)) {
            return r < 0.15 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : (r < 0.22 ? Blocks.ANDESITE.defaultBlockState() : pal.base);
        } else if (pal.base.is(Blocks.ANDESITE) || pal.base.is(Blocks.POLISHED_ANDESITE)) {
            return r < 0.15 ? Blocks.COBBLESTONE.defaultBlockState() : pal.base;
        } else {
            return pal.base;
        }
    }

    static BlockState stairsOf(Block timber) {
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(timber).getPath();
        Block b;
        if (name.contains("dark_oak")) {
            b = Blocks.DARK_OAK_STAIRS;
        } else if (name.contains("mangrove")) {
            b = Blocks.MANGROVE_STAIRS;
        } else if (name.contains("birch")) {
            b = Blocks.BIRCH_STAIRS;
        } else if (name.contains("quartz")) {
            b = Blocks.QUARTZ_STAIRS;
        } else if (name.contains("spruce")) {
            b = Blocks.SPRUCE_STAIRS;
        } else {
            b = Blocks.OAK_STAIRS;
        }

        return b.defaultBlockState();
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static void furnish(ColumnBuffer col, TownPlan.Building b, int bx, int bz) {
        if (b.w >= 5 && b.d >= 5) {
            boolean west = bx == 1;
            boolean east = bx == b.w - 2;
            boolean north = bz == 1;
            boolean south = bz == b.d - 2;
            boolean nearWall = west || east || north || south;
            Direction inward = west ? Direction.EAST : (east ? Direction.WEST : (north ? Direction.SOUTH : Direction.NORTH));

            for (int k = 0; k < b.floors; k++) {
                int fy = 66 + 4 * k;
                if (k != 0 || !nearDoor(b, bx, bz)) {
                    int room = WorldNoise.hash(b.x0, k, b.z0, 7771);
                    double r = WorldNoise.rand(col.x, k, col.z, 313);
                    int theme = Math.floorMod(room, 5);
                    if (k == 0 && theme == 0) {
                        theme = 1;
                    }

                    if (nearWall) {
                        wallPiece(col, fy, theme, r, inward, WorldNoise.rand(col.x, k, col.z, 919));
                    } else {
                        int tx = Math.floorMod(bx, 5);
                        int tz = Math.floorMod(bz, 5);
                        boolean tableX = bx >= 2 && bx <= b.w - 3;
                        boolean tableZ = bz >= 2 && bz <= b.d - 3;
                        if (tx == 2 && tz == 2 && tableX && tableZ) {
                            table(col, fy, r);
                            col.set(fy + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.valueOf(true)));
                        } else {
                            Block chair = !b.pal.floor.is(Blocks.SPRUCE_PLANKS) && !b.pal.floor.is(Blocks.DARK_OAK_PLANKS)
                                ? Blocks.OAK_STAIRS
                                : Blocks.SPRUCE_STAIRS;
                            if (tz == 2
                                && (tx == 1 || tx == 3)
                                && bz >= 2
                                && bz <= b.d - 3
                                && bx + (tx == 1 ? 1 : -1) <= b.w - 3
                                && bx + (tx == 1 ? 1 : -1) >= 2
                                && r < 0.8) {
                                col.set(fy + 1, chair.defaultBlockState().setValue(StairBlock.FACING, tx == 1 ? Direction.WEST : Direction.EAST));
                            } else if (tx == 2
                                && (tz == 1 || tz == 3)
                                && bx >= 2
                                && bx <= b.w - 3
                                && bz + (tz == 1 ? 1 : -1) <= b.d - 3
                                && bz + (tz == 1 ? 1 : -1) >= 2
                                && r < 0.35) {
                                col.set(fy + 1, chair.defaultBlockState().setValue(StairBlock.FACING, tz == 1 ? Direction.NORTH : Direction.SOUTH));
                            } else if ((room >> 4 & 3) != 0) {
                                col.set(fy + 1, CARPETS[Math.floorMod(room >> 7, CARPETS.length)].defaultBlockState());
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean nearDoor(TownPlan.Building b, int bx, int bz) {
        int along = Math.min(b.doorAlong, (b.doorSide.getAxis() == Axis.Z ? b.w : b.d) - 2);
        int dx;
        int dz;
        switch (b.doorSide) {
            case NORTH:
                dx = along;
                dz = 1;
                break;
            case SOUTH:
                dx = along;
                dz = b.d - 2;
                break;
            case WEST:
                dx = 1;
                dz = along;
                break;
            default:
                dx = b.w - 2;
                dz = along;
        }

        return Math.abs(bx - dx) <= 1 && Math.abs(bz - dz) <= 1;
    }

    private static void table(ColumnBuffer col, int fy, double r) {
        col.set(fy + 1, Blocks.DARK_OAK_FENCE.defaultBlockState());
        if (r < 0.3) {
            col.set(fy + 2, POTS[(int)(r * 20.0) % POTS.length].defaultBlockState());
        } else if (r < 0.45) {
            col.set(fy + 2, Blocks.CANDLE.defaultBlockState());
        } else {
            col.set(fy + 2, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
        }
    }

    private static void wallPiece(ColumnBuffer col, int fy, int theme, double r, Direction inward, double r2) {
        float fill = theme == 0 ? 0.8F : 0.45F;
        if (!(r >= (double)fill)) {
            double q = r / (double)fill;
            BlockState a = null;
            BlockState top = null;
            switch (theme) {
                case 0:
                    a = Blocks.BOOKSHELF.defaultBlockState();
                    top = q < 0.75 ? Blocks.BOOKSHELF.defaultBlockState() : null;
                    if (q > 0.92) {
                        a = Blocks.CRAFTING_TABLE.defaultBlockState();
                        top = Blocks.CANDLE.defaultBlockState();
                    }
                    break;
                case 1:
                    if (q < 0.25) {
                        a = Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, inward);
                    } else if (q < 0.4) {
                        a = Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, inward);
                    } else if (q < 0.6) {
                        a = Blocks.BARREL.defaultBlockState();
                        top = r2 < 0.4 ? Blocks.BARREL.defaultBlockState() : null;
                    } else if (q < 0.75) {
                        a = Blocks.CAULDRON.defaultBlockState();
                    } else if (q < 0.9) {
                        a = Blocks.CRAFTING_TABLE.defaultBlockState();
                        top = r2 < 0.5 ? Blocks.FLOWER_POT.defaultBlockState() : null;
                    } else {
                        a = Blocks.COMPOSTER.defaultBlockState();
                    }
                    break;
                case 2:
                    if (q < 0.18) {
                        a = Blocks.SMITHING_TABLE.defaultBlockState();
                    } else if (q < 0.34) {
                        a = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, inward.getClockWise());
                    } else if (q < 0.5) {
                        a = Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACING, inward);
                    } else if (q < 0.62) {
                        a = Blocks.FLETCHING_TABLE.defaultBlockState();
                    } else if (q < 0.74) {
                        a = Blocks.LOOM.defaultBlockState().setValue(LoomBlock.FACING, inward);
                    } else if (q < 0.86) {
                        a = Blocks.STONECUTTER.defaultBlockState().setValue(StonecutterBlock.FACING, inward);
                    } else {
                        a = Blocks.BARREL.defaultBlockState();
                        top = Blocks.LANTERN.defaultBlockState();
                    }
                    break;
                case 3:
                    if (q < 0.5) {
                        a = Blocks.BARREL.defaultBlockState();
                        top = r2 < 0.6 ? Blocks.BARREL.defaultBlockState() : null;
                    } else if (q < 0.8) {
                        a = Blocks.HAY_BLOCK.defaultBlockState();
                        top = r2 < 0.5 ? Blocks.HAY_BLOCK.defaultBlockState() : null;
                    } else {
                        a = Blocks.COMPOSTER.defaultBlockState();
                    }
                    break;
                default:
                    if (q < 0.3) {
                        a = Blocks.BOOKSHELF.defaultBlockState();
                        top = r2 < 0.5 ? Blocks.FLOWER_POT.defaultBlockState() : null;
                    } else if (q < 0.55) {
                        a = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
                        top = r2 < 0.5 ? Blocks.CANDLE.defaultBlockState() : null;
                    } else if (q < 0.75) {
                        a = Blocks.WHITE_WOOL.defaultBlockState();
                        top = Blocks.RED_CARPET.defaultBlockState();
                    } else {
                        a = Blocks.POTTED_FERN.defaultBlockState();
                    }
            }

            col.set(fy + 1, a);
            if (top != null) {
                col.set(fy + 2, top);
            }
        }
    }

    private static void roof(ColumnBuffer col, TownPlan.Building b, int bx, int bz, int top) {
        TownPlan.Palette pal = b.pal;
        boolean gableEnd = false;
        int e;
        int maxE;
        Direction up;
        switch (b.roof) {
            case GABLE_X: {
                int n = b.d;
                e = Math.min(bz, n - 1 - bz);
                maxE = (n - 1) / 2;
                up = bz < n - 1 - bz ? Direction.SOUTH : Direction.NORTH;
                gableEnd = bx == 0 || bx == b.w - 1;
                break;
            }
            case GABLE_Z: {
                int n = b.w;
                e = Math.min(bx, n - 1 - bx);
                maxE = (n - 1) / 2;
                up = bx < n - 1 - bx ? Direction.EAST : Direction.WEST;
                gableEnd = bz == 0 || bz == b.d - 1;
                break;
            }
            default:
                int ex = Math.min(bx, b.w - 1 - bx);
                int ez = Math.min(bz, b.d - 1 - bz);
                e = Math.min(ex, ez);
                maxE = Math.min((b.w - 1) / 2, (b.d - 1) / 2);
                if (ez <= ex) {
                    up = bz < b.d - 1 - bz ? Direction.SOUTH : Direction.NORTH;
                } else {
                    up = bx < b.w - 1 - bx ? Direction.EAST : Direction.WEST;
                }
        }

        int rise = Math.round((float)e * b.pitch);
        int surface = top + 1 + rise;
        if (gableEnd) {
            boolean timber = !b.solidWalls && b.pitch == 1.0F;
            BlockState rake = timber ? bracesFor(pal, !rakeRises(b, bx, bz, up)) : null;
            boolean gableX = b.roof == TownPlan.Roof.GABLE_X;
            for (int y = top + 1; y < surface; y++) {
                BlockState s = pal.plaster;
                if (timber && e == maxE) {
                    s = log(pal.timber, Axis.Y);
                } else if (timber && y == top + 2 && e == maxE - 1 && maxE >= 3) {
                    s = pane(!gableX);
                } else if (!timber && y == top + 2 && e == maxE && maxE >= 2) {
                    s = Blocks.GLASS.defaultBlockState();
                } else if (rake != null && y == surface - 1) {
                    s = rake;
                }

                col.set(y, s);
            }
        } else if (b.pitch > 1.0F) {
            col.fill(top + 1, surface - 1, pal.roof);
        }

        if (e >= maxE) {
            col.set(surface, pal.roof);
            if (b.pitch > 1.0F && bx == b.w / 2 && bz == b.d / 2) {
                col.set(surface + 1, Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        } else {
            col.set(surface, pal.roofStairs.setValue(StairBlock.FACING, up));
            if (b.pitch > 1.0F) {
                col.fill(surface - Math.round(b.pitch) + 1, surface - 1, pal.roof);
            }
        }

        dormer(col, b, bx, bz, top);
        if (col.x == b.chimneyX && col.z == b.chimneyZ) {
            col.fill(top + 1, surface + 2, Blocks.BRICKS.defaultBlockState());
            col.set(surface + 3, Blocks.BRICK_SLAB.defaultBlockState());
            // about a third of the hearths are lit: smoke rises through the cap and the town looks lived in
            if (WorldNoise.rand(b.chimneyX, 5, b.chimneyZ, 31) < 0.35) {
                col.set(surface + 2, Blocks.CAMPFIRE.defaultBlockState());
            }
        }
    }

    /**
     * Dormer windows along the slopes of plain pitched roofs: a little gable three wide, its face flush with the wall
     * below and its own ridge running back into the main roof, every four blocks along the eaves.
     */
    private static void dormer(ColumnBuffer col, TownPlan.Building b, int bx, int bz, int top) {
        if (b.solidWalls || b.pitch != 1.0F || Math.floorMod(WorldNoise.hash(b.x0, 3, b.z0, 8181), 5) >= 3) {
            return;
        }

        // a hipped roof only takes them on its two long sides, well clear of the hips
        boolean ridgeX = b.roof == TownPlan.Roof.GABLE_X || b.roof == TownPlan.Roof.HIP && b.w >= b.d;
        int along = ridgeX ? bx : bz;
        int len = ridgeX ? b.w : b.d;
        int across = ridgeX ? bz : bx;
        int depth = ridgeX ? b.d : b.w;
        int e = Math.min(across, depth - 1 - across);
        int maxE = (depth - 1) / 2;
        int margin = b.roof == TownPlan.Roof.HIP ? 4 : 2;
        if (maxE < 3 || e > 2 || len < 2 * margin + 3) {
            return;
        }

        int c = margin + Math.round((along - margin) / 4.0F) * 4;
        int rel = along - c;
        if (Math.abs(rel) > 1 || c > len - 1 - margin || e == 2 && rel != 0) {
            return;
        }

        Direction toCentre = ridgeX ? (rel < 0 ? Direction.EAST : Direction.WEST) : (rel < 0 ? Direction.SOUTH : Direction.NORTH);
        TownPlan.Palette pal = b.pal;
        if (rel != 0) {
            if (e == 0) {
                col.set(top + 1, log(pal.timber, Axis.Y));
                col.set(top + 2, log(pal.timber, Axis.Y));
            }

            col.set(top + 3, pal.roofStairs.setValue(StairBlock.FACING, toCentre));
        } else {
            if (e == 0) {
                col.set(top + 1, pane(ridgeX));
                col.set(top + 2, pane(ridgeX));
                col.set(top + 3, pal.plaster);
            } else if (e == 1) {
                col.fill(top + 2, top + 3, pal.plaster);
            }

            col.set(top + 4, pal.roof);
        }
    }

    /**
     * Whether the roof rises to the right as seen from outside the gable wall this column belongs to: "/" braces then
     * follow the slope, "\" on the falling side.
     */
    private static boolean rakeRises(TownPlan.Building b, int bx, int bz, Direction up) {
        Direction out;
        if (b.roof == TownPlan.Roof.GABLE_X) {
            out = bx == 0 ? Direction.WEST : Direction.EAST;
        } else {
            out = bz == 0 ? Direction.NORTH : Direction.SOUTH;
        }

        // seen from outside, the viewer's right is the wall's outward direction turned counter-clockwise (from above)
        Direction right = out.getCounterClockWise();
        return up == right;
    }

    public static boolean isBuilding(int x, int z) {
        TownPlan.Lot lot = lotAt(x, z);
        if (lot == null) {
            return false;
        } else {
            for (TownPlan.Building b : lot.buildings) {
                if (b.contains(x, z)) {
                    return true;
                }
            }

            return false;
        }
    }

    static final class Building {
        final int x0;
        final int z0;
        final int w;
        final int d;
        final int floors;
        final TownPlan.Roof roof;
        final TownPlan.Palette pal;
        final Direction doorSide;
        final int doorAlong;
        final int chimneyX;
        final int chimneyZ;
        final float pitch;
        final boolean solidWalls;

        Building(
            int x0,
            int z0,
            int w,
            int d,
            int floors,
            TownPlan.Roof roof,
            TownPlan.Palette pal,
            Direction doorSide,
            int doorAlong,
            int chimneyX,
            int chimneyZ,
            float pitch,
            boolean solidWalls
        ) {
            this.x0 = x0;
            this.z0 = z0;
            this.w = w;
            this.d = d;
            this.floors = floors;
            this.roof = roof;
            this.pal = pal;
            this.doorSide = doorSide;
            this.doorAlong = doorAlong;
            this.chimneyX = chimneyX;
            this.chimneyZ = chimneyZ;
            this.pitch = pitch;
            this.solidWalls = solidWalls;
        }

        boolean contains(int x, int z) {
            return x >= this.x0 && x < this.x0 + this.w && z >= this.z0 && z < this.z0 + this.d;
        }

        int wallTop() {
            return 66 + this.floors * 4;
        }
    }

    /** A walkway spanning [a0, a1] along one axis, three blocks wide around the centre line c across it. */
    static final class Bridge {
        final boolean alongX;
        final int a0;
        final int a1;
        final int c;
        final TownPlan.Palette pal;

        Bridge(boolean alongX, int a0, int a1, int c, TownPlan.Palette pal) {
            this.alongX = alongX;
            this.a0 = a0;
            this.a1 = a1;
            this.c = c;
            this.pal = pal;
        }

        boolean deck(int x, int z) {
            int a = this.alongX ? x : z;
            int k = this.alongX ? z : x;
            return a >= this.a0 && a <= this.a1 && Math.abs(k - this.c) <= 1;
        }

        /** The doorway column in the wall of the house at either end. */
        boolean door(int x, int z) {
            int a = this.alongX ? x : z;
            int k = this.alongX ? z : x;
            return k == this.c && (a == this.a0 - 1 || a == this.a1 + 1);
        }
    }

    static final class Lot {
        final int x0;
        final int z0;
        final TownPlan.LotKind kind;
        final List<TownPlan.Building> buildings = new ArrayList<>();
        final int seed;
        boolean local;
        /** Worked out on first use; deterministic, so two generator threads racing to fill it agree. */
        volatile List<TownPlan.Bridge> bridges;

        Lot(int x0, int z0, TownPlan.LotKind kind, int seed) {
            this.x0 = x0;
            this.z0 = z0;
            this.kind = kind;
            this.seed = seed;
        }
    }

    static enum LotKind {
        NONE,
        HOUSES,
        PLAZA,
        GATE_SQUARE,
        GARDEN,
        TRAINING;
    }

    static record Palette(BlockState plaster, Block timber, BlockState base, BlockState roof, BlockState roofStairs, BlockState floor, Block door) {
    }

    static enum Roof {
        GABLE_X,
        GABLE_Z,
        HIP;
    }
}
