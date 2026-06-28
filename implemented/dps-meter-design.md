# Spec: MMO-Style Results Charts
**Date:** 2026-06-19
**Status:** Approved

---

## Overview

Replace the line charts in the simulator Results tab that show per-character DPS, mitigation breakdowns, and armor vs. magic data with MMO-style bar meters and a damage-type pie chart. The existing time-series line charts for Effective DPS, Morale, Wounds, Spirit, and Inspirations are untouched.

Also requires one sim engine change: Captain becomes 50/50 physical/magic damage, and Red Dawn boost tracking is added.

---

## Color System

| Character / Type | Color |
|---|---|
| Hunter (DPS) | Red — `#c0392b` |
| Warden (Tank) | Blue — `#2e6da4` |
| Keeper damage | Purple — `#7d5a93` |
| Keeper healing | Green — `#5b7f63` |
| Captain | Gold — `#b8843c` |
| Captain's boost (party-wide) | Silver — `#8a9aaa` |
| Physical damage type | Red — `#c0392b` |
| Magic damage type | Purple — `#7d5a93` |

These replace the existing `TPL[k].color` values used in the replaced charts. The line charts that remain (Morale, Wounds, Spirit) keep their existing colors.

---

## Bar Meter Component

A reusable `meterChart(host, rows, opts)` function renders a card body as an MMO DPS meter:

**Each row:**
```
[Name label]   [████████████░░░░░░░░░░░]   [47.3 dps]   [38%]
```

- Bar fills proportionally to that row's value relative to the largest value in the set (longest bar = 100% width)
- Value label uses the unit appropriate to the chart (dps, hp/s, etc.)
- Percentage is that row's share of the sum of all rows
- Rows sorted highest value first
- Footer row: `Total: X dps` (or appropriate unit)
- Bar color is per-row (passed in with the data)
- Implemented as plain HTML/CSS, no SVG — `div` with inline width % for the bar fill

---

## Cards Replaced

### 1. DPS Per Character
**Replaces:** `ch_dpschar` line chart
**Card title:** DPS per character

Four rows, sorted by avg effective DPS:

| Row | Color |
|---|---|
| Hunter (DPS) | Red |
| Warden (Tank) | Blue |
| Keeper (damage) | Purple |
| Captain | Gold |

**Data:** average of `effBy[k]` across all ticks (existing field).
**Footer:** Total party DPS.

---

### 2. Keeper Healing
**New card** (no previous chart)
**Card title:** Keeper healing

Single green row: Keeper's avg HP/s restored across the fight.

**Data:** Requires new per-tick field `healBy.keeper` — sum of all HP restored by Keeper burst rescues in a tick, divided by tick count. Add to sim engine alongside existing `effBy`.

---

### 3. Captain's Total Boost
**Replaces:** nothing (was folded into Mitigation chart)
**Card title:** Captain's boost to the party

Single silver row showing avg DPS the party gained because of the Captain:
- **Will boost:** avg `willSaved` per tick (already tracked — DPS preserved from Dread)
- **Red Dawn boost:** avg `dawnBoost` per tick — new field: when `windows.dawn > 0`, record `raw * 0.5` (the extra 50% from the 1.5× multiplier)

**Data:** `captainBoost = avg(willSaved + dawnBoost)` per tick.

Label: "Captain's boost (Will + Red Dawn)"

---

### 4. Enemy Damage by Type — Pie Chart
**Replaces:** `ch_dmgmit` line chart
**Card title:** Damage dealt to enemy — by type

Pie chart with two initial slices, extensible:

| Slice | Color | Data |
|---|---|---|
| Physical | Red | Hunter effBy + Warden effBy + Captain physical half (50% of Captain effBy) |
| Magic | Purple | Keeper effBy + Captain magic half (50% of Captain effBy) |

Rendered as an SVG pie/donut chart. Each slice is labeled with its damage type name, total value, and %. A legend below uses the same color dots used elsewhere.

**Extensibility:** slice data is passed as an array `[{name, color, value}]` so future damage types (Westernesse, fire, etc.) are added by pushing a new entry.

---

### 5. Armor Mitigation
**Replaces:** existing armor portion of `ch_mit` and `ch_dmgmit`
**Card title:** Armor mitigation

Two rows:

| Row | Color | Data |
|---|---|---|
| Physical DPS absorbed by armor | Red `#c0392b` | avg `physMitTotal` per tick (already tracked) |
| Physical DPS through armor | Green `#5b7f63` | avg physical effBy total (Warden + Hunter + Captain physical) per tick |

Footer: Total physical attempted.

Shows what the enemy's armor cost the party, and what got through.

---

### 6. Dread Mitigation
**Replaces:** remaining portion of `ch_mit`
**Card title:** Dread mitigation

Three rows:

| Row | Color | Data |
|---|---|---|
| DPS lost to Dread (net) | Dark red `#9a3526` | avg `dreadAfter * raw` per tick (what Dread actually cost after counters) |
| Saved by Captain Will | Gold | avg `willSaved` per tick (already tracked) |
| Saved by Hope | Amber `#c9a84c` | avg `hopeSaved` per tick (already tracked) |

Footer: Total Dread pressure (would have been lost without any counters).

Only shown when Dread is active, same as current `anyMit` guard.

---

## Sim Engine Changes

### Captain 50/50 hybrid damage

Current:
```js
else if(k==="captain") r = M[k].mig*0.3 + M[k].fat*0.2;
// PHYS_ROLES: captain: true
```

New:
```js
else if(k==="captain") r = M[k].mig*0.3 + M[k].fat*0.2; // total unchanged
// PHYS_ROLES: captain is split — 50% physical, 50% magic
```

In `dpsBreakdown`, Captain's `effBy.captain` is split:
- `effBy.captain_phys = rawBy.captain * 0.5 * (1 - physAfter) * (1 - dreadAfter)`
- `effBy.captain_magic = rawBy.captain * 0.5 * (1 - dreadAfter)`
- `effBy.captain = effBy.captain_phys + effBy.captain_magic` (for the DPS bar meter total)

`physMitTotal` is updated to include Captain's physical half.
`magicDmg` is updated to include Captain's magic half.

### Keeper healing tracking

Add `healBy: { keeper: 0 }` to each sim tick's series entry. When the Keeper fires a burst rescue, record the HP restored in that tick's `healBy.keeper`. Average over all ticks for the card.

### Red Dawn boost tracking

Add `dawnBoost` to each tick's series entry. When `windows.dawn > 0`, set `dawnBoost = raw * 0.5` for that tick (the delta above baseline). Average over all ticks for the Captain's boost card.

---

## Layout

Results grid stays 2-column. New card order:

1. Effective DPS over time *(line chart, unchanged)*
2. Party Morale over time *(line chart, unchanged)*
3. **DPS per character** *(bar meter — new)*
4. **Captain's boost** *(bar meter — new)*
5. **Keeper healing** *(bar meter — new)*
6. **Enemy damage by type** *(pie chart — new)*
7. **Armor mitigation** *(bar meter — new)*
8. **Dread mitigation** *(bar meter — new, conditional on Dread active)*
9. Wounds accumulated *(line chart, unchanged)*
10. Shadow — party spirit *(line chart, unchanged, conditional)*

---

## Out of Scope

- Hunter HP adjustment (separate sim-tuning task)
- Live tab DPS gauges (text-only, not changed in this pass)
- Westernesse or additional damage types (pie chart is architected for them but not added)
