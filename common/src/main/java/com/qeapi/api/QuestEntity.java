package com.qeapi.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Interface for entities that can provide quests to players.
 * Implement on a custom entity to enable quest functionality.
 */
public interface QuestEntity {

    /**
     * Quest pool tag ID for this entity, referencing
     * data/[namespace]/tags/entity_quests/[path].json.
     */
    ResourceLocation getQuestPoolId();

    /**
     * Whether this entity can currently offer quests.
     * Override to add conditions (time of day, entity state, etc).
     */
    default boolean canProvideQuests() {
        return true;
    }

    /**
     * Called when a player interacts with this entity to access quests.
     * Default opens the quest GUI.
     */
    default void onQuestInteraction(ServerPlayer player) {
        if (this instanceof Entity entity) {
            QuestEntityAccess.openQuestMenu(player, entity);
        }
    }

    /**
     * Whether to show the quest marker above this entity's head.
     */
    default boolean shouldShowQuestMarker() {
        return canProvideQuests();
    }

    /**
     * Interaction priority; higher takes precedence over other interactions. Default 0.
     */
    default int getQuestInteractionPriority() {
        return 0;
    }
}
