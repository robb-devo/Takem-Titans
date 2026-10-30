package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class MeteorDive {
    private static final List<MeteorDive.Impact> ACTIVE = new ArrayList<>();
    public static final double CATACLYSM = 70.0;

    private MeteorDive() {
    }

    public static double power(ServerPlayer player, OdmPlayerData d, double speed, double fall) {
        double e = Math.max(0.0, fall) + Math.max(0.0, speed - 1.0) * 3.0;
        e *= 1.0 + 0.15 * (double)Skills.rank(player, Skill.HEAVY_LANDING);
        if (Skills.has(player, Skill.COMET_TRAIL)) {
            e *= 1.2;
        }

        if (Skills.has(player, Skill.CATACLYSM)) {
            e *= 1.25;
        }

        if (d.chainTicks > 0 && d.meteorChain > 0) {
            e *= Math.pow(1.35, (double)d.meteorChain);
        }

        return e * TakemConfig.METEOR_POWER.get();
    }

    public static double radius(double e, boolean extinction) {
        double r = e <= 70.0 ? 4.0 + 0.45 * e + 0.004 * e * e : 55.1 + 5.0 * Math.sqrt(e - 70.0) + 0.02 * (e - 70.0);
        // tighter than it once was: a hard landing levels a street, not a district
        r *= 0.78;
        return extinction ? r * 1.2 : r;
    }

    public static double damage(double e) {
        return Math.min(1000000.0, 6.0 * Math.pow(2.0, e / 10.0));
    }

    public static double titanDamage(double e) {
        return Math.min(1000000.0, 4.0 * Math.pow(2.0, e / 9.5));
    }

    /**
     * Where the dive actually hit. The client sends the landing in the same tick it lands, before its new position,
     * so on the server the player is still one tick up the fall: up to 60+ blocks above the ground after a long
     * dive, which carved the crater into thin air. Trace the rest of the way down the dive line to find the ground.
     */
    public static Vec3 landing(ServerPlayer player, double speed) {
        Vec3 from = player.position().add(0.0, 0.5, 0.0);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 dir = new Vec3(look.x * 0.6, Math.min(-0.75, look.y), look.z * 0.6).normalize();
        double reach = Math.min(320.0, speed * 1.5 + 8.0);
        for (Vec3 d : new Vec3[]{dir, new Vec3(0.0, -1.0, 0.0)}) {
            BlockHitResult hit = player.level().clip(new ClipContext(from, from.add(d.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getLocation().add(0.0, 0.05, 0.0);
            }
        }

        return player.position();
    }

    public static void slam(ServerPlayer player, double speed, double fall) {
        slam(player, speed, fall, player.position());
    }

    public static void slam(ServerPlayer player, double speed, double fall, Vec3 at) {
        ServerLevel level = player.serverLevel();
        OdmPlayerData d = OdmServer.data(player);
        RandomSource rnd = level.random;
        double e = power(player, d, speed, fall);
        boolean extinction = Skills.has(player, Skill.EXTINCTION_EVENT);
        int shockRank = Skills.rank(player, Skill.SHOCKWAVE);
        double demo = Skills.demolition(player);
        double R = radius(e, extinction);
        // past 32 blocks the crater keeps growing with the height of the fall, ever more slowly
        double natural = Math.max(2.5, R * 0.42 * demo);
        double craterR = natural <= 32.0 ? natural : 32.0 + Math.pow(natural - 32.0, 0.7);
        double ringMax = R * (1.35 + 0.3 * (double)shockRank);
        float k = (float)Math.min(4.0, e / 35.0);
        Vec3 c = at;
        boolean red = OdmServer.isRed(player, d);
        d.immuneTicks = Math.max(d.immuneTicks, 70 + (int)Math.min(80.0, e));
        d.diveGrace = Math.max(d.diveGrace, 40);
        MeteorDive.Impact impact = new MeteorDive.Impact(level, player, c, e, R, craterR, ringMax, demo, extinction, shockRank);
        Fx.send(level, 25, c.x, c.y + 0.1, c.z, (float)Math.min(R, 160.0), k, red ? 1.0F : 0.0F, impact.sideCode(), 300.0 + R * 2.0);
        if (e >= 40.0) {
            Fx.send(level, 26, c.x, c.y + 0.1, c.z, (float)R, (float)e, extinction ? 1.0F : 0.0F, red ? 1.0F : 0.0F, 400.0 + R * 3.0);
        }

        // one deep impact and the rubble coming down after it; stacking thunder on top only turned it to noise
        float vol = (float)Math.min(40.0, 4.0 + e * 0.1);
        level.playSound(null, c.x, c.y, c.z, ModSounds.METEOR_SLAM.get(), SoundSource.PLAYERS, vol, 0.8F + rnd.nextFloat() * 0.15F);
        level.playSound(null, c.x, c.y, c.z, ModSounds.BOULDER_IMPACT.get(), SoundSource.PLAYERS, vol * 0.7F, 0.6F + rnd.nextFloat() * 0.1F);
        if (e > 55.0) {
            level.playSound(null, c.x, c.y, c.z, ModSounds.RUMBLING_QUAKE.get(), SoundSource.PLAYERS, vol * 0.8F, 0.75F);
        }

        AbstractTitan.shakeNearby(level, c, 32.0 + R * 3.0, (float)Math.min(2.2, 0.5 + e / 80.0), 24 + (int)Math.min(60.0, e * 0.3));
        if (e > 28.0) {
            PacketDistributor.sendToPlayer(player, FeedbackPayload.of(10, 100, (int)Math.min(220.0, 110.0 + e), 0));
            KillCam.slowMo(level.getServer(), (float)Math.max(3.0, 7.0 - e / 30.0), (long)Math.min(900.0, 250.0 + e * 4.0), 700L);
        }

        TownLife.alarm(level, c, ringMax * 1.6 + 30.0, 0);
        impact.blocks = TakemConfig.METEOR_DIVE_BREAKS_BLOCKS.get();
        impact.core();
        if (impact.blocks) {
            Destruction.glassBurst(level, c, impact.clampToSide(R * 1.6));
        }

        ACTIVE.add(impact);
        int after = Skills.rank(player, Skill.AFTERSHOCK);
        if (after > 0) {
            d.aftershockUntil = level.getGameTime() + (after >= 2 ? 200L : 120L);
            d.aftershockHits = after >= 2 ? 5 : 3;
            player.displayClientMessage(Component.translatable("message.takemtitans.aftershock", d.aftershockHits).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }

        int cool = 140;
        if (Skills.has(player, Skill.METEOR_REBOUND) && d.meteorChain < 2) {
            cool = 16;
        }

        if (Skills.has(player, Skill.CATACLYSM)) {
            cool /= 2;
        }

        d.meteorReadyAt = level.getGameTime() + (long)cool;
        PacketDistributor.sendToPlayer(player, FeedbackPayload.of(17, cool, 0, 0));
        if (Skills.has(player, Skill.METEOR_REBOUND)) {
            d.meteorChain = Math.min(3, d.meteorChain + 1);
            d.chainTicks = 80;
            d.diveGrace = Math.max(d.diveGrace, 80);
            PacketDistributor.sendToPlayer(player, FeedbackPayload.of(11, (int)(Math.min(3.4, 1.6 + e / 60.0) * 100.0), d.meteorChain, 0));
        } else {
            d.meteorChain = 0;
        }
    }

    public static void cometTrail(ServerPlayer player, OdmPlayerData d) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position().add(0.0, 1.0, 0.0);
        DamageSource src = level.damageSources().playerAttack(player);

        for (Entity e : level.getEntities(player, new AABB(c, c).inflate(3.5), Entity::isAlive)) {
            if (e instanceof TitanPart) {
                TitanPart part = (TitanPart)e;
                if (!(part.getParent() instanceof RumblingTitan) && part.getParent().invulnerableTime <= 0) {
                    part.getParent().igniteForSeconds(5.0F);
                    part.getParent().traumaHit(10.0F, player, src, false);
                }
            } else if (e instanceof LivingEntity) {
                LivingEntity l = (LivingEntity)e;
                if (!(e instanceof AbstractTitan)) {
                    if (e instanceof TownsfolkEntity) {
                        TownsfolkEntity t = (TownsfolkEntity)e;
                        if (t.isStoryCharacter()) {
                            continue;
                        }
                    }

                    l.igniteForSeconds(6.0F);
                    l.hurt(src, 8.0F);
                }
            }
        }

        if (player.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 12, 0.5, 0.8, 0.5, 0.05);
            level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.0);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && !ACTIVE.isEmpty()) {
            Iterator<MeteorDive.Impact> it = ACTIVE.iterator();

            while (it.hasNext()) {
                MeteorDive.Impact i = it.next();
                if (i.level == level && i.step()) {
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    static final class Impact {
        final ServerLevel level;
        final UUID owner;
        final Vec3 c;
        final double e;
        final double R;
        final double craterR;
        final double ringMax;
        final double demo;
        final boolean extinction;
        final int shockRank;
        final double depth;
        final double dome;
        /** In the walled world the blast stays on the side of the Wall it hit: the Wall stops it dead. */
        final boolean walled;
        final ShiganshinaLayout.Region side;
        /** Past this many blocks the shock wave only throws people about and rattles what is left. */
        final int flattenCap;
        boolean blocks = true;
        double carved;
        double ring;
        int age;
        int thrown;
        int flattened;
        double nextCollapse;
        final IntOpenHashSet hit = new IntOpenHashSet();

        Impact(
            ServerLevel level, ServerPlayer owner, Vec3 c, double e, double R, double craterR, double ringMax, double demo, boolean extinction, int shockRank
        ) {
            this.level = level;
            this.owner = owner.getUUID();
            this.c = c;
            this.e = e;
            this.R = R;
            this.craterR = craterR;
            this.ringMax = ringMax;
            this.demo = demo;
            this.extinction = extinction;
            this.shockRank = shockRank;
            this.depth = 1.5 + Math.min(craterR * 0.3, 16.0);
            this.dome = Math.min(28.0, craterR * 0.45 + Math.min(12.0, e * 0.08));
            this.walled = TitanSpawner.isShiganshina(level);
            this.side = ShiganshinaLayout.region(c.x, c.z);
            this.flattenCap = 700 + (int)Math.min(1800.0, e * 8.0);
            this.nextCollapse = Math.max(10.0, craterR);
        }

        boolean sameSide(double x, double z) {
            return !this.walled || ShiganshinaLayout.region(x, z) == this.side;
        }

        /** The side for the client, so its dust stops at the Wall too: 0 anywhere, else the region's ordinal + 1. */
        float sideCode() {
            return this.walled ? (float)(this.side.ordinal() + 1) : 0.0F;
        }

        /** A radius for effects that can't tell sides apart, kept short of the Wall. */
        double clampToSide(double r) {
            return this.walled ? Math.min(r, Math.max(6.0, ShiganshinaLayout.distToWall(this.c.x, this.c.z))) : r;
        }

        @Nullable
        ServerPlayer player() {
            return this.level.getPlayerByUUID(this.owner) instanceof ServerPlayer sp ? sp : null;
        }

        void core() {
            this.band(0.0, Math.max(this.craterR * 1.1, 4.0));
            this.ring = Math.max(this.craterR * 1.1, 4.0);
            if (this.e >= 35.0) {
                this.ejecta();
            }
        }

        boolean step() {
            this.age++;
            if (this.blocks && this.carved < this.craterR) {
                // the crater opens over a second or two, ring by ring, so the server keeps up and you watch it grow
                double step = Mth.clamp(12000.0 / (Math.PI * 2.0 * Math.max(4.0, this.carved + 2.0) * (this.depth + this.dome)), 0.5, 4.0);
                double next = Math.min(this.craterR, this.carved + step);
                this.carve(this.carved, next);
                this.carved = next;
            }

            if (this.ring < this.ringMax) {
                double speed = Math.min(10.0, 2.6 + this.e * 0.015);
                double prev = this.ring;
                this.ring = Math.min(this.ringMax, this.ring + speed);
                this.band(prev, this.ring);
                if (this.blocks) {
                    this.flatten(prev, this.ring);
                    if (this.ring >= this.nextCollapse) {
                        this.collapseRing(this.ring);
                        this.nextCollapse = this.nextCollapse + Math.max(14.0, this.ringMax / 10.0);
                    }
                }

                if (this.age % 3 == 1) {
                    Fx.send(this.level, 22, this.c.x, this.c.y, this.c.z, (float)this.ring, (float)Math.min(10.0, 3.0 + this.e * 0.05), this.sideCode(), 0.0F, 400.0);
                }
            }

            boolean done = this.ring >= this.ringMax && (!this.blocks || this.carved >= this.craterR);
            if (done && this.blocks) {
                this.collapseRing(this.R * 0.7);
                if (this.e > 45.0 && TakemConfig.DESTRUCTION_FIRES.get()) {
                    Boom.ignite(this.level, this.c, this.clampToSide(this.R * 0.7), (int)Math.min(120.0, this.e * 0.4), this.level.random);
                }
            }

            return done || this.age > 900;
        }

        private void band(double r0, double r1) {
            ServerPlayer player = this.player();
            DamageSource src = player != null ? this.level.damageSources().playerAttack(player) : this.level.damageSources().generic();
            double h = Math.max(12.0, r1 * 0.6 + 8.0);
            AABB box = new AABB(this.c.x - r1, this.c.y - h, this.c.z - r1, this.c.x + r1, this.c.y + h + 10.0, this.c.z + r1);

            for (Entity ent : this.level.getEntities((Entity)null, box, Entity::isAlive)) {
                if (ent == player || ent instanceof TitanPart || this.hit.contains(ent.getId()) || !this.sameSide(ent.getX(), ent.getZ())) {
                    continue;
                }

                double dx = ent.getX() - this.c.x;
                double dz = ent.getZ() - this.c.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r1 || dist < r0 - 0.001) {
                    continue;
                }

                this.hit.add(ent.getId());
                double f = Mth.clamp(1.0 - dist / this.ringMax, 0.0, 1.0);
                double shock = 1.0 + 0.25 * (double)this.shockRank;
                Vec3 out = new Vec3(dx, 0.0, dz);
                out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
                double fling = Math.min(7.0, (0.6 + this.e * 0.02) * f * shock);
                if (ent instanceof AbstractTitan t) {
                    if (!(t instanceof RumblingTitan)) {
                        this.titan(t, dist, f, player, src);
                    }
                } else if (ent instanceof FallingSectionEntity fs) {
                    fs.shove(out.scale(fling * 0.6).add(0.0, 0.3 + fling * 0.4, 0.0));
                } else if (ent instanceof DebrisEntity) {
                    double f2 = Math.min(2.2, fling);
                    ent.setDeltaMovement(ent.getDeltaMovement().add(out.scale(f2)).add(0.0, 0.3 + f2 * 0.4, 0.0));
                } else {
                    if (ent instanceof Player p && (p.isCreative() || p.isSpectator()) || ent instanceof TownsfolkEntity tf && tf.isStoryCharacter()) {
                        continue;
                    }

                    ent.setDeltaMovement(ent.getDeltaMovement().add(out.scale(fling)).add(0.0, Math.min(3.5, 0.5 + fling * 0.55), 0.0));
                    ent.hurtMarked = true;
                    if (ent instanceof LivingEntity l) {
                        double dmg = MeteorDive.damage(this.e) * Math.pow(f, 1.3) * shock;
                        if (dmg >= 1.0) {
                            l.invulnerableTime = 0;
                            l.hurt(src, (float)Math.min(dmg, 1000000.0));
                        }

                        if (this.e > 45.0 && f > 0.35) {
                            l.igniteForSeconds((float)(4.0 + 6.0 * f));
                        }
                    }
                }
            }
        }

        private void titan(AbstractTitan t, double dist, double f, @Nullable ServerPlayer player, DamageSource src) {
            double power = MeteorDive.titanDamage(this.e) * Math.pow(f, 1.2) * (1.0 + 0.2 * (double)this.shockRank);
            // Coming down on the neck is what kills; the rest of the blast throws them around and breaks bones
            double napeD = t.skeletonPoint(3, com.takem.titans.entity.titan.TitanSkeleton.NAPE).distanceTo(this.c);
            power *= Mth.clamp(1.25 - napeD / Math.max(4.0, this.craterR * 1.5), 0.2, 1.0);
            boolean core = dist < this.craterR + (double)t.getBbWidth() * 0.5 + 2.0;
            if (core && (this.e >= 60.0 || power >= (double)t.napeHealthLeft()) && this.extinction) {
                power = Math.max(power, (double)t.napeHealthLeft() + 1.0);
            }

            t.slammed(this.level, (float)Math.min(3.0, this.e / 30.0 * f));
            t.traumaHit((float)Math.min(power, 1000000.0), player, src, power > 25.0);
            if (!t.isDeadOrDying()) {
                Vec3 out = t.position().subtract(this.c).multiply(1.0, 0.0, 1.0);
                if (out.lengthSqr() > 1.0E-4) {
                    double fl = Math.min(3.5, this.e * 0.015 * f);
                    t.setDeltaMovement(t.getDeltaMovement().add(out.normalize().scale(fl)).add(0.0, fl * 0.4, 0.0));
                    t.hurtMarked = true;
                }

                if (this.e > 45.0) {
                    t.igniteForSeconds(6.0F);
                }
            }
        }

        private boolean breakable(BlockState s, BlockPos p) {
            // the Walls are not touched by any dive, however hard: they stop it dead
            if (Destruction.isWallStone(s) || !Chunks.takes(this.level, p, s, 1)) {
                return false;
            } else {
                float h = s.getDestroySpeed(this.level, p);
                return h >= 0.0F && h < 20.0F;
            }
        }

        private void carve(double r0, double r1) {
            RandomSource rnd = this.level.random;
            float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
            boolean molten = this.e >= 70.0;
            int r = (int)Math.ceil(r1);
            int cx = Mth.floor(this.c.x);
            int cy = Mth.floor(this.c.y);
            int cz = Mth.floor(this.c.z);
            MutableBlockPos m = new MutableBlockPos();
            // only so much of the bowl flies as rubble; the rest is simply gone
            int budget = 120 + (int)Math.min(330.0, this.e * 1.5);

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt((double)(dx * dx + dz * dz));
                    if (dist >= r1 || dist < r0 || !this.level.hasChunk(cx + dx >> 4, cz + dz >> 4) || !this.sameSide(cx + dx + 0.5, cz + dz + 0.5)) {
                        continue;
                    }

                    double h = dist / this.craterR;
                    int dig = (int)Math.round(this.depth * (1.0 - h * h) + rnd.nextDouble() * 1.2);
                    int top = 1 + (int)(this.dome * (1.0 - h * h));
                    Vec3 out = dist < 0.01 ? new Vec3(0.0, 1.0, 0.0) : new Vec3((double)dx / dist, 0.0, (double)dz / dist);

                    for (int dy = top; dy >= 1 - dig; dy--) {
                        m.set(cx + dx, cy + dy, cz + dz);
                        BlockState s = this.level.getBlockState(m);
                        if (s.isAir()) {
                            continue;
                        }

                        if (this.breakable(s, m)) {
                            if (this.thrown < budget && rnd.nextFloat() < 0.3F) {
                                this.thrown++;
                                // ejecta leave the bowl at a steep angle and rain down around the rim
                                double kick = 0.4 + Math.min(1.2, this.e * 0.01);
                                Vec3 v = out.scale(0.5 + rnd.nextDouble() * kick).add(0.0, 0.45 + rnd.nextDouble() * kick * 0.9, 0.0);
                                Destruction.smash(this.level, m.immutable(), s, v, rubble, this.e > 40.0 ? 2 : 1);
                            } else {
                                this.level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                                Rebuild.mark(this.level, m);
                            }
                        } else if (!s.getFluidState().isEmpty() && dy <= 0 || s.getCollisionShape(this.level, m).isEmpty() && s.getDestroySpeed(this.level, m) >= 0.0F) {
                            this.level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                            Rebuild.mark(this.level, m);
                        }
                    }

                    m.set(cx + dx, cy - dig, cz + dz);
                    BlockState below = this.level.getBlockState(m);
                    if (!below.isAir() && below.getFluidState().isEmpty() && this.breakable(below, m)) {
                        float roll = rnd.nextFloat();
                        BlockState lining;
                        if (molten) {
                            lining = roll < 0.3F
                                ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                : (roll < 0.55F ? Blocks.BLACKSTONE.defaultBlockState() : (roll < 0.8F ? Blocks.BASALT.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState()));
                        } else {
                            lining = roll < 0.45F
                                ? Blocks.COARSE_DIRT.defaultBlockState()
                                : (roll < 0.75F ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                        }

                        this.level.setBlock(m, lining, 3);
                        Rebuild.mark(this.level, m);
                        if (molten && rnd.nextFloat() < 0.12F && TakemConfig.DESTRUCTION_FIRES.get()) {
                            m.move(0, 1, 0);
                            if (this.level.getBlockState(m).isAir()) {
                                Destruction.flame(this.level, m);
                                Rebuild.mark(this.level, m);
                            }
                        }
                    }
                }
            }

            if (r1 >= this.craterR) {
                this.rim();
            }
        }

        private void rim() {
            RandomSource rnd = this.level.random;
            MutableBlockPos m = new MutableBlockPos();
            int rimN = (int)(30.0 + this.craterR * 6.0);

            for (int i = 0; i < rimN; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double d = this.craterR * (0.95 + rnd.nextDouble() * 0.45);
                int x = Mth.floor(this.c.x + Math.cos(a) * d);
                int z = Mth.floor(this.c.z + Math.sin(a) * d);
                if (!this.level.hasChunk(x >> 4, z >> 4) || !this.sameSide(x + 0.5, z + 0.5)) {
                    continue;
                }

                int y = this.level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs((double)y - this.c.y) > this.craterR) {
                    continue;
                }

                int pile = 1 + rnd.nextInt(Math.max(1, (int)(this.craterR / 8.0) + 1));
                for (int k = 0; k < pile; k++) {
                    m.set(x, y + k, z);
                    if (this.level.getBlockState(m).isAir()) {
                        BlockState s = this.e >= 70.0 && rnd.nextFloat() < 0.3F
                            ? Blocks.BLACKSTONE.defaultBlockState()
                            : (rnd.nextFloat() < 0.5F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                        this.level.setBlock(m, s, 3);
                        Rebuild.mark(this.level, m);
                    }
                }
            }
        }

        private void flatten(double r0, double r1) {
            if (this.flattened >= this.flattenCap) {
                return;
            }

            RandomSource rnd = this.level.random;
            float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue() * 0.6F;
            double shock = (1.0 + 0.25 * (double)this.shockRank) * this.demo;
            int budget = Math.min(this.flattenCap - this.flattened, 160 + (int)Math.min(420.0, this.e * 1.6));
            int cx = Mth.floor(this.c.x);
            int cy = Mth.floor(this.c.y);
            int cz = Mth.floor(this.c.z);
            MutableBlockPos m = new MutableBlockPos();
            double circumference = Math.PI * 2.0 * r1;
            int samples = Mth.clamp((int)(circumference * Math.max(1.0, r1 - r0) * 1.2), 16, 2400);

            for (int i = 0; i < samples && budget > 0; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double d = r0 + rnd.nextDouble() * (r1 - r0);
                int x = cx + Mth.floor(Math.cos(a) * d);
                int z = cz + Mth.floor(Math.sin(a) * d);
                if (!this.level.hasChunk(x >> 4, z >> 4) || !this.sameSide(x + 0.5, z + 0.5)) {
                    continue;
                }

                double f = 1.0 - d / this.ringMax;
                double reach = (2.0 + Math.min(40.0, this.e * 0.16) * f * f) * shock;
                double chance = Math.min(1.0, f * 1.35 * shock);
                Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));

                for (int y = cy - 1; (double)y <= (double)cy + reach && budget > 0; y++) {
                    m.set(x, y, z);
                    BlockState s = this.level.getBlockState(m);
                    if (!s.isAir() && !Destruction.isWallStone(s) && Destruction.canSmash(this.level, m, s, 6.0F) && rnd.nextDouble() < chance) {
                        double kick = Math.min(1.6, 0.45 + this.e * 0.014 * f);
                        Vec3 v = out.scale(kick * (0.6 + rnd.nextDouble() * 0.6)).add(0.0, 0.25 + rnd.nextDouble() * kick * 0.6, 0.0);
                        Destruction.smash(this.level, m.immutable(), s, v, rubble, kick > 1.3 ? 1 : 0);
                        budget--;
                        this.flattened++;
                    }
                }
            }
        }

        private void collapseRing(double r) {
            int n = Mth.clamp((int)(r * Math.PI * 2.0 / 18.0), 3, 16);
            double off = this.level.random.nextDouble() * Math.PI * 2.0;
            int span = Mth.clamp((int)(14.0 * this.demo), 12, 24);

            for (int i = 0; i < n; i++) {
                double a = off + (double)i * Math.PI * 2.0 / (double)n;
                double x = this.c.x + Math.cos(a) * r;
                double z = this.c.z + Math.sin(a) * r;
                if (this.sameSide(x, z)) {
                    Destruction.requestCollapse(this.level, BlockPos.containing(x, this.c.y + 2.0, z), span, 72, 6);
                }
            }
        }

        private void ejecta() {
            RandomSource rnd = this.level.random;
            ServerPlayer player = this.player();
            if (this.blocks) {
                // a few great clods of ground thrown out of the crater
                int lumps = (int)Math.min(10.0, 2.0 + this.e / 16.0);
                for (int i = 0; i < lumps; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2.0;
                    double x = this.c.x + Math.cos(a) * this.craterR * 0.8;
                    double z = this.c.z + Math.sin(a) * this.craterR * 0.8;
                    if (!this.sameSide(x, z)) {
                        continue;
                    }

                    FallingSectionEntity lump = Chunks.rip(this.level, BlockPos.containing(x, this.c.y - 1.0, z), 2 + (this.e > 80.0 ? 1 : 0), 1, 6);
                    if (lump != null) {
                        Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));
                        double kick = Math.min(2.0, 0.5 + this.e * 0.012);
                        lump.launch(out.scale(kick * (0.6 + rnd.nextDouble() * 0.5)).add(0.0, kick * (0.8 + rnd.nextDouble() * 0.6), 0.0), Chunks.tumble(rnd, 0.35F));
                        this.level.addFreshEntity(lump);
                    }
                }
            }

            if (this.e >= 60.0) {
                // and boulders arcing out past the blast - never over the Wall
                int rocks = (int)Math.min(this.extinction ? 16.0 : 10.0, (this.e - 40.0) / 10.0);
                for (int ix = 0; ix < rocks; ix++) {
                    double a = rnd.nextDouble() * Math.PI * 2.0;
                    double dist = this.R * (0.9 + rnd.nextDouble() * 1.1);
                    if (!this.sameSide(this.c.x + Math.cos(a) * dist, this.c.z + Math.sin(a) * dist)) {
                        continue;
                    }

                    double t = 34.0 + rnd.nextDouble() * 22.0;
                    Vec3 from = this.c.add(Math.cos(a) * 3.0, 3.0, Math.sin(a) * 3.0);
                    Vec3 v = new Vec3(Math.cos(a) * dist / t, 0.02 * t, Math.sin(a) * dist / t);
                    FallingSectionEntity rock = FallingSectionEntity.boulder(this.level, from, 1.2F + rnd.nextFloat() * 1.0F, player != null ? player.getUUID() : null);
                    rock.launch(v, Chunks.tumble(rnd, 0.4F));
                    this.level.addFreshEntity(rock);
                }
            }
        }
    }
}
