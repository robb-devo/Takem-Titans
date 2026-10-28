package com.takem.titans.world;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What makes a town square feel lived in, column by column like the rest of {@link TownPlan}: the fountain, trees and
 * benches of each district's main square, plane trees and flags along the main avenue, and market stalls on the square
 * inside the gate.
 */
final class StreetLife {
    private static final int G = 66;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** Awning stripes of the market stalls, each paired with white. */
    private static final Block[] AWNINGS = {
        Blocks.RED_WOOL, Blocks.BLUE_WOOL, Blocks.GREEN_WOOL, Blocks.YELLOW_WOOL, Blocks.ORANGE_WOOL, Blocks.PURPLE_WOOL, Blocks.CYAN_WOOL, Blocks.BROWN_WOOL
    };

    private StreetLife() {
    }

    /** The main square of a district: centred on the avenue, so it is built here and not by the lots around it. */
    static boolean inCentralPlaza(int x, int z) {
        int off = ShiganshinaLayout.districtOffset(z);
        double dx = x + 0.5;
        double dz = z + 0.5 - (TownPlan.PLAZA_Z + off);
        return dx * dx + dz * dz < 23.0 * 23.0 && ShiganshinaLayout.region(x + 0.5, z + 0.5) == ShiganshinaLayout.Region.DISTRICT;
    }

    /** Whether something built at runtime (a quartermaster's stall, a waystone) stands within {@code r} of here. */
    static boolean nearFixture(int x, int z, int r) {
        for (Stalls.Stall s : Stalls.all()) {
            if (Math.abs(s.x() - x) <= r && Math.abs(s.z() - z) <= r) {
                return true;
            }
        }

        for (Waystones.Stone s : Waystones.all()) {
            if (Math.abs(s.x() - x) <= r && Math.abs(s.z() - z) <= r) {
                return true;
            }
        }

        return false;
    }

    /**
     * A column of a district's main square: rings of paving round a two-tier fountain, benches facing it, a ring of
     * trees in stone planters and the avenue running through as a smooth road. Mitras has its royal fountain instead.
     */
    static void centralPlaza(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        int off = ShiganshinaLayout.districtOffset(z);
        double dx = x + 0.5;
        double dz = z + 0.5 - (TownPlan.PLAZA_Z + off);
        double d = Math.sqrt(dx * dx + dz * dz);
        boolean mitras = off == -1100;
        double r = WorldNoise.rand(x, 1, z, 5);
        boolean ring = (int)d % 6 == 0;
        col.set(G, ring ? Blocks.STONE_BRICKS.defaultBlockState() : (r < 0.85 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState()));
        if (Math.abs(x) <= 1 && d > 10.0) {
            col.set(G, Blocks.SMOOTH_STONE.defaultBlockState());
        }

        if (mitras ? royalFountain(col, d) : fountain(col, d, dx, dz)) {
            return;
        }

        if (nearFixture(x, z, 6)) {
            return;
        }

        double a = Math.atan2(dz, dx);
        // benches on the diagonals, facing the water
        if (d >= 9.0 && d < 10.0) {
            double oct = a / (Math.PI / 4.0);
            int k = (int)Math.round(oct);
            if (Math.floorMod(k, 2) == 1 && Math.abs(oct - k) < 0.16) {
                Direction away = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                col.set(G + 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, away));
            }
            return;
        }

        // eight trees round the square, clear of the avenue
        for (int t = 0; t < 8; t++) {
            double ta = Math.PI / 8.0 + t * Math.PI / 4.0;
            int tx = (int)Math.floor(Math.cos(ta) * 16.0);
            int tz = (int)Math.floor(TownPlan.PLAZA_Z + off + Math.sin(ta) * 16.0);
            if (Math.abs(x - tx) <= 2 && Math.abs(z - tz) <= 2) {
                tree(col, x - tx, z - tz, WorldNoise.hash(tx, 3, tz, 404));
                return;
            }
        }

        if (d > 6.5 && WorldNoise.rand(x, 2, z, 31) < 0.004) {
            TownPlan.lamp(col);
        }
    }

    /** Stone basin with a raised inner bowl and a lantern column; water stands still until something breaks it. */
    private static boolean fountain(ColumnBuffer col, double d, double dx, double dz) {
        if (d >= 7.0) {
            return false;
        }

        if (d >= 6.0) {
            col.set(G + 1, Blocks.STONE_BRICK_WALL.defaultBlockState());
        } else if (d >= 5.0) {
            col.set(G + 1, Blocks.STONE_BRICK_SLAB.defaultBlockState());
            col.set(G, Blocks.STONE_BRICKS.defaultBlockState());
        } else if (d >= 2.6) {
            col.set(G, Blocks.WATER.defaultBlockState());
            col.set(G - 1, Blocks.STONE_BRICKS.defaultBlockState());
            if (Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.7 && d < 3.6) {
                col.set(G - 1, Blocks.SEA_LANTERN.defaultBlockState());
            }
        } else if (d >= 1.6) {
            col.set(G, Blocks.STONE_BRICKS.defaultBlockState());
            col.set(G + 1, Blocks.STONE_BRICKS.defaultBlockState());
            col.set(G + 2, Blocks.WATER.defaultBlockState());
            col.set(G + 3, d >= 2.2 ? Blocks.STONE_BRICK_WALL.defaultBlockState() : AIR);
        } else {
            col.fill(G, G + 4, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            col.set(G + 5, Blocks.STONE_BRICK_WALL.defaultBlockState());
            col.set(G + 6, Blocks.LANTERN.defaultBlockState());
        }

        return true;
    }

    private static boolean royalFountain(ColumnBuffer col, double d) {
        if (d >= 10.0) {
            return false;
        }

        if (d >= 9.0) {
            col.set(G + 1, Blocks.POLISHED_DIORITE.defaultBlockState());
        } else if (d < 1.6) {
            col.fill(G + 1, 88, Blocks.QUARTZ_PILLAR.defaultBlockState());
            col.set(89, Blocks.GOLD_BLOCK.defaultBlockState());
            if (d < 0.8) {
                col.fill(90, 92, Blocks.GOLD_BLOCK.defaultBlockState());
            }
        } else if (d < 2.6) {
            col.fill(G + 1, G + 3, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        } else {
            col.set(G, Blocks.WATER.defaultBlockState());
            col.set(G - 1, Blocks.QUARTZ_BRICKS.defaultBlockState());
        }

        return true;
    }

    /**
     * Along the main avenue, outside the squares: plane trees in planters down both sides between the lamps, a bench
     * under every other one and a flag on a pole halfway between.
     */
    static void avenue(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        int ax = Math.abs(x);
        if (ax > 7 || ShiganshinaLayout.distToWall(x + 0.5, z + 0.5) < 12.0 || nearSquare(x, z) || nearFixture(x, z, 8)) {
            return;
        }

        int side = x >= 0 ? 1 : -1;
        int tz = Math.floorDiv(z - 8, 16) * 16 + 8;
        int tx = 5 * side;
        if (Math.abs(x - tx) <= 2 && Math.abs(z - tz) <= 2 && !nearSquare(tx, tz)) {
            tree(col, x - tx, z - tz, WorldNoise.hash(tx, 7, tz, 505));
            return;
        }

        int zz = Math.floorMod(z, 32);
        if (ax == 7 && (zz == 15 || zz == 17)) {
            // a bench looking out across the avenue, its back to the houses
            col.set(G + 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, side > 0 ? Direction.EAST : Direction.WEST));
        } else if (ax == 7 && zz == 28) {
            col.fill(G + 1, G + 4, Blocks.DARK_OAK_FENCE.defaultBlockState());
            col.set(G + 5, flag(z, side > 0 ? 4 : 12));
        }
    }

    private static boolean nearSquare(int x, int z) {
        int off = ShiganshinaLayout.districtOffset(z);
        double pz = z + 0.5 - (TownPlan.PLAZA_Z + off);
        double gz = z + 0.5 - (TownPlan.GATE_SQUARE_Z + off);
        return x * x + pz * pz < 28.0 * 28.0 || off != -1100 && x * x + gz * gz < 40.0 * 40.0;
    }

    /** District colours: green for the Garrison towns, the royal purple in Mitras. */
    private static BlockState flag(int z, int rotation) {
        int off = ShiganshinaLayout.districtOffset(z);
        Block banner = off == -1100 ? Blocks.PURPLE_BANNER : (Math.floorMod(z, 64) < 32 ? Blocks.GREEN_BANNER : Blocks.WHITE_BANNER);
        return banner.defaultBlockState().setValue(BannerBlock.ROTATION, rotation);
    }

    /**
     * Inside the gate, the square where the Survey Corps rides out is also the market: rows of striped stalls facing the
     * road on both sides, with a lane between each pair.
     */
    static boolean market(ColumnBuffer col, int lx, int lz, int side) {
        int x = col.x;
        int z = col.z;
        // two rows per lot, both facing the avenue, with a walk between them and behind
        int u0 = side > 0 ? 2 : 16;
        int u1 = side > 0 ? 11 : 7;
        int row = lx >= Math.min(u0, u1) && lx < Math.min(u0, u1) + 2 ? 0 : (lx >= Math.max(u0, u1) && lx < Math.max(u0, u1) + 2 ? 1 : -1);
        if (row < 0) {
            return false;
        }

        int slot = Math.floorDiv(lz - 1, 6);
        int u = lz - 1 - slot * 6;
        if (u > 2 || lz < 1 || lz > 18) {
            return false;
        }

        int rowX = row == 0 ? Math.min(u0, u1) : Math.max(u0, u1);
        int seed = WorldNoise.hash(x - lx + rowX, slot, z - lz, 6060);
        if (WorldNoise.rand(seed, 1, 0, 1) < 0.2 || nearFixture(x, z, 5)) {
            return false;
        }

        // depth 0 is the counter at the front, the side nearer the avenue
        int depth = side > 0 ? lx - rowX : rowX + 1 - lx;
        stall(col, u, depth, side > 0 ? Direction.WEST : Direction.EAST, seed);
        return true;
    }

    /**
     * The square inside a district's outer gate, where the road from the gate comes in: paved, and lined on both sides
     * with rows of market stalls facing the road, a lane between each row.
     */
    static boolean gateMarket(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        int off = ShiganshinaLayout.districtOffset(z);
        if (off == -1100) {
            return false;
        }

        double gz = TownPlan.GATE_SQUARE_Z + off;
        double dz = z + 0.5 - gz;
        double d = Math.sqrt((x + 0.5) * (x + 0.5) + dz * dz);
        if (d >= 32.0 || ShiganshinaLayout.region(x + 0.5, z + 0.5) != ShiganshinaLayout.Region.DISTRICT) {
            return false;
        }

        int ax = Math.abs(x);
        double r = WorldNoise.rand(x, 1, z, 5);
        if (ax <= 2) {
            col.set(G, Blocks.STONE_BRICKS.defaultBlockState());
            return true;
        }

        col.set(G, Math.floorMod(z, 5) == 3 ? Blocks.STONE_BRICKS.defaultBlockState() : (r < 0.8 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState()));
        // stall rows: fronts at 6, 12, 18 and 24 blocks out from the road, two deep
        int row = (ax - 6) / 6;
        int depth = (ax - 6) % 6;
        if (ax < 6 || row > 3 || depth > 1) {
            return true;
        }

        int u = Math.floorMod(z - (int)gz, 5);
        if (u > 2) {
            return true;
        }

        int side = x >= 0 ? 1 : -1;
        int sz = z - u + 1;
        int sx = side * (6 + row * 6);
        int seed = WorldNoise.hash(sx, sz, off, 7070);
        if (WorldNoise.rand(seed, 1, 0, 2) < 0.2
            || ShiganshinaLayout.distToWall(sx + 0.5 * side, sz + 0.5) < 9.0
            || Math.hypot(sx, sz - gz) > 29.0
            || nearFixture(sx, sz, 6)) {
            return true;
        }

        stall(col, u, depth, side > 0 ? Direction.WEST : Direction.EAST, seed);
        return true;
    }

    /**
     * The empty ground behind the houses, along the inside of a wall, is where the Garrison camps and the town keeps its
     * stores: now and then a tent with a fire in front, a stack of crates, a hay rick or a pile of timber.
     */
    static boolean wallCamp(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        int fx = Math.floorDiv(x, 11) * 11 + 5;
        int fz = Math.floorDiv(z, 11) * 11 + 5;
        int lx = x - fx;
        int lz = z - fz;
        if (Math.abs(lx) > 4 || Math.abs(lz) > 4) {
            return false;
        }

        int seed = WorldNoise.hash(fx, 11, fz, 9191);
        if (WorldNoise.rand(seed, 2, 0, 1) > 0.45 || !campSite(fx, fz)) {
            return false;
        }

        int kind = Math.floorMod(seed >> 4, 4);
        boolean alongX = Math.floorMod(seed >> 7, 2) == 0;
        int u = alongX ? lz : lx;
        int v = alongX ? lx : lz;
        double r = WorldNoise.rand(x, 1, z, 5);
        // trodden ground round the site, frayed at the edge into the paving
        if (Math.sqrt(lx * lx + lz * lz) < 2.6 + r * 1.8) {
            col.set(G, r < 0.6 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
        }

        switch (kind) {
            case 0 -> {
                // an A-frame tent, open at both ends, a fire in front of it
                if (Math.abs(u) <= 1 && Math.abs(v) <= 2) {
                    BlockState cloth = Math.floorMod(seed >> 9, 3) == 0 ? Blocks.BROWN_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
                    if (u == 0) {
                        col.set(G + 2, cloth);
                        if (Math.abs(v) <= 1) {
                            col.set(G + 1, Blocks.AIR.defaultBlockState());
                        }
                    } else {
                        col.set(G + 1, cloth);
                    }
                } else if (u == 0 && v == 4) {
                    col.set(G + 1, Blocks.CAMPFIRE.defaultBlockState());
                } else if (Math.abs(u) == 1 && v == 4) {
                    col.set(G + 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, alongX ? (u > 0 ? Direction.SOUTH : Direction.NORTH) : (u > 0 ? Direction.EAST : Direction.WEST)));
                }
            }
            case 1 -> {
                // crates and barrels, stacked higher in the middle
                if (Math.abs(lx) <= 1 && Math.abs(lz) <= 1) {
                    int h = 3 - Math.max(Math.abs(lx), Math.abs(lz)) - (r < 0.4 ? 1 : 0);
                    for (int y = 1; y <= Math.max(1, h); y++) {
                        col.set(G + y, crate(WorldNoise.hash(x, y, z, 17)));
                    }
                }
            }
            case 2 -> {
                // a hay rick
                double dd = Math.sqrt(lx * lx + lz * lz);
                if (dd <= 2.2) {
                    int h = dd < 0.5 ? 3 : (dd < 1.5 ? 2 : 1);
                    col.fill(G + 1, G + h, Blocks.HAY_BLOCK.defaultBlockState());
                }
            }
            default -> {
                // seasoned timber, stacked two high under a lean-to
                if (Math.abs(u) <= 1 && Math.abs(v) <= 2) {
                    BlockState log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Axis.X : Axis.Z);
                    col.fill(G + 1, G + (u == 1 ? 1 : 2), log);
                }
            }
        }

        return true;
    }

    private static boolean campSite(int fx, int fz) {
        if (Math.abs(fx) < 12 || ShiganshinaLayout.region(fx + 0.5, fz + 0.5) != ShiganshinaLayout.Region.DISTRICT) {
            return false;
        }

        double wall = ShiganshinaLayout.distToWall(fx + 0.5, fz + 0.5);
        if (wall < 10.0 || wall > 24.0 || ShiganshinaLayout.nearGate(fx, fz) != 0 || inCentralPlaza(fx, fz) || nearFixture(fx, fz, 9)) {
            return false;
        }

        // the whole site has to lie on open street, not across a lot
        for (int dx = -5; dx <= 5; dx += 5) {
            for (int dz = -5; dz <= 5; dz += 5) {
                if (TownPlan.lotAt(fx + dx, fz + dz) != null) {
                    return false;
                }
            }
        }

        int off = ShiganshinaLayout.districtOffset(fz);
        return off == -1100 || Math.hypot(fx, fz - (TownPlan.GATE_SQUARE_Z + off)) > 36.0;
    }

    /**
     * One column of a market stall three wide and two deep: posts at the corners, a counter with goods at the front,
     * crates behind and a striped wool awning on top. {@code u} runs across the front, {@code depth} away from it.
     */
    private static void stall(ColumnBuffer col, int u, int depth, Direction facing, int seed) {
        boolean post = u != 1;
        Block stripe = AWNINGS[Math.floorMod(seed, AWNINGS.length)];
        int goods = Math.floorMod(seed >> 4, 6);
        if (post) {
            col.fill(G + 1, G + 3, Blocks.SPRUCE_FENCE.defaultBlockState());
        } else if (depth == 0) {
            col.set(G + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            col.set(G + 2, goods(goods));
        } else {
            col.set(G + 1, crate(seed));
            if (WorldNoise.rand(seed, 2, 0, 3) < 0.6) {
                col.set(G + 2, crate(seed >> 3));
            }
            col.set(G + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        boolean white = (u + depth) % 2 == 0;
        col.set(G + 4, white ? Blocks.WHITE_WOOL.defaultBlockState() : stripe.defaultBlockState());
    }

    private static BlockState goods(int kind) {
        return switch (kind) {
            case 0 -> Blocks.MELON.defaultBlockState();
            case 1 -> Blocks.PUMPKIN.defaultBlockState();
            case 2 -> Blocks.HAY_BLOCK.defaultBlockState();
            case 3 -> Blocks.POTTED_RED_TULIP.defaultBlockState();
            case 4 -> Blocks.CAKE.defaultBlockState();
            default -> Blocks.DRIED_KELP_BLOCK.defaultBlockState();
        };
    }

    private static BlockState crate(int seed) {
        return switch (Math.floorMod(seed, 4)) {
            case 0 -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.NORTH);
            case 1 -> Blocks.COMPOSTER.defaultBlockState();
            case 2 -> Blocks.HAY_BLOCK.defaultBlockState();
            default -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.EAST);
        };
    }

    /**
     * A tree in a stone planter, (dx, dz) from its trunk: a five-high trunk under a rounded crown about five across.
     * Oak most of the time, now and then a birch or a dark oak.
     */
    static void tree(ColumnBuffer col, int dx, int dz, int seed) {
        int kind = Math.floorMod(seed, 7);
        Block log = kind == 0 ? Blocks.BIRCH_LOG : (kind == 1 ? Blocks.DARK_OAK_LOG : Blocks.OAK_LOG);
        Block leaves = kind == 0 ? Blocks.BIRCH_LEAVES : (kind == 1 ? Blocks.DARK_OAK_LEAVES : Blocks.OAK_LEAVES);
        BlockState leaf = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int h = 5 + Math.floorMod(seed >> 3, 2);
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        double dd = Math.sqrt(dx * dx + dz * dz);
        if (ring == 0) {
            col.set(G, Blocks.ROOTED_DIRT.defaultBlockState());
            col.fill(G + 1, G + h, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.Y));
            col.set(G + h + 1, leaf);
            col.set(G + h + 2, leaf);
            return;
        }

        if (ring == 1) {
            col.set(G, Blocks.GRASS_BLOCK.defaultBlockState());
            col.set(G + 1, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }

        // crown: wide at the bottom, rounded off at the top, a few ragged gaps
        for (int y = G + h - 2; y <= G + h + 2; y++) {
            int layer = y - (G + h - 2);
            double reach = layer == 0 ? 1.6 : (layer <= 2 ? 2.5 : (layer == 3 ? 1.9 : 1.2));
            if (dd <= reach && WorldNoise.rand(col.x, y, col.z, seed) > (dd > reach - 0.6 ? 0.25 : 0.0)) {
                col.set(y, leaf);
            }
        }
    }
}
