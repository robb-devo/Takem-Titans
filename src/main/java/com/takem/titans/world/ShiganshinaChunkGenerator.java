package com.takem.titans.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.takem.titans.block.WallGateBlock;
import com.takem.titans.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class ShiganshinaChunkGenerator extends ChunkGenerator {
    public static final MapCodec<ShiganshinaChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, i.stable(ShiganshinaChunkGenerator::new))
    );
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static BlockState winch(Direction facing) {
        return ModBlocks.GATE_CONTROL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    private static BlockState supply(Direction facing) {
        return ModBlocks.SUPPLY_STATION.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    private static void place(int x, int y, int z, BlockState state) {
        ShiganshinaChunkGenerator.Fixed.PLACEMENTS
            .computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>())
            .add(new ShiganshinaChunkGenerator.Placement(y, state));
    }

    static int wallCentreZ(int x, boolean outer) {
        return outer
            ? (int)Math.floor(-100.0 + Math.sqrt(40000.0 - ((double)x + 0.5) * ((double)x + 0.5)))
            : (int)Math.floor(-1100.0 + Math.sqrt(1000000.0 - ((double)x + 0.5) * ((double)x + 0.5)));
    }

    private static void placeOnWall(int x, boolean outer, ShiganshinaChunkGenerator.GateWinch facing) {
        place(x, 117, wallCentreZ(x, outer), winch(facing == ShiganshinaChunkGenerator.GateWinch.NORTH ? Direction.NORTH : Direction.SOUTH));
    }

    private static void placeOnRing(int x, double cz, double r, Direction facing, boolean winch) {
        int z = (int)Math.floor(cz + Math.sqrt(r * r - ((double)x + 0.5) * ((double)x + 0.5)));
        place(x, 117, z, winch ? winch(facing) : supply(facing));
    }

    private static void placeSupplyOnWall(int x, boolean outer, Direction facing) {
        place(x, 117, wallCentreZ(x, outer), supply(facing));
    }

    public ShiganshinaChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structures, ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight();
        int height = chunk.getHeight();
        ColumnBuffer col = new ColumnBuffer(minY, height);
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Types.WORLD_SURFACE_WG);
        ChunkPos cp = chunk.getPos();
        boolean forest = GiantTrees.nearForest(cp.getMiddleBlockX(), cp.getMiddleBlockZ());
        MutableBlockPos pos = new MutableBlockPos();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx;
                int z = cp.getMinBlockZ() + lz;
                col.reset(x, z);
                this.buildColumn(col, forest);

                for (int i = 0; i < height; i++) {
                    BlockState s = col.states[i];
                    if (!s.isAir()) {
                        int y = minY + i;
                        chunk.setBlockState(pos.set(lx, y, lz), s, false);
                        ocean.update(lx, y, lz, s);
                        surface.update(lx, y, lz, s);
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    void buildColumn(ColumnBuffer col, boolean forest) {
        int x = col.x;
        int z = col.z;
        double depth = ShiganshinaLayout.wallDepth((double)x + 0.5, (double)z + 0.5);
        ShiganshinaLayout.Region region = ShiganshinaLayout.region((double)x + 0.5, (double)z + 0.5);
        int h = depth >= 0.0 ? 66 : ShiganshinaLayout.terrainHeight(x, z);
        this.ground(col, h, region);
        if (depth >= 0.0) {
            this.wall(col, depth);
        } else if (region == ShiganshinaLayout.Region.DISTRICT) {
            if (ShiganshinaLayout.inArena((double)x + 0.5, (double)z + 0.5)) {
                ArenaPlan.column(col);
            } else {
                TownPlan.column(col);
            }
        } else {
            this.nature(col, h, region);
            if (forest) {
                GiantTrees.column(col);
            }
        }

        List<ShiganshinaChunkGenerator.Placement> extra = ShiganshinaChunkGenerator.Fixed.PLACEMENTS.get(ChunkPos.asLong(x, z));
        if (extra != null) {
            for (ShiganshinaChunkGenerator.Placement p : extra) {
                col.set(p.y, p.state);
            }
        }
    }

    private void ground(ColumnBuffer col, int h, ShiganshinaLayout.Region region) {
        int x = col.x;
        int z = col.z;
        col.set(col.minY, BEDROCK);

        for (int y = col.minY + 1; y <= h - 4; y++) {
            if (y < col.minY + 4 && WorldNoise.rand(x, y, z, 3) < 0.5) {
                col.set(y, BEDROCK);
            } else {
                col.set(y, ore(x, y, z, y < 0));
            }
        }

        boolean underwater = h < 63;
        boolean beach = !underwater && h <= 64 && region == ShiganshinaLayout.Region.OUTSIDE;
        col.fill(h - 3, h - 1, !beach && !underwater ? DIRT : SAND);
        col.set(h, underwater ? (WorldNoise.rand(x, 0, z, 8) < 0.5 ? SAND : GRAVEL) : (beach ? SAND : GRASS));
        if (underwater) {
            col.fill(h + 1, 63, WATER);
        }
    }

    private static BlockState ore(int x, int y, int z, boolean deep) {
        int cx = Math.floorDiv(x, 5);
        int cy = Math.floorDiv(y, 5);
        int cz = Math.floorDiv(z, 5);
        double pick = WorldNoise.rand(cx, cy, cz, 41);
        if (pick < 0.86) {
            return deep ? DEEPSLATE : STONE;
        } else {
            double ox = (double)(cx * 5 + 1) + WorldNoise.rand(cx, cy, cz, 42) * 3.0;
            double oy = (double)(cy * 5 + 1) + WorldNoise.rand(cx, cy, cz, 43) * 3.0;
            double oz = (double)(cz * 5 + 1) + WorldNoise.rand(cx, cy, cz, 44) * 3.0;
            double dx = (double)x + 0.5 - ox;
            double dy = (double)y + 0.5 - oy;
            double dz = (double)z + 0.5 - oz;
            double size = pick > 0.985 ? 0.9 : 1.4;
            if (dx * dx + dy * dy + dz * dz > size * size) {
                return deep ? DEEPSLATE : STONE;
            } else {
                double t = (pick - 0.86) / 0.14;
                if (t < 0.32) {
                    return y > 0 ? Blocks.COAL_ORE.defaultBlockState() : (deep ? DEEPSLATE : STONE);
                } else if (t < 0.55) {
                    return deep ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
                } else if (t < 0.68) {
                    return y > -16 ? (deep ? Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState() : Blocks.COPPER_ORE.defaultBlockState()) : DEEPSLATE;
                } else if (t < 0.8) {
                    return deep ? ModBlocks.DEEPSLATE_ICE_BURST_ORE.get().defaultBlockState() : ModBlocks.ICE_BURST_ORE.get().defaultBlockState();
                } else if (t < 0.86) {
                    return y < 24 ? (deep ? Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState() : Blocks.GOLD_ORE.defaultBlockState()) : STONE;
                } else if (t < 0.92) {
                    return y < 12
                        ? (deep ? Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState() : Blocks.REDSTONE_ORE.defaultBlockState())
                        : (deep ? DEEPSLATE : STONE);
                } else if (t < 0.96) {
                    return y < 24 ? (deep ? Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState() : Blocks.LAPIS_ORE.defaultBlockState()) : STONE;
                } else {
                    return y < -10 ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState() : (deep ? DEEPSLATE : STONE);
                }
            }
        }
    }

    private static double arcPosition(int x, int z) {
        int ring = Math.max(1, ShiganshinaLayout.ring((double)x + 0.5, (double)z + 0.5));
        return Math.atan2((double)z + 0.5 - ShiganshinaLayout.ringZ(ring), (double)x + 0.5 - ShiganshinaLayout.ringX(ring)) * ShiganshinaLayout.ringR(ring);
    }

    private static Direction inward(int x, int z) {
        int ring = Math.max(1, ShiganshinaLayout.ring((double)x + 0.5, (double)z + 0.5));
        return Direction.getNearest(ShiganshinaLayout.ringX(ring) - ((double)x + 0.5), 0.0, ShiganshinaLayout.ringZ(ring) - ((double)z + 0.5));
    }

    private void wall(ColumnBuffer col, double depth) {
        int x = col.x;
        int z = col.z;
        BlockState body = ModBlocks.WALL_STONE.get().defaultBlockState();
        BlockState bricks = ModBlocks.WALL_STONE_BRICKS.get().defaultBlockState();
        BlockState tiles = ModBlocks.WALL_STONE_TILES.get().defaultBlockState();
        boolean innerFace = depth < 1.0;
        boolean outerFace = depth > 11.0;
        boolean face = innerFace || outerFace;
        double arc = arcPosition(x, z);
        int arcBlock = (int)Math.floor(arc);

        // the foundations go all the way down to the bedrock: nothing digs under the Wall
        for (int y = col.minY + 1; y < 60; y++) {
            if (!col.get(y).is(Blocks.BEDROCK)) {
                col.set(y, body);
            }
        }

        for (int y = 60; y <= 116; y++) {
            BlockState s = body;
            int ly = y - 66;
            if (face) {
                if (ly > 2 && ly % 10 != 0) {
                    if (Math.floorMod(Math.floorDiv(arcBlock, 3) * 7 + ly / 4 * 3, 11) == 0) {
                        s = tiles;
                    }
                } else {
                    s = bricks;
                }
            }

            col.set(y, s);
        }

        if (!face) {
            col.set(116, tiles);
        }

        if (outerFace) {
            col.set(117, bricks);
            if (Math.floorMod(Math.floorDiv(arcBlock, 2), 2) == 0) {
                col.set(118, bricks);
            }
        } else if (innerFace) {
            col.set(117, ModBlocks.WALL_STONE_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }

        boolean centre = depth >= 5.5 && depth < 6.5;
        if (centre && Math.floorMod(arcBlock, 160) == 0 && Math.abs(x) > 40) {
            col.set(117, supply(inward(x, z)));
        }

        int gate = ShiganshinaLayout.nearGate(x, z);
        if (gate != 0) {
            boolean outer = gate > 0;

            for (int y = 67; y <= 88; y++) {
                if (ShiganshinaLayout.inGateOpening(x, y, z)) {
                    col.set(
                        y,
                        ShiganshinaLayout.isGateDoorPlane(depth) && ShiganshinaLayout.ring((double)x + 0.5, (double)z + 0.5) != 5
                            ? ModBlocks.WALL_GATE.get().defaultBlockState().setValue(WallGateBlock.OPEN, Boolean.valueOf(false))
                            : AIR
                    );
                }
            }

            if (ShiganshinaLayout.inGateOpening(x, 67, z)) {
                col.set(66, Blocks.STONE_BRICKS.defaultBlockState());
            }

            int shaftX = 11;
            if (x == shaftX || x == -shaftX - 1) {
                boolean districtSide = outer ? depth < 5.5 : depth >= 6.5;
                Direction toDistrict = outer ? Direction.NORTH : Direction.SOUTH;
                if (centre) {
                    col.fill(67, 116, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, toDistrict));
                } else if (districtSide) {
                    col.set(67, AIR);
                    col.set(68, AIR);
                    if (innerFace || outerFace) {
                        col.set(69, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.valueOf(true)));
                    }
                }
            }
        }
    }

    private void nature(ColumnBuffer col, int h, ShiganshinaLayout.Region region) {
        int x = col.x;
        int z = col.z;
        if (h >= 63 && !(Math.abs(x) <= 2 && z < -100 && z > -848) && Countryside.column(col, h, region)) {
            return;
        }

        if (h >= 63) {
            double near = ShiganshinaLayout.distToWall((double)x + 0.5, (double)z + 0.5);
            double forest = ShiganshinaLayout.forestFactor((double)x, (double)z);
            double r = WorldNoise.rand(x, 9, z, 61);
            if (region == ShiganshinaLayout.Region.MARIA) {
                if (Math.abs(x) <= 2 && z < -100 && z > -848) {
                    col.set(h, Blocks.DIRT_PATH.defaultBlockState());
                    return;
                }

                if (Math.abs(x) >= 6 && Math.abs(x) <= 70 && z < -125 && z > -460 && near > 10.0) {
                    int strip = Math.floorDiv(z, 9);
                    if (Math.floorMod(z, 9) == 0) {
                        col.set(h, Blocks.DIRT_PATH.defaultBlockState());
                        return;
                    }

                    if (Math.floorMod(z, 9) == 4 && Math.floorMod(x, 16) == 0) {
                        col.set(h, WATER);
                        return;
                    }

                    col.set(h, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, Integer.valueOf(7)));

                    BlockState crop = switch (Math.floorMod(strip * 7 + Integer.signum(x), 4)) {
                        case 0 -> Blocks.CARROTS.defaultBlockState();
                        case 1 -> Blocks.POTATOES.defaultBlockState();
                        default -> Blocks.WHEAT.defaultBlockState();
                    };
                    col.set(h + 1, crop.setValue(CropBlock.AGE, Integer.valueOf(7)));
                    return;
                }
            }

            if (forest > 0.2) {
                double f = WorldNoise.rand(x, 10, z, 62);
                if (f < 0.35 * forest) {
                    col.set(h, Blocks.PODZOL.defaultBlockState());
                } else if (f < 0.5 * forest) {
                    col.set(h, Blocks.COARSE_DIRT.defaultBlockState());
                } else if (f < 0.55 * forest) {
                    col.set(h, Blocks.MOSS_BLOCK.defaultBlockState());
                }

                if (r < 0.2) {
                    col.set(h + 1, Blocks.FERN.defaultBlockState());
                } else if (r < 0.24) {
                    tallPlant(col, h + 1, Blocks.LARGE_FERN.defaultBlockState());
                } else if (r < 0.34) {
                    col.set(h + 1, Blocks.SHORT_GRASS.defaultBlockState());
                }
            } else {
                if (near > 14.0) {
                    smallTrees(col, region);
                }

                if (col.isAir(h + 1)) {
                    if (r < 0.14) {
                        col.set(h + 1, Blocks.SHORT_GRASS.defaultBlockState());
                    } else if (r < 0.17) {
                        tallPlant(col, h + 1, Blocks.TALL_GRASS.defaultBlockState());
                    } else if (r < 0.185) {
                        BlockState[] flowers = new BlockState[]{
                            Blocks.DANDELION.defaultBlockState(),
                            Blocks.POPPY.defaultBlockState(),
                            Blocks.CORNFLOWER.defaultBlockState(),
                            Blocks.OXEYE_DAISY.defaultBlockState(),
                            Blocks.AZURE_BLUET.defaultBlockState()
                        };
                        col.set(h + 1, flowers[(int)(WorldNoise.rand(x, 11, z, 63) * (double)flowers.length)]);
                    }
                }
            }
        }
    }

    private static void tallPlant(ColumnBuffer col, int y, BlockState plant) {
        col.set(y, plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
        col.set(y + 1, plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void smallTrees(ColumnBuffer col, ShiganshinaLayout.Region region) {
        int x = col.x;
        int z = col.z;
        int ci = Math.floorDiv(x, 9);
        int cj = Math.floorDiv(z, 9);

        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                double cx = (double)(i * 9 + 2) + WorldNoise.rand(i, 1, j, 71) * 5.0;
                double cz = (double)(j * 9 + 2) + WorldNoise.rand(i, 2, j, 71) * 5.0;
                double woods = WorldNoise.fbm2(cx * 0.012, cz * 0.012, 72, 2);
                double need = region == ShiganshinaLayout.Region.MARIA ? 0.66 : 0.55;
                if (!(woods < need) && !(WorldNoise.rand(i, 3, j, 71) > (woods - need) * 4.0)) {
                    int tx = (int)Math.floor(cx);
                    int tz = (int)Math.floor(cz);
                    if (!(ShiganshinaLayout.distToWall((double)tx + 0.5, (double)tz + 0.5) < 14.0)
                        && ShiganshinaLayout.region((double)tx + 0.5, (double)tz + 0.5) == region) {
                        int base = ShiganshinaLayout.terrainHeight(tx, tz);
                        if (base >= 63) {
                            boolean birch = WorldNoise.rand(i, 4, j, 71) < 0.3;
                            int trunk = 4 + (int)(WorldNoise.rand(i, 5, j, 71) * 3.0);
                            BlockState log = birch ? Blocks.BIRCH_LOG.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
                            BlockState leaves = (birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES)
                                .defaultBlockState()
                                .setValue(LeavesBlock.PERSISTENT, Boolean.valueOf(true));
                            double dx = (double)x + 0.5 - ((double)tx + 0.5);
                            double dz = (double)z + 0.5 - ((double)tz + 0.5);
                            double d = Math.sqrt(dx * dx + dz * dz);
                            double lr = 2.6;
                            if (d < lr) {
                                double cy = (double)(base + trunk) + 0.5;
                                double half = Math.sqrt(lr * lr - d * d) * 0.85;

                                for (int y = (int)Math.floor(cy - half); y <= (int)Math.floor(cy + half + 0.5); y++) {
                                    if ((!(WorldNoise.rand(x, y, z, 73) < 0.08) || !(d > 1.5)) && col.isAir(y)) {
                                        col.set(y, leaves);
                                    }
                                }
                            }

                            if (x == tx && z == tz) {
                                col.fill(base + 1, base + trunk, log);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void applyCarvers(
        WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, Carving step
    ) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
    }

    @Override
    public void createStructures(
        RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates
    ) {
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structures, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos cp = region.getCenter();
        ShiganshinaLayout.Region r = ShiganshinaLayout.region((double)cp.getMiddleBlockX(), (double)cp.getMiddleBlockZ());
        if (r != ShiganshinaLayout.Region.DISTRICT && r != ShiganshinaLayout.Region.WALL) {
            Holder<Biome> biome = region.getBiome(cp.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            random.setDecorationSeed(region.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(region, biome, cp, random);
        }
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return 67;
    }

    @Override
    public int getBaseHeight(int x, int z, Types type, LevelHeightAccessor level, RandomState random) {
        ColumnBuffer col = new ColumnBuffer(level.getMinBuildHeight(), level.getHeight());
        col.reset(x, z);
        this.buildColumn(col, GiantTrees.nearForest(x, z));

        for (int i = col.states.length - 1; i >= 0; i--) {
            if (type.isOpaque().test(col.states[i])) {
                return col.minY + i + 1;
            }
        }

        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        ColumnBuffer col = new ColumnBuffer(level.getMinBuildHeight(), level.getHeight());
        col.reset(x, z);
        this.buildColumn(col, GiantTrees.nearForest(x, z));
        return new NoiseColumn(col.minY, (BlockState[])col.states.clone());
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Shiganshina: " + ShiganshinaLayout.region((double)pos.getX() + 0.5, (double)pos.getZ() + 0.5));
    }

    private static final class Fixed {
        static final Map<Long, List<ShiganshinaChunkGenerator.Placement>> PLACEMENTS = new HashMap<>();

        static {
            ShiganshinaChunkGenerator.placeOnWall(2, true, ShiganshinaChunkGenerator.GateWinch.NORTH);
            ShiganshinaChunkGenerator.placeOnWall(-3, true, ShiganshinaChunkGenerator.GateWinch.NORTH);
            ShiganshinaChunkGenerator.place(9, 67, 92, ShiganshinaChunkGenerator.winch(Direction.NORTH));
            ShiganshinaChunkGenerator.place(-10, 67, 92, ShiganshinaChunkGenerator.winch(Direction.NORTH));
            ShiganshinaChunkGenerator.placeSupplyOnWall(15, true, Direction.NORTH);
            ShiganshinaChunkGenerator.placeSupplyOnWall(-16, true, Direction.NORTH);
            ShiganshinaChunkGenerator.place(12, 67, 84, ShiganshinaChunkGenerator.supply(Direction.NORTH));
            ShiganshinaChunkGenerator.place(-13, 67, 84, ShiganshinaChunkGenerator.supply(Direction.NORTH));
            ShiganshinaChunkGenerator.placeOnWall(2, false, ShiganshinaChunkGenerator.GateWinch.SOUTH);
            ShiganshinaChunkGenerator.placeOnWall(-3, false, ShiganshinaChunkGenerator.GateWinch.SOUTH);
            ShiganshinaChunkGenerator.place(9, 67, -93, ShiganshinaChunkGenerator.winch(Direction.SOUTH));
            ShiganshinaChunkGenerator.place(-10, 67, -93, ShiganshinaChunkGenerator.winch(Direction.SOUTH));
            ShiganshinaChunkGenerator.place(9, 67, -108, ShiganshinaChunkGenerator.winch(Direction.NORTH));
            ShiganshinaChunkGenerator.placeSupplyOnWall(15, false, Direction.SOUTH);
            ShiganshinaChunkGenerator.placeSupplyOnWall(-16, false, Direction.SOUTH);
            ShiganshinaChunkGenerator.place(10, 67, -109, ShiganshinaChunkGenerator.supply(Direction.NORTH));
            int t = -400;
            ShiganshinaChunkGenerator.placeOnRing(2, -500.0, 200.0, Direction.NORTH, true);
            ShiganshinaChunkGenerator.placeOnRing(-3, -500.0, 200.0, Direction.NORTH, true);
            ShiganshinaChunkGenerator.place(9, 67, 92 + t, ShiganshinaChunkGenerator.winch(Direction.NORTH));
            ShiganshinaChunkGenerator.place(-10, 67, 92 + t, ShiganshinaChunkGenerator.winch(Direction.NORTH));
            ShiganshinaChunkGenerator.placeOnRing(15, -500.0, 200.0, Direction.NORTH, false);
            ShiganshinaChunkGenerator.placeOnRing(-16, -500.0, 200.0, Direction.NORTH, false);
            ShiganshinaChunkGenerator.place(12, 67, 84 + t, ShiganshinaChunkGenerator.supply(Direction.NORTH));
            ShiganshinaChunkGenerator.place(-13, 67, 84 + t, ShiganshinaChunkGenerator.supply(Direction.NORTH));
            ShiganshinaChunkGenerator.placeOnRing(2, -1100.0, 600.0, Direction.SOUTH, true);
            ShiganshinaChunkGenerator.placeOnRing(-3, -1100.0, 600.0, Direction.SOUTH, true);
            ShiganshinaChunkGenerator.place(9, 67, -93 + t, ShiganshinaChunkGenerator.winch(Direction.SOUTH));
            ShiganshinaChunkGenerator.place(-10, 67, -93 + t, ShiganshinaChunkGenerator.winch(Direction.SOUTH));
            ShiganshinaChunkGenerator.place(9, 67, -108 + t, ShiganshinaChunkGenerator.winch(Direction.NORTH));
            ShiganshinaChunkGenerator.placeOnRing(15, -1100.0, 600.0, Direction.SOUTH, false);
            ShiganshinaChunkGenerator.placeOnRing(-16, -1100.0, 600.0, Direction.SOUTH, false);
            ShiganshinaChunkGenerator.place(10, 67, -2 + t, ShiganshinaChunkGenerator.supply(Direction.WEST));
            ShiganshinaChunkGenerator.place(-11, 67, -2 + t, ShiganshinaChunkGenerator.supply(Direction.EAST));
            ShiganshinaChunkGenerator.place(12, 67, -48 + t, ShiganshinaChunkGenerator.supply(Direction.SOUTH));
            ShiganshinaChunkGenerator.place(10, 67, -2, ShiganshinaChunkGenerator.supply(Direction.WEST));
            ShiganshinaChunkGenerator.place(-11, 67, -2, ShiganshinaChunkGenerator.supply(Direction.EAST));
            ShiganshinaChunkGenerator.place(12, 67, -48, ShiganshinaChunkGenerator.supply(Direction.SOUTH));
            ShiganshinaChunkGenerator.place(22, 67, -48, ShiganshinaChunkGenerator.supply(Direction.SOUTH));
        }
    }

    private static enum GateWinch {
        NORTH,
        SOUTH;
    }

    static record Placement(int y, BlockState state) {
    }
}
