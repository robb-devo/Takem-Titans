package com.takem.titans.fx;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Effects that an entity's client-side copy draws for itself, without a packet: a flung block that hits the street
 * knows that on the client the same tick it happens. The client installs the implementation at startup; on a
 * dedicated server nothing is installed and every call is a no-op.
 */
public final class LocalFx {
    private static LocalFx.Sink sink;

    private LocalFx() {
    }

    public static void install(LocalFx.Sink s) {
        sink = s;
    }

    /** A flung block lands hard: {@code kick} is how fast it came down, {@code v} its motion before the hit. */
    public static void debrisImpact(Level level, Vec3 at, BlockState s, double kick, Vec3 v) {
        if (sink != null && level.isClientSide) {
            sink.debrisImpact(level, at, s, kick, v);
        }
    }

    /** Bits crumbling off a falling piece of building as it goes: {@code at} is a block on it, {@code v} its motion. */
    public static void crumbleOff(Level level, Vec3 at, BlockState s, Vec3 v, float dust) {
        if (sink != null && level.isClientSide) {
            sink.crumbleOff(level, at, s, v, dust);
        }
    }

    public interface Sink {
        void debrisImpact(Level level, Vec3 at, BlockState s, double kick, Vec3 v);

        void crumbleOff(Level level, Vec3 at, BlockState s, Vec3 v, float dust);
    }
}
