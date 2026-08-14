package com.qeapi.neoforge.network;

import com.qeapi.QuestEntityAPI;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.neoforge.QuestEntityAPINeoForge;
import com.qeapi.network.packet.AcceptQuestPacket;
import com.qeapi.network.packet.CancelQuestLinePacket;
import com.qeapi.network.packet.ChooseQuestLinePacket;
import com.qeapi.network.packet.ClaimQuestLineRootPacket;
import com.qeapi.network.packet.ClaimRewardsPacket;
import com.qeapi.network.packet.DismissQuestLineRootPacket;
import com.qeapi.network.packet.DismissQuestPacket;
import com.qeapi.network.packet.OpenQuestMenuPacket;
import com.qeapi.network.packet.QuestProgressPacket;
import com.qeapi.network.packet.ActiveQuestEntry;
import com.qeapi.network.packet.ActiveQuestsPacket;
import com.qeapi.network.packet.RequestActiveQuestsPacket;
import com.qeapi.network.packet.RequestMerchantMenuPacket;
import com.qeapi.network.packet.RequestQuestMenuPacket;
import com.qeapi.network.packet.SyncEntityQuestsPacket;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.FindStructureTask;
import com.qeapi.quest.task.QuestLineChoiceTask;
import com.qeapi.quest.task.QuestTask;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Server side only. Mirrors fabric.network.FabricNetworking's server handlers and send helpers.
// S2C payload handlers live in neoforge.client.QuestEntityAPINeoForgeClient instead, since
// their bodies touch Minecraft client classes that must never load on a dedicated server.
public final class NeoForgeNetworking {

    private NeoForgeNetworking() {}

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        QuestEntityAPI.LOGGER.info("Registering server-side Quest Entity API packets");

        var registrar = event.registrar("1");

        registrar.playToServer(AcceptQuestPacket.TYPE, AcceptQuestPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleAcceptQuest((ServerPlayer) context.player(), packet)));

        registrar.playToServer(DismissQuestPacket.TYPE, DismissQuestPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleDismissQuest((ServerPlayer) context.player(), packet)));

        registrar.playToServer(ClaimRewardsPacket.TYPE, ClaimRewardsPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleClaimRewards((ServerPlayer) context.player(), packet)));

        registrar.playToServer(RequestQuestMenuPacket.TYPE, RequestQuestMenuPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleRequestQuestMenu((ServerPlayer) context.player(), packet)));

        registrar.playToServer(RequestMerchantMenuPacket.TYPE, RequestMerchantMenuPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleRequestMerchantMenu((ServerPlayer) context.player(), packet)));

        registrar.playToServer(RequestActiveQuestsPacket.TYPE, RequestActiveQuestsPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleRequestActiveQuests((ServerPlayer) context.player())));

        registrar.playToServer(ChooseQuestLinePacket.TYPE, ChooseQuestLinePacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleChooseQuestLine((ServerPlayer) context.player(), packet)));

        registrar.playToServer(ClaimQuestLineRootPacket.TYPE, ClaimQuestLineRootPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleClaimQuestLineRoot((ServerPlayer) context.player(), packet)));

        registrar.playToServer(CancelQuestLinePacket.TYPE, CancelQuestLinePacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleCancelQuestLine((ServerPlayer) context.player(), packet)));

        registrar.playToServer(DismissQuestLineRootPacket.TYPE, DismissQuestLineRootPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleDismissQuestLineRoot((ServerPlayer) context.player(), packet)));
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

        EntityQuestComponent component = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);
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
            PlayerQuestData rootPlayerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
            PlayerQuestData.LineSelectionState lineState = rootPlayerData.getLineSelection(entityId);
            if (!lineState.acceptedRoots().contains(quest.id())) {
                rootPlayerData.setLineSelection(entityId, lineState.withAcceptedRoot(quest.id()));
                player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, rootPlayerData);
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
        entity.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, updatedComponent);

        // one-way: once accepted from, this giver is protected from despawning for good, even
        // after every quest with it wraps up - no-ops for non-Mob QuestEntity implementations
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setPersistenceRequired();
        }

        PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
        playerData.startQuest(entity.getUUID(), packet.questId());
        playerData.recordEntityLocation(entity);
        player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, playerData);

        // Grant a one-time structure map if this quest has a provides_map task (no-op otherwise)
        com.qeapi.event.QuestEventHandler.grantStructureMapIfNeeded(player, quest);
        // resolves deliver_item's target selector to one concrete entity; no-op otherwise
        com.qeapi.event.QuestEventHandler.resolveDeliveryTargetIfNeeded(player, entity, quest);
        com.qeapi.event.QuestEventHandler.playAcceptSound(player, quest);

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

        EntityQuestComponent component = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);
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
        entity.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, updatedComponent);

        List<QuestPool> allPools = component.getAllQuestPools();

        PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
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

        player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, playerData);


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

        EntityQuestComponent component = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);
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

        // Sync progress from player data to entity component (handles unloaded-entity progress)
        PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
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
                entity.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, updatedComponent);
                component = updatedComponent;
                activeQuest = component.getActiveQuest(playerId).orElse(activeQuest);
            }
        }

        List<QuestPool> allPools = component.getAllQuestPools();
        if (allPools.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("No quest pools found for claim");
            return;
        }

        Quest quest = null;
        for (QuestPool pool : allPools) {
            quest = findQuestInPool(pool, activeQuest.questId());
            if (quest != null) break;
        }
        if (quest == null) {
            QuestEntityAPI.LOGGER.warn("Quest {} not found in pools", activeQuest.questId());
            return;
        }

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
        EntityQuestComponent postGrantComponent = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT)
                .orElse(component);
        EntityQuestComponent updatedComponent = postGrantComponent.withCompletedQuest(playerId, activeQuest.questId(),
                player.serverLevel().getDayTime());
        entity.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, updatedComponent);
        com.qeapi.api.QuestEntityAccess.resolveQuestLineIfNeeded(player, entity, updatedComponent, quest);

        // checkNearbyQuestEntities only syncs an entity once per continuous presence in range,
        // so without forcing this the marker (e.g. available -> complete) won't refresh after claiming.
        QuestEntityAPINeoForge.forceResyncForNearbyPlayers(entity);

        com.qeapi.advancement.QuestCompleteTrigger.INSTANCE.trigger(player, activeQuest.questId());

        playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
        playerData.clearEntityProgress(entity.getUUID());
        player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, playerData);


        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));

        QuestEntityAPI.LOGGER.info("Player {} claimed rewards for quest {} from entity {}",
                player.getName().getString(), activeQuest.questId(), packet.entityId());
    }

    private static void handleChooseQuestLine(ServerPlayer player, ChooseQuestLinePacket packet) {
        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) return;

        EntityQuestComponent component = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);
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
        PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
        playerData.setLineSelection(entityUuid, playerData.getLineSelection(entityUuid).withActiveLine(packet.lineId()));
        player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, playerData);

        sendOpenQuestMenu(player, packet.entityId(), component, getAvailableQuestsForPlayer(allPools, component, player, entity));
        QuestEntityAPINeoForge.forceResyncForNearbyPlayers(entity);

        QuestEntityAPI.LOGGER.info("Player {} chose line {} for quest_line_choice root {}",
                player.getName().getString(), packet.lineId(), packet.rootQuestId());
    }

    private static void handleClaimQuestLineRoot(ServerPlayer player, ClaimQuestLineRootPacket packet) {
        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) return;

        EntityQuestComponent component = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);
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
        PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
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
        entity.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, updatedComponent);

        playerData.setLineSelection(entityUuid, lineState.withClaimedRoot(root.id()));
        player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, playerData);


        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));
        QuestEntityAPINeoForge.forceResyncForNearbyPlayers(entity);

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

        EntityQuestComponent component = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);
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

        PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
        PlayerQuestData.LineSelectionState lineState = playerData.getLineSelection(entityUuid);

        if (lineState.activeLine().isEmpty() || !lineState.activeLine().get().equals(packet.lineId())) {
            return;
        }

        playerData.setLineSelection(entityUuid, lineState.withoutActiveLine());

        EntityQuestComponent updatedComponent = clearActiveLineStep(component, allPools, player, entity, playerData, packet.lineId());

        player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, playerData);


        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));
        QuestEntityAPINeoForge.forceResyncForNearbyPlayers(entity);

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

        EntityQuestComponent component = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);
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

        PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
        PlayerQuestData.LineSelectionState lineState = playerData.getLineSelection(entityUuid);

        if (!lineState.acceptedRoots().contains(root.id()) || lineState.claimedRoots().contains(root.id())) {
            return;
        }

        EntityQuestComponent updatedComponent = component;
        if (lineState.activeLine().isPresent()) {
            updatedComponent = clearActiveLineStep(component, allPools, player, entity, playerData, lineState.activeLine().get());
        }

        playerData.setLineSelection(entityUuid, lineState.withoutActiveLine().withoutAcceptedRoot(root.id()));
        player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, playerData);


        sendOpenQuestMenu(player, packet.entityId(), updatedComponent, getAvailableQuestsForPlayer(allPools, updatedComponent, player, entity));
        QuestEntityAPINeoForge.forceResyncForNearbyPlayers(entity);

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
        entity.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, updatedComponent);
        playerData.clearEntityProgress(entity.getUUID());
        return updatedComponent;
    }

    private static void handleRequestQuestMenu(ServerPlayer player, RequestQuestMenuPacket packet) {
        QuestEntityAPI.LOGGER.debug("Player {} requesting quest menu for entity {}",
                player.getName().getString(), packet.entityId());

        // Close any open container first (e.g., merchant trading screen)
        player.closeContainer();

        Entity entity = player.serverLevel().getEntity(packet.entityId());
        if (entity == null) {
            QuestEntityAPI.LOGGER.warn("Entity {} not found for quest menu request", packet.entityId());
            return;
        }

        EntityQuestComponent component = entity.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);
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
        PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
        List<ActiveQuestEntry> entries = new java.util.ArrayList<>();

        boolean showCoords = com.qeapi.config.QuestEntityAPIConfig.get().show_quest_coordinates || player.isCreative();

        for (Map.Entry<UUID, QuestProgress> e : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = e.getKey();
            QuestProgress progress = e.getValue();

            Optional<Quest> questOpt = com.qeapi.data.QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Optional<PlayerQuestData.QuestGiverLocation> locOpt = playerData.getEntityLocation(entityUuid);
            ActiveQuestEntry.GiverLocation location = locOpt
                    .map(loc -> new ActiveQuestEntry.GiverLocation(loc.entityType(), loc.dimension(), loc.pos(), showCoords, loc.displayName()))
                    .orElseGet(() -> new ActiveQuestEntry.GiverLocation(
                            ResourceLocation.withDefaultNamespace("villager"),
                            player.level().dimension().location(), player.blockPosition(), false, ""));

            entries.add(new ActiveQuestEntry(entityUuid, location, questOpt.get(), progress));
        }

        PacketDistributor.sendToPlayer(player, new ActiveQuestsPacket(entries));
    }

    // ==================== Send Helpers ====================

    // Syncs progress from player data and updates BringItemTask progress before sending the menu.
    public static void sendOpenQuestMenu(ServerPlayer player, int entityId,
                                          EntityQuestComponent component, List<Quest> quests) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (entity != null && component.hasActiveQuest(player.getUUID())) {
            PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
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
                        entity.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, component);
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
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendQuestProgress(ServerPlayer player, int entityId,
                                          ResourceLocation questId,
                                          Map<Integer, Integer> taskProgress) {
        QuestProgressPacket packet = new QuestProgressPacket(entityId, questId, taskProgress);
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendSyncEntityQuests(ServerPlayer player, int entityId, UUID entityUuid,
                                             ResourceLocation questPoolId, boolean hasActiveQuest, boolean isQuestComplete,
                                             boolean allQuestsCompleted, boolean enraged) {
        SyncEntityQuestsPacket packet = new SyncEntityQuestsPacket(entityId, entityUuid, questPoolId, hasActiveQuest, isQuestComplete, allQuestsCompleted, enraged);
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendSyncDeliveryTarget(ServerPlayer player, int entityId, UUID entityUuid, boolean active,
                                                Optional<ResourceLocation> itemId, Optional<com.qeapi.item.QuestItemDefinition> questItem) {
        PacketDistributor.sendToPlayer(player, new com.qeapi.network.packet.SyncDeliveryTargetPacket(entityId, entityUuid, active, itemId, questItem));
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

            // Skip any quest whose find_structure task has no matching structure within range of
            // this entity (see isQuestGeographicallyEligible) - a quest already accepted/completed
            // above is never re-filtered this way, only a fresh pick
            List<Quest> eligibleQuests = tierQuests.stream()
                    .filter(q -> isQuestGeographicallyEligible(q, entity))
                    .toList();
            if (eligibleQuests.isEmpty()) {
                continue;
            }

            int totalWeight = eligibleQuests.stream().mapToInt(Quest::weight).sum();
            if (totalWeight <= 0) {
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

    // Diffs the player's inventory against originalProgress and returns an updated
    // component, or null if unchanged.
    private static EntityQuestComponent updateBringItemProgress(ServerPlayer player, Quest quest,
            EntityQuestComponent component, QuestProgress originalProgress) {
        boolean changed = false;

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

    // Updates BringItemTask progress for the active quest, across entity and player data.
    // Searches all pools associated with the component (supports tags).
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
            entity.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, updated);

            PlayerQuestData playerData = player.getData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT);
            playerData.setProgressForEntity(entity.getUUID(), progress);
            player.setData(QuestEntityAPINeoForge.PLAYER_QUEST_ATTACHMENT, playerData);

            return updated;
        }

        return component;
    }
}
