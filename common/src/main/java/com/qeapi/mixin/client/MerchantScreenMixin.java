package com.qeapi.mixin.client;

import com.qeapi.QuestAPI;
import com.qeapi.client.ClientQuestCache;
import com.qeapi.network.ClientPacketSender;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    @Unique
    private Button quest_api$questButton;

    @Unique
    private boolean quest_api$hasQuests = false;

    @Unique
    private int quest_api$merchantEntityId = -1;

    public MerchantScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void quest_api$addQuestButton(CallbackInfo ci) {
        // set by MultiPlayerGameModeMixin when the player interacted
        quest_api$merchantEntityId = ClientQuestCache.getLastMerchantEntityId();
        UUID merchantUuid = ClientQuestCache.getLastMerchantEntityUuid();

        quest_api$hasQuests = merchantUuid != null && ClientQuestCache.hasQuests(merchantUuid);

        QuestAPI.LOGGER.debug("MerchantScreen init: entityId={}, uuid={}, hasQuests={}",
                quest_api$merchantEntityId, merchantUuid, quest_api$hasQuests);

        if (quest_api$hasQuests && quest_api$merchantEntityId != -1) {
            int buttonX = this.leftPos + this.imageWidth - 24;
            int buttonY = this.topPos + 4;

            quest_api$questButton = Button.builder(
                    Component.literal("Q"),
                    button -> quest_api$openQuestScreen()
            ).bounds(buttonX, buttonY, 20, 14).build();

            this.addRenderableWidget(quest_api$questButton);
        }
    }

    @Unique
    private void quest_api$openQuestScreen() {
        if (quest_api$merchantEntityId == -1) {
            QuestAPI.LOGGER.warn("Cannot open quest screen: merchant entity ID not tracked");
            return;
        }

        QuestAPI.LOGGER.debug("Opening quest screen for merchant entity {}", quest_api$merchantEntityId);

        // so the quest screen's back button returns here
        ClientQuestCache.setOpenedFromMerchant(true);

        // also closes the container on the server
        if (this.minecraft != null) {
            this.onClose();
        }

        ClientPacketSender.sendRequestQuestMenu(quest_api$merchantEntityId);
    }
}
