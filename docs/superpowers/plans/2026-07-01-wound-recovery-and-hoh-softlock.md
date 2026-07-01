# Wound Recovery & HoH Softlock Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the dead wound-treatment code path, make ordinary wounds auto-heal on a timer, remove death-by-combat-wounds, and add a softlock-safe fallback so a band can never get permanently stuck with an untreatable grievous wound before Houses of Healing (HoH) content exists.

**Architecture:** Bottom-up: data model → repositories → worker logic → ViewModel/UI → game data → docs. `EncounterWorker` gains a pure, directly-testable function (`resolveWoundOutcome`) that decides severity + recovery duration; a new `WoundRecoveryWorker` (same WorkManager pattern as `HiveWorker`/`CookingWorker`) auto-clears the wound after its duration elapses.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, Hilt, kotlinx.serialization, JUnit

**Spec:** `docs/superpowers/specs/2026-07-01-wound-recovery-and-hoh-softlock-design.md`

## Global Constraints

- All Kotlin source under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- Commit prefix: `[hc]`
- Build must pass: `./gradlew build` before every commit — never commit a broken build
- No new libraries — use existing dependencies only
- GPL-3.0
- Recovery durations are set to **15 seconds** for all three bands for Wes's on-device testing right now (not the target production values — see Task 5). Do not "fix" these back to hour-scale values as part of this plan; that's a deliberate, temporary choice.

---

### Task 1: Move `isRecipeVisible` to `GameDataRepository`, add `hasVisibleRecipeOfClass`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt:40-46,98,115`
- Modify: `app/src/test/kotlin/com/liquidcode7/hearthcraft/RecipeAvailabilityTest.kt:4`

**Interfaces:**
- Produces: `isRecipeVisible(recipe: Recipe, foundGrimoires: Set<String>, discoveredIds: Set<String>): Boolean` (moved, same signature) and `hasVisibleRecipeOfClass(recipes: List<Recipe>, recipeClass: String, foundGrimoires: Set<String>, discoveredIds: Set<String>): Boolean` — both top-level functions in `com.liquidcode7.hearthcraft.data.repository`.
- Consumed by: Task 5 (`EncounterWorker` uses `hasVisibleRecipeOfClass` to check HoH availability).

Reason for the move: `EncounterWorker` (a `Worker`, not a `ViewModel`) needs this logic in Task 5, and a `Worker` importing from `ui.viewmodel` would be a layering violation.

- [ ] **Step 1: Add the two functions to `GameDataRepository.kt`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt` first.

Insert this immediately after the closing `}` of the `GameDataRepository` class (before the `StarterItem` data class):

```kotlin
fun isRecipeVisible(recipe: Recipe, foundGrimoires: Set<String>, discoveredIds: Set<String>): Boolean {
    if (recipe.id in discoveredIds) return true
    if (recipe.tier == 1 && recipe.recipeClass != "hoh") return true
    // "food" recipes are gated by "cooking" grimoires; all other classes match 1:1.
    val grimoireClass = if (recipe.recipeClass == "food") "cooking" else recipe.recipeClass
    return "${grimoireClass}_t${recipe.tier}" in foundGrimoires
}

fun hasVisibleRecipeOfClass(
    recipes: List<Recipe>,
    recipeClass: String,
    foundGrimoires: Set<String>,
    discoveredIds: Set<String>
): Boolean = recipes.any { it.recipeClass == recipeClass && isRecipeVisible(it, foundGrimoires, discoveredIds) }
```

- [ ] **Step 2: Remove the old definition from `KitchenViewModel.kt` and update call sites**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt` first.

Delete lines 40-46 (the `fun isRecipeVisible(...) { ... }` block and its blank line after).

Add this import alongside the existing `GameDataRepository` import (near line 16):
```kotlin
import com.liquidcode7.hearthcraft.data.repository.isRecipeVisible
```

No other changes needed — the two call sites (`isRecipeVisible(it, foundGrimoires, discovered)` at what were lines 98 and 115) resolve via the new import automatically since the function name and signature are unchanged.

- [ ] **Step 3: Update the test import**

Read `app/src/test/kotlin/com/liquidcode7/hearthcraft/RecipeAvailabilityTest.kt` first.

Replace line 4:
```kotlin
import com.liquidcode7.hearthcraft.ui.viewmodel.isRecipeVisible
```
with:
```kotlin
import com.liquidcode7.hearthcraft.data.repository.isRecipeVisible
```

- [ ] **Step 4: Run the existing test to confirm no regression**

```bash
./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.RecipeAvailabilityTest"
```
Expected: all 8 existing tests still PASS (pure relocation, no behavior change).

- [ ] **Step 5: Write failing tests for the new `hasVisibleRecipeOfClass` function**

Append to `app/src/test/kotlin/com/liquidcode7/hearthcraft/RecipeAvailabilityTest.kt`, just before the final closing `}` of the class:

```kotlin

    // --- hasVisibleRecipeOfClass: used by the HoH-availability softlock check ---

    @Test
    fun `hasVisibleRecipeOfClass is false when no recipes of that class exist`() {
        val recipes = listOf(recipe("stew_basic", tier = 1, recipeClass = "food"))
        assertFalse(com.liquidcode7.hearthcraft.data.repository.hasVisibleRecipeOfClass(
            recipes, "hoh", emptySet(), emptySet()
        ))
    }

    @Test
    fun `hasVisibleRecipeOfClass is false when hoh recipe exists but is not visible`() {
        val recipes = listOf(recipe("athelas_prep", tier = 1, recipeClass = "hoh"))
        assertFalse(com.liquidcode7.hearthcraft.data.repository.hasVisibleRecipeOfClass(
            recipes, "hoh", emptySet(), emptySet()
        ))
    }

    @Test
    fun `hasVisibleRecipeOfClass is true once a hoh recipe is visible via grimoire`() {
        val recipes = listOf(recipe("athelas_prep", tier = 1, recipeClass = "hoh"))
        assertTrue(com.liquidcode7.hearthcraft.data.repository.hasVisibleRecipeOfClass(
            recipes, "hoh", setOf("hoh_t1"), emptySet()
        ))
    }
```

- [ ] **Step 6: Run to verify the new tests pass**

```bash
./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.RecipeAvailabilityTest"
```
Expected: 11 tests, all PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt
git add app/src/test/kotlin/com/liquidcode7/hearthcraft/RecipeAvailabilityTest.kt
git commit -m "[hc] Move isRecipeVisible to GameDataRepository; add hasVisibleRecipeOfClass"
```

---

### Task 2: One-shot suspend getters on `PlayerRepository`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt`
- Modify: `app/src/test/kotlin/com/liquidcode7/hearthcraft/PlayerRepositoryGrimoireTest.kt`

**Interfaces:**
- Produces: `PlayerRepository.getFoundGrimoireIds(): Set<String>` and `PlayerRepository.getDiscoveredRecipeIds(): Set<String>` — one-shot suspend reads (mirrors the existing `getDiscoveredIngredientIds()` pattern at line 82-85).
- Consumed by: Task 5 (`EncounterWorker.doWork()` needs single reads, not `Flow`s, inside a one-shot worker execution).

- [ ] **Step 1: Write failing tests**

Read `app/src/test/kotlin/com/liquidcode7/hearthcraft/PlayerRepositoryGrimoireTest.kt` first.

Add these two tests inside the `PlayerRepositoryGrimoireTest` class, just before its closing `}`:

```kotlin

    @Test
    fun `getFoundGrimoireIds returns current ids as a one-shot read`() = runBlocking {
        fakeDao.state = PlayerState(id = 0, foundGrimoireIds = "cooking_t2,hoh_t1")
        val ids = repo.getFoundGrimoireIds()
        assertEquals(setOf("cooking_t2", "hoh_t1"), ids)
    }

    @Test
    fun `getDiscoveredRecipeIds returns current ids as a one-shot read`() = runBlocking {
        fakeDao.state = PlayerState(id = 0, discoveredRecipeIds = "potency_draught")
        val ids = repo.getDiscoveredRecipeIds()
        assertEquals(setOf("potency_draught"), ids)
    }
```

- [ ] **Step 2: Run to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.PlayerRepositoryGrimoireTest"
```
Expected: FAIL with "unresolved reference: getFoundGrimoireIds" / "getDiscoveredRecipeIds".

- [ ] **Step 3: Implement the two getters**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt` first.

Add this directly after `observeDiscoveredIds()` (after line 55, before `discoverRecipe`):

```kotlin
    suspend fun getDiscoveredRecipeIds(): Set<String> {
        val state = dao.get() ?: return emptySet()
        return state.discoveredRecipeIds.split(",").filter { it.isNotBlank() }.toSet()
    }
```

Add this directly after `observeFoundGrimoireIds()` (after line 103, before `discoverGrimoire`):

```kotlin
    suspend fun getFoundGrimoireIds(): Set<String> {
        val state = dao.get() ?: return emptySet()
        return state.foundGrimoireIds.split(",").filter { it.isNotBlank() }.toSet()
    }
```

- [ ] **Step 4: Run to verify tests pass**

```bash
./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.PlayerRepositoryGrimoireTest"
```
Expected: 7 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt
git add app/src/test/kotlin/com/liquidcode7/hearthcraft/PlayerRepositoryGrimoireTest.kt
git commit -m "[hc] Add one-shot getFoundGrimoireIds/getDiscoveredRecipeIds to PlayerRepository"
```

---

### Task 3: `woundedDurationMs` schema field + Room migration

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/BandMemberState.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/BandMemberStateDao.kt:28-29`

**Interfaces:**
- Produces: `BandMemberState.woundedDurationMs: Long` (default `0L`) — read by Task 6/7 for the recovery countdown UI, written by Task 4.

- [ ] **Step 1: Add the field to `BandMemberState`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/BandMemberState.kt` first.

Replace the whole file:

```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "band_member_state")
data class BandMemberState(
    @PrimaryKey val memberId: String,
    val isAlive: Boolean = true,
    val woundStatus: String = "healthy",
    val woundedSinceMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val woundedDurationMs: Long = 0L,
    val might: Int = 0,
    val agility: Int = 0,
    val vitality: Int = 0,
    val will: Int = 0,
    val fate: Int = 0
)
```

- [ ] **Step 2: Bump the database version and add the AutoMigration**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt` first.

Change `version = 12` to `version = 13`.

Add `AutoMigration(from = 12, to = 13),` as the last entry in the `autoMigrations` list (after `AutoMigration(from = 11, to = 12),`).

- [ ] **Step 3: Update the `healWound` query to also clear the duration**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/BandMemberStateDao.kt` first.

Replace:
```kotlin
    @Query("UPDATE band_member_state SET woundStatus = 'healthy', woundedSinceMs = 0 WHERE memberId = :id")
    suspend fun healWound(id: String)
```
with:
```kotlin
    @Query("UPDATE band_member_state SET woundStatus = 'healthy', woundedSinceMs = 0, woundedDurationMs = 0 WHERE memberId = :id")
    suspend fun healWound(id: String)
```

- [ ] **Step 4: Build to generate and validate the new schema**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL. This generates `app/schemas/com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase/13.json` — confirm it exists:
```bash
ls app/schemas/com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase/13.json
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/BandMemberState.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/BandMemberStateDao.kt
git add "app/schemas/com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase/13.json"
git commit -m "[hc] Add woundedDurationMs field; DB schema 12->13"
```

---

### Task 4: `BandRepository.woundMember` gains a duration parameter

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt:41-45`

**Interfaces:**
- Consumes: `BandMemberState` (Task 3), `BandMemberStateDao` (existing).
- Produces: `BandRepository.woundMember(memberId: String, grievous: Boolean, durationMs: Long)` — signature change (was `woundMember(memberId: String, grievous: Boolean)`). Consumed by Task 5.

**No automated unit test for this task.** `BandRepository`'s constructor requires a real `GameDataRepository`, which itself requires an Android `Context` — this codebase has no Mockito or Robolectric (checked: `app/build.gradle.kts` only has plain JUnit4 for unit tests), and adding one would violate this plan's own "no new libraries" constraint for a single trivial mock. This matches an existing documented limitation in this codebase (see `EncounterWorkerGrimoireTest`'s note about `EncounterWorker` needing Android context). This task's correctness is verified by the full build (Step 3) and the on-device check in Task 10.

- [ ] **Step 1: Update `BandRepository.woundMember`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt` first.

Replace:
```kotlin
    suspend fun woundMember(memberId: String, grievous: Boolean) {
        val existing = dao.get(memberId) ?: BandMemberState(memberId = memberId)
        val status = if (grievous) "grievously_wounded" else "wounded"
        dao.upsert(existing.copy(woundStatus = status, woundedSinceMs = System.currentTimeMillis()))
    }
```
with:
```kotlin
    suspend fun woundMember(memberId: String, grievous: Boolean, durationMs: Long) {
        val existing = dao.get(memberId) ?: BandMemberState(memberId = memberId)
        val status = if (grievous) "grievously_wounded" else "wounded"
        dao.upsert(existing.copy(
            woundStatus = status,
            woundedSinceMs = System.currentTimeMillis(),
            woundedDurationMs = durationMs
        ))
    }
```

- [ ] **Step 2: Build**

```bash
./gradlew build
```
Expected: BUILD FAILS — `EncounterWorker.kt` still calls the old two-argument `band.woundMember(memberId, grievous = ...)`. This is expected; Task 5 fixes the only caller. Confirm the failure is specifically an unresolved-reference/argument-count error on `woundMember`, not anything else.

- [ ] **Step 3: Commit anyway is NOT appropriate here — hold this change uncommitted**

Do not commit yet. Proceed directly to Task 5, which updates the only caller. Task 5's build step will confirm both changes together compile, and Task 5's commit steps will include this file too.

---

### Task 5: Wound severity/duration resolution + `WoundRecoveryWorker`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/WoundRecoveryWorker.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WoundResolutionTest.kt` (new)

**Interfaces:**
- Consumes: `hasVisibleRecipeOfClass` (Task 1), `PlayerRepository.getFoundGrimoireIds()`/`getDiscoveredRecipeIds()` (Task 2), `BandRepository.woundMember(memberId, grievous, durationMs)` (Task 4).
- Produces: `WoundOutcome(grievous: Boolean, durationMs: Long)` data class and `resolveWoundOutcome(wounds: Int, hohAvailable: Boolean): WoundOutcome?` — pure top-level functions in `com.liquidcode7.hearthcraft.worker`, both in `EncounterWorker.kt`. `WoundRecoveryWorker.buildRequest(memberId: String, durationMs: Long): OneTimeWorkRequest`.

This is the core of the design: death-by-wounds removed, three severity/duration bands, and the HoH-availability check.

**Note on TDD flow for this task:** Task 4 left the module in a non-compiling state (`BandRepository.woundMember` now requires 3 args; `EncounterWorker` still calls it with 2). Because of that, a strict red-green-per-file cycle isn't possible here — the compiler error spans two files. Steps 1-4 below make all the necessary changes together; Step 5 is the first point at which the module compiles again, and Step 6 is the first point the new test can actually run.

- [ ] **Step 1: Write the test for `resolveWoundOutcome` (cannot run yet — module doesn't compile until Step 4)**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WoundResolutionTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.worker

import org.junit.Assert.*
import org.junit.Test

class WoundResolutionTest {

    @Test
    fun `0 wounds resolves to no outcome`() {
        assertNull(resolveWoundOutcome(wounds = 0, hohAvailable = false))
        assertNull(resolveWoundOutcome(wounds = 0, hohAvailable = true))
    }

    @Test
    fun `1-2 wounds is light wounded regardless of HoH availability`() {
        val a = resolveWoundOutcome(wounds = 1, hohAvailable = false)!!
        val b = resolveWoundOutcome(wounds = 2, hohAvailable = true)!!
        assertFalse(a.grievous)
        assertFalse(b.grievous)
        assertEquals(a.durationMs, b.durationMs)
    }

    @Test
    fun `3-4 wounds is heavy wounded with a longer duration than light`() {
        val light = resolveWoundOutcome(wounds = 1, hohAvailable = false)!!
        val heavy = resolveWoundOutcome(wounds = 3, hohAvailable = false)!!
        assertFalse(heavy.grievous)
        assertTrue(heavy.durationMs > light.durationMs)
    }

    @Test
    fun `5 plus wounds with HoH available is genuinely grievous`() {
        val outcome = resolveWoundOutcome(wounds = 5, hohAvailable = true)!!
        assertTrue(outcome.grievous)
    }

    @Test
    fun `5 plus wounds without HoH available is capped at heavy wounded with the safety-net duration`() {
        val heavy = resolveWoundOutcome(wounds = 3, hohAvailable = false)!!
        val safetyNet = resolveWoundOutcome(wounds = 5, hohAvailable = false)!!
        assertFalse(safetyNet.grievous)
        assertTrue(safetyNet.durationMs > heavy.durationMs)
    }

}
```

(There is deliberately no "wounds never cause death" test here: `WoundOutcome` has no death case in its shape at all — `grievous: Boolean` plus `durationMs: Long` — so that guarantee is enforced by the type system, not worth a runtime assertion that would just restate what the compiler already proves.)

- [ ] **Step 2: Add the pure functions and constants to `EncounterWorker.kt`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt` first.

Add these at the very bottom of the file, after the closing `}` of the `EncounterWorker` class:

```kotlin

// Testing values: 15 seconds for all three bands so Wes can exercise recovery on-device.
// Target production values (revisit once the sim rebalance pass locks in real numbers):
//   LIGHT_WOUND_MS = 3_600_000L   (1 hour)
//   HEAVY_WOUND_MS = 7_200_000L   (2 hours)
//   SAFETY_NET_WOUND_MS = 21_600_000L (6 hours)
private const val LIGHT_WOUND_MS       = 15_000L
private const val HEAVY_WOUND_MS       = 15_000L
private const val SAFETY_NET_WOUND_MS  = 15_000L

data class WoundOutcome(val grievous: Boolean, val durationMs: Long)

// Pure function extracted from EncounterWorker — testable without Android context.
// Down-count -> severity. Wounds never kill (death-by-combat-wound was removed).
// 5+ wounds is genuinely grievous only if the player has a usable HoH recipe;
// otherwise it's capped at heavy-wounded with a longer safety-net timer so the
// band never gets permanently stuck before HoH content exists.
fun resolveWoundOutcome(wounds: Int, hohAvailable: Boolean): WoundOutcome? = when {
    wounds >= 5 -> if (hohAvailable) WoundOutcome(grievous = true, durationMs = 0L)
                   else WoundOutcome(grievous = false, durationMs = SAFETY_NET_WOUND_MS)
    wounds >= 3 -> WoundOutcome(grievous = false, durationMs = HEAVY_WOUND_MS)
    wounds >= 1 -> WoundOutcome(grievous = false, durationMs = LIGHT_WOUND_MS)
    else        -> null
}
```

The module still won't compile after this step (the old `applyWounds`/`band.woundMember` calls are untouched) — continue to Step 3.

- [ ] **Step 3: Create `WoundRecoveryWorker`**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/WoundRecoveryWorker.kt`:

```kotlin
package com.liquidcode7.hearthcraft.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.MainActivity
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WoundRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val band: BandRepository,
    private val gameData: GameDataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val memberId = inputData.getString(KEY_MEMBER_ID) ?: return Result.failure()
        band.healWound(memberId)
        val name = gameData.bandMembers.find { it.id == memberId }?.name ?: "A band member"
        notify("Recovered", "$name is back on their feet.")
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, HearthCraftApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    companion object {
        const val KEY_MEMBER_ID   = "memberId"
        const val NOTIFICATION_ID = 43

        fun buildRequest(memberId: String, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<WoundRecoveryWorker>()
                .setInputData(workDataOf(KEY_MEMBER_ID to memberId))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
```

- [ ] **Step 4: Wire `EncounterWorker` to use `resolveWoundOutcome`, check HoH availability, and schedule recovery**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt` again (in full) before editing.

Add these imports (alongside the existing ones):
```kotlin
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.hasVisibleRecipeOfClass
```

Add `gameData` to the constructor (after `private val combatRepo: CombatRepository`):
```kotlin
    private val combatRepo: CombatRepository,
    private val gameData: GameDataRepository
) : CoroutineWorker(context, params) {
```

Replace the `else -> { // DEFEAT ... }` branch inside `applyOutcome`:
```kotlin
            else -> { // DEFEAT
                applyWounds(wounds)
                band.grantMissionStats(bandId, succeeded = false)
                notify("Mission Failed", "${encounter.name} — your band did not prevail.")
            }
```
with:
```kotlin
            else -> { // DEFEAT
                val hohAvailable = hasVisibleRecipeOfClass(
                    gameData.recipes, "hoh", player.getFoundGrimoireIds(), player.getDiscoveredRecipeIds()
                )
                val safetyNetTriggered = applyWounds(wounds, hohAvailable)
                band.grantMissionStats(bandId, succeeded = false)
                if (safetyNetTriggered) {
                    notify(
                        "Mission Failed",
                        "${encounter.name} — the band was nearly overwhelmed. Without a healer's " +
                            "true craft, they pulled back to recover. It will be a long recovery."
                    )
                } else {
                    notify("Mission Failed", "${encounter.name} — your band did not prevail.")
                }
            }
```

Replace `applyWounds`:
```kotlin
    private suspend fun applyWounds(woundsByMember: Map<String, Int>) {
        woundsByMember.forEach { (memberId, wounds) ->
            when {
                wounds >= 5 -> band.killMember(memberId)
                wounds >= 3 -> band.woundMember(memberId, grievous = true)
                wounds >= 1 -> band.woundMember(memberId, grievous = false)
            }
        }
    }
```
with:
```kotlin
    // Returns true if the HoH-availability safety net downgraded any member's wound this call.
    private suspend fun applyWounds(woundsByMember: Map<String, Int>, hohAvailable: Boolean): Boolean {
        var safetyNetTriggered = false
        woundsByMember.forEach { (memberId, wounds) ->
            val outcome = resolveWoundOutcome(wounds, hohAvailable) ?: return@forEach
            band.woundMember(memberId, outcome.grievous, outcome.durationMs)
            if (!outcome.grievous) {
                scheduleRecovery(memberId, outcome.durationMs)
                if (wounds >= 5) safetyNetTriggered = true
            }
        }
        return safetyNetTriggered
    }

    private fun scheduleRecovery(memberId: String, durationMs: Long) {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "wound_recovery_$memberId",
            ExistingWorkPolicy.REPLACE,
            WoundRecoveryWorker.buildRequest(memberId, durationMs)
        )
    }
```

- [ ] **Step 5: Build — this is the first point the module compiles again**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run `WoundResolutionTest` — first point it can actually execute**

```bash
./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.worker.WoundResolutionTest"
```
Expected: 5 tests, PASS.

- [ ] **Step 7: Commit everything together (includes Task 4's held-back change)**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/WoundRecoveryWorker.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt
git add app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WoundResolutionTest.kt
git commit -m "[hc] Wire EncounterWorker to resolveWoundOutcome; add WoundRecoveryWorker for auto-heal"
```

---

### Task 6: Delete dead wound-treatment code; expose wound timing to the UI layer

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt`

**Interfaces:**
- Produces: `BandMemberWithState.woundedSinceMs: Long` and `BandMemberWithState.woundedDurationMs: Long` (new fields). Consumed by Task 7 (`BandScreen`).
- Removes: `BandViewModel.treatWound(memberId: String, food: PreparedFoodDetail)`.

- [ ] **Step 1: Add fields to `BandMemberWithState`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt` first.

Replace the `BandMemberWithState` data class:
```kotlin
data class BandMemberWithState(
    val memberId: String,
    val name: String,
    val personality: String,
    val foodPreference: String,
    val quirkNote: String,
    val role: String = "",
    val isAlive: Boolean,
    val woundStatus: String = "healthy",
    val might: Int = 0,
    val agility: Int = 0,
    val vitality: Int = 0,
    val will: Int = 0,
    val fate: Int = 0
)
```
with:
```kotlin
data class BandMemberWithState(
    val memberId: String,
    val name: String,
    val personality: String,
    val foodPreference: String,
    val quirkNote: String,
    val role: String = "",
    val isAlive: Boolean,
    val woundStatus: String = "healthy",
    val woundedSinceMs: Long = 0L,
    val woundedDurationMs: Long = 0L,
    val might: Int = 0,
    val agility: Int = 0,
    val vitality: Int = 0,
    val will: Int = 0,
    val fate: Int = 0
)
```

- [ ] **Step 2: Populate the new fields and remove `treatWound`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt` first.

In the `members` builder, replace:
```kotlin
                    woundStatus = state?.woundStatus ?: "healthy",
```
with:
```kotlin
                    woundStatus = state?.woundStatus ?: "healthy",
                    woundedSinceMs = state?.woundedSinceMs ?: 0L,
                    woundedDurationMs = state?.woundedDurationMs ?: 0L,
```

Delete the entire `treatWound` function:
```kotlin
    fun treatWound(memberId: String, food: PreparedFoodDetail) {
        viewModelScope.launch {
            val canTreat = food.buffType == "healing" || food.buffType == "healing_deep"
            if (!canTreat) return@launch
            band.healWound(memberId)
            inventory.removePreparedFood(food.recipeId)
        }
    }

```

- [ ] **Step 3: Build**

```bash
./gradlew build
```
Expected: BUILD FAILS at this point — `BandScreen.kt` still calls `bandViewModel.treatWound(...)`. This is expected; Task 7 fixes it. Confirm the failure is specifically about `treatWound` being unresolved, not anything else.

- [ ] **Step 4: Commit anyway is NOT appropriate here — hold this change uncommitted**

Do not commit yet. Proceed directly to Task 7, which removes the only caller. Task 7's build step will confirm both changes together compile, and Task 7's commit step will include these files too.

---

### Task 7: Remove the "Treat Wounds" UI; add a wound recovery countdown

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt`

**Interfaces:**
- Consumes: `BandMemberWithState.woundedSinceMs`/`woundedDurationMs` (Task 6), `formatMs` (existing, `ui/util/TimeFormat.kt`).

- [ ] **Step 1: Replace the "Treat Wounds" block with a recovery countdown**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt` in full first.

Replace this block (the `woundedMembers`/`healingFood` card, originally lines 132-167):
```kotlin
        val woundedMembers = members.filter { it.isAlive && it.woundStatus != "healthy" }
        val healingFood = preparedFood.filter { it.buffType == "healing" || it.buffType == "healing_deep" }
        if (woundedMembers.isNotEmpty() && healingFood.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Treat Wounds", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            woundedMembers.forEach { member ->
                val applicableFood = healingFood.filter { food ->
                    when (member.woundStatus) {
                        "wounded" -> true
                        "grievously_wounded" -> food.buffType == "healing_deep"
                        else -> false
                    }
                }
                if (applicableFood.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "${member.name} — ${if (member.woundStatus == "grievously_wounded") "Grievous Wound" else "Wounded"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            applicableFood.forEach { food ->
                                OutlinedButton(
                                    onClick = { bandViewModel.treatWound(member.memberId, food) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Treat with ${food.name}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
```
with:
```kotlin
        val recoveringMembers = members.filter { it.isAlive && it.woundStatus == "wounded" }
        if (recoveringMembers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Recovering", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            recoveringMembers.forEach { member ->
                WoundRecoveryRow(
                    name = member.name,
                    woundedSinceMs = member.woundedSinceMs,
                    woundedDurationMs = member.woundedDurationMs
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
```

Note: `"grievously_wounded"` members are deliberately excluded from this list — per design §9.1/§6.7, a genuine grievous wound doesn't auto-heal on a timer, so a countdown would be misleading. (The `MemberDetailDialog` further down the file already shows "Grievous Wound" as a status label for these members — no change needed there.)

- [ ] **Step 2: Add the `WoundRecoveryRow` composable**

In the same file, add this new private composable directly after `MissionActiveCard` (after its closing `}`, matching the same live-ticking pattern):

```kotlin
@Composable
private fun WoundRecoveryRow(name: String, woundedSinceMs: Long, woundedDurationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(woundedSinceMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val remainingMs = maxOf(0L, woundedSinceMs + woundedDurationMs - now)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$name — Wounded",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatMs(remainingMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit both Task 6 and Task 7 changes together**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt
git commit -m "[hc] Remove dead Treat Wounds UI; add live wound-recovery countdown"
```

---

### Task 8: Delete the misclassified `athelas_infusion` recipe

**Files:**
- Modify: `app/src/main/assets/data/recipes.json:736-754`

**Interfaces:** None — data-only change.

- [ ] **Step 1: Remove the recipe entry**

Read `app/src/main/assets/data/recipes.json` first.

Replace:
```json
    "hazardEffect": "hale"
  },
  {
    "id": "athelas_infusion",
    "name": "Athelas Infusion",
    "band": "greycloaks",
    "class": "draught",
    "method": "brew",
    "tier": 5,
    "cookLevel": 12,
    "heroIngredient": "athelas",
    "description": "The last line between a grievous wound and what comes after.",
    "ingredients": [
      { "id": "athelas",      "qty": 2 },
      { "id": "meadowsweet",  "qty": 1 },
      { "id": "wolf_moss",    "qty": 1 }
    ],
    "hazardEffect": "hale"
  }
]
```
with:
```json
    "hazardEffect": "hale"
  }
]
```

This is the last two recipe entries in the file (`restorative_broth` followed by `athelas_infusion`) — after this edit, `restorative_broth` becomes the final entry in the array. Wes wants a dedicated brainstorming/design session for the real HoH recipe roster later (see the "context piece" already prepared) — this entry's numbers (draught class, tier 5, cookLevel 12) don't match design intent for "first HoH recipe" and shouldn't be left half-migrated in the meantime.

- [ ] **Step 2: Verify the JSON is still valid and the build passes**

```bash
python3 -c "import json; json.load(open('app/src/main/assets/data/recipes.json'))" && echo "valid JSON"
./gradlew build
```
Expected: `valid JSON` printed, then BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/data/recipes.json
git commit -m "[hc] Delete misclassified athelas_infusion recipe pending real HoH design"
```

---

### Task 9: Update `design/master-design.md`

**Files:**
- Modify: `design/master-design.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Add a new §6.7 documenting wound severity and recovery**

Read `design/master-design.md` first, specifically §6.6 (ends around line 212) and the `---` separator before §7.

Insert this new subsection immediately after §6.6's content and before the `---` that precedes `## 7. The Provisioning System`:

```markdown

### 6.7 Wound Severity & Recovery

A lost encounter (DEFEAT) assigns each member a wound severity based on how
many times they went down during the fight:

| Down-count | Severity | Recovery |
|------------|----------|----------|
| 1-2 | Wounded (light) | Auto-heals after 1 hour — time only, no food or prep involved |
| 3-4 | Wounded (heavy) | Auto-heals after 2 hours — time only |
| 5+  | Grievously wounded | See §9.1 — requires Houses of Healing, does not auto-heal |

**Wounds never kill.** There is no death-by-combat-wound mechanic. Permadeath,
if designed later, is a separate system (see roadmap Phase 2B).

**HoH-availability safety net:** while the player has no visible/craftable HoH
recipe at all, a 5+ down-count result is capped at "Wounded (heavy)" with an
extended 6-hour recovery instead of becoming a genuine grievous wound. This
prevents an unrecoverable band before Houses of Healing content exists. The
safety net switches off automatically the moment a real HoH recipe becomes
available to the player — it is not a permanent difficulty reduction.
```

- [ ] **Step 2: Cross-reference from §9.1**

Read the §9.1 "What It Does" section (around line 341-352).

Directly after the paragraph ending "...Damage reduction was judged too powerful and excluded." add:

```markdown

See §6.7 for the current wound severity thresholds and the pre-HoH softlock
safety net that applies before any HoH recipe exists.
```

- [ ] **Step 3: Diff review**

```bash
git diff design/master-design.md
```
Confirm both additions are present and nothing else changed.

- [ ] **Step 4: Commit**

```bash
git add design/master-design.md
git commit -m "[hc] Document wound severity/recovery rules and HoH softlock safety net in design doc"
```

---

### Task 10: Full build, test suite, and on-device verification

**Files:** None — verification only.

- [ ] **Step 1: Full test suite**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL, all tests pass (including the new `RecipeAvailabilityTest`, `PlayerRepositoryGrimoireTest`, `BandRepositoryWoundTest`, and `WoundResolutionTest` cases).

- [ ] **Step 2: Full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install and manually verify on-device**

```bash
./gradlew installDebug
```

Manual check (this also satisfies "get the Kotlin build running on the phone" from Wes's original notes):
1. Send the band on an encounter you expect to lose (or repeat one until DEFEAT).
2. Confirm a wounded member shows up under a new "Recovering" card on the Band screen with a live countdown (should read ~15 seconds given the current testing constants).
3. Wait ~15 seconds and confirm the member auto-clears back to healthy with a "back on their feet" notification.
4. Confirm the old "Treat Wounds" card no longer appears anywhere.

- [ ] **Step 4: Report back**

Summarize to Wes: confirm all tests pass, the build is clean, and describe what was observed on-device. Remind him the recovery timers are still at the 15-second testing value and will need to move to their target values (1hr/2hr/6hr) once the sim rebalance pass locks in real encounter difficulty.
