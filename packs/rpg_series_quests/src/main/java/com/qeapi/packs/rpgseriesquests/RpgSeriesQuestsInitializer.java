package com.qeapi.packs.rpgseriesquests;

import com.qeapi.item.QuestItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

// QuestItemModelProvider touches QuestItems.QUEST_ITEM, which constructs an Item and needs the
// item registry unfrozen to do it - the real qe_api mod registers it from its own main entrypoint
// (QuestEntityAPIFabric.init), but that mod isn't loaded here, so this has to happen the same way,
// at the same "main" lifecycle stage, or QuestItems' class-init crashes once the registry freezes
public class RpgSeriesQuestsInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM, QuestItems.QUEST_ITEM_ID, QuestItems.QUEST_ITEM);
    }
}
