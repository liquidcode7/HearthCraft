# Balance Test Harness Design (Phase 1: Core Mechanics + Harness)

**Status:** Draft. Implements the core mechanisms from the
stat-growth-and-food-power design so win-rate testing becomes possible.
Recipe ranks (from the companion recipe-rank-system design) are explicitly
**out of scope** for this phase — deferred to a follow-up once this harness
exists and the base curve is validated.

## 1. Why a Kotlin Harness, Not a JS Rewrite

`tools/sim/run_sim.js` has drifted from the real game repeatedly this
session: hardcoded legacy-scale stats, an additive grade model instead of
the game's real one, no connection to `encounters.json`, and a hand-copied
mirror of `recipes.json` that can't help but go stale. Every fix is another
hand-maintained parallel implementation to keep in sync.

`EncounterEngine.kt` has zero Android dependencies (confirmed — its only
imports are `kotlin.math.*`, `kotlin.random.Random`, and two local data
model types). It can run as a plain JVM unit test with no emulator. So
instead of reimplementing combat math a third time, the harness **calls the
real engine directly**: it cannot drift, because it isn't a copy.

## 2. Phase 1 Mechanic Changes (Real Code)

### 2.1 Compound Stat Growth

`data/model/CombatLeveling.kt`'s `statAtLevel()` currently computes
`start + growthRate × (level - 1)` (flat linear). Change to:

```kotlin
fun statAtLevel(start: Int, growthRatePercent: Float, level: Int): Float =
    start * (1f + growthRatePercent).pow(level - 1)
```

`growth_curves.json` is reformatted from flat per-level amounts to the
compound percentage rates in the stat-growth-and-food-power spec (§2):
primary 3.5%, secondary 2.0%, off-stat 1.0%, Captain's two secondaries
(Fate, Vitality) each at 1.0%.

### 2.2 Grade Curve: Additive → Multiplicative

The real, wired-up grade logic is `data/model/QualityUtils.kt`'s
`gradeStep()` — an additive `GRADE_STEP` float array, called from exactly
one place: `data/repository/BandRepository.kt:131`
(`gradeStep(Grade.fromOrdinal(food.grade))` added to the food's authored
boost). Replace the additive step with the multiplicative curve from the
food-power spec (§3):

```kotlin
private val GRADE_MULTIPLIER = floatArrayOf(0.7f, 0.85f, 1.0f, 1.3f, 1.7f) // Crude..Pristine

fun gradeMultiplier(grade: Grade): Float = GRADE_MULTIPLIER[grade.ordinal]
```

`BandRepository.kt:131` changes from `base + gradeStep(...)` to
`base * gradeMultiplier(...)`.

**Cleanup, same change:** `data/quality/CookQuality.kt` is an orphaned
duplicate of this exact logic (`GRADE_STEPS = [0,1,2,3,5]`, zero callers
anywhere in the codebase). Delete it as part of this task — it's dead code
that will only confuse the next person who greps for "grade step" and finds
two different, disagreeing implementations.

### 2.3 Boss Difficulty Tier

`encounters.json`'s `difficulty` field currently allows `"easy"`,
`"medium"`, `"hard"`. Add `"boss"` wherever this is validated or
enumerated in code. No boss-flavored encounter needs to be authored in this
phase — that's content work for later — this just makes the value legal.

## 3. The Harness

A new JVM-only Kotlin source, `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/BalanceHarness.kt`
(test source set — no Android runtime needed, runs via `./gradlew test`
or a dedicated Gradle task).

**What it does, per run:**

1. **Builds a party.** Reads real starting stats from
   `band_members.json` for a chosen band (Greycloaks only, per
   master-design.md — Elves/Dwarves are parked), applies §2.1's compound
   growth formula for a given target level, producing real `MemberInput`
   values — same type `EncounterEngine.resolve()` already consumes.
2. **Applies a food loadout.** Given a chosen recipe (from `recipes.json`)
   and a chosen `Grade`, computes `boost = recipe.primaryBoost *
   gradeMultiplier(grade)` and adds it to the relevant stat on each member
   who eats it. (Rank multiplier fixed at 1.0x this phase — no rank data
   exists yet.)
3. **Loads a real encounter.** Deserializes `encounters.json` with the
   same `kotlinx.serialization` models the app uses (`Stage`,
   `GrievousWoundSpec`) — picks one `Stage` by encounter ID, no
   hand-copied data.
4. **Runs trials.** Calls `EncounterEngine.resolve(stage, members, seed =
   i, grievousWoundSpecs)` for `i` in `0 until trialCount` (placeholder:
   500 trials per data point — enough for a stable win-rate estimate
   without a slow test run). Tallies `Outcome.VICTORY` / `DEFEAT` /
   `STALEMATE`.
5. **Reports.** Prints win-rate % for the tested (encounter, grade)
   combination. A driver loop runs this across every difficulty tier ×
   every grade, producing a table directly comparable to the anchor table
   in the food-power spec (§4).

**What it is not:** not a UI feature, not shipped in the app — a
developer-only tool for tuning, analogous to a benchmark test.

## 4. The Tuning Loop This Enables

Once the harness exists, closing the gap between simulated and target win
rates (food-power spec §4's anchor table) means editing:
- `growth_curves.json` (growth rates)
- The `GRADE_MULTIPLIER` array (§2.2)
- `EncounterEngine.kt`'s DPS/HP formula coefficients (`rawDps()`,
  `morale()`, etc. — currently known-stale legacy values per the earlier
  combat audit)

...then rerunning the harness. No code changes needed for most iteration
passes — only if a formula's *shape*, not just its constants, needs to
change.

## 5. What This Doc Does Not Decide

- Recipe ranks — explicitly deferred; the harness fixes rank multiplier at
  1.0x this phase.
- Any boss-flavored encounter content (stage parameters for an actual boss
  fight) — mechanism only, content comes later.
- The final tuned values for growth rates, grade multipliers, or
  `EncounterEngine.kt` coefficients — that's the harness's job to help
  discover, not this doc's.
- Exact trial count (500 is a placeholder — may need adjusting if win
  rates prove noisy at that sample size).
