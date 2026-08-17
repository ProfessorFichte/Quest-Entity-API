package com.qeapi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.qeapi.QuestAPI;
import com.qeapi.api.QuestEntity;
import com.qeapi.client.ClientQuestCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public class QuestMarkerRenderer {

    public static final ResourceLocation MARKER_RED = QuestAPI.id("textures/gui/marker/quest_red.png");
    public static final ResourceLocation MARKER_GREY = QuestAPI.id("textures/gui/marker/quest_grey.png");
    public static final ResourceLocation MARKER_GREEN = QuestAPI.id("textures/gui/marker/quest_green.png");
    public static final ResourceLocation MARKER_DONE_CHECKMARK = QuestAPI.id("textures/gui/marker/green_checkmark.png");
    public static final ResourceLocation MARKER_ENRAGED = QuestAPI.id("textures/gui/marker/quest_entity_enraged.png");

    private static final float BOB_AMPLITUDE = 0.1f;
    private static final float BOB_SPEED = 0.12f;
    private static final float MARKER_SIZE = 0.4f;
    private static final float DELIVERY_ITEM_SPIN_SPEED = 1.5f; // degrees per tick

    public static void render(Entity entity, PoseStack poseStack, MultiBufferSource buffer,
                               float partialTick, int packedLight) {
        MarkerState state = getMarkerState(entity);
        if (state == MarkerState.NONE) return;

        Minecraft mc = Minecraft.getInstance();

        poseStack.pushPose();

        float height = entity.getBbHeight() + 0.6f;
        poseStack.translate(0, height, 0);

        if (state == MarkerState.AVAILABLE || state == MarkerState.READY) {
            float time = (entity.level().getGameTime() + partialTick) * BOB_SPEED;
            float bob = Mth.sin(time) * BOB_AMPLITUDE;
            poseStack.translate(0, bob, 0);
        }

        // No extra 180-degree flip here: the quad renders with RenderType.entityCutoutNoCull
        // (backface culling off), so that rotation never served a visibility purpose - it only
        // mirrored the texture horizontally.
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        poseStack.scale(MARKER_SIZE, MARKER_SIZE, MARKER_SIZE);

        ResourceLocation texture = switch (state) {
            case AVAILABLE -> MARKER_RED;
            case ACTIVE -> MARKER_GREY;
            case READY -> MARKER_GREEN;
            case DONE -> MARKER_DONE_CHECKMARK;
            case ENRAGED -> MARKER_ENRAGED;
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

        if (ClientQuestCache.isEnraged(entity.getUUID())) {
            return MarkerState.ENRAGED;
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

    // Entirely independent of the exclamation-mark state above - the item this entity wants, not
    // "does it have quests".
    public static boolean shouldRenderDeliveryItem(Entity entity) {
        ItemStack item = ClientQuestCache.getDeliveryTargetItem(entity.getUUID());
        return item != null && !item.isEmpty();
    }

    // Same bob-animation math as render() above, plus a slow spin so the icon reads from every
    // angle as the camera orbits. The enchantment-glint override forces the shimmering foil
    // overlay regardless of whether the item is actually enchanted - cheapest reliable "glowing"
    // look available through the normal item render path, applied only to this display copy.
    public static void renderDeliveryItem(Entity entity, PoseStack poseStack, MultiBufferSource buffer,
                                           float partialTick, int packedLight) {
        ItemStack item = ClientQuestCache.getDeliveryTargetItem(entity.getUUID());
        if (item == null || item.isEmpty()) return;

        ItemStack displayStack = item.copy();
        displayStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        Minecraft mc = Minecraft.getInstance();

        poseStack.pushPose();

        float height = entity.getBbHeight() + 0.6f;
        poseStack.translate(0, height, 0);

        float time = (entity.level().getGameTime() + partialTick) * BOB_SPEED;
        float bob = Mth.sin(time) * BOB_AMPLITUDE;
        poseStack.translate(0, bob, 0);

        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        float spin = (entity.level().getGameTime() + partialTick) * DELIVERY_ITEM_SPIN_SPEED;
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));

        poseStack.scale(0.5f, 0.5f, 0.5f);

        mc.getItemRenderer().renderStatic(displayStack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), 0);

        poseStack.popPose();
    }

    public enum MarkerState {
        NONE,       // No marker
        AVAILABLE,  // Red animated marker - quests available, none active
        ACTIVE,     // Grey static marker - quest in progress
        READY,      // Green animated marker - quest complete, ready to claim
        DONE,       // Green checkmark, static marker - every quest already completed
        ENRAGED     // Static marker - entity refuses to interact (hit cooldown)
    }
}
