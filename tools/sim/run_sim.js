#!/usr/bin/env node
// HearthCraft headless combat simulator
// Same math as hearthcraft_fight_sim.html — no DOM required.
// Usage:
//   node run_sim.js [--runs N] [--level L] [--duration S]
//                   [--recipe ID]        (same recipe stat bonuses for all members)
//                   [--recipes W,F,K,C]  (per-member recipe IDs)
//                   [--statbonus N]      (flat bonus added to all recipe stat outputs; simulates grade step)
//                   [--boss R] [--drain D] [--spike S] [--spikeiv I]
//                   [--phys P] [--dread V] [--shadow V] [--cold V] [--heat V]
//                   [--disease V] [--wake V] [--hope V] [--radiance V]
//                   [--hale V] [--warmth V] [--heatease V] [--alert V]
//                   [--survival] [--fighter might] [--burstmul F] [--verbose]
//                   [--beta B]      (cascade exponent: 1=cascade/current, 0=neutral, <0=last-stand; default 1)
//                   [--decouple]    (independent per-member spikes instead of one global spike)
//                   [--sink]        (replace 40% softcap with a reserve pool that absorbs spikes first)
//                   [--rmax R]      (reserve pool cap for --sink mode; default 50)
//
// Food gives stat bonuses only (Model B — HP/s removed).
//
// Runs N fights and prints aggregate stats + Inspiration fire rates.

const FM = require('./food_model');

// ── constants (mirrored from the browser sim) ──────────────────────────────
const TPL = {
  warden:  { name:"Borin", role:"Warden",  soak:true,  start:{mig:13,agi:6, vit:15,wil:7, fat:6 }, grow:{mig:0.50,agi:0.15,vit:0.55,wil:0.20,fat:0.15} },
  fighterA:{ name:"Mira",  role:"Fighter", soak:false, start:{mig:9, agi:15,vit:7, wil:5, fat:9 }, grow:{mig:0.30,agi:0.55,vit:0.20,wil:0.15,fat:0.35} },
  fighterM:{ name:"Mira",  role:"Fighter", soak:false, start:{mig:15,agi:9, vit:8, wil:5, fat:7 }, grow:{mig:0.55,agi:0.30,vit:0.25,wil:0.15,fat:0.25} },
  keeper:  { name:"Cael",  role:"Keeper",  soak:false, start:{mig:5, agi:7, vit:9, wil:15,fat:12}, grow:{mig:0.15,agi:0.20,vit:0.30,wil:0.55,fat:0.45} },
  captain: { name:"Aelin", role:"Captain", soak:true,  start:{mig:8, agi:7, vit:12,wil:13,fat:13}, grow:{mig:0.20,agi:0.20,vit:0.40,wil:0.45,fat:0.45} },
};
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
const GROUP_HEAL_IV   = 20;   // ticks between group heals
const GROUP_HEAL_MUL  = 0.5;  // groupHeal per member = keeper.wil * GROUP_HEAL_MUL

// ── Fate streak constants (must match EncounterEngine.kt Task 7) ───────────
const STREAK_K          = 0.002; // trigger prob per eligible tick = fat * STREAK_K
const STREAK_REFRACTORY = 20;    // ticks before streak can re-trigger
const STREAK_DURATION   = 5;     // streak active ticks
const STREAK_MULT       = 1.5;   // DPS/heal multiplier during streak

const statAt  = (tpl, k, lvl) => tpl.start[k] + tpl.grow[k] * (lvl - 1);
const moraleOf= (tpl, lvl)    => Math.round(30 + statAt(tpl,"vit",lvl) * 16);
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
    beta:      num("--beta",    1.0),
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
    const max = Math.round(30 + baseVit * 16);
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

  const active    = () => ORDER.filter(k => !M[k].grievous && !M[k].broken);
  const standing  = () => active().filter(k => M[k].hp > 0);
  const inTrouble = () => active().filter(k => M[k].hp / M[k].max < 0.35).length;
  const inspRoll  = (base, boost) => Math.random() < (base + boost);

  function dpsBreakdown() {
    if (cfg.survival) return { raw:0, eff:0, physAfter:0, layerADrag:0, potency:0 };
    let raw = 0;
    ORDER.forEach(k => {
      if (M[k].grievous || M[k].hp <= 0 || M[k].stunned || M[k].broken) return;
      const mult = (streak[k] && streak[k].active > 0) ? STREAK_MULT : 1;
      let base = 0;
      if (k==="keeper")        { if (keeperDealtDps) base = M[k].wil * 0.9; }
      else if (k==="fighter")  base = M[k].agi + M[k].mig * 0.4;
      else if (k==="warden")   base = M[k].mig * 0.5;
      else if (k==="captain")  base = M[k].mig * 0.3 + M[k].wil * 0.2;
      raw += base * mult;
    });
    if (windows.dawn > 0) raw *= 1.5;
    const physAfter = Math.max(0, cfg.phys * (1 - Math.min(1, cfg.potency/PEN_SCALE)));
    const capWil = (!M.captain.grievous && !M.captain.stunned) ? M.captain.wil : 0;
    const willCut = Math.min(1, capWil/100), hopeCut = Math.min(1, cfg.hope/100);
    const effectiveDread = cfg.dread * (1 - Math.min(1, willCut + hopeCut));
    const layerADrag = Math.min(1, effectiveDread * DREAD_VAR_COEF + cfg.dread * DREAD_FLOOR_COEF);
    const eff = Math.max(0, raw * (1-physAfter) * (1-layerADrag));
    return { raw, eff, physAfter, layerADrag, potency: cfg.potency };
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
      for (const hot of [hot1, hot2].filter(Boolean)) {
        const tgt = M[hot.targetKey];
        if (tgt && !tgt.grievous) {
          tgt.hp = Math.min(tgt.max, tgt.hp + hot.healPerTick * healMult);
        }
        hot.ticksLeft--;
      }
      if (hot1 && hot1.ticksLeft <= 0) hot1 = null;
      if (hot2 && hot2.ticksLeft <= 0) hot2 = null;
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
    function applySpike(target, dmg) {
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
            let tgt = (windows.horn > 0 && !M.warden.grievous && M.warden.hp > 0) ? "warden" : k;
            if (tgt === "keeper" && (M.keeper.hp - spikeRoll) <= 0 && M.warden.hp > 0 && !M.warden.grievous && wardsUsed < WARD_CAP) {
              tgt = "warden"; wardsUsed++;
              lg(`${M.warden.tpl.name} guards Cael from killing blow (guard ${wardsUsed}/${WARD_CAP})`);
            }
            applySpike(tgt, spikeRoll);
          }
        }
      } else {
        // Global spike: one per interval, hits a random standing member
        const isSpike = t >= nextSpikeAt;
        if (isSpike) {
          nextSpikeAt = t + Math.round(cfg.spikeiv * (0.5 + Math.random()));
          const spikeRoll = cfg.spike * (0.7 + Math.random() * 0.6);
          let target = null;
          if (windows.horn > 0 && !M.warden.grievous && M.warden.hp > 0) {
            target = "warden";
          } else {
            target = live.length ? live[Math.floor(Math.random() * live.length)] : null;
            if (target === "keeper" && (M.keeper.hp - spikeRoll) <= 0 && M.warden.hp > 0 && !M.warden.grievous && wardsUsed < WARD_CAP) {
              target = "warden"; wardsUsed++;
              lg(`${M.warden.tpl.name} guards Cael from killing blow (guard ${wardsUsed}/${WARD_CAP})`);
            }
          }
          if (target) applySpike(target, spikeRoll);
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
            M[k].wounds=0; M[k].hp=Math.round(M[k].max*0.4); M[k].atZero=false;
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
      act.forEach(k => { M[k].hp = Math.min(M[k].max, M[k].hp + M[k].max*0.15); });
      inspBoost = 0.15;
      events.push({t, kind:"Wrath, Ruin, and the Red Dawn", who:M.captain.tpl.name});
      lg(`★ RED DAWN — ${M.captain.tpl.name} (inspBoost +0.15 for ~30s)`);
    }
    prevTrouble = nowTrouble;

    // DPS tick
    const bd = dpsBreakdown();
    if (!cfg.survival) boss = Math.max(0, boss - bd.eff * (1 + (Math.random()*2-1)*cfg.jitter));

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
          if (cfg.survival) { maxT -= Math.round(cfg.duration*0.15); events.push({t, kind:"Black Arrow (flaming)", who:M.fighter.tpl.name}); lg(`★ BLACK ARROW — clock cut`); }
          else              { boss = Math.max(0, boss-cfg.boss*0.18); events.push({t, kind:"Black Arrow", who:M.fighter.tpl.name}); lg(`★ BLACK ARROW — 18% resolve chunk`); }
        }
      }
    }

    // termination
    if (!cfg.survival && boss <= 0) return _result("VICTORY", t, M, events, rescuesUsed, wardsUsed, logs, 0, cfg.boss);
    if (!ORDER.some(k => !M[k].grievous && !M[k].broken && M[k].hp>0)) return _result("DEFEAT", t, M, events, rescuesUsed, wardsUsed, logs, boss, cfg.boss);
    if (t >= maxT) return _result(cfg.survival?"REPELLED":"STALEMATE", t, M, events, rescuesUsed, wardsUsed, logs, boss, cfg.boss);
  }
}

function _result(outcome, t, M, events, rescuesUsed, wardsUsed, logs, bossRemaining, bossMax) {
  const grievous = ORDER.filter(k=>M[k].grievous);
  const totalWounds = ORDER.reduce((s,k)=>s+M[k].wounds+(M[k].grievous?GRIEVOUS:0),0);
  const insps = events.filter(e=>/Grace|Horn|Red Dawn|Black Arrow/.test(e.kind));
  const closeness = bossMax > 0 ? bossRemaining / bossMax : 0; // 0 = kill, 1 = never scratched
  const stunCount  = events.filter(e => e.kind === "dread-stun").length;
  const breakCount = events.filter(e => e.kind === "morale-break").length;
  return { outcome, t, grievous, totalWounds, rescuesUsed, wardsUsed, events, insps, logs,
    bossRemaining, closeness, stunCount, breakCount,
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
  }

  return { counts, inspFired, totalWounds, totalRescues, totalWardsUsed, totalTime,
           woundsByRole, n, sample, closeDefeats, totalBossRemaining, defeatCount,
           totalStuns, totalBreaks };
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

  console.log("\n── Streak activity ──");
  console.log(`  Fate×${STREAK_K} trigger | ${STREAK_DURATION}-tick duration | ${STREAK_MULT}× multiplier`);
  console.log(`  Refractory: ${STREAK_REFRACTORY} ticks`);

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
