package com.qeapi.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// groups multiple quest IDs into a pool an entity can offer; values can reference other tags with "#"
public class EntityQuestTag {
    private final ResourceLocation id;
    private final List<ResourceLocation> questIds;
    private final List<ResourceLocation> referencedTags;

    public static final Codec<EntityQuestTag> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(EntityQuestTag::getId),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("values", List.of())
                            .forGetter(EntityQuestTag::getQuestIds),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of())
                            .forGetter(EntityQuestTag::getReferencedTags)
            ).apply(instance, EntityQuestTag::new)
    );

    public EntityQuestTag(ResourceLocation id, List<ResourceLocation> questIds, List<ResourceLocation> referencedTags) {
        this.id = id;
        this.questIds = new ArrayList<>(questIds);
        this.referencedTags = new ArrayList<>(referencedTags);
    }

    public ResourceLocation getId() {
        return id;
    }

    public List<ResourceLocation> getQuestIds() {
        return Collections.unmodifiableList(questIds);
    }

    public List<ResourceLocation> getReferencedTags() {
        return Collections.unmodifiableList(referencedTags);
    }

    public boolean isEmpty() {
        return questIds.isEmpty() && referencedTags.isEmpty();
    }

    @Override
    public String toString() {
        return "EntityQuestTag{id=" + id + ", quests=" + questIds.size() + ", tags=" + referencedTags.size() + "}";
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private final List<ResourceLocation> questIds = new ArrayList<>();
        private final List<ResourceLocation> referencedTags = new ArrayList<>();

        public Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder addQuest(ResourceLocation questId) {
            questIds.add(questId);
            return this;
        }

        public Builder addQuest(String questId) {
            return addQuest(ResourceLocation.parse(questId));
        }

        public Builder addTag(ResourceLocation tagId) {
            referencedTags.add(tagId);
            return this;
        }

        public Builder addTag(String tagId) {
            return addTag(ResourceLocation.parse(tagId));
        }

        public EntityQuestTag build() {
            return new EntityQuestTag(id, questIds, referencedTags);
        }
    }
}
