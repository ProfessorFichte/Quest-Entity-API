package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Client-to-server packet to request reopening the merchant trading menu, used when clicking the
// back button on the quest screen (when opened from a merchant).
public record RequestMerchantMenuPacket(
        int entityId
) implements CustomPacketPayload {

    public static final Type<RequestMerchantMenuPacket> TYPE = new Type<>(QENetworking.REQUEST_MERCHANT_MENU);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMerchantMenuPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, RequestMerchantMenuPacket::entityId,
                    RequestMerchantMenuPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
