package com.takem.titans.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Valgard, the arena city: built to be fought through and torn down. At its heart a colosseum of black stone banded in
 * gold under a copper crown; six avenues run out from its gates to the wall under lit viaducts, and between them three
 * kinds of quarter:
 * <ul>
 * <li>the Spires - a forest of octagonal towers sixty to a hundred blocks tall, stepped back near the top, lit from
 * inside, joined to their neighbours by glazed sky-bridges on two levels;</li>
 * <li>the Foundry - long brick halls with copper roofs and tall hollow chimneys that smoke and topple;</li>
 * <li>the Old Quarter - the sandstone town Valgard grew from, round towers and terracotta.</li>
 * </ul>
 * Spans are kept short enough for the collapse engine to carry them honestly: a sky-bridge hangs between two towers that
 * stand a dozen blocks apart, so when one tower comes down its half of the bridge goes with it.
 * <p>
 * Everything is worked out from the column's polar position round the arena centre, so the plan holds no state.
 */
public final class ArenaPlan {
    /** The arena waystone stands this far south of the centre, on the concourse at the head of the south avenue. */
    public static final int WAYSTONE_DZ = 63;
    /** The bell that starts the waves stands on the fighting floor, just inside the south gate. */
    public static final int BELL_DZ = 32;
    private static final int G = 66;
    private static final double FLOOR_R = 36.0;
    private static final double PODIUM_R = 38.5;
    private static final double FACADE_IN = 54.0;
    private static final double FACADE_OUT = 56.5;
    private static final double CONCOURSE_R = 68.0;
    private static final int FACADE_TOP = 112;
    private static final double AVENUE_HALF = 7.0;
    private static final double SECTOR = Math.PI / 3.0;
    /** Radial bands of building cells: inner radius, outer radius. */
    private static final double[][] BANDS = {{72.0, 102.0}, {106.0, 136.0}, {140.0, 170.0}, {174.0, 204.0}, {208.0, 232.0}};
    /** The style of each of the six quarters, counted from the south avenue going clockwise round the compass. */
    private static final int SPIRES = 0;
    private static final int FOUNDRY = 1;
    private static final int OLD = 2;
    private static final int[] QUARTERS = {SPIRES, FOUNDRY, SPIRES, OLD, FOUNDRY, SPIRES};

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BLACK_BRICK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState BLACK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState BLACK_CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState DEEP_TILE = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState DEEP_BRICK = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState DEEP_POLISHED = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState TUFF_BRICK = Blocks.TUFF_BRICKS.defaultBlockState();
    private static final BlockState TUFF_POLISHED = Blocks.POLISHED_TUFF.defaultBlockState();
    private static final BlockState TUFF_CHISELED = Blocks.CHISELED_TUFF.defaultBlockState();
    private static final BlockState STONE_BRICK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState BRICK = Blocks.BRICKS.defaultBlockState();
    private static final BlockState RED_NETHER = Blocks.RED_NETHER_BRICKS.defaultBlockState();
    private static final BlockState COPPER = Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
    private static final BlockState COPPER_W = Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState TINTED = Blocks.TINTED_GLASS.defaultBlockState();
    private static final BlockState PLANKS = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SHROOMLIGHT.defaultBlockState();
    private static final BlockState SEA_LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState CHANNEL = Blocks.PRISMARINE_BRICKS.defaultBlockState();
    private static final BlockState SMOOTH = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    private static final BlockState CUT = Blocks.CUT_SANDSTONE.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_SANDSTONE.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState ORANGE = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final BlockState RED = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState TERRA = Blocks.TERRACOTTA.defaultBlockState();

    private ArenaPlan() {
    }

    private static double rnd(int x, int y, int z, int seed) {
        return WorldNoise.rand(x, y, z, seed);
    }

    public static void column(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        double dx = x + 0.5 - ShiganshinaLayout.ARENA_X;
        double dz = z + 0.5 - ShiganshinaLayout.ARENA_Z;
        double r = Math.sqrt(dx * dx + dz * dz);
        double a = Math.atan2(dz, dx);
        // the quarter and how far the column is off the nearest of the six avenues
        double rel = a - Math.PI / 2.0;
        rel -= Math.floor(rel / (Math.PI * 2.0)) * Math.PI * 2.0;
        int quarter = Math.min(5, (int)(rel / SECTOR));
        double off = rel - quarter * SECTOR;
        double av = r * Math.sin(Math.min(off, SECTOR - off));
        col.fill(58, 65, Blocks.STONE.defaultBlockState());
        if (r < FLOOR_R) {
            floor(col, x, z, dx, dz, r);
        } else if (r < FACADE_OUT + 0.5) {
            colosseum(col, x, z, r, a, av);
        } else if (r < CONCOURSE_R) {
            concourse(col, x, z, r, a);
        } else if (av < AVENUE_HALF) {
            avenue(col, x, z, r, av);
        } else {
            int style = QUARTERS[quarter];
            city(col, x, z, dx, dz, r, a, av, style);
            // (no sky-bridges any more: they tied every spire to its neighbours, so one falling dragged the next down)
        }
    }

    // ---------------------------------------------------------------- the colosseum

    /** The fighting floor: packed earth, a gilded seal at the centre, eight basalt columns round it. */
    private static void floor(ColumnBuffer col, int x, int z, double dx, double dz, double r) {
        double n = rnd(x, 0, z, 601);
        col.set(G, r < 3.0 ? GILDED : (n < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : (n < 0.8 ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.MUD_BRICKS.defaultBlockState())));
        if (r > 3.0 && r < 4.0 || r > 33.0 && r < 34.0) {
            col.set(G, BLACK_BRICK);
        }

        for (int k = 0; k < 8; k++) {
            double ang = Math.PI / 4.0 * k + Math.PI / 8.0;
            double px = Math.cos(ang) * 24.0;
            double pz = Math.sin(ang) * 24.0;
            double d = Math.sqrt((dx - px) * (dx - px) + (dz - pz) * (dz - pz));
            if (d < 2.2) {
                int h = 12 + (int)(rnd(k, 1, 0, 602) * 14.0);
                if (d > 1.2) {
                    h -= (int)(rnd(x, 2, z, 603) * 5.0);
                }

                col.set(G + 1, BLACK_CHISELED);
                col.fill(G + 2, G + h, Blocks.POLISHED_BASALT.defaultBlockState());
                col.set(G + h + 1, d < 1.2 ? GILDED : AIR);
            }
        }
    }

    /** Podium, stands on vaulted piers, a three-tier arcaded facade with gold bands and a copper crown; six great gates. */
    private static void colosseum(ColumnBuffer col, int x, int z, double r, double a, double av) {
        col.set(G, DEEP_POLISHED);
        if (r < PODIUM_R) {
            col.fill(G + 1, G + 6, BLACK_BRICK);
            col.set(G + 7, BLACK_CHISELED);
        } else if (r < FACADE_IN) {
            int top = 73 + (int)((r - PODIUM_R) * 1.4);
            double arc = a * r;
            if (Math.floorMod((int)Math.floor(arc), 10) < 2) {
                col.fill(G + 1, top - 2, BLACK);
            }

            col.set(top - 1, DEEP_BRICK);
            int row = (int)((r - PODIUM_R) * 1.4);
            col.set(top, row % 4 == 3 ? RED_NETHER : DEEP_POLISHED);
        } else if (r < FACADE_OUT) {
            double arc = a * 55.0;
            double k = Math.floorMod((int)Math.floor(arc * 2.0), 14) * 0.5;
            boolean bay = k >= 1.5 && k < 5.5;
            for (int y = G + 1; y <= FACADE_TOP; y++) {
                BlockState s = BLACK_BRICK;
                if (y == 80 || y == 92 || y == 104) {
                    s = GILDED;
                } else if (bay && (y >= 70 && y <= 78 || y >= 83 && y <= 90 || y >= 95 && y <= 102)) {
                    int bandTop = y <= 78 ? 78 : (y <= 90 ? 90 : 102);
                    boolean shoulder = k < 2.0 || k >= 5.0;
                    s = shoulder && y >= bandTop - 1 ? BLACK_BRICK : AIR;
                } else if (bay && (y == 79 || y == 91 || y == 103) && k >= 3.0 && k < 4.0) {
                    // a glowing keystone over every arch
                    s = LIGHT;
                }

                col.set(y, s);
            }

            int crown = Math.floorMod((int)Math.floor(arc), 4);
            col.set(FACADE_TOP + 1, crown == 0 ? COPPER : AIR);
            if (Math.floorMod((int)Math.floor(arc), 28) == 0) {
                col.fill(FACADE_TOP + 1, FACADE_TOP + 7, COPPER);
                col.set(FACADE_TOP + 8, Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }

        if (av < 6.0) {
            int archTop = 86 - (int)(av * av * 0.35);
            for (int y = G + 1; y <= archTop; y++) {
                col.set(y, AIR);
            }

            col.set(G, av < 1.5 ? GILDED : DEEP_POLISHED);
        }
    }

    /** The concourse round the colosseum: tuff flags, a chiselled ring, blackstone lamp posts. */
    private static void concourse(ColumnBuffer col, int x, int z, double r, double a) {
        double n = rnd(x, 1, z, 611);
        BlockState s = n < 0.7 ? TUFF_BRICK : TUFF_POLISHED;
        if (Math.abs(r - 62.5) < 0.6) {
            s = TUFF_CHISELED;
        }

        col.set(G, s);
        if (r > 66.5 && r < 67.5 && Math.floorMod((int)Math.floor(a * 67.0), 16) == 0) {
            col.fill(G + 1, G + 3, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
            col.set(G + 4, Blocks.LANTERN.defaultBlockState());
        }
    }

    /** An avenue: polished centre strip, and down the middle a viaduct on tall piers carrying a lit channel. */
    private static void avenue(ColumnBuffer col, int x, int z, double r, double av) {
        double n = rnd(x, 1, z, 612);
        col.set(G, av < 1.5 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : (av > 6.0 ? DEEP_TILE : (n < 0.85 ? STONE_BRICK : Blocks.CRACKED_STONE_BRICKS.defaultBlockState())));
        if (av >= 2.5) {
            return;
        }

        double along = r;
        double m = Math.floorMod((int)Math.floor(along), 12);
        double toPier = Math.min(m, 12.0 - m);
        if (m < 2.0) {
            col.fill(G + 1, 85, DEEP_POLISHED);
        } else {
            col.fill(toPier < 3.0 ? 80 : (toPier < 4.0 ? 83 : 84), 85, DEEP_BRICK);
        }

        col.set(86, av < 1.0 ? (Math.floorMod((int)Math.floor(along), 12) == 6 ? SEA_LANTERN : CHANNEL) : DEEP_TILE);
        if (av > 1.8) {
            col.set(87, BLACK_BRICK);
        }
    }

    // ---------------------------------------------------------------- the quarters

    /** The cell a column falls in, or null for the streets between them. */
    private static final class Cell {
        int band;
        int k;
        int sectors;
        double r0;
        double r1;
        double rc;
        double ac;
        double arcIn;
        double arcOut;
    }

    private static int sectors(int band) {
        double rc = (BANDS[band][0] + BANDS[band][1]) * 0.5;
        return (int)Math.round(Math.PI * 2.0 * rc / 28.0);
    }

    private static Cell cell(double r, double a) {
        for (int b = 0; b < BANDS.length; b++) {
            if (r >= BANDS[b][0] && r < BANDS[b][1]) {
                Cell c = new Cell();
                c.band = b;
                c.sectors = sectors(b);
                double span = Math.PI * 2.0 / c.sectors;
                c.k = Math.floorMod((int)Math.floor((a + Math.PI) / span), c.sectors);
                double a0 = -Math.PI + c.k * span;
                c.r0 = BANDS[b][0];
                c.r1 = BANDS[b][1];
                c.rc = (c.r0 + c.r1) * 0.5;
                c.ac = a0 + span * 0.5;
                double rel = a - a0;
                rel -= Math.floor(rel / (Math.PI * 2.0)) * Math.PI * 2.0;
                c.arcIn = rel * r;
                c.arcOut = (span - rel) * r;
                return c;
            }
        }

        return null;
    }

    private static void city(ColumnBuffer col, int x, int z, double dx, double dz, double r, double a, double av, int style) {
        double n = rnd(x, 2, z, 621);
        BlockState street = switch (style) {
            case OLD -> n < 0.7 ? SMOOTH : (n < 0.9 ? SANDSTONE : TERRA);
            case FOUNDRY -> n < 0.6 ? Blocks.COBBLESTONE.defaultBlockState() : (n < 0.85 ? STONE_BRICK : Blocks.ANDESITE.defaultBlockState());
            default -> n < 0.75 ? DEEP_TILE : (n < 0.9 ? DEEP_BRICK : Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
        };
        col.set(G, street);
        Cell c = cell(r, a);
        if (c == null || c.arcIn < 2.0 || c.arcOut < 2.0 || r - c.r0 < 1.0 || c.r1 - r < 1.0) {
            return;
        }

        double h = rnd(c.band, c.k, style, 631);
        switch (style) {
            case SPIRES -> {
                if (hasTower(c.band, c.k)) {
                    tower(col, dx, dz, c);
                } else {
                    plaza(col, dx, dz, c);
                }
            }
            case FOUNDRY -> {
                if (h < 0.85) {
                    foundry(col, r, c, av);
                } else {
                    plaza(col, dx, dz, c);
                }
            }
            default -> {
                if (h < 0.4 && clearOfAvenue(c.band, c.k, c.sectors, 7.5 + 1.5)) {
                    oldSpire(col, dx, dz, c);
                } else if (h < 0.85) {
                    oldHall(col, r, c, av);
                } else {
                    plaza(col, dx, dz, c);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the Spires

    /** A spire's shape, all of it decided by its cell. */
    private static final class Tower {
        double cx;
        double cz;
        double R;
        double R2;
        int top;
        int setback;
        int mat;
    }

    private static boolean hasTower(int band, int k) {
        int n = sectors(band);
        k = Math.floorMod(k, n);
        // every other cell only: a falling spire has forty blocks of open square before it reaches the next one
        return k % 2 == 0 && rnd(band, k, SPIRES, 631) < 0.88 && clearOfAvenue(band, k, n, 8.5 + 1.5);
    }

    /** Whether a cell's centre stands far enough off the avenues for something {@code reach} wide to fit. */
    private static boolean clearOfAvenue(int band, int k, int n, double reach) {
        double ac = -Math.PI + (k + 0.5) * Math.PI * 2.0 / n;
        double rc = (BANDS[band][0] + BANDS[band][1]) * 0.5;
        double rel = ac - Math.PI / 2.0;
        rel -= Math.floor(rel / (Math.PI * 2.0)) * Math.PI * 2.0;
        double off = rel - Math.min(5, (int)(rel / SECTOR)) * SECTOR;
        return rc * Math.sin(Math.min(off, SECTOR - off)) > AVENUE_HALF + reach;
    }

    private static int quarterOf(int band, int k, int n) {
        double ac = -Math.PI + (k + 0.5) * Math.PI * 2.0 / n;
        double rel = ac - Math.PI / 2.0;
        rel -= Math.floor(rel / (Math.PI * 2.0)) * Math.PI * 2.0;
        return Math.min(5, (int)(rel / SECTOR));
    }

    private static Tower tower(int band, int k) {
        int n = sectors(band);
        k = Math.floorMod(k, n);
        double span = Math.PI * 2.0 / n;
        double ac = -Math.PI + (k + 0.5) * span;
        double rc = (BANDS[band][0] + BANDS[band][1]) * 0.5;
        Tower t = new Tower();
        t.cx = Math.cos(ac) * rc;
        t.cz = Math.sin(ac) * rc;
        t.R = 6.5 + rnd(band, k, 1, 632) * 2.0;
        t.R2 = t.R * 0.68;
        // the inner rings stand tallest, so the skyline climbs towards the colosseum
        int H = 40 + (int)(rnd(band, k, 2, 633) * 26.0) + (BANDS.length - band) * 4;
        t.top = G + H;
        t.setback = G + (int)(H * 0.74);
        t.mat = (int)(rnd(band, k, 3, 634) * 3.0);
        return t;
    }

    private static double oct(double dx, double dz) {
        double ax = Math.abs(dx);
        double az = Math.abs(dz);
        return Math.max(Math.max(ax, az), (ax + az) * 0.7071);
    }

    private static void tower(ColumnBuffer col, double dx, double dz, Cell c) {
        Tower t = tower(c.band, c.k);
        double ox = dx - t.cx;
        double oz = dz - t.cz;
        double d = oct(ox, oz);
        if (d >= t.R) {
            return;
        }

        BlockState body = switch (t.mat) {
            case 0 -> DEEP_TILE;
            case 1 -> TUFF_BRICK;
            default -> BLACK_BRICK;
        };
        BlockState trim = switch (t.mat) {
            case 0 -> COPPER;
            case 1 -> GILDED;
            default -> DEEP_POLISHED;
        };
        boolean upper = d < t.R2;
        int face = Math.floorMod((int)Math.floor(Math.atan2(oz, ox) * t.R * 0.5), 3);
        // the door faces the colosseum
        double inX = -t.cx;
        double inZ = -t.cz;
        double inL = Math.sqrt(inX * inX + inZ * inZ);
        double along = (ox * inX + oz * inZ) / inL;
        double across = Math.abs(ox * inZ - oz * inX) / inL;
        boolean door = along > t.R - 2.0 && across < 1.5;
        col.set(G, DEEP_POLISHED);
        int top = upper ? t.top : t.setback;
        for (int y = G + 1; y <= top; y++) {
            int rel = y - G;
            double R = y > t.setback ? t.R2 : t.R;
            boolean wall = d > R - 1.25;
            if (wall) {
                BlockState s = rel % 12 == 0 ? trim : body;
                if (face == 0 && rel % 7 >= 2 && rel % 7 <= 5 && rel > 1) {
                    s = t.mat == 2 ? TINTED : GLASS;
                }

                if (door && rel <= 4) {
                    s = AIR;
                }

                col.set(y, s);
            } else {
                BlockState s = AIR;
                if (d < 1.3) {
                    s = DEEP_POLISHED;
                } else if (rel % 7 == 0 || y == t.setback) {
                    s = y == t.setback ? DEEP_POLISHED : PLANKS;
                } else if (rel % 7 == 6 && d < 2.4) {
                    s = LIGHT;
                }

                col.set(y, s);
            }
        }

        if (!upper) {
            // the setback terrace: a floor and a parapet of trim
            col.set(t.setback, d > t.R - 1.25 ? body : DEEP_POLISHED);
            col.set(t.setback + 1, d > t.R - 1.25 && Math.floorMod((int)Math.floor(Math.atan2(oz, ox) * t.R), 2) == 0 ? trim : AIR);
            return;
        }

        // the crown: a copper cone, or a gilded drum with a spike
        int cone = (int)(t.R2 * 2.2);
        if (t.mat != 1) {
            for (int y = 1; y <= cone; y++) {
                double rr = t.R2 * (1.0 - (double)y / cone) + 0.4;
                if (d < rr) {
                    col.set(t.top + y, d > rr - 1.4 ? COPPER : AIR);
                }
            }

            if (d < 0.75) {
                col.set(t.top + cone + 1, Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        } else {
            col.set(t.top + 1, d > t.R2 - 1.25 ? GILDED : AIR);
            col.set(t.top + 2, d > t.R2 - 1.25 && Math.floorMod((int)Math.floor(Math.atan2(oz, ox) * t.R2), 2) == 0 ? GILDED : AIR);
            if (d < 1.0) {
                col.fill(t.top + 1, t.top + 12, GILDED);
                col.set(t.top + 13, Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
    }

    /**
     * Glazed sky-bridges between neighbouring spires of a ring: a deck, glass walls framed in the towers' trim and a
     * roof, at one or two heights, with a doorway cut through each tower wall.
     */
    private static void bridges(ColumnBuffer col, double dx, double dz, double r, double a) {
        for (int b = 0; b < BANDS.length; b++) {
            double rc = (BANDS[b][0] + BANDS[b][1]) * 0.5;
            if (Math.abs(r - rc) > 6.0) {
                continue;
            }

            int n = sectors(b);
            double span = Math.PI * 2.0 / n;
            int k = Math.floorMod((int)Math.floor((a + Math.PI) / span), n);
            for (int j = k - 1; j <= k; j++) {
                bridge(col, dx, dz, b, j, n);
            }
        }
    }

    private static void bridge(ColumnBuffer col, double dx, double dz, int b, int j, int n) {
        int k0 = Math.floorMod(j, n);
        int k1 = Math.floorMod(j + 1, n);
        if (!hasTower(b, k0) || !hasTower(b, k1) || rnd(b, k0, 7, 641) > 0.8) {
            return;
        }

        // both ends stand in the same Spires quarter: a bridge never crosses an avenue
        int q0 = quarterOf(b, k0, n);
        if (q0 != quarterOf(b, k1, n) || QUARTERS[q0] != SPIRES) {
            return;
        }

        Tower t0 = tower(b, k0);
        Tower t1 = tower(b, k1);
        double ex = t1.cx - t0.cx;
        double ez = t1.cz - t0.cz;
        double len = Math.sqrt(ex * ex + ez * ez);
        double ux = ex / len;
        double uz = ez / len;
        double px = dx - t0.cx;
        double pz = dz - t0.cz;
        double along = px * ux + pz * uz;
        double perp = Math.abs(px * uz - pz * ux);
        if (along < 0.0 || along > len || perp > 2.6) {
            return;
        }

        double d0 = oct(dx - t0.cx, dz - t0.cz);
        double d1 = oct(dx - t1.cx, dz - t1.cz);
        int levels = rnd(b, k0, 8, 642) < 0.55 ? 2 : 1;
        int y1 = G + 14 + (int)(rnd(b, k0, 9, 643) * 6.0);
        int y2 = y1 + 14 + (int)(rnd(b, k0, 10, 644) * 4.0);
        int limit = Math.min(t0.setback, t1.setback) - 5;
        BlockState frame = t0.mat == 1 ? GILDED : COPPER;
        for (int L = 0; L < levels; L++) {
            int y = L == 0 ? y1 : y2;
            if (y > limit) {
                continue;
            }

            boolean inside0 = d0 < t0.R - 1.25;
            boolean inside1 = d1 < t1.R - 1.25;
            boolean wall0 = d0 < t0.R && !inside0;
            boolean wall1 = d1 < t1.R && !inside1;
            if (inside0 || inside1) {
                // a landing inside the tower
                if (perp <= 2.6 && (inside0 ? d0 > t0.R - 4.0 : d1 > t1.R - 4.0)) {
                    col.set(y, PLANKS);
                }

                continue;
            }

            if (wall0 || wall1) {
                // the doorway through the tower wall
                if (perp <= 1.6) {
                    col.fill(y + 1, y + 3, AIR);
                }

                continue;
            }

            col.set(y, perp > 1.6 ? DEEP_POLISHED : PLANKS);
            col.set(y - 1, perp <= 1.6 && Math.floorMod((int)Math.floor(along), 5) == 0 ? frame : (perp > 1.6 ? DEEP_BRICK : AIR));
            if (perp > 1.6) {
                boolean post = Math.floorMod((int)Math.floor(along), 4) == 0;
                col.fill(y + 1, y + 3, post ? frame : GLASS);
            }

            col.set(y + 4, perp > 1.6 ? frame : DEEP_TILE);
            if (perp <= 0.6 && Math.floorMod((int)Math.floor(along), 6) == 3) {
                col.set(y + 3, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, Boolean.TRUE));
            }
        }
    }

    // ---------------------------------------------------------------- the Foundry

    /** A long brick hall with deepslate pilasters, tall windows, a copper roof, and often a chimney at its back. */
    private static void foundry(ColumnBuffer col, double r, Cell c, double av) {
        double m = 2.5;
        double ri = r - (c.r0 + m);
        double ro = c.r1 - m - r;
        double sAv = av - AVENUE_HALF - m;
        double si = Math.min(c.arcIn - m, sAv);
        double so = Math.min(c.arcOut - m, sAv);
        if (ri < 0.0 || ro < 0.0 || si < 0.0 || so < 0.0) {
            return;
        }

        // the chimney: a hollow brick stack near the outer wall
        double chimneyR = 2.4;
        double chimneyAt = (si + so) * 0.5;
        double cdx = si - chimneyAt;
        double cdr = ro - 4.0;
        double cd = Math.sqrt(cdx * cdx + cdr * cdr);
        boolean stack = rnd(c.band, c.k, 11, 651) < 0.65;
        int floors = 2 + (int)(rnd(c.band, c.k, 12, 652) * 2.0);
        int top = G + floors * 8;
        if (stack && cd < chimneyR) {
            int h = top + 22 + (int)(rnd(c.band, c.k, 13, 653) * 24.0);
            col.set(G, BRICK);
            for (int y = G + 1; y <= h; y++) {
                if (cd > chimneyR - 1.3) {
                    col.set(y, (y - G) % 10 == 0 ? DEEP_BRICK : BRICK);
                } else {
                    col.set(y, AIR);
                }
            }

            if (cd > chimneyR - 1.3) {
                col.set(h + 1, DEEP_BRICK);
            } else if (cd < 0.8) {
                col.set(h - 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, Boolean.TRUE));
            }

            return;
        }

        boolean radialWall = ri < 1.0 || ro < 1.0;
        boolean sideWall = si < 1.0 || so < 1.0;
        boolean corner = radialWall && sideWall;
        int along = (int)Math.floor(radialWall ? c.arcIn : r);
        boolean pilaster = corner || Math.floorMod(along, 6) == 0;
        boolean door = ri < 1.0 && Math.abs(si - so) < 2.5;
        boolean post = Math.floorMod((int)Math.floor(r), 7) == 3 && Math.floorMod((int)Math.floor(c.arcIn), 7) == 3;
        boolean lamp = Math.floorMod((int)Math.floor(r), 7) == 3 && Math.floorMod((int)Math.floor(c.arcIn), 7) == 4;
        col.set(G, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int y = G + 1; y <= top; y++) {
            int rel = y - G;
            if (radialWall || sideWall) {
                BlockState s = pilaster ? DEEP_POLISHED : BRICK;
                if (!pilaster && rel % 8 >= 2 && rel % 8 <= 6) {
                    s = GLASS;
                }

                if (door && rel <= 5) {
                    s = AIR;
                }

                col.set(y, s);
            } else {
                BlockState s = AIR;
                if (rel % 8 == 0) {
                    s = y == top ? COPPER_W : Blocks.SPRUCE_PLANKS.defaultBlockState();
                } else if (post) {
                    s = DEEP_POLISHED;
                } else if (rel % 8 == 7 && lamp) {
                    s = LIGHT;
                }

                col.set(y, s);
            }
        }

        // a parapet round the flat copper roof
        if (radialWall || sideWall) {
            col.set(top + 1, corner || Math.floorMod(along, 2) == 0 ? DEEP_BRICK : AIR);
        }
    }

    // ---------------------------------------------------------------- the Old Quarter

    private static void oldSpire(ColumnBuffer col, double dx, double dz, Cell c) {
        double cx = Math.cos(c.ac) * c.rc;
        double cz = Math.sin(c.ac) * c.rc;
        double ox = dx - cx;
        double oz = dz - cz;
        double d = Math.sqrt(ox * ox + oz * oz);
        double R = 5.0 + rnd(c.band, c.k, 1, 632) * 2.5;
        if (d >= R) {
            return;
        }

        int top = G + 30 + (int)(rnd(c.band, c.k, 2, 633) * 26.0);
        boolean wall = d > R - 1.3;
        int face = Math.floorMod((int)Math.floor(Math.atan2(oz, ox) * R), 4);
        col.set(G, SMOOTH);
        for (int y = G + 1; y <= top; y++) {
            int rel = y - G;
            if (wall) {
                BlockState s = rel % 9 == 0 ? ORANGE : CUT;
                if (rel % 7 >= 3 && rel % 7 <= 4 && face == 0 && rel > 2) {
                    s = GLASS;
                } else if (rel <= 3 && oz > 0.0 && Math.abs(ox) < 1.0) {
                    s = AIR;
                }

                col.set(y, s);
            } else {
                col.set(y, d < 1.2 ? CUT : (rel % 7 == 0 ? PLANKS : (rel % 7 == 6 && d < 2.2 ? LIGHT : AIR)));
            }
        }

        int cone = (int)(R * 1.6);
        for (int y = 1; y <= cone; y++) {
            double rr = R * (1.0 - (double)y / cone) + 0.4;
            if (d < rr) {
                col.set(top + y, d > rr - 1.4 ? RED : AIR);
            }
        }
    }

    private static void oldHall(ColumnBuffer col, double r, Cell c, double av) {
        double m = 3.0;
        double ri = r - (c.r0 + m);
        double ro = c.r1 - m - r;
        double sAv = av - AVENUE_HALF - m;
        double si = Math.min(c.arcIn - m, sAv);
        double so = Math.min(c.arcOut - m, sAv);
        if (ri < 0.0 || ro < 0.0 || si < 0.0 || so < 0.0) {
            return;
        }

        int floors = 2 + (int)(rnd(c.band, c.k, 3, 641) * 3.0);
        int top = G + floors * 6;
        boolean radialWall = ri < 1.0 || ro < 1.0;
        boolean sideWall = si < 1.0 || so < 1.0;
        boolean corner = radialWall && sideWall;
        int along = (int)Math.floor(radialWall ? c.arcIn : r);
        boolean door = ri < 1.0 && Math.abs(si - so) < 2.0;
        col.set(G, SMOOTH);
        for (int y = G + 1; y <= top; y++) {
            int rel = y - G;
            if (radialWall || sideWall) {
                BlockState s = corner ? CHISELED : CUT;
                if (!corner && rel % 6 >= 2 && rel % 6 <= 3 && Math.floorMod(along, 4) >= 1 && Math.floorMod(along, 4) <= 2) {
                    s = GLASS;
                }

                if (!corner && rel == 1) {
                    s = RED;
                }

                if (door && rel <= 3) {
                    s = AIR;
                }

                col.set(y, s);
            } else {
                boolean post = Math.floorMod((int)Math.floor(r), 7) == 3 && Math.floorMod((int)Math.floor(c.arcIn), 7) == 3;
                col.set(y, rel % 6 == 0 ? PLANKS : (post ? CUT : AIR));
            }
        }

        if (radialWall || sideWall) {
            col.set(top + 1, Math.floorMod(along, 2) == 0 || corner ? SMOOTH : AIR);
        } else {
            col.set(top, SMOOTH);
        }
    }

    /** An open square with a planted island: room for a titan to stand in and for you to land. */
    private static void plaza(ColumnBuffer col, double dx, double dz, Cell c) {
        double ox = dx - Math.cos(c.ac) * c.rc;
        double oz = dz - Math.sin(c.ac) * c.rc;
        double d = Math.sqrt(ox * ox + oz * oz);
        if (d < 4.0) {
            col.set(G, d > 3.2 ? BLACK_CHISELED : Blocks.GRASS_BLOCK.defaultBlockState());
            if (d < 0.75) {
                col.fill(G + 1, G + 5, Blocks.DARK_OAK_LOG.defaultBlockState());
            }

            if (d < 3.0) {
                col.fill(d < 0.75 ? G + 6 : G + 4, G + 7 - (int)d, Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE));
            }
        } else if (((int)Math.floor(d)) % 4 == 0) {
            col.set(G, TUFF_POLISHED);
        }
    }
}
