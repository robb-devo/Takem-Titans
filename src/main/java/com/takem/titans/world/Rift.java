package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Rift {
    private static final int PULL = 150;
    private static final int COLLAPSE = 22;
    private static final double WAVE_SPEED = 3.0;
    private static final int UPDATE = 50;
    private static final List<Rift.Running> RUNNING = new ArrayList<>();

    private Rift() {
    }

    public static void start(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        float R = TakemConfig.RIFT_RADIUS.get().floatValue();
        Vec3 c = at.add(0.0, (double)R * 0.45 + 3.0, 0.0);
        RUNNING.add(new Rift.Running(level, c, owner == null ? null : owner.getUUID()));
        Fx.send(level, 19, c.x, c.y, c.z, R, 0.0F, 172.0F, 0.0F, 600.0);
        level.playSound(null, c.x, c.y, c.z, ModSounds.RIFT_OPEN.get(), SoundSource.HOSTILE, 10.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && !RUNNING.isEmpty()) {
            for (int i = 0; i < RUNNING.size(); i++) {
                Rift.Running r = RUNNING.get(i);
                if (r.level == level && !step(r)) {
                    Attractors.remove(r.pull);
                    RUNNING.remove(i--);
                }
            }

            return;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNNING.clear();
        Attractors.clear();
    }

    private static boolean step(Rift.Running r) {
        ServerLevel level = r.level;
        RandomSource rnd = level.random;
        int t = r.t++;
        Vec3 c = r.c;
        if (t < 150) {
            float k = (float)t / 150.0F;
            r.pull.strength = 0.04 + 0.08 * (double)k;
            if (t % 10 == 0) {
                Fx.send(level, 19, c, r.radius, 1.0F, 400.0);
            }

            if (t % 40 == 0) {
                level.playSound(null, c.x, c.y, c.z, ModSounds.RIFT_HUM.get(), SoundSource.HOSTILE, 7.0F, 0.9F + k * 0.3F);
            }

            if (t % 8 == 0) {
                AbstractTitan.shakeNearby(level, c, r.reach * 2.5, 0.15F + 0.35F * k, 10);
            }

            if (r.blocks) {
                tear(r, k, rnd);
            }

            suck(r, k);
            if (t == 110) {
                level.playSound(null, c.x, c.y, c.z, ModSounds.RIFT_IMPLODE.get(), SoundSource.HOSTILE, 10.0F, 1.0F);
            }

            return true;
        } else {
            int kx = t - 150;
            if (kx == 0) {
                Fx.send(level, 19, c, r.radius, 2.0F, 500.0);
                r.pull.strength = 0.25;
            }

            if (kx < 22) {
                suck(r, 1.0F);
                return true;
            } else {
                if (kx == 22) {
                    detonate(r, rnd);
                }

                if (r.wave >= 0.0 && r.wave < r.reach * 1.3) {
                    wave(r, rnd);
                }

                return r.wave >= 0.0 && r.wave < r.reach * 1.3;
            }
        }
    }

    private static void tear(Rift.Running r, float k, RandomSource rnd) {
        ServerLevel level = r.level;
        Vec3 c = r.c;
        int tries = 20 + (int)(60.0F * k);
        int torn = 0;
        MutableBlockPos m = new MutableBlockPos();

        for (int i = 0; i < tries && (float)torn < 8.0F + 22.0F * k; i++) {
            double d = (double)r.radius * 0.4 + rnd.nextDouble() * r.reach * (0.45 + 0.4 * (double)k);
            double a = rnd.nextDouble() * 6.2831854820251465;
            double y = c.y - (double)r.radius * 0.9 + rnd.nextDouble() * (double)r.radius * 2.5;
            m.set(Mth.floor(c.x + Math.cos(a) * d), Mth.floor(y), Mth.floor(c.z + Math.sin(a) * d));
            BlockState s = level.getBlockState(m);
            if (!s.isAir()) {
                boolean ground = RumblingTitan.isGround(s);
                if (!ground || !(k < 0.45F) && !(d > (double)r.radius * (0.8 + (double)k))) {
                    if (!Chunks.takes(level, m, s, 1)) {
                        if (s.is(BlockTags.LEAVES) || s.canBeReplaced()) {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                            Rebuild.mark(level, m);
                        }
                    } else {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                        Rebuild.mark(level, m);
                        Vec3 p = Vec3.atCenterOf(m);
                        Vec3 in = c.subtract(p).normalize();
                        Vec3 v = in.scale(0.3 + 0.4 * (double)k).add(0.0, 0.35 + rnd.nextDouble() * 0.3, 0.0);
                        Destruction.throwOrSettle(level, p, s, v, 0.0F, 0);
                        torn++;
                    }
                }
            }
        }

        if (r.t % Math.max(4, 12 - (int)(8.0F * k)) == 0) {
            double d = (double)r.radius * 0.8 + rnd.nextDouble() * r.reach * 0.6;
            double a = rnd.nextDouble() * 6.2831854820251465;
            BlockPos at = BlockPos.containing(
                c.x + Math.cos(a) * d, c.y - (double)r.radius * 0.5 + rnd.nextDouble() * (double)r.radius * 1.5, c.z + Math.sin(a) * d
            );
            FallingSectionEntity lump = Chunks.rip(level, at, 2 + rnd.nextInt(2), k > 0.5F ? 1 : 0, 10);
            if (lump != null) {
                Vec3 in = c.subtract(lump.position()).normalize();
                lump.launch(in.scale(0.4).add(0.0, 0.6, 0.0), Chunks.tumble(rnd, 0.4F));
                level.addFreshEntity(lump);
            }
        }

        if (r.t % 15 == 0) {
            Destruction.requestCollapse(level, BlockPos.containing(c.x, c.y - (double)r.radius * 0.5, c.z), (int)Math.min(24.0, r.reach * 0.7), 64, 10);
        }
    }

    private static void suck(Rift.Running r, float k) {
        ServerLevel level = r.level;
        Vec3 c = r.c;
        DamageSource src = r.source();

        for (Entity e : level.getEntities((Entity)null, new AABB(c, c).inflate(r.reach), Entity::isAlive)) {
            if (!(e instanceof TitanPart) && !(e instanceof FallingSectionEntity) && !(e instanceof DebrisEntity)) {
                if (e instanceof Player) {
                    Player p = (Player)e;
                    if (p.isSpectator()) {
                        continue;
                    }
                }

                Vec3 to = c.subtract(e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0));
                double d = to.length();
                if (!(d > r.reach) && !(d < 0.001)) {
                    double f = (1.0 - d / r.reach) * (0.06 + 0.12 * (double)k);
                    Vec3 in = to.scale(1.0 / d);
                    Vec3 swirl = new Vec3(-in.z, 0.0, in.x);
                    double scale = e instanceof AbstractTitan ? 0.25 : 1.0;
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.92).add(in.scale(f * scale)).add(swirl.scale(f * 0.8 * scale)).add(0.0, 0.02 * scale, 0.0));
                    e.hurtMarked = true;
                    e.fallDistance = 0.0F;
                    if (e instanceof ItemEntity && d < 3.0) {
                        e.discard();
                    } else if (d < 4.0 && r.t % 5 == 0) {
                        if (e instanceof Player) {
                            Player p = (Player)e;
                            if (p.isCreative()) {
                                continue;
                            }
                        }

                        if (e instanceof LivingEntity living) {
                            living.hurt(src, e instanceof AbstractTitan ? 40.0F : 6.0F);
                        }
                    }
                }
            }
        }
    }

    private static void detonate(Rift.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        Vec3 c = r.c;
        float R = r.radius;
        Attractors.remove(r.pull);
        Fx.send(level, 19, c, R, 3.0F, 900.0);
        level.playSound(null, c.x, c.y, c.z, ModSounds.RIFT_DETONATE.get(), SoundSource.HOSTILE, 16.0F, 1.0F);
        KillCam.slowMo(level.getServer(), 5.0F, 700L, 1400L);

        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(c);
            if (d < 500.0) {
                float s = (float)Mth.clamp(1.2 - d / 400.0, 0.3, 1.0);
                PacketDistributor.sendToPlayer(p, FeedbackPayload.of(10, Math.round(s * 100.0F), 500, 100));
            }
        }

        AbstractTitan.shakeNearby(level, c, 400.0, 1.2F, 40);
        DamageSource src = r.source();

        for (Entity e : level.getEntities((Entity)null, new AABB(c, c).inflate((double)R * 1.4), Entity::isAlive)) {
            if (!(e instanceof TitanPart)
                && !(e instanceof FallingSectionEntity)
                && !(e instanceof DebrisEntity)
                && !(e instanceof RumblingTitan)
                && !(e.position().distanceTo(c) > (double)R * 1.4)) {
                if (e instanceof AbstractTitan titan) {
                    titan.vaporise(src);
                } else if (e instanceof Player) {
                    Player px = (Player)e;
                    if (!px.isCreative() && !px.isSpectator()) {
                        px.hurt(src, 5000.0F);
                    }
                } else if (e instanceof LivingEntity living) {
                    living.hurt(src, 5000.0F);
                } else if (e instanceof ItemEntity) {
                    e.discard();
                }
            }
        }

        if (r.blocks) {
            carve(r, rnd);
            eject(r, rnd);
        }

        Destruction.glassBurst(level, c, (double)R * 3.5);
        r.wave = (double)R * 0.8;
    }

    private static void carve(Rift.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        Vec3 c = r.c;
        float R = r.radius;
        int ri = Mth.ceil(R);
        BlockPos center = BlockPos.containing(c);
        MutableBlockPos m = new MutableBlockPos();
        int thrown = 0;

        for (int dy = -ri; dy <= ri; dy++) {
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt((double)(dx * dx + dy * dy + dz * dz));
                    if (!(d > (double)R + rnd.nextDouble() * 1.2)) {
                        m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        BlockState s = level.getBlockState(m);
                        if (!s.isAir() && !(s.getDestroySpeed(level, m) < 0.0F)) {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                            Rebuild.mark(level, m);
                            if (thrown < 80 && rnd.nextFloat() < 0.04F && !s.getCollisionShape(level, m).isEmpty()) {
                                Vec3 p = Vec3.atCenterOf(m);
                                Vec3 out = p.subtract(c).normalize();
                                Destruction.throwOrSettle(level, p, s, out.scale(1.2 + rnd.nextDouble() * 1.4).add(0.0, 0.5, 0.0), 0.3F, 3);
                                thrown++;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void eject(Rift.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        Vec3 c = r.c;
        List<BlockState> got = r.pull.swallowed;
        int i = 0;

        for (int lumps = 0; lumps < 18 && got.size() - i >= 20; lumps++) {
            int n = Math.min(got.size() - i, 20 + rnd.nextInt(40));
            List<BlockState> part = got.subList(i, i + n);
            i += n;
            FallingSectionEntity lump = packed(level, c, part, rnd);
            if (lump == null) {
                break;
            }

            Vec3 dir = randomOut(rnd);
            lump.launch(dir.scale(1.0 + rnd.nextDouble() * 1.2), Chunks.tumble(rnd, 0.45F));
            level.addFreshEntity(lump);
        }

        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();

        for (int k = 0; i < got.size() && k < 400; k++) {
            Vec3 dir = randomOut(rnd);
            Destruction.throwOrSettle(level, c.add(dir.scale(1.5)), got.get(i), dir.scale(1.2 + rnd.nextDouble() * 1.8), rubble, 2);
            i++;
        }

        got.clear();
    }

    private static Vec3 randomOut(RandomSource rnd) {
        double u = rnd.nextDouble() * 1.4 - 0.4;
        double a = rnd.nextDouble() * 6.2831854820251465;
        double s = Math.sqrt(Math.max(0.0, 1.0 - u * u));
        return new Vec3(s * Math.cos(a), u, s * Math.sin(a));
    }

    @Nullable
    private static FallingSectionEntity packed(ServerLevel level, Vec3 c, List<BlockState> states, RandomSource rnd) {
        int n = states.size();
        if (!FallingSectionEntity.budgetFor(n)) {
            return null;
        } else {
            long[] pos = new long[n];
            BlockState[] st = new BlockState[n];
            int R = 1;

            while ((double)((2 * R + 1) * (2 * R + 1) * (2 * R + 1)) * 0.5 < (double)n) {
                R++;
            }

            BlockPos base = BlockPos.containing(c);
            int k = 0;

            for (int dx = -R; dx <= R && k < n; dx++) {
                for (int dy = -R; dy <= R && k < n; dy++) {
                    for (int dz = -R; dz <= R && k < n; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= R * R + 1) {
                            pos[k] = base.offset(dx, dy, dz).asLong();
                            st[k] = states.get(k);
                            k++;
                        }
                    }
                }
            }

            return k < 8 ? null : FallingSectionEntity.create(level, pos, st, k, null, null, null);
        }
    }

    private static void wave(Rift.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        Vec3 c = r.c;
        double w0 = r.wave;
        double w1 = w0 + 3.0;
        r.wave = w1;
        double outer = r.reach * 1.3;
        DamageSource src = r.source();

        for (Entity e : level.getEntities((Entity)null, new AABB(c, c).inflate(w1 + 2.0, 40.0, w1 + 2.0), Entity::isAlive)) {
            if (!(e instanceof TitanPart) && !(e instanceof FallingSectionEntity) && !(e instanceof DebrisEntity) && !(e instanceof RumblingTitan)) {
                Vec3 to = e.position().subtract(c);
                double d = Math.sqrt(to.x * to.x + to.z * to.z);
                if (!(d < w0) && !(d >= w1)) {
                    double g = 1.0 - Mth.clamp(d / outer, 0.0, 1.0);
                    Vec3 dir = d < 0.1 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(to.x / d, 0.0, to.z / d);
                    if (e instanceof AbstractTitan titan) {
                        titan.hurt(src, (float)(500.0 * g));
                        titan.blastKnockDown(140);
                    } else {
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(3.0 * g + 0.4)).add(0.0, 1.0 * g + 0.3, 0.0));
                        e.hurtMarked = true;
                        if (e instanceof Player) {
                            Player p = (Player)e;
                            if (p.isCreative() || p.isSpectator()) {
                                continue;
                            }
                        }

                        if (e instanceof LivingEntity living) {
                            living.hurt(src, (float)(80.0 * g + 4.0));
                        }
                    }
                }
            }
        }

        if (r.blocks) {
            float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
            int R = Mth.ceil(w1);
            BlockPos center = BlockPos.containing(c);
            int y0 = center.getY() - Mth.ceil(r.radius);
            int y1 = center.getY() + 40;
            MutableBlockPos m = new MutableBlockPos();

            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    double d = Math.sqrt((double)(dx * dx + dz * dz));
                    if (!(d < w0) && !(d >= w1)) {
                        double g = 1.0 - Mth.clamp(d / outer, 0.0, 1.0);
                        Vec3 dir = new Vec3((double)dx / d, 0.0, (double)dz / d);

                        for (int y = y0; y <= y1; y++) {
                            m.set(center.getX() + dx, y, center.getZ() + dz);
                            BlockState s = level.getBlockState(m);
                            if (!s.isAir()) {
                                if (s.is(BlockTags.LEAVES)) {
                                    if ((double)rnd.nextFloat() < g) {
                                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                                        Rebuild.mark(level, m);
                                    }
                                } else if (Destruction.isStructural(level, m, s) && !((double)rnd.nextFloat() >= 0.8 * Math.pow(g, 1.4))) {
                                    Vec3 v = dir.scale((0.6 + 2.4 * g) * (0.7 + rnd.nextDouble() * 0.6)).add(0.0, 0.3 + rnd.nextDouble() * 0.7 * g, 0.0);
                                    Destruction.smash(level, m.immutable(), s, v, rubble, g > 0.5 ? 2 : 0);
                                }
                            }
                        }
                    }
                }
            }

            int step = 10;
            if ((int)(w1 / (double)step) != (int)(w0 / (double)step)) {
                double ring = Math.floor(w1 / (double)step) * (double)step;
                int count = Math.max(6, (int)(6.2831854820251465 * ring / 18.0));

                for (int i = 0; i < count; i++) {
                    double a = (double)(6.2831855F * (float)i / (float)count);
                    Destruction.requestCollapse(
                        level, BlockPos.containing(c.x + Math.cos(a) * ring, c.y - (double)r.radius * 0.5, c.z + Math.sin(a) * ring), 14, 56, 8
                    );
                }
            }
        }
    }

    private static final class Running {
        final ServerLevel level;
        final Vec3 c;
        @Nullable
        final UUID owner;
        final float radius;
        final double reach;
        final boolean blocks;
        final Attractors.Attractor pull;
        int t;
        double wave = -1.0;

        Running(ServerLevel level, Vec3 c, @Nullable UUID owner) {
            this.level = level;
            this.c = c;
            this.owner = owner;
            this.radius = TakemConfig.RIFT_RADIUS.get().floatValue();
            this.reach = (double)this.radius * 3.0;
            this.blocks = TakemConfig.RIFT_BREAKS_BLOCKS.get();
            this.pull = Attractors.add(level, c, this.reach, 0.05);
        }

        DamageSource source() {
            Entity o = this.owner == null ? null : this.level.getEntity(this.owner);
            return this.level.damageSources().explosion(null, o);
        }
    }
}
