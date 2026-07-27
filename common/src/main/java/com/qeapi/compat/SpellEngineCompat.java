package com.qeapi.compat;

import com.qeapi.QuestEntityAPI;
import com.qeapi.event.QuestEventHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.item.ScrollItem;
import net.spell_engine.item.SpellEngineItems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Optional Spell Engine (net.spell_engine) integration - server/common side only: spell lookup, the
// cast event hook, and the recent-cast correlation cache for EntityKillTask's spell-kill heuristic.
// Client rendering helpers live in SpellEngineClientCompat instead, since a dedicated server never
// has Spell Engine's client classes. All direct references to Spell Engine live in this file - callers must check isLoaded() first.
public final class SpellEngineCompat {

    private static final String MOD_ID = "spell_engine";

    // how long a cast spell "counts" toward a kill for EntityKillTask's in_spell_id check - Spell
    // Engine doesn't expose which spell dealt a given hit, so this is a best-effort time window, not
    // exact attribution (see the design note on EntityKillTask.inSpellId)
    private static final long RECENT_CAST_WINDOW_TICKS = 40;

    private static final Map<UUID, RecentCast> RECENT_CASTS = new HashMap<>(); // playerId -> last cast spell + tick

    private record RecentCast(ResourceLocation spellId, long tick) {}

    private SpellEngineCompat() {}

    public static boolean isLoaded() {
        return ModCompatUtil.isModLoaded(MOD_ID);
    }

    // call once during platform init, guarded by isLoaded()
    public static void registerCastListener() {
        SpellEvents.SPELL_CAST.register(args -> {
            if (!(args.caster() instanceof ServerPlayer player)) return;
            Optional<ResourceLocation> spellId = args.spell().unwrapKey().map(key -> key.location());
            if (spellId.isEmpty()) return;

            RECENT_CASTS.put(player.getUUID(), new RecentCast(spellId.get(), player.level().getGameTime()));
            QuestEventHandler.onSpellCast(player, spellId.get());
        });
    }

    // Best-effort: did this player cast the given spell within RECENT_CAST_WINDOW_TICKS? Used by
    // EntityKillTask.inSpellId.
    public static boolean recentlyCastSpell(UUID playerId, ResourceLocation spellId, long currentTick) {
        RecentCast recent = RECENT_CASTS.get(playerId);
        if (recent == null) return false;
        return recent.spellId().equals(spellId) && (currentTick - recent.tick()) <= RECENT_CAST_WINDOW_TICKS;
    }

    // Used by EntityKillTask's in_spell_pool/in_spell_school checks, which (unlike in_spell_id)
    // don't know the exact spell up front and need to test the cast spell against a selector via
    // matchesSelector.
    public static Optional<ResourceLocation> recentCastSpellId(UUID playerId, long currentTick) {
        RecentCast recent = RECENT_CASTS.get(playerId);
        if (recent == null || (currentTick - recent.tick()) > RECENT_CAST_WINDOW_TICKS) {
            return Optional.empty();
        }
        return Optional.of(recent.spellId());
    }

    // Matches against a task/requirement's selector fields - spell_id (exact), spell_pool (tag
    // membership), or spell_school. Matches everything if none are present.
    public static boolean matchesSelector(ServerLevel level, ResourceLocation castSpellId,
                                           Optional<ResourceLocation> spellId,
                                           Optional<ResourceLocation> spellPool,
                                           Optional<ResourceLocation> spellSchool) {
        if (spellId.isEmpty() && spellPool.isEmpty() && spellSchool.isEmpty()) {
            return true;
        }

        if (spellId.isPresent() && castSpellId.equals(spellId.get())) {
            return true;
        }

        if (spellPool.isPresent()) {
            boolean inPool = SpellRegistry.entries(level, spellPool.get()).stream()
                    .anyMatch(entry -> entry.unwrapKey().map(key -> key.location().equals(castSpellId)).orElse(false));
            if (inPool) return true;
        }

        if (spellSchool.isPresent()) {
            Registry<Spell> registry = SpellRegistry.from(level);
            Spell spell = registry.get(castSpellId);
            if (spell != null && spell.school != null && spellSchool.get().equals(spell.school.id)) {
                return true;
            }
        }

        return false;
    }

    // Picks a random spell matching the given filters (or the exact spell_id, bypassing random
    // selection) and applies it via Spell Engine's ScrollItem.applySpell, which sets up the spell
    // container, item model, rarity, and display name the way a naturally-found scroll would.
    // Returns ItemStack.EMPTY if no matching spell was found.
    @SuppressWarnings("unchecked")
    public static ItemStack createSpellScroll(ServerLevel level, Optional<ResourceLocation> spellId,
                                               Optional<ResourceLocation> pool, int tierMin, int tierMax,
                                               List<ResourceLocation> excludedSpells) {
        Registry<Spell> registry = SpellRegistry.from(level);

        Holder<Spell> chosen = null;
        if (spellId.isPresent()) {
            chosen = registry.getHolder(spellId.get()).map(h -> (Holder<Spell>) h).orElse(null);
        } else {
            List<Holder<Spell>> candidates = pool.isPresent()
                    ? SpellRegistry.entries(level, pool.get())
                    : registry.holders().map(h -> (Holder<Spell>) h).toList();

            List<Holder<Spell>> filtered = candidates.stream()
                    .filter(entry -> {
                        ResourceLocation id = entry.unwrapKey().map(key -> key.location()).orElse(null);
                        if (id == null || excludedSpells.contains(id)) return false;
                        int tier = entry.value().tier;
                        return tier >= tierMin && tier <= tierMax;
                    })
                    .toList();

            if (!filtered.isEmpty()) {
                chosen = filtered.get(level.getRandom().nextInt(filtered.size()));
            }
        }

        if (chosen == null) {
            QuestEntityAPI.LOGGER.warn("[SpellScrollReward] No matching spell found for scroll reward (pool={}, tier {}-{})",
                    pool.map(ResourceLocation::toString).orElse("any"), tierMin, tierMax);
            return ItemStack.EMPTY;
        }

        Holder<Spell> finalChosen = chosen;
        ItemStack scroll = new ItemStack(SpellEngineItems.SCROLL.get());
        TagKey<Spell> resolvedPool = pool.map(p -> TagKey.create(SpellRegistry.KEY, p))
                .orElseGet(() -> ScrollItem.resolveSpellPool(level, finalChosen));
        ScrollItem.applySpell(scroll, finalChosen, resolvedPool);
        return scroll;
    }

    // Scroll display name for a pool (e.g. "Frost Spell Scroll"), via Spell Engine's translation-key
    // convention (item.<namespace>.<pool_path>, see ScrollItem.onSpellAdded). Used by
    // SpellScrollReward's display text instead of showing the raw pool tag.
    public static Component scrollDisplayName(ResourceLocation poolId) {
        return Component.translatable(ScrollItem.translationKeyForPool(poolId));
    }

    // Binds a specific spell onto an existing item, making it a spell container if it isn't one
    // yet - same sequence Spell Engine's own SpellBindRandomlyLootFunction uses, just with a fixed
    // spell instead of a random roll. If clearExisting is true, any spells already bound to the
    // item are dropped first so only the new spell remains; otherwise they're kept alongside it.
    // Returns false if the spell id doesn't resolve (nothing is changed in that case).
    public static boolean bindSpellToItem(ServerLevel level, ItemStack stack, ResourceLocation spellId, boolean clearExisting) {
        Registry<Spell> registry = SpellRegistry.from(level);
        Optional<Holder.Reference<Spell>> holder = registry.getHolder(spellId);
        if (holder.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("[SpellBindReward] Spell {} not found", spellId);
            return false;
        }

        SpellContainer existingContainer = SpellContainerHelper.containerFromItemStack(stack);
        SpellContainer container = existingContainer != null ? existingContainer : SpellContainer.EMPTY;

        if (clearExisting) {
            // Only drop the bound spells - access/pool/max_spell_count/slot are the item's own
            // spellcasting capability (what kinds of spells it can resolve at all), not part of
            // "existing spells". Resetting to SpellContainer.EMPTY instead of copyWith(List.of())
            // used to zero those out too, leaving the item unable to cast anything.
            container = container.copyWith(List.of());
        } else if (container.spell_ids().contains(spellId.toString())) {
            return true; // already bound
        }

        container = container.withAdditionalSpell(List.of(spellId.toString()));
        List<String> sortedSpellIds = SpellContainerHelper.sortedSpells(level, container.spell_ids());
        container = container.copyWith(sortedSpellIds);

        stack.set(SpellDataComponents.SPELL_CONTAINER, container);
        return true;
    }
}
