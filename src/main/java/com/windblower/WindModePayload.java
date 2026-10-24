package com.windblower;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-authoritative client indicator for whether the Z gust control is armed. */
public record WindModePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<WindModePayload> TYPE = new Type<>(WindBlowerMod.id("wind_mode"));
    public static final StreamCodec<FriendlyByteBuf, WindModePayload> CODEC =
            CustomPacketPayload.codec(WindModePayload::write, WindModePayload::read);

    private void write(FriendlyByteBuf buf) { buf.writeBoolean(enabled); }
    private static WindModePayload read(FriendlyByteBuf buf) { return new WindModePayload(buf.readBoolean()); }
    @Override public Type<WindModePayload> type() { return TYPE; }
}
