package com.qeapi.datagen;

import com.qeapi.item.QuestItemDefinition;
import com.qeapi.quest.reward.ItemReward;
import com.qeapi.quest.reward.RewardChoicePool;
import com.qeapi.quest.reward.SpellScrollReward;
import com.qeapi.quest.reward.function.SetEnchantmentsFunction;
import com.qeapi.quest.reward.function.SetLoreFunction;
import com.qeapi.quest.reward.function.SetNameFunction;
import com.qeapi.quest.reward.function.SetPowerLevelFunction;
import com.qeapi.quest.task.ApplyStatusEffectTask;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.ConditionalDropTask;
import com.qeapi.quest.task.DealDamageAmountTask;
import com.qeapi.quest.task.DoHealingAmountTask;
import com.qeapi.quest.task.EntityKillTask;
import com.qeapi.quest.task.QuestLineChoiceTask;
import com.qeapi.quest.task.VisitBiomeTask;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;
import java.util.Optional;

// wizard_paths and epic_monster_story stay as their own larger pools since they demonstrate branching mechanics that only exist across several connected quests
public class ExampleQuestProvider extends QuestProvider {

    public ExampleQuestProvider(PackOutput output) {
        super(output, "quest_api");
    }

    @Override
    protected void addQuests() {
        createPool("apprentice_basics")
                .tier(1)
                    .quest("test")
                        .name("Apprentice Basics")
                        .description("Test - eat a golden apple, brew a potion, mine {amount} oak logs, and travel {amount_1} blocks.")
                        .task(itemUsed(Items.GOLDEN_APPLE, 5))
                        .task(brewPotion(Potions.SWIFTNESS.value(), 3))
                        .task(mineBlock(Blocks.OAK_LOG, 3))
                        .task(blocksTraveled(500))
                        .reward(item(Items.ENCHANTED_GOLDEN_APPLE, 1))
                        .reward(statusEffect(MobEffects.REGENERATION, 60 * 20, 2))
                        .reward(experience(150))
                        .rewardChoicePool(1,
                                item(Items.DIAMOND, 2),
                                item(Items.EMERALD, 6),
                                statusEffect(MobEffects.LUCK, 300 * 20, 0))
                        .acceptQuestSoundOverride(ResourceLocation.withDefaultNamespace("entity.experience_orb.pickup"))
                        .finishQuestSoundOverride(ResourceLocation.withDefaultNamespace("ui.toast.challenge_complete"))
                        .weight(100)
                        .add()
                .build();

        createPool("craft_and_repair")
                .tier(1)
                    .quest("test")
                        .name("Craft and Repair")
                        .description("Test - craft a crafting table, smith an item, repair one at an anvil, and enchant one.")
                        .task(crafting(Items.CRAFTING_TABLE, 1))
                        .task(smithing(1))
                        .task(anvilRepair(1))
                        .task(enchanting(1))
                        .reward(item(Items.LAPIS_LAZULI, 8))
                        .reward(enchantRandomly(2))
                        .reward(enchantSpecific("minecraft:sharpness", 3))
                        .reward(experience(100))
                        .weight(100)
                        .add()
                .build();

        createPool("field_skills")
                .tier(1)
                    .quest("test")
                        .name("Field Skills")
                        .description("Test - catch {amount_0} fish, harvest {amount_1} crops, poison another entity, and heal 50 health.")
                        .task(fishing(5))
                        .task(harvestCrops(10))
                        .task(ApplyStatusEffectTask.builder()
                                .effectId(ResourceLocation.withDefaultNamespace("poison"))
                                .amount(3)
                                .target(ApplyStatusEffectTask.Target.OTHERS)
                                .build())
                        .task(DoHealingAmountTask.builder()
                                .amount(50)
                                .healTarget(DoHealingAmountTask.HealTarget.EITHER)
                                .build())
                        .reward(item(Items.BREAD, 8))
                        .reward(experience(100))
                        .weight(100)
                        .add()
                .build();

        createPool("monster_hunter_trials")
                .tier(1)
                    .quest("test")
                        .name("Monster Hunter Trials")
                        .description("Test - kill a zombie while holding a diamond sword (target needs 15+ max health), then deal 100 total damage to zombies.")
                        .requirement(hasLevel(20))
                        .requirement(hasItem(Items.DIAMOND_SWORD, 1))
                        .requirement(hasAdvancement("minecraft:adventure/kill_a_mob"))
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(1)
                                .requiredItemId(Items.DIAMOND_SWORD)
                                .minAttributeValue(15.0)
                                .build())
                        .task(DealDamageAmountTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(100)
                                .build())
                        .reward(ItemReward.builder().item(Items.DIAMOND_SWORD)
                                .addFunction(new SetEnchantmentsFunction(List.of(
                                        new SetEnchantmentsFunction.EnchantmentEntry(
                                                Enchantments.SHARPNESS.location(), 5),
                                        new SetEnchantmentsFunction.EnchantmentEntry(
                                                Enchantments.UNBREAKING.location(), 3),
                                        new SetEnchantmentsFunction.EnchantmentEntry(
                                                Enchantments.FIRE_ASPECT.location(), 2)
                                ), true))
                                .addFunction(new SetNameFunction(Component.literal("Legendary Blade")
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)))
                                .addFunction(new SetLoreFunction(List.of(
                                        Component.literal("Forged in dragon fire").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                                        Component.literal("The blade of heroes").withStyle(ChatFormatting.DARK_PURPLE)
                                )))
                                .build())
                        .reward(command(
                                "title {player} title {\"text\":\"Wither Hunter!\",\"color\":\"dark_purple\"}",
                                "Title: Wither Hunter"))
                        .reward(lootTable("minecraft:chests/simple_dungeon"))
                        .reward(experience(500))
                        .weight(100)
                        .add()
                .build();

        // each filter combination gets its own task so it stays easy to read
        createPool("filter_showcase")
                .tier(1)
                    .quest("test")
                        .name("Filter Showcase")
                        .description("Test - entity_kill filters: undead in a forest biome, piglins in the Nether, a skeleton from range, a zombie via explosion.")
                        .task(EntityKillTask.builder()
                                .entityTag(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("undead")))
                                .amount(2)
                                .inBiomeTag(TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_forest")))
                                .build())
                        .task(entityKillInDimension(EntityType.PIGLIN, 5, "minecraft:the_nether"))
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("skeleton"))
                                .amount(1)
                                .minRange(40)
                                .build())
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(3)
                                .damageTypes(ResourceLocation.withDefaultNamespace("explosion"),
                                        ResourceLocation.withDefaultNamespace("player_explosion"))
                                .build())
                        .reward(advancement("minecraft:adventure/kill_a_mob"))
                        .reward(experience(300))
                        .weight(100)
                        .add()
                .build();

        createPool("dungeon_delver")
                .tier(1)
                    .quest("test")
                        .name("Dungeon Delver")
                        .description("{entity_name} heard rumors of a mineshaft at least power level 2 nearby - bring back proof you found it.")
                        .requirement(hasLevel(3))
                        .requiredMod("dungeon_difficulty")
                        .task(findStructureWithMap("minecraft:mineshaft", 10000).withMinPowerLevel(2))
                        .reward(item(Items.IRON_PICKAXE, 1))
                        .reward(item(Items.TORCH, 32))
                        .reward(mapToStructure("minecraft:mineshaft", 10000))
                        .reward(teleportToStructure("minecraft:mineshaft", 10000))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("biome_tracker")
                .tier(1)
                    .quest("test")
                        .name("Biome Tracker")
                        .description("Test - bring a compass, then visit both the Desert and the Jungle.")
                        .task(bringItem(Items.COMPASS, 1))
                        .task(VisitBiomeTask.builder()
                                .biomeIds("minecraft:desert", "minecraft:jungle")
                                .amount(2)
                                .build())
                        .reward(experience(100))
                        .rewardChoicePool(1,
                                teleportToBiome("minecraft:desert"),
                                teleportToCoordinates(0, 100, 0))
                        .weight(100)
                        .add()
                .build();

        // to see the "only one" resolution behavior, spawn two Allays before accepting - only the nearest gets the floating icon
        createPool("allay_delivery")
                .tier(1)
                    .quest("test")
                        .name("Cake for the Allay")
                        .description("Test - deliver_item targeting: resolves to exactly one nearby Allay at accept time, even with several around; bring it a cake.")
                        .task(deliverItem(Items.CAKE, 1, EntityType.ALLAY))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("raid_hero")
                .tier(1)
                    .quest("test")
                        .name("Raid Hero")
                        .description("Test - win a raid at raid omen level 2 or higher.")
                        .task(raidComplete(1, 2))
                        .reward(experience(300))
                        .weight(100)
                        .add()
                .build();

        createPool("trial_spawner_hero")
                .tier(1)
                    .quest("test")
                        .name("Trial Spawner Hero")
                        .description("Test - clear an Ominous trial spawner's wave.")
                        .task(trialSpawnerComplete(1, true))
                        .reward(experience(200))
                        .weight(100)
                        .add()
                .build();

        QuestItemDefinition testHammer = questItem(
                ResourceLocation.fromNamespaceAndPath("quest_api", "item/test_hammer"),
                1001, "Smith's Test Hammer");
        QuestItemDefinition piglinTrophy = questItem(
                ResourceLocation.fromNamespaceAndPath("quest_api", "item/piglin_trophy"),
                1002, "Piglin Bastion Trophy");
        QuestItemDefinition fortressRelic = questItem(
                ResourceLocation.fromNamespaceAndPath("quest_api", "item/fortress_relic"),
                1003, "Fortress Relic");
        QuestItemDefinition minersLocket = questItem(
                ResourceLocation.fromNamespaceAndPath("quest_api", "item/miners_locket"),
                1004, "Miner's Locket");
        QuestItemDefinition anglersCharm = questItem(
                ResourceLocation.fromNamespaceAndPath("quest_api", "item/anglers_charm"),
                1005, "Angler's Charm");
        QuestItemDefinition farmersBounty = questItem(
                ResourceLocation.fromNamespaceAndPath("quest_api", "item/farmers_bounty"),
                1006, "Farmer's Bounty");

        createPool("treasure_hunter_trials")
                .tier(1)
                    .quest("test")
                        .name("Treasure Hunter Trials")
                        .description("Test - conditional_drop targeting: a pillager outpost hammer (mob_drop_filters), a bastion trophy (loot_table_ids), a fortress relic (chest_in_structures), a miner's locket (loot_table_ids on a block table), an angler's charm (loot_table_ids on a fishing table), a farmer's bounty (loot_table_ids on a crop table).")
                        .task(ConditionalDropTask.builder()
                                .questItem(testHammer)
                                .amount(1)
                                .entityId(ResourceLocation.withDefaultNamespace("pillager"))
                                .inStructure("minecraft:pillager_outpost")
                                .mobDropChance(0.5)
                                .build())
                        .task(bringItemQuestItem(testHammer, 1))
                        .task(ConditionalDropTask.builder()
                                .questItem(piglinTrophy)
                                .amount(1)
                                .lootTableIds(
                                        "minecraft:chests/bastion_treasure",
                                        "minecraft:chests/bastion_other",
                                        "minecraft:chests/bastion_bridge",
                                        "minecraft:chests/bastion_hoglin_stable")
                                .chestDropChance(0.2)
                                .build())
                        .task(bringItemQuestItem(piglinTrophy, 1))
                        .task(ConditionalDropTask.builder()
                                .questItem(fortressRelic)
                                .amount(1)
                                .chestInStructures("minecraft:nether_fortress")
                                .chestDropChance(0.2)
                                .build())
                        .task(bringItemQuestItem(fortressRelic, 1))
                        .task(ConditionalDropTask.builder()
                                .questItem(minersLocket)
                                .amount(1)
                                .lootTableIds("minecraft:blocks/diamond_ore")
                                .chestDropChance(0.2)
                                .build())
                        .task(bringItemQuestItem(minersLocket, 1))
                        .task(ConditionalDropTask.builder()
                                .questItem(anglersCharm)
                                .amount(1)
                                .lootTableIds("minecraft:gameplay/fishing/treasure")
                                .chestDropChance(0.2)
                                .build())
                        .task(bringItemQuestItem(anglersCharm, 1))
                        .task(ConditionalDropTask.builder()
                                .questItem(farmersBounty)
                                .amount(1)
                                .lootTableIds("minecraft:blocks/wheat")
                                .chestDropChance(0.2)
                                .build())
                        .task(bringItemQuestItem(farmersBounty, 1))
                        .reward(experience(300))
                        .weight(100)
                        .add()
                .build();

        // water/earth are gated behind required_mod on both the reward option and the tier-2 quest itself, in case a player already had that group set
        createPool("wizard_paths")
                .followOrder(true)

                .tier(1)
                    .quest("wizard_root")
                        .name("Wizard Path Root")
                        .description("Every apprentice must choose a discipline. Bring us 3 books to begin your training, then pick your path.")
                        .task(bringItem(Items.BOOK, 3))
                        .reward(experience(100))
                        .rewardChoicePoolOptions(1,
                                RewardChoicePool.Option.of(setQuestGroup("fire",
                                        ResourceLocation.fromNamespaceAndPath("quest_api", "textures/gui/quest_paths/fire.png"))),
                                RewardChoicePool.Option.of(setQuestGroup("frost",
                                        ResourceLocation.fromNamespaceAndPath("quest_api", "textures/gui/quest_paths/frost.png"))),
                                new RewardChoicePool.Option(setQuestGroup("water",
                                        ResourceLocation.fromNamespaceAndPath("quest_api", "textures/gui/quest_paths/water.png")),
                                        Optional.of("elemental_wizards_rpg")),
                                new RewardChoicePool.Option(setQuestGroup("earth",
                                        ResourceLocation.fromNamespaceAndPath("quest_api", "textures/gui/quest_paths/earth.png")),
                                        Optional.of("elemental_wizards_rpg")))
                        .weight(100)
                        .add()

                .tier(2)
                    .quest("fire_path")
                        .name("Fire Path")
                        .description("Quest Path Testing")
                        .questGroup("fire")
                        .task(bringItem(Items.BLAZE_POWDER, 3))
                        .reward(item(Items.FIRE_CHARGE, 8))
                        .weight(100)
                        .add()
                    .quest("frost_path")
                        .name("Frost Path")
                        .description("Quest Path Testing")
                        .questGroup("frost")
                        .task(bringItem(Items.ICE, 3))
                        .reward(item(Items.PACKED_ICE, 8))
                        .weight(100)
                        .add()
                    .quest("water_path")
                        .name("Water Path")
                        .description("Quest Path Testing")
                        .questGroup("water")
                        .requiredMod("elemental_wizards_rpg")
                        .task(bringItem(Items.PRISMARINE_CRYSTALS, 3))
                        .reward(item(Items.PRISMARINE_SHARD, 8))
                        .weight(100)
                        .add()
                    .quest("earth_path")
                        .name("Earth Path")
                        .description("Quest Path Testing")
                        .questGroup("earth")
                        .requiredMod("elemental_wizards_rpg")
                        .task(bringItem(Items.EMERALD, 3))
                        .reward(item(Items.RAW_IRON, 8))
                        .weight(100)
                        .add()

                .build();

        // these are quest/task-level mechanics, not task types, so each gets its own minimal pool rather than muddying another one
        createPool("ordered_tasks_test")
                .tier(1)
                    .quest("test")
                        .name("Ordered Tasks")
                        .description("Test - mine {amount} oak logs, then kill {amount_1} zombies, then bring {amount_2} iron ingot.")
                        .ordered()
                        .task(mineBlock(Blocks.OAK_LOG, 3))
                        .task(entityKill(EntityType.ZOMBIE, 2))
                        .task(bringItem(Items.IRON_INGOT, 1))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("task_choice_group_test")
                .tier(1)
                    .quest("test")
                        .name("Either Ingot")
                        .description("Test - bring {amount} iron ingot OR {amount_1} gold ingot.")
                        .task(BringItemTask.builder().item(Items.IRON_INGOT).amount(1).choiceGroup("ingot").build())
                        .task(BringItemTask.builder().item(Items.GOLD_INGOT).amount(1).choiceGroup("ingot").build())
                        .reward(experience(100))
                        .weight(100)
                        .add()
                .build();

        // with show_all_quests on, tiers 2-3 show with the locked marker + tooltip until the lower tier is completed
        createPool("locked_tier_test")
                .followOrder(true)
                .tier(1)
                    .quest("step_one")
                        .name("Locked Test - Step 1")
                        .description("Test - bring {amount} oak logs to unlock step 2.")
                        .task(bringItem(Items.OAK_LOG, 4))
                        .reward(experience(50))
                        .weight(100)
                        .add()
                .tier(2)
                    .quest("step_two")
                        .name("Locked Test - Step 2")
                        .description("Test - bring {amount} iron ingots to unlock step 3.")
                        .task(bringItem(Items.IRON_INGOT, 4))
                        .reward(experience(75))
                        .weight(100)
                        .add()
                .tier(3)
                    .quest("step_three")
                        .name("Locked Test - Step 3")
                        .description("Test - bring {amount} diamonds.")
                        .task(bringItem(Items.DIAMOND, 2))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        // step quests use follow_quest_order(false) - gated by the questLine filter instead, since tier-gating them behind the root would deadlock (root only completes once every step is done)
        createPool("epic_monster_story")
                .tier(1)
                    .quest("adventurers_task")
                        .name("Adventurer's First Task")
                        .description("Prove yourself worthy before the trials ahead - thin out this local zombie horde.")
                        .task(entityKill(EntityType.ZOMBIE, 5))
                        .reward(experience(100))
                        .weight(100)
                        .add()

                .tier(2)
                    .quest("quest_lines_root")
                        .name("Epic Monster Story Quest Lines")
                        .description("Two legendary foes await. Choose which story you wish to pursue - your choice unlocks that story's trials below.")
                        .task(QuestLineChoiceTask.builder()
                                .line("wither_story",
                                        Component.literal("Trial of the Wither"),
                                        Component.literal("Hunt down the Wither and prove your mastery over the undead."),
                                        ResourceLocation.fromNamespaceAndPath("quest_api", "textures/gui/quest_tasks/find_structure_default.png"))
                                .line("ender_dragon_story",
                                        Component.literal("Trial of the Ender Dragon"),
                                        Component.literal("Journey to the End and slay the dragon itself."),
                                        ResourceLocation.fromNamespaceAndPath("quest_api", "textures/gui/quest_tasks/travel.png"),
                                        "dungeon_difficulty")
                                .build())
                        .reward(item(Items.NETHERITE_INGOT, 1))
                        .reward(experience(1000))
                        .weight(100)
                        .add()

                .tier(3)
                    .quest("wither_trial_1")
                        .questLine("wither_story")
                        .followQuestOrder(false)
                        .name("Wither Trial I")
                        .description("Gather the components needed to summon the Wither.")
                        .task(bringItem(Items.WITHER_SKELETON_SKULL, 3))
                        .reward(experience(200))
                        .weight(100)
                        .add()
                    .quest("dragon_trial_1")
                        .questLine("ender_dragon_story")
                        .followQuestOrder(false)
                        .name("Dragon Trial I")
                        .description("Find the stronghold that leads to the End.")
                        .task(findStructure("minecraft:stronghold"))
                        .reward(experience(200))
                        .weight(100)
                        .add()

                .tier(4)
                    .quest("wither_trial_2")
                        .questLine("wither_story")
                        .followQuestOrder(false)
                        .name("Wither Trial II")
                        .description("Summon and defeat the Wither.")
                        .task(entityKill(EntityType.WITHER, 1))
                        .reward(item(Items.NETHER_STAR, 1))
                        .reward(experience(300))
                        .weight(100)
                        .add()
                    .quest("dragon_trial_2")
                        .questLine("ender_dragon_story")
                        .followQuestOrder(false)
                        .name("Dragon Trial II")
                        .description("Fight your way through the End and thin out its endermen.")
                        .task(entityKill(EntityType.ENDERMAN, 10))
                        .reward(experience(300))
                        .weight(100)
                        .add()

                .tier(5)
                    .quest("wither_trial_3")
                        .questLine("wither_story")
                        .followQuestOrder(false)
                        .name("Wither Trial III")
                        .description("Bring back proof of your victory over the Wither.")
                        .task(bringItem(Items.NETHER_STAR, 1))
                        .reward(item(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1))
                        .reward(experience(500))
                        .weight(100)
                        .add()
                    .quest("dragon_trial_3")
                        .questLine("ender_dragon_story")
                        .followQuestOrder(false)
                        .name("Dragon Trial III")
                        .description("Slay the Ender Dragon and claim your prize.")
                        .task(entityKill(EntityType.ENDER_DRAGON, 1))
                        .reward(item(Items.DRAGON_EGG, 1))
                        .reward(experience(500))
                        .weight(100)
                        .add()

                .build();

        createPool("spell_engine_trials")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine Trials")
                        .description("Test - Spell Engine: cast an Arcane spell, bind 2 spells, complete the Frost pool, kill zombies attributed to a specific spell/pool/school, and deal/heal damage attributed to a spell.")
                        .requiredMod("spell_engine")
                        .task(spellCastFromSchool(ResourceLocation.fromNamespaceAndPath("spell_power", "arcane"), 5))
                        .task(spellBindTask(2))
                        .task(spellPoolCompleteTask(ResourceLocation.fromNamespaceAndPath("wizards", "spell_book/frost")))
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(2)
                                .inSpellId(ResourceLocation.fromNamespaceAndPath("wizards", "fireball"))
                                .build())
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(2)
                                .inSpellPool(ResourceLocation.fromNamespaceAndPath("wizards", "spell_book/frost"))
                                .build())
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(2)
                                .inSpellSchool(ResourceLocation.fromNamespaceAndPath("spell_power", "arcane"))
                                .build())
                        .task(DealDamageAmountTask.builder()
                                .amount(50)
                                .inSpellId(ResourceLocation.fromNamespaceAndPath("wizards", "fireball"))
                                .build())
                        .task(DoHealingAmountTask.builder()
                                .amount(30)
                                .inSpellPool(ResourceLocation.fromNamespaceAndPath("wizards", "spell_book/frost"))
                                .build())
                        .reward(SpellScrollReward.builder()
                                .pool(ResourceLocation.fromNamespaceAndPath("wizards", "spell_scroll/frost"))
                                .tierRange(1, 3)
                                .build())
                        .reward(spellBind(ResourceLocation.fromNamespaceAndPath("wizards", "fireball")))
                        .reward(experience(300))
                        .weight(100)
                        .add()
                .build();

        createPool("puffish_skills_trials")
                .tier(1)
                    .quest("test")
                        .name("Pufferfish's Skills Trials")
                        .description("Test - Pufferfish's Skills XP and level rewards.")
                        .requiredMod("puffish_skills")
                        .task(bringItem(Items.BOOK, 1))
                        .reward(skillExperience(
                                ResourceLocation.fromNamespaceAndPath("skill_tree_rpgs", "class_skills"), 200,
                                ResourceLocation.fromNamespaceAndPath("skill_tree_rpgs", "textures/gui/icon.png")))
                        .reward(skillLevel(
                                ResourceLocation.fromNamespaceAndPath("skill_tree_rpgs", "class_skills"), 1,
                                ResourceLocation.fromNamespaceAndPath("skill_tree_rpgs", "textures/gui/icon.png")))
                        .weight(100)
                        .add()
                .build();

        createPool("levelz_trials")
                .tier(1)
                    .quest("test")
                        .name("LevelZ Trials")
                        .description("Test - LevelZ skill requirement and reward.")
                        .requiredMod("levelz")
                        .requirement(hasLevelZSkill("mining", 1))
                        .task(bringItem(Items.IRON_PICKAXE, 1))
                        .reward(levelZSkillLevel("smithing", 2))
                        .weight(100)
                        .add()
                .build();

        createPool("dungeon_difficulty_trials")
                .tier(1)
                    .quest("test")
                        .name("Dungeon Difficulty Trials")
                        .description("Test - Dungeon Difficulty: a pre-scaled sword, a sword-only repair+power-level bundle, and min_power_level filters on entity_kill/find_structure/visit_biome.")
                        .requiredMod("dungeon_difficulty")
                        .task(bringItem(Items.DIAMOND_SWORD, 1))
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(3)
                                .minPowerLevel(5)
                                .build())
                        .task(findStructure("minecraft:ancient_city", 10000, 5))
                        .task(VisitBiomeTask.builder()
                                .biomeId(ResourceLocation.withDefaultNamespace("deep_dark"))
                                .amount(1)
                                .minPowerLevel(5)
                                .build())
                        .reward(ItemReward.builder().item(Items.DIAMOND_SWORD)
                                .addFunction(new SetPowerLevelFunction(5))
                                .build())
                        .reward(enhanceItemRestricted(
                                ItemTags.SWORDS,
                                repairItem(),
                                increasePowerLevel(3, 10)))
                        .weight(100)
                        .add()
                .build();

        createPool("enchant_limiter_trials")
                .tier(1)
                    .quest("test")
                        .name("Enchant Limiter Trials")
                        .description("Test - Enchant Limiter slot increase.")
                        .requiredMod("enchant_limiter")
                        .task(bringItem(Items.LAPIS_LAZULI, 3))
                        .reward(increaseEnchantSlots(1, 6))
                        .weight(100)
                        .add()
                .build();

        // all 3 quests need the shuffle flag, not just the first one - see Quest.Builder's shuffleRefreshingQuests doc for why
        createPool("daily_shuffle_test")
                .tier(1)
                    .quest("gather_wood")
                        .name("Daily: Gather Wood")
                        .description("Test - shuffle_refreshing_quests, one of three.")
                        .task(mineBlock(Blocks.OAK_LOG, 8))
                        .reward(experience(50))
                        .weight(100)
                        .repeatable()
                        .shuffleRefreshingQuests()
                        .add()
                    .quest("gather_stone")
                        .name("Daily: Gather Stone")
                        .description("Test - shuffle_refreshing_quests, one of three.")
                        .task(mineBlock(Blocks.STONE, 8))
                        .reward(experience(50))
                        .weight(100)
                        .repeatable()
                        .shuffleRefreshingQuests()
                        .add()
                    .quest("bring_apples")
                        .name("Daily: Bring Apples")
                        .description("Test - shuffle_refreshing_quests, one of three.")
                        .task(bringItem(Items.APPLE, 4))
                        .reward(experience(50))
                        .weight(100)
                        .repeatable()
                        .shuffleRefreshingQuests()
                        .add()
                .build();
    }
}
