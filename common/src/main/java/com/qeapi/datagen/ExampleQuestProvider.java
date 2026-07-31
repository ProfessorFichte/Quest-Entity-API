package com.qeapi.datagen;

import com.qeapi.quest.reward.ItemReward;
import com.qeapi.quest.reward.SpellScrollReward;
import com.qeapi.quest.reward.function.SetEnchantmentsFunction;
import com.qeapi.quest.reward.function.SetLoreFunction;
import com.qeapi.quest.reward.function.SetNameFunction;
import com.qeapi.quest.task.EntityKillTask;
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

public class ExampleQuestProvider extends QuestProvider {

    public ExampleQuestProvider(PackOutput output) {
        super(output, "qe_api");
    }

    @Override
    protected void addQuests() {
        createPool("villager")
                .followOrder(true)

                .tier(1)
                    .quest("eat_golden_apples")
                        .name("Golden Apple Use")
                        .description("Test ")
                        .task(itemUsed(Items.GOLDEN_APPLE, 5))
                        .reward(item(Items.ENCHANTED_GOLDEN_APPLE, 1))
                        .reward(statusEffect(MobEffects.REGENERATION, 60 * 20, 2))
                        .weight(60)
                        .add()
                    .quest("brew_master")
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

                .tier(2)
                    .quest("find_mineshaft_map")
                        .name("Structure with map")
                        .description("Mineshaft")
                        .requirement(hasLevel(3))
                        .task(findStructureWithMap("minecraft:mineshaft"))
                        .reward(item(Items.IRON_PICKAXE, 1))
                        .reward(item(Items.TORCH, 32))
                        .reward(experience(150))
                        .weight(80)
                        .add()

                .tier(3)
                    .quest("outpost_kill")
                        .name("Outpost Kill Test")
                        .description("Pillager outpost Kill")
                        .requirement(hasAdvancement("minecraft:adventure/kill_a_mob"))
                        .task(entityKillInStructure(EntityType.PILLAGER, 2, "minecraft:pillager_outpost"))
                        .reward(item(Items.CROSSBOW, 1))
                        .reward(experience(150))
                        .weight(100)
                        .add()

                .tier(4)
                    .quest("explosion_test")
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

                .tier(5)
                    .quest("legendary_hunter")
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

                .tier(6)
                    .quest("enchantment_reward_travel")
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

                .tier(7)
                    .quest("command_level_kill")
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

                .tier(8)
                    .quest("advancement_req_test")
                        .name("Advancement Test")
                        .description("Monster Room Zombie Advancement Req Test.")
                        .requirement(hasAdvancement("minecraft:story/mine_diamond"))
                        .task(entityKillInStructure(EntityType.ZOMBIE, 10, "minecraft:monster_room"))
                        .reward(lootTable("minecraft:chests/simple_dungeon"))
                        .reward(experience(250))
                        .weight(100)
                        .add()

                .build();

        createPool("explorer")
                .followOrder(false)

                .tier(1)
                    .quest("forest_undead")
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
                    .quest("nether_piglin_hunt")
                        .name("Nether Piglin Bounty")
                        .description("Test")
                        .requirement(hasAdvancement("minecraft:story/enter_the_nether"))
                        .task(entityKillInDimension(EntityType.PIGLIN, 10, "minecraft:the_nether"))
                        .reward(experience(300))
                        .reward(lootTable("minecraft:chests/bastion_treasure"))
                        .weight(100)
                        .add()

                .build();

        // Optional-mod compatibility examples
        createPool("compat_examples")
                .followOrder(false)

                .tier(1)
                    .quest("spell_engine_specific_spell_kill")
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
                    .quest("spell_engine_cast_test")
                        .name("Spell Engine")
                        .description("Arcane Cast Test")
                        .requiredMod("spell_engine")
                        .task(spellCastFromSchool(ResourceLocation.fromNamespaceAndPath("spell_power", "arcane"), 5))
                        .reward(experience(150))
                        .weight(100)
                        .add()
                    .quest("spell_engine_pool_kill")
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
                    .quest("spell_engine_school_kill")
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
                    .quest("spell_scroll_test")
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
                    .quest("skill_tree_test")
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
                    .quest("levelz_test")
                        .name("LevelZ")
                        .description("Test")
                        .requiredMod("levelz")
                        .requirement(hasLevelZSkill("mining", 1))
                        .task(bringItem(Items.IRON_PICKAXE, 1))
                        .reward(levelZSkillLevel("smithing", 2))
                        .weight(100)
                        .add()
                    .quest("dd_1")
                        .name("Dungeon Difficulty")
                        .description("Test")
                        .requiredMod("dungeon_difficulty")
                        .task(bringItem(Items.DIAMOND_SWORD, 1))
                        .reward(ItemReward.builder().item(Items.DIAMOND_SWORD)
                                .addFunction(new com.qeapi.quest.reward.function.SetPowerLevelFunction(5))
                                .build())
                        .weight(100)
                        .add()
                    .quest("repair_test")
                        .name("Repair")
                        .description("Test")
                        .task(bringItem(Items.IRON_INGOT, 5))
                        .reward(repairItem())
                        .weight(100)
                        .add()
                    .quest("spellbound_weapon")
                        .name("Spell Engine")
                        .description("Test")
                        .requiredMod("spell_engine")
                        .task(spellCast(ResourceLocation.fromNamespaceAndPath("wizards", "fireball"), 3))
                        .reward(spellBind(ResourceLocation.fromNamespaceAndPath("wizards", "fireball")))
                        .weight(100)
                        .add()
                    .quest("dd_2")
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
                    .quest("enchantment_limiter")
                        .name("Enchantment Limiter")
                        .description("Test")
                        .requiredMod("enchant_limiter")
                        .task(bringItem(Items.LAPIS_LAZULI, 3))
                        .reward(increaseEnchantSlots(1, 6))
                        .weight(100)
                        .add()
                    .quest("dd_3")
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
                    .quest("spell_cleanse")
                        .name("Spell Engine")
                        .description("Test")
                        .requiredMod("spell_engine")
                        .task(bringItem(Items.ENDER_PEARL, 3))
                        .reward(spellBind(ResourceLocation.fromNamespaceAndPath("wizards", "fireball"), true))
                        .weight(100)
                        .add()

                .build();

        // Demonstrates quest_group: the tier-1 quest's reward is a choice between two
        // setQuestGroup(...) options, and each tier-2 quest is only ever offered to a player who
        // picked its matching group
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
    }
}
