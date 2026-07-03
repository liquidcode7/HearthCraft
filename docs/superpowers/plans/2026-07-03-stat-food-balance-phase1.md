# Stat Growth, Grade Curve & Balance Harness — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the core mechanics from the stat-growth-and-food-power
design (compound stat growth, multiplicative grade curve, boss difficulty
tier) and build a JVM-native balance test harness that calls the real
`EncounterEngine` directly — so win rates can be tuned against real
encounter data instead of guessed.

**Architecture:** Three small, independent real-code changes
(`CombatLeveling.kt`'s growth formula, `QualityUtils.kt`'s grade curve,
`MissionsScreen.kt`'s difficulty label mapping), followed by a new JUnit
test class that exercises the real engine end-to-end with real JSON data.
No new dependencies, no Android/emulator requirement — `EncounterEngine.kt`
has zero Android imports and runs as plain JVM code.

**Tech Stack:** Kotlin, JUnit 4 (existing test convention), kotlinx.serialization
(existing JSON deserialization pattern already used by `GrimoireDataTest.kt`).

## Global Constraints

- Recipe ranks (from the companion recipe-rank-system design) are **out of
  scope** for this plan. The harness fixes rank multiplier at 1.0x implicitly
  by not modeling ranks at all.
- No boss-flavored encounter content is authored in this plan — `"boss"`
  becomes a legal `difficulty` value with a UI label, nothing more.
- All numeric values touched here (growth rates, grade multipliers) are
  explicitly first-pass placeholders per both design docs — do not treat
  any number as final; the harness exists to test and refine them later.
- `CookQuality.kt` (`data/quality/` package) must **not** be modified or
  deleted — it is live code for HoH dish-grade resolution, a separate
  concern from this plan's grade-multiplier change.
- Every task must leave `./gradlew build` passing before commit — never
  commit a broken build.

---

### Task 1: Compound Stat Growth Formula

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLeveling.kt:8-11`
- Modify: `app/src/main/assets/data/growth_curves.json`
- Modify: `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLevelingTest.kt:9-17`

**Interfaces:**
- Produces: `statAtLevel(startingStat: Int, growthRate: Float, level: Int): Float`
  — signature unchanged, only the internal formula changes. Existing callers
  (`BandRepository.kt:96-100`, `BandViewModel.kt:144-148`) need no changes.

- [ ] **Step 1: Update the failing test for compound growth**

Replace the existing linear-growth test in `CombatLevelingTest.kt` (the
`statAtLevel grows linearly with level` test, lines 13-17) with:

```kotlin
    @Test
    fun `statAtLevel at level 1 returns the starting stat unchanged`() {
        assertEquals(5.0f, statAtLevel(startingStat = 5, growthRate = 0.20f, level = 1), 0.0001f)
    }

    @Test
    fun `statAtLevel compounds by the growth rate each level, not adds`() {
        // level 11: 10 levels above 1, growth 0.20/level compounding -> 5 * 1.20^10
        val expected = 5.0 * Math.pow(1.20, 10.0)
        assertEquals(expected.toFloat(), statAtLevel(startingStat = 5, growthRate = 0.20f, level = 11), 0.01f)
    }

    @Test
    fun `statAtLevel with zero growth rate never changes`() {
        assertEquals(5.0f, statAtLevel(startingStat = 5, growthRate = 0f, level = 50), 0.0001f)
    }
```

(The first test, `statAtLevel at level 1 returns the starting stat
unchanged`, already exists and passes under both formulas — keep it as-is.
Add the second and third as new tests, removing the old
`statAtLevel grows linearly with level` test since it specifically asserts
the linear formula's output.)

- [ ] **Step 2: Run tests to verify the new compounding test fails**

Run: `./gradlew testDebugUnitTest --tests "*.CombatLevelingTest"`
Expected: FAIL on `statAtLevel compounds by the growth rate each level, not adds`
(the current linear implementation returns `5 + 0.20*10 = 7.0`, not `~30.96`)

- [ ] **Step 3: Implement compound growth**

In `CombatLeveling.kt`, replace lines 8-11:

```kotlin
// A member's stat at a given level: starting value plus a flat per-level growth rate.
// Growth rates are per-role placeholders (see growth_curves.json) — not final-balanced.
fun statAtLevel(startingStat: Int, growthRate: Float, level: Int): Float =
    startingStat + growthRate * (level - 1)
```

with:

```kotlin
// A member's stat at a given level: starting value compounding by a fixed
// percentage each level (not flat-linear) — this is what makes a level-50
// veteran dramatically stronger than a fresh recruit, per the stat-growth-
// and-food-power design doc. Growth rates are per-role/per-stat placeholders
// (see growth_curves.json) — not final-balanced; tuned via the balance harness.
fun statAtLevel(startingStat: Int, growthRate: Float, level: Int): Float =
    startingStat * (1f + growthRate).pow(level - 1)
```

(`kotlin.math.pow` is already imported at the top of the file — no new
import needed.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*.CombatLevelingTest"`
Expected: PASS, all tests including the new compounding one.

- [ ] **Step 5: Update growth_curves.json to compound-rate values**

Replace the entire contents of `app/src/main/assets/data/growth_curves.json`
with (rates per the stat-growth-and-food-power design §2: primary 3.5%,
secondary 2.0%, off-stat 1.0%, Captain's two secondaries split to 1.0% each):

```json
[
  { "role": "warden",         "migGrowth": 0.02,  "agiGrowth": 0.01, "vitGrowth": 0.035, "wilGrowth": 0.01,  "fatGrowth": 0.01 },
  { "role": "keeper",         "migGrowth": 0.01,  "agiGrowth": 0.01, "vitGrowth": 0.02,  "wilGrowth": 0.035, "fatGrowth": 0.01 },
  { "role": "captain",        "migGrowth": 0.01,  "agiGrowth": 0.01, "vitGrowth": 0.01,  "wilGrowth": 0.035, "fatGrowth": 0.01 },
  { "role": "fighter_ranged", "migGrowth": 0.01,  "agiGrowth": 0.035,"vitGrowth": 0.01,  "wilGrowth": 0.01,  "fatGrowth": 0.02 },
  { "role": "fighter_melee",  "migGrowth": 0.035, "agiGrowth": 0.01, "vitGrowth": 0.01,  "wilGrowth": 0.01,  "fatGrowth": 0.02 }
]
```

This maps: Warden (primary Vitality, secondary Might), Keeper (primary
Will, secondary Vitality), Captain (primary Will, secondaries Fate +
Vitality each at half rate), Fighter ranged (primary Agility, secondary
Fate), Fighter melee (primary Might, secondary Fate) — matching
master-design.md's documented class stat priorities.

- [ ] **Step 6: Run the full test suite and build**

Run: `./gradlew clean testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (this will also exercise
`GrimoireDataTest` and any other test reading `growth_curves.json` shape —
none currently do beyond `GameDataRepository`, which only checks
deserialization, not values).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLeveling.kt \
        app/src/main/assets/data/growth_curves.json \
        app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/CombatLevelingTest.kt
git commit -m "[hc] Compound stat growth: replace flat-linear with %/level formula

statAtLevel now compounds (start * (1+rate)^(level-1)) instead of adding a
flat amount per level, matching the stat-growth-and-food-power design.
growth_curves.json rescaled to compound rates: primary 3.5%, secondary
2.0%, off-stat 1.0%, Captain's two secondaries split to 1.0% each."
```

---

### Task 2: Multiplicative Grade Curve

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/QualityUtils.kt:28-41,86-89`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt:6,126-134`
- Modify: `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/QualityUtilsTest.kt:38-61`
- Modify: `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/repository/MemberInputStatTest.kt:4,32-50`
- Modify: `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/CookResolutionTest.kt:98-115`

**Interfaces:**
- Produces: `gradeMultiplier(grade: Grade): Float` — replaces `gradeStep(grade: Grade): Float`.
  Callers must switch from `base + gradeStep(...)` to `base * gradeMultiplier(...)`.
- Consumes: `Grade` enum (unchanged, `data/model/Grade.kt`).

**Do not touch:** `data/quality/CookQuality.kt` — it has its own unrelated
`gradeStep()` (zero callers, fine to leave) and a live `resolveDishGrade()`
used by `HohRepository.kt` for HoH dish-grade resolution, a separate
concern. This task only changes `data/model/QualityUtils.kt`.

- [ ] **Step 1: Write failing tests for the new multiplicative semantics**

In `QualityUtilsTest.kt`, replace the `// --- gradeStep ---` section
(lines 38-61) with:

```kotlin
    // --- gradeMultiplier ---

    @Test
    fun `gradeMultiplier for CRUDE softly reduces the authored boost, never to zero`() {
        val multiplier = gradeMultiplier(Grade.CRUDE)
        assertTrue("Crude multiplier must be positive", multiplier > 0f)
        assertTrue("Crude multiplier must be below the Fine baseline of 1.0", multiplier < 1.0f)
    }

    @Test
    fun `gradeMultiplier at FINE is exactly the baseline 1x`() {
        assertEquals(1.0f, gradeMultiplier(Grade.FINE), 0.0001f)
    }

    @Test
    fun `gradeMultiplier increases monotonically with grade`() {
        val multipliers = Grade.entries.map { gradeMultiplier(it) }
        for (i in 1..multipliers.lastIndex) {
            assertTrue(
                "gradeMultiplier(${Grade.entries[i]}) should exceed gradeMultiplier(${Grade.entries[i - 1]})",
                multipliers[i] > multipliers[i - 1]
            )
        }
    }

    @Test
    fun `gradeMultiplier is never negative`() {
        Grade.entries.forEach { grade ->
            assertTrue("gradeMultiplier($grade) must not be negative", gradeMultiplier(grade) >= 0f)
        }
    }
```

In `MemberInputStatTest.kt`, change the import on line 4 from
`import com.liquidcode7.hearthcraft.data.model.gradeStep` to
`import com.liquidcode7.hearthcraft.data.model.gradeMultiplier`, and
replace lines 32-50 (the three grade-bonus tests) with:

```kotlin
    @Test
    fun `Crude grade softly reduces stat boost, never zeroes it`() {
        val multiplier = gradeMultiplier(Grade.CRUDE)
        assertTrue("Crude multiplier should be positive", multiplier > 0f)
        assertTrue("Crude multiplier should be below the Fine baseline of 1.0", multiplier < 1.0f)
    }

    @Test
    fun `grade bonus multiplies the authored boost`() {
        val authoredBoost = 3f
        val effectiveBoost = authoredBoost * gradeMultiplier(Grade.SUPERB)
        assertTrue("Superb grade should increase stat above authored boost", effectiveBoost > authoredBoost)
    }

    @Test
    fun `grade bonus does not apply when stat does not match`() {
        val base = BandRepository.statBonusFor("wil", "mig", 3, null, 0)
        assertEquals(0f, base, 0.001f)
        assertTrue(gradeMultiplier(Grade.PRISTINE) >= 0f)
    }
```

In `CookResolutionTest.kt`, replace the `// ── gradeStep: additive scaling`
section (lines 98-115, ending just before the file's closing brace) with:

```kotlin
    // ── gradeMultiplier: multiplicative scaling ──────────────────────────────

    @Test
    fun `Crude multiplies boost down but never to zero`() {
        val authoredBoost = 3f
        val effectiveBoost = authoredBoost * gradeMultiplier(Grade.CRUDE)
        assertTrue(effectiveBoost > 0f)
        assertTrue(effectiveBoost < authoredBoost)
    }

    @Test
    fun `grades above Fine always amplify the authored boost`() {
        val authoredBoost = 3f
        listOf(Grade.SUPERB, Grade.PRISTINE).forEach { grade ->
            val multiplier = gradeMultiplier(grade)
            assertTrue("gradeMultiplier($grade) should exceed 1.0", multiplier > 1.0f)
            assertTrue("effective boost should exceed authored", authoredBoost * multiplier > authoredBoost)
        }
    }
```

- [ ] **Step 2: Run tests to verify they fail (gradeMultiplier doesn't exist yet)**

Run: `./gradlew testDebugUnitTest --tests "*.QualityUtilsTest" --tests "*.MemberInputStatTest" --tests "*.CookResolutionTest"`
Expected: FAIL to compile — `gradeMultiplier` is not yet defined.

- [ ] **Step 3: Implement gradeMultiplier in QualityUtils.kt**

Replace lines 28-41 (the `GRADE_STEP` array and its doc comment) with:

```kotlin
/**
 * Grade multiplier applied to a dish's authored stat boost.
 * CRUDE softly reduces the authored boost; PRISTINE amplifies it well
 * beyond it. FINE is the 1.0x baseline ("tier-appropriate" reference
 * point). Multiplies the recipe's authored boost on matching stats only.
 * Placeholder values — see stat-growth-and-food-power design doc §3;
 * tuned via the balance harness.
 */
private val GRADE_MULTIPLIER = floatArrayOf(
    0.7f,  // CRUDE    (ordinal 0)
    0.85f, // COMMON   (ordinal 1)
    1.0f,  // FINE     (ordinal 2) — baseline
    1.3f,  // SUPERB   (ordinal 3)
    1.7f,  // PRISTINE (ordinal 4)
)
```

Replace lines 86-89 (the `gradeStep` function and its doc comment) with:

```kotlin
/**
 * Multiplicative grade bonus for a given grade.
 * Multiply this by the recipe's authored boost — do not add it.
 */
fun gradeMultiplier(grade: Grade): Float = GRADE_MULTIPLIER[grade.ordinal]
```

- [ ] **Step 4: Update BandRepository.kt's caller**

Change the import on line 6 from
`import com.liquidcode7.hearthcraft.data.model.gradeStep` to
`import com.liquidcode7.hearthcraft.data.model.gradeMultiplier`.

Replace lines 126-134:

```kotlin
                val bonus  = { stat: String ->
                    if (food != null) {
                        val base = statBonusFor(stat, food.primaryStat, food.primaryBoost,
                                                food.secondaryStat, food.secondaryBoost)
                        // Grade adds a flat step on top of the authored boost, on matching stats only.
                        val step = if (base > 0f) gradeStep(Grade.fromOrdinal(food.grade)) else 0f
                        base + step
                    } else 0f
                }
```

with:

```kotlin
                val bonus  = { stat: String ->
                    if (food != null) {
                        val base = statBonusFor(stat, food.primaryStat, food.primaryBoost,
                                                food.secondaryStat, food.secondaryBoost)
                        // Grade multiplies the authored boost — Crude softly reduces it,
                        // Pristine amplifies it well beyond it. Zero base stays zero.
                        base * gradeMultiplier(Grade.fromOrdinal(food.grade))
                    } else 0f
                }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*.QualityUtilsTest" --tests "*.MemberInputStatTest" --tests "*.CookResolutionTest"`
Expected: PASS.

- [ ] **Step 6: Run the full test suite and build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL. This confirms no other caller of `gradeStep`
was missed (the build would fail to compile if one existed).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/QualityUtils.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt \
        app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/QualityUtilsTest.kt \
        app/src/test/kotlin/com/liquidcode7/hearthcraft/data/repository/MemberInputStatTest.kt \
        app/src/test/kotlin/com/liquidcode7/hearthcraft/data/model/CookResolutionTest.kt
git commit -m "[hc] Grade curve: additive gradeStep -> multiplicative gradeMultiplier

Crude no longer zeroes out a food's stat contribution -- it softly reduces
it (0.7x), Pristine now amplifies well beyond the authored value (1.7x),
per the stat-growth-and-food-power design's reshaped grade curve. Only
QualityUtils.gradeStep is affected; CookQuality.kt (HoH dish-grade
resolution) is untouched -- separate concern, separate implementation."
```

---

### Task 3: Boss Difficulty Tier

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Encounter.kt:42`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt:247-252`

**Interfaces:**
- No new types. `difficulty` remains a plain `String` on `Encounter` — this
  task only makes `"boss"` a recognized value in the one place that maps
  difficulty to a display label/color.

- [ ] **Step 1: Update the doc comment on Encounter.difficulty**

In `Encounter.kt`, change line 42 from:

```kotlin
    val difficulty: String,         // "easy" | "medium" | "hard"
```

to:

```kotlin
    val difficulty: String,         // "easy" | "medium" | "hard" | "boss"
```

- [ ] **Step 2: Add a boss branch to the difficulty label/color mapping**

In `MissionsScreen.kt`, replace lines 247-252:

```kotlin
    val (difficultyLabel, difficultyColor) = when (encounter.difficulty) {
        "easy"   -> "Routine"     to Color(0xFF4CAF50)
        "medium" -> "Challenging" to Color(0xFFFF9800)
        "hard"   -> "Dangerous"   to MaterialTheme.colorScheme.error
        else     -> encounter.difficulty to MaterialTheme.colorScheme.onSurfaceVariant
    }
```

with:

```kotlin
    val (difficultyLabel, difficultyColor) = when (encounter.difficulty) {
        "easy"   -> "Routine"     to Color(0xFF4CAF50)
        "medium" -> "Challenging" to Color(0xFFFF9800)
        "hard"   -> "Dangerous"   to MaterialTheme.colorScheme.error
        "boss"   -> "Boss"        to Color(0xFF6A1B9A)
        else     -> encounter.difficulty to MaterialTheme.colorScheme.onSurfaceVariant
    }
```

(The deep purple `0xFF6A1B9A` is a placeholder distinct from "hard"'s red —
adjust if you want a different color; this is a cosmetic choice, not a
mechanical one.)

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. No unit test covers this Composable mapping
directly (consistent with the rest of `MissionsScreen.kt`, which has no
existing test file) — this is a UI-only change verified by compilation
plus the harness's use of `encounters.json` difficulty values in Task 4.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Encounter.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt
git commit -m "[hc] Add boss as a 4th encounter difficulty tier

No boss-flavored encounter content authored yet -- this just makes \"boss\"
a legal, properly-displayed difficulty value (distinct label and color),
per the stat-growth-and-food-power design's difficulty-scaled win-rate
anchors, which call for a category where even Pristine food doesn't
guarantee a win."
```

---

### Task 4: Balance Harness

**Files:**
- Create: `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/BalanceHarness.kt`

**Interfaces:**
- Consumes: `EncounterEngine.resolve(stage: Stage, members: List<MemberInput>, seed: Long, grievousWoundSpecs: List<GrievousWoundSpec>): EncounterResult`
  (`engine/EncounterEngine.kt:67`), `MemberInput` (`engine/EncounterEngine.kt:37`),
  `Outcome` enum (`engine/EncounterEngine.kt:35`), `statAtLevel` and
  `growthCurveKeyForRole` (`data/model/CombatLeveling.kt`, from Task 1),
  `gradeMultiplier` (`data/model/QualityUtils.kt`, from Task 2), `BandMember`,
  `GrowthCurve`, `Recipe`, `Encounter`, `Grade` (all `data/model/`).
- Produces: nothing consumed elsewhere — this is a standalone reporting/
  regression test, run via `./gradlew test`.

This task depends on Tasks 1 and 2 being complete, since it exercises the
new compound growth formula and multiplicative grade curve.

- [ ] **Step 1: Write the harness with one working test**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/BalanceHarness.kt`:

```kotlin
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.BandMember
import com.liquidcode7.hearthcraft.data.model.Encounter
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.GrowthCurve
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.gradeMultiplier
import com.liquidcode7.hearthcraft.data.model.growthCurveKeyForRole
import com.liquidcode7.hearthcraft.data.model.statAtLevel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Balance test harness — calls the real EncounterEngine directly with real
 * band/recipe/encounter JSON data, so win rates can be tuned against actual
 * game data instead of a hand-maintained JS reimplementation. Not a
 * shippable feature; a developer tool for calibrating the placeholder
 * numbers in the stat-growth-and-food-power design doc.
 *
 * Recipe ranks are NOT modeled here (out of scope for this phase) --
 * food bonuses use only the base authored boost x grade multiplier.
 */
class BalanceHarness {

    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> loadList(filename: String, decode: (String) -> List<T>): List<T> {
        val raw = javaClass.classLoader!!.getResourceAsStream("data/$filename")!!
            .bufferedReader().readText()
        return decode(raw)
    }

    private val bandMembers: List<BandMember> = loadList("band_members.json") { json.decodeFromString(it) }
    private val growthCurves: List<GrowthCurve> = loadList("growth_curves.json") { json.decodeFromString(it) }
    private val recipes: List<Recipe> = loadList("recipes.json") { json.decodeFromString(it) }
    private val encounters: List<Encounter> = loadList("encounters.json") { json.decodeFromString(it) }

    // T1 Greycloaks recipes, one per primary stat -- a "properly provisioned"
    // band eats a dish suited to each member's own role.
    private val ROLE_RECIPE = mapOf(
        "warden" to "goodmans_stew",       // vit primary
        "fighter" to "hedgerow_hand_pie",  // agi primary (ranged build)
        "keeper" to "brookcress_bannock",  // wil primary
        "captain" to "brookcress_bannock"  // wil primary
    )

    private fun buildParty(level: Int, fighterBuild: String = "ranged"): List<MemberInput> =
        bandMembers.filter { it.bandId == "greycloaks" }.map { member ->
            val curveKey = growthCurveKeyForRole(member.role, fighterBuild)
            val curve = growthCurves.find { it.role == curveKey }
            MemberInput(
                id = member.id,
                role = member.role.lowercase(),
                might    = curve?.let { statAtLevel(member.startingMight, it.migGrowth, level) } ?: member.startingMight.toFloat(),
                agility  = curve?.let { statAtLevel(member.startingAgility, it.agiGrowth, level) } ?: member.startingAgility.toFloat(),
                vitality = curve?.let { statAtLevel(member.startingVitality, it.vitGrowth, level) } ?: member.startingVitality.toFloat(),
                will     = curve?.let { statAtLevel(member.startingWill, it.wilGrowth, level) } ?: member.startingWill.toFloat(),
                fate     = curve?.let { statAtLevel(member.startingFate, it.fatGrowth, level) } ?: member.startingFate.toFloat()
            )
        }

    // Applies each member's role-appropriate T1 recipe at the given grade.
    // No food (rank not modeled, out of scope this phase): pass grade = null.
    private fun applyFood(members: List<MemberInput>, grade: Grade?): List<MemberInput> {
        if (grade == null) return members
        val multiplier = gradeMultiplier(grade)
        return members.map { member ->
            val recipe = recipes.find { it.id == ROLE_RECIPE[member.role] } ?: return@map member
            fun boost(stat: String): Float = when (stat) {
                recipe.primaryStat -> recipe.primaryBoost * multiplier
                recipe.secondaryStat -> recipe.secondaryBoost * multiplier
                else -> 0f
            }
            member.copy(
                might = member.might + boost("mig"),
                agility = member.agility + boost("agi"),
                vitality = member.vitality + boost("vit"),
                will = member.will + boost("wil")
            )
        }
    }

    private fun winRate(encounterId: String, members: List<MemberInput>, trials: Int = 300): Float {
        val encounter = encounters.find { it.id == encounterId } ?: error("Unknown encounter: $encounterId")
        val stage = encounter.stages.first()
        var wins = 0
        repeat(trials) { i ->
            val result = EncounterEngine.resolve(stage, members, seed = i.toLong(), grievousWoundSpecs = encounter.grievousWoundSpecs)
            if (result.outcome == Outcome.VICTORY) wins++
        }
        return wins.toFloat() / trials
    }

    @Test
    fun `neekerbreekers win rate increases monotonically from Crude to Pristine at level 1`() {
        println("=== greycloaks_neekerbreekers (easy) @ band level 1 ===")
        val noFoodRate = winRate("greycloaks_neekerbreekers", buildParty(level = 1))
        println("  No food: ${(noFoodRate * 100).toInt()}%")
        val rates = Grade.entries.map { grade ->
            val party = applyFood(buildParty(level = 1), grade)
            val rate = winRate("greycloaks_neekerbreekers", party)
            println("  ${grade.displayName}: ${(rate * 100).toInt()}%")
            rate
        }
        for (i in 1..rates.lastIndex) {
            assertTrue(
                "Win rate at ${Grade.entries[i]} (${rates[i]}) should be >= ${Grade.entries[i - 1]} (${rates[i - 1]})",
                rates[i] >= rates[i - 1]
            )
        }
    }
}
```

- [ ] **Step 2: Run the new test**

Run: `./gradlew testDebugUnitTest --tests "*.BalanceHarness"`
Expected: PASS, with printed win-rate output in the test log
(`app/build/test-results/testDebugUnitTest/` or console with `--info`).
Compare the printed rates against the easy-tier row of the
stat-growth-and-food-power design's anchor table (§4): No food ~20%,
Crude ~70%, Common ~85%, Fine ~95%, Superb ~98%, Pristine ~99%. Large gaps
here are expected and fine — closing them is future tuning work, not part
of this task; the point of this step is confirming the harness runs and
produces a real, non-degenerate report.

- [ ] **Step 3: Add medium and hard difficulty-tier tests**

Append two more test methods to `BalanceHarness`, reusing the same
private helpers, testing at each encounter's own `recLevel` as the band
level:

```kotlin
    @Test
    fun `wolves win rate increases monotonically from Crude to Pristine at level 3`() {
        println("=== greycloaks_wolves (medium) @ band level 3 ===")
        val noFoodRate = winRate("greycloaks_wolves", buildParty(level = 3))
        println("  No food: ${(noFoodRate * 100).toInt()}%")
        val rates = Grade.entries.map { grade ->
            val party = applyFood(buildParty(level = 3), grade)
            val rate = winRate("greycloaks_wolves", party)
            println("  ${grade.displayName}: ${(rate * 100).toInt()}%")
            rate
        }
        for (i in 1..rates.lastIndex) {
            assertTrue(
                "Win rate at ${Grade.entries[i]} (${rates[i]}) should be >= ${Grade.entries[i - 1]} (${rates[i - 1]})",
                rates[i] >= rates[i - 1]
            )
        }
    }

    @Test
    fun `goblins win rate increases monotonically from Crude to Pristine at level 5`() {
        println("=== greycloaks_goblins (hard) @ band level 5 ===")
        val noFoodRate = winRate("greycloaks_goblins", buildParty(level = 5))
        println("  No food: ${(noFoodRate * 100).toInt()}%")
        val rates = Grade.entries.map { grade ->
            val party = applyFood(buildParty(level = 5), grade)
            val rate = winRate("greycloaks_goblins", party)
            println("  ${grade.displayName}: ${(rate * 100).toInt()}%")
            rate
        }
        for (i in 1..rates.lastIndex) {
            assertTrue(
                "Win rate at ${Grade.entries[i]} (${rates[i]}) should be >= ${Grade.entries[i - 1]} (${rates[i - 1]})",
                rates[i] >= rates[i - 1]
            )
        }
    }
```

- [ ] **Step 4: Run the full harness and verify all three pass**

Run: `./gradlew testDebugUnitTest --tests "*.BalanceHarness" --info`
Expected: PASS, 3 tests, with printed win-rate tables for easy/medium/hard.
Note whichever rates diverge most sharply from the anchor table in a
short summary (for the next tuning task, not fixed here).

- [ ] **Step 5: Run the full test suite and build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass including the 3 new harness
tests and everything from Tasks 1-3.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/BalanceHarness.kt
git commit -m "[hc] Add balance test harness calling EncounterEngine directly

JVM-only JUnit test that builds real party stats (Task 1's compound growth)
and food bonuses (Task 2's grade multiplier) from real JSON data, then runs
EncounterEngine.resolve() directly across 300 trials per data point --
no reimplementation, no drift. Covers easy/medium/hard difficulty tiers
against Greycloaks' neekerbreekers/wolves/goblins encounters, asserting
win rate rises monotonically from Crude to Pristine and printing the
report for comparison against the anchor table in the food-power design."
```

---

## After This Plan

Once all four tasks are complete and merged, the printed win-rate reports
from Task 4's harness become the input for the next round of tuning:
adjusting `growth_curves.json` rates, the `GRADE_MULTIPLIER` array, and
`EncounterEngine.kt`'s DPS/HP formula coefficients until simulated rates
converge on the anchor table's targets. That tuning pass, and the deferred
recipe-rank system, are separate follow-on work — not part of this plan.
