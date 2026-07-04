# Navigation Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate mission-sending into the Missions tab, move character stats/bio into Journal, trim Band to a roster overview, and build a new House of Healing tab with a real crafting-and-treatment flow.

**Architecture:** Existing screens/ViewModels are extended in place following established patterns in this codebase (StateFlow-per-concern ViewModels, Hilt injection, Room migrations, WorkManager for timed operations). No new architectural layers are introduced. The House of Healing tab reuses the Kitchen screen's exact ingredient-grade-picker pattern and the exact craft-then-collect session/worker pattern already used by cooking, since the user chose a two-step "craft into inventory, then apply" flow (matching food) over the alternative one-step design.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, WorkManager, kotlinx.coroutines (StateFlow/combine/collectLatest).

## Global Constraints

- No ViewModel unit tests — this project's existing test suite (`app/src/test/`) only unit-tests pure functions/objects (`EncounterEngine`, `CookQuality`, repository-adjacent pure logic); ViewModels and Compose screens are verified by running the app. Follow this convention: write real JUnit tests only for new pure logic (e.g. `OddsEstimator`); verify UI/ViewModel wiring manually by running the app.
- Run `./gradlew build` after every task and do not commit a broken build (per CLAUDE.md).
- Room schema changes require both a version bump on `HearthCraftDatabase` and a new `MigrationNToN+1` object registered in `di/DatabaseModule.kt`, following the exact pattern of `Migration16To17`.
- All new/changed UI strings use "the player" language per CLAUDE.md — no "Hearthwright" or other deprecated player-title terms.
- Commit messages prefixed with `[hc]`, one logical change per commit.

---

### Task 1: Gather-quality badge

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GatheringScreen.kt:317-346` (`HarvestResultDialog`'s item row)

**Interfaces:**
- Consumes: existing `GradeBadge(grade: Int, modifier: Modifier = Modifier)` composable (`ui/screen/GradeBadge.kt`, same package, no import needed) and existing `HarvestItem.grade: Int` field (already populated by every gathering/production worker).

- [ ] **Step 1: Add the grade badge to the harvest row**

Replace the row content (currently ends after the quantity `Text` and rarity `Text`) with the quantity, a `GradeBadge`, then the rarity label:

```kotlin
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.isNew) {
                            Text(
                                "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            "×${item.quantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        GradeBadge(grade = item.grade)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            item.rarity.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = rarityColor
                        )
                    }
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification**

Run the app (see `run` skill or `./gradlew installDebug` + launch), go to Gather, run a forage/farm/husbandry collection, open the harvest result dialog. Confirm each item row shows a colored grade badge (Crude/Common/Fine/Superb/Pristine) between the quantity and the rarity label.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GatheringScreen.kt
git commit -m "[hc] Show ingredient grade badge in harvest result dialog"
```

---

### Task 2: Home band card taps through to Missions

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HomeScreen.kt:179-200` (band thumbnail `Card`)

**Interfaces:**
- Consumes: existing `onNavigate: (String) -> Unit` parameter already present on `HomeScreen` (default `{}`).

- [ ] **Step 1: Add onClick to the band card**

```kotlin
        if (activeBandName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(activeBandName)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                onClick = { onNavigate("missions") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val memberLine = when {
                        woundedCount > 0 -> "$aliveCount active · $woundedCount wounded"
                        aliveCount > 0   -> "$aliveCount active"
                        else             -> "Members not yet initialized"
                    }
                    Text(memberLine, style = MaterialTheme.typography.bodySmall)
                    if (growingCount > 0) {
                        Text(
                            "$growingCount plot${if (growingCount > 1) "s" else ""} growing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification**

Run the app, on Home tap the band summary card, confirm it navigates to Missions.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HomeScreen.kt
git commit -m "[hc] Home band card now taps through to Missions"
```

---

### Task 3: Boss HP on Missions encounter cards

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt:69-81` (`EncounterDetail`)
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt:162-176` (`encounters` StateFlow)
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt` (`EncounterCard`, currently lines 241-297)

**Interfaces:**
- Produces: `EncounterDetail.bossResolve: Int` — the boss's total resolve (HP-equivalent) for the encounter's first stage, sourced from `Stage.resolve` (already authored in `encounters.json`).

- [ ] **Step 1: Add the field to EncounterDetail**

In `UiModels.kt`, add `bossResolve` after `physMitPct`:

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

- [ ] **Step 2: Populate it in BandViewModel**

In `BandViewModel.kt`, inside the `encounters` StateFlow's `map`, add `bossResolve` alongside `physMitPct`:

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

- [ ] **Step 3: Display it on the encounter card**

In `MissionsScreen.kt`, inside `EncounterCard` (the `Column` at line ~260), add a boss HP row right after the name/difficulty `Row` and before the "unprovisioned" check:

```kotlin
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Boss HP: ${encounter.bossResolve}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HpBar(
                fraction = 1f,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
```

(`HpBar` is already defined as a private composable later in this same file, at line ~463 — reusable within-file.)

- [ ] **Step 4: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Manual verification**

Run the app, go to Missions, confirm every encounter card shows a "Boss HP: N" line with a full red bar beneath it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt
git commit -m "[hc] Show boss HP on Missions encounter cards"
```

---

### Task 4: OddsEstimator — Monte-Carlo win-rate sampler

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/OddsEstimator.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/OddsEstimatorTest.kt`

**Interfaces:**
- Consumes: `EncounterEngine.resolve(stage: Stage, members: List<MemberInput>, seed: Long): EncounterResult`, `Outcome.VICTORY` (both already in `engine/EncounterEngine.kt`)
- Produces: `enum class OddsLabel { OUTMATCHED, EVEN_FIGHT, FAVORED, CRUSHING }`; `OddsEstimator.estimate(stage: Stage, members: List<MemberInput>, trials: Int = 75): OddsLabel`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/OddsEstimatorTest.kt
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage
import org.junit.Test
import org.junit.Assert.assertEquals

class OddsEstimatorTest {

    private fun stage(resolve: Int, drain: Float, spike: Float, spikeIv: Int) = Stage(
        stageId = "main", label = "test", type = "attrition",
        objective = "kill", durationSec = 1500, resolve = resolve,
        drain = drain, spike = spike, spikeIntervalSec = spikeIv, physMitPct = 0f
    )

    private fun party() = listOf(
        MemberInput("warden",  "warden",  4f, 2f, 5f, 3f, 2f),
        MemberInput("fighter", "fighter", 3f, 5f, 3f, 4f, 4f),
        MemberInput("keeper",  "keeper",  2f, 3f, 3f, 5f, 4f),
        MemberInput("captain", "captain", 3f, 2f, 4f, 5f, 4f)
    )

    @Test
    fun `an overwhelming encounter is labeled Outmatched`() {
        val brutalStage = stage(resolve = 200000, drain = 120f, spike = 300f, spikeIv = 5)
        assertEquals(OddsLabel.OUTMATCHED, OddsEstimator.estimate(brutalStage, party()))
    }

    @Test
    fun `an easy encounter is labeled Favored or Crushing`() {
        val easyStage = stage(resolve = 15000, drain = 2f, spike = 30f, spikeIv = 20)
        val label = OddsEstimator.estimate(easyStage, party())
        assert(label == OddsLabel.FAVORED || label == OddsLabel.CRUSHING) {
            "Expected Favored or Crushing, got $label"
        }
    }

    @Test
    fun `an empty band is always Outmatched`() {
        val anyStage = stage(resolve = 100, drain = 1f, spike = 1f, spikeIv = 20)
        assertEquals(OddsLabel.OUTMATCHED, OddsEstimator.estimate(anyStage, emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.engine.OddsEstimatorTest"`
Expected: FAIL — "Unresolved reference: OddsEstimator" (compile failure)

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/OddsEstimator.kt
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage

enum class OddsLabel { OUTMATCHED, EVEN_FIGHT, FAVORED, CRUSHING }

// Live pre-battle odds estimate: samples the real encounter simulation many
// times with varying seeds and buckets the resulting win rate into a
// plain-language label. Cutoffs are starting points, tunable later via the
// balance harness — not locked.
object OddsEstimator {
    fun estimate(stage: Stage, members: List<MemberInput>, trials: Int = 75): OddsLabel {
        if (members.isEmpty()) return OddsLabel.OUTMATCHED
        var wins = 0
        for (i in 0 until trials) {
            val result = EncounterEngine.resolve(stage, members, seed = i.toLong())
            if (result.outcome == Outcome.VICTORY) wins++
        }
        val winRate = wins.toFloat() / trials
        return when {
            winRate < 0.25f -> OddsLabel.OUTMATCHED
            winRate < 0.60f -> OddsLabel.EVEN_FIGHT
            winRate < 0.85f -> OddsLabel.FAVORED
            else -> OddsLabel.CRUSHING
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.liquidcode7.hearthcraft.engine.OddsEstimatorTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/OddsEstimator.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/OddsEstimatorTest.kt
git commit -m "[hc] Add OddsEstimator: Monte-Carlo win-rate sampler for pre-battle odds"
```

---

### Task 5: Wire the odds estimate into Missions

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt`

**Interfaces:**
- Consumes: `OddsEstimator.estimate(...)`, `OddsLabel` (Task 4); `BandRepository.memberInputsForBand(bandId, draughtPotency, memberFood)` (existing)
- Produces: `BandViewModel.oddsLabel: StateFlow<OddsLabel?>` (null = not yet computed, or no encounter selected)

- [ ] **Step 1: Add the odds StateFlow to BandViewModel**

Add these imports to `BandViewModel.kt`:

```kotlin
import com.liquidcode7.hearthcraft.engine.OddsLabel
import com.liquidcode7.hearthcraft.engine.OddsEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
```

Add this private data class near the top of the class body and the new StateFlow + `init` block after `sendOnEncounter()`:

```kotlin
    private data class OddsQuery(
        val encounter: EncounterDetail?,
        val foodMap: Map<String, PreparedFoodDetail?>,
        val draught: Float,
        val bandId: String?
    )

    private val _oddsLabel = MutableStateFlow<OddsLabel?>(null)
    val oddsLabel: StateFlow<OddsLabel?> = _oddsLabel.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_selectedEncounter, _memberFood, _draughtPotency, activeBandId) { encounter, foodMap, draught, bandId ->
                OddsQuery(encounter, foodMap, draught, bandId)
            }.collectLatest { query ->
                _oddsLabel.value = null
                val encounter = query.encounter ?: return@collectLatest
                val bandId = query.bandId ?: return@collectLatest
                val enc = encounterRepo.get(encounter.encounterId) ?: return@collectLatest
                val stage = enc.stages.firstOrNull() ?: return@collectLatest
                val members = withContext(Dispatchers.Default) {
                    band.memberInputsForBand(bandId, query.draught, query.foodMap)
                }
                if (members.isEmpty()) return@collectLatest
                _oddsLabel.value = withContext(Dispatchers.Default) {
                    OddsEstimator.estimate(stage, members)
                }
            }
        }
    }
```

- [ ] **Step 2: Display the odds label on the selected encounter's card**

In `MissionsScreen.kt`, collect the new flow near the other `collectAsState()` calls at the top of `MissionsScreen`:

```kotlin
    val oddsLabel by bandViewModel.oddsLabel.collectAsState()
```

Pass it into `EncounterCard` (add an `oddsLabel: OddsLabel?` parameter, only meaningful when this card is the selected one):

```kotlin
@Composable
private fun EncounterCard(
    encounter: EncounterDetail,
    isSelected: Boolean,
    provisioned: Boolean,
    oddsLabel: com.liquidcode7.hearthcraft.engine.OddsLabel?,
    onClick: () -> Unit
) {
```

and in the call site (the `unlockedEncounters.forEach` loop around line 145):

```kotlin
            unlockedEncounters.forEach { enc ->
                EncounterCard(
                    encounter = enc,
                    isSelected = enc.encounterId == selectedEncounter?.encounterId,
                    provisioned = allAliveProvisioned,
                    oddsLabel = if (enc.encounterId == selectedEncounter?.encounterId) oddsLabel else null,
                    onClick = { bandViewModel.selectEncounter(enc) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
```

Inside `EncounterCard`'s `Column`, right after the boss HP bar added in Task 3, add:

```kotlin
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                val (oddsText, oddsColor) = when (oddsLabel) {
                    com.liquidcode7.hearthcraft.engine.OddsLabel.OUTMATCHED -> "Outmatched" to MaterialTheme.colorScheme.error
                    com.liquidcode7.hearthcraft.engine.OddsLabel.EVEN_FIGHT -> "Even Fight" to MaterialTheme.colorScheme.onSurfaceVariant
                    com.liquidcode7.hearthcraft.engine.OddsLabel.FAVORED    -> "Favored" to MaterialTheme.colorScheme.primary
                    com.liquidcode7.hearthcraft.engine.OddsLabel.CRUSHING   -> "Crushing" to Color(0xFF4CAF50)
                    null -> "Calculating odds…" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(oddsText, style = MaterialTheme.typography.labelMedium, color = oddsColor)
            }
```

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual verification**

Run the app, go to Missions, tap an encounter to select it. Confirm "Calculating odds…" appears briefly then resolves to one of Outmatched/Even Fight/Favored/Crushing. Change the assigned food (once Task 6 lands) or draught and confirm the label recomputes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt
git commit -m "[hc] Wire live odds estimate into the selected Missions encounter card"
```

---

### Task 6: Inline provisioning UI on Missions

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt` (the "Food selection" section, currently lines 111-129, and the `FoodRow` composable)

**Interfaces:**
- Consumes: `BandViewModel.assignFoodToMember(memberId, food)`, `BandViewModel.memberFood`, `BandViewModel.members` (all existing)

- [ ] **Step 1: Replace the read-only food list with a real per-member assignment list**

Replace the current "Food selection" section (the `Text("Food", ...)` block through the `preparedFood.forEach { FoodRow(...) }` block) with a per-member provisioning list:

```kotlin
        // ── Provisioning ────────────────────────────────────────────────────
        Text("Provisioning", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        if (preparedFood.isEmpty()) {
            Text(
                "Nothing cooked. Head to Kitchen first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            var pickingFor by remember { mutableStateOf<String?>(null) }
            if (pickingFor != null) {
                FoodPickerDialog(
                    options = preparedFood,
                    onSelect = { food -> bandViewModel.assignFoodToMember(pickingFor!!, food); pickingFor = null },
                    onDismiss = { pickingFor = null }
                )
            }
            members.filter { it.isAlive }.forEach { member ->
                val food = memberFood[member.memberId]
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                member.role.replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (food != null) {
                                val statLine = buildString {
                                    if (food.primaryStat != null) append("+${food.primaryBoost} ${food.primaryStat.uppercase()}")
                                    if (food.secondaryStat != null) append("  +${food.secondaryBoost} ${food.secondaryStat.uppercase()}")
                                }
                                Text(
                                    if (statLine.isNotEmpty()) "${food.name}  $statLine" else food.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text("Unfed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { pickingFor = member.memberId }) {
                            Text(if (food != null) "Change" else "Assign")
                        }
                        if (food != null) {
                            TextButton(onClick = { bandViewModel.assignFoodToMember(member.memberId, null) }) {
                                Text("Clear")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
```

This requires the imports `androidx.compose.material3.TextButton`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue` — check `MissionsScreen.kt`'s import block and add any missing ones (`Card`, `Alignment`, `Modifier`, `MaterialTheme` are already imported).

- [ ] **Step 2: Add the FoodPickerDialog composable to MissionsScreen.kt**

This is the same dialog already in `BandScreen.kt` (it will be deleted from there in Task 7) — add it as a private composable at the bottom of `MissionsScreen.kt`:

```kotlin
@Composable
private fun FoodPickerDialog(
    options: List<PreparedFoodDetail>,
    onSelect: (PreparedFoodDetail) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose food") },
        text = {
            Column {
                if (options.isEmpty()) {
                    Text(
                        "No food prepared. Cook something in the Kitchen first.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    options.forEach { food ->
                        TextButton(onClick = { onSelect(food) }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(food.name)
                                    if (food.primaryStat != null) {
                                        val secondaryPart = if (food.secondaryStat != null)
                                            "  +${food.secondaryBoost} ${food.secondaryStat.uppercase()}"
                                        else ""
                                        Text(
                                            "+${food.primaryBoost} ${food.primaryStat.uppercase()}$secondaryPart",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                GradeBadge(food.grade)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("×${food.quantity}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

Add `import androidx.compose.material3.AlertDialog` if not already present in `MissionsScreen.kt`.

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual verification**

Run the app, cook some food in Kitchen, go to Missions, confirm you can assign/change/clear food per member directly on the Missions screen without visiting Band. Select an encounter and send — confirm the mission launches with the assigned provisioning.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt
git commit -m "[hc] Missions tab: inline per-member provisioning, no dialog hop"
```

---

### Task 7: Remove the duplicate send-flow from Band tab

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt`

**Interfaces:**
- None — this is pure removal now that Task 6 makes Missions fully self-sufficient.

- [ ] **Step 1: Remove the send-flow UI and state from BandScreen**

In `BandScreen.kt`:
- Remove the `showProvisioningDialog` state and the `if (showProvisioningDialog && selectedEncounter != null) { ProvisioningDialog(...) }` block (lines 66, 72-88).
- Remove the `else if (activeMission == null && activeEncounterSession == null) { ... EncounterSendRow ... }` block (lines 167-196) — replace with nothing (Band no longer offers a send action; that whole `else if` branch and its contents are deleted, leaving only the `!hasAliveMembers` branch and the second-band-unlock card below it).
- Remove the composables `EncounterSendRow`, `ProvisioningDialog`, `FoodPickerDialog` entirely (lines 493-517, 519-593, 595-646).
- Remove now-unused imports: `EncounterDetail`, `PreparedFoodDetail` stay only if still referenced elsewhere in the file (check — `MemberDetailDialog` doesn't use them, so remove both imports), `AlertDialog` stays (still used by `MemberDetailDialog` in this task's scope — leave it), `FilterChip` stays (used by `BandSwitcher`).
- Remove the `encounters`, `selectedEncounter`, `memberFood`, `preparedFood`, `allAliveProvisioned` (if only used by the removed code — check `allAliveHealthy` is still used by the wounded-member message, keep it) StateFlow collections that are now unused in this screen. Keep `activeMission`, `activeEncounterSession`, `members`, `hasAliveMembers`, `viewingSecond`, `isSecondBandUnlocked`, `firstBandId`, `secondBandId`, `availableBandsForUnlock`, `allAliveHealthy`.
- Remove the `inventoryViewModel: InventoryViewModel = hiltViewModel()` parameter and its `preparedFood` collection if nothing else in this file uses it after the above removals.

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (fix any now-unused-import or now-unresolved-reference errors surfaced by the removal — this step should leave `BandScreen.kt` compiling clean with only roster-display code remaining)

- [ ] **Step 3: Manual verification**

Run the app, go to Band tab. Confirm there is no "Send" action anywhere on this screen — only the roster, wound recovery timers, and band switcher (wound recovery list will move to House of Healing in Task 17; leave it for now). Confirm Missions tab still fully sends missions (from Task 6).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt
git commit -m "[hc] Remove duplicate mission-send flow from Band tab"
```

---

### Task 8: Journal — per-character stats and bio

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/JournalScreen.kt`

**Interfaces:**
- Consumes: `BandViewModel.members: StateFlow<List<BandMemberWithState>>` (existing, already carries `name, personality, role, isAlive, woundStatus, level, might, agility, vitality, will, fate`)

- [ ] **Step 1: Inject BandViewModel and add a Characters section**

In `JournalScreen.kt`, add the second ViewModel parameter and collect `members`:

```kotlin
@Composable
fun JournalScreen(
    onBack: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel(),
    bandViewModel: BandViewModel = hiltViewModel()
) {
    val discoveredRecipes by viewModel.discoveredRecipes.collectAsState()
    val members by bandViewModel.members.collectAsState()
```

Add `import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel` and `import com.liquidcode7.hearthcraft.ui.viewmodel.BandMemberWithState`.

Insert a new "Characters" section right after the header `Row` and before the "Stats glossary" section:

```kotlin
            // ── Characters ─────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            JournalSection("Characters")
            Spacer(modifier = Modifier.height(8.dp))
            members.forEach { member ->
                CharacterCard(member)
                Spacer(modifier = Modifier.height(8.dp))
            }
```

- [ ] **Step 2: Add the CharacterCard composable**

Add this private composable near the bottom of the file (mirrors `MemberDetailDialog`'s content from `BandScreen.kt`, without the dialog wrapper):

```kotlin
@Composable
private fun CharacterCard(member: BandMemberWithState) {
    val (statusLabel, statusColor) = when {
        !member.isAlive -> "Fallen" to MaterialTheme.colorScheme.error
        member.woundStatus == "grievously_wounded" -> "Grievous Wound" to MaterialTheme.colorScheme.error
        member.woundStatus == "wounded" -> "Wounded" to Color(0xFFFF9800)
        else -> "Active" to MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium)
                    if (member.role.isNotEmpty()) {
                        Text(
                            "${member.role} — Level ${member.level}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                member.personality,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (member.isAlive) {
                Spacer(modifier = Modifier.height(12.dp))
                JournalStatBar(label = "VIT", value = member.vitality)
                Spacer(modifier = Modifier.height(4.dp))
                JournalStatBar(label = "MGT", value = member.might)
                Spacer(modifier = Modifier.height(4.dp))
                JournalStatBar(label = "AGI", value = member.agility)
                Spacer(modifier = Modifier.height(4.dp))
                JournalStatBar(label = "WIL", value = member.will)
                Spacer(modifier = Modifier.height(4.dp))
                JournalStatBar(label = "FAT", value = member.fate)
                roleAbility(member.role)?.let { (abilityName, abilityDesc) ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(abilityName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(abilityDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun JournalStatBar(label: String, value: Int, max: Int = 10) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
        LinearProgressIndicator(
            progress = { value.toFloat() / max.coerceAtLeast(value) },
            modifier = Modifier.weight(1f).height(8.dp)
        )
        Text(" $value", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
    }
}

private fun roleAbility(role: String): Pair<String, String>? = when (role.lowercase()) {
    "warden" -> "The Horn of Gondor" to
        "Can intercept a killing blow aimed at the Keeper, up to three times per engagement. The Warden steps between the blade and the one who cannot fall."
    "fighter" -> "Black Arrow" to
        "Deals damage that scales with both Agility and Might, making the fighter the party's primary offensive force. Armor reduces effectiveness — bring a potency draught if the enemy is mailed."
    "keeper" -> "Hands of Healing" to
        "When a companion is downed, the Keeper calls them back with a healing burst. Can be used up to five times per engagement. Without the Keeper, fallen members stay fallen."
    "captain" -> "Wrath, Ruin, and the Red Dawn" to
        "When the Captain calls, the entire company fights with renewed fury — all damage output rises by half again for ten strikes. Once per engagement."
    else -> null
}
```

Add imports: `androidx.compose.material3.CardDefaults`, `androidx.compose.material3.LinearProgressIndicator`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.graphics.Color`, `androidx.compose.foundation.layout.width`, `androidx.compose.foundation.layout.height` (check existing imports first, add only what's missing).

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual verification**

Run the app, open Journal, confirm a "Characters" section appears above "Stats" showing each current band member's name, role, level, status, personality bio, five stat bars, and role ability — matching what used to be in Band tab's member-tap dialog.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/JournalScreen.kt
git commit -m "[hc] Journal: add per-character stats and bio section"
```

---

### Task 9: Band tab taps through to Journal

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt` (pass a navigation callback to `BandScreen`)

**Interfaces:**
- Produces: `BandScreen(onOpenJournal: () -> Unit = {})` — new parameter

- [ ] **Step 1: Remove MemberDetailDialog, add navigation callback**

In `BandScreen.kt`: remove `selectedMember` state, the `selectedMember?.let { MemberDetailDialog(...) }` block, and the `MemberDetailDialog`/`StatBar`/`roleAbility` composables entirely (this content now lives in Journal per Task 8). Change the function signature and the member row click:

```kotlin
@Composable
fun BandScreen(
    onOpenJournal: () -> Unit = {},
    bandViewModel: BandViewModel = hiltViewModel()
) {
```

```kotlin
        Text("Members", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        members.forEach { member ->
            MemberRow(member = member, onClick = onOpenJournal)
            Spacer(modifier = Modifier.height(4.dp))
        }
```

- [ ] **Step 2: Wire the callback in MainScreen**

In `MainScreen.kt`, change the `"band"` route registration:

```kotlin
            composable("band") {
                BandScreen(onOpenJournal = { navController.navigate("journal") })
            }
```

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual verification**

Run the app, go to Band, tap any member row, confirm it navigates to Journal (rather than opening a dialog).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt
git commit -m "[hc] Band tab: tapping a member opens Journal instead of a dialog"
```

---

### Task 10: Bio-stage schema (framework only, unwired)

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration17To18.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/BandMemberState.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt` (version bump)
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt` (register migration)

**Interfaces:**
- Produces: three new columns on `BandMemberState` — `missionsSurvived: Int`, `woundsSurvived: Int`, `grievousWoundsSurvived: Int`, all defaulting to 0. No code anywhere increments them yet — this is schema-only plumbing for a future bio-stage trigger design.

- [ ] **Step 1: Write the migration**

```kotlin
// app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration17To18.kt
package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration17To18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN missionsSurvived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN woundsSurvived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN grievousWoundsSurvived INTEGER NOT NULL DEFAULT 0")
    }
}
```

- [ ] **Step 2: Add the fields to BandMemberState**

Append to the `BandMemberState` data class (after `combatXp`):

```kotlin
    @ColumnInfo(defaultValue = "0") val missionsSurvived: Int = 0,
    @ColumnInfo(defaultValue = "0") val woundsSurvived: Int = 0,
    @ColumnInfo(defaultValue = "0") val grievousWoundsSurvived: Int = 0
```

(Match the exact `@ColumnInfo(defaultValue = "0")` style already used for `woundedDurationMs` etc. in this file.)

- [ ] **Step 3: Bump the database version and register the migration**

In `HearthCraftDatabase.kt`, change `version = 17` to `version = 18` in the `@Database` annotation.

In `di/DatabaseModule.kt`, change:
```kotlin
.addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17)
```
to:
```kotlin
.addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17, Migration17To18)
```

- [ ] **Step 4: Build (this regenerates the schema snapshot)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, and a new `app/schemas/com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase/18.json` file is generated.

- [ ] **Step 5: Manual verification**

Run the app on a device/emulator with existing save data (or fresh install), confirm it launches without a Room migration crash — the three new columns exist with default 0, nothing reads or writes them yet.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration17To18.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/BandMemberState.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt app/schemas/
git commit -m "[hc] Add unwired bio-stage counters to BandMemberState (framework only)"
```

---

### Task 11: Keep HoH items out of the food UI; add HoH-item read model

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/InventoryViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/InventoryRepository.kt`

**Interfaces:**
- Produces: `PreparedHohItemDetail(recipeId, name, quantity, grade, treatsWoundTypes: List<String>)`; `InventoryViewModel.preparedHohItems: StateFlow<List<PreparedHohItemDetail>>`; `InventoryRepository.removePreparedFood(recipeId: String, grade: Int)` (new grade-aware overload)
- Fixes: `InventoryViewModel.preparedFood` currently has no `recipeClass` filter — any HoH item crafted via `HohCookingWorker`'s existing `inventory.addPreparedFood(...)` call would otherwise leak into the Missions/Band food-assignment pickers.

- [ ] **Step 1: Filter preparedFood to food-class recipes only**

In `InventoryViewModel.kt`, change the `preparedFood` StateFlow's `mapNotNull`:

```kotlin
    val preparedFood: StateFlow<List<PreparedFoodDetail>> = combine(
        inventory.observePreparedFood(),
        player.observe()
    ) { foods, state ->
        foods.mapNotNull { pf ->
            val recipe = gameData.recipes.find { it.id == pf.recipeId && it.recipeClass == "food" } ?: return@mapNotNull null
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

(Note: the unused `state` parameter was already unused for cooking level in the original — left as-is to keep the diff minimal; `cookingLevel` was assigned but never read in the original code, so no behavior changes there.)

- [ ] **Step 2: Add the HoH-item read model**

In `UiModels.kt`, add:

```kotlin
data class PreparedHohItemDetail(
    val recipeId: String,
    val name: String,
    val quantity: Int,
    val grade: Int,
    val treatsWoundTypes: List<String>
)
```

In `InventoryViewModel.kt`, add a parallel StateFlow:

```kotlin
    val preparedHohItems: StateFlow<List<PreparedHohItemDetail>> = inventory.observePreparedFood()
        .map { foods ->
            foods.mapNotNull { pf ->
                val recipe = gameData.recipes.find { it.id == pf.recipeId && it.recipeClass == "hoh" } ?: return@mapNotNull null
                PreparedHohItemDetail(
                    recipeId = pf.recipeId,
                    name = recipe.name,
                    quantity = pf.quantity,
                    grade = pf.grade,
                    treatsWoundTypes = recipe.treatsWoundTypes
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

- [ ] **Step 3: Add a grade-aware removePreparedFood overload**

In `InventoryRepository.kt`, add alongside the existing `removePreparedFood(recipeId: String)`:

```kotlin
    suspend fun removePreparedFood(recipeId: String, grade: Int) {
        preparedFoodDao.removeOne(recipeId, grade)
    }
```

- [ ] **Step 4: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/InventoryViewModel.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/InventoryRepository.kt
git commit -m "[hc] Split prepared-item inventory by class: food UI never sees HoH items"
```

---

### Task 12: HoH crafting session (mirrors Kitchen's cooking session)

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HohCookingSession.kt`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/HohCookingSessionDao.kt`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration18To19.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/SessionRepository.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HohCookingWorker.kt` (clear the session on completion)

**Interfaces:**
- Produces: `SessionRepository.observeHohCookingSession(): Flow<HohCookingSession?>`, `SessionRepository.startHohCooking(session: HohCookingSession)`, `SessionRepository.clearHohCookingSession()`

- [ ] **Step 1: Entity and DAO (single slot — HoH crafts one preparation at a time)**

```kotlin
// app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HohCookingSession.kt
package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hoh_cooking_session")
data class HohCookingSession(
    @PrimaryKey val id: Int = 0,
    val recipeId: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val workRequestId: String
)
```

```kotlin
// app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/HohCookingSessionDao.kt
package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.HohCookingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface HohCookingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun start(session: HohCookingSession)

    @Query("SELECT * FROM hoh_cooking_session WHERE id = 0 LIMIT 1")
    fun observe(): Flow<HohCookingSession?>

    @Query("DELETE FROM hoh_cooking_session WHERE id = 0")
    suspend fun clear()
}
```

- [ ] **Step 2: Migration and database registration**

```kotlin
// app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration18To19.kt
package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration18To19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS hoh_cooking_session (
                id INTEGER NOT NULL PRIMARY KEY,
                recipeId TEXT NOT NULL, startedAtMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL, workRequestId TEXT NOT NULL
            )
        """.trimIndent())
    }
}
```

In `HearthCraftDatabase.kt`:
- Add `import com.liquidcode7.hearthcraft.data.db.dao.HohCookingSessionDao`
- Change `version = 17` (note: this is now `version = 18` after Task 10 — bump it one further) to `version = 19`
- Add `HohCookingSession::class,` to the `entities = [...]` list (after `EncounterTicks::class,`)
- Add `abstract fun hohCookingSessionDao(): HohCookingSessionDao` after `abstract fun encounterTicksDao(): EncounterTicksDao`

In `DatabaseModule.kt`:
- Add `import com.liquidcode7.hearthcraft.data.db.dao.HohCookingSessionDao` and `import com.liquidcode7.hearthcraft.data.db.Migration18To19`
- Change `.addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17, Migration17To18)` (the version after Task 10) to `.addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17, Migration17To18, Migration18To19)`
- Add `@Provides fun provideHohCookingSessionDao(db: HearthCraftDatabase): HohCookingSessionDao = db.hohCookingSessionDao()` after `provideEncounterTicksDao`

- [ ] **Step 3: SessionRepository methods**

In `SessionRepository.kt`, add the constructor parameter and methods mirroring the existing cooking-slot ones exactly:

```kotlin
    // constructor: add `private val hohCookingDao: HohCookingSessionDao,`
    fun observeHohCookingSession(): Flow<HohCookingSession?> = hohCookingDao.observe()
    suspend fun startHohCooking(session: HohCookingSession) = hohCookingDao.start(session)
    suspend fun clearHohCookingSession() = hohCookingDao.clear()
```

Add the matching imports (`com.liquidcode7.hearthcraft.data.db.HohCookingSession`, `com.liquidcode7.hearthcraft.data.db.dao.HohCookingSessionDao`).

- [ ] **Step 4: Clear the session when HohCookingWorker completes**

In `HohCookingWorker.kt`, inject `SessionRepository` and clear the session at the end of `doWork()`:

```kotlin
@HiltWorker
class HohCookingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val sessions: SessionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recipeId = inputData.getString(KEY_RECIPE_ID) ?: return Result.failure()
        val grade = inputData.getInt(KEY_GRADE, 0)
        val isFirst = inventory.preparedFoodQty(recipeId) == 0
        inventory.addPreparedFood(recipeId, grade)
        val xp = if (isFirst) PlayerRepository.XP_HOH_FIRST else PlayerRepository.XP_HOH_REPEAT
        player.addHohXp(xp)
        sessions.clearHohCookingSession()
        return Result.success()
    }

    companion object {
        const val KEY_RECIPE_ID = "recipeId"
        const val KEY_GRADE = "grade"
        const val NOTIFICATION_ID = 56

        fun buildRequest(recipeId: String, durationMs: Long, grade: Int): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HohCookingWorker>()
                .setInputData(workDataOf(KEY_RECIPE_ID to recipeId, KEY_GRADE to grade))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
```

Add `import com.liquidcode7.hearthcraft.data.repository.SessionRepository`.

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, new `app/schemas/.../19.json` generated.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HohCookingSession.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/HohCookingSessionDao.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/Migration18To19.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/SessionRepository.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HohCookingWorker.kt app/schemas/
git commit -m "[hc] Add HoH cooking session tracking (mirrors Kitchen's cooking session)"
```

---

### Task 13: HohRepository — two-step apply from a crafted item

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/HohRepository.kt`

**Interfaces:**
- Removes: `applyPreparation(memberId, recipeId, ingredientGrades)` (dead code implementing the one-step design the user did not choose)
- Produces: `HohRepository.applyPreparedItem(memberId: String, recipeId: String, grade: Int): ApplyResult` — same timer/XP/session logic as the old function, but takes an already-known `grade` (no `CookQuality.resolveDishGrade` call needed — the item was already graded when crafted in Task 14) and consumes one `PreparedFood` row of that exact recipe+grade.

- [ ] **Step 1: Replace applyPreparation with applyPreparedItem**

```kotlin
    suspend fun applyPreparedItem(
        memberId: String,
        recipeId: String,
        grade: Int
    ): ApplyResult {
        val member = memberDao.get(memberId) ?: error("Member $memberId not found")
        val recipe = gameData.recipes.find { it.id == recipeId }
            ?: error("Recipe $recipeId not found")

        inventory.removePreparedFood(recipeId, grade)

        val tier = recipe.tier.coerceIn(1, 4)

        val session = hohSessionDao.get(memberId) ?: HohSession(memberId)

        val nowMs = System.currentTimeMillis()
        val elapsed = if (member.hohTimerStartMs > 0L)
            (nowMs - member.hohTimerStartMs).coerceAtLeast(0L)
        else
            session.elapsedMsAtLastTreatment

        val prevTreated = session.treatedTypes.split(",").filter { it.isNotBlank() }.toMutableSet()
        val newlyTreated = recipe.treatsWoundTypes.toSet()
        val allTreated = prevTreated + newlyTreated

        val bestGrade = maxOf(session.bestGrade, grade)
        val bestTier = maxOf(session.bestTier, tier)

        val memberWoundTypes = member.woundTypes.split(",").filter { it.isNotBlank() }
        val allWoundsCleared = memberWoundTypes.isNotEmpty() && allTreated.containsAll(memberWoundTypes)

        val newTimer = calcTimer(memberWoundTypes, allTreated.toList(), bestGrade, bestTier)
        val creditedTimer = (newTimer - elapsed).coerceAtLeast(0L)

        hohSessionDao.upsert(
            session.copy(
                treatedTypes = allTreated.joinToString(","),
                bestGrade = bestGrade,
                bestTier = bestTier,
                elapsedMsAtLastTreatment = elapsed
            )
        )

        memberDao.setHohTimer(memberId, nowMs, creditedTimer)
        memberDao.setRecoveryBuff(memberId, bestGrade, bestTier, pending = true)

        val xp = PlayerRepository.XP_HOH_APPLY +
            if (allWoundsCleared) PlayerRepository.XP_HOH_CLEAR_BONUS else 0
        player.addHohXp(xp)

        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("hoh_$memberId")
        workManager.enqueue(HohWorker.buildRequest(memberId, creditedTimer))

        return ApplyResult(timerMs = creditedTimer, grade = grade, allWoundsCleared = allWoundsCleared)
    }
```

Remove the old `applyPreparation` function entirely. Add the constructor parameter `private val inventory: InventoryRepository,` to `HohRepository` and its import.

Note `CookQuality` import may now be unused in this file — remove it if `resolveDishGrade` is no longer called here (grade is now supplied by the caller, resolved at craft time in Task 14).

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/HohRepository.kt
git commit -m "[hc] HohRepository: applyPreparedItem replaces the unused one-step applyPreparation"
```

---

### Task 14: HohViewModel

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/HohViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/InventoryRepository.kt` (confirm `ingredientQtyAtGrade` / `removeIngredient` are accessible — already public, no change needed beyond Task 11's addition)

**Interfaces:**
- Consumes: `GameDataRepository.recipes`, `GameDataRepository.isRecipeVisible(...)` (existing), `InventoryRepository.removeIngredient(id, grade, qty)`, `CookQuality.resolveDishGrade(recipe, ingredientGrades, playerCookLevel, overrideUnlockLevel)`, `HohRepository.applyPreparedItem(memberId, recipeId, grade)`, `SessionRepository.{observeHohCookingSession, startHohCooking}`, `PlayerRepository.{addHohXp is not called here — already called by the worker}`
- Produces: `visibleHohRecipes: StateFlow<List<Recipe>>`, `hohInventoryItems: StateFlow<List<InventoryItem>>`, `selectedRecipe`/`selectRecipe`/`selectedIngredientGrades`/`setIngredientGrade` (mirrors `KitchenViewModel`), `canCraft(recipe): Boolean`, `startCrafting()`, `hohCookingSession: StateFlow<HohCookingSession?>`, `hohXpProgress: StateFlow<XpProgress>`, `applyToMember(memberId: String, item: PreparedHohItemDetail)`

- [ ] **Step 1: Write the ViewModel**

```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.HohCookingSession
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.quality.CookQuality
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.HohRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.data.repository.isRecipeVisible
import com.liquidcode7.hearthcraft.worker.HohCookingWorker
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

@HiltViewModel
class HohViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    private val sessions: SessionRepository,
    private val player: PlayerRepository,
    private val hoh: HohRepository
) : ViewModel() {

    val hohInventoryItems: StateFlow<List<InventoryItem>> = inventory.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hohCookingSession: StateFlow<HohCookingSession?> = sessions.observeHohCookingSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hohXpProgress: StateFlow<XpProgress> = player.observe()
        .map { xpProgress(it?.hohXp ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XpProgress(1, 0, 100))

    val visibleHohRecipes: StateFlow<List<Recipe>> = combine(
        player.observe(),
        player.observeDiscoveredIds(),
        player.observeFoundGrimoireIds()
    ) { state, discovered, foundGrimoires ->
        val bandId = state?.chosenBandId.orEmpty()
        gameData.recipes
            .filter { it.recipeClass == "hoh" && (it.band == bandId || it.band == "all") }
            .filter { isRecipeVisible(it, foundGrimoires, discovered) }
            .sortedBy { it.hohLevel }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe.asStateFlow()

    private val _selectedIngredientGrades = MutableStateFlow<Map<String, Int>>(emptyMap())
    val selectedIngredientGrades: StateFlow<Map<String, Int>> = _selectedIngredientGrades.asStateFlow()

    fun selectRecipe(recipe: Recipe) {
        _selectedRecipe.value = recipe
        viewModelScope.launch {
            val items = hohInventoryItems.value
            val defaults = recipe.ingredients.associate { needed ->
                val lowestGrade = items
                    .filter { it.ingredientId == needed.id && it.quantity >= needed.qty }
                    .minByOrNull { it.grade }
                    ?.grade ?: 0
                needed.id to lowestGrade
            }
            _selectedIngredientGrades.value = defaults
        }
    }

    fun setIngredientGrade(ingredientId: String, gradeOrdinal: Int) {
        _selectedIngredientGrades.value = _selectedIngredientGrades.value + (ingredientId to gradeOrdinal)
    }

    fun canCraft(recipe: Recipe): Boolean {
        val items = hohInventoryItems.value
        val chosen = _selectedIngredientGrades.value
        return recipe.ingredients.all { needed ->
            val grade = chosen[needed.id]
            if (grade != null) {
                (items.find { it.ingredientId == needed.id && it.grade == grade }?.quantity ?: 0) >= needed.qty
            } else {
                items.filter { it.ingredientId == needed.id }.sumOf { it.quantity } >= needed.qty
            }
        }
    }

    fun startCrafting() {
        val recipe = _selectedRecipe.value ?: return
        if (!canCraft(recipe)) return
        if (hohCookingSession.value != null) return
        val chosen = _selectedIngredientGrades.value
        viewModelScope.launch {
            val hohLevel = player.get()?.hohLevel ?: 1
            val dishGrade = CookQuality.resolveDishGrade(recipe, chosen, hohLevel, overrideUnlockLevel = recipe.hohLevel)

            recipe.ingredients.forEach { needed ->
                val grade = chosen[needed.id]
                if (grade != null) inventory.removeIngredient(needed.id, grade, needed.qty)
                else inventory.removeIngredient(needed.id, needed.qty)
            }

            val durationMs = recipe.durationMs
            val request = HohCookingWorker.buildRequest(recipe.id, durationMs, dishGrade)
            WorkManager.getInstance(context).enqueue(request)
            sessions.startHohCooking(
                HohCookingSession(
                    recipeId = recipe.id,
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = durationMs,
                    workRequestId = request.id.toString()
                )
            )
            _selectedRecipe.value = null
            _selectedIngredientGrades.value = emptyMap()
        }
    }

    fun applyToMember(memberId: String, item: PreparedHohItemDetail) {
        viewModelScope.launch {
            hoh.applyPreparedItem(memberId, item.recipeId, item.grade)
        }
    }

    private fun xpProgress(totalXp: Int): XpProgress {
        val level = PlayerRepository.levelForTotalXp(totalXp, PlayerRepository.Track.HOH)
        val levelStartXp = PlayerRepository.totalXpForLevel(level, PlayerRepository.Track.HOH)
        val levelEndXp = PlayerRepository.totalXpForLevel(level + 1, PlayerRepository.Track.HOH)
        return XpProgress(level = level, earned = totalXp - levelStartXp, needed = levelEndXp - levelStartXp)
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/HohViewModel.kt
git commit -m "[hc] Add HohViewModel: crafting, applying preparations, HoH level progress"
```

---

### Task 15: HouseOfHealingScreen UI + navigation

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HouseOfHealingScreen.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt` (register the `"hoh"` route)
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HomeScreen.kt` (add a NavCard)

**Interfaces:**
- Consumes: `HohViewModel` (Task 14), `BandViewModel.members` (for wounded/recovering status), `InventoryViewModel.preparedHohItems` (Task 11)

- [ ] **Step 1: Write the screen**

```kotlin
package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.ui.util.formatMs
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.HohViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.InventoryViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedHohItemDetail

@Composable
fun HouseOfHealingScreen(
    hohViewModel: HohViewModel = hiltViewModel(),
    bandViewModel: BandViewModel = hiltViewModel(),
    inventoryViewModel: InventoryViewModel = hiltViewModel()
) {
    val members by bandViewModel.members.collectAsState()
    val recipes by hohViewModel.visibleHohRecipes.collectAsState()
    val inventoryItems by hohViewModel.hohInventoryItems.collectAsState()
    val session by hohViewModel.hohCookingSession.collectAsState()
    val xpProgress by hohViewModel.hohXpProgress.collectAsState()
    val selectedRecipe by hohViewModel.selectedRecipe.collectAsState()
    val selectedGrades by hohViewModel.selectedIngredientGrades.collectAsState()
    val preparedItems by inventoryViewModel.preparedHohItems.collectAsState()

    val recovering = members.filter { it.isAlive && it.woundStatus != "healthy" }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("House of Healing", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "HoH Level ${xpProgress.level} — ${xpProgress.earned}/${xpProgress.needed} XP",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { xpProgress.earned.toFloat() / xpProgress.needed.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )

        // ── Wounded / recovering ─────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        Text("Recovering", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (recovering.isEmpty()) {
            Text("No one needs treatment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            recovering.forEach { member ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (member.woundStatus == "grievously_wounded") "Grievous" else "Wounded",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (preparedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            preparedItems.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    GradeBadge(item.grade)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(onClick = { hohViewModel.applyToMember(member.memberId, item) }) {
                                        Text("Treat")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Crafting ───────────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        Text("Preparations", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (session != null) {
            Text(
                "Brewing a preparation…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (recipes.isEmpty()) {
            Text("No preparations known yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            recipes.forEach { recipe ->
                HohRecipeCard(
                    recipe = recipe,
                    isSelected = recipe.id == selectedRecipe?.id,
                    inventoryItems = inventoryItems,
                    selectedGrades = selectedGrades,
                    onSelect = { hohViewModel.selectRecipe(recipe) },
                    onGradeChosen = { ingId, grade -> hohViewModel.setIngredientGrade(ingId, grade) },
                    onCraft = { hohViewModel.startCrafting() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HohRecipeCard(
    recipe: Recipe,
    isSelected: Boolean,
    inventoryItems: List<InventoryItem>,
    selectedGrades: Map<String, Int>,
    onSelect: () -> Unit,
    onGradeChosen: (String, Int) -> Unit,
    onCraft: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                recipe.treatsWoundTypes.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                recipe.ingredients.forEach { ing ->
                    val chosenGrade = selectedGrades[ing.id] ?: 0
                    val availableGrades = Grade.entries.filter { g ->
                        (inventoryItems.find { it.ingredientId == ing.id && it.grade == g.ordinal }?.quantity ?: 0) >= ing.qty
                    }
                    Text("${ing.id} ×${ing.qty}", style = MaterialTheme.typography.labelSmall)
                    if (availableGrades.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            availableGrades.forEach { g ->
                                FilterChip(
                                    selected = chosenGrade == g.ordinal,
                                    onClick = { onGradeChosen(ing.id, g.ordinal) },
                                    label = { Text(g.displayName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    } else if (availableGrades.size == 1) {
                        GradeBadge(availableGrades.first().ordinal)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onCraft, modifier = Modifier.fillMaxWidth()) {
                    Text("Brew")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Register the route and Home NavCard**

In `MainScreen.kt`, add inside the `NavHost` block:

```kotlin
            composable("hoh") { HouseOfHealingScreen() }
```

In `HomeScreen.kt`, replace the "Journal link" `OutlinedButton` section with a NavCard row for both Journal and House of Healing:

```kotlin
        // ── Journal & House of Healing ──────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavCard(
                label = "Journal",
                icon = Icons.Filled.Groups,
                statusLine = "Characters & recipes",
                onClick = onOpenJournal,
                modifier = Modifier.weight(1f)
            )
            NavCard(
                label = "House of Healing",
                icon = Icons.Filled.LocalDining,
                statusLine = if (woundedCount > 0) "$woundedCount recovering" else "All well",
                onClick = { onNavigate("hoh") },
                modifier = Modifier.weight(1f)
            )
        }
```

(Reuses the existing `NavCard` composable already defined in this file. `Icons.Filled.Groups` is already imported; `Icons.Filled.LocalDining` is already imported. Remove the now-replaced `OutlinedButton(onClick = onOpenJournal, ...) { Text("Journal") }` block.)

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual verification**

Run the app. On Home, confirm a "House of Healing" card appears next to "Journal" and navigates correctly. On the HoH screen: confirm the HoH level bar, the "Recovering" list (empty until a member is wounded), and the "Preparations" list (empty until a HoH grimoire/recipe is visible). If a HoH recipe is visible, select it, confirm ingredient grade chips appear, craft one, confirm "Brewing a preparation…" shows, and after the wait, confirm the crafted item appears under a wounded member's "Treat" row (if one exists) and applying it starts their recovery timer.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HouseOfHealingScreen.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HomeScreen.kt
git commit -m "[hc] Add House of Healing screen: wound status, crafting, treatment, HoH level"
```

---

### Task 16: Kitchen no longer shows HoH recipes

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt`

**Interfaces:**
- None — pure filter addition to three existing `StateFlow`s.

- [ ] **Step 1: Exclude recipeClass == "hoh" from all three recipe-list flows**

In `bandRecipes` (line ~89-91):
```kotlin
        gameData.recipes
            .filter { (it.band == bandId || it.band == "all") && it.recipeClass != "hoh" }
            .filter { bandId.isNotEmpty() && isRecipeVisible(it, foundGrimoires, discovered) }
```

In `tieredRecipes` (line ~106-108):
```kotlin
        gameData.recipes
            .filter { (it.band == bandId || it.band == "all") && bandId.isNotEmpty() && it.recipeClass != "hoh" }
            .filter { isRecipeVisible(it, foundGrimoires, discovered) }
```

In `sortedRecipes` (line ~129):
```kotlin
        val filtered = gameData.recipes.filter { (it.band == bandId || it.band == "all") && it.recipeClass != "hoh" }
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification**

Run the app, go to Kitchen, confirm no HoH-class recipe (e.g. "Athelas Poultice") appears in any tier list, even if its grimoire/level would otherwise make it visible.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt
git commit -m "[hc] Kitchen no longer lists HoH recipes — they moved to their own tab"
```

---

### Task 17: Band tab final trim — remove Recovering list

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt`

**Interfaces:**
- None — pure removal, now that Task 15's House of Healing screen shows this same information.

- [ ] **Step 1: Remove the Recovering section**

Remove the block:

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

and the now-unused `WoundRecoveryRow` composable.

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification**

Run the full app end to end: Home → Missions (select encounter, provision, send, watch battle, see recap) → Journal (character bios/stats) → Band (roster only) → House of Healing (wound status, crafting, treatment). Confirm no screen references removed code and the full loop works.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandScreen.kt
git commit -m "[hc] Band tab: remove Recovering list, now shown on House of Healing"
```
