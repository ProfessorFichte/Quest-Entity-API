package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Cancels the player's active line for a quest_line_choice root, sent from that line's own
// bordered icon in the picker row. rootQuestId/lineId are explicit for the same reason as
// ChooseQuestLinePacket: the root never goes through accept/entityProgress, so there's no
// active quest to look it up by.
public record CancelQuestLinePacket(
        int entityId,
        ResourceLocation rootQuestId,
        String lineId
) implements CustomPacketPayload {

    public static final Type<CancelQuestLinePacket> TYPE = new Type<>(QENetworking.CANCEL_QUEST_LINE);

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelQuestLinePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, CancelQuestLinePacket::entityId,
                    ResourceLocation.STREAM_CODEC, CancelQuestLinePacket::rootQuestId,
                    ByteBufCodecs.STRING_UTF8, CancelQuestLinePacket::lineId,
                    CancelQuestLinePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
