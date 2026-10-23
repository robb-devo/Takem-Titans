package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class KillCam {
    private static final float SLOW_RATE = 5.0F;
    private static final long HOLD_MS = 520L;
    private static final long RAMP_MS = 380L;
    private static final long COOLDOWN_MS = 2500L;
    private static long startedAt = -1L;
    private static long lastKill = -2500L;
    private static float normalRate = 20.0F;
    private static float slowRate = 5.0F;
    private static long holdMs = 520L;
    private static long rampMs = 380L;

    private KillCam() {
    }

    public static void slowMo(MinecraftServer server, float rate, long hold, long ramp) {
        if (server != null && TakemConfig.KILL_SLOW_MOTION.get() && server.getPlayerCount() <= 1) {
            if (startedAt < 0L) {
                normalRate = server.tickRateManager().tickrate();
                if (normalRate <= rate) {
                    return;
                }
            }

            startedAt = System.currentTimeMillis();
            slowRate = rate;
            holdMs = hold;
            rampMs = ramp;
            server.tickRateManager().setTickRate(rate);
        }
    }

    public static void trigger(ServerPlayer killer) {
        MinecraftServer server = killer.getServer();
        if (server != null && TakemConfig.KILL_SLOW_MOTION.get() && server.getPlayerCount() <= 1) {
            long now = System.currentTimeMillis();
            if (now - lastKill >= 2500L && startedAt < 0L) {
                lastKill = now;
                normalRate = server.tickRateManager().tickrate();
                if (!(normalRate <= 5.0F)) {
                    startedAt = now;
                    slowRate = 5.0F;
                    holdMs = 520L;
                    rampMs = 380L;
                    server.tickRateManager().setTickRate(5.0F);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(Post event) {
        if (startedAt >= 0L) {
            long t = System.currentTimeMillis() - startedAt;
            MinecraftServer server = event.getServer();
            if (t >= holdMs) {
                if (t >= holdMs + rampMs) {
                    server.tickRateManager().setTickRate(normalRate);
                    startedAt = -1L;
                } else {
                    float f = (float)(t - holdMs) / (float)rampMs;
                    server.tickRateManager().setTickRate(slowRate + (normalRate - slowRate) * f * f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        startedAt = -1L;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (startedAt >= 0L) {
            event.getServer().tickRateManager().setTickRate(normalRate);
            startedAt = -1L;
        }
    }
}
