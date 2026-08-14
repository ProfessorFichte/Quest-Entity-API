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
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
    private static Consumer<Entity> nearbyResyncTrigger;

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

    // Wires the platform's forceResyncForNearbyPlayers; called by platform init code alongside init() above.
    public static void initNearbyResyncTrigger(Consumer<Entity> resyncTrigger) {
        nearbyResyncTrigger = resyncTrigger;
    }

    // Forces nearby players' quest-marker sync to refresh immediately, for state changes made from
    // common code (which can't call the platform's own forceResyncForNearbyPlayers directly).
    public static void forceResyncNearbyPlayers(Entity entity) {
        if (nearbyResyncTrigger != null) {
            nearbyResyncTrigger.accept(entity);
        }
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
        playerData.recordEntityLocation(entity);
        setPlayerData(player, playerData);

        EntityQuestComponent component = getEntityQuestComponent(entity);
        if (component != null) {
            EntityQuestComponent updated = component.withActiveQuest(
                    player.getUUID(),
                    EntityQuestComponent.ActiveQuestData.create(questId)
            );
            setEntityQuestComponent(entity, updated);

            // one-way: once accepted from, this giver is protected from despawning for good, even
            // after every quest with it wraps up - no-ops for non-Mob QuestEntity implementations
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
        }

        // no-op unless quest has a provides_map task
        com.qeapi.event.QuestEventHandler.grantStructureMapIfNeeded(player, quest);
        // no-op unless quest has a deliver_item task
        com.qeapi.event.QuestEventHandler.resolveDeliveryTargetIfNeeded(player, entity, quest);
        com.qeapi.event.QuestEventHandler.playAcceptSound(player, quest);

        QuestEntityAPI.LOGGER.debug("Player {} accepted quest {} from entity {}",
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

        QuestEntityAPI.LOGGER.debug("Player {} dismissed quest from entity {}",
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
        com.qeapi.event.QuestEventHandler.playClaimEffects(player, quest);

        playerData.clearEntityProgress(entity.getUUID());
        setPlayerData(player, playerData);

        EntityQuestComponent component = getEntityQuestComponent(entity);
        if (component != null) {
            EntityQuestComponent updated = component.withCompletedQuest(player.getUUID(), activeQuestId.get(),
                    player.serverLevel().getDayTime());
            setEntityQuestComponent(entity, updated);
            resolveQuestLineIfNeeded(player, entity, updated, quest);
        }

        QuestEntityAPI.LOGGER.debug("Player {} completed quest {} from entity {}",
                player.getName().getString(), activeQuestId.get(), entity.getId());
        return true;
    }

    // True for a quest whose sole task is quest_line_choice - the root of a quest line, which never
    // goes through the normal accept/entityProgress pipeline (see QuestLineChoiceTask's javadoc).
    // Mirrors QuestScreen.isLineRootQuest; shared here so the Fabric/NeoForge accept handlers don't
    // each need their own copy of the check.
    public static boolean isLineRootQuest(Quest quest) {
        return quest.tasks().size() == 1 && quest.tasks().get(0) instanceof com.qeapi.quest.task.QuestLineChoiceTask;
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

    // Called right after a quest's rewards are granted and it's marked completed - if it's one step
    // of a questLine and every other step sharing that id is now also completed (checked against
    // postGrantComponent, not a stale pre-grant copy), marks that line resolved in the giver's
    // LineSelectionState. That's what makes the quest_line_choice root's Claim button light up -
    // see QuestLineChoiceTask.isResolved and QuestScreen's canClaimReward.
    public static void resolveQuestLineIfNeeded(ServerPlayer player, Entity entity,
                                                 EntityQuestComponent postGrantComponent, Quest quest) {
        if (quest.questLine().isEmpty()) return;
        String lineId = quest.questLine().get();

        boolean allStepsComplete = true;
        outer:
        for (QuestPool pool : postGrantComponent.getAllQuestPools()) {
            for (Quest candidate : pool.getAllQuests()) {
                if (candidate.questLine().isPresent() && candidate.questLine().get().equals(lineId)
                        && !postGrantComponent.hasCompletedQuest(player.getUUID(), candidate.id())) {
                    allStepsComplete = false;
                    break outer;
                }
            }
        }

        if (allStepsComplete) {
            PlayerQuestData playerData = getPlayerData(player);
            PlayerQuestData.LineSelectionState state = playerData.getLineSelection(entity.getUUID());
            playerData.setLineSelection(entity.getUUID(), state.withResolvedLine(lineId));
            setPlayerData(player, playerData);
        }
    }
}
