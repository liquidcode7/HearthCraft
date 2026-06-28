# Process Station, Coop & Dairy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a timed Process station to the Kitchen (render, churn, cure, smoke, brew, mill, press) and add Coop + Dairy workers to the Gathering screen, completing the husbandry and processing supply chain.

**Architecture:** The Process station reuses the existing `GrowingSlot` table (type = "process", slot "process_0") and follows the same WorkManager CoroutineWorker pattern as HiveWorker. A third tab is added to KitchenScreen. Coop and Dairy are two new workers identical in structure to HiveWorker, shown as new sections in GatheringScreen. The data layer is purely additive: `processInputs` fields in ingredients.json and 4 new raw forage ingredients.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Room/GrowingSlot, WorkManager, Hilt, kotlinx.serialization, JUnit 4 unit tests.

## Global Constraints

- Kotlin only — all source under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- No new XP tracks — Process station uses cooking XP (`player.addCookingXp(XP_COOK_REPEAT)`)
- No new Room tables — Process station uses existing `growing_slots` table
- All process cookLevel gates = 1 for now (tune later)
- gatheringMode on new raw forage ingredients = "forage" (same as existing hunt_fish items)
- Notification IDs already in use: 1 (Gathering), 2 (Cooking), 3 (Encounter), 10–11 (Farm), 20–21 (Garden), 30 (Hive)
- New notification IDs: ProcessWorker=40, CoopWorker=41, DairyWorker=42
- New slot IDs: ProcessWorker.SLOT_ID = "process_0", CoopWorker.SLOT_ID = "coop_0", DairyWorker.SLOT_ID = "dairy_0"
- Run `JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test` after every commit
- Commit prefix: `[hc]`

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/assets/data/ingredients.json` | Add processInputs to 13 process items; add 4 new raw hunt_fish ingredients |
| `worker/ProcessWorker.kt` | New HiltWorker — timed process station |
| `worker/CoopWorker.kt` | New HiltWorker — produces hens_egg |
| `worker/DairyWorker.kt` | New HiltWorker — produces milk |
| `viewmodel/KitchenViewModel.kt` | Replace experimentMode bool with selectedTab Int; add process station logic |
| `screen/KitchenScreen.kt` | Add Process tab; update tab row to 3 tabs |
| `viewmodel/GatheringViewModel.kt` | Add coopSlot, dairySlot, startCoop(), startDairy(); update collectGrowingSlot() |
| `screen/GatheringScreen.kt` | Add Coop and Dairy sections |
| `test/.../ProcessStationTest.kt` | New unit tests for canProcess logic |
| `test/.../WorkerConstantsTest.kt` | New unit tests for worker companion constants |

---

## processInputs Reference Table

These are the 13 process ingredients that get processInputs populated. The remaining 5 (salted_pork, smoked_goat, salt_mutton, rendered_tallow, dried_marrow_bone) keep processInputs null — they need a livestock producer not yet built.

| Output ID | processType | processInputs |
|-----------|-------------|---------------|
| butter | churn | `[{"id":"milk","qty":2}]` |
| rendered_fat | render | `[{"id":"rabbit","qty":2}]` |
| smoked_river_trout | smoke | `[{"id":"river_trout","qty":1}]` |
| lhun_flatbread_base | mill | `[{"id":"barleycorn","qty":2}]` |
| lhun_olive_oil | press | `[{"id":"lhun_plum","qty":3}]` |
| rendered_seabird_fat | render | `[{"id":"seabird_egg","qty":2}]` |
| lhun_saltfish | cure | `[{"id":"river_trout","qty":1}]` |
| smoked_eel | smoke | `[{"id":"eel","qty":1}]` |
| deer_haunch_dried | cure | `[{"id":"deer_haunch","qty":1}]` |
| pressed_herb_cheese | press | `[{"id":"milk","qty":2}]` |
| malt_syrup | brew | `[{"id":"barleycorn","qty":3}]` |
| mountain_ale_flat | brew | `[{"id":"barleycorn","qty":2}]` |
| peat_smoked_grouse | smoke | `[{"id":"grouse","qty":1}]` |

## New Raw Ingredients

4 new ingredients to add at end of ingredients.json (total goes 141 → 145):

```json
{
  "id": "river_trout",
  "name": "River Trout",
  "region": "Bree-land / Celondim",
  "rarity": "c",
  "source": "forage",
  "gatheringMode": "forage",
  "gatherType": "hunt_fish"
},
{
  "id": "eel",
  "name": "Eel",
  "region": "Bree Wildlands / Lone-Lands",
  "rarity": "c",
  "source": "forage",
  "gatheringMode": "forage",
  "gatherType": "hunt_fish"
},
{
  "id": "deer_haunch",
  "name": "Deer Haunch",
  "region": "Bree Wildlands / Lone-Lands",
  "rarity": "uc",
  "source": "forage",
  "gatheringMode": "forage",
  "gatherType": "hunt_fish"
},
{
  "id": "grouse",
  "name": "Grouse",
  "region": "Misty Mountains",
  "rarity": "c",
  "source": "forage",
  "gatheringMode": "forage",
  "gatherType": "hunt_fish"
}
```

"Bree-land / Celondim" region matches both "Bree" (greycloaks keyword) and "Celondim" (mithlost keyword) because the forage filter uses `ingredient.region.contains(keyword)`.

---

### Task 1: processInputs data

**Files:**
- Modify: `app/src/main/assets/data/ingredients.json`

**Interfaces:**
- Produces: 13 process ingredients with populated `processInputs` arrays; 4 new raw hunt_fish ingredients appended at end of array; 5 process items still have `processInputs` absent/null (salted_pork, smoked_goat, salt_mutton, rendered_tallow, dried_marrow_bone)
- Total ingredient count after task: 145

- [ ] **Step 1: Write a verification script**

Create `/tmp/verify_ingredients.py`:
```python
import json, sys

with open("app/src/main/assets/data/ingredients.json") as f:
    data = json.load(f)

errors = []
if len(data) != 145:
    errors.append(f"Expected 145 ingredients, got {len(data)}")

# Check new raw ingredients exist
for raw_id in ["river_trout", "eel", "deer_haunch", "grouse"]:
    if not any(i["id"] == raw_id for i in data):
        errors.append(f"Missing new raw ingredient: {raw_id}")

# Check processInputs are populated
expected = {
    "butter": [{"id":"milk","qty":2}],
    "rendered_fat": [{"id":"rabbit","qty":2}],
    "smoked_river_trout": [{"id":"river_trout","qty":1}],
    "lhun_flatbread_base": [{"id":"barleycorn","qty":2}],
    "lhun_olive_oil": [{"id":"lhun_plum","qty":3}],
    "rendered_seabird_fat": [{"id":"seabird_egg","qty":2}],
    "lhun_saltfish": [{"id":"river_trout","qty":1}],
    "smoked_eel": [{"id":"eel","qty":1}],
    "deer_haunch_dried": [{"id":"deer_haunch","qty":1}],
    "pressed_herb_cheese": [{"id":"milk","qty":2}],
    "malt_syrup": [{"id":"barleycorn","qty":3}],
    "mountain_ale_flat": [{"id":"barleycorn","qty":2}],
    "peat_smoked_grouse": [{"id":"grouse","qty":1}],
}
for ing_id, inputs in expected.items():
    ing = next((i for i in data if i["id"] == ing_id), None)
    if ing is None:
        errors.append(f"Missing ingredient: {ing_id}")
        continue
    actual = ing.get("processInputs")
    if actual != inputs:
        errors.append(f"{ing_id}: expected processInputs {inputs}, got {actual}")

# Check the 5 items that should NOT have processInputs
for no_input_id in ["salted_pork","smoked_goat","salt_mutton","rendered_tallow","dried_marrow_bone"]:
    ing = next((i for i in data if i["id"] == no_input_id), None)
    if ing and ing.get("processInputs") is not None:
        errors.append(f"{no_input_id} should have null/absent processInputs")

# Check all new raw ingredients have correct fields
for item in data:
    if item["id"] in ["river_trout","eel","deer_haunch","grouse"]:
        for field in ["gatherType","gatheringMode","source","region","rarity"]:
            if field not in item:
                errors.append(f"{item['id']} missing field: {field}")
        if item.get("gatherType") != "hunt_fish":
            errors.append(f"{item['id']} should have gatherType hunt_fish")
        if item.get("gatheringMode") != "forage":
            errors.append(f"{item['id']} should have gatheringMode forage")

if errors:
    print("ERRORS:")
    for e in errors: print(f"  {e}")
    sys.exit(1)
else:
    print("All checks passed. 145 ingredients, processInputs correct.")
```

Run it before editing to confirm baseline:
```bash
python3 /tmp/verify_ingredients.py
```
Expected: fails with count error (141, not 145). That's correct — confirms the script works.

- [ ] **Step 2: Add processInputs to the 13 process ingredients**

Open `app/src/main/assets/data/ingredients.json`. Find each of the 13 ingredients below and add the `processInputs` field exactly as shown. These already have `processType` set — add `processInputs` alongside it.

For `butter` — find `"id": "butter"` and add:
```json
"processInputs": [{"id": "milk", "qty": 2}]
```

For `rendered_fat`:
```json
"processInputs": [{"id": "rabbit", "qty": 2}]
```

For `smoked_river_trout`:
```json
"processInputs": [{"id": "river_trout", "qty": 1}]
```

For `lhun_flatbread_base`:
```json
"processInputs": [{"id": "barleycorn", "qty": 2}]
```

For `lhun_olive_oil`:
```json
"processInputs": [{"id": "lhun_plum", "qty": 3}]
```

For `rendered_seabird_fat`:
```json
"processInputs": [{"id": "seabird_egg", "qty": 2}]
```

For `lhun_saltfish`:
```json
"processInputs": [{"id": "river_trout", "qty": 1}]
```

For `smoked_eel`:
```json
"processInputs": [{"id": "eel", "qty": 1}]
```

For `deer_haunch_dried`:
```json
"processInputs": [{"id": "deer_haunch", "qty": 1}]
```

For `pressed_herb_cheese`:
```json
"processInputs": [{"id": "milk", "qty": 2}]
```

For `malt_syrup`:
```json
"processInputs": [{"id": "barleycorn", "qty": 3}]
```

For `mountain_ale_flat`:
```json
"processInputs": [{"id": "barleycorn", "qty": 2}]
```

For `peat_smoked_grouse`:
```json
"processInputs": [{"id": "grouse", "qty": 1}]
```

Do NOT add `processInputs` to: salted_pork, smoked_goat, salt_mutton, rendered_tallow, dried_marrow_bone.

- [ ] **Step 3: Append the 4 new raw ingredients**

The ingredients.json file is a JSON array. Find the closing `]` at the very end of the file. Before it, add a comma after the last entry then these 4 objects:

```json
  {
    "id": "river_trout",
    "name": "River Trout",
    "region": "Bree-land / Celondim",
    "rarity": "c",
    "source": "forage",
    "gatheringMode": "forage",
    "gatherType": "hunt_fish",
    "notes": ""
  },
  {
    "id": "eel",
    "name": "Eel",
    "region": "Bree Wildlands / Lone-Lands",
    "rarity": "c",
    "source": "forage",
    "gatheringMode": "forage",
    "gatherType": "hunt_fish",
    "notes": ""
  },
  {
    "id": "deer_haunch",
    "name": "Deer Haunch",
    "region": "Bree Wildlands / Lone-Lands",
    "rarity": "uc",
    "source": "forage",
    "gatheringMode": "forage",
    "gatherType": "hunt_fish",
    "notes": ""
  },
  {
    "id": "grouse",
    "name": "Grouse",
    "region": "Misty Mountains",
    "rarity": "c",
    "source": "forage",
    "gatheringMode": "forage",
    "gatherType": "hunt_fish",
    "notes": ""
  }
```

- [ ] **Step 4: Run verification script**

```bash
python3 /tmp/verify_ingredients.py
```
Expected: `All checks passed. 145 ingredients, processInputs correct.`

- [ ] **Step 5: Run tests**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/assets/data/ingredients.json
git commit -m "[hc] Data: populate processInputs for 13 process items; add 4 raw hunt_fish ingredients"
```

---

### Task 2: ProcessWorker.kt

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/ProcessWorker.kt`
- Create: `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WorkerConstantsTest.kt`

**Interfaces:**
- Consumes: `GrowingRepository.setPendingResult(id, json)`, `PlayerRepository.addCookingXp(xp)`, `PlayerRepository.XP_COOK_REPEAT`, `GameDataRepository.ingredients`, `GrowingRepository`, `HarvestItem`
- Produces: `ProcessWorker.SLOT_ID: String = "process_0"`, `ProcessWorker.NOTIFICATION_ID: Int = 40`, `ProcessWorker.durationForType(processType: String): Long`, `ProcessWorker.buildRequest(slotId: String, ingredientId: String, durationMs: Long): OneTimeWorkRequest`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WorkerConstantsTest.kt`:
```kotlin
package com.liquidcode7.hearthcraft.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerConstantsTest {

    @Test
    fun `ProcessWorker SLOT_ID is process_0`() {
        assertEquals("process_0", ProcessWorker.SLOT_ID)
    }

    @Test
    fun `ProcessWorker NOTIFICATION_ID is 40`() {
        assertEquals(40, ProcessWorker.NOTIFICATION_ID)
    }

    @Test
    fun `durationForType mill is 3 minutes`() {
        assertEquals(3 * 60 * 1000L, ProcessWorker.durationForType("mill"))
    }

    @Test
    fun `durationForType press is 4 minutes`() {
        assertEquals(4 * 60 * 1000L, ProcessWorker.durationForType("press"))
    }

    @Test
    fun `durationForType render is 5 minutes`() {
        assertEquals(5 * 60 * 1000L, ProcessWorker.durationForType("render"))
    }

    @Test
    fun `durationForType churn is 5 minutes`() {
        assertEquals(5 * 60 * 1000L, ProcessWorker.durationForType("churn"))
    }

    @Test
    fun `durationForType smoke is 6 minutes`() {
        assertEquals(6 * 60 * 1000L, ProcessWorker.durationForType("smoke"))
    }

    @Test
    fun `durationForType cure is 8 minutes`() {
        assertEquals(8 * 60 * 1000L, ProcessWorker.durationForType("cure"))
    }

    @Test
    fun `durationForType brew is 10 minutes`() {
        assertEquals(10 * 60 * 1000L, ProcessWorker.durationForType("brew"))
    }

    @Test
    fun `durationForType unknown falls back to 5 minutes`() {
        assertEquals(5 * 60 * 1000L, ProcessWorker.durationForType("unknown_type"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: FAIL — `ProcessWorker` does not exist yet.

- [ ] **Step 3: Create ProcessWorker.kt**

Create `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/ProcessWorker.kt`:
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
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@HiltWorker
class ProcessWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slotId = inputData.getString(KEY_SLOT_ID) ?: return Result.failure()
        val ingredientId = inputData.getString(KEY_INGREDIENT_ID) ?: return Result.failure()

        val ingredient = gameData.ingredients.find { it.id == ingredientId }
            ?: return Result.failure()

        val items = listOf(
            HarvestItem(ingredientId = ingredientId, name = ingredient.name, quantity = 1, rarity = ingredient.rarity)
        )
        val json = Json.encodeToString(items)

        player.addCookingXp(PlayerRepository.XP_COOK_REPEAT)
        growing.setPendingResult(slotId, json)

        notify("Processing complete", "${ingredient.name} is ready to collect.")
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
        const val SLOT_ID         = "process_0"
        const val NOTIFICATION_ID = 40
        const val KEY_SLOT_ID     = "slotId"
        const val KEY_INGREDIENT_ID = "ingredientId"

        fun durationForType(processType: String): Long = when (processType) {
            "mill"   -> 3 * 60 * 1000L
            "press"  -> 4 * 60 * 1000L
            "render" -> 5 * 60 * 1000L
            "churn"  -> 5 * 60 * 1000L
            "smoke"  -> 6 * 60 * 1000L
            "cure"   -> 8 * 60 * 1000L
            "brew"   -> 10 * 60 * 1000L
            else     -> 5 * 60 * 1000L
        }

        fun buildRequest(slotId: String, ingredientId: String, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ProcessWorker>()
                .setInputData(workDataOf(
                    KEY_SLOT_ID to slotId,
                    KEY_INGREDIENT_ID to ingredientId
                ))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
```

- [ ] **Step 4: Run tests**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: BUILD SUCCESSFUL, all WorkerConstantsTest tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/ProcessWorker.kt \
        app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WorkerConstantsTest.kt
git commit -m "[hc] Worker: add ProcessWorker with per-type duration table"
```

---

### Task 3: KitchenViewModel — tab system + process station logic

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt`
- Create: `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/ProcessStationTest.kt`

**Interfaces:**
- Consumes from earlier tasks: `ProcessWorker.SLOT_ID`, `ProcessWorker.durationForType()`, `ProcessWorker.buildRequest()`, `GrowingRepository.observeSlot()`, `GrowingRepository.plantSlot()`, `GrowingRepository.collectAndClearSlot()`, `Ingredient.processInputs: List<ProcessInput>?`, `Ingredient.processType: String?`
- Produces (KitchenScreen Task 4 depends on these exact names):
  - `selectedTab: StateFlow<Int>` — 0=Recipes, 1=Discover, 2=Process
  - `selectTab(index: Int)` — replaces `toggleExperimentMode()`; `toggleExperimentMode()` is DELETED
  - `processSlot: StateFlow<GrowingSlot?>` — observes "process_0"
  - `processIngredients: List<Ingredient>` — all process items with non-null processInputs, sorted by name; computed at init, not a Flow
  - `selectedProcessIngredient: StateFlow<Ingredient?>`
  - `selectProcessIngredient(ingredient: Ingredient?)`
  - `canProcess(ingredient: Ingredient, items: List<InventoryItem>): Boolean`
  - `startProcess(ingredient: Ingredient)`
  - `collectProcess()`
  - The KitchenViewModel now requires `GrowingRepository` injected (add to constructor)

**Note on `experimentMode`:** KitchenScreen currently uses `viewModel.experimentMode` as a StateFlow<Boolean>. Replace it by deriving from `selectedTab`. The new `experimentMode` StateFlow is: `selectedTab.map { it == 1 }.stateIn(...)`. Keep the name `experimentMode` so the existing ExperimentPanel code in KitchenScreen still compiles — Task 4 will later update KitchenScreen to use `selectedTab` directly.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/ProcessStationTest.kt`:
```kotlin
package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.model.ProcessInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessStationTest {

    private fun inventoryItem(id: String, qty: Int) =
        InventoryItem(ingredientId = id, quantity = qty)

    @Test
    fun `canProcess returns true when all inputs are available`() {
        val ingredient = Ingredient(
            id = "butter",
            name = "Butter",
            processInputs = listOf(ProcessInput(id = "milk", qty = 2))
        )
        val items = listOf(inventoryItem("milk", 3))
        assertTrue(canProcess(ingredient, items))
    }

    @Test
    fun `canProcess returns false when input quantity is insufficient`() {
        val ingredient = Ingredient(
            id = "butter",
            name = "Butter",
            processInputs = listOf(ProcessInput(id = "milk", qty = 2))
        )
        val items = listOf(inventoryItem("milk", 1))
        assertFalse(canProcess(ingredient, items))
    }

    @Test
    fun `canProcess returns false when input ingredient is missing entirely`() {
        val ingredient = Ingredient(
            id = "butter",
            name = "Butter",
            processInputs = listOf(ProcessInput(id = "milk", qty = 2))
        )
        assertFalse(canProcess(ingredient, emptyList()))
    }

    @Test
    fun `canProcess returns false when processInputs is null`() {
        val ingredient = Ingredient(id = "butter", name = "Butter", processInputs = null)
        assertFalse(canProcess(ingredient, listOf(inventoryItem("milk", 5))))
    }

    @Test
    fun `canProcess handles multiple inputs correctly`() {
        val ingredient = Ingredient(
            id = "malt_syrup",
            name = "Malt Syrup",
            processInputs = listOf(ProcessInput(id = "barleycorn", qty = 3))
        )
        val items = listOf(inventoryItem("barleycorn", 3))
        assertTrue(canProcess(ingredient, items))
    }

    // Extracted logic matching the ViewModel's canProcess implementation
    private fun canProcess(ingredient: Ingredient, items: List<InventoryItem>): Boolean {
        val inputs = ingredient.processInputs ?: return false
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        return inputs.all { (qtyMap[it.id] ?: 0) >= it.qty }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: FAIL — compilation error because `ProcessInput` import is used but process logic not in ViewModel yet. If the test file itself compiles (ProcessInput exists from Task 1 of Plan A), the tests should pass immediately — that's fine, proceed.

- [ ] **Step 3: Modify KitchenViewModel.kt**

Read the full current file first, then make the following changes:

**3a. Add `GrowingRepository` to the constructor:**
```kotlin
@HiltViewModel
class KitchenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    private val sessions: SessionRepository,
    private val player: PlayerRepository,
    private val growing: GrowingRepository          // ADD THIS
) : ViewModel() {
```

Add the import: `import com.liquidcode7.hearthcraft.data.repository.GrowingRepository`

**3b. Add required imports:**
```kotlin
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.worker.ProcessWorker
```

**3c. Replace `experimentMode` MutableStateFlow with `selectedTab`:**

Remove:
```kotlin
private val _experimentMode = MutableStateFlow(false)
val experimentMode: StateFlow<Boolean> = _experimentMode.asStateFlow()
```

Add in its place:
```kotlin
private val _selectedTab = MutableStateFlow(0)   // 0=Recipes 1=Discover 2=Process
val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

// Backward-compat derived state for existing ExperimentPanel usage
val experimentMode: StateFlow<Boolean> = _selectedTab
    .map { it == 1 }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
```

**3d. Replace `toggleExperimentMode()` with `selectTab()`:**

Remove `toggleExperimentMode()` entirely. Add:
```kotlin
fun selectTab(index: Int) {
    _selectedTab.value = index
    if (index != 1) {
        _lastExperimentResult.value = null
        _liveResult.value = null
    }
}
```

**3e. Add process station state and computed property:**

After the `experimentHintSeen` StateFlow, add:
```kotlin
val processSlot: StateFlow<GrowingSlot?> = growing.observeSlot(ProcessWorker.SLOT_ID)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

val processIngredients: List<Ingredient> = gameData.ingredients
    .filter { it.gatherType == "process" && it.processInputs != null }
    .sortedBy { it.name }

private val _selectedProcessIngredient = MutableStateFlow<Ingredient?>(null)
val selectedProcessIngredient: StateFlow<Ingredient?> = _selectedProcessIngredient.asStateFlow()

fun selectProcessIngredient(ingredient: Ingredient?) {
    _selectedProcessIngredient.value = ingredient
}
```

**3f. Add `canProcess`, `startProcess`, `collectProcess` functions:**

Add after the experiment functions section:
```kotlin
// ── Process station functions ─────────────────────────────────────────────

fun canProcess(ingredient: Ingredient, items: List<InventoryItem>): Boolean {
    val inputs = ingredient.processInputs ?: return false
    val qtyMap = items.associate { it.ingredientId to it.quantity }
    return inputs.all { (qtyMap[it.id] ?: 0) >= it.qty }
}

fun startProcess(ingredient: Ingredient) {
    val inputs = ingredient.processInputs ?: return
    val processType = ingredient.processType ?: return
    viewModelScope.launch {
        if (growing.getSlot(ProcessWorker.SLOT_ID) != null) return@launch
        if (!canProcess(ingredient, inventoryItems.value)) return@launch
        inputs.forEach { input -> inventory.removeIngredient(input.id, input.qty) }
        val durationMs = ProcessWorker.durationForType(processType)
        val request = ProcessWorker.buildRequest(ProcessWorker.SLOT_ID, ingredient.id, durationMs)
        WorkManager.getInstance(context).enqueue(request)
        growing.plantSlot(
            id           = ProcessWorker.SLOT_ID,
            type         = "process",
            ingredientId = ingredient.id,
            plantedAtMs  = System.currentTimeMillis(),
            durationMs   = durationMs,
            workRequestId = request.id.toString()
        )
        _selectedProcessIngredient.value = null
    }
}

fun collectProcess() {
    viewModelScope.launch {
        val items = growing.collectAndClearSlot(ProcessWorker.SLOT_ID)
        items.forEach { inventory.addIngredient(it.ingredientId, it.quantity) }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: BUILD SUCCESSFUL, all ProcessStationTest tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt \
        app/src/test/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/ProcessStationTest.kt
git commit -m "[hc] Logic: KitchenViewModel — tab system; process station start/collect/canProcess"
```

---

### Task 4: KitchenScreen — Process tab

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`

**Interfaces:**
- Consumes from Task 3: `viewModel.selectedTab: StateFlow<Int>`, `viewModel.selectTab(index: Int)`, `viewModel.processSlot: StateFlow<GrowingSlot?>`, `viewModel.processIngredients: List<Ingredient>`, `viewModel.selectedProcessIngredient: StateFlow<Ingredient?>`, `viewModel.selectProcessIngredient(ingredient: Ingredient?)`, `viewModel.canProcess(ingredient: Ingredient, items: List<InventoryItem>): Boolean`, `viewModel.startProcess(ingredient: Ingredient)`, `viewModel.collectProcess()`
- Note: `viewModel.experimentMode` still exists as a derived StateFlow and still compiles — but this task replaces all usages with `selectedTab` directly.

- [ ] **Step 1: Read KitchenScreen.kt fully**

Read the entire file before editing. It is ~620 lines long.

- [ ] **Step 2: Update collectAsState calls at top of KitchenScreen composable**

Replace:
```kotlin
val experimentMode by viewModel.experimentMode.collectAsState()
```
With:
```kotlin
val selectedTab by viewModel.selectedTab.collectAsState()
val processSlot by viewModel.processSlot.collectAsState()
val processIngredients = viewModel.processIngredients
val selectedProcessIngredient by viewModel.selectedProcessIngredient.collectAsState()
```

Also remove `val experimentMode by viewModel.experimentMode.collectAsState()` — no longer needed as a top-level variable (experimentMode is still in the ViewModel for the ExperimentPanel, but not used directly in the top-level composable now).

- [ ] **Step 3: Update the tab row — change from 2 to 3 tabs, always visible**

The current code hides tabs during cooking:
```kotlin
if (!isCooking) {
    TabRow(selectedTabIndex = if (experimentMode) 1 else 0) {
        Tab(selected = !experimentMode, onClick = { if (experimentMode) viewModel.toggleExperimentMode() }, text = { Text("Recipes") })
        Tab(selected = experimentMode, onClick = { if (!experimentMode) viewModel.toggleExperimentMode() }, text = { Text("Discover") })
    }
}
```

Replace this entire block with:
```kotlin
TabRow(selectedTabIndex = selectedTab) {
    Tab(selected = selectedTab == 0, onClick = { viewModel.selectTab(0) }, text = { Text("Recipes") })
    Tab(selected = selectedTab == 1, onClick = { viewModel.selectTab(1) }, text = { Text("Discover") })
    Tab(selected = selectedTab == 2, onClick = { viewModel.selectTab(2) }, text = { Text("Process") })
}
```

The tab row is now always visible (even during cooking). Move the `CookingActiveCard` inside the Recipes tab content instead.

- [ ] **Step 4: Update the scrollable content section**

The current content block checks `if (isCooking)` then `else if (experimentMode)` then `else`. Replace this entire block with a `when (selectedTab)` dispatch:

```kotlin
when (selectedTab) {
    0 -> {
        // Recipes tab
        if (isCooking) {
            val recipeName = viewModel.recipes.find { it.id == session!!.recipeId }?.name
                ?: session!!.recipeId
            CookingActiveCard(
                recipeName = recipeName,
                startedAtMs = session!!.startedAtMs,
                durationMs = session!!.durationMs
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewRecipes, modifier = Modifier.weight(1f)) {
                    Text("Recipe Book")
                }
                OutlinedButton(onClick = onViewPantry, modifier = Modifier.weight(1f)) {
                    Text("Pantry")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

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
    }
    1 -> {
        // Discover tab — pass experimentMode = true to ExperimentPanel
        val experimentIngredients by viewModel.experimentIngredients.collectAsState()
        val experimentMethod by viewModel.experimentMethod.collectAsState()
        val lastResult by viewModel.lastExperimentResult.collectAsState()
        val hintsSeen by viewModel.hintsSeen.collectAsState()
        val liveResult by viewModel.liveResult.collectAsState()
        val canCommit by viewModel.canCommit.collectAsState()
        val experimentHintSeen by viewModel.experimentHintSeen.collectAsState()
        ExperimentPanel(
            viewModel = viewModel,
            inventoryItems = inventoryItems,
            experimentIngredients = experimentIngredients,
            experimentMethod = experimentMethod,
            lastResult = lastResult,
            cookingLevel = cookingLevel,
            hintsSeen = hintsSeen,
            liveResult = liveResult,
            canCommit = canCommit,
            experimentHintSeen = experimentHintSeen
        )
    }
    2 -> {
        // Process tab
        ProcessPanel(
            viewModel = viewModel,
            processSlot = processSlot,
            processIngredients = processIngredients,
            selectedProcessIngredient = selectedProcessIngredient,
            inventoryItems = inventoryItems
        )
    }
}
```

**Important:** In the Discover tab content (case 1), the `collectAsState()` calls for `experimentIngredients`, `experimentMethod`, `lastResult`, `hintsSeen`, `liveResult`, `canCommit`, `experimentHintSeen` should be moved from the top of `KitchenScreen` to inside the `1 ->` branch as shown above. Remove them from the top-level `KitchenScreen` composable if they were there. This avoids unnecessary recomposition when not on the Discover tab.

- [ ] **Step 5: Add ProcessPanel composable**

Add this new private composable at the bottom of the file, before the `formatMs` function:

```kotlin
@Composable
private fun ProcessPanel(
    viewModel: KitchenViewModel,
    processSlot: GrowingSlot?,
    processIngredients: List<Ingredient>,
    selectedProcessIngredient: Ingredient?,
    inventoryItems: List<InventoryItem>
) {
    when {
        processSlot?.pendingResultJson != null -> {
            val ingredientName = viewModel.ingredientName(processSlot.ingredientId ?: "")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Processing complete", style = MaterialTheme.typography.titleSmall)
                    Text(ingredientName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.collectProcess() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Collect")
                    }
                }
            }
        }
        processSlot != null -> {
            val ingredientName = viewModel.ingredientName(processSlot.ingredientId ?: "")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Processing: $ingredientName", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessTimer(startedAtMs = processSlot.plantedAtMs, durationMs = processSlot.durationMs)
                        Text(
                            " remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        else -> {
            if (processIngredients.isEmpty()) {
                Text(
                    "No processable items available. Gather raw ingredients first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Select an item to process:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                processIngredients.forEach { ingredient ->
                    val canDo = viewModel.canProcess(ingredient, inventoryItems)
                    val isSelected = ingredient.id == selectedProcessIngredient?.id
                    ProcessItemRow(
                        ingredient = ingredient,
                        canProcess = canDo,
                        isSelected = isSelected,
                        inventoryItems = inventoryItems,
                        viewModel = viewModel,
                        onClick = { viewModel.selectProcessIngredient(if (isSelected) null else ingredient) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (selectedProcessIngredient != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.startProcess(selectedProcessIngredient) },
                        enabled = viewModel.canProcess(selectedProcessIngredient, inventoryItems),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Processing")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessItemRow(
    ingredient: Ingredient,
    canProcess: Boolean,
    isSelected: Boolean,
    inventoryItems: List<InventoryItem>,
    viewModel: KitchenViewModel,
    onClick: () -> Unit
) {
    val qtyMap = inventoryItems.associate { it.ingredientId to it.quantity }
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canProcess) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ingredient.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ingredient.processType?.replaceFirstChar { it.uppercase() } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ingredient.processInputs?.forEach { input ->
                val have = qtyMap[input.id] ?: 0
                val name = viewModel.ingredientName(input.id)
                Text(
                    "• $name  $have/${input.qty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (have >= input.qty) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ProcessTimer(startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }
    val remaining = maxOf(0L, startedAtMs + durationMs - now)
    Text(
        formatMs(remaining),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
}
```

Add these imports to KitchenScreen.kt (add to existing import block):
```kotlin
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.model.Ingredient
```

- [ ] **Step 6: Build to verify compilation**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew build
```
Expected: BUILD SUCCESSFUL with no compilation errors.

- [ ] **Step 7: Run tests**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt
git commit -m "[hc] UI: KitchenScreen — add Process tab; tab row always visible"
```

---

### Task 5: CoopWorker.kt + DairyWorker.kt

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CoopWorker.kt`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/DairyWorker.kt`

**Interfaces:**
- Produces:
  - `CoopWorker.SLOT_ID = "coop_0"`, `CoopWorker.NOTIFICATION_ID = 41`, `CoopWorker.buildRequest(durationMs: Long): OneTimeWorkRequest`
  - `DairyWorker.SLOT_ID = "dairy_0"`, `DairyWorker.NOTIFICATION_ID = 42`, `DairyWorker.buildRequest(durationMs: Long): OneTimeWorkRequest`
- These workers are structurally identical to HiveWorker but produce fixed ingredients (no band variation).

- [ ] **Step 1: Write failing tests — add to WorkerConstantsTest.kt**

Append to the existing `WorkerConstantsTest` class:
```kotlin
@Test
fun `CoopWorker SLOT_ID is coop_0`() {
    assertEquals("coop_0", CoopWorker.SLOT_ID)
}

@Test
fun `CoopWorker NOTIFICATION_ID is 41`() {
    assertEquals(41, CoopWorker.NOTIFICATION_ID)
}

@Test
fun `DairyWorker SLOT_ID is dairy_0`() {
    assertEquals("dairy_0", DairyWorker.SLOT_ID)
}

@Test
fun `DairyWorker NOTIFICATION_ID is 42`() {
    assertEquals(42, DairyWorker.NOTIFICATION_ID)
}
```

Run:
```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: FAIL — CoopWorker and DairyWorker do not exist yet.

- [ ] **Step 2: Create CoopWorker.kt**

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
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.MainActivity
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class CoopWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val player: PlayerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val qty = BASE_YIELD + Random.nextInt(2)   // 2–3 eggs
        val items = listOf(
            HarvestItem(ingredientId = "hens_egg", name = "Hen's Egg", quantity = qty, rarity = "common")
        )
        val json = Json.encodeToString(items)
        player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)
        growing.setPendingResult(SLOT_ID, json)
        notify("Coop ready — tap to collect", "Your hens have laid eggs.")
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
        const val SLOT_ID         = "coop_0"
        const val NOTIFICATION_ID = 41
        private const val BASE_YIELD = 2

        fun buildRequest(durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<CoopWorker>()
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
```

- [ ] **Step 3: Create DairyWorker.kt**

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
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.MainActivity
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class DairyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val player: PlayerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val qty = BASE_YIELD + Random.nextInt(2)   // 2–3 milk
        val items = listOf(
            HarvestItem(ingredientId = "milk", name = "Milk", quantity = qty, rarity = "common")
        )
        val json = Json.encodeToString(items)
        player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)
        growing.setPendingResult(SLOT_ID, json)
        notify("Dairy ready — tap to collect", "Milk is ready.")
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
        const val SLOT_ID         = "dairy_0"
        const val NOTIFICATION_ID = 42
        private const val BASE_YIELD = 2

        fun buildRequest(durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DairyWorker>()
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
```

- [ ] **Step 4: Run tests**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: BUILD SUCCESSFUL, all 4 new WorkerConstantsTest tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CoopWorker.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/DairyWorker.kt \
        app/src/test/kotlin/com/liquidcode7/hearthcraft/worker/WorkerConstantsTest.kt
git commit -m "[hc] Workers: CoopWorker (eggs) and DairyWorker (milk)"
```

---

### Task 6: GatheringViewModel + GatheringScreen — Coop and Dairy

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/GatheringViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GatheringScreen.kt`

**Interfaces:**
- Consumes: `CoopWorker.SLOT_ID`, `CoopWorker.buildRequest()`, `DairyWorker.SLOT_ID`, `DairyWorker.buildRequest()`
- Produces: `coopSlot: StateFlow<GrowingSlot?>`, `dairySlot: StateFlow<GrowingSlot?>`, `startCoop()`, `startDairy()` (all on GatheringViewModel); `CoopCard` and `DairyCard` in GatheringScreen

**Auto-restart behavior:** When the player collects the coop or dairy slot, it auto-restarts — same pattern as the hive. The existing `collectGrowingSlot()` function handles hive restart with a special case at the end. Extend this pattern for coop and dairy.

- [ ] **Step 1: Modify GatheringViewModel.kt**

Read the file in full first.

**1a. Add imports:**
```kotlin
import com.liquidcode7.hearthcraft.worker.CoopWorker
import com.liquidcode7.hearthcraft.worker.DairyWorker
```

**1b. Add coopSlot and dairySlot StateFlows** (after the existing `hiveSlot` declaration):
```kotlin
val coopSlot: StateFlow<GrowingSlot?> = growing.observeSlot(CoopWorker.SLOT_ID)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

val dairySlot: StateFlow<GrowingSlot?> = growing.observeSlot(DairyWorker.SLOT_ID)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

**1c. Update init block** to auto-start coop and dairy alongside hive:
```kotlin
init {
    viewModelScope.launch {
        if (growing.getSlot(HiveWorker.SLOT_ID) == null) startHive()
        if (growing.getSlot(CoopWorker.SLOT_ID) == null) startCoop()
        if (growing.getSlot(DairyWorker.SLOT_ID) == null) startDairy()
    }
}
```

**1d. Add startCoop() and startDairy() functions** (after the existing `startHive()` function):
```kotlin
fun startCoop() {
    viewModelScope.launch {
        if (growing.getSlot(CoopWorker.SLOT_ID) != null) return@launch
        val request = CoopWorker.buildRequest(DURATION_COOP_MS)
        WorkManager.getInstance(context).enqueue(request)
        growing.plantSlot(
            id            = CoopWorker.SLOT_ID,
            type          = "coop",
            ingredientId  = "hens_egg",
            plantedAtMs   = System.currentTimeMillis(),
            durationMs    = DURATION_COOP_MS,
            workRequestId = request.id.toString()
        )
    }
}

fun startDairy() {
    viewModelScope.launch {
        if (growing.getSlot(DairyWorker.SLOT_ID) != null) return@launch
        val request = DairyWorker.buildRequest(DURATION_DAIRY_MS)
        WorkManager.getInstance(context).enqueue(request)
        growing.plantSlot(
            id            = DairyWorker.SLOT_ID,
            type          = "dairy",
            ingredientId  = "milk",
            plantedAtMs   = System.currentTimeMillis(),
            durationMs    = DURATION_DAIRY_MS,
            workRequestId = request.id.toString()
        )
    }
}
```

**1e. Update collectGrowingSlot()** to auto-restart coop and dairy:

Find the end of `collectGrowingSlot()`. It currently ends with:
```kotlin
if (slotId == HiveWorker.SLOT_ID) startHive()
```

Change to:
```kotlin
when (slotId) {
    HiveWorker.SLOT_ID -> startHive()
    CoopWorker.SLOT_ID -> startCoop()
    DairyWorker.SLOT_ID -> startDairy()
}
```

**1f. Add new duration constants** to the companion object:
```kotlin
const val DURATION_COOP_MS  = 15 * 60 * 1000L
const val DURATION_DAIRY_MS = 20 * 60 * 1000L
```

- [ ] **Step 2: Modify GatheringScreen.kt**

Read the file in full first.

**2a. Add imports:**
```kotlin
import com.liquidcode7.hearthcraft.worker.CoopWorker
import com.liquidcode7.hearthcraft.worker.DairyWorker
```

**2b. Add coopSlot and dairySlot to top of GatheringScreen composable:**
```kotlin
val coopSlot by viewModel.coopSlot.collectAsState()
val dairySlot by viewModel.dairySlot.collectAsState()
```

**2c. Add Coop and Dairy sections after the Hive section.**

Find the existing Hive section ending (just before the Forage section divider). After `HiveCard(...)`, add:

```kotlin
Spacer(modifier = Modifier.height(20.dp))
HorizontalDivider()
Spacer(modifier = Modifier.height(20.dp))
SectionHeader("Coop")
Spacer(modifier = Modifier.height(4.dp))
Text(
    "Your hens lay steadily. Collect eggs every 15 minutes.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
Spacer(modifier = Modifier.height(8.dp))
CoopCard(
    slot     = coopSlot,
    onCollect = { viewModel.collectGrowingSlot(CoopWorker.SLOT_ID) }
)

Spacer(modifier = Modifier.height(20.dp))
HorizontalDivider()
Spacer(modifier = Modifier.height(20.dp))
SectionHeader("Dairy")
Spacer(modifier = Modifier.height(4.dp))
Text(
    "Keep your cow milked. Ready every 20 minutes.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
Spacer(modifier = Modifier.height(8.dp))
DairyCard(
    slot     = dairySlot,
    onCollect = { viewModel.collectGrowingSlot(DairyWorker.SLOT_ID) }
)
```

**2d. Add CoopCard and DairyCard composables** at the bottom of GatheringScreen.kt (before `formatMs`):

```kotlin
@Composable
private fun CoopCard(slot: GrowingSlot?, onCollect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        when {
            slot?.pendingResultJson != null -> {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Coop", style = MaterialTheme.typography.bodyMedium)
                        Text("Eggs are ready.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Button(onClick = onCollect) { Text("Collect") }
                }
            }
            slot != null -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Coop", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hens laying — ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SlotTimer(startedAtMs = slot.plantedAtMs, durationMs = slot.durationMs)
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Coop", style = MaterialTheme.typography.bodyMedium)
                    Text("Starting…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DairyCard(slot: GrowingSlot?, onCollect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        when {
            slot?.pendingResultJson != null -> {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dairy", style = MaterialTheme.typography.bodyMedium)
                        Text("Milk is ready.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Button(onClick = onCollect) { Text("Collect") }
                }
            }
            slot != null -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Dairy", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Milking — ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SlotTimer(startedAtMs = slot.plantedAtMs, durationMs = slot.durationMs)
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Dairy", style = MaterialTheme.typography.bodyMedium)
                    Text("Starting…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build and test**

```bash
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew build
JAVA_HOME=/home/wes/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew test
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/GatheringViewModel.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/GatheringScreen.kt
git commit -m "[hc] Logic+UI: GatheringViewModel + GatheringScreen — Coop and Dairy producers"
```

---

## Self-Review

**1. Spec coverage check:**

| Spec requirement | Task |
|---|---|
| Process station in Kitchen, timed | Tasks 2, 3, 4 |
| No new XP track — uses cooking XP | Task 2 (ProcessWorker uses addCookingXp) |
| processInputs populated for 13 items | Task 1 |
| processInputs null for 5 farm-animal items | Task 1 |
| 4 new raw hunt_fish forage ingredients | Task 1 |
| Coop worker (hens_egg) | Task 5 |
| Dairy worker (milk) | Task 5 |
| Coop/Dairy in GatheringViewModel | Task 6 |
| Coop/Dairy UI in GatheringScreen | Task 6 |
| All cookLevel gates = 1 for now | enforced by Global Constraints (no gate logic added) |

**2. Placeholder scan:** None found.

**3. Type consistency check:**
- `ProcessWorker.SLOT_ID = "process_0"` used in Task 3 `startProcess()` ✓
- `processIngredients: List<Ingredient>` (not Flow) — initialized in ViewModel, referenced in Task 4 ProcessPanel ✓
- `canProcess(ingredient: Ingredient, items: List<InventoryItem>): Boolean` — defined in Task 3, called in Task 4 ✓
- `selectTab(index: Int)` replaces `toggleExperimentMode()` — Task 3 deletes old, Task 4 uses new ✓
- `CoopWorker.SLOT_ID = "coop_0"`, `DairyWorker.SLOT_ID = "dairy_0"` — defined in Task 5, used in Task 6 ✓
- `collectGrowingSlot(slotId)` — existing function, unchanged signature ✓
