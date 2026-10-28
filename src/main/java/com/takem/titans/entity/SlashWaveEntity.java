package com.takem.titans.entity;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.BladeTier;
import com.takem.titans.item.GearTier;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Destruction;
import com.takem.titans.world.Rebuild;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class SlashWaveEntity extends Entity implements IEntityWithComplexSpawn {
    public static final double SPEED = 2.4;
    public static final float HALF_WIDTH = 4.5F;
    private static final int LIFE = 32;
    private Vec3 dir = new Vec3(0.0, 0.0, 1.0);
    private float roll;
    @Nullable
    private UUID owner;
    private final IntOpenHashSet hit = new IntOpenHashSet();
    private int cutSound = -10;
    private int life = 32;
    private float power = 1.0F;
    private boolean founder;
    public static Consumer<SlashWaveEntity> CLIENT_TICK = w -> {
    };

    public SlashWaveEntity(EntityType<? extends SlashWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void fire(ServerPlayer player, OdmPlayerData d, int roll) {
        ItemStack blades = player.getMainHandItem();
        if (blades.getItem() instanceof BladeItem b0 && b0.tier == BladeTier.FOUNDER) {
            // the Founder's Edge throws no wave: it tears space open where you look
            com.takem.titans.item.FounderEdge.rend(player, d, roll);
            return;
        }

        if (blades.getItem() instanceof BladeItem b && b.tier.waves() && !BladeItem.isBroken(blades) && d.waveCooldown <= 0) {
            boolean founder = b.tier == BladeTier.FOUNDER;
            d.waveCooldown = founder ? (d.overdrive() ? 3 : 5) : (d.overdrive() ? 4 : 8);
            ServerLevel level = player.serverLevel();
            Vec3 look = player.getLookAngle();
            Vec3 at = player.getEyePosition().add(look.scale(1.5));
            // the Founder's Edge throws a fan of three
            for (float spread : founder ? new float[]{-0.3F, 0.0F, 0.3F} : new float[]{0.0F}) {
                SlashWaveEntity w = new SlashWaveEntity(ModEntities.SLASH_WAVE.get(), level);
                w.setPos(at.x, at.y, at.z);
                w.dir = look.yRot(spread).normalize();
                w.roll = (float)Mth.clamp(roll, -90, 90);
                w.owner = player.getUUID();
                if (founder) {
                    w.founder = true;
                    w.power = 1.6F;
                    w.life = 40;
                }

                level.addFreshEntity(w);
            }
            level.playSound(null, at.x, at.y, at.z, ModSounds.SLASH_WAVE.get(), SoundSource.PLAYERS, 2.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            if (founder) {
                level.playSound(null, at.x, at.y, at.z, ModSounds.NAPE_THUNDER.get(), SoundSource.PLAYERS, 0.7F, 1.6F + player.getRandom().nextFloat() * 0.3F);
            }

            if (player.getRandom().nextInt(4) == 0) {
                blades.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }

            return;
        }
    }

    public static void crescentRing(ServerPlayer player, int count, int life, boolean red) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position().add(0.0, 1.0, 0.0);
        double yaw0 = Math.toRadians((double)player.getYRot());

        for (int i = 0; i < count; i++) {
            double a = yaw0 + (double)i * 3.141592653589793 * 2.0 / (double)count;
            Vec3 dir = new Vec3(-Math.sin(a), 0.0, Math.cos(a));
            SlashWaveEntity w = new SlashWaveEntity(ModEntities.SLASH_WAVE.get(), level);
            Vec3 at = c.add(dir.scale(1.4));
            w.setPos(at.x, at.y, at.z);
            w.dir = dir;
            w.roll = i % 2 == 0 ? 10.0F : -10.0F;
            w.owner = player.getUUID();
            w.life = life;
            w.power = 0.55F;
            level.addFreshEntity(w);
        }

        level.playSound(null, c.x, c.y, c.z, ModSounds.SLASH_WAVE.get(), SoundSource.PLAYERS, 3.0F, red ? 0.6F : 0.75F);
    }

    public static void sonicBoom(ServerPlayer player, OdmPlayerData d) {
        GearTier tier = OdmGear.tier(OdmGear.getGear(player));
        if (d.boomCooldown <= 0 && tier.supersonic()) {
            d.boomCooldown = 30;
            ServerLevel level = player.serverLevel();
            Vec3 at = player.position().add(0.0, 1.0, 0.0);
            Vec3 v = d.velocity.lengthSqr() > 1.0E-4 ? d.velocity.normalize() : player.getLookAngle();
            Fx.send(level, 20, at.x, at.y, at.z, (float)v.x, (float)v.y, (float)v.z, (float)d.velocity.length(), 300.0);
            if (tier == GearTier.EVENT_HORIZON) {
                // the Event Horizon rig doesn't just break the sound barrier, it bruises space: a violet rift burst
                Fx.send(level, 41, at, 7.0F, 0.0F, 300.0);
            }
            level.playSound(null, at.x, at.y, at.z, ModSounds.SONIC_BOOM.get(), SoundSource.PLAYERS, 8.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            DamageSource src = level.damageSources().playerAttack(player);

            for (Entity e : level.getEntities(player, new AABB(at, at).inflate(10.0), Entity::isAlive)) {
                if (!(e instanceof TitanPart) && !(e instanceof FallingSectionEntity) && !(e instanceof DebrisEntity)) {
                    double dist = e.position().distanceTo(at);
                    if (!(dist > 10.0)) {
                        double f = 1.0 - dist / 10.0;
                        Vec3 out = e.position().subtract(at).normalize();
                        if (e instanceof AbstractTitan) {
                            AbstractTitan t = (AbstractTitan)e;
                            if (!(t instanceof RumblingTitan)) {
                                t.hurt(src, (float)(20.0 * f));
                            }
                        } else {
                            e.setDeltaMovement(e.getDeltaMovement().add(out.scale(1.4 * f)).add(0.0, 0.5 * f, 0.0));
                            e.hurtMarked = true;
                            if (e instanceof LivingEntity) {
                                LivingEntity l = (LivingEntity)e;
                                if (e instanceof Player) {
                                    Player p = (Player)e;
                                    if (p.isCreative()) {
                                        continue;
                                    }
                                }

                                l.hurt(src, (float)(8.0 * f));
                            }
                        }
                    }
                }
            }

            if (TakemConfig.SONIC_BOOM_BREAKS_GLASS.get()) {
                Destruction.glassBurst(level, at, 9.0);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 from = this.position();
        Vec3 to = from.add(this.dir.scale(2.4));
        if (this.level().isClientSide) {
            this.setPos(to.x, to.y, to.z);
            CLIENT_TICK.accept(this);
        } else {
            this.setPos(to.x, to.y, to.z);
            ServerLevel level = (ServerLevel)this.level();
            Player player = this.owner == null ? null : level.getPlayerByUUID(this.owner);
            float fade = 1.0F - (float)this.tickCount / (float)this.life;
            this.cutEntities(level, player, from, to, fade);
            if (TakemConfig.SLASH_WAVE_BREAKS_BLOCKS.get()) {
                this.cutBlocks(level, from, to, fade);
            }

            if (this.tickCount >= this.life) {
                this.discard();
            }
        }
    }

    public Vec3 edge() {
        Vec3 up = Math.abs(this.dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = this.dir.cross(up).normalize();
        Vec3 vert = side.cross(this.dir).normalize();
        double r = Math.toRadians((double)this.roll);
        return side.scale(Math.cos(r)).add(vert.scale(Math.sin(r)));
    }

    private void cutBlocks(ServerLevel level, Vec3 from, Vec3 to, float fade) {
        RandomSource rnd = this.random;
        Vec3 e = this.edge();
        float half = this.half() * (0.7F + 0.3F * fade);
        int cut = 0;
        float rubble = TakemConfig.RUBBLE_CHANCE.get().floatValue();
        MutableBlockPos m = new MutableBlockPos();
        Vec3 sum = Vec3.ZERO;

        for (double a = 0.0; a <= 2.4 && cut < 180; a += 0.5) {
            Vec3 c = from.add(this.dir.scale(a));

            for (double l = (double)(-half); l <= (double)half && cut < 180; l += 0.5) {
                double bow = (1.0 - l / (double)half * (l / (double)half)) * 1.2;
                Vec3 p = c.add(e.scale(l)).add(this.dir.scale(bow));
                m.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
                BlockState s = level.getBlockState(m);
                if (!s.isAir() && (Destruction.isStructural(level, m, s) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.WITHER_IMMUNE))) {
                    float h = s.getDestroySpeed(level, m);
                    if (!(h < 0.0F) && !(h > 50.0F)) {
                        Vec3 v = this.dir.scale(0.25 + rnd.nextDouble() * 0.3).add(e.scale((rnd.nextDouble() - 0.5) * 0.3)).add(0.0, 0.1, 0.0);
                        if (rnd.nextFloat() < 0.5F) {
                            Destruction.smash(level, m.immutable(), s, v, rubble);
                        } else {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                            Rebuild.mark(level, m);
                        }

                        sum = sum.add(p);
                        cut++;
                    }
                }
            }
        }

        if (cut > 0) {
            Vec3 at = sum.scale(1.0 / (double)cut);
            if (this.tickCount - this.cutSound > 4) {
                this.cutSound = this.tickCount;
                level.playSound(null, at.x, at.y, at.z, ModSounds.SLASH_WAVE_CUT.get(), SoundSource.BLOCKS, 2.5F, 0.8F + rnd.nextFloat() * 0.3F);
                Fx.send(level, 21, at.x, at.y, at.z, (float)this.dir.x, (float)this.dir.y, (float)this.dir.z, this.half(), 128.0);
            }

            if (this.tickCount % 3 == 0) {
                Destruction.requestCollapse(level, BlockPos.containing(at), 12, 48, 8);
            }
        }
    }

    private void cutEntities(ServerLevel level, @Nullable Player player, Vec3 from, Vec3 to, float fade) {
        Vec3 e = this.edge();
        AABB box = new AABB(from, to).inflate((double)this.half() + 1.5);
        DamageSource src = player != null ? level.damageSources().playerAttack(player) : level.damageSources().magic();

        for (Entity t : level.getEntities(this, box, x -> x.isAlive() && x != player)) {
            if (!(t instanceof FallingSectionEntity) && !(t instanceof DebrisEntity) && !(t instanceof SlashWaveEntity)) {
                Vec3 rel = t.getBoundingBox().getCenter().subtract(from);
                double along = rel.dot(this.dir);
                if (!(along < -1.5) && !(along > 4.9)) {
                    Vec3 off = rel.subtract(this.dir.scale(along));
                    double lat = off.dot(e);
                    double perp = off.subtract(e.scale(lat)).length();
                    double reach = (double)Math.max(t.getBbWidth(), t.getBbHeight()) * 0.5 + 1.0;
                    if (!(Math.abs(lat) > (double)this.half() + reach) && !(perp > reach + 0.5)) {
                        if (t instanceof TitanPart) {
                            TitanPart part = (TitanPart)t;
                            AbstractTitan titan = part.getParent();
                            if (!(titan instanceof RumblingTitan) && this.hit.add(part.getId())) {
                                titan.hurtPart(part, src, 70.0F * this.power * (0.6F + 0.4F * fade));
                            }
                        } else if (!(t instanceof AbstractTitan) && this.hit.add(t.getId()) && t instanceof LivingEntity living) {
                            living.hurt(src, 40.0F * this.power);
                            living.setDeltaMovement(living.getDeltaMovement().add(this.dir.scale(0.8)).add(0.0, 0.3, 0.0));
                            living.hurtMarked = true;
                        }
                    }
                }
            }
        }
    }

    public float roll() {
        return this.roll;
    }

    public Vec3 dir() {
        return this.dir;
    }

    public boolean founder() {
        return this.founder;
    }

    public float half() {
        return this.founder ? 7.0F : HALF_WIDTH;
    }

    public float fade() {
        return 1.0F - (float)this.tickCount / (float)this.life;
    }

    @Override
    protected void defineSynchedData(Builder builder) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 65536.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(this.dir.x);
        buf.writeDouble(this.dir.y);
        buf.writeDouble(this.dir.z);
        buf.writeFloat(this.roll);
        buf.writeVarInt(this.life);
        buf.writeBoolean(this.founder);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        this.dir = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.roll = buf.readFloat();
        this.life = buf.readVarInt();
        this.founder = buf.readBoolean();
    }
}
