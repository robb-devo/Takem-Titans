package com.takem.titans.block;

import com.takem.titans.world.Waystones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A waystone: a dark obelisk with a crystal of Paths light in its head. Touch one and its district is yours to travel
 * to from any other; use it again and the map of the walls opens.
 */
public class WaystoneBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

    public WaystoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Waystones.use(sp, pos);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        // motes of light drift up out of the crystal and runes spiral in towards it
        double x = (double)pos.getX() + 0.5;
        double y = (double)pos.getY() + 1.05;
        double z = (double)pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.END_ROD, x + (rnd.nextDouble() - 0.5) * 0.3, y, z + (rnd.nextDouble() - 0.5) * 0.3, 0.0, 0.02 + rnd.nextDouble() * 0.03, 0.0);
        for (int i = 0; i < 2; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            level.addParticle(ParticleTypes.ENCHANT, x + Math.cos(a) * 1.4, y + rnd.nextDouble(), z + Math.sin(a) * 1.4, -Math.cos(a) * 1.2, -0.4, -Math.sin(a) * 1.2);
        }
    }
}
