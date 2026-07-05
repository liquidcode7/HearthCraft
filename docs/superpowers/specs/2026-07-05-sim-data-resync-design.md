# JS Sim Toolkit Data Resync — Design

**Date:** 2026-07-05
**Status:** Approved

---

## 1. Overview

`tools/sim/run_sim.js` (headless) and `tools/sim/hearthcraft_fight_sim.html`
(browser) were both built from a "legacy-scale" universe that predates
several changes already shipped in production:

- A 3x combat-coefficient rescale (`EncounterEngine.kt`, commit `4d39231`,
  "Rescale combat coefficients and add the Inspiration system").
- A compound stat-growth formula (`CombatLeveling.kt`'s `statAtLevel`),
  replacing flat-linear growth.
- The current Greycloaks roster (`band_members.json`) — the sim's Warden
  and Captain (Borin Ironmantle, Aelindra) now belong to other bands
  (Undermarch, Mithlost); the real current Greycloaks Warden and Captain
  are Hollis and Aldric.
- The real recipe catalog and grade-multiplier system
  (`recipes.json` + `QualityUtils.kt`'s `gradeMultiplier`), replacing an
  obsolete "familiarity level" food-progression mechanic that predates
  Model B and doesn't exist anywhere in the current design.
- Real, already-validated encounter tuning (`encounters.json`), which the
  encounter-builder spreadsheet's rows never picked up — its "validated"
  rows still cite the old HP/s food model directly in their notes (e.g.
  `CL1(5.0 HP/s)=23%`), and its "placeholder" rows were never tuned against
  the corrected coefficients at all.

This pass resyncs both JS tools to match every one of those sources
exactly, fixes the encounter-builder spreadsheet (legacy warning + real
numbers for the three most-played early encounters), and adds a small
manual drift-check script so this kind of divergence gets caught fast next
time instead of silently compounding for weeks.

This is a one-way sync: the JS tools conform to the shipped Kotlin/JSON
data, never the reverse. No production code changes.

## 2. Combat Math Resync

Applied identically to `run_sim.js` and the browser sim's inlined engine
(`createFightState`/`fightTick`/`fightDpsBreakdown`) — both files currently
share the same constants, so both need the same edits.

**Damage coefficients** — sync to `EncounterEngine.kt`'s `rawDps()`:

| Role | Old (JS tools) | New (matches EncounterEngine.kt) |
|------|----------------|-----------------------------------|
| Warden | `mig × 0.5` | `mig × 1.5` |
| Fighter | `agi + mig × 0.4` | `agi × 3 + mig × 1.2` |
| Keeper | `wil × 0.9` | `wil × 2.7` |
| Captain | `mig × 0.3 + wil × 0.2` | `mig × 0.9 + wil × 0.6` |

The Captain's physical/magic split (added during the browser-sim-resync
branch for the "damage dealt by type" pie chart) keeps the same 60/40
mig-to-wil ratio, now applied to the new coefficients (`mig × 0.9` /
`wil × 0.6`).

**Group Heal** — sync to `EncounterEngine.kt`:

| Constant | Old (JS tools) | New |
|----------|----------------|-----|
| `GROUP_HEAL_IV` | 20 | 5 |
| `GROUP_HEAL_MUL` | 0.5 | 1.5 |

Every other Streak/HoT/Triage constant (`STREAK_K`, `STREAK_REFRACTORY`,
`STREAK_DURATION`, `STREAK_MULT`, `HOT_DURATION`, `HOT_HEAL_MUL`,
`TRIAGE_HP`, `TRIAGE_MUL`) already matches `EncounterEngine.kt` exactly —
confirmed by direct comparison, no changes needed there.

**Stat growth formula** — replace linear with compound, matching
`CombatLeveling.kt`:

```
OLD: stat(level) = start + growthRate × (level − 1)
NEW: stat(level) = start × (1 + growthRate) ^ (level − 1)
```

**Growth rates per role** — sync to `growth_curves.json` (primary stat
3.5%/level, single secondary 2.0%/level, all other stats 1.0%/level;
Captain's two secondaries — Fate and Vitality — each at 1.0%/level so
their combined budget matches a single-secondary role's 2.0%):

| Role | mig | agi | vit | wil | fat |
|------|-----|-----|-----|-----|-----|
| Warden | 0.02 | 0.01 | 0.035 | 0.01 | 0.01 |
| Keeper | 0.01 | 0.01 | 0.02 | 0.035 | 0.01 |
| Captain | 0.01 | 0.01 | 0.01 | 0.035 | 0.01 |
| Fighter (ranged) | 0.01 | 0.035 | 0.01 | 0.01 | 0.02 |
| Fighter (melee) | 0.035 | 0.01 | 0.01 | 0.01 | 0.02 |

## 3. Roster & Character Resync

Sync both JS tools' character data to `band_members.json`'s current
Greycloaks lineup:

| Role | Old (JS tools) | New | Starting stats (mig/agi/vit/wil/fat) |
|------|----------------|-----|-----------------------------------------|
| Warden | Borin Ironmantle | **Hollis** | 4/2/5/3/2 |
| Fighter | Mira | Mira (unchanged) | 3/5/3/4/4 |
| Keeper | Cael | Cael (unchanged) | 2/3/3/5/4 |
| Captain | Aelindra | **Aldric** | 3/2/4/5/4 |

Starting stats drop from the old 13–15 range to the real 2–5 range —
this is the other half of the coefficient rescale: small stats × big
coefficients now, not big stats × small coefficients.

The browser sim's Company tab needs new bio/mechanic/Inspiration text for
the Hollis and Aldric cards — **Wes is writing this text**, not part of
this spec or its implementation plan. The plan should leave clearly
marked placeholders for this content rather than inventing it.

Worth noting for context: the sim's existing Warden/Captain bios turn out
to already be the *real* Borin Ironmantle's (Undermarch) and Aelindra's
(Mithlost) personality text, near-verbatim from `band_members.json` —
simply mis-slotted into the Greycloaks cards at some point. Mira's
existing bio already says "Has known Aldric since she was small," meaning
the sim's own text has been assuming Aldric as Captain all along,
contradicted only by the card label.

Mechanical name-reference updates (not new prose — just swapping an
existing name to match the new roster) still belong in this pass:
- Mira's `char-mech`: "She carries none of Borin's durability" → "She carries none of Hollis's durability"
- Cael's `char-mech`: "Borin guards him from killing blows. That arrangement is not accidental." → "Hollis guards him from killing blows. That arrangement is not accidental."

## 4. Food Model Resync

Applied identically to both JS tools' food-model data.

**Remove** the obsolete "familiarity level" progression entirely:
`flFromCookLevel`, the FL-based scaling in `statBonusesAt`, and the
leftover HP/s `TIER_TABLE`/`recipeLevelToHps`/`SCURVE_P` machinery (two
separate copies of this exist today — one inside the `FOOD_MODEL` IIFE,
one at top level — both go).

**Recipe list** replaced with the 14 real Greycloaks food-class recipes
from `app/src/main/assets/data/recipes.json` (`band: "greycloaks"`,
`class: "food"`): id, name, cookLevel, tier, primaryStat + primaryBoost,
secondaryStat + secondaryBoost, copied verbatim. This drops:
- `contemplative_tea` and `moonpetal_tisane` — these are **draughts** in
  production (band Mithlost, no stat boost at all), not food; the sim
  already has equivalent effects via its existing draught sliders
  (Potency/Warmth/Hale/Alert/Radiance/Hope).
- `iron_stew`, `stormcap_saute`, `athelas_infusion` — no production
  counterpart exists for these at all; they were invented at some earlier
  point and never removed.

**Grade multiplier** added: a per-member grade selector (Crude / Common /
Fine / Superb / Pristine), multiplying the recipe's authored boost by
`0.7 / 0.85 / 1.0 / 1.3 / 1.7` respectively — matching
`QualityUtils.kt`'s `GRADE_MULTIPLIER` array exactly. Final formula,
matching `BandRepository.kt`'s `bonus()`:

```
finalBonus(stat) = matchingBoost(stat) × gradeMultiplier(grade)
memberStat(stat) = leveledStat(stat) + finalBonus(stat)
```

No bonus ever applies to Fate — matching the hard "no Fate food" rule.
Cook-level gating is simple availability: a recipe is selectable once the
Setup tab's cook-level slider meets its `cookLevel` value, no familiarity
scaling on top.

## 5. Encounter Builder Spreadsheet

`tools/sim/HearthCraft_Encounter_Builder.xlsx` (last edited 2026-06-23,
predates the 30-Jun-2026 redesign entirely).

- **Legacy warning**: add a clear notice to the "Sim Encounters" sheet
  header and the "Design Notes" sheet, stating that any row not resynced
  in this pass predates the Model B redesign and the coefficient rescale,
  and its resolve/drain/spike numbers should not be trusted as current
  until re-validated.
- **Resync the first three rows** (the most-played early encounters) to
  the real, currently-shipped `stages[0]` parameters from
  `app/src/main/assets/data/encounters.json`:

  | Row (xlsx id) | Real encounter id | resolve | drain | spike | spikeIv | physMitPct |
  |---|---|---|---|---|---|---|
  | `neekerbreekers_midgewater` | `greycloaks_neekerbreekers` | 40000 | 4 | 75 | 13 | 0 |
  | `wolves_chetwood` | `greycloaks_wolves` | 60000 | 3 | 75 | 9 | 0 |
  | `goblin_scouts` | `greycloaks_goblins` | 68000 | 7 | 60 | 14 | 35 |

  Each row's stale `winCurve` note (the ones citing "HP/s") gets cleared
  and replaced with a freshly generated win-rate curve, produced by running
  the corrected `run_sim.js` (this step depends on §2 being complete
  first) — same validation method the "validated" rows already used, just
  against corrected math.
- The other 10 rows get the legacy-warning flag but are **not** re-tuned
  in this pass — re-tuning them is real balance work (no source of truth
  to copy from, unlike the three above) and is explicitly deferred.

## 6. Drift-Check Script

A new, uncommitted-to-CI Node script, `tools/sim/check_drift.js`, run
manually on demand:

- Reads the real source files: `EncounterEngine.kt` (regex-extracts the
  `rawDps()` coefficients and the Streak/HoT/Triage/GroupHeal constant
  block), `growth_curves.json`, `band_members.json` (Greycloaks band
  only), `recipes.json` (Greycloaks food-class only), and
  `QualityUtils.kt`'s `GRADE_MULTIPLIER` array.
- Extracts the equivalent hardcoded constants from `run_sim.js` and the
  inlined blocks in `hearthcraft_fight_sim.html` (same script-extraction
  technique already used for syntax-checking).
- Prints a clear mismatch report: which constant, which file, old vs new
  value — nothing more elaborate than that. Exit code reflects pass/fail
  so it can be scripted into a pre-check habit later if desired, but
  nothing wires it in automatically as part of this pass.

## 7. Verification

- Node-based syntax-check on the browser sim (same technique as the
  browser-sim-resync branch: extract inline `<script>` blocks, run
  through `new Function()`).
- Win-rate comparison between `run_sim.js` and the browser sim before/after
  the resync, confirming they still agree with each other post-fix (same
  method as before).
- **New this pass**: sanity-check the corrected `run_sim.js` against real
  production data — run it with `greycloaks_goblins`'s actual parameters
  and roster, and confirm the win-rate trend across grades is monotonic
  and roughly consistent with `BalanceHarness.kt`'s
  `` `goblins win rate increases monotonically from Crude to Pristine at
  level 5` `` test expectations. This is the first time either JS tool's
  output gets checked against the real game rather than only against
  itself.
- Run `tools/sim/check_drift.js` at the end and confirm it reports clean.

## 8. Out of Scope

- Re-tuning the other 10 encounters in the spreadsheet — real balance
  work, no source of truth to sync from, deferred to a future pass.
- Any change to production Kotlin, JSON data files, or `EncounterEngine.kt`
  itself. This is a one-way sync.
- Mithlost/Undermarch (Elf/Dwarf) band data in the sim — it stays
  Greycloaks-only, matching its current scope.
- Draught mechanics beyond what the sim's existing sliders already cover.
- Wiring `check_drift.js` into CI or any automated build step.
