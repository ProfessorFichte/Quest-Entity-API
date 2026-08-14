package com.qeapi.packs.villagerquests;

import com.qeapi.datagen.AssignmentProvider;
import net.minecraft.data.PackOutput;

public class VillagerQuestsAssignmentProvider extends AssignmentProvider {

    public VillagerQuestsAssignmentProvider(PackOutput output) {
        super(output, "villager_quests");
    }

    @Override
    protected void addAssignments() {
        assign("farmer", "minecraft:villager")
                .questPool("villager_quests:farmer")
                .villagerProfession("farmer")
                .add();

        assign("farmer_desert", "minecraft:villager")
                .questPool("villager_quests:farmer_desert")
                .villager("desert", "farmer")
                .add();

        assign("farmer_snow", "minecraft:villager")
                .questPool("villager_quests:farmer_snow")
                .villager("snow", "farmer")
                .add();

        assign("farmer_swamp", "minecraft:villager")
                .questPool("villager_quests:farmer_swamp")
                .villager("swamp", "farmer")
                .add();

        assign("farmer_jungle", "minecraft:villager")
                .questPool("villager_quests:farmer_jungle")
                .villager("jungle", "farmer")
                .add();
    }
}
