package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.SpellEngineCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

// clearExisting drops any spells already bound to the item first so only this one remains;
// otherwise it's added alongside whatever's already there.
public record SpellBindReward(ResourceLocation spellId, boolean clearExisting, Optional<ResourceLocation> textureOverrideId) implements QuestReward, TargetItemReward, EnhanceOperation {

    public SpellBindReward(ResourceLocation spellId) {
        this(spellId, false, Optional.empty());
    }

    public SpellBindReward(ResourceLocation spellId, boolean clearExisting) {
        this(spellId, clearExisting, Optional.empty());
    }

    public static final MapCodec<SpellBindReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("spell_id").forGetter(SpellBindReward::spellId),
                    Codec.BOOL.optionalFieldOf("clear_existing", false).forGetter(SpellBindReward::clearExisting),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(SpellBindReward::textureOverrideId)
            ).apply(instance, SpellBindReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("spell_bind");
    }

    @Override
    public boolean isValidTarget(Level level, ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public void applyToTarget(ServerPlayer player, ItemStack stack) {
        if (!SpellEngineCompat.isLoaded()) {
            QuestEntityAPI.LOGGER.warn("[SpellBindReward] Spell Engine isn't loaded - skipping reward");
            return;
        }
        SpellEngineCompat.bindSpellToItem(player.serverLevel(), stack, spellId, clearExisting);
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestEntityAPI.LOGGER.warn("[SpellBindReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.spell_bind", spellId.toString());
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}
