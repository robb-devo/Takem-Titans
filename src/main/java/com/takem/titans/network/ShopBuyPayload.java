package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ShopBuyPayload(int npc, int offer, int count) implements CustomPacketPayload {
    public static final Type<ShopBuyPayload> TYPE = new Type<>(TakemTitans.id("shop_buy"));
    public static final StreamCodec<ByteBuf, ShopBuyPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ShopBuyPayload::npc,
        ByteBufCodecs.VAR_INT,
        ShopBuyPayload::offer,
        ByteBufCodecs.VAR_INT,
        ShopBuyPayload::count,
        ShopBuyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
