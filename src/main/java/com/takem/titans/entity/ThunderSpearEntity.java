package com.takem.titans.entity;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import com.takem.titans.world.Boom;
import com.takem.titans.world.Destruction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.entity.PartEntity;

public class ThunderSpearEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(ThunderSpearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int FUSE = 24;
    private int fuse = -1;
    private int stuckEntityId = -1;
    private Vec3 stuckOffset = Vec3.ZERO;
    private int stuckPart = -1;
    private int life;

    public ThunderSpearEntity(EntityType<? extends ThunderSpearEntity> type, Level level) {
        super(type, level);
    }

    public ThunderSpearEntity(Level level, LivingEntity owner) {
        this(ModEntities.THUNDER_SPEAR.get(), level);
        this.setOwner(owner);
    }

    @Override
    protected void defineSynchedData(Builder builder) {
        builder.define(STUCK, false);
    }

    public boolean isStuck() {
        return this.entityData.get(STUCK);
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        if (!this.isStuck()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            Vec3 v = this.getDeltaMovement();
            if (hit.getType() != Type.MISS) {
                this.setPos(hit.getLocation());
                if (!this.level().isClientSide) {
                    this.stick(hit);
                }
            } else {
                this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
                this.setDeltaMovement(v.scale(0.995).add(0.0, -0.025, 0.0));
                this.updateRotation();
            }

            if (this.level().isClientSide) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        } else {
            label46:
            if (this.stuckEntityId >= 0) {
                Entity e = this.level().getEntity(this.stuckEntityId);
                if (e instanceof AbstractTitan titan && this.stuckPart >= 0 && !titan.isDeadOrDying()) {
                    this.setPos(titan.partAnchor(this.stuckPart, this.stuckOffset, 1.0F));
                    break label46;
                }

                if (e != null && e.isAlive()) {
                    this.setPos(e.position().add(this.stuckOffset));
                }
            }

            if (this.level().isClientSide) {
                if (this.life % 4 == 0) {
                    this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 0.0, 0.05, 0.0);
                }
            } else if (--this.fuse <= 0) {
                this.detonate();
            }
        }

        if (!this.level().isClientSide && this.life > 200) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != this.getOwner();
    }

    private void stick(HitResult hit) {
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
        this.fuse = 24 - 7 * Skills.rank(this.getOwner() instanceof Player p ? p : null, Skill.SPEAR_SPECIALIST);
        if (hit instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            if (target instanceof TitanPart part) {
                this.stuckPart = part.index;
                this.stuckEntityId = part.getParent().getId();
                this.stuckOffset = part.getParent().partLocal(part.index, hit.getLocation());
            } else {
                if (target instanceof PartEntity<?> part) {
                    target = part.getParent();
                }

                this.stuckEntityId = target.getId();
                this.stuckOffset = hit.getLocation().subtract(target.position());
            }
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.HOOK_HIT.get(), SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    private void detonate() {
        ServerLevel level = (ServerLevel)this.level();
        Vec3 pos = this.position();
        Entity owner = this.getOwner();
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.THUNDER_SPEAR_BLAST.get(), SoundSource.PLAYERS, 8.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 40, 1.5, 1.5, 1.5, 0.08);
        Fx.send(level, 4, pos, 5.6F, 0.0F, 192.0);
        AbstractTitan.shakeNearby(level, pos, 48.0, 0.7F, 14);
        AABB box = new AABB(pos, pos).inflate(7.0);

        for (Entity e : level.getEntities(this, box)) {
            if (!(e instanceof TitanPart)) {
                double dist = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).distanceTo(pos);
                if (e instanceof TitanHeartEntity heart) {
                    heart.damage(dist < 5.0 ? 140.0F : 60.0F, owner instanceof Player pl ? pl : null);
                } else if (e instanceof AbstractTitan titan) {
                    titan.onThunderSpear(pos, owner);
                } else if (e instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity)e;
                    if (dist < 6.0) {
                        float dmg = (float)(14.0 * (1.0 - dist / 6.0)) + 2.0F;
                        living.hurt(level.damageSources().explosion(this, owner), dmg);
                        Vec3 push = e.position().subtract(pos).normalize().scale(1.2 * (1.0 - dist / 7.0));
                        e.push(push.x, push.y + 0.3, push.z);
                    }
                }
            }
        }

        int spec = Skills.rank(owner instanceof Player p ? p : null, Skill.SPEAR_SPECIALIST);
        Destruction.blast(level, pos, 5.6 + 1.6 * (double)spec);
        Boom.Spec shock = new Boom.Spec()
            .radius(8.0 + 2.5 * (double)spec)
            .blocks(spec > 0 ? 3.0 + 1.5 * (double)spec : 0.0, 6.0F)
            .damage(6.0F + 6.0F * (float)spec, 10.0F + 30.0F * (float)spec)
            .fx(4, 7.0F + 2.0F * (float)spec, 0.0F)
            .owner(owner, this);
        shock.knock = 1.8 + 0.5 * (double)spec;
        shock.volume = 5.0F;
        shock.fire = spec >= 2;
        shock.fireBlocks = spec >= 2 ? 8 : 0;
        Boom.explode(level, pos, shock);
        if (spec > 0) {
            for (Entity ex : level.getEntities(this, new AABB(pos, pos).inflate(7.0 + 2.0 * (double)spec))) {
                if (ex instanceof AbstractTitan) {
                    AbstractTitan titan = (AbstractTitan)ex;
                    if (!titan.isDeadOrDying()) {
                        titan.blastHit(pos, 45.0F * (float)spec, owner instanceof Player px ? px : null, level.damageSources().explosion(this, owner));
                    }
                }
            }
        }

        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Fuse", this.fuse);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.fuse = tag.getInt("Fuse");
    }
}
