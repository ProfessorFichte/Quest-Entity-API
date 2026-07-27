package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Client-to-server packet to dismiss/cancel an active quest.
public record DismissQuestPacket(
        int entityId
) implements CustomPacketPayload {

    public static final Type<DismissQuestPacket> TYPE = new Type<>(QENetworking.DISMISS_QUEST);

    public static final StreamCodec<RegistryFriendlyByteBuf, DismissQuestPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, DismissQuestPacket::entityId,
                    DismissQuestPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
