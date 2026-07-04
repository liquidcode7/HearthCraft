# Navigation Redesign — Missions, Journal, Band, House of Healing

**Date:** 2026-07-04
**Status:** Approved

---

## 1. Overview

The mission-sending flow is currently clunky and duplicated: both the Band tab
(`EncounterSendRow` → `ProvisioningDialog`, a bare `AlertDialog`) and the
Missions tab (`EncounterCard` → inline food/draught selection → send button)
can launch a mission. Character personality and stats live in a modal dialog
off the Band tab's member rows, disconnected from the Journal, which today is
just a stats glossary + discovered recipe list.

This redesign consolidates responsibilities per tab so each one has exactly
one job:

| Tab | Job |
|-----|-----|
| Home | Status summary. Band card taps through to Missions. |
| Missions | The only place to provision and launch a mission. Shows pre-battle intel. |
| Band | Roster overview + band switcher. Nothing else. |
| Journal | Per-character stats + bio, plus glossary/recipe content it already has. |
| House of Healing (new) | Wound status, HoH crafting, applying preparations, HoH level. |

No new game mechanics are introduced by this pass except the Missions-tab
odds estimate (§2) and the Journal bio-stage schema (§3, unwired). Everything
else is a relocation of existing UI and existing data.

---

## 2. Missions Tab — Launch Pad

Replaces both the Band tab's send flow and the current Missions tab layout
with a single encounter-card list. Each unlocked encounter's card shows:

- **Name** and existing qualitative difficulty tag (Routine/Challenging/
  Dangerous/Boss) — unchanged.
- **Boss HP** — a bar plus the numeric value, sourced from `Stage.resolve`
  (already authored per stage in `encounters.json`/`Encounter.kt`). For
  multi-stage encounters, show each stage's resolve total (e.g. as a
  segmented bar or a small per-stage list) rather than only the first stage.
- **Flavor blurb** — the existing `flavorIntro`/`flavorLine` text. Atmospheric
  only, never states mechanics or numbers.
- **Odds label** — one of `Outmatched` / `Even Fight` / `Favored` / `Crushing`.
  Computed by calling `EncounterEngine.resolve()` ~50–100 times off the main
  thread (`Dispatchers.Default`), using `BandRepository.memberInputsForBand()`
  with the band's **current** stats and **currently assigned** food/draughts,
  varying the RNG seed each run, and bucketing the resulting win rate:
  - < 25% → Outmatched
  - 25–60% → Even Fight
  - 60–85% → Favored
  - > 85% → Crushing
  (Exact cutoffs tunable later via the balance harness — these are starting
  points, not locked.) Recomputes whenever the assigned food/draughts change.
  Shows an inline "calculating odds…" spinner on the card while running; does
  not block the rest of the screen.
- **Provisioning** — per-member food/draught assignment happens directly on
  this card (or an expanded state of it) — richer than today's plain
  `AlertDialog` list, but no separate dialog screen to navigate into. Replaces
  `ProvisioningDialog` entirely.
- **Send button** — same gating as today (all alive, all healthy).

The Band tab's `EncounterSendRow` and `ProvisioningDialog` are removed;
`BandScreen` no longer initiates missions.

---

## 3. Journal Tab — Character Detail

Journal keeps its existing sections (Stats glossary, Food Effects glossary,
Discovered Recipes) and gains one new section per character:

- **Bio** — the existing `personality` paragraph, relocated here from
  `MemberDetailDialog` (today only reachable via Band tab). Content unchanged.
- **Stats** — VIT/MGT/AGI/WIL/FAT bars, relocated from the same dialog.
- **Role ability** — the existing ability name + description text (e.g. "The
  Horn of Gondor"), relocated from the same dialog.

Reached by tapping a member row on the Band tab (replacing the current
`MemberDetailDialog` popup with real navigation to a Journal page/section).

**Bio-stage framework (schema only, unwired):** Add cumulative per-member
counters to `BandMemberState` (new Room migration): missions survived, wounds
survived, grievous wounds survived. Add a data shape allowing more than one
authored bio stage per character. **No trigger logic ships in this pass** —
every character still shows exactly one bio stage (their current
`personality` text). The counters exist so a future pass can wire specific
story-beat triggers without another migration. This pass does not decide what
those triggers are.

Scope note: only handles the 4 members of whichever band is currently being
viewed — no changes related to multi-band rosters or permadeath.

---

## 4. Band Tab — Roster Overview

Trimmed to:
- Band switcher (unchanged, if second band unlocked)
- Member rows: name, role, status tag (Active/Wounded/Grievous/Fallen — same
  logic as today), stat line summary (unchanged)
- Tapping a row navigates to that member's Journal page instead of opening
  `MemberDetailDialog`

Removed: `EncounterSendRow` list, `ProvisioningDialog`, the "Recovering"
wound-timer list (moves to House of Healing, §5).

---

## 5. House of Healing Tab (new)

New bottom-nav (or drawer) destination. Contains:

- **Wounded/recovering status** — relocated from Band tab's current
  "Recovering" section: who's hurt, wound type(s), ordinary vs. grievous,
  recovery timer.
- **HoH crafting UI** — moved off the Kitchen screen. Surfaces the
  already-authored `class: "hoh"` recipes in `recipes.json` (~20 recipes,
  `hohLevel` 1–20, athelas-based) gated the same way cooking recipes are
  (HoH level + grimoire, per design doc §8.4/§9.2). No new recipes are
  authored in this pass — this is a UI move of existing data into its own
  crafting surface, not new content.
- **Apply preparation** — assign a crafted HoH item to a specific grievously
  wounded member to begin/continue their treatment (per design doc §9.1's
  recovery-timer + recovery-buff mechanics, already specified).
- **HoH level/XP track** — its own display, independent of Cooking level
  (design doc §9.2 already specifies this is a separate leveling track).

---

## 6. Home Tab

Band card's status content is unchanged. It gains a tap action: navigates to
Missions (previously no `onClick` at all).

---

## 7. Gather-Quality Indicator

`HarvestResultDialog` (`GatheringScreen.kt`) already receives `item.grade` on
every row (farm/garden/forage/hive/coop/dairy/process — `HarvestItem.grade`
is populated by every relevant worker) but never renders it. Add the existing
`GradeBadge` composable (already used in Band/Pantry/Kitchen/Missions
screens) next to each row's name/quantity. No new dialog, no new copy — just
the existing colored grade badge appearing where results are already shown.

---

## 8. Out of Scope (explicitly deferred)

- Bio-stage trigger logic and additional authored bio text (§3) — framework
  only this pass.
- Any new HoH recipes — the existing set is sufficient for this pass.
- The gather/farm/garden yield-balance concern and the "too many unused
  ingredients" concern raised alongside this request — separate balance/
  content work, not a UI design question.
- The Coop/Hive/Dairy timer-sticks-at-zero bug — separate bug fix, not a UI
  design question.
- The HTML sim metrics overhaul and the recipe-tab spreadsheet tool — both
  separate tooling projects with their own open questions.

---

## 9. Files Likely Touched

- `ui/screen/MissionsScreen.kt` — provisioning UI, odds label, boss HP display
- `ui/screen/BandScreen.kt` — strip send flow, strip recovering list, strip
  `MemberDetailDialog`, add navigation to Journal
- `ui/screen/JournalScreen.kt` — add per-character stats/bio section
- `ui/screen/GatheringScreen.kt` — `HarvestResultDialog` grade badges
- `ui/screen/KitchenScreen.kt` — remove HoH recipe crafting (moves out)
- New: `ui/screen/HouseOfHealingScreen.kt`
- `ui/viewmodel/BandViewModel.kt` — odds-estimate calculation, provisioning
  state lives here or a new `MissionsViewModel`
- New: `ui/viewmodel/HouseOfHealingViewModel.kt` (or extend `BandViewModel`)
- `data/db/BandMemberState.kt` + new Room migration — bio-stage counters
- Navigation graph (wherever bottom-nav destinations are registered) — add
  House of Healing tab
