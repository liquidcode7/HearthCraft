# Browser Sim Resync + Metrics Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resync `tools/sim/hearthcraft_fight_sim.html`'s combat math to match `run_sim.js` exactly (Model B: stat-boost-only food, global spikes, Streak system, HoT/Triage/Group Heal), consolidate its two duplicate fight engines into one, remove the now-nonsensical Batch tab, and add the same metrics dashboard already built for `run_sim.js`.

**Architecture:** One new shared, DOM-free fight-resolution engine (`createFightState()` + `fightTick()`) ported directly from `run_sim.js`, added alongside the existing code, then wired to replace the Live tab's duplicate inline logic. The engine returns plain data (state object + per-tick narrative log lines) — nothing in it touches the DOM — so the Live tab's existing rendering functions (`renderParty()`, `updLive()`, etc.) become thin readers of that state rather than owners of the simulation.

**Tech Stack:** Vanilla JavaScript, single self-contained HTML file (no external script dependencies — matches this project's existing convention for `tools/sim/` and `tools/recipe-browser/`), inline SVG charts via existing `lineChart`/`meterChart`/`pieChart` helpers.

## Global Constraints

- No change to `EncounterEngine.kt` or other production Kotlin.
- No change to actual combat balance beyond what's needed to match `run_sim.js`'s already-correct model — this is a resync, not a rebalance.
- The file must remain a single, self-contained HTML file (no `<script src>` to a second local file) — matches the existing `food_model.js`-inlining convention already in this file.
- No browser/emulator is available in this environment. Manual on-device/browser verification is required afterward for anything a code read can't confirm (chart layout, Live tab tick animation) — flag this in each task's report rather than claiming it was checked.
- Constants (`STREAK_K`, `HOT_HEAL_MUL`, etc.) must be copied verbatim from `run_sim.js` so the "must match" relationship between the two files holds in both directions.

---

### Task 1: Hunter → Fighter rename

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html` (all "Hunter"/"hunter" occurrences — 41 total per a pre-check grep)

**Interfaces:**
- Produces: `TPL` keys `fighterA`/`fighterM` (were `hunterA`/`hunterM`), `ORDER = ["warden","fighter","keeper","captain"]` (was `"hunter"`), CSS classes `.hdr-fighter`/`.role-fighter` (were `.hdr-hunter`/`.role-hunter`), DOM ids like `g_dps_fighter` (was `g_dps_hunter`) — this is the naming every later task in this plan assumes exists.

This is a mechanical, case-sensitive global rename done on the file as it exists today, before any other change in this plan. Do this first so every later task can write "fighter" without checking which name is currently in use.

- [ ] **Step 1: Global rename**

Replace every occurrence of `Hunter` with `Fighter` and every occurrence of `hunter` with `fighter` throughout `tools/sim/hearthcraft_fight_sim.html`. This covers (non-exhaustive — the point is every occurrence, not just this list): the CSS custom property `--hunter`, classes `.hdr-hunter`/`.role-hunter`/`.c-hunter`, `TPL.hunterA`/`TPL.hunterM`, the `ORDER` array, `RECIPE_SELECTS.hunter`, `roster()`'s `hunterM`/`hunterA` lookup, every `M.hunter`/`members.hunter`/`M[k]==="hunter"` comparison, DOM ids `g_dps_hunter`, the Setup tab's "Hunter eats" label and "Hunter flavor" slider, the Company tab's `hdr-hunter` card (name "Mira", role label "Hunter" → "Fighter"; her bio/mechanics/Inspiration text can keep referring to her by name — only the literal role label "Hunter" changes to "Fighter"), and every `M.hunter.tpl.name`-style reference used for log strings.

Do NOT rename the character name "Mira" — she stays Mira. Only the role label changes.

- [ ] **Step 2: Verify the rename is complete**

Run: `grep -ic "hunter" tools/sim/hearthcraft_fight_sim.html`
Expected: `0`

- [ ] **Step 3: Syntax sanity check**

Extract the file's single `<script>` block (the one starting after `<script>` on the line containing `// ── recipe level → HP/s (must match food_model.js) ──`, i.e. the second `<script>` tag — not the first one, which holds `FOOD_MODEL`) and check it parses as valid JS:

```bash
node -e "
const fs = require('fs');
const html = fs.readFileSync('tools/sim/hearthcraft_fight_sim.html', 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
const mainScript = scripts[scripts.length - 1];
new Function(mainScript);
console.log('OK: script parses, ' + mainScript.length + ' chars');
"
```

Expected: `OK: script parses, <N> chars` with no thrown SyntaxError. (This only checks syntax, not DOM behavior — `document`/`window` references inside won't execute here, but `new Function(...)` parses the whole body without running it, which is exactly what's needed to catch a broken rename.)

- [ ] **Step 4: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[hc] Browser sim: rename Hunter role label to Fighter"
```

---

### Task 2: Remove the Batch tab

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html`

**Interfaces:**
- None — pure removal. `run_sim.js` (already correct, already has the new metrics from this session) is the tool for batch/aggregate analysis going forward.

The Batch tab's entire premise is an "HP/s sweep" to find win-rate cliffs. In Model B, food has no HP/s value (stat boosts only) — there's no axis left to sweep. Rather than rebuild this feature for a mechanic that no longer exists, remove it and point to `run_sim.js`.

- [ ] **Step 1: Remove the Batch tab's UI**

In the HTML body, remove:
- The tab button: `<button id="tab_batch" onclick="showTab('batch')">5 &middot; Batch</button>` (inside `.tabbar`)
- The entire `<!-- ===================== TAB 5: BATCH / MONTE CARLO ===================== -->` section (the full `<div class="tab" id="pane_batch">...</div>` block, from that comment through its closing `</div>`)

Renumber the remaining tab buttons' leading digits to stay sequential (Setup=1, Live=2, Results=3, Company=4 — Company was previously labeled "4" already; just remove the "5 · Batch" button, no other renumbering needed since Company already comes after Results in the tab bar and was already labeled 4).

Add a short note where the Batch tab used to be reachable — inside the Setup tab's existing `.impbar` panel (the "Import encounter from spreadsheet" panel at the top), append one line to its `.gnote` div:

```
 For batch/aggregate win-rate analysis across many fights, use <code>node tools/sim/run_sim.js</code> instead — this browser tool is for watching and inspecting one fight at a time.
```

- [ ] **Step 2: Remove the Batch tab's JavaScript**

Remove these functions and the `_batchSweepData`/`FL_MARKERS`/`FL_COLORS` module-level declarations entirely: `batchRunOne`, `batchAtHps`, `batchParams`, `runBatch`, `drawSweepChart`, `drawFlTable`, `drawInspectBar`, and the comment line `// Re-draw inspect bar whenever the slider moves...` immediately after them.

Remove `'batch'` from the `showTab()` function's tab-name array: change
```js
function showTab(name){['setup','live','results','batch','company'].forEach(...
```
to
```js
function showTab(name){['setup','live','results','company'].forEach(...
```

- [ ] **Step 3: Syntax check**

Run the same extraction check as Task 1 Step 3:

```bash
node -e "
const fs = require('fs');
const html = fs.readFileSync('tools/sim/hearthcraft_fight_sim.html', 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
const mainScript = scripts[scripts.length - 1];
new Function(mainScript);
console.log('OK: script parses, ' + mainScript.length + ' chars');
"
```

Expected: `OK: script parses, <N> chars` (a smaller N than Task 1's, since code was removed).

Also confirm no leftover references to removed functions:

```bash
grep -n "batchRunOne\|batchAtHps\|batchParams\|runBatch\|drawSweepChart\|drawFlTable\|drawInspectBar\|FL_MARKERS\|FL_COLORS\|pane_batch\|tab_batch" tools/sim/hearthcraft_fight_sim.html
```

Expected: no output (empty).

- [ ] **Step 4: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[hc] Browser sim: remove Batch tab (HP/s sweep doesn't apply to Model B; use run_sim.js for batch analysis)"
```

---

### Task 3: Shared fight engine — constants, state, and per-tick resolution

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html` (add new code; do not modify or remove any existing function yet — this task is purely additive)

**Interfaces:**
- Produces: `createFightState(cfg): FightState` and `fightTick(state: FightState): { done: boolean, outcome: string|null }`. `FightState` shape documented below — Task 4 (Live tab wiring) reads every field on it.

This is the core resync: a single, DOM-free engine ported directly from `run_sim.js`'s `runFight()`, adapted to a create-once/tick-repeatedly shape instead of a single `while(true)` loop, so both a live per-tick caller (Task 4) and a future headless caller (Task 6's verification harness) can use it identically. Add this code as a new block right after the existing `dpsBreakdown()` function (which Task 4 will later delete) and before `sustain()`.

- [ ] **Step 1: Add the new constants block**

These mirror `run_sim.js` exactly (some, like `PEN_SCALE`, `SHADOW_FLOOR`, `RESCUE_CAP`, etc., already exist as top-level consts in this file from the pre-existing code — do NOT redeclare those; only add the ones genuinely missing, listed below):

```js
// ── Streak + Keeper-heal constants (ported verbatim from run_sim.js — must match) ──
const HOT_DURATION    = 8;    // ticks per HoT application
const HOT_HEAL_MUL    = 0.15; // healPerTick = keeper.wil * HOT_HEAL_MUL
const TRIAGE_HP       = 0.25; // triage fires when target hp < max * TRIAGE_HP
const TRIAGE_MUL      = 2.0;  // triageHeal = keeper.wil * TRIAGE_MUL
const TRIAGE_COOLDOWN = 2;    // ticks between triage uses
const GROUP_HEAL_IV   = 20;   // ticks between group heals
const GROUP_HEAL_MUL  = 0.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL
const STREAK_K          = 0.002; // trigger prob per eligible tick = fat * STREAK_K
const STREAK_REFRACTORY = 20;    // ticks before streak can re-trigger
const STREAK_DURATION   = 5;     // streak active ticks
const STREAK_MULT       = 1.5;   // DPS/heal multiplier during streak
```

- [ ] **Step 2: Add `createFightState()`**

```js
// ── Shared fight engine (DOM-free) — ported from run_sim.js's runFight() ──
// cfg: { lvl, roster:{warden,fighter,keeper,captain} TPL objects, duration, boss,
//        drain, spike, spikeiv, phys, dread, hope, shadow, radiance, disease, hale,
//        cold, warmth, heat, heatease, wake, alert, burstmul, potency,
//        graceBase, hornBase, dawnBase, blackArrowCap, memberFoodBonuses:{k:{mig,agi,vit,wil,fat}} }
function createFightState(cfg) {
  const lvl = cfg.lvl;
  const M = {};
  ORDER.forEach(k => {
    const tpl = cfg.roster[k];
    const sb = (cfg.memberFoodBonuses && cfg.memberFoodBonuses[k]) || {};
    const bvit = sb.vit || 0;
    const max = Math.round(30 + (stat(tpl,"vit",lvl) + bvit) * 16);
    M[k] = {
      key:k, tpl, role:tpl.role, cls:tpl.cls, soak:tpl.soak, color:tpl.color,
      max, hp:max, wounds:0, grievous:false, atZero:false, stunned:false, stunTicks:0,
      broken:false, horn:false, action:"",
      mig:stat(tpl,"mig",lvl)+(sb.mig||0), agi:stat(tpl,"agi",lvl)+(sb.agi||0),
      vit:stat(tpl,"vit",lvl)+bvit, wil:stat(tpl,"wil",lvl)+(sb.wil||0), fat:stat(tpl,"fat",lvl)+(sb.fat||0),
      wilBase:stat(tpl,"wil",lvl)+(sb.wil||0), fatBase:stat(tpl,"fat",lvl)+(sb.fat||0), shDrain:0,
      fatEvades:0
    };
  });
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
}
```

- [ ] **Step 3: Add the per-tick engine helpers**

```js
function fightActive(S)   { return ORDER.filter(k => !S.M[k].grievous && !S.M[k].broken); }
function fightStanding(S) { return fightActive(S).filter(k => S.M[k].hp > 0); }
function fightInTrouble(S){ return fightActive(S).filter(k => S.M[k].hp / S.M[k].max < 0.35).length; }
function fightInspRoll(base, boost) { return Math.random() < (base + boost); }

// Mirrors run_sim.js's dpsBreakdown() exactly, reading from a FightState instead of closures.
function fightDpsBreakdown(S) {
  const cfg = S.cfg;
  if (cfg.unkill) return { raw:0, eff:0, physAfter:0, layerADrag:0, rawBy:{warden:0,fighter:0,keeper:0,captain:0}, streakBonus:0, dawnBonus:0 };
  let raw = 0, streakBonus = 0;
  const rawBy = { warden:0, fighter:0, keeper:0, captain:0 };
  ORDER.forEach(k => {
    const m = S.M[k];
    if (m.grievous || m.hp <= 0 || m.stunned || m.broken) return;
    const isStreaking = S.streak[k] && S.streak[k].active > 0;
    const mult = isStreaking ? STREAK_MULT : 1;
    let base = 0;
    if (k==="keeper")        { if (S.keeperDealtDps) base = m.wil * 0.9; }
    else if (k==="fighter")  base = m.agi + m.mig * 0.4;
    else if (k==="warden")   base = m.mig * 0.5;
    else if (k==="captain")  base = m.mig * 0.3 + m.wil * 0.2;
    const contribution = base * mult;
    rawBy[k] += contribution; raw += contribution;
    if (isStreaking) streakBonus += base * (STREAK_MULT - 1);
  });
  let dawnBonus = 0;
  if (S.windows.dawn > 0) {
    dawnBonus = raw * 0.5;
    raw *= 1.5; streakBonus *= 1.5;
    ORDER.forEach(k => { rawBy[k] *= 1.5; });
  }
  const potency = cfg.potency || 0;
  const physAfter = Math.max(0, cfg.phys * (1 - Math.min(1, potency/PEN_SCALE)));
  const capWil = (!S.M.captain.grievous && !S.M.captain.stunned) ? S.M.captain.wil : 0;
  const willCut = Math.min(1, capWil/100), hopeCut = Math.min(1, (cfg.hope||0)/100);
  const effectiveDread = cfg.dread * (1 - Math.min(1, willCut + hopeCut));
  const layerADrag = Math.min(1, effectiveDread * DREAD_VAR_COEF + cfg.dread * DREAD_FLOOR_COEF);
  const eff = Math.max(0, raw * (1-physAfter) * (1-layerADrag));
  return { raw, eff, physAfter, layerADrag, rawBy, streakBonus, dawnBonus };
}

// Advances S by exactly one tick. Returns { done, outcome, logLines }.
// logLines is an array of narrative strings for this tick only (empty most ticks) —
// the caller (Live tab, Task 4) is responsible for displaying them; this function
// never touches the DOM.
function fightTick(S) {
  S.t++;
  const logLines = [];
  const cfg = S.cfg;

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
  if (S.windows.horn > 0) { S.windows.horn--; if (S.windows.horn === 0) S.M.warden.horn = false; }
  if (cfg.dread > 0) {
    ORDER.forEach(k => {
      if (S.M[k].stunned) {
        S.M[k].stunTicks--;
        if (S.M[k].stunTicks <= 0) { S.M[k].stunned = false; S.M[k].stunTicks = 0; logLines.push(`${S.M[k].tpl.name} shakes free`); }
      }
    });
  }

  const act = fightActive(S);
  const live = fightStanding(S);

  // Streak updates
  act.forEach(k => {
    const s = S.streak[k];
    if (S.M[k].grievous || S.M[k].broken || S.M[k].stunned) return;
    if (s.active > 0) { s.active--; }
    else if (s.refractory > 0) { s.refractory--; }
    else if (Math.random() < S.M[k].fat * STREAK_K) {
      s.active = STREAK_DURATION; s.refractory = STREAK_REFRACTORY;
      S.streakCount[k]++;
      logLines.push(`★ STREAK — ${S.M[k].tpl.name}`);
    }
  });

  // Keeper action
  S.keeperDealtDps = true;
  S.M.keeper.action = "damage";
  if (!S.M.keeper.grievous && S.M.keeper.hp > 0 && !S.M.keeper.stunned) {
    const healPerTick = S.M.keeper.wil * HOT_HEAL_MUL;
    const keepStreakAct = S.streak.keeper;
    const healMult = (keepStreakAct && keepStreakAct.active > 0) ? STREAK_MULT : 1;
    if (S.triageCooldown > 0) S.triageCooldown--;
    S.groupHealTimer--;
    let actionTaken = false;
    if (S.groupHealTimer <= 0) {
      const groupHeal = S.M.keeper.wil * GROUP_HEAL_MUL * healMult;
      live.forEach(k => { S.M[k].hp = Math.min(S.M[k].max, S.M[k].hp + groupHeal); });
      S.healByType.group += groupHeal * live.length;
      if (healMult > 1) S.streakBonusHeal += (S.M.keeper.wil * GROUP_HEAL_MUL) * (STREAK_MULT - 1) * live.length;
      S.groupHealTimer = GROUP_HEAL_IV;
      actionTaken = true;
      logLines.push(`Cael group heal +${Math.round(groupHeal)} to all`);
    } else {
      const triageTarget = live.filter(k => k !== "keeper").find(k => S.M[k].hp / S.M[k].max < TRIAGE_HP);
      if (triageTarget && S.triageCooldown === 0) {
        const triageHeal = S.M.keeper.wil * TRIAGE_MUL * healMult;
        S.M[triageTarget].hp = Math.min(S.M[triageTarget].max, S.M[triageTarget].hp + triageHeal);
        S.healByType.triage += triageHeal;
        if (healMult > 1) S.streakBonusHeal += (S.M.keeper.wil * TRIAGE_MUL) * (STREAK_MULT - 1);
        S.triageCooldown = TRIAGE_COOLDOWN;
        actionTaken = true;
        logLines.push(`Cael triage → ${S.M[triageTarget].tpl.name} +${Math.round(triageHeal)}`);
      } else {
        if (!S.hot1 || !S.hot2) {
          const covered = new Set([S.hot1?.targetKey, S.hot2?.targetKey].filter(Boolean));
          const hotTarget = live.filter(k => !covered.has(k)).sort((a,b) => (S.M[a].hp/S.M[a].max) - (S.M[b].hp/S.M[b].max))[0];
          if (hotTarget) {
            const newHot = { targetKey: hotTarget, ticksLeft: HOT_DURATION, healPerTick };
            if (!S.hot1) S.hot1 = newHot; else S.hot2 = newHot;
            logLines.push(`Cael applies HoT → ${S.M[hotTarget].tpl.name}`);
          }
        }
      }
    }
    S.keeperDealtDps = !actionTaken;
    S.M.keeper.action = actionTaken ? "burst" : "damage";
  }

  // Rescue burst
  if (!S.M.keeper.grievous && S.M.keeper.hp > 0 && !S.M.keeper.stunned && S.rescuesUsed < RESCUE_CAP) {
    let rescue = null;
    for (const k of ORDER) { if (k === "keeper" || S.M[k].grievous) continue; if (S.M[k].hp <= 0) { rescue = k; break; } }
    if (rescue) {
      const burst = (40 + S.M.keeper.wil * 4) * (cfg.burstmul || 1);
      S.M[rescue].hp = Math.min(S.M[rescue].max, Math.max(S.M[rescue].hp, 0) + burst);
      S.healByType.rescue += burst;
      S.rescuesUsed++;
      S.M.keeper.action = "burst";
      logLines.push(`Cael bursts ${S.M[rescue].tpl.name} (+${Math.round(burst)}) rescue ${S.rescuesUsed}/${RESCUE_CAP}`);
    }
  }

  // Steady drain
  if (live.length) { live.forEach(k => { S.M[k].hp -= cfg.drain / 4; }); }

  // Global spike (one per interval, hits one random standing member; Horn redirects to Warden)
  if (cfg.spike > 0 && S.t >= S.nextSpikeAt) {
    S.nextSpikeAt = S.t + Math.round(cfg.spikeiv * (0.5 + Math.random()));
    const spikeRoll = cfg.spike * (0.7 + Math.random() * 0.6);
    let target = null, hornRedirected = false;
    if (S.windows.horn > 0 && !S.M.warden.grievous && S.M.warden.hp > 0) { target = "warden"; hornRedirected = true; }
    else {
      target = live.length ? live[Math.floor(Math.random() * live.length)] : null;
      if (target === "keeper" && (S.M.keeper.hp - spikeRoll) <= 0 && S.M.warden.hp > 0 && !S.M.warden.grievous && S.wardsUsed < WARD_CAP) {
        target = "warden"; S.wardsUsed++;
        logLines.push(`${S.M.warden.tpl.name} guards Cael from a killing blow (guard ${S.wardsUsed}/${WARD_CAP})`);
      }
    }
    if (target) {
      S.spikesTotal++;
      if (Math.random() < S.M[target].fat * 0.004) {
        S.M[target].fatEvades++;
        logLines.push(`${S.M[target].tpl.name} slips the blow — Fate ${Math.round(S.M[target].fat)} (near miss)`);
      } else {
        S.M[target].hp -= spikeRoll;
        if (hornRedirected && target === "warden") S.inspHornRedirect += spikeRoll;
        logLines.push(`a blow strikes ${S.M[target].tpl.name} (${Math.round(spikeRoll)})`);
      }
    }
  }

  // Shadow drain
  if (cfg.shadow > 0) {
    const deepFloor = Math.max(0.25, SHADOW_FLOOR - (cfg.shadow/100)*0.30);
    const floor = deepFloor + (1-deepFloor)*Math.min(1, (cfg.radiance||0)/100);
    const dr = cfg.shadow * SHADOW_RATE;
    ORDER.forEach(k => {
      if (S.M[k].grievous) return;
      S.M[k].wil = Math.max(S.M[k].wilBase*floor, S.M[k].wil-dr);
      S.M[k].fat = Math.max(S.M[k].fatBase*floor, S.M[k].fat-dr);
      S.M[k].shDrain = (S.M[k].wilBase-S.M[k].wil)+(S.M[k].fatBase-S.M[k].fat);
    });
  }

  // Simple hazard drain
  { const haz=[["disease","hale"],["cold","warmth"],["heat","heatease"],["wake","alert"]];
    let extra=0; haz.forEach(([hz,an])=>{ const str=cfg[hz]||0; if(str>0) extra+=(str*0.08)*(1-Math.min(1,(cfg[an]||0)/100)); });
    if(extra>0) act.forEach(k=>{ S.M[k].hp -= extra/act.length; }); }

  // Dread Layer B
  if (cfg.dread > 0 && S.t % BREAK_CHECK_INTERVAL === 0) {
    const capWil = (!S.M.captain.grievous && !S.M.captain.stunned) ? S.M.captain.wil : 0;
    const wCut = Math.min(1, capWil/100), hCut = Math.min(1, (cfg.hope||0)/100);
    const neg = wCut + hCut;
    const dreadPool = captW => { const pool = []; ORDER.forEach(k => { if (!S.M[k].grievous && !S.M[k].broken && !S.M[k].stunned && S.M[k].hp > 0) pool.push({k, w: k==="captain" ? captW : 1}); }); return pool; };
    const dreadPick = pool => { const tot = pool.reduce((s,p) => s+p.w, 0); if (!tot) return null; let r = Math.random() * tot; for (const p of pool) { r -= p.w; if (r <= 0) return p.k; } return pool[pool.length-1]?.k || null; };
    if (neg < T_TEMP) { const tgt = dreadPick(dreadPool(CAPTAIN_STUN_WEIGHT)); if (tgt) { S.M[tgt].stunned = true; S.M[tgt].stunTicks = STUN_TICKS; logLines.push(`${S.M[tgt].tpl.name} is seized by dread — action lost for ${STUN_TICKS} seconds`); S.events.push({t:S.t, kind:"dread-stun", who:S.M[tgt].tpl.name}); } }
    if (neg < T_PERM) { const tgt = dreadPick(dreadPool(CAPTAIN_BREAK_WEIGHT)); if (tgt) { S.M[tgt].broken = true; logLines.push(`${S.M[tgt].tpl.name}'s courage fails — they are gone`); S.events.push({t:S.t, kind:"morale-break", who:S.M[tgt].tpl.name}); } }
  }

  // Wound / grievous checks
  ORDER.forEach(k => {
    if (S.M[k].grievous) return;
    if (S.M[k].hp <= 0 && !S.M[k].atZero) {
      S.M[k].atZero = true; S.M[k].wounds++;
      logLines.push(`${S.M[k].tpl.name} hits zero — a wound (${S.M[k].wounds}/${GRIEVOUS})`);
      if (S.M[k].wounds >= GRIEVOUS) {
        if (!S.M.keeper.grievous && !S.M.keeper.stunned && S.M.keeper.hp>0 && fightInspRoll(Math.min(0.25, cfg.graceBase + S.M.keeper.fat*0.003), S.inspBoost)) {
          const restored = S.M[k].max*0.4;
          S.M[k].wounds=0; S.M[k].hp=Math.round(restored); S.M[k].atZero=false;
          S.inspGraceHeal += restored;
          logLines.push(`★ LAURELIN'S GRACE — ${S.M[k].tpl.name} pulled back`);
          S.events.push({t:S.t, kind:"Laurelin's Grace", who:S.M[k].tpl.name});
        } else {
          S.M[k].grievous=true;
          logLines.push(`${S.M[k].tpl.name} takes a GRIEVOUS wound — out of the fight`);
          S.events.push({t:S.t, kind:"grievous", who:S.M[k].tpl.name});
        }
      }
    }
    if (S.M[k].hp > 5) S.M[k].atZero = false;
  });

  // Inspiration: Horn of Gondor
  if (S.inspBoost > 0) S.inspBoost = Math.max(0, S.inspBoost - 0.005);
  const nowTrouble = fightInTrouble(S);
  const crisisEdge = nowTrouble >= 2 && S.prevTrouble < 2;
  if (!S.fired.horn && crisisEdge && !S.M.warden.grievous && !S.M.warden.stunned && !S.M.warden.broken && S.M.warden.hp>0 && fightInspRoll(Math.min(0.25, cfg.hornBase + S.M.warden.fat*0.003), S.inspBoost)) {
    S.fired.horn=true; S.windows.horn=8; S.M.warden.horn=true;
    logLines.push(`★ THE HORN OF GONDOR — ${S.M.warden.tpl.name}`);
    S.events.push({t:S.t, kind:"The Horn of Gondor", who:S.M.warden.tpl.name});
  }

  // Inspiration: Wrath, Ruin, and the Red Dawn
  if (!S.fired.dawn && crisisEdge && !S.M.captain.grievous && !S.M.captain.stunned && !S.M.captain.broken && S.M.captain.hp>0 && fightInspRoll(Math.min(0.25, cfg.dawnBase + S.M.captain.fat*0.003), 0)) {
    S.fired.dawn=true; S.windows.dawn=10;
    act.forEach(k => { const before = S.M[k].hp; S.M[k].hp = Math.min(S.M[k].max, S.M[k].hp + S.M[k].max*0.15); S.inspDawnHeal += (S.M[k].hp - before); });
    S.inspBoost = 0.15;
    logLines.push(`★ RED DAWN — ${S.M.captain.tpl.name}`);
    S.events.push({t:S.t, kind:"Wrath, Ruin, and the Red Dawn", who:S.M.captain.tpl.name});
  }
  S.prevTrouble = nowTrouble;

  // DPS tick
  const bd = fightDpsBreakdown(S);
  if (!cfg.unkill) {
    const jitter = 0.10;
    const jitterFactor = 1 + (Math.random()*2-1)*jitter;
    const appliedDmg = bd.eff * jitterFactor;
    S.boss = Math.max(0, S.boss - appliedDmg);
    const mitFactor = (1-bd.physAfter) * (1-bd.layerADrag);
    if (bd.raw > 0) { ORDER.forEach(k => { S.dmgByMember[k] += (bd.rawBy[k] / bd.raw) * appliedDmg; }); }
    S.streakBonusDmg   += bd.streakBonus * mitFactor * jitterFactor;
    S.inspDawnBonusDps += bd.dawnBonus   * mitFactor * jitterFactor;
  }

  // Inspiration: Black Arrow
  if (!S.baFired && !S.M.fighter.grievous && !S.M.fighter.stunned && !S.M.fighter.broken && S.M.fighter.hp>0) {
    const elapsed = S.t/S.maxT;
    const ttk = bd.eff>0 ? S.boss/bd.eff : 1e9;
    const trem = S.maxT-S.t;
    const losing = cfg.unkill ? elapsed>0.5 : (ttk>trem && elapsed>0.4);
    if (losing) {
      const rise = Math.min(cfg.blackArrowCap, (elapsed-0.4)*cfg.blackArrowCap*2.5);
      if (fightInspRoll(rise + S.M.fighter.fat*0.003, S.inspBoost)) {
        S.baFired=true;
        if (cfg.unkill) {
          const cut = Math.round(cfg.duration*0.15);
          S.maxT -= cut; S.inspBlackArrowAmount = cut;
          logLines.push(`★ BLACK ARROW — ${S.M.fighter.tpl.name} looses a flaming arrow — the terror recoils, the clock shortens`);
          S.events.push({t:S.t, kind:"Black Arrow (flaming)", who:S.M.fighter.tpl.name});
        } else {
          const burn = cfg.boss*0.18;
          S.boss = Math.max(0, S.boss-burn); S.inspBlackArrowAmount = burn;
          logLines.push(`★ BLACK ARROW — ${S.M.fighter.tpl.name} draws the Black Arrow — go now, and speed well!`);
          S.events.push({t:S.t, kind:"Black Arrow", who:S.M.fighter.tpl.name});
        }
      }
    }
  }

  // Termination
  if (!cfg.unkill && S.boss <= 0) { S.outcome = "VICTORY"; S.done = true; }
  else if (!ORDER.some(k => !S.M[k].grievous && !S.M[k].broken && S.M[k].hp>0)) { S.outcome = "DEFEAT"; S.done = true; }
  else if (S.t >= S.maxT) { S.outcome = cfg.unkill ? "REPELLED" : "STALEMATE"; S.done = true; }

  return { done: S.done, outcome: S.outcome, logLines };
}
```

- [ ] **Step 4: Syntax check**

Run the same extraction check as Task 1 Step 3. Expected: `OK: script parses, <N> chars` (larger N than Task 2's, since this task only adds code).

- [ ] **Step 5: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[hc] Browser sim: add shared DOM-free fight engine (createFightState/fightTick), ported from run_sim.js"
```

---

### Task 4: Wire the Live tab to the shared engine

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html`

**Interfaces:**
- Consumes: `createFightState(cfg)`, `fightTick(state)` (Task 3)
- Produces: a module-level `let FIGHT = null;` holding the current fight's `FightState`, replacing the old scattered globals (`M`, `t`, `events`, `fired`, `windows`, `rescuesUsed`, `wardsUsed`, `inspBoost`, `prevTrouble`, `spikesTotal`, `baFired`, and `window._boss`) that `renderParty()`/`updLive()`/`buildSummary()`/`dpsBreakdown()` currently read. Later tasks (Results tab) read `FIGHT` after a fight completes.

This is where the old duplicate inline logic (HP/s food healing, per-member independent spikes, reserve pool, no Streak/HoT/Triage/Group-Heal) gets replaced by calls into the new engine.

- [ ] **Step 1: Delete the old per-tick logic and old per-tick globals**

Delete these functions entirely (their logic now lives in `fightTick`/`createFightState`, Task 3): `dpsBreakdown()`, `stepOnce()`.

Delete these now-unused old globals and their declarations: `let M={},events=[],fired={},windows={},baFired=false,series=[],rescuesUsed=0,wardsUsed=0,inspBoost=0,prevTrouble=0,spikesTotal=0;` — replace with:

```js
let FIGHT = null;
let series = [];
```

(`series` stays — it's the per-tick trace array the Results tab charts read; Task 4 Step 3 below repopulates it from `FIGHT` each tick instead of from the old scattered globals.)

Delete `foodOf()` and `sustain()` — food no longer has an HP/s value (Model B: stat boosts only). Also delete the now-orphaned `RMAX` constant and its only other use, the `reserve`/`bar-reserve`/`res-num` DOM elements in `renderParty()` (see Step 2) — the reserve pool doesn't exist in the resynced model.

Delete the `roster()` function's now-inapplicable reference is unaffected (it already just resolves `TPL` entries by key and needs no change beyond the Task 1 rename already applied to it).

- [ ] **Step 2: Rewrite `buildParty()` and `renderParty()` to read from `FIGHT`**

Replace `buildParty()` (currently builds the standalone global `M`) with a version that builds a `FightState` via `createFightState()` and assigns it to `FIGHT`, used only for the Setup tab's live stat preview (not an actual running fight):

```js
function buildParty(){
  const lvl=+$("lvl").value, R=roster();
  const cfg = { lvl, roster:R, duration:+$("dur").value, boss:+$("boss").value,
    memberFoodBonuses: Object.fromEntries(ORDER.map(k=>[k, memberFoodBonuses(k)])) };
  FIGHT = createFightState(cfg);
  renderParty();
}
```

Replace `renderParty()` to read `FIGHT.M` instead of the old bare `M`, and drop the reserve-pool bar entirely:

```js
function renderParty(){const host=$("party");host.innerHTML="";
  ORDER.forEach(k=>{const m=FIGHT.M[k],pct=Math.max(0,(m.hp/m.max)*100);
    const el=document.createElement("div");el.className=`panel member ${m.grievous?"grievous":""} ${m.horn?"horn":""}`;
    el.innerHTML=`<div class="m-top"><span class="m-name">${m.tpl.name}</span><span class="m-role ${m.cls}">${m.role}</span></div>
      <div class="bar-track"><div class="bar-fill" style="width:${pct}%"></div></div>
      <div class="m-hp-num"><span>Morale</span><span>${Math.max(0,Math.round(m.hp))} / ${m.max}</span></div>
      <div class="m-wounds">${woundPips(m.wounds)}</div><div class="m-action">${m.action||""}</div>`;
    host.appendChild(el);});}
```

- [ ] **Step 3: Replace `stepOnce()` with a thin wrapper around `fightTick()`**

Add this function in the same place `stepOnce()` used to live:

```js
function stepOnce(){
  const result = fightTick(FIGHT);
  result.logLines.forEach(msg => {
    const cls = /GRIEVOUS|GRACE/.test(msg) ? "insp" : /hits zero|blow strikes/.test(msg) ? "t" : /group heal|triage|bursts|HoT/.test(msg) ? "h" : /shakes free|slips the blow/.test(msg) ? "g" : "";
    log(msg, cls || undefined);
  });
  const bd = fightDpsBreakdown(FIGHT);
  const incoming = FIGHT.cfg.drain + FIGHT.cfg.spike/FIGHT.cfg.spikeiv;
  series.push({t:FIGHT.t, boss:FIGHT.boss, dps:bd.eff, raw:bd.raw, layerADrag:bd.layerADrag||0, incoming,
    effBy:{warden:bd.rawBy.warden*(1-bd.physAfter)*(1-bd.layerADrag), fighter:bd.rawBy.fighter*(1-bd.physAfter)*(1-bd.layerADrag),
           keeper:bd.rawBy.keeper*(1-bd.layerADrag), captain:bd.rawBy.captain*(1-bd.physAfter)*(1-bd.layerADrag)},
    dawnBoost:bd.dawnBonus||0,
    spirit:{wil:ORDER.reduce((a,k)=>a+(FIGHT.M[k].grievous?0:FIGHT.M[k].wil),0),fat:ORDER.reduce((a,k)=>a+(FIGHT.M[k].grievous?0:FIGHT.M[k].fat),0)},
    spiritBase:{wil:ORDER.reduce((a,k)=>a+FIGHT.M[k].wilBase,0),fat:ORDER.reduce((a,k)=>a+FIGHT.M[k].fatBase,0)},
    morale:Object.fromEntries(ORDER.map(k=>[k,Math.max(0,FIGHT.M[k].hp)])),
    maxmorale:Object.fromEntries(ORDER.map(k=>[k,FIGHT.M[k].max])),
    wounds:Object.fromEntries(ORDER.map(k=>[k,FIGHT.M[k].wounds+(FIGHT.M[k].grievous?GRIEVOUS:0)]))});
  updLive(bd, FIGHT.boss, incoming);
  if (result.done) { finish(result.outcome, "v-"+result.outcome.toLowerCase()); return true; }
  return false;
}
```

- [ ] **Step 4: Rewrite `prep()` to build a `FightState` instead of the old globals**

```js
function prep(){
  const lvl=+$("lvl").value, R=roster();
  const cfg = {
    lvl, roster:R, duration:+$("dur").value, boss:+$("boss").value,
    drain:+$("drain").value, spike:+$("spike").value, spikeiv:+$("spikeiv").value,
    phys:(MODE===1?0:+$("phys").value/100), unkill:MODE===1, burstmul:+$("burstmul").value/100,
    potency: draughtPotency(),
    dread:statusOn("dread")/100, hope:antidoteOn("hope"),
    shadow:statusOn("shadow"), radiance:antidoteOn("radiance"),
    disease:statusOn("disease"), hale:antidoteOn("hale"),
    cold:statusOn("cold"), warmth:antidoteOn("warmth"),
    heat:statusOn("heat"), heatease:antidoteOn("heatease"),
    wake:statusOn("wake"), alert:antidoteOn("alert"),
    graceBase:0.05, hornBase:0.03, dawnBase:0.03, blackArrowCap:0.06,
    memberFoodBonuses: Object.fromEntries(ORDER.map(k=>[k, memberFoodBonuses(k)]))
  };
  FIGHT = createFightState(cfg);
  P = cfg; // P stays as an alias other functions (refreshRead, buildSummary) already read
  maxT = cfg.duration;
  $("log").innerHTML=""; series=[];
  $("verdict").textContent="ENGAGED";$("verdict").className="verdict v-fighting";
  const sus = 0; // sustain no longer exists (Model B: no HP/s food) — startup log below no longer cites it
  const drain=cfg.drain, spike=cfg.spike, iv=cfg.spikeiv;
  log(cfg.unkill?`The band sets itself against a terror that cannot be slain — endure for ${fmtT(maxT)}.`:`The band draws weapons. The enemy host holds ${Math.round(cfg.boss)} resolve.`,"");
  if(drain>0||spike>0){
    let pressStr=`Pressure: `;
    if(drain>0)pressStr+=`steady drain ${drain}/s (${Math.round(drain/4)}/s per member, independent)`;
    if(drain>0&&spike>0)pressStr+=` · `;
    if(spike>0)pressStr+=`one spike every ~${iv}s, potency ${spike}`;
    log(pressStr,"");
  }
  if(!cfg.unkill){const bd=fightDpsBreakdown(FIGHT);const ttk=bd.eff>0?Math.ceil(cfg.boss/bd.eff):99999;
    if(ttk<=maxT*0.6)log(`Damage looks strong — ${Math.round(bd.eff)} effective DPS, kill in ~${fmtT(ttk)} (${Math.round(ttk/maxT*100)}% of the clock).`,"g");
    else if(ttk<=maxT)log(`Damage is thin — ${Math.round(bd.eff)} effective DPS, kill in ~${fmtT(ttk)}. Cuts it close.`,"");
    else log(`Damage deficit — ${Math.round(bd.eff)} effective DPS, can't kill in time. Stalemate likely unless an Inspiration turns the fight.`,"t");}
  const hazards=[];
  if(cfg.dread>0)hazards.push(`Dread (${Math.round(cfg.dread*100)}%) — suppresses all damage; countered by Hope and Captain's Will`);
  if(cfg.shadow>0)hazards.push(`Shadow (${cfg.shadow}) — drains Will and Fate toward a floor; Radiance raises that floor`);
  if(cfg.cold>0)hazards.push(`Cold (${cfg.cold}) — extra Morale drain${cfg.warmth>0?" (Warmth antidote active)":""}`);
  if(cfg.heat>0)hazards.push(`Heat (${cfg.heat}) — extra Morale drain${cfg.heatease>0?" (Heat-ease active)":""}`);
  if(cfg.disease>0)hazards.push(`Disease (${cfg.disease}) — extra Morale drain${cfg.hale>0?" (Hale active)":""}`);
  if(cfg.wake>0)hazards.push(`Wakefulness (${cfg.wake}) — extra Morale drain${cfg.alert>0?" (Alert active)":""}`);
  if(hazards.length)hazards.forEach(h=>log(`Hazard: ${h}`,"t"));
  else log(`No hazards — a clean fight.`,"");
  log(`Rescues: Cael holds ${RESCUE_CAP} burst rescues and ${WARD_CAP} Warden lethal-spike guards.`,"");
  log(`――― ENGAGED ―――`,"");
}
```

Remove `refreshRead()`'s reference to `sustain()`/`foodOf()` (it currently computes `sus=sustain()` and a survive-chip based on sustain vs incoming — since Model B has no sustain-from-food concept, simplify `refreshRead()` to only compute the win-likelihood chip, not the survive chip):

```js
function refreshRead(){buildParty();
  if(MODE===1){setChip("read_win","UNLIKELY");$("read_hint").textContent="The Nine cannot be killed — survival is the only victory.";}
  else{const bd=fightDpsBreakdown(FIGHT);const boss=+$("boss").value;const ttk=bd.eff>0?boss/bd.eff:1e9;
    setChip("read_win",ttk<=+$("dur").value*0.7?"LIKELY":ttk<=+$("dur").value?"RISKY":"UNLIKELY");
    $("read_hint").textContent=ttk>+$("dur").value?"At this rate you won't finish in time.":"";}}
```

Remove the now-orphaned `read_surv`/`c-*` "Survive" chip from the Live tab's `.readbar` panel in the HTML body (the `<span>Survive <span class="chip c-risky" id="read_surv">&#8212;</span></span>` element) — Model B has no HP/s-vs-drain survive prediction to show there.

- [ ] **Step 5: Update `updLive()` and `buildSummary()` to read from `FIGHT`**

`updLive(bd, boss, incoming)`'s body already takes `bd`/`boss`/`incoming` as parameters (unchanged call shape from Step 3 above) — update its internal references from the old bare `M`/`P` reads to `FIGHT.M`/`FIGHT.cfg` and remove the `g_sus`/`g_inc`/`g_hold` sustain-vs-incoming gauge block (Model B has no per-member sustain value) along with its now-empty `.gauge` panel column in the HTML (the "Survive axis" `.panel.gauge` block in the Live tab). Keep the "Win axis & mitigation" and "DPS per character" gauge panels, updating their internal `M`→`FIGHT.M`, `P`→`FIGHT.cfg`, and `bd.effBy`→`bd.rawBy`-derived per-member effective values (mirroring the `effBy` computation already shown in Step 3's `series.push` block) references.

`buildSummary(verdict)` reads the module-level `M`/`t`/`events`/`rescuesUsed` — update every reference to `FIGHT.M`/`FIGHT.t`/`FIGHT.events`/`FIGHT.rescuesUsed`.

`finish(label, cls)` calls `buildSummary(label)` — no signature change needed, it already just forwards the label.

`reset` button's click handler currently resets scattered globals (`t=0`, `inspBoost=0`, etc.) — simplify to `FIGHT = null;` plus its existing DOM-reset lines (drop the two `g_sus`/`g_inc` id references from its `.forEach` id list, matching Step 5's gauge removal).

- [ ] **Step 6: Syntax check**

Run the same extraction check as Task 1 Step 3. Expected: `OK: script parses, <N> chars` with no SyntaxError.

Also grep for any remaining reference to deleted symbols:

```bash
grep -n "\bfoodOf(\|\bsustain(\|\.reserve\b|RMAX\b" tools/sim/hearthcraft_fight_sim.html
```

Expected: no output (empty) — confirms no dangling reference to the removed HP/s-food/reserve-pool code.

- [ ] **Step 7: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[hc] Browser sim: wire Live tab to the shared fight engine (removes HP/s food, per-member spikes, reserve pool)"
```

---

### Task 5: Resync and extend the Results tab

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html`

**Interfaces:**
- Consumes: `series` (per-tick trace, already repopulated correctly by Task 4 Step 3), `FIGHT` (final state after a completed fight — `FIGHT.dmgByMember`, `FIGHT.healByType`, `FIGHT.streakCount`, `FIGHT.streakBonusDmg`, `FIGHT.streakBonusHeal`, `FIGHT.hotActiveTicks`, `FIGHT.inspGraceHeal`, `FIGHT.inspHornRedirect`, `FIGHT.inspDawnHeal`, `FIGHT.inspDawnBonusDps`, `FIGHT.inspBlackArrowAmount`, all from Task 3).

`renderResults()` already reads `series`/`events`/`fired`/`baFired` as bare globals — since Task 4 removed those globals in favor of `FIGHT`, this task updates every read site and adds the new metric panels.

- [ ] **Step 1: Fix `renderResults()`'s existing reads**

Change every bare reference inside `renderResults()` from the old globals to `FIGHT`-scoped equivalents: `events` → `FIGHT.events`, `fired.horn`/`fired.dawn` → `FIGHT.fired.horn`/`FIGHT.fired.dawn`, `baFired` → `FIGHT.baFired`, `spikesTotal` → `FIGHT.spikesTotal`, `M[k].fatEvades` → `FIGHT.M[k].fatEvades`, `M[k]` (in the Morale-over-time chart and Wounds chart's `TPL[...]`/color lookups) → `FIGHT.M[k]`. `P.dread`/`P.phys`/`P.graceBase`/etc. → `FIGHT.cfg.dread`/`FIGHT.cfg.phys`/`FIGHT.cfg.graceBase`/etc.

Remove the "Keeper healing" panel's now-incorrect `f.healBy?f.healBy.keeper` read (that field no longer exists in `series` — Task 4 Step 3's `series.push` doesn't include a `healBy` field since healing is now tracked as `FIGHT.healByType`, a fight-level total, not a per-tick series value) — this panel is superseded by the new "Healing by source" panel in Step 2 below, so delete the old "Keeper healing" `<div class="panel chart-card">` block from the HTML body and its corresponding `renderResults()` code block (the `avgHeal`/`ch_keeperheal` section).

Remove the "Captain's boost to the party" panel and its `avgWillSaved`/`avgHopeSaved`/`avgDawnBoost`/`captainBoostTotal` computation — those relied on `willSaved`/`hopeSaved` series fields that Task 4 Step 3's simplified `series.push` no longer populates (the underlying Dread-mitigation breakdown these represented is still shown by the existing "Dread mitigation" panel via `layerADrag`, so this is not a loss of information, just removing a now-stale duplicate view). Delete its `<div class="panel chart-card">` block from the HTML body too.

The "Fate — Inspiration trigger rates" panel's `inspDefs` array currently reads `M[d.k].fat` — change to `FIGHT.M[d.k].fat`; its `P.graceBase` etc. → `FIGHT.cfg.graceBase` etc.

- [ ] **Step 2: Add the "Healing by source" panel**

In the HTML body, add a new chart-card panel to the `.results-grid` (place it where the old "Keeper healing" panel was removed from, in Step 1):

```html
<div class="panel chart-card"><div class="ch-h">Healing by source</div><div id="ch_healsource"></div></div>
```

In `renderResults()`, add:

```js
  // Healing by source — HoT / Triage / Group / Rescue
  const healTotal = FIGHT.healByType.hot + FIGHT.healByType.triage + FIGHT.healByType.group + FIGHT.healByType.rescue;
  if (healTotal > 0.01) {
    meterChart("ch_healsource",[
      {name:"HoT",          color:"#5b7f63", value:FIGHT.healByType.hot},
      {name:"Triage",       color:"#7d5a93", value:FIGHT.healByType.triage},
      {name:"Group Heal",   color:"#4a6c93", value:FIGHT.healByType.group},
      {name:"Rescue Burst", color:"#b8843c", value:FIGHT.healByType.rescue},
    ],"hp","Total healing");
    const hotUptimePct = FIGHT.t > 0 ? (FIGHT.hotActiveTicks / FIGHT.t * 100).toFixed(1) : "0.0";
    $("ch_healsource").innerHTML += `<div class="meter-footer">HoT uptime: ${hotUptimePct}% of fight duration</div>`;
  } else {
    $("ch_healsource").innerHTML='<div class="empty" style="padding:24px 14px;text-align:center">No healing this engagement.</div>';
  }
```

- [ ] **Step 3: Add the "Streak activity" panel**

In the HTML body:

```html
<div class="panel chart-card"><div class="ch-h">Fate — Streak activity</div><div id="ch_streak"></div></div>
```

In `renderResults()`:

```js
  // Streak activity — trigger count per member + bonus damage/healing
  const totalStreaks = ORDER.reduce((s,k)=>s+FIGHT.streakCount[k],0);
  if (totalStreaks > 0) {
    meterChart("ch_streak", ORDER.map(k=>({
      name: FIGHT.M[k].tpl.name+" ("+FIGHT.M[k].role+")",
      color: FIGHT.M[k].color,
      value: FIGHT.streakCount[k]
    })),"triggers",`Total: ${totalStreaks} · bonus damage ${Math.round(FIGHT.streakBonusDmg)} · bonus healing ${Math.round(FIGHT.streakBonusHeal)}`);
  } else {
    $("ch_streak").innerHTML='<div class="empty" style="padding:24px 14px;text-align:center">No streaks triggered this engagement.</div>';
  }
```

- [ ] **Step 4: Add Inspiration contribution amounts to the existing Inspiration timeline panel**

In `renderResults()`, immediately after the existing `$("insp_list").innerHTML=...` line, append a contribution summary:

```js
  const contribLines = [];
  if (FIGHT.inspGraceHeal > 0) contribLines.push(`Grace restored ${Math.round(FIGHT.inspGraceHeal)} HP`);
  if (FIGHT.inspHornRedirect > 0) contribLines.push(`Horn redirected ${Math.round(FIGHT.inspHornRedirect)} damage to the Warden`);
  if (FIGHT.inspDawnHeal > 0) contribLines.push(`Red Dawn restored ${Math.round(FIGHT.inspDawnHeal)} HP`);
  if (FIGHT.inspDawnBonusDps > 0) contribLines.push(`Red Dawn added ${Math.round(FIGHT.inspDawnBonusDps)} bonus damage`);
  if (FIGHT.inspBlackArrowAmount > 0) contribLines.push(FIGHT.cfg.unkill ? `Black Arrow cut ${Math.round(FIGHT.inspBlackArrowAmount)}s off the clock` : `Black Arrow burned ${Math.round(FIGHT.inspBlackArrowAmount)} resolve`);
  if (contribLines.length) {
    $("insp_list").innerHTML += `<div class="meter-footer" style="text-align:left;padding:8px 2px 0">${contribLines.join(" · ")}</div>`;
  }
```

- [ ] **Step 5: Syntax check**

Run the same extraction check as Task 1 Step 3. Expected: `OK: script parses, <N> chars` with no SyntaxError.

- [ ] **Step 6: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[hc] Browser sim: resync Results tab to shared engine, add healing/streak/Inspiration-contribution panels"
```

---

### Task 6: Verification — compare against `run_sim.js` baselines

**Files:**
- Create (temporary, not committed): a scratch verification script

**Interfaces:**
- None — this task produces no shipped code, only a confidence check that the resynced engine's win rates land in the same range `run_sim.js` produces for equivalent inputs.

Since `createFightState`/`fightTick` (Task 3) are pure and DOM-free, they can be extracted and run in Node directly, using the exact same `TPL`/`ORDER`/constants already defined earlier in the same file (also DOM-free).

- [ ] **Step 1: Write the extraction + comparison script**

```js
// /tmp/verify-browser-sim.js — scratch script, not committed
const fs = require('fs');
const html = fs.readFileSync('tools/sim/hearthcraft_fight_sim.html', 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
const mainScript = scripts[scripts.length - 1];

// Stub out the DOM calls the extracted script's top-level init code makes on load,
// so `new Function` + invocation doesn't throw — we only need the pure engine
// functions (createFightState, fightTick, TPL, ORDER, stat) to be defined afterward,
// not the actual UI to run.
const stubbedScript = mainScript.replace(
  /\/\/ init\n[\s\S]*$/,
  '' // drop everything from the "// init" comment to end of file (the DOM-touching init calls)
);
const context = {};
const fn = new Function('module_exports_target', `
  const $=()=>({value:0,addEventListener(){},style:{},classList:{toggle(){}}});
  ${stubbedScript}
  module_exports_target.createFightState = createFightState;
  module_exports_target.fightTick = fightTick;
  module_exports_target.TPL = TPL;
  module_exports_target.ORDER = ORDER;
`);
fn(context);

function runOneFight(cfg) {
  const state = context.createFightState(cfg);
  while (!state.done) context.fightTick(state);
  return state.outcome;
}

function winRate(cfg, n) {
  let wins = 0;
  for (let i = 0; i < n; i++) {
    const outcome = runOneFight(cfg);
    if (outcome === "VICTORY" || outcome === "REPELLED") wins++;
  }
  return wins / n;
}

// Same "easy" and "brutal" scenarios used in EncounterEngineTest.kt / run_sim.js
const easyCfg = {
  lvl: 10, roster: { warden: context.TPL.warden, fighter: context.TPL.fighterA, keeper: context.TPL.keeper, captain: context.TPL.captain },
  duration: 1800, boss: 15000, drain: 2, spike: 30, spikeiv: 20, phys: 0, unkill: false, burstmul: 1,
  potency: 0, dread: 0, hope: 0, shadow: 0, radiance: 0, disease: 0, hale: 0, cold: 0, warmth: 0, heat: 0, heatease: 0, wake: 0, alert: 0,
  graceBase: 0.05, hornBase: 0.03, dawnBase: 0.03, blackArrowCap: 0.06, memberFoodBonuses: {}
};
const brutalCfg = Object.assign({}, easyCfg, { boss: 200000, drain: 120, spike: 300, spikeiv: 5 });

const easyWin = winRate(easyCfg, 500);
const brutalWin = winRate(brutalCfg, 500);
console.log(`Easy config win rate:   ${(easyWin*100).toFixed(1)}%  (run_sim.js/EncounterEngineTest expect >75% wins)`);
console.log(`Brutal config win rate: ${(brutalWin*100).toFixed(1)}%  (expect near-0% wins — "defeat happens when encounter is overwhelming")`);
```

- [ ] **Step 2: Run it**

```bash
node /tmp/verify-browser-sim.js
```

Expected: "Easy config win rate" well above 75% (matching `EncounterEngineTest.kt`'s `keeper heals party — band survives easy encounter` expectation of >75%/200), and "Brutal config win rate" near 0% (matching that same test file's `defeat happens when encounter is overwhelming` test). If either is wildly off from these expectations, that indicates a porting error in Task 3 to go back and fix — do not proceed to declare this task done until both land in the expected ranges.

- [ ] **Step 3: Record the result**

No commit for this task (the verification script is scratch-only, not shipped). Write the actual console output (both percentages) into the task report so the reviewer can see the evidence without re-running it themselves.

---

## Manual Verification

No emulator/browser is available in this environment — the following need a human, browser-based check afterward:
- Setup tab: sliders still update the live stat preview correctly with the new engine
- Live tab: Run/Step/Reset buttons work, the log shows sensible narrative text, HP bars animate, Streak/HoT/Triage/Group-Heal/Rescue/Inspiration events appear in the log with the removed reserve-pool bar gone
- Results tab: all panels render (including the three new ones — Healing by source, Streak activity, Inspiration contribution amounts) with no console errors
- Company tab: Fighter role label displays correctly (not "Hunter") for Mira's card
- Confirm the Batch tab button and pane are gone, and the Setup tab's import panel note about `run_sim.js` reads sensibly
