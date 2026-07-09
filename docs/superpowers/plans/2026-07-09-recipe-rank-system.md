# Recipe Rank System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give food recipes a 4-step rank ladder (Base → Insightful → Inspired → Inimitable) that scales in place as cook level rises within a tier, and widen the cook-level/tier curve to a flat 20 levels/tier (cap 100) to give ranks room to breathe.

**Architecture:** `Recipe` gains an optional `ranks: List<RecipeRank>` field (additive — `primaryBoost`/`secondaryBoost` stay as the Base-rank values, so recipes with no authored ranks behave exactly as today). `PreparedFood` gains a `rank` column and joins it to its primary key, since rank — like Grade — is locked in at cook time. `CookingWorker` resolves and stores the rank once, at cook completion; everything downstream (Pantry, Missions, combat stat application) reads the already-resolved values off `PreparedFoodDetail` with no further rank-awareness needed, because the resolution happens once, upstream, in the two ViewModels that build `PreparedFoodDetail` from raw `PreparedFood` rows.

**Tech Stack:** Kotlin, kotlinx.serialization, Room (manual `Migration`, not `AutoMigration` — see Task 3), Jetpack Compose, Hilt.

## Global Constraints

- `ranks` is additive, not a replacement — `Recipe.primaryBoost`/`secondaryBoost` must keep their current names, types, and default of `0`. Do not remove or rename them.
- Recipe Rank applies to **food recipes only**. Do not touch draught or HoH recipes, their JSON, or any code path that only draughts/HoH use (`RecipeBookScreen.kt`'s hazard/wound-type display branches, `BandViewModel.setDraught`, `HohRepository`/`HohViewModel`).
- Rank is locked in at cook time, exactly like Grade — resolved once in `CookingWorker`, stored on the `PreparedFood` row, never re-resolved against a player's later (possibly higher) cook level.
- The `PreparedFood` schema change requires a **manual** Room `Migration` (version 21 → 22), not an `AutoMigration` — SQLite cannot alter a primary key in place, so the migration recreates the table (see Task 3 for the exact SQL). Follow the existing recreate-table pattern already used in this codebase (e.g. `Migration16To17.kt`).
- Do not touch `BalanceHarness.kt`, `tools/recipe-browser/*.js`, or `tools/sim/*.js` — they read `primaryBoost` directly and keep working unchanged (reflecting Base rank only) under the additive schema. Updating them is explicitly out of scope for this plan.
- Rank name prefixes (`Insightful`/`Inspired`/`Inimitable`) live in exactly one place in code (`RANK_PREFIXES` in `Recipe.kt`) — never author them per-recipe in JSON.
- Commit prefix convention: `[hc] <summary>`.
- Run gradle from the repo root (`/home/wes/projects/HearthCraft`), not a `.claude/worktrees/...` job directory — `cd` there explicitly in every shell command since the working directory resets between tool calls.
- Test command form: `./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.<package>.<ClassName>"`. Compile-check command form: `./gradlew :app:compileDebugKotlin`.

---

### Task 1: Cook-level / tier-width restructure

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/repository/CookingLevelCurveTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (used by Task 4): `PlayerRepository.MAX_LEVEL = 100`, unchanged `xpToNext`/`totalXpForLevel`/`levelForTotalXp` signatures (only their internal constants change).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/repository/CookingLevelCurveTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingLevelCurveTest {

    @Test
    fun `MAX_LEVEL is 100`() {
        assertEquals(100, PlayerRepository.MAX_LEVEL)
    }

    @Test
    fun `cooking XP required strictly increases across a tier boundary`() {
        // Levels 20, 40, 60, 80 are tier-transition levels; the XP to climb OUT of
        // one of those levels should cost noticeably more than a same-tier level
        // just below it, thanks to the 1.8x wall multiplier.
        val withinTierCost = PlayerRepository.xpToNext(19, PlayerRepository.Track.COOKING)
        val wallCost = PlayerRepository.xpToNext(20, PlayerRepository.Track.COOKING)
        assertTrue(
            "wall cost ($wallCost) should exceed a same-tier cost near it ($withinTierCost)",
            wallCost > withinTierCost
        )
    }

    @Test
    fun `totalXpForLevel is monotonically increasing up to MAX_LEVEL`() {
        var previous = -1
        for (level in 1..PlayerRepository.MAX_LEVEL) {
            val total = PlayerRepository.totalXpForLevel(level, PlayerRepository.Track.COOKING)
            assertTrue(
                "level $level total XP ($total) should exceed level ${level - 1} ($previous)",
                total > previous
            )
            previous = total
        }
    }

    @Test
    fun `levelForTotalXp round-trips with totalXpForLevel at tier-boundary levels`() {
        for (level in listOf(1, 20, 21, 40, 41, 60, 80, 100)) {
            val xp = PlayerRepository.totalXpForLevel(level, PlayerRepository.Track.COOKING)
            assertEquals(level, PlayerRepository.levelForTotalXp(xp, PlayerRepository.Track.COOKING))
        }
    }

    @Test
    fun `levelForTotalXp never exceeds MAX_LEVEL`() {
        val level = PlayerRepository.levelForTotalXp(Int.MAX_VALUE / 2, PlayerRepository.Track.COOKING)
        assertEquals(PlayerRepository.MAX_LEVEL, level)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.data.repository.CookingLevelCurveTest"`
Expected: FAIL — `MAX_LEVEL is 100` fails (currently 50); the tier-boundary round-trip test fails at levels 20/40/60/80 (currently not boundaries).

- [ ] **Step 3: Implement**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt`, replace:

```kotlin
        // Tier boundaries for cooking — these match TIER_TABLE in the sim/food_model.js
        private val COOK_TIER_BOUNDARIES = setOf(5, 10, 16, 23, 31, 41)
```

with:

```kotlin
        // Tier boundaries for cooking: every cook tier is a flat 20 levels wide.
        // NOTE: tools/sim/food_model.js's TIER_TABLE still reflects the old boundaries
        // and is intentionally not updated by this change (see Global Constraints).
        private val COOK_TIER_BOUNDARIES = setOf(20, 40, 60, 80)
```

And replace:

```kotlin
        const val MAX_LEVEL = 50
```

with:

```kotlin
        const val MAX_LEVEL = 100
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.data.repository.CookingLevelCurveTest"`
Expected: PASS, 5 tests green.

- [ ] **Step 5: Run the full existing test suite to check for regressions**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. (`HOH_TIER_BOUNDARIES` and the HOH track are untouched by this task — only `COOK_TIER_BOUNDARIES` and `MAX_LEVEL` change, and `MAX_LEVEL` is shared across all three tracks, so also check that no GATHERING/HOH-track test hardcodes an assumption of `MAX_LEVEL == 50`.)

- [ ] **Step 6: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/data/repository/CookingLevelCurveTest.kt
git commit -m "[hc] Widen cook-level tiers to a flat 20 levels, raise cap to 100"
```

---

### Task 2: Recipe Rank data model

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Recipe.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/RecipeRankTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (used by Tasks 4, 5, 6, 7): `RecipeRank(cookLevelOffset: Int, primaryBoost: Int, secondaryBoost: Int = 0)`, `Recipe.ranks: List<RecipeRank> = emptyList()`, `RANK_PREFIXES: List<String>`, `Recipe.resolvedRankOrdinal(playerCookLevel: Int): Int`, `Recipe.rankAt(ordinal: Int): RecipeRank`, `Recipe.rankedDisplayName(ordinal: Int): String`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/RecipeRankTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeRankTest {

    private fun recipe(cookLevel: Int = 1, ranks: List<RecipeRank> = emptyList()) = Recipe(
        id = "test", name = "Test Dish", cookLevel = cookLevel,
        primaryStat = "mig", primaryBoost = 2, ranks = ranks
    )

    private val fourRanks = listOf(
        RecipeRank(cookLevelOffset = 0, primaryBoost = 2),
        RecipeRank(cookLevelOffset = 7, primaryBoost = 2),
        RecipeRank(cookLevelOffset = 14, primaryBoost = 3),
        RecipeRank(cookLevelOffset = 20, primaryBoost = 3)
    )

    @Test
    fun `resolvedRankOrdinal returns 0 when ranks is empty`() {
        val r = recipe(cookLevel = 1, ranks = emptyList())
        assertEquals(0, r.resolvedRankOrdinal(playerCookLevel = 50))
    }

    @Test
    fun `resolvedRankOrdinal returns 0 below the first breakpoint`() {
        val r = recipe(cookLevel = 1, ranks = fourRanks)
        assertEquals(0, r.resolvedRankOrdinal(playerCookLevel = 5))
    }

    @Test
    fun `resolvedRankOrdinal returns the matching ordinal exactly at a breakpoint`() {
        val r = recipe(cookLevel = 1, ranks = fourRanks)
        assertEquals(1, r.resolvedRankOrdinal(playerCookLevel = 8))  // 1 + 7
        assertEquals(2, r.resolvedRankOrdinal(playerCookLevel = 15)) // 1 + 14
        assertEquals(3, r.resolvedRankOrdinal(playerCookLevel = 21)) // 1 + 20
    }

    @Test
    fun `resolvedRankOrdinal stays at the last ordinal above the final breakpoint`() {
        val r = recipe(cookLevel = 1, ranks = fourRanks)
        assertEquals(3, r.resolvedRankOrdinal(playerCookLevel = 99))
    }

    @Test
    fun `rankAt returns the matching RecipeRank for a valid ordinal`() {
        val r = recipe(cookLevel = 1, ranks = fourRanks)
        assertEquals(fourRanks[2], r.rankAt(2))
    }

    @Test
    fun `rankAt falls back to a synthesized Base rank when ranks is empty`() {
        val r = recipe(cookLevel = 1, ranks = emptyList())
        val fallback = r.rankAt(0)
        assertEquals(0, fallback.cookLevelOffset)
        assertEquals(2, fallback.primaryBoost)
    }

    @Test
    fun `rankedDisplayName returns the plain name at ordinal 0`() {
        val r = recipe(ranks = fourRanks)
        assertEquals("Test Dish", r.rankedDisplayName(0))
    }

    @Test
    fun `rankedDisplayName prefixes the name at higher ordinals`() {
        val r = recipe(ranks = fourRanks)
        assertEquals("Insightful Test Dish", r.rankedDisplayName(1))
        assertEquals("Inspired Test Dish", r.rankedDisplayName(2))
        assertEquals("Inimitable Test Dish", r.rankedDisplayName(3))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.data.model.RecipeRankTest"`
Expected: FAIL (compile error) — `RecipeRank`, `Recipe.ranks`, `resolvedRankOrdinal`, `rankAt`, `rankedDisplayName` are unresolved.

- [ ] **Step 3: Implement**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Recipe.kt`, add a new `RecipeRank` class before `Recipe`, add the `ranks` field to `Recipe`, and add the resolver functions after the `Recipe` class closes. Full new file content:

```kotlin
package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeIngredient(
    val id: String,
    val qty: Int
)

@Serializable
data class RecipeRank(
    val cookLevelOffset: Int,   // 0 = base rank. Positive values are cook levels
                                // above the recipe's own tier-entry threshold
                                // (Recipe.cookLevel).
    val primaryBoost: Int,
    val secondaryBoost: Int = 0
)

@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val band: String = "all",
    @SerialName("class") val recipeClass: String = "food",
    val method: String = "simmer",
    val tier: Int = 1,
    val cookLevel: Int = 1,
    val primaryStat: String? = null,
    val primaryBoost: Int = 0,
    val secondaryStat: String? = null,
    val secondaryBoost: Int = 0,
    val tertiaryStat: String? = null,
    val hazardEffect: String? = null,
    val description: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val heroIngredient: String = "",   // id of the hero ingredient (counts double in grade resolution)
    val hohLevel: Int = 0,             // minimum HoH level to craft (only relevant when recipeClass == "hoh")
    val treatsWoundTypes: List<String> = emptyList(), // wound type ids this recipe treats, e.g. ["physical", "will"]
    val penalty: Boolean = false,
    val ranks: List<RecipeRank> = emptyList()   // optional. Empty = single-rank,
                                                 // behaves exactly like today.
) {
    // Derived properties used by existing UI code.
    // buffType maps the primary stat to a human-readable buff category.
    val buffType: String get() = when (primaryStat) {
        "mig" -> "might"
        "agi" -> "agility"
        "vit" -> "vitality"
        "wil" -> "will"
        else -> recipeClass  // draughts use their hazardEffect or class as their identity
    }

    // flavorTag is the cooking method — used as a visual tag in the recipe book.
    val flavorTag: String get() = method

    // Cooking duration scales with tier. Tier 1 = 5 min; doubles roughly each tier up.
    val durationMs: Long get() = when (tier) {
        1 ->  5L * 60_000
        2 -> 10L * 60_000
        3 -> 20L * 60_000
        4 -> 30L * 60_000
        else -> 45L * 60_000
    }

    // levelRequired maps to cookLevel for backwards compat with KitchenViewModel tier sorting.
    val levelRequired: Int get() = cookLevel
}

// Rank name prefixes, shared across every recipe family — index 0 (Base) has no
// prefix. The single source of truth for rank naming; never author these per-recipe.
val RANK_PREFIXES = listOf("", "Insightful", "Inspired", "Inimitable")

/**
 * Ordinal into [Recipe.ranks] for the given [playerCookLevel] — 0 (Base) if [Recipe.ranks]
 * is empty or the player hasn't reached the first breakpoint yet.
 */
fun Recipe.resolvedRankOrdinal(playerCookLevel: Int): Int {
    if (ranks.isEmpty()) return 0
    val tierRelative = (playerCookLevel - cookLevel).coerceAtLeast(0)
    return ranks.indices.filter { ranks[it].cookLevelOffset <= tierRelative }.maxOrNull() ?: 0
}

/**
 * The [RecipeRank] at a specific, already-resolved [ordinal] (e.g. one stored on a
 * PreparedFood row). Falls back to a synthesized Base rank from [Recipe.primaryBoost]/
 * [Recipe.secondaryBoost] when [Recipe.ranks] is empty or [ordinal] is out of range.
 */
fun Recipe.rankAt(ordinal: Int): RecipeRank =
    ranks.getOrNull(ordinal) ?: RecipeRank(cookLevelOffset = 0, primaryBoost = primaryBoost, secondaryBoost = secondaryBoost)

/** [Recipe.name] with the rank prefix for [ordinal] applied (no prefix at ordinal 0). */
fun Recipe.rankedDisplayName(ordinal: Int): String {
    val prefix = RANK_PREFIXES.getOrNull(ordinal)?.takeIf { it.isNotEmpty() }
    return if (prefix != null) "$prefix $name" else name
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.data.model.RecipeRankTest"`
Expected: PASS, 8 tests green.

- [ ] **Step 5: Run the full existing test suite to check for regressions**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — confirms the 4 pre-existing test files that construct `Recipe(...)` fixtures (`RecipeAvailabilityTest.kt`, `QualityTest.kt`, `CookingWorkerDiscoveryTest.kt`, `RecipeFilterSortTest.kt`) still compile unchanged, since `ranks` defaults to `emptyList()`.

- [ ] **Step 6: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Recipe.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/RecipeRankTest.kt
git commit -m "[hc] Add Recipe Rank data model (additive, food-only)"
```

---

### Task 3: PreparedFood schema migration

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PreparedFood.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/PreparedFoodDao.kt`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration21To22.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/InventoryRepository.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (used by Tasks 4, 5): `PreparedFood.rank: Int = 0`, `InventoryRepository.addPreparedFood(recipeId: String, grade: Int = 0, rank: Int = 0)`.

**Note on testing:** this repo has no Room `MigrationTestHelper` infrastructure (no `room-testing` dependency, no prior migration test despite 8 existing manual `Migration` classes) — adding that infrastructure is out of proportion for this task. This task is verified by compile + the existing test suite (confirming no other code references the old 2-column primary key assumption) plus manual QA in Task 8, consistent with how every prior migration in this codebase has shipped.

- [ ] **Step 1: Update the `PreparedFood` entity**

Replace the full contents of `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PreparedFood.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity

@Entity(tableName = "prepared_food", primaryKeys = ["recipeId", "grade", "rank"])
data class PreparedFood(
    val recipeId: String,
    val grade: Int = 0,        // ordinal of Grade enum: 0=Crude … 4=Pristine
    val rank: Int = 0,         // ordinal into Recipe.ranks; resolved and stored at
                                // cook-completion time, same lifecycle as grade
    val quantity: Int = 0
)
```

- [ ] **Step 2: Update `PreparedFoodDao`**

Replace the full contents of `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/PreparedFoodDao.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.PreparedFood
import kotlinx.coroutines.flow.Flow

@Dao
interface PreparedFoodDao {

    @Query("SELECT * FROM prepared_food")
    fun observeAll(): Flow<List<PreparedFood>>

    @Query("SELECT * FROM prepared_food WHERE recipeId = :id AND grade = :grade AND rank = :rank")
    suspend fun get(id: String, grade: Int, rank: Int): PreparedFood?

    // Lowest grade first, then lowest rank within that grade — same "protect the
    // player's higher-value stock" intent the grade-only ordering already had.
    @Query("SELECT * FROM prepared_food WHERE recipeId = :id ORDER BY grade ASC, rank ASC")
    suspend fun getAllGradesOf(id: String): List<PreparedFood>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: PreparedFood)

    @Query("UPDATE prepared_food SET quantity = quantity + 1 WHERE recipeId = :id AND grade = :grade AND rank = :rank")
    suspend fun addOne(id: String, grade: Int, rank: Int)

    @Query("UPDATE prepared_food SET quantity = quantity - 1 WHERE recipeId = :id AND grade = :grade AND rank = :rank")
    suspend fun removeOne(id: String, grade: Int, rank: Int)

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM prepared_food WHERE recipeId = :id")
    suspend fun totalQuantity(id: String): Int

    @Query("DELETE FROM prepared_food WHERE recipeId = :id AND grade = :grade AND rank = :rank AND quantity <= 0")
    suspend fun deleteIfEmpty(id: String, grade: Int, rank: Int)
}
```

- [ ] **Step 3: Write the migration**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration21To22.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 21 → 22: Recipe Rank. prepared_food gains a `rank` column and joins it to
 * the primary key, so a Base-rank and an Insightful-rank batch of the same recipe at
 * the same grade are tracked as separate stacks. SQLite has no ALTER TABLE support for
 * changing a primary key, so this recreates the table.
 */
val Migration21To22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS prepared_food_new (
                recipeId TEXT NOT NULL,
                grade INTEGER NOT NULL,
                rank INTEGER NOT NULL DEFAULT 0,
                quantity INTEGER NOT NULL,
                PRIMARY KEY(recipeId, grade, rank)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO prepared_food_new (recipeId, grade, rank, quantity)
            SELECT recipeId, grade, 0, quantity FROM prepared_food
        """.trimIndent())
        db.execSQL("DROP TABLE prepared_food")
        db.execSQL("ALTER TABLE prepared_food_new RENAME TO prepared_food")
    }
}
```

- [ ] **Step 4: Register the migration**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`, change:

```kotlin
    version = 21,
```

to:

```kotlin
    version = 22,
```

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt`, add the import:

```kotlin
import com.liquidcode7.hearthcraft.data.db.Migration21To22
```

alongside the existing `Migration20To21` import, and change:

```kotlin
            .addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17, Migration17To18, Migration18To19, Migration19To20, Migration20To21)
```

to:

```kotlin
            .addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17, Migration17To18, Migration18To19, Migration19To20, Migration20To21, Migration21To22)
```

- [ ] **Step 5: Update `InventoryRepository`**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/InventoryRepository.kt`, replace the "Prepared food" section:

```kotlin
    // ── Prepared food ─────────────────────────────────────────────────────────

    suspend fun addPreparedFood(recipeId: String, grade: Int = 0) {
        val existing = preparedFoodDao.get(recipeId, grade)
        if (existing == null) {
            preparedFoodDao.upsert(PreparedFood(recipeId = recipeId, grade = grade, quantity = 1))
        } else {
            preparedFoodDao.addOne(recipeId, grade)
        }
    }

    /** Removes one serving, consuming lowest grade first. */
    suspend fun removePreparedFood(recipeId: String) {
        val rows = preparedFoodDao.getAllGradesOf(recipeId)
        val target = rows.firstOrNull { it.quantity > 0 } ?: return
        preparedFoodDao.removeOne(target.recipeId, target.grade)
        preparedFoodDao.deleteIfEmpty(target.recipeId, target.grade)
    }

    /** Removes one serving of [recipeId] at the explicit [grade]. */
    suspend fun removePreparedFood(recipeId: String, grade: Int) {
        preparedFoodDao.removeOne(recipeId, grade)
    }

    /** Total quantity of [recipeId] across all grades. */
    suspend fun preparedFoodQty(recipeId: String): Int = preparedFoodDao.totalQuantity(recipeId)
```

with:

```kotlin
    // ── Prepared food ─────────────────────────────────────────────────────────

    suspend fun addPreparedFood(recipeId: String, grade: Int = 0, rank: Int = 0) {
        val existing = preparedFoodDao.get(recipeId, grade, rank)
        if (existing == null) {
            preparedFoodDao.upsert(PreparedFood(recipeId = recipeId, grade = grade, rank = rank, quantity = 1))
        } else {
            preparedFoodDao.addOne(recipeId, grade, rank)
        }
    }

    /** Removes one serving, consuming lowest grade first, then lowest rank. */
    suspend fun removePreparedFood(recipeId: String) {
        val rows = preparedFoodDao.getAllGradesOf(recipeId)
        val target = rows.firstOrNull { it.quantity > 0 } ?: return
        preparedFoodDao.removeOne(target.recipeId, target.grade, target.rank)
        preparedFoodDao.deleteIfEmpty(target.recipeId, target.grade, target.rank)
    }

    /** Removes one serving of [recipeId] at the explicit [grade] (and [rank], default Base). */
    suspend fun removePreparedFood(recipeId: String, grade: Int, rank: Int = 0) {
        preparedFoodDao.removeOne(recipeId, grade, rank)
    }

    /** Total quantity of [recipeId] across all grades and ranks. */
    suspend fun preparedFoodQty(recipeId: String): Int = preparedFoodDao.totalQuantity(recipeId)
```

Note: `HohRepository.kt`'s existing call `inventory.removePreparedFood(recipeId, grade)` (2 args) still compiles unchanged — `rank` defaults to `0`, and HoH `PreparedFood` rows only ever have `rank = 0` since `CookingWorker` (Task 4) only resolves nonzero ranks for food-class recipes.

- [ ] **Step 6: Compile check**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full existing test suite to check for regressions**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PreparedFood.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/PreparedFoodDao.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration21To22.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/InventoryRepository.kt
git commit -m "[hc] PreparedFood: add rank column (DB v21->v22)"
```

---

### Task 4: Resolve rank at cook completion

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorkerRankTest.kt`

**Interfaces:**
- Consumes: `Recipe.resolvedRankOrdinal(playerCookLevel: Int): Int` (Task 2), `InventoryRepository.addPreparedFood(recipeId: String, grade: Int, rank: Int)` (Task 3).
- Produces: nothing new consumed elsewhere.

**Note on testing:** `CookingWorker` is an Android `CoroutineWorker` requiring Hilt/WorkManager infrastructure to instantiate directly (see the existing `CookingWorkerDiscoveryTest.kt` for the established pattern in this repo — it tests the *pure logic* extracted from the worker, not the worker class itself). Follow that same pattern here: test the rank-resolution logic as a plain function call against `Recipe`, not by instantiating `CookingWorker`.

- [ ] **Step 1: Check the existing pattern**

Read `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorkerDiscoveryTest.kt` first to confirm its test style (it should test discovery-filter logic as a standalone function, not construct `CookingWorker` itself). Match that same style for the new test file below.

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorkerRankTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.worker

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.RecipeRank
import com.liquidcode7.hearthcraft.data.model.resolvedRankOrdinal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CookingWorker resolves a food recipe's rank at cook-completion time via
 * Recipe.resolvedRankOrdinal(playerCookLevel) — this test exercises that exact call
 * shape against realistic recipe/cook-level combinations, mirroring how the worker
 * itself calls it (see doWork()'s `recipe.resolvedRankOrdinal(oldLevel)`).
 */
class CookingWorkerRankTest {

    private val rankedRecipe = Recipe(
        id = "brookcress_bannock", name = "Brookcress Bannock", cookLevel = 1,
        primaryStat = "wil", primaryBoost = 2,
        ranks = listOf(
            RecipeRank(cookLevelOffset = 0, primaryBoost = 2),
            RecipeRank(cookLevelOffset = 7, primaryBoost = 2),
            RecipeRank(cookLevelOffset = 14, primaryBoost = 3),
            RecipeRank(cookLevelOffset = 20, primaryBoost = 3)
        )
    )

    @Test
    fun `cooking at the recipe's own unlock level resolves to Base rank`() {
        assertEquals(0, rankedRecipe.resolvedRankOrdinal(playerCookLevel = 1))
    }

    @Test
    fun `cooking at a higher cook level resolves to the matching higher rank`() {
        assertEquals(2, rankedRecipe.resolvedRankOrdinal(playerCookLevel = 15))
    }

    @Test
    fun `a recipe with no authored ranks always resolves to ordinal 0`() {
        val unranked = Recipe(id = "ember_porridge", name = "Ember Porridge", cookLevel = 1)
        assertEquals(0, unranked.resolvedRankOrdinal(playerCookLevel = 50))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.worker.CookingWorkerRankTest"`
Expected: FAIL only if `resolvedRankOrdinal` isn't visible from this package (it should already pass, since it's the same function tested in Task 2 — this step primarily confirms the import resolves and the test compiles against Task 2's completed work). If it already passes, that's fine; proceed to Step 4 to wire it into the actual worker.

- [ ] **Step 4: Implement**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt`, add the import:

```kotlin
import com.liquidcode7.hearthcraft.data.model.resolvedRankOrdinal
```

alongside the existing imports, and replace:

```kotlin
        val oldLevel = player.get()?.cookingLevel ?: 1

        val isFirstCook = inventory.preparedFoodQty(recipeId) == 0
        inventory.addPreparedFood(recipeId, dishGrade)
```

with:

```kotlin
        val oldLevel = player.get()?.cookingLevel ?: 1
        val rankOrdinal = if (recipe.recipeClass == "food") recipe.resolvedRankOrdinal(oldLevel) else 0

        val isFirstCook = inventory.preparedFoodQty(recipeId) == 0
        inventory.addPreparedFood(recipeId, dishGrade, rankOrdinal)
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.worker.CookingWorkerRankTest"`
Expected: PASS, 3 tests green.

- [ ] **Step 6: Run the full existing test suite to check for regressions**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorkerRankTest.kt
git commit -m "[hc] Resolve and store food recipe rank at cook completion"
```

---

### Task 5: Rank-aware prepared-food display

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/InventoryViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryViewModel.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PreparedFoodRankTest.kt`

**Interfaces:**
- Consumes: `Recipe.rankAt(ordinal: Int): RecipeRank`, `Recipe.rankedDisplayName(ordinal: Int): String` (Task 2), `PreparedFood.rank: Int` (Task 3).
- Produces: `PreparedFoodDetail.rank: Int = 0`. No changes to `MissionsScreen.kt` or `BandRepository.kt` are needed — both already read `PreparedFoodDetail.name`/`.primaryBoost`/`.secondaryBoost` generically, and those fields will now already carry rank-resolved values once this task's mapping change lands.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PreparedFoodRankTest.kt` — this tests the exact resolution logic both `InventoryViewModel.preparedFood` and `PantryViewModel.preparedFood` apply, as a standalone function (matching the file-local pure-function-plus-test pattern already used by `RecipeFilterSortTest.kt` and `PantryFilterSortTest.kt` in this same directory):

```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.RecipeRank
import com.liquidcode7.hearthcraft.data.model.rankAt
import com.liquidcode7.hearthcraft.data.model.rankedDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparedFoodRankTest {

    private val recipe = Recipe(
        id = "brookcress_bannock", name = "Brookcress Bannock", cookLevel = 1,
        primaryStat = "wil", primaryBoost = 2,
        ranks = listOf(
            RecipeRank(cookLevelOffset = 0, primaryBoost = 2),
            RecipeRank(cookLevelOffset = 7, primaryBoost = 2),
            RecipeRank(cookLevelOffset = 14, primaryBoost = 3),
            RecipeRank(cookLevelOffset = 20, primaryBoost = 3)
        )
    )

    @Test
    fun `a Base-rank prepared food batch shows the plain name and base boost`() {
        val rank = recipe.rankAt(0)
        assertEquals("Brookcress Bannock", recipe.rankedDisplayName(0))
        assertEquals(2, rank.primaryBoost)
    }

    @Test
    fun `an Inspired-rank prepared food batch shows the prefixed name and its own boost`() {
        val rank = recipe.rankAt(2)
        assertEquals("Inspired Brookcress Bannock", recipe.rankedDisplayName(2))
        assertEquals(3, rank.primaryBoost)
    }

    @Test
    fun `a recipe with no authored ranks always shows the plain name and base boost`() {
        val unranked = Recipe(id = "ember_porridge", name = "Ember Porridge", cookLevel = 1, primaryBoost = 0)
        assertEquals("Ember Porridge", unranked.rankedDisplayName(0))
        assertEquals(0, unranked.rankAt(0).primaryBoost)
    }
}
```

- [ ] **Step 2: Run the test to verify it passes already**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest --tests "com.liquidcode7.hearthcraft.ui.viewmodel.PreparedFoodRankTest"`
Expected: PASS (this exercises Task 2's already-implemented functions directly — it confirms the exact call shape the two ViewModels below will use, before wiring them in).

- [ ] **Step 3: Add `rank` to `PreparedFoodDetail`**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`, replace:

```kotlin
data class PreparedFoodDetail(
    val recipeId: String,
    val name: String,
    val buffType: String,
    val buffStrength: Int,
    val quantity: Int,
    val grade: Int = 0,            // ordinal of Grade enum; 0=Crude
    val primaryStat: String? = null,
    val primaryBoost: Int = 0,
    val secondaryStat: String? = null,
    val secondaryBoost: Int = 0
)
```

with:

```kotlin
data class PreparedFoodDetail(
    val recipeId: String,
    val name: String,
    val buffType: String,
    val buffStrength: Int,
    val quantity: Int,
    val grade: Int = 0,            // ordinal of Grade enum; 0=Crude
    val rank: Int = 0,             // ordinal into Recipe.ranks; 0=Base
    val primaryStat: String? = null,
    val primaryBoost: Int = 0,
    val secondaryStat: String? = null,
    val secondaryBoost: Int = 0
)
```

- [ ] **Step 4: Update `InventoryViewModel`'s `preparedFood` mapping**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/InventoryViewModel.kt`, add the imports:

```kotlin
import com.liquidcode7.hearthcraft.data.model.rankAt
import com.liquidcode7.hearthcraft.data.model.rankedDisplayName
```

and replace:

```kotlin
    val preparedFood: StateFlow<List<PreparedFoodDetail>> = combine(
        inventory.observePreparedFood(),
        player.observe()
    ) { foods, state ->
        foods.mapNotNull { pf ->
            val recipe = gameData.recipes.find { it.id == pf.recipeId && it.recipeClass != "hoh" } ?: return@mapNotNull null
            PreparedFoodDetail(
                recipeId      = pf.recipeId,
                name          = recipe.name,
                buffType      = recipe.buffType,
                buffStrength  = recipe.primaryBoost,
                quantity      = pf.quantity,
                grade         = pf.grade,
                primaryStat   = recipe.primaryStat,
                primaryBoost  = recipe.primaryBoost,
                secondaryStat = recipe.secondaryStat,
                secondaryBoost = recipe.secondaryBoost
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

with:

```kotlin
    val preparedFood: StateFlow<List<PreparedFoodDetail>> = combine(
        inventory.observePreparedFood(),
        player.observe()
    ) { foods, state ->
        foods.mapNotNull { pf ->
            val recipe = gameData.recipes.find { it.id == pf.recipeId && it.recipeClass != "hoh" } ?: return@mapNotNull null
            val rank = recipe.rankAt(pf.rank)
            PreparedFoodDetail(
                recipeId      = pf.recipeId,
                name          = recipe.rankedDisplayName(pf.rank),
                buffType      = recipe.buffType,
                buffStrength  = rank.primaryBoost,
                quantity      = pf.quantity,
                grade         = pf.grade,
                rank          = pf.rank,
                primaryStat   = recipe.primaryStat,
                primaryBoost  = rank.primaryBoost,
                secondaryStat = recipe.secondaryStat,
                secondaryBoost = rank.secondaryBoost
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

- [ ] **Step 5: Update `PantryViewModel`'s `preparedFood` mapping**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryViewModel.kt`, add the imports:

```kotlin
import com.liquidcode7.hearthcraft.data.model.rankAt
import com.liquidcode7.hearthcraft.data.model.rankedDisplayName
```

and replace:

```kotlin
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
```

with:

```kotlin
    val preparedFood: StateFlow<List<PreparedFoodDetail>> = inventory.observePreparedFood()
        .map { foods ->
            foods.mapNotNull { pf ->
                val recipe = gameData.recipes.find { it.id == pf.recipeId && it.recipeClass != "hoh" } ?: return@mapNotNull null
                val rank = recipe.rankAt(pf.rank)
                PreparedFoodDetail(
                    recipeId = pf.recipeId,
                    name = recipe.rankedDisplayName(pf.rank),
                    buffType = recipe.buffType,
                    buffStrength = rank.primaryBoost,
                    quantity = pf.quantity,
                    grade = pf.grade,
                    rank = pf.rank,
                    primaryStat = recipe.primaryStat,
                    primaryBoost = rank.primaryBoost,
                    secondaryStat = recipe.secondaryStat,
                    secondaryBoost = rank.secondaryBoost
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

- [ ] **Step 6: Compile check**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full existing test suite to check for regressions**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/InventoryViewModel.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PantryViewModel.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/PreparedFoodRankTest.kt
git commit -m "[hc] Show rank-resolved name and boost for prepared food"
```

---

### Task 6: Kitchen recipe detail panel rank preview

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`

**Interfaces:**
- Consumes: `Recipe.resolvedRankOrdinal(playerCookLevel: Int): Int`, `Recipe.rankAt(ordinal: Int): RecipeRank`, `Recipe.rankedDisplayName(ordinal: Int): String` (Task 2). `RecipeDetailPanel`'s existing `cookingLevel: Int` parameter (already present, unchanged signature).
- Produces: nothing new consumed elsewhere.

**Why this one's needed (not just nice-to-have):** without it, the Kitchen detail panel keeps showing `recipe.primaryBoost` (Base rank) while a recipe with `ranks` authored may actually cook at a higher rank right now, given the player's current cook level — the previewed number would be wrong, not just incomplete. Previewing *upcoming* ranks not yet reached is still out of scope (per the spec); this task only makes the *current* preview accurate.

- [ ] **Step 1: Update imports**

In `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`, add:

```kotlin
import com.liquidcode7.hearthcraft.data.model.rankAt
import com.liquidcode7.hearthcraft.data.model.rankedDisplayName
import com.liquidcode7.hearthcraft.data.model.resolvedRankOrdinal
```

alongside the existing `com.liquidcode7.hearthcraft.data.model.*` imports.

- [ ] **Step 2: Resolve the current rank in `RecipeDetailPanel`**

In `RecipeDetailPanel` (private composable), replace:

```kotlin
    val gradeToUse = predictedGrade?.first ?: Grade.FINE
    val scaledBoost = (recipe.primaryBoost * gradeMultiplier(gradeToUse)).roundToInt()
    val effectLine = when {
        recipe.penalty && statName != null -> "$statName $scaledBoost"
        recipe.primaryStat != null && statName != null -> "$statName +$scaledBoost"
```

with:

```kotlin
    val currentRank = recipe.rankAt(recipe.resolvedRankOrdinal(cookingLevel))
    val gradeToUse = predictedGrade?.first ?: Grade.FINE
    val scaledBoost = (currentRank.primaryBoost * gradeMultiplier(gradeToUse)).roundToInt()
    val effectLine = when {
        recipe.penalty && statName != null -> "$statName $scaledBoost"
        recipe.primaryStat != null && statName != null -> "$statName +$scaledBoost"
```

- [ ] **Step 3: Show the rank-prefixed name in the panel header**

Still in `RecipeDetailPanel`, replace:

```kotlin
            Text(recipe.name, style = MaterialTheme.typography.titleSmall)
```

with:

```kotlin
            Text(
                recipe.rankedDisplayName(recipe.resolvedRankOrdinal(cookingLevel)),
                style = MaterialTheme.typography.titleSmall
            )
```

(This is inside `RecipeDetailPanel`, which already receives `cookingLevel: Int` as a parameter — no signature change needed.)

- [ ] **Step 4: Compile check**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt
git commit -m "[hc] Kitchen detail panel previews the current resolved rank"
```

---

### Task 7: Author ranks for Greycloaks' food recipes

**Files:**
- Modify: `app/src/main/assets/data/recipes.json`

**Interfaces:**
- Consumes: the `ranks` field added to `Recipe` in Task 2.
- Produces: nothing new consumed elsewhere — content only.

**Scope:** only the 20 Greycloaks food recipes with a `primaryStat` and no `penalty` (Greycloaks is the only live/playable band right now; Mithlost and Undermarch recipes are pre-authored for bands with no missions yet, so ranking them now is premature — they simply stay single-rank until those bands go live, which the additive schema handles with zero risk). `fernys_treacle` (Greycloaks, `penalty: true`, a debuff dish) is deliberately excluded — a penalty recipe getting *stronger* at higher rank would make its drawback worse as a reward for leveling, which is backwards.

Add a `"ranks"` array to each of these 20 recipe objects in `recipes.json` (position within the JSON object doesn't matter — kotlinx.serialization matches by key name, not order; append it as the last field before the object's closing `}` for consistency). Use exactly these values — computed from each recipe's current `primaryBoost`/`secondaryBoost` × the placeholder multiplier table `[1.0, 1.15, 1.3, 1.5]`, rounded, at offsets `[0, 7, 14, 20]`:

- [ ] **Step 1: Recipes with `primaryBoost: 2`, no secondary stat** — `ploughmans_plate`, `hedgerow_hand_pie`, `goodmans_stew`, `brookcress_bannock`, `hearthbread`, `wanderers_supper`, `bree_root_pottage`, `crabapple_blush_tart`, `old_forest_broth` (9 recipes). Add this exact `ranks` array to each:

```json
    "ranks": [
      { "cookLevelOffset": 0, "primaryBoost": 2 },
      { "cookLevelOffset": 7, "primaryBoost": 2 },
      { "cookLevelOffset": 14, "primaryBoost": 3 },
      { "cookLevelOffset": 20, "primaryBoost": 3 }
    ]
```

Worked example — `brookcress_bannock`'s object currently ends with:

```json
    "primaryStat": "wil",
    "primaryBoost": 2
  },
```

it should become:

```json
    "primaryStat": "wil",
    "primaryBoost": 2,
    "ranks": [
      { "cookLevelOffset": 0, "primaryBoost": 2 },
      { "cookLevelOffset": 7, "primaryBoost": 2 },
      { "cookLevelOffset": 14, "primaryBoost": 3 },
      { "cookLevelOffset": 20, "primaryBoost": 3 }
    ]
  },
```

Apply the same pattern (add a comma after the recipe's last existing field, then the `ranks` array, before the closing `}`) to the other 8 recipes in this group.

- [ ] **Step 2: Recipes with `primaryBoost: 4, secondaryBoost: 2`** — `pipeweed_crumble`, `chetwood_rabbit_roast`, `field_and_fen_potage`, `hearth_and_hops`, `rendered_hearth_pie`, `roadwise_berry_mix`, `honey_oat_cake` (7 recipes). Add this exact `ranks` array to each:

```json
    "ranks": [
      { "cookLevelOffset": 0, "primaryBoost": 4, "secondaryBoost": 2 },
      { "cookLevelOffset": 7, "primaryBoost": 5, "secondaryBoost": 2 },
      { "cookLevelOffset": 14, "primaryBoost": 5, "secondaryBoost": 3 },
      { "cookLevelOffset": 20, "primaryBoost": 6, "secondaryBoost": 3 }
    ]
```

- [ ] **Step 3: Recipes with `primaryBoost: 6, secondaryBoost: 3`** — `lone_lands_venison`, `trout_and_mushroom_pan`, `weather_hills_grouse` (3 recipes). Add this exact `ranks` array to each:

```json
    "ranks": [
      { "cookLevelOffset": 0, "primaryBoost": 6, "secondaryBoost": 3 },
      { "cookLevelOffset": 7, "primaryBoost": 7, "secondaryBoost": 3 },
      { "cookLevelOffset": 14, "primaryBoost": 8, "secondaryBoost": 4 },
      { "cookLevelOffset": 20, "primaryBoost": 9, "secondaryBoost": 4 }
    ]
```

- [ ] **Step 4: `rangers_fare`** (`primaryBoost: 8, secondaryBoost: 4`, the only T4 recipe in this set). Add this exact `ranks` array:

```json
    "ranks": [
      { "cookLevelOffset": 0, "primaryBoost": 8, "secondaryBoost": 4 },
      { "cookLevelOffset": 7, "primaryBoost": 9, "secondaryBoost": 5 },
      { "cookLevelOffset": 14, "primaryBoost": 10, "secondaryBoost": 5 },
      { "cookLevelOffset": 20, "primaryBoost": 12, "secondaryBoost": 6 }
    ]
```

- [ ] **Step 5: Validate the JSON**

Run: `cd /home/wes/projects/HearthCraft && python3 -c "import json; data = json.load(open('app/src/main/assets/data/recipes.json')); print('valid JSON,', len(data), 'recipes')"`
Expected: `valid JSON, 71 recipes` (the recipe count must be unchanged — this task only adds fields to existing objects, never adds/removes recipes).

Then confirm exactly 20 recipes now have a `ranks` array:

Run: `cd /home/wes/projects/HearthCraft && python3 -c "
import json
data = json.load(open('app/src/main/assets/data/recipes.json'))
ranked = [r['id'] for r in data if r.get('ranks')]
print(len(ranked), 'recipes have ranks')
assert len(ranked) == 20, ranked
print('OK')
"`
Expected: `20 recipes have ranks` then `OK`.

- [ ] **Step 6: Compile check (also validates the JSON deserializes correctly against the new schema)**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Deserialization itself is exercised at runtime/in unit tests that load `recipes.json`, not by this compile step alone — Step 7 covers that.)

- [ ] **Step 7: Run the full existing test suite to check for regressions**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. This exercises `BalanceHarness.kt` and any other test that loads `recipes.json` via `Json.decodeFromString`, confirming the new `ranks` field parses without error even in tests that don't care about it.

- [ ] **Step 8: Commit**

```bash
cd /home/wes/projects/HearthCraft
git add app/src/main/assets/data/recipes.json
git commit -m "[hc] Author rank ladders for Greycloaks' 20 stat-boost food recipes"
```

---

### Task 8: Manual verification pass

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Full test suite**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no regressions in any existing test class.

- [ ] **Step 2: Debug build**

Run: `cd /home/wes/projects/HearthCraft && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual click-through (requires a connected device or emulator; if none is available, report this step as skipped rather than guessing a result)**

- Cook a rank-eligible recipe (e.g. Brookcress Bannock) at a low cook level; confirm it shows as plain "Brookcress Bannock" in Pantry and the Kitchen detail panel.
- Raise cook level past a breakpoint (no debug/cheat screen exists in this codebase as of this plan — repeat cooking sessions until cooking XP crosses cook level `1 + 7 = 8`, or skip this specific check if that's impractical in one sitting and note it as skipped); cook the same recipe again; confirm the new batch shows as "Insightful Brookcress Bannock" with a higher boost, while the earlier Base-rank batch in the pantry still shows as plain "Brookcress Bannock" with its original (lower) boost — the two must remain separate stacks, not merge.
- Assign an Insightful-rank batch to a band member in Missions; confirm the stat line shown matches the Insightful boost, not Base.
- Confirm a fresh install (or an upgrade from a build at DB v21, if one is available to test against) doesn't crash on the `prepared_food` migration.

- [ ] **Step 4: Report**

No commit for this task — report the outcome of Steps 1–3 (pass/fail/skipped-with-reason) to the user.
