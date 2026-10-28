package com.takem.titans.world;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.story.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Survival waves in Valgard. Ring the bell on the fighting floor and the titans come up through the streets, more and
 * bigger each wave, a shifter at the head of every fifth. From the third wave on, boulders come over the wall and the
 * city starts to come apart around you; it is never put back while you fight, only after the run, when the builders
 * move in. The run ends when you fall or leave the district.
 */
@EventBusSubscriber(modid = "takemtitans")
public final class Waves {
    private static final String TAG = "takemtitans_wave";
    private static final String BEST = "takemtitans:best_wave";
    @Nullable
    private static Run run;

    private Waves() {
    }

    private static final class Run {
        final ServerLevel level;
        final UUID player;
        final List<UUID> titans = new ArrayList<>();
        final ServerBossEvent bar = new ServerBossEvent(Component.literal("Valgard"), BossBarColor.RED, BossBarOverlay.NOTCHED_10);
        int wave;
        int phase;
        int timer = 100;
        int total;
        int away;
        int strike = 300;

        Run(ServerLevel level, ServerPlayer p) {
            this.level = level;
            this.player = p.getUUID();
        }
    }

    public static boolean running() {
        return run != null;
    }

    private static Vec3 centre() {
        return new Vec3(ShiganshinaLayout.ARENA_X, 67.0, ShiganshinaLayout.ARENA_Z);
    }

    private static BlockPos bellPos() {
        return BlockPos.containing(ShiganshinaLayout.ARENA_X, 68.0, ShiganshinaLayout.ARENA_Z + ArenaPlan.BELL_DZ);
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer p && event.getPos().equals(bellPos())
            && event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL)) {
            start(p);
        }
    }

    public static void start(ServerPlayer p) {
        if (run != null) {
            p.displayClientMessage(Component.translatable("wave.takemtitans.running", run.wave).withStyle(ChatFormatting.RED), true);
            return;
        }

        run = new Run(p.serverLevel(), p);
        run.bar.addPlayer(p);
        run.bar.setName(Component.translatable("wave.takemtitans.title", 1));
        run.bar.setProgress(1.0F);
        Story.bannerFor(p, "chapter", Component.translatable("wave.takemtitans.arena"), Component.translatable("wave.takemtitans.countdown"));
        p.playNotifySound(ModSounds.RUMBLING_HORN.get(), SoundSource.HOSTILE, 3.0F, 0.8F);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Run r = run;
        if (r == null || event.getLevel() != r.level) {
            return;
        }

        ServerPlayer p = r.level.getServer().getPlayerList().getPlayer(r.player);
        if (p == null || !p.isAlive() || p.level() != r.level) {
            end(r, p, false);
            return;
        }

        if (p.position().distanceTo(centre()) > ShiganshinaLayout.ARENA_R + 30.0) {
            if (++r.away == 1) {
                p.displayClientMessage(Component.translatable("wave.takemtitans.come_back").withStyle(ChatFormatting.RED), true);
            }

            if (r.away > 300) {
                end(r, p, false);
                return;
            }
        } else {
            r.away = 0;
        }

        if (r.phase != 1) {
            if (--r.timer <= 0) {
                spawnWave(r, p);
            }

            return;
        }

        if (r.wave >= 3 && --r.strike <= 0) {
            r.strike = Math.max(120, 420 - r.wave * 20);
            barrage(r, p);
        }

        if (r.level.getGameTime() % 20L == 7L) {
            int alive = 0;
            for (int i = 0; i < r.titans.size(); i++) {
                if (r.level.getEntity(r.titans.get(i)) instanceof AbstractTitan t && t.isAlive()) {
                    alive++;
                    if (t.getTarget() == null) {
                        t.setTarget(p);
                    }
                } else {
                    r.titans.remove(i--);
                }
            }

            r.bar.setName(Component.translatable("wave.takemtitans.bar", r.wave, alive));
            r.bar.setProgress(r.total == 0 ? 0.0F : Mth.clamp((float)alive / r.total, 0.0F, 1.0F));
            if (alive == 0) {
                cleared(r, p);
            }
        }
    }

    private static void cleared(Run r, ServerPlayer p) {
        boolean boss = r.wave % 5 == 0;
        int badges = (1 + r.wave / 2) * (boss ? 3 : 1);
        int xp = 40 * r.wave * (boss ? 2 : 1);
        Story.give(p, new ItemStack(ModItems.MERIT_BADGE.get(), badges));
        Story.addXp(p, xp);
        if (r.wave > p.getPersistentData().getInt(BEST)) {
            p.getPersistentData().putInt(BEST, r.wave);
        }

        Story.bannerFor(p, "stage", Component.translatable("wave.takemtitans.cleared", r.wave), Component.translatable("wave.takemtitans.reward", badges, xp));
        // Valgard has no supply stall: the garrison throws a crate down to whoever holds the floor
        if (com.takem.titans.odm.OdmGear.resupply(p)) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REFILL.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            p.displayClientMessage(Component.translatable("wave.takemtitans.resupply").withStyle(ChatFormatting.AQUA), true);
        }
        p.playNotifySound(ModSounds.QUEST_COMPLETE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        r.phase = 2;
        r.timer = boss ? 300 : 200;
        r.bar.setName(Component.translatable("wave.takemtitans.next", r.wave + 1));
        r.bar.setProgress(1.0F);
    }

    /** What leads a boss wave: the shifters in turn, then two at once. */
    private static List<EntityType<? extends AbstractTitan>> bosses(int wave) {
        List<EntityType<? extends AbstractTitan>> order = List.of(
            ModEntities.FEMALE_TITAN.get(), ModEntities.CART_TITAN.get(), ModEntities.WAR_HAMMER_TITAN.get(),
            ModEntities.ARMORED_TITAN.get(), ModEntities.BEAST_TITAN.get()
        );
        int i = wave / 5 - 1;
        List<EntityType<? extends AbstractTitan>> out = new ArrayList<>();
        out.add(order.get(i % order.size()));
        if (i >= order.size()) {
            out.add(order.get((i + 2) % order.size()));
        }

        return out;
    }

    private static void spawnWave(Run r, ServerPlayer p) {
        r.wave++;
        r.phase = 1;
        r.titans.clear();
        RandomSource rnd = r.level.random;
        boolean boss = r.wave % 5 == 0;
        int n = Math.min(26, 3 + r.wave * 2 - (boss ? 2 : 0));
        List<EntityType<? extends AbstractTitan>> types = new ArrayList<>(boss ? bosses(r.wave) : List.of());
        for (int i = 0; i < n; i++) {
            int roll = rnd.nextInt(100);
            types.add(
                r.wave >= 3 && roll >= 85 ? ModEntities.GHOUL_TITAN.get()
                    : r.wave >= 8 && roll < 5 ? ModEntities.JAW_TITAN.get()
                    : (roll < 30 ? ModEntities.ABNORMAL_TITAN.get() : (roll < 45 ? ModEntities.CRAWLER_TITAN.get() : ModEntities.PURE_TITAN.get()))
            );
        }

        int leaders = types.size() - n;
        for (int i = 0; i < types.size(); i++) {
            AbstractTitan t = spawn(r.level, types.get(i), i < leaders);
            if (t != null) {
                t.addTag(TAG);
                t.addTag(NightMission.AWAKE);
                t.setTarget(p);
                t.marchTo(centre());
                if (i >= leaders && !(t instanceof com.takem.titans.entity.titan.JawTitan)) {
                    t.setTitanHeight(Math.min(15.0F, t.getTitanHeight() + r.wave * 0.45F));
                }

                r.titans.add(t.getUUID());
            }
        }

        r.total = r.titans.size();
        r.strike = 200;
        Component sub = boss
            ? Component.translatable("wave.takemtitans.boss_sub", Component.translatable(types.get(0).getDescriptionId()))
            : Component.translatable("wave.takemtitans.sub", r.total);
        Story.bannerFor(p, boss ? "chapter" : "stage", Component.translatable("wave.takemtitans.title", r.wave), sub);
        p.playNotifySound(boss ? ModSounds.BOSS_MUSIC.get() : ModSounds.TITAN_ROAR.get(), SoundSource.HOSTILE, boss ? 1.0F : 2.0F, boss ? 1.0F : 0.7F);
        r.bar.setColor(boss ? BossBarColor.PURPLE : BossBarColor.RED);
    }

    /** A titan coming up out of the streets of the outer quarters, or straight into the middle for a shifter. */
    @Nullable
    private static AbstractTitan spawn(ServerLevel level, EntityType<? extends AbstractTitan> type, boolean boss) {
        RandomSource rnd = level.random;
        for (int attempt = 0; attempt < 16; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = boss ? 78.0 + rnd.nextDouble() * 20.0 : 110.0 + rnd.nextDouble() * 105.0;
            double x = ShiganshinaLayout.ARENA_X + Math.cos(a) * d;
            double z = ShiganshinaLayout.ARENA_Z + Math.sin(a) * d;
            if (!level.isLoaded(BlockPos.containing(x, 66.0, z))) {
                continue;
            }

            // titans stand in the street, not on the roofs: only open ground will do
            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (y > 70 && attempt < 12) {
                continue;
            }

            AbstractTitan t = TitanSpawner.spawnAt(level, type, Math.floor(x) + 0.5, Math.max(y, 67), Math.floor(z) + 0.5, rnd.nextFloat() * 360.0F);
            if (t != null) {
                Fx.send(level, Fx.EMERGE, t.position(), t.getTitanHeight() * 0.5F, boss ? 1.0F : 0.5F, 256.0);
                level.sendParticles(ModParticles.BIG_STEAM.get(), t.getX(), t.getY() + 2.0, t.getZ(), 30, 2.0, 2.0, 2.0, 0.05);
                return t;
            }
        }

        return null;
    }

    /** Boulders lobbed in over the wall at the city: every wave a little more of Valgard comes down. */
    private static void barrage(Run r, ServerPlayer p) {
        ServerLevel level = r.level;
        RandomSource rnd = level.random;
        int n = 1 + r.wave / 4;
        double a0 = rnd.nextDouble() * Math.PI * 2.0;
        Vec3 from = new Vec3(ShiganshinaLayout.ARENA_X + Math.cos(a0) * 290.0, 140.0, ShiganshinaLayout.ARENA_Z + Math.sin(a0) * 290.0);
        for (int i = 0; i < n; i++) {
            double a = a0 + Math.PI + (rnd.nextDouble() - 0.5) * 1.6;
            double d = 60.0 + rnd.nextDouble() * 160.0;
            double x = ShiganshinaLayout.ARENA_X + Math.cos(a) * d;
            double z = ShiganshinaLayout.ARENA_Z + Math.sin(a) * d;
            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            BoulderBarrage.throwAt(level, from, new Vec3(x, y - 2.0, z), null, 2.0F + rnd.nextFloat() * 1.4F, 3);
        }

        if (r.wave == 3) {
            Story.bannerFor(p, "stage", Component.translatable("wave.takemtitans.barrage"), Component.translatable("wave.takemtitans.barrage_sub"));
        }
    }

    private static void end(Run r, @Nullable ServerPlayer p, boolean won) {
        run = null;
        r.bar.removeAllPlayers();
        for (UUID id : r.titans) {
            if (r.level.getEntity(id) instanceof AbstractTitan t) {
                r.level.sendParticles(ModParticles.BIG_STEAM.get(), t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 40, 2.0, 4.0, 2.0, 0.05);
                t.discard();
            }
        }

        // the builders move in and put Valgard back up for the next run
        Rebuild.hurry(r.level, "arena", 2400);
        if (p != null) {
            int best = Math.max(p.getPersistentData().getInt(BEST), r.wave - 1);
            Story.bannerFor(p, "chapter", Component.translatable("wave.takemtitans.over"), Component.translatable("wave.takemtitans.over_sub", Math.max(0, r.wave - 1), best));
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        // titans left over from a run that ended with the game closed do not stay
        if (!event.getLevel().isClientSide && run == null && event.getEntity() instanceof AbstractTitan && event.getEntity().getTags().contains(TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 100 == 41 && p.level().dimension() == Level.OVERWORLD
            && p.position().distanceTo(centre()) < 120.0) {
            ServerLevel level = p.serverLevel();
            BlockPos bell = bellPos();
            if (level.isLoaded(bell) && !level.getBlockState(bell).is(Blocks.BELL) && TitanSpawner.isShiganshina(level)) {
                level.setBlock(bell.below(), Blocks.CHISELED_SANDSTONE.defaultBlockState(), 3);
                level.setBlock(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.SOUTH), 3);
            }

            if (run == null && p.tickCount % 1200 == 41) {
                p.displayClientMessage(Component.translatable("wave.takemtitans.hint").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        run = null;
    }
}
