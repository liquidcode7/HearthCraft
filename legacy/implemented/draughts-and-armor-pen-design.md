# Draughts, Potency, and Encounter 3 — Design Spec
**Date:** 2026-06-20
**Status:** Approved

---

## What This Spec Covers

Two connected decisions made together:

1. **The Food/Draught split** — food handles sustain and stats; draughts handle tactical effects (potency and antidotes). Antidotes move off food entirely.
2. **Potency as a draught property** — armor penetration is no longer derived from character stats. It comes from the party's equipped draught.
3. **Encounter 3** — the first armored encounter; the lesson is output, not survival.

---

## The Food / Draught System

### Food (per member, one per member)
- Stat boosts: Might / Agility / Vitality / Will
- HP/s sustain
- **Antidotes are removed from food entirely.** Food is purely sustain and power.

### Draughts (party-wide, one choice per encounter)
- One draught applies to all four party members
- Carry **potency** (armor penetration) and/or **antidotes** (hazard counters)
- **Low-tier draughts: one effect only** — potency OR one antidote
- **High-tier draughts: maximum two effects** — e.g. potency + one antidote, or two antidotes. Secondary effects on combo draughts are weaker than a dedicated single-effect draught. No draught ever carries three effects.

### The provisioning puzzle
In an armored region with no hazard: bring a potency draught. Easy read.
In an armored region WITH a hazard: choose — potency or antidote. You cannot fully cover both until high-tier draughts. That tension is the teaching tool for mid-game.

---

## Potency and the Armor Formula

### What changes
Stat-based penetration is **removed entirely**. The old system derived pen from each member's stats via coefficients (Hunter Agi × 0.90 dominant). This is gone.

### New formula
```
effArmor = physMit × (1 - min(1, draughtPotency / PEN_SCALE))
```

- `draughtPotency` — the potency value on the equipped draught. Zero if no potency draught equipped.
- `PEN_SCALE` — tuning constant. Currently 110; needs retuning in the sim now that pen comes from a single draught value rather than accumulated member stat contributions.
- Magic damage (Keeper, Captain's Will half) **bypasses armor entirely** — unchanged.

### What this means in play
| Draught | Physical DPS impact |
|---|---|
| None / antidote-only | Full armor applies. Physical DPS significantly reduced. |
| Potency draught (low tier) | Armor partially stripped. Physical DPS recovers. Winnable. |
| Potency draught (high tier) | Armor largely stripped. Comfortable win margin. |
| Two-effect draught (potency + antidote) | Partial potency + partial antidote — not as strong as a dedicated draught for either. |

---

## Encounter 3 — Goblin-town Gate

### Identity
**The new question:** can you kill fast enough?

This is a pure output test. The party survives the goblins just fine — chip drain, low spikes. But the resolve pool doesn't die. The clock expires with the boss sitting at 3–5% HP. The player sees exactly what happened: the armor bled just enough DPS that they ran out of time.

The fix is one decision: bring a potency draught.

### Details
| Field | Value |
|---|---|
| `recLevel` | 5 |
| `region` | Misty Mountains — Goblin-town gate |
| `tier` | Rabble |
| `physMitPct` | 20–25% (mailed goblin guards, not plate) |
| Hazards | None (Goblin-town at this rung carries no regional hazard) |
| Drain profile | Steady, moderate — survival is not the question |
| Spike profile | Low potency, low frequency — goblins chip, they don't spike hard |
| `resolve` | Sized in the sim: large enough that lv5 party with good stat food and NO potency draught cannot clear in time; clears comfortably with a potency draught |
| `durationCap` | 1500 sec (25 min — consistent with existing encounters) |

### Why low armor, not high armor
20–25% physMit is intentionally modest. The lesson is that **any armor is a wall without penetration.** The player doesn't expect modest armor to matter this much. When it does, the lesson lands harder. If armor were 50%, the player might suspect the encounter is just overtuned — at 20–25%, the wall is clearly mechanical, not unfair.

### Tuning process
Same as Neekerbreekers and Wolves: set physMit in the sim, adjust resolve until:
- No potency draught → consistent timeout loss (boss at low % HP, clock expires)
- Potency draught (entry-tier) → consistent win at lv5 with role-matched food

---

## What Changes in Existing Systems

### Recipes
- All existing food recipes lose their antidote secondary effects
- Antidotes become a draught property only
- Food card displays: stat boosts + HP/s (no antidote line)

### New draught recipe category
- Needs its own recipe list (parallel to the food recipe list)
- Each draught has: `name`, `effects[]` (array of effect objects), `tier`, `flavor`
- Effect object: `{ type: "potency" | "warmth" | "alert" | "hale" | "hope" | "radiance" | "heat-ease", value: float }`
- Low-tier draughts: one item in `effects[]`
- High-tier draughts: up to two items in `effects[]`, values lower than a dedicated single-effect draught

### Sim
- Remove `PEN_COEF` stat-based penetration calculation
- Add draught slot to provisioning panel (party-wide, one dropdown)
- Draught carries potency value that feeds directly into `effArmor` formula
- `PEN_SCALE` to be retuned against lv5 encounter 3 baseline
- Antidote sliders remain but are now conceptually "draught antidote" not "food antidote"

---

## Open Questions
- What is PEN_SCALE retuned to? (Validate in sim against encounter 3 target win/loss rates)
- Draught recipe count for V1 — how many draughts does the player start with access to?
- Draught crafting time — same session model as food (background cook), or instant?
