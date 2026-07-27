package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.SpellEngineCompat;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Optional;

// Spell Engine integration - matches any spell if none of spell_id/spell_pool/spell_school are given
public record SpellCastTask(
        Optional<ResourceLocation> spellId,
        Optional<ResourceLocation> spellPool,
        Optional<ResourceLocation> spellSchool,
        int amount
) implements QuestTask {

    public static final MapCodec<SpellCastTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("spell_id").forGetter(SpellCastTask::spellId),
                    ResourceLocation.CODEC.optionalFieldOf("spell_pool").forGetter(SpellCastTask::spellPool),
                    ResourceLocation.CODEC.optionalFieldOf("spell_school").forGetter(SpellCastTask::spellSchool),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(SpellCastTask::amount)
            ).apply(instance, SpellCastTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("spell_cast");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String spellName = spellId.map(ResourceLocation::toString)
                .or(() -> spellPool.map(id -> "#" + id))
                .or(() -> spellSchool.map(ResourceLocation::toString))
                .orElse("any spell");

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "cast_amount", String.valueOf(amount),
                        "current_casts", String.valueOf(current),
                        "spell_name", spellName
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.spell_cast";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    // requires Spell Engine loaded (spell_pool/spell_school resolution needs its registry)
    public boolean matches(ServerLevel level, ResourceLocation castSpellId) {
        if (!SpellEngineCompat.isLoaded()) return false;
        return SpellEngineCompat.matchesSelector(level, castSpellId, spellId, spellPool, spellSchool);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> spellId = Optional.empty();
        private Optional<ResourceLocation> spellPool = Optional.empty();
        private Optional<ResourceLocation> spellSchool = Optional.empty();
        private int amount = 1;

        public Builder spellId(ResourceLocation id) {
            this.spellId = Optional.of(id);
            return this;
        }

        public Builder spellId(String id) {
            return spellId(ResourceLocation.parse(id));
        }

        public Builder spellPool(ResourceLocation pool) {
            this.spellPool = Optional.of(pool);
            return this;
        }

        public Builder spellPool(String pool) {
            return spellPool(ResourceLocation.parse(pool));
        }

        public Builder spellSchool(ResourceLocation school) {
            this.spellSchool = Optional.of(school);
            return this;
        }

        public Builder spellSchool(String school) {
            return spellSchool(ResourceLocation.parse(school));
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public SpellCastTask build() {
            return new SpellCastTask(spellId, spellPool, spellSchool, amount);
        }
    }
}
