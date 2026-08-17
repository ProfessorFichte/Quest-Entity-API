package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record ActiveQuestsPacket(
        List<ActiveQuestEntry> entries
) implements CustomPacketPayload {

    public static final Type<ActiveQuestsPacket> TYPE = new Type<>(QENetworking.ACTIVE_QUESTS);

    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveQuestsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ActiveQuestEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), ActiveQuestsPacket::entries,
                    ActiveQuestsPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
