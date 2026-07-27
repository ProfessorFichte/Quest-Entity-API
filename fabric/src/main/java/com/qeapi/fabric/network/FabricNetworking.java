package com.qeapi.fabric.network;

import com.qeapi.QuestEntityAPI;
import com.qeapi.client.ClientQuestCache;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.data.QuestManager;
import com.qeapi.fabric.QuestEntityAPIFabric;
import com.qeapi.network.ClientPacketSender;
import com.qeapi.network.packet.*;
import net.minecraft.world.entity.npc.AbstractVillager;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.QuestTask;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FabricNetworking {

    private FabricNetworking() {}

    public static void registerServer() {
        QuestEntityAPI.LOGGER.info("Registering server-side Quest Entity API packets");

        // S2C
        PayloadTypeRegistry.playS2C().register(OpenQuestMenuPacket.TYPE, OpenQuestMenuPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(QuestProgressPacket.TYPE, QuestProgressPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncEntityQuestsPacket.TYPE, SyncEntityQuestsPacket.STREAM_CODEC);

        // C2S
        PayloadTypeRegistry.playC2S().register(AcceptQuestPacket.TYPE, AcceptQuestPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DismissQuestPacket.TYPE, DismissQuestPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimRewardsPacket.TYPE, ClaimRewardsPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestQuestMenuPacket.TYPE, RequestQuestMenuPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMerchantMenuPacket.TYPE, RequestMerchantMenuPacket.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AcceptQuestPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleAcceptQuest(context.player(), packet);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DismissQuestPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleDismissQuest(context.player(), packet);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ClaimRewardsPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleClaimRewards(context.player(), packet);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestQuestMenuPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleRequestQuestMenu(context.player(), packet);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestMerchantMenuPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleRequestMerchantMenu(context.player(), packet);
            });
        });
    }

    public static void registerClient() {
        QuestEntityAPI.LOGGER.info("Registering client-side Quest Entity API packets");

        ClientPlayNetworking.registerGlobalReceiver(OpenQuestMenuPacket.TYPE, (packet, context) -> {
            context.client().execute(() -> {
                handleOpenQuestMenu(packet);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(QuestProgressPacket.TYPE, (packet, context) -> {
            context.client().execute(() -> {
                handleQuestProgress(packet);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncEntityQuestsPacket.TYPE, (packet, context) -> {
            context.client().execute(() -> {
                handleSyncEntityQuests(packet);
            });
        });

        // Lets the GUI send packets without depending on ClientPlayNetworking directly
        ClientPacketSender.setInstance(new ClientPacketSender.PacketSender() {
            @Override
            public void sendAcceptQuest(int entityId, ResourceLocation questId) {
                ClientPlayNetworking.send(new AcceptQuestPacket(entityId, questId));
            }

            @Override
            public void sendDismissQuest(int entityId) {
                ClientPlayNetworking.send(new DismissQuestPacket(entityId));
            }

            @Override
            public void sendClaimRewards(int entityId, java.util.List<java.util.List<Integer>> poolChoices,
                                          java.util.List<Integer> rewardTargetSlots, java.util.List<java.util.List<Integer>> bringItemSlots) {
                ClientPlayNetworking.send(new ClaimRewardsPacket(entityId, poolChoices, rewardTargetSlots, bringItemSlots));
            }

            @Override
            public void sendRequestQuestMenu(int entityId) {
                ClientPlayNetworking.send(new RequestQuestMenuPacket(entityId));
            }

            @Override
            public void sendRequestMerchantMenu(int entityId) {
                ClientPlayNetworking.send(new RequestMerchantMenuPacket(entityId));
            }
        });
    }

    // ==================== Server Handlers ====================

    private static void handleAcceptQuest(ServerPlayer player, AcceptQuestPacket packet) {
        QuestEntityAPI.LOGGER.debug("Player {} accepting quest {} from entity {}",
                player.getName().getString(), packet.questId(), packet.entityId());

        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) {
            QuestEntityAPI.LOGGER.warn("Entity {} not found for quest accept", packet.entityId());
            return;
        }

        EntityQuestComponent component = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (component == null) {
            QuestEntityAPI.LOGGER.warn("Entity {} has no quest component attached", packet.entityId());
            return;
        }

        UUID playerId = player.getUUID();
        UUID entityId = entity.getUUID();

        if (component.isOnCooldown(playerId)) {
            long remainingMs = component.getRemainingCooldownMs(playerId);
            int remainingMinutes = (int) Math.ceil(remainingMs / 60000.0);
            player.sendSystemMessage(Component.translatable("message.qe_api.cooldown_active", remainingMinutes)
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (component.hasActiveQuest(playerId)) {
            player.sendSystemMessage(Component.translatable("message.qe_api.already_has_quest")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // Search all pools - entity may reference multiple via tags
        List<QuestPool> allPools = component.getAllQuestPools();
        if (allPools.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("No quest pools found for {}", component.questPoolId());
            return;
        }

        Quest quest = null;
        for (QuestPool pool : allPools) {
            quest = findQuestInPool(pool, packet.questId());
            if (quest != null) break;
        }
        if (quest == null) {
            QuestEntityAPI.LOGGER.warn("Quest {} not found in pools for {}", packet.questId(), component.questPoolId());
            return;
        }

        if (!quest.meetsRequirements(player)) {
            player.sendSystemMessage(Component.translatable("message.qe_api.requirements_not_met")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (component.hasCompletedQuest(playerId, packet.questId())) {
            long completedAt = component.getCompletionDayTime(playerId, packet.questId());
            long currentDayTime = player.serverLevel().getDayTime();
            if (!quest.isDueForRepeat(completedAt, currentDayTime)) {
                player.sendSystemMessage(Component.translatable("message.qe_api.already_completed")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }
        }

        EntityQuestComponent updatedComponent = component.withActiveQuest(
                playerId,
                EntityQuestComponent.ActiveQuestData.create(packet.questId())
        );
        entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, updatedComponent);

        PlayerQuestData playerData = player.getAttachedOrCreate(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT,
                PlayerQuestData::new);
        playerData.startQuest(entity.getUUID(), packet.questId());
        player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);

        // Grants a one-time structure map if this quest has a provides_map task; no-op otherwise
        com.qeapi.event.QuestEventHandler.grantStructureMapIfNeeded(player, quest);

        player.sendSystemMessage(Component.translatable("message.qe_api.quest_accepted")
                .withStyle(ChatFormatting.GREEN));

        // First pool only, for compatibility
        QuestPool primaryPool = allPools.get(0);
        EntityQuestComponent finalComponent = checkAndUpdateBringItemProgress(player, entity, updatedComponent, primaryPool);

        sendOpenQuestMenu(player, packet.entityId(), finalComponent, getAvailableQuestsForPlayer(allPools, finalComponent, playerId, entityId));

        QuestEntityAPI.LOGGER.info("Player {} accepted quest {} from entity {}",
                player.getName().getString(), packet.questId(), packet.entityId());
    }

    private static void handleDismissQuest(ServerPlayer player, DismissQuestPacket packet) {
        QuestEntityAPI.LOGGER.debug("Player {} dismissing quest from entity {}",
                player.getName().getString(), packet.entityId());

        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) {
            QuestEntityAPI.LOGGER.warn("Entity {} not found for quest dismiss", packet.entityId());
            return;
        }

        EntityQuestComponent component = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (component == null) {
            return;
        }

        UUID playerId = player.getUUID();
        UUID entityId = entity.getUUID();

        if (!component.hasActiveQuest(playerId)) {
            return;
        }

        EntityQuestComponent updatedComponent = component.withoutActiveQuest(playerId);
        entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, updatedComponent);

        PlayerQuestData playerData = player.getAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT);
        if (playerData != null) {
            playerData.clearEntityProgress(entity.getUUID());
            player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);
        }

        player.sendSystemMessage(Component.translatable("message.qe_api.quest_dismissed")
                .withStyle(ChatFormatting.YELLOW));

        List<QuestPool> allPools = component.getAllQuestPools();
        if (!allPools.isEmpty()) {
            sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, playerId, entityId));
        }

        QuestEntityAPI.LOGGER.info("Player {} dismissed quest from entity {}",
                player.getName().getString(), packet.entityId());
    }

    private static void handleClaimRewards(ServerPlayer player, ClaimRewardsPacket packet) {
        QuestEntityAPI.LOGGER.debug("Player {} claiming rewards from entity {}",
                player.getName().getString(), packet.entityId());

        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) {
            QuestEntityAPI.LOGGER.warn("Entity {} not found for reward claim", packet.entityId());
            return;
        }

        EntityQuestComponent component = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (component == null) {
            return;
        }

        UUID playerId = player.getUUID();
        UUID entityId = entity.getUUID();

        Optional<EntityQuestComponent.ActiveQuestData> activeQuestOpt = component.getActiveQuest(playerId);
        if (activeQuestOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.qe_api.no_active_quest")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        EntityQuestComponent.ActiveQuestData activeQuest = activeQuestOpt.get();

        QuestEntityAPI.LOGGER.info("Claim rewards: Active quest {} with entity progress {}",
                activeQuest.questId(), activeQuest.progress().getAllTaskProgress());

        // Sync progress from player data - handles the entity having been unloaded while progress updated
        PlayerQuestData playerData = player.getAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT);
        if (playerData != null) {
            Optional<QuestProgress> playerProgress = playerData.getProgressForEntity(entity.getUUID());
            if (playerProgress.isPresent()) {
                QuestProgress pp = playerProgress.get();
                QuestEntityAPI.LOGGER.info("Player data has progress: {}", pp.getAllTaskProgress());

                Map<Integer, Integer> entityTaskProgress = activeQuest.progress().getAllTaskProgress();
                Map<Integer, Integer> playerTaskProgress = pp.getAllTaskProgress();

                boolean needsSync = false;
                for (Map.Entry<Integer, Integer> e : playerTaskProgress.entrySet()) {
                    int entityValue = entityTaskProgress.getOrDefault(e.getKey(), 0);
                    if (e.getValue() > entityValue) {
                        needsSync = true;
                        break;
                    }
                }

                if (needsSync) {
                    QuestEntityAPI.LOGGER.info("Syncing player progress to entity component");
                    EntityQuestComponent updatedComponent = component.withUpdatedProgress(playerId, pp);
                    entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, updatedComponent);
                    component = updatedComponent;
                    activeQuest = component.getActiveQuest(playerId).orElse(activeQuest);
                }
            }
        }

        // Search all pools - entity may reference multiple via tags
        List<QuestPool> allPools = component.getAllQuestPools();
        if (allPools.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("No quest pools found for claim");
            return;
        }

        Quest quest = null;
        QuestPool questPool = null;
        for (QuestPool pool : allPools) {
            quest = findQuestInPool(pool, activeQuest.questId());
            if (quest != null) {
                questPool = pool;
                break;
            }
        }
        if (quest == null) {
            QuestEntityAPI.LOGGER.warn("Quest {} not found in pools", activeQuest.questId());
            return;
        }

        // Copy progress - avoid mutating the original during serialization
        QuestProgress progress = activeQuest.progress().copy();
        QuestEntityAPI.LOGGER.info("Progress copy before BringItem check: {}", progress.getAllTaskProgress());

        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestTask task = quest.tasks().get(i);
            if (task instanceof BringItemTask bringTask) {
                int count = bringTask.countMatchingItems(player.getInventory().items);
                progress.setTaskProgress(i, count);
            }
        }

        QuestEntityAPI.LOGGER.info("Progress after BringItem check: {}", progress.getAllTaskProgress());

        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestTask task = quest.tasks().get(i);
            boolean taskComplete = task.isComplete(progress, i);
            QuestEntityAPI.LOGGER.info("Task {} ({}): complete={}, progress={}, target={}",
                    i, task.getTypeId(), taskComplete, progress.getTaskProgress(i), task.getTargetAmount());
        }

        if (!quest.isComplete(progress)) {
            QuestEntityAPI.LOGGER.warn("Quest not complete - cannot claim rewards");
            player.sendSystemMessage(Component.translatable("message.qe_api.quest_not_complete")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        com.qeapi.network.ClaimRewardsHelper.consumeBringItemTasks(player, quest, packet.bringItemSlots());

        if (!quest.isValidPoolChoice(packet.poolChoices())) {
            QuestEntityAPI.LOGGER.warn("Invalid reward pool choice from player {} for quest {}",
                    player.getName().getString(), quest.id());
            return;
        }
        quest.grantRewards(player, entity, packet.poolChoices(), packet.rewardTargetSlots());
        com.qeapi.event.QuestEventHandler.grantVillagerTradeXp(entity, quest.tier());

        // Re-fetch rather than reusing the pre-grant `component` - an EntityAwareReward (e.g.
        // SetQuestGroupReward) may have already written its own update onto the entity during
        // grantRewards above, and building withCompletedQuest off the stale copy would clobber it.
        EntityQuestComponent postGrantComponent = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (postGrantComponent == null) {
            postGrantComponent = component;
        }
        EntityQuestComponent updatedComponent = postGrantComponent.withCompletedQuest(playerId, activeQuest.questId(),
                player.serverLevel().getDayTime());
        entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, updatedComponent);

        // checkNearbyQuestEntities only syncs an entity once per continuous presence in range,
        // so without forcing this the marker (e.g. available -> complete) won't refresh after claiming.
        QuestEntityAPIFabric.forceResyncForNearbyPlayers(entity);

        com.qeapi.advancement.QuestCompleteTrigger.INSTANCE.trigger(player, activeQuest.questId());

        // Re-fetch playerData - may have changed during the sync above
        playerData = player.getAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT);
        if (playerData != null) {
            playerData.clearEntityProgress(entity.getUUID());
            player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);
        }

        player.sendSystemMessage(Component.translatable("message.qe_api.rewards_claimed")
                .withStyle(ChatFormatting.GREEN));

        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, playerId,entityId));

        QuestEntityAPI.LOGGER.info("Player {} claimed rewards for quest {} from entity {}",
                player.getName().getString(), activeQuest.questId(), packet.entityId());
    }

    private static void handleRequestQuestMenu(ServerPlayer player, RequestQuestMenuPacket packet) {
        QuestEntityAPI.LOGGER.debug("Player {} requesting quest menu for entity {}",
                player.getName().getString(), packet.entityId());

        // Close any open container first (e.g. merchant screen) so the player can reinteract with the villager
        player.closeContainer();

        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) {
            QuestEntityAPI.LOGGER.warn("Entity {} not found for quest menu request", packet.entityId());
            return;
        }

        EntityQuestComponent component = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (component == null) {
            QuestEntityAPI.LOGGER.debug("Entity {} has no quest component", packet.entityId());
            player.sendSystemMessage(Component.translatable("message.qe_api.no_quests_available")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        List<QuestPool> allPools = component.getAllQuestPools();
        if (allPools.isEmpty()) {
            QuestEntityAPI.LOGGER.debug("No quest pools found for entity {}", packet.entityId());
            player.sendSystemMessage(Component.translatable("message.qe_api.no_quests_available")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        List<Quest> availableQuests = getAvailableQuestsForPlayer(allPools, component, player.getUUID(),entity.getUUID());

        sendOpenQuestMenu(player, packet.entityId(), component, availableQuests);

        QuestEntityAPI.LOGGER.info("Sent quest menu to player {} for entity {}",
                player.getName().getString(), packet.entityId());
    }

    private static void handleRequestMerchantMenu(ServerPlayer player, RequestMerchantMenuPacket packet) {
        QuestEntityAPI.LOGGER.debug("Player {} requesting merchant menu for entity {}",
                player.getName().getString(), packet.entityId());

        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) {
            QuestEntityAPI.LOGGER.warn("Entity {} not found for merchant menu request", packet.entityId());
            return;
        }

        // AbstractVillager covers both villagers and wandering traders
        if (!(entity instanceof AbstractVillager villager)) {
            QuestEntityAPI.LOGGER.warn("Entity {} is not a merchant", packet.entityId());
            return;
        }

        villager.setTradingPlayer(player);

        java.util.OptionalInt containerId = player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, playerInventory, playerEntity) ->
                    new net.minecraft.world.inventory.MerchantMenu(id, playerInventory, villager),
                villager.getDisplayName()
        ));

        if (containerId.isPresent()) {
            int villagerLevel = 1;
            int villagerXp = 0;
            boolean showProgressBar = false;
            boolean canRestock = false;

            if (villager instanceof net.minecraft.world.entity.npc.Villager v) {
                villagerLevel = v.getVillagerData().getLevel();
                villagerXp = v.getVillagerXp();
                showProgressBar = true;
                canRestock = true;
            }

            player.sendMerchantOffers(
                    containerId.getAsInt(),
                    villager.getOffers(),
                    villagerLevel,
                    villagerXp,
                    showProgressBar,
                    canRestock
            );
        }

        QuestEntityAPI.LOGGER.info("Opened merchant menu for player {} with entity {}",
                player.getName().getString(), packet.entityId());
    }

    // ==================== Client Handlers ====================

    private static void handleOpenQuestMenu(OpenQuestMenuPacket packet) {
        QuestEntityAPI.LOGGER.debug("Opening quest menu for entity {}", packet.entityId());

        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        net.minecraft.world.entity.Entity entity = minecraft.level.getEntity(packet.entityId());
        if (entity != null) {
            com.qeapi.client.ClientQuestCache.markEntityHasQuests(
                    entity.getUUID(),
                    packet.entityId(),
                    packet.questComponent()
            );
        }

        minecraft.setScreen(new com.qeapi.client.gui.QuestScreen(
                packet.entityId(),
                packet.availableQuests(),
                packet.questComponent(),
                minecraft.player.getUUID()
        ));
    }

    private static void handleQuestProgress(QuestProgressPacket packet) {
        QuestEntityAPI.LOGGER.debug("Received quest progress update for entity {}", packet.entityId());
        // Update is handled by refreshing the GUI when needed
    }

    private static void handleSyncEntityQuests(SyncEntityQuestsPacket packet) {
        QuestEntityAPI.LOGGER.debug("Received entity quest sync for entity {} (UUID: {}), active={}, complete={}",
                packet.entityId(), packet.entityUuid(), packet.hasActiveQuest(), packet.isQuestComplete());

        ClientQuestCache.markEntityHasQuestsSimple(
                packet.entityUuid(),
                packet.entityId(),
                packet.hasActiveQuest(),
                packet.isQuestComplete(),
                packet.allQuestsCompleted()
        );
    }

    // ==================== Send Helpers ====================

    // Syncs progress from player data and updates BringItemTask progress before sending the menu.
    public static void sendOpenQuestMenu(ServerPlayer player, int entityId,
                                          EntityQuestComponent component, List<Quest> quests) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (entity != null && component.hasActiveQuest(player.getUUID())) {
            // Sync progress from player data - handles the player completing tasks while far from the quest entity
            PlayerQuestData playerData = player.getAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT);
            if (playerData != null) {
                Optional<QuestProgress> playerProgress = playerData.getProgressForEntity(entity.getUUID());
                if (playerProgress.isPresent()) {
                    QuestProgress pp = playerProgress.get();
                    Optional<EntityQuestComponent.ActiveQuestData> activeQuestOpt = component.getActiveQuest(player.getUUID());

                    if (activeQuestOpt.isPresent()) {
                        Map<Integer, Integer> entityTaskProgress = activeQuestOpt.get().progress().getAllTaskProgress();
                        Map<Integer, Integer> playerTaskProgress = pp.getAllTaskProgress();

                        boolean needsSync = false;
                        for (Map.Entry<Integer, Integer> e : playerTaskProgress.entrySet()) {
                            int entityValue = entityTaskProgress.getOrDefault(e.getKey(), 0);
                            if (e.getValue() > entityValue) {
                                needsSync = true;
                                break;
                            }
                        }

                        if (needsSync) {
                            QuestEntityAPI.LOGGER.info("Syncing player progress to entity component when opening menu");
                            component = component.withUpdatedProgress(player.getUUID(), pp);
                            entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, component);
                        }
                    }
                }
            }

            List<QuestPool> allPools = component.getAllQuestPools();
            if (!allPools.isEmpty()) {
                component = checkAndUpdateBringItemProgress(player, entity, component, allPools.get(0));
            }
        }

        OpenQuestMenuPacket packet = new OpenQuestMenuPacket(entityId, component, quests);
        ServerPlayNetworking.send(player, packet);
    }

    public static void sendQuestProgress(ServerPlayer player, int entityId,
                                          net.minecraft.resources.ResourceLocation questId,
                                          java.util.Map<Integer, Integer> taskProgress) {
        QuestProgressPacket packet = new QuestProgressPacket(entityId, questId, taskProgress);
        ServerPlayNetworking.send(player, packet);
    }

    public static void sendSyncEntityQuests(ServerPlayer player, int entityId, UUID entityUuid,
                                             ResourceLocation questPoolId, boolean hasActiveQuest, boolean isQuestComplete,
                                             boolean allQuestsCompleted) {
        SyncEntityQuestsPacket packet = new SyncEntityQuestsPacket(entityId, entityUuid, questPoolId, hasActiveQuest, isQuestComplete, allQuestsCompleted);
        ServerPlayNetworking.send(player, packet);
    }

    // ==================== Helper Methods ====================

    private static Quest findQuestInPool(QuestPool pool, ResourceLocation questId) {
        for (List<Quest> tierQuests : pool.getQuestsByTier().values()) {
            for (Quest quest : tierQuests) {
                if (quest.id().equals(questId)) {
                    return quest;
                }
            }
        }
        return null;
    }

    // Picks one quest per tier, weighted-random and seeded by entity UUID so each entity's
    // selection is stable. Respects followQuestOrder: a quest is locked until at least one
    // quest from every lower tier has been completed. Also respects questGroup: a quest with one
    // set is only a candidate for a player who's chosen that exact group for this pool (see
    // SetQuestGroupReward) - a quest with none is a candidate regardless.
    public static List<Quest> getAvailableQuestsForPlayer(List<QuestPool> pools, EntityQuestComponent component, UUID playerId, UUID entityUuid) {
        java.util.Set<ResourceLocation> completedQuests = component.getCompletedQuests(playerId);

        java.util.Map<Integer, java.util.List<Quest>> questsByTier = new java.util.HashMap<>();

        for (QuestPool pool : pools) {
            for (int tier : pool.getAvailableTiers()) {
                List<Quest> tierQuests = pool.getQuestsForTier(tier);

                for (Quest quest : tierQuests) {
                    if (quest.followQuestOrder() && quest.tier() > 1) {
                        boolean canAccept = true;
                        for (int lowerTier = 1; lowerTier < quest.tier(); lowerTier++) {
                            List<Quest> lowerTierQuests = pool.getQuestsForTier(lowerTier);
                            if (!lowerTierQuests.isEmpty()) {
                                boolean hasCompletedInTier = lowerTierQuests.stream()
                                        .anyMatch(q -> completedQuests.contains(q.id()));
                                if (!hasCompletedInTier) {
                                    canAccept = false;
                                    break;
                                }
                            }
                        }
                        if (!canAccept) {
                            continue;
                        }
                    }

                    if (quest.questGroup().isPresent()) {
                        Optional<String> chosenGroup = component.getChosenQuestGroup(playerId);
                        if (chosenGroup.isEmpty() || !chosenGroup.get().equals(quest.questGroup().get())) {
                            continue;
                        }
                    }

                    questsByTier.computeIfAbsent(tier, k -> new java.util.ArrayList<>()).add(quest);
                }
            }
        }

        java.util.List<Quest> available = new java.util.ArrayList<>();
        long seed = entityUuid.hashCode();
        java.util.Random random = new java.util.Random(seed);

        java.util.List<Integer> tiers = new java.util.ArrayList<>(questsByTier.keySet());
        java.util.Collections.sort(tiers);

        for (int tier : tiers) {
            java.util.List<Quest> tierQuests = questsByTier.get(tier);
            if (tierQuests.isEmpty()) continue;

            // Always show the player's active quest for this tier, if any
            Optional<EntityQuestComponent.ActiveQuestData> activeQuest = component.getActiveQuest(playerId);
            if (activeQuest.isPresent()) {
                Quest activeInTier = null;
                for (Quest q : tierQuests) {
                    if (q.id().equals(activeQuest.get().questId())) {
                        activeInTier = q;
                        break;
                    }
                }
                if (activeInTier != null) {
                    available.add(activeInTier);
                    continue;
                }
            }

            // Otherwise show a completed quest for this tier, if any
            Quest completedInTier = null;
            for (Quest q : tierQuests) {
                if (completedQuests.contains(q.id())) {
                    completedInTier = q;
                    break;
                }
            }
            if (completedInTier != null) {
                available.add(completedInTier);
                continue;
            }

            // Otherwise pick randomly by weight
            int totalWeight = tierQuests.stream().mapToInt(Quest::weight).sum();
            if (totalWeight <= 0) {
                // Fallback: all weights <= 0
                available.add(tierQuests.get(random.nextInt(tierQuests.size())));
                continue;
            }

            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            Quest selectedQuest = tierQuests.get(0);

            for (Quest quest : tierQuests) {
                cumulative += quest.weight();
                if (roll < cumulative) {
                    selectedQuest = quest;
                    break;
                }
            }

            available.add(selectedQuest);
        }

        return available;
    }

    // Diffs the player's inventory against originalProgress (not mutated) and returns an
    // updated component, or null if nothing changed.
    private static EntityQuestComponent updateBringItemProgress(ServerPlayer player, Quest quest,
            EntityQuestComponent component, QuestProgress originalProgress) {
        boolean changed = false;

        // Copy - avoid mutating the original during serialization
        QuestProgress progress = originalProgress.copy();

        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestTask task = quest.tasks().get(i);
            if (task instanceof BringItemTask bringTask) {
                int count = bringTask.countMatchingItems(player.getInventory().items);
                int currentProgress = progress.getTaskProgress(i);

                if (count != currentProgress) {
                    progress.setTaskProgress(i, count);
                    changed = true;
                }
            }
        }

        if (changed) {
            return component.withUpdatedProgress(player.getUUID(), progress);
        }
        return null;
    }

    // Updates BringItemTask progress for the player's active quest, across entity and player
    // data. Searches all pools associated with the component (supports tags).
    public static EntityQuestComponent checkAndUpdateBringItemProgress(ServerPlayer player, Entity entity,
            EntityQuestComponent component, QuestPool pool) {
        UUID playerId = player.getUUID();

        Optional<EntityQuestComponent.ActiveQuestData> activeQuestOpt = component.getActiveQuest(playerId);
        if (activeQuestOpt.isEmpty()) {
            return component;
        }

        EntityQuestComponent.ActiveQuestData activeQuest = activeQuestOpt.get();

        Quest quest = findQuestInPool(pool, activeQuest.questId());
        if (quest == null) {
            // Fall back to searching all pools - entity may have multiple via tags
            for (QuestPool p : component.getAllQuestPools()) {
                quest = findQuestInPool(p, activeQuest.questId());
                if (quest != null) break;
            }
        }
        if (quest == null) {
            return component;
        }

        boolean hasBringItemTask = quest.tasks().stream().anyMatch(t -> t instanceof BringItemTask);
        if (!hasBringItemTask) {
            return component;
        }

        QuestProgress progress = activeQuest.progress();
        EntityQuestComponent updated = updateBringItemProgress(player, quest, component, progress);

        if (updated != null) {
            entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, updated);

            PlayerQuestData playerData = player.getAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT);
            if (playerData != null) {
                playerData.setProgressForEntity(entity.getUUID(), progress);
                player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);
            }

            return updated;
        }

        return component;
    }
}
