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

// a raid has no single "killer", so this credits every nearby online player with a matching
// active quest instead - see QuestEventHandler.onRaidComplete and RaidTickMixin
public record RaidCompleteTask(
        int amount,
        Optional<Integer> minRaidLevel,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<RaidCompleteTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(RaidCompleteTask::amount),
                    Codec.INT.optionalFieldOf("min_raid_level").forGetter(RaidCompleteTask::minRaidLevel),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(RaidCompleteTask::textureOverrideId)
            ).apply(instance, RaidCompleteTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("raid_complete");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "raid_amount", String.valueOf(amount),
                        "current_raids", String.valueOf(current),
                        "min_raid_level", String.valueOf(minRaidLevel.orElse(0))
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.raid_complete";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(int raidLevel) {
        return minRaidLevel.isEmpty() || raidLevel >= minRaidLevel.get();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int amount = 1;
        private Optional<Integer> minRaidLevel = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder minRaidLevel(int minRaidLevel) {
            this.minRaidLevel = Optional.of(minRaidLevel);
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public RaidCompleteTask build() {
            return new RaidCompleteTask(amount, minRaidLevel, textureOverrideId);
        }
    }
}
