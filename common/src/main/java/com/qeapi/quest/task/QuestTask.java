package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestAPI;
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

    // Quest.getDescription merges these as both {key} (from the first task) and {key_<index>} per task
    default Map<String, String> getDescriptionValues() {
        return Map.of();
    }

    default Optional<ResourceLocation> getDisplayTexture() {
        return Optional.empty();
    }

    // takes priority over this task's own icon logic regardless of what that would show - see QuestScreen.renderTextureOverride
    Optional<ResourceLocation> textureOverrideId();

    // absent falls back to the task's own index in the quest's tasks list - see Quest.isTaskUnlocked
    Optional<Integer> taskOrder();

    // tasks sharing a choice_group form one "OR" (or "any N of") group - see Quest.effectiveTaskChoiceGroups
    Optional<String> choiceGroup();

    static <T extends QuestTask> void registerType(ResourceLocation id, MapCodec<T> codec) {
        TASK_TYPES.put(id, new TaskType<>(id, codec));
    }

    static void registerBuiltInTypes() {
        registerType(QuestAPI.id("entity_kill"), EntityKillTask.CODEC); // kill matching mobs - filterable by type/tag, damage type, location, spell attribution, power level
        registerType(QuestAPI.id("find_structure"), FindStructureTask.CODEC); // locate the nearest instance of a structure
        registerType(QuestAPI.id("bring_item"), BringItemTask.CODEC); // turn in an item (or inline quest item), consumed on claim
        registerType(QuestAPI.id("blocks_traveled"), BlocksTraveledTask.CODEC); // travel a set distance
        registerType(QuestAPI.id("item_used"), ItemUsedTask.CODEC); // right-click/use a specific item a number of times
        registerType(QuestAPI.id("brew_potion"), BrewPotionTask.CODEC); // brew a specific potion in a brewing stand
        registerType(ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_cast"), SpellCastTask.CODEC); // cast a spell matching an optional spell/pool/school filter
        registerType(QuestAPI.id("mine_block"), MineBlockTask.CODEC); // mine a specific block or one from a block tag
        registerType(QuestAPI.id("fishing"), FishingTask.CODEC); // catch fish with a fishing rod
        registerType(QuestAPI.id("harvest_crops"), HarvestCropsTask.CODEC); // break a fully-grown crop
        registerType(QuestAPI.id("anvil_repair"), AnvilTask.CODEC); // repair an item's durability at an anvil
        registerType(QuestAPI.id("smithing"), SmithingTask.CODEC); // produce a result at a smithing table (upgrades, trims, custom recipes)
        registerType(QuestAPI.id("crafting"), CraftingTask.CODEC); // craft a specific item or one matching a tag
        registerType(QuestAPI.id("enchanting"), EnchantingTask.CODEC); // enchant an item at the enchanting table
        registerType(ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_bind"), SpellBindTask.CODEC); // bind a spell to a spellbook at the Spell Binding Table
        registerType(ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_pool_complete"), SpellPoolCompleteTask.CODEC); // finish binding every spell in a pool, or create a pre-made book for it
        registerType(QuestAPI.id("conditional_drop"), ConditionalDropTask.CODEC); // grant an item on a matching mob kill and/or matching chest-loot roll
        registerType(QuestAPI.id("raid_complete"), RaidCompleteTask.CODEC); // win a raid, optionally at a minimum raid omen level
        registerType(QuestAPI.id("trial_spawner_complete"), TrialSpawnerCompleteTask.CODEC); // clear a trial spawner's wave, optionally requiring/excluding ominous
        registerType(QuestAPI.id("deliver_item"), DeliverItemTask.CODEC); // hand an item to a specific resolved NPC, resolved from a type/tag selector at accept time
        registerType(QuestAPI.id("apply_status_effect"), ApplyStatusEffectTask.CODEC); // apply a status effect to yourself or another entity a number of times
        registerType(QuestAPI.id("deal_damage_amount"), DealDamageAmountTask.CODEC); // deal a total amount of damage to matching entities
        registerType(QuestAPI.id("do_healing_amount"), DoHealingAmountTask.CODEC); // heal a total amount, self or (best-effort, Spell Engine only) others
        registerType(QuestAPI.id("visit_biome"), VisitBiomeTask.CODEC); // visit a number of distinct matching biomes
        registerType(QuestAPI.id("quest_line_choice"), QuestLineChoiceTask.CODEC); // pick one of several quest lines - the sole task on a quest-line root quest
    }

    record TaskType<T extends QuestTask>(ResourceLocation id, MapCodec<T> codec) {
        @SuppressWarnings("unchecked")
        public MapCodec<QuestTask> mapCodec() {
            return (MapCodec<QuestTask>) (MapCodec<?>) codec;
        }
    }
}
