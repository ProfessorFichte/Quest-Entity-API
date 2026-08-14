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
        boolean allQuestsCompleted,
        boolean enraged
) implements CustomPacketPayload {

    public static final ResourceLocation ID = QuestEntityAPI.id("sync_entity_quests");
    public static final Type<SyncEntityQuestsPacket> TYPE = new Type<>(ID);

    // The four booleans are packed into one flags byte so this stays within StreamCodec.composite's
    // 6-slot limit (and leaves room to grow), hence a hand-written codec rather than composite.
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEntityQuestsPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        ByteBufCodecs.INT.encode(buf, packet.entityId);
                        ByteBufCodecs.STRING_UTF8.encode(buf, packet.entityUuid.toString());
                        ResourceLocation.STREAM_CODEC.encode(buf, packet.questPoolId);
                        int flags = (packet.hasActiveQuest ? 1 : 0)
                                | (packet.isQuestComplete ? 2 : 0)
                                | (packet.allQuestsCompleted ? 4 : 0)
                                | (packet.enraged ? 8 : 0);
                        buf.writeByte(flags);
                    },
                    buf -> {
                        int entityId = ByteBufCodecs.INT.decode(buf);
                        UUID entityUuid = UUID.fromString(ByteBufCodecs.STRING_UTF8.decode(buf));
                        ResourceLocation questPoolId = ResourceLocation.STREAM_CODEC.decode(buf);
                        int flags = buf.readByte();
                        return new SyncEntityQuestsPacket(entityId, entityUuid, questPoolId,
                                (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
