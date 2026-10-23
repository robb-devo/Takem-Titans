package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record OdmActionPayload(int action, int value) implements CustomPacketPayload {
    public static final int DASH = 0;
    public static final int RELOAD = 1;
    public static final int IMPACT = 2;
    public static final int STRUGGLE = 3;
    public static final int SPIN = 4;
    public static final int RUSH = 5;
    public static final int SONIC_BOOM = 6;
    public static final int SLASH_WAVE = 7;
    public static final int DIVE = 8;
    public static final int SLAM = 9;
    public static final Type<OdmActionPayload> TYPE = new Type<>(TakemTitans.id("odm_action"));
    public static final StreamCodec<ByteBuf, OdmActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, OdmActionPayload::action, ByteBufCodecs.VAR_INT, OdmActionPayload::value, OdmActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
