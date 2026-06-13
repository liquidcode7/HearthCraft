# HearthCraft — Battlegrounds: The Raid RPG (Design Scratchpad)

> SCRATCHPAD. NOT V1. NOT V2. Long-term destination — realistically V5+.
> This describes the endgame "game mode": a party-based combat RPG that sits on
> top of the provisioning game and is fuelled by it. Captured in full so the
> vision is preserved and coherent. Do NOT build from this without promotion to
> docs/. Nothing here changes the V1 plan.
>
> Companion file: battlegrounds.md (the lore-grounded roster of scenarios).

---

## The One-Sentence Vision

HearthCraft is two games sharing an economy: a provisioning idle game (gather,
cook, discover, manage a band) that *fuels* a party-based raid RPG (stats, gear,
roles, damage types, wounds, permadeath) fought across a roster of lore-accurate
battlegrounds, where the player — the warlock-culinarian — is the indispensable
support that makes the impossible survivable.

The connective tissue: **your cooking is the raid's sustain mechanic.** The band
eats continuously through an hours-long raid. Provisioning is logistics, not a
pre-battle buff. This is the original idea that makes the two halves one game.

---

## Scope Honesty (read this every time)

This is a second full game. People ship party-raid RPGs as their entire product
and it takes years. It only exists if:
1. The V1 provisioning loop ships and is fun.
2. Alchemy and multi-member provisioning land (V2-V3).
3. The builder has real Kotlin fluency.

If the core cooking loop isn't fun, none of this matters — nobody reaches it.
The fastest path to this RPG is shipping the small game first.

---

## How a Battleground Plays (the resolution model)

**Provision, then resolve.** Not a played-out real-time fight the player
micromanages turn by turn — the player's agency is in *preparation and
composition*, and the raid then resolves over real-world time (hours).

The loop:
1. **Unlock** the battleground (see Progression below).
2. **Discover the requirement** through layered clues (see Clue System).
3. **Compose the party** — satisfy role needs (tank, healer, damage), stat
   thresholds, and effective damage types.
4. **Stock provisions** — cook ENOUGH of the right food to sustain the party
   for the full duration. Running out mid-raid = the back half is unprovisioned
   = people fall.
5. **Send and wait** — the raid runs in real time (hours), fitting the idle
   genre: provision, send, come back later.
6. **Resolve** — success/failure, casualties, rewards, after-action clues, and
   a visible push on the influence map.

Hard by design. "Not for the faint of heart."

### Open question (still unresolved)
Within "provision then resolve," does an escalation event (the ~1-2% ancient
evil) allow any mid-raid player response, or is the outcome fully locked at
send time? Locked is simpler and probably right for the first build.

---

## The Four Pillars of a Successful Raid

A battleground checks four things. All must be satisfied. The difficulty is that
the *requirements are hidden* and must be discovered.

### Pillar 1 — Composition (roles)
A valid party needs at least one **tank** (absorbs) and one **healer**
(sustains), plus **damage** dealers. The game may not surface these words, but
members function as roles. Implies band members have a **combat role/class
identity**, which they currently lack.

### Pillar 2 — Stats & Strength
Members have stats/levels that gate whether they're strong enough for a given
battleground.
- CRITICAL IDENTITY CONSTRAINT: member growth must come from **the player's
  provisioning and care**, NOT from members training independently. The whole
  game is that the band is nothing without you. If members just grind their own
  stats, the player stops being indispensable. Resolve how growth works so it
  routes through the player. (E.g. better food/preparation permanently improves
  members; gear the player provides; alchemical enhancement.)

### Pillar 3 — Gear & Damage Types
Members carry weapons/armor with **damage types**, and enemies have
**vulnerabilities and resistances** (a type-effectiveness layer).
- Barrow-wights: vulnerable to fire, light, and blades of Westernesse
  (canonical — the hobbits' barrow-blades were made to harm exactly this enemy).
- NOTE: this is the type system that was cut from the early IdleFantasy
  direction, returning in a focused, endgame-only form. That's fine — but
  recognize it's back, and keep it contained to battlegrounds.

### Pillar 4 — Provisioning as Sustain (THE STAR MECHANIC)
Food is consumed continuously over the raid's duration. You need **enough
quantity** of the right food, not one dish.
- Run out mid-raid → unprovisioned second half → casualties.
- Turns cooking from "make one buff dish" into "stock a supply train for a
  campaign." Ties the entire core loop (gather + cook) directly to the endgame.
- This is the mechanic to build the system around. It's original, it's
  thematically perfect for a cook-hero, and it's the bridge between the two
  games.

---

## The Clue System (discovering the requirement)

Requirements are never stated outright. The player deduces them. Three layers,
increasing explicitness:

1. **Environmental** — the battleground's description is written to evoke the
   answer. The Barrow-downs reads as cold, draining, deathly; a thoughtful
   player infers "warmth/vitality" before sending anyone.
2. **Diagnostic** — a failed/partial run returns after-action text naming the
   *nature* of the failure, not the numbers: "The cold sank into them. Aldric
   could not get warm."
3. **Band voice** — members comment before and after from their own read: "I
   don't like this place. It pulls the heat out of you."

Strength numbers and exact quantities are never told — the player converges by
attempting. Lore engagement is rewarded but not required; trial-and-error
reaches the same knowledge via diagnostic clues. Each solved battleground is a
discovery the player keeps (a battleground journal, parallel to the recipe book).

Difficulty scales the clue puzzle: early battlegrounds have one clear
requirement; later ones layer several conditions that must be solved together.

---

## Casualties, Wounds & Permadeath

- A failed or costly raid can put members **down** — one, two, three at a time.
- Downed members can be **healed and recover a LIMITED number of times** (a
  wound system — each fall deepens a wound / spends a "life").
- Exhaust the limit → **permanent death.** Gone for good.
- This makes failure sting without being instantly catastrophic, and makes
  watching a beloved member's wound count a real source of dread.

### The Hired-Hand escape valve
If the player is too attached to a member to risk them, they can **retire them
to a safe role** (the IdleFantasy inn-hire pattern) — contributing without
raiding.
- The cost: retiring should be permanent or near-permanent. You lose them as a
  raider. Otherwise it's a cheap "bench during danger" toggle.
- The decision it creates: keep your best member in the rotation where they
  might die, or pull them to safety and lose their strength on the front.
- Humane pressure-release that is itself a meaningful choice.

---

## Rewards

A ladder, first-clear vs repeat:

- **First clear:** the discovery payoff (you solved the puzzle) + notoriety
  toward the next unlock + a one-time reward + a visible push on the influence
  map.
- **Repeat clears at higher difficulty:** rare ingredients unavailable
  elsewhere, scaling with tier. Solved battlegrounds become a rare-ingredient
  farm with rising difficulty.

### Named Legendaries
Rare gear includes **named legendary items** with flavor text, kept forever and
**upgradeable**. A weapon with a name and history, carried by a specific member
across many raids, becomes a story the player is invested in. Canonically rich:
barrow-blades of Westernesse, named Dwarven weapons, Elvish blades. The gear can
*be* lore.

### The Influence Map (the emotional core)
A map of Middle-earth where **shadow recedes and light returns as battlegrounds
are cleared.** This is the reward that isn't a number — the world visibly
healing because of the player. It turns the roster from a menu into a *war*.
Arguably the most important single feature of the whole mode: it gives "beating
back the darkness" a visual home. Build toward this.

---

## Progression & Unlocking

Not a flat level gate. A narrative reputation arc:

1. **Establish the band** as genuinely helpful to the free peoples of
   Middle-earth (early core-game play earns standing).
2. **Skirmishes** — a tier of lower-stakes engagements BELOW the true
   battlegrounds, existing to build the crew's ability and gear and to teach the
   raid systems. The difficulty ramp and the "prove yourself" phase made
   playable.
3. **Battlegrounds proper** unlock by notoriety thresholds earned across
   skirmishes and the roster. Unlocks are **unannounced and story-flavored** — a
   band member mentions something, and the new battleground is simply *there*.

### The Morality Question (a real fork — unresolved)
The player hints that bands may need to "straighten out their morals" — e.g.
dwarves overly focused on gold and mining — as part of earning trust. Two
readings, very different games:
- **Flavor:** every band runs the same "prove yourself" arc, just *voiced*
  differently (the dwarven version flavored around overcoming greed). Consistent
  with the established "bands are flavor only, no mechanical difference."
- **Mechanical:** bands start at different moral/reputation positions with
  different work to earn trust. Makes band choice mechanically meaningful for
  the first time — breaks "no mechanical difference," but only in the
  battleground meta-game.
- Possible clean split: who you are doesn't change how you COOK (core loop stays
  flavor-only), but it changes what you must PROVE to march to war (battleground
  meta-game can diverge). Decide when the time comes.

---

## What This Adds On Top of the Base Game (build inventory)

A rough catalogue of systems this endgame requires that the provisioning game
does NOT already have. Sobering on purpose:

- Member combat stats + growth (routed through the player, per the identity
  constraint)
- Member roles/classes (tank/healer/damage)
- Gear system (weapons/armor, slots, stats)
- Damage types + enemy resistance/vulnerability matchups
- Named legendary items + upgrade system
- Wound/life tracking + permadeath + healing-recovery limits
- Hired-hand retirement system
- Battleground definitions (requirements, duration, clues, rewards, escalation)
- Hidden-requirement + layered-clue authoring per battleground
- Long-duration raid resolution engine (provision-then-resolve over hours)
- Provisioning-as-sustain consumption model (quantity over time)
- Skirmish tier (lower-stakes pre-battleground content)
- Notoriety/reputation progression + unlock gating
- Influence map (state + visualization of shadow receding)
- Battleground journal (parallel to recipe book)
- (Possible) per-band morality/reputation arcs

Each line is real work. Together they're a second game. That's the point of
writing it down: so the size is honest and the sequencing stays disciplined.

---

## The Through-Lines Worth Protecting

Whatever gets cut or simplified when this is eventually built, these are the
ideas that make it special and should survive:

1. **Cooking IS the raid's sustain.** The two games are one because of this.
2. **The influence map** — the world healing as the reward.
3. **Hidden requirements + layered clues** — discovery extended into the endgame.
4. **The player stays indispensable** — member growth routes through the player;
   never let members become self-sufficient.
5. **Lore-grounded battlegrounds** with canonically-correct threats and the
   Glorfindel thread (Gondolin → Fornost) that rewards deep fans.
