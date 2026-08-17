package com.qeapi.network.packet;

import com.mojang.datafixers.util.Pair;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.network.QENetworking;
import com.qeapi.quest.Quest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// activeLine/resolvedLines/claimedRoots/acceptedRoots are the viewed entity's
// PlayerQuestData.LineSelectionState, giver-scoped rather than per-quest (see that record's
// javadoc). The client needs all four to classify a quest_line_choice root in availableQuests as
// unaccepted, pickable, mid-line (activeLine present), or claimable (QuestLineChoiceTask.isResolved
// and not in claimedRoots). activeLine is a 0-or-1-element list rather than an Optional stream
// codec, matching the other three fields' list encoding here.
public record OpenQuestMenuPacket(
        int entityId,
        EntityQuestComponent questComponent,
        List<Quest> availableQuests,
        List<String> activeLine,
        List<String> resolvedLines,
        List<ResourceLocation> claimedRoots,
        List<ResourceLocation> acceptedRoots
) implements CustomPacketPayload {

    public static final Type<OpenQuestMenuPacket> TYPE = new Type<>(QENetworking.OPEN_QUEST_MENU);

    // composite() tops out at 6 slots for this record's 7 fields, so claimedRoots and acceptedRoots
    // share one wire slot as a Pair, unpacked again in the factory below.
    private static final StreamCodec<RegistryFriendlyByteBuf, Pair<List<ResourceLocation>, List<ResourceLocation>>> ROOT_ID_LISTS_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), Pair::getFirst,
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), Pair::getSecond,
                    Pair::of
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestMenuPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, OpenQuestMenuPacket::entityId,
                    EntityQuestComponent.STREAM_CODEC, OpenQuestMenuPacket::questComponent,
                    Quest.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenQuestMenuPacket::availableQuests,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenQuestMenuPacket::activeLine,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenQuestMenuPacket::resolvedLines,
                    ROOT_ID_LISTS_CODEC, packet -> Pair.of(packet.claimedRoots(), packet.acceptedRoots()),
                    (entityId, questComponent, availableQuests, activeLine, resolvedLines, rootIdLists) ->
                            new OpenQuestMenuPacket(entityId, questComponent, availableQuests, activeLine,
                                    resolvedLines, rootIdLists.getFirst(), rootIdLists.getSecond())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
