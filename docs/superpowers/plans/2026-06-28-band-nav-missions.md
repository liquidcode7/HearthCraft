# Band / Nav / Missions Cleanup Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the "Band" and "Market" tabs from the bottom nav; make the Band accessible from the Home screen; fix the missions difficulty meter (shows "Routine" when band is unfed); improve the Missions pre-deploy screen to show band status and food assignment; fix the Greycloaks Will food (Contemplative Tea uses out-of-region herbs).

**Architecture:** `MainScreen.kt` removes Band and Market from the `tabs` list; navigation to `"band"` and `"market"` routes is now only triggered from `HomeScreen` or `MissionsScreen`. `MissionsScreen` adds a band fed-status section before the Send button. The difficulty meter bug is a display logic error in `EncounterCard` — it uses static data instead of live fed state.

**Tech Stack:** Jetpack Compose, Room, kotlinx.serialization.

## Global Constraints

- Minimum SDK API 26. No Google Play Services.
- All Kotlin source under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`.
- No version bumps to `versionName`/`versionCode`.
- `./gradlew build` must pass after every commit.
- Commit messages prefixed `[hc]`.

---

### Task 1: Remove Market from the app

The Market is not useful yet. Remove it from the bottom nav and hide the screen route entirely.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt`

- [ ] **Step 1: Remove Market from `tabs` list**

In `MainScreen.kt`, find:
```kotlin
private val tabs = listOf(
    Tab("home",     "Home",     Icons.Filled.Home),
    Tab("gather",   "Gather",   Icons.Filled.Forest),
    Tab("kitchen",  "Kitchen",  Icons.Filled.LocalDining),
    Tab("band",     "Band",     Icons.Filled.Groups),
    Tab("missions", "Missions", Icons.Filled.Flag),
    Tab("market",   "Market",   Icons.Filled.Storefront),
)
```

Change to:
```kotlin
private val tabs = listOf(
    Tab("home",     "Home",     Icons.Filled.Home),
    Tab("gather",   "Gather",   Icons.Filled.Forest),
    Tab("kitchen",  "Kitchen",  Icons.Filled.LocalDining),
    Tab("missions", "Missions", Icons.Filled.Flag),
)
```

The `composable("market")` route stays in the NavHost (so any existing save data referencing it doesn't crash) — just remove the Market import from the icon set if it causes an unused-import warning.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt
git commit -m "[hc] Nav: remove Market from bottom nav bar"
```

---

### Task 2: Remove Band tab from bottom nav; add Band card to Home screen

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/HomeScreen.kt`

- [ ] **Step 1: Remove Band from `tabs` in `MainScreen.kt`**

After Task 1, the tabs list is:
```kotlin
private val tabs = listOf(
    Tab("home",     "Home",     Icons.Filled.Home),
    Tab("gather",   "Gather",   Icons.Filled.Forest),
    Tab("kitchen",  "Kitchen",  Icons.Filled.LocalDining),
    Tab("missions", "Missions", Icons.Filled.Flag),
)
```

The `composable("band")` route stays in the NavHost — `HomeScreen` and `MissionsScreen` navigate to it.

- [ ] **Step 2: Add a "Band" nav card to `HomeScreen`**

`HomeScreen` already has a 2×2 grid of `NavCard` composables (Gather, Kitchen, Band, Missions). The Band and Missions cards are in the second row. The Band card is already there — it just needs to be the ONLY way to reach the Band screen now that the tab is gone. No code change needed on `HomeScreen` for this — the existing Band `NavCard` already navigates to `"band"` via `onNavigate`.

Verify: in `HomeScreen.kt`, the existing row has:
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    NavCard(label = "Band", icon = Icons.Filled.Groups, ..., onClick = { onNavigate("band") }, ...)
    NavCard(label = "Missions", icon = Icons.Filled.Flag, ..., onClick = { onNavigate("missions") }, ...)
}
```

If the Band NavCard is already present and wired, this task is complete after Step 1.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MainScreen.kt
git commit -m "[hc] Nav: remove Band tab; Band accessible from Home screen cards"
```

---

### Task 3: Fix difficulty meter — "Routine" shown when band is unfed

**Problem:** `EncounterCard` shows difficulty from the encounter's static `difficulty` field ("easy" → "Routine"). The player interprets this as overall mission difficulty INCLUDING provisioning status — but it's not. The band going hungry should either modify what's shown, or a warning should make it crystal clear.

**Fix:** When no food is assigned to any member, add a prominent warning next to the difficulty label: "No provisions — actual difficulty higher."

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt`

- [ ] **Step 1: Expose `anyFoodAssigned` from `BandViewModel`**

In `BandViewModel.kt`, add:

```kotlin
val anyFoodAssigned: StateFlow<Boolean> = _memberFood
    .map { it.values.any { food -> food != null } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
```

(Find the actual name of the `MutableStateFlow` holding memberFood in the ViewModel — look for `_memberFood` or similar. Read the ViewModel before editing.)

- [ ] **Step 2: Collect `anyFoodAssigned` in `MissionsScreen`**

In `MissionsScreen.kt`, add:
```kotlin
val anyFoodAssigned by bandViewModel.anyFoodAssigned.collectAsState()
```

- [ ] **Step 3: Update `EncounterCard` to accept and show unprovision warning**

Change `EncounterCard` signature to include `provisioned: Boolean`:

```kotlin
@Composable
private fun EncounterCard(
    encounter: EncounterDetail,
    isSelected: Boolean,
    provisioned: Boolean,
    onClick: () -> Unit
)
```

Inside `EncounterCard`, after the difficulty label row, add:

```kotlin
if (!provisioned) {
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        "Band unprovision — actual difficulty higher",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error
    )
}
```

- [ ] **Step 4: Update call site in `MissionsScreen`**

Find the `EncounterCard(encounter = enc, isSelected = ...)` call and pass `provisioned = anyFoodAssigned`:

```kotlin
unlockedEncounters.forEach { enc ->
    EncounterCard(
        encounter = enc,
        isSelected = enc.encounterId == selectedEncounter?.encounterId,
        provisioned = anyFoodAssigned,
        onClick = { bandViewModel.selectEncounter(enc) }
    )
    Spacer(modifier = Modifier.height(8.dp))
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt \
        app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt
git commit -m "[hc] Fix: difficulty meter shows unprovision warning when band goes hungry"
```

---

### Task 4: Missions screen — pre-deploy band and food status panel

**Problem:** The player can't see WHO is in the band and whether they've been provisioned before hitting "Send". The food and encounter sections are separate, with no band member overview.

**Fix:** Add a collapsible "Band Ready?" section below the Encounters list and above the Send button. It shows each alive member, their assigned food (or "unfed"), and a summary line.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt`

- [ ] **Step 1: Expose `members` and `memberFood` from `BandViewModel` in `MissionsScreen`**

`MissionsScreen` already injects `BandViewModel` and collects `memberFood`. It does NOT currently collect `members`. Add:

```kotlin
val members by bandViewModel.members.collectAsState()
```

(Verify `bandViewModel.members` is a `StateFlow<List<BandMemberWithState>>` — it is, from reading `BandViewModel.kt`.)

- [ ] **Step 2: Add `BandReadyPanel` composable to `MissionsScreen.kt`**

```kotlin
@Composable
private fun BandReadyPanel(
    members: List<BandMemberWithState>,
    memberFood: Map<String, PreparedFoodDetail?>
) {
    val aliveMembersCount = members.count { it.isAlive }
    val fedCount = members.count { it.isAlive && memberFood[it.memberId] != null }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (fedCount == aliveMembersCount && aliveMembersCount > 0)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Band: $fedCount/$aliveMembersCount provisioned",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            members.filter { it.isAlive }.forEach { member ->
                val food = memberFood[member.memberId]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            member.role.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        food?.name ?: "Unfed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (food != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Insert `BandReadyPanel` into `MissionsScreen` between encounters and the Send button**

In the `MissionsScreen` body, after the encounter selection section and before `selectedEncounter != null` send-button block, add:

```kotlin
if (unlockedEncounters.isNotEmpty() && members.isNotEmpty()) {
    Spacer(modifier = Modifier.height(16.dp))
    Text("Band Status", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(6.dp))
    BandReadyPanel(members = members, memberFood = memberFood)
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionsScreen.kt
git commit -m "[hc] UI: Missions — band ready panel shows members and fed status before Send"
```

---

### Task 5: Fix Greycloaks Will food — remove Contemplative Tea (wrong-region herbs)

**Problem:** Contemplative Tea is tagged `"band": "greycloaks"` but uses `sunpetal_herb` and `moonpetal` from Celondim / Ered Luin — the Mithlost's region, not Bree-land.

**Fix:** Change Contemplative Tea's `band` to `"mithlost"` (where those herbs actually grow). The Greycloaks already have two local Will foods: Brookcress Bannock and Honey Oat Cake.

**Files:**
- Modify: `app/src/main/assets/data/recipes.json`

- [ ] **Step 1: Find and update Contemplative Tea's band field**

In `recipes.json`, find the entry with `"id": "contemplative_tea"`. Change:
```json
"band": "greycloaks"
```
to:
```json
"band": "mithlost"
```

- [ ] **Step 2: Verify the Mithlost doesn't already have a duplicate Will recipe with those exact ingredients**

Run a quick check — the Mithlost already have `Starflower Wafer` (wil, sunpetal_herb + moonpetal + pale_waybread_grain). Contemplative Tea uses sunpetal_herb + moonpetal. They overlap but are distinct recipes. Having two Will options for Mithlost is fine — players discover recipes, so both will be available to discover.

- [ ] **Step 3: Audit Mithlost and Undermarch band recipes for ingredient region consistency**

Run this Python check from the project root:

```bash
python3 -c "
import json
with open('app/src/main/assets/data/ingredients.json') as f:
    ings = {i['id']: i for i in json.load(f)}
with open('app/src/main/assets/data/recipes.json') as f:
    recipes = json.load(f)

band_regions = {
    'mithlost':   ['Ered Luin', 'Celondim', 'Lhun'],
    'undermarch': ['Blue Mountains', 'Thorin', 'mountain', 'Mountain', 'cave', 'deep', 'Deep'],
    'greycloaks': ['Bree', 'Cardolan', 'Wildwood', 'Shire', 'Eriador'],
}

for r in recipes:
    band = r.get('band')
    if band not in band_regions:
        continue
    allowed = band_regions[band]
    for ing_ref in r.get('ingredients', []):
        ing = ings.get(ing_ref['id'])
        if ing is None:
            continue
        region = ing.get('region', '')
        if region and not any(a in region for a in allowed):
            print(f'MISMATCH: {r[\"name\"]} [{band}] uses {ing[\"id\"]} from {region}')
"
```

Fix any additional mismatches found by either moving the recipe to the correct band or substituting a region-appropriate ingredient.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (JSON files aren't compiled — this confirms the app still starts)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/data/recipes.json
git commit -m "[hc] Data: Contemplative Tea moved to Mithlost — Greycloaks use local Will foods"
```

---

### Task 6: ⚠️ BLOCKED — Inspiration Titles

**Status:** Needs clarification from Wes before implementing.

The audit says: "their inspiration titles are wrong." The current `Band` model has a `flavorNote` field that is not displayed anywhere in the app (it's parsed but never shown in `BandScreen`, `BandSelectionScreen`, or `HomeScreen`).

**Possible interpretations:**
1. The `flavorNote` text for Mithlost and Greycloaks is nearly identical copy-paste — both say "They do not give you a title. What they give you is their trust." This might be the "wrong" content Wes noticed.
2. The role ability names in `BandScreen.roleAbility()` ("Shield of the Company", "Keeper's Grace", etc.) might not match what Wes intended.
3. There may be a planned UI element (a title/motto shown per band in the Band screen) that hasn't been implemented yet.

**Before implementing:** Ask Wes: "What are the inspiration titles — where should they appear in the UI, and what should they say for each band?"

---

## Self-Review

**Spec coverage:**
- ✅ Market removed from bottom nav (Task 1)
- ✅ Band tab removed from bottom nav (Task 2)
- ✅ Band accessible from Home screen via NavCard (Task 2 — already implemented)
- ✅ Difficulty meter unfed warning (Task 3)
- ✅ Missions pre-deploy band + fed status (Task 4)
- ✅ Contemplative Tea moved to Mithlost (Task 5)
- ✅ Greycloaks local Will food verified (Brookcress Bannock, Honey Oat Cake)
- ⛔ Inspiration titles — BLOCKED, needs clarification

**Gaps / cautions:**
- Task 3 Step 1: `_memberFood` field name in `BandViewModel` must be confirmed by reading the file — the `anyFoodAssigned` flow needs to reference the correct private backing field.
- Task 5 Step 3: the audit script may find additional mismatches. Each mismatch is a data-only fix (change `"band"` or swap an ingredient `"id"` in the recipe entry).
