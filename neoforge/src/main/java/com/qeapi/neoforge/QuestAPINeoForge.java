package com.qeapi.neoforge;

import com.qeapi.QuestAPI;
import com.qeapi.advancement.QuestCompleteTrigger;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.command.QuestCommands;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.data.ChunkAssignmentTracker;
import com.qeapi.data.EntityQuestAssignment;
import com.qeapi.data.EntityQuestAssignmentLoader;
import com.qeapi.data.EntityQuestAssignmentManager;
import com.qeapi.data.EntityQuestTagLoader;
import com.qeapi.data.QuestLoader;
import com.qeapi.data.QuestManager;
import com.qeapi.event.QuestEventHandler;
import com.qeapi.item.QuestItems;
import com.qeapi.loot.ConditionalDropLootSupport;
import com.qeapi.mixin.LootTableAccessor;
import com.qeapi.neoforge.network.NeoForgeNetworking;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.ConditionalDropTask;
import com.qeapi.quest.task.EntityKillTask;
import com.qeapi.quest.task.QuestTask;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// NeoForge entrypoint, mirrors fabric.QuestAPIFabric using NeoForge-native APIs. NeoForge attachments never return null (unlike Fabric's nullable getAttached),
// so entity quest checks use getExistingData(...).orElse(null); PLAYER_QUEST_ATTACHMENT has a default (PlayerQuestData::new), so it's read via plain getData(...) with no null guard.
@Mod(QuestAPI.MOD_ID)
public final class QuestAPINeoForge {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, QuestAPI.MOD_ID);

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, QuestAPI.MOD_ID);

    public static final DeferredHolder<Item, Item> QUEST_ITEM =
            ITEMS.register(QuestItems.QUEST_ITEM_ID.getPath(), () -> QuestItems.QUEST_ITEM);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EntityQuestComponent>> ENTITY_QUEST_ATTACHMENT =
            ATTACHMENT_TYPES.register("entity_quests",
                    () -> AttachmentType.<EntityQuestComponent>builder(holder -> null)
                            .serialize(EntityQuestComponent.CODEC)
                            .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerQuestData>> PLAYER_QUEST_ATTACHMENT =
            ATTACHMENT_TYPES.register("player_quests",
                    () -> AttachmentType.builder(() -> new PlayerQuestData())
                            .serialize(PlayerQuestData.CODEC)
                            .copyOnDeath()
                            .build());

    public QuestAPINeoForge(IEventBus modEventBus) {
        QuestAPI.init();
        init(modEventBus);
    }

    public static void init(IEventBus modEventBus) {
        QuestAPI.LOGGER.info("Initializing NeoForge-specific Quest API components");

        ATTACHMENT_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);

        CriteriaTriggers.register(QuestCompleteTrigger.ID.toString(), QuestCompleteTrigger.INSTANCE);
        QuestAPI.LOGGER.info("Registered advancement trigger: {}", QuestCompleteTrigger.ID);

        initQuestEntityAccess();

        modEventBus.addListener(NeoForgeNetworking::registerPayloadHandlers);
        modEventBus.addListener(QuestAPINeoForge::onGatherData); // regenerates data/quest_api/entity_quest and tags/entity_quests

        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onServerTick);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onPlayerInteract);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onDeliverItemInteract);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onGrindstoneInteract);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onServerStopping);
        NeoForge.EVENT_BUS.addListener(QuestAPINeoForge::onLootTableLoad);

        QuestCommands.QuestGuiOpener.setImpl(NeoForgeNetworking::sendOpenQuestMenu);

        // see QuestEventHandler.progressSyncHandler for why this exists
        QuestEventHandler.setProgressSyncHandler((player, entityUuid) -> {
            Set<UUID> synced = SYNCED_ENTITIES_PER_PLAYER.get(player.getUUID());
            if (synced != null) {
                synced.remove(entityUuid);
            }
        });

        QuestEventHandler.setDeliveryClearedHandler((player, target) ->
                NeoForgeNetworking.sendSyncDeliveryTarget(player, target.getId(), target.getUUID(), false,
                        java.util.Optional.empty(), java.util.Optional.empty()));

        QuestEventHandler.setHudMessageHandler(NeoForgeNetworking::sendHudMessage);

        if (com.qeapi.compat.SpellEngineCompat.isLoaded()) {
            com.qeapi.compat.SpellEngineCompat.registerCastListener();
            QuestAPI.LOGGER.info("Spell Engine detected - registered spell cast listener for quest tasks");
        }
    }

    private static void initQuestEntityAccess() {
        QuestEntityAccess.init(
                entity -> entity.getExistingData(ENTITY_QUEST_ATTACHMENT).orElse(null),
                (entity, component) -> entity.setData(ENTITY_QUEST_ATTACHMENT, component),
                player -> player.getData(PLAYER_QUEST_ATTACHMENT),
                (player, data) -> player.setData(PLAYER_QUEST_ATTACHMENT, data)
        );
        QuestEntityAccess.initNearbyResyncTrigger(QuestAPINeoForge::forceResyncForNearbyPlayers);
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        // order matters: quests, then tags (validate pool existence), then assignments (validate quests+tags)
        event.addListener(new QuestLoader());
        event.addListener(new EntityQuestTagLoader());
        event.addListener(new EntityQuestAssignmentLoader());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        QuestCommands.register(event.getDispatcher());
        QuestAPI.LOGGER.info("Registered Quest API commands");
    }

    private static void onGatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                (net.minecraft.data.DataProvider.Factory<com.qeapi.datagen.ExampleQuestProvider>) com.qeapi.datagen.ExampleQuestProvider::new);
        event.getGenerator().addProvider(event.includeServer(),
                (net.minecraft.data.DataProvider.Factory<com.qeapi.datagen.ExampleAssignmentProvider>) com.qeapi.datagen.ExampleAssignmentProvider::new);
        // lang and item models are client-side assets, unlike the server-side quest JSON above
        event.getGenerator().addProvider(event.includeClient(),
                (net.minecraft.data.DataProvider.Factory<com.qeapi.datagen.LangProvider>) output ->
                        new com.qeapi.datagen.LangProvider(output, "quest_api", new com.qeapi.datagen.ExampleQuestProvider(output)));
        event.getGenerator().addProvider(event.includeClient(),
                (net.minecraft.data.DataProvider.Factory<com.qeapi.datagen.QuestItemModelProvider>) output ->
                        new com.qeapi.datagen.QuestItemModelProvider(output, "quest_api", new com.qeapi.datagen.ExampleQuestProvider(output)));
    }

    // entities each player has already received a sync packet for
    private static final Map<UUID, Set<UUID>> SYNCED_ENTITIES_PER_PLAYER = new HashMap<>();
    private static final double SYNC_DISTANCE = 32.0;

    // same "sync once while nearby" shape as SYNCED_ENTITIES_PER_PLAYER, keyed by the resolved delivery target's UUID instead of a quest giver's
    private static final Map<UUID, Set<UUID>> SYNCED_DELIVERY_TARGETS_PER_PLAYER = new HashMap<>();

    // call after an entity's quest data changes externally (e.g. villager job conversion), so it re-syncs next tick instead of staying stale
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
                    QuestAPI.LOGGER.debug("Marked entity {} for re-sync to player {}",
                            entityUuid, player.getName().getString());
                }
            }
        }
    }

    // structure-distance cache is keyed by chunk coords only, not world seed - clear it on stop so a later world in the same JVM can't reuse stale positions
    private static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.qeapi.util.StructureDistanceUtil.clearCache();
    }

    // a no-op for tables no ConditionalDropTask targets - see ConditionalDropLootSupport for why matching happens at roll time, not here.
    // LootTableLoadEvent only hands out the already-built LootTable (unlike Fabric's LootTableEvents.MODIFY), so the extra pool is spliced in via LootTableAccessor instead.
    private static void onLootTableLoad(LootTableLoadEvent event) {
        LootPool pool = ConditionalDropLootSupport.buildPoolBuilder(event.getName()).build();
        LootTableAccessor accessor = (LootTableAccessor) (Object) event.getTable();
        List<LootPool> pools = new ArrayList<>(accessor.quest_api$getPools());
        pools.add(pool);
        accessor.quest_api$setPools(List.copyOf(pools));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return; // once a second is enough

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            checkNearbyQuestEntities(player);
            checkDeliveryTargets(player);
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUuid = event.getEntity().getUUID();
        SYNCED_ENTITIES_PER_PLAYER.remove(playerUuid);
        SYNCED_DELIVERY_TARGETS_PER_PLAYER.remove(playerUuid);
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource damageSource = event.getSource();

        String damageType = damageSource.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
        QuestAPI.LOGGER.debug("Entity {} died from damage type: {}, attacker: {}, direct: {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                damageType,
                damageSource.getEntity() != null ? damageSource.getEntity().getName().getString() : "none",
                damageSource.getDirectEntity() != null ? damageSource.getDirectEntity().getName().getString() : "none");

        EntityQuestComponent questComponent = entity.getExistingData(ENTITY_QUEST_ATTACHMENT).orElse(null);
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
            QuestAPI.LOGGER.debug("Attacker is not a player, skipping quest tracking");
            return;
        }

        handleEntityKill(player, entity, damageSource);

        // shares entity_kill progress with nearby teammates, but each still needs to have independently accepted the quest for it to tick
        for (ServerPlayer teammate : findNearbyTeammates(player, entity)) {
            handleEntityKill(teammate, entity, damageSource);
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity entity = event.getTarget();

        if (player.level().isClientSide()) {
            return;
        }

        if (!(entity instanceof Villager) && !(entity instanceof WanderingTrader)) {
            return;
        }

        if (!com.qeapi.config.QuestAPIConfig.get().hit_cooldown_enabled) {
            return;
        }

        EntityQuestComponent component = entity.getExistingData(ENTITY_QUEST_ATTACHMENT).orElse(null);
        if (component == null || component.isNoQuestMarker()) {
            return;
        }

        UUID playerId = player.getGameProfile().getId();

        if (component.isOnCooldown(playerId)) {
            return;
        }

        int cooldownMinutes = com.qeapi.config.QuestAPIConfig.get().hit_cooldown_minutes;

        // penalty applies regardless of whether the player had an active quest
        boolean hasActiveQuest = component.hasActiveQuest(playerId);
        EntityQuestComponent updated = component.withCooldown(playerId, cooldownMinutes * 60_000L);
        entity.setData(ENTITY_QUEST_ATTACHMENT, updated);

        if (player instanceof ServerPlayer serverPlayer) {
            if (hasActiveQuest) {
                PlayerQuestData playerData = serverPlayer.getData(PLAYER_QUEST_ATTACHMENT);
                playerData.clearEntityProgress(entity.getUUID());
                serverPlayer.setData(PLAYER_QUEST_ATTACHMENT, playerData);
            }

            Set<UUID> syncedEntities = SYNCED_ENTITIES_PER_PLAYER.get(playerId);
            if (syncedEntities != null) {
                syncedEntities.remove(entity.getUUID());
            }

            NeoForgeNetworking.sendSyncEntityQuests(
                    serverPlayer,
                    entity.getId(),
                    entity.getUUID(),
                    updated.questPoolId(),
                    false,
                    false,
                    false,
                    true
            );

            NeoForgeNetworking.sendHudMessage(serverPlayer, Component.translatable(
                    "hud.quest_api.quest_entity_enraged",
                    com.qeapi.util.EntityNameResolver.resolve(entity), cooldownMinutes));

        }

        QuestAPI.LOGGER.debug("Player {} hit quest villager - cooldown applied (had active quest: {})",
                player.getName().getString(), hasActiveQuest);
    }

    private static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        Entity entity = event.getTarget();

        if (player.level().isClientSide()) {
            return;
        }

        if (player.isSecondaryUseActive()) {
            return;
        }

        if (entity instanceof WanderingTrader) {
            return;
        }

        if (entity instanceof Villager villager) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            // unemployed (NONE) and nitwit villagers have no trade GUI, so they open the quest GUI directly
            if (profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT) {
                return;
            }
        }

        EntityQuestComponent component = entity.getExistingData(ENTITY_QUEST_ATTACHMENT).orElse(null);

        if (component != null && component.isNoQuestMarker()) {
            return;
        }

        if (component == null && player instanceof ServerPlayer serverPlayer) {
            component = tryAssignQuestPool(entity, serverPlayer.serverLevel());
            if (component != null) {
                entity.setData(ENTITY_QUEST_ATTACHMENT, component);
                if (component.isNoQuestMarker()) {
                    QuestAPI.LOGGER.debug("Entity {} marked as no-quest from data-driven assignment",
                            BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                    return;
                }
                QuestAPI.LOGGER.info("Assigned quest pool {} to entity {} from data-driven assignment",
                        component.questPoolId(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
            }
        }

        if (component == null || component.isNoQuestMarker()) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer && component.isOnCooldown(serverPlayer.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            openQuestGuiForPlayer(serverPlayer, entity, component);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    // separate from onPlayerInteract above - fires on any entity, checking whether it's a resolved deliver_item target
    private static void onDeliverItemInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        Entity entity = event.getTarget();

        if (player.level().isClientSide()) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer && QuestEventHandler.tryDeliverItem(serverPlayer, entity)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void onGrindstoneInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        if (!level.getBlockState(event.getPos()).is(Blocks.GRINDSTONE)) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer serverPlayer
                && com.qeapi.item.QuestItemRecycling.tryRecycle(serverPlayer, event.getHand(), level, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void openQuestGuiForPlayer(ServerPlayer player, Entity entity, EntityQuestComponent component) {
        List<QuestPool> allPools = component.getAllQuestPools();
        if (allPools.isEmpty()) {
            QuestAPI.LOGGER.warn("No quest pools found for entity {} (pool/tag: {})",
                    entity.getId(), component.questPoolId());
            return;
        }

        List<Quest> quests = NeoForgeNetworking.getAvailableQuestsForPlayer(allPools, component, player, entity);

        if (quests.isEmpty()) {
            QuestAPI.LOGGER.warn("No quests available for entity {}", entity.getId());
            return;
        }

        NeoForgeNetworking.sendOpenQuestMenu(player, entity.getId(), component, quests);
    }

    // null if no assignments exist for this entity type, a "no quest" marker if the chance check failed, otherwise a valid component
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
            QuestAPI.LOGGER.debug("Entity {} failed quest chance check, marking as no-quest", entityTypeId);
            return EntityQuestComponent.createNoQuest();
        }

        EntityQuestAssignment assignment = assignmentOpt.get();
        return createComponentFromAssignment(entity, level, assignment);
    }

    // same null/no-quest/valid-component contract as tryAssignQuestPool, but matched by biome + profession
    private static EntityQuestComponent tryAssignVillagerQuestPool(Villager villager, ServerLevel level) {
        String biomeType = getVillagerBiomeType(villager);
        String profession = getVillagerProfessionName(villager);

        QuestAPI.LOGGER.debug("Trying to assign quest pool to villager: biome={}, profession={}",
                biomeType, profession);

        ResourceLocation villagerId = ResourceLocation.parse("minecraft:villager");
        List<EntityQuestAssignment> allAssignments = EntityQuestAssignmentManager.getAssignmentsForEntity(villagerId);
        if (allAssignments.isEmpty()) {
            return null;
        }

        Optional<EntityQuestAssignment> assignmentOpt = EntityQuestAssignmentManager.findVillagerAssignment(
                biomeType, profession, level.getRandom());

        if (assignmentOpt.isEmpty()) {
            QuestAPI.LOGGER.debug("Villager (biome={}, profession={}) failed quest assignment, marking as no-quest",
                    biomeType, profession);
            return EntityQuestComponent.createNoQuest();
        }

        EntityQuestAssignment assignment = assignmentOpt.get();
        QuestAPI.LOGGER.debug("Found villager assignment: {} -> {}", assignment.entityId(), assignment.questPools());
        return createComponentFromAssignment(villager, level, assignment);
    }

    // quest_pool always references a tag under tags/entity_quests/
    private static EntityQuestComponent createComponentFromAssignment(Entity entity, ServerLevel level, EntityQuestAssignment assignment) {
        if (assignment.chunkRestrictionRadius().isPresent()) {
            int radius = assignment.chunkRestrictionRadius().get();
            String restrictionKey = assignment.restrictionKey();
            ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
            if (ChunkAssignmentTracker.get(level).isRestricted(restrictionKey, chunkPos, radius)) {
                QuestAPI.LOGGER.debug("[createComponentFromAssignment] Assignment {} is chunk-restricted near {}, marking as no-quest",
                        restrictionKey, chunkPos);
                return EntityQuestComponent.createNoQuest();
            }
        }

        String questPoolRef = assignment.pickQuestPool(level.getRandom());

        QuestAPI.LOGGER.debug("[createComponentFromAssignment] Creating component from assignment: {}", questPoolRef);

        if (questPoolRef.isEmpty()) {
            QuestAPI.LOGGER.warn("[createComponentFromAssignment] quest_pool is empty!");
            return null;
        }

        // strip the legacy "tag:" prefix, and convert its "/" form (tag:quest_api/farm -> quest_api:farm)
        if (questPoolRef.startsWith("tag:")) {
            questPoolRef = questPoolRef.substring(4);
            if (questPoolRef.contains("/") && !questPoolRef.contains(":")) {
                int slashIndex = questPoolRef.indexOf('/');
                questPoolRef = questPoolRef.substring(0, slashIndex) + ":" + questPoolRef.substring(slashIndex + 1);
            }
            QuestAPI.LOGGER.debug("[createComponentFromAssignment] Converted legacy tag format to: {}", questPoolRef);
        }

        ResourceLocation tagId = ResourceLocation.parse(questPoolRef);
        QuestAPI.LOGGER.debug("[createComponentFromAssignment] Creating component with tag ID: {}", tagId);

        EntityQuestComponent component = EntityQuestComponent.create(tagId,
                assignment.acceptQuestSoundOverride(), assignment.finishQuestSoundOverride());

        List<QuestPool> pools = component.getAllQuestPools();
        if (pools.isEmpty()) {
            QuestAPI.LOGGER.error("[createComponentFromAssignment] WARNING: Tag {} has no quest pools! " +
                    "Check that data/{}/tags/entity_quests/{}.json exists and references valid quest pool files.",
                    tagId, tagId.getNamespace(), tagId.getPath());
        } else {
            QuestAPI.LOGGER.debug("[createComponentFromAssignment] Tag {} has {} quest pools", tagId, pools.size());
        }

        if (assignment.chunkRestrictionRadius().isPresent()) {
            ChunkAssignmentTracker.get(level).markAssigned(
                    assignment.restrictionKey(), new ChunkPos(entity.blockPosition()), entity.getUUID());
        }

        return component;
    }

    private static String getVillagerBiomeType(Villager villager) {
        ResourceLocation typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType());
        return typeId != null ? typeId.getPath() : "plains";
    }

    private static String getVillagerProfessionName(Villager villager) {
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                villager.getVillagerData().getProfession());
        return professionId != null ? professionId.toString() : "minecraft:none";
    }

    // no direct attacker (fire, lava, ...) - credits the kill to nearby players with a matching active quest
    private static void handleEnvironmentalKill(ServerLevel level, LivingEntity killed, DamageSource damageSource) {
        QuestAPI.LOGGER.debug("Handling environmental kill of {} at {}",
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
        QuestAPI.LOGGER.info("Quest entity {} died - removing active quests from players",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));

        // Free up any chunk-restricted assignment slot this entity was holding
        ChunkAssignmentTracker.get(level).release(entityUuid);

        Map<UUID, EntityQuestComponent.ActiveQuestData> activeQuests = component.activeQuests();

        if (activeQuests.isEmpty()) {
            QuestAPI.LOGGER.debug("No active quests on dying entity {}", entityUuid);
            return;
        }

        for (UUID playerId : activeQuests.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);

            if (player != null) {
                PlayerQuestData playerData = player.getData(PLAYER_QUEST_ATTACHMENT);
                playerData.clearEntityProgress(entityUuid);
                player.setData(PLAYER_QUEST_ATTACHMENT, playerData);

                Set<UUID> syncedEntities = SYNCED_ENTITIES_PER_PLAYER.get(playerId);
                if (syncedEntities != null) {
                    syncedEntities.remove(entityUuid);
                }

                player.sendSystemMessage(Component.translatable(
                        "message.quest_api.quest_entity_died").withStyle(ChatFormatting.RED));

                QuestAPI.LOGGER.info("Removed quest progress for player {} from dead entity {}",
                        player.getName().getString(), entityUuid);
            } else {
                QuestAPI.LOGGER.debug("Player {} is offline, quest progress will be orphaned", playerId);
            }
        }
    }

    // same radius used for the other "nearby player" checks in this file, reused here for consistency
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
        QuestAPI.LOGGER.debug("Entity {} killed by player {} with damage type: {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()), player.getName().getString(), actualDamageType);

        Set<UUID> processedEntities = new HashSet<>(); // avoid double-counting an entity handled in both passes below

        AABB searchBox = player.getBoundingBox().inflate(64.0);
        List<Entity> questEntities = level.getEntities(player, searchBox, entity -> {
            EntityQuestComponent component = entity.getExistingData(ENTITY_QUEST_ATTACHMENT).orElse(null);
            return component != null && component.hasActiveQuest(playerId);
        });

        QuestAPI.LOGGER.debug("Found {} nearby quest entities with active quests for player {}",
                questEntities.size(), player.getName().getString());

        for (Entity questEntity : questEntities) {
            processedEntities.add(questEntity.getUUID());
            handleEntityKillForQuestEntity(player, killed, damageSource, level, questEntity);
        }

        // unloaded entities aren't in the world, so their progress lives in PlayerQuestData instead
        PlayerQuestData playerData = player.getData(PLAYER_QUEST_ATTACHMENT);
        if (!playerData.isEmpty()) {
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
                    QuestAPI.LOGGER.debug("Quest {} not found in QuestManager for unloaded entity {}",
                            questId, entityUuid);
                    continue;
                }

                Quest quest = questOpt.get();
                boolean progressUpdated = false;

                for (int i = 0; i < quest.tasks().size(); i++) {
                    QuestTask task = quest.tasks().get(i);
                    if (task instanceof EntityKillTask killTask) {
                        if (killTask.matches(killed, damageSource, level, player) && quest.isTaskUnlocked(progress, i)) {
                            int currentProgress = progress.getTaskProgress(i);
                            if (currentProgress < killTask.amount()) {
                                progress.setTaskProgress(i, currentProgress + 1);
                                progressUpdated = true;
                                QuestAPI.LOGGER.debug("Player {} killed matching entity for unloaded quest entity {}, task {}: {}/{}",
                                        player.getName().getString(), entityUuid, i, currentProgress + 1, killTask.amount());
                            }
                        }
                    } else if (task instanceof ConditionalDropTask dropTask) {
                        if (dropTask.matchesKill(killed, damageSource, level)) {
                            int currentProgress = progress.getTaskProgress(i);
                            // self-heal against a lost item - see ConditionalDropLootSupport.tryGrant. Persisted even
                            // if the drop_chance roll below misses, so it isn't left stale.
                            int heldCount = dropTask.countMatchingItems(player.getInventory().items);
                            if (heldCount < currentProgress) {
                                currentProgress = heldCount;
                                progress.setTaskProgress(i, currentProgress);
                                progressUpdated = true;
                            }
                            if (currentProgress < dropTask.amount() && level.getRandom().nextDouble() < dropTask.mobDropFilters().dropChance()) {
                                killed.spawnAtLocation(dropTask.createGrantStack(1));
                                progress.setTaskProgress(i, currentProgress + 1);
                                progressUpdated = true;
                                QuestAPI.LOGGER.debug("Player {} got a conditional drop for unloaded quest entity {}, task {}: {}/{}",
                                        player.getName().getString(), entityUuid, i, currentProgress + 1, dropTask.amount());
                            }
                        }
                    }
                }

                if (progressUpdated) {
                    playerData.setProgressForEntity(entityUuid, progress);
                    QuestAPI.LOGGER.info("Updated kill progress for player {} on quest {} (entity {} unloaded)",
                            player.getName().getString(), questId, entityUuid);
                }
            }

            player.setData(PLAYER_QUEST_ATTACHMENT, playerData);
        }
    }

    private static void handleEntityKillForQuestEntity(ServerPlayer player, LivingEntity killed,
            DamageSource damageSource, ServerLevel level, Entity questEntity) {
        UUID playerId = player.getUUID();
        EntityQuestComponent component = questEntity.getExistingData(ENTITY_QUEST_ATTACHMENT).orElse(null);
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
                if (killTask.matches(killed, damageSource, level, player) && quest.isTaskUnlocked(progress, i)) {
                    int currentProgress = progress.getTaskProgress(i);
                    if (currentProgress < killTask.amount()) {
                        progress.setTaskProgress(i, currentProgress + 1);
                        progressUpdated = true;
                        QuestAPI.LOGGER.debug("Player {} killed matching entity for task {}: {}/{}",
                                player.getName().getString(), i, currentProgress + 1, killTask.amount());
                    }
                }
            } else if (task instanceof ConditionalDropTask dropTask) {
                if (dropTask.matchesKill(killed, damageSource, level)) {
                    int currentProgress = progress.getTaskProgress(i);
                    // self-heal against a lost item - see ConditionalDropLootSupport.tryGrant. Persisted even
                    // if the drop_chance roll below misses, so it isn't left stale.
                    int heldCount = dropTask.countMatchingItems(player.getInventory().items);
                    if (heldCount < currentProgress) {
                        currentProgress = heldCount;
                        progress.setTaskProgress(i, currentProgress);
                        progressUpdated = true;
                    }
                    if (currentProgress < dropTask.amount() && level.getRandom().nextDouble() < dropTask.mobDropFilters().dropChance()) {
                        killed.spawnAtLocation(dropTask.createGrantStack(1));
                        progress.setTaskProgress(i, currentProgress + 1);
                        progressUpdated = true;
                        QuestAPI.LOGGER.debug("Player {} got a conditional drop for task {}: {}/{}",
                                player.getName().getString(), i, currentProgress + 1, dropTask.amount());
                    }
                }
            }
        }

        if (progressUpdated) {
            EntityQuestComponent updated = component.withUpdatedProgress(playerId, progress);
            questEntity.setData(ENTITY_QUEST_ATTACHMENT, updated);

            PlayerQuestData playerData = player.getData(PLAYER_QUEST_ATTACHMENT);
            playerData.setProgressForEntity(questEntity.getUUID(), progress);
            player.setData(PLAYER_QUEST_ATTACHMENT, playerData);

            QuestAPI.LOGGER.info("Updated kill progress for player {} on quest {}",
                    player.getName().getString(), activeQuest.questId());

            Set<UUID> syncedEntities = SYNCED_ENTITIES_PER_PLAYER.get(playerId);
            if (syncedEntities != null) {
                syncedEntities.remove(questEntity.getUUID());
            }
        }
    }

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
                return true;
            }
            ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return !EntityQuestAssignmentManager.getAssignmentsForEntity(entityTypeId).isEmpty();
        });

        Set<UUID> currentlyNearby = new HashSet<>();

        PlayerQuestData playerData = player.getData(PLAYER_QUEST_ATTACHMENT);

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
                        entity.setData(ENTITY_QUEST_ATTACHMENT, component);
                        if (component.isNoQuestMarker()) {
                            QuestAPI.LOGGER.debug("Entity {} marked as no-quest on proximity detection",
                                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                            syncedEntities.add(entityUuid);
                            continue;
                        }
                        QuestAPI.LOGGER.info("Assigned quest pool {} to entity {} on proximity detection",
                                component.questPoolId(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                    }
                }

                if (component != null && !component.isNoQuestMarker()) {
                    // sync PlayerQuestData back onto the component - covers progress made while this entity was unloaded
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
                                entity.setData(ENTITY_QUEST_ATTACHMENT, updated);
                                component = updated;
                                QuestAPI.LOGGER.info("Synced progress from PlayerQuestData to EntityQuestComponent for entity {}",
                                        entityUuid);
                            }
                        }
                    }

                    boolean hasActiveQuest = component.hasActiveQuest(playerId);

                    // bring-item progress isn't tracked incrementally like other tasks, so it needs a refresh here or completion below reads stale (usually zero) progress
                    if (hasActiveQuest) {
                        List<QuestPool> refreshPools = component.getAllQuestPools();
                        if (!refreshPools.isEmpty()) {
                            component = NeoForgeNetworking.checkAndUpdateBringItemProgress(player, entity, component, refreshPools.get(0));
                        }

                        // opportunistic refresh for the Active Quest screen, since this entity is already in range of this proximity scan
                        playerData.recordEntityLocation(entity);
                        player.setData(PLAYER_QUEST_ATTACHMENT, playerData);
                    }

                    boolean isQuestComplete = false;

                    if (hasActiveQuest) {
                        isQuestComplete = checkQuestCompletion(player, component);
                    }

                    boolean allQuestsCompleted = false;
                    if (!hasActiveQuest) {
                        List<QuestPool> pools = component.getAllQuestPools();
                        if (!pools.isEmpty()) {
                            List<Quest> avail = NeoForgeNetworking.getAvailableQuestsForPlayer(pools, component, player, entity);
                            Set<ResourceLocation> completed = component.getCompletedQuests(playerId);
                            allQuestsCompleted = !avail.isEmpty() && avail.stream()
                                    .allMatch(q -> completed.contains(q.id()));
                        }
                    }

                    NeoForgeNetworking.sendSyncEntityQuests(
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
                    QuestAPI.LOGGER.debug("Sent quest sync for entity {} to player {}, active={}, complete={}",
                            entityUuid, player.getName().getString(), hasActiveQuest, isQuestComplete);
                }
            } else {
                // already synced this window, but bring_item completion is live and can flip back to false, unlike every other task's monotonic progress - resync it every tick
                EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(entity);
                if (component != null && !component.isNoQuestMarker() && component.hasActiveQuest(playerId)
                        && hasActiveBringItemTask(component, playerId)) {
                    List<QuestPool> refreshPools = component.getAllQuestPools();
                    if (!refreshPools.isEmpty()) {
                        component = NeoForgeNetworking.checkAndUpdateBringItemProgress(player, entity, component, refreshPools.get(0));
                    }

                    playerData.recordEntityLocation(entity);
                    player.setData(PLAYER_QUEST_ATTACHMENT, playerData);

                    boolean isQuestComplete = checkQuestCompletion(player, component);

                    NeoForgeNetworking.sendSyncEntityQuests(
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

        syncedEntities.retainAll(currentlyNearby);
    }

    // true if the player's active quest with this entity has a bring_item task - see the resync branch above for why those need to bypass the already-synced skip
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

    // syncs the floating item marker to nearby resolved deliver_item targets - separate from checkNearbyQuestEntities since a delivery target usually isn't a quest giver
    private static void checkDeliveryTargets(ServerPlayer player) {
        PlayerQuestData playerData = player.getData(PLAYER_QUEST_ATTACHMENT);
        if (playerData.isEmpty()) {
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
                    NeoForgeNetworking.sendSyncDeliveryTarget(player, found.get(0).getId(), targetUuid, true,
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
}
