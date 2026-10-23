package com.takem.titans.entity.titan;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class PureTitan extends AbstractTitan {
    private static final float[] HEAD_SCALE = new float[]{1.0F, 1.22F, 0.92F, 1.1F, 1.0F, 1.32F};
    private static final float[] HEAD_TILT = new float[]{1.0F, 0.4F, 1.6F, 0.8F, 2.2F, 0.6F};

    public PureTitan(EntityType<? extends PureTitan> type, Level level) {
        super(type, level);
    }

    @Override
    protected float randomHeight(RandomSource random) {
        float h = 7.0F + random.nextFloat() * 6.0F + random.nextFloat() * 3.0F;
        return Math.min(15.0F, h);
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.05 + (double)height * 0.016;
    }

    @Override
    public int titanKind() {
        return 0;
    }

    @Override
    public int variantCount() {
        return HEAD_SCALE.length;
    }

    @Override
    public float headScale() {
        return HEAD_SCALE[Math.floorMod(this.getVariant(), HEAD_SCALE.length)];
    }

    @Override
    public float headTilt() {
        return HEAD_TILT[Math.floorMod(this.getVariant(), HEAD_TILT.length)];
    }
}
