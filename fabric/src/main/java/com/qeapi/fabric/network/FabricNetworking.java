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
import com.qeapi.quest.task.FindStructureTask;
import com.qeapi.quest.task.QuestLineChoiceTask;
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
        PayloadTypeRegistry.playS2C().register(SyncDeliveryTargetPacket.TYPE, SyncDeliveryTargetPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ActiveQuestsPacket.TYPE, ActiveQuestsPacket.STREAM_CODEC);

        // C2S
        PayloadTypeRegistry.playC2S().register(AcceptQuestPacket.TYPE, AcceptQuestPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DismissQuestPacket.TYPE, DismissQuestPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimRewardsPacket.TYPE, ClaimRewardsPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestQuestMenuPacket.TYPE, RequestQuestMenuPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMerchantMenuPacket.TYPE, RequestMerchantMenuPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestActiveQuestsPacket.TYPE, RequestActiveQuestsPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ChooseQuestLinePacket.TYPE, ChooseQuestLinePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimQuestLineRootPacket.TYPE, ClaimQuestLineRootPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CancelQuestLinePacket.TYPE, CancelQuestLinePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DismissQuestLineRootPacket.TYPE, DismissQuestLineRootPacket.STREAM_CODEC);

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

        ServerPlayNetworking.registerGlobalReceiver(RequestActiveQuestsPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleRequestActiveQuests(context.player());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ChooseQuestLinePacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleChooseQuestLine(context.player(), packet);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ClaimQuestLineRootPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleClaimQuestLineRoot(context.player(), packet);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CancelQuestLinePacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleCancelQuestLine(context.player(), packet);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DismissQuestLineRootPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                handleDismissQuestLineRoot(context.player(), packet);
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

        ClientPlayNetworking.registerGlobalReceiver(SyncDeliveryTargetPacket.TYPE, (packet, context) -> {
            context.client().execute(() -> {
                handleSyncDeliveryTarget(packet);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ActiveQuestsPacket.TYPE, (packet, context) -> {
            context.client().execute(() -> {
                handleActiveQuests(packet);
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

            @Override
            public void sendRequestActiveQuests() {
                ClientPlayNetworking.send(new RequestActiveQuestsPacket());
            }

            @Override
            public void sendChooseQuestLine(int entityId, ResourceLocation rootQuestId, String lineId) {
                ClientPlayNetworking.send(new ChooseQuestLinePacket(entityId, rootQuestId, lineId));
            }

            @Override
            public void sendClaimQuestLineRoot(int entityId, ResourceLocation rootQuestId) {
                ClientPlayNetworking.send(new ClaimQuestLineRootPacket(entityId, rootQuestId));
            }

            @Override
            public void sendCancelQuestLine(int entityId, ResourceLocation rootQuestId, String lineId) {
                ClientPlayNetworking.send(new CancelQuestLinePacket(entityId, rootQuestId, lineId));
            }

            @Override
            public void sendDismissQuestLineRoot(int entityId, ResourceLocation rootQuestId) {
                ClientPlayNetworking.send(new DismissQuestLineRootPacket(entityId, rootQuestId));
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

        // A quest_line_choice root never occupies the giver's entityProgress/active-quest slot (see
        // QuestLineChoiceTask's javadoc) - accepting it only flips its own acceptedRoots flag, which
        // is what gates the line picker becoming interactive in QuestScreen.
        if (com.qeapi.api.QuestEntityAccess.isLineRootQuest(quest)) {
            PlayerQuestData rootPlayerData = player.getAttachedOrCreate(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT,
                    PlayerQuestData::new);
            PlayerQuestData.LineSelectionState lineState = rootPlayerData.getLineSelection(entityId);
            if (!lineState.acceptedRoots().contains(quest.id())) {
                rootPlayerData.setLineSelection(entityId, lineState.withAcceptedRoot(quest.id()));
                player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, rootPlayerData);
            }

            sendOpenQuestMenu(player, packet.entityId(), component, getAvailableQuestsForPlayer(allPools, component, player, entity));
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

        // one-way: once accepted from, this giver is protected from despawning for good, even
        // after every quest with it wraps up - no-ops for non-Mob QuestEntity implementations
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setPersistenceRequired();
        }

        PlayerQuestData playerData = player.getAttachedOrCreate(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT,
                PlayerQuestData::new);
        playerData.startQuest(entity.getUUID(), packet.questId());
        playerData.recordEntityLocation(entity);
        player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);

        // Grants a one-time structure map if this quest has a provides_map task; no-op otherwise
        com.qeapi.event.QuestEventHandler.grantStructureMapIfNeeded(player, quest);
        // resolves deliver_item's target selector to one concrete entity; no-op otherwise
        com.qeapi.event.QuestEventHandler.resolveDeliveryTargetIfNeeded(player, entity, quest);
        com.qeapi.event.QuestEventHandler.playAcceptSound(player, quest);

        // First pool only, for compatibility
        QuestPool primaryPool = allPools.get(0);
        EntityQuestComponent finalComponent = checkAndUpdateBringItemProgress(player, entity, updatedComponent, primaryPool);

        sendOpenQuestMenu(player, packet.entityId(), finalComponent, getAvailableQuestsForPlayer(allPools, finalComponent, player, entity));

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

        Optional<ResourceLocation> dismissedQuestId = component.getActiveQuest(playerId).map(EntityQuestComponent.ActiveQuestData::questId);

        EntityQuestComponent updatedComponent = component.withoutActiveQuest(playerId);
        entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, updatedComponent);

        List<QuestPool> allPools = component.getAllQuestPools();

        PlayerQuestData playerData = player.getAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT);
        if (playerData != null) {
            playerData.clearEntityProgress(entity.getUUID());

            Quest dismissedQuest = null;
            for (QuestPool pool : allPools) {
                dismissedQuest = findQuestInPool(pool, dismissedQuestId.orElse(null));
                if (dismissedQuest != null) break;
            }
            if (dismissedQuest != null && dismissedQuest.questLine().isPresent()) {
                UUID entityUuid = entity.getUUID();
                playerData.setLineSelection(entityUuid, playerData.getLineSelection(entityUuid).withoutActiveLine());
            }

            player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);
        }


        if (!allPools.isEmpty()) {
            sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));
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
        com.qeapi.event.QuestEventHandler.playClaimEffects(player, quest);

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
        com.qeapi.api.QuestEntityAccess.resolveQuestLineIfNeeded(player, entity, updatedComponent, quest);

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


        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));

        QuestEntityAPI.LOGGER.info("Player {} claimed rewards for quest {} from entity {}",
                player.getName().getString(), activeQuest.questId(), packet.entityId());
    }

    private static void handleChooseQuestLine(ServerPlayer player, ChooseQuestLinePacket packet) {
        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) return;

        EntityQuestComponent component = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (component == null) return;

        List<QuestPool> allPools = component.getAllQuestPools();
        Quest root = null;
        for (QuestPool pool : allPools) {
            root = findQuestInPool(pool, packet.rootQuestId());
            if (root != null) break;
        }
        if (root == null || root.tasks().size() != 1
                || !(root.tasks().get(0) instanceof QuestLineChoiceTask lineChoiceTask)) {
            QuestEntityAPI.LOGGER.warn("Quest {} is not a quest_line_choice root", packet.rootQuestId());
            return;
        }

        Optional<QuestLineChoiceTask.LineOption> lineOption = lineChoiceTask.findLine(packet.lineId());
        if (lineOption.isEmpty() || !lineOption.get().isAvailable()) {
            QuestEntityAPI.LOGGER.warn("Line {} is not a valid/available choice on {}", packet.lineId(), packet.rootQuestId());
            return;
        }

        UUID entityUuid = entity.getUUID();

        // A quest_line-tagged step quest must be authored with follow_quest_order: false (see the
        // README) - it never needs the root's own tier "completed" to unlock, only its questLine
        // matching the active line, so picking a line here never has to touch completedQuests.
        PlayerQuestData playerData = player.getAttachedOrCreate(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, PlayerQuestData::new);
        playerData.setLineSelection(entityUuid, playerData.getLineSelection(entityUuid).withActiveLine(packet.lineId()));
        player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);

        sendOpenQuestMenu(player, packet.entityId(), component, getAvailableQuestsForPlayer(allPools, component, player, entity));
        QuestEntityAPIFabric.forceResyncForNearbyPlayers(entity);

        QuestEntityAPI.LOGGER.info("Player {} chose line {} for quest_line_choice root {}",
                player.getName().getString(), packet.lineId(), packet.rootQuestId());
    }

    private static void handleClaimQuestLineRoot(ServerPlayer player, ClaimQuestLineRootPacket packet) {
        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) return;

        EntityQuestComponent component = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (component == null) return;

        List<QuestPool> allPools = component.getAllQuestPools();
        Quest root = null;
        for (QuestPool pool : allPools) {
            root = findQuestInPool(pool, packet.rootQuestId());
            if (root != null) break;
        }
        if (root == null || root.tasks().size() != 1
                || !(root.tasks().get(0) instanceof QuestLineChoiceTask lineChoiceTask)) {
            QuestEntityAPI.LOGGER.warn("Quest {} is not a quest_line_choice root", packet.rootQuestId());
            return;
        }

        UUID entityUuid = entity.getUUID();
        PlayerQuestData playerData = player.getAttachedOrCreate(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, PlayerQuestData::new);
        PlayerQuestData.LineSelectionState lineState = playerData.getLineSelection(entityUuid);

        if (lineState.claimedRoots().contains(root.id()) || !lineChoiceTask.isResolved(lineState.resolvedLines())) {
            return;
        }

        root.grantRewards(player, entity, List.of());
        com.qeapi.event.QuestEventHandler.grantVillagerTradeXp(entity, root.tier());
        com.qeapi.event.QuestEventHandler.playClaimEffects(player, root);

        // Marked completed here - only on the real claim, once every line is actually resolved -
        // so follow_quest_order for a sibling root at a higher tier only unlocks once this one is
        // genuinely done, matching every other quest's completion timing.
        EntityQuestComponent updatedComponent = component.withCompletedQuest(player.getUUID(), root.id(), player.serverLevel().getDayTime());
        entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, updatedComponent);

        playerData.setLineSelection(entityUuid, lineState.withClaimedRoot(root.id()));
        player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);


        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));
        QuestEntityAPIFabric.forceResyncForNearbyPlayers(entity);

        QuestEntityAPI.LOGGER.info("Player {} claimed quest_line_choice root reward {}",
                player.getName().getString(), packet.rootQuestId());
    }

    // Cancels the player's currently active line for a quest_line_choice root, sent from clicking
    // that line's own bordered icon (see QuestScreen's confirm-dismiss dialog reuse). Clears
    // activeLine unconditionally, and additionally clears entityProgress/the component's active
    // quest if the giver's active quest happens to be a step of the line being canceled - mirrors
    // handleDismissQuest's questLine-clearing hook, just triggered from the root side instead.
    private static void handleCancelQuestLine(ServerPlayer player, CancelQuestLinePacket packet) {
        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) return;

        EntityQuestComponent component = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (component == null) return;

        List<QuestPool> allPools = component.getAllQuestPools();
        Quest root = null;
        for (QuestPool pool : allPools) {
            root = findQuestInPool(pool, packet.rootQuestId());
            if (root != null) break;
        }
        if (root == null || root.tasks().size() != 1
                || !(root.tasks().get(0) instanceof QuestLineChoiceTask)) {
            QuestEntityAPI.LOGGER.warn("Quest {} is not a quest_line_choice root", packet.rootQuestId());
            return;
        }

        UUID entityUuid = entity.getUUID();

        PlayerQuestData playerData = player.getAttachedOrCreate(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, PlayerQuestData::new);
        PlayerQuestData.LineSelectionState lineState = playerData.getLineSelection(entityUuid);

        if (lineState.activeLine().isEmpty() || !lineState.activeLine().get().equals(packet.lineId())) {
            return;
        }

        playerData.setLineSelection(entityUuid, lineState.withoutActiveLine());

        EntityQuestComponent updatedComponent = clearActiveLineStep(component, allPools, player, entity, playerData, packet.lineId());

        player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);


        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));
        QuestEntityAPIFabric.forceResyncForNearbyPlayers(entity);

        QuestEntityAPI.LOGGER.info("Player {} canceled line {} for quest_line_choice root {}",
                player.getName().getString(), packet.lineId(), packet.rootQuestId());
    }

    // Un-accepts an already-accepted, not-yet-claimed quest_line_choice root, sent from clicking
    // that root's own checkbox a second time (see QuestScreen's confirm-dismiss dialog reuse).
    // Clears activeLine and acceptedRoots unconditionally, and additionally clears
    // entityProgress/the component's active quest if the giver's active quest happens to be a step
    // of whichever line was active - same clearActiveLineStep helper handleCancelQuestLine uses.
    // Never touches resolvedLines/claimedRoots, so already-claimed steps stay claimed.
    private static void handleDismissQuestLineRoot(ServerPlayer player, DismissQuestLineRootPacket packet) {
        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) return;

        EntityQuestComponent component = entity.getAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT);
        if (component == null) return;

        List<QuestPool> allPools = component.getAllQuestPools();
        Quest root = null;
        for (QuestPool pool : allPools) {
            root = findQuestInPool(pool, packet.rootQuestId());
            if (root != null) break;
        }
        if (root == null || root.tasks().size() != 1
                || !(root.tasks().get(0) instanceof QuestLineChoiceTask)) {
            QuestEntityAPI.LOGGER.warn("Quest {} is not a quest_line_choice root", packet.rootQuestId());
            return;
        }

        UUID entityUuid = entity.getUUID();

        PlayerQuestData playerData = player.getAttachedOrCreate(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, PlayerQuestData::new);
        PlayerQuestData.LineSelectionState lineState = playerData.getLineSelection(entityUuid);

        if (!lineState.acceptedRoots().contains(root.id()) || lineState.claimedRoots().contains(root.id())) {
            return;
        }

        EntityQuestComponent updatedComponent = component;
        if (lineState.activeLine().isPresent()) {
            updatedComponent = clearActiveLineStep(component, allPools, player, entity, playerData, lineState.activeLine().get());
        }

        playerData.setLineSelection(entityUuid, lineState.withoutActiveLine().withoutAcceptedRoot(root.id()));
        player.setAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT, playerData);


        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));
        QuestEntityAPIFabric.forceResyncForNearbyPlayers(entity);

        QuestEntityAPI.LOGGER.info("Player {} dismissed quest_line_choice root {}",
                player.getName().getString(), packet.rootQuestId());
    }

    // Clears the giver's active quest + entityProgress if the player's current in-progress step
    // belongs to lineId - shared by handleCancelQuestLine (lineId is the line being explicitly
    // canceled) and handleDismissQuestLineRoot (lineId is whatever line was active on the root
    // being dismissed). Only touches entityProgress/the active-quest pointer, never
    // completedQuests, so already-claimed steps stay claimed.
    private static EntityQuestComponent clearActiveLineStep(EntityQuestComponent component, List<QuestPool> allPools,
                                                              ServerPlayer player, Entity entity,
                                                              PlayerQuestData playerData, String lineId) {
        UUID playerId = player.getUUID();
        if (!component.hasActiveQuest(playerId)) return component;

        Optional<ResourceLocation> activeQuestId = component.getActiveQuest(playerId)
                .map(EntityQuestComponent.ActiveQuestData::questId);
        Quest activeStepQuest = null;
        for (QuestPool pool : allPools) {
            activeStepQuest = findQuestInPool(pool, activeQuestId.orElse(null));
            if (activeStepQuest != null) break;
        }
        if (activeStepQuest == null || activeStepQuest.questLine().isEmpty()
                || !activeStepQuest.questLine().get().equals(lineId)) {
            return component;
        }

        EntityQuestComponent updatedComponent = component.withoutActiveQuest(playerId);
        entity.setAttached(QuestEntityAPIFabric.ENTITY_QUEST_ATTACHMENT, updatedComponent);
        playerData.clearEntityProgress(entity.getUUID());
        return updatedComponent;
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

        List<Quest> availableQuests = getAvailableQuestsForPlayer(allPools, component, player, entity);

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

    // Gathers every quest the player currently has active, across every quest-giver, using only
    // PlayerQuestData (no world/entity scan - see PlayerQuestData.QuestGiverLocation for how giver
    // positions are kept fresh without one).
    private static void handleRequestActiveQuests(ServerPlayer player) {
        PlayerQuestData playerData = player.getAttached(QuestEntityAPIFabric.PLAYER_QUEST_ATTACHMENT);
        List<ActiveQuestEntry> entries = new java.util.ArrayList<>();

        if (playerData != null) {
            boolean showCoords = com.qeapi.config.QuestEntityAPIConfig.get().show_quest_coordinates || player.isCreative();

            for (Map.Entry<UUID, QuestProgress> e : playerData.getAllProgress().entrySet()) {
                UUID entityUuid = e.getKey();
                QuestProgress progress = e.getValue();

                Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
                if (questOpt.isEmpty()) continue;

                Optional<PlayerQuestData.QuestGiverLocation> locOpt = playerData.getEntityLocation(entityUuid);
                ActiveQuestEntry.GiverLocation location = locOpt
                        .map(loc -> new ActiveQuestEntry.GiverLocation(loc.entityType(), loc.dimension(), loc.pos(), showCoords, loc.displayName()))
                        .orElseGet(() -> new ActiveQuestEntry.GiverLocation(
                                ResourceLocation.withDefaultNamespace("villager"),
                                player.level().dimension().location(), player.blockPosition(), false, ""));

                entries.add(new ActiveQuestEntry(entityUuid, location, questOpt.get(), progress));
            }
        }

        ServerPlayNetworking.send(player, new ActiveQuestsPacket(entries));
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
                minecraft.player.getUUID(),
                packet.activeLine(),
                packet.resolvedLines(),
                packet.claimedRoots(),
                packet.acceptedRoots()
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
                packet.allQuestsCompleted(),
                packet.enraged()
        );
    }

    private static void handleActiveQuests(ActiveQuestsPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.setScreen(new com.qeapi.client.gui.ActiveQuestScreen(packet.entries()));
    }

    private static void handleSyncDeliveryTarget(SyncDeliveryTargetPacket packet) {
        if (!packet.active()) {
            ClientQuestCache.clearDeliveryTarget(packet.entityUuid());
            return;
        }

        net.minecraft.world.item.ItemStack stack;
        if (packet.questItem().isPresent()) {
            stack = packet.questItem().get().createStack(1);
        } else if (packet.itemId().isPresent()) {
            stack = new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(packet.itemId().get()));
        } else {
            stack = net.minecraft.world.item.ItemStack.EMPTY;
        }

        ClientQuestCache.setDeliveryTarget(packet.entityUuid(), stack);
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

        PlayerQuestData.LineSelectionState lineState = entity != null
                ? com.qeapi.api.QuestEntityAccess.getPlayerData(player).getLineSelection(entity.getUUID())
                : PlayerQuestData.LineSelectionState.empty();

        OpenQuestMenuPacket packet = new OpenQuestMenuPacket(entityId, component, quests,
                lineState.activeLine().map(List::of).orElse(List.of()),
                List.copyOf(lineState.resolvedLines()), List.copyOf(lineState.claimedRoots()),
                List.copyOf(lineState.acceptedRoots()));
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
                                             boolean allQuestsCompleted, boolean enraged) {
        SyncEntityQuestsPacket packet = new SyncEntityQuestsPacket(entityId, entityUuid, questPoolId, hasActiveQuest, isQuestComplete, allQuestsCompleted, enraged);
        ServerPlayNetworking.send(player, packet);
    }

    public static void sendSyncDeliveryTarget(ServerPlayer player, int entityId, UUID entityUuid, boolean active,
                                                Optional<ResourceLocation> itemId, Optional<com.qeapi.item.QuestItemDefinition> questItem) {
        ServerPlayNetworking.send(player, new SyncDeliveryTargetPacket(entityId, entityUuid, active, itemId, questItem));
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
    // SetQuestGroupReward) - a quest with none is a candidate regardless. Also respects questLine:
    // a quest with one set is only a candidate while that line is the player's active line for this
    // giver (see PlayerQuestData.LineSelectionState) - a quest with none is a candidate regardless.
    public static List<Quest> getAvailableQuestsForPlayer(List<QuestPool> pools, EntityQuestComponent component, ServerPlayer player, Entity entity) {
        UUID playerId = player.getUUID();
        UUID entityUuid = entity.getUUID();
        java.util.Set<ResourceLocation> completedQuests = component.getCompletedQuests(playerId);
        Optional<String> activeLine = com.qeapi.api.QuestEntityAccess.getPlayerData(player)
                .getLineSelection(entityUuid).activeLine();

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
                        if (!canAccept && !com.qeapi.config.QuestEntityAPIConfig.get().show_all_quests) {
                            continue;
                        }
                    }

                    if (quest.questGroup().isPresent()) {
                        Optional<String> chosenGroup = component.getChosenQuestGroup(playerId);
                        if (chosenGroup.isEmpty() || !chosenGroup.get().equals(quest.questGroup().get())) {
                            continue;
                        }
                    }

                    if (quest.questLine().isPresent()) {
                        if (activeLine.isEmpty() || !activeLine.get().equals(quest.questLine().get())) {
                            continue;
                        }
                        boolean priorStepsComplete = true;
                        for (Quest sibling : pool.getAllQuests()) {
                            if (sibling.questLine().isPresent() && sibling.questLine().get().equals(quest.questLine().get())
                                    && sibling.tier() < quest.tier() && !completedQuests.contains(sibling.id())) {
                                priorStepsComplete = false;
                                break;
                            }
                        }
                        if (!priorStepsComplete) {
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

            // Otherwise pick randomly by weight, skipping any quest whose find_structure task has
            // no matching structure within range of this entity (see isQuestGeographicallyEligible) -
            // a quest already accepted/completed is never re-filtered this way, only a fresh pick
            List<Quest> eligibleQuests = tierQuests.stream()
                    .filter(q -> isQuestGeographicallyEligible(q, entity))
                    .toList();
            if (eligibleQuests.isEmpty()) {
                continue;
            }

            int totalWeight = eligibleQuests.stream().mapToInt(Quest::weight).sum();
            if (totalWeight <= 0) {
                // Fallback: all weights <= 0
                available.add(eligibleQuests.get(random.nextInt(eligibleQuests.size())));
                continue;
            }

            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            Quest selectedQuest = eligibleQuests.get(0);

            for (Quest quest : eligibleQuests) {
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

    // A quest with a find_structure task is only offerable if the nearest matching structure is
    // within that task's max_distance of the quest-giving entity - see FindStructureTask.maxDistance.
    // Structure lookups are cached (see StructureDistanceUtil), so this is cheap after the first check.
    private static boolean isQuestGeographicallyEligible(Quest quest, Entity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return true;
        }
        for (QuestTask task : quest.tasks()) {
            if (task instanceof FindStructureTask findTask
                    && findTask.resolveNearestStructure(serverLevel, entity.blockPosition()).isEmpty()) {
                return false;
            }
            if (task instanceof com.qeapi.quest.task.DeliverItemTask deliverTask
                    && !com.qeapi.event.QuestEventHandler.hasDeliveryTargetNearby(entity, deliverTask)) {
                return false;
            }
        }
        return true;
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
