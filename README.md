# HearthCraft

An offline idle RPG for Android set in Middle-earth — built for the love of Lord of the Rings Online, the Ettenmoors, and the belief that the person who keeps the fighters alive is the most interesting person in the room.

---

## What It Is

HearthCraft is one integrated game. You are the indispensable provisioner behind a band of fighters in a war for Middle-earth. You gather, grow, cook, and sustain. The band succeeds entirely because of what you provide. You never fight.

The line between a perfect dish and a potion is a matter of intent. Food is borderline magical — not metaphorically. Ingredients have power; preparation unlocks it; feeding someone the right thing at the right moment changes what they are capable of. The band doesn't know if it's cuisine or sorcery. They don't care. It works.

This is not a cozy cooking sim. It is a specialist identity fantasy rooted in craft, deep knowledge, and a war that needs winning.

---

## The Shape of the Game

```
THE CAMPAIGN — the core game
    Cook, provision, gather, discover recipes
    Build your band — meet them, feed them, grow them through your craft
    Unlock battlegrounds across the ages of Middle-earth
    Master your kitchen; bank power; develop every member

        unlocks mid-to-late, when you are genuinely ready →

THE ETTENMOORS — the strategic endgame
    Brutal, fixed difficulty — never scales down to you
    Manage multiple factions at once as high command
    Take ground, hold it, push deeper — territory ebbs and flows
    Valor economy: earned here, spent on Moors-specific gear and upgrades
    The biggest fights in the game live at the back of this map;
    you reach them by holding enough ground to push that far

NEW GAME PLUS — campaign only
    After maxing everything, raise the campaign difficulty ceiling
    The Ettenmoors has no NG+ — it is already as hard as the game gets
```

The Campaign is the game. A player who never touches the Ettenmoors has a complete, satisfying experience. The Ettenmoors unlocks because you became genuinely ready — depth of craft, developed members, mastered recipes — not because you cleared a checklist. Once unlocked, both layers coexist: you dip into the Moors for its kind of pressure and return to the Campaign to develop further.

**The kitchen is never automated and never becomes irrelevant.** The Ettenmoors never gets easier, so there is always a reason to go back and master another recipe, grow another member, stock better ingredients.

---

## The Four Bands

At the start, the player chooses two bands to work with. The choice sets the flavor of the entire world — mission names, ingredient character, the culture you operate within. Developing both bands is what eventually opens the Ettenmoors.

### The Mithlost — Forest / Elves
Ancient, grey, lingering. The ones who stayed to fight a long defeat when others sailed West. Sorrowful, faithful, deadlier than their gentleness suggests. They do not conquer land — they tend it. Their missions feel like stewardship: protecting what exists, restoring what was lost.

### The Undermarch — Mountain / Dwarves
Grim, loyal, stone-deep. Words mostly unnecessary. They will not complain about a hard march or a bad night, but they will notice — and remember — who kept them fed. Their missions go into the deep places: cave-ins or worse, holds to reclaim, passes to defend.

### The Freewake — Sea / Númenórean Corsairs
Their bloodlines trace to Númenór — mariners who carried the old knowledge of sail and star long after the island sank. They answer to no current king because they remember kings who have been dust for three thousand years. Proud, long-memoried, formal in ways that surprise newcomers. Of all the bands, only the Greycloaks share the same ancient blood — two branches of the same lineage, grown in different directions.

### The Greycloaks — Eriador / Dúnedain of the North
The remnant of the line of Isildur. Heirs to a kingdom that fell and was never rebuilt. They walk the wild lands of Eriador not as wanderers but as guardians bound by an oath older than the Shire. They have no home because their home is gone. They wait for the return of the King of the North, and in the meantime they hold the darkness back with their own hands. They are not what they once were. They are more than the world knows.

---

## The Roster

Thirty-two named members across four bands — eight per band, two per role. They are not faceless stats. They are people. Permadeath is real, and every role has a backup within the band so a single loss never leaves a gap that cannot be filled.

Four roles: **Warden, Keeper, Hunter, Captain.** Each essential. The four-role party is the only formation that works — no redundant position, no optional slot. Full roster in `docs/characters.md`.

---

## The Battlegrounds

Fourteen named set-piece engagements drawn from the history of Middle-earth. Each is a discovery with hidden requirements solved through a clue system — requirements are never stated outright. A failed run returns after-action text naming the nature of failure, not numbers.

**The Angmar Wars (Third Age ~1409–1975)**
- Cleansing of the Barrow-downs
- The Fall of Fornost
- Holding the Ettenmoors
- The Host of the West at Fornost — the reconquest
- Storming of Angmar / Assault on Carn Dûm

**The War of the Ring and beyond**
- The Paths of the Dead
- Battle under the Trees / Mirkwood
- The Cleansing of Dol Guldur
- Helm's Deep
- The Battle of the Pelennor Fields
- The Black Gate / Morannon
- Battle of Dale / Siege of Erebor
- Battle of Azanulbizar
- The Fall of Gondolin *(First Age, mythic register)*

Full lore and design in `docs/battlegrounds.md`.

---

## The Combat System

The player's agency is in preparation and composition. The raid resolves over real-world time through a live event log. This is an idle game: provision, send, come back later.

**Four roles:**
- **Warden** — holds space and attention; heroic peak: the Last Stand
- **Keeper** — sustains life and counters corruption; heroic peak: Hands of Healing (snatch a fallen member back mid-raid)
- **Hunter** — drives kill speed, exploits vulnerabilities; heroic peak: Deadeye (fell a Bane in a single blow)
- **Captain** — force-multiplier and dread-resistance; heroic peak: Inspiration

**Inspiration** is the Tolkien theme made mechanical. When resolution projects a loss, a member can become borderline supernatural — Beren in Angband, Éowyn against the Witch-king, Sam carrying Frodo. Not a reliable cooldown. Grace. The Captain makes it more likely; the whole company rising at once is rarer still. Inspiration cannot beat the apex — against the Nine it converts Defeat into Stalemate.

**The enemy:** five canonical tiers, power and untouchability kept separate.
- **Rabble** — orcs, Easterlings, hired swords; dangerous in numbers
- **The Fell** — warg-riders, orc captains, barrow-wights; dangerous individually
- **Banes** — dragons, wight-lords, ancient things; felling one is a legendary deed
- **The Nine** — the Nazgûl; never killed, only survived or repelled; their Dread aura buffs the entire host and breaks your company's will
- **Nameless Things** — beneath Moria; not a combat target at all

Full systems design in `docs/battlegrounds-rpg.md` and `docs/bestiary.md`.

---

## Wounds and the Houses of Healing

Ordinary wounds heal with time and the right food — back in the player's domain. Grievous wounds require the Houses of Healing, a healer of sufficient power, a rare alchemical preparation discovered and mastered by the player, and time. The wounded member is out of the war for a meaningful stretch. You fight short-handed.

*Raw athelas is a worthless weed until the right hands prepare it.* The cure requires the player's mastered preparation and the healer's skill together. Neither is sufficient alone. The player's identity holds even at the deepest moment of crisis.

---

## Current State

The provisioning loop is in active development. It is not a prototype to be replaced — it is the permanent foundation everything else extends.

**What exists today:**
- Working gather → cook → provision → mission loop
- Farm plot, four garden slots, forage sessions with seed trickle
- Probability-based missions gated by band vitality; food strength improves odds
- Kitchen with tiered recipes (Apprentice / Journeyman / Craftsman) by cooking level
- Band members with five stats (Might, Agility, Vitality, Will, Fate) that grow through missions
- Wound system: ordinary wounds and grievous wounds with different treatment requirements
- Market for buying seeds with earned gold
- Harvest collect mechanic — forage and farm results are claimed actively, not auto-deposited
- Intro screen with opening lore, band selection, and welcome quotes
- Four bands: The Mithlost, The Undermarch, The Freewake, The Greycloaks — each with four named members (Captain, Warden, Keeper, Hunter)

---

## What This Game Is Not — Ever

- No combat for the player — the band fights so you never have to
- No multiplayer, trading, or leaderboards
- No cloud saves or accounts
- No ads, IAP, or real-money transactions of any kind
- No internet requirement — ever
- No energy systems or artificial time pressure

---

## Build

Requires Android Studio (JBR included) or a standalone JDK 11+.

```bash
./gradlew build
```

Minimum SDK: API 26 (Android 8.0). No Google Play Services required. F-Droid compatible.

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Database**: Room
- **Background work**: WorkManager
- **DI**: Hilt
- **Serialization**: kotlinx.serialization
- **Architecture**: MVVM + Repository

---

## License

GPL-3.0. See [LICENSE](LICENSE).
