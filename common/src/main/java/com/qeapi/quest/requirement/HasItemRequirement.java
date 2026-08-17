package com.qeapi.quest.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

// checks for presence only - doesn't consume the item
public record HasItemRequirement(
        ResourceLocation itemId,
        int amount,
        Optional<ResourceLocation> textureOverrideId
) implements QuestRequirement {

    public static final MapCodec<HasItemRequirement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("item_id").forGetter(HasItemRequirement::itemId),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(HasItemRequirement::amount),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(HasItemRequirement::textureOverrideId)
            ).apply(instance, HasItemRequirement::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("has_item");
    }

    @Override
    public boolean isMet(ServerPlayer player) {
        if (BuiltInRegistries.ITEM.get(itemId) == null) {
            QuestAPI.LOGGER.warn("HasItemRequirement: Item {} not found in registry", itemId);
            return false;
        }
        return isMetClientSide(player);
    }

    @Override
    public Component getDisplayText() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        String itemName = item != null ? item.getDescription().getString() : itemId.toString();
        return Component.translatable("requirement.quest_api.has_item", amount, itemName);
    }

    @Override
    public Component getFailureMessage() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        String itemName = item != null ? item.getDescription().getString() : itemId.toString();
        return Component.translatable("requirement.quest_api.has_item.failure", amount, itemName);
    }

    @Override
    public boolean isMetClientSide(Player player) {
        Item targetItem = BuiltInRegistries.ITEM.get(itemId);
        if (targetItem == null) {
            return false;
        }

        int totalCount = 0;

        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(targetItem)) {
                totalCount += stack.getCount();
            }
        }

        for (ItemStack stack : player.getInventory().armor) {
            if (stack.is(targetItem)) {
                totalCount += stack.getCount();
            }
        }

        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(targetItem)) {
                totalCount += stack.getCount();
            }
        }

        return totalCount >= amount;
    }

    @Override
    public boolean canCheckClientSide() {
        return true;
    }

    public static HasItemRequirement of(ResourceLocation itemId, int amount) {
        return new HasItemRequirement(itemId, amount, Optional.empty());
    }

    public static HasItemRequirement of(String itemId, int amount) {
        return new HasItemRequirement(ResourceLocation.parse(itemId), amount, Optional.empty());
    }

    public static HasItemRequirement of(String itemId) {
        return of(itemId, 1);
    }
}
