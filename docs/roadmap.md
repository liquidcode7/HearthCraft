# HearthCraft — Full Build Roadmap

> Authoritative phase plan — June 2026.
> Covers everything from current state to the complete game.
> Phases within each layer are ordered by dependency, not necessarily time.
> Layer 1 = foundation. Campaign = the full core game. Ettenmoors = endgame.

---

## Where We Are Now

The provisioning engine is built and playable:
- Gather → grow → cook → provision → mission loop works end-to-end
- Four bands, four members each (Warden / Keeper / Hunter / Captain)
- Stats system (Might, Agility, Vitality, Will, Fate) initialized and growing
- Vitality-gated missions with probability-based outcomes
- Wounds (ordinary and grievous), wound treatment
- Kitchen with three visible tiers (Apprentice / Journeyman / Craftsman)
- Market for buying seeds
- Harvest collect mechanic
- Two-faction start in progress (this session)

---

## Layer 1 — Foundation

*Finish the foundation before building upward.*

### Phase 1A — Two-faction start (in progress)
- Player picks two bands at character creation
- Second band dormant until cooking level threshold is met
- Band screen shows both bands with a switcher
- Both bands can run concurrent missions

### Phase 1B — Character depth (data → display)
- Band member personalities visible in-game (member detail panel or tap)
- Food preference shown on member cards
- Role displayed with mechanical meaning (not just a label)
- Member wound status shown clearly with treatment options

### Phase 1C — Crafting level design
- Define the full level curve and max level (see wishlist)
- Align the 7 design tiers (Hearthkeeper → Grandmaster) with code
- Add recipes to fill all tiers meaningfully
- Grimoires as rare mission drops that unlock locked recipes

### Phase 1D — Squad level design
- Define what "squad level" means and how it is calculated
- Display it somewhere meaningful on the Band screen
- Wire it into the second-band unlock condition alongside cooking level

---

## Layer 2 — Campaign

*The core game. A player who never reaches the Ettenmoors has a complete experience.*

### Phase 2A — Mission depth
- Hidden requirements: missions never show numbers, show clues instead
- After-action text: failure names the nature of failure, not a stat readout
- Three outcome states: Victory / Stalemate / Defeat (currently binary)
- Reward variance tied to outcome state
- Stalemate: partial reward, no member consequences
- Defeat: no reward, wound/loss risk as now

### Phase 2B — Full 8-member rosters
- Second member of each role (permadeath insurance) loads from JSON
- Backup activates when the first member of that role is lost
- Player never manually recruits — the roster is the roster
- Member detail screen showing personality, stats, history

### Phase 2C — Recipe discovery
- Combinatorial experimentation interface
- Failed experiments consume ingredients
- Discoveries are permanent and go into the recipe book
- Grimoire-gated apex recipes (require the book to be found first)

### Phase 2D — Named battlegrounds (first arc)
- First 5 battlegrounds: the Angmar Wars arc
  - Cleansing of the Barrow-downs
  - The Fall of Fornost
  - Holding the Ettenmoors
  - The Host of the West at Fornost
  - Storming of Angmar / Assault on Carn Dûm
- Clue system: each battleground has hidden requirements discovered through
  failed runs and after-action text
- Influence map: visual record of victories, defeats, and contested ground

### Phase 2E — Combat roles become mechanical
- Warden: reduces damage intake, generates threat
- Keeper: sustains vitality between segments, counters corruption effects
- Hunter: increases kill speed, exploits enemy vulnerabilities
- Captain: raises Inspiration probability, reduces Dread effects
- Heroic peaks: Last Stand / Hands of Healing / Deadeye / Inspiration
- Inspiration trigger: forecast of defeat, not accumulated death

### Phase 2F — Houses of Healing
- Grievous wounds require: active Keeper + player's mastered alchemical
  preparation + time (member out for meaningful duration)
- Athelas preparation as the first discoverable grievous-wound cure
- The player's craft is the indispensable ingredient — the Keeper alone
  cannot save them

### Phase 2G — Full Campaign battleground roster
- Remaining 9 battlegrounds (War of the Ring arc + mythic)
- Each with clue system and after-action narrative
- Influence map fills in as victories are won

---

## Layer 3 — Ettenmoors

*Unlocks when both bands are at appropriate strength. Brutal, fixed difficulty.*

### Phase 3A — Readiness gate
- Ettenmoors locked until: both bands meet squad level + cooking level threshold
- Gate is felt, not stated — the player recognises readiness by what they can do
- A narrative event fires when the gate is met

### Phase 3B — Valor economy
- Valor is earned only in the Ettenmoors, spent only there
- Valor buys: Moors-specific ingredients, gear, runes, recipes, bonuses
- Separate from campaign gold — does not cross over

### Phase 3C — Territory map
- Contested ground that ebbs and flows over time
- Holding territory = access to deeper fights
- Losing territory = losing access, not losing the game
- Both bands can be deployed simultaneously in the Ettenmoors

### Phase 3D — Ettenmoors encounter tiers
- Rabble and Fell in the outer territory
- Banes in the mid-territory (felling one is a legendary deed)
- The Nine in the deep territory — never killed, only survived or repelled
- Nameless Things as doom-clock hazards in the deepest zone

### Phase 3E — Pre-boss encounters
- Raid-tier encounters gating the final boss zones
- Require full provisioning mastery to survive
- Multi-stage with segment breaks (Bonfires / Respites)

---

## Layer 4 — Polish and Endgame

### Phase 4A — New Game Plus
- After completing the full campaign, raise the difficulty ceiling
- The Ettenmoors has no NG+ — it is already the hardest the game gets

### Phase 4B — Lesser rings
- Discoverable or forgeable at very high crafting level
- Each grants one singular meaningful bonus to the wearer
- Not a stat boost — one unusual effect each (see wishlist)

### Phase 4C — Faction-swap token
- Buy-once Market item allowing the player to exchange one faction
  while maintaining all progress and identity
- Flavor change only — no mechanical advantage

### Phase 4D — F-Droid release
- Final stability pass
- No Google Play Services dependency audit
- Submit to F-Droid

---

## Open Design Work (not yet phased)

These are needed before the relevant phase can be built but are not yet
designed. Each has a wishlist entry.

- **Crafting level curve and max level** — needed before Phase 1C
- **Squad level system** — needed before Phase 1D and Phase 3A
- **Influence map visual design** — needed before Phase 2D
- **Ettenmoors territory map layout** — needed before Phase 3B
- **Faction-swap token exact mechanics** — needed before Phase 4C

### Active Design Priorities (June 2026)

Work explicitly queued — design these before building the relevant systems:

1. **Crafting and gathering mechanics redesign** *(first priority)* — the current
   system (flat gathering sessions, simple cooking tiers) needs a full design pass
   before Phase 1C. Questions to answer: what does a gathering session feel like at
   different levels? What are the meaningful decisions? How does farm/garden/forage
   differentiate beyond output pool? What does cooking mastery feel like vs. just
   unlocking higher-tier recipes?

2. **Battlegrounds design** — full encounter roster, structure per battleground
   (cleansing vs. defense vs. assault), clue system design, influence map behavior,
   per-battleground provisioning requirements. Needed before Phase 2D.

3. **Ettenmoors design** — territory map layout, faction structure, Valor economy
   design, readiness gate definition, how provisioning scales across multiple forces.
   Needed before Phase 3A.

4. **Legendary item system** — stats, how items are found, upgrade path, economy
   (cost to upgrade, Valor vs. gold, rare material gates). Named legendaries are
   the long-term reward layer; design needed before Phase 2D or Phase 3B.

---

## What Never Changes

Regardless of phase:
- The provisioning loop is never automated and never becomes irrelevant
- The player never fights
- No multiplayer, cloud saves, IAP, ads, or internet requirement — ever
- The kitchen is always the player's domain and always matters
