# HearthCraft — Wishlist

> Deferred ideas. Add freely. Never act on during V1.
> When V1 ships, this becomes the starting point for V2 planning.

---

## Gameplay Systems

- **Member inspection screen** — tap a member to open a full detail panel: large
  name and role header, personality blurb and flavor text, colored stat bars
  (green for Vitality, red for wounds, blue for Might, etc.) in a proper RPG
  display, wound history, and eventually a small character portrait or icon.
  The stats exist — they just aren't shown beautifully yet. This is the screen
  that makes the band feel alive.

- **Second-band unlock popup** — when the player's cooking level reaches the
  threshold (currently 6), fire a one-time celebration dialog naming the newly
  unlocked company and briefly explaining the band switcher. The switcher itself
  already exists; this is the moment that teaches the player it appeared.
  Requires a "has seen unlock notification" flag in PlayerState so it only fires
  once.

- **Band management as a second pillar** — the game currently has deep
  player-side systems (gathering, cooking, provisioning) but a thin band
  side (pick food, send, wait). V2 should give the band side equal weight:
  mission-specific member selection (choose who goes, not just what they
  eat), recruiting to replace losses, member training and growth arcs,
  equipment that complements provisioning, and inter-member dynamics.
  The vision: two interlocking halves — the hearth and the company.

- **Alchemy** — full parallel crafting track. Takes unusual inputs
  (minerals, foraged oddities, mission components), produces special
  ingredients and eventually potions. Low levels: infusions and enhancers.
  High levels: potions that unlock unusual mission types the band couldn't
  otherwise attempt.

- **Recipe discovery engine** — combinatorial experimentation system.
  Player combines ingredients, some combinations produce discoveries,
  failed experiments consume ingredients. Discoveries are permanent.
  The recipe book becomes earned knowledge rather than a given list.

- **Elite and rare ingredient tiers** — high-level gathering unlocks
  ingredients not available any other way. Maxed gardener grows things
  that can't be found in the wild. Maxed forager finds things that can't
  be cultivated.

- **Crafting level design** — the cooking and gathering level curves, tier
  breakpoints, and maximum level are not yet fully designed. Currently the
  highest recipe requires level 12 and the XP formula has no hard ceiling.
  Needs a complete design pass: how many levels total, what each tier boundary
  means, what the Grandmaster ceiling feels like, and how the curve paces
  across the full game. Should be done before adding significant new recipes.

- **Squad level system** — a band needs a meaningful combat level metric: a
  single number (or progression) reflecting how seasoned the band is through
  missions survived, members grown, hard fights endured. Currently the second
  band unlock uses cooking level alone as a proxy. The full design should define
  what "squad level" means, how it is calculated from member stats and mission
  history, how it is displayed, and how it gates the second band unlock and
  eventually the Ettenmoors readiness check. Should combine with cooking level
  for the final unlock condition.

- **Lesser rings** — at very high crafting level, the player can discover
  or forge lesser rings that grant singular, meaningful bonuses to the
  band member who wears one. Not a general stat boost — one unusual
  effect each. A nod to Tolkien's ring lore, and a long-term reward for
  players who have mastered the craft.

- **Cooking skill trees** — specialization paths (Baking, Pastry, Knife
  Work, etc.) that gate recipe categories. Evaluate after V1 — does the
  base loop create demand for this?

- **Band member storylines** — named members with personal arcs that
  unfold over time. Missions tied to specific members. Relationship depth
  beyond mechanical quirks.

- **Band member replacement** — mechanic for replacing lost members.
  New members arrive through missions or reputation, not automatically.

- **Training missions** — but only if they feed back into what the player
  provides. Band member growth should come from better provisioning,
  not independent training.

- **Multiple band relationships** — reputation with bands beyond your
  own. Other bands hear about your work. Competing demands on your time
  and supplies.

- **Potions and unusual mission types** — high-level Alchemy unlocks
  mission types food alone cannot enable.

- **Money as a resource** — currently collected but not spent. Future:
  buy rare ingredients, upgrade equipment, pay for information.

- **Kitchen upgrades** — workspace improvements that affect session
  efficiency or unlock new recipe categories.

- **Pantry freshness and decay** — ingredients and prepared food have
  a shelf life. Adds urgency and planning without artificial energy
  systems.

- **Additional bands** — Norse warband (Tundra), Hedge witch coven
  (Swamp), Dark elf house (Volcanic), Halfling militia (Plains/luck).
  Halflings would be the natural luck band — their missions are things
  like "borrow mushrooms," "pickpocket big folk," "pipeweed tending,"
  and "second breakfast." The player character, who also never fights,
  is basically a Hobbit. There is something to this.

## Recipe Discovery and Alchemy (V2)

- **Ingredient combination interface** — how many slots does the experimentation
  screen have? What does a failed experiment feel like vs. a discovery moment?
  Design this before building the recipe discovery engine.

- **Alchemy discovery system** — does Alchemy have its own combinatorial
  discovery mechanic, or are recipes level-gated (unlock at tier N)? The cooking
  discovery system and the Alchemy discovery system may diverge here. Decide
  before building either.

---

## Fate Balance — Shadow as the Missing Counterweight

**The problem:** Fate's two live mechanics (spike evasion at `Fat × 0.004`, Inspiration
rate boost at `base + Fat × 0.003`) are strong enough in isolation to shift encounter
win rates by ~12 percentage points across food levels. Neekerbreekers at FL1 moved from
27% → 40% after Fate was added. Attempts to compensate by tweaking enemy drain values
revealed a deeper design mismatch: drain is deterministic (constant HP/s pressure), but
Fate is stochastic (probabilistic near-misses and Inspiration triggers). Cancelling a
probabilistic mechanic with an arithmetic offset works in aggregate but feels wrong in
play — you end up fighting a floating bubble with a sledgehammer.

**Why it was accepted:** Neekerbreekers has no Shadow, so Fate runs unchecked there.
That's correct for an early no-magic encounter — lucky near-misses and the occasional
Inspiration should make early fights feel survivable and miraculous. The 27% baseline was
a pre-Fate artifact. 40% with Fate active is the new locked reality for this encounter.

**The designed fix that isn't built yet:** Shadow was always intended as Fate's
counterbalance. Shadow drains Will and Fate toward a floor over time. In a Shadow
encounter, a high-Fate party starts lucky but gets progressively less lucky as the
Shadow deepens — evasion chances drop, Inspiration trigger rates drop. The player
counterplays with Radiance food to raise the drain floor. This creates the right
stochastic tension: Fate vs Shadow is a race between fortune and darkness, not
a fixed offset.

**What needs to happen before the combat system ships:**
1. Validate that Shadow encounters (Mirkwood, Moria, Morgul-vale) produce win rates
   that match the pre-Fate baseline at their intended food levels — specifically, that
   Shadow suppresses Fate enough to restore the difficulty curve at higher rungs.
2. If Shadow suppression is insufficient, tune the Shadow drain rate (`SHADOW_RATE`)
   or the Fate coefficients — but tune them together as a pair, not independently.
3. Do not retune Fate coefficients in isolation for no-Shadow encounters. Neekerbreekers
   is the wrong test bed for Fate balance.

**Locked encounter baselines (post-stat-bonuses, role-matched food, no Shadow):**
- Neekerbreekers FL1=47%, FL2=79%, FL3=99%, FL4=~100%
- Wolves in the Chetwood: not yet re-validated post-stat-bonuses (do this before V2 combat)

---

## Combat System (Simulator / V2+)

- **The 5th role (melee DPS)** — slot reserved in the sim and Mechanics Reference.
  Needs full design before the Campaign combat system is built. Key questions:
  party-of-5 vs field-4-from-roster; its mechanical identity vs the Might Hunter;
  name, template member, and what its Inspiration is.

- **Black Arrow IP review** — the Hunter Inspiration "Black Arrow" is direct
  Tolkien coinage. Needs review before public ship to confirm it falls under fair
  use or needs a rename.

- **Simulator slider alignment** — status sliders (Cold, Heat, Disease, etc.)
  still use raw internal values. The encounter JSON schema uses a locked 1–10
  severity scale. Align the sim sliders to match the schema as a polish pass.

- **Sim test: magic damage must not be reduced by physMit** — write a headless test
  (in `run_sim.js` or a dedicated test script) that sets physMit to a high value (e.g. 80%),
  runs a short fight, and asserts that Keeper effective DPS equals Keeper raw DPS × (1 − dread)
  with zero armor reduction. Catches any future refactor that accidentally routes magic damage
  through `physAfter`. The new "damage mitigated vs. magic bypass" chart makes this visually
  obvious in the browser, but a scripted assertion is the real safety net.

 — a `magMit %` on the enemy (parallel to `physMit %`) that
  resists magic damage. Currently the Keeper's Will-based attacks bypass armor entirely,
  which is correct at Rung 0 but may become unbalanced as magic damage scales at higher
  rungs or with stronger Keepers. When magic DPS starts outpacing physical in the sim's
  "damage mitigated vs. magic bypass" chart, introduce a `magMit` slider and a
  corresponding party counter (Radiance is the natural candidate, or a dedicated
  Spirit-penetration stat). Design the counter before adding the mitigation so the
  provisioner always has an answer.



- **Forgejo migration** — move repo from GitHub to self-hosted Forgejo
  when convenient. Trivial git remote swap, no rush.

- **Multiplayer ingredient trading** — if the game ever goes online,
  players of different bands trade region-exclusive ingredients.
  Noted as a natural mechanic, not a planned feature.

- **F-Droid submission** — submit to F-Droid once V1 is stable.

---

## Combat Role Subtype Design (V2+)

**Melee vs. ranged DPS by faction.** Different bands will have the Hunter role
filled by either a melee striker or a ranged archer, and this distinction will
need mechanical locking before V2 combat is built. Questions to resolve:
- Does melee DPS scale differently than ranged? (e.g., melee = higher Might coefficient; ranged = Agility-dominant as now)
- Are there enemy types that resist ranged or melee differently?
- Does the Hunter flavor slider (`hunterA` vs `hunterM`) become a melee/ranged flag instead of just a stat weight?
- How does this interact with armor penetration? (ranged = Agi pen; melee = Mig pen?)

Decision must be locked before the first V2 battleground roster is designed.
Each band's DPS member identity (melee vs. ranged) should be canonical in `docs/characters.md`.

---

## Design Identity Constraints (Battlegrounds, V5+)

These rules must be honored when the Battlegrounds endgame mode is eventually
built. Captured here so they aren't forgotten during V2/V3 design decisions.

- **Member growth must route through the player.** Stats, strength, and
  capability must improve because of what the player provides (better food,
  alchemical enhancement, gear) — never through members training independently.
  The whole game is that the band is nothing without you. Any mechanic that lets
  members become self-sufficient breaks the identity.

- **Provisioning as sustain, not a pre-battle buff.** In Battlegrounds, food
  is consumed continuously over hours — you stock a supply train, not one dish.
  This is the connective tissue between the provisioning game and the raid RPG.
  Do not dilute it into a simpler pre-battle buff model when the time comes.

Full Battlegrounds design: `docs/battlegrounds.md` and `docs/battlegrounds-rpg.md`
