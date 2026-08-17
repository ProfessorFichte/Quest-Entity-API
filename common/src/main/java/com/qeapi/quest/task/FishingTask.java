package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

// no fish_id/fish_tag falls back to #minecraft:fishes, so junk/treasure catches don't count by default
public record FishingTask(
        Optional<ResourceLocation> fishId,
        Optional<TagKey<Item>> fishTag,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestAPI.id("textures/gui/quest_tasks/fishing_default.png");

    public static final MapCodec<FishingTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("fish_id").forGetter(FishingTask::fishId),
                    TagKey.codec(Registries.ITEM).optionalFieldOf("fish_tag").forGetter(FishingTask::fishTag),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(FishingTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(FishingTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(FishingTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(FishingTask::textureOverrideId)
            ).apply(instance, FishingTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("fishing");
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String fishName = getFishDisplayName();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "fish_amount", String.valueOf(amount),
                        "current_fish", String.valueOf(current),
                        "fish_name", fishName
                )
        );
    }

    public String getFishDisplayName() {
        if (fishId.isPresent()) {
            Item item = BuiltInRegistries.ITEM.get(fishId.get());
            return item != null ? item.getDescription().getString() : fishId.get().toString();
        }
        if (fishTag.isPresent()) {
            return "#" + fishTag.get().location();
        }
        return "fish";
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.fishing";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ItemStack caught) {
        if (caught.isEmpty()) return false;

        if (fishId.isPresent()) {
            ResourceLocation caughtId = BuiltInRegistries.ITEM.getKey(caught.getItem());
            return caughtId.equals(fishId.get());
        }
        if (fishTag.isPresent()) {
            return caught.is(fishTag.get());
        }
        return caught.is(ItemTags.FISHES);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> fishId = Optional.empty();
        private Optional<TagKey<Item>> fishTag = Optional.empty();
        private int amount = 1;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder fishId(ResourceLocation id) {
            this.fishId = Optional.of(id);
            return this;
        }

        public Builder fishId(String id) {
            return fishId(ResourceLocation.parse(id));
        }

        public Builder fish(Item item) {
            return fishId(BuiltInRegistries.ITEM.getKey(item));
        }

        public Builder fishTag(TagKey<Item> tag) {
            this.fishTag = Optional.of(tag);
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

        public FishingTask build() {
            return new FishingTask(fishId, fishTag, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}
