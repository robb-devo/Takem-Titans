package com.takem.titans.block;

import com.mojang.serialization.MapCodec;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SupplyStationBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SupplyStationBlock> CODEC = simpleCodec(SupplyStationBlock::new);
    private static final VoxelShape SHAPE_NS = Shapes.or(
        Block.box(0.0, 0.0, 1.0, 16.0, 10.0, 15.0), Block.box(1.5, 10.0, 3.0, 7.5, 16.0, 13.0), Block.box(8.5, 10.0, 3.0, 14.5, 16.0, 13.0)
    );
    private static final VoxelShape SHAPE_EW = Shapes.or(
        Block.box(1.0, 0.0, 0.0, 15.0, 10.0, 16.0), Block.box(3.0, 10.0, 1.5, 13.0, 16.0, 7.5), Block.box(3.0, 10.0, 8.5, 13.0, 16.0, 14.5)
    );

    public SupplyStationBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp && OdmGear.hasGear(player)) {
                // the drill is about finding the station and using it, full tanks or not
                com.takem.titans.story.Training.on(sp, com.takem.titans.story.Training.SUPPLY);
            }

            if (OdmGear.resupply(player)) {
                level.playSound(null, pos, ModSounds.REFILL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                ((ServerLevel)level)
                    .sendParticles(
                        ModParticles.GAS.get(), (double)pos.getX() + 0.5, (double)pos.getY() + 1.05, (double)pos.getZ() + 0.5, 18, 0.3, 0.15, 0.3, 0.03
                    );
                player.displayClientMessage(Component.translatable("message.takemtitans.resupplied"), true);
            } else {
                player.displayClientMessage(
                    Component.translatable(OdmGear.hasGear(player) ? "message.takemtitans.already_full" : "message.takemtitans.no_gear"), true
                );
            }

            return InteractionResult.CONSUME;
        }
    }
}
