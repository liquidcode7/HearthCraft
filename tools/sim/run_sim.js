#!/usr/bin/env node
// HearthCraft headless combat simulator
// Same math as hearthcraft_fight_sim.html — no DOM required.
// Usage:
//   node run_sim.js [--runs N] [--level L] [--duration S] [--food W,H,K,C]
//                   [--boss R] [--drain D] [--spike S] [--spikeiv I]
//                   [--phys P] [--dread V] [--shadow V] [--cold V] [--heat V]
//                   [--disease V] [--wake V] [--hope V] [--radiance V]
//                   [--hale V] [--warmth V] [--heatease V] [--alert V]
//                   [--survival] [--hunter might] [--burstmul F] [--verbose]
//
// Runs N fights and prints aggregate stats + Inspiration fire rates.

// ── constants (mirrored from the browser sim) ──────────────────────────────
const TPL = {
  warden:  { name:"Borin", role:"Warden",  soak:true,  start:{mig:13,agi:6, vit:15,wil:7, fat:6 }, grow:{mig:0.50,agi:0.15,vit:0.55,wil:0.20,fat:0.15} },
  hunterA: { name:"Mira",  role:"Hunter",  soak:false, start:{mig:9, agi:15,vit:7, wil:5, fat:9 }, grow:{mig:0.30,agi:0.55,vit:0.20,wil:0.15,fat:0.35} },
  hunterM: { name:"Mira",  role:"Hunter",  soak:false, start:{mig:15,agi:9, vit:8, wil:5, fat:7 }, grow:{mig:0.55,agi:0.30,vit:0.25,wil:0.15,fat:0.25} },
  keeper:  { name:"Cael",  role:"Keeper",  soak:false, start:{mig:5, agi:7, vit:9, wil:15,fat:12}, grow:{mig:0.15,agi:0.20,vit:0.30,wil:0.55,fat:0.45} },
  captain: { name:"Aelin", role:"Captain", soak:true,  start:{mig:8, agi:7, vit:12,wil:13,fat:13}, grow:{mig:0.20,agi:0.20,vit:0.40,wil:0.45,fat:0.45} },
};
const ORDER        = ["warden","hunter","keeper","captain"];
const PEN_COEF     = { warden:{stat:"mig",k:0.25}, hunter:{stat:"agi",k:0.90}, keeper:{stat:"agi",k:0.15}, captain:{stat:"mig",k:0.20} };
const PEN_SCALE    = 110;
const SHADOW_FLOOR = 0.55;
const SHADOW_RATE  = 0.0011;
const RESCUE_CAP   = 5;
const WARD_CAP     = 3;
const GRIEVOUS     = 5;

const statAt  = (tpl, k, lvl) => tpl.start[k] + tpl.grow[k] * (lvl - 1);
const moraleOf= (tpl, lvl)    => Math.round(30 + statAt(tpl,"vit",lvl) * 16);
const fmtT    = s => `${Math.floor(s/60)}:${String(Math.max(0,s)%60).padStart(2,"0")}`;
const clamp   = (v,lo,hi)     => Math.max(lo, Math.min(hi, v));

// ── CLI parsing ────────────────────────────────────────────────────────────
function parseArgs() {
  const a = process.argv.slice(2);
  const get = (flag, def) => { const i = a.indexOf(flag); return i>=0 ? a[i+1] : def; };
  const num = (flag, def) => parseFloat(get(flag, def));
  const food = (get("--food","26,20,22,24")).split(",").map(Number);
  return {
    runs:      parseInt(get("--runs", "1000")),
    level:     num("--level",    10),
    duration:  num("--duration", 1800),
    food:      { warden:food[0]||26, hunter:food[1]||20, keeper:food[2]||22, captain:food[3]||24 },
    boss:      num("--boss",    8500),
    drain:     num("--drain",   70),
    spike:     num("--spike",   160),
    spikeiv:   num("--spikeiv", 12),
    phys:      num("--phys",    0) / 100,
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
    // Inspiration rates (fixed in design; exposed for research runs)
    graceBase:      num("--grace",  0.05),
    hornBase:       num("--horn",   0.03),
    dawnBase:       num("--dawn",   0.03),
    blackArrowCap:  num("--ba-cap", 0.06),
  };
}

// ── single fight ───────────────────────────────────────────────────────────
function runFight(cfg, verbose) {
  const lvl   = cfg.level;
  const hTpl  = cfg.hunterMight ? TPL.hunterM : TPL.hunterA;
  const tpls  = { warden:TPL.warden, hunter:hTpl, keeper:TPL.keeper, captain:TPL.captain };

  // build party
  const M = {};
  ORDER.forEach(k => {
    const tpl = tpls[k];
    const max = moraleOf(tpl, lvl);
    M[k] = {
      key:k, tpl, max, hp:max, wounds:0, grievous:false, atZero:false,
      mig: statAt(tpl,"mig",lvl), agi: statAt(tpl,"agi",lvl),
      vit: statAt(tpl,"vit",lvl), wil: statAt(tpl,"wil",lvl), fat: statAt(tpl,"fat",lvl),
      wilBase: statAt(tpl,"wil",lvl), fatBase: statAt(tpl,"fat",lvl), shDrain:0,
    };
  });

  const active    = () => ORDER.filter(k => !M[k].grievous);
  const standing  = () => active().filter(k => M[k].hp > 0);
  const inTrouble = () => active().filter(k => M[k].hp / M[k].max < 0.35).length;
  const inspRoll  = (base, boost) => Math.random() < (base + boost);
  const sustain   = () => ORDER.filter(k=>!M[k].grievous).reduce((s,k)=>s+cfg.food[k],0);

  function dpsBreakdown() {
    if (cfg.survival) return { raw:0, eff:0, penSaved:0, willSaved:0, hopeSaved:0, pen:{total:0,parts:[]} };
    let raw = 0;
    ORDER.forEach(k => {
      if (M[k].grievous || M[k].hp <= 0) return;
      if (k==="keeper")  { if (M[k]._action !== "burst") raw += M[k].wil * 0.9; }
      else if (k==="hunter")  raw += M[k].agi + M[k].mig * 0.4;
      else if (k==="warden")  raw += M[k].mig * 0.5;
      else if (k==="captain") raw += M[k].mig * 0.3 + M[k].fat * 0.2;
    });
    if (windows.dawn > 0) raw *= 1.5;
    // penetration
    let penTotal = 0;
    ORDER.forEach(k => { if (M[k].grievous) return; const c=PEN_COEF[k]; penTotal += M[k][c.stat]*c.k; });
    const physAfter = Math.max(0, cfg.phys * (1 - Math.min(1, penTotal/PEN_SCALE)));
    // dread
    const capWil = !M.captain.grievous ? M.captain.wil : 0;
    const willCut = Math.min(1, capWil/100), hopeCut = Math.min(1, cfg.hope/100);
    const dreadAfter = Math.max(0, cfg.dread * (1 - Math.min(1, willCut+hopeCut)));
    const eff = Math.max(0, raw * (1-physAfter) * (1-dreadAfter));
    return { raw, eff, physAfter, dreadAfter, penTotal };
  }

  let t=0, boss=cfg.boss, maxT=cfg.duration;
  let rescuesUsed=0, wardsUsed=0, inspBoost=0, baFired=false;
  const fired   = {};
  const windows = { dawn:0, horn:0 };
  const events  = [];
  const logs    = [];
  const lg = (msg) => { if (verbose) logs.push(`${fmtT(t).padStart(5)} ${msg}`); };

  while (true) {
    t++;
    if (windows.dawn > 0) windows.dawn--;
    if (windows.horn > 0) { windows.horn--; }

    const act = active();
    const live = standing();

    // Keeper action
    let rescue = null;
    M.keeper._action = "damage";
    if (!M.keeper.grievous && M.keeper.hp > 0 && rescuesUsed < RESCUE_CAP) {
      for (const k of ORDER) {
        if (k==="keeper" || M[k].grievous) continue;
        if (M[k].hp <= 0) { rescue = k; break; }
      }
      if (rescue) M.keeper._action = "burst";
    }

    // steady drain (standing only)
    if (live.length) live.forEach(k => { M[k].hp -= cfg.drain / live.length; });

    // spike
    const isSpike = (t % cfg.spikeiv === 0) && cfg.spike > 0;
    if (isSpike) {
      let target = null;
      if (windows.horn > 0 && !M.warden.grievous && M.warden.hp > 0) {
        target = "warden";
      } else {
        const lv = live;
        target = lv.length ? lv[Math.floor(Math.random()*lv.length)] : null;
        if (target==="keeper" && (M.keeper.hp-cfg.spike)<=0 && M.warden.hp>0 && !M.warden.grievous && wardsUsed<WARD_CAP) {
          target="warden"; wardsUsed++;
          lg(`${M.warden.tpl.name} guards Cael from killing blow (guard ${wardsUsed}/${WARD_CAP})`);
        }
      }
      if (target) {
        M[target].hp -= cfg.spike;
        lg(`spike → ${M[target].tpl.name} (${Math.round(cfg.spike)})`);
      }
    }

    // food healing (standing only, soft cap)
    act.forEach(k => {
      if (M[k].hp <= 0) return;
      const incPer = cfg.drain / act.length;
      let heal = cfg.food[k];
      const soft = incPer * 1.5;
      if (heal > soft) heal = soft + (heal-soft)*0.4;
      if (!(windows.horn>0 && k==="warden")) M[k].hp = Math.min(M[k].max, M[k].hp + heal);
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

    // wound / grievous checks
    ORDER.forEach(k => {
      if (M[k].grievous) return;
      if (M[k].hp <= 0 && !M[k].atZero) {
        M[k].atZero = true; M[k].wounds++;
        lg(`${M[k].tpl.name} hits zero — wound ${M[k].wounds}/${GRIEVOUS}`);
        if (M[k].wounds >= GRIEVOUS) {
          if (!M.keeper.grievous && M.keeper.hp>0 && inspRoll(cfg.graceBase, inspBoost)) {
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

    // Inspiration: Horn of Gondor (Warden)
    if (inspBoost > 0) inspBoost = Math.max(0, inspBoost - 0.005);
    if (!fired.horn && windows.horn<=0 && inTrouble()>=2 && !M.warden.grievous && M.warden.hp>0 && inspRoll(cfg.hornBase, inspBoost)) {
      fired.horn=true; windows.horn=8;
      events.push({t, kind:"The Horn of Gondor", who:M.warden.tpl.name});
      lg(`★ HORN OF GONDOR — ${M.warden.tpl.name}`);
    }

    // Inspiration: Wrath, Ruin, and the Red Dawn (Captain)
    if (!fired.dawn && windows.dawn<=0 && inTrouble()>=2 && !M.captain.grievous && M.captain.hp>0 && inspRoll(cfg.dawnBase, 0)) {
      fired.dawn=true; windows.dawn=10;
      act.forEach(k => { M[k].hp = Math.min(M[k].max, M[k].hp + M[k].max*0.15); });
      inspBoost = 0.15;
      events.push({t, kind:"Wrath, Ruin, and the Red Dawn", who:M.captain.tpl.name});
      lg(`★ RED DAWN — ${M.captain.tpl.name} (inspBoost +0.15 for ~30s)`);
    }

    // DPS tick
    const bd = dpsBreakdown();
    if (!cfg.survival) boss = Math.max(0, boss - bd.eff);

    // Inspiration: Black Arrow (Hunter)
    if (!baFired && !M.hunter.grievous && M.hunter.hp>0) {
      const elapsed = t/maxT;
      const ttk = bd.eff>0 ? boss/bd.eff : 1e9;
      const trem = maxT-t;
      const losing = cfg.survival ? elapsed>0.5 : (ttk>trem && elapsed>0.4);
      if (losing) {
        const rise = Math.min(cfg.blackArrowCap, (elapsed-0.4)*cfg.blackArrowCap*2.5);
        if (inspRoll(rise, inspBoost)) {
          baFired=true;
          if (cfg.survival) { maxT -= Math.round(cfg.duration*0.15); events.push({t, kind:"Black Arrow (flaming)", who:M.hunter.tpl.name}); lg(`★ BLACK ARROW — clock cut`); }
          else              { boss = Math.max(0, boss-cfg.boss*0.35); events.push({t, kind:"Black Arrow", who:M.hunter.tpl.name}); lg(`★ BLACK ARROW — 35% resolve chunk`); }
        }
      }
    }

    // termination
    if (!cfg.survival && boss <= 0) return _result("VICTORY", t, M, events, rescuesUsed, wardsUsed, logs);
    if (!ORDER.some(k => !M[k].grievous && M[k].hp>0)) return _result("DEFEAT", t, M, events, rescuesUsed, wardsUsed, logs);
    if (t >= maxT) return _result(cfg.survival?"REPELLED":"STALEMATE", t, M, events, rescuesUsed, wardsUsed, logs);
  }
}

function _result(outcome, t, M, events, rescuesUsed, wardsUsed, logs) {
  const grievous = ORDER.filter(k=>M[k].grievous);
  const totalWounds = ORDER.reduce((s,k)=>s+M[k].wounds+(M[k].grievous?GRIEVOUS:0),0);
  const insps = events.filter(e=>/Grace|Horn|Red Dawn|Black Arrow/.test(e.kind));
  return { outcome, t, grievous, totalWounds, rescuesUsed, wardsUsed, events, insps, logs,
    wounds: Object.fromEntries(ORDER.map(k=>[k, M[k].wounds+(M[k].grievous?GRIEVOUS:0)])) };
}

// ── aggregate N fights ─────────────────────────────────────────────────────
function runMany(cfg, n, verbose) {
  const counts   = { VICTORY:0, DEFEAT:0, STALEMATE:0, REPELLED:0 };
  const inspFired = { "Laurelin's Grace":0, "The Horn of Gondor":0, "Wrath, Ruin, and the Red Dawn":0, "Black Arrow":0, "Black Arrow (flaming)":0 };
  let totalWounds=0, totalRescues=0, totalWardsUsed=0, totalTime=0;
  const woundsByRole = Object.fromEntries(ORDER.map(k=>[k,0]));
  let sample = null;

  for (let i=0; i<n; i++) {
    const r = runFight(cfg, verbose && i===0);
    counts[r.outcome]++;
    totalWounds  += r.totalWounds;
    totalRescues += r.rescuesUsed;
    totalWardsUsed += r.wardsUsed;
    totalTime    += r.t;
    ORDER.forEach(k => woundsByRole[k] += r.wounds[k]);
    r.insps.forEach(e => { if (inspFired[e.kind]!==undefined) inspFired[e.kind]++; });
    if (i===0) sample = r;
  }

  return { counts, inspFired, totalWounds, totalRescues, totalWardsUsed, totalTime, woundsByRole, n, sample };
}

// ── reporting ──────────────────────────────────────────────────────────────
function report(cfg, res) {
  const n = res.n;
  const pct = v => `${(v/n*100).toFixed(1)}%`;
  const avg = v => (v/n).toFixed(2);

  console.log("\n═══ HearthCraft Headless Sim ═══");
  console.log(`Runs: ${n}  |  Level ${cfg.level}  |  Duration ${fmtT(cfg.duration)}  |  ${cfg.survival?"SURVIVAL":"ATTRITION"}`);
  console.log(`Boss ${cfg.boss} resolve  |  Drain ${cfg.drain}/s  |  Spike ${cfg.spike} every ${cfg.spikeiv}s`);
  if (cfg.phys>0) console.log(`Armor ${Math.round(cfg.phys*100)}%`);
  const haz=[];
  if(cfg.dread>0) haz.push(`Dread ${Math.round(cfg.dread*100)}%`+(cfg.hope>0?` Hope ${cfg.hope}`:""));
  if(cfg.shadow>0) haz.push(`Shadow ${cfg.shadow}`+(cfg.radiance>0?` Radiance ${cfg.radiance}`:""));
  if(cfg.cold>0)    haz.push(`Cold ${cfg.cold}`+(cfg.warmth>0?` Warmth ${cfg.warmth}`:""));
  if(cfg.heat>0)    haz.push(`Heat ${cfg.heat}`+(cfg.heatease>0?` Heatease ${cfg.heatease}`:""));
  if(cfg.disease>0) haz.push(`Disease ${cfg.disease}`+(cfg.hale>0?` Hale ${cfg.hale}`:""));
  if(cfg.wake>0)    haz.push(`Wake ${cfg.wake}`+(cfg.alert>0?` Alert ${cfg.alert}`:""));
  if(haz.length) console.log(`Hazards: ${haz.join("  ")}`);
  console.log(`Food W/H/K/C: ${cfg.food.warden}/${cfg.food.hunter}/${cfg.food.keeper}/${cfg.food.captain}/s`);

  console.log("\n── Outcomes ──");
  Object.entries(res.counts).forEach(([k,v])=>{ if(v>0) console.log(`  ${k.padEnd(10)} ${pct(v).padStart(6)}  (${v})`); });

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
