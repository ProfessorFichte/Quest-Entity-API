package com.qeapi.mixin;

import com.qeapi.QuestAPI;
import com.qeapi.api.QuestEntity;
import com.qeapi.command.QuestCommands;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.data.QuestManager;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Handles player interaction with quest entities - shift+right-click opens the quest GUI.
@Mixin(Player.class)
public abstract class PlayerInteractMixin {

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void quest_api$onInteract(Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = (Player) (Object) this;

        if (player.level().isClientSide || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        EntityQuestComponent component = QuestCommands.getEntityQuestComponent(entity);
        if (component == null && !(entity instanceof QuestEntity)) {
            return;
        }

        EntityQuestComponent questComponent;
        if (component != null) {
            questComponent = component;
        } else if (entity instanceof QuestEntity questEntity) {
            questComponent = EntityQuestComponent.create(questEntity.getQuestPoolId());
        } else {
            return;
        }

        // supports both direct pools and tags
        List<QuestPool> allPools = questComponent.getAllQuestPools();
        if (allPools.isEmpty()) {
            return;
        }

        List<Quest> availableQuests = new ArrayList<>();
        for (QuestPool pool : allPools) {
            availableQuests.addAll(pool.getAllQuests());
        }

        if (availableQuests.isEmpty()) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            QuestCommands.openQuestGui(serverPlayer, entity.getId(), questComponent, availableQuests);
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
