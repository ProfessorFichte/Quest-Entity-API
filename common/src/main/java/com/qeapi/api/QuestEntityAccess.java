package com.qeapi.api;

import com.qeapi.QuestEntityAPI;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.data.QuestManager;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import com.qeapi.quest.QuestProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Public API for interacting with the Quest Entity system.
 * Use this class to programmatically manage quests.
 */
public final class QuestEntityAccess {

    private QuestEntityAccess() {}

    // set by platform init
    private static Function<Entity, EntityQuestComponent> entityComponentGetter;
    private static BiConsumer<Entity, EntityQuestComponent> entityComponentSetter;
    private static Function<ServerPlayer, PlayerQuestData> playerDataGetter;
    private static BiConsumer<ServerPlayer, PlayerQuestData> playerDataSetter;

    // Wires the platform-specific implementations; called by platform init code.
    public static void init(
            Function<Entity, EntityQuestComponent> entityGetter,
            BiConsumer<Entity, EntityQuestComponent> entitySetter,
            Function<ServerPlayer, PlayerQuestData> playerGetter,
            BiConsumer<ServerPlayer, PlayerQuestData> playerSetter
    ) {
        entityComponentGetter = entityGetter;
        entityComponentSetter = entitySetter;
        playerDataGetter = playerGetter;
        playerDataSetter = playerSetter;
        QuestEntityAPI.LOGGER.info("QuestEntityAccess initialized with platform-specific implementations");
    }

    /**
     * Opens the quest menu for a player interacting with an entity.
     * Used by {@link QuestEntity#onQuestInteraction} for mod-author-implemented quest entities.
     */
    public static void openQuestMenu(ServerPlayer player, Entity entity) {
        EntityQuestComponent component = getEntityQuestComponent(entity);
        Optional<QuestPool> poolOpt = getQuestPool(entity);
        if (component == null || poolOpt.isEmpty()) {
            QuestEntityAPI.LOGGER.debug("No quest pool found for entity {} when opening quest menu", entity.getId());
            return;
        }

        com.qeapi.command.QuestCommands.QuestGuiOpener.open(player, entity.getId(), component, poolOpt.get().getAllQuests());
    }

    /**
     * Quest pool for an entity, resolved from its quest pool tag.
     */
    public static Optional<QuestPool> getQuestPool(Entity entity) {
        ResourceLocation tagId = getQuestPoolId(entity);
        if (tagId == null) return Optional.empty();

        return QuestManager.getQuestPoolFromTag(tagId);
    }

    /**
     * Quest pool tag ID for an entity, or null if it doesn't have quests.
     */
    public static ResourceLocation getQuestPoolId(Entity entity) {
        if (entity instanceof QuestEntity questEntity) {
            return questEntity.getQuestPoolId();
        }

        EntityQuestComponent component = getEntityQuestComponent(entity);
        return component != null ? component.questPoolId() : null;
    }

    /**
     * The EntityQuestComponent attached to an entity, or null if not present.
     */
    public static EntityQuestComponent getEntityQuestComponent(Entity entity) {
        if (entityComponentGetter != null) {
            return entityComponentGetter.apply(entity);
        }
        return null;
    }

    public static void setEntityQuestComponent(Entity entity, EntityQuestComponent component) {
        if (entityComponentSetter != null) {
            entityComponentSetter.accept(entity, component);
        }
    }

    /**
     * The player's quest data, or a new empty instance if not present.
     */
    public static PlayerQuestData getPlayerData(ServerPlayer player) {
        if (playerDataGetter != null) {
            PlayerQuestData data = playerDataGetter.apply(player);
            return data != null ? data : new PlayerQuestData();
        }
        return new PlayerQuestData();
    }

    public static void setPlayerData(ServerPlayer player, PlayerQuestData data) {
        if (playerDataSetter != null) {
            playerDataSetter.accept(player, data);
        }
    }

    /**
     * Accepts a quest for a player from an entity. Returns false if requirements aren't met.
     */
    public static boolean acceptQuest(ServerPlayer player, Entity entity, ResourceLocation questId) {
        Optional<QuestPool> poolOpt = getQuestPool(entity);
        if (poolOpt.isEmpty()) return false;

        Quest quest = findQuestById(poolOpt.get(), questId);
        if (quest == null) return false;

        if (!quest.meetsRequirements(player)) {
            return false;
        }

        PlayerQuestData playerData = getPlayerData(player);
        playerData.startQuest(entity.getUUID(), questId);
        setPlayerData(player, playerData);

        EntityQuestComponent component = getEntityQuestComponent(entity);
        if (component != null) {
            EntityQuestComponent updated = component.withActiveQuest(
                    player.getUUID(),
                    EntityQuestComponent.ActiveQuestData.create(questId)
            );
            setEntityQuestComponent(entity, updated);
        }

        // no-op unless quest has a provides_map task
        com.qeapi.event.QuestEventHandler.grantStructureMapIfNeeded(player, quest);

        QuestEntityAPI.LOGGER.info("Player {} accepted quest {} from entity {}",
                player.getName().getString(), questId, entity.getId());
        return true;
    }

    /**
     * Dismisses a player's active quest with an entity. Returns false if none was active.
     */
    public static boolean dismissQuest(ServerPlayer player, Entity entity) {
        PlayerQuestData playerData = getPlayerData(player);
        if (!playerData.hasActiveQuestForEntity(entity.getUUID())) {
            return false;
        }

        playerData.clearEntityProgress(entity.getUUID());
        setPlayerData(player, playerData);

        EntityQuestComponent component = getEntityQuestComponent(entity);
        if (component != null) {
            EntityQuestComponent updated = component.withoutActiveQuest(player.getUUID());
            setEntityQuestComponent(entity, updated);
        }

        QuestEntityAPI.LOGGER.info("Player {} dismissed quest from entity {}",
                player.getName().getString(), entity.getId());
        return true;
    }

    /**
     * Completes a quest and grants rewards, if the player's active quest for this entity is complete.
     */
    public static boolean completeQuest(ServerPlayer player, Entity entity) {
        PlayerQuestData playerData = getPlayerData(player);
        Optional<ResourceLocation> activeQuestId = playerData.getActiveQuestId(entity.getUUID());

        if (activeQuestId.isEmpty()) return false;

        Optional<QuestPool> poolOpt = getQuestPool(entity);
        if (poolOpt.isEmpty()) return false;

        Quest quest = findQuestById(poolOpt.get(), activeQuestId.get());
        if (quest == null) return false;

        QuestProgress progress = playerData.getProgressForEntity(entity.getUUID()).orElse(null);
        if (progress == null) return false;

        if (!quest.isComplete(progress)) {
            return false;
        }

        // No reward-choice-pool selection here (that's only made via the GUI claim flow), so pools are left empty.
        java.util.List<java.util.List<Integer>> emptyPoolChoices = quest.rewardChoicePools().stream()
                .map(pool -> java.util.List.<Integer>of())
                .toList();
        quest.grantRewards(player, entity, emptyPoolChoices);
        com.qeapi.event.QuestEventHandler.grantVillagerTradeXp(entity, quest.tier());

        playerData.clearEntityProgress(entity.getUUID());
        setPlayerData(player, playerData);

        EntityQuestComponent component = getEntityQuestComponent(entity);
        if (component != null) {
            EntityQuestComponent updated = component.withCompletedQuest(player.getUUID(), activeQuestId.get(),
                    player.serverLevel().getDayTime());
            setEntityQuestComponent(entity, updated);
        }

        QuestEntityAPI.LOGGER.info("Player {} completed quest {} from entity {}",
                player.getName().getString(), activeQuestId.get(), entity.getId());
        return true;
    }

    // Public so command/networking code doesn't need its own duplicate of this search.
    public static Quest findQuestById(QuestPool pool, ResourceLocation questId) {
        for (List<Quest> tierQuests : pool.getQuestsByTier().values()) {
            for (Quest quest : tierQuests) {
                if (quest.id().equals(questId)) {
                    return quest;
                }
            }
        }
        return null;
    }
}
