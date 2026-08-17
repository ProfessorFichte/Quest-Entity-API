package com.qeapi.event;

import com.qeapi.QuestAPI;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.config.QuestAPIConfig;
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

    // re-triggers the nearby-entity sync, which otherwise only fires once per entity per player on first proximity detection
    private static BiConsumer<ServerPlayer, UUID> progressSyncHandler;

    public static void setProgressSyncHandler(BiConsumer<ServerPlayer, UUID> handler) {
        progressSyncHandler = handler;
    }

    // sends an explicit clear so the floating item icon disappears immediately instead of lingering until the target drops out of range
    private static java.util.function.BiConsumer<ServerPlayer, Entity> deliveryClearedHandler;

    public static void setDeliveryClearedHandler(java.util.function.BiConsumer<ServerPlayer, Entity> handler) {
        deliveryClearedHandler = handler;
    }

    // setVillagerXp is a plain field setter - the level-up check is private and normally only runs off a deferred 40-tick trade timer, so VillagerXpAccessor replicates it here to apply immediately
    public static void grantVillagerTradeXp(Entity entity, int tier) {
        if (!(entity instanceof Villager villager)) return;
        if (!QuestAPIConfig.get().villager_trade_xp_enabled) return;

        int xpAmount = tier * QuestAPIConfig.get().villager_trade_xp_per_tier;
        villager.setVillagerXp(villager.getVillagerXp() + xpAmount);

        VillagerXpAccessor accessor = (VillagerXpAccessor) villager;
        if (accessor.quest_api$shouldIncreaseLevel()) {
            accessor.quest_api$increaseMerchantCareer();
        }

        QuestAPI.LOGGER.debug("Granted {} trade XP (tier {}) to villager {}",
                xpAmount, tier, villager.getUUID());
    }

    // resolved via a raw variable-range SoundEvent, not a registry lookup, so a resource-pack-only sound with no Java registration still works
    // priority lowest to highest: config default -> EntityQuestAssignment override -> the quest's own override; component may be null
    public static void playAcceptSound(ServerPlayer player, Quest quest, EntityQuestComponent component) {
        ResourceLocation configDefault = ResourceLocation.parse(QuestAPIConfig.get().accept_quest_sound);
        Optional<ResourceLocation> assignmentOverride = component != null ? component.acceptQuestSoundOverride() : Optional.empty();
        playQuestSound(player, quest.acceptQuestSoundOverride().or(() -> assignmentOverride).orElse(configDefault));
    }

    public static void playClaimEffects(ServerPlayer player, Quest quest, EntityQuestComponent component) {
        ResourceLocation configDefault = ResourceLocation.parse(QuestAPIConfig.get().finish_quest_sound);
        Optional<ResourceLocation> assignmentOverride = component != null ? component.finishQuestSoundOverride() : Optional.empty();
        playQuestSound(player, quest.finishQuestSoundOverride().or(() -> assignmentOverride).orElse(configDefault));

        if (QuestAPIConfig.get().finish_quest_fireworks_enabled) {
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
        // purely decorative - a real firework entity otherwise deals proximity explosion damage on detonation
        com.qeapi.util.NoDamageFireworks.mark(firework);
        level.addFreshEntity(firework);
    }

    // never re-grants for the same quest+task, even across decline/re-accept - hasMapBeenGranted is deliberately not cleared with the rest of a player's per-entity progress
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
                maxDistance = findTask.filters().maxDistance();
            } else if (task instanceof EntityKillTask killTask && killTask.filters().inStructure().isPresent()) {
                structureId = killTask.filters().inStructure().get();
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

            QuestAPI.LOGGER.debug("Granted structure map ({}) to player {} for quest {} task {}",
                    structureId, player.getName().getString(), quest.id(), i);
        }

        if (dataChanged) {
            QuestEntityAccess.setPlayerData(player, playerData);
        }
    }

    // same 64-block radius entity_kill's nearby-quest-entity scan uses
    private static final double DELIVERY_TARGET_SEARCH_RADIUS = 64.0;

    // resolves to the nearest match and commits to it for the quest's lifetime; only the first deliver_item task in a quest gets resolved
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
                QuestAPI.LOGGER.warn("No deliver_item target found near {} for quest {}", giver.getUUID(), quest.id());
                return;
            }

            PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
            playerData.recordDeliveryTarget(giver.getUUID(), nearest.getUUID());
            QuestEntityAccess.setPlayerData(player, playerData);

            // protect it the same way accepting a quest protects the giver, or a mid-quest despawn strands the player
            if (nearest instanceof net.minecraft.world.entity.Mob mob) {
                mob.setPersistenceRequired();
            }

            QuestAPI.LOGGER.debug("Resolved deliver_item target {} for player {} on quest {}",
                    nearest.getUUID(), player.getName().getString(), quest.id());
            return;
        }
    }

    // gates a deliver quest from being offered when there's nothing nearby to deliver to
    public static boolean hasDeliveryTargetNearby(Entity giver, DeliverItemTask deliverTask) {
        if (!(giver.level() instanceof ServerLevel level)) return false;
        AABB searchBox = AABB.ofSize(giver.position(),
                DELIVERY_TARGET_SEARCH_RADIUS * 2, DELIVERY_TARGET_SEARCH_RADIUS * 2, DELIVERY_TARGET_SEARCH_RADIUS * 2);
        return !level.getEntities(giver, searchBox, deliverTask::matchesTargetSelector).isEmpty();
    }

    // returns true if a delivery happened, so callers can decide whether to consume/cancel the interaction
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
                if (!quest.isTaskUnlocked(progress, i)) continue;

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
                        "message.quest_api.item_delivered", deliverTask.getItemDisplayName()));

                QuestAPI.LOGGER.debug("Player {} delivered {}x {} to {} for quest {}",
                        player.getName().getString(), deliverTask.amount(), deliverTask.getItemDisplayName(),
                        targetUuid, quest.id());
            }
        }

        return delivered;
    }

public static void onPlayerMove(ServerPlayer player, Vec3 from, Vec3 to, double accumulatedDistance) {
        if (accumulatedDistance < 0.01) return;

        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
        boolean anyUpdated = false;

        if (playerData.getAllProgress().isEmpty()) {
            QuestAPI.LOGGER.debug("Player {} has no active quests for movement tracking", player.getName().getString());
            return;
        }

        QuestAPI.LOGGER.debug("Player {} has {} active quest(s), checking for BlocksTraveledTask",
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
                if (task instanceof BlocksTraveledTask && quest.isTaskUnlocked(progress, i)) {
                    int previousProgress = progress.getTaskProgress(i);
                    progress.addTaskProgress(i, (int) Math.floor(accumulatedDistance));
                    updated = true;

                    QuestAPI.LOGGER.debug("Player {} traveled {} blocks for quest task ({}/{})",
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
                    if (useTask.matches(stack) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} used {} for quest task ({}/{})",
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
                    if (castTask.matches(player.serverLevel(), spellId) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} cast spell {} for quest task ({}/{})",
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
                    if (brewTask.matches(potionStack) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} brewed a potion for quest task ({}/{})",
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
                    if (mineTask.matches(minedState) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} mined {} for quest task ({}/{})",
                                player.getName().getString(),
                                BuiltInRegistries.BLOCK.getKey(minedState.getBlock()),
                                progress.getTaskProgress(i), mineTask.amount());
                    }
                } else if (task instanceof HarvestCropsTask harvestTask) {
                    if (harvestTask.matches(minedState) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} harvested {} for quest task ({}/{})",
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
                        if (fishingTask.matches(caught) && quest.isTaskUnlocked(progress, i)) {
                            progress.incrementTaskProgress(i);
                            updated = true;

                            QuestAPI.LOGGER.debug("Player {} caught {} for quest task ({}/{})",
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
                    if (anvilTask.matches(before, after) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} repaired {} at an anvil for quest task ({}/{})",
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
                    if (smithingTask.matches(result) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} smithed {} for quest task ({}/{})",
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
                    if (craftingTask.matches(result) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} crafted {} for quest task ({}/{})",
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
                    if (enchantingTask.matches(player.serverLevel(), enchantedItem) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} enchanted {} for quest task ({}/{})",
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

    // Spell Engine integration - fires for every bind, and also progresses SpellPoolCompleteTask when this bind completed the pool
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
                    if (spellBindTask.matches(spellPoolId) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} bound a spell (pool {}) for quest task ({}/{})",
                                player.getName().getString(), spellPoolId,
                                progress.getTaskProgress(i), spellBindTask.amount());
                    }
                } else if (isComplete && task instanceof SpellPoolCompleteTask poolCompleteTask) {
                    if (poolCompleteTask.matches(spellPoolId) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} completed spell pool {} for quest task ({}/{})",
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

    // fires when a pre-made spellbook is created directly - the other path, besides onSpellBound's isComplete flag, that ends with a fully bound book
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
                    if (poolCompleteTask.matches(spellPoolId) && quest.isTaskUnlocked(progress, i)) {
                        progress.incrementTaskProgress(i);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} created a spellbook for pool {} for quest task ({}/{})",
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
                            && findTask.matchesPowerLevel(player.serverLevel(), player.blockPosition())
                            && quest.isTaskUnlocked(progress, i)) {
                        progress.setTaskProgress(i, 1);
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} found structure {} for quest task",
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
                if (task instanceof ApplyStatusEffectTask effectTask && effectTask.matches(instance, isSelf)
                        && quest.isTaskUnlocked(progress, i)) {
                    progress.incrementTaskProgress(i);
                    updated = true;

                    QuestAPI.LOGGER.debug("Player {} applied effect {} for quest task ({}/{})",
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
                        && damageTask.matches(target, source, level, player)
                        && quest.isTaskUnlocked(progress, i)) {
                    progress.addTaskProgress(i, Math.round(amount));
                    updated = true;

                    QuestAPI.LOGGER.debug("Player {} dealt {} damage for quest task ({}/{})",
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

    // same "nearby player" radius convention onRaidComplete/onTrialSpawnerComplete use for crediting without a direct source entity
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
                        && healTask.matches(healed, isSelf, castSpellId, level)
                        && quest.isTaskUnlocked(progress, i)) {
                    progress.addTaskProgress(i, Math.round(amount));
                    updated = true;

                    QuestAPI.LOGGER.debug("Player {} healed {} for quest task ({}/{})",
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
                if (task instanceof VisitBiomeTask visitTask && quest.isTaskUnlocked(progress, i)) {
                    Optional<ResourceLocation> matched = visitTask.matchedBiomeAt(level, pos);
                    if (matched.isPresent() && playerData.recordVisitedBiome(entityUuid, i, matched.get())) {
                        int distinctCount = playerData.getVisitedBiomes(entityUuid, i).size();
                        progress.setTaskProgress(i, Math.min(distinctCount, visitTask.amount()));
                        updated = true;

                        QuestAPI.LOGGER.debug("Player {} visited new biome {} for quest task ({}/{})",
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
                        net.minecraft.network.chat.Component.translatable("message.quest_api.quest_cancelled")
                );

                QuestAPI.LOGGER.info("Quest cancelled for player {} (entity {} removed)",
                        player.getName().getString(), entityUuid);
            }
        }
    }

    // same ~32-block radius already used elsewhere for "nearby player" crediting (team kill-sharing, environmental kills, proximity sync)
    private static final double RAID_TRIAL_CREDIT_RANGE = 32.0;

    // a raid has no single "killer" to credit, so every online player near its center with a matching active quest gets progress
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
                if (task instanceof RaidCompleteTask raidTask && raidTask.matches(raidLevel)
                        && quest.isTaskUnlocked(progress, i)) {
                    progress.incrementTaskProgress(i);
                    updated = true;

                    QuestAPI.LOGGER.debug("Player {} credited for raid completion (level {}) for quest task ({}/{})",
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

    // vanilla ejects one reward per detected player (~30 ticks apart), so without dedup N players clearing one spawner together would each get credited N times
    private static final Map<String, Long> lastTrialSpawnerCredit = new HashMap<>();
    private static final long TRIAL_SPAWNER_CREDIT_WINDOW_TICKS = 400; // >> vanilla's 30-tick ejection gap, << a spawner's re-arm time

    // same "no single killer" crediting convention as onRaidComplete, radius centered on the spawner block
    public static void onTrialSpawnerComplete(ServerLevel level, net.minecraft.core.BlockPos pos, boolean isOminous) {
        String key = level.dimension().location() + "@" + pos;
        long now = level.getGameTime();
        Long last = lastTrialSpawnerCredit.get(key);
        if (last != null && now - last < TRIAL_SPAWNER_CREDIT_WINDOW_TICKS) {
            return;
        }
        lastTrialSpawnerCredit.put(key, now);

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
                if (task instanceof TrialSpawnerCompleteTask trialTask && trialTask.matches(isOminous)
                        && quest.isTaskUnlocked(progress, i)) {
                    progress.incrementTaskProgress(i);
                    updated = true;

                    QuestAPI.LOGGER.debug("Player {} credited for trial spawner completion (ominous={}) for quest task ({}/{})",
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
            Entity questEntity = resolveQuestEntity(player, entityUuid);
            String entityName = com.qeapi.util.EntityNameResolver.resolve(questEntity);
            sendHudMessage(player, net.minecraft.network.chat.Component.translatable(
                    "hud.quest_api.all_tasks_completed", quest.getDisplayName(), entityName));

            QuestAPI.LOGGER.debug("Player {} completed quest {}",
                    player.getName().getString(), quest.id());
        }
    }

    // nearby search first (cheap, covers most calls), falling back to every loaded entity for one that wandered off; null if unloaded
    private static Entity resolveQuestEntity(ServerPlayer player, UUID entityUuid) {
        ServerLevel level = player.serverLevel();

        AABB searchBox = player.getBoundingBox().inflate(128.0);
        List<Entity> entities = level.getEntities(player, searchBox, entity ->
                entity.getUUID().equals(entityUuid)
        );
        if (!entities.isEmpty()) {
            return entities.get(0);
        }

        for (Entity e : level.getAllEntities()) {
            if (e.getUUID().equals(entityUuid)) {
                return e;
            }
        }
        return null;
    }

    // set once at platform init, same pattern as progressSyncHandler/deliveryClearedHandler
    private static BiConsumer<ServerPlayer, net.minecraft.network.chat.Component> hudMessageHandler;

    public static void setHudMessageHandler(BiConsumer<ServerPlayer, net.minecraft.network.chat.Component> handler) {
        hudMessageHandler = handler;
    }

    public static void sendHudMessage(ServerPlayer player, net.minecraft.network.chat.Component message) {
        if (hudMessageHandler != null) {
            hudMessageHandler.accept(player, message);
        }
    }

    public static void updateEntityComponent(ServerPlayer player, UUID entityUuid, QuestProgress progress) {
        Entity questEntity = resolveQuestEntity(player, entityUuid);
        if (questEntity == null) {
            QuestAPI.LOGGER.warn("Quest entity {} not found for progress update - entity may be unloaded", entityUuid);
            return;
        }

        EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(questEntity);
        if (component == null) {
            QuestAPI.LOGGER.warn("Entity {} has no quest component attached", entityUuid);
            return;
        }

        if (!component.hasActiveQuest(player.getUUID())) {
            QuestAPI.LOGGER.warn("Entity {} has no active quest for player {}", entityUuid, player.getName().getString());
            return;
        }

        EntityQuestComponent updated = component.withUpdatedProgress(player.getUUID(), progress);
        QuestEntityAccess.setEntityQuestComponent(questEntity, updated);

        QuestAPI.LOGGER.debug("Updated entity {} quest component - task progress: {}",
                entityUuid, progress.getAllTaskProgress());
    }

    public static void syncProgressToClient(ServerPlayer player, UUID entityUuid, QuestProgress progress) {
        if (progressSyncHandler != null) {
            progressSyncHandler.accept(player, entityUuid);
        }
    }
}
