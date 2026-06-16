# HearthCraft — Character Progression & Stats

> Authoritative design document — June 2026.
> Stat system and progression design for band members. Not fully built yet —
> stats exist in V1 but growth mechanics are minimal. This is what they grow into.
> Companion files: battlegrounds.md, battlegrounds-rpg.md, bestiary.md.
> Stat foundation is LotRO's, trimmed to what HearthCraft's systems actually use.

---

## The Foundational Principle: Greatness Is MADE, Not Born

The most important rule in the whole progression design, and it overrides any
convenience that contradicts it:

**Every member can rise to the heights. No one is born capped.**

This is core Tolkien — "even the smallest person can change the course of the
future." Frodo the comfortable hobbit, Sam the gardener, Merry and Pippin the
baggage who become a knight of Rohan and a Guard of the Citadel, Bard the grim
bowman who fells the greatest dragon of the age. The small and overlooked rise
to greatness through what they DO and what is poured into them — never through
bloodline or a printed ceiling.

Therefore: NO innate tier, NO rarity roll, NO fixed potential cap. A gardener and
a lord start far closer than any RPG would have it, and the gardener can surpass
the lord if you invest in the gardener and neglect the lord.

### What this means mechanically
- Members are not "leveled up a ladder," they are REALIZED — brought toward
  greatness that was always possible.
- The distance between a regular Captain and an "Earendil-reincarnate" Captain is
  entirely the distance YOU carried them. There is no lucky legendary pull; there
  is only "I took this ordinary person and made them a legend."
- This is the purest expression of the indispensable-player fantasy AND the
  truest to the books.

### Preserving individuality without a ceiling
"Anyone can become great" must NOT make everyone an identical stat-block.
Tolkien's heroes rise AS THEMSELVES (Sam stays a gardener who did the
impossible). Individuality lives in WHO THEY ARE and HOW THEY GROW, never in a
limit on how far:
- **Traits** = the seed of identity and starting tendencies (see below). A
  "Stubborn" member starts strong in Will; a "Gentle" one leans toward healing.
  Traits suggest a path of least resistance, never a locked door.
- **Growth shaped by deeds + treatment** — a member becomes great along the road
  YOU walk them. Feed a quiet soul through enough desperate battles and they can
  awaken into a Captain (Merry/Pippin made into warriors by the war).
- **Surprising arcs are a FEATURE** — the hireling you almost retired becomes the
  Captain who repels a wraith. Only possible because greatness is unbounded.

### What keeps a fully-realized legend rare (if anyone can get there)?
TIME and SURVIVAL. Reaching the heights takes a long road and surviving content
that kills lesser-developed members. Legendary members are rare not because few
CAN get there, but because few SURVIVE the journey. Permadeath is what makes
realized greatness precious. The cap isn't on potential — it's on how many make
it.

---

## The Stats (LotRO base, trimmed)

We adopt LotRO's base stats because they're proven, thematically perfect, and
already encode the dread/fear/wound resistances this horror-heavy endgame needs.
But we CUT what an MMO needs and we don't.

### Five base stats — UNIVERSAL (every member has all five)
Universal stats with no class locks is the mechanical form of "anyone can become
great" — a gardener can grow high Will and become a healer or Captain.

- **Might** — physical strength. Melee damage, block/parry, physical durability.
- **Agility** — finesse. Ranged damage, evasion, crit-to-hit, accuracy.
- **Vitality** — life force. Sets the **Morale** pool size; governs resistance to
  Wounds, Fear, Poison, and Shadow; mitigates non-physical damage. The
  toughness-and-resistance stat — the backbone of the wound system.
- **Will** — strength of spirit = **the magic stat.** In Middle-earth, power over
  the unseen IS spiritual strength (Gandalf vs the Balrog is a contest of wills).
  Governs: magical/healing potency (a healer's "Grace"/beastliness scales off
  Will), resistance to **Dread** (what lets a unit stand near the Nine), and is
  the substrate of inspiration. The caster/healer/Captain stat.
- **Fate** — fortune and the unlooked-for turn. Crits (including crit-heals that
  save a life against the odds), momentum, and a key input to inspiration odds.
  Tolkien's theology of providence/eucatastrophe lives here.

### One resource pool — Morale (NOT health, NOT "HP")
- **Morale** — "the amount you can take before being DEFEATED." When it empties a
  unit is defeated, not necessarily dead — exactly the distinction we want
  between losing a battleground and losing a character. Driven by Vitality.
- Morale IS the survivability pool (`P_effective`) in the two-pool combat model
  (party Morale vs enemy Morale).

### CUT: Power (deliberately removed)
LotRO has a second pool, Power, that fuels ability buttons in real-time combat.
HearthCraft is PROVISION-THEN-RESOLVE — no moment-to-moment ability economy — so
Power solves a problem we don't have. CUT IT. Crucially:
- **Our sustain mechanic IS the Power pool, relocated to the kitchen.** The limit
  on how much the party can do over a long raid is HOW MUCH FOOD YOU PACKED.
  Provisioning is the resource economy. A per-member Power pool would be a
  redundant second economy competing with our core mechanic.
- Removing Power also makes inspiration cleaner: it is NOT a spent resource, it's
  a rare brink-triggered grace, triggered off Will/Fate odds, not drained from a
  pool.

### Net: five stats + Morale. Nothing that doesn't drive a real mechanic.

---

## Roles Emerge From Stat Distribution (no hard class locks)

The four raid roles are not classes you pick — they emerge from how you've
developed a member's stats, plus traits, plus gear. Any member can be grown
toward any role (the "smallest can become great" principle in action).

- **Warden** — Might + Vitality. High Morale, soaks/redirects enemy damage. The
  wall that keeps enemy damage off the party. (Signature behavior: "Bulwark.")
- **Hunter** — Might (bruiser) or Agility (nimble striker). Two flavors. Drives
  party damage; exploits enemy damage-type vulnerabilities via gear.
  (Signature behavior: "Edge.")
- **Keeper** — Will + Fate. Will sets how DEEP a grievous wound they can mend
  (beastliness); Fate gives the crit-heal that saves against the odds. The
  whole Houses-of-Healing system keys off this. (Signature behavior: "Grace.")
- **Captain** — Will + Fate, the inspirer. Will to resist Dread and stand near
  the Nine; Fate for the fortune that turns a battle. Valor (the inspiration
  capability) is what a Captain DOES with high Will + Fate — named as a behavior,
  not tracked as a separate base stat. (Signature behavior: "Valor.")

NOTE on signature behaviors (Bulwark/Grace/Edge/Valor): these are NAMES for what
a role does with the five base stats, NOT extra base stats. We considered adding
them as stats and decided the five base stats already do the work; inventing more
was solving problems LotRO already solved. Keep them as flavor/derived labels.

---

## Traits (identity, never caps)

Stats are the continuous grown numbers. Traits are discrete characteristics that
shape tendencies, starting points, and the SHAPE of growth — never the limit.
They formalize the personality/food-preference work band members already have.
Examples:
- "Stubborn" — strong Will floor, resists fear; natural toward Captain/holding.
- "Glory-hungry" — Valor/inspiration comes readily but takes more risks.
- "Gentle" — favors Will/healing (Grace), disfavors raw Might/Edge.
- "Watchful" — Agility/perception leanings; natural scout/striker.
Traits are WHO THEY ARE. Some are starting seeds; a very small number of rare,
legendary deeds might grant or deepen a trait (earned, not bought).

---

## What Drives Realization (how members actually grow)

Growth routes through the PLAYER — never independent training (the identity
constraint). Two intertwined drivers:

1. **Your provisioning (the engine).** Your cooking permanently advances members
   toward greatness over time. This is the thematic heart: the kitchen is how a
   gardener becomes a hero. Because there's no ceiling, there's no limit on how
   far your care can take someone. (This is YOUR Will working on them through
   the craft — see player section.)
2. **Deeds survived (where growth happens).** Members grow by surviving hard
   content — but they can only survive it because you provisioned them, and they
   metabolize the deed into strength afterward. You can't grow a member who never
   fights, nor one you feed poorly. Both needed → keeps the kitchen indispensable
   without being the ONLY thing.

OPEN QUESTION: exact curve/weighting of provisioning vs deeds in realization.
Decide at build time with playtesting.

---

## The Player Has NO Stat Sheet — Your Craft IS Your Power

The player (warlock-culinarian) does NOT have Might/Agility/etc. You never fight.
Your strength is your CRAFT, and your gathering/cooking/alchemy levels already
measure it:
- A high-level alchemist produces stronger preparations the way a high-Will mage
  casts stronger spells — they are the same thing.
- Your potions/preparations are as potent as your craft is deep.
- When your cooking permanently grows a member, that is YOUR Will working on them
  through the craft over time.
This keeps the player mechanically DISTINCT from the band (they have stat sheets;
you have mastery) and reinforces that you are a different kind of being in the
operation — not a fighter with stats, but the spirit working behind all of them.

---

## How It All Plugs Into Systems We Already Designed

Nothing here is invented for its own sake; every stat feeds a live mechanic:
- **Two-pool combat** → party Morale vs enemy Morale; Might/Agility drive damage,
  Vitality/Warden drive Morale.
- **Wound system** → Vitality governs wound thresholds, ordinary-wound recovery,
  and resistance to Fear/Shadow.
- **Houses of Healing** → healer's Will (+ Fate) sets how deep a grievous wound
  they can mend ("beastliness") and the crit-heal chance.
- **Inspiration** → triggered off Will + Fate (+ hidden levers); NOT a spent
  pool. Will also lets a Captain resist Dread enough to act near the Nine.
- **Dread / the Nine** → Will resists "cowering from Dread"; low-Will units break.
- **Provisioning** → the relocated resource economy (the cut Power pool's job),
  AND the permanent growth engine for members.

---

## Open Questions
- Exact realization curve: weighting of provisioning vs deeds; how fast.
- Do signature behaviors (Bulwark/Grace/Edge/Valor) ever need to be surfaced to
  the player as numbers, or stay purely derived/flavor?
- Can rare legendary deeds grant/deepen traits — and how rare?
- Whole-company inspiration vs single-hero (from battlegrounds-rpg.md) interacts
  with Captain Will/Fate — resolve together when inspiration is built.
