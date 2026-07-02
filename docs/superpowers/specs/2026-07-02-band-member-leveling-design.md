# Band Member Leveling System — Design Spec

**Date:** 2026-07-02
**Status:** Approved, ready for planning

## Problem

While investigating "how does the Neekerbreekers fight look in the new sim," it became clear the headless sim (`tools/sim/run_sim.js`) and the real Kotlin game have quietly diverged on the most basic question: how does a band member get stronger?

The sim still implements a formal leveling curve (`stat_at_level = base + grow_rate × (level − 1)`, levels 1-20), documented in `legacy/implemented/mechanics-math-reference.md`. This predates the June 30 "ground-up redesign." When that redesign landed (Model B combat, smaller starting stat scale), band member progression in the real Kotlin code was replaced with something much simpler: `BandRepository.grantMissionStats()` grants a flat +1 Vitality (and +1 Might on a win) to every alive member after every mission — no level concept at all. `design/master-design.md` §4 only says stats "grow over time," with no defined curve. Nobody carried the old leveling system forward into the new data model.

This means: the sim (used for balance decisions) and the real game (what players experience) are not measuring the same thing. Any encounter-difficulty conclusion drawn from the current sim is unreliable until this is fixed. Restoring a real leveling system is a prerequisite for the sim rewire, not part of it — hence this separate spec.

## Decisions

### 1. Scope of this spec

This spec covers restoring band-member combat leveling in the **design doc and Kotlin game** only. Rewiring `tools/sim/run_sim.js` / `hearthcraft_fight_sim.html` to read from this same system is a separate, follow-on spec — the sim rewire depends on this existing first.

### 2. Data model

`BandMemberState` gains one new field: `combatXp: Int = 0` (per-member, matches the existing per-member wound/state tracking already on this entity).

The five stat columns currently on `BandMemberState` (`might`, `agility`, `vitality`, `will`, `fate`) are **removed**. They were storing directly-mutated flat-grant values under the old system; going forward, a member's combat stats are **computed**, not stored — derived from `(role, level)` at the point they're needed (mirrors how food/grade bonuses are already layered on top of a base value in `BandRepository.memberInputsForBand`, rather than stored pre-combined).

Removing columns requires a proper `Migration` class (not `AutoMigration`, which only handles additive changes) — this project already has a precedent for this (`Migration14To15.kt`, from the HoH work merged in earlier today). The new migration both adds `combatXp` and rebuilds the table without the five stat columns.

### 3. Growth rates — per role, not per individual member

One growth curve per role (Warden/Fighter/Keeper/Captain), shared across all three peoples' bands — not per named individual. This matches the legacy sim's `TPL` structure and keeps the number of hand-tuned constants small (4 roles × 5 stats = 20 numbers, not 12+ individuals × 5 stats).

New data file: `app/src/main/assets/data/growth_curves.json` — one entry per role, each with `migGrowth`/`agiGrowth`/`vitGrowth`/`wilGrowth`/`fatGrowth` (the per-level growth rate for that stat). The `role` key is lowercase (`"warden"`/`"fighter"/`"keeper"`/`"captain"`), matching the casing `BandRepository.memberInputsForBand` already normalizes to via `member.role.lowercase()` — no new casing convention introduced. Loaded via a new `GrowthCurve` data class and a `GameDataRepository.growthCurves: List<GrowthCurve> by lazy { load(...) }` property, following the existing pattern used for `recipes`/`ingredients`/`encounters`/`grimoires`.

A member's stat at their current level is:
```
statAtLevel = member.startingX (from band_members.json, unchanged) + growthCurveForRole.xGrowth × (level − 1)
```
This reuses the existing per-individual starting stats already authored in `band_members.json` (e.g., Hollis the Warden: Mig 4) as the level-1 base, and layers the shared per-role growth rate on top — so named members keep their distinct starting-stat identity, while growth *rate* is shared per role.

**Rescaled growth rate values** (proportionally rescaled from the legacy formula — legacy rates were calibrated against ~13-15-range starting stats; today's real starting stats are ~2-5. Each rate below is `legacy_rate × (real_base / legacy_base)`, preserving the legacy curve's *shape* — which stat grows fastest for which role — while fitting today's actual numbers):

| Role | Growth/level: Mig | Agi | Vit | Wil | Fat |
|---|---|---|---|---|---|
| Warden | +0.15 | +0.05 | +0.18 | +0.09 | +0.05 |
| Fighter | +0.10 | +0.18 | +0.09 | +0.12 | +0.16 |
| Keeper | +0.06 | +0.09 | +0.10 | +0.18 | +0.19 |
| Captain | +0.10 | +0.09 | +0.17 | +0.14 | +0.07 |

These are a computed starting point, explicitly **not** final-balanced numbers — validating them against real fights is exactly what the follow-on sim rewire is for.

### 4. Fractional stats, whole-number display

Computed stats stay `Float` internally (feeding directly into `MemberInput` and combat math, matching how grade bonuses already work — e.g. Pristine grade is already a +5.5 bonus). Wherever a stat is shown in the UI (`MemberDetailDialog`'s `StatBar`, etc.), it's rounded to a whole number for display — the same treatment morale already gets (`round(30 + vit × 16)`).

### 5. Level cap and XP curve

Level cap: **50** (matches Cooking's existing cap, not the legacy system's 20 — this needs to span the full campaign, which is much longer than what the legacy 1-20 range was designed against).

XP curve shape: same style as the existing Cooking/Gathering curves (`base = A × level^P`), using Gathering's constants as a placeholder (`A=20, P=1.35`) — combat leveling advances through repeated missions across the whole campaign, the same shape of progression as Gathering, not through Cooking's discrete tiers. Like the growth rates, this is an explicit placeholder to validate via the sim rewire, not a locked balance decision.

Implementation: a small set of functions mirroring `PlayerRepository.Companion`'s existing `xpToNext`/`levelForTotalXp`/precomputed-threshold-array pattern, but scoped to combat leveling (XP lives on `BandMemberState.combatXp`, not `PlayerState`, so this can't reuse `PlayerRepository.Track` directly — it needs its own small equivalent, likely living in `BandRepository`'s companion object, the natural home since `BandRepository` already owns `BandMemberState` mutations).

### 6. XP sources — mirrors the existing "no reward on defeat" mission pattern

Per the roadmap's already-established pattern (§2A: Stalemate = partial reward, Defeat = no reward), combat XP follows the same shape:

| Outcome | XP granted (per member sent) |
|---|---|
| Victory | `XP_COMBAT_WIN = 40` |
| Stalemate | `XP_COMBAT_STALEMATE = 15` |
| Defeat | `0` |

This replaces `BandRepository.grantMissionStats(bandId, succeeded: Boolean)` (the flat +1 vit/+1 mig grant) with an XP-granting equivalent, called from `EncounterWorker`'s existing VICTORY/STALEMATE/DEFEAT branches. All members sent on the encounter receive the same XP (bands are currently always sent as a complete unit — no partial roster selection exists yet).

### 7. Consuming the computed stats

`BandRepository.memberInputsForBand()` currently reads `state?.might ?: member.startingMight` etc. directly from stored columns. This changes to: derive the member's current level from `state?.combatXp ?: 0`, then compute each stat via the formula in §3, with food/grade bonuses layered on top exactly as today (unchanged).

`BandViewModel.members` (producing `BandMemberWithState` for the UI) needs the same treatment, plus a new `level: Int` field on `BandMemberWithState` so the UI can display it (e.g. in `MemberDetailDialog`, next to the role label).

## Design doc updates required

`design/master-design.md` §4 ("Stats") currently just says stats "grow over time" with no defined curve. Per this project's convention (code and docs must agree), this needs a concrete addition documenting: the per-role growth-rate model (§3 above), that growth rates are shared per role rather than per individual member, the level cap (50), and that combat leveling is driven by mission XP (§5-6 above) rather than flat per-mission stat grants. This will be done as part of implementation, alongside the code changes.

## Out of scope (explicitly deferred)

- Rewiring `tools/sim/run_sim.js`/`hearthcraft_fight_sim.html` to read `growth_curves.json` and real encounter/recipe data — separate follow-on spec, unblocked once this ships.
- Actually retuning Neekerbreekers (or any other encounter) — that requires the rewired sim to validate against, not this spec.
- Any change to the HoH leveling track (`hohXp`/`hohLevel`, merged in from the concurrent HoH mechanics work) — entirely separate system, untouched here.
- Any change to how food/draught stat bonuses are computed or layered — unchanged, just now layered on top of a computed rather than stored base.

## Testing

- Pure functions (`statAtLevel`, the combat XP curve functions) are directly unit-testable without Android context, following the pattern already established this session (`resolveWoundOutcome`/`WoundResolutionTest`).
- Migration correctness: schema export + `./gradlew build`, following the pattern already used for the two migrations added earlier today/this week (12→13, 14→15).
- `BandRepository.memberInputsForBand` and `BandViewModel.members`'s derived-stat logic: verify via existing build conventions (no ViewModel/Repository-with-Context tests exist in this codebase — matches established precedent, not a new gap).
