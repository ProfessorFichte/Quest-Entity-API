package com.qeapi.event;

import com.qeapi.QuestEntityAPI;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.config.QuestEntityAPIConfig;
import com.qeapi.data.QuestManager;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.task.*;
import com.qeapi.mixin.VillagerXpAccessor;
import com.qeapi.util.StructureMapUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.BiConsumer;

// Handles game events that can progress quests.
public final class QuestEventHandler {

    private QuestEventHandler() {}

    // Platform-specific progress sync implementation. Forces the periodic nearby-entity sync
    // (which normally only sends once per entity per player, on first proximity detection) to
    // re-check and re-send this specific entity's quest state on its next pass - otherwise a
    // passively-tracked task (kill, brew, travel, item-use) reaching completion while the
    // entity stays continuously nearby would never flip the marker to green/grey.
    private static BiConsumer<ServerPlayer, UUID> progressSyncHandler;

    public static void setProgressSyncHandler(BiConsumer<ServerPlayer, UUID> handler) {
        progressSyncHandler = handler;
    }

    // Grants a villager trading XP toward their profession level on quest completion, scaled by
    // tier. No-op for non-villager quest-givers.
    //
    // Villager.setVillagerXp(int) is a plain field setter with no side effects - the actual
    // level-up check/apply (shouldIncreaseLevel/increaseMerchantCareer) is private, only ever run
    // from the protected, trade-driven rewardTradeXp() on a deferred 40-tick timer. This replicates
    // that logic via VillagerXpAccessor, applying immediately instead (also refreshing their trade
    // list right away via increaseMerchantCareer's own updateTrades(), rather than waiting on
    // vanilla's timer).
    public static void grantVillagerTradeXp(Entity entity, int tier) {
        if (!(entity instanceof Villager villager)) return;
        if (!QuestEntityAPIConfig.get().villager_trade_xp_enabled) return;

        int xpAmount = tier * QuestEntityAPIConfig.get().villager_trade_xp_per_tier;
        villager.setVillagerXp(villager.getVillagerXp() + xpAmount);

        VillagerXpAccessor accessor = (VillagerXpAccessor) villager;
        if (accessor.qe_api$shouldIncreaseLevel()) {
            accessor.qe_api$increaseMerchantCareer();
        }

        QuestEntityAPI.LOGGER.debug("Granted {} trade XP (tier {}) to villager {}",
                xpAmount, tier, villager.getUUID());
    }

    // Grants a one-time treasure map for any find_structure/entity_kill task in this quest with
    // provides_map set, searching from the player's current position. Call once, when the quest
    // is accepted.
    //
    // Never re-grants a map for the same quest+task once granted, even across decline/re-accept
    // cycles - see PlayerQuestData.hasMapBeenGranted, which is deliberately not cleared alongside
    // the rest of a player's per-entity progress.
    public static void grantStructureMapIfNeeded(ServerPlayer player, Quest quest) {
        ServerLevel level = player.serverLevel();
        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
        boolean dataChanged = false;

        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestTask task = quest.tasks().get(i);
            ResourceLocation structureId = null;
            boolean providesMap = false;

            if (task instanceof FindStructureTask findTask) {
                structureId = findTask.structureId();
                providesMap = findTask.providesMap();
            } else if (task instanceof EntityKillTask killTask && killTask.inStructure().isPresent()) {
                structureId = killTask.inStructure().get();
                providesMap = killTask.providesMap();
            }

            if (!providesMap || structureId == null) continue;

            String taskKey = quest.id() + "#" + i;
            if (playerData.hasMapBeenGranted(taskKey)) continue;

            Optional<ItemStack> mapStack = StructureMapUtil.createMapToStructure(
                    level, player.blockPosition(), structureId);
            if (mapStack.isEmpty()) continue;

            if (!player.getInventory().add(mapStack.get())) {
                player.drop(mapStack.get(), false);
            }
            playerData.markMapGranted(taskKey);
            dataChanged = true;

            QuestEntityAPI.LOGGER.info("Granted structure map ({}) to player {} for quest {} task {}",
                    structureId, player.getName().getString(), quest.id(), i);
        }

        if (dataChanged) {
            QuestEntityAccess.setPlayerData(player, playerData);
        }
    }

    public static void onEntityKilled(LivingEntity killed, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        if (killed.level().isClientSide) return;

        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof EntityKillTask killTask) {
                    if (killTask.matches(killed, source, killed.level())) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} killed {} for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()),
                                progress.getTaskProgress(i),
                                killTask.amount());
                    }
                }
            }

            if (updated) {
                QuestEntityAccess.setPlayerData(player, playerData);
                updateEntityComponent(player, entityUuid, progress);
                syncProgressToClient(player, entityUuid, progress);
                checkQuestCompletion(player, entityUuid, quest, progress);
            }
        }
    }

    public static void onPlayerMove(ServerPlayer player, Vec3 from, Vec3 to, double accumulatedDistance) {
        if (accumulatedDistance < 0.01) return;

        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
        boolean anyUpdated = false;

        if (playerData.getAllProgress().isEmpty()) {
            QuestEntityAPI.LOGGER.debug("Player {} has no active quests for movement tracking", player.getName().getString());
            return;
        }

        QuestEntityAPI.LOGGER.debug("Player {} has {} active quest(s), checking for BlocksTraveledTask",
                player.getName().getString(), playerData.getAllProgress().size());

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof BlocksTraveledTask) {
                    int previousProgress = progress.getTaskProgress(i);
                    progress.addTaskProgress(i, (int) Math.floor(accumulatedDistance));
                    updated = true;

                    QuestEntityAPI.LOGGER.debug("Player {} traveled {} blocks for quest task ({}/{})",
                            player.getName().getString(),
                            (int) Math.floor(accumulatedDistance),
                            progress.getTaskProgress(i),
                            ((BlocksTraveledTask) task).distance());
                }
            }

            if (updated) {
                anyUpdated = true;
                updateEntityComponent(player, entityUuid, progress);
                // sync every 10 blocks only, to avoid spamming packets
                int currentProgress = progress.getTaskProgress(0);
                if (currentProgress % 10 == 0 || currentProgress % 10 > (currentProgress - (int) Math.floor(accumulatedDistance)) % 10) {
                    syncProgressToClient(player, entityUuid, progress);
                }
                checkQuestCompletion(player, entityUuid, quest, progress);
            }
        }

        if (anyUpdated) {
            QuestEntityAccess.setPlayerData(player, playerData);
        }
    }

    // overload that derives accumulatedDistance from from/to directly
    public static void onPlayerMove(ServerPlayer player, Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        onPlayerMove(player, from, to, distance);
    }

    public static void onItemUsed(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;

        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof ItemUsedTask useTask) {
                    if (useTask.matches(stack)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} used {} for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.ITEM.getKey(stack.getItem()),
                                progress.getTaskProgress(i),
                                useTask.amount());
                    }
                }
            }

            if (updated) {
                QuestEntityAccess.setPlayerData(player, playerData);
                updateEntityComponent(player, entityUuid, progress);
                syncProgressToClient(player, entityUuid, progress);
                checkQuestCompletion(player, entityUuid, quest, progress);
            }
        }
    }

    // Spell Engine integration
    public static void onSpellCast(ServerPlayer player, ResourceLocation spellId) {
        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof SpellCastTask castTask) {
                    if (castTask.matches(player.serverLevel(), spellId)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} cast spell {} for quest task ({}/{})",
                                player.getName().getString(), spellId,
                                progress.getTaskProgress(i), castTask.amount());
                    }
                }
            }

            if (updated) {
                QuestEntityAccess.setPlayerData(player, playerData);
                updateEntityComponent(player, entityUuid, progress);
                syncProgressToClient(player, entityUuid, progress);
                checkQuestCompletion(player, entityUuid, quest, progress);
            }
        }
    }

    public static void onPotionBrewed(ServerPlayer player, ItemStack potionStack) {
        if (potionStack.isEmpty()) return;

        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof BrewPotionTask brewTask) {
                    if (brewTask.matches(potionStack)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} brewed a potion for quest task ({}/{})",
                                player.getName().getString(),
                                progress.getTaskProgress(i),
                                brewTask.amount());
                    }
                }
            }

            if (updated) {
                QuestEntityAccess.setPlayerData(player, playerData);
                updateEntityComponent(player, entityUuid, progress);
                syncProgressToClient(player, entityUuid, progress);
                checkQuestCompletion(player, entityUuid, quest, progress);
            }
        }
    }

    public static void onBlockMined(ServerPlayer player, BlockState minedState) {
        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof MineBlockTask mineTask) {
                    if (mineTask.matches(minedState)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} mined {} for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.BLOCK.getKey(minedState.getBlock()),
                                progress.getTaskProgress(i), mineTask.amount());
                    }
                }
            }

            if (updated) {
                QuestEntityAccess.setPlayerData(player, playerData);
                updateEntityComponent(player, entityUuid, progress);
                syncProgressToClient(player, entityUuid, progress);
                checkQuestCompletion(player, entityUuid, quest, progress);
            }
        }
    }

    public static void onStructureEntered(ServerPlayer player, ResourceLocation structureId) {
        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof FindStructureTask findTask) {
                    if (findTask.structureId().equals(structureId)) {
                        progress.setTaskProgress(i, 1);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} found structure {} for quest task",
                                player.getName().getString(), structureId);
                    }
                }
            }

            if (updated) {
                QuestEntityAccess.setPlayerData(player, playerData);
                updateEntityComponent(player, entityUuid, progress);
                syncProgressToClient(player, entityUuid, progress);
                checkQuestCompletion(player, entityUuid, quest, progress);
            }
        }
    }

    public static void onQuestEntityRemoved(Entity entity) {
        if (entity.level().isClientSide) return;

        EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(entity);
        if (component == null) return;

        ServerLevel level = (ServerLevel) entity.level();
        UUID entityUuid = entity.getUUID();

        for (UUID playerId : component.activeQuests().keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
                playerData.clearEntityProgress(entityUuid);
                QuestEntityAccess.setPlayerData(player, playerData);

                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("message.qe_api.quest_cancelled")
                );

                QuestEntityAPI.LOGGER.info("Quest cancelled for player {} (entity {} removed)",
                        player.getName().getString(), entityUuid);
            }
        }
    }

    private static void checkQuestCompletion(ServerPlayer player, UUID entityUuid,
                                              Quest quest, QuestProgress progress) {
        if (quest.isComplete(progress)) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.qe_api.quest_complete",
                            quest.getDisplayName()
                    )
            );
            QuestEntityAPI.LOGGER.info("Player {} completed quest {}",
                    player.getName().getString(), quest.id());
        }
    }

    private static void updateEntityComponent(ServerPlayer player, UUID entityUuid, QuestProgress progress) {
        ServerLevel level = player.serverLevel();

        AABB searchBox = player.getBoundingBox().inflate(128.0);
        List<Entity> entities = level.getEntities(player, searchBox, entity ->
                entity.getUUID().equals(entityUuid)
        );

        if (entities.isEmpty()) {
            // fallback: search all loaded entities, not just nearby ones
            Entity entity = null;
            for (Entity e : level.getAllEntities()) {
                if (e.getUUID().equals(entityUuid)) {
                    entity = e;
                    break;
                }
            }
            if (entity == null) {
                QuestEntityAPI.LOGGER.warn("Quest entity {} not found for progress update - entity may be unloaded", entityUuid);
                return;
            }
            entities = List.of(entity);
        }

        Entity questEntity = entities.get(0);
        EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(questEntity);
        if (component == null) {
            QuestEntityAPI.LOGGER.warn("Entity {} has no quest component attached", entityUuid);
            return;
        }

        if (!component.hasActiveQuest(player.getUUID())) {
            QuestEntityAPI.LOGGER.warn("Entity {} has no active quest for player {}", entityUuid, player.getName().getString());
            return;
        }

        EntityQuestComponent updated = component.withUpdatedProgress(player.getUUID(), progress);
        QuestEntityAccess.setEntityQuestComponent(questEntity, updated);

        QuestEntityAPI.LOGGER.info("Updated entity {} quest component - task progress: {}",
                entityUuid, progress.getAllTaskProgress());
    }

    private static void syncProgressToClient(ServerPlayer player, UUID entityUuid, QuestProgress progress) {
        if (progressSyncHandler != null) {
            progressSyncHandler.accept(player, entityUuid);
        }
    }
}
