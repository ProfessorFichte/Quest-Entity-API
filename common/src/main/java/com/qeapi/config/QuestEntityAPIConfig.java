package com.qeapi.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import com.qeapi.QuestEntityAPI;

@Config(name = QuestEntityAPI.MOD_ID)
public class QuestEntityAPIConfig implements ConfigData {

    @Comment("Villagers gain trading-level experience when a player claims quest rewards from them.")
    @ConfigEntry.Gui.Tooltip
    public boolean villager_trade_xp_enabled = true;

    @Comment("Trading XP granted per quest tier on claim (tier * this value).")
    @ConfigEntry.Gui.Tooltip
    public int villager_trade_xp_per_tier = 5;

    @Comment("Attacking a quest giver cancels your active quest with them and puts them on an interaction cooldown.")
    @ConfigEntry.Gui.Tooltip
    public boolean hit_cooldown_enabled = true;

    @Comment("How many minutes a quest giver refuses interaction after being hit.")
    @ConfigEntry.Gui.Tooltip
    public int hit_cooldown_minutes = 5;

    public static void register() {
        AutoConfig.register(QuestEntityAPIConfig.class, JanksonConfigSerializer::new);
    }

    public static QuestEntityAPIConfig get() {
        return AutoConfig.getConfigHolder(QuestEntityAPIConfig.class).getConfig();
    }
}
