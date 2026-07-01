# Wound Recovery & HoH Softlock Fix — Design Spec

**Date:** 2026-07-01
**Status:** Approved, ready for planning

## Problem

Two related problems surfaced this session:

1. **Wound treatment is dead code today.** `BandViewModel.treatWound()` and the
   "Treat Wounds" card on `BandScreen` gate healing on
   `food.buffType == "healing" || "healing_deep"`. But `Recipe.buffType`
   (`Recipe.kt:34-40`) can only ever resolve to `"might"/"agility"/"vitality"/"will"`
   or the recipe's class — never `"healing"`/`"healing_deep"`. This is leftover
   from before the ingredient/recipe redesign. No wound — ordinary or grievous —
   can currently be treated by any means.
2. **Grievous wounds can permanently soft-lock a band.** Per design §9.1, a
   grievous wound "cannot be walked off or outcooked" — it requires a Houses of
   Healing preparation. But zero recipes currently have `class: "hoh"`
   (`athelas_infusion` in `recipes.json` is misclassified as `"draught"`, tier 5).
   A band that takes a grievous wound before HoH content exists has no path to
   recovery at all.

## Decisions

### 1. Wound severity thresholds (post-battle, DEFEAT only)

`EncounterWorker.applyWounds()` currently maps down-count → severity:
`>=5 → death`, `>=3 → grievous`, `>=1 → wounded`.

New mapping — **death-by-wounds is removed entirely** from this path:

| Down-count | Severity |
|---|---|
| 1-2 | wounded (light) |
| 3-4 | wounded (heavy) |
| 5+ | grievously_wounded — *subject to the HoH-availability check below* |

Permadeath is not part of this mechanic. If it's designed later (per roadmap
Phase 2B's backup-member system), it'll be its own separate trigger.

The in-fight behavior where a member is pulled from active combat after 5
downs (`EncounterEngine`'s `GRIEVOUS = 5` constant) already matches this and
needs no change.

### 2. Ordinary wound recovery — time-only, automatic

No player action, no food/prep involved. Two duration bands:

| Severity | Recovery time |
|---|---|
| Light (1-2 wounds) | 1 hour |
| Heavy (3-4 wounds) | 2 hours |

Recovery is driven by a new `WoundRecoveryWorker` (same pattern as
`HiveWorker`/`CookingWorker`): scheduled with `setInitialDelay` at the moment
a wound is applied, unique work keyed per member (`ExistingWorkPolicy.REPLACE`
— a member wounded again before recovering restarts the clock on the new,
worse injury). On firing, it clears the wound via `BandRepository.healWound()`
and posts a notification ("<Member> is back on their feet.").

**Testing note:** durations live as named constants (mirroring `DURATION_HIVE_MS`
etc.). For Wes's on-device testing right now, all three bands (light, heavy,
safety-net) are set to **15 seconds** instead of their target values. The
target values (1hr / 2hr / 6hr, see below) are the intended production numbers
once the sim rebalance pass sets real encounter difficulty — revisit before
that work locks in final numbers.

### 3. The HoH-availability softlock safety net

Before persisting a 5+ down-count as `grievously_wounded`, check whether the
player currently has **any visible/craftable recipe with `class == "hoh"`**.

This check is deliberately based on "does a usable HoH recipe exist for this
player" — not merely "has an HoH grimoire been found." Today there are zero
`hoh`-class recipes in the data at all, so this check is always false
regardless of grimoire state, and the safety net stays active exactly as long
as it needs to (self-resolving once real HoH recipes ship later — no second
fix required).

- **HoH available:** 5+ wounds persists as genuine `grievously_wounded`.
  Per §9.1 this does **not** auto-heal via time — it strictly requires HoH
  treatment. That treatment mechanism is out of scope for this spec (see
  "Out of scope" below) and remains a known gap until HoH recipes exist.
- **HoH not available:** the result is capped at `wounded` (not grievous),
  using the **safety-net duration: 6 hours** — longer than either normal
  ordinary-wound band, but still finite and automatic. The post-battle
  notification text explains the near-collapse, e.g.:
  *"Mission Failed — \<encounter\> — the band was nearly overwhelmed. Without
  a healer's true craft, they pulled back to recover. It will be a long
  recovery."*

Implementation: `isRecipeVisible()` (currently a top-level function in
`KitchenViewModel.kt:40-45`) moves to `GameDataRepository.kt` so it can be
reused from `EncounterWorker` without a Worker depending on the `ui.viewmodel`
package. `KitchenViewModel` updates its call site accordingly — no behavior
change there. `PlayerRepository` gains one-shot suspend getters
`getDiscoveredRecipeIds()` and `getFoundGrimoireIds()` (mirroring the existing
`getDiscoveredIngredientIds()` pattern) for `EncounterWorker` to use, since a
`Worker.doWork()` needs a single read, not a `Flow`.

### 4. Data model changes

`BandMemberState` gains one field: `woundedDurationMs: Long = 0L` (paired with
the existing `woundedSinceMs`). Room schema bump 12 → 13,
`AutoMigration(from = 12, to = 13)` (matches the pattern used for the v11→v12
Grimoire migration last session).

`BandRepository.woundMember(memberId, grievous, durationMs)` gains the
duration parameter and persists it. `BandMemberStateDao.healWound()` additionally
zeroes `woundedDurationMs` on clear.

### 5. Recovery countdown UI

`BandScreen` gets a countdown for wounded members, reusing the existing
`ActiveTimerRow` pattern used for cooking/growing timers — computed from
`woundedSinceMs + woundedDurationMs`. Replaces the deleted "Treat Wounds" card
in the member list.

### 6. Dead code removal

- `BandViewModel.treatWound()` — deleted.
- `BandScreen.kt` lines ~132-167 (the "Treat Wounds" card, `healingFood`
  filtering, `applicableFood` logic) — deleted, replaced by the recovery
  countdown from item 5.
- `athelas_infusion` recipe entry — deleted from `recipes.json` entirely.
  Wes wants a dedicated brainstorming session for the real HoH recipe roster
  later; this recipe's stats (draught, tier 5, cookLevel 12) don't match the
  design intent for "first HoH recipe" (§9.3) and shouldn't be left around
  half-migrated.

## Out of scope (explicitly deferred)

- Actual HoH treatment mechanics (how a real `hoh`-class recipe clears a
  grievous wound, the "recovery buff" from §9.1) — needs its own design
  session per Wes's request.
- Any change to the Keeper's in-combat healing (§6, unrelated system).
- The post-battle stats/report UI (separate work item from the same session's
  notes, not part of this spec).
- Sim rebuild/rebalance (separate work item).

## Design doc updates required

`design/master-design.md` §9 (Houses of Healing) needs a short addition
documenting: the new severity thresholds (no death-by-wounds), the ordinary
wound time-only auto-heal rule and durations, and the HoH-availability safety
net behavior. This will be done as part of implementation, keeping code and
docs in sync per CLAUDE.md.

## Testing

- Unit tests for `isRecipeVisible`'s new home in `GameDataRepository`
  (existing `RecipeAvailabilityTest` should still pass unmodified, just import
  from the new location).
- New tests for severity mapping (1-2 / 3-4 / 5+ boundaries, no death branch).
- New tests for the HoH-availability check (false when no hoh recipes exist;
  true once a visible hoh recipe is added to test fixtures).
- `WoundRecoveryWorker` — likely a thin worker like `EncounterWorkerGrimoireTest`
  (needs Android context), so a full integration test may not be practical;
  follow the same documented-limitation pattern used there if so.
