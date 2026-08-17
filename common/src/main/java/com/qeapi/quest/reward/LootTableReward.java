package com.qeapi.quest.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import java.util.Optional;

public record LootTableReward(
        ResourceLocation lootTableId,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward {

    public static final MapCodec<LootTableReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("loot_table_id").forGetter(LootTableReward::lootTableId),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(LootTableReward::textureOverrideId)
            ).apply(instance, LootTableReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("loot_table");
    }

    @Override
    public void grant(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        LootTable lootTable = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTableId));

        if (lootTable == LootTable.EMPTY) {
            QuestAPI.LOGGER.warn("LootTableReward: Loot table {} not found", lootTableId);
            return;
        }

        LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position());

        LootParams params = paramsBuilder.create(LootContextParamSets.GIFT);

        List<ItemStack> loot = lootTable.getRandomItems(params);

        for (ItemStack stack : loot) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        QuestAPI.LOGGER.debug("Granted {} items from loot table {} to player {}",
                loot.size(), lootTableId, player.getName().getString());
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.quest_api.loot_table", lootTableId.getPath());
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.CHEST));
    }

    // QuestScreen calls this every 3 seconds to rotate the preview icon.
    public Optional<ItemStack> getRandomPreviewItem(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }

        LootTable lootTable = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTableId));

        if (lootTable == LootTable.EMPTY) {
            return Optional.empty();
        }

        LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position());

        LootParams params = paramsBuilder.create(LootContextParamSets.GIFT);
        List<ItemStack> loot = lootTable.getRandomItems(params);

        if (loot.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(loot.get(player.getRandom().nextInt(loot.size())));
    }

    public static LootTableReward of(ResourceLocation lootTableId) {
        return new LootTableReward(lootTableId, Optional.empty());
    }

    public static LootTableReward of(String lootTableId) {
        return new LootTableReward(ResourceLocation.parse(lootTableId), Optional.empty());
    }
}
