package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Sent when opening the Active Quests screen via keybind.
public record RequestActiveQuestsPacket() implements CustomPacketPayload {

    public static final Type<RequestActiveQuestsPacket> TYPE = new Type<>(QENetworking.REQUEST_ACTIVE_QUESTS);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestActiveQuestsPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestActiveQuestsPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
