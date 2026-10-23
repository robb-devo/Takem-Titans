package com.takem.titans.registry;

import com.takem.titans.block.GateControlBlock;
import com.takem.titans.block.SupplyStationBlock;
import com.takem.titans.block.WallGateBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;

public final class ModBlocks {
    public static final Blocks BLOCKS = DeferredRegister.createBlocks("takemtitans");
    public static final DeferredBlock<Block> WALL_STONE = BLOCKS.register("wall_stone", () -> new Block(wallStone()));
    public static final DeferredBlock<Block> WALL_STONE_BRICKS = BLOCKS.register("wall_stone_bricks", () -> new Block(wallStone()));
    public static final DeferredBlock<Block> WALL_STONE_TILES = BLOCKS.register("wall_stone_tiles", () -> new Block(wallStone()));
    public static final DeferredBlock<Block> CHISELED_WALL_STONE = BLOCKS.register("chiseled_wall_stone", () -> new Block(wallStone()));
    public static final DeferredBlock<StairBlock> WALL_STONE_STAIRS = BLOCKS.register(
        "wall_stone_stairs", () -> new StairBlock(WALL_STONE_BRICKS.get().defaultBlockState(), wallStone())
    );
    public static final DeferredBlock<SlabBlock> WALL_STONE_SLAB = BLOCKS.register("wall_stone_slab", () -> new SlabBlock(wallStone()));
    public static final DeferredBlock<DropExperienceBlock> ICE_BURST_ORE = BLOCKS.register(
        "ice_burst_ore",
        () -> new DropExperienceBlock(
                UniformInt.of(2, 5),
                Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F)
                    .lightLevel(s -> 5)
            )
    );
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_ICE_BURST_ORE = BLOCKS.register(
        "deepslate_ice_burst_ore",
        () -> new DropExperienceBlock(
                UniformInt.of(2, 5),
                Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(s -> 5)
            )
    );
    public static final DeferredBlock<SupplyStationBlock> SUPPLY_STATION = BLOCKS.register(
        "supply_station", () -> new SupplyStationBlock(Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 6.0F).sound(SoundType.WOOD).noOcclusion())
    );
    public static final DeferredBlock<WallGateBlock> WALL_GATE = BLOCKS.register(
        "wall_gate",
        () -> new WallGateBlock(
                Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .isViewBlocking((s, l, p) -> !s.getValue(WallGateBlock.OPEN))
                    .isSuffocating((s, l, p) -> !s.getValue(WallGateBlock.OPEN))
                    .isRedstoneConductor((s, l, p) -> false)
            )
    );
    public static final DeferredBlock<GateControlBlock> GATE_CONTROL = BLOCKS.register(
        "gate_control", () -> new GateControlBlock(Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F).sound(SoundType.METAL).noOcclusion())
    );

    private ModBlocks() {
    }

    private static Properties wallStone() {
        return Properties.of()
            .mapColor(MapColor.SAND)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .strength(25.0F, 1200.0F)
            .sound(SoundType.STONE)
            .pushReaction(PushReaction.BLOCK);
    }
}
