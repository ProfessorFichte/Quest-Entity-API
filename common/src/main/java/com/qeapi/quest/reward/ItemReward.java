package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.reward.function.ItemFunction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public record ItemReward(
        ResourceLocation itemId,
        int amount,
        Optional<DataComponentPatch> components,
        List<ItemFunction> functions,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward {

    public static final MapCodec<ItemReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("item_id").forGetter(ItemReward::itemId),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(ItemReward::amount),
                    DataComponentPatch.CODEC.optionalFieldOf("components").forGetter(ItemReward::components),
                    ItemFunction.CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(ItemReward::functions),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(ItemReward::textureOverrideId)
            ).apply(instance, ItemReward::new)
    );

    public ItemReward(ResourceLocation itemId, int amount, Optional<DataComponentPatch> components) {
        this(itemId, amount, components, List.of(), Optional.empty());
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("item");
    }

    @Override
    public void grant(ServerPlayer player) {
        ItemStack stack = createItemStack(player);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public Component getDisplayText() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        String itemName = item != null ? item.getDescription().getString() : itemId.toString();
        return Component.translatable("reward.qe_api.item", amount, itemName);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(createItemStack(null));
    }

    public ItemStack createItemStack() {
        return createItemStack(null);
    }

    // player may be null for display purposes.
    public ItemStack createItemStack(ServerPlayer player) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) {
            QuestEntityAPI.LOGGER.warn("Item not found for reward: {}", itemId);
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, amount);

        components.ifPresent(stack::applyComponents); // legacy support

        for (ItemFunction function : functions) {
            try {
                stack = function.apply(stack, player);
            } catch (Exception e) {
                QuestEntityAPI.LOGGER.warn("Failed to apply function {} to item {}: {}",
                        function.getTypeId(), itemId, e.getMessage());
            }
        }

        return stack;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ResourceLocation itemId;
        private int amount = 1;
        private Optional<DataComponentPatch> components = Optional.empty();
        private List<ItemFunction> functions = List.of();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder itemId(ResourceLocation id) {
            this.itemId = id;
            return this;
        }

        public Builder itemId(String id) {
            return itemId(ResourceLocation.parse(id));
        }

        public Builder item(Item item) {
            return itemId(BuiltInRegistries.ITEM.getKey(item));
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder components(DataComponentPatch patch) {
            this.components = Optional.of(patch);
            return this;
        }

        public Builder functions(List<ItemFunction> functions) {
            this.functions = functions;
            return this;
        }

        public Builder addFunction(ItemFunction function) {
            if (this.functions.isEmpty()) {
                this.functions = new java.util.ArrayList<>();
            }
            this.functions.add(function);
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public ItemReward build() {
            if (itemId == null) {
                throw new IllegalStateException("ItemReward requires itemId");
            }
            return new ItemReward(itemId, amount, components, functions, textureOverrideId);
        }
    }
}
