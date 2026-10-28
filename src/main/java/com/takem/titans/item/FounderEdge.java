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
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.entity.titan.BossTitan;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.world.Boom;
import com.takem.titans.world.Destruction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.takem.titans.TakemConfig;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.world.Rebuild;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Founder's Edge's own move, the Supernova: overcharge a Blade Rush and let go, and wherever you are looking the
 * Coordinate gathers into a point of light and goes off like a dying star - the ground torn open, every titan in reach
 * struck at the nape, rings of lightning walking out after it. One minute to recover.
 * <p>
 * What makes the Founder's Edge more than a sharper blade: a nape kill calls down the Coordinate on the titans around
 * it. Each one nearby is struck at the nape by lightning, and the ones that die from it call it down again (three
 * echoes deep, so a crowded street can go in one cut). Bosses only take a piece of it.
 */
@EventBusSubscriber(
    modid = "takemtitans"
)
public final class FounderEdge {
    private static final double RANGE = 42.0;
    /** Ticks of held attack (a full Blade Rush and then some) that ready Severance. */
    public static final int SEVER_CHARGE = 50;
    public static final int SEVER_COOLDOWN = 1200;
    /** How far off the Supernova can be set, how long its light gathers, and how far its Coordinate reaches. */
    public static final double NOVA_RANGE = 160.0;
    public static final int NOVA_DELAY = 14;
    private static final double NOVA_RADIUS = 60.0;
    private static final List<FounderEdge.Nova> NOVAS = new ArrayList<>();
    /** How far the Founder's Edge reaches with an ordinary swing: it cuts where you look, not where a wave could fly. */
    public static final double REND_RANGE = 56.0;
    private static final double REND_HALF = 9.0;
    private static final List<FounderEdge.Snap> SNAPS = new ArrayList<>();
    private static int depth;

    private FounderEdge() {
    }

    public static boolean holds(net.minecraft.world.entity.player.Player p) {
        ItemStack hand = p.getMainHandItem();
        return hand.getItem() instanceof BladeItem b && b.tier == BladeTier.FOUNDER && !BladeItem.isBroken(hand);
    }

    /** The Supernova: where you are looking, up to {@link #NOVA_RANGE} blocks off, the light gathers and goes off. */
    public static void sever(ServerPlayer p, OdmPlayerData d) {
        if (holds(p) && d.severCooldown <= 0) {
            ServerLevel level = p.serverLevel();
            d.severCooldown = SEVER_COOLDOWN;
            d.immuneTicks = Math.max(d.immuneTicks, 40);
            ItemStack blade = p.getMainHandItem();
            blade.setDamageValue(Math.min(blade.getMaxDamage() - 1, blade.getDamageValue() + 60));
            Vec3 eye = p.getEyePosition();
            Vec3 far = eye.add(p.getLookAngle().scale(NOVA_RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            boolean ground = hit.getType() != HitResult.Type.MISS;
            Vec3 at = ground ? hit.getLocation() : far;
            EntityHitResult body = ProjectileUtil.getEntityHitResult(
                level, p, eye, at, new AABB(eye, at).inflate(3.0), e -> e.isAlive() && e instanceof TitanPart
            );
            if (body != null) {
                at = body.getLocation();
                ground = true;
            }

            // never on top of yourself: a star set against the wall in front of you goes off further out
            if (at.distanceTo(eye) < 14.0) {
                at = eye.add(p.getLookAngle().scale(14.0));
            }

            NOVAS.add(new FounderEdge.Nova(level, p, at, ground));
            Fx.send(level, Fx.SEVER, at.x, at.y, at.z, (float)NOVA_DELAY, 0.0F, 0.0F, 0.0F, 512.0);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 10.0F, 0.5F);
            level.playSound(null, at.x, at.y, at.z, ModSounds.RIFT_HUM.get(), SoundSource.PLAYERS, 10.0F, 0.7F);
            level.playSound(null, eye.x, eye.y, eye.z, ModSounds.SLASH_WAVE_CUT.get(), SoundSource.PLAYERS, 3.0F, 0.5F);
            p.displayClientMessage(Component.translatable("message.takemtitans.severance").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
        }
    }

    /**
     * The Founder's Edge's ordinary swing, the Paths Rend: no wave leaves the blade. Space simply tears open where you
     * are looking, up to {@link #REND_RANGE} blocks off - a jagged seam of light eighteen blocks long, laid at the angle
     * of the swing, tied back to the blade by a golden thread. Whatever the seam lies across is cut; a moment later the
     * tear snaps shut and the snap hits again.
     */
    public static void rend(ServerPlayer p, OdmPlayerData d, int roll) {
        if (!holds(p) || d.waveCooldown > 0) {
            return;
        }

        d.waveCooldown = d.overdrive() ? 4 : 7;
        ServerLevel level = p.serverLevel();
        RandomSource rnd = p.getRandom();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 far = eye.add(look.scale(REND_RANGE));
        BlockHitResult wall = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 at = wall.getType() == HitResult.Type.MISS ? far : wall.getLocation();
        EntityHitResult body = ProjectileUtil.getEntityHitResult(
            level, p, eye, at, new AABB(eye, at).inflate(2.0),
            e -> e.isAlive() && (e instanceof TitanPart || e instanceof LivingEntity && !(e instanceof TownsfolkEntity) && !(e instanceof AbstractTitan))
        );
        if (body != null) {
            at = body.getLocation();
        }

        // never right in your face: a tear at arm's length opens a few blocks out
        if (at.distanceTo(eye) < 5.0) {
            at = eye.add(look.scale(5.0));
        }

        Vec3 edge = edge(look, roll);
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 hand = eye.add(look.scale(0.8)).add(side.scale(0.35)).add(0.0, -0.35, 0.0);
        Fx.send(level, Fx.REND_THREAD, hand.x, hand.y, hand.z, (float)at.x, (float)at.y, (float)at.z, 0.0F, 200.0);
        Fx.send(level, Fx.REND, at.x, at.y, at.z, (float)edge.x, (float)edge.y, (float)edge.z, (float)REND_HALF, 256.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.RIFT_OPEN.get(), SoundSource.PLAYERS, 3.0F, 1.5F + rnd.nextFloat() * 0.3F);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SLASH_WAVE_CUT.get(), SoundSource.PLAYERS, 3.0F, 0.7F + rnd.nextFloat() * 0.2F);
        level.playSound(null, eye.x, eye.y, eye.z, ModSounds.BLADE_SLASH.get(), SoundSource.PLAYERS, 1.2F, 0.6F + rnd.nextFloat() * 0.2F);
        level.playSound(null, eye.x, eye.y, eye.z, ModSounds.NAPE_THUNDER.get(), SoundSource.PLAYERS, 0.6F, 1.7F + rnd.nextFloat() * 0.2F);
        DamageSource src = level.damageSources().playerAttack(p);
        tear(level, p, src, at, edge, REND_HALF, 115.0F, 40.0F);
        if (TakemConfig.SLASH_WAVE_BREAKS_BLOCKS.get()) {
            cutSeam(level, at, edge, look, rnd);
        }

        SNAPS.add(new FounderEdge.Snap(level, p, at, 6));
        if (rnd.nextInt(4) == 0) {
            p.getMainHandItem().hurtAndBreak(1, p, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
    }

    /** The line the seam lies along: across the line of sight, turned by the swing's roll. */
    private static Vec3 edge(Vec3 look, int roll) {
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = look.cross(up).normalize();
        Vec3 vert = side.cross(look).normalize();
        double r = Math.toRadians((double)Mth.clamp(roll, -90, 90));
        return side.scale(Math.cos(r)).add(vert.scale(Math.sin(r)));
    }

    private static double segDist(Vec3 p, Vec3 c, Vec3 e, double half) {
        double l = Mth.clamp(p.subtract(c).dot(e), -half, half);
        return p.distanceTo(c.add(e.scale(l)));
    }

    /** Cuts every titan limb and creature the seam lies across. */
    private static void tear(ServerLevel level, ServerPlayer p, DamageSource src, Vec3 c, Vec3 e, double half, float partDamage, float livingDamage) {
        AABB box = new AABB(c, c).inflate(half + 4.0);
        for (Entity t : level.getEntities(p, box, x -> x.isAlive())) {
            if (t instanceof FallingSectionEntity || t instanceof DebrisEntity) {
                continue;
            }

            double reach = Math.max(t.getBbWidth(), t.getBbHeight()) * 0.5 + 1.2;
            if (segDist(t.getBoundingBox().getCenter(), c, e, half) > reach) {
                continue;
            }

            if (t instanceof TitanPart part) {
                AbstractTitan titan = part.getParent();
                if (titan != null && !(titan instanceof RumblingTitan)) {
                    titan.hurtPart(part, src, partDamage);
                }
            } else if (!(t instanceof AbstractTitan) && !(t instanceof TownsfolkEntity) && t instanceof LivingEntity l) {
                l.hurt(src, livingDamage);
            }
        }
    }

    /** The seam goes through stone and timber like they were not there: the blocks on it are cut out, some thrown. */
    private static void cutSeam(ServerLevel level, Vec3 c, Vec3 e, Vec3 look, RandomSource rnd) {
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
        MutableBlockPos m = new MutableBlockPos();
        Vec3 n = look.cross(e);
        int cut = 0;
        for (double l = -REND_HALF; l <= REND_HALF && cut < 220; l += 0.5) {
            double jag = Math.sin(l * 1.7) * 0.6;
            for (double k = -1.0; k <= 2.5 && cut < 220; k += 0.5) {
                Vec3 q = c.add(e.scale(l)).add(look.scale(k)).add(n.scale(jag));
                m.set(Mth.floor(q.x), Mth.floor(q.y), Mth.floor(q.z));
                BlockState s = level.getBlockState(m);
                if (s.isAir() || Destruction.isWallStone(s)) {
                    continue;
                }

                if (!Destruction.isStructural(level, m, s) && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES) && !Destruction.isMasonryAnchor(level, m, s)) {
                    continue;
                }

                float h = s.getDestroySpeed(level, m);
                if (h < 0.0F || h > 50.0F) {
                    continue;
                }

                if (rnd.nextFloat() < 0.35F) {
                    Vec3 v = look.scale(0.35 + rnd.nextDouble() * 0.4).add(e.scale((rnd.nextDouble() - 0.5) * 0.5)).add(0.0, 0.15, 0.0);
                    Destruction.smash(level, m.immutable(), s, v, rubble);
                } else {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                    Rebuild.mark(level, m);
                    if (cut % 5 == 0) {
                        Destruction.crumble(level, Vec3.atCenterOf(m), s, 3, 0.3F, 1.0F);
                    }
                }

                cut++;
            }
        }

        if (cut > 0) {
            Destruction.requestCollapse(level, BlockPos.containing(c), 14, 56, 8);
        }
    }

    /** A tear about to snap shut. */
    private static final class Snap {
        final ServerLevel level;
        final ServerPlayer owner;
        final Vec3 at;
        int ticks;

        Snap(ServerLevel level, ServerPlayer owner, Vec3 at, int ticks) {
            this.level = level;
            this.owner = owner;
            this.at = at;
            this.ticks = ticks;
        }

        boolean tick() {
            if (--this.ticks > 0) {
                return false;
            }

            Fx.send(this.level, Fx.REND_SNAP, this.at, 1.0F, 0.0F, 200.0);
            this.level.playSound(null, this.at.x, this.at.y, this.at.z, ModSounds.RIFT_IMPLODE.get(), SoundSource.PLAYERS, 2.5F, 1.6F + this.level.random.nextFloat() * 0.3F);
            this.level.playSound(null, this.at.x, this.at.y, this.at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.6F);
            DamageSource src = this.level.damageSources().playerAttack(this.owner);
            for (Entity t : this.level.getEntities(this.owner, new AABB(this.at, this.at).inflate(6.0), x -> x.isAlive())) {
                if (t instanceof TitanPart part && part.getParent() != null && !(part.getParent() instanceof RumblingTitan)
                    && t.getBoundingBox().getCenter().distanceTo(this.at) < 5.0 + t.getBbWidth() * 0.5) {
                    part.getParent().hurtPart(part, src, 45.0F);
                } else if (t instanceof LivingEntity l && !(t instanceof AbstractTitan) && !(t instanceof TownsfolkEntity)) {
                    // the closing tear drags in what is near it
                    Vec3 in = this.at.subtract(l.position());
                    if (in.lengthSqr() > 0.01) {
                        l.setDeltaMovement(l.getDeltaMovement().add(in.normalize().scale(0.6)));
                        l.hurtMarked = true;
                    }

                    l.hurt(src, 12.0F);
                }
            }

            return true;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (!NOVAS.isEmpty()) {
                NOVAS.removeIf(n -> n.level == level && n.tick());
            }

            if (!SNAPS.isEmpty()) {
                SNAPS.removeIf(n -> n.level == level && n.tick());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NOVAS.clear();
        SNAPS.clear();
    }

    /**
     * A Supernova on its way. For {@link #NOVA_DELAY} ticks the light gathers at the spot, then it goes off: a blast that
     * tears the ground open, the Coordinate striking every titan in reach at the nape, and rings of lightning walking
     * outwards after it.
     */
    private static final class Nova {
        final ServerLevel level;
        final ServerPlayer owner;
        final Vec3 at;
        final boolean ground;
        int t;

        Nova(ServerLevel level, ServerPlayer owner, Vec3 at, boolean ground) {
            this.level = level;
            this.owner = owner;
            this.at = at;
            this.ground = ground;
        }

        boolean tick() {
            this.t++;
            if (this.t == NOVA_DELAY) {
                this.detonate();
            } else if (this.t > NOVA_DELAY && (this.t - NOVA_DELAY) % 6 == 0 && this.t <= NOVA_DELAY + 18) {
                // the Coordinate walks outwards in rings: a couple of real strikes per ring, the rest is light
                double r = 14.0 * ((this.t - NOVA_DELAY) / 6);
                for (int i = 0; i < 2; i++) {
                    double a = this.level.random.nextDouble() * Math.PI * 2.0;
                    double x = this.at.x + Math.cos(a) * r;
                    double z = this.at.z + Math.sin(a) * r;
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(this.level);
                    if (bolt != null) {
                        bolt.moveTo(x, this.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)), z);
                        bolt.setVisualOnly(true);
                        this.level.addFreshEntity(bolt);
                    }
                }
            }

            return this.t > NOVA_DELAY + 20 || this.owner.isRemoved();
        }

        private void detonate() {
            ServerLevel level = this.level;
            Vec3 c = this.at;
            level.playSound(null, c.x, c.y, c.z, ModSounds.RIFT_DETONATE.get(), SoundSource.PLAYERS, 12.0F, 0.5F);
            level.playSound(null, c.x, c.y, c.z, ModSounds.BIG_BLAST.get(), SoundSource.PLAYERS, 12.0F, 0.55F);
            level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 12.0F, 0.5F);
            level.playSound(null, c.x, c.y, c.z, ModSounds.COLOSSAL_ARRIVAL.get(), SoundSource.PLAYERS, 8.0F, 1.3F);
            AbstractTitan.shakeNearby(level, c, 200.0, 1.6F, 40);
            Boom.Spec s = new Boom.Spec().owner(this.owner, this.owner);
            s.radius(30.0).blocks(this.ground ? 13.0 : 0.0, 8.0F).damage(30.0F, 160.0F).fx(-1, 0.0F, 0.0F);
            s.knock = 3.0;
            s.volume = 10.0F;
            Boom.explode(level, c, s);
            Destruction.glassBurst(level, c, 60.0);
            DamageSource src = level.damageSources().playerAttack(this.owner);
            // the Coordinate: every titan in reach struck at the nape
            for (AbstractTitan t : level.getEntitiesOfClass(
                AbstractTitan.class, new AABB(c, c).inflate(NOVA_RADIUS, 80.0, NOVA_RADIUS), e -> e.isAlive() && !e.isDummy() && !(e instanceof RumblingTitan)
            )) {
                if (t.position().distanceTo(c) > NOVA_RADIUS + t.getBbWidth()) {
                    continue;
                }

                Vec3 nape = t.skeletonPoint(3, TitanSkeleton.NAPE);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(nape.x, nape.y - 2.0, nape.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }

                Fx.send(level, 40, c, (float)nape.x, (float)nape.y, (float)nape.z, 0.0F, 300.0);
                boolean boss = isBoss(t);
                t.traumaHit(t.maxNapeHealth() * (boss ? 0.35F : 1.5F), this.owner, src, !boss);
            }
        }
    }

    /** The named titans only ever take a piece of the Founder's power. */
    public static boolean isBoss(AbstractTitan t) {
        return t instanceof ArmoredTitan || t instanceof BeastTitan || t instanceof RavagerTitan || t instanceof WallTitan || t instanceof ColossalTitan
            || t instanceof BossTitan;
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
                boolean boss = isBoss(t);
                t.traumaHit(t.maxNapeHealth() * (boss ? 0.2F : 0.75F), p, p.damageSources().playerAttack(p), !boss);
            }
        } finally {
            depth--;
        }
    }
}
