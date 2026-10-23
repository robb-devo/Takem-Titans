package com.takem.titans.world;

import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class Attractors {
    static final List<Attractors.Attractor> ACTIVE = new ArrayList<>();

    private Attractors() {
    }

    public static Attractors.Attractor add(Level level, Vec3 c, double reach, double strength) {
        Attractors.Attractor a = new Attractors.Attractor(level.dimension(), c, reach, strength);
        ACTIVE.add(a);
        return a;
    }

    public static void remove(Attractors.Attractor a) {
        a.active = false;
        ACTIVE.remove(a);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    @Nullable
    public static Vec3 accel(Level level, Vec3 p) {
        if (!ACTIVE.isEmpty() && !level.isClientSide) {
            Vec3 sum = null;

            for (Attractors.Attractor a : ACTIVE) {
                if (a.dim == level.dimension()) {
                    Vec3 to = a.c.subtract(p);
                    double d = to.length();
                    if (!(d > a.reach) && !(d < 0.001)) {
                        double f = a.strength * (0.35 + 0.65 * (1.0 - d / a.reach));
                        Vec3 in = to.scale(1.0 / d);
                        Vec3 swirl = new Vec3(-in.z, 0.0, in.x).scale(f * 0.8);
                        Vec3 acc = in.scale(f).add(swirl).add(0.0, 0.04, 0.0);
                        sum = sum == null ? acc : sum.add(acc);
                    }
                }
            }

            return sum;
        } else {
            return null;
        }
    }

    public static boolean swallow(Entity e) {
        if (!ACTIVE.isEmpty() && !e.level().isClientSide) {
            for (Attractors.Attractor a : ACTIVE) {
                if (a.dim == e.level().dimension()) {
                    double r = 2.5 + (e instanceof FallingSectionEntity f ? (double)f.radius * 0.5 : 0.0);
                    if (!(e.position().distanceToSqr(a.c) > r * r)) {
                        if (e instanceof FallingSectionEntity fx) {
                            List<BlockState> got = fx.takeBlocks(null);
                            if (a.swallowed.size() < 3000) {
                                a.swallowed.addAll(got);
                            }
                        } else if (e instanceof DebrisEntity d) {
                            if (a.swallowed.size() < 3000) {
                                a.swallowed.add(d.state());
                            }

                            d.discard();
                        }

                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public static final class Attractor {
        final ResourceKey<Level> dim;
        public final Vec3 c;
        public double reach;
        public double strength;
        public final List<BlockState> swallowed = new ArrayList<>();
        public boolean active = true;

        Attractor(ResourceKey<Level> dim, Vec3 c, double reach, double strength) {
            this.dim = dim;
            this.c = c;
            this.reach = reach;
            this.strength = strength;
        }
    }
}
