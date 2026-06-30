# HearthCraft — Audit June 2026 Design Spec

> Covers five items from the 28 JUN 2026 app audit. Two are major system designs
> (per-member provisioning + stat-based encounters; guided recipe discovery
> amendments). Three are targeted fixes (forage delay bug, XP tuning, seed
> drops, missing Will recipe, honey/beekeeping).

---

## 1. Per-Member Provisioning + Stat-Based Encounters

### What changes

The current single-food-for-the-whole-band model is replaced. Each member
is provisioned individually before every mission. Mission outcomes reflect the
collective stat profile of all four members, not a single aggregate buff
strength number.

### Mission send flow

Three steps, replacing the current one-step send:

1. Player taps "Send on Mission" on the Band screen.
2. Mission picker: Easy / Medium / Hard (same as today).
3. Provisioning screen: one row per member. Each row shows the member's name,
   role, and primary stat. If a food item is assigned, the row shows the food
   name and its stat boosts. If no food is assigned, the slot is empty — shown
   neutrally, not as a warning or penalty label. Player picks food for each
   member from pantry via a picker dialog. A member with no food assigned simply
   contributes their base stats only.
4. Confirm → food items consumed from pantry → mission starts.

Food is consumed at the moment of send, not on completion.

### Stat boosts on food

Each recipe in `recipes.json` carries explicit stat boost fields:

```json
{
  "primaryStat": "agi",
  "primaryBoost": 3,
  "secondaryStat": "mig",
  "secondaryBoost": 1
}
```

These are design data, not derived from a formula. Higher-level recipes have
higher boost values by authorial choice. Cook level gates which recipes the
player can make; it does not modify the output stats. If a recipe has no stat
focus (antidote/healing recipes), both fields are null.

### Member base stats

Each member in `band_members.json` gains explicit base stat fields mirroring
the sim's role templates (`tools/sim/run_sim.js` TPL):

```json
{
  "baseStats": { "mig": 13, "agi": 6, "vit": 15, "wil": 7, "fat": 6 },
  "statGrowth": { "mig": 0.50, "agi": 0.15, "vit": 0.55, "wil": 0.20, "fat": 0.15 }
}
```

Effective stat at band level L = `baseStat + statGrowth × (L - 1)`.

Role stat profiles (matching the sim):

| Role    | Primary stat  | Growth lean |
|---------|--------------|-------------|
| Warden  | Might + Vit   | mig 0.50, vit 0.55 |
| Hunter  | Agility       | agi 0.55, mig 0.30 |
| Keeper  | Will          | wil 0.55, fat 0.45 |
| Captain | Will + Might  | wil 0.45, fat 0.45 |

### Encounter calculation in MissionWorker

V1 uses a simplified (non-tick) combat model. The full tick simulation
(HP/s, spike damage, cascade, hazards) is deferred to V2.

**Effective stats per member:**
```
effectiveStat = baseStat(level) + foodPrimaryBoost (if food.primaryStat == stat)
                                + foodSecondaryBoost (if food.secondaryStat == stat)
```

**Member contribution (from `run_sim.js` `dpsBreakdown()`):**

| Role    | Formula |
|---------|---------|
| Warden  | `mig × 0.5` |
| Hunter  | `agi + mig × 0.4` |
| Keeper  | `wil × 0.9` |
| Captain | `mig × 0.3 + wil × 0.2` |

These are the exact formulas from the sim. Do not adjust them here — if
balance needs changing, change the sim first, then mirror to the Android
build.

**Party power:**
```
partyPower = Σ(memberContribution) × (aliveMemberCount / totalMemberCount)
```

**Mission outcome:**

Missions in `missions.json` replace `buffStrength` with `powerThreshold`.
Success probability is a linear ramp:

```
ratio = partyPower / mission.powerThreshold
successChance = clamp(ratio, 0.05, 0.95)   // never certain, never impossible
```

A random roll determines the final outcome. This gives the player meaningful
feedback: underfeed the Keeper and the odds visibly drop; overprovision and
they climb toward the cap.

**Member loss condition** stays the same: `ratio < 0.60` AND random roll < 0.33.

### Data model changes

- `Recipe`: add `primaryStat: String?`, `primaryBoost: Int`, `secondaryStat: String?`,
  `secondaryBoost: Int`
- `BandMember` / `band_members.json`: add `baseStats`, `statGrowth`
- `Mission` / `missions.json`: replace `buffStrength` with `powerThreshold: Float`
- `MissionSession`: add `memberFoodIds: Map<String, String>` (memberId → foodId)
  so the worker knows what each member ate
- Room migration required (new MissionSession field)

### What food for the wrong role does

No artificial penalty. A Will food given to the Hunter adds its `primaryBoost`
to the Hunter's Will. The Hunter's contribution formula weights Will at 0.0, so
it contributes nothing to party power. The design handles it — no extra code
needed.

---

## 2. Recipe Discovery — Amendments to 2026-06-27 Spec

The existing spec (`2026-06-27-recipe-discovery-design.md`) is correct on
engine design, proximity tiers, KitchenViewModel structure, and auto-discover
on level-up. Two things change from today's session:

### 2a. Free assembly phase (replaces "always consume")

The existing spec says: *"Ingredient deduction always happens — there is no
free experiment."* This is replaced.

The Experiment tab now works in two phases:

**Phase 1 — Assemble (free):** Player slots ingredients and picks a method.
As they build the combination, live proximity feedback updates in real time
using `RecipeDiscoveryEngine.evaluate()`. No ingredients are spent. The player
can swap freely. Proximity tiers display as:

- `NONE` — "Nothing here."
- `SOME` — "Something about this feels promising."
- `CLOSE` — "You're close to something real."
- `NEAR_MISS` — "Almost — just out of reach."

**Phase 2 — Commit (costs ingredients):** If the combination is an exact match
for an undiscovered recipe, a "Cook it" button activates. Tapping it:
- Spends the ingredients
- Discovers the recipe
- Produces the food item
- Shows a discovery card

If the combination is not a match, the "Cook it" button stays disabled. No
ingredients are ever spent on a failed experiment.

`submitExperiment()` in `KitchenViewModel` is called only when the match is
confirmed. The live proximity evaluation is a separate `evaluateLive()` call
that reads inventory without modifying it.

### 2b. Starter pantry + tutorial flow

**Starter pantry:** On first launch (after band selection, before any play),
the player's inventory is seeded with 4–5 band-appropriate ingredients. These
are chosen so that at least one discoverable recipe per member role is
reachable on day one without foraging first. The seed set is defined per band in a new `starter_inventory.json` asset
under `app/src/main/assets/data/`. Format: `{ "bandId": ["ingredient_id", ...] }`.
Seeding runs once on first launch, gated by a `hasReceivedStarterPantry`
boolean added to `PlayerState`.

**Tutorial flow (one-time, non-blocking):**

1. On first Kitchen visit: a dismissible card at the top of the Experiment tab
   reads: "Combine ingredients with a cooking method. Find the right combination
   to discover a recipe. Assembly is free — you only spend ingredients when
   you've found something."
2. After the first forage session completes: a one-time notification or home
   screen nudge reads: "You've gathered ingredients. Head to the Kitchen and
   see what you can make."

Both flags stored as booleans in `PlayerState`:
- `hasSeenExperimentHint: Boolean`
- `hasSeenPostForageNudge: Boolean`

Room migration required for both new fields.

---

## 3. Targeted Fixes

### 3a. Forage delay bug

**Symptom:** After the forage timer reaches 0:00, there is a gap before the
"Collect Forage" button appears. The gap also occurs after reopening the app
with a completed session.

**Cause:** The `ActiveTimerCard` UI counts down locally. When it hits zero,
the UI is showing `forageSession != null && pendingResultJson == null` — the
"in progress" state. The WorkManager job fires slightly after the calculated
end time (scheduler jitter). During that window, neither the timer card nor
the collect button shows — the UI falls into the `else` branch (Start button).

**Fix:** Add a third UI state: `session exists AND timer ≤ 0 AND
pendingResultJson == null` → show a "Finishing up…" card (spinner, no
button). Once `pendingResultJson` is set by the worker, the collect button
replaces it normally. The "Start Foraging" button must not appear while a
session row exists in the DB, regardless of timer state.

### 3b. XP too fast

**Symptom:** Gathering level 5 reached after 1–2 sessions.

**Cause:** `XP_GATHER_SESSION = 90`. Level 1→5 requires ~299 XP. With
discovery bonuses (5 new ingredients × 20 XP = 100), a single session can
yield 190 XP, reaching level 5 in 2 sessions.

**Fix:** Reduce `XP_GATHER_SESSION` from `90` to `30`. With discovery
bonuses, a first session yields ~130 XP (30 + 5×20), reaching level 3.
Level 5 takes roughly 4–5 sessions, which is a day of normal play.

Tune in `PlayerRepository`:
```kotlin
const val XP_GATHER_SESSION = 30   // was 90
```

No curve change needed.

### 3c. Seed drops from foraging

**Symptom:** Seeds not appearing reliably from forage sessions.

**Current state:** Farm and Garden workers already guarantee 1–2 seeds per
harvest (working correctly). Forage has a 25% seed drop chance — unreliable
by design, but too low for a functional seed economy.

**Fix:** Raise forage seed drop chance to 50% and guarantee at least one seed
drop per session (remove the roll entirely, always drop 1 seed of a random
plantable ingredient):

```kotlin
// GatheringWorker — replace the probability roll with a guaranteed drop
val plantable = gameData.ingredients.filter { ... }
plantable.randomOrNull()?.let { ingredient ->
    val seedCount = 1 + Random.nextInt(2)
    harvestItems.add(HarvestItem(...))
}
```

This removes `SEED_DROP_CHANCE` constant entirely.

### 3d. Missing Will recipe for Greycloaks

**Symptom:** No Will-boosting recipe available at cook level 1 for the
Greycloaks band.

The Greycloaks Keeper is Cael (herbal preference, Will primary). There is no
level-1 recipe with `primaryStat: "wil"` appropriate to Bree-land ingredients.

**Fix:** `Contemplative Tea` exists in both `food_model.js` and `recipes.json`
with `levelRequired: 1` and `primaryStat: "wil"`. Add `primaryBoost` and
`secondaryBoost` values to its `recipes.json` entry consistent with other
level-1 recipes. Verify its ingredient list uses Bree-land / Eriador
ingredients so Greycloaks players can experiment into it from the starter
pantry.

### 3e. Honey and beekeeping

**Symptom:** Honey appears as a gardenable ingredient, which is thematically
wrong.

**Fix — data:** Remove honey from the farm/garden ingredient pool
(`gatheringMode` set to `"hive"` or removed from the plantable set).

**Fix — beekeeping system (V1 scope, minimal):**

A new "Hive" section in the Gathering screen, below Garden. Unlocks at
Gathering level 8.

- One hive slot in V1
- Player starts a hive session (no seed/planting required — the hive is
  always there once unlocked)
- Timer: 10 minutes (increases in V2 as hive quality and honey rarity improve)
- On completion: 2–4 honey (common drop only — royal jelly deferred to V2)
- Collected via the same `GrowingRepository` / slot pattern as Farm/Garden,
  using slot id `"hive_0"`
- New `HiveWorker` (mirrors `FarmWorker` without the seed input)
- No upgrades in V1 — the hive is a fixed slot with fixed yield

Room migration: add hive slot support to `GrowingSlot` (the `type` field
already exists — `"hive"` is a new valid value, no schema change needed if
the column is already a string).

---

## DB Migration Summary

This work requires a Room schema migration. Collect all field additions into
a single migration version bump:

| Table | Change |
|-------|--------|
| `PlayerState` | Add `hasSeenExperimentHint INTEGER NOT NULL DEFAULT 0` |
| `PlayerState` | Add `hasSeenPostForageNudge INTEGER NOT NULL DEFAULT 0` |
| `MissionSession` | Add `memberFoodJson TEXT NOT NULL DEFAULT ''` (serialised map) |

Use `@AutoMigration` where no data transform is needed. The MissionSession
change may need a manual migration if the table structure requires it.

---

## Implementation Order

1. **Data layer first** — add stat fields to `recipes.json` and
   `band_members.json`; Room migration; `MissionSession.memberFoodJson`
2. **Encounter model** — update `MissionWorker` stat calculation; remove old
   `buffStrength` path
3. **Provisioning screen** — new Composable, `BandViewModel` changes
4. **Recipe discovery amendments** — free assembly phase in
   `KitchenViewModel`; live evaluate; starter pantry seeding; tutorial flags
5. **Targeted fixes** — forage delay, XP constant, seed drop, Will recipe,
   beekeeping

Each step is independently committable. Do not bundle steps.
