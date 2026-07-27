package com.qeapi.datagen;

import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

// Generates assets/<modId>/lang/en_us.json - every fixed GUI/task/requirement/reward translation
// the mod ships, plus every quest name/description registered via QuestBuilder's literal-text
// .name(String)/.description(String) overloads. Replaces a hand-maintained lang file so missing
// translations are caught at datagen time instead of showing raw keys in-game.
public class LangProvider implements DataProvider {

    private final PackOutput output;
    private final String modId;
    private final List<QuestProvider> questProviders;

    public LangProvider(PackOutput output, String modId, QuestProvider... questProviders) {
        this.output = output;
        this.modId = modId;
        this.questProviders = List.of(questProviders);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        LangEntries.clear();
        addStaticTranslations();

        // Re-run quest collection here (rather than relying on ExampleQuestProvider's own run()
        // having already executed) so this doesn't depend on unspecified ordering between
        // separately registered DataProviders - the resulting QuestPools are discarded, only
        // the LangEntries side effect from .name(String)/.description(String) calls matters.
        for (QuestProvider questProvider : questProviders) {
            questProvider.collectQuests();
        }

        JsonObject json = new JsonObject();
        new TreeMap<>(LangEntries.getAll()).forEach(json::addProperty);

        Path path = output.getOutputFolder()
                .resolve("assets")
                .resolve(modId)
                .resolve("lang")
                .resolve("en_us.json");

        return DataProvider.saveStable(cache, json, path);
    }

    private void addStaticTranslations() {
        // GUI
        LangEntries.add("gui.qe_api.quest_screen.title", "Quests");
        LangEntries.add("gui.qe_api.accept", "Accept");
        LangEntries.add("gui.qe_api.dismiss", "Dismiss");
        LangEntries.add("gui.qe_api.claim", "Claim Rewards");
        LangEntries.add("gui.qe_api.tier", "Tier %d");
        LangEntries.add("gui.qe_api.tasks", "Tasks:");
        LangEntries.add("gui.qe_api.rewards", "Rewards:");
        LangEntries.add("gui.qe_api.choose_rewards", "Choose %d/%d of %d:");
        LangEntries.add("gui.qe_api.choose_quest_path", "Choose a future Quest Path");
        LangEntries.add("gui.qe_api.requirements", "Requirements:");
        LangEntries.add("gui.qe_api.progress", "Progress: %s/%s");
        LangEntries.add("gui.qe_api.completed", "Completed!");
        LangEntries.add("gui.qe_api.no_quests", "No quests available");
        LangEntries.add("gui.qe_api.quest_active", "Quest Active");
        LangEntries.add("gui.qe_api.all_completed", "All quests completed!");
        LangEntries.add("gui.qe_api.effect_duration", "Duration: %s");
        LangEntries.add("gui.qe_api.effect_level", "Level: %d");
        LangEntries.add("gui.qe_api.entity_rotating", "Showing %1$d of %2$d possible targets");
        LangEntries.add("gui.qe_api.loot_table_reward", "Loot Table Reward");
        LangEntries.add("gui.qe_api.pick_item", "Pick an item:");
        LangEntries.add("gui.qe_api.back", "Back");
        LangEntries.add("gui.qe_api.no_valid_items", "You don't have any valid item for this.");

        // Tasks
        LangEntries.add("task.qe_api.entity_kill", "Defeat {kill_amount} {entity_name} ({current_kills}/{kill_amount})");
        LangEntries.add("task.qe_api.entity_kill.default", "Defeat %s entities");
        LangEntries.add("task.qe_api.entity_kill_dynamic", "Defeat %1$d %2$s (%3$d/%4$d)");
        LangEntries.add("task.qe_api.entity_kill.in_structure", "Must be within: %s");
        LangEntries.add("task.qe_api.entity_kill.in_biome", "Must be in biome: %s");
        LangEntries.add("task.qe_api.entity_kill.in_dimension", "Must be in dimension: %s");
        LangEntries.add("task.qe_api.entity_kill.damage_types", "Must be killed by:");
        LangEntries.add("task.qe_api.find_structure", "Discover {structure_name}");
        LangEntries.add("task.qe_api.find_structure.default", "Find a structure");
        LangEntries.add("task.qe_api.bring_item", "Bring {item_amount}x {item_name}");
        LangEntries.add("task.qe_api.bring_item.default", "Deliver items");
        LangEntries.add("task.qe_api.blocks_traveled", "Travel {target_distance} blocks ({current_distance}/{target_distance})");
        LangEntries.add("task.qe_api.blocks_traveled.default", "Travel %s blocks");
        LangEntries.add("task.qe_api.item_used", "Use {item_name} {use_amount} times ({current_uses}/{use_amount})");
        LangEntries.add("task.qe_api.item_used.default", "Use an item");
        LangEntries.add("task.qe_api.brew_potion", "Brew {brew_amount}x {potion_name} ({current_brews}/{brew_amount})");
        LangEntries.add("task.qe_api.brew_potion.default", "Brew a potion");
        LangEntries.add("task.qe_api.mine_block", "Mine {mine_amount}x {block_name} ({current_mined}/{mine_amount})");
        LangEntries.add("task.qe_api.mine_block.default", "Mine a block");
        LangEntries.add("task.qe_api.spell_cast", "Cast {spell_name} {cast_amount} times ({current_casts}/{cast_amount})");
        LangEntries.add("task.qe_api.spell_cast.default", "Cast a spell");
        LangEntries.add("task.qe_api.spell_cast_dynamic", "Cast %2$s %1$d times (%3$d/%4$d)");
        LangEntries.add("task.qe_api.entity_kill.in_spell_id", "Must be killed with spell: %s");
        LangEntries.add("task.qe_api.entity_kill.in_spell_pool", "Must be killed with a spell from: %s");
        LangEntries.add("task.qe_api.entity_kill.in_spell_school", "Must be killed with a spell of school: %s");
        LangEntries.add("task.qe_api.entity_kill.min_power_level", "Must be power level %d or higher");

        // Requirements
        LangEntries.add("requirement.qe_api.has_advancement", "Requires advancement: %s");
        LangEntries.add("requirement.qe_api.has_advancement.failure", "You need to unlock the advancement: %s");
        LangEntries.add("requirement.qe_api.has_level", "Requires level %d");
        LangEntries.add("requirement.qe_api.has_level.failure", "You need to be at least level %d");
        LangEntries.add("requirement.qe_api.has_item", "Requires %dx %s");
        LangEntries.add("requirement.qe_api.has_item.failure", "You need %dx %s");

        // Rewards
        LangEntries.add("reward.qe_api.experience", "%d Experience Points");
        LangEntries.add("reward.qe_api.item", "%dx %s");
        LangEntries.add("reward.qe_api.status_effect", "%s for %s");
        LangEntries.add("reward.qe_api.loot_table", "Random loot from: %s");
        LangEntries.add("reward.qe_api.loot_table_formatted", "Random rewards from: %s");
        LangEntries.add("reward.qe_api.command", "%s");
        LangEntries.add("reward.qe_api.advancement", "Grants advancement: %s");
        LangEntries.add("reward.qe_api.skill_experience", "%d Skill Experience (%s)");
        LangEntries.add("reward.qe_api.skill_level", "%d Skill Level(s) (%s)");
        LangEntries.add("reward.qe_api.spell_scroll", "%dx Spell Scroll (%s)");
        LangEntries.add("reward.qe_api.enchant_randomly", "Randomly enchant an item (up to level %d)");
        LangEntries.add("reward.qe_api.enchant_specific", "Enchant an item with %2$s %1$d");
        LangEntries.add("reward.qe_api.repair_item", "Fully repair an item's durability");
        LangEntries.add("reward.qe_api.spell_bind", "Bind spell %s to an item");
        LangEntries.add("reward.qe_api.increase_power_level", "Increase an item's power level by %d");
        LangEntries.add("reward.qe_api.increase_enchant_slots", "Grant an item %d extra enchantment slot(s)");
        LangEntries.add("reward.qe_api.set_quest_group", "Choose the %s path");

        // Messages
        LangEntries.add("message.qe_api.quest_accepted", "Quest accepted!");
        LangEntries.add("message.qe_api.quest_dismissed", "Quest dismissed.");
        LangEntries.add("message.qe_api.quest_complete", "Quest complete: %s! Return to claim your rewards.");
        LangEntries.add("message.qe_api.quest_cancelled", "Your quest has been cancelled because the quest giver is no longer available.");
        LangEntries.add("message.qe_api.quest_cancelled_hit", "Your quest has been cancelled because you attacked the quest giver.");
        LangEntries.add("message.qe_api.hit_quest_giver", "Attacking a quest giver puts them on cooldown - be gentle!");
        LangEntries.add("message.qe_api.rewards_claimed", "Rewards claimed!");
        LangEntries.add("message.qe_api.requirements_not_met", "You don't meet the requirements for this quest.");
        LangEntries.add("message.qe_api.already_has_quest", "You already have an active quest with this entity.");
        LangEntries.add("message.qe_api.already_completed", "You have already completed this quest.");
        LangEntries.add("message.qe_api.no_active_quest", "You don't have an active quest to claim.");
        LangEntries.add("message.qe_api.quest_not_complete", "You haven't completed all the tasks yet.");
        LangEntries.add("message.qe_api.no_quests_available", "This entity has no quests available.");
        LangEntries.add("message.qe_api.cooldown_active", "This quest giver is on cooldown for %d more minute(s).");
    }

    @Override
    public String getName() {
        return "Quest Entity API Lang: " + modId;
    }
}
