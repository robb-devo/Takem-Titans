package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.npc.JaegerEntity;
import com.takem.titans.entity.npc.TownsfolkEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TownLife {
    public static final int BLAST = 0;
    public static final int COLLAPSE = 1;
    public static final int TITAN = 2;
    public static final int EATEN = 3;

    private TownLife() {
    }

    public static void alarm(ServerLevel level, Vec3 at, double radius, int kind) {
        if (TakemConfig.TOWN_REACTIONS.get()) {
            AABB box = new AABB(at, at).inflate(radius, Math.min(radius, 48.0), radius);

            for (TownsfolkEntity t : level.getEntitiesOfClass(TownsfolkEntity.class, box)) {
                double d = t.position().distanceTo(at);
                if (d < radius) {
                    t.react(at, kind, d);
                }
            }
        }
    }

    public static void titanDown(ServerLevel level, Vec3 at, ServerPlayer hero) {
        if (TakemConfig.TOWN_REACTIONS.get()) {
            int cheered = 0;

            for (TownsfolkEntity t : level.getEntitiesOfClass(TownsfolkEntity.class, new AABB(at, at).inflate(56.0, 32.0, 56.0))) {
                if (t.hasLineOfSight(hero) || t.distanceTo(hero) < 24.0F) {
                    t.cheer(hero, cheered < 3);
                    cheered++;
                }
            }

            if (cheered > 4) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 2.0F, 1.0F);
            }
        }
    }

    public static void rally(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(24.0, 12.0, 24.0);

        for (TownsfolkEntity t : level.getEntitiesOfClass(TownsfolkEntity.class, box)) {
            t.heal(6.0F);
            level.sendParticles(ParticleTypes.HEART, t.getX(), t.getY() + 2.0, t.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
        }

        for (JaegerEntity j : level.getEntitiesOfClass(JaegerEntity.class, box)) {
            j.heal(10.0F);
            level.sendParticles(ParticleTypes.HEART, j.getX(), j.getY() + 2.0, j.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
        }

        player.heal(4.0F);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.2F, 1.4F);
    }
}
