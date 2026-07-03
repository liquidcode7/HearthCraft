# Recipe Rank System Design

**Status:** Draft — mechanism locked, numeric values are first-pass
placeholders pending sim calibration (same caveat as the companion
stat-growth-and-food-power design). Rank titles are explicitly unassigned —
naming is Wes's call, not invented here.

## 1. Problem

The original "cook has level requirements within tiers" idea (from the
stat-growth-and-food-power design) would, if built literally as separate
recipes per level gate, require authoring a large number of distinct
recipes — the current data already does this in a small way (T2 has 11 food
recipes spread across `cookLevel` 3/4/5/6, each an independent entry) and
that content burden only grows if every tier needs several fully distinct
dishes per gate.

The alternative: instead of unlocking a *new* recipe at each cook-level
breakpoint, a **single recipe scales in place** — same ingredients, same
recipe card in the kitchen UI, but its stat output increases and its
displayed name gains a rank title as the player's cook level rises within
the tier they're already in.

This is a distinct mechanic from **Three-Lock Progression**
(master-design.md §8.4: band level + cook level + Grimoire, gating
advancement to a *whole new tier*) — that system already exists in design
and is missing only its cook-level check in `isRecipeVisible()`. Recipe
ranks are about depth *within* an already-unlocked tier, not the tier gate
itself.

## 2. Data Model Change

`Recipe` (`data/model/Recipe.kt`) currently stores a single flat
`cookLevel: Int`, `primaryBoost: Int`, and `secondaryBoost: Int` per recipe.
Replace the flat boost fields with an embedded list of ranks:

```kotlin
@Serializable
data class RecipeRank(
    val cookLevelOffset: Int,       // 0 = base rank, unlocked as soon as the
                                     // recipe's tier itself is unlocked.
                                     // Positive values are cook levels above
                                     // the tier's own entry threshold.
    val nameSuffix: String = "",    // rank title, e.g. "" / "Initiate" / ...
                                     // — TBD, Wes's call on flavor
    val primaryBoost: Int,
    val secondaryBoost: Int = 0
)

@Serializable
data class Recipe(
    val id: String,
    val name: String,
    // ...unchanged fields (band, recipeClass, method, tier, ingredients, etc.)...
    val ranks: List<RecipeRank>      // always at least one entry (the base rank)
)
```

A recipe is not required to define more than one rank — simple/minor dishes
can stay single-rank. Resolving "what does this recipe currently do for
this player" becomes: take the player's current cook level, subtract the
tier's entry threshold to get their tier-relative level, then pick the
highest rank in `ranks` whose `cookLevelOffset` is `<=` that value.

**Consumers that need updating** (found via `cookLevel`/`primaryBoost`
references): `KitchenViewModel` (grade-ceiling scaling, recipe sort order,
recipe card display), the cooking-completion logic that reads
`primaryBoost`/`secondaryBoost` when a dish is prepared, and combat's
food-buff resolution in `EncounterEngine.kt`. Each of these needs to resolve
the player's current rank for a recipe before reading its boost values,
rather than reading `recipe.primaryBoost` directly.

## 3. Rank Breakpoints (Tier-Relative)

Ranks are **relative to the tier's own entry cook-level threshold**, not
absolute cook level — every tier feels like its own progression arc,
regardless of how high the player's overall cook level has climbed by the
time they reach it. Placeholder structure, 4 ranks per family (a base rank
plus 3 upgrades):

| Rank | Cook level offset from tier entry | Placeholder stat multiplier |
|---|---|---|
| Base | +0 | 1.0x |
| Rank 2 | +3 | 1.15x |
| Rank 3 | +5 | 1.3x |
| Rank 4 (capstone) | +7 | 1.5x |

The multiplier applies to the recipe's base `primaryBoost`/`secondaryBoost`
values. A recipe with fewer than 4 authored ranks simply stops scaling past
its last defined rank.

## 4. Interaction with Grade (Crude→Pristine)

Rank and Grade are independent, multiplicative axes on the same dish:

```
totalBoost = baseBoost × rankMultiplier × gradeMultiplier
```

(Grade multiplier values are the placeholders from the companion
stat-growth-and-food-power design: Crude 0.7x … Pristine 1.7x.) Neither axis
alone maxes out a dish — a high-rank recipe cooked with Crude ingredients is
still held back, and a base-rank recipe cooked with Pristine ingredients
doesn't hit the ceiling either. Both matter: rank rewards staying in a tier
and leveling up cooking skill; grade rewards the ingredient-quality chase
described in the companion design.

## 5. Scope

Applies to **all recipe classes** — food, draught, and HoH — not just food.
The same rank/tier-relative-offset structure and the same interaction with
Grade apply uniformly across all three craft tracks, since they already
share the same `Recipe` data class and the same Three-Lock tier-gating
concept.

## 6. Migration of Existing Data

T2's current 11 food recipes spread across flat `cookLevel` values
3/4/5/6 are exactly the kind of content this system replaces — they'll need
to be consolidated into fewer recipe *families*, each with an embedded
`ranks` list, as part of implementation. This is a content-authoring task,
not just a schema change, and should be scoped as its own implementation
task rather than assumed to be a mechanical find-and-replace.

## 7. What This Doc Does Not Decide

- Rank titles / naming convention (e.g. "Initiate/Master/King") — explicitly
  Wes's call, not proposed here.
- Exact rank count per recipe family beyond the 4-rank placeholder
  structure — some families may reasonably have fewer.
- The tier's own cook-level entry threshold (the number `cookLevelOffset`
  is relative to) — that's part of Three-Lock Progression's cook-level lock,
  which still needs its own concrete values decided when that gate is
  implemented.
- Exact rank multiplier values — placeholders pending sim calibration, same
  as every other numeric value in the companion design.
- Whether the existing T2/T3/T4 recipe content gets reorganized into
  families as a mechanical migration or a fresh content pass.
