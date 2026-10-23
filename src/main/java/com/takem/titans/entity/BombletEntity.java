package com.takem.titans.entity;

import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Boom;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BombletEntity extends ThrowableItemProjectile {
    public BombletEntity(EntityType<? extends BombletEntity> type, Level level) {
        super(type, level);
    }

    public BombletEntity(Level level, LivingEntity owner) {
        super(ModEntities.BOMBLET.get(), owner, level);
    }

    public BombletEntity(Level level, double x, double y, double z, @Nullable Entity owner) {
        super(ModEntities.BOMBLET.get(), x, y, z, level);
        if (owner != null) {
            this.setOwner(owner);
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }

        if (this.tickCount > 120 && !this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            Vec3 at = result.getLocation();
            Boom.Spec s = new Boom.Spec().radius(3.6).blocks(2.4, 3.0F).damage(14.0F, 45.0F).fire(4).fx(4, 3.0F, 0.0F).owner(this.getOwner(), this);
            s.knock = 1.2;
            s.volume = 3.0F;
            s.sound = ModSounds.BOMBLET_POP.get();
            Boom.explode(level, at, s);
            this.discard();
        }
    }
}
