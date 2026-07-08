#!/usr/bin/env node
// HearthCraft headless combat simulator
// Same math as hearthcraft_fight_sim.html — no DOM required.
// Usage:
//   node run_sim.js [--runs N] [--level L] [--band ID] [--duration S]
//                   (--band selects which band's real band_members.json stats
//                    to use -- greycloaks (default), mithlost, or undermarch)
//                   [--recipe ID]        (same recipe stat bonuses for all members)
//                   [--recipes W,F,K,C]  (per-member recipe IDs)
//                   [--statbonus N]      (flat bonus added to all recipe stat outputs; simulates grade step)
//                   [--boss R] [--drain D] [--spike S] [--spikeiv I]
//                   [--phys P] [--dread V] [--shadow V] [--cold V] [--heat V]
//                   [--disease V] [--wake V] [--hope V] [--radiance V]
//                   [--hale V] [--warmth V] [--heatease V] [--alert V]
//                   [--survival] [--fighter might] [--burstmul F] [--verbose]
//                   [--beta B]      (cascade exponent: 1=cascade, 0=neutral/default (matches EncounterEngine.kt's
//                                    flat drain/4 -- cascade was explicitly removed there by design), <0=last-stand)
//                   [--decouple]    (independent per-member spikes instead of one global spike)
//                   [--sink]        (replace 40% softcap with a reserve pool that absorbs spikes first)
//                   [--rmax R]      (reserve pool cap for --sink mode; default 50)
//
// Food gives stat bonuses only (Model B — HP/s removed).
//
// Runs N fights and prints aggregate stats + Inspiration fire rates + full
// DPS/healing/streak/Inspiration-contribution breakdowns.

const FM = require('./food_model');

// ── party templates — loaded from the real shipped data, not hand-copied ───
// (Found 08 Jul 2026: this file previously hardcoded a full legacy pre-rescale
// stat block here, e.g. warden vit=15/growth=0.55. The real game rescaled
// band_members.json/growth_curves.json on 2026-07-03 -- smaller starting
// stats, compound %/level growth (see CombatLeveling.kt) instead of flat
// linear -- but this hardcoded copy was never updated, so every sim result
// run against it was describing a party far stronger than the real game's.
// Loading directly from the real data files closes that drift class for good.)
const BAND_MEMBERS   = require('../../app/src/main/assets/data/band_members.json');
const GROWTH_CURVES  = require('../../app/src/main/assets/data/growth_curves.json');

function curveFor(roleKey) {
  const curve = GROWTH_CURVES.find(c => c.role === roleKey);
  if (!curve) throw new Error(`No growth curve for role "${roleKey}"`);
  return { mig: curve.migGrowth, agi: curve.agiGrowth, vit: curve.vitGrowth, wil: curve.wilGrowth, fat: curve.fatGrowth };
}

function startFor(member) {
  return { mig: member.startingMight, agi: member.startingAgility, vit: member.startingVitality, wil: member.startingWill, fat: member.startingFate };
}

// Builds the TPL structure (same shape the rest of this file already expects:
// {warden, fighterA, fighterM, keeper, captain}, each {name, role, soak, start, grow})
// from the real band_members.json for the given band.
function buildTpl(bandId) {
  const members = BAND_MEMBERS.filter(m => m.bandId === bandId);
  const byRole = role => members.find(m => m.role === role);
  const warden  = byRole("warden");
  const fighter = byRole("fighter");
  const keeper  = byRole("keeper");
  const captain = byRole("captain");
  if (!warden || !fighter || !keeper || !captain) {
    throw new Error(`band "${bandId}" is missing one or more of warden/fighter/keeper/captain in band_members.json`);
  }
  return {
    warden:  { name: warden.id,  role: "Warden",  soak: true,  start: startFor(warden),  grow: curveFor("warden") },
    fighterA:{ name: fighter.id, role: "Fighter", soak: false, start: startFor(fighter), grow: curveFor("fighter_ranged") },
    fighterM:{ name: fighter.id, role: "Fighter", soak: false, start: startFor(fighter), grow: curveFor("fighter_melee") },
    keeper:  { name: keeper.id,  role: "Keeper",  soak: false, start: startFor(keeper),  grow: curveFor("keeper") },
    captain: { name: captain.id, role: "Captain", soak: true,  start: startFor(captain), grow: curveFor("captain") },
  };
}
const ORDER        = ["warden","fighter","keeper","captain"];
const PEN_SCALE    = 80;  // must match hearthcraft_fight_sim.html
const SHADOW_FLOOR = 0.55;
const SHADOW_RATE  = 0.0011;
const RESCUE_CAP   = 5;
const WARD_CAP     = 3;
const GRIEVOUS     = 5;
const DREAD_VAR_COEF       = 0.008;
const DREAD_FLOOR_COEF     = 0.003;
const T_TEMP               = 0.65;
const T_PERM               = 0.35;
const STUN_TICKS           = 5;
const BREAK_CHECK_INTERVAL = 30;
const CAPTAIN_STUN_WEIGHT  = 0.3;
const CAPTAIN_BREAK_WEIGHT = 0.15;

const HOT_DURATION    = 8;    // ticks per HoT application — must match EncounterEngine.kt
const HOT_HEAL_MUL    = 0.15; // healPerTick = keeper.wil * HOT_HEAL_MUL
const TRIAGE_HP       = 0.25; // triage fires when target hp < max * TRIAGE_HP
const TRIAGE_MUL      = 2.0;  // triageHeal = keeper.wil * TRIAGE_MUL
const TRIAGE_COOLDOWN = 2;    // ticks between triage uses
const GROUP_HEAL_IV   = 5;    // ticks between group heals — resynced to match EncounterEngine.kt (was stale at 20)
const GROUP_HEAL_MUL  = 1.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL — resynced (was stale at 0.5)
const CAPTAIN_HEAL_INTERVAL = 12;   // ticks between Captain's burst-heal casts — must match EncounterEngine.kt
const CAPTAIN_HEAL_MUL      = 1.0;  // heal = captain.wil * CAPTAIN_HEAL_MUL, delivered as a single instant burst

// ── Fate streak constants (must match EncounterEngine.kt Task 7) ───────────
const STREAK_K          = 0.002; // trigger prob per eligible tick = fat * STREAK_K
const STREAK_REFRACTORY = 20;    // ticks before streak can re-trigger
const STREAK_DURATION   = 5;     // streak active ticks
const STREAK_MULT       = 1.5;   // DPS/heal multiplier during streak

// Compound %/level growth, matching CombatLeveling.kt's statAtLevel exactly
// (startingStat * (1 + growthRate)^(level-1)) -- NOT flat-linear. This file
// used a flat-linear formula until 08 Jul 2026, another silent drift from
// the real game's 2026-07-03 compound-growth rewrite.
const statAt  = (tpl, k, lvl) => tpl.start[k] * Math.pow(1 + tpl.grow[k], lvl - 1);
const moraleOf= (tpl, lvl)    => Math.round(30 + statAt(tpl,"vit",lvl) * 32);
const fmtT    = s => `${Math.floor(s/60)}:${String(Math.max(0,s)%60).padStart(2,"0")}`;
const clamp   = (v,lo,hi)     => Math.max(lo, Math.min(hi, v));

// ── CLI parsing ────────────────────────────────────────────────────────────
function parseArgs() {
  const a = process.argv.slice(2);
  const get = (flag, def) => { const i = a.indexOf(flag); return i>=0 ? a[i+1] : def; };
  const num = (flag, def) => parseFloat(get(flag, def));

  // Food: recipes provide stat bonuses only (no HP/s).
  // --recipe ID       same recipe stat bonuses for all members
  // --recipes W,F,K,C per-member recipe IDs
  // --statbonus N     flat bonus added to all recipe stat outputs (simulates grade step)
  const recipeArg  = get("--recipe",  null);
  const recipesArg = get("--recipes", null);
  const memberRecipes = { warden:null, fighter:null, keeper:null, captain:null };
  if (recipesArg) {
    const parts = recipesArg.split(",");
    ["warden","fighter","keeper","captain"].forEach((k,i) => { memberRecipes[k] = parts[i]?.trim() || null; });
  } else if (recipeArg) {
    Object.keys(memberRecipes).forEach(k => { memberRecipes[k] = recipeArg; });
  }
  const memberStatBonuses = { warden:{}, fighter:{}, keeper:{}, captain:{} };
  ["warden","fighter","keeper","captain"].forEach(k => {
    if (memberRecipes[k]) memberStatBonuses[k] = FM.statBonusesFor(memberRecipes[k]);
  });

  return {
    runs:      parseInt(get("--runs", "1000")),
    level:     num("--level",    10),
    band:      get("--band",    "greycloaks"),
    duration:  num("--duration", 1800),
    memberStatBonuses,
    memberRecipes,
    recipeArg,
    boss:      num("--boss",    8500),
    drain:     num("--drain",   70),
    spike:     num("--spike",   160),
    spikeiv:   num("--spikeiv", 12),
    phys:      num("--phys",    0) / 100,
    potency:   num("--potency", 0),
    jitter:    num("--jitter",  10) / 100,
    dread:     num("--dread",   0) / 100,
    hope:      num("--hope",    0),
    shadow:    num("--shadow",  0),
    radiance:  num("--radiance",0),
    disease:   num("--disease", 0),
    hale:      num("--hale",    0),
    cold:      num("--cold",    0),
    warmth:    num("--warmth",  0),
    heat:      num("--heat",    0),
    heatease:  num("--heatease",0),
    wake:      num("--wake",    0),
    alert:     num("--alert",   0),
    siphon:    num("--siphon",  0),
    siphonref: num("--siphonref", 0),
    siphoniv:  num("--siphoniv",30),
    statbonus: num("--statbonus", 0),
    survival:  a.includes("--survival"),
    fighterMight: get("--fighter","agility") === "might",
    burstmul:  num("--burstmul", 1.0),
    verbose:   a.includes("--verbose"),
    beta:      num("--beta",    0),
    decouple:  a.includes("--decouple"),
    sink:      a.includes("--sink"),
    rmax:      num("--rmax",    50),
    // Inspiration rates (fixed in design; exposed for research runs)
    graceBase:      num("--grace",  0.05),
    hornBase:       num("--horn",   0.03),
    dawnBase:       num("--dawn",   0.03),
    blackArrowCap:  num("--ba-cap", 0.06),
    // Fate tuning knobs — override the design coefficients for balance testing
    fateEvadeCoef:  num("--fate-evade", 0.004),
    fateInspCoef:   num("--fate-insp",  0.003),
  };
}

// ── single fight ───────────────────────────────────────────────────────────
function runFight(cfg, verbose) {
  const lvl   = cfg.level;
  const TPL   = buildTpl(cfg.band);
  const fTpl  = cfg.fighterMight ? TPL.fighterM : TPL.fighterA;
  const tpls  = { warden:TPL.warden, fighter:fTpl, keeper:TPL.keeper, captain:TPL.captain };

  // build party — apply per-member food stat bonuses on top of base stats
  const M = {};
  ORDER.forEach(k => {
    const tpl = tpls[k];
    const sb  = (cfg.memberStatBonuses && cfg.memberStatBonuses[k]) || {};
    // statbonus simulates grade step: adds cfg.statbonus to any stat that already has a bonus > 0
    const gs  = cfg.statbonus || 0;
    const migBonus = (sb.mig||0) > 0 ? (sb.mig||0) + gs : (sb.mig||0);
    const agiBonus = (sb.agi||0) > 0 ? (sb.agi||0) + gs : (sb.agi||0);
    const vitBonus = (sb.vit||0) > 0 ? (sb.vit||0) + gs : (sb.vit||0);
    const wilBonus = (sb.wil||0) > 0 ? (sb.wil||0) + gs : (sb.wil||0);
    const baseMig = statAt(tpl,"mig",lvl) + migBonus;
    const baseAgi = statAt(tpl,"agi",lvl) + agiBonus;
    const baseVit = statAt(tpl,"vit",lvl) + vitBonus;
    const baseWil = statAt(tpl,"wil",lvl) + wilBonus;
    const baseFat = statAt(tpl,"fat",lvl) + (sb.fat||0);
    const max = Math.round(30 + baseVit * 32);
    M[k] = {
      key:k, tpl, max, hp:max, wounds:0, grievous:false, atZero:false,
      mig:baseMig, agi:baseAgi, vit:baseVit, wil:baseWil, fat:baseFat,
      wilBase:baseWil, fatBase:baseFat, shDrain:0,
      stunned:false, stunTicks:0, broken:false,
      reserve:0,
    };
  });

  // Per-member streak state
  const streak = {};
  ORDER.forEach(k => { streak[k] = { refractory: 0, active: 0 }; });

  // Keeper HoT state — two independent slots
  let hot1 = null, hot2 = null; // { targetKey, ticksLeft, healPerTick }
  let triageCooldown = 0;
  let groupHealTimer = GROUP_HEAL_IV;

  // Captain burst-heal state — fully independent of Keeper's two HoT slots
  let captainHealCooldown = CAPTAIN_HEAL_INTERVAL;

  const active    = () => ORDER.filter(k => !M[k].grievous && !M[k].broken);
  const standing  = () => active().filter(k => M[k].hp > 0);
  const inTrouble = () => active().filter(k => M[k].hp / M[k].max < 0.35).length;
  const inspRoll  = (base, boost) => Math.random() < (base + boost);

  // ── metrics accumulators (Task: sim metrics overhaul — observation only) ──
  const dmgByMember       = { warden:0, fighter:0, keeper:0, captain:0 };
  const healByType        = { hot:0, triage:0, group:0, rescue:0, captainHeal:0 };
  const streakCount       = { warden:0, fighter:0, keeper:0, captain:0 };
  let streakBonusDmg      = 0;   // extra damage attributable to the streak 1.5x multiplier
  let streakBonusHeal     = 0;   // extra healing attributable to the streak 1.5x multiplier
  let hotActiveTicks      = 0;   // ticks with at least one HoT slot active (for uptime %)
  let inspGraceHeal       = 0;   // HP restored by Laurelin's Grace
  let inspHornRedirect    = 0;   // spike damage redirected to the Warden by Horn of Gondor
  let inspDawnHeal        = 0;   // HP restored by Wrath, Ruin, and the Red Dawn
  let inspDawnBonusDps    = 0;   // bonus effective damage from Red Dawn's DPS window
  let inspBlackArrowAmount= 0;   // resolve burned (kill fights) or seconds cut (survival fights)

  // Fraction of a member's raw damage that is physical (armor-mitigated) vs
  // magical (bypasses armor) -- must match EncounterEngine.kt's physicalFraction
  // exactly. Warden/Fighter are pure physical, Keeper is pure magical, Captain
  // splits proportionally between the might-driven and will-driven terms of
  // their own rawDps formula (currently equal coefficients, so this reduces to
  // mig/(mig+wil), but computed from the same coefficients as dpsBreakdown's
  // own captain base so the two never drift independently).
  function physicalFraction(k, m) {
    if (k === "warden" || k === "fighter") return 1;
    if (k === "keeper") return 0;
    // captain
    const physTerm = m.mig * 2.0, magicTerm = m.wil * 2.0;
    const total = physTerm + magicTerm;
    return total > 0 ? physTerm / total : 1;
  }

  function dpsBreakdown() {
    if (cfg.survival) return { raw:0, eff:0, physAfter:0, layerADrag:0, potency:0, rawBy:{warden:0,fighter:0,keeper:0,captain:0}, streakBonus:0, dawnBonus:0 };
    let raw = 0;
    let eff = 0;
    let streakBonus = 0;
    const rawBy = { warden:0, fighter:0, keeper:0, captain:0 };
    const physAfter = Math.max(0, cfg.phys * (1 - Math.min(1, cfg.potency/PEN_SCALE)));
    ORDER.forEach(k => {
      if (M[k].grievous || M[k].hp <= 0 || M[k].stunned || M[k].broken) return;
      const isStreaking = streak[k] && streak[k].active > 0;
      const mult = isStreaking ? STREAK_MULT : 1;
      let base = 0;
      if (k==="keeper")        { if (keeperDealtDps) base = M[k].wil * 2.7; }
      else if (k==="fighter")  base = M[k].agi * 2.33 + M[k].mig * 2.33;
      else if (k==="warden")   base = M[k].mig * 1.5;
      else if (k==="captain")  base = M[k].mig * 2.0 + M[k].wil * 2.0;
      const contribution = base * mult;
      rawBy[k] += contribution;
      raw += contribution;
      // Armor only mitigates the physical share of this member's damage --
      // the magical share bypasses it entirely (see damage-types design).
      const physFrac = physicalFraction(k, M[k]);
      eff += contribution * physFrac * (1 - physAfter) + contribution * (1 - physFrac);
      if (isStreaking) streakBonus += base * (STREAK_MULT - 1);
    });
    let dawnBonus = 0;
    if (windows.dawn > 0) {
      dawnBonus = raw * 0.5; // the extra half of the 1.5x Red Dawn multiplier
      raw *= 1.5;
      eff *= 1.5;
      streakBonus *= 1.5; // Dawn's multiplier stacks on top of whatever streak already added
      ORDER.forEach(k => { rawBy[k] *= 1.5; });
    }
    const capWil = (!M.captain.grievous && !M.captain.stunned) ? M.captain.wil : 0;
    const willCut = Math.min(1, capWil/100), hopeCut = Math.min(1, cfg.hope/100);
    const effectiveDread = cfg.dread * (1 - Math.min(1, willCut + hopeCut));
    const layerADrag = Math.min(1, effectiveDread * DREAD_VAR_COEF + cfg.dread * DREAD_FLOOR_COEF);
    eff = Math.max(0, eff * (1-layerADrag));
    return { raw, eff, physAfter, layerADrag, potency: cfg.potency, rawBy, streakBonus, dawnBonus };
  }

  let t=0, boss=cfg.boss, maxT=cfg.duration;
  let keeperDealtDps = true; // reset each tick in keeper action block
  let rescuesUsed=0, wardsUsed=0, inspBoost=0, baFired=false;
  // first spike fires after one full interval (with jitter)
  let nextSpikeAt = Math.round(cfg.spikeiv * (0.5 + Math.random()));
  let nextSiphonAt = cfg.siphon > 0 ? Math.round(cfg.siphoniv * (0.5 + Math.random())) : Infinity;
  const fired   = {};
  const windows = { dawn:0, horn:0 };
  const events  = [];
  const logs    = [];
  const lg = (msg) => { if (verbose) logs.push(`${fmtT(t).padStart(5)} ${msg}`); };
  let prevTrouble = 0; // track last tick's trouble count to detect rising edge

  while (true) {
    t++;
    // ── Keeper HoT ticks ──────────────────────────────────────────────────────
    if (!M.keeper.grievous && M.keeper.hp > 0 && !M.keeper.stunned) {
      const keepStreak = streak["keeper"];
      const healMult = (keepStreak && keepStreak.active > 0) ? STREAK_MULT : 1;
      if (hot1 || hot2) hotActiveTicks++;
      for (const hot of [hot1, hot2].filter(Boolean)) {
        const tgt = M[hot.targetKey];
        if (tgt && !tgt.grievous) {
          const healAmt = hot.healPerTick * healMult;
          tgt.hp = Math.min(tgt.max, tgt.hp + healAmt);
          healByType.hot += healAmt;
          if (healMult > 1) streakBonusHeal += hot.healPerTick * (STREAK_MULT - 1);
        }
        hot.ticksLeft--;
      }
      if (hot1 && hot1.ticksLeft <= 0) hot1 = null;
      if (hot2 && hot2.ticksLeft <= 0) hot2 = null;
    }

    // ── Captain burst heal (instant, fixed-cooldown proc that targets the current
    // lowest-HP standing member; free — does not cost Captain's DPS action this tick) ──
    if (!M.captain.grievous && M.captain.hp > 0 && !M.captain.stunned) {
      captainHealCooldown--;
      if (captainHealCooldown <= 0) {
        const healTarget = standing().sort((a,b) => (M[a].hp/M[a].max) - (M[b].hp/M[b].max))[0];
        if (healTarget) {
          const healAmt = M.captain.wil * CAPTAIN_HEAL_MUL;
          M[healTarget].hp = Math.min(M[healTarget].max, M[healTarget].hp + healAmt);
          healByType.captainHeal += healAmt;
        }
        captainHealCooldown = CAPTAIN_HEAL_INTERVAL;
      }
    }
    if (windows.dawn > 0) windows.dawn--;
    if (windows.horn > 0) { windows.horn--; }
    // stun countdown
    if (cfg.dread > 0) {
      ORDER.forEach(k => {
        if (M[k].stunned) {
          M[k].stunTicks--;
          if (M[k].stunTicks <= 0) { M[k].stunned = false; M[k].stunTicks = 0; lg(`${M[k].tpl.name} shakes free`); }
        }
      });
    }

    const act = active();
    const live = standing();

    // ── Streak updates ────────────────────────────────────────────────────────
    act.forEach(k => {
      const s = streak[k];
      if (M[k].grievous || M[k].broken || M[k].stunned) return;
      if (s.active > 0) {
        s.active--;
      } else if (s.refractory > 0) {
        s.refractory--;
      } else if (Math.random() < M[k].fat * STREAK_K) {
        s.active = STREAK_DURATION;
        s.refractory = STREAK_REFRACTORY;
        streakCount[k]++;
        lg(`★ STREAK — ${M[k].tpl.name}`);
      }
    });

    // ── Keeper action ─────────────────────────────────────────────────────────
    keeperDealtDps = true; // default: keeper contributes DPS this tick
    if (!M.keeper.grievous && M.keeper.hp > 0 && !M.keeper.stunned) {
      const healPerTick = M.keeper.wil * HOT_HEAL_MUL;
      const keepStreakAct = streak["keeper"];
      const healMult = (keepStreakAct && keepStreakAct.active > 0) ? STREAK_MULT : 1;
      if (triageCooldown > 0) triageCooldown--;
      groupHealTimer--;

      let actionTaken = false;

      if (groupHealTimer <= 0) {
        // Group heal — hits all standing members
        const groupHeal = M.keeper.wil * GROUP_HEAL_MUL * healMult;
        live.forEach(k => { M[k].hp = Math.min(M[k].max, M[k].hp + groupHeal); });
        healByType.group += groupHeal * live.length;
        if (healMult > 1) streakBonusHeal += (M.keeper.wil * GROUP_HEAL_MUL) * (STREAK_MULT - 1) * live.length;
        groupHealTimer = GROUP_HEAL_IV;
        actionTaken = true;
        lg(`Cael group heal +${Math.round(groupHeal)} to all`);
      } else {
        // Triage — lowest-health non-keeper below threshold
        const triageTarget = live
          .filter(k => k !== "keeper")
          .find(k => M[k].hp / M[k].max < TRIAGE_HP);
        if (triageTarget && triageCooldown === 0) {
          const triageHeal = M.keeper.wil * TRIAGE_MUL * healMult;
          M[triageTarget].hp = Math.min(M[triageTarget].max, M[triageTarget].hp + triageHeal);
          healByType.triage += triageHeal;
          if (healMult > 1) streakBonusHeal += (M.keeper.wil * TRIAGE_MUL) * (STREAK_MULT - 1);
          triageCooldown = TRIAGE_COOLDOWN;
          actionTaken = true;
          lg(`Cael triage → ${M[triageTarget].tpl.name} +${Math.round(triageHeal)}`);
        } else {
          // Apply HoT to lowest-health member if a slot is free
          if (!hot1 || !hot2) {
            const covered = new Set([hot1?.targetKey, hot2?.targetKey].filter(Boolean));
            const hotTarget = live
              .filter(k => !covered.has(k))
              .sort((a,b) => (M[a].hp/M[a].max) - (M[b].hp/M[b].max))[0];
            if (hotTarget) {
              const newHot = { targetKey: hotTarget, ticksLeft: HOT_DURATION, healPerTick };
              if (!hot1) hot1 = newHot; else hot2 = newHot;
              lg(`Cael applies HoT → ${M[hotTarget].tpl.name}`);
            }
          }
          // HoT application is free — keeper still DPSes
        }
      }

      keeperDealtDps = !actionTaken; // keeper DPSes only when no consuming action taken
    }

    // Rescue burst (existing mechanic — fires even when keeper healed this tick)
    if (!M.keeper.grievous && M.keeper.hp > 0 && !M.keeper.stunned && rescuesUsed < RESCUE_CAP) {
      let rescue = null;
      for (const k of ORDER) {
        if (k === "keeper" || M[k].grievous) continue;
        if (M[k].hp <= 0) { rescue = k; break; }
      }
      if (rescue) {
        const burst = (40 + M.keeper.wil * 4) * cfg.burstmul;
        M[rescue].hp = Math.min(M[rescue].max, Math.max(M[rescue].hp, 0) + burst);
        healByType.rescue += burst;
        rescuesUsed++;
        lg(`Cael bursts ${M[rescue].tpl.name} (+${Math.round(burst)}) rescue ${rescuesUsed}/${RESCUE_CAP}`);
      }
    }

    // steady drain (standing only)
    // beta=1 → full cascade (current); beta=0 → neutral; beta<0 → last-stand (survivors face less drain)
    if (live.length) {
      const drainFactor = Math.pow(4 / live.length, cfg.beta);
      live.forEach(k => { M[k].hp -= (cfg.drain / 4) * drainFactor; });
    }

    // spike application — shared by both global and decouple modes
    function applySpike(target, dmg, hornRedirected) {
      // Fate evasion
      if (Math.random() < M[target].fat * cfg.fateEvadeCoef) {
        lg(`${M[target].tpl.name} slips the blow — Fate ${Math.round(M[target].fat)} (near miss)`);
        return;
      }
      // sink mode: reserve pool absorbs damage before morale
      if (cfg.sink && M[target].reserve > 0) {
        const absorbed = Math.min(M[target].reserve, dmg);
        M[target].reserve -= absorbed;
        dmg -= absorbed;
      }
      if (dmg > 0) {
        M[target].hp -= dmg;
        if (hornRedirected) inspHornRedirect += dmg;
        lg(`spike → ${M[target].tpl.name} (${Math.round(dmg)})`);
      }
    }

    // spike — variable interval and damage range
    if (cfg.spike > 0) {
      if (cfg.decouple) {
        // Each living member independently rolls their own spike each tick (same total rate as global)
        const pPerTick = 1 / (cfg.spikeiv * 4);
        for (const k of live) {
          if (Math.random() < pPerTick) {
            const spikeRoll = cfg.spike * (0.7 + Math.random() * 0.6);
            const hornActive = windows.horn > 0 && !M.warden.grievous && M.warden.hp > 0;
            let tgt = hornActive ? "warden" : k;
            if (tgt === "keeper" && (M.keeper.hp - spikeRoll) <= 0 && M.warden.hp > 0 && !M.warden.grievous && wardsUsed < WARD_CAP) {
              tgt = "warden"; wardsUsed++;
              lg(`${M.warden.tpl.name} guards Cael from killing blow (guard ${wardsUsed}/${WARD_CAP})`);
            }
            applySpike(tgt, spikeRoll, hornActive && tgt === "warden" && k !== "warden");
          }
        }
      } else {
        // Global spike: one per interval, hits a random standing member
        const isSpike = t >= nextSpikeAt;
        if (isSpike) {
          nextSpikeAt = t + Math.round(cfg.spikeiv * (0.5 + Math.random()));
          const spikeRoll = cfg.spike * (0.7 + Math.random() * 0.6);
          let target = null;
          let hornRedirected = false;
          if (windows.horn > 0 && !M.warden.grievous && M.warden.hp > 0) {
            target = "warden";
            hornRedirected = true;
          } else {
            target = live.length ? live[Math.floor(Math.random() * live.length)] : null;
            if (target === "keeper" && (M.keeper.hp - spikeRoll) <= 0 && M.warden.hp > 0 && !M.warden.grievous && wardsUsed < WARD_CAP) {
              target = "warden"; wardsUsed++;
              lg(`${M.warden.tpl.name} guards Cael from killing blow (guard ${wardsUsed}/${WARD_CAP})`);
            }
          }
          if (target) applySpike(target, spikeRoll, hornRedirected && target === "warden");
        }
      }
    }

    // Shadow drain
    if (cfg.shadow > 0) {
      const deepFloor = Math.max(0.25, SHADOW_FLOOR - (cfg.shadow/100)*0.30);
      const floor = deepFloor + (1-deepFloor)*Math.min(1, cfg.radiance/100);
      const dr = cfg.shadow * SHADOW_RATE;
      ORDER.forEach(k => {
        if (M[k].grievous) return;
        M[k].wil = Math.max(M[k].wilBase*floor, M[k].wil-dr);
        M[k].fat = Math.max(M[k].fatBase*floor, M[k].fat-dr);
        M[k].shDrain = (M[k].wilBase-M[k].wil)+(M[k].fatBase-M[k].fat);
      });
    }

    // simple hazard drain
    { const haz=[["disease","hale"],["cold","warmth"],["heat","heatease"],["wake","alert"]];
      let extra=0; haz.forEach(([hz,an])=>{ const str=cfg[hz]||0; if(str>0) extra+=(str*0.08)*(1-Math.min(1,(cfg[an]||0)/100)); });
      if(extra>0) act.forEach(k=>{ M[k].hp -= extra/act.length; }); }

    // siphon — blood-speaker drains a random member and independently refills boss resolve
    if (cfg.siphon > 0 && t >= nextSiphonAt) {
      const lv = live; // standing() alias
      if (lv.length > 0) {
        if (cfg.siphon > 0) {
          const tgt = lv[Math.floor(Math.random() * lv.length)];
          const siphonRoll = cfg.siphon * (0.8 + Math.random() * 0.4);
          M[tgt].hp -= siphonRoll;
          lg(`siphon dmg → ${M[tgt].tpl.name} (${Math.round(siphonRoll)})`);
        }
        const refill = (cfg.siphonref > 0 ? cfg.siphonref : cfg.siphon) * (0.8 + Math.random() * 0.4);
        boss = Math.min(cfg.boss, boss + refill);
        lg(`siphon refill → boss +${Math.round(refill)} = ${Math.round(boss)}`);
      }
      nextSiphonAt = t + Math.round(cfg.siphoniv * (0.5 + Math.random()));
    }

    // Layer B: dread action denial — checked every BREAK_CHECK_INTERVAL ticks
    if (cfg.dread > 0 && t % BREAK_CHECK_INTERVAL === 0) {
      const capWil = (!M.captain.grievous && !M.captain.stunned) ? M.captain.wil : 0;
      const wCut = Math.min(1, capWil/100), hCut = Math.min(1, cfg.hope/100);
      const neg = wCut + hCut;
      const dreadPool = captW => {
        const pool = [];
        ORDER.forEach(k => { if (!M[k].grievous && !M[k].broken && !M[k].stunned && M[k].hp > 0) pool.push({k, w: k==="captain" ? captW : 1}); });
        return pool;
      };
      const dreadPick = pool => {
        const tot = pool.reduce((s,p) => s+p.w, 0);
        if (!tot) return null;
        let r = Math.random() * tot;
        for (const p of pool) { r -= p.w; if (r <= 0) return p.k; }
        return pool[pool.length-1]?.k || null;
      };
      if (neg < T_TEMP) {
        const tgt = dreadPick(dreadPool(CAPTAIN_STUN_WEIGHT));
        if (tgt) { M[tgt].stunned = true; M[tgt].stunTicks = STUN_TICKS; lg(`${M[tgt].tpl.name} seized by dread — ${STUN_TICKS}s lost`); events.push({t, kind:"dread-stun", who:M[tgt].tpl.name}); }
      }
      if (neg < T_PERM) {
        const tgt = dreadPick(dreadPool(CAPTAIN_BREAK_WEIGHT));
        if (tgt) { M[tgt].broken = true; lg(`${M[tgt].tpl.name} breaks — gone`); events.push({t, kind:"morale-break", who:M[tgt].tpl.name}); }
      }
    }

    // wound / grievous checks
    ORDER.forEach(k => {
      if (M[k].grievous) return;
      if (M[k].hp <= 0 && !M[k].atZero) {
        M[k].atZero = true; M[k].wounds++;
        lg(`${M[k].tpl.name} hits zero — wound ${M[k].wounds}/${GRIEVOUS}`);
        if (M[k].wounds >= GRIEVOUS) {
          if (!M.keeper.grievous && !M.keeper.stunned && M.keeper.hp>0 && inspRoll(Math.min(0.25, cfg.graceBase + M.keeper.fat*cfg.fateInspCoef), inspBoost)) {
            const restored = M[k].max*0.4;
            M[k].wounds=0; M[k].hp=Math.round(restored); M[k].atZero=false;
            inspGraceHeal += restored;
            events.push({t, kind:"Laurelin's Grace", who:M[k].tpl.name});
            lg(`★ LAURELIN'S GRACE — ${M[k].tpl.name} pulled back`);
          } else {
            M[k].grievous=true;
            events.push({t, kind:"grievous", who:M[k].tpl.name});
            lg(`${M[k].tpl.name} GRIEVOUS — out`);
          }
        }
      }
      if (M[k].hp > 5) M[k].atZero = false;
    });

    // Inspiration: Horn of Gondor (Warden) — rolls once on rising edge into crisis, not every tick
    if (inspBoost > 0) inspBoost = Math.max(0, inspBoost - 0.005);
    const nowTrouble = inTrouble();
    const crisisEdge = nowTrouble >= 2 && prevTrouble < 2; // just crossed the threshold
    if (!fired.horn && crisisEdge && !M.warden.grievous && !M.warden.stunned && !M.warden.broken && M.warden.hp>0 && inspRoll(Math.min(0.25, cfg.hornBase + M.warden.fat*cfg.fateInspCoef), inspBoost)) {
      fired.horn=true; windows.horn=8;
      events.push({t, kind:"The Horn of Gondor", who:M.warden.tpl.name});
      lg(`★ HORN OF GONDOR — ${M.warden.tpl.name}`);
    }

    // Inspiration: Wrath, Ruin, and the Red Dawn (Captain) — same rising-edge gate
    if (!fired.dawn && crisisEdge && !M.captain.grievous && !M.captain.stunned && !M.captain.broken && M.captain.hp>0 && inspRoll(Math.min(0.25, cfg.dawnBase + M.captain.fat*cfg.fateInspCoef), 0)) {
      fired.dawn=true; windows.dawn=10;
      act.forEach(k => { const before = M[k].hp; M[k].hp = Math.min(M[k].max, M[k].hp + M[k].max*0.15); inspDawnHeal += (M[k].hp - before); });
      inspBoost = 0.15;
      events.push({t, kind:"Wrath, Ruin, and the Red Dawn", who:M.captain.tpl.name});
      lg(`★ RED DAWN — ${M.captain.tpl.name} (inspBoost +0.15 for ~30s)`);
    }
    prevTrouble = nowTrouble;

    // DPS tick
    const bd = dpsBreakdown();
    if (!cfg.survival) {
      const jitterFactor = 1 + (Math.random()*2-1)*cfg.jitter;
      const appliedDmg = bd.eff * jitterFactor;
      boss = Math.max(0, boss - appliedDmg);
      const mitFactor = (1-bd.physAfter) * (1-bd.layerADrag);
      if (bd.raw > 0) {
        ORDER.forEach(k => { dmgByMember[k] += (bd.rawBy[k] / bd.raw) * appliedDmg; });
      }
      streakBonusDmg   += bd.streakBonus * mitFactor * jitterFactor;
      inspDawnBonusDps += bd.dawnBonus   * mitFactor * jitterFactor;
    }

    // Inspiration: Black Arrow (Fighter)
    if (!baFired && !M.fighter.grievous && !M.fighter.stunned && !M.fighter.broken && M.fighter.hp>0) {
      const elapsed = t/maxT;
      const ttk = bd.eff>0 ? boss/bd.eff : 1e9;
      const trem = maxT-t;
      const losing = cfg.survival ? elapsed>0.5 : (ttk>trem && elapsed>0.4);
      if (losing) {
        const rise = Math.min(cfg.blackArrowCap, (elapsed-0.4)*cfg.blackArrowCap*2.5);
        if (inspRoll(rise + M.fighter.fat*cfg.fateInspCoef, inspBoost)) {
          baFired=true;
          if (cfg.survival) {
            const cut = Math.round(cfg.duration*0.15);
            maxT -= cut; inspBlackArrowAmount = cut;
            events.push({t, kind:"Black Arrow (flaming)", who:M.fighter.tpl.name}); lg(`★ BLACK ARROW — clock cut`);
          } else {
            const burn = cfg.boss*0.18;
            boss = Math.max(0, boss-burn); inspBlackArrowAmount = burn;
            events.push({t, kind:"Black Arrow", who:M.fighter.tpl.name}); lg(`★ BLACK ARROW — 18% resolve chunk`);
          }
        }
      }
    }

    // termination
    const metrics = { dmgByMember, healByType, streakCount, streakBonusDmg, streakBonusHeal, hotActiveTicks,
                       inspGraceHeal, inspHornRedirect, inspDawnHeal, inspDawnBonusDps, inspBlackArrowAmount };
    if (!cfg.survival && boss <= 0) return _result("VICTORY", t, M, events, rescuesUsed, wardsUsed, logs, 0, cfg.boss, metrics);
    if (!ORDER.some(k => !M[k].grievous && !M[k].broken && M[k].hp>0)) return _result("DEFEAT", t, M, events, rescuesUsed, wardsUsed, logs, boss, cfg.boss, metrics);
    if (t >= maxT) return _result(cfg.survival?"REPELLED":"STALEMATE", t, M, events, rescuesUsed, wardsUsed, logs, boss, cfg.boss, metrics);
  }
}

function _result(outcome, t, M, events, rescuesUsed, wardsUsed, logs, bossRemaining, bossMax, metrics) {
  const grievous = ORDER.filter(k=>M[k].grievous);
  const totalWounds = ORDER.reduce((s,k)=>s+M[k].wounds+(M[k].grievous?GRIEVOUS:0),0);
  const insps = events.filter(e=>/Grace|Horn|Red Dawn|Black Arrow/.test(e.kind));
  const closeness = bossMax > 0 ? bossRemaining / bossMax : 0; // 0 = kill, 1 = never scratched
  const stunCount  = events.filter(e => e.kind === "dread-stun").length;
  const breakCount = events.filter(e => e.kind === "morale-break").length;
  return { outcome, t, grievous, totalWounds, rescuesUsed, wardsUsed, events, insps, logs,
    bossRemaining, closeness, stunCount, breakCount, metrics,
    wounds: Object.fromEntries(ORDER.map(k=>[k, M[k].wounds+(M[k].grievous?GRIEVOUS:0)])) };
}

// ── aggregate N fights ─────────────────────────────────────────────────────
function runMany(cfg, n, verbose) {
  const counts   = { VICTORY:0, DEFEAT:0, STALEMATE:0, REPELLED:0 };
  const inspFired = { "Laurelin's Grace":0, "The Horn of Gondor":0, "Wrath, Ruin, and the Red Dawn":0, "Black Arrow":0, "Black Arrow (flaming)":0 };
  let totalWounds=0, totalRescues=0, totalWardsUsed=0, totalTime=0, totalStuns=0, totalBreaks=0;
  const woundsByRole = Object.fromEntries(ORDER.map(k=>[k,0]));
  // close-call tracking: defeats where enemy was < 25% resolve remaining
  let closeDefeats=0, totalBossRemaining=0, defeatCount=0;
  let sample = null;

  // ── metrics aggregates ────────────────────────────────────────────────────
  const totalDmgByMember   = Object.fromEntries(ORDER.map(k=>[k,0]));
  const totalHealByType    = { hot:0, triage:0, group:0, rescue:0, captainHeal:0 };
  const totalStreakCount   = Object.fromEntries(ORDER.map(k=>[k,0]));
  let totalStreakBonusDmg=0, totalStreakBonusHeal=0, totalHotActiveTicks=0;
  let totalInspGraceHeal=0, totalInspHornRedirect=0, totalInspDawnHeal=0, totalInspDawnBonusDps=0;
  let totalInspBlackArrowAmount=0;

  for (let i=0; i<n; i++) {
    const r = runFight(cfg, verbose && i===0);
    counts[r.outcome]++;
    totalWounds  += r.totalWounds;
    totalRescues += r.rescuesUsed;
    totalWardsUsed += r.wardsUsed;
    totalStuns   += r.stunCount || 0;
    totalBreaks  += r.breakCount || 0;
    totalTime    += r.t;
    ORDER.forEach(k => woundsByRole[k] += r.wounds[k]);
    r.insps.forEach(e => { if (inspFired[e.kind]!==undefined) inspFired[e.kind]++; });
    if (r.outcome === "DEFEAT" || r.outcome === "STALEMATE") {
      defeatCount++;
      totalBossRemaining += r.bossRemaining;
      if (r.closeness <= 0.25) closeDefeats++;
    }
    if (i===0) sample = r;

    const m = r.metrics;
    ORDER.forEach(k => { totalDmgByMember[k] += m.dmgByMember[k]; totalStreakCount[k] += m.streakCount[k]; });
    ["hot","triage","group","rescue","captainHeal"].forEach(t => { totalHealByType[t] += m.healByType[t]; });
    totalStreakBonusDmg   += m.streakBonusDmg;
    totalStreakBonusHeal  += m.streakBonusHeal;
    totalHotActiveTicks   += m.hotActiveTicks;
    totalInspGraceHeal    += m.inspGraceHeal;
    totalInspHornRedirect += m.inspHornRedirect;
    totalInspDawnHeal     += m.inspDawnHeal;
    totalInspDawnBonusDps += m.inspDawnBonusDps;
    totalInspBlackArrowAmount += m.inspBlackArrowAmount;
  }

  return { counts, inspFired, totalWounds, totalRescues, totalWardsUsed, totalTime,
           woundsByRole, n, sample, closeDefeats, totalBossRemaining, defeatCount,
           totalStuns, totalBreaks,
           totalDmgByMember, totalHealByType, totalStreakCount,
           totalStreakBonusDmg, totalStreakBonusHeal, totalHotActiveTicks,
           totalInspGraceHeal, totalInspHornRedirect, totalInspDawnHeal,
           totalInspDawnBonusDps, totalInspBlackArrowAmount };
}

// ── reporting ──────────────────────────────────────────────────────────────
function report(cfg, res) {
  const n = res.n;
  const pct = v => `${(v/n*100).toFixed(1)}%`;
  const avg = v => (v/n).toFixed(2);

  console.log("\n═══ HearthCraft Headless Sim ═══");
  console.log(`Runs: ${n}  |  Level ${cfg.level}  |  Duration ${fmtT(cfg.duration)}  |  ${cfg.survival?"SURVIVAL":"ATTRITION"}`);
  const modes = [`cascade β=${cfg.beta}`, cfg.decouple?"spikes:decoupled":"spikes:global", cfg.sink?`heal:sink(rmax=${cfg.rmax})`:"heal:softcap"].join("  ");
  console.log(`Modes: ${modes}`);
  console.log(`Boss ${cfg.boss} resolve  |  Drain ${cfg.drain}/s  |  Spike ${cfg.spike} every ${cfg.spikeiv}s`);
  if (cfg.phys>0) console.log(`Armor ${Math.round(cfg.phys*100)}% | Draught potency ${cfg.potency} / ${PEN_SCALE}`);
  if (cfg.siphon>0||cfg.siphonref>0) {
    const dmgStr = cfg.siphon>0 ? `${cfg.siphon} dmg` : "no dmg";
    const refStr = cfg.siphonref>0 ? `${cfg.siphonref} refill` : `${cfg.siphon} refill`;
    console.log(`Siphon ${dmgStr} / ${refStr} every ${cfg.siphoniv}s`);
  }
  if (cfg.dread>0) console.log(`Dread ${cfg.dread} | Hope ${cfg.hope} | Avg stuns/fight: ${(res.totalStuns/n).toFixed(2)} | Avg breaks/fight: ${(res.totalBreaks/n).toFixed(2)}`);
  // food line (stat bonuses only — no HP/s)
  const anyMemberRecipe = cfg.memberRecipes && Object.values(cfg.memberRecipes).find(r => r !== null);
  if (anyMemberRecipe) {
    const perMember = ["warden","fighter","keeper","captain"].map(k => {
      const rid = cfg.memberRecipes[k];
      if (!rid) return `${k[0].toUpperCase()}: none`;
      const r = FM.RECIPES.find(r => r.id === rid);
      const sb = cfg.memberStatBonuses[k] || {};
      const bStr = Object.entries(sb).map(([s,n])=>`+${n}${s}`).join(" ") || "no bonus";
      return `${k[0].toUpperCase()}: ${r ? r.name : rid} [${bStr}]`;
    }).join("  |  ");
    console.log(`Food (stat bonuses): ${perMember}`);
  } else {
    console.log(`Food: none`);
  }
  const haz=[];
  if(cfg.dread>0) haz.push(`Dread ${Math.round(cfg.dread*100)}%`+(cfg.hope>0?` Hope ${cfg.hope}`:""));
  if(cfg.shadow>0) haz.push(`Shadow ${cfg.shadow}`+(cfg.radiance>0?` Radiance ${cfg.radiance}`:""));
  if(cfg.cold>0)    haz.push(`Cold ${cfg.cold}`+(cfg.warmth>0?` Warmth ${cfg.warmth}`:""));
  if(cfg.heat>0)    haz.push(`Heat ${cfg.heat}`+(cfg.heatease>0?` Heatease ${cfg.heatease}`:""));
  if(cfg.disease>0) haz.push(`Disease ${cfg.disease}`+(cfg.hale>0?` Hale ${cfg.hale}`:""));
  if(cfg.wake>0)    haz.push(`Wake ${cfg.wake}`+(cfg.alert>0?` Alert ${cfg.alert}`:""));
  if(haz.length) console.log(`Hazards: ${haz.join("  ")}`);

  console.log("\n── Outcomes ──");
  Object.entries(res.counts).forEach(([k,v])=>{ if(v>0) console.log(`  ${k.padEnd(10)} ${pct(v).padStart(6)}  (${v})`); });

  // close-call block — only shown when there are defeats/stalemates
  if (res.defeatCount > 0) {
    const avgBossLeft = (res.totalBossRemaining / res.defeatCount / cfg.boss * 100).toFixed(1);
    const closeDefeats = res.closeDefeats;
    const closePct = (closeDefeats / res.defeatCount * 100).toFixed(1);
    console.log("\n── On defeat ──");
    console.log(`  Enemy resolve remaining  avg ${avgBossLeft}% of max`);
    console.log(`  Close calls (< 25% left)     ${closePct}% of defeats  (${closeDefeats}/${res.defeatCount})`);
    if (parseFloat(avgBossLeft) < 20) {
      console.log(`  ★ Near-miss zone — most defeats came with enemy almost dead`);
    }
  }

  console.log("\n── Averages (per fight) ──");
  console.log(`  Duration    ${fmtT(Math.round(res.totalTime/n))}`);
  console.log(`  Wounds      ${avg(res.totalWounds)}  (by role: ${ORDER.map(k=>`${k[0].toUpperCase()}${avg(res.woundsByRole[k])}`).join(" ")})`);
  console.log(`  Rescues     ${avg(res.totalRescues)} / ${RESCUE_CAP}`);
  console.log(`  Warden guards ${avg(res.totalWardsUsed)} / ${WARD_CAP}`);

  console.log("\n── Inspiration fire rates ──");
  const inspNames = { "Laurelin's Grace":"Grace (Keeper)", "The Horn of Gondor":"Horn (Warden)", "Wrath, Ruin, and the Red Dawn":"Red Dawn (Captain)", "Black Arrow":"Black Arrow (Fighter)", "Black Arrow (flaming)":"Black Arrow flaming (Fighter)" };
  Object.entries(res.inspFired).forEach(([k,v])=>{
    if(k==="Black Arrow (flaming)"&&!cfg.survival) return;
    if(k==="Black Arrow"&&cfg.survival) return;
    console.log(`  ${(inspNames[k]||k).padEnd(26)} ${pct(v).padStart(6)}  fired in ${v} fights`);
  });

  console.log("\n── DPS by member (avg per fight, effective damage to boss) ──");
  const totalDmg = ORDER.reduce((s,k)=>s+res.totalDmgByMember[k],0);
  ORDER.forEach(k => {
    const share = totalDmg > 0 ? (res.totalDmgByMember[k]/totalDmg*100).toFixed(1) : "0.0";
    console.log(`  ${k.padEnd(10)} ${avg(res.totalDmgByMember[k]).padStart(9)} dmg   (${share}% of party)`);
  });
  console.log(`  ${"TOTAL".padEnd(10)} ${avg(totalDmg).padStart(9)} dmg`);

  console.log("\n── Healing by source (avg per fight, outgoing/pre-overheal) ──");
  const totalHeal = Object.values(res.totalHealByType).reduce((s,v)=>s+v,0);
  const healLabels = { hot:"HoT", triage:"Triage", group:"Group Heal", rescue:"Rescue Burst", captainHeal:"Captain Heal" };
  Object.entries(res.totalHealByType).forEach(([k,v]) => {
    console.log(`  ${healLabels[k].padEnd(14)} ${avg(v).padStart(9)} hp`);
  });
  console.log(`  ${"TOTAL".padEnd(14)} ${avg(totalHeal).padStart(9)} hp`);
  const hotUptimePct = (res.totalHotActiveTicks / res.totalTime * 100).toFixed(1);
  const hotPotency = res.totalHotActiveTicks > 0 ? (res.totalHealByType.hot / res.totalHotActiveTicks).toFixed(2) : "0.00";
  console.log(`  HoT uptime      ${hotUptimePct}% of fight duration  |  potency ${hotPotency} hp/tick avg`);

  console.log("\n── Streak activity ──");
  console.log(`  Fate×${STREAK_K} trigger | ${STREAK_DURATION}-tick duration | ${STREAK_MULT}× multiplier | Refractory: ${STREAK_REFRACTORY} ticks`);
  const totalStreaks = ORDER.reduce((s,k)=>s+res.totalStreakCount[k],0);
  ORDER.forEach(k => console.log(`  ${k.padEnd(10)} ${avg(res.totalStreakCount[k]).padStart(6)} triggers/fight`));
  console.log(`  ${"TOTAL".padEnd(10)} ${avg(totalStreaks).padStart(6)} triggers/fight`);
  console.log(`  Bonus damage from streaks   ${avg(res.totalStreakBonusDmg)} dmg/fight`);
  console.log(`  Bonus healing from streaks  ${avg(res.totalStreakBonusHeal)} hp/fight`);

  console.log("\n── Inspiration contribution (avg per fight, all fights — not just where it fired) ──");
  console.log(`  Grace         HP restored        ${avg(res.totalInspGraceHeal)} hp`);
  console.log(`  Horn          Damage redirected  ${avg(res.totalInspHornRedirect)} dmg`);
  console.log(`  Red Dawn      HP restored        ${avg(res.totalInspDawnHeal)} hp`);
  console.log(`  Red Dawn      Bonus damage       ${avg(res.totalInspDawnBonusDps)} dmg`);
  if (cfg.survival) {
    console.log(`  Black Arrow   Clock cut          ${avg(res.totalInspBlackArrowAmount)} sec`);
  } else {
    console.log(`  Black Arrow   Resolve burned     ${avg(res.totalInspBlackArrowAmount)} dmg`);
  }

  if (res.sample && res.sample.logs.length) {
    console.log("\n── Verbose log (fight #1) ──");
    res.sample.logs.forEach(l=>console.log(" "+l));
  }
  console.log("");
}

// ── main ───────────────────────────────────────────────────────────────────
const cfg = parseArgs();
const res = runMany(cfg, cfg.runs, cfg.verbose);
report(cfg, res);
