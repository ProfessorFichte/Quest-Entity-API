package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Claims a quest_line_choice root's own capstone reward, once every line it offers is resolved.
// Separate from ClaimRewardsPacket since the root has no QuestProgress to validate against -
// rootQuestId identifies it directly instead, same reasoning as ChooseQuestLinePacket.
public record ClaimQuestLineRootPacket(
        int entityId,
        ResourceLocation rootQuestId
) implements CustomPacketPayload {

    public static final Type<ClaimQuestLineRootPacket> TYPE = new Type<>(QENetworking.CLAIM_QUEST_LINE_ROOT);

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimQuestLineRootPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, ClaimQuestLineRootPacket::entityId,
                    ResourceLocation.STREAM_CODEC, ClaimQuestLineRootPacket::rootQuestId,
                    ClaimQuestLineRootPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
