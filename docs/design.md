# HearthCraft — Game Design Document

> Status: Living document. Update as decisions are made.
> Last revised: June 16, 2026 (post-redefinition audit)

---

## What This Game Is

An offline Android game set in high fantasy. The player is the indispensable
provisioner behind a roving band of fighters. They never fight. They gather,
grow, cook, and sustain. Without their craft, the band is nothing.

The player's title is **[PLACEHOLDER — not yet decided]**.

The line between a perfect dish and a potion is a matter of intent. Food is
borderline magical — not metaphorically. Ingredients have power; preparation
unlocks it; feeding someone the right thing at the right moment changes what
they are capable of. The band doesn't know if it's cuisine or sorcery. They
don't care. It works.

This is not a cozy cooking sim. It is a specialist identity fantasy rooted in
craft and deep knowledge. The long-term destination is a full raid RPG fought
across named battlegrounds from Middle-earth's history, with this provisioning
game as the indispensable foundation. V1 proves the foundation feels good.

Design inspirations:
- **GW2** — crafting tier structure, recipe discovery, ambient tone
- **FFXIV** — specialist identity, dedication has visible reward
- **LotRO** — high fantasy grounding, mastery through repetition, the
  Ettenmoors as the endgame model

---

## The Core Loop

```
Gather ingredients (Farm/Garden or Forage/Wild session)
        +
Cook food (Cooking session — select recipe, run in background)
        +
Send band on mission (runs in parallel)
        ↓
Band returns: money + rare regional ingredients
        ↓
Better ingredients unlock better recipes → stronger provisions
        ↓
Stronger provisions → band attempts harder missions → rarer ingredients
        ↓
repeat
```

Gathering, cooking, and missions all run simultaneously. The game never
forces the player to wait on one thing before starting another.

---

## Skill Categories

Two categories: **Gathering** and **Cooking**. Each has two tracks.
One session per category at a time. Missions run independently in parallel.

---

### Gathering

One shared skill level across both modes. The player chooses which mode to run
each session. Both contribute XP to the same Gathering pool.

#### Farm / Garden
- Cultivated ingredients: vegetables, fruits, grains, herbs
- Predictable output — the player knows roughly what they will get
- Beekeeping unlocks at higher levels: honey → royal jelly → rare cultivars
- A maxed gardener produces things that cannot be reliably found anywhere
  else — a distinct long-term specialist identity

#### Forage / Wild
- Uncultivated ingredients: wild herbs, mushrooms, bark, roots, unusual plants
- Unpredictable output — occasionally surprising, sometimes lean
- Primary source for unusual and alchemical inputs (V2+)
- Rare finds possible at any level; more frequent at higher levels
- Neither track obsoletes the other

---

### Cooking Skill

The primary loop. Produces food and preparations that buff the band.

#### Skill Tiers (GW2 structure — seven tiers)

| Tier | Title        | Notes                                      |
|------|--------------|--------------------------------------------|
| 1    | Hearthkeeper | Starting tier. Basic recipes, simple food. |
| 2    | Initiate     | Unlocks more ingredient combinations.      |
| 3    | Apprentice   | Grimoire-gated recipes begin to appear.    |
| 4    | Journeyman   | Mid-game. Preparations grow complex.       |
| 5    | Adept        | Grimoire-gated. Stronger preparations.     |
| 6    | Master       | Late game. Deep craft identity.            |
| 7    | Grandmaster  | Ceiling. Grimoire-gated apex recipes.      |

Tiers gate recipe complexity, not access to the game. The player progresses
through tiers by accumulating Cooking XP. Each tier requires meaningfully more
XP than the last.

**Grimoires** — rare mission drops that unlock the deepest recipes within tiers
3, 5, and 7. Finding one is a meaningful event. They cannot be bought or crafted.

**Specialization trees** — deferred to post-max-level content (V2+, wishlist).
V1 has one linear cooking progression with no branches.

#### Recipes (V1)
- V1 uses hand-coded recipes only — no discovery system yet (V2)
- Starting set: 8–10 recipes, visible in the recipe book from the start
- V1 uses a simplified buff model (see below); the full stat-based model
  is implemented in V2+ alongside the combat system

#### Food & Buffs — Full Model (V2+)

Every recipe has a **stat focus** (1–2 stats boosted strongly) that persists
at every tier. Higher tiers add **breadth** (minor boosts to other stats), but
the focus magnitude stays high — a focused low-tier recipe stays relevant for
min-maxing. HP/s is a **separate universal sustain layer** on top of stat boosts.

Food boosts the five base stats directly (Might / Agility / Vitality / Will /
Fate) plus HP per second. "Cook the right dish for the right member" is the
provisioning puzzle. The old abstract buff vocabulary is **retired.**

Six **hazard antidotes** are secondary effects carried by specific recipes:

| Hazard | Antidote | What it counters |
|---|---|---|
| Cold | Warmth | Morale drain |
| Heat | Heat-ease | Morale drain |
| Disease | Hale | Morale drain |
| Wakefulness | Alert | Morale drain |
| Dread | Hope | DPS suppression |
| Shadow | Radiance | Will + Fate drain |

Hazards are properties of **regions** — the clue system telegraphs which
hazard a region carries before the player faces it. See `docs/combat-model.md`
for the full mechanical spec.

#### Food & Buffs — V1 Simplification

V1 uses a single **buff strength** number (stronger food → better mission odds)
rather than the full stat model. This lets the core gather→cook→provision loop
be playable before the combat system is built. V1 buff strength scales with
Cooking level:
- Simple recipes (30-min cook): base 10, +0.4 per level
- Complex recipes (90-min cook): base 12, +0.65 per level

V1 missions use buff strength vs. a vitality threshold to determine success
probability. This layer is replaced when full combat lands.

#### Food Flavor Tags
Every recipe has a flavor tag: sweet, hearty, light, spicy, herbal, earthy.
This vocabulary is shared with band member food preferences. Not mechanically
linked in V1 — the data is in place for future use.

---

## The Band

### Identity
At the start of the game the player chooses one of four bands. This is a
permanent flavor choice — no mechanical advantage. The choice sets the tone:
mission names, ingredient flavor text, and the culture the player operates
within. All bands are equivalent in power.

---

### The Four Starting Bands

#### The Mithlost — Forest / Elves
Ancient, grey, lingering. The ones who stayed to fight a long defeat when others
sailed West. Sorrowful, faithful, deadlier than their gentleness suggests.
Missions feel like stewardship and a rearguard action against encroaching shadow.
Ingredients lean toward woodland herbs, moonflower, ancient resins, things that
grow in old-growth forests.

**Members (V1 — 4 active, full roster in `docs/characters.md`):**
- **Aelindra** (Captain) — Ancient and precise. Carries centuries of grief without
  making it anyone else's burden. Food: earthy. Quirk: food from the ground, honest, unadorned.
- **Thalindel** (Hunter/ranged) — Young by elven reckoning. Finds the world
  beautiful and hasn't lost that yet. Asks too many questions. Deadly with a bow.
  Food: herbal. Quirk: wants to know every ingredient in his cup.
- **Galadorn** (Keeper) — Speaks rarely; when he does, people listen. Food: hearty.
  Quirk: dense, sustaining fare, nothing fussy.
- **Caranthir** (Warden) — Stands where he is told and does not move. Has held passes
  alone. Speaks in single words. Food: hearty. Quirk: dense, plain, eaten standing.

#### The Undermarch — Mountain / Dwarves
Grim, loyal, stone-deep. Words mostly unnecessary. They will not complain about a
hard march, but they notice — and remember — who kept them fed. Missions feel like
deep delves and the defense of ancient holds. Ingredients lean toward cave fungi,
deep minerals, things that grow without sunlight.

**Members (V1 — 3 active, full roster in `docs/characters.md`):**
- **Borin Ironmantle** (Warden) — Never complained once in forty years. Reliable as
  bedrock. Food: hearty. Quirk: dense food, nothing complicated.
- **Dagra Copperhelm** (Hunter/melee) — Laughs at everything. The band finds this
  inspiring or alarming depending on the day. Food: spicy. Quirk: give her heat.
- **Keldra** (Captain) — Youngest in the company. Ambitious in a way she hasn't
  learned to hide. Food: spicy. Quirk: bold flavors for a bold dwarf.

#### The Freewake — Sea / Corsairs
Free, chaotic, opportunistic. Answer to no flag and no crown. Eat what they can
get, go where the money is — but learned the right meal before a hard job changes
everything. Missions feel like raids, contracts, and opportunities seized.
Ingredients lean toward sea herbs, salt-cured finds, exotic port goods.

**Members (V1 — 3 active, full roster in `docs/characters.md`):**
- **Reva Tidecaller** (Captain) — Charismatic and completely unpredictable.
  Decides in three seconds flat and is right often enough that no one argues.
  Food: light. Quirk: nothing heavy before a job.
- **Silas Thorn** (Hunter/ranged) — Quiet, watchful. The fleet uses him as a
  compass — he always knows when something's wrong before it is. Food: herbal.
  Quirk: something brewed and quiet, like him.
- **Marta Wavebreaker** (Warden) — Loud, boastful, terrifyingly capable. The
  stories she tells about herself are exaggerated; the actual events were worse.
  Food: hearty. Quirk: plenty of it.

#### The Greycloaks — Eriador / Dúnedain of the North
The remnant of the line of Isildur — heirs to a kingdom that fell and was never
rebuilt. They walk the wild lands of Eriador not as wanderers but as guardians
bound by an oath older than the Shire. They have no home because their home is
gone. They wait for a king who has not yet come, and in the meantime they hold
the darkness back with their own hands. Among themselves they know what they are.
The world has mostly forgotten. Ingredients lean toward trail food, preserved
rations, foraged Eriador herbs — the food of a people who live in the open by
duty, not by preference.

**Members (V1 — 4 active, full roster in `docs/characters.md`):**
- **Aldric** (Captain) — Descended from the old chieftains. Quiet, unsentimental,
  and possessed of the long memory his line carries. Does not speak often. When he
  does, it is the weight of generations behind it. Food: earthy. Quirk: grounding
  food that doesn't announce itself.
- **Mira** (Hunter/ranged) — Young even by Dúnedain reckoning, and already more
  capable than most will ever be. She learned young what she was born to defend.
  Food: light. Quirk: light, fast — that's the rule before a hunt.
- **Cael** (Keeper) — Keeps a detailed field journal of everything observed —
  threats, remedies, routes, lore. The others tease him about it and borrow it
  constantly. Food: herbal. Quirk: thinks better with something warm and herbal
  beside him.
- **Hollis** (Warden) — The newest Greycloak, still learning what he carries. Came
  to this company from a village that no longer exists. Has not yet spoken of it.
  Food: hearty. Quirk: eats like a man who has known lean times and does not
  intend to again.

---

### Band Members — General Rules

Members are named individuals with personalities and a food preference. Cooking
for them is personal, not transactional.

- Each member has: name, personality, food preference (flavor tag), role lean
- Role leans are starting tendencies only — not caps (see `docs/progression.md`)
- Food preference is flavor in V1 — does not affect mission outcomes yet
- Members can be lost on badly failed missions. Loss is permanent in V1.
- Member loss condition: buff strength below 60% of required threshold AND a
  33% random roll succeeds

---

### Missions

- Missions run in parallel with gathering and cooking sessions
- Missions unlock by **vitality threshold** — the highest vitality among alive band
  members must meet the mission's minimum (easy: 0, medium: 3, hard: 6)
- No buff type requirement — any food helps; the right type is never gated
- Missions are **very difficult when first unlocked but always attemptable** — the
  player can send the band any time the vitality gate is met; success is unlikely
  early and improves as the band grows and provisions get stronger
- Buff **strength** affects success probability — stronger food meaningfully
  increases odds but never guarantees success
- Success: money reward (random within a range) + 1–3 ingredient drops
- Failure: nothing. Possible member wound or loss per conditions above.
- The player always sees the vitality requirement before confirming
- Harder missions return rarer ingredients and more money
- **Mission durations** scale with band level: below level 10, missions run
  20–30 minutes; level 10 and above, missions run 30–45 minutes.

---

## Progression Axes

Three independent axes that reinforce each other:

1. **Skill levels** — Gathering and Cooking XP. XP curve: each level costs
   `level × 100` XP. One forage session = 50 XP. Pacing subject to revision
   after playtesting.
2. **Recipe book** — what the player has unlocked. Reflects investment and
   exploration.
3. **Ingredient access** — what gathering sessions and missions return. Reflects
   how far both skill tracks have been pushed.

A player can be high on one axis and low on another. No single forced path.

**Band combat level cap: 20 (V1).** The simulator and encounter design treat band
level as the primary difficulty axis. V1 caps this at 20; later expansions may
raise the ceiling. Gathering and Cooking skill levels are uncapped.

---

## The Shape of the Whole Game (not V1 — for architectural awareness only)

The provisioning loop is the core of a two-half game. These layers exist in
`future/` and must NOT be built until promoted to `docs/`. They are noted here
so architectural decisions in V1 don't accidentally foreclose them. Full
structure is in `docs/redefinition.md`.

**The Campaign (the core game).** Cooking, provisioning, gathering, recipe
discovery, band members (meet them, feed them, bond, grow them), and battlegrounds
across the ages of Middle-earth. A living roster of battlegrounds that expands as
you grow — not a linear march, and it does not "end." A player can play only the
campaign and have a complete game. Four combat roles (Warden, Keeper, Hunter,
Captain). Three outcome states: Victory, Stalemate, Defeat. The clue system,
after-action log, and influence map are first-class features here.

**The Ettenmoors (the strategic endgame).** Unlocks mid-to-late, when the player
is genuinely ready (a readiness gate, not a checklist), and then coexists with the
campaign. Brutal, fixed difficulty — never scales down to the player. Manage
several opening factions at once. Take and hold contested ground that ebbs and
flows over time. Its own walled economy (Valor) buys Moors-specific gear, runes,
recipes, ingredients, skills, and bonuses. One continuous space: the deeper you
hold, the harder it gets, with raid-tier pre-boss encounters and world-ending boss
fights as the deepest territory — reached by holding ground far enough to get
there, not by a separate unlock.

**New Game Plus (campaign only).** After maxing everything, raise the campaign's
difficulty ceiling. The Ettenmoors has no NG+ — it is already as hard as the game
gets.

**Why the kitchen stays relevant forever:** the Ettenmoors never gets easier, so
there is always a reason to return to the campaign and the kitchen to develop
further. The campaign is the investment; the Moors is where it is tested. They are
interdependent, not sequential.

**Key architectural constraint to preserve now:** Mission resolution must remain
extensible toward multi-member, multi-stage, and variance-based outcomes. Do not
hard-code assumptions that a mission is always one food item, one outcome, one
duration. Keep the resolution layer thin and replaceable.

---

## What This Game Is Not

- No combat for the player — the band fights so the player never has to
- No punishment for idle play — close the app, come back, nothing is lost
- No energy systems, stamina bars, or artificial time pressure
- No multiplayer, trading, or leaderboards
- No ads, IAP, or accounts — ever
- No internet requirement — ever

---

## Open Questions

*(Resolve before building the relevant system)*

- **Player title** — [PLACEHOLDER]. Do not use Hearthwright or warlock-culinarian.
- How many ingredient combination slots does the experimentation interface have?
  (V2 question — recipe discovery is not in V1)
- Does Alchemy have its own discovery system or are recipes level-gated? (V2)

**Resolved:**
- *How are band member losses triggered?* — Below 60% of required buff strength
  AND 33% random roll. Implemented in MissionWorker. (V1 simplification — full
  combat uses wounds + grievous system; see `docs/combat-model.md`.)
- *How many named band members does the player start with?* — 3 per band (V1).
  Full 8-member rosters are designed in `docs/characters.md` for V2+.
- *Four starting bands?* — The Mithlost (elves/forest), The Undermarch
  (dwarves/mountain), The Freewake (corsairs/sea), The Greycloaks
  (wardens/borderlands).
- *Ingredient quality tiers?* — No. Complexity lives in the cook's skill and
  decisions, not raw materials. Never add quality tiers.
- *Craft branches (Waybread Fortifier, Miruvor Distiller, Athelas Apothecary)?*
  — Removed from V1 and from the active design. May return as specialization
  trees at max level (wishlist). GW2 seven-tier linear progression replaces them.
- *Abstract buff vocabulary (Endurance / Agility / Acuity / Warmth / Luck)?* —
  **Retired.** Food now buffs the five base stats directly (Might / Agility /
  Vitality / Will / Fate) + HP/s sustain. V1 code retains a simplified
  buff-strength number as a prototype layer; V2+ implements the full stat model.
- *Mission failure — partial success?* — No partial success. Failure is outright.
  Failed experiments also consume ingredients.
