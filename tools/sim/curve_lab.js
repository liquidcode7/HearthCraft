#!/usr/bin/env node
/* curve_lab.js — abstract Monte Carlo to study cliff-vs-curve in HearthCraft survival fights.
 *
 * Strips the combat to its essential dynamics so we can isolate what makes the
 * win-rate a CLIFF vs a CURVE. Maps onto HearthCraft terms but is intentionally minimal.
 *
 * Members: 4. Each has morale m in [0, MMAX]. Incapacitated at m<=0 (stops draining, stops healing).
 *
 * ── The one knob that matters: feedback exponent BETA ────────────────────────
 *   drain_i = (D/4) * (4 / nAlive)^BETA
 *     BETA =  1  -> drain_i = D/nAlive      full redistribution  (POSITIVE feedback / cascade)  [BASELINE]
 *     BETA =  0  -> drain_i = D/4           no redistribution    (NEUTRAL feedback)
 *     BETA < 0  -> survivors face LESS drain as members fall     (NEGATIVE feedback / last-stand)
 *
 * Softcap modes:
 *   'penalty' (baseline): heal above 1.5x per-member drain runs at 40% efficiency
 *   'none'              : raw heal, no penalty
 *   'sink'              : no penalty; overflow above MMAX banks into a finite reserve that eats spikes first
 *
 * independentSpikes:
 *   false (baseline): one global spike every `interval` ticks, hits a random living member
 *   true            : each living member rolls its own spike (same total rate) -> more independence
 */

function simFight(p) {
  const N = 4;
  const m = new Array(N).fill(p.MMAX);
  const reserve = new Array(N).fill(0);
  let alive = N;
  const aliveArr = new Array(N).fill(true);
  let bossHP = p.bossResolve || 0;            // 0 = pure survival fight (no kill axis)
  let killed = false;

  for (let t = 0; t < p.T; t++) {
    if (alive === 0) break;
    const factor = Math.pow(4 / alive, p.beta);     // feedback term
    const drainPer = (p.D / 4) * factor;

    // second axis: party burns the boss down; DPS scales with the same food (better food = stronger party)
    if (bossHP > 0) {
      let dps = 0;
      for (let i = 0; i < N; i++) if (aliveArr[i]) dps += (p.dpsBase || 0) + (p.dpsK || 0) * p.H;
      bossHP -= dps;
      if (bossHP <= 0) { killed = true; break; }    // killed it while still standing -> win
    }

    // continuous drain + heal
    for (let i = 0; i < N; i++) {
      if (!aliveArr[i]) continue;
      let heal = p.H;
      if (p.softcap === 'penalty') {
        const cap = 1.5 * drainPer;
        if (heal > cap) heal = cap + 0.4 * (heal - cap);
      }
      let next = m[i] + heal - drainPer;
      if (p.softcap === 'sink' && next > p.MMAX) {
        reserve[i] = Math.min(p.RMAX, reserve[i] + (next - p.MMAX));
        next = p.MMAX;
      }
      m[i] = next > p.MMAX ? p.MMAX : next;
      if (m[i] <= 0) { aliveArr[i] = false; alive--; m[i] = 0; }
    }

    // spikes
    if (p.independentSpikes) {
      const pPerTick = 1 / (p.interval * 4);          // same total rate as global
      for (let i = 0; i < N; i++) {
        if (!aliveArr[i]) continue;
        if (Math.random() < pPerTick) applySpike(i);
      }
    } else {
      if (t > 0 && t % p.interval === 0 && alive > 0) {
        // random living target
        let k = Math.floor(Math.random() * alive);
        for (let i = 0; i < N; i++) {
          if (!aliveArr[i]) continue;
          if (k === 0) { applySpike(i); break; }
          k--;
        }
      }
    }

    function applySpike(i) {
      let dmg = p.S;
      if (p.softcap === 'sink' && reserve[i] > 0) {
        const absorbed = Math.min(reserve[i], dmg);
        reserve[i] -= absorbed; dmg -= absorbed;
      }
      m[i] -= dmg;
      if (m[i] <= 0) { aliveArr[i] = false; alive--; m[i] = 0; }
    }
  }

  // win logic:
  //  - boss fight: must KILL before timer/wipe. timer expiry with boss alive = loss.
  //  - pure survival: just don't get wiped.
  const survived = (p.bossResolve > 0) ? killed : (alive >= 1);
  return { survived, flawless: (survived && alive === 4), casualties: N - alive };
}

function sweep(cfg, Hvals, trials) {
  return Hvals.map(H => {
    let win = 0, flawless = 0, cas = 0;
    for (let n = 0; n < trials; n++) {
      const r = simFight({ ...cfg, H });
      if (r.survived) win++;
      if (r.flawless) flawless++;
      cas += r.casualties;
    }
    return { H, win: win / trials, flawless: flawless / trials, cas: cas / trials };
  });
}

// Width of the 20%->80% transition band in H-units. Wider = gentler curve. Returns null if it never crosses.
function bandWidth(curve) {
  const find = (thr) => {
    for (let i = 1; i < curve.length; i++) {
      if (curve[i - 1].win < thr && curve[i].win >= thr) {
        const a = curve[i - 1], b = curve[i];
        return a.H + (thr - a.win) * (b.H - a.H) / (b.win - a.win);
      }
    }
    return null;
  };
  const lo = find(0.20), hi = find(0.80);
  return (lo != null && hi != null) ? +(hi - lo).toFixed(2) : null;
}

// ── Base encounter ────────────────────────────────────────────────────────────
const BASE = { D: 14, S: 22, interval: 5, T: 900, MMAX: 100, RMAX: 50,
               beta: 1, softcap: 'penalty', independentSpikes: false };

// Three tiers of food, deliberately CLOSE together in HP/s (this is the real constraint)
const FL = { FL1: 5.0, FL2: 6.0, FL3: 7.5 };
const Hvals = []; for (let h = 2; h <= 12.0001; h += 0.5) Hvals.push(+h.toFixed(1));

// Calibrate global drain D so the MIDDLE tier (FL2) wins ~60%. This holds mid-difficulty
// constant across configs so we can compare TIER SPREAD fairly, not overall difficulty.
function winAt(cfg, H, trials) { let w = 0; for (let n=0;n<trials;n++) if (simFight({...cfg,H}).survived) w++; return w/trials; }
function calibrateD(cfg, targetH, targetWin = 0.60) {
  let lo = 2, hi = 60;                       // higher D = harder = lower win (monotone)
  for (let it = 0; it < 22; it++) {
    const mid = (lo + hi) / 2;
    const w = winAt({ ...cfg, D: mid }, targetH, 700);
    if (w > targetWin) lo = mid; else hi = mid;
  }
  return +((lo + hi) / 2).toFixed(2);
}

const configs = {
  'A baseline      beta=+1, global spikes, softcap=penalty': { ...BASE },
  'B cascade-flip  beta=-0.6                              ': { ...BASE, beta: -0.6 },
  'C decoupled     beta=0,  independent spikes            ': { ...BASE, beta: 0, independentSpikes: true },
  'D sink          beta=+1, softcap=sink                  ': { ...BASE, softcap: 'sink' },
  'E combined      flip + decouple + sink                 ': { ...BASE, beta: -0.6, independentSpikes: true, softcap: 'sink' },
};

const TRIALS = 4000;
console.log(`\nEncounter: spike=${BASE.S}/${BASE.interval}s T=${BASE.T}s  | tiers close together: FL1=${FL.FL1} FL2=${FL.FL2} FL3=${FL.FL3} HP/s`);
console.log(`Each config's drain D is auto-calibrated so FL2 wins ~60% (equal mid-difficulty).`);
console.log(`TARGET spread: FL1 ~25%, FL2 ~60%, FL3 ~90%.  trials=${TRIALS}\n`);
console.log('config                                                  D     FL1    FL2    FL3   | spread FL3-FL1 | 20->80 band');
console.log('-'.repeat(120));

const curves = {};
for (const [name, cfg] of Object.entries(configs)) {
  const D = calibrateD(cfg, FL.FL2);
  const tuned = { ...cfg, D };
  const c = sweep(tuned, Hvals, TRIALS);
  curves[name] = c;
  const at = (H) => c.reduce((a, b) => Math.abs(b.H - H) < Math.abs(a.H - H) ? b : a).win;
  const w = bandWidth(c);
  const pct = (x) => (x * 100).toFixed(0).padStart(3) + '%';
  const spread = ((at(FL.FL3) - at(FL.FL1)) * 100).toFixed(0);
  console.log(name + '  ' + String(D).padStart(5) + '  ' + pct(at(FL.FL1)) + '   ' + pct(at(FL.FL2)) + '   ' + pct(at(FL.FL3)) +
              '   |     ' + spread.padStart(3) + ' pts    |  ' + (w == null ? ' n/a ' : w.toFixed(2).padStart(5)) + ' HP/s');
}

// ── ASCII curves so we can SEE cliff vs curve ────────────────────────────────
console.log('\nWin% vs food HP/s  (each config calibrated to FL2~60%; columns = H 2..12)\n');
console.log('         ' + Hvals.map(h => String(h).padStart(4)).join(''));
for (const [name, c] of Object.entries(curves)) {
  const row = c.map(p => (p.win * 99).toFixed(0).padStart(4)).join('');
  console.log(name.slice(0, 8).padEnd(9) + row);
}
console.log('         ' + Hvals.map(h => (h===FL.FL1||h===FL.FL2||h===FL.FL3)?'  ^ ':'    ').join(''));
console.log('              (^ marks nearest column to FL1 / FL2 / FL3)');

// ── Texture: gap between "survived" and "flawless" = the clutch zone ──────────
console.log('\nClutch texture at FL2 (survive% minus flawless%) — bigger = more "won but bloodied" fights\n');
for (const [name, c] of Object.entries(curves)) {
  const p = c.reduce((a, b) => Math.abs(b.H - FL.FL2) < Math.abs(a.H - FL.FL2) ? b : a);
  console.log('  ' + name.slice(0, 16) + '  survive ' + (p.win*100).toFixed(0).padStart(3) +
              '%  flawless ' + (p.flawless*100).toFixed(0).padStart(3) + '%  clutch ' + ((p.win-p.flawless)*100).toFixed(0).padStart(3) + ' pts');
}

// ── TWO-AXIS fight: survival AND a kill-the-boss race ────────────────────────
console.log('\n\n=== TWO-AXIS FIGHT (survival + DPS race) — even on the WORST topology (beta=1) ===\n');
function winAtBoss(cfg, H, trials) { let w=0; for(let n=0;n<trials;n++) if(simFight({...cfg,H}).survived) w++; return w/trials; }
function calibrateResolve(cfg, targetH, targetWin = 0.60) {
  let lo = 1000, hi = 60000;
  for (let it = 0; it < 22; it++) {
    const mid = (lo + hi) / 2;
    const w = winAtBoss({ ...cfg, bossResolve: mid }, targetH, 700);
    if (w > targetWin) lo = mid; else hi = mid;
  }
  return Math.round((lo + hi) / 2);
}
const twoAxisCfgs = {
  'F two-axis  baseline topology (beta=+1)        ': { ...BASE, D: 19, beta: 1, dpsBase: 0, dpsK: 0.9 },
  'G two-axis  combined topology (flip+decpl+sink)': { ...BASE, D: 19, beta: -0.6, independentSpikes: true, softcap: 'sink', dpsBase: 0, dpsK: 0.9 },
};
console.log('config                                            resolve   FL1    FL2    FL3   | spread | 20->80 band');
console.log('-'.repeat(108));
for (const [name, cfg] of Object.entries(twoAxisCfgs)) {
  const R = calibrateResolve(cfg, FL.FL2);
  const tuned = { ...cfg, bossResolve: R };
  const c = sweep(tuned, Hvals, TRIALS);
  const at = (H) => c.reduce((a, b) => Math.abs(b.H - H) < Math.abs(a.H - H) ? b : a).win;
  const pct = (x) => (x*100).toFixed(0).padStart(3)+'%';
  const w = bandWidth(c);
  console.log(name + '  ' + String(R).padStart(6) + '  ' + pct(at(FL.FL1)) + '   ' + pct(at(FL.FL2)) + '   ' + pct(at(FL.FL3)) +
              '   | ' + ((at(FL.FL3)-at(FL.FL1))*100).toFixed(0).padStart(3) + 'pts | ' + (w==null?' n/a ':w.toFixed(2).padStart(5)) + ' HP/s');
  console.log('           curve: ' + c.map(p=>(p.win*99).toFixed(0).padStart(4)).join(''));
}

// ── OPTION 3 demo: WHY some counters reward thought and others don't ─────────
// Additive counters (flat ward, warmth) are substitutable by raw sustain -> weak, no real choice.
// MULTIPLICATIVE counters are not: Dread suppresses healing by factor (1-d). Piling on HP/s hits a
// wall; buying Hope (removing Dread) multiplies the value of ALL your sustain. That's a real decision.
function simFightDread(p, hope) {           // hope in [0,1] cancels that fraction of Dread
  const N = 4; const m = new Array(N).fill(p.MMAX); const al = new Array(N).fill(true); let alive = N;
  const dEff = p.dread * (1 - hope);
  for (let t = 0; t < p.T; t++) {
    if (!alive) break;
    const drainPer = p.D / 4;
    for (let i = 0; i < N; i++) {
      if (!al[i]) continue;
      const heal = p.sustain * (1 - dEff);              // <- Dread suppresses healing (multiplicative)
      m[i] = Math.min(p.MMAX, m[i] + heal - drainPer);
      if (m[i] <= 0) { al[i] = false; alive--; }
    }
    if (t > 0 && t % p.interval === 0 && alive) {
      let k = Math.floor(Math.random() * alive);
      for (let i = 0; i < N; i++) { if (!al[i]) continue; if (k===0){ m[i]-=p.S; if(m[i]<=0){al[i]=false;alive--;} break;} k--; }
    }
  }
  return alive >= 1;
}
function wr(p, hope, sustain, trials) { let w=0; for(let n=0;n<trials;n++) if(simFightDread({...p,sustain}, hope)) w++; return w/trials; }
const PD = { D: 20, S: 16, interval: 6, T: 900, MMAX: 100, dread: 0.45 };
const BUD = 9;   // budget: spend on raw sustain, OR divert ~3 of it to buy full Hope
console.log('\nOption 3 — additive vs MULTIPLICATIVE counters (why only some prep rewards thought)\n');
console.log('  enemy Dread = 0.45 (suppresses all healing by 45%). budget = ' + BUD + ' HP/s-equiv.\n');
console.log('  naive  (all ' + BUD + ' into sustain, ignore Dread)      win%: ' + (wr(PD, 0,   BUD,   6000)*100).toFixed(0) + '%');
console.log('  smart  (spend 3 to cancel Dread, ' + (BUD-3) + ' sustain)    win%: ' + (wr(PD, 1.0, BUD-3, 6000)*100).toFixed(0) + '%');
console.log('  -> sustain alone is throttled; Hope multiplies the value of everything else. That is a real choice.');
console.log('     (a flat additive counter here would NOT show this gap — sustain would just substitute for it.)\n');
