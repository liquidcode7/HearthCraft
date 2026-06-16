# HearthCraft — Redefinition Document

> Status: Draft — June 16, 2026
> Purpose: To formally supersede the original framing and establish the current
> structure of the game as the authoritative design direction. Existing docs
> (design.md, v1-plan.md) are being updated in parallel to match this. The
> `future/` scratchpads remain reference material, not build instructions.

---

## What Changed and Why

HearthCraft began as a cooking idle game. It is still built on a cooking and
provisioning loop, but the scope of what that loop is *in service of* has been
fully redefined.

The original framing — "warlock-culinarian, potion master and chef" — was
dropped. The player is not a chef who happens to support fighters. The player is
the indispensable provisioner behind a band of fighters in a war for Middle-earth:
they gather, grow, cook, and sustain, and the band succeeds entirely because of
what they provide. The player never fights.

This is one game with two connected halves, not a cooking game with a bolted-on
combat mode. The cooking, the band, the battlegrounds, the growth — all one
integrated experience. The player title is **[PLACEHOLDER — not yet decided]**;
"Hearthwright" and "warlock-culinarian" are both deprecated.

---

## The Shape of the Game

HearthCraft has two halves and an endgame difficulty mode:

```
THE CAMPAIGN — the core game, the whole experience for most players
    │  cooking, provisioning, gathering, recipe discovery
    │  band members: meet them, feed them, bond with them, grow them
    │  battlegrounds across the ages of Middle-earth (a living roster,
    │      not a linear march — it expands as you grow, it does not "end")
    │  bank materials, build power, master your craft
    │
    │  ── unlocks gradually, mid-to-late, when you are genuinely ready ──>
    ▼
THE ETTENMOORS — the strategic endgame layer, coexists with the campaign
    │  brutal, fixed difficulty — never becomes easy, never scales down to you
    │  manage multiple forces / opening factions at once
    │  take ground, hold ground, push deeper — territory ebbs and flows
    │  Valor: earned here, spent on Moors-specific gear, runes, recipes,
    │      ingredients, skills, and bonuses (its own walled economy)
    │  Valor purchases ease specific encounters but never make the Moors easy
    │  one continuous space — the deeper you hold, the harder it gets:
    │      frontier → contested ground → deep territory → raid encounters → bosses
    │  the biggest battles in the game live at the back of this map; you reach
    │      them by holding enough ground to push that far, not by a separate unlock
    ▼
NEW GAME PLUS — campaign only
       after you have maxed everything, raise the campaign's difficulty ceiling
       and run the battlegrounds harder. (The Ettenmoors has NO NG+ — it is
       already as hard as the game gets.)
```

---

## The Campaign (the core)

This is the game. A player can play only the campaign and have a complete,
satisfying experience. It is where everything fundamental lives:

- **The cooking and provisioning loop** — gather, grow, cook, provision, send
  the band out, collect rewards, push harder. The kitchen is never automated;
  it stays a place of real decisions no matter how deep you are.
- **The band** — you meet named members, learn who they are, feed them what they
  like, form bonds, and grow them through your provisioning and the deeds they
  survive. Member growth routes through the player — always.
- **Battlegrounds** — set-piece engagements drawn from across the ages of
  Middle-earth. A living roster you unlock into as you grow, each a discovery
  with hidden requirements solved through the clue system. Not a sequence with a
  terminus — it expands.
- **Growth, banking, mastery** — recipes learned, ingredients found and stocked,
  crafting skill raised, power built. The long investment.

The campaign does not "end." It keeps offering harder content, and New Game Plus
raises the ceiling again once you hit the top.

---

## The Ettenmoors (the strategic endgame)

A graduation from the campaign, not an alternative to it. You reach it by
becoming genuinely ready — depth of craft, recipes, ingredients, member bonds,
power — not by clearing a checklist. It unlocks mid-to-late and then coexists
with the campaign: you dip into the Moors for its kind of pressure and return to
the campaign to develop further.

What makes it distinct:

- **Brutal, fixed difficulty.** The Moors never scale down to you. Going back to
  the campaign to grow stronger is the correct response to a wall, not a failure.
  This is precisely what keeps the campaign and the kitchen relevant forever — the
  Ettenmoors always demands more than you currently have.
- **Force management at scale.** You control several of the opening factions at
  once. The campaign is one band; the Moors is a war council. (See open question
  below on how provisioning scales across multiple forces.)
- **Living territory.** Ground you take can be lost again over time — the natural
  ebb and flow of a contested front that never fully closes. Endless by nature.
- **The Valor economy.** Earned in the Moors, spent only in the Moors, on
  Moors-specific gear, runes, recipes, ingredients, skills, and bonuses. Walled
  off from the campaign economy so neither inflates the other. Valor purchases
  can ease specific encounters but never trivialize the mode.
- **The raid at the back of the map.** Pre-boss encounters and world-ending boss
  fights are the deepest territory of the Ettenmoors. You reach them by mounting a
  sufficient, sustained, focused campaign — taking and holding enough ground to
  push that far. Geography is the gate; there is no separate unlock.

---

## What Keeps the Base Game Relevant After the Endgame Unlocks

This was the central design worry, and the structure resolves it:

1. **The Ettenmoors never gets easier.** Because difficulty is fixed and brutal,
   there is always a reason to return to the campaign to develop another member,
   master another recipe, or stock better ingredients.
2. **You survive the Moors on what you built in the campaign.** The campaign is
   the investment; the Ettenmoors is where it is tested. They are interdependent,
   not sequential.
3. **The kitchen is never automated and its complexity keeps scaling.** Harder
   content demands provisioning solutions you have not had to think about before.
4. **NG+ gives campaign-only players an infinite hard mode** so there is no
   "I'm done" cliff even for those who never touch the Moors.

---

## What Did Not Change

- **Offline-first, F-Droid compatible, no accounts, no internet, no ads, no IAP.**
- **The player never fights.** Growth and healing route through the player.
  Members never self-improve. The player stays indispensable.
- **Cooking is the sustain mechanic.** In battlegrounds and the Moors, food is a
  supply train consumed over time, not a pre-battle buff.
- **Inspiration belongs to the fighters, not the player.** The player is why they
  are standing there; the rally is theirs.
- **Ingredient quality tiers are unwanted** — complexity lives in the cook's
  skill and decisions, never raw materials.
- **Mission failure is outright** — no partial success. Failed experiments
  consume ingredients.
- **Four starting bands as permanent flavor choices**, no mechanical difference:
  The Mithlost, The Undermarch, The Freewake, The Greycloaks.
- **GW2 seven-tier cooking structure** (Hearthkeeper → Grandmaster), no craft
  branches. Specialization trees deferred to post-max-level (wishlist).
- **Grimoires gate deepest recipes** within tiers 3, 5, and 7 as rare drops.
- **The five-tier bestiary** (Rabble, the Fell, Banes, the Nine, Nameless Things),
  power and untouchability kept separate, the Nine never killed.

---

## Corrected From Earlier Drafts

- The "three-layer model" framing is dropped — it implied the cooking game was a
  tutorial you graduate out of. It is not. One integrated game.
- "The base game must stand alone" is dropped — the campaign IS the core, not a
  prologue you can skip.
- The "four-faction gate" (develop four bands to threshold to unlock the Moors)
  is softened to a natural readiness gate. You graduate into the Ettenmoors by
  becoming deep enough, not by completing a checklist.
- The Ettenmoors raid tier is NOT a separately-unlocked mode. It is the deep
  territory of the Moors map, reached by holding ground far enough to get there.
- Healer heroic peak renamed **Hands of Healing** (was Pull-Back).
- Damage heroic peak renamed **Deadeye** (was Slaying).
- Dread aura applies to any dread-source (Nazgûl, Balrog, Shelob, etc.), not
  only the Nine.
- Buff type "focus" renamed **acuity**.

---

## Open Questions (logged, not blocking)

1. **Provisioning across multiple forces in the Ettenmoors.** In the campaign you
   cook for one band. In the Moors you manage several factions at once. Does the
   kitchen scale up to supply all of them (provisioning demand multiplies), or
   does the Ettenmoors have its own supply logic? This is the most important
   unresolved Ettenmoors mechanic and should be decided before that layer is
   built — but it does not block V1 or V2. Resolve after the campaign is playable.

2. **The Ettenmoors readiness gate.** What concretely signals "you are ready" —
   crafting tier, recipe count, battlegrounds cleared, member development, or a
   blend? Should feel earned and discovered, not announced as a progress bar.

3. **The deepest campaign battleground.** The campaign has no hard ending, but the
   battleground roster needs a felt high-water mark. What sits at the top, and how
   does it relate to the Ettenmoors unlock? (Likely the Moors opens before the
   campaign's hardest content, so both remain live.)

4. **V1 combat fidelity.** When battlegrounds eventually land, V1-era combat should
   be a stripped skirmish-tier threshold model behind an interface that can upgrade
   to the full two-pool model without a rewrite.

---

## Impact on V1

None. V1 is still the core provisioning loop: gather → cook → provision → mission
→ rewards. Nothing in this redefinition changes what gets built next. The structure
above is the destination that quietly informs architecture — keep mission
resolution extensible toward multi-member, multi-stage, variance-based outcomes —
but it does not pull focus from shipping the small game first.
