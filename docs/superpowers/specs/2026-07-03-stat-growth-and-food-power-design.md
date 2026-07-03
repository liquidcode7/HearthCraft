# Stat Growth & Food Power Design

**Status:** Draft — mechanisms locked, numeric values are first-pass placeholders
pending calibration via the rebuilt combat sim (a separate, already-planned
follow-on project). Do not treat any number in this doc as final; treat every
*relationship* (compound growth, difficulty-scaled anchors, grade curve shape)
as the actual decision.

## 1. Problem

The current combat system has two things wrong with it at once:

1. **Stat growth per level is flat-linear** (`statAtLevel = start + growth ×
   (level-1)`, in `growth_curves.json` / `CombatLeveling.kt`). This produces
   only modest separation between a level-1 and a level-50 character, which
   doesn't match the desired feel of a level-50 veteran being dramatically
   more powerful than a fresh recruit.
2. **Food's role in combat outcomes isn't tied to anything.** There's no
   defined relationship between an encounter's difficulty, the tier/grade of
   food a band eats going in, and the likelihood of winning. The DPS/HP
   formula coefficients in `EncounterEngine.kt` are also known to be stale
   (calibrated for a legacy, larger stat scale) — see the combat system audit
   from the prior session.

The design goal, in the player's own words: level gives you a baseline that
keeps you in the right ballpark for on-level content, but **food is the real
lever** — going into a fight with no food should be a clear loss, going in
with tier-appropriate Crude food should be a real "probably not, but close"
coin-flip-leaning-loss (not a crushing wall), and going in with tier-
appropriate Pristine food should be a near-guaranteed win — except for
deliberately hard boss fights, which are the one place even Pristine doesn't
save you.

## 2. Level-Based Stat Growth

Replace flat-linear growth with **compound (percentage-per-level) growth**:

```
statAtLevel = startingStat × (1 + growthRate) ^ (level - 1)
```

This is how most RPGs with meaningful level progression handle it (Pokémon,
JRPG stat curves, WoW's underlying per-level stat budget) — each level
multiplies the previous level's stat, so the level-1-to-50 gap compounds into
something dramatic, while low levels stay small, legible numbers.

**Why keep it modest relative to food:** food (§3) is the design's primary
difficulty lever, not level. If both level growth *and* food bonuses are each
independently dramatic multipliers, they compound into numbers nobody can
reason about. Level growth should be the reliable, unglamorous baseline; food
is where the real swing lives.

**Placeholder rates** (per stat, replacing the current per-stat linear rates
in `growth_curves.json`):

| Stat role on this class | Compound rate/level | L1→L50 multiple (start 5) |
|---|---|---|
| Primary stat | 3.5% | 5 → ~27 (~5.4x) |
| Secondary stat | 2.0% | 5 → ~13.4 (~2.7x) |
| Off-stat (no growth emphasis) | 1.0% | 5 → ~8.2 (~1.6x) |

**Captain's two secondaries** (Fate + Vitality, per master-design.md §5.1):
recommend splitting the secondary budget so Captain's total elevated growth
matches every other role's — each of Captain's two secondaries grows at
**1.0%/level** (half of the standard 2.0% single-secondary rate) rather than
each getting the full secondary rate, which would make Captain snowball past
every other role by endgame. Flagged for your confirmation since it wasn't
explicitly re-confirmed after the compound-growth pivot.

Cap remains level 50 (`COMBAT_MAX_LEVEL`, unchanged).

## 3. Food Grade Curve (Reshape, Not Replace)

Keep the existing 5-grade axis (Crude → Common → Fine → Superb → Pristine) —
it's wired end-to-end (DB, UI, gather/cook/combat) and shouldn't be torn out.
Instead, reshape the *multiplier curve* applied to a recipe's stat-boost
magnitude so the bottom is compressed (soft penalty) and the top spreads out
(real reward for chasing quality):

| Grade | Multiplier (placeholder) |
|---|---|
| Crude | 0.7× |
| Common | 0.85× |
| Fine | 1.0× (baseline — "tier-appropriate" reference point) |
| Superb | 1.3× |
| Pristine | 1.7× |

Crude and Common are only 0.15 apart (a new player eating whatever they can
scrounge isn't crushed); Fine → Pristine spans 0.7 (chasing quality
meaningfully pays off).

## 4. Difficulty-Scaled Win-Rate Anchors

`encounters.json`'s existing `difficulty` field currently has 3 values
(easy/medium/hard) with no documented relationship to food grade. Add a 4th
value, **`boss`**, for deliberately hard set-piece fights — the one category
where even Pristine tier-appropriate food doesn't guarantee a win.

Each difficulty tier gets its own expected win-rate curve across grades (all
values below are placeholder targets for the sim to hit, not hand-tuned
final numbers):

| Difficulty | No food | Crude | Common | Fine | Superb | Pristine |
|---|---|---|---|---|---|---|
| Easy | ~20% | ~70% | ~85% | ~95% | ~98% | ~99% |
| Medium | ~10% | ~45% | ~65% | ~85% | ~95% | ~98% |
| Hard | ~5% | ~20% | ~40% | ~65% | ~85% | ~95% |
| Boss | ~2% | ~10% | ~20% | ~40% | ~65% | ~85% |

This resolves the concrete problem that started this discussion: the game's
first encounter (`greycloaks_neekerbreekers`, difficulty `easy`, recLevel 1)
is safely winnable on Common (~85%) and merely risky on Crude (~70%) — a new
player is never hard-blocked by lack of access to rare ingredients. The
"Crude ≈ 0%, Pristine ≈ 99%" swing that already exists as a tuning comment in
`QualityUtils.kt` (for a recLevel-6 encounter) becomes the `hard`/`boss` row,
not a universal rule applied to every encounter regardless of difficulty.

**DPS/HP formula coefficients** (`EncounterEngine.kt`'s `rawDps()`,
`morale()`, etc.): these are explicitly **not** being hand-picked in this
design. Once the sim rewrite exists, it will be used to reverse-engineer
coefficient values that make simulated win rates match the table above, for
each difficulty tier. Until then, existing coefficients are left as-is
(already known to be miscalibrated, per the prior audit) — implementation of
the leveling and grade mechanisms below does not depend on the coefficients
being fixed first.

## 5. Gathering Aid Item (Grade Acquisition Agency)

**Problem this solves:** ingredient grade is currently a passive weighted
random roll tied to `gatheringLevel` (`QualityUtils.kt`'s `rollGrade()`) —
slow, un-directed, no player choice. There's an existing but never-wired
`aidUplift` parameter threaded through every gathering worker
(`GatheringWorker`, `FarmWorker`, `GardenWorker`, `CoopWorker`, `DairyWorker`,
`HiveWorker`) — clearly planned but never built.

**Mechanic:** a single-use, player-crafted consumable (name TBD — flavor is
your call) that the player crafts via the existing recipe/cooking system and
activates to temporarily boost gather-grade odds.

- **Crafted, not purchased.** Recipe-only acquisition — reuses the existing
  cooking/recipe/tier system rather than building a parallel gold-shop
  economy. Placeholder: unlocks at Cooking level 5+, a Tier 2 recipe (meant
  to be a genuine mid-game crafting investment, not trivial).
- **Duration-based, not per-session.** Gathering happens via background
  WorkManager workers on a schedule — there's no discrete "session" the
  player consciously enters. Activating the aid starts a timer; every gather
  roll (`rollGrade()` call) that occurs while the timer is active receives
  the `aidUplift` bonus. Placeholder duration: **4 hours**.
- **Weekly cap, stacked with crafting difficulty.** Both scarcity levers
  apply simultaneously (your call, over the alternative of the cap replacing
  crafting cost) — the aid is meant to be a precious, reserved-for-important-
  fights resource, not a routine buff. Placeholder: **3 uses per rolling
  7-day window**, tracked independently of how many the player has crafted
  and stockpiled.

This directly answers "how does a player work toward high-quality
ingredients without being cock-blocked": passive `gatheringLevel` growth
raises the baseline odds over time (unchanged), and the aid gives an active,
strategic lever for the moments that matter most — "I need Pristine food
before this specific fight, let me spend one of my weekly uses now."

## 6. What This Doc Does Not Decide

- Exact final numeric values anywhere above (growth rates, grade
  multipliers, win-rate targets, aid duration/cap/recipe cost) — all
  placeholders pending the sim rewrite.
- `EncounterEngine.kt`'s DPS/HP formula coefficients — deferred to sim-based
  reverse-engineering against §4's anchor table.
- The aid item's name, flavor, and exact recipe ingredients.
- Whether `recLevel`-gating for encounter unlock (currently compared against
  a band's max Vitality, per `EncounterDetail.isUnlocked`) should shift to
  compare against band member combat *level* instead, now that real per-
  member leveling exists. Noted here as a related implementation question,
  not resolved by this doc.
