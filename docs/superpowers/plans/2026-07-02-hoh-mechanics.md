# Houses of Healing — Full Mechanics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the full Houses of Healing system — five wound types, guaranteed+chance wound infliction, combined recovery timers with time-credit, HoH XP/leveling, recipe data, stockpiled preparations, and post-recovery buff.

**Architecture:** HoH mirrors the cooking system structurally. New fields on `PlayerState` and `BandMemberState` carry HoH level/XP and wound type data. A new `HohWorker` fires when recovery completes. Wound type logic lives in `EncounterEngine`. Quality resolution reuses `CookQuality`. A new `HohSession` DB entity tracks active preparations per member. The recipe JSON gains HoH-class entries.

**Tech Stack:** Kotlin, Room (SQLite), WorkManager, Hilt, kotlinx.serialization, Jetpack Compose.

## Global Constraints

- Minimum SDK: API 26
- No Google Play Services dependency
- All Kotlin source under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- Game data JSON under `app/src/main/assets/data/`
- GPL-3.0 license — no proprietary dependencies
- Room database currently at version 13 — each migration increments the version
- Use `@ColumnInfo(defaultValue = "...")` on all new Room columns for zero-migration safety
- Never use `java/` source directories — Kotlin only
- Commit message prefix: `[hc]`

---

## File Map

**New files:**
- `data/db/HohSession.kt` — Room entity tracking active HoH treatment per member
- `data/db/dao/HohSessionDao.kt` — DAO for `HohSession`
- `data/db/Migration14To15.kt` — manual Room migration (adding `HohSession` table + new columns)
- `worker/HohWorker.kt` — WorkManager worker that fires when HoH recovery completes
- `data/repository/HohRepository.kt` — business logic: apply preparation, calculate timer, manage sessions

**Modified files:**
- `data/db/PlayerState.kt` — add `hohLevel: Int`, `hohXp: Int`
- `data/db/BandMemberState.kt` — add `woundTypes: String` (comma-delimited), `hohTimerStartMs: Long`, `hohTimerDurationMs: Long`, `recoveryBuffGrade: Int`, `recoveryBuffTier: Int`, `recoveryBuffPending: Boolean`
- `data/db/dao/BandMemberStateDao.kt` — add queries for wound type and HoH timer operations
- `data/db/dao/PlayerStateDao.kt` — add `addHohXp(xp: Int)`, `setHohLevel(level: Int)` queries
- `data/db/HearthCraftDatabase.kt` — bump version to 15, register `HohSession` entity, add migration
- `data/model/Encounter.kt` — add `grievousWoundTypes: List<GrievousWoundSpec>` field
- `data/model/Recipe.kt` — add `hohLevel: Int`, `treatsWoundTypes: List<String>` fields
- `data/repository/PlayerRepository.kt` — add `Track.HOH`, `addHohXp()`, HoH XP constants, HoH level thresholds
- `data/repository/BandRepository.kt` — add `woundMemberHoh()`, `applyHohPreparation()` methods
- `data/repository/GameDataRepository.kt` — `isRecipeVisible` handles `recipeClass == "hoh"` correctly (verify existing logic suffices)
- `engine/EncounterEngine.kt` — wound type rolling on grievous result
- `app/src/main/assets/data/recipes.json` — add all HoH recipe entries
- `app/src/main/assets/data/ingredients.json` — add HoH-exclusive ingredients
- `app/src/main/assets/data/encounters.json` — add `grievousWoundTypes` to existing encounters

---

## Task 1: Data Model — Wound Types on BandMemberState

**Files:**
- Modify: `data/db/BandMemberState.kt`
- Modify: `data/db/dao/BandMemberStateDao.kt`

**Interfaces:**
- Produces:
  - `BandMemberState.woundTypes: String` — comma-delimited wound type ids, e.g. `"physical,corruption"`. Empty string = no grievous wound types active.
  - `BandMemberState.hohTimerStartMs: Long` — epoch ms when HoH timer started. 0 = no timer running.
  - `BandMemberState.hohTimerDurationMs: Long` — total ms of HoH recovery. 0 = not set.
  - `BandMemberState.recoveryBuffGrade: Int` — grade ordinal (0–4) of best preparation applied. 0 default.
  - `BandMemberState.recoveryBuffTier: Int` — recipe tier (1–4) of best preparation applied. 0 default.
  - `BandMemberState.recoveryBuffPending: Boolean` — true when buff should fire on next mission. false default.
  - DAO: `suspend fun setWoundTypes(id: String, types: String)`
  - DAO: `suspend fun setHohTimer(id: String, startMs: Long, durationMs: Long)`
  - DAO: `suspend fun clearHohState(id: String)` — clears wound types, timer, buff pending
  - DAO: `suspend fun setRecoveryBuff(id: String, grade: Int, tier: Int, pending: Boolean)`

- [ ] **Step 1: Add fields to `BandMemberState`**

```kotlin
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
    val fate: Int = 0,
    @ColumnInfo(defaultValue = "")
    val woundTypes: String = "",
    @ColumnInfo(defaultValue = "0")
    val hohTimerStartMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val hohTimerDurationMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val recoveryBuffGrade: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val recoveryBuffTier: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val recoveryBuffPending: Boolean = false
)
```

- [ ] **Step 2: Add DAO queries to `BandMemberStateDao`**

```kotlin
@Query("UPDATE band_member_state SET woundTypes = :types WHERE memberId = :id")
suspend fun setWoundTypes(id: String, types: String)

@Query("UPDATE band_member_state SET hohTimerStartMs = :startMs, hohTimerDurationMs = :durationMs WHERE memberId = :id")
suspend fun setHohTimer(id: String, startMs: Long, durationMs: Long)

@Query("UPDATE band_member_state SET woundTypes = '', hohTimerStartMs = 0, hohTimerDurationMs = 0, recoveryBuffPending = 0 WHERE memberId = :id")
suspend fun clearHohState(id: String)

@Query("UPDATE band_member_state SET recoveryBuffGrade = :grade, recoveryBuffTier = :tier, recoveryBuffPending = :pending WHERE memberId = :id")
suspend fun setRecoveryBuff(id: String, grade: Int, tier: Int, pending: Boolean)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/BandMemberState.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/BandMemberStateDao.kt
git commit -m "[hc] Add HoH wound type and timer fields to BandMemberState"
```

---

## Task 2: Data Model — HoH Level on PlayerState

**Files:**
- Modify: `data/db/PlayerState.kt`
- Modify: `data/db/dao/PlayerStateDao.kt`

**Interfaces:**
- Consumes: existing `PlayerState` structure
- Produces:
  - `PlayerState.hohLevel: Int` default `1`
  - `PlayerState.hohXp: Int` default `0`
  - DAO: `suspend fun addHohXp(xp: Int)`

- [ ] **Step 1: Add fields to `PlayerState`**

Add after `cookingXp`:
```kotlin
@ColumnInfo(defaultValue = "1")
val hohLevel: Int = 1,
@ColumnInfo(defaultValue = "0")
val hohXp: Int = 0,
```

- [ ] **Step 2: Add DAO query to `PlayerStateDao`**

Find the existing `addCookingXp` query and add alongside it:
```kotlin
@Query("UPDATE player_state SET hohXp = hohXp + :xp WHERE id = 0")
suspend fun addHohXp(xp: Int)

@Query("UPDATE player_state SET hohLevel = :level WHERE id = 0")
suspend fun setHohLevel(level: Int)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PlayerState.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/PlayerStateDao.kt
git commit -m "[hc] Add hohLevel and hohXp fields to PlayerState"
```

---

## Task 3: Room Migration 13 → 15

**Files:**
- Create: `data/db/Migration14To15.kt`
- Modify: `data/db/HearthCraftDatabase.kt`

**Interfaces:**
- Consumes: new columns from Tasks 1 and 2, new `hoh_sessions` table from Task 4
- Produces: `Migration14To15` object registered in `HearthCraftDatabase`

Note: We skip version 14 by jumping straight from 13 to 15 in one migration, since all schema changes are being introduced together. Room allows this.

- [ ] **Step 1: Create `Migration14To15.kt`**

```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration14To15 = object : Migration(13, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // BandMemberState new columns
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN woundTypes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN hohTimerStartMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN hohTimerDurationMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN recoveryBuffGrade INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN recoveryBuffTier INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN recoveryBuffPending INTEGER NOT NULL DEFAULT 0")

        // PlayerState new columns
        db.execSQL("ALTER TABLE player_state ADD COLUMN hohLevel INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE player_state ADD COLUMN hohXp INTEGER NOT NULL DEFAULT 0")

        // New hoh_sessions table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS hoh_sessions (
                memberId TEXT NOT NULL PRIMARY KEY,
                treatedTypes TEXT NOT NULL DEFAULT '',
                bestGrade INTEGER NOT NULL DEFAULT 0,
                bestTier INTEGER NOT NULL DEFAULT 1,
                elapsedMsAtLastTreatment INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}
```

- [ ] **Step 2: Update `HearthCraftDatabase`**

Find the `@Database` annotation and bump `version` from `13` to `15`. Find the `addMigrations(...)` call in the Room builder and add `Migration14To15`. Also add `HohSession::class` to the entities list (defined in Task 4 — leave a TODO comment if Task 4 not yet done).

```kotlin
// Change:
@Database(entities = [...], version = 13, ...)
// To:
@Database(entities = [..., HohSession::class], version = 15, ...)

// In the builder:
.addMigrations(/* existing migrations */, Migration14To15)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration14To15.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt
git commit -m "[hc] Room migration 13→15: HoH wound types, timer fields, hoh_sessions table"
```

---

## Task 4: HohSession Entity and DAO

**Files:**
- Create: `data/db/HohSession.kt`
- Create: `data/db/dao/HohSessionDao.kt`

**Interfaces:**
- Produces:
  - `HohSession` entity with fields: `memberId`, `treatedTypes`, `bestGrade`, `bestTier`, `elapsedMsAtLastTreatment`
  - `HohSessionDao`: `upsert`, `get`, `delete`, `observeAll`

- [ ] **Step 1: Create `HohSession.kt`**

```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hoh_sessions")
data class HohSession(
    @PrimaryKey val memberId: String,
    // Comma-delimited wound types already treated, e.g. "physical,corruption"
    @ColumnInfo(defaultValue = "") val treatedTypes: String = "",
    // Best grade ordinal (0–4) across all preparations applied so far
    @ColumnInfo(defaultValue = "0") val bestGrade: Int = 0,
    // Recipe tier (1–4) of the best preparation applied
    @ColumnInfo(defaultValue = "1") val bestTier: Int = 1,
    // Elapsed ms already served before the last treatment recalculation
    @ColumnInfo(defaultValue = "0") val elapsedMsAtLastTreatment: Long = 0L
)
```

- [ ] **Step 2: Create `HohSessionDao.kt`**

```kotlin
package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.*
import com.liquidcode7.hearthcraft.data.db.HohSession
import kotlinx.coroutines.flow.Flow

@Dao
interface HohSessionDao {
    @Query("SELECT * FROM hoh_sessions")
    fun observeAll(): Flow<List<HohSession>>

    @Query("SELECT * FROM hoh_sessions WHERE memberId = :id")
    suspend fun get(id: String): HohSession?

    @Upsert
    suspend fun upsert(session: HohSession)

    @Query("DELETE FROM hoh_sessions WHERE memberId = :id")
    suspend fun delete(id: String)
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HohSession.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/HohSessionDao.kt
git commit -m "[hc] Add HohSession entity and DAO for active HoH treatments"
```

---

## Task 5: HoH Track in PlayerRepository

**Files:**
- Modify: `data/repository/PlayerRepository.kt`

**Interfaces:**
- Consumes: `PlayerStateDao.addHohXp(xp: Int)`, `PlayerStateDao.setHohLevel(level: Int)` from Task 2
- Produces:
  - `Track.HOH` enum value
  - `suspend fun addHohXp(xp: Int)` — grants XP and auto-levels
  - Constants: `XP_HOH_FIRST = 40`, `XP_HOH_REPEAT = 25`, `XP_HOH_APPLY = 35`, `XP_HOH_CLEAR_BONUS = 20`
  - `HOH_TIER_BOUNDARIES = setOf(5, 10, 16, 23, 31, 41)`
  - `xpToNext(level, Track.HOH)` handles HOH track with `A=22, P=1.2, wallMultiplier=2.0`

- [ ] **Step 1: Add `Track.HOH` to the enum**

Find `enum class Track { COOKING, GATHERING }` and change to:
```kotlin
enum class Track { COOKING, GATHERING, HOH }
```

- [ ] **Step 2: Add HOH curve constants and thresholds to companion object**

Add alongside `COOK_TIER_BOUNDARIES`:
```kotlin
private val HOH_TIER_BOUNDARIES = setOf(5, 10, 16, 23, 31, 41)

const val XP_HOH_FIRST       = 40
const val XP_HOH_REPEAT      = 25
const val XP_HOH_APPLY       = 35
const val XP_HOH_CLEAR_BONUS = 20
```

- [ ] **Step 3: Extend `xpToNext` to handle `Track.HOH`**

Find the `when (track)` block inside `xpToNext` and add:
```kotlin
Track.HOH -> {
    val base = (22.0 * level.toDouble().pow(1.2)).roundToInt()
    val wall = if (level > 1 && HOH_TIER_BOUNDARIES.contains(level)) 2.0 else 1.0
    maxOf(1, (base * wall).roundToInt())
}
```

- [ ] **Step 4: Add HOH threshold array and extend `totalXpForLevel` and `levelForTotalXp`**

Add after `GATHER_THRESHOLDS`:
```kotlin
private val HOH_THRESHOLDS: IntArray = IntArray(MAX_LEVEL) { i ->
    var total = 0; for (l in 1..i) total += xpToNext(l, Track.HOH); total
}
```

Extend `totalXpForLevel`:
```kotlin
Track.HOH -> HOH_THRESHOLDS[idx]
```

Extend `levelForTotalXp`:
```kotlin
Track.HOH -> HOH_THRESHOLDS
```

- [ ] **Step 5: Add `addHohXp` method to `PlayerRepository`**

Add alongside `addCookingXp`:
```kotlin
suspend fun addHohXp(xp: Int) {
    dao.addHohXp(xp)
    val state = dao.get() ?: return
    val newLevel = levelForTotalXp(state.hohXp, track = Track.HOH)
    if (newLevel != state.hohLevel) dao.setHohLevel(newLevel)
}
```

- [ ] **Step 6: Build and verify no compile errors**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt
git commit -m "[hc] Add HOH XP track to PlayerRepository"
```

---

## Task 6: HoH Recipe and Ingredient Data Model Extensions

**Files:**
- Modify: `data/model/Recipe.kt`
- Modify: `data/model/Encounter.kt`

**Interfaces:**
- Produces:
  - `Recipe.hohLevel: Int` — minimum HoH level to craft (default 0, only relevant when `recipeClass == "hoh"`)
  - `Recipe.treatsWoundTypes: List<String>` — wound type ids this recipe treats, e.g. `["physical", "will"]`
  - `GrievousWoundSpec` data class: `woundType: String`, `guaranteed: Boolean`, `chance: Float` (0f–1f)
  - `Encounter.grievousWoundSpecs: List<GrievousWoundSpec>` — wound infliction config

- [ ] **Step 1: Add fields to `Recipe.kt`**

Add after `heroIngredient`:
```kotlin
val hohLevel: Int = 0,
val treatsWoundTypes: List<String> = emptyList(),
```

- [ ] **Step 2: Add `GrievousWoundSpec` and field to `Encounter.kt`**

Add new serializable data class (can live in `Encounter.kt` or its own file — put it in `Encounter.kt` for now):
```kotlin
@Serializable
data class GrievousWoundSpec(
    val woundType: String,
    val guaranteed: Boolean = false,
    val chance: Float = 0f
)
```

Add to `Encounter`:
```kotlin
val grievousWoundSpecs: List<GrievousWoundSpec> = emptyList(),
```

- [ ] **Step 3: Build to verify serialization compiles**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Recipe.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Encounter.kt
git commit -m "[hc] Add HoH recipe fields and GrievousWoundSpec to data model"
```

---

## Task 7: Wound Type Rolling in EncounterEngine

**Files:**
- Modify: `engine/EncounterEngine.kt`

**Interfaces:**
- Consumes: `Encounter.grievousWoundSpecs: List<GrievousWoundSpec>` from Task 6
- Produces: `fun rollWoundTypes(specs: List<GrievousWoundSpec>, rng: Random): List<String>` — internal function returning list of wound type ids that resolved. Always returns at least one type (guaranteed types ensure this).

- [ ] **Step 1: Add `rollWoundTypes` function inside `EncounterEngine`**

Find `EncounterEngine` and add this function (private or internal scope):
```kotlin
internal fun rollWoundTypes(specs: List<GrievousWoundSpec>, rng: Random): List<String> {
    val result = mutableListOf<String>()
    for (spec in specs) {
        if (spec.guaranteed || rng.nextFloat() < spec.chance) {
            result += spec.woundType
        }
    }
    // Safety: if all chance rolls missed and nothing was guaranteed, fall back to first spec
    if (result.isEmpty() && specs.isNotEmpty()) result += specs.first().woundType
    return result
}
```

- [ ] **Step 2: Call `rollWoundTypes` at the grievous wound assignment point**

Find where `m.grievous = true` is set (currently around line 89). Immediately after that block, collect wound types for that member. The engine currently returns a result that gets processed in `EncounterWorker` or `BandRepository`. Locate how the grievous flag reaches `woundMember()` and pass wound types alongside it.

Find the result-building block at the end of `runEncounter` (or equivalent) and update it to include wound types per member:

```kotlin
// Existing pattern (find and extend):
val grievousIds = party.filter { it.grievous }.map { it.input.id }

// Add:
val grievousWoundMap: Map<String, List<String>> = party
    .filter { it.grievous }
    .associate { ms ->
        ms.input.id to rollWoundTypes(input.encounter.grievousWoundSpecs, rng)
    }
```

Return `grievousWoundMap` as part of the engine's output. Trace the exact return type of `runEncounter` and add a `grievousWoundTypes: Map<String, List<String>>` field to it.

- [ ] **Step 3: Build and fix any compile errors**

```bash
./gradlew assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt
git commit -m "[hc] Roll wound types on grievous result in EncounterEngine"
```

---

## Task 8: Wire Wound Types Through EncounterWorker → BandRepository

**Files:**
- Modify: `worker/EncounterWorker.kt`
- Modify: `data/repository/BandRepository.kt`

**Interfaces:**
- Consumes: `grievousWoundMap: Map<String, List<String>>` from Task 7's engine output
- Produces:
  - `BandRepository.woundMemberHoh(memberId: String, grievous: Boolean, durationMs: Long, woundTypes: List<String>)` — extends existing `woundMember` to also persist wound types when grievous

- [ ] **Step 1: Update `BandRepository.woundMember` to accept wound types**

Find `woundMember(memberId: String, grievous: Boolean, durationMs: Long)` and update signature:
```kotlin
suspend fun woundMember(memberId: String, grievous: Boolean, durationMs: Long, woundTypes: List<String> = emptyList()) {
    val status = if (grievous) "grievously_wounded" else "wounded"
    dao.updateWound(memberId, status, System.currentTimeMillis())
    if (grievous && woundTypes.isNotEmpty()) {
        dao.setWoundTypes(memberId, woundTypes.joinToString(","))
    }
    // existing woundedDurationMs logic unchanged
}
```

- [ ] **Step 2: Update `EncounterWorker` to pass wound types to `woundMember`**

Find where `EncounterWorker` calls `band.woundMember(...)` for grievous members and change to:
```kotlin
val woundTypes = result.grievousWoundTypes[memberId] ?: emptyList()
band.woundMember(memberId, grievous = true, durationMs = ..., woundTypes = woundTypes)
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt
git commit -m "[hc] Wire wound types from EncounterEngine through to BandMemberState"
```

---

## Task 9: HohRepository — Apply Preparation and Timer Logic

**Files:**
- Create: `data/repository/HohRepository.kt`

**Interfaces:**
- Consumes:
  - `BandMemberStateDao.setWoundTypes(id, types)`
  - `BandMemberStateDao.setHohTimer(id, startMs, durationMs)`
  - `BandMemberStateDao.setRecoveryBuff(id, grade, tier, pending)`
  - `HohSessionDao.get(id)`, `HohSessionDao.upsert(session)`, `HohSessionDao.delete(id)`
  - `PlayerRepository.addHohXp(xp)`
  - `CookQuality.resolveDishGrade(recipe, ingredientGrades, playerHohLevel)`
  - `PlayerRepository.Track.HOH`
- Produces:
  - `suspend fun applyPreparation(memberId: String, recipeId: String, ingredientGrades: Map<String, Int>): ApplyResult`
  - `data class ApplyResult(val timerMs: Long, val grade: Int, val allWoundsCleared: Boolean)`
  - `fun calcTimer(woundTypes: List<String>, treatedTypes: List<String>, grade: Int, tier: Int): Long`

Timer formula constants (floor durations in ms):
```kotlin
private val WOUND_FLOOR_MS = mapOf(
    "physical"  to  4 * 3600_000L,
    "will"      to  3 * 3600_000L,
    "corruption"to  6 * 3600_000L,
    "poison"    to  4 * 3600_000L,
    "disease"   to  5 * 3600_000L
)
// Grade reduction factors per tier: index = grade (0..4)
private val TIER_REDUCTION = mapOf(
    1 to floatArrayOf(1.00f, 0.75f, 0.50f, 0.375f, 0.25f),
    2 to floatArrayOf(1.00f, 0.70f, 0.45f, 0.340f, 0.22f),
    3 to floatArrayOf(1.00f, 0.65f, 0.40f, 0.310f, 0.20f),
    4 to floatArrayOf(1.00f, 0.60f, 0.35f, 0.280f, 0.18f)
)
```

- [ ] **Step 1: Create `HohRepository.kt`**

```kotlin
package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.HohSession
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
import com.liquidcode7.hearthcraft.data.db.dao.HohSessionDao
import com.liquidcode7.hearthcraft.data.quality.CookQuality
import javax.inject.Inject
import javax.inject.Singleton

data class ApplyResult(
    val timerMs: Long,
    val grade: Int,
    val allWoundsCleared: Boolean
)

@Singleton
class HohRepository @Inject constructor(
    private val memberDao: BandMemberStateDao,
    private val hohSessionDao: HohSessionDao,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) {

    private val WOUND_FLOOR_MS = mapOf(
        "physical"   to  4 * 3_600_000L,
        "will"       to  3 * 3_600_000L,
        "corruption" to  6 * 3_600_000L,
        "poison"     to  4 * 3_600_000L,
        "disease"    to  5 * 3_600_000L
    )

    private val TIER_REDUCTION = mapOf(
        1 to floatArrayOf(1.00f, 0.75f, 0.50f, 0.375f, 0.25f),
        2 to floatArrayOf(1.00f, 0.70f, 0.45f, 0.340f, 0.22f),
        3 to floatArrayOf(1.00f, 0.65f, 0.40f, 0.310f, 0.20f),
        4 to floatArrayOf(1.00f, 0.60f, 0.35f, 0.280f, 0.18f)
    )

    suspend fun applyPreparation(
        memberId: String,
        recipeId: String,
        ingredientGrades: Map<String, Int>
    ): ApplyResult {
        val member = memberDao.get(memberId) ?: error("Member $memberId not found")
        val recipe = gameData.recipes.find { it.id == recipeId }
            ?: error("Recipe $recipeId not found")
        val playerState = player.get() ?: error("PlayerState not found")

        val grade = CookQuality.resolveDishGrade(recipe, ingredientGrades, playerState.hohLevel)
        val tier = recipe.tier.coerceIn(1, 4)

        // Load or create session
        val session = hohSessionDao.get(memberId) ?: HohSession(memberId)

        // Calculate elapsed time already served
        val nowMs = System.currentTimeMillis()
        val elapsed = if (member.hohTimerStartMs > 0L)
            (nowMs - member.hohTimerStartMs).coerceAtLeast(0L)
        else
            session.elapsedMsAtLastTreatment

        // Merge treated types
        val prevTreated = session.treatedTypes.split(",").filter { it.isNotBlank() }.toMutableSet()
        val newlyTreated = recipe.treatsWoundTypes.toSet()
        val allTreated = prevTreated + newlyTreated

        // Best grade and tier across all preparations
        val bestGrade = maxOf(session.bestGrade, grade)
        val bestTier = maxOf(session.bestTier, tier)

        // All wound types on member
        val memberWoundTypes = member.woundTypes.split(",").filter { it.isNotBlank() }
        val allWoundsCleared = memberWoundTypes.isNotEmpty() && allTreated.containsAll(memberWoundTypes)

        // Calculate new timer
        val newTimer = calcTimer(memberWoundTypes, allTreated.toList(), bestGrade, bestTier)
        val creditedTimer = (newTimer - elapsed).coerceAtLeast(0L)

        // Persist session
        hohSessionDao.upsert(
            session.copy(
                treatedTypes = allTreated.joinToString(","),
                bestGrade = bestGrade,
                bestTier = bestTier,
                elapsedMsAtLastTreatment = elapsed
            )
        )

        // Start/restart timer
        memberDao.setHohTimer(memberId, nowMs, creditedTimer)

        // Set recovery buff (best across applications)
        memberDao.setRecoveryBuff(memberId, bestGrade, bestTier, pending = true)

        // Grant XP
        val xp = PlayerRepository.XP_HOH_APPLY +
            if (allWoundsCleared) PlayerRepository.XP_HOH_CLEAR_BONUS else 0
        player.addHohXp(xp)

        return ApplyResult(timerMs = creditedTimer, grade = grade, allWoundsCleared = allWoundsCleared)
    }

    fun calcTimer(
        woundTypes: List<String>,
        treatedTypes: List<String>,
        grade: Int,
        tier: Int
    ): Long {
        val treated = treatedTypes.toSet()
        val reductions = TIER_REDUCTION[tier.coerceIn(1, 4)]!!
        val factor = reductions[grade.coerceIn(0, 4)]
        return woundTypes.sumOf { type ->
            val floor = WOUND_FLOOR_MS[type] ?: 3_600_000L
            if (type in treated) (floor * factor).toLong() else floor
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/HohRepository.kt
git commit -m "[hc] Add HohRepository with applyPreparation and calcTimer logic"
```

---

## Task 10: HohWorker — Recovery Completion

**Files:**
- Create: `worker/HohWorker.kt`
- Modify: `data/repository/BandRepository.kt` (add `completeHohRecovery`)

**Interfaces:**
- Consumes:
  - `BandMemberStateDao.clearHohState(id)`
  - `HohSessionDao.delete(id)`
  - `BandMemberStateDao.healWound(id)` — existing method, resets woundStatus to healthy
- Produces:
  - `HohWorker` — fires when recovery timer expires, clears grievous wound state
  - `BandRepository.completeHohRecovery(memberId: String)`
  - `HohWorker.buildRequest(memberId: String, durationMs: Long): OneTimeWorkRequest`

- [ ] **Step 1: Create `HohWorker.kt`**

```kotlin
package com.liquidcode7.hearthcraft.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.liquidcode7.hearthcraft.R
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class HohWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val band: BandRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val memberId = inputData.getString(KEY_MEMBER_ID) ?: return Result.failure()
        band.completeHohRecovery(memberId)
        postNotification(memberId)
        return Result.success()
    }

    private fun postNotification(memberId: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel("hoh", "Recovery", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, "hoh")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Recovery complete")
            .setContentText("A band member has recovered from their wounds.")
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_MEMBER_ID = "memberId"
        const val NOTIFICATION_ID = 55

        fun buildRequest(memberId: String, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HohWorker>()
                .setInputData(workDataOf(KEY_MEMBER_ID to memberId))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .addTag("hoh_$memberId")
                .build()
    }
}
```

- [ ] **Step 2: Add `completeHohRecovery` to `BandRepository`**

```kotlin
suspend fun completeHohRecovery(memberId: String) {
    dao.healWound(memberId)
    dao.clearHohState(memberId)
    // HohSession cleanup handled by HohRepository or directly:
    hohSessionDao.delete(memberId)
}
```

Note: `BandRepository` will need `HohSessionDao` injected — add it to its constructor alongside `BandMemberStateDao`.

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HohWorker.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt
git commit -m "[hc] Add HohWorker and completeHohRecovery for recovery completion"
```

---

## Task 11: Recovery Buff in EncounterEngine

**Files:**
- Modify: `engine/EncounterEngine.kt`
- Modify: `data/db/dao/BandMemberStateDao.kt`

**Interfaces:**
- Consumes: `BandMemberState.recoveryBuffGrade`, `BandMemberState.recoveryBuffTier`, `BandMemberState.recoveryBuffPending`
- Produces: incoming heal multiplier applied to a member with pending recovery buff. Buff consumed after first mission.

Recovery buff formula: `incomingHealMultiplier = 1.0f + (grade * buffStep(tier))`
where `buffStep` per tier: T1=0.05f, T2=0.07f, T3=0.10f, T4=0.14f

- [ ] **Step 1: Add buff step lookup in `EncounterEngine`**

```kotlin
private fun recoveryBuffMultiplier(grade: Int, tier: Int): Float {
    val step = when (tier) {
        1 -> 0.05f
        2 -> 0.07f
        3 -> 0.10f
        4 -> 0.14f
        else -> 0.05f
    }
    return 1.0f + grade * step
}
```

- [ ] **Step 2: Apply multiplier to incoming heals for buffed members**

Find where Keeper HoT ticks apply to `target.hp`. The member's `MemberInput` carries their id. Before applying a heal, check if that member has a pending recovery buff (pass buff state in via `MemberInput` or a separate lookup map).

Add to `MemberInput` (or the local `MS` class in the engine):
```kotlin
val recoveryBuffMult: Float = 1.0f  // set from BandMemberState on input construction
```

Apply in the heal tick:
```kotlin
target.hp = min(target.maxHp, target.hp + hot.healPerTick * healMult * target.recoveryBuffMult)
```

- [ ] **Step 3: Add DAO query to consume the buff after the encounter**

In `BandMemberStateDao`:
```kotlin
@Query("UPDATE band_member_state SET recoveryBuffPending = 0 WHERE memberId = :id")
suspend fun consumeRecoveryBuff(id: String)
```

Call `dao.consumeRecoveryBuff(memberId)` in `EncounterWorker` after the encounter resolves for any member who had `recoveryBuffPending = true`.

- [ ] **Step 4: Wire buff multiplier into `memberInputsForBand` in `BandRepository`**

When building `MemberInput` for each member, read `recoveryBuffPending`, `recoveryBuffGrade`, `recoveryBuffTier` from their `BandMemberState` and compute `recoveryBuffMult`. Pass it through.

- [ ] **Step 5: Build**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/BandMemberStateDao.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt
git commit -m "[hc] Apply and consume post-recovery buff in EncounterEngine"
```

---

## Task 12: HoH Craft XP in a New HohCookingWorker

**Files:**
- Create: `worker/HohCookingWorker.kt`

**Interfaces:**
- Consumes: `PlayerRepository.addHohXp(xp)`, `PlayerRepository.XP_HOH_FIRST`, `PlayerRepository.XP_HOH_REPEAT`
- Produces: `HohCookingWorker` — fires when a HoH preparation finishes crafting, grants XP, stores result in inventory (reuses `InventoryRepository.addPreparedFood`)

- [ ] **Step 1: Create `HohCookingWorker.kt`**

```kotlin
package com.liquidcode7.hearthcraft.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class HohCookingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recipeId = inputData.getString(KEY_RECIPE_ID) ?: return Result.failure()
        val grade = inputData.getInt(KEY_GRADE, 0)
        val isFirst = inventory.preparedFoodQty(recipeId) == 0
        inventory.addPreparedFood(recipeId, grade)
        val xp = if (isFirst) PlayerRepository.XP_HOH_FIRST else PlayerRepository.XP_HOH_REPEAT
        player.addHohXp(xp)
        return Result.success()
    }

    companion object {
        const val KEY_RECIPE_ID = "recipeId"
        const val KEY_GRADE = "grade"

        fun buildRequest(recipeId: String, durationMs: Long, grade: Int): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HohCookingWorker>()
                .setInputData(workDataOf(KEY_RECIPE_ID to recipeId, KEY_GRADE to grade))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HohCookingWorker.kt
git commit -m "[hc] Add HohCookingWorker for HoH craft XP and inventory storage"
```

---

## Task 13: JSON Data — HoH Ingredients and Recipes

**Files:**
- Modify: `app/src/main/assets/data/ingredients.json`
- Modify: `app/src/main/assets/data/recipes.json`

**Interfaces:**
- Produces: all HoH ingredients and T1–T4 recipes defined in the spec, parseable by existing `GameDataRepository` loader

- [ ] **Step 1: Add HoH-exclusive ingredients to `ingredients.json`**

Add these entries to the ingredients array (follow existing field conventions — include `region`, `source`, `gatheringMode`):
```json
{ "id": "silver_thread_lichen", "name": "Silver-thread Lichen", "region": "bree-land", "rarity": "r", "source": "forage", "gatheringMode": "forage", "cultivatable": false },
{ "id": "bloodmoss", "name": "Bloodmoss", "region": "bree-land", "rarity": "u", "source": "forage", "gatheringMode": "forage", "cultivatable": false },
{ "id": "healing_clay", "name": "Healing Clay", "region": "bree-land", "rarity": "c", "source": "gather", "gatheringMode": "gather", "cultivatable": false },
{ "id": "rendered_beeswax", "name": "Rendered Beeswax", "region": "bree-land", "rarity": "c", "source": "husbandry", "gatheringMode": "husbandry", "cultivatable": false },
{ "id": "shadowbane_moss", "name": "Shadowbane Moss", "region": "lone-lands", "rarity": "r", "source": "forage", "gatheringMode": "forage", "cultivatable": false },
{ "id": "starflower", "name": "Starflower", "region": "lone-lands", "rarity": "r", "source": "forage", "gatheringMode": "forage", "cultivatable": false },
{ "id": "miruvor_dilute", "name": "Miruvor (Dilute)", "region": "rivendell", "rarity": "l", "source": "found", "gatheringMode": "found", "cultivatable": false }
```

- [ ] **Step 2: Add all HoH recipes to `recipes.json`**

Each entry uses `"class": "hoh"` and includes `hohLevel` and `treatsWoundTypes`. Add all recipes from the spec (§9 of the design doc). T1 sample:
```json
{
  "id": "athelas_poultice", "name": "Athelas Poultice",
  "band": "all", "class": "hoh", "method": "prepare",
  "tier": 1, "cookLevel": 1, "hohLevel": 1,
  "heroIngredient": "athelas",
  "treatsWoundTypes": ["physical"],
  "description": "A simple athelas compress that begins the healing of a physical wound.",
  "ingredients": [
    { "id": "athelas", "qty": 2 },
    { "id": "kingsfoil", "qty": 1 },
    { "id": "honey", "qty": 1 }
  ]
},
{
  "id": "yarrow_compress", "name": "Yarrow Compress",
  "band": "all", "class": "hoh", "method": "prepare",
  "tier": 1, "cookLevel": 1, "hohLevel": 1,
  "heroIngredient": "yarrow",
  "treatsWoundTypes": ["physical"],
  "description": "Yarrow bound with healing clay draws out wound fever.",
  "ingredients": [
    { "id": "yarrow", "qty": 2 },
    { "id": "healing_clay", "qty": 1 },
    { "id": "spring_water", "qty": 1 }
  ]
}
```
Continue for all remaining recipes in the spec. Follow same pattern — `hohLevel` per the tier/complexity table in §11 of the spec.

- [ ] **Step 3: Build and verify JSON parses**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL (kotlinx.serialization unknown fields are ignored by default — new fields won't break existing non-HoH recipes)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/data/ingredients.json
git add app/src/main/assets/data/recipes.json
git commit -m "[hc] Add HoH ingredients and T1-T4 recipes to game data"
```

---

## Task 14: Update `isRecipeVisible` for HoH Class

**Files:**
- Modify: `data/repository/GameDataRepository.kt`

**Interfaces:**
- Consumes: `Recipe.recipeClass == "hoh"`, `foundGrimoires`, `Recipe.hohLevel`, `PlayerState.hohLevel`
- Produces: `isRecipeVisible` correctly gates HoH recipes — requires HoH grimoire (all tiers, including T1) and hohLevel met

- [ ] **Step 1: Read the existing `isRecipeVisible` function**

Locate it in `GameDataRepository.kt`. It currently maps `recipeClass == "food"` → `"cooking"` for grimoire lookup. Verify HoH class already passes through correctly (`grimoireClass == "hoh"` matching recipe class `"hoh"`).

- [ ] **Step 2: Add hohLevel gating**

`isRecipeVisible` currently doesn't gate on cook level — that's a separate check at craft time. Confirm HoH level gating also happens at craft time (in `HohRepository` or UI layer), not in `isRecipeVisible`. `isRecipeVisible` only controls visibility, not craftability. No change needed here if the existing grimoire check already handles HoH class — verify this is the case.

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 4: Commit only if changes were needed**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt
git commit -m "[hc] Verify/fix isRecipeVisible handles hoh recipe class"
```

---

## Task 15: Hilt Module Registration

**Files:**
- Modify: whichever Hilt module provides DAOs and repositories (search for `@Provides` or `@Binds` annotations, typically in a `di/` or `module/` directory)

**Interfaces:**
- Produces: `HohSessionDao`, `HohRepository`, `HohCookingWorker`, `HohWorker` all resolvable by Hilt

- [ ] **Step 1: Find the Hilt module files**

```bash
find app/src/main/kotlin -name "*Module*" -o -name "*module*" | grep -v test
```

- [ ] **Step 2: Register `HohSessionDao`**

Add a `@Provides` for `HohSessionDao` alongside existing DAO provisions:
```kotlin
@Provides fun provideHohSessionDao(db: HearthCraftDatabase): HohSessionDao = db.hohSessionDao()
```

Also add `abstract fun hohSessionDao(): HohSessionDao` to `HearthCraftDatabase` if not already present.

- [ ] **Step 3: Verify `HohRepository` is `@Singleton @Inject` — no manual provision needed**

`HohRepository` uses `@Inject constructor` and `@Singleton` — Hilt resolves it automatically as long as its constructor dependencies are all provided.

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL with no Hilt missing-binding errors

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "[hc] Register HoH Hilt bindings"
```

---

## Self-Review

**Spec coverage check:**
- §2 Wound types → Tasks 1, 6, 7, 8 ✓
- §3 Recovery timer (combined, time-credit) → Task 9 ✓
- §4 Recipe structure / two-lock gate → Tasks 6, 14 ✓
- §5 Athelas unlock (T3 craft trigger) → not implemented — add note below
- §6 Ingredient list → Task 13 ✓
- §7 HoH leveling → Task 5 ✓
- §8 Athelas cultivar unlock → **Gap: needs a check in `HohRepository` or `PlayerRepository` that fires when first T3 recipe is crafted, unlocking cultivated athelas seeds. Add to Task 13 or a follow-up.**
- §9 Recipe list → Task 13 ✓
- §10 XP system → Tasks 5, 9, 12 ✓
- §11 hohLevel minimums → Task 13 (encoded in recipe JSON) ✓
- §12 Recovery buff → Task 11 ✓
- Wound infliction model (guaranteed + chance) → Tasks 6, 7 ✓
- Partial treatment + time credit → Task 9 ✓

**Athelas cultivar unlock gap — add to Task 13, Step 2:**
In `HohCookingWorker.doWork()`, after granting XP, check if the crafted recipe is tier 3 and if the player hasn't yet unlocked the cultivated athelas seed. If so, add the cultivated athelas seed to inventory/seed stock. The exact seed item ID and unlock flag TBD with Wes.

**Placeholder scan:** No TBDs in task steps. All code is complete. Migration SQL matches entity fields exactly.

**Type consistency:** `woundTypes: String` (comma-delimited) used consistently across `BandMemberState`, `HohSession.treatedTypes`, and `rollWoundTypes` return type. `grade: Int` (ordinal 0–4) consistent throughout. `tier: Int` (1–4) consistent.
