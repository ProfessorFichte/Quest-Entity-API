package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.compat.ModCompatUtil;
import com.qeapi.quest.QuestProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

// picking a line writes to PlayerQuestData's line-selection map instead of QuestProgress, so isComplete here is just interface compliance - real completion is isResolved()
public record QuestLineChoiceTask(
        List<LineOption> lineOptions,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE =
            QuestAPI.id("textures/gui/quest_tasks/quest_line_choice_default.png");

    public record LineOption(
            String id,
            Component displayName,
            Optional<Component> description,
            ResourceLocation iconTextureId,
            Optional<String> requiredMod
    ) {
        public static final Codec<LineOption> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("id").forGetter(LineOption::id),
                        ComponentSerialization.CODEC.fieldOf("display_name").forGetter(LineOption::displayName),
                        ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(LineOption::description),
                        ResourceLocation.CODEC.fieldOf("icon_texture_id").forGetter(LineOption::iconTextureId),
                        Codec.STRING.optionalFieldOf("required_mod").forGetter(LineOption::requiredMod)
                ).apply(instance, LineOption::new)
        );

        public boolean isAvailable() {
            return requiredMod.isEmpty() || ModCompatUtil.isModLoaded(requiredMod.get());
        }
    }

    public static final MapCodec<QuestLineChoiceTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    LineOption.CODEC.listOf().fieldOf("line_options").forGetter(QuestLineChoiceTask::lineOptions),
                    Codec.INT.optionalFieldOf("task_order").forGetter(QuestLineChoiceTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(QuestLineChoiceTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(QuestLineChoiceTask::textureOverrideId)
            ).apply(instance, QuestLineChoiceTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("quest_line_choice");
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        return Component.translatable(getDefaultTranslationKey());
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.quest_line_choice";
    }

    @Override
    public boolean isComplete(QuestProgress progress, int taskIndex) {
        return false;
    }

    @Override
    public int getTargetAmount() {
        return 1;
    }

    public List<LineOption> availableLineOptions() {
        return lineOptions.stream().filter(LineOption::isAvailable).toList();
    }

    // The root is claimable once every one of its (available) lines has been resolved.
    public boolean isResolved(Set<String> resolvedLines) {
        for (LineOption option : availableLineOptions()) {
            if (!resolvedLines.contains(option.id())) {
                return false;
            }
        }
        return true;
    }

    public Optional<LineOption> findLine(String lineId) {
        return lineOptions.stream().filter(option -> option.id().equals(lineId)).findFirst();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<LineOption> lineOptions = new java.util.ArrayList<>();
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder line(LineOption option) {
            this.lineOptions.add(option);
            return this;
        }

        public Builder line(String id, Component displayName, ResourceLocation iconTextureId) {
            return line(new LineOption(id, displayName, Optional.empty(), iconTextureId, Optional.empty()));
        }

        public Builder line(String id, Component displayName, Component description, ResourceLocation iconTextureId) {
            return line(new LineOption(id, displayName, Optional.of(description), iconTextureId, Optional.empty()));
        }

        public Builder line(String id, Component displayName, Component description, ResourceLocation iconTextureId, String requiredMod) {
            return line(new LineOption(id, displayName, Optional.of(description), iconTextureId, Optional.of(requiredMod)));
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

        public QuestLineChoiceTask build() {
            return new QuestLineChoiceTask(List.copyOf(lineOptions), taskOrder, choiceGroup, textureOverrideId);
        }
    }
}
