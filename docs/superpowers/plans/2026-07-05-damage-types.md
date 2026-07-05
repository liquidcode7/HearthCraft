# Damage Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `docs/superpowers/specs/2026-07-05-damage-types-design.md` — split outgoing combat damage into physical (armor-mitigated) and magical (armor-bypassing) channels, document the named magic-type taxonomy, and tag current encounters with an inert enemy-category field for the future gear/bane system.

**Architecture:** Two independent tasks. Task 1 is the engine mechanic (`EncounterEngine.kt`) plus the design-doc section documenting it. Task 2 is pure data (`Encounter.kt` model field + `encounters.json` tagging), independent of Task 1's logic — it adds a field nothing reads yet.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit

## Global Constraints

- All Kotlin source lives under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- All game data JSON lives under `app/src/main/assets/data/`
- Commit messages prefixed `[hc]`
- Run `./gradlew build` before every commit — never commit a broken build (`export JAVA_HOME=/usr/share/pycharm/jbr` first)
- `EncounterEngine.rawDps()` formulas are NOT changed — only how the resulting number is split and mitigated
- No enemy-side "magic resistance" stat added — magic damage bypasses armor entirely for now, per spec
- No bane-affinity multiplier logic, no incoming (enemy-side) typed damage, no UI visibility of damage types — all explicitly out of scope per spec, deferred to later sub-project groups
- `tools/sim/run_sim.js` and `tools/sim/food_model.js` do not model armor/mitigation at all (confirmed by grep — no `physMit`/`armor` references anywhere in `tools/sim/`), so there is no JS mirror to keep in sync for this plan
- Design doc updates land in `design/master-design.md` alongside the code change in the same commit
- GPL-3.0

---

### Task 1: Split outgoing damage into physical and magical channels

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt`
- Modify: `design/master-design.md`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt`

**Interfaces:**
- Produces: `EncounterEngine.physicalFraction(m: MemberInput): Float` — `internal fun` (same visibility as the existing `rollWoundTypes`, for direct unit testing), returns the fraction of a member's raw damage that is physical (armor-mitigated); the remainder is magical (bypasses armor). No other task depends on this.

**Context:** `EncounterEngine.kt` currently applies `(1f - effArmor)` uniformly to all outgoing damage in two places: the main non-Keeper DPS loop (which also covers Captain, since only Keeper is excluded from that loop) and the Keeper's own DPS-tick branch. Per master-design.md §5, Warden/Fighter deal physical damage, Keeper deals magical damage, and Captain deals a hybrid of both — but the code doesn't distinguish any of this today. Fix: physical damage keeps `(1f - effArmor)`; magical damage bypasses it entirely (full raw damage, no new enemy-side stat). Captain's split is stat-weighted from the existing `rawDps` formula (`might * 0.9f` is the physical portion, `will * 0.6f` is the magical portion) — not a fixed ratio.

- [ ] **Step 1: Write the failing tests for `physicalFraction`**

In `EncounterEngineTest.kt`, add these two tests after the existing `party()` helper (before the first `@Test`):
```kotlin
    @Test
    fun `physicalFraction is 1 for warden and fighter, 0 for keeper`() {
        val warden = MemberInput("w", "warden", 4f, 2f, 5f, 3f, 2f)
        val fighter = MemberInput("f", "fighter", 3f, 5f, 3f, 4f, 4f)
        val keeper = MemberInput("k", "keeper", 2f, 3f, 3f, 5f, 4f)
        assert(EncounterEngine.physicalFraction(warden) == 1f)
        assert(EncounterEngine.physicalFraction(fighter) == 1f)
        assert(EncounterEngine.physicalFraction(keeper) == 0f)
    }

    @Test
    fun `physicalFraction for captain is stat-weighted between might and will terms`() {
        val captain = MemberInput("c", "captain", 3f, 2f, 4f, 5f, 4f)
        // physTerm = 3*0.9 = 2.7, magicTerm = 5*0.6 = 3.0, total = 5.7
        val expected = (3f * 0.9f) / (3f * 0.9f + 5f * 0.6f)
        val actual = EncounterEngine.physicalFraction(captain)
        assert(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew test --tests "*.EncounterEngineTest"
```
Expected: compilation failure, `physicalFraction` is unresolved on `EncounterEngine`.

- [ ] **Step 3: Add `physicalFraction` to `EncounterEngine.kt`**

Add this function right after `rawDps`:
```kotlin
    // Fraction of a member's raw damage that is physical (armor-mitigated) vs. magical
    // (bypasses armor). See master-design.md §6.9. Warden/Fighter are pure physical,
    // Keeper is pure magical, Captain splits proportionally between the might-driven and
    // will-driven terms of their own rawDps formula.
    internal fun physicalFraction(m: MemberInput): Float = when (m.role) {
        "warden", "fighter" -> 1f
        "keeper" -> 0f
        "captain" -> {
            val physTerm = m.might * 0.9f
            val magicTerm = m.will * 0.6f
            val total = physTerm + magicTerm
            if (total > 0f) physTerm / total else 1f
        }
        else -> 1f
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*.EncounterEngineTest"
```
Expected: PASS, all tests including the two new ones.

- [ ] **Step 5: Apply the split at the two damage-mitigation sites**

Find the main non-Keeper DPS loop:
```kotlin
            for (m in standing().filter { it.input.role != "keeper" }) {
                val s = streaks[m.input.id]
                val mult = (if (s != null && s.active > 0) STREAK_MULT else 1f) * dawnDpsMult
                val dmg = rawDps(m.input).toFloat() * mult * (1f - effArmor) * jMul
                nonKeeperDps += dmg
                damageAcc[m.input.id] = (damageAcc[m.input.id] ?: 0f) + dmg
            }
```
Replace with:
```kotlin
            for (m in standing().filter { it.input.role != "keeper" }) {
                val s = streaks[m.input.id]
                val mult = (if (s != null && s.active > 0) STREAK_MULT else 1f) * dawnDpsMult
                val raw = rawDps(m.input).toFloat() * mult * jMul
                val physFrac = physicalFraction(m.input)
                val dmg = raw * physFrac * (1f - effArmor) + raw * (1f - physFrac)
                nonKeeperDps += dmg
                damageAcc[m.input.id] = (damageAcc[m.input.id] ?: 0f) + dmg
            }
```

Find the Keeper DPS-tick branch:
```kotlin
                // Keeper DPS only when no consuming action taken
                if (!actionTaken) {
                    val keeperDmg = rawDps(keeper.input) * healMult * dawnDpsMult * (1f - effArmor) * jMul
                    boss -= keeperDmg
                    damageAcc[keeper.input.id] = (damageAcc[keeper.input.id] ?: 0f) + keeperDmg
                    keeperDpsTicksCount++
                }
```
Replace with:
```kotlin
                // Keeper DPS only when no consuming action taken. Keeper is pure magical
                // (physicalFraction == 0), so this is unconditionally unmitigated by armor —
                // written directly rather than routed through physicalFraction since the
                // role is fixed here.
                if (!actionTaken) {
                    val keeperDmg = rawDps(keeper.input) * healMult * dawnDpsMult * jMul
                    boss -= keeperDmg
                    damageAcc[keeper.input.id] = (damageAcc[keeper.input.id] ?: 0f) + keeperDmg
                    keeperDpsTicksCount++
                }
```

- [ ] **Step 6: Run the full `EncounterEngineTest` suite**

```bash
./gradlew test --tests "*.EncounterEngineTest"
```
Expected: PASS, all tests (this is a damage buff to Keeper/Captain against armored encounters — the existing win-rate threshold tests should still pass, likely with more margin, not less; if any fails, read the failure message before assuming the test itself needs changing — it may be revealing a bug in the split logic instead).

- [ ] **Step 7: Add the Damage Types section to `design/master-design.md`**

Find the end of §6.8 (right before the `---` separator that precedes §7):
```markdown
**HoH-availability safety net:** while the player has no visible/craftable HoH
recipe at all, a 5+ down-count result is capped at "Wounded (heavy)" with an
extended 2-hour recovery instead of becoming a genuine grievous wound. This is
deliberately harsh — a real penalty for under-provisioning, not a soft nudge —
since starting areas have no HoH recipe available at all and this is the only
consequence a new player faces for this outcome. The safety net switches off
automatically the moment a real HoH recipe becomes available to the player —
it is not a permanent difficulty reduction.

---
```
Replace with:
```markdown
**HoH-availability safety net:** while the player has no visible/craftable HoH
recipe at all, a 5+ down-count result is capped at "Wounded (heavy)" with an
extended 2-hour recovery instead of becoming a genuine grievous wound. This is
deliberately harsh — a real penalty for under-provisioning, not a soft nudge —
since starting areas have no HoH recipe available at all and this is the only
consequence a new player faces for this outcome. The safety net switches off
automatically the moment a real HoH recipe becomes available to the player —
it is not a permanent difficulty reduction.

### 6.9 Damage Types

Every role's damage is either physical or magical. Physical damage (Warden, Fighter, and
the Might-driven portion of Captain's hybrid damage) is reduced by encounter armor
(`physMitPct`, countered by Potency draughts as already documented in §7.3). Magical
damage (Keeper's full damage, and the Will-driven portion of Captain's hybrid damage)
bypasses armor entirely — full raw damage, always. There is no enemy-side magic
resistance stat yet; this is a deliberate simplification until a specific encounter needs
enemies resistant to magic.

Captain's split is stat-weighted, not fixed: of the `might * 0.9 + will * 0.6` hybrid
rawDps formula (§5.1), the `might * 0.9` term is physical and the `will * 0.6` term is
magical. A Captain built with more Will naturally deals more armor-immune damage — this
falls out of the existing formula, not a new number.

Magical damage carries a named type, tied to who is casting it (character-driven until
weapons exist, weapon-driven after):

| Role | Magic type | Lore character |
|---|---|---|
| Keeper (default) | **Light** | Grace of the Valar; the fire of Aman and Elbereth's starlight |
| Captain (default) | **Westernesse** | The strength of Númenorean heritage; ancient craftsmanship and the lineage of kings |

The type name is documentation only in this pass — no code field carries it yet, since
nothing reads it (no UI shows damage type, no bane-affinity logic exists to match
against it). It becomes a real field the moment either is built.

**Enemy category tags** (§12 encounters) mark what an enemy *is*, for the future
gear/bane system: a legendary weapon will eventually carry a magic damage type of its
own (**Beleriand** — First Age elvish/dwarven power, vs. orc-kind; **Mormegil** — the
Black Sword's doom, vs. dragon-kind), and when a weapon's type matches an enemy's
category vulnerability, a bane multiplier will fire. No bane fires without a weapon, and
weapons don't exist yet — the tags are authored now purely so that system has data to
read later.

| Category | Vulnerable to | Notes |
|---|---|---|
| `orc` | Beleriand | Orcs, goblins, Uruk-hai |
| `dragon` | Mormegil | Drakes, dragons |
| `shadow` | Light | Shadow-creatures, spiders, Balrog-adjacent |
| `wraith` | Westernesse | Ringwraiths, Barrow-wights, Dead Men of Dunharrow |
| `nature` | *(none defined yet)* | Huorns and other nature-creatures |

**Explicitly deferred:** the bane multiplier itself (needs weapons — gear system, not yet
designed), incoming (enemy-side) typed damage such as Shadow/Wraith damage dealt *to* the
party (needs a dedicated design session — see `legacy/brainstorm/damage-types-bane-affinities-design.md`
for the open questions), and any UI surfacing of damage type or category.

---
```

- [ ] **Step 8: Full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt design/master-design.md
git commit -m "$(cat <<'EOF'
[hc] Magic damage bypasses armor (Keeper full, Captain stat-weighted)

EncounterEngine applied the same physical-armor mitigation to every
role's damage, including the Keeper's magical damage — contradicting
master-design.md §5's role damage-type assignments (audit finding, 04
Jul 2026: "how do you have magic damage coded?" — answer was: not at
all). Warden/Fighter unchanged. Keeper now fully bypasses armor.
Captain splits stat-weighted between the might-driven (physical) and
will-driven (magical) terms of its existing hybrid rawDps formula. No
new enemy-side magic-resist stat added — deliberately simple until a
specific encounter needs one. This is a real damage buff to Keeper/
Captain against armored encounters; re-tuning encounter values is
deferred to the later economy/balance-pass group, not addressed here.

Also documents the named magic-type taxonomy (Light/Westernesse) and
the enemy-category vocabulary (orc/dragon/shadow/wraith/nature) that
the later gear/bane-affinity system will consume — both inert in this
change, no new fields, no UI.
EOF
)"
```

---

### Task 2: Tag current encounters with an enemy category

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Encounter.kt`
- Modify: `app/src/main/assets/data/encounters.json`

**Interfaces:**
- Produces: `Encounter.enemyCategory: String?` — no other task in this plan depends on it; it exists purely as forward-compatible data for the future gear/bane system (see Task 1's §6.9 doc section).

**Context:** Independent of Task 1's engine logic. Adds one nullable field to the `Encounter` data class and sets it on 7 of the 16 current encounters, per the category table Task 1 wrote into `design/master-design.md` §6.9. The other 9 encounters (beasts, and human/dwarf hostile factions that don't fit the fantasy-creature taxonomy) get no category — they rely on the field's `null` default, no JSON change needed for them.

- [ ] **Step 1: Add the field to `Encounter.kt`**

Find:
```kotlin
@Serializable
data class Encounter(
    val id: String,
    val bandId: String,
    val name: String,
    val region: String,
    val difficulty: String,         // "easy" | "medium" | "hard" | "boss"
    val recLevel: Int,
    val requiredCookingLevel: Int,
    val flavorIntro: String,
    val flavorLine: String,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardMultiplier: Int,
    val durationMs: Long,
    val rewardTable: List<String>,
    val grimoireDrops: List<String> = emptyList(),
    val grievousWoundSpecs: List<GrievousWoundSpec> = emptyList(), // wound infliction config
    val stages: List<Stage>
)
```
Replace with:
```kotlin
@Serializable
data class Encounter(
    val id: String,
    val bandId: String,
    val name: String,
    val region: String,
    val difficulty: String,         // "easy" | "medium" | "hard" | "boss"
    val recLevel: Int,
    val requiredCookingLevel: Int,
    val flavorIntro: String,
    val flavorLine: String,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardMultiplier: Int,
    val durationMs: Long,
    val rewardTable: List<String>,
    val enemyCategory: String? = null, // "orc"|"dragon"|"shadow"|"wraith"|"nature"|null — bane-affinity target, inert until weapons exist (master-design.md §6.9)
    val grimoireDrops: List<String> = emptyList(),
    val grievousWoundSpecs: List<GrievousWoundSpec> = emptyList(), // wound infliction config
    val stages: List<Stage>
)
```

- [ ] **Step 2: Tag the 7 encounters in `encounters.json`**

For each of the 7 encounters below, find the `"rewardTable"` line shown and add the
`"enemyCategory"` line immediately after it.

`mithlost_goblins` — find:
```json
    "rewardTable": ["cave_truffle", "ironbark_resin", "moonpetal", "stormcap"],
    "stages": [
```
Replace with:
```json
    "rewardTable": ["cave_truffle", "ironbark_resin", "moonpetal", "stormcap"],
    "enemyCategory": "orc",
    "stages": [
```

`undermarch_goblins` — find:
```json
    "rewardTable": ["cave_truffle", "ironbark_resin", "pale_cap", "stormcap"],
    "stages": [
```
Replace with:
```json
    "rewardTable": ["cave_truffle", "ironbark_resin", "pale_cap", "stormcap"],
    "enemyCategory": "orc",
    "stages": [
```

`greycloaks_goblins` — find:
```json
    "rewardTable": ["wolf_moss", "marshwort", "thornberry", "road_herb"],
    "grievousWoundSpecs": [
      { "woundType": "physical", "guaranteed": true },
```
Replace with:
```json
    "rewardTable": ["wolf_moss", "marshwort", "thornberry", "road_herb"],
    "enemyCategory": "orc",
    "grievousWoundSpecs": [
      { "woundType": "physical", "guaranteed": true },
```

`greycloaks_barrow_wight` — find:
```json
    "rewardTable": ["athelas", "meadowsweet", "willowherb", "yarrow"],
    "grievousWoundSpecs": [
      { "woundType": "corruption", "guaranteed": true },
```
Replace with:
```json
    "rewardTable": ["athelas", "meadowsweet", "willowherb", "yarrow"],
    "enemyCategory": "wraith",
    "grievousWoundSpecs": [
      { "woundType": "corruption", "guaranteed": true },
```

`undermarch_drakeling` — find:
```json
    "rewardTable": ["stone_honey", "cave_truffle", "deep_sage", "ironfoot_shroom"],
    "stages": [
```
Replace with:
```json
    "rewardTable": ["stone_honey", "cave_truffle", "deep_sage", "ironfoot_shroom"],
    "enemyCategory": "dragon",
    "stages": [
```

`mithlost_large_spider` — find:
```json
    "rewardTable": ["ironbark_resin", "moonpetal", "cave_truffle", "tidal_kelp"],
    "stages": [
```
Replace with:
```json
    "rewardTable": ["ironbark_resin", "moonpetal", "cave_truffle", "tidal_kelp"],
    "enemyCategory": "shadow",
    "stages": [
```

`mithlost_huorn` — find:
```json
    "rewardTable": ["ironbark_resin", "moonpetal", "sunpetal_herb", "cave_truffle"],
    "stages": [
```
Replace with:
```json
    "rewardTable": ["ironbark_resin", "moonpetal", "sunpetal_herb", "cave_truffle"],
    "enemyCategory": "nature",
    "stages": [
```

**Note:** several of these `"rewardTable"` array lines and immediately-following lines are
not unique across the whole file (other encounters may have similarly-shaped lines) — use
each encounter's `"id"` field (found via grep for `"id": "mithlost_goblins"` etc.) to
locate the correct block first, then apply the edit within that block specifically.

- [ ] **Step 3: Verify no other encounter accidentally matches the same find text**

Before editing, confirm each `"rewardTable"` line above is unique to its target encounter:
```bash
grep -n '"rewardTable": \["cave_truffle", "ironbark_resin", "moonpetal", "stormcap"\]' app/src/main/assets/data/encounters.json
grep -n '"rewardTable": \["cave_truffle", "ironbark_resin", "pale_cap", "stormcap"\]' app/src/main/assets/data/encounters.json
grep -n '"rewardTable": \["wolf_moss", "marshwort", "thornberry", "road_herb"\]' app/src/main/assets/data/encounters.json
grep -n '"rewardTable": \["athelas", "meadowsweet", "willowherb", "yarrow"\]' app/src/main/assets/data/encounters.json
grep -n '"rewardTable": \["stone_honey", "cave_truffle", "deep_sage", "ironfoot_shroom"\]' app/src/main/assets/data/encounters.json
grep -n '"rewardTable": \["ironbark_resin", "moonpetal", "cave_truffle", "tidal_kelp"\]' app/src/main/assets/data/encounters.json
grep -n '"rewardTable": \["ironbark_resin", "moonpetal", "sunpetal_herb", "cave_truffle"\]' app/src/main/assets/data/encounters.json
```
Expected: exactly one line number each.

- [ ] **Step 4: Full build**

```bash
export JAVA_HOME=/usr/share/pycharm/jbr
./gradlew build
```
Expected: BUILD SUCCESSFUL — this confirms the JSON is still valid and deserializes correctly (a bad edit here would show up as a JSON parse or missing-field crash surfaced by existing data-loading tests/build, not silently).

- [ ] **Step 5: Verify all 16 encounters still deserialize correctly**

```bash
python3 -c "
import json
data = json.load(open('app/src/main/assets/data/encounters.json'))
tagged = {'mithlost_goblins':'orc','undermarch_goblins':'orc','greycloaks_goblins':'orc','greycloaks_barrow_wight':'wraith','undermarch_drakeling':'dragon','mithlost_large_spider':'shadow','mithlost_huorn':'nature'}
assert len(data) == 16, f'expected 16 encounters, got {len(data)}'
for e in data:
    expected = tagged.get(e['id'])
    actual = e.get('enemyCategory')
    assert actual == expected, f\"{e['id']}: expected {expected}, got {actual}\"
print('OK: all 16 encounters have the expected enemyCategory value')
"
```
Expected: `OK: all 16 encounters have the expected enemyCategory value`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Encounter.kt app/src/main/assets/data/encounters.json
git commit -m "$(cat <<'EOF'
[hc] Tag 7 encounters with an enemy category for the future bane system

Adds Encounter.enemyCategory (nullable, defaults to null) and tags
orc/dragon/shadow/wraith/nature on the 7 current encounters that fit
the fantasy-creature taxonomy from master-design.md §6.9. The other 9
(beasts, and human/dwarf hostile factions) get no category. Inert in
this change — nothing reads the field yet; it's forward-compatible
data for when the gear/bane-affinity system gets built.
EOF
)"
```
