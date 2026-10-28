package com.takem.titans.world;

import com.takem.titans.entity.FlareEntity;
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.JawTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.item.FlareColor;
import com.takem.titans.network.StoryPayload;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.story.Shop;
import com.takem.titans.story.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class TitanRaid {
    public static final int WAVES = 4;
    @Nullable
    private static TitanRaid.Raid active;
    private static long lastRaidDay = -1L;

    private TitanRaid() {
    }

    public static boolean start(ServerLevel level, ServerPlayer player) {
        if (active != null) {
            return false;
        } else {
            String zone = Rebuild.zoneAt(player.getX(), player.getZ());
            // Valgard is kept for the waves
            if (zone == null || zone.contains("wall") || zone.startsWith("arena")) {
                double best = 1.7976931348623157E308;

                for (ShiganshinaLayout.Bulge b : ShiganshinaLayout.BULGES) {
                    double d = player.distanceToSqr(b.cx(), player.getY(), b.cz());
                    if (d < best && b.ring() != ShiganshinaLayout.ARENA_RING) {
                        best = d;
                        zone = b.name();
                    }
                }
            }

            Vec3 c = zone == null ? null : Rebuild.zoneCentre(zone);
            if (c != null && !Rebuild.isFallen(level, c.x, c.z)) {
                double r = zone.equals("mitras") ? 250.0 : 200.0;

                for (ShiganshinaLayout.Bulge bx : ShiganshinaLayout.BULGES) {
                    if (bx.name().equals(zone)) {
                        r = bx.r();
                    }
                }

                active = new TitanRaid.Raid(zone, c, r, OdmServer.data(player).level);
                lastRaidDay = level.getDayTime() / 24000L;

                for (ServerPlayer p : near(level)) {
                    Story.bannerFor(
                        p,
                        "chapter",
                        Component.translatable("raid.takemtitans.start", Rebuild.zoneName(zone)),
                        Component.translatable("raid.takemtitans.start_sub", 4)
                    );
                }

                TownLife.alarm(level, c, r, 2);
                return true;
            } else {
                return false;
            }
        }
    }

    static List<ServerPlayer> near(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        if (active != null) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(active.centre.x, p.getY(), active.centre.z) < (active.r + 300.0) * (active.r + 300.0)) {
                    out.add(p);
                }
            }
        }

        return out;
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD && TitanSpawner.isShiganshina(level)) {
            if (active == null) {
                maybeStart(level);
            } else {
                tick(level, active);
            }
        }
    }

    private static void maybeStart(ServerLevel level) {
        long day = level.getDayTime() / 24000L;
        long time = level.getDayTime() % 24000L;
        if (time == 12000L && day != lastRaidDay) {
            lastRaidDay = day;

            for (ServerPlayer p : level.players()) {
                String zone = Rebuild.zoneAt(p.getX(), p.getZ());
                if (zone != null && !zone.contains("wall") && OdmServer.data(p).stage > 12 && (level.random.nextFloat() < 0.4F || day % 3L == 0L)) {
                    start(level, p);
                    return;
                }
            }
        }
    }

    private static void tick(ServerLevel level, TitanRaid.Raid r) {
        List<ServerPlayer> players = near(level);
        if (players.isEmpty()) {
            if (++r.lonely > 1200) {
                active = null;
            }
        } else {
            r.lonely = 0;
            if (Rebuild.isFallen(level, r.centre.x, r.centre.z)) {
                end(level, r, false);
            } else {
                r.timer--;
                if (r.phase == 0) {
                    if (r.timer % 20 == 0) {
                        for (ServerPlayer p : players) {
                            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 2.0F, 0.8F + level.random.nextFloat() * 0.2F);
                        }
                    }

                    if (r.timer % 60 == 0) {
                        flare(level, r);
                    }

                    if (r.timer <= 0) {
                        r.phase = 1;
                        spawnWave(level, r, players);
                    }
                } else if (r.phase == 1) {
                    if (level.getGameTime() % 20L == 0L) {
                        int alive = 0;

                        for (int i = r.titans.size() - 1; i >= 0; i--) {
                            if (!(level.getEntity(r.titans.get(i)) instanceof AbstractTitan t) || !t.isAlive() || t.isDeadOrDying()) {
                                r.titans.remove(i);
                                continue;
                            }

                            if (t.distanceToSqr(r.centre.x, t.getY(), r.centre.z) > (r.r + 220.0) * (r.r + 220.0)) {
                                t.discard();
                                r.titans.remove(i);
                            } else {
                                alive++;
                                if (t.getTarget() == null || level.random.nextInt(8) == 0) {
                                    t.setTarget(players.get(level.random.nextInt(players.size())));
                                }
                            }
                        }

                        if (alive == 0 && r.titans.isEmpty()) {
                            if (r.wave >= 4) {
                                end(level, r, true);
                                return;
                            }

                            r.phase = 2;
                            r.timer = 200;

                            for (ServerPlayer p : players) {
                                Story.bannerFor(
                                    p,
                                    "stage",
                                    Component.translatable("raid.takemtitans.wave_clear", r.wave, 4),
                                    Component.translatable("raid.takemtitans.next_wave")
                                );
                            }
                        }
                    }
                } else if (r.phase == 2 && r.timer <= 0) {
                    r.phase = 1;
                    spawnWave(level, r, players);
                }

                if (level.getGameTime() % 20L == 3L) {
                    for (ServerPlayer p : players) {
                        status(p, r);
                    }
                }
            }
        }
    }

    private static void spawnWave(ServerLevel level, TitanRaid.Raid r, List<ServerPlayer> players) {
        r.wave++;
        r.timer = 0;
        RandomSource rnd = level.random;
        boolean boss = r.wave >= 4;
        int n = boss ? 4 : 3 + r.wave * 2 + r.level / 10;

        for (int i = 0; i < n + (boss ? 1 : 0); i++) {
            EntityType<? extends AbstractTitan> type = boss && i == 0
                ? ModEntities.JAW_TITAN.get()
                : (
                    rnd.nextInt(10) < 6
                        ? ModEntities.PURE_TITAN.get()
                        : (rnd.nextInt(3) == 0 ? ModEntities.CRAWLER_TITAN.get() : ModEntities.ABNORMAL_TITAN.get())
                );
            AbstractTitan t = spawnOutside(level, r, type);
            if (t != null) {
                r.titans.add(t.getUUID());
                t.setTarget(players.get(rnd.nextInt(players.size())));
                if (!(t instanceof JawTitan)) {
                    t.setTitanHeight(Math.min(15.0F, t.getTitanHeight() + (float)r.wave * 0.8F));
                }
            }
        }

        for (ServerPlayer p : players) {
            if (boss) {
                Story.bannerFor(p, "chapter", Component.translatable("raid.takemtitans.boss"), Component.translatable("raid.takemtitans.boss_sub"));
                p.playNotifySound(ModSounds.ABNORMAL_SCREECH.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
            } else {
                Story.bannerFor(p, "stage", Component.translatable("raid.takemtitans.wave", r.wave, 4), Component.translatable("raid.takemtitans.wave_sub", n));
            }

            p.playNotifySound(ModSounds.TITAN_ROAR.get(), SoundSource.HOSTILE, 2.0F, 0.7F);
        }

        flare(level, r);
    }

    @Nullable
    private static AbstractTitan spawnOutside(ServerLevel level, TitanRaid.Raid r, EntityType<? extends AbstractTitan> type) {
        RandomSource rnd = level.random;

        for (int attempt = 0; attempt < 24; attempt++) {
            double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
            double d = r.r + 16.0 + rnd.nextDouble() * 24.0;
            double x = r.centre.x + Math.cos(a) * d;
            double z = r.centre.z + Math.sin(a) * d;
            if (!r.zone.equals(Rebuild.zoneAt(x, z))
                && ShiganshinaLayout.region(x, z) != ShiganshinaLayout.Region.WALL
                && level.isLoaded(BlockPos.containing(x, 66.0, z))) {
                int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                AbstractTitan t = TitanSpawner.spawnAt(level, type, Math.floor(x) + 0.5, (double)Math.max(y, 64), Math.floor(z) + 0.5, rnd.nextFloat() * 360.0F);
                if (t != null) {
                    Fx.send(level, 16, t.position(), t.getTitanHeight() * 0.5F, 0.5F, 256.0);
                    return t;
                }
            }
        }

        return null;
    }

    private static void flare(ServerLevel level, TitanRaid.Raid r) {
        flare(level, r, FlareColor.RED);
    }

    private static void flare(ServerLevel level, TitanRaid.Raid r, FlareColor color) {
        List<TownsfolkEntity> soldiers = level.getEntitiesOfClass(
            TownsfolkEntity.class, new AABB(r.centre, r.centre).inflate(r.r, 60.0, r.r), t -> t.getRole() == 1
        );
        List<ServerPlayer> players = near(level);
        LivingEntity from = !soldiers.isEmpty() ? soldiers.get(level.random.nextInt(soldiers.size())) : (players.isEmpty() ? null : players.get(0));
        if (from != null) {
            FlareEntity f = new FlareEntity(level, from, color);
            f.setPos(from.getX(), from.getY() + 2.0, from.getZ());
            f.shoot((level.random.nextDouble() - 0.5) * 0.2, 1.0, (level.random.nextDouble() - 0.5) * 0.2, 1.6F, 2.0F);
            level.addFreshEntity(f);
        }
    }

    private static void end(ServerLevel level, TitanRaid.Raid r, boolean won) {
        active = null;
        List<ServerPlayer> players = near2(level, r);
        int pct = Rebuild.percent(level, r.zone);
        int reward = won ? 160 + Math.max(0, 80 - pct * 2) : 0;

        for (ServerPlayer p : players) {
            if (won) {
                Story.bannerFor(
                    p,
                    "story",
                    Component.translatable("raid.takemtitans.won", Rebuild.zoneName(r.zone)),
                    Component.translatable("raid.takemtitans.won_sub", reward, pct)
                );
                Shop.addBadges(p, reward);
                Story.addXp(p, 800);
                p.playNotifySound(ModSounds.QUEST_COMPLETE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            } else {
                Story.bannerFor(
                    p, "story", Component.translatable("raid.takemtitans.lost", Rebuild.zoneName(r.zone)), Component.translatable("raid.takemtitans.lost_sub")
                );
            }
        }

        if (won) {
            for (TownsfolkEntity t : level.getEntitiesOfClass(TownsfolkEntity.class, new AABB(r.centre, r.centre).inflate(r.r, 80.0, r.r))) {
                t.celebrate();
            }

            for (int i = 0; i < 6; i++) {
                flare(level, new TitanRaid.Raid(r.zone, r.centre, r.r, r.level), i % 2 == 0 ? FlareColor.YELLOW : FlareColor.GREEN);
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.putString("t", "raid");

        for (ServerPlayer px : players) {
            PacketDistributor.sendToPlayer(px, new StoryPayload(tag));
        }
    }

    private static List<ServerPlayer> near2(ServerLevel level, TitanRaid.Raid r) {
        List<ServerPlayer> out = new ArrayList<>();

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(r.centre.x, p.getY(), r.centre.z) < (r.r + 300.0) * (r.r + 300.0)) {
                out.add(p);
            }
        }

        return out;
    }

    private static void status(ServerPlayer p, TitanRaid.Raid r) {
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "raid");
        tag.putString("zone", r.zone);
        tag.putInt("wave", r.wave);
        tag.putInt("waves", 4);
        tag.putInt("left", r.titans.size());
        tag.putInt("phase", r.phase);
        tag.putInt("timer", Math.max(0, r.timer));
        tag.putInt("pct", Rebuild.percent(p.serverLevel(), r.zone));
        PacketDistributor.sendToPlayer(p, new StoryPayload(tag));
    }

    static final class Raid {
        final String zone;
        final Vec3 centre;
        final double r;
        int wave;
        int phase;
        int timer = 200;
        int lonely;
        final List<UUID> titans = new ArrayList<>();
        final int level;

        Raid(String zone, Vec3 centre, double r, int level) {
            this.zone = zone;
            this.centre = centre;
            this.r = r;
            this.level = level;
        }
    }
}
