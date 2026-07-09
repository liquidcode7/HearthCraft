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

**Data model — additive, not a replacement.** An audit of the current codebase
before writing the plan found that a straight "replace `primaryBoost`/
`secondaryBoost` with a `ranks` list" schema change would break more than it needs
to: 4 test files construct `Recipe(...)` fixtures directly (none of them currently
pass `primaryBoost`/`secondaryBoost`, relying on the 0 default — removing the flat
fields breaks all four), `BalanceHarness.kt` reads `recipe.primaryBoost` directly for
food-boost simulation, and two dev tools (`tools/recipe-browser/format.js`,
`tools/sim/food_model.js` + `tier_grade_sweep.js`) read `primaryBoost` straight out of
`recipes.json`. None of that needs to break.

Instead, `ranks` is a **new, optional field** — `primaryBoost`/`secondaryBoost` stay
exactly as they are today (the recipe's Base-rank values, same JSON keys, same
default of 0):

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
    val primaryBoost: Int = 0,     // unchanged — also IS the Base rank's value
    val secondaryBoost: Int = 0,   // unchanged
    // ...unchanged fields (band, recipeClass, method, tier, cookLevel, ingredients,
    // heroIngredient, hazardEffect, description, penalty, etc.)...
    val ranks: List<RecipeRank> = emptyList()   // NEW, optional. Empty = single-rank,
                                                 // unchanged behavior from today.
)
```

A recipe with an empty `ranks` list behaves exactly as it does today — nothing about
it changes unless content is explicitly authored for it. Add a resolver:

```kotlin
// Returns the ordinal into `ranks` (or 0 for an empty/single-rank recipe) — the
// index, not the RecipeRank itself, since CookingWorker needs to persist exactly
// this value onto PreparedFood (see §3).
fun Recipe.resolvedRankOrdinal(playerCookLevel: Int): Int {
    if (ranks.isEmpty()) return 0
    val tierRelative = (playerCookLevel - cookLevel).coerceAtLeast(0)
    return ranks.indices.filter { ranks[it].cookLevelOffset <= tierRelative }
        .maxOrNull() ?: 0
}
```

Only consumers that need rank-aware behavior call `resolvedRankOrdinal(...)`;
everything else keeps reading `recipe.primaryBoost` directly and keeps working, just
without reflecting ranks above Base until (if ever) it's updated. `BalanceHarness.kt`
and the two dev tools fall into this category — not required by this spec, may be
updated later as a separate, optional follow-up.

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

**Scope — food recipes only, corrected from the earlier draft.** The 03 Jul draft
assumed this applies uniformly to food, draught, and HoH since they share the
`Recipe` class. Checking how each class's boost actually gets used in the live game
found that's not true:

- **Draughts** (15 recipes) — their gameplay effect (armor penetration, etc.) comes
  from a flat player-selected potency slider in `MissionsScreen`
  (`BandViewModel.setDraught(potency)`, values None/45/65), entirely independent of
  which specific draught recipe was cooked. `primaryBoost` is 0 for 14 of 15 draughts
  already (the one exception, "Contemplative Tea" at `wil 2`, looks like a data
  artifact, not an intentional mechanic — left alone, out of scope here).
- **HoH** (20 recipes) — healing is entirely `treatsWoundTypes`-based (which wound
  categories a preparation treats); none of them have a nonzero `primaryBoost` at
  all.
- **Food** (36 recipes) — the only class where `primaryBoost`/`secondaryBoost`
  actually flows into combat, via `BandRepository.memberInputsForBand`'s
  `statBonusFor(...)` call. Of these, 35 have a real `primaryStat` to scale; one
  ("Ember Porridge") is a hazard-effect utility dish like the draughts above and
  is a natural candidate to simply never get a `ranks` list authored for it — the
  additive schema handles that with zero special-casing.

Recipe Rank content is authored for **food recipes with a primaryStat** only.
Draughts, HoH, and hazard-only/penalty food recipes are unaffected — not blocked
from ever getting ranks later if their mechanics change, just not part of this pass.

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

This needs a **manual** Room `Migration` (current version 21 → 22, not an
`AutoMigration`) — confirmed against the real schema
(`app/schemas/.../21.json`), `prepared_food`'s primary key is
`PRIMARY KEY(recipeId, grade)`; SQLite has no `ALTER TABLE` support for changing a
primary key, so the migration has to follow the same recreate-table pattern already
used elsewhere in this codebase (e.g. `Migration16To17.kt`): create a new table with
the 3-column primary key, copy existing rows across with `rank` defaulted to `0`
(Base), drop the old table, rename. Existing prepared-food rows becoming Base-rank on
migration is pragmatically fine — Recipe Rank doesn't exist in any build those rows
could have come from.

A second small helper distinguishes the two ways `ranks` gets read:
`resolvedRankOrdinal(playerCookLevel)` (above) is used exactly once, at
cook-completion time, to decide what to *store*. Reading it back later must use the
*stored* ordinal directly, not re-resolve against the player's current (possibly
higher) cook level:

```kotlin
fun Recipe.rankAt(ordinal: Int): RecipeRank =
    ranks.getOrNull(ordinal) ?: RecipeRank(cookLevelOffset = 0, primaryBoost, secondaryBoost)
```

## 4. Consumers that need updating

Found via `grep -rln "\.primaryBoost\|\.secondaryBoost\|\.cookLevel\b"` against the
current codebase, then narrowed to the food-only call sites (the 03 Jul draft's
consumer list is both stale — several of these files didn't exist yet — and too
broad, since it assumed all three recipe classes needed changes):

- **`CookingWorker.kt`** — at cook-completion time, for food recipes only, call
  `recipe.resolvedRankOrdinal(playerCookLevel)` and pass it to
  `inventory.addPreparedFood(recipeId, dishGrade, rankOrdinal)` (signature gains a
  `rank` parameter, default 0 — draught/HoH call sites are unaffected and keep
  passing nothing extra).
- **`InventoryRepository`** (wherever `addPreparedFood`/`observePreparedFood`/
  `preparedFoodQty` live) — needs the new `rank` parameter threaded through.
- **`PantryViewModel.kt` / `InventoryViewModel.kt`** — `preparedFood` mapping needs to
  resolve `PreparedFoodDetail`'s displayed name (prefix + recipe name) and boost
  values via `recipe.rankAt(pf.rank)` instead of reading `recipe.primaryBoost`
  directly. `PreparedFoodDetail` (`UiModels.kt`) needs a `rank: Int` field so screens
  can render the prefix.
- **`BandRepository.kt`** (`statBonusFor(...)` call site, line ~128) — combat stat
  application must read the boost via `recipe.rankAt(food.rank)`, not a flat
  `food.primaryBoost`, for the food this member has equipped.
- **`RecipeBookScreen.kt`**, **`KitchenScreen.kt`** / **`KitchenViewModel.kt`**,
  **`MissionsScreen.kt`** — anywhere a food recipe's or prepared-food's boost/name is
  displayed needs to resolve and show the current rank's prefix + boost. Draught/HoH
  display branches in these same files (hazard-effect labels, wound-type labels) are
  untouched. Kitchen's recipe detail panel (`RecipeDetailPanel`) is the natural place
  to also preview the *next* rank-up (a stretch goal, not required for this spec —
  see Out of Scope).
- **`JournalViewModel.kt`** (`sortedWith(compareBy({ it.tier }, { it.cookLevel }, ...
  ))`) — sorts by the recipe's base `cookLevel` (tier-entry threshold), which is
  unchanged by this spec; no functional change needed here.
- **`BalanceHarness.kt`, `tools/recipe-browser/format.js`, `tools/sim/food_model.js`,
  `tools/sim/tier_grade_sweep.js`** — all read `primaryBoost` directly and keep
  compiling/working unchanged (they'll just reflect Base rank only, same as every
  recipe does today, until/unless someone updates them separately). Not required by
  this spec.

## 5. Existing content: ranks are authored onto it, not consolidated

Two different questions here, worth keeping separate:

**Should existing recipes get a `ranks` list authored?** Yes — this is the whole
point, and matches Wes's own worked example directly ("brookcress bannock becomes
insightful brookcress bannock"). `ranks` gets authored onto the eligible existing
food recipes for the one live band, Greycloaks (see the implementation plan for the
exact list and numbers). Mithlost and Undermarch recipes are left unranked for now,
since neither band has missions yet — not blocked from getting ranks later, just not
part of this pass (same reasoning as the draught/HoH exclusion in §2).

**Should near-duplicate existing recipes be merged into one family?** No. Checking
the actual current data (not the earlier draft's assumption of a clean per-stat
"ladder"): Greycloaks' T2 food recipes are mostly distinct dishes at different cook
levels with different stat pairs (a Will dish at level 4, four different dishes at
level 5, one at level 6) — there isn't a ladder shape to consolidate, except one
near-duplicate pair ("Hearth and Hops" and "Rendered Hearth Pie," both
Might+4/Vitality+2 at the same level 5). That pair is left alone rather than folded
into one recipe as a special case — each keeps its own identity and gets its own
independent `ranks` list.

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

- Unit tests for `resolvedRankOrdinal` and `rankAt` (given a recipe's `ranks` list, a
  player cook level, and the recipe's own `cookLevel`, return the correct ordinal/
  `RecipeRank`) — boundary cases at exactly a breakpoint, below the first breakpoint,
  above the last one, and the empty-`ranks` fallback.
- Unit tests for the cook-level/XP curve changes (`PlayerRepository`'s
  `totalXpForLevel`/`levelForTotalXp` for the COOKING track) confirming tier
  boundaries land at 20/40/60/80 and `MAX_LEVEL` behaves correctly at the new cap.
- A Room migration test for the `PreparedFood` schema change (v21→v22), verifying
  existing rows default to `rank = 0` and the new composite primary key holds.
- No automated Compose UI test framework exists in this repo — display changes
  (rank-prefixed names, resolved boost values) are manual-verification items.
