package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client-to-server packet to pick a line from a quest_line_choice root's picker. rootQuestId is
// sent explicitly (rather than resolved server-side via getActiveQuest) since the root never goes
// through the normal accept/entityProgress pipeline, so there's no "active quest" to look it up by.
public record ChooseQuestLinePacket(
        int entityId,
        ResourceLocation rootQuestId,
        String lineId
) implements CustomPacketPayload {

    public static final Type<ChooseQuestLinePacket> TYPE = new Type<>(QENetworking.CHOOSE_QUEST_LINE);

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseQuestLinePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, ChooseQuestLinePacket::entityId,
                    ResourceLocation.STREAM_CODEC, ChooseQuestLinePacket::rootQuestId,
                    ByteBufCodecs.STRING_UTF8, ChooseQuestLinePacket::lineId,
                    ChooseQuestLinePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
