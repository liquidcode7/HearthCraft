# HearthCraft — Mechanics and Math Reference

> All formulas derived from `tools/sim/run_sim.js` and `hearthcraft_fight_sim.html`.
> This is the authoritative reference for how combat numbers work.
> Update this file whenever a formula changes in the sim.

---

## Stats

Five stats drive everything. Each member has a unique spread:

| Stat | What it does |
|---|---|
| **Might (mig)** | Physical DPS (Warden, Captain), armor penetration, Hunter hybrid DPS |
| **Agility (agi)** | Hunter DPS, ranged penetration, Keeper and Captain pen contributions |
| **Vitality (vit)** | Morale pool size for that member |
| **Will (wil)** | Keeper DPS, rescue burst size, Dread resistance (Captain), Captain DPS |
| **Fate (fat)** | Spike evasion chance, all Inspiration trigger rates |

---

## Member Templates (base stats at level 1)

| Member | Role | Mig | Agi | Vit | Wil | Fat | Morale |
|---|---|---|---|---|---|---|---|
| Borin | Warden | 13 | 6 | 15 | 7 | 6 | **270** |
| Mira (Agi build) | Hunter | 9 | 15 | 7 | 5 | 9 | **142** |
| Mira (Mig build) | Hunter | 15 | 9 | 8 | 5 | 7 | **158** |
| Cael | Keeper | 5 | 7 | 9 | 15 | 12 | **174** |
| Aelin | Captain | 8 | 7 | 12 | 13 | 13 | **222** |

---

## Growth (per band level above 1)

`stat_at_level = base + grow_rate × (level − 1)`

| Member | Mig/lvl | Agi/lvl | Vit/lvl | Wil/lvl | Fat/lvl |
|---|---|---|---|---|---|
| Warden | +0.50 | +0.15 | **+0.55** | +0.20 | +0.15 |
| Hunter (Agi) | +0.30 | **+0.55** | +0.20 | +0.15 | +0.35 |
| Hunter (Mig) | **+0.55** | +0.30 | +0.25 | +0.15 | +0.25 |
| Keeper | +0.15 | +0.20 | +0.30 | **+0.55** | +0.45 |
| Captain | +0.20 | +0.20 | +0.40 | +0.45 | **+0.45** |

---

## Morale (the HP analog)

```
morale = round(30 + vit × 16)
```

Every +1 Vitality = +16 morale. The flat 30 is the floor at Vit 0.

**Level 1 examples:**
- Warden: 30 + 15×16 = **270**
- Hunter (Agi): 30 + 7×16 = **142**
- Captain: 30 + 12×16 = **222**

---

## DPS Formulas (per second, per member)

Each member contributes raw DPS every tick while active and above 0 HP:

| Member | Formula |
|---|---|
| Warden | `Mig × 0.5` |
| Hunter (Agi build) | `Agi + Mig × 0.4` |
| Hunter (Mig build) | same formula, different base stats |
| Keeper (damage mode) | `Wil × 0.9` |
| Captain | `Mig × 0.3 + Wil × 0.2` |

The Keeper only deals damage when no rescue is needed. If a party member is at 0 HP and the Keeper has a rescue available, the Keeper bursts that member instead of attacking.

**Red Dawn bonus:** during the 10-tick Captain Inspiration window, all party DPS × 1.5.

---

## Armor Penetration

Penetration comes from the party's **draught** — a single party-wide choice, not from character stats:

```
physAfter = max(0,  physMit × (1 − min(1, draughtPotency / PEN_SCALE)))
```

`physMit` is the enemy's armor value (0.0 to 1.0). `draughtPotency` is the potency value of the equipped draught (0 if none). A draught potency equal to `PEN_SCALE` fully negates the armor. Magic damage (Keeper, Captain's Will half) bypasses armor entirely.

**Effective DPS** after armor, Dread, and per-tick jitter:
```
DPS_effective = DPS_raw × (1 − physAfter) × (1 − dreadAfter) × jitterFactor
jitterFactor  = 1 + U(−JITTER, +JITTER)   // uniform random each tick
```

`JITTER = 0.10` by default (±10%). Each tick's output varies around the mean — same average DPS over a long fight, real variance fight-to-fight. Set to 0 for fully deterministic output.

---

## Dread

Dread suppresses the party's DPS output. The Captain (Will) and Hope food both counter it:

```
willCut  = min(1, captain.wil / 100)
hopeCut  = min(1, hopeFood / 100)
dreadAfter = max(0, dread × (1 − min(1, willCut + hopeCut)))
```

A Captain with Wil 100 fully negates Dread. Hope food stacks on top.

---

## Enemy Drain

Steady pressure applied every tick, distributed evenly across *standing* members (HP > 0, not grievous):

```
drain_per_member = drain / standing_count
```

As members fall to 0 HP, the remaining standing members each absorb more drain. This is why losing a member mid-fight accelerates the collapse.

---

## Food — HP/s

Food provides healing every tick to active members (not grievous) who have HP > 0.

**Softcap:**
```
incPer = drain / active_count        (drain share per active member)
softcap = incPer × 1.5
if food > softcap:
    effective_heal = softcap + (food − softcap) × 0.4
else:
    effective_heal = food
```

Healing above the softcap is reduced to 40% efficiency. The softcap is 1.5× the drain each member absorbs — excess healing has diminishing returns.

**Example:** Wolves drain=18, 4 members → incPer=4.5 → softcap=6.75 HP/s. Food above 6.75 heals at 40%.

---

## Food — Tier Table (HP/s per cooking level)

Within-tier HP/s uses a convex curve (power 1.8): slow gains at the tier start, bigger gains near the ceiling. Tier boundaries are hard anchors.

| Tier | Cooking level | HP/s range |
|---|---|---|
| Hearthkeeper | 1–4 | 5 – 10 |
| Initiate | 5–9 | 11 – 18 |
| Apprentice | 10–15 | 19 – 30 |
| Journeyman | 16–22 | 31 – 44 |
| Adept | 23–30 | 45 – 62 |
| Master | 31–40 | 63 – 86 |
| Grandmaster | 41–50 | 87 – 110 |

```
frac   = (cookLevel − tier.lo) / (tier.hi − tier.lo)
HP/s   = tier.hpsLo + frac^1.8 × (tier.hpsHi − tier.hpsLo)
```

---

## Food — Stat Bonuses

Stat bonuses are determined by the recipe and the food level (FL). FL is relative to the recipe:

```
FL = cookingLevel − recipe.levelRequired + 1
```

| FL | Primary stat | Secondary stat |
|---|---|---|
| FL1 | +1 | — |
| FL2 | +1 | — |
| FL3 | +2 | — |
| FL4 | +2 | +1 |

**Recipe stat assignments (tier-1):**

| Recipe | Primary | Secondary | levelRequired |
|---|---|---|---|
| Hearthbread | Vit | Mig | 1 |
| Wanderer's Supper | Agi | Mig | 1 |
| Contemplative Tea | Wil | Agi | 1 |
| Ranger's Fare | Mig | Vit | 1 |

Stat bonuses are additive: +1 Vit = +16 morale, +1 Wil = +0.9 Keeper DPS and +4 rescue burst, etc.

**Fate cannot be increased by food.** Fate is an innate quality of each band member — it grows with level but cannot be cooked into. Food affects Mig, Agi, Vit, and Wil only.

Each party member can eat a different recipe. The player chooses — nothing forces role-matched food.

---

## Spike Mechanics

Enemy spikes are discrete burst hits that drive members to 0 HP (triggering wounds):

- **Interval:** `mean × U(0.5, 1.5)` — uniform jitter around the configured mean (±50%)
- **Damage:** `base × U(0.7, 1.3)` — uniform jitter around the base (±30%)
- **Target:** random active standing member
  - If the Horn of Gondor is active, spikes always target the Warden
  - If a spike would kill the Keeper and the Warden is alive, the Warden intercepts (up to WARD_CAP = 3 times)

---

## Fate — Spike Evasion

Before spike damage applies, the target gets a chance to slip it entirely:

```
evasion_chance = target.fat × 0.004
```

At Fat 25: 10% chance to evade any given spike. No damage dealt on evasion.

---

## Shadow — Stat Drain

Shadow drains Will and Fate from all active members each tick:

```
drain_per_step = shadow × 0.0011
deepFloor = max(0.25, 0.55 − (shadow/100) × 0.30)
floor = deepFloor + (1 − deepFloor) × min(1, radiance/100)

wil = max(wilBase × floor, wil − drain_per_step)
fat = max(fatBase × floor, fat − drain_per_step)
```

Shadow has a hard floor (stats cannot drain below `base × floor`). Radiance food raises the floor, limiting how far Shadow can suppress Wil and Fat.

Shadow is the designed counterbalance to Fate: in Shadow encounters, high-Fat parties start lucky and become progressively less lucky as the fight drags on.

---

## Hazard Drains (Cold, Heat, Disease, Wakefulness)

Each hazard adds morale drain per tick, partially offset by its matching antidote food:

```
extra_drain = severity × 0.08 × (1 − min(1, antidote/100))
```

Distributed evenly across active members. A Warmth food value of 100 fully negates Cold drain at any severity.

---

## Wounds and Grievous

Hitting 0 HP does not eliminate a member — it inflicts a wound:

- Member at 0 HP → +1 wound
- Member recovers above 5 HP → wound timer resets (they can take another wound next time they hit 0)
- **5 wounds = grievous** — the member is eliminated unless Laurelin's Grace fires

GRIEVOUS cap: 5 wounds. Grievous members are out for the fight.

---

## Keeper — Rescue Burst

When any non-Keeper party member hits 0 HP and the Keeper is alive with HP > 0:

```
burst = (40 + keeper.wil × 4) × burstMultiplier
rescued_member.hp = min(max_morale, max(0, member.hp) + burst)
```

The rescued member is brought back at 0 + burst. The Keeper can rescue up to RESCUE_CAP = 5 times per fight. After 5 rescues, the Keeper deals damage instead of rescuing.

**+1 Wil to Keeper = +4 burst healing per rescue.** At Wil 15 (level 1): burst = 40 + 60 = 100 HP. At Wil 20 (level 10 growth): burst = 40 + 80 = 120 HP.

---

## Warden — Guard

If a spike would kill the Keeper outright, the Warden intercepts it instead (absorbs the hit):

```
if spike_would_kill(keeper) and warden.hp > 0 and wardsUsed < WARD_CAP:
    target = warden
    wardsUsed++
```

WARD_CAP = 3 guards per fight.

---

## Inspirations

All four Inspirations fire at most once per fight (except Laurelin's Grace, which can fire once per member per wound threshold crossing). Each has a trigger condition and a probability roll.

### Laurelin's Grace (Keeper)

**Trigger:** A party member hits their 5th wound (about to go grievous).
**Roll:**
```
chance = min(0.25, graceBase + keeper.fat × 0.003)
graceBase = 0.05
```
At Fat 12 (Keeper level 1): `0.05 + 0.036 = 8.6%` chance. Capped at 25%.
**Effect:** The member returns at 40% morale, wounds reset to 0. Does not consume the Keeper's rescue count.

---

### The Horn of Gondor (Warden)

**Trigger:** Rising edge — the moment two or more party members enter crisis (HP < 35%). Fires only once, on the first crossing of that threshold.
**Roll:**
```
chance = min(0.25, hornBase + warden.fat × 0.003)
hornBase = 0.03
```
At Fat 6 (Warden level 1): `0.03 + 0.018 = 4.8%` chance.
**Effect:** 8-tick window where all spikes are redirected to the Warden. The Warden receives no food healing during this window (they're holding the line, not resting).

---

### Wrath, Ruin, and the Red Dawn (Captain)

**Trigger:** Same rising-edge crisis condition as the Horn, evaluated independently.
**Roll:**
```
chance = min(0.25, dawnBase + captain.fat × 0.003)
dawnBase = 0.03
```
At Fat 13 (Captain level 1): `0.03 + 0.039 = 6.9%` chance.
**Effect:**
- All active members healed for 15% of their max morale
- All party DPS × 1.5 for 10 ticks
- All other Inspiration base rates +0.15 for approximately 30 ticks (decays at 0.005/tick)

---

### Black Arrow (Hunter)

**Trigger:** DPS-race stalemate — the fight is being lost on damage output, not sustain.
```
ttk = boss_hp_remaining / DPS_effective     (time to kill at current rate)
losing = (ttk > time_remaining) AND (elapsed > 40% of max duration)
```
**Roll:** Probability rises over time once the trigger condition is met:
```
rise = min(0.06, (elapsed − 0.4) × 0.06 × 2.5)
chance = rise + hunter.fat × 0.003 + inspBoost
```
Caps at 6% + Fate contribution + boost. At Hunter Fat 9 (level 1): Fate adds 2.7%, making max ~8.7%.
**Effect (attrition mode):** Enemy resolve reduced by 35% instantly.
**Effect (survival mode):** Fight duration cut by 15% (enemy retreats sooner).

Black Arrow does NOT fire in sustain-failing fights — only in fights where the party is healthy but the kill is out of reach. This is correct: it is a last-ditch DPS offensive, not a rescue.

---

## Constants Summary

| Constant | Value | What it is |
|---|---|---|
| RESCUE_CAP | 5 | Max Keeper rescues per fight |
| WARD_CAP | 3 | Max Warden intercepts per fight |
| GRIEVOUS | 5 | Wounds required to eliminate a member |
| SHADOW_FLOOR | 0.55 | Base stat floor under Shadow (fraction of base) |
| SHADOW_RATE | 0.0011 | Stat drain per tick per Shadow point |
| PEN_SCALE | 80 | draughtPotency needed to fully negate armor |
| JITTER | 0.10 | Per-tick DPS variance (±10%); 0 = deterministic |
| SCURVE_P | 1.8 | Within-tier HP/s curve exponent |
| fateEvadeCoef | 0.004 | Fate × this = spike evasion chance |
| fateInspCoef | 0.003 | Fate × this = Inspiration rate bonus |
| graceBase | 0.05 | Laurelin's Grace base trigger rate |
| hornBase | 0.03 | Horn of Gondor base trigger rate |
| dawnBase | 0.03 | Red Dawn base trigger rate |
| blackArrowCap | 0.06 | Black Arrow max rise probability |
