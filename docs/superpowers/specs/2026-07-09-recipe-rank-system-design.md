# Recipe Rank System — Design Spec

**Status:** Approved by Wes, 09 Jul 2026. Item 1 of Group 6 (Economy pass) of the
04 Jul 2026 audit follow-up roadmap. Supersedes the earlier draft at
`docs/superpowers/specs/2026-07-03-recipe-rank-system-design.md`, which was designed
before the current grade-multiplier values and the current recipe catalog existed —
the core mechanism carries over, the numbers and naming below are new.

## Why

The original "cook has level requirements within tiers" idea would, taken literally,
require authoring a brand-new recipe at every level gate — content debt that only
grows as more tiers are authored. The alternative: a single recipe scales in place —
same ingredients, same recipe entry, but its stat output rises and its displayed name
changes as the player's cook level climbs within the tier they're already in.

This also gives cooking XP a felt payoff beyond the invisible quality-ceiling creep
(`QualityUtils.cookCeiling`) that already governs how high a grade a dish can reach —
Recipe Rank is a second, visible axis of "getting better at cooking," independent of
ingredient quality.

This is distinct from Three-Lock Progression (`design/master-design.md` §8.4: band
level + cook level + Grimoire, gating access to a whole new *tier*) — that system is
unrelated and already exists in design (missing only its own cook-level check in
`isRecipeVisible()`, out of scope here). Recipe Rank is about depth *within* an
already-unlocked tier, not the tier gate itself.

## 1. Cook-level / tier-width restructure

This spec also revises the underlying cook-level curve in `PlayerRepository.kt`,
since Recipe Rank's breakpoints need a stable, predictable tier width to hang off:

- **Every cook tier is a flat 20 levels wide**, replacing the current unequal-width
  curve (`COOK_TIER_BOUNDARIES = setOf(5, 10, 16, 23, 31, 41)`, tier widths 4/5/6/7...).
  New boundaries: tier transitions at levels 20, 40, 60, 80.
- **`MAX_LEVEL` rises from 50 to 100** (5 tiers' worth) — not an attempt to answer
  "how many cooking tiers does the full campaign need" (`design/master-design.md`
  §8.2 leaves that explicitly open); just enough headroom for what's likely to be
  authored in the near term, extendable again later the same way tier count itself
  already is.
- The existing XP-per-level formula shape is unchanged (`25 × level^1.2` for the
  COOKING track, `PlayerRepository.kt` line ~187) — only its domain extends to 100,
  and the tier-transition XP "wall" multiplier (1.8×, applied when leveling out of a
  tier's final level) recomputes against the new boundaries instead of the old ones.
- This does **not** attempt to resolve "how many total cooking tiers the campaign
  needs" — that stays an open question. It only fixes the *width* of whichever tiers
  get authored.

## 2. Recipe Rank mechanic

**Data model.** `Recipe` (`data/model/Recipe.kt`) currently stores a flat
`primaryBoost: Int` / `secondaryBoost: Int` per recipe. Replace with an embedded list
of ranks:

```kotlin
@Serializable
data class RecipeRank(
    val cookLevelOffset: Int,   // 0 = base rank, unlocked as soon as the recipe's
                                // tier itself is unlocked. Positive values are cook
                                // levels above the tier's own entry threshold.
    val primaryBoost: Int,
    val secondaryBoost: Int = 0
)

@Serializable
data class Recipe(
    val id: String,
    val name: String,
    // ...unchanged fields (band, recipeClass, method, tier, cookLevel, ingredients,
    // heroIngredient, hazardEffect, description, penalty, etc.)...
    val ranks: List<RecipeRank>   // always at least one entry (the base rank)
)
```

A recipe is not required to define more than one rank — minor/utility dishes can stay
single-rank. Resolving "what does this recipe do at cook level L" is: take
`tierRelative = L - recipe.cookLevel` (the recipe's own tier-entry threshold, already
its `cookLevel` field), then pick the highest rank in `ranks` whose `cookLevelOffset`
is `<=` that value (clamped to 0 minimum).

**Breakpoints.** 3 rank-up intervals per 20-level tier, roughly evenly spaced:
`cookLevelOffset` values of **0 / 7 / 14 / 20** (4 total ranks: base + 3 upgrades).
A recipe with fewer than 4 authored ranks simply stops scaling past its last one.

**Names.** Rank names are a **prefix on the recipe's own display name**, not a
separate badge or suffix — e.g. "Brookcress Bannock" → "Insightful Brookcress
Bannock" → "Inspired Brookcress Bannock" → "Inimitable Brookcress Bannock". The same
3 words are reused across every recipe family (one small vocabulary to author, not
bespoke names per dish):

| Rank | `cookLevelOffset` | Name prefix | Placeholder stat multiplier |
|---|---|---|---|
| Base | 0 | *(none)* | 1.0x |
| Rank 2 | 7 | Insightful | 1.15x |
| Rank 3 | 14 | Inspired | 1.3x |
| Rank 4 (capstone) | 20 | Inimitable | 1.5x |

Multiplier values are placeholders (consistent with every other numeric value in
this project — pending validation via `BalanceHarness`, not hand-tuned here). They
apply to the recipe's base `primaryBoost`/`secondaryBoost` — i.e. `ranks[n].primaryBoost
= round(ranks[0].primaryBoost × multiplier[n])` when authoring content, though the
authored value is what actually ships (the multiplier column is the authoring
guideline, not a runtime calculation).

**Interaction with Grade.** Rank and Grade are independent, multiplicative axes on
the same dish, unchanged from the mechanism's original framing:

```
totalBoost = rankBoost × gradeMultiplier(grade)
```

using the current live `GRADE_MULTIPLIER` values in `QualityUtils.kt`
(`[0.8, 0.9, 1.0, 1.15, 1.3]` for Crude…Pristine — the earlier draft's `[0.7…1.7]`
values are stale and not used). Neither axis alone maxes out a dish: an Inimitable
dish cooked with Crude ingredients is still held back; a Base-rank dish cooked with
Pristine ingredients doesn't hit the ceiling either. Rank rewards staying in a tier
and leveling cooking skill; Grade rewards the ingredient-quality chase.

**Scope.** Applies to all three recipe classes — food, draught, and HoH — since they
already share the same `Recipe` data class and the same tier-gating concept.

## 3. Rank is locked in at cook time (like Grade)

Grade is already resolved once, at the moment a dish finishes cooking, and stored
per prepared-food batch — it never changes afterward even if the player later
gathers higher-grade ingredients. Rank works the same way: **a batch of food cooked
at rank Insightful stays Insightful forever**, even if the player's cook level later
reaches Inspired. This matches how Wes described the mechanic ("an Insightful
Brookcress Bannock Pristine would be the strongest you could get *at that
interval*") and avoids the alternative (resolving rank live from the player's
*current* cook level every time the food is read) — which would mean a stockpiled
batch quietly gets stronger while sitting in the pantry without being re-cooked, an
odd emergent behavior nothing else in the game does.

**Why this needs a schema change:** unlike Grade, boost values today are *not*
snapshotted anywhere — `PantryViewModel`, `InventoryViewModel`, `BandRepository`, and
`MissionsScreen` all look up `gameData.recipes.find { it.id == pf.recipeId }` fresh
each time and read `.primaryBoost`/`.secondaryBoost` directly off the live `Recipe`.
That pattern works today because a recipe currently has exactly one boost value. Once
a recipe has several ranks, *something* has to remember which rank a given batch of
prepared food actually is, and the only thing that already persists per-batch
identity is `PreparedFood`'s composite primary key.

**Schema change:** `PreparedFood` (`data/db/PreparedFood.kt`) gains a `rank: Int`
field (ordinal into the recipe's `ranks` list, same pattern as `grade: Int` already
being an ordinal into `Grade`), and `rank` joins the primary key alongside
`recipeId`/`grade` — a player can hold a Base-rank batch and an Insightful-rank batch
of the same recipe at the same grade simultaneously, and they must not merge into one
stack:

```kotlin
@Entity(tableName = "prepared_food", primaryKeys = ["recipeId", "grade", "rank"])
data class PreparedFood(
    val recipeId: String,
    val grade: Int = 0,
    val rank: Int = 0,     // NEW — ordinal into Recipe.ranks; resolved and stored at
                            // cook-completion time, same lifecycle as grade
    val quantity: Int = 0
)
```

This needs a Room DB migration (current version 21 → 22) adding the `rank` column and
updating the primary key. Existing prepared-food rows get `rank = 0` (Base) by
default on migration — pragmatically fine, since Recipe Rank doesn't exist in any
build those rows could have come from.

## 4. Consumers that need updating

Found via `grep -rln "\.primaryBoost\|\.secondaryBoost\|\.cookLevel\b"` against the
current codebase (the 03 Jul draft's consumer list is stale — several of these files
didn't exist yet):

- **`CookingWorker.kt`** — at cook-completion time, resolve the player's current rank
  for the recipe (`tierRelative = playerCookLevel - recipe.cookLevel`, pick the
  matching `RecipeRank`) and pass its ordinal to `inventory.addPreparedFood(recipeId,
  dishGrade, rank)` (signature gains a `rank` parameter).
- **`InventoryRepository`** (wherever `addPreparedFood`/`observePreparedFood`/
  `preparedFoodQty` live) — these need the new `rank` parameter threaded through.
- **`PantryViewModel.kt` / `InventoryViewModel.kt`** — `preparedFood` mapping needs to
  resolve `PreparedFoodDetail`'s displayed name (prefix + recipe name) and boost
  values from `recipe.ranks[pf.rank]` instead of `recipe.primaryBoost` directly.
  `PreparedFoodDetail` (`UiModels.kt`) needs a `rank: Int` field so screens can render
  the prefix.
- **`BandRepository.kt`** (`statBonusFor(...)` call site, line ~128) — combat stat
  application must read the boost from the specific prepared-food batch's resolved
  rank, not a flat `recipe.primaryBoost`.
- **`RecipeBookScreen.kt`**, **`KitchenScreen.kt`** / **`KitchenViewModel.kt`**,
  **`MissionsScreen.kt`** — anywhere a recipe's or prepared-food's boost/name is
  displayed needs to resolve and show the current rank's prefix + boost, not the base
  values. Kitchen's recipe detail panel (`RecipeDetailPanel`) is the natural place to
  also preview the *next* rank-up (a stretch goal, not required for this spec — see
  Out of Scope).
- **`JournalViewModel.kt`** (`sortedWith(compareBy({ it.tier }, { it.cookLevel }, ...
  ))`) — sorts by the recipe's base `cookLevel` (tier-entry threshold), which is
  unchanged by this spec; no functional change needed here, just confirming it still
  compiles against the new `Recipe` shape.

## 5. T2 migration

T2's current recipes are **not** consolidated by this spec. Checking the actual
current data (not the earlier draft's assumption of a clean per-stat "ladder"):
Greycloaks' T2 food recipes are mostly distinct dishes at different cook levels with
different stat pairs (a Will dish at level 4, four different dishes at level 5, one
at level 6) — there isn't a ladder shape to consolidate, except one near-duplicate
pair ("Hearth and Hops" and "Rendered Hearth Pie," both Might+4/Vitality+2 at the same
level 5) that's left alone rather than folded in as a special case. **All existing
recipes stay single-rank as-is.** New recipes authored going forward use the rank
ladder from the start.

## Explicitly out of scope

- Rank-preview UI (showing the next rank's name/boost before you reach it) — nice to
  have, not required for the mechanic to function.
- Reworking T2's existing content into rank families.
- Deciding the total number of cooking tiers for the full campaign — stays open per
  `design/master-design.md` §8.2.
- Exact rank multiplier values beyond the placeholder table in §2 — pending
  `BalanceHarness` validation, same caveat as every other numeric value in this
  project.
- Recipe Rank's interaction with the deferred Gathering Aid item or the
  not-yet-implemented Three-Lock cook-level gate in `isRecipeVisible()` — both are
  separate, already-tracked deferred items.

## Testing

- Unit tests for the pure rank-resolution function (given a recipe's `ranks` list, a
  player cook level, and the recipe's own `cookLevel`, return the correct
  `RecipeRank`) — boundary cases at exactly a breakpoint, below the first breakpoint,
  above the last one.
- Unit tests for the cook-level/XP curve changes (`PlayerRepository`'s
  `totalXpForLevel`/`levelForTotalXp` for the COOKING track) confirming tier
  boundaries land at 20/40/60/80 and `MAX_LEVEL` behaves correctly at the new cap.
- A Room migration test for the `PreparedFood` schema change (v21→v22), verifying
  existing rows default to `rank = 0` and the new composite primary key holds.
- No automated Compose UI test framework exists in this repo — display changes
  (rank-prefixed names, resolved boost values) are manual-verification items.
