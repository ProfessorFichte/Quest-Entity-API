package com.qeapi.fabric.mixin;

import com.qeapi.QuestAPI;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.data.ChunkAssignmentTracker;
import com.qeapi.data.EntityQuestAssignment;
import com.qeapi.data.EntityQuestAssignmentManager;
import com.qeapi.data.QuestManager;
import com.qeapi.fabric.QuestAPIFabric;
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

@Mixin(Villager.class)
public abstract class VillagerJobConversionMixin {

    @Shadow
    public abstract VillagerData getVillagerData();

    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void quest_api$onSetVillagerData(VillagerData newData, CallbackInfo ci) {
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

        QuestAPI.LOGGER.debug("[VillagerJobMixin] Villager {} profession changed: {} -> {}",
                villager.getUUID(), oldProfId, newProfId);

        EntityQuestComponent existingComponent = villager.getAttached(QuestAPIFabric.ENTITY_QUEST_ATTACHMENT);

        // Becoming UNEMPLOYED (workstation destroyed)
        if (newProfession == VillagerProfession.NONE) {
            QuestAPI.LOGGER.debug("[VillagerJobMixin] Villager {} becoming unemployed", villager.getUUID());

            if (existingComponent != null && !existingComponent.isNoQuestMarker()) {
                if (existingComponent.hasAnyPlayerInteraction()) {
                    QuestAPI.LOGGER.debug("[VillagerJobMixin] Villager {} has player interactions, keeping quest",
                            villager.getUUID());
                    return;
                }

                QuestAPI.LOGGER.info("[VillagerJobMixin] Villager {} became unemployed, removing quest (no player interaction)",
                        villager.getUUID());
                villager.removeAttached(QuestAPIFabric.ENTITY_QUEST_ATTACHMENT);

                // Resync so nearby players see the quest is gone
                QuestAPIFabric.forceResyncForNearbyPlayers(villager);
            }
            return;
        }

        // Getting a job (UNEMPLOYED -> JOB or JOB -> different JOB)
        if (existingComponent != null) {
            if (existingComponent.isNoQuestMarker()) {
                // Clear the no-quest marker so we retry with the new profession
                QuestAPI.LOGGER.debug("[VillagerJobMixin] Villager {} had no-quest marker, clearing for new profession {}",
                        villager.getUUID(), newProfId);
                villager.removeAttached(QuestAPIFabric.ENTITY_QUEST_ATTACHMENT);
            } else {
                if (existingComponent.hasAnyPlayerInteraction()) {
                    QuestAPI.LOGGER.debug("[VillagerJobMixin] Villager {} has player interactions, keeping existing quests",
                            villager.getUUID());
                    return;
                }

                QuestAPI.LOGGER.debug("[VillagerJobMixin] Villager {} has no player interactions, will reassign quest",
                        villager.getUUID());
            }
        }

        if (villager.level() instanceof ServerLevel serverLevel) {
            String biomeType = getBiomeType(villager);
            String profession = newProfId != null ? newProfId.toString() : "minecraft:none";

            QuestAPI.LOGGER.debug("[VillagerJobMixin] Looking for quest assignment: biome={}, profession={}",
                    biomeType, profession);

            QuestManager.debugPrintAll();

            ResourceLocation villagerId = ResourceLocation.parse("minecraft:villager");
            var allAssignments = EntityQuestAssignmentManager.getAssignmentsForEntity(villagerId);
            QuestAPI.LOGGER.debug("[VillagerJobMixin] Total villager assignments: {}", allAssignments.size());
            for (var a : allAssignments) {
                QuestAPI.LOGGER.debug("[VillagerJobMixin]   Assignment: entity={}, pool={}, chance={}, villagerData={}",
                        a.entityId(), a.questPools(), a.questChance(), a.villagerData());
            }

            Optional<EntityQuestAssignment> assignmentOpt = EntityQuestAssignmentManager.findVillagerAssignment(
                    biomeType, profession, serverLevel.getRandom());

            if (assignmentOpt.isPresent()) {
                EntityQuestAssignment assignment = assignmentOpt.get();
                QuestAPI.LOGGER.debug("[VillagerJobMixin] Found assignment: {} -> {}", assignment.entityId(), assignment.questPools());

                EntityQuestComponent newComponent = createComponentFromAssignment(villager, serverLevel, assignment);

                if (newComponent != null) {
                    villager.setAttached(QuestAPIFabric.ENTITY_QUEST_ATTACHMENT, newComponent);
                    QuestAPI.LOGGER.info("[VillagerJobMixin] Assigned quest tag {} to villager {} after job conversion to {}",
                            assignment.questPools(), villager.getUUID(), profession);

                    // Resync so the quest marker shows immediately, without needing to leave/return
                    QuestAPIFabric.forceResyncForNearbyPlayers(villager);
                } else {
                    QuestAPI.LOGGER.warn("[VillagerJobMixin] Failed to create component from assignment!");
                }
            } else {
                QuestAPI.LOGGER.debug("[VillagerJobMixin] No matching assignment found for villager (biome={}, profession={})",
                        biomeType, profession);

                if (!allAssignments.isEmpty()) {
                    villager.setAttached(QuestAPIFabric.ENTITY_QUEST_ATTACHMENT, EntityQuestComponent.createNoQuest());
                    QuestAPI.LOGGER.debug("[VillagerJobMixin] Marked villager {} as no-quest (chance failed or no match)",
                            villager.getUUID());

                    QuestAPIFabric.forceResyncForNearbyPlayers(villager);
                }
            }
        }
    }

    private String getBiomeType(Villager villager) {
        ResourceLocation typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType());
        String biome = typeId != null ? typeId.getPath() : "plains";
        QuestAPI.LOGGER.debug("[VillagerJobMixin] getBiomeType: {} -> {}", typeId, biome);
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
                QuestAPI.LOGGER.debug("[VillagerJobMixin] Assignment {} is chunk-restricted near {}, marking as no-quest",
                        restrictionKey, chunkPos);
                return EntityQuestComponent.createNoQuest();
            }
        }

        String questPoolRef = assignment.pickQuestPool(level.getRandom());

        QuestAPI.LOGGER.debug("[VillagerJobMixin] createComponentFromAssignment: {}", questPoolRef);

        if (questPoolRef.isEmpty()) {
            QuestAPI.LOGGER.warn("[VillagerJobMixin] quest_pool is empty!");
            return null;
        }

        // Legacy "tag:" prefix format
        if (questPoolRef.startsWith("tag:")) {
            questPoolRef = questPoolRef.substring(4);
            // tag:quest_api/farm -> quest_api:farm
            if (questPoolRef.contains("/") && !questPoolRef.contains(":")) {
                int slashIndex = questPoolRef.indexOf('/');
                questPoolRef = questPoolRef.substring(0, slashIndex) + ":" + questPoolRef.substring(slashIndex + 1);
            }
            QuestAPI.LOGGER.debug("[VillagerJobMixin] Converted legacy tag format to: {}", questPoolRef);
        }

        ResourceLocation tagId = ResourceLocation.parse(questPoolRef);
        QuestAPI.LOGGER.debug("[VillagerJobMixin] Creating component with tag ID: {}", tagId);

        EntityQuestComponent component = EntityQuestComponent.create(tagId,
                assignment.acceptQuestSoundOverride(), assignment.finishQuestSoundOverride());

        var pools = component.getAllQuestPools();
        if (pools.isEmpty()) {
            QuestAPI.LOGGER.error("[VillagerJobMixin] WARNING: Tag {} has no quest pools! " +
                    "Check that data/{}/tags/entity_quests/{}.json exists and references valid quest pool files.",
                    tagId, tagId.getNamespace(), tagId.getPath());
        } else {
            QuestAPI.LOGGER.debug("[VillagerJobMixin] Tag {} has {} quest pools", tagId, pools.size());
        }

        if (assignment.chunkRestrictionRadius().isPresent()) {
            ChunkAssignmentTracker.get(level).markAssigned(
                    assignment.restrictionKey(), new ChunkPos(villager.blockPosition()), villager.getUUID());
        }

        return component;
    }
}
