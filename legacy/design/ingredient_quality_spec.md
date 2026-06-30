# Ingredient Quality System — Design Spec

**Date:** 2026-06-29
**Status:** Approved — ready for phased implementation
**Supersedes:** The "ingredient quality tiers are unwanted" pillar in `brainstorm/redefinition.md`

---

## 0. Pillar Reversal (do this first)

`brainstorm/redefinition.md` currently lists under "What Did Not Change":

> Ingredient quality tiers are unwanted — complexity lives in the cook's skill
> and decisions, never raw materials.

**This is overturned.** Ingredient quality is now a core axis of the crafting game.

The reversal is not "raw materials replace cook skill." The justification is:
**quality is the bridge that makes the whole Gather → Process → Cook chain matter
end to end.** Gather earns grade, Process carries it, Cook resolves it, combat
consumes it. Cook skill still governs access and the quality ceiling — raw
materials are now an *input to* the cook's outcome, not a substitute for skill.

**Action:** Edit `redefinition.md` — remove the "unwanted" line, replace with a
short note that ingredient quality is in, with the justification above. Keep the
edit minimal; this spec is the authoritative source for the mechanics.

---

## 1. The Model (the locked spine)

### 1.1 Quality grades

Quality is **discrete**, five grades, **tier-relative**:

```
Crude → Common → Fine → Superb → Pristine
```

- **Tier-relative** means the five grades reset within each of the 7 craft tier
  bands. "Pristine" is *Pristine for its tier* — a Pristine T1 ingredient and a
  Pristine T4 ingredient are both top-of-band, but the T4 is categorically more
  powerful because Tier sets the floor.
- Effective ceiling: 7 tiers × 5 grades ≈ 35 quality steps across the game.
- Store grade as an integer ordinal `0..4` (Crude=0 … Pristine=4) internally;
  display the name. **Never store the localized name as the key.**

### 1.2 Orthogonal axes (the anti-confusion rule)

Two power axes that must never overlap:

| Axis | Controls | Set by |
|---|---|---|
| **Tier (T1–T7)** | HP/s sustain band | Authored per recipe (existing `tier` field) |
| **Quality (grade)** | Stat boost magnitude | Resolved from ingredients at cook time |

Tier never touches stats. Quality never touches HP/s. This separation is the
whole point — it is what stops "Tier" and "Food Level" from both meaning
"strong," which was the original confusion this redesign exists to kill.

> **Terminology:** "Food Level" is **retired** as a concept. Do not introduce a
> per-recipe leveling track. A dish's stat power comes entirely from its
> **grade**, resolved at cook time. The only level that climbs is **Cook Level**
> (see §1.6).

### 1.3 Gather — the sole source of grade

- A gather **session produces a single grade** — everything returned that
  session shares one grade.
- Grade is a **weighted random roll** on a distribution. Genuinely random every
  session. The player tilts odds, never buys an outcome.
- **Gathering level** slides the *center* of the distribution upward over time.
  Low level: curve bunched on Crude/Common, thin tail to Pristine. High level:
  whole curve slides up; Pristine becomes *plausible*, never *promised*. There is
  no guaranteed floor.
- A **gathering aid** (consumable, non-food — see §1.5) slides the whole curve up
  for one chosen session: **reliable uplift** (every grade gets likelier to be
  better, including Pristine), not a rare-jackpot spike.
- Gather is the **only** place grade enters the system.

### 1.4 Process — preserves grade

- Process **preserves** input grade. Output grade = input grade. The Process
  station transforms ingredients (milk→butter, raw→cured); it does **not** refine
  quality.
- A Fine input yields a Fine output. No laundering a bad pull into a good one.
- **PARKED (Phase 2, deferred — do not build now):** a later-game mechanic may
  let Process *upgrade* grade as an advanced refinement lever. When that lands it
  reintroduces a ceiling-clamp problem (grade climbing at both gather and
  process). Out of scope for this build. Recorded in `docs/parked_topics.md`.

### 1.5 Gathering aid

A consumable item, **not food**, that the player spends on a single gather
session to apply reliable uplift to that session's grade distribution.

- It is to gathering what food is to combat: a prepared/found item that tilts a
  roll. It respects the fiction — the Hearthwright never eats their own
  provisions, so this is *not* food and does *not* route through the food system.
- **Source of the aid is itself PARKED** for the discovery/recipe session that
  follows this one. For this build: define the data shape and the consumption
  hook, but the aid's *recipe/acquisition* is stubbed (a single starter aid type
  is acceptable, or the slot can be wired and left empty pending the next
  session's design). Flag clearly in code where the aid recipe will plug in.

### 1.6 Cook Level (what it does, finally)

Cook Level is the single easy-to-climb lever. It does exactly two things:

1. **Gates recipe access** (existing `cookLevel` field on recipes = unlock level).
2. **Caps the quality ceiling** of a cooked dish (see §1.7). A low cook level
   cannot produce a Pristine dish even from Pristine ingredients.

Nothing else bolts onto Cook Level. XP/leveling curve is unchanged by this spec.

### 1.7 Cook — resolving a dish's grade

When a dish is cooked, its grade is the **hero-weighted average** of its
ingredient grades, then **clamped by the cook ceiling**.

**Hero ingredient:** each recipe names one ingredient as its **hero**. The hero's
grade counts **double**; every other (supporting) ingredient counts **once**.

```
weightedSum   = heroGrade*2 + Σ(supportingGrade_i * 1)
weightDivisor = 2 + (number of supporting ingredients)
rawDishGrade  = round(weightedSum / weightDivisor)        // ordinal 0..4
dishGrade     = min(rawDishGrade, cookCeiling(cookLevel)) // §1.8
```

- Round half-up.
- Quantities in the recipe do **not** affect the weighting — a recipe ingredient
  is one "voice" regardless of qty (hero=2 voices, each support=1 voice). (If a
  future design wants qty-weighting, that's a separate decision; not now.)

### 1.8 Cook ceiling

The cook ceiling clamps how high a dish grade can resolve, as a function of cook
level relative to the recipe's unlock level. **Exact thresholds are a tuning
table for Wes/the sim** — this spec fixes the *shape*, not the numbers:

- At a recipe's unlock level, the ceiling is low (e.g. caps at Common-ish).
- As cook level climbs above the unlock level, the ceiling rises grade by grade
  until it reaches Pristine.
- This is what makes Pristine a *triple-gated* rare triumph: a Pristine hero +
  good supports + cook level high enough to not clamp it.

Provide the ceiling as a small, data-driven function or table so the sim and the
app share one source of truth. **Do not hardcode magic numbers inline.**

### 1.9 Scaling — additive, Crude = baseline

Grade scales **stat boosts only** (per §1.2), **additively**, with **Crude as the
zero-point baseline**:

- **Crude delivers the recipe's authored stat boost as written.** No penalty.
- Each grade above Crude adds a **flat additive step**. No multipliers.
- **Nothing is ever negative.** The worst outcome is "no bonus," never a loss.
  (Crude is the *most common* early result; the default experience must never be
  "your food is worse than the card says.")

```
finalStatBoost = authoredBoost + gradeStep(grade)
gradeStep(Crude)   = 0
gradeStep(Common)  = +s1
gradeStep(Fine)    = +s2
gradeStep(Superb)  = +s3
gradeStep(Pristine)= +s4
```

The step magnitudes `s1..s4` are a **tuning table for Wes/the sim**, not invented
here. Additive was chosen specifically because Pristine is rare — additive keeps
the reward **bounded and tunable** against the encounter curve, where
multiplicative would create an unbalanceable spike.

> **Draughts & poultices:** the same grade concept applies — grade scales the
> item's *primary magnitude* (draught potency, poultice healing) by the same
> additive, Crude-baseline rule. One concept, applied everywhere. This unifies
> food/draught/poultice and dissolves the old asymmetry. Magnitudes for non-food
> are likewise a tuning table.

---

## 2. Data & Migration Surface

Current DB version is **10**. This is migration **10 → 11**.

The crux: `inventory_items` and `prepared_food` are keyed on a **single ID**.
Adding grade means **composite primary keys** and stacks that **split by grade**.

### 2.1 `InventoryItem` (entity + table)

**File:** `data/db/InventoryItem.kt`

- Add `grade: Int` (ordinal 0..4).
- Primary key becomes **composite**: `(ingredientId, grade)`.
- Migration 10→11:
  - Add column `grade INTEGER NOT NULL DEFAULT 0` (existing stock becomes Crude).
  - Rebuild the table to set the composite PK (Room can't alter a PK in place —
    create new table, copy rows, drop old, rename). Provide the full
    `CREATE TABLE` / `INSERT … SELECT` / `DROP` / `ALTER … RENAME` sequence.

### 2.2 `InventoryDao`

**File:** `data/db/dao/InventoryDao.kt`

Every ID-keyed query gains a grade dimension:
- `get(id)` → `get(id, grade)`; add `getAllGradesOf(id): List<InventoryItem>`.
- `addQuantity` / `removeQuantity` → keyed on `(id, grade)`.
- `upsert` unchanged (composite PK handles conflict).
- `observeAll()` unchanged (UI groups by id, shows grade badges).
- Add a helper to total quantity across grades for an id (for "do I have enough
  of X at *any* grade" checks where grade is irrelevant).

### 2.3 `InventoryRepository`

**File:** `data/repository/InventoryRepository.kt`

- `addIngredient(id, qty)` → `addIngredient(id, grade, qty)`.
- `removeIngredient(id, qty)` → must decide **which grade to consume**. Default
  consumption policy: **lowest grade first** (spend Crude before Pristine) unless
  a specific grade is requested. This protects the player's high-grade stock from
  being silently burned on a recipe that doesn't care. Expose an overload that
  takes an explicit grade for the cook path (which *does* care).
- Add grade-aware lookups used by cook resolution.

### 2.4 `PreparedFood` (entity + table)

**File:** `data/db/PreparedFood.kt`

- Add `grade: Int` (the resolved dish grade from §1.7).
- Primary key becomes **composite**: `(recipeId, grade)`.
- Same migration pattern as 2.1 (rebuild for composite PK; existing prepared food
  defaults to Crude=0).

### 2.5 `PreparedFoodDao`

**File:** `data/db/dao/PreparedFoodDao.kt`

- `get(id)` → `get(id, grade)`.
- `addOne` / `removeOne` → keyed on `(id, grade)`.
- `observeAll()` unchanged (UI groups).

### 2.6 `Ingredient` model

**File:** `data/model/Ingredient.kt` (+ `ingredients.json`)

- Ingredients themselves are **not** authored with a grade — grade is rolled at
  gather, not a static property. **No change to the static grade of an
  ingredient.**
- **Hero flag lives on the recipe, not the ingredient** (see 2.7).

### 2.7 `Recipe` model + `recipes.json`

**File:** `data/model/Recipe.kt` (+ `recipes.json`)

- Add `heroIngredient: String` — the id of the recipe's hero ingredient (must be
  one of the ids in `ingredients[]`). Required for any cookable recipe.
- Data pass: assign a hero to **every** recipe in `recipes.json`. Choose the
  thematically central ingredient (the tater in a tater dish, the meat in a
  roast, the herb in a tea). This is authorial; if ambiguous, pick the
  highest-qty or most-named ingredient and flag for Wes review.
- Remove the dead `buffType` / `flavorTag` / `baseBuffStrength` /
  `buffStrengthPerLevel` derived helpers if they're no longer referenced after
  this work (verify with usage search first — do not break callers).

### 2.8 Gather workers — roll grade

**Files:** `worker/GatheringWorker.kt`, `worker/FarmWorker.kt`,
`worker/GardenWorker.kt`, `worker/HiveWorker.kt`, `worker/CoopWorker.kt`,
`worker/DairyWorker.kt` (any worker that *adds raw ingredients* to inventory).

- On completion, **roll one grade for the session** from the weighted
  distribution (§1.3), using gathering level to position the curve and the
  gathering-aid flag (if set for this session) to apply reliable uplift.
- Write the rolled grade into every ingredient added that session via the
  grade-aware `addIngredient(id, grade, qty)`.
- Centralize the distribution + roll in **one** shared place (e.g. a
  `QualityRoll` object) so all workers and the sim use identical logic. Do not
  duplicate the curve per-worker.

> **Process worker exception:** `ProcessWorker` must **preserve** grade — read the
> input ingredient's grade(s) consumed and write the *same* grade to the output.
> If inputs span multiple grades (because consumption pulled mixed stock), use
> the **hero-weighted-average rule is NOT used here** — process is 1:1
> transformation; carry the grade of the consumed input. If a process recipe
> consumes a single input type, that input's grade is the output grade. For
> multi-input process recipes, carry the **lowest** consumed grade (conservative;
> avoids free upgrades, consistent with "process never refines").

### 2.9 Cook path — resolve & store dish grade

**Files:** `ui/viewmodel/KitchenViewModel.kt`, `worker/CookingWorker.kt`

- At cook start, the player's selected ingredients have grades. Resolve the dish
  grade via §1.7 (hero-weighted average, clamped by cook ceiling §1.8).
- Consume the specific graded stock chosen (cook path uses explicit grades —
  see 2.3).
- Store the result as `PreparedFood(recipeId, grade, …)`.
- The cook ceiling function (§1.8) lives in a shared util mirrored in the sim.

### 2.10 Combat / mission read

**Files:** wherever food stat boosts feed mission/combat (search:
`primaryBoost`, `secondaryBoost`, `MissionWorker`, `EncounterWorker`, the V2
engine read).

- When a `PreparedFood` is consumed to provision a member, compute its effective
  stat boost via §1.9: `authoredBoost + gradeStep(grade)`.
- HP/s is unaffected by grade (Tier governs HP/s — §1.2). Only stat boosts scale.
- Mirror `gradeStep` and the cook-ceiling table into the sim
  (`tools/sim/food_model.js`) so sim and app agree. Where they disagree, the sim
  is the source of truth (per `combat-model.md`); reconcile.

---

## 3. UI Surface

- **Inventory / Pantry:** ingredient rows show a **grade badge** (Crude→Pristine).
  Stacks of the same ingredient at different grades display as separate rows or a
  grouped row with per-grade counts. Compact — Wes dislikes sprawl.
- **Kitchen / cook screen:** when selecting ingredients, the player picks **which
  grade** to use (default to lowest-grade-first to protect good stock; let them
  override). Show the **predicted dish grade** live as they choose, and show the
  ceiling clamp if it's binding ("Cook Lv caps this at Fine").
- **Recipe detail:** mark the **hero ingredient** visibly (it's the one that
  counts double).
- **Prepared food / provisioning:** food entries show their **grade** and the
  resulting effective stat boosts.
- **Gathering screen:** if a gathering aid is available/equipped for the next
  session, show it and its uplift effect.

Keep all of the above compact. Badges, not paragraphs.

---

## 4. Phased Build Order (for Claude Code)

Each phase ends with `./gradlew build` green and a `[hc]` commit. Do not start a
phase before the previous one builds.

**Phase 0 — Pillar reversal & parked-topics record**
- Edit `redefinition.md` (§0).
- Add the Process-upgrade and gathering-aid-source parks to
  `docs/parked_topics.md`.
- Commit. (No code.)

**Phase 1 — Quality core (no behavior change yet)**
- Add the `Grade` ordinal enum/constants (0..4) + name mapping.
- Add the shared `QualityRoll` (distribution + roll) and `cookCeiling` /
  `gradeStep` util stubs (tables filled with **placeholder** values flagged for
  Wes/sim tuning).
- Unit tests for roll bounds, hero-weighted average, ceiling clamp, additive
  scaling (Crude=0 baseline, no negatives).
- Commit.

**Phase 2 — DB migration 10→11**
- Composite PKs on `inventory_items` and `prepared_food`; the rebuild migration;
  schema export (bump to 11.json).
- DAO + repository grade-aware methods; lowest-grade-first consumption policy.
- Build, run existing tests, commit.

**Phase 3 — Gather rolls grade**
- Wire `QualityRoll` into all raw-adding workers; ProcessWorker preserves grade.
- Gathering-aid data shape + consumption hook (aid *source* stubbed).
- Tests for worker grade assignment + process preservation.
- Commit.

**Phase 4 — Recipe hero + cook resolution**
- Add `heroIngredient` to `Recipe` + data pass over `recipes.json` (every recipe
  gets a hero).
- KitchenViewModel/CookingWorker resolve dish grade (hero-weighted avg + ceiling)
  and store graded PreparedFood.
- Tests.
- Commit.

**Phase 5 — Combat/mission read**
- Effective stat boost = authored + gradeStep at provisioning time; HP/s
  untouched.
- Mirror `gradeStep` + ceiling into `tools/sim/food_model.js`; reconcile with sim.
- Commit.

**Phase 6 — UI**
- Grade badges (pantry), grade picker + live predicted-grade + ceiling hint
  (kitchen), hero marker (recipe detail), grade on prepared food, gathering-aid
  display.
- Commit.

---

## 5. Tuning Tables Owed to Wes / the Sim (not invented in this spec)

These are explicitly **not** set here. Claude Code uses flagged placeholders;
Wes/the sim set the real values:

1. The weighted gather distribution per gathering level (curve center + spread).
2. Gathering-aid uplift magnitude.
3. The cook-ceiling function (grade cap vs cook-level-over-unlock).
4. `gradeStep` additive magnitudes `s1..s4` for food stats.
5. `gradeStep` magnitudes for draught potency and poultice healing.

---

## 6. Open Threads (next session, not this build)

- **Gathering-aid recipe/acquisition** — what is the aid, how is it made/found.
  Feeds into the discovery session that follows.
- **Recipe discovery & crafting discovery** — the next session's topic; quality
  may interact (e.g. discovering a recipe vs discovering how to source its hero
  at grade). Keep the quality hooks clean so discovery can build on them.
- **Process-as-upgrade (Phase 2 deferred)** — the later-game refinement lever.
- **Qty-weighting at cook** — currently each ingredient is one voice regardless
  of qty; revisit only if a design reason appears.
