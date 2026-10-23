package com.takem.titans.client;

import com.takem.titans.client.fx.WorldFx;
import com.takem.titans.entity.SlashWaveEntity;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.BladeTier;
import com.takem.titans.item.GearTier;
import com.takem.titans.network.OdmActionPayload;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class GodGearClient {
    private static final double BOOM_SPEED = 3.6;
    private static final int TAP_TICKS = 6;
    private static int attackHeld;
    private static boolean supersonic;
    private static int lastRoll;
    private static boolean clicked;

    private GodGearClient() {
    }

    @SubscribeEvent
    public static void onClick(InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            clicked = true;
        }
    }

    public static void init() {
        SlashWaveEntity.CLIENT_TICK = GodGearClient::waveTick;
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && mc.level != null && !mc.isPaused()) {
            RandomSource rnd;
            boolean down;
            boolean var10000;
            label106: {
                rnd = p.getRandom();
                down = mc.screen == null && mc.options.keyAttack.isDown();
                ItemStack hand = p.getMainHandItem();
                if (hand.getItem() instanceof BladeItem b && b.tier.waves() && !BladeItem.isBroken(hand)) {
                    var10000 = true;
                    break label106;
                }

                var10000 = false;
            }

            ItemStack held = p.getMainHandItem();
            if (held.getItem() instanceof BladeItem fb && fb.tier == BladeTier.FOUNDER && !BladeItem.isBroken(held)) {
                founderAura(p, rnd);
            }

            if (down) {
                attackHeld++;
            } else {
                if ((attackHeld > 0 && attackHeld <= 6 || clicked && attackHeld == 0) && var10000) {
                    int roll = (lastRoll > 0 ? -1 : 1) * (15 + rnd.nextInt(55));
                    if (rnd.nextInt(5) == 0) {
                        roll = 90;
                    }

                    lastRoll = roll;
                    PacketDistributor.sendToServer(new OdmActionPayload(7, roll + 180));
                }

                attackHeld = 0;
            }

            clicked = false;
            ClientOdm odm = ClientOdm.INSTANCE;
            boolean god = OdmGear.tier(p) == GearTier.GODSPEED;
            if (god && odm.inFlight() && odm.speed > 3.6 && !supersonic) {
                supersonic = true;
                PacketDistributor.sendToServer(new OdmActionPayload(6, 0));
            } else if (supersonic && odm.speed < 2.52) {
                supersonic = false;
            }

            if (god && odm.boosting) {
                afterburner(p, odm.velocity, supersonic, rnd);
            }

            for (Player other : mc.level.players()) {
                if (other != p && !(other.distanceToSqr(p) > 9216.0) && RemoteHooks.isBoosting(other.getId()) && OdmGear.tier(other) == GearTier.GODSPEED) {
                    afterburner(other, other.getDeltaMovement(), other.getDeltaMovement().length() > 3.6, rnd);
                }
            }
        } else {
            attackHeld = 0;
            supersonic = false;
        }
    }

    private static void afterburner(Player p, Vec3 vel, boolean hot, RandomSource rnd) {
        Minecraft mc = Minecraft.getInstance();
        float yaw = p.yBodyRot * 0.017453292F;
        Vec3 back = new Vec3(Math.sin((double)yaw), 0.0, -Math.cos((double)yaw)).scale(0.45);
        Vec3 side = new Vec3(Math.cos((double)yaw), 0.0, Math.sin((double)yaw)).scale(0.28);
        Vec3 base = p.position().add(0.0, 0.8, 0.0).add(back);
        Vec3 drift = vel.scale(-0.35);

        for (int s = -1; s <= 1; s += 2) {
            Vec3 nozzle = base.add(side.scale((double)s));

            for (int i = 0; i < (hot ? 3 : 2); i++) {
                Vec3 v = drift.add((rnd.nextDouble() - 0.5) * 0.08, -0.05 - rnd.nextDouble() * 0.05, (rnd.nextDouble() - 0.5) * 0.08);
                mc.level.addParticle(ModParticles.FIREBALL.get(), nozzle.x, nozzle.y, nozzle.z, v.x, v.y, v.z);
            }

            if (rnd.nextInt(2) == 0) {
                mc.level.addParticle(ModParticles.EMBER.get(), nozzle.x, nozzle.y, nozzle.z, drift.x * 1.5, drift.y * 1.5 - 0.05, drift.z * 1.5);
            }

            if (hot && rnd.nextInt(2) == 0) {
                mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, nozzle.x, nozzle.y, nozzle.z, drift.x, drift.y, drift.z);
            }
        }

        if (hot && p.tickCount % 2 == 0) {
            Vec3 at = p.position().add(0.0, 0.9, 0.0);
            Vec3 tail = at.subtract(vel.normalize().scale(Math.min(6.0, vel.length() * 1.6)));
            WorldFx.add(new WorldFx.RushTrail(tail, at, 1.4F, 16747050));
        }
    }

    private static final int VIOLET = 11560703;

    private static Vec3 arc(Vec3 c, Vec3 dir, Vec3 edge, float half, RandomSource rnd) {
        double f = rnd.nextDouble() * 2.0 - 1.0;
        return c.add(edge.scale(f * (double)half)).add(dir.scale((1.0 - f * f) * (double)half * 0.32));
    }

    // a quiet storm around whoever carries the Founder's Edge
    private static void founderAura(LocalPlayer p, RandomSource rnd) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 at = p.position();
        if (p.tickCount % 2 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = 0.6 + rnd.nextDouble() * 0.5;
            mc.level.addParticle(ParticleTypes.WITCH, at.x + Math.cos(a) * r, at.y + 0.1 + rnd.nextDouble() * 1.4, at.z + Math.sin(a) * r, 0.0, 0.02, 0.0);
        }

        if (rnd.nextInt(6) == 0) {
            mc.level.addParticle(ParticleTypes.END_ROD, at.x + (rnd.nextDouble() - 0.5) * 1.6, at.y + 0.2, at.z + (rnd.nextDouble() - 0.5) * 1.6, 0.0, 0.04, 0.0);
        }

        if (rnd.nextInt(45) == 0) {
            Vec3 hand = at.add(p.getLookAngle().yRot(-0.9F).multiply(0.7, 0.0, 0.7)).add(0.0, 0.9, 0.0);
            Vec3 ground = at.add((rnd.nextDouble() - 0.5) * 2.4, 0.0, (rnd.nextDouble() - 0.5) * 2.4);
            WorldFx.add(new WorldFx.Bolt(hand, ground, 0.04F, VIOLET, rnd));
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, ground.x, ground.y + 0.1, ground.z, 0.0, 0.1, 0.0);
        }
    }

    private static void waveTick(SlashWaveEntity w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Vec3 c = w.position();
            Vec3 dir = w.dir();
            Vec3 edge = w.edge();
            float half = w.half() * (0.7F + 0.3F * w.fade());
            boolean founder = w.founder();
            WorldFx.add(new WorldFx.WaveCrescent(c, dir, edge, half, true, founder));
            WorldFx.add(new WorldFx.WaveCrescent(c, dir, edge, half, false, founder));
            RandomSource rnd = w.getRandom();
            if (founder) {
                // lightning crawls along the edge of every Founder wave
                for (int i = 0; i < 2; i++) {
                    WorldFx.add(new WorldFx.Bolt(arc(c, dir, edge, half, rnd), arc(c, dir, edge, half, rnd), 0.1F, VIOLET, rnd));
                }

                for (int i = 0; i < 5; i++) {
                    Vec3 p = arc(c, dir, edge, half, rnd);
                    mc.level.addAlwaysVisibleParticle(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.WITCH, p.x, p.y, p.z, -dir.x * 0.3, 0.05, -dir.z * 0.3);
                }

                Vec3 p = arc(c, dir, edge, half, rnd);
                mc.level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, dir.x * 0.4, 0.02, dir.z * 0.4);
                return;
            }

            for (int i = 0; i < 4; i++) {
                double l = (rnd.nextDouble() * 2.0 - 1.0) * (double)half;
                Vec3 p = c.add(edge.scale(l));
                mc.level.addParticle(ModParticles.EMBER.get(), p.x, p.y, p.z, -dir.x * 0.2, 0.02, -dir.z * 0.2);
            }

            if (rnd.nextInt(2) == 0) {
                mc.level.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, dir.x * 0.3, 0.0, dir.z * 0.3);
            }
        }
    }
}
