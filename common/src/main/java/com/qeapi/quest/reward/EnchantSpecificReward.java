package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.Optional;

// Enchants a player-chosen item with one specific enchantment - like a survival enchanting table
// or anvil, isValidTarget only allows items that enchantment actually supports (its own
// supported_items tag), so the picker only ever offers compatible items.
public record EnchantSpecificReward(ResourceLocation enchantmentId, int level) implements QuestReward, TargetItemReward, EnhanceOperation {

    public static final MapCodec<EnchantSpecificReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("enchantment_id").forGetter(EnchantSpecificReward::enchantmentId),
                    Codec.intRange(1, 255).optionalFieldOf("level", 1).forGetter(EnchantSpecificReward::level)
            ).apply(instance, EnchantSpecificReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("enchant_specific");
    }

    private Optional<Holder.Reference<Enchantment>> resolve(Level level) {
        Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
    }

    @Override
    public boolean isValidTarget(Level level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return resolve(level)
                .map(holder -> holder.value().definition().supportedItems().contains(stack.getItem().builtInRegistryHolder()))
                .orElse(false);
    }

    @Override
    public void applyToTarget(ServerPlayer player, ItemStack stack) {
        Optional<Holder.Reference<Enchantment>> holder = resolve(player.level());
        if (holder.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("[EnchantSpecificReward] Enchantment {} not found", enchantmentId);
            return;
        }
        if (!isValidTarget(player.level(), stack)) {
            QuestEntityAPI.LOGGER.warn("[EnchantSpecificReward] {} is not compatible with {}", enchantmentId, stack.getItem());
            return;
        }

        ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        int newLevel = Math.max(level, existing.getLevel(holder.get()));
        mutable.set(holder.get(), newLevel);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestEntityAPI.LOGGER.warn("[EnchantSpecificReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.enchant_specific", level, Component.translatable(
                "enchantment." + enchantmentId.getNamespace() + "." + enchantmentId.getPath()));
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}
