# Kitchen Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Pantry button disappearing when cooking is active; add swipe-to-switch between Kitchen tabs; implement a second cooking slot (always available, no level gate for now — the design says "start with two").

**Architecture:** The Pantry and Recipe Book buttons move outside the `if (isCooking)` branch so they're always visible. The `TabRow` is paired with `HorizontalPager` for swipe support. A second cooking slot requires a new `slot` column on `CookingSession`, a DB migration, a `startCooking(slot)` overload, and UI for two concurrent sessions.

**Tech Stack:** Jetpack Compose, `HorizontalPager` (in Compose BOM), WorkManager, Room (migration).

## Global Constraints

- Minimum SDK API 26. No Google Play Services.
- All Kotlin source under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`.
- No version bumps to `versionName`/`versionCode`.
- `./gradlew build` must pass after every commit.
- Commit messages prefixed `[hc]`.

---

### Task 1: Fix Pantry button disappearing when cooking

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`

The issue: `OutlinedButton(onClick = onViewPantry)` is inside the `else` branch of `if (isCooking)`, so it disappears while cooking is in progress.

- [ ] **Step 1: Move Recipe Book / Pantry buttons above the cooking guard**

In `KitchenScreen.kt`, the Recipes tab content (selectedTab == 0) currently looks like:

```kotlin
if (isCooking) {
    CookingActiveCard(...)
} else {
    Row { OutlinedButton("Recipe Book"); OutlinedButton("Pantry") }
    Spacer(...)
    if (selectedRecipe != null) { RecipeDetailPanel(...) ... Button("Start Cooking") }
    else { Text("Select a recipe below...") }
    // recipe list...
}
```

Change it to:

```kotlin
// Always-visible navigation buttons
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = onViewRecipes, modifier = Modifier.weight(1f)) { Text("Recipe Book") }
    OutlinedButton(onClick = onViewPantry, modifier = Modifier.weight(1f)) { Text("Pantry") }
}
Spacer(modifier = Modifier.height(12.dp))

if (isCooking) {
    val recipeName = viewModel.recipes.find { it.id == session!!.recipeId }?.name
        ?: session!!.recipeId
    CookingActiveCard(
        recipeName = recipeName,
        startedAtMs = session!!.startedAtMs,
        durationMs = session!!.durationMs
    )
} else {
    if (selectedRecipe != null) {
        RecipeDetailPanel(
            recipe = selectedRecipe!!,
            inventoryItems = inventoryItems,
            cookingLevel = cookingLevel,
            viewModel = viewModel
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { viewModel.startCooking() },
            enabled = viewModel.canCook(selectedRecipe!!, inventoryItems),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Cooking")
        }
    } else {
        Text(
            "Select a recipe below to see details.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Select a Recipe", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))

    if (tieredRecipes.isEmpty()) {
        Text(
            "No recipes discovered yet. Head to the Discover tab to find them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        tieredRecipes.forEach { tier ->
            val isUnlocked = cookingLevel >= tier.minLevel
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rangeLabel = if (tier.minLevel <= 1) "Lv 1" else "Lv ${tier.minLevel}+"
                Text(
                    "${tier.label}  ·  $rangeLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!isUnlocked) {
                    Text(
                        "Reach Lv ${tier.minLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            tier.recipes.forEach { recipe ->
                val canCook = isUnlocked && viewModel.canCook(recipe, inventoryItems)
                RecipeRow(
                    recipe = recipe,
                    canCook = canCook,
                    isSelected = recipe.id == selectedRecipe?.id,
                    isLocked = !isUnlocked,
                    onClick = { if (isUnlocked) viewModel.selectRecipe(recipe) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt
git commit -m "[hc] Fix: Pantry and Recipe Book buttons always visible in Kitchen"
```

---

### Task 2: Add swipe-between-tabs to Kitchen

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`

The Kitchen already has a `TabRow` driven by `viewModel.selectedTab`. Pair it with `HorizontalPager` so swiping left/right also switches tabs.

- [ ] **Step 1: Wrap Kitchen scrollable content in HorizontalPager**

In `KitchenScreen`, the current pattern is:

```kotlin
Column(modifier = Modifier.fillMaxSize().verticalScroll(...).padding(...)) {
    Spacer(...)
    when (selectedTab) {
        0 -> { /* recipes */ }
        1 -> { /* discover */ }
        2 -> { /* process */ }
    }
}
```

Replace the outer scrollable Column + `when` with a `HorizontalPager`, keeping each page's own scrollable Column:

```kotlin
val pagerState = rememberPagerState(pageCount = { 3 })
LaunchedEffect(selectedTab) { if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab) }
LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage != selectedTab) viewModel.selectTab(pagerState.currentPage) }

HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        when (page) {
            0 -> { /* existing Recipes tab content */ }
            1 -> { /* existing Discover tab content */ }
            2 -> { /* existing Process tab content */ }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
```

Add imports:
```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
```

Add `@OptIn(ExperimentalFoundationApi::class)` annotation to `KitchenScreen`.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt
git commit -m "[hc] UI: Kitchen tabs support horizontal swipe via HorizontalPager"
```

---

### Task 3: DB schema — add `slot` column to `CookingSession`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/CookingSession.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/CookingSessionDao.kt`

**Interfaces:**
- Produces: `CookingSession.slot: Int` (0 or 1, default 0); a Room migration from version N to N+1; DAO queries for slot-specific lookup.

- [ ] **Step 1: Read `CookingSession.kt`, `HearthCraftDatabase.kt`, and `CookingSessionDao.kt` to get current schema version and DAO methods**

Read all three files before editing.

- [ ] **Step 2: Add `slot` column to `CookingSession`**

In `CookingSession.kt`, add a `slot: Int = 0` field. The entity should look like:

```kotlin
@Entity(tableName = "cooking_sessions")
data class CookingSession(
    @PrimaryKey val id: Int = 0,   // keep existing PK — may vary, check the actual file
    val recipeId: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val workRequestId: String,
    val slot: Int = 0              // 0 = first slot, 1 = second slot
)
```

Verify the existing `@PrimaryKey` field name and type by reading the file first.

- [ ] **Step 3: Add migration in `HearthCraftDatabase.kt`**

Increment `version` by 1 (e.g., if current is 7, set to 8). Add the migration object:

```kotlin
val MIGRATION_N_N1 = object : Migration(N, N+1) {   // replace N with actual version
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE cooking_sessions ADD COLUMN slot INTEGER NOT NULL DEFAULT 0")
    }
}
```

In the `Room.databaseBuilder` call, add `.addMigrations(MIGRATION_N_N1)`.

- [ ] **Step 4: Add slot-specific DAO queries**

In `CookingSessionDao.kt`, add:

```kotlin
@Query("SELECT * FROM cooking_sessions WHERE slot = :slot LIMIT 1")
fun observeSlot(slot: Int): Flow<CookingSession?>

@Query("SELECT * FROM cooking_sessions WHERE slot = :slot LIMIT 1")
suspend fun getSlot(slot: Int): CookingSession?
```

Keep the existing `observe()` / `get()` queries unchanged (they return all sessions or the first one — check the actual file before keeping or renaming).

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/CookingSession.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/CookingSessionDao.kt
git commit -m "[hc] DB: add slot column to cooking_sessions, migration N→N+1"
```

---

### Task 4: `SessionRepository` — multi-slot cooking support

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/SessionRepository.kt`

- [ ] **Step 1: Read `SessionRepository.kt` to understand current cooking session management**

Read the file before editing.

- [ ] **Step 2: Add slot-aware methods**

Add/update these methods (keeping all existing methods intact):

```kotlin
fun observeCookingSlot(slot: Int): Flow<CookingSession?> = cookingDao.observeSlot(slot)

suspend fun activeCookingSlot(slot: Int): CookingSession? = cookingDao.getSlot(slot)

suspend fun startCookingInSlot(session: CookingSession): Unit = cookingDao.upsert(session)

suspend fun collectCookingSlot(slot: Int): CookingSession? {
    val session = cookingDao.getSlot(slot) ?: return null
    cookingDao.delete(session)
    return session
}
```

(If `cookingDao` has a different name in the file, use whatever name is already there.)

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/SessionRepository.kt
git commit -m "[hc] Repo: SessionRepository — slot-aware cooking session methods"
```

---

### Task 5: `CookingWorker` — pass slot index

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt`

- [ ] **Step 1: Read `CookingWorker.kt`**

Read the file before editing.

- [ ] **Step 2: Add slot key and pass it through**

In the companion object, add:
```kotlin
const val KEY_SLOT = "slot"
```

In `buildRequest`, add `slot` to the input data:
```kotlin
fun buildRequest(recipeId: String, durationMs: Long, slot: Int): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<CookingWorker>()
        .setInputData(workDataOf(
            KEY_RECIPE_ID to recipeId,
            KEY_DURATION to durationMs,
            KEY_SLOT to slot
        ))
        .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
        .build()
```

In `doWork()`, read the slot:
```kotlin
val slot = inputData.getInt(KEY_SLOT, 0)
```

When the worker saves its result or deletes the session, use `sessions.collectCookingSlot(slot)` or however it currently completes the session — read the file first to see exactly how it clears the session, then replace the slot-0-specific call with a slot-aware one.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt
git commit -m "[hc] Worker: CookingWorker accepts slot index"
```

---

### Task 6: `KitchenViewModel` — two cooking slots

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt`

- [ ] **Step 1: Read `KitchenViewModel.kt` lines 1–150 to understand existing session / startCooking logic**

- [ ] **Step 2: Add slot-1 state flow**

The existing `session` flow observes slot 0. Add slot 1:

```kotlin
val session0: StateFlow<CookingSession?> = sessions.observeCookingSlot(0)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

val session1: StateFlow<CookingSession?> = sessions.observeCookingSlot(1)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

Keep the existing `session` alias pointing at `session0` for any callers that haven't been updated yet:
```kotlin
val session: StateFlow<CookingSession?> get() = session0
```

- [ ] **Step 3: Update `startCooking()` to pick the free slot**

Replace the existing `startCooking()`:

```kotlin
fun startCooking(preferredSlot: Int = -1) {
    val recipe = _selectedRecipe.value ?: return
    viewModelScope.launch {
        val freeSlot = when {
            preferredSlot >= 0 -> preferredSlot
            sessions.activeCookingSlot(0) == null -> 0
            sessions.activeCookingSlot(1) == null -> 1
            else -> return@launch  // both slots busy
        }
        if (sessions.activeCookingSlot(freeSlot) != null) return@launch
        if (!canCook(recipe, inventoryItems.value)) return@launch

        recipe.ingredients.forEach { ing ->
            inventory.removeIngredient(ing.id, ing.qty)
        }

        val cookingLevel = playerState.value?.cookingLevel ?: 1
        val isFirstTime = recipe.id !in (discoveredIds.value)
        player.addCookingXp(
            if (isFirstTime) PlayerRepository.XP_COOK_FIRST else PlayerRepository.XP_COOK_REPEAT,
            recipe.id
        )
        if (isFirstTime) player.discoverRecipe(recipe.id)

        val request = CookingWorker.buildRequest(recipe.id, recipe.durationMs(cookingLevel), freeSlot)
        WorkManager.getInstance(context).enqueue(request)
        sessions.startCookingInSlot(
            CookingSession(
                id = freeSlot,          // use slot as PK or adjust if PK is auto
                recipeId = recipe.id,
                startedAtMs = System.currentTimeMillis(),
                durationMs = recipe.durationMs(cookingLevel),
                workRequestId = request.id.toString(),
                slot = freeSlot
            )
        )
    }
}
```

> **Note:** Check the actual `CookingSession` primary key approach in the DB — if it's auto-generated (AUTOINCREMENT), the slot PK approach won't work. In that case, use a unique constraint on `slot` and `upsertOnConflict`, or just insert without specifying the PK. Read the actual `CookingSession.kt` and DAO to confirm before writing this code.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt
git commit -m "[hc] ViewModel: KitchenViewModel — two cooking slots"
```

---

### Task 7: Kitchen UI — show two cooking slots

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`

- [ ] **Step 1: Collect both slot states in `KitchenScreen`**

Add:
```kotlin
val session0 by viewModel.session0.collectAsState()
val session1 by viewModel.session1.collectAsState()
```

Remove the old `val session by viewModel.session.collectAsState()` if it existed.

- [ ] **Step 2: Update Recipes tab to show two slot cards**

In the Recipes tab (page/tab 0), replace the single `if (isCooking)` block with two slot cards side by side (or stacked if cooking):

```kotlin
// Show both cooking slots
val bothBusy = session0 != null && session1 != null
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CookingSlotCard(
        slot = 0,
        session = session0,
        viewModel = viewModel,
        modifier = Modifier.weight(1f)
    )
    CookingSlotCard(
        slot = 1,
        session = session1,
        viewModel = viewModel,
        modifier = Modifier.weight(1f)
    )
}
Spacer(modifier = Modifier.height(12.dp))

// Recipe selection only shown when at least one slot is free
if (!bothBusy) {
    // (existing recipe selection UI — selectedRecipe detail panel + recipe list)
}
```

- [ ] **Step 3: Add `CookingSlotCard` composable**

```kotlin
@Composable
private fun CookingSlotCard(
    slot: Int,
    session: CookingSession?,
    viewModel: KitchenViewModel,
    modifier: Modifier = Modifier
) {
    val slotLabel = if (slot == 0) "Slot 1" else "Slot 2"
    if (session != null) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(session.startedAtMs) {
            while (true) { now = System.currentTimeMillis(); delay(1000L) }
        }
        val remaining = maxOf(0L, session.startedAtMs + session.durationMs - now)
        val recipeName = viewModel.recipes.find { it.id == session.recipeId }?.name ?: session.recipeId
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(slotLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(recipeName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(formatMs(remaining), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(slotLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Open", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

Add import `import com.liquidcode7.hearthcraft.data.db.CookingSession`.

- [ ] **Step 4: Update "Start Cooking" button to pass the slot**

The "Start Cooking" button should target the free slot. Find which slot is free and pass it:

```kotlin
val freeSlot = if (session0 == null) 0 else 1
Button(
    onClick = { viewModel.startCooking(freeSlot) },
    enabled = viewModel.canCook(selectedRecipe!!, inventoryItems) && (session0 == null || session1 == null),
    modifier = Modifier.fillMaxWidth()
) {
    Text("Start Cooking")
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run tests**

Run: `./gradlew :app:test 2>&1 | tail -20`
Expected: tests pass (KitchenExperimentTest and ProcessStationTest do not test cooking slots)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt
git commit -m "[hc] UI: Kitchen shows two cooking slots side by side"
```

---

## Self-Review

**Spec coverage:**
- ✅ Pantry button always visible (Task 1)
- ✅ Swipe between Kitchen tabs (Task 2)
- ✅ Two cooking slots starting from the beginning (Tasks 3–7)

**Gaps / cautions:**
- Task 3 Step 2: the actual DB version number must be read from `HearthCraftDatabase.kt` before writing the migration. Do not guess the version number.
- Task 5 Step 2: `CookingWorker`'s `doWork()` must clear the correct slot. Read the file carefully.
- Task 6 Step 3: if `CookingSession` uses an auto-increment PK, the `id = freeSlot` approach won't work — use a `slot` unique constraint instead and upsert. The DB read in Task 3 Step 1 will clarify this.
- `isCooking` references in the existing code must be updated to `session0 != null || session1 != null` wherever it's used.
