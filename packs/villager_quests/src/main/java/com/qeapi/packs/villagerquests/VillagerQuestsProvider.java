package com.qeapi.packs.villagerquests;

import com.qeapi.datagen.QuestProvider;
import com.qeapi.quest.reward.ItemReward;
import com.qeapi.quest.reward.function.SetEnchantmentsFunction;
import com.qeapi.quest.reward.function.SetEnchantmentsFunction.EnchantmentEntry;
import com.qeapi.quest.reward.function.SetNameFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class VillagerQuestsProvider extends QuestProvider {

    public VillagerQuestsProvider(PackOutput output) {
        super(output, "villager_quests");
    }

    @Override
    protected void addQuests() {
        farmer();
        farmerBiomes();
    }

    private void farmer() {
        createPool("farmer")
                .tier(1)
                    .quest("harvest_wheat")
                        .name("Empty Wheat Stores")
                        .description("The villages stores are running low. Please help us by harvesting {amount} wheat.")
                        .task(harvestCrops(Blocks.WHEAT, 20))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                    .quest("harvest_carrots")
                        .name("A Crop of Carrots")
                        .description("We are in the need for {amount} carrots for the root cellar.")
                        .task(harvestCrops(Blocks.CARROTS, 22))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                    .quest("harvest_potatoes")
                        .name("Pile of Potatoes")
                        .description("Our village could use {amount} potatoes, can you bring them to me?")
                        .task(harvestCrops(Blocks.POTATOES, 26))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                    .quest("harvest_beetroot")
                        .name("Bushel of Beetroot")
                        .description("I want to cook a hearty stew, but don't have enough Beetroot's. Could you bring me {amount} Beetroot please?")
                        .task(harvestCrops(Blocks.BEETROOTS, 15))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                    .quest("gather_seeds")
                        .name("Seed for the next Season")
                        .description("For the next season I'm in the need for {amount} Wheat Seeds.")
                        .task(bringItem(Items.WHEAT_SEEDS, 32))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()

                .tier(2)
                    .quest("bake_bread")
                        .name("Fresh from the Oven")
                        .description("The table from my family is empty, can you bake {amount} fresh bread for us?")
                        .task(crafting(Items.BREAD, 6))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()
                    .quest("haul_pumpkins")
                        .name("A Patch of Pumpkins")
                        .description("My patch came up short this year. Could you bring me {amount} pumpkins?")
                        .task(bringItem(Items.PUMPKIN, 10))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()
                    .quest("haul_melons")
                        .name("Melon Season")
                        .description("The melons are ripe but I can't carry them all. Would you bring me {amount} slices?")
                        .task(bringItem(Items.MELON_SLICE, 16))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()
                    .quest("spread_growth")
                        .name("A Little Encouragement")
                        .description("The crops are slow this season. Could you coax them along by using bone meal {amount} times?")
                        .task(itemUsed(Items.BONE_MEAL, 16))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()
                    .quest("feed_the_livestock")
                        .name("Feed the Livestock")
                        .description("One of our cows looks half-starved. Could you carry a hay bale over and feed it for me?")
                        .task(deliverItem(Items.HAY_BLOCK, 1, EntityType.COW))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()

                .tier(3)
                    .quest("mega_harvest")
                        .name("A Bountiful Yield")
                        .description("We're bringing in the big harvest. Could you harvest {amount} crops of any kind to help us fill the stores?")
                        .task(harvestCrops(48))
                        .reward(enhanceItem(repairItem(), enchantSpecific("minecraft:fortune", 1)))
                        .reward(item(Items.EMERALD, 8)).add()
                    .quest("fertilizer_run")
                        .name("Rich Soil")
                        .description("My fields are worn thin. Bring me {amount} bone meal and we'll make them rich again.")
                        .task(bringItem(Items.BONE_MEAL, 32))
                        .reward(enhanceItem(repairItem(), enchantSpecific("minecraft:fortune", 1)))
                        .reward(item(Items.EMERALD, 8)).add()
                    .quest("sweeten_the_deal")
                        .name("Sugar for the Baker")
                        .description("The baker keeps pestering me for sugar. Could you bring me {amount} sugar cane?")
                        .task(bringItem(Items.SUGAR_CANE, 32))
                        .reward(enhanceItem(repairItem(), enchantSpecific("minecraft:fortune", 1)))
                        .reward(item(Items.EMERALD, 8)).add()
                    .quest("hay_stores")
                        .name("Stock the Barn")
                        .description("Winter's coming and the barn is near empty. Could you bring me {amount} hay bales?")
                        .task(bringItem(Items.HAY_BLOCK, 6))
                        .reward(enhanceItem(repairItem(), enchantSpecific("minecraft:fortune", 1)))
                        .reward(item(Items.EMERALD, 8)).add()

                .tier(4)
                    .quest("harvest_festival")
                        .name("The Harvest Festival")
                        .description("The whole village is counting on the feast! Could you bake {amount_0} pumpkin pies and bring {amount_1} loaves of bread?")
                        .requirement(hasLevel(5))
                        .task(crafting(Items.PUMPKIN_PIE, 3))
                        .task(bringItem(Items.BREAD, 6))
                        .reward(goldenHoe()).reward(item(Items.EMERALD, 16)).reward(experience(100)).add()
                    .quest("the_golden_orchard")
                        .name("The Golden Orchard")
                        .description("It's an old family recipe. Bring me {amount_0} golden carrots and {amount_1} apples, and I'll share something special.")
                        .requirement(hasLevel(5))
                        .task(bringItem(Items.GOLDEN_CARROT, 8))
                        .task(bringItem(Items.APPLE, 16))
                        .reward(goldenHoe()).reward(item(Items.GOLDEN_APPLE, 1)).reward(item(Items.EMERALD, 16)).reward(experience(100)).add()
                    .quest("prize_livestock")
                        .name("The Prize Livestock")
                        .description("I mean to win first prize at the fair! Help me fatten the herd with {amount_0} hay bales and {amount_1} wheat.")
                        .requirement(hasLevel(5))
                        .task(bringItem(Items.HAY_BLOCK, 8))
                        .task(bringItem(Items.WHEAT, 64))
                        .reward(goldenHoe()).reward(item(Items.EMERALD, 24)).reward(experience(100)).add()

                .build();
    }

    private void farmerBiomes() {
        createPool("farmer_desert").includePool("farmer")
                .tier(1)
                    .quest("haul_water")
                        .name("Water for the Fields")
                        .description("Nothing grows without water out here. Could you haul {amount} buckets of water to my parched plot?")
                        .task(bringItem(Items.WATER_BUCKET, 4))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                    .quest("harvest_cactus")
                        .name("A Prickly Harvest")
                        .description("Cactus is one of the few things that thrives in this sand. Could you bring me {amount}?")
                        .task(bringItem(Items.CACTUS, 16))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                .tier(2)
                    .quest("tough_soil")
                        .name("Coax the Barren Soil")
                        .description("This ground fights back. Could you work bone meal into it {amount} times to force a crop?")
                        .task(itemUsed(Items.BONE_MEAL, 24))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()
                .build();

        createPool("farmer_snow").includePool("farmer")
                .tier(1)
                    .quest("sweet_berries")
                        .name("Frost-Hardy Berries")
                        .description("Little else fruits in this cold. Could you bring me {amount} sweet berries?")
                        .task(bringItem(Items.SWEET_BERRIES, 16))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                .tier(2)
                    .quest("pack_the_cellar")
                        .name("Pack the Ice Cellar")
                        .description("Could you bring me {amount} ice? It keeps the harvest from spoiling.")
                        .task(bringItem(Items.ICE, 16))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()
                .build();

        createPool("farmer_swamp").includePool("farmer")
                .tier(1)
                    .quest("sugar_cane")
                        .name("Cane from the Wetlands")
                        .description("The swamp is good for one thing at least. Could you bring me {amount} sugar cane?")
                        .task(bringItem(Items.SUGAR_CANE, 32))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                    .quest("mushroom_forage")
                        .name("Forage the Shade")
                        .description("The swamp floor is thick with mushrooms. Could you forage me {amount} brown mushrooms?")
                        .task(bringItem(Items.BROWN_MUSHROOM, 16))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                .tier(2)
                    .quest("clay_dig")
                        .name("Dig the Riverbed")
                        .description("There's good clay in the muddy banks. Could you dig me up {amount} of it?")
                        .task(bringItem(Items.CLAY_BALL, 16))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()
                .build();

        createPool("farmer_jungle").includePool("farmer")
                .tier(1)
                    .quest("cocoa_harvest")
                        .name("Cocoa Harvest")
                        .description("The cocoa's ripe up in the canopy. Could you gather me {amount} cocoa beans?")
                        .task(bringItem(Items.COCOA_BEANS, 16))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                    .quest("melon_grove")
                        .name("The Melon Grove")
                        .description("The grove is overflowing with melons. Would you bring me {amount} slices?")
                        .task(bringItem(Items.MELON_SLICE, 32))
                        .reward(experience(20)).reward(item(Items.EMERALD, 5))
                        .repeatAfterDays(3).add()
                .tier(2)
                    .quest("bamboo_run")
                        .name("Bamboo Run")
                        .description("I need bamboo for scaffolding and feed. Could you bring me {amount}?")
                        .task(bringItem(Items.BAMBOO, 32))
                        .reward(farmhandHoe()).reward(item(Items.EMERALD, 4)).add()
                .build();
    }

    private ItemReward farmhandHoe() {
        return namedGear(Items.IRON_HOE, "Farmhand's Hoe", ChatFormatting.YELLOW,
                List.of(ench(Enchantments.UNBREAKING, 1)));
    }

    private ItemReward goldenHoe() {
        return namedGear(Items.GOLDEN_HOE, "Golden Hoe", ChatFormatting.GOLD,
                List.of(ench(Enchantments.EFFICIENCY, 2), ench(Enchantments.UNBREAKING, 2)));
    }

    private ItemReward namedGear(Item item, String name, ChatFormatting color, List<EnchantmentEntry> enchants) {
        return ItemReward.builder().item(item)
                .addFunction(new SetEnchantmentsFunction(enchants, true))
                .addFunction(new SetNameFunction(Component.literal(name).withStyle(color)))
                .build();
    }

    private static EnchantmentEntry ench(ResourceKey<Enchantment> enchantment, int level) {
        return new EnchantmentEntry(enchantment.location(), level);
    }
}
