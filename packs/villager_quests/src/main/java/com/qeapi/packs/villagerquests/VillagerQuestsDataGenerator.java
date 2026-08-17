package com.qeapi.packs.villagerquests;

import com.qeapi.datagen.LangProvider;
import com.qeapi.datagen.QuestItemModelProvider;
import com.qeapi.registry.QERegistries;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

// only exists so Loom's runDatagen has a fabric-datagen entrypoint to call - this "mod" is never
// built into a real jar or installed, it just drives datagen for the villager_quests pack
public class VillagerQuestsDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        // the real quest_api mod isn't loaded here (this project only compiles against :common), so
        // its task/requirement/reward codecs never get registered unless we do it ourselves -
        // without this, encoding any quest that uses e.g. bring_item throws "Unknown task type"
        QERegistries.init();

        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider((FabricDataGenerator.Pack.Factory<VillagerQuestsProvider>) VillagerQuestsProvider::new);
        pack.addProvider((FabricDataGenerator.Pack.Factory<VillagerQuestsAssignmentProvider>) VillagerQuestsAssignmentProvider::new);
        pack.addProvider((FabricDataGenerator.Pack.Factory<LangProvider>) output ->
                new LangProvider(output, "villager_quests", false, new VillagerQuestsProvider(output)));
        pack.addProvider((FabricDataGenerator.Pack.Factory<QuestItemModelProvider>) output ->
                new QuestItemModelProvider(output, "villager_quests", new VillagerQuestsProvider(output)));
    }
}
