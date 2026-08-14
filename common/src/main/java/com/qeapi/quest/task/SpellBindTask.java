package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

// optional/soft dependency on Spell Engine - see SpellBindingCriteriaMixin. Fires once per
// individual spell bound at the Spell Binding Table; no spell_pool filter matches any bind.
public record SpellBindTask(
        Optional<ResourceLocation> spellPool,
        int amount,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<SpellBindTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("spell_pool").forGetter(SpellBindTask::spellPool),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(SpellBindTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(SpellBindTask::textureOverrideId)
            ).apply(instance, SpellBindTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("spell_bind");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String poolName = spellPool.map(id -> "#" + id).orElse("a spell");

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "bind_amount", String.valueOf(amount),
                        "current_binds", String.valueOf(current),
                        "spell_pool_name", poolName
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.spell_bind";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ResourceLocation boundSpellPool) {
        if (spellPool.isEmpty()) return true;
        return boundSpellPool != null && spellPool.get().equals(boundSpellPool);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> spellPool = Optional.empty();
        private int amount = 1;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder spellPool(ResourceLocation pool) {
            this.spellPool = Optional.of(pool);
            return this;
        }

        public Builder spellPool(String pool) {
            return spellPool(ResourceLocation.parse(pool));
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public SpellBindTask build() {
            return new SpellBindTask(spellPool, amount, textureOverrideId);
        }
    }
}
