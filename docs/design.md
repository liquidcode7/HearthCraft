# HearthCraft — Game Design Document

> Status: Living document. Update as decisions are made.
> Last revised: session 6 (June 13, 2026)

---

## What This Game Is

An offline idle game for Android set in high fantasy. You play as a
warlock-culinarian — potion master, chef, and alchemist — who is the
indispensable hidden engine of a roving band of fighters. You never fight.
You gather, grow, brew, and cook. Without you, they are nothing.

The line between a perfect dish and a potion is a matter of intent. Your
food is borderline magical — not metaphorically. You understand that
ingredients have power, preparation unlocks it, and feeding someone the
right thing at the right moment changes what they're capable of. The band
doesn't know if it's cuisine or sorcery. They don't care. It works.

Design inspirations:
- **GW2** — recipe discovery, ambient tone, no punishment for casual play
- **FFXIV** — parallel gathering and crafting skill tracks, specialist
  identity, dedication has visible reward
- **LotRO** — high fantasy grounding, mastery through repetition

---

## The Core Loop

```
Gather ingredients (Farm/Garden or Forage/Wild session)
        +
Cook food / brew preparations (Cooking or Alchemy session)
        +
Send band on mission (runs in parallel with the above)
        ↓
Experiment with ingredient combinations → discover new recipes
        ↓
Band returns: money + rare regional ingredients
        ↓
Rare ingredients unlock new experiments and better preparations
        ↓
Better food → band attempts harder missions → rarer ingredients
        ↓
repeat
```

Gathering, crafting, and missions all run simultaneously. The game never
forces you to wait on one thing before starting another.

---

## Skill Categories

Two categories: **Gathering** and **Crafting**. Each has two tracks.
One session per category at a time. Missions run independently in parallel.

---

### Gathering

One shared skill level across both modes. You choose which mode to run
each session. Both contribute XP to the same Gathering pool.

#### Farm / Garden
- Cultivated ingredients: vegetables, fruits, grains, herbs
- Beekeeping: honey at low levels; royal jelly, beeswax, rare cultivars
  at elite skill levels
- Predictable output — you know roughly what you will get
- Rare and exotic ingredients unlock at elite skill levels
- A maxed gardener produces things that cannot be reliably found anywhere
  else — this is a distinct long-term specialist identity

#### Forage / Wild
- Uncultivated ingredients: wild herbs, mushrooms, bark, roots,
  unusual plants
- Unpredictable output — occasionally surprising, sometimes lean
- Primary source for alchemical inputs (unusual, non-food materials)
- Rare finds possible at any level, more frequent at higher levels
- Different specialist identity from farming — neither track obsoletes
  the other

---

### Crafting

Two skills that level independently but feed into each other.

#### Cooking
The primary loop. Produces food and preparations that buff the band.

- Recipes discovered through ingredient experimentation, not handed to you
- Leveled through cooking AND gathering — gathering is considered part of
  the craft, both contribute Cooking XP
- No hard level gates on recipes — a powerful recipe can be discovered
  early, but its ingredients are the natural gate
- At high levels, preparations blur the line between food and magic

**Buff types (V1):** endurance, agility, acuity, warmth, luck. Each recipe
produces exactly one buff type. Missions require a specific buff type — the
wrong type counts as a miss regardless of strength.

**Buff strength scaling:** `baseBuffStrength + (cookingLevel - 1) × buffStrengthPerLevel`.
Simple recipes (30-min cook): base 10, scale 0.4/level. Complex recipes
(90-min cook): base 12, scale 0.65/level. Skill matters continuously —
every level makes your provisions meaningfully better.

**Food flavor tags:** Every recipe has a `flavorTag` — one of: sweet, hearty,
light, spicy, herbal, earthy. This vocabulary is shared with band member
`foodPreference` (see Band Members below). Not mechanically linked in V1
but the data is in place for future use.

#### Alchemy
The weird science track. Produces special ingredients that normal cooking
cannot make, and eventually potions.

- Takes unusual inputs: minerals, strange plants, components from missions,
  foraged oddities
- Low levels: flavor enhancers, preserves, infusions that upgrade base
  ingredients
- High levels: potions that give the band unusual abilities beyond what
  food alone can provide
- Some alchemical ingredients only come from specific missions — alchemy
  progression is partly gated by how far the band has explored
- Feeds back into Cooking: the best recipes likely need at least one
  alchemical component

---

## Recipe Discovery

Recipes are not given to you. You find them by experimenting.

- Combine ingredients in the cooking interface
- Some combinations produce a discovery, others produce nothing (and
  consume the ingredients — experimentation has a real cost)
- Discovering a recipe adds it permanently to your recipe book
- The recipe book is earned knowledge, not a shop
- Powerful recipes can be discovered early — ingredient scarcity is
  the natural pacing mechanism, not arbitrary level walls

---

## The Band

### Identity
At the start of the game you choose one of four bands. This is a permanent
flavor choice — not a mechanical advantage. All bands are equivalent in
power. The choice sets the tone of your world: mission names, ingredient
flavors, the culture you operate within.

### Starting Bands

**Druid Circle** — Forest. Ancient, patient, connected to living things.
Missions feel like stewardship and protection. Ingredients lean toward
woodland herbs, moonflower, ancient resins, things that grow in old-growth
forests.

**Dwarven Company** — Mountain. Gruff, loyal, direct. Bring back deep
minerals, cave fungi, things that grow without sunlight. Food needs to be
dense and sustaining.

**Corsair Fleet** — Sea. Chaotic, free, opportunistic. Ingredients from
deep water, sea caves, exotic ports. Provisions must survive salt air and
long voyages.

**The Greycloaks** — Borderlands. Wandering wardens who answer to no crown
and hold no land. They move through the margins of the world as though they
belong there. Their missions demand perception and careful observation —
all three are acuity missions. Members: Aldric, Mira, Cael.

### Future Bands (later phases)
Norse warband (Tundra), Hedge witch coven (Swamp), Halfling militia
(Plains), Dark elf house (Volcanic). Present in world lore from the start.

### Band Members
The band is not faceless. Members are named individuals with personalities
and a `foodPreference` — one of the six flavor tags (sweet, hearty, light,
spicy, herbal, earthy). This is characterization, not a mechanical stat:
it tells you something true about who they are. Dagra Copperhelm wants
spicy food because she laughs at mild food the same way she laughs at
everything else. Keeping them well-fed and well-provisioned is personal,
not abstract.

In V1, `foodPreference` is flavor only — it does not affect mission outcomes.
Future phases can use it to add minor bonuses or personality-driven reactions.

Members can be lost on missions that fail badly enough. Loss is permanent.
This makes provisioning feel consequential.

### Missions
- Missions run in parallel with your gathering and crafting sessions
- Each mission requires a specific buff **type** and a minimum buff **strength**
- Both must be met — wrong buff type counts as a miss regardless of strength
- Meet both → success: money reward (random within a range) + 1–3 ingredient
  drops from the mission's reward table
- Miss either → outright failure: no rewards
- Member loss: only possible if buff strength is below 60% of the required
  threshold AND a 33% random roll succeeds. Failure is always costly;
  losing someone is rarer and more severe.
- The player always knows the requirements before sending the band out
- Harder missions return rarer ingredients and more money
- Mission difficulty scales with buff threshold — harder missions require
  stronger preparations

---

## Progression Axes

Three independent axes that reinforce each other:

1. **Skill levels** — Gathering and Cooking XP (Alchemy in V2). Reflects
   time and sessions invested. XP curve: each level costs `level × 100` XP
   — level 2 costs 100 total, level 3 costs 300 total, level 4 costs 600
   total. One forage session = 50 XP, so roughly 2 sessions to level 2.
   Pacing subject to revision after playtesting.
2. **Recipe book** — what you have discovered. Reflects curiosity and
   willingness to experiment and lose ingredients trying.
3. **Ingredient access** — what your gathering sessions and missions
   return. Reflects how far you have pushed both skill tracks.

A player can be high on one axis and low on another. The game does not
force a single path through its content.

---

## What This Game Is Not

- No combat — the band fights so you never have to
- No punishment for idle play — close the app, come back, nothing is
  lost or decayed
- No energy systems, stamina bars, or artificial time pressure
- No multiplayer, trading, or leaderboards
- No ads, IAP, or accounts — ever
- No internet requirement — ever

---

## Wishlist / Future Phases

Ideas confirmed as good but deferred. Do not build during V1.
Full list in `docs/wishlist.md`.

- Alchemy as a fully built parallel crafting track (V2)
- Recipe discovery engine — combinatorial, persistent (V2)
- Elite and rare ingredient tiers from gathering (V2)
- Potions and unusual mission types unlocked by Alchemy (V2)
- Additional bands beyond the starting four (V2+)
- Band member storylines and relationship arcs (V2+)
- Forgejo migration (whenever)
- Multiplayer ingredient trading between band players (long-term maybe)

---

## Open Questions

*(Decisions not yet made — resolve before building the relevant system)*

- What is the player character's title, or is it player-defined?
- How many ingredient combination slots does the experimentation
  interface have?
- Does Alchemy have its own discovery system or are recipes level-gated?

**Resolved:**
- *How are band member losses triggered?* — Below 60% of required buff
  strength AND 33% random roll. Implemented in MissionWorker.
- *How many named band members does the player start with?* — 3 per band
  (Druid Circle: Aelindra, Thornwick, Old Mossback; Dwarven Company: Borin,
  Dagra, Keldra, Snorri — 4; Corsair Fleet: Reva, Silas, Marta — 3;
  Greycloaks: Aldric, Mira, Cael — 3).
