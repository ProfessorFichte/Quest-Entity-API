package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.util.TextFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

// Records which quest_group the player picked for this entity's pool (see Quest.questGroup) -
// usually one option in a RewardChoicePool, so a "pick your path" quest locks the player into one
// branch. No-op if granted without entity context (see EntityAwareReward); that only happens
// through the direct-completion API, which doesn't pass an entity through.
//
// textureOverrideId is that path's own icon (e.g. a school's symbol), shown bordered with
// selection.png so the player can see what they picked. Falls back to a generic XP-bottle icon if
// omitted, same as SkillExperienceReward/SkillLevelReward.
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
        return QuestEntityAPI.id("set_quest_group");
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestEntityAPI.LOGGER.warn("[SetQuestGroupReward] granted without entity context - skipping");
    }

    @Override
    public void grantWithEntity(ServerPlayer player, Entity entity) {
        EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(entity);
        if (component == null) {
            QuestEntityAPI.LOGGER.warn("[SetQuestGroupReward] entity {} has no quest component - skipping", entity.getId());
            return;
        }
        QuestEntityAccess.setEntityQuestComponent(entity, component.withChosenQuestGroup(player.getUUID(), group));
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.set_quest_group", TextFormatting.titleCaseWords(group));
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}
