# HearthCraft — Development Journal

> **Current Status** is the living summary at the top — update it every session.
> Session entries below are append-only.

---

## Current Status — July 3, 2026

**Phase:** Men (Greycloaks) — battle in-progress screen and post-fight recap built.
**What's working:** Missions tab now shows live health bars (boss + per-member) driven by pre-computed tick replay while the fight is in progress. Post-fight recap shows per-member DPS bars, Keeper heal-vs-DPS uptime %, total healing, and wound recap per member. False "no provisions" starvation narrative removed. DB migrated to v17.
**What's not wired yet:** HoH UI. No encounter has `grievousWoundSpecs` populated yet. Lone-Lands region unlock trigger not yet wired. Balance sim vs. Men encounters not yet run.
**Next session:** Build must be verified in Android Studio (SDK path issue prevented `./gradlew build` from CLI). Then add `grievousWoundSpecs` to Greycloaks encounters and run balance sim.
**Open questions:** Player title (still TBD). Exact Lone-Lands unlock trigger. HoH UI design.

---

## Session 19 — July 3, 2026
**Battle in-progress screen, post-fight recap, starvation narrative fix**

**What was built:**
- `engine/TickSnapshot.kt`: new serializable data class — one snapshot per game-tick (boss resolve + per-member HP)
- `engine/EncounterEngine.kt`: per-member damage and healing accumulators; Keeper heal-tick vs. DPS-tick counters; tick snapshot emission inside the main loop; all five fields added to `EncounterResult`
- `data/db/CombatReport.kt`: three new Room columns — `dpsJson`, `healJson`, `keeperHealUptime`
- `data/db/EncounterTicks.kt`: new Room entity storing the tick snapshot list for in-progress replay
- `data/db/dao/EncounterTicksDao.kt`: DAO (upsert/observe/delete)
- `data/db/Migration16To17.kt`: additive columns on `combat_reports` + new `encounter_ticks` table
- `data/db/HearthCraftDatabase.kt`: version → 17, `EncounterTicks::class` registered
- `di/DatabaseModule.kt`: `Migration16To17` and `EncounterTicksDao` wired up
- `data/repository/SessionRepository.kt`: `saveTicks()`/`clearTicks()` methods added; `EncounterTicksDao` injected
- `ui/viewmodel/BandViewModel.kt`: `encounterTicks` StateFlow; `sendOnEncounter()` populates all new `CombatReport` fields and serializes tick snapshots to DB; `dismissCombatReport()` also clears ticks
- `ui/screen/MissionsScreen.kt`: `MissionActiveCard` replaced by `BattleInProgressCard` showing live boss HP bar + per-member HP bars driven by tick replay; `CombatReportCard` expanded with DPS bars, Keeper uptime %, wound recap; false starvation narrative removed

**Decisions made:**
- Tick snapshots stored in a new `encounter_ticks` table rather than bloating `EncounterSession` or `CombatReport` — cleaner lifecycle, clears independently on dismiss
- Member name display in DPS/wound recap uses the raw member ID (which is the first name) rather than a separate lookup — acceptable since all IDs are plain first names
- `BandScreen.kt` retains its simple countdown card (`MissionActiveCard`) — full battle view lives on Missions tab only

**Anything that diverged from design/master-design.md:**
- None

**Coming up:**
- Next session: Verify build in Android Studio. Then add `grievousWoundSpecs` to Greycloaks encounters; run balance sim against Men encounters
- Near term: HoH UI; Lone-Lands unlock trigger
- Future ideas logged: none

## Session 18 — July 3, 2026
**Bug fixes: gathering, HoH gating, producer timers, kitchen grade picking**

**What was built:**
- `GatheringWorker.kt`: Greycloaks forage region narrowed to Bree-land only — Lone-Lands/North Downs were leaking in from start
- `GatheringWorker.kt`: seed bonus now picks from cultivatable ingredients actually in the forage haul, not a random regional pool
- `CookingWorker.kt`: HoH recipes excluded from level-up auto-discovery — they were all unlocking on first level-up, bypassing the grimoire gate
- `HiveWorker/CoopWorker/DairyWorker`: `updatePlantedAt` moved to just before `enqueueUniqueWork` so the timer stamps the start of the next waiting period, not the moment the worker ran — fixes timers sitting at 0 after collect
- `KitchenViewModel`: added `selectedIngredientGrades` state; `selectRecipe` auto-defaults to lowest available grade; `setIngredientGrade` lets player override; `canCook` and `startCooking` check and consume the chosen grade; `predictedDishGrade` reacts live
- `KitchenScreen` — `RecipeDetailPanel`: per-ingredient `FilterChip` grade pickers appear when multiple grades are in stock; single-grade stock shows a static badge

**Decisions made:**
- Lone-Lands region unlock: leaving `foragableRegions` as `setOf("Bree", "Special")` until the unlock trigger design question is resolved — code comment marks the expansion point
- Grade picker only shows when ≥2 grades have enough stock to satisfy the recipe qty — avoids UI noise when there's no real choice

**Anything that diverged from design/master-design.md:**
- None

**Coming up:**
- Next session: Add `grievousWoundSpecs` to Greycloaks encounters; run balance sim; begin HoH UI
- Near term: Lone-Lands unlock trigger; encounter validation pass
- Future ideas logged: none this session

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

---

## Session 9 — June 13, 2026
**Vision expanded: bestiary, progression, and raid RPG systems**

Three new design scratchpads saved to `vision/`. All V5+ — zero code impact.

**What was built:**
- `vision/bestiary.md`: Enemy tier system — five tiers (Rabble, the Fell, Banes,
  the Nine, Nameless Things). Power and untouchability tracked separately. The
  Nine are NEVER killed, only repelled — canon-protected. Nameless Things are a
  hazard/doom-clock, not a combat tier. Win conditions vary by tier: kill, repel,
  or race. Mixed fields (orc host led by a Ringwraith) create asymmetric fights
  where inspiration clears the rabble but leaves the real problem standing.
- `vision/progression.md`: Character stats and member realization. Five universal
  stats (Might, Agility, Vitality, Will, Fate) with no class locks — roles emerge
  from stat distribution. One resource pool: Morale (not HP). Power pool
  deliberately cut — provisioning IS the power economy, relocated to the kitchen.
  Core principle: "Greatness Is MADE, Not Born." No innate caps; any member can
  reach the heights if you invest in them and they survive the road.
- `vision/battlegrounds-rpg.md`: Expanded systems design — the full RPG layer.
  Updates and expands on the existing `vision/battlegrounds.md`. Key additions:
  Two-Pool combat math (`E/D_party` vs `P_effective/D_enemy`); live event log;
  Bonfires/Respites as segment breaks; Inspiration triggered by FORECAST of
  defeat (not accumulated death — the critical design fix); Captain as 4th role
  beyond tank/healer/damage; two wound tiers (ordinary vs grievous); Houses of
  Healing requiring healer + mastered alchemical preparation + time; healers
  can't self-heal. The influence map as emotional core.

**Decisions made:**
- `battlegrounds-rpg.md` coexists with the existing `vision/battlegrounds.md`
  rather than replacing it. The existing file is the earlier overview; the new
  file is the developed systems doc. Both are scratchpads.
- Inspiration trigger is the FORECAST of defeat, not accumulated deaths. This
  is the critical design fix — it decouples "experiencing inspiration" from
  "burying characters to get there." The dread precedes the dying.
- Healing cannot fail if costs are paid — the drama is entirely upstream
  (getting there, having the means, having time). This is the Aragorn model.
- The player has NO stat sheet. Craft levels ARE player power. This is the
  mechanical distinction that keeps the warlock-culinarian identity intact.

**Anything that diverged from docs/design.md:**
- Nothing. All three files are `vision/` scratchpads — they document future
  design intent, not V1 decisions.

**What's next:**
- Phase 6 — Band Selection Screen

---

## Session 10 — June 14, 2026
**Phase 7: All core screens built — the game is now navigable**

**What was built:**
- `gradle/libs.versions.toml`, `app/build.gradle.kts`: Added `material-icons-extended` dependency for full Material icon set (Forest, Groups, LocalDining, Inventory).
- `MainActivity.kt`: Removed outer Scaffold; the `"main"` route now delegates entirely to `MainScreen`. Cleaner separation of concerns.
- `ui/screen/BandSelectionScreen.kt`: Added `statusBarsPadding()` + `navigationBarsPadding()` so content doesn't clip under system bars now that enableEdgeToEdge has no outer Scaffold to compensate.
- `ui/viewmodel/UiModels.kt`: Added `IngredientStock(ingredientId, name, quantity)` — a joined view of `InventoryItem` + `Ingredient.name`.
- `ui/viewmodel/InventoryViewModel.kt`: Added `namedIngredients: StateFlow<List<IngredientStock>>` computed from `observeIngredients()` + `gameData.ingredients` name lookup.
- `ui/viewmodel/HomeViewModel.kt`: New ViewModel. Aggregates player XP progress + all three session StateFlows so HomeScreen stays self-contained.
- `ui/screen/MainScreen.kt`: Bottom NavigationBar with five tabs (Home, Gather, Kitchen, Band, Pantry). Owns its own Scaffold + nested NavHost. Recipe Book and Mission Board are sub-routes; bottom bar hides on those screens.
- `ui/screen/HomeScreen.kt`: Skill cards with LinearProgressIndicator XP bars, active session status row.
- `ui/screen/GatheringScreen.kt`: TabRow mode picker (Farm/Garden vs Forage/Wild) with descriptions, active session countdown timer.
- `ui/screen/KitchenScreen.kt`: Recipe list with can-cook indicators (✓/✗), ingredient detail panel, active cooking timer, Recipe Book button.
- `ui/screen/RecipeBookScreen.kt`: Full recipe list with buff strength computed at current cooking level. TopAppBar with back nav.
- `ui/screen/PantryScreen.kt`: Gold display, named ingredient stock list, prepared food list with buff info.
- `ui/screen/BandScreen.kt`: Member list with alive/fallen status, food selector, mission selector, Send button, active mission countdown.
- `ui/screen/MissionBoardScreen.kt`: All band missions with requirement highlighted green/red based on best available buff. TopAppBar with back nav.

**Decisions made:**
- `HomeViewModel` aggregates PlayerRepository + SessionRepository directly rather than depending on other ViewModels. Keeps Home self-contained.
- `MainScreen` owns its own Scaffold; MainActivity's outer Scaffold removed. BandSelectionScreen manually adds statusBarsPadding + navigationBarsPadding to compensate.
- Recipe Book and Mission Board are sub-routes under the main NavHost, not separate top-level destinations. They share the parent NavController for back navigation.
- `IngredientStock` added to `UiModels.kt` alongside the other projected UI data classes.
- `formatMs()` duplicated as a private function in each screen that needs a timer. Three small copies beats a shared utility file at this scale.

**Anything that diverged from docs/design.md:**
- Nothing structural. All screens are consistent with the V1 plan.

**What's next:**
- Phase 8 — Wire up game logic: deduct ingredients on cook start, consume prepared food on mission send, populate inventory from gathering worker output. Sessions start and timers tick but nothing flows in or out of the pantry yet.

---

## Session 11 — June 14, 2026
**Phase 8: Wire the complete core loop**

**What was built:**
- `KitchenViewModel.startCooking()`: added `recipe.ingredients.forEach { inventory.removeIngredient(it.id, it.qty) }` before enqueuing the work request. Ingredients are now consumed the moment cooking starts.
- `BandViewModel`: injected `InventoryRepository`; added `inventory.removePreparedFood(food.recipeId)` after `sessions.startMission()`. The prepared food is now consumed when the band is sent.

**Decisions made:**
- The three workers (GatheringWorker, CookingWorker, MissionWorker) were already fully implemented from Phase 4 — they write to inventory and handle XP, money, member loss, and notifications on completion. Phase 8 was just the two missing TODO lines in the ViewModels.
- Ingredients are deducted at cook-start (not cook-complete). This means if the app crashes mid-cook, ingredients are gone but the food isn't made yet. Acceptable for V1 — Phase 9 can address this if it feels wrong in play.
- Prepared food is consumed at mission-send (not mission-return). Same reasoning.

**Anything that diverged from docs/design.md:**
- Nothing.

**Coming up:**
- Next session: Phase 9 — edge cases, notification deep-links, stability pass.
- Near term: Phase 10 — install and play for a week, fill wishlist, decide V2 priorities.
- Future ideas logged: none this session.

---

## Session 12 — June 14, 2026
**Phase 9: Edge cases, member kill bug, notification routing**

**What was built:**
- `BandRepository.killMember()`: changed from a bare SQL UPDATE (which silently did nothing if the row didn't exist) to an upsert — gets the existing record or creates a new live one, then marks it dead. Member loss now actually persists.
- `BandSelectionViewModel.confirmSelection()`: injected `BandRepository`, added `band.initMembers(id)` call after `player.init(id)`. Band member states now exist in the DB from the moment the player chooses their band.
- `BandViewModel.hasAliveMembers`: new `StateFlow<Boolean>` derived from `members`. Returns `true` while the list is loading or any member is alive, `false` only when the list is non-empty and everyone is fallen.
- `BandScreen`: added `!hasAliveMembers` branch between the active-mission card and the provisioning UI. Shows a red "The band has fallen" card when the condition is met. Provisioning UI is hidden entirely.
- `GatheringWorker`, `CookingWorker`, `MissionWorker`: added `PendingIntent` to each notification. Tapping any completion notification now opens the app.

**Decisions made:**
- `killMember` uses upsert instead of raw UPDATE as a defensive fix — even if `initMembers` was never called for some reason, killing a member now works correctly.
- `hasAliveMembers` defaults to `true` and treats an empty members list as "still loading" rather than "all fallen" — avoids a false-positive error state on app startup before Room data arrives.
- Notification PendingIntent uses `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` — brings the existing app to the foreground (or restarts it) and always lands on the home screen. Deep-linking to a specific tab is a V2 polish item.

**Anything that diverged from docs/design.md:**
- Nothing.

**Coming up:**
- Next session: Phase 10 — install on device, play the loop, gather observations.
- Near term: V2 planning based on what playing actually feels like.
- Future ideas logged: none this session.

---

## Session 13 — June 14, 2026
**Post-audit redesign: farming, wounds, missions, README**

Wes played the game on a Pixel 7 Pro and submitted a detailed first-play audit. This session addressed every finding.

**What was built:**
- `SeedStock.kt` + `SeedStockDao.kt`: new entity tracking seed inventory per ingredient
- `GrowingSlot.kt` + `GrowingSlotDao.kt`: entity for farm plot and 4 independent garden slots
- `GrowingRepository.kt`: observeFarmPlot, observeGardenSlots, plantSlot, clearSlot
- `FarmWorker.kt`: HiltWorker; base yield 6, scales with gathering level, XP=40
- `GardenWorker.kt`: HiltWorker; base yield 3, XP=25
- `GatheringWorker.kt`: rewritten as forage-only; 25% chance to drop 1–2 seeds of a random plantable ingredient; buildRequest signature changed to (level, durationMs)
- `HearthCraftDatabase.kt`: bumped to version 3; added SeedStock and GrowingSlot entities; fallbackToDestructiveMigration(true)
- `DatabaseModule.kt`: added providers for SeedStockDao and GrowingSlotDao
- `InventoryRepository.kt`: injected SeedStockDao; added observeSeeds, addSeed, removeSeed, seedQty
- `BandMemberState.kt`: added woundStatus (healthy/wounded/grievously_wounded) and woundedSinceMs
- `BandMemberStateDao.kt`: added updateWound and healWound queries
- `BandRepository.kt`: added woundMember, healWound, woundableMemberIds
- `MissionWorker.kt`: full rewrite — probability-based success (easy 80%, medium 55%, hard 25%; +10% type match, +up to 10% strength ratio); wounds on failure; reward multiplier scales gold and drop count
- `UiModels.kt`: added woundStatus to BandMemberWithState; added SeedDetail data class
- `GatheringViewModel.kt`: major rewrite — forageSession, farmPlot, gardenSlots, seeds StateFlows; plantFarm, plantGarden, startForage functions; durations FARM=8min, GARDEN=4min, FORAGE=3min
- `BandSelectionViewModel.kt`: injected InventoryRepository; added giveStarterSeeds() called in confirmSelection (3× each of duskberry_seed, pale_cap_seed, hearthgrain_seed)
- `BandViewModel.kt`: passes woundStatus; added treatWound(memberId, food)
- `KitchenViewModel.kt`: added sortedRecipes StateFlow (cookable recipes first partition)
- `HomeViewModel.kt`: added activeGrowingCount StateFlow combining farm + garden active slots
- `GatheringScreen.kt`: complete rewrite — farm section, 4 garden slots, forage timer, SeedPickerDialog
- `BandScreen.kt`: wound status color-coded in member rows; Treat Wounds section with applicable food options
- `KitchenScreen.kt`: uses sortedRecipes instead of recipes
- `HomeScreen.kt`: shows "Growing (N plots)" row when active; "Gathering" renamed "Foraging"
- `MissionBoardScreen.kt`: color-coded difficulty dot, flavorLine in difficulty color, multiplied gold reward range; removed hard requirements display
- `Mission.kt`: added flavorLine and rewardMultiplier fields
- `ingredients.json`: added bilberry (forage/wild) and athelas (forage/wild)
- `recipes.json`: added bilberry_tea (acuity), restorative_broth (healing), athelas_infusion (healing_deep); verified all recipe names match their ingredients
- `missions.json`: all 12 missions updated with flavorLine and rewardMultiplier
- `README.md`: rewritten with two-game vision (provisioning idle loop + future raid RPG), faction descriptions, V2–V5+ roadmap

**Decisions made:**
- Farming bootstrap problem solved with Option C: starter seeds on band selection + forage drops seeds as a trickle
- Farm base yield 6, garden base yield 3 — "Shire-sized farm" calibration
- Wounds: ordinary food (buffType=healing) clears "wounded"; only healing_deep clears "grievously_wounded"
- Mission success is probabilistic with no hard gate — the food improves odds, not locks/unlocks
- flavorLine is static per-mission text replacing "Needs: buffType ≥X" — hides the probability math from the player

**Anything that diverged from docs/design.md:**
- Farming split into farm plot (targeted, larger yield) + garden (4 independent targeted slots, smaller yield) + forage (random, seed trickle) — more granular than prior design. Updated design.md to match.
- Wound system implemented earlier than planned (was V4); minimal version only, not the full Houses of Healing arc.

**Coming up:**
- Next session: intro screen, then install and play.
- Near term: assess farming balance in real play; mission unlock tiers.
- Future ideas logged: swipe tabs, recipe tiers by level, band member traits/levels.

---

## Session 14 — June 14, 2026
**Intro screen and Corsair Fleet flavor rework**

**What was built:**
- `BandSelectionScreen.kt`: complete rewrite as a three-page flow — opening lore (page 0), band selection (page 1), welcome quote (page 2). Single `page` integer state variable; no new navigation routes. DB writes happen when player taps "Enter" on the welcome page.
- `bands.json` (corsair_fleet): description reworked to reflect Númenórean lineage — ancient mariners, formal bearing, kings three thousand years in the dust. Previous "chaotic, adaptable" framing replaced.
- `BandSelectionScreen.kt` (Reva Tidecaller welcome line): rewritten from casual wanderer to fallen-nobility register — "the sea took most of that back," "certain standard," "no more steadfast company in the West."

**Decisions made:**
- confirmSelection() is called from the "Enter" button on page 2 (not from "Begin" on page 1). The welcome screen shows immediately; DB writes complete in the background before navigation fires via the navigateToMain SharedFlow.
- Welcome lines are hardcoded in the composable keyed by band ID rather than added to bands.json — they are character voice, not game data.
- Corsair Fleet is Númenórean-descended, not opportunistic pirates. Noble, formal, proud of lineage even in diminishment.

**Anything that diverged from docs/design.md:**
- Corsair Fleet identity updated: README already described them as "Freewake — Sea corsairs, chaotic, adaptable, opportunistic." Updated bands.json to match the new framing. README should be updated to align when band names are finalized in V2.

**Coming up:**
- Next session: install and play the full redesigned loop end-to-end.
- Near term: kitchen ingredient name display (shows raw IDs in recipe detail), mission unlock tiers.
- Future ideas logged: none this session.

---

## Session 16 — June 16, 2026
**Design redefinition, full character audit, README rewrite**

The game vision was formally locked this session. All future/ design docs promoted to docs/. Band rosters audited and corrected. README completely rewritten.

**What was built:**
- `README.md`: complete rewrite — one integrated game (Campaign + Ettenmoors + NG+), correct band identities, full vision including 14 battlegrounds, 4 roles, 5-tier bestiary, Inspiration mechanic
- `docs/characters.md` (new): promoted from future/; Greycloaks fully rewritten as Dúnedain community with built-in trust; name renames throughout
- `docs/battlegrounds.md`, `docs/battlegrounds-rpg.md`, `docs/bestiary.md`, `docs/progression.md` (new): promoted from future/; SCRATCHPAD headers replaced with authoritative headers
- `docs/redefinition.md`: Campaign + Ettenmoors + NG+ structure document
- `app/src/main/assets/data/band_members.json`: full personality audit
  - Mithlost: Thornwick → Thalindel, Maelgwyn the Greenwarden → Galadorn, Caranthir Oakshield → Caranthir; all "druid" language removed; Aelindra corrected to carry grief (elven); Thalindel rewritten with elven wonder
  - Freewake: Númenórean dormant greatness thread woven through all four members
  - Undermarch: Keldra given a quirky competitive personality; Thrain gets a subtle ring easter egg
  - Greycloaks: rewritten as a people with centuries of shared trust, not four separate stoic individuals
- `docs/design.md`: Mithlost member list updated to 4 active members with correct names
- `CLAUDE.md`: reference section updated, future/ docs removed
- `future/wishlist.md`: lesser rings idea added

**Decisions made:**
- Role names locked: Warden, Keeper, Hunter, Captain
- All five "future/" design docs are now first-class authoritative documents in docs/
- future/wishlist.md remains the only file in future/ — it is the correct place for deferred ideas

**Anything that diverged from docs/design.md:**
- Nothing structural. Personality text and naming are flavor, not design decisions.

**Coming up:**
- Next session: install and play — vitality gate feel, harvest collect feel
- Near term: lock role names; address two-faction-start question; faction-swap token in Market
- Future ideas logged: lesser rings (wishlist.md)

---

## Session 15 — June 16, 2026
**Stats, tiered kitchen, Market, and pending-harvest collect system**

This session introduced stats to band members, gated missions on vitality, added recipe tiers to the kitchen, added a Market tab for buying seeds, and changed harvest behavior from auto-deposit to a collect dialog.

**What was built:**
- `band_members.json` + `BandMember.kt`: all 16 members now have `startingMight/Agility/Vitality/Will/Fate` values
- `BandMemberState.kt`: added `might`, `agility`, `vitality`, `will`, `fate` columns (DB version → 4)
- `BandMemberStateDao.kt`: added `grantStats(memberId, vitality, might)` query
- `BandRepository.kt`: `initMembers()` now seeds stats from JSON data; added `grantMissionStats()` (alive members +1 vitality always, +1 might on success) and `maxVitality()`
- `UiModels.kt` / `BandViewModel.kt`: `BandMemberWithState` carries all 5 stats; `maxVitality` StateFlow derived from alive members
- `BandScreen.kt`: member rows show stat line; mission rows show locked state if vitality too low; Send button disabled when vitality gate not met
- `Mission.kt` + `missions.json`: `requiredBuffType` replaced with `vitalityRequired` (0/3/6 for easy/medium/hard)
- `MissionBoardScreen.kt`: shows vitality requirement and provision strength hint
- `MissionWorker.kt`: stat grants after every mission; removed buffType bonus from probability math; tweaked base chances (easy 70%, medium 45%, hard 20%); removed `KEY_BUFF_TYPE`
- `MissionSession.kt` / `BandViewModel.sendOnMission()`: `buffType` field removed throughout
- `HarvestItem.kt` (new): serializable data class with ingredientId, name, quantity, rarity
- `GatheringSession.kt` + `GrowingSlot.kt`: added `pendingResultJson: String?` column
- `GatheringSessionDao.kt` + `GrowingSlotDao.kt`: added `setPendingResult()` / `clearPendingResult()` queries
- `GatheringWorker.kt` + `FarmWorker.kt` + `GardenWorker.kt`: no longer write to inventory directly; write JSON result to pending column; notification text changed to "tap to collect"
- `SessionRepository.kt`: added `setPendingForageResult()` and `collectForage()`
- `GrowingRepository.kt`: added `setPendingResult()` and `collectAndClearSlot()`
- `GatheringViewModel.kt`: added `lastHarvest` StateFlow; `collectForage()` and `collectGrowingSlot()` claim items and set lastHarvest; `clearLastHarvest()` dismisses dialog
- `GatheringScreen.kt`: forage shows "Collect" button when result pending; growing slot cards show "Ready to harvest" state + Collect button; `HarvestResultDialog` shows what was collected with rarity colors
- `Ingredient.kt` + `ingredients.json`: added `rarity: String = "common"` field to all ingredients
- `Recipe.kt` + `recipes.json`: added `levelRequired: Int = 1`; all recipes assigned tiers (1, 3, 6, 8, 12)
- `KitchenViewModel.kt`: added `tieredRecipes` StateFlow — Apprentice (1–5), Journeyman (6–10), Craftsman (11+); added `playerState` StateFlow
- `KitchenScreen.kt`: replaced flat recipe list with tiered sections; locked tiers shown greyed with "Reach Lv N" label
- `PlayerStateDao.kt` + `PlayerRepository.kt`: added `spendMoney()` that conditionally deducts only if funds sufficient
- `MarketViewModel.kt` (new): `SeedForSale` data class; catalogue of 5 cultivated seeds at 5g each; `buySeed()` deducts gold atomically then adds seed
- `MarketScreen.kt` (new): seed purchase UI with gold display, card per seed, Buy button disabled when can't afford
- `MainScreen.kt`: added "Market" tab with Storefront icon

**Decisions made:**
- Harvest requires active collection (tap "Collect") rather than auto-depositing — adds intentionality and makes the world feel less mechanical
- Vitality gates mission access rather than buff type — simpler to understand, grows organically as members survive missions
- Base mission success chances lowered (70/45/20%) but food strength contributes up to +25% — food still matters but isn't dominant
- Stat grants after every mission (alive members): +1 vitality always, +1 might on success — consistent growth with a small success reward
- Market seeds cost 5g each — cheaper than the reward from a single easy mission; bootstraps farming without being trivial

**Anything that diverged from docs/design.md:**
- Stats system (Might/Agility/Vitality/Will/Fate) introduced as a minimal V1 layer — character sheet not displayed, stats affect mission gating only via vitality. Full stats progression is V5+ vision but the data foundation is now in place.
- Mission access gate changed from buff type to vitality requirement.

**Coming up:**
- Next session: install and play. Does vitality gate feel right on first play? Does the harvest collect step feel satisfying or annoying?
- Near term: kitchen ingredient name display (pre-existing gap); stat display tuning based on real-play feedback.
- Future ideas logged: none this session.

---

## Session 17 — June 16, 2026
**Two-faction start — complete implementation (data + UI)**

The two-faction system is now fully built: a player picks two bands at character creation, the second is dormant until cooking level 6, both can run concurrent missions, and the Band screen shows a switcher between them.

**What was built:**

Data layer (completed in previous compacted session):
- `PlayerState.kt`: added `secondBandId: String = ""`
- `MissionSession.kt`: primary key changed from `id: Int = 0` to `bandId: String` — enables concurrent missions per band
- `PlayerStateDao.kt`: added `setSecondBand()` query
- `BandMemberStateDao.kt`, `GatheringSessionDao.kt`, `GrowingSlotDao.kt`, `PlayerStateDao.kt`: various updates
- `SessionRepository.kt`: all mission methods now take `bandId` parameter
- `PlayerRepository.kt`: added `setSecondBand()`
- `MissionWorker.kt`: `clearMission()` call now passes `mission.bandId`
- `HearthCraftDatabase.kt`: version bumped 4 → 5 (fallbackToDestructiveMigration)
- `BandSelectionViewModel.kt`: complete rewrite for two-band selection flow

UI layer (this session):
- `BandSelectionScreen.kt`: 4-page flow — lore introduction → pick first band → pick second band (first excluded from list) → welcome quote from first band's captain
- `BandViewModel.kt`: added `viewingSecond` toggle, `firstBandId`/`secondBandId` StateFlows, `cookingLevel`, `isSecondBandUnlocked` (threshold: cooking level 6), `switchBand()` function, `activeBandId` derived flow; all `members`/`missions`/`activeMission` now keyed to `activeBandId`; `sendOnMission()` passes `bandId` in `MissionSession`
- `BandScreen.kt`: `BandSwitcher` composable at top — two `FilterChip` buttons, second greyed at 0.6 alpha with "Unlock at cooking 6" label when locked
- `HomeViewModel.kt`: `missionSession` now uses `flatMapLatest` off player state to observe primary band's mission
- `docs/roadmap.md`: full phased build plan (Layer 1 V1 → Layer 2 Campaign → Layer 3 Ettenmoors → Layer 4 Polish)
- `future/wishlist.md`: added member inspection RPG UI and second-band unlock popup entries

**Decisions made:**
- Second band unlocks at cooking level 6 (squad level system deferred to wishlist — cooking level is the proxy for now)
- `SECOND_BAND_UNLOCK_COOKING_LEVEL = 6` is a named constant in `BandViewModel.kt` — easy to tune
- Band switching resets food and mission selection to avoid stale UI state
- The unlock visual is the chip going from greyed to active; a celebration popup is a wishlist item
- `flatMapLatest` used for reactive `bandId` → `Flow<MissionSession?>` mapping; `@OptIn(ExperimentalCoroutinesApi::class)` added where needed

**Anything that diverged from docs/design.md:**
- None. Two-faction start was a new addition consistent with the vision.

**Coming up:**
- Next session: install and play the two-faction flow end-to-end
- Near term: Phase 1B — member personalities and food preferences visible in-game
- Near term: Phase 1C — crafting level design pass
- Future ideas logged: member inspection screen, second-band unlock popup (both wishlist.md)

---

## Session 8 — June 18, 2026
**Combat model integrated into repo — design docs and tuning toolkit**

**What was built:**
- `docs/combat-model.md`: new authoritative readable spec for the full combat
  system, synthesized from the claude.ai design session handoff. Covers party
  roles + stat templates, DPS formulas, survival rules, food/hazard mechanics,
  Inspirations, encounter JSON schema, difficulty onramp, and open threads.
- `tools/sim/hearthcraft_fight_sim.html`: the validated combat simulator.
  Open in a browser to run fights and tune encounters.
- `tools/sim/HearthCraft_Encounter_Builder.xlsx`: encounter authoring
  spreadsheet. Edit the Sim Encounters tab, load into the sim via Import panel.
- `tools/sim/HearthCraft_Tier_Planner.xlsx`: food/recipe content and
  Provisioning Reference (HP/s tiers, antidote ladder).
- `tools/sim/HearthCraft_Mechanics_Reference.xlsx`: combat number reference.
  Blue/yellow cells are tunable; values match the simulator.
- `tools/sim/export_encounters.py`: batch-exports one JSON per encounter tab.
- `app/src/main/assets/data/encounters/*.json`: 14 encounter files. Two are
  validated and locked (Neekerbreekers L1, Wolves L3); the rest are
  placeholder numbers awaiting re-tuning.
- `docs/design.md`: updated food/buff section — abstract buff vocabulary
  (Endurance/Agility/Acuity/Warmth/Luck) retired; full stat model documented;
  V1 simplification layer noted.
- `docs/progression.md`: difficulty onramp rung ladder added.

**Decisions made:**
- Old abstract buff types are RETIRED as the design destination. V1 code keeps
  the simplified buff-strength model as a prototype layer; V2+ builds the full
  stat system.
- Encounter JSON schema uses 0–10 severity integers; sim sliders use raw values
  (alignment is a future polish pass).
- Encounter JSON holds a `stages` list — even V1 single-stage encounters use
  this shape so multi-stage is a later data change, not a rewrite.
- Simulator CDN dependency (SheetJS via cdnjs) is acceptable for a dev tool;
  paste fallback handles offline use.

**Anything that diverged from docs/design.md:**
- Food buff model redesigned (direct stat boosts replacing abstract buff types).
  `docs/design.md` updated to match.

**Coming up:**
- Next session: play V1 build, confirm feel of the core loop
- Near term: decide V2 scope — does combat integration come before or after
  alchemy and recipe discovery?
- Open design thread: 5th combat role (melee DPS) — party-of-5 vs field-4;
  identity vs Might Hunter; name and Inspiration
- Future ideas logged: none this session

---

## Session 9 — June 19, 2026
**README updated to document the combat toolkit**

Completed the one remaining task from Session 8: updated `README.md` to reflect
the current state of the project.

**What was built:**
- `README.md`: Updated "Current State" section; added new "Encounter Toolkit"
  section documenting `tools/sim/` (simulator, spreadsheet, exporter, reference
  spreadsheets).

**Decisions made:**
- Toolkit section lives in the README rather than a separate tools/README — keeps
  everything in one place for a project this size.

**Anything that diverged from docs/design.md:**
- None.

**Coming up:**
- Next session: play V1 build, confirm feel of the core loop
- Near term: decide V2 scope — combat integration vs alchemy/recipe discovery
- Open design thread: 5th combat role

---

## Session 10 — June 19, 2026
**Simulator redesign: band level cap, duration presets, UI polish**

**What was built:**
- `tools/sim/hearthcraft_fight_sim.html`: Band level slider capped at 20 (was 60).
  Duration slider replaced with 6 preset buttons (20/25/30/35/40/45 min). Valid
  range highlighted per level — L1–9: 20–30 min active, higher options dimmed;
  L10–20: 30–45 min active, lower options dimmed. Range sliders enlarged (6px
  track, 20px thumb). Control labels and value displays made larger and bolder.
  Buttons have hover-lift and click animations. Tab bar buttons have hover
  transitions. Panel body padding increased for roomier layout.
- `docs/design.md`: Added band combat level cap of 20 (gathering and cooking
  XP skills uncapped). Added mission duration rule to Missions section.
- `docs/v1-plan.md`: Added mission duration rule to Missions section.

**Decisions made:**
- Band combat level cap is 20 for V1, subject to change with future expansions.
  Gathering and cooking skill XP levels are explicitly uncapped — only the band's
  combat level is capped, not the player's craft progression.
- Mission durations: below level 10 = 20–30 min; level 10+ = 30–45 min.
  This is reflected in the simulator's button highlighting and hint text.
- "Mettle" as a composite combat readiness metric was discussed and deferred.
  Recommendation: keep vitality (mission access gate) and buff strength (success
  probability) as separate axes. "Mettle" may be valuable as a display label
  for band readiness — not a new mechanic, just better vocabulary. Logged as
  an open question.

**Anything that diverged from docs/design.md:**
- None. design.md updated to match all decisions.

**Coming up:**
- Next session: balance check on encounter difficulty vs squad level — 20 may
  not be the right ceiling; need to run encounters across the level range and
  see where the curve breaks.
- Near term: play V1 build; V2 scope decision.
- Future ideas logged: none this session.

---

## Session 11 — June 19, 2026
**Encounter tuning: variable spikes, HP/s curve, Neekerbreekers validated, Design Notes sheet**

A long design and tuning session. No Kotlin code touched. All work in the combat simulator, encounter spreadsheet, and design docs.

**What was built:**

`tools/sim/run_sim.js` — headless simulator:
- `--rlevel R` flag: pass a cooking level (1–50) and the sim derives HP/s from the tier table, labels output as "Lv4 Hearthkeeper → 10 HP/s per member"
- HP/s tier table added: 7 tiers, Hearthkeeper (Lv1–4, 5–10 HP/s) through Grandmaster (Lv41–50, 86–110 HP/s)
- Within-tier curve changed from concave (power=0.65, steep at entry) to convex (power=1.8, slow at entry, biggest reward near tier ceiling). Rationale: rewards pushing to the tier boundary, creates a natural pull toward the next tier rather than farming the current one
- Spike interval changed from fixed (`t % spikeiv === 0`) to variable: each spike schedules the next at `spikeiv × U(0.5, 1.5)` — uniform jitter of ±50% around the mean
- Spike damage changed from fixed base to rolled: `spike × U(0.7, 1.3)` — ±30% range each hit. Together with interval jitter, this breaks the binary win/loss cliff produced by deterministic spikes
- Close-call stats added to aggregate output: "Enemy resolve remaining avg X% of max" and "Close calls (< 25% left): Y% of defeats" — surfaces near-miss information that informs after-action report design. Near-miss zone flag fires when avg < 20%
- `nextSpikeAt` state variable added; initialised at fight start with jitter

`tools/sim/hearthcraft_fight_sim.html` — browser simulator:
- Variable spike interval and damage range synced from headless sim (same math)
- `nextSpikeAt` state variable added; initialised in `prep()` on each reset
- Spike interval label updated: "Seconds between spikes (mean)" with note that interval is randomised ±50% and damage ±30%
- Ward check updated to use rolled `hit` value instead of `P.spike`
- Company tab added (4 · The Company): character cards for Borin Ironmantle (Warden), Mira (Hunter), Cael (Keeper), Aelindra (Captain). Each card has: biography (flavor-first, in-character voice), mechanics paragraph (how they work, implied not stated), Inspiration block (in-world description, not tooltip language). Tab wired into showTab() and tabbar

`tools/sim/HearthCraft_Encounter_Builder.xlsx`:
- Neekerbreekers row updated: drain 6→12, spike 40→75, spikeIntervalSec 15→13
- Design Notes sheet injected (new sheet 19, rId23): philosophy section, difficulty ramp table (all 8 rungs), encounter notes table (Neekers validated, Wolves TBD), full HP/s tier table with per-level values, after-action report design notes

**Design decisions made:**

Encounter vocabulary locked:
- **Encounters** = single-stage fights. The V1 unit. What the sim runs. Neekerbreekers, Wolves, everything on the early ramp. Band-agnostic mechanically; flavor-per-band via separate flavor blocks (name, flavorIntro, stageFlavor, rewardFlavor keyed by bandId).
- **Battlegrounds** = named historical set-pieces (Fornost, Pelennor, Azanulbizar). Multi-stage, scored differently, not yet fully designed. Same underlying engine, different shape and scope.
- Reward tables are band-agnostic (same ingredient pool). Named weapon/legendary drops belong to Battlegrounds only, not Encounters.

Encounter flavor is band-specific, lore-grounded:
- Neekerbreekers and Wolves are canonically Greycloak territory (Midgewater Marshes, Chetwood). Other bands will have their own regionally grounded encounters — not the same fights with different flavor text. Undermarch: mountain passes, goblin tunnels. Mithlost: forest edge, corrupted groves. Freewake: coastal/river work, brigands.
- Each band's encounter names and descriptions should feel native to that band's home geography and voice.

HP/s curve philosophy:
- Convex within each tier (power=1.8). Lv1 of any tier = floor (just crossed into this tier). Tier ceiling = biggest reward. This creates meaningful progression at every level without farming behavior.
- Tier 1 exact values: Lv1=5.0, Lv2=5.7, Lv3=7.4, Lv4=10.0 HP/s

Neekerbreekers (VALIDATED — 5000 runs):
- drain=12, spike=75, spikeIntervalSec=13 (mean), resolve=50000, duration=1500, no hazards, no armor
- Win rates: no food=0%, Lv1=25%, Lv2=66%, Lv3=98%, Lv4=100%
- On defeat at Lv2: avg enemy resolve remaining 23% — 61% of defeats are close calls. The after-action report will have near-miss stories to tell.
- Design character: drain-dominant sustain test. The tutorial fight. Unwinnable with no food (wipe ~1min). The lesson is go cook. Every recipe level does visible work.

Variable spike design rationale:
- Fixed spikes create a deterministic pressure equation — the win curve is a sharp threshold, not a slope. Variable interval + damage range creates genuine run-to-run variance.
- In HearthCraft (zero in-fight player agency), randomness is more justified than in action RPGs — the player can't dodge crits, so variance is what creates replayability and war stories, not frustration.
- The separate "crit" mechanic was considered and rejected in favor of a damage range — simpler, same effect. High rolls on the damage distribution naturally produce memorable "oh no" moments without a new system.

After-action report philosophy:
- Binary win/loss is insufficient. The screen that follows the fight does the emotional work.
- Well-provisioned: low variance, consistent wins, clean aftermath. Preparation is reliably rewarded.
- Squeaky zone: high variance. Sometimes win, sometimes lose. Aftermath explains why — enemy resolve remaining at defeat tells the player they were close. Losing with the enemy at 8% resolve is a near-miss story, not an opaque failure.
- Underprepared: consistent fast wipe. Lesson is immediate and clear.
- Key stats to surface: enemy resolve remaining %, fight duration, wounds per member, rescues used, Warden guards spent, Inspirations that fired.

Cooking XP design (design intent, not yet built):
- First-cook bonus: 3–5× XP on first completion of any recipe. Rewards recipe discovery.
- Preference match bonus: small XP bonus when flavor tag matches a living member's preference. Rewards knowing the band.
- Antidote/hazard prep bonus: XP bonus when cooking an antidote food while a relevant encounter is active or queued. Rewards reading the fight before you cook.
- Diminishing returns on repetition: same recipe back-to-back decays XP ~15% per consecutive repeat, floor at 25%. Soft tax on spam.

**Anything that diverged from docs/design.md:**
- Encounter vs Battleground vocabulary is new and not yet in design.md. Should be added next session.
- "Missions" in the original design are now being called "Encounters" for single-stage fights. missions.json will need to be replaced with encounters.json when the Android build catches up.

**Coming up:**
- Next session: tune Wolves in the Chetwood. Target win curve: Lv4 (Neekers ceiling) = squeaky entry (~25–35%), Lv6–7 = comfortable, Lv8–9 = clean ceiling.
- Near term: lock both Rung 0 encounters, then design the cooking XP system properly.
- Near term: add Encounter vs Battleground vocabulary to design.md.
- Near term: design per-band encounter flavor for Neekerbreekers and Wolves (Greycloaks-native), then design the Undermarch/Mithlost/Freewake equivalents.
- Future ideas logged: character cards in-game (not just in the sim) — noted but deferred.

---

## Session 12 — June 19, 2026
**Encounter tuning: Wolves in the Chetwood validated and locked**

Sim-tuning session. No Kotlin touched. All work in the combat simulator and encounter JSON.

**What was built:**
- `encounters/wolves_chetwood.json` — params tuned and validated: drain=18, spike=75, spikeIntervalSec=9, resolve=60000. designNote updated with validated win rates.

**Tuning methodology:**
- Ran multiple trials across drain/spike/boss-resolve space at party Lv3, food Lv3–8 (5000 runs each).
- Anchored on party level 3 (encounter recLevel = 3, so earliest possible unlock).
- Target curve: Lv3 food should barely work (~5–10%); Lv4 food is the real threshold (~80%); Lv6–7 food feels well-prepared (~90–98%); Lv8 food is decisive (~100%).
- Rejected earlier params (drain=12, spike=55) that made Lv3 food an 85% win — too easy for a Rung 2 encounter that should demand better provisioning than the tutorial.

**Validated win rates (party Lv3, 5000 runs):**
- Lv3 Hearthkeeper food (~7.4 HP/s): 5.8% win
- Lv4 Hearthkeeper food (~10 HP/s): 80.9% win
- Lv6 Initiate food (~10.7 HP/s): 90.8% win
- Lv7 Initiate food (~12.3 HP/s): 98.3% win
- Lv8 Initiate food (~14.8 HP/s): ~100% win

**Design decisions made:**
- Wolves demands max Hearthkeeper food (Lv4, Tier 1 ceiling) to have a real chance. This is the right message: Neekerbreekers taught you to cook; Wolves punishes you if you go in underprepared.
- The steep jump from 6% → 81% between Lv3 and Lv4 food is intentional. Lv4 = last level of Tier 1 = largest within-tier gain. The convex curve is doing its job.
- physMit remains 0 — armor is a Rung 3+ question.

**Raised in session (not yet resolved):**
- The Encounter Builder Excel uses raw HP/s inputs rather than cooking-level selectors. Should add a food-level dropdown that maps cooking level → HP/s the same way `--rlevel` does. This would make the tool self-consistent with the sim.
- Need to formally design the food/party/encounter progression ladder: which cooking level gates each encounter rung? This is the core design question that makes progression predictable for both the player and the designer.

**What's not in session scope (deferred):**
- Encounter Builder food-level dropdown
- Formal progression ladder design
- Per-band flavor for Wolves (Greycloaks-native; other bands need their own Chetwood equivalents)

**Coming up:**
- Next session: design the food/party/encounter progression ladder. Formalize the rung-by-rung design target. Then add food-level dropdown to Encounter Builder.
- Near term: recipe/XP design. Encounter vs Battleground vocabulary into design.md.

---

## Session 14 — June 19, 2026
**Repo-wide doc audit and sync**

Full read of all docs, data files, and JSON encounters to find stale or inconsistent info. Eight issues found and fixed.

**What was fixed:**
- `app/src/main/assets/data/encounters/neekerbreekers_midgewater.json`: fight params corrected to validated numbers (`drain 6→12, spike 40→75, spikeIntervalSec 15→13`). The designNote had the right numbers but the actual stage block still had the old pre-tuning values.
- `docs/combat-model.md`: "Tuned Encounters" section updated with final validated params and win-rate summaries for both encounters (was pre-tuning placeholder numbers).
- `docs/battlegrounds.md`: deprecated "warlock-culinarian" title removed (CLAUDE.md: use "the player" or "the provisioner" until title is locked).
- `docs/battlegrounds-rpg.md`: same deprecated title removed; heroic peak renames propagated — "the Pull-Back" → Hands of Healing, "the Slaying" → Deadeye (locked in redefinition.md, not yet synced here).
- `docs/design.md`: Freewake identity reworked to match Númenórean-descended framing established in Session 14/16 (bands.json was updated then; design.md was not). Also added Encounter vs Battleground vocabulary to Resolved Open Questions.
- `docs/v1-plan.md`: buff type "focus" → "acuity" (renamed Session 4, missed here).

**Decisions made:**
- None. This was a sync session only — no new design decisions.

**Anything that diverged from docs/design.md:**
- Nothing new. All changes brought docs into alignment with previously made decisions.

**Coming up:**
- Next session: redesign crafting and gathering mechanics (Wes's request).
- Near term: formal encounter/food/party progression ladder; recipe/XP design.
- Future ideas logged: Freewake name brainstorm (identity rework complete; name should follow).

---

## Session 13 — June 19, 2026
**Tier table fix, sim UI overhaul, full band×food validation matrices**

No Kotlin touched. All work in the simulator, HTML sim, and encounter JSONs.

**What was built:**
- `tools/sim/run_sim.js` — tier table floors bumped by 1 HP/s at every tier boundary
- `tools/sim/hearthcraft_fight_sim.html` — tier table fix synced; per-member HP/s sliders replaced with single cooking-level slider; stat-focus sliders replaced with dropdowns styled to match parchment theme
- `app/src/main/assets/data/encounters/neekerbreekers_midgewater.json` — design note corrected with validated band Lv1 numbers
- `app/src/main/assets/data/encounters/wolves_chetwood.json` — design note updated with full matrix results
- `tools/sim/HearthCraft_Encounter_Builder.xlsx` — winCurve + simCmd columns added for validated encounters

**Bug fixed: tier boundary HP/s gap**

Every tier floor matched the previous tier's ceiling — so Lv4→Lv5, Lv9→Lv10, etc. gave zero HP/s improvement. Crossing a tier boundary felt like nothing. Fix: bump each tier floor by 1.

Old vs new floors:

| Tier | Old hpsLo | New hpsLo |
|---|---|---|
| Initiate (Lv5) | 10 | 11 |
| Apprentice (Lv10) | 18 | 19 |
| Journeyman (Lv16) | 30 | 31 |
| Adept (Lv23) | 44 | 45 |
| Master (Lv31) | 62 | 63 |
| Grandmaster (Lv41) | 86 | 87 |

**Correct band level baseline established**

Previous runs used band Lv3 for Neekerbreekers — wrong. Correct unlock floor is band Lv1. Wolves unlock floor is band Lv3. All prior Neekers numbers were slightly generous (more morale buffer than a fresh band actually has). New validated numbers at correct floors below.

**Full band × food validation matrices (1000 runs per cell)**

HP/s per food level (fixed tier table):
- FL1=5.0, FL2=5.7, FL3=7.4, FL4=10.0, FL5=11.0, FL6=11.6, FL7=13.0, FL8=15.2

### Neekerbreekers — band Lv1 is unlock floor
*(boss 50k · drain 12 · spike 75 · spikeiv 13 · duration 25:00)*

| Band Lv | FL1  | FL2  | FL3  | FL4   | FL5   | FL6+  |
|---------|------|------|------|-------|-------|-------|
| 1       | 27%  | 67%  | 97%  | ~100% | 100%  | 100%  |
| 2       | 36%  | 75%  | 99%  | 100%  | 100%  | 100%  |
| 3       | 47%  | 80%  | 99%  | 100%  | 100%  | 100%  |
| 4       | 54%  | 87%  | 99%  | 100%  | 100%  | 100%  |
| 5       | 66%  | 90%  | 100% | 100%  | 100%  | 100%  |
| 6       | 73%  | 93%  | 100% | 100%  | 100%  | 100%  |
| 7       | 79%  | 96%  | 100% | 100%  | 100%  | 100%  |
| 8       | 83%  | 95%  | 100% | 100%  | 100%  | 100%  |

FL1 food at band Lv1 = 27% — the tutorial "go cook" lesson. FL3 food solves Neekers permanently regardless of band level.

### Wolves in the Chetwood — band Lv3 is unlock floor
*(boss 60k · drain 18 · spike 75 · spikeiv 9 · duration 25:00)*

| Band Lv | FL1 | FL2 | FL3  | FL4  | FL5  | FL6  | FL7  | FL8   |
|---------|-----|-----|------|------|------|------|------|-------|
| 1       | 0%  | 0%  | 3%   | 68%  | 83%  | 89%  | 97%  | ~100% |
| 2       | 0%  | 0%  | 6%   | 72%  | 89%  | 94%  | 99%  | ~100% |
| 3       | 0%  | 0%  | 7%   | 80%  | 94%  | 95%  | 99%  | 100%  |
| 4       | 0%  | 0%  | 8%   | 88%  | 96%  | 99%  | ~100%| 100%  |
| 5       | 0%  | 0%  | 14%  | 90%  | 98%  | 99%  | ~100%| 100%  |
| 6       | 0%  | 0%  | 18%  | 94%  | 98%  | ~100%| 100% | 100%  |
| 7       | 0%  | 0%  | 27%  | 96%  | 99%  | ~100%| 100% | 100%  |
| 8       | 0%  | 0%  | 33%  | 97%  | ~100%| ~100%| 100% | 100%  |

FL1/FL2 food = wipe at every band level. FL4 is the real entry point (80% at band Lv3). FL5 (first Initiate level) now cleanly separates from FL4 — the tier fix worked.

**Key observations from the matrices:**
- Food is the dominant lever; band level is secondary but meaningful. Lv4 food on Wolves improves from 80% (band Lv3) to 97% (band Lv8) purely from morale growth and Keeper Will scaling.
- The Hunter takes the majority of wounds in both encounters — most fragile role, catches stray spikes. Expected by design.
- Keeper barely gets scratched when food is adequate — Warden guard mechanic functioning correctly.
- Neekers is essentially solved at FL3 regardless of band level. Wolves has a real food ladder all the way to FL8.

**Decisions made:**
- Tier boundary fix: +1 HP/s at every tier floor is the rule going forward. Tier crossings must always feel like a real reward.
- Correct band level validation baseline: always validate at the unlock floor (band Lv = recLevel of encounter), not at an arbitrary higher level.
- Band×food matrix is the standard validation artifact for any new encounter — run it, include it in the journal.

**Coming up:**
- Next session: design the food/party/encounter progression ladder formally.
- Near term: recipe/XP design. Encounter vs Battleground vocabulary into design.md.

---

## Session 15 — June 19, 2026
**Band rename: The Freewake → The Kingswake; pending design work logged**

**What was built:**
- `app/src/main/assets/data/bands.json`: display name updated (`"The Freewake"` → `"The Kingswake"`). `bandId` unchanged (`corsair_fleet`) to preserve database compatibility.
- `docs/design.md`, `docs/v1-plan.md`, `docs/redefinition.md`, `docs/characters.md` (×3 occurrences), `README.md` (×2 occurrences): all display references updated.
- `docs/roadmap.md`: pending major design work added to Open Design Work section.

**Decisions made:**
- Band name: **The Kingswake** — "wake" carries the nautical world and a ship's mourning wake; "King's" carries the drowned king of Númenor without spelling it out. Sad and regal.
- `bandId` stays `corsair_fleet` — changing a primary key would break saves. Display name only.
- Journal historical entries left untouched — they correctly record what the band was called at the time.

**Anything that diverged from docs/design.md:**
- None. Name change only.

**Coming up:**
- Next session: redesign crafting and gathering mechanics (first priority).
- Near term: Battlegrounds design, Ettenmoors design, legendary item system + economy.

---

## Session 16 — June 19, 2026
**Sim: per-character DPS meter and damage mitigated chart**

**What was built:**
- `tools/sim/hearthcraft_fight_sim.html`: per-character DPS meter added to the Live tab (new "DPS per character" gauge panel, live-updating each tick). Shows each member's effective DPS after all mitigations, or "grievous/down/rescuing" state when applicable.
- `tools/sim/hearthcraft_fight_sim.html`: two new charts in the Results tab — "DPS per character" (line chart, all four members over the fight) and "Damage mitigated vs. magic bypassing armor" (physical DPS lost to armor in red, Keeper magic DPS bypassing armor in purple). The mitigation chart only renders when physical mitigation > 0.
- `dpsBreakdown()` refactored to compute per-member raw and effective DPS, then return `effBy`, `physMitTotal`, and `magicDmg` for both live display and series recording.

**Decisions made:**
- Keeper (Cael) is classified as a magic dealer — his damage bypasses physical armor entirely, only Dread can reduce it. Warden, Hunter, Captain are physical dealers subject to armor. This is confirmed by the sim math and now visually verifiable.
- Stat growth (level 1–20) stays linear — superlinear growth would make encounter tuning brittle and shift the interesting power curve away from the provisioner's food/cooking skill, where it belongs. The S-curve on cooking tiers (SCURVE_P = 1.8) is where increasing returns live.

**Anything that diverged from docs/design.md:**
- None.

**Coming up:**
- Next session: redesign crafting and gathering mechanics (first priority).
- Near term: Battlegrounds design, Ettenmoors design, legendary item system + economy.

---

## Session 17 — June 19, 2026
**Sim: MMO-style DPS meters, pie chart, Captain hybrid damage, color system locked**

**What was built:**
- `tools/sim/hearthcraft_fight_sim.html`: Results tab fully rebuilt. Replaced three line charts (DPS per character, mitigation, armor vs. magic) with MMO-style bar meters and a pie chart.
- New Results cards: DPS per character (bar meter), Captain's boost (silver bar — Will + Red Dawn combined), Keeper healing (green bar), damage type pie chart (physical/magic, extensible), armor mitigation (absorbed vs. through), Dread mitigation (Will + Hope bars).
- `meterChart()` and `pieChart()` helper functions added. Bar meters are HTML/CSS divs; pie chart is SVG.
- Character color system locked: Hunter red `#c0392b`, Warden blue `#2e6da4`, Keeper purple `#7d5a93`, Captain gold `#b8843c`.
- Captain redesigned as 50/50 hybrid damage dealer: physical = `Mig × 0.3` (armor-affected), magic = `Wil × 0.2` (bypasses armor). Will now drives her magic output; Fate removed from raw damage formula.
- Keeper healing tracked per tick (`healBy.keeper`), Red Dawn boost tracked per tick (`dawnBoost`), Captain physical/magic split tracked (`effBy.captain_phys`, `effBy.captain_magic`).
- `docs/combat-model.md`: all role damage formulas updated with damage types and stat roles.
- `docs/design.md`: new Combat Roles and Damage Types section added with color table and Captain hybrid notes.
- `future/wishlist.md`: melee vs. ranged DPS subtype design question logged.

**Decisions made:**
- Captain magic damage scales from Will, not Fate. `Mig × 0.3` physical + `Wil × 0.2` magic. Food that boosts Will shifts her toward more magic output — strategically interesting.
- Fate removed from Captain's raw damage. Its V1 role is now solely Shadow drain resistance (tracked alongside Will). Fate's intended role (Inspiration odds, critical heals) is deferred to V2+.
- Results line charts for DPS-per-character and mitigation removed — time-dimension data was not useful there; summary meters are clearer.
- Pie chart architected to accept an array of slices so Westernesse, fire, and other future damage types are a push to an array.

**Anything that diverged from docs/design.md:**
- Captain's damage formula changed from `Mig × 0.3 + Fat × 0.2` to `Mig × 0.3 + Wil × 0.2`. `docs/design.md` and `docs/combat-model.md` updated.
- Character colors changed from existing sim colors to locked design palette. All docs updated.

**Coming up:**
- Next session: Fate stat design (what does it DO in combat beyond Shadow drain?). Then crafting/gathering redesign.
- Near term: Battlegrounds design, Ettenmoors design, legendary item system + economy.
- Future ideas logged: melee vs. ranged DPS subtype design question.

---

## Session 18 — June 19, 2026
**Sim: Fate mechanics — Inspiration rate boost and spike evasion**

**What was built:**
- `tools/sim/hearthcraft_fight_sim.html`: Two Fate mechanics wired into the fight engine.
  - Spike evasion: each standing member has `Fat × 0.004` chance to slip a spike entirely. Near-misses log in green. Evasion does not fire if the Warden is guarding (guard takes priority).
  - Inspiration rate boost: all four Inspirations now use `min(0.25, base + memberFate × 0.003)` as their trigger probability. Each member's own Fate stat boosts their own Inspiration.
- `docs/combat-model.md`: Fate mechanics section added under Inspirations. Key Constants updated with `Fate insp coef = 0.003` and `Fate evade coef = 0.004`. Stat roles entry for Fate updated with both mechanics.

**Decisions made:**
- Fate cap at 0.25 for Inspiration trigger rate — prevents Fate-stacking from making Inspirations feel scripted.
- Evasion does not trigger on guarded spikes — the Warden's guard is a deliberate sacrifice; fate shouldn't undo it.
- Each member uses their own Fate for their own Inspiration. The Captain's high Fate feeds Red Dawn; the Keeper's high Fate feeds Grace.
- Shadow draining Fate is now meaningful in two ways: reduces Inspiration frequency AND reduces spike evasion. Shadow-heavy fights are harder on both fronts.

**Anything that diverged from docs/design.md:**
- Nothing new — Fate mechanics were undesigned before this session. `combat-model.md` is now authoritative.

**Known balance flag:**
- Fate may push too strong at current coefficients, particularly with high-Fate members (Keeper 12, Captain 13). Shadow mechanics may need deepening to provide counter-pressure. Flagged by user — will revisit when rebalancing. Do not tune before seeing it in play.

**Coming up:**
- Next session: Crafting and gathering redesign pass (or Battlegrounds design).
- Near term: Balance pass once Fate effects are visible in sim runs. Shadow deepening if Fate proves too dominant.
- Future ideas logged: none this session.

---

## Session 20 — June 22, 2026
**Combat difficulty curve problem: root cause, fix, and four-tier design**

**What was built:**
- `tools/sim/run_sim.js`: Added `--beta`, `--decouple`, `--sink`, `--rmax` CLI flags for independent member model. beta=0 removes cascade, decouple makes each member roll spikes independently, sink replaces 40% softcap with a reserve pool.
- `tools/sim/curve_lab.js`: Created abstract Monte Carlo lab testing 7 combat configurations (A–G). Proved Config E (beta=−0.6 + decouple + sink) widens the 20→80 win-rate band from 0.60 HP/s to 1.41 HP/s.
- `tools/sim/combat_curve_spec.md`: Design spec written in the claude.ai session documenting the cliff problem, all five levers, and measured results.
- `docs/combat-curve-problem.md`: Full design reference document — cliff problem diagnosis, all levers, simulation findings, three fight shapes, paths forward, open questions. Updated with the resolution.
- `tools/sim/food_model.js`: SCURVE_P changed 1.8→1.0 (linear); Hearthkeeper hpsHi changed 10→5.6. Hearthkeeper CL1–4 now maps to exactly 5.0/5.2/5.4/5.6 HP/s.
- `encounters/neekerbreekers_midgewater.json`: drain changed 18→16. designNote updated to reflect cull fight identity and four-tier win rates.
- `docs/design.md`: Added "Encounter Difficulty — Four Food Tiers" design section documenting T1–T4 win rate targets.
- `tools/sim/hearthcraft_fight_sim.html`: Rebuilt to match new mechanics — independent drain (drain/4 per member), per-member independent spike rolls, sink/reserve healing, updated TIER_TABLE and SCURVE_P. Built-in encounter dropdown pre-loaded with Neekerbreekers, Wolves, Goblin-town Gate (no spreadsheet needed to use them). RMAX=50 reserve pool constant added.
- `docs/combat-model.md`: Updated drain description (cascade → independent), food healing rule (softcap → sink), Neekerbreekers entry (new resolve/drain/win rates), Key Constants.
- `docs/mechanics-math-reference.md`: Updated drain formula, food HP/s section (sink model replacing softcap), tier table (SCURVE_P 1.0, Hearthkeeper 5.0–5.6 HP/s with per-CL breakdown), constants (RMAX=50 added).

**Decisions made:**
- **Four food tiers per encounter (not three).** T1≈25%/T2≈50%/T3≈70%/T4≈85–90%. The math simply cannot place T1 and T2 within 0.2 HP/s and T3 at 99% with consecutive cooking levels — four tiers with equal ~0.2 HP/s steps is the right shape.
- **Hearthkeeper tier redesigned.** HP/s range narrowed from 5–10 to 5.0–5.6. S-curve exponent changed to 1.0 (linear). CL1=5.0/CL2=5.2/CL3=5.4/CL4=5.6. Adjacent levels are 0.2 HP/s apart — exactly the granularity needed to straddle the transition zone.
- **Neekerbreekers drain changed to 16.** At drain=18, CL1 was in the fail zone (0.7%). At drain=16, CL1=5.0 HP/s lands at 24% — the T1 target.
- **Independent member model is canonical.** beta=0 + decouple + sink is the correct long-term design. Cascade was a mistake.
- **Neekerbreekers is a cull fight.** Narrative: clear an infestation for a hobbit farmer. Win by killing (resolve=40000), not by surviving.

**Anything that diverged from docs/design.md:**
- Four tiers per encounter (was three in prior planning). docs/design.md updated with "Encounter Difficulty — Four Food Tiers" section.
- Hearthkeeper HP/s values are now 5.0–5.6 (was 5–10). Not previously documented in design.md (food model implementation detail, lives in food_model.js and combat-curve-problem.md).

**Coming up:**
- Next session: Validate Wolves in the Chetwood against new HP/s values. Re-run Goblin-town Gate test (Initiate tier food, armor mechanic). Design encounter 3 and tune its four tiers.
- Near term: Initiate+ tier recalibration when those encounters are designed. Browser sim TIER_TABLE deduplication.
- Future ideas logged: none this session.

---

## Session 19 — June 20, 2026
**Food model redesign: stat bonuses, shared module, Neekerbreekers re-validation**

**What was built:**
- `tools/sim/food_model.js`: New shared module containing TIER_TABLE, recipe definitions with primaryStat/secondaryStat, and helper functions (statBonusesAt, statBonusesForCookLevel, hpsAt, bonusSummary, flFromCookLevel). Used by both sims.
- `tools/sim/run_sim.js`: Added `--recipe`, `--recipes W,H,K,C`, and `--fl` CLI flags for per-member recipe assignment with stat bonuses. Stat bonuses applied to member stats when building party. Requires food_model.js.
- `tools/sim/hearthcraft_fight_sim.html`: Replaced per-member stat-focus selectors and strength slider with per-member recipe dropdowns (Hearthbread, Wanderer's Supper, Contemplative Tea, Ranger's Fare). Bonus summary line and bolded stat preview added. Loads food_model.js.
- `app/src/main/assets/data/recipes.json`: Added primaryStat/secondaryStat to tier-1 stat recipes. Added Ranger's Fare (Mig/Vit, levelRequired 1). Renamed Scholar's Tea → Contemplative Tea. Removed Lucky Dumplings.
- `docs/mechanics-math-reference.md`: New file — complete math reference for every formula, constant, and role breakdown in the combat system.
- `docs/design.md`: Added rule that Fate cannot be food-boosted. Updated food stat list to four stats (not five).
- `docs/combat-model.md`: Locked Neekerbreekers post-stat-bonus baselines (FL1=47%, FL2=79%).
- `future/wishlist.md`: Updated Fate/Shadow balance note with new locked baselines.

**Decisions made:**
- **Fate cannot be increased by food.** Fate is innate — grows with band level but not cookable. Prevents a single recipe from dominating all encounters by gaming Inspiration rates and spike evasion.
- **Lucky Dumplings removed.** Was the only Fate recipe. No natural replacement found that didn't reintroduce the same problem. Removed cleanly.
- **Hearthbread secondary: Vit→Mig** (dense bread builds strength). **Contemplative Tea secondary: Wil→Agi** (clear mind sharpens reflexes). Both previously had Fat as secondary, now corrected.
- **FL1 target set at ~47%** for Neekerbreekers — just below 50%, enough to signal "cook better food" without being demoralizing. Drain unchanged at 12; stat bonuses from the new food model naturally landed the encounter here.
- **Food model uses role-matched defaults** (W: Hearthbread, H: Wanderer's Supper, K: Contemplative Tea, C: Ranger's Fare) as the reference point for encounter validation.
- **food_model.js is the single source of truth** for recipe data and FL math in the sim toolchain.

**Anything that diverged from docs/design.md:**
- Fate no longer listed as a food-boostable stat. design.md updated.

**Coming up:**
- Next session: Design encounter 3 (first real difficulty ramp). Validate Wolves in the Chetwood post-stat-bonuses. Ingredient scarcity model.
- Near term: 5th role design. Encounter 3 unlock mechanic.
- Future ideas logged: Ingredient scarcity tiers (common/uncommon/rare) as the primary gate against food optimization abuse — noted in session, not yet logged to wishlist.

---

## Session 22 — June 25, 2026
**XP & Leveling Simulator — xp_lab.js**

**What was built:**
- `tools/sim/xp_lab.js`: Node simulator answering pacing, grind-ratio, and curve-shape questions for both the Cooking and Gathering level tracks (1–50). Single-file, all weights in one CONFIG block at the top, mirrors curve_lab.js style. `require('./food_model')` for TIER_TABLE — no duplication.
- Supports four level-curve shapes: `linear`, `power`, `exponential`, `tierWall` (power + multiplier at tier-boundary levels). Gathering uses its own constants but same machinery.
- Soft diminishing returns (`softDR`) flattens to a floor rather than zeroing — grind stays rewarding.
- Grade sampler: gathering level raises mean quality floor; region ceiling (world-stage gated) hard-caps the top.
- Four player profiles: Explorer (intended play), Grinder, Completionist, Idle-only.
- Idle/active asymmetry modeled: gathering XP from 48 background forage cycles/day (wall-clock); cooking XP gated by active engagement.
- Five output sections: milestone table, XP source breakdown (grind-ratio readout), ASCII level-over-days bar chart, Explorer-vs-Grinder comparison, sensitivity sweeps (DISC_XP, curve exponent P, curve shape).

**Decisions made:**
- `require('./food_model')` used for TIER_TABLE and tier mapping — single source of truth, as specified.
- Seeded PRNG (mulberry32) used so results are reproducible and sweeps are comparable.
- All CONFIG constants commented as PLACEHOLDERS. The sim's job is to find good values, not assume them.
- Grade distribution: sampled by soft weighting around a mean that rises with gathering level, hard-capped by region ceiling. This respects the structural rule: level = floor/consistency, region = ceiling.
- World-stage gating models cook-level thresholds for region access, capping available recipes, ingredients, and grade ceiling. Keeps XP from scaling unboundedly before the world opens.

**Baseline run findings (placeholders are broken in expected ways — that's the point):**
- Level curve is too steep: Explorer reaches Cook Tier 3 at day 171, everything else `>200d`. curveA/P need reduction.
- WIN_XP dominates Explorer cooking: 72% of total cook XP. Target is steady ≈ 55–70%. Cut WIN_XP or raise REPEAT_XP substantially.
- Gathering outpaces cooking: Explorer Gather Lv25 vs Cook Lv10 at day 200 — asymmetry is working mechanically but ratio may be too extreme once cook XP is rebalanced.
- Gathering grind ratio is too flat (94.5% steady): FIRSTSOURCE_XP barely registers because ingredient discovery rate is slow. Raise FIRSTSOURCE_XP or discovery probability.
- Grinder vs Explorer comparison is degenerate (both stuck before Tier 5) until the curve is fixed.

**Anything that diverged from docs/design.md:**
- Nothing — sim is a tuning instrument, not a design decision.

**Coming up:**
- Next session: Tune xp_lab CONFIG — cut WIN_XP, raise REPEAT_XP, flatten the curve — until the grind-ratio target (steady 55–70%, spikes 30–45%) is hit and Explorer visibly leads Grinder to Tier 5.
- Near term: Validate Wolves in the Chetwood. Design encounter 3.
- Future ideas logged: None this session.

---

## Session 20 — June 25, 2026
**Design filing: parked topics integrated into docs**

No code written. Wes handed over a large block of parked design material. Settled
design-ready content was integrated into the authoritative docs; unresolved topics
were filed into `future/`.

**What was built:**
- `docs/design.md`: Band starting regions added to each band header (Mithlost →
  Celondim/Duillond; Undermarch → Thorin's Halls; Greycloaks → Bree-land;
  Kingswake → placeholder pending narrative frame). Three-era narrative structure
  and the "adjacent to the Fellowship" frame added as a new subsection inside
  "Shape of the Whole Game." Encounter placement open thread noted (Goblin-town
  placement is era-geography wrong). Burglar archetype added as a full subsection
  under The Band (cohesion-exposure mechanic, loot-on-win rule, hire→recruit arc,
  the running hobbit gag, open questions listed).
- `docs/combat-model.md`: Black Arrow resolve chunk corrected 35% → 18% with
  rationale. Bullroarer's Five-Iron added as the Might-Hunter inspiration.
  Inspiration flavor text subsection added with seed lines for all five
  inspirations and the stat-agnostic vs. named-member open choice.
- `docs/voice-tone.md` (new): full voice and tone guide. Core rule, rules of the
  register, race/culture voice table, vocabulary discipline, burglar-gag worked
  example, tone fork open question.
- `future/design/galadriel-mirrors.md` (new): full design capture for the
  Galadriel's Mirrors system — lore framing, outlast-fight mechanic, knowledge-
  only rewards, post-Moria gating, return-loop, campaign-gated secrets.
- `future/wishlist.md`: new sections appended — Racial Affinities (all explored/
  rejected directions captured so they aren't re-pitched), Burglar open design
  questions, Inspiration stat-scaling optional knob, Encounter Ladder Placement
  vs. eastward journey geography, Narrative Tone Fork, Kingswake home region
  placeholder.

**Decisions made:**
- Black Arrow chunk: 35% → 18%. 35% rescued badly-provisioned fights (outcome
  randomness overriding preparation failure — design-philosophy violation). 18%
  pulls a close loss back to winnable without rescuing a bad one.
- Bullroarer's Five-Iron: locked as the Might-Hunter inspiration. Same mechanical
  role as Black Arrow. Named for Bandobras Took knocking Golfimbul's head off —
  lore-native deep-cut gag.
- Three-era structure: Era 1 (Eriador, free), Rivendell hinge (Elrond's charge),
  Era 2 (east of mountains, war's wake). Palette merge (bands converge) is early
  at the Lone-Lands; era hinge is later at Rivendell. Two separate transitions.
- Goblin-town at recLevel 5 is geographically wrong (era 1 placement, but
  Goblin-town is post-hinge territory). Flagged as an open thread; encounter
  ladder placement must be reconciled with three-era geography before campaign
  layer is locked.
- Voice & tone guide is now authoritative for all player-facing writing.

**Anything that diverged from docs/design.md:**
- Nothing diverged — this session only added material.

**Coming up:**
- Next session: Tune xp_lab CONFIG (the deferred XP-calibration task from last
  session — this session was design filing only).
- Near term: Reconcile encounter ladder placement with three-era geography.
  Goblin-town needs to move; the Rung 1 armored-enemy teacher foe for era 1
  needs a replacement.
- Future ideas logged: Galadriel's Mirrors, racial affinities, burglar open
  questions, Kingswake home region, encounter placement — all in future/.

---

## Session 21 — June 25, 2026
**V2 Encounter Engine: full combat resolver wired into Android**

Replaced the placeholder buff-strength mission model with a real headless combat
engine. Twelve tasks, three phases. Executed via subagent-driven development.

**What was built:**

Phase 1 — Data:
- `app/src/main/assets/data/bands.json`: rewritten with canonical IDs
  (mithlost/undermarch/greycloaks), canonical regions, Kingswake removed, deprecated player titles removed
- `app/src/main/assets/data/band_members.json`: bandIds updated to match
- `app/src/main/kotlin/.../ui/screen/BandSelectionScreen.kt`: corsair_fleet branch removed, IDs fixed
- `app/src/main/assets/data/encounters.json` (new): nine encounters across three bands.
  Easy (Rung 0a swarm), medium (Rung 0b spike-heavy), hard (Rung 1 armored goblin incursion).
  All parameters match validated combat-model.md values.

Phase 2 — Model/engine:
- `data/model/Encounter.kt` (new): Encounter and Stage data classes, all fields
  matching encounters.json schema
- `engine/EncounterEngine.kt` (new): headless combat resolver, direct Kotlin port of
  tools/sim/run_sim.js. Constants match exactly: PEN_SCALE=80, RESCUE_CAP=5,
  WARD_CAP=3, GRIEVOUS=5, RMAX=50, JITTER=0.10. All DPS formulas match.
- `engine/EncounterEngineTest.kt` (new): three probabilistic unit tests
- `data/repository/EncounterRepository.kt` (new): forBand(), get()
- `data/repository/GameDataRepository.kt`: added encounters lazy load
- `data/db/EncounterSession.kt` (new): Room entity for active encounter sessions
- `data/db/dao/EncounterSessionDao.kt` (new): upsert, observe, get, clear
- `data/db/HearthCraftDatabase.kt`: version bumped 5→6, AutoMigration, EncounterSession added
- `di/DatabaseModule.kt`: provideEncounterSessionDao added
- `data/repository/SessionRepository.kt`: observeEncounter, activeEncounter, startEncounter, clearEncounter added

Phase 3 — Wiring:
- `worker/EncounterWorker.kt` (new): WorkManager worker, loads encounter, builds
  MemberInput from live stats, calls EncounterEngine.resolve(), applies wounds/rewards
- `data/repository/BandRepository.kt`: memberInputsForBand() (suspend) added
- `ui/viewmodel/UiModels.kt`: EncounterDetail data class added
- `ui/viewmodel/BandViewModel.kt`: missions replaced by encounters StateFlow,
  sendOnMission replaced by sendOnEncounter, draughtPotency + draught selector added,
  activeEncounterSession StateFlow added
- `ui/screen/MissionBoardScreen.kt`: rewritten to encounter API, draught selector,
  locked/unlocked state display
- `ui/screen/BandScreen.kt`: migrated to encounter API, active session display fixed
- `app/src/main/assets/data/missions.json`: deleted
- `app/src/main/assets/data/encounters/` subfolder: all old per-band sim stubs deleted

**Decisions made:**
- Kingswake / corsair_fleet: removed entirely. Three bands only.
- Per-member food provisioning: deferred. All members currently receive the same
  HP/s from the selected food item. Full "cook the right dish for the right member"
  puzzle is V2 polish.
- MissionWorker kept: active MissionSessions from pre-upgrade must complete cleanly.
- Tick order: EncounterEngine runs food→DPS→drain→spike (slight 1-tick difference
  from JS sim which runs drain→spike→food). Party is ~1 tick more favorable.
  Documented; acceptable for V1.
- draughtPotency: party-wide, read from members.first(). All members receive same
  value from memberInputsForBand() — this is correct per design ("one draught choice
  per encounter, applied to all party members").
- SHADOW_FLOOR/SHADOW_RATE: constants declared in engine but shadow drain tick not
  implemented. Shadow encounters don't exist in V1 — deferred with TODO comment.

**Anything that diverged from docs/design.md:**
- Mithlost easy encounter is midges (not spiders as the earlier combat-model note
  had stated). combat-model.md updated to reflect mithlost_midges / mithlost_wargs.

**Coming up:**
- Next session: Build in Android Studio, verify Room migration, play the loop on device.
  Commit Room schema 6.json after first successful build.
- Near term: Tune XP constants (xp_lab — still deferred). Per-member food provisioning.
  Draught item system.
- Future ideas logged: draught item system, per-member provisioning, MissionWorker
  cleanup (once all V1 sessions have expired).

---

## Session 23 — June 27, 2026
**Dread redesign: two-layer model implemented in both sims**

**What was built:**
- `tools/sim/hearthcraft_fight_sim.html`: Layer A floor drag formula replaces old single multiplier. Layer B stun/break gates fire at BREAK_CHECK_INTERVAL. stunned/broken member state added. active() excludes broken members. Defeat check includes broken. buildSummary shows "fled the field." Per-member DPS display shows "frozen"/"fled" states. Series push includes layerADrag. Results tab dread card updated with Layer A drag bar + Layer B event count footer.
- `tools/sim/run_sim.js`: Mirror of all browser changes. report() prints dread line with avg stuns/breaks per fight. _result() returns stunCount/breakCount. runMany() accumulates and surfaces totals.
- `docs/combat-model.md`: Dread section replaced with two-layer model. Key Constants table extended with all dread constants.
- `docs/superpowers/specs/2026-06-27-dread-redesign.md`: Full approved spec (committed previous session).
- `docs/superpowers/plans/2026-06-27-dread-redesign.md`: Seven-task implementation plan committed.

**Decisions made:**
- **Layer A formula**: `layerADrag = min(1, effectiveDread × 0.008 + rawDread × 0.003)` — placeholder values, tuning sweep required.
- **Layer B thresholds**: T_TEMP=0.65, T_PERM=0.35 — placeholder, same caveat.
- **Broken members excluded from active()** — same code path as grievous for DPS, healing, Inspirations, and defeat check.
- **Stunned members excluded from food healing and Keeper rescue** — they still take drain/spikes.
- **Captain stunned → willCut = 0 that tick** — prevents the Captain providing cover while incapacitated.

**Anything that diverged from docs/design.md:**
- None. Dread redesign was spec-first; design.md not affected.

**Coming up:**
- Next session: Tune dread constants via Monte Carlo sweep. Address user's incoming design changes and attachments.
- Near term: Damage types (Westernesse) — deferred from this session. Encounter 3 design and validation.
- Future ideas logged: Morale-break flavor text (Tolkien-appropriate flight vocabulary, separate from death language).

---

## Session 24 — June 27, 2026
**App data rebuild: Excel workbooks → JSON, Kotlin models updated, build verified**

**What was built:**
- `app/src/main/assets/data/ingredients.json`: Replaced entirely from hearthcraft_ingredients.xlsx (134 ingredients across 6 regions, full stat affinity data).
- `app/src/main/assets/data/recipes.json`: Replaced entirely from hearthcraft_food_master.xlsx (40 band-specific recipes: 15 Greycloaks, 10 Mithlost, 10 Undermarch, 5 universal).
- `app/src/main/assets/data/bands.json`: Kingswake (corsair_fleet) removed — band is dead.
- `data/model/Ingredient.kt`: Updated to new schema (added region, source, primaryStat, secondaryStat, hazardTendency, notes; removed old type/flavor fields not referenced by any code).
- `data/model/Recipe.kt`: Updated to new band-specific schema (added band, recipeClass, method, tier, cookLevel, primaryStat, secondaryStat, tertiaryStat, hazardEffect; old fields buffType/durationMs/levelRequired/etc. now computed as derived properties from new fields so no UI code broke).
- `app/src/main/assets/data/missions.json`: Removed 3 dead Kingswake missions. (This file was later fully replaced by encounters.json.)

**Decisions made:**
- **Recipe.buffType** is derived from primaryStat (mig→might, agi→agility, vit→vitality, wil→will). Draughts use their recipeClass.
- **Recipe.durationMs** derived from tier: tier 1=30min, 2=45min, 3=60min, 4=90min, 5+=120min.
- **Recipe.baseBuffStrength** = tier × 5; **buffStrengthPerLevel** = tier × 0.5.
- **willowherb** added to ingredients.json manually — present in food master Ingredients tab but missing from the JSON Schema tab.

**Anything that diverged from docs/design.md:**
- Kingswake removal confirmed. V1 has three bands: Mithlost, Undermarch, Greycloaks.

**Coming up:**
- Next session: Build in Android Studio, commit Room schema 6.json, install on device and walk the full loop.
- Near term: KitchenViewModel tier grouping (uses old levelRequired ranges — should use recipe.tier). Dread constant tuning sweep.

---

## Session 27 — June 27, 2026
**Recipe discovery system — GW2-style experiment mode with proximity feedback**

Full implementation of the recipe discovery system: recipes are now hidden until discovered through experimentation or levelling up. Built across two sub-sessions (design + implementation).

**What was built:**
- `docs/superpowers/specs/2026-06-27-recipe-discovery-design.md`: Full design spec committed.
- `docs/superpowers/plans/2026-06-27-recipe-discovery.md`: 5-task implementation plan.
- `tools/generate_data.py` + `tools/README.md` + `tools/data/*.xlsx`: Spreadsheet data pipeline committed.
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/PlayerState.kt`: Added `discoveredRecipeIds: String` and `hasSeenFoodStructureHints: Boolean`.
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`: DB version bumped to 7 with AutoMigration(6→7).
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt`: Added `observeDiscoveredIds()`, `discoverRecipe()`, `discoverRecipes()`, `markHintsSeen()`.
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngine.kt`: Pure Kotlin engine — exact-match + 4-tier proximity feedback (NONE/SOME/CLOSE/NEAR_MISS).
- `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/RecipeDiscoveryEngineTest.kt`: 7 JUnit tests, all passing.
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/KitchenViewModel.kt`: `tieredRecipes` and `bandRecipes` now filtered to discovered-only; full experiment state; starter-recipe seed on init.
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/CookingWorker.kt`: Discovers the just-cooked recipe; auto-discovers all band-accessible recipes on level-up.
- `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/KitchenScreen.kt`: Recipes|Experiment tab toggle; ExperimentPanel (method chips, ingredient picker, submit, result card); FoodHintsCard (collapsible, shown at cookLevel ≥ 3).

**Decisions made:**
- Ingredients are consumed on experiment failure (real stakes, like GW2).
- Discovery is exact-match only (ingredient set + quantities + method must all be right).
- Proximity feedback is 4-tier — guides without spoiling.
- Ingredient categories (grain/liquid/herb/etc.) are hidden from the player; only food structure hints are shown.
- Starter recipes (cookLevel ≤ 1) are auto-seeded when `discoveredRecipeIds` is blank (new player handling).
- Auto-discover on level-up only discovers recipes belonging to the player's current band (or universal).
- FoodHintsCard uses local `expanded` state so it can be freely toggled after first collapse; `markHintsSeen()` fires only once on the first collapse.

**Anything that diverged from docs/design.md:**
- None. Discovery system was a new addition, not a redesign.

**Coming up:**
- Next session: Install on device, test discovery loop end-to-end. Then: update spreadsheets (3 back-ports), run generate_data.py, commit clean JSON. Then: home screen as hub (tappable navigation cards).
- Near term: Band-specific visual backgrounds. Seeds gated behind first wild find. Mission difficulty prediction.
- Future ideas logged: none this session.
- Future: Wounds redesign, damage types, Wolves retune — all V2 sim work, deferred.

---

## Session 38 — June 27, 2026
**Device testing polish: 7 fixes from reported issues**

**What was built:**
- `KitchenScreen.kt`: Renamed "Experiment" tab → "Discover"; updated tab empty-state hint and submit button text
- `KitchenViewModel.kt`: Reduced starter recipe seed from 19 (all cookLevel ≤ 1) to 4 explicit starters — 3 universals (hearthbread, wanderers_supper, contemplative_tea) + 1 band-specific (ember_porridge/springwater_broth/delvers_hash)
- `GatheringViewModel.kt`: Garden slots reduced from 4 → 2 (`(0..1)`); exposed `gatheringLevel: StateFlow<Int>`
- `GatheringScreen.kt`: Farm section gated behind gathering level 5 (locked card shown below); garden counter updated to "/2"
- `MissionsScreen.kt`: Send button now shows when encounter is selected regardless of food; "no provisions" warning shown when food is absent; hint text simplified
- `BandViewModel.kt`: `sendOnEncounter()` made food-optional (0 HP/s if no food); `SECOND_BAND_UNLOCK_COOKING_LEVEL` raised to 10; added `availableBandsForUnlock` StateFlow and `unlockSecondBand()` function; Band import added
- `BandSelectionViewModel.kt`: Removed `_secondBandId`, `selectSecond()`, and `player.setSecondBand()` from onboarding — player now picks one band only
- `BandSelectionScreen.kt`: Removed page 2 (second band selection); flow is now 3 pages (lore → pick band → welcome); opening lore text rewritten to Third Age / Eriador setting; WelcomePage updated (no second-band mention, "The first watch begins.")
- `BandScreen.kt`: Added `SecondBandUnlockCard` — shows at cooking level 10 when secondBandId is null; fixed label "Unlock at cooking 6" → "cooking 10"

**Decisions made:**
- Band selection: one band at start, second at cooking level 10 prompted in BandScreen (not an interrupt modal)
- Farm gate: gathering level 5 — visible as a locked card so player knows it exists
- Mission send: food is optional; going unfed is a valid (costly) choice, not a blocker
- Starter recipes: 4 explicit IDs rather than "all cookLevel ≤ 1" — keeps recipe book sparse on day 1

**Anything that diverged from docs/design.md:**
- None — these were all UX corrections not previously documented

**Coming up:**
- Next session: Home screen redesign — nav cards + active status + band thumbnail + recent news/flavor text + journal sub-screen
- Near term: Band member detail cards (clickable, stat bar readout). Onboarding lore can be further deepened per band.
- Future ideas logged: none this session.

---

## Session 39 — June 27, 2026
**Home screen hub redesign + Journal sub-screen**

**What was built:**
- `HomeViewModel.kt`: Added injections for `GameDataRepository` and `BandRepository`; new flows: `cookingRecipeName`, `encounterSession`, `encounterName`, `activeBandName`, `aliveMemberCount`, `woundedMemberCount`, `discoveredCount`
- `HomeScreen.kt`: Full rewrite — flavor text (state-driven), active session timers (cooking/foraging/mission with countdown), band thumbnail card (name + member status), 2×2 navigation cards (Gather/Kitchen/Band/Missions with tappable nav), skills XP bars, Journal button, version label
- `JournalViewModel.kt` (new): Exposes `discoveredRecipes` filtered to active band, sorted by tier/level
- `JournalScreen.kt` (new): Three sections — Stats glossary (VIT/MGT/AGI/WIL/FAT explained), Food effects reference (11 effects), Discovered recipes list grouped by tier
- `MainScreen.kt`: Wired `onNavigate` and `onOpenJournal` callbacks into `HomeScreen`; added `composable("journal")` route

**Decisions made:**
- Active timers show countdown live on home screen; nav cards show just "active" or "ready" (not timers — detail belongs in each screen)
- Flavor text is state-driven: mission → "The band is away", cooking → "Something on the fire", foraging → "Out gathering", else → "The hearth is quiet"
- Journal is a pop-to sub-screen (no bottom nav), reachable via Journal button on Home
- Discovered recipes in journal filtered to active band (same filter as kitchen)

**Anything that diverged from docs/design.md:**
- None

**Coming up:**
- Next session: Band member detail cards — clickable rows in BandScreen → stat bar graph readout
- Near term: Market screen implementation

---

## Session 42 — June 28, 2026
**Forage targeting: discovery gating + targeted duration; farm/garden seed drops; post-harvest XP readout; region/recipe audit; Ferny's Treacle**

**What was built:**
- `PlayerState.kt`: Added `discoveredIngredientIds: String = ""`
- `HearthCraftDatabase.kt`: Bumped to v9 with AutoMigration from v8
- `PlayerRepository.kt`: Added `observeDiscoveredIngredientIds()`, `getDiscoveredIngredientIds()`, `discoverIngredients()`, `XP_GATHER_DISCOVERY = 20`
- `HarvestItem.kt`: Added `@Transient val isNew: Boolean = false` — set by ViewModel at collect time, not stored in DB
- `UiModels.kt`: Added `HarvestReadout(items, baseXp, discoveryBonusXp)` data class
- `GatheringViewModel.kt`: `foragableIngredients` now filtered to discovered ingredients only; `collectForage()` detects new discoveries, awards +20 XP per discovery, marks items with `isNew`; `collectGrowingSlot()` routes `rarity == "bonus"` items to seed stock; `startForage()` uses 5-min delay for targeted sessions vs 3-min random; `_lastHarvest` type changed from `List<HarvestItem>` to `HarvestReadout?`
- `GatheringScreen.kt`: Harvest dialog now shows items with NEW badges + XP breakdown (base + discovery bonus); target section shows "Forage a few times to discover ingredients you can target" when no discoveries yet; target row shows "(+2 min)" cost hint; Start button shows "Forage for [Name] — 5 min" when targeted
- `FarmWorker.kt`: Harvest result now includes 1–2 seeds of the grown ingredient (`rarity: "bonus"`)
- `GardenWorker.kt`: Same seed drop as FarmWorker
- `GatheringWorker.kt`: Added "Cardolan" and "Wildwood" to greycloaks region keywords (lore-correct: adjacent lands in their patrol zone)
- `recipes.json`: Fixed 6 cross-region violations: `bilberry_tonic` sunpetal_herb→meadowsweet; `hearthbread`/`wanderers_supper` band all→greycloaks; `contemplative_tea` band all→mithlost; `restorative_broth` band all→greycloaks + sunpetal_herb→yarrow; `athelas_infusion` band all→greycloaks + moonpetal→meadowsweet + ironbark_resin→wolf_moss. Added `fernys_treacle` (greycloaks, lv1, deliberately useless, ethically dubious description)

**Decisions made:**
- Targeting gated behind discovery: forage first, then pin. Makes discovery meaningful.
- Targeted forage costs +2 min (5 min total). Represents focused, deliberate searching.
- Discovery bonus: +20 XP per newly found ingredient, on top of base +90 session XP
- Farm/garden always drops 1–2 seeds. Lets player sustain themselves without constant foraging.
- "band: all" recipes that were using region-specific ingredients were not truly universal — converted to the appropriate band rather than inventing fictional universal ingredients.
- Ferny's Treacle: tier 1 vit food with minimum possible buff, rendered fat + nettleleaf, description does the work

**Anything that diverged from docs/design.md:**
- None

**Coming up:**
- Next session: Market screen
- Near term: Band member detail stat cards
**Forage targeting**

**What was built:**
- `UiModels.kt`: Added `ForageTargetDetail(ingredientId, name, rarity)` data class
- `GatheringWorker.kt`: Added `KEY_TARGET_ID` constant; `buildRequest()` now accepts optional `targetId: String?`; `doWork()` guarantees the target ingredient fills the first slot if it's in the band's regional pool (rest of slots remain random)
- `GatheringViewModel.kt`: Added `foragableIngredients: StateFlow<List<ForageTargetDetail>>` (all forageable ingredients for the player's band, sorted by name); `forageTargetId: StateFlow<String?>` and `setForageTarget()`; `startForage()` passes target to Worker
- `GatheringScreen.kt`: Added target row under "Forage" header showing current target or "none (random)"; Change/Clear buttons; `ForageTargetDialog` composable lists all band-region ingredients with rarity labels; Start button label updates to "Forage for [Name] — 3 min" when target is set

**Decisions made:**
- Target list shows all forageable ingredients in the band's region, not just previously found ones — simpler, equally functional
- Target is in-memory only (ViewModel StateFlow, not DB) — resets on app restart, which is fine since it's a per-session decision
- Targeting guarantees the ingredient appears but doesn't eliminate other drops — still get remaining slots as random haul

**Anything that diverged from docs/design.md:**
- None

**Coming up:**
- Next session: Market screen — buy ingredients and seeds with gold
- Near term: Deeper onboarding for each band
**Early mission termination + post-fight readout + XP bars + role abilities + recipe gates**

**What was built:**
- `EncounterEngine.kt`: Added `endedAtSec: Int` to `EncounterResult` — records the actual game-time tick the fight ended (VICTORY/DEFEAT at tick `t`, STALEMATE at full `durationSec`)
- `CombatReport.kt` (new): Room entity storing pre-computed fight results (outcome, wounds, rescues, wardGuards, resolveRemainingFraction, endedAtSec, durationSec)
- `CombatReportDao.kt` (new): Upsert, get, observe (Flow), clear by bandId
- `CombatRepository.kt` (new): Thin repository wrapping CombatReportDao
- `HearthCraftDatabase.kt`: Bumped to v8, added CombatReport entity and AutoMigration from v7
- `DatabaseModule.kt`: Added `provideCombatReportDao`
- `BandViewModel.kt`: `sendOnEncounter()` now pre-computes fight via `EncounterEngine.resolve()`, calculates `actualDelayMs = endedAtSec * msPerGameSec`, saves CombatReport to DB, schedules WorkManager with `actualDelayMs` (not full `durationMs`). Added `combatReport: StateFlow<CombatReport?>` and `dismissCombatReport()`
- `EncounterWorker.kt`: Reads stored CombatReport and applies pre-computed outcome; fallback re-runs engine if no report found (handles in-flight tasks during upgrade)
- `MissionsScreen.kt`: Added `CombatReportCard` composable — colored card (green/red/amber by outcome), Tolkienesque narrative, fight duration, rescues/shields used, dismiss button. Shows between mission timer and encounter selector.
- `GatheringViewModel.kt`: Added `gatheringXpProgress: StateFlow<XpProgress>` with correct power-curve formula
- `KitchenViewModel.kt`: Added `cookingXpProgress: StateFlow<XpProgress>`; changed greycloaks starter to `ploughmans_plate`
- `GatheringScreen.kt`: XP bar at top of tab using `LinearProgressIndicator`
- `KitchenScreen.kt`: XP bar in fixed top section
- `HomeViewModel.kt`: Fixed `xpProgressFor()` — now uses `PlayerRepository.totalXpForLevel()` instead of old linear `l * 100` formula; was causing negative earned XP display
- `BandScreen.kt`: Added `roleAbility()` in member detail dialog — shows per-role ability name and description (Warden: shield guard, Hunter: relentless shot, Keeper: rescue grace, Captain: will of the host)
- `recipes.json`: Fixed 4 recipe cook levels — `sloe_bitters`, `keenwater_tincture`, `ironbite_stout` → cookLevel 5; `ember_porridge` → cookLevel 3, tier 2
- `PlayerRepository.kt`: Moved `enum class Track` from inside `companion object` to direct nested class of `PlayerRepository` (fixes "Unresolved reference 'Track'" compiler error)

**Decisions made:**
- Pre-compute fight at dispatch, store in DB, Worker reads stored result — no double computation, accurate countdown timer
- Forage targeting deferred to next session
- Live animated combat screen deferred (future)

**Anything that diverged from docs/design.md:**
- None

**Coming up:**
- Next session: Forage targeting — pin discovered ingredients as forage priority
- Near term: Market screen implementation
- Future ideas logged: none this session.

---

## Session 43 — June 28, 2026
**Audit June 2026: per-member provisioning, free-assembly recipe discovery, beekeeping, five targeted fixes**

**What was built:**
- `Recipe.kt`: Added `primaryBoost: Int = 0`, `secondaryBoost: Int = 0` fields; all food recipes now carry explicit stat boost values in `recipes.json`
- `recipes.json`: Contemplative Tea changed from `band: "mithlost"` to `band: "all"` — fixes Greycloaks Will food gap; boost values added to 26 food recipes following tier-based scaling
- `PlayerState.kt`: Added `hasSeenExperimentHint`, `hasSeenPostForageNudge`, `hasReceivedStarterPantry` boolean fields
- `HearthCraftDatabase.kt`: Bumped to v10, AutoMigration(from=9, to=10) added
- `PlayerRepository.kt`: Added `markExperimentHintSeen()`, `markPostForageNudgeSeen()`, `markStarterPantryReceived()`, `observeHasReceivedStarterPantry()` helpers
- `BandRepository.kt`: `memberInputsForBand()` signature changed to accept `Map<String, Recipe?>` per member + `cookLevel: Int`; stat bonuses applied per member; HP/s now computed from 7-tier cook level table (mirrors `food_model.js`); `hpsForCookLevel()` and `statBonusFor()` in companion object
- `BandViewModel.kt`: `_selectedFood`/`selectFood()` removed; replaced with `_memberFood: Map<String, PreparedFoodDetail?>`, `assignFoodToMember()`, `clearMemberFood()`; `sendOnEncounter()` now builds per-member recipe map, applies stat bonuses, and consumes one PreparedFood per member
- `UiModels.kt`: `PreparedFoodDetail` gains `primaryStat`, `primaryBoost`, `secondaryStat`, `secondaryBoost` fields
- `EncounterWorker.kt`: Fallback path updated to use `emptyMap(), cookLevel=1`
- `BandScreen.kt`: Provisioning dialog added — one row per living member, food picker with stat preview (MIG/AGI/VIT/WIL/FAT abbreviations), Assign/Change/Clear per member
- `KitchenViewModel.kt`: `submitExperiment()` deleted; replaced with `evaluateLive()` (free, no cost) and `commitDiscovery()` (spends ingredients, only callable on exact match); `liveResult`, `canCommit`, `experimentHintSeen` state flows added
- `KitchenScreen.kt`: Experiment tab now shows live proximity feedback as player assembles ingredients; "Cook it" button enabled only on exact match; one-time dismissible hint card on first visit
- `starter_inventory.json`: New asset; 5 band-appropriate ingredients seeded on first launch
- `GameDataRepository.kt`: Added `starterInventoryFor(bandId)` method
- `KitchenViewModel.init{}`: Starter pantry seeded on first launch, guarded by `hasReceivedStarterPantry`
- `GatheringViewModel.kt`: Added `showPostForageNudge`, `dismissPostForageNudge()`, `hiveSlot`, `startHive()`, `DURATION_HIVE_MS = 10 * 60 * 1000L`
- `GatheringScreen.kt`: Post-forage nudge card; "Finishing up…" state when forage timer elapses before worker fires; Hive section (locked below level 8); `GrowingSlotCard` reused for hive slot
- `GatheringWorker.kt`: Seed drop now unconditional (removed 25% roll); `SEED_DROP_CHANCE` constant deleted
- `PlayerRepository.kt`: `XP_GATHER_SESSION` reduced 90 → 30
- `ingredients.json`: `forest_honey` and `field_honey` changed to `gatheringMode: "forage"` — forageable as wild finds, consistent with all honey types; `HiveWorker` produces `forest_honey` as the dedicated timer-based source
- `HiveWorker.kt`: New `@HiltWorker`; 10-min timer; produces 2–4 `forest_honey`; slot ID `"hive_0"`
- `GrowingRepository.kt`: Added `observeSlot(id: String): Flow<GrowingSlot?>` generic method

**Decisions made:**
- Per-member food provisioning: each member gets their own food before each mission; stat bonuses are explicit data on recipes (not formula-derived); HP/s from cook level tier table
- Recipe discovery: free assembly phase (live proximity feedback, no cost) + commit phase (spends ingredients only on exact match); submitExperiment() removed entirely
- Starter pantry seeds 5 band-appropriate ingredients on first launch to enable immediate Kitchen experimentation without foraging first
- Honey design: all honey types are forageable as wild finds; the hive system provides reliable timer-based supply; different hive types for different honey varieties = V2 (not in V1 scope)
- XP_GATHER_SESSION 90→30: level 5 now takes ~4 sessions (was 2); more sustainable progression
- Beekeeping unlocks at gathering level 8; one hive slot in V1

**Anything that diverged from docs/design.md:**
- Honey mechanics refined: honey is both forageable (wild) and hive-produced (reliable). Design.md mentions "honey → royal jelly → rare cultivars" as hive progression — that structure is preserved; V1 just has the first tier (forest honey from one hive type). Royal jelly and rare cultivars remain deferred to V2.

**Coming up:**
- Next session: Design session for hive system — multiple hive types gated by gathering level, each producing a different honey variety (field_honey hive, heather hive, etc.) — OR defer to V2 and move to Market screen
- Near term: Market screen (buy ingredients and seeds with gold from missions)
- Future ideas logged: Multiple hive types (different honey = different bees = different hive) — needs design session before building

---

## Session 44 — June 28, 2026
**Ingredient Sourcing Data Pass (Plan A)**

**What was built:**
- `Ingredient.kt`: Added `gatherType: String?`, `processType: String?`, and `processInputs: List<ProcessInput>?` fields (all nullable, null default); added `ProcessInput` data class
- `ingredients.json`: Full `gatherType` taxonomy applied to all 134 existing ingredients (cultivate/forage/hunt_fish/draw/husbandry/process/craft/mission/trade); `processType` added to 18 processed items; `gatheringMode` changed on exactly 5 honey types (forest_honey, stone_honey, white_nectar, field_honey, heather_honey → "husbandry" to drive hive-only production)
- `ingredients.json`: 7 new entries added (total 141): bree_well_water, brandywine_water, chetwood_spring, lhun_brine, deep_cistern_water, pass_snowmelt (all Draw water sources per band); milk (husbandry, all bands, future dairy)
- `recipes.json`: Three surgical fixes — ferny's_treacle swaps rendered_fat → hens_egg (rendered_fat now process-only); brookcress_bannock and sloe_bitters swap field_honey → forest_honey (field_honey now husbandry/hive-only and not accessible to Greycloaks)
- `HiveWorker.kt`: Region-locked via new `honeyForBand(bandId)` companion function — Greycloaks→forest_honey, Mithlost→white_nectar, Undermarch→stone_honey
- `GatheringViewModel.kt`: `startHive()` updated to derive honey type from player's band via `HiveWorker.honeyForBand()`
- All active design docs scrubbed of Kingswake (fourth band): `design/design.md`, `design/characters.md`, `design/combat-model.md`, `design/voice-tone.md`, `docs/roadmap.md`, `README.md`, `future/wishlist.md`

**Decisions made:**
- gatherType is informational only — `gatheringMode` continues to drive runtime gathering logic (unchanged except for 5 honey types); processInputs populated in Plan B when Process station ships
- Honey region-lock: each band's hive produces only their regional honey; field_honey and heather_honey exist in data but are not currently acquirable (reserved for Plan B or alternate hive variants)
- ferny's_treacle fixed to use hens_egg (cookLevel 1 starter must not require a process ingredient)
- All process ingredients set to cookLevel 1 gates for now — tune later once Process station is in
- Three bands confirmed as authoritative: greycloaks, mithlost, undermarch. No fourth band.

**Anything that diverged from design/design.md:**
- Nothing new; Kingswake removal was already flagged and applied across docs

**Coming up:**
- Next session: Plan B — Process station (timed kitchen station), Coop, Dairy workers
- Near term: Draw station UI; Market screen
- Future ideas logged: None this session

---

## Session 45 — June 28, 2026
**Process Station, Coop & Dairy (Plan B)**

**What was built:**
- `ingredients.json`: processInputs populated for 13 process ingredients; 4 new raw hunt_fish forage ingredients added (river_trout, eel, deer_haunch, grouse); total 145 ingredients
- `worker/ProcessWorker.kt`: New @HiltWorker — timed process station, per-type durations (mill 3min → brew 10min), uses cooking XP, produces qty=1 into GrowingSlot pending result
- `viewmodel/KitchenViewModel.kt`: Replaced `experimentMode` bool with `selectedTab: StateFlow<Int>` (0=Recipes, 1=Discover, 2=Process); added `GrowingRepository` injection; added `processSlot`, `processIngredients`, `canProcess()`, `startProcess()`, `collectProcess()`, `selectProcessIngredient()`
- `screen/KitchenScreen.kt`: 3-tab Kitchen (Recipes / Discover / Process); tab row always visible; CookingActiveCard moved inside Recipes branch; `ProcessPanel`, `ProcessItemRow`, `ProcessTimer` composables added
- `worker/CoopWorker.kt`: New @HiltWorker — produces hens_egg (2–3), 15-min timer, NOTIFICATION_ID=41, SLOT_ID="coop_0"
- `worker/DairyWorker.kt`: New @HiltWorker — produces milk (2–3), 20-min timer, NOTIFICATION_ID=42, SLOT_ID="dairy_0"
- `viewmodel/GatheringViewModel.kt`: coopSlot and dairySlot StateFlows; startCoop() and startDairy(); init auto-starts all three husbandry producers; collectGrowingSlot() uses `when(slotId)` for all three auto-restarts; DURATION_COOP_MS and DURATION_DAIRY_MS in companion object
- `screen/GatheringScreen.kt`: Coop and Dairy sections (after Hive, before Forage); CoopCard and DairyCard composables following HiveCard 3-state pattern
- `test/.../ProcessStationTest.kt`: 6 unit tests for canProcess logic including genuine multi-input case
- `test/.../WorkerConstantsTest.kt`: 4 new tests for CoopWorker and DairyWorker constants (12 total)

**Decisions made:**
- processInputs for 5 farm-animal items (salted_pork, smoked_goat, salt_mutton, rendered_tallow, dried_marrow_bone) left null — no livestock producer yet; these process items won't show in Process tab until a future plan
- `river_trout` region = "Bree-land / Celondim" — forageable by both greycloaks and mithlost (used for smoked_river_trout and lhun_saltfish)
- `lhun_olive_oil` processInputs uses `lhun_plum ×3` — closest existing cultivate ingredient for Mithlost
- Process station uses existing `growing_slots` table (type="process", slot="process_0") — no new Room table
- Process station cookLevel gates all set to 1 for now

**Anything that diverged from design/design.md:**
- None

**Coming up:**
- Next session: Draw station UI (6 water-source ingredients already in data), or Market screen
- Near term: Market screen; livestock producer design (goat/sheep/pig)
- Future ideas logged: Livestock producer system (needed to unlock 5 remaining process items)

---

## Session 46 — June 28, 2026
**Gather Screen Overhaul (Plan A from App Audit 28JUN2026 2.0)**

**What was built:**
- `data/repository/GrowingRepository.kt`: `addToPendingResult()` — merges `HarvestItem` lists by ingredientId (summing quantities) instead of overwriting; used by Hive/Coop/Dairy workers to accumulate across cycles
- `test/.../GrowingRepositoryStockpileTest.kt`: 3 unit tests for merge, keep-separate, and empty-existing cases
- `ui/viewmodel/GatheringViewModel.kt`: sub-tab state (`gatherSubTab`, `selectGatherSubTab`, 0=Growing/1=Wild/2=Producers) + badge count flows (`growingReadyCount`, `wildReady`, `producersReadyCount`); removed erroneous elapsed-cycle multiplier from `collectGrowingSlot` (workers handle accumulation themselves)
- `ui/screen/GatheringScreen.kt`: full restructure — fixed sticky header (title + XpBar + `GatherStatusStrip` with live countdown strip), `TabRow` with `BadgedBox` on all three tabs, `HorizontalPager` for swipe navigation; content split into `GrowingTab`, `WildTab`, `ProducersTab` composables
- `worker/GardenWorker.kt`: `BASE_YIELD` 3 → 5
- `worker/FarmWorker.kt`: `BASE_YIELD` 6 → 8
- `data/repository/PlayerRepository.kt`: `XP_GATHER_SESSION` 30 → 15; stale comment updated
- `ui/viewmodel/HomeViewModel.kt`: `hiveSlot`, `coopSlot`, `dairySlot` StateFlows observing producer slots
- `ui/screen/HomeScreen.kt`: Active section now shows Hive/Coop/Dairy running timers (via `ActiveTimerRow`) and "X: ready to collect" labels when `pendingResultJson != null`

**Decisions made:**
- Stockpile mechanic: workers self-reschedule and accumulate via `addToPendingResult` until cap (3 cycles × ~3 items); `collectGrowingSlot` just hands back whatever the workers accumulated — no additional multiplication
- An intermediate Task 2 implementation added elapsed-cycle multiplication to `collectGrowingSlot` but this double-counted because workers were already accumulating; removed in the final review fix commit (365678f)
- `GatherStatusStrip` uses a simple `Row` per spec; may overflow if all 7 slots are simultaneously active (future: `LazyRow` or `FlowRow`)
- `rememberPagerState` cold-starts at page 0; could pass `initialPage` snapshot in a future polish pass

**Anything that diverged from design/design.md:**
- None

**Coming up:**
- Next session: Kitchen improvements (Plan B) — Pantry fix, swipe tabs in Kitchen, 2-slot cooking
- Near term: Band/Nav/Missions (Plan C) — remove Market and Band from bottom nav, difficulty meter fix, missions pre-deploy panel, Contemplative Tea band fix
- Blocked: inspiration titles in Plan C need clarification from Wes

## Session 47 — June 28, 2026
**Kitchen Improvements (Plan B from App Audit 28JUN2026 2.0)**

**What was built:**
- `ui/screen/KitchenScreen.kt`: Pantry and Recipe Book buttons moved above the cooking-active check so they're always visible; outer layout replaced with `HorizontalPager` for swipe-to-switch between Recipes / Process tabs; two `CookingSlotCard` composables shown side by side — each shows slot label, recipe name (looked up from `viewModel.recipes`), and a live countdown
- `data/db/dao/CookingSessionDao.kt`: three slot-aware queries — `observeSlot(slot)`, `getSlot(slot)`, `clearSlot(slot)` — using `cooking_session.id` as the slot key (slot 0 → id=0, slot 1 → id=1; no schema change or migration needed)
- `data/repository/SessionRepository.kt`: `observeCookingSlot`, `activeCookingSlot`, `startCookingInSlot`, `clearCookingSlot` wrappers
- `worker/CookingWorker.kt`: reads `KEY_SLOT` from inputData; clears `clearCookingSlot(slot)` on completion; uses `NOTIFICATION_ID + slot` for distinct notifications per slot
- `ui/viewmodel/KitchenViewModel.kt`: `session0` and `session1` StateFlows; backward-compat `session` alias pointing at `session0`; `startCooking()` picks the first free slot (or returns if both busy)

**Decisions made:**
- `CookingSession.id` (existing `@PrimaryKey val id: Int = 0`) doubles as slot key — no new column or Room migration needed
- Two slots are always available; no unlock gate planned (F-Droid release, one game)
- `CookingActiveCard` is now dead code (unreferenced after Task 7 refactor); left in place as a minor cleanup deferred to a future pass

**Anything that diverged from design/design.md:**
- None

**Coming up:**
- Next session: Plan C — Band/Nav/Missions
- Near term: producer upgrade system (where Hive/Coop/Dairy gathering XP will live)
- Future: Pantry shortcut from more screens (wishlist)

---

## Session 48 — June 28, 2026
**Bug fixes: producer worker double-scheduling, dead-producer-at-cap, cap check, XP removal**

**What was built:**
- `worker/HiveWorker.kt`: unconditional self-reschedule (moved outside `if (!atCap)`); cap check fixed from `firstOrNull()?.quantity` to `sumOf { it.quantity }`; XP call removed
- `worker/CoopWorker.kt`: same fixes; `PlayerRepository` dependency removed entirely (no longer needed)
- `worker/DairyWorker.kt`: same fixes; `PlayerRepository` dependency removed entirely
- `data/repository/GrowingRepository.kt`: new `collectAndClearPendingOnly()` — returns items and clears `pendingResultJson` but keeps the slot row alive so the next worker cycle always has a valid row to write into
- `ui/viewmodel/GatheringViewModel.kt`: `collectGrowingSlot` uses `collectAndClearPendingOnly` for producer slots and `collectAndClearSlot` for garden/farm; `when` restart block removed (workers are self-sustaining)

**Decisions made:**
- Gathering XP for Hive/Coop/Dairy removed from passive cycles; will be awarded when producers are upgraded (upgrade system not yet built)
- "No XP at cap" is now moot — workers always reschedule so there's no cap-idle scenario to award XP for
- Producer slots are never deleted on collect (only `pendingResultJson` is cleared), unlike garden/farm which delete their slot so the player can replant

**Anything that diverged from design/design.md:**
- Gathering XP source for producers changed: passive cycle → upgrade action. Will update `design/design.md` when the upgrade system is specced.

**Coming up:**
- Next session: Plan C — Band/Nav/Missions (`docs/superpowers/plans/2026-06-28-band-nav-missions.md`)
- Near term: producer upgrade system + XP awards
- Blocked: Plan C Task 6 (inspiration titles) needs Wes to clarify what these are

## Session 49 — June 28, 2026
**Band / Nav / Missions Cleanup (Plan C from App Audit 28JUN2026 2.0) + Inspiration Names**

**What was built:**
- `ui/screen/MainScreen.kt`: Market and Band tabs removed from the bottom nav — 6 tabs down to 4 (Home, Gather, Kitchen, Missions). Both `composable("market")` and `composable("band")` routes remain in the NavHost for save-data compatibility. Band is now only reachable via the Home screen NavCard.
- `ui/viewmodel/BandViewModel.kt`: `anyFoodAssigned: StateFlow<Boolean>` — true when at least one member has food assigned; resets to false on band switch.
- `ui/screen/MissionsScreen.kt`: `EncounterCard` now accepts `provisioned: Boolean` and shows "Band unprovisioned — actual difficulty higher" in error color when false; `BandReadyPanel` composable added between encounter list and Send button — shows each alive member, their assigned food or "Unfed", and a summary header that turns green when all are fed or red otherwise; panel hidden when all members are dead.
- `app/src/main/assets/data/recipes.json`: Contemplative Tea `"band"` changed from `"all"` to `"mithlost"` — its ingredients (`sunpetal_herb`, `moonpetal`) grow in Celondim/Ered Luin (Mithlost region). Greycloaks retain two local Will foods: Brookcress Bannock and Honey Oat Cake. Region audit script confirmed zero mismatches across all bands.
- `ui/screen/BandScreen.kt` + `design/combat-model.md` + `implemented/mechanics-math-reference.md`: "Laurelin's Grace" renamed to "Hands of Healing" (Keeper inspiration, Greycloaks); Band screen now shows all four inspiration names — Hands of Healing / Horn of Gondor / Black Arrow / Wrath, Ruin, and the Red Dawn.

**Decisions made:**
- Market is hidden entirely (nav removed) rather than labeled "coming soon" — cleaner, no false promises
- Band screen shows inspiration names as the role ability header (e.g. "Hands of Healing") rather than generic descriptors ("Keeper's Grace")
- `anyFoodAssigned` warns on zero food assigned rather than partial — the `BandReadyPanel` directly below shows the full per-member truth, so the encounter-level warning is just a quick flag
- Contemplative Tea's original band was `"all"` (not `"greycloaks"` as the plan stated) — fix to `"mithlost"` is correct either way

**Anything that diverged from design/design.md:**
- None

**Coming up:**
- Next session: TBD — producer upgrade system, Band/Mission polish, or next audit pass
- Deferred: Undermarch and Mithlost inspiration names (Wes will supply; wishlist entry created)
- Minor notes from final review: `anyFoodAssigned` warning clears after feeding one member (intentional — BandReadyPanel shows full truth); M3 color-token pairing in BandReadyPanel uses `primary`/`error` rather than `onPrimaryContainer`/`onErrorContainer` (fine for current theme; revisit if dynamic color is ever added)

---

## Session 50 — June 29, 2026
**App Audit 28JUN2026 3.0 — Kitchen, Gathering, Band, and data fixes**

Full audit pass working through all confirmed issues from the 28JUN2026 audit document.

**What was built:**
- `app/src/main/assets/data/recipes.json`: "★ Existing. " prefix stripped from 9 recipe descriptions; methods consolidated — simmer/roast → cook, infuse → brew (only cook/bake/brew remain); `fernys_treacle` gets `"penalty": true` and `"primaryBoost": -2`.
- `app/src/main/assets/data/ingredients.json`: `salted_pork` changed to forage source with `processInputs: [{id: "hog_meat", qty: 1}]`; new `hog_meat` ingredient added (forage/hunt_fish, Bree-land region, mig/vit).
- `data/model/Recipe.kt`: `val penalty: Boolean = false` added to Recipe data class.
- `ui/viewmodel/KitchenViewModel.kt`: Starter recipe seeding fixed — 2 universals (hearthbread, wanderers_supper) + 2 band-specific starters each; Greycloaks now get brookcress_bannock (Will food) as second starter; Contemplative Tea seeded only for Mithlost; `_experimentMethod` default changed from "simmer" to "cook".
- `ui/viewmodel/GatheringViewModel.kt`: Forage filter excludes `gatherType == "process"` items (fixes smoked trout appearing as forageable); `ingredientName()` lookup function added.
- `ui/screen/GatheringScreen.kt`: Growing slot cards now display ingredient name (via `ingredientNameFor` lambda) instead of raw underscore-separated ID.
- `ui/screen/KitchenScreen.kt`: Tab 2 renamed Process → Prepare; methods list trimmed to 3 (cook/bake/brew); kiln labels changed to "Kiln 1"/"Kiln 2"; tier labels no longer show level suffix (just "Hearthkeeper", not "Hearthkeeper · Lv 1"); recipe list always visible — detail panel shown when recipe selected, Start Cooking button hidden only when both kilns busy; auto-scroll to top on recipe selection; RecipeRow shows stat letter (M/A/V/W) instead of method tag, italic for draughts (Warm/P/H/R/Alt), error color for penalty recipes; RecipeDetailPanel shows penalty effect in error color using `primaryBoost` directly; ProcessPanel text renamed prepare/preparing/Start Preparing.
- `ui/screen/RecipeBookScreen.kt`: effectLine handles `recipe.penalty` — shows `"StatName -2"` in error color.
- `ui/screen/BandScreen.kt`: "Send the Band" header label removed; Captain ability description corrected — "party damage ×1.5 for ten strikes" replaces incorrect "bypasses armor" description.

**Decisions made:**
- Method consolidation to 3 is data-only; RecipeDiscoveryEngine still matches on method so old experiments with "simmer" would fail — acceptable since discovery state persists the method string, and "simmer" recipes in JSON are now "cook".
- Ironroot not moved (would break 4 Greycloaks recipes); flagged as blocked pending substitute ingredient work.
- Food structures hint card (`FoodHintsCard`) left unchanged — Wes flagged it for a separate redesign pass.
- Penalty display uses `primaryBoost` directly (−2) rather than deriving from the buff formula, so the negative value is always exact regardless of cooking level.

**Anything that diverged from design/design.md:**
- None

**Coming up:**
- Next session: Producer upgrade system, or test on device and next audit round
- Near term: Ironroot region move (needs 4 Greycloaks recipe substitutions first); Undermarch/Mithlost inspiration names
- Future ideas logged: None this session

---

## Session 50 — June 30, 2026
**Code Review Bug Fixes — all 15 issues resolved**

Post-Session-49 code review surfaced 9 correctness bugs and 6 cleanup issues. All were fixed across 6 tasks using subagent-driven development.

**What was built:**
- `data/db/dao/GrowingSlotDao.kt`: new `updatePlantedAt(id, ms)` query
- `data/repository/GrowingRepository.kt`: `addToPendingResult` now atomic (cap check + write in one DB read, returns Boolean); both collect methods and `addToPendingResult` hardened with `runCatching` on JSON decode; new `updatePlantedAt` delegate
- `worker/HiveWorker.kt`: `enqueue` → `enqueueUniqueWork(SLOT_ID, KEEP, ...)` prevents chain doubling; calls `updatePlantedAt` at cycle start so timers are accurate after collect; cap check delegated to repository
- `worker/CoopWorker.kt`: same fixes as HiveWorker
- `worker/DairyWorker.kt`: same fixes as HiveWorker
- `ui/viewmodel/KitchenViewModel.kt`: `startCooking` wrapped in `Mutex.withLock` to prevent double-tap ingredient deduction; removed `session` backward-compat alias
- `ui/screen/GatheringScreen.kt`: pager LaunchedEffects now guard with `if (currentPage != subTab)` before animating — matches KitchenScreen pattern
- `data/db/dao/CookingSessionDao.kt`: removed three `WHERE id = 0` hardcoded methods (`observe`, `get`, `clear`)
- `data/repository/SessionRepository.kt`: removed `observeCooking`, `startCooking`, `clearCooking`, `activeCooking` (no callers)
- `ui/viewmodel/HomeViewModel.kt`: replaced single `cookingSession`/`cookingRecipeName` (slot-0 only) with `cookingSession0`, `cookingSession1`, `cookingRecipeName0`, `cookingRecipeName1`, `anyCookingActive`
- `ui/screen/HomeScreen.kt`: shows both cooking slots; all five `cSession` references updated
- `ui/viewmodel/BandViewModel.kt`: added `allAliveProvisioned: StateFlow<Boolean>` (true only when every alive member has food)
- `ui/screen/MissionsScreen.kt`: `EncounterCard` now uses `allAliveProvisioned` instead of `anyFoodAssigned`
- `ui/util/TimeFormat.kt`: new shared `formatMs(ms: Long): String` — extracted from 3 duplicate copies
- `ui/screen/BandScreen.kt`: removed private `formatMs`, imports shared one
- `ui/screen/KitchenScreen.kt`: removed private `formatMs`; deleted dead `CookingActiveCard` composable
- `ui/viewmodel/GatheringViewModel.kt`: replaced `producerSlotIds` SLOT_ID set with `selfReschedulingTypes = setOf("hive", "coop", "dairy")` slot-type check
- `test/.../GrowingRepositoryStockpileTest.kt`: two new tests for JSON hardening and cap logic

**Decisions made:**
- `addToPendingResult` returns `Boolean` (backward-compatible; default `maxQty = Int.MAX_VALUE`): simplifies workers and eliminates the cap TOCTOU in one move
- Worker chain safety via `ExistingWorkPolicy.KEEP`: if two workers are somehow already running, the second enqueue is ignored
- `collectAndClearPendingOnly` uses `runCatching { }.getOrNull() ?: emptyList()`: corrupt JSON returns empty harvest rather than crashing; worker slot stays intact for next cycle
- HomeScreen NavCard shows slot-0 recipe name when both slots active (slot-0 preference): simple, correct, noted for future polish

**Anything that diverged from design/design.md:**
- None — all changes are bug fixes, not design decisions

**Coming up:**
- Next session: Producer upgrade system, or feature work
- Near term: Ironroot region move; Undermarch/Mithlost inspiration names
- Future ideas logged: None this session

---

## Session 52 — June 30, 2026
**Ground-up redesign: god document written, Men ingredients finalized, legacy archived**

**What was built:**
- `design/master-design.md`: comprehensive master GDD capturing all redesign decisions
- `legacy/`: all prior design/, implemented/, brainstorm/, retired/ docs moved here — archived, not authoritative
- `CLAUDE.md`: updated to point to master-design.md, reflect new folder structure

**Decisions made:**
- Combat model B: food provides stat boosts only, no HP/s. Keeper is sole healer.
- Universal streak system: Fate×k trigger, 5 ticks, 1.5×, all roles equal. No Fate food ever.
- Captain: Will primary, Fate+Vitality secondary (two secondaries). Hybrid damage. Sole Dread anchor.
- Keeper: two independent HoT slots, triage heal, group heal, rescue burst (5-cap), DPS in all gaps.
- Per-member provisioning: 4 food + 4 draught = 8 slots. Nothing is party-wide.
- Houses of Healing: third craft track, own grimoires, only grievous-wound mechanic. Recovery buff = elevated incoming healing (not damage reduction).
- Shadow renamed Despair. Cold = double-bind (DPS rate + healing rate). Disease = healing + Inspiration odds.
- Regional ingredient separation: forage/hunt location-locked; all plants farmable once acquired; husbandry portable.
- All crafting including prep steps (butter, cheese, etc.) in the kitchen.
- No T1 recipes requiring Lone-Lands ingredients.
- ironroot renamed brackenroot (Bree-land root — name was too Dwarven).
- nettleleaf moved to Bree-land core (grows anywhere; was incorrectly Lone-Lands).
- willowherb moved to Bree-land core (riverbank herb; was incorrectly Wildwood/Cardolan).
- Full Bree-land ingredient roster designed: 46 core + 10 Lone-Lands = 56 total for Men.

**Anything that diverged from prior design:**
- Everything. This is a ground-up redesign. All prior design docs are archived in legacy/.

**Coming up:**
- Next session: Update ingredients.json (rename brackenroot, restructure regions, add cultivatable flag), update recipes.json (4 ironroot→brackenroot swaps), update band_members.json (park Mithlost/Undermarch, focus Men)
- Near term: Combat engine rewrite for Model B (remove HP/s, wire Keeper healing), per-member provisioning slots
- Future: Elves redesign (Mithlost), Dwarves redesign (Undermarch)

---

## Session 51 — June 30, 2026
**Quality system Phases 1–6 complete; starter region encounters added; design decisions locked**

**What was built:**
- `Grade.kt`: five-tier ordinal enum (Crude=0…Pristine=4)
- `QualityUtils.kt`: rollGrade, gradeStep, cookCeiling, resolveDishGrade — placeholder tuning tables marked TODO:TUNE
- `QualityUtilsTest.kt`: 15 unit tests
- DB migration 10→11: composite PKs on inventory_items and prepared_food; existing stock defaults to Crude
- InventoryDao/PreparedFoodDao: grade-aware queries, totalQuantity, deleteIfEmpty
- InventoryRepository: grade-explicit and grade-agnostic overloads; lowest-grade-first consumption
- HarvestItem: grade field (serializable)
- All gather workers (Gathering/Farm/Garden/Hive/Coop/Dairy): roll grade per session via rollGrade(level)
- ProcessWorker: receives and preserves input grade; carries lowest consumed grade to output
- Recipe.kt: heroIngredient field; recipes.json data pass — every recipe assigned a hero
- KitchenViewModel.startCooking: resolves dish grade (hero-weighted avg + cook ceiling) before consuming
- CookingWorker: stores graded PreparedFood
- BandRepository.memberInputsForBand: applies gradeStep to stat boosts at provisioning; HP/s untouched
- PreparedFoodDetail: grade field; InventoryViewModel passes pf.grade through
- food_model.js: mirrored GRADE_NAMES, GRADE_STEPS, effectiveStatBoost
- GradeBadge.kt: shared color-coded composable
- PantryScreen: grade badge on ingredients and prepared food
- KitchenScreen.RecipeDetailPanel: hero marker (★), predicted dish grade + ceiling hint; qty aggregation fixed
- MissionsScreen.FoodRow, BandScreen.FoodPickerDialog: grade badges
- design/ingredient_quality_spec.md, design/starter_region_bosses_spec.md, design/discovery_grimoire_spec.md: filed into repo
- encounters.json: 7 new encounters — Wolf-Master, Rhudaur Men, Barrow-wight (Greycloaks); Dourhand, Drakeling (Undermarch); Large Spider, Huorn (Mithlost)
- starter_region_bosses_spec.md: updated to reflect locked decisions
- parked_topics.md: updated with all new parks and resolutions

**Decisions made:**
- Poison = disease/Hale reskin. No new Venom hazard. Lone-Lands poison question resolved.
- Five-slot encounter structure (two ladders → miniboss → malady fight → region boss + return vault)
- All three bands: first malady = armor (recLv5 goblins). Uniform, simple.
- Miniboss drops XP reward for now. Potency draught stays level-gated (cookLevel 5).
- Expedition mechanic parked — minibosses on encounter list as optional fights for now.
- Drakeling: flat Heat hazard (buildable today), not escalating ramp.
- Rhudaur Men and Huorn: in encounters.json as wall fights; full tricks need V2 engine.

**Anything that diverged from design:**
- Boss spec revised: six slots → five slots; miniboss slot moves to recLv4 (before malady); malady unified to armor for all bands.
- Miniboss tricks for Dourhand and Large Spider left as TBD (endurance placeholder in JSON) — parked.

**Coming up:**
- Next session: Grimoire system implementation (requires basic-vs-grimoire recipe data pass first), or quality tuning with the sim
- Near term: Miniboss tricks for Dourhand + Large Spider; Undermarch/Mithlost return vault creatures
- Future ideas logged: Expedition mechanic (parked in parked_topics.md)

---

## Session 53 — July 1, 2026
**Combat Engine Redesign (Model B) — full implementation across sim + Android**

Both the JavaScript headless sim (`tools/sim/run_sim.js`) and the Android `EncounterEngine.kt` now implement Model B combat. This session executed a 7-task Subagent-Driven Development plan.

**What was built:**
- `tools/sim/food_model.js`: stripped HP/s system; kept stat-boost exports only
- `tools/sim/run_sim.js`: hunter→fighter rename; HP/s food healing removed; Keeper HoT + triage + group heal added; Fate streak (crit mechanic) added alongside existing Inspirations (Horn, Red Dawn, Black Arrow, Laurelin's Grace — all four untouched)
- `app/.../engine/EncounterEngine.kt`: HP/s stripped from MemberInput; Keeper HoT system added (full priority chain: group heal → triage → HoT slots → conditional keeper DPS); Fate streak added (per-member, all active members, 1.5× DPS+heal mult)
- `app/.../data/repository/BandRepository.kt`: `hpsForCookLevel()` and TIER_TABLE removed; hunter→fighter
- `app/.../worker/EncounterWorker.kt`: KEY_HPS_* constants removed
- `app/.../ui/viewmodel/BandViewModel.kt`: `hps = hpsList` argument removed from buildRequest()
- `app/.../ui/screen/BandScreen.kt`: roleAbility() "hunter" → "fighter"
- `app/src/test/.../EncounterEngineTest.kt`: 8 tests covering healing, streak, armor, draught, warden guard
- `app/src/test/.../MemberInputStatTest.kt`: hpsForCookLevel tests removed

**Decisions made:**
- Fate streaks are the crit mechanic and coexist with Inspirations — they do NOT replace Inspirations. Both systems run in the sim simultaneously.
- TRIAGE_COOLDOWN = 2 delivers a 1-tick minimum gap (decrement-before-check). Comment corrected; constant unchanged so sim and Android stay in sync.
- Test parameters differ between sim and Android: the sim has cascade drain, fate evasion, and Inspirations that the Android engine lacks. Android test values were independently calibrated against the new HoT system balance.

**Anything that diverged from design:**
- None. All changes align with master-design.md Model B spec.

**Constants (sim ↔ Android identical):**
HoT: HOT_DURATION=8, HOT_HEAL_MUL=0.15, TRIAGE_HP=0.25, TRIAGE_MUL=2.0, TRIAGE_COOLDOWN=2, GROUP_HEAL_IV=20, GROUP_HEAL_MUL=0.5
Streak: STREAK_K=0.002, STREAK_REFRACTORY=20, STREAK_DURATION=5, STREAK_MULT=1.5

**Coming up:**
- Next session: Run sim against Men encounters (Wolf-Master, Rhudaur Men, Barrow-wight) to validate balance under new HoT+streak system
- Near term: Ingredient/recipe data pass for Men (JSON still has old-era data); Grimoire system
- Future ideas logged: None this session

---

## Session 54 — July 1, 2026
**Ingredient/Recipe Data Pass — Men (Greycloaks)**

Cleaned ingredients.json to match the new master-design.md Men roster. recipes.json required no structural changes (brackenroot was already wired, HP/s fields already stripped).

**What was built/fixed:**
- `ingredients.json`: 83 targeted fixes via Python transform
  - Region "Bree-land & The Shire" → "Bree-land" (all 44 Bree-land core entries)
  - Region "Bree Wildlands / Lone-Lands" → "Lone-Lands" (9 Lone-Lands entries)
  - `deer_haunch_dried` region "Celondim / Ered Luin" → "Lone-Lands"; source/gatheringMode → "process"
  - `river_trout` region "Bree-land / Celondim" → "Bree-land"; source/gatheringMode/gatherType → "fish"
  - `rabbit`, `hog_meat` → source/gatheringMode/gatherType "hunt"
  - `deer_haunch`, `eel` → source/gatheringMode/gatherType "hunt"/"fish"
  - `forest_honey`, `field_honey` → source "husbandry" (gatheringMode/Type were already correct)
  - `hens_egg` → source/gatheringMode "husbandry"
  - `deer_haunch` → added primaryStat/secondaryStat (mig/vit) and notes
  - `river_trout`, `eel` → notes added; `hog_meat` → Shire reference removed from notes
- `recipes.json`: verified clean — no HP/s fields, no ironroot. All 20 Greycloaks recipes resolve to valid ingredient IDs.

**Decisions made:**
- Non-Men ingredient regions untouched (Mithlost "Celondim / Ered Luin", Undermarch "Thorin's Halls") — parked content
- `milk` keeps region "All" — portable husbandry, correct

**Anything that diverged from design:**
- None.

**Coming up:**
- Next session: Run sim against Men encounters for balance validation; Grimoire system implementation
- Near term: Warden stat decisions; recipe tier projection for full campaign
- Future ideas logged: None


---

## Session 55 — July 1, 2026
**Grimoire System — Full Implementation**

Designed and built the complete three-class Grimoire system via SDD (5 tasks, 5 per-task reviews, 1 whole-branch review on Opus). All tasks reviewed clean; branch is READY TO MERGE.

**What was built:**
- `grimoires.json` + `Grimoire.kt`: three grimoire definitions (`cooking_t2`, `draught_t2`, `hoh_t1`); IDs follow `{class}_t{tier}` convention
- `Encounter.kt`: added `grimoireDrops: List<String> = emptyList()` field
- `GameDataRepository.kt`: added `grimoires: List<Grimoire>` lazy property
- `encounters.json`: all six Greycloaks encounters updated — reward tables replaced with valid regional ingredient IDs; Wolf-Master `recLevel` 4→7, `grimoireDrops` `["draught_t2","hoh_t1"]`; Rhudaur Men `recLevel` 6→9, `grimoireDrops` `["cooking_t2"]`
- `PlayerState.kt`: added `foundGrimoireIds: String = ""` with `@ColumnInfo(defaultValue = "")`
- `HearthCraftDatabase.kt`: version 11→12, `AutoMigration(from=11, to=12)` added; schema `12.json` generated and confirmed
- `PlayerRepository.kt`: added `observeFoundGrimoireIds()`, `discoverGrimoire()`, `discoverGrimoires()` following existing comma-split/join pattern
- `EncounterWorker.kt`: on VICTORY, `encounter.grimoireDrops` applied via `player.discoverGrimoires()`, guarded by `isNotEmpty()`
- `KitchenViewModel.kt`: added `isRecipeVisible(recipe, foundGrimoires, discoveredIds)` top-level function; updated `tieredRecipes` (4-source combine) and `bandRecipes` (3-source combine) to use it; added `foundGrimoireIds: StateFlow` and `allGrimoires` properties
- `RecipeBookScreen.kt`: now uses `tieredRecipes` grouped by tier with headers; "Undiscovered" section shows unfound grimoires by name
- `build.gradle.kts`: added `src/main/assets` as test resource srcDir (required for classloader-based JSON tests)
- Tests: `GrimoireDataTest` (4 tests), `PlayerRepositoryGrimoireTest` (5 tests), `EncounterWorkerGrimoireTest` (2 tests), `RecipeAvailabilityTest` (9 tests) — all pass

**Decisions made:**
- Grimoire class `"cooking"` (not `"food"`) for food recipe grimoires: follows design terminology ("Cooking grimoire"). `isRecipeVisible` maps `recipe.recipeClass == "food"` → `"cooking"` for the grimoire lookup. Documented in `design/master-design.md` §8.3.
- HoH grimoire gates ALL HoH tiers (including T1) — design §9.3 specifies Athelas preparation requires a HoH grimoire; this differs from food/draught T1 which are always free.
- `EncounterWorkerGrimoireTest` is intentionally vacuous (identity function test) because the worker requires Android context for integration testing. Known limitation, logged for follow-up.
- `isRecipeVisible` honors `discoveredIds` for any recipe class, not draught-only as the design states. Harmless today (nothing populates food/HoH discovery outside T1-free path) — minor permissiveness that can be tightened if needed.

**Anything that diverged from design/master-design.md:**
- `food→cooking` class mapping added to §8.3 as an implementation note.
- No other divergences.

**Coming up:**
- Next session: Add `grievousWoundSpecs` to existing Greycloaks encounters; run balance sim against Men encounters
- Near term: HoH UI (wound state display, preparation application); Lone-Lands unlock trigger; player title decision
- Future ideas logged: None this session

---

## Session 27 — July 2, 2026
**HoH Full Mechanics Design and Implementation**

**What was built:**
- Design session: full HoH mechanics spec (`docs/superpowers/specs/2026-07-02-hoh-mechanics-design.md`) — five wound types, guaranteed+chance infliction model, combined timer with time-credit, recipe structure, athelas design, ingredient list, XP system, recovery buff
- `BandMemberState.kt`: added `woundTypes`, `hohTimerStartMs`, `hohTimerDurationMs`, `recoveryBuffGrade`, `recoveryBuffTier`, `recoveryBuffPending` fields
- `PlayerState.kt`: added `hohLevel`, `hohXp` fields
- `HohSession.kt` + `HohSessionDao.kt`: new entity tracking active HoH treatments per member
- `Migration14To15.kt`: manual Room migration 13→15, all new columns and `hoh_sessions` table
- `HearthCraftDatabase.kt`: bumped to version 15, registered `HohSession`, added `hohSessionDao()`
- `DatabaseModule.kt`: added `provideHohSessionDao`, registered `Migration14To15`
- `PlayerRepository.kt`: added `Track.HOH`, HOH XP curve (A=22, P=1.2, wall=2.0), `addHohXp()`, `XP_HOH_*` constants
- `Recipe.kt`: added `hohLevel`, `treatsWoundTypes` fields
- `Encounter.kt`: added `GrievousWoundSpec` data class, `grievousWoundSpecs` field
- `EncounterEngine.kt`: added `rollWoundTypes()`, `buildGrievousWoundMap()`, `grievousWoundTypes` in `EncounterResult`, `recoveryBuffMult` in `MemberInput`, buff multiplier at all four heal sites, `recoveryBuffMultiplier()` helper
- `BandRepository.kt`: extended `woundMember()` to accept wound types, added `completeHohRecovery()`, `consumeRecoveryBuffs()`, `HohSessionDao` injection
- `EncounterWorker.kt`: passes `grievousWoundSpecs` to engine, propagates `grievousWoundTypes` to `applyOutcome` on both primary and fallback paths
- `HohRepository.kt`: new — `applyPreparation()` with elapsed time-credit, `calcTimer()`, WorkManager enqueue for `HohWorker`
- `HohWorker.kt`: new — fires on recovery completion, calls `completeHohRecovery`, posts notification with channel registration
- `HohCookingWorker.kt`: new — fires on HoH craft completion, grants XP, stores preparation in inventory
- `ingredients.json`: added 15 new ingredients including 7 HoH-exclusives (`silver_thread_lichen`, `bloodmoss`, `healing_clay`, `rendered_beeswax`, `shadowbane_moss`, `sandflower`, `miruvor_dilute`)
- `recipes.json`: added 20 HoH recipes T1–T4

**Decisions made:**
- Five wound types: Physical (4h floor), Will (3h), Corruption (6h), Poison (4h), Disease (5h)
- Wound infliction: each encounter has `guaranteed` and `chance` wound specs — always produces at least one type
- Recovery timer only starts on treatment; partial treatment credits elapsed time on subsequent applications
- Compound recipes more efficient than separate T1 applications
- Athelas cultivar unlocks when first T3 recipe is crafted (TODO: implement trigger)
- `sandflower` added as Lone-Lands rare forage ingredient for Corruption healing (existing `starflower` is a cooking ingredient — kept separate)
- Grade ceiling fix: `CookQuality.resolveDishGrade` accepts `overrideUnlockLevel` param; HoH passes `recipe.hohLevel` not `recipe.cookLevel`

**Anything that diverged from design/master-design.md:**
- None — spec was written first this session, implementation follows it exactly

**Coming up:**
- Next session: Add `grievousWoundSpecs` to Greycloaks encounters; run balance sim; begin HoH UI
- Near term: HoH UI screens; Lone-Lands unlock trigger; athelas cultivar unlock trigger implementation
- Future ideas logged: None
