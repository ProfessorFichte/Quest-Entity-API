package com.qeapi.network.packet;

import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// One row of the Active Quests screen: the quest itself, its live progress, and everything needed
// to render the giving entity without having to locate it in the world (see PlayerQuestData's
// QuestGiverLocation - this mirrors that, but only carries what the client needs to draw).
public record ActiveQuestEntry(
        UUID entityUuid,
        GiverLocation location,
        Quest quest,
        QuestProgress progress
) {
    public record GiverLocation(
            ResourceLocation entityType,
            ResourceLocation dimension,
            BlockPos pos,
            boolean coordinatesKnown,
            String displayName
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GiverLocation> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, GiverLocation::entityType,
                ResourceLocation.STREAM_CODEC, GiverLocation::dimension,
                BlockPos.STREAM_CODEC, GiverLocation::pos,
                ByteBufCodecs.BOOL, GiverLocation::coordinatesKnown,
                ByteBufCodecs.STRING_UTF8, GiverLocation::displayName,
                GiverLocation::new
        );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveQuestEntry> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ActiveQuestEntry::entityUuid,
            GiverLocation.STREAM_CODEC, ActiveQuestEntry::location,
            Quest.STREAM_CODEC, ActiveQuestEntry::quest,
            QuestProgress.STREAM_CODEC, ActiveQuestEntry::progress,
            ActiveQuestEntry::new
    );
}
