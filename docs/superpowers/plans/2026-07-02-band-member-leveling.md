# Band Member Leveling System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a real per-member combat leveling system (XP → level → computed stats via role-based growth curves) in the Kotlin game and design doc, replacing the current flat +1-per-mission stat grant, so the game's progression model is something the (separately planned) sim rewire can actually be validated against.

**Architecture:** New pure functions in `data/model/CombatLeveling.kt` compute a member's current stats from `(startingStats, growthCurve, combatXp)` — no Android dependency, directly unit-testable. `BandMemberState` drops its five stored stat columns in favor of one `combatXp: Int`, via a manual Room migration (table rebuild) matching this project's existing `Migration14To15` pattern. `BandRepository` and `BandViewModel` both consume the new pure functions to compute stats on demand, replacing their previous "read stored stat column" logic. A new Fighter melee/ranged build choice is added at character creation, stored on `PlayerState`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, Hilt, kotlinx.serialization, JUnit

**Spec:** `docs/superpowers/specs/2026-07-02-band-member-leveling-design.md`

## Global Constraints

- All Kotlin source under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- Commit prefix: `[hc]`
- Build must pass: `./gradlew build` before every commit — never commit a broken build
- No new libraries — use existing dependencies only
- GPL-3.0
- Growth rates and the XP curve constants are explicit placeholders (see spec) — implement them as specified, do not "improve" or re-derive them from any other source.

---

### Task 1: `CombatLeveling` pure functions, `GrowthCurve` model, `growth_curves.json`

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/GrowthCurve.kt`
- Create: `app/src/main/assets/data/growth_curves.json`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLeveling.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLevelingTest.kt` (new)

**Interfaces:**
- Produces: `GrowthCurve` data class (`role: String`, `migGrowth/agiGrowth/vitGrowth/wilGrowth/fatGrowth: Float`); `GameDataRepository.growthCurves: List<GrowthCurve>`; `statAtLevel(startingStat: Int, growthRate: Float, level: Int): Float`; `growthCurveKeyForRole(role: String, fighterBuild: String): String`; `xpToNextCombatLevel(level: Int): Int`; `levelForCombatXp(xp: Int): Int`; `COMBAT_MAX_LEVEL: Int` (=50). Consumed by Tasks 3 and 6.

- [ ] **Step 1: Create `growth_curves.json`**

Create `app/src/main/assets/data/growth_curves.json`:

```json
[
  { "role": "warden",         "migGrowth": 0.10, "agiGrowth": 0.05, "vitGrowth": 0.20, "wilGrowth": 0.05, "fatGrowth": 0.05 },
  { "role": "keeper",         "migGrowth": 0.05, "agiGrowth": 0.05, "vitGrowth": 0.10, "wilGrowth": 0.20, "fatGrowth": 0.05 },
  { "role": "captain",        "migGrowth": 0.05, "agiGrowth": 0.05, "vitGrowth": 0.10, "wilGrowth": 0.20, "fatGrowth": 0.10 },
  { "role": "fighter_ranged", "migGrowth": 0.05, "agiGrowth": 0.20, "vitGrowth": 0.05, "wilGrowth": 0.05, "fatGrowth": 0.10 },
  { "role": "fighter_melee",  "migGrowth": 0.20, "agiGrowth": 0.05, "vitGrowth": 0.05, "wilGrowth": 0.05, "fatGrowth": 0.10 }
]
```

- [ ] **Step 2: Create the `GrowthCurve` data class**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/GrowthCurve.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GrowthCurve(
    val role: String,
    val migGrowth: Float,
    val agiGrowth: Float,
    val vitGrowth: Float,
    val wilGrowth: Float,
    val fatGrowth: Float
)
```

- [ ] **Step 3: Add `growthCurves` to `GameDataRepository`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt` first.

Add the import:
```kotlin
import com.liquidcode7.hearthcraft.data.model.GrowthCurve
```

Add this property alongside the other `by lazy { load(...) }` properties (after `val grimoires`):
```kotlin
    val growthCurves: List<GrowthCurve> by lazy { load("growth_curves.json") }
```

- [ ] **Step 4: Write failing tests for the `CombatLeveling` pure functions**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLevelingTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.model

import org.junit.Assert.*
import org.junit.Test

class CombatLevelingTest {

    @Test
    fun `statAtLevel at level 1 returns the starting stat unchanged`() {
        assertEquals(5.0f, statAtLevel(startingStat = 5, growthRate = 0.20f, level = 1), 0.0001f)
    }

    @Test
    fun `statAtLevel grows linearly with level`() {
        // level 11: 10 levels above 1, growth 0.20/level -> +2.0
        assertEquals(7.0f, statAtLevel(startingStat = 5, growthRate = 0.20f, level = 11), 0.0001f)
    }

    @Test
    fun `growthCurveKeyForRole passes non-fighter roles through as lowercase`() {
        assertEquals("warden", growthCurveKeyForRole(role = "Warden", fighterBuild = "ranged"))
        assertEquals("keeper", growthCurveKeyForRole(role = "keeper", fighterBuild = "melee"))
    }

    @Test
    fun `growthCurveKeyForRole appends fighter build for the fighter role`() {
        assertEquals("fighter_ranged", growthCurveKeyForRole(role = "fighter", fighterBuild = "ranged"))
        assertEquals("fighter_melee", growthCurveKeyForRole(role = "Fighter", fighterBuild = "melee"))
    }

    @Test
    fun `levelForCombatXp is 1 at zero xp`() {
        assertEquals(1, levelForCombatXp(0))
    }

    @Test
    fun `levelForCombatXp increases as xp increases`() {
        val lowXpLevel = levelForCombatXp(0)
        val highXpLevel = levelForCombatXp(50_000)
        assertTrue(highXpLevel > lowXpLevel)
    }

    @Test
    fun `levelForCombatXp never exceeds the level cap`() {
        assertEquals(COMBAT_MAX_LEVEL, levelForCombatXp(Int.MAX_VALUE / 2))
    }

    @Test
    fun `xpToNextCombatLevel returns a positive amount below the cap`() {
        assertTrue(xpToNextCombatLevel(1) > 0)
        assertTrue(xpToNextCombatLevel(25) > 0)
    }
}
```

- [ ] **Step 5: Run to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.data.model.CombatLevelingTest"
```
Expected: FAIL — `CombatLeveling.kt` doesn't exist yet.

- [ ] **Step 6: Implement `CombatLeveling.kt`**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLeveling.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.model

import kotlin.math.pow
import kotlin.math.roundToInt

const val COMBAT_MAX_LEVEL = 50

// A member's stat at a given level: starting value plus a flat per-level growth rate.
// Growth rates are per-role placeholders (see growth_curves.json) — not final-balanced.
fun statAtLevel(startingStat: Int, growthRate: Float, level: Int): Float =
    startingStat + growthRate * (level - 1)

// Fighter has two growth-curve variants (melee/ranged); every other role has one.
fun growthCurveKeyForRole(role: String, fighterBuild: String): String {
    val lower = role.lowercase()
    return if (lower == "fighter") "fighter_$fighterBuild" else lower
}

// Combat XP curve — same shape as Gathering's (base = A * level^P), a placeholder
// pending validation via the sim rewire. Mirrors PlayerRepository.Companion's
// xpToNext/levelForTotalXp pattern, scoped separately since combat XP lives on
// BandMemberState (per member), not PlayerState.
fun xpToNextCombatLevel(level: Int): Int {
    if (level >= COMBAT_MAX_LEVEL) return Int.MAX_VALUE
    return maxOf(1, (20.0 * level.toDouble().pow(1.35)).roundToInt())
}

private val COMBAT_XP_THRESHOLDS: IntArray = IntArray(COMBAT_MAX_LEVEL) { i ->
    var total = 0; for (l in 1..i) total += xpToNextCombatLevel(l); total
}

fun levelForCombatXp(xp: Int): Int {
    var lo = 0; var hi = COMBAT_MAX_LEVEL - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (COMBAT_XP_THRESHOLDS[mid] <= xp) lo = mid else hi = mid - 1
    }
    return lo + 1
}
```

- [ ] **Step 7: Run to verify tests pass**

```bash
./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.data.model.CombatLevelingTest"
```
Expected: 8 tests, all PASS.

- [ ] **Step 8: Build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/assets/data/growth_curves.json
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/GrowthCurve.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLeveling.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt
git add app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLevelingTest.kt
git commit -m "[hc] Add CombatLeveling pure functions, GrowthCurve model, growth_curves.json"
```

---

### Task 2: Schema migration — `combatXp`, drop stored stat columns, `fighterBuild`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/BandMemberState.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PlayerState.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/BandMemberStateDao.kt`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration15To16.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt`

**Interfaces:**
- Produces: `BandMemberState.combatXp: Int`; `PlayerState.fighterBuild: String` (default `"ranged"`); `BandMemberStateDao.addCombatXp(memberId: String, xp: Int)`. Consumed by Task 3.
- Removes: `BandMemberState.might/agility/vitality/will/fate`; `BandMemberStateDao.grantStats(...)` (its only caller, `BandRepository.grantMissionStats`, is replaced in Task 3).

This task deliberately leaves the module non-compiling — `BandRepository.kt` (Task 3) still references the fields/method being removed here. Do not attempt to fix `BandRepository.kt` in this task; that's Task 3's job.

- [ ] **Step 1: Update `BandMemberState`**

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
    val recoveryBuffPending: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val combatXp: Int = 0
)
```

- [ ] **Step 2: Add `fighterBuild` to `PlayerState`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PlayerState.kt` first.

Add this field at the end of the constructor, after `val foundGrimoireIds: String = "",`:
```kotlin
    @ColumnInfo(defaultValue = "ranged")
    val fighterBuild: String = "ranged",
```

- [ ] **Step 3: Update `BandMemberStateDao`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/BandMemberStateDao.kt` first.

Replace:
```kotlin
    @Query("UPDATE band_member_state SET vitality = vitality + :vitality, might = might + :might WHERE memberId = :memberId AND isAlive = 1")
    suspend fun grantStats(memberId: String, vitality: Int, might: Int)
```
with:
```kotlin
    @Query("UPDATE band_member_state SET combatXp = combatXp + :xp WHERE memberId = :memberId AND isAlive = 1")
    suspend fun addCombatXp(memberId: String, xp: Int)
```

- [ ] **Step 4: Create the manual migration**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration15To16.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 15 → 16: band-member combat leveling.
 *
 * band_member_state: drops the five stored stat columns (might, agility,
 * vitality, will, fate) — stats are now computed from level, not stored —
 * and adds combatXp. Column removal requires a table rebuild (SQLite's
 * ALTER TABLE ... DROP COLUMN support varies by version on Android), so
 * this creates a new table with the surviving columns, copies data across,
 * then swaps it in for the old one.
 *
 * player_state: adds fighterBuild (additive only, included in this same
 * migration rather than a separate AutoMigration since the whole version
 * bump must use one migration strategy).
 */
val Migration15To16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── band_member_state: rebuild without might/agility/vitality/will/fate ──
        db.execSQL("""
            CREATE TABLE band_member_state_new (
                memberId TEXT NOT NULL PRIMARY KEY,
                isAlive INTEGER NOT NULL,
                woundStatus TEXT NOT NULL,
                woundedSinceMs INTEGER NOT NULL,
                woundedDurationMs INTEGER NOT NULL DEFAULT 0,
                woundTypes TEXT NOT NULL DEFAULT '',
                hohTimerStartMs INTEGER NOT NULL DEFAULT 0,
                hohTimerDurationMs INTEGER NOT NULL DEFAULT 0,
                recoveryBuffGrade INTEGER NOT NULL DEFAULT 0,
                recoveryBuffTier INTEGER NOT NULL DEFAULT 0,
                recoveryBuffPending INTEGER NOT NULL DEFAULT 0,
                combatXp INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO band_member_state_new (
                memberId, isAlive, woundStatus, woundedSinceMs, woundedDurationMs,
                woundTypes, hohTimerStartMs, hohTimerDurationMs,
                recoveryBuffGrade, recoveryBuffTier, recoveryBuffPending, combatXp
            )
            SELECT
                memberId, isAlive, woundStatus, woundedSinceMs, woundedDurationMs,
                woundTypes, hohTimerStartMs, hohTimerDurationMs,
                recoveryBuffGrade, recoveryBuffTier, recoveryBuffPending, 0
            FROM band_member_state
        """.trimIndent())
        db.execSQL("DROP TABLE band_member_state")
        db.execSQL("ALTER TABLE band_member_state_new RENAME TO band_member_state")

        // ── player_state: additive column ──────────────────────────────────────
        db.execSQL("ALTER TABLE player_state ADD COLUMN fighterBuild TEXT NOT NULL DEFAULT 'ranged'")
    }
}
```

- [ ] **Step 5: Bump the version and wire the migration**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt` first.

Change `version = 15` to `version = 16` (`@Database(... version = 16, ...)`).

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt` first — manual migrations are wired here, not in `HearthCraftDatabase.kt`.

Add the import:
```kotlin
import com.liquidcode7.hearthcraft.data.db.Migration15To16
```

Replace:
```kotlin
            .addMigrations(MIGRATION_10_11, Migration14To15)
```
with:
```kotlin
            .addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16)
```

- [ ] **Step 6: Build to generate and validate the new schema**

```bash
./gradlew build
```
Expected: BUILD FAILS — `BandRepository.kt` still references the removed `might`/`agility`/`vitality`/`will`/`fate` fields and the removed `grantStats`/`grantMissionStats`. Confirm the failure is specifically about these missing members, not something else (e.g. a typo in the migration SQL). This is expected; Task 3 fixes the only caller.

- [ ] **Step 7: Do NOT commit yet — hold this change uncommitted**

Task 3 will commit this together with its own changes, once the module compiles again.

---

### Task 3: `BandRepository` — computed stats, `grantCombatXp`, `PlayerRepository` dependency

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt`

**Interfaces:**
- Consumes: `statAtLevel`, `growthCurveKeyForRole`, `levelForCombatXp` (Task 1); `BandMemberState.combatXp`, `PlayerState.fighterBuild` (Task 2); `BandMemberStateDao.addCombatXp` (Task 2).
- Produces: `BandRepository.grantCombatXp(bandId: String, xp: Int)` (replaces `grantMissionStats`). Consumed by Task 4.

This task fixes the compile break Task 2 deliberately left behind.

- [ ] **Step 1: Update `BandRepository`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt` in full first.

Add these imports:
```kotlin
import com.liquidcode7.hearthcraft.data.model.GrowthCurve
import com.liquidcode7.hearthcraft.data.model.growthCurveKeyForRole
import com.liquidcode7.hearthcraft.data.model.levelForCombatXp
import com.liquidcode7.hearthcraft.data.model.statAtLevel
import com.liquidcode7.hearthcraft.data.model.BandMember
```

Add `player: PlayerRepository` to the constructor:
```kotlin
@Singleton
class BandRepository @Inject constructor(
    private val dao: BandMemberStateDao,
    private val hohSessionDao: HohSessionDao,
    private val gameData: GameDataRepository,
    private val player: PlayerRepository
) {
```

Replace `initMembers` (it no longer needs to seed stat columns — they're computed, not stored):
```kotlin
    suspend fun initMembers(bandId: String) {
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .forEach { member ->
                dao.upsert(BandMemberState(memberId = member.id))
            }
    }
```

Replace `grantMissionStats`:
```kotlin
    suspend fun grantCombatXp(bandId: String, xp: Int) {
        aliveMemberIds(bandId).forEach { id ->
            dao.addCombatXp(memberId = id, xp = xp)
        }
    }
```

Add a private helper that resolves a member's five current stats — this is the single place `memberInputsForBand` and `maxVitality` both go through:
```kotlin
    private suspend fun growthCurveFor(member: BandMember): GrowthCurve {
        val fighterBuild = player.get()?.fighterBuild ?: "ranged"
        val key = growthCurveKeyForRole(member.role, fighterBuild)
        return gameData.growthCurves.find { it.role == key }
            ?: error("No growth curve found for role/build key '$key'")
    }

    private suspend fun currentStats(member: BandMember, state: BandMemberState?): FloatArray {
        val curve = growthCurveFor(member)
        val level = levelForCombatXp(state?.combatXp ?: 0)
        return floatArrayOf(
            statAtLevel(member.startingMight,    curve.migGrowth, level),
            statAtLevel(member.startingAgility,  curve.agiGrowth, level),
            statAtLevel(member.startingVitality, curve.vitGrowth, level),
            statAtLevel(member.startingWill,     curve.wilGrowth, level),
            statAtLevel(member.startingFate,     curve.fatGrowth, level)
        )
    }
```

Replace `maxVitality`:
```kotlin
    suspend fun maxVitality(bandId: String): Int =
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .mapNotNull { member ->
                val state = dao.get(member.id)
                if (state?.isAlive == false) null else currentStats(member, state)[2].toInt()
            }
            .maxOrNull() ?: 0
```

Replace the body of `memberInputsForBand`'s `.map { member -> ... }` block — the `MemberInput(...)` construction changes from reading stored columns to using `currentStats`:
```kotlin
    suspend fun memberInputsForBand(
        bandId: String,
        draughtPotency: Float,
        memberFood: Map<String, PreparedFoodDetail?>
    ): List<MemberInput> {
        val roleOrder = listOf("warden", "fighter", "keeper", "captain")
        return gameData.bandMembers
            .filter { it.bandId == bandId }
            .sortedBy { roleOrder.indexOf(it.role.lowercase()) }
            .map { member ->
                val state  = dao.get(member.id)
                val stats  = currentStats(member, state)
                val food   = memberFood[member.id]
                val bonus  = { stat: String ->
                    if (food != null) {
                        val base = statBonusFor(stat, food.primaryStat, food.primaryBoost,
                                                food.secondaryStat, food.secondaryBoost)
                        val step = if (base > 0f) gradeStep(Grade.fromOrdinal(food.grade)) else 0f
                        base + step
                    } else 0f
                }
                MemberInput(
                    id             = member.id,
                    role           = member.role.lowercase(),
                    might          = stats[0] + bonus("mig"),
                    agility        = stats[1] + bonus("agi"),
                    vitality       = stats[2] + bonus("vit"),
                    will           = stats[3] + bonus("wil"),
                    fate           = stats[4],
                    draughtPotency = draughtPotency,
                    recoveryBuffMult = if (state?.recoveryBuffPending == true)
                        EncounterEngine.recoveryBuffMultiplier(state.recoveryBuffGrade, state.recoveryBuffTier)
                    else 1.0f
                )
            }
    }
```

Leave `observeMemberStates`, `woundMember`, `healWound`, `completeHohRecovery`, `consumeRecoveryBuffs`, `aliveMemberIds`, and the `statBonusFor` companion function unchanged.

- [ ] **Step 2: Build**

```bash
./gradlew build
```
Expected: BUILD FAILS — `EncounterWorker.kt` still calls the now-removed `grantMissionStats`. Confirm the failure is specifically that, not anything else. This is expected; Task 4 fixes it.

- [ ] **Step 3: Do NOT commit yet — hold this change uncommitted**

Task 4 will commit this together with its own change.

---

### Task 4: `EncounterWorker` — grant combat XP on Victory/Stalemate

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt`

**Interfaces:**
- Consumes: `BandRepository.grantCombatXp(bandId: String, xp: Int)` (Task 3).

This task fixes the compile break Task 3 left behind, and completes the loop back to a compiling, committable state.

- [ ] **Step 1: Update the outcome branches**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt` in full first — find the `"VICTORY"`, `"STALEMATE"`, and `else` (DEFEAT) branches (around lines 94-118).

Replace:
```kotlin
                band.grantMissionStats(bandId, succeeded = true)
```
(inside the `"VICTORY"` branch) with:
```kotlin
                band.grantCombatXp(bandId, xp = 40)
```

Replace:
```kotlin
                band.grantMissionStats(bandId, succeeded = false)
```
(inside the `"STALEMATE"` branch) with:
```kotlin
                band.grantCombatXp(bandId, xp = 15)
```

Replace the third occurrence of:
```kotlin
                band.grantMissionStats(bandId, succeeded = false)
```
(inside the `else` / DEFEAT branch) — **delete this line entirely** (no `grantCombatXp` call at all; defeat grants zero combat XP per the spec's "no reward on defeat" pattern, so there's nothing to call).

- [ ] **Step 2: Build — this is the first point the module compiles again**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL, all tests pass (including the new `CombatLevelingTest` from Task 1).

- [ ] **Step 4: Commit everything from Tasks 2-4 together**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/BandMemberState.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PlayerState.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/BandMemberStateDao.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration15To16.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt
git add "app/schemas/com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase/16.json"
git commit -m "[hc] Replace flat stat grants with combat XP/level system (schema 15->16)"
```

---

### Task 5: Fighter build choice at character creation

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandSelectionViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandSelectionScreen.kt`

**Interfaces:**
- Produces: `PlayerRepository.init(bandId: String, fighterBuild: String)` — signature change (was `init(bandId: String)`).

- [ ] **Step 1: Update `PlayerRepository.init`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt` first.

Replace:
```kotlin
    suspend fun init(bandId: String) {
        dao.upsert(PlayerState(chosenBandId = bandId))
    }
```
with:
```kotlin
    suspend fun init(bandId: String, fighterBuild: String = "ranged") {
        dao.upsert(PlayerState(chosenBandId = bandId, fighterBuild = fighterBuild))
    }
```

- [ ] **Step 2: Add fighter build state to `BandSelectionViewModel`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandSelectionViewModel.kt` in full first.

Add a field near `_firstBandId` (follow this file's existing `MutableStateFlow`/`StateFlow` pattern for `_firstBandId`/`firstBandId`):
```kotlin
    private val _fighterBuild = MutableStateFlow("ranged")
    val fighterBuild: StateFlow<String> = _fighterBuild.asStateFlow()

    fun selectFighterBuild(build: String) { _fighterBuild.value = build }
```
`MutableStateFlow`, `StateFlow`, and `asStateFlow` are already imported in this file (used for `_firstBandId`/`firstBandId`) — no new imports needed for this step.

Update `confirmSelection()`:
```kotlin
    fun confirmSelection() {
        val first = _firstBandId.value ?: return
        viewModelScope.launch {
            player.init(first, _fighterBuild.value)
            band.initMembers(first)
            giveStarterSeeds()
            _navigateToMain.emit(Unit)
        }
    }
```

- [ ] **Step 3: Add the build choice UI to `BandSelectionScreen`'s `WelcomePage`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandSelectionScreen.kt` in full first.

Add these imports (none of the three are currently present in this file):
```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
```

Update the call site in `BandSelectionScreen` (the `when (page)` block) to pass the new state through:
```kotlin
        2 -> WelcomePage(
            firstBandId = firstBandId ?: "",
            firstName = viewModel.bands.find { it.id == firstBandId }?.name ?: "",
            fighterBuild = viewModel.fighterBuild.collectAsState().value,
            onFighterBuildSelected = { viewModel.selectFighterBuild(it) },
            onEnter = { viewModel.confirmSelection() }
        )
```

Update `WelcomePage`'s signature and body:
```kotlin
private fun WelcomePage(
    firstBandId: String,
    firstName: String,
    fighterBuild: String,
    onFighterBuildSelected: (String) -> Unit,
    onEnter: () -> Unit
) {
    val (quote, speaker) = welcomeFor(firstBandId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(firstName, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "The first watch begins.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("\"$quote\"", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "— $speaker",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (firstBandId == "greycloaks") {
            Spacer(modifier = Modifier.height(24.dp))
            Text("How does your Fighter fight?", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                FilterChip(
                    selected = fighterBuild == "ranged",
                    onClick = { onFighterBuildSelected("ranged") },
                    label = { Text("Ranged") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = fighterBuild == "melee",
                    onClick = { onFighterBuildSelected("melee") },
                    label = { Text("Melee") }
                )
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onEnter, modifier = Modifier.fillMaxWidth()) {
            Text("Enter")
        }
    }
}
```

- [ ] **Step 4: Build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandSelectionViewModel.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandSelectionScreen.kt
git commit -m "[hc] Add Fighter melee/ranged build choice at character creation"
```

---

### Task 6: UI — show member level, compute stats via the new system

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt`

**Interfaces:**
- Consumes: `statAtLevel`, `growthCurveKeyForRole`, `levelForCombatXp` (Task 1).
- Produces: `BandMemberWithState.level: Int` (new field).

- [ ] **Step 1: Add `level` to `BandMemberWithState`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt` first.

Add `val level: Int = 1,` to the `BandMemberWithState` data class, directly after `val woundStatus: String = "healthy",` and before `val woundedSinceMs: Long = 0L,`.

- [ ] **Step 2: Compute stats and level in `BandViewModel.members`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt` in full first — find the `members` `StateFlow` block (the `combine(band.observeMemberStates(), activeBandId) { states, bandId -> ... }` block).

Add these imports (none are currently present in this file):
```kotlin
import com.liquidcode7.hearthcraft.data.model.growthCurveKeyForRole
import com.liquidcode7.hearthcraft.data.model.levelForCombatXp
import com.liquidcode7.hearthcraft.data.model.statAtLevel
import kotlin.math.roundToInt
```

Replace the `members` block's body. The `combine` needs `playerState` added as a third source so it can read `fighterBuild`, and each member's stats are now computed rather than read from stored columns:
```kotlin
    val members: StateFlow<List<BandMemberWithState>> = combine(
        band.observeMemberStates(),
        activeBandId,
        playerState
    ) { states, bandId, pState ->
        if (bandId == null) return@combine emptyList()
        val fighterBuild = pState?.fighterBuild ?: "ranged"
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .map { member ->
                val state = states.find { it.memberId == member.id }
                val level = levelForCombatXp(state?.combatXp ?: 0)
                val curveKey = growthCurveKeyForRole(member.role, fighterBuild)
                val curve = gameData.growthCurves.find { it.role == curveKey }
                BandMemberWithState(
                    memberId = member.id,
                    name = member.name,
                    personality = member.personality,
                    foodPreference = member.foodPreference,
                    quirkNote = member.quirkNote,
                    role = member.role,
                    isAlive = state?.isAlive != false,
                    woundStatus = state?.woundStatus ?: "healthy",
                    level = level,
                    woundedSinceMs = state?.woundedSinceMs ?: 0L,
                    woundedDurationMs = state?.woundedDurationMs ?: 0L,
                    might = curve?.let { statAtLevel(member.startingMight, it.migGrowth, level) }?.roundToInt() ?: member.startingMight,
                    agility = curve?.let { statAtLevel(member.startingAgility, it.agiGrowth, level) }?.roundToInt() ?: member.startingAgility,
                    vitality = curve?.let { statAtLevel(member.startingVitality, it.vitGrowth, level) }?.roundToInt() ?: member.startingVitality,
                    will = curve?.let { statAtLevel(member.startingWill, it.wilGrowth, level) }?.roundToInt() ?: member.startingWill,
                    fate = curve?.let { statAtLevel(member.startingFate, it.fatGrowth, level) }?.roundToInt() ?: member.startingFate
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

Note: `playerState` is already defined earlier in this file (`private val playerState = player.observe().stateIn(...)`) — it's used as-is here, not redefined.

- [ ] **Step 3: Show level in `MemberDetailDialog`**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt` first — find `MemberDetailDialog`.

Replace:
```kotlin
                if (member.role.isNotEmpty()) {
                    Text(
                        member.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
```
with:
```kotlin
                if (member.role.isNotEmpty()) {
                    Text(
                        "${member.role} — Level ${member.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
```

- [ ] **Step 4: Build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt
git commit -m "[hc] Show computed level/stats in Band UI"
```

---

### Task 7: Update `design/master-design.md`

**Files:**
- Modify: `design/master-design.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Add a leveling subsection to §4 (Stats)**

Read `design/master-design.md` first, specifically §4 ("Stats", ends before the `---` preceding `## 5. Band Roles`).

Replace:
```markdown
**Fate food does not exist and will never exist.** Feeding Fate would make the
streak system untunable. This is a hard rule.

---
```
with:
```markdown
**Fate food does not exist and will never exist.** Feeding Fate would make the
streak system untunable. This is a hard rule.

### 4.1 Leveling

Each band member has their own combat level (cap **50**), driven by combat XP
earned per mission: Victory grants XP, Stalemate grants a smaller amount,
Defeat grants none — the same "no reward on defeat" shape already used for
mission rewards.

A member's stat at their current level is `startingStat + growthRate ×
(level − 1)`. Growth rates are **per role**, not per named individual, and
follow each role's documented primary/secondary stats above: the primary
stat grows fastest, secondary stat(s) grow at half that rate, other stats
grow slowly. These are placeholder magnitudes pending balance validation via
the sim, not locked numbers.

---
```

- [ ] **Step 2: Update §5.4 (Fighter) with the melee/ranged split**

Read §5.4 ("Fighter") in the same file.

Replace:
```markdown
### 5.4 Fighter

- **Primary stat:** Agility
- **Secondary stat:** Fate
- **Damage type:** Physical
- **Identity:** Pure DPS. High Fate secondary means they trigger streaks often —
  but so would any member with equivalent Fate. High Fate is a character trait,
  not a role privilege.
```
with:
```markdown
### 5.4 Fighter

Two builds, same secondary stat:

- **Ranged** — Primary: Agility. Secondary: Fate.
- **Melee** — Primary: Might. Secondary: Fate.
- **Damage type:** Physical (both builds)
- **Identity:** Pure DPS. High Fate secondary means they trigger streaks often —
  but so would any member with equivalent Fate. High Fate is a character trait,
  not a role privilege.

For Men (Greycloaks), the player chooses melee or ranged once, at character
creation — permanent for that save. Elves and Dwarves are pending redesign
(§3); their Fighter build, if any, isn't decided yet.
```

- [ ] **Step 3: Diff review**

```bash
git diff design/master-design.md
```
Confirm both additions are present and nothing else changed.

- [ ] **Step 4: Commit**

```bash
git add design/master-design.md
git commit -m "[hc] Document band member leveling and Fighter melee/ranged split in design doc"
```

---

### Task 8: Full build, test suite verification

**Files:** None — verification only.

- [ ] **Step 1: Full test suite**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL, all tests pass (including the 8 new `CombatLevelingTest` cases from Task 1).

- [ ] **Step 2: Full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Report back**

Summarize to Wes: confirm all tests pass and the build is clean. Note that the growth rates and XP curve are explicit placeholders pending sim-based validation (per the spec), and that this unblocks the follow-on sim-rewire spec.
