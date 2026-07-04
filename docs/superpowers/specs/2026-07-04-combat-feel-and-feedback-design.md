# Combat Feel & Feedback — Design Spec

**Status:** Approved by Wes, 04 Jul 2026. First sub-project of the 04 Jul 2026 playtest
audit follow-up (see `docs/superpowers/plans/2026-07-04-audit-quick-fixes.md` for the
full 7-group sequencing this belongs to).

## Why

Three related complaints from the audit, all about combat feeling flat or opaque:
- The pre-baked mission timer telegraphs a loss before it happens
- Wounds have no visible consequence or countdown anywhere in the UI
- Winning a fight gives no feedback about what was actually won

None of these need new mechanics — the underlying data mostly already exists. This is a
UI-surfacing and data-correction pass, not new game design, with one small schema
addition (Section C).

---

## Section A: Remove the countdown timer on kill-fights

**Current behavior:** `BattleInProgressCard` (`ui/screen/MissionsScreen.kt`) shows a
prominent "MM:SS / remaining" countdown for every mission, regardless of fight type. Since
`EncounterEngine.resolve()` computes the entire fight synchronously the instant the
mission starts, an underprepared band's HP bars are already guaranteed to hit zero —
watching an exact countdown to that guaranteed loss removes any sense of uncertainty.

**Fix:** Encounters already carry an `objective` field (`"kill" | "survive" | "retrieve"`,
`data/model/Encounter.kt`). Hide the countdown text (`formatMissionMs(remainingMs)` +
"remaining" label, `MissionsScreen.kt` ~line 493-497) when `objective == "kill"`. Keep it
unchanged for `"survive"`/`"retrieve"` — those fight shapes are explicitly about racing a
clock, so the countdown is the point there.

**Explicitly not changing:** the HP-bar tick-by-tick animation, `EncounterEngine.resolve()`
itself, any skip/fast-forward control. Wes confirmed: the fight should "look as normal" —
this is purely about removing the one numeric readout that gives away the outcome early.

---

## Section B: Make wound severity and recovery visible

**Current behavior:** `BandMemberState` already tracks `woundStatus`, `woundedSinceMs`,
`woundedDurationMs` per member, and `HouseOfHealingScreen.kt`'s "Recovering" section
already lists every wounded member (light/heavy/grievous alike) with a "Wounded" or
"Grievous" label. But:
- Light/heavy ("Wounded") members are incorrectly offered HoH treatment options
  ("Treat anyway" buttons) alongside grievous members — contradicting
  master-design.md §6.8 ("time only, no food or prep involved" for light/heavy)
- No countdown or explanation is shown anywhere for light/heavy wounds
- The actual recovery durations (in both `EncounterWorker.kt` and the design doc) are
  wrong per Wes's correction this session

**Corrected durations** (§6.8 table + `EncounterWorker.kt` constants):

| Down-count | Severity | Recovery (was) | Recovery (corrected) |
|---|---|---|---|
| 1-2 | Wounded (light) | 1 hour | **18 minutes** |
| 3-4 | Wounded (heavy) | 2 hours | **30 minutes** |
| 5+, no HoH recipe available yet (safety net) | Wounded (heavy), extended | 6 hours | **2 hours** — kept deliberately harsh as a real penalty for under-provisioning/poor band care, not a nudge toward HoH specifically (starting areas don't have any HoH recipe available at all, so this is the only consequence a new player faces for this outcome) |
| 5+, HoH recipe available | Grievously wounded | — | unchanged, no auto-heal, HoH-only (§9.1) |

**Full picture — wound types and compounding (grievous only, no change, reproduced here for
completeness):** Light and heavy wounds are flat-duration, time-only — they never roll a
wound type (§6.8: "time only, no food or prep involved"). Only grievous wounds (5+ downs,
HoH available) roll one or more of five wound types, each with its own base floor duration,
and multiple types on the same member sum into a single combined timer. This is entirely
pre-existing design from `docs/superpowers/specs/2026-07-02-hoh-mechanics-design.md` §2 —
not a new mechanic, included here so this spec is a complete reference for wound recovery
across all three severities.

| Wound type | Typical sources | Base floor duration |
|---|---|---|
| Physical | Any combat grievous wound | 4h |
| Will | Barrow-wights, wraith-adjacent enemies, Old Forest | 3h |
| Corruption | Cargul, Nazgûl, Morgul weapons | 6h |
| Poison | Orcs, spiders, venomous creatures | 4h |
| Disease/Wasting | Dark places, cursed wounds, lingering illness | 5h |

Stacking example — combined (uncured) floor durations when multiple types resolve on the
same grievous wound:

| Wound types present | Combined floor (uncured) |
|---|---|
| Physical only | 4h |
| Physical + Will | 7h |
| Physical + Corruption | 10h |
| Physical + Poison | 8h |
| Physical + Disease | 9h |
| Physical + Will + Corruption | 13h |
| Physical + Corruption + Poison | 14h |
| Physical + Corruption + Disease | 15h |

HoH treatment reduces this combined timer based on preparation grade and whether it's a
single-type or compound recipe (full grade-to-timer tables and the elapsed-time-credit rule
for partial treatment are in the HoH mechanics spec, not repeated here). None of this
changes as part of this spec — Section B's actual work is only the light/heavy duration
fix and the HoH-tab countdown display above.

**UI fix (`HouseOfHealingScreen.kt`):**
- For `woundStatus == "wounded"` (light or heavy): remove the treat-options block entirely;
  show a live countdown instead, computed from `woundedSinceMs + woundedDurationMs - now`
  (e.g. "Wounded — resting, back in 12m"). No crafting or treatment applies to these.
- For `woundStatus == "grievously_wounded"`: unchanged — keep the existing craft-and-treat
  flow exactly as it is today.

**Data/doc changes:**
- `master-design.md` §6.8 table updated to the corrected durations above
- `EncounterWorker.kt`: `LIGHT_WOUND_MS`, `HEAVY_WOUND_MS`, `SAFETY_NET_WOUND_MS` constants
  updated to `1_080_000L` (18 min), `1_800_000L` (30 min), `7_200_000L` (2 hr) respectively

**Explicitly not changing:** the Journal/Band screens' existing simple "Wounded"/"Grievous
Wound" status labels stay as a lightweight indicator there — the detailed countdown lives
specifically on the House of Healing tab, per Wes's direction that recovery info belongs
there.

---

## Section C: Post-fight rewards summary

**Current behavior:** On Victory, `EncounterWorker.kt` already grants money
(`rewardMoneyMin`..`rewardMoneyMax` × `rewardMultiplier`), 1-3 random ingredients from
`encounter.rewardTable`, sometimes a grimoire (`encounter.grimoireDrops`), and combat XP
(40 victory / 15 stalemate / 0 defeat) — entirely silently. `CombatReport` (the data behind
the post-fight recap card) has no fields for any of this.

**Fix:**
- Add four columns to `CombatReport` (`data/db/CombatReport.kt`): `moneyGranted: Int = 0`,
  `ingredientsGrantedJson: String = ""` (`id:qty,id:qty` format, matching the existing
  `woundsJson`/`dpsJson`/`healJson` convention on this same entity), `grimoireIdsGrantedJson:
  String = ""` (comma-separated IDs, usually empty), `xpGranted: Int = 0`. New additive
  migration, DB v19 → v20 (same pattern as `Migration18To19`).
- `EncounterWorker.kt`: capture the actual granted values into these new `CombatReport`
  fields at the same point they're already being applied (money/ingredients/grimoires/XP
  granting logic itself is unchanged — just also recorded).
- `CombatReportCard` (`MissionsScreen.kt`): add a "Rewards" section — money gained,
  ingredients gained (resolved to names via `GameDataRepository`, with quantities),
  grimoire unlocked (if any, visually distinct — this is the rarer, more exciting drop),
  XP gained. Shown only when `outcome != "DEFEAT"` (matches the existing "no reward on
  defeat" rule — nothing to show there since nothing was granted).

**Explicitly not changing:** the actual reward *values* or drop tables — this is purely
making already-existing grants visible, not rebalancing what's granted.

---

## Testing

- Section A: existing Compose UI, no automated test infra for visual verification in this
  repo (consistent with the tab/pager fixes from the prior audit-fixes session) — manual
  on-device check that kill-fight countdown is gone, survive/retrieve fights still show it
- Section B: a unit test on the wound-duration-selection logic (`EncounterWorker.kt`'s
  `WoundOutcome` resolution) asserting the three corrected constants; manual verification
  of the HoH tab countdown display and that light/heavy members no longer see treat options
- Section C: unit test on `CombatReport` migration (schema round-trip, matching existing
  migration test patterns in this repo); manual verification that a completed mission shows
  accurate rewards matching what was actually granted

## Out of scope (belongs to later groups in the sequence)

Damage types, live in-combat DPS/heal visibility, Inspiration/narrative copy, Kitchen/Pantry
UX, Recipe Rank, ingredient/recipe economy balance, gear system — all explicitly deferred to
their own later sub-projects per the plan's sequencing.
