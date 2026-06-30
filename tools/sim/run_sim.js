#!/usr/bin/env node
// HearthCraft headless combat simulator
// Same math as hearthcraft_fight_sim.html — no DOM required.
// Usage:
//   node run_sim.js [--runs N] [--level L] [--duration S] [--food W,H,K,C]
//                   [--rlevel R]       (cooking level 1-50; HP/s from tier table, no stat bonuses)
//                   [--recipe ID]      (same recipe for all members; combined with --rlevel or --fl)
//                   [--fl N]           (food level 1-4+; combined with --recipe)
//                   [--recipes W,H,K,C]  (per-member recipe IDs; combined with --rlevel or --fl)
//                   [--boss R] [--drain D] [--spike S] [--spikeiv I]
//                   [--phys P] [--dread V] [--shadow V] [--cold V] [--heat V]
//                   [--disease V] [--wake V] [--hope V] [--radiance V]
//                   [--hale V] [--warmth V] [--heatease V] [--alert V]
//                   [--survival] [--hunter might] [--burstmul F] [--verbose]
//                   [--beta B]      (cascade exponent: 1=cascade/current, 0=neutral, <0=last-stand; default 1)
//                   [--decouple]    (independent per-member spikes instead of one global spike)
//                   [--sink]        (replace 40% softcap with a reserve pool that absorbs spikes first)
//                   [--rmax R]      (reserve pool cap for --sink mode; default 50)
//
// Food precedence (highest wins):
//   --food         raw HP/s per member, no stat bonuses
//   --recipes+--fl per-member recipe IDs + food level → per-member stat bonuses + HP/s
//   --recipe+--fl  all members same recipe + food level
//   --rlevel       HP/s from tier table only (no stat bonuses)
//
// Runs N fights and prints aggregate stats + Inspiration fire rates.

const FM = require('./food_model');

// ── constants (mirrored from the browser sim) ──────────────────────────────
const TPL = {
  warden:  { name:"Borin", role:"Warden",  soak:true,  start:{mig:13,agi:6, vit:15,wil:7, fat:6 }, grow:{mig:0.50,agi:0.15,vit:0.55,wil:0.20,fat:0.15} },
  hunterA: { name:"Mira",  role:"Hunter",  soak:false, start:{mig:9, agi:15,vit:7, wil:5, fat:9 }, grow:{mig:0.30,agi:0.55,vit:0.20,wil:0.15,fat:0.35} },
  hunterM: { name:"Mira",  role:"Hunter",  soak:false, start:{mig:15,agi:9, vit:8, wil:5, fat:7 }, grow:{mig:0.55,agi:0.30,vit:0.25,wil:0.15,fat:0.25} },
  keeper:  { name:"Cael",  role:"Keeper",  soak:false, start:{mig:5, agi:7, vit:9, wil:15,fat:12}, grow:{mig:0.15,agi:0.20,vit:0.30,wil:0.55,fat:0.45} },
  captain: { name:"Aelin", role:"Captain", soak:true,  start:{mig:8, agi:7, vit:12,wil:13,fat:13}, grow:{mig:0.20,agi:0.20,vit:0.40,wil:0.45,fat:0.45} },
};
const ORDER        = ["warden","hunter","keeper","captain"];
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

const statAt  = (tpl, k, lvl) => tpl.start[k] + tpl.grow[k] * (lvl - 1);
const moraleOf= (tpl, lvl)    => Math.round(30 + statAt(tpl,"vit",lvl) * 16);
const fmtT    = s => `${Math.floor(s/60)}:${String(Math.max(0,s)%60).padStart(2,"0")}`;
const clamp   = (v,lo,hi)     => Math.max(lo, Math.min(hi, v));

// ── CLI parsing ────────────────────────────────────────────────────────────
function parseArgs() {
  const a = process.argv.slice(2);
  const get = (flag, def) => { const i = a.indexOf(flag); return i>=0 ? a[i+1] : def; };
  const num = (flag, def) => parseFloat(get(flag, def));

  // Food resolution: --food > --recipes/--recipe + --fl/--rlevel > --rlevel
  const recipeArg  = get("--recipe",  null);   // single recipe for all members
  const recipesArg = get("--recipes", null);   // comma-separated per-member (W,H,K,C)
  const flArg      = a.includes("--fl") ? parseInt(get("--fl", "1")) : null;
  const rLevel     = a.includes("--rlevel") ? parseFloat(get("--rlevel", "1")) : null;

  // Per-member recipe IDs: --recipes W,H,K,C overrides --recipe for everyone
  const memberRecipes = { warden:null, hunter:null, keeper:null, captain:null };
  if (recipesArg) {
    const parts = recipesArg.split(",");
    ["warden","hunter","keeper","captain"].forEach((k,i) => { memberRecipes[k] = parts[i]?.trim() || null; });
  } else if (recipeArg) {
    Object.keys(memberRecipes).forEach(k => { memberRecipes[k] = recipeArg; });
  }

  // Resolve HP/s and per-member stat bonuses
  let derivedHps = null, effectiveRL = null;
  const memberStatBonuses = { warden:{}, hunter:{}, keeper:{}, captain:{} };

  const anyRecipe = Object.values(memberRecipes).find(r => r !== null);
  if (anyRecipe && flArg !== null) {
    // FL given explicitly — legacy path, still supported
    const r = FM.RECIPES.find(r => r.id === anyRecipe);
    effectiveRL = r ? r.cookLevel + flArg - 1 : (rLevel || 1);
    derivedHps  = FM.recipeLevelToHps(effectiveRL);
    ["warden","hunter","keeper","captain"].forEach(k => {
      if (memberRecipes[k]) memberStatBonuses[k] = FM.statBonusesAt(memberRecipes[k], flArg);
    });
  } else if (anyRecipe && rLevel !== null) {
    effectiveRL = rLevel;
    derivedHps  = FM.recipeLevelToHps(rLevel);
    ["warden","hunter","keeper","captain"].forEach(k => {
      if (memberRecipes[k]) memberStatBonuses[k] = FM.statBonusesFor(memberRecipes[k]);
    });
  } else if (anyRecipe) {
    // Recipe with no level specified — use authored stat bonuses, no HP/s override
    effectiveRL = rLevel;
    derivedHps  = rLevel ? FM.recipeLevelToHps(rLevel) : null;
    ["warden","hunter","keeper","captain"].forEach(k => {
      if (memberRecipes[k]) memberStatBonuses[k] = FM.statBonusesFor(memberRecipes[k]);
    });
  } else if (rLevel !== null) {
    effectiveRL = rLevel;
    derivedHps  = FM.recipeLevelToHps(rLevel);
  }

  const foodStr = get("--food", null);
  let food;
  if (foodStr) {
    food = foodStr.split(",").map(Number);
    ["warden","hunter","keeper","captain"].forEach(k => { memberStatBonuses[k] = {}; });
  } else if (derivedHps !== null) {
    food = [derivedHps, derivedHps, derivedHps, derivedHps];
  } else {
    food = [26, 20, 22, 24]; // default
  }

  return {
    runs:      parseInt(get("--runs", "1000")),
    level:     num("--level",    10),
    duration:  num("--duration", 1800),
    food:      { warden:food[0]??26, hunter:food[1]??20, keeper:food[2]??22, captain:food[3]??24 },
    memberStatBonuses,  // per-member { vit:2, fat:1 } etc
    memberRecipes,
    recipeArg,
    flArg,
    rLevel: effectiveRL,
    derivedHps,
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
    survival:  a.includes("--survival"),
    hunterMight: get("--hunter","agility") === "might",
    burstmul:  num("--burstmul", 1.0),
    verbose:   a.includes("--verbose"),
    beta:      num("--beta",    1.0),    // cascade exponent (1=current, 0=neutral, <0=last-stand)
    decouple:  a.includes("--decouple"),// independent per-member spikes
    sink:      a.includes("--sink"),    // reserve pool instead of softcap
    rmax:      num("--rmax",    50),    // reserve pool cap for sink mode
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
  const hTpl  = cfg.hunterMight ? TPL.hunterM : TPL.hunterA;
  const tpls  = { warden:TPL.warden, hunter:hTpl, keeper:TPL.keeper, captain:TPL.captain };

  // build party — apply per-member food stat bonuses on top of base stats
  const M = {};
  ORDER.forEach(k => {
    const tpl = tpls[k];
    const sb  = (cfg.memberStatBonuses && cfg.memberStatBonuses[k]) || {};
    const baseMig = statAt(tpl,"mig",lvl) + (sb.mig||0);
    const baseAgi = statAt(tpl,"agi",lvl) + (sb.agi||0);
    const baseVit = statAt(tpl,"vit",lvl) + (sb.vit||0);
    const baseWil = statAt(tpl,"wil",lvl) + (sb.wil||0);
    const baseFat = statAt(tpl,"fat",lvl) + (sb.fat||0);
    const max = Math.round(30 + baseVit * 16);
    M[k] = {
      key:k, tpl, max, hp:max, wounds:0, grievous:false, atZero:false,
      mig:baseMig, agi:baseAgi, vit:baseVit, wil:baseWil, fat:baseFat,
      wilBase:baseWil, fatBase:baseFat, shDrain:0,
      stunned:false, stunTicks:0, broken:false,
      reserve:0,  // sink-mode overflow buffer; absorbs spike damage before morale
    };
  });

  const active    = () => ORDER.filter(k => !M[k].grievous && !M[k].broken);
  const standing  = () => active().filter(k => M[k].hp > 0);
  const inTrouble = () => active().filter(k => M[k].hp / M[k].max < 0.35).length;
  const inspRoll  = (base, boost) => Math.random() < (base + boost);
  const sustain   = () => ORDER.filter(k=>!M[k].grievous).reduce((s,k)=>s+cfg.food[k],0);

  function dpsBreakdown() {
    if (cfg.survival) return { raw:0, eff:0, physAfter:0, layerADrag:0, potency:0 };
    let raw = 0;
    ORDER.forEach(k => {
      if (M[k].grievous || M[k].hp <= 0 || M[k].stunned || M[k].broken) return;
      if (k==="keeper")  { if (M[k]._action !== "burst") raw += M[k].wil * 0.9; }
      else if (k==="hunter")  raw += M[k].agi + M[k].mig * 0.4;
      else if (k==="warden")  raw += M[k].mig * 0.5;
      else if (k==="captain") raw += M[k].mig * 0.3 + M[k].wil * 0.2;
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
  let rescuesUsed=0, wardsUsed=0, inspBoost=0, baFired=false;
  // first spike fires after one full interval (with jitter)
  let nextSpikeAt = Math.round(cfg.spikeiv * (0.5 + Math.random()));
  const fired   = {};
  const windows = { dawn:0, horn:0 };
  const events  = [];
  const logs    = [];
  const lg = (msg) => { if (verbose) logs.push(`${fmtT(t).padStart(5)} ${msg}`); };
  let prevTrouble = 0; // track last tick's trouble count to detect rising edge

  while (true) {
    t++;
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

    // Keeper action
    let rescue = null;
    M.keeper._action = "damage";
    if (!M.keeper.grievous && M.keeper.hp > 0 && !M.keeper.stunned && rescuesUsed < RESCUE_CAP) {
      for (const k of ORDER) {
        if (k==="keeper" || M[k].grievous) continue;
        if (M[k].hp <= 0) { rescue = k; break; }
      }
      if (rescue) M.keeper._action = "burst";
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

    // food healing (active members with hp > 0)
    act.forEach(k => {
      if (M[k].hp <= 0 || M[k].stunned) return;
      const hornBlock = windows.horn > 0 && k === "warden"; // Warden gets no food heal during Horn window
      if (cfg.sink) {
        // Sink mode: no throttle; overflow above max banks into a finite reserve that absorbs spikes
        if (!hornBlock) {
          const next = M[k].hp + cfg.food[k];
          if (next > M[k].max) {
            M[k].reserve = Math.min(cfg.rmax, M[k].reserve + (next - M[k].max));
            M[k].hp = M[k].max;
          } else {
            M[k].hp = next;
          }
        }
      } else {
        // Softcap mode (default): healing above 1.5× drain-per-member runs at 40% efficiency
        const incPer = cfg.drain / act.length;
        let heal = cfg.food[k];
        const soft = incPer * 1.5;
        if (heal > soft) heal = soft + (heal - soft) * 0.4;
        if (!hornBlock) M[k].hp = Math.min(M[k].max, M[k].hp + heal);
      }
    });

    // Keeper rescue burst
    if (rescue) {
      const burst = (40 + M.keeper.wil*4) * cfg.burstmul;
      M[rescue].hp = Math.min(M[rescue].max, Math.max(M[rescue].hp,0) + burst);
      rescuesUsed++;
      lg(`Cael bursts ${M[rescue].tpl.name} (+${Math.round(burst)}) rescue ${rescuesUsed}/${RESCUE_CAP}`);
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

    // Inspiration: Black Arrow (Hunter)
    if (!baFired && !M.hunter.grievous && !M.hunter.stunned && !M.hunter.broken && M.hunter.hp>0) {
      const elapsed = t/maxT;
      const ttk = bd.eff>0 ? boss/bd.eff : 1e9;
      const trem = maxT-t;
      const losing = cfg.survival ? elapsed>0.5 : (ttk>trem && elapsed>0.4);
      if (losing) {
        const rise = Math.min(cfg.blackArrowCap, (elapsed-0.4)*cfg.blackArrowCap*2.5);
        if (inspRoll(rise + M.hunter.fat*cfg.fateInspCoef, inspBoost)) {
          baFired=true;
          if (cfg.survival) { maxT -= Math.round(cfg.duration*0.15); events.push({t, kind:"Black Arrow (flaming)", who:M.hunter.tpl.name}); lg(`★ BLACK ARROW — clock cut`); }
          else              { boss = Math.max(0, boss-cfg.boss*0.18); events.push({t, kind:"Black Arrow", who:M.hunter.tpl.name}); lg(`★ BLACK ARROW — 18% resolve chunk`); }
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
  if (cfg.dread>0) console.log(`Dread ${cfg.dread} | Hope ${cfg.hope} | Avg stuns/fight: ${(res.totalStuns/n).toFixed(2)} | Avg breaks/fight: ${(res.totalBreaks/n).toFixed(2)}`);
  // food line
  const anyMemberRecipe = cfg.memberRecipes && Object.values(cfg.memberRecipes).find(r => r !== null);
  if (anyMemberRecipe && cfg.flArg !== null) {
    const flLabel = `FL${cfg.flArg}  →  ${cfg.derivedHps} HP/s per member`;
    const perMember = ["warden","hunter","keeper","captain"].map(k => {
      const rid = cfg.memberRecipes[k];
      if (!rid) return `${k[0].toUpperCase()}: none`;
      const r = FM.RECIPES.find(r => r.id === rid);
      const sb = cfg.memberStatBonuses[k] || {};
      const bStr = Object.entries(sb).map(([s,n])=>`+${n}${s}`).join(" ") || "HP/s";
      return `${k[0].toUpperCase()}: ${r ? r.name : rid} [${bStr}]`;
    }).join("  |  ");
    console.log(`Food ${flLabel}`);
    console.log(`     ${perMember}`);
  } else if (anyMemberRecipe && cfg.rLevel !== null) {
    console.log(`Food: ${FM.recipeLevelLabel(cfg.rLevel)}  →  ${cfg.derivedHps} HP/s per member`);
    const perMember = ["warden","hunter","keeper","captain"].map(k => {
      const rid = cfg.memberRecipes[k]; if (!rid) return "";
      const sb = cfg.memberStatBonuses[k] || {};
      const bStr = Object.entries(sb).map(([s,n])=>`+${n}${s}`).join(" ") || "HP/s";
      const r = FM.RECIPES.find(r => r.id === rid);
      return `${k[0].toUpperCase()}: ${r ? r.name : rid} [${bStr}]`;
    }).filter(Boolean).join("  |  ");
    if (perMember) console.log(`     ${perMember}`);
  } else if (cfg.rLevel !== null) {
    console.log(`Food: ${FM.recipeLevelLabel(cfg.rLevel)}  →  ${cfg.derivedHps} HP/s per member`);
  } else {
    console.log(`Food W/H/K/C: ${cfg.food.warden}/${cfg.food.hunter}/${cfg.food.keeper}/${cfg.food.captain}/s`);
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
  const inspNames = { "Laurelin's Grace":"Grace (Keeper)", "The Horn of Gondor":"Horn (Warden)", "Wrath, Ruin, and the Red Dawn":"Red Dawn (Captain)", "Black Arrow":"Black Arrow (Hunter)", "Black Arrow (flaming)":"Black Arrow flaming (Hunter)" };
  Object.entries(res.inspFired).forEach(([k,v])=>{
    if(k==="Black Arrow (flaming)"&&!cfg.survival) return;
    if(k==="Black Arrow"&&cfg.survival) return;
    console.log(`  ${(inspNames[k]||k).padEnd(26)} ${pct(v).padStart(6)}  fired in ${v} fights`);
  });

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
