# HearthCraft — Combat Model

> Not V1. The campaign combat system. This is the design destination, not the
> current build. V1 uses a simplified mission model; full combat lands in V2+.
>
> **Executable spec: `tools/sim/hearthcraft_fight_sim.html`** — where this doc
> and the HTML disagree, the HTML wins. Flag any divergence and reconcile.
>
> Companion files: `battlegrounds-rpg.md` (full RPG vision), `progression.md`
> (stats and growth), `bestiary.md` (enemy tiers), `battlegrounds.md` (lore
> roster). The spreadsheets in `tools/sim/` are the tuning instruments.

---

## What Combat Is

Preparation-driven, semi-autonomous. The player (the unseen provisioner) does
not act during a fight. All agency is in **preparation**: party level, food
(stat boosts + HP/s healing + hazard antidotes), and reading what the encounter
demands. The fight runs itself and returns a result.

One tick = one real second.

Victory condition: **kill the enemy resolve pool with at least one member
standing.** Three grievous members and one survivor at 1 HP who lands the kill
is a full win — the cost is healing time after, not the fight outcome.

---

## The Party (four roles; a fifth is reserved)

| Role | Template name | Job | Soak | Start Mig/Agi/Vit/Wil/Fat | Grow / level |
|---|---|---|---|---|---|
| Warden | Borin Ironmantle | Tank — guards the Keeper | yes | 13/6/15/7/6 | .5/.15/.55/.2/.15 |
| Hunter (Agi build) | Mira | Primary DPS | no | 9/15/7/5/9 | .3/.55/.2/.15/.35 |
| Hunter (Mig build) | Mira | Primary DPS (alt) | no | 15/9/8/5/7 | .55/.3/.25/.15/.25 |
| Keeper | Cael | Healer — the only in-fight revive | no | 5/7/9/15/12 | .15/.2/.3/.55/.45 |
| Captain | — | Support / Dread resist | yes | 8/7/12/13/13 | .2/.2/.4/.45/.45 |

**Stat at level L** = `start + grow × (L - 1)`

**Morale (the HP pool)** = `round(30 + Vit × 16)`

Roles are **fixed nature** (the Aragorn principle): growth is realization within
role, not class-swapping. Any named member can realize any role through
provisioning and survival — but once realized, a Warden is a Warden.

**The 5th role (melee DPS)** — slot reserved, stats and penetration coefficient
wired into the sim but not yet designed. Open thread: party-of-5 vs
field-4-from-roster; distinct identity vs. the Might Hunter; name and
Inspiration.

### DPS formulas (raw per second, before mitigation)

Only from members with HP > 0 and not grievous:

- **Warden:** `Mig × 0.5`
- **Hunter (Agi):** `Agi + Mig × 0.4`
- **Hunter (Mig):** `Mig + Agi × 0.4`
- **Keeper:** `Wil × 0.9` (zero on any tick it spends a rescue/burst)
- **Captain:** `Mig × 0.3 + Fat × 0.2`

---

## The Enemy

A single **resolve** pool the party grinds to zero with DPS. Fight length ≈
`resolve / effective party DPS`, targeted at ~20 minutes. "A swarm of foes"
and "one large creature" are mechanically identical — one pool, one fight.

Pressure on the party:

- **Steady drain / sec** — spread across **standing** members only. Downed
  members have no morale left to bleed; loss concentrates pressure on survivors.
  This is the solvable layer: provision correctly and it's handled.
- **Spikes** — every `spikeIntervalSec`, a spike of `spike` potency hits **one
  random standing member.** Pure random targeting — this is what makes spikes
  dangerous in a semi-autonomous fight: they can land on fragile members
  (Hunter, Keeper) with no mid-fight reaction possible. The drain-to-spike ratio
  is the per-encounter feel dial: calm vs. white-knuckle.
- **physMit %** — armor; reduces effective DPS. Countered by party penetration.
- **Status severities** — Dread, Shadow, Disease, Cold, Heat, Wakefulness.
  See Food & Hazards.

---

## Survival Rules (the heart of the model)

1. **Down = out.** HP ≤ 0 → incapacitated + 1 wound. No DPS, no food healing,
   no stat contribution. Stays out until a Keeper rescue lifts HP above 0. Food
   does **not** revive the fallen — it only sustains the living.

2. **Wounds and grievous.** Each crossing to ≤ 0 (from above 0) = +1 wound.
   **5 wounds = grievous** → out for the rest of the fight → Houses of Healing.
   A member sitting at exactly 0 takes only one wound; no re-wounding until
   healed back above the threshold.

3. **The Warden guards the Keeper — against killing blows only, and only 3×.**
   A spike that would *down* the Keeper is body-blocked by a **standing Warden**,
   who takes it instead. Capped at **WARD_CAP = 3 guards per fight.** Non-lethal
   spikes hit the Keeper normally. Once the 3 guards are spent (or the Warden is
   down), the Keeper is exposed. Keeper death is downstream of the tank being
   overwhelmed — never a cheap unlucky spike.

4. **Keeper rescue.** When a member is down and the Keeper is standing and
   rescues remain (**RESCUE_CAP = 5** per fight), the Keeper revives them to a
   burst of HP = `40 + Wil × 4` (× burstmul). The Keeper deals no DPS that
   tick. A **downed Keeper cannot rescue** — losing the Keeper is the spiral.

5. **Food healing** — per-second HP to **living** members only. Diminishing
   returns: full rate up to `1.5 × per-member incoming drain`; excess heals at
   40% effectiveness. Provisions do not revive the fallen.

6. **Victory** = kill the resolve pool with ≥ 1 member standing. Grievous
   casualties are survivable; the cost is Houses of Healing time, not a loss.

7. **Defeat** = no one left standing (all downed or grievous = no DPS, no
   rescuer) **or** the duration cap is reached without the kill. The cap is a
   backstop, not the target.

---

## Physical Mitigation and Penetration

**effArmor** = `physMit × (1 - min(1, partyPen / PEN_SCALE))`

`PEN_SCALE = 110`. Penetration is not a buff — it is computed from party stats:

| Member | Pen stat | Coefficient |
|---|---|---|
| Warden | Mig | 0.25 |
| Hunter | Agi | 0.90 (dominant) |
| Keeper | Agi | 0.15 |
| Captain | Mig | 0.20 |

Total penetration = Σ (standing, non-grievous) `penStat × coef`. Higher Agility
from food raises the Hunter's contribution and is the primary armor-counter.

---

## Food, Stats, and Hazards

### Food buffs stats directly

Every recipe has a **stat focus** (1–2 stats boosted strongly) that persists at
every tier. Higher tiers add **breadth** (minor boosts to other stats), but the
focus magnitude stays high — a focused low-tier recipe remains relevant for
min-maxing. HP/s is a **separate universal sustain layer** on top of stat boosts.

Old abstract buff vocabulary (Endurance, Agility, Acuity, Warmth, Luck) is
**retired.** Food now directly touches the five base stats:

- **Might** — melee damage, Warden penetration, Captain DPS
- **Agility** — Hunter DPS (dominant), armor penetration
- **Vitality** — Morale pool size (Vit × 16)
- **Will** — Keeper heal magnitude, Dread resistance, Captain aura, Inspiration
- **Fate** — Inspiration odds, critical heals

**"Cook the right dish for the right member"** is the provisioning puzzle.

### Six hazards, six antidotes

Hazards are properties of **regions.** Each hazard has one single-word food
antidote. The clue system telegraphs which hazard a region carries.

| Hazard | Antidote | Mechanic |
|---|---|---|
| Cold | **Warmth** | extra Morale drain |
| Heat | **Heat-ease** | extra Morale drain |
| Disease | **Hale** | extra Morale drain |
| Wakefulness/torpor | **Alert** | extra Morale drain |
| Dread | **Hope** | suppresses party DPS; countered by Hope (flat %) + Captain Will (multiplicative) |
| Shadow | **Radiance** | drains Will + Fate over time toward a floor; Radiance raises the floor (Model C) |

### Dread mechanics

Dread suppresses party DPS. Counter: `effective dread = dread × (1 - min(1, willCut + hopeCut))`
where `willCut = min(1, captainWil / 100)` and `hopeCut = min(1, hopeAntidote / 100)`.
Will and Hope stack additively before capping. Captain will be grievous → no Will cut.

### Shadow mechanics (Model C)

Shadow drains Will and Fate every tick toward a floor. The floor determines how
deep the spirit can sink:

```
deepFloor = max(0.25, SHADOW_FLOOR - (shadow/100) × 0.30)   // harsher Shadow = lower floor
floor = deepFloor + (1 - deepFloor) × min(1, radiance/100)  // Radiance raises floor
```

`SHADOW_FLOOR = 0.55`. Drain rate is constant; Radiance limits **depth**, not
speed. Partial Radiance always helps. Full Radiance nearly stops the drain.

Cascades: draining Will weakens Keeper rescues AND Dread resistance (Shadow
makes Dread worse when both are present). Draining Fate lowers Inspiration odds.

### Shadow vs Dread — the lore carve

**Shadow** = the metaphysical Unlight that unmakes light and withers the spirit.
Answered by Light (Radiance). Anti-*light.*

**Dread** = the cowering of will before evil. Answered by courage (Hope + Will).
Anti-*courage.*

The rule: does the foe **unmake light**, or merely **frighten**? Unmake light →
Shadow. Frighten → Dread. Great foes carry both.

| Foe | Shadow | Dread |
|---|---|---|
| Great spiders / Shelob | yes | yes |
| The Balrog | yes | yes |
| Nazgûl / Ringwraiths | yes | yes |
| Sauron / Dawnless Day | yes | yes |
| Barrow-wights | no | yes + Disease + Cold |
| Dragons | no | yes |
| Orcs, wargs, trolls | no | minor |

The Black Breath is the lore-flavor of Nazgûl Shadow — no separate mechanic.
Athelas belongs in the Houses of Healing aftermath (Apothecary branch), not as
an in-fight counter.

---

## Inspirations (rare grace — outside core balance)

All four are gated on the inspirer being **standing** (HP > 0, not grievous).
Tune these separately from core balance; do not let them carry the fight.

- **Keeper — "Laurelin's Grace":** revive a member from the grievous brink
  (wounds reset to 0). Separate from the 5 rescues; uncapped but rare.
- **Warden — "The Horn of Gondor":** Warden pulls ALL spikes for a window;
  fires when 2+ members are in trouble.
- **Hunter — "Black Arrow":** killable = large resolve chunk; un-killable =
  burns time off the survival clock. Rising capped chance as a fight trends to
  time-loss. **IP review before public ship** — direct Tolkien coinage.
- **Captain — "Wrath, Ruin, and the Red Dawn":** party-wide HP burst (can
  revive downed members). Epic / Aragorn at the Black Gate feel.

---

## Key Constants

```
morale            = round(30 + Vit × 16)
stat(L)           = start + grow × (L - 1)
PEN_SCALE         = 110
RESCUE_CAP        = 5      // Keeper rescues per fight
WARD_CAP          = 3      // Warden lethal-spike guards per fight
GRIEVOUS          = 5      // wounds → grievous
rescue burst      = 40 + Wil × 4  (× burstmul)
food heal softcap = 1.5 × (drain / standing count); excess at 0.4 effectiveness
SHADOW_FLOOR      = 0.55   // base floor with no Radiance
SHADOW_RATE       = 0.0011 // per-tick stat drain per point of shadow severity
tick              = 1 second
```

---

## Encounter JSON Schema

The contract between the Encounter Builder spreadsheet and the game loader.
Claude Code builds the Kotlin data class / Room entity against this schema.

**Top-level fields:**

| Field | Type | Description |
|---|---|---|
| `id` | string | Unique key (snake_case) |
| `name` | string | Display name |
| `region` | string | In-world region |
| `tier` | string | Enemy tier: Rabble / Fell / Bane / Nine / Nameless |
| `recLevel` | int | Recommended band level |
| `resupply` | string | `"none"` (gauntlet) or `"checkpoint"` (journey→boss) |
| `rewards` | string | Flavor description of reward pool |
| `flavorIntro` | string | Pre-fight flavor text |
| `designNote` | string | Internal design/tuning notes (not shown to player) |
| `stages` | array | Ordered list of stage objects (see below) |

**Stage object fields:**

| Field | Type | Description |
|---|---|---|
| `stageId` | string | Unique within encounter |
| `label` | string | Display name for this stage |
| `type` | string | `"attrition"` or `"survival"` |
| `objective` | string | `"kill"`, `"survive"`, or `"retrieve"` |
| `durationSec` | int | Fight clock in seconds |
| `resolve` | int | Enemy HP pool (attrition only) |
| `drain` | float | Steady damage per second (spread over standing members) |
| `spike` | float | Spike damage potency (0–340) |
| `spikeIntervalSec` | int | Seconds between spikes (2–20) |
| `physMitPct` | float | Armor percentage (0–90) |
| `dread` | int | Dread severity (0–10) |
| `shadow` | int | Shadow severity (0–10) |
| `disease` | int | Disease severity (0–10) |
| `cold` | int | Cold severity (0–10) |
| `heat` | int | Heat severity (0–10) |
| `wakefulness` | int | Wakefulness severity (0–10) |
| `stageFlavor` | string | Optional flavor text for this specific stage |

**V1 forward-compat note:** build the V1 encounter loader to accept a
`stages` list of length 1 — one layer of indirection now means multi-stage
support is a later data change, not a code rewrite.

**Severity note:** encounter JSONs use 0–10 severity. The simulator's internal
sliders still use raw values. The game engine translates severity → resolved
effect in one place.

---

## Tuned Encounters (current canonical values)

Two encounters are validated against the Monte Carlo simulator and considered
locked. All others exported from the Encounter Builder are pre-tuned placeholders
— treat their numbers as starting points, not final values.

### Neekerbreekers at the Marsh's Edge — recLevel 1 (the cooking tutorial)
`resolve 50000 · drain 12 · spike 75 · spikeInterval 13 (mean, ±50% jitter) · physMit 0 · no statuses · durationCap 1500 (25 min)`

Drain-dominant sustain test. **Unwinnable with no food** (wipe in ~2 min — the
lesson: go cook). Validated at band Lv1 (unlock floor), 5000 runs: FL1=27%, FL2=64%,
FL3=98%, FL4=~100%. Food is the primary lever — FL3 solves this encounter permanently
regardless of band level.

### Wolves in the Chetwood — recLevel 3
`resolve 60000 · drain 18 · spike 75 · spikeInterval 9 (mean, ±50% jitter) · physMit 0 · no statuses · durationCap 1500`

Adds the second question: sustain AND whether the Warden can hold the line for
the Keeper. Validated at band Lv3 (unlock floor), 5000 runs: FL3=6%, FL4=81%,
FL5=93%, FL6=97%, FL7=99%, FL8=~100%. Completely unwinnable at FL1/FL2.
Needs max Hearthkeeper food (FL4) for a real chance; first Initiate level (FL5) is
the comfort threshold.

---

## Difficulty Onramp

Each step up the ladder adds **exactly one new question the fight asks.** The
region gates the question. Dread and Shadow are the last two — they cannot appear
in early content by construction.

| Rung | New question | Teacher foes | Region | Provisioning answer |
|---|---|---|---|---|
| 0 | Survive + win at all? | Wolves, lone goblins | Bree-land, Chetwood | HP/s food + damage |
| 1 | Pierce armor? | Mailed orcs, stone-trolls | Trollshaws, Goblin-town | Agi/Mig food (penetration) |
| 2 | Weather the dice? | Warg packs, barghests | Misty Mtn passes | Feed fragile members; respect Keeper's 5 rescues |
| 3 | Have the region's antidote? | Huorns, midge-marsh, cold passes | Old Forest, Midgewater, Caradhras | The one hazard food (Alert / Hale / Warmth) |
| 4 | Endure when you can't win? | Old Man Willow, blizzard | Old Forest, high passes | All-sustain; the survival mindset |
| 5 | Fight while afraid? | Barrow-wights, the Dead | Barrow-downs, Dead Marshes | Hope food + Captain Will |
| 6 | Fight while the light fails? | Great spiders, Balrog, the Nine | Mirkwood, Moria, Morgul-vale | Radiance |
| 7 | All at once. | Sieges, combined raids | Fornost, the Black Gate | Multi-stage prep, the full toolkit |

**V1 is Rung 0 only.** Rungs 0–4 are all single-stage fights. Multi-stage
journeys and gauntlets appear first at Rung 7.

Early encounter variety before Dread/Shadow comes from **foe shape** (swarm,
tank, endure) crossed with the four "safe" environmental hazards. Five-plus
region-bands of variety before a player ever meets fear.

---

## Multi-Stage Encounters (campaign destination, not V1)

An encounter is an ordered list of stages. A normal boss fight is one stage.
Two dials differentiate the shapes:

1. **Resupply policy** — `none` (gauntlet: one provisioning, N fights, all must
   clear) vs `checkpoint` (journey→boss: re-provision between stages, test
   adaptation).
2. **Final-stage objective** — `kill` | `survive` (the Nine) | `retrieve`
   (war-aim only — the band clearing a path, never doing the player's gathering).

Wounds always carry across stages. Morale and food reset only at a checkpoint.

The **journey stage** is an atmospheric telegraph, not a briefing. It applies
low-dose versions of the region's hazards. The player learns by surviving the
approach and reading the clue system — no intel popup. Optional: a clean journey
clear reduces the boss stage's resolve; arriving battered means full resolve.

---

## Open Threads

1. **The 5th role (melee DPS)** — the one genuine open design thread. Slot
   reserved in sim and Mechanics Reference. Decide: party-of-5 vs
   field-4-from-roster; its identity vs. the Might Hunter; name/template/
   Inspiration.
2. **All magnitudes are placeholders** — stat growth, mitigation per bestiary
   tier, wound count (~5), Keeper burst size + the 5-cap, Inspiration base
   chances, spike potency/interval, drain-vs-sustain margins, severity per-point
   scales.
3. **Black Arrow IP review** before public ship.
4. **Hazard taxonomy** fleshed out as regions are designed.
5. **Rung-3 first-hazard fork** — which environmental hazard does the player
   meet first: Cold→Warmth (most intuitive) or Old Forest's Wakefulness (most
   geographically natural step out of the Shire)?
6. **Simulator slider alignment** — status sliders still use raw values; the
   1–10 severity scale is locked in the spreadsheet/schema. Align the sim
   sliders to 1–10 as a polish pass.

---

## Tools (in `tools/sim/`)

- **`hearthcraft_fight_sim.html`** — the simulator. Open in a browser. Setup /
  Live / Results tabs. Has an Import panel in the Setup tab.
- **`HearthCraft_Encounter_Builder.xlsx`** — encounter authoring. Edit the
  **Sim Encounters** tab (one row per encounter), save, then load into the sim
  via the Import panel. The Schema tab is the contract for the JSON schema above.
- **`HearthCraft_Tier_Planner.xlsx`** — food content and recipe tiers.
  Provisioning Reference tab maps cooking tiers to HP/s ranges and antidotes.
- **`HearthCraft_Mechanics_Reference.xlsx`** — combat number reference. Blue/
  yellow cells are tunable. Values match the simulator.
- **`export_encounters.py`** — batch-exports one `<id>.json` per encounter tab
  from the Builder xlsx. Run after editing the spreadsheet.

**How to test an encounter:**
1. Edit the Sim Encounters tab in the Encounter Builder
2. Save the xlsx
3. Open `hearthcraft_fight_sim.html` in a browser
4. Click **Load .xlsx** in the Import panel and pick the Builder file
5. Pick an encounter from the dropdown — numbers drop into the controls
6. Adjust band level, food HP/s, and antidotes in the right panel
7. Click **Run**
