package com.qeapi.fabric;

import com.qeapi.QuestEntityAPI;
import com.qeapi.advancement.QuestCompleteTrigger;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.command.QuestCommands;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.data.ChunkAssignmentTracker;
import com.qeapi.data.EntityQuestAssignment;
import com.qeapi.data.EntityQuestAssignmentManager;
import com.qeapi.data.QuestManager;
import com.qeapi.event.QuestEventHandler;
import com.qeapi.fabric.network.FabricNetworking;
import com.qeapi.item.QuestItems;
import com.qeapi.loot.ConditionalDropLootSupport;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.ConditionalDropTask;
import com.qeapi.quest.task.EntityKillTask;
import com.qeapi.quest.task.QuestTask;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;

import java.util.*;

public final class QuestEntityAPIFabric implements ModInitializer {

    public static final AttachmentType<EntityQuestComponent> ENTITY_QUEST_ATTACHMENT =
            AttachmentRegistry.<EntityQuestComponent>builder()
                    .persistent(EntityQuestComponent.CODEC)
                    .buildAndRegister(QuestEntityAPI.id("entity_quests"));

    public static final AttachmentType<PlayerQuestData> PLAYER_QUEST_ATTACHMENT =
            AttachmentRegistry.<PlayerQuestData>builder()
                    .persistent(PlayerQuestData.CODEC)
                    .copyOnDeath()
                    .buildAndRegister(QuestEntityAPI.id("player_quests"));

    @Override
    public void onInitialize() {
        QuestEntityAPI.init();
        init();
    }

    public static void init() {
        QuestEntityAPI.LOGGER.info("Initializing Fabric-specific Quest Entity API components");

        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, QuestItems.QUEST_ITEM_ID, QuestItems.QUEST_ITEM);

        CriteriaTriggers.register(QuestCompleteTrigger.ID.toString(), QuestCompleteTrigger.INSTANCE);
        QuestEntityAPI.LOGGER.info("Registered advancement trigger: {}", QuestCompleteTrigger.ID);

        initQuestEntityAccess();
        registerDataLoader();
        registerCommands();
        registerEventHandlers();
        registerNetworking();
    }

    private static void initQuestEntityAccess() {
        QuestEntityAccess.init(
                entity -> entity.getAttached(ENTITY_QUEST_ATTACHMENT),
                (entity, component) -> entity.setAttached(ENTITY_QUEST_ATTACHMENT, component),
                player -> player.getAttachedOrCreate(PLAYER_QUEST_ATTACHMENT, PlayerQuestData::new),
                (player, data) -> player.setAttached(PLAYER_QUEST_ATTACHMENT, data)
        );
        QuestEntityAccess.initNearbyResyncTrigger(QuestEntityAPIFabric::forceResyncForNearbyPlayers);
    }

    private static void registerDataLoader() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new FabricQuestLoader());
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new FabricEntityQuestTagLoader());
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new FabricEntityQuestAssignmentLoader());
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            QuestCommands.register(dispatcher);
            QuestEntityAPI.LOGGER.info("Registered Quest Entity API commands");
        });
    }

    // entities each player has already received a sync packet for
    private static final Map<UUID, Set<UUID>> SYNCED_ENTITIES_PER_PLAYER = new HashMap<>();
    private static final double SYNC_DISTANCE = 32.0;

    // resolved deliver_item target UUIDs each player has already received a sync packet for -
    // same "sync once while nearby" shape as SYNCED_ENTITIES_PER_PLAYER, just keyed by the
    // resolved target's UUID instead of a quest giver's
    private static final Map<UUID, Set<UUID>> SYNCED_DELIVERY_TARGETS_PER_PLAYER = new HashMap<>();

    // call after an entity's quest data changes externally (e.g. villager job conversion) -
    // clears it from nearby players' synced sets so it re-syncs next tick
    public static void forceResyncForNearbyPlayers(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }

        UUID entityUuid = entity.getUUID();

        if (entity.level() instanceof ServerLevel serverLevel) {
            AABB searchBox = entity.getBoundingBox().inflate(SYNC_DISTANCE);
            List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(ServerPlayer.class, searchBox);

            for (ServerPlayer player : nearbyPlayers) {
                UUID playerId = player.getUUID();
                Set<UUID> syncedEntities = SYNCED_ENTITIES_PER_PLAYER.get(playerId);
                if (syncedEntities != null) {
                    syncedEntities.remove(entityUuid);
                    QuestEntityAPI.LOGGER.debug("Marked entity {} for re-sync to player {}",
                            entityUuid, player.getName().getString());
                }
            }
        }
    }

    private static void registerEventHandlers() {
        // see QuestEventHandler.progressSyncHandler for why this exists
        QuestEventHandler.setProgressSyncHandler((player, entityUuid) -> {
            Set<UUID> synced = SYNCED_ENTITIES_PER_PLAYER.get(player.getUUID());
            if (synced != null) {
                synced.remove(entityUuid);
            }
        });

        QuestEventHandler.setDeliveryClearedHandler((player, target) ->
                FabricNetworking.sendSyncDeliveryTarget(player, target.getId(), target.getUUID(), false,
                        java.util.Optional.empty(), java.util.Optional.empty()));

        if (com.qeapi.compat.SpellEngineCompat.isLoaded()) {
            com.qeapi.compat.SpellEngineCompat.registerCastListener();
            QuestEntityAPI.LOGGER.info("Spell Engine detected - registered spell cast listener for quest tasks");
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return; // once a second is enough

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                checkNearbyQuestEntities(player);
                checkDeliveryTargets(player);
            }
        });

        // the structure-distance cache is keyed by chunk coords only, not by world seed - clear it
        // so a later world (same JVM, e.g. singleplayer "save and quit" then load a different save)
        // never reuses another world's structure positions
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> com.qeapi.util.StructureDistanceUtil.clearCache());

        // appended to every loot table - a no-op for tables no ConditionalDropTask targets, see
        // ConditionalDropLootSupport for why the actual matching happens at roll time, not here
        LootTableEvents.MODIFY.register((key, tableBuilder, source) ->
                tableBuilder.withPool(ConditionalDropLootSupport.buildPoolBuilder(key.location())));

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    UUID playerUuid = handler.getPlayer().getUUID();
                    SYNCED_ENTITIES_PER_PLAYER.remove(playerUuid);
                    SYNCED_DELIVERY_TARGETS_PER_PLAYER.remove(playerUuid);
                }
        );

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            String damageType = damageSource.typeHolder().unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("unknown");
            QuestEntityAPI.LOGGER.debug("Entity {} died from damage type: {}, attacker: {}, direct: {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                    damageType,
                    damageSource.getEntity() != null ? damageSource.getEntity().getName().getString() : "none",
                    damageSource.getDirectEntity() != null ? damageSource.getDirectEntity().getName().getString() : "none");

            EntityQuestComponent questComponent = entity.getAttached(ENTITY_QUEST_ATTACHMENT);
            if (questComponent != null && !questComponent.isNoQuestMarker()) {
                handleQuestEntityDeath(entity, questComponent);
            }

            Entity attacker = damageSource.getEntity();
            if (attacker == null) {
                if (entity.level() instanceof ServerLevel serverLevel) {
                    handleEnvironmentalKill(serverLevel, entity, damageSource);
                }
                return;
            }

            if (!(attacker instanceof ServerPlayer player)) {
                QuestEntityAPI.LOGGER.debug("Attacker is not a player, skipping quest tracking");
                return;
            }

            handleEntityKill(player, entity, damageSource);

            // share entity_kill progress with nearby teammates (same scoreboard team, in range) - each
            // still needs to have independently accepted the matching quest for their own progress to
            // tick, and this only ever touches EntityKillTask progress (see handleEntityKill)
            for (ServerPlayer teammate : findNearbyTeammates(player, entity)) {
                handleEntityKill(teammate, entity, damageSource);
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }

            if (!(entity instanceof Villager) && !(entity instanceof WanderingTrader)) {
                return InteractionResult.PASS;
            }

            if (!com.qeapi.config.QuestEntityAPIConfig.get().hit_cooldown_enabled) {
                return InteractionResult.PASS;
            }

            EntityQuestComponent component = entity.getAttached(ENTITY_QUEST_ATTACHMENT);
            if (component == null || component.isNoQuestMarker()) {
                return InteractionResult.PASS;
            }

            UUID playerId = player.getGameProfile().getId();

            if (component.isOnCooldown(playerId)) {
                return InteractionResult.PASS;
            }

            int cooldownMinutes = com.qeapi.config.QuestEntityAPIConfig.get().hit_cooldown_minutes;

            // penalty applies regardless of whether the player had an active quest
            boolean hasActiveQuest = component.hasActiveQuest(playerId);
            EntityQuestComponent updated = component.withCooldown(playerId, cooldownMinutes * 60_000L);
            entity.setAttached(ENTITY_QUEST_ATTACHMENT, updated);

            if (player instanceof ServerPlayer serverPlayer) {
                if (hasActiveQuest) {
                    PlayerQuestData playerData = serverPlayer.getAttached(PLAYER_QUEST_ATTACHMENT);
                    if (playerData != null) {
                        playerData.clearEntityProgress(entity.getUUID());
                        serverPlayer.setAttached(PLAYER_QUEST_ATTACHMENT, playerData);
                    }

                }

                Set<UUID> syncedEntities = SYNCED_ENTITIES_PER_PLAYER.get(playerId);
                if (syncedEntities != null) {
                    syncedEntities.remove(entity.getUUID());
                }

                FabricNetworking.sendSyncEntityQuests(
                        serverPlayer,
                        entity.getId(),
                        entity.getUUID(),
                        updated.questPoolId(),
                        false,
                        false,
                        false,
                        true
                );

            }

            QuestEntityAPI.LOGGER.debug("Player {} hit quest villager - cooldown applied (had active quest: {})",
                    player.getName().getString(), hasActiveQuest);

            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }

            if (player.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }

            if (entity instanceof WanderingTrader) {
                return InteractionResult.PASS;
            }

            if (entity instanceof Villager villager) {
                VillagerProfession profession = villager.getVillagerData().getProfession();
                // unemployed (NONE) and nitwit villagers have no trade GUI, so they open the quest GUI directly
                if (profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT) {
                    return InteractionResult.PASS;
                }
            }

            EntityQuestComponent component = entity.getAttached(ENTITY_QUEST_ATTACHMENT);

            if (component != null && component.isNoQuestMarker()) {
                return InteractionResult.PASS;
            }

            if (component == null && player instanceof ServerPlayer serverPlayer) {
                component = tryAssignQuestPool(entity, serverPlayer.serverLevel());
                if (component != null) {
                    entity.setAttached(ENTITY_QUEST_ATTACHMENT, component);
                    if (component.isNoQuestMarker()) {
                        QuestEntityAPI.LOGGER.debug("Entity {} marked as no-quest from data-driven assignment",
                                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                        return InteractionResult.PASS;
                    }
                    QuestEntityAPI.LOGGER.info("Assigned quest pool {} to entity {} from data-driven assignment",
                            component.questPoolId(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                }
            }

            if (component == null || component.isNoQuestMarker()) {
                return InteractionResult.PASS;
            }

            if (player instanceof ServerPlayer serverPlayer && component.isOnCooldown(serverPlayer.getUUID())) {
                return InteractionResult.SUCCESS;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                openQuestGuiForPlayer(serverPlayer, entity, component);
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });

        // separate from the quest-giver GUI hook above - this fires on ANY entity, checking whether
        // it's the resolved deliver_item target for one of the interacting player's active quests
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || world.isClientSide()) {
                return InteractionResult.PASS;
            }

            if (player instanceof ServerPlayer serverPlayer && QuestEventHandler.tryDeliverItem(serverPlayer, entity)) {
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }

    private static void openQuestGuiForPlayer(ServerPlayer player, Entity entity, EntityQuestComponent component) {
        List<QuestPool> allPools = component.getAllQuestPools();
        if (allPools.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("No quest pools found for entity {} (pool/tag: {})",
                    entity.getId(), component.questPoolId());
            return;
        }

        List<Quest> quests = FabricNetworking.getAvailableQuestsForPlayer(allPools, component, player, entity);

        if (quests.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("No quests available for entity {}", entity.getId());
            return;
        }

        FabricNetworking.sendOpenQuestMenu(player, entity.getId(), component, quests);
    }

    // null if no assignments exist for this entity type, a "no quest" marker if assignments
    // exist but the chance check failed, otherwise a valid component
    private static EntityQuestComponent tryAssignQuestPool(Entity entity, ServerLevel level) {
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        if (entity instanceof Villager villager) {
            return tryAssignVillagerQuestPool(villager, level);
        }

        List<EntityQuestAssignment> allAssignments = EntityQuestAssignmentManager.getAssignmentsForEntity(entityTypeId);
        if (allAssignments.isEmpty()) {
            return null;
        }

        Optional<EntityQuestAssignment> assignmentOpt = EntityQuestAssignmentManager.findEntityAssignment(
                entityTypeId, level.getRandom());

        if (assignmentOpt.isEmpty()) {
            QuestEntityAPI.LOGGER.debug("Entity {} failed quest chance check, marking as no-quest", entityTypeId);
            return EntityQuestComponent.createNoQuest();
        }

        EntityQuestAssignment assignment = assignmentOpt.get();
        return createComponentFromAssignment(entity, level, assignment);
    }

    // same null/no-quest/valid-component contract as tryAssignQuestPool, but matched by biome + profession
    private static EntityQuestComponent tryAssignVillagerQuestPool(Villager villager, ServerLevel level) {
        String biomeType = getVillagerBiomeType(villager);
        String profession = getVillagerProfessionName(villager);

        QuestEntityAPI.LOGGER.debug("Trying to assign quest pool to villager: biome={}, profession={}",
                biomeType, profession);

        ResourceLocation villagerId = ResourceLocation.parse("minecraft:villager");
        List<EntityQuestAssignment> allAssignments = EntityQuestAssignmentManager.getAssignmentsForEntity(villagerId);
        if (allAssignments.isEmpty()) {
            return null;
        }

        Optional<EntityQuestAssignment> assignmentOpt = EntityQuestAssignmentManager.findVillagerAssignment(
                biomeType, profession, level.getRandom());

        if (assignmentOpt.isEmpty()) {
            QuestEntityAPI.LOGGER.debug("Villager (biome={}, profession={}) failed quest assignment, marking as no-quest",
                    biomeType, profession);
            return EntityQuestComponent.createNoQuest();
        }

        EntityQuestAssignment assignment = assignmentOpt.get();
        QuestEntityAPI.LOGGER.debug("Found villager assignment: {} -> {}", assignment.entityId(), assignment.questPools());
        return createComponentFromAssignment(villager, level, assignment);
    }

    // quest_pool always references a tag under tags/entity_quests/, e.g. "qe_api:farm" -> data/qe_api/tags/entity_quests/farm.json
    private static EntityQuestComponent createComponentFromAssignment(Entity entity, ServerLevel level, EntityQuestAssignment assignment) {
        if (assignment.chunkRestrictionRadius().isPresent()) {
            int radius = assignment.chunkRestrictionRadius().get();
            String restrictionKey = assignment.restrictionKey();
            ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
            if (ChunkAssignmentTracker.get(level).isRestricted(restrictionKey, chunkPos, radius)) {
                QuestEntityAPI.LOGGER.debug("[createComponentFromAssignment] Assignment {} is chunk-restricted near {}, marking as no-quest",
                        restrictionKey, chunkPos);
                return EntityQuestComponent.createNoQuest();
            }
        }

        String questPoolRef = assignment.pickQuestPool(level.getRandom());

        QuestEntityAPI.LOGGER.debug("[createComponentFromAssignment] Creating component from assignment: {}", questPoolRef);

        if (questPoolRef.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("[createComponentFromAssignment] quest_pool is empty!");
            return null;
        }

        // strip the legacy "tag:" prefix, and convert its "/" form (tag:qe_api/farm -> qe_api:farm)
        if (questPoolRef.startsWith("tag:")) {
            questPoolRef = questPoolRef.substring(4);
            if (questPoolRef.contains("/") && !questPoolRef.contains(":")) {
                int slashIndex = questPoolRef.indexOf('/');
                questPoolRef = questPoolRef.substring(0, slashIndex) + ":" + questPoolRef.substring(slashIndex + 1);
            }
            QuestEntityAPI.LOGGER.debug("[createComponentFromAssignment] Converted legacy tag format to: {}", questPoolRef);
        }

        ResourceLocation tagId = ResourceLocation.parse(questPoolRef);
        QuestEntityAPI.LOGGER.debug("[createComponentFromAssignment] Creating component with tag ID: {}", tagId);

        EntityQuestComponent component = EntityQuestComponent.create(tagId);

        List<QuestPool> pools = component.getAllQuestPools();
        if (pools.isEmpty()) {
            QuestEntityAPI.LOGGER.error("[createComponentFromAssignment] WARNING: Tag {} has no quest pools! " +
                    "Check that data/{}/tags/entity_quests/{}.json exists and references valid quest pool files.",
                    tagId, tagId.getNamespace(), tagId.getPath());
        } else {
            QuestEntityAPI.LOGGER.debug("[createComponentFromAssignment] Tag {} has {} quest pools", tagId, pools.size());
        }

        if (assignment.chunkRestrictionRadius().isPresent()) {
            ChunkAssignmentTracker.get(level).markAssigned(
                    assignment.restrictionKey(), new ChunkPos(entity.blockPosition()), entity.getUUID());
        }

        return component;
    }

    // VillagerType corresponds to the biome (plains, desert, jungle, etc.)
    private static String getVillagerBiomeType(Villager villager) {
        ResourceLocation typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType());
        return typeId != null ? typeId.getPath() : "plains";
    }

    // full resource location string, e.g. "minecraft:farmer" or "wizards:wizard_merchant"
    private static String getVillagerProfessionName(Villager villager) {
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                villager.getVillagerData().getProfession());
        return professionId != null ? professionId.toString() : "minecraft:none";
    }

    // no direct attacker (fire, lava, ...) - credits the kill to nearby players with a matching active quest
    private static void handleEnvironmentalKill(ServerLevel level, LivingEntity killed, DamageSource damageSource) {
        QuestEntityAPI.LOGGER.debug("Handling environmental kill of {} at {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()), killed.blockPosition());

        AABB searchBox = killed.getBoundingBox().inflate(32.0);
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, searchBox);

        for (ServerPlayer player : nearbyPlayers) {
            handleEntityKill(player, killed, damageSource);
        }
    }

    private static void handleQuestEntityDeath(LivingEntity entity, EntityQuestComponent component) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        UUID entityUuid = entity.getUUID();
        QuestEntityAPI.LOGGER.info("Quest entity {} died - removing active quests from players",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));

        ChunkAssignmentTracker.get(level).release(entityUuid);

        Map<UUID, EntityQuestComponent.ActiveQuestData> activeQuests = component.activeQuests();

        if (activeQuests.isEmpty()) {
            QuestEntityAPI.LOGGER.debug("No active quests on dying entity {}", entityUuid);
            return;
        }

        for (UUID playerId : activeQuests.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);

            if (player != null) {
                PlayerQuestData playerData = player.getAttached(PLAYER_QUEST_ATTACHMENT);
                if (playerData != null) {
                    playerData.clearEntityProgress(entityUuid);
                    player.setAttached(PLAYER_QUEST_ATTACHMENT, playerData);
                }

                Set<UUID> syncedEntities = SYNCED_ENTITIES_PER_PLAYER.get(playerId);
                if (syncedEntities != null) {
                    syncedEntities.remove(entityUuid);
                }

                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.qe_api.quest_entity_died").withStyle(net.minecraft.ChatFormatting.RED));

                QuestEntityAPI.LOGGER.info("Removed quest progress for player {} from dead entity {}",
                        player.getName().getString(), entityUuid);
            } else {
                // offline - their progress is cleared next login; the entity data is lost anyway since it's dying
                QuestEntityAPI.LOGGER.debug("Player {} is offline, quest progress will be orphaned", playerId);
            }
        }
    }

    // Same radius already used for other "nearby player" checks in this file (proximity sync,
    // environmental kill crediting) - reused here for team kill-sharing consistency.
    private static final double TEAM_KILL_SHARE_RANGE = 32.0;

    // empty if the player isn't on a team
    private static List<ServerPlayer> findNearbyTeammates(ServerPlayer player, LivingEntity killed) {
        PlayerTeam team = player.getTeam();
        if (team == null) {
            return List.of();
        }

        AABB searchBox = killed.getBoundingBox().inflate(TEAM_KILL_SHARE_RANGE);
        return player.serverLevel().getEntitiesOfClass(ServerPlayer.class, searchBox,
                other -> other != player && team.equals(other.getTeam()));
    }

    // handles both nearby loaded quest entities and unloaded ones tracked via PlayerQuestData
    private static void handleEntityKill(ServerPlayer player, LivingEntity killed, DamageSource damageSource) {
        ServerLevel level = player.serverLevel();
        UUID playerId = player.getUUID();

        String actualDamageType = damageSource.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
        QuestEntityAPI.LOGGER.debug("Entity {} killed by player {} with damage type: {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()), player.getName().getString(), actualDamageType);

        Set<UUID> processedEntities = new HashSet<>(); // avoid double-counting an entity handled in both passes below

        AABB searchBox = player.getBoundingBox().inflate(64.0);
        List<Entity> questEntities = level.getEntities(player, searchBox, entity -> {
            EntityQuestComponent component = entity.getAttached(ENTITY_QUEST_ATTACHMENT);
            return component != null && component.hasActiveQuest(playerId);
        });

        QuestEntityAPI.LOGGER.debug("Found {} nearby quest entities with active quests for player {}",
                questEntities.size(), player.getName().getString());

        for (Entity questEntity : questEntities) {
            processedEntities.add(questEntity.getUUID());
            handleEntityKillForQuestEntity(player, killed, damageSource, level, questEntity);
        }

        // unloaded entities aren't in the world, so their progress lives in PlayerQuestData instead
        PlayerQuestData playerData = player.getAttached(PLAYER_QUEST_ATTACHMENT);
        if (playerData != null && !playerData.isEmpty()) {
            Map<UUID, QuestProgress> allProgress = playerData.getAllProgress();

            for (Map.Entry<UUID, QuestProgress> entry : allProgress.entrySet()) {
                UUID entityUuid = entry.getKey();

                if (processedEntities.contains(entityUuid)) {
                    continue;
                }

                QuestProgress progress = entry.getValue();
                ResourceLocation questId = progress.getQuestId();

                Optional<Quest> questOpt = QuestManager.getQuest(questId);
                if (questOpt.isEmpty()) {
                    QuestEntityAPI.LOGGER.debug("Quest {} not found in QuestManager for unloaded entity {}",
                            questId, entityUuid);
                    continue;
                }

                Quest quest = questOpt.get();
                boolean progressUpdated = false;

                for (int i = 0; i < quest.tasks().size(); i++) {
                    QuestTask task = quest.tasks().get(i);
                    if (task instanceof EntityKillTask killTask) {
                        if (killTask.matches(killed, damageSource, level, player)) {
                            int currentProgress = progress.getTaskProgress(i);
                            if (currentProgress < killTask.amount()) {
                                progress.setTaskProgress(i, currentProgress + 1);
                                progressUpdated = true;
                                QuestEntityAPI.LOGGER.debug("Player {} killed matching entity for unloaded quest entity {}, task {}: {}/{}",
                                        player.getName().getString(), entityUuid, i, currentProgress + 1, killTask.amount());
                            }
                        }
                    } else if (task instanceof ConditionalDropTask dropTask) {
                        if (dropTask.matchesKill(killed, damageSource, level)) {
                            int currentProgress = progress.getTaskProgress(i);
                            if (currentProgress < dropTask.amount() && level.getRandom().nextDouble() < dropTask.mobDropChance()) {
                                killed.spawnAtLocation(dropTask.createGrantStack(1));
                                progress.setTaskProgress(i, currentProgress + 1);
                                progressUpdated = true;
                                QuestEntityAPI.LOGGER.debug("Player {} got a conditional drop for unloaded quest entity {}, task {}: {}/{}",
                                        player.getName().getString(), entityUuid, i, currentProgress + 1, dropTask.amount());
                            }
                        }
                    }
                }

                if (progressUpdated) {
                    playerData.setProgressForEntity(entityUuid, progress);
                    QuestEntityAPI.LOGGER.info("Updated kill progress for player {} on quest {} (entity {} unloaded)",
                            player.getName().getString(), questId, entityUuid);
                }
            }

            player.setAttached(PLAYER_QUEST_ATTACHMENT, playerData);
        }
    }

    private static void handleEntityKillForQuestEntity(ServerPlayer player, LivingEntity killed,
            DamageSource damageSource, ServerLevel level, Entity questEntity) {
        UUID playerId = player.getUUID();
        EntityQuestComponent component = questEntity.getAttached(ENTITY_QUEST_ATTACHMENT);
        if (component == null) return;

        Optional<EntityQuestComponent.ActiveQuestData> activeQuestOpt = component.getActiveQuest(playerId);
        if (activeQuestOpt.isEmpty()) return;

        EntityQuestComponent.ActiveQuestData activeQuest = activeQuestOpt.get();

        Quest quest = null;
        for (QuestPool pool : component.getAllQuestPools()) {
            quest = findQuestInPool(pool, activeQuest.questId());
            if (quest != null) break;
        }
        if (quest == null) return;

        boolean progressUpdated = false;
        QuestProgress progress = activeQuest.progress().copy();

        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestTask task = quest.tasks().get(i);
            if (task instanceof EntityKillTask killTask) {
                if (killTask.matches(killed, damageSource, level, player)) {
                    int currentProgress = progress.getTaskProgress(i);
                    if (currentProgress < killTask.amount()) {
                        progress.setTaskProgress(i, currentProgress + 1);
                        progressUpdated = true;
                        QuestEntityAPI.LOGGER.debug("Player {} killed matching entity for task {}: {}/{}",
                                player.getName().getString(), i, currentProgress + 1, killTask.amount());
                    }
                }
            } else if (task instanceof ConditionalDropTask dropTask) {
                if (dropTask.matchesKill(killed, damageSource, level)) {
                    int currentProgress = progress.getTaskProgress(i);
                    if (currentProgress < dropTask.amount() && level.getRandom().nextDouble() < dropTask.mobDropChance()) {
                        killed.spawnAtLocation(dropTask.createGrantStack(1));
                        progress.setTaskProgress(i, currentProgress + 1);
                        progressUpdated = true;
                        QuestEntityAPI.LOGGER.debug("Player {} got a conditional drop for task {}: {}/{}",
                                player.getName().getString(), i, currentProgress + 1, dropTask.amount());
                    }
                }
            }
        }

        if (progressUpdated) {
            EntityQuestComponent updated = component.withUpdatedProgress(playerId, progress);
            questEntity.setAttached(ENTITY_QUEST_ATTACHMENT, updated);

            PlayerQuestData playerData = player.getAttached(PLAYER_QUEST_ATTACHMENT);
            if (playerData != null) {
                playerData.setProgressForEntity(questEntity.getUUID(), progress);
                player.setAttached(PLAYER_QUEST_ATTACHMENT, playerData);
            }

            QuestEntityAPI.LOGGER.info("Updated kill progress for player {} on quest {}",
                    player.getName().getString(), activeQuest.questId());

            Set<UUID> syncedEntities = SYNCED_ENTITIES_PER_PLAYER.get(playerId);
            if (syncedEntities != null) {
                syncedEntities.remove(questEntity.getUUID());
            }
        }
    }

    private static Quest findQuestInPool(QuestPool pool, net.minecraft.resources.ResourceLocation questId) {
        for (List<Quest> tierQuests : pool.getQuestsByTier().values()) {
            for (Quest quest : tierQuests) {
                if (quest.id().equals(questId)) {
                    return quest;
                }
            }
        }
        return null;
    }

    // also syncs progress from PlayerQuestData back into EntityQuestComponent for entities returning into range
    private static void checkNearbyQuestEntities(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID playerId = player.getUUID();

        Set<UUID> syncedEntities = SYNCED_ENTITIES_PER_PLAYER.computeIfAbsent(playerId, k -> new HashSet<>());

        AABB searchBox = player.getBoundingBox().inflate(SYNC_DISTANCE);
        List<Entity> nearbyEntities = level.getEntities(player, searchBox, entity -> {
            EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(entity);
            if (component != null) {
                return true;
            }
            if (entity instanceof Villager) {
                return true; // villagers can always get a quest assignment
            }
            ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return !EntityQuestAssignmentManager.getAssignmentsForEntity(entityTypeId).isEmpty();
        });

        Set<UUID> currentlyNearby = new HashSet<>();
        PlayerQuestData playerData = player.getAttached(PLAYER_QUEST_ATTACHMENT);

        for (Entity entity : nearbyEntities) {
            UUID entityUuid = entity.getUUID();
            currentlyNearby.add(entityUuid);

            if (!syncedEntities.contains(entityUuid)) {
                EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(entity);

                if (component != null && component.isNoQuestMarker()) {
                    syncedEntities.add(entityUuid);
                    continue;
                }

                if (component == null) {
                    component = tryAssignQuestPool(entity, level);
                    if (component != null) {
                        entity.setAttached(ENTITY_QUEST_ATTACHMENT, component);
                        if (component.isNoQuestMarker()) {
                            QuestEntityAPI.LOGGER.debug("Entity {} marked as no-quest on proximity detection",
                                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                            syncedEntities.add(entityUuid);
                            continue;
                        }
                        QuestEntityAPI.LOGGER.info("Assigned quest pool {} to entity {} on proximity detection",
                                component.questPoolId(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                    }
                }

                if (component != null && !component.isNoQuestMarker()) {
                    // sync PlayerQuestData back onto the component - covers progress made while this entity was unloaded
                    if (playerData != null) {
                        Optional<QuestProgress> playerProgress = playerData.getProgressForEntity(entityUuid);
                        if (playerProgress.isPresent() && component.hasActiveQuest(playerId)) {
                            Optional<EntityQuestComponent.ActiveQuestData> activeQuestOpt = component.getActiveQuest(playerId);
                            if (activeQuestOpt.isPresent()) {
                                QuestProgress storedProgress = playerProgress.get();
                                QuestProgress entityProgress = activeQuestOpt.get().progress();

                                boolean needsSync = false;
                                for (Map.Entry<Integer, Integer> entry : storedProgress.getAllTaskProgress().entrySet()) {
                                    if (entry.getValue() > entityProgress.getTaskProgress(entry.getKey())) {
                                        needsSync = true;
                                        break;
                                    }
                                }

                                if (needsSync) {
                                    EntityQuestComponent updated = component.withUpdatedProgress(playerId, storedProgress);
                                    entity.setAttached(ENTITY_QUEST_ATTACHMENT, updated);
                                    component = updated;
                                    QuestEntityAPI.LOGGER.info("Synced progress from PlayerQuestData to EntityQuestComponent for entity {}",
                                            entityUuid);
                                }
                            }
                        }
                    }

                    boolean hasActiveQuest = component.hasActiveQuest(playerId);

                    // Refresh BringItemTask progress against current inventory before checking completion -
                    // bring-item progress isn't tracked incrementally like other task types, so without this
                    // the completion check below would use stale (usually zero) progress.
                    if (hasActiveQuest) {
                        List<QuestPool> refreshPools = component.getAllQuestPools();
                        if (!refreshPools.isEmpty()) {
                            component = FabricNetworking.checkAndUpdateBringItemProgress(player, entity, component, refreshPools.get(0));
                        }

                        // opportunistic refresh for the Active Quest screen - this entity is right here,
                        // in range of the same proximity scan that's already running once a second
                        if (playerData != null) {
                            playerData.recordEntityLocation(entity);
                            player.setAttached(PLAYER_QUEST_ATTACHMENT, playerData);
                        }
                    }

                    boolean isQuestComplete = false;

                    if (hasActiveQuest) {
                        isQuestComplete = checkQuestCompletion(player, component);
                    }

                    boolean allQuestsCompleted = false;
                    if (!hasActiveQuest) {
                        List<QuestPool> pools = component.getAllQuestPools();
                        if (!pools.isEmpty()) {
                            List<Quest> avail = FabricNetworking.getAvailableQuestsForPlayer(pools, component, player, entity);
                            Set<ResourceLocation> completed = component.getCompletedQuests(playerId);
                            allQuestsCompleted = !avail.isEmpty() && avail.stream()
                                    .allMatch(q -> completed.contains(q.id()));
                        }
                    }

                    FabricNetworking.sendSyncEntityQuests(
                            player,
                            entity.getId(),
                            entityUuid,
                            component.questPoolId(),
                            hasActiveQuest,
                            isQuestComplete,
                            allQuestsCompleted,
                            component.isOnCooldown(playerId)
                    );

                    syncedEntities.add(entityUuid);
                    QuestEntityAPI.LOGGER.debug("Sent quest sync for entity {} to player {}, active={}, complete={}",
                            entityUuid, player.getName().getString(), hasActiveQuest, isQuestComplete);
                }
            } else {
                // already synced this window, but bring_item completion is live (recomputed from
                // current inventory, can flip back to false) unlike every other task's monotonic
                // stored progress - resync those specifically every tick instead of waiting for the
                // player to leave and re-enter range
                EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(entity);
                if (component != null && !component.isNoQuestMarker() && component.hasActiveQuest(playerId)
                        && hasActiveBringItemTask(component, playerId)) {
                    List<QuestPool> refreshPools = component.getAllQuestPools();
                    if (!refreshPools.isEmpty()) {
                        component = FabricNetworking.checkAndUpdateBringItemProgress(player, entity, component, refreshPools.get(0));
                    }

                    if (playerData != null) {
                        playerData.recordEntityLocation(entity);
                        player.setAttached(PLAYER_QUEST_ATTACHMENT, playerData);
                    }

                    boolean isQuestComplete = checkQuestCompletion(player, component);

                    FabricNetworking.sendSyncEntityQuests(
                            player,
                            entity.getId(),
                            entityUuid,
                            component.questPoolId(),
                            true,
                            isQuestComplete,
                            false,
                            component.isOnCooldown(playerId)
                    );
                }
            }
        }

        syncedEntities.retainAll(currentlyNearby); // entities no longer nearby get re-synced when they come back
    }

    // true if the player's active quest with this entity has a bring_item task - see the resync
    // branch above for why those specifically need to bypass the already-synced skip
    private static boolean hasActiveBringItemTask(EntityQuestComponent component, UUID playerId) {
        Optional<EntityQuestComponent.ActiveQuestData> activeQuestOpt = component.getActiveQuest(playerId);
        if (activeQuestOpt.isEmpty()) {
            return false;
        }
        ResourceLocation questId = activeQuestOpt.get().questId();
        for (QuestPool pool : component.getAllQuestPools()) {
            Quest quest = findQuestInPool(pool, questId);
            if (quest != null) {
                for (QuestTask task : quest.tasks()) {
                    if (task instanceof BringItemTask) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    // syncs the floating item marker to nearby resolved deliver_item targets - entirely separate
    // from checkNearbyQuestEntities above, since a delivery target usually isn't a quest giver at
    // all and wouldn't otherwise show up in that scan
    private static void checkDeliveryTargets(ServerPlayer player) {
        PlayerQuestData playerData = player.getAttached(PLAYER_QUEST_ATTACHMENT);
        if (playerData == null || playerData.isEmpty()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        UUID playerId = player.getUUID();
        Set<UUID> synced = SYNCED_DELIVERY_TARGETS_PER_PLAYER.computeIfAbsent(playerId, k -> new HashSet<>());
        Set<UUID> currentlyNearby = new HashSet<>();
        AABB searchBox = player.getBoundingBox().inflate(SYNC_DISTANCE);

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            Optional<UUID> targetUuidOpt = playerData.getDeliveryTarget(entry.getKey());
            if (targetUuidOpt.isEmpty()) continue;
            UUID targetUuid = targetUuidOpt.get();

            QuestProgress progress = entry.getValue();
            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            for (int i = 0; i < questOpt.get().tasks().size(); i++) {
                QuestTask task = questOpt.get().tasks().get(i);
                if (!(task instanceof com.qeapi.quest.task.DeliverItemTask deliverTask)) continue;
                if (progress.getTaskProgress(i) >= deliverTask.amount()) break; // already delivered

                List<Entity> found = level.getEntities(player, searchBox, e -> e.getUUID().equals(targetUuid));
                if (found.isEmpty()) break;

                currentlyNearby.add(targetUuid);
                if (!synced.contains(targetUuid)) {
                    FabricNetworking.sendSyncDeliveryTarget(player, found.get(0).getId(), targetUuid, true,
                            deliverTask.itemId(), deliverTask.questItem());
                    synced.add(targetUuid);
                }
                break;
            }
        }

        synced.retainAll(currentlyNearby);
    }

    private static boolean checkQuestCompletion(ServerPlayer player, EntityQuestComponent component) {
        Optional<EntityQuestComponent.ActiveQuestData> activeQuestOpt = component.getActiveQuest(player.getUUID());
        if (activeQuestOpt.isEmpty()) {
            return false;
        }

        EntityQuestComponent.ActiveQuestData activeQuest = activeQuestOpt.get();
        QuestProgress progress = activeQuest.progress();

        List<QuestPool> pools = component.getAllQuestPools();
        for (QuestPool pool : pools) {
            for (List<Quest> tierQuests : pool.getQuestsByTier().values()) {
                for (Quest quest : tierQuests) {
                    if (quest.id().equals(activeQuest.questId())) {
                        return quest.isComplete(progress);
                    }
                }
            }
        }

        return false;
    }

    private static void registerNetworking() {
        com.qeapi.fabric.network.FabricNetworking.registerServer();

        com.qeapi.command.QuestCommands.QuestGuiOpener.setImpl(
                com.qeapi.fabric.network.FabricNetworking::sendOpenQuestMenu
        );
    }
}
