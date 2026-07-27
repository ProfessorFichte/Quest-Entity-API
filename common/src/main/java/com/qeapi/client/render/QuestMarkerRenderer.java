package com.qeapi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qeapi.QuestEntityAPI;
import com.qeapi.api.QuestEntity;
import com.qeapi.client.ClientQuestCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

// Renders the exclamation mark indicator above quest entities.
public class QuestMarkerRenderer {

    public static final ResourceLocation MARKER_RED = QuestEntityAPI.id("textures/gui/marker/quest_red.png");
    public static final ResourceLocation MARKER_GREY = QuestEntityAPI.id("textures/gui/marker/quest_grey.png");
    public static final ResourceLocation MARKER_GREEN = QuestEntityAPI.id("textures/gui/marker/quest_green.png");
    public static final ResourceLocation MARKER_DONE_CHECKMARK = QuestEntityAPI.id("textures/gui/marker/green_checkmark.png");

    private static final float BOB_AMPLITUDE = 0.1f;
    private static final float BOB_SPEED = 0.12f;
    private static final float MARKER_SIZE = 0.4f; // Increased size for thicker marker

    public static void render(Entity entity, PoseStack poseStack, MultiBufferSource buffer,
                               float partialTick, int packedLight) {
        MarkerState state = getMarkerState(entity);
        if (state == MarkerState.NONE) return;

        Minecraft mc = Minecraft.getInstance();

        poseStack.pushPose();

        float height = entity.getBbHeight() + 0.6f;
        poseStack.translate(0, height, 0);

        // bob available (red) and ready-to-claim (green) markers
        if (state == MarkerState.AVAILABLE || state == MarkerState.READY) {
            float time = (entity.level().getGameTime() + partialTick) * BOB_SPEED;
            float bob = Mth.sin(time) * BOB_AMPLITUDE;
            poseStack.translate(0, bob, 0);
        }

        // Billboard - face camera. No extra 180-degree flip here: the quad renders with
        // RenderType.entityCutoutNoCull (backface culling disabled), so that rotation never
        // served a visibility purpose - it only mirrored the texture horizontally.
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        poseStack.scale(MARKER_SIZE, MARKER_SIZE, MARKER_SIZE);

        ResourceLocation texture = switch (state) {
            case AVAILABLE -> MARKER_RED;
            case ACTIVE -> MARKER_GREY;
            case READY -> MARKER_GREEN;
            case DONE -> MARKER_DONE_CHECKMARK;
            default -> MARKER_RED;
        };

        renderMarkerQuad(poseStack, buffer, texture, packedLight);

        poseStack.popPose();
    }

    private static void renderMarkerQuad(PoseStack poseStack, MultiBufferSource buffer,
                                          ResourceLocation texture, int packedLight) {
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        Matrix4f matrix = poseStack.last().pose();

        float halfSize = 0.5f;

        // Bottom-left
        vertexConsumer.addVertex(matrix, -halfSize, -halfSize, 0)
                .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                .setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0, 0, 1);

        // Bottom-right
        vertexConsumer.addVertex(matrix, halfSize, -halfSize, 0)
                .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                .setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0, 0, 1);

        // Top-right
        vertexConsumer.addVertex(matrix, halfSize, halfSize, 0)
                .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                .setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0, 0, 1);

        // Top-left
        vertexConsumer.addVertex(matrix, -halfSize, halfSize, 0)
                .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0, 0, 1);
    }

    public static MarkerState getMarkerState(Entity entity) {
        if (entity instanceof QuestEntity questEntity) {
            if (!questEntity.shouldShowQuestMarker()) {
                return MarkerState.NONE;
            }
        }

        if (ClientQuestCache.hasQuests(entity.getUUID())) {
            if (ClientQuestCache.hasActiveQuest(entity.getUUID())) {
                if (ClientQuestCache.isQuestReadyToClaim(entity.getUUID())) {
                    return MarkerState.READY; // Green - ready to claim
                }
                return MarkerState.ACTIVE; // Grey - quest in progress
            }
            if (ClientQuestCache.allQuestsCompleted(entity.getUUID())) {
                return MarkerState.DONE; // Green checkmark, static - nothing left to accept
            }
            return MarkerState.AVAILABLE; // Red - quests available
        }

        return MarkerState.NONE;
    }

    public static boolean shouldRender(Entity entity) {
        return getMarkerState(entity) != MarkerState.NONE;
    }

    public enum MarkerState {
        NONE,       // No marker
        AVAILABLE,  // Red animated marker - quests available, none active
        ACTIVE,     // Grey static marker - quest in progress
        READY,      // Green animated marker - quest complete, ready to claim
        DONE        // Green checkmark, static marker - every quest already completed
    }
}
