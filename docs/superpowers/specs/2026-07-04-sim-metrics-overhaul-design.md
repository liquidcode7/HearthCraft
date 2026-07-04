# Combat Sim Metrics Overhaul — Design

**Date:** 2026-07-04
**Status:** Approved

---

## 1. Overview

`tools/sim/run_sim.js` (headless batch simulator) and `tools/sim/hearthcraft_fight_sim.html`
(browser fight simulator) already mirror the same combat math as the production
`EncounterEngine.kt` — streak triggers, HoT/Triage/Group/Rescue healing, the four
per-role Inspirations. Both currently report only coarse aggregates: total party
DPS, whether each Inspiration fired, wound counts. Neither tracks per-member DPS,
healing broken out by source, streak trigger counts/damage, or how much each
Inspiration actually contributed.

This pass adds that instrumentation to both tools. **No change to production
Kotlin code or to the actual simulation math** — this only observes and records
values that already exist inside the tick loop; fight outcomes (win rates,
wound counts, Inspiration fire rates) must be identical before and after.

## 2. New Metrics (shared concept, both tools)

- **Per-member DPS** — running damage total per member, not just party aggregate
- **Healing broken out by source** — separate running totals for HoT, Triage,
  Group Heal, Rescue Burst (today only individual per-event log lines exist,
  nothing is summed)
- **HoT uptime/potency** — fraction of fight duration with at least one HoT
  active, and average heal-per-tick delivered
- **Fate streak activity** — trigger count per member, plus the *bonus* damage/
  healing attributable to the streak's 1.5× multiplier (the portion above what
  would have happened at 1×) — not tracked at all today, only text-logged
- **Inspiration contribution** — beyond fire/no-fire (already tracked): Red
  Dawn's total heal delivered + bonus DPS from its window, Black Arrow's
  resolve/clock burned, Grace's HP restored, Horn's damage redirected/absorbed

## 3. `run_sim.js` Changes

Add a new report section, in the same style as the existing "Averages (per
fight)" and "Inspiration fire rates" blocks, averaged across all N runs:
- "DPS by member" (avg per fight)
- "Healing by source" (avg HoT / Triage / Group / Rescue per fight)
- "Streak activity" (avg triggers/fight per member, avg streak-attributable
  bonus damage/fight)
- "Inspiration contribution" (avg amount delivered per type, for fights where
  it fired)

## 4. `hearthcraft_fight_sim.html` Changes

Add new panels using the existing chart helpers (`meterChart`/`lineChart`/
`pieChart`) already used for DPS-per-character, wounds-over-time, etc.:
- A healing-by-source meter chart (parallel to the existing Keeper-healing meter)
- A streak-activity meter chart (count + bonus damage per member)
- Per-Inspiration contribution values added to the existing Inspiration timeline

Also renames the stale "Hunter" role label to "Fighter" throughout this file
(CSS classes, JS variable names, display strings) to match the current design
and `run_sim.js`, which already uses "Fighter." Character names (e.g. "Mira")
are already correct and unaffected — only the role label is stale.

## 5. Verification

Neither file has automated tests today (plain scripts, not part of the JVM/JS
test suites) — existing convention is manual runs. Verification for this pass:
run `run_sim.js` with a fixed seed/config before and after, confirm existing
numbers (win rate, wound counts, Inspiration fire rates) are byte-identical
(proving the new instrumentation doesn't alter simulation behavior), and
sanity-check new numbers (e.g., summed per-member DPS ≈ existing party-total
DPS already reported).

## 6. Out of Scope

- Any change to `EncounterEngine.kt` or other production Kotlin
- Any change to actual combat balance/behavior — this is observation only
