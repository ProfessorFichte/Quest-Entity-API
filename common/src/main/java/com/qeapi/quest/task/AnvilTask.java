package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

// counts an anvil operation as complete only when it actually reduced an item's damage - a rename
// or an enchant-merge with no repair involved doesn't count. AnvilMenu.onTake only fires once a
// valid (cost > 0) operation is taken, so the before/after damage comparison is all that's needed
// to tell an actual repair apart from those other anvil uses.
public record AnvilTask(
        Optional<ResourceLocation> resultItemId,
        Optional<TagKey<Item>> resultItemTag,
        int amount,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_tasks/anvil_repair_default.png");

    public static final MapCodec<AnvilTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("result_item_id").forGetter(AnvilTask::resultItemId),
                    TagKey.codec(Registries.ITEM).optionalFieldOf("result_item_tag").forGetter(AnvilTask::resultItemTag),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(AnvilTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(AnvilTask::textureOverrideId)
            ).apply(instance, AnvilTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("anvil_repair");
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String itemName = getResultDisplayName();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "repair_amount", String.valueOf(amount),
                        "current_repairs", String.valueOf(current),
                        "item_name", itemName
                )
        );
    }

    public String getResultDisplayName() {
        if (resultItemId.isPresent()) {
            Item item = BuiltInRegistries.ITEM.get(resultItemId.get());
            return item != null ? item.getDescription().getString() : resultItemId.get().toString();
        }
        if (resultItemTag.isPresent()) {
            return "#" + resultItemTag.get().location();
        }
        return "an item";
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.anvil_repair";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ItemStack before, ItemStack after) {
        if (!wasRepaired(before, after)) return false;

        if (resultItemId.isPresent()) {
            ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(after.getItem());
            return resultId.equals(resultItemId.get());
        }
        if (resultItemTag.isPresent()) {
            return after.is(resultItemTag.get());
        }
        return true;
    }

    private static boolean wasRepaired(ItemStack before, ItemStack after) {
        if (before.isEmpty() || after.isEmpty()) return false;
        if (!before.isDamageableItem() || !after.isDamageableItem()) return false;
        return after.getDamageValue() < before.getDamageValue();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> resultItemId = Optional.empty();
        private Optional<TagKey<Item>> resultItemTag = Optional.empty();
        private int amount = 1;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder resultItemId(ResourceLocation id) {
            this.resultItemId = Optional.of(id);
            return this;
        }

        public Builder resultItemId(String id) {
            return resultItemId(ResourceLocation.parse(id));
        }

        public Builder resultItem(Item item) {
            return resultItemId(BuiltInRegistries.ITEM.getKey(item));
        }

        public Builder resultItemTag(TagKey<Item> tag) {
            this.resultItemTag = Optional.of(tag);
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public AnvilTask build() {
            return new AnvilTask(resultItemId, resultItemTag, amount, textureOverrideId);
        }
    }
}
