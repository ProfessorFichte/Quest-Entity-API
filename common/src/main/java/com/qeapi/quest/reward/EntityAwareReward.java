package com.qeapi.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// Secondary capability for rewards that need the granting entity, not just the player (e.g. to record per-entity state via QuestEntityAccess) - checked alongside TargetItemReward; a reward only needs one.
public interface EntityAwareReward {
    void grantWithEntity(ServerPlayer player, Entity entity);
}
