# Combat Feel & Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the three fixes from `docs/superpowers/specs/2026-07-04-combat-feel-and-feedback-design.md`: hide the countdown timer on kill-fights, correct and surface wound recovery durations on the House of Healing tab, and show a post-fight rewards summary.

**Architecture:** Three independent slices sharing no files: (1) `EncounterDetail`/`BandViewModel`/`MissionsScreen` for the timer, (2) `EncounterWorker`/`master-design.md`/`HouseOfHealingScreen` for wounds, (3) `CombatReport`/migration/`EncounterWorker`/`MissionsScreen` for rewards. Slice 3's two tasks (schema+capture, then UI) are sequential; everything else can run in any order.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, kotlinx.coroutines

## Global Constraints

- All Kotlin source lives under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- Commit messages prefixed `[hc]`
- Run `./gradlew build` before every commit — never commit a broken build (`export JAVA_HOME=/usr/share/pycharm/jbr` first)
- DB is currently at version 19; the one schema change in this plan (Task 4) is a single additive v19→v20 migration, no destructive changes
- No changes to `EncounterEngine.resolve()`, the tick-by-tick HP-bar animation, or any skip/fast-forward control — per the spec, Section A is display-only
- Design doc updates land in `design/master-design.md` alongside the code change in the same commit (per CLAUDE.md: docs and code must agree)
- This repo has no Room migration test harness and no Compose UI test harness — verification for schema and UI changes is `./gradlew build` plus manual on-device check, consistent with prior sessions
- GPL-3.0

---

### Task 1: Hide the countdown timer on kill-fights

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt`

**Interfaces:**
- Produces: `EncounterDetail.objective: String` — no other task depends on this.

**Context:** Encounters already carry `objective: String` (`"kill" | "survive" | "retrieve"`) on `Stage` (`data/model/Encounter.kt:17`), but the UI-facing `EncounterDetail` doesn't expose it. `BattleInProgressCard` (`MissionsScreen.kt`) always shows a "MM:SS / remaining" countdown regardless of fight type — this hides it for `"kill"` fights only.

- [ ] **Step 1: Add `objective` to `EncounterDetail`**

In `UiModels.kt`, find:
```kotlin
data class EncounterDetail(
    val encounterId: String,
    val name: String,
    val difficulty: String,
    val recLevel: Int,
    val requiredCookingLevel: Int,
    val flavorLine: String,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardMultiplier: Int,
    val physMitPct: Float,  // from stage[0] — used to show "brings a draught" hint
    val bossResolve: Int,   // from stage[0] — the boss's total HP-equivalent, shown pre-battle
    val isUnlocked: Boolean // recLevel <= band's max vitality AND cookingLevel >= requiredCookingLevel
)
```
Add `objective` after `bossResolve`:
```kotlin
data class EncounterDetail(
    val encounterId: String,
    val name: String,
    val difficulty: String,
    val recLevel: Int,
    val requiredCookingLevel: Int,
    val flavorLine: String,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardMultiplier: Int,
    val physMitPct: Float,  // from stage[0] — used to show "brings a draught" hint
    val bossResolve: Int,   // from stage[0] — the boss's total HP-equivalent, shown pre-battle
    val objective: String,  // from stage[0] — "kill" | "survive" | "retrieve"
    val isUnlocked: Boolean // recLevel <= band's max vitality AND cookingLevel >= requiredCookingLevel
)
```

- [ ] **Step 2: Populate it in `BandViewModel.encounters`**

In `BandViewModel.kt`, find the `EncounterDetail(...)` construction inside the `encounters` StateFlow:
```kotlin
            EncounterDetail(
                encounterId          = enc.id,
                name                 = enc.name,
                difficulty           = enc.difficulty,
                recLevel             = enc.recLevel,
                requiredCookingLevel = enc.requiredCookingLevel,
                flavorLine           = enc.flavorLine,
                rewardMoneyMin       = enc.rewardMoneyMin,
                rewardMoneyMax       = enc.rewardMoneyMax,
                rewardMultiplier     = enc.rewardMultiplier,
                physMitPct           = enc.stages.firstOrNull()?.physMitPct ?: 0f,
                bossResolve          = enc.stages.firstOrNull()?.resolve ?: 0,
                isUnlocked           = maxVit >= enc.recLevel && cookLvl >= enc.requiredCookingLevel
            )
```
Add the `objective` line after `bossResolve`:
```kotlin
            EncounterDetail(
                encounterId          = enc.id,
                name                 = enc.name,
                difficulty           = enc.difficulty,
                recLevel             = enc.recLevel,
                requiredCookingLevel = enc.requiredCookingLevel,
                flavorLine           = enc.flavorLine,
                rewardMoneyMin       = enc.rewardMoneyMin,
                rewardMoneyMax       = enc.rewardMoneyMax,
                rewardMultiplier     = enc.rewardMultiplier,
                physMitPct           = enc.stages.firstOrNull()?.physMitPct ?: 0f,
                bossResolve          = enc.stages.firstOrNull()?.resolve ?: 0,
                objective            = enc.stages.firstOrNull()?.objective ?: "kill",
                isUnlocked           = maxVit >= enc.recLevel && cookLvl >= enc.requiredCookingLevel
            )
```

- [ ] **Step 3: Thread `objective` through to `BattleInProgressCard` in `MissionsScreen.kt`**

Find the active-mission block:
```kotlin
        if (activeEncounterSession != null || activeMission != null) {
            val name = activeEncounterSession?.let { es ->
                encounters.find { it.encounterId == es.encounterId }?.name ?: es.encounterId
            } ?: activeMission?.let { ms ->
                encounters.find { it.encounterId == ms.missionId }?.name ?: ms.missionId
            } ?: ""
            val startedAt = activeEncounterSession?.startedAtMs ?: activeMission!!.startedAtMs
            val duration  = activeEncounterSession?.durationMs  ?: activeMission!!.durationMs
            BattleInProgressCard(
                missionName  = name,
                startedAtMs  = startedAt,
                durationMs   = duration,
                ticks        = encounterTicks,
                combatReport = combatReport,
                members      = members
            )
            return@Column
        }
```
Replace with:
```kotlin
        if (activeEncounterSession != null || activeMission != null) {
            val name = activeEncounterSession?.let { es ->
                encounters.find { it.encounterId == es.encounterId }?.name ?: es.encounterId
            } ?: activeMission?.let { ms ->
                encounters.find { it.encounterId == ms.missionId }?.name ?: ms.missionId
            } ?: ""
            val objective = activeEncounterSession?.let { es ->
                encounters.find { it.encounterId == es.encounterId }?.objective
            } ?: activeMission?.let { ms ->
                encounters.find { it.encounterId == ms.missionId }?.objective
            } ?: "kill"
            val startedAt = activeEncounterSession?.startedAtMs ?: activeMission!!.startedAtMs
            val duration  = activeEncounterSession?.durationMs  ?: activeMission!!.durationMs
            BattleInProgressCard(
                missionName  = name,
                objective    = objective,
                startedAtMs  = startedAt,
                durationMs   = duration,
                ticks        = encounterTicks,
                combatReport = combatReport,
                members      = members
            )
            return@Column
        }
```

- [ ] **Step 4: Add the `objective` parameter to `BattleInProgressCard` and gate the countdown**

Find the signature:
```kotlin
private fun BattleInProgressCard(
    missionName: String,
    startedAtMs: Long,
    durationMs: Long,
    ticks: EncounterTicks?,
    combatReport: CombatReport?,
    members: List<BandMemberWithState>
) {
```
Replace with:
```kotlin
private fun BattleInProgressCard(
    missionName: String,
    objective: String,
    startedAtMs: Long,
    durationMs: Long,
    ticks: EncounterTicks?,
    combatReport: CombatReport?,
    members: List<BandMemberWithState>
) {
```

Find the countdown display near the end of the composable:
```kotlin
            Text(
                formatMissionMs(remainingMs),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "remaining",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
```
Replace with:
```kotlin
            if (objective != "kill") {
                Text(
                    formatMissionMs(remainingMs),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
```

- [ ] **Step 5: Build**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual verification**

Launch the app, send a band on a `"kill"`-objective encounter (e.g. Neekerbreekers) — confirm no countdown text appears while the fight is "in progress," but the boss/member HP bars still animate normally. If any `"survive"`/`"retrieve"` encounter exists in current data, confirm its countdown still shows (if none exist yet, note this in the commit — the gate is still correct, just unexercised by current content).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt
git commit -m "$(cat <<'EOF'
[hc] Hide countdown timer on kill-fights

A kill-fight's outcome is already fully computed the instant it starts
(EncounterEngine.resolve() runs synchronously up front) — showing an
exact countdown to a guaranteed loss removed any sense of uncertainty
(audit finding, 04 Jul 2026). Survive/retrieve fights keep the
countdown since racing a clock is their whole point. No change to the
HP-bar animation or tick playback, per spec.
EOF
)"
```

---

### Task 2: Correct wound recovery durations

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt`
- Modify: `design/master-design.md`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WoundResolutionTest.kt`

**Interfaces:**
- Consumes: none from other tasks in this plan.
- Produces: corrected `LIGHT_WOUND_MS`/`HEAVY_WOUND_MS`/`SAFETY_NET_WOUND_MS` constants — Task 3's manual verification relies on these being correct, but no code in Task 3 references them directly (it reads `woundedDurationMs` off the member, which is populated from these constants at wound time).

**Context:** `EncounterWorker.kt`'s wound duration constants are currently seconds-scale placeholders (15s/30s/60s) with a comment noting the "target production values" as 1h/2h/6h — both the placeholders and that target were wrong. Corrected per Wes: 18 min light, 30 min heavy, 2 hr safety-net (kept deliberately harsh as a real penalty, not a soft nudge — starting areas have no HoH recipe at all, so this is the only consequence a new player faces for a 5+ down-count result).

- [ ] **Step 1: Write the failing test locking in the corrected values**

In `WoundResolutionTest.kt`, add this test after the existing ones (before the closing `}`):
```kotlin
    @Test
    fun `corrected production durations are 18 minutes light, 30 minutes heavy, 2 hours safety net`() {
        val light = resolveWoundOutcome(wounds = 1, hohAvailable = false)!!
        val heavy = resolveWoundOutcome(wounds = 3, hohAvailable = false)!!
        val safetyNet = resolveWoundOutcome(wounds = 5, hohAvailable = false)!!
        assertEquals(18 * 60 * 1000L, light.durationMs)
        assertEquals(30 * 60 * 1000L, heavy.durationMs)
        assertEquals(2 * 60 * 60 * 1000L, safetyNet.durationMs)
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew test --tests "*.WoundResolutionTest"
```
Expected: FAIL — current constants are 15_000L/30_000L/60_000L, not the corrected values.

- [ ] **Step 3: Update the constants in `EncounterWorker.kt`**

Find:
```kotlin
// Testing values: short (seconds-scale) durations so Wes can exercise recovery on-device.
// Strictly increasing by severity band — matches the design intent below (heavy wounds
// recover slower than light; the safety net is deliberately the longest of all).
// Target production values (revisit once the sim rebalance pass locks in real numbers):
//   LIGHT_WOUND_MS = 3_600_000L   (1 hour)
//   HEAVY_WOUND_MS = 7_200_000L   (2 hours)
//   SAFETY_NET_WOUND_MS = 21_600_000L (6 hours)
private const val LIGHT_WOUND_MS       = 15_000L
private const val HEAVY_WOUND_MS       = 30_000L
private const val SAFETY_NET_WOUND_MS  = 60_000L
```
Replace with:
```kotlin
// Wound recovery durations (master-design.md §6.8). Corrected 05 Jul 2026 — both the
// original 1h/2h/6h design figures and this file's earlier seconds-scale test
// stand-ins were wrong. These are the real production values.
private const val LIGHT_WOUND_MS       = 1_080_000L  // 18 minutes
private const val HEAVY_WOUND_MS       = 1_800_000L  // 30 minutes
// Deliberately harsh, not a soft nudge toward HoH: a real penalty for
// under-provisioning. Starting areas have no HoH recipe available at all, so this
// is the only consequence a new player faces for a 5+ down-count result.
private const val SAFETY_NET_WOUND_MS  = 7_200_000L  // 2 hours
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests "*.WoundResolutionTest"
```
Expected: PASS, all 6 tests (5 existing + 1 new).

- [ ] **Step 5: Update `design/master-design.md` §6.8**

Find the table:
```markdown
| Down-count | Severity | Recovery |
|------------|----------|----------|
| 1-2 | Wounded (light) | Auto-heals after 1 hour — time only, no food or prep involved |
| 3-4 | Wounded (heavy) | Auto-heals after 2 hours — time only |
| 5+  | Grievously wounded | See §9.1 — requires Houses of Healing, does not auto-heal |
```
Replace with:
```markdown
| Down-count | Severity | Recovery |
|------------|----------|----------|
| 1-2 | Wounded (light) | Auto-heals after 18 minutes — time only, no food or prep involved |
| 3-4 | Wounded (heavy) | Auto-heals after 30 minutes — time only |
| 5+  | Grievously wounded | See §9.1 — requires Houses of Healing, does not auto-heal |
```

Find the safety-net paragraph immediately below it:
```markdown
**HoH-availability safety net:** while the player has no visible/craftable HoH
recipe at all, a 5+ down-count result is capped at "Wounded (heavy)" with an
extended 6-hour recovery instead of becoming a genuine grievous wound. This
prevents an unrecoverable band before Houses of Healing content exists. The
safety net switches off automatically the moment a real HoH recipe becomes
available to the player — it is not a permanent difficulty reduction.
```
Replace with:
```markdown
**HoH-availability safety net:** while the player has no visible/craftable HoH
recipe at all, a 5+ down-count result is capped at "Wounded (heavy)" with an
extended 2-hour recovery instead of becoming a genuine grievous wound. This is
deliberately harsh — a real penalty for under-provisioning, not a soft nudge —
since starting areas have no HoH recipe available at all and this is the only
consequence a new player faces for this outcome. The safety net switches off
automatically the moment a real HoH recipe becomes available to the player —
it is not a permanent difficulty reduction.
```

- [ ] **Step 6: Full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt design/master-design.md app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WoundResolutionTest.kt
git commit -m "$(cat <<'EOF'
[hc] Correct wound recovery durations (18min/30min/2hr)

Both the design doc's 1h/2h/6h figures and this file's seconds-scale
test placeholders were wrong (Wes's correction this session). Locked
in with a test since these values have already been wrong once.
Safety-net duration (2hr) is deliberately harsh — a real penalty for
under-provisioning, not a nudge toward HoH — since starting areas have
no HoH recipe available at all.
EOF
)"
```

---

### Task 3: Surface wound recovery on the House of Healing tab

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HouseOfHealingScreen.kt`

**Interfaces:**
- Consumes: `BandMemberWithState.woundStatus`, `.woundedSinceMs`, `.woundedDurationMs` (all pre-existing, `ui/viewmodel/UiModels.kt:32-50`).

**Context:** `HouseOfHealingScreen.kt`'s "Recovering" section already lists every wounded member, but light/heavy ("Wounded") members are incorrectly offered HoH treatment options identical to grievous members — contradicting §6.8 ("time only, no food or prep involved" for light/heavy). This replaces the treat-options block with a live countdown for non-grievous members; grievous members are untouched.

- [ ] **Step 1: Add imports**

At the top of `HouseOfHealingScreen.kt`, add:
```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
```

- [ ] **Step 2: Add a ticking clock at the top of the composable**

Find:
```kotlin
    val recovering = members.filter { it.isAlive && it.woundStatus != "healthy" }

    Column(
```
Replace with:
```kotlin
    val recovering = members.filter { it.isAlive && it.woundStatus != "healthy" }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    Column(
```

- [ ] **Step 3: Replace the treat-options block with a countdown for non-grievous members**

Find:
```kotlin
                        if (preparedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            preparedItems.forEach { item ->
                                val treatsAnything = item.treatsWoundTypes.any { it in member.woundTypes }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Treats: ${item.treatsWoundTypes.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (treatsAnything) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    GradeBadge(item.grade)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(onClick = { hohViewModel.applyToMember(member.memberId, item) }) {
                                        Text(if (treatsAnything) "Treat" else "Treat anyway")
                                    }
                                }
                            }
                        }
```
Replace with:
```kotlin
                        if (member.woundStatus == "grievously_wounded") {
                            if (preparedItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                preparedItems.forEach { item ->
                                    val treatsAnything = item.treatsWoundTypes.any { it in member.woundTypes }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                "Treats: ${item.treatsWoundTypes.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (treatsAnything) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                            )
                                        }
                                        GradeBadge(item.grade)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(onClick = { hohViewModel.applyToMember(member.memberId, item) }) {
                                            Text(if (treatsAnything) "Treat" else "Treat anyway")
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            val remainingMs = (member.woundedSinceMs + member.woundedDurationMs - now).coerceAtLeast(0L)
                            val remainingMin = (remainingMs / 60_000L).toInt()
                            Text(
                                if (remainingMin > 0) "Resting — back in ${remainingMin}m" else "Resting — back any moment",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
```

- [ ] **Step 4: Build**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification**

Lose a fight with 1-4 downs on a member (light or heavy). Open House of Healing — confirm the member shows under "Recovering" as "Wounded" with a "Resting — back in Xm" line and no treat buttons. Confirm a grievously-wounded member (5+ downs, HoH recipe available) still shows the existing craft-and-treat flow unchanged.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HouseOfHealingScreen.kt
git commit -m "$(cat <<'EOF'
[hc] Show live recovery countdown for light/heavy wounds on HoH tab

Light/heavy wounds were incorrectly offered HoH treatment options
identical to grievous wounds, contradicting §6.8 ("time only, no food
or prep involved"). Replaced with a live "back in Xm" countdown;
grievous members keep the existing craft-and-treat flow unchanged.
Addresses the audit finding that wound consequences were invisible
(04 Jul 2026).
EOF
)"
```

---

### Task 4: Persist actual reward values on CombatReport

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration19To20.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/CombatReport.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/RewardEncodingTest.kt` (create)

**Interfaces:**
- Produces: `CombatReport.moneyGranted: Int`, `.ingredientsGrantedJson: String` (`id:qty,id:qty` format), `.grimoireIdsGrantedJson: String` (comma-separated), `.xpGranted: Int` — Task 5 reads all four.
- Produces: `encodeIngredientCounts(ingredientIds: List<String>): String` — top-level function in `EncounterWorker.kt`, same testable-pure-function pattern as `resolveWoundOutcome` in this same file.

**Context:** On Victory, `EncounterWorker.kt` already grants money, 1-3 random ingredients, sometimes a grimoire, and combat XP — entirely silently. `CombatReport` (already saved earlier in `BandViewModel.sendOnEncounter()` before this worker runs) has no fields for any of it. This task adds those fields via an additive migration and updates the already-saved report once rewards are granted.

- [ ] **Step 1: Write the failing test for the ingredient-count encoding**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/RewardEncodingTest.kt`:
```kotlin
package com.liquidcode7.hearthcraft.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class RewardEncodingTest {

    @Test
    fun `encodes distinct ingredient ids with a count of 1 each`() {
        val result = encodeIngredientCounts(listOf("hearthgrain", "wanderer_fig"))
        assertEquals("hearthgrain:1,wanderer_fig:1", result)
    }

    @Test
    fun `aggregates duplicate ingredient ids into one entry with a summed count`() {
        val result = encodeIngredientCounts(listOf("hearthgrain", "hearthgrain", "wanderer_fig"))
        assertEquals("hearthgrain:2,wanderer_fig:1", result)
    }

    @Test
    fun `empty list encodes to an empty string`() {
        val result = encodeIngredientCounts(emptyList())
        assertEquals("", result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew test --tests "*.RewardEncodingTest"
```
Expected: compilation failure, `encodeIngredientCounts` is unresolved.

- [ ] **Step 3: Add `encodeIngredientCounts` to `EncounterWorker.kt`**

Add this at the bottom of the file, alongside `resolveWoundOutcome`:
```kotlin
// Pure function extracted from EncounterWorker — testable without Android context.
fun encodeIngredientCounts(ingredientIds: List<String>): String =
    ingredientIds.groupingBy { it }.eachCount().entries.joinToString(",") { "${it.key}:${it.value}" }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests "*.RewardEncodingTest"
```
Expected: PASS, all 3 tests.

- [ ] **Step 5: Add the new columns to `CombatReport.kt`**

Find:
```kotlin
@Entity(tableName = "combat_reports")
data class CombatReport(
    @PrimaryKey val bandId: String,
    val encounterId: String,
    val encounterName: String,
    val outcome: String,          // "VICTORY" | "DEFEAT" | "STALEMATE"
    val endedAtSec: Int,
    val durationSec: Int,
    val woundsJson: String,       // "memberId:wounds,memberId:wounds,..."
    val rescuesUsed: Int,
    val wardGuardsUsed: Int,
    val resolveRemainingFraction: Float,
    val createdAtMs: Long,
    val dpsJson: String = "",     // "memberId:damage,..." for all members
    val healJson: String = "",    // "memberId:healing,..." (keeper only populated)
    val keeperHealUptime: Int = 0 // keeperHealTicks / totalTicks * 100 as Int
)
```
Replace with:
```kotlin
@Entity(tableName = "combat_reports")
data class CombatReport(
    @PrimaryKey val bandId: String,
    val encounterId: String,
    val encounterName: String,
    val outcome: String,          // "VICTORY" | "DEFEAT" | "STALEMATE"
    val endedAtSec: Int,
    val durationSec: Int,
    val woundsJson: String,       // "memberId:wounds,memberId:wounds,..."
    val rescuesUsed: Int,
    val wardGuardsUsed: Int,
    val resolveRemainingFraction: Float,
    val createdAtMs: Long,
    val dpsJson: String = "",     // "memberId:damage,..." for all members
    val healJson: String = "",    // "memberId:healing,..." (keeper only populated)
    val keeperHealUptime: Int = 0, // keeperHealTicks / totalTicks * 100 as Int
    val moneyGranted: Int = 0,
    val ingredientsGrantedJson: String = "", // "ingredientId:qty,ingredientId:qty,..."
    val grimoireIdsGrantedJson: String = "", // comma-separated, usually empty
    val xpGranted: Int = 0
)
```

- [ ] **Step 6: Create the migration**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration19To20.kt`:
```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration19To20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN moneyGranted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN ingredientsGrantedJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN grimoireIdsGrantedJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN xpGranted INTEGER NOT NULL DEFAULT 0")
    }
}
```

- [ ] **Step 7: Bump the database version in `HearthCraftDatabase.kt`**

Find `version = 19,` and change to `version = 20,`.

- [ ] **Step 8: Register the migration in `DatabaseModule.kt`**

Find:
```kotlin
import com.liquidcode7.hearthcraft.data.db.Migration18To19
```
Add immediately after:
```kotlin
import com.liquidcode7.hearthcraft.data.db.Migration19To20
```

Find:
```kotlin
            .addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17, Migration17To18, Migration18To19)
```
Replace with:
```kotlin
            .addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17, Migration17To18, Migration18To19, Migration19To20)
```

- [ ] **Step 9: Capture the granted values in `EncounterWorker.kt`**

Find the `applyOutcome` method's `"VICTORY"` and `"STALEMATE"` branches:
```kotlin
            "VICTORY" -> {
                val multiplier = encounter.rewardMultiplier
                val money = (encounter.rewardMoneyMin..encounter.rewardMoneyMax).random() * multiplier
                player.addMoney(money)
                val rewardCount = minOf(3, (1..3).random() + (multiplier - 1))
                encounter.rewardTable.shuffled().take(rewardCount).forEach { inventory.addIngredient(it, 1) }
                val grimoires = encounter.grimoireDrops
                if (grimoires.isNotEmpty()) {
                    player.discoverGrimoires(grimoires)
                }
                player.addCookingXp(PlayerRepository.XP_COOK_WIN)
                player.addGatheringXp(PlayerRepository.XP_GATHER_WIN)
                band.grantCombatXp(bandId, xp = 40)
                notify("Mission Complete", "${encounter.name} — your band has returned.")
            }
            "STALEMATE" -> {
                band.grantCombatXp(bandId, xp = 15)
                notify("No Result", "${encounter.name} — the band held but couldn't finish it.")
            }
```
Replace with:
```kotlin
            "VICTORY" -> {
                val multiplier = encounter.rewardMultiplier
                val money = (encounter.rewardMoneyMin..encounter.rewardMoneyMax).random() * multiplier
                player.addMoney(money)
                val rewardCount = minOf(3, (1..3).random() + (multiplier - 1))
                val grantedIngredients = encounter.rewardTable.shuffled().take(rewardCount)
                grantedIngredients.forEach { inventory.addIngredient(it, 1) }
                val grimoires = encounter.grimoireDrops
                if (grimoires.isNotEmpty()) {
                    player.discoverGrimoires(grimoires)
                }
                player.addCookingXp(PlayerRepository.XP_COOK_WIN)
                player.addGatheringXp(PlayerRepository.XP_GATHER_WIN)
                band.grantCombatXp(bandId, xp = 40)
                recordRewards(bandId, money = money, ingredients = grantedIngredients, grimoires = grimoires, xp = 40)
                notify("Mission Complete", "${encounter.name} — your band has returned.")
            }
            "STALEMATE" -> {
                band.grantCombatXp(bandId, xp = 15)
                recordRewards(bandId, money = 0, ingredients = emptyList(), grimoires = emptyList(), xp = 15)
                notify("No Result", "${encounter.name} — the band held but couldn't finish it.")
            }
```

Add this new private method to the `EncounterWorker` class, right after `applyOutcome`:
```kotlin
    private suspend fun recordRewards(
        bandId: String,
        money: Int,
        ingredients: List<String>,
        grimoires: List<String>,
        xp: Int
    ) {
        val existing = combatRepo.get(bandId) ?: return
        combatRepo.save(existing.copy(
            moneyGranted = money,
            ingredientsGrantedJson = encodeIngredientCounts(ingredients),
            grimoireIdsGrantedJson = grimoires.joinToString(","),
            xpGranted = xp
        ))
    }
```

- [ ] **Step 10: Full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL. This regenerates `app/schemas/.../20.json` automatically (Room's schema export, `exportSchema = true`) — confirm it appears under `app/schemas/com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase/`.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration19To20.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/CombatReport.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/RewardEncodingTest.kt app/schemas/com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase/20.json
git commit -m "$(cat <<'EOF'
[hc] Persist actual reward values on CombatReport (v19->v20)

Victory already silently grants money, ingredients, grimoires, and XP
in EncounterWorker — none of it was ever recorded anywhere the UI
could show it. Additive migration adds four columns; the existing
grant logic is unchanged, just also captured onto the already-saved
report. Sets up the post-fight rewards summary (next task).
EOF
)"
```

---

### Task 5: Show a rewards summary on the post-fight recap

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt`

**Interfaces:**
- Consumes: `CombatReport.moneyGranted/.ingredientsGrantedJson/.grimoireIdsGrantedJson/.xpGranted` from Task 4.

**Context:** `CombatReportCard` currently shows DPS/heal/wound breakdowns but nothing about what was actually won. This adds a "Rewards" section, shown only when `outcome != "DEFEAT"` (matching the existing "no reward on defeat" rule).

- [ ] **Step 1: Add name-lookup helpers to `BandViewModel.kt`**

Add these two functions anywhere in the `BandViewModel` class body (e.g. near `firstBandName()`/`secondBandName()`, which already follow the same `gameData.X.find { ... }` pattern):
```kotlin
    fun ingredientName(id: String): String = gameData.ingredients.find { it.id == id }?.name ?: id
    fun grimoireName(id: String): String = gameData.grimoires.find { it.id == id }?.name ?: id
```

- [ ] **Step 2: Pass the lookups into `CombatReportCard` at its call site**

Find:
```kotlin
        if (combatReport != null) {
            CombatReportCard(
                report = combatReport!!,
                onDismiss = { bandViewModel.dismissCombatReport() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
```
Replace with:
```kotlin
        if (combatReport != null) {
            CombatReportCard(
                report = combatReport!!,
                ingredientName = bandViewModel::ingredientName,
                grimoireName = bandViewModel::grimoireName,
                onDismiss = { bandViewModel.dismissCombatReport() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
```

- [ ] **Step 3: Add the parameters to `CombatReportCard` and render the Rewards section**

Find the signature:
```kotlin
@Composable
private fun CombatReportCard(report: CombatReport, onDismiss: () -> Unit) {
```
Replace with:
```kotlin
@Composable
private fun CombatReportCard(
    report: CombatReport,
    ingredientName: (String) -> String,
    grimoireName: (String) -> String,
    onDismiss: () -> Unit
) {
```

Find the wound-recap block and the dismiss button right after it:
```kotlin
            // ── Wound recap ───────────────────────────────────────────────────
            val woundedMembers = woundsMap.filter { it.value > 0 }
            if (woundedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Wounds", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                woundedMembers.entries.sortedByDescending { it.value }.forEach { (memberId, count) ->
                    val severity = when {
                        count >= 5 -> "Grievous" to MaterialTheme.colorScheme.error
                        count >= 3 -> "Heavy" to Color(0xFFFF9800)
                        else       -> "Light" to Color(0xFF4CAF50)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            memberId.substringAfterLast("_").replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${severity.first} ($count×)",
                            style = MaterialTheme.typography.labelSmall,
                            color = severity.second
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss")
            }
        }
    }
}
```
Replace with:
```kotlin
            // ── Wound recap ───────────────────────────────────────────────────
            val woundedMembers = woundsMap.filter { it.value > 0 }
            if (woundedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Wounds", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                woundedMembers.entries.sortedByDescending { it.value }.forEach { (memberId, count) ->
                    val severity = when {
                        count >= 5 -> "Grievous" to MaterialTheme.colorScheme.error
                        count >= 3 -> "Heavy" to Color(0xFFFF9800)
                        else       -> "Light" to Color(0xFF4CAF50)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            memberId.substringAfterLast("_").replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${severity.first} ($count×)",
                            style = MaterialTheme.typography.labelSmall,
                            color = severity.second
                        )
                    }
                }
            }

            // ── Rewards ────────────────────────────────────────────────────────
            if (report.outcome != "DEFEAT") {
                val grantedIngredients: Map<String, Int> = remember(report.ingredientsGrantedJson) {
                    report.ingredientsGrantedJson.split(",").mapNotNull { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
                    }.toMap()
                }
                val grantedGrimoires: List<String> = remember(report.grimoireIdsGrantedJson) {
                    report.grimoireIdsGrantedJson.split(",").filter { it.isNotBlank() }
                }
                if (report.moneyGranted > 0 || grantedIngredients.isNotEmpty() || grantedGrimoires.isNotEmpty() || report.xpGranted > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Rewards", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (report.moneyGranted > 0) {
                        Text("+${report.moneyGranted} gold", style = MaterialTheme.typography.bodySmall)
                    }
                    grantedIngredients.forEach { (id, qty) ->
                        Text("+$qty ${ingredientName(id)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.xpGranted > 0) {
                        Text("+${report.xpGranted} band XP", style = MaterialTheme.typography.bodySmall)
                    }
                    grantedGrimoires.forEach { id ->
                        Text(
                            "Grimoire found: ${grimoireName(id)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss")
            }
        }
    }
}
```

- [ ] **Step 4: Build**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification**

Win a mission — confirm the recap card shows a "Rewards" section with gold, ingredient names (not raw IDs) and quantities, and XP gained, matching what actually landed in the Pantry/wallet. Trigger a grimoire drop if possible (or temporarily point an encounter's `grimoireDrops` at a valid grimoire id to verify the drop line renders distinctly) and confirm a Defeat shows no Rewards section at all.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt
git commit -m "$(cat <<'EOF'
[hc] Show rewards summary on post-fight recap

Money, ingredients, grimoires, and XP were already granted on victory
but never shown anywhere (audit finding, 04 Jul 2026: "what are the
rewards... need another dialogue saying what loot was received").
Reads the fields Task 4 added to CombatReport; nothing shown on
Defeat, matching the existing no-reward-on-defeat rule.
EOF
)"
```
