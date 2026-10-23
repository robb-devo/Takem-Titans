package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record StoryActionPayload(int npc, int option) implements CustomPacketPayload {
    public static final Type<StoryActionPayload> TYPE = new Type<>(TakemTitans.id("story_action"));
    public static final StreamCodec<ByteBuf, StoryActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, StoryActionPayload::npc, ByteBufCodecs.VAR_INT, StoryActionPayload::option, StoryActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
