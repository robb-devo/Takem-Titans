package com.takem.titans.combat;

import com.takem.titans.TakemConfig;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanAction;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.KillCam;
import com.takem.titans.world.MeteorDive;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Titan Shift. A player who injects Shifter Serum is struck by lightning and grows into a sixteen-metre Titan for 45
 * seconds. The body is the player themself, scaled up, so walking, jumping, collision and the camera stay the game's own;
 * every client draws a titan in their place (ShiftClient). Whatever the body walks into comes down, its fists crush napes
 * and tear limbs off, every third blow is a stomp, the roar flattens titans and bursts windows, and a leap lands as a
 * Meteor Dive. Damage goes to the body, not the player; when it is spent, or the player holds sneak, they cut themselves
 * out of the nape and the empty body crashes down and boils away.
 */
@EventBusSubscriber(modid = "takemtitans")
public final class TitanShift {
    public static final ResourceLocation ID = TakemTitans.id("titan_shift");
    /** Nine times a man: sixteen blocks tall. */
    public static final float SCALE = 9.0F;
    /** Ticks of build-up before the bolt. */
    public static final int IGNITE = 24;
    /** Ticks the body takes to grow. */
    public static final int GROW = 18;
    public static final int LIFE = 900;
    public static final float MAX_HP = 480.0F;
    public static final int ROAR_CD = 220;
    public static final int LEAP_CD = 140;
    private static final List<Holder<Attribute>> BODY = List.of(
        Attributes.SCALE, Attributes.MOVEMENT_SPEED, Attributes.STEP_HEIGHT, Attributes.JUMP_STRENGTH, Attributes.SAFE_FALL_DISTANCE,
        Attributes.KNOCKBACK_RESISTANCE
    );
    private static final Map<UUID, TitanShift.State> STATES = new HashMap<>();

    private TitanShift() {
    }

    static final class State {
        int t;
        int life = LIFE;
        float hp = MAX_HP;
        int combo;
        int comboTimer;
        int punchCd;
        int hitIn = -1;
        int hitKind;
        int roarCd;
        int roarIn = -1;
        int leapCd;
        int leapIn = -1;
        boolean leaping;
        int air;
        int sneak;
        int ending = -1;
        Vec3 husk;
        float huskYaw;
        float huskHeight;
        Vec3 last;
        int sync;
        int lastCrunch;
        int lastCollapse;
    }

    /** A player wearing a titan right now. True on both sides: the scale modifier rides the attribute sync. */
    public static boolean isShifted(Entity e) {
        if (e instanceof Player p) {
            AttributeInstance a = p.getAttribute(Attributes.SCALE);
            return a != null && a.hasModifier(ID);
        }
        return false;
    }

    public static boolean begin(ServerPlayer p) {
        if (STATES.containsKey(p.getUUID()) || p.isPassenger() || p.isSpectator()) {
            return false;
        }
        TitanShift.State s = new TitanShift.State();
        s.last = p.position();
        STATES.put(p.getUUID(), s);
        ServerLevel level = p.serverLevel();
        p.stopUsingItem();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, IGNITE + 2, 5, false, false));
        Fx.send(level, 60, p.getX(), p.getY(), p.getZ(), (float)p.getId(), (float)IGNITE, 0.0F, 0.0F, 192.0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SHIFT_IGNITE.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
        return true;
    }

    /**
     * Input from a client. A shifted player's moves (30 strike, 31 roar, 32 leap, 33 cut free) are handled here, and any
     * ODM input while they're in the flesh is swallowed. Returns true when the input was taken.
     */
    public static boolean input(ServerPlayer p, int action) {
        TitanShift.State s = STATES.get(p.getUUID());
        if (s == null || s.ending >= 0) {
            return action >= 30 && action < 40;
        }
        if (s.t > IGNITE + GROW) {
            switch (action) {
                case 30 -> punch(p, s);
                case 31 -> roar(p, s);
                case 32 -> leap(p, s);
                case 33 -> end(p, s, 0);
                default -> {
                }
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        TitanShift.State s = STATES.get(p.getUUID());
        if (s == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (!p.isAlive() || p.isSpectator()) {
            clear(p);
            STATES.remove(p.getUUID());
            return;
        }
        s.t++;
        Vec3 pos = p.position();
        Vec3 moved = pos.subtract(s.last);
        s.last = pos;
        if (s.ending >= 0) {
            // cut free: the empty body crashes down behind them, then they're on their own again
            if (++s.ending == 26 && s.husk != null) {
                Vec3 fwd = Vec3.directionFromRotation(0.0F, s.huskYaw);
                Destruction.bodySlam(level, s.husk, fwd, (double)s.huskHeight * 0.85, 2.6, false);
                AbstractTitan.shakeNearby(level, s.husk, 70.0, 0.9F, 16);
                level.playSound(null, s.husk.x, s.husk.y, s.husk.z, ModSounds.TITAN_FALL.get(), SoundSource.PLAYERS, 6.0F, 0.8F);
            }
            if (s.ending > 110) {
                STATES.remove(p.getUUID());
            }
            return;
        }
        if (s.t < IGNITE) {
            return;
        }
        if (s.t == IGNITE) {
            strike(p, level);
        }
        int grown = s.t - IGNITE;
        float scale = grown >= GROW ? SCALE : 1.0F + (SCALE - 1.0F) * ease((float)grown / (float)GROW);
        setScale(p, scale);
        if (scale > 1.6F) {
            crush(p, s, level, moved, grown < GROW);
        }
        if (grown <= GROW) {
            return;
        }
        s.life--;
        if (s.punchCd > 0) {
            s.punchCd--;
        }
        if (s.comboTimer > 0) {
            s.comboTimer--;
        }
        if (s.roarCd > 0) {
            s.roarCd--;
        }
        if (s.leapCd > 0) {
            s.leapCd--;
        }
        if (s.hitIn >= 0 && --s.hitIn < 0) {
            blow(p, level, s.hitKind);
        }
        if (s.roarIn >= 0 && --s.roarIn < 0) {
            roarBlast(p, s, level);
        }
        if (s.leapIn >= 0 && --s.leapIn < 0) {
            launch(p, s, level);
        }
        if (s.leaping && ++s.air > 100) {
            s.leaping = false;
        }
        s.sneak = p.isShiftKeyDown() ? s.sneak + 1 : 0;
        if (s.sneak >= 16 || s.life <= 0) {
            end(p, s, s.life <= 0 ? 1 : 0);
        } else if (++s.sync >= 4) {
            s.sync = 0;
            status(p, s);
        }
    }

    private static float ease(float x) {
        float k = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - k * k * k;
    }

    private static void setScale(ServerPlayer p, float scale) {
        AttributeInstance a = p.getAttribute(Attributes.SCALE);
        if (a != null) {
            AttributeModifier m = a.getModifier(ID);
            double want = (double)scale - 1.0;
            if (m == null || Math.abs(m.amount() - want) > 1.0E-3) {
                a.removeModifier(ID);
                a.addTransientModifier(new AttributeModifier(ID, want, Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    private static void mod(ServerPlayer p, Holder<Attribute> attribute, double amount, Operation op) {
        AttributeInstance a = p.getAttribute(attribute);
        if (a != null) {
            a.removeModifier(ID);
            a.addTransientModifier(new AttributeModifier(ID, amount, op));
        }
    }

    private static void clear(ServerPlayer p) {
        for (Holder<Attribute> attribute : BODY) {
            AttributeInstance a = p.getAttribute(attribute);
            if (a != null) {
                a.removeModifier(ID);
            }
        }
    }

    /** The bolt: the body's strides and weight go on, the blast clears room for it and throws everything back. */
    private static void strike(ServerPlayer p, ServerLevel level) {
        Vec3 feet = p.position();
        mod(p, Attributes.MOVEMENT_SPEED, 1.0, Operation.ADD_MULTIPLIED_BASE);
        mod(p, Attributes.STEP_HEIGHT, 4.4, Operation.ADD_VALUE);
        mod(p, Attributes.JUMP_STRENGTH, 0.38, Operation.ADD_VALUE);
        mod(p, Attributes.SAFE_FALL_DISTANCE, 64.0, Operation.ADD_VALUE);
        mod(p, Attributes.KNOCKBACK_RESISTANCE, 1.0, Operation.ADD_VALUE);
        setScale(p, 1.0F);
        Fx.send(level, 61, feet.x, feet.y, feet.z, (float)p.getId(), SCALE * 1.8F, p.getYRot(), 0.0F, 640.0);
        level.playSound(null, feet.x, feet.y, feet.z, ModSounds.SHIFT_TRANSFORM.get(), SoundSource.PLAYERS, 16.0F, 1.0F);
        KillCam.slowMo(level.getServer(), 5.0F, 700L, 900L);
        PacketDistributor.sendToPlayer(p, FeedbackPayload.of(10, 100, 160, 0));
        AbstractTitan.shakeNearby(level, feet, 140.0, 2.2F, 36);
        Destruction.blast(level, feet.add(0.0, 3.0, 0.0), 6.5);
        Destruction.glassBurst(level, feet, 28.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32.0), e -> e != p && e.isAlive())) {
            double dist = e.distanceTo(p);
            if (dist < 32.0) {
                float k = (float)(1.0 - dist / 32.0);
                if (e instanceof AbstractTitan t) {
                    t.blastKnockDown(30 + (int)(70.0F * k));
                } else {
                    e.setDeltaMovement(e.position().subtract(feet).normalize().scale(0.6 + 2.2 * (double)k).add(0.0, 0.4 + 0.8 * (double)k, 0.0));
                    e.hurtMarked = true;
                }
            }
        }
    }

    /**
     * The body ploughs through whatever it walks or grows into, much as a titan does: the blocks ahead of it, from the
     * feet to above the head, fly off in the direction it's going and the buildings around are asked to settle.
     */
    private static void crush(ServerPlayer p, TitanShift.State s, ServerLevel level, Vec3 moved, boolean growing) {
        if (!TakemConfig.TITAN_GRIEFING.get() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        double speed = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
        Vec3 look = Vec3.directionFromRotation(0.0F, p.getYRot());
        Vec3 dir = speed > 0.02 ? new Vec3(moved.x / speed, 0.0, moved.z / speed) : look;
        AABB body = p.getBoundingBox();
        double reach = 1.0 + Math.min(speed, 1.2) * 3.0;
        AABB box = body.inflate(0.45, 0.0, 0.45).expandTowards(dir.x * reach, 0.0, dir.z * reach).expandTowards(look.x * 1.2, 0.0, look.z * 1.2);
        Vec3 mid = body.getCenter();
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        int limit = growing ? 260 : 110;
        int broken = 0;
        MutableBlockPos m = new MutableBlockPos();
        for (int y = Mth.floor(p.getY() + 0.6); y <= Mth.floor(body.maxY + 1.0) && broken < limit; y++) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX) && broken < limit; x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ) && broken < limit; z++) {
                    m.set(x, y, z);
                    BlockState state = level.getBlockState(m);
                    if (!state.isAir() && Destruction.canSmash(level, m, state, 3.0F)) {
                        double lateral = ((double)x + 0.5 - mid.x) * side.x + ((double)z + 0.5 - mid.z) * side.z;
                        double h = ((double)y - p.getY()) / Math.max(1.0, body.getYsize());
                        Vec3 v;
                        if (growing) {
                            v = new Vec3((double)x + 0.5 - mid.x, 0.0, (double)z + 0.5 - mid.z).normalize()
                                .scale(0.5 + level.random.nextDouble() * 0.7)
                                .add(0.0, 0.25 + level.random.nextDouble() * 0.5, 0.0);
                        } else {
                            v = dir.scale(0.35 + speed * 1.8 + level.random.nextDouble() * 0.45 + h * 0.4)
                                .add(side.scale(Math.signum(lateral) * level.random.nextDouble() * 0.35))
                                .add(0.0, 0.12 + level.random.nextDouble() * 0.3, 0.0);
                        }
                        Destruction.smash(level, m.immutable(), state, v, 0.2F, v.lengthSqr() > 1.0 ? 1 : 0);
                        broken++;
                    }
                }
            }
        }
        if (broken > 0) {
            if (s.t - s.lastCrunch > 6) {
                s.lastCrunch = s.t;
                Destruction.crunch(level, box.getCenter(), 3.2F);
            }
            if (s.t - s.lastCollapse > 8) {
                s.lastCollapse = s.t;
                Destruction.requestCollapse(level, BlockPos.containing(box.getCenter().x, p.getY() + 1.0, box.getCenter().z), 26, 64, 3);
            }
        }
    }

    /** Strike: a right hook, a left hook, then a stomp. The blow lands as the arm (or heel) arrives, not on the click. */
    private static void punch(ServerPlayer p, TitanShift.State s) {
        if (s.punchCd > 0 || s.hitIn >= 0) {
            return;
        }
        int step = s.comboTimer > 0 ? s.combo % 3 : 0;
        s.combo = step + 1;
        s.comboTimer = 40;
        s.hitKind = step;
        s.hitIn = step == 2 ? 9 : 7;
        s.punchCd = step == 2 ? 24 : 13;
        TitanAction a = step == 0 ? TitanAction.SWAT_RIGHT : (step == 1 ? TitanAction.SWAT_LEFT : TitanAction.STOMP);
        ServerLevel level = p.serverLevel();
        Fx.send(level, 62, p.getX(), p.getY(), p.getZ(), (float)p.getId(), (float)a.ordinal(), step == 2 ? 4.0F : 3.0F, 0.0F, 256.0);
        level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.ODM_SWING.get(), SoundSource.PLAYERS, 3.0F, 0.42F);
    }

    private static void blow(ServerPlayer p, ServerLevel level, int kind) {
        float h = 1.8F * p.getScale();
        Vec3 feet = p.position();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, p.getYRot()) : flat.normalize();
        DamageSource src = source(p);
        if (kind < 2) {
            // a hook: the fist sweeps across in front of the chest, at whatever height the player is looking
            double reach = (double)h * 0.55;
            double y = Mth.clamp(p.getEyeY() + look.y * reach, feet.y + (double)h * 0.12, feet.y + (double)h * 0.95);
            Vec3 side = new Vec3(-flat.z, 0.0, flat.x).scale(kind == 0 ? -1.0 : 1.0);
            Vec3 fist = feet.add(flat.scale(reach)).add(side.scale((double)h * 0.06));
            fist = new Vec3(fist.x, y, fist.z);
            int hit = strikeTitans(p, level, fist, (double)h * 0.32, src, false);
            Destruction.blast(level, fist, 3.4);
            hurtSmall(p, level, fist, 5.5, 14.0F, flat);
            Fx.send(level, 63, fist.x, fist.y, fist.z, hit > 0 ? 1.0F : 0.6F, p.getYRot(), (float)hit, 0.0F, 256.0);
            level.playSound(null, fist.x, fist.y, fist.z, ModSounds.SHIFT_PUNCH.get(), SoundSource.PLAYERS, 6.0F, 0.9F + level.random.nextFloat() * 0.2F);
            AbstractTitan.shakeNearby(level, fist, 48.0, hit > 0 ? 0.9F : 0.55F, 10);
            if (hit > 0) {
                PacketDistributor.sendToPlayer(p, FeedbackPayload.of(10, 70, 90, 0));
            }
        } else {
            // the stomp: the heel comes down a stride ahead and the street jumps
            Vec3 foot = feet.add(flat.scale((double)h * 0.22));
            strikeTitans(p, level, foot.add(0.0, (double)h * 0.12, 0.0), (double)h * 0.4, src, true);
            Destruction.blast(level, foot.add(0.0, 1.5, 0.0), 3.2);
            quake(p, level, foot, 22.0);
            Fx.send(level, 65, foot.x, foot.y, foot.z, 22.0F, p.getYRot(), 0.0F, 0.0F, 256.0);
            level.playSound(null, foot.x, foot.y, foot.z, ModSounds.SHIFT_STOMP.get(), SoundSource.PLAYERS, 8.0F, 1.0F);
            AbstractTitan.shakeNearby(level, foot, 80.0, 1.3F, 18);
        }
    }

    /**
     * Every titan whose body is within reach of a blow takes it. A blow on the head or the nape crushes the nape outright
     * (as does a stomp on a titan already down); anywhere else it tears at the limb and knocks the titan flat.
     * Returns 1 for a hit, 2 when one died of it.
     */
    private static int strikeTitans(ServerPlayer p, ServerLevel level, Vec3 at, double radius, DamageSource src, boolean stomp) {
        int result = 0;
        for (AbstractTitan t : level.getEntitiesOfClass(AbstractTitan.class, new AABB(at, at).inflate(radius + 30.0), AbstractTitan::isAlive)) {
            TitanPart best = null;
            TitanPart nape = null;
            double bestD = Double.MAX_VALUE;
            for (TitanPart part : t.getTitanParts()) {
                if (part.kind == 1) {
                    nape = part;
                }
                double d = distSqr(part.getBoundingBox(), at);
                if (d < bestD) {
                    bestD = d;
                    best = part;
                }
            }
            if (best != null && bestD <= radius * radius) {
                if (nape != null && (best.kind == 1 || best.kind == 2 || stomp && t.isDowned())) {
                    t.hurtPart(nape, src, t.maxNapeHealth() * 14.0F + 60.0F);
                } else {
                    t.hurtPart(best, src, t.maxNapeHealth() * 6.0F);
                    t.slammed(level, 1.0F);
                }
                result = Math.max(result, t.isDeadOrDying() ? 2 : 1);
                Vec3 push = t.position().subtract(p.position()).multiply(1.0, 0.0, 1.0).normalize().scale(stomp ? 0.5 : 1.3).add(0.0, 0.3, 0.0);
                t.setDeltaMovement(t.getDeltaMovement().add(push));
                t.hurtMarked = true;
            }
        }
        return result;
    }

    private static double distSqr(AABB b, Vec3 p) {
        double dx = Math.max(Math.max(b.minX - p.x, p.x - b.maxX), 0.0);
        double dy = Math.max(Math.max(b.minY - p.y, p.y - b.maxY), 0.0);
        double dz = Math.max(Math.max(b.minZ - p.z, p.z - b.maxZ), 0.0);
        return dx * dx + dy * dy + dz * dz;
    }

    private static void hurtSmall(ServerPlayer p, ServerLevel level, Vec3 at, double radius, float dmg, Vec3 dir) {
        DamageSource src = source(p);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> e != p && e.isAlive() && !(e instanceof AbstractTitan))) {
            double d = e.position().distanceTo(at);
            if (d < radius) {
                e.hurt(src, dmg * (float)(1.0 - d / radius * 0.5));
                e.setDeltaMovement(e.position().subtract(at).normalize().scale(1.2).add(dir.scale(0.9)).add(0.0, 0.6, 0.0));
                e.hurtMarked = true;
            }
        }
    }

    /** The ground jumping under a stomp or a landing: titans near it go down, everything smaller is thrown up. */
    private static void quake(ServerPlayer p, ServerLevel level, Vec3 at, double radius) {
        DamageSource src = source(p);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 8.0, radius), e -> e != p && e.isAlive())) {
            double dx = e.getX() - at.x;
            double dz = e.getZ() - at.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < radius) {
                float k = (float)(1.0 - d / radius);
                if (e instanceof AbstractTitan t) {
                    if (k > 0.25F) {
                        t.blastKnockDown(40 + (int)(k * 60.0F));
                    }
                } else {
                    e.hurt(src, 4.0F + 10.0F * k);
                    Vec3 out = d > 1.0E-3 ? new Vec3(dx / d, 0.0, dz / d) : Vec3.ZERO;
                    e.setDeltaMovement(out.scale(0.5 * (double)k).add(0.0, 0.5 + 0.7 * (double)k, 0.0));
                    e.hurtMarked = true;
                }
            }
        }
    }

    private static void roar(ServerPlayer p, TitanShift.State s) {
        if (s.roarCd > 0 || s.roarIn >= 0) {
            return;
        }
        s.roarCd = ROAR_CD;
        s.roarIn = 11;
        Fx.send(p.serverLevel(), 62, p.getX(), p.getY(), p.getZ(), (float)p.getId(), (float)TitanAction.ROAR.ordinal(), 0.0F, 0.0F, 256.0);
    }

    /** The roar itself, once the jaw is open: titans close by go down, those further off reel blind, windows burst. */
    private static void roarBlast(ServerPlayer p, TitanShift.State s, ServerLevel level) {
        Vec3 mouth = p.getEyePosition().add(p.getLookAngle().scale(2.0));
        Fx.send(level, 64, mouth.x, mouth.y, mouth.z, (float)p.getId(), p.getYRot(), p.getXRot(), 0.0F, 320.0);
        level.playSound(null, mouth.x, mouth.y, mouth.z, ModSounds.SHIFT_ROAR.get(), SoundSource.PLAYERS, 14.0F, 1.0F);
        Destruction.glassBurst(level, mouth, 30.0);
        AbstractTitan.shakeNearby(level, mouth, 90.0, 1.1F, 40);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(52.0), e -> e != p && e.isAlive())) {
            double d = e.distanceTo(p);
            if (e instanceof AbstractTitan t) {
                if (d < 24.0) {
                    t.blastKnockDown(70);
                } else if (d < 52.0) {
                    t.blind(90);
                }
            } else if (d < 26.0) {
                e.setDeltaMovement(e.position().subtract(p.position()).normalize().scale(1.8 * (1.0 - d / 26.0)).add(0.0, 0.5, 0.0));
                e.hurtMarked = true;
            }
        }
        // the roar burns off steam: some of the body's wounds close
        s.hp = Math.min(MAX_HP, s.hp + MAX_HP * 0.12F);
        status(p, s);
    }

    private static void leap(ServerPlayer p, TitanShift.State s) {
        if (s.leapCd > 0 || s.leapIn >= 0 || s.leaping || !p.onGround()) {
            return;
        }
        s.leapCd = LEAP_CD;
        s.leapIn = 6;
        Fx.send(p.serverLevel(), 62, p.getX(), p.getY(), p.getZ(), (float)p.getId(), (float)TitanAction.LEAP.ordinal(), 0.0F, 0.0F, 256.0);
    }

    /** Off the crouch: the body goes up and forward, and the ground it pushed off from gives. */
    private static void launch(ServerPlayer p, TitanShift.State s, ServerLevel level) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, p.getYRot()) : flat.normalize();
        Vec3 v = flat.scale(2.0).add(0.0, 1.25 + Math.max(0.0, look.y) * 0.6, 0.0);
        p.setDeltaMovement(v);
        PacketDistributor.sendToPlayer(p, FeedbackPayload.of(31, (int)(v.x * 100.0), (int)(v.y * 100.0), (int)(v.z * 100.0)));
        s.leaping = true;
        s.air = 0;
        Vec3 feet = p.position();
        Destruction.blast(level, feet.add(0.0, 1.0, 0.0), 2.8);
        Fx.send(level, 65, feet.x, feet.y, feet.z, 9.0F, p.getYRot(), 0.0F, 0.0F, 256.0);
        level.playSound(null, feet.x, feet.y, feet.z, ModSounds.SHIFT_STOMP.get(), SoundSource.PLAYERS, 5.0F, 1.25F);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            TitanShift.State s = STATES.get(p.getUUID());
            if (s != null) {
                if (s.leaping && s.air > 4 && s.ending < 0) {
                    s.leaping = false;
                    land(p, event.getDistance());
                }
                event.setCanceled(true);
            }
        }
    }

    /** A sixteen-metre body coming down out of a leap lands as a Meteor Dive does: crater, shockwave and all. */
    private static void land(ServerPlayer p, float fall) {
        ServerLevel level = p.serverLevel();
        Vec3 feet = p.position();
        MeteorDive.slam(p, 1.5, (double)Math.max(8.0F, fall) + 10.0, feet);
        quake(p, level, feet, 30.0);
        strikeTitans(p, level, feet.add(0.0, 3.0, 0.0), 9.0, source(p), true);
        Fx.send(level, 65, feet.x, feet.y, feet.z, 30.0F, p.getYRot(), 0.0F, 0.0F, 320.0);
        level.playSound(null, feet.x, feet.y, feet.z, ModSounds.SHIFT_STOMP.get(), SoundSource.PLAYERS, 10.0F, 0.8F);
    }

    /** Cutting free of the nape (reason 0: by choice, 1: time ran out, 2: the body was spent). */
    private static void end(ServerPlayer p, TitanShift.State s, int reason) {
        if (s.ending >= 0) {
            return;
        }
        ServerLevel level = p.serverLevel();
        float h = 1.8F * p.getScale();
        Vec3 feet = p.position();
        Vec3 back = Vec3.directionFromRotation(0.0F, p.yBodyRot).scale(-1.0);
        Vec3 nape = feet.add(back.scale((double)h * 0.07)).add(0.0, (double)h * 0.84, 0.0);
        // out through the back of the neck: they come out clear of the body, facing it as it goes down
        Vec3 out = nape.add(back.scale((double)h * 0.16)).add(0.0, -1.0, 0.0);
        s.ending = 0;
        s.husk = feet;
        s.huskYaw = p.yBodyRot;
        s.huskHeight = h;
        Fx.send(level, 66, nape.x, nape.y, nape.z, (float)p.getId(), p.yBodyRot, h, (float)reason, 512.0);
        level.playSound(null, nape.x, nape.y, nape.z, ModSounds.SHIFT_EJECT.get(), SoundSource.PLAYERS, 6.0F, 1.0F);
        clear(p);
        p.teleportTo(out.x, out.y, out.z);
        p.setDeltaMovement(back.scale(1.15).add(0.0, 0.9, 0.0));
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, false, false));
        PacketDistributor.sendToPlayer(p, FeedbackPayload.of(30, -1, 0, 0));
    }

    private static void status(ServerPlayer p, TitanShift.State s) {
        int cds = Mth.clamp(s.roarCd / 2, 0, 255) | Mth.clamp(s.leapCd / 2, 0, 255) << 8 | Math.min(15, s.sneak) << 16;
        PacketDistributor.sendToPlayer(p, FeedbackPayload.of(30, Math.round(Math.max(0.0F, s.hp) / MAX_HP * 1000.0F), s.life, cds));
    }

    /** While shifting the player can't be hurt; once in the flesh, everything that would hurt them hurts the body. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            TitanShift.State s = STATES.get(p.getUUID());
            DamageSource src = event.getSource();
            if (s != null && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
                if (s.ending < 0 && s.t > IGNITE + GROW && !src.is(DamageTypeTags.IS_FALL) && !src.is(DamageTypes.IN_WALL) && !src.is(DamageTypes.CRAMMING)) {
                    float dmg = event.getAmount() * (src.getEntity() instanceof AbstractTitan ? 1.6F : 0.6F);
                    s.hp -= dmg;
                    PacketDistributor.sendToPlayer(p, FeedbackPayload.of(16, Math.min(100, (int)(dmg * 4.0F)), 8, 0));
                    if (s.hp <= 0.0F) {
                        end(p, s, 2);
                    } else {
                        status(p, s);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && STATES.remove(p.getUUID()) != null) {
            clear(p);
        }
    }

    private static DamageSource source(ServerPlayer p) {
        return new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.PLAYER_ATTACK), null, p);
    }
}
