package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

// Server-to-client packet to sync quest progress updates.
public record QuestProgressPacket(
        int entityId,
        ResourceLocation questId,
        Map<Integer, Integer> taskProgress
) implements CustomPacketPayload {

    public static final Type<QuestProgressPacket> TYPE = new Type<>(QENetworking.QUEST_PROGRESS);

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestProgressPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, QuestProgressPacket::entityId,
                    ResourceLocation.STREAM_CODEC, QuestProgressPacket::questId,
                    ByteBufCodecs.map(
                            java.util.HashMap::new,
                            ByteBufCodecs.INT,
                            ByteBufCodecs.INT
                    ), QuestProgressPacket::taskProgress,
                    QuestProgressPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
