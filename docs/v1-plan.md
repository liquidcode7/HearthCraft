# HearthCraft — V1 Plan

> This is the source of truth for what is in V1.
> Do not implement anything not listed here without explicit approval.
> Deferred ideas go in `docs/wishlist.md`, not here.

---

## V1 Goal

Ship a playable loop. The point of V1 is not a complete game — it is a
testable answer to one question: **does gather → cook → provision band →
send on mission → collect rewards feel good to play?**

Everything in V1 exists to answer that question. Nothing else.

---

## What V1 Includes

### Band Selection
- Player chooses one of four bands at first launch: The Mithlost,
  The Undermarch, The Freewake, The Greycloaks
- Choice is permanent, flavor only, no mechanical difference
- Each band has a name, a short description, and a regional flavor
- Band choice determines mission names and ingredient flavor text

### Band Members
- 3–4 named members per band, hand-written
- Each member has: a name, a personality line or two, one mechanical
  quirk (a preferred buff type or food preference)
- Members are displayed on the band screen with their current status
- Members can be lost on a badly failed mission (outright failure
  with provisioning significantly below threshold)
- Member loss is permanent in V1 — no replacement mechanic yet

### Gathering Skill
- One Gathering skill with a visible XP bar and level
- Two modes: Farm/Garden and Forage/Wild
- Player picks a mode and starts a session — sessions run in background
  via WorkManager
- Session completes after a set duration, returns a batch of ingredients
- Farm/Garden returns cultivated ingredients (predictable pool)
- Forage/Wild returns uncultivated ingredients (randomized pool,
  occasionally surprising)
- Both modes contribute to the same Gathering XP
- Starting ingredient pool: 10–12 hand-picked ingredients across both
  modes, appropriate to the chosen band's region
- No rare or elite ingredients in V1 — those are V2

### Cooking Skill
- One Cooking skill with a visible XP bar and level
- Player selects a known recipe and available ingredients → starts a
  cooking session
- Session runs in background via WorkManager, completes after set duration
- Completing a recipe produces a food item and grants Cooking XP
- Gathering sessions also grant a small amount of Cooking XP
- Starting recipe set: 8–10 hand-coded recipes
- Recipes have a buff type (endurance, agility, focus, warmth, luck)
  and a buff strength value
- No recipe discovery in V1 — all recipes are hand-coded and visible
  in the recipe book from the start
- Recipe book screen shows all known recipes with ingredients and
  buff values

### Missions
- 3 mission types per band: Easy, Medium, Hard
- Missions unlock by vitality threshold — highest alive member vitality
  must meet the minimum (easy: 0, medium: 3, hard: 6)
- Each mission has: a name, a flavor description, a vitality requirement,
  a required buff strength (affects success odds), a duration, and a reward table
- Player feeds the band (selects a prepared food item) then sends them
- Mission runs in background via WorkManager
- Missions are difficult when first unlocked but always attemptable
- On return: success or outright failure — no partial outcomes
- Success returns: money + 1–3 ingredient drops from the mission's
  reward table
- Failure returns: nothing. Possible member wound or loss on hard failures
- Player always sees the vitality requirement before confirming

### Inventory and Pantry
- Simple inventory: ingredients with quantities, prepared food items
  with quantities, current money total
- No freshness or decay in V1
- No storage limits in V1

### Notifications
- Notification fires when a gathering session completes
- Notification fires when a cooking session completes
- Notification fires when a mission returns
- Tapping a notification opens the relevant screen

### Screens (minimum viable)
- **Home / Dashboard** — current session status at a glance, quick
  links to each area
- **Gathering** — choose Farm/Garden or Forage/Wild, start session,
  see current session progress and recent returns
- **Kitchen** — select recipe, confirm ingredients, start cooking
  session, see current session progress
- **Recipe Book** — list of all known recipes with ingredients and
  buff values
- **Pantry / Inventory** — current ingredient quantities, prepared
  food items, money
- **Band** — named members with status, current provisioning, send
  on mission, mission history
- **Mission Board** — available missions with requirements and rewards

---

## What V1 Does NOT Include

If any of these come up during V1 development, add to `docs/wishlist.md`
and continue.

- Alchemy — full V2 system
- Recipe discovery — V1 uses hand-coded recipes only
- Rare or elite ingredient tiers from gathering
- Potions or unusual mission types
- Additional bands beyond the starting four
- Band member replacement after loss
- Band member storylines or relationship arcs
- Kitchen upgrades
- Pantry freshness or decay
- Inventory limits or storage management
- Training missions for band members
- Money spent on anything (collected but not yet spent in V1)
- Multiple simultaneous crafting sessions
- Crafting queues

---

## Build Phases

Complete these in order. Build and verify between each phase.
One logical change per commit. Never commit a broken build.

---

### Phase 0 — Project Setup
- [ ] Add CLAUDE.md to repo root
- [ ] Add docs/ folder with design.md, v1-plan.md, wishlist.md
- [ ] Add .gitignore entries for local.properties and build outputs
  if not already present
- [ ] Verify `./gradlew build` passes on clean project
- [ ] Commit: `[v1] Add project docs and verify clean build`

### Phase 1 — Dependencies and Architecture Scaffold
- [ ] Add dependencies to build.gradle.kts: Room, Hilt, WorkManager,
  kotlinx.serialization, Material 3
- [ ] Set up Hilt application class
- [ ] Set up Room database class (empty, no entities yet)
- [ ] Verify build passes
- [ ] Commit: `[v1] Add dependencies and Hilt/Room scaffold`

### Phase 2 — Game Data (JSON)
- [ ] Create `app/src/main/assets/data/` directory
- [ ] Write `bands.json` — four bands with names, descriptions,
  regional flavor
- [ ] Write `band_members.json` — 3–4 members per band with name,
  personality, buff preference
- [ ] Write `ingredients.json` — 10–12 ingredients with name, type
  (cultivated/wild), gathering mode, band flavor
- [ ] Write `recipes.json` — 8–10 recipes with name, required
  ingredients, buff type, buff strength, cooking duration
- [ ] Write `missions.json` — 3 missions per band (Easy/Medium/Hard)
  with name, description, required buff type, required buff strength,
  duration, reward table
- [ ] No Kotlin changes yet — data only
- [ ] Verify build passes
- [ ] Commit: `[v1] Add game data JSON files`

### Phase 3 — Data Models and Room Entities
- [ ] Create data classes for: Band, BandMember, Ingredient,
  Recipe, Mission
- [ ] Create Room entities for: PlayerState, Inventory,
  GatheringSession, CookingSession, MissionSession, BandMemberState
- [ ] Create DAOs for each entity
- [ ] Wire entities into Room database class
- [ ] Create repository classes for each domain area
- [ ] Write JSON deserializers for game data files
- [ ] Verify build passes
- [ ] Commit: `[v1] Add data models, Room entities, and repositories`

### Phase 4 — WorkManager Sessions
- [ ] Implement GatheringWorker — runs a gathering session for set
  duration, returns ingredient results on completion
- [ ] Implement CookingWorker — runs a cooking session for set
  duration, returns food item on completion
- [ ] Implement MissionWorker — runs a mission for set duration,
  evaluates buff threshold, returns results on completion
- [ ] Set up notification channel
- [ ] Fire completion notifications for each worker type
- [ ] Verify sessions start, run in background, and complete correctly
  on device
- [ ] Commit: `[v1] Add WorkManager session workers and notifications`

### Phase 5 — ViewModels
- [ ] GatheringViewModel — session state, mode selection, ingredient
  history
- [ ] KitchenViewModel — recipe selection, ingredient availability,
  session state
- [ ] BandViewModel — member state, provisioning, mission selection,
  mission history
- [ ] InventoryViewModel — ingredient quantities, food items, money
- [ ] Shared PlayerViewModel — overall player state, skill levels,
  XP progress
- [ ] Verify build passes
- [ ] Commit: `[v1] Add ViewModels`

### Phase 6 — Band Selection Screen
- [ ] First-launch screen: display four band options with name and
  flavor description
- [ ] Player selects one, choice persisted to Room
- [ ] Never shown again after first launch
- [ ] Verify on device
- [ ] Commit: `[v1] Add band selection screen`

### Phase 7 — Core Screens
Build screens in this order — each one is a separate commit:

- [ ] Home/Dashboard — session status overview, navigation
- [ ] Gathering screen — mode picker, start session, progress,
  recent returns
- [ ] Kitchen screen — recipe picker, ingredient check, start session,
  progress
- [ ] Recipe Book screen — list of recipes with details
- [ ] Pantry/Inventory screen — ingredients, food items, money
- [ ] Band screen — member list with status and quirks, provisioning,
  send on mission
- [ ] Mission Board screen — available missions with requirements
  and current buff value displayed

Each screen: `[v1] Add [screen name] screen`

### Phase 8 — Wire the Loop
- [ ] Gathering session results write ingredients to inventory
- [ ] Cooking session consumes ingredients, writes food item to
  inventory, grants XP
- [ ] Feeding band sets active buff type and strength
- [ ] Mission evaluates buff vs threshold, writes results to inventory
  and money, handles member loss if applicable
- [ ] XP gains update skill levels
- [ ] Verify full loop works end to end on device: gather → cook →
  feed → mission → rewards → repeat
- [ ] Commit: `[v1] Wire complete core loop`

### Phase 9 — Polish and Stability
- [ ] Handle edge cases: session already running, insufficient
  ingredients, all members lost
- [ ] Verify notifications fire and navigate correctly on tap
- [ ] Check for crashes on fresh install
- [ ] Verify app survives being backgrounded and killed during active
  sessions
- [ ] Commit: `[v1] Edge case handling and stability`

### Phase 10 — V1 Complete
- [ ] Install on device and play for at least one week
- [ ] Note what feels good, what feels missing, what is confusing
- [ ] Fill `docs/wishlist.md` with observations
- [ ] Decide V2 priorities based on what playing actually felt like

---

## V1 Success Criteria

V1 is done when:
- The full loop runs without crashing: gather → cook → provision →
  mission → rewards → repeat
- Sessions run correctly in the background
- Notifications fire on completion
- Band member loss feels consequential but not punishing
- You have played it for a week and have opinions about what V2 should be
