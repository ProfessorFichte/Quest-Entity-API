package com.qeapi.network.packet;

import com.qeapi.component.EntityQuestComponent;
import com.qeapi.network.QENetworking;
import com.qeapi.quest.Quest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Server-to-client packet to open the quest menu.
public record OpenQuestMenuPacket(
        int entityId,
        EntityQuestComponent questComponent,
        List<Quest> availableQuests
) implements CustomPacketPayload {

    public static final Type<OpenQuestMenuPacket> TYPE = new Type<>(QENetworking.OPEN_QUEST_MENU);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestMenuPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, OpenQuestMenuPacket::entityId,
                    EntityQuestComponent.STREAM_CODEC, OpenQuestMenuPacket::questComponent,
                    Quest.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenQuestMenuPacket::availableQuests,
                    OpenQuestMenuPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
