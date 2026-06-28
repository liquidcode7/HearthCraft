# HearthCraft — Combat Model

> The campaign combat system. This is the design destination, not the
> current build. Current missions use a simplified buff-strength model; full combat is designed here.
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

- **Warden:** `Mig × 0.5` — physical, subject to armor
- **Hunter (Agi):** `Agi + Mig × 0.4` — physical, subject to armor
- **Hunter (Mig):** `Mig + Agi × 0.4` — physical, subject to armor
- **Keeper:** `Wil × 0.9` — **magic-type, bypasses armor** (zero on any tick it spends a rescue/burst)
- **Captain:** hybrid damage — **physical:** `Mig × 0.3` (subject to armor) + **magic:** `Wil × 0.2` (bypasses armor). Boosting Will via food increases her magic output; boosting Might increases her martial output. Both halves show in the damage-type pie chart. Fate no longer contributes to raw damage — it drives Inspiration odds instead.

---

## The Enemy

A single **resolve** pool the party grinds to zero with DPS. Fight length ≈
`resolve / effective party DPS`, targeted at ~20 minutes. "A swarm of foes"
and "one large creature" are mechanically identical — one pool, one fight.

Pressure on the party:

- **Steady drain / sec** — each active (non-grievous) member absorbs exactly
  `drain / 4` per tick, independent of who else is standing. Losing a member
  does **not** increase pressure on survivors. This is the solvable layer:
  provision correctly and it's handled.
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

5. **Food healing** — per-second HP to **living** members only. No softcap.
   Overflow above max morale banks into a **reserve pool** (cap RMAX=50) that
   absorbs spike damage before morale takes it. Provisions do not revive the fallen.

6. **Victory** = kill the resolve pool with ≥ 1 member standing. Grievous
   casualties are survivable; the cost is Houses of Healing time, not a loss.

7. **Defeat** = no one left standing (all downed or grievous = no DPS, no
   rescuer) **or** the duration cap is reached without the kill. The cap is a
   backstop, not the target.

---

## Physical Mitigation and Penetration

**effArmor** = `physMit × (1 - min(1, draughtPotency / PEN_SCALE))`

`PEN_SCALE = 80`. Penetration comes from the party's **draught** — a single
party-wide choice, not from character stats. Magic damage (Keeper, Captain's
Will half) bypasses armor entirely and is unaffected by physMit.

`draughtPotency` = the potency value of the equipped draught (0 if none). Entry-tier
potency draughts carry ~45 potency. Mid-tier ~65. No draught → full armor applies.

Stat-based penetration coefficients (the old PEN_COEF system) are removed.

---

## Food, Stats, and Hazards

### Food buffs stats directly

Every recipe has a **stat focus** (1–2 stats boosted strongly) that persists at
every tier. Higher tiers add **breadth** (minor boosts to other stats), but the
focus magnitude stays high — a focused low-tier recipe remains relevant for
min-maxing. HP/s is a **separate universal sustain layer** on top of stat boosts.

Old abstract buff vocabulary (Endurance, Agility, Acuity, Warmth, Luck) is
**retired.** Food now directly touches the five base stats:

- **Might** — melee damage, Warden penetration, Captain physical DPS
- **Agility** — Hunter DPS (dominant), armor penetration
- **Vitality** — Morale pool size (Vit × 16)
- **Will** — Keeper damage + heal magnitude, Dread resistance, Captain magic DPS, Inspiration
- **Fate** — two live mechanics: (1) boosts each member's Inspiration trigger rate (`base + Fat × 0.003`, cap 0.25); (2) grants spike evasion (`Fat × 0.004` chance to slip a spike entirely). Shadow drains Fate, making both effects weaker under pressure. No longer contributes to raw damage.

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

### Dread mechanics — Two-Layer Model

Dread operates on two separate layers. The old single-multiplier model is replaced.

**Effective dread and negation:**
```
effectiveDread = rawDread × (1 - min(1, willCut + hopeCut))
willCut  = min(1, captainWil / 100)   // 0 if Captain is grievous OR stunned
hopeCut  = min(1, hopeAntidote / 100)
```

**Layer A — Floor Drag (always present):**
```
layerADrag = min(1, effectiveDread × DREAD_VAR_COEF + rawDread × DREAD_FLOOR_COEF)
eff = raw × (1 - physAfter) × (1 - layerADrag)
```
The floor term (`rawDread × DREAD_FLOOR_COEF`) cannot be removed by any counter. Even a fully-prepped band fighting a high-dread encounter carries a faint drag. Armor and dread are separate multipliers — distinct code paths.

**Layer B — Action Denial (fires when negation is insufficient):**

Checked every `BREAK_CHECK_INTERVAL` ticks. Two severity gates:

| Gate | Condition | Effect |
|---|---|---|
| Stun | `willCut + hopeCut < T_TEMP` | Random standing member incapacitated for `STUN_TICKS`. Returns at their current HP. No wounds. |
| Break | `willCut + hopeCut < T_PERM` | Random standing member's will breaks — they flee. Gone for the fight. |

Targeting: random from standing, non-grievous, non-stunned, non-broken members. Captain has reduced targeting weight (`CAPTAIN_STUN_WEIGHT` for stuns, `CAPTAIN_BREAK_WEIGHT` for breaks). A stunned Captain's willCut = 0 that tick.

**Three zones:**

| Zone | Condition | Experience |
|---|---|---|
| Safe | negation ≥ T_TEMP | Layer A floor only. Faint drag. No action denial. |
| Danger | T_PERM ≤ negation < T_TEMP | Layer A full drag + stuns possible. Visible pressure. |
| Critical | negation < T_PERM | Layer A full drag + stuns + permanent breaks. Fight in serious danger. |

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

### Draughts (party-wide provisioning)

One draught choice per encounter, applied to all party members. Draughts carry
**potency** and/or **antidote** effects — distinct from food, which handles stats
and HP/s only.

- **Low-tier draughts:** one effect only (potency OR one antidote)
- **High-tier draughts:** up to two effects; secondary effect weaker than a
  dedicated single-effect draught. No draught ever carries three effects.

Antidotes are draught properties. Food no longer carries antidote effects.

In regions with both armor and a hazard, the player chooses: potency draught or
antidote draught. High-tier draughts can cover both at reduced efficiency.

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
- **Hunter — "Black Arrow":** killable = resolve chunk **18%**; un-killable =
  burns time off the survival clock. Rising capped chance as a fight trends to
  time-loss. **IP review before public ship** — direct Tolkien coinage.
  *(Previously 35%; reduced because Black Arrow fires in fights the player is
  already losing — a 35% chunk rescued fights that were decided by provisioning
  failure, which the design rejects. 18% pulls a close loss back to winnable
  without rescuing a bad one.)*
- **Might Hunter — "Bullroarer's Five-Iron":** same mechanical role as Black
  Arrow (rare, fight-deciding burst against a kill target) but Might-driven, for
  a Might-build captain/fighter rather than an Agility/Fate hunter. Fires a
  resolve chunk of **18%** against a killable target, or burns survival-clock
  time on an unkillable. Named for Bandobras "Bullroarer" Took knocking
  Golfimbul's head into a rabbit hole — lore-native deep-cut gag that stays in
  Tolkien register. **IP review before public ship.**
- **Captain — "Wrath, Ruin, and the Red Dawn":** party-wide HP burst (can
  revive downed members). Epic / Aragorn at the Black Gate feel.

### Fate and Inspiration rates

Each member's Fate stat contributes to their own Inspiration trigger rate:

```
trigger chance = min(0.25, base + memberFate × 0.003)
```

The cap (0.25) prevents Fate from crowding out every other factor at extreme
values. Shadow drains Fate, which softens all Inspiration chances simultaneously
— high-Shadow fights are harsher on morale *and* less likely to produce miraculous
reversals.

### Fate and spike evasion

Each standing member has a per-spike chance to slip a blow entirely:

```
evasion chance = memberFate × 0.004
```

A near-miss logs in green: `"[Name] slips the blow — Fate [value] (near miss)"`.
This is not a Warden guard — it fires on any standing target, including the Keeper.
Shadow drains Fate, reducing this protection over time. The design intention: high
Fate characters (Keeper, Captain) are naturally luckier; provisioning Fate-boosting
food amplifies this further.

### Inspiration Flavor Text

When an Inspiration fires, the player sees a short line describing what happened.
Register: plain, slightly archaic, understated, past tense, *observational* —
describes what happened in the world, not "you unleash…". Keep the Tolkien
narrator voice. **Multiple variants per Inspiration should be written** and
rotated so the hundredth proc doesn't read like the first.

Seed lines (expand when the full content pass is done):

- **Black Arrow** (Agility/Fate — decisive shot):
  > *One arrow remained, black-fletched and true. She drew it to her ear, and loosed.*

- **Bullroarer's Five-Iron** (Might — decisive blow):
  > *He swung with all the weight of the old Took in his arms — and somewhere, a head went flying clean off its shoulders.*

- **The Horn of Gondor** (Warden — rally / spike-soak window):
  > *A horn rang out, clear and high, and weary hearts remembered they were not yet beaten.*

- **Wrath, Ruin, and the Red Dawn** (Captain — turning of the tide):
  > *Light broke red over the rim of the world, and with it came hope unlooked-for.*

- **Laurelin's Grace** (Keeper — uncapped grievous-rescue):
  > *A warmth like the first spring of the world settled over them, and the wounded breathed easier.*

**Open choice:** keep wording stat-agnostic (reads cleanly whoever triggered it)
vs. name the member/role (more personal, needs a variant per role). Current
drafts lean slightly personal but stay flexible. Resolve during the narrative
content pass, not during combat-system build.

---

```
morale            = round(30 + Vit × 16)
stat(L)           = start + grow × (L - 1)
PEN_SCALE         = 80
RESCUE_CAP        = 5      // Keeper rescues per fight
WARD_CAP          = 3      // Warden lethal-spike guards per fight
GRIEVOUS          = 5      // wounds → grievous
rescue burst      = 40 + Wil × 4  (× burstmul)
food heal (sink)  = overflow above max morale → reserve pool (cap RMAX=50); reserve absorbs spikes before morale
RMAX              = 50     // reserve pool cap per member
SHADOW_FLOOR      = 0.55   // base floor with no Radiance
SHADOW_RATE       = 0.0011 // per-tick stat drain per point of shadow severity
Fate insp coef    = 0.003  // per Fate point added to Inspiration base rate (cap 0.25)
Fate evade coef   = 0.004  // per Fate point chance to slip a spike entirely
JITTER            = 0.10   // per-tick DPS variance ±10%; U(−J, +J) multiplied onto effective DPS
tick              = 1 second
// Dread two-layer model (all placeholder — validate in sim)
DREAD_VAR_COEF       = 0.008  // Layer A variable term coefficient
DREAD_FLOOR_COEF     = 0.003  // Layer A floor term coefficient (tied to rawDread — cannot be countered)
T_TEMP               = 0.65   // negation threshold below which Gate 1 stuns fire
T_PERM               = 0.35   // negation threshold below which Gate 2 breaks fire
STUN_TICKS           = 5      // duration of temporary incapacitation
BREAK_CHECK_INTERVAL = 30     // ticks between Layer B gate checks
CAPTAIN_STUN_WEIGHT  = 0.3×   // Captain's relative targeting weight in stun pool
CAPTAIN_BREAK_WEIGHT = 0.15×  // Captain's relative targeting weight in break pool
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

**Forward-compat note:** build the encounter loader to accept a
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
`resolve 40000 · drain 16 · spike 75 · spikeInterval 13 · physMit 0 · no statuses · durationCap 1500 (25 min)`

Cull fight (kill-to-win). **Unwinnable with no food** — the band bleeds out in minutes. Tuned with the independent member model (beta=0, decouple, sink). Four food tiers validated at band Lv1, 4000 runs:

| Cooking Level | HP/s | Win rate | Tier |
|---|---|---|---|
| CL1 | 5.0 | ~24% | T1 — risky |
| CL2 | 5.2 | ~48% | T2 — coin flip |
| CL3 | 5.4 | ~80% | FL3 — solid |
| CL4 | 5.6 | ~91% | FL4 — strong |

First Initiate food (6.0 HP/s, CL5) gives ~99%.

> **Note:** these win rates are pre-stat-bonus baselines (HP/s only, no role-matched
> stat bonuses applied). With role-matched food at FL1 (Hearthbread/Wanderer's Supper/
> Contemplative Tea/Ranger's Fare), Neekerbreekers FL1 ≈ 47%, FL2 ≈ 79%, FL3 ≈ 99%.
> The higher rates in `future/wishlist.md` (Fate Balance section) are the post-bonus
> validated values — both are correct, just measured differently.

**Model:** Independent health bars. Each member takes drain/4 with no redistribution on member loss. Each member rolls their own spikes independently. Reserve pool (RMAX=50) absorbs spike damage before morale takes it.

### Wolves in the Chetwood — recLevel 3
`resolve 60000 · drain 18 · spike 75 · spikeInterval 9 (mean, ±50% jitter) · physMit 0 · no statuses · durationCap 1500`

Demands you push into the Initiate tier — Hearthkeeper food cannot win. Validated
post-tier-table-fix at band Lv3 (unlock floor), 1000 runs: FL4(5.6 HP/s)=0%,
FL5(6.0)=7%, FL6(6.8)=57%, FL7(7.5)=91%, FL8(8.3)=99%. FL5 is the entry gate;
FL6 is the real threshold; FL7+ is comfortable.

### Goblin Incursion — recLevel 5 (teaches potency / Rung 1)
`resolve 68000 · drain 20 · spike 60 · spikeInterval 14 · physMit 35% · no statuses · durationCap 1500 (25 min)`

First armored encounter. Rung 1 — teaches potency. Two distinct failure modes:

1. **Bad food (Hearthkeeper, FL1–4) → death.** Drain overwhelms below Initiate — the band bleeds out before the clock runs. FL1: 100% defeat. FL4: 76% defeat, 24% stalemate (lucky spike runs survive).
2. **Good food (Initiate FL5+), no draught → stalemate.** The band survives but the armor wall stops the kill — 95% stalemate. Black Arrow fires in 98% of these fights as the last desperate push. Resolve sits at ~11% remaining when the clock expires.
3. **Initiate FL5 + entry potency draught (45) → T3 win (~74%).** The draught cuts effective armor enough to kill in time. 21% stalemates remain (tight fights where the clock runs out regardless). FL6+p45: ~100%.

Validated at band Lv5, 5000 runs, role-matched food (Warden: Hearthbread, Hunter: Wanderer's Supper, Keeper: Contemplative Tea, Captain: Ranger's Fare):

| FL | Potency | Win | Defeat | Stalemate |
|---|---|---|---|---|
| FL1 | 0 | 0% | 100% | 0% — die |
| FL4 | 0 | 0% | 76% | 24% — mostly die |
| FL5 | 0 | 0% | 5% | 95% — armor wall |
| FL5 | 45 | 74% | 5% | 21% — T3 solid |
| FL6 | 45 | ~100% | 0% | ~0% — comfortable |

**Per-band flavor (same numbers, different skin):**
- **Greycloaks:** goblin warband working the East Road east of Weathertop; intercepted at dusk. Region: Lone-lands.
- **Mithlost:** goblins probing down from the mountain passes above Celondim, three nights at the tree-line. Region: Ered Luin western woodland edge.
- **Undermarch:** mountain goblins hitting the outer approach to Thorin's Halls before dawn — a test of the defenses, not a raid. Region: Blue Mountains, Thorin's Halls approach.
- **Kingswake:** placeholder (home region not yet locked).

---

## Difficulty Onramp

Each step up the ladder adds **exactly one new question the fight asks.** The
region gates the question. Dread and Shadow are the last two — they cannot appear
in early content by construction.

| Rung | New question | Teacher foes | Region | Provisioning answer |
|---|---|---|---|---|
| 0 | Survive + win at all? | Midges, wolves, spiders, cave bats | Bree-land/Chetwood (Greycloaks); Ered Luin woodland (Mithlost); Blue Mountain tunnels (Undermarch) | HP/s food + damage |
| 1 | Pierce armor? | Mailed goblin incursion | Lone-lands (Greycloaks); Ered Luin passes (Mithlost); Thorin's Halls approach (Undermarch) | Potency draught |
| 2 | Weather the dice? | Warg packs, barghests | Misty Mtn passes | Feed fragile members; respect Keeper's 5 rescues |
| 3 | Have the region's antidote? | Huorns, midge-marsh, cold passes | Old Forest, Midgewater, Caradhras | The one hazard food (Alert / Hale / Warmth) |
| 4 | Endure when you can't win? | Old Man Willow, blizzard | Old Forest, high passes | All-sustain; the survival mindset |
| 5 | Fight while afraid? | Barrow-wights, the Dead | Barrow-downs, Dead Marshes | Hope food + Captain Will |
| 6 | Fight while the light fails? | Great spiders, Balrog, the Nine | Mirkwood, Moria, Morgul-vale | Radiance |
| 7 | All at once. | Sieges, combined raids | Fornost, the Black Gate | Multi-stage prep, the full toolkit |

**Current build is Rung 0 only.** Rungs 0–4 are all single-stage fights. Multi-stage
journeys and gauntlets appear first at Rung 7.

Early encounter variety before Dread/Shadow comes from **foe shape** (swarm,
tank, endure) crossed with the four "safe" environmental hazards. Five-plus
region-bands of variety before a player ever meets fear.

### Per-band Rung 0 encounter roster

Each band has its own Rung 0 pair matched to their home region. Same mechanical
role as Neekerbreekers (swarm/attrition) and Wolves (spike-heavy), different skin.
Numbers are **not yet tuned** — use Neekerbreekers and Wolves as the tuning target
for feel; validate per-band encounters before the combat system ships.

| Band | Rung 0a (swarm/drain — Neekerbreekers slot) | Rung 0b (spike-heavy — Wolves slot) |
|---|---|---|
| **Greycloaks** | Neekerbreekers at the Marsh's Edge *(validated)* | Wolves in the Chetwood *(validated)* |
| **Mithlost** | Midges at the forest margin (Celondim woodland edge, Ered Luin) — `mithlost_midges` | Wargs on the mountain slopes above Celondim — `mithlost_wargs` |
| **Undermarch** | Cave bats in the lower halls (Blue Mountain tunnels — swarming, claustrophobic, deep-dark) | Mountain wolves on the outer passes above Thorin's Halls |
| **Kingswake** | TBD — home region not yet locked | TBD |

**Design note:** the Undermarch bats are creatures, not enemies with intent — tonally
distinct from the goblin raid that follows. The first fight is deep underground and
passive; the second is an armed assault at the gates. Same progression shape, very
different feel.

---

## Multi-Stage Encounters (campaign destination, not yet built)

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

Active design questions that need resolution before the relevant system is built.
Deferred items and polish tasks live in `future/wishlist.md`.

1. **All magnitudes are placeholders** — stat growth, mitigation per bestiary
   tier, wound count (~5), Keeper burst size + the 5-cap, Inspiration base
   chances, spike potency/interval, drain-vs-sustain margins, severity per-point
   scales. Validate after each tuning pass in the sim.
2. **Hazard taxonomy** — fleshed out as regions are designed. Each new region
   locks in which hazard it carries and adds it to the encounter ladder.
3. **Rung-3 first-hazard fork** — which environmental hazard does the player
   meet first: Cold→Warmth (most intuitive) or Old Forest's Wakefulness (most
   geographically natural step out of the Shire)?

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
