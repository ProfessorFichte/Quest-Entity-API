package com.qeapi.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// Secondary capability for a reward that needs to know which entity granted it, not just the
// player - e.g. to record per-entity player state via QuestEntityAccess. Checked alongside
// TargetItemReward in Quest.grantRewards; a reward can only need one of the two extra contexts.
public interface EntityAwareReward {
    void grantWithEntity(ServerPlayer player, Entity entity);
}
