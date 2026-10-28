package com.takem.titans.world;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

/**
 * Hardened crystal a shifter drives up out of the ground. The spikes are real blocks: they burst through whatever stands
 * on top of them and throw it aside, they can be hooked, and after a while they shatter again and leave the street to the
 * collapse engine. They grow a few layers a tick, so an eruption reads as a strike rather than a pop-in.
 */
@EventBusSubscriber(modid = "takemtitans")
public final class CrystalSpikes {
    private static final BlockState[] CRYSTAL = {
        Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState()
    };
    private static final List<Spike> SPIKES = new ArrayList<>();
    private static final int MAX_SPIKES = 90;

    private CrystalSpikes() {
    }

    /** The ground under (x, z): the street, not the roof of whatever stands there. */
    public static int groundY(ServerLevel level, double x, double z, int near) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return top > near + 4 ? near : top;
    }

    public static boolean isCrystal(BlockState s) {
        return s.is(Blocks.QUARTZ_BLOCK) || s.is(Blocks.CALCITE) || s.is(Blocks.SMOOTH_QUARTZ);
    }

    /**
     * A spike of {@code height} blocks with a base {@code girth} across, rising from {@code base}. It deals
     * {@code damage} to whatever it skewers and lasts {@code life} ticks once grown.
     */
    public static void erupt(ServerLevel level, BlockPos base, int height, float girth, float damage, int life, @Nullable Entity owner) {
        if (SPIKES.size() >= MAX_SPIKES) {
            shatter(SPIKES.remove(0));
        }

        Spike s = new Spike(level, base, height, girth, damage, life, owner);
        SPIKES.add(s);
        level.playSound(null, base, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 4.0F, 0.5F + level.random.nextFloat() * 0.2F);
        level.playSound(null, base, ModSounds.TITAN_STEP.get(), SoundSource.HOSTILE, 3.0F, 0.7F);
        Fx.send(level, Fx.CRYSTAL, Vec3.atBottomCenterOf(base), (float)height, 0.0F, 160.0);
    }

    /** A line of spikes running from {@code from} toward {@code to}, one every {@code step} blocks, each taller than the last. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, int count, double step, int delayEach, float damage, @Nullable Entity owner) {
        Vec3 d = to.subtract(from);
        d = new Vec3(d.x, 0.0, d.z);
        if (d.lengthSqr() < 1.0E-4) {
            return;
        }

        d = d.normalize();
        for (int i = 0; i < count; i++) {
            Vec3 p = from.add(d.scale(4.0 + i * step));
            int h = 5 + Math.min(10, i);
            PENDING.add(new Pending(level, p, (int)from.y, h, 1.6F + Math.min(1.6F, i * 0.12F), damage, owner, i * delayEach));
        }
    }

    /** A crown of spikes thrown up around a titan to sweep riders off its back. */
    public static void ring(ServerLevel level, Vec3 c, double radius, int count, float damage, @Nullable Entity owner) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2.0 * i / count;
            Vec3 p = c.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
            PENDING.add(new Pending(level, p, (int)c.y, 9 + level.random.nextInt(6), 2.0F, damage, owner, i % 3));
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(ServerLevel level, Vec3 at, int near, int height, float girth, float damage, @Nullable Entity owner, int delay) {
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        for (int i = 0; i < PENDING.size(); i++) {
            Pending p = PENDING.get(i);
            if (p.level == level) {
                if (p.delay <= 0) {
                    PENDING.remove(i--);
                    int y = groundY(level, p.at.x, p.at.z, p.near);
                    erupt(level, BlockPos.containing(p.at.x, y, p.at.z), p.height, p.girth, p.damage, 140 + level.random.nextInt(60), p.owner);
                } else {
                    PENDING.set(i, new Pending(p.level, p.at, p.near, p.height, p.girth, p.damage, p.owner, p.delay - 1));
                }
            }
        }

        for (int i = 0; i < SPIKES.size(); i++) {
            Spike s = SPIKES.get(i);
            if (s.level == level && s.tick()) {
                SPIKES.remove(i--);
                shatter(s);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // nothing of a fight should be left standing in the save
        for (Spike s : SPIKES) {
            for (BlockPos p : s.placed) {
                if (s.level.isLoaded(p) && isCrystal(s.level.getBlockState(p))) {
                    s.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        SPIKES.clear();
        PENDING.clear();
    }

    private static void shatter(Spike s) {
        ServerLevel level = s.level;
        RandomSource rnd = level.random;
        int n = 0;
        for (BlockPos p : s.placed) {
            if (level.isLoaded(p)) {
                BlockState st = level.getBlockState(p);
                if (isCrystal(st)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    if (n++ % 4 == 0) {
                        Destruction.crumble(level, Vec3.atCenterOf(p), st, 4, 0.4F, 1.0F);
                    }
                }
            }
        }

        level.playSound(null, s.base, ModSounds.GLASS_SHATTER.get(), SoundSource.HOSTILE, 2.5F, 0.7F + rnd.nextFloat() * 0.3F);
        if (s.wrecked) {
            Destruction.requestCollapse(level, s.base.above(s.height / 2), Mth.ceil(s.girth) + 4, s.height + 16, 4);
        }
    }

    private static final class Spike {
        final ServerLevel level;
        final BlockPos base;
        final int height;
        final float girth;
        final float damage;
        final Entity owner;
        final List<BlockPos> placed = new ArrayList<>();
        final Set<Integer> hit = new HashSet<>();
        int grown;
        int life;
        boolean wrecked;

        Spike(ServerLevel level, BlockPos base, int height, float girth, float damage, int life, @Nullable Entity owner) {
            this.level = level;
            this.base = base;
            this.height = height;
            this.girth = girth;
            this.damage = damage;
            this.life = life;
            this.owner = owner;
        }

        /** True once the spike has stood its time. */
        boolean tick() {
            if (this.grown < this.height) {
                for (int k = 0; k < 3 && this.grown < this.height; k++) {
                    this.layer(this.grown++);
                }

                this.skewer();
                return false;
            }

            return --this.life <= 0;
        }

        private void layer(int y) {
            RandomSource rnd = this.level.random;
            float t = (float)y / this.height;
            float r = Math.max(0.45F, this.girth * (1.0F - t * t * 0.9F) * 0.5F);
            int R = Mth.ceil(r);
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dz * dz <= r * r + 0.3F) {
                        BlockPos p = this.base.offset(dx, y, dz);
                        BlockState old = this.level.getBlockState(p);
                        if (isCrystal(old) || Destruction.isWallStone(old) || old.getDestroySpeed(this.level, p) < 0.0F) {
                            continue;
                        }

                        if (!old.isAir() && old.getFluidState().isEmpty()) {
                            // whatever stood here goes up with the crystal
                            this.wrecked = true;
                            Vec3 kick = new Vec3(dx + (rnd.nextDouble() - 0.5), 0.0, dz + (rnd.nextDouble() - 0.5));
                            kick = kick.lengthSqr() < 0.01 ? new Vec3(0.0, 1.0, 0.0) : kick.normalize().scale(0.35);
                            Destruction.smash(this.level, p, old, kick.add(0.0, 0.7 + rnd.nextDouble() * 0.5, 0.0), 0.5F);
                        }

                        this.level.setBlock(p, CRYSTAL[rnd.nextInt(10) < 6 ? 0 : 1 + rnd.nextInt(2)], 3);
                        this.placed.add(p);
                    }
                }
            }
        }

        private void skewer() {
            double r = this.girth * 0.5 + 0.8;
            AABB box = new AABB(this.base.getX() - r + 0.5, this.base.getY(), this.base.getZ() - r + 0.5,
                this.base.getX() + r + 0.5, this.base.getY() + this.grown + 1, this.base.getZ() + r + 0.5);
            for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (e instanceof AbstractTitan || e == this.owner || !this.hit.add(e.getId())) {
                    continue;
                }

                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                    continue;
                }

                e.hurt(this.owner instanceof LivingEntity l ? this.level.damageSources().mobAttack(l) : this.level.damageSources().generic(), this.damage);
                Vec3 out = e.position().subtract(Vec3.atBottomCenterOf(this.base));
                out = new Vec3(out.x, 0.0, out.z);
                out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(0.6);
                e.setDeltaMovement(e.getDeltaMovement().add(out.x, 1.15, out.z));
                e.hurtMarked = true;
            }
        }
    }
}
