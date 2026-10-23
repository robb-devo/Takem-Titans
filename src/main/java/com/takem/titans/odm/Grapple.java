package com.takem.titans.odm;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.fx.Fx;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import com.takem.titans.story.Story;
import com.takem.titans.world.Chunks;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.TownLife;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Grapple {
    private Grapple() {
    }

    @Nullable
    public static Tether tetherOf(@Nullable Entity e) {
        if (e instanceof FallingSectionEntity fs) {
            return fs.tether;
        } else {
            return e instanceof DebrisEntity d ? d.tether : null;
        }
    }

    public static boolean grabbable(@Nullable Entity e) {
        return (e instanceof FallingSectionEntity || e instanceof DebrisEntity) && e.isAlive();
    }

    public static int mass(Entity e) {
        return e instanceof FallingSectionEntity fs ? fs.aliveCount() : 1;
    }

    public static boolean liftable(Player p, @Nullable Entity e) {
        return grabbable(e) && TakemConfig.GRAB_DEBRIS.get() && mass(e) <= Skills.liftCapacity(p);
    }

    public static boolean catches(Player p, @Nullable Entity e) {
        return liftable(p, e) && (p.isShiftKeyDown() || mass(e) <= 12);
    }

    public static double radius(Entity e) {
        return e instanceof FallingSectionEntity fs ? (double)fs.radius : 0.6;
    }

    public static double minLength(Entity e) {
        return 2.2 + radius(e) * 0.9;
    }

    public static Vec3 center(Entity e) {
        return e instanceof DebrisEntity ? e.position().add(0.0, 0.5, 0.0) : e.position();
    }

    static Vec3 origin(Player p) {
        return p.position().add(0.0, 1.0, 0.0);
    }

    public static float throwPower(Player p) {
        return Skills.has(p, Skill.WRECKING_BALL) ? 2.2F : 1.0F;
    }

    public static void onAttach(ServerPlayer player, HookState h, Entity e) {
        h.tether = false;
        if (catches(player, e)) {
            Tether t = tetherOf(e);
            if (t != null && (!t.active() || t.holder == player.getId())) {
                t.holder = player.getId();
                t.length = Math.max(minLength(e), center(e).distanceTo(origin(player)));
                t.carried = false;
                t.age = 0;
                h.tether = true;
                if (e instanceof FallingSectionEntity fs) {
                    fs.shove(Vec3.ZERO);
                }

                ServerLevel level = player.serverLevel();
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.6F, 0.6F);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.7F);
                Story.onCatch(player);
            }
        }
    }

    public static void onRelease(ServerPlayer player, OdmPlayerData d, HookState h) {
        if (h.entityId >= 0) {
            Entity e = player.level().getEntity(h.entityId);
            Tether t = tetherOf(e);
            if (t != null && t.holder == player.getId()) {
                HookState other = h == d.left ? d.right : d.left;
                if (!other.isAttached() || other.entityId != h.entityId) {
                    t.clear();
                    t.thrown = 60;
                    t.thrower = player.getUUID();
                    t.power = throwPower(player);
                }
            }
        }

        h.tether = false;
    }

    public static void tick(ServerPlayer player, OdmPlayerData d) {
        for (HookState h : new HookState[]{d.left, d.right}) {
            if (h.isAttached() && h.tether && h.entityId >= 0) {
                Entity e = player.level().getEntity(h.entityId);
                Tether t = tetherOf(e);
                if (t != null && t.holder == player.getId()) {
                    t.age++;
                    if (h.reeling && !t.carried) {
                        double rate = 0.8 * OdmGear.tier(player).reel * Skills.reelMul(player) / Math.max(1.0, Math.sqrt((double)mass(e)) / 7.0);
                        t.length = Math.max(minLength(e), t.length - Math.max(0.3, rate));
                        if (t.length <= minLength(e) + 0.01) {
                            t.carried = true;
                            PacketDistributor.sendToPlayer(player, FeedbackPayload.of(12, e.getId(), mass(e), 0));
                            player.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
                        }
                    }
                } else {
                    h.tether = false;
                }
            }
        }
    }

    public static void throwHeld(ServerPlayer player, OdmPlayerData d) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();

        for (HookState h : new HookState[]{d.left, d.right}) {
            if (h.isAttached() && h.entityId >= 0) {
                Entity e = level.getEntity(h.entityId);
                Tether t = tetherOf(e);
                if (t != null && t.holder == player.getId()) {
                    int m = mass(e);
                    double cap = Math.min(4000.0, (double)Skills.liftCapacity(player));
                    double base = 2.5 + (Skills.has(player, Skill.WRECKING_BALL) ? 1.0 : 0.0);
                    double sp = base * Mth.clamp(Math.sqrt(cap / (double)Math.max(1, m)) * 0.55, 0.55, 1.5);
                    Vec3 v = look.scale(sp).add(d.velocity.scale(0.8));
                    boolean carried = t.carried;
                    t.clear();
                    t.thrown = 160;
                    t.thrower = player.getUUID();
                    t.power = throwPower(player) * (carried ? 1.25F : 1.0F);
                    if (e instanceof FallingSectionEntity fs) {
                        fs.launch(v, Chunks.tumble(level.random, 0.3F));
                    } else {
                        e.setDeltaMovement(v);
                        e.hurtMarked = true;
                    }

                    h.tether = false;
                    Vec3 at = center(e);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0F, 0.5F);
                    level.playSound(null, at.x, at.y, at.z, ModSounds.GAS_BURST.get(), SoundSource.PLAYERS, 1.6F, 0.7F);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 1.6F, 0.6F);
                    Fx.send(level, 5, at.x, at.y, at.z, (float)(at.x + v.x * 4.0), (float)(at.y + v.y * 4.0), (float)(at.z + v.z * 4.0), 2.0F, 160.0);
                    d.ticksSinceOdmUse = 0;
                }
            }
        }
    }

    public static void rip(ServerPlayer player, OdmPlayerData d, int side) {
        if (Skills.has(player, Skill.TITAN_WINCH) && TakemConfig.GRAB_DEBRIS.get()) {
            HookState h = d.hook(HookSide.byIndex(side));
            if (h.isAttached() && h.entityId < 0) {
                ServerLevel level = player.serverLevel();
                BlockPos hit = solidNear(level, h.pos);
                if (hit != null) {
                    BlockState st = level.getBlockState(hit);
                    if (Destruction.isStructural(level, hit, st) && !(st.getDestroySpeed(level, hit) > 6.0F)) {
                        int rad = Skills.has(player, Skill.WRECKING_BALL) ? 3 : 2;
                        FallingSectionEntity fs = Chunks.rip(level, hit, rad, 0, 6);
                        if (fs != null) {
                            Vec3 toward = origin(player).subtract(fs.position());
                            toward = toward.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : toward.normalize();
                            fs.launch(toward.scale(0.75).add(0.0, 0.35, 0.0), Chunks.tumble(level.random, 0.2F));
                            level.addFreshEntity(fs);
                            Tether t = fs.tether;
                            t.holder = player.getId();
                            t.length = Math.max(minLength(fs), fs.position().distanceTo(origin(player)) - 2.0);
                            t.carried = false;
                            h.entityId = fs.getId();
                            h.partIndex = -1;
                            h.localOffset = fs.worldToLocal(h.pos);
                            h.tether = true;
                            OdmServer.broadcast(player, h);
                            PacketDistributor.sendToPlayer(player, FeedbackPayload.of(13, side, fs.getId(), 0));
                            Vec3 at = Vec3.atCenterOf(hit);
                            Destruction.crunch(level, at, 3.0F);
                            level.playSound(null, at.x, at.y, at.z, ModSounds.BUILDING_COLLAPSE.get(), SoundSource.BLOCKS, 2.5F, 1.3F);
                            level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
                            Fx.send(level, 0, at, 4.0F + (float)rad, 1.5F, 160.0);
                            Destruction.requestCollapse(level, hit, 16, 64, 6);
                            TownLife.alarm(level, at, 40.0, 1);
                            Story.onCatch(player);
                        }
                    } else {
                        player.displayClientMessage(Component.translatable("message.takemtitans.rip_too_solid"), true);
                    }
                }
            }
        }
    }

    @Nullable
    private static BlockPos solidNear(ServerLevel level, Vec3 p) {
        BlockPos c = BlockPos.containing(p);
        if (!level.getBlockState(c).getCollisionShape(level, c).isEmpty()) {
            return c;
        } else {
            BlockPos best = null;
            double bd = 1.7976931348623157E308;

            for (Direction dir : Direction.values()) {
                BlockPos n = c.relative(dir);
                if (!level.getBlockState(n).getCollisionShape(level, n).isEmpty()) {
                    double dd = Vec3.atCenterOf(n).distanceToSqr(p);
                    if (dd < bd) {
                        bd = dd;
                        best = n;
                    }
                }
            }

            return best;
        }
    }

    public static Vec3 constrain(Entity self, Tether t, Vec3 pos, Vec3 v, double radius) {
        if (self.level().getEntity(t.holder) instanceof Player p && p.isAlive() && !p.isSpectator() && !(p.distanceToSqr(self) > 14400.0)) {
            Vec3 pv = OdmServer.data(p).velocity;
            Vec3 anchor = origin(p).add(pv);
            if (t.carried) {
                Vec3 look = p.getLookAngle();
                Vec3 target = p.getEyePosition().add(pv).add(look.scale(1.8 + radius)).add(0.0, -0.6 - radius * 0.35, 0.0);
                Vec3 nv = target.subtract(pos).scale(0.5).add(pv.scale(0.5));
                double l = nv.length();
                return l > 5.0 ? nv.scale(5.0 / l) : nv;
            }

            Vec3 next = pos.add(v);
            Vec3 dv = next.subtract(anchor);
            double dist = dv.length();
            if (dist > t.length && dist > 1.0E-4) {
                Vec3 fixed = anchor.add(dv.scale(t.length / dist));
                Vec3 nv = fixed.subtract(pos);
                double l = nv.length();
                return l > 4.5 ? nv.scale(4.5 / l) : nv;
            }

            return v;
        }

        t.clear();
        return v;
    }
}
