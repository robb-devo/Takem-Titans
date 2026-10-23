package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record SectionDetachPayload(int entityId, int[] indices) implements CustomPacketPayload {
    public static final Type<SectionDetachPayload> TYPE = new Type<>(TakemTitans.id("section_detach"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SectionDetachPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entityId);
        buf.writeVarIntArray(p.indices);
    }, buf -> new SectionDetachPayload(buf.readVarInt(), buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
