package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Rumbling {
    private static final int SPAWN_AT = 50;
    private static final double DISTANCE = 120.0;
    private static final double SPACING = 30.0;
    private static final List<Rumbling.Running> RUNNING = new ArrayList<>();

    private Rumbling() {
    }

    public static boolean start(ServerLevel level, Entity who) {
        if (RUNNING.size() >= 2) {
            return false;
        } else {
            Vec3 look = who.getLookAngle();
            Vec3 forward = new Vec3(look.x, 0.0, look.z);
            if (forward.lengthSqr() < 1.0E-4) {
                forward = new Vec3(0.0, 0.0, 1.0);
            }

            forward = forward.normalize();
            Rumbling.Running r = new Rumbling.Running(level, who.position(), forward, TakemConfig.RUMBLING_TITANS.get(), level.random);
            RUNNING.add(r);
            Vec3 at = who.position();
            level.playSound(null, at.x, at.y, at.z, ModSounds.RUMBLING_HORN.get(), SoundSource.PLAYERS, 40.0F, 1.0F);
            Fx.send(level, 17, at.add(0.0, (double)who.getBbHeight() * 0.8, 0.0), 1.0F, 0.0F, 600.0);

            for (int i = 0; i < r.spawnAt.length; i++) {
                ChunkPos c = new ChunkPos(BlockPos.containing(r.slot(i)));
                level.getChunkSource().addRegionTicket(Chunks.TICKET, c, 3, c);
            }

            Component title = Component.translatable("message.takemtitans.rumbling_title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            Component sub = Component.translatable("message.takemtitans.rumbling_subtitle").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(at) < 360000.0) {
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
                    p.connection.send(new ClientboundSetTitleTextPacket(title));
                }
            }

            return true;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && !RUNNING.isEmpty()) {
            for (int i = 0; i < RUNNING.size(); i++) {
                Rumbling.Running r = RUNNING.get(i);
                if (r.level == level && !step(r)) {
                    RUNNING.remove(i--);
                }
            }

            return;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNNING.clear();
    }

    private static boolean step(Rumbling.Running r) {
        ServerLevel level = r.level;
        RandomSource rnd = level.random;
        int t = r.t++;
        boolean waiting = false;

        for (int i = 0; i < r.spawnAt.length; i++) {
            if (!r.spawned[i]) {
                waiting = true;
                if (t == r.spawnAt[i]) {
                    r.spawned[i] = true;
                    spawn(r, i);
                }
            }
        }

        r.titans.removeIf(Entity::isRemoved);
        if (t % 10 == 0) {
            float build = Mth.clamp((float)t / 110.0F, 0.15F, 1.0F);

            for (ServerPlayer p : level.players()) {
                double d = nearest(r, p.position());
                float s = build * (float)Mth.clamp(1.15 - d / 320.0, 0.12, 1.0) * 0.45F;
                AbstractTitan.shakeNearby(level, p.position(), 1.0, s, 14);
            }
        }

        if (t == 20 || t > 20 && t % 120 == 0 && !r.titans.isEmpty()) {
            for (ServerPlayer p : level.players()) {
                double d = nearest(r, p.position());
                if (d < 700.0) {
                    float vol = (float)Mth.clamp(1.4 - d / 500.0, 0.25, 1.2);
                    level.playSound(
                        null, p.getX(), p.getY(), p.getZ(), ModSounds.RUMBLING_QUAKE.get(), SoundSource.AMBIENT, vol, 0.9F + rnd.nextFloat() * 0.15F
                    );
                }
            }
        }

        return waiting || !r.titans.isEmpty() || t < 170;
    }

    private static double nearest(Rumbling.Running r, Vec3 p) {
        if (r.titans.isEmpty()) {
            return r.origin.add(r.forward.scale(120.0)).distanceTo(p);
        } else {
            double best = 1.7976931348623157E308;

            for (RumblingTitan titan : r.titans) {
                best = Math.min(best, Math.sqrt(titan.distanceToSqr(p.x, titan.getY(), p.z)));
            }

            return best;
        }
    }

    private static void spawn(Rumbling.Running r, int i) {
        ServerLevel level = r.level;
        Vec3 at = r.slot(i);
        RumblingTitan titan = ModEntities.RUMBLING_TITAN.get().create(level);
        if (titan != null) {
            int ground = RumblingTitan.groundY(level, Mth.floor(at.x), Mth.floor(at.z), Mth.floor(r.origin.y));
            titan.moveTo(at.x, (double)(ground - 2), at.z, 0.0F, 0.0F);
            titan.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
            int march = (int)((120.0 + TakemConfig.RUMBLING_DISTANCE.get()) / 0.42);
            titan.setup(r.forward.scale(-1.0), march, TakemConfig.RUMBLING_BREAKS_BLOCKS.get());
            level.addFreshEntity(titan);
            r.titans.add(titan);
        }
    }

    private static final class Running {
        final ServerLevel level;
        final Vec3 origin;
        final Vec3 forward;
        final int[] spawnAt;
        final boolean[] spawned;
        final List<RumblingTitan> titans = new ArrayList<>();
        int t;

        Running(ServerLevel level, Vec3 origin, Vec3 forward, int count, RandomSource rnd) {
            this.level = level;
            this.origin = origin;
            this.forward = forward;
            this.spawnAt = new int[count];
            this.spawned = new boolean[count];

            for (int i = 0; i < count; i++) {
                this.spawnAt[i] = 50 + rnd.nextInt(50);
            }
        }

        Vec3 slot(int i) {
            int n = this.spawnAt.length;
            Vec3 side = new Vec3(-this.forward.z, 0.0, this.forward.x);
            double off = ((double)i - (double)(n - 1) * 0.5) * 30.0;
            double ahead = 120.0 + (double)(i * 7919 % 17 - 8);
            return this.origin.add(this.forward.scale(ahead)).add(side.scale(off));
        }
    }
}
