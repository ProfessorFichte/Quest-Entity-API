package com.qeapi.packs.rpgseriesquests;

import com.qeapi.datagen.QuestProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;

public class RpgSeriesQuestsProvider extends QuestProvider {

    public RpgSeriesQuestsProvider(PackOutput output) {
        super(output, "rpg_series_quests");
    }

    @Override
    protected void addQuests() {
        createPool("demo")
                .tier(1)
                    .quest("first_blood")
                        .name("First Blood")
                        .description("Defeat 3 zombies to prove this pack's own datagen pipeline works end to end.")
                        .task(entityKill(EntityType.ZOMBIE, 3))
                        .reward(experience(30))
                        .weight(100)
                        .add()
                .build();
    }
}
