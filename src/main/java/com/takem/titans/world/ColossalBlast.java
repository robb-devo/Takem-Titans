package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.ColossalTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class ColossalBlast {
    public static final int FUSE = 80;
    private static final int CARVE_TICKS = 20;
    private static final int END = 220;
    private static final double WAVE_SPEED = 3.6;
    private static final int UPDATE = 50;
    private static final List<ColossalBlast.Running> RUNNING = new ArrayList<>();

    private ColossalBlast() {
    }

    public static void start(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        RUNNING.add(new ColossalBlast.Running(level, at, owner == null ? null : owner.getUUID()));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 6.0F, 0.5F);
        Component title = Component.translatable("message.takemtitans.serum_title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component sub = Component.translatable("message.takemtitans.serum_subtitle").withStyle(ChatFormatting.GOLD);

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) < 40000.0) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(4, 50, 16));
                p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && !RUNNING.isEmpty()) {
            for (int i = 0; i < RUNNING.size(); i++) {
                ColossalBlast.Running r = RUNNING.get(i);
                if (r.level == level) {
                    step(r);
                    if (r.t > 220) {
                        RUNNING.remove(i--);
                    }
                }
            }

            return;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNNING.clear();
    }

    private static void step(ColossalBlast.Running r) {
        ServerLevel level = r.level;
        RandomSource rnd = level.random;
        int t = r.t++;
        Vec3 c = r.c;
        if (t < 80) {
            charge(r, t, rnd);
            if (r.blocks) {
                scanLayer(r, rnd);
            }
        } else {
            if (t == 80) {
                detonate(r, rnd);
            }

            int k = t - 80;
            if (k < 20 && r.blocks) {
                carve(r, k, rnd);
            }

            if (r.wave < (double)r.outer) {
                wave(r, rnd);
            }

            if (k == 20 && r.blocks) {
                scorch(r, rnd);
            }

            if (k == 22 && !r.titanSpawned && TakemConfig.SERUM_SPAWNS_COLOSSAL.get()) {
                riseColossal(r);
            }

            if (k > 0 && k % 10 == 0 && k < 120) {
                Destruction.far(
                    level,
                    ModParticles.BIG_STEAM.get(),
                    c.x,
                    c.y - (double)r.depth * 0.5,
                    c.z,
                    30,
                    (double)r.radius * 0.4,
                    3.0,
                    (double)r.radius * 0.4,
                    0.15,
                    400.0
                );
            }
        }
    }

    private static void charge(ColossalBlast.Running r, int t, RandomSource rnd) {
        ServerLevel level = r.level;
        Vec3 c = r.c;
        float p = (float)t / 80.0F;
        if (t % 4 == 0) {
            Fx.send(level, 9, c, p, 0.0F, 400.0);
        }

        int every = p < 0.5F ? 7 : (p < 0.8F ? 4 : 2);
        if (t % every == 0) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            double d = rnd.nextDouble() * (double)(6.0F + 10.0F * p);
            Vec3 at = c.add(Math.cos(a) * d, 0.0, Math.sin(a) * d);
            Fx.send(level, 11, at, 40.0F + rnd.nextFloat() * 40.0F, 0.0F, 400.0);
            if (rnd.nextInt(3) == 0) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 3.0F, 0.8F + rnd.nextFloat() * 0.4F);
            }
        }

        if (t == 10 || t == 40 || t == 62 || t == 74) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(c.add(rnd.nextGaussian() * 3.0, 0.0, rnd.nextGaussian() * 3.0));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }

        int beat = p < 0.4F ? 16 : (p < 0.7F ? 10 : 5);
        if (t % beat == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 6.0F, 0.7F + p * 0.6F);
        }

        if (t % 5 == 0) {
            AbstractTitan.shakeNearby(level, c, 160.0, 0.1F + p * 0.7F, 8);
        }

        if (t == 60) {
            level.playSound(null, c.x, c.y, c.z, ModSounds.COLOSSAL_STEAM.get(), SoundSource.HOSTILE, 10.0F, 0.5F);
        }
    }

    private static void detonate(ColossalBlast.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        Vec3 c = r.c;
        Fx.send(level, 10, c, r.radius, 0.0F, 900.0);
        KillCam.slowMo(level.getServer(), 5.0F, 900L, 1600L);

        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(c);
            if (d < 600.0) {
                float s = (float)Mth.clamp(1.2 - d / 500.0, 0.3, 1.0);
                PacketDistributor.sendToPlayer(p, FeedbackPayload.of(10, Math.round(s * 100.0F), 650, 100));
            }
        }

        double kill = (double)r.radius * 1.1;
        DamageSource src = r.blast();

        for (Entity e : level.getEntities((Entity)null, new AABB(c, c).inflate(kill), ex -> ex.isAlive())) {
            if (!(e.position().distanceTo(c) > kill) && !(e instanceof TitanPart)) {
                if (e instanceof AbstractTitan titan) {
                    titan.vaporise(src);
                } else if (e instanceof Player) {
                    Player px = (Player)e;
                    if (!px.isCreative() && !px.isSpectator()) {
                        px.hurt(src, 5000.0F);
                        px.igniteForSeconds(10.0F);
                    }
                } else if (e instanceof LivingEntity living) {
                    living.hurt(src, 5000.0F);
                } else if (e instanceof ItemEntity || e instanceof DebrisEntity) {
                    e.discard();
                }
            }
        }
    }

    private static double shape(ColossalBlast.Running r, int dx, int dy, int dz) {
        double h = (double)(dx * dx + dz * dz) / (double)(r.radius * r.radius);
        return dy >= 0 ? Math.sqrt(h + (double)(dy * dy) / (double)(r.radius * r.radius)) : Math.sqrt(h + (double)(dy * dy) / (double)(r.depth * r.depth));
    }

    private static void scanLayer(ColossalBlast.Running r, RandomSource rnd) {
        int top = Mth.ceil(r.radius);

        for (int layer = 0; layer < 2 && r.scanY <= top; r.scanY++) {
            int dy = r.scanY;
            int R = Mth.ceil(r.radius);
            MutableBlockPos m = new MutableBlockPos();

            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    double n = shape(r, dx, dy, dz);
                    n += (rnd.nextDouble() - 0.5) * 0.08;
                    if (!(n > 1.0)) {
                        m.set(r.center.getX() + dx, r.center.getY() + dy, r.center.getZ() + dz);
                        BlockState s = r.level.getBlockState(m);
                        if (!s.isAir() && !(s.getDestroySpeed(r.level, m) < 0.0F)) {
                            int bucket = Mth.clamp((int)(Math.max(0.0, n) * 20.0), 0, 19);
                            r.shells[bucket].add(m.asLong());
                        }
                    }
                }
            }

            layer++;
        }
    }

    private static void carve(ColossalBlast.Running r, int k, RandomSource rnd) {
        ServerLevel level = r.level;
        LongArrayList shell = r.shells[k];
        Vec3 c = r.c;
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
        MutableBlockPos m = new MutableBlockPos();
        double f = (double)k / 20.0;

        for (int i = 0; i < shell.size(); i++) {
            m.set(shell.getLong(i));
            BlockState s = level.getBlockState(m);
            if (!s.isAir() && !(s.getDestroySpeed(level, m) < 0.0F)) {
                level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                Rebuild.mark(level, m);
                if (f > 0.5 && rnd.nextFloat() < 0.12F && s.getFluidState().isEmpty()) {
                    Vec3 p = Vec3.atCenterOf(m);
                    Vec3 dir = p.subtract(c);
                    double d = dir.length();
                    dir = d < 0.1 ? new Vec3(0.0, 1.0, 0.0) : dir.scale(1.0 / d);
                    double power = 1.6 + rnd.nextDouble() * 1.8;
                    Vec3 v = new Vec3(dir.x * power, Math.abs(dir.y) * power * 0.5 + 0.7 + rnd.nextDouble() * 1.2, dir.z * power);
                    Destruction.throwOrSettle(level, p, s, v, rubble * 0.5F, 3);
                }
            }
        }

        if (k >= 12 && k % 2 == 0) {
            for (int n = 0; n < 2 && r.wreckage < 10; n++) {
                wreck(r, rnd);
            }
        }

        shell.clear();
        if (k % 4 == 0) {
            Destruction.far(
                level, ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 40, (double)r.radius * f, (double)r.radius * f * 0.5, (double)r.radius * f, 0.1, 400.0
            );
        }
    }

    private static void wreck(ColossalBlast.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        double a = rnd.nextDouble() * 6.2831854820251465;
        double d = (double)r.radius * (1.0 + rnd.nextDouble() * 0.25);
        int cx = Mth.floor(r.c.x + Math.cos(a) * d);
        int cz = Mth.floor(r.c.z + Math.sin(a) * d);
        int cy = r.center.getY() + rnd.nextInt(8) - 1;
        int rad = 2 + rnd.nextInt(2);
        long[] pos = new long[(2 * rad + 1) * (2 * rad + 1) * (2 * rad + 1)];
        BlockState[] st = new BlockState[pos.length];
        int n = 0;
        MutableBlockPos m = new MutableBlockPos();

        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= rad * rad + rnd.nextInt(3)) {
                        m.set(cx + dx, cy + dy, cz + dz);
                        BlockState s = level.getBlockState(m);
                        if (!s.isAir()
                            && s.getFluidState().isEmpty()
                            && !(s.getDestroySpeed(level, m) < 0.0F)
                            && !(s.getDestroySpeed(level, m) > 50.0F)
                            && !s.getCollisionShape(level, m).isEmpty()) {
                            pos[n] = m.asLong();
                            st[n] = s;
                            n++;
                        }
                    }
                }
            }
        }

        r.wreckage++;
        if (n >= 8 && FallingSectionEntity.budgetFor(n)) {
            for (int i = 0; i < n; i++) {
                level.setBlock(BlockPos.of(pos[i]), Blocks.AIR.defaultBlockState(), 50);
                Rebuild.mark(level, BlockPos.of(pos[i]));
            }

            FallingSectionEntity e = FallingSectionEntity.create(level, pos, st, n, null, null, null);
            Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));
            double power = 0.7 + rnd.nextDouble() * 0.9;
            e.launch(
                new Vec3(out.x * power, 1.3 + rnd.nextDouble() * 1.0, out.z * power),
                new Vector3f((rnd.nextFloat() - 0.5F) * 0.3F, (rnd.nextFloat() - 0.5F) * 0.2F, (rnd.nextFloat() - 0.5F) * 0.3F)
            );
            level.addFreshEntity(e);
        }
    }

    private static void scorch(ColossalBlast.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        int R = Mth.ceil((double)r.radius * 1.35);
        MutableBlockPos m = new MutableBlockPos();
        int top = r.center.getY() + Mth.ceil((double)r.radius * 0.4);
        int bottom = r.center.getY() - Mth.ceil(r.depth) - 3;

        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double h = Math.sqrt((double)(dx * dx + dz * dz)) / (double)r.radius;
                if (!(h > 1.35)) {
                    int y = top;
                    m.set(r.center.getX() + dx, top, r.center.getZ() + dz);

                    while (y > bottom && level.getBlockState(m).isAir()) {
                        m.setY(--y);
                    }

                    if (y > bottom) {
                        BlockState s = level.getBlockState(m);
                        if (!(s.getDestroySpeed(level, m) < 0.0F) && s.getFluidState().isEmpty()) {
                            if (h <= 1.02) {
                                float roll = rnd.nextFloat();
                                BlockState glow = roll < 0.3F
                                    ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                    : (
                                        roll < 0.58F
                                            ? Blocks.BLACKSTONE.defaultBlockState()
                                            : (
                                                roll < 0.78F
                                                    ? Blocks.BASALT.defaultBlockState()
                                                    : (
                                                        roll < 0.88F
                                                            ? Blocks.COAL_BLOCK.defaultBlockState()
                                                            : (roll < 0.93F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState())
                                                    )
                                            )
                                    );
                                level.setBlock(m, glow, 50);
                                Rebuild.mark(level, m);
                                if (glow.is(Blocks.NETHERRACK) || rnd.nextFloat() < 0.06F) {
                                    placeFire(level, m.above());
                                }
                            } else if ((double)rnd.nextFloat() < 0.3499999940395355 * (1.35 - h) / 0.35) {
                                if (s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.SAND)) {
                                    level.setBlock(m, rnd.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), 50);
                                    Rebuild.mark(level, m);
                                }

                                if (rnd.nextFloat() < 0.15F) {
                                    placeFire(level, m.above());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void placeFire(ServerLevel level, BlockPos p) {
        if (level.getBlockState(p).isAir()) {
            BlockState fire = BaseFireBlock.getState(level, p);
            if (fire.canSurvive(level, p)) {
                level.setBlock(p, fire, 3);
                Rebuild.mark(level, p);
            }
        }
    }

    private static void wave(ColossalBlast.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        double w0 = r.wave;
        double w1 = Math.min((double)r.outer, (w0 <= 0.0 ? (double)r.radius * 0.6 : w0) + 3.6);
        r.wave = w1;
        Vec3 c = r.c;
        DamageSource src = r.blast();
        AABB box = new AABB(c, c).inflate(w1 + 2.0, 48.0, w1 + 2.0);

        for (Entity e : level.getEntities((Entity)null, box, ex -> ex.isAlive())) {
            if (!(e instanceof TitanPart) && !(e instanceof FallingSectionEntity)) {
                Vec3 to = e.position().subtract(c);
                double d = Math.sqrt(to.x * to.x + to.z * to.z);
                if (!(d < w0) && !(d >= w1)) {
                    double f = Mth.clamp((d - (double)r.radius) / (double)(r.outer - r.radius), 0.0, 1.0);
                    double g = (1.0 - f) * (1.0 - f);
                    Vec3 dir = d < 0.1 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(to.x / d, 0.0, to.z / d);
                    if (e instanceof AbstractTitan) {
                        AbstractTitan titan = (AbstractTitan)e;
                        if (!(e instanceof ColossalTitan)) {
                            if (f < 0.35) {
                                titan.vaporise(src);
                            } else {
                                titan.hurt(src, (float)(400.0 * g));
                                titan.blastKnockDown(120 + (int)(120.0 * g));
                                titan.setDeltaMovement(titan.getDeltaMovement().add(dir.scale(1.2 * g)));
                            }
                        }
                    } else {
                        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                            e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(3.5 * g + 0.4)).add(0.0, 1.2 * g + 0.3, 0.0));
                            e.hurtMarked = true;
                            continue;
                        }

                        if (e instanceof LivingEntity living) {
                            living.hurt(src, (float)(90.0 * g + 4.0));
                            if (g > 0.1) {
                                living.igniteForSeconds((float)(8.0 * g));
                            }
                        }

                        e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(3.5 * g + 0.4)).add(0.0, 1.2 * g + 0.3, 0.0));
                        e.hurtMarked = true;
                    }
                }
            }
        }

        if (r.blocks) {
            float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
            int R = Mth.ceil(w1);
            int y0 = r.center.getY() - 3;
            int y1 = r.center.getY() + 44;
            MutableBlockPos m = new MutableBlockPos();

            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    double d = Math.sqrt((double)(dx * dx + dz * dz));
                    if (!(d < w0) && !(d >= w1) && !(d < (double)r.radius * 0.9)) {
                        double f = Mth.clamp((d - (double)r.radius) / (double)(r.outer - r.radius), 0.0, 1.0);
                        double g = 1.0 - f;
                        Vec3 dir = new Vec3((double)dx / d, 0.0, (double)dz / d);

                        for (int y = y0; y <= y1; y++) {
                            m.set(r.center.getX() + dx, y, r.center.getZ() + dz);
                            BlockState s = level.getBlockState(m);
                            if (!s.isAir()) {
                                int up = y - r.center.getY();
                                if (s.is(BlockTags.LEAVES)) {
                                    if ((double)rnd.nextFloat() < 0.95 * g) {
                                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                                        Rebuild.mark(level, m);
                                    }
                                } else if (s.getCollisionShape(level, m).isEmpty()) {
                                    if (s.getFluidState().isEmpty() && s.canBeReplaced() && (double)rnd.nextFloat() < g) {
                                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                                        Rebuild.mark(level, m);
                                    }
                                } else if (!Destruction.isStructural(level, m, s)) {
                                    if (up >= 0 && f < 0.6 && (double)rnd.nextFloat() < 0.03 * g && level.getBlockState(m.above()).isAir()) {
                                        placeFire(level, m.above());
                                    }
                                } else {
                                    double p = 0.75 * Math.pow(g, 1.6) * (1.0 + (double)Math.max(0, up) * 0.035);
                                    if ((double)rnd.nextFloat() >= p) {
                                        if (f < 0.5 && (double)rnd.nextFloat() < 0.02 && level.getBlockState(m.above()).isAir()) {
                                            placeFire(level, m.above());
                                        }
                                    } else {
                                        double power = 0.6 + 2.6 * g;
                                        Vec3 v = dir.scale(power * (0.7 + rnd.nextDouble() * 0.6)).add(0.0, 0.35 + rnd.nextDouble() * 0.7 * g, 0.0);
                                        Destruction.smash(level, m.immutable(), s, v, rubble, g > 0.5 ? 2 : 0);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            int step = 10;
            if ((int)(w1 / (double)step) != (int)(w0 / (double)step) && w1 > (double)r.radius) {
                double ring = Math.floor(w1 / (double)step) * (double)step;
                int count = Math.max(6, (int)(6.2831854820251465 * ring / 18.0));
                double off = rnd.nextDouble() * 6.2831854820251465;

                for (int i = 0; i < count; i++) {
                    double a = off + (double)(6.2831855F * (float)i / (float)count);
                    Destruction.requestCollapse(level, BlockPos.containing(c.x + Math.cos(a) * ring, c.y + 2.0, c.z + Math.sin(a) * ring), 14, 56, 6);
                }
            }
        }
    }

    private static void riseColossal(ColossalBlast.Running r) {
        r.titanSpawned = true;
        ServerLevel level = r.level;
        Vec3 c = r.c;
        MutableBlockPos m = new MutableBlockPos(r.center.getX(), r.center.getY(), r.center.getZ());
        int floor = r.center.getY() - Mth.ceil(r.depth) - 4;

        while (m.getY() > floor && level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
            m.move(0, -1, 0);
        }

        double y = (double)(m.getY() + 1);
        ColossalTitan titan = ModEntities.COLOSSAL_TITAN.get().create(level);
        if (titan != null) {
            Entity owner = r.owner();
            float yaw = owner != null
                ? (float)(Mth.atan2(owner.getZ() - c.z, owner.getX() - c.x) * 180.0 / 3.1415927410125732) - 90.0F
                : level.random.nextFloat() * 360.0F;
            titan.moveTo(c.x, y, c.z, yaw, 0.0F);
            titan.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(c)), MobSpawnType.EVENT, null);
            titan.yBodyRot = yaw;
            titan.yHeadRot = yaw;
            titan.serumRise(320);
            level.addFreshEntity(titan);
            level.playSound(null, c.x, c.y + 30.0, c.z, ModSounds.COLOSSAL_ARRIVAL.get(), SoundSource.HOSTILE, 16.0F, 0.9F);
            level.playSound(null, c.x, c.y + 50.0, c.z, ModSounds.TITAN_ROAR.get(), SoundSource.HOSTILE, 16.0F, 0.5F);
            AbstractTitan.shakeNearby(level, c, 400.0, 1.2F, 50);
            Destruction.far(level, ModParticles.BIG_STEAM.get(), c.x, y + 30.0, c.z, 300, 8.0, 25.0, 8.0, 0.25, 500.0);
        }
    }

    private static final class Running {
        final ServerLevel level;
        final Vec3 c;
        final BlockPos center;
        @Nullable
        final UUID owner;
        final float radius;
        final float depth;
        final float outer;
        final boolean blocks;
        final LongArrayList[] shells = new LongArrayList[20];
        int t;
        int scanY;
        double wave;
        int wreckage;
        boolean titanSpawned;

        Running(ServerLevel level, Vec3 c, @Nullable UUID owner) {
            this.level = level;
            this.c = c;
            this.center = BlockPos.containing(c);
            this.owner = owner;
            this.radius = TakemConfig.SERUM_RADIUS.get().floatValue();
            this.depth = this.radius * 0.45F;
            this.outer = this.radius * 2.5F;
            this.blocks = TakemConfig.SERUM_BREAKS_BLOCKS.get();

            for (int i = 0; i < 20; i++) {
                this.shells[i] = new LongArrayList();
            }

            this.scanY = -Mth.ceil(this.depth);
        }

        @Nullable
        Entity owner() {
            return this.owner == null ? null : this.level.getEntity(this.owner);
        }

        DamageSource blast() {
            Entity o = this.owner();
            return this.level.damageSources().explosion(null, o);
        }
    }
}
