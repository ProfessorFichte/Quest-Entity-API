package com.qeapi.datagen;

import net.minecraft.data.PackOutput;

// attaches a quest pool tag to an entity type so it's offered without any code on the entity itself; pools referenced here come from ExampleQuestProvider
public class ExampleAssignmentProvider extends AssignmentProvider {

    public ExampleAssignmentProvider(PackOutput output) {
        super(output, "quest_api");
    }

    @Override
    protected void addAssignments() {
        assign("villager_generic", "minecraft:villager")
                .questPool("quest_api:harvest_test")
                .questChance(0.25)
                .add();

        assign("villager_farmer", "minecraft:villager")
                .questPool("quest_api:brew_master")
                .villagerProfession("farmer")
                .add();

        // chunk_restriction caps this to one villager per 2 chunks holding the assignment
        assign("villager_desert_librarian", "minecraft:villager")
                .questPool("quest_api:find_mineshaft_map", "quest_api:angler")
                .villager("desert", "librarian")
                .questChance(0.5)
                .chunkRestrictionRadius(2)
                .add();
    }
}
