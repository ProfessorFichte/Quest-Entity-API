package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

// poolChoices: chosen option index per reward_choice_pool, in quest order.
// rewardTargetSlots: inventory slot picked for each TargetItemReward, or -1 (pool options can't
// be TargetItemReward - see its javadoc - so they never need a slot of their own).
// bringItemSlots: inventory slots chosen to satisfy each BringItemTask; empty falls back to the
// old greedy slot-order consumption.
public record ClaimRewardsPacket(
        int entityId,
        List<List<Integer>> poolChoices,
        List<Integer> rewardTargetSlots,
        List<List<Integer>> bringItemSlots
) implements CustomPacketPayload {

    public static final Type<ClaimRewardsPacket> TYPE = new Type<>(QENetworking.CLAIM_REWARDS);

    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST_CODEC =
            ByteBufCodecs.INT.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, List<List<Integer>>> NESTED_INT_LIST_CODEC =
            INT_LIST_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRewardsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, ClaimRewardsPacket::entityId,
                    NESTED_INT_LIST_CODEC.cast(), ClaimRewardsPacket::poolChoices,
                    INT_LIST_CODEC.cast(), ClaimRewardsPacket::rewardTargetSlots,
                    NESTED_INT_LIST_CODEC.cast(), ClaimRewardsPacket::bringItemSlots,
                    ClaimRewardsPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
