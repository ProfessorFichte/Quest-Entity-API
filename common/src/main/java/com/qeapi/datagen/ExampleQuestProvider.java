package com.qeapi.datagen;

import com.qeapi.item.QuestItemDefinition;
import com.qeapi.quest.reward.ItemReward;
import com.qeapi.quest.reward.SpellScrollReward;
import com.qeapi.quest.reward.function.SetEnchantmentsFunction;
import com.qeapi.quest.reward.function.SetLoreFunction;
import com.qeapi.quest.reward.function.SetNameFunction;
import com.qeapi.quest.task.ApplyStatusEffectTask;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

// Every quest below is a standalone, single-quest pool - each one is its own tag, so you can
// `/qe_api give_tag <entity> qe_api:<pool_name>` to test exactly one feature at a time, without
// wading through a shared pool's other quests or a follow_quest_order chain to reach it.
// wizard_paths is the one exception: it's testing the quest_group/path branching mechanic itself,
// so it has to stay as one connected multi-quest pool to actually exercise that feature.
public class ExampleQuestProvider extends QuestProvider {

    public ExampleQuestProvider(PackOutput output) {
        super(output, "qe_api");
    }

    @Override
    protected void addQuests() {
        createPool("eat_golden_apples")
                .tier(1)
                    .quest("test")
                        .name("Golden Apple Use")
                        .description("Test ")
                        .task(itemUsed(Items.GOLDEN_APPLE, 5))
                        .reward(item(Items.ENCHANTED_GOLDEN_APPLE, 1))
                        .reward(statusEffect(MobEffects.REGENERATION, 60 * 20, 2))
                        .weight(60)
                        .add()
                .build();

        createPool("brew_master")
                .tier(1)
                    .quest("test")
                        .name("Brewer")
                        .description("Test brew 3 potions of Swiftness")
                        .task(brewPotion(Potions.SWIFTNESS.value(), 3))
                        .reward(experience(120))
                        .rewardChoicePool(1,
                                item(Items.DIAMOND, 2),
                                item(Items.EMERALD, 6),
                                statusEffect(MobEffects.LUCK, 300 * 20, 0))
                        .weight(60)
                        .add()
                .build();

        createPool("find_mineshaft_map")
                .tier(1)
                    .quest("test")
                        .name("Structure with map")
                        .description("{entity_name} heard rumors of an abandoned mineshaft nearby - bring back proof you found it.")
                        .requirement(hasLevel(3))
                        .task(findStructureWithMap("minecraft:mineshaft"))
                        .reward(item(Items.IRON_PICKAXE, 1))
                        .reward(item(Items.TORCH, 32))
                        .reward(experience(150))
                        .weight(80)
                        .add()
                .build();

        createPool("outpost_kill")
                .tier(1)
                    .quest("test")
                        .name("Outpost Kill Test")
                        .description("Pillager outpost Kill")
                        .requirement(hasAdvancement("minecraft:adventure/kill_a_mob"))
                        .task(entityKillInStructure(EntityType.PILLAGER, 2, "minecraft:pillager_outpost"))
                        .reward(item(Items.CROSSBOW, 1))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("explosion_test")
                .tier(1)
                    .quest("test")
                        .name("Explosion Kill Test")
                        .description("Kill Zombie with TNT Test.")
                        .task(EntityKillTask.builder().entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(3)
                                .damageTypes(ResourceLocation.withDefaultNamespace("explosion"),
                                        ResourceLocation.withDefaultNamespace("player_explosion"))
                                .build())
                        .reward(item(Items.TNT, 8))
                        .reward(item(Items.GUNPOWDER, 16))
                        .weight(100)
                        .add()
                .build();

        createPool("legendary_hunter")
                .tier(1)
                    .quest("test")
                        .name("Legendary Hunter")
                        .description("Prove your skill against the undead - defeat 5 skeletons and earn a blade worthy of legend.")
                        .task(entityKill(EntityType.SKELETON, 5))
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
                        .reward(experience(500))
                        .weight(100)
                        .add()
                .build();

        createPool("enchantment_reward_travel")
                .tier(1)
                    .quest("test")
                        .name("Travel Enchantment")
                        .description("Test")
                        .requirement(hasLevel(5))
                        .task(blocksTraveled(500))
                        .reward(ItemReward.builder().item(Items.DIAMOND_PICKAXE)
                                .addFunction(new SetEnchantmentsFunction(List.of(
                                        new SetEnchantmentsFunction.EnchantmentEntry(Enchantments.EFFICIENCY.location(), 5),
                                        new SetEnchantmentsFunction.EnchantmentEntry(Enchantments.FORTUNE.location(), 3),
                                        new SetEnchantmentsFunction.EnchantmentEntry(Enchantments.UNBREAKING.location(), 3)
                                ), true))
                                .addFunction(new SetNameFunction(Component.literal("Earthshatter").withStyle(ChatFormatting.GREEN)))
                                .build())
                        .reward(statusEffect(MobEffects.DIG_SPEED, 600 * 20, 1))
                        .weight(100)
                        .add()
                .build();

        createPool("command_level_kill")
                .tier(1)
                    .quest("test")
                        .name("Combination Test")
                        .description("")
                        .requirement(hasLevel(20))
                        .requirement(hasItem(Items.DIAMOND_SWORD, 1))
                        .requirement(hasAdvancement("minecraft:adventure/kill_a_mob"))
                        .task(entityKill(EntityType.WITHER_SKELETON, 5))
                        .reward(item(Items.WITHER_SKELETON_SKULL, 1))
                        .reward(experience(800))
                        .reward(command(
                                "title {player} title {\"text\":\"Wither Hunter!\",\"color\":\"dark_purple\"}",
                                "Title: Wither Hunter"))
                        .weight(100)
                        .add()
                .build();

        createPool("advancement_req_test")
                .tier(1)
                    .quest("test")
                        .name("Advancement Test")
                        .description("Monster Room Zombie Advancement Req Test.")
                        .requirement(hasAdvancement("minecraft:story/mine_diamond"))
                        .task(entityKillInStructure(EntityType.ZOMBIE, 10, "minecraft:monster_room"))
                        .reward(lootTable("minecraft:chests/simple_dungeon"))
                        .reward(experience(250))
                        .weight(100)
                        .add()
                .build();

        createPool("woodland_expedition")
                .tier(1)
                    .quest("test")
                        .name("Woodland Expedition")
                        .description("{entity_name} wants you to seek out a woodland mansion. Bring back tales of what you find within 30,000 blocks - anything farther is beyond even the bravest adventurer's reach.")
                        .requirement(hasLevel(15))
                        .task(findStructure("minecraft:woodland_mansion", 30000))
                        .reward(mapToStructure("minecraft:woodland_mansion", 30000))
                        .reward(teleportToStructure("minecraft:woodland_mansion", 30000))
                        .reward(experience(600))
                        .weight(80)
                        .add()
                .build();

        createPool("forest_undead")
                .tier(1)
                    .quest("test")
                        .name("Forest Biome Undead")
                        .description("Test")
                        .task(EntityKillTask.builder()
                                .entityTag(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("undead")))
                                .amount(2)
                                .inBiomeTag(TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_forest")))
                                .build())
                        .reward(experience(400))
                        .reward(statusEffect(MobEffects.DAMAGE_BOOST, 120 * 20, 1))
                        .reward(advancement("minecraft:adventure/kill_a_mob"))
                        .weight(100)
                        .add()
                .build();

        createPool("nether_piglin_hunt")
                .tier(1)
                    .quest("test")
                        .name("Nether Piglin Bounty")
                        .description("Test")
                        .requirement(hasAdvancement("minecraft:story/enter_the_nether"))
                        .task(entityKillInDimension(EntityType.PIGLIN, 10, "minecraft:the_nether"))
                        .reward(experience(300))
                        .reward(lootTable("minecraft:chests/bastion_treasure"))
                        .weight(100)
                        .add()
                .build();

        createPool("angler")
                .tier(1)
                    .quest("test")
                        .name("Angler")
                        .description("Test - catch 5 fish")
                        .task(fishing(5))
                        .reward(experience(100))
                        .weight(100)
                        .add()
                .build();

        createPool("harvest_test")
                .tier(1)
                    .quest("test")
                        .name("Harvest Hand")
                        .description("Test - harvest 10 crops")
                        .task(harvestCrops(10))
                        .reward(item(Items.BREAD, 8))
                        .weight(100)
                        .add()
                .build();

        createPool("anvil_test")
                .tier(1)
                    .quest("test")
                        .name("Anvil Work")
                        .description("Test - repair an item at an anvil")
                        .task(anvilRepair(1))
                        .reward(item(Items.IRON_INGOT, 4))
                        .weight(100)
                        .add()
                .build();

        createPool("crafting_test")
                .tier(1)
                    .quest("test")
                        .name("Crafting Practice")
                        .description("Test - craft a crafting table")
                        .task(crafting(Items.CRAFTING_TABLE, 1))
                        .reward(experience(50))
                        .weight(100)
                        .add()
                .build();

        createPool("enchanting_test")
                .tier(1)
                    .quest("test")
                        .name("Arcane Study")
                        .description("Test - enchant an item at an enchanting table")
                        .task(enchanting(1))
                        .reward(item(Items.LAPIS_LAZULI, 8))
                        .weight(100)
                        .add()
                .build();

        createPool("smithing_test")
                .tier(1)
                    .quest("test")
                        .name("Netherite Smith")
                        .description("Test - smith an item at a smithing table")
                        .task(smithing(1))
                        .reward(item(Items.NETHERITE_SCRAP, 2))
                        .weight(100)
                        .add()
                .build();

        // Optional-mod compatibility examples - each requiredMod(...) so it only shows up if that
        // mod is actually installed
        createPool("spell_engine_specific_spell_kill")
                .tier(1)
                    .quest("test")
                        .name("Fireball Kill Spell Engine")
                        .description("Test")
                        .requiredMod("spell_engine")
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(2)
                                .inSpellId(ResourceLocation.fromNamespaceAndPath("wizards", "fireball"))
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("spell_engine_cast_test")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine")
                        .description("Arcane Cast Test")
                        .requiredMod("spell_engine")
                        .task(spellCastFromSchool(ResourceLocation.fromNamespaceAndPath("spell_power", "arcane"), 5))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("spell_engine_pool_kill")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine")
                        .description("Test Frost Book Kill")
                        .requiredMod("spell_engine")
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(2)
                                .inSpellPool(ResourceLocation.fromNamespaceAndPath("wizards", "spell_book/frost"))
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("spell_engine_school_kill")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine")
                        .description("Test Arcane School Kill")
                        .requiredMod("spell_engine")
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(2)
                                .inSpellSchool(ResourceLocation.fromNamespaceAndPath("spell_power", "arcane"))
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("spell_scroll_test")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine")
                        .description("Test")
                        .requiredMod("spell_engine")
                        .task(bringItem(Items.BOOK, 3))
                        .reward(SpellScrollReward.builder()
                                .pool(ResourceLocation.fromNamespaceAndPath("wizards", "spell_scroll/frost"))
                                .tierRange(1, 3)
                                .build())
                        .weight(100)
                        .add()
                .build();

        createPool("skill_tree_test")
                .tier(1)
                    .quest("test")
                        .name("Pufferfish's Skills")
                        .description("Test")
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

        createPool("levelz_test")
                .tier(1)
                    .quest("test")
                        .name("LevelZ")
                        .description("Test")
                        .requiredMod("levelz")
                        .requirement(hasLevelZSkill("mining", 1))
                        .task(bringItem(Items.IRON_PICKAXE, 1))
                        .reward(levelZSkillLevel("smithing", 2))
                        .weight(100)
                        .add()
                .build();

        createPool("dd_1")
                .tier(1)
                    .quest("test")
                        .name("Dungeon Difficulty")
                        .description("Test")
                        .requiredMod("dungeon_difficulty")
                        .task(bringItem(Items.DIAMOND_SWORD, 1))
                        .reward(ItemReward.builder().item(Items.DIAMOND_SWORD)
                                .addFunction(new com.qeapi.quest.reward.function.SetPowerLevelFunction(5))
                                .build())
                        .weight(100)
                        .add()
                .build();

        createPool("repair_test")
                .tier(1)
                    .quest("test")
                        .name("Repair")
                        .description("Test")
                        .task(bringItem(Items.IRON_INGOT, 5))
                        .reward(repairItem())
                        .weight(100)
                        .add()
                .build();

        createPool("spellbound_weapon")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine")
                        .description("Test")
                        .requiredMod("spell_engine")
                        .task(spellCast(ResourceLocation.fromNamespaceAndPath("wizards", "fireball"), 3))
                        .reward(spellBind(ResourceLocation.fromNamespaceAndPath("wizards", "fireball")))
                        .weight(100)
                        .add()
                .build();

        createPool("dd_2")
                .tier(1)
                    .quest("test")
                        .name("Dungeon Difficulty")
                        .description("Test")
                        .requiredMod("dungeon_difficulty")
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(3)
                                .build())
                        .reward(increasePowerLevel(3, 10))
                        .weight(100)
                        .add()
                .build();

        createPool("enchantment_limiter")
                .tier(1)
                    .quest("test")
                        .name("Enchantment Limiter")
                        .description("Test")
                        .requiredMod("enchant_limiter")
                        .task(bringItem(Items.LAPIS_LAZULI, 3))
                        .reward(increaseEnchantSlots(1, 6))
                        .weight(100)
                        .add()
                .build();

        createPool("dd_3")
                .tier(1)
                    .quest("test")
                        .name("Dungeon Difficulty")
                        .description("Test")
                        .requiredMod("dungeon_difficulty")
                        .task(bringItem(Items.DIAMOND, 5))
                        .reward(enhanceItem(
                                repairItem(),
                                enchantSpecific("minecraft:sharpness", 3),
                                increasePowerLevel(3, 10)))
                        .weight(100)
                        .add()
                .build();

        createPool("spell_cleanse")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine")
                        .description("Test")
                        .requiredMod("spell_engine")
                        .task(bringItem(Items.ENDER_PEARL, 3))
                        .reward(spellBind(ResourceLocation.fromNamespaceAndPath("wizards", "fireball"), true))
                        .weight(100)
                        .add()
                .build();

        createPool("spell_binding_test")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine")
                        .description("Test - bind 2 spells at the Spell Binding Table")
                        .requiredMod("spell_engine")
                        .task(spellBindTask(2))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("spell_pool_complete_test")
                .tier(1)
                    .quest("test")
                        .name("Spell Engine")
                        .description("Test - finish binding the Frost spell pool")
                        .requiredMod("spell_engine")
                        .task(spellPoolCompleteTask(ResourceLocation.fromNamespaceAndPath("wizards", "spell_book/frost")))
                        .reward(experience(300))
                        .weight(100)
                        .add()
                .build();

        createPool("sound_and_fireworks_test")
                .tier(1)
                    .quest("test")
                        .name("Sound & Fireworks Test")
                        .description("Test - exercises the accept/claim sound overrides and the finish fireworks.")
                        .task(bringItem(Items.STICK, 1))
                        .reward(experience(50))
                        .acceptQuestSoundOverride(ResourceLocation.withDefaultNamespace("entity.experience_orb.pickup"))
                        .finishQuestSoundOverride(ResourceLocation.withDefaultNamespace("ui.toast.challenge_complete"))
                        .weight(100)
                        .add()
                .build();

        QuestItemDefinition testHammer = questItem(
                ResourceLocation.fromNamespaceAndPath("qe_api", "item/test_hammer"),
                1001,
                "Smith's Test Hammer");

        createPool("conditional_drop_test")
                .tier(1)
                    .quest("test")
                        .name("Conditional Drop Test")
                        .description("Test - pillagers at an outpost sometimes drop a Smith's Test Hammer; bring one back.")
                        .task(ConditionalDropTask.builder()
                                .questItem(testHammer)
                                .amount(1)
                                .entityId(ResourceLocation.withDefaultNamespace("pillager"))
                                .inStructure("minecraft:pillager_outpost")
                                .mobDropChance(0.5)
                                .build())
                        .task(bringItemQuestItem(testHammer, 1))
                        .reward(experience(200))
                        .weight(100)
                        .add()
                .build();

        QuestItemDefinition piglinTrophy = questItem(
                ResourceLocation.fromNamespaceAndPath("qe_api", "item/piglin_trophy"),
                1002,
                "Piglin Bastion Trophy");

        // loot_table_ids demo: a Bastion Remnant splits its loot across several distinct tables
        // (treasure/other/bridge/hoglin_stable), so covering every chest in the structure means
        // listing all of them - contrast with nether_fortress_relic below, which targets a
        // structure through chest_in_structures instead.
        createPool("bastion_treasure_hunt")
                .tier(1)
                    .quest("test")
                        .name("Bastion Treasure Hunt")
                        .description("Test - loot_table_ids targeting: any of a Bastion Remnant's several loot tables can drop a Piglin Bastion Trophy; bring one back.")
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
                        .reward(experience(250))
                        .weight(100)
                        .add()
                .build();

        QuestItemDefinition fortressRelic = questItem(
                ResourceLocation.fromNamespaceAndPath("qe_api", "item/fortress_relic"),
                1003,
                "Fortress Relic");

        // chest_in_structures demo: matches any chest physically inside the structure regardless of
        // which of its loot tables actually resolved there, with no loot_table_ids set at all -
        // contrast with bastion_treasure_hunt above, which lists ids instead.
        createPool("nether_fortress_relic")
                .tier(1)
                    .quest("test")
                        .name("Fortress Relic")
                        .description("Test - chest_in_structures targeting: any chest physically inside a Nether Fortress can drop a Fortress Relic; bring one back.")
                        .task(ConditionalDropTask.builder()
                                .questItem(fortressRelic)
                                .amount(1)
                                .chestInStructures("minecraft:nether_fortress")
                                .chestDropChance(0.2)
                                .build())
                        .task(bringItemQuestItem(fortressRelic, 1))
                        .reward(experience(250))
                        .weight(100)
                        .add()
                .build();

        // Demonstrates quest_group: the tier-1 quest's reward is a choice between two
        // setQuestGroup(...) options, and each tier-2 quest is only ever offered to a player who
        // picked its matching group. Kept as one connected pool since the branching itself is what's
        // under test - splitting it apart would remove the thing being tested.
        createPool("wizard_paths")
                .followOrder(true)

                .tier(1)
                    .quest("wizard_root")
                        .name("Wizard Path Root")
                        .description("Every apprentice must choose a discipline. Bring us 3 books to begin your training, then pick your path.")
                        .task(bringItem(Items.BOOK, 3))
                        .reward(experience(100))
                        .rewardChoicePool(1,
                                setQuestGroup("fire", ResourceLocation.fromNamespaceAndPath("qe_api", "textures/gui/quest_paths/fire.png")),
                                setQuestGroup("frost", ResourceLocation.fromNamespaceAndPath("qe_api", "textures/gui/quest_paths/frost.png")))
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

        createPool("ominous_vault_test")
                .tier(1)
                    .quest("test")
                        .name("Ominous Vault Test")
                        .description("Test - clear an Ominous trial spawner's wave.")
                        .task(trialSpawnerComplete(1, true))
                        .reward(experience(200))
                        .weight(100)
                        .add()
                .build();

        // deliver_item resolution demo: targets the Allay type, which resolves to exactly one
        // nearby Allay at accept time. To actually see the "only one" behavior, spawn two Allays
        // near the giver before accepting (e.g. `/summon minecraft:allay ~2 ~ ~` twice) - only the
        // nearest one gets the floating cake icon and only that one accepts the delivery.
        createPool("cake_for_the_allay")
                .tier(1)
                    .quest("test")
                        .name("Cake for the Allay")
                        .description("Test - deliver_item targeting: resolves to exactly one nearby Allay at accept time, even with several around; bring it a cake.")
                        .task(deliverItem(Items.CAKE, 1, EntityType.ALLAY))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("sniper_test")
                .tier(1)
                    .quest("test")
                        .name("Sniper")
                        .description("Test - kill a skeleton from at least {min_range} blocks away.")
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("skeleton"))
                                .amount(1)
                                .minRange(40)
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("armed_and_dangerous")
                .tier(1)
                    .quest("test")
                        .name("Armed and Dangerous")
                        .description("Test - required_item/attribute filters: kill a zombie while holding a diamond sword, and it must have at least 15 max health.")
                        .task(EntityKillTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(1)
                                .requiredItemId(Items.DIAMOND_SWORD)
                                .minAttributeValue(15.0)
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("empowered_mineshaft")
                .tier(1)
                    .quest("test")
                        .name("Dungeon Difficulty")
                        .description("Test - find_structure's min_power_level: find a mineshaft whose location is at least power level 2.")
                        .requiredMod("dungeon_difficulty")
                        .task(findStructure("minecraft:mineshaft", com.qeapi.util.StructureDistanceUtil.DEFAULT_MAX_DISTANCE, 2))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("biome_explorer")
                .tier(1)
                    .quest("test")
                        .name("Biome Explorer")
                        .description("Test - visit_biome: visit both the Desert and the Jungle.")
                        .task(VisitBiomeTask.builder()
                                .biomeIds("minecraft:desert", "minecraft:jungle")
                                .amount(2)
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("poison_practice")
                .tier(1)
                    .quest("test")
                        .name("Poison Practice")
                        .description("Test - apply_status_effect: poison another entity 3 times.")
                        .task(ApplyStatusEffectTask.builder()
                                .effectId(ResourceLocation.withDefaultNamespace("poison"))
                                .amount(3)
                                .target(ApplyStatusEffectTask.Target.OTHERS)
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("heavy_hitter")
                .tier(1)
                    .quest("test")
                        .name("Heavy Hitter")
                        .description("Test - deal_damage_amount: deal a total of 100 damage to zombies.")
                        .task(DealDamageAmountTask.builder()
                                .entityId(ResourceLocation.withDefaultNamespace("zombie"))
                                .amount(100)
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        createPool("field_medic")
                .tier(1)
                    .quest("test")
                        .name("Field Medic")
                        .description("Test - do_healing_amount: heal a total of 50 health (self-healing always counts; healing others only counts with Spell Engine and a recently-cast healing spell).")
                        .task(DoHealingAmountTask.builder()
                                .amount(50)
                                .healTarget(DoHealingAmountTask.HealTarget.EITHER)
                                .build())
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        // quest_line_choice demo: a tier-2 root offering two mutually exclusive three-step lines.
        // The step quests (tiers 3-5) sit in this same pool so they show up for the same giver, but
        // each is authored with follow_quest_order(false) - their real availability gate is the
        // questLine filter matching the player's currently-picked line, not tier order. Without that
        // override they'd deadlock: a step tier-gated behind the root's own tier would need the root
        // marked completed first, but the root only completes once every step is already done.
        // ender_dragon_story requires dungeon_difficulty (same optional-compat id the dd_1/dd_2/dd_3
        // pools above use), to prove a required_mod'd LineOption is genuinely hidden from the picker
        // when that mod isn't loaded.
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
                                        ResourceLocation.fromNamespaceAndPath("qe_api", "textures/gui/quest_tasks/find_structure_default.png"))
                                .line("ender_dragon_story",
                                        Component.literal("Trial of the Ender Dragon"),
                                        Component.literal("Journey to the End and slay the dragon itself."),
                                        ResourceLocation.fromNamespaceAndPath("qe_api", "textures/gui/quest_tasks/travel.png"),
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

        // {amount} description mutator: the placeholder resolves to the task's target amount.
        createPool("amount_mutator_test")
                .tier(1)
                    .quest("test")
                        .name("Amount Mutator")
                        .description("Test - bring me {amount} diamonds ({amount} should read as 5).")
                        .task(bringItem(Items.DIAMOND, 5))
                        .reward(experience(100))
                        .weight(100)
                        .add()
                .build();

        // Multiple tasks use indexed {amount_0}, {amount_1}, ... in task order.
        createPool("amount_mutator_multi_test")
                .tier(1)
                    .quest("test")
                        .name("Indexed Amount Mutator")
                        .description("Test - forge {amount_0} iron ingots and {amount_1} gold ingots (8 and 4).")
                        .task(bringItem(Items.IRON_INGOT, 8))
                        .task(bringItem(Items.GOLD_INGOT, 4))
                        .reward(experience(100))
                        .weight(100)
                        .add()
                .build();

        // conditional_drop loot injection of an API quest_item into a BLOCK loot table - mine stone,
        // it sometimes drops the custom Buried Gem quest item; then bring it back. amount(1) caps it.
        QuestItemDefinition blockGem = questItem(
                ResourceLocation.fromNamespaceAndPath("qe_api", "item/block_loot_gem"),
                1004, "Buried Gem");
        createPool("block_loot_drop_test")
                .tier(1)
                    .quest("test")
                        .name("Block Loot Injection")
                        .description("Test - mine stone; the block loot table sometimes drops a Buried Gem quest item. Bring one back.")
                        .task(ConditionalDropTask.builder()
                                .questItem(blockGem)
                                .amount(1)
                                .lootTableIds("minecraft:blocks/stone")
                                .chestDropChance(0.5)
                                .build())
                        .task(bringItemQuestItem(blockGem, 1))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        // Same, but the FISHING loot table.
        QuestItemDefinition fishingPearl = questItem(
                ResourceLocation.fromNamespaceAndPath("qe_api", "item/fishing_loot_pearl"),
                1005, "Sunken Pearl");
        createPool("fishing_loot_drop_test")
                .tier(1)
                    .quest("test")
                        .name("Fishing Loot Injection")
                        .description("Test - go fishing; the fishing loot table sometimes yields a Sunken Pearl quest item. Bring one back.")
                        .task(ConditionalDropTask.builder()
                                .questItem(fishingPearl)
                                .amount(1)
                                .lootTableIds("minecraft:gameplay/fishing")
                                .chestDropChance(0.5)
                                .build())
                        .task(bringItemQuestItem(fishingPearl, 1))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        // Same, but a CROP loot table - harvest wheat.
        QuestItemDefinition cropCharm = questItem(
                ResourceLocation.fromNamespaceAndPath("qe_api", "item/crop_loot_charm"),
                1006, "Harvest Charm");
        createPool("crop_loot_drop_test")
                .tier(1)
                    .quest("test")
                        .name("Crop Loot Injection")
                        .description("Test - harvest wheat; its loot table sometimes drops a Harvest Charm quest item. Bring one back.")
                        .task(ConditionalDropTask.builder()
                                .questItem(cropCharm)
                                .amount(1)
                                .lootTableIds("minecraft:blocks/wheat")
                                .chestDropChance(0.5)
                                .build())
                        .task(bringItemQuestItem(cropCharm, 1))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                .build();

        // Locked-quest / show_all_quests test: a 3-tier follow_quest_order pool. With show_all_quests
        // on, tiers 2-3 show with the locked marker + tooltip until the lower tier is completed.
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

        createPool("teleport_coords_test")
                .tier(1)
                    .quest("test")
                        .name("Teleport to Coordinates")
                        .description("Test - bring a compass to teleport to fixed coordinates (0, 100, 0).")
                        .task(bringItem(Items.COMPASS, 1))
                        .reward(teleportToCoordinates(0, 100, 0))
                        .weight(100)
                        .add()
                .build();

        createPool("teleport_biome_test")
                .tier(1)
                    .quest("test")
                        .name("Teleport to Biome")
                        .description("Test - bring a compass to teleport to the nearest desert.")
                        .task(bringItem(Items.COMPASS, 1))
                        .reward(teleportToBiome("minecraft:desert"))
                        .weight(100)
                        .add()
                .build();
    }
}
