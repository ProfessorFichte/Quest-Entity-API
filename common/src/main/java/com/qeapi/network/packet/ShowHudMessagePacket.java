package com.qeapi.network.packet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Shows a fading HUD toast above the hotbar instead of a chat message (see
// ClientHudMessageState/QuestHudMessageRenderer). The client checks hud_messages_enabled itself
// before displaying it, same as any other purely cosmetic client-side toggle.
public record ShowHudMessagePacket(Component message) implements CustomPacketPayload {

    public static final ResourceLocation ID = QuestAPI.id("show_hud_message");
    public static final Type<ShowHudMessagePacket> TYPE = new Type<>(ID);

    public static final Codec<ShowHudMessagePacket> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ComponentSerialization.CODEC.fieldOf("message").forGetter(ShowHudMessagePacket::message)
            ).apply(instance, ShowHudMessagePacket::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowHudMessagePacket> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
