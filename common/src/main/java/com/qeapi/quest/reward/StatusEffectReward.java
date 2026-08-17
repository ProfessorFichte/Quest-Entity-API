package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record StatusEffectReward(
        ResourceLocation effectId,
        int duration,  // seconds
        int amplifier,  // 0 = level 1, 1 = level 2, etc.
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward {

    public static final MapCodec<StatusEffectReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("effect_id").forGetter(StatusEffectReward::effectId),
                    Codec.INT.fieldOf("duration").forGetter(StatusEffectReward::duration),
                    Codec.INT.optionalFieldOf("amplifier", 0).forGetter(StatusEffectReward::amplifier),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(StatusEffectReward::textureOverrideId)
            ).apply(instance, StatusEffectReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("status_effect");
    }

    @Override
    public void grant(ServerPlayer player) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
        if (effect == null) {
            QuestAPI.LOGGER.warn("Effect not found for reward: {}", effectId);
            return;
        }

        int durationTicks = duration * 20;

        MobEffectInstance instance = new MobEffectInstance(
                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                durationTicks,
                amplifier,
                false,  // ambient
                true,   // visible
                true    // show icon
        );

        player.addEffect(instance);
    }

    @Override
    public Component getDisplayText() {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
        String effectName = effect != null ? effect.getDisplayName().getString() : effectId.toString();

        String durationText;
        if (duration >= 60) {
            int minutes = duration / 60;
            int seconds = duration % 60;
            durationText = String.format("%d:%02d", minutes, seconds);
        } else {
            durationText = duration + "s";
        }

        String levelText = amplifier > 0 ? " " + com.qeapi.util.TextFormatting.toRomanNumeral(amplifier + 1) : "";

        return Component.translatable("reward.quest_api.status_effect",
                effectName + levelText, durationText);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        // no item - the effect icon is rendered separately
        return Optional.empty();
    }

    public MobEffect getEffect() {
        return BuiltInRegistries.MOB_EFFECT.get(effectId);
    }

    // most mods use the format effect.<namespace>.<path>.description
    public Optional<Component> getEffectDescription() {
        String descKey = "effect." + effectId.getNamespace() + "." + effectId.getPath() + ".description";
        Component desc = Component.translatable(descKey);
        if (!desc.getString().equals(descKey)) {
            return Optional.of(desc);
        }
        return Optional.empty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StatusEffectReward of(net.minecraft.core.Holder<MobEffect> effect, int durationTicks, int amplifier) {
        ResourceLocation effectId = effect.unwrapKey()
                .map(net.minecraft.resources.ResourceKey::location)
                .orElseGet(() -> BuiltInRegistries.MOB_EFFECT.getKey(effect.value()));
        return new StatusEffectReward(effectId, durationTicks / 20, amplifier, Optional.empty());
    }

    public static StatusEffectReward of(String effectId, int durationTicks, int amplifier) {
        return new StatusEffectReward(ResourceLocation.parse(effectId), durationTicks / 20, amplifier, Optional.empty());
    }

    public static StatusEffectReward of(String effectId) {
        return new StatusEffectReward(ResourceLocation.parse(effectId), 60, 0, Optional.empty());
    }

    public static class Builder {
        private ResourceLocation effectId;
        private int duration = 60;
        private int amplifier = 0;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder effectId(ResourceLocation id) {
            this.effectId = id;
            return this;
        }

        public Builder effectId(String id) {
            return effectId(ResourceLocation.parse(id));
        }

        public Builder effect(MobEffect effect) {
            return effectId(BuiltInRegistries.MOB_EFFECT.getKey(effect));
        }

        public Builder duration(int seconds) {
            this.duration = seconds;
            return this;
        }

        public Builder amplifier(int amplifier) {
            this.amplifier = amplifier;
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public StatusEffectReward build() {
            if (effectId == null) {
                throw new IllegalStateException("StatusEffectReward requires effectId");
            }
            return new StatusEffectReward(effectId, duration, amplifier, textureOverrideId);
        }
    }
}
