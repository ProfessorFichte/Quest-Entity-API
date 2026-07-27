package com.qeapi.network.packet;

import com.qeapi.network.QENetworking;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

// Client-to-server packet to claim rewards for a completed quest. poolChoices holds, per reward
// choice pool (in quest-defined order), the option indices the player selected - empty lists for
// quests with no reward choice pools.
//
// rewardTargetSlots holds, per entry in the quest's flat rewards list (in quest-defined order),
// the inventory slot (into player.getInventory().items) the player picked as that reward's
// target item, or -1 if that reward isn't a TargetItemReward. Target-item rewards inside
// reward_choice_pools aren't supported (see TargetItemReward's javadoc) so pool options never
// need a slot of their own.
//
// bringItemSlots holds, per task in the quest's tasks list (in quest-defined order), the
// inventory slots the player explicitly chose to satisfy that BringItemTask - empty for tasks
// that aren't BringItemTask, or when the player never opened the picker (server falls back to the
// old greedy slot-order consumption in that case).
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
