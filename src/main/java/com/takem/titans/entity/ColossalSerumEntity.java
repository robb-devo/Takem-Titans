package com.takem.titans.entity;

import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.world.ColossalBlast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ColossalSerumEntity extends ThrowableItemProjectile {
    public ColossalSerumEntity(EntityType<? extends ColossalSerumEntity> type, Level level) {
        super(type, level);
    }

    public ColossalSerumEntity(Level level, LivingEntity owner) {
        super(ModEntities.COLOSSAL_SERUM.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.COLOSSAL_SERUM.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.045;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                this.level()
                    .addParticle(
                        ModParticles.EMBER.get(),
                        this.getX(),
                        this.getY() + 0.1,
                        this.getZ(),
                        (this.random.nextDouble() - 0.5) * 0.05,
                        0.02,
                        (this.random.nextDouble() - 0.5) * 0.05
                    );
            }

            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.02, 0.0);
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
            if (result instanceof BlockHitResult bhr) {
                at = at.add(Vec3.atLowerCornerOf(bhr.getDirection().getNormal()).scale(0.5));
            }

            ColossalBlast.start(level, at, this.getOwner());
            this.discard();
        }
    }
}
