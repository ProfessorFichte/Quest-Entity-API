package com.qeapi.item;

import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

// Shared base item every inline quest_item definition (see QuestItemDefinition) resolves onto -
// the actual identity of a specific quest item is carried entirely by its CustomModelData, not by
// a separate registered Item per quest item.
public final class QuestItems {

    private QuestItems() {}

    public static final ResourceLocation QUEST_ITEM_ID = QuestEntityAPI.id("quest_item");
    public static final Item QUEST_ITEM = new Item(new Item.Properties());
}
