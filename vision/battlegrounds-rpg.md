# HearthCraft — Battlegrounds: The Raid RPG (Design Scratchpad)

> SCRATCHPAD. NOT V1. NOT V2. Long-term destination — realistically V5+.
> The endgame "game mode": a party combat RPG on top of the provisioning game,
> fuelled by it. Captured in full so the vision is preserved and coherent.
> Do NOT build from this without promotion to docs/. Nothing here changes V1.
>
> Companion files: battlegrounds.md (lore roster), bestiary.md (enemy tiers).

---

## The One-Sentence Vision

HearthCraft is two games sharing an economy: a provisioning idle game (gather,
cook, discover, manage a band) that *fuels* a party combat RPG (stats, gear,
roles, damage types, wounds, permadeath) fought across a roster of lore-accurate
battlegrounds, where the player — the warlock-culinarian — is the indispensable
support that makes the impossible survivable.

The connective tissue: **your cooking is the raid's sustain mechanic.** The band
eats continuously through an hours-long raid. Provisioning is logistics, not a
pre-battle buff. This is the original idea that makes the two halves one game.

---

## Scope Honesty (read every time)

This is a second full game. It only exists if (1) the V1 provisioning loop ships
and is fun, (2) alchemy + multi-member provisioning land (V2–V3), (3) the builder
has real Kotlin fluency. If the core cooking loop isn't fun, none of this matters
— nobody reaches it. The fastest path to this RPG is shipping the small game.

---

## How a Battleground Plays — Resolution Model

**Provision, then resolve.** The player's agency is in PREPARATION and
COMPOSITION; the raid then resolves over real-world time (hours), narrating
itself via a live event log. This fits the idle genre: provision, send, come
back later. NOT a turn-by-turn tactics game the player micromanages.

### The Two-Pool Model (the chosen combat math)
A segment is a race between two pools:
- Enemy force has a segment HP pool `E`.
- Party has a survivability pool `P_effective` (raw HP stretched by healing and
  by how long provisions last).
- Party deals `D_party` per tick; enemy deals `D_enemy` per tick.
- You WIN if kill-time `E / D_party` < wipe-time `P_effective / D_enemy`.

All four pillars live in this one inequality:
- **Damage role** → raises `D_party`.
- **Tank role** → raises `P_effective`.
- **Healer role** → raises `P_effective` (sustain over time).
- **Provisioning/sustain** → feeds both `D_party` and `P_effective`, AND food
  RUNS OUT: if the segment outlasts provisions, the back half runs at reduced
  values. This is the supply-train mechanic expressed in math.

This is the build target. It can later be upgraded to a discrete-actor sim
(enemies fall one by one) BEHIND THE SAME provision-then-resolve interface,
without a rewrite, because both produce the same shaped output (a resolved raid
+ log + casualties).

### The Live Log
An hours-long committed raid needs to be watchable but not demanding. The raid
narrates itself as a timestamped event feed against the raid clock:
- "02:48 — Borin takes the brunt of a wight's chill. He holds." (tank working)
- "03:30 — Provisions running low. Three hours of food left, four of barrow
  ahead." (sustain tension surfacing)
- "04:02 — Cael falters. Mira pulls him back from the dark." (near-casualty)
Checking in is rewarding, never required. The log is ALSO where after-action
diagnostic clues come from (see Clue System).

### Bonfires / Respites (Dark Souls-style)
The longest battlegrounds are SEGMENTED by respite points. Fight to the respite,
then: (1) restock provisions for the next segment, (2) inspiration resets, (3)
optionally heal/swap. A respite turns a 6-hour bar into e.g. three 2-hour
segments — far more legible and dramatic. Short battlegrounds (Barrow-downs) have
ZERO respites: one deployment, one shot. Respite count is a length/difficulty
dial. Inspiration is once PER SEGMENT (a no-respite battleground = one segment).

---

## The Four Pillars of a Successful Raid

All must be satisfied; difficulty is that the requirements are HIDDEN (see Clue
System).

1. **Composition (roles)** — needs at least a tank and a healer, plus damage.
   PLUS the Captain role (see Inspiration). Implies members have combat roles.
2. **Stats & Strength** — members have stats/levels gating eligibility.
   CRITICAL IDENTITY CONSTRAINT: member growth must route through the PLAYER's
   provisioning and care, never independent training. If members self-improve,
   the player stops being indispensable and the game's soul breaks.
3. **Gear & Damage Types** — weapons/armor with damage types; enemies have
   vulnerabilities/resistances. (Barrow-wights: fire, light, blades of
   Westernesse — canonical.) NOTE: this is the cut IdleFantasy type system
   returning in focused, endgame-only form. Study IdleFantasy's melee/ranged/
   magic style-triangle + equipment-stat code as the reference pattern.
4. **Provisioning as Sustain (THE STAR MECHANIC)** — food is consumed over the
   raid's duration; you need ENOUGH QUANTITY, not one dish. Run out → casualties.
   Turns cooking into "stock a supply train for a campaign." The bridge between
   the two games.

---

## The Clue System (discovering hidden requirements)

Requirements are never stated. Three layers, increasing explicitness:
1. **Environmental** — the battleground description evokes the answer (Barrow-
   downs reads cold, draining, deathly → infer warmth/vitality).
2. **Diagnostic** — failed/partial runs return after-action text naming the
   NATURE of failure, not numbers ("The cold sank into them. Aldric could not
   get warm.").
3. **Band voice** — members comment before/after ("It pulls the heat out of
   you.").
Strength numbers and quantities are never told; the player converges by
attempting. Lore engagement rewarded, not required. Each solved battleground is
a kept discovery (a battleground journal, parallel to the recipe book).
Difficulty scales the clue puzzle: early = one clear requirement; later = several
conditions solved together.

---

## Inspiration (the comeback mechanic)

Inspiration is the Tolkien theme made mechanical: at the brink, a fighter becomes
borderline supernatural (Beren & Lúthien in Angband; Éowyn & Merry vs the
Witch-king; Sam carrying Frodo; Boromir alone against a host; Gandalf vs the
Balrog). Inspired units are NIGH-UNBEATABLE — but NOT absolute.

### Trigger: the FORECAST of defeat, not accumulated death
CRITICAL DESIGN FIX. Inspiration fires when the resolution PROJECTS a loss
(wipe-clock will beat kill-clock) — which can happen BEFORE anyone dies. This
decouples "experiencing inspiration" from "burying characters to get there,"
solving the death-spiral problem. You feel the dread of imminent defeat;
inspiration answers it; you come back without a graveyard.

### Effect: a power-gap TRANSFORMATION (not small multipliers)
Inspiration is a large state-change, scaled by the captain-vs-enemy power gap and
by enemy TIER (see bestiary.md). Against beatable tiers it roughly:
- party `D_party` ×2–3, party `P_effective` ×2–2.5, enemy `D_enemy` ×0.5–0.6.
Math note (worked example): even from a ~2.4× losing brink these turn the fight
into a rout — that's the "nigh-unbeatable" feel. From a CATASTROPHIC brink
(~6× losing) even this only makes it close — so inspiration reliably rescues
NORMAL brinks and is a desperate chance from hopeless ones. The ~10% failures
cluster naturally at the worst positions; no arbitrary dice needed. Variance
(±15–20%) does the rest.

### Tier interaction (see bestiary.md for full tiers)
- Rabble → routed. The Fell → defeated. Banes → only a peak captain reaches
  them, at cost. the Nine → never killed, only repelled, even by inspiration.
  Nameless → not a combat target at all.
- Mixed field: inspiration clears the Rabble/Fell but leaves a Nine wraith
  untouched — changes the fight's SHAPE without solving it.

### Not absolute — two ways it can still fail/cost
1. Can't salvage a truly hopeless (catastrophic-margin) fight.
2. The Boromir clause: even an inspired VICTORY can cost the inspirer — the deed
   that takes the hero. Suggested: chance scales with how hopeless the brink was
   (deeper miracle = likelier to kill the one who performs it).

### Once per segment; secret levers raise trigger odds
Once per deployment/segment (respites reset it). Inspiration is RARE and must
never become a reliable cooldown — it should feel like grace. HIDDEN levers
raise trigger chance AND/OR magnitude (discovery-through-attention, like clues):
- Captain's own level/power.
- Bonds between long-companions (rewards a stable party; permadeath frays bonds
  → another reason loss hurts).
- Desperation depth (darker = likelier — "the dawn comes at the last").
- Named legendary gear with a history.
- Lore/location resonance (a Dwarven captain on Dwarven ground).
Even stacked, these turn a rare miracle into a slightly-less-rare miracle. The
ceiling stays low.

### NOTE: inspiration is the FIGHTERS', not the chef's
Food does NOT fuel inspiration. The courage is the band's own — the one thing
purely theirs. The player is why they're standing there at all, but the rally is
theirs. (Captain role below is the inspirer.)

---

## The Captain Role (breaks the holy trinity)

A FOURTH role beyond tank/healer/damage: the Captain, whose function is
inspiration (LotRO Captain — the rally/leader who makes everyone better). Adds a
new composition axis: do you have someone who can turn a losing raid? Makes a
Captain member PRECIOUS — you'll guard them, dread their wounds, weigh risking
them. Captain POWER gates which enemy tiers their inspiration can affect (rout
Rabble → defeat Fell → reach Banes at peak → help repel the Nine; never kill the
Nine). A long power horizon: most players never reach the peak that can contest a
Bane, which is right for things that end ages of the world.

OPEN: can ANY member have a rare inspired moment (Sam was a gardener), with the
Captain merely making it far likelier? And can the WHOLE company catch fire at
once (the Rohirrim at cockcrow) vs. a single hero rising? Army-wide = rarer, more
powerful; single-hero = more frequent, more personal. Undecided.

---

## Wounds, Healing & Permadeath

### Difficulty ≠ lethality (the key reframe)
Battlegrounds are hard to WIN; failing usually means RETREAT with wounds, not
death. Permadeath is the rare punctuation at the end of a long chain of
"couldn't pay a cost in time," not the standard tax on every hard fight. This is
what lets battlegrounds be brutal without emptying the roster.

### Two wound tiers
- **Ordinary wounds** — heal with time and provisioning (warm/recovery food —
  back in the player's domain). Cheap, slow, the everyday cost of pushing hard.
- **Grievous wounds** — deep, near-death; CANNOT heal the ordinary way; tick
  toward permadeath if untreated. Recoverable only via the Houses of Healing
  chain below.

### The Houses of Healing — recovery requires ALL of:
1. **The Houses unlocked** — a progression milestone; until then, grievous =
   death. One of the most important early-campaign goals.
2. **A healer of sufficient "beastliness"** — healers LEVEL; their power gates
   how deep a wound they can mend. A wound too deep for your current healer is
   curable-in-principle but lethal-in-time. Leveling your healer raises the
   ceiling on what your whole roster can survive → an organic gate on pushing
   into deadlier content.
3. **Rare herbs/materials, ALTERED by craft** — raw herbs do NOT heal (athelas
   is a worthless weed until the right hands prepare it). The cure is a
   DISCOVERED-AND-MASTERED alchemical preparation (poultice/draught), exactly
   like recipe discovery + mastery. A freshly-discovered poultice is crude; a
   mastered one is potent enough for grievous wounds. THIS IS ALCHEMY'S ULTIMATE
   PURPOSE and keeps the player indispensable in healing WITHOUT being the
   healer: the healer can't heal without the player's mastered preparation; the
   preparation can't heal without the healer. Two indispensable roles, neither
   sufficient alone. Mastery now has life-or-death weight.
4. **A LOT of time** — recovery is long; the wounded character is OUT OF THE WAR
   for a meaningful stretch; you fight short-handed. The time cost is what makes
   a grievous wound a real strategic event, not a quick detour.

### Healing CANNOT fail, but is HARD TO ACHIEVE
If a character reaches the Houses and you can pay all four costs, they recover —
GUARANTEED. The tension is entirely upstream (getting there, affording it, having
the means), like Aragorn in the Houses: the drama is whether the king comes in
TIME, not whether his hands work. Permadeath happens when a grievous wound goes
untreated because you couldn't pay a cost in time — a death the player feels
responsible for, because there WAS a door and they couldn't open it.

### Healers can't heal themselves → protect them
A healer cannot heal their own grievous wounds. Consequences:
- A lone healer is a single point of failure; a serious campaign wants TWO
  healers as mutual insurance (they guard each other — the only one who can save
  the other). Recruiting/developing a second healer is a real strategic
  aspiration, possibly a gate toward the hardest content.
- The whole party organizes protectively around the healer: the tank keeps
  enemies off them; provisioning keeps their survivability high. One rule
  (no self-heal) radiates into composition and supply.

### Herb sourcing
Raw herbs come from SAFE gathering and as rewards (a tiered mix is fine: common
restoratives from safe ground; rarer materials as rewards). But they're inert
until ALTERED — see #3. The value is in the discovered/mastered preparation, not
the raw herb.

### Retirement to Hireling (the escape valve)
If too attached to a member to risk them, RETIRE them to a safe role (the
IdleFantasy inn-hire pattern): contributing without raiding, alive. Cost: it's
permanent/near-permanent — you lose them as a raider. The decision: keep your
best member in the rotation where they might die, or pull them to safety and lose
their strength on the front. Humane pressure-release that is itself a meaningful
choice.

---

## Rewards & The Influence Map

- **First clear:** the discovery payoff + notoriety toward the next unlock + a
  one-time reward + a visible push on the influence map.
- **Repeat clears at higher difficulty:** rare ingredients unavailable elsewhere,
  scaling with tier. Solved battlegrounds become a rare-ingredient farm with
  rising difficulty.
- **Named Legendaries:** rare gear includes named, flavored, KEPT-FOREVER,
  UPGRADEABLE items. A weapon with a name and history, carried by a specific
  member across raids, becomes a story. Canonically rich (barrow-blades of
  Westernesse, named Dwarven weapons, Elvish blades). Gear can BE lore. Also
  feeds the inspiration levers (a legendary with history raises rally odds).

### The Influence Map (emotional core — arguably the mode's most important feature)
A map of Middle-earth where SHADOW RECEDES and LIGHT RETURNS as battlegrounds are
cleared. The reward that isn't a number — the world visibly healing because of
the player. Turns the roster from a menu into a WAR. When a battleground is LOST,
the cost can be lost GROUND (shadow re-spreading) rather than lost characters —
the punishment that stings without emptying the roster.

---

## Progression & Unlocking

Not a flat level gate — a narrative reputation arc:
1. **Establish the band** as genuinely helpful to the free peoples (early
   core-game play earns standing).
2. **Skirmishes** — a tier of lower-stakes engagements BELOW true battlegrounds,
   to build the crew's ability/gear and teach the raid systems. (Skirmishes can
   use the simplest resolution — a stat/style/gear threshold, ~IdleFantasy-level
   combat — while true battlegrounds use the two-pool model.)
3. **Battlegrounds proper** unlock by notoriety thresholds. Unlocks are
   UNANNOUNCED and story-flavored — a band member mentions something, and the new
   battleground is simply THERE. Discovery is part of the reward.

Concrete early spine of milestones: unlock the Houses of Healing; develop a
healer of sufficient beastliness; ideally recruit a SECOND healer; discover and
master the healing poultice — each gates safe entry into deadlier content.

### The Morality Question (a real fork — unresolved)
Hint: bands may need to "straighten out their morals" (dwarves over-focused on
gold/mining) to earn trust. Two readings:
- FLAVOR: every band runs the same "prove yourself" arc, voiced differently.
  Consistent with "bands are flavor only."
- MECHANICAL: bands start at different moral/reputation positions with different
  work to earn trust. Breaks "no mechanical difference," but only in the
  battleground meta-game.
- Possible clean split: who you are doesn't change how you COOK (core loop stays
  flavor-only), but it changes what you must PROVE to march to war.

---

## First Implementation (whenever this is built)

Start with ONE battleground: **Cleansing of the Barrow-downs** (see
battlegrounds.md). Reasons: simplest type (a cleansing, undead, fixed ground, no
army/pincer/multi-front), thematically self-contained, validates the whole mode
(unlock, clue system, requirement, sustain, rewards, influence map) at lowest
risk, and is a safe sandbox for the open mechanical questions. If it's fun,
patch in the rest as content drops (the "seasons" cadence). Build the resolution
as the two-pool model; do NOT start at a discrete-actor sim.

---

## The Through-Lines Worth Protecting

Whatever gets cut/simplified when built, these make it special and must survive:
1. **Cooking IS the raid's sustain** — the two games are one because of this.
2. **The influence map** — the world healing as the reward.
3. **Hidden requirements + layered clues** — discovery extended into the endgame.
4. **Healing IS mastered alchemy** — the cure is a discovered/mastered
   preparation; ties the warlock identity to life-and-death.
5. **The player stays indispensable** — member growth and healing both route
   THROUGH the player; never let members become self-sufficient.
6. **Inspiration belongs to the fighters** — not fuelled by food; the band's own
   courage; nigh-unbeatable but not absolute (the Boromir clause).
7. **Lore-grounded, canon-correct bestiary** — the five tiers (Rabble, the Fell,
   Banes, the Nine, Nameless Things), power and untouchability kept separate, the
   Nine never killed.
