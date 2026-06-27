# Recipe Discovery System — Design Spec

## Summary

Replace the always-visible recipe list with a GW2-style discovery system. The
player learns recipes by experimenting: combining raw ingredients with a cooking
method, consuming the ingredients whether or not they succeed. Discovered recipes
graduate into the normal recipe list. Unknown recipes remain hidden, keeping
early-game focused and rewarding exploration.

---

## Goals

- Recipes are hidden until discovered through experimentation or levelling
- Experiments consume ingredients on failure (permanent cost, real stakes)
- Proximity feedback guides the player without giving the answer away
- Ingredient categories (grain, liquid, herb, etc.) are hidden labels used
  internally by the discovery engine — not shown directly in the UI
- Ingredient categories inform food type structures so guessing isn't purely
  random (a bread will always want grain + liquid + binder)
- Players can also unlock recipes by reaching the required cook level
  (auto-discover on level-up)
- The system is purely additive: it does not break existing cooking flow

---

## What Is Not Changing

- `CookingWorker`, `KitchenViewModel`, recipe data format, Room schema for
  `CookingSession` — none of these change
- The `tieredRecipes` / `bandRecipes` flow stays; the only change is a
  discovered filter layered on top
- Food type structures (bread = grain + liquid + binder, etc.) guide *which*
  experiments feel reasonable, but are enforced by nothing — any combination
  can be submitted

---

## Data Layer

### Room: PlayerState v7 migration

Add one new field to `PlayerState`:

```kotlin
@ColumnInfo(defaultValue = "")
val discoveredRecipeIds: String = ""
```

Stored as a comma-separated string of recipe IDs (e.g., `"ember_porridge,rangers_fare"`).
No join table needed at this scale.

Auto-migration from v6 → v7 using Room's `@AutoMigration(from = 6, to = 7)`.

### PlayerRepository additions

```kotlin
suspend fun discoverRecipe(recipeId: String)
fun observeDiscoveredIds(): Flow<Set<String>>
```

`discoverRecipe` reads the current set, adds the id, writes back.
`observeDiscoveredIds` maps the comma-separated string to `Set<String>`.

---

## Discovery Engine

A pure Kotlin object — no Android dependencies, fully testable.

```kotlin
object RecipeDiscoveryEngine {
    fun evaluate(
        attempt: ExperimentAttempt,
        allRecipes: List<Recipe>
    ): ExperimentResult
}

data class ExperimentAttempt(
    val ingredientIds: List<String>,   // order ignored, treated as a multiset
    val quantities: Map<String, Int>,
    val method: String
)

sealed class ExperimentResult {
    data class Discovered(val recipe: Recipe) : ExperimentResult()
    data class AlreadyKnown(val recipe: Recipe) : ExperimentResult()
    data class Failure(val proximity: ProximityTier) : ExperimentResult()
}

enum class ProximityTier {
    NONE,       // no ingredient overlap at all
    SOME,       // some ingredients overlap, others missing
    CLOSE,      // all right ingredients, wrong quantities or wrong method
    NEAR_MISS   // right ingredients, right quantities, wrong method — or vice versa
}
```

### Match logic (exact, like GW2)

A recipe is a **hit** when:
- `attempt.method == recipe.method`
- `attempt.ingredientIds.toSet() == recipe.ingredients.map { it.id }.toSet()`
- For every ingredient in the recipe: `attempt.quantities[id] == recipe.qty[id]`

All three must pass simultaneously. The match is unordered (ingredient order
doesn't matter).

### Proximity logic

When there is no hit, proximity is computed by looking at the closest recipe
(fewest mismatches across all recipes):

| Condition | Tier |
|-----------|------|
| All ingredients match, all quantities match, wrong method | `NEAR_MISS` |
| All ingredients match, right method, wrong quantities | `NEAR_MISS` |
| All ingredients in recipe found in attempt (may have extras), right method | `CLOSE` |
| Some ingredients overlap (≥50% match by count) | `SOME` |
| No overlap | `NONE` |

The engine picks the **best** (highest) proximity tier across all recipes and
returns it. The player never learns *which* recipe they were close to.

### Quantity flavor text

When proximity is `NEAR_MISS` due to quantity mismatch, the result message
can include a flavor hint:

> "The balance is nearly right, but something is missing — perhaps a touch more of one ingredient?"

This is generated at the result-message level (ViewModel / UI), not in the
engine itself. The engine returns only the tier.

---

## Auto-Discover on Level-Up

When the player reaches a new cook level, all recipes with `cookLevel <=
newLevel` that are not yet discovered should be auto-discovered. This runs in
`PlayerRepository.incrementCookingLevel()` (or wherever level-up is triggered),
after the level write, before the transaction closes.

This means: if you grind levels without experimenting, you eventually unlock
everything. Experimentation is a shortcut, not a gate.

---

## KitchenViewModel Changes

Add:
```kotlin
val discoveredIds: StateFlow<Set<String>>
val tieredRecipes: StateFlow<List<RecipeTier>>   // already exists — filter added
```

The `tieredRecipes` flow now filters to discovered-only:
```kotlin
combine(allRecipes, discoveredIds) { recipes, known ->
    recipes.filter { it.id in known }
}.stateIn(...)
```

Add experiment state:
```kotlin
val experimentMode: StateFlow<Boolean>
val experimentIngredients: StateFlow<List<ExperimentSlot>>  // up to 4 slots
val experimentMethod: StateFlow<String>
val lastExperimentResult: StateFlow<ExperimentResult?>

fun toggleExperimentMode()
fun addIngredient(id: String, qty: Int)
fun removeIngredient(id: String)
fun setMethod(method: String)
fun submitExperiment()   // consumes ingredients, calls engine, saves discovery
```

`submitExperiment()` is a suspend function. It:
1. Calls `RecipeDiscoveryEngine.evaluate(attempt, allRecipes)`
2. If `Discovered`: calls `playerRepository.discoverRecipe(id)`, deducts ingredients from inventory
3. If `AlreadyKnown`: deducts ingredients, returns appropriate message
4. If `Failure`: deducts ingredients, returns proximity tier

Ingredient deduction always happens — there is no free experiment.

---

## KitchenScreen UI

A mode toggle appears below the "Kitchen" header:

```
[ Recipes ]  [ Experiment ]
```

### Recipes mode (default)
- Existing recipe list, unchanged
- Locked tiers show "Reach Lv N" as before
- Undiscovered recipes are simply absent (not shown as "???" or locked)

### Experiment mode
- Ingredient picker: up to 4 slots, each slot selects an ingredient (from
  the player's inventory) and a quantity (1–5, spinner or +/- buttons)
- Method chips: Cook · Bake · Roast · Simmer · Infuse · Brew
- Submit button ("Experiment") — disabled if no ingredients selected
- Result card appears after submission, showing:
  - `Discovered`: recipe name + flavor text ("You've found something new!")
  - `AlreadyKnown`: recipe name + "You already know this one."
  - `Failure NEAR_MISS`: "Almost — the balance is nearly right."
  - `Failure CLOSE`: "Something is missing from this combination."
  - `Failure SOME`: "A few of these ingredients feel right, but not together."
  - `Failure NONE`: "Nothing. These ingredients don't belong together."

The result card persists until the player clears it or submits a new experiment.

### Ingredient picker design

Show only ingredients currently in inventory (qty ≥ 1). Each slot shows:
- Ingredient name
- Available quantity
- Chosen quantity selector

No ingredient categories are shown. The player must experiment or use food type
hints (see below).

---

## Food Type Hint System

When the player has unlocked cook level 3+, a "Food Structures" hint card
appears at the top of Experiment mode:

> **Bread** needs a grain, a liquid, and a binder.
> **Soup** needs a base liquid, a protein, and a vegetable.
> **Roast** needs a meat, a fat, and seasoning.
> **Infusion** needs an herb and a base liquid.
> **Stew** needs a meat or protein, root vegetables, and a broth.

These are flavour hints only — the engine enforces no typing. They tell the
player *what kinds of ingredients to combine*, not which specific ingredients.
Ingredient categories (grain, liquid, protein, etc.) are never surfaced directly.

The hint card is collapsible and defaults to collapsed after the player has
seen it once (tracked in `PlayerState.hasSeenFoodStructureHints: Boolean`).

---

## DB Schema Changes Summary

**v6 → v7 (AutoMigration):**
- `PlayerState`: add `discoveredRecipeIds TEXT NOT NULL DEFAULT ''`
- `PlayerState`: add `hasSeenFoodStructureHints INTEGER NOT NULL DEFAULT 0`

---

## Implementation Order

1. Room v7 migration + `PlayerRepository` changes
2. `RecipeDiscoveryEngine` (pure object, test first)
3. `KitchenViewModel` experiment state + `submitExperiment()`
4. Filter `tieredRecipes` to discovered-only
5. Auto-discover on level-up
6. `KitchenScreen` mode toggle + Experiment UI
7. Food type hints card

Steps 1–5 can be built and tested before touching the UI.

---

## What This Does Not Include (deferred)

- Recipe book showing discovered recipes (it already exists, will auto-update
  once `tieredRecipes` is filtered)
- Social hints ("Ivorybark and watercress go together") — future
- Ingredient category column in spreadsheet (needed but not a blocker for
  the proximity engine, which works on IDs not categories)
- "Almost perfect" quantity flavor messages generated dynamically — the tier
  is enough for V1, message can be static
