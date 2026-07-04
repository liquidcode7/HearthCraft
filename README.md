# HearthCraft

An offline Android game set in high fantasy, built for the love of Lord of the Rings Online, the Ettenmoors, and the belief that the person who keeps the fighters alive is the most interesting person in the room.

---

## Why This Game Exists

Every fantasy RPG puts you in the armor. HearthCraft doesn't. You are the provisioner — the one who gathers, grows, cooks, and sustains a band of fighters through a war that needs winning. You never swing a blade. The band survives, fights, and grows entirely because of what you put in front of them before they leave the fire.

That's not a gimmick bolted onto a cooking sim. It's the whole premise, taken seriously:

- **You are an expert, not a hobbyist.** The provisioner is not a cook who stumbled into a war — they're an expert in hostile conditions whose craft is the difference between the band surviving and not. No whimsy, no cozy-game cutesiness, no self-deprecating "just cooking" humor. Recipes are terse and honest. After-action reports are specific and direct about what went wrong, not vague. The world is hard and your food is what keeps it manageable.
- **The band are people, not a fantasy party.** They're individuals in a bad situation being kept alive by one very capable provisioner — not faceless slots waiting for stat upgrades.
- **The line between a good dish and a potion is a matter of intent.** Food in this world is borderline magical, not metaphorically. Ingredients have power, preparation unlocks it, and feeding someone the right thing at the right moment changes what they're capable of. The band doesn't know if it's cuisine or sorcery, and they don't care. It works. *Raw athelas is a worthless weed until the right hands prepare it* — the cure for the worst wounds needs the player's mastered craft and the healer's skill together. Neither is enough alone.
- **The kitchen never stops mattering.** It's never automated, never becomes a checkbox you outgrow. The harder the game gets, the more your craft matters — not less.
- **One game, no gates.** No Early Access, no version 2.0 held back behind a paywall, no splitting the game into a "free" slice and a "real" one. Everything ships as one continuous project, aimed at a full F-Droid release: no ads, no in-app purchases, no accounts, no cloud saves, no internet requirement, ever. If a feature idea implies any of those, it doesn't belong in this game.

If any of that sounds like a lot of conviction for a mobile game — it is. That's deliberate.

---

## What It Is

You are the indispensable provisioner behind a band of fighters. You gather, grow, cook, and sustain. You never fight — the band does that, and they succeed entirely because of what you provide.

This is not a cozy cooking sim. It's a specialist identity fantasy rooted in craft, deep knowledge, and a war that needs winning. The long-term destination is a full raid RPG fought across named battlegrounds drawn from Middle-earth's history, with the provisioning game as its indispensable foundation — not a side activity bolted onto a combat game, the other way around.

---

## The Shape of the Game

```
THE CAMPAIGN — the core game, in active development
    Gather, grow, cook, provision, discover recipes
    Meet your band, feed them, grow them through your craft
    Unlock encounters and, eventually, named battlegrounds across the ages

        unlocks mid-to-late, once the campaign is genuinely mastered →

THE ETTENMOORS — the strategic endgame (long-term, not yet built)
    Brutal, fixed difficulty — never scales down to you
    Territory that ebbs and flows as you push deeper
    A separate Valor economy, earned and spent only there

NEW GAME PLUS — campaign only, long-term
    After mastering everything, raise the campaign's difficulty ceiling
    The Ettenmoors has no NG+ — it's already as hard as the game gets
```

The Campaign is the game. A player who never reaches the Ettenmoors gets a complete, satisfying experience — the Ettenmoors is a reward for genuine mastery, not a checklist gate. Once unlocked, both layers coexist: dip into the Moors for its particular pressure, return to the Campaign to keep developing.

**The kitchen is never automated and never becomes irrelevant.** There's always a reason to master another recipe, grow another band member, stock better ingredients — the game never outgrows its own foundation.

---

## The Three Peoples

At the start, the player picks the people whose band they'll provision for. The choice sets the flavor of the whole world — ingredient character, mission tone, the culture the player operates within.

**Men are being built first** and serve as the template every other people is validated against. Elves and Dwarves are parked, pending a redesign pass once the Men arc proves the systems out — their old data stays in the codebase for reference but isn't being built toward yet.

| People | Band | Home Region | Status |
|--------|------|--------------|--------|
| Men | The Greycloaks | Bree-land | Active — built first |
| Elves | The Mithlost | Ered Luin / Celondim | Parked, pending redesign |
| Dwarves | The Undermarch | Thorin's Halls | Parked, pending redesign |

Each band has exactly **four members, one per role** — no redundant position, no optional slot:

| Role | Identity |
|------|----------|
| **Captain** | Hybrid damage, the party's Dread anchor, jack of all trades — the band cannot function without them |
| **Warden** | The wall — takes hits, deals reliable physical damage, hardest to kill |
| **Keeper** | The party's sole healer and its arcane damage dealer — every tick is a heal or a hit, never idle |
| **Fighter** | Pure DPS, ranged or melee build chosen once at character creation |

Full mechanical design for stats, food, and combat lives in `design/master-design.md`.

---

## The Craft System

Everything the player crafts happens in one kitchen — there's no separate prep station. Gathering, farming, and foraging feed a pantry of graded ingredients (Crude through Pristine); higher grades multiply a recipe's effect above its baseline. Recipes are gated by tier, cooking level, and **grimoires** — rare drops that unlock a tier's recipes regardless of how strong the player's cooking level already is, so raw grinding can't skip the discovery curve.

Food provides **stat boosts only** — never a health-over-time effect. That distinction matters: it means the Keeper is the only thing keeping the band alive mid-fight, and food is about making the band better at what they do, not patching damage after the fact. There is no universally-correct food for a fight — a Keeper fed Vitality survives longer, a Keeper fed Will hits and heals harder, and reading which one the fight actually needs is the player's job.

**Houses of Healing** is a third, fully independent craft track alongside cooking and draught-making. It's the only mechanic capable of clearing a grievous wound — a wound severe enough that time alone won't fix it. The wounded member is out of the war for a meaningful stretch, and the band fights short-handed until the player crafts the cure and applies it.

---

## The Combat Model

The player never fights, but preparation is where every fight is actually won or lost. Encounters resolve over real-world time via a tick-by-tick simulation — provision the band, send them, come back later to a full recap.

- **The Streak system** — a small, universal crit mechanic. Every role's Fate stat drives an equal chance to trigger it; there's no role-specific treatment.
- **Inspiration** — rare, powerful, and named per role: the Warden's Horn of Gondor, the Fighter's Black Arrow, the Keeper's Hands of Healing, the Captain's Wrath, Ruin, and the Red Dawn. This is the Tolkien theme made mechanical — the moment a fight turns when it looks lost, not a routine cooldown.
- **Dread, Despair, and Maladies** — status effects that punish an unprepared band multiplicatively, not additively, so no amount of raw stat investment alone out-scales the right draught or preparation.
- **Wounds** — ordinary wounds heal with time and food, back in the player's domain. Grievous wounds require the Houses of Healing track above. Wounds never kill — there's no death-by-combat mechanic in the game today.

Full combat design lives in `design/master-design.md`.

---

## Current State

The provisioning loop is complete and playable end to end: gather → grow → cook → provision → send → fight → recap. Recent work consolidated the app's navigation around that loop — Missions is the single place to launch and provision an encounter (with a live, plain-language odds estimate), Journal holds each character's stats and story, Band is a pure roster view, and House of Healing has its own tab with a full craft-then-treat flow.

**What exists today:**
- Full gather → cook → provision → mission loop, with ingredient quality grades (Crude → Pristine) affecting recipe output
- Kitchen with tiered recipes gated by cooking level, band level, and grimoire discovery
- Missions tab: encounter selection, per-member provisioning, a live Monte-Carlo win-odds estimate, and a real-time battle-in-progress view with post-fight recap (per-member damage/healing, wound recap)
- Journal: per-character stats and bio, plus a stats/food-effects glossary and discovered-recipe list
- House of Healing: its own crafting track, wounded/recovering status, and a two-step craft-then-apply treatment flow
- Band members with five stats (Might, Agility, Vitality, Will, Fate) that grow through combat XP, per-role growth curves
- The Streak (universal crit) and Inspiration (per-role heroic moment) systems, fully implemented in the encounter engine
- Wound system: ordinary wounds (time + food) and grievous wounds (Houses of Healing required)
- Market for buying seeds, a Pantry for tracking stock
- The Greycloaks (Men): four named members, fully playable band

**What's next:** manual on-device verification of the recent navigation work, a gather/farm yield balance pass, and populating grievous-wound specifics on more encounters.

---

## Tools

Design and balance work happens outside the Android app before it touches the game, in `tools/`.

### `tools/sim/`
- **`hearthcraft_fight_sim.html`** — standalone browser-based fight simulator. Open directly, no server required. Configure party stats, food buffs, and hazard antidotes, or import an encounter directly; simulates tick-by-tick with a live event log.
- **`run_sim.js`** — the same simulation math, headless, for running large batches of fights from the command line.
- **`export_encounters.py`** — reads the encounter design spreadsheet and writes JSON into `app/src/main/assets/data/`.
- Reference spreadsheets for encounter building, tier planning, and mechanics constants.

### `tools/recipe-browser/`
A read-only recipe and ingredient browser, generated from the live game data. Run `node tools/recipe-browser/generate.js` to produce `recipe-browser.html` — a tabbed page (by region and recipe class, plus an ingredient-usage view) for reviewing the recipe roster and spotting ingredients that no recipe uses yet.

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

Requires a JDK (Android Studio's bundled JBR works fine) and the Android SDK.

```bash
export JAVA_HOME=/path/to/a/jdk   # Android Studio's bundled JBR works
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
