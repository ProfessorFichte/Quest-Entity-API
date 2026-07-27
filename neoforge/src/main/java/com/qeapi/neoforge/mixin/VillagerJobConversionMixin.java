package com.qeapi.neoforge.mixin;

import com.qeapi.QuestEntityAPI;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.data.ChunkAssignmentTracker;
import com.qeapi.data.EntityQuestAssignment;
import com.qeapi.data.EntityQuestAssignmentManager;
import com.qeapi.data.QuestManager;
import com.qeapi.neoforge.QuestEntityAPINeoForge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

// Mirrors fabric.mixin.VillagerJobConversionMixin, using NeoForge attachment access.
// Handles villager profession changes:
// - UNEMPLOYED -> JOB: keep quest if a player already interacted, otherwise assign a new one.
// - JOB -> UNEMPLOYED: remove quest (workstation destroyed).
// - JOB -> JOB: keep quest if a player already interacted, otherwise reassign.
@Mixin(Villager.class)
public abstract class VillagerJobConversionMixin {

    @Shadow
    public abstract VillagerData getVillagerData();

    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void qe_api$onSetVillagerData(VillagerData newData, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;

        if (villager.level().isClientSide()) {
            return;
        }

        VillagerData oldData = getVillagerData();
        VillagerProfession oldProfession = oldData.getProfession();
        VillagerProfession newProfession = newData.getProfession();

        if (oldProfession == newProfession) {
            return;
        }

        ResourceLocation oldProfId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(oldProfession);
        ResourceLocation newProfId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(newProfession);

        QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Villager {} profession changed: {} -> {}",
                villager.getUUID(), oldProfId, newProfId);

        EntityQuestComponent existingComponent = villager.getExistingData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT).orElse(null);

        // Becoming UNEMPLOYED (workstation destroyed)
        if (newProfession == VillagerProfession.NONE) {
            QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Villager {} becoming unemployed", villager.getUUID());

            if (existingComponent != null && !existingComponent.isNoQuestMarker()) {
                if (existingComponent.hasAnyPlayerInteraction()) {
                    QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Villager {} has player interactions, keeping quest",
                            villager.getUUID());
                    return;
                }

                QuestEntityAPI.LOGGER.info("[VillagerJobMixin] Villager {} became unemployed, removing quest (no player interaction)",
                        villager.getUUID());
                villager.removeData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT);

                QuestEntityAPINeoForge.forceResyncForNearbyPlayers(villager);
            }
            return;
        }

        // Getting a job (UNEMPLOYED -> JOB or JOB -> different JOB)
        if (existingComponent != null) {
            if (existingComponent.isNoQuestMarker()) {
                QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Villager {} had no-quest marker, clearing for new profession {}",
                        villager.getUUID(), newProfId);
                villager.removeData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT);
            } else {
                if (existingComponent.hasAnyPlayerInteraction()) {
                    QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Villager {} has player interactions, keeping existing quests",
                            villager.getUUID());
                    return;
                }

                QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Villager {} has no player interactions, will reassign quest",
                        villager.getUUID());
            }
        }

        if (villager.level() instanceof ServerLevel serverLevel) {
            String biomeType = getBiomeType(villager);
            String profession = newProfId != null ? newProfId.toString() : "minecraft:none";

            QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Looking for quest assignment: biome={}, profession={}",
                    biomeType, profession);

            QuestManager.debugPrintAll();

            ResourceLocation villagerId = ResourceLocation.parse("minecraft:villager");
            var allAssignments = EntityQuestAssignmentManager.getAssignmentsForEntity(villagerId);
            QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Total villager assignments: {}", allAssignments.size());
            for (var a : allAssignments) {
                QuestEntityAPI.LOGGER.debug("[VillagerJobMixin]   Assignment: entity={}, pool={}, chance={}, villagerData={}",
                        a.entityId(), a.questPools(), a.questChance(), a.villagerData());
            }

            Optional<EntityQuestAssignment> assignmentOpt = EntityQuestAssignmentManager.findVillagerAssignment(
                    biomeType, profession, serverLevel.getRandom());

            if (assignmentOpt.isPresent()) {
                EntityQuestAssignment assignment = assignmentOpt.get();
                QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Found assignment: {} -> {}", assignment.entityId(), assignment.questPools());

                EntityQuestComponent newComponent = createComponentFromAssignment(villager, serverLevel, assignment);

                if (newComponent != null) {
                    villager.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, newComponent);
                    QuestEntityAPI.LOGGER.info("[VillagerJobMixin] Assigned quest tag {} to villager {} after job conversion to {}",
                            assignment.questPools(), villager.getUUID(), profession);

                    QuestEntityAPINeoForge.forceResyncForNearbyPlayers(villager);
                } else {
                    QuestEntityAPI.LOGGER.warn("[VillagerJobMixin] Failed to create component from assignment!");
                }
            } else {
                QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] No matching assignment found for villager (biome={}, profession={})",
                        biomeType, profession);

                if (!allAssignments.isEmpty()) {
                    villager.setData(QuestEntityAPINeoForge.ENTITY_QUEST_ATTACHMENT, EntityQuestComponent.createNoQuest());
                    QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Marked villager {} as no-quest (chance failed or no match)",
                            villager.getUUID());

                    QuestEntityAPINeoForge.forceResyncForNearbyPlayers(villager);
                }
            }
        }
    }

    private String getBiomeType(Villager villager) {
        ResourceLocation typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType());
        String biome = typeId != null ? typeId.getPath() : "plains";
        QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] getBiomeType: {} -> {}", typeId, biome);
        return biome;
    }

    // quest_pool always references a tag under tags/entity_quests/.
    private EntityQuestComponent createComponentFromAssignment(Villager villager, ServerLevel level, EntityQuestAssignment assignment) {
        // Only one entity within range may hold this assignment at a time
        if (assignment.chunkRestrictionRadius().isPresent()) {
            int radius = assignment.chunkRestrictionRadius().get();
            String restrictionKey = assignment.restrictionKey();
            ChunkPos chunkPos = new ChunkPos(villager.blockPosition());
            if (ChunkAssignmentTracker.get(level).isRestricted(restrictionKey, chunkPos, radius)) {
                QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Assignment {} is chunk-restricted near {}, marking as no-quest",
                        restrictionKey, chunkPos);
                return EntityQuestComponent.createNoQuest();
            }
        }

        String questPoolRef = assignment.pickQuestPool(level.getRandom());

        QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] createComponentFromAssignment: {}", questPoolRef);

        if (questPoolRef.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("[VillagerJobMixin] quest_pool is empty!");
            return null;
        }

        if (questPoolRef.startsWith("tag:")) {
            questPoolRef = questPoolRef.substring(4);
            if (questPoolRef.contains("/") && !questPoolRef.contains(":")) {
                int slashIndex = questPoolRef.indexOf('/');
                questPoolRef = questPoolRef.substring(0, slashIndex) + ":" + questPoolRef.substring(slashIndex + 1);
            }
            QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Converted legacy tag format to: {}", questPoolRef);
        }

        ResourceLocation tagId = ResourceLocation.parse(questPoolRef);
        QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Creating component with tag ID: {}", tagId);

        EntityQuestComponent component = EntityQuestComponent.create(tagId);

        var pools = component.getAllQuestPools();
        if (pools.isEmpty()) {
            QuestEntityAPI.LOGGER.error("[VillagerJobMixin] WARNING: Tag {} has no quest pools! " +
                    "Check that data/{}/tags/entity_quests/{}.json exists and references valid quest pool files.",
                    tagId, tagId.getNamespace(), tagId.getPath());
        } else {
            QuestEntityAPI.LOGGER.debug("[VillagerJobMixin] Tag {} has {} quest pools", tagId, pools.size());
        }

        if (assignment.chunkRestrictionRadius().isPresent()) {
            ChunkAssignmentTracker.get(level).markAssigned(
                    assignment.restrictionKey(), new ChunkPos(villager.blockPosition()), villager.getUUID());
        }

        return component;
    }
}
