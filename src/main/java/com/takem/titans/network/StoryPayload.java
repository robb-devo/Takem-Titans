package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record StoryPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<StoryPayload> TYPE = new Type<>(TakemTitans.id("story"));
    public static final StreamCodec<ByteBuf, StoryPayload> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(StoryPayload::new, StoryPayload::tag);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
