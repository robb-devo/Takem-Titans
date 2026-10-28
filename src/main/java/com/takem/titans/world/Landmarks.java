package com.takem.titans.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One thing in every district you will remember it by, each standing on a lot of its own: Shiganshina's bell tower,
 * the boulder in Trost that once sealed a gate, the market halls of Karanes and Krolva, the Wall cult's chapels in
 * Stohess and Ehrmich. Mitras gets its obelisk in the main square (see {@link TownPlan}).
 */
final class Landmarks {
    private static final int G = 66;
    private static final int BELL_TOWER = 0;
    private static final int BOULDER = 1;
    private static final int MARKET = 2;
    private static final int CHAPEL = 3;
    /** x, z of each landmark lot's north-west corner, and its kind. Lots are twenty by twenty. */
    private static final int[][] SITES = {
        {34, 39, BELL_TOWER},
        {34, -387, BOULDER},
        {710, -1113, MARKET},
        {-729, -1113, MARKET},
        {320, -1113, CHAPEL},
        {-339, -1113, CHAPEL},
    };
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private Landmarks() {
    }

    private static double rnd(int x, int y, int z, int seed) {
        return WorldNoise.rand(x, y, z, seed);
    }

    /** True when the column belongs to a landmark and has been built. */
    static boolean column(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        for (int[] s : SITES) {
            int lx = x - s[0];
            int lz = z - s[1];
            if (lx >= 0 && lx < 20 && lz >= 0 && lz < 20) {
                double dx = lx - 9.5;
                double dz = lz - 9.5;
                switch (s[2]) {
                    case BELL_TOWER -> bellTower(col, dx, dz);
                    case BOULDER -> boulder(col, dx, dz);
                    case MARKET -> market(col, dx, dz, s[0] < 0);
                    default -> chapel(col, dx, dz);
                }

                return true;
            }
        }

        return false;
    }

    private static void paving(ColumnBuffer col, double d) {
        int x = col.x;
        int z = col.z;
        double n = rnd(x, 1, z, 801);
        col.set(G, (int)d % 4 == 0 ? Blocks.STONE_BRICKS.defaultBlockState()
            : (n < 0.8 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState()));
    }

    /** Shiganshina's bell tower: a square stone shaft, an open belfry of arches, a copper spire. */
    private static void bellTower(ColumnBuffer col, double dx, double dz) {
        double d = Math.max(Math.abs(dx), Math.abs(dz));
        paving(col, Math.sqrt(dx * dx + dz * dz));
        if (d > 4.5) {
            if (d > 8.5 && (Math.abs(dx) > 8.5 && Math.abs(dz) > 8.5)) {
                TownPlan.lamp(col);
            }

            return;
        }

        int top = G + 34;
        boolean wall = d > 3.5;
        boolean corner = Math.abs(dx) > 3.5 && Math.abs(dz) > 3.5;
        for (int y = G + 1; y <= top + 6; y++) {
            int rel = y - G;
            BlockState s = AIR;
            if (y <= top) {
                if (wall) {
                    s = corner ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    if (rel % 9 == 0) {
                        s = Blocks.POLISHED_DIORITE.defaultBlockState();
                    }

                    // tall slit windows on every face, a door to the south
                    if (!corner && Math.min(Math.abs(dx), Math.abs(dz)) < 0.6 && rel % 9 >= 3 && rel % 9 <= 6) {
                        s = Blocks.GLASS.defaultBlockState();
                    }

                    if (dz > 3.5 && Math.abs(dx) < 1.5 && rel <= 4) {
                        s = AIR;
                    }

                    // the clock: a pale face on each side near the top
                    if (!corner && rel >= 24 && rel <= 26 && Math.min(Math.abs(dx), Math.abs(dz)) < 1.6) {
                        s = rel == 25 && Math.min(Math.abs(dx), Math.abs(dz)) < 0.6 ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.QUARTZ_BLOCK.defaultBlockState();
                    }
                } else if (rel % 9 == 0) {
                    s = Blocks.SPRUCE_PLANKS.defaultBlockState();
                }
            } else if (y <= top + 5) {
                // the belfry: corner piers, arches between, a bell of gold hanging in the middle
                if (corner) {
                    s = Blocks.STONE_BRICKS.defaultBlockState();
                } else if (wall && y == top + 5) {
                    s = Blocks.STONE_BRICKS.defaultBlockState();
                } else if (d < 1.0 && y >= top + 2) {
                    // hung on a timber yoke from the belfry roof
                    s = y >= top + 4 ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState();
                }
            } else {
                s = Blocks.STONE_BRICKS.defaultBlockState();
            }

            col.set(y, s);
        }

        // the spire
        int cone = 12;
        for (int y = 1; y <= cone; y++) {
            double rr = 4.5 * (1.0 - (double)y / cone) + 0.3;
            if (d < rr) {
                col.set(top + 6 + y, d > rr - 1.3 ? Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState() : AIR);
            }
        }

        if (d < 0.6) {
            col.set(top + 6 + cone + 1, Blocks.LIGHTNING_ROD.defaultBlockState());
        }
    }

    /** The boulder that sealed Trost's gate, half sunk into a square it cracked. */
    private static void boulder(ColumnBuffer col, double dx, double dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        int x = col.x;
        int z = col.z;
        paving(col, d);
        if (d < 9.5 && rnd(x, 2, z, 802) < 0.3) {
            col.set(G, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
        }

        double R = 7.2;
        if (d < R) {
            double half = Math.sqrt(R * R - d * d);
            // the centre sits a little above the street, so most of the stone shows
            int c = G + 4;
            int bottom = (int)Math.floor(c - half);
            int top = (int)Math.ceil(c + half);
            for (int y = Math.max(G - 2, bottom); y <= top; y++) {
                double n = rnd(x, y, z, 803);
                col.set(y, n < 0.4 ? Blocks.STONE.defaultBlockState() : (n < 0.7 ? Blocks.ANDESITE.defaultBlockState()
                    : (n < 0.88 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState())));
            }
        } else if (d < 9.5 && rnd(x, 3, z, 804) < 0.15) {
            col.set(G + 1, Blocks.COBBLESTONE.defaultBlockState());
        }
    }

    /** An open market hall: stone piers, a timber roof, striped awnings over stalls round its edge. */
    private static void market(ColumnBuffer col, double dx, double dz, boolean west) {
        double ax = Math.abs(dx);
        double az = Math.abs(dz);
        int x = col.x;
        int z = col.z;
        paving(col, Math.sqrt(dx * dx + dz * dz));
        if (ax > 8.5 || az > 8.5) {
            return;
        }

        int top = G + 8;
        boolean pier = Math.floorMod((int)Math.floor(dx + 9.5), 4) == 1 && Math.floorMod((int)Math.floor(dz + 9.5), 4) == 1;
        if (pier) {
            col.fill(G + 1, top - 1, Blocks.STONE_BRICKS.defaultBlockState());
        }

        // a pitched roof: up from the edges to a ridge along x
        int roof = top + (int)Math.max(0.0, 3.0 - az * 0.4);
        col.set(top, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int y = top + 1; y <= roof; y++) {
            col.set(y, y == roof ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : AIR);
        }

        // stalls round the edge with striped awnings
        if ((ax > 6.5 || az > 6.5) && !pier) {
            BlockState awning = ((int)Math.floor(dx + dz + 20) % 2 == 0) ^ west ? Blocks.RED_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
            col.set(G + 4, awning);
            double n = rnd(x, 4, z, 805);
            if (n < 0.3) {
                col.set(G + 1, Blocks.BARREL.defaultBlockState());
            } else if (n < 0.45) {
                col.set(G + 1, Blocks.HAY_BLOCK.defaultBlockState());
            } else if (n < 0.55) {
                col.set(G + 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
                col.set(G + 2, n < 0.5 ? Blocks.MELON.defaultBlockState() : Blocks.PUMPKIN.defaultBlockState());
            }
        }

        if (ax < 0.6 && az < 0.6) {
            col.set(top - 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.TRUE));
        }
    }

    /** A chapel of the Wall cult: a white nave with tall windows, its tower at the front topped by a slim spire. */
    private static void chapel(ColumnBuffer col, double dx, double dz) {
        double ax = Math.abs(dx);
        paving(col, Math.sqrt(dx * dx + dz * dz));
        // the nave runs north-south, eight wide and sixteen long; the tower stands at its south end
        if (ax > 4.5 || dz < -8.5 || dz > 9.5) {
            return;
        }

        boolean tower = dz > 4.5;
        int top = tower ? G + 28 : G + 11;
        boolean side = ax > 3.5;
        boolean end = dz < -7.5 || dz > 8.5;
        boolean front = tower && (dz > 8.5 || ax > 3.5 || dz < 5.5);
        for (int y = G + 1; y <= top; y++) {
            int rel = y - G;
            BlockState s = AIR;
            boolean shell = tower ? front : side || end;
            if (shell) {
                s = Blocks.CALCITE.defaultBlockState();
                if ((int)Math.floor(dz + 20) % 3 == 0 && side && !tower) {
                    s = Blocks.POLISHED_DIORITE.defaultBlockState();
                }

                // tall pointed windows down the nave, a rose window over the door
                if (side && !tower && rel >= 3 && rel <= 8 && (int)Math.floor(dz + 20) % 3 == 1) {
                    s = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
                }

                if (tower && dz > 8.5 && ax < 1.5 && rel <= 4) {
                    s = AIR;
                }

                if (tower && dz > 8.5 && ax < 1.5 && rel >= 7 && rel <= 9) {
                    s = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
                }
            } else if (tower && rel % 7 == 0) {
                s = Blocks.SPRUCE_PLANKS.defaultBlockState();
            }

            col.set(y, s);
        }

        if (!tower) {
            // a steep roof over the nave
            int peak = top + 1 + (int)Math.max(0.0, 4.0 - ax);
            for (int y = top + 1; y <= peak; y++) {
                col.set(y, y == peak ? Blocks.DEEPSLATE_TILES.defaultBlockState() : AIR);
            }

            col.set(top, Blocks.SPRUCE_PLANKS.defaultBlockState());
        } else {
            double c = Math.max(ax, Math.abs(dz - 7.0));
            int cone = 14;
            for (int y = 1; y <= cone; y++) {
                double rr = 4.5 * (1.0 - (double)y / cone) + 0.3;
                if (c < rr) {
                    col.set(top + y, c > rr - 1.3 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : AIR);
                }
            }

            if (ax < 0.6 && Math.abs(dz - 7.0) < 0.6) {
                col.set(top + cone + 1, Blocks.GOLD_BLOCK.defaultBlockState());
            }
        }
    }
}
