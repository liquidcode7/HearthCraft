# HearthCraft — Battlegrounds: The Campaign Combat System

> Authoritative design document — June 2026.
> The party combat system for the Campaign layer. Not built yet — this is the
> design the code grows toward. Nothing here changes V1 scope.
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
- **Hunter role** → raises `D_party`.
- **Warden role** → raises `P_effective`.
- **Keeper role** → raises `P_effective` (sustain over time).
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
- Rabble → DISMAYED (not routed — orcs are driven, they don't flee; they
  falter and fight badly, easily cut down). The Fell → defeated. Banes →
  ordinary inspiration lets you SURVIVE/withdraw; only a peak captain's
  inspiration KILLS one, at cost. the Nine → never killed; inspiration lets you
  SURVIVE and drive them off. Nameless → not a combat target at all.
- INSPIRATION'S FUNCTION SLIDES BY TIER: against weak enemies it WINS the
  fight; against apex enemies it lets you SURVIVE (turns a Defeat into a
  Stalemate — see Outcomes). Same mechanic, sliding purpose. This is a core
  balance lever: the Captain is decisive vs the weak, only protective vs the
  strong, handing the spotlight to the trinity exactly when stakes are highest.
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

### Keepers can't heal themselves → protect them
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

---

## Roles, Balance & Outcomes (the four-role combat design)

> Added after a long balance discussion. The core problem solved here: the
> Captain was a one-trick, simultaneously OP (when inspiration fired) and
> useless (the rest of the raid). Fix: give EVERY role ongoing texture AND a
> heroic peak, make inspiration depend on / cost / share the spotlight, and add
> a third outcome state. All four roles end up balanced in DEPTH, each owning a
> different domain, none able to solo-carry.

### The Captain-OP problem and the three levers

Inspiration is a near-win button and the Captain owned it alone while every
other role did steady linear work. A role that WINS games outshines roles that
merely contribute. Fixes (all adopted):

- **Lever 1 — Inspiration depends on the trinity.** It can only fire if the
  party survived to a SURVIVABLE brink in good shape — which is the tank's and
  healer's job. Fail the foundation and you hit the catastrophic-margin brink
  where inspiration can't save you. The Captain doesn't win the raid; the
  Captain converts the foundation the others built into a win. No foundation,
  no rally.
- **Lever 2 — Every role gets its own heroic peak** (below). Inspiration becomes
  ONE of four legendary moments, not THE one.
- **Lever 3 — Inspiration COSTS the Captain.** The Boromir clause as a balance
  mechanism: leaning on inspiration spends the Captain — risk to their life,
  scaled to how desperate the rally was. A Captain who wins every raid by
  rallying is a Captain you will lose. Best play is a party good enough that you
  rarely NEED to rally; save the deed for when it truly matters.
- **Lever 4 (already in place) — Inspiration can't beat the apex.** Does nothing
  decisive to the Nine; only a peak Captain at great cost touches Banes. So in
  the hardest content inspiration is weakest and the trinity reasserts.

### The Captain's ONGOING kit (not just inspiration)

The Captain was a one-trick role — useless except at the brink. Now they are a
constant FORCE-MULTIPLIER, valuable every moment, whose value scales with the
QUALITY OF THE REST OF THE PARTY (you can't solo-carry with a Captain; their
whole job is amplifying good specialists). All four ongoing contributions kept:

- **Leadership aura** — the whole party performs modestly better simply because
  the Captain is present (a little more damage, resilience, steadier morale).
- **Morale-drain stabilization** — the Captain SLOWS the party's Morale bleed
  (distinct from the tank who blocks and the healer who restores — three
  different survival levers, no overlap).
- **Dread resistance** — the Captain extends their Will to the party, letting the
  company STAND where it would break (near the Nine). THE KEYSTONE: makes the
  Captain structurally essential for apex content without raw power — without a
  Captain the party may break from Dread before the fight even starts.
- **Battle coordination** — the party fights SMARTER (better target priority,
  better exploitation of damage-type vulnerabilities). Ties the Captain into the
  bestiary/damage-type systems as an ongoing multiplier.

Captain peak: **Inspiration** at the projected-loss brink (see above + earlier).

### The other three roles — ongoing texture + a heroic peak each

Each role mirrors the Captain's shape: a primary job, 2–3 ongoing contributions,
one categorical heroic peak. Balanced in depth, distinct in domain.

**TANK — domain: space & attention. Peak: the Last Stand.**
- Primary: soaks/redirects enemy damage (raises effective Morale).
- Ongoing: **threat control** (keeps enemy OFF the healer/Captain back line —
  ties to the "protect the healer" rule; tank failing = enemy reaches healer =
  no one can mend); **stagger/control** (disrupts, knocks back, lowers enemy
  damage output); **fortification** (grows more effective the longer they hold —
  rewards long-duration raids).
- Peak — **Last Stand:** at the brink, holds the line ALONE for a stretch,
  buying time nothing else could (Boromir at Amon Hen, Háma, the breach).
  Endurance made heroic — distinct from a buff or a heal.

**HEALER — domain: life & corruption. Peak: the Pull-Back.**
- Primary: restores party Morale (the sustain that stretches survivability).
- Ongoing: **wound prevention** (good in-raid healing means FEWER grievous
  wounds to treat afterward — links to the Houses-of-Healing burden);
  **cleansing** (counters fear, shadow-sickness, poison, Black Breath, blight —
  teams with the Captain's dread-resistance vs horror, different jobs);
  **stamina pacing** (spend now vs conserve for a long raid — resource feel
  without a Power pool).
- Peak — **the Pull-Back:** snatch a JUST-fallen member back from death mid-raid,
  reversing a casualty before the permadeath clock starts. Denial of death
  itself, once, when it matters most.

**DAMAGE — domain: the enemy's existence. Peak: the Slaying.**
- Primary: drives kill-speed (the `D_party` half of the two-pool race).
- Ongoing: **damage-type mastery** (exploits enemy vulnerabilities — fire on
  wights, blades of Westernesse; makes knowing the bestiary mechanically
  valuable, ties to the clue/discovery layer); **focus fire / target priority**
  (drops the dangerous enemies first — the whipping captain, the caster);
  **momentum** (early kills make later ones easier; clearing rabble frees focus
  for the real threat).
- Peak — **the Slaying:** fell a Bane or champion in a single legendary blow
  (Bard & Smaug, Éowyn's strike, Túrin & Glaurung). The role that can kill the
  unkillable-by-others. NOTE: a Bane may fall to EITHER an inspired peak-Captain
  OR a legendary Slaying — two paths to the impossible kill, one per role.

### Interdependence (the real anti-OP structure)

Every role is load-bearing FOR THE OTHERS, so no single role can dominate:
- Captain's inspiration (Lever 1) needs the tank to have held and the healer to
  have kept people up to reach a survivable brink.
- Keeper needs the Warden's threat-control to keep enemies off them.
- Hunter needs the Captain's dread-resistance to function near the Nine, and the
  tank to keep them alive long enough to land the Slaying.
Pull one out and the structure sags. This is what makes a party feel like a
FELLOWSHIP rather than four independent stat-blocks.

### Heroic-peak TRIGGERS (spread across the raid, not bunched)

Different conditions so the drama doesn't all proc at once at the brink:
- Warden Last Stand → at the brink.
- Keeper Pull-Back → when a member falls.
- Hunter Slaying → when facing a Bane/champion.
- Captain Inspiration → at the PROJECTED-loss brink (forecast, not body count).
One peak per role (clean, distinct). Reserve any SECOND peak for legendary
individuals as a very-late-game rarity, if at all.

### Rabble verb correction (lore)

Inspiration does NOT rout the Rabble — orcs are driven, whipped forward, more
afraid of their masters than of any hero. Inspiration DISMAYS them: they falter,
quail, fall into confusion, fight at a fraction of strength — but STILL FIGHT,
and are easily cut down. (Théoden at Pelennor: the host was thrown into dismay
and confusion but kept fighting; the true mindless ROUT came only later when
Sauron's will was removed.) Optional rare special mechanic / lore note: striking
the directing will (a Ring-falls moment) could produce the real total rout —
a special-case event, NOT part of normal inspiration.

### THREE outcome states (was win/lose; now win/stalemate/defeat)

The missing third state, and the most Tolkien outcome — the inconclusive hold
(Fords of Isen, Osgiliath, Helm's Deep as a near-loss survived):

- **Victory** — cleared the objective (killed/repelled as the battleground
  demands). Full rewards + influence-map gain.
- **Stalemate (neither side wins)** — survived and withdrew intact (or nearly),
  ground stays contested. THIS IS WHAT INSPIRATION BUYS AGAINST APEX ENEMIES:
  it converts a DEFEAT into a STALEMATE — not victory, not destruction, survival
  with the matter unsettled. Very small material rewards; the big payoff is you
  KEEP YOUR PEOPLE and the influence map doesn't worsen (you held the line). Can
  still cost ordinary wounds — bloodied but alive; survival, not safety.
- **Defeat** — broken. Casualties, wounds, LOST ground (shadow re-spreads on the
  map), and the rare grievous-wound permadeath chain.

Campaign texture this creates: a contested front where you fight battles to
DRAWS, holding ground you can't yet take, returning stronger until stalemates
become victories. The Ettenmoors feeling. Progression made of draws-becoming-wins
is far richer than binary win/lose.

### How depth reaches the player (the format constraint)

The rich simulation runs WHILE THE PLAYER IS AWAY (provision-then-resolve). So
none of this depth is experienced DURING the fight — the player isn't there.
Meaning must live in three vehicles, and the design must serve them:
1. **Composition-as-choice** — the provisioning/composition screen surfaces
   consequences as real decisions (bring a Captain for the dread? protect the
   healer?), legible enough to decide, opaque enough not to solve once.
2. **Log-as-story** — the after-action log MUST be a genuine narrative that NAMES
   who did what ("Borin held the breach alone for an hour"; "Mira pulled Cael
   back from the dark"). This is the ONLY place the simulation's depth reaches
   the player. Good log = every rule is felt. "You won, +3 herbs" = all depth is
   invisible machinery. The single most important deliverable of the whole mode.
3. **Roster-as-persistent-state** — wounds, recovery, bonds, growth persist
   between sessions, so opening the app means re-entering an ongoing situation
   you're invested in. This is WHY the complexity works on a phone in two-minute
   bursts: the meaning lives not in the two minutes but in the persistent world
   you step back into.

### Complexity / format honesty (a real concern, logged)

This combat design is deeper than many full tactical RPGs. It is plausibly fine
for an IDLE game BECAUSE the complexity is RESOLUTION-side, not INTERACTION-side
— the player provisions/composes then reads a story, never operating the systems
in real time. BUT two genuine risks remain:
- **Legibility risk:** can a player prepare WELL without understanding the full
  engine? The clue system must carry this. If preparing correctly requires a
  spreadsheet, complexity has leaked back to the interaction side and it's too
  much for the format.
- **Build-feasibility risk:** this is a person-years combat engine to build and
  BALANCE; large and growing. A non-programmer learning Kotlin is far from this.
This is all V5+; whether it's "too complex" can't truly be judged until the V1
core loop ships and real players show how much depth they absorb. Do not let it
pull focus from V1.

---

## Enemy Leadership: The Dread Aura (battlefield symmetry)

> Added to fix a missing symmetry: the party had a force-multiplier (the
> Captain's leadership) but the enemy had none — a host of rabble was just a pile
> of weak units whether or not something terrible led it. Wrong. A leader is a
> multiplier on the led, for the enemy exactly as for you. "Rabble led by a
> Nazgûl is way, way worse" because the leader makes the host stronger AND your
> party weaker — multiplicative in both directions, not additive.

### What a Dread aura does (mirror of the Captain, inverted)

A **dread-source** (an enemy captain, a Bane, or a Nazgûl) projects an aura that
works both directions at once:

1. **Buffs its own host** — the Rabble and Fell under it fight harder, hold
   longer, and don't break. The Witch-king's presence is WHY his orcs don't rout.
   Raises the effective power and resilience of every lesser enemy around it. The
   direct mirror of the Captain's leadership aura: a multiplier on the led. The
   same orcs are far more dangerous led than leaderless.
2. **Debuffs your party with DREAD** — saps will, makes your people falter and
   cower, threatens to BREAK low-Will members before the fight is even joined.
   (This is the cowering-from-Dread mechanic; Will resists it.)

### Dread has its OWN identity (not a tidy inverse of every Captain effect)

Dread leans HARD into a will-breaking signature: its effect on YOU is specifically
about nerve and breaking — the inverse of inspiration's "stand and fight" — rather
than a clean mathematical mirror of each Captain buff. It buffs the enemy host
generally, but against your party its character is FALTER AND COWER. This keeps it
thematically distinct from "a Captain but evil."

### The central duel: Captain aura vs. Dread aura

The two leadership auras are OPPOSING FORCES and the fight turns on the contest
between them:
- Makes the **Captain's dread-resistance the single most important thing in apex
  content** — not a flat stat but the COUNTER to the specific enemy aura. Against
  a Nazgûl-led host the Captain is what stands between your party functioning and
  your party breaking.
- Explains concretely **why you bring a Captain to face the Nine:** not to beat
  the wraith (you can't), but to NEUTRALIZE its Dread aura so the trinity can
  fight the host it leads. Without a Captain the enemy aura runs unopposed and
  your people falter while the orcs surge.

### Aura scales by leader tier
- **Enemy captain (Fell):** modest dread aura; your Captain easily counters it.
- **Bane:** powerful aura, the terror of an ancient thing; your Captain strains,
  lesser-Will members may falter.
- **Nazgûl:** overwhelming Dread, the signature of the Nine; only a strong Captain
  keeps the party standing at all — the aura that breaks unprepared companies
  before the first blow.

### Target-the-leader as a strategic objective (the Pelennor payoff)

Because the leader's aura buffs the whole host, BREAKING the leader collapses the
host. If you fell or REPEL the dread-source, its aura collapses **dramatically and
immediately** — the buff vanishes, the host becomes ordinary beatable rabble, and
the log narrates the host faltering as the leader withdraws. The canonical beat:
the host of Mordor breaking when the Witch-king falls.
- So a battleground may be winnable by BREAKING ENEMY LEADERSHIP rather than
  grinding the whole host: repel the Nazgûl (you can't kill it, but you can drive
  it off) and the rabble it held together loses its buff and crumbles.
- This makes "target the aura-source" a real emergent tactic, and makes repelling
  the leader the single most satisfying tactical outcome in the mode.

### What this resolves
- **Mixed fields** (the common case) finally have STRUCTURE: a Nazgûl + orc host
  isn't "a wraith plus orcs," it's a host made dangerous BY the wraith and a party
  being broken BY the wraith, with your Captain holding the line against the aura
  so the trinity can grind the buffed host — until you repel the wraith and watch
  the host's buff evaporate. A fight with a clear pressure and a clear answer.
- **Dread** becomes a thing with a SOURCE and a COUNTER you can see, fear, target,
  and oppose — not an ambient property of the ground.
