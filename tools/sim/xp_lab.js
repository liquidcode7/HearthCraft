#!/usr/bin/env node
/* xp_lab.js — HearthCraft XP & leveling pacing simulator.
 *
 * Answers: how long to climb Cooking 1→50 and Gathering 1→50 under different
 * play styles, what fraction of XP comes from steady grind vs spikes, and
 * whether any profile is absurdly fast or slow.
 *
 * ALL CONFIG VALUES ARE PLACEHOLDERS. The sim exists to FIND good values.
 * Sweep the knobs, read the output, bring the options to Wes.
 *
 * Run: node tools/sim/xp_lab.js
 */

'use strict';
const FM = require('./food_model');
const { TIER_TABLE } = FM;

// ── CONFIG — every tunable constant lives here ────────────────────────────────
const CONFIG = {

  // Simulation harness
  SEED: 42,
  PLAYERS_PER_PROFILE: 60,   // Monte Carlo players averaged per profile
  SIM_DAYS: 200,

  // Time model
  activeMinutesPerDay: 45,   // active cooking/discovery/mastery window per day
  forageCyclesPerDay: 48,    // background forage cycles (1 per 30 min, 24h wall-clock)

  // ── Level curve — try 'power' or 'tierWall' first ──────────────────────────
  // PLACEHOLDER — these numbers are deliberately rough starting points
  curveType: 'tierWall',     // 'linear' | 'power' | 'exponential' | 'tierWall'
  curveA: 25,                // base XP at level 1  (was 100 — flattened so Tier 4+ is reachable)
  curveB: 25,                // linear only: xpToNext = A + B*level
  curveP: 1.2,               // power / tierWall exponent  (was 1.8 — gentler climb)
  curveBase: 1.10,           // exponential only: A * BASE^level
  wallMultiplier: 1.8,       // tierWall: extra multiplier AT tier-boundary levels  (was 2.2)

  // Gathering uses the same machinery, own constants
  gatherCurveType: 'power',  // gathering has no tier table so no walls needed
  gatherCurveA: 20,          // (was 80)
  gatherCurveP: 1.35,        // (was 1.15 — steepen back half so Lv50 isn't trivially reachable)

  // Soft diminishing returns — flattens to floor, never zeroes
  // softDR(n) = floor + (1-floor) / (1 + n/halfLife)
  DR_FLOOR: 0.15,            // grind stays at least 15% of base reward
  DR_HALF_LIFE: 8,           // reward halved (above floor) after ~8 repeats

  // ── Cooking XP sources ────────────────────────────────────────────────────
  DISC_XP: 60,               // per new recipe discovered — once per recipe, un-grindable
  FIRSTCOOK_XP: 35,          // per recipe, first time cooked — once per recipe
  REPEAT_XP: 28,             // per repeat cook, before softDR  (was 15 — steady backbone)
  MASTERY_XP: 30,            // per mastery refinement step
  MASTERY_CAP: 90,           // max mastery XP earnable per dish (finite, self-limiting)
  WIN_XP: 22,                // per winning encounter provisioned  (was 60 — was dominating)

  // ── Gathering XP sources ──────────────────────────────────────────────────
  FIRSTSOURCE_XP: 130,       // per new ingredient / new-region flora  (was 70 — spikes too quiet)
  HARVEST_XP: 3,             // per forage cycle, scaled by grade  (was 5 — 48 cycles/day so choke firehose)
  ROUTINE_XP: 2,             // per forage cycle, softDR on ingredient  (was 3)
  SRC_WIN_ENABLED: true,
  SRC_WIN_XP: 65,            // when your ingredient fed a winning fight  (was 25 — needs to be a real spike)

  // Grade multipliers: poor / standard / fine / pristine
  // Gathering level raises floor + consistency; region ceiling caps the top
  GRADE_MULTS: [0.5, 1.0, 1.6, 2.4],

  // World-stage gating: what's available by cook-level threshold
  // Stage 0 = Bree, Stage 1 = next region, Stage 2 = deeper
  stageTransitions: [20, 40],      // cook level at which the world advances to stage 1, 2
  recipesByStage:    [5, 9, 14],   // cumulative available recipes per stage
  ingredientsByStage: [6, 10, 16], // cumulative available ingredients per stage
  regionCeilingByStage: [1, 2, 3], // max grade index (into GRADE_MULTS) per stage
};

// ── Tier boundary levels (from TIER_TABLE — single source of truth) ───────────
const TIER_BOUNDARIES = new Set(TIER_TABLE.map(t => t.lo));

function tierFor(level) {
  for (let i = TIER_TABLE.length - 1; i >= 0; i--) {
    if (level >= TIER_TABLE[i].lo) return TIER_TABLE[i].tier;
  }
  return 1;
}

// ── Level curve ───────────────────────────────────────────────────────────────
function xpToNext(level, cfg, track) {
  const A  = track === 'gather' ? cfg.gatherCurveA : cfg.curveA;
  const P  = track === 'gather' ? cfg.gatherCurveP : cfg.curveP;
  const ct = track === 'gather' ? cfg.gatherCurveType : cfg.curveType;

  let base;
  if (ct === 'linear') {
    base = A + cfg.curveB * level;
  } else if (ct === 'exponential') {
    base = A * Math.pow(cfg.curveBase, level);
  } else if (ct === 'tierWall') {
    base = A * Math.pow(level, P);
    if (TIER_BOUNDARIES.has(level) && level > 1) base *= cfg.wallMultiplier;
  } else {
    // 'power' (default)
    base = A * Math.pow(level, P);
  }
  return Math.max(1, Math.round(base));
}

// ── Soft diminishing returns ──────────────────────────────────────────────────
function softDR(n, cfg) {
  return cfg.DR_FLOOR + (1 - cfg.DR_FLOOR) / (1 + n / cfg.DR_HALF_LIFE);
}

// ── Grade sampler ─────────────────────────────────────────────────────────────
// Gathering level raises mean grade; regionCeiling hard-caps it.
function sampleGrade(gatherLevel, regionCeiling, cfg, rng) {
  const maxGrade = Math.min(regionCeiling, cfg.GRADE_MULTS.length - 1);
  // Mean grade index scales from ~0.3 at lvl1 up to maxGrade at lvl50
  const mean = maxGrade * (0.2 + 0.8 * (gatherLevel - 1) / 49);
  const weights = cfg.GRADE_MULTS.map((_, i) => {
    if (i > maxGrade) return 0;
    return Math.exp(-Math.abs(i - mean) * 1.5);
  });
  const total = weights.reduce((a, b) => a + b, 0);
  let r = rng() * total;
  for (let i = 0; i < weights.length; i++) {
    r -= weights[i];
    if (r <= 0) return i;
  }
  return maxGrade;
}

// ── Seeded PRNG (mulberry32) ──────────────────────────────────────────────────
function makeRNG(seed) {
  let s = seed >>> 0;
  return () => {
    s |= 0; s = s + 0x6D2B79F5 | 0;
    let t = Math.imul(s ^ s >>> 15, 1 | s);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}

// ── Player profiles ───────────────────────────────────────────────────────────
const PROFILES = {
  Explorer: {
    label: 'Explorer (intended)',
    discoveriesPerDay: 1.5,    // high discovery appetite; chases new recipes
    cooksPerDay: 8,
    masteryStepsPerDay: 3,
    fightsWonPerDay: 1.5,      // provisions real fights
    sourcingReach: 0.85,       // chases quality ingredients; varied sourcing
    discoveryAppetite: 0.90,
  },
  Grinder: {
    label: 'Grinder',
    discoveriesPerDay: 0.2,    // rarely unlocks new recipes
    cooksPerDay: 14,           // hammers the same cheap cook
    masteryStepsPerDay: 0.5,
    fightsWonPerDay: 0.3,
    sourcingReach: 0.10,       // safe garden only; minimal new ingredients
    discoveryAppetite: 0.10,
  },
  Completionist: {
    label: 'Completionist',
    discoveriesPerDay: 2.5,
    cooksPerDay: 10,
    masteryStepsPerDay: 6,
    fightsWonPerDay: 1.0,
    sourcingReach: 1.00,
    discoveryAppetite: 1.00,
  },
  IdleOnly: {
    label: 'Idle-only',
    discoveriesPerDay: 0.3,
    cooksPerDay: 2,
    masteryStepsPerDay: 0.5,
    fightsWonPerDay: 0.2,
    sourcingReach: 0.20,
    discoveryAppetite: 0.20,
  },
};

// ── Single player simulation ───────────────────────────────────────────────────
function simulatePlayer(profile, cfg, rng) {
  let cookLvl = 1, cookXP = 0;
  let gathLvl = 1, gathXP = 0;

  const knownRecipes = [];               // ordered list of discovered recipe IDs
  const timesCookedMap = {};             // recipeId → cook count
  const masteryEarned = {};              // recipeId → cumulative mastery XP
  const knownIngredients = [];
  const timesHarvestedMap = {};

  const xp = {
    cook: { discovery: 0, firstCook: 0, repeatCook: 0, mastery: 0, provisioningWin: 0 },
    gather: { firstSource: 0, qualityHarvest: 0, routineHarvest: 0, sourcedWin: 0 },
  };

  const cookTierDays  = {};  // tier  → first day reached
  const gatherMile    = {};  // level → first day reached (for 10,20,30,40,50)

  function addCookXP(amt, src) {
    xp.cook[src] += amt;
    cookXP += amt;
    while (cookLvl < 50 && cookXP >= xpToNext(cookLvl, cfg, 'cook')) {
      cookXP -= xpToNext(cookLvl, cfg, 'cook');
      cookLvl++;
    }
    if (cookLvl >= 50) { cookLvl = 50; cookXP = 0; }
  }

  function addGathXP(amt, src) {
    xp.gather[src] += amt;
    gathXP += amt;
    while (gathLvl < 50 && gathXP >= xpToNext(gathLvl, cfg, 'gather')) {
      gathXP -= xpToNext(gathLvl, cfg, 'gather');
      gathLvl++;
    }
    if (gathLvl >= 50) { gathLvl = 50; gathXP = 0; }
  }

  function currentStage() {
    for (let i = cfg.stageTransitions.length - 1; i >= 0; i--) {
      if (cookLvl >= cfg.stageTransitions[i]) return i + 1;
    }
    return 0;
  }

  function stageVal(arr) {
    return arr[Math.min(currentStage(), arr.length - 1)];
  }

  const snapshots = [];

  for (let day = 1; day <= cfg.SIM_DAYS; day++) {
    const availRecipes = stageVal(cfg.recipesByStage);
    const availIngred  = stageVal(cfg.ingredientsByStage);
    const regCeil      = stageVal(cfg.regionCeilingByStage);

    // ── ACTIVE: discovery ───────────────────────────────────────────────────
    const wantDisc = profile.discoveriesPerDay * profile.discoveryAppetite;
    const numDisc = Math.floor(wantDisc) + (rng() < (wantDisc % 1) ? 1 : 0);
    for (let d = 0; d < numDisc; d++) {
      if (knownRecipes.length < availRecipes) {
        const rid = `r${knownRecipes.length}`;
        knownRecipes.push(rid);
        timesCookedMap[rid] = 0;
        masteryEarned[rid]  = 0;
        addCookXP(cfg.DISC_XP, 'discovery');
      }
    }

    // ── ACTIVE: cooking ─────────────────────────────────────────────────────
    if (knownRecipes.length > 0) {
      const numCooks = Math.round(profile.cooksPerDay * (0.7 + rng() * 0.6));
      for (let c = 0; c < numCooks; c++) {
        // Grinder fixates on recipe 0; explorer picks at random
        let rid;
        if (profile.sourcingReach < 0.3) {
          rid = knownRecipes[0];
        } else {
          rid = knownRecipes[Math.floor(rng() * knownRecipes.length)];
        }
        if (timesCookedMap[rid] === 0) {
          addCookXP(cfg.FIRSTCOOK_XP, 'firstCook');
        }
        timesCookedMap[rid]++;
        addCookXP(Math.round(cfg.REPEAT_XP * softDR(timesCookedMap[rid], cfg)), 'repeatCook');
      }
    }

    // ── ACTIVE: mastery ─────────────────────────────────────────────────────
    if (knownRecipes.length > 0) {
      const numMastery = Math.round(profile.masteryStepsPerDay * (0.5 + rng() * 1.0));
      for (let m = 0; m < numMastery; m++) {
        const rid = knownRecipes[Math.floor(rng() * knownRecipes.length)];
        const earned = masteryEarned[rid];
        if (earned < cfg.MASTERY_CAP) {
          const gain = Math.min(cfg.MASTERY_XP, cfg.MASTERY_CAP - earned);
          masteryEarned[rid] += gain;
          addCookXP(gain, 'mastery');
        }
      }
    }

    // ── ACTIVE: provisioning wins ───────────────────────────────────────────
    const numFights = Math.round(profile.fightsWonPerDay * (0.4 + rng() * 1.2));
    for (let f = 0; f < numFights; f++) {
      addCookXP(cfg.WIN_XP, 'provisioningWin');
      if (cfg.SRC_WIN_ENABLED) addGathXP(cfg.SRC_WIN_XP, 'sourcedWin');
    }

    // ── BACKGROUND: foraging (wall-clock, not gated by active minutes) ──────
    for (let fc = 0; fc < cfg.forageCyclesPerDay; fc++) {
      // Chance to discover a new ingredient
      if (knownIngredients.length < availIngred) {
        if (rng() < profile.sourcingReach * 0.12) {
          const iid = `i${knownIngredients.length}`;
          knownIngredients.push(iid);
          timesHarvestedMap[iid] = 0;
          addGathXP(cfg.FIRSTSOURCE_XP, 'firstSource');
        }
      }

      if (knownIngredients.length === 0) continue;

      // Pick ingredient (grinder stays on #0; explorer varies)
      let iid;
      if (profile.sourcingReach < 0.3) {
        iid = knownIngredients[0];
      } else {
        iid = knownIngredients[Math.floor(rng() * knownIngredients.length)];
      }

      const gradeIdx = sampleGrade(gathLvl, regCeil, cfg, rng);
      addGathXP(Math.round(cfg.HARVEST_XP * cfg.GRADE_MULTS[gradeIdx]), 'qualityHarvest');

      timesHarvestedMap[iid]++;
      addGathXP(Math.round(cfg.ROUTINE_XP * softDR(timesHarvestedMap[iid], cfg)), 'routineHarvest');
    }

    // ── Record milestones ───────────────────────────────────────────────────
    const ct = tierFor(cookLvl);
    if (!cookTierDays[ct]) cookTierDays[ct] = day;
    for (const ml of [10, 20, 30, 40, 50]) {
      if (gathLvl >= ml && !gatherMile[ml]) gatherMile[ml] = day;
    }

    snapshots.push({ day, cookLvl, gathLvl });
  }

  return { snapshots, xp, cookTierDays, gatherMile };
}

// ── Average many players per profile ─────────────────────────────────────────
function runProfile(profileName, cfg) {
  const profile = PROFILES[profileName];
  const N = cfg.PLAYERS_PER_PROFILE;
  const pidx = Object.keys(PROFILES).indexOf(profileName);

  const allSnaps = [];
  const cookTierAccum = {};
  const gatherMileAccum = {};
  const xpSum = {
    cook:   { discovery: 0, firstCook: 0, repeatCook: 0, mastery: 0, provisioningWin: 0 },
    gather: { firstSource: 0, qualityHarvest: 0, routineHarvest: 0, sourcedWin: 0 },
  };

  for (let p = 0; p < N; p++) {
    const rng = makeRNG(cfg.SEED + pidx * 100000 + p * 997);
    const res = simulatePlayer(profile, cfg, rng);
    allSnaps.push(res.snapshots);

    for (const [tier, day] of Object.entries(res.cookTierDays)) {
      if (!cookTierAccum[tier]) cookTierAccum[tier] = [];
      cookTierAccum[tier].push(day);
    }
    for (const [ml, day] of Object.entries(res.gatherMile)) {
      if (!gatherMileAccum[ml]) gatherMileAccum[ml] = [];
      gatherMileAccum[ml].push(day);
    }
    for (const [s, v] of Object.entries(res.xp.cook))   xpSum.cook[s]   += v;
    for (const [s, v] of Object.entries(res.xp.gather)) xpSum.gather[s] += v;
  }

  const cookTierAvg   = {};
  for (const [t, days] of Object.entries(cookTierAccum))
    cookTierAvg[t] = Math.round(days.reduce((a, b) => a + b, 0) / days.length);

  const gatherMileAvg = {};
  for (const [ml, days] of Object.entries(gatherMileAccum))
    gatherMileAvg[ml] = Math.round(days.reduce((a, b) => a + b, 0) / days.length);

  // Per-day averages across all players
  const avgSnaps = allSnaps[0].map((_, di) => ({
    day:     di + 1,
    cookLvl: allSnaps.reduce((s, r) => s + r[di].cookLvl, 0) / N,
    gathLvl: allSnaps.reduce((s, r) => s + r[di].gathLvl, 0) / N,
  }));

  const cookTotal  = Object.values(xpSum.cook).reduce((a, b) => a + b, 0);
  const gathTotal  = Object.values(xpSum.gather).reduce((a, b) => a + b, 0);
  const cookPct    = {};
  const gathPct    = {};
  for (const [s, v] of Object.entries(xpSum.cook))
    cookPct[s]  = cookTotal  > 0 ? v / cookTotal  * 100 : 0;
  for (const [s, v] of Object.entries(xpSum.gather))
    gathPct[s]  = gathTotal  > 0 ? v / gathTotal  * 100 : 0;

  return { profileName, label: profile.label, avgSnaps, cookTierAvg, gatherMileAvg, cookPct, gathPct };
}

// ── Formatting helpers ────────────────────────────────────────────────────────
const pL = (s, n) => String(s).padStart(n);
const pR = (s, n) => String(s).padEnd(n);
const pct = (n)   => n.toFixed(1).padStart(5) + '%';
const fday = (d)  => d ? `day ${String(d).padStart(3)}` : '>200d';

// ── Output 1: Days-to-milestone table ─────────────────────────────────────────
function printMilestoneTable(results) {
  console.log('\n══ 1. DAYS-TO-MILESTONE ════════════════════════════════════════════════════════');
  const COL = 12;
  const names = results.map(r => r.profileName);
  console.log('\n' + pR('', 22) + names.map(n => pL(n, COL)).join(''));
  console.log('─'.repeat(22 + names.length * COL));

  console.log('  COOK TIERS');
  for (let tier = 2; tier <= 7; tier++) {
    const info = TIER_TABLE[tier - 1];
    const lbl = `    Tier ${tier} ${info.title} (Lv${info.lo})`;
    console.log(pR(lbl, 22) + results.map(r => pL(fday(r.cookTierAvg[tier]), COL)).join(''));
  }

  console.log('  GATHER LEVELS');
  for (const ml of [10, 20, 30, 40, 50]) {
    const lbl = `    Gather Lv ${ml}`;
    console.log(pR(lbl, 22) + results.map(r => pL(fday(r.gatherMileAvg[ml]), COL)).join(''));
  }
}

// ── Output 2: XP source breakdown ────────────────────────────────────────────
function printXPBreakdown(results) {
  console.log('\n══ 2. XP SOURCE BREAKDOWN (grind-ratio readout) ════════════════════════════════\n');
  for (const r of results) {
    console.log(`  ── ${r.label}`);
    console.log('     COOKING:');
    for (const [src, val] of Object.entries(r.cookPct))
      console.log(`       ${pR(src, 18)} ${pct(val)}`);
    const steadyCook = (r.cookPct.repeatCook || 0) + (r.cookPct.mastery || 0);
    const spikeCook  = (r.cookPct.discovery  || 0) + (r.cookPct.firstCook || 0) + (r.cookPct.provisioningWin || 0);
    console.log(`       ─── steady (repeat+mastery) ${pct(steadyCook)}  |  spikes (disc+first+win) ${pct(spikeCook)}`);
    console.log('     GATHERING:');
    for (const [src, val] of Object.entries(r.gathPct))
      console.log(`       ${pR(src, 18)} ${pct(val)}`);
    const steadyGath = (r.gathPct.routineHarvest || 0) + (r.gathPct.qualityHarvest || 0);
    const spikeGath  = (r.gathPct.firstSource   || 0) + (r.gathPct.sourcedWin      || 0);
    console.log(`       ─── steady (routine+quality) ${pct(steadyGath)}  |  spikes (first+win)  ${pct(spikeGath)}`);
    console.log('');
  }
  console.log('  TARGET: steady ≈ 55–70%, spikes ≈ 30–45%  (tune REPEAT_XP / DISC_XP / WIN_XP)');
}

// ── Output 3: ASCII level-over-days curves ────────────────────────────────────
function printASCIICurves(results) {
  const DAYS = [1, 5, 10, 20, 35, 50, 75, 100, 150, 200];
  console.log('\n══ 3. LEVEL PROGRESSION OVER DAYS ══════════════════════════════════════════════');
  console.log(`\n  day →     ${DAYS.map(d => pL(d, 5)).join('')}`);
  console.log('─'.repeat(14 + DAYS.length * 5));

  for (const r of results) {
    const snap = (d) => r.avgSnaps.find(s => s.day === d) || { cookLvl: 1, gathLvl: 1 };
    const cRow = DAYS.map(d => pL(snap(d).cookLvl.toFixed(0), 5)).join('');
    const gRow = DAYS.map(d => pL(snap(d).gathLvl.toFixed(0), 5)).join('');
    console.log(`\n  ${r.label}`);
    console.log(`    cook  ${cRow}`);
    console.log(`    gathr ${gRow}`);

    // Bar chart — fill from day 1 to 200 (cook = █, gather = ▒)
    const W = 50;
    const lastC = r.avgSnaps[r.avgSnaps.length - 1]?.cookLvl || 1;
    const lastG = r.avgSnaps[r.avgSnaps.length - 1]?.gathLvl || 1;
    const fc = Math.round((lastC - 1) / 49 * W);
    const fg = Math.round((lastG - 1) / 49 * W);
    console.log(`    cook  ${'█'.repeat(fc)}${'░'.repeat(W - fc)} Lv${lastC.toFixed(0)} at day 200`);
    console.log(`    gathr ${'▒'.repeat(fg)}${'░'.repeat(W - fg)} Lv${lastG.toFixed(0)} at day 200`);
  }
}

// ── Output 4: Explorer vs Grinder ────────────────────────────────────────────
function printExplorerVsGrinder(results) {
  console.log('\n══ 4. EXPLORER vs GRINDER — days to Cook Tier 5 (Adept, Lv23) ═════════════════');
  const exp = results.find(r => r.profileName === 'Explorer');
  const grd = results.find(r => r.profileName === 'Grinder');
  const eDay = exp?.cookTierAvg[5];
  const gDay = grd?.cookTierAvg[5];
  console.log(`\n  Explorer      ${fday(eDay)}`);
  console.log(`  Grinder       ${fday(gDay)}`);

  if (eDay && gDay) {
    const gap = gDay - eDay;
    if (gap > 5)
      console.log(`\n  Explorer is ${gap} days ahead. Intended play pacing leads. ✓`);
    else if (gap >= -5)
      console.log(`\n  Within 5 days — effectively tied. Explorer should ideally be modestly ahead.`);
    else
      console.log(`\n  Grinder is ${-gap} days ahead. Boost DISC_XP / WIN_XP or reduce REPEAT_XP.`);
  }

  // Also show cook tier 5 for all profiles in one line
  console.log('\n  All profiles at Cook Tier 5:');
  for (const r of results)
    console.log(`    ${pR(r.label, 28)} ${fday(r.cookTierAvg[5])}`);
}

// ── Output 5: Sensitivity sweeps ─────────────────────────────────────────────
// Uses 10 players per data point (fast; shows trends, not precise values)
function sweepExplorer(overrides, cfg) {
  const N = 10;
  const c = { ...cfg, ...overrides, PLAYERS_PER_PROFILE: N };
  return runProfile('Explorer', c);
}

function printSensitivitySweep(cfg) {
  console.log('\n══ 5. SENSITIVITY SWEEPS ════════════════════════════════════════════════════════');

  // Sweep A: DISC_XP → effect on Cook grind ratio and days-to-T5
  console.log('\n  Sweep A: DISC_XP  (Explorer — effect on steady%, days to Tier 5)\n');
  console.log(`  ${pR('DISC_XP', 10)} ${pR('steady%', 10)} ${pR('spike%', 10)} ${pR('days→T5', 10)}`);
  for (const dv of [30, 60, 100, 150, 220]) {
    const r = sweepExplorer({ DISC_XP: dv }, cfg);
    const steady = (r.cookPct.repeatCook || 0) + (r.cookPct.mastery || 0);
    const spike  = (r.cookPct.discovery  || 0) + (r.cookPct.firstCook || 0) + (r.cookPct.provisioningWin || 0);
    console.log(`  ${pR(dv, 10)} ${pR(steady.toFixed(1) + '%', 10)} ${pR(spike.toFixed(1) + '%', 10)} ${fday(r.cookTierAvg[5])}`);
  }

  // Sweep B: curve exponent P → pacing at top end
  console.log('\n  Sweep B: curve exponent P (power curve)  — Explorer days to Tier 5 and Tier 7\n');
  console.log(`  ${pR('P', 8)} ${pR('days→T5', 10)} ${pR('days→T7', 10)}`);
  for (const pv of [1.4, 1.6, 1.8, 2.0, 2.3]) {
    const r = sweepExplorer({ curveType: 'power', gatherCurveType: 'power', curveP: pv }, cfg);
    console.log(`  ${pR(pv, 8)} ${pR(fday(r.cookTierAvg[5]), 10)} ${pR(fday(r.cookTierAvg[7]), 10)}`);
  }

  // Sweep C: curve shape — power vs tierWall
  console.log('\n  Sweep C: curve shape (power vs tierWall)  — Explorer days to each tier\n');
  const tierHeader = [2, 3, 4, 5, 6, 7].map(t => pL(`T${t}`, 8)).join('');
  console.log(`  ${pR('shape', 14)} ${tierHeader}`);
  for (const shape of ['power', 'tierWall']) {
    const r = sweepExplorer({ curveType: shape }, cfg);
    const tierRow = [2, 3, 4, 5, 6, 7].map(t => pL(r.cookTierAvg[t] ? `d${r.cookTierAvg[t]}` : '>200', 8)).join('');
    console.log(`  ${pR(shape, 14)} ${tierRow}`);
  }
  console.log('\n  tierWall should show a clear deceleration at boundary levels (T2=Lv5, T3=Lv10 …)');
  console.log('  If T5 is the same under both shapes, wallMultiplier may need raising.');
}

// ── MAIN ──────────────────────────────────────────────────────────────────────
console.log('');
console.log('HearthCraft XP Lab  ─  pacing & grind-ratio simulator');
console.log(`curve: ${CONFIG.curveType}  A=${CONFIG.curveA}  P=${CONFIG.curveP}  wall×${CONFIG.wallMultiplier}`);
console.log(`players/profile: ${CONFIG.PLAYERS_PER_PROFILE}  days: ${CONFIG.SIM_DAYS}  seed: ${CONFIG.SEED}`);
console.log('ALL CONFIG VALUES ARE PLACEHOLDERS. Sweep the knobs, read the ratios.');

const results = Object.keys(PROFILES).map(name => runProfile(name, CONFIG));

printMilestoneTable(results);
printXPBreakdown(results);
printASCIICurves(results);
printExplorerVsGrinder(results);
printSensitivitySweep(CONFIG);

console.log('\n══ NEXT EXPERIMENTS ════════════════════════════════════════════════════════════');
console.log('  §10 #1  baseline: review §1 milestone table — are tier paces remotely plausible?');
console.log('  §10 #2  grind ratio: tune REPEAT_XP / ROUTINE_XP vs DISC_XP / WIN_XP');
console.log('          target in §2: steady ≈ 55-70%, spikes ≈ 30-45%');
console.log('  §10 #3  explorer lead: grinder should NOT dramatically outpace explorer (§4)');
console.log('  §10 #4  curve shape: does tierWall create felt deceleration at tier 3, 4, 5?');
console.log('\n  Primary knobs: DISC_XP · REPEAT_XP · WIN_XP · curveP · wallMultiplier · DR_HALF_LIFE');
