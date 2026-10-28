package com.takem.titans.world;

import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Stalls {
    private static List<Stalls.Stall> stalls;

    private Stalls() {
    }

    public static synchronized List<Stalls.Stall> all() {
        if (stalls == null) {
            stalls = new ArrayList<>();
            stalls.add(new Stalls.Stall("shiganshina", 5, -57, Direction.SOUTH, false));
            stalls.add(new Stalls.Stall("trost", -15, -402, Direction.EAST, true));

            for (ShiganshinaLayout.Bulge b : ShiganshinaLayout.BULGES) {
                if (b.ring() >= 6 && b.ring() != ShiganshinaLayout.ARENA_RING) {
                    double len = Math.sqrt(b.cx() * b.cx() + (b.cz() + 1100.0) * (b.cz() + 1100.0));
                    double tx = b.cx() + b.cx() / len * b.r() * 0.45;
                    double tz = b.cz() + (b.cz() + 1100.0) / len * b.r() * 0.45;
                    TownPlan.Lot lot = TownPlan.lotAt(Mth.floor(tx), Mth.floor(tz));
                    if (lot != null) {
                        stalls.add(new Stalls.Stall(b.name(), lot.x0 + 10, lot.z0 - 4, Direction.SOUTH, true));
                    }
                }
            }
        }

        return stalls;
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 61) {
            ServerLevel level = player.serverLevel();
            if (level.dimension() == Level.OVERWORLD && TitanSpawner.isShiganshina(level)) {
                for (Stalls.Stall s : all()) {
                    double dx = (double)s.x + 0.5 - player.getX();
                    double dz = (double)s.z + 0.5 - player.getZ();
                    if (dx * dx + dz * dz < 6400.0 && level.hasChunk(s.x >> 4, s.z >> 4)) {
                        Stalls.Data d = Stalls.Data.get(level);
                        int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z);
                        if (d.built.add(s.zone)) {
                            build(level, s, y);
                            d.setDirty();
                        }

                        if (s.npc) {
                            keeper(level, s);
                        }
                    }
                }
            }
        }
    }

    private static BlockPos at(Stalls.Stall s, int y, int f, int side) {
        Direction fwd = s.facing;
        Direction right = fwd.getClockWise();
        return new BlockPos(s.x + fwd.getStepX() * f + right.getStepX() * side, y, s.z + fwd.getStepZ() * f + right.getStepZ() * side);
    }

    private static void put(ServerLevel level, BlockPos p, BlockState st) {
        level.setBlock(p, st, 3);
    }

    static void build(ServerLevel level, Stalls.Stall s, int y) {
        for (int f = -2; f <= 2; f++) {
            for (int side = -3; side <= 3; side++) {
                for (int h = 0; h <= 3; h++) {
                    put(level, at(s, y + h, f, side), Blocks.AIR.defaultBlockState());
                }

                BlockPos floor = at(s, y - 1, f, side);
                if (level.getBlockState(floor).isAir() || Math.abs(side) <= 2) {
                    put(level, floor, (f + side & 1) == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                }
            }
        }

        BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
        put(level, at(s, y, 1, -1), barrel);
        put(level, at(s, y, 1, 0), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        put(level, at(s, y, 1, 1), barrel);
        put(level, at(s, y + 1, 1, -1), Blocks.LANTERN.defaultBlockState());
        put(level, at(s, y + 1, 1, 1), Blocks.POTTED_RED_TULIP.defaultBlockState());
        put(level, at(s, y, -2, -1), barrel);
        put(level, at(s, y + 1, -2, -1), barrel);
        put(level, at(s, y, -2, 0), Blocks.BOOKSHELF.defaultBlockState());
        put(level, at(s, y + 1, -2, 0), Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        put(level, at(s, y, -2, 1), Blocks.SMITHING_TABLE.defaultBlockState());
        put(level, at(s, y + 1, -2, 1), Blocks.LANTERN.defaultBlockState());

        for (int f : new int[]{-2, 1}) {
            for (int side : new int[]{-2, 2}) {
                for (int h = 0; h < 3; h++) {
                    put(level, at(s, y + h, f, side), Blocks.DARK_OAK_FENCE.defaultBlockState());
                }
            }
        }

        for (int f = -2; f <= 2; f++) {
            for (int side = -3; side <= 3; side++) {
                boolean edge = f == 2 || Math.abs(side) == 3;
                BlockState wool = (side & 1) == 0 ? Blocks.GREEN_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
                put(level, at(s, y + (edge ? 3 : 3), f, side), wool);
            }
        }

        for (int side : new int[]{-3, 3}) {
            put(level, at(s, y + 2, 2, side), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.valueOf(true)));
        }

        put(level, at(s, y + 4, 0, 0), Blocks.DARK_OAK_FENCE.defaultBlockState());
        put(level, at(s, y + 5, 0, 0), Blocks.DARK_OAK_FENCE.defaultBlockState());
        put(level, at(s, y + 6, 0, 0), Blocks.GREEN_BANNER.defaultBlockState());
    }

    private static void keeper(ServerLevel level, Stalls.Stall s) {
        int top = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z);
        int y = top;

        for (int yy = top - 1; yy > top - 10; yy--) {
            BlockPos p = new BlockPos(s.x, yy, s.z);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir()) {
                y = yy;
                break;
            }
        }

        BlockPos home = at(s, y, 0, 0);
        List<TownsfolkEntity> found = level.getEntitiesOfClass(TownsfolkEntity.class, new AABB(home).inflate(10.0), e -> e.getRole() == 3);
        if (found.isEmpty()) {
            TownsfolkEntity npc = ModEntities.TOWNSFOLK.get().create(level);
            if (npc != null) {
                float yaw = s.facing.toYRot();
                npc.moveTo((double)home.getX() + 0.5, (double)home.getY(), (double)home.getZ() + 0.5, yaw, 0.0F);
                npc.setup(3, 1, home, false);
                npc.setYHeadRot(yaw);
                npc.setCustomName(Component.translatable("npc.takemtitans.quartermaster_of", Rebuild.zoneName(s.zone)));
                level.addFreshEntity(npc);
            }
        }
    }

    static final class Data extends SavedData {
        final Set<String> built = new HashSet<>();

        static Stalls.Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(Stalls.Data::new, Stalls.Data::load, null), "takemtitans_stalls");
        }

        private static Stalls.Data load(CompoundTag tag, Provider registries) {
            Stalls.Data d = new Stalls.Data();
            ListTag l = tag.getList("Built", 8);

            for (int i = 0; i < l.size(); i++) {
                d.built.add(l.getString(i));
            }

            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, Provider registries) {
            ListTag l = new ListTag();

            for (String s : this.built) {
                l.add(StringTag.valueOf(s));
            }

            tag.put("Built", l);
            return tag;
        }
    }

    public static record Stall(String zone, int x, int z, Direction facing, boolean npc) {
    }
}
