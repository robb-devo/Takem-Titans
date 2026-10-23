package com.takem.titans.entity.titan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Vector3f;

public class TitanPart extends PartEntity<AbstractTitan> {
    public final String name;
    public final int index;
    public final int kind;
    public final int side;
    final int joint;
    final Vector3f local;
    final float unitWidth;
    final float unitHeight;
    private EntityDimensions size;
    public boolean active = true;

    public TitanPart(AbstractTitan parent, int index, String name, int kind, int side, int joint, Vector3f local, float unitWidth, float unitHeight) {
        super(parent);
        this.index = index;
        this.name = name;
        this.kind = kind;
        this.side = side;
        this.joint = joint;
        this.local = local;
        this.unitWidth = unitWidth;
        this.unitHeight = unitHeight;
        this.size = EntityDimensions.scalable(unitWidth / 16.0F, unitHeight / 16.0F);
        this.refreshDimensions();
    }

    void rescale(float scale) {
        EntityDimensions next = EntityDimensions.scalable(this.unitWidth / 16.0F * scale, this.unitHeight / 16.0F * scale);
        if (next.width() != this.size.width() || next.height() != this.size.height()) {
            this.size = next;
            this.refreshDimensions();
        }
    }

    @Override
    protected void defineSynchedData(Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return this.active && !this.getParent().isDeadOrDying();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return this.active && !this.isInvulnerableTo(source) && this.getParent().hurtPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getParent().getPickResult();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }
}
