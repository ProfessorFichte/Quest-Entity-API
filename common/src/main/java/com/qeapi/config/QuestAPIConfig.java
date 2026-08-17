package com.qeapi.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import com.qeapi.QuestAPI;

@Config(name = QuestAPI.MOD_ID)
public class QuestAPIConfig implements ConfigData {

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

    @Comment("Show the quest giver's coordinates in the Active Quests screen. Always shown in creative mode regardless of this setting.")
    @ConfigEntry.Gui.Tooltip
    public boolean show_quest_coordinates = true;

    @Comment("Spawn a colorful firework burst on the player when they claim quest rewards.")
    @ConfigEntry.Gui.Tooltip
    public boolean finish_quest_fireworks_enabled = true;

    @Comment("Reveal a quest's description one character at a time the first time its detail pane is opened in a client session.")
    @ConfigEntry.Gui.Tooltip
    public boolean quest_description_typewriter_enabled = true;

    @Comment("Milliseconds per revealed character for the quest description typewriter effect.")
    @ConfigEntry.Gui.Tooltip
    public int quest_description_typewriter_speed_ms = 30;

    @Comment("Show all quest tiers in the list at once, with not-yet-unlocked ones marked as locked. Turn off to reveal each tier only after completing the previous one.")
    @ConfigEntry.Gui.Tooltip
    public boolean show_all_quests = true;

    @Comment("Show short fading HUD messages above the hotbar for quest events (e.g. all tasks completed, hit a quest giver on cooldown) instead of posting them in chat.")
    @ConfigEntry.Gui.Tooltip
    public boolean hud_messages_enabled = true;

    @Comment("Default sound played when accepting a quest. Overridden by an entity assignment's accept_quest_sound_override, which is in turn overridden by a quest's own accept_quest_sound_override.")
    @ConfigEntry.Gui.Tooltip
    public String accept_quest_sound = "minecraft:block.note_block.chime";

    @Comment("Default sound played when claiming quest rewards. Overridden by an entity assignment's finish_quest_sound_override, which is in turn overridden by a quest's own finish_quest_sound_override.")
    @ConfigEntry.Gui.Tooltip
    public String finish_quest_sound = "minecraft:entity.player.levelup";

    public static void register() {
        AutoConfig.register(QuestAPIConfig.class, JanksonConfigSerializer::new);
    }

    public static QuestAPIConfig get() {
        return AutoConfig.getConfigHolder(QuestAPIConfig.class).getConfig();
    }
}
