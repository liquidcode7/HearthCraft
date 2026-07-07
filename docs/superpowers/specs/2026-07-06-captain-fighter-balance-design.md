# Captain/Fighter DPS Balance & Captain HoT — Design Spec

**Status:** Approved by Wes, 06 Jul 2026. Follows on from `2026-07-05-damage-types-design.md`,
which established the physical/magical split mechanic and Captain's stat-weighted mitigation
split — this spec revises the coefficients within that same mechanic, and separately fixes an
unrelated symmetry bug found in Fighter's formula while investigating.

## Why

Real playtest observation (05 Jul 2026 audit): Keeper is doing second-most DPS overall and,
in armored encounters, the most — because Keeper's damage is pure magical (never
armor-mitigated) while Warden/Fighter/part-of-Captain's damage is. Wes's read: Keeper
out-damaging Captain isn't the actual problem (Keeper and Captain being "about equal" is
fine) — the real problem is Captain's own coefficients are simply too low. Confirmed by
pulling live numbers from `tools/sim/run_sim.js`'s stat tables (`statAt(tpl, k, lvl) =
start[k] + grow[k] * (lvl-1)`) at level 5:

| Role | Formula (current) | Output |
|---|---|---|
| Warden | `might × 1.5` | 22.5 |
| Keeper | `will × 2.7` (when not healing) | 46.4 |
| Fighter (Ranged build) | `agility × 3 + might × 1.2` (same formula, both builds) | 63.8 |
| Fighter (Melee build) | `agility × 3 + might × 1.2` (same formula, both builds) | 51.2 |
| **Captain** | `might × 0.9 + will × 0.6` | **16.8** — below Warden, the actual floor |

While deriving target numbers, a second, independent issue surfaced: Fighter's two builds
(`master-design.md` §5.4 — Ranged: Agility primary/Fate secondary; Melee: Might
primary/Fate secondary, both "Pure DPS," meant to be equivalent flavor choices) have exact
mirror-image stat blocks, but the shared formula weights agility (×3) far above might (×1.2).
Since Ranged's primary stat gets the strong coefficient and Melee's primary stat gets the
weak one, Melee is structurally ~20% behind Ranged (51.2 vs 63.8 at level 5) — an accident of
the coefficients never being made symmetric when the two-build system was added, not a
deliberate ranged > melee design choice.

## Section A: Captain damage buff

**Change:** `rawDps("captain")` becomes `might × CAPTAIN_MIGHT_COEF + will × CAPTAIN_WILL_COEF`,
with `CAPTAIN_MIGHT_COEF = CAPTAIN_WILL_COEF = 2.0f` (was `0.9f` / `0.6f`).

At level 5: `2.0 × (8.8 + 14.8) = 47.2` — lands almost exactly even with Keeper (46.4),
clearly above Warden (22.5), below Fighter's new shared output (~64, Section C). Matches
Wes's stated target: Captain and Keeper "about equal," Captain not the brightest but no
longer the floor.

Both coefficients are named constants (not inline literals), consistent with this file's
existing `TODO:TUNE` convention (`HOT_HEAL_MUL`, `TRIAGE_MUL`, etc.) — these are placeholder
values pending balance-harness validation like everything else in this system, not locked
final numbers.

## Section B: Captain's `physicalFraction` — updated to match, plus new "spec" lever

**Current:** `physicalFraction("captain")` computes `physTerm = might × 0.9f`, `magicTerm =
will × 0.6f`, ratio = `physTerm / (physTerm + magicTerm)` — hardcoded literals duplicating
Section A's old coefficients.

**Change:** Use the same `CAPTAIN_MIGHT_COEF`/`CAPTAIN_WILL_COEF` constants from Section A
(not new literals) so the two never drift independently. Because both coefficients are now
equal, the ratio simplifies to `might / (might + will)` — a purely stat-driven split with no
fixed lean.

This gives Wes the "spec toward armor-piercing" lever he asked for: a Captain build with
more Will shifts a larger share of their damage into the magical (armor-bypassing) portion,
useful against armored encounters; a might-heavy build stays mostly physical. A perfectly
balanced build sits at 50/50 regardless of fight type, matching "when facing unarmored, it
doesn't matter."

## Section C: Fighter symmetry fix

**Change:** `rawDps("fighter")` becomes `might × FIGHTER_COEF + agility × FIGHTER_COEF`, with
`FIGHTER_COEF = 2.33f` (was `agility × 3f + might × 1.2f`).

Because the two builds' stat blocks are exact mirrors of each other, any symmetric
(equal-coefficient) formula produces identical total output for both builds automatically —
no build-detection logic needed. `FIGHTER_COEF = 2.33` is calibrated to match the current
Ranged build's output (63.8 at level 5) — Wes's call: treat the higher existing number as
the one that was actually tuned, buff Melee up to meet it rather than nerf Ranged down.

Fighter's `physicalFraction` is unaffected by this change — both builds remain 100% physical
either way (`"warden", "fighter" -> 1f`), this section only touches `rawDps`.

## Section D: Captain's new heal — burst-on-cooldown (revised 07 Jul 2026)

**Status:** This section supersedes the original "Captain HoT" design (shipped in commit
`ee49fc7`, then replaced before Task 5 mirrored it into the sim tools). Wes's original ask
("a little HoT he can apply on the lowest ally") was implemented as a trickle — `will × 0.08`
per tick for 6 ticks, retargeting every 9 ticks. In review, that shape turned out to be
mathematically real but perceptually inert: no single tick is ever big enough to notice, so
it read as ambient number-go-up rather than something the Captain visibly did. Wes's actual
goal, stated directly: the heal should not compete with Keeper's identity as the party's real
healer, but should still have flavor — a legible, meaningful moment, not invisible background
healing.

**New mechanic:** Captain periodically lands one instant, visible burst heal on whoever is
currently the lowest-HP standing ally, on a fixed cooldown — structurally a smaller sibling of
Keeper's Triage (`will × TRIAGE_MUL`, fires reactively below a health threshold), except
Captain's version fires automatically on cooldown regardless of need (Wes's call — simpler
and more predictable than a reactive-threshold trigger).

| Constant | Value | Rationale |
|---|---|---|
| `CAPTAIN_HEAL_INTERVAL` | 12 ticks | Cooldown between casts. Noticeably less frequent than Keeper's group-heal (5 ticks) or reactive triage — stays clearly secondary to Keeper's kit. |
| `CAPTAIN_HEAL_MUL` | 1.0f | Half of Keeper's `TRIAGE_MUL` (2.0) — `healAmount = captain.will × CAPTAIN_HEAL_MUL`. A real, visible chunk of HP, clearly smaller than Keeper's signature heal. |

**Behavior:**
- Fires on a fixed cooldown (decrementing counter, same pattern as `groupHealTimer`),
  independent of Captain's DPS/inspiration actions — automatic, not conditioned on anyone
  actually being low (Wes's explicit choice over a reactive Triage-style threshold).
- Targets whoever is lowest-HP-fraction among `standing()` at the moment it fires (same
  target-selection Keeper's own kit uses: `standing().minByOrNull { it.hp / it.maxHp }`).
- Scales off Will alone, consistent with the original design's reasoning (legible as "the Will
  half of Captain," distinct from the might/will blend driving Captain's damage).
- **Free** — does not consume Captain's DPS action that tick, same precedent as before.
- **One-shot, not lingering** — this is a single `applyHeal(...)` call, not a `HoT` slot with a
  multi-tick duration. No `captainHot`/`captainHotTimer` HoT-slot state; just a cooldown
  counter and, when it fires, an immediate heal.
- **UI: a flash, not the HoT badge.** Since there's no lingering effect, this does *not* join
  `TickSnapshot.hotTargets` (that field's "currently under an ongoing heal-over-time" meaning
  no longer applies here, and reverts to being Keeper-only again). Instead it follows the
  existing Black Arrow / Grace precedent — one-shot events that get a few ticks of artificial
  visual "just fired" flash so they're not imperceptible at playback speed
  (`BLACK_ARROW_FLASH_TICKS`/`GRACE_FLASH_TICKS`, both 5 ticks). Add a matching
  `captainHealFlash: Boolean` field to `TickSnapshot`, using the same flash-duration constant
  style.

## Explicitly out of scope

- **Encounter difficulty re-tuning** in response to any of these changes — same reasoning as
  the damage-types spec: implement the mechanic/tuning correctly now, revisit encounter
  armor/resolve values in a later dedicated balance pass once the sim reflects these numbers.
- **Keeper's healing AI** (thresholds, cooldown removal, escalating full-rez chance) — a
  separate, larger discussion Wes raised in the same session; deliberately not bundled into
  this pass. This spec's Section A change (Captain no longer the DPS floor) may on its own
  resolve part of what motivated that discussion — worth re-assessing after this ships and is
  played, before deciding whether the larger AI rework is still needed.
- **Elf/Dwarf Fighter builds** — `master-design.md` §5.4 notes these are pending redesign;
  not addressed here.

## Testing

- Unit tests in `EncounterEngineTest.kt`: assert `rawDps`/`physicalFraction` for Captain at
  fixed stats match the new symmetric formula; assert Fighter's two builds (mirror-image
  stat blocks) now produce identical `rawDps` output.
- Unit test for the Captain HoT: fixed seed, assert it fires on the expected tick interval,
  targets the correct (lowest-HP) member, heals the expected amount, and does not reduce that
  tick's damage output versus an otherwise-identical fight with the HoT disabled.
- Run the full existing `EncounterEngineTest.kt` suite — confirm no regression in win-rate
  threshold tests (these buffs should, if anything, make win-rate thresholds easier to clear).
- Mirror every constant and formula change into `tools/sim/run_sim.js` in the same commit (or
  immediately after), per that file's own "must match run_sim.js exactly" comment — this is
  the same drift risk Task 4 of the kitchen/grimoire/forage plan is independently fixing for
  the food-grade formula, don't reintroduce the same class of bug here.
