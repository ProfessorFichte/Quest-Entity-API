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
import com.qeapi.compat.SpellEngineCompat;
import com.qeapi.mixin.VillagerXpAccessor;
import com.qeapi.util.StructureMapUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.*;
import java.util.function.BiConsumer;

public final class QuestEventHandler {

    private QuestEventHandler() {}

    // Re-triggers the periodic nearby-entity sync for one entity - that sync only fires once per
    // entity per player on first proximity detection, so without this a passively-tracked task
    // completing while the entity stays in range would never flip the marker.
    private static BiConsumer<ServerPlayer, UUID> progressSyncHandler;

    public static void setProgressSyncHandler(BiConsumer<ServerPlayer, UUID> handler) {
        progressSyncHandler = handler;
    }

    // Platform-specific delivery-marker-clear implementation - sends the client an explicit
    // SyncDeliveryTargetPacket(active=false) so the floating item icon disappears the instant the
    // item's actually delivered, rather than lingering until the target next drops out of range.
    private static java.util.function.BiConsumer<ServerPlayer, Entity> deliveryClearedHandler;

    public static void setDeliveryClearedHandler(java.util.function.BiConsumer<ServerPlayer, Entity> handler) {
        deliveryClearedHandler = handler;
    }

    // Grants villager trading XP scaled by tier on quest completion; no-op for non-villager givers.
    // setVillagerXp is a plain field setter - the level-up check (shouldIncreaseLevel/
    // increaseMerchantCareer) is private and normally only runs off a deferred 40-tick trade
    // timer, so VillagerXpAccessor replicates it here to apply immediately.
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

    private static final ResourceLocation DEFAULT_ACCEPT_SOUND = ResourceLocation.withDefaultNamespace("block.note_block.chime");
    private static final ResourceLocation DEFAULT_FINISH_SOUND = ResourceLocation.withDefaultNamespace("entity.player.levelup");

    // Sound id is resolved via a raw variable-range SoundEvent rather than a BuiltInRegistries
    // lookup, so a resource-pack-only sound (no Java SoundEvent registration) works too - the
    // same way vanilla's /playsound command plays an arbitrary id.
    public static void playAcceptSound(ServerPlayer player, Quest quest) {
        playQuestSound(player, quest.acceptQuestSoundOverride().orElse(DEFAULT_ACCEPT_SOUND));
    }

    public static void playClaimEffects(ServerPlayer player, Quest quest) {
        playQuestSound(player, quest.finishQuestSoundOverride().orElse(DEFAULT_FINISH_SOUND));

        if (QuestEntityAPIConfig.get().finish_quest_fireworks_enabled) {
            spawnClaimFirework(player);
        }
    }

    private static void playQuestSound(ServerPlayer player, ResourceLocation soundId) {
        Holder<SoundEvent> soundHolder = Holder.direct(SoundEvent.createVariableRangeEvent(soundId));
        ((ServerLevel) player.level()).playSound(null, player.blockPosition(), soundHolder.value(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void spawnClaimFirework(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        DyeColor[] colors = DyeColor.values();

        int explosionCount = 1 + random.nextInt(2);
        List<FireworkExplosion> explosions = new ArrayList<>(explosionCount);
        for (int i = 0; i < explosionCount; i++) {
            int color = colors[random.nextInt(colors.length)].getFireworkColor();
            explosions.add(new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, IntList.of(color), IntList.of(), false, false));
        }

        ItemStack fireworkStack = new ItemStack(Items.FIREWORK_ROCKET);
        fireworkStack.set(DataComponents.FIREWORKS, new Fireworks(0, explosions));

        FireworkRocketEntity firework = new FireworkRocketEntity(level, player.getX(), player.getY(), player.getZ(), fireworkStack);
        level.addFreshEntity(firework);
    }

    // Grants a one-time treasure map for any find_structure/entity_kill task with provides_map
    // set, from the player's current position. Call once, on accept. Never re-grants for the
    // same quest+task, even across decline/re-accept - see PlayerQuestData.hasMapBeenGranted,
    // which is deliberately not cleared with the rest of a player's per-entity progress.
    public static void grantStructureMapIfNeeded(ServerPlayer player, Quest quest) {
        ServerLevel level = player.serverLevel();
        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
        boolean dataChanged = false;

        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestTask task = quest.tasks().get(i);
            ResourceLocation structureId = null;
            boolean providesMap = false;
            int maxDistance = com.qeapi.util.StructureDistanceUtil.DEFAULT_MAX_DISTANCE;

            if (task instanceof FindStructureTask findTask) {
                structureId = findTask.structureId();
                providesMap = findTask.providesMap();
                maxDistance = findTask.maxDistance();
            } else if (task instanceof EntityKillTask killTask && killTask.inStructure().isPresent()) {
                structureId = killTask.inStructure().get();
                providesMap = killTask.providesMap();
            }

            if (!providesMap || structureId == null) continue;

            String taskKey = quest.id() + "#" + i;
            if (playerData.hasMapBeenGranted(taskKey)) continue;

            Optional<ItemStack> mapStack = StructureMapUtil.createMapToStructure(
                    level, player.blockPosition(), structureId, maxDistance);
            if (mapStack.isEmpty()) continue;

            if (!player.getInventory().add(mapStack.get())) {
                player.drop(mapStack.get(), false);
            }
            playerData.markMapGranted(taskKey);
            dataChanged = true;

            QuestEntityAPI.LOGGER.debug("Granted structure map ({}) to player {} for quest {} task {}",
                    structureId, player.getName().getString(), quest.id(), i);
        }

        if (dataChanged) {
            QuestEntityAccess.setPlayerData(player, playerData);
        }
    }

    // same 64-block radius entity_kill's nearby-quest-entity scan uses
    private static final double DELIVERY_TARGET_SEARCH_RADIUS = 64.0;

    // Resolves deliver_item's target_entity_id/tag/ids selector to exactly one concrete entity -
    // the nearest match around the giver's own position - and commits to it for the rest of this
    // quest's lifetime. Call once, when the quest is accepted, same as grantStructureMapIfNeeded.
    // Only the first deliver_item task in a quest gets resolved; a quest isn't expected to need more
    // than one delivery target per giver.
    public static void resolveDeliveryTargetIfNeeded(ServerPlayer player, Entity giver, Quest quest) {
        if (!(giver.level() instanceof ServerLevel level)) return;

        for (QuestTask task : quest.tasks()) {
            if (!(task instanceof DeliverItemTask deliverTask)) continue;

            AABB searchBox = AABB.ofSize(giver.position(),
                    DELIVERY_TARGET_SEARCH_RADIUS * 2, DELIVERY_TARGET_SEARCH_RADIUS * 2, DELIVERY_TARGET_SEARCH_RADIUS * 2);
            Entity nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (Entity candidate : level.getEntities(giver, searchBox, deliverTask::matchesTargetSelector)) {
                double distSq = candidate.position().distanceToSqr(giver.position());
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = candidate;
                }
            }

            if (nearest == null) {
                QuestEntityAPI.LOGGER.warn("No deliver_item target found near {} for quest {}", giver.getUUID(), quest.id());
                return;
            }

            PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
            playerData.recordDeliveryTarget(giver.getUUID(), nearest.getUUID());
            QuestEntityAccess.setPlayerData(player, playerData);

            // protect it the same way accepting a quest protects the giver - if it despawns
            // mid-quest the player is stranded with nothing to deliver to
            if (nearest instanceof net.minecraft.world.entity.Mob mob) {
                mob.setPersistenceRequired();
            }

            QuestEntityAPI.LOGGER.debug("Resolved deliver_item target {} for player {} on quest {}",
                    nearest.getUUID(), player.getName().getString(), quest.id());
            return;
        }
    }

    // True if a valid deliver_item target already exists within range of the giver - used to gate a
    // deliver quest from being offered when there's nothing nearby to deliver to (see the offering filter).
    public static boolean hasDeliveryTargetNearby(Entity giver, DeliverItemTask deliverTask) {
        if (!(giver.level() instanceof ServerLevel level)) return false;
        AABB searchBox = AABB.ofSize(giver.position(),
                DELIVERY_TARGET_SEARCH_RADIUS * 2, DELIVERY_TARGET_SEARCH_RADIUS * 2, DELIVERY_TARGET_SEARCH_RADIUS * 2);
        return !level.getEntities(giver, searchBox, deliverTask::matchesTargetSelector).isEmpty();
    }

    // Checks whether targetEntity is the resolved deliver_item target for any of the player's active
    // quests, and if so, whether they're currently holding enough of the required item - marks that
    // task's progress complete on match. Returns true if a delivery happened, so callers can decide
    // whether to consume/cancel the interaction.
    public static boolean tryDeliverItem(ServerPlayer player, Entity targetEntity) {
        UUID targetUuid = targetEntity.getUUID();
        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
        boolean delivered = false;

        for (Map.Entry<UUID, QuestProgress> entry : new HashMap<>(playerData.getAllProgress()).entrySet()) {
            UUID giverUuid = entry.getKey();
            Optional<UUID> resolvedTarget = playerData.getDeliveryTarget(giverUuid);
            if (resolvedTarget.isEmpty() || !resolvedTarget.get().equals(targetUuid)) continue;

            QuestProgress progress = entry.getValue();
            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;
            Quest quest = questOpt.get();

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (!(task instanceof DeliverItemTask deliverTask)) continue;
                if (progress.getTaskProgress(i) >= deliverTask.amount()) continue;

                int have = deliverTask.countMatchingItems(player.getInventory().items);
                if (have < deliverTask.amount()) continue;

                progress.setTaskProgress(i, deliverTask.amount());
                delivered = true;

                QuestEntityAccess.setPlayerData(player, playerData);
                updateEntityComponent(player, giverUuid, progress);
                syncProgressToClient(player, giverUuid, progress);
                checkQuestCompletion(player, giverUuid, quest, progress);

                if (deliveryClearedHandler != null) {
                    deliveryClearedHandler.accept(player, targetEntity);
                }

                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.qe_api.item_delivered", deliverTask.getItemDisplayName()));

                QuestEntityAPI.LOGGER.debug("Player {} delivered {}x {} to {} for quest {}",
                        player.getName().getString(), deliverTask.amount(), deliverTask.getItemDisplayName(),
                        targetUuid, quest.id());
            }
        }

        return delivered;
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
                    if (killTask.matches(killed, source, killed.level(), player)) {
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
                } else if (task instanceof HarvestCropsTask harvestTask) {
                    if (harvestTask.matches(minedState)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} harvested {} for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.BLOCK.getKey(minedState.getBlock()),
                                progress.getTaskProgress(i), harvestTask.amount());
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

    public static void onFishCaught(ServerPlayer player, Collection<ItemStack> caughtItems) {
        if (caughtItems.isEmpty()) return;

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
                if (task instanceof FishingTask fishingTask) {
                    for (ItemStack caught : caughtItems) {
                        if (fishingTask.matches(caught)) {
                            progress.incrementTaskProgress(i);
                            updated = true;

                            QuestEntityAPI.LOGGER.debug("Player {} caught {} for quest task ({}/{})",
                                    player.getName().getString(),
                                    BuiltInRegistries.ITEM.getKey(caught.getItem()),
                                    progress.getTaskProgress(i), fishingTask.amount());
                        }
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

    public static void onAnvilUsed(ServerPlayer player, ItemStack before, ItemStack after) {
        if (after.isEmpty()) return;

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
                if (task instanceof AnvilTask anvilTask) {
                    if (anvilTask.matches(before, after)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} repaired {} at an anvil for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.ITEM.getKey(after.getItem()),
                                progress.getTaskProgress(i), anvilTask.amount());
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

    public static void onItemSmithed(ServerPlayer player, ItemStack result) {
        if (result.isEmpty()) return;

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
                if (task instanceof SmithingTask smithingTask) {
                    if (smithingTask.matches(result)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} smithed {} for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.ITEM.getKey(result.getItem()),
                                progress.getTaskProgress(i), smithingTask.amount());
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

    public static void onItemCrafted(ServerPlayer player, ItemStack result) {
        if (result.isEmpty()) return;

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
                if (task instanceof CraftingTask craftingTask) {
                    if (craftingTask.matches(result)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} crafted {} for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.ITEM.getKey(result.getItem()),
                                progress.getTaskProgress(i), craftingTask.amount());
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

    public static void onItemEnchanted(ServerPlayer player, ItemStack enchantedItem) {
        if (enchantedItem.isEmpty()) return;

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
                if (task instanceof EnchantingTask enchantingTask) {
                    if (enchantingTask.matches(player.serverLevel(), enchantedItem)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} enchanted {} for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.ITEM.getKey(enchantedItem.getItem()),
                                progress.getTaskProgress(i), enchantingTask.amount());
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

    // Spell Engine integration - fires for every individual spell bind, and also progresses
    // SpellPoolCompleteTask when this particular bind is the one that completed the pool
    public static void onSpellBound(ServerPlayer player, ResourceLocation spellPoolId, boolean isComplete) {
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
                if (task instanceof SpellBindTask spellBindTask) {
                    if (spellBindTask.matches(spellPoolId)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} bound a spell (pool {}) for quest task ({}/{})",
                                player.getName().getString(), spellPoolId,
                                progress.getTaskProgress(i), spellBindTask.amount());
                    }
                } else if (isComplete && task instanceof SpellPoolCompleteTask poolCompleteTask) {
                    if (poolCompleteTask.matches(spellPoolId)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} completed spell pool {} for quest task ({}/{})",
                                player.getName().getString(), spellPoolId,
                                progress.getTaskProgress(i), poolCompleteTask.amount());
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

    // Spell Engine integration - fires when a pre-made spellbook is created directly for a pool,
    // the other path (besides onSpellBound's isComplete flag) that ends with a fully bound book
    public static void onSpellPoolCompleted(ServerPlayer player, ResourceLocation spellPoolId) {
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
                if (task instanceof SpellPoolCompleteTask poolCompleteTask) {
                    if (poolCompleteTask.matches(spellPoolId)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} created a spellbook for pool {} for quest task ({}/{})",
                                player.getName().getString(), spellPoolId,
                                progress.getTaskProgress(i), poolCompleteTask.amount());
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
                    if (findTask.structureId().equals(structureId)
                            && findTask.matchesPowerLevel(player.serverLevel(), player.blockPosition())) {
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

    public static void onEffectApplied(LivingEntity target, MobEffectInstance instance, Entity source) {
        if (target.level().isClientSide) return;
        if (!(source instanceof ServerPlayer player)) return;

        boolean isSelf = target == source;

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
                if (task instanceof ApplyStatusEffectTask effectTask && effectTask.matches(instance, isSelf)) {
                    progress.incrementTaskProgress(i);
                    updated = true;

                    QuestEntityAPI.LOGGER.debug("Player {} applied effect {} for quest task ({}/{})",
                            player.getName().getString(), BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()),
                            progress.getTaskProgress(i), effectTask.amount());
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

    public static void onDamageDealt(LivingEntity target, DamageSource source, float amount) {
        if (target.level().isClientSide) return;
        if (amount <= 0) return;

        ServerPlayer player = null;
        if (source.getEntity() instanceof ServerPlayer direct) {
            player = direct;
        } else if (source.getDirectEntity() instanceof ServerPlayer indirect) {
            player = indirect;
        }
        if (player == null) return;

        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
        Level level = target.level();

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof DealDamageAmountTask damageTask
                        && damageTask.matches(target, source, level, player)) {
                    progress.addTaskProgress(i, Math.round(amount));
                    updated = true;

                    QuestEntityAPI.LOGGER.debug("Player {} dealt {} damage for quest task ({}/{})",
                            player.getName().getString(), amount, progress.getTaskProgress(i), damageTask.getTargetAmount());
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

    // same "nearby player" radius convention onRaidComplete/onTrialSpawnerComplete use for
    // crediting without a direct source entity
    private static final double HEAL_ATTRIBUTION_RANGE = 32.0;

    public static void onEntityHealed(LivingEntity healed, float amount) {
        if (healed.level().isClientSide) return;
        if (amount <= 0) return;
        if (!(healed.level() instanceof ServerLevel level)) return;

        if (healed instanceof ServerPlayer selfHealer) {
            creditHealingAmount(selfHealer, healed, amount, true, Optional.empty(), level);
        }

        if (!SpellEngineCompat.isLoaded()) return;

        long currentTick = level.getGameTime();
        AABB searchBox = AABB.ofSize(healed.position(),
                HEAL_ATTRIBUTION_RANGE * 2, HEAL_ATTRIBUTION_RANGE * 2, HEAL_ATTRIBUTION_RANGE * 2);
        for (ServerPlayer candidate : level.getEntitiesOfClass(ServerPlayer.class, searchBox)) {
            if (candidate == healed) continue; // self-healing already credited unconditionally above
            Optional<ResourceLocation> recentSpell = SpellEngineCompat.recentCastSpellId(candidate.getUUID(), currentTick);
            if (recentSpell.isEmpty()) continue;
            creditHealingAmount(candidate, healed, amount, false, recentSpell, level);
        }
    }

    private static void creditHealingAmount(ServerPlayer player, LivingEntity healed, float amount, boolean isSelf,
                                             Optional<ResourceLocation> castSpellId, ServerLevel level) {
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
                if (task instanceof DoHealingAmountTask healTask
                        && healTask.matches(healed, isSelf, castSpellId, level)) {
                    progress.addTaskProgress(i, Math.round(amount));
                    updated = true;

                    QuestEntityAPI.LOGGER.debug("Player {} healed {} for quest task ({}/{})",
                            player.getName().getString(), amount, progress.getTaskProgress(i), healTask.getTargetAmount());
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

    public static void onBiomeEntered(ServerPlayer player, ResourceLocation biomeId) {
        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            UUID entityUuid = entry.getKey();
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean updated = false;

            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestTask task = quest.tasks().get(i);
                if (task instanceof VisitBiomeTask visitTask) {
                    Optional<ResourceLocation> matched = visitTask.matchedBiomeAt(level, pos);
                    if (matched.isPresent() && playerData.recordVisitedBiome(entityUuid, i, matched.get())) {
                        int distinctCount = playerData.getVisitedBiomes(entityUuid, i).size();
                        progress.setTaskProgress(i, Math.min(distinctCount, visitTask.amount()));
                        updated = true;

                        QuestEntityAPI.LOGGER.debug("Player {} visited new biome {} for quest task ({}/{})",
                                player.getName().getString(), matched.get(), distinctCount, visitTask.amount());
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

    // same ~32-block radius already used elsewhere in this file/the loader classes for
    // "nearby player" crediting (team kill-sharing, environmental kills, proximity sync)
    private static final double RAID_TRIAL_CREDIT_RANGE = 32.0;

    // a raid has no single "killer" to credit, so every online player near its center with a
    // matching active quest gets progress - see RaidTickMixin
    public static void onRaidComplete(ServerLevel level, net.minecraft.core.BlockPos center, int raidLevel) {
        AABB searchBox = AABB.ofSize(net.minecraft.world.phys.Vec3.atCenterOf(center),
                RAID_TRIAL_CREDIT_RANGE * 2, RAID_TRIAL_CREDIT_RANGE * 2, RAID_TRIAL_CREDIT_RANGE * 2);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, searchBox)) {
            creditRaidComplete(player, raidLevel);
        }
    }

    private static void creditRaidComplete(ServerPlayer player, int raidLevel) {
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
                if (task instanceof RaidCompleteTask raidTask && raidTask.matches(raidLevel)) {
                    progress.incrementTaskProgress(i);
                    updated = true;

                    QuestEntityAPI.LOGGER.debug("Player {} credited for raid completion (level {}) for quest task ({}/{})",
                            player.getName().getString(), raidLevel, progress.getTaskProgress(i), raidTask.amount());
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

    // same "no single killer" crediting convention as onRaidComplete, radius centered on the spawner block
    public static void onTrialSpawnerComplete(ServerLevel level, net.minecraft.core.BlockPos pos, boolean isOminous) {
        AABB searchBox = AABB.ofSize(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                RAID_TRIAL_CREDIT_RANGE * 2, RAID_TRIAL_CREDIT_RANGE * 2, RAID_TRIAL_CREDIT_RANGE * 2);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, searchBox)) {
            creditTrialSpawnerComplete(player, isOminous);
        }
    }

    private static void creditTrialSpawnerComplete(ServerPlayer player, boolean isOminous) {
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
                if (task instanceof TrialSpawnerCompleteTask trialTask && trialTask.matches(isOminous)) {
                    progress.incrementTaskProgress(i);
                    updated = true;

                    QuestEntityAPI.LOGGER.debug("Player {} credited for trial spawner completion (ominous={}) for quest task ({}/{})",
                            player.getName().getString(), isOminous, progress.getTaskProgress(i), trialTask.amount());
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

    public static void checkQuestCompletion(ServerPlayer player, UUID entityUuid,
                                              Quest quest, QuestProgress progress) {
        if (quest.isComplete(progress)) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.qe_api.quest_complete",
                            quest.getDisplayName()
                    )
            );
            QuestEntityAPI.LOGGER.debug("Player {} completed quest {}",
                    player.getName().getString(), quest.id());
        }
    }

    public static void updateEntityComponent(ServerPlayer player, UUID entityUuid, QuestProgress progress) {
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

        QuestEntityAPI.LOGGER.debug("Updated entity {} quest component - task progress: {}",
                entityUuid, progress.getAllTaskProgress());
    }

    public static void syncProgressToClient(ServerPlayer player, UUID entityUuid, QuestProgress progress) {
        if (progressSyncHandler != null) {
            progressSyncHandler.accept(player, entityUuid);
        }
    }
}
