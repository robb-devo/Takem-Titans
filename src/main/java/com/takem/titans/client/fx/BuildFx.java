package com.takem.titans.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The builders at work, as you see it: every block they put back snaps into place inside a frame of warm light that
 * shrinks onto it, while chips of its own material fly in from round about.
 */
public final class BuildFx {
    private BuildFx() {
    }

    public static void placed(ClientLevel level, Vec3 at, int stateId) {
        BlockState s = Block.stateById(stateId);
        RandomSource rnd = level.random;
        WorldFx.add(new Frame(at));
        if (!s.isAir()) {
            BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, s);
            for (int i = 0; i < 4; i++) {
                Vec3 o = ClientFx.randomDir(rnd).scale(1.6);
                level.addParticle(chips, at.x + o.x, at.y + o.y, at.z + o.z, -o.x * 0.18, -o.y * 0.18 + 0.05, -o.z * 0.18);
            }
        }
    }

    /** A cube of light the size of a block, shrinking onto it and fading. */
    static final class Frame extends WorldFx.Effect {
        final Vec3 c;

        Frame(Vec3 c) {
            super(9);
            this.c = c;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            double h = Mth.lerp(p, 0.85, 0.5);
            float a = 0.55F * (1.0F - p);
            float w = 0.05F;
            for (int axis = 0; axis < 3; axis++) {
                for (int s1 = -1; s1 <= 1; s1 += 2) {
                    for (int s2 = -1; s2 <= 1; s2 += 2) {
                        Vec3 a0;
                        Vec3 a1;
                        if (axis == 0) {
                            a0 = this.c.add(-h, s1 * h, s2 * h);
                            a1 = this.c.add(h, s1 * h, s2 * h);
                        } else if (axis == 1) {
                            a0 = this.c.add(s1 * h, -h, s2 * h);
                            a1 = this.c.add(s1 * h, h, s2 * h);
                        } else {
                            a0 = this.c.add(s1 * h, s2 * h, -h);
                            a1 = this.c.add(s1 * h, s2 * h, h);
                        }

                        d.ribbon(new Vec3[]{a0, a1}, new float[]{w, w}, 1.0F, 0.86F, 0.55F, new float[]{a, a});
                    }
                }
            }
        }
    }
}
