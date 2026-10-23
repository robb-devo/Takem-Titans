package com.takem.titans.odm;

import com.takem.titans.story.Training;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.CannonEntity;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.SlashWaveEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanGrabs;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.BladeTier;
import com.takem.titans.item.VanguardArmorItem;
import com.takem.titans.mixin.ServerGamePacketListenerImplAccessor;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.network.HookActionPayload;
import com.takem.titans.network.HookSyncPayload;
import com.takem.titans.network.OdmActionPayload;
import com.takem.titans.network.OdmInputPayload;
import com.takem.titans.registry.ModAttachments;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import com.takem.titans.world.Boom;
import com.takem.titans.world.GasNova;
import com.takem.titans.world.KillCam;
import com.takem.titans.world.MeteorDive;
import com.takem.titans.world.TownLife;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class OdmServer {
    private OdmServer() {
    }

    public static OdmPlayerData data(Player player) {
        return player.getData(ModAttachments.ODM);
    }

    private static boolean freeGas(Player player, OdmPlayerData d) {
        return player.getAbilities().instabuild || d.overdrive();
    }

    public static void handleHookAction(HookActionPayload msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            OdmPlayerData var10 = data(player);
            HookSide side = HookSide.byIndex(msg.side());
            HookState h = var10.hook(side);
            ItemStack gear = OdmGear.getGear(player);
            switch (msg.action()) {
                case 0:
                    if (gear.isEmpty() || OdmGear.getGas(gear) <= 0 && !freeGas(player, var10)) {
                        return;
                    }

                    if (msg.pos().distanceToSqr(player.position()) > 16.0) {
                        return;
                    }

                    if (!freeGas(player, var10)) {
                        OdmGear.setGas(gear, OdmGear.getGas(gear) - 6);
                    }

                    h.reset();
                    h.phase = HookState.Phase.FLYING;
                    h.pos = msg.pos();
                    h.prevPos = msg.pos();
                    h.vel = msg.vel();
                    var10.ticksSinceOdmUse = 0;
                    player.level()
                        .playSound(
                            player,
                            player.getX(),
                            player.getY() + 1.0,
                            player.getZ(),
                            ModSounds.HOOK_FIRE.get(),
                            SoundSource.PLAYERS,
                            0.9F,
                            0.95F + player.getRandom().nextFloat() * 0.1F
                        );
                    broadcast(player, h);
                    break;
                case 1:
                    if (gear.isEmpty()) {
                        return;
                    }

                    if (player.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) {
                        player.stopRiding();
                    }

                    double range = OdmGear.hookRange() + 12.0;
                    if (msg.pos().distanceToSqr(player.getEyePosition()) > range * range) {
                        return;
                    }

                    h.phase = HookState.Phase.ATTACHED;
                    h.pos = msg.pos();
                    h.prevPos = msg.pos();
                    h.entityId = msg.entityId();
                    h.partIndex = msg.part();
                    h.localOffset = msg.localOffset();
                    h.ropeLength = (double)msg.rope();
                    h.age = 0;
                    var10.ticksSinceOdmUse = 0;
                    Entity target = h.entityId >= 0 ? player.level().getEntity(h.entityId) : null;
                    if (target != null && Grapple.grabbable(target)) {
                        Grapple.onAttach(player, h, target);
                    }

                    Training.on(player, Training.HOOK);

                    player.level()
                        .playSound(
                            player,
                            msg.pos().x,
                            msg.pos().y,
                            msg.pos().z,
                            target != null ? ModSounds.HOOK_HIT_FLESH.get() : ModSounds.HOOK_HIT.get(),
                            SoundSource.PLAYERS,
                            0.8F,
                            1.0F
                        );
                    broadcast(player, h);
                    break;
                case 2:
                    Grapple.onRelease(player, var10, h);
                    if (h.isActive()) {
                        h.phase = HookState.Phase.RETRACTING;
                        h.entityId = -1;
                        h.reeling = false;
                        broadcast(player, h);
                        h.reset();
                    }
            }
        }
    }

    public static void handleInput(OdmInputPayload msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            OdmPlayerData d = data(player);
            boolean wasBoosting = d.boosting;
            d.boosting = msg.has(1);
            d.left.reeling = msg.has(2);
            d.right.reeling = msg.has(4);
            if (msg.has(8) || d.boosting) {
                d.ticksSinceOdmUse = 0;
            }

            if (wasBoosting != d.boosting) {
                PacketDistributor.sendToPlayersTrackingEntity(player, FeedbackPayload.of(7, player.getId(), d.boosting ? 1 : 0, 0));
                if (d.boosting) {
                    Training.on(player, Training.GAS);
                }
            }
        }
    }

    public static void handleAction(OdmActionPayload msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            OdmPlayerData var10 = data(player);
            ItemStack gear = OdmGear.getGear(player);
            // the drills count what the recruit did on their own screen: the server's own checks (on the ground a tick
            // early, a cooldown a tick late) mustn't swallow a manoeuvre they saw happen
            switch (msg.action()) {
                case 0 -> Training.on(player, Training.GAS);
                case 1 -> {
                    if (player.getMainHandItem().getItem() instanceof BladeItem) {
                        Training.on(player, Training.RELOAD);
                    }
                }
                case 4 -> Training.on(player, Training.SPIN);
                case 5 -> Training.on(player, Training.RUSH);
                default -> {
                }
            }

            switch (msg.action()) {
                case 0:
                    if (gear.isEmpty() || var10.dashCooldown > 0 || !freeGas(player, var10) && OdmGear.getGas(gear) < 25) {
                        return;
                    }

                    if (!freeGas(player, var10)) {
                        OdmGear.setGas(gear, OdmGear.getGas(gear) - (int)Math.round(50.0 * Skills.gasMul(player)));
                    }

                    var10.dashCooldown = Skills.dashCooldown(player, var10.overdrive() ? 8 : 16) - 2;
                    var10.ticksSinceOdmUse = 0;
                    if (Skills.has(player, Skill.STORM_DASH)) {
                        Boom.Spec storm = new Boom.Spec().radius(5.0).damage(8.0F, 25.0F).fx(-1, 0.0F, 0.0F).owner(player, player);
                        storm.glass = false;
                        storm.collapse = false;
                        storm.volume = 1.5F;
                        storm.knock = 2.2;
                        storm.shake = 0.15F;
                        Boom.explode(player.serverLevel(), player.position().add(0.0, 1.0, 0.0), storm);
                        player.serverLevel().sendParticles(ModParticles.GAS.get(), player.getX(), player.getY() + 1.0, player.getZ(), 40, 1.2, 0.8, 1.2, 0.2);
                    }

                    player.level()
                        .playSound(
                            player,
                            player.getX(),
                            player.getY() + 1.0,
                            player.getZ(),
                            ModSounds.GAS_BURST.get(),
                            SoundSource.PLAYERS,
                            1.0F,
                            0.9F + player.getRandom().nextFloat() * 0.2F
                        );
                    break;
                case 1:
                    reload(player, var10);
                    break;
                case 2:
                    if (gear.isEmpty() || var10.ticksSinceOdmUse > 40 || !TakemConfig.WALL_IMPACT_DAMAGE.get()) {
                        return;
                    }

                    double lost = Math.min((double)msg.value() / 1000.0, 4.08);
                    double excess = lost - 1.55;
                    if (excess > 0.0) {
                        float dmg = (float)(excess * 11.0);
                        player.hurt(player.damageSources().flyIntoWall(), dmg);
                    }
                    break;
                case 3:
                    TitanGrabs.struggle(player);
                    break;
                case 4:
                    ItemStack blades = player.getMainHandItem();
                    boolean cyclone = msg.value() == 1;
                    int cost = cyclone ? 90 : 70;
                    if (gear.isEmpty()
                        || var10.spinCooldown > 0
                        || player.onGround()
                        || !(blades.getItem() instanceof BladeItem)
                        || BladeItem.isBroken(blades)
                        || !freeGas(player, var10) && OdmGear.getGas(gear) < cost / 2) {
                        return;
                    }

                    if (!freeGas(player, var10)) {
                        OdmGear.setGas(gear, OdmGear.getGas(gear) - cost);
                    }

                    var10.cyclone = cyclone;
                    var10.spinTicks = cyclone ? 22 : 10;
                    var10.spinCooldown = (var10.overdrive() ? 14 : 28) - 2;
                    var10.spinHits.clear();
                    if (Skills.has(player, Skill.CRESCENT_CYCLONE)) {
                        SlashWaveEntity.crescentRing(player, cyclone ? 12 : 8, cyclone ? 14 : 9, isRed(player, var10));
                    }

                    var10.ticksSinceOdmUse = 0;
                    player.level()
                        .playSound(player, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.7F);
                    player.level()
                        .playSound(player, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.GAS_BURST.get(), SoundSource.PLAYERS, 1.0F, 1.3F);
                    if (cyclone) {
                        player.level()
                            .playSound(
                                player,
                                player.getX(),
                                player.getY() + 1.0,
                                player.getZ(),
                                SoundEvents.TRIDENT_RIPTIDE_2.value(),
                                SoundSource.PLAYERS,
                                1.4F,
                                1.2F
                            );
                    }
                    break;
                case 5:
                    ItemStack bladesx = player.getMainHandItem();
                    if (gear.isEmpty()
                        || var10.rushCooldown > 0
                        || !(bladesx.getItem() instanceof BladeItem)
                        || BladeItem.isBroken(bladesx)
                        || !freeGas(player, var10) && OdmGear.getGas(gear) < 30) {
                        return;
                    }

                    if (!freeGas(player, var10)) {
                        OdmGear.setGas(gear, OdmGear.getGas(gear) - 60);
                    }

                    var10.rushTicks = 10;
                    var10.rushCooldown = (var10.overdrive() ? 15 : 30) - 3;
                    var10.rushFrom = player.position().add(0.0, 1.0, 0.0);
                    var10.rushHits.clear();
                    var10.ticksSinceOdmUse = 0;
                    ServerLevel level = player.serverLevel();
                    level.playSound(
                        player, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 2.0F, 1.1F
                    );
                    level.playSound(player, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.GAS_BURST.get(), SoundSource.PLAYERS, 1.4F, 0.8F);
                    break;
                case 6:
                    SlashWaveEntity.sonicBoom(player, var10);
                    break;
                case 7:
                    SlashWaveEntity.fire(player, var10, msg.value() - 180);
                    break;
                case 8:
                    if (gear.isEmpty() || !(player.getMainHandItem().getItem() instanceof BladeItem)) {
                        return;
                    }

                    if (player.level().getGameTime() < var10.meteorReadyAt) {
                        return;
                    }

                    var10.diveGrace = 1200;
                    var10.diveStartY = player.getY();
                    var10.ticksSinceOdmUse = 0;
                    player.level()
                        .playSound(player, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.GAS_BURST.get(), SoundSource.PLAYERS, 1.6F, 0.6F);
                    break;
                case 9:
                    if (var10.diveGrace <= 0 || gear.isEmpty()) {
                        return;
                    }

                    var10.diveGrace = 20;
                    player.fallDistance = 0.0F;
                    double diveSpeed = Math.min((double)msg.value() / 100.0, 80.0);
                    Vec3 at = MeteorDive.landing(player, diveSpeed);
                    MeteorDive.slam(player, Math.min(diveSpeed, 40.0), var10.diveStartY - at.y, at);
                    break;
                case 10:
                    Grapple.throwHeld(player, var10);
                    break;
                case 11:
                    Grapple.rip(player, var10, msg.value());
                    break;
                case 12:
                    GasNova.trigger(player, var10);
                case 13:
                case 14:
                case 15:
                case 16:
                case 17:
                case 18:
                case 19:
                default:
                    break;
                case 20:
                    if (player.getVehicle() instanceof CannonEntity cannon) {
                        cannon.tryFire(player);
                    }
            }
        }
    }

    private static void spinSweep(ServerPlayer player, OdmPlayerData d) {
        ItemStack blades = player.getMainHandItem();
        if (blades.getItem() instanceof BladeItem && !BladeItem.isBroken(blades)) {
            double radius = d.cyclone ? 4.4 : 3.4;
            float dmg = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (d.cyclone ? 0.9F : 1.3F);
            if (d.cyclone) {
                if (d.spinTicks % 4 == 0) {
                    d.spinHits.clear();
                }

                if (d.spinTicks % 2 == 0) {
                    Vec3 v = d.velocity.lengthSqr() > 1.0E-4 ? d.velocity : player.getLookAngle();
                    float yaw = (float)(Mth.atan2(-v.x, v.z) * 180.0 / 3.1415927410125732);
                    float pitch = (float)(-Mth.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * 180.0 / 3.1415927410125732);
                    Fx.send(
                        player.serverLevel(),
                        6,
                        player.getX(),
                        player.getY() + 1.0,
                        player.getZ(),
                        (float)radius,
                        yaw,
                        pitch,
                        isRed(player, d) ? -1.0F : 1.0F,
                        96.0
                    );
                }
            }

            AABB box = player.getBoundingBox().inflate(radius);
            Vec3 c = player.position().add(0.0, 1.0, 0.0);

            for (Entity e : player.level().getEntities(player, box, ex -> ex.isAlive() && !ex.isSpectator())) {
                long key = e instanceof TitanPart part ? (long)part.getParent().getId() << 8 | (long)part.index : (long)e.getId() << 8 | 255L;
                if (!d.spinHits.contains(key) && !(e.getBoundingBox().distanceToSqr(c) > radius * radius)) {
                    boolean hit;
                    if (e instanceof TitanPart partx) {
                        hit = partx.getParent().hurtPart(partx, player.damageSources().playerAttack(player), dmg);
                    } else {
                        if (!(e instanceof LivingEntity living) || e instanceof AbstractTitan) {
                            continue;
                        }

                        hit = living.hurt(player.damageSources().playerAttack(player), dmg * 0.6F);
                    }

                    if (hit) {
                        d.spinHits.add(key);
                        BladeItem.wear(blades, player, 1);
                        if (BladeItem.isBroken(blades)) {
                            d.spinTicks = 0;
                            return;
                        }
                    }
                }
            }
        } else {
            d.spinTicks = 0;
        }
    }

    public static boolean isRed(Player player, OdmPlayerData d) {
        if (d.overdrive()) {
            return true;
        } else {
            if (player.getMainHandItem().getItem() instanceof BladeItem b && b.tier == BladeTier.ACKERMAN) {
                return true;
            }

            return false;
        }
    }

    private static void rushSweep(ServerPlayer player, OdmPlayerData d) {
        ItemStack blades = player.getMainHandItem();
        Vec3 to = player.position().add(0.0, 1.0, 0.0);
        Vec3 from = d.rushFrom != null ? d.rushFrom : to;
        d.rushFrom = to;
        if (blades.getItem() instanceof BladeItem && !BladeItem.isBroken(blades)) {
            ServerLevel level = player.serverLevel();
            boolean red = isRed(player, d);
            if (from.distanceToSqr(to) > 0.04) {
                Fx.send(level, 5, from.x, from.y - 0.25, from.z, (float)to.x, (float)(to.y - 0.25), (float)to.z, red ? -1.6F : 1.6F, 128.0);
            }

            float dmg = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6F;
            double r = 2.4;
            AABB box = new AABB(from, to).inflate(r + 1.0);
            Vec3 dir = to.subtract(from);
            dir = dir.lengthSqr() < 1.0E-6 ? player.getLookAngle() : dir.normalize();

            for (Entity e : level.getEntities(player, box, ex -> ex.isAlive() && !ex.isSpectator())) {
                long key = e instanceof TitanPart part ? (long)part.getParent().getId() << 8 | (long)part.index : (long)e.getId() << 8 | 255L;
                if (!d.rushHits.contains(key)) {
                    AABB eb = e.getBoundingBox().inflate(r);
                    if (eb.contains(from) || !eb.clip(from, to).isEmpty()) {
                        boolean hit;
                        if (e instanceof TitanPart partx) {
                            float rushDmg = dmg;
                            if (partx.kind == 1 && Skills.has(player, Skill.THUNDER_EXECUTION)) {
                                rushDmg = dmg * 2.5F;
                                thunderExecution(level, partx.position().add(0.0, (double)partx.getBbHeight() * 0.5, 0.0));
                            }

                            hit = partx.getParent().hurtPart(partx, player.damageSources().playerAttack(player), rushDmg);
                        } else {
                            if (!(e instanceof LivingEntity living) || e instanceof AbstractTitan || e instanceof Player) {
                                continue;
                            }

                            hit = living.hurt(player.damageSources().playerAttack(player), dmg * 1.2F);
                            if (hit) {
                                living.knockback(1.2, -dir.x, -dir.z);
                                Vec3 at = living.position().add(0.0, (double)living.getBbHeight() * 0.6, 0.0);
                                Fx.send(level, 3, at, player.getYRot(), player.getXRot(), (level.random.nextFloat() - 0.5F) * 120.0F, red ? -2.4F : 2.4F, 96.0);
                            }
                        }

                        if (hit) {
                            d.rushHits.add(key);
                            BladeItem.wear(blades, player, 1);
                            if (BladeItem.isBroken(blades)) {
                                d.rushTicks = 0;
                                return;
                            }
                        }
                    }
                }
            }
        } else {
            d.rushTicks = 0;
        }
    }

    public static void aftershock(ServerPlayer player, Vec3 at) {
        ServerLevel level = player.serverLevel();
        thunderExecution(level, at);
        Boom.Spec s = new Boom.Spec().radius(5.5).damage(12.0F, 80.0F).fx(4, 4.5F, 0.0F).owner(player, player);
        s.glass = false;
        s.volume = 4.0F;
        s.knock = 1.4;
        Boom.explode(level, at, s);
    }

    private static void skybreaker(ServerPlayer player, OdmPlayerData d) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        d.combo = 5;
        Fx.send(level, 27, c.x, c.y + 1.0, c.z, 16.0F, 1.0F, 0.0F, 0.0F, 320.0);
        Fx.send(level, 11, c, 60.0F, 0.0F, 320.0);
        level.playSound(null, c.x, c.y, c.z, ModSounds.NAPE_THUNDER.get(), SoundSource.PLAYERS, 8.0F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8.0F, 0.8F);
        PacketDistributor.sendToPlayer(player, FeedbackPayload.of(10, 100, 180, 0));
        KillCam.slowMo(level.getServer(), 4.0F, 600L, 600L);
        DamageSource src = level.damageSources().playerAttack(player);

        for (AbstractTitan t : level.getEntitiesOfClass(
            AbstractTitan.class, new AABB(c, c).inflate(28.0, 40.0, 28.0), tx -> tx.isAlive() && !(tx instanceof RumblingTitan) && !tx.isDummy()
        )) {
            Vec3 at = t.position().add(0.0, (double)t.getBbHeight(), 0.0);
            thunderExecution(level, at);
            t.igniteForSeconds(6.0F);
            t.traumaHit(260.0F, player, src, true);
        }

        Boom.Spec s = new Boom.Spec().radius(12.0).damage(20.0F, 0.0F).fx(-1, 0.0F, 0.0F).owner(player, player);
        s.knock = 2.4;
        s.volume = 6.0F;
        Boom.explode(level, c, s);
        player.displayClientMessage(Component.translatable("message.takemtitans.skybreaker").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void thunderExecution(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y - 2.0, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        Fx.send(level, 11, at, 40.0F, 0.0F, 256.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.NAPE_THUNDER.get(), SoundSource.PLAYERS, 5.0F, 1.2F);
    }

    public static void startOverdrive(ServerPlayer player, OdmPlayerData d) {
        d.overdriveTicks = Skills.has(player, Skill.ACKERMAN_AWAKENING) ? 400 : 240;
        ServerLevel level = player.serverLevel();
        PacketDistributor.sendToPlayer(player, FeedbackPayload.of(9, d.overdriveTicks, 0, 0));
        PacketDistributor.sendToPlayer(player, FeedbackPayload.of(10, 100, 140, 0));
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 1.3F);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
        Fx.send(level, 13, player.position().add(0.0, 1.0, 0.0), (float)player.getId(), 0.0F, 128.0);
    }

    private static void tickOverdrive(ServerPlayer player, OdmPlayerData d) {
        if (d.overdriveCooldown > 0) {
            d.overdriveCooldown--;
        }

        if (d.overdriveTicks > 0) {
            d.overdriveTicks--;
            if (d.overdriveTicks % 2 == 0) {
                Fx.send(player.serverLevel(), 8, player.position(), (float)player.getId(), 0.0F, 64.0);
            }

            if (d.overdriveTicks == 0 || !player.isAlive()) {
                d.overdriveTicks = 0;
                d.overdriveCooldown = 300;
                PacketDistributor.sendToPlayer(player, FeedbackPayload.of(9, 0, 0, 0));
                player.serverLevel()
                    .playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
    }

    private static void reload(ServerPlayer player, OdmPlayerData d) {
        if (d.reloadCooldown <= 0) {
            ItemStack blades = player.getMainHandItem();
            if (blades.getItem() instanceof BladeItem) {
                ItemStack gear = OdmGear.getGear(player);
                if (!gear.isEmpty() && blades.getDamageValue() != 0) {
                    int spare = OdmGear.getSpareBlades(gear);
                    if (spare <= 0) {
                        player.displayClientMessage(Component.translatable("message.takemtitans.no_blades"), true);
                    } else {
                        OdmGear.setSpareBlades(gear, spare - 1);
                        blades.setDamageValue(0);
                        d.reloadCooldown = 14;
                        player.level()
                            .playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.BLADE_RELOAD.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                }
            }
        }
    }

    static void broadcast(ServerPlayer player, HookState state) {
        HookState copy = new HookState(state.side);
        copy.copyFrom(state);
        PacketDistributor.sendToPlayersTrackingEntity(player, new HookSyncPayload(player.getId(), copy));
    }

    private static void clearHooks(ServerPlayer player, OdmPlayerData d) {
        for (HookState h : new HookState[]{d.left, d.right}) {
            if (h.isActive()) {
                h.reset();
                broadcast(player, h);
            }
        }

        d.boosting = false;
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OdmPlayerData var10 = data(player);
            Vec3 pos = player.position();
            if (var10.lastPos != null) {
                Vec3 vel = pos.subtract(var10.lastPos);
                if (vel.lengthSqr() > 400.0) {
                    vel = Vec3.ZERO;
                }

                var10.velocity = vel;
                var10.recentDownSpeed = Math.max(var10.recentDownSpeed * 0.5, -vel.y);
                var10.recentSpeed = Math.max(var10.recentSpeed * 0.75, vel.length());
            }

            var10.lastPos = pos;
            if (var10.dashCooldown > 0) {
                var10.dashCooldown--;
            }

            if (var10.reloadCooldown > 0) {
                var10.reloadCooldown--;
            }

            if (var10.spinCooldown > 0) {
                var10.spinCooldown--;
            }

            if (var10.rushCooldown > 0) {
                var10.rushCooldown--;
            }

            if (var10.waveCooldown > 0) {
                var10.waveCooldown--;
            }

            if (var10.boomCooldown > 0) {
                var10.boomCooldown--;
            }

            if (var10.diveGrace > 0) {
                var10.diveGrace--;
            }

            tickSkills(player, var10);
            var10.ticksSinceOdmUse++;
            tickOverdrive(player, var10);
            if (var10.rushTicks > 0) {
                var10.rushTicks--;
                rushSweep(player, var10);
            }

            if (var10.combo > 0) {
                var10.comboTimer++;
                var10.groundTicks = player.onGround() ? var10.groundTicks + 1 : 0;
                if (var10.comboTimer > Skills.comboWindow(player) || var10.groundTicks > 12 || !player.isAlive()) {
                    var10.combo = 0;
                    PacketDistributor.sendToPlayer(player, FeedbackPayload.of(8, 0, 100, 0));
                }
            }

            if (var10.spinTicks > 0) {
                var10.spinTicks--;
                spinSweep(player, var10);
            }

            ItemStack gear = OdmGear.getGear(player);
            if (!gear.isEmpty() && !player.isSpectator() && player.isAlive()) {
                for (HookState h : new HookState[]{var10.left, var10.right}) {
                    if (h.phase == HookState.Phase.FLYING && ++h.age > 40) {
                        h.reset();
                    }

                    if (h.isAttached()) {
                        var10.ticksSinceOdmUse = 0;
                        if (h.entityId >= 0) {
                            Entity e = player.level().getEntity(h.entityId);
                            if (e == null || !e.isAlive()) {
                                h.reset();
                                broadcast(player, h);
                            }
                        }
                    }
                }

                double use = 0.0;
                if (var10.boosting && !player.onGround()) {
                    use += 3.0;
                }

                if (var10.left.isAttached() && var10.left.reeling) {
                    use++;
                }

                if (var10.right.isAttached() && var10.right.reeling) {
                    use++;
                }

                if (use > 0.0 && !freeGas(player, var10)) {
                    var10.pendingGas = var10.pendingGas + use * Skills.gasMul(player);
                    int whole = (int)var10.pendingGas;
                    if (whole > 0) {
                        var10.pendingGas -= (double)whole;
                        int gas = OdmGear.getGas(gear);
                        if (gas > 0) {
                            OdmGear.setGas(gear, gas - whole);
                            if (gas - whole <= 0) {
                                player.level()
                                    .playSound(
                                        null, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.GAS_EMPTY.get(), SoundSource.PLAYERS, 1.0F, 1.0F
                                    );
                                player.displayClientMessage(Component.translatable("message.takemtitans.out_of_gas"), true);
                            }
                        }
                    }
                }

                Grapple.tick(player, var10);
                if (var10.ticksSinceOdmUse < 60) {
                    ((ServerGamePacketListenerImplAccessor)player.connection).takemtitans$setAboveGroundTickCount(0);
                }
            } else if (var10.anyHookActive() || var10.boosting) {
                clearHooks(player, var10);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof ServerPlayer p && event.getSource().getEntity() == p) {
            LivingEntity t = event.getEntity();
            if (!(t instanceof AbstractTitan) && p.getMainHandItem().getItem() instanceof BladeItem) {
                if (data(p).rushTicks > 0) {
                    return;
                }

                Vec3 at = t.position().add(0.0, (double)t.getBbHeight() * 0.6, 0.0);
                Fx.send(p.serverLevel(), 3, at, p.getYRot(), p.getXRot(), (p.getRandom().nextFloat() - 0.5F) * 120.0F, isRed(p, data(p)) ? -1.8F : 1.8F, 64.0);
                return;
            }

            return;
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OdmPlayerData d = data(player);
            if (d.diveGrace > 0 || d.immuneTicks > 0 || VanguardArmorItem.fullSet(player)) {
                event.setDistance(0.0F);
            } else if (d.ticksSinceOdmUse <= 80 && !OdmGear.getGear(player).isEmpty()) {
                double h = OdmPhysics.equivalentFallHeight(d.recentDownSpeed, player.getGravity());
                event.setDistance((float)Math.max(0.0, h - 1.0));
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
            OdmPlayerData d = data(target);

            for (HookState h : new HookState[]{d.left, d.right}) {
                if (h.isActive()) {
                    HookState copy = new HookState(h.side);
                    copy.copyFrom(h);
                    PacketDistributor.sendToPlayer(watcher, new HookSyncPayload(target.getId(), copy));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            clearHooks(p, data(p));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            OdmPlayerData d = data(p);
            d.left.reset();
            d.right.reset();
            d.boosting = false;
            d.grabbedBy = -1;
            d.lastPos = null;
            d.overdriveTicks = 0;
            d.rushTicks = 0;
            d.spinTicks = 0;
        }
    }

    private static void tickSkills(ServerPlayer player, OdmPlayerData d) {
        if (d.immuneTicks > 0) {
            d.immuneTicks--;
        }

        if (d.novaCooldown > 0) {
            d.novaCooldown--;
        }

        if (d.rallyTicks > 0) {
            d.rallyTicks--;
        }

        if (d.chainTicks > 0 && --d.chainTicks == 0) {
            d.meteorChain = 0;
        }

        int medic = Skills.rank(player, Skill.FIELD_MEDIC);
        if (medic > 0 && player.isAlive() && ++d.medicTimer >= 80 - 25 * medic) {
            d.medicTimer = 0;
            if (player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0F);
            }
        }

        if (d.grabbedBy >= 0
            && Skills.has(player, Skill.VETERAN_INSTINCTS)
            && player.level().getGameTime() >= d.instinctReadyAt
            && player.level().getEntity(d.grabbedBy) instanceof AbstractTitan titan
            && titan.getHeld() == player
            && player.getMainHandItem().getItem() instanceof BladeItem) {
            d.instinctReadyAt = player.level().getGameTime() + 600L;
            titan.cutFree(player, true);
            if (titan.getHeld() == player) {
                titan.release(true);
            }

            player.displayClientMessage(Component.translatable("message.takemtitans.instincts"), true);
            player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.BLADE_NAPE.get(), SoundSource.PLAYERS, 2.0F, 1.3F);
            Fx.send(player.serverLevel(), 3, player.position().add(0.0, 1.0, 0.0), player.getYRot(), 30.0F, 0.0F, -3.0F, 96.0);
        }

        if (d.diveGrace > 20 && Skills.has(player, Skill.COMET_TRAIL) && d.velocity.y < -1.2) {
            MeteorDive.cometTrail(player, d);
        }
    }

    public static void onNapeKill(ServerPlayer player) {
        OdmPlayerData d = data(player);
        if (Skills.has(player, Skill.PHANTOM_STEP)) {
            d.dashCooldown = 0;
            d.spinCooldown = 0;
            d.rushCooldown = 0;
            ItemStack gear = OdmGear.getGear(player);
            if (!gear.isEmpty()) {
                OdmGear.setGas(gear, OdmGear.getGas(gear) + 150);
            }

            PacketDistributor.sendToPlayer(player, FeedbackPayload.of(14, 0, 0, 0));
        }

        if (Skills.has(player, Skill.KILL_CHAIN)) {
            long now = player.level().getGameTime();
            d.killChain = now < d.killChainUntil ? Math.min(5, d.killChain + 1) : 1;
            d.killChainUntil = now + 160L;
            if (d.killChain >= 2) {
                player.displayClientMessage(Component.translatable("message.takemtitans.kill_chain", d.killChain).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }

        if (Skills.has(player, Skill.BLADE_DANCER)) {
            player.heal(3.0F);
        }

        if (Skills.has(player, Skill.SKYBREAKER) && d.combo >= (Skills.has(player, Skill.GODSLAYER) ? 10 : 15)) {
            skybreaker(player, d);
        }

        if (Skills.has(player, Skill.RALLY_CRY)) {
            d.rallyTicks = 200;
            TownLife.rally(player);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            OdmPlayerData d = data(p);
            DamageSource src = event.getSource();
            if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return;
            }

            if (d.immuneTicks > 0) {
                event.setCanceled(true);
            } else if (!VanguardArmorItem.fullSet(p) || !src.is(DamageTypes.FALL) && !src.is(DamageTypes.FLY_INTO_WALL)) {
                if (src.getEntity() == p && src.is(DamageTypeTags.IS_EXPLOSION)) {
                    event.setCanceled(true);
                } else {
                    if (src.getDirectEntity() instanceof FallingSectionEntity fs
                        && (fs.tether.holder == p.getId() || fs.tether.thrownBy(p.getId(), p.getUUID()))) {
                        event.setCanceled(true);
                        return;
                    }

                    if (src.getDirectEntity() instanceof DebrisEntity de && (de.tether.holder == p.getId() || de.tether.thrownBy(p.getId(), p.getUUID()))) {
                        event.setCanceled(true);
                    }
                }
            } else {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Skills.has(p, Skill.WINGS_OF_FREEDOM)) {
            OdmPlayerData d = data(p);
            long now = p.level().getGameTime();
            if (now >= d.wingsReadyAt && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
                d.wingsReadyAt = now + 6000L;
                p.setHealth(Math.max(8.0F, p.getMaxHealth() * 0.4F));
                d.immuneTicks = 60;
                if (d.grabbedBy >= 0 && p.level().getEntity(d.grabbedBy) instanceof AbstractTitan titan && titan.getHeld() == p) {
                    titan.release(true);
                }

                startOverdrive(p, d);
                ServerLevel level = p.serverLevel();
                Fx.send(level, 13, p.position().add(0.0, 1.0, 0.0), (float)p.getId(), 0.0F, 160.0);
                level.playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.CUTSCENE_STING.get(), SoundSource.PLAYERS, 2.0F, 1.2F);
                PacketDistributor.sendToPlayer(p, new FeedbackPayload(5, 0, 0, 0, "message.takemtitans.wings"));
            }
        }
    }

    public static void particlesForOthers(
        ServerPlayer source, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed
    ) {
        ServerLevel level = source.serverLevel();

        for (ServerPlayer other : level.players()) {
            if (other != source && other.distanceToSqr(source) < 16384.0) {
                level.sendParticles(other, particle, false, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }
}
