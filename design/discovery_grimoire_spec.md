# Discovery & Grimoire System — Design Spec

**Date:** 2026-06-29
**Status:** Approved design — implementation spec to follow once boss/grimoire
map is complete (see §7).
**Supersedes:** `implemented/recipe-discovery-design.md` (the experiment-based
discovery system) — that system is being **removed**, see §2.

---

## 1. Summary

Recipe access moves from **experimentation** (combine ingredients, get
warmer/colder feedback, unlock by trial) to **grimoires** (recipe books unlocked
as campaign rewards). The cooking *action* does not change — only **how recipes
enter the player's known set** changes.

This sits alongside the **Ingredient Quality** system (`ingredient_quality_spec.md`)
as a second progression axis:

- **Quality** = *how well* you cook (sourcing + grade + cook ceiling).
- **Grimoires** = *what* you can cook (recipe breadth, gated by campaign).

Two clean axes that do not overlap.

---

## 2. What Is Removed

The shipped experiment/discovery system comes out. It is well-isolated, so this
is mostly deletion:

- `engine/RecipeDiscoveryEngine.kt` — the proximity engine. **Remove.**
- KitchenViewModel experiment state: `experimentIngredients`, `experimentMethod`,
  `liveResult`, `lastExperimentResult`, `canCommit`, `evaluateLive()`,
  `commitDiscovery()`, `addExperimentIngredient()` etc. — **remove.**
- The **Discover** tab in `KitchenScreen.kt` and its UI. **Remove.** (Kitchen
  goes from 3 tabs to 2: Recipes, Process.)
- Food-structure hint card + `hasSeenFoodStructureHints` / `hasSeenExperimentHint`
  flags — **remove** (or leave the columns dead; do not migrate-drop unless clean).

**Critically — cut auto-discover-on-level-up.** `CookingWorker` (≈ lines 47–61)
and `KitchenViewModel` auto-discover recipes when cook level rises. If recipes
keep auto-unlocking by level, **the grimoire gate is meaningless.** This must be
removed or repointed. See §4 for the replacement access rule.

> **Do NOT remove** the starter-seed logic (band starter recipes on first launch).
> That stays — see §4.

---

## 3. Three Gate-Types

All gates share one safety net: **a sufficiently high band level beats anything by
brute force.** Every gate is therefore "optional" — the gate is the *elegant /
efficient* path; grinding band level through repeat encounters is the *slow* path
around it. This is the universal escape valve; there is never a hard deadlock.

| Gate-type | Location | Drops | Cardinality |
|---|---|---|---|
| **Region boss** | Region *exit* | The **next region's** toolkit grimoire | One per region |
| **Miniboss** | *Mid*-region | A **varied** reward (often NOT a grimoire) | Zero or more per region |
| **Return vault** | Early-visible, late-beatable | A **rare/special** reward (planted, see §6) | Zero or more |

### 3.1 Region boss
- Sits at the region exit. Defeating it gates progress to the next region.
- Drops the grimoire containing **what the next region demands** — capability
  arrives *just before* the content that needs it, never as a key to a far-off
  door.
- Has a single self-contained **trick** (see §5 trickie vocabulary).

### 3.2 Miniboss
- A self-contained **"tricky-tricky"** fight: a sharp gimmick, **less raw power**
  than a region boss. NOT curriculum — it does not teach for a *future* fight; it
  is a clever puzzle solved *for itself*.
- Reward is **varied** and chosen by the content timeline: XP dump, money,
  ingredient cache, OR a grimoire — but **a grimoire only when a genuine new
  capability is due.** When the player already has the tools (e.g. a region that
  is "just T1 food"), the reward is **progression fuel, not new tools.**

### 3.3 Return vault
- Early-visible but **deliberately un-passable on first encounter** — its maladies
  are ahead of where the player is when they first arrive. The player retreats and
  returns later, over-equipped, for a **big reward**.
- Off the critical path. Never blocks progression.
- Pairs with the **planted-reward** pattern (§6).

---

## 4. Recipe Access Rule (the replacement)

A recipe is **known** if **any** of:

1. It is a **starter recipe** for the player's band (seeded on first launch —
   existing logic, unchanged), **OR**
2. The player **owns the grimoire** that contains it, **OR**
3. It is a **basic recipe gated by cook level only** (see §4.1).

This replaces the old `(starter) OR (experimented) OR (cookLevel ≥ unlock)`.
**Note (3) is a deliberate carve-out, not the old auto-discover** — see below.

### 4.1 Basics gate by level; advancement gates by grimoire

Two tiers of recipe:

- **Basic recipes** (T1 stat food, the entry-level potency/antidote draughts like
  `sloe_bitters`) are **cook-level-gated**: they become available automatically
  when the player reaches the recipe's `cookLevel`. **No grimoire required.** This
  is what keeps the early game playable — e.g. the recLv5 armored fight is
  answerable by the cook-level-5 potency draught with no book.
- **Advancement recipes** (T2+ food, regional/advanced draughts, apex recipes) are
  **grimoire-gated**: known only if the player owns the book. Cook level may still
  apply as a *secondary* gate (you must be high enough level to *make* it even
  after you own the book), but the book is what unlocks *knowing it exists*.

> **Implementation:** add a `gating` field (or equivalent) to each recipe:
> `"level"` (basic, auto-unlock at cookLevel) or `"grimoire"` (needs a book).
> Default existing T1 food + entry draughts to `"level"`; default T2+ to
> `"grimoire"`. The exact split is a **data pass for Wes** — flag ambiguous cases.

### 4.2 Grimoire ownership data

- New `PlayerState` field: `ownedGrimoireIds: String` (comma-separated, mirroring
  the existing `discoveredRecipeIds` pattern). Migration adds the column,
  default `""`.
- A `grimoires.json` asset: each grimoire has `id`, `name`, `recipeIds[]` (what it
  unlocks), `source` (which boss/vault drops it), and `flavor`.
- Known-recipe resolution = starter set ∪ (recipes from owned grimoires) ∪
  (level-gated basics at/under cookLevel). Compute in `PlayerRepository`, mirror
  the existing `observeDiscoveredIds()` shape so the Kitchen UI changes minimally.

---

## 5. Trickie Vocabulary (boss/miniboss gimmicks)

The menu of distinct provisioning puzzles. A fight picks one (region bosses) or a
light one (minibosses). **Each demands a different kind of provisioning answer**
so fights don't collapse into one lookup table.

| Trickie | Pressure | Answer | Engine status |
|---|---|---|---|
| **Hazard exam** | Imposes a hazard (Dread/Cold/etc.) | Matching counter draught | Buildable today (hazard fields exist) |
| **The race** | Armored + fat resolve + hard time cap | Potency draught + stat food (DPS) | Buildable today |
| **Fragility / spike trap** | Hard random-target spikes | **Even** Vitality across whole party (raise HP floor everywhere) | Buildable today |
| **Endurance grind** | Huge resolve + relentless flat drain, no spikes | High HP/s sustain (Tier-axis food) | Buildable today |
| **Two-front** | Two pressures at once (e.g. Dread + time cap) | Combo draught OR a real provisioning sacrifice | Needs draught potency as a number, not a string flag |
| **Escalator** | Pressure *changes/ramps* partway through | Anticipation + broad prep; kill before the ramp matures | **Needs new engine behavior** (time-varying stages) |
| **Self-heal race** | Enemy drains band to refill its own resolve | Out-damage the heal AND deny the drain | **Needs new engine behavior** (resolve can rise) |

> **Engine-dependency flag:** the current encounter model appears to run **flat,
> single-stage** stats and **monotonically-decreasing** resolve. Trickies marked
> "needs new engine behavior" (escalator, self-heal race) and the numeric-potency
> two-front are **design-ready but not implementation-ready** until the V2 engine
> gains: (a) time-varying stage pressure, (b) enemy self-heal / resolve increase,
> (c) draught potency as a numeric value. Bosses using these are **build-after-
> engine**. Bosses using only the top four trickies are buildable on today's
> engine.

---

## 6. Planted Rewards

A reusable pattern: **an item granted early, inert until a later condition unlocks
it.**

- First instance: the **Barrow-wight's forgotten grimoire**. On the player's first
  (failed) Barrow-downs visit, an inert/locked grimoire is silently added to
  inventory ("a mouldered book grabbed in the dark"). It does nothing and is shown
  as unreadable/locked. When the player **later defeats** the wight (after gaining
  Cold + Dread counters), the book "wakes up" — it becomes a normal owned grimoire.
- **Implementation shape:** grimoires can have a `locked: Boolean` state in the
  player's owned set, or a separate "dormant items" concept. Defeating the gating
  boss flips locked→unlocked rather than granting a fresh drop. Keep it simple;
  this is one instance for now, but design the field so the pattern generalizes.

---

## 7. Implementation Sequencing (NOT this build)

This doc is **design**. The code spec to *execute* the removal+grimoire system
should be written **after** the boss/grimoire map is complete enough to know what
each grimoire contains. The code is plumbing for the design; writing it before the
map exists means guessing at grimoire contents.

Prerequisite before code spec:
- Each starter-region boss's grimoire **contents** are pinned (currently parked —
  see `starter_region_bosses_spec.md` §Parked).
- The **basic vs grimoire** recipe split data pass (§4.1) is done.

When ready, the code spec covers: remove experiment system (§2), cut auto-discover
(§2), add `grimoires.json` + `ownedGrimoireIds` + access rule (§4), planted-reward
field (§6), and the boss→grimoire drop wiring (encounter/mission completion grants
a grimoire).

---

## 8. Parked

- **Grimoire contents** for all three starter-region bosses — pending each band's
  *second* region design.
- **Lone-Lands poison** — new hazard (Venom/Antivenom — gives region bosses a real
  unlock to drop) vs disease/Hale reskin (cheap, but Hale already exists). Gates
  what the Rhudaur men (and the elf spider miniboss) actually require.
- **Return-vault creatures** for Undermarch and Mithlost (Greycloaks has the
  Barrow-wight).
- **Engine work** to enable escalator / self-heal / numeric-potency trickies.
