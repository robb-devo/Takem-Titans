package com.takem.titans.world;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.CannonEntity;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.TitanHeartEntity;
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Boom {
    private Boom() {
    }

    public static void explode(ServerLevel level, Vec3 c, Boom.Spec s) {
        RandomSource rnd = level.random;
        Player credit = s.owner instanceof Player p ? p : null;
        if (credit != null && Skills.has(credit, Skill.INFERNO)) {
            s.radius *= 1.2;
            s.damage *= 1.25F;
            s.titanPower *= 1.25F;
        }

        DamageSource src = level.damageSources().explosion(s.direct, s.owner);
        float pitch = (float)Mth.clamp(1.25 - s.radius * 0.025, 0.7, 1.15) + (rnd.nextFloat() - 0.5F) * 0.1F;
        level.playSound(null, c.x, c.y, c.z, s.sound != null ? s.sound : ModSounds.BIG_BLAST.get(), SoundSource.BLOCKS, s.volume, pitch);
        if (s.radius >= 6.0) {
            level.playSound(null, c.x, c.y, c.z, ModSounds.BLAST_TAIL.get(), SoundSource.BLOCKS, s.volume * 0.8F, pitch * 0.95F);
        }

        if (s.fx >= 0) {
            Fx.send(level, s.fx, c.x, c.y, c.z, s.fxSize > 0.0F ? s.fxSize : (float)s.radius, s.fire ? 1.0F : 0.0F, s.fxExtra, 0.0F, 256.0 + s.radius * 6.0);
        }

        level.sendParticles(
            ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, Math.max(1, (int)(s.radius / 6.0)), s.radius * 0.2, s.radius * 0.1, s.radius * 0.2, 0.0
        );
        float shake = s.shake >= 0.0F ? s.shake : (float)Math.min(1.4, 0.3 + s.radius / 16.0);
        AbstractTitan.shakeNearby(level, c, 30.0 + s.radius * 5.0, shake, 14 + (int)s.radius);
        TownLife.alarm(level, c, s.radius * 3.0 + 12.0, 0);
        AABB box = new AABB(c, c).inflate(s.radius);
        Set<AbstractTitan> titans = new LinkedHashSet<>();

        for (Entity e : level.getEntities((Entity)null, box, Entity::isAlive)) {
            if (e instanceof TitanHeartEntity heart) {
                double hd = heart.position().distanceTo(c);
                heart.damage((float)((double)s.titanPower * Math.max(0.25, 1.0 - hd / (s.radius + 2.0))), credit);
            } else if (!(e instanceof CannonEntity)) {
                if (e instanceof TitanPart) {
                    TitanPart part = (TitanPart)e;
                    if (!(part.getParent() instanceof RumblingTitan)) {
                        titans.add(part.getParent());
                    }
                } else if (e instanceof AbstractTitan) {
                    AbstractTitan t = (AbstractTitan)e;
                    if (!(t instanceof RumblingTitan)) {
                        titans.add(t);
                    }
                } else {
                    Vec3 mid = e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0);
                    double d = mid.distanceTo(c);
                    if (!(d > s.radius) && (e != s.owner || !s.sparesOwner)) {
                        if (e instanceof Player) {
                            Player px = (Player)e;
                            if (px.isCreative() || px.isSpectator()) {
                                continue;
                            }
                        }

                        double f = 1.0 - d / s.radius;
                        Vec3 out = mid.subtract(c);
                        out = out.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : out.normalize();
                        if (s.pull > 0.0) {
                            out = out.scale(-s.pull);
                        }

                        if (e instanceof FallingSectionEntity fs) {
                            fs.shove(out.scale(s.knock * f * 0.7).add(0.0, 0.45 * f * s.knock, 0.0));
                        } else if (e instanceof DebrisEntity) {
                            e.setDeltaMovement(e.getDeltaMovement().add(out.scale(s.knock * f * 1.2)).add(0.0, 0.5 * f * s.knock, 0.0));
                        } else {
                            e.setDeltaMovement(e.getDeltaMovement().add(out.scale(s.knock * f)).add(0.0, 0.25 + 0.4 * f * s.knock, 0.0));
                            e.hurtMarked = true;
                            if (e instanceof LivingEntity) {
                                LivingEntity l = (LivingEntity)e;
                                if (e instanceof TownsfolkEntity) {
                                    TownsfolkEntity tf = (TownsfolkEntity)e;
                                    if (tf.isStoryCharacter()) {
                                        continue;
                                    }
                                }

                                l.hurt(src, (float)((double)s.damage * (0.25 + 0.75 * f)));
                                if (s.fire) {
                                    l.igniteForSeconds((float)(3.0 + 5.0 * f));
                                }
                            }
                        }
                    }
                }
            }
        }

        for (AbstractTitan t : titans) {
            double d = t.position().add(0.0, (double)t.getBbHeight() * 0.6, 0.0).distanceTo(c);
            double reach = s.radius + (double)t.getBbWidth();
            double fx = Mth.clamp(1.0 - d / (reach * 1.3), 0.15, 1.0);
            t.blastHit(c, (float)((double)s.titanPower * fx), credit, src);
            if (s.fire) {
                t.igniteForSeconds(4.0F);
            }
        }

        boolean breaks = TakemConfig.EXPLOSIVES_BREAK_BLOCKS.get();
        if (breaks && s.blockRadius > 0.0) {
            blow(level, c, s, rnd);
        }

        if (s.glass) {
            Destruction.glassBurst(level, c, Math.min(60.0, s.radius * 2.4));
        }

        if (s.radius >= 6.0) {
            Fx.send(level, 22, c.x, c.y, c.z, (float)(s.radius * 1.6), (float)Math.min(10.0, 2.0 + s.radius * 0.3), 0.0F, 0.0F, 256.0 + s.radius * 4.0);
        }

        if (breaks && s.blockRadius >= 3.0 && s.collapse) {
            hurl(level, c, s, rnd);
        }

        if (s.fireBlocks > 0 && TakemConfig.DESTRUCTION_FIRES.get()) {
            ignite(level, c, Math.max(s.radius, s.blockRadius) * 1.1, s.fireBlocks, rnd);
        }
    }

    private static void blow(ServerLevel level, Vec3 c, Boom.Spec s, RandomSource rnd) {
        double radius = s.blockRadius;
        int r = Mth.ceil(radius);
        BlockPos center = BlockPos.containing(c);
        MutableBlockPos m = new MutableBlockPos();
        int broken = 0;
        int budget = 5000;

        for (int dy = -r; dy <= r && budget > 0; dy++) {
            for (int dx = -r; dx <= r && budget > 0; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    Vec3 p = Vec3.atCenterOf(m);
                    double d = p.distanceTo(c);
                    if (!(d > radius) && (!(d > radius * 0.72) || !((double)rnd.nextFloat() < (d / radius - 0.72) * 2.6))) {
                        BlockState st = level.getBlockState(m);
                        Vec3 dir = d < 0.01 ? new Vec3(0.0, 1.0, 0.0) : p.subtract(c).scale(1.0 / d);
                        double power = 0.7 + 2.1 * (1.0 - d / radius);
                        Vec3 v = dir.scale(power * (0.75 + rnd.nextDouble() * 0.5)).add(0.0, 0.25 + rnd.nextDouble() * 0.35, 0.0);
                        if (Destruction.canSmash(level, m, st, s.maxHardness)) {
                            Destruction.smash(level, m.immutable(), st, v, 0.12F, power > 1.4 ? 2 : (power > 1.0 ? 1 : 0));
                            broken++;
                            budget--;
                        } else if (Destruction.blastOther(level, m.immutable(), st, d / radius, s.maxHardness, v)) {
                            broken++;
                            budget--;
                        }
                    }
                }
            }
        }

        if (broken > 0) {
            Destruction.far(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, 8, radius * 0.5, radius * 0.4, radius * 0.5, 0.03, 200.0);
            if (s.collapse) {
                Destruction.requestCollapse(level, center, Mth.clamp(r + 10, 10, 32), 72, Math.min(16, r + 4));
            }
        }
    }

    private static void hurl(ServerLevel level, Vec3 c, Boom.Spec s, RandomSource rnd) {
        int chunks = Mth.clamp((int)(s.blockRadius / 3.0), 1, 6);
        double reach = s.blockRadius + 1.5;

        for (int i = 0; i < chunks; i++) {
            double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
            double up = -0.3 + rnd.nextDouble() * 0.9;
            Vec3 dir = new Vec3(Math.cos(a), up, Math.sin(a)).normalize();
            BlockPos at = BlockPos.containing(c.add(dir.scale(reach)));
            FallingSectionEntity lump = Chunks.rip(level, at, 1 + (s.blockRadius > 8.0 ? 1 : 0) + rnd.nextInt(2), 0, 5);
            if (lump != null) {
                double kick = Math.min(3.2, 0.9 + s.blockRadius * 0.12);
                lump.launch(dir.scale(kick * (0.7 + rnd.nextDouble() * 0.5)).add(0.0, 0.35 + rnd.nextDouble() * kick * 0.5, 0.0), Chunks.tumble(rnd, 0.5F));
                level.addFreshEntity(lump);
            }
        }
    }

    public static void ignite(ServerLevel level, Vec3 c, double radius, int count, RandomSource rnd) {
        MutableBlockPos m = new MutableBlockPos();

        for (int i = 0; i < count * 3 && count > 0; i++) {
            double a = rnd.nextDouble() * 3.141592653589793 * 2.0;
            double d = Math.sqrt(rnd.nextDouble()) * radius;
            m.set(c.x + Math.cos(a) * d, c.y + radius * 0.5, c.z + Math.sin(a) * d);

            for (int k = 0; k < (int)radius + 8 && m.getY() > level.getMinBuildHeight(); k++) {
                if (!level.getBlockState(m).isAir()) {
                    m.move(0, 1, 0);
                    if (level.getBlockState(m).isAir() && BaseFireBlock.canBePlacedAt(level, m, Direction.UP)) {
                        Destruction.flame(level, m);
                        count--;
                    }
                    break;
                }

                m.move(0, -1, 0);
            }
        }
    }

    public static final class Spec {
        public double radius = 6.0;
        public double blockRadius;
        public float maxHardness = 6.0F;
        public float damage = 20.0F;
        public float titanPower = 40.0F;
        public double knock = 1.5;
        public boolean fire;
        public int fireBlocks;
        public boolean glass = true;
        public int fx = 4;
        public float fxSize = -1.0F;
        public float fxExtra;
        public float volume = 6.0F;
        @Nullable
        public SoundEvent sound;
        public float shake = -1.0F;
        public boolean collapse = true;
        public boolean sparesOwner = true;
        public double pull;
        @Nullable
        public Entity owner;
        @Nullable
        public Entity direct;

        public Boom.Spec radius(double r) {
            this.radius = r;
            return this;
        }

        public Boom.Spec blocks(double r, float hardness) {
            this.blockRadius = r;
            this.maxHardness = hardness;
            return this;
        }

        public Boom.Spec damage(float d, float titan) {
            this.damage = d;
            this.titanPower = titan;
            return this;
        }

        public Boom.Spec fire(int blocks) {
            this.fire = true;
            this.fireBlocks = blocks;
            return this;
        }

        public Boom.Spec fx(int kind, float size, float extra) {
            this.fx = kind;
            this.fxSize = size;
            this.fxExtra = extra;
            return this;
        }

        public Boom.Spec owner(@Nullable Entity owner, @Nullable Entity direct) {
            this.owner = owner;
            this.direct = direct;
            return this;
        }
    }
}
