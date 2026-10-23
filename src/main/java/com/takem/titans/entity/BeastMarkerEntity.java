package com.takem.titans.entity;

import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.world.BoulderBarrage;
import net.minecraft.core.particles.DustParticleOptions;
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
import org.joml.Vector3f;

public class BeastMarkerEntity extends ThrowableItemProjectile {
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9F, 0.12F, 0.08F), 1.5F);

    public BeastMarkerEntity(EntityType<? extends BeastMarkerEntity> type, Level level) {
        super(type, level);
    }

    public BeastMarkerEntity(Level level, LivingEntity owner) {
        super(ModEntities.BEAST_MARKER.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BEAST_BOULDER.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.level().addParticle(RED, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.02, 0.0);
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

            BoulderBarrage.start(level, at, this.getOwner());
            this.discard();
        }
    }
}
