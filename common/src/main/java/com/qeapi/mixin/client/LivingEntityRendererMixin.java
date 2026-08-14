package com.qeapi.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qeapi.client.render.QuestMarkerRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Renders quest markers above living entities.
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> extends EntityRenderer<T> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void qe_api$renderQuestMarker(T entity, float entityYaw, float partialTick,
                                           PoseStack poseStack, MultiBufferSource buffer,
                                           int packedLight, CallbackInfo ci) {
        if (QuestMarkerRenderer.shouldRender(entity)) {
            QuestMarkerRenderer.render(entity, poseStack, buffer, partialTick, packedLight);
        }
        if (QuestMarkerRenderer.shouldRenderDeliveryItem(entity)) {
            QuestMarkerRenderer.renderDeliveryItem(entity, poseStack, buffer, partialTick, packedLight);
        }
    }
}
