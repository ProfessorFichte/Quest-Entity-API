package com.qeapi.mixin;

import com.qeapi.api.QuestEntity;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.component.EntityQuestComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Allows villagers with quest components to provide quests. The quest button is added to the
// MerchantScreen instead of intercepting interaction.
@Mixin(Villager.class)
public abstract class VillagerMixin {

    public boolean qe_api$hasQuests() {
        Villager self = (Villager) (Object) this;
        EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(self);
        return component != null;
    }

    public ResourceLocation qe_api$getQuestPoolId() {
        Villager self = (Villager) (Object) this;
        EntityQuestComponent component = QuestEntityAccess.getEntityQuestComponent(self);
        return component != null ? component.questPoolId() : null;
    }
}
