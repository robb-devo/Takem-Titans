package com.takem.titans.world;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class GiantTrees {
    static final int CELL = 34;
    private static final BlockState LOG_Y = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState LOG_X = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.X);
    private static final BlockState LOG_Z = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.Z);
    private static final BlockState WOOD = Blocks.SPRUCE_WOOD.defaultBlockState();
    private static final BlockState LEAVES = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.valueOf(true));
    private static final BlockState LEAVES_2 = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.valueOf(true));
    private static final Map<Long, Optional<GiantTrees.Tree>> CACHE = new ConcurrentHashMap<>();

    private GiantTrees() {
    }

    static GiantTrees.Tree tree(int i, int j) {
        return CACHE.computeIfAbsent((long)i << 32 ^ (long)j & 4294967295L, k -> Optional.ofNullable(makeTree(i, j))).orElse(null);
    }

    private static GiantTrees.Tree makeTree(int i, int j) {
        double cx = (double)(i * 34 + 8) + WorldNoise.rand(i, 1, j, 501) * 18.0;
        double cz = (double)(j * 34 + 8) + WorldNoise.rand(i, 2, j, 501) * 18.0;
        double f = ShiganshinaLayout.forestFactor(cx, cz);
        if (f < 0.25 || WorldNoise.rand(i, 3, j, 501) > 0.35 + f * 0.6) {
            return null;
        } else if (ShiganshinaLayout.distToWall(cx, cz) < 40.0) {
            return null;
        } else {
            int base = ShiganshinaLayout.terrainHeight((int)Math.floor(cx), (int)Math.floor(cz));
            double radius = 2.8 + WorldNoise.rand(i, 4, j, 501) * 2.4 * (0.6 + 0.4 * f);
            int height = (int)(55.0 + WorldNoise.rand(i, 5, j, 501) * 40.0 * (0.5 + 0.5 * f));
            int n = 9 + (int)(WorldNoise.rand(i, 6, j, 501) * 7.0);
            GiantTrees.Branch[] branches = new GiantTrees.Branch[n];

            for (int k = 0; k < n; k++) {
                double a = WorldNoise.rand(i, 10 + k, j, 502) * 3.141592653589793 * 2.0;
                double hFrac = ((double)k + WorldNoise.rand(i, 30 + k, j, 502)) / (double)n;
                double sy = (double)(base + 24) + hFrac * (double)(height - 30);
                double len = (9.0 + WorldNoise.rand(i, 50 + k, j, 502) * 10.0) * (1.0 - 0.45 * hFrac);
                double rise = 0.12 + WorldNoise.rand(i, 70 + k, j, 502) * 0.35;
                double thick = 1.0 + (1.0 - hFrac) * 0.8;
                double leafR = 3.5 + WorldNoise.rand(i, 90 + k, j, 502) * 2.5;
                branches[k] = new GiantTrees.Branch(cx, sy, cz, Math.cos(a), Math.sin(a), rise, len, thick, leafR);
            }

            return new GiantTrees.Tree(i, j, cx, cz, base, radius, height, branches);
        }
    }

    /** The giant tree whose reach covers this column, the nearest one where two crowns overlap. */
    @Nullable
    public static GiantTrees.Tree near(double x, double z) {
        int ci = Math.floorDiv(Mth.floor(x), CELL);
        int cj = Math.floorDiv(Mth.floor(z), CELL);
        GiantTrees.Tree best = null;
        double bestD = 30.5 * 30.5;

        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                GiantTrees.Tree t = tree(i, j);
                if (t != null) {
                    double d = (x - t.cx) * (x - t.cx) + (z - t.cz) * (z - t.cz);
                    if (d < bestD) {
                        bestD = d;
                        best = t;
                    }
                }
            }
        }

        return best;
    }

    /** Whether this block is a living part of one of the giant trees: its trunk, a bough or the foliage the generator gave it. */
    @Nullable
    public static GiantTrees.Tree owner(Level level, BlockPos pos, BlockState s) {
        boolean log = s.is(Blocks.SPRUCE_LOG) || s.is(Blocks.SPRUCE_WOOD);
        boolean leaf = s.is(Blocks.SPRUCE_LEAVES) || s.is(Blocks.DARK_OAK_LEAVES);
        if ((log || leaf) && level instanceof ServerLevel sl && TitanSpawner.isShiganshina(sl)) {
            GiantTrees.Tree t = near(pos.getX() + 0.5, pos.getZ() + 0.5);
            if (t != null && pos.getY() >= t.base - 2 && pos.getY() <= t.topY()) {
                int k = shape(t, pos.getX(), pos.getY(), pos.getZ());
                return log && (k == TRUNK || k == BOUGH) || leaf && k == LEAF ? t : null;
            }
        }

        return null;
    }

    public static final byte TRUNK = 1;
    public static final byte BOUGH = 2;
    public static final byte LEAF = 3;

    /** What the generator put at this spot for this tree, ignoring the random gaps in the foliage. */
    public static int shape(GiantTrees.Tree t, int x, int y, int z) {
        byte[] col = new byte[1];
        shapeColumn(t, x, z, col, y);
        return col[0];
    }

    /** Fills {@code out[y - y0]} with the shape codes of one column, in the generator's order: foliage, then boughs, then trunk. */
    public static void shapeColumn(GiantTrees.Tree t, int x, int z, byte[] out, int y0) {
        double px = x + 0.5;
        double pz = z + 0.5;
        double dx = px - t.cx;
        double dz = pz - t.cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 30.0) {
            return;
        }

        for (GiantTrees.Branch b : t.branches) {
            double hd = Math.hypot(px - (b.sx + b.dx * b.len), pz - (b.sz + b.dz * b.len));
            if (hd < b.leafR) {
                double ey = b.sy + b.rise * b.len;
                double half = Math.sqrt(b.leafR * b.leafR - hd * hd) * 0.65;
                fill(out, y0, (int)Math.floor(ey - half), (int)Math.ceil(ey + half), LEAF);
            }
        }

        double crownR = t.radius + 7.0;
        if (d < crownR) {
            double crownY = t.base + t.height - 2;
            double half = Math.sqrt(crownR * crownR - d * d) * 0.5;
            fill(out, y0, (int)Math.floor(crownY - half), (int)Math.ceil(crownY + half), LEAF);
        }

        for (GiantTrees.Branch b : t.branches) {
            double along = dx * b.dx + dz * b.dz;
            if (!(along < t.radius * 0.5) && !(along > b.len)) {
                double perp = Math.abs(-dx * b.dz + dz * b.dx);
                double th = b.thick * (1.0 - 0.45 * along / b.len);
                if (!(perp > th)) {
                    double yc = b.sy + b.rise * along;
                    double half = Math.sqrt(Math.max(0.0, th * th - perp * perp));
                    fill(out, y0, (int)Math.floor(yc - half), (int)Math.floor(yc + half), BOUGH);
                }
            }
        }

        double ang = Math.atan2(dz, dx);
        for (int y = t.base - 2; y <= t.base + t.height; y++) {
            double r = t.trunkRadius(y);
            double flare = Math.max(0.0, 1.0 - (double)(y - t.base) / 7.0);
            double roots = flare * (1.6 + 1.2 * Math.max(0.0, Math.cos(ang * 5.0 + (double)t.i * 1.7 + (double)t.j)));
            if (d < r + roots) {
                fill(out, y0, y, y, TRUNK);
            }
        }
    }

    private static void fill(byte[] out, int y0, int from, int to, byte v) {
        for (int y = Math.max(from, y0); y <= to && y - y0 < out.length; y++) {
            out[y - y0] = v;
        }
    }

    public static boolean nearForest(int x, int z) {
        return ShiganshinaLayout.forestFactor((double)x, (double)z) > 0.0
            || ShiganshinaLayout.forestFactor((double)(x + 30), (double)z) > 0.0
            || ShiganshinaLayout.forestFactor((double)(x - 30), (double)z) > 0.0
            || ShiganshinaLayout.forestFactor((double)x, (double)(z + 30)) > 0.0
            || ShiganshinaLayout.forestFactor((double)x, (double)(z - 30)) > 0.0;
    }

    public static void column(ColumnBuffer col) {
        int x = col.x;
        int z = col.z;
        int ci = Math.floorDiv(x, 34);
        int cj = Math.floorDiv(z, 34);

        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                GiantTrees.Tree t = tree(i, j);
                if (t != null) {
                    draw(col, t, i, j);
                }
            }
        }
    }

    private static void draw(ColumnBuffer col, GiantTrees.Tree t, int ti, int tj) {
        double px = (double)col.x + 0.5;
        double pz = (double)col.z + 0.5;
        double dx = px - t.cx;
        double dz = pz - t.cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (!(d > 30.0)) {
            for (GiantTrees.Branch b : t.branches) {
                double ex = b.sx + b.dx * b.len;
                double ez = b.sz + b.dz * b.len;
                double ey = b.sy + b.rise * b.len;
                double hd = Math.hypot(px - ex, pz - ez);
                if (hd < b.leafR) {
                    double half = Math.sqrt(b.leafR * b.leafR - hd * hd) * 0.65;

                    for (int y = (int)Math.floor(ey - half); y <= (int)Math.ceil(ey + half); y++) {
                        if (!(WorldNoise.rand(col.x, y, col.z, 503) < 0.12) && col.isAir(y)) {
                            col.set(y, WorldNoise.rand(col.x, y, col.z, 504) < 0.3 ? LEAVES_2 : LEAVES);
                        }
                    }
                }
            }

            double crownR = t.radius + 7.0;
            double crownY = (double)(t.base + t.height - 2);
            if (d < crownR) {
                double half = Math.sqrt(crownR * crownR - d * d) * 0.5;

                for (int yx = (int)Math.floor(crownY - half); yx <= (int)Math.ceil(crownY + half); yx++) {
                    if (!(WorldNoise.rand(col.x, yx, col.z, 505) < 0.1) && col.isAir(yx)) {
                        col.set(yx, WorldNoise.rand(col.x, yx, col.z, 506) < 0.3 ? LEAVES_2 : LEAVES);
                    }
                }
            }

            for (GiantTrees.Branch bx : t.branches) {
                double along = dx * bx.dx + dz * bx.dz;
                if (!(along < t.radius * 0.5) && !(along > bx.len)) {
                    double perp = Math.abs(-dx * bx.dz + dz * bx.dx);
                    double th = bx.thick * (1.0 - 0.45 * along / bx.len);
                    if (!(perp > th)) {
                        double yc = bx.sy + bx.rise * along;
                        double half = Math.sqrt(Math.max(0.0, th * th - perp * perp));
                        BlockState log = Math.abs(bx.dx) > Math.abs(bx.dz) ? LOG_X : LOG_Z;

                        for (int yxx = (int)Math.floor(yc - half); yxx <= (int)Math.floor(yc + half); yxx++) {
                            col.set(yxx, log);
                        }
                    }
                }
            }

            int top = t.base + t.height;

            for (int yxx = t.base - 2; yxx <= top; yxx++) {
                double h = (double)(yxx - t.base) / (double)t.height;
                double r = t.radius * (1.0 - 0.45 * h);
                double flare = Math.max(0.0, 1.0 - (double)(yxx - t.base) / 7.0);
                double ang = Math.atan2(dz, dx);
                double roots = flare * (1.6 + 1.2 * Math.max(0.0, Math.cos(ang * 5.0 + (double)ti * 1.7 + (double)tj)));
                if (d < r + roots) {
                    col.set(yxx, d < r - 1.2 ? WOOD : LOG_Y);
                }
            }
        }
    }

    static record Branch(double sx, double sy, double sz, double dx, double dz, double rise, double len, double thick, double leafR) {
    }

    public static record Tree(int i, int j, double cx, double cz, int base, double radius, int height, GiantTrees.Branch[] branches) {
        /** Highest block the crown or a leaf cluster can reach. */
        public int topY() {
            double top = this.base + this.height - 2 + (this.radius + 7.0) * 0.5;

            for (GiantTrees.Branch b : this.branches) {
                top = Math.max(top, b.sy + b.rise * b.len + b.leafR * 0.65);
            }

            return (int)Math.ceil(top) + 1;
        }

        public double trunkRadius(int y) {
            return this.radius * (1.0 - 0.45 * (double)(y - this.base) / (double)this.height);
        }
    }
}
