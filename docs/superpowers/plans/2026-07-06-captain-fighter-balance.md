# Captain/Fighter DPS Balance & Captain HoT Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `docs/superpowers/specs/2026-07-06-captain-fighter-balance-design.md` — buff Captain's DPS coefficients (was structurally the floor, below Warden), fix an unrelated Fighter melee/ranged symmetry bug found while deriving target numbers, add a new small Captain HoT ability, and first resync the balance-sim tools (`tools/sim/run_sim.js` and its HTML-inlined copy), which turned out to already be stale relative to the shipped engine on every role, not just Captain.

**Architecture:** Five tasks, mostly sequential because they all touch the same two files (`EncounterEngine.kt` and its sim mirrors). Task 1 resyncs the sim tools to match today's *shipping* formulas — this must happen before Tasks 2-4 change those formulas, so there's a clean "before" baseline. Tasks 2, 3, and 4 each change one piece of `EncounterEngine.kt` (Captain coefficients, Fighter coefficient, new Captain HoT) — kept as separate tasks since a reviewer could accept one while rejecting another. Task 5 mirrors all three changes into the now-resynced sim tools, per `EncounterEngine.kt`'s own "must match run_sim.js exactly" comment.

**Tech Stack:** Kotlin, JUnit, vanilla JavaScript (no build step — `run_sim.js` is a Node CLI script, `hearthcraft_fight_sim.html` inlines an equivalent copy for the browser)

## Global Constraints

- All Kotlin source lives under `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- Commit messages prefixed `[hc]`
- Run `./gradlew build` before every commit that touches Kotlin — never commit a broken build (`export JAVA_HOME=/usr/share/pycharm/jbr` first)
- Do not bump `versionName`/`versionCode`
- GPL-3.0
- Every constant/formula change to `EncounterEngine.kt` must be mirrored into `tools/sim/run_sim.js` **and** the inlined copy in `tools/sim/hearthcraft_fight_sim.html` — this file's own top-of-file comment states "All constants must match tools/sim/run_sim.js exactly," and the two sim copies must match each other too (confirmed: they currently do, byte-for-byte on every constant checked in this plan).

---

### Task 1: Resync run_sim.js and hearthcraft_fight_sim.html to match today's shipping formulas

**Context:** While deriving target numbers for the Captain/Fighter buff, found that both sim tools have drifted from `EncounterEngine.kt` on every role's `rawDps`, not just Captain's. This must be fixed to the **current, pre-buff, actually-shipping** values first — Tasks 2-4 change `EncounterEngine.kt` itself, and Task 5 mirrors those *new* values in. This task only fixes the pre-existing drift so Task 5 has a correct baseline to build on.

| Constant | `EncounterEngine.kt` (real, shipping) | Sim tools (stale, both files identical) |
|---|---|---|
| Warden `rawDps` | `might × 1.5` | `might × 0.5` |
| Fighter `rawDps` | `agility × 3 + might × 1.2` | `agility × 1 + might × 0.4` |
| Keeper `rawDps` | `will × 2.7` | `will × 0.9` |
| Captain `rawDps` | `might × 0.9 + will × 0.6` | `might × 0.3 + will × 0.2` |
| `GROUP_HEAL_IV` | `5` | `20` |
| `GROUP_HEAL_MUL` | `1.5` | `0.5` |

**Files:**
- Modify: `tools/sim/run_sim.js`
- Modify: `tools/sim/hearthcraft_fight_sim.html`

**Interfaces:** None — pure data/constant fix, no new functions, no signature changes. Nothing downstream depends on the *old* stale values (this is a bug fix, not a behavior anyone relies on).

- [ ] **Step 1: Fix `GROUP_HEAL_IV`/`GROUP_HEAL_MUL` in run_sim.js**

  Find (around line 55-56):
  ```js
  const GROUP_HEAL_IV   = 20;   // ticks between group heals
  const GROUP_HEAL_MUL  = 0.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL
  ```
  Replace with:
  ```js
  const GROUP_HEAL_IV   = 5;    // ticks between group heals — resynced to match EncounterEngine.kt (was stale at 20)
  const GROUP_HEAL_MUL  = 1.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL — resynced (was stale at 0.5)
  ```

- [ ] **Step 2: Fix the four role `rawDps` coefficients in run_sim.js's `dpsBreakdown()`**

  Find (around line 211-214):
  ```js
      if (k==="keeper")        { if (keeperDealtDps) base = M[k].wil * 0.9; }
      else if (k==="fighter")  base = M[k].agi + M[k].mig * 0.4;
      else if (k==="warden")   base = M[k].mig * 0.5;
      else if (k==="captain")  base = M[k].mig * 0.3 + M[k].wil * 0.2;
  ```
  Replace with:
  ```js
      if (k==="keeper")        { if (keeperDealtDps) base = M[k].wil * 2.7; }
      else if (k==="fighter")  base = M[k].agi * 3 + M[k].mig * 1.2;
      else if (k==="warden")   base = M[k].mig * 1.5;
      else if (k==="captain")  base = M[k].mig * 0.9 + M[k].wil * 0.6;
  ```

- [ ] **Step 3: Fix the same in hearthcraft_fight_sim.html's `GROUP_HEAL_IV`/`GROUP_HEAL_MUL`**

  Find (around line 779-780):
  ```js
  const GROUP_HEAL_IV   = 20;   // ticks between group heals
  const GROUP_HEAL_MUL  = 0.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL
  ```
  Replace with:
  ```js
  const GROUP_HEAL_IV   = 5;    // ticks between group heals — resynced to match EncounterEngine.kt (was stale at 20)
  const GROUP_HEAL_MUL  = 1.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL — resynced (was stale at 0.5)
  ```

- [ ] **Step 4: Fix the same four coefficients in hearthcraft_fight_sim.html's `fightDpsBreakdown()`**

  Find (around line 844-851):
  ```js
      if (k==="keeper")        { if (S.keeperDealtDps) base = m.wil * 0.9; }
      else if (k==="fighter")  base = m.agi + m.mig * 0.4;
      else if (k==="warden")   base = m.mig * 0.5;
      else if (k==="captain")  {
        // Captain's damage is a physical (Mig-driven)/magic (Wil-driven) hybrid — armor
        // only mitigates the physical half, so the split must survive into rawBy.
        rawBy.captain_phys += m.mig*0.3*mult; rawBy.captain_magic += m.wil*0.2*mult;
        base = m.mig * 0.3 + m.wil * 0.2;
      }
  ```
  Replace with:
  ```js
      if (k==="keeper")        { if (S.keeperDealtDps) base = m.wil * 2.7; }
      else if (k==="fighter")  base = m.agi * 3 + m.mig * 1.2;
      else if (k==="warden")   base = m.mig * 1.5;
      else if (k==="captain")  {
        // Captain's damage is a physical (Mig-driven)/magic (Wil-driven) hybrid — armor
        // only mitigates the physical half, so the split must survive into rawBy.
        rawBy.captain_phys += m.mig*0.9*mult; rawBy.captain_magic += m.wil*0.6*mult;
        base = m.mig * 0.9 + m.wil * 0.6;
      }
  ```

- [ ] **Step 5: Sanity-check with a manual run**

  ```bash
  node tools/sim/run_sim.js --duration 300 --boss 20000 --drain 4 --spike 25 --spikeiv 12 --verbose
  ```
  Expected: script runs without error; DPS breakdown numbers are visibly ~3x higher for Warden/Fighter/Keeper than they would have been before this change (sanity check only — no assertion needed, there's no automated test harness for these JS files, consistent with how Task 4 of `docs/superpowers/plans/2026-07-06-kitchen-grimoire-forage-balance.md` verified its own formula resync).

- [ ] **Step 6: Commit**

  ```bash
  git add tools/sim/run_sim.js tools/sim/hearthcraft_fight_sim.html
  git commit -m "$(cat <<'EOF'
  [hc] Sim: resync Warden/Fighter/Keeper/Captain rawDps and group-heal constants to match EncounterEngine.kt

  Found while deriving target numbers for the Captain/Fighter balance
  pass: both sim tools (run_sim.js and hearthcraft_fight_sim.html's
  inlined copy) had drifted from the shipping engine on every role's
  rawDps coefficient (uniformly 1/3 of the real value) plus
  GROUP_HEAL_IV (20 vs 5) and GROUP_HEAL_MUL (0.5 vs 1.5). This resyncs
  to today's actual shipping formulas only — the upcoming Captain/
  Fighter buff changes land in a later commit, mirrored into these
  same files once they're a correct baseline to mirror onto.
  EOF
  )"
  ```

---

### Task 2: Captain damage buff + physicalFraction update

**Context:** `EncounterEngine.kt`'s `rawDps("captain")` is `might × 0.9f + will × 0.6f` — the lowest coefficients of any role, structurally below even Warden. Confirmed via `tools/sim/run_sim.js`'s stat tables at level 5: Captain 16.8 vs. Warden 22.5, Keeper 46.4, Fighter 51-64. Buff to symmetric `2.0f`/`2.0f` coefficients lands Captain at ~47.2 — even with Keeper, below Fighter, above Warden. Because the coefficients become equal, `physicalFraction("captain")` (which currently hardcodes the same `0.9f`/`0.6f` split) should reuse the same two constants rather than duplicate literals, so the two never drift independently again.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt`

**Interfaces:**
- `rawDps(m: MemberInput): Float` changes visibility from `private` to `internal` in this task (needed so this task's and Task 3's tests can call it directly — `physicalFraction` already made this same trade for the same reason; matches the file's own precedent).
- Produces: `CAPTAIN_MIGHT_COEF`, `CAPTAIN_WILL_COEF` (both `2.0f`) as new private constants — Task 4 does not need these (its HoT scales off `will` alone via its own new constant), but keep the names in mind so Task 4 doesn't collide with them.

- [ ] **Step 1: Write the failing tests**

  In `EncounterEngineTest.kt`, replace the existing test (it hardcodes the old coefficients and must change) — find:
  ```kotlin
      @Test
      fun `physicalFraction for captain is stat-weighted between might and will terms`() {
          val captain = MemberInput("c", "captain", 3f, 2f, 4f, 5f, 4f)
          // physTerm = 3*0.9 = 2.7, magicTerm = 5*0.6 = 3.0, total = 5.7
          val expected = (3f * 0.9f) / (3f * 0.9f + 5f * 0.6f)
          val actual = EncounterEngine.physicalFraction(captain)
          assert(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
      }
  ```
  Replace with:
  ```kotlin
      @Test
      fun `physicalFraction for captain is stat-weighted between might and will terms`() {
          val captain = MemberInput("c", "captain", 3f, 2f, 4f, 5f, 4f)
          // CAPTAIN_MIGHT_COEF = CAPTAIN_WILL_COEF = 2.0f: physTerm = 3*2.0 = 6.0, magicTerm = 5*2.0 = 10.0, total = 16.0
          val expected = (3f * 2.0f) / (3f * 2.0f + 5f * 2.0f)
          val actual = EncounterEngine.physicalFraction(captain)
          assert(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
      }

      @Test
      fun `captain rawDps is symmetric between might and will`() {
          val mightHeavy = MemberInput("c1", "captain", 10f, 2f, 4f, 3f, 4f)
          val willHeavy  = MemberInput("c2", "captain", 3f, 2f, 4f, 10f, 4f)
          assert(kotlin.math.abs(EncounterEngine.rawDps(mightHeavy) - EncounterEngine.rawDps(willHeavy)) < 0.0001f) {
              "Expected might=10/will=3 and might=3/will=10 captains to deal equal DPS (symmetric coefficients)"
          }
      }

      @Test
      fun `captain rawDps uses the buffed symmetric coefficients`() {
          val captain = MemberInput("c", "captain", 3f, 2f, 4f, 5f, 4f)
          val expected = 3f * 2.0f + 5f * 2.0f  // CAPTAIN_MIGHT_COEF = CAPTAIN_WILL_COEF = 2.0f
          val actual = EncounterEngine.rawDps(captain)
          assert(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
      }
  ```

- [ ] **Step 2: Run tests to verify the new ones fail**

  ```bash
  export JAVA_HOME=/usr/share/pycharm/jbr
  ./gradlew test --tests "*.EncounterEngineTest"
  ```
  Expected: compilation failure — `EncounterEngine.rawDps` is not visible from the test (still `private`), and the updated `physicalFraction` test fails against the old `0.9f`/`0.6f` implementation.

- [ ] **Step 3: Add the new constants**

  Find (in `EncounterEngine.kt`, near the other role-tuning constants — right before the `rawDps` function, around line 584):
  ```kotlin
      // ── Top-level helpers (no MS dependency) ─────────────────────────────────

      private fun morale(m: MemberInput): Float = (30f + m.vitality * 32f).roundToInt().toFloat()

      private fun rawDps(m: MemberInput): Float = when (m.role) {
          "warden"  -> m.might * 1.5f
          "fighter" -> m.agility * 3f + m.might * 1.2f
          "keeper"  -> m.will * 2.7f
          "captain" -> m.might * 0.9f + m.will * 0.6f
          else      -> 0f
      }
  ```
  Replace with:
  ```kotlin
      // ── Top-level helpers (no MS dependency) ─────────────────────────────────

      private fun morale(m: MemberInput): Float = (30f + m.vitality * 32f).roundToInt().toFloat()

      // Captain damage-split constants — equal by design so the physical/magical split
      // (physicalFraction, below) is purely stat-driven with no fixed lean. Must match
      // tools/sim/run_sim.js exactly.
      private const val CAPTAIN_MIGHT_COEF = 2.0f
      private const val CAPTAIN_WILL_COEF  = 2.0f

      internal fun rawDps(m: MemberInput): Float = when (m.role) {
          "warden"  -> m.might * 1.5f
          "fighter" -> m.agility * 3f + m.might * 1.2f
          "keeper"  -> m.will * 2.7f
          "captain" -> m.might * CAPTAIN_MIGHT_COEF + m.will * CAPTAIN_WILL_COEF
          else      -> 0f
      }
  ```

  Note: `rawDps` changes from `private fun` to `internal fun` here — this is intentional (see Interfaces above), matching `physicalFraction`'s existing `internal` visibility one function below it.

- [ ] **Step 4: Update physicalFraction to reuse the same constants**

  Find:
  ```kotlin
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
  Replace with:
  ```kotlin
      internal fun physicalFraction(m: MemberInput): Float = when (m.role) {
          "warden", "fighter" -> 1f
          "keeper" -> 0f
          "captain" -> {
              val physTerm = m.might * CAPTAIN_MIGHT_COEF
              val magicTerm = m.will * CAPTAIN_WILL_COEF
              val total = physTerm + magicTerm
              if (total > 0f) physTerm / total else 1f
          }
          else -> 1f
      }
  ```

- [ ] **Step 5: Run tests to verify they pass**

  ```bash
  ./gradlew test --tests "*.EncounterEngineTest"
  ```
  Expected: PASS, all tests including the three new/updated ones.

- [ ] **Step 6: Run the full test suite to confirm no regressions**

  ```bash
  ./gradlew test
  ```
  Expected: PASS. This is a damage buff to Captain only — existing win-rate threshold tests should, if anything, become easier to clear, not harder (same reasoning `2026-07-05-damage-types-design.md` used for its own Keeper/Captain buff).

- [ ] **Step 7: Full build**

  ```bash
  ./gradlew build
  ```
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt
  git commit -m "$(cat <<'EOF'
  [hc] Buff Captain's DPS coefficients to symmetric 2.0/2.0 (was 0.9/0.6)

  Captain was structurally the DPS floor — even below Warden — per
  real playtest observation. New coefficients land Captain roughly
  even with Keeper, below Fighter, above Warden. Coefficients are now
  equal, so physicalFraction's might/will split becomes purely
  stat-driven (might / (might + will)), giving a might/will-heavy
  Captain build a lever to shift their damage split toward
  armor-piercing (magical) or armor-mitigated (physical).

  rawDps visibility changes private -> internal (matching
  physicalFraction's existing internal visibility) so both this
  task's and the next task's tests can assert on it directly.
  EOF
  )"
  ```

---

### Task 3: Fighter symmetry fix

**Context:** Fighter has two builds (`master-design.md` §5.4 — Ranged: Agility primary/Fate secondary; Melee: Might primary/Fate secondary; both "Pure DPS," meant to be equivalent). Their stat blocks (`tools/sim/run_sim.js`'s `TPL.fighterA`/`TPL.fighterM`) are exact mirror images of each other, but `rawDps("fighter")` weights agility (`×3`) 2.5x higher than might (`×1.2`) — so Ranged's primary stat gets the strong coefficient and Melee's gets the weak one, making Melee ~20% weaker for no design reason (63.8 vs 51.2 at level 5). Fix: a single symmetric coefficient for both stats. Since the two builds' stat blocks are exact mirrors, any symmetric formula produces identical output for both automatically. `2.33f` is calibrated to match the current Ranged output (Wes's call: buff Melee up to meet Ranged, not nerf Ranged down).

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt`

**Interfaces:**
- Consumes: `rawDps`'s `internal` visibility from Task 2 (already landed).
- Produces: `FIGHTER_COEF` (`2.33f`) as a new private constant.

- [ ] **Step 1: Write the failing tests**

  In `EncounterEngineTest.kt`, add these two tests (anywhere after the Captain tests added in Task 2 is fine — e.g. right after `captain rawDps uses the buffed symmetric coefficients`):
  ```kotlin
      @Test
      fun `fighter rawDps is identical for mirror-image might-agility builds`() {
          val ranged = MemberInput("f1", "fighter", 10f, 20f, 3f, 4f, 4f) // might=10, agility=20
          val melee  = MemberInput("f2", "fighter", 20f, 10f, 3f, 4f, 4f) // swapped
          assert(kotlin.math.abs(EncounterEngine.rawDps(ranged) - EncounterEngine.rawDps(melee)) < 0.0001f) {
              "Expected mirror-image might/agility builds to deal equal DPS (symmetric coefficient)"
          }
      }

      @Test
      fun `fighter rawDps uses the resynced FIGHTER_COEF`() {
          val fighter = MemberInput("f", "fighter", 3f, 5f, 3f, 4f, 4f) // matches party()'s fighter
          val expected = 3f * 2.33f + 5f * 2.33f  // FIGHTER_COEF = 2.33f
          val actual = EncounterEngine.rawDps(fighter)
          assert(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
      }
  ```

- [ ] **Step 2: Run tests to verify they fail**

  ```bash
  export JAVA_HOME=/usr/share/pycharm/jbr
  ./gradlew test --tests "*.EncounterEngineTest"
  ```
  Expected: both new tests FAIL against the current asymmetric `agility * 3f + might * 1.2f` formula.

- [ ] **Step 3: Add FIGHTER_COEF and update rawDps**

  Find:
  ```kotlin
      // Captain damage-split constants — equal by design so the physical/magical split
      // (physicalFraction, below) is purely stat-driven with no fixed lean. Must match
      // tools/sim/run_sim.js exactly.
      private const val CAPTAIN_MIGHT_COEF = 2.0f
      private const val CAPTAIN_WILL_COEF  = 2.0f

      internal fun rawDps(m: MemberInput): Float = when (m.role) {
          "warden"  -> m.might * 1.5f
          "fighter" -> m.agility * 3f + m.might * 1.2f
          "keeper"  -> m.will * 2.7f
          "captain" -> m.might * CAPTAIN_MIGHT_COEF + m.will * CAPTAIN_WILL_COEF
          else      -> 0f
      }
  ```
  Replace with:
  ```kotlin
      // Captain damage-split constants — equal by design so the physical/magical split
      // (physicalFraction, below) is purely stat-driven with no fixed lean. Must match
      // tools/sim/run_sim.js exactly.
      private const val CAPTAIN_MIGHT_COEF = 2.0f
      private const val CAPTAIN_WILL_COEF  = 2.0f

      // Fighter damage constant — both builds (Ranged: Agility primary/Fate secondary;
      // Melee: Might primary/Fate secondary) share this single symmetric coefficient so
      // their exact stat-mirror-image builds produce identical output. Must match
      // tools/sim/run_sim.js exactly.
      private const val FIGHTER_COEF = 2.33f

      internal fun rawDps(m: MemberInput): Float = when (m.role) {
          "warden"  -> m.might * 1.5f
          "fighter" -> m.might * FIGHTER_COEF + m.agility * FIGHTER_COEF
          "keeper"  -> m.will * 2.7f
          "captain" -> m.might * CAPTAIN_MIGHT_COEF + m.will * CAPTAIN_WILL_COEF
          else      -> 0f
      }
  ```

- [ ] **Step 4: Run tests to verify they pass**

  ```bash
  ./gradlew test --tests "*.EncounterEngineTest"
  ```
  Expected: PASS, all tests including the two new ones.

- [ ] **Step 5: Run the full test suite to confirm no regressions**

  ```bash
  ./gradlew test
  ```
  Expected: PASS. This buffs the Melee build up to the Ranged build's existing level — should not reduce any win-rate threshold test's outcome (`party()`'s fighter uses `agi=5, might=3`, closer to a Ranged-leaning mix; its output moves from `5*3+3*1.2=18.6` to `(3+5)*2.33=18.64` — nearly unchanged for this specific test party, confirming the resync is safe for existing statistical tests).

- [ ] **Step 6: Full build**

  ```bash
  ./gradlew build
  ```
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt
  git commit -m "$(cat <<'EOF'
  [hc] Fix Fighter melee/ranged asymmetry — single symmetric FIGHTER_COEF

  Fighter's two builds (master-design.md §5.4) have exact mirror-image
  stat blocks (Ranged: Agility primary/Fate secondary; Melee: Might
  primary/Fate secondary) but the old formula (agility*3 + might*1.2)
  weighted agility 2.5x higher than might, making Melee ~20% weaker
  than Ranged for no design reason. FIGHTER_COEF=2.33 applies equally
  to both stats, so mirror-image builds now produce identical output —
  calibrated to match the Ranged build's existing (higher) output.
  EOF
  )"
  ```

---

### Task 4: Captain's new HoT ability

**Context:** Captain gains a small, periodic heal-over-time on top of their (now-buffed) DPS — a minor support layer, deliberately smaller and rarer than Keeper's healing kit, not a second healer. Fires every `CAPTAIN_HOT_INTERVAL` (9) ticks, retargeting fresh each time to whoever is currently the lowest-HP-fraction standing member (mirrors the target-selection Keeper's own HoT-slot-fill logic already uses). Heals `will × CAPTAIN_HOT_MUL` (0.08) per tick for `CAPTAIN_HOT_DURATION` (6) ticks. Free — does not consume Captain's DPS action that tick, mirroring the existing precedent that Keeper applying a *new* HoT slot doesn't cost a DPS tick either. Reuses the existing `TickSnapshot.hotTargets` field (already rendered as a generic "HOT" badge by `BattleInProgressCard` — no UI work needed), whose comment currently says "Keeper heal-over-time" and needs updating since it's no longer Keeper-only.

**Files:**
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt`
- Modify: `app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/TickSnapshot.kt`
- Test: `app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt`

**Interfaces:**
- Consumes: the existing `HoT` data class (`data class HoT(val targetId: String, var ticksLeft: Int, val healPerTick: Float)`, already declared inside `resolve()` for Keeper's two slots) — reused as-is for Captain's single slot, no new type needed.
- Produces: nothing consumed by a later task — this is the last engine-code task. Task 5 mirrors its constants/behavior into the sim tools.

- [ ] **Step 1: Update TickSnapshot's hotTargets and cumHeal comments**

  In `TickSnapshot.kt`, find:
  ```kotlin
      val cumHeal: Map<String, Float> = emptyMap(),     // memberId → cumulative healing delivered as of this tick (keeper only, non-zero)
      val memberReserve: Map<String, Float> = emptyMap(), // memberId → current shield/reserve amount at this tick
      val streakActive: Set<String> = emptySet(),       // member ids currently inside a streak (1.5x DPS/heal) window
      val hotTargets: Set<String> = emptySet(),         // member ids currently receiving a Keeper heal-over-time
  ```
  Replace with:
  ```kotlin
      val cumHeal: Map<String, Float> = emptyMap(),     // memberId → cumulative healing delivered as of this tick (Keeper and/or Captain, non-zero)
      val memberReserve: Map<String, Float> = emptyMap(), // memberId → current shield/reserve amount at this tick
      val streakActive: Set<String> = emptySet(),       // member ids currently inside a streak (1.5x DPS/heal) window
      val hotTargets: Set<String> = emptySet(),         // member ids currently receiving a heal-over-time (Keeper's two HoT slots and/or Captain's HoT)
  ```

- [ ] **Step 2: Write the failing tests**

  In `EncounterEngineTest.kt`, add this test (anywhere after the Fighter tests added in Task 3 is fine):
  ```kotlin
      @Test
      fun `captain HoT fires periodically, heals, and targets the lowest-HP standing ally`() {
          // Captain + a lower-vitality ally: equal flat drain per standing member makes the
          // ally's HP fraction fall faster than Captain's, so the lowest-HP target is
          // unambiguous — no RNG needed to reason about who gets targeted.
          val captain = MemberInput("captain", "captain", 3f, 2f, 6f, 5f, 4f)
          val ally = MemberInput("ally", "warden", 4f, 2f, 2f, 3f, 2f)
          val gentleStage = stage(resolve = 500000, drain = 1f, spike = 0f, spikeIv = 10000)
          val r = EncounterEngine.resolve(gentleStage, listOf(captain, ally), seed = 1L)

          assert((r.healingByMember["captain"] ?: 0f) > 0f) {
              "Expected Captain to have delivered some healing via their HoT over the fight"
          }
          assert((r.damageByMember["captain"] ?: 0f) > 0f) {
              "Expected Captain to have also dealt damage — the HoT must not cost their DPS action"
          }
          val everTargetedAlly = r.snapshots.any { "ally" in it.hotTargets }
          assert(everTargetedAlly) {
              "Expected Captain's HoT to have targeted the lower-vitality ally at some point"
          }
      }
  ```

- [ ] **Step 3: Run tests to verify the new one fails**

  ```bash
  export JAVA_HOME=/usr/share/pycharm/jbr
  ./gradlew test --tests "*.EncounterEngineTest"
  ```
  Expected: FAIL — `r.healingByMember["captain"]` is `0f` (or null) since Captain doesn't heal at all yet.

- [ ] **Step 4: Add the Captain HoT constants**

  Find:
  ```kotlin
  // Keeper healing constants — must match tools/sim/run_sim.js exactly.
  private const val HOT_DURATION    = 8      // ticks per HoT application
  private const val HOT_HEAL_MUL    = 0.15f  // healPerTick = keeper.will × HOT_HEAL_MUL
  private const val TRIAGE_HP       = 0.25f  // triage fires when target hp < maxHp × TRIAGE_HP
  private const val TRIAGE_MUL      = 2.0f   // triageHeal = keeper.will × TRIAGE_MUL
  private const val TRIAGE_COOLDOWN = 2      // 1-tick minimum gap between triage uses (set to 2 → decrements before check)
  private const val GROUP_HEAL_IV   = 5      // ticks between group heals
  private const val GROUP_HEAL_MUL  = 1.5f   // groupHeal per member = keeper.will × GROUP_HEAL_MUL
  ```
  Replace with:
  ```kotlin
  // Keeper healing constants — must match tools/sim/run_sim.js exactly.
  private const val HOT_DURATION    = 8      // ticks per HoT application
  private const val HOT_HEAL_MUL    = 0.15f  // healPerTick = keeper.will × HOT_HEAL_MUL
  private const val TRIAGE_HP       = 0.25f  // triage fires when target hp < maxHp × TRIAGE_HP
  private const val TRIAGE_MUL      = 2.0f   // triageHeal = keeper.will × TRIAGE_MUL
  private const val TRIAGE_COOLDOWN = 2      // 1-tick minimum gap between triage uses (set to 2 → decrements before check)
  private const val GROUP_HEAL_IV   = 5      // ticks between group heals
  private const val GROUP_HEAL_MUL  = 1.5f   // groupHeal per member = keeper.will × GROUP_HEAL_MUL

  // Captain HoT constants — a minor healing utility layered on top of Captain's DPS,
  // deliberately smaller/rarer than Keeper's kit (not a second healer). Free: firing this
  // does not consume Captain's DPS action for the tick. Must match tools/sim/run_sim.js exactly.
  private const val CAPTAIN_HOT_INTERVAL = 9     // ticks between HoT re-targets
  private const val CAPTAIN_HOT_DURATION = 6     // ticks per HoT application
  private const val CAPTAIN_HOT_MUL      = 0.08f // healPerTick = captain.will × CAPTAIN_HOT_MUL
  ```

- [ ] **Step 5: Declare Captain's HoT state alongside Keeper's**

  Find:
  ```kotlin
          // Keeper HoT state — two independent slots
          data class HoT(val targetId: String, var ticksLeft: Int, val healPerTick: Float)
          var hot1: HoT? = null
          var hot2: HoT? = null
          var triageCooldown = 0
          var groupHealTimer = GROUP_HEAL_IV
  ```
  Replace with:
  ```kotlin
          // Keeper HoT state — two independent slots
          data class HoT(val targetId: String, var ticksLeft: Int, val healPerTick: Float)
          var hot1: HoT? = null
          var hot2: HoT? = null
          var triageCooldown = 0
          var groupHealTimer = GROUP_HEAL_IV

          // Captain HoT state — a single independent slot, fully separate from Keeper's
          // two (can coincide on the same target; that's fine, same as Keeper's own two
          // slots already being independent of each other).
          var captainHot: HoT? = null
          var captainHotTimer = CAPTAIN_HOT_INTERVAL
  ```

- [ ] **Step 6: Include captainHot's target in buildSnapshot's hotTargets**

  Find:
  ```kotlin
              hotTargets = setOfNotNull(hot1?.targetId, hot2?.targetId),
  ```
  Replace with:
  ```kotlin
              hotTargets = setOfNotNull(hot1?.targetId, hot2?.targetId, captainHot?.targetId),
  ```

- [ ] **Step 7: Add the Captain HoT tick block**

  Find (right after the existing "Keeper HoT ticks" block, before "Streak updates"):
  ```kotlin
              // ── Keeper HoT ticks ──────────────────────────────────────────────────
              if (keeper != null && !keeper.grievous && keeper.hp > 0) {
                  val keepStreak = streaks[keeper.input.id]
                  val healMult = if (keepStreak != null && keepStreak.active > 0) STREAK_MULT else 1f
                  for (hot in listOfNotNull(hot1, hot2)) {
                      val target = party.find { it.input.id == hot.targetId }
                      if (target != null && !target.grievous && target.hp > 0) {
                          val delivered = applyHeal(target, hot.healPerTick * healMult * target.recoveryBuffMult)
                          healingAcc[keeper.input.id] = (healingAcc[keeper.input.id] ?: 0f) + delivered
                      }
                      hot.ticksLeft--
                  }
                  if (hot1?.ticksLeft == 0) hot1 = null
                  if (hot2?.ticksLeft == 0) hot2 = null
              }

              // ── Streak updates ────────────────────────────────────────────────────
  ```
  Replace with:
  ```kotlin
              // ── Keeper HoT ticks ──────────────────────────────────────────────────
              if (keeper != null && !keeper.grievous && keeper.hp > 0) {
                  val keepStreak = streaks[keeper.input.id]
                  val healMult = if (keepStreak != null && keepStreak.active > 0) STREAK_MULT else 1f
                  for (hot in listOfNotNull(hot1, hot2)) {
                      val target = party.find { it.input.id == hot.targetId }
                      if (target != null && !target.grievous && target.hp > 0) {
                          val delivered = applyHeal(target, hot.healPerTick * healMult * target.recoveryBuffMult)
                          healingAcc[keeper.input.id] = (healingAcc[keeper.input.id] ?: 0f) + delivered
                      }
                      hot.ticksLeft--
                  }
                  if (hot1?.ticksLeft == 0) hot1 = null
                  if (hot2?.ticksLeft == 0) hot2 = null
              }

              // ── Captain HoT (fixed-interval proc, retargets to current lowest-HP standing
              // member; free — does not cost Captain's DPS action this tick) ────────────────
              if (captain != null && !captain.grievous && captain.hp > 0) {
                  captainHot?.let { hot ->
                      val target = party.find { it.input.id == hot.targetId }
                      if (target != null && !target.grievous && target.hp > 0) {
                          val delivered = applyHeal(target, hot.healPerTick * target.recoveryBuffMult)
                          healingAcc[captain.input.id] = (healingAcc[captain.input.id] ?: 0f) + delivered
                      }
                      hot.ticksLeft--
                  }
                  if (captainHot?.ticksLeft == 0) captainHot = null

                  captainHotTimer--
                  if (captainHotTimer <= 0) {
                      val target = standing().minByOrNull { it.hp / it.maxHp }
                      if (target != null) {
                          captainHot = HoT(target.input.id, CAPTAIN_HOT_DURATION, captain.input.will * CAPTAIN_HOT_MUL)
                      }
                      captainHotTimer = CAPTAIN_HOT_INTERVAL
                  }
              }

              // ── Streak updates ────────────────────────────────────────────────────
  ```

- [ ] **Step 8: Run tests to verify they pass**

  ```bash
  ./gradlew test --tests "*.EncounterEngineTest"
  ```
  Expected: PASS, all tests including the new Captain HoT test.

- [ ] **Step 9: Run the full test suite to confirm no regressions**

  ```bash
  ./gradlew test
  ```
  Expected: PASS. This adds healing capacity to Captain — if anything, win-rate threshold tests should become easier to clear, not harder.

- [ ] **Step 10: Full build**

  ```bash
  ./gradlew build
  ```
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Manual verification**

  Launch the app (see the `run` skill if needed), start a mission, and watch the in-progress card during combat: confirm the "HOT" badge (added by the live-combat-visibility work) appears under a member's name periodically even when only Captain (not Keeper) would be responsible for it in a fight where Keeper is occupied elsewhere or absent from the encounter's party composition. Confirm the post-fight recap still shows Captain's total healing contribution alongside their damage.

- [ ] **Step 12: Commit**

  ```bash
  git add app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngine.kt app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/TickSnapshot.kt app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/EncounterEngineTest.kt
  git commit -m "$(cat <<'EOF'
  [hc] Add Captain's periodic HoT (fixed 9-tick interval, free, targets lowest-HP ally)

  A minor support layer on top of Captain's DPS — deliberately smaller
  and rarer than Keeper's healing kit, not a second healer. Retargets
  every 9 ticks to whoever is currently the lowest-HP-fraction standing
  member, heals will x 0.08/tick for 6 ticks, and does not consume
  Captain's DPS action for the tick it fires (same "free" precedent as
  Keeper assigning a new HoT slot). Reuses the existing
  TickSnapshot.hotTargets field/UI badge from the live-combat-visibility
  work — updated its comment since it's no longer Keeper-only.
  EOF
  )"
  ```

---

### Task 5: Mirror the buffed formulas and new Captain HoT into the sim tools

**Context:** Tasks 2-4 changed three things in `EncounterEngine.kt`: Captain's coefficients (0.9/0.6 → 2.0/2.0), Fighter's coefficient (asymmetric → symmetric 2.33), and a new Captain HoT. Task 1 already brought the sim tools to a correct *pre-buff* baseline — this task brings them to the correct *post-buff* state, so `run_sim.js`/`hearthcraft_fight_sim.html` reflect the same game Tasks 2-4 shipped.

**Files:**
- Modify: `tools/sim/run_sim.js`
- Modify: `tools/sim/hearthcraft_fight_sim.html`

**Interfaces:** None — pure data/constant mirror, no new functions.

- [ ] **Step 1: Update Captain/Fighter coefficients in run_sim.js's dpsBreakdown()**

  Find (this is Task 1's Step 2 output):
  ```js
      if (k==="keeper")        { if (keeperDealtDps) base = M[k].wil * 2.7; }
      else if (k==="fighter")  base = M[k].agi * 3 + M[k].mig * 1.2;
      else if (k==="warden")   base = M[k].mig * 1.5;
      else if (k==="captain")  base = M[k].mig * 0.9 + M[k].wil * 0.6;
  ```
  Replace with:
  ```js
      if (k==="keeper")        { if (keeperDealtDps) base = M[k].wil * 2.7; }
      else if (k==="fighter")  base = M[k].agi * 2.33 + M[k].mig * 2.33;
      else if (k==="warden")   base = M[k].mig * 1.5;
      else if (k==="captain")  base = M[k].mig * 2.0 + M[k].wil * 2.0;
  ```

- [ ] **Step 2: Add Captain HoT constants to run_sim.js**

  Find (this is Task 1's Step 1 output):
  ```js
  const GROUP_HEAL_IV   = 5;    // ticks between group heals — resynced to match EncounterEngine.kt (was stale at 20)
  const GROUP_HEAL_MUL  = 1.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL — resynced (was stale at 0.5)
  ```
  Replace with:
  ```js
  const GROUP_HEAL_IV   = 5;    // ticks between group heals — resynced to match EncounterEngine.kt (was stale at 20)
  const GROUP_HEAL_MUL  = 1.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL — resynced (was stale at 0.5)
  const CAPTAIN_HOT_INTERVAL = 9;    // ticks between Captain HoT re-targets — must match EncounterEngine.kt
  const CAPTAIN_HOT_DURATION = 6;    // ticks per Captain HoT application
  const CAPTAIN_HOT_MUL      = 0.08; // healPerTick = captain.wil * CAPTAIN_HOT_MUL
  ```

- [ ] **Step 3: Declare Captain HoT state alongside Keeper's in run_sim.js**

  Find (around line 178-181):
  ```js
    // Keeper HoT state — two independent slots
    let hot1 = null, hot2 = null; // { targetKey, ticksLeft, healPerTick }
    let triageCooldown = 0;
    let groupHealTimer = GROUP_HEAL_IV;
  ```
  Replace with:
  ```js
    // Keeper HoT state — two independent slots
    let hot1 = null, hot2 = null; // { targetKey, ticksLeft, healPerTick }
    let triageCooldown = 0;
    let groupHealTimer = GROUP_HEAL_IV;

    // Captain HoT state — a single independent slot, fully separate from Keeper's two
    let captainHot = null; // { targetKey, ticksLeft, healPerTick }
    let captainHotTimer = CAPTAIN_HOT_INTERVAL;
  ```

- [ ] **Step 4: Add a `captainHot` heal-metric bucket in run_sim.js**

  Find (around line 190):
  ```js
    const healByType        = { hot:0, triage:0, group:0, rescue:0 };
  ```
  Replace with:
  ```js
    const healByType        = { hot:0, triage:0, group:0, rescue:0, captainHot:0 };
  ```

  Find (around line 611):
  ```js
    const totalHealByType    = { hot:0, triage:0, group:0, rescue:0 };
  ```
  Replace with:
  ```js
    const totalHealByType    = { hot:0, triage:0, group:0, rescue:0, captainHot:0 };
  ```

  Find (around line 637):
  ```js
      ["hot","triage","group","rescue"].forEach(t => { totalHealByType[t] += m.healByType[t]; });
  ```
  Replace with:
  ```js
      ["hot","triage","group","rescue","captainHot"].forEach(t => { totalHealByType[t] += m.healByType[t]; });
  ```

  Find (around line 739):
  ```js
    const healLabels = { hot:"HoT", triage:"Triage", group:"Group Heal", rescue:"Rescue Burst" };
  ```
  Replace with:
  ```js
    const healLabels = { hot:"HoT", triage:"Triage", group:"Group Heal", rescue:"Rescue Burst", captainHot:"Captain HoT" };
  ```

- [ ] **Step 5: Add the Captain HoT tick block to run_sim.js**

  Find (right after the existing "Keeper HoT ticks" block, around line 251-269 — the block ends with the two `hot1`/`hot2` nulling lines, immediately followed by the Dawn/Horn window decrements):
  ```js
      if (hot1 && hot1.ticksLeft <= 0) hot1 = null;
      if (hot2 && hot2.ticksLeft <= 0) hot2 = null;
    }
    if (windows.dawn > 0) windows.dawn--;
  ```
  Replace with:
  ```js
      if (hot1 && hot1.ticksLeft <= 0) hot1 = null;
      if (hot2 && hot2.ticksLeft <= 0) hot2 = null;
    }

    // ── Captain HoT (fixed-interval proc, retargets to current lowest-HP standing member;
    // free — does not cost Captain's DPS action this tick) ─────────────────────────────
    if (!M.captain.grievous && M.captain.hp > 0 && !M.captain.stunned) {
      if (captainHot) {
        const tgt = M[captainHot.targetKey];
        if (tgt && !tgt.grievous) {
          const healAmt = captainHot.healPerTick;
          tgt.hp = Math.min(tgt.max, tgt.hp + healAmt);
          healByType.captainHot += healAmt;
        }
        captainHot.ticksLeft--;
      }
      if (captainHot && captainHot.ticksLeft <= 0) captainHot = null;

      captainHotTimer--;
      if (captainHotTimer <= 0) {
        const hotTarget = standing().sort((a,b) => (M[a].hp/M[a].max) - (M[b].hp/M[b].max))[0];
        if (hotTarget) {
          captainHot = { targetKey: hotTarget, ticksLeft: CAPTAIN_HOT_DURATION, healPerTick: M.captain.wil * CAPTAIN_HOT_MUL };
        }
        captainHotTimer = CAPTAIN_HOT_INTERVAL;
      }
    }
    if (windows.dawn > 0) windows.dawn--;
  ```

  Note: `standing()` (defined at line 184 as `const standing = () => active().filter(k => M[k].hp > 0);`) is a plain function with no memoization — calling it fresh here is correct and matches how the Keeper's own retarget logic further down the tick calls `live` (a cached copy of the same function, computed later in the tick — calling `standing()` directly here, earlier in the tick, is equivalent and simpler than restructuring cache timing).

- [ ] **Step 6: Repeat Steps 1-5 for hearthcraft_fight_sim.html**

  This file mirrors run_sim.js closely but prefixes shared fight state with `S.` (a state object returned by `createFightState()`) and uses `fightStanding(S)`/`fightActive(S)` instead of bare `standing()`/`active()`.

  Coefficients (Step 1, repeated) — find (around line 844-851, in `fightDpsBreakdown()`):
  ```js
      if (k==="keeper")        { if (S.keeperDealtDps) base = m.wil * 2.7; }
      else if (k==="fighter")  base = m.agi * 3 + m.mig * 1.2;
      else if (k==="warden")   base = m.mig * 1.5;
      else if (k==="captain")  {
        // Captain's damage is a physical (Mig-driven)/magic (Wil-driven) hybrid — armor
        // only mitigates the physical half, so the split must survive into rawBy.
        rawBy.captain_phys += m.mig*0.9*mult; rawBy.captain_magic += m.wil*0.6*mult;
        base = m.mig * 0.9 + m.wil * 0.6;
      }
  ```
  Replace with:
  ```js
      if (k==="keeper")        { if (S.keeperDealtDps) base = m.wil * 2.7; }
      else if (k==="fighter")  base = m.agi * 2.33 + m.mig * 2.33;
      else if (k==="warden")   base = m.mig * 1.5;
      else if (k==="captain")  {
        // Captain's damage is a physical (Mig-driven)/magic (Wil-driven) hybrid — armor
        // only mitigates the physical half, so the split must survive into rawBy.
        rawBy.captain_phys += m.mig*2.0*mult; rawBy.captain_magic += m.wil*2.0*mult;
        base = m.mig * 2.0 + m.wil * 2.0;
      }
  ```

  Constants — find (around line 779-780, this is Task 1 Step 3's output):
  ```js
  const GROUP_HEAL_IV   = 5;    // ticks between group heals — resynced to match EncounterEngine.kt (was stale at 20)
  const GROUP_HEAL_MUL  = 1.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL — resynced (was stale at 0.5)
  ```
  Replace with:
  ```js
  const GROUP_HEAL_IV   = 5;    // ticks between group heals — resynced to match EncounterEngine.kt (was stale at 20)
  const GROUP_HEAL_MUL  = 1.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL — resynced (was stale at 0.5)
  const CAPTAIN_HOT_INTERVAL = 9;    // ticks between Captain HoT re-targets — must match EncounterEngine.kt
  const CAPTAIN_HOT_DURATION = 6;    // ticks per Captain HoT application
  const CAPTAIN_HOT_MUL      = 0.08; // healPerTick = captain.wil * CAPTAIN_HOT_MUL
  ```

  State init — find (around line 809-823, the object returned by `createFightState()`):
  ```js
    return {
      cfg, M,
      streak: Object.fromEntries(ORDER.map(k=>[k,{refractory:0,active:0}])),
      hot1:null, hot2:null, triageCooldown:0, groupHealTimer:GROUP_HEAL_IV,
      t:0, boss:cfg.boss, maxT:cfg.duration,
      keeperDealtDps:true, rescuesUsed:0, wardsUsed:0, inspBoost:0, baFired:false,
      nextSpikeAt: Math.round(cfg.spikeiv*(0.5+Math.random())),
      fired:{}, windows:{dawn:0,horn:0}, events:[], prevTrouble:0, spikesTotal:0,
      outcome:null, done:false,
      // metrics (Task: sim metrics overhaul — observation only, no effect on simulation)
      dmgByMember:{warden:0,fighter:0,keeper:0,captain:0},
      healByType:{hot:0,triage:0,group:0,rescue:0},
      streakCount:{warden:0,fighter:0,keeper:0,captain:0},
      streakBonusDmg:0, streakBonusHeal:0, hotActiveTicks:0,
      inspGraceHeal:0, inspHornRedirect:0, inspDawnHeal:0, inspDawnBonusDps:0, inspBlackArrowAmount:0
    };
  ```
  Replace with:
  ```js
    return {
      cfg, M,
      streak: Object.fromEntries(ORDER.map(k=>[k,{refractory:0,active:0}])),
      hot1:null, hot2:null, triageCooldown:0, groupHealTimer:GROUP_HEAL_IV,
      captainHot:null, captainHotTimer:CAPTAIN_HOT_INTERVAL,
      t:0, boss:cfg.boss, maxT:cfg.duration,
      keeperDealtDps:true, rescuesUsed:0, wardsUsed:0, inspBoost:0, baFired:false,
      nextSpikeAt: Math.round(cfg.spikeiv*(0.5+Math.random())),
      fired:{}, windows:{dawn:0,horn:0}, events:[], prevTrouble:0, spikesTotal:0,
      outcome:null, done:false,
      // metrics (Task: sim metrics overhaul — observation only, no effect on simulation)
      dmgByMember:{warden:0,fighter:0,keeper:0,captain:0},
      healByType:{hot:0,triage:0,group:0,rescue:0,captainHot:0},
      streakCount:{warden:0,fighter:0,keeper:0,captain:0},
      streakBonusDmg:0, streakBonusHeal:0, hotActiveTicks:0,
      inspGraceHeal:0, inspHornRedirect:0, inspDawnHeal:0, inspDawnBonusDps:0, inspBlackArrowAmount:0
    };
  ```

  Tick block — find (around line 883-901):
  ```js
    // Keeper HoT ticks
    if (!S.M.keeper.grievous && S.M.keeper.hp > 0 && !S.M.keeper.stunned) {
      const keepStreak = S.streak.keeper;
      const healMult = (keepStreak && keepStreak.active > 0) ? STREAK_MULT : 1;
      if (S.hot1 || S.hot2) S.hotActiveTicks++;
      for (const hot of [S.hot1, S.hot2].filter(Boolean)) {
        const tgt = S.M[hot.targetKey];
        if (tgt && !tgt.grievous) {
          const healAmt = hot.healPerTick * healMult;
          tgt.hp = Math.min(tgt.max, tgt.hp + healAmt);
          S.healByType.hot += healAmt;
          if (healMult > 1) S.streakBonusHeal += hot.healPerTick * (STREAK_MULT - 1);
        }
        hot.ticksLeft--;
      }
      if (S.hot1 && S.hot1.ticksLeft <= 0) S.hot1 = null;
      if (S.hot2 && S.hot2.ticksLeft <= 0) S.hot2 = null;
    }
    if (S.windows.dawn > 0) S.windows.dawn--;
  ```
  Replace with:
  ```js
    // Keeper HoT ticks
    if (!S.M.keeper.grievous && S.M.keeper.hp > 0 && !S.M.keeper.stunned) {
      const keepStreak = S.streak.keeper;
      const healMult = (keepStreak && keepStreak.active > 0) ? STREAK_MULT : 1;
      if (S.hot1 || S.hot2) S.hotActiveTicks++;
      for (const hot of [S.hot1, S.hot2].filter(Boolean)) {
        const tgt = S.M[hot.targetKey];
        if (tgt && !tgt.grievous) {
          const healAmt = hot.healPerTick * healMult;
          tgt.hp = Math.min(tgt.max, tgt.hp + healAmt);
          S.healByType.hot += healAmt;
          if (healMult > 1) S.streakBonusHeal += hot.healPerTick * (STREAK_MULT - 1);
        }
        hot.ticksLeft--;
      }
      if (S.hot1 && S.hot1.ticksLeft <= 0) S.hot1 = null;
      if (S.hot2 && S.hot2.ticksLeft <= 0) S.hot2 = null;
    }

    // ── Captain HoT (fixed-interval proc, retargets to current lowest-HP standing member;
    // free — does not cost Captain's DPS action this tick) ─────────────────────────────
    if (!S.M.captain.grievous && S.M.captain.hp > 0 && !S.M.captain.stunned) {
      if (S.captainHot) {
        const tgt = S.M[S.captainHot.targetKey];
        if (tgt && !tgt.grievous) {
          const healAmt = S.captainHot.healPerTick;
          tgt.hp = Math.min(tgt.max, tgt.hp + healAmt);
          S.healByType.captainHot += healAmt;
        }
        S.captainHot.ticksLeft--;
      }
      if (S.captainHot && S.captainHot.ticksLeft <= 0) S.captainHot = null;

      S.captainHotTimer--;
      if (S.captainHotTimer <= 0) {
        const hotTarget = fightStanding(S).sort((a,b) => (S.M[a].hp/S.M[a].max) - (S.M[b].hp/S.M[b].max))[0];
        if (hotTarget) {
          S.captainHot = { targetKey: hotTarget, ticksLeft: CAPTAIN_HOT_DURATION, healPerTick: S.M.captain.wil * CAPTAIN_HOT_MUL };
        }
        S.captainHotTimer = CAPTAIN_HOT_INTERVAL;
      }
    }
    if (S.windows.dawn > 0) S.windows.dawn--;
  ```

  Heal-total display — find (around line 1373):
  ```js
    const healTotal = FIGHT.healByType.hot + FIGHT.healByType.triage + FIGHT.healByType.group + FIGHT.healByType.rescue;
  ```
  Replace with:
  ```js
    const healTotal = FIGHT.healByType.hot + FIGHT.healByType.triage + FIGHT.healByType.group + FIGHT.healByType.rescue + FIGHT.healByType.captainHot;
  ```

- [ ] **Step 7: Sanity-check with a manual run**

  ```bash
  node tools/sim/run_sim.js --duration 300 --boss 20000 --drain 4 --spike 25 --spikeiv 12 --verbose
  ```
  Expected: script runs without error; Captain's DPS share visibly increases relative to a pre-Task-5 run; the printed heal-by-type breakdown (using the new `healLabels.captainHot = "Captain HoT"` entry from Step 4) shows a nonzero "Captain HoT" total for a long-enough fight.

- [ ] **Step 8: Commit**

  ```bash
  git add tools/sim/run_sim.js tools/sim/hearthcraft_fight_sim.html
  git commit -m "$(cat <<'EOF'
  [hc] Sim: mirror Captain/Fighter DPS buff and new Captain HoT

  Brings both sim tools in line with the EncounterEngine.kt changes
  from the last three commits: Captain's coefficients (0.9/0.6 ->
  2.0/2.0), Fighter's coefficient (asymmetric -> symmetric 2.33), and
  the new Captain HoT (9-tick interval, will x 0.08/tick for 6 ticks,
  free). Completes the captain-fighter-balance plan.
  EOF
  )"
  ```

---

## Explicitly out of scope (carried over from the spec)

- Encounter difficulty re-tuning in response to these changes — a later dedicated balance pass, once the sim reflects these numbers.
- Keeper's healing AI (thresholds, cooldown removal, escalating full-rez chance) — separate, larger discussion; re-assess after this ships and is played.
- Elf/Dwarf Fighter builds — pending redesign per `master-design.md` §5.4, not addressed here.
