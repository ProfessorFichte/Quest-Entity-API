package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Map;
import java.util.Optional;

public record BrewPotionTask(
        ResourceLocation potionId,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<BrewPotionTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("potion_id").forGetter(BrewPotionTask::potionId),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(BrewPotionTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(BrewPotionTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(BrewPotionTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(BrewPotionTask::textureOverrideId)
            ).apply(instance, BrewPotionTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("brew_potion");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        ItemStack displayStack = getDisplayStack();
        String potionName = !displayStack.isEmpty() ? displayStack.getHoverName().getString() : potionId.toString();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "brew_amount", String.valueOf(amount),
                        "current_brews", String.valueOf(current),
                        "potion_name", potionName
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.brew_potion";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ItemStack brewedStack) {
        if (brewedStack.isEmpty()) return false;
        PotionContents contents = brewedStack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        return contents.potion()
                .map(Holder::value)
                .map(BuiltInRegistries.POTION::getKey)
                .map(potionId::equals)
                .orElse(false);
    }

    public ItemStack getDisplayStack() {
        Potion potion = BuiltInRegistries.POTION.get(potionId);
        if (potion == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(potion)));
        return stack;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ResourceLocation potionId;
        private int amount = 1;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder potionId(ResourceLocation id) {
            this.potionId = id;
            return this;
        }

        public Builder potionId(String id) {
            return potionId(ResourceLocation.parse(id));
        }

        public Builder potion(Potion potion) {
            return potionId(BuiltInRegistries.POTION.getKey(potion));
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

        public BrewPotionTask build() {
            if (potionId == null) {
                throw new IllegalStateException("BrewPotionTask requires potionId");
            }
            return new BrewPotionTask(potionId, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}
