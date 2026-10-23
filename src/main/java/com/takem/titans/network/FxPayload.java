package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record FxPayload(int kind, double x, double y, double z, float a, float b, float c, float d) implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(TakemTitans.id("fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.kind);
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeFloat(p.a);
            buf.writeFloat(p.b);
            buf.writeFloat(p.c);
            buf.writeFloat(p.d);
        },
        buf -> new FxPayload(
                buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
