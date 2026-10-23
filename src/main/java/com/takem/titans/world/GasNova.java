package com.takem.titans.world;

import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class GasNova {
    public static final int COST = 320;
    private static final List<GasNova.Delayed> QUEUE = new ArrayList<>();

    private GasNova() {
    }

    public static void trigger(ServerPlayer player, OdmPlayerData d) {
        if (!Skills.has(player, Skill.GAS_NOVA)) {
            player.displayClientMessage(Component.translatable("message.takemtitans.nova_locked"), true);
        } else if (d.novaCooldown > 0) {
            player.displayClientMessage(Component.translatable("message.takemtitans.nova_cooldown", (d.novaCooldown + 19) / 20), true);
        } else {
            ItemStack gear = OdmGear.getGear(player);
            boolean free = player.getAbilities().instabuild || d.overdrive();
            if (gear.isEmpty()) {
                return;
            }

            int gas = OdmGear.getGas(gear);
            if (!free && gas < 320) {
                player.displayClientMessage(Component.translatable("message.takemtitans.out_of_gas"), true);
                return;
            }

            boolean supernova = Skills.has(player, Skill.SUPERNOVA) && (free || (double)gas >= (double)OdmGear.maxGas(gear) * 0.6);
            int volatility = Skills.rank(player, Skill.VOLATILE_MIX);
            double demo = Skills.demolition(player);
            ServerLevel level = player.serverLevel();
            Vec3 c = player.position().add(0.0, 1.0, 0.0);
            if (!free) {
                OdmGear.setGas(gear, supernova ? gas / 4 : gas - 320);
            }

            d.novaCooldown = supernova ? 400 : 140;
            if (Skills.has(player, Skill.INFERNO)) {
                d.novaCooldown /= 2;
            }

            PacketDistributor.sendToPlayer(player, FeedbackPayload.of(17, d.novaCooldown, 1, 0));
            d.immuneTicks = Math.max(d.immuneTicks, 40);
            d.ticksSinceOdmUse = 0;
            Boom.Spec s = new Boom.Spec();
            if (supernova) {
                s.radius(24.0).blocks(15.0 * demo, 6.0F).damage(70.0F, 420.0F).fire(70).fx(27, 24.0F, 1.0F).owner(player, player);
                s.knock = 3.2;
                s.volume = 16.0F;
                s.shake = 1.6F;
                KillCam.slowMo(level.getServer(), 4.0F, 700L, 700L);
                PacketDistributor.sendToPlayer(player, FeedbackPayload.of(10, 100, 200, 0));
                level.playSound(null, c.x, c.y, c.z, ModSounds.RIFT_DETONATE.get(), SoundSource.PLAYERS, 12.0F, 0.6F);
                level.playSound(null, c.x, c.y, c.z, ModSounds.COLOSSAL_STEAM.get(), SoundSource.PLAYERS, 10.0F, 0.7F);
                TownLife.alarm(level, c, 140.0, 0);
            } else {
                double r = 6.5 + 2.5 * (double)volatility;
                s.radius(r)
                    .blocks(r * 0.55 * demo, 3.0F)
                    .damage(22.0F * (1.0F + 0.3F * (float)volatility), 60.0F * (1.0F + 0.35F * (float)volatility))
                    .fire(12)
                    .fx(27, (float)r, 0.0F)
                    .owner(player, player);
                s.knock = 2.0;
                s.volume = 7.0F;
            }

            level.playSound(null, c.x, c.y, c.z, ModSounds.AFTERBURNER.get(), SoundSource.PLAYERS, 4.0F, 0.6F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 3.0F, 0.5F);
            Boom.explode(level, c, s);
            PacketDistributor.sendToPlayer(player, FeedbackPayload.of(15, supernova ? 260 : 140, 0, 0));
            if (Skills.has(player, Skill.CHAIN_REACTION)) {
                RandomSource rnd = level.random;
                int n = supernova ? 10 : 6;
                double ring = s.radius * 1.15;

                for (int i = 0; i < n; i++) {
                    double a = (double)i / (double)n * 3.141592653589793 * 2.0 + rnd.nextDouble() * 0.4;
                    Vec3 p = c.add(Math.cos(a) * ring, -0.5 + rnd.nextDouble() * 2.0, Math.sin(a) * ring);
                    QUEUE.add(new GasNova.Delayed(level, p, 5 + i * 2 + rnd.nextInt(4), player.getUUID(), supernova ? 7.0 : 4.5, demo));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && !QUEUE.isEmpty()) {
            Iterator<GasNova.Delayed> it = QUEUE.iterator();
            List<GasNova.Delayed> fire = new ArrayList<>();

            while (it.hasNext()) {
                GasNova.Delayed q = it.next();
                if (q.level == level && --q.ticks <= 0) {
                    it.remove();
                    fire.add(q);
                }
            }

            for (GasNova.Delayed q : fire) {
                ServerPlayer owner = level.getPlayerByUUID(q.owner) instanceof ServerPlayer sp ? sp : null;
                Boom.Spec s = new Boom.Spec()
                    .radius(q.radius)
                    .blocks(q.radius * 0.6 * q.demo, 3.0F)
                    .damage(16.0F, 40.0F)
                    .fire(4)
                    .fx(27, (float)q.radius, 0.0F)
                    .owner(owner, owner);
                s.volume = 4.0F;
                s.knock = 1.4;
                s.shake = 0.5F;
                Boom.explode(level, q.at, s);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
    }

    static final class Delayed {
        final ServerLevel level;
        final Vec3 at;
        int ticks;
        final UUID owner;
        final double radius;
        final double demo;

        Delayed(ServerLevel level, Vec3 at, int ticks, UUID owner, double radius, double demo) {
            this.level = level;
            this.at = at;
            this.ticks = ticks;
            this.owner = owner;
            this.radius = radius;
            this.demo = demo;
        }
    }
}
