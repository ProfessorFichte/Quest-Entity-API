package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.compat.SpellEngineCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

// Rolls a random spell matching the filters (pool/tier range/exclusions) unless spell_id is set, in which case that exact spell is always granted.
// Delegates to Spell Engine's own ScrollItem.applySpell (SpellEngineCompat.createSpellScroll) instead of hand-setting components, so scrolls match naturally-found ones.
public record SpellScrollReward(
        Optional<ResourceLocation> spellId,
        Optional<ResourceLocation> pool,
        int tierMin,
        int tierMax,
        List<ResourceLocation> excludedSpells,
        int amount,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward {

    public static final MapCodec<SpellScrollReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("spell_id").forGetter(SpellScrollReward::spellId),
                    ResourceLocation.CODEC.optionalFieldOf("pool").forGetter(SpellScrollReward::pool),
                    Codec.INT.optionalFieldOf("tier_min", 1).forGetter(SpellScrollReward::tierMin),
                    Codec.INT.optionalFieldOf("tier_max", Integer.MAX_VALUE).forGetter(SpellScrollReward::tierMax),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("excluded_spells", List.of()).forGetter(SpellScrollReward::excludedSpells),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(SpellScrollReward::amount),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(SpellScrollReward::textureOverrideId)
            ).apply(instance, SpellScrollReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_scroll");
    }

    @Override
    public void grant(ServerPlayer player) {
        if (!SpellEngineCompat.isLoaded()) {
            QuestAPI.LOGGER.warn("[SpellScrollReward] Spell Engine isn't loaded - skipping reward");
            return;
        }
        for (int i = 0; i < Math.max(1, amount); i++) {
            ItemStack scroll = SpellEngineCompat.createSpellScroll(
                    player.serverLevel(), spellId, pool, tierMin, tierMax, excludedSpells);
            if (scroll.isEmpty()) continue;
            if (!player.getInventory().add(scroll)) {
                player.drop(scroll, false);
            }
        }
    }

    @Override
    public Component getDisplayText() {
        // prefer the scroll's real item name (e.g. "Frost Spell Scroll") over the raw pool tag/spell_id
        Component target;
        if (SpellEngineCompat.isLoaded() && pool.isPresent()) {
            target = SpellEngineCompat.scrollDisplayName(pool.get());
        } else {
            target = Component.literal(spellId.map(ResourceLocation::toString)
                    .or(() -> pool.map(id -> "#" + id))
                    .orElse("random spell"));
        }
        return Component.translatable("reward.quest_api.spell_scroll", amount, target);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        // spell isn't rolled until claim time, so there's nothing real to preview - shows a generic icon instead
        return Optional.empty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> spellId = Optional.empty();
        private Optional<ResourceLocation> pool = Optional.empty();
        private int tierMin = 1;
        private int tierMax = Integer.MAX_VALUE;
        private List<ResourceLocation> excludedSpells = List.of();
        private int amount = 1;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder spellId(ResourceLocation id) {
            this.spellId = Optional.of(id);
            return this;
        }

        public Builder spellId(String id) {
            return spellId(ResourceLocation.parse(id));
        }

        public Builder pool(ResourceLocation pool) {
            this.pool = Optional.of(pool);
            return this;
        }

        public Builder pool(String pool) {
            return pool(ResourceLocation.parse(pool));
        }

        public Builder tierRange(int min, int max) {
            this.tierMin = min;
            this.tierMax = max;
            return this;
        }

        public Builder excludedSpells(ResourceLocation... ids) {
            this.excludedSpells = List.of(ids);
            return this;
        }

        public Builder excludedSpells(String... ids) {
            this.excludedSpells = java.util.Arrays.stream(ids).map(ResourceLocation::parse).toList();
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

        public SpellScrollReward build() {
            return new SpellScrollReward(spellId, pool, tierMin, tierMax, excludedSpells, amount, textureOverrideId);
        }
    }
}
