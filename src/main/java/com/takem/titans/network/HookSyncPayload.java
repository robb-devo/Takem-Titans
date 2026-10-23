package com.takem.titans.network;

import com.takem.titans.TakemTitans;
import com.takem.titans.odm.HookSide;
import com.takem.titans.odm.HookState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record HookSyncPayload(int playerId, HookState state) implements CustomPacketPayload {
    public static final Type<HookSyncPayload> TYPE = new Type<>(TakemTitans.id("hook_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HookSyncPayload> STREAM_CODEC = StreamCodec.ofMember(HookSyncPayload::write, HookSyncPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.playerId);
        buf.writeByte(this.state.side.ordinal());
        this.state.write(buf);
    }

    private static HookSyncPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        HookState s = new HookState(HookSide.byIndex(buf.readByte()));
        s.read(buf);
        return new HookSyncPayload(id, s);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
