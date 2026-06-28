# Combat Curve Problem — Context, Findings, and Paths Forward

> Written for design continuity. Bring this document to any design session
> to get full context without replaying the conversation from scratch.

---

## What We're Trying to Achieve

Fights that reward players who prepare carefully and punish players who don't,
with real drama in between. **Four food tiers per encounter** — risky, coin flip,
solid, strong — that genuinely feel different. A player who reads the encounter,
picks the right recipes and draught, and understands their party's strengths
should win more reliably than someone who grabbed the nearest food.

The provisioner fantasy: preparation is the game, not a menu.

---

## The Problem: Why Fights Feel Binary

### One axis, positive feedback

A pure survival fight asks one question: does your food's HP/s beat the enemy's
drain? That is one threshold. Below it you slowly die; above it you survive.
The win rate is a step function — a cliff, not a curve.

Two things make it worse:

**Positive feedback (the cascade):** When a member falls, their share of drain
redistributes to the survivors, making the next death more likely. Positive
feedback self-sharpens the cliff. A fight that starts going badly collapses fast.

**Food tiers sit too close together:** The three tiers (FL1/FL2/FL3 for a given
encounter) span a narrow HP/s range. They all sit near the same cliff edge
instead of landing on clearly different parts of a slope.

### Why jitter doesn't fix it

Adding ±10% per-tick DPS variance (which we did) smears the cliff slightly but
doesn't change its shape. You're sanding the wall, not building a ramp.

### Why a second axis (DPS race) doesn't fix it

Adding boss resolve so the party has to kill before time runs out just adds a
second cliff. The sim confirmed this: two deterministic thresholds stacked still
produce binary outcomes. A DPS race only helps if the DPS carries real variance
(Inspirations, crits) rather than being a fixed number to beat.

---

## The Two Goals — Don't Conflate Them

| Goal | What it means | What fixes it |
|---|---|---|
| **Texture / feel** | "down a member but holding" fights instead of clean-win-or-wipe | cascade flip + member decoupling |
| **Win-rate spread** | three tiers landing at ~25% / 60% / 90% | wider food tier spacing |

Texture fixes change how fights feel without much changing the overall win rate.
Spread requires either wider HP/s gaps between tiers, or shallower fight math.

---

## The Levers (What We Can Pull)

### On the food side
- **HP/s per recipe** — the dominant axis; too narrow between tiers
- **Stat bonuses** (Mig, Agi, Vit, Wil) — affect DPS, morale, rescue burst
- **Recipe choice per member** — each member can eat different food
- **Draught potency** — party-wide armor penetration
- **Hazard antidotes** (warmth, cooling, remedy, wakefulness)
- **Hope food** — counters Dread (multiplicative — see below)
- **Radiance food** — counters Shadow

### On the encounter side
- **Drain rate** — morale pressure per second (primary tuning lever)
- **Boss resolve pool** — how much damage to deal (zero = pure survival)
- **Fight duration timer** — hard ceiling
- **Spike damage and interval** — random burst hits
- **Physical mitigation** (armor) — scales with draught potency
- **Dread** — suppresses party DPS as a multiplier
- **Shadow** — drains Will and Fate over time
- **Hazard severity** (cold, heat, disease, wakefulness)

### On the combat mechanics side (current)
- **Cascade / drain redistribution** — currently increases pressure on survivors
- **Softcap on healing** — 40% efficiency above 1.5× drain-per-member
- **Keeper rescues** — up to 5 burst heals per fight
- **Warden guard** — up to 3 spike interceptions per fight
- **Spike evasion via Fate**
- **Inspirations** — four one-time chance-based effects
- **DPS jitter** — ±10% per tick (added this session)

---

## What the Simulation Showed

An abstract Monte Carlo lab (`tools/sim/curve_lab.js`) tested five configurations.
Each was calibrated so the middle food tier (FL2) won ~60%, making tier spread
the fair comparison.

```
Config                              FL1   FL2   FL3   Clutch   20→80 band
A baseline (cascade=+1)              0%   59%  100%    2 pts    0.60 HP/s
B cascade-flip (beta=-0.6)           0%   60%  100%   61 pts    0.59 HP/s
C decoupled (beta=0, indep spikes)   0%   61%  100%   62 pts    0.58 HP/s
D sink softcap                       0%   60%  100%    3 pts    0.59 HP/s
E combined (flip+decouple+sink)      1%   57%   94%   57 pts    1.41 HP/s
```

**Key findings:**

- Cascade flip and member decoupling dramatically increase **clutch texture**
  (fights where you win but members fell and were rescued) without much changing
  win rates. 2 pts of clutch → 61 pts with these changes.
- Only the combined Config E widens the win-rate band (1.41 HP/s vs 0.60 HP/s).
  FL3 finally comes down from 100% to 94%.
- The abstract lab's numbers don't transfer to the real combat model because
  real spike damage is proportionally much larger (75 damage vs a Hunter's
  142 HP = 53% of morale per spike; the lab used 22% of morale per spike).

**Real sim results with independent member model (beta=0, decouple, sink):**

| Drain | FL1 (5 HP/s) | FL2 (5.7 HP/s) | FL3 (7.4 HP/s) |
|---|---|---|---|
| 16 | **26%** | 95% | 100% |

FL1 lands exactly in the risky tier (T1 target: 20–35%). But FL2 is nearly
as good as FL3, and both are far above T2. The cliff shifted but didn't flatten.

---

## Why the Cliff Is Hard to Eliminate in Spike-Heavy Fights

With independent member health (beta=0, decouple), each member has:
- Net healing: food HP/s − drain/4
- Reserve (sink mode): absorbs spikes before morale takes damage
- Recovery race: reserve refills at the net-healing rate

The cliff exists because the race between reserve-refill and spike-frequency
is extremely sensitive to net HP/s. A tiny difference in food strength
(e.g., 5.0 vs 5.7 HP/s) determines whether reserve is usually full or usually
depleted when the next spike hits — which determines whether spikes deal 25
damage or 75 damage to morale. That asymmetry creates a steep transition.

For a fight to have a gentle win-rate slope, either the spikes need to be
smaller relative to morale (less swingy per hit, higher frequency) or the
food tiers need to straddle the cliff rather than cluster on one side of it.

---

## What's Built — The Resolution

### Independent member model flags (tools/sim/run_sim.js)

All three new combat modes are implemented behind flags. Defaults are unchanged.

```
--beta B        cascade exponent (1=current cascade, 0=neutral/independent; default 1)
--decouple      each member rolls their own spike independently
--sink          replace 40% softcap with a finite reserve pool (RMAX=50)
                that absorbs spike damage before morale takes it
--rmax R        reserve pool cap for sink mode (default 50)
```

To run independent member model (Config E):
```
node run_sim.js --beta 0 --decouple --sink [encounter params]
```

### Food tier redesign (tools/sim/food_model.js)

The Hearthkeeper cooking tier was redesigned to produce four closely-spaced
HP/s values that straddle the encounter's win-rate transition zone:

```
SCURVE_P changed:  1.8 → 1.0  (linear; evenly-spaced steps within each tier)
Hearthkeeper HP/s: 5–10 → 5.0–5.6  (narrowed to fit four tiers in the transition zone)
```

Resulting cooking levels and Neekerbreekers cull win rates at drain=16:

| Cooking Level | HP/s | Win rate (drain=16 cull) | Tier |
|---|---|---|---|
| CL1 | 5.0 | 24% | T1 — risky |
| CL2 | 5.2 | 48% | T2 — coin flip |
| CL3 | 5.4 | 70% | T3 — solid |
| CL4 | 5.6 | 86% | T4 — strong |

Mastery food (6.0 HP/s, Initiate tier entry) gives ~97%.

### Neekerbreekers encounter (encounters/neekerbreekers_midgewater.json)

```
drain: 18 → 16   (recalibrated to align transition zone with Hearthkeeper HP/s values)
```

---

## What the Independent Health Bar Design Means

With `--beta 0 --decouple`:

- Each member always takes exactly drain/4, regardless of how many others
  are standing. No redistribution. No cascade.
- Each member rolls their own spikes independently. Losing the Hunter doesn't
  protect the Warden from spikes.
- Fight outcomes become "how many of four held" rather than "all-or-nothing."
- Per-member food choices are now genuinely consequential — feeding the Hunter
  differently than the Warden has direct, independent effects on each.

This is the correct long-term design. The cascade was a mistake.

---

## The Multiplicative Counter Insight

Some enemy threats can be outmuscled by raw sustain (food tier). Others cannot.

**Additive threats (e.g., flat warmth drain):** more food HP/s substitutes for
the specific counter. No real prep decision — just "cook higher."

**Multiplicative threats (e.g., Dread):** Dread suppresses ALL healing by a
percentage. More healing food hits a wall — 45% of extra healing is still lost.
Only Hope food (which removes the Dread multiplier) actually helps.

The sim showed: all budget into raw sustain, ignore Dread → **0% win**.
Spend a third of budget on Hope, rest on sustain → **99% win**.

**Armor (potency) already works this way.** Armor doesn't get weaker because
you healed more. Only draught potency pierces it. This is why armor↔potency
is a good design — it creates a genuine prep decision that raw sustain can't
shortcut.

Every encounter property that functions as a multiplier creates a "real choice"
for the player. Properties that function additively don't.

The number of live multiplicative threat↔counter pairs per encounter is the
dial for how much prep-rewarding depth that encounter has.

---

## Three Fight Shapes

Mechanically, all encounters fall into one of three shapes:

| Shape | Description | Win condition |
|---|---|---|
| **Kill fight** | Boss has resolve; party burns it down | Kill before timer |
| **Survival fight** | No kill condition; hold out until timer | Survive the duration |
| **Escape/outlast fight** | Enemy cannot be killed; narratively "flee or hold on" | Survive the duration |

Kill and survival fights are mechanically distinct — kill fights have a DPS
axis, survival fights don't. Escape fights are narratively distinct from
survival but mechanically identical. Encounter JSON should tag which shape each
fight is, even if sim math is the same for survival and escape.

---

## Paths Forward

**Path A — Space food tiers further apart (no math change)**
Accept the cliff exists. Design each encounter's T1/T2/T3 breakpoints to
straddle the cliff by wider HP/s margins. T1 food is clearly below break-even,
T3 is clearly above. The fight texture is still clean-win-or-wipe, but the
tiers land at different rates.

**Path B — Independent health bars (implemented)**
Use `--beta 0 --decouple --sink`. Removes the cascade, makes each member's
survival independent, creates clutch texture. FL1 still lands in T1 range at
the right drain. FL2/FL3 still cluster at the high end — the cliff moved but
didn't flatten. Best combined with Path A (wider tier spacing).

**Path C — Multiplicative threat design (encounter content)**
Design new encounters around multiplicative threats that food can't outmuscle.
Armor↔potency (Goblin-town Gate) is already this. Add Dread, Shadow, and others
in subsequent encounters. Each new pair is another axis of prep that rewards
thoughtful players. Does not require math changes — it's encounter design.

**Recommended order:** Implement B (already done), design encounters around C,
accept A as the tier-spacing convention going forward.

---

## Open Questions

1. Should Neekerbreekers use independent health bars (beta=0 + decouple)?
   The texture change is significant — what was a clean wipe becomes "Mira fell
   at minute 18, the rest held." Is that the right feel for a tutorial fight?

2. What HP/s values should define T1/T2/T3 for Neekerbreekers? Current FL1/FL2/FL3
   (5.0 / 5.7 / 7.4 HP/s) put FL1 in T1 range but leave FL2/FL3 at the ceiling.
   Wider spacing requires redesigning which cooking levels represent each tier.

3. How many multiplicative threat↔counter pairs should Encounter 3 (Goblin-town
   Gate) have? Armor↔potency is already there. Dread would be a strong second.

4. Win condition for survival fights: any member alive, or minimum members
   standing (≥2, ≥3)?

5. Should the reserve pool cap (RMAX=50) be configurable per encounter? Higher
   RMAX rewards bringing very strong food; lower RMAX makes spikes bite harder.
