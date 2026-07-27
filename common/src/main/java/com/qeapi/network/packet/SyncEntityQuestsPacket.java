package com.qeapi.network.packet;

import com.qeapi.QuestEntityAPI;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Server-to-client packet to sync entity quest presence, sent when the player approaches an
// entity with quests so the marker can render.
public record SyncEntityQuestsPacket(
        int entityId,
        UUID entityUuid,
        ResourceLocation questPoolId,
        boolean hasActiveQuest,
        boolean isQuestComplete,
        boolean allQuestsCompleted
) implements CustomPacketPayload {

    public static final ResourceLocation ID = QuestEntityAPI.id("sync_entity_quests");
    public static final Type<SyncEntityQuestsPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEntityQuestsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncEntityQuestsPacket::entityId,
                    ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), SyncEntityQuestsPacket::entityUuid,
                    ResourceLocation.STREAM_CODEC, SyncEntityQuestsPacket::questPoolId,
                    ByteBufCodecs.BOOL, SyncEntityQuestsPacket::hasActiveQuest,
                    ByteBufCodecs.BOOL, SyncEntityQuestsPacket::isQuestComplete,
                    ByteBufCodecs.BOOL, SyncEntityQuestsPacket::allQuestsCompleted,
                    SyncEntityQuestsPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
