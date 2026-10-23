package com.takem.titans.world;

import net.minecraft.util.Mth;

public final class ShiganshinaLayout {
    public static final int GROUND = 66;
    public static final int SEA_LEVEL = 63;
    public static final int WALL_HEIGHT = 50;
    public static final int WALL_TOP = 116;
    public static final int WALL_THICKNESS = 12;
    public static final double MARIA_Z = -1100.0;
    public static final double MARIA_R = 1000.0;
    public static final double DISTRICT_Z = -100.0;
    public static final double DISTRICT_R = 200.0;
    public static final double ROSE_Z = -1100.0;
    public static final double ROSE_R = 600.0;
    public static final double SINA_Z = -1100.0;
    public static final double SINA_R = 250.0;
    public static final int MITRAS_OFFSET = -1100;
    public static final double TROST_Z = -500.0;
    public static final double TROST_R = 200.0;
    public static final int TROST_OFFSET = -400;
    public static final int TROST_SPAWN_Z = -396;
    public static final int INNER_GATE_Z = -100;
    public static final int OUTER_GATE_Z = 100;
    public static final int GATE_HALF_WIDTH = 7;
    public static final int GATE_HEIGHT = 22;
    public static final double FOREST_X = -560.0;
    public static final double FOREST_Z = 520.0;
    public static final double FOREST_R = 300.0;
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Z = 4;
    public static final ShiganshinaLayout.Bulge[] BULGES = new ShiganshinaLayout.Bulge[]{
        new ShiganshinaLayout.Bulge(2, "shiganshina", 0.0, -100.0, 200.0, 0),
        new ShiganshinaLayout.Bulge(4, "trost", 0.0, -500.0, 200.0, 1),
        new ShiganshinaLayout.Bulge(6, "karanes", 600.0, -1100.0, 180.0, 1),
        new ShiganshinaLayout.Bulge(7, "krolva", -600.0, -1100.0, 180.0, 1),
        new ShiganshinaLayout.Bulge(8, "stohess", 250.0, -1100.0, 110.0, 2),
        new ShiganshinaLayout.Bulge(9, "ehrmich", -250.0, -1100.0, 110.0, 2)
    };
    private static final double HALF = 6.0;

    private ShiganshinaLayout() {
    }

    public static double mariaDist(double x, double z) {
        double dz = z - -1100.0;
        return Math.sqrt(x * x + dz * dz);
    }

    public static double districtDist(double x, double z) {
        double dz = z - -100.0;
        return Math.sqrt(x * x + dz * dz);
    }

    public static double roseDist(double x, double z) {
        double dz = z - -1100.0;
        return Math.sqrt(x * x + dz * dz);
    }

    public static double sinaDist(double x, double z) {
        double dz = z - -1100.0;
        return Math.sqrt(x * x + dz * dz);
    }

    public static double trostDist(double x, double z) {
        double dz = z - -500.0;
        return Math.sqrt(x * x + dz * dz);
    }

    private static int zone(double x, double z) {
        double dm = mariaDist(x, z);
        if (dm > 1000.0) {
            return 0;
        } else if (roseDist(x, z) > 600.0) {
            return 1;
        } else {
            return sinaDist(x, z) > 250.0 ? 2 : -1;
        }
    }

    public static ShiganshinaLayout.Bulge bulgeAt(double x, double z) {
        int zone = zone(x, z);

        for (ShiganshinaLayout.Bulge b : BULGES) {
            if (b.zone == zone && b.dist(x, z) <= b.r + 6.0) {
                return b;
            }
        }

        return null;
    }

    public static ShiganshinaLayout.Bulge bulge(int ring) {
        for (ShiganshinaLayout.Bulge b : BULGES) {
            if (b.ring == ring) {
                return b;
            }
        }

        return null;
    }

    public static int ring(double x, double z) {
        double dm = mariaDist(x, z);
        if (Math.abs(dm - 1000.0) <= 6.0) {
            return 1;
        } else {
            if (dm < 1006.0) {
                double dr = roseDist(x, z);
                if (Math.abs(dr - 600.0) <= 6.0) {
                    return 3;
                }

                if (dr < 606.0 && Math.abs(sinaDist(x, z) - 250.0) <= 6.0) {
                    return 5;
                }
            }

            ShiganshinaLayout.Bulge b = bulgeAt(x, z);
            return b != null && Math.abs(b.dist(x, z) - b.r) <= 6.0 ? b.ring : 0;
        }
    }

    public static double ringX(int ring) {
        ShiganshinaLayout.Bulge b = bulge(ring);
        return b == null ? 0.0 : b.cx;
    }

    public static double ringZ(int ring) {
        ShiganshinaLayout.Bulge b = bulge(ring);
        return b == null ? -1100.0 : b.cz;
    }

    public static double ringR(int ring) {
        return switch (ring) {
            case 1 -> 1000.0;
            default -> {
                ShiganshinaLayout.Bulge b = bulge(ring);
                yield b == null ? 200.0 : b.r;
            }
            case 3 -> 600.0;
            case 5 -> 250.0;
        };
    }

    public static int districtOffset(double z) {
        return z < -800.0 ? -1100 : (z < -200.0 ? -400 : 0);
    }

    public static double wallDepth(double x, double z) {
        double dm = mariaDist(x, z);
        if (Math.abs(dm - 1000.0) <= 6.0) {
            return dm - 994.0;
        } else {
            if (dm < 1006.0) {
                double dr = roseDist(x, z);
                if (Math.abs(dr - 600.0) <= 6.0) {
                    return dr - 594.0;
                }

                double ds = sinaDist(x, z);
                if (Math.abs(ds - 250.0) <= 6.0) {
                    return ds - 244.0;
                }
            }

            ShiganshinaLayout.Bulge b = bulgeAt(x, z);
            if (b != null) {
                double d = b.dist(x, z);
                if (Math.abs(d - b.r) <= 6.0) {
                    return d - (b.r - 6.0);
                }
            }

            return -1.0;
        }
    }

    public static boolean isWall(double x, double z) {
        return wallDepth(x, z) >= 0.0;
    }

    private static ShiganshinaLayout.Region bulgeRegion(double x, double z, ShiganshinaLayout.Region fallback) {
        ShiganshinaLayout.Bulge b = bulgeAt(x, z);
        if (b == null) {
            return fallback;
        } else {
            double d = b.dist(x, z);
            return d < b.r - 6.0 ? ShiganshinaLayout.Region.DISTRICT : ShiganshinaLayout.Region.WALL;
        }
    }

    public static ShiganshinaLayout.Region region(double x, double z) {
        double dm = mariaDist(x, z);
        if (dm < 994.0) {
            double dr = roseDist(x, z);
            if (dr < 594.0) {
                double ds = sinaDist(x, z);
                if (ds < 244.0) {
                    return ShiganshinaLayout.Region.DISTRICT;
                } else {
                    return ds <= 256.0 ? ShiganshinaLayout.Region.WALL : bulgeRegion(x, z, ShiganshinaLayout.Region.MARIA);
                }
            } else {
                return dr <= 606.0 ? ShiganshinaLayout.Region.WALL : bulgeRegion(x, z, ShiganshinaLayout.Region.MARIA);
            }
        } else {
            return dm <= 1006.0 ? ShiganshinaLayout.Region.WALL : bulgeRegion(x, z, ShiganshinaLayout.Region.OUTSIDE);
        }
    }

    public static boolean insideWalls(double x, double z) {
        ShiganshinaLayout.Region r = region(x, z);
        return r == ShiganshinaLayout.Region.MARIA || r == ShiganshinaLayout.Region.DISTRICT;
    }

    private static boolean nearZone(double x, double z, int zone) {
        return switch (zone) {
            case 0 -> mariaDist(x, z) > 994.0;
            case 1 -> mariaDist(x, z) < 1006.0 && roseDist(x, z) > 594.0;
            default -> roseDist(x, z) < 606.0 && sinaDist(x, z) > 244.0;
        };
    }

    public static double distToWall(double x, double z) {
        double dm = mariaDist(x, z);
        double best = Math.max(0.0, Math.abs(dm - 1000.0) - 6.0);
        if (dm < 1006.0) {
            double dr = roseDist(x, z);
            best = Math.min(best, Math.max(0.0, Math.abs(dr - 600.0) - 6.0));
            if (dr < 606.0) {
                best = Math.min(best, Math.max(0.0, Math.abs(sinaDist(x, z) - 250.0) - 6.0));
            }
        }

        for (ShiganshinaLayout.Bulge b : BULGES) {
            if (nearZone(x, z, b.zone)) {
                best = Math.min(best, Math.max(0.0, Math.abs(b.dist(x, z) - b.r) - 6.0));
            }
        }

        return best;
    }

    public static boolean inGateOpening(int x, int y, int z) {
        if (Math.abs((double)x + 0.5) > 7.0) {
            return false;
        } else {
            int h = gateHeight(x);
            return y > 66 && y <= 66 + h ? nearGate(x, z) != 0 : false;
        }
    }

    public static int gateHeight(int x) {
        double t = ((double)x + 0.5) / 7.0;
        return (int)Math.round(22.0 - 6.0 * t * t * t * t);
    }

    public static int nearGate(int x, int z) {
        if (Math.abs(x) > 24) {
            return 0;
        } else {
            double depth = wallDepth((double)x + 0.5, (double)z + 0.5);
            if (depth < 0.0) {
                return 0;
            } else {
                int ring = ring((double)x + 0.5, (double)z + 0.5);
                return ring != 2 && ring != 4 ? -1 : 1;
            }
        }
    }

    public static boolean isGateDoorPlane(double depth) {
        double mid = 6.0;
        return depth >= mid - 1.0 && depth < mid + 1.0;
    }

    public static double forestFactor(double x, double z) {
        double dx = x - -560.0;
        double dz = z - 520.0;
        double d = Math.sqrt(dx * dx + dz * dz);
        double edge = 300.0 * (0.85 + 0.25 * WorldNoise.value2(x * 0.004, z * 0.004, 911));
        return Mth.clamp((edge - d) / 60.0, 0.0, 1.0);
    }

    public static int terrainHeight(int x, int z) {
        ShiganshinaLayout.Region r = region((double)x + 0.5, (double)z + 0.5);
        if (r == ShiganshinaLayout.Region.DISTRICT) {
            return 66;
        } else {
            double n = WorldNoise.fbm2((double)x * 0.006, (double)z * 0.006, 17, 3) - 0.5;
            double fine = WorldNoise.fbm2((double)x * 0.03, (double)z * 0.03, 23, 2) - 0.5;
            double amp = r == ShiganshinaLayout.Region.MARIA ? 6.0 : 10.0;
            double h = 66.0 + n * amp * 2.0 + fine * 3.0;
            double lake = WorldNoise.value2((double)x * 0.0045, (double)z * 0.0045, 77);
            if (lake > 0.78 && r == ShiganshinaLayout.Region.OUTSIDE) {
                h -= (lake - 0.78) * 60.0;
            }

            double near = Mth.clamp(distToWall((double)x + 0.5, (double)z + 0.5) / 48.0, 0.0, 1.0);
            h = Mth.lerp(near * near * (3.0 - 2.0 * near), 66.0, h);
            double f = forestFactor((double)x, (double)z);
            h = Mth.lerp(f * 0.6, h, 67.0);
            return (int)Math.floor(h);
        }
    }

    public static record Bulge(int ring, String name, double cx, double cz, double r, int zone) {
        public double dist(double x, double z) {
            double dx = x - this.cx;
            double dz = z - this.cz;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    public static enum Region {
        MARIA,
        DISTRICT,
        WALL,
        OUTSIDE;
    }
}
