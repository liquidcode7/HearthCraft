# Audit Quick Fixes (04 Jul 2026 playtest) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the four confirmed, unambiguous bugs surfaced by Wes's 04 Jul 2026 playtest audit — the ones that don't depend on an open design decision. The other 13 audit items are logged in a triage table below but deliberately NOT planned here; they need a design call or content writing, not a bugfix.

**Architecture:** Four independent tasks touching four unrelated subsystems (Kitchen discovery UI, recipe data, Market seed catalogue, Gathering tab UI). No task depends on another — any order works, and each leaves a working build.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, kotlinx.serialization, Kotlin Coroutines (StateFlow, snapshotFlow)

## Global Constraints

- All Kotlin source lives under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- All game data JSON lives under `app/src/main/assets/data/`
- Commit messages prefixed `[hc]`
- Run `./gradlew build` before every commit — never commit a broken build (remember `export JAVA_HOME=/usr/share/pycharm/jbr` first — see `docs/journal.md` CLI build note)
- Do not bump `versionName`/`versionCode`
- No Room schema changes in this plan — `PlayerState.hasSeenExperimentHint` is left in place even though its last caller is removed in Task 1 (deleting a Room column requires a migration; a single unused boolean isn't worth that risk given `fallbackToDestructiveMigration(true)` is already flagged as a latent risk elsewhere)
- GPL-3.0

---

## Triage: all 17 audit items

| # | Audit item | Verdict | Where it's handled |
|---|-----------|---------|---------------------|
| 1 | Discovery mechanic still in Kitchen despite Grimoire replacing it | **Confirmed — fully live, not a remnant** | Task 1 |
| 2 | Inspiration descriptions inaccurate; Fighter inspiration should do true damage | Needs content pass + a design confirm (Fighter's Black Arrow/Bullroarer already burns boss resolve directly, bypassing armor — conceptually true-damage already; copy needs rewriting to say so) | **Not planned here** — separate content task |
| 3 | Damage types — is magic damage its own type? | **Confirmed not implemented** — only one physical-mitigation number exists engine-wide | **Not planned here** — real engine feature, needs its own design+plan |
| 4 | Combat timer makes losses feel pre-decided | **Confirmed** — `EncounterEngine.resolve()` computes the whole fight instantly; the on-screen timer only replays pre-computed `TickSnapshot`s | **Not planned here** — Wes's proposed fix (drop timer for kill fights, keep for survival fights) is a combat-feel design decision |
| 5 | Live in-combat DPS/heal/damage-type/buff visibility | Blocked on #3 (damage types) for the type-split part; the rest is a UI feature | **Not planned here** |
| 6a | Antidote recipes showing at initiate tier | **Not reproducible** — the T1 grimoire-bypass (`GameDataRepository.kt:45`) is class-blind by design (master-design.md §8.3: "T1 recipes never require a grimoire," no class exception stated), and no antidote-tagged recipe exists at T1 in current data | Need a repro (screenshot/recipe name) before this can be fixed |
| 6b | Recipe "titles" that get more potent per cook level within a tier | **Confirmed not implemented** — this is the deferred "Recipe Rank" spec (designed, never built) | **Not planned here** — revive the deferred spec if wanted |
| 6c | Ingredient filters (quality/type/quantity/alphabetical) in Pantry | Not implemented | **Not planned here** — UI feature |
| 7 | Post-fight recap needs more info | Partially superseded — Session 19/20 already added DPS bars, Keeper uptime, wound recap | Folded into #9 below |
| 8a | Too many ingredients vs. too few recipes | Already an open item in `docs/journal.md` ("gather/farm/garden yield balance") | Not planned here — existing open item |
| 8b | Dual-stat T1 recipes shouldn't exist before T2 | **Confirmed bug** — `hearthbread` and `wanderers_supper` both carry a secondary stat at tier 1 | Task 2 |
| 8c | Red>green quality-upgrade comparison indicator | Not implemented | **Not planned here** — UI feature |
| 9 | Heal bars + damage-by-type % (color-coded) on recap | Blocked on #3 (damage types) for the type-split part | **Not planned here** |
| 10 | Wound consequence/timer not visible anywhere | Real UI gap — §6.8 auto-heal timers (1hr/2hr) exist in the data model but aren't surfaced | **Not planned here** — UI feature |
| 11 | Prepared ingredients seem unused | **Partially confirmed** — `butter`, `salted_pork`, `smoked_river_trout` are used by ≥1 recipe; `hard_cheese`, `rendered_fat`, `deer_haunch_dried` are consumed by nothing yet | Content/recipe-authoring work, not a bug |
| 12 | Gathering tab swipe gets stuck mid-transition | **Confirmed mechanism** — dual `LaunchedEffect` ping-pong between `TabRow` and `HorizontalPager` (same bug also present in `KitchenScreen.kt`) | Task 1 (Kitchen) + Task 4 (Gathering) |
| 13 | Fight narrative is thin/bizarre in places | Content writing, not code | **Not planned here** |
| 14 | No visible loot/reward summary after a fight | Not implemented | **Not planned here** — UI feature |
| 15 | Can't garden/farm everything found (e.g. wanderer's fig) | **Confirmed bug** — `wanderer_fig` *is* correctly flagged `cultivatable: true` and reachable via random forage bonus, but the Market's seed catalogue is a hardcoded 5-item list, so it can never be bought/planted on purpose | Task 3 |
| 16 | Kitchen scroll jumps to top on recipe select; want collapsible/filterable tiers | Not implemented | **Not planned here** — UI feature |
| 17a | Gear system (weapons/armor) scope | Real open design question — not in master-design.md at all | **Not planned here** — needs a brainstorm with Wes |
| 17b | Grade/drop-rate progression too generous too early | `QualityUtils.kt`'s `GATHER_DISTRIBUTION` table already scales with gathering level but is explicitly a `TODO:TUNE` placeholder; there's also a second, currently-dead `QualityRoll.kt` with an `aidActive` parameter that looks like a stub for the previously-deferred "Gathering Aid" spec — **do not delete it**, it's parked work, not dead code | **Not planned here** — a tuning pass against the balance harness, not a bugfix |

---

### Task 1: Remove the Discover/experimentation mechanic from the Kitchen

**Context:** `KitchenViewModel.kt` and `KitchenScreen.kt` still have a fully-wired "Discover" tab — free-assembly ingredient experimentation via `RecipeDiscoveryEngine` — left over from before the 30 June 2026 redesign replaced recipe unlocking with the Grimoire system (master-design.md §8.3–8.4). Wes's audit note confirms this should be gone: *"completely dumped in favor of the grimoire mechanic."*

This is **not** the same code path as starter-recipe seeding. `PlayerRepository.discoverRecipe()` / `discoverRecipes()` and the `discoveredRecipeIds` column are also used by `KitchenViewModel`'s first-launch starter-recipe seeding (`init` block) and by `CookingWorker.kt:48` (marks a recipe known the first time it's cooked). **Keep those.** Only the interactive experiment UI/flow is being removed.

While in `KitchenScreen.kt`'s tab-switching code, this task also fixes the same stuck-mid-swipe bug reported in Gathering (Task 4) — both screens sync `TabRow` ↔ `HorizontalPager` the same broken way (a `LaunchedEffect(pagerState.currentPage)` that fires on every transient frame of the animation, not just once it settles).

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`
- Delete: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngine.kt`
- Delete: `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngineTest.kt`
- Delete: `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenExperimentTest.kt`

**Interfaces:** No other task depends on this one. `PlayerRepository.discoverRecipe()`/`discoverRecipes()`/`observeDiscoveredIds()` are unchanged and keep being used by starter-recipe seeding and `CookingWorker`.

- [ ] **Step 1: Delete the two test files that only test the removed mechanic**

```bash
git rm app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngineTest.kt
git rm app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenExperimentTest.kt
```

- [ ] **Step 2: Delete the engine file**

```bash
git rm app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngine.kt
```

- [ ] **Step 3: Strip the experiment state/functions out of `KitchenViewModel.kt`**

Remove these three imports (no longer used anywhere in the file):
```kotlin
import com.liquidcode7.hearthcraft.engine.ExperimentAttempt
import com.liquidcode7.hearthcraft.engine.ExperimentResult
import com.liquidcode7.hearthcraft.engine.RecipeDiscoveryEngine
```

Change the tab-index comment (Discover tab is gone, Prepare shifts from index 2 to index 1):
```kotlin
private val _selectedTab = MutableStateFlow(0)   // 0=Recipes 1=Prepare
```

Delete the "Experiment state" block entirely (`_experimentIngredients` through `experimentHintSeen`):
```kotlin
    private val _experimentIngredients = MutableStateFlow<Map<String, Int>>(emptyMap())
    val experimentIngredients: StateFlow<Map<String, Int>> = _experimentIngredients.asStateFlow()

    private val _experimentMethod = MutableStateFlow("cook")
    val experimentMethod: StateFlow<String> = _experimentMethod.asStateFlow()

    private val _lastExperimentResult = MutableStateFlow<ExperimentResult?>(null)
    val lastExperimentResult: StateFlow<ExperimentResult?> = _lastExperimentResult.asStateFlow()

    private val _liveResult = MutableStateFlow<ExperimentResult?>(null)
    val liveResult: StateFlow<ExperimentResult?> = _liveResult.asStateFlow()

    val canCommit: StateFlow<Boolean> = _liveResult.map { result ->
        result is ExperimentResult.Discovered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val experimentHintSeen: StateFlow<Boolean> = player.observe()
        .map { it?.hasSeenExperimentHint ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
```
(Leave `val hintsSeen: StateFlow<Boolean>` and `fun markHintsSeen()` — those back the *Recipes*-tab `FoodHintsCard`, unrelated to the Discover tab.)

Simplify `selectTab` — the special-case reset is no longer needed once there's no experiment state to clear:
```kotlin
    fun selectTab(index: Int) {
        _selectedTab.value = index
    }
```

Delete the whole "Tab and experiment functions" block except `selectTab` (already handled above) and `markHintsSeen`:
```kotlin
    fun addExperimentIngredient(id: String) { ... }
    fun removeExperimentIngredient(id: String) { ... }
    fun updateExperimentQty(id: String, qty: Int) { ... }
    fun setExperimentMethod(method: String) { ... }
    fun clearExperimentResult() { ... }
    fun evaluateLive() { ... }
    fun commitDiscovery() { ... }
    fun markExperimentHintSeen() { ... }
```
All eight of the above are deleted — none are called from anywhere once the Discover tab UI is gone.

- [ ] **Step 4: Strip the Discover tab out of `KitchenScreen.kt`**

Remove these two imports:
```kotlin
import com.liquidcode7.hearthcraft.engine.ExperimentResult
import com.liquidcode7.hearthcraft.engine.ProximityTier
```

Change the tab row from three tabs to two:
```kotlin
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { viewModel.selectTab(0) }, text = { Text("Recipes") })
                Tab(selected = selectedTab == 1, onClick = { viewModel.selectTab(1) }, text = { Text("Prepare") })
            }
```

Change the pager to 2 pages and fix the tab↔pager sync (the same `settledPage` fix as Task 4 — see that task for why):
```kotlin
        val pagerState = rememberPagerState(pageCount = { 2 })
        LaunchedEffect(selectedTab) { if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { settled -> if (settled != selectedTab) viewModel.selectTab(settled) }
        }
```
Add the missing import:
```kotlin
import androidx.compose.runtime.snapshotFlow
```

Renumber the `when (page)` block from three cases to two — delete the `1 -> { // Discover tab ... }` case entirely and renumber the old `2 -> { // Process tab ... }` to `1 ->`:
```kotlin
                    1 -> {
                        // Process tab
                        ProcessPanel(
                            viewModel = viewModel,
                            processSlot = processSlot,
                            processIngredients = processIngredients,
                            selectedProcessIngredient = selectedProcessIngredient,
                            inventoryItems = inventoryItems
                        )
                    }
```

Delete the three now-orphaned composables entirely — `ExperimentPanel`, `IngredientDropdown`, and `ExperimentResultCard` (everything from the `@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)` line immediately before `private fun ExperimentPanel` through the closing `}` of `ExperimentResultCard`, immediately before `private fun FoodHintsCard`). `FoodHintsCard` and everything after it is untouched.

Remove these now-unused imports (verify with grep first — `FilterChip`, `OutlinedButton`, and `Arrangement` are still used elsewhere in the file and must stay):
```kotlin
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
```

Also delete the now-stale hint string still referencing the Discover tab:
```kotlin
"No recipes discovered yet. Head to the Discover tab to find them.",
```
Replace with a string that doesn't promise a tab that no longer exists — since recipe unlocking is now Grimoire-driven, not experimentation-driven:
```kotlin
"No recipes unlocked yet. Find a Grimoire to unlock the next tier.",
```

- [ ] **Step 5: Build and verify**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew build
```
Expected: BUILD SUCCESSFUL, no unresolved references, no unused-import warnings for the six imports removed above.

- [ ] **Step 6: Manual verification (Compose UI — no automated test infra for this in the repo)**

Launch the app, open Kitchen. Confirm: only "Recipes" and "Prepare" tabs exist. Confirm cooking still works end-to-end (select recipe → cook → collect). Confirm rapidly tapping between the two tabs several times in a row never leaves the pager visually stuck between them.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
[hc] Remove leftover Discover/experimentation mechanic from Kitchen

Grimoire fully replaced recipe-unlock-via-experimentation in the 30 June
redesign, but the old free-assembly Discover tab was never torn out and
was still fully wired (audit finding, 04 Jul 2026). Starter-recipe
seeding and CookingWorker's discover-on-first-cook are untouched — they
share storage with the old mechanic but are a different feature.

Also fixes the Kitchen tab/pager desync bug (settledPage instead of
currentPage for the reverse sync), same root cause as the Gathering tab
fix in this same audit pass.
EOF
)"
```

---

### Task 2: Fix dual-stat T1 starter recipes

**Context:** master-design.md §8.2 is explicit: "T1: 1 stat coverage." `hearthbread` and `wanderers_supper` are both tier-1 Greycloaks starter recipes (part of the `universals` list seeded on first launch — `KitchenViewModel.kt` init block) but each currently has a `secondaryStat`/`secondaryBoost`, violating the rule. Fix: strip the secondary stat from both — they stay tier 1 (they're core starter food, must remain grimoire-free per §8.3), just single-stat as T1 requires.

`tools/sim/food_model.js` hand-mirrors `recipes.json` for the JS balance sim and must be kept in sync or the sim silently diverges from the real game data.

**Files:**
- Modify: `app/src/main/assets/data/recipes.json`
- Modify: `tools/sim/food_model.js`

**Interfaces:** No other task depends on this one.

- [ ] **Step 1: Fix `recipes.json`**

Find the `hearthbread` entry and remove its secondary stat:
```json
  "primaryStat": "vit",
  "primaryBoost": 2,
  "secondaryStat": "mig",
  "secondaryBoost": 1
```
becomes:
```json
  "primaryStat": "vit",
  "primaryBoost": 2
```

Find the `wanderers_supper` entry and do the same:
```json
  "primaryStat": "agi",
  "primaryBoost": 2,
  "secondaryStat": "mig",
  "secondaryBoost": 1
```
becomes:
```json
  "primaryStat": "agi",
  "primaryBoost": 2
```

- [ ] **Step 2: Mirror the same change in `tools/sim/food_model.js`**

Line 28-29 currently read:
```js
    { id:"hearthbread",            name:"Hearthbread",              cookLevel:1,  primaryStat:"vit", primaryBoost:2,  secondaryStat:"mig", secondaryBoost:1 },
    { id:"wanderers_supper",       name:"Wanderer's Supper",        cookLevel:1,  primaryStat:"agi", primaryBoost:2,  secondaryStat:"mig", secondaryBoost:1 },
```
Change to:
```js
    { id:"hearthbread",            name:"Hearthbread",              cookLevel:1,  primaryStat:"vit", primaryBoost:2 },
    { id:"wanderers_supper",       name:"Wanderer's Supper",        cookLevel:1,  primaryStat:"agi", primaryBoost:2 },
```

- [ ] **Step 3: Build and verify**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew test --tests "*.RecipeStatTest" --tests "*.GameDataRepository*"
```
Expected: PASS (no test asserted a secondary stat on these two recipes specifically — confirm by reading test output, not just exit code).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/data/recipes.json tools/sim/food_model.js
git commit -m "$(cat <<'EOF'
[hc] Fix dual-stat T1 starter recipes (hearthbread, wanderers_supper)

master-design.md §8.2: T1 recipes cover exactly one stat. Both starter
recipes had picked up a secondary stat somewhere along the way (audit
finding, 04 Jul 2026). Stripped the secondary — both stay tier 1 (they
must remain grimoire-free starter food per §8.3), just single-stat.
Mirrored in tools/sim/food_model.js to keep the JS balance sim in sync.
EOF
)"
```

---

### Task 3: Replace the Market's hardcoded seed catalogue with the player's actual discovered-ingredient set

**Context:** master-design.md §10.2: "All plants are farmable once acquired... If you find sloe in the wild, you now have sloe-stock to cultivate." The data already supports this correctly — `wanderer_fig` has `cultivatable: true` in `ingredients.json`, and `GatheringWorker.kt` already grants cultivatable bonus seeds dynamically off real harvests. `PlayerRepository` already persistently tracks every ingredient the player has ever pulled from a harvest, in `discoveredIngredientIds` (`GatheringViewModel.kt:264` calls `player.discoverIngredients(newIds)` on every harvest; `GatheringViewModel.kt:132` already reads it back via `observeDiscoveredIngredientIds()` to drive the Wild-forage target picker).

The one place this breaks down is `MarketViewModel.kt`'s `SEED_CATALOGUE` — a hardcoded list of exactly 5 seed IDs, against 97 ingredients flagged `cultivatable: true` in the game data. `wanderer_fig` isn't one of the 5, so a player who has found it in the wild has no way to deliberately buy/plant it — exactly Wes's audit complaint.

Fix: derive the seed list from `gameData.ingredients` filtered to `cultivatable && id in discoveredIngredientIds`, the same pattern `GatheringViewModel.foragableIngredients` already uses. Extract the filter into a plain top-level function so it's unit-testable without Hilt/Robolectric — this repo has no existing pattern for testing ViewModels directly (all current unit tests target pure functions/repositories/engines), so business logic belongs in a function, not inline in the `StateFlow` builder.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/MarketViewModel.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/MarketSeedsTest.kt` (create)

**Interfaces:**
- Produces: `availableSeeds(ingredients: List<Ingredient>, discoveredIds: Set<String>): List<Pair<String, String>>` — top-level function in `MarketViewModel.kt`, returns `(seedId, displayName)` pairs, seedId format `"${ingredient.id}_seed"`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/MarketSeedsTest.kt`:
```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.data.model.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketSeedsTest {

    private fun ingredient(id: String, name: String, cultivatable: Boolean) = Ingredient(
        id = id,
        name = name,
        cultivatable = cultivatable
    )

    @Test
    fun `only cultivatable and discovered ingredients become seeds for sale`() {
        val ingredients = listOf(
            ingredient("wanderer_fig", "Wanderer Fig", cultivatable = true),   // discovered, cultivatable -> included
            ingredient("athelas", "Athelas", cultivatable = true),            // cultivatable but not discovered -> excluded
            ingredient("river_trout", "River Trout", cultivatable = false)    // discovered but not cultivatable -> excluded
        )
        val discoveredIds = setOf("wanderer_fig", "river_trout")

        val result = availableSeeds(ingredients, discoveredIds)

        assertEquals(listOf("wanderer_fig_seed" to "Wanderer Fig Seed"), result)
    }

    @Test
    fun `results are sorted by ingredient name`() {
        val ingredients = listOf(
            ingredient("z_herb", "Zinnia Herb", cultivatable = true),
            ingredient("a_herb", "Ashroot", cultivatable = true)
        )
        val discoveredIds = setOf("z_herb", "a_herb")

        val result = availableSeeds(ingredients, discoveredIds)

        assertEquals(listOf("a_herb_seed" to "Ashroot Seed", "z_herb_seed" to "Zinnia Herb Seed"), result)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (function doesn't exist yet)**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew test --tests "*.MarketSeedsTest"
```
Expected: compilation failure, `availableSeeds` is unresolved.

- [ ] **Step 3: Implement — replace the hardcoded catalogue in `MarketViewModel.kt`**

Full new file:
```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeedForSale(
    val seedId: String,
    val name: String,
    val priceGold: Int,
    val ownedQty: Int
)

fun availableSeeds(ingredients: List<Ingredient>, discoveredIds: Set<String>): List<Pair<String, String>> =
    ingredients
        .filter { it.cultivatable && it.id in discoveredIds }
        .sortedBy { it.name }
        .map { "${it.id}_seed" to "${it.name} Seed" }

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val inventory: InventoryRepository,
    private val gameData: GameDataRepository
) : ViewModel() {

    val gold: StateFlow<Int> = player.observe()
        .map { it?.money ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val seedsForSale: StateFlow<List<SeedForSale>> = combine(
        inventory.observeSeeds(),
        player.observeDiscoveredIngredientIds()
    ) { stocks, discoveredIds ->
        val stockMap = stocks.associate { it.seedId to it.quantity }
        availableSeeds(gameData.ingredients, discoveredIds).map { (seedId, name) ->
            SeedForSale(
                seedId = seedId,
                name = name,
                priceGold = SEED_PRICE_GOLD,
                ownedQty = stockMap[seedId] ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun buySeed(seedId: String) {
        viewModelScope.launch {
            val purchased = player.spendMoney(SEED_PRICE_GOLD)
            if (purchased) {
                inventory.addSeed(seedId, 1)
            }
        }
    }

    companion object {
        const val SEED_PRICE_GOLD = 5
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew test --tests "*.MarketSeedsTest"
```
Expected: both tests PASS.

- [ ] **Step 5: Full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL. `MarketViewModel` now requires `GameDataRepository` in its constructor — Hilt resolves this automatically (it's already an `@Singleton` bound in the DI graph for other ViewModels), no manual wiring needed. Confirm no other call site constructs `MarketViewModel` directly (it shouldn't — it's `@HiltViewModel`, always obtained via `hiltViewModel()`).

- [ ] **Step 6: Manual verification**

Launch the app, forage or gather any single cultivatable ingredient not in the old 5-item list (e.g. wanderer_fig, if reachable). Open Market. Confirm its seed now appears for sale. Confirm the original 5 seeds still appear once their ingredients are discovered (they should already be discovered on a save that's played a few sessions — if testing fresh, forage/harvest each first).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/MarketViewModel.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/MarketSeedsTest.kt
git commit -m "$(cat <<'EOF'
[hc] Market seed catalogue now reflects actually-discovered ingredients

MarketViewModel had a hardcoded 5-seed SEED_CATALOGUE against 97
cultivatable ingredients in the data. wanderer_fig (audit finding, 04
Jul 2026) is a real example: cultivatable and obtainable via forage
bonus, but never purchasable/plantable on purpose. Fixed by deriving
the seed list from GameDataRepository.ingredients filtered against
PlayerRepository.observeDiscoveredIngredientIds() — the same
persistent "ever acquired" set GatheringViewModel.foragableIngredients
already uses, matching master-design.md §10.2 ("all plants are
farmable once acquired").
EOF
)"
```

---

### Task 4: Fix the Gathering tab/pager stuck-mid-swipe glitch

**Context:** `GatheringScreen.kt` syncs its `TabRow` and `HorizontalPager` with two `LaunchedEffect`s pointed at each other:
```kotlin
LaunchedEffect(subTab) { if (pagerState.currentPage != subTab) pagerState.animateScrollToPage(subTab) }
LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage != subTab) viewModel.selectGatherSubTab(pagerState.currentPage) }
```
`pagerState.currentPage` updates continuously *during* a scroll/animation, not just once it settles — so the second effect re-triggers on every intermediate frame while `animateScrollToPage` is still running, and if the player switches tabs again before that animation finishes, the `LaunchedEffect(subTab)` coroutine restarts (cancelling the in-flight animation abruptly) while the pager is mid-transition. This is the reported "sliding gets stuck halfway between tabs" bug (audit item, 04 Jul 2026).

Fix: sync the reverse direction off `pagerState.settledPage` (only changes once a transition/animation/drag has fully settled) instead of `pagerState.currentPage`, using `snapshotFlow` so the collector isn't re-launched on every intermediate frame.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GatheringScreen.kt`

**Interfaces:** No other task depends on this one. (Task 1 applies the identical fix independently to `KitchenScreen.kt`'s copy of the same bug.)

- [ ] **Step 1: Add the import**

```kotlin
import androidx.compose.runtime.snapshotFlow
```

- [ ] **Step 2: Replace the two sync effects**

Current:
```kotlin
    val pagerState = rememberPagerState(pageCount = { 3 })
    LaunchedEffect(subTab) { if (pagerState.currentPage != subTab) pagerState.animateScrollToPage(subTab) }
    LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage != subTab) viewModel.selectGatherSubTab(pagerState.currentPage) }
```
Becomes:
```kotlin
    val pagerState = rememberPagerState(pageCount = { 3 })
    LaunchedEffect(subTab) { if (pagerState.currentPage != subTab) pagerState.animateScrollToPage(subTab) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { settled -> if (settled != subTab) viewModel.selectGatherSubTab(settled) }
    }
```
`LaunchedEffect(pagerState)` keys on the (stable, never-changing) `pagerState` object itself rather than its `currentPage` property, so this coroutine is launched exactly once and just keeps collecting — it no longer restarts every time the page changes, which is what let a rapid tab-click race the in-flight animation before.

- [ ] **Step 3: Build**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification (Compose UI gesture bug — no automated test infra in this repo for this)**

Launch the app, open Gathering. Rapidly tap between Growing/Wild/Producers several times in quick succession, and separately try swiping quickly. Confirm the pager never freezes visually between two tabs and the selected tab indicator always matches what's on screen.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GatheringScreen.kt
git commit -m "$(cat <<'EOF'
[hc] Fix Gathering tab/pager getting stuck mid-swipe

Two LaunchedEffects were syncing TabRow <-> HorizontalPager off
pagerState.currentPage, which changes on every intermediate frame of
an animation/drag, not just once it settles. Rapid tab-switching could
cancel an in-flight animateScrollToPage mid-transition, leaving the
pager visually stuck between pages (audit finding, 04 Jul 2026). Fixed
by driving the reverse sync off pagerState.settledPage via
snapshotFlow, keyed on the stable pagerState object so the collector
isn't relaunched on every frame.
EOF
)"
```

---

## What's deliberately not in this plan

See the triage table above. In particular, before any further audit items become plannable:

- **Combat timer removal** and **damage-type system** are real combat-feel/engine design decisions — worth a `superpowers:brainstorming` pass with Wes before writing a plan, not something to decide unilaterally.
- **Gear system scope** is an open design question not covered anywhere in `design/master-design.md` — needs Wes's call on scope (a weapon+cloak minimal version vs. full itemization) before any implementation plan makes sense.
- Everything else in the triage table is either a content-writing task (narrative copy, Inspiration descriptions) or a straightforward-but-unplanned UI feature (wound timers, post-fight loot summary, ingredient filters, kitchen scroll/collapse behavior) that can be planned separately once prioritized.

---

## Roadmap: the 7 sub-project groups (ordered, with status)

Wes approved this decomposition/order on 04 Jul 2026. Each group gets its own
brainstorm → spec → plan → subagent-driven-development cycle, in this order:

1. **Combat feel & feedback** — DONE (05 Jul 2026). Spec:
   `docs/superpowers/specs/2026-07-04-combat-feel-and-feedback-design.md`. Plan:
   `docs/superpowers/plans/2026-07-05-combat-feel-and-feedback.md`. Shipped: kill-fight
   timer hidden, wound recovery durations corrected (18min/30min/2hr) + visible on HoH
   tab, post-fight rewards summary. Merged to main.
2. **Damage types (engine)** — DONE (05 Jul 2026). Spec:
   `docs/superpowers/specs/2026-07-05-damage-types-design.md`. Plan:
   `docs/superpowers/plans/2026-07-05-damage-types.md`. Shipped: magic damage (Keeper
   full, Captain stat-weighted) now bypasses armor instead of being mitigated like
   physical; named magic-type taxonomy (Light/Westernesse) and enemy-category tags
   (orc/dragon/shadow/wraith/nature) documented/tagged for the future gear/bane system,
   both inert until weapons exist. Merged to main.
3. **Live combat visibility** — DONE (05 Jul 2026). Spec:
   `docs/superpowers/specs/2026-07-05-live-combat-visibility-design.md`. Plan:
   `docs/superpowers/plans/2026-07-05-live-combat-visibility.md`. Shipped: live
   cumulative DPS/heal bars on the in-progress fight card, colored by the engine's real
   physical/magical split; shield outline, STREAK/HOT badges per member; a banner for
   the four inspiration effects (Horn of Gondor, Red Dawn, Black Arrow, Grace). Merged
   to main.
4. **Content pass** — DONE (06 Jul 2026). Spec:
   `docs/superpowers/specs/2026-07-06-content-pass-design.md`. Plan:
   `docs/superpowers/plans/2026-07-06-content-pass.md`. Shipped: Journal's per-role
   ability cards rewritten (3 of 4 previously described the wrong mechanic entirely),
   Tolkien-lore-flavored, Fighter branching on ranged/melee build; two stale Inspiration
   numbers corrected in master-design.md §6.3 (Horn duration, Red Dawn heal/DPS/duration);
   post-fight recap narrative replaced with outcome-bucketed text (no more encounter name
   used as a nonsensical grammatical subject). Merged to main.
5. **Kitchen/Pantry UX** — DONE (09 Jul 2026). Spec:
   `docs/superpowers/specs/2026-07-09-kitchen-pantry-ux-design.md`. Plan:
   `docs/superpowers/plans/2026-07-09-kitchen-pantry-ux.md`. Shipped: Kitchen Recipes tab
   got a sticky detail panel (replacing the old scroll-jump-to-top bug), collapsible
   tiers with a quick-jump row, and combinable cookable/class/stat filter chips plus a
   within-tier sort selector; Pantry got grade/stat filter chips and a sort selector for
   ingredients via a new dedicated `PantryViewModel` (kept isolated from the shared
   `InventoryViewModel`). Merged to main.
6. **Economy pass** — NOT STARTED. Recipe Rank (recipes get more potent per cook level
   within a tier — deferred spec already exists per memory), the too-many-ingredients-
   too-few-recipes imbalance, prepared-ingredients sitting unused, drop-rate curve tuning
   (`QualityUtils.kt`'s `GATHER_DISTRIBUTION`, currently marked `TODO:TUNE`). Use the
   recipe-browser tool (`tools/recipe-browser/`) to help diagnose the ingredient/recipe
   imbalance.
7. **Gear system** — NOT STARTED, deliberately last. Needs its own dedicated brainstorm
   session, not folded into any other group — Wes has explicitly said he doesn't yet
   know the scope he wants (minimal weapon+cloak vs. full itemization). This is also
   what will finally activate the bane-affinity system tagged inert in group #2.

**Also still open from before this audit** (not part of the 7 groups, longstanding):
bio-stage trigger logic, `grievousWoundSpecs` population on encounters, Lone-Lands
region unlock trigger, balance sim vs. Men encounters not yet run. And: `contemplative_tea`
(Mithlost) has the same T1 dual-stat issue fixed in group #1 — deferred to the eventual
Elves redesign, not touched.
