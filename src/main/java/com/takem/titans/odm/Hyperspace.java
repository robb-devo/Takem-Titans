package com.takem.titans.odm;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.item.GearTier;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Destruction;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The tricks of the late rigs. Phantom and Event Horizon gear phase you out of the world for the length of a dash; the
 * Event Horizon rig also folds space and puts you up to {@link #RANGE} blocks further along your line of sight, cutting
 * through whatever stood in between and throwing back everything at the far end.
 */
public final class Hyperspace {
    public static final double RANGE = 28.0;
    public static final int COOLDOWN = 60;
    public static final int GAS = 150;
    /** Ticks a phase dash keeps you out of reach. */
    private static final int PHASE_TICKS = 9;
    private static final double SHOCK_RADIUS = 9.0;

    private Hyperspace() {
    }

    public static void phase(ServerPlayer player, OdmPlayerData d) {
        if (OdmGear.tier(player).phases()) {
            d.immuneTicks = Math.max(d.immuneTicks, PHASE_TICKS);
            // everyone else sees the afterimages too
            Fx.send(player.serverLevel(), Fx.PHASE, player.getX(), player.getY(), player.getZ(), (float)player.getId(), (float)PHASE_TICKS, 0.0F, 0.0F, 128.0);
        }
    }

    public static void jump(ServerPlayer player, OdmPlayerData d) {
        ItemStack gear = OdmGear.getGear(player);
        if (OdmGear.tier(gear) == GearTier.EVENT_HORIZON && d.warpCooldown <= 0 && !player.isPassenger()) {
            boolean free = player.getAbilities().instabuild || d.overdrive();
            if (free || OdmGear.getGas(gear) >= GAS) {
                ServerLevel level = player.serverLevel();
                Vec3 dir = player.getLookAngle();
                // slide the player's own box along the line of sight until something solid is in the way
                AABB box = player.getBoundingBox();
                double reach = 0.0;

                for (double t = 0.5; t <= RANGE; t += 0.5) {
                    if (!level.noCollision(player, box.move(dir.scale(t)))) {
                        break;
                    }

                    reach = t;
                }

                if (!(reach < 2.0)) {
                    Vec3 shift = dir.scale(reach);
                    Vec3 from = player.position();
                    Vec3 to = from.add(shift);
                    if (!free) {
                        OdmGear.setGas(gear, OdmGear.getGas(gear) - GAS);
                    }

                    d.warpCooldown = COOLDOWN;
                    d.immuneTicks = Math.max(d.immuneTicks, 14);
                    PacketDistributor.sendToPlayer(player, FeedbackPayload.of(17, COOLDOWN, 2, 0));
                    Vec3 a = from.add(0.0, 0.9, 0.0);
                    Vec3 b = to.add(0.0, 0.9, 0.0);
                    cut(level, player, a, b);
                    // absolute target, sent to the client as a relative move so the swing carries straight on out of the jump
                    player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot(), EnumSet.allOf(RelativeMovement.class));
                    player.resetFallDistance();
                    Fx.send(level, Fx.HYPERSPACE, a.x, a.y, a.z, (float)b.x, (float)b.y, (float)b.z, (float)player.getId(), 320.0);
                    level.playSound(null, a.x, a.y, a.z, ModSounds.RIFT_IMPLODE.get(), SoundSource.PLAYERS, 3.0F, 1.5F);
                    level.playSound(null, b.x, b.y, b.z, ModSounds.RIFT_DETONATE.get(), SoundSource.PLAYERS, 4.0F, 1.25F);
                    level.playSound(null, b.x, b.y, b.z, ModSounds.SONIC_BOOM.get(), SoundSource.PLAYERS, 4.0F, 1.4F);
                    shockwave(level, player, b);
                    if (TakemConfig.SONIC_BOOM_BREAKS_GLASS.get()) {
                        Destruction.glassBurst(level, b, 11.0);
                        Destruction.glassBurst(level, a.lerp(b, 0.5), 6.0);
                    }
                }
            }
        }
    }

    /** Titans the tunnel passes through are cut open along it. */
    private static void cut(ServerLevel level, ServerPlayer player, Vec3 a, Vec3 b) {
        DamageSource src = level.damageSources().playerAttack(player);
        Set<Entity> hit = new HashSet<>();

        for (Entity e : level.getEntities(player, new AABB(a, b).inflate(2.0), Entity::isAlive)) {
            Entity target = e instanceof TitanPart part ? part.getParent() : e;
            if (!hit.contains(target) && e.getBoundingBox().inflate(0.8).clip(a, b).isPresent()) {
                hit.add(target);
                if (target instanceof AbstractTitan t) {
                    if (!(t instanceof RumblingTitan)) {
                        t.hurt(src, 45.0F);
                    }
                } else if (target instanceof LivingEntity l && !(l instanceof Player)) {
                    l.hurt(src, 14.0F);
                }
            }
        }
    }

    /** Space snaps shut behind you: everything around the far end is thrown back, falling rubble included. */
    private static void shockwave(ServerLevel level, ServerPlayer player, Vec3 at) {
        DamageSource src = level.damageSources().playerAttack(player);

        for (Entity e : level.getEntities(player, new AABB(at, at).inflate(SHOCK_RADIUS), Entity::isAlive)) {
            if (!(e instanceof TitanPart)) {
                Vec3 c = e instanceof FallingSectionEntity ? e.position() : e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0);
                double dist = c.distanceTo(at);
                if (!(dist > SHOCK_RADIUS)) {
                    double f = 1.0 - dist / SHOCK_RADIUS;
                    Vec3 out = dist < 0.01 ? new Vec3(0.0, 1.0, 0.0) : c.subtract(at).scale(1.0 / dist);
                    if (e instanceof FallingSectionEntity fs) {
                        fs.shove(out.scale(0.9 * f).add(0.0, 0.25 * f, 0.0));
                    } else if (e instanceof DebrisEntity) {
                        e.setDeltaMovement(e.getDeltaMovement().add(out.scale(1.2 * f)).add(0.0, 0.3 * f, 0.0));
                    } else if (e instanceof AbstractTitan t) {
                        if (!(t instanceof RumblingTitan)) {
                            t.hurt(src, (float)(25.0 * f));
                        }
                    } else {
                        e.setDeltaMovement(e.getDeltaMovement().add(out.scale(1.6 * f)).add(0.0, 0.55 * f, 0.0));
                        e.hurtMarked = true;
                        if (e instanceof LivingEntity l && !(e instanceof Player p && p.isCreative())) {
                            l.hurt(src, (float)(8.0 * f));
                        }
                    }
                }
            }
        }
    }
}
