package com.qeapi.loot;

import com.qeapi.QuestEntityAPI;
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

// Builds the extra loot pool appended to every loaded loot table (see the Fabric/NeoForge
// LootTableEvents.MODIFY / LootTableLoadEvent hooks) that resolves ConditionalDropTask's
// loot_table_ids/chest_in_structures targeting. The pool structure is the same for every table -
// all the actual "does this table/player/task match" work happens inside the function at roll
// time, since a loot table is only ever built once per reload but opened by many different
// players afterward.
//
// Unlike every other task type's event hook, this one is appended to literally every loot table
// in the game (fishing, block drops, mob drops, chests - everything), so resolve() runs on a vast
// number of rolls that have nothing to do with any loaded quest at all. anyChestTargetingQuestLoaded
// caches whether any loaded quest even uses loot_table_ids/chest_in_structures, so resolve() can
// bail out before touching any player data on a server that doesn't use the feature - see
// invalidateCache() for how this stays correct across data reloads.
public final class ConditionalDropLootSupport {

    private ConditionalDropLootSupport() {}

    private static Boolean anyChestTargetingQuestLoaded = null;

    // Called from QuestLoader.apply() right after QuestManager.clear(), so the cache is rebuilt
    // against the newly reloaded quest data the next time resolve() actually needs it.
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
                        && (!dropTask.lootTableIds().isEmpty() || !dropTask.chestInStructures().isEmpty())) {
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

    // Checked in order: the player actually opening the container (if the context carries one),
    // then every other online player, first eligible match wins - see the "no per-open player
    // context" note on ConditionalDropTask's chest-loot targeting.
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

    // Null if this loot context doesn't carry an origin (e.g. fishing/entity-drop tables usually
    // don't) - see ConditionalDropTask.matchesChestLoot for how that's handled.
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

                List<ResourceLocation> lootTableIds = dropTask.lootTableIds();
                if (lootTableIds.isEmpty() && dropTask.chestInStructures().isEmpty()) continue;
                if (!lootTableIds.isEmpty() && !lootTableIds.contains(tableId)) continue;

                // only resolve the origin/run a structure lookup for a task that actually needs
                // one - the vast majority of conditional_drop tasks only ever use loot_table_ids
                BlockPos originPos = !dropTask.chestInStructures().isEmpty() ? resolveOriginPos(context) : null;
                if (!dropTask.matchesChestLoot(tableId, level, originPos)) continue;

                int current = progress.getTaskProgress(i);
                if (current >= dropTask.amount()) continue;
                if (context.getRandom().nextDouble() >= dropTask.chestDropChance()) continue;

                progress.incrementTaskProgress(i);
                QuestEntityAccess.setPlayerData(player, playerData);
                // same persist/sync/complete chain every normal task runs - without this the
                // conditional_drop task's own progress never reaches the GUI or the amount cap
                QuestEventHandler.updateEntityComponent(player, entry.getKey(), progress);
                QuestEventHandler.syncProgressToClient(player, entry.getKey(), progress);
                QuestEventHandler.checkQuestCompletion(player, entry.getKey(), quest, progress);

                QuestEntityAPI.LOGGER.debug("Player {} got a conditional loot drop from loot table {} for quest {} task {}",
                        player.getName().getString(), tableId, quest.id(), i);

                return dropTask.createGrantStack(1);
            }
        }

        return ItemStack.EMPTY;
    }
}
