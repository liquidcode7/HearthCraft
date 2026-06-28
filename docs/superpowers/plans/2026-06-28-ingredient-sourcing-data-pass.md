# Ingredient Sourcing Data Pass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `gatherType` taxonomy to every ingredient, correct honey sourcing (hive-only, region-locked), fix three broken starter recipes, and add missing Draw/Milk ingredients — all without touching UI or introducing new systems.

**Architecture:** Purely additive to the Kotlin data model and JSON data files. `gatheringMode` is preserved on all processed and hunt_fish items (so existing gathering logic is unaffected), while `gatherType` is added as a new informational field that Plan B's Process station will use. Honey types are the one exception: their `gatheringMode` changes from `"forage"` to `"husbandry"` to remove them from the forage pool, which is safe because the hive already produces them. HiveWorker and GatheringViewModel get a small update to derive honey type from the player's chosen band rather than hard-coding `forest_honey`.

**Tech Stack:** Kotlin, kotlinx.serialization, WorkManager, Hilt, Room (no Room migration — `Ingredient` is not a Room entity)

## Global Constraints

- No new skill tracks — only COOKING and GATHERING XP tracks exist. Process type is informational data only; it has no XP track.
- Do not change `gatheringMode` on any processed items (rendered_fat, butter, salted_pork, etc.) — they must still obtainable via farm/forage until Plan B ships the Process station.
- All new nullable fields on `Ingredient` must have defaults so existing JSON without those fields still deserializes without error.
- No Room migration required — `Ingredient` is loaded from JSON assets, not stored in Room.
- No versionName/versionCode bump.
- Build must pass (`./gradlew build`) before any commit.
- Commit prefix: `[hc]`

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Ingredient.kt` | Add `ProcessInput` data class + three new nullable fields |
| `app/src/main/assets/data/ingredients.json` | Add `gatherType` to every entry; change `gatheringMode` on five honey types; add `processType` to processed items; add 7 new Draw ingredients + Milk |
| `app/src/main/assets/data/recipes.json` | Fix `ferny's_treacle` (swap ingredient), fix `brookcress_bannock` and `sloe_bitters` (swap honey) |
| `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HiveWorker.kt` | Add `honeyForBand()` companion function; derive honey from player band in `doWork()` |
| `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/GatheringViewModel.kt` | Use `HiveWorker.honeyForBand()` in `startHive()` |

No other files change.

---

## gatherType Reference

These are the valid values and what they mean:

| gatherType | What it covers | gatheringMode in Plan A |
|---|---|---|
| `cultivate` | Grown plants — grains, roots, veg, cultivated herbs, orchard fruit | `"farm"` (unchanged) |
| `forage` | Genuinely wild — wild herbs, mushrooms, berries, nuts, seaweed | `"forage"` (unchanged) |
| `hunt_fish` | Wild-caught animals & fish — game, fowl, fish, wild eggs | `"forage"` (unchanged — Plan B adds hunt mode) |
| `draw` | Liquids at source — well-water, spring-water, brine, snowmelt | `"draw"` for new; `"forage"` for existing (unchanged) |
| `husbandry` | Kept-animal products — honey, eggs, milk | `"husbandry"` for honey (changed); `"farm"` kept for eggs/royal_jelly until coop/dairy ships |
| `process` | Made, not gathered — rendered fat, butter, cured meat, smoked fish, malt | keep existing `gatheringMode` (unchanged) |
| `trade` | Obtained through trade or missions | `"trade"` / `"mission"` (unchanged) |
| `craft` | High-level craft-branch outputs | `"craft"` (unchanged) |
| `mission` | Found only in mission drops | `"mission"` (unchanged) |

---

## Task 1: Add fields to `Ingredient.kt`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Ingredient.kt`

**Interfaces:**
- Produces: `Ingredient.gatherType: String?`, `Ingredient.processType: String?`, `Ingredient.processInputs: List<ProcessInput>?`
- `ProcessInput(id: String, qty: Int)` — used in `processInputs` for Plan B's process station

- [ ] **Step 1: Read the current file before editing**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Ingredient.kt` to confirm current content.

- [ ] **Step 2: Add `ProcessInput` and the three new fields**

Replace the file content with:

```kotlin
package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProcessInput(val id: String, val qty: Int)

@Serializable
data class Ingredient(
    val id: String,
    val name: String,
    val region: String = "",
    val rarity: String = "c",
    val source: String = "forage",
    val gatheringMode: String = "forage",
    val gatherType: String? = null,
    val processType: String? = null,
    val processInputs: List<ProcessInput>? = null,
    val primaryStat: String? = null,
    val secondaryStat: String? = null,
    val hazardTendency: String? = null,
    val notes: String = ""
)
```

- [ ] **Step 3: Build to confirm no compilation errors**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (or only pre-existing warnings, no new errors)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Ingredient.kt
git commit -m "[hc] Data model: add gatherType, processType, processInputs fields to Ingredient"
```

---

## Task 2: Add `gatherType` to every existing ingredient in `ingredients.json`

**Files:**
- Modify: `app/src/main/assets/data/ingredients.json`

**What changes per ingredient:**

For every entry, add a `"gatherType"` field after `"gatheringMode"`. For processed items, also add `"processType"`. For honey, also change `"gatheringMode"`.

**Complete `gatherType` assignment for every ingredient (alphabetical by gatherType group):**

### CULTIVATE — add `"gatherType": "cultivate"` only

`ancient_rye`, `barleycorn`, `bitter_bulb`, `black_barley`, `cave_onion`, `common_thyme`, `cram_base_grain`, `deep_carrot`, `deepcap`, `duskberry`, `fennel_bulb`, `hearthgrain`, `goodmans_taters`, `iron_turnip`, `ironbite_wort`, `ironroot`, `leek`, `lhun_plum`, `lhun_sage`, `mountain_malt`, `oat_sheaf`, `onion`, `pale_cap`, `pale_waybread_grain`, `parsnip`, `pipeweed_leaf`, `rye_stalk`, `saltmarsh_root`, `silver_leek`, `silvern_mint`, `starflower`, `starfruit_havens`, `stone_root`, `sunpetal_herb`, `turnip`

### FORAGE — add `"gatherType": "forage"` only

`anduin_reed`, `athelas`, `bilberry`, `blushcap`, `brookcress`, `caradhras_snowmelt`*, `chetwood_acorn`, `chetwood_browncap`, `cobweb_grass`, `coldwort`, `crabapple`, `dawnshroom`, `deep_sage`, `elderflower`, `fell_mushroom`, `feverfew`, `flint_truffle`, `forest_ghost`, `frost_berry`, `ghostgill`, `goldmoss_cap`, `hall_bracket`, `hallspring_water`*, `heartflame_pepper`, `heather_honey`†, `hillwort`, `icecap_lichen`, `ironbark_resin`, `ironfoot_shroom`, `ironweed`, `marshwort`, `meadowsweet`, `mirkwood_blackcap`, `moonpetal`, `moorgrass_seed`, `mountain_sorrel`, `mountain_thyme`, `nettleleaf`, `old_forest_mushroom`, `pale_fen_cap`, `peak_thyme`, `road_herb`, `rockmoss`, `rosehip`, `seagrass_herb`, `seabloom_hip`, `shadowbane_herb`, `shorewrack_berry`, `shorewrack_greens`, `sloe`, `sorrel`, `springwater_lhun`*, `stormcap`, `stonewort`, `thornberry`, `tidal_cress`, `tidal_kelp`, `wanderer_fig`, `white_nettle`, `willowherb`, `wolf_moss`, `yarrow`

> *These are water sources — they get `"gatherType": "draw"`, not `"forage"`, even though their `gatheringMode` stays `"forage"`. See draw group below.

> †`heather_honey` also changes `gatheringMode` — see husbandry group.

**Correction — water sources are draw:**

`caradhras_snowmelt`, `hallspring_water`, `springwater_lhun` — use `"gatherType": "draw"` (their `gatheringMode` stays `"forage"` for now)

### HUNT_FISH — add `"gatherType": "hunt_fish"` only (gatheringMode stays "forage")

`cave_trout`, `mountain_boar`, `mountain_crow_egg`, `rabbit`, `seabird_egg`

### HUSBANDRY — add `"gatherType": "husbandry"` AND change `gatheringMode`

| Ingredient | Old gatheringMode | New gatheringMode |
|---|---|---|
| `field_honey` | `"forage"` | `"husbandry"` |
| `forest_honey` | `"forage"` | `"husbandry"` |
| `heather_honey` | `"forage"` | `"husbandry"` |
| `stone_honey` | `"forage"` | `"husbandry"` |
| `white_nectar` | `"farm"` | `"husbandry"` |

These five also keep their existing `source` field unchanged.

**Keep gatheringMode unchanged for these husbandry items** (coop/dairy ships in Plan B):

`hens_egg` — add `"gatherType": "husbandry"` but keep `"gatheringMode": "farm"`
`royal_jelly` — add `"gatherType": "husbandry"` but keep `"gatheringMode": "farm"`

### PROCESS — add `"gatherType": "process"` and `"processType"` (gatheringMode unchanged)

| Ingredient | processType | Keep gatheringMode |
|---|---|---|
| `butter` | `"churn"` | `"farm"` |
| `deer_haunch_dried` | `"cure"` | `"forage"` |
| `dried_marrow_bone` | `"cure"` | `"farm"` |
| `lhun_flatbread_base` | `"mill"` | `"farm"` |
| `lhun_olive_oil` | `"press"` | `"farm"` |
| `lhun_saltfish` | `"cure"` | `"forage"` |
| `malt_syrup` | `"brew"` | `"farm"` |
| `mountain_ale_flat` | `"brew"` | `"farm"` |
| `peat_smoked_grouse` | `"smoke"` | `"forage"` |
| `pressed_herb_cheese` | `"press"` | `"farm"` |
| `rendered_fat` | `"render"` | `"farm"` |
| `rendered_seabird_fat` | `"render"` | `"forage"` |
| `rendered_tallow` | `"render"` | `"farm"` |
| `salt_mutton` | `"cure"` | `"farm"` |
| `salted_pork` | `"cure"` | `"farm"` |
| `smoked_eel` | `"smoke"` | `"forage"` |
| `smoked_goat` | `"smoke"` | `"farm"` |
| `smoked_river_trout` | `"smoke"` | `"forage"` |

### SPECIAL

| Ingredient | gatherType | gatheringMode |
|---|---|---|
| `athelas_concentrate` | `"craft"` | `"craft"` (unchanged) |
| `beorns_honey` | `"trade"` | `"trade"` (unchanged) |
| `black_mushroom` | `"mission"` | `"mission"` (unchanged) |
| `miruvor_base_raw` | `"craft"` | `"farm"` (unchanged) |
| `old_vintage_fragment` | `"mission"` | `"mission"` (unchanged) |

- [ ] **Step 1: Read the current ingredients.json before editing**

Read `app/src/main/assets/data/ingredients.json` to load the full current content. Do not edit from memory.

- [ ] **Step 2: Add `gatherType` (and `processType` where applicable) to every ingredient entry**

Working ingredient by ingredient, insert `"gatherType": "<value>"` after the `"gatheringMode"` line. For process items also insert `"processType": "<value>"` after `gatherType`. For the five honey types listed above, also change the `gatheringMode` value.

The result for a sample cultivate item should look like:
```json
{
  "id": "hearthgrain",
  "name": "Hearthgrain",
  "region": "Bree-land & The Shire",
  "rarity": "c",
  "source": "farm",
  "gatheringMode": "farm",
  "gatherType": "cultivate",
  "primaryStat": "vit",
  "secondaryStat": "mig",
  "notes": "Short-stalked kitchen grain. The foundation."
}
```

A sample process item should look like:
```json
{
  "id": "rendered_fat",
  "name": "Rendered Fat",
  "region": "Bree-land & The Shire",
  "rarity": "c",
  "source": "farm",
  "gatheringMode": "farm",
  "gatherType": "process",
  "processType": "render",
  "primaryStat": "mig",
  "secondaryStat": "vit",
  "notes": "Lard or dripping. Carries heat and sustains."
}
```

A honey type (changes gatheringMode too) should look like:
```json
{
  "id": "forest_honey",
  "name": "Forest Honey",
  "region": "Bree-land & The Shire",
  "rarity": "c",
  "source": "forage",
  "gatheringMode": "husbandry",
  "gatherType": "husbandry",
  "primaryStat": "vit",
  "secondaryStat": "wil",
  "notes": "Dark honey from wild hives in the forest. Also produced by tended hives."
}
```

- [ ] **Step 3: Verify the JSON is valid**

Run: `python3 -c "import json; data=json.load(open('app/src/main/assets/data/ingredients.json')); print(f'{len(data)} ingredients loaded')" `

Expected output: a count matching the original count (no items dropped). If the count changes, investigate — you may have accidentally removed or duplicated an entry.

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/data/ingredients.json
git commit -m "[hc] Data: add gatherType taxonomy to all ingredients; region-lock honey to husbandry"
```

---

## Task 3: Add new Draw ingredients and Milk to `ingredients.json`

**Files:**
- Modify: `app/src/main/assets/data/ingredients.json`

**New ingredients to append (before the closing `]`):**

### Greycloaks — Bree-land Draw sources

```json
{
  "id": "bree_well_water",
  "name": "Bree Well-water",
  "region": "Bree-land & The Shire",
  "rarity": "c",
  "source": "draw",
  "gatheringMode": "draw",
  "gatherType": "draw",
  "notes": "Common well-water from Bree. Clean enough. Base for soups and draughts."
},
{
  "id": "brandywine_water",
  "name": "Brandywine Water",
  "region": "Bree-land & The Shire",
  "rarity": "c",
  "source": "draw",
  "gatheringMode": "draw",
  "gatherType": "draw",
  "notes": "River water drawn fresh. Mild, faintly earthy."
},
{
  "id": "chetwood_spring",
  "name": "Chetwood Spring Water",
  "region": "Bree-land & The Shire",
  "rarity": "u",
  "source": "draw",
  "gatheringMode": "draw",
  "gatherType": "draw",
  "notes": "Cold spring in the Chetwood. Cleaner than the well."
}
```

### Mithlost — Lhûn Draw sources

```json
{
  "id": "lhun_brine",
  "name": "Lhûn Brine",
  "region": "Celondim / Ered Luin",
  "rarity": "c",
  "source": "draw",
  "gatheringMode": "draw",
  "gatherType": "draw",
  "notes": "Seawater from the Gulf of Lhûn. Salt and mineral. Used in curing and preserving."
}
```

### Undermarch — Thorin's Halls Draw sources

```json
{
  "id": "deep_cistern_water",
  "name": "Deep Cistern Water",
  "region": "Thorin's Halls",
  "rarity": "c",
  "source": "draw",
  "gatheringMode": "draw",
  "gatherType": "draw",
  "notes": "Stone-filtered water from the deep hall cisterns. Minerally. Reliable."
},
{
  "id": "pass_snowmelt",
  "name": "Pass Snowmelt",
  "region": "Thorin's Halls",
  "rarity": "u",
  "source": "draw",
  "gatheringMode": "draw",
  "gatherType": "draw",
  "hazardTendency": "warmth",
  "notes": "Snowmelt from the Blue Mountain passes. Cold-counter draughts."
}
```

### Milk — Husbandry (all regions)

```json
{
  "id": "milk",
  "name": "Milk",
  "region": "All",
  "rarity": "c",
  "source": "husbandry",
  "gatheringMode": "husbandry",
  "gatherType": "husbandry",
  "primaryStat": "vit",
  "notes": "From the dairy. Whole and fresh. Base for butter, cheese, and cream preparations."
}
```

- [ ] **Step 1: Read the current end of ingredients.json to find the closing bracket**

Read the last 20 lines of `app/src/main/assets/data/ingredients.json` to locate the final `}` before `]`.

- [ ] **Step 2: Append the 7 new ingredients before the closing `]`**

Insert the 7 JSON objects above (with correct commas between entries) before the final `]`.

- [ ] **Step 3: Validate JSON**

Run: `python3 -c "import json; data=json.load(open('app/src/main/assets/data/ingredients.json')); print(f'{len(data)} ingredients loaded')"`

Expected: previous count + 7 (if previous count was N, now N+7)

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/data/ingredients.json
git commit -m "[hc] Data: add Draw water sources for all 3 bands and Milk ingredient"
```

---

## Task 4: Fix three broken recipes in `recipes.json`

**Files:**
- Modify: `app/src/main/assets/data/recipes.json`

**Three fixes required:**

### Fix 1 — `ferny's_treacle` (cookLevel 1, Greycloaks)

`rendered_fat` moves to process in this redesign. Replace it with `hens_egg`, which is a reliable husbandry/farm source. The recipe stays ghastly.

Change the ingredient entry:
```json
{ "id": "rendered_fat", "qty": 2 }
```
to:
```json
{ "id": "hens_egg", "qty": 2 }
```

### Fix 2 — `brookcress_bannock` (cookLevel 1, Greycloaks)

`field_honey` is now husbandry-only and no hive currently maps to it for Greycloaks (Greycloaks hive produces `forest_honey`). Replace with `forest_honey`.

Change:
```json
{ "id": "field_honey", "qty": 1 }
```
to:
```json
{ "id": "forest_honey", "qty": 1 }
```

### Fix 3 — `sloe_bitters` (cookLevel 5, Greycloaks)

Same issue — `field_honey` → `forest_honey`.

Change:
```json
{ "id": "field_honey", "qty": 1 }
```
to:
```json
{ "id": "forest_honey", "qty": 1 }
```

- [ ] **Step 1: Read recipes.json before editing**

Read `app/src/main/assets/data/recipes.json` to locate all three recipes. Confirm the current ingredient entries before making changes.

- [ ] **Step 2: Apply Fix 1 — ferny's_treacle**

Find the `ferny's_treacle` entry. Change `rendered_fat` qty 2 → `hens_egg` qty 2. Do not change anything else.

- [ ] **Step 3: Apply Fix 2 — brookcress_bannock**

Find the `brookcress_bannock` entry. Change `field_honey` qty 1 → `forest_honey` qty 1.

- [ ] **Step 4: Apply Fix 3 — sloe_bitters**

Find the `sloe_bitters` entry. Change `field_honey` qty 1 → `forest_honey` qty 1.

- [ ] **Step 5: Validate JSON**

Run: `python3 -c "import json; data=json.load(open('app/src/main/assets/data/recipes.json')); print(f'{len(data)} recipes loaded')"`

Expected: same recipe count as before (no recipes added or removed).

- [ ] **Step 6: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/assets/data/recipes.json
git commit -m "[hc] Data: fix starter recipes — swap rendered_fat and field_honey for reliable sources"
```

---

## Task 5: Region-lock `HiveWorker.kt`

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HiveWorker.kt`

**Band → honey mapping (3 bands currently in bands.json):**

| bandId | honeyId | honeyName |
|---|---|---|
| `"greycloaks"` | `"forest_honey"` | `"Forest Honey"` |
| `"mithlost"` | `"white_nectar"` | `"White Nectar"` |
| `"undermarch"` | `"stone_honey"` | `"Stone Honey"` |
| any other | `"forest_honey"` | `"Forest Honey"` (safe default) |

**Interfaces:**
- Produces: `HiveWorker.honeyForBand(bandId: String): Pair<String, String>` — returns `(honeyId, honeyName)` so `GatheringViewModel` can use the same mapping

- [ ] **Step 1: Read the current HiveWorker.kt**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HiveWorker.kt` to confirm current content.

- [ ] **Step 2: Add `honeyForBand` to companion object and update `doWork()`**

Replace `HiveWorker.kt` with:

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
class HiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val player: PlayerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bandId = player.get()?.chosenBandId ?: "greycloaks"
        val (honeyId, honeyName) = honeyForBand(bandId)
        val honeyQty = BASE_YIELD + Random.nextInt(3)   // 2–4 honey

        val items = listOf(
            HarvestItem(ingredientId = honeyId, name = honeyName, quantity = honeyQty, rarity = "common")
        )
        val json = Json.encodeToString(items)

        player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)
        growing.setPendingResult(SLOT_ID, json)

        notify("Hive ready — tap to collect", "$honeyName is ready to harvest.")
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
        const val SLOT_ID         = "hive_0"
        const val NOTIFICATION_ID = 30
        private const val BASE_YIELD = 2

        fun honeyForBand(bandId: String): Pair<String, String> = when (bandId) {
            "mithlost"   -> "white_nectar" to "White Nectar"
            "undermarch" -> "stone_honey"  to "Stone Honey"
            else         -> "forest_honey" to "Forest Honey"  // greycloaks default
        }

        fun buildRequest(durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HiveWorker>()
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/HiveWorker.kt
git commit -m "[hc] Logic: region-lock HiveWorker — honey type derived from player's chosen band"
```

---

## Task 6: Update `GatheringViewModel.startHive()` for region-awareness

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/GatheringViewModel.kt`

`startHive()` currently hardcodes `ingredientId = "forest_honey"` in the GrowingSlot record. It needs to use the player's chosen band to derive the correct honey ID.

- [ ] **Step 1: Read the current GatheringViewModel.kt**

Read `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/GatheringViewModel.kt` to confirm the current `startHive()` implementation.

- [ ] **Step 2: Update `startHive()`**

Find `startHive()` (currently lines 141–155). Replace it with:

```kotlin
fun startHive() {
    viewModelScope.launch {
        if (growing.getSlot(HiveWorker.SLOT_ID) != null) return@launch
        val state = player.get() ?: return@launch
        val (honeyId, _) = HiveWorker.honeyForBand(state.chosenBandId)
        val request = HiveWorker.buildRequest(DURATION_HIVE_MS)
        WorkManager.getInstance(context).enqueue(request)
        growing.plantSlot(
            id            = HiveWorker.SLOT_ID,
            type          = "hive",
            ingredientId  = honeyId,
            plantedAtMs   = System.currentTimeMillis(),
            durationMs    = DURATION_HIVE_MS,
            workRequestId = request.id.toString()
        )
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/GatheringViewModel.kt
git commit -m "[hc] Logic: GatheringViewModel startHive() uses band-appropriate honey type"
```

---

## Self-Review Checklist

Before declaring this plan complete, verify these against the design spec:

**Spec coverage:**
- [x] `gatherType` added to all ingredients — Task 2
- [x] Honey `gatheringMode` changed to `"husbandry"` (5 types) — Task 2
- [x] `processType` added to all processed items — Task 2
- [x] `gatheringMode` kept unchanged for processed/hunt_fish items — Task 2 (by design)
- [x] New Draw ingredients for Greycloaks (3), Mithlost (1), Undermarch (2) — Task 3
- [x] Milk added — Task 3
- [x] `ferny's_treacle` fixed — Task 4
- [x] `field_honey` in recipes swapped to `forest_honey` — Task 4
- [x] HiveWorker region-locked — Task 5
- [x] GatheringViewModel updated — Task 6
- [ ] `processInputs` populated — **deliberately deferred to Plan B** (raw ingredient definitions needed first)
- [ ] Coop/Dairy workers — **deferred to Plan B**
- [ ] Draw station UI — **deferred to Plan B**
- [ ] Process station UI — **deferred to Plan B**

**Known gap that does NOT need fixing in Plan A:** The foragable ingredient filter in `GatheringViewModel` checks `ingredient.gatheringMode == GatheringWorker.MODE_FORAGE`. After Task 2, honey types have `gatheringMode: "husbandry"` and will correctly drop out of that filter. Hunt_fish items keep `gatheringMode: "forage"` and remain in the forage pool — correct for now. No code change needed to `GatheringViewModel.foragableIngredients`.

---

## Plan B Preview

Plan B (separate document, written after Plan A is reviewed) covers:
1. Process station DB entity + DAO + WorkManager worker
2. CoopWorker (eggs) and DairyWorker (milk)
3. Raw ingredient definitions needed for `processInputs`
4. Process station UI in KitchenScreen (timed station, cookLevel-gated, no XP track)
5. Husbandry section in GatheringScreen for Coop and Dairy
6. Changing `gatheringMode` away from `"farm"`/`"forage"` for process items (once station is live)
7. Draw station UI
