# V2 Encounter Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the simplified buff-strength mission model with a real encounter engine driven by the validated JSON encounter schema, running the same attrition/survival math as `tools/sim/run_sim.js`, and rewrite all band/mission/encounter content to match the canonical three-band, three-region design.

**Architecture:** Three phases. Phase 1 is pure data — rename IDs, rewrite content, no Kotlin changes. Phase 2 wires the Encounter data model into the Android code: new `Encounter`/`Stage` data classes, `EncounterRepository`, updated `GameDataRepository`, `EncounterSession` DB entity, and a headless `EncounterEngine` that runs the combat math. Phase 3 replaces `MissionWorker`'s buff-strength logic with the engine, updates the UI (MissionBoard shows encounter params, BandScreen shows provisioning vs. encounter requirements), and increments the DB version.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, Hilt, kotlinx.serialization. No new dependencies needed.

## Global Constraints

- All Kotlin source lives under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- Game data JSON lives under `app/src/main/assets/data/`
- Three bands only: `mithlost`, `undermarch`, `greycloaks`. No `corsair_fleet`/`dwarven_company`/`druid_circle`.
- Band IDs, member IDs, and encounter IDs must use snake_case throughout
- No internet requirement; all encounter resolution runs on-device
- GPL-3.0 — no proprietary libraries
- Min SDK API 26
- DB version must be incremented (currently 5 → 6) with a migration
- `missions.json` is renamed to `encounters.json` per the locked vocabulary in `docs/design.md`
- `GameDataRepository.missions` is renamed to `GameDataRepository.encounters`
- All encounter math constants must match `tools/sim/run_sim.js` exactly (RESCUE_CAP=5, WARD_CAP=3, GRIEVOUS=5, PEN_SCALE=80, SHADOW_FLOOR=0.55, RMAX=50)
- Player title is [PLACEHOLDER] — use "the provisioner" in all UI strings and comments

---

## Phase 1: Content and Data Rewrite (pure JSON + BandSelectionScreen)

No Kotlin model changes yet. Goal: correct canonical content compiles and loads.

---

### Task 1: Rename band IDs and rewrite bands.json

**Files:**
- Modify: `app/src/main/assets/data/bands.json`

**What changes:** `druid_circle` → `mithlost`, `dwarven_company` → `undermarch`. Remove Kingswake entirely. Update regions to canonical starting regions from `docs/design.md`. Remove "Hearthkeeper"/"Provender" flavor notes.

- [ ] **Step 1: Rewrite bands.json**

```json
[
  {
    "id": "mithlost",
    "name": "The Mithlost",
    "region": "Celondim, western Ered Luin",
    "description": "Ancient, grey, lingering. The ones who stayed to fight a long defeat when others sailed West. Sorrowful, faithful, deadlier than their gentleness suggests. Their missions feel like stewardship and a rearguard action against encroaching shadow.",
    "flavorNote": "They do not give you a title. What they give you is their trust."
  },
  {
    "id": "undermarch",
    "name": "The Undermarch",
    "region": "Thorin's Halls, Blue Mountains",
    "description": "Grim, loyal, stone-deep. Words mostly unnecessary. They will not complain about a hard march, but they notice — and remember — who kept them fed. Their missions feel like deep delves and the defense of ancient holds.",
    "flavorNote": "Among dwarves, feeding someone well is how you say you trust them."
  },
  {
    "id": "greycloaks",
    "name": "The Greycloaks",
    "region": "Bree-land",
    "description": "The Dúnedain of the North — heirs to a kingdom that fell and was never rebuilt. They walk the wild lands of Eriador not as wanderers but as guardians bound by an oath older than the Shire. They have no home because their home is gone. They wait for a king who has not yet come.",
    "flavorNote": "They do not give you a title. What they give you is their trust — and among the Dúnedain, that is rarer and worth more."
  }
]
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/assets/data/bands.json
git commit -m "[v1] Data: rewrite bands.json — three bands, canonical regions, no Kingswake"
```

---

### Task 2: Rewrite band_members.json with correct bandIds

**Files:**
- Modify: `app/src/main/assets/data/band_members.json`

**What changes:** `druid_circle` → `mithlost` on all four Mithlost members; `dwarven_company` → `undermarch` on all four Undermarch members. No content changes to members themselves — just IDs.

- [ ] **Step 1: Update all bandId fields**

Replace every `"bandId": "druid_circle"` with `"bandId": "mithlost"`.
Replace every `"bandId": "dwarven_company"` with `"bandId": "undermarch"`.
No other changes.

- [ ] **Step 2: Verify member count**

`mithlost`: aelindra, thalindel, galadorn, caranthir (4 members) ✓
`undermarch`: borin_ironmantle, dagra_copperhelm, keldra, thrain_deepvein (4 members) ✓
`greycloaks`: aldric, mira, cael, hollis (4 members) ✓

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/data/band_members.json
git commit -m "[v1] Data: update band member bandIds to mithlost/undermarch"
```

---

### Task 3: Rewrite BandSelectionScreen — remove Kingswake, fix band IDs

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandSelectionScreen.kt`

**What changes:** Remove `corsair_fleet` branch from `welcomeFor()`. Update `druid_circle` → `mithlost`, `dwarven_company` → `undermarch`. Update opening lore text (already says "Three companies" — confirm it's correct).

- [ ] **Step 1: Update welcomeFor()**

```kotlin
private fun welcomeFor(bandId: String): Pair<String, String> = when (bandId) {
    "mithlost" -> Pair(
        "We have kept our own counsel for a long time. That you are here suggests something has shifted. We will see what you are made of before we say more.",
        "Aelindra"
    )
    "undermarch" -> Pair(
        "You cook. We fight. Keep the food coming and we'll have nothing to argue about.",
        "Borin Ironmantle"
    )
    "greycloaks" -> Pair(
        "The borderlands do not forgive the unprepared. Keep us fed. We'll do the rest.",
        "Aldric"
    )
    else -> Pair("Welcome.", "")
}
```

- [ ] **Step 2: Verify opening lore text reads "Three companies" (line 110) — it already does from the previous fix.**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/BandSelectionScreen.kt
git commit -m "[v1] UI: BandSelectionScreen — remove Kingswake, fix band IDs to mithlost/undermarch"
```

---

### Task 4: Rewrite encounters.json — three per band (Rung 0a, 0b, Rung 1)

**Files:**
- Create: `app/src/main/assets/data/encounters.json`
- Delete: `app/src/main/assets/data/missions.json` (renamed — handled in Task 8 with GameDataRepository)

**What this produces:** Nine encounters total. Each band has:
- Easy: their Rung 0a swarm fight (midges / cave bats / spiders)
- Medium: their Rung 0b spike-heavy fight (wolves / mountain wolves / wargs)
- Hard: their Rung 1 armored fight (goblin incursion, per-band flavor)

**Encounter parameters are lifted directly from `docs/combat-model.md` validated values.** The `recLevel` and `requiredCookingLevel` fields gate unlock. `bandFlavor` sub-object provides per-band name/flavorIntro for the goblin fight.

> **Key design point:** `missions.json` used `bandId` + `difficulty` + `vitalityRequired` + `requiredBuffStrength` to gate access. The new `encounters.json` uses `bandId` + `difficulty` + `recLevel` (band combat level gate) + `requiredCookingLevel` (food tier gate). The V1-style `requiredBuffStrength` field is **dropped** — it was a placeholder for this system. The encounter engine itself determines success/failure from food stats vs. combat parameters.

- [ ] **Step 1: Create encounters.json**

```json
[
  {
    "id": "mithlost_midges",
    "bandId": "mithlost",
    "name": "Midges at the Forest Margin",
    "region": "Celondim woodland edge, Ered Luin",
    "difficulty": "easy",
    "recLevel": 1,
    "requiredCookingLevel": 1,
    "flavorIntro": "The forest margin has been unpassable at dusk for three weeks. A biting, clicking swarm thick enough to turn back a horse. The elves know this ground and can drive them back — if they are fed.",
    "flavorLine": "Drive them back before nightfall. They know this ground.",
    "rewardMoneyMin": 15,
    "rewardMoneyMax": 25,
    "rewardMultiplier": 1,
    "durationMs": 1500000,
    "rewardTable": ["moonpetal", "wanderer_fig", "stormcap"],
    "stages": [
      {
        "stageId": "main",
        "label": "Midge Cull",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 40000,
        "drain": 16,
        "spike": 75,
        "spikeIntervalSec": 13,
        "physMitPct": 0,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  },
  {
    "id": "mithlost_wargs",
    "bandId": "mithlost",
    "name": "Wargs on the Mountain Slopes",
    "region": "Ered Luin high passes, above Celondim",
    "difficulty": "medium",
    "recLevel": 3,
    "requiredCookingLevel": 5,
    "flavorIntro": "A pack of wargs has come down from the high passes and is ranging the slopes above Celondim. Bold, organized, not ordinary wolves. A well-provisioned company can drive them back.",
    "flavorLine": "Not wolves. Wargs. Hearthkeeper food won't hold them.",
    "rewardMoneyMin": 40,
    "rewardMoneyMax": 60,
    "rewardMultiplier": 2,
    "durationMs": 1500000,
    "rewardTable": ["ironbark_resin", "moonpetal", "stormcap", "cave_truffle"],
    "stages": [
      {
        "stageId": "main",
        "label": "Warg Pack",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 60000,
        "drain": 18,
        "spike": 75,
        "spikeIntervalSec": 9,
        "physMitPct": 0,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  },
  {
    "id": "mithlost_goblins",
    "bandId": "mithlost",
    "name": "Goblin Scouts at the Forest Margin",
    "region": "Ered Luin, western woodland edge",
    "difficulty": "hard",
    "recLevel": 5,
    "requiredCookingLevel": 5,
    "flavorIntro": "Goblins probing down from the mountain passes above Celondim. They have been watching the tree-line for three nights. They are armored in scavenged plate — not the rabble you expected.",
    "flavorLine": "Armored. Organized. Hearthkeeper food will not carry your band through this.",
    "rewardMoneyMin": 100,
    "rewardMoneyMax": 150,
    "rewardMultiplier": 4,
    "durationMs": 1500000,
    "rewardTable": ["cave_truffle", "ironbark_resin", "moonpetal", "stormcap"],
    "stages": [
      {
        "stageId": "main",
        "label": "Goblin Incursion",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 68000,
        "drain": 20,
        "spike": 60,
        "spikeIntervalSec": 14,
        "physMitPct": 35,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  },
  {
    "id": "undermarch_bats",
    "bandId": "undermarch",
    "name": "Cave Bats in the Lower Halls",
    "region": "Thorin's Halls, Blue Mountains — lower tunnels",
    "difficulty": "easy",
    "recLevel": 1,
    "requiredCookingLevel": 1,
    "flavorIntro": "The lower access tunnels are impassable. A colony of cave bats, disturbed by recent stonework, has swarmed and blocked the passage. Dense, disorienting, hard to fight in the dark. A fed band can push through.",
    "flavorLine": "Tight, dark, and loud. Nothing they haven't handled before.",
    "rewardMoneyMin": 15,
    "rewardMoneyMax": 25,
    "rewardMultiplier": 1,
    "durationMs": 1500000,
    "rewardTable": ["pale_cap", "cave_truffle", "stormcap"],
    "stages": [
      {
        "stageId": "main",
        "label": "Bat Colony",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 40000,
        "drain": 16,
        "spike": 75,
        "spikeIntervalSec": 13,
        "physMitPct": 0,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  },
  {
    "id": "undermarch_wolves",
    "bandId": "undermarch",
    "name": "Mountain Wolves on the Outer Passes",
    "region": "Blue Mountains, passes above Thorin's Halls",
    "difficulty": "medium",
    "recLevel": 3,
    "requiredCookingLevel": 5,
    "flavorIntro": "The outer passes have been dangerous since the last snowmelt. A pack of mountain wolves, lean and territorial, has been raiding supply routes. They move at night and do not yield without a fight.",
    "flavorLine": "Mountain wolves. They don't scare easily and they don't leave until they're made to.",
    "rewardMoneyMin": 40,
    "rewardMoneyMax": 60,
    "rewardMultiplier": 2,
    "durationMs": 1500000,
    "rewardTable": ["cave_truffle", "stormcap", "pale_cap", "ironbark_resin"],
    "stages": [
      {
        "stageId": "main",
        "label": "Wolf Pack",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 60000,
        "drain": 18,
        "spike": 75,
        "spikeIntervalSec": 9,
        "physMitPct": 0,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  },
  {
    "id": "undermarch_goblins",
    "bandId": "undermarch",
    "name": "Goblin Assault on the Outer Gate",
    "region": "Blue Mountains, Thorin's Halls approach",
    "difficulty": "hard",
    "recLevel": 5,
    "requiredCookingLevel": 5,
    "flavorIntro": "Mountain goblins, mail-clad and organized, hit the outer approach to Thorin's Halls before dawn. Not a raid — a test of the defenses. Your band answers the alarm.",
    "flavorLine": "Armored. They know what they're doing. Your band needs to eat before they answer this.",
    "rewardMoneyMin": 100,
    "rewardMoneyMax": 150,
    "rewardMultiplier": 4,
    "durationMs": 1500000,
    "rewardTable": ["cave_truffle", "ironbark_resin", "pale_cap", "stormcap"],
    "stages": [
      {
        "stageId": "main",
        "label": "Goblin Assault",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 68000,
        "drain": 20,
        "spike": 60,
        "spikeIntervalSec": 14,
        "physMitPct": 35,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  },
  {
    "id": "greycloaks_neekerbreekers",
    "bandId": "greycloaks",
    "name": "Neekerbreekers at the Marsh's Edge",
    "region": "Northern Midgewater, east of Staddle",
    "difficulty": "easy",
    "recLevel": 1,
    "requiredCookingLevel": 1,
    "flavorIntro": "A hobbit farmer outside Staddle has had enough. The neekerbreekers have been at the marsh-edge crops for three seasons running — a writhing mass of clicking, biting insects. Clear them out, and the farmer will sleep easy.",
    "flavorLine": "They know this ground. Should be back by nightfall — if they're fed.",
    "rewardMoneyMin": 15,
    "rewardMoneyMax": 25,
    "rewardMultiplier": 1,
    "durationMs": 1500000,
    "rewardTable": ["moonpetal", "duskberry", "stormcap"],
    "stages": [
      {
        "stageId": "main",
        "label": "Neekerbreeker Cull",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 40000,
        "drain": 16,
        "spike": 75,
        "spikeIntervalSec": 13,
        "physMitPct": 0,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  },
  {
    "id": "greycloaks_wolves",
    "bandId": "greycloaks",
    "name": "Wolves in the Chetwood",
    "region": "Chetwood, east of Bree",
    "difficulty": "medium",
    "recLevel": 3,
    "requiredCookingLevel": 5,
    "flavorIntro": "Yellow eyes ring the firelight. The pack has trailed the band since dusk, and hunger has made them bold.",
    "flavorLine": "They've been circling since dusk. Hearthkeeper food won't hold them.",
    "rewardMoneyMin": 40,
    "rewardMoneyMax": 60,
    "rewardMultiplier": 2,
    "durationMs": 1500000,
    "rewardTable": ["ironbark_resin", "moonpetal", "cave_truffle", "duskberry"],
    "stages": [
      {
        "stageId": "main",
        "label": "Wolf Pack",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 60000,
        "drain": 18,
        "spike": 75,
        "spikeIntervalSec": 9,
        "physMitPct": 0,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  },
  {
    "id": "greycloaks_goblins",
    "bandId": "greycloaks",
    "name": "Goblin Raiders on the East Road",
    "region": "The Lone-lands, East Road",
    "difficulty": "hard",
    "recLevel": 5,
    "requiredCookingLevel": 5,
    "flavorIntro": "A goblin warband has come down from the hills and is working the stretch of road east of Weathertop. They have been raiding for days. Your band intercepts them at dusk.",
    "flavorLine": "Armored raiders. They've been at this for days. The band needs to be ready.",
    "rewardMoneyMin": 100,
    "rewardMoneyMax": 150,
    "rewardMultiplier": 4,
    "durationMs": 1500000,
    "rewardTable": ["cave_truffle", "ironbark_resin", "moonpetal", "stormcap"],
    "stages": [
      {
        "stageId": "main",
        "label": "Goblin Raiders",
        "type": "attrition",
        "objective": "kill",
        "durationSec": 1500,
        "resolve": 68000,
        "drain": 20,
        "spike": 60,
        "spikeIntervalSec": 14,
        "physMitPct": 35,
        "dread": 0, "shadow": 0, "disease": 0,
        "cold": 0, "heat": 0, "wakefulness": 0
      }
    ]
  }
]
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/assets/data/encounters.json
git commit -m "[v1] Data: add encounters.json — nine encounters, three bands, three rungs each"
```

---

## Phase 2: Kotlin Data Model + Engine

Goal: `Encounter`, `Stage`, `EncounterRepository`, `EncounterEngine`, and `EncounterSession` DB entity all compile and pass unit tests. No UI changes yet. `missions.json` still exists and still loads — nothing is broken mid-phase.

---

### Task 5: Encounter and Stage data model

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Encounter.kt`

**Interfaces:**
- Produces: `Encounter`, `Stage` — used by EncounterRepository, EncounterEngine, BandViewModel

- [ ] **Step 1: Create Encounter.kt**

```kotlin
package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Stage(
    val stageId: String,
    val label: String,
    val type: String,           // "attrition" | "survival"
    val objective: String,      // "kill" | "survive" | "retrieve"
    val durationSec: Int,
    val resolve: Int = 0,
    val drain: Float,
    val spike: Float,
    val spikeIntervalSec: Int,
    val physMitPct: Float,
    val dread: Int = 0,
    val shadow: Int = 0,
    val disease: Int = 0,
    val cold: Int = 0,
    val heat: Int = 0,
    val wakefulness: Int = 0,
    val stageFlavor: String = ""
)

@Serializable
data class Encounter(
    val id: String,
    val bandId: String,
    val name: String,
    val region: String,
    val difficulty: String,         // "easy" | "medium" | "hard"
    val recLevel: Int,
    val requiredCookingLevel: Int,
    val flavorIntro: String,
    val flavorLine: String,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardMultiplier: Int,
    val durationMs: Long,
    val rewardTable: List<String>,
    val stages: List<Stage>
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/model/Encounter.kt
git commit -m "[v2] Model: add Encounter and Stage data classes"
```

---

### Task 6: EncounterEngine — the headless combat resolver

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt`

**Interfaces:**
- Consumes: `Encounter.stages[0]` (single-stage for V1), `FoodInput` (per-member HP/s and stat bonuses), `PartyInput` (per-member stats at their current level)
- Produces: `EncounterResult(outcome: Outcome, wounds: Map<String,Int>, rescuesUsed: Int, wardGuardsUsed: Int, resolveRemaining: Float)` where `Outcome` is `VICTORY | DEFEAT | STALEMATE`

This is a direct port of the math in `tools/sim/run_sim.js`. Key constants must match exactly.

- [ ] **Step 1: Create EncounterEngine.kt**

```kotlin
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// All constants must match tools/sim/run_sim.js exactly
private const val PEN_SCALE    = 80f
private const val SHADOW_FLOOR = 0.55f
private const val SHADOW_RATE  = 0.0011f
private const val RESCUE_CAP   = 5
private const val WARD_CAP     = 3
private const val GRIEVOUS     = 5
private const val RMAX         = 50f
private const val JITTER       = 0.10f

enum class Outcome { VICTORY, DEFEAT, STALEMATE }

data class MemberInput(
    val id: String,
    val role: String,       // "warden"|"hunter"|"keeper"|"captain"
    val might: Float,
    val agility: Float,
    val vitality: Float,
    val will: Float,
    val fate: Float,
    val hps: Float,         // food HP/s for this member
    val draughtPotency: Float = 0f
)

data class EncounterResult(
    val outcome: Outcome,
    val woundsByMember: Map<String, Int>,
    val rescuesUsed: Int,
    val wardGuardsUsed: Int,
    val resolveRemainingFraction: Float  // 0.0 = killed, 1.0 = untouched
)

object EncounterEngine {

    fun resolve(stage: Stage, members: List<MemberInput>, seed: Long = System.nanoTime()): EncounterResult {
        val rng = Random(seed)
        val physMit = stage.physMitPct / 100f
        val draughtPotency = members.firstOrNull()?.draughtPotency ?: 0f
        val effArmor = physMit * (1f - min(1f, draughtPotency / PEN_SCALE))

        // Member state
        data class MS(
            val input: MemberInput,
            var hp: Float = morale(input),
            val maxHp: Float = morale(input),
            var reserve: Float = 0f,
            var wounds: Int = 0,
            var grievous: Boolean = false
        )

        val party = members.map { MS(it) }.toMutableList()
        val standing  = { party.filter { !it.grievous && it.hp > 0 } }
        val active    = { party.filter { !it.grievous } }

        var boss = stage.resolve.toFloat()
        var rescues = 0
        var wardGuards = 0
        var nextSpike = (stage.spikeIntervalSec * rng.nextDouble(0.5, 1.5)).toFloat()
        val warden = party.find { it.input.role == "warden" }
        val keeper = party.find { it.input.role == "keeper" }

        for (t in 1..stage.durationSec) {
            // ── Food healing ──────────────────────────────────────────────────
            for (m in active()) {
                val heal = m.input.hps
                val overflow = max(0f, (m.hp + heal) - m.maxHp)
                m.hp = min(m.maxHp, m.hp + heal)
                m.reserve = min(RMAX, m.reserve + overflow)
            }

            // ── DPS against boss ──────────────────────────────────────────────
            val rawDps = standing().sumOf { rawDps(it.input).toDouble() }.toFloat()
            val jMul = 1f + rng.nextFloat() * 2 * JITTER - JITTER
            val effDps = rawDps * (1f - effArmor) * jMul
            boss -= effDps
            if (boss <= 0f) {
                return EncounterResult(
                    Outcome.VICTORY,
                    party.associate { it.input.id to it.wounds },
                    rescues, wardGuards, 0f
                )
            }

            // ── Drain ─────────────────────────────────────────────────────────
            val standingCount = max(1, standing().size)
            val drainPerMember = stage.drain / standingCount.toFloat()
            for (m in standing()) {
                applyDamage(m, drainPerMember)
            }

            // ── Spike ─────────────────────────────────────────────────────────
            if (t >= nextSpike) {
                val spikeRoll = stage.spike * rng.nextFloat(0.7f, 1.3f)
                val standingList = standing()
                if (standingList.isNotEmpty()) {
                    val target = standingList.random(rng)
                    val isKeeperTarget = target.input.role == "keeper"
                    val wardenCanGuard = warden != null && !warden.grievous && warden.hp > 0 &&
                        warden.input.id != target.input.id && wardGuards < WARD_CAP

                    if (isKeeperTarget && wardenCanGuard && wouldDown(target, spikeRoll)) {
                        // Warden intercepts killing blow on Keeper
                        applyDamage(warden!!, spikeRoll)
                        wardGuards++
                    } else {
                        applyDamage(target, spikeRoll)
                    }
                }
                nextSpike = t + (stage.spikeIntervalSec * rng.nextDouble(0.5, 1.5)).toFloat()
            }

            // ── Keeper rescue ─────────────────────────────────────────────────
            if (keeper != null && !keeper.grievous && keeper.hp > 0 && rescues < RESCUE_CAP) {
                val downed = active().filter { it.hp <= 0 && it.input.id != keeper.input.id }
                if (downed.isNotEmpty()) {
                    val rescued = downed.first()
                    rescued.hp = 40f + keeper.input.will * 4f
                    rescues++
                }
            }

            // ── Check defeat ──────────────────────────────────────────────────
            if (standing().isEmpty()) {
                return EncounterResult(
                    Outcome.DEFEAT,
                    party.associate { it.input.id to it.wounds },
                    rescues, wardGuards,
                    boss / stage.resolve
                )
            }
        }

        // Duration expired
        val finalOutcome = if (boss <= 0f) Outcome.VICTORY else Outcome.STALEMATE
        return EncounterResult(
            finalOutcome,
            party.associate { it.input.id to it.wounds },
            rescues, wardGuards,
            max(0f, boss / stage.resolve)
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun morale(m: MemberInput): Float = (30f + m.vitality * 16f).roundToInt().toFloat()

    private fun rawDps(m: MemberInput): Float = when (m.role) {
        "warden"  -> m.might * 0.5f
        "hunter"  -> m.agility + m.might * 0.4f
        "keeper"  -> m.will * 0.9f
        "captain" -> m.might * 0.3f + m.will * 0.2f
        else      -> 0f
    }

    private fun wouldDown(m: MS, damage: Float): Boolean =
        (m.hp - max(0f, damage - m.reserve)) <= 0f

    private fun applyDamage(m: MS, damage: Float) {
        val absorbedByReserve = min(m.reserve, damage)
        m.reserve -= absorbedByReserve
        val remainder = damage - absorbedByReserve
        if (remainder > 0f && m.hp > 0f) {
            val wasAbove = m.hp > 0f
            m.hp -= remainder
            if (wasAbove && m.hp <= 0f) {
                m.wounds++
                if (m.wounds >= GRIEVOUS) m.grievous = true
            }
        }
    }

    private fun Random.nextFloat(lo: Float, hi: Float): Float =
        lo + nextFloat() * (hi - lo)
}
```

- [ ] **Step 2: Write a unit test to verify VICTORY when food is strong**

Create `app/src/test/java/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt`:

```kotlin
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

class EncounterEngineTest {

    private val neekerbreekerStage = Stage(
        stageId = "main", label = "test", type = "attrition",
        objective = "kill", durationSec = 1500, resolve = 40000,
        drain = 16f, spike = 75f, spikeIntervalSec = 13, physMitPct = 0f
    )

    private fun party(hps: Float) = listOf(
        MemberInput("warden",  "warden",  13f, 6f,  15f, 7f,  6f,  hps),
        MemberInput("hunter",  "hunter",  9f,  15f, 7f,  5f,  9f,  hps),
        MemberInput("keeper",  "keeper",  5f,  7f,  9f,  15f, 12f, hps),
        MemberInput("captain", "captain", 8f,  7f,  12f, 13f, 13f, hps)
    )

    @Test
    fun `high food guarantees victory in neekerbreekers`() {
        // FL4 food (5.6 HP/s) should win ~91% — run 200 times, expect majority wins
        var wins = 0
        repeat(200) { seed ->
            val result = EncounterEngine.resolve(neekerbreekerStage, party(5.6f), seed.toLong())
            if (result.outcome == Outcome.VICTORY) wins++
        }
        assert(wins > 150) { "Expected >75% wins at FL4, got ${wins}/200" }
    }

    @Test
    fun `no food results in defeat in neekerbreekers`() {
        // 0 HP/s — band bleeds out every time
        var defeats = 0
        repeat(50) { seed ->
            val result = EncounterEngine.resolve(neekerbreekerStage, party(0f), seed.toLong())
            if (result.outcome == Outcome.DEFEAT) defeats++
        }
        assertEquals("Expected all defeats with no food", 50, defeats)
    }

    @Test
    fun `goblin armor blocks kill without draught`() {
        val goblinStage = Stage(
            stageId = "main", label = "test", type = "attrition",
            objective = "kill", durationSec = 1500, resolve = 68000,
            drain = 20f, spike = 60f, spikeIntervalSec = 14, physMitPct = 35f
        )
        // FL5 food (6.0 HP/s), no draught — expect stalemate or defeat, never victory
        var victories = 0
        repeat(100) { seed ->
            val result = EncounterEngine.resolve(goblinStage, party(6.0f), seed.toLong())
            if (result.outcome == Outcome.VICTORY) victories++
        }
        assert(victories < 10) { "Expected almost no victories without draught, got $victories/100" }
    }
}
```

- [ ] **Step 3: Run tests** (Android unit tests run on the JVM, no device needed)

```
./gradlew :app:testDebugUnitTest --tests "*.EncounterEngineTest" 2>&1 | tail -20
```
Expected: 3 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt
git add app/src/test/java/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt
git commit -m "[v2] Engine: add EncounterEngine — headless combat resolver ported from run_sim.js"
```

---

### Task 7: EncounterRepository and GameDataRepository update

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/EncounterRepository.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt`

**Interfaces:**
- Consumes: `Encounter` from Task 5
- Produces: `EncounterRepository.forBand(bandId: String): List<Encounter>`, `EncounterRepository.get(encounterId: String): Encounter?`

- [ ] **Step 1: Update GameDataRepository to load encounters.json**

```kotlin
@Singleton
class GameDataRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    val bands: List<Band> by lazy { load("bands.json") }
    val bandMembers: List<BandMember> by lazy { load("band_members.json") }
    val ingredients: List<Ingredient> by lazy { load("ingredients.json") }
    val recipes: List<Recipe> by lazy { load("recipes.json") }
    val encounters: List<Encounter> by lazy { load("encounters.json") }

    // Keep missions as a shim until MissionWorker is updated in Task 9
    @Deprecated("Use encounters instead")
    val missions: List<Mission> by lazy { load("missions.json") }

    private inline fun <reified T> load(filename: String): List<T> =
        json.decodeFromString(context.assets.open("data/$filename").bufferedReader().readText())
}
```

- [ ] **Step 2: Create EncounterRepository.kt**

```kotlin
package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.model.Encounter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncounterRepository @Inject constructor(
    private val gameData: GameDataRepository
) {
    fun forBand(bandId: String): List<Encounter> =
        gameData.encounters.filter { it.bandId == bandId }

    fun get(encounterId: String): Encounter? =
        gameData.encounters.find { it.id == encounterId }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/EncounterRepository.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt
git commit -m "[v2] Repository: EncounterRepository + GameDataRepository loads encounters.json"
```

---

### Task 8: EncounterSession DB entity and migration

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/EncounterSession.kt`
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/EncounterSessionDao.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt`
- Create: `app/src/main/assets/databases/` — migration SQL file (Room auto-migration)

**What changes:** Add `EncounterSession` table (replaces `MissionSession` over time; both coexist in version 6). Bump DB version 5 → 6. Add `draughtPotency` column to `EncounterSession` so the player's draught choice is persisted through the WorkManager job.

- [ ] **Step 1: Create EncounterSession.kt**

```kotlin
package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encounter_session")
data class EncounterSession(
    @PrimaryKey val bandId: String,
    val encounterId: String,
    val draughtPotency: Float = 0f,
    val startedAtMs: Long,
    val durationMs: Long,
    val workRequestId: String
)
```

- [ ] **Step 2: Create EncounterSessionDao.kt**

```kotlin
package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.EncounterSession
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: EncounterSession)

    @Query("SELECT * FROM encounter_session WHERE bandId = :bandId LIMIT 1")
    fun observe(bandId: String): Flow<EncounterSession?>

    @Query("SELECT * FROM encounter_session WHERE bandId = :bandId LIMIT 1")
    suspend fun get(bandId: String): EncounterSession?

    @Query("DELETE FROM encounter_session WHERE bandId = :bandId")
    suspend fun clear(bandId: String)
}
```

- [ ] **Step 3: Update HearthCraftDatabase.kt**

```kotlin
@Database(
    entities = [
        PlayerState::class,
        InventoryItem::class,
        PreparedFood::class,
        GatheringSession::class,
        CookingSession::class,
        MissionSession::class,
        EncounterSession::class,   // NEW
        BandMemberState::class,
        SeedStock::class,
        GrowingSlot::class,
    ],
    version = 6,                   // bumped from 5
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 5, to = 6)]
)
abstract class HearthCraftDatabase : RoomDatabase() {
    abstract fun playerStateDao(): PlayerStateDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun preparedFoodDao(): PreparedFoodDao
    abstract fun gatheringSessionDao(): GatheringSessionDao
    abstract fun cookingSessionDao(): CookingSessionDao
    abstract fun missionSessionDao(): MissionSessionDao
    abstract fun encounterSessionDao(): EncounterSessionDao   // NEW
    abstract fun bandMemberStateDao(): BandMemberStateDao
    abstract fun seedStockDao(): SeedStockDao
    abstract fun growingSlotDao(): GrowingSlotDao
}
```

- [ ] **Step 4: Wire EncounterSessionDao into DatabaseModule.kt**

Add to the `@Module` object in `DatabaseModule.kt`:
```kotlin
@Provides fun provideEncounterSessionDao(db: HearthCraftDatabase): EncounterSessionDao = db.encounterSessionDao()
```

- [ ] **Step 5: Add EncounterSessionDao to SessionRepository**

In `SessionRepository.kt`, inject `EncounterSessionDao` and add:
```kotlin
fun observeEncounter(bandId: String): Flow<EncounterSession?> = encounterDao.observe(bandId)
suspend fun activeEncounter(bandId: String): EncounterSession? = encounterDao.get(bandId)
suspend fun startEncounter(session: EncounterSession) = encounterDao.upsert(session)
suspend fun clearEncounter(bandId: String) = encounterDao.clear(bandId)
```

- [ ] **Step 6: Verify build compiles**

```
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/EncounterSession.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/dao/EncounterSessionDao.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/db/HearthCraftDatabase.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/di/DatabaseModule.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/SessionRepository.kt
git commit -m "[v2] DB: EncounterSession entity, DAO, migration v5→v6"
```

---

## Phase 3: Replace MissionWorker + Wire UI

Goal: MissionWorker uses EncounterEngine to resolve fights. BandViewModel and MissionBoardScreen updated to use `Encounter` not `Mission`. Build passes. App is playable.

---

### Task 9: EncounterWorker — replaces MissionWorker's buff-strength logic

**Files:**
- Create: `app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/PlayerRepository.kt` (no change needed — XP grants already exist)

**What this does:** EncounterWorker loads the encounter, builds `MemberInput` list from current band stats (via `BandRepository`), calls `EncounterEngine.resolve()`, applies rewards/wounds exactly as MissionWorker did, fires notification.

- [ ] **Step 1: Create EncounterWorker.kt**

```kotlin
package com.liquidcode7.hearthcraft.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.MainActivity
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import com.liquidcode7.hearthcraft.data.repository.EncounterRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.engine.EncounterEngine
import com.liquidcode7.hearthcraft.engine.MemberInput
import com.liquidcode7.hearthcraft.engine.Outcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class EncounterWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val encounters: EncounterRepository,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val sessions: SessionRepository,
    private val band: BandRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val encounterId  = inputData.getString(KEY_ENCOUNTER_ID) ?: return Result.failure()
        val bandId       = inputData.getString(KEY_BAND_ID) ?: return Result.failure()
        val draughtPotency = inputData.getFloat(KEY_DRAUGHT_POTENCY, 0f)
        val hpsWarden    = inputData.getFloat(KEY_HPS_WARDEN, 0f)
        val hpsHunter    = inputData.getFloat(KEY_HPS_HUNTER, 0f)
        val hpsKeeper    = inputData.getFloat(KEY_HPS_KEEPER, 0f)
        val hpsCaptain   = inputData.getFloat(KEY_HPS_CAPTAIN, 0f)

        val encounter = encounters.get(encounterId) ?: return Result.failure()
        val stage = encounter.stages.firstOrNull() ?: return Result.failure()

        // Build MemberInput list from live stats
        val memberStates = band.memberInputsForBand(bandId, draughtPotency, listOf(hpsWarden, hpsHunter, hpsKeeper, hpsCaptain))
        if (memberStates.isEmpty()) return Result.failure()

        val result = EncounterEngine.resolve(stage, memberStates, Random.nextLong())

        when (result.outcome) {
            Outcome.VICTORY -> {
                val multiplier = encounter.rewardMultiplier
                val money = (encounter.rewardMoneyMin..encounter.rewardMoneyMax).random() * multiplier
                player.addMoney(money)
                val rewardCount = minOf(3, (1..3).random() + (multiplier - 1))
                encounter.rewardTable.shuffled().take(rewardCount).forEach {
                    inventory.addIngredient(it, 1)
                }
                player.addCookingXp(PlayerRepository.XP_COOK_WIN)
                player.addGatheringXp(PlayerRepository.XP_GATHER_WIN)
                band.grantMissionStats(bandId, succeeded = true)
                notify("Mission Complete", "${encounter.name} — your band has returned.")
            }
            Outcome.STALEMATE -> {
                // Band survived but didn't kill — no rewards, no wounds beyond what happened
                band.grantMissionStats(bandId, succeeded = false)
                notify("No Result", "${encounter.name} — the band held but couldn't finish it.")
            }
            Outcome.DEFEAT -> {
                applyWounds(result.woundsByMember, bandId)
                band.grantMissionStats(bandId, succeeded = false)
                notify("Mission Failed", "${encounter.name} — your band did not prevail.")
            }
        }

        sessions.clearEncounter(bandId)
        return Result.success()
    }

    private suspend fun applyWounds(woundsByMember: Map<String, Int>, bandId: String) {
        woundsByMember.forEach { (memberId, wounds) ->
            when {
                wounds >= 5 -> band.killMember(memberId)
                wounds >= 3 -> band.woundMember(memberId, grievous = true)
                wounds >= 1 -> band.woundMember(memberId, grievous = false)
            }
        }
    }

    private fun notify(title: String, text: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, HearthCraftApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    companion object {
        const val KEY_ENCOUNTER_ID    = "encounterId"
        const val KEY_BAND_ID         = "bandId"
        const val KEY_DRAUGHT_POTENCY = "draughtPotency"
        const val KEY_HPS_WARDEN      = "hpsWarden"
        const val KEY_HPS_HUNTER      = "hpsHunter"
        const val KEY_HPS_KEEPER      = "hpsKeeper"
        const val KEY_HPS_CAPTAIN     = "hpsCaptain"
        const val NOTIFICATION_ID     = 3

        fun buildRequest(
            encounterId: String,
            bandId: String,
            draughtPotency: Float,
            hps: List<Float>,  // [warden, hunter, keeper, captain]
            durationMs: Long
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EncounterWorker>()
                .setInputData(workDataOf(
                    KEY_ENCOUNTER_ID    to encounterId,
                    KEY_BAND_ID         to bandId,
                    KEY_DRAUGHT_POTENCY to draughtPotency,
                    KEY_HPS_WARDEN      to (hps.getOrElse(0) { 5f }),
                    KEY_HPS_HUNTER      to (hps.getOrElse(1) { 5f }),
                    KEY_HPS_KEEPER      to (hps.getOrElse(2) { 5f }),
                    KEY_HPS_CAPTAIN     to (hps.getOrElse(3) { 5f })
                ))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
```

- [ ] **Step 2: Add `memberInputsForBand()` to BandRepository**

```kotlin
// Add to BandRepository.kt
fun memberInputsForBand(
    bandId: String,
    draughtPotency: Float,
    hpsList: List<Float>  // ordered: warden, hunter, keeper, captain
): List<MemberInput> {
    val roleOrder = listOf("warden", "hunter", "keeper", "captain")
    return gameData.bandMembers
        .filter { it.bandId == bandId }
        .sortedBy { roleOrder.indexOf(it.role.lowercase()) }
        .mapIndexed { i, member ->
            val state = runBlocking { dao.get(member.id) }
            MemberInput(
                id             = member.id,
                role           = member.role.lowercase(),
                might          = (state?.might  ?: member.startingMight).toFloat(),
                agility        = (state?.agility ?: member.startingAgility).toFloat(),
                vitality       = (state?.vitality ?: member.startingVitality).toFloat(),
                will           = (state?.will    ?: member.startingWill).toFloat(),
                fate           = (state?.fate    ?: member.startingFate).toFloat(),
                hps            = hpsList.getOrElse(i) { 5f },
                draughtPotency = draughtPotency
            )
        }
}
```

> Note: `runBlocking` here is acceptable — `BandRepository` methods are already called from `suspend` contexts and workers. If this causes lint warnings, change the `memberInputsForBand` signature to `suspend` and call it from a coroutine scope in EncounterWorker.

- [ ] **Step 3: Register EncounterWorker with Hilt in the WorkManager configuration**

In `HearthCraftApp.kt` or wherever `HiltWorkerFactory` is wired, no change needed — Hilt auto-discovers `@HiltWorker` classes.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/worker/EncounterWorker.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/BandRepository.kt
git commit -m "[v2] Worker: EncounterWorker replaces buff-strength resolution with EncounterEngine"
```

---

### Task 10: BandViewModel — switch to Encounter, add HP/s and draught inputs

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt`

**What changes:** Replace `missions: StateFlow<List<Mission>>` with `encounters: StateFlow<List<Encounter>>`. Add `draughtPotency: StateFlow<Float>` and `setDraught(potency: Float)`. Replace `sendOnMission()` with `sendOnEncounter()` that builds a `EncounterWorker` request, computes per-member HP/s from the selected food's `buffStrength` (V1 approximation: all members get the same HP/s from the selected food item — full per-member provisioning is V2 polish). Inject `EncounterRepository`.

- [ ] **Step 1: Add `EncounterDetail` to UiModels.kt**

```kotlin
// Add to UiModels.kt
data class EncounterDetail(
    val encounterId: String,
    val name: String,
    val difficulty: String,
    val recLevel: Int,
    val requiredCookingLevel: Int,
    val flavorLine: String,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardMultiplier: Int,
    val physMitPct: Float,  // from stage[0] — used to show "brings a draught" hint
    val isUnlocked: Boolean // recLevel <= band's max vitality AND cookingLevel >= requiredCookingLevel
)
```

- [ ] **Step 2: Update BandViewModel**

Key changes — replace the `missions` StateFlow and `sendOnMission()`:

```kotlin
// Inject EncounterRepository
@HiltViewModel
class BandViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val encounterRepo: EncounterRepository,
    private val band: BandRepository,
    private val inventory: InventoryRepository,
    private val sessions: SessionRepository,
    private val player: PlayerRepository
) : ViewModel() {

    // ... existing fields unchanged ...

    val encounters: StateFlow<List<EncounterDetail>> = combine(
        activeBandId, members, cookingLevel
    ) { bandId, memberList, cookLvl ->
        if (bandId == null) return@combine emptyList()
        val maxVit = memberList.filter { it.isAlive }.maxOfOrNull { it.vitality } ?: 0
        encounterRepo.forBand(bandId).map { enc ->
            EncounterDetail(
                encounterId          = enc.id,
                name                 = enc.name,
                difficulty           = enc.difficulty,
                recLevel             = enc.recLevel,
                requiredCookingLevel = enc.requiredCookingLevel,
                flavorLine           = enc.flavorLine,
                rewardMoneyMin       = enc.rewardMoneyMin,
                rewardMoneyMax       = enc.rewardMoneyMax,
                rewardMultiplier     = enc.rewardMultiplier,
                physMitPct           = enc.stages.firstOrNull()?.physMitPct ?: 0f,
                isUnlocked           = maxVit >= enc.recLevel && cookLvl >= enc.requiredCookingLevel
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _draughtPotency = MutableStateFlow(0f)
    val draughtPotency: StateFlow<Float> = _draughtPotency.asStateFlow()

    private val _selectedEncounter = MutableStateFlow<EncounterDetail?>(null)
    val selectedEncounter: StateFlow<EncounterDetail?> = _selectedEncounter.asStateFlow()

    fun setDraught(potency: Float) { _draughtPotency.value = potency }
    fun selectEncounter(detail: EncounterDetail) { _selectedEncounter.value = detail }

    fun sendOnEncounter() {
        val food      = _selectedFood.value ?: return
        val encounter = _selectedEncounter.value ?: return
        val bandId    = activeBandId.value ?: return
        val enc       = encounterRepo.get(encounter.encounterId) ?: return
        viewModelScope.launch {
            if (sessions.activeEncounter(bandId) != null) return@launch
            // V1: all members get same HP/s from the food's buffStrength
            // Full per-member provisioning is a V2 polish task
            val hps = food.buffStrength.toFloat() / 10f  // scale: buffStrength 50 → 5.0 HP/s
            val hpsList = listOf(hps, hps, hps, hps)
            val request = EncounterWorker.buildRequest(
                encounterId    = encounter.encounterId,
                bandId         = bandId,
                draughtPotency = _draughtPotency.value,
                hps            = hpsList,
                durationMs     = enc.durationMs
            )
            WorkManager.getInstance(context).enqueue(request)
            sessions.startEncounter(
                com.liquidcode7.hearthcraft.data.db.EncounterSession(
                    bandId         = bandId,
                    encounterId    = encounter.encounterId,
                    draughtPotency = _draughtPotency.value,
                    startedAtMs    = System.currentTimeMillis(),
                    durationMs     = enc.durationMs,
                    workRequestId  = request.id.toString()
                )
            )
            inventory.removePreparedFood(food.recipeId)
            _selectedFood.value = null
            _selectedEncounter.value = null
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/BandViewModel.kt
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/viewmodel/UiModels.kt
git commit -m "[v2] ViewModel: BandViewModel switches to Encounter, adds draughtPotency, sendOnEncounter"
```

---

### Task 11: MissionBoardScreen → EncounterBoardScreen

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionBoardScreen.kt`

**What changes:** Read `encounters` from BandViewModel instead of `missions`. Show locked/unlocked state. Show physMit hint ("Bring a potency draught") when `physMitPct > 0`. Show draught selector (simple row of potency buttons: None / 45 / 65). Call `sendOnEncounter()` instead of `sendOnMission()`. Keep the same visual structure — no major layout changes.

- [ ] **Step 1: Rewrite MissionBoardScreen.kt**

```kotlin
package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.EncounterDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionBoardScreen(
    onBack: () -> Unit,
    bandViewModel: BandViewModel = hiltViewModel()
) {
    val encounters by bandViewModel.encounters.collectAsState()
    val selectedEncounter by bandViewModel.selectedEncounter.collectAsState()
    val selectedFood by bandViewModel.selectedFood.collectAsState()
    val draughtPotency by bandViewModel.draughtPotency.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mission Board") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (encounters.isEmpty()) {
                Text(
                    "No encounters available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                encounters.forEach { enc ->
                    EncounterCard(
                        encounter = enc,
                        isSelected = enc.encounterId == selectedEncounter?.encounterId,
                        onClick = { if (enc.isUnlocked) bandViewModel.selectEncounter(enc) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Draught selector — only shown if an armored encounter is selected
            if (selectedEncounter?.physMitPct ?: 0f > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Draught", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0f to "None", 45f to "Entry (45)", 65f to "Mid (65)").forEach { (potency, label) ->
                        FilterChip(
                            selected = draughtPotency == potency,
                            onClick = { bandViewModel.setDraught(potency) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Send button
            if (selectedEncounter != null && selectedFood != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { bandViewModel.sendOnEncounter() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send — ${selectedEncounter?.name}")
                }
            }
        }
    }
}

@Composable
private fun EncounterCard(
    encounter: EncounterDetail,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (difficultyLabel, difficultyColor) = when (encounter.difficulty) {
        "easy"   -> "Routine"     to Color(0xFF4CAF50)
        "medium" -> "Challenging" to Color(0xFFFF9800)
        "hard"   -> "Dangerous"   to MaterialTheme.colorScheme.error
        else     -> encounter.difficulty to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alpha = if (encounter.isUnlocked) 1f else 0.4f

    Card(
        onClick = onClick,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary
        ) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    encounter.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = difficultyColor.copy(alpha = alpha))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    difficultyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = difficultyColor.copy(alpha = alpha)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                encounter.flavorLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (!encounter.isUnlocked) {
                Text(
                    "Requires band level ${encounter.recLevel}, cooking level ${encounter.requiredCookingLevel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (encounter.physMitPct > 0f) {
                    Text(
                        "Armored foes — bring a potency draught",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Reward: ${encounter.rewardMoneyMin * encounter.rewardMultiplier}–${encounter.rewardMoneyMax * encounter.rewardMultiplier} gold",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**

```
./gradlew :app:assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/liquidcode7/hearthcraft/ui/screen/MissionBoardScreen.kt
git commit -m "[v2] UI: MissionBoardScreen reads Encounter, draught selector, locked/unlocked state"
```

---

### Task 12: Final cleanup — delete missions.json, delete deprecated shim

**Files:**
- Delete: `app/src/main/assets/data/missions.json`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/data/repository/GameDataRepository.kt` (remove `@Deprecated missions` field)
- Delete: `app/src/main/assets/data/encounters/goblin_scouts.json` and all other standalone encounter JSON files in `encounters/` — they are superseded by `encounters.json`

> The old `encounters/` folder contained sim-output JSON files with the old schema. The canonical data is now `encounters.json`. Keep the `tools/sim/` folder encounter files — they are the sim's input, not the game's.

- [ ] **Step 1: Remove missions.json and the deprecated shim**

```bash
git rm app/src/main/assets/data/missions.json
```

Remove from `GameDataRepository.kt`:
```kotlin
// DELETE these lines:
@Deprecated("Use encounters instead")
val missions: List<Mission> by lazy { load("missions.json") }
```

- [ ] **Step 2: Remove old encounters/ standalone JSONs from assets (not tools/sim)**

```bash
git rm app/src/main/assets/data/encounters/goblin_scouts.json
git rm app/src/main/assets/data/encounters/neekerbreekers_midgewater.json
git rm app/src/main/assets/data/encounters/wolves_chetwood.json
# (and any other json files in that folder if they exist)
```

> Keep `app/src/main/assets/data/encounters/` folder only if it's used by another loader. If nothing else references it, remove the directory too.

- [ ] **Step 3: Verify nothing references `missions` or the deleted files**

```bash
grep -r "missions" app/src/main/kotlin --include="*.kt" | grep -v "MissionSession\|MissionWorker\|missionId\|missionSession"
```
Expected: no results (or only the deprecated `MissionWorker` which can stay for now).

- [ ] **Step 4: Final build + test**

```
./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "[v2] Cleanup: remove missions.json, old encounter JSONs, deprecated shim"
```

---

## Self-Review

**Spec coverage check:**
- ✅ Band IDs renamed: mithlost/undermarch/greycloaks (Task 1–3)
- ✅ Kingswake removed everywhere (Tasks 1, 3)
- ✅ Starting regions in bands.json (Task 1)
- ✅ flavorNote rewritten, "Provender" gone (Task 1)
- ✅ Nine encounters with per-band flavor (Task 4)
- ✅ Encounter data model (Task 5)
- ✅ Combat engine ported from run_sim.js (Task 6)
- ✅ EncounterRepository (Task 7)
- ✅ DB migration v5→v6 (Task 8)
- ✅ EncounterWorker with real combat resolution (Task 9)
- ✅ BandViewModel uses Encounter + draught (Task 10)
- ✅ MissionBoardScreen updated (Task 11)
- ✅ missions.json deleted, old encounter JSONs deleted (Task 12)
- ✅ "Three companies" lore text already correct in BandSelectionScreen

**Items deferred to future tasks (not in scope here, log to wishlist):**
- Per-member food provisioning (currently all members get same HP/s from one food item — full provisioning puzzle is V2 polish)
- `MissionWorker` can be deleted once `MissionSession` is fully replaced — left alive because existing active sessions from before the upgrade would otherwise orphan. Remove in a follow-up cleanup once all sessions have expired.
- STALEMATE notification text is a placeholder — tune before ship
- Draught potency values (None/45/65) are hardcoded — needs a draught item system in V3
