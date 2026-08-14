package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.QuestProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public sealed interface QuestTask permits
        EntityKillTask,
        FindStructureTask,
        BringItemTask,
        BlocksTraveledTask,
        ItemUsedTask,
        BrewPotionTask,
        SpellCastTask,
        MineBlockTask,
        FishingTask,
        HarvestCropsTask,
        AnvilTask,
        SmithingTask,
        CraftingTask,
        EnchantingTask,
        SpellBindTask,
        SpellPoolCompleteTask,
        ConditionalDropTask,
        RaidCompleteTask,
        TrialSpawnerCompleteTask,
        DeliverItemTask,
        ApplyStatusEffectTask,
        DealDamageAmountTask,
        DoHealingAmountTask,
        VisitBiomeTask,
        QuestLineChoiceTask {

    Map<ResourceLocation, TaskType<?>> TASK_TYPES = new HashMap<>();

    Codec<QuestTask> CODEC = Codec.lazyInitialized(() ->
            ResourceLocation.CODEC.dispatch(
                    "task",
                    QuestTask::getTypeId,
                    id -> {
                        TaskType<?> type = TASK_TYPES.get(id);
                        if (type == null) {
                            throw new IllegalArgumentException("Unknown task type: " + id);
                        }
                        return type.mapCodec();
                    }
            )
    );

    ResourceLocation getTypeId();

    Component getDisplayText(QuestProgress progress, int taskIndex);

    String getDefaultTranslationKey();

    // every built-in task is complete once progress reaches its target amount
    default boolean isComplete(QuestProgress progress, int taskIndex) {
        return progress.getTaskProgress(taskIndex) >= getTargetAmount();
    }

    int getTargetAmount();

    // Extra {placeholder} values a task feeds into its quest's description text (e.g. {min_range}).
    // Quest.getDescription merges these as both {key} (from the first task) and {key_<index>} per task.
    default Map<String, String> getDescriptionValues() {
        return Map.of();
    }

    default Optional<ResourceLocation> getDisplayTexture() {
        return Optional.empty();
    }

    // author-set override that takes priority over this task's own icon logic in the quest GUI,
    // regardless of what that logic would otherwise show (live item/entity render, bundled
    // texture, or generic fallback) - see QuestScreen.renderTextureOverride
    Optional<ResourceLocation> textureOverrideId();

    static <T extends QuestTask> void registerType(ResourceLocation id, MapCodec<T> codec) {
        TASK_TYPES.put(id, new TaskType<>(id, codec));
    }

    static void registerBuiltInTypes() {
        registerType(QuestEntityAPI.id("entity_kill"), EntityKillTask.CODEC); // kill matching mobs - filterable by type/tag, damage type, location, spell attribution, power level
        registerType(QuestEntityAPI.id("find_structure"), FindStructureTask.CODEC); // locate the nearest instance of a structure
        registerType(QuestEntityAPI.id("bring_item"), BringItemTask.CODEC); // turn in an item (or inline quest item), consumed on claim
        registerType(QuestEntityAPI.id("blocks_traveled"), BlocksTraveledTask.CODEC); // travel a set distance
        registerType(QuestEntityAPI.id("item_used"), ItemUsedTask.CODEC); // right-click/use a specific item a number of times
        registerType(QuestEntityAPI.id("brew_potion"), BrewPotionTask.CODEC); // brew a specific potion in a brewing stand
        registerType(QuestEntityAPI.id("spell_cast"), SpellCastTask.CODEC); // cast a spell matching an optional spell/pool/school filter
        registerType(QuestEntityAPI.id("mine_block"), MineBlockTask.CODEC); // mine a specific block or one from a block tag
        registerType(QuestEntityAPI.id("fishing"), FishingTask.CODEC); // catch fish with a fishing rod
        registerType(QuestEntityAPI.id("harvest_crops"), HarvestCropsTask.CODEC); // break a fully-grown crop
        registerType(QuestEntityAPI.id("anvil_repair"), AnvilTask.CODEC); // repair an item's durability at an anvil
        registerType(QuestEntityAPI.id("smithing"), SmithingTask.CODEC); // produce a result at a smithing table (upgrades, trims, custom recipes)
        registerType(QuestEntityAPI.id("crafting"), CraftingTask.CODEC); // craft a specific item or one matching a tag
        registerType(QuestEntityAPI.id("enchanting"), EnchantingTask.CODEC); // enchant an item at the enchanting table
        registerType(QuestEntityAPI.id("spell_bind"), SpellBindTask.CODEC); // bind a spell to a spellbook at the Spell Binding Table
        registerType(QuestEntityAPI.id("spell_pool_complete"), SpellPoolCompleteTask.CODEC); // finish binding every spell in a pool, or create a pre-made book for it
        registerType(QuestEntityAPI.id("conditional_drop"), ConditionalDropTask.CODEC); // grant an item on a matching mob kill and/or matching chest-loot roll
        registerType(QuestEntityAPI.id("raid_complete"), RaidCompleteTask.CODEC); // win a raid, optionally at a minimum raid omen level
        registerType(QuestEntityAPI.id("trial_spawner_complete"), TrialSpawnerCompleteTask.CODEC); // clear a trial spawner's wave, optionally requiring/excluding ominous
        registerType(QuestEntityAPI.id("deliver_item"), DeliverItemTask.CODEC); // hand an item to a specific resolved NPC, resolved from a type/tag selector at accept time
        registerType(QuestEntityAPI.id("apply_status_effect"), ApplyStatusEffectTask.CODEC); // apply a status effect to yourself or another entity a number of times
        registerType(QuestEntityAPI.id("deal_damage_amount"), DealDamageAmountTask.CODEC); // deal a total amount of damage to matching entities
        registerType(QuestEntityAPI.id("do_healing_amount"), DoHealingAmountTask.CODEC); // heal a total amount, self or (best-effort, Spell Engine only) others
        registerType(QuestEntityAPI.id("visit_biome"), VisitBiomeTask.CODEC); // visit a number of distinct matching biomes
        registerType(QuestEntityAPI.id("quest_line_choice"), QuestLineChoiceTask.CODEC); // pick one of several quest lines - the sole task on a quest-line root quest
    }

    record TaskType<T extends QuestTask>(ResourceLocation id, MapCodec<T> codec) {
        @SuppressWarnings("unchecked")
        public MapCodec<QuestTask> mapCodec() {
            return (MapCodec<QuestTask>) (MapCodec<?>) codec;
        }
    }
}
