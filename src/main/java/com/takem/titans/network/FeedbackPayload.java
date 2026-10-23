package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record FeedbackPayload(int kind, int a, int b, int c, String text) implements CustomPacketPayload {
    public static final int HIT = 0;
    public static final int KILL = 1;
    public static final int SHAKE = 2;
    public static final int GRAB = 3;
    public static final int WAYPOINT = 4;
    public static final int MESSAGE = 5;
    public static final int BLADE_BREAK = 6;
    public static final int REMOTE_BOOST = 7;
    public static final int COMBO = 8;
    public static final int OVERDRIVE = 9;
    public static final int IMPACT_FRAME = 10;
    public static final Type<FeedbackPayload> TYPE = new Type<>(TakemTitans.id("feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        FeedbackPayload::kind,
        ByteBufCodecs.VAR_INT,
        FeedbackPayload::a,
        ByteBufCodecs.VAR_INT,
        FeedbackPayload::b,
        ByteBufCodecs.VAR_INT,
        FeedbackPayload::c,
        ByteBufCodecs.STRING_UTF8,
        FeedbackPayload::text,
        FeedbackPayload::new
    );

    public static FeedbackPayload of(int kind, int a, int b, int c) {
        return new FeedbackPayload(kind, a, b, c, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
