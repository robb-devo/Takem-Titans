package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import com.takem.titans.odm.HookState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.phys.Vec3;

public record HookActionPayload(int side, int action, Vec3 pos, Vec3 vel, int entityId, int part, Vec3 localOffset, float rope) implements CustomPacketPayload {
    public static final int FIRE = 0;
    public static final int ATTACH = 1;
    public static final int RELEASE = 2;
    public static final Type<HookActionPayload> TYPE = new Type<>(TakemTitans.id("hook_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HookActionPayload> STREAM_CODEC = StreamCodec.ofMember(
        HookActionPayload::write, HookActionPayload::read
    );

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(this.side);
        buf.writeByte(this.action);
        HookState.writeVec(buf, this.pos);
        HookState.writeVec(buf, this.vel);
        buf.writeVarInt(this.entityId + 1);
        buf.writeByte(this.part + 1);
        HookState.writeVec(buf, this.localOffset);
        buf.writeFloat(this.rope);
    }

    private static HookActionPayload read(RegistryFriendlyByteBuf buf) {
        return new HookActionPayload(
            buf.readByte(),
            buf.readByte(),
            HookState.readVec(buf),
            HookState.readVec(buf),
            buf.readVarInt() - 1,
            buf.readByte() - 1,
            HookState.readVec(buf),
            buf.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
