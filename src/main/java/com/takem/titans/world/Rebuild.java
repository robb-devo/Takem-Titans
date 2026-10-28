package com.takem.titans.world;

import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.network.StoryPayload;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.story.Shop;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent.Detonate;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Rebuild {
    private static final int START_DELAY = 1200;
    private static final int MAX_PER_TICK = 320;
    private static final int MAX_BUILDERS = 3;
    private static final Map<Long, BlockState[]> COLUMNS = new LinkedHashMap<Long, BlockState[]>(4096, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Entry<Long, BlockState[]> e) {
            return this.size() > 4096;
        }
    };
    private static ColumnBuffer buffer;
    private static int columnMinY;
    private static final Map<String, Integer> BASELINE = new HashMap<>();

    private Rebuild() {
    }

    @Nullable
    public static String zoneAt(double x, double z) {
        ShiganshinaLayout.Region r = ShiganshinaLayout.region(x, z);
        if (r == ShiganshinaLayout.Region.WALL) {
            int ring = ShiganshinaLayout.ring(x, z);

            return switch (ring) {
                case 1 -> "wall_maria";
                default -> {
                    ShiganshinaLayout.Bulge b = ShiganshinaLayout.bulge(ring);
                    yield b != null ? b.name() + "_wall" : null;
                }
                case 3 -> "wall_rose";
                case 5 -> "wall_sina";
            };
        } else if (r == ShiganshinaLayout.Region.DISTRICT) {
            ShiganshinaLayout.Bulge b = ShiganshinaLayout.bulgeAt(x, z);
            return b != null ? b.name() : "mitras";
        } else {
            return null;
        }
    }

    static Vec3 zoneCentre(String zone) {
        if (!zone.equals("mitras") && !zone.equals("wall_sina")) {
            if (!zone.equals("wall_rose") && !zone.equals("wall_maria")) {
                ShiganshinaLayout.Bulge b = null;

                for (ShiganshinaLayout.Bulge x : ShiganshinaLayout.BULGES) {
                    if (zone.startsWith(x.name())) {
                        b = x;
                    }
                }

                return b == null ? null : new Vec3(b.cx(), 67.0, b.cz());
            } else {
                return null;
            }
        } else {
            return new Vec3(0.0, 67.0, -1100.0);
        }
    }

    private static boolean excluded(int x, int y, int z) {
        double dx = (double)x - 304.0;
        double dz = (double)z - -1103.0;
        if (!(dx * dx + dz * dz < 100.0) && (ShiganshinaLayout.nearGate(x, z) == 0 || Math.abs(x) > 10) && y >= 40) {
            for (Stalls.Stall s : Stalls.all()) {
                if (Math.abs(s.x() - x) <= 4 && Math.abs(s.z() - z) <= 4) {
                    return true;
                }
            }

            return false;
        } else {
            return true;
        }
    }

    static int baseline(ServerLevel level, String zone) {
        Integer b = BASELINE.get(zone);
        if (b == null) {
            Vec3 c = zoneCentre(zone);
            ShiganshinaLayout.Bulge bulge = null;

            for (ShiganshinaLayout.Bulge x : ShiganshinaLayout.BULGES) {
                if (x.name().equals(zone)) {
                    bulge = x;
                }
            }

            double r = zone.equals("mitras") ? 250.0 : (bulge != null ? bulge.r() : 0.0);
            if (c != null && !(r <= 0.0)) {
                Random rnd = new Random((long)zone.hashCode());
                long sum = 0L;
                int n = 1200;
                MutableBlockPos m = new MutableBlockPos();

                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
                    double d = Math.sqrt(rnd.nextDouble()) * r;
                    int xx = Mth.floor(c.x + Math.cos(a) * d);
                    int z = Mth.floor(c.z + Math.sin(a) * d);
                    if (zone.equals(zoneAt((double)xx + 0.5, (double)z + 0.5))) {
                        for (int y = 67; y < 140; y++) {
                            BlockState s = pristine(level, m.set(xx, y, z));
                            if (s != null && !s.isAir()) {
                                sum++;
                            }
                        }
                    }
                }

                b = (int)Math.max(2000L, (long)((double)sum / (double)n * 3.141592653589793 * r * r * 0.85));
            } else {
                b = 2147483647;
            }

            BASELINE.put(zone, b);
        }

        return b;
    }

    public static int percent(ServerLevel level, String zone) {
        Rebuild.Zone z = Rebuild.Data.get(level).zones.get(zone);
        return z == null ? 0 : (int)Math.min(100L, (long)z.pending * 100L / (long)Math.max(1, baseline(level, zone)));
    }

    public static int fortLevel(ServerLevel level, @Nullable String zone) {
        return zone == null ? 0 : Rebuild.Data.get(level).fort.getOrDefault(zone, 0);
    }

    public static boolean resists(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl && TitanSpawner.isShiganshina(sl)) {
            Rebuild.Data d = Rebuild.Data.get(sl);
            if (!d.fort.isEmpty()) {
                int f = d.fort.getOrDefault(zoneAt((double)pos.getX() + 0.5, (double)pos.getZ() + 0.5), 0);
                return f > 0 && sl.random.nextFloat() < 0.18F * (float)f;
            }
        }

        return false;
    }

    public static boolean isFallen(ServerLevel level, double x, double z) {
        String zone = zoneAt(x, z);
        Rebuild.Zone zz = zone == null ? null : Rebuild.Data.get(level).zones.get(zone);
        return zz != null && zz.fallen;
    }

    private static void fall(ServerLevel level, String zone, Rebuild.Zone z) {
        z.fallen = true;
        z.repairStart = -1L;
        z.quota = 0.0F;
        clearScaffold(level, z);

        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(
                Component.translatable("message.takemtitans.district_fallen", zoneName(zone), rebuildCost(z))
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
            );
            p.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 2.0F, 0.5F);
        }

        Vec3 c = zoneCentre(zone);
        if (c != null) {
            for (TownsfolkEntity t : level.getEntitiesOfClass(
                TownsfolkEntity.class, new AABB(c, c).inflate(260.0, 100.0, 260.0), e -> !e.isStoryCharacter() && zone.equals(zoneAt(e.getX(), e.getZ()))
            )) {
                t.leaveSoon();
            }
        }
    }

    static int rebuildCost(Rebuild.Zone z) {
        return 300 + z.pending / 40;
    }

    @Nullable
    public static String nearestFallen(ServerPlayer p) {
        Rebuild.Data d = Rebuild.Data.get(p.serverLevel());
        String best = null;
        double bd = 1.7976931348623157E308;

        for (Entry<String, Rebuild.Zone> e : d.zones.entrySet()) {
            Vec3 c = zoneCentre(e.getKey());
            if (e.getValue().fallen && c != null && p.distanceToSqr(c) < bd) {
                bd = p.distanceToSqr(c);
                best = e.getKey();
            }
        }

        return best;
    }

    public static int rebuildCost(ServerPlayer p, String zone) {
        Rebuild.Zone z = Rebuild.Data.get(p.serverLevel()).zones.get(zone);
        return z == null ? 0 : rebuildCost(z);
    }

    public static void buyRebuild(ServerPlayer p) {
        String zone = nearestFallen(p);
        if (zone != null) {
            Rebuild.Data d = Rebuild.Data.get(p.serverLevel());
            Rebuild.Zone z = d.zones.get(zone);
            int cost = rebuildCost(z);
            if (Shop.spend(p, cost)) {
                z.fallen = false;
                z.repairStart = -1L;
                z.lastDamage = p.serverLevel().getGameTime() - 1200L;
                d.setDirty();

                for (ServerPlayer o : p.getServer().getPlayerList().getPlayers()) {
                    o.sendSystemMessage(
                        Component.translatable("message.takemtitans.district_reborn", p.getName(), zoneName(zone)).withStyle(ChatFormatting.GOLD)
                    );
                }
            } else {
                p.displayClientMessage(Component.translatable("shop.takemtitans.too_poor", cost).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @Nullable
    public static String fortifiable(ServerPlayer p) {
        String zone = zoneAt(p.getX(), p.getZ());
        if (zone != null && !zone.contains("wall")) {
            Rebuild.Data d = Rebuild.Data.get(p.serverLevel());
            Rebuild.Zone z = d.zones.get(zone);
            return (z == null || !z.fallen) && d.fort.getOrDefault(zone, 0) < 3 ? zone : null;
        } else {
            return null;
        }
    }

    public static int fortCost(ServerPlayer p, String zone) {
        return 150 * (Rebuild.Data.get(p.serverLevel()).fort.getOrDefault(zone, 0) + 1);
    }

    public static void buyFort(ServerPlayer p) {
        String zone = fortifiable(p);
        if (zone != null) {
            Rebuild.Data d = Rebuild.Data.get(p.serverLevel());
            int cost = fortCost(p, zone);
            if (Shop.spend(p, cost)) {
                int lvl = d.fort.getOrDefault(zone, 0) + 1;
                d.fort.put(zone, lvl);
                d.setDirty();
                p.sendSystemMessage(Component.translatable("message.takemtitans.fortified", zoneName(zone), lvl).withStyle(ChatFormatting.GOLD));
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
            } else {
                p.displayClientMessage(Component.translatable("shop.takemtitans.too_poor", cost).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private static void scaffold(ServerLevel level, Rebuild.Zone z, BlockPos target) {
        if (z.scaffold.size() < 400 && level.random.nextInt(5) == 0) {
            int ground = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
            if (target.getY() - 66 > 3) {
                for (Direction dir : Plane.HORIZONTAL) {
                    BlockPos side = target.relative(dir);
                    if (level.getBlockState(side).isAir() && level.getBlockState(side.below()).isAir()) {
                        int y = side.getY();
                        MutableBlockPos m = new MutableBlockPos(side.getX(), y, side.getZ());

                        while (y > level.getMinBuildHeight() && level.getBlockState(m.setY(y - 1)).isAir()) {
                            y--;
                        }

                        if (side.getY() - y <= 24) {
                            for (int yy = y; yy < side.getY(); yy++) {
                                m.setY(yy);
                                if (level.getBlockState(m).isAir()) {
                                    level.setBlock(m, Blocks.SCAFFOLDING.defaultBlockState(), 3);
                                    z.scaffold.add(m.asLong());
                                }
                            }
                        }

                        return;
                    }
                }
            }
        }
    }

    private static void clearScaffold(ServerLevel level, Rebuild.Zone z) {
        MutableBlockPos m = new MutableBlockPos();

        for (int i = z.scaffold.size() - 1; i >= 0; i--) {
            m.set(z.scaffold.getLong(i));
            if (level.isLoaded(m) && level.getBlockState(m).is(Blocks.SCAFFOLDING)) {
                level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            }
        }

        z.scaffold.clear();
    }

    public static void mark(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD && TitanSpawner.isShiganshina(sl)) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (!excluded(x, y, z)) {
                String zone = zoneAt((double)x + 0.5, (double)z + 0.5);
                if (zone != null) {
                    Rebuild.Data d = Rebuild.Data.get(sl);
                    Rebuild.Zone zz = d.zones.computeIfAbsent(zone, k -> new Rebuild.Zone());
                    zz.add(pos.asLong());
                    zz.lastDamage = sl.getGameTime();
                    if (!zz.fallen && !zone.contains("wall") && !zone.startsWith("arena") && zz.pending % 64 == 0 && zz.pending >= baseline(sl, zone)) {
                        fall(sl, zone, zz);
                    }

                    if (zz.repairStart >= 0L) {
                        zz.repairEnd = Math.max(zz.repairEnd, sl.getGameTime() + duration(zz.pending) / 2L);
                    }

                    d.setDirty();
                }
            }
        }
    }

    /** Sets a zone's repair going now, to be done within {@code ticks} (Valgard, once a wave run is over). */
    public static void hurry(ServerLevel level, String zone, int ticks) {
        Rebuild.Data d = Rebuild.Data.get(level);
        Rebuild.Zone z = d.zones.get(zone);
        if (z != null && z.pending > 0) {
            long now = level.getGameTime();
            z.lastDamage = now - 1200L;
            z.repairStart = now;
            z.repairEnd = now + (long)ticks;
            d.setDirty();
        }
    }

    @SubscribeEvent
    public static void onExplosion(Detonate event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            for (BlockPos p : event.getAffectedBlocks()) {
                if (!sl.getBlockState(p).isAir()) {
                    mark(sl, p);
                }
            }
        }
    }

    static long duration(int blocks) {
        return (long)Mth.clamp(Math.sqrt((double)blocks) * 600.0, 6000.0, 72000.0);
    }

    @Nullable
    static BlockState pristine(ServerLevel level, BlockPos pos) {
        if (!(level.getChunkSource().getGenerator() instanceof ShiganshinaChunkGenerator gen)) {
            return null;
        } else {
            long var7 = ChunkPos.asLong(pos.getX(), pos.getZ());
            BlockState[] col = COLUMNS.get(var7);
            if (col == null) {
                if (buffer == null || columnMinY != level.getMinBuildHeight()) {
                    buffer = new ColumnBuffer(level.getMinBuildHeight(), level.getHeight());
                    columnMinY = level.getMinBuildHeight();
                }

                buffer.reset(pos.getX(), pos.getZ());
                gen.buildColumn(buffer, GiantTrees.nearForest(pos.getX() & -16 | 8, pos.getZ() & -16 | 8));
                col = (BlockState[])buffer.states.clone();
                COLUMNS.put(var7, col);
            }

            int i = pos.getY() - level.getMinBuildHeight();
            return i >= 0 && i < col.length ? col[i] : Blocks.AIR.defaultBlockState();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            Rebuild.Data d = Rebuild.Data.get(level);
            if (!d.zones.isEmpty() || !d.fort.isEmpty()) {
                long now = level.getGameTime();
                boolean dirty = false;
                List<String> done = new ArrayList<>();

                for (Entry<String, Rebuild.Zone> e : d.zones.entrySet()) {
                    Rebuild.Zone z = e.getValue();
                    if (z.pending <= 0) {
                        done.add(e.getKey());
                    } else if (!z.fallen && now >= z.lastDamage + 1200L) {
                        if (now % 40L == 0L) {
                            z.paused = titansInside(level, e.getKey());
                        }

                        if (!z.paused) {
                            if (z.repairStart < 0L) {
                                z.repairStart = now;
                                z.repairEnd = now + (long)((double)duration(z.pending) / (1.0 + 0.5 * (double)d.fort.getOrDefault(e.getKey(), 0).intValue()));
                                announce(level, e.getKey(), z);
                            }

                            long left = Math.max(20L, z.repairEnd - now);
                            z.quota = Math.min(40000.0F, z.quota + (float)z.pending / (float)left);
                            dirty |= work(level, e.getKey(), z);
                            if (now % 100L == 30L) {
                                builders(level, e.getKey(), z);
                            }
                        }
                    }
                }

                for (String k : done) {
                    Rebuild.Zone z = d.zones.remove(k);
                    clearScaffold(level, z);
                    finished(level, k, z);
                    dirty = true;
                }

                if (dirty) {
                    d.setDirty();
                }

                if (now % 40L == 5L) {
                    for (ServerPlayer p : level.players()) {
                        sendStatus(p, d);
                    }
                }
            }
        }
    }

    private static boolean titansInside(ServerLevel level, String zone) {
        Vec3 c = zoneCentre(zone);
        if (c == null) {
            return false;
        } else {
            double r = zone.equals("mitras") ? 250.0 : 210.0;

            for (AbstractTitan t : level.getEntitiesOfClass(AbstractTitan.class, new AABB(c, c).inflate(r, 120.0, r))) {
                if (!t.isDummy() && zone.equals(zoneAt(t.getX(), t.getZ()))) {
                    return true;
                }
            }

            return false;
        }
    }

    private static boolean work(ServerLevel level, String zoneName, Rebuild.Zone z) {
        int budget = Math.min(320, (int)z.quota);
        if (budget <= 0) {
            return false;
        } else {
            int sounds = 0;
            int fx = 0;
            boolean any = false;
            long chunk = z.builderChunk;
            if (chunk == -9223372036854775808L || !z.byChunk.containsKey(chunk) || !level.hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
                chunk = -9223372036854775808L;
                LongIterator set = z.byChunk.keySet().iterator();

                while (set.hasNext()) {
                    long c = (Long)set.next();
                    if (level.hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c))) {
                        chunk = c;
                        break;
                    }
                }

                z.builderChunk = chunk;
            }

            if (chunk == -9223372036854775808L) {
                return false;
            } else {
                LongOpenHashSet set = (LongOpenHashSet)z.byChunk.get(chunk);
                LongArrayList order = z.sorted.computeIfAbsent(chunk, c -> {
                    LongArrayList l = new LongArrayList(set);
                    l.sort((a, bx) -> Integer.compare(BlockPos.getY(a), BlockPos.getY(bx)));
                    return l;
                });
                MutableBlockPos m = new MutableBlockPos();

                while (budget > 0 && !order.isEmpty()) {
                    long p = order.removeLong(0);
                    if (set.remove(p)) {
                        z.pending--;
                        z.repaired++;
                        z.quota--;
                        budget--;
                        any = true;
                        m.set(p);
                        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(m)).isEmpty()) {
                            set.add(p);
                            z.pending++;
                            z.repaired--;
                        } else {
                            BlockState want = pristine(level, m);
                            if (want != null) {
                                BlockState have = level.getBlockState(m);
                                if (have != want && !have.is(Blocks.BEDROCK)) {
                                    level.setBlock(m, want, 3);
                                    if (!want.isAir() && fx < 10
                                        && level.getNearestPlayer((double)m.getX(), (double)m.getY(), (double)m.getZ(), 56.0, false) != null) {
                                        fx++;
                                        com.takem.titans.fx.Fx.send(level, com.takem.titans.fx.Fx.REBUILD, m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5,
                                            (float)Block.getId(want), 0.0F, 0.0F, 0.0F, 64.0);
                                    }

                                    if (!want.isAir()
                                        && sounds < 3
                                        && level.getNearestPlayer((double)m.getX(), (double)m.getY(), (double)m.getZ(), 40.0, false) != null
                                        && level.random.nextInt(3) == 0) {
                                        sounds++;
                                        level.levelEvent(2001, m, Block.getId(want));
                                    }

                                    expand(level, z, m);
                                    scaffold(level, z, m.immutable());

                                    for (TownsfolkEntity b : level.getEntitiesOfClass(TownsfolkEntity.class, new AABB(m).inflate(24.0), e -> e.getRole() == 6)) {
                                        b.setWorkTarget(m.immutable());
                                    }
                                }
                            }
                        }
                    }
                }

                if (set.isEmpty()) {
                    clearScaffold(level, z);
                    z.byChunk.remove(chunk);
                    z.sorted.remove(chunk);
                    z.builderChunk = -9223372036854775808L;
                } else if (order.isEmpty()) {
                    z.sorted.remove(chunk);
                }

                return any;
            }
        }
    }

    private static void expand(ServerLevel level, Rebuild.Zone z, BlockPos p) {
        for (Direction dir : Direction.values()) {
            BlockPos q = p.relative(dir);
            if (!excluded(q.getX(), q.getY(), q.getZ()) && level.isLoaded(q)) {
                BlockState have = level.getBlockState(q);
                if (have.isAir() || have.is(Blocks.FIRE) || have.is(Blocks.SOUL_FIRE)) {
                    BlockState want = pristine(level, q);
                    if (want != null && !want.isAir() && zoneAt((double)q.getX() + 0.5, (double)q.getZ() + 0.5) != null) {
                        z.add(q.asLong());
                    }
                }
            }
        }
    }

    private static void builders(ServerLevel level, String zoneName, Rebuild.Zone z) {
        if (z.builderChunk != -9223372036854775808L && !zoneName.startsWith("wall_")) {
            int cx = (ChunkPos.getX(z.builderChunk) << 4) + 8;
            int cz = (ChunkPos.getZ(z.builderChunk) << 4) + 8;
            if (level.getNearestPlayer((double)cx, 70.0, (double)cz, 96.0, false) != null) {
                List<TownsfolkEntity> crew = level.getEntitiesOfClass(
                    TownsfolkEntity.class,
                    new AABB((double)(cx - 80), 40.0, (double)(cz - 80), (double)(cx + 80), 160.0, (double)(cz + 80)),
                    e -> e.getRole() == 6
                );
                if (crew.size() < 3) {
                    for (int attempt = 0; attempt < 8; attempt++) {
                        int x = cx + level.random.nextInt(24) - 12;
                        int zz = cz + level.random.nextInt(24) - 12;
                        int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, zz);
                        BlockPos at = new BlockPos(x, y, zz);
                        if (y <= 70
                            && level.getBlockState(at).isAir()
                            && level.getBlockState(at.above()).isAir()
                            && zoneName.equals(zoneAt((double)x + 0.5, (double)zz + 0.5))) {
                            TownsfolkEntity b = ModEntities.TOWNSFOLK.get().create(level);
                            if (b != null) {
                                b.moveTo((double)x + 0.5, (double)y, (double)zz + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
                                b.setup(6, level.random.nextInt(3), at, true);
                                level.addFreshEntity(b);
                                b.shout("builder_start", ChatFormatting.YELLOW);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private static void announce(ServerLevel level, String zone, Rebuild.Zone z) {
        Vec3 c = zoneCentre(zone);

        for (ServerPlayer p : level.players()) {
            if (c == null || p.distanceToSqr(c) < 90000.0) {
                p.displayClientMessage(
                    Component.translatable("message.takemtitans.rebuild_start", zoneName(zone), hours(z.repairEnd - z.repairStart))
                        .withStyle(ChatFormatting.YELLOW),
                    false
                );
            }
        }
    }

    private static void finished(ServerLevel level, String zone, Rebuild.Zone z) {
        Vec3 c = zoneCentre(zone);

        for (ServerPlayer p : level.players()) {
            if (c == null || p.distanceToSqr(c) < 90000.0) {
                p.displayClientMessage(Component.translatable("message.takemtitans.rebuild_done", zoneName(zone)).withStyle(ChatFormatting.GREEN), false);
            }
        }

        if (c != null) {
            for (TownsfolkEntity b : level.getEntitiesOfClass(TownsfolkEntity.class, new AABB(c, c).inflate(260.0, 100.0, 260.0), e -> e.getRole() == 6)) {
                b.shout("builder_done", ChatFormatting.GREEN);
                b.leaveSoon();
            }
        }
    }

    public static Component zoneName(String zone) {
        return Component.translatable("zone.takemtitans." + zone);
    }

    static String hours(long ticks) {
        long h = Math.max(1L, ticks / 1000L);
        return h >= 24L ? h / 24L + "d " + h % 24L + "h" : h + "h";
    }

    public static int pendingAt(ServerLevel level, double x, double z) {
        String zone = zoneAt(x, z);
        if (zone == null) {
            return 0;
        } else {
            Rebuild.Zone zz = Rebuild.Data.get(level).zones.get(zone);
            return zz == null ? 0 : zz.pending;
        }
    }

    private static void sendStatus(ServerPlayer p, Rebuild.Data d) {
        String zone = zoneAt(p.getX(), p.getZ());
        Rebuild.Zone z = zone == null ? null : d.zones.get(zone);
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "town");
        if (z != null && z.pending > 0) {
            tag.putString("zone", zone);
            tag.putInt("pending", z.pending);
            tag.putInt("initial", Math.max(1, z.initial));
            tag.putInt("state", z.fallen ? 3 : (z.repairStart < 0L ? 0 : (z.paused ? 1 : 2)));
            tag.putInt("pct", (int)Math.min(100L, (long)z.pending * 100L / (long)Math.max(1, baseline((ServerLevel)p.level(), zone))));
            tag.putInt("cost", rebuildCost(z));
            long now = p.level().getGameTime();
            tag.putLong("eta", z.repairStart < 0L ? z.lastDamage + 1200L - now : z.repairEnd - now);
        }

        if (zone != null) {
            tag.putInt("fort", d.fort.getOrDefault(zone, 0));
            tag.putString("here", zone);
        }

        PacketDistributor.sendToPlayer(p, new StoryPayload(tag));
    }

    public static final class Data extends SavedData {
        final Map<String, Rebuild.Zone> zones = new HashMap<>();
        final Map<String, Integer> fort = new HashMap<>();

        static Rebuild.Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(Rebuild.Data::new, Rebuild.Data::load, null), "takemtitans_rebuild");
        }

        private static Rebuild.Data load(CompoundTag tag, Provider registries) {
            Rebuild.Data d = new Rebuild.Data();
            CompoundTag fort = tag.getCompound("Fort");

            for (String key : fort.getAllKeys()) {
                d.fort.put(key, fort.getInt(key));
            }

            for (String key : tag.getAllKeys()) {
                if (!key.equals("Fort")) {
                    CompoundTag z = tag.getCompound(key);
                    Rebuild.Zone zone = new Rebuild.Zone();

                    for (long p : z.getLongArray("Pos")) {
                        zone.add(p);
                    }

                    zone.initial = Math.max(zone.pending, z.getInt("Initial"));
                    zone.repaired = Math.max(0, zone.initial - zone.pending);
                    zone.lastDamage = z.getLong("Last");
                    zone.repairStart = z.getLong("Start");
                    zone.repairEnd = z.getLong("End");
                    zone.fallen = z.getBoolean("Fallen");
                    if (zone.pending > 0) {
                        d.zones.put(key, zone);
                    }
                }
            }

            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, Provider registries) {
            for (Entry<String, Rebuild.Zone> e : this.zones.entrySet()) {
                Rebuild.Zone zone = e.getValue();
                if (zone.pending > 0) {
                    LongArrayList all = new LongArrayList();
                    ObjectIterator z = zone.byChunk.values().iterator();

                    while (z.hasNext()) {
                        LongOpenHashSet s = (LongOpenHashSet)z.next();
                        all.addAll(s);
                    }

                    CompoundTag zx = new CompoundTag();
                    zx.put("Pos", new LongArrayTag(all.toLongArray()));
                    zx.putInt("Initial", zone.initial);
                    zx.putLong("Last", zone.lastDamage);
                    zx.putLong("Start", zone.repairStart);
                    zx.putLong("End", zone.repairEnd);
                    zx.putBoolean("Fallen", zone.fallen);
                    tag.put(e.getKey(), zx);
                }
            }

            CompoundTag f = new CompoundTag();

            for (Entry<String, Integer> ex : this.fort.entrySet()) {
                f.putInt(ex.getKey(), ex.getValue());
            }

            tag.put("Fort", f);
            return tag;
        }
    }

    static final class Zone {
        final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap();
        final Map<Long, LongArrayList> sorted = new HashMap<>();
        int pending;
        int initial;
        long lastDamage;
        long repairStart = -1L;
        long repairEnd;
        float quota;
        int repaired;
        boolean paused;
        boolean fallen;
        long builderChunk = -9223372036854775808L;
        final LongArrayList scaffold = new LongArrayList();

        void add(long pos) {
            long c = ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
            if (((LongOpenHashSet)this.byChunk.computeIfAbsent(c, k -> new LongOpenHashSet())).add(pos)) {
                this.pending++;
                this.initial = Math.max(this.initial, this.pending + this.repaired);
                this.sorted.remove(c);
            }
        }
    }
}
