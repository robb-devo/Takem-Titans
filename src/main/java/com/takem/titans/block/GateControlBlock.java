package com.takem.titans.block;

import com.mojang.serialization.MapCodec;
import com.takem.titans.registry.ModBlocks;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public class GateControlBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<GateControlBlock> CODEC = simpleCodec(GateControlBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final int REACH_XZ = 18;
    private static final int REACH_DOWN = 56;
    private static final int REACH_UP = 8;
    private static final int TICKS_PER_ROW = 3;
    private static final List<GateControlBlock.Motion> MOTIONS = new ArrayList<>();

    public GateControlBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, Boolean.valueOf(false)));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            for (GateControlBlock.Motion m : MOTIONS) {
                if (m.level == server && !m.rows.isEmpty() && m.rows.get(0).get(0).distManhattan(pos) < 92) {
                    player.displayClientMessage(Component.translatable("message.takemtitans.gate_moving"), true);
                    return InteractionResult.CONSUME;
                }
            }

            TreeMap<Integer, List<BlockPos>> byY = new TreeMap<>();
            Boolean open = null;

            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-18, -56, -18), pos.offset(18, 8, 18))) {
                BlockState s = server.getBlockState(p);
                if (s.is(ModBlocks.WALL_GATE.get())) {
                    if (open == null) {
                        open = s.getValue(WallGateBlock.OPEN);
                    }

                    byY.computeIfAbsent(p.getY(), k -> new ArrayList<>()).add(p.immutable());
                }
            }

            if (open == null) {
                player.displayClientMessage(Component.translatable("message.takemtitans.no_gate"), true);
                return InteractionResult.CONSUME;
            }

            boolean target = !open;
            List<List<BlockPos>> rows = new ArrayList<>(byY.values());
            if (!target) {
                Collections.reverse(rows);
            }

            MOTIONS.add(new GateControlBlock.Motion(server, rows, target));
            server.setBlock(pos, state.setValue(OPEN, Boolean.valueOf(target)), 3);
            server.playSound(null, pos, ModSounds.GATE_MOVE.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
            player.displayClientMessage(Component.translatable(target ? "message.takemtitans.gate_opening" : "message.takemtitans.gate_closing"), true);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && !MOTIONS.isEmpty()) {
            Iterator<GateControlBlock.Motion> it = MOTIONS.iterator();

            while (it.hasNext()) {
                GateControlBlock.Motion m = it.next();
                if (m.level == level && ++m.tick % 3 == 0) {
                    if (m.row >= m.rows.size()) {
                        it.remove();
                    } else {
                        List<BlockPos> row = m.rows.get(m.row++);

                        for (BlockPos p : row) {
                            BlockState s = level.getBlockState(p);
                            if (s.is(ModBlocks.WALL_GATE.get())) {
                                level.setBlock(p, s.setValue(WallGateBlock.OPEN, Boolean.valueOf(m.open)), 2);
                            }
                        }

                        BlockPos mid = row.get(row.size() / 2);
                        if (m.row % 3 == 0) {
                            level.playSound(null, mid, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 2.5F, 0.5F);
                        }

                        if (m.row == m.rows.size()) {
                            level.playSound(null, mid, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.5F, 0.5F);
                        }
                    }
                }
            }

            return;
        }
    }

    public static int smashGate(ServerLevel level, BlockPos center, int rxz, int down, int up) {
        int n = 0;

        for (BlockPos p : BlockPos.betweenClosed(center.offset(-rxz, -down, -rxz), center.offset(rxz, up, rxz))) {
            if (level.getBlockState(p).is(ModBlocks.WALL_GATE.get())) {
                level.removeBlock(p, false);
                n++;
            }
        }

        return n;
    }

    private static final class Motion {
        final ServerLevel level;
        final List<List<BlockPos>> rows;
        final boolean open;
        int tick;
        int row;

        Motion(ServerLevel level, List<List<BlockPos>> rows, boolean open) {
            this.level = level;
            this.rows = rows;
            this.open = open;
        }
    }
}
