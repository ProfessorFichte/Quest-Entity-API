package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.util.TextFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

// Records which quest_group the player picked (see Quest.questGroup) - usually one RewardChoicePool option, so a "pick your path" quest locks the player into a branch.
// No-op without entity context (only happens via the direct-completion API).
// textureOverrideId is the path's own icon, shown bordered with selection.png; falls back to a generic XP-bottle icon if omitted.
public record SetQuestGroupReward(String group, Optional<ResourceLocation> textureOverrideId) implements QuestReward, EntityAwareReward {

    public SetQuestGroupReward(String group, ResourceLocation textureOverrideId) {
        this(group, Optional.of(textureOverrideId));
    }

    public static final MapCodec<SetQuestGroupReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("group").forGetter(SetQuestGroupReward::group),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(SetQuestGroupReward::textureOverrideId)
            ).apply(instance, SetQuestGroupReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("set_quest_group");
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestAPI.LOGGER.warn("[SetQuestGroupReward] granted without entity context - skipping");
    }

    @Override
    public void grantWithEntity(ServerPlayer player, Entity entity) {
        EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(entity);
        if (component == null) {
            QuestAPI.LOGGER.warn("[SetQuestGroupReward] entity {} has no quest component - skipping", entity.getId());
            return;
        }
        QuestEntityAccess.setEntityQuestComponent(entity, component.withChosenQuestGroup(player.getUUID(), group));
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.quest_api.set_quest_group", TextFormatting.titleCaseWords(group));
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}
