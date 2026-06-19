# HearthCraft — Development Journal

> **Current Status** is the living summary at the top — update it every session.
> Session entries below are append-only.

---

## Current Status — June 19, 2026

**Phase:** V1 core loop complete. Combat model designed and tooled. Both Rung 0 encounters fully validated. Full doc audit complete. Kingswake rename done. Sim now shows per-character DPS and damage mitigation charts.
**V1 progress:** Core loop playable. Combat system designed in full (not yet built — V2+ destination). Two V1 encounters validated and locked. All docs synced.
**What's working:** Full V1 loop. Combat simulator with per-character DPS meter (live + results) and damage mitigated vs. magic bypass chart. Both validated encounter JSONs correct. Four bands synced.
**What's not wired yet:** Full combat system (V2+). missions.json → encounters.json rename. Encounter Builder food-level selector. 5th combat role not yet designed. Progression ladder not yet formally designed. Crafting/gathering mechanics redesign pending.
**Next session:** Design the crafting and gathering mechanics (redesign pass). Then: Battlegrounds design, Ettenmoors design, legendary item system.
**Open questions:** 5th role design. Rung-3 first-hazard fork (Cold vs Wakefulness). XP design for cooking. Formal encounter rung progression ladder. Crafting/gathering redesign scope.

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
