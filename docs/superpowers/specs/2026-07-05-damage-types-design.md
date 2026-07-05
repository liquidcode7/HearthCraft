# Damage Types — Design Spec

**Status:** Approved by Wes, 05 Jul 2026. Second sub-project of the 04 Jul 2026 playtest
audit follow-up (see `docs/superpowers/plans/2026-07-04-audit-quick-fixes.md` for the
full 7-group sequencing this belongs to).

## Why

The audit asked directly: "how do you have [magic damage] coded?" Answer, found this
session: it isn't. `EncounterEngine.kt` applies the same physical-armor mitigation
(`(1f - effArmor)`) to every member's damage, including the Keeper's — despite
master-design.md §5 already assigning distinct damage types per role (Warden/Fighter:
Physical, Keeper: Magical, Captain: Hybrid with explicit "partial armor immunity").

This spec also draws on a pre-redesign brainstorm doc,
`legacy/brainstorm/damage-types-bane-affinities-design.md` — confirmed by Wes as the
right reference this session, despite predating the 30 June redesign (its role names and
DPS numbers are stale, but the mechanic shape and damage-type taxonomy carry forward).

---

## Section A: Outgoing damage split (the actual mechanical fix)

**Current behavior:** `EncounterEngine.kt` has exactly two places `effArmor` is applied to
outgoing damage:
- The main per-tick DPS loop (`for (m in standing().filter { it.input.role != "keeper" })`)
  — covers Warden, Fighter, **and Captain** (only Keeper is excluded from this loop).
- The Keeper's own DPS-tick branch (fires when the Keeper isn't healing that tick).

Both multiply raw damage by `(1f - effArmor)` uninformed by role — Keeper's magical damage
is reduced by armor exactly like a Warden's sword swing.

**Fix:**
- **Physical** damage (Warden, Fighter, and the Might-driven portion of Captain's damage):
  unchanged, still `(1f - effArmor)`.
- **Magical** damage (Keeper's full damage, and the Will-driven portion of Captain's
  damage): bypasses armor entirely — full raw damage, no mitigation factor. **No new
  enemy-side "magic resistance" stat is added in this pass** — Wes's call: keep it simple
  until a specific encounter actually needs enemies resistant to magic.
- **Captain's split is stat-weighted**, not a fixed ratio: `rawDps("captain") = might *
  0.9f + will * 0.6f`. The `might * 0.9f` term is the physical portion (armor-mitigated),
  the `will * 0.6f` term is the magical portion (bypasses armor). A Captain built with more
  Will naturally deals more armor-immune damage — this falls out of stats already in the
  formula, no new number to invent or tune.

**Balance impact (explicitly not addressed here):** this is a real damage buff to Keeper
(and part of Captain's) output against any encounter with nonzero `physMitPct`, since magic
damage that used to be reduced by armor no longer is. Encounter armor values were tuned
without this distinction existing. Per Wes: implement the mechanic correctly now, flag
existing encounter armor/difficulty values for the later economy/balance-pass group rather
than hand-tune blind in this pass.

**Test risk:** low. `EncounterEngineTest.kt`'s existing tests are statistical (win-rate
thresholds over many seeded runs), not exact-damage assertions — a damage buff to
Keeper/Captain should, if anything, make these thresholds easier to clear, not break them.
Confirm with a full test run regardless.

---

## Section B: Named magic types (flavor layer, no new mechanics)

Per the legacy brainstorm doc: magic damage isn't just generically "magical" — it has a
name, tied to *who* is casting it (weapon-driven once weapons exist, character-driven
until then):

| Role | Magic type | Lore character |
|---|---|---|
| Keeper (default) | **Light** | Grace of the Valar; the fire of Aman and Elbereth's starlight |
| Captain (default) | **Westernesse** | The strength of Númenorean heritage; ancient craftsmanship and the lineage of kings |

**This is a naming/documentation layer only in this pass** — no new field is added to
`MemberInput`/`EncounterResult`/anywhere else in code, because nothing reads it yet: no UI
shows damage type (that's the later "live combat visibility" group), and no bane-affinity
logic exists to match against it (that requires weapons, which don't exist yet). Building a
data field nobody consumes would be premature plumbing. The names are recorded here and in
`design/master-design.md` as the canonical vocabulary, to be wired into an actual field the
moment either the visibility group or the gear/bane group needs it.

---

## Section C: Enemy category tags (data authoring, inert until weapons exist)

Also per the legacy doc: legendary weapons will eventually carry a magic damage type
(**Beleriand** — First Age elvish/dwarven power vs. orc-kind; **Mormegil** — the Black
Sword's doom vs. dragon-kind), and enemies carry a category that's vulnerable to a
matching weapon type ("bane affinity"). **No bane multiplier fires without a weapon, and
weapons don't exist yet** — this section is purely tagging current encounter data now so
the gear/bane system (last in the audit sequence) has data to read later, rather than
revisiting every encounter's data at that point.

Category → vulnerability table (from the legacy doc, confirmed by Wes this session):

| Category | Vulnerable to | Example enemies |
|---|---|---|
| `orc` | Beleriand | Orcs, goblins, Uruk-hai |
| `dragon` | Mormegil | Drakes, dragons |
| `shadow` | Light | Shadow-creatures, spiders, Balrog-adjacent |
| `wraith` | Westernesse | Ringwraiths, Barrow-wights, Dead Men of Dunharrow |
| `nature` | *(none defined yet)* | Huorns and other nature-creatures — Wes: "I don't have anything built as effective towards them yet" |

Proposed tagging for all 16 current encounters (`app/src/main/assets/data/encounters.json`),
confirmed by Wes:

| Encounter ID | Category |
|---|---|
| `mithlost_goblins` | `orc` |
| `undermarch_goblins` | `orc` |
| `greycloaks_goblins` | `orc` |
| `undermarch_drakeling` | `dragon` |
| `mithlost_large_spider` | `shadow` |
| `greycloaks_barrow_wight` | `wraith` |
| `mithlost_huorn` | `nature` |
| `mithlost_midges` | *(none — beast, no defined vulnerability)* |
| `mithlost_wargs` | *(none)* |
| `undermarch_bats` | *(none)* |
| `undermarch_wolves` | *(none)* |
| `greycloaks_neekerbreekers` | *(none)* |
| `greycloaks_wolves` | *(none)* |
| `greycloaks_wolf_master` | *(none)* |
| `greycloaks_rhudaur_men` | *(none — human faction, doesn't fit the fantasy-creature taxonomy)* |
| `undermarch_dourhand` | *(none — hostile dwarf faction, same reasoning)* |

**Implementation:** add `val enemyCategory: String? = null` to the `Encounter` data class
(`data/model/Encounter.kt`), and set `"enemyCategory"` in `encounters.json` only for the 7
tagged encounters above — the other 9 rely on the `null` default, no JSON change needed for
them. This field is inert — nothing reads it in this pass.

---

## Explicitly out of scope (deferred to later groups/sessions)

- **Bane affinity multiplier logic** — requires weapons (gear system group, last in
  sequence).
- **Incoming (enemy-side) typed damage** — Shadow/Unlight/Cataclysm/Wraith as damage
  enemies deal, and how it interacts with the existing Shadow/Despair status effect. The
  legacy doc marks this "design pending" and explicitly says not to implement it until a
  dedicated design session answers several open questions (is it a separate channel or
  attached to spikes? what's the mitigation model? can players ever deal Shadow/Wraith
  damage?). Not touched here.
- **Enemy-side magic resistance stat** — Wes's call this session: bypass armor entirely
  for magic damage rather than add a new stat, until a specific encounter needs it.
- **Damage-type visibility in the UI** (post-fight % breakdown by type, color-coded bars) —
  belongs to the "live combat visibility" group later in the sequence, which depends on
  this section's mechanic existing first.
- **Encounter armor/difficulty re-tuning** in response to the Keeper/Captain damage buff —
  belongs to the later economy/balance-pass group.

## Testing

- Unit test on the physical/magic split logic itself — assert that, for a fixed `effArmor`
  and fixed stats, Keeper's full damage is unmitigated while Warden/Fighter's damage is
  reduced by the same factor as today, and Captain's damage lands between the two
  (partially mitigated) matching the stat-weighted split.
- Run the existing `EncounterEngineTest.kt` suite in full — confirm no regression in the
  win-rate threshold tests (expected: unaffected or easier to clear, not harder).
- Manual/data verification: confirm `enemyCategory` deserializes correctly for the 7 tagged
  encounters and defaults to `null` for the other 9, with no crash either way.
