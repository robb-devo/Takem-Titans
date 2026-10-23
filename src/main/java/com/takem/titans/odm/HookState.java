package com.takem.titans.odm;

import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HookState {
    public final HookSide side;
    public HookState.Phase phase = HookState.Phase.IDLE;
    public Vec3 pos = Vec3.ZERO;
    public Vec3 prevPos = Vec3.ZERO;
    public Vec3 vel = Vec3.ZERO;
    public int entityId = -1;
    public int partIndex = -1;
    public Vec3 localOffset = Vec3.ZERO;
    public double ropeLength;
    public double traveled;
    public int age;
    public boolean reeling;
    public boolean tether;

    public HookState(HookSide side) {
        this.side = side;
    }

    public boolean isActive() {
        return this.phase != HookState.Phase.IDLE;
    }

    public boolean isAttached() {
        return this.phase == HookState.Phase.ATTACHED;
    }

    public void reset() {
        this.phase = HookState.Phase.IDLE;
        this.entityId = -1;
        this.partIndex = -1;
        this.reeling = false;
        this.tether = false;
        this.age = 0;
        this.traveled = 0.0;
    }

    public Vec3 anchor(Level level, float partialTick) {
        if (this.entityId >= 0) {
            Entity e = level.getEntity(this.entityId);
            if (e instanceof AbstractTitan titan && this.partIndex >= 0) {
                return titan.partAnchor(this.partIndex, this.localOffset, partialTick);
            }

            if (e instanceof FallingSectionEntity fs) {
                return fs.localToWorld(this.localOffset, partialTick);
            }

            if (e != null) {
                return entityAnchor(e, this.localOffset, partialTick);
            }
        }

        return this.pos;
    }

    public Vec3 renderPos(Level level, float partialTick) {
        return this.phase == HookState.Phase.ATTACHED ? this.anchor(level, partialTick) : this.prevPos.lerp(this.pos, (double)partialTick);
    }

    public static float entityYaw(Entity e, float partialTick) {
        return e instanceof LivingEntity le ? Mth.rotLerp(partialTick, le.yBodyRotO, le.yBodyRot) : e.getViewYRot(partialTick);
    }

    public static Vec3 entityAnchor(Entity e, Vec3 local, float partialTick) {
        float yaw = entityYaw(e, partialTick);
        Vec3 base = e.getPosition(partialTick);
        return base.add(local.yRot((float)Math.toRadians((double)(-yaw))));
    }

    public static Vec3 toLocal(Entity e, Vec3 worldPoint) {
        float yaw = entityYaw(e, 1.0F);
        return worldPoint.subtract(e.position()).yRot((float)Math.toRadians((double)yaw));
    }

    public static Vec3 launcherPos(Player player, HookSide side, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Vec3 base = player.getPosition(partialTick);
        Vec3 local = new Vec3((double)(-side.sign) * 0.36, 0.95, -0.05);
        return base.add(local.yRot((float)Math.toRadians((double)(-bodyYaw))));
    }

    public void copyFrom(HookState o) {
        this.phase = o.phase;
        this.pos = o.pos;
        this.prevPos = o.prevPos;
        this.vel = o.vel;
        this.entityId = o.entityId;
        this.partIndex = o.partIndex;
        this.localOffset = o.localOffset;
        this.ropeLength = o.ropeLength;
        this.traveled = o.traveled;
        this.age = o.age;
        this.reeling = o.reeling;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(this.phase.ordinal());
        writeVec(buf, this.pos);
        writeVec(buf, this.vel);
        buf.writeVarInt(this.entityId + 1);
        buf.writeByte(this.partIndex + 1);
        writeVec(buf, this.localOffset);
        buf.writeFloat((float)this.ropeLength);
        buf.writeBoolean(this.reeling);
    }

    public void read(RegistryFriendlyByteBuf buf) {
        this.phase = HookState.Phase.byId(buf.readByte());
        this.pos = readVec(buf);
        this.prevPos = this.pos;
        this.vel = readVec(buf);
        this.entityId = buf.readVarInt() - 1;
        this.partIndex = buf.readByte() - 1;
        this.localOffset = readVec(buf);
        this.ropeLength = (double)buf.readFloat();
        this.reeling = buf.readBoolean();
    }

    public static void writeVec(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    public static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Nullable
    public Entity anchoredEntity(Level level) {
        return this.entityId >= 0 ? level.getEntity(this.entityId) : null;
    }

    public static enum Phase {
        IDLE,
        FLYING,
        ATTACHED,
        RETRACTING;

        public static HookState.Phase byId(int id) {
            HookState.Phase[] v = values();
            return id >= 0 && id < v.length ? v[id] : IDLE;
        }
    }
}
