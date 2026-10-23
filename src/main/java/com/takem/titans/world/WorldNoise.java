package com.takem.titans.world;

public final class WorldNoise {
    private WorldNoise() {
    }

    public static int hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1442695041 + seed * 699999953;
        h = (h ^ h >>> 13) * 1274126177;
        return h ^ h >>> 16;
    }

    public static double rand(int x, int y, int z, int seed) {
        return (double)(hash(x, y, z, seed) & 16777215) / 1.6777216E7;
    }

    public static double rand2(int x, int z, int seed) {
        return rand(x, 0, z, seed);
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    public static double value2(double x, double z, int seed) {
        int ix = (int)Math.floor(x);
        int iz = (int)Math.floor(z);
        double fx = smooth(x - (double)ix);
        double fz = smooth(z - (double)iz);
        double a = rand2(ix, iz, seed);
        double b = rand2(ix + 1, iz, seed);
        double c = rand2(ix, iz + 1, seed);
        double d = rand2(ix + 1, iz + 1, seed);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    public static double fbm2(double x, double z, int seed, int octaves) {
        double total = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double f = 1.0;

        for (int o = 0; o < octaves; o++) {
            total += value2(x * f, z * f, seed + o * 31) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }

        return total / norm;
    }
}
