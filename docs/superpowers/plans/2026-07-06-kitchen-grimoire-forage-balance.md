# Kitchen Preview Bug, Grimoire Bypass, Forage Scope & Balance Backlog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two confirmed bugs from the 06 Jul 2026 playtest, implement Wes's forage-scope call, and plan out the two items originally logged as "not planned": the tier-vs-grade balance tension, and the 29 dead-end ingredients. Task 4 (reconciling two incompatible grade-bonus formulas between the shipped app and the balance tools) was discovered while investigating the balance question and blocks Task 5.

**Architecture:** Six tasks. Tasks 1–3 are independent of everything else. Task 5 depends on Task 4 (can't validate tier-vs-grade balance on a sim that's computing the wrong numbers). Task 6 (recipe authoring) is independent of all of them.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, kotlinx.serialization, WorkManager

## Global Constraints

- All Kotlin source lives under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- All game data JSON lives under `app/src/main/assets/data/`
- Commit messages prefixed `[hc]`
- Run `./gradlew build` before every commit — never commit a broken build (`export JAVA_HOME=/usr/share/pycharm/jbr` first)
- Do not bump `versionName`/`versionCode`
- GPL-3.0

## Triage: all four items from this session

| # | Item | Verdict | Where it's handled |
|---|---|---|---|
| 1 | "Predicted" stat line in Kitchen doesn't change when you pick a different ingredient grade | Confirmed UI-only bug — `RecipeDetailPanel`'s `effectLine` renders `recipe.primaryBoost` raw. The real combat math (`BandRepository.statBonusFor` × `gradeMultiplier`) already scales Crude ×0.7 → Pristine ×1.7 correctly at mission time — the Kitchen screen just never shows it | Task 1 |
| 2 | Journeyman-tier recipe (Heartflame Broth, tier 4 draught) visible with no grimoire found — no `draught_t4` grimoire even exists yet | Confirmed bug — `CookingWorker`'s level-up auto-discover filters only on `cookLevel <= newLevel`, with zero grimoire check. Bypasses `isRecipeVisible()` for every food/draught recipe; only HoH is excluded from the hole | Task 2 |
| 3 | Tier-to-tier stat growth (+2 total per tier at baseline) is roughly the same size as the grade-quality swing, so a Pristine low-tier dish can match a Common next-tier dish | Bigger than first thought. The in-game math (`QualityUtils.kt`) and the balance tools (`food_model.js`, used by both `run_sim.js` and the HTML fight sim) implement two different grade-bonus formulas — see Task 4. Can't validate tier-vs-grade balance until the tools agree with the app | Task 4 (reconcile formulas), then Task 5 (the actual sweep) |
| 4 | Ingredient sourcing feels lopsided — pantry fills with stuff nothing uses, hero ingredients (e.g. Rabbit) never show up | Split verdict. (a) 29 ingredients in Wes's own playable regions are consumed by zero recipes anywhere. (b) hunt/fish/draw gather modes had no acquisition UI | (a) Task 6 — recipe-authoring drafts for 26 of the 29. (b) Resolved: folded into forage, Task 3 |

---

### Task 1: Scale the Kitchen "Predicted" stat line by the selected grade

**Context:** `KitchenScreen.kt`'s `RecipeDetailPanel` computes `effectLine` from `recipe.primaryStat`/`recipe.primaryBoost` alone — it never looks at `predictedGrade` (already passed into the composable as a parameter) even though the row directly below it renders that exact `predictedGrade` as a badge. Meanwhile `BandRepository.kt` (companion `statBonusFor`) already multiplies the authored boost by `gradeMultiplier(grade)` from `QualityUtils.kt` (Crude 0.7x, Common 0.85x, Fine 1.0x, Superb 1.3x, Pristine 1.7x) when a mission actually resolves. The fix is display-only — no change to the underlying combat math, which is already correct.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`

**Interfaces:** `gradeMultiplier(grade: Grade): Float` already exists and is public in `data/model/QualityUtils.kt` — just needs importing into `KitchenScreen.kt`.

- [ ] **Step 1: Recompute effectLine's numeric portion using the predicted grade**
  In `RecipeDetailPanel`, where `effectLine` is built (around the `recipe.primaryStat != null && statName != null -> "$statName +${recipe.primaryBoost}"` branch), multiply `recipe.primaryBoost` by `gradeMultiplier(predictedGrade?.first ?: Grade.FINE)` before formatting. Round for display (`.roundToInt()` — matches the rounding `CookQuality`/`resolveDishGrade` already use elsewhere) rather than truncating. Leave the `recipe.penalty` (negative-stat) branch's sign handling intact — it should scale the same way, just staying negative.
- [ ] **Step 2: Verify draught rows are unaffected**
  The `recipe.hazardEffect != null` branch (Warmth/Alert/Hale/Potency/Radiance) has no numeric value today and isn't part of this bug — leave it as-is. Confirm visually after the change that draught recipe rows still show their plain descriptive text with no stray number appended.
- [ ] **Step 3: Manual check against Brookcress Bannock**
  Cooking lv6, Brookcress at Pristine/Pristine/Pristine should now show "Will +3" (2 × 1.7 = 3.4 → rounds to 3), not "Will +2", while Common/Crude/Common should still show "Will +1" or "+2" depending on rounding — pick whichever rounding rule (`roundToInt` vs floor) the sim/`CookQuality` already uses elsewhere so the Kitchen preview and the actual mission math never visibly disagree at the boundary.
- [ ] **Step 4: Run tests, build, commit**
  ```bash
  ./gradlew build
  git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt
  git commit -m "[hc] Kitchen: scale predicted stat line by selected grade"
  ```

---

### Task 2: Route level-up auto-discovery through grimoire visibility

**Context:** `CookingWorker.doWork()`, on a cooking-XP level-up, bulk-discovers every recipe that's now within `cookLevel` — but the filter never calls `isRecipeVisible()` the way `KitchenViewModel.tieredRecipes`/`bandRecipes` do. Any tier-2+ food or draught recipe whose `cookLevel` happens to be ≤ the new level gets silently added to `discoveredRecipeIds`, regardless of whether the matching `{class}_t{tier}` grimoire has been found — or even exists yet (`draught_t4` isn't in `grimoires.json` at all; only `cooking_t2`, `draught_t2`, `hoh_t1` exist). This is how Heartflame Broth (tier 4, cookLevel 6) appeared the moment Wes hit cook level 6.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt`

**Interfaces:** `isRecipeVisible(recipe, foundGrimoires, discoveredIds)` already exists and is public in `data/repository/GameDataRepository.kt`. `PlayerRepository` already has a suspend `getFoundGrimoireIds(): Set<String>` alongside the existing `getDiscoveredRecipeIds()` used in this same function — no new repository method needed.

- [ ] **Step 1: Import isRecipeVisible**
  Add `import com.liquidcode7.hearthcraft.data.repository.isRecipeVisible` to `CookingWorker.kt`.
- [ ] **Step 2: Fetch found grimoires alongside the existing discovered-ids fetch**
  In the level-up branch, next to `val currentDiscovered = snapshot?.discoveredRecipeIds?.split(",")...`, add: `val foundGrimoires = player.getFoundGrimoireIds()`.
- [ ] **Step 3: Add the visibility check to the toDiscover filter**
  Change:
  ```kotlin
  val toDiscover = gameData.recipes.filter { recipe ->
      recipe.cookLevel <= newLevel
          && recipe.recipeClass != "hoh"
          && recipe.id !in currentDiscovered
          && (recipe.band == bandId || recipe.band == "all")
  }.map { it.id }
  ```
  to also require `&& isRecipeVisible(recipe, foundGrimoires, currentDiscovered)`. Since `isRecipeVisible` already returns true for tier == 1 non-HoH recipes and for anything already in `currentDiscovered`, this doesn't change behavior for T1 starter recipes — it only closes the hole for tier 2+ recipes whose grimoire hasn't been found.
- [ ] **Step 4: Confirm the recipe-you-just-cooked path is untouched**
  `player.discoverRecipe(recipeId)` (unconditional, right after cooking) should stay exactly as-is — cooking something you already had access to always marks it known. This task only touches the bulk level-up discovery branch.
- [ ] **Step 5: Add a regression test**
  Extend the existing Grimoire test suite (`GrimoireDataTest`/`RecipeAvailabilityTest` territory) with a case: player at cook level 5, no grimoires found, levels up to cook level 6 with a tier-4 recipe at `cookLevel = 6` in the data — assert that recipe is not in `discoveredRecipeIds` afterward. Pair with a positive case: same setup but `cooking_t2` already found, and a tier-2 food recipe at `cookLevel = 6` — assert that one is discovered.
- [ ] **Step 6: Run tests, build, commit**
  ```bash
  ./gradlew build
  git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt
  git commit -m "[hc] Fix level-up auto-discover bypassing grimoire gating"
  ```

---

### Task 3: Fold hunt/fish/draw gather modes into forage

**Context:** Wes's call this session — hunting, fishing, and drawing water don't need their own mechanic; "foraging could be anything." This keeps Gathering as the single lever it's meant to be (no new skill tracks beyond Cooking and Gathering). Confirmed via code read: `gatheringMode` is compared for exact equality against `MODE_FORAGE` in exactly two places (`GatheringViewModel.foragableIngredients`, `GatheringWorker`'s forage-roll gate) and nowhere else branches on hunt/fish/draw specifically. This is a pure data change — no Kotlin touched.

**Files:**
- Modify: `app/src/main/assets/data/ingredients.json`

**Decision baked into this task (flagged, not silently assumed):** Rabbit, Hog Meat, Deer Haunch, and Eel are already mission rewards (e.g. Rabbit from "Wolves in the Chetwood"). Folding them into forage makes them dual-sourced — a common forage trickle and a mission-reward burst. Proceeding with dual-sourcing as the default; revisit if a specific mission's reward table was meant to be that item's only source.

**Not touched:** husbandry (Forest Honey, Field Honey, Hen's Egg, Milk, Rendered Beeswax, White Nectar, Heather Honey) reads as a raise-it-yourself mechanic closer to farm than forage — Wes's ask was specifically "meat," and husbandry wasn't part of the original hunt/fish/draw complaint. trade (Beorn's Honey), craft (Athelas Concentrate), mission (Black Mushroom, Old Vintage fragment), and found (Miruvor Dilute) are all deliberately gated behind other systems and stay as-is.

- [ ] **Step 1: Change gatheringMode to "forage" for the 12 hunt/fish/draw ingredients**
  In `ingredients.json`, change `"gatheringMode"` from its current value to `"forage"` for these ids (leave every other field — source, gatherType, region, stats, notes — untouched; they still carry accurate flavor/lore even once the mechanic is unified):

  | id | current mode |
  |---|---|
  | rabbit | hunt |
  | hog_meat | hunt |
  | deer_haunch | hunt |
  | river_trout | fish |
  | eel | fish |
  | bree_well_water | draw |
  | brandywine_water | draw |
  | chetwood_spring | draw |
  | spring_water | draw |
  | lhun_brine | draw |
  | deep_cistern_water | draw |
  | pass_snowmelt | draw |

- [ ] **Step 2: Fix the healing_clay typo in the same pass**
  `healing_clay` has `"gatheringMode": "gather"`, which matches neither `MODE_FORAGE` nor `MODE_FARM` — it's unreachable regardless of this task, and reads like a straight typo for "forage". Fix it here since the file's already open; flag to Wes in the commit message in case there's a reason it was deliberately excluded.
- [ ] **Step 3: Confirm region matching still works**
  `foragableIngredients` also filters on `GatheringWorker.foragableRegions(bandId)` matching `ingredient.region`. Rabbit/Hog Meat/well-waters are already tagged Bree-land, Deer Haunch/Eel Lone-Lands — both should already be in Greycloaks' region set. Spot-check the other bands' newly-foldable ingredients (Lhûn Brine → Celondim/Mithlost, Deep Cistern Water + Pass Snowmelt → Thorin's Halls/Undermarch) land in their respective region lists too.
- [ ] **Step 4: Manual check — Rabbit appears in the Bree-land forage list**
  As Greycloaks, open Gather → confirm Rabbit now appears as a selectable forage target alongside Brookcress/Hearthgrain, and that a completed forage session can actually return it.
- [ ] **Step 5: Build, commit**
  ```bash
  ./gradlew build
  git add app/src/main/assets/data/ingredients.json
  git commit -m "[hc] Fold hunt/fish/draw gather modes into forage; fix healing_clay typo"
  ```

---

### Task 4: Reconcile the two grade-bonus formulas (blocks Task 5)

**Context:** Found while investigating item #3. Two completely different formulas exist for the same concept — how much a food's grade should scale its authored stat boost:
- Shipped app (`app/.../data/model/QualityUtils.kt`, actually running in `BandRepository.kt` today): multiplicative — `authoredBoost × GRADE_MULTIPLIER`, where `GRADE_MULTIPLIER = [0.7, 0.85, 1.0, 1.3, 1.7]` (Crude→Pristine).
- Balance tools (`tools/sim/food_model.js`, required by both `run_sim.js` and inlined directly into `hearthcraft_fight_sim.html`): additive — `authoredBoost + GRADE_STEPS`, where `GRADE_STEPS = [0, 1.0, 2.0, 3.5, 5.5]`.

These produce wildly different numbers for the same dish. A tier-1 recipe (`primaryBoost = 2`) at Pristine: the app gives 2 × 1.7 = 3.4; the sim gives 2 + 5.5 = 7.5. Every balance conclusion anyone has drawn from either sim tool about food/grade power is describing a game that isn't the one that ships. This has to be fixed before Task 5's sweep is worth running.

**Status:** Confirmed by Wes, 06 Jul 2026 — multiplicative (`QualityUtils.kt`) is canonical. `food_model.js` and the HTML sim's inlined copy are the ones that get fixed.

**Files:**
- Modify: `tools/sim/food_model.js`
- Modify: `tools/sim/hearthcraft_fight_sim.html` (has its own inlined copy per the comment at line 182 — must be updated in lockstep with `food_model.js` or the two tools will drift from each other the same way the sim drifted from the app)

- [x] **Step 1: Confirm with Wes which formula is canonical** — Confirmed multiplicative.
- [ ] **Step 2: Replace GRADE_STEPS + effectiveStatBoost in food_model.js**
  Swap the additive table/function for the multiplicative one:
  ```js
  const GRADE_MULTIPLIER = [0.7, 1.0, 1.0, 1.3, 1.7]; // placeholder — mirror QualityUtils.kt exactly, including its own TODO:TUNE status
  function effectiveStatBoost(authoredBoost, gradeOrdinal) {
    if (authoredBoost === 0) return 0;
    return authoredBoost * (GRADE_MULTIPLIER[gradeOrdinal] ?? 1.0);
  }
  ```
  (Pull the actual current values straight from `QualityUtils.kt` at implementation time rather than copying the array above by hand — they're still marked `TODO:TUNE` and may have moved. As of this writing they are `[0.7, 0.85, 1.0, 1.3, 1.7]`.)
- [ ] **Step 3: Apply the same change to the inlined copy in hearthcraft_fight_sim.html**
- [ ] **Step 4: Re-run any existing saved sim scenarios and confirm output numbers shift in the expected direction (smaller grade spread, since multiplicative on a small base boost is a smaller swing than the old additive table)**
- [ ] **Step 5: Commit**
  ```bash
  git add tools/sim/food_model.js tools/sim/hearthcraft_fight_sim.html
  git commit -m "[hc] Sim: match food grade-bonus formula to shipped QualityUtils.kt (multiplicative, not additive)"
  ```

---

### Task 5: Tier-vs-grade balance sweep (depends on Task 4)

**Context:** This is the actual "stats barely increase with next tier" question. Not tunable until Task 4 lands — no point sweeping a formula that doesn't match the app.

- [ ] **Step 1:** Write a small sweep script (in `tools/sim/`, alongside `curve_lab.js`/`xp_lab.js` as precedent) that, for every recipe tier that has a stat boost, prints the effective stat value at all five grades, and flags any case where a lower tier's Pristine result meets or beats a higher tier's Common/Fine result — this reproduces, with corrected numbers, the exact comparison flagged in item #3.
- [ ] **Step 2:** Run it, share the output table with Wes — don't pre-decide the fix. Possible levers, all his call once he sees real numbers: widen the per-tier `primaryBoost`/`secondaryBoost` deltas in `recipes.json`, narrow the `GRADE_MULTIPLIER` spread in `QualityUtils.kt`, or leave it — quality mastery mattering a lot relative to tier might be the intended feel, not a flaw.
- [ ] **Step 3:** Once Wes picks a direction, that's a follow-up task — not written here, since it means committing actual tuning numbers, which is Wes's/the sim's call, not something to hand-wave in a planning doc.

---

### Task 6: Tier/grade rebalance (widen tier deltas, narrow grade spread)

**Context:** Task 5's sweep confirmed the tier-vs-grade inversion is universal (39 flagged pairs, every band, every stat) — a maxed-Pristine dish at one tier meets or beats the *next* tier's Fine-quality dish, because `GRADE_MULTIPLIER`'s spread (0.7x–1.7x, a 2.43x ratio) is wider than the entire 4-tier boost progression (2→3→4→4, at most 2x, and 0x between T3 and T4 specifically — `trout_and_mushroom_pan` and `rangers_fare` currently share the identical `primaryBoost: 4`). Wes's call, 07 Jul 2026: fix both sides — narrow the grade spread and widen the tier deltas. Verified by direct calculation against the real recipe data (not just the two Task-5-report examples): with `GRADE_MULTIPLIER = [0.8, 0.9, 1.0, 1.15, 1.3]` and tier boosts primary `2/4/6/8` + secondary `-/2/3/4`, **every "meets/beats Fine" inversion is eliminated** at all three tier boundaries (T1→T2, T2→T3, T3→T4), for both primary and secondary stats. The only inversion remaining anywhere is Pristine-vs-next-tier's-bare-Common at the T3→T4 boundary (7.80 vs 7.20) — judged an acceptable trade-off (quality mastery beating someone who just unlocked the tier and hasn't invested in quality at all), not the "outclasses the whole next tier" problem being fixed.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/QualityUtils.kt`
- Modify: `app/src/main/assets/data/recipes.json` (10 tier-2 recipes, 1 tier-3 recipe, 1 tier-4 recipe)
- Modify: `tools/sim/food_model.js` + inlined copy in `tools/sim/hearthcraft_fight_sim.html`

**Interfaces:** `gradeMultiplier(grade: Grade): Float` and `GRADE_MULTIPLIER` stay the same shape/signature — only the five float values inside the array change. No callers need updating.

- [ ] **Step 1: Update GRADE_MULTIPLIER in QualityUtils.kt**

  Find:
  ```kotlin
  private val GRADE_MULTIPLIER = floatArrayOf(
      0.7f,  // CRUDE    (ordinal 0)
      0.85f, // COMMON   (ordinal 1)
      1.0f,  // FINE     (ordinal 2) — baseline
      1.3f,  // SUPERB   (ordinal 3)
      1.7f,  // PRISTINE (ordinal 4)
  )
  ```
  Replace with:
  ```kotlin
  private val GRADE_MULTIPLIER = floatArrayOf(
      0.8f,  // CRUDE    (ordinal 0)
      0.9f,  // COMMON   (ordinal 1)
      1.0f,  // FINE     (ordinal 2) — baseline
      1.15f, // SUPERB   (ordinal 3)
      1.3f,  // PRISTINE (ordinal 4)
  )
  ```
  Note: `QualityUtilsTest.kt`'s existing `gradeMultiplier` tests are all property-based (Crude positive and below Fine's 1.0 baseline; Fine exactly 1.0; strictly monotonic increasing; never negative) — no hardcoded exact values, so no test changes needed here; running the suite (Step 6) will confirm they still pass against the new array.

- [ ] **Step 2: Widen tier-2 boosts in recipes.json**

  In `recipes.json`, for these 10 tier-2 recipes, change `primaryBoost` from `3` to `4` and `secondaryBoost` from `1` to `2` (leave every other field — `primaryStat`, `secondaryStat`, ingredients, description — untouched):

  | id | primaryStat | secondaryStat |
  |---|---|---|
  | hearth_and_hops | mig | vit |
  | chetwood_rabbit_roast | agi | mig |
  | field_and_fen_potage | vit | wil |
  | honey_oat_cake | wil | agi |
  | saltfish_and_kelp | mig | vit |
  | silver_leek_omelette | agi | wil |
  | fennel_and_cheese_bake | vit | wil |
  | marrow_and_malt_broth | mig | vit |
  | goat_and_ironfoot | agi | mig |
  | deepcap_and_malt_loaf | vit | mig |

- [ ] **Step 3: Widen tier-3 and tier-4 boosts in recipes.json**

  - `trout_and_mushroom_pan` (tier 3): `primaryBoost` 4 → **6**, `secondaryBoost` 2 → **3**
  - `rangers_fare` (tier 4): `primaryBoost` 4 → **8**, `secondaryBoost` 2 → **4**

  (This also directly fixes the most extreme case from Task 5's report — these two recipes no longer share an identical `primaryBoost`.)

- [ ] **Step 4: Confirm no other recipe/test hardcodes these tier's old boost values**

  Already checked during planning: `RecipeStatTest.kt` and `MemberInputStatTest.kt` use inline synthetic literals (e.g. `primaryBoost = 3`) as test fixtures unrelated to any specific recipe id or tier — they are not testing `hearth_and_hops`/`trout_and_mushroom_pan`/etc. specifically, so they need no changes. `BalanceHarness.kt` reads `recipe.primaryBoost`/`recipe.secondaryBoost` generically (no hardcoded tier values). No action needed here beyond running the full suite in Step 6 to confirm.

- [ ] **Step 5: Mirror both changes into tools/sim/food_model.js**

  Find (the `GRADE_MULTIPLIER` array, set by Task 4):
  ```js
  const GRADE_MULTIPLIER = [0.7, 0.85, 1.0, 1.3, 1.7];
  ```
  Replace with:
  ```js
  const GRADE_MULTIPLIER = [0.8, 0.9, 1.0, 1.15, 1.3];
  ```

  Then update the same 12 recipes' `primaryBoost`/`secondaryBoost` in `food_model.js`'s embedded `RECIPES` array (matching the recipes.json changes from Steps 2-3 exactly — same 10 tier-2 recipes to `primaryBoost:4, secondaryBoost:2`, `trout_and_mushroom_pan` to `primaryBoost:6, secondaryBoost:3`, `rangers_fare` to `primaryBoost:8, secondaryBoost:4`). Locate each by `id` in the array (same ids as the table in Step 2, plus the two named in Step 3).

  Repeat both edits (the `GRADE_MULTIPLIER` array and the same 12 recipes' boost values) in the inlined copy inside `tools/sim/hearthcraft_fight_sim.html` — grep for `GRADE_MULTIPLIER` in that file to confirm whether Task 4 already gave it its own inlined copy of this array (Task 4's report noted the HTML's *food-model* section is actually stale pre-redesign v1 code with no grade concept — if `GRADE_MULTIPLIER` genuinely doesn't appear anywhere in that file, there is nothing to mirror there and this step is a no-op for the HTML file, matching Task 4's own precedent of not inventing grade mechanics in dead legacy code).

- [ ] **Step 6: Run the full test suite and the tier/grade sweep script**

  ```bash
  export JAVA_HOME=/usr/share/pycharm/jbr
  ./gradlew build
  node tools/sim/tier_grade_sweep.js
  ```
  Expected: `./gradlew build` succeeds, all existing tests pass unchanged (per Steps 1/4's analysis, nothing hardcodes the old values). The sweep script's "TIER-INVERSION FLAGS" section should now show zero "meets/beats Fine" results — only "meets/beats Common" (or no flags at all) should remain, confirming the fix.

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/QualityUtils.kt app/src/main/assets/data/recipes.json tools/sim/food_model.js tools/sim/hearthcraft_fight_sim.html
  git commit -m "$(cat <<'EOF'
  [hc] Fix tier-vs-grade inversion: narrow GRADE_MULTIPLIER, widen tier boosts

  tools/sim/tier_grade_sweep.js (Task 5) found the inversion is
  universal: grade quality's spread (0.7x-1.7x, 2.43x ratio) exceeded
  the entire 4-tier boost progression (2x max, 0x between T3/T4
  specifically). Fixes both sides per Wes's call: GRADE_MULTIPLIER
  narrows to [0.8, 0.9, 1.0, 1.15, 1.3] (1.625x ratio); tier primary
  boosts widen from 2/3/4/4 to 2/4/6/8 (secondary 1/2/2 to 2/3/4).
  Verified analytically: eliminates every "beats Fine" inversion at
  all three tier boundaries; only a minor "beats bare Common" edge
  remains at T3->T4, judged an acceptable trade-off. Mirrored into
  both sim tools per their "must match" convention.
  EOF
  )"
  ```

---

### Task 7: Author recipes for the 29 dead-end ingredients

**Context:** Concrete recipe drafts covering 26 of the 29 orphaned ingredients found in item #4a, grouped so each new recipe clears 2–4 dead ingredients at once rather than authoring 29 one-off recipes. Follows the **rebalanced** per-tier stat convention from Task 6 (T1 = primary+2 only; T2 = primary+4/secondary+2; T3 = primary+6/secondary+3; T4 = primary+8/secondary+4 — widened from the pre-Task-6 2/3/4/4 convention to fix the tier-vs-grade inversion) and matches the existing terse, plainspoken description voice (see `hearth_and_hops`, `rangers_fare`). All band "greycloaks", all region-appropriate (Bree-land / Lone-Lands / North Downs — Weather Hills). Hero ingredient qty 2, others qty 1, matching existing recipes' pattern.

**Not covered, flagged separately:**
- Field Honey, Heather Honey — husbandry-sourced. Same class of gap hunt/fish/draw was in before Task 3, just not part of Wes's "meat" decision this session. Any new recipe using them is still uncookable until husbandry gets an acquisition path — that's a separate design call, not raised here.
- Eel — has no `primaryStat`/`secondaryStat` tags at all (same as the four water ingredients), which reads as intentional: it's a flavor/filler ingredient meant to slot into future recipes as a secondary, not to headline one. Left for whenever the next Lone-Lands recipe gets authored rather than forcing a recipe around it now.

**Files:**
- Modify: `app/src/main/assets/data/recipes.json` (add the 11 entries below)
- Modify: `tools/sim/food_model.js` + inlined copy in `hearthcraft_fight_sim.html` (mirror the new recipes into the `RECIPES` array, same as every existing recipe — keeps the sim/app recipe lists in sync, which is the whole point of Task 4)

- [ ] **Step 1: Add these 11 recipes to recipes.json**

  ```json
  { "id": "bree_root_pottage", "name": "Bree Root Pottage", "band": "greycloaks",
    "class": "food", "method": "cook", "tier": 1, "cookLevel": 1,
    "primaryStat": "vit", "primaryBoost": 2,
    "description": "Boiled roots in well-water, plain and filling. What you eat when there's nothing else.",
    "heroIngredient": "parsnip",
    "ingredients": [ {"id":"parsnip","qty":2}, {"id":"turnip","qty":1}, {"id":"bree_well_water","qty":1} ] }

  { "id": "pipeweed_crumble", "name": "Pipeweed Crumble", "band": "greycloaks",
    "class": "food", "method": "bake", "tier": 2, "cookLevel": 4,
    "primaryStat": "wil", "primaryBoost": 4, "secondaryStat": "vit", "secondaryBoost": 2,
    "description": "Rye crumble laced with dried pipe-leaf. Calms the nerves before a hard road.",
    "heroIngredient": "pipeweed_leaf",
    "ingredients": [ {"id":"pipeweed_leaf","qty":2}, {"id":"rye_stalk","qty":1}, {"id":"brandywine_water","qty":1} ] }

  { "id": "crabapple_blush_tart", "name": "Crabapple Blush Tart", "band": "greycloaks",
    "class": "food", "method": "bake", "tier": 1, "cookLevel": 1,
    "primaryStat": "agi", "primaryBoost": 2,
    "description": "Tart crabapple and blushcap, quick to bake, quicker to eat.",
    "heroIngredient": "crabapple",
    "ingredients": [ {"id":"crabapple","qty":2}, {"id":"blushcap","qty":1}, {"id":"chetwood_spring","qty":1} ] }

  { "id": "rendered_hearth_pie", "name": "Rendered Hearth Pie", "band": "greycloaks",
    "class": "food", "method": "bake", "tier": 2, "cookLevel": 5,
    "primaryStat": "mig", "primaryBoost": 4, "secondaryStat": "vit", "secondaryBoost": 2,
    "description": "Cheese and rendered fat baked into a heavy crust. Sits like a stone, in a good way.",
    "heroIngredient": "rendered_fat",
    "ingredients": [ {"id":"rendered_fat","qty":2}, {"id":"hard_cheese","qty":1}, {"id":"spring_water","qty":1} ] }

  { "id": "lone_lands_venison", "name": "Lone-Lands Venison", "band": "greycloaks",
    "class": "food", "method": "cook", "tier": 3, "cookLevel": 10,
    "primaryStat": "mig", "primaryBoost": 6, "secondaryStat": "vit", "secondaryBoost": 3,
    "description": "Cured haunch roasted slow over acorn-wood embers. Lone-Lands fare, built for the long watch.",
    "heroIngredient": "deer_haunch_dried",
    "ingredients": [ {"id":"deer_haunch_dried","qty":2}, {"id":"chetwood_acorn","qty":1} ] }

  { "id": "old_forest_broth", "name": "Old Forest Broth", "band": "greycloaks",
    "class": "food", "method": "cook", "tier": 1, "cookLevel": 1,
    "primaryStat": "wil", "primaryBoost": 2,
    "description": "Deep-wood mushrooms simmered plain. Quiets the mind more than the tongue.",
    "heroIngredient": "old_forest_mushroom",
    "ingredients": [ {"id":"old_forest_mushroom","qty":2}, {"id":"pale_fen_cap","qty":1} ] }

  { "id": "roadwise_berry_mix", "name": "Roadwise Berry Mix", "band": "greycloaks",
    "class": "food", "method": "cook", "tier": 2, "cookLevel": 5,
    "primaryStat": "agi", "primaryBoost": 4, "secondaryStat": "vit", "secondaryBoost": 2,
    "description": "Thornberry and road-herb, chewed on the move. Keeps the legs going.",
    "heroIngredient": "thornberry",
    "ingredients": [ {"id":"thornberry","qty":2}, {"id":"road_herb","qty":1}, {"id":"moorgrass_seed","qty":1} ] }

  { "id": "marshwort_tonic", "name": "Marshwort Tonic", "band": "greycloaks",
    "class": "draught", "method": "brew", "tier": 2, "cookLevel": 4,
    "hazardEffect": "hale",
    "description": "Bitter marsh-root, boiled thin. Keeps the fever off the Fen-crossers.",
    "heroIngredient": "marshwort",
    "ingredients": [ {"id":"marshwort","qty":2}, {"id":"bree_well_water","qty":1} ] }

  { "id": "wolf_moss_draught", "name": "Wolf-moss Draught", "band": "greycloaks",
    "class": "draught", "method": "brew", "tier": 2, "cookLevel": 5,
    "hazardEffect": "alert",
    "description": "Grey moss off the wolf-dens, bitter enough to keep any watchman awake.",
    "heroIngredient": "wolf_moss",
    "ingredients": [ {"id":"wolf_moss","qty":2}, {"id":"mountain_sorrel","qty":1} ] }

  { "id": "weather_hills_grouse", "name": "Weather Hills Grouse", "band": "greycloaks",
    "class": "food", "method": "cook", "tier": 3, "cookLevel": 10,
    "primaryStat": "mig", "primaryBoost": 6, "secondaryStat": "vit", "secondaryBoost": 3,
    "description": "Smoked grouse off the Hills, mushroom-stewed. Warms even a North Downs wind.",
    "heroIngredient": "peat_smoked_grouse",
    "ingredients": [ {"id":"peat_smoked_grouse","qty":2}, {"id":"fell_mushroom","qty":1}, {"id":"hillwort","qty":1} ] }

  { "id": "crowberry_warmth_draught", "name": "Crowberry Warmth Draught", "band": "greycloaks",
    "class": "draught", "method": "brew", "tier": 2, "cookLevel": 5,
    "hazardEffect": "warmth",
    "description": "Sharp crowberries, boiled down bitter. The old Downs-remedy for a bone-deep chill.",
    "heroIngredient": "bitter_crowberry",
    "ingredients": [ {"id":"bitter_crowberry","qty":2}, {"id":"hillwort","qty":1} ] }
  ```

  (Written as readable blocks, not one valid JSON array — fold each into `recipes.json`'s actual array syntax/formatting when implementing.)

- [ ] **Step 2: Ingredient coverage check**
  Confirms all 11 pull only from ingredients that already exist in `ingredients.json` and (after Task 3) are actually obtainable. `hillwort` and `moorgrass_seed` are each reused across two recipes, matching how existing ingredients like leek/barleycorn already get reused — not a problem. (Verified during planning: all 26 referenced ingredient IDs exist in `ingredients.json` — no typos.)
- [ ] **Step 3: Mirror the same 11 entries into tools/sim/food_model.js's RECIPES array**
  (and the inlined copy in the HTML sim), same shape as the existing entries there, so the balance tools stay in sync with the shipped data — this is the exact drift Task 4 is fixing; don't reintroduce it here.
- [ ] **Step 4: Build, commit**
  ```bash
  ./gradlew build
  git add app/src/main/assets/data/recipes.json tools/sim/food_model.js tools/sim/hearthcraft_fight_sim.html
  git commit -m "[hc] Author 11 recipes closing 26 of the 29 dead-end ingredients"
  ```

---

## Logged, not planned

What's still genuinely open (inside the tasks above, not deferred past them): everything from this session now has a task, and Task 4's formula question is settled (multiplicative). One thing inside these tasks is still a real decision rather than a mechanical step:

- Task 5, Step 2: whatever the sweep turns up about tier deltas vs. grade spread — intentionally not pre-solved here.

Husbandry (Field Honey, Heather Honey) and Eel stay out of scope, as noted in Task 6 — neither was part of what Wes asked to fold in this session.
