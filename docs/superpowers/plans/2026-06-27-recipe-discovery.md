# Recipe Discovery System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the always-visible recipe list with a GW2-style discovery system where recipes are hidden until the player experiments with ingredients or levels up.

**Architecture:** The `RecipeDiscoveryEngine` is a pure Kotlin object (no Android) that evaluates `ExperimentAttempt` inputs and returns a sealed `ExperimentResult`. `PlayerState` stores discovered recipe IDs as a comma-separated string (Room v7 migration). `KitchenViewModel` filters `tieredRecipes` to discovered-only and owns experiment state. `KitchenScreen` gains a mode toggle (Recipes | Experiment) and an experiment panel.

**Tech Stack:** Kotlin, Room AutoMigration, Jetpack Compose / Material 3, Hilt, kotlinx.coroutines `combine`

## Global Constraints

- Min SDK API 26, target SDK 36
- All source under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`; never under `java/`
- No Google Play Services; no internet; GPL-3.0
- Never bump versionCode/versionName unless explicitly told to
- Do NOT use "Hearthwright" or "warlock-culinarian" in comments or strings
- Build command: `JAVA_HOME=/opt/jetbrains-toolbox/jre ./gradlew assembleDebug --no-daemon`
- Test command: `JAVA_HOME=/opt/jetbrains-toolbox/jre ./gradlew test --no-daemon`
- One logical change per commit; prefix messages with `[v1]`

---

## File Map

| Action | File |
|--------|------|
| Modify | `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PlayerState.kt` |
| Modify | `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt` |
| Modify | `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt` |
| Create | `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngine.kt` |
| Create | `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngineTest.kt` |
| Modify | `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt` |
| Modify | `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt` |
| Modify | `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt` |

---

## Task 1: Room v7 migration + PlayerRepository

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PlayerState.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt`

**Interfaces:**
- Produces: `PlayerRepository.observeDiscoveredIds(): Flow<Set<String>>`
- Produces: `PlayerRepository.discoverRecipe(id: String)`
- Produces: `PlayerRepository.discoverRecipes(ids: Collection<String>)`
- Produces: `PlayerRepository.markHintsSeen()`

- [ ] **Step 1: Add two new fields to PlayerState**

Replace the entire file:

```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_state")
data class PlayerState(
    @PrimaryKey val id: Int = 0,
    val chosenBandId: String = "",
    val secondBandId: String = "",
    val gatheringLevel: Int = 1,
    val gatheringXp: Int = 0,
    val cookingLevel: Int = 1,
    val cookingXp: Int = 0,
    val money: Int = 0,
    @ColumnInfo(defaultValue = "")
    val discoveredRecipeIds: String = "",
    @ColumnInfo(defaultValue = "0")
    val hasSeenFoodStructureHints: Boolean = false
)
```

- [ ] **Step 2: Bump HearthCraftDatabase to version 7**

In `HearthCraftDatabase.kt`, change `version = 6` to `version = 7` and add the auto-migration entry:

```kotlin
@Database(
    entities = [
        PlayerState::class,
        InventoryItem::class,
        PreparedFood::class,
        GatheringSession::class,
        CookingSession::class,
        MissionSession::class,
        EncounterSession::class,
        BandMemberState::class,
        SeedStock::class,
        GrowingSlot::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7)
    ]
)
abstract class HearthCraftDatabase : RoomDatabase() {
    abstract fun playerStateDao(): PlayerStateDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun preparedFoodDao(): PreparedFoodDao
    abstract fun gatheringSessionDao(): GatheringSessionDao
    abstract fun cookingSessionDao(): CookingSessionDao
    abstract fun missionSessionDao(): MissionSessionDao
    abstract fun encounterSessionDao(): EncounterSessionDao
    abstract fun bandMemberStateDao(): BandMemberStateDao
    abstract fun seedStockDao(): SeedStockDao
    abstract fun growingSlotDao(): GrowingSlotDao
}
```

- [ ] **Step 3: Add discovery methods to PlayerRepository**

Add these four methods to `PlayerRepository` (below `spendMoney`). The existing imports are sufficient — no new ones needed:

```kotlin
fun observeDiscoveredIds(): Flow<Set<String>> = dao.observe().map { state ->
    state?.discoveredRecipeIds
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()
}

suspend fun discoverRecipe(recipeId: String) {
    val state = dao.get() ?: return
    val current = state.discoveredRecipeIds
        .split(",").filter { it.isNotBlank() }.toMutableSet()
    if (current.add(recipeId)) {
        dao.upsert(state.copy(discoveredRecipeIds = current.joinToString(",")))
    }
}

suspend fun discoverRecipes(recipeIds: Collection<String>) {
    if (recipeIds.isEmpty()) return
    val state = dao.get() ?: return
    val current = state.discoveredRecipeIds
        .split(",").filter { it.isNotBlank() }.toMutableSet()
    if (current.addAll(recipeIds)) {
        dao.upsert(state.copy(discoveredRecipeIds = current.joinToString(",")))
    }
}

suspend fun markHintsSeen() {
    val state = dao.get() ?: return
    if (!state.hasSeenFoodStructureHints) {
        dao.upsert(state.copy(hasSeenFoodStructureHints = true))
    }
}
```

You also need `import kotlinx.coroutines.flow.map` — add it to the existing import block if it isn't there already.

- [ ] **Step 4: Build to verify migration compiles**

```
JAVA_HOME=/opt/jetbrains-toolbox/jre ./gradlew assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL. Room will generate the v7 schema JSON automatically.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PlayerState.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt
git add app/schemas/
git commit -m "[v1] DB v6→v7: add discoveredRecipeIds + hasSeenFoodStructureHints to PlayerState"
```

---

## Task 2: RecipeDiscoveryEngine

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngine.kt`
- Create: `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngineTest.kt`

**Interfaces:**
- Consumes: `Recipe`, `RecipeIngredient` from `data.model`
- Produces: `ExperimentAttempt`, `ExperimentResult` (sealed), `ProximityTier`, `RecipeDiscoveryEngine.evaluate()`

- [ ] **Step 1: Write the failing test first**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngineTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDiscoveryEngineTest {

    private val gruel = Recipe(
        id = "simple_gruel",
        name = "Simple Gruel",
        method = "simmer",
        tier = 1,
        cookLevel = 1,
        ingredients = listOf(
            RecipeIngredient("oats", 2),
            RecipeIngredient("water", 1)
        )
    )

    private val allRecipes = listOf(gruel)

    @Test
    fun `exact match with no prior knowledge returns Discovered`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water"),
            quantities = mapOf("oats" to 2, "water" to 1),
            method = "simmer"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Discovered)
        assertEquals("simple_gruel", (result as ExperimentResult.Discovered).recipe.id)
    }

    @Test
    fun `exact match when already known returns AlreadyKnown`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water"),
            quantities = mapOf("oats" to 2, "water" to 1),
            method = "simmer"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, setOf("simple_gruel"))
        assertTrue(result is ExperimentResult.AlreadyKnown)
    }

    @Test
    fun `right ingredients and qty but wrong method returns NEAR_MISS`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water"),
            quantities = mapOf("oats" to 2, "water" to 1),
            method = "bake"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.NEAR_MISS, (result as ExperimentResult.Failure).proximity)
    }

    @Test
    fun `right ingredients and method but wrong qty returns NEAR_MISS`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water"),
            quantities = mapOf("oats" to 1, "water" to 1),
            method = "simmer"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.NEAR_MISS, (result as ExperimentResult.Failure).proximity)
    }

    @Test
    fun `all recipe ingredients present with an extra and right method returns CLOSE`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water", "salt"),
            quantities = mapOf("oats" to 2, "water" to 1, "salt" to 1),
            method = "simmer"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.CLOSE, (result as ExperimentResult.Failure).proximity)
    }

    @Test
    fun `partial ingredient overlap returns SOME`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "milk"),
            quantities = mapOf("oats" to 2, "milk" to 1),
            method = "brew"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.SOME, (result as ExperimentResult.Failure).proximity)
    }

    @Test
    fun `completely unrelated ingredients returns NONE`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("gold_dust", "silver_bark"),
            quantities = mapOf("gold_dust" to 1, "silver_bark" to 1),
            method = "brew"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.NONE, (result as ExperimentResult.Failure).proximity)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
JAVA_HOME=/opt/jetbrains-toolbox/jre ./gradlew test --no-daemon
```

Expected: compilation failure — `ExperimentAttempt`, `RecipeDiscoveryEngine`, `ExperimentResult`, `ProximityTier` not yet defined.

- [ ] **Step 3: Create RecipeDiscoveryEngine.kt**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngine.kt`:

```kotlin
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Recipe

data class ExperimentAttempt(
    val ingredientIds: List<String>,
    val quantities: Map<String, Int>,
    val method: String
)

sealed class ExperimentResult {
    data class Discovered(val recipe: Recipe) : ExperimentResult()
    data class AlreadyKnown(val recipe: Recipe) : ExperimentResult()
    data class Failure(val proximity: ProximityTier) : ExperimentResult()
}

enum class ProximityTier { NONE, SOME, CLOSE, NEAR_MISS }

object RecipeDiscoveryEngine {

    fun evaluate(
        attempt: ExperimentAttempt,
        allRecipes: List<Recipe>,
        discoveredIds: Set<String>
    ): ExperimentResult {
        for (recipe in allRecipes) {
            if (isExactMatch(attempt, recipe)) {
                return if (recipe.id in discoveredIds) {
                    ExperimentResult.AlreadyKnown(recipe)
                } else {
                    ExperimentResult.Discovered(recipe)
                }
            }
        }
        val best = allRecipes.maxOfOrNull { proximityScore(attempt, it) } ?: 0
        val tier = when (best) {
            4 -> ProximityTier.NEAR_MISS
            3 -> ProximityTier.CLOSE
            2 -> ProximityTier.SOME
            else -> ProximityTier.NONE
        }
        return ExperimentResult.Failure(tier)
    }

    private fun isExactMatch(attempt: ExperimentAttempt, recipe: Recipe): Boolean {
        val recipeIds = recipe.ingredients.map { it.id }.toSet()
        if (attempt.ingredientIds.toSet() != recipeIds) return false
        if (attempt.method != recipe.method) return false
        return recipe.ingredients.all { needed -> attempt.quantities[needed.id] == needed.qty }
    }

    private fun proximityScore(attempt: ExperimentAttempt, recipe: Recipe): Int {
        val recipeIds = recipe.ingredients.map { it.id }.toSet()
        val attemptIds = attempt.ingredientIds.toSet()
        val methodMatches = attempt.method == recipe.method
        val allIngredientsMatch = recipeIds == attemptIds
        val quantitiesMatch = recipe.ingredients.all { needed ->
            attempt.quantities[needed.id] == needed.qty
        }
        if (allIngredientsMatch && quantitiesMatch && !methodMatches) return 4
        if (allIngredientsMatch && methodMatches && !quantitiesMatch) return 4
        val allPresent = recipeIds.all { it in attemptIds }
        if (allPresent && methodMatches) return 3
        val overlap = attemptIds.intersect(recipeIds).size
        val minSize = minOf(attemptIds.size, recipeIds.size)
        if (minSize > 0 && overlap.toFloat() / minSize >= 0.5f) return 2
        return 0
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
JAVA_HOME=/opt/jetbrains-toolbox/jre ./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all 7 `RecipeDiscoveryEngineTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngine.kt
git add app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngineTest.kt
git commit -m "[v1] Engine: RecipeDiscoveryEngine with proximity-tier feedback"
```

---

## Task 3: KitchenViewModel — discovered filter + experiment state

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt`

**Interfaces:**
- Consumes: `PlayerRepository.observeDiscoveredIds()`, `PlayerRepository.discoverRecipe()`, `PlayerRepository.discoverRecipes()`, `PlayerRepository.markHintsSeen()`
- Consumes: `RecipeDiscoveryEngine.evaluate()`, `ExperimentAttempt`, `ExperimentResult`, `ProximityTier`
- Produces: `KitchenViewModel.discoveredIds: StateFlow<Set<String>>`
- Produces: `KitchenViewModel.tieredRecipes` (now filters to discovered-only)
- Produces: `KitchenViewModel.experimentMode: StateFlow<Boolean>`
- Produces: `KitchenViewModel.experimentIngredients: StateFlow<Map<String, Int>>`
- Produces: `KitchenViewModel.experimentMethod: StateFlow<String>`
- Produces: `KitchenViewModel.lastExperimentResult: StateFlow<ExperimentResult?>`
- Produces: `KitchenViewModel.hintsSeen: StateFlow<Boolean>`
- Produces: fun `toggleExperimentMode()`, `addExperimentIngredient(id)`, `removeExperimentIngredient(id)`, `updateExperimentQty(id, qty)`, `setExperimentMethod(method)`, `submitExperiment()`, `clearExperimentResult()`, `markHintsSeen()`

- [ ] **Step 1: Replace KitchenViewModel.kt with the updated version**

```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.engine.ExperimentAttempt
import com.liquidcode7.hearthcraft.engine.ExperimentResult
import com.liquidcode7.hearthcraft.engine.RecipeDiscoveryEngine
import com.liquidcode7.hearthcraft.worker.CookingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeTier(val label: String, val minLevel: Int, val recipes: List<Recipe>)

@HiltViewModel
class KitchenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    private val sessions: SessionRepository,
    private val player: PlayerRepository
) : ViewModel() {

    val recipes: List<Recipe> = gameData.recipes

    val inventoryItems: StateFlow<List<InventoryItem>> = inventory.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val session: StateFlow<CookingSession?> = sessions.observeCooking()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playerState: StateFlow<PlayerState?> = player.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val discoveredIds: StateFlow<Set<String>> = player.observeDiscoveredIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val hintsSeen: StateFlow<Boolean> = player.observe()
        .map { it?.hasSeenFoodStructureHints ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val bandRecipes: StateFlow<List<Recipe>> = combine(
        player.observe(),
        player.observeDiscoveredIds()
    ) { state, discovered ->
        val bandId = state?.chosenBandId.orEmpty()
        gameData.recipes.filter { (it.band == bandId || it.band == "all") && it.id in discovered }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tierNames = mapOf(
        1 to "Hearthkeeper", 2 to "Initiate", 3 to "Apprentice", 4 to "Journeyman", 5 to "Adept"
    )

    val tieredRecipes: StateFlow<List<RecipeTier>> = combine(
        inventory.observeIngredients(),
        player.observe(),
        player.observeDiscoveredIds()
    ) { items, state, discovered ->
        val bandId = state?.chosenBandId.orEmpty()
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        gameData.recipes
            .filter { (it.band == bandId || it.band == "all") && bandId.isNotEmpty() }
            .filter { it.id in discovered }
            .groupBy { it.tier }
            .entries.sortedBy { it.key }
            .map { (tier, recipes) ->
                val minLevel = recipes.minOf { it.cookLevel }
                val sorted = recipes.sortedWith(
                    compareByDescending<Recipe> {
                        it.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
                    }.thenBy { it.cookLevel }
                )
                RecipeTier(tierNames[tier] ?: "Tier $tier", minLevel, sorted)
            }
            .filter { it.recipes.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedRecipes: StateFlow<List<Recipe>> = combine(
        inventory.observeIngredients(),
        player.observe()
    ) { items, state ->
        val bandId = state?.chosenBandId.orEmpty()
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        val filtered = gameData.recipes.filter { it.band == bandId || it.band == "all" }
        val (can, cannot) = filtered.partition { recipe ->
            recipe.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
        }
        can + cannot
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe.asStateFlow()

    // ── Experiment state ──────────────────────────────────────────────────────

    private val _experimentMode = MutableStateFlow(false)
    val experimentMode: StateFlow<Boolean> = _experimentMode.asStateFlow()

    private val _experimentIngredients = MutableStateFlow<Map<String, Int>>(emptyMap())
    val experimentIngredients: StateFlow<Map<String, Int>> = _experimentIngredients.asStateFlow()

    private val _experimentMethod = MutableStateFlow("simmer")
    val experimentMethod: StateFlow<String> = _experimentMethod.asStateFlow()

    private val _lastExperimentResult = MutableStateFlow<ExperimentResult?>(null)
    val lastExperimentResult: StateFlow<ExperimentResult?> = _lastExperimentResult.asStateFlow()

    // ── Init: seed starter recipes for a brand-new player ────────────────────

    init {
        viewModelScope.launch {
            val state = player.get() ?: return@launch
            if (state.discoveredRecipeIds.isBlank()) {
                val starters = gameData.recipes.filter { it.cookLevel <= 1 }.map { it.id }
                player.discoverRecipes(starters)
            }
        }
    }

    // ── Existing recipe functions ─────────────────────────────────────────────

    fun selectRecipe(recipe: Recipe) { _selectedRecipe.value = recipe }

    fun ingredientName(id: String): String = gameData.ingredients.find { it.id == id }?.name ?: id

    fun canCook(recipe: Recipe, items: List<InventoryItem>): Boolean {
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        return recipe.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
    }

    fun startCooking() {
        val recipe = _selectedRecipe.value ?: return
        viewModelScope.launch {
            if (sessions.activeCooking() != null) return@launch
            recipe.ingredients.forEach { inventory.removeIngredient(it.id, it.qty) }
            val request = CookingWorker.buildRequest(recipe.id, recipe.durationMs)
            WorkManager.getInstance(context).enqueue(request)
            sessions.startCooking(
                CookingSession(
                    recipeId = recipe.id,
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = recipe.durationMs,
                    workRequestId = request.id.toString()
                )
            )
        }
    }

    // ── Experiment functions ──────────────────────────────────────────────────

    fun toggleExperimentMode() {
        _experimentMode.value = !_experimentMode.value
        _lastExperimentResult.value = null
    }

    fun addExperimentIngredient(id: String) {
        if (id !in _experimentIngredients.value && _experimentIngredients.value.size < 4) {
            _experimentIngredients.value = _experimentIngredients.value + (id to 1)
        }
    }

    fun removeExperimentIngredient(id: String) {
        _experimentIngredients.value = _experimentIngredients.value - id
    }

    fun updateExperimentQty(id: String, qty: Int) {
        if (qty <= 0) removeExperimentIngredient(id)
        else _experimentIngredients.value = _experimentIngredients.value + (id to qty.coerceAtMost(5))
    }

    fun setExperimentMethod(method: String) { _experimentMethod.value = method }

    fun clearExperimentResult() { _lastExperimentResult.value = null }

    fun submitExperiment() {
        val ingredients = _experimentIngredients.value
        if (ingredients.isEmpty()) return
        viewModelScope.launch {
            val attempt = ExperimentAttempt(
                ingredientIds = ingredients.keys.toList(),
                quantities = ingredients,
                method = _experimentMethod.value
            )
            ingredients.forEach { (id, qty) -> inventory.removeIngredient(id, qty) }
            val result = RecipeDiscoveryEngine.evaluate(attempt, gameData.recipes, discoveredIds.value)
            if (result is ExperimentResult.Discovered) {
                player.discoverRecipe(result.recipe.id)
            }
            _lastExperimentResult.value = result
            _experimentIngredients.value = emptyMap()
        }
    }

    fun markHintsSeen() {
        viewModelScope.launch { player.markHintsSeen() }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```
JAVA_HOME=/opt/jetbrains-toolbox/jre ./gradlew assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt
git commit -m "[v1] KitchenViewModel: discovery filter, experiment state, seeded starters"
```

---

## Task 4: CookingWorker — auto-discover on cook + level-up

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt`

**Interfaces:**
- Consumes: `PlayerRepository.discoverRecipe(id)`, `PlayerRepository.discoverRecipes(ids)`
- Consumes: `PlayerRepository.get(): PlayerState?`

- [ ] **Step 1: Update CookingWorker.doWork() to discover the cooked recipe and auto-discover on level-up**

Replace the `doWork()` function (lines 34–50 in the current file):

```kotlin
override suspend fun doWork(): Result {
    val recipeId = inputData.getString(KEY_RECIPE_ID) ?: return Result.failure()
    val recipe = gameData.recipes.find { it.id == recipeId } ?: return Result.failure()

    val oldLevel = player.get()?.cookingLevel ?: 1

    val isFirstCook = inventory.preparedFoodQty(recipeId) == 0
    inventory.addPreparedFood(recipeId)
    val cookingXp = if (isFirstCook) PlayerRepository.XP_COOK_FIRST else PlayerRepository.XP_COOK_REPEAT
    player.addCookingXp(cookingXp)

    // Always discover the recipe you just cooked
    player.discoverRecipe(recipeId)

    // If cooking XP caused a level-up, auto-discover all recipes now accessible
    val newLevel = player.get()?.cookingLevel ?: 1
    if (newLevel > oldLevel) {
        val currentDiscovered = player.get()?.discoveredRecipeIds
            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val toDiscover = gameData.recipes
            .filter { it.cookLevel <= newLevel && it.id !in currentDiscovered }
            .map { it.id }
        player.discoverRecipes(toDiscover)
    }

    sessions.clearCooking()

    notify("Cooking Complete", "${recipe.name} is ready.", NOTIFICATION_ID)

    return Result.success()
}
```

- [ ] **Step 2: Build to verify**

```
JAVA_HOME=/opt/jetbrains-toolbox/jre ./gradlew assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt
git commit -m "[v1] CookingWorker: discover cooked recipe + auto-discover on level-up"
```

---

## Task 5: KitchenScreen — mode toggle, Experiment UI, food hints card

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`

**Interfaces:**
- Consumes: all the new `KitchenViewModel` StateFlows and functions from Task 3
- Consumes: `ExperimentResult`, `ProximityTier` from `engine` package

- [ ] **Step 1: Replace KitchenScreen.kt with the updated version**

The existing screen has two sections: fixed top (detail panel) and scrollable recipe list. We add:
- A `TabRow` below the "Kitchen" header: **Recipes** | **Experiment**
- In Experiment mode: method chips, ingredient slots, submit, result card, food hints

Replace the entire file:

```kotlin
package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.engine.ExperimentResult
import com.liquidcode7.hearthcraft.engine.ProximityTier
import com.liquidcode7.hearthcraft.ui.viewmodel.KitchenViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeTier
import kotlinx.coroutines.delay

@Composable
fun KitchenScreen(
    onViewRecipes: () -> Unit,
    onViewPantry: () -> Unit = {},
    viewModel: KitchenViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val selectedRecipe by viewModel.selectedRecipe.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val tieredRecipes by viewModel.tieredRecipes.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val experimentMode by viewModel.experimentMode.collectAsState()
    val experimentIngredients by viewModel.experimentIngredients.collectAsState()
    val experimentMethod by viewModel.experimentMethod.collectAsState()
    val lastResult by viewModel.lastExperimentResult.collectAsState()
    val hintsSeen by viewModel.hintsSeen.collectAsState()
    val cookingLevel = playerState?.cookingLevel ?: 1
    val isCooking = session != null

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Fixed top section ──────────────────────────────────────────────
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Kitchen", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (!isCooking) {
                TabRow(selectedTabIndex = if (experimentMode) 1 else 0) {
                    Tab(
                        selected = !experimentMode,
                        onClick = { if (experimentMode) viewModel.toggleExperimentMode() },
                        text = { Text("Recipes") }
                    )
                    Tab(
                        selected = experimentMode,
                        onClick = { if (!experimentMode) viewModel.toggleExperimentMode() },
                        text = { Text("Experiment") }
                    )
                }
            }
        }

        HorizontalDivider()

        // ── Scrollable content ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (isCooking) {
                val recipeName = viewModel.recipes.find { it.id == session!!.recipeId }?.name
                    ?: session!!.recipeId
                CookingActiveCard(
                    recipeName = recipeName,
                    startedAtMs = session!!.startedAtMs,
                    durationMs = session!!.durationMs
                )
            } else if (experimentMode) {
                ExperimentPanel(
                    viewModel = viewModel,
                    inventoryItems = inventoryItems,
                    experimentIngredients = experimentIngredients,
                    experimentMethod = experimentMethod,
                    lastResult = lastResult,
                    cookingLevel = cookingLevel,
                    hintsSeen = hintsSeen
                )
            } else {
                // Recipes mode
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onViewRecipes, modifier = Modifier.weight(1f)) {
                        Text("Recipe Book")
                    }
                    OutlinedButton(onClick = onViewPantry, modifier = Modifier.weight(1f)) {
                        Text("Pantry")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedRecipe != null) {
                    RecipeDetailPanel(
                        recipe = selectedRecipe!!,
                        inventoryItems = inventoryItems,
                        cookingLevel = cookingLevel,
                        viewModel = viewModel
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.startCooking() },
                        enabled = viewModel.canCook(selectedRecipe!!, inventoryItems),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Cooking")
                    }
                } else {
                    Text(
                        "Select a recipe below to see details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select a Recipe", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                if (tieredRecipes.isEmpty()) {
                    Text(
                        "No recipes discovered yet. Head to the Experiment tab to find them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    tieredRecipes.forEach { tier ->
                        val isUnlocked = cookingLevel >= tier.minLevel
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val rangeLabel = if (tier.minLevel <= 1) "Lv 1" else "Lv ${tier.minLevel}+"
                            Text(
                                "${tier.label}  ·  $rangeLabel",
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
                        Spacer(modifier = Modifier.height(6.dp))
                        tier.recipes.forEach { recipe ->
                            val canCook = isUnlocked && viewModel.canCook(recipe, inventoryItems)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExperimentPanel(
    viewModel: KitchenViewModel,
    inventoryItems: List<InventoryItem>,
    experimentIngredients: Map<String, Int>,
    experimentMethod: String,
    lastResult: ExperimentResult?,
    cookingLevel: Int,
    hintsSeen: Boolean
) {
    val methods = listOf("simmer", "cook", "bake", "roast", "infuse", "brew")

    if (cookingLevel >= 3) {
        FoodHintsCard(expanded = !hintsSeen, onToggle = { viewModel.markHintsSeen() })
        Spacer(modifier = Modifier.height(12.dp))
    }

    Text("Method", style = MaterialTheme.typography.labelMedium)
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        methods.forEach { method ->
            FilterChip(
                selected = experimentMethod == method,
                onClick = { viewModel.setExperimentMethod(method) },
                label = { Text(method.replaceFirstChar { it.uppercase() }) }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Ingredients  (up to 4)", style = MaterialTheme.typography.labelMedium)
    Spacer(modifier = Modifier.height(4.dp))

    experimentIngredients.entries.forEachIndexed { _, (id, qty) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                viewModel.ingredientName(id),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.updateExperimentQty(id, qty - 1) }) {
                Text("−", style = MaterialTheme.typography.bodyMedium)
            }
            Text("×$qty", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { viewModel.updateExperimentQty(id, qty + 1) }) {
                Text("+", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { viewModel.removeExperimentIngredient(id) }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove")
            }
        }
    }

    if (experimentIngredients.size < 4) {
        val available = inventoryItems.filter {
            it.quantity > 0 && it.ingredientId !in experimentIngredients.keys
        }
        if (available.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            IngredientDropdown(available = available, viewModel = viewModel)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { viewModel.submitExperiment() },
        enabled = experimentIngredients.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Experiment — ingredients consumed")
    }

    lastResult?.let { result ->
        Spacer(modifier = Modifier.height(12.dp))
        ExperimentResultCard(result = result, onDismiss = { viewModel.clearExperimentResult() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientDropdown(available: List<InventoryItem>, viewModel: KitchenViewModel) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        ) {
            Text("+ Add ingredient")
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text("${viewModel.ingredientName(item.ingredientId)}  ×${item.quantity}")
                    },
                    onClick = {
                        viewModel.addExperimentIngredient(item.ingredientId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExperimentResultCard(result: ExperimentResult, onDismiss: () -> Unit) {
    val (title, body, isGood) = when (result) {
        is ExperimentResult.Discovered ->
            Triple("Discovered: ${result.recipe.name}", "You've found something new!", true)
        is ExperimentResult.AlreadyKnown ->
            Triple(result.recipe.name, "You already know this one.", false)
        is ExperimentResult.Failure -> when (result.proximity) {
            ProximityTier.NEAR_MISS ->
                Triple("Almost", "The balance is nearly right — one thing is off.", false)
            ProximityTier.CLOSE ->
                Triple("Close", "Something is missing from this combination.", false)
            ProximityTier.SOME ->
                Triple("Familiar", "A few of these feel right, but not together.", false)
            ProximityTier.NONE ->
                Triple("Nothing", "These ingredients don't belong together.", false)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGood) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun FoodHintsCard(expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Food Structures",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    listOf(
                        "Bread — a grain, a liquid, and a binder",
                        "Soup — a base liquid, a protein, and a vegetable",
                        "Roast — a meat, a fat, and a seasoning",
                        "Infusion — an herb and a base liquid",
                        "Stew — a meat or protein, root vegetables, and a broth"
                    ).forEach { hint ->
                        Text(
                            "· $hint",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
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
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!isLocked && canCook) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    recipe.flavorTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    viewModel: KitchenViewModel
) {
    val qtyMap = inventoryItems.associate { it.ingredientId to it.quantity }
    val buffAtLevel = (recipe.baseBuffStrength + (cookingLevel - 1) * recipe.buffStrengthPerLevel).toInt()
    val hps = buffAtLevel / 10f

    val effectLine = when {
        recipe.primaryStat != null -> {
            val statName = when (recipe.primaryStat) {
                "mig" -> "Might"; "agi" -> "Agility"
                "vit" -> "Vitality"; "wil" -> "Will"
                else -> recipe.primaryStat
            }
            "$statName +$buffAtLevel"
        }
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
            Text(effectLine, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "%.1f HP/s in combat".format(hps),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ingredients:", style = MaterialTheme.typography.labelMedium)
            recipe.ingredients.forEach { ing ->
                val have = qtyMap[ing.id] ?: 0
                val name = viewModel.ingredientName(ing.id)
                Text(
                    "• $name  $have/${ing.qty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (have >= ing.qty) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CookingActiveCard(recipeName: String, startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) { now = System.currentTimeMillis(); delay(1000L) }
    }
    val remainingMs = maxOf(0L, startedAtMs + durationMs - now)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Cooking in progress", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(recipeName, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(formatMs(remainingMs), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text("remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000; val m = total / 60; val s = total % 60
    return "%d:%02d".format(m, s)
}
```

- [ ] **Step 2: Build to verify**

```
JAVA_HOME=/opt/jetbrains-toolbox/jre ./gradlew assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL. If there are import errors, check that `AnimatedVisibility` is imported from `androidx.compose.animation` and `FlowRow` from `androidx.compose.foundation.layout`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt
git commit -m "[v1] KitchenScreen: Experiment mode, ingredient picker, result card, food hints"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] Recipes hidden until discovered — `tieredRecipes` filters to `discoveredIds`
- [x] Experiments consume ingredients on failure — `submitExperiment()` calls `removeIngredient` before evaluating
- [x] Exact match logic — `RecipeDiscoveryEngine.isExactMatch()` checks method + ingredient set + quantities
- [x] Proximity tiers (NEAR_MISS / CLOSE / SOME / NONE) — `proximityScore()` + result card
- [x] Ingredient categories hidden — engine works purely on IDs, never surfaces categories
- [x] Food type hints (bread, soup, etc.) — `FoodHintsCard`, shown at cookingLevel ≥ 3
- [x] Auto-discover on level-up — `CookingWorker.doWork()` compares old/new level
- [x] Cooked recipe is always discovered — `player.discoverRecipe(recipeId)` in `CookingWorker`
- [x] New-player seed (level-1 recipes) — `KitchenViewModel.init` block
- [x] `hasSeenFoodStructureHints` tracked in PlayerState — Task 1 + `markHintsSeen()`
- [x] Room v6→v7 migration — `AutoMigration(from = 6, to = 7)` with `@ColumnInfo(defaultValue)`
- [x] Mode toggle (Recipes | Experiment) — `TabRow` in `KitchenScreen`
- [x] `AlreadyKnown` distinct from `Discovered` — handled in engine and result card
- [x] `bandRecipes` also filtered to discovered — updated in `KitchenViewModel`

**Placeholder scan:** None found.

**Type consistency:**
- `ExperimentResult` sealed class defined in Task 2, consumed in Tasks 3 and 5 ✓
- `ProximityTier` enum defined in Task 2, used in Tasks 3 and 5 ✓
- `discoverRecipes(Collection<String>)` defined in Task 1, called in Tasks 3 and 4 ✓
- `observeDiscoveredIds(): Flow<Set<String>>` defined in Task 1, used in Task 3 ✓
- `markHintsSeen()` defined in Tasks 1 and 3, called in Task 5 ✓
