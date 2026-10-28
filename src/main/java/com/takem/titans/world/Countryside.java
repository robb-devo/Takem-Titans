package com.takem.titans.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The land between the districts. Inside the Walls: roads between the gates with lamp posts, a wooden watchtower every
 * hundred-odd blocks along them (something to hook onto between the districts), and farmsteads - a barn, its fields,
 * hay, a paddock, sometimes a windmill. Outside Wall Maria: the shells of villages the titans emptied, and the fenced
 * forward outposts of the Survey Corps, each with a tower and a flag.
 * <p>
 * Like the town plan, everything is worked out from the column's position alone.
 */
public final class Countryside {
    private static final double CX = 0.0;
    private static final double CZ = -1100.0;
    /** Ring roads round the inside of Wall Rose and Wall Maria, and the radials between them. */
    private static final double ROSE_RING = 568.0;
    private static final double MARIA_RING = 958.0;
    private static final double TOWER_STEP = 110.0;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();
    private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState LOG = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState DARK = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState STONE_BRICK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
    private static final BlockState HAY = Blocks.HAY_BLOCK.defaultBlockState();

    private Countryside() {
    }

    private static double rnd(int x, int y, int z, int seed) {
        return WorldNoise.rand(x, y, z, seed);
    }

    /** True when the column was built here and the ordinary grass and trees should be left out. */
    static boolean column(ColumnBuffer col, int h, ShiganshinaLayout.Region region) {
        int x = col.x;
        int z = col.z;
        double px = x + 0.5;
        double pz = z + 0.5;
        double wall = ShiganshinaLayout.distToWall(px, pz);
        if (wall < 6.0) {
            return false;
        }

        if (region == ShiganshinaLayout.Region.MARIA) {
            return road(col, h, px, pz, wall) || farmstead(col, h, px, pz, wall);
        } else if (region == ShiganshinaLayout.Region.OUTSIDE) {
            return outpost(col, h, px, pz, wall) || ruins(col, h, px, pz, wall);
        }

        return false;
    }

    /** Levels a column to a structure's floor: fills under it, clears over it. */
    private static void level(ColumnBuffer col, int h, int base, BlockState surface) {
        if (h < base) {
            col.fill(h + 1, base - 1, Blocks.DIRT.defaultBlockState());
        } else if (h > base) {
            col.fill(base + 1, h + 1, AIR);
        }

        col.set(base, surface);
    }

    // ---------------------------------------------------------------- roads and their towers

    /** Distance across a road, and how far along it, for every road there is; the nearest one wins. */
    private static double[] nearestRoad(double px, double pz) {
        double dx = px - CX;
        double dz = pz - CZ;
        double r = Math.sqrt(dx * dx + dz * dz);
        double a = Math.atan2(dz, dx);
        double best = 1.0E9;
        double along = 0.0;
        double ring = 0.0;
        double side = 1.0;
        for (double R : new double[]{ROSE_RING, MARIA_RING}) {
            double d = Math.abs(r - R);
            if (d < best) {
                best = d;
                along = (a + Math.PI) * R;
                ring = R;
                side = Math.signum(r - R);
            }
        }

        // radials east, west and north from Wall Sina to Wall Maria (south is the old Shiganshina-Trost road)
        for (int k = 0; k < 3; k++) {
            double ang = k == 0 ? 0.0 : (k == 1 ? Math.PI : -Math.PI / 2.0);
            double ux = Math.cos(ang);
            double uz = Math.sin(ang);
            double t = dx * ux + dz * uz;
            if (t > 256.0 && t < 994.0) {
                double s = dx * uz - dz * ux;
                double d = Math.abs(s);
                if (d < best) {
                    best = d;
                    along = t + k * 10000.0;
                    ring = -(k + 1);
                    side = Math.signum(s);
                }
            }
        }

        return new double[]{best, along, ring, side == 0.0 ? 1.0 : side};
    }

    /** Where a watchtower stands: {@code slot} along the road, eight blocks off it on the given side. */
    private static double[] towerAt(double slot, double ring, double side) {
        if (ring > 0.0) {
            double ang = slot / ring - Math.PI;
            double rc = ring + 8.0 * side;
            return new double[]{CX + Math.cos(ang) * rc, CZ + Math.sin(ang) * rc};
        }

        int k = (int)(-ring) - 1;
        double ang = k == 0 ? 0.0 : (k == 1 ? Math.PI : -Math.PI / 2.0);
        double ux = Math.cos(ang);
        double uz = Math.sin(ang);
        double t = slot - k * 10000.0;
        return new double[]{CX + ux * t + uz * 8.0 * side, CZ + uz * t - ux * 8.0 * side};
    }

    private static boolean road(ColumnBuffer col, int h, double px, double pz, double wall) {
        double[] nr = nearestRoad(px, pz);
        double d = nr[0];
        double along = nr[1];
        int x = col.x;
        int z = col.z;
        if (d < 2.6) {
            double n = rnd(x, 3, z, 701);
            col.set(h, d < 1.5 ? (n < 0.85 ? PATH : COARSE) : (n < 0.5 ? COARSE : Blocks.GRAVEL.defaultBlockState()));
            return true;
        }

        // lamp posts along the verge
        if (d >= 3.2 && d < 4.2 && Math.floorMod((int)Math.floor(along), 48) == 0) {
            col.set(h, COARSE);
            col.fill(h + 1, h + 3, FENCE);
            col.set(h + 4, Blocks.LANTERN.defaultBlockState());
            return true;
        }

        // a watchtower every hundred and ten blocks, set back off the road
        double slot = Math.floor(along / TOWER_STEP + 0.5) * TOWER_STEP;
        double off = along - slot;
        if (Math.abs(off) <= 4.0 && d >= 4.0 && d <= 12.0 && wall > 20.0) {
            double[] c = towerAt(slot, nr[2], nr[3]);
            double lx = px - c[0];
            double lz = pz - c[1];
            if (Math.abs(lx) <= 3.5 && Math.abs(lz) <= 3.5) {
                int base = ShiganshinaLayout.terrainHeight((int)Math.floor(c[0]), (int)Math.floor(c[1]));
                level(col, h, base, STONE_BRICK);
                return tower(col, base, lx, lz, (int)Math.floor(slot), 28 + (int)(rnd((int)slot, 4, (int)nr[2], 702) * 12.0), false);
            }
        }

        return false;
    }

    /**
     * A watchtower: a rubble-stone base, a timber shaft on log corners with slit windows and a floor every eight, and at
     * the top a railed lookout gallery hanging a block out past the walls under a stepped slate roof. Everything up there
     * stays within a block or two of the corner posts, so a tower that loses its base comes down whole.
     * {@code lx, lz} are the column's place relative to the tower's centre.
     */
    private static boolean tower(ColumnBuffer col, int h, double lx, double lz, int seed, int height, boolean stone) {
        int ax = (int)Math.round(Math.abs(lx));
        int az = (int)Math.round(Math.abs(lz));
        if (ax > 3 || az > 3) {
            return false;
        }

        int base = h;
        int top = base + height;
        int stoneTop = base + Math.max(6, height / 3);
        BlockState slate = Blocks.DEEPSLATE_TILES.defaultBlockState();
        col.set(base, STONE_BRICK);
        if (ax == 3 || az == 3) {
            // only the gallery and the eaves reach out this far
            col.set(top, PLANKS);
            col.set(top + 1, FENCE);
            col.set(top + 4, slate);
            return true;
        }

        boolean corner = ax == 2 && az == 2;
        boolean edge = ax == 2 || az == 2;
        for (int y = base + 1; y <= top + 3; y++) {
            int rel = y - base;
            BlockState s = AIR;
            if (y <= top) {
                if (corner) {
                    s = y <= stoneTop ? STONE_BRICK : LOG;
                } else if (edge) {
                    s = y <= stoneTop || stone ? (rnd(seed, y, ax * 7 + az, 703) < 0.3 ? MOSSY : COBBLE) : PLANKS;
                    if (y == stoneTop) {
                        s = STONE_BRICK;
                    }

                    if (rel % 8 >= 5 && rel % 8 <= 6 && (ax == 0 || az == 0)) {
                        s = AIR;
                    }

                    if (rel <= 3 && ax == 0 && lz > 0.0) {
                        s = AIR;
                    }
                } else if (rel % 8 == 0 || y == top) {
                    s = DARK;
                }
            } else if (y <= top + 2) {
                s = corner ? LOG : AIR;
            } else {
                s = slate;
            }

            col.set(y, s);
        }

        // the roof steps in to a point
        if (ax <= 2 && az <= 2) {
            col.set(top + 4, slate);
        }

        if (ax <= 1 && az <= 1) {
            col.set(top + 5, slate);
        }

        if (ax == 0 && az == 0) {
            col.set(top + 6, Blocks.SPRUCE_FENCE.defaultBlockState());
            col.set(top + 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.TRUE));
        }

        return true;
    }

    // ---------------------------------------------------------------- farmsteads

    private static boolean farmstead(ColumnBuffer col, int h, double px, double pz, double wall) {
        int ci = Math.floorDiv((int)Math.floor(px), 150);
        int cj = Math.floorDiv((int)Math.floor(pz), 150);
        if (rnd(ci, 5, cj, 711) > 0.5) {
            return false;
        }

        double fx = ci * 150 + 75 + (rnd(ci, 6, cj, 711) - 0.5) * 50.0;
        double fz = cj * 150 + 75 + (rnd(ci, 7, cj, 711) - 0.5) * 50.0;
        double dx = px - fx;
        double dz = pz - fz;
        if (Math.abs(dx) > 46.0 || Math.abs(dz) > 46.0) {
            return false;
        }

        // keep clear of the walls, the roads and the districts
        if (ShiganshinaLayout.distToWall(fx, fz) < 60.0 || nearestRoad(fx, fz)[0] < 50.0
            || ShiganshinaLayout.region(fx, fz) != ShiganshinaLayout.Region.MARIA) {
            return false;
        }

        int base = ShiganshinaLayout.terrainHeight((int)Math.floor(fx), (int)Math.floor(fz));
        int x = col.x;
        int z = col.z;
        // the barn: thirteen by nine, gable roof along its length
        if (Math.abs(dx) <= 6.5 && Math.abs(dz) <= 4.5) {
            level(col, h, base, Blocks.PACKED_MUD.defaultBlockState());
            int ix = (int)Math.round(dx);
            int iz = (int)Math.round(dz);
            int ax = Math.abs(ix);
            int az = Math.abs(iz);
            boolean wallX = ax == 6;
            boolean wallZ = az == 4;
            int ridge = base + 6 + (4 - az);
            for (int y = base + 1; y <= ridge; y++) {
                BlockState s = AIR;
                if (y == ridge) {
                    s = DARK;
                } else if (wallX || wallZ) {
                    boolean post = (ax == 6 || ax % 3 == 0) && wallZ || wallX && az == 4;
                    s = post ? LOG.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : (y <= base + 1 ? COBBLE : (y == base + 4 && !post && ax % 3 == 1 && wallZ ? Blocks.SPRUCE_TRAPDOOR.defaultBlockState() : PLANKS));
                    if (wallZ && ax <= 1 && y <= base + 4) {
                        s = AIR;
                    }
                    if (wallZ && y > base + 6) {
                        s = AIR;
                    }
                } else if (y == base + 6) {
                    s = PLANKS;
                }

                col.set(y, s);
            }

            if (!wallX && !wallZ && ax == 4 && az <= 2) {
                col.fill(base + 1, base + 2 + az % 2, HAY);
            }

            return true;
        }

        double d = Math.sqrt(dx * dx + dz * dz);
        // the windmill, on some farms
        boolean mill = rnd(ci, 8, cj, 711) < 0.45;
        double mx = dx + 16.0;
        double mz = dz + 12.0;
        if (mill && Math.abs(mx) <= 8.5 && Math.abs(mz) <= 4.5) {
            return windmill(col, h, base, mx, mz);
        }

        // a paddock with a fence round it
        double kx = dx - 16.0;
        double kz = dz;
        double kd = Math.max(Math.abs(kx), Math.abs(kz));
        if (kd <= 7.5) {
            level(col, h, base, Blocks.GRASS_BLOCK.defaultBlockState());
            if (kd > 6.5 && !(Math.abs(kz) < 1.0 && kx < 0.0)) {
                col.set(base + 1, FENCE);
            } else if (rnd(x, 8, z, 712) < 0.04) {
                col.set(base + 1, HAY);
            }

            return true;
        }

        // the yard: a few haystacks and a cart path
        if (d < 12.0) {
            level(col, h, base, rnd(x, 9, z, 713) < 0.7 ? COARSE : PATH);
            if (rnd(x, 10, z, 713) < 0.03) {
                col.fill(base + 1, base + 1 + (int)(rnd(x, 11, z, 713) * 3.0), HAY);
            }

            return true;
        }

        // a fence round the fields, with a gap for the cart path
        if (d >= 43.0 && d < 44.0) {
            if (!(Math.abs(dz) < 2.0 && dx > 0.0)) {
                col.set(h + 1, FENCE);
            }

            return true;
        }

        // the odd scarecrow standing in the crops
        if (d > 16.0 && d < 42.0 && Math.floorMod(x, 23) == 7 && Math.floorMod(z, 9) == 5 && rnd(x, 30, z, 714) < 0.25) {
            col.set(h, Blocks.GRASS_BLOCK.defaultBlockState());
            col.fill(h + 1, h + 2, FENCE);
            col.set(h + 3, HAY);
            col.set(h + 4, Blocks.CARVED_PUMPKIN.defaultBlockState());
            return true;
        }

        // the fields: rows of crops with a water ditch every ninth row
        if (d < 43.0) {
            int strip = Math.floorDiv(z, 9);
            if (Math.floorMod(z, 9) == 0) {
                col.set(h, PATH);
            } else if (Math.floorMod(z, 9) == 4 && Math.floorMod(x, 14) == 0) {
                col.set(h, Blocks.WATER.defaultBlockState());
            } else {
                col.set(h, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                BlockState crop = switch (Math.floorMod(strip * 5 + ci + cj, 4)) {
                    case 0 -> Blocks.CARROTS.defaultBlockState();
                    case 1 -> Blocks.POTATOES.defaultBlockState();
                    default -> Blocks.WHEAT.defaultBlockState();
                };
                col.set(h + 1, crop.setValue(CropBlock.AGE, 7));
            }

            return true;
        }

        return false;
    }

    /** A round stone mill with a cone roof and four sails in the plane facing south. */
    private static boolean windmill(ColumnBuffer col, int h, int base, double mx, double mz) {
        double d = Math.sqrt(mx * mx + mz * mz);
        int top = base + 16;
        int hub = top - 2;
        if (d < 3.2) {
            level(col, h, base, COBBLE);
            for (int y = base + 1; y <= top; y++) {
                col.set(y, d > 2.0 ? (y % 5 == 0 ? STONE_BRICK : COBBLE) : (y == top ? DARK : AIR));
            }

            for (int y = 1; y <= 5; y++) {
                double rr = 3.4 * (1.0 - y / 6.0);
                if (d < rr) {
                    col.set(top + y, d > rr - 1.3 ? DARK : AIR);
                }
            }

            return true;
        }

        // the sails stand just off the tower's south face
        int sx = (int)Math.round(mx);
        int sz = (int)Math.round(mz);
        if (sz == 4) {
            boolean any = false;
            for (int y = hub - 7; y <= hub + 7; y++) {
                int dy = y - hub;
                boolean arm = Math.abs(sx) <= 7 && Math.abs(dy) <= 1 && sx != 0 || Math.abs(sx) <= 1 && Math.abs(dy) <= 7;
                if (arm) {
                    boolean spar = sx == 0 || dy == 0;
                    col.set(y, spar ? LOG : Blocks.WHITE_WOOL.defaultBlockState());
                    any = true;
                }
            }

            return any;
        }

        return false;
    }

    // ---------------------------------------------------------------- outside the Walls

    /** A Survey Corps forward outpost: a palisade with a gate, a tower, supplies and a red flag. */
    private static boolean outpost(ColumnBuffer col, int h, double px, double pz, double wall) {
        int ci = Math.floorDiv((int)Math.floor(px), 260);
        int cj = Math.floorDiv((int)Math.floor(pz), 260);
        if (rnd(ci, 12, cj, 721) > 0.32) {
            return false;
        }

        double ox = ci * 260 + 130 + (rnd(ci, 13, cj, 721) - 0.5) * 120.0;
        double oz = cj * 260 + 130 + (rnd(ci, 14, cj, 721) - 0.5) * 120.0;
        double dx = px - ox;
        double dz = pz - oz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 15.0 || ShiganshinaLayout.region(ox, oz) != ShiganshinaLayout.Region.OUTSIDE || ShiganshinaLayout.distToWall(ox, oz) < 40.0
            || ShiganshinaLayout.forestFactor(ox, oz) > 0.2) {
            return false;
        }

        int base = ShiganshinaLayout.terrainHeight((int)Math.floor(ox), (int)Math.floor(oz));
        if (base < 64) {
            return false;
        }

        int x = col.x;
        int z = col.z;
        if (d > 13.5) {
            // the palisade, with a gate to the south
            level(col, h, base, COARSE);
            if (!(dz > 0.0 && Math.abs(dx) < 2.0)) {
                col.fill(base + 1, base + 4 + (int)(rnd(x, 15, z, 722) * 2.0), LOG);
            }

            return true;
        }

        level(col, h, base, rnd(x, 16, z, 723) < 0.6 ? COARSE : Blocks.GRASS_BLOCK.defaultBlockState());
        if (tower(col, base, dx, dz, ci * 31 + cj, 30, false)) {
            return true;
        }

        // the flag: a pole with a red banner of wool
        if (Math.abs(dx - 7.0) < 0.5 && Math.abs(dz + 6.0) < 0.5) {
            col.fill(base + 1, base + 12, FENCE);
            return true;
        }

        if (Math.abs(dz + 6.0) < 0.5 && dx > 7.5 && dx < 11.5) {
            col.fill(base + 10, base + 12, Blocks.RED_WOOL.defaultBlockState());
            return true;
        }

        // supplies and a fire
        if (Math.abs(dx + 7.0) < 2.5 && Math.abs(dz - 4.0) < 1.5) {
            col.set(base + 1, Blocks.BARREL.defaultBlockState());
            if (rnd(x, 17, z, 724) < 0.5) {
                col.set(base + 2, Blocks.BARREL.defaultBlockState());
            }

            return true;
        }

        if (Math.abs(dx - 5.0) < 0.5 && Math.abs(dz - 5.0) < 0.5) {
            col.set(base + 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, Boolean.FALSE));
        }

        return true;
    }

    /** What the titans left of a village: roofless shells of rubble stone, grass growing inside. */
    private static boolean ruins(ColumnBuffer col, int h, double px, double pz, double wall) {
        int ci = Math.floorDiv((int)Math.floor(px), 200);
        int cj = Math.floorDiv((int)Math.floor(pz), 200);
        if (rnd(ci, 18, cj, 731) > 0.4) {
            return false;
        }

        double vx = ci * 200 + 100 + (rnd(ci, 19, cj, 731) - 0.5) * 80.0;
        double vz = cj * 200 + 100 + (rnd(ci, 20, cj, 731) - 0.5) * 80.0;
        if (Math.abs(px - vx) > 40.0 || Math.abs(pz - vz) > 40.0 || ShiganshinaLayout.forestFactor(vx, vz) > 0.2
            || ShiganshinaLayout.distToWall(vx, vz) < 40.0) {
            return false;
        }

        int x = col.x;
        int z = col.z;
        for (int k = 0; k < 6; k++) {
            if (rnd(ci * 7 + k, 21, cj, 732) > 0.75) {
                continue;
            }

            double hx = vx + (rnd(ci * 7 + k, 22, cj, 732) - 0.5) * 64.0;
            double hz = vz + (rnd(ci * 7 + k, 23, cj, 732) - 0.5) * 64.0;
            boolean wide = rnd(ci * 7 + k, 24, cj, 732) < 0.5;
            double hw = wide ? 5.0 : 3.5;
            double hd = wide ? 3.5 : 5.0;
            double lx = px - hx;
            double lz = pz - hz;
            if (Math.abs(lx) <= hw + 0.5 && Math.abs(lz) <= hd + 0.5) {
                int base = ShiganshinaLayout.terrainHeight((int)Math.floor(hx), (int)Math.floor(hz));
                if (base < 63) {
                    return false;
                }

                level(col, h, base, rnd(x, 25, z, 733) < 0.5 ? COARSE : Blocks.GRASS_BLOCK.defaultBlockState());
                boolean shell = Math.abs(lx) > hw - 0.5 || Math.abs(lz) > hd - 0.5;
                if (shell) {
                    // broken off at whatever height the titans left it
                    int left = 1 + (int)(rnd(x, 26, z, 734) * rnd(x, 27, z, 734) * 7.0);
                    for (int y = base + 1; y <= base + left; y++) {
                        col.set(y, rnd(x, y, z, 735) < 0.35 ? MOSSY : COBBLE);
                    }
                } else if (rnd(x, 28, z, 736) < 0.12) {
                    col.set(base + 1, rnd(x, 29, z, 736) < 0.5 ? Blocks.SHORT_GRASS.defaultBlockState() : COBBLE);
                }

                return true;
            }
        }

        return false;
    }
}
