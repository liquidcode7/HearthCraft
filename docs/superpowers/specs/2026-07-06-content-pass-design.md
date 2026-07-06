# Content Pass — Design Spec

**Status:** Approved by Wes, 06 Jul 2026. Fourth sub-project of the 04 Jul 2026 playtest
audit follow-up (see `docs/superpowers/plans/2026-07-04-audit-quick-fixes.md` for the
full 7-group sequencing this belongs to).

## Why

Two related audit items, both about description/narrative accuracy:
- Audit item #2: "Inspiration descriptions inaccurate; Fighter inspiration should do true
  damage." Investigation this session found the problem is worse than inaccurate copy —
  three of the four role ability descriptions in `JournalScreen.kt`'s `roleAbility()`
  describe an entirely different mechanic than the one they're named for.
- Audit item #13: "Fight narrative is thin/bizarre in places." Investigation found the
  cause: `buildCombatNarrative` (`MissionsScreen.kt`) uses one generic sentence template
  across all 16 encounters — regardless of enemy type — and that template treats the
  encounter's display name as the grammatical subject of "withstand" / "wore them down."
  For scene-titled encounters ("Goblin Assault on the Outer Gate," "Midges at the Forest
  Margin") this reads as nonsensical or tonally mismatched (an "easy" bug-swarm defeat
  reading like an epic last stand).

Separately, this session also found `design/master-design.md` §6.3's own prose has
drifted from the actual `EncounterEngine.kt` code for two Inspiration numbers (not
`EncounterEngine.kt`'s fault — the doc is stale, the code is correct and unchanged).
Wes confirmed fixing this drift belongs in this same pass, since it's the same category
of work (description accuracy) touching adjacent content.

This is a copy/content pass — no simulation or engine logic changes anywhere in this spec.

---

## Section A: Ability description corrections (`JournalScreen.kt`)

**Current problem:** `roleAbility(role: String)` returns one hardcoded description per
role, keyed only by role. Comparing each against the real mechanic (`EncounterEngine.kt`,
`design/master-design.md` §6.3):

| Role | Current copy describes | Actually named for | Real trigger/effect |
|---|---|---|---|
| Warden | Ward Guard (intercepts a killing blow aimed at the Keeper, cap 3) — a real but *separate* mechanic | Horn of Gondor | Crisis-edge trigger (2+ members below 35% HP): 20-tick window, all spike damage redirects to the Warden, his HP floors at 1 |
| Fighter | Baseline attack stat scaling (Agility+Might, armor-mitigated) — not an Inspiration at all | Black Arrow / Bullroarer's Five-Iron | Fires late-fight only if the party's pace won't kill in time: burns 18% of boss resolve directly, bypassing armor entirely (audit's "should do true damage" — it already does; the copy just didn't say so) |
| Keeper | Rescue (auto-revives anyone simply knocked down, cap 5) — a real but *separate*, passive mechanic | Hands of Healing | Fires only at the grievous wound threshold (5 wounds): fully clears wounds, heals to 40% max HP, cap 2 uses |
| Captain | Roughly right, but only mentions the DPS budge, omitting the heal/revive effect and the "unlocks other roles' conditions" effect entirely | Wrath, Ruin, and the Red Dawn | Crisis-edge trigger: heals party 30% max HP (can revive), ×2.0 party DPS for 15 ticks, *and* relaxes the other three roles' trigger conditions for the same window |

**Fix — new copy** (Tolkien-lore-flavored per Wes's direction: Horn of Gondor's
never-unanswered call, Bard's Black Arrow finding the dragon's one bare scale, Bullroarer
Took's blow at the Battle of Greenfields, "the hands of a healer are known by their
grace," and Théoden's ride — "wrath, ruin, and a red dawn... ere the sun rises"):

- **Warden — "The Horn of Gondor":** "Kept for the direst hour, its call has never gone
  unanswered. When the company's peril turns to crisis, the horn sounds — and for a time,
  every blow meant for the fallen turns instead upon the one who blew it. While it sounds,
  he does not fall."
- **Fighter, ranged build — "Black Arrow":** "An heirloom shot, spent only when the fight
  is slipping away. It cares nothing for mail or hide, and finds its mark plainly — as the
  Black Arrow once found the one bare scale."
- **Fighter, melee build — "Bullroarer's Five-Iron":** "A blow swung with everything
  behind it, spent only when the fight is slipping away. Mail or hide, it makes no
  difference — it finds its mark clean, the way Bullroarer's swing once found Golfimbul's
  head and sent it a hundred yards down a rabbit-hole."
- **Keeper — "Hands of Healing":** "Named of old for a healer's hands, which are known by
  their grace. When a companion's wounds pile past bearing, the Keeper's touch clears
  them wholly and calls them back into the light — twice in a battle, and no more."
- **Captain — "Wrath, Ruin, and the Red Dawn":** "Wrath, ruin, and a red dawn ere the sun
  rises — the Captain's call, and the company surges as one. Blows fall harder, the
  standing are made whole, the fallen lifted back to their feet. And for that little
  while, courage comes easier to every hand in the company."

**Implementation:** `roleAbility()` needs a second parameter, `fighterBuild: String`, to
select between the two Fighter variants. `PlayerState.fighterBuild` ("ranged"|"melee",
default "ranged") already exists and is already read the same way elsewhere in the UI
layer (`BandViewModel.kt:144`'s `isMeleeFighter`) — `JournalScreen.kt` needs to thread the
same value in at its `roleAbility(member.role)` call site.

The Rescue/Ward-Guard conflation (Warden's Ward Guard, Keeper's Rescue) are real, separate
mechanics that currently have *no* description anywhere in the UI once this fix lands —
that's an acceptable, explicit gap for this pass (adding a second ability-card entry per
role is a UI-layout question, not a copy question) — not silently dropped, just out of
scope here.

---

## Section B: `design/master-design.md` §6.3 numeric corrections

Two stale numbers, doc-only (the code is correct and unchanged):

- Horn of Gondor: "for an 8-tick window" → **"for a 20-tick window"** (matches
  `HORN_DURATION = 20`)
- Red Dawn: "heals all active members 15% of max HP... grants the whole party a ×1.5 DPS
  multiplier for 10 ticks" → **"heals all active members 30% of max HP... grants the
  whole party a ×2.0 DPS multiplier for 15 ticks"** (matches `DAWN_HEAL_FRAC = 0.30f`,
  `DAWN_DPS_MULT = 2.0f`, `DAWN_DURATION = 15`)

Black Arrow's 18%/15%/40%-gate figures and Grace's 2-use cap are already correct in the
doc — not touched.

---

## Section C: Post-fight narrative — outcome-bucketed, no new data

**Current problem:** `buildCombatNarrative` (`MissionsScreen.kt`) has one template per
outcome (`VICTORY`/`DEFEAT`/`STALEMATE`), and the `VICTORY`/`DEFEAT` templates use the
encounter's display name (`report.encounterName`) as the grammatical subject of
"could not withstand them" / "wore them down... until there was no one left standing."
For encounters named after a scene rather than an enemy ("Goblin Assault on the Outer
Gate," "Midges at the Forest Margin"), this reads as nonsensical or as an inappropriately
epic tone applied uniformly regardless of the actual enemy or difficulty — this is the
audit's "thin/bizarre" complaint.

**Fix:** stop using the encounter name as the narrative's subject at all, and instead
bucket the outcome using data already present on `CombatReport`
(`endedAtSec`, `durationSec`, `rescuesUsed`, `wardGuardsUsed`, and total wounds parsed
from the already-existing `woundsJson`) into one of five buckets — no new `Encounter`
fields, no `encounters.json` authoring, nothing deferred for later:

- **Victory, clean & fast** — finished before half duration, no rescues, no ward-guards,
  low total wounds: *"The fight was swift — victory, and hardly tested."*
- **Victory, clean & hard-fought** — finished after half duration, still low
  wounds/no rescues/no ward-guards: *"A hard-fought victory. The band held until the
  enemy could hold no longer."*
- **Victory, close call** — rescues used, OR ward-guards used, OR total wounds ≥ 3:
  *"A close call — the band nearly broke before the enemy did. Victory, but a near
  thing."*
- **Defeat, quick collapse** — fell before a quarter of full duration elapsed:
  *"The band fell quickly, overwhelmed before they found their footing."*
- **Defeat, worn down** — fell later than a quarter of full duration:
  *"Wound by wound, the band was worn down until none remained standing. The party
  fell."*
- **Stalemate** — unchanged. Its existing `resolveRemainingFraction`-based bucketing
  ("barely scratched" / "bloodied but standing" / "on the edge of breaking") has no
  naming-subject problem and doesn't need this fix.

"Close call" threshold (`rescuesUsed > 0 || wardGuardsUsed > 0 || totalWounds >= 3`) is a
starting definition, not final-tuned — easy to retune later if it doesn't feel right
in practice. Total wounds is summed from the same `woundsJson` string `CombatReportCard`
already parses into `woundsMap` for the wound-recap section further down the same card;
`buildCombatNarrative` should take the parsed sum as a parameter rather than re-parsing
`woundsJson` itself, to avoid duplicating that parsing logic.

---

## Explicitly out of scope

- Any simulation/engine logic change — this is a pure copy/content + one small function-
  signature change (`roleAbility` gains `fighterBuild`, `buildCombatNarrative` gains a
  wound-total parameter). No `EncounterEngine.kt` change.
- Per-encounter authored victory/defeat flavor lines — considered and explicitly rejected
  in favor of the simpler outcome-bucketing approach above (Wes's direction).
- A UI entry for the Warden's Ward Guard or Keeper's Rescue mechanics (now undescribed
  anywhere in the UI once Section A lands) — a real, acknowledged gap, but a UI-layout
  decision for a separate pass, not a copy question.
- Re-authoring `flavorIntro`/`flavorLine` (the pre-fight encounter flavor text) — these
  were reviewed this session and found to already read well; not touched.

## Testing

- Section A: a unit test on `roleAbility(role, fighterBuild)` asserting the correct title
  is returned per role, and specifically that `"fighter"` + `"melee"` returns the
  Bullroarer title/copy while `"fighter"` + `"ranged"` (and the default) returns Black
  Arrow's.
- Section C: a unit test on the bucketing logic asserting each of the five buckets is
  selected under the right `CombatReport` field combinations described above.
- No automated test infra exists for the Compose UI rendering itself (consistent with
  every prior UI-touching plan in this sequence) — manual verification that the Journal
  screen shows the right per-role/per-build text, and that a few real post-fight recaps
  read sensibly, is deferred to on-device testing.
