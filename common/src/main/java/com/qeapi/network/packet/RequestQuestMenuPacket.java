package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Sent when clicking the Q button on the merchant screen.
public record RequestQuestMenuPacket(
        int entityId
) implements CustomPacketPayload {

    public static final Type<RequestQuestMenuPacket> TYPE = new Type<>(QENetworking.REQUEST_QUEST_MENU);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestQuestMenuPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, RequestQuestMenuPacket::entityId,
                    RequestQuestMenuPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
