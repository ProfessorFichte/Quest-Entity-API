package com.qeapi.fabric;

import com.qeapi.datagen.ExampleQuestProvider;
import com.qeapi.datagen.LangProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

// `./gradlew runDatagen` regenerates data/qe_api/entity_quest, tags/entity_quests,
// and assets/qe_api/lang/en_us.json via the providers registered here.
public class ModDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider((FabricDataGenerator.Pack.Factory<ExampleQuestProvider>) ExampleQuestProvider::new);
        pack.addProvider((FabricDataGenerator.Pack.Factory<LangProvider>) output ->
                new LangProvider(output, "qe_api", new ExampleQuestProvider(output)));
    }
}
