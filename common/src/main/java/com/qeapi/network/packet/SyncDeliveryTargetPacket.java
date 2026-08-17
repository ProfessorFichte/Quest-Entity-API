package com.qeapi.network.packet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.item.QuestItemDefinition;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

// Tells the client this entity is the resolved deliver_item target for one of the player's active
// quests, and what item it wants - the same role SyncEntityQuestsPacket plays for the
// exclamation-mark marker, but for the floating item icon (see QuestMarkerRenderer/ClientQuestCache).
// active=false clears the marker again, whether delivered or the quest ended.
public record SyncDeliveryTargetPacket(
        int entityId,
        UUID entityUuid,
        boolean active,
        Optional<ResourceLocation> itemId,
        Optional<QuestItemDefinition> questItem
) implements CustomPacketPayload {

    public static final ResourceLocation ID = QuestAPI.id("sync_delivery_target");
    public static final Type<SyncDeliveryTargetPacket> TYPE = new Type<>(ID);

    public static final Codec<SyncDeliveryTargetPacket> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("entity_id").forGetter(SyncDeliveryTargetPacket::entityId),
                    UUIDUtil.STRING_CODEC.fieldOf("entity_uuid").forGetter(SyncDeliveryTargetPacket::entityUuid),
                    Codec.BOOL.fieldOf("active").forGetter(SyncDeliveryTargetPacket::active),
                    ResourceLocation.CODEC.optionalFieldOf("item_id").forGetter(SyncDeliveryTargetPacket::itemId),
                    QuestItemDefinition.CODEC.optionalFieldOf("quest_item").forGetter(SyncDeliveryTargetPacket::questItem)
            ).apply(instance, SyncDeliveryTargetPacket::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDeliveryTargetPacket> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
