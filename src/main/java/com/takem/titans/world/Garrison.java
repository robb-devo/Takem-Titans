package com.takem.titans.world;

import com.takem.titans.entity.CannonEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.takem.titans.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Garrison {
    private static List<Garrison.Spot> spots;

    private Garrison() {
    }

    static synchronized List<Garrison.Spot> spots() {
        if (spots == null) {
            spots = new ArrayList<>();

            for (ShiganshinaLayout.Bulge b : ShiganshinaLayout.BULGES) {
                int tier = b.zone() == 2 ? 3 : (b.ring() == 2 ? 1 : 2);
                double step = b.r() > 150.0 ? 20.0 : 32.0;

                // the arena's titans are inside its wall, everyone else's come from outside
                int side = b.ring() == ShiganshinaLayout.ARENA_RING ? -1 : 1;

                for (double deg = 0.0; deg < 360.0; deg += step) {
                    double a = Math.toRadians(deg);
                    double cx = b.cx() + Math.cos(a) * b.r();
                    double cz = b.cz() + Math.sin(a) * b.r();
                    if (ShiganshinaLayout.ring(cx, cz) == b.ring() && Math.abs(cx) > 30.0) {
                        double ix = b.cx() + Math.cos(a) * (b.r() - 3.0);
                        double iz = b.cz() + Math.sin(a) * (b.r() - 3.0);
                        float yaw = (float)Math.toDegrees(Math.atan2(Math.sin(a), Math.cos(a))) - 90.0F + (side < 0 ? 180.0F : 0.0F);
                        spots.add(new Garrison.Spot(BlockPos.asLong(Mth.floor(ix), 0, Mth.floor(iz)), cx, cz, Math.cos(a) * side, Math.sin(a) * side, yaw, tier));
                    }
                }
            }
        }

        return spots;
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 37) {
            ServerLevel level = player.serverLevel();
            if (TitanSpawner.isShiganshina(level) && level.dimension() == Level.OVERWORLD) {
                Garrison.Data d = null;

                for (Garrison.Spot s : spots()) {
                    double dx = s.x - player.getX();
                    double dz = s.z - player.getZ();
                    if (dx * dx + dz * dz < 14400.0) {
                        if (d == null) {
                            d = Garrison.Data.get(level);
                        }

                        int bx = Mth.floor(s.x);
                        int bz = Mth.floor(s.z);
                        Vec3 tip = s.at(9.5, 0.0);
                        if (!d.placed.contains(s.key) && level.hasChunk(bx >> 4, bz >> 4) && level.hasChunk(Mth.floor(tip.x) >> 4, Mth.floor(tip.z) >> 4)) {
                            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                            if (y > 100) {
                                Vec3 at = ledge(level, s, y - 1);
                                CannonEntity c = CannonEntity.create(level, at, s.yaw, s.tier, true);
                                c.mountOnLedge();
                                level.addFreshEntity(c);
                            }

                            d.placed.add(s.key);
                            d.setDirty();
                        }
                    }
                }
            }
        }
    }

    public static final class Data extends SavedData {
        final Set<Long> placed = new HashSet<>();

        static Garrison.Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(Garrison.Data::new, Garrison.Data::load, null), "takemtitans_garrison");
        }

        private static Garrison.Data load(CompoundTag tag, Provider registries) {
            Garrison.Data d = new Garrison.Data();

            for (long l : tag.getLongArray("Placed")) {
                d.placed.add(l);
            }

            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, Provider registries) {
            tag.put("Placed", new LongArrayTag(new ArrayList<>(this.placed)));
            return tag;
        }
    }

    /**
     * A gun ledge: a platform of wall stone run out three blocks past the face of the wall on the titans' side, carried
     * on a stepped corbel, with the parapet opened behind it. A gun up there shoots clear of its own wall instead of into
     * it. Returns where the gun stands.
     */
    private static Vec3 ledge(ServerLevel level, Garrison.Spot s, int floor) {
        BlockState brick = ModBlocks.WALL_STONE_BRICKS.get().defaultBlockState();
        BlockState slab = ModBlocks.WALL_STONE_SLAB.get().defaultBlockState();
        Direction toWall = Direction.getNearest(-s.nx, 0.0, -s.nz);
        BlockState corbel = ModBlocks.WALL_STONE_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, toWall).setValue(StairBlock.HALF, Half.TOP);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // sampled finer than a block so a ledge on a curved wall has no gaps
        for (double w = -2.5; w <= 2.5; w += 0.5) {
            for (double d = 4.0; d <= 9.5; d += 0.5) {
                Vec3 p = s.at(d, w);
                int x = Mth.floor(p.x);
                int z = Mth.floor(p.z);
                put(level, m.set(x, floor, z), brick);
                for (int up = 1; up <= 3; up++) {
                    put(level, m.set(x, floor + up, z), Blocks.AIR.defaultBlockState());
                }

                if (d >= 6.0 && d < 7.0) {
                    put(level, m.set(x, floor - 1, z), brick);
                    put(level, m.set(x, floor - 2, z), brick);
                    put(level, m.set(x, floor - 3, z), corbel);
                } else if (d >= 7.0 && d < 8.0) {
                    put(level, m.set(x, floor - 1, z), brick);
                    put(level, m.set(x, floor - 2, z), corbel);
                } else if (d >= 8.0 && d < 9.0) {
                    put(level, m.set(x, floor - 1, z), corbel);
                }

                if (Math.abs(w) >= 2.0 && d >= 6.5) {
                    // a low rail along the two sides; the front stays open for the barrel
                    put(level, m.set(x, floor + 1, z), slab);
                }
            }
        }

        Vec3 at = s.at(7.8, 0.0);
        return new Vec3(at.x, floor + 1.0, at.z);
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos) != state) {
            level.setBlock(pos, state, 3);
        }
    }

    /** A gun spot: (x, z) on the middle line of the wall, (nx, nz) the way the gun faces. */
    static record Spot(long key, double x, double z, double nx, double nz, float yaw, int tier) {
        /** {@code d} blocks from the middle of the wall towards the titans, {@code w} along it. */
        Vec3 at(double d, double w) {
            return new Vec3(this.x + this.nx * d - this.nz * w, 0.0, this.z + this.nz * d + this.nx * w);
        }
    }
}
