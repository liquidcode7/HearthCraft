# Kitchen/Pantry UX — Design Spec

**Status:** Approved by Wes, 09 Jul 2026. Group 5 of the 04 Jul 2026 audit follow-up roadmap
(see `docs/superpowers/plans/2026-07-04-audit-quick-fixes.md`'s "Roadmap" section) — the first
group not otherwise entangled with combat/balance work.

## Why

The roadmap listed this group as: "Scroll-jump-to-top fix, collapsible/filterable recipe
tiers, ingredient filters (quality/type/quantity/alphabetical) in Pantry." Confirmed directly
with Wes and by reading the current code:

- **Scroll-jump bug, confirmed:** `KitchenScreen.kt` has a `LaunchedEffect(selectedRecipe?.id)`
  that force-scrolls the Recipes page back to the absolute top every time a recipe is
  selected. If you're browsing deep in Tier 3, tapping any recipe yanks you back up to the
  detail panel, and you have to scroll all the way back down to keep browsing.
- **Recipes are already grouped by tier**, but every tier is always fully expanded (locked
  tiers just show grayed rows with a "Reach Lv X" label) — no collapse, no filtering.
- **Pantry (`PantryScreen.kt`) is a flat, unfiltered list** — no search, sort, or filter of
  any kind.

## Section A: Kitchen Recipes tab

**Sticky detail panel replaces the scroll-jump fix.** Rather than patching *when* the
force-scroll fires, remove the mechanism entirely: split the Recipes page into a fixed,
non-scrolling top section (the "Recipe Book"/"Pantry" nav buttons, the two kilns, and the
recipe detail panel) and a separately-scrollable section below it for the tiered recipe list.
Selecting any recipe — regardless of scroll position — just updates the pinned detail panel
in place. The current `LaunchedEffect(selectedRecipe?.id) { recipesScrollState.animateScrollTo(0) }`
is deleted outright, along with the single shared `recipesScrollState` that spans the whole
page; the fixed section needs no scroll state, and the recipe list gets its own independent
one.

**Collapsible tiers.** Each tier section header becomes tappable, toggling that tier's recipe
rows open/closed independently (accordion — not a single global expand-all/collapse-all).
Default state: unlocked tiers start expanded, locked tiers (below `cookingLevel`) start
collapsed — matches how a player would actually want to scan the list (see what you can cook,
collapse what you can't yet).

**Tier quick-jump.** A row of tier chips (T1/T2/T3/T4, only for tiers that exist in
`tieredRecipes`) above the list. Tapping one scrolls to and expands that tier, collapsing the
others. Tier stays the permanent outer grouping structure at all times (see Sort below) — this
chip row is the resolution of "filterable by tier" now that tier isn't itself a show/hide
filter.

**Filter chips, combinable.** Three independent, simultaneously-active toggle groups:
- **Cookable now** — hide recipes the player doesn't currently have ingredients for
  (reuses `KitchenViewModel.canCook(recipe, inventoryItems)`, already exists).
- **Class** — Food / Draught (`recipe.class` field, already exists — `"class": "food"` or
  `"class": "draught"` in `recipes.json`).
- **Stat** — Might / Agility / Vitality / Will, matching `recipe.primaryStat`/`secondaryStat`.

All three groups AND across groups: a recipe must satisfy every currently-active filter to
show (AND across groups, OR within a group's own multi-select — e.g. selecting both "Might"
and "Will" stat chips shows recipes matching either stat).

**Sort control, single-select, reorders rows *within* each tier — does not change grouping.**
By Tier (default; no reorder, list order as authored in `recipes.json`) / Alphabetical /
By Level (`recipe.cookLevel` ascending). Confirmed explicitly with Wes: tier remains the
permanent grouping structure regardless of sort mode — "Alphabetical" does not flatten the
list into one ungrouped A-Z sweep, it only reorders the rows inside each already-existing tier
section.

## Section B: Pantry

**Filter chips, combinable**, same two-groups-both-active pattern as Kitchen:
- **Quality grade** — Crude / Common / Fine / Superb / Pristine (`InventoryItem`'s existing
  `grade` field).
- **Stat** — Might / Agility / Vitality / Will (which stat the ingredient contributes —
  matches Kitchen's stat filter for a consistent mental model between the two screens).

**Sort control, single-select:** Quantity (most-owned first — the current implicit/default
order) / Alphabetical.

**Explicitly out of scope:** Prepared Food (the second list already on `PantryScreen.kt`)
is untouched. Confirmed with Wes — that list is typically much shorter and the roadmap's ask
was specifically about ingredients.

## Section C: Architecture

**Kitchen** — `KitchenViewModel` (already exists, already owns `tieredRecipes`) gains three
new pieces of state:
- `expandedTiers: StateFlow<Set<String>>` — tier labels currently expanded. Initialized once
  `tieredRecipes`/`cookingLevel` are known (unlocked → expanded, locked → collapsed), then
  user-toggleable per tier.
- `recipeFilters: StateFlow<RecipeFilterState>` — a data class: `cookableOnly: Boolean`,
  `classFilter: Set<String>` ("food"/"draught"), `statFilter: Set<String>` ("mig"/"agi"/"vit"/"wil").
- `recipeSort: StateFlow<RecipeSortMode>` — enum `{ TIER, ALPHABETICAL, LEVEL }`.

A derived/combined flow applies `recipeFilters` + `recipeSort` to `tieredRecipes` to produce
what the screen actually renders — tier grouping itself is untouched by this derivation, only
which rows appear and their order within each tier.

**Pantry** — gets its own new, small `PantryViewModel`, deliberately *not* added to the
existing `InventoryViewModel`. Confirmed via `grep`: `InventoryViewModel` is also used by
`HouseOfHealingScreen.kt` and `MissionsScreen.kt` — adding Pantry-screen-only UI state
(filter chips, sort mode) to it would leak Pantry-specific concerns into two unrelated
screens. `PantryViewModel` holds just this screen's filter/sort UI state and reads ingredient
data from the same repository `InventoryViewModel` already uses.

**Shared UI components** — the filter-chip row and the sort selector become small reusable
composables (e.g. `FilterChipRow`, `SortSelector`) used by both screens, rather than two
independent implementations of the same interaction pattern.

## Explicitly out of scope

- Prepared Food filtering on Pantry (Section B).
- Any change to how tiers/recipes are *authored* (`recipes.json` itself) — this is purely a
  display/filtering layer on top of existing data.
- Ingredient filtering by source/gatheringMode (forage/farm/husbandry/etc.) — considered and
  explicitly declined in favor of the stat-based filter, for consistency with Kitchen's own
  filter axis.
- Group 6 (Economy pass) and the ingredient-variety-vs-recipe-count pacing concern raised in
  the 07-08 Jul session — a related but separate discussion, not folded into this group.

## Testing

- Unit tests for the new derived filter/sort logic in `KitchenViewModel` (given a
  `tieredRecipes` fixture and various `RecipeFilterState`/`RecipeSortMode` combinations, assert
  the correct subset/order comes out, and that tier grouping is preserved regardless of sort
  mode).
- Unit tests for `PantryViewModel`'s equivalent filter/sort logic.
- No automated Compose UI test infrastructure exists in this repo (consistent with every prior
  UI-touching plan this cycle) — the sticky-panel layout, collapsible-tier interaction, and
  filter-chip UI itself are manual-verification items.
