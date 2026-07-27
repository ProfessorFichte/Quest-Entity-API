# Quest Entity API

A data-driven quest system for Minecraft (Fabric + NeoForge, 1.21.1). Give any entity - a
villager, a custom mob from your own mod, anything - one or more quests with tasks,
requirements, and rewards, defined entirely in JSON. No code required for basic use; a small
Java API is available for mod authors who want to hook their own entities in directly.

## Requirements

- Minecraft 1.21.1, Fabric Loader or NeoForge
- Fabric API (Fabric only)

Optional: [Spell Engine](https://modrinth.com/mod/spell-engine), Pufferfish's Skills,
Dungeon Difficulty, or Enchant Limiter for the extra task/reward types listed under
[Compatibility](#compatibility) - none of them are required, everything else works fine
without them.

## Installation

Drop the jar in your `mods` folder. On its own this mod does nothing - it only adds quests to
entities if a datapack (yours or one bundled with another mod) actually defines some. Everything
below is for datapack authors and mod developers who want to add quests.

## Core concepts

- **Quest** - one task list + requirement list + reward list, belonging to a **tier** (1-8).
  Defined as a single JSON file under `data/<namespace>/entity_quest/`.
- **Tag** - a list of quest IDs (optionally including other tags) under
  `data/<namespace>/tags/entity_quests/`. This is how you group multiple quests into a
  **pool**: the tag resolves to every quest it references, grouped by each quest's own tier,
  and that's what an entity actually offers.
- **Assignment** - a data file that attaches a tag to entities that already exist in the game
  (like villagers) without writing any code, under `data/<namespace>/entity_quest_assignment/`.
- **Tiers** - each pool can have up to 8 tiers. Only one quest per tier is ever offered to a
  given entity (picked once, weighted-random, seeded by that entity's UUID so it's stable),
  and `follow_quest_order` (per-quest) can require every lower tier to be completed first.
- Quests are **per-player** - each player has independent progress with each quest entity, and
  can only have one active quest per entity at a time.
- If the entity holding an active quest dies, that quest's progress is cleared for whoever had
  it active.

## Quests (`data/<namespace>/entity_quest/<path>.json`)

Each file is exactly one quest. `id` is optional and defaults to the file's own resource
location if omitted.

```json
{
  "tier": 1,
  "quest_name": { "translate": "quest.mymod.gather_wheat.name" },
  "quest_description": { "translate": "quest.mymod.gather_wheat.desc" },
  "follow_quest_order": true,
  "required_mod": "some_other_mod",
  "requirements": [ ... ],
  "tasks": [ ... ],
  "rewards": [ ... ],
  "weight": 100
}
```

| Field | Required | Notes |
|---|---|---|
| `id` | no | Defaults to the file's own location |
| `tier` | **yes** | 1-8 |
| `quest_name` / `quest_description` | no | Any vanilla text component (`{"translate": "..."}` or `{"text": "..."}`). Falls back to `quest.<namespace>.<path>.name` / `.desc` translation keys if omitted |
| `follow_quest_order` | no, default `true` | If true and this quest's tier > 1, the player must have completed at least one quest from every lower tier in the same pool first |
| `required_mod` | no | Quest is skipped entirely (not offered) unless this mod ID is loaded |
| `requirements` | no, default `[]` | Must all be met to accept the quest - see below |
| `tasks` | **yes** | What the player must do - see below |
| `rewards` | **yes** | What the player gets on completion - see below |
| `weight` | no, default `100` | Relative weight vs. other quests in the same tier when one is picked |
| `repeat_after_days` | no | If set, the quest becomes acceptable again this many in-game days after it was last completed. Omitted (the default) means once completed, it's done forever |
| `quest_group` | no | Only offered to a player who's chosen this exact group for the pool - see [Quest paths](#quest-paths) |

## Tags (`data/<namespace>/tags/entity_quests/<name>.json`)

Vanilla tag format. This is the only way to combine multiple quests into a pool.

```json
{
  "replace": false,
  "values": [
    "mymod:villager/gather_wheat",
    "mymod:villager/kill_zombies",
    "#mymod:other_tag"
  ]
}
```

## Assigning quests to existing entities (`data/<namespace>/entity_quest_assignment/<name>.json`)

Attaches a tag to entities you didn't write code for (e.g. vanilla villagers), purely via data.

```json
{
  "entity_id": "minecraft:villager",
  "quest_pool": ["mymod:villager", "mymod:villager_alt"],
  "quest_chance": 0.5,
  "chunk_restriction_radius": 0,
  "villager_data": {
    "biome_type": "plains",
    "profession": "farmer"
  }
}
```

| Field | Required | Notes |
|---|---|---|
| `entity_id` | **yes** | Vanilla or modded entity type |
| `quest_pool` | **yes** | A tag reference, or a JSON array of tag references - one is picked uniformly at random per entity if you give a list |
| `quest_chance` | no, default `1.0` | Probability (0.0-1.0) this assignment actually triggers when a matching entity is checked |
| `chunk_restriction_radius` | no | If present, only one entity within this many chunks of another can hold *this specific assignment* at a time. `0` = same chunk only. Freed again when the entity holding it dies. Omit for no restriction (default) |
| `villager_data` | no, villager-only | `biome_type` (the villager's own `VillagerType`, e.g. `plains`/`desert`/`jungle`/`savanna`/`snow`/`swamp`/`taiga` - not the biome it happens to be standing in) and/or `profession` (short name like `farmer`, or a full ID like `wizards:wizard_merchant` for modded professions). Either, both, or neither |

If multiple assignments match the same entity, the most specific one wins (biome+profession >
either alone > neither), ties broken randomly, then `quest_chance` is rolled.

Villagers also re-check assignments whenever their profession changes (job conversion) -
existing quest progress is preserved if a player has already interacted with it.

## Task types

| Type | Fields |
|---|---|
| `qe_api:entity_kill` | `amount`; one of `entity_id`, `entity_tag`, or `entity_ids` (list, OR logic); optional `damage_types` (list), `in_structure`, `in_biome`, `in_biome_tag`, `in_dimension`, `in_spell_id`/`in_spell_pool`/`in_spell_school` (Spell Engine compat - see [Compatibility](#compatibility)), `min_power_level` (Dungeon Difficulty compat - see [Compatibility](#compatibility)), `provides_map` (default `false`, only meaningful with `in_structure` - see below) |
| `qe_api:find_structure` | `structure_id`; optional `texture_id` for a custom icon; optional `provides_map` (default `false` - see below) |
| `qe_api:bring_item` | `item_id`, `amount` (default 1), optional `has_component` (data component ID the item must carry). Item is deleted from the player's inventory when the reward is claimed. The GUI always lets the player open a picker and choose specifically which stack(s) to turn in (e.g. to protect a uniquely-enchanted sword when a plain one would also match) - if the player never opens it, the server falls back to consuming whichever matching stack(s) it meets first in inventory-slot order |
| `qe_api:blocks_traveled` | `distance` |
| `qe_api:item_used` | `item_id`, `amount` (default 1) - counts right-click uses (eating, shooting a bow, etc.) |
| `qe_api:brew_potion` | `potion_id`, `amount` (default 1) - counts completed brewing-stand cycles for the given potion type. Progress is credited to any player within 8 blocks of the brewing stand when the brew finishes (there's no reliable way to attribute a specific "owner" to a brewing stand) |
| `qe_api:mine_block` | `amount` (default 1); one of `block_id` or `block_tag` - counts blocks broken by the player |
| `qe_api:spell_cast` | (Spell Engine compat) `amount` (default 1); optional `spell_id`, `spell_pool`, or `spell_school` selector (any spell counts if none given) - see [Compatibility](#compatibility) |

**`provides_map`** (on `find_structure`, and `entity_kill` when `in_structure` is set): the moment
the player accepts the quest, they're handed a real vanilla treasure map (the same kind used for
buried treasure/ocean ruins) pointing at the nearest instance of that structure, searched from the
player's position at accept time. If no instance can be found within range, the quest still starts
normally - no map, no error. **The map is only ever granted once** per quest + task: repeatedly
accepting and dismissing (or even completing and being re-offered) the same quest will never hand
out a second copy - this is tracked per-player, separately from quest progress, specifically so it
survives the accept/decline cycle.

```json
{ "task": "qe_api:find_structure", "structure_id": "minecraft:mineshaft", "provides_map": true }
{ "task": "qe_api:entity_kill", "entity_id": "minecraft:zombie", "amount": 3, "in_structure": "minecraft:pillager_outpost", "provides_map": true }
```

**Multiplayer note**: quest progress is per-player - accepting, tracking, and claiming a quest are
all tied to your own player UUID, with no shared state by default. The one exception is
`qe_api:entity_kill`: when a player lands a kill, any other online player on the same scoreboard
team within 32 blocks *who has independently accepted the same quest* also gets their own
progress on that task credited (still subject to all of the task's own filters - damage type,
spell attribution, biome, etc.). Nothing else is shared - rewards are always claimed individually,
and no other task type credits teammates.

## Requirement types

Requirements must be met to *accept* a quest (checked when the player tries to start it).

| Type | Fields |
|---|---|
| `qe_api:has_advancement` | `advancement_id` |
| `qe_api:has_level` | `experience_level` |
| `qe_api:has_item` | `item_id`, `amount` (default 1) - checks inventory, armor, and offhand; does not consume the item |

## Reward types

| Type | Fields |
|---|---|
| `qe_api:experience` | `amount` |
| `qe_api:item` | `item_id`, `amount` (default 1), optional `functions` (list, loot-table-style item functions - see below) |
| `qe_api:status_effect` | `effect_id`, `duration` (seconds), `amplifier` (0 = level 1) |
| `qe_api:loot_table` | `loot_table_id` - rolls the table into the player's inventory |
| `qe_api:command` | `command`, optional `display_name`. Supports `{player}`, `{uuid}`, `{x}`, `{y}`, `{z}` placeholders, runs with permission level 2 |
| `qe_api:advancement` | `advancement_id` - grants it directly |
| `qe_api:skill_experience` | (Pufferfish's Skills compat) `skill_tree_id`, `amount`, optional `icon` (a texture, e.g. that skill tree's own category icon) - see [Compatibility](#compatibility) |
| `qe_api:skill_level` | (Pufferfish's Skills compat) `skill_tree_id`, `levels`, optional `icon` - see [Compatibility](#compatibility) |
| `qe_api:spell_scroll` | (Spell Engine compat) optional `spell_id` (bypasses random selection entirely), `pool` (a spell tag), `tier_min`/`tier_max` (default `1`/unbounded), `excluded_spells` (list), `amount` (default 1) - see [Compatibility](#compatibility) |
| `qe_api:enchant_randomly` | `level_cap` (default 1) - picks a random enchantment valid for a **player-chosen item** (see [Target-item rewards](#target-item-rewards) below), excluding ones already present |
| `qe_api:enchant_specific` | `enchantment_id`, `level` (default 1) - enchants a **player-chosen item**; only items that enchantment actually supports are offered |
| `qe_api:repair_item` | none - fully repairs a **player-chosen item**'s durability; only damaged items are offered |
| `qe_api:spell_bind` | (Spell Engine compat) `spell_id`, optional `clear_existing` (default `false`) - binds that spell onto a **player-chosen item**; keeps any spells already bound unless `clear_existing` is `true`, in which case they're removed first and only the reward's spell remains - see [Compatibility](#compatibility) |
| `qe_api:increase_power_level` | (Dungeon Difficulty compat) `amount`, `cap` - raises a **player-chosen item**'s existing power level, unlike `set_power_level` which applies to a freshly granted stack - see [Compatibility](#compatibility) |
| `qe_api:increase_enchant_slots` | (Enchant Limiter compat) `amount`, `cap` - grants a **player-chosen item** extra enchantment slots, existing enchantments untouched - see [Compatibility](#compatibility) |
| `qe_api:enhance_item` | `operations` (list) - bundles any of `enchant_randomly`, `enchant_specific`, `repair_item`, `spell_bind`, `increase_power_level`, `increase_enchant_slots` onto **one player-chosen item** at once, instead of picking a separate item per reward - see [Target-item rewards](#target-item-rewards) |
| `qe_api:set_quest_group` | `group` - records that the player has chosen this group for the granting entity's pool, optional `icon` (a texture, e.g. that path's own symbol - same convention as `skill_experience`/`skill_level`) - see [Quest paths](#quest-paths) |

### Target-item rewards

`enchant_randomly`, `enchant_specific`, `repair_item`, `spell_bind`, `increase_power_level`,
`increase_enchant_slots`, and `enhance_item` all apply to an item the player already owns, rather
than granting a new one. When claiming a quest with one of these, the GUI shows a slot bordered
with the same selection square used for reward choice pools - click it to open a picker over the
player's inventory (scrollable if there are more valid items than fit on screen) and choose which
one the reward applies to (auto-picked without opening the picker if only one valid item exists).
The server always re-checks that the chosen item is still valid before applying anything. These
aren't currently supported inside `reward_choice_pools` - only in a quest's flat `rewards` list.

`qe_api:enhance_item` bundles any number of the other five onto **one** player-chosen item, claimed
with a single item pick instead of one per reward:

```json
{
  "reward": "qe_api:enhance_item",
  "operations": [
    { "type": "qe_api:repair_item" },
    { "type": "qe_api:enchant_specific", "enchantment_id": "minecraft:sharpness", "level": 3 },
    { "type": "qe_api:increase_power_level", "amount": 2, "cap": 10 }
  ]
}
```

Each operation uses the same fields as its standalone reward (just `type` instead of `reward` as
the dispatch key) and is checked individually against the chosen item before being applied - one
that doesn't fit (e.g. `repair_item` on an item with no durability) is skipped with a warning
rather than blocking the others.

### Item reward functions (`qe_api:item`'s `functions` list)

| Type | Fields |
|---|---|
| `qe_api:set_enchantments` | `enchantments` (list of `{"id": ..., "level": ...}`), `add` (default `true`, merges with existing rather than replacing) |
| `qe_api:set_count` | `count` |
| `qe_api:set_name` | `name` (text component) |
| `qe_api:set_lore` | `lore` (list of text components) |
| `qe_api:set_components` | `components` - a raw data component patch, same format as loot tables |
| `qe_api:set_power_level` | (Dungeon Difficulty compat) `level` - see [Compatibility](#compatibility) |

## Reward choice pools (`reward_choice_pools` on a quest, optional)

An alternative to the flat, unconditional `rewards` list: a pool of reward options where the
player must manually pick exactly `pick` of them in the quest GUI before the Claim button
becomes available. This is additive - a quest's flat `rewards` are always granted regardless,
and each `reward_choice_pools` entry adds its own separate pick.

```json
{
  "rewards": [ { "reward": "qe_api:experience", "amount": 120 } ],
  "reward_choice_pools": [
    {
      "pick": 1,
      "options": [
        { "reward": "qe_api:item", "item_id": "minecraft:diamond", "amount": 2 },
        { "reward": "qe_api:item", "item_id": "minecraft:emerald", "amount": 6 },
        { "reward": "qe_api:status_effect", "effect_id": "minecraft:luck", "duration": 300, "amplifier": 0 }
      ]
    }
  ]
}
```

| Field | Required | Notes |
|---|---|---|
| `options` | **yes** | A list of any reward type from the table above |
| `pick` | no, default `1` | How many of `options` the player must select before claiming |

A quest can have more than one pool (each with its own independent pick count). In the GUI,
picking with `pick: 1` behaves like radio buttons (selecting a new option replaces the current
one); with `pick > 1`, you must deselect one before selecting another once you've reached the
limit.

## Quest paths

By default, a pool's tiers are the same for every player - one quest per tier, weighted-random,
seeded by the entity's UUID. `quest_group` branches that: a quest with one set is only ever
offered to a player who's previously chosen that exact group *for this same pool* - a quest with
none is offered to everyone, same as today. Nothing else changes about tier weighting or
`follow_quest_order`; a grouped quest just isn't in the running at all until its group is chosen.

The player's choice is made via the reward `qe_api:set_quest_group`, normally as one option in a
`reward_choice_pools` entry so accepting the reward commits them to exactly one path:

```json
{
  "tier": 1,
  "tasks": [ { "task": "qe_api:bring_item", "item_id": "minecraft:book", "amount": 3 } ],
  "rewards": [ { "reward": "qe_api:experience", "amount": 100 } ],
  "reward_choice_pools": [
    {
      "pick": 1,
      "options": [
        { "reward": "qe_api:set_quest_group", "group": "fire" },
        { "reward": "qe_api:set_quest_group", "group": "frost" }
      ]
    }
  ]
}
```

Without `icon`, a `set_quest_group` option renders as a plain bullet + text row like most other
reward-choice-pool options. Give it one (e.g. that path's own symbol) and it renders like
`skill_experience`/`skill_level`'s icon instead - a 16x16 texture next to the text:

```json
{ "reward": "qe_api:set_quest_group", "group": "fire", "icon": "mymod:textures/gui/fire_path.png" }
```

```json
{ "tier": 2, "quest_group": "fire", "tasks": [ ... ], "rewards": [ ... ] }
{ "tier": 2, "quest_group": "frost", "tasks": [ ... ], "rewards": [ ... ] }
```

Once the player picks "fire" from that tier-1 quest, only the `quest_group: "fire"` quest is ever
a candidate for tier 2 onward in this pool - the `frost` one simply never comes up for them. See
the `wizard_paths` pool in the example quests (`ExampleQuestProvider`) for this end-to-end: a
`wizard_initiate` quest branching into `fire_apprentice`/`frost_apprentice`.

The choice is per-entity (scoped to whichever entity granted it, same as `completed_quests`), not
global to the player - the same player can be on a different path with a different quest-giving
entity. There's no in-game way to change a path once chosen; `/qe_api reset <entity> [player]`
clears it along with the rest of that player's progress on that entity.

## Compatibility

Four optional integrations with other mods. None of them are required dependencies - the mod
compiles and runs identically whether or not any of them are installed. Each one is compiled
against (`modCompileOnly`) but never bundled or forced at runtime; every task/reward that uses
one checks whether the target mod is actually loaded before doing anything, and just logs a
warning and no-ops (skips that one reward, or never matches that one task/requirement) if it
isn't - it never crashes or breaks the rest of the quest.

### Pufferfish's Skills

Grant experience or whole levels directly in one of that mod's skill trees ("categories").

```json
{ "reward": "qe_api:skill_experience", "skill_tree_id": "mymod:combat", "amount": 200, "icon": "mymod:textures/gui/icon.png" }
{ "reward": "qe_api:skill_level", "skill_tree_id": "mymod:combat", "levels": 1, "icon": "mymod:textures/gui/icon.png" }
```

`skill_level` has no direct "add a level" call in Pufferfish's Skills API, so it computes the
extra experience needed to reach `current_level + levels` and grants that.

Pufferfish's Skills doesn't expose a category's icon through its stable cross-mod API (the
icon data is internal, client-only, and only synced lazily per-category, per-player - not
something reliably queryable for an arbitrary skill tree from another mod). The optional `icon`
field lets you reference that skill tree's own icon texture directly instead - check the skill
tree's `category.json` for its `icon.data.texture` path. Falls back to a generic experience
bottle icon if omitted.

### Spell Engine

- **Task** `qe_api:spell_cast` - counts successful spell casts/releases. Filter with `spell_id`
  (an exact spell), `spell_pool` (a spell tag), or `spell_school` (e.g. `spell_power:fire`) - any
  spell counts if none of the three are given.
- **`entity_kill`'s `in_spell_id`/`in_spell_pool`/`in_spell_school`** - require the kill to be
  attributed to a spell matching the selector (same precedence as `spell_cast`: exact id, then
  pool, then school). **Best-effort, not exact**: Spell Engine doesn't record which spell dealt a
  given hit of damage, so this checks whether the killing player successfully cast a matching
  spell within the last 40 ticks (2 seconds) of the kill. Good enough for "you must be the one
  casting spells to get credit," not airtight against edge cases (a very late kill from a slow
  projectile, or another player finishing off the same target right after you cast).
- When a spell is shown in the quest GUI (the `spell_cast` task's icon, or `entity_kill`'s
  attribution icon), it renders that spell's actual icon and tooltip, matching Spell Engine's own
  presentation, and the task's description text shows the spell's real translated name instead of
  its raw `spell_id`. For a `spell_pool` selector, the icon rotates through every spell in the
  pool (same 3-second cadence as `entity_kill`'s entity-tag rotation) - rotation pauses while
  you're hovering the icon so its tooltip doesn't change to a different spell mid-read. Spell
  Power (a hard dependency of Spell Engine) doesn't register a distinct icon per school, so a
  `spell_school` selector instead shows Spell Power's generic mob-effect icon, with the school's
  name conveyed in the task text.
- **Reward** `qe_api:spell_scroll` - grants a Spell Engine spell scroll. By default rolls a random
  spell matching `pool`/`tier_min`/`tier_max`/`excluded_spells`; set `spell_id` to bypass random
  selection and always grant that exact spell. The scroll's spell container, item model, rarity,
  and display name are all set up via Spell Engine's own scroll-creation code, the same as a
  naturally-found scroll - the reward text shows the scroll's real item name (e.g. "Frost Spell
  Scroll"), not the raw pool tag. Since the actual spell is only rolled server-side at claim time,
  the quest GUI preview doesn't resolve one either - but it does apply the pool's own item model
  (Spell Engine's scroll models are keyed per-pool, not per-spell), so it still previews accurately.
  **Note**: content mods commonly register a *separate* tag per item type for the same theme (e.g.
  Wizards' `spell_book/frost` for the spell book vs `spell_scroll/frost` for scrolls) - make sure
  `pool` here references the scroll-specific tag, not the book one, or the granted scroll's model/
  name will resolve to the wrong item.
- **Reward** `qe_api:spell_bind` - binds one specific spell onto a **player-chosen item** (see
  [Target-item rewards](#target-item-rewards)), making it a spell container if it isn't one yet.
  Any spells already bound to that item are kept - it's the same "add to the container" logic
  Spell Engine's own random spellbinding loot function uses, just with a fixed spell instead of a
  random roll. Set `clear_existing: true` to remove all of the item's existing spells first, so
  only the reward's spell remains bound afterward.

```json
{ "task": "qe_api:spell_cast", "spell_id": "mymod:fireball", "amount": 5 }
{ "task": "qe_api:spell_cast", "spell_pool": "mymod:spell_book/frost", "amount": 5 }
{ "task": "qe_api:entity_kill", "entity_id": "minecraft:zombie", "amount": 3, "in_spell_id": "mymod:fireball" }
{ "task": "qe_api:entity_kill", "entity_id": "minecraft:zombie", "amount": 3, "in_spell_pool": "mymod:spell_book/frost" }
{ "reward": "qe_api:spell_scroll", "pool": "mymod:spell_scroll/frost", "tier_min": 1, "tier_max": 3 }
{ "reward": "qe_api:spell_bind", "spell_id": "mymod:fireball" }
{ "reward": "qe_api:spell_bind", "spell_id": "mymod:fireball", "clear_existing": true }
```

### Dungeon Difficulty

Apply Dungeon Difficulty's power-level item scaling as an item reward function - this isn't a
plain data component (Dungeon Difficulty computes the actual attribute values from its own
config-driven pattern matching based on item type/rarity), so it needs the real integration
rather than `set_components`:

```json
{
  "reward": "qe_api:item",
  "item_id": "minecraft:diamond_sword",
  "functions": [ { "function": "qe_api:set_power_level", "level": 5 } ]
}
```

When an item reward's `functions` include `set_power_level`, the quest GUI overlays Dungeon
Difficulty's own power-level symbol in the top-right corner of the item icon (only if Dungeon
Difficulty is actually loaded, since it's the one supplying that texture).

`set_power_level` only ever applies to a freshly granted item. To raise the power level of an item
the player already has, use the **reward** `qe_api:increase_power_level` instead - it reads
whatever level the player-chosen item currently has (0 if unscaled) and raises it by `amount`, up
to `cap`:

```json
{ "reward": "qe_api:increase_power_level", "amount": 3, "cap": 10 }
```

Dungeon Difficulty also scales *entities*, not just items - mobs spawned in a dimension or
structure a pack's config flags as dangerous get buffed HP/damage and a matching power level. The
**task** `qe_api:entity_kill`'s `min_power_level` requires the kill to be at least that level, so a
"defeat 3 zombies" task can specifically mean dungeon-tier zombies rather than any zombie:

```json
{ "task": "qe_api:entity_kill", "entity_id": "minecraft:zombie", "amount": 1, "min_power_level": 3 }
```

### Enchant Limiter

[Enchant Limiter](https://modrinth.com/mod/enchant-limiter) caps how many enchantments an item can
carry via its own `enchant_limiter:limit` data component (default 3 if the item has none yet). The
**reward** `qe_api:increase_enchant_slots` raises that cap on a **player-chosen item** by `amount`,
up to `cap` - it only ever touches that one component, so existing enchantments on the item are
never removed:

```json
{ "reward": "qe_api:increase_enchant_slots", "amount": 1, "cap": 6 }
```

## Datagen

Extend `QuestProvider` and describe your quests with the builder API - it generates one JSON
file per quest plus the tag file grouping them, matching the format above exactly:

```java
public class MyQuestProvider extends QuestProvider {
    public MyQuestProvider(PackOutput output) {
        super(output, "mymod");
    }

    @Override
    protected void addQuests() {
        createPool("villager")
                .followOrder(true)
                .tier(1)
                    .quest("gather_wheat")
                        .name(Component.translatable("quest.mymod.gather_wheat.name"))
                        .task(bringItem(Items.WHEAT, 16))
                        .reward(experience(50))
                        .rewardChoicePool(1, item(Items.DIAMOND, 2), item(Items.EMERALD, 6))
                        .weight(100)
                        .add()
                .build();
    }
}
```

See `common/src/main/java/com/qeapi/datagen/ExampleQuestProvider.java` for a full worked
example (the `villager` and `explorer` pools shipped with this mod, including a `brewPotion(...)`
task and a `rewardChoicePool(...)` reward pool, plus a `compat_examples` pool exercising
`mineBlock(...)`, `spellCast(...)`, `skillExperience(...)`/`skillLevel(...)`,
`SetPowerLevelFunction`, `enhanceItem(...)` (`master_smith`, bundling `repairItem()` +
`enchantSpecific(...)` + `increasePowerLevel(...)` onto one item), and `spellBind(...,
clearExisting: true)` (`spell_cleanse`) - each gated with `.requiredMod(...)` so it only shows up
if that mod is actually installed), and `common/src/main/java/com/qeapi/datagen/QuestProvider.java`
for every available task / requirement / reward helper method.

Wiring per loader:
- **Fabric**: implement `DataGeneratorEntrypoint`, register it under `"fabric-datagen"` in
  `fabric.mod.json`, then run it via the `runDatagen` Gradle task.
- **NeoForge**: listen for `GatherDataEvent` on the mod event bus and call
  `event.getGenerator().addProvider(...)`.

**Important:** point the datagen output at its own directory (this project uses
`common/src/main/generated`, wired in via an extra `sourceSets.main.resources.srcDir` in
`common/build.gradle`) - never at `src/main/resources` directly. Vanilla's data generator
deletes anything under its output root that isn't freshly written by a registered provider in
that run, so pointing it at a folder with hand-authored files (assignments, textures, lang)
will silently delete them.

## Commands

All require permission level 2 (ops). `<entity>` accepts any vanilla entity selector (`@e[type=minecraft:villager,limit=1,sort=nearest]`, a UUID, `@n`, ...).

- `/qe_api give_tag <entity> <tag>` - assign a quest tag directly, for testing
  ```
  /qe_api give_tag @n[type=minecraft:villager] mymod:villager
  ```
- `/qe_api open_gui <entity>` - open the quest GUI for that entity as yourself
  ```
  /qe_api open_gui @n[type=minecraft:villager]
  ```
- `/qe_api list_pools` - list every loaded quest and tag
  ```
  /qe_api list_pools
  ```
- `/qe_api reload` - reminder to use vanilla `/reload`
  ```
  /qe_api reload
  ```
- `/qe_api spawn <entity_type> <tag>` - summon an entity of that type at your position and give it
  a quest tag in one step (`give_tag` still needs an entity to already exist)
  ```
  /qe_api spawn minecraft:villager mymod:villager
  ```
- `/qe_api reset <entity> [player]` - forget everything the player has done with that entity's
  quests (in-progress quest, completed quests, chosen quest path, and the accept/decline
  cooldown), as if they'd never interacted with it. Defaults to yourself if `player` is omitted
  ```
  /qe_api reset @n[type=minecraft:villager]
  /qe_api reset @n[type=minecraft:villager] SomePlayerName
  ```
- `/qe_api force_complete <entity> <quest_id> [player]` - grants a quest's rewards directly,
  skipping task/requirement progress entirely - for testing reward output without playing the quest
  out. `reward_choice_pools` are left unpicked and target-item rewards (`enchant_specific`,
  `spell_bind`, etc. - see [Target-item rewards](#target-item-rewards)) are skipped with a warning,
  since there's no GUI picker to choose an item from here - test those two through the normal
  accept-and-claim flow instead. Defaults to yourself if `player` is omitted
  ```
  /qe_api force_complete @n[type=minecraft:villager] mymod:villager/gather_wheat
  /qe_api force_complete @n[type=minecraft:villager] mymod:villager/gather_wheat SomePlayerName
  ```

## GUI

Right-click a quest entity to open the quest screen (villagers/wandering traders get a small
button on their trade screen instead, so their normal trading isn't blocked). The exclamation
mark above a quest entity's head is red and bobbing when quests are available, grey and static
while a quest is in progress or once everything's been claimed, and green and bobbing when a
completed quest is ready to claim. Like vanilla inventory/container screens, opening it dims the
game world behind it.

`textures/gui/selection.png` is the selection-square sprite: 22x22, with the inner 16x16 fully
transparent so it frames a 16x16 item icon without covering it. It's used for reward-choice-pool
selection and the item-picker overlay (see [Target-item rewards](#target-item-rewards) and
`qe_api:bring_item`'s picker) - replace it with your own art to match a custom `quests.png` theme.

If a player completes a quest offered by a villager, that villager also gains trading XP toward
their profession level (`quest tier * 5` XP, applied immediately - not on vanilla's usual
deferred timer, so their trades update right away).

## Mod-author API (for custom entities)

Depend on this mod in your dev environment and implement `QuestEntity` on your own entity
class to hook it into the quest system directly, instead of going through the
data-driven assignment system:

```java
public class MyQuestNpc extends PathfinderMob implements QuestEntity {
    @Override
    public ResourceLocation getQuestPoolId() {
        // A tag reference, same as entity_quest_assignment's quest_pool
        return ResourceLocation.fromNamespaceAndPath("mymod", "my_npc_quests");
    }
}
```

`QuestEntity` also has `canProvideQuests()`, `shouldShowQuestMarker()`, and
`getQuestInteractionPriority()` you can override, all with sensible defaults.

For lower-level programmatic control (accept/dismiss/complete a quest for a player, query
available quests, etc.), see the static methods on
`com.qeapi.api.QuestEntityAccess`.

## License

MIT - see [LICENSE](LICENSE).
