package com.takem.titans.world;

import java.util.Arrays;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ColumnBuffer {
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public final int minY;
    public final BlockState[] states;
    public int x;
    public int z;

    public ColumnBuffer(int minY, int height) {
        this.minY = minY;
        this.states = new BlockState[height];
    }

    public void reset(int x, int z) {
        this.x = x;
        this.z = z;
        Arrays.fill(this.states, AIR);
    }

    public void set(int y, BlockState state) {
        int i = y - this.minY;
        if (i >= 0 && i < this.states.length) {
            this.states[i] = state;
        }
    }

    public BlockState get(int y) {
        int i = y - this.minY;
        return i >= 0 && i < this.states.length ? this.states[i] : AIR;
    }

    public void fill(int y0, int y1, BlockState state) {
        for (int y = Math.max(y0, this.minY); y <= y1 && y - this.minY < this.states.length; y++) {
            this.states[y - this.minY] = state;
        }
    }

    public boolean isAir(int y) {
        return this.get(y).isAir();
    }

    public int top() {
        for (int i = this.states.length - 1; i >= 0; i--) {
            if (!this.states[i].isAir()) {
                return i + this.minY;
            }
        }

        return this.minY - 1;
    }
}
