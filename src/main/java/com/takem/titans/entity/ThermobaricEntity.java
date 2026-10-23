package com.takem.titans.entity;

import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Boom;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ThermobaricEntity extends ThrowableItemProjectile {
    public ThermobaricEntity(EntityType<? extends ThermobaricEntity> type, Level level) {
        super(type, level);
    }

    public ThermobaricEntity(Level level, LivingEntity owner) {
        super(ModEntities.THERMOBARIC.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.THERMOBARIC_CANISTER.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.045;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.02, 0.0);
            this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.01, 0.0);
        }

        if (this.tickCount > 400 && !this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            Vec3 at = result.getLocation();
            Boom.Spec cloud = new Boom.Spec().radius(4.0).blocks(0.0, 0.0F).damage(4.0F, 10.0F).fx(28, 5.0F, 0.0F).owner(this.getOwner(), this);
            cloud.knock = 0.2;
            cloud.fire = false;
            cloud.collapse = false;
            cloud.sound = ModSounds.THERMO_CLOUD.get();
            cloud.volume = 3.0F;
            Boom.explode(level, at, cloud);
            Boom.Spec blast = new Boom.Spec().radius(10.0).blocks(7.0, 4.0F).damage(38.0F, 160.0F).fire(28).fx(29, 10.0F, 1.0F).owner(this.getOwner(), this);
            blast.knock = 2.4;
            blast.volume = 9.0F;
            blast.sound = ModSounds.THERMO_BLAST.get();
            Boom.explode(level, at, blast);
            this.discard();
        }
    }
}
