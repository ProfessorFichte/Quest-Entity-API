package com.qeapi.mixin.client;

import com.qeapi.QuestEntityAPI;
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

// Adds a quest tab/button to the MerchantScreen (villager trading).
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    @Unique
    private Button qe_api$questButton;

    @Unique
    private boolean qe_api$hasQuests = false;

    @Unique
    private int qe_api$merchantEntityId = -1;

    public MerchantScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void qe_api$addQuestButton(CallbackInfo ci) {
        // set by MultiPlayerGameModeMixin when the player interacted
        qe_api$merchantEntityId = ClientQuestCache.getLastMerchantEntityId();
        UUID merchantUuid = ClientQuestCache.getLastMerchantEntityUuid();

        qe_api$hasQuests = merchantUuid != null && ClientQuestCache.hasQuests(merchantUuid);

        QuestEntityAPI.LOGGER.debug("MerchantScreen init: entityId={}, uuid={}, hasQuests={}",
                qe_api$merchantEntityId, merchantUuid, qe_api$hasQuests);

        if (qe_api$hasQuests && qe_api$merchantEntityId != -1) {
            int buttonX = this.leftPos + this.imageWidth - 24;
            int buttonY = this.topPos + 4;

            qe_api$questButton = Button.builder(
                    Component.literal("Q"), // Quest button
                    button -> qe_api$openQuestScreen()
            ).bounds(buttonX, buttonY, 20, 14).build();

            this.addRenderableWidget(qe_api$questButton);
        }
    }

    @Unique
    private void qe_api$openQuestScreen() {
        if (qe_api$merchantEntityId == -1) {
            QuestEntityAPI.LOGGER.warn("Cannot open quest screen: merchant entity ID not tracked");
            return;
        }

        QuestEntityAPI.LOGGER.debug("Opening quest screen for merchant entity {}", qe_api$merchantEntityId);

        // so the quest screen's back button returns here
        ClientQuestCache.setOpenedFromMerchant(true);

        // also closes the container on the server
        if (this.minecraft != null) {
            this.onClose();
        }

        ClientPacketSender.sendRequestQuestMenu(qe_api$merchantEntityId);
    }
}
