# HearthCraft — Development Journal

> One entry per session. Written to be pasted into claude.ai to keep the design
> conversation in sync with the codebase. Each entry covers: what was built,
> decisions made and why, anything that diverged from the original design,
> and what's next.

---

## Session 1 — June 8, 2026
**Phase 0 started: Project Setup**

Initialized the Android project scaffold. Bare bones — just the app skeleton,
no game code yet.

**What was built:**
- Android project created in Android Studio
- Repo initialized on GitHub

**What's next:** Get CLAUDE.md and the design docs into the repo so the
project has a contract to build against.

---

## Session 2 — June 10, 2026
**Phase 0 complete: Docs and contract in place**

Got the design documents and working instructions into the repo. The project
now has a source of truth for what it is and what V1 includes.

**What was built:**
- `CLAUDE.md` — working instructions for Claude Code, session start/end
  routines, scope discipline rules
- `docs/design.md` — full game design document
- `docs/v1-plan.md` — phased build plan, V1 scope definition
- `docs/wishlist.md` — deferred ideas
- `design/` folder — idea log and annotated drafts for human reference

**Decisions made:**
- CLAUDE.md lives at repo root so Claude Code always reads it at session start
- Design docs live in `docs/` and are the authoritative reference
- The `design/` folder is human-only — not authoritative, Claude doesn't act
  on it

**What's next:** Phase 1 — add library dependencies and architecture scaffold.

---

## Session 3 — June 11, 2026
**Phase 1 and Phase 2 complete: Architecture and game data**

Added the full dependency stack and all game data as JSON files. The project
now knows what it is mechanically, even though there's no Kotlin game logic yet.

**What was built:**

Phase 1 — Dependencies and scaffold:
- Room (SQLite database), Hilt (dependency injection), WorkManager (background
  sessions), kotlinx.serialization (JSON parsing), Material 3 (UI)
- Hilt application class (`HearthCraftApp`)
- Empty Room database class

Phase 2 — Game data JSON:
- `bands.json` — four bands with names, descriptions, regional flavor
- `band_members.json` — 3–4 members per band with name, personality, food
  preference, and quirk note
- `ingredients.json` — 12 ingredients (6 farm, 6 forage)
- `recipes.json` — 9 recipes covering all 5 buff types
- `missions.json` — 12 missions (3 per band: easy/medium/hard)

**Decisions made:**
- `fallbackToDestructiveMigration(true)` on the Room database — intentional for
  V1 development. Wipes the database on any schema change rather than requiring
  migration scripts. Will need real migrations before the game ships to anyone.
- `exportSchema = true` on the database — Room exports a JSON schema file
  per version to `app/schemas/`. Good practice, tracks schema history.
- Hilt needs `@HiltAndroidApp` on the Application class — that's the entry
  point for the whole injection graph.

**Code review fix applied this session:**
Three issues caught and fixed before committing Phase 1: migration strategy
(switched from addMigrations to fallbackToDestructiveMigration), schema export
flag was missing, Hilt entry point annotation was wrong.

**What's next:** Design review before Phase 3 — the data needed refinement.

---

## Session 4 — June 12, 2026 (morning)
**Design revision before Phase 3: Greycloaks, acuity, flavor system**

Before writing any Kotlin game logic, the game data got a significant design
pass. Several things didn't feel right or had evolved since the original plan.

**Changes made:**

**1. Nomadic Confederation → The Greycloaks**
The fourth band was redesigned from the ground up. The Nomadic Confederation
(desert, trade-connected, djinn-touched) was replaced with The Greycloaks —
wandering borderland wardens who answer to no crown and hold no land. Region
changed from Desert to Borderlands.

Why: The Greycloaks feel more grounded and more useful mechanically. Their
identity is perception and careful observation, which maps cleanly to the
`acuity` buff type. They're also the band that most closely mirrors the player
character — people who operate in the margins, indispensable and unaffiliated.

New members: Aldric (veteran warden, earthy food preference), Mira (sharp and
watchful, light food preference), Cael (keeps a detailed field journal, herbal
food preference).

New missions: Track the Border Crossing (acuity 10), Read the Waystone Marks
(acuity 20), Find the Lost Garrison (acuity 25). All three are acuity missions
— the Greycloaks are a specialist band and their missions reflect that.

**2. "focus" renamed to "acuity"**
The buff type previously called "focus" is now "acuity" throughout — recipes,
missions, band member data, and code. Acuity means sharpness of perception and
readiness to read a situation correctly. It fits the game's tone better than
focus, which reads as generic productivity language.

**3. Food flavor system added**
Recipes now have a `flavorTag` field: sweet, hearty, light, spicy, herbal, earthy.
Band members now have a `foodPreference` field (same vocabulary) instead of a
`preferredBuffType`.

Why this matters: Previously, band member quirks were mechanical (member X
responds better to endurance buffs). The new system is characterful instead —
Dagra Copperhelm wants spicy food because she laughs at mild food the way she
laughs at everything else. Old Mossback wants hearty food because he doesn't
say much about food either.

The mechanical link between `flavorTag` and `foodPreference` is not implemented
in V1 — the data exists and is waiting. Phase 8 (wiring the loop) or V2 can use
it to add minor bonuses or personality-driven reactions. For now it makes the
band feel like real people rather than stat blocks.

**4. Linear buff scaling introduced**
Recipes changed from a single flat `buffStrength` to
`baseBuffStrength + (cookingLevel - 1) * buffStrengthPerLevel`.

Simple recipes (30-min cook): base 10, scale 0.4/level
Complex recipes (90-min cook): base 12, scale 0.65/level

Why: Flat strength meant your cooking skill was irrelevant to mission outcomes
once you unlocked the recipe. Linear scaling means every level makes your
provisions meaningfully better. At cooking level 1 your Iron Stew is strength
12; at level 10 it's 18. It also creates natural tension — a complex recipe at
low level vs. a simple recipe at high level.

**5. Corsair hard mission changed**
The Corsair hard mission changed from agility to endurance ("Break the Siege
at Ironport"). Previously all three Corsair missions were agility. Now: easy =
agility, medium = agility, hard = endurance. Better variety and the hard mission
description (a grinding overnight fight) fits endurance better than agility.

**What's next:** Phase 3 and 4 — data models and workers.

---

## Session 4 continued — June 12, 2026 (late morning)
**Phase 3 and Phase 4 complete: Data models, Room, WorkManager**

All the Kotlin data layer and background session logic is now in place.

**What was built:**

Phase 3 — Data models and Room:
- Data classes (not Room entities): `Band`, `BandMember`, `Ingredient`,
  `Recipe` (with `RecipeIngredient`), `Mission` — these are the game data
  models, loaded from JSON, never written to the database
- Room entities (the live game state): `PlayerState`, `InventoryItem`,
  `PreparedFood`, `GatheringSession`, `CookingSession`, `MissionSession`,
  `BandMemberState`
- DAOs for each entity with Flow-based observation
- Repositories: `GameDataRepository`, `PlayerRepository`, `InventoryRepository`,
  `SessionRepository`, `BandRepository`
- Hilt `DatabaseModule` wiring everything together

Phase 4 — WorkManager sessions:
- `GatheringWorker` — runs a session for a set duration, rolls ingredient
  results based on mode and level, adds to inventory, grants XP, fires notification
- `CookingWorker` — produces the cooked food item, grants cooking XP, fires
  notification
- `MissionWorker` — evaluates buff threshold vs. mission requirement, handles
  success (money + ingredient rewards) or failure (possible member loss), fires
  notification
- Notification channel set up in `HearthCraftApp`

**Key architecture decisions:**

`PlayerState` uses a singleton-row pattern (always `id = 0`). There is only
ever one player state row. Simple and sufficient for a single-player offline
game.

Session entities (`GatheringSession`, `CookingSession`, `MissionSession`) also
use singleton rows. Only one of each can be active at a time, which matches the
design (one gathering session, one cooking session, one mission at a time).

`GameDataRepository` uses `lazy` loading — JSON files are parsed once on first
access, then held in memory for the lifetime of the app. Game data never
changes at runtime so this is clean.

Workers receive all the data they need as `inputData` key-value pairs (WorkManager's
built-in input system). They don't query the database themselves for game data —
they take it as input and write results to the database via injected repositories.

Member loss in `MissionWorker`: only triggers if buff strength is below 60% of
the required threshold AND a 33% random roll succeeds. So failure below threshold
= no rewards. Catastrophic failure (below 60%) = chance of permanent member loss.
This felt like the right gradient — failure is always costly, but losing someone
is rarer and more severe.

**What's next:** Phase 5 — ViewModels.

---

## Session 5 — June 13, 2026
**Phase 5 prep: Code review and bug fixes**

Code review before starting ViewModels. Two real bugs found and fixed.

**Bug 1 fixed: MissionWorker ignored buff type**
`MissionWorker` was evaluating mission success as:
`buffStrength >= mission.requiredBuffStrength`

It never checked whether the buff *type* matched. A warmth dish could pass an
acuity mission as long as the strength number was high enough. Fixed to require
both type and strength match:
`buffType == mission.requiredBuffType && buffStrength >= mission.requiredBuffStrength`

`MissionWorker.buildRequest()` now takes `buffType: String` as a parameter.
The BandViewModel (Phase 5) will pass the buff type of the selected food item
when launching a mission.

**Bug 2 fixed: XP never triggered level-up**
`PlayerRepository.addGatheringXp()` and `addCookingXp()` were adding XP to the
raw total but never advancing the level. The level sat at 1 forever.

Fixed: both methods now read the new XP total after writing, compute the correct
level using `levelForXp()`, and update the level if it changed.

XP curve: each level costs `level × 100` XP. Level 2 = 100 XP total, level 3 =
300 XP total, level 4 = 600 XP total. One forage session = 50 XP, so roughly
2 sessions to level 2. Pacing subject to revision after playtesting.

`levelForXp()` is public on `PlayerRepository.Companion` so ViewModels can
call it to compute progress-to-next-level for the XP bar display.

**What's next:** Phase 5 — ViewModels. Five to write:
- `GatheringViewModel` — session state, mode selection, ingredient history
- `KitchenViewModel` — recipe selection, ingredient availability, session state
- `BandViewModel` — member state, provisioning, mission selection, mission history
- `InventoryViewModel` — ingredient quantities, food items, money
- `PlayerViewModel` — overall player state, skill levels, XP progress

---

## Session 6 — June 13, 2026
**Repo cleanup and doc sync — no new features**

**What was built:**
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/MainActivity.kt`: moved from `java/`
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/theme/`: moved Color.kt, Theme.kt, Type.kt from `java/`
- `app/src/main/java/`: deleted entirely
- `docs/design.md`: updated to reflect all decisions made since session 1
- `CLAUDE.md`: tightened — structured journal format, design doc sync rule, numbered session-end steps

**Decisions made:**
- All Kotlin source lives under `kotlin/`, never `java/`. The split was an
  Android Studio scaffold artifact, not intentional. Consolidated.
- `docs/design.md` is now the authoritative record of every design decision.
  Any decision made in code must be reflected there before committing.
  This is now enforced in CLAUDE.md.

**Anything that diverged from docs/design.md:**
- This session *fixed* the drift rather than adding to it. The full list of
  what was synced:
  - Fourth band: Nomadic Confederation → The Greycloaks (Borderlands)
  - Buff type "focus" → "acuity" everywhere
  - Buff types now listed explicitly: endurance, agility, acuity, warmth, luck
  - Buff strength scaling formula documented: `baseBuffStrength + (cookingLevel - 1) × buffStrengthPerLevel`
  - Food flavor tag system documented (`flavorTag` on recipes, `foodPreference` on members)
  - `foodPreference` clarified as characterization only in V1, not mechanical
  - Mission success rule: both buff type AND strength must match
  - Member loss rule: below 60% threshold + 33% random roll
  - XP curve documented: level × 100 per level
  - Open Questions: two answered and moved to Resolved section
  - design.md "Last revised" updated

**What's next:**
- Phase 5 — ViewModels: GatheringViewModel, KitchenViewModel, BandViewModel,
  InventoryViewModel, PlayerViewModel

---

## Session 7 — June 13, 2026
**Phase 5 complete: ViewModels**

**What was built:**
- `ui/viewmodel/UiModels.kt`: shared data classes — `XpProgress`, `PreparedFoodDetail`, `BandMemberWithState`
- `ui/viewmodel/PlayerViewModel.kt`: observes `PlayerState`, computes XP progress for gathering and cooking
- `ui/viewmodel/GatheringViewModel.kt`: session state, mode selection, starts gathering sessions via WorkManager
- `ui/viewmodel/KitchenViewModel.kt`: recipe list, ingredient availability check, starts cooking sessions
- `ui/viewmodel/InventoryViewModel.kt`: ingredients, money, prepared food with computed buff strength
- `ui/viewmodel/BandViewModel.kt`: band members with alive/dead state, missions, provisioning selection, sends on mission

**Decisions made:**
- Gathering session durations: Farm = 5 minutes, Forage = 10 minutes. These are
  placeholder values defined as constants — easy to tune after playtesting.
- `UiModels.kt` holds shared data classes used across ViewModels rather than
  defining them inside individual ViewModel files. Avoids awkward cross-file imports.
- `canCook()` in `KitchenViewModel` is a pure function that takes the current
  inventory list rather than reading state internally — makes it easy for the UI
  to call with the current `inventoryItems` value.
- `BandMemberWithState` flattens `BandMember` fields directly rather than nesting
  the object — simpler for the UI to consume.

**Anything that diverged from docs/design.md:**
- Nothing. The ViewModels implement exactly what design.md describes.

**Phase 5 TODOs for Phase 8:**
- `KitchenViewModel.startCooking()`: deduct recipe ingredients from inventory before starting
- `BandViewModel.sendOnMission()`: remove the used prepared food item from inventory after sending

**What's next:**
- Phase 6 — Band Selection Screen: first-launch screen, player picks a band,
  choice persisted to Room, never shown again

---

## Session 8 — June 13, 2026
**Battlegrounds design captured; navigation dependencies added**

**What was built:**
- `design/battlegrounds.md`: full Battlegrounds endgame design document — V5+
  scratchpad, not authoritative, do not act on without promotion to docs/
- `docs/wishlist.md`: added Design Identity Constraints section with two
  non-negotiable rules for when Battlegrounds is eventually built
- `gradle/libs.versions.toml` + `app/build.gradle.kts`: added
  `navigation-compose 2.9.8` and `hilt-navigation-compose 1.3.0` in preparation
  for Phase 6 screens

**Decisions made:**
- Battlegrounds is correctly scoped at V5+. Zero V1 or V2 code changes required.
- The two identity constraints (member growth routes through player; provisioning
  as sustain not pre-battle buff) are saved to wishlist.md so they survive into
  future design sessions and aren't designed around accidentally.
- `BandMemberState.isAlive: Boolean` is correct for V1. The wound system will
  eventually change this to `woundsRemaining: Int` — trivial future migration,
  no action now.
- Navigation Compose added now rather than mid-Phase 6 to keep dependency
  changes as their own clean commit.

**Anything that diverged from docs/design.md:**
- Nothing. Battlegrounds goes in `design/` (scratchpad), not `docs/` (authoritative).

**What's next:**
- Phase 6 — Band Selection Screen
