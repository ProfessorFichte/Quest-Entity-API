package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.compat.SpellEngineCompat;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.FlexibleListCodec;
import com.qeapi.util.TextMutator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// matches any spell cast if none of spell_ids/spell_pools/spell_schools are set
public record SpellCastTask(
        List<ResourceLocation> spellIds,
        List<ResourceLocation> spellPools,
        List<ResourceLocation> spellSchools,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<SpellCastTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("spell_ids", List.of()).forGetter(SpellCastTask::spellIds),
                    FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("spell_pools", List.of()).forGetter(SpellCastTask::spellPools),
                    FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("spell_schools", List.of()).forGetter(SpellCastTask::spellSchools),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(SpellCastTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(SpellCastTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(SpellCastTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(SpellCastTask::textureOverrideId)
            ).apply(instance, SpellCastTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_cast");
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return textureOverrideId;
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String spellName = getDisplayName();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "cast_amount", String.valueOf(amount),
                        "current_casts", String.valueOf(current),
                        "spell_name", spellName
                )
        );
    }

    private String getDisplayName() {
        if (!spellIds.isEmpty()) {
            String first = spellIds.get(0).toString();
            return spellIds.size() == 1 ? first : first + " (+" + (spellIds.size() - 1) + " others)";
        }
        if (!spellPools.isEmpty()) {
            String first = "#" + spellPools.get(0);
            return spellPools.size() == 1 ? first : first + " (+" + (spellPools.size() - 1) + " others)";
        }
        if (!spellSchools.isEmpty()) {
            String first = spellSchools.get(0).toString();
            return spellSchools.size() == 1 ? first : first + " (+" + (spellSchools.size() - 1) + " others)";
        }
        return "any spell";
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.spell_cast";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    // requires Spell Engine loaded (spell_pool/spell_school resolution needs its registry)
    public boolean matches(ServerLevel level, ResourceLocation castSpellId) {
        if (!SpellEngineCompat.isLoaded()) return false;
        return SpellEngineCompat.matchesSelector(level, castSpellId, spellIds, spellPools, spellSchools);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ResourceLocation> spellIds = List.of();
        private List<ResourceLocation> spellPools = List.of();
        private List<ResourceLocation> spellSchools = List.of();
        private int amount = 1;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder spellId(ResourceLocation id) {
            return spellIds(id);
        }

        public Builder spellId(String id) {
            return spellId(ResourceLocation.parse(id));
        }

        public Builder spellIds(ResourceLocation... ids) {
            this.spellIds = List.of(ids);
            return this;
        }

        public Builder spellPool(ResourceLocation pool) {
            return spellPools(pool);
        }

        public Builder spellPool(String pool) {
            return spellPool(ResourceLocation.parse(pool));
        }

        public Builder spellPools(ResourceLocation... pools) {
            this.spellPools = List.of(pools);
            return this;
        }

        public Builder spellSchool(ResourceLocation school) {
            return spellSchools(school);
        }

        public Builder spellSchool(String school) {
            return spellSchool(ResourceLocation.parse(school));
        }

        public Builder spellSchools(ResourceLocation... schools) {
            this.spellSchools = List.of(schools);
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder taskOrder(int order) {
            this.taskOrder = Optional.of(order);
            return this;
        }

        public Builder choiceGroup(String groupId) {
            this.choiceGroup = Optional.of(groupId);
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public SpellCastTask build() {
            return new SpellCastTask(spellIds, spellPools, spellSchools, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}
