package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.registry.ModEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class TitanSpawner {
    private static final int INTERVAL = 40;
    private static final double MIN_DIST = 48.0;
    private static final double MAX_DIST = 100.0;
    private static final double COUNT_RADIUS = 128.0;

    private TitanSpawner() {
    }

    public static boolean isShiganshina(Level level) {
        if (level instanceof ServerLevel sl && sl.getChunkSource().getGenerator() instanceof ShiganshinaChunkGenerator) {
            return true;
        }

        return false;
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level
            && level.getGameTime() % 40L == 0L
            && TakemConfig.TITAN_SPAWNING.get()
            && level.getDifficulty() != Difficulty.PEACEFUL
            && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            boolean shiganshina = isShiganshina(level);
            if ((shiganshina || TakemConfig.TITAN_SPAWNING_NORMAL_WORLDS.get() && level.dimension() == Level.OVERWORLD) && level.isDay()) {
                int cap = shiganshina ? TakemConfig.TITAN_CAP_PER_PLAYER.get() : TakemConfig.TITAN_CAP_NORMAL_WORLDS.get();
                if (cap > 0) {
                    boolean breached = shiganshina && BreachData.get(level).breached;

                    for (ServerPlayer player : level.players()) {
                        if (!player.isSpectator()) {
                            int near = level.getEntitiesOfClass(AbstractTitan.class, player.getBoundingBox().inflate(128.0), t -> !t.isDummy()).size();
                            int tierCap = com.takem.titans.story.Story.threatTier(player) == 0 ? Math.max(2, cap / 2) : cap;
                            if (near < tierCap) {
                                trySpawn(level, player, shiganshina, breached);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void trySpawn(ServerLevel level, ServerPlayer player, boolean shiganshina, boolean breached) {
        RandomSource r = level.getRandom();

        for (int attempt = 0; attempt < 4; attempt++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double d = Mth.lerp(r.nextDouble(), 48.0, 100.0);
            int x = Mth.floor(player.getX() + Math.cos(a) * d);
            int z = Mth.floor(player.getZ() + Math.sin(a) * d);
            if (level.hasChunkAt(new BlockPos(x, 0, z))) {
                if (shiganshina) {
                    ShiganshinaLayout.Region region = ShiganshinaLayout.region((double)x + 0.5, (double)z + 0.5);
                    boolean ok = region == ShiganshinaLayout.Region.OUTSIDE || breached && region == ShiganshinaLayout.Region.DISTRICT && z > -200;
                    if (!ok) {
                        continue;
                    }
                }

                int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                BlockState below = level.getBlockState(pos.below());
                if (below.isFaceSturdy(level, pos.below(), Direction.UP)
                    && level.getFluidState(pos).isEmpty()
                    && !level.getFluidState(pos.below()).is(FluidTags.WATER)
                    && !below.is(BlockTags.LEAVES)
                    && !below.is(BlockTags.LOGS)
                    && (shiganshina || natural(below))
                    && level.canSeeSky(pos)
                    && level.getNearestPlayer((double)x + 0.5, (double)y, (double)z + 0.5, 38.400000000000006, false) == null) {
                    int tier = com.takem.titans.story.Story.threatTier(player);
                    EntityType<? extends AbstractTitan> type = pickType(r, tier);
                    AbstractTitan titan = type.create(level);
                    if (titan == null) {
                        return;
                    }

                    titan.moveTo((double)x + 0.5, (double)y, (double)z + 0.5, r.nextFloat() * 360.0F, 0.0F);
                    titan.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
                    titan.applyThreat(tier);
                    titan.yBodyRot = titan.getYRot();
                    titan.yHeadRot = titan.getYRot();
                    AABB box = titan.getBoundingBox();
                    if (level.noCollision(titan, box.deflate(0.3).move(0.0, 0.4, 0.0))) {
                        level.addFreshEntity(titan);
                        return;
                    }

                    titan.discard();
                }
            }
        }
    }

    private static boolean natural(BlockState below) {
        return below.is(BlockTags.DIRT)
            || below.is(BlockTags.SAND)
            || below.is(BlockTags.BASE_STONE_OVERWORLD)
            || below.is(Blocks.SNOW_BLOCK)
            || below.is(BlockTags.TERRACOTTA);
    }

    /** Abnormals and crawlers only start turning up once a player has learned to fly; late in the story they're common. */
    static EntityType<? extends AbstractTitan> pickType(RandomSource r, int tier) {
        int roll = r.nextInt(100);
        int abnormal = switch (tier) {
            case 0 -> 0;
            case 1 -> 10;
            case 2 -> 18;
            case 3 -> 24;
            default -> 30;
        };
        int crawler = tier == 0 ? 0 : (tier == 1 ? 5 : 12);
        if (tier >= 2 && roll >= 96) {
            return ModEntities.GHOUL_TITAN.get();
        }

        if (roll < abnormal) {
            return ModEntities.ABNORMAL_TITAN.get();
        } else {
            return roll < abnormal + crawler ? ModEntities.CRAWLER_TITAN.get() : ModEntities.PURE_TITAN.get();
        }
    }

    @Nullable
    public static AbstractTitan spawnAt(ServerLevel level, EntityType<? extends AbstractTitan> type, double x, double y, double z, float yaw) {
        AbstractTitan titan = type.create(level);
        if (titan == null) {
            return null;
        } else {
            titan.moveTo(x, y, z, yaw, 0.0F);
            titan.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.EVENT, null);
            titan.yBodyRot = yaw;
            titan.yHeadRot = yaw;
            level.addFreshEntity(titan);
            return titan;
        }
    }
}
