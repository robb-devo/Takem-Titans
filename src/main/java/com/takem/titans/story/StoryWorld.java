package com.takem.titans.story;

import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.ArmoredTitan;
import com.takem.titans.entity.titan.BeastTitan;
import com.takem.titans.entity.titan.RavagerTitan;
import com.takem.titans.entity.titan.WallTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Chunks;
import com.takem.titans.world.Rebuild;
import com.takem.titans.world.ShiganshinaLayout;
import com.takem.titans.world.TitanSpawner;
import com.takem.titans.world.TownLife;
import com.takem.titans.world.TownPlan;
import com.takem.titans.world.TrainingGround;
import com.takem.titans.world.TunnelDungeon;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class StoryWorld {
    static final int GROUND = 66;
    static final Vec3 COMMANDER_HOME = new Vec3(5.5, 0.0, -9.5);
    static final Vec3 QUARTERMASTER_HOME = new Vec3(5.5, 0.0, -57.5);
    static final Vec3 HALE_HOME = new Vec3(5.5, 0.0, -409.5);
    static final Vec3 MIRA_PLAZA = new Vec3(-4.5, 0.0, -9.5);
    static final Vec3 PLAZA = new Vec3(0.5, 0.0, -2.5);
    private static final int AMBIENT_CAP = 10;
    private static final Map<String, Long> BOSS_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> FROZEN = new HashMap<>();

    private StoryWorld() {
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isSpectator()) {
            ServerLevel level = player.serverLevel();
            if (level.getGameTime() % 20L != 7L) {
                OdmPlayerData d = OdmServer.data(player);
                if (d.training < Training.DONE && TitanSpawner.isShiganshina(level)) {
                    Training.everyTick(player, d);
                }

                return;
            }

            OdmPlayerData d = OdmServer.data(player);
            if (!TitanSpawner.isShiganshina(level)) {
                return;
            }

            Training.tick(level, player, d);
            if (!d.introSeen && d.stage == 0 && d.training >= Training.DONE && player.distanceToSqr(0.0, player.getY(), 4.0) < 90000.0) {
                d.introSeen = true;
                Story.cutscene(player, "intro", new Vec3(PLAZA.x, 67.0, PLAZA.z), 180.0F, 300);
                Story.banner(
                    player,
                    "chapter",
                    Component.translatable("quest.takemtitans.chapter.prologue"),
                    Component.translatable("quest.takemtitans.report.obj", 0, 1)
                );
            }

            if (!FROZEN.isEmpty()) {
                FROZEN.entrySet().removeIf(en -> {
                    if (en.getValue() > level.getGameTime()) {
                        return false;
                    } else {
                        if (level.getEntity(en.getKey()) instanceof AbstractTitan t) {
                            t.setNoAi(false);
                        }

                        return true;
                    }
                });
            }

            Story.checkReach(player, d);
            storyPeople(level, player, d);
            ambientPeople(level, player);
            if (d.stage == 7) {
                siege(level, player);
            }

            if (d.stage == 9) {
                boss(level, player, d, ModEntities.ARMORED_TITAN.get(), ArmoredTitan.class, Story.ARMORED_SPAWN, "armored", 150.0);
            }

            if (d.stage == 11) {
                boss(level, player, d, ModEntities.BEAST_TITAN.get(), BeastTitan.class, Story.BEAST_SPAWN, "beast", 150.0);
            }

            if (d.stage == 17 || d.stage == 18) {
                mitrasSiege(level, player, d.stage == 17);
            }

            if (d.stage == 19) {
                boss(level, player, d, ModEntities.RAVAGER_TITAN.get(), RavagerTitan.class, Story.RAVAGER_SPAWN, "ravager", 160.0);
            }

            if (d.stage >= 27) {
                if (d.stage <= 30 && near(player, Story.PIT, 420.0)) {
                    TunnelDungeon.ensureBuilt(level);
                }

                TunnelDungeon.tickPlayer(level, player, d.stage == 29 || d.stage == 30);
            }

            if (d.stage == 30 && level.getGameTime() % 40L == 9L) {
                TunnelDungeon.ensureHearts(level, player);
            }

            if (d.stage == 31) {
                boss(level, player, d, ModEntities.WALL_TITAN.get(), WallTitan.class, Story.WALL_TITAN_SPAWN, "wall_titan", 260.0);
            }

            if (d.stage == 23) {
                districtSiege(level, player, "karanes", Story.KARANES, 6, false);
            } else if (d.stage == 24) {
                districtSiege(level, player, "krolva", Story.KROLVA, 3, true);
            } else if (d.stage == 25) {
                districtSiege(level, player, "stohess", Story.STOHESS, 5, false);
            }

            if (level.getGameTime() % 100L == 7L) {
                Story.sync(player);
            }

            return;
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Story.applyPerks(p);
            Story.sync(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Story.applyPerks(p);
            OdmServer.data(p).cutsceneUntil = 0L;
            Story.sync(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Story.applyPerks(p);
            Story.sync(p);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Story.inCutscene(p)) {
            event.setCanceled(true);
        }
    }

    private static void storyPeople(ServerLevel level, ServerPlayer player, OdmPlayerData d) {
        keep(level, player, 7, TrainingGround.INSTRUCTOR);
        keep(level, player, 2, COMMANDER_HOME);
        keep(level, player, 3, QUARTERMASTER_HOME);
        keep(level, player, 5, HALE_HOME);
        if (d.stage > 5) {
            keep(level, player, 4, MIRA_PLAZA);
        }

        if (d.stage == 5 && near(player, Story.FOREST, 420.0)) {
            List<TownsfolkEntity> mira = level.getEntitiesOfClass(TownsfolkEntity.class, player.getBoundingBox().inflate(96.0), e -> e.getRole() == 4);
            if (mira.isEmpty()) {
                spawnNear(level, player, 4);
            }
        }
    }

    private static void keep(ServerLevel level, ServerPlayer player, int role, Vec3 home) {
        if (near(player, home, 72.0)) {
            BlockPos at = BlockPos.containing(home.x, 67.0, home.z);
            if (level.isLoaded(at)) {
                List<TownsfolkEntity> found = level.getEntitiesOfClass(TownsfolkEntity.class, new AABB(at).inflate(80.0), e -> e.getRole() == role);

                for (int i = 1; i < found.size(); i++) {
                    found.get(i).discard();
                }

                if (found.isEmpty()) {
                    BlockPos spot = freeSpot(level, at, 6);
                    if (spot != null) {
                        TownsfolkEntity npc = ModEntities.TOWNSFOLK.get().create(level);
                        if (npc != null) {
                            npc.moveTo((double)spot.getX() + 0.5, (double)spot.getY(), (double)spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
                            npc.setup(role, 0, spot, false);
                            level.addFreshEntity(npc);
                        }
                    }
                }
            }
        }
    }

    private static void spawnNear(ServerLevel level, ServerPlayer player, int role) {
        RandomSource r = level.random;

        for (int i = 0; i < 12; i++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            BlockPos ground = Story.ground(level, player.getX() + Math.cos(a) * 7.0, player.getZ() + Math.sin(a) * 7.0);
            BlockState below = level.getBlockState(ground.below());
            if (!below.is(BlockTags.LOGS)
                && !below.is(BlockTags.LEAVES)
                && level.getFluidState(ground.below()).isEmpty()
                && level.getBlockState(ground).isAir()
                && level.getBlockState(ground.above()).isAir()) {
                TownsfolkEntity npc = ModEntities.TOWNSFOLK.get().create(level);
                if (npc == null) {
                    return;
                }

                npc.moveTo((double)ground.getX() + 0.5, (double)ground.getY(), (double)ground.getZ() + 0.5, 0.0F, 0.0F);
                npc.setup(role, 0, ground, false);
                npc.getLookControl().setLookAt(player);
                level.addFreshEntity(npc);
                level.sendParticles(ModParticles.GAS.get(), npc.getX(), npc.getY() + 1.0, npc.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
                level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), ModSounds.GAS_BURST.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
                return;
            }
        }
    }

    private static void ambientPeople(ServerLevel level, ServerPlayer player) {
        if (isTown(player.getX(), player.getZ()) && !Rebuild.isFallen(level, player.getX(), player.getZ())) {
            AABB around = player.getBoundingBox().inflate(64.0, 24.0, 64.0);
            int count = level.getEntitiesOfClass(TownsfolkEntity.class, around, e -> !e.isStoryCharacter()).size();
            if (count < (level.isDay() ? 18 : 8)) {
                RandomSource r = level.random;

                for (int attempt = 0; attempt < 6; attempt++) {
                    double a = r.nextDouble() * 3.141592653589793 * 2.0;
                    double dist = 14.0 + r.nextDouble() * 36.0;
                    int x = Mth.floor(player.getX() + Math.cos(a) * dist);
                    int z = Mth.floor(player.getZ() + Math.sin(a) * dist);
                    if (isTown((double)x + 0.5, (double)z + 0.5) && !TownPlan.isBuilding(x, z)) {
                        BlockPos pos = new BlockPos(x, 67, z);
                        if (level.isLoaded(pos)
                            && level.getBlockState(pos).isAir()
                            && level.getBlockState(pos.above()).isAir()
                            && !level.getBlockState(pos.below()).isAir()) {
                            TownsfolkEntity npc = ModEntities.TOWNSFOLK.get().create(level);
                            if (npc == null) {
                                return;
                            }

                            boolean soldier = r.nextInt(5) == 0;
                            npc.moveTo((double)x + 0.5, (double)pos.getY(), (double)z + 0.5, r.nextFloat() * 360.0F, 0.0F);
                            npc.setup(soldier ? 1 : 0, r.nextInt(soldier ? 3 : 6), pos, true);
                            level.addFreshEntity(npc);
                            return;
                        }
                    }
                }
            }
        }
    }

    public static boolean isTown(double x, double z) {
        return ShiganshinaLayout.region(x, z) == ShiganshinaLayout.Region.DISTRICT;
    }

    @Nullable
    private static BlockPos freeSpot(ServerLevel level, BlockPos at, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        for (int dy = -2; dy <= 3; dy++) {
                            BlockPos p = at.offset(dx, dy, dz);
                            if (level.getBlockState(p).isAir()
                                && level.getBlockState(p.above()).isAir()
                                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                                return p;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private static boolean near(ServerPlayer p, Vec3 at, double r) {
        double dx = p.getX() - at.x;
        double dz = p.getZ() - at.z;
        return dx * dx + dz * dz < r * r;
    }

    private static void siege(ServerLevel level, ServerPlayer player) {
        if (near(player, Story.TROST, 320.0) && level.getGameTime() % 60L == 7L) {
            AABB trost = new AABB(Story.TROST.x - 260.0, 46.0, Story.TROST.z - 260.0, Story.TROST.x + 260.0, 146.0, Story.TROST.z + 260.0);
            if (level.getEntitiesOfClass(AbstractTitan.class, trost).size() < 5) {
                spawnInTrost(level, player);
            }
        }
    }

    private static void districtSiege(ServerLevel level, ServerPlayer player, String name, Vec3 at, int max, boolean fallingSky) {
        ShiganshinaLayout.Bulge b = null;

        for (ShiganshinaLayout.Bulge x : ShiganshinaLayout.BULGES) {
            if (x.name().equals(name)) {
                b = x;
            }
        }

        if (b != null && near(player, at, b.r() + 160.0)) {
            if (level.getGameTime() % 70L == 13L) {
                AABB box = new AABB(b.cx() - b.r() - 20.0, 40.0, b.cz() - b.r() - 20.0, b.cx() + b.r() + 20.0, 180.0, b.cz() + b.r() + 20.0);
                if (level.getEntitiesOfClass(AbstractTitan.class, box).size() < max) {
                    spawnInDistrict(level, player, b);
                }
            }

            if (fallingSky
                && level.getGameTime() % 120L == 31L
                && level.getEntitiesOfClass(FallingSectionEntity.class, player.getBoundingBox().inflate(60.0)).size() < 3) {
                dropChunk(level, player);
            }
        }
    }

    static void spawnInDistrict(ServerLevel level, ServerPlayer player, ShiganshinaLayout.Bulge b) {
        RandomSource r = level.random;

        for (int attempt = 0; attempt < 14; attempt++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double dist = Math.sqrt(r.nextDouble()) * (b.r() - 16.0);
            double x = b.cx() + Math.cos(a) * dist;
            double z = b.cz() + Math.sin(a) * dist;
            if (ShiganshinaLayout.bulgeAt(x, z) == b
                && isTown(x, z)
                && !(player.distanceToSqr(x, player.getY(), z) < 1024.0)
                && level.isLoaded(BlockPos.containing(x, 66.0, z))) {
                int roll = r.nextInt(10);
                EntityType<? extends AbstractTitan> type = roll < 5
                    ? ModEntities.PURE_TITAN.get()
                    : (roll < 9 ? ModEntities.ABNORMAL_TITAN.get() : ModEntities.CRAWLER_TITAN.get());
                BlockPos g = Story.ground(level, x, z);
                AbstractTitan t = TitanSpawner.spawnAt(
                    level, type, Math.floor(x) + 0.5, (double)Math.max(g.getY(), 67), Math.floor(z) + 0.5, r.nextFloat() * 360.0F
                );
                if (t != null) {
                    Fx.send(level, 16, t.position(), t.getTitanHeight() * 0.5F, 0.5F, 256.0);
                    TownLife.alarm(level, t.position(), 80.0, 2);
                    return;
                }
            }
        }
    }

    private static void mitrasSiege(ServerLevel level, ServerPlayer player, boolean fallingSky) {
        if (near(player, Story.MITRAS, 360.0)) {
            if (level.getGameTime() % 80L == 7L) {
                AABB city = new AABB(Story.MITRAS.x - 300.0, 40.0, Story.MITRAS.z - 300.0, Story.MITRAS.x + 300.0, 180.0, Story.MITRAS.z + 300.0);
                if (level.getEntitiesOfClass(AbstractTitan.class, city).size() < (fallingSky ? 4 : 7)) {
                    spawnInTown(level, player, Story.MITRAS, 240.0);
                }
            }

            if (fallingSky
                && level.getGameTime() % 140L == 27L
                && level.getEntitiesOfClass(FallingSectionEntity.class, player.getBoundingBox().inflate(60.0)).size() < 3) {
                dropChunk(level, player);
            }
        }
    }

    private static void dropChunk(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.random;

        for (int attempt = 0; attempt < 12; attempt++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double dist = 14.0 + r.nextDouble() * 26.0;
            int x = Mth.floor(player.getX() + Math.cos(a) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(a) * dist);
            if (level.hasChunk(x >> 4, z >> 4)) {
                int top = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (top > 78) {
                    BlockPos at = new BlockPos(x, top - 2, z);
                    FallingSectionEntity fs = Chunks.rip(level, at, 2, 0, 10);
                    if (fs != null) {
                        fs.launch(new Vec3((r.nextDouble() - 0.5) * 0.3, 0.1, (r.nextDouble() - 0.5) * 0.3), Chunks.tumble(r, 0.15F));
                        level.addFreshEntity(fs);
                        Vec3 c = Vec3.atCenterOf(at);
                        level.playSound(null, c.x, c.y, c.z, ModSounds.BUILDING_COLLAPSE.get(), SoundSource.BLOCKS, 4.0F, 0.9F);
                        Fx.send(level, 0, c, 6.0F, 1.5F, 200.0);
                        TownLife.alarm(level, c, 48.0, 1);
                        return;
                    }
                }
            }
        }
    }

    private static void spawnInTown(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
        RandomSource r = level.random;

        for (int attempt = 0; attempt < 10; attempt++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double dist = 40.0 + r.nextDouble() * (radius - 40.0);
            double x = center.x + Math.cos(a) * dist;
            double z = center.z + Math.sin(a) * dist;
            if (isTown(x, z) && !(player.distanceToSqr(x, player.getY(), z) < 1296.0) && level.isLoaded(BlockPos.containing(x, 66.0, z))) {
                int roll = r.nextInt(10);
                EntityType<? extends AbstractTitan> type = roll < 6
                    ? ModEntities.PURE_TITAN.get()
                    : (roll < 9 ? ModEntities.ABNORMAL_TITAN.get() : ModEntities.CRAWLER_TITAN.get());
                BlockPos g = Story.ground(level, x, z);
                AbstractTitan t = TitanSpawner.spawnAt(
                    level, type, Math.floor(x) + 0.5, (double)Math.max(g.getY(), 67), Math.floor(z) + 0.5, r.nextFloat() * 360.0F
                );
                if (t != null) {
                    Fx.send(level, 16, t.position(), t.getTitanHeight() * 0.5F, 0.5F, 256.0);
                    TownLife.alarm(level, t.position(), 80.0, 2);
                    return;
                }
            }
        }
    }

    private static void spawnInTrost(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.random;

        for (int attempt = 0; attempt < 10; attempt++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double dist = 40.0 + r.nextDouble() * 120.0;
            double x = Story.TROST.x + Math.cos(a) * dist;
            double z = Story.TROST.z + Math.sin(a) * dist;
            if (isTown(x, z) && !(player.distanceToSqr(x, player.getY(), z) < 1296.0) && level.isLoaded(BlockPos.containing(x, 66.0, z))) {
                int roll = r.nextInt(10);
                EntityType<? extends AbstractTitan> type = roll < 6
                    ? ModEntities.PURE_TITAN.get()
                    : (roll < 9 ? ModEntities.ABNORMAL_TITAN.get() : ModEntities.CRAWLER_TITAN.get());
                BlockPos g = Story.ground(level, x, z);
                AbstractTitan t = TitanSpawner.spawnAt(
                    level, type, Math.floor(x) + 0.5, (double)Math.max(g.getY(), 67), Math.floor(z) + 0.5, r.nextFloat() * 360.0F
                );
                if (t != null) {
                    Fx.send(level, 16, t.position(), t.getTitanHeight() * 0.5F, 0.5F, 256.0);
                    return;
                }
            }
        }
    }

    private static <T extends AbstractTitan> void boss(
        ServerLevel level, ServerPlayer player, OdmPlayerData d, EntityType<T> type, Class<T> cls, Vec3 spawn, String scene, double trigger
    ) {
        if (near(player, spawn, trigger)) {
            List<T> alive = level.getEntitiesOfClass(cls, player.getBoundingBox().inflate(220.0, 96.0, 220.0));
            if (!alive.isEmpty()) {
                for (T b : alive) {
                    if (b.isNoAi() && !FROZEN.containsKey(b.getUUID())) {
                        b.setNoAi(false);
                    }
                }
            } else if (level.getEntitiesOfClass(cls, new AABB(spawn.x - 200.0, 26.0, spawn.z - 200.0, spawn.x + 200.0, 186.0, spawn.z + 200.0)).isEmpty()) {
                String key = level.dimension().location() + "/" + scene;
                Long cd = BOSS_COOLDOWN.get(key);
                if ((cd == null || cd <= level.getGameTime())
                    && (d.bossStage != d.stage || level.getGameTime() - d.bossTime >= 6000L || level.getGameTime() < d.bossTime)) {
                    BlockPos g = Story.ground(level, spawn.x, spawn.z);
                    if (level.isLoaded(g) && level.areEntitiesLoaded(ChunkPos.asLong(g))) {
                        d.bossStage = d.stage;
                        d.bossTime = level.getGameTime();
                        float yaw = (float)(Mth.atan2(player.getZ() - spawn.z, player.getX() - spawn.x) * 180.0 / 3.1415927410125732) - 90.0F;
                        T t = (T)type.create(level);
                        if (t != null) {
                            t.moveTo(spawn.x, (double)Math.max(g.getY(), 67), spawn.z, yaw, 0.0F);
                            t.finalizeSpawn(level, level.getCurrentDifficultyAt(g), MobSpawnType.EVENT, null);
                            t.yBodyRot = yaw;
                            t.yHeadRot = yaw;
                            level.addFreshEntity(t);
                            t.setNoAi(true);
                            FROZEN.put(t.getUUID(), level.getGameTime() + 190L);
                            level.playSound(
                                null,
                                t.getX(),
                                t.getY() + 10.0,
                                t.getZ(),
                                t instanceof BeastTitan
                                    ? ModSounds.BEAST_ROAR.get()
                                    : (t instanceof RavagerTitan ? ModSounds.ABNORMAL_SCREECH.get() : ModSounds.TITAN_ROAR.get()),
                                SoundSource.HOSTILE,
                                8.0F,
                                0.8F
                            );
                            BOSS_COOLDOWN.put(key, level.getGameTime() + 1200L);
                            Vec3 anchor = new Vec3(spawn.x, (double)Math.max(g.getY(), 67), spawn.z);

                            for (ServerPlayer p : level.players()) {
                                if (near(p, spawn, trigger + 40.0)) {
                                    Story.cutscene(p, scene, anchor, yaw, 200);
                                }
                            }

                            AbstractTitan.shakeNearby(level, t.position(), 120.0, 1.2F, 40);
                        }
                    }
                }
            }
        }
    }

    static void onStageStarted(ServerPlayer player, int stage) {
        ServerLevel level = player.serverLevel();
        if (TitanSpawner.isShiganshina(level)) {
            if (stage == 7) {
                float yaw = (float)(Mth.atan2(Story.TROST.z - player.getZ(), Story.TROST.x - player.getX()) * 180.0 / 3.1415927410125732) - 90.0F;
                Story.cutscene(player, "trost", player.position(), yaw, 220);

                for (int i = 0; i < 3; i++) {
                    spawnInTrost(level, player);
                }
            } else if (stage == 5) {
                spawnNear(level, player, 4);
            } else if (stage == 17) {
                float yaw = (float)(Mth.atan2(Story.MITRAS.z - player.getZ(), Story.MITRAS.x - player.getX()) * 180.0 / 3.1415927410125732) - 90.0F;
                Story.cutscene(player, "falling", player.position(), yaw, 220);

                for (int i = 0; i < 3; i++) {
                    spawnInTown(level, player, Story.MITRAS, 240.0);
                }
            } else if (stage == 14) {
                Squad.ensure(level, player);
            } else if (stage == 28) {
                TunnelDungeon.ensureBuilt(level);
            } else if (stage == 30) {
                Story.cutscene(player, "tunnels", player.position(), player.getYRot(), 220);
            } else if (stage == 23 || stage == 24) {
                Vec3 at = stage == 23 ? Story.KARANES : Story.KROLVA;
                float yaw = (float)(Mth.atan2(at.z - player.getZ(), at.x - player.getX()) * 180.0 / 3.1415927410125732) - 90.0F;
                Story.cutscene(player, stage == 23 ? "karanes" : "krolva", player.position(), yaw, 220);
                String name = stage == 23 ? "karanes" : "krolva";

                for (ShiganshinaLayout.Bulge b : ShiganshinaLayout.BULGES) {
                    if (b.name().equals(name)) {
                        for (int i = 0; i < 3; i++) {
                            spawnInDistrict(level, player, b);
                        }
                    }
                }
            }
        }
    }

    static void afterTalk(ServerPlayer player, TownsfolkEntity npc, int done) {
        ServerLevel level = player.serverLevel();
        if (done == 5) {
            npc.leaveSoon();
        }

        if (done == 20) {
            Story.cutscene(player, "epilogue", new Vec3(PLAZA.x, 67.0, PLAZA.z), 180.0F, 340);
        }

        if (done == 12) {
            Story.cutscene(player, "finale", new Vec3(PLAZA.x, 67.0, PLAZA.z), 180.0F, 340);
            long day = level.getDayTime();
            level.setDayTime(day - Math.floorMod(day, 24000L) + 23200L + (Math.floorMod(day, 24000L) > 23200L ? 24000L : 0L));
        }
    }

    @Nullable
    static Vec3 target(ServerPlayer player, OdmPlayerData d) {
        if (!TitanSpawner.isShiganshina(player.level())) {
            return null;
        } else {
            Story.Stage s = Story.stage(d);

            return switch (s.obj()) {
                case TALK -> {
                    switch (s.role()) {
                        case 2:
                            yield COMMANDER_HOME;
                        case 3:
                        default:
                            yield null;
                        case 4:
                            List<TownsfolkEntity> m = player.level()
                                .getEntitiesOfClass(TownsfolkEntity.class, player.getBoundingBox().inflate(96.0), e -> e.getRole() == 4);
                            yield m.isEmpty() ? Story.FOREST : m.get(0).position();
                        case 5:
                            yield HALE_HOME;
                    }
                }
                case REACH, KILL_AREA -> new Vec3(s.x(), 0.0, s.z());
                case CATCH -> new Vec3(s.x(), 0.0, s.z());
                case HEARTS -> TunnelDungeon.chamber(player.serverLevel());
                case BOSS -> {
                    Class<? extends AbstractTitan> cls = d.stage == 9
                        ? ArmoredTitan.class
                        : (d.stage == 19 ? RavagerTitan.class : (d.stage == 31 ? WallTitan.class : BeastTitan.class));
                    List<? extends AbstractTitan> b = player.level().getEntitiesOfClass(cls, player.getBoundingBox().inflate(200.0, 96.0, 200.0));
                    yield b.isEmpty() ? new Vec3(s.x(), 0.0, s.z()) : b.get(0).position();
                }
                case CONTRACTS -> d.contractKind <= 0 ? COMMANDER_HOME : null;
                default -> null;
            };
        }
    }
}
