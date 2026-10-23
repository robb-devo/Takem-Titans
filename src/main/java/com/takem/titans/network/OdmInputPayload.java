package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record OdmInputPayload(int flags) implements CustomPacketPayload {
    public static final int BOOSTING = 1;
    public static final int REEL_LEFT = 2;
    public static final int REEL_RIGHT = 4;
    public static final int AIRBORNE = 8;
    public static final Type<OdmInputPayload> TYPE = new Type<>(TakemTitans.id("odm_input"));
    public static final StreamCodec<ByteBuf, OdmInputPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(OdmInputPayload::new, OdmInputPayload::flags);

    public boolean has(int flag) {
        return (this.flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
