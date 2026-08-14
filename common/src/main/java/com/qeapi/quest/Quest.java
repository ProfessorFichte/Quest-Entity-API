package com.qeapi.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.requirement.QuestRequirement;
import com.qeapi.quest.reward.EntityAwareReward;
import com.qeapi.quest.reward.QuestReward;
import com.qeapi.quest.reward.RewardChoicePool;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Quest(
        ResourceLocation id,
        int tier,
        Optional<String> requiredMod,
        boolean followQuestOrder,
        Optional<Component> questName,
        Optional<Component> questDescription,
        List<QuestRequirement> requirements,
        List<QuestTask> tasks,
        List<QuestReward> rewards,
        List<RewardChoicePool> rewardChoicePools,
        int weight,
        Optional<Integer> repeatAfterDays,
        Optional<String> questGroup,
        Optional<String> questLine,
        Optional<ResourceLocation> acceptQuestSoundOverride,
        Optional<ResourceLocation> finishQuestSoundOverride
) {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 8;
    public static final int DEFAULT_REPEAT_AFTER_DAYS = 1;

    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("id", ResourceLocation.withDefaultNamespace("unknown"))
                            .forGetter(Quest::id),
                    Codec.intRange(MIN_TIER, MAX_TIER).fieldOf("tier").forGetter(Quest::tier),
                    Codec.STRING.optionalFieldOf("required_mod").forGetter(Quest::requiredMod),
                    Codec.BOOL.optionalFieldOf("follow_quest_order", true).forGetter(Quest::followQuestOrder),
                    ComponentSerialization.CODEC.optionalFieldOf("quest_name").forGetter(Quest::questName),
                    ComponentSerialization.CODEC.optionalFieldOf("quest_description").forGetter(Quest::questDescription),
                    QuestRequirement.CODEC.listOf().optionalFieldOf("requirements", List.of())
                            .forGetter(Quest::requirements),
                    QuestTask.CODEC.listOf().fieldOf("tasks").forGetter(Quest::tasks),
                    QuestReward.CODEC.listOf().fieldOf("rewards").forGetter(Quest::rewards),
                    RewardChoicePool.CODEC.listOf().optionalFieldOf("reward_choice_pools", List.of())
                            .forGetter(Quest::rewardChoicePools),
                    Codec.intRange(1, 1000).optionalFieldOf("weight", 100).forGetter(Quest::weight),
                    Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("repeat_after_days")
                            .forGetter(Quest::repeatAfterDays),
                    Codec.STRING.optionalFieldOf("quest_group").forGetter(Quest::questGroup),
                    Codec.STRING.optionalFieldOf("quest_line").forGetter(Quest::questLine),
                    ResourceLocation.CODEC.optionalFieldOf("accept_quest_sound_override").forGetter(Quest::acceptQuestSoundOverride),
                    ResourceLocation.CODEC.optionalFieldOf("finish_quest_sound_override").forGetter(Quest::finishQuestSoundOverride)
            ).apply(instance, Quest::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, Quest> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

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

    // Same as above, but for callers that only have a previously-resolved name string on hand (e.g.
    // the Active Quests screen, whose entries describe a quest-giver that may not be currently
    // loaded - see PlayerQuestData.QuestGiverLocation).
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

    public boolean meetsRequirements(ServerPlayer player) {
        for (QuestRequirement requirement : requirements) {
            if (!requirement.isMet(player)) {
                return false;
            }
        }
        return true;
    }

    public boolean isComplete(QuestProgress progress) {
        for (int i = 0; i < tasks.size(); i++) {
            if (!tasks.get(i).isComplete(progress, i)) {
                return false;
            }
        }
        return true;
    }

    // poolChoices must already be validated via isValidPoolChoice. No target-item slot for any
    // TargetItemReward in this quest - used by direct API completion (QuestEntityAccess), which
    // has no GUI picker to have chosen one from, so those rewards just no-op with a warning.
    // entity: the quest-giving entity, needed by an EntityAwareReward (e.g. SetQuestGroupReward).
    public void grantRewards(ServerPlayer player, Entity entity, List<List<Integer>> poolChoices) {
        grantRewards(player, entity, poolChoices, List.of());
    }

    // rewardTargetSlots: per entry in `rewards` (in order), the inventory slot (into
    // player.getInventory().items) the player picked as that reward's target, or -1/absent.
    // Slots are re-validated against TargetItemReward.isValidTarget here - never trust the
    // client's selection blindly.
    public void grantRewards(ServerPlayer player, Entity entity, List<List<Integer>> poolChoices, List<Integer> rewardTargetSlots) {
        for (int i = 0; i < rewards.size(); i++) {
            QuestReward reward = rewards.get(i);
            if (reward instanceof TargetItemReward targeted) {
                int slot = (rewardTargetSlots != null && i < rewardTargetSlots.size()) ? rewardTargetSlots.get(i) : -1;
                if (slot < 0 || slot >= player.getInventory().items.size()) {
                    QuestEntityAPI.LOGGER.warn("No valid target slot chosen for reward {} - skipping", reward.getTypeId());
                    continue;
                }
                ItemStack stack = player.getInventory().items.get(slot);
                if (!targeted.isValidTarget(player.level(), stack)) {
                    QuestEntityAPI.LOGGER.warn("Chosen target slot {} is not valid for reward {} - skipping",
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
        return new Quest(newId, tier, requiredMod, followQuestOrder, questName, questDescription,
                requirements, tasks, rewards, rewardChoicePools, weight, repeatAfterDays, questGroup, questLine,
                acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private int tier = 1;
        private Optional<String> requiredMod = Optional.empty();
        private boolean followQuestOrder = true;
        private Optional<Component> questName = Optional.empty();
        private Optional<Component> questDescription = Optional.empty();
        private List<QuestRequirement> requirements = List.of();
        private List<QuestTask> tasks = List.of();
        private List<QuestReward> rewards = List.of();
        private List<RewardChoicePool> rewardChoicePools = new java.util.ArrayList<>();
        private int weight = 100;
        private Optional<Integer> repeatAfterDays = Optional.empty();
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

        // Quest becomes acceptable again this many in-game days after it was last completed -
        // absent (the default) means once completed, it's done forever.
        public Builder repeatAfterDays(int days) {
            this.repeatAfterDays = Optional.of(days);
            return this;
        }

        public Builder repeatable() {
            return repeatAfterDays(DEFAULT_REPEAT_AFTER_DAYS);
        }

        // Only offered to a player who's chosen this group for the pool - see SetQuestGroupReward.
        // Absent (the default) means the quest is offered regardless of any chosen group.
        public Builder questGroup(String group) {
            this.questGroup = Optional.of(group);
            return this;
        }

        // Marks this quest as one step of a named quest line - only offered while that line is
        // the player's active line for this giver (see the questLine filter in
        // FabricNetworking/NeoForgeNetworking.getAvailableQuestsForPlayer). Absent (the default)
        // means this quest isn't part of any line.
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
            return new Quest(id, tier, requiredMod, followQuestOrder, questName, questDescription,
                    requirements, tasks, rewards, List.copyOf(rewardChoicePools), weight, repeatAfterDays, questGroup, questLine,
                    acceptQuestSoundOverride, finishQuestSoundOverride);
        }
    }
}
