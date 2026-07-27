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

// Records the player's choice of quest_group for the granting entity's pool - see Quest.questGroup.
// Typically one option among several in a RewardChoicePool, so completing a "pick your path" quest
// commits the player to one branch. No-op with a warning if granted without entity context (see
// EntityAwareReward) - that only happens via direct API completion that doesn't pass one through.
//
// icon: required texture for this path (e.g. that school's own symbol), always shown bordered
// with selection.png so the player can see which path they've picked - unlike
// SkillExperienceReward/SkillLevelReward's icon, this one isn't optional, since a path choice
// with no visual mark of what's selected defeats the point.
public record SetQuestGroupReward(String group, ResourceLocation icon) implements QuestReward, EntityAwareReward {

    public static final MapCodec<SetQuestGroupReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("group").forGetter(SetQuestGroupReward::group),
                    ResourceLocation.CODEC.fieldOf("icon").forGetter(SetQuestGroupReward::icon)
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
