package com.qeapi.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.quest.requirement.QuestRequirement;
import com.qeapi.quest.reward.EntityAwareReward;
import com.qeapi.quest.reward.QuestReward;
import com.qeapi.quest.reward.RewardChoicePool;
import com.qeapi.quest.reward.RewardEntry;
import com.qeapi.quest.reward.TargetItemReward;
import com.qeapi.quest.task.QuestTask;
import com.qeapi.util.EntityNameResolver;
import com.qeapi.util.TextMutator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record Quest(
        ResourceLocation id,
        int tier,
        Optional<String> requiredMod,
        boolean followQuestOrder,
        TaskMode taskMode,
        int decisionCount,
        List<TaskChoiceGroup> taskChoiceGroups,
        Optional<Component> questName,
        Optional<Component> questDescription,
        List<QuestRequirement> requirements,
        List<QuestTask> tasks,
        List<RewardEntry> rewardEntries,
        int weight,
        Optional<Integer> repeatAfterDays,
        boolean shuffleRefreshingQuests,
        Optional<String> questGroup,
        Optional<String> questLine,
        Optional<ResourceLocation> acceptQuestSoundOverride,
        Optional<ResourceLocation> finishQuestSoundOverride
) {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 8;
    public static final int DEFAULT_REPEAT_AFTER_DAYS = 1;

    // Governs how the tasks list is judged complete (see isComplete/isTaskUnlocked); DECISION treats the
    // whole list as one implicit choice group, but explicit task_choice_groups still apply on top of it.
    public enum TaskMode implements StringRepresentable {
        ALL("all"),
        ORDER("order"),
        DECISION("decision");

        public static final Codec<TaskMode> CODEC = StringRepresentable.fromEnum(TaskMode::values);

        private final String name;

        TaskMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // RecordCodecBuilder.group() caps at 16 args and Quest has more fields than that, so the task settings
    // are folded into one nested group slot to fit (same trick as EntityKillTask.ExtraFilters) - still
    // serializes as flat top-level JSON, not a nested object.
    private record TaskSettings(boolean followQuestOrder, TaskMode taskMode, int decisionCount,
                                 List<TaskChoiceGroup> taskChoiceGroups) {}

    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("id", ResourceLocation.withDefaultNamespace("unknown"))
                            .forGetter(Quest::id),
                    Codec.intRange(MIN_TIER, MAX_TIER).fieldOf("tier").forGetter(Quest::tier),
                    Codec.STRING.optionalFieldOf("required_mod").forGetter(Quest::requiredMod),
                    instance.group(
                            Codec.BOOL.optionalFieldOf("follow_quest_order", true).forGetter(Quest::followQuestOrder),
                            TaskMode.CODEC.optionalFieldOf("type", TaskMode.ALL).forGetter(Quest::taskMode),
                            Codec.intRange(1, 64).optionalFieldOf("decision_count", 1).forGetter(Quest::decisionCount),
                            TaskChoiceGroup.CODEC.listOf().optionalFieldOf("task_choice_groups", List.of())
                                    .forGetter(Quest::taskChoiceGroups)
                    ).apply(instance, TaskSettings::new),
                    ComponentSerialization.CODEC.optionalFieldOf("quest_name").forGetter(Quest::questName),
                    ComponentSerialization.CODEC.optionalFieldOf("quest_description").forGetter(Quest::questDescription),
                    QuestRequirement.CODEC.listOf().optionalFieldOf("requirements", List.of())
                            .forGetter(Quest::requirements),
                    QuestTask.CODEC.listOf().fieldOf("tasks").forGetter(Quest::tasks),
                    RewardEntry.CODEC.listOf().fieldOf("rewards").forGetter(Quest::rewardEntries),
                    Codec.intRange(1, 1000).optionalFieldOf("weight", 100).forGetter(Quest::weight),
                    Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("repeat_after_days")
                            .forGetter(Quest::repeatAfterDays),
                    Codec.BOOL.optionalFieldOf("shuffle_refreshing_quests", false).forGetter(Quest::shuffleRefreshingQuests),
                    Codec.STRING.optionalFieldOf("quest_group").forGetter(Quest::questGroup),
                    Codec.STRING.optionalFieldOf("quest_line").forGetter(Quest::questLine),
                    ResourceLocation.CODEC.optionalFieldOf("accept_quest_sound_override").forGetter(Quest::acceptQuestSoundOverride),
                    ResourceLocation.CODEC.optionalFieldOf("finish_quest_sound_override").forGetter(Quest::finishQuestSoundOverride)
            ).apply(instance, (id, tier, requiredMod, taskSettings, questName, questDescription, requirements,
                    tasks, rewardEntries, weight, repeatAfterDays, shuffleRefreshingQuests, questGroup, questLine,
                    acceptQuestSoundOverride, finishQuestSoundOverride) ->
                    new Quest(id, tier, requiredMod, taskSettings.followQuestOrder(), taskSettings.taskMode(),
                            taskSettings.decisionCount(), taskSettings.taskChoiceGroups(), questName, questDescription,
                            requirements, tasks, rewardEntries, weight, repeatAfterDays, shuffleRefreshingQuests, questGroup,
                            questLine, acceptQuestSoundOverride, finishQuestSoundOverride))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, Quest> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    // Derived views over rewardEntries for callers that only care about one kind (most rendering/claim
    // code) - kept from before "rewards"/"reward_choice_pools" merged into one JSON array.
    public List<QuestReward> rewards() {
        List<QuestReward> result = new ArrayList<>();
        for (RewardEntry entry : rewardEntries) {
            if (entry instanceof RewardEntry.All all) {
                result.add(all.reward());
            }
        }
        return result;
    }

    public List<RewardChoicePool> rewardChoicePools() {
        List<RewardChoicePool> result = new ArrayList<>();
        for (RewardEntry entry : rewardEntries) {
            if (entry instanceof RewardEntry.Choice choice) {
                result.add(choice.pool());
            }
        }
        return result;
    }

    public static String defaultNameKey(ResourceLocation id) {
        return "quest." + id.getNamespace() + "." + id.getPath().replace("/", ".") + ".name";
    }

    public static String defaultDescriptionKey(ResourceLocation id) {
        return "quest." + id.getNamespace() + "." + id.getPath().replace("/", ".") + ".desc";
    }

    public Component getDisplayName() {
        return questName.orElseGet(() -> Component.translatable(defaultNameKey(id)));
    }

    public Component getDescription() {
        return questDescription.orElseGet(() ->
                Component.translatable(defaultDescriptionKey(id))
        );
    }

    // Resolves the {entity_name} placeholder against the quest-giving entity - see TextMutator.
    public Component getDescription(Entity givingEntity) {
        return getDescription(EntityNameResolver.resolve(givingEntity));
    }

    public Component getDisplayName(Entity givingEntity) {
        return getDisplayName(EntityNameResolver.resolve(givingEntity));
    }

    // Same as above but for callers holding only a previously-resolved name, e.g. the Active Quests
    // screen, whose entries describe a quest-giver that may not currently be loaded.
    public Component getDescription(String entityName) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("entity_name", entityName);
        for (int i = 0; i < tasks.size(); i++) {
            QuestTask task = tasks.get(i);
            String amount = String.valueOf(task.getTargetAmount());
            replacements.put("amount_" + i, amount);
            replacements.putIfAbsent("amount", amount);
            for (Map.Entry<String, String> value : task.getDescriptionValues().entrySet()) {
                replacements.put(value.getKey() + "_" + i, value.getValue());
                replacements.putIfAbsent(value.getKey(), value.getValue());
            }
        }
        return TextMutator.mutate(getDescription(), replacements);
    }

    public Component getDisplayName(String entityName) {
        return TextMutator.mutate(getDisplayName(), Map.of("entity_name", entityName));
    }

    // completedAtDayTime: EntityQuestComponent.getCompletionDayTime's result for this quest (-1 if
    // never completed); currentDayTime: ServerLevel.getDayTime(). False for a non-repeatable quest.
    public boolean isDueForRepeat(long completedAtDayTime, long currentDayTime) {
        if (repeatAfterDays.isEmpty() || completedAtDayTime < 0) {
            return false;
        }
        long elapsedDays = (currentDayTime - completedAtDayTime) / 24000L;
        return elapsedDays >= repeatAfterDays.get();
    }

    // Ticks remaining until due again (0 if already due or n/a) - used for the quest GUI's real-world
    // countdown (1 in-game day = 20 real minutes at normal tick speed).
    public long remainingRepeatTicks(long completedAtDayTime, long currentDayTime) {
        if (repeatAfterDays.isEmpty() || completedAtDayTime < 0) {
            return 0;
        }
        long totalTicks = repeatAfterDays.get() * 24000L;
        long elapsedTicks = currentDayTime - completedAtDayTime;
        return Math.max(0, totalTicks - elapsedTicks);
    }

    public boolean meetsRequirements(ServerPlayer player) {
        for (QuestRequirement requirement : requirements) {
            if (!requirement.isMet(player)) {
                return false;
            }
        }
        return true;
    }

    // Resolved membership of one choice_group label (or DECISION's synthetic whole-list group) -
    // Java-side only, never serialized.
    private record ResolvedChoiceGroup(List<Integer> taskIndices, int requiredCount) {}

    // Groups tasks by choice_group label, resolving each group's requiredCount from taskChoiceGroups
    // (default 1); under DECISION also adds an unlabeled synthetic group covering every task, with
    // decisionCount required.
    private List<ResolvedChoiceGroup> effectiveTaskChoiceGroups() {
        Map<String, List<Integer>> membersByGroup = new LinkedHashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            final int taskIndex = i;
            tasks.get(i).choiceGroup().ifPresent(groupId ->
                    membersByGroup.computeIfAbsent(groupId, id -> new ArrayList<>()).add(taskIndex));
        }

        List<ResolvedChoiceGroup> effective = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : membersByGroup.entrySet()) {
            int requiredCount = taskChoiceGroups.stream()
                    .filter(group -> group.groupId().equals(entry.getKey()))
                    .findFirst()
                    .map(TaskChoiceGroup::requiredCount)
                    .orElse(1);
            effective.add(new ResolvedChoiceGroup(entry.getValue(), requiredCount));
        }

        if (taskMode == TaskMode.DECISION) {
            List<Integer> allIndices = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                allIndices.add(i);
            }
            effective.add(new ResolvedChoiceGroup(allIndices, decisionCount));
        }
        return effective;
    }

    // GUI-facing summary of a resolved choice group - see taskChoiceInfo/singleChoiceGroupCoveringAllTasks.
    public record TaskChoiceInfo(int requiredCount, int totalMembers) {}

    // Resolved the same way isComplete/getSatisfyingTaskIndices already do (respects required_count
    // overrides and TaskMode.DECISION), unlike a naive "same choiceGroup() label" check.
    public Optional<TaskChoiceInfo> taskChoiceInfo(int taskIndex) {
        for (ResolvedChoiceGroup group : effectiveTaskChoiceGroups()) {
            if (group.taskIndices().contains(taskIndex)) {
                return Optional.of(new TaskChoiceInfo(group.requiredCount(), group.taskIndices().size()));
            }
        }
        return Optional.empty();
    }

    // True when the whole tasks list is one choice group - lets the GUI show "Choose Tasks: (X/Y)"
    // instead of per-task "(OR)" hints. A partial/multi-group quest falls back to the hint instead.
    public Optional<TaskChoiceInfo> singleChoiceGroupCoveringAllTasks() {
        List<ResolvedChoiceGroup> groups = effectiveTaskChoiceGroups();
        if (groups.size() == 1 && groups.get(0).taskIndices().size() == tasks.size()) {
            ResolvedChoiceGroup group = groups.get(0);
            return Optional.of(new TaskChoiceInfo(group.requiredCount(), group.taskIndices().size()));
        }
        return Optional.empty();
    }

    // Tasks sharing a choice_group only need requiredCount of that group done (OR); tasks outside
    // any group stay mandatory (AND), same as when no task has a choice_group.
    public boolean isComplete(QuestProgress progress) {
        Set<Integer> groupedIndices = new HashSet<>();
        for (ResolvedChoiceGroup group : effectiveTaskChoiceGroups()) {
            groupedIndices.addAll(group.taskIndices());
            long completedCount = group.taskIndices().stream()
                    .filter(index -> tasks.get(index).isComplete(progress, index))
                    .count();
            if (completedCount < group.requiredCount()) {
                return false;
            }
        }

        for (int i = 0; i < tasks.size(); i++) {
            if (!groupedIndices.contains(i) && !tasks.get(i).isComplete(progress, i)) {
                return false;
            }
        }
        return true;
    }

    // A task's place in the ORDER sequence: its explicit task_order if set, else its plain list index.
    private int effectiveOrder(int taskIndex) {
        return tasks.get(taskIndex).taskOrder().orElse(taskIndex);
    }

    // Under ORDER, a task only accepts progress once every task with a smaller effective order is
    // complete - always true otherwise (ALL/DECISION: any task any order).
    public boolean isTaskUnlocked(QuestProgress progress, int taskIndex) {
        if (taskMode != TaskMode.ORDER) return true;
        int order = effectiveOrder(taskIndex);
        for (int i = 0; i < tasks.size(); i++) {
            if (i == taskIndex) continue;
            if (effectiveOrder(i) < order && !tasks.get(i).isComplete(progress, i)) {
                return false;
            }
        }
        return true;
    }

    // The first requiredCount complete members (in list order) of each group. A complete member NOT
    // in this set is "extra" and wasn't needed, so callers like BringItemTask consumption must leave
    // it alone rather than treating every satisfiable group member as required.
    public Set<Integer> getSatisfyingTaskIndices(QuestProgress progress) {
        Set<Integer> satisfying = new HashSet<>();
        for (ResolvedChoiceGroup group : effectiveTaskChoiceGroups()) {
            int taken = 0;
            for (int index : group.taskIndices()) {
                if (taken >= group.requiredCount()) break;
                if (tasks.get(index).isComplete(progress, index)) {
                    satisfying.add(index);
                    taken++;
                }
            }
        }
        return satisfying;
    }

    // True for every ungrouped task, and for a grouped task only if it's one of that group's actual
    // satisfying members - see getSatisfyingTaskIndices.
    public boolean isTaskConsumable(QuestProgress progress, int taskIndex) {
        boolean inAnyGroup = effectiveTaskChoiceGroups().stream()
                .anyMatch(group -> group.taskIndices().contains(taskIndex));
        if (!inAnyGroup) return true;
        return getSatisfyingTaskIndices(progress).contains(taskIndex);
    }

    // poolChoices must already be validated via isValidPoolChoice; entity is needed for an
    // EntityAwareReward (e.g. SetQuestGroupReward). Used by direct API completion (QuestEntityAccess),
    // which has no GUI picker, so any TargetItemReward here just no-ops with a warning.
    public void grantRewards(ServerPlayer player, Entity entity, List<List<Integer>> poolChoices) {
        grantRewards(player, entity, poolChoices, List.of());
    }

    // rewardTargetSlots: per `rewards` entry, the inventory slot the player picked as its target
    // (-1/absent if none) - re-validated here against TargetItemReward.isValidTarget since the
    // client's pick can't be trusted.
    public void grantRewards(ServerPlayer player, Entity entity, List<List<Integer>> poolChoices, List<Integer> rewardTargetSlots) {
        List<QuestReward> rewards = rewards();
        for (int i = 0; i < rewards.size(); i++) {
            QuestReward reward = rewards.get(i);
            if (reward instanceof TargetItemReward targeted) {
                int slot = (rewardTargetSlots != null && i < rewardTargetSlots.size()) ? rewardTargetSlots.get(i) : -1;
                if (slot < 0 || slot >= player.getInventory().items.size()) {
                    QuestAPI.LOGGER.warn("No valid target slot chosen for reward {} - skipping", reward.getTypeId());
                    continue;
                }
                ItemStack stack = player.getInventory().items.get(slot);
                if (!targeted.isValidTarget(player.level(), stack)) {
                    QuestAPI.LOGGER.warn("Chosen target slot {} is not valid for reward {} - skipping",
                            slot, reward.getTypeId());
                    continue;
                }
                targeted.applyToTarget(player, stack);
            } else if (reward instanceof EntityAwareReward entityAware) {
                entityAware.grantWithEntity(player, entity);
            } else {
                reward.grant(player);
            }
        }
        List<RewardChoicePool> rewardChoicePools = rewardChoicePools();
        for (int i = 0; i < rewardChoicePools.size(); i++) {
            RewardChoicePool pool = rewardChoicePools.get(i);
            for (int index : poolChoices.get(i)) {
                QuestReward option = pool.options().get(index).reward();
                if (option instanceof EntityAwareReward entityAware) {
                    entityAware.grantWithEntity(player, entity);
                } else {
                    option.grant(player);
                }
            }
        }
    }

    // One entry per pool, each satisfying that pool's own RewardChoicePool.isValidChoice.
    public boolean isValidPoolChoice(List<List<Integer>> poolChoices) {
        List<RewardChoicePool> rewardChoicePools = rewardChoicePools();
        if (poolChoices.size() != rewardChoicePools.size()) return false;
        for (int i = 0; i < rewardChoicePools.size(); i++) {
            if (!rewardChoicePools.get(i).isValidChoice(poolChoices.get(i))) return false;
        }
        return true;
    }

    public boolean isModLoaded() {
        if (requiredMod.isEmpty()) {
            return true;
        }
        // reflection avoids a compile-time dependency on platform-specific code
        try {
            Class<?> platformClass = Class.forName("dev.architectury.platform.Platform");
            java.lang.reflect.Method isModLoaded = platformClass.getMethod("isModLoaded", String.class);
            return (Boolean) isModLoaded.invoke(null, requiredMod.get());
        } catch (Exception e) {
            // fall back to Fabric loader directly
            try {
                Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
                java.lang.reflect.Method getInstance = fabricLoader.getMethod("getInstance");
                Object instance = getInstance.invoke(null);
                java.lang.reflect.Method isModLoadedMethod = fabricLoader.getMethod("isModLoaded", String.class);
                return (Boolean) isModLoadedMethod.invoke(instance, requiredMod.get());
            } catch (Exception e2) {
                // assume loaded if we can't check
                return true;
            }
        }
    }

    public Quest withId(ResourceLocation newId) {
        return new Quest(newId, tier, requiredMod, followQuestOrder, taskMode, decisionCount, taskChoiceGroups,
                questName, questDescription, requirements, tasks, rewardEntries, weight, repeatAfterDays,
                shuffleRefreshingQuests, questGroup, questLine, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private int tier = 1;
        private Optional<String> requiredMod = Optional.empty();
        private boolean followQuestOrder = true;
        private TaskMode taskMode = TaskMode.ALL;
        private int decisionCount = 1;
        private List<TaskChoiceGroup> taskChoiceGroups = new ArrayList<>();
        private Optional<Component> questName = Optional.empty();
        private Optional<Component> questDescription = Optional.empty();
        private List<QuestRequirement> requirements = List.of();
        private List<QuestTask> tasks = List.of();
        private List<QuestReward> rewards = List.of();
        private List<RewardChoicePool> rewardChoicePools = new ArrayList<>();
        private int weight = 100;
        private Optional<Integer> repeatAfterDays = Optional.empty();
        private boolean shuffleRefreshingQuests = false;
        private Optional<String> questGroup = Optional.empty();
        private Optional<String> questLine = Optional.empty();
        private Optional<ResourceLocation> acceptQuestSoundOverride = Optional.empty();
        private Optional<ResourceLocation> finishQuestSoundOverride = Optional.empty();

        public Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder tier(int tier) {
            this.tier = tier;
            return this;
        }

        public Builder requiredMod(String modId) {
            this.requiredMod = Optional.of(modId);
            return this;
        }

        public Builder followQuestOrder(boolean follow) {
            this.followQuestOrder = follow;
            return this;
        }

        public Builder taskMode(TaskMode mode) {
            this.taskMode = mode;
            return this;
        }

        // Shorthand for taskMode(ORDER) - tasks unlock in task_order sequence (see QuestTask.taskOrder).
        public Builder ordered() {
            return taskMode(TaskMode.ORDER);
        }

        // Shorthand for taskMode(DECISION) with a custom requiredCount (the whole tasks list becomes
        // one implicit group) - use taskChoiceGroup(...) instead for partial/multi-group cases.
        public Builder decision(int requiredCount) {
            this.decisionCount = requiredCount;
            return taskMode(TaskMode.DECISION);
        }

        public Builder decision() {
            return decision(1);
        }

        // Overrides a choice_group's requiredCount above its default of 1; membership itself comes
        // from tagging tasks with the same groupId via their own .choiceGroup(String) call.
        public Builder taskChoiceGroup(String groupId, int requiredCount) {
            this.taskChoiceGroups.add(new TaskChoiceGroup(groupId, requiredCount));
            return this;
        }

        // Quest becomes acceptable again this many in-game days after completion; absent (the
        // default) means it's done forever once completed.
        public Builder repeatAfterDays(int days) {
            this.repeatAfterDays = Optional.of(days);
            return this;
        }

        public Builder repeatable() {
            return repeatAfterDays(DEFAULT_REPEAT_AFTER_DAYS);
        }

        // Once due to refresh, offer a different weighted-random repeatable quest from the same
        // tier instead of always re-offering this one.
        public Builder shuffleRefreshingQuests() {
            this.shuffleRefreshingQuests = true;
            return this;
        }

        // Only offered to a player who's chosen this group for the pool (see SetQuestGroupReward);
        // absent means offered regardless of any chosen group.
        public Builder questGroup(String group) {
            this.questGroup = Optional.of(group);
            return this;
        }

        // Marks this quest as one step of a named quest line - only offered while that line is the
        // player's active line for this giver (see the questLine filter in
        // FabricNetworking/NeoForgeNetworking.getAvailableQuestsForPlayer).
        public Builder questLine(String lineId) {
            this.questLine = Optional.of(lineId);
            return this;
        }

        public Builder name(Component name) {
            this.questName = Optional.of(name);
            return this;
        }

        public Builder description(Component description) {
            this.questDescription = Optional.of(description);
            return this;
        }

        public Builder requirements(List<QuestRequirement> requirements) {
            this.requirements = requirements;
            return this;
        }

        public Builder requirement(QuestRequirement requirement) {
            this.requirements = List.of(requirement);
            return this;
        }

        public Builder tasks(List<QuestTask> tasks) {
            this.tasks = tasks;
            return this;
        }

        public Builder task(QuestTask task) {
            this.tasks = List.of(task);
            return this;
        }

        public Builder rewards(List<QuestReward> rewards) {
            this.rewards = rewards;
            return this;
        }

        public Builder reward(QuestReward reward) {
            this.rewards = List.of(reward);
            return this;
        }

        public Builder rewardChoicePool(int pick, QuestReward... options) {
            this.rewardChoicePools.add(new RewardChoicePool(
                    java.util.Arrays.stream(options).map(RewardChoicePool.Option::of).toList(), pick));
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder acceptQuestSoundOverride(ResourceLocation soundId) {
            this.acceptQuestSoundOverride = Optional.of(soundId);
            return this;
        }

        public Builder finishQuestSoundOverride(ResourceLocation soundId) {
            this.finishQuestSoundOverride = Optional.of(soundId);
            return this;
        }

        public Quest build() {
            if (tasks.isEmpty()) {
                throw new IllegalStateException("Quest must have at least one task");
            }
            if (rewards.isEmpty()) {
                throw new IllegalStateException("Quest must have at least one reward");
            }
            List<RewardEntry> rewardEntries = new ArrayList<>();
            for (QuestReward reward : rewards) {
                rewardEntries.add(new RewardEntry.All(reward));
            }
            for (RewardChoicePool pool : rewardChoicePools) {
                rewardEntries.add(new RewardEntry.Choice(pool));
            }
            return new Quest(id, tier, requiredMod, followQuestOrder, taskMode, decisionCount,
                    List.copyOf(taskChoiceGroups), questName, questDescription, requirements, tasks, rewardEntries,
                    weight, repeatAfterDays, shuffleRefreshingQuests, questGroup, questLine,
                    acceptQuestSoundOverride, finishQuestSoundOverride);
        }
    }
}
