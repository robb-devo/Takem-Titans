package com.takem.titans.item;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.ArmoredTitan;
import com.takem.titans.entity.titan.BeastTitan;
import com.takem.titans.entity.titan.ColossalTitan;
import com.takem.titans.entity.titan.RavagerTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanSkeleton;
import com.takem.titans.entity.titan.WallTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModSounds;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What makes the Founder's Edge more than a sharper blade: a nape kill calls down the Coordinate on the titans around
 * it. Each one nearby is struck at the nape by lightning, and the ones that die from it call it down again (three
 * echoes deep, so a crowded street can go in one cut). Bosses only take a piece of it.
 */
public final class FounderEdge {
    private static final double RANGE = 42.0;
    private static int depth;

    private FounderEdge() {
    }

    public static boolean holds(ServerPlayer p) {
        ItemStack hand = p.getMainHandItem();
        return hand.getItem() instanceof BladeItem b && b.tier == BladeTier.FOUNDER && !BladeItem.isBroken(hand);
    }

    public static void onKill(ServerPlayer p, AbstractTitan dead) {
        if (depth >= 3 || !holds(p) || !(p.level() instanceof ServerLevel level)) {
            return;
        }

        List<AbstractTitan> near = level.getEntitiesOfClass(
            AbstractTitan.class, dead.getBoundingBox().inflate(RANGE), t -> t != dead && t.isAlive() && !t.isDummy() && !(t instanceof RumblingTitan)
        );
        if (near.isEmpty()) {
            return;
        }

        near.sort(Comparator.comparingDouble(t -> t.distanceToSqr(dead)));
        Vec3 from = dead.skeletonPoint(3, TitanSkeleton.NAPE);
        Fx.send(level, 41, from, 18.0F + depth * 6.0F, 0.0F, 256.0);
        AbstractTitan.shakeNearby(level, from, 60.0, 0.9F, 18);
        level.playSound(null, from.x, from.y, from.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0F, 0.6F + depth * 0.1F);
        depth++;
        try {
            if (depth == 1) {
                p.displayClientMessage(Component.translatable("message.takemtitans.coordinate").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
            }

            for (AbstractTitan t : near.subList(0, Math.min(5, near.size()))) {
                Vec3 nape = t.skeletonPoint(3, TitanSkeleton.NAPE);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(nape.x, nape.y - 2.0, nape.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }

                Fx.send(level, 40, from, (float)nape.x, (float)nape.y, (float)nape.z, 0.0F, 256.0);
                Fx.send(level, 11, nape, 40.0F, 0.0F, 256.0);
                level.playSound(null, nape.x, nape.y, nape.z, ModSounds.NAPE_THUNDER.get(), SoundSource.PLAYERS, 5.0F, 0.8F + depth * 0.15F);
                boolean boss = t instanceof ArmoredTitan || t instanceof BeastTitan || t instanceof RavagerTitan || t instanceof WallTitan
                    || t instanceof ColossalTitan;
                t.traumaHit(t.maxNapeHealth() * (boss ? 0.2F : 0.75F), p, p.damageSources().playerAttack(p), !boss);
            }
        } finally {
            depth--;
        }
    }
}
