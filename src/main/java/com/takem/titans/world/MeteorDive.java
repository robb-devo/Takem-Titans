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
        return extinction ? r * 1.25 : r;
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
        // no ceiling: past 48 blocks the crater keeps growing with the height of the fall, only a little slower, so a dive
        // from a thousand blocks leaves a far bigger hole than one from three hundred and five thousand a bigger one still
        double natural = Math.max(2.5, R * 0.5 * demo);
        double craterR = natural <= 48.0 ? natural : 48.0 + Math.pow(natural - 48.0, 0.8);
        double ringMax = R * (1.45 + 0.35 * (double)shockRank);
        float k = (float)Math.min(6.0, e / 35.0);
        Vec3 c = at;
        boolean red = OdmServer.isRed(player, d);
        d.immuneTicks = Math.max(d.immuneTicks, 70 + (int)Math.min(80.0, e));
        d.diveGrace = Math.max(d.diveGrace, 40);
        Fx.send(level, 25, c.x, c.y + 0.1, c.z, (float)Math.min(R, 420.0), k, red ? 1.0F : 0.0F, 0.0F, 400.0 + R * 3.0);
        if (e >= 40.0) {
            Fx.send(level, 26, c.x, c.y + 0.1, c.z, (float)R, (float)e, extinction ? 1.0F : 0.0F, red ? 1.0F : 0.0F, 600.0 + R * 4.0);
        }

        float vol = (float)Math.min(60.0, 4.0 + e * 0.12);
        level.playSound(null, c.x, c.y, c.z, ModSounds.METEOR_SLAM.get(), SoundSource.PLAYERS, vol, 0.8F + rnd.nextFloat() * 0.15F);
        level.playSound(null, c.x, c.y, c.z, ModSounds.BOULDER_IMPACT.get(), SoundSource.PLAYERS, vol * 0.8F, 0.6F);
        if (e > 25.0) {
            level.playSound(null, c.x, c.y, c.z, ModSounds.NAPE_THUNDER.get(), SoundSource.PLAYERS, vol, 0.55F);
        }

        if (e > 55.0) {
            level.playSound(null, c.x, c.y, c.z, ModSounds.RIFT_DETONATE.get(), SoundSource.PLAYERS, vol, 0.65F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, vol, 0.45F);
        }

        if (e > 90.0) {
            level.playSound(null, c.x, c.y, c.z, ModSounds.COLOSSAL_ARRIVAL.get(), SoundSource.PLAYERS, vol, 0.7F);
            level.playSound(null, c.x, c.y, c.z, ModSounds.RUMBLING_QUAKE.get(), SoundSource.PLAYERS, vol, 0.8F);
        }

        AbstractTitan.shakeNearby(level, c, 40.0 + R * 4.0, (float)Math.min(2.6, 0.5 + e / 70.0), 26 + (int)Math.min(100.0, e * 0.4));
        if (e > 28.0) {
            PacketDistributor.sendToPlayer(player, FeedbackPayload.of(10, 100, (int)Math.min(260.0, 120.0 + e), 0));
            KillCam.slowMo(level.getServer(), (float)Math.max(3.0, 8.0 - e / 30.0), (long)Math.min(1300.0, 300.0 + e * 5.0), 800L);
        }

        if (e > 60.0) {
            for (int i = 0; i < Math.min(48, (int)(e / 20.0)); i++) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
                    double dd = R * (0.3 + rnd.nextDouble() * 0.9);
                    bolt.moveTo(c.x + Math.cos(a) * dd, c.y, c.z + Math.sin(a) * dd);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
        }

        TownLife.alarm(level, c, ringMax * 1.6 + 30.0, 0);
        MeteorDive.Impact impact = new MeteorDive.Impact(level, player, c, e, R, craterR, ringMax, demo, extinction, shockRank);
        impact.core();
        boolean blocks = TakemConfig.METEOR_DIVE_BREAKS_BLOCKS.get();
        impact.blocks = blocks;
        if (blocks) {
            Destruction.glassBurst(level, c, R * 2.2);
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
        final boolean walls;
        boolean blocks = true;
        double carved;
        double ring;
        int age;
        int thrown;
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
            this.walls = e >= 70.0 && TakemConfig.METEOR_BREAKS_WALLS.get();
            this.nextCollapse = Math.max(10.0, craterR);
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
                double height = 1.5 + Math.min(this.craterR * 0.32, 30.0) + Math.min(80.0, this.craterR * 0.55 + Math.min(30.0, this.e * 0.15));
                // a big crater opens over seconds, ring by ring, so the server keeps up and you watch it grow
                double step = Mth.clamp(16000.0 / (6.283185307179586 * Math.max(4.0, this.carved + 2.0) * height), 0.25, 4.0);
                double next = Math.min(this.craterR, this.carved + step);
                this.carve(this.carved, next);
                this.carved = next;
            }

            if (this.ring < this.ringMax) {
                double speed = Math.min(11.0, 2.6 + this.e * 0.015);
                double prev = this.ring;
                this.ring = Math.min(this.ringMax, this.ring + speed);
                this.band(prev, this.ring);
                if (this.blocks) {
                    this.flatten(prev, this.ring);
                    if (this.ring >= this.nextCollapse) {
                        this.collapseRing(this.ring);
                        this.nextCollapse = this.nextCollapse + Math.max(14.0, this.ringMax / 14.0);
                    }
                }

                if (this.age % 3 == 1) {
                    Fx.send(this.level, 22, this.c.x, this.c.y, this.c.z, (float)this.ring, (float)Math.min(14.0, 3.0 + this.e * 0.06), 0.0F, 0.0F, 400.0);
                }
            }

            boolean done = this.ring >= this.ringMax && (!this.blocks || this.carved >= this.craterR);
            if (done && this.blocks) {
                this.collapseRing(this.R * 0.7);
                if (this.e > 45.0 && TakemConfig.DESTRUCTION_FIRES.get()) {
                    Boom.ignite(this.level, this.c, this.R * 0.9, (int)Math.min(220.0, this.e * 0.5), this.level.random);
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
                if (ent != player && !(ent instanceof TitanPart) && !this.hit.contains(ent.getId())) {
                    double dx = ent.getX() - this.c.x;
                    double dz = ent.getZ() - this.c.z;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (!(dist > r1) && !(dist < r0 - 0.001)) {
                        this.hit.add(ent.getId());
                        double f = Mth.clamp(1.0 - dist / this.ringMax, 0.0, 1.0);
                        double shock = 1.0 + 0.25 * (double)this.shockRank;
                        Vec3 out = new Vec3(dx, 0.0, dz);
                        out = out.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : out.normalize();
                        double fling = Math.min(7.0, (0.6 + this.e * 0.02) * f * shock);
                        if (ent instanceof AbstractTitan) {
                            AbstractTitan t = (AbstractTitan)ent;
                            if (!(t instanceof RumblingTitan)) {
                                this.titan(t, dist, f, player, src);
                            }
                        } else if (ent instanceof FallingSectionEntity fs) {
                            fs.shove(out.scale(fling * 0.6).add(0.0, 0.3 + fling * 0.4, 0.0));
                        } else if (ent instanceof DebrisEntity) {
                            double f2 = Math.min(2.2, fling);
                            ent.setDeltaMovement(ent.getDeltaMovement().add(out.scale(f2)).add(0.0, 0.3 + f2 * 0.4, 0.0));
                        } else {
                            if (ent instanceof Player) {
                                Player p = (Player)ent;
                                if (p.isCreative() || p.isSpectator()) {
                                    continue;
                                }
                            }

                            ent.setDeltaMovement(ent.getDeltaMovement().add(out.scale(fling)).add(0.0, Math.min(3.5, 0.5 + fling * 0.55), 0.0));
                            ent.hurtMarked = true;
                            if (ent instanceof LivingEntity) {
                                LivingEntity l = (LivingEntity)ent;
                                if (ent instanceof TownsfolkEntity) {
                                    TownsfolkEntity tf = (TownsfolkEntity)ent;
                                    if (tf.isStoryCharacter()) {
                                        continue;
                                    }
                                }

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
            if (!Chunks.takes(this.level, p, s, 1)) {
                return false;
            } else {
                float h = s.getDestroySpeed(this.level, p);
                return this.walls || !(h >= 20.0F);
            }
        }

        private void carve(double r0, double r1) {
            RandomSource rnd = this.level.random;
            float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
            double depth = 1.5 + Math.min(this.craterR * 0.32, 30.0);
            double dome = Math.min(80.0, this.craterR * 0.55 + Math.min(30.0, this.e * 0.15));
            boolean molten = this.e >= 70.0;
            int r = (int)Math.ceil(r1);
            int cx = Mth.floor(this.c.x);
            int cy = Mth.floor(this.c.y);
            int cz = Mth.floor(this.c.z);
            MutableBlockPos m = new MutableBlockPos();
            int budget = 520 + (int)Math.min(1600.0, this.e * 4.0);

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt((double)(dx * dx + dz * dz));
                    if (!(dist >= r1) && !(dist < r0) && this.level.hasChunk(cx + dx >> 4, cz + dz >> 4)) {
                        double h = dist / this.craterR;
                        int dig = (int)Math.round(depth * (1.0 - h * h) + rnd.nextDouble() * 1.2);
                        int top = 1 + (int)(dome * (1.0 - h * h));
                        Vec3 out = dist < 0.01 ? new Vec3(0.0, 1.0, 0.0) : new Vec3((double)dx / dist, 0.0, (double)dz / dist);
                        int floor = -2147483648;

                        for (int dy = top; dy >= 1 - dig; dy--) {
                            m.set(cx + dx, cy + dy, cz + dz);
                            BlockState s = this.level.getBlockState(m);
                            if (!s.isAir() && this.breakable(s, m)) {
                                if (this.thrown < budget && rnd.nextFloat() < 0.4F) {
                                    this.thrown++;
                                    // ejecta leave the bowl at a steep angle and rain down around the rim
                                    double kick = 0.4 + Math.min(1.4, this.e * 0.012);
                                    Vec3 v = out.scale(0.5 + rnd.nextDouble() * kick).add(0.0, 0.45 + rnd.nextDouble() * kick * 0.9, 0.0);
                                    Destruction.smash(this.level, m.immutable(), s, v, rubble, this.e > 40.0 ? 2 : 1);
                                } else {
                                    this.level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                                    Rebuild.mark(this.level, m);
                                }
                            } else if (!s.isAir() && (!s.getFluidState().isEmpty() && dy <= 0 || s.getCollisionShape(this.level, m).isEmpty() && s.getDestroySpeed(this.level, m) >= 0.0F)) {
                                this.level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                                Rebuild.mark(this.level, m);
                            }
                        }

                        floor = cy - dig;
                        m.set(cx + dx, floor, cz + dz);
                        BlockState below = this.level.getBlockState(m);
                        if (!below.isAir() && below.getFluidState().isEmpty() && this.breakable(below, m)) {
                            BlockState lining;
                            if (molten) {
                                float roll = rnd.nextFloat();
                                lining = roll < 0.3F
                                    ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                    : (
                                        roll < 0.55F
                                            ? Blocks.BLACKSTONE.defaultBlockState()
                                            : (roll < 0.8F ? Blocks.BASALT.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState())
                                    );
                            } else {
                                float roll = rnd.nextFloat();
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
            }

            if (r1 >= this.craterR) {
                this.rim();
            }
        }

        private void rim() {
            RandomSource rnd = this.level.random;
            MutableBlockPos m = new MutableBlockPos();
            int rimN = (int)(40.0 + this.craterR * 8.0);

            for (int i = 0; i < rimN; i++) {
                double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
                double d = this.craterR * (0.95 + rnd.nextDouble() * 0.45);
                int x = Mth.floor(this.c.x + Math.cos(a) * d);
                int z = Mth.floor(this.c.z + Math.sin(a) * d);
                if (this.level.hasChunk(x >> 4, z >> 4)) {
                    int y = this.level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (!(Math.abs((double)y - this.c.y) > this.craterR)) {
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
            }
        }

        private void flatten(double r0, double r1) {
            RandomSource rnd = this.level.random;
            float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
            double shock = (1.0 + 0.25 * (double)this.shockRank) * this.demo;
            int budget = 1100 + (int)Math.min(2600.0, this.e * 6.0);
            int cx = Mth.floor(this.c.x);
            int cy = Mth.floor(this.c.y);
            int cz = Mth.floor(this.c.z);
            MutableBlockPos m = new MutableBlockPos();
            double circumference = 6.283185307179586 * r1;
            int samples = Mth.clamp((int)(circumference * Math.max(1.0, r1 - r0) * 1.2), 16, 6000);

            for (int i = 0; i < samples && budget > 0; i++) {
                double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
                double d = r0 + rnd.nextDouble() * (r1 - r0);
                int x = cx + Mth.floor(Math.cos(a) * d);
                int z = cz + Mth.floor(Math.sin(a) * d);
                if (this.level.hasChunk(x >> 4, z >> 4)) {
                    double f = 1.0 - d / this.ringMax;
                    double reach = (2.0 + Math.min(60.0, this.e * 0.22) * f * f) * shock;
                    double chance = Math.min(1.0, f * 1.35 * shock);
                    Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));

                    for (int y = cy - 1; (double)y <= (double)cy + reach && budget > 0; y++) {
                        m.set(x, y, z);
                        BlockState s = this.level.getBlockState(m);
                        if (!s.isAir() && Destruction.canSmash(this.level, m, s, 6.0F) && rnd.nextDouble() < chance) {
                            double kick = Math.min(1.8, 0.45 + this.e * 0.016 * f);
                            Vec3 v = out.scale(kick * (0.6 + rnd.nextDouble() * 0.6)).add(0.0, 0.25 + rnd.nextDouble() * kick * 0.6, 0.0);
                            Destruction.smash(this.level, m.immutable(), s, v, rubble, kick > 1.4 ? 1 : 0);
                            budget--;
                        }
                    }
                }
            }
        }

        private void collapseRing(double r) {
            int n = Mth.clamp((int)(r * 3.141592653589793 * 2.0 / 18.0), 3, 20);
            double off = this.level.random.nextDouble() * 3.141592653589793 * 2.0;
            int span = Mth.clamp((int)(14.0 * this.demo), 12, 24);

            for (int i = 0; i < n; i++) {
                double a = off + (double)i * 3.141592653589793 * 2.0 / (double)n;
                BlockPos p = BlockPos.containing(this.c.x + Math.cos(a) * r, this.c.y + 2.0, this.c.z + Math.sin(a) * r);
                Destruction.requestCollapse(this.level, p, span, 72, 6);
            }
        }

        private void ejecta() {
            RandomSource rnd = this.level.random;
            ServerPlayer player = this.player();
            if (this.blocks) {
                int lumps = (int)Math.min(28.0, 2.0 + this.e / 12.0);

                for (int i = 0; i < lumps; i++) {
                    double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
                    BlockPos at = BlockPos.containing(this.c.x + Math.cos(a) * this.craterR * 0.8, this.c.y - 1.0, this.c.z + Math.sin(a) * this.craterR * 0.8);
                    FallingSectionEntity lump = Chunks.rip(this.level, at, 2 + (this.e > 80.0 ? 1 : 0), 1, 6);
                    if (lump != null) {
                        Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));
                        double kick = Math.min(2.4, 0.5 + this.e * 0.015);
                        lump.launch(
                            out.scale(kick * (0.6 + rnd.nextDouble() * 0.5)).add(0.0, kick * (0.8 + rnd.nextDouble() * 0.6), 0.0), Chunks.tumble(rnd, 0.35F)
                        );
                        this.level.addFreshEntity(lump);
                    }
                }
            }

            if (this.e >= 60.0) {
                int rocks = (int)Math.min(this.extinction ? 64.0 : 40.0, (this.e - 40.0) / 6.0);

                for (int ix = 0; ix < rocks; ix++) {
                    double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
                    double dist = this.R * (0.9 + rnd.nextDouble() * 1.4);
                    double t = 34.0 + rnd.nextDouble() * 22.0;
                    Vec3 from = this.c.add(Math.cos(a) * 3.0, 3.0, Math.sin(a) * 3.0);
                    Vec3 v = new Vec3(Math.cos(a) * dist / t, 0.02 * t, Math.sin(a) * dist / t);
                    FallingSectionEntity rock = FallingSectionEntity.boulder(
                        this.level, from, 1.2F + rnd.nextFloat() * 1.0F, player != null ? player.getUUID() : null
                    );
                    rock.launch(v, Chunks.tumble(rnd, 0.4F));
                    this.level.addFreshEntity(rock);
                }
            }
        }
    }
}
