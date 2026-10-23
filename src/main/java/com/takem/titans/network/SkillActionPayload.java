package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record SkillActionPayload(int action, int skill) implements CustomPacketPayload {
    public static final int UNLOCK = 0;
    public static final int RESET = 1;
    public static final Type<SkillActionPayload> TYPE = new Type<>(TakemTitans.id("skill_action"));
    public static final StreamCodec<ByteBuf, SkillActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SkillActionPayload::action, ByteBufCodecs.VAR_INT, SkillActionPayload::skill, SkillActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
