package com.takem.titans.world;

import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.TitanHeartEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.story.Story;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class TunnelDungeon {
    public static final double ENTRY_X = 304.0;
    public static final double ENTRY_Z = -1103.0;
    public static final int HALL = 0;
    public static final int BONES = 1;
    public static final int CRYSTAL = 2;
    public static final int MAGMA = 3;
    public static final int ENTRY = 4;
    public static final int CHAMBER = 5;
    private static final int CHUNKS_PER_TICK = 3;
    private static final int DECO_PER_TICK = 160;
    private static final Map<Long, TunnelDungeon.Layout> LAYOUTS = new HashMap<>();
    private static final Map<Long, List<TunnelDungeon.Deco>> DECOS = new HashMap<>();

    private TunnelDungeon() {
    }

    static boolean shaft(double x, double y, double z, TunnelDungeon.Room entry, double grow) {
        if (!(y < entry.y - 2.0) && !(y > 72.0)) {
            double dx = x - 304.0;
            double dz = z - -1103.0;
            double r = 4.6 + 0.8 * WorldNoise.value2(y * 0.2, Math.atan2(dz, dx) * 2.0, 4411) + grow;
            return dx * dx + dz * dz < r * r;
        } else {
            return false;
        }
    }

    public static synchronized TunnelDungeon.Layout layout(long worldSeed) {
        return LAYOUTS.computeIfAbsent(worldSeed, TunnelDungeon::generate);
    }

    public static TunnelDungeon.Layout layout(ServerLevel level) {
        return layout(level.getSeed());
    }

    private static TunnelDungeon.Layout generate(long worldSeed) {
        RandomSource r = RandomSource.create(worldSeed ^ 2054053393L);
        TunnelDungeon.Layout L = new TunnelDungeon.Layout();
        L.entry = new TunnelDungeon.Room(4, 304.0, 32.0, -1103.0, 13.0, 16.0, r.nextInt());
        L.rooms.add(L.entry);
        List<TunnelDungeon.Room> parents = new ArrayList<>();
        parents.add(L.entry);
        int target = 13 + r.nextInt(4);
        int guard = 0;

        while (L.rooms.size() < target && guard++ < 600) {
            TunnelDungeon.Room p = parents.get(r.nextInt(parents.size()));
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double dist = 38.0 + r.nextDouble() * 26.0;
            double rad = 9.0 + r.nextDouble() * 9.0;
            double h = Math.min(24.0, rad * (1.2 + r.nextDouble() * 0.5));
            double x = p.x + Math.cos(a) * dist;
            double z = p.z + Math.sin(a) * dist;
            double y = Mth.clamp(p.y + (r.nextDouble() - 0.6) * 12.0, 4.0 + h * 0.5, 50.0 - h * 0.5);
            if (Math.hypot(x - 304.0, z - -1103.0) <= 175.0 && !overlaps(L, x, z, rad)) {
                int kind = r.nextInt(4);
                TunnelDungeon.Room room = new TunnelDungeon.Room(kind, x, y, z, rad, h, r.nextInt());
                L.rooms.add(room);
                parents.add(room);
                connect(L, p, room, r);
            }
        }

        TunnelDungeon.Room far = L.entry;

        for (TunnelDungeon.Room room : L.rooms) {
            if (Math.hypot(room.x - 304.0, room.z - -1103.0) > Math.hypot(far.x - 304.0, far.z - -1103.0)) {
                far = room;
            }
        }

        double a = Math.atan2(far.z - -1103.0, far.x - 304.0);
        double cx = far.x + Math.cos(a) * 34.0;
        double cz = far.z + Math.sin(a) * 34.0;
        L.chamber = new TunnelDungeon.Room(5, cx, 22.0, cz, 30.0, 34.0, r.nextInt());
        L.rooms.add(L.chamber);
        connect(L, far, L.chamber, r);

        for (int k = 0; k < 3 && L.rooms.size() > 3; k++) {
            TunnelDungeon.Room ra = L.rooms.get(1 + r.nextInt(L.rooms.size() - 2));
            TunnelDungeon.Room best = null;

            for (TunnelDungeon.Room rb : L.rooms) {
                if (rb != ra && rb.kind != 5) {
                    double d = Math.hypot(rb.x - ra.x, rb.z - ra.z);
                    if (d > 30.0 && d < 75.0 && (best == null || d < Math.hypot(best.x - ra.x, best.z - ra.z))) {
                        best = rb;
                    }
                }
            }

            if (best != null) {
                connect(L, ra, best, r);
            }
        }

        double floor = L.chamber.floorY();

        for (int i = 0; i < 3; i++) {
            double ha = a + 6.283185307179586 * (double)i / 3.0 + 0.6;
            L.hearts.add(new Vec3(L.chamber.x + Math.cos(ha) * 15.0, floor + 2.0, L.chamber.z + Math.sin(ha) * 15.0));
        }

        Set<Long> set = new LinkedHashSet<>();
        List<AABB> boxes = new ArrayList<>();

        for (TunnelDungeon.Room roomx : L.rooms) {
            boxes.add(roomx.box());
        }

        for (TunnelDungeon.Tube t : L.tubes) {
            boxes.add(t.box());
        }

        boxes.add(new AABB(296.0, L.entry.y - 4.0, -1111.0, 312.0, 74.0, -1095.0));
        L.minY = 2147483647;
        L.maxY = -2147483648;

        for (AABB b : boxes) {
            L.minY = Math.min(L.minY, Mth.floor(b.minY));
            L.maxY = Math.max(L.maxY, Mth.ceil(b.maxY));

            for (int x = Mth.floor(b.minX) >> 4; x <= Mth.floor(b.maxX) >> 4; x++) {
                for (int z = Mth.floor(b.minZ) >> 4; z <= Mth.floor(b.maxZ) >> 4; z++) {
                    set.add(ChunkPos.asLong(x, z));
                }
            }
        }

        List<Long> sorted = new ArrayList<>(set);
        sorted.sort(
            (p, q) -> Double.compare(
                    Math.hypot((double)(ChunkPos.getX(p) << 4) - 304.0, (double)(ChunkPos.getZ(p) << 4) - -1103.0),
                    Math.hypot((double)(ChunkPos.getX(q) << 4) - 304.0, (double)(ChunkPos.getZ(q) << 4) - -1103.0)
                )
        );
        L.chunks.addAll(sorted);
        return L;
    }

    private static boolean overlaps(TunnelDungeon.Layout L, double x, double z, double rad) {
        for (TunnelDungeon.Room o : L.rooms) {
            if (Math.hypot(o.x - x, o.z - z) < o.r + rad + 8.0) {
                return true;
            }
        }

        return false;
    }

    private static void connect(TunnelDungeon.Layout L, TunnelDungeon.Room a, TunnelDungeon.Room b, RandomSource r) {
        Vec3 pa = new Vec3(a.x, a.floorY() + 0.5, a.z);
        Vec3 pb = new Vec3(b.x, b.floorY() + 0.5, b.z);
        Vec3 mid = pa.add(pb).scale(0.5);
        Vec3 dir = pb.subtract(pa);
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x).normalize();
        Vec3 c = mid.add(side.scale((r.nextDouble() - 0.5) * dir.length() * 0.6));
        L.tubes.add(new TunnelDungeon.Tube(pa, c, pb, 3.2 + r.nextDouble() * 0.8, 7.5 + r.nextDouble() * 2.0, r.nextInt()));
    }

    public static boolean built(ServerLevel level) {
        return TunnelDungeon.Data.get(level).phase >= 3;
    }

    public static void ensureBuilt(ServerLevel level) {
        TunnelDungeon.Data d = TunnelDungeon.Data.get(level);
        if (d.phase == 0) {
            d.phase = 1;
            d.chunk = 0;
            d.deco = 0;
            d.setDirty();
        }
    }

    public static void tick(ServerLevel level) {
        TunnelDungeon.Data d = TunnelDungeon.Data.get(level);
        if (d.phase == 1 || d.phase == 2) {
            TunnelDungeon.Layout L = layout(level);
            if (d.phase == 1) {
                for (int i = 0; i < 3 && d.chunk < L.chunks.size(); i++) {
                    long c = L.chunks.get(d.chunk++);
                    carveChunk(level, L, ChunkPos.getX(c), ChunkPos.getZ(c));
                }

                if (d.chunk >= L.chunks.size()) {
                    d.phase = 2;
                    d.deco = 0;
                }
            } else {
                List<TunnelDungeon.Deco> decos = decorations(level.getSeed(), L);
                int end = Math.min(decos.size(), d.deco + 160);

                for (int i = d.deco; i < end; i++) {
                    decos.get(i).place(level, L);
                }

                d.deco = end;
                if (end >= decos.size()) {
                    d.phase = 3;
                }
            }

            d.setDirty();
        }
    }

    private static void carveChunk(ServerLevel level, TunnelDungeon.Layout L, int cx, int cz) {
        level.getChunk(cx, cz);
        int x0 = cx << 4;
        int z0 = cz << 4;
        AABB area = new AABB((double)(x0 - 1), (double)L.minY, (double)(z0 - 1), (double)(x0 + 17), (double)L.maxY, (double)(z0 + 17));
        List<TunnelDungeon.Room> rooms = new ArrayList<>();
        List<TunnelDungeon.Tube> tubes = new ArrayList<>();

        for (TunnelDungeon.Room r : L.rooms) {
            if (r.box().intersects(area)) {
                rooms.add(r);
            }
        }

        for (TunnelDungeon.Tube t : L.tubes) {
            if (t.box().intersects(area)) {
                tubes.add(t);
            }
        }

        boolean hasShaft = Math.abs(304.0 - (double)(x0 + 8)) < 20.0 && Math.abs(-1103.0 - (double)(z0 + 8)) < 20.0;
        MutableBlockPos m = new MutableBlockPos();
        int bottom = Math.max(level.getMinBuildHeight() + 5, L.minY);

        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int y = bottom; y <= L.maxY; y++) {
                    double px = (double)x + 0.5;
                    double py = (double)y + 0.5;
                    double pz = (double)z + 0.5;
                    int in = -1;

                    for (TunnelDungeon.Room rx : rooms) {
                        if (rx.contains(px, py, pz, 0.0)) {
                            in = rx.kind;
                            break;
                        }
                    }

                    if (in < 0) {
                        for (TunnelDungeon.Tube tx : tubes) {
                            if (tx.contains(px, py, pz, 0.0)) {
                                in = 10;
                                break;
                            }
                        }
                    }

                    if (in < 0 && hasShaft && shaft(px, py, pz, L.entry, 0.0)) {
                        in = 11;
                    }

                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (!s.is(Blocks.BEDROCK)) {
                        if (in >= 0) {
                            if (!s.isAir()) {
                                level.setBlock(m, Blocks.AIR.defaultBlockState(), 18);
                            }
                        } else if (y < 60 && !s.isAir() && s.getFluidState().isEmpty()) {
                            int lining = -1;

                            for (TunnelDungeon.Room rxx : rooms) {
                                if (rxx.contains(px, py, pz, 1.2)) {
                                    lining = rxx.kind;
                                    break;
                                }
                            }

                            if (lining < 0) {
                                for (TunnelDungeon.Tube txx : tubes) {
                                    if (txx.contains(px, py, pz, 1.2)) {
                                        lining = 10;
                                        break;
                                    }
                                }
                            }

                            if (lining >= 0) {
                                level.setBlock(m, lining(lining, x, y, z), 18);
                            }
                        } else if (!s.getFluidState().isEmpty() && y < 60) {
                            for (TunnelDungeon.Room rxxx : rooms) {
                                if (rxxx.contains(px, py, pz, 2.0)) {
                                    level.setBlock(m, Blocks.STONE.defaultBlockState(), 18);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static BlockState lining(int kind, int x, int y, int z) {
        double n = WorldNoise.rand(x, y, z, 77);

        return switch (kind) {
            case 0, 4 -> n < 0.35
            ? Blocks.COBBLESTONE.defaultBlockState()
            : (
                n < 0.55
                    ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : (n < 0.75 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE.defaultBlockState())
            );
            case 1 -> n < 0.4
            ? Blocks.TUFF.defaultBlockState()
            : (n < 0.65 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : (n < 0.8 ? Blocks.CALCITE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState()));
            case 2 -> n < 0.45
            ? Blocks.CALCITE.defaultBlockState()
            : (n < 0.7 ? Blocks.SMOOTH_BASALT.defaultBlockState() : (n < 0.88 ? Blocks.TUFF.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState()));
            case 3 -> n < 0.4
            ? Blocks.BLACKSTONE.defaultBlockState()
            : (n < 0.7 ? Blocks.BASALT.defaultBlockState() : (n < 0.85 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState()));
            case 5 -> n < 0.35
            ? Blocks.BLACKSTONE.defaultBlockState()
            : (
                n < 0.6
                    ? Blocks.NETHER_WART_BLOCK.defaultBlockState()
                    : (
                        n < 0.8
                            ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                            : (n < 0.93 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState())
                    )
            );
            default -> n < 0.5 ? Blocks.STONE.defaultBlockState() : (n < 0.75 ? Blocks.ANDESITE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
        };
    }

    private static synchronized List<TunnelDungeon.Deco> decorations(long seed, TunnelDungeon.Layout L) {
        return DECOS.computeIfAbsent(seed, s -> buildDecos(s, L));
    }

    private static List<TunnelDungeon.Deco> buildDecos(long seed, TunnelDungeon.Layout L) {
        RandomSource r = RandomSource.create(seed ^ 1373527789L);
        List<TunnelDungeon.Deco> out = new ArrayList<>();
        out.add((level, lay) -> rim(level));
        out.add((level, lay) -> ladder(level, lay));

        for (TunnelDungeon.Room room : L.rooms) {
            int n = (int)(room.r * room.r * 0.18);

            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * 3.141592653589793 * 2.0;
                double d = Math.sqrt(r.nextDouble()) * room.r * 0.85;
                int x = Mth.floor(room.x + Math.cos(a) * d);
                int z = Mth.floor(room.z + Math.sin(a) * d);
                int pick = r.nextInt(100);
                int kind = room.kind;
                out.add((level, lay) -> roomDeco(level, room, kind, pick, x, z));
            }

            int chests = room.kind == 5 ? 0 : (room.kind == 4 ? 1 : 1 + r.nextInt(2));

            for (int i = 0; i < chests; i++) {
                double a = r.nextDouble() * 3.141592653589793 * 2.0;
                int x = Mth.floor(room.x + Math.cos(a) * room.r * 0.6);
                int z = Mth.floor(room.z + Math.sin(a) * room.r * 0.6);
                long lootSeed = r.nextLong();
                out.add((level, lay) -> chest(level, room, x, z, lootSeed));
            }

            if (room.kind == 5) {
                out.add((level, lay) -> chamber(level, room, lay));
            }
        }

        for (TunnelDungeon.Tube t : L.tubes) {
            for (int i = 3; i < t.pts.length - 3; i += 5) {
                Vec3 a = t.pts[i];
                Vec3 b = t.pts[i + 1];
                boolean lamp = i / 5 % 2 == 0;
                out.add((level, lay) -> frame(level, a, b, t, lamp));
            }
        }

        return out;
    }

    @Nullable
    private static Integer floorAt(ServerLevel level, int x, int z, double fromY, double toY) {
        MutableBlockPos m = new MutableBlockPos(x, Mth.floor(fromY), z);

        for (int y = Mth.floor(fromY); y >= Mth.floor(toY); y--) {
            m.setY(y);
            if (level.getBlockState(m).isAir()) {
                m.setY(y - 1);
                if (!level.getBlockState(m).isAir() && level.getBlockState(m).getFluidState().isEmpty()) {
                    return y;
                }
            }
        }

        return null;
    }

    @Nullable
    private static Integer ceilingAt(ServerLevel level, int x, int z, double fromY, double toY) {
        MutableBlockPos m = new MutableBlockPos(x, Mth.floor(fromY), z);

        for (int y = Mth.floor(fromY); y <= Mth.floor(toY); y++) {
            m.setY(y);
            if (level.getBlockState(m).isAir()) {
                m.setY(y + 1);
                if (!level.getBlockState(m).isAir()) {
                    return y;
                }
            }
        }

        return null;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, 18);
    }

    private static void roomDeco(ServerLevel level, TunnelDungeon.Room room, int kind, int pick, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        Integer fy = floorAt(level, x, z, room.y + room.h * 0.3, room.floorY() - 3.0);
        Integer cy = ceilingAt(level, x, z, room.y, room.y + room.h * 0.5 + 4.0);
        if (fy != null) {
            int y = fy;
            switch (kind) {
                case 0:
                case 4:
                    if (pick < 14) {
                        int h = 3 + pick % 6;

                        for (int k = 0; k < h; k++) {
                            set(
                                level,
                                x,
                                y + k,
                                z,
                                k == h - 1 && pick % 2 == 0
                                    ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                                    : (k % 3 == 1 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState())
                            );
                        }
                    } else if (pick < 26) {
                        set(level, x, y, z, Blocks.STONE_BRICK_SLAB.defaultBlockState());
                    } else if (pick < 34) {
                        set(level, x, y, z, Blocks.IRON_BARS.defaultBlockState());
                        set(level, x, y + 1, z, Blocks.IRON_BARS.defaultBlockState());
                    } else if (pick < 40) {
                        set(level, x, y, z, Blocks.SKELETON_SKULL.defaultBlockState());
                    } else if (pick < 46) {
                        set(level, x, y, z, Blocks.COBWEB.defaultBlockState());
                    }
                    break;
                case 1:
                    if (pick < 30) {
                        int h = 1 + pick % 3;

                        for (int k = 0; k < h; k++) {
                            set(level, x, y + k, z, Blocks.BONE_BLOCK.defaultBlockState());
                        }
                    } else if (pick < 40) {
                        set(level, x, y, z, Blocks.SKELETON_SKULL.defaultBlockState());
                    } else if (pick < 55) {
                        set(level, x, y, z, Blocks.COBWEB.defaultBlockState());
                    } else if (pick < 62) {
                        set(
                            level,
                            x,
                            y,
                            z,
                            Blocks.CANDLE
                                .defaultBlockState()
                                .setValue(CandleBlock.LIT, Boolean.valueOf(true))
                                .setValue(CandleBlock.CANDLES, Integer.valueOf(1 + pick % 4))
                        );
                    }
                    break;
                case 2:
                    if (pick < 25) {
                        set(level, x, y, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                    } else if (pick < 38) {
                        int h = 2 + pick % 4;

                        for (int k = 0; k < h; k++) {
                            set(level, x, y + k, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
                        }

                        set(level, x, y + h, z, Blocks.LARGE_AMETHYST_BUD.defaultBlockState());
                    } else if (pick < 46) {
                        set(
                            level,
                            x,
                            y,
                            z,
                            Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), Boolean.valueOf(true))
                        );
                    }
                    break;
                case 3:
                    if (pick < 18) {
                        set(level, x, y - 1, z, Blocks.LAVA.defaultBlockState());
                    } else if (pick < 34) {
                        int h = 2 + pick % 7;

                        for (int k = 0; k < h; k++) {
                            set(level, x, y + k, z, Blocks.BASALT.defaultBlockState());
                        }
                    } else if (pick < 44) {
                        set(level, x, y, z, Blocks.FIRE.defaultBlockState());
                        set(level, x, y - 1, z, Blocks.NETHERRACK.defaultBlockState());
                    }
                    break;
                default:
                    if (pick < 20) {
                        set(level, x, y - 1, z, Blocks.NETHER_WART_BLOCK.defaultBlockState());
                    } else if (pick < 30) {
                        set(level, x, y, z, Blocks.BONE_BLOCK.defaultBlockState());
                    } else if (pick < 38) {
                        set(level, x, y, z, Blocks.SOUL_FIRE.defaultBlockState());
                        set(level, x, y - 1, z, Blocks.SOUL_SOIL.defaultBlockState());
                    }
            }
        }

        if (cy != null && (pick + 7) % 9 == 0) {
            int len = 2 + pick % 5;

            for (int k = 0; k < len; k++) {
                set(level, x, cy - k, z, Blocks.CHAIN.defaultBlockState());
            }

            boolean soul = kind == 1 || kind == 5;
            set(level, x, cy - len, z, (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, Boolean.valueOf(true)));
        } else if (cy != null && pick % 11 == 0) {
            set(level, x, cy + 1, z, Blocks.SHROOMLIGHT.defaultBlockState());
        } else if (cy != null && kind == 2 && pick % 5 == 0) {
            set(level, x, cy, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
        }
    }

    private static void chest(ServerLevel level, TunnelDungeon.Room room, int x, int z, long lootSeed) {
        level.getChunk(x >> 4, z >> 4);
        Integer fy = floorAt(level, x, z, room.y + room.h * 0.3, room.floorY() - 3.0);
        if (fy != null) {
            BlockPos p = new BlockPos(x, fy, z);
            level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.from2DDataValue(Math.floorMod((int)lootSeed, 4))), 2);
            if (level.getBlockEntity(p) instanceof ChestBlockEntity c) {
                RandomSource r = RandomSource.create(lootSeed);
                List<ItemStack> loot = new ArrayList<>();
                loot.add(new ItemStack(ModItems.GAS_CANISTER.get(), 1 + r.nextInt(3)));
                loot.add(new ItemStack(ModItems.BLADE_PACK.get(), 1 + r.nextInt(2)));
                loot.add(new ItemStack(ModItems.MERIT_BADGE.get(), 3 + r.nextInt(8)));
                if (r.nextInt(3) == 0) {
                    loot.add(new ItemStack(ModItems.THUNDER_SPEAR.get(), 1 + r.nextInt(2)));
                }

                if (r.nextInt(4) == 0) {
                    loot.add(new ItemStack(ModItems.THERMOBARIC_CANISTER.get()));
                }

                if (r.nextInt(5) == 0) {
                    loot.add(new ItemStack(ModItems.CLUSTER_SPEAR.get()));
                }

                if (r.nextInt(4) == 0) {
                    loot.add(new ItemStack(ModItems.DEMOLITION_CHARGE.get(), 2));
                }

                if (r.nextInt(6) == 0) {
                    loot.add(new ItemStack(ModItems.TITAN_HEART_SHARD.get()));
                }

                if (r.nextInt(3) == 0) {
                    loot.add(new ItemStack(Items.BREAD, 2 + r.nextInt(4)));
                }

                if (r.nextInt(8) == 0) {
                    loot.add(new ItemStack(ModItems.ULTRAHARD_STEEL_INGOT.get(), 2 + r.nextInt(3)));
                }

                for (ItemStack st : loot) {
                    c.setItem(r.nextInt(27), st);
                }
            }
        }
    }

    private static void frame(ServerLevel level, Vec3 a, Vec3 b, TunnelDungeon.Tube t, boolean lamp) {
        int bx = Mth.floor(a.x);
        int bz = Mth.floor(a.z);
        level.getChunk(bx >> 4, bz >> 4);
        Vec3 dir = b.subtract(a);
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        if (!(side.lengthSqr() < 1.0E-6)) {
            side = side.normalize();
            Integer fy = floorAt(level, bx, bz, a.y + 3.0, a.y - 3.0);
            if (fy != null) {
                int top = fy + 5;
                double half = t.w * 0.75;

                for (int s = -1; s <= 1; s += 2) {
                    int px = Mth.floor(a.x + side.x * half * (double)s);
                    int pz = Mth.floor(a.z + side.z * half * (double)s);

                    for (int y = fy; y < top; y++) {
                        if (level.getBlockState(new BlockPos(px, y, pz)).isAir()) {
                            set(level, px, y, pz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                        }
                    }
                }

                for (double k = -half; k <= half; k += 0.5) {
                    int px = Mth.floor(a.x + side.x * k);
                    int pz = Mth.floor(a.z + side.z * k);
                    if (level.getBlockState(new BlockPos(px, top, pz)).isAir()) {
                        set(level, px, top, pz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    }
                }

                if (lamp && level.getBlockState(new BlockPos(bx, top - 1, bz)).isAir()) {
                    set(level, bx, top - 1, bz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.valueOf(true)));
                }
            }
        }
    }

    private static void rim(ServerLevel level) {
        level.getChunk(Mth.floor(304.0) >> 4, Mth.floor(-1103.0) >> 4);

        for (int i = 0; i < 72; i++) {
            double a = 6.283185307179586 * (double)i / 72.0;

            for (double d = 5.2; d <= 7.4; d += 0.7) {
                int x = Mth.floor(304.0 + Math.cos(a) * d);
                int z = Mth.floor(-1103.0 + Math.sin(a) * d);
                double n = WorldNoise.rand(x, 66, z, 9);
                set(
                    level,
                    x,
                    66,
                    z,
                    n < 0.4
                        ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                        : (n < 0.7 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState())
                );
                if (d > 7.0 && i % 3 != 0) {
                    set(level, x, 67, z, i % 9 == 1 ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            double a = 1.5707963267948966 * (double)i + 0.4;
            int x = Mth.floor(304.0 + Math.cos(a) * 3.0);
            int z = Mth.floor(-1103.0 + Math.sin(a) * 3.0);

            for (int y = 66; y > 50; y--) {
                set(level, x, y, z, Blocks.CHAIN.defaultBlockState());
            }
        }
    }

    private static void ladder(ServerLevel level, TunnelDungeon.Layout L) {
        int x = Mth.floor(304.0);
        int z = Mth.floor(-1103.0);
        MutableBlockPos m = new MutableBlockPos();

        for (int y = 67; y >= (int)L.entry.floorY(); y--) {
            for (int dx = 0; dx < 9; dx++) {
                m.set(x + dx, y, z);
                if (!level.getBlockState(m).isAir()) {
                    if (dx > 0) {
                        BlockPos at = new BlockPos(x + dx - 1, y, z);
                        if (level.getBlockState(at).isAir()) {
                            level.setBlock(at, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), 18);
                        }
                    }
                    break;
                }
            }
        }
    }

    private static void chamber(ServerLevel level, TunnelDungeon.Room room, TunnelDungeon.Layout L) {
        for (int rib = 0; rib < 6; rib++) {
            double off = -room.r * 0.6 + (double)rib * room.r * 0.24;

            for (int s = 0; s <= 40; s++) {
                double t = (double)s / 40.0 * 3.141592653589793;
                double x = room.x + off;
                double z = room.z + Math.cos(t) * room.r * 0.7;
                double y = room.floorY() + Math.sin(t) * room.h * 0.7;
                set(level, Mth.floor(x), Mth.floor(y), Mth.floor(z), Blocks.BONE_BLOCK.defaultBlockState());
                set(level, Mth.floor(x) + 1, Mth.floor(y), Mth.floor(z), Blocks.BONE_BLOCK.defaultBlockState());
            }
        }

        for (Vec3 h : L.hearts) {
            int x = Mth.floor(h.x);
            int z = Mth.floor(h.z);
            Integer fy = floorAt(level, x, z, room.y, room.floorY() - 4.0);
            int y = fy != null ? fy : Mth.floor(h.y);

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= 5) {
                        set(level, x + dx, y - 1, z + dz, Blocks.CRYING_OBSIDIAN.defaultBlockState());
                        set(level, x + dx, y, z + dz, (dx * dx + dz * dz <= 2 ? Blocks.POLISHED_BLACKSTONE : Blocks.AIR).defaultBlockState());
                    }
                }
            }
        }
    }

    public static boolean inTunnels(ServerLevel level, Vec3 p) {
        return built(level) && !(p.y > 64.0) && !(Math.hypot(p.x - 304.0, p.z - -1103.0) > 260.0) ? layout(level).inside(p, 2.0) : false;
    }

    public static void tickPlayer(ServerLevel level, ServerPlayer player, boolean story) {
        if (level.getGameTime() % 170L == (long)player.getId() % 170L && inTunnels(level, player.position())) {
            player.playNotifySound(ModSounds.TUNNEL_AMBIENCE.get(), SoundSource.AMBIENT, 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
        }

        if (level.getGameTime() % 90L == (long)player.getId() % 90L && inTunnels(level, player.position())) {
            TunnelDungeon.Layout L = layout(level);
            int cap = story ? 5 : 3;
            if (level.getEntitiesOfClass(AbstractTitan.class, player.getBoundingBox().inflate(70.0, 30.0, 70.0)).size() < cap) {
                RandomSource r = level.random;
                List<TunnelDungeon.Room> options = new ArrayList<>();

                for (TunnelDungeon.Room room : L.rooms) {
                    double d = Math.hypot(room.x - player.getX(), room.z - player.getZ());
                    if (room.kind != 5 && room.kind != 4 && d > room.r + 8.0 && d < 90.0) {
                        options.add(room);
                    }
                }

                if (!options.isEmpty()) {
                    TunnelDungeon.Room roomx = options.get(r.nextInt(options.size()));
                    spawnIn(level, roomx, r);
                }
            }
        }
    }

    @Nullable
    static AbstractTitan spawnIn(ServerLevel level, TunnelDungeon.Room room, RandomSource r) {
        boolean big = room.h >= 16.0 && r.nextInt(3) != 0;
        EntityType<? extends AbstractTitan> type = big
            ? (r.nextInt(3) == 0 ? ModEntities.ABNORMAL_TITAN.get() : ModEntities.PURE_TITAN.get())
            : ModEntities.CRAWLER_TITAN.get();
        double a = r.nextDouble() * 3.141592653589793 * 2.0;
        double d = r.nextDouble() * room.r * 0.4;
        int x = Mth.floor(room.x + Math.cos(a) * d);
        int z = Mth.floor(room.z + Math.sin(a) * d);
        Integer fy = floorAt(level, x, z, room.y, room.floorY() - 4.0);
        if (fy == null) {
            return null;
        } else {
            AbstractTitan t = TitanSpawner.spawnAt(level, type, (double)x + 0.5, (double)fy.intValue(), (double)z + 0.5, r.nextFloat() * 360.0F);
            if (t != null) {
                if (big) {
                    t.setTitanHeight((float)Math.min((double)t.getTitanHeight(), Math.max(7.0, room.h * 0.62)));
                }

                Fx.send(level, 16, t.position(), t.getTitanHeight() * 0.5F, 0.5F, 128.0);
            }

            return t;
        }
    }

    public static void ensureHearts(ServerLevel level, ServerPlayer player) {
        if (built(level)) {
            TunnelDungeon.Layout L = layout(level);
            TunnelDungeon.Room c = L.chamber;
            if (Math.hypot(player.getX() - c.x, player.getZ() - c.z) < c.r + 80.0) {
                TunnelDungeon.Data d = TunnelDungeon.Data.get(level);
                if (d.heartsDown == 7) {
                    d.heartsDown = 0;
                    d.setDirty();
                }

                List<TitanHeartEntity> alive = level.getEntitiesOfClass(TitanHeartEntity.class, c.box().inflate(10.0), hx -> !hx.isCore());

                for (int i = 0; i < L.hearts.size(); i++) {
                    int slot = i;
                    if ((d.heartsDown & 1 << i) == 0 && alive.stream().noneMatch(hx -> hx.slot == slot)) {
                        Vec3 h = L.hearts.get(i);
                        Integer fy = floorAt(level, Mth.floor(h.x), Mth.floor(h.z), c.y, c.floorY() - 4.0);
                        TitanHeartEntity heart = TitanHeartEntity.tunnel(level, new Vec3(h.x, (fy != null ? (double)fy.intValue() : h.y) + 2.5, h.z), slot);
                        level.addFreshEntity(heart);
                    }
                }
            }
        }
    }

    public static void breed(ServerLevel level, TitanHeartEntity heart) {
        TunnelDungeon.Layout L = layout(level);
        if (level.getEntitiesOfClass(AbstractTitan.class, heart.getBoundingBox().inflate(50.0, 30.0, 50.0)).size() < 5) {
            AbstractTitan t = spawnIn(level, L.chamber, level.random);
            if (t != null) {
                level.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ModSounds.HEART_BEAT.get(), SoundSource.HOSTILE, 5.0F, 0.6F);
            }
        }
    }

    public static void onHeartDestroyed(ServerLevel level, TitanHeartEntity heart) {
        TunnelDungeon.Data d = TunnelDungeon.Data.get(level);
        d.heartsDown = d.heartsDown | 1 << Math.max(0, heart.slot);
        d.setDirty();

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(heart) < 40000.0) {
                Story.onHeart(p);
            }
        }

        if (d.heartsDown == 7) {
            collapseChamber(level);
        }
    }

    private static void collapseChamber(ServerLevel level) {
        TunnelDungeon.Room c = layout(level).chamber;
        RandomSource r = level.random;
        Vec3 mid = new Vec3(c.x, c.y, c.z);
        AbstractTitan.shakeNearby(level, mid, 160.0, 1.4F, 120);
        level.playSound(null, mid.x, mid.y, mid.z, ModSounds.RUMBLING_QUAKE.get(), SoundSource.BLOCKS, 10.0F, 0.6F);
        level.playSound(null, mid.x, mid.y, mid.z, ModSounds.BUILDING_COLLAPSE.get(), SoundSource.BLOCKS, 10.0F, 0.5F);

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mid) < 22500.0) {
                p.displayClientMessage(Component.translatable("message.takemtitans.chamber_collapse").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }

        for (int i = 0; i < 22; i++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double d = Math.sqrt(r.nextDouble()) * c.r * 0.8;
            int x = Mth.floor(c.x + Math.cos(a) * d);
            int z = Mth.floor(c.z + Math.sin(a) * d);
            Integer cy = ceilingAt(level, x, z, c.y, c.y + c.h * 0.5 + 6.0);
            if (cy != null) {
                FallingSectionEntity slab = Chunks.rip(level, new BlockPos(x, cy + 2, z), 2 + r.nextInt(2), 1, 10);
                if (slab != null) {
                    slab.launch(new Vec3((r.nextDouble() - 0.5) * 0.2, -0.1, (r.nextDouble() - 0.5) * 0.2), Chunks.tumble(r, 0.15F));
                    level.addFreshEntity(slab);
                }
            }
        }
    }

    public static Vec3 chamber(ServerLevel level) {
        TunnelDungeon.Room c = layout(level).chamber;
        return new Vec3(c.x, c.y, c.z);
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            tick(level);
        }
    }

    public static final class Data extends SavedData {
        public int phase;
        public int chunk;
        public int deco;
        public int heartsDown;

        public static TunnelDungeon.Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(TunnelDungeon.Data::new, TunnelDungeon.Data::load, null), "takemtitans_tunnels");
        }

        private static TunnelDungeon.Data load(CompoundTag tag, Provider registries) {
            TunnelDungeon.Data d = new TunnelDungeon.Data();
            d.phase = tag.getInt("Phase");
            d.chunk = tag.getInt("Chunk");
            d.deco = tag.getInt("Deco");
            d.heartsDown = tag.getInt("HeartsDown");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, Provider registries) {
            tag.putInt("Phase", this.phase);
            tag.putInt("Chunk", this.chunk);
            tag.putInt("Deco", this.deco);
            tag.putInt("HeartsDown", this.heartsDown);
            return tag;
        }
    }

    interface Deco {
        void place(ServerLevel var1, TunnelDungeon.Layout var2);
    }

    public static final class Layout {
        public final List<TunnelDungeon.Room> rooms = new ArrayList<>();
        public final List<TunnelDungeon.Tube> tubes = new ArrayList<>();
        public TunnelDungeon.Room entry;
        public TunnelDungeon.Room chamber;
        public final List<Vec3> hearts = new ArrayList<>();
        public final List<Long> chunks = new ArrayList<>();
        int minY;
        int maxY;

        public boolean inside(Vec3 p, double grow) {
            for (TunnelDungeon.Room r : this.rooms) {
                if (r.contains(p.x, p.y, p.z, grow)) {
                    return true;
                }
            }

            for (TunnelDungeon.Tube t : this.tubes) {
                if (t.contains(p.x, p.y, p.z, grow)) {
                    return true;
                }
            }

            return TunnelDungeon.shaft(p.x, p.y, p.z, this.entry, grow);
        }
    }

    public static final class Room {
        public final int kind;
        public final double x;
        public final double y;
        public final double z;
        public final double r;
        public final double h;
        final int seed;

        Room(int kind, double x, double y, double z, double r, double h, int seed) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.h = h;
            this.seed = seed;
        }

        double floorY() {
            return this.y - this.h * 0.5 * 0.55;
        }

        boolean contains(double px, double py, double pz, double grow) {
            double rr = this.r + grow + 2.0;
            if (!(Math.abs(px - this.x) > rr) && !(Math.abs(pz - this.z) > rr) && !(py < this.floorY() - grow)) {
                double dx = (px - this.x) / (this.r + grow);
                double dz = (pz - this.z) / (this.r + grow);
                double dy = (py - this.y) / (this.h * 0.5 + grow);
                double n = WorldNoise.value2(px * 0.09 + py * 0.13, pz * 0.09 - py * 0.07, this.seed) - 0.5;
                return dx * dx + dz * dz + dy * dy < 1.0 + n * 0.45;
            } else {
                return false;
            }
        }

        AABB box() {
            return new AABB(
                this.x - this.r - 3.0, this.floorY() - 3.0, this.z - this.r - 3.0, this.x + this.r + 3.0, this.y + this.h * 0.5 + 4.0, this.z + this.r + 3.0
            );
        }
    }

    public static final class Tube {
        final Vec3[] pts;
        final double w;
        final double h;
        final int seed;
        private AABB cachedBox;

        Tube(Vec3 a, Vec3 c, Vec3 b, double w, double h, int seed) {
            int n = Math.max(8, (int)(a.distanceTo(b) / 1.5));
            this.pts = new Vec3[n + 1];

            for (int i = 0; i <= n; i++) {
                double t = (double)i / (double)n;
                double u = 1.0 - t;
                this.pts[i] = a.scale(u * u).add(c.scale(2.0 * u * t)).add(b.scale(t * t));
            }

            this.w = w;
            this.h = h;
            this.seed = seed;
        }

        boolean contains(double px, double py, double pz, double grow) {
            AABB bb = this.box();
            if (!(px < bb.minX) && !(px > bb.maxX) && !(py < bb.minY) && !(py > bb.maxY) && !(pz < bb.minZ) && !(pz > bb.maxZ)) {
                double best = 1.7976931348623157E308;
                double floor = 0.0;

                for (int i = 0; i < this.pts.length - 1; i++) {
                    Vec3 a = this.pts[i];
                    Vec3 b = this.pts[i + 1];
                    double vx = b.x - a.x;
                    double vz = b.z - a.z;
                    double l2 = vx * vx + vz * vz;
                    double t = l2 < 1.0E-6 ? 0.0 : Mth.clamp(((px - a.x) * vx + (pz - a.z) * vz) / l2, 0.0, 1.0);
                    double cx = a.x + vx * t;
                    double cz = a.z + vz * t;
                    double d = (px - cx) * (px - cx) + (pz - cz) * (pz - cz);
                    if (d < best) {
                        best = d;
                        floor = a.y + (b.y - a.y) * t;
                    }
                }

                double w = this.w * (0.85 + 0.3 * WorldNoise.value2(px * 0.1, pz * 0.1, this.seed)) + grow;
                double dxz = Math.sqrt(best);
                if (dxz > w) {
                    return false;
                } else {
                    double k = dxz / w;
                    return py >= floor - grow && py <= floor + this.h * (1.0 - k * k * 0.45) + grow;
                }
            } else {
                return false;
            }
        }

        AABB box() {
            if (this.cachedBox != null) {
                return this.cachedBox;
            } else {
                double x0 = 1.7976931348623157E308;
                double y0 = 1.7976931348623157E308;
                double z0 = 1.7976931348623157E308;
                double x1 = -1.7976931348623157E308;
                double y1 = -1.7976931348623157E308;
                double z1 = -1.7976931348623157E308;

                for (Vec3 p : this.pts) {
                    x0 = Math.min(x0, p.x);
                    y0 = Math.min(y0, p.y);
                    z0 = Math.min(z0, p.z);
                    x1 = Math.max(x1, p.x);
                    y1 = Math.max(y1, p.y);
                    z1 = Math.max(z1, p.z);
                }

                double m = this.w * 1.3 + 3.0;
                this.cachedBox = new AABB(x0 - m, y0 - 3.0, z0 - m, x1 + m, y1 + this.h + 4.0, z1 + m);
                return this.cachedBox;
            }
        }
    }
}
