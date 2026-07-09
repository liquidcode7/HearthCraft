# Kitchen/Pantry UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Kitchen Recipes tab a sticky detail panel, collapsible/filterable/sortable tiers, and give Pantry filterable/sortable ingredient stock — per `docs/superpowers/specs/2026-07-09-kitchen-pantry-ux-design.md` (Group 5 of the audit roadmap).

**Architecture:** `KitchenViewModel` gains recipe filter/sort/tier-expand state plus a derived `displayedTieredRecipes` flow. A brand-new `PantryViewModel` (NOT folded into the shared `InventoryViewModel`, which also serves House of Healing and Missions) gains ingredient filter/sort state plus a derived `displayedIngredients` flow. Two small reusable composables (`FilterChipRow`, `SortSelector`) back both screens' chip UI. `KitchenScreen`'s Recipes page is restructured into a fixed top section (nav buttons, kilns, recipe detail panel) and an independently-scrolling section below it, replacing the old scroll-jump-to-top mechanism entirely.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Kotlin coroutines/Flow. No automated Compose UI test framework exists in this repo — filter/sort/tier-expand logic is unit-tested as pure functions; UI composition is verified by compiling and by a manual pass (Task 6).

## Global Constraints

- Prepared Food on Pantry is explicitly out of scope — do not filter, sort, or otherwise touch that list or its data flow.
- `InventoryViewModel` (`app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/InventoryViewModel.kt`) must not be modified — it is shared by `HouseOfHealingScreen.kt` and `MissionsScreen.kt`; Pantry gets its own `PantryViewModel` instead.
- Tier is always the permanent outer grouping for Kitchen recipes. No sort mode may flatten recipes across tiers.
- Filter chip groups combine with AND across groups, OR within a group's own multi-select (e.g. selecting both "Might" and "Will" stat chips shows recipes matching either stat).
- Run gradle from the repo root (`/home/wes/projects/HearthCraft`), not a `.claude/worktrees/...` job directory — `cd` there explicitly in every shell command since the working directory resets between tool calls. `local.properties` at the repo root already has `sdk.dir=/home/wes/Android/Sdk`; if a fresh checkout is missing it, recreate that one line.
- Test command form: `./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.ui.viewmodel.<ClassName>"`. Compile-check command form for UI-only tasks: `./gradlew :app:compileDebugKotlin`.
- Commit prefix convention: `[hc] <summary>`.

---

### Task 1: Kitchen recipe filter/sort/tier-expand state

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/RecipeFilterSortTest.kt`

**Interfaces:**
- Consumes: existing `RecipeTier(label: String, minLevel: Int, recipes: List<Recipe>)`, existing `KitchenViewModel.tieredRecipes: StateFlow<List<RecipeTier>>`, `KitchenViewModel.inventoryItems: StateFlow<List<InventoryItem>>`, `KitchenViewModel.canCook(recipe: Recipe, items: List<InventoryItem>): Boolean` — all already exist in this file, unchanged.
- Produces (used by Task 4): `RecipeFilterState(cookableOnly: Boolean, classFilter: Set<String>, statFilter: Set<String>)`, `RecipeSortMode { TIER, ALPHABETICAL, LEVEL }`, `KitchenViewModel.recipeFilters: StateFlow<RecipeFilterState>`, `KitchenViewModel.setRecipeFilters(filters: RecipeFilterState)`, `KitchenViewModel.recipeSort: StateFlow<RecipeSortMode>`, `KitchenViewModel.setRecipeSort(mode: RecipeSortMode)`, `KitchenViewModel.displayedTieredRecipes: StateFlow<List<RecipeTier>>`, `KitchenViewModel.isTierExpanded(tierLabel: String, isUnlocked: Boolean): Boolean`, `KitchenViewModel.toggleTierExpanded(tierLabel: String, isUnlocked: Boolean)`, `KitchenViewModel.expandTierOnly(tierLabel: String, allLabels: List<String>)`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/RecipeFilterSortTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.data.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFilterSortTest {

    private fun recipe(
        id: String,
        name: String = id,
        recipeClass: String = "food",
        primaryStat: String? = "mig",
        secondaryStat: String? = null,
        cookLevel: Int = 1
    ) = Recipe(
        id = id, name = name, recipeClass = recipeClass,
        primaryStat = primaryStat, secondaryStat = secondaryStat, cookLevel = cookLevel
    )

    // ── matchesRecipeFilters ─────────────────────────────────────────────

    @Test
    fun `no active filters matches every recipe`() {
        val r = recipe("a")
        assertTrue(matchesRecipeFilters(r, RecipeFilterState(), isCookable = false))
    }

    @Test
    fun `cookableOnly filter excludes recipes the player can't cook`() {
        val r = recipe("a")
        val filters = RecipeFilterState(cookableOnly = true)
        assertFalse(matchesRecipeFilters(r, filters, isCookable = false))
        assertTrue(matchesRecipeFilters(r, filters, isCookable = true))
    }

    @Test
    fun `class filter matches only recipes in the selected classes`() {
        val food = recipe("a", recipeClass = "food")
        val draught = recipe("b", recipeClass = "draught")
        val filters = RecipeFilterState(classFilter = setOf("draught"))
        assertFalse(matchesRecipeFilters(food, filters, isCookable = true))
        assertTrue(matchesRecipeFilters(draught, filters, isCookable = true))
    }

    @Test
    fun `stat filter matches on either primary or secondary stat`() {
        val r = recipe("a", primaryStat = "mig", secondaryStat = "vit")
        assertTrue(matchesRecipeFilters(r, RecipeFilterState(statFilter = setOf("vit")), isCookable = true))
        assertFalse(matchesRecipeFilters(r, RecipeFilterState(statFilter = setOf("wil")), isCookable = true))
    }

    @Test
    fun `multiple active filter groups combine with AND`() {
        val r = recipe("a", recipeClass = "food", primaryStat = "mig")
        val filters = RecipeFilterState(classFilter = setOf("draught"), statFilter = setOf("mig"))
        assertFalse(matchesRecipeFilters(r, filters, isCookable = true))
    }

    // ── sortRecipesForDisplay ────────────────────────────────────────────

    @Test
    fun `TIER mode returns the input order unchanged`() {
        val input = listOf(recipe("z", name = "Zed"), recipe("a", name = "Ace"))
        assertEquals(input, sortRecipesForDisplay(input, RecipeSortMode.TIER))
    }

    @Test
    fun `ALPHABETICAL mode sorts by name`() {
        val input = listOf(recipe("z", name = "Zed"), recipe("a", name = "Ace"))
        val sorted = sortRecipesForDisplay(input, RecipeSortMode.ALPHABETICAL)
        assertEquals(listOf("Ace", "Zed"), sorted.map { it.name })
    }

    @Test
    fun `LEVEL mode sorts by cookLevel ascending`() {
        val input = listOf(recipe("hi", cookLevel = 9), recipe("lo", cookLevel = 1))
        val sorted = sortRecipesForDisplay(input, RecipeSortMode.LEVEL)
        assertEquals(listOf("lo", "hi"), sorted.map { it.id })
    }

    // ── tier expansion helpers ───────────────────────────────────────────

    @Test
    fun `expandedStateFor defaults to isUnlocked when no override exists`() {
        assertTrue(expandedStateFor(emptyMap(), "T1", isUnlocked = true))
        assertFalse(expandedStateFor(emptyMap(), "T1", isUnlocked = false))
    }

    @Test
    fun `expandedStateFor honors an explicit override`() {
        val overrides = mapOf("T1" to false)
        assertFalse(expandedStateFor(overrides, "T1", isUnlocked = true))
    }

    @Test
    fun `withToggledTier flips the current effective state`() {
        val result = withToggledTier(emptyMap(), "T1", isUnlocked = true)
        assertEquals(mapOf("T1" to false), result)
    }

    @Test
    fun `withOnlyTierExpanded expands the target tier and collapses the rest`() {
        val result = withOnlyTierExpanded("T2", listOf("T1", "T2", "T3"))
        assertEquals(mapOf("T1" to false, "T2" to true, "T3" to false), result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.ui.viewmodel.RecipeFilterSortTest"`
Expected: FAIL (compile error) — `matchesRecipeFilters`, `RecipeFilterState`, `sortRecipesForDisplay`, `RecipeSortMode`, `expandedStateFor`, `withToggledTier`, `withOnlyTierExpanded` are unresolved references.

- [ ] **Step 3: Implement**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt`, add these top-level declarations immediately after the existing `data class RecipeTier(...)` (currently line 38), before `@HiltViewModel`:

```kotlin
enum class RecipeSortMode { TIER, ALPHABETICAL, LEVEL }

data class RecipeFilterState(
    val cookableOnly: Boolean = false,
    val classFilter: Set<String> = emptySet(),   // "food" | "draught"
    val statFilter: Set<String> = emptySet()     // "mig" | "agi" | "vit" | "wil"
)

/** True if [recipe] passes every active filter in [filters]. [isCookable] is the caller's
 *  already-computed KitchenViewModel.canCook result for this recipe. */
fun matchesRecipeFilters(recipe: Recipe, filters: RecipeFilterState, isCookable: Boolean): Boolean {
    if (filters.cookableOnly && !isCookable) return false
    if (filters.classFilter.isNotEmpty() && recipe.recipeClass !in filters.classFilter) return false
    if (filters.statFilter.isNotEmpty()) {
        val recipeStats = setOfNotNull(recipe.primaryStat, recipe.secondaryStat)
        if (recipeStats.none { it in filters.statFilter }) return false
    }
    return true
}

/** Reorders [recipes] according to [mode]. TIER performs no reorder -- callers pass recipes
 *  already in tier-default order (cookable-first, then cookLevel ascending). */
fun sortRecipesForDisplay(recipes: List<Recipe>, mode: RecipeSortMode): List<Recipe> = when (mode) {
    RecipeSortMode.TIER -> recipes
    RecipeSortMode.ALPHABETICAL -> recipes.sortedBy { it.name }
    RecipeSortMode.LEVEL -> recipes.sortedBy { it.cookLevel }
}

/** Effective expanded state for a tier: an explicit user override if one exists, else
 *  [isUnlocked] (unlocked tiers default open, locked tiers default collapsed). */
fun expandedStateFor(overrides: Map<String, Boolean>, tierLabel: String, isUnlocked: Boolean): Boolean =
    overrides[tierLabel] ?: isUnlocked

/** Returns [overrides] with [tierLabel]'s effective state flipped. */
fun withToggledTier(overrides: Map<String, Boolean>, tierLabel: String, isUnlocked: Boolean): Map<String, Boolean> {
    val current = expandedStateFor(overrides, tierLabel, isUnlocked)
    return overrides + (tierLabel to !current)
}

/** Returns an override map that expands only [tierLabel] and collapses every other label in
 *  [allLabels] -- used by the tier quick-jump chips. */
fun withOnlyTierExpanded(tierLabel: String, allLabels: List<String>): Map<String, Boolean> =
    allLabels.associateWith { it == tierLabel }
```

Then, inside `class KitchenViewModel`, add the block below immediately before the `// ── Tab selection ─────...` comment (currently just above `private val _selectedTab = MutableStateFlow(0)`). This must go *after* the existing `_selectedIngredientGrades` and `setIngredientGrade(...)` declarations, not right after `tieredRecipes` — `displayedTieredRecipes`'s initializer references `_selectedIngredientGrades` as a `combine()` argument, and Kotlin initializes properties top-to-bottom in declaration order, so referencing it before its own `val` line would use it while still null:

```kotlin
    private val _recipeFilters = MutableStateFlow(RecipeFilterState())
    val recipeFilters: StateFlow<RecipeFilterState> = _recipeFilters.asStateFlow()

    fun setRecipeFilters(filters: RecipeFilterState) {
        _recipeFilters.value = filters
    }

    private val _recipeSort = MutableStateFlow(RecipeSortMode.TIER)
    val recipeSort: StateFlow<RecipeSortMode> = _recipeSort.asStateFlow()

    fun setRecipeSort(mode: RecipeSortMode) {
        _recipeSort.value = mode
    }

    // Explicit user overrides only; a tier with no entry here defaults per expandedStateFor().
    private val _expandedTierOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val expandedTierOverrides: StateFlow<Map<String, Boolean>> = _expandedTierOverrides.asStateFlow()

    fun isTierExpanded(tierLabel: String, isUnlocked: Boolean): Boolean =
        expandedStateFor(_expandedTierOverrides.value, tierLabel, isUnlocked)

    fun toggleTierExpanded(tierLabel: String, isUnlocked: Boolean) {
        _expandedTierOverrides.value = withToggledTier(_expandedTierOverrides.value, tierLabel, isUnlocked)
    }

    fun expandTierOnly(tierLabel: String, allLabels: List<String>) {
        _expandedTierOverrides.value = withOnlyTierExpanded(tierLabel, allLabels)
    }

    // _selectedIngredientGrades is included as a combine input (unused in the lambda body)
    // solely to force recomputation when it changes -- canCook() reads it internally, so the
    // "cookable now" filter would otherwise go stale after selecting a different recipe.
    val displayedTieredRecipes: StateFlow<List<RecipeTier>> = combine(
        tieredRecipes, inventoryItems, _recipeFilters, _recipeSort, _selectedIngredientGrades
    ) { tiers, items, filters, sort, _ ->
        tiers.map { tier ->
            val filtered = tier.recipes.filter { recipe ->
                matchesRecipeFilters(recipe, filters, canCook(recipe, items))
            }
            tier.copy(recipes = sortRecipesForDisplay(filtered, sort))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.ui.viewmodel.RecipeFilterSortTest"`
Expected: PASS, 12 tests green.

- [ ] **Step 5: Compile check**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (confirms `KitchenViewModel.kt` still compiles cleanly; nothing else references it yet).

- [ ] **Step 6: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/RecipeFilterSortTest.kt
git commit -m "[hc] Add Kitchen recipe filter/sort/tier-expand state"
```

---

### Task 2: Pantry ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryViewModel.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryFilterSortTest.kt`

**Interfaces:**
- Consumes: `InventoryRepository.observeIngredients(): Flow<List<InventoryItem>>`, `InventoryRepository.observePreparedFood(): Flow<List<PreparedFood>>` (exact upstream type not needed here — same call already used in `InventoryViewModel.kt`), `PlayerRepository.observe(): Flow<PlayerState?>`, `GameDataRepository.ingredients: List<Ingredient>` (has `.primaryStat: String?`), `GameDataRepository.recipes: List<Recipe>` — all already exist, unchanged. Existing `PreparedFoodDetail` and `IngredientStock` data classes from `UiModels.kt`.
- Produces (used by Task 5): `IngredientStock` gains `primaryStat: String? = null`. New `PantrySortMode { QUANTITY, ALPHABETICAL }`, `PantryFilterState(gradeFilter: Set<Int>, statFilter: Set<String>)`, `PantryViewModel.money: StateFlow<Int>`, `PantryViewModel.preparedFood: StateFlow<List<PreparedFoodDetail>>`, `PantryViewModel.filters: StateFlow<PantryFilterState>`, `PantryViewModel.setFilters(filters: PantryFilterState)`, `PantryViewModel.sortMode: StateFlow<PantrySortMode>`, `PantryViewModel.setSortMode(mode: PantrySortMode)`, `PantryViewModel.displayedIngredients: StateFlow<List<IngredientStock>>`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryFilterSortTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryFilterSortTest {

    private fun stock(id: String, name: String = id, quantity: Int = 1, grade: Int = 0, primaryStat: String? = null) =
        IngredientStock(ingredientId = id, name = name, quantity = quantity, grade = grade, primaryStat = primaryStat)

    @Test
    fun `no active filters matches every stock`() {
        assertTrue(matchesPantryFilters(stock("a"), PantryFilterState()))
    }

    @Test
    fun `grade filter matches only stocks at the selected grades`() {
        val fine = stock("a", grade = 2)
        val filters = PantryFilterState(gradeFilter = setOf(0, 1))
        assertFalse(matchesPantryFilters(fine, filters))
        assertTrue(matchesPantryFilters(fine.copy(grade = 1), filters))
    }

    @Test
    fun `stat filter matches only stocks whose ingredient has that primary stat`() {
        val vitStock = stock("a", primaryStat = "vit")
        assertTrue(matchesPantryFilters(vitStock, PantryFilterState(statFilter = setOf("vit"))))
        assertFalse(matchesPantryFilters(vitStock, PantryFilterState(statFilter = setOf("wil"))))
    }

    @Test
    fun `stat filter excludes stocks with no primary stat`() {
        val noStat = stock("a", primaryStat = null)
        assertFalse(matchesPantryFilters(noStat, PantryFilterState(statFilter = setOf("mig"))))
    }

    @Test
    fun `grade and stat filters combine with AND`() {
        val s = stock("a", grade = 3, primaryStat = "mig")
        val filters = PantryFilterState(gradeFilter = setOf(0), statFilter = setOf("mig"))
        assertFalse(matchesPantryFilters(s, filters))
    }

    @Test
    fun `QUANTITY mode sorts by quantity descending`() {
        val input = listOf(stock("a", quantity = 2), stock("b", quantity = 9))
        val sorted = sortIngredientStocks(input, PantrySortMode.QUANTITY)
        assertEquals(listOf("b", "a"), sorted.map { it.ingredientId })
    }

    @Test
    fun `ALPHABETICAL mode sorts by name`() {
        val input = listOf(stock("a", name = "Zed"), stock("b", name = "Ace"))
        val sorted = sortIngredientStocks(input, PantrySortMode.ALPHABETICAL)
        assertEquals(listOf("Ace", "Zed"), sorted.map { it.name })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.ui.viewmodel.PantryFilterSortTest"`
Expected: FAIL (compile error) — `matchesPantryFilters`, `PantryFilterState`, `sortIngredientStocks`, `PantrySortMode` are unresolved, and `IngredientStock(...)` doesn't yet accept `primaryStat`.

- [ ] **Step 3: Extend `IngredientStock`**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`, replace:

```kotlin
data class IngredientStock(
    val ingredientId: String,
    val name: String,
    val quantity: Int,
    val grade: Int = 0   // ordinal of Grade enum; one IngredientStock per (id, grade) pair
)
```

with:

```kotlin
data class IngredientStock(
    val ingredientId: String,
    val name: String,
    val quantity: Int,
    val grade: Int = 0,   // ordinal of Grade enum; one IngredientStock per (id, grade) pair
    val primaryStat: String? = null   // "mig"/"agi"/"vit"/"wil", from Ingredient.primaryStat -- used by Pantry's stat filter
)
```

- [ ] **Step 4: Create `PantryViewModel.kt`**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryViewModel.kt`:

```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class PantrySortMode { QUANTITY, ALPHABETICAL }

data class PantryFilterState(
    val gradeFilter: Set<Int> = emptySet(),    // Grade ordinals (0=Crude..4=Pristine)
    val statFilter: Set<String> = emptySet()   // "mig" | "agi" | "vit" | "wil"
)

/** True if [stock] passes every active filter in [filters]. */
fun matchesPantryFilters(stock: IngredientStock, filters: PantryFilterState): Boolean {
    if (filters.gradeFilter.isNotEmpty() && stock.grade !in filters.gradeFilter) return false
    if (filters.statFilter.isNotEmpty() && stock.primaryStat !in filters.statFilter) return false
    return true
}

/** Reorders [stocks] according to [mode]. */
fun sortIngredientStocks(stocks: List<IngredientStock>, mode: PantrySortMode): List<IngredientStock> = when (mode) {
    PantrySortMode.QUANTITY -> stocks.sortedByDescending { it.quantity }
    PantrySortMode.ALPHABETICAL -> stocks.sortedBy { it.name }
}

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) : ViewModel() {

    val money: StateFlow<Int> = player.observe()
        .map { it?.money ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val preparedFood: StateFlow<List<PreparedFoodDetail>> = inventory.observePreparedFood()
        .map { foods ->
            foods.mapNotNull { pf ->
                val recipe = gameData.recipes.find { it.id == pf.recipeId && it.recipeClass != "hoh" } ?: return@mapNotNull null
                PreparedFoodDetail(
                    recipeId = pf.recipeId,
                    name = recipe.name,
                    buffType = recipe.buffType,
                    buffStrength = recipe.primaryBoost,
                    quantity = pf.quantity,
                    grade = pf.grade,
                    primaryStat = recipe.primaryStat,
                    primaryBoost = recipe.primaryBoost,
                    secondaryStat = recipe.secondaryStat,
                    secondaryBoost = recipe.secondaryBoost
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allIngredients: StateFlow<List<IngredientStock>> = inventory.observeIngredients()
        .map { items ->
            items.filter { it.quantity > 0 }.map { item ->
                val def = gameData.ingredients.find { it.id == item.ingredientId }
                IngredientStock(
                    ingredientId = item.ingredientId,
                    name = def?.name ?: item.ingredientId,
                    quantity = item.quantity,
                    grade = item.grade,
                    primaryStat = def?.primaryStat
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filters = MutableStateFlow(PantryFilterState())
    val filters: StateFlow<PantryFilterState> = _filters.asStateFlow()

    fun setFilters(filters: PantryFilterState) {
        _filters.value = filters
    }

    private val _sortMode = MutableStateFlow(PantrySortMode.ALPHABETICAL)
    val sortMode: StateFlow<PantrySortMode> = _sortMode.asStateFlow()

    fun setSortMode(mode: PantrySortMode) {
        _sortMode.value = mode
    }

    val displayedIngredients: StateFlow<List<IngredientStock>> = combine(
        allIngredients, _filters, _sortMode
    ) { stocks, filters, sort ->
        sortIngredientStocks(stocks.filter { matchesPantryFilters(it, filters) }, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

Note: default sort is `ALPHABETICAL`, matching `InventoryViewModel.namedIngredients`'s existing `sortedWith(compareBy({ it.name }, { it.grade }))` default — i.e. the on-screen order does not change until the player picks a different sort.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.ui.viewmodel.PantryFilterSortTest"`
Expected: PASS, 7 tests green.

- [ ] **Step 6: Compile check**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryViewModel.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryFilterSortTest.kt
git commit -m "[hc] Add PantryViewModel with ingredient filter/sort state"
```

---

### Task 3: Shared filter/sort composables

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/FilterSortControls.kt`

**Interfaces:**
- Consumes: nothing project-specific (pure Compose/Material3).
- Produces (used by Tasks 4 and 5): `@Composable fun <T> FilterChipRow(options: List<Pair<T, String>>, selected: Set<T>, onToggle: (T) -> Unit, modifier: Modifier = Modifier)`, `@Composable fun <T> SortSelector(options: List<Pair<T, String>>, selectedOption: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier)`, `fun <T> toggledSet(set: Set<T>, value: T): Set<T>`.

- [ ] **Step 1: Create the file**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/FilterSortControls.kt`:

```kotlin
package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Returns [set] with [value] toggled: removed if present, added if absent. */
fun <T> toggledSet(set: Set<T>, value: T): Set<T> = if (value in set) set - value else set + value

/**
 * A horizontally-scrolling row of independently-toggleable chips. Multiple options may be
 * selected at once; [onToggle] is called with the tapped option's own value.
 */
@Composable
fun <T> FilterChipRow(
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value in selected,
                onClick = { onToggle(value) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

/** A horizontally-scrolling row of single-select chips; exactly one option is selected. */
@Composable
fun <T> SortSelector(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selectedOption,
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (No existing file references these composables yet, so this only confirms the new file itself compiles.)

- [ ] **Step 3: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/FilterSortControls.kt
git commit -m "[hc] Add shared FilterChipRow/SortSelector composables"
```

---

### Task 4: Kitchen Recipes tab — sticky panel, collapsible tiers, filters, sort

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`

**Interfaces:**
- Consumes: everything produced by Task 1 (`RecipeFilterState`, `RecipeSortMode`, `KitchenViewModel.recipeFilters/setRecipeFilters/recipeSort/setRecipeSort/displayedTieredRecipes/isTierExpanded/toggleTierExpanded/expandTierOnly`) and Task 3 (`FilterChipRow`, `SortSelector`, `toggledSet`). `RecipeDetailPanel`, `RecipeRow`, `CookingSlotCard`, `KitchenXpBar`, `ProcessPanel` are unchanged private composables already in this file.
- Produces: nothing new consumed elsewhere — `KitchenScreen` is a top-level navigation destination.

- [ ] **Step 1: Replace the file**

Replace the full contents of `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt` with:

```kotlin
package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.gradeMultiplier
import com.liquidcode7.hearthcraft.ui.viewmodel.KitchenViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeFilterState
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeSortMode
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeTier
import com.liquidcode7.hearthcraft.ui.util.formatMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KitchenScreen(
    onViewRecipes: () -> Unit,
    onViewPantry: () -> Unit = {},
    viewModel: KitchenViewModel = hiltViewModel()
) {
    val session0 by viewModel.session0.collectAsState()
    val session1 by viewModel.session1.collectAsState()
    val bothBusy = session0 != null && session1 != null
    val selectedRecipe by viewModel.selectedRecipe.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val tieredRecipes by viewModel.tieredRecipes.collectAsState()
    val displayedTieredRecipes by viewModel.displayedTieredRecipes.collectAsState()
    val recipeFilters by viewModel.recipeFilters.collectAsState()
    val recipeSort by viewModel.recipeSort.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val processSlot by viewModel.processSlot.collectAsState()
    val processIngredients = viewModel.processIngredients
    val selectedProcessIngredient by viewModel.selectedProcessIngredient.collectAsState()
    val cookingXp by viewModel.cookingXpProgress.collectAsState()
    val cookingLevel = playerState?.cookingLevel ?: 1

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Fixed top section (tab bar) ─────────────────────────────────────
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Kitchen", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            KitchenXpBar(level = cookingXp.level, earned = cookingXp.earned, needed = cookingXp.needed)
            Spacer(modifier = Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { viewModel.selectTab(0) }, text = { Text("Recipes") })
                Tab(selected = selectedTab == 1, onClick = { viewModel.selectTab(1) }, text = { Text("Prepare") })
            }
        }

        HorizontalDivider()

        val pagerState = rememberPagerState(pageCount = { 2 })
        LaunchedEffect(selectedTab) { if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { settled -> if (settled != selectedTab) viewModel.selectTab(settled) }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> RecipesTabContent(
                    onViewRecipes = onViewRecipes,
                    onViewPantry = onViewPantry,
                    session0 = session0,
                    session1 = session1,
                    bothBusy = bothBusy,
                    selectedRecipe = selectedRecipe,
                    inventoryItems = inventoryItems,
                    cookingLevel = cookingLevel,
                    tieredRecipes = tieredRecipes,
                    displayedTieredRecipes = displayedTieredRecipes,
                    recipeFilters = recipeFilters,
                    recipeSort = recipeSort,
                    viewModel = viewModel
                )
                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProcessPanel(
                        viewModel = viewModel,
                        processSlot = processSlot,
                        processIngredients = processIngredients,
                        selectedProcessIngredient = selectedProcessIngredient,
                        inventoryItems = inventoryItems
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RecipesTabContent(
    onViewRecipes: () -> Unit,
    onViewPantry: () -> Unit,
    session0: CookingSession?,
    session1: CookingSession?,
    bothBusy: Boolean,
    selectedRecipe: Recipe?,
    inventoryItems: List<InventoryItem>,
    cookingLevel: Int,
    tieredRecipes: List<RecipeTier>,
    displayedTieredRecipes: List<RecipeTier>,
    recipeFilters: RecipeFilterState,
    recipeSort: RecipeSortMode,
    viewModel: KitchenViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // ── Pinned: nav buttons, kilns, recipe detail panel ─────────────────
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewRecipes, modifier = Modifier.weight(1f)) { Text("Recipe Book") }
                OutlinedButton(onClick = onViewPantry, modifier = Modifier.weight(1f)) { Text("Pantry") }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CookingSlotCard(slot = 0, session = session0, viewModel = viewModel, modifier = Modifier.weight(1f))
                CookingSlotCard(slot = 1, session = session1, viewModel = viewModel, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedRecipe != null) {
                val predictedGrade by viewModel.predictedDishGrade.collectAsState()
                RecipeDetailPanel(
                    recipe = selectedRecipe,
                    inventoryItems = inventoryItems,
                    cookingLevel = cookingLevel,
                    predictedGrade = predictedGrade,
                    viewModel = viewModel
                )
                if (!bothBusy) {
                    val freeSlot = if (session0 == null) 0 else 1
                    Button(
                        onClick = { viewModel.startCooking(freeSlot) },
                        enabled = viewModel.canCook(selectedRecipe, inventoryItems),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Cooking")
                    }
                }
            } else {
                Text(
                    "Select a recipe below to see details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalDivider()

        // ── Scrolls independently: tier quick-jump, filters, sort, recipe list ──
        val listScrollState = rememberScrollState()
        val tierPositions = remember { mutableStateMapOf<String, Int>() }
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(listScrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (tieredRecipes.isNotEmpty()) {
                Text("Jump to tier", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tieredRecipes.forEach { tier ->
                        AssistChip(
                            onClick = {
                                viewModel.expandTierOnly(tier.label, tieredRecipes.map { it.label })
                                tierPositions[tier.label]?.let { y ->
                                    coroutineScope.launch { listScrollState.animateScrollTo(y) }
                                }
                            },
                            label = { Text(tier.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Filter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                FilterChip(
                    selected = recipeFilters.cookableOnly,
                    onClick = { viewModel.setRecipeFilters(recipeFilters.copy(cookableOnly = !recipeFilters.cookableOnly)) },
                    label = { Text("Cookable now", style = MaterialTheme.typography.labelSmall) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(
                    options = listOf("food" to "Food", "draught" to "Draught"),
                    selected = recipeFilters.classFilter,
                    onToggle = { key -> viewModel.setRecipeFilters(recipeFilters.copy(classFilter = toggledSet(recipeFilters.classFilter, key))) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(
                    options = listOf("mig" to "Might", "agi" to "Agility", "vit" to "Vitality", "wil" to "Will"),
                    selected = recipeFilters.statFilter,
                    onToggle = { key -> viewModel.setRecipeFilters(recipeFilters.copy(statFilter = toggledSet(recipeFilters.statFilter, key))) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Sort", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                SortSelector(
                    options = listOf(
                        RecipeSortMode.TIER to "By Tier",
                        RecipeSortMode.ALPHABETICAL to "Alphabetical",
                        RecipeSortMode.LEVEL to "By Level"
                    ),
                    selectedOption = recipeSort,
                    onSelect = { viewModel.setRecipeSort(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("Select a Recipe", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            if (tieredRecipes.isEmpty()) {
                Text(
                    "No recipes unlocked yet. Find a Grimoire to unlock the next tier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (displayedTieredRecipes.all { it.recipes.isEmpty() }) {
                Text(
                    "No recipes match the current filters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayedTieredRecipes.forEach { tier ->
                    val isUnlocked = cookingLevel >= tier.minLevel
                    val isExpanded = viewModel.isTierExpanded(tier.label, isUnlocked)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords -> tierPositions[tier.label] = coords.positionInParent().y.roundToInt() }
                            .clickable { viewModel.toggleTierExpanded(tier.label, isUnlocked) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (isExpanded) "▾ " else "▸ ") + tier.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isUnlocked) {
                            Text(
                                "Reach Lv ${tier.minLevel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        tier.recipes.forEach { recipe ->
                            val canCook = isUnlocked && !bothBusy && viewModel.canCook(recipe, inventoryItems)
                            RecipeRow(
                                recipe = recipe,
                                canCook = canCook,
                                isSelected = recipe.id == selectedRecipe?.id,
                                isLocked = !isUnlocked,
                                onClick = { if (isUnlocked) viewModel.selectRecipe(recipe) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RecipeRow(
    recipe: Recipe,
    canCook: Boolean,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.4f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = if (canCook && !isLocked) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        val statTag = when {
            recipe.penalty && recipe.primaryStat != null ->
                when (recipe.primaryStat) { "mig"->"−M"; "agi"->"−A"; "vit"->"−V"; "wil"->"−W"; else->"−" }
            recipe.primaryStat != null ->
                when (recipe.primaryStat) { "mig"->"M"; "agi"->"A"; "vit"->"V"; "wil"->"W"; else->recipe.primaryStat }
            recipe.hazardEffect != null ->
                when (recipe.hazardEffect) { "potency"->"P"; "hale"->"H"; "warmth"->"Warm"; "radiance"->"R"; "alert"->"Alt"; else->recipe.hazardEffect }
            else -> ""
        }
        val isDraught = recipe.hazardEffect != null
        val tagColor = when {
            recipe.penalty -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!isLocked && canCook) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (statTag.isNotEmpty()) {
                    Text(
                        statTag,
                        style = if (isDraught) MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)
                                else MaterialTheme.typography.labelSmall,
                        color = tagColor
                    )
                }
            }
            Text(
                when {
                    isLocked -> "🔒"
                    canCook -> "✓"
                    else -> "✗"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isLocked -> MaterialTheme.colorScheme.onSurfaceVariant
                    canCook -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun RecipeDetailPanel(
    recipe: Recipe,
    inventoryItems: List<InventoryItem>,
    cookingLevel: Int,
    predictedGrade: Pair<Grade, Boolean>?,
    viewModel: KitchenViewModel
) {
    val selectedGrades by viewModel.selectedIngredientGrades.collectAsState()

    val statName: String? = when (recipe.primaryStat) {
        "mig" -> "Might"; "agi" -> "Agility"
        "vit" -> "Vitality"; "wil" -> "Will"
        else -> recipe.primaryStat
    }
    val gradeToUse = predictedGrade?.first ?: Grade.FINE
    val scaledBoost = (recipe.primaryBoost * gradeMultiplier(gradeToUse)).roundToInt()
    val effectLine = when {
        recipe.penalty && statName != null -> "$statName $scaledBoost"
        recipe.primaryStat != null && statName != null -> "$statName +$scaledBoost"
        recipe.hazardEffect != null -> when (recipe.hazardEffect) {
            "warmth" -> "Warmth (cold resist)"
            "alert" -> "Alert (fatigue resist)"
            "hale" -> "Hale (disease resist)"
            "potency" -> "Potency (armor penetration)"
            "radiance" -> "Radiance (shadow resist)"
            else -> recipe.hazardEffect
        }
        else -> "Sustaining"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.titleSmall)
            Text(recipe.description, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                effectLine,
                style = MaterialTheme.typography.labelMedium,
                color = if (recipe.penalty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )

            if (predictedGrade != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Predicted: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GradeBadge(predictedGrade.first.ordinal)
                    if (predictedGrade.second) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Cook Lv caps this",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Ingredients:", style = MaterialTheme.typography.labelMedium)
            recipe.ingredients.forEach { ing ->
                val name = viewModel.ingredientName(ing.id)
                val isHero = ing.id == recipe.heroIngredient
                val chosenGrade = selectedGrades[ing.id] ?: 0
                val availableGrades = Grade.entries.filter { g ->
                    (inventoryItems.find { it.ingredientId == ing.id && it.grade == g.ordinal }?.quantity ?: 0) >= ing.qty
                }
                val have = inventoryItems.filter { it.ingredientId == ing.id }.sumOf { it.quantity }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isHero) "★ $name  $have/${ing.qty}"
                        else "• $name  $have/${ing.qty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (have >= ing.qty) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    if (isHero) {
                        Text(
                            "hero",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (availableGrades.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableGrades.forEach { g ->
                            FilterChip(
                                selected = chosenGrade == g.ordinal,
                                onClick = { viewModel.setIngredientGrade(ing.id, g.ordinal) },
                                label = { Text(g.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                } else if (availableGrades.size == 1) {
                    GradeBadge(availableGrades.first().ordinal)
                }
            }
        }
    }
}

@Composable
private fun CookingSlotCard(
    slot: Int,
    session: CookingSession?,
    viewModel: KitchenViewModel,
    modifier: Modifier = Modifier
) {
    val slotLabel = if (slot == 0) "Kiln 1" else "Kiln 2"
    if (session != null) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(session.startedAtMs) {
            while (true) { now = System.currentTimeMillis(); delay(1000L) }
        }
        val remaining = maxOf(0L, session.startedAtMs + session.durationMs - now)
        val recipeName = viewModel.recipes.find { it.id == session.recipeId }?.name ?: session.recipeId
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(slotLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(recipeName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(formatMs(remaining), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(slotLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Open", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun KitchenXpBar(level: Int, earned: Int, needed: Int) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            "Cooking lv$level",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        LinearProgressIndicator(
            progress = { earned.toFloat() / needed.toFloat().coerceAtLeast(1f) },
            modifier = Modifier.weight(1f).height(6.dp)
        )
        Text(
            "  $earned/$needed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProcessPanel(
    viewModel: KitchenViewModel,
    processSlot: GrowingSlot?,
    processIngredients: List<Ingredient>,
    selectedProcessIngredient: Ingredient?,
    inventoryItems: List<InventoryItem>
) {
    when {
        processSlot?.pendingResultJson != null -> {
            val ingredientName = viewModel.ingredientName(processSlot.ingredientId ?: "")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Preparation complete", style = MaterialTheme.typography.titleSmall)
                    Text(ingredientName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.collectProcess() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Collect")
                    }
                }
            }
        }
        processSlot != null -> {
            val ingredientName = viewModel.ingredientName(processSlot.ingredientId ?: "")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Preparing: $ingredientName", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessTimer(startedAtMs = processSlot.plantedAtMs, durationMs = processSlot.durationMs)
                        Text(
                            " remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        else -> {
            if (processIngredients.isEmpty()) {
                Text(
                    "No processable items available. Gather raw ingredients first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Select an item to prepare:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                processIngredients.forEach { ingredient ->
                    val canDo = viewModel.canProcess(ingredient, inventoryItems)
                    val isSelected = ingredient.id == selectedProcessIngredient?.id
                    ProcessItemRow(
                        ingredient = ingredient,
                        canProcess = canDo,
                        isSelected = isSelected,
                        inventoryItems = inventoryItems,
                        viewModel = viewModel,
                        onClick = { viewModel.selectProcessIngredient(if (isSelected) null else ingredient) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (selectedProcessIngredient != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.startProcess(selectedProcessIngredient) },
                        enabled = viewModel.canProcess(selectedProcessIngredient, inventoryItems),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Preparing")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessItemRow(
    ingredient: Ingredient,
    canProcess: Boolean,
    isSelected: Boolean,
    inventoryItems: List<InventoryItem>,
    viewModel: KitchenViewModel,
    onClick: () -> Unit
) {
    val qtyMap = remember(inventoryItems) {
        inventoryItems.groupBy { it.ingredientId }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    }
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canProcess) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ingredient.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ingredient.processType?.replaceFirstChar { it.uppercase() } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ingredient.processInputs?.forEach { input ->
                val have = qtyMap[input.id] ?: 0
                val name = viewModel.ingredientName(input.id)
                Text(
                    "• $name  $have/${input.qty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (have >= input.qty) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ProcessTimer(startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }
    val remaining = maxOf(0L, startedAtMs + durationMs - now)
    Text(
        formatMs(remaining),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
}
```

- [ ] **Step 2: Compile check**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt
git commit -m "[hc] Kitchen Recipes tab: sticky panel, collapsible tiers, filters, sort"
```

---

### Task 5: Pantry screen — filters and sort

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/PantryScreen.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt` — no change expected; `PantryScreen(onBack = ...)` call site does not pass `viewModel` explicitly, so swapping the default parameter type is source-compatible. Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt:93` to confirm before starting, but do not edit it unless the compile check in Step 2 fails there.

**Interfaces:**
- Consumes: everything produced by Task 2 (`PantryViewModel`, `PantryFilterState`, `PantrySortMode`) and Task 3 (`FilterChipRow`, `SortSelector`, `toggledSet`). `GradeBadge(grade: Int)` is an existing composable in `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GradeBadge.kt`, unchanged.
- Produces: nothing new consumed elsewhere.

- [ ] **Step 1: Replace the file**

Replace the full contents of `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/PantryScreen.kt` with:

```kotlin
package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.ui.viewmodel.PantrySortMode
import com.liquidcode7.hearthcraft.ui.viewmodel.PantryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(onBack: () -> Unit = {}, viewModel: PantryViewModel = hiltViewModel()) {
    val displayedIngredients by viewModel.displayedIngredients.collectAsState()
    val preparedFood by viewModel.preparedFood.collectAsState()
    val money by viewModel.money.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pantry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Gold: $money",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("Ingredients", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Filter by grade", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        FilterChipRow(
            options = Grade.entries.map { it.ordinal to it.displayName },
            selected = filters.gradeFilter,
            onToggle = { g -> viewModel.setFilters(filters.copy(gradeFilter = toggledSet(filters.gradeFilter, g))) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        Text("Filter by stat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        FilterChipRow(
            options = listOf("mig" to "Might", "agi" to "Agility", "vit" to "Vitality", "wil" to "Will"),
            selected = filters.statFilter,
            onToggle = { s -> viewModel.setFilters(filters.copy(statFilter = toggledSet(filters.statFilter, s))) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        Text("Sort", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        SortSelector(
            options = listOf(PantrySortMode.QUANTITY to "Quantity", PantrySortMode.ALPHABETICAL to "Alphabetical"),
            selectedOption = sortMode,
            onSelect = { viewModel.setSortMode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (displayedIngredients.isEmpty()) {
            Text(
                if (filters.gradeFilter.isEmpty() && filters.statFilter.isEmpty()) "No ingredients gathered yet."
                else "No ingredients match the current filters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    displayedIngredients.forEachIndexed { i, stock ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stock.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            GradeBadge(stock.grade)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("×${stock.quantity}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (i < displayedIngredients.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Prepared Food", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (preparedFood.isEmpty()) {
            Text(
                "Nothing cooked yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            preparedFood.forEach { food ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(food.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${food.buffType} +${food.buffStrength}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        GradeBadge(food.grade)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("×${food.quantity}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. `MainScreen.kt:93`'s `PantryScreen(onBack = { navController.popBackStack() })` call does not name `viewModel`, so it resolves to the new default (`hiltViewModel<PantryViewModel>()`) automatically. If this step fails specifically at `MainScreen.kt`, read that file and report back rather than guessing a fix — the plan's assumption (no explicit `viewModel:` argument at the call site) should be re-verified against current `main`.

- [ ] **Step 3: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/PantryScreen.kt
git commit -m "[hc] Pantry: add ingredient filter/sort controls"
```

---

### Task 6: Manual verification pass

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Full test suite**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no regressions in any existing test class.

- [ ] **Step 2: Debug build**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual click-through (requires a connected device or emulator; if none is available, report this step as skipped rather than guessing a result)**

Install the debug APK and verify, on the Kitchen Recipes tab:
- Selecting a recipe deep in a lower tier does not jump the scroll position — the recipe detail panel updates in place at the top, and the tier list below stays where it was scrolled.
- Tapping a tier header collapses/expands only that tier.
- Tapping a "Jump to tier" chip scrolls to and expands that tier, collapsing all others.
- The "Cookable now" chip, when active, hides recipes the player can't currently cook.
- The Food/Draught and stat filter chips combine correctly (e.g. selecting "Draught" + "Might" shows only draughts that boost/penalize Might).
- Each sort mode (By Tier / Alphabetical / By Level) reorders recipes within a tier without moving any recipe to a different tier section.

And on Pantry:
- The grade and stat filter chips narrow the ingredient list correctly, combinable with each other.
- Quantity and Alphabetical sort both produce the expected order.
- The Prepared Food list is unchanged (no filter/sort controls, same display as before).

- [ ] **Step 4: Report**

No commit for this task — report the outcome of Steps 1–3 (pass/fail/skipped-with-reason) to the user.
