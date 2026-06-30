# Dread Redesign — Two-Layer Floor + Peak Model
**Date:** 2026-06-27
**Status:** Approved — ready for implementation

---

## What This Spec Covers

A full redesign of the dread mechanic. The current model collapses dread and armor onto the same axis (both are `(1 - x)` output multipliers). This spec replaces it with a two-layer model where:

- **Layer A** is a persistent floor drag — always present, never fully removed
- **Layer B** is action denial — two severity tiers that only fire when the party is insufficiently prepared

---

## The Problem With the Current Model

```js
const physAfter  = physMit × (1 - min(1, potency/PEN_SCALE));
const dreadAfter = dread × (1 - min(1, willCut + hopeCut));
const eff = raw × (1 - physAfter) × (1 - dreadAfter);
```

Armor and dread are both `(1 - x)` multipliers on the same output number. A fight with 40% armor + 40% dread is mechanically identical to a single 64% output reduction. The player cannot tell them apart; the designer cannot tune them independently. They are two names for one problem.

Additionally, at `willCut + hopeCut >= 1`, dread effect drops to zero entirely. A counter that fully nullifies a debuff is a design failure — the same lesson already applied to Shadow (Model C).

---

## Design Goals

- Armor stays on the kill clock (output multiplier)
- Dread operates on a **different axis**: a floor drag (Layer A) and action denial (Layer B)
- Dread is never fully nullified — Layer A floor always persists
- Partial preparation meaningfully helps — pushing above T_PERM removes the worst effects before it eliminates all of them
- Two independent tuning dials: Layer A is the smooth difficulty knob; Layer B is the terror/texture knob

---

## Layer A — Floor Drag

A small, continuous output penalty. Two additive terms:

```
layerADrag = (effectiveDread × VAR_COEF) + (rawDread × FLOOR_COEF)
```

- **`effectiveDread × VAR_COEF`** — suppressed by Hope + Captain Will. Approaches zero as negation improves.
- **`rawDread × FLOOR_COEF`** — tied to raw encounter dread, cannot be removed by any counter. The faint weight that high-dread encounters always carry.
- Applied as a separate multiplier from `physAfter` — dread and armor must be distinct code paths.
- `VAR_COEF` and `FLOOR_COEF` are constants found by sim validation (see Validation section).

**Applied in the DPS formula:**

```
layerADrag = min(1, (effectiveDread × VAR_COEF) + (rawDread × FLOOR_COEF))
eff = raw × (1 - physAfter) × (1 - layerADrag)
```

---

## Layer B — Action Denial

Only active when `willCut + hopeCut` is below a threshold. Two severity gates:

### Gate 1 — Temporary Incapacitation

- **Condition:** `willCut + hopeCut < T_TEMP` (placeholder: 0.65)
- **Effect:** One random standing member is incapacitated for `STUN_TICKS` (placeholder: 5 ticks). During this window they contribute no DPS, no food healing, no stat contribution — identical to being downed, but not caused by HP loss.
- **Recovery:** Time only. After `STUN_TICKS` the member returns at their current HP. No Keeper involvement. No wounds.
- **Targeting:** Random from standing, non-grievous, non-stunned members. Captain has `CAPTAIN_STUN_WEIGHT` reduced probability relative to other members (placeholder: 0.3× the base weight).
- **Log line:** `"[Name] is seized by dread — action lost for N seconds"`

### Gate 2 — Permanent Removal (Morale Break)

- **Condition:** `willCut + hopeCut < T_PERM` (placeholder: 0.35)
- **Effect:** One random standing member's will breaks — they flee the field and are removed from the fight entirely. No DPS, no stat contribution, no food healing for the rest of the fight. This is a fight-ending event if it hits a critical member.
- **Recovery:** None within the fight. Member is gone.
- **Targeting:** Random from standing, non-grievous, non-stunned members. Captain has `CAPTAIN_BREAK_WEIGHT` significantly reduced probability (placeholder: 0.15× the base weight). She can still break under catastrophic dread but is the last to go.
- **Log line:** `"[Name]'s will breaks — they are gone"` (flavor TBD — needs words that convey fleeing, not death)
- **Wounds:** Morale break does **not** add a wound. This is a distinct removal path from the HP/wound system. The wounds-dread bridge is deferred (see Open Questions).

### Layer B check cadence

Both gates fire on a per-check basis, not per-tick. `BREAK_CHECK_INTERVAL` (placeholder: 30 ticks) — a check fires every N ticks when the party is below the relevant threshold. This prevents constant triggering while keeping the threat active.

---

## Effective Dread and Negation

```
effectiveDread = rawDread × (1 - min(1, willCut + hopeCut))
willCut  = min(1, captainWil / 100)   // 0 if Captain is grievous OR stunned
hopeCut  = min(1, hopeAntidote / 100)
```

**Three zones:**

| Zone | Condition | Experience |
|---|---|---|
| Safe | negation ≥ T_TEMP | Layer A floor only. Faint weight. No action denial. |
| Danger | T_PERM ≤ negation < T_TEMP | Layer A full drag + temporary incapacitations. Visible pressure, recoverable. |
| Critical | negation < T_PERM | Layer A full drag + temporary incapacitations + permanent breaks possible. Fight in serious danger. |

---

## Code Paths That Change

### `hearthcraft_fight_sim.html`

- Replace current `dreadAfter` calculation with Layer A formula
- Add Layer B check at `BREAK_CHECK_INTERVAL` cadence
- Track `stunned` state per member (separate from `grievous` and HP)
- Stunned members excluded from DPS, healing, and stat contribution for stun window
- Broken members flagged permanently out (similar to grievous)
- Captain targeting weight reduced for both stun and break checks
- DPS formula updated: `eff = raw × (1 - physAfter) × (1 - layerADrag)`
- Results tab: dread mitigation meter updated to reflect Layer A drag vs. Layer B events

### `run_sim.js`

- Mirror all Layer A and Layer B changes from the browser sim
- `PEN_SCALE` and all dread constants must match between both files
- Add summary output: Layer B events per run (stuns fired, breaks fired)

### `docs/combat-model.md`

- Replace "Dread mechanics" section with two-layer model
- Update Key Constants table with new dread constants
- Note: all dread constants are placeholders until sim validation

---

## Validation Protocol

Monte Carlo sweep across dread severity (0 → max) at several negation levels (0%, 35%, 65%, 90%, 100%), 5000 runs each. Confirm:

1. At 100% negation, Layer A floor still produces a measurable (small) win-rate effect — dread is never fully nullified
2. Layer B (stun) absent at negation ≥ T_TEMP, present and visible at negation < T_TEMP
3. Layer B (break) absent at negation ≥ T_PERM, fight-ending at negation < T_PERM
4. Win-rate responds smoothly to Layer A tuning (`VAR_COEF`, `FLOOR_COEF`)
5. Win-rate responds sharply to Layer B threshold tuning (`T_TEMP`, `T_PERM`)
6. Two-dial behavior confirmed: adjusting Layer A constants moves win-rate without affecting Layer B event frequency; adjusting T_TEMP/T_PERM moves Layer B events without changing Layer A drag

Lock constants once validation passes. Document validated values in `docs/combat-model.md`.

---

## Open Questions

- **Wounds bridge (deferred):** Should a morale-break eventually apply a lingering wound (Houses of Healing / Apothecary content)? Do not wire until wounds redesign is settled.
- **Flavor text:** Break log line needs Tolkien-appropriate words for flight/collapse of will that don't overlap with death vocabulary. Design separately.
- **Stun vs. grievous display:** Does the stunned member's card show a distinct visual state in the sim (separate from the grievous state)? Decide before UI implementation.

---

## Constants Summary (all placeholders — sim to validate)

| Constant | Placeholder | Description |
|---|---|---|
| `VAR_COEF` | TBD | Layer A variable term coefficient |
| `FLOOR_COEF` | TBD | Layer A floor term coefficient (tied to rawDread) |
| `T_TEMP` | 0.65 | Negation threshold below which stuns fire |
| `T_PERM` | 0.35 | Negation threshold below which breaks fire |
| `STUN_TICKS` | 5 | Duration of temporary incapacitation |
| `BREAK_CHECK_INTERVAL` | 30 | Ticks between Layer B checks |
| `CAPTAIN_STUN_WEIGHT` | 0.3× | Captain's relative stun targeting weight |
| `CAPTAIN_BREAK_WEIGHT` | 0.15× | Captain's relative break targeting weight |
