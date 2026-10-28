package com.takem.titans.world;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.network.StoryPayload;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.story.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Night missions. On some nights, soon after dark, word comes that titans are moving in the fog somewhere nearby - awake,
 * when they should be asleep. A thick fog rolls in over that stretch of land; thin them out before dawn and you are paid
 * well. Titans in a night mission do not sleep (see {@link #AWAKE}).
 */
@EventBusSubscriber(modid = "takemtitans")
public final class NightMission {
    /** Titans with this tag do not go sluggish at night (night missions, the Valgard waves). */
    public static final String AWAKE = "takemtitans_awake";
    private static final String NIGHT = "takemtitans_night";
    private static final double FOG_R = 110.0;
    @Nullable
    private static Mission active;
    private static long lastDay = -1L;

    private NightMission() {
    }

    private static final class Mission {
        final ServerLevel level;
        final UUID player;
        final Vec3 at;
        final int need;
        final List<UUID> titans = new ArrayList<>();
        final ServerBossEvent bar = new ServerBossEvent(Component.literal(""), BossBarColor.BLUE, BossBarOverlay.NOTCHED_10);
        int killed;

        Mission(ServerLevel level, ServerPlayer p, Vec3 at, int need) {
            this.level = level;
            this.player = p.getUUID();
            this.at = at;
            this.need = need;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }

        long time = level.getDayTime() % 24000L;
        if (time > 13000L && time < 23000L && level.getGameTime() % 1200L == 600L && TitanSpawner.isShiganshina(level)) {
            for (ServerPlayer p : level.players()) {
                stalker(level, p);
            }
        }

        Mission m = active;
        if (m == null) {
            long day = level.getDayTime() / 24000L;
            if (time == 13400L && day != lastDay && TitanSpawner.isShiganshina(level) && !Waves.running()) {
                lastDay = day;
                for (ServerPlayer p : level.players()) {
                    if (OdmServer.data(p).stage > 6 && (level.random.nextFloat() < 0.5F || day % 3L == 1L)) {
                        start(level, p);
                        return;
                    }
                }
            }

            return;
        }

        if (m.level != level) {
            return;
        }

        ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.player);
        if (p == null) {
            end(m, null, false);
            return;
        }

        if (time >= 23000L || time < 12000L) {
            end(m, p, false);
            return;
        }

        if (level.getGameTime() % 20L == 11L) {
            int alive = 0;
            for (int i = 0; i < m.titans.size(); i++) {
                if (level.getEntity(m.titans.get(i)) instanceof AbstractTitan t && t.isAlive()) {
                    alive++;
                    if (t.getTarget() == null && p.position().distanceTo(m.at) < FOG_R) {
                        t.setTarget(p);
                    }
                } else {
                    m.titans.remove(i--);
                }
            }

            // the fog keeps giving them up until the job is done
            if (alive < 3 && m.killed + alive < m.need + 2) {
                for (int i = 0; i < 2; i++) {
                    spawn(m);
                }
            }

            m.bar.setName(Component.translatable("night.takemtitans.bar", m.killed, m.need));
            m.bar.setProgress(Mth.clamp((float)m.killed / m.need, 0.0F, 1.0F));
            if (m.killed >= m.need) {
                end(m, p, true);
            }
        }
    }

    /** A ghoul sent after a player out in the open at night, from where they are not looking. */
    private static void stalker(ServerLevel level, ServerPlayer p) {
        ShiganshinaLayout.Region region = ShiganshinaLayout.region(p.getX(), p.getZ());
        if (OdmServer.data(p).stage <= 8 || level.random.nextFloat() > 0.3F
            || region != ShiganshinaLayout.Region.OUTSIDE && region != ShiganshinaLayout.Region.MARIA
            || !level.getEntitiesOfClass(com.takem.titans.entity.titan.GhoulTitan.class, p.getBoundingBox().inflate(140.0)).isEmpty()) {
            return;
        }

        Vec3 back = p.getLookAngle().multiply(-1.0, 0.0, -1.0);
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : back.normalize();
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 at = p.position().add(back.yRot((level.random.nextFloat() - 0.5F) * 1.2F).scale(60.0 + level.random.nextDouble() * 25.0));
            if (level.isLoaded(BlockPos.containing(at)) && ShiganshinaLayout.region(at.x, at.z) != ShiganshinaLayout.Region.WALL) {
                int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
                AbstractTitan t = TitanSpawner.spawnAt(level, ModEntities.GHOUL_TITAN.get(), Math.floor(at.x) + 0.5, y, Math.floor(at.z) + 0.5, 0.0F);
                if (t != null) {
                    t.addTag(AWAKE);
                    t.setTarget(p);
                    return;
                }
            }
        }
    }

    private static void start(ServerLevel level, ServerPlayer p) {
        RandomSource rnd = level.random;
        Vec3 at = null;
        for (int attempt = 0; attempt < 20 && at == null; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = 110.0 + rnd.nextDouble() * 110.0;
            double x = p.getX() + Math.cos(a) * d;
            double z = p.getZ() + Math.sin(a) * d;
            ShiganshinaLayout.Region r = ShiganshinaLayout.region(x, z);
            if (r != ShiganshinaLayout.Region.WALL && !ShiganshinaLayout.inArena(x, z) && level.isLoaded(BlockPos.containing(x, 66.0, z))) {
                at = new Vec3(x, level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)), z);
            }
        }

        if (at == null) {
            return;
        }

        Mission m = new Mission(level, p, at, 5 + rnd.nextInt(4));
        active = m;
        for (int i = 0; i < 6 + rnd.nextInt(3); i++) {
            spawn(m);
        }

        m.bar.addPlayer(p);
        m.bar.setName(Component.translatable("night.takemtitans.bar", 0, m.need));
        String zone = Rebuild.zoneAt(at.x, at.z);
        Component where = zone != null && !zone.contains("wall") ? Component.translatable("zone.takemtitans." + zone) : Component.translatable("night.takemtitans.fields");
        Story.bannerFor(p, "chapter", Component.translatable("night.takemtitans.title"), Component.translatable("night.takemtitans.sub", where, m.need));
        p.playNotifySound(ModSounds.RUMBLING_HORN.get(), SoundSource.HOSTILE, 2.0F, 0.6F);
        fog(p, m.at, true);
    }

    private static void spawn(Mission m) {
        RandomSource rnd = m.level.random;
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = 8.0 + rnd.nextDouble() * 40.0;
            double x = m.at.x + Math.cos(a) * d;
            double z = m.at.z + Math.sin(a) * d;
            if (ShiganshinaLayout.region(x, z) == ShiganshinaLayout.Region.WALL || !m.level.isLoaded(BlockPos.containing(x, 66.0, z))) {
                continue;
            }

            int y = m.level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            int roll = rnd.nextInt(10);
            EntityType<? extends AbstractTitan> type = roll < 3 ? ModEntities.ABNORMAL_TITAN.get() : (roll < 6 ? ModEntities.GHOUL_TITAN.get() : ModEntities.PURE_TITAN.get());
            AbstractTitan t = TitanSpawner.spawnAt(m.level, type, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, rnd.nextFloat() * 360.0F);
            if (t != null) {
                t.addTag(AWAKE);
                t.addTag(NIGHT);
                m.titans.add(t.getUUID());
                Fx.send(m.level, Fx.EMERGE, t.position(), t.getTitanHeight() * 0.5F, 0.3F, 200.0);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Mission m = active;
        if (m != null && event.getEntity() instanceof AbstractTitan t && t.getTags().contains(NIGHT) && t.level() == m.level) {
            m.killed++;
        }
    }

    private static void end(Mission m, @Nullable ServerPlayer p, boolean won) {
        active = null;
        m.bar.removeAllPlayers();
        if (p == null) {
            return;
        }

        fog(p, m.at, false);
        if (won) {
            int badges = 6 + m.need;
            int xp = 70 * m.need;
            Story.give(p, new ItemStack(ModItems.MERIT_BADGE.get(), badges));
            Story.addXp(p, xp);
            Story.bannerFor(p, "stage", Component.translatable("night.takemtitans.won"), Component.translatable("wave.takemtitans.reward", badges, xp));
            p.playNotifySound(ModSounds.QUEST_COMPLETE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            Story.bannerFor(p, "stage", Component.translatable("night.takemtitans.lost"), Component.translatable("night.takemtitans.lost_sub", m.killed, m.need));
        }
    }

    private static void fog(ServerPlayer p, Vec3 at, boolean on) {
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "fog");
        tag.putBoolean("on", on);
        tag.putDouble("x", at.x);
        tag.putDouble("z", at.z);
        tag.putDouble("r", FOG_R);
        PacketDistributor.sendToPlayer(p, new StoryPayload(tag));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        active = null;
        lastDay = -1L;
    }
}
