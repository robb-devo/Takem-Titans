package com.takem.titans.world;

import com.takem.titans.entity.FallingSectionEntity;
import java.util.Comparator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public final class Chunks {
    public static final int STRUCTURE = 0;
    public static final int ANYTHING = 1;
    public static final TicketType<ChunkPos> TICKET = TicketType.create("takemtitans_event", Comparator.comparingLong(ChunkPos::toLong), 60);
    private static final int UPDATE = 50;

    private Chunks() {
    }

    @Nullable
    public static FallingSectionEntity rip(ServerLevel level, BlockPos c, int rad, int mode, int minBlocks) {
        RandomSource rnd = level.random;
        int side = 2 * rad + 1;
        long[] pos = new long[side * side * side];
        BlockState[] st = new BlockState[pos.length];
        int n = 0;
        MutableBlockPos m = new MutableBlockPos();

        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= rad * rad + rnd.nextInt(3)) {
                        m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        BlockState s = level.getBlockState(m);
                        if (takes(level, m, s, mode)) {
                            pos[n] = m.asLong();
                            st[n] = s;
                            n++;
                        }
                    }
                }
            }
        }

        if (n >= minBlocks && FallingSectionEntity.budgetFor(n)) {
            for (int i = 0; i < n; i++) {
                level.setBlock(BlockPos.of(pos[i]), Blocks.AIR.defaultBlockState(), 50);
                Rebuild.mark(level, BlockPos.of(pos[i]));
            }

            return FallingSectionEntity.create(level, pos, st, n, null, null, null);
        } else {
            return null;
        }
    }

    static boolean takes(ServerLevel level, BlockPos p, BlockState s, int mode) {
        if (!s.isAir() && s.getFluidState().isEmpty()) {
            float h = s.getDestroySpeed(level, p);
            if (h < 0.0F || h > 60.0F) {
                return false;
            } else if (s.getCollisionShape(level, p).isEmpty()) {
                return false;
            } else {
                return mode == 1
                    ? true
                    : Destruction.isStructural(level, p, s) || s.is(BlockTags.WITHER_IMMUNE) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES);
            }
        } else {
            return false;
        }
    }

    public static Vector3f tumble(RandomSource rnd, float s) {
        return new Vector3f((rnd.nextFloat() - 0.5F) * s, (rnd.nextFloat() - 0.5F) * s * 0.6F, (rnd.nextFloat() - 0.5F) * s);
    }
}
