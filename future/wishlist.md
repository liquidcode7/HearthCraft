# HearthCraft — Wishlist

> Deferred ideas. Add freely. Never act on during V1.
> When V1 ships, this becomes the starting point for V2 planning.

---

## Gameplay Systems

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

## Technical / Platform

- **Forgejo migration** — move repo from GitHub to self-hosted Forgejo
  when convenient. Trivial git remote swap, no rush.

- **Multiplayer ingredient trading** — if the game ever goes online,
  players of different bands trade region-exclusive ingredients.
  Noted as a natural mechanic, not a planned feature.

- **F-Droid submission** — submit to F-Droid once V1 is stable.

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
