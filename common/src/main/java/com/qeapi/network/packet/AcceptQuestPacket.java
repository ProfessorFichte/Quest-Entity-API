package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client-to-server packet to accept a quest.
public record AcceptQuestPacket(
        int entityId,
        ResourceLocation questId
) implements CustomPacketPayload {

    public static final Type<AcceptQuestPacket> TYPE = new Type<>(QENetworking.ACCEPT_QUEST);

    public static final StreamCodec<RegistryFriendlyByteBuf, AcceptQuestPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, AcceptQuestPacket::entityId,
                    ResourceLocation.STREAM_CODEC, AcceptQuestPacket::questId,
                    AcceptQuestPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
