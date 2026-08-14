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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public record EnchantRandomlyReward(int levelCap, Optional<ResourceLocation> textureOverrideId) implements QuestReward, TargetItemReward, EnhanceOperation {

    public EnchantRandomlyReward(int levelCap) {
        this(levelCap, Optional.empty());
    }

    public static final MapCodec<EnchantRandomlyReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.intRange(1, 255).optionalFieldOf("level_cap", 1).forGetter(EnchantRandomlyReward::levelCap),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(EnchantRandomlyReward::textureOverrideId)
            ).apply(instance, EnchantRandomlyReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("enchant_randomly");
    }

    @Override
    public boolean isValidTarget(Level level, ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public void applyToTarget(ServerPlayer player, ItemStack stack) {
        Registry<Enchantment> registry = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        List<Holder.Reference<Enchantment>> candidates = registry.holders()
                .filter(holder -> holder.value().definition().supportedItems().contains(stack.getItem().builtInRegistryHolder()))
                .filter(holder -> existing.getLevel(holder) == 0)
                .toList();

        if (candidates.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("[EnchantRandomlyReward] No valid enchantment left to roll for {}",
                    stack.getItem());
            return;
        }

        Holder.Reference<Enchantment> chosen = candidates.get(player.getRandom().nextInt(candidates.size()));
        int maxLevel = Math.min(levelCap, chosen.value().definition().maxLevel());
        int level = 1 + player.getRandom().nextInt(Math.max(1, maxLevel));

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        mutable.set(chosen, level);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestEntityAPI.LOGGER.warn("[EnchantRandomlyReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.enchant_randomly", levelCap);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}
