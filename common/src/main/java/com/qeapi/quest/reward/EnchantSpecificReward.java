package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
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

public record EnchantSpecificReward(ResourceLocation enchantmentId, int level, Optional<ResourceLocation> textureOverrideId) implements QuestReward, TargetItemReward, EnhanceOperation {

    public EnchantSpecificReward(ResourceLocation enchantmentId, int level) {
        this(enchantmentId, level, Optional.empty());
    }

    public static final MapCodec<EnchantSpecificReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("enchantment_id").forGetter(EnchantSpecificReward::enchantmentId),
                    Codec.intRange(1, 255).optionalFieldOf("level", 1).forGetter(EnchantSpecificReward::level),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(EnchantSpecificReward::textureOverrideId)
            ).apply(instance, EnchantSpecificReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("enchant_specific");
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
            QuestAPI.LOGGER.warn("[EnchantSpecificReward] Enchantment {} not found", enchantmentId);
            return;
        }
        if (!isValidTarget(player.level(), stack)) {
            QuestAPI.LOGGER.warn("[EnchantSpecificReward] {} is not compatible with {}", enchantmentId, stack.getItem());
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
        QuestAPI.LOGGER.warn("[EnchantSpecificReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.quest_api.enchant_specific", level, Component.translatable(
                "enchantment." + enchantmentId.getNamespace() + "." + enchantmentId.getPath()));
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}
