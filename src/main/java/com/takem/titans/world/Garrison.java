package com.takem.titans.world;

import com.takem.titans.entity.CannonEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
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

                for (double deg = 0.0; deg < 360.0; deg += step) {
                    double a = Math.toRadians(deg);
                    double cx = b.cx() + Math.cos(a) * b.r();
                    double cz = b.cz() + Math.sin(a) * b.r();
                    if (ShiganshinaLayout.ring(cx, cz) == b.ring() && Math.abs(cx) > 30.0) {
                        double ix = b.cx() + Math.cos(a) * (b.r() - 3.0);
                        double iz = b.cz() + Math.sin(a) * (b.r() - 3.0);
                        double x = b.cx() + Math.cos(a) * (b.r() + 3.5);
                        double z = b.cz() + Math.sin(a) * (b.r() + 3.5);
                        float yaw = (float)Math.toDegrees(Math.atan2(Math.sin(a), Math.cos(a))) - 90.0F;
                        spots.add(new Garrison.Spot(BlockPos.asLong(Mth.floor(ix), 0, Mth.floor(iz)), x, z, yaw, tier));
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
                        if (!d.placed.contains(s.key) && level.hasChunk(bx >> 4, bz >> 4)) {
                            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                            if (y > 100) {
                                level.addFreshEntity(CannonEntity.create(level, new Vec3(s.x, (double)y, s.z), s.yaw, s.tier, true));
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

    static record Spot(long key, double x, double z, float yaw, int tier) {
    }
}
