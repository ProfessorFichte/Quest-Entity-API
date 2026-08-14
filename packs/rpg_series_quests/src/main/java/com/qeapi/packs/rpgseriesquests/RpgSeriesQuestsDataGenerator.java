package com.qeapi.packs.rpgseriesquests;

import com.qeapi.datagen.LangProvider;
import com.qeapi.datagen.QuestItemModelProvider;
import com.qeapi.registry.QERegistries;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

// only exists so Loom's runDatagen has a fabric-datagen entrypoint to call - this "mod" is never
// built into a real jar or installed, it just drives datagen for the rpg_series_quests pack
public class RpgSeriesQuestsDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        // the real qe_api mod isn't loaded here (this project only compiles against :common), so
        // its task/requirement/reward codecs never get registered unless we do it ourselves -
        // without this, encoding any quest that uses e.g. entity_kill throws "Unknown task type"
        QERegistries.init();

        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider((FabricDataGenerator.Pack.Factory<RpgSeriesQuestsProvider>) RpgSeriesQuestsProvider::new);
        pack.addProvider((FabricDataGenerator.Pack.Factory<LangProvider>) output ->
                new LangProvider(output, "rpg_series_quests", new RpgSeriesQuestsProvider(output)));
        pack.addProvider((FabricDataGenerator.Pack.Factory<QuestItemModelProvider>) output ->
                new QuestItemModelProvider(output, "rpg_series_quests", new RpgSeriesQuestsProvider(output)));
    }
}
