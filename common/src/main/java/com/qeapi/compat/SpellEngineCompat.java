package com.qeapi.compat;

import com.qeapi.QuestAPI;
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

// Server/common side only: spell lookup, the cast event hook, and the recent-cast correlation
// cache for EntityKillTask's spell-kill heuristic. Client rendering helpers live in
// SpellEngineClientCompat instead, since a dedicated server never has Spell Engine's client
// classes. All direct references to Spell Engine live in this file - callers must check isLoaded() first.
public final class SpellEngineCompat {

    private static final String MOD_ID = "spell_engine";

    // Spell Engine doesn't expose which spell dealt a given hit, so this is a best-effort time
    // window for EntityKillTask's in_spell_id check, not exact attribution.
    private static final long RECENT_CAST_WINDOW_TICKS = 40;

    private static final Map<UUID, RecentCast> RECENT_CASTS = new HashMap<>();

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

    // Best-effort: did this player cast the given spell within RECENT_CAST_WINDOW_TICKS?
    public static boolean recentlyCastSpell(UUID playerId, ResourceLocation spellId, long currentTick) {
        RecentCast recent = RECENT_CASTS.get(playerId);
        if (recent == null) return false;
        return recent.spellId().equals(spellId) && (currentTick - recent.tick()) <= RECENT_CAST_WINDOW_TICKS;
    }

    // Unlike in_spell_id, in_spell_pool/in_spell_school don't know the exact spell up front and
    // need to test the cast spell against a selector via matchesSelector.
    public static Optional<ResourceLocation> recentCastSpellId(UUID playerId, long currentTick) {
        RecentCast recent = RECENT_CASTS.get(playerId);
        if (recent == null || (currentTick - recent.tick()) > RECENT_CAST_WINDOW_TICKS) {
            return Optional.empty();
        }
        return Optional.of(recent.spellId());
    }

    // Matches spell_ids (exact), spell_pools (tag membership), or spell_schools, any match wins.
    // Matches everything if all three are empty.
    public static boolean matchesSelector(ServerLevel level, ResourceLocation castSpellId,
                                           List<ResourceLocation> spellIds,
                                           List<ResourceLocation> spellPools,
                                           List<ResourceLocation> spellSchools) {
        if (spellIds.isEmpty() && spellPools.isEmpty() && spellSchools.isEmpty()) {
            return true;
        }

        if (spellIds.stream().anyMatch(castSpellId::equals)) {
            return true;
        }

        if (!spellPools.isEmpty()) {
            boolean inPool = spellPools.stream().anyMatch(pool -> SpellRegistry.entries(level, pool).stream()
                    .anyMatch(entry -> entry.unwrapKey().map(key -> key.location().equals(castSpellId)).orElse(false)));
            if (inPool) return true;
        }

        if (!spellSchools.isEmpty()) {
            Registry<Spell> registry = SpellRegistry.from(level);
            Spell spell = registry.get(castSpellId);
            if (spell != null && spell.school != null && spellSchools.contains(spell.school.id)) {
                return true;
            }
        }

        return false;
    }

    // Applies the chosen spell via Spell Engine's ScrollItem.applySpell, which sets up the spell
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
            QuestAPI.LOGGER.warn("[SpellScrollReward] No matching spell found for scroll reward (pool={}, tier {}-{})",
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

    // Scroll display name for a pool (e.g. "Frost Spell Scroll"), via Spell Engine's
    // translation-key convention (item.<namespace>.<pool_path>, see ScrollItem.onSpellAdded).
    public static Component scrollDisplayName(ResourceLocation poolId) {
        return Component.translatable(ScrollItem.translationKeyForPool(poolId));
    }

    // Same sequence Spell Engine's own SpellBindRandomlyLootFunction uses, just with a fixed spell
    // instead of a random roll. Returns false if the spell id doesn't resolve.
    public static boolean bindSpellToItem(ServerLevel level, ItemStack stack, ResourceLocation spellId, boolean clearExisting) {
        Registry<Spell> registry = SpellRegistry.from(level);
        Optional<Holder.Reference<Spell>> holder = registry.getHolder(spellId);
        if (holder.isEmpty()) {
            QuestAPI.LOGGER.warn("[SpellBindReward] Spell {} not found", spellId);
            return false;
        }

        SpellContainer existingContainer = SpellContainerHelper.containerFromItemStack(stack);
        SpellContainer container = existingContainer != null ? existingContainer : SpellContainer.EMPTY;

        if (clearExisting) {
            // copyWith(List.of()), not SpellContainer.EMPTY - access/pool/max_spell_count/slot are
            // the item's own spellcasting capability, not "existing spells", and resetting to EMPTY
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
