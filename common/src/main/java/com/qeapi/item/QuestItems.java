package com.qeapi.item;

import com.qeapi.QuestAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

// a quest item's actual identity is carried entirely by its CustomModelData, not by a separate registered Item per quest item
public final class QuestItems {

    private QuestItems() {}

    public static final ResourceLocation QUEST_ITEM_ID = QuestAPI.id("quest_item");
    public static final Item QUEST_ITEM = new Item(new Item.Properties());
}
