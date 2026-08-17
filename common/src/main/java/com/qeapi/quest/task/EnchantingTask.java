package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Map;
import java.util.Optional;

public record EnchantingTask(
        Optional<ResourceLocation> enchantmentId,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<EnchantingTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("enchantment_id").forGetter(EnchantingTask::enchantmentId),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(EnchantingTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(EnchantingTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(EnchantingTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(EnchantingTask::textureOverrideId)
            ).apply(instance, EnchantingTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("enchanting");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String enchantmentName = enchantmentId.map(ResourceLocation::toString).orElse("an enchantment");

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "enchant_amount", String.valueOf(amount),
                        "current_enchants", String.valueOf(current),
                        "enchantment_name", enchantmentName
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.enchanting";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    // only fires for the Enchanting Table - an anvil merging enchanted books doesn't go through CriteriaTriggers.ENCHANTED_ITEM
    public boolean matches(ServerLevel level, ItemStack enchantedItem) {
        if (enchantmentId.isEmpty()) return true;

        Optional<Holder.Reference<Enchantment>> holder = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(enchantmentId.get());
        if (holder.isEmpty()) return false;

        return EnchantmentHelper.getItemEnchantmentLevel(holder.get(), enchantedItem) > 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> enchantmentId = Optional.empty();
        private int amount = 1;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder enchantmentId(ResourceLocation id) {
            this.enchantmentId = Optional.of(id);
            return this;
        }

        public Builder enchantmentId(String id) {
            return enchantmentId(ResourceLocation.parse(id));
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

        public EnchantingTask build() {
            return new EnchantingTask(enchantmentId, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}
