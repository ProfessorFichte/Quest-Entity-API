package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client-to-server packet to dismiss (un-accept) an already-accepted, not-yet-claimed
// quest_line_choice root, sent when clicking that root's own checkbox a second time - mirrors
// CancelQuestLinePacket's shape, just scoped to the whole root rather than a single line.
public record DismissQuestLineRootPacket(
        int entityId,
        ResourceLocation rootQuestId
) implements CustomPacketPayload {

    public static final Type<DismissQuestLineRootPacket> TYPE = new Type<>(QENetworking.DISMISS_QUEST_LINE_ROOT);

    public static final StreamCodec<RegistryFriendlyByteBuf, DismissQuestLineRootPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, DismissQuestLineRootPacket::entityId,
                    ResourceLocation.STREAM_CODEC, DismissQuestLineRootPacket::rootQuestId,
                    DismissQuestLineRootPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
