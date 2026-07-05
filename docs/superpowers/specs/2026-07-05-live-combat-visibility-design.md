# Live Combat Visibility — Design Spec

**Status:** Approved by Wes, 05 Jul 2026. Third sub-project of the 04 Jul 2026 playtest
audit follow-up (see `docs/superpowers/plans/2026-07-04-audit-quick-fixes.md` for the
full 7-group sequencing this belongs to).

## Why

Two related audit items (#5, #9) both pointed at the same gap: the in-progress fight
screen (`BattleInProgressCard`, `MissionsScreen.kt`) shows only a boss resolve bar and
per-member HP bars — no damage/heal numbers, no indication of what's driving them, and
nothing about the rare inspiration effects firing mid-fight. All of that information
already exists post-fight, in the `CombatReportCard` recap (DPS/heal bars, wound recap),
but only after the fight ends. Both audit items were explicitly blocked on the
damage-types engine work (Group 2, `docs/superpowers/specs/2026-07-05-damage-types-design.md`)
landing first, since "colored by damage type" requires a real physical/magical split to
exist — it now does.

This spec covers making that same live information visible *during* the fight, as the
existing tick-by-tick replay plays out in real time, plus surfacing buff/inspiration
state that currently has no UI at all.

---

## Section A: Engine & data model changes

### `TickSnapshot` (`engine/TickSnapshot.kt`)

New fields, all with defaults (safe decode of any in-flight `EncounterTicks` row written
before this change, since that table is transient per-fight state):

```kotlin
data class TickSnapshot(
    val tick: Int,
    val bossResolve: Float,
    val memberHp: Map<String, Float>,
    val cumDamage: Map<String, Float> = emptyMap(),   // running total as of this tick
    val cumHeal: Map<String, Float> = emptyMap(),     // running total as of this tick (keeper only, non-zero)
    val memberReserve: Map<String, Float> = emptyMap(),  // current shield/reserve amount
    val streakActive: Set<String> = emptySet(),       // member ids currently in a streak window
    val hotTargets: Set<String> = emptySet(),         // member ids currently receiving a Keeper HoT
    val keeperHealing: Boolean = false,               // true if Keeper's last-resolved action this tick was healing, not DPS
    val hornActive: Boolean = false,                  // Horn of Gondor window active
    val dawnActive: Boolean = false,                  // Red Dawn window active
    val blackArrowFlash: Boolean = false,             // short artificial "just fired" pulse (one-shot effect, no natural window)
    val graceFlash: Boolean = false                   // same, for Grace
)
```

`keeperHealing` caveat: on the rare tick where a snapshot is captured *before* the
Keeper-action block executes that tick (i.e. the fight ends earlier in the same tick,
at one of the early victory-check return sites), this defaults to `false`. That's moot —
the fight is already over on that snapshot, so the flag isn't read by anything.

`blackArrowFlash`/`graceFlash`: Black Arrow and Grace are one-shot effects with no
ongoing buff window (unlike Horn/Dawn), so a real-time UI would only see them as a
single-tick blip — often imperceptible at normal playback speed. Both get an artificial
flash counter (`BLACK_ARROW_FLASH_TICKS = 5`, `GRACE_FLASH_TICKS = 5`), set on fire and
decremented every tick like the existing `hornWindow`/`dawnWindow` pattern, so the UI
badge is visible for a few real ticks instead of one.

### `EncounterEngine.resolve()`

The physical/magical split used for coloring is **static for the whole fight** — armor
(`effArmor`) and member stats don't change mid-encounter, so the split doesn't need to be
computed per tick. Once, right after `effArmor` is established:

```
postMitPhysFraction(member) =
    physFrac*(1-effArmor) / (physFrac*(1-effArmor) + (1-physFrac))
```

This is the *output* ratio (post-mitigation), not the raw stat-driven `physicalFraction()` —
it answers "of the damage that actually landed, how much was physical vs magical,"
which is what a live bar should represent. Returned once as a new `EncounterResult` field:
`physFractionByMember: Map<String, Float>`.

**Refactor bundled into this change (not scope creep — required to add these fields safely):**
`resolve()` currently has 6 near-identical `EncounterResult(...)` construction sites (one
per early-return branch: 3× victory, defeat, stalemate-on-timeout, stalemate-on-survival-clock)
and 4 near-identical `TickSnapshot(...)` construction sites. Every field added to either
data class up to now has had to be threaded through all of them by hand — already a
drift risk, and this change adds several more fields to both. Both get consolidated into
one local closure each (`buildSnapshot(t, bossVal)`, `buildResult(outcome, endedAtSec, ...)`)
that reads current mutable state, used at every call site.

---

## Section B: DB schema / persistence

`EncounterTicks` (`data/db/EncounterTicks.kt`) gets one new column:

```kotlin
val memberPhysFractionJson: String = ""   // "memberId:fraction,..." — static per fight, same convention as memberMaxHpJson
```

Additive migration, following the existing `Migration19To20` pattern:

```kotlin
val Migration20To21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE encounter_ticks ADD COLUMN memberPhysFractionJson TEXT NOT NULL DEFAULT ''")
    }
}
```

`HearthCraftDatabase.kt`: bump `version = 21`, register `Migration20To21`.

`BandViewModel.kt` (~line 316-324, alongside the existing `memberMaxHpEncoded` build):

```kotlin
val memberPhysFractionEncoded = result.physFractionByMember.entries.joinToString(",") { "${it.key}:${it.value}" }
```
...passed into the new `EncounterTicks` field.

No changes to `CombatReport`/`combat_reports` — that table backs the post-fight recap
(Group 1's territory), untouched here.

---

## Section C: UI — `BattleInProgressCard` (`ui/screen/MissionsScreen.kt`)

**Per-member row (existing HP row, extended):**
- HP bar: unchanged colors/logic.
- Shield indicator: a thin colored outline drawn around the HP bar when
  `memberReserve[id] > 0` — reads as "armor around the health bar" rather than a
  separate element, since reserve mechanically *is* a shield.
- Wound count (`↓$wounds`): unchanged.
- New small text-badge row beneath the name: `"STREAK"` when the member is in
  `streakActive`, `"HOT"` when in `hotTargets`. Plain colored `Text`, matching this
  file's existing convention (no emoji anywhere in this codebase's UI — confirmed by
  grep before choosing this — so all new indicators are short colored text labels, same
  register as the existing `"↓$wounds"`).

**Inspiration banner (new, top of card, rendered only when non-empty):** a row of small
pill-shaped text chips — `"HORN OF GONDOR"` while `hornActive`, `"RED DAWN"` while
`dawnActive`, `"BLACK ARROW"` while `blackArrowFlash`, `"GRACE"` while `graceFlash` —
appearing and disappearing live as the tick replay advances.

**New "Live Damage & Healing" section, below the existing HP bars:** a new
`TypedDamageBar(totalFraction, physFraction, modifier)` composable, built on the same
two-`Box` pattern as the existing `HpBar`, filling proportionally with two adjacent
colors:
- **Physical = orange** (`0xFFFF9800`) — same hex already used for "heavy wound" text
  elsewhere in this file, but a visually distinct region of the card, so no ambiguity.
- **Magical = blue** (`0xFF2196F3`).

Per-member fill fraction is normalized against the current max cumulative damage among
party members *as of the current tick* (same normalization the post-fight `maxDamage`
calc already does, just recomputed every tick instead of once). Warden/Fighter render
solid orange (`physFraction = 1.0`), Keeper's DPS ticks render solid blue
(`physFraction = 0.0`), Captain's bar visibly splits according to
`physFractionByMember["captain_id"]`.

Keeper additionally gets a plain green heal-bar row sourced from `cumHeal` — healing
isn't typed (only damage has a physical/magical distinction per the damage-types spec),
so no split coloring applies there.

---

## Section D: Testing

- **`EncounterEngineTest`**: assert `physFractionByMember` matches the expected
  post-mitigation ratio for fixed stats/`effArmor` — Warden/Fighter → `1.0`, Keeper →
  `0.0`, Captain → the stat-weighted value strictly between the two.
- A test on the consolidated `buildSnapshot`/`buildResult` helpers — assert the final
  `TickSnapshot.cumDamage`/`cumHeal` equal the final `EncounterResult.damageByMember`/
  `healingByMember` (the running accumulators must never diverge from the totals).
- Migration test for `Migration20To21`, matching the existing `Migration19To20` test
  pattern (schema round-trip, default value on upgrade).
- Manual verification: run a mission, watch the in-progress card — bars grow live and
  settle at the same totals the post-fight recap independently reports; shield outline
  appears/disappears with reserve; inspiration banners appear only during real
  Horn/Dawn windows and briefly flash for Black Arrow/Grace; Captain's bar visibly
  splits orange/blue while Warden/Fighter/Keeper stay solid.

## Explicitly out of scope (deferred to later groups)

- **Named magic-type flavor** (Light/Westernesse) — still inert per the damage-types
  spec's own deferral; this pass colors by generic physical/magical only.
- **Enemy-side typed damage, bane multipliers** — no weapons exist yet (gear system
  group, last in the 7-group sequence).
- **Any post-fight recap (`CombatReportCard`) changes** — that's Group 1's territory,
  already shipped; this spec is the in-progress view only.
- **Per-member streak *rate* or magnitude display** — only on/off membership in
  `streakActive` is shown, not the numeric multiplier, to keep the per-member row compact.
