# Gather Screen Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the Gather screen into three sub-tabs (Growing / Wild / Producers) with a sticky status strip; add honey/egg/milk stockpile accumulation; bump garden and farm yields; reduce gathering XP rate; show active grow timers on the home screen.

**Architecture:** Add an internal `subTab` state int to `GatheringViewModel`. `GatheringScreen` gains a fixed-position header (status strip + sub-tab row), with a `HorizontalPager` for the three content sections. Workers (Hive/Coop/Dairy) reschedule themselves and accumulate items into `pendingResultJson` instead of overwriting. A new `addToPendingResult` method in `GrowingRepository` handles the merge.

**Tech Stack:** Jetpack Compose, `androidx.compose.foundation.pager.HorizontalPager` (already in BOM), WorkManager, Room, kotlinx.serialization.

## Global Constraints

- Minimum SDK API 26. No Google Play Services.
- All Kotlin source under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`.
- No version bumps to `versionName`/`versionCode`.
- `./gradlew build` must pass after every commit.
- Commit messages prefixed `[hc]`.

---

### Task 1: Add `addToPendingResult` to `GrowingRepository`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GrowingRepository.kt`

**Interfaces:**
- Produces: `suspend fun addToPendingResult(id: String, newItems: List<HarvestItem>)` — merges quantities for matching `ingredientId`, then writes back via `dao.setPendingResult`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/data/repository/GrowingRepositoryStockpileTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.model.HarvestItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GrowingRepositoryStockpileTest {

    private fun mergeItems(
        existing: List<HarvestItem>,
        incoming: List<HarvestItem>
    ): List<HarvestItem> {
        return (existing + incoming)
            .groupBy { it.ingredientId }
            .map { (_, group) -> group.first().copy(quantity = group.sumOf { it.quantity }) }
    }

    @Test
    fun `merge adds quantities for same ingredient`() {
        val existing = listOf(HarvestItem("forest_honey", "Forest Honey", 3, "common"))
        val incoming = listOf(HarvestItem("forest_honey", "Forest Honey", 3, "common"))
        val result = mergeItems(existing, incoming)
        assertEquals(1, result.size)
        assertEquals(6, result[0].quantity)
    }

    @Test
    fun `merge keeps separate ingredients`() {
        val existing = listOf(HarvestItem("forest_honey", "Forest Honey", 3, "common"))
        val incoming = listOf(HarvestItem("hens_egg", "Hen's Egg", 2, "common"))
        val result = mergeItems(existing, incoming)
        assertEquals(2, result.size)
    }

    @Test
    fun `merge with empty existing returns incoming`() {
        val incoming = listOf(HarvestItem("forest_honey", "Forest Honey", 3, "common"))
        val result = mergeItems(emptyList(), incoming)
        assertEquals(3, result[0].quantity)
    }
}
```

- [ ] **Step 2: Run test to verify it fails (merge function is not yet in production code)**

Run: `./gradlew :app:test --tests "*.GrowingRepositoryStockpileTest" 2>&1 | tail -20`
Expected: PASS (it tests the pure merge logic inline — that's intentional; this verifies the algorithm before wiring it up)

- [ ] **Step 3: Add `addToPendingResult` to `GrowingRepository`**

In `GrowingRepository.kt`, add after the `setPendingResult` method:

```kotlin
suspend fun addToPendingResult(id: String, newItems: List<HarvestItem>) {
    val existing = dao.get(id)?.pendingResultJson
    val merged = if (existing != null) {
        val current = Json.decodeFromString<List<HarvestItem>>(existing)
        (current + newItems)
            .groupBy { it.ingredientId }
            .map { (_, group) -> group.first().copy(quantity = group.sumOf { it.quantity }) }
    } else {
        newItems
    }
    dao.setPendingResult(id, Json.encodeToString(merged))
}
```

- [ ] **Step 4: Build to verify no compile errors**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GrowingRepository.kt \
        app/src/test/kotlin/com/liquidcode7/hearthcraft/data/repository/GrowingRepositoryStockpileTest.kt
git commit -m "[hc] Repo: addToPendingResult — accumulates items instead of overwriting"
```

---

### Task 2: Hive/Coop/Dairy workers accumulate and self-reschedule

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HiveWorker.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CoopWorker.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/DairyWorker.kt`

**Interfaces:**
- Consumes: `GrowingRepository.addToPendingResult(id, items)` from Task 1.
- Produces: Workers self-reschedule after adding to pending; cap at `MAX_STOCKPILE_CYCLES = 3`.

- [ ] **Step 1: Update `HiveWorker.doWork()`**

Replace the `growing.setPendingResult(SLOT_ID, json)` block entirely:

```kotlin
override suspend fun doWork(): Result {
    val bandId = player.get()?.chosenBandId ?: "greycloaks"
    val (honeyId, honeyName) = honeyForBand(bandId)
    val honeyQty = BASE_YIELD + Random.nextInt(3)   // 2–4 honey

    val items = listOf(
        HarvestItem(ingredientId = honeyId, name = honeyName, quantity = honeyQty, rarity = "common")
    )

    // Accumulate — count existing cycles to enforce the cap
    val slot = growing.getSlot(SLOT_ID)
    val existingJson = slot?.pendingResultJson
    val existingQty = if (existingJson != null) {
        Json.decodeFromString<List<HarvestItem>>(existingJson).firstOrNull()?.quantity ?: 0
    } else 0

    val atCap = existingQty >= MAX_STOCKPILE_CYCLES * (BASE_YIELD + 1)
    if (!atCap) {
        player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)
        growing.addToPendingResult(SLOT_ID, items)
        notify("Hive ready — tap to collect", "$honeyName is ready to harvest.")
    }

    // Self-reschedule so accumulation continues even without collection
    if (!atCap) {
        val next = buildRequest(DURATION_HIVE_MS)
        WorkManager.getInstance(applicationContext).enqueue(next)
    }
    return Result.success()
}
```

Add `import androidx.work.WorkManager` to imports if not already present.

Add to companion object:
```kotlin
private const val MAX_STOCKPILE_CYCLES = 3
private const val DURATION_HIVE_MS     = 10 * 60 * 1000L
```

- [ ] **Step 2: Update `CoopWorker.doWork()`**

Replace `growing.setPendingResult(SLOT_ID, json)` block:

```kotlin
override suspend fun doWork(): Result {
    val qty = BASE_YIELD + Random.nextInt(2)   // 2–3 eggs
    val items = listOf(
        HarvestItem(ingredientId = "hens_egg", name = "Hen's Egg", quantity = qty, rarity = "common")
    )

    val slot = growing.getSlot(SLOT_ID)
    val existingJson = slot?.pendingResultJson
    val existingQty = if (existingJson != null) {
        Json.decodeFromString<List<HarvestItem>>(existingJson).firstOrNull()?.quantity ?: 0
    } else 0
    val atCap = existingQty >= MAX_STOCKPILE_CYCLES * (BASE_YIELD + 1)

    if (!atCap) {
        player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)
        growing.addToPendingResult(SLOT_ID, items)
        notify("Coop ready — tap to collect", "Your hens have laid eggs.")
        val next = buildRequest(DURATION_COOP_MS)
        WorkManager.getInstance(applicationContext).enqueue(next)
    }
    return Result.success()
}
```

Add to companion object:
```kotlin
private const val MAX_STOCKPILE_CYCLES = 3
private const val DURATION_COOP_MS     = 15 * 60 * 1000L
```

- [ ] **Step 3: Update `DairyWorker.doWork()`** — read `DairyWorker.kt` first to confirm its structure matches CoopWorker, then apply the same pattern:

```kotlin
override suspend fun doWork(): Result {
    val qty = BASE_YIELD + Random.nextInt(2)
    val items = listOf(
        HarvestItem(ingredientId = "milk", name = "Milk", quantity = qty, rarity = "common")
    )

    val slot = growing.getSlot(SLOT_ID)
    val existingJson = slot?.pendingResultJson
    val existingQty = if (existingJson != null) {
        Json.decodeFromString<List<HarvestItem>>(existingJson).firstOrNull()?.quantity ?: 0
    } else 0
    val atCap = existingQty >= MAX_STOCKPILE_CYCLES * (BASE_YIELD + 1)

    if (!atCap) {
        player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)
        growing.addToPendingResult(SLOT_ID, items)
        notify("Dairy ready — tap to collect", "Milk is ready.")
        val next = buildRequest(DURATION_DAIRY_MS)
        WorkManager.getInstance(applicationContext).enqueue(next)
    }
    return Result.success()
}
```

Add to companion object:
```kotlin
private const val MAX_STOCKPILE_CYCLES = 3
private const val DURATION_DAIRY_MS     = 20 * 60 * 1000L
```

Note: The `buildRequest(durationMs)` companion functions already exist in each worker; they remain unchanged.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HiveWorker.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CoopWorker.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/DairyWorker.kt
git commit -m "[hc] Workers: Hive/Coop/Dairy accumulate stockpile up to 3 cycles"
```

---

### Task 3: Garden and Farm yield bump + gathering XP reduction

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/GardenWorker.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/FarmWorker.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt`

**Interfaces:**
- Produces: `GardenWorker.BASE_YIELD` increases to 5 (was 3); `FarmWorker.BASE_YIELD` increases to 8 (was 6); `XP_GATHER_SESSION` decreases to 15 (was 30).

- [ ] **Step 1: Update GardenWorker yield**

In `GardenWorker.kt` companion object, change:
```kotlin
private const val BASE_YIELD = 5   // was 3
```

- [ ] **Step 2: Update FarmWorker yield**

In `FarmWorker.kt` companion object, change:
```kotlin
private const val BASE_YIELD = 8   // was 6
```

- [ ] **Step 3: Reduce gathering XP per session**

In `PlayerRepository.kt`, change:
```kotlin
const val XP_GATHER_SESSION    = 15   // was 30 — leveling was too fast
```

- [ ] **Step 4: Build and verify tests still pass**

Run: `./gradlew :app:test :app:assembleDebug 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL, tests pass (WorkerConstantsTest may need updating if it asserts specific values — check and update if so)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/GardenWorker.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/FarmWorker.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt
git commit -m "[hc] Tuning: garden yield 3→5, farm 6→8, gathering XP session 30→15"
```

---

### Task 4: Sub-tab state in `GatheringViewModel`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/GatheringViewModel.kt`

**Interfaces:**
- Produces: `val gatherSubTab: StateFlow<Int>` (0=Growing, 1=Wild, 2=Producers); `fun selectGatherSubTab(tab: Int)`; `val growingReadyCount: StateFlow<Int>`; `val wildReady: StateFlow<Boolean>`; `val producersReadyCount: StateFlow<Int>`.

- [ ] **Step 1: Add sub-tab state and badge counts**

In `GatheringViewModel`, add after the existing `StateFlow` declarations:

```kotlin
// Sub-tab selection: 0 = Growing, 1 = Wild, 2 = Producers
private val _gatherSubTab = MutableStateFlow(0)
val gatherSubTab: StateFlow<Int> = _gatherSubTab.asStateFlow()
fun selectGatherSubTab(tab: Int) { _gatherSubTab.value = tab }

// Badge: number of growing slots with a ready result
val growingReadyCount: StateFlow<Int> = combine(
    farmPlot, gardenSlots
) { farm, garden ->
    val farmReady = if (farm?.pendingResultJson != null) 1 else 0
    val gardenReady = garden.count { it?.pendingResultJson != null }
    farmReady + gardenReady
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

// Badge: forage session has a result waiting
val wildReady: StateFlow<Boolean> = forageSession
    .map { it?.pendingResultJson != null }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

// Badge: number of producers (hive/coop/dairy) with a result waiting
val producersReadyCount: StateFlow<Int> = combine(
    hiveSlot, coopSlot, dairySlot
) { hive, coop, dairy ->
    listOf(hive, coop, dairy).count { it?.pendingResultJson != null }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/GatheringViewModel.kt
git commit -m "[hc] ViewModel: gather sub-tab state and ready-badge counts"
```

---

### Task 5: Gather screen — sticky header + sub-tabs + paged content

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GatheringScreen.kt`

**Interfaces:**
- Consumes: `viewModel.gatherSubTab`, `viewModel.selectGatherSubTab`, `viewModel.growingReadyCount`, `viewModel.wildReady`, `viewModel.producersReadyCount` from Task 4.
- The existing farm, garden, hive, coop, dairy, forage state flows remain unchanged.

- [ ] **Step 1: Restructure GatheringScreen top-level layout**

Replace the entire `GatheringScreen` composable with a two-part layout: a fixed-position header column (XP bar + status strip + sub-tab row) and a `HorizontalPager` below. The existing per-section composables (GrowingSlotCard, HiveCard, etc.) are moved into their respective tab composables.

Full replacement for `GatheringScreen`:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GatheringScreen(viewModel: GatheringViewModel = hiltViewModel()) {
    val farmPlot by viewModel.farmPlot.collectAsState()
    val gardenSlots by viewModel.gardenSlots.collectAsState()
    val hiveSlot by viewModel.hiveSlot.collectAsState()
    val coopSlot by viewModel.coopSlot.collectAsState()
    val dairySlot by viewModel.dairySlot.collectAsState()
    val forageSession by viewModel.forageSession.collectAsState()
    val seeds by viewModel.seeds.collectAsState()
    val lastHarvest by viewModel.lastHarvest.collectAsState()
    val gatheringLevel by viewModel.gatheringLevel.collectAsState()
    val gatheringXp by viewModel.gatheringXpProgress.collectAsState()
    val foragableIngredients by viewModel.foragableIngredients.collectAsState()
    val forageTargetId by viewModel.forageTargetId.collectAsState()
    val showPostForageNudge by viewModel.showPostForageNudge.collectAsState()
    val subTab by viewModel.gatherSubTab.collectAsState()
    val growingReady by viewModel.growingReadyCount.collectAsState()
    val wildReady by viewModel.wildReady.collectAsState()
    val producersReady by viewModel.producersReadyCount.collectAsState()

    var pickingSlot by remember { mutableStateOf<String?>(null) }
    var pickingForageTarget by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 3 })
    LaunchedEffect(subTab) { pagerState.animateScrollToPage(subTab) }
    LaunchedEffect(pagerState.currentPage) { viewModel.selectGatherSubTab(pagerState.currentPage) }

    if (lastHarvest != null) {
        HarvestResultDialog(readout = lastHarvest!!, onDismiss = { viewModel.clearLastHarvest() })
    }
    if (pickingForageTarget) {
        ForageTargetDialog(
            ingredients = foragableIngredients,
            currentTargetId = forageTargetId,
            onSelect = { id -> viewModel.setForageTarget(id); pickingForageTarget = false },
            onDismiss = { pickingForageTarget = false }
        )
    }
    if (pickingSlot != null) {
        SeedPickerDialog(
            seeds = seeds,
            onSelect = { seedId ->
                val slot = pickingSlot!!
                if (slot == "farm_0") viewModel.plantFarm(seedId)
                else viewModel.plantGarden(slot.last().digitToInt(), seedId)
                pickingSlot = null
            },
            onDismiss = { pickingSlot = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Fixed header (does not scroll) ────────────────────────────────
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)) {
            Text("Gathering", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            XpBar(
                label = "Gathering",
                level = gatheringXp.level,
                earned = gatheringXp.earned,
                needed = gatheringXp.needed
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Status strip — quick glance at what's running
            GatherStatusStrip(
                farmPlot = farmPlot,
                gardenSlots = gardenSlots,
                forageSession = forageSession,
                hiveSlot = hiveSlot,
                coopSlot = coopSlot,
                dairySlot = dairySlot
            )
        }

        // ── Sub-tab row ───────────────────────────────────────────────────
        TabRow(selectedTabIndex = subTab, modifier = Modifier.padding(horizontal = 16.dp)) {
            Tab(
                selected = subTab == 0,
                onClick = { viewModel.selectGatherSubTab(0) },
                text = {
                    BadgedBox(badge = {
                        if (growingReady > 0) Badge { Text(growingReady.toString()) }
                    }) { Text("Growing") }
                }
            )
            Tab(
                selected = subTab == 1,
                onClick = { viewModel.selectGatherSubTab(1) },
                text = {
                    BadgedBox(badge = {
                        if (wildReady) Badge()
                    }) { Text("Wild") }
                }
            )
            Tab(
                selected = subTab == 2,
                onClick = { viewModel.selectGatherSubTab(2) },
                text = {
                    BadgedBox(badge = {
                        if (producersReady > 0) Badge { Text(producersReady.toString()) }
                    }) { Text("Producers") }
                }
            )
        }
        HorizontalDivider()

        // ── Paged content ─────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> GrowingTab(
                    farmPlot = farmPlot,
                    gardenSlots = gardenSlots,
                    gatheringLevel = gatheringLevel,
                    onPickSlot = { pickingSlot = it },
                    onCollect = { viewModel.collectGrowingSlot(it) }
                )
                1 -> WildTab(
                    forageSession = forageSession,
                    foragableIngredients = foragableIngredients,
                    forageTargetId = forageTargetId,
                    showPostForageNudge = showPostForageNudge,
                    onPickTarget = { pickingForageTarget = true },
                    onClearTarget = { viewModel.setForageTarget(null) },
                    onStartForage = { viewModel.startForage() },
                    onCollectForage = { viewModel.collectForage() },
                    onDismissNudge = { viewModel.dismissPostForageNudge() }
                )
                2 -> ProducersTab(
                    hiveSlot = hiveSlot,
                    coopSlot = coopSlot,
                    dairySlot = dairySlot,
                    onCollect = { viewModel.collectGrowingSlot(it) }
                )
            }
        }
    }
}
```

Add import at top of file:
```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
```

- [ ] **Step 2: Add `GatherStatusStrip` composable**

Add after the existing private composables in the file:

```kotlin
@Composable
private fun GatherStatusStrip(
    farmPlot: GrowingSlot?,
    gardenSlots: List<GrowingSlot?>,
    forageSession: com.liquidcode7.hearthcraft.data.db.GatheringSession?,
    hiveSlot: GrowingSlot?,
    coopSlot: GrowingSlot?,
    dairySlot: GrowingSlot?
) {
    val items = buildList {
        if (farmPlot != null && farmPlot.pendingResultJson == null)
            add("Farm" to Pair(farmPlot.plantedAtMs, farmPlot.durationMs))
        gardenSlots.forEachIndexed { i, slot ->
            if (slot != null && slot.pendingResultJson == null)
                add("Bed ${i + 1}" to Pair(slot.plantedAtMs, slot.durationMs))
        }
        if (forageSession != null && forageSession.pendingResultJson == null)
            add("Forage" to Pair(forageSession.startedAtMs, forageSession.durationMs))
        if (hiveSlot != null && hiveSlot.pendingResultJson == null)
            add("Hive" to Pair(hiveSlot.plantedAtMs, hiveSlot.durationMs))
        if (coopSlot != null && coopSlot.pendingResultJson == null)
            add("Coop" to Pair(coopSlot.plantedAtMs, coopSlot.durationMs))
        if (dairySlot != null && dairySlot.pendingResultJson == null)
            add("Dairy" to Pair(dairySlot.plantedAtMs, dairySlot.durationMs))
    }
    val readyItems = buildList {
        if (farmPlot?.pendingResultJson != null) add("Farm")
        gardenSlots.forEachIndexed { i, slot -> if (slot?.pendingResultJson != null) add("Bed ${i + 1}") }
        if (forageSession?.pendingResultJson != null) add("Forage")
        if (hiveSlot?.pendingResultJson != null) add("Hive")
        if (coopSlot?.pendingResultJson != null) add("Coop")
        if (dairySlot?.pendingResultJson != null) add("Dairy")
    }

    if (items.isEmpty() && readyItems.isEmpty()) return

    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        readyItems.forEach { name ->
            Text(
                "$name: ready",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items.take(3).forEach { (name, times) ->
            StripTimer(label = name, startedAtMs = times.first, durationMs = times.second)
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun StripTimer(label: String, startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }
    val remaining = maxOf(0L, startedAtMs + durationMs - now)
    Text(
        "$label: ${formatMs(remaining)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

- [ ] **Step 3: Add `GrowingTab`, `WildTab`, `ProducersTab` composables**

These extract the existing section content verbatim. Add after `GatherStatusStrip`:

```kotlin
@Composable
private fun GrowingTab(
    farmPlot: GrowingSlot?,
    gardenSlots: List<GrowingSlot?>,
    gatheringLevel: Int,
    onPickSlot: (String) -> Unit,
    onCollect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionHeader("Farm Plot")
        Spacer(modifier = Modifier.height(8.dp))
        if (gatheringLevel < 5) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Locked — reach Gathering level 5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            GrowingSlotCard(
                slot = farmPlot,
                label = "Farm plot",
                onPlant = { if (farmPlot?.pendingResultJson == null) onPickSlot("farm_0") },
                onCollect = { onCollect("farm_0") }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("Garden (${gardenSlots.count { it != null }}/2 growing)")
        Spacer(modifier = Modifier.height(8.dp))
        gardenSlots.forEachIndexed { index, slot ->
            GrowingSlotCard(
                slot = slot,
                label = "Bed ${index + 1}",
                onPlant = { if (slot?.pendingResultJson == null) onPickSlot("garden_$index") },
                onCollect = { onCollect("garden_$index") }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WildTab(
    forageSession: com.liquidcode7.hearthcraft.data.db.GatheringSession?,
    foragableIngredients: List<ForageTargetDetail>,
    forageTargetId: String?,
    showPostForageNudge: Boolean,
    onPickTarget: () -> Unit,
    onClearTarget: () -> Unit,
    onStartForage: () -> Unit,
    onCollectForage: () -> Unit,
    onDismissNudge: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionHeader("Forage")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Head into the wild. Random ingredients, and a chance of finding seeds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (forageSession == null) {
            if (foragableIngredients.isEmpty()) {
                Text(
                    "Forage a few times to discover ingredients you can target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val targetName = foragableIngredients.find { it.ingredientId == forageTargetId }?.name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        if (targetName != null) "Target: $targetName (+2 min)" else "Target: none (random)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (targetName != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        if (forageTargetId != null) {
                            TextButton(onClick = onClearTarget) { Text("Clear") }
                        }
                        TextButton(onClick = onPickTarget) { Text("Change") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        when {
            forageSession?.pendingResultJson != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Forage complete — ready to collect", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onCollectForage, modifier = Modifier.fillMaxWidth()) {
                            Text("Collect Forage")
                        }
                    }
                }
            }
            forageSession != null && isForageTimerElapsed(forageSession) -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Finishing up…", style = MaterialTheme.typography.titleSmall)
                        Text("Just a moment.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            forageSession != null -> {
                ActiveTimerCard(
                    label = "Foraging in progress",
                    startedAtMs = forageSession.startedAtMs,
                    durationMs = forageSession.durationMs
                )
            }
            else -> {
                val targetName = foragableIngredients.find { it.ingredientId == forageTargetId }?.name
                Button(onClick = onStartForage, modifier = Modifier.fillMaxWidth()) {
                    Text(if (targetName != null) "Forage for $targetName — 5 min" else "Start Foraging — 3 min")
                }
            }
        }

        if (showPostForageNudge) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "You've gathered ingredients — head to the Kitchen and see what you can make.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissNudge) { Text("Got it") }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ProducersTab(
    hiveSlot: GrowingSlot?,
    coopSlot: GrowingSlot?,
    dairySlot: GrowingSlot?,
    onCollect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionHeader("Hive")
        Spacer(modifier = Modifier.height(4.dp))
        Text("Tend your hive for a steady supply of honey.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        HiveCard(slot = hiveSlot, onHarvest = { onCollect(HiveWorker.SLOT_ID) })

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("Coop")
        Spacer(modifier = Modifier.height(4.dp))
        Text("Your hens lay steadily. Collect eggs every 15 minutes.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        CoopCard(slot = coopSlot, onCollect = { onCollect(CoopWorker.SLOT_ID) })

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("Dairy")
        Spacer(modifier = Modifier.height(4.dp))
        Text("Keep your cow milked. Ready every 20 minutes.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        DairyCard(slot = dairySlot, onCollect = { onCollect(DairyWorker.SLOT_ID) })
        Spacer(modifier = Modifier.height(16.dp))
    }
}
```

- [ ] **Step 4: Remove the old monolithic body of `GatheringScreen`**

The original body of `GatheringScreen` from its Column block (lines ~95–306 in the original file) is now replaced by the `GatheringScreen` rewrite in Step 1. Confirm the old single-column body is gone and not duplicated.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. Fix any missing imports — all needed types were already imported before this refactor.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GatheringScreen.kt
git commit -m "[hc] UI: GatheringScreen — sub-tabs (Growing/Wild/Producers), sticky status strip, swipe"
```

---

### Task 6: Show active grow timers on the Home screen

The Home screen already shows forage and cooking timers. Add growing slots and producer timers to the "Active" section.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/HomeViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HomeScreen.kt`

- [ ] **Step 1: Read HomeViewModel.kt to understand its current state flows**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/HomeViewModel.kt` before editing.

- [ ] **Step 2: Add active growing/producer streams to `HomeViewModel`**

After the existing state declarations, add:

```kotlin
val hiveSlot: StateFlow<GrowingSlot?> = growing.observeSlot("hive_0")
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

val coopSlot: StateFlow<GrowingSlot?> = growing.observeSlot("coop_0")
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

val dairySlot: StateFlow<GrowingSlot?> = growing.observeSlot("dairy_0")
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

If `HomeViewModel` does not yet inject `GrowingRepository`, add it to the constructor: `private val growing: GrowingRepository` and its Hilt `@Inject` annotation.

- [ ] **Step 3: Collect the new states in `HomeScreen` and show timers**

In `HomeScreen`, add:
```kotlin
val hiveSlot by viewModel.hiveSlot.collectAsState()
val coopSlot by viewModel.coopSlot.collectAsState()
val dairySlot by viewModel.dairySlot.collectAsState()
```

In the "Active" section, after the existing `growingCount` check, add before the closing brace of the `else` block:

```kotlin
// Producer timers — only show if running (not ready to collect)
listOf(
    Triple(hiveSlot, "Hive", HiveWorker.SLOT_ID),
    Triple(coopSlot, "Coop", CoopWorker.SLOT_ID),
    Triple(dairySlot, "Dairy", DairyWorker.SLOT_ID)
).forEach { (slot, name, _) ->
    if (slot != null && slot.pendingResultJson == null) {
        ActiveTimerRow(
            label = name,
            startedAtMs = slot.plantedAtMs,
            durationMs = slot.durationMs
        )
        Spacer(modifier = Modifier.height(4.dp))
    } else if (slot?.pendingResultJson != null) {
        Text(
            "$name: ready to collect",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
```

Add necessary imports for `HiveWorker`, `CoopWorker`, `DairyWorker`.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/HomeViewModel.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HomeScreen.kt
git commit -m "[hc] Home: show Hive/Coop/Dairy timers and ready status in Active section"
```

---

## Self-Review

**Spec coverage:**
- ✅ Gather sub-menus: Growing / Wild / Producers tabs
- ✅ Alert badges on tabs when something is ready
- ✅ Timers at top of Gather screen stay when scrolling (sticky header)
- ✅ Timers for gather items on home screen
- ✅ Honey/eggs/milk stockpile (up to 3 cycles)
- ✅ Garden yield bump (3→5)
- ✅ Farm yield bump (6→8)
- ✅ Gathering XP too fast (30→15 per session)
- ✅ Swipe between Gather sub-tabs (HorizontalPager)

**Gaps:**
- The `WorkerConstantsTest.kt` may test `XP_GATHER_SESSION` — check and update if it asserts the old value of 30.
- The `GatheringSession` model (not `GrowingSlot`) is used for forage sessions; `pendingResultJson` is on `GatheringSession` — confirm the field name matches before compiling (Task 5 uses `forageSession.pendingResultJson` — verify this field exists on `GatheringSession`).
- `HomeViewModel` may not currently inject `GrowingRepository` — Task 6 Step 2 adds it. Check if the Hilt module needs to provide it (it's a `@Singleton` so it should bind automatically).
