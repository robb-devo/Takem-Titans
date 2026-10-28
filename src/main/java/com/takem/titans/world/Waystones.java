package com.takem.titans.world;

import com.takem.titans.fx.Fx;
import com.takem.titans.network.StoryPayload;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModBlocks;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Waystones: one obelisk per district, standing beside its quartermaster (in Mitras on the high street, in Valgard at
 * the edge of the arena). Each is raised the first time someone comes near, in old worlds as well as new ones. Touch
 * a waystone to wake it; from then on any waystone can send you there. The journey gathers for a second and a half,
 * folds you away in a column of light and sets you down at the far stone.
 */
@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Waystones {
    private static final int GATHER = 30;
    private static final int COOLDOWN = 200;
    private static List<Waystones.Stone> stones;

    private Waystones() {
    }

    public static synchronized List<Waystones.Stone> all() {
        if (stones == null) {
            stones = new ArrayList<>();

            for (Stalls.Stall s : Stalls.all()) {
                Direction right = s.facing().getClockWise();
                stones.add(new Waystones.Stone(s.zone(), s.x() + right.getStepX() * 6, s.z() + right.getStepZ() * 6));
            }

            stones.add(new Waystones.Stone("mitras", 7, -880));
            stones.add(new Waystones.Stone("arena", (int)ShiganshinaLayout.ARENA_X, (int)ShiganshinaLayout.ARENA_Z + ArenaPlan.WAYSTONE_DZ));
        }

        return stones;
    }

    @Nullable
    private static Waystones.Stone nearest(double x, double z, double within) {
        Waystones.Stone best = null;
        double bd = within * within;

        for (Waystones.Stone s : all()) {
            double dx = (double)s.x + 0.5 - x;
            double dz = (double)s.z + 0.5 - z;
            if (dx * dx + dz * dz <= bd) {
                bd = dx * dx + dz * dz;
                best = s;
            }
        }

        return best;
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            if (player.tickCount % 100 == 37 && level.dimension() == Level.OVERWORLD && TitanSpawner.isShiganshina(level)) {
                Waystones.Data d = Waystones.Data.get(level);

                for (Waystones.Stone s : all()) {
                    double dx = (double)s.x + 0.5 - player.getX();
                    double dz = (double)s.z + 0.5 - player.getZ();
                    if (dx * dx + dz * dz < 6400.0 && level.hasChunk(s.x >> 4, s.z >> 4) && d.placed.add(s.zone)) {
                        raise(level, s);
                        d.setDirty();
                    }
                }
            }

            OdmPlayerData od = OdmServer.data(player);
            if (od.travelTo >= 0 && level.getGameTime() >= od.travelAt) {
                depart(player, od);
            }
        }
    }

    /** A dark stone platform with the obelisk on it, set into whatever ground is there. */
    private static void raise(ServerLevel level, Waystones.Stone s) {
        int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z);
        BlockPos base = new BlockPos(s.x, y, s.z);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos f = base.offset(dx, -1, dz);
                level.setBlock(f, (dx == 0 || dz == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState(), 3);
                for (int h = 0; h < 3; h++) {
                    if (!(dx == 0 && dz == 0 && h == 0)) {
                        BlockPos a = base.offset(dx, h, dz);
                        if (!level.getBlockState(a).isAir() && level.getBlockState(a).canBeReplaced()) {
                            level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        level.setBlock(base, ModBlocks.WAYSTONE.get().defaultBlockState(), 3);
    }

    private static boolean nearWaystone(ServerPlayer p) {
        BlockPos c = p.blockPosition();

        for (BlockPos q : BlockPos.betweenClosed(c.offset(-5, -3, -5), c.offset(5, 3, 5))) {
            if (p.level().getBlockState(q).is(ModBlocks.WAYSTONE.get())) {
                return true;
            }
        }

        return false;
    }

    public static void use(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        Waystones.Stone here = nearest((double)pos.getX() + 0.5, (double)pos.getZ() + 0.5, 6.0);
        OdmPlayerData d = OdmServer.data(p);
        if (here != null && d.waystones.add(here.zone)) {
            Vec3 c = Vec3.atCenterOf(pos);
            Fx.send(level, Fx.WAYSTONE_WAKE, c, 0.0F, 0.0F, 160.0);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.2F);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.8F);
            p.sendSystemMessage(
                Component.translatable("message.takemtitans.waystone_woken", Component.translatable("zone.takemtitans." + here.zone)).withStyle(ChatFormatting.LIGHT_PURPLE)
            );
        }

        open(p, here);
    }

    private static void open(ServerPlayer p, @Nullable Waystones.Stone here) {
        OdmPlayerData d = OdmServer.data(p);
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "waystones");
        tag.putString("here", here == null ? "" : here.zone);
        ListTag list = new ListTag();

        for (Waystones.Stone s : all()) {
            CompoundTag e = new CompoundTag();
            e.putString("n", s.zone);
            e.putInt("x", s.x);
            e.putInt("z", s.z);
            e.putBoolean("on", d.waystones.contains(s.zone));
            list.add(e);
        }

        tag.put("list", list);
        PacketDistributor.sendToPlayer(p, new StoryPayload(tag));
    }

    /** Asked for by the map screen: start gathering for the journey to waystone {@code index}. */
    public static void travel(ServerPlayer p, OdmPlayerData d, int index) {
        List<Waystones.Stone> all = all();
        if (index >= 0 && index < all.size() && d.travelTo < 0) {
            Waystones.Stone to = all.get(index);
            ServerLevel level = p.serverLevel();
            long now = level.getGameTime();
            if (!d.waystones.contains(to.zone)) {
                return;
            } else if (!nearWaystone(p)) {
                p.displayClientMessage(Component.translatable("message.takemtitans.waystone_far").withStyle(ChatFormatting.GRAY), true);
            } else if (now < d.travelReadyAt) {
                p.displayClientMessage(Component.translatable("message.takemtitans.waystone_busy").withStyle(ChatFormatting.GRAY), true);
            } else {
                d.travelTo = index;
                d.travelAt = now + (long)GATHER;
                d.travelReadyAt = now + (long)COOLDOWN;
                Fx.send(level, Fx.TRAVEL_CHARGE, p.position(), (float)GATHER, (float)p.getId(), 128.0);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.8F);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.RIFT_OPEN.get(), SoundSource.PLAYERS, 1.2F, 1.4F);
            }
        }
    }

    private static void depart(ServerPlayer p, OdmPlayerData d) {
        Waystones.Stone to = all().get(d.travelTo);
        d.travelTo = -1;
        ServerLevel level = p.serverLevel();
        // load the far side first, so there's ground to stand on when we get there
        level.getChunk(to.x >> 4, to.z >> 4);
        int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, to.x + 2, to.z);
        Vec3 from = p.position();
        Vec3 dest = new Vec3((double)to.x + 2.5, (double)y, (double)to.z + 0.5);
        Fx.send(level, Fx.TRAVEL_OUT, from.x, from.y, from.z, (float)dest.x, (float)dest.y, (float)dest.z, (float)p.getId(), 160.0);
        level.playSound(null, from.x, from.y, from.z, ModSounds.RIFT_IMPLODE.get(), SoundSource.PLAYERS, 2.0F, 1.3F);
        p.teleportTo(level, dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        d.immuneTicks = Math.max(d.immuneTicks, 40);
        Fx.send(level, Fx.TRAVEL_IN, dest, (float)p.getId(), 0.0F, 256.0);
        level.playSound(null, dest.x, dest.y, dest.z, ModSounds.RIFT_DETONATE.get(), SoundSource.PLAYERS, 2.5F, 1.4F);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F, 1.6F);
    }

    public static record Stone(String zone, int x, int z) {
    }

    static final class Data extends SavedData {
        final Set<String> placed = new HashSet<>();

        static Waystones.Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(Waystones.Data::new, Waystones.Data::load, null), "takemtitans_waystones");
        }

        private static Waystones.Data load(CompoundTag tag, Provider registries) {
            Waystones.Data d = new Waystones.Data();
            ListTag l = tag.getList("Placed", 8);

            for (int i = 0; i < l.size(); i++) {
                d.placed.add(l.getString(i));
            }

            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, Provider registries) {
            ListTag l = new ListTag();

            for (String s : this.placed) {
                l.add(StringTag.valueOf(s));
            }

            tag.put("Placed", l);
            return tag;
        }
    }
}
