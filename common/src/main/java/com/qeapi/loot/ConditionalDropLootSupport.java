package com.qeapi.loot;

import com.qeapi.QuestAPI;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.data.QuestManager;
import com.qeapi.event.QuestEventHandler;
import com.qeapi.item.QuestItems;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.task.ConditionalDropTask;
import com.qeapi.quest.task.QuestTask;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// appended to every loot table in the game, so resolve() must stay cheap for rolls that have nothing to do with a loaded quest - anyChestTargetingQuestLoaded caches that check
public final class ConditionalDropLootSupport {

    private ConditionalDropLootSupport() {}

    private static Boolean anyChestTargetingQuestLoaded = null;

    // called from QuestLoader.apply() after QuestManager.clear() so the cache reflects the reload
    public static void invalidateCache() {
        anyChestTargetingQuestLoaded = null;
    }

    private static boolean isAnyChestTargetingQuestLoaded() {
        if (anyChestTargetingQuestLoaded == null) {
            anyChestTargetingQuestLoaded = computeAnyChestTargetingQuestLoaded();
        }
        return anyChestTargetingQuestLoaded;
    }

    private static boolean computeAnyChestTargetingQuestLoaded() {
        for (Quest quest : QuestManager.getAllQuests()) {
            for (QuestTask task : quest.tasks()) {
                if (task instanceof ConditionalDropTask dropTask
                        && (!dropTask.chestLootFilters().lootTableIds().isEmpty() || !dropTask.chestLootFilters().chestInStructures().isEmpty())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static LootPool.Builder buildPoolBuilder(ResourceLocation tableId) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(QuestItems.QUEST_ITEM).apply(() -> conditionalDropFunction(tableId)));
    }

    private static LootItemFunction conditionalDropFunction(ResourceLocation tableId) {
        return new LootItemFunction() {
            @Override
            public ItemStack apply(ItemStack stack, LootContext context) {
                return resolve(tableId, context);
            }

            @Override
            public LootItemFunctionType<?> getType() {
                return null;
            }
        };
    }

    // checked in order: the opening player, then same-dimension players, then everyone else - first match wins
    private static ItemStack resolve(ResourceLocation tableId, LootContext context) {
        if (!isAnyChestTargetingQuestLoaded()) {
            return ItemStack.EMPTY;
        }

        if (!(context.getLevel() instanceof ServerLevel level)) {
            return ItemStack.EMPTY;
        }

        for (ServerPlayer player : orderedCandidates(context, level)) {
            ItemStack granted = tryGrant(tableId, player, level, context);
            if (!granted.isEmpty()) {
                return granted;
            }
        }

        return ItemStack.EMPTY;
    }

    // null if this loot context has no origin, e.g. fishing/entity-drop tables
    private static BlockPos resolveOriginPos(LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        return origin != null ? BlockPos.containing(origin) : null;
    }

    private static List<ServerPlayer> orderedCandidates(LootContext context, ServerLevel level) {
        LinkedHashSet<ServerPlayer> candidates = new LinkedHashSet<>();
        Entity contextEntity = context.hasParam(LootContextParams.THIS_ENTITY)
                ? context.getParam(LootContextParams.THIS_ENTITY) : null;
        if (contextEntity instanceof ServerPlayer contextPlayer) {
            candidates.add(contextPlayer);
        } else if (contextEntity instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer ownerPlayer) {
            // fishing/arrow tables carry the hook/projectile as THIS_ENTITY, not the player - credit its owner
            candidates.add(ownerPlayer);
        }
        // no player context on this roll - prefer same-dimension players before widening server-wide
        candidates.addAll(level.players());
        candidates.addAll(level.getServer().getPlayerList().getPlayers());
        return List.copyOf(candidates);
    }

    private static ItemStack tryGrant(ResourceLocation tableId, ServerPlayer player, ServerLevel level, LootContext context) {
        PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);

        for (Map.Entry<UUID, QuestProgress> entry : playerData.getAllProgress().entrySet()) {
            QuestProgress progress = entry.getValue();

            Optional<Quest> questOpt = QuestManager.getQuest(progress.getQuestId());
            if (questOpt.isEmpty()) continue;
            Quest quest = questOpt.get();

            for (int i = 0; i < quest.tasks().size(); i++) {
                if (!(quest.tasks().get(i) instanceof ConditionalDropTask dropTask)) continue;

                List<ResourceLocation> lootTableIds = dropTask.chestLootFilters().lootTableIds();
                if (lootTableIds.isEmpty() && dropTask.chestLootFilters().chestInStructures().isEmpty()) continue;
                if (!lootTableIds.isEmpty() && !lootTableIds.contains(tableId)) continue;

                // only resolve the origin/run a structure lookup for tasks that actually need one
                BlockPos originPos = !dropTask.chestLootFilters().chestInStructures().isEmpty() ? resolveOriginPos(context) : null;
                if (!dropTask.matchesChestLoot(tableId, level, originPos)) continue;

                int current = progress.getTaskProgress(i);
                // self-heal against a lost item - forgets credit the player no longer holds, so the cap can't permanently lock them out
                int held = dropTask.countMatchingItems(player.getInventory().items);
                if (held < current) {
                    current = held;
                    progress.setTaskProgress(i, current);
                }
                if (current >= dropTask.amount()) continue;
                if (!quest.isTaskUnlocked(progress, i)) continue;
                if (context.getRandom().nextDouble() >= dropTask.chestLootFilters().dropChance()) continue;

                progress.incrementTaskProgress(i);
                QuestEntityAccess.setPlayerData(player, playerData);
                // same persist/sync/complete chain every normal task runs
                QuestEventHandler.updateEntityComponent(player, entry.getKey(), progress);
                QuestEventHandler.syncProgressToClient(player, entry.getKey(), progress);
                QuestEventHandler.checkQuestCompletion(player, entry.getKey(), quest, progress);

                QuestAPI.LOGGER.debug("Player {} got a conditional loot drop from loot table {} for quest {} task {}",
                        player.getName().getString(), tableId, quest.id(), i);

                return dropTask.createGrantStack(1);
            }
        }

        return ItemStack.EMPTY;
    }
}
