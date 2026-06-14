# HearthCraft

An offline idle game for Android set in high fantasy.

You are a warlock-culinarian — potion master, chef, alchemist — and the indispensable hidden engine of a roving band of fighters. You never fight. You gather, grow, brew, and cook. The band succeeds entirely because of what you provide.

The line between a perfect dish and a potion is a matter of intent. Food is borderline magical. This is not a cozy cooking sim — it is a specialist identity fantasy rooted in craft and deep knowledge.

---

## Two Games in One

HearthCraft is designed as two interconnected games sharing the same economy:

**Game 1 — The Provisioning Loop (being built now)**
You gather wild ingredients and tend a farm and garden, cook recipes from a growing repertoire, brew preparations with alchemical properties, and provision a band of fighters before sending them on missions. The kitchen is the engine. Everything runs on what you make.

**Game 2 — The Raid RPG (future)**
A party combat RPG fought across named battlegrounds drawn from Tolkien's world — Barrow-downs, Weathertop, Mirkwood, and deeper still. The band has roles (tank, healer, damage, captain), individual stats, wounds, and the permanent possibility of death. Your cooking is their sustain — not a pre-battle buff but an ongoing supply train through hours-long engagements. Run out of provisions mid-raid and things go wrong.

These two halves are designed together from the start so that what is built now extends cleanly into what comes later. The provisioning loop is not a prototype — it is the permanent foundation. A mastered poultice that saves a life in the raid RPG is the same mastery system being built in the kitchen today.

---

## Factions

The player aligns with one of four bands at the start:

- **The Mithlost** — Ancient elves of the forest. Patient, sorrowful, deadlier than their gentleness suggests.
- **The Undermarch** — Mountain dwarves. Grim, loyal, stone-deep. They will remember who kept them fed.
- **The Freewake** — Sea corsairs. Chaotic, adaptable, opportunistic. They answer to no flag.
- **The Greycloaks** — Borderland wardens. Watchful, unaffiliated, worn. At the edges of civilization long enough that the edges feel like home.

Each band has distinct members, missions, and personality. The choice is permanent per run.

---

## What the Future Looks Like

Beyond the current provisioning loop, the planned arc:

- **V2** — Alchemy and potion-making; expanded 8-member rosters per band; multi-member provisioning
- **V3** — Deeper recipe discovery; ingredient rarities; the store and seed economy maturing
- **V4** — Band member growth through provisioning; wounds and the Houses of Healing
- **V5+** — The full raid RPG: battlegrounds, bestiary, stat system, real-time event log, the influence map of Middle-earth healing as shadow recedes

The shadow in the south-east is not idle. The world is in slow decay. What the player builds in the kitchen is not just comfort — it is the thing standing between the band and what is coming.

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
