package com.qeapi.client.compat;

import com.qeapi.compat.ModCompatUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.util.SpellRender;
import net.spell_engine.item.ScrollItem;
import net.spell_engine.item.SpellEngineItems;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Client-only half of the Spell Engine integration (icon/tooltip resolution for the quest GUI).
// Separate from SpellEngineCompat because a dedicated server never has Spell Engine's client
// classes available. Only call from client-only code, after confirming Spell Engine is loaded.
public final class SpellEngineClientCompat {

    private SpellEngineClientCompat() {}

    public static boolean isLoaded() {
        return ModCompatUtil.isModLoaded("spell_engine");
    }

    // one standalone PNG per spell, addressed by convention from the spell id (not an atlas sprite)
    public static ResourceLocation iconTexture(ResourceLocation spellId) {
        return SpellRender.iconTexture(spellId);
    }

    public static List<Component> tooltipLines(ResourceLocation spellId, Player player) {
        return SpellTooltip.spellEntry(spellId, player, ItemStack.EMPTY, true, 0);
    }

    // first tooltip line, for task/requirement text instead of the raw spell_id; falls back to the id itself
    public static String spellName(ResourceLocation spellId, Player player) {
        List<Component> lines = tooltipLines(spellId, player);
        return lines.isEmpty() ? spellId.toString() : lines.get(0).getString();
    }

    // spell ids in a pool (a Spell Engine registry tag); used to rotate through pool contents in the quest GUI
    public static List<ResourceLocation> spellsInPool(Level level, ResourceLocation poolId) {
        List<ResourceLocation> spells = new ArrayList<>();
        for (Holder<Spell> entry : SpellRegistry.entries(level, poolId)) {
            entry.unwrapKey().ifPresent(key -> spells.add(key.location()));
        }
        return spells;
    }

    // only icon available for a spell_school selector - Spell Power has no per-school icon
    public static final ResourceLocation SCHOOL_GENERIC_ICON =
            ResourceLocation.fromNamespaceAndPath("spell_power", "textures/mob_effect/generic.png");

    // resolved from Spell Power's mob effect for the school where one exists; falls back to the raw id path
    public static Component schoolDisplayName(ResourceLocation schoolId) {
        for (SpellSchool school : SpellSchools.all()) {
            if (school.id.equals(schoolId)) {
                if (school.ownedBoostEffect != null) {
                    return school.ownedBoostEffect.getDisplayName();
                }
                break;
            }
        }
        return Component.literal(schoolId.getPath());
    }

    private static final ResourceLocation SPELL_BINDING_TABLE_ID =
            ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_binding");

    public static ItemStack spellBindingTableItemStack() {
        return new ItemStack(BuiltInRegistries.ITEM.get(SPELL_BINDING_TABLE_ID));
    }

    // Placeholder stack for previewing SpellScrollReward in the GUI - the real spell isn't picked
    // until claim time. Applies the pool's item model if given; Spell Engine keys its model presets
    // by pool rather than by spell, so this previews accurately without pre-rolling a spell.
    public static ItemStack genericScrollItemStack(Optional<ResourceLocation> pool) {
        ItemStack stack = new ItemStack(SpellEngineItems.SCROLL.get());
        pool.ifPresent(p -> stack.set(SpellDataComponents.ITEM_MODEL, ScrollItem.modelIdForPool(p)));
        return stack;
    }
}
