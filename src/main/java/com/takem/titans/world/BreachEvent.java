package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.TakemTitans;
import com.takem.titans.block.GateControlBlock;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.ColossalTitan;
import com.takem.titans.registry.ModBlocks;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class BreachEvent {
    private static final int APPEAR = 0;
    private static final int KICK = 120;
    private static final int IMPACT = 145;
    private static final int HORDE_START = 175;
    private static final int HORDE_EVERY = 22;
    private static final int HORDE_SIZE = 10;
    private static final int VANISH = 275;
    private static final int END = 405;
    private static final Vec3 COLOSSAL_POS = new Vec3(-0.5, 67.0, 115.0);
    private static final BlockPos GATE_CENTER = new BlockPos(0, 77, 100);
    @Nullable
    private static BreachEvent.Running running;

    private BreachEvent() {
    }

    public static boolean isRunning() {
        return running != null;
    }

    public static boolean start(ServerLevel level) {
        if (running == null && TitanSpawner.isShiganshina(level)) {
            running = new BreachEvent.Running(level);
            return true;
        } else {
            return false;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (running == null) {
                if (level.getGameTime() % 100L == 0L) {
                    checkSchedule(level);
                }
            } else if (running.level == level) {
                try {
                    step(running);
                } catch (RuntimeException var3) {
                    TakemTitans.LOGGER.error("Breach event failed", var3);
                    running = null;
                }
            }
        }
    }

    private static void checkSchedule(ServerLevel level) {
        int day = TakemConfig.BREACH_DAY.get();
        if (day > 0 && TitanSpawner.isShiganshina(level) && !BreachData.get(level).breached) {
            long time = level.getDayTime();
            long dayIndex = time / 24000L + 1L;
            long timeOfDay = time % 24000L;
            if (dayIndex >= (long)day && timeOfDay >= 2000L && timeOfDay <= 9000L) {
                Vec3 gate = Vec3.atCenterOf(GATE_CENTER);

                for (ServerPlayer p : level.players()) {
                    if (!p.isSpectator() && p.position().distanceTo(gate) < 420.0) {
                        start(level);
                        return;
                    }
                }
            }
        }
    }

    private static void step(BreachEvent.Running r) {
        ServerLevel level = r.level;
        int t = r.tick++;
        RandomSource rnd = level.getRandom();
        Vec3 gate = Vec3.atCenterOf(GATE_CENTER);
        if (t == 0) {
            for (int i = 0; i < 4; i++) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(COLOSSAL_POS.add(rnd.nextGaussian() * 6.0, 0.0, rnd.nextGaussian() * 6.0));
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }

            level.playSound(null, COLOSSAL_POS.x, COLOSSAL_POS.y + 30.0, COLOSSAL_POS.z, ModSounds.COLOSSAL_ARRIVAL.get(), SoundSource.HOSTILE, 12.0F, 1.0F);
            AbstractTitan.shakeNearby(level, gate, 400.0, 1.4F, 60);
            ColossalTitan colossal = ModEntities.COLOSSAL_TITAN.get().create(level);
            if (colossal != null) {
                colossal.moveTo(COLOSSAL_POS.x, COLOSSAL_POS.y, COLOSSAL_POS.z, 180.0F, 0.0F);
                colossal.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(COLOSSAL_POS)), MobSpawnType.EVENT, null);
                colossal.yBodyRot = 180.0F;
                colossal.yHeadRot = 180.0F;
                colossal.hold(160);
                level.addFreshEntity(colossal);
                r.colossal = colossal;
            }

            far(level, ModParticles.BIG_STEAM.get(), COLOSSAL_POS.x, COLOSSAL_POS.y + 48.0, COLOSSAL_POS.z, 260, 9.0, 14.0, 9.0, 0.2);
            title(
                level,
                Component.translatable("message.takemtitans.breach_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.translatable("message.takemtitans.breach_subtitle")
            );
        }

        if (t > 0 && t < 275 && t % 6 == 0 && r.colossal != null && r.colossal.isAlive()) {
            Vec3 c = r.colossal.position();
            far(level, ModParticles.BIG_STEAM.get(), c.x, c.y + 58.0, c.z - 2.0, 14, 5.0, 4.0, 4.0, 0.08);
        }

        if (t == 60) {
            level.playSound(null, gate.x, gate.y, gate.z, ModSounds.BELL.get(), SoundSource.BLOCKS, 8.0F, 1.0F);
        }

        if (t == 120 && r.colossal != null && r.colossal.isAlive()) {
            r.colossal.startKick();
        }

        if (t == 145) {
            impact(level, rnd);
        }

        if (t >= 175 && r.spawned < 10 && (t - 175) % 22 == 0) {
            EntityType<? extends AbstractTitan> type = r.spawned == 3
                ? ModEntities.ABNORMAL_TITAN.get()
                : (r.spawned == 7 ? ModEntities.CRAWLER_TITAN.get() : ModEntities.PURE_TITAN.get());
            double x = -0.5 + (rnd.nextDouble() - 0.5) * 36.0;
            double z = 130.0 + rnd.nextDouble() * 22.0;
            AbstractTitan titan = TitanSpawner.spawnAt(level, type, x, 67.0, z, 180.0F);
            if (titan != null) {
                titan.marchTo(new Vec3((rnd.nextDouble() - 0.5) * 30.0, 67.0, 20.0 - rnd.nextDouble() * 60.0));
            }

            r.spawned++;
        }

        if (t == 275 && r.colossal != null && r.colossal.isAlive()) {
            r.colossal.setVanishIn(1);
        }

        if (t >= 405) {
            running = null;
        }
    }

    private static void impact(ServerLevel level, RandomSource rnd) {
        BreachData.get(level).setBreached(true);
        GateControlBlock.smashGate(level, GATE_CENTER, 10, 14, 14);
        int debris = 0;
        int hw = 10;
        int top = 28;

        for (int x = -hw; x <= hw; x++) {
            for (int y = 67; y <= 66 + top; y++) {
                for (int z = 92; z <= 108; z++) {
                    double ex = ((double)x + 0.5) / (double)hw;
                    double ey = (double)(y - 66) / (double)top;
                    if (!(ex * ex + ey * ey > 1.0)) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState s = level.getBlockState(pos);
                        if (!s.isAir() && isWallBlock(s)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 50);
                            if (rnd.nextFloat() < 0.6F) {
                                double depth = (double)(z - 92) / 16.0;
                                Destruction.throwBlock(
                                    level,
                                    pos,
                                    s,
                                    new Vec3(
                                        (rnd.nextDouble() - 0.5) * 1.1 * (0.5 + Math.abs(ex)),
                                        0.25 + rnd.nextDouble() * 0.8,
                                        -(1.2 + rnd.nextDouble() * 1.8) * (0.6 + depth * 0.6)
                                    ),
                                    0.3F,
                                    3
                                );
                                debris++;
                            }
                        }
                    }
                }
            }
        }

        Vec3 g = Vec3.atCenterOf(GATE_CENTER);
        level.playSound(null, g.x, g.y, g.z, ModSounds.WALL_BREACH.get(), SoundSource.HOSTILE, 14.0F, 1.0F);
        far(level, ParticleTypes.EXPLOSION_EMITTER, g.x, g.y, g.z, 4, 6.0, 6.0, 4.0, 0.0);
        far(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, g.x, g.y, g.z - 4.0, 120, 8.0, 8.0, 6.0, 0.05);
        far(level, ModParticles.BIG_STEAM.get(), g.x, g.y + 10.0, g.z + 10.0, 120, 10.0, 12.0, 6.0, 0.15);
        AbstractTitan.shakeNearby(level, g, 450.0, 2.0F, 50);

        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(g) < 600.0) {
                p.displayClientMessage(Component.translatable("message.takemtitans.breach_gate").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private static boolean isWallBlock(BlockState s) {
        return s.is(ModBlocks.WALL_STONE.get())
            || s.is(ModBlocks.WALL_STONE_BRICKS.get())
            || s.is(ModBlocks.WALL_STONE_TILES.get())
            || s.is(ModBlocks.CHISELED_WALL_STONE.get())
            || s.is(ModBlocks.WALL_GATE.get())
            || s.is(Blocks.LADDER)
            || s.is(Blocks.STONE_BRICKS);
    }

    private static <T extends ParticleOptions> void far(
        ServerLevel level, T type, double x, double y, double z, int count, double dx, double dy, double dz, double speed
    ) {
        for (ServerPlayer p : level.players()) {
            level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    private static void title(ServerLevel level, Component title, Component subtitle) {
        for (ServerPlayer p : level.players()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static final class Running {
        final ServerLevel level;
        int tick;
        int spawned;
        @Nullable
        ColossalTitan colossal;

        Running(ServerLevel level) {
            this.level = level;
        }
    }
}
