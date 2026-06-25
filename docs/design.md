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

Food boosts four base stats directly (Might / Agility / Vitality / Will) plus
HP per second. "Cook the right dish for the right member" is the provisioning
puzzle. The old abstract buff vocabulary is **retired.**

**Fate cannot be increased by food.** Fate is innate — it grows with band level
but cannot be cooked into. This keeps Fate as a stable background quality of each
member rather than an optimisable food stat. Lucky Dumplings (the former Fate
recipe) has been removed.

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
**Home region: Celondim / Duillond, western Ered Luin** — the elf-havens at the
coast; the last of the Eldar lingering in the West.

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
**Home region: Thorin's Halls, southern Ered Luin (Blue Mountains)** — the
established Third-Age dwarf-hold; deep halls, exiles working their craft far
from lost Erebor.

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

#### The Kingswake — Sea / Corsairs
**Home region: [PLACEHOLDER — not yet decided. Candidate: Grey Havens / Lond Daer
(the old Númenórean anchorage south of the Greyflood). To be locked when the
narrative frame is written.)]**

Númenórean-descended mariners — ancient bloodlines, formal bearing, kings three
thousand years in the dust. The sea took most of that back. What remains is a
fleet that answers to no crown, because their crown drowned with Númenor; that
accepts no master, because they remember what mastery once was. Proud,
deliberate, with a standard for how things are done that comes from somewhere
older than the Age. Missions feel like contracted work and earned trust. Ingredients
lean toward sea herbs, salt-cured finds, preserved stores from long voyages.

**Members (V1 — 3 active, full roster in `docs/characters.md`):**
- **Reva Tidecaller** (Captain) — Descended from the old navigator families.
  Carries herself with the quiet assurance of someone who knows what she is, even
  if no one else does. Decides fast and is right often enough that no one argues.
  Food: light. Quirk: nothing heavy before a job.
- **Silas Thorn** (Hunter/ranged) — Quiet, watchful. The fleet uses him as a
  compass — he always knows when something's wrong before it is. Food: herbal.
  Quirk: something brewed and quiet, like him.
- **Marta Wavebreaker** (Warden) — Loud, boastful, terrifyingly capable. The
  stories she tells about herself are exaggerated; the actual events were worse.
  Food: hearty. Quirk: plenty of it.

#### The Greycloaks — Eriador / Dúnedain of the North
**Home region: Bree-land** — crossroads of the world, ranging country, the
wardens' natural beat.

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

#### Encounter Difficulty — Four Food Tiers

> **V2+ combat system — not V1.** V1 uses a single buff-strength number for mission outcomes. This section describes the full HP/s encounter model that replaces it in V2+.

Each encounter is tuned against **four food quality tiers** defined by the player's
cooking level within the recommended range. The party's food HP/s (healing per
second) is the primary preparation axis:

| Tier | Win target | Feel |
|---|---|---|
| T1 | ~25% | Risky — possible with luck, teaches "cook better" |
| T2 | ~50% | Coin flip — clearly right approach but could go either way |
| T3 | ~70–75% | Solid — likely to succeed with this preparation |
| T4 | ~85–90% | Strong — reliable win for a prepared provisioner |

Mastery-level food (one tier above the encounter's recommended range) can push
win rates to 97–99%. No food is a fail state regardless of tier.

The HP/s steps between tiers are small (~0.2 HP/s). Each encounter is tuned at a
specific drain rate so the interesting zone — where food quality genuinely changes
outcomes — aligns with the player's expected cooking level for that encounter.
Full analysis in `docs/combat-model.md` (Tuned Encounters section).

---

### The Burglar

> **V2+ — not in V1 scope.** Full design is here for architectural awareness. Do not build until promoted in `docs/v1-plan.md`.

A fifth band member archetype — not a fifth combat role, but a *specialist*
slotted into the party alongside the four fixed roles. The provisioner is
**not** the burglar; burgling lives inside the band as a member archetype.
- Near-zero DPS (rare procs only).
- Very high dodge / avoidance — high-but-not-guaranteed. Certainty would
  flatten tension; variance is a curve-smoothing source, not a bug.
- Gleans extra loot from fights.
- **Exposure scales with party cohesion (the key mechanic).** While the party
  is whole, the burglar barely draws aggro and mostly takes no damage — enemy
  attention is distributed across the band. As bandmates fall, the burglar
  becomes increasingly exposed: attention that was spread across the band finds
  the one squishy target left. A burglar in a healthy party is untouchable; the
  last-one-standing burglar is the most exposed they've ever been, with no
  staying power to fall back on.

This reuses the combat cascade (already built) expressed through one member's
vulnerability — economical design. It also self-corrects a potential exploit:
a high-avoid unit would otherwise be the best candidate to cheese unwinnable
survival/escape fights, but this makes them weakest exactly when you'd try to
abuse that. The in-fiction read is obvious: the burglar thrives behind a strong
party and flounders alone.

**Indirection worth keeping:** you protect the burglar by provisioning the
*rest* of the party well. Their survival is the burglar's armor. The burglar
ties back into whole-party care without adding a new provisioning slot.

**Loot rule:** you gain the burgled loot only if you **win** the fight. Fielding
a burglar is a wager, not a free bonus.

**The hire → recruit arc:**
- *Early:* no burglar. Learn combat and cooking first; the burglar's arrival
  later feels like an earned upgrade.
- *Mid:* **hire** a burglar per job, paid in coin. Transactional, temporary, no
  loyalty. Combined with win-or-nothing loot, hiring is a real money-sink wager.
- *Late:* **recruit** one permanently. Always-available gleaning, no per-job fee,
  loot curve steps up. The emotional payoff — the hired stranger becomes family —
  fits the provisioner-as-soul identity.

Produces the desired rising loot curve: flat → bursty/conditional → high/steady.

**The running gag — "it's always a hobbit" (deadpan):**
You hire a **burglar** from the nearest inn. The hire option stays deadpan
forever — "Hire a burglar," never "hire a hobbit." The UI plays it completely
straight and never acknowledges the pattern. Without fail, every hired burglar
is a hobbit. The comedy lives in the band members' reactions, which escalate
over many hires: early — mild, almost unremarked; middle — noticing out loud;
later — full affectionate resignation, bets among the band on whether the next
one breaks the trend (they never do). The gag tells a truth about Middle-earth
(burgling *is* a hobbit profession), and by the time you permanently recruit
one, the bit has built genuine fondness. The band protecting the little one
without quite saying so is the tone to hit.

All band member lines about the burglar must follow the voice and tone guide —
no modern idiom. See `docs/voice-tone.md`.

**Open questions** (to resolve when the burglar is designed in full — see
`future/wishlist.md`):**
- Is the burglar a 5th role or does party composition change (field-4-from-5
  roster approach)?
- Does avoidance scale with food/provisioning?
- Does gleaning happen continuously through the fight or as an end-of-fight
  payout?
- Recruitment: is "permanent" earned by repeated hiring, or bought outright?

---

## Progression Axes

Three independent axes that reinforce each other:

1. **Skill levels** — Gathering and Cooking XP. Each track uses a power-law
   curve with tier-wall multipliers at cooking tier boundaries. Exact parameters
   are in `PlayerRepository.kt` (constants and `xpToNext`) and calibrated in
   `tools/sim/xp_lab.js`. Do not hardcode XP values here — use those files as
   the source of truth.
2. **Recipe book** — what the player has unlocked. Reflects investment and
   exploration.
3. **Ingredient access** — what gathering sessions and missions return. Reflects
   how far both skill tracks have been pushed.

A player can be high on one axis and low on another. No single forced path.

**Band combat level cap: 20 (V1).** The simulator and encounter design treat band
level as the primary difficulty axis. V1 caps this at 20; later expansions may
raise the ceiling. Gathering and Cooking skill levels are uncapped.

---

## Combat Roles and Damage Types

Four roles, each with a distinct damage profile. Full mechanical spec in `docs/combat-model.md`.

| Role | Color | Damage type | Stat | Notes |
|---|---|---|---|---|
| Warden (tank) | Blue `#2e6da4` | Physical (armor-affected) | Might | Guards Keeper from killing blows |
| Hunter (DPS) | Red `#c0392b` | Physical (armor-affected) | Agility + Might | Dominant physical DPS; armor countered by party draught potency |
| Keeper (healer) | Purple `#7d5a93` | Magic (bypasses armor) | Will | Deals damage when not rescuing |
| Captain (support) | Gold `#b8843c` | Hybrid — Mig×0.3 physical + Wil×0.2 magic | Might + Will | Dread resist aura + Red Dawn inspiration |

**Captain hybrid split:** her physical output scales from Might (armor-affected); her magic output scales from Will (bypasses armor). Boosting Will via food shifts her toward more magic damage. Fate no longer contributes to her raw damage — it drives Inspiration odds instead.

**Keeper damage formula:** `Wil × 0.9`. Magic-type: bypasses armor entirely. Zero on any tick the Keeper fires a rescue burst.

**Sim color conventions** (Results tab):
- Keeper healing output = Green `#5b7f63`
- Captain's party boost (Will + Red Dawn combined) = Silver `#8a9aaa`
- Physical damage type = Red `#c0392b`
- Magic damage type = Purple `#7d5a93`

---

## The Shape of the Whole Game (not V1 — for architectural awareness only)

The provisioning loop is the core of a two-half game. These layers exist in
`future/` and must NOT be built until promoted to `docs/`. They are noted here
so architectural decisions in V1 don't accidentally foreclose them. Full
structure is in `docs/redefinition.md`.

### Narrative Frame — Adjacent to the Fellowship

The game runs *parallel* to the canonical War of the Ring, brushing against
famous events constantly but never sharing the frame with named heroes. The
player's bands fight their own desperate battles — a held pass the same night
as Helm's Deep, a valley over; history records neither. This is the unsung
logistics of the war made literal: the Fellowship gets the songs, your bands
are the supply lines and side-skirmishes the chronicle forgot. The provisioner
is the heart of that forgotten network — invisible to history, indispensable to
everyone they feed.

The canonical timeline gives the eastward journey a free spine: the War of the
Ring already has a route and a rising clock (Eriador → Rivendell → mountains →
Rohan/Gondor), so the canonical escalation *is* the progression structure.

**IP approach:** IP is not a constraint here. Named heroes, their dialogue,
the Fellowship on screen — all are on the table. The creative *lean* worth
weighing: great events felt as distant weather, plus rare well-placed cameos,
often hit harder than a constant parade, and keep your own bands the
protagonists rather than small figures beside giants. Glimpses (riders that
might be the Three Hunters, a recently-left campsite, news of a grey wizard)
are cheap and evocative. Spend cameos where they land.

**Northern/eastern fronts** are an underused opportunity: Dale, Erebor,
Mirkwood, the Lonely Mountain let the dwarves and woodland-elves shine on
*their* home ground.

### Three-Era Structure

**Era 1 — Free era (Eriador, Fellowship-independent).** Three bands with
separate home bubbles (Celondim for elves; Thorin's Halls for dwarves;
Bree-land for Dúnedain) converge in the Lone-Lands (mechanical
palette-merge) and expand outward through the arms of western Eriador.
Pre-war country — mostly yours to develop:
- **Eregion / Enedwaith / Dunland** — the seeding of Saruman's betrayal,
  before it breaks.
- **Fornost & Carn Dûm / Angmar** — present-day ruins, haunted, questions
  unanswered. No "Angmar is still active" timeline problem; they are simply
  places that fell, seeds for later visions.

**Transition A — the Rivendell hinge: Elrond's charge.** Geographic
chokepoint and narrative gate in the same place. Elrond is on screen — named,
written, given weight. His house launched the quest; he is the right figure
to turn the unified operation eastward into the Fellowship's footsteps. The
bands become one operation *before* being pointed east — a fellowship of their
own, then sent after the real one.

**Era 2 — Hounding era (east of the mountains, war's wake).** Cross Moria
post-Fellowship (deep dark in their wake, present danger you survive), then
Lothlórien, the Anduin, the open Rohan/Gondor war. Fellowship-adjacency
becomes the organizing flavor; the "unsung logistics of the war" theme finally
has an open war to pay off behind.

**Palette merge (bands converging) is early, Lone-Lands. Era hinge is later,
Rivendell.** These are two separate transitions, not one.

### Encounter Placement and the Eastward Journey

**Open thread — see `future/wishlist.md` for the full encounter ladder
placement question.** The narrative frame makes some V1 encounter placeholders
obviously wrong: Goblin-town at recLevel 5 places armored goblins *before* the
Rivendell hinge (era 1), which conflicts with the eastward journey logic —
Goblin-town is deep in the Misty Mountains, a post-hinge destination. Encounter
placement must be reconciled with the three-era geography before the encounter
roster is locked for the campaign layer.

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

V2+ open questions (recipe discovery interface, Alchemy discovery system) are
tracked in `future/wishlist.md`.

**Resolved:**
- *How are band member losses triggered?* — Below 60% of required buff strength
  AND 33% random roll. Implemented in MissionWorker. (V1 simplification — full
  combat uses wounds + grievous system; see `docs/combat-model.md`.)
- *How many named band members does the player start with?* — 3 per band (V1).
  Full 8-member rosters are designed in `docs/characters.md` for V2+.
- *Four starting bands?* — The Mithlost (elves/forest), The Undermarch
  (dwarves/mountain), The Kingswake (corsairs/sea), The Greycloaks
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
- *Encounter vs Battleground vocabulary* — **Locked.** **Encounters** = single-stage
  fights (the V1 unit; what `tools/sim/` runs). **Battlegrounds** = named historical
  set-pieces (Fornost, Pelennor, Azanulbizar) — multi-stage, differently scored,
  Campaign-layer content. Same underlying engine, different shape and scope. Reward
  tables for Encounters are band-agnostic; Named Legendary drops belong to
  Battlegrounds only. `missions.json` will be renamed `encounters.json` when the
  Android build catches up.
