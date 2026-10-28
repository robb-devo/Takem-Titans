package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import org.joml.Vector3f;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class BoulderBarrage {
    private static final int WARN = 50;
    private static final int VOLLEYS = 3;
    private static final int VOLLEY_GAP = 28;
    private static final double SPREAD = 26.0;
    private static final int UPDATE = 50;
    private static final DustParticleOptions RED_SMOKE = new DustParticleOptions(new Vector3f(0.9F, 0.12F, 0.08F), 4.0F);
    private static final List<BoulderBarrage.Running> RUNNING = new ArrayList<>();

    private BoulderBarrage() {
    }

    public static void start(ServerLevel level, Vec3 target, @Nullable Entity owner) {
        RandomSource rnd = level.random;
        Vec3 away = owner != null ? new Vec3(target.x - owner.getX(), 0.0, target.z - owner.getZ()) : Vec3.ZERO;
        if (away.lengthSqr() < 4.0) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            away = new Vec3(Math.cos(a), 0.0, Math.sin(a));
        }

        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0.0, away.x);
        Vec3 from = target.add(away.scale(150.0)).add(side.scale((rnd.nextDouble() - 0.5) * 60.0)).add(0.0, 80.0, 0.0);
        RUNNING.add(new BoulderBarrage.Running(level, target, from, owner == null ? null : owner.getUUID(), TakemConfig.BOULDER_COUNT.get()));
        ChunkPos c = new ChunkPos(BlockPos.containing(from));
        level.getChunkSource().addRegionTicket(Chunks.TICKET, c, 2, c);
        Component title = Component.translatable("message.takemtitans.boulder_incoming").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(target) < 25600.0) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(4, 40, 12));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && !RUNNING.isEmpty()) {
            for (int i = 0; i < RUNNING.size(); i++) {
                BoulderBarrage.Running r = RUNNING.get(i);
                if (r.level == level) {
                    step(r);
                    if (r.thrown >= r.total && r.t > 174) {
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

    private static void step(BoulderBarrage.Running r) {
        ServerLevel level = r.level;
        RandomSource rnd = level.random;
        int t = r.t++;
        Vec3 c = r.target;
        if (t < 194 && t % 2 == 0) {
            Destruction.far(level, RED_SMOKE, c.x, c.y + 1.5, c.z, 12, 0.6, 1.5, 0.6, 0.0, 300.0);
            Destruction.far(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + 1.0, c.z, 2, 0.3, 0.2, 0.3, 0.02, 300.0);
        }

        if (t == 8) {
            Vec3 heard = c.add(r.from.subtract(c).normalize().scale(60.0));
            level.playSound(null, heard.x, heard.y, heard.z, ModSounds.TITAN_ROAR.get(), SoundSource.HOSTILE, 12.0F, 0.45F);
        }

        if (t >= 50) {
            int k = t - 50;
            int volley = k / 28;
            int inVolley = k % 28;
            if (volley < 3) {
                int perVolley = Mth.ceil((double)r.total / 3.0);
                if (inVolley == 0) {
                    Vec3 heard = c.add(r.from.subtract(c).normalize().scale(50.0)).add(0.0, 20.0, 0.0);
                    level.playSound(null, heard.x, heard.y, heard.z, ModSounds.BOULDER_THROW.get(), SoundSource.HOSTILE, 10.0F, 0.8F + rnd.nextFloat() * 0.2F);
                }

                if (inVolley < 8) {
                    int n = perVolley / 8 + (inVolley < perVolley % 8 ? 1 : 0);

                    for (int i = 0; i < n && r.thrown < r.total; i++) {
                        throwOne(r, rnd);
                    }
                }
            }
        }
    }

    private static void throwOne(BoulderBarrage.Running r, RandomSource rnd) {
        ServerLevel level = r.level;
        r.thrown++;
        double a = rnd.nextDouble() * 6.2831854820251465;
        double d = Math.sqrt(rnd.nextDouble()) * 26.0;
        Vec3 hit = r.target.add(Math.cos(a) * d, 0.0, Math.sin(a) * d);
        Vec3 from = r.from.add(rnd.nextGaussian() * 6.0, rnd.nextGaussian() * 4.0, rnd.nextGaussian() * 6.0);
        float rad = 1.6F + rnd.nextFloat() * 1.6F;
        FallingSectionEntity b = FallingSectionEntity.boulder(level, from, rad, r.owner);
        if (FallingSectionEntity.budgetFor(b.states.length)) {
            double dist = Math.sqrt(hit.subtract(from).horizontalDistanceSqr());
            int T = Math.max(30, (int)(dist / 2.0) + rnd.nextInt(12));
            double g = 0.04;
            Vec3 v = new Vec3((hit.x - from.x) / (double)T, (hit.y - from.y + g * (double)T * (double)(T + 1) / 2.0) / (double)T, (hit.z - from.z) / (double)T);
            b.launch(v, Chunks.tumble(rnd, 0.25F));
            level.addFreshEntity(b);
            if (r.thrown % 2 == 0) {
                level.playSound(null, b, ModSounds.BOULDER_WHOOSH.get(), SoundSource.HOSTILE, 5.0F, 0.8F + rnd.nextFloat() * 0.4F);
            }
        }
    }

    public static void throwAt(ServerLevel level, Vec3 from, Vec3 hit, @Nullable Entity owner, float rad, int speedDiv) {
        RandomSource rnd = level.random;
        FallingSectionEntity b = FallingSectionEntity.boulder(level, from, rad, owner == null ? null : owner.getUUID());
        if (FallingSectionEntity.budgetFor(b.states.length)) {
            double dist = Math.sqrt(hit.subtract(from).horizontalDistanceSqr());
            int T = Math.max(22, (int)(dist / (double)speedDiv));
            double g = 0.04;
            Vec3 v = new Vec3((hit.x - from.x) / (double)T, (hit.y - from.y + g * (double)T * (double)(T + 1) / 2.0) / (double)T, (hit.z - from.z) / (double)T);
            b.launch(v, Chunks.tumble(rnd, 0.25F));
            level.addFreshEntity(b);
            level.playSound(null, b, ModSounds.BOULDER_WHOOSH.get(), SoundSource.HOSTILE, 5.0F, 0.8F + rnd.nextFloat() * 0.4F);
        }
    }

    public static void impact(FallingSectionEntity boulder, Vec3 at, Vec3 vel) {
        ServerLevel level = (ServerLevel)boulder.level();
        RandomSource rnd = level.random;
        float R = boulder.radius * 1.5F + 2.0F;
        Entity owner = boulder.owner();
        DamageSource src = level.damageSources().explosion(boulder, owner);
        boolean blocks = TakemConfig.BOULDER_BREAKS_BLOCKS.get();
        List<Vec3> where = new ArrayList<>();
        List<BlockState> rocks = boulder.takeBlocks(where);
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();

        for (int i = 0; i < rocks.size(); i++) {
            if (!(rnd.nextFloat() < 0.5F)) {
                Vec3 p = where.get(i);
                Vec3 out = p.subtract(at);
                out = out.lengthSqr() < 0.001 ? new Vec3(0.0, 1.0, 0.0) : out.normalize();
                Vec3 v = out.scale(0.5 + rnd.nextDouble() * 0.9).add(vel.scale(0.25)).add(0.0, 0.4 + rnd.nextDouble() * 0.6, 0.0);
                Destruction.throwOrSettle(level, p.add(0.0, 1.0, 0.0), rocks.get(i), v, rubble, 2);
            }
        }

        Fx.send(level, 18, at, R, 0.0F, 400.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BOULDER_IMPACT.get(), SoundSource.BLOCKS, 9.0F, 0.75F + rnd.nextFloat() * 0.3F);
        AbstractTitan.shakeNearby(level, at, 110.0, 0.75F, 16);
        if (blocks) {
            crater(level, at, R, vel, rnd);
        }

        Destruction.glassBurst(level, at, (double)R * 2.6);

        for (Entity e : level.getEntities((Entity)null, new AABB(at, at).inflate((double)R * 2.2), Entity::isAlive)) {
            if (!(e instanceof TitanPart) && !(e instanceof FallingSectionEntity) && !(e instanceof DebrisEntity)) {
                double d = e.position().distanceTo(at);
                if (!(d > (double)R * 2.2)) {
                    double f = 1.0 - d / ((double)R * 2.2);
                    Vec3 dir = e.position().subtract(at);
                    dir = dir.lengthSqr() < 0.001 ? new Vec3(0.0, 1.0, 0.0) : dir.normalize();
                    if (e instanceof AbstractTitan) {
                        AbstractTitan titan = (AbstractTitan)e;
                        titan.hurt(src, (float)(220.0 * f));
                        if (f > 0.5) {
                            titan.blastKnockDown(100);
                        }
                    } else {
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(1.6 * f)).add(0.0, 0.7 * f + 0.2, 0.0));
                        e.hurtMarked = true;
                        if (e instanceof Player) {
                            Player p = (Player)e;
                            if (p.isCreative() || p.isSpectator()) {
                                continue;
                            }
                        }

                        if (e instanceof LivingEntity) {
                            LivingEntity living = (LivingEntity)e;
                            living.hurt(src, (float)(70.0 * f + 6.0));
                            if (f > 0.3) {
                                living.igniteForSeconds(5.0F);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void crater(ServerLevel level, Vec3 at, float R, Vec3 vel, RandomSource rnd) {
        int r = Mth.ceil(R);
        BlockPos c = BlockPos.containing(at);
        MutableBlockPos m = new MutableBlockPos();
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
        int thrown = 0;

        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt((double)(dx * dx) + (double)(dy * dy) * (dy < 0 ? 2.2 : 1.0) + (double)(dz * dz));
                    if (!(d > (double)R) && (!(d > (double)R * 0.75) || !((double)rnd.nextFloat() < (d / (double)R - 0.75) * 3.0))) {
                        m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        BlockState s = level.getBlockState(m);
                        if (!s.isAir()) {
                            float h = s.getDestroySpeed(level, m);
                            if (!(h < 0.0F) && !(h > 30.0F) && (s.getFluidState().isEmpty() || !s.getCollisionShape(level, m).isEmpty())) {
                                level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                                Rebuild.mark(level, m);
                                if (thrown < 60 && rnd.nextFloat() < 0.35F && !s.getCollisionShape(level, m).isEmpty()) {
                                    Vec3 p = Vec3.atCenterOf(m);
                                    Vec3 out = p.subtract(at);
                                    out = out.lengthSqr() < 0.001 ? new Vec3(0.0, 1.0, 0.0) : out.normalize();
                                    Vec3 v = new Vec3(out.x, Math.abs(out.y) * 0.5 + 0.6, out.z).scale(0.6 + rnd.nextDouble() * 0.8);
                                    Destruction.throwOrSettle(level, p, s, v, rubble, 2);
                                    thrown++;
                                }
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < 18; i++) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            double d = (double)R * (0.5 + rnd.nextDouble() * 0.9);
            m.set(Mth.floor(at.x + Math.cos(a) * d), c.getY() + r, Mth.floor(at.z + Math.sin(a) * d));

            for (int k = 0; k < r * 2 + 4 && level.getBlockState(m).isAir(); k++) {
                m.move(0, -1, 0);
            }

            BlockState s = level.getBlockState(m);
            if (!s.isAir() && s.getFluidState().isEmpty()) {
                if (rnd.nextFloat() < 0.4F && s.getDestroySpeed(level, m) >= 0.0F && isSoil(s)) {
                    level.setBlock(m, rnd.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), 50);
                    Rebuild.mark(level, m);
                }

                BlockPos up = m.above();
                if (rnd.nextFloat() < 0.5F && level.getBlockState(up).isAir()) {
                    BlockState fire = BaseFireBlock.getState(level, up);
                    if (fire.canSurvive(level, up)) {
                        Destruction.flame(level, up);
                    }
                }
            }
        }

        Destruction.far(level, ModParticles.EMBER.get(), at.x, at.y + 1.0, at.z, 30, (double)R * 0.5, 1.0, (double)R * 0.5, 0.3, 200.0);
        Destruction.requestCollapse(level, c, Mth.clamp(r + 8, 10, 22), 56, 8);
    }

    private static boolean isSoil(BlockState s) {
        return RumblingTitan.isGround(s);
    }

    private static final class Running {
        final ServerLevel level;
        final Vec3 target;
        final Vec3 from;
        @Nullable
        final UUID owner;
        final int total;
        int t;
        int thrown;

        Running(ServerLevel level, Vec3 target, Vec3 from, @Nullable UUID owner, int total) {
            this.level = level;
            this.target = target;
            this.from = from;
            this.owner = owner;
            this.total = total;
        }
    }
}
