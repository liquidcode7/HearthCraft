# Browser Sim Resync + Metrics Overhaul — Design

**Date:** 2026-07-04
**Status:** Approved

---

## 1. Overview

`tools/sim/hearthcraft_fight_sim.html` (the browser fight simulator — Setup/Live/
Results/Batch/Company tabs) predates the Model B combat redesign in a way that
goes deeper than cosmetics. Its actual simulation math has drifted from
`run_sim.js` (the headless batch tool, already correct and already updated
this session with the new metrics — see
`docs/superpowers/specs/2026-07-04-sim-metrics-overhaul-design.md`) and from
the production `EncounterEngine.kt`. Specifically:

- **Two separate, hand-duplicated fight engines exist in this one file**:
  `stepOnce()` (drives the Live tab, one tick at a time) and `batchRunOne()`
  (a second, independently-written copy that drives the Batch/Monte-Carlo
  tab). They already disagree with each other in places (e.g. `batchRunOne`'s
  `rawDps()` has no Captain hybrid physical/magic split that `dpsBreakdown()`
  has).
- **Food still heals HP/s** (`foodOf(k)` applies steady healing every tick) —
  contradicts the hard rule "Food provides stat boosts only. Food does not
  provide HP/s. Ever." (master-design.md §6). Stat bonuses are *also* applied
  on top, so today's file does both at once — not a coherent version of
  either model.
- **Spikes default to independent per-member rolls with a damage-absorbing
  reserve pool** — in `run_sim.js` these are the same mechanics, but gated
  behind opt-in `--decouple`/`--sink` research flags, not the default
  behavior. The real default is one global spike per interval, no reserve pool.
- **The Streak system does not exist anywhere in this file.**
- **HoT / Triage / Group Heal do not exist as distinct Keeper actions** — only
  steady food-HP/s plus the existing rescue burst.

This pass resyncs the simulation math to match `run_sim.js` exactly (which
itself mirrors `EncounterEngine.kt`), consolidates the two duplicate engines
into one shared function, renames the stale "Hunter" role label to "Fighter,"
and adds the same metrics dashboard already built for `run_sim.js` this
session.

## 2. Consolidate the Duplicate Engines

Extract one shared fight-resolution function — mirroring `run_sim.js`'s
`runFight()` structure — that both the Live tab and the Batch tab call.

- The Live tab needs per-tick state to drive its DOM updates (bars, log lines,
  gauges) — the shared function takes an optional per-tick callback (or
  returns a tick-by-tick trace the caller iterates) rather than the Batch tab
  reimplementing its own copy of the same logic headlessly.
- The Batch tab needs speed (many fights in a tight loop, no DOM) — calling
  the shared function without the per-tick callback must not do any DOM work
  or incur its cost.
- Both tabs currently have independent `dpsBreakdown()`/`rawDps()`
  implementations; these collapse into one.

## 3. Math Resync (must match `run_sim.js` exactly)

- **Remove HP/s food healing entirely.** `foodOf()` and the steady per-tick
  food-heal block are deleted. Food remains stat-boosts-only via
  `memberFoodBonuses()` (already correct, unchanged).
- **Global spike only.** Remove the default per-member independent spike
  rolls and the default reserve-pool absorption (`M[k].reserve`,
  `RMAX`/sink-mode code). One spike per interval (`spikeiv`, ±50% jitter),
  hitting one random standing member (or redirected to the Warden during a
  Horn of Gondor window), damage ±30% jitter — same as `run_sim.js`'s default
  (non-`--decouple`, non-`--sink`) path.
- **Add the Streak system**, ported from `run_sim.js`: `STREAK_K = 0.002`,
  `STREAK_REFRACTORY = 20`, `STREAK_DURATION = 5`, `STREAK_MULT = 1.5`. Same
  per-member trigger/refractory/active state machine, same 1.5× multiplier
  applied to that member's damage or (for the Keeper) healing while active.
- **Add HoT / Triage / Group Heal**, ported from `run_sim.js`: `HOT_DURATION =
  8`, `HOT_HEAL_MUL = 0.15`, `TRIAGE_HP = 0.25`, `TRIAGE_MUL = 2.0`,
  `TRIAGE_COOLDOWN = 2`, `GROUP_HEAL_IV = 20`, `GROUP_HEAL_MUL = 0.5`. Same
  priority order the Keeper's action picks each tick: group heal (on its
  interval) → triage (lowest-HP member below threshold) → HoT application
  (free slot, lowest-HP member) → otherwise DPS. Rescue burst (existing
  mechanic) stays as-is, unchanged.
- All four Inspirations (Horn, Red Dawn, Grace, Black Arrow) already exist in
  this file in roughly the right shape — verify each against `run_sim.js`'s
  version during implementation and correct any divergence found (e.g.
  trigger-rate formula, effect magnitude), but these are not a ground-up
  rebuild like Streak/HoT/Triage/Group Heal are.

## 4. Metrics Dashboard

Same metric set as the `run_sim.js` pass, applied here:

- **Existing panels** (DPS per character, Keeper healing, damage-by-type pie,
  armor mitigation, Dread mitigation, wounds-over-time, Inspiration timeline,
  Inspiration trigger-rate meters) get updated to read from the new,
  resynced engine — same visual slots, correct underlying data, no new panel
  needed since the shape already exists.
- **New panels** for what has no current home: healing-by-source breakdown
  (HoT / Triage / Group / Rescue, parallel to the existing Keeper-healing
  meter), streak activity (trigger count + bonus damage/healing per member),
  Inspiration contribution amounts (not just fire/no-fire — how much each one
  actually delivered, matching the "Inspiration contribution" section already
  built into `run_sim.js`'s report).
- Same chart helpers already in this file (`meterChart`/`lineChart`/
  `pieChart`) render the new panels — no new charting infrastructure needed.

## 5. Rename Hunter → Fighter

Mechanical rename throughout this file (CSS classes `.hdr-hunter`/
`.role-hunter`, JS keys `hunterA`/`hunterM`, `ORDER` array, DOM ids like
`g_dps_hunter`, display strings "Hunter" in the Company tab and setup
controls). Character names (Mira, etc.) are already correct and unaffected —
only the role label changes, matching `run_sim.js` and the current design.

## 6. Verification

No browser/emulator is available in this environment, so this needs a
different verification approach than "run it and look":

- Port the same fixed scenarios `EncounterEngineTest.kt`/`run_sim.js` already
  use as known-good references (e.g. the "easy" config: resolve 15000, drain
  2, spike 30, spikeIv 20; the "brutal" config: resolve 200000, drain 120,
  spike 300, spikeIv 5) into the new shared engine's headless path (the same
  path the Batch tab uses) and confirm win rates land in the same range
  `run_sim.js` produces for equivalent inputs — the strongest check available
  without a browser.
- Actual visual rendering (chart layout, Live tab tick animation, DOM
  updates) needs your manual on-device/browser check afterward — same
  limitation as every UI task this session.

## 7. Out of Scope

- Any change to `EncounterEngine.kt` or other production Kotlin (per the
  earlier decision on the `run_sim.js` metrics pass — this stays confined to
  the offline design/balance toolkit)
- Any change to actual combat balance beyond what's needed to match
  `run_sim.js`'s already-correct model — this is a resync, not a rebalance
- Redesigning the Batch tab's sweep/inspection UI beyond making its
  underlying numbers correct
