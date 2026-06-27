# Dread Redesign — Two-Layer Floor + Peak Model — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current single-multiplier dread model with a two-layer system: Layer A (persistent floor drag, never fully removed) and Layer B (action denial — stun + morale-break — only active when the party is insufficiently prepared).

**Architecture:** Both sims (`hearthcraft_fight_sim.html` and `run_sim.js`) carry identical dread logic — changes must be mirrored in both. The browser sim additionally drives the Results tab display. Constants are placeholders; the sim will find final values.

**Tech Stack:** Vanilla JS in `hearthcraft_fight_sim.html`, Node.js in `run_sim.js`, Markdown in `docs/`.

## Global Constraints

- All changes stay in `tools/sim/` and `docs/` — no Kotlin source touched
- All dread constants must be identical between `hearthcraft_fight_sim.html` and `run_sim.js`
- `broken` members (fled field) are permanently out — same exclusion rules as `grievous`
- `stunned` members are temporarily inactive — still take drain/spikes, excluded from DPS/healing/rescues/Inspirations
- Captain targeting weight for stun = `0.3×`; for break = `0.15×`
- Layer A formula: `layerADrag = min(1, effectiveDread × VAR_COEF + rawDread × FLOOR_COEF)`
- Layer B checks every 30 ticks (`BREAK_CHECK_INTERVAL`)
- Stun duration = 5 ticks (`STUN_TICKS`)
- Captain stunned → `willCut = 0` that tick
- Do not touch `docs/v1-plan.md` or any Kotlin source

---

## File Map

| File | What changes |
|---|---|
| `tools/sim/hearthcraft_fight_sim.html` | Constants, member state, `dpsBreakdown()`, `stepOnce()`, `updLive()`, `buildSummary()`, `renderResults()` |
| `tools/sim/run_sim.js` | Constants, member state, `dpsBreakdown()`, `runFight()` loop, `_result()`, `report()` |
| `docs/combat-model.md` | Replace dread section, update constants table |

---

## Task 1: Constants + Member State in Both Files

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html` (~line 604)
- Modify: `tools/sim/run_sim.js` (~line 44)

**Interfaces:**
- Produces: `DREAD_VAR_COEF`, `DREAD_FLOOR_COEF`, `T_TEMP`, `T_PERM`, `STUN_TICKS`, `BREAK_CHECK_INTERVAL`, `CAPTAIN_STUN_WEIGHT`, `CAPTAIN_BREAK_WEIGHT` — used by Tasks 2–5
- Produces: `M[k].stunned`, `M[k].stunTicks`, `M[k].broken` — used by Tasks 2–5

- [ ] **Step 1: Add dread constants to browser sim**

Find:
```js
const RMAX=50; // reserve pool cap per member (sink mode) — absorbs spike damage before morale
```

Insert immediately after:
```js
const DREAD_VAR_COEF       = 0.008;   // Layer A variable term coefficient (placeholder — tune in sim)
const DREAD_FLOOR_COEF     = 0.003;   // Layer A floor term coefficient (placeholder — tune in sim)
const T_TEMP               = 0.65;    // negation threshold below which Gate 1 stuns fire
const T_PERM               = 0.35;    // negation threshold below which Gate 2 breaks fire
const STUN_TICKS           = 5;       // duration of temporary incapacitation in ticks
const BREAK_CHECK_INTERVAL = 30;      // ticks between Layer B gate checks
const CAPTAIN_STUN_WEIGHT  = 0.3;     // Captain's relative targeting weight in stun pool
const CAPTAIN_BREAK_WEIGHT = 0.15;    // Captain's relative targeting weight in break pool
```

- [ ] **Step 2: Add `stunned`, `stunTicks`, `broken` to browser sim `buildParty()`**

Find:
```js
    M[k]={key:k,tpl,role:tpl.role,cls:tpl.cls,soak:tpl.soak,color:tpl.color,max,hp:max,reserve:0,wounds:0,grievous:false,atZero:false,action:"",horn:false,
```

Replace with:
```js
    M[k]={key:k,tpl,role:tpl.role,cls:tpl.cls,soak:tpl.soak,color:tpl.color,max,hp:max,reserve:0,wounds:0,grievous:false,atZero:false,stunned:false,stunTicks:0,broken:false,action:"",horn:false,
```

- [ ] **Step 3: Update `active()` in browser sim to exclude broken members**

Find:
```js
function active(){return ORDER.filter(k=>!M[k].grievous);}
```

Replace:
```js
function active(){return ORDER.filter(k=>!M[k].grievous&&!M[k].broken);}
```

- [ ] **Step 4: Add dread constants to headless runner**

Find:
```js
const GRIEVOUS     = 5;
```

Insert immediately after:
```js
const DREAD_VAR_COEF       = 0.008;
const DREAD_FLOOR_COEF     = 0.003;
const T_TEMP               = 0.65;
const T_PERM               = 0.35;
const STUN_TICKS           = 5;
const BREAK_CHECK_INTERVAL = 30;
const CAPTAIN_STUN_WEIGHT  = 0.3;
const CAPTAIN_BREAK_WEIGHT = 0.15;
```

- [ ] **Step 5: Add `stunned`, `stunTicks`, `broken` to headless runner party init**

Find:
```js
      wilBase:baseWil, fatBase:baseFat, shDrain:0,
      reserve:0,  // sink-mode overflow buffer; absorbs spike damage before morale
```

Replace:
```js
      wilBase:baseWil, fatBase:baseFat, shDrain:0,
      stunned:false, stunTicks:0, broken:false,
      reserve:0,
```

- [ ] **Step 6: Update `active()` in headless runner to exclude broken members**

Find:
```js
  const active    = () => ORDER.filter(k => !M[k].grievous);
```

Replace:
```js
  const active    = () => ORDER.filter(k => !M[k].grievous && !M[k].broken);
```

- [ ] **Step 7: Verify constants exist in both files**

Open browser sim — open console, type `DREAD_VAR_COEF`. Expected: `0.008`.

Run headless: `node tools/sim/run_sim.js --level 1 --runs 1`. Expected: no crash (constants defined).

- [ ] **Step 8: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html tools/sim/run_sim.js
git commit -m "[v1] Sim: dread redesign — constants + stunned/broken member state"
```

---

## Task 2: Layer A Formula in Browser Sim `dpsBreakdown()`

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html` (function `dpsBreakdown`, ~lines 794–845)

**Interfaces:**
- Produces: `dpsBreakdown()` returns `layerADrag` (replaces `dreadAfter`); still returns `willSaved`, `hopeSaved`

- [ ] **Step 1: Update the DPS raw loop to skip stunned/broken members**

Find:
```js
  let raw=0;ORDER.forEach(k=>{if(M[k].grievous||M[k].hp<=0)return;
```

Replace:
```js
  let raw=0;ORDER.forEach(k=>{if(M[k].grievous||M[k].hp<=0||M[k].stunned||M[k].broken)return;
```

- [ ] **Step 2: Replace the dread block with Layer A formula**

Find:
```js
  // DREAD: Will (stat, current = possibly shadow-drained) + Hope (flat %, additive then cap)
  const capWill=(!M.captain.grievous)?M.captain.wil:0;
  const dreadRaw=P.dread;
  const willCut=Math.min(1,capWill/100), hopeCut=Math.min(1,P.hope/100);
  const dreadAfter=Math.max(0,dreadRaw*(1-Math.min(1,willCut+hopeCut)));
  const eff=raw*(1-physAfter)*(1-dreadAfter);
  // preserved-by-counter decomposition
  const effNoPen=raw*(1-physRaw)*(1-dreadAfter);
  const dreadNoWill=Math.max(0,dreadRaw*(1-hopeCut));
  const dreadNoHope=Math.max(0,dreadRaw*(1-willCut));
  const effNoWill=raw*(1-physAfter)*(1-dreadNoWill);
  const effNoHope=raw*(1-physAfter)*(1-dreadNoHope);
```

Replace:
```js
  // DREAD: Layer A two-term formula — variable drag (suppressed by counters) + floor (always present)
  const capWill=(!M.captain.grievous&&!M.captain.stunned)?M.captain.wil:0;
  const dreadRaw=P.dread;
  const willCut=Math.min(1,capWill/100), hopeCut=Math.min(1,P.hope/100);
  const negation=willCut+hopeCut;
  const effectiveDread=dreadRaw*(1-Math.min(1,negation));
  const layerADrag=Math.min(1,effectiveDread*DREAD_VAR_COEF+dreadRaw*DREAD_FLOOR_COEF);
  const eff=raw*(1-physAfter)*(1-layerADrag);
  // preserved-by-counter decomposition
  const effNoPen=raw*(1-physRaw)*(1-layerADrag);
  const dreadNoWill=dreadRaw*(1-Math.min(1,hopeCut));
  const layerANoWill=Math.min(1,dreadNoWill*DREAD_VAR_COEF+dreadRaw*DREAD_FLOOR_COEF);
  const dreadNoHope=dreadRaw*(1-Math.min(1,willCut));
  const layerANoHope=Math.min(1,dreadNoHope*DREAD_VAR_COEF+dreadRaw*DREAD_FLOOR_COEF);
  const effNoWill=raw*(1-physAfter)*(1-layerANoWill);
  const effNoHope=raw*(1-physAfter)*(1-layerANoHope);
```

- [ ] **Step 3: Update Captain-specific rawBy calculations to skip stunned/broken**

Find:
```js
    if(k==="captain"){
      const captPhysRaw=M.captain.grievous?0:M.captain.mig*0.3*(windows.dawn>0?1.5:1);
      const captMagicRaw=M.captain.grievous?0:M.captain.wil*0.2*(windows.dawn>0?1.5:1);
      effBy.captain_phys=captPhysRaw*(1-physAfter)*(1-dreadAfter);
      effBy.captain_magic=captMagicRaw*(1-dreadAfter);
```

Replace:
```js
    if(k==="captain"){
      const captPhysRaw=(M.captain.grievous||M.captain.stunned||M.captain.broken)?0:M.captain.mig*0.3*(windows.dawn>0?1.5:1);
      const captMagicRaw=(M.captain.grievous||M.captain.stunned||M.captain.broken)?0:M.captain.wil*0.2*(windows.dawn>0?1.5:1);
      effBy.captain_phys=captPhysRaw*(1-physAfter)*(1-layerADrag);
      effBy.captain_magic=captMagicRaw*(1-layerADrag);
```

- [ ] **Step 4: Update effBy for non-captain roles to use `layerADrag`**

Find:
```js
      const isPhys=k!=="keeper";
      effBy[k]=rawBy[k]*(isPhys?(1-physAfter)*(1-dreadAfter):(1-dreadAfter));
```

Replace:
```js
      const isPhys=k!=="keeper";
      effBy[k]=rawBy[k]*(isPhys?(1-physAfter)*(1-layerADrag):(1-layerADrag));
```

- [ ] **Step 5: Update `physMitTotal` and `magicDmg` global calculations**

Find:
```js
  const captPhysRawGlobal=M.captain.grievous?0:M.captain.mig*0.3*(windows.dawn>0?1.5:1);
  const captMagicRawGlobal=M.captain.grievous?0:M.captain.wil*0.2*(windows.dawn>0?1.5:1);
  const physMitTotal=(rawBy.warden+rawBy.hunter+captPhysRawGlobal)*physAfter*(1-dreadAfter);
  const magicDmg=rawBy.keeper*(1-dreadAfter)+captMagicRawGlobal*(1-dreadAfter);
```

Replace:
```js
  const captPhysRawGlobal=(M.captain.grievous||M.captain.stunned||M.captain.broken)?0:M.captain.mig*0.3*(windows.dawn>0?1.5:1);
  const captMagicRawGlobal=(M.captain.grievous||M.captain.stunned||M.captain.broken)?0:M.captain.wil*0.2*(windows.dawn>0?1.5:1);
  const physMitTotal=(rawBy.warden+rawBy.hunter+captPhysRawGlobal)*physAfter*(1-layerADrag);
  const magicDmg=rawBy.keeper*(1-layerADrag)+captMagicRawGlobal*(1-layerADrag);
```

- [ ] **Step 6: Update return statement (rename `dreadAfter` → `layerADrag`)**

Find:
```js
  return {raw,physRaw,physAfter,dreadRaw,dreadAfter,eff:Math.max(0,eff),
    penSaved:Math.max(0,eff-effNoPen), willSaved:Math.max(0,eff-effNoWill), hopeSaved:Math.max(0,eff-effNoHope),
    potency, effBy, physMitTotal:Math.max(0,physMitTotal), magicDmg:Math.max(0,magicDmg), dawnBoost};
```

Replace:
```js
  return {raw,physRaw,physAfter,dreadRaw,layerADrag,eff:Math.max(0,eff),
    penSaved:Math.max(0,eff-effNoPen), willSaved:Math.max(0,eff-effNoWill), hopeSaved:Math.max(0,eff-effNoHope),
    potency, effBy, physMitTotal:Math.max(0,physMitTotal), magicDmg:Math.max(0,magicDmg), dawnBoost};
```

- [ ] **Step 7: Update `unkill` early return to use `layerADrag`**

Find:
```js
  if(P.unkill)return {raw:0,physAfter:0,dreadAfter:0,eff:0,penSaved:0,willSaved:0,hopeSaved:0,potency:0,effBy:{warden:0,hunter:0,keeper:0,captain:0,captain_phys:0,captain_magic:0},physMitTotal:0,magicDmg:0,dawnBoost:0};
```

Replace:
```js
  if(P.unkill)return {raw:0,physAfter:0,layerADrag:0,eff:0,penSaved:0,willSaved:0,hopeSaved:0,potency:0,effBy:{warden:0,hunter:0,keeper:0,captain:0,captain_phys:0,captain_magic:0},physMitTotal:0,magicDmg:0,dawnBoost:0};
```

- [ ] **Step 8: Update `updLive()` to use `layerADrag` instead of `dreadAfter`**

Find:
```js
  if(showDread){$("g_dreadval").textContent="−"+Math.round(bd.dreadAfter*100)+"%";$("g_willsave").textContent="+"+Math.round(bd.willSaved)+" dps";$("g_hopesave").textContent="+"+Math.round(bd.hopeSaved)+" dps";}
```

Replace:
```js
  if(showDread){$("g_dreadval").textContent="−"+Math.round((bd.layerADrag||0)*100)+"%";$("g_willsave").textContent="+"+Math.round(bd.willSaved)+" dps";$("g_hopesave").textContent="+"+Math.round(bd.hopeSaved)+" dps";}
```

- [ ] **Step 9: Update per-member DPS display in `updLive()` to show stunned/broken states**

Find:
```js
  if(bd.effBy){ORDER.forEach(k=>{const el=$("g_dps_"+k);if(el)el.innerHTML=M[k].grievous?'<span class="no">grievous</span>':(M[k].hp<=0?'<span class="no">down</span>':(k==="keeper"&&M[k].action==="burst"?'<span style="color:var(--keeper)">rescuing</span>':"<b>"+Math.round(bd.effBy[k])+"</b>"));});}
```

Replace:
```js
  if(bd.effBy){ORDER.forEach(k=>{const el=$("g_dps_"+k);if(el)el.innerHTML=M[k].grievous?'<span class="no">grievous</span>':M[k].broken?'<span class="no">fled</span>':M[k].stunned?'<span style="color:#9a3526">frozen</span>':(M[k].hp<=0?'<span class="no">down</span>':(k==="keeper"&&M[k].action==="burst"?'<span style="color:var(--keeper)">rescuing</span>':"<b>"+Math.round(bd.effBy[k])+"</b>"));});}
```

- [ ] **Step 10: Add `layerADrag` to series push**

Find:
```js
  series.push({t,boss:boss,dps:bd.eff,raw:bd.raw,penSaved:bd.penSaved,willSaved:bd.willSaved,hopeSaved:bd.hopeSaved,sustain:sustain(),incoming,
```

Replace:
```js
  series.push({t,boss:boss,dps:bd.eff,raw:bd.raw,penSaved:bd.penSaved,willSaved:bd.willSaved,hopeSaved:bd.hopeSaved,layerADrag:bd.layerADrag||0,sustain:sustain(),incoming,
```

- [ ] **Step 11: Verify Layer A in browser**

Open `tools/sim/hearthcraft_fight_sim.html`. Set Dread to 50, Hope to 90 (high negation). Run a fight. In the Live tab, the dread row should show a small non-zero drag (Layer A floor). Set Hope to 0 — should show larger drag. No console errors.

- [ ] **Step 12: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[v1] Sim: dread Layer A formula in browser dpsBreakdown — floor drag replaces single multiplier"
```

---

## Task 3: Layer A Formula in Headless Runner `dpsBreakdown()`

**Files:**
- Modify: `tools/sim/run_sim.js` (function `dpsBreakdown`, ~lines 187–206)

**Interfaces:**
- Produces: `dpsBreakdown()` returns `layerADrag` (replaces `dreadAfter`)

- [ ] **Step 1: Replace `dpsBreakdown()` entirely**

Find the entire function:
```js
  function dpsBreakdown() {
    if (cfg.survival) return { raw:0, eff:0, penSaved:0, willSaved:0, hopeSaved:0, pen:{total:0,parts:[]} };
    let raw = 0;
    ORDER.forEach(k => {
      if (M[k].grievous || M[k].hp <= 0) return;
      if (k==="keeper")  { if (M[k]._action !== "burst") raw += M[k].wil * 0.9; }
      else if (k==="hunter")  raw += M[k].agi + M[k].mig * 0.4;
      else if (k==="warden")  raw += M[k].mig * 0.5;
      else if (k==="captain") raw += M[k].mig * 0.3 + M[k].wil * 0.2;
    });
    if (windows.dawn > 0) raw *= 1.5;
    // penetration via draught potency
    const physAfter = Math.max(0, cfg.phys * (1 - Math.min(1, cfg.potency/PEN_SCALE)));
    // dread
    const capWil = !M.captain.grievous ? M.captain.wil : 0;
    const willCut = Math.min(1, capWil/100), hopeCut = Math.min(1, cfg.hope/100);
    const dreadAfter = Math.max(0, cfg.dread * (1 - Math.min(1, willCut+hopeCut)));
    const eff = Math.max(0, raw * (1-physAfter) * (1-dreadAfter));
    return { raw, eff, physAfter, dreadAfter, potency: cfg.potency };
  }
```

Replace with:
```js
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
```

- [ ] **Step 2: Verify headless runs without crash**

```bash
node tools/sim/run_sim.js --level 3 --runs 100 --boss 40000 --drain 16 --spike 75 --spikeiv 13 --decouple --sink
```

Expected: completes, win rate output. No crash.

- [ ] **Step 3: Verify Layer A drag is non-zero at full negation**

```bash
node tools/sim/run_sim.js --level 5 --runs 100 --boss 115000 --drain 20 --spike 60 --spikeiv 14 --phys 22 --potency 45 --dread 50 --hope 90
```

Run the same without dread:
```bash
node tools/sim/run_sim.js --level 5 --runs 100 --boss 115000 --drain 20 --spike 60 --spikeiv 14 --phys 22 --potency 45
```

Expected: win rate with dread + hope 90 is slightly lower than no dread — confirming floor drag is present even with high negation.

- [ ] **Step 4: Commit**

```bash
git add tools/sim/run_sim.js
git commit -m "[v1] Sim: dread Layer A formula in headless runner dpsBreakdown"
```

---

## Task 4: Layer B Checks in Browser Sim `stepOnce()`

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html` (`stepOnce()` ~lines 862–976, `buildSummary()` ~line 1011)

**Interfaces:**
- Produces: stun/break events pushed to `events[]` with `kind:"dread-stun"` and `kind:"morale-break"`

- [ ] **Step 1: Add stun countdown at the top of `stepOnce()`**

Find (the first two lines of stepOnce body):
```js
  t++;let tickHeal=0;
  if(windows.dawn>0)windows.dawn--;
```

Replace:
```js
  t++;let tickHeal=0;
  if(windows.dawn>0)windows.dawn--;
  // stun countdown — decrement before this tick's action to let them act on the tick they recover
  if(P.dread>0){ORDER.forEach(k=>{if(M[k].stunned){M[k].stunTicks--;if(M[k].stunTicks<=0){M[k].stunned=false;M[k].stunTicks=0;log(`${M[k].tpl.name} shakes free — action restored`,"g");}}});}
```

- [ ] **Step 2: Add Layer B gate check after the simple hazard drain block**

Find the line immediately after the simple hazard drain block:
```js
   if(extra>0)act.forEach(k=>{M[k].hp-=extra/act.length;});}
  ORDER.forEach(k=>{if(M[k].grievous)return;
```

Insert between those two lines:
```js
   if(extra>0)act.forEach(k=>{M[k].hp-=extra/act.length;});}
  // --- DREAD Layer B: action denial — checked every BREAK_CHECK_INTERVAL ticks ---
  if(P.dread>0&&t%BREAK_CHECK_INTERVAL===0){
    const capWil=(!M.captain.grievous&&!M.captain.stunned)?M.captain.wil:0;
    const wCut=Math.min(1,capWil/100),hCut=Math.min(1,P.hope/100);
    const neg=wCut+hCut;
    function dreadPool(captW){const pool=[];ORDER.forEach(k=>{if(!M[k].grievous&&!M[k].broken&&!M[k].stunned&&M[k].hp>0)pool.push({k,w:k==="captain"?captW:1});});return pool;}
    function dreadPick(pool){const tot=pool.reduce((s,p)=>s+p.w,0);if(!tot)return null;let r=Math.random()*tot;for(const p of pool){r-=p.w;if(r<=0)return p.k;}return pool[pool.length-1]?.k||null;}
    if(neg<T_TEMP){const tgt=dreadPick(dreadPool(CAPTAIN_STUN_WEIGHT));if(tgt){M[tgt].stunned=true;M[tgt].stunTicks=STUN_TICKS;log(`${M[tgt].tpl.name} is seized by dread — action lost for ${STUN_TICKS} seconds`,"t");events.push({t,kind:"dread-stun",who:M[tgt].tpl.name});}}
    if(neg<T_PERM){const tgt=dreadPick(dreadPool(CAPTAIN_BREAK_WEIGHT));if(tgt){M[tgt].broken=true;log(`${M[tgt].tpl.name}'s courage fails — they are gone`,"t");events.push({t,kind:"morale-break",who:M[tgt].tpl.name});}}
  }
  ORDER.forEach(k=>{if(M[k].grievous)return;
```

- [ ] **Step 3: Update food healing to skip stunned members**

Find:
```js
  act.forEach(k=>{if(M[k].hp<=0)return;const heal=foodOf(k);
```

Replace:
```js
  act.forEach(k=>{if(M[k].hp<=0||M[k].stunned)return;const heal=foodOf(k);
```

- [ ] **Step 4: Update Keeper rescue check to skip if Keeper is stunned**

Find:
```js
  if(!M.keeper.grievous && M.keeper.hp>0 && rescuesUsed<RESCUE_CAP){for(const k of ORDER){if(k==="keeper"||M[k].grievous)continue;if(M[k].hp<=0){rescue=k;break;}}M.keeper.action=rescue?"burst":"damage";}
```

Replace:
```js
  if(!M.keeper.grievous && M.keeper.hp>0 && !M.keeper.stunned && rescuesUsed<RESCUE_CAP){for(const k of ORDER){if(k==="keeper"||M[k].grievous)continue;if(M[k].hp<=0){rescue=k;break;}}M.keeper.action=rescue?"burst":"damage";}
```

- [ ] **Step 5: Update Inspiration checks to exclude stunned/broken members**

Find the Horn of Gondor check:
```js
  if(crisisEdge&&!fired.horn&&!M.warden.grievous&&M.warden.hp>0&&inspRoll(
```

Replace:
```js
  if(crisisEdge&&!fired.horn&&!M.warden.grievous&&!M.warden.stunned&&!M.warden.broken&&M.warden.hp>0&&inspRoll(
```

Find the Red Dawn check:
```js
  if(crisisEdge&&!fired.dawn&&!M.captain.grievous&&M.captain.hp>0&&inspRoll(
```

Replace:
```js
  if(crisisEdge&&!fired.dawn&&!M.captain.grievous&&!M.captain.stunned&&!M.captain.broken&&M.captain.hp>0&&inspRoll(
```

Find the Black Arrow check:
```js
  if(!baFired&&!M.hunter.grievous&&M.hunter.hp>0){
```

Replace:
```js
  if(!baFired&&!M.hunter.grievous&&!M.hunter.stunned&&!M.hunter.broken&&M.hunter.hp>0){
```

Find the Laurelin's Grace check inside the wound block:
```js
      if(M[k].wounds>=GRIEVOUS){if(!M.keeper.grievous&&M.keeper.hp>0&&inspRoll(
```

Replace:
```js
      if(M[k].wounds>=GRIEVOUS){if(!M.keeper.grievous&&!M.keeper.stunned&&M.keeper.hp>0&&inspRoll(
```

- [ ] **Step 6: Update defeat check to include broken members**

Find:
```js
  if(!ORDER.some(k=>!M[k].grievous&&M[k].hp>0)){finish("DEFEAT","v-defeat");return true;}
```

Replace:
```js
  if(!ORDER.some(k=>!M[k].grievous&&!M[k].broken&&M[k].hp>0)){finish("DEFEAT","v-defeat");return true;}
```

- [ ] **Step 7: Update `buildSummary()` to show broken members**

Find:
```js
  ORDER.forEach(k=>{const m=M[k];let cls,txt;
    if(m.grievous){cls="ag";txt="grievous — Houses of Healing";}
```

Replace:
```js
  ORDER.forEach(k=>{const m=M[k];let cls,txt;
    if(m.broken){cls="ag";txt="fled the field — morale shattered";}
    else if(m.grievous){cls="ag";txt="grievous — Houses of Healing";}
```

- [ ] **Step 8: Verify Layer B in browser**

Open browser sim. Set Dread to 80 (high), Hope to 0, no Captain Will (lower level). Run a fight. Expected within ~30 ticks: log shows "is seized by dread" and "courage fails" lines. Run again with Dread 80 and Hope 70 (negation ≥ 0.65): no stun or break lines should appear (layer B suppressed). Check the defeat check fires correctly when all members are broken/grievous/down.

- [ ] **Step 9: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[v1] Sim: dread Layer B in browser — stun/break gates, Keeper/Inspiration exclusions, defeat check"
```

---

## Task 5: Layer B Checks in Headless Runner `runFight()`

**Files:**
- Modify: `tools/sim/run_sim.js` (`runFight()` loop ~lines 219–418, `_result()` ~line 420, `report()` ~line 462)

**Interfaces:**
- Consumes: `STUN_TICKS`, `BREAK_CHECK_INTERVAL`, `T_TEMP`, `T_PERM`, `CAPTAIN_STUN_WEIGHT`, `CAPTAIN_BREAK_WEIGHT`
- Produces: `_result()` returns `stunCount`, `breakCount`; `report()` prints them

- [ ] **Step 1: Add stun countdown at the top of the `while(true)` loop**

Find:
```js
    t++;
    if (windows.dawn > 0) windows.dawn--;
```

Replace:
```js
    t++;
    if (windows.dawn > 0) windows.dawn--;
    // stun countdown
    if (cfg.dread > 0) {
      ORDER.forEach(k => {
        if (M[k].stunned) {
          M[k].stunTicks--;
          if (M[k].stunTicks <= 0) { M[k].stunned = false; M[k].stunTicks = 0; lg(`${M[k].tpl.name} shakes free`); }
        }
      });
    }
```

- [ ] **Step 2: Update Keeper rescue check to skip if Keeper is stunned**

Find:
```js
    if (!M.keeper.grievous && M.keeper.hp > 0 && rescuesUsed < RESCUE_CAP) {
```

Replace:
```js
    if (!M.keeper.grievous && M.keeper.hp > 0 && !M.keeper.stunned && rescuesUsed < RESCUE_CAP) {
```

- [ ] **Step 3: Update food healing to skip stunned members**

Find:
```js
    act.forEach(k => {
      if (M[k].hp <= 0) return;
```

Replace:
```js
    act.forEach(k => {
      if (M[k].hp <= 0 || M[k].stunned) return;
```

- [ ] **Step 4: Add Layer B gate check after the simple hazard drain block**

Find the line immediately after the simple hazard drain:
```js
      if(extra>0) act.forEach(k=>{ M[k].hp -= extra/act.length; }); }
```

Insert immediately after:
```js
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
```

- [ ] **Step 5: Update Inspiration checks to exclude stunned/broken**

Find the Horn of Gondor check:
```js
    if (!fired.horn && crisisEdge && !M.warden.grievous && M.warden.hp>0 && inspRoll(
```

Replace:
```js
    if (!fired.horn && crisisEdge && !M.warden.grievous && !M.warden.stunned && !M.warden.broken && M.warden.hp>0 && inspRoll(
```

Find the Red Dawn check:
```js
    if (!fired.dawn && crisisEdge && !M.captain.grievous && M.captain.hp>0 && inspRoll(
```

Replace:
```js
    if (!fired.dawn && crisisEdge && !M.captain.grievous && !M.captain.stunned && !M.captain.broken && M.captain.hp>0 && inspRoll(
```

Find the Black Arrow check:
```js
    if (!baFired && !M.hunter.grievous && M.hunter.hp>0) {
```

Replace:
```js
    if (!baFired && !M.hunter.grievous && !M.hunter.stunned && !M.hunter.broken && M.hunter.hp>0) {
```

Find the Laurelin's Grace check:
```js
          if (!M.keeper.grievous && M.keeper.hp>0 && inspRoll(Math.min(0.25, cfg.graceBase + M.keeper.fat*cfg.fateInspCoef), inspBoost)) {
```

Replace:
```js
          if (!M.keeper.grievous && !M.keeper.stunned && M.keeper.hp>0 && inspRoll(Math.min(0.25, cfg.graceBase + M.keeper.fat*cfg.fateInspCoef), inspBoost)) {
```

- [ ] **Step 6: Update termination check to include broken members**

Find:
```js
    if (!ORDER.some(k => !M[k].grievous && M[k].hp>0)) return _result("DEFEAT", t, M, events, rescuesUsed, wardsUsed, logs, boss, cfg.boss);
```

Replace:
```js
    if (!ORDER.some(k => !M[k].grievous && !M[k].broken && M[k].hp>0)) return _result("DEFEAT", t, M, events, rescuesUsed, wardsUsed, logs, boss, cfg.boss);
```

- [ ] **Step 7: Add stun/break counts to `_result()`**

Find:
```js
  return { outcome, t, grievous, totalWounds, rescuesUsed, wardsUsed, events, insps, logs,
    bossRemaining, closeness,
    wounds: Object.fromEntries(ORDER.map(k=>[k, M[k].wounds+(M[k].grievous?GRIEVOUS:0)])) };
```

Replace:
```js
  const stunCount  = events.filter(e => e.kind === "dread-stun").length;
  const breakCount = events.filter(e => e.kind === "morale-break").length;
  return { outcome, t, grievous, totalWounds, rescuesUsed, wardsUsed, events, insps, logs,
    bossRemaining, closeness, stunCount, breakCount,
    wounds: Object.fromEntries(ORDER.map(k=>[k, M[k].wounds+(M[k].grievous?GRIEVOUS:0)])) };
```

- [ ] **Step 8: Add stun/break totals to `runMany()` and `report()`**

In `runMany()`, find:
```js
  let totalWounds=0, totalRescues=0, totalWardsUsed=0, totalTime=0;
```

Replace:
```js
  let totalWounds=0, totalRescues=0, totalWardsUsed=0, totalTime=0, totalStuns=0, totalBreaks=0;
```

Find inside the for loop:
```js
    totalWounds  += r.totalWounds;
    totalRescues += r.rescuesUsed;
```

Add after:
```js
    totalStuns   += r.stunCount || 0;
    totalBreaks  += r.breakCount || 0;
```

In the return of `runMany()`:
```js
  return { counts, inspFired, totalWounds, totalRescues, totalWardsUsed, totalTime,
           woundsByRole, n, sample, closeDefeats, totalBossRemaining, defeatCount };
```

Replace:
```js
  return { counts, inspFired, totalWounds, totalRescues, totalWardsUsed, totalTime,
           woundsByRole, n, sample, closeDefeats, totalBossRemaining, defeatCount,
           totalStuns, totalBreaks };
```

In `report()`, find the line:
```js
  if (cfg.phys>0) console.log(`Armor ${Math.round(cfg.phys*100)}% | Draught potency ${cfg.potency} / ${PEN_SCALE}`);
```

Add after it:
```js
  if (cfg.dread>0) console.log(`Dread ${cfg.dread} | Hope ${cfg.hope} | Avg stuns/fight: ${(res.totalStuns/n).toFixed(2)} | Avg breaks/fight: ${(res.totalBreaks/n).toFixed(2)}`);
```

- [ ] **Step 9: Verify Layer B in headless runner**

Run with high dread, no antidote:
```bash
node tools/sim/run_sim.js --level 5 --runs 200 --boss 115000 --drain 20 --spike 60 --spikeiv 14 --dread 60 --hope 0 --decouple --sink --verbose
```

Expected: output line `Dread 60 | Hope 0 | Avg stuns/fight: N.NN | Avg breaks/fight: N.NN` with non-zero values. Verbose run shows stun/break log lines.

Run with sufficient Hope (negation ≥ T_TEMP):
```bash
node tools/sim/run_sim.js --level 5 --runs 200 --boss 115000 --drain 20 --spike 60 --spikeiv 14 --dread 60 --hope 70 --decouple --sink
```

Expected: stuns = 0.00, breaks = 0.00 (negation = 0.70 ≥ T_TEMP 0.65).

- [ ] **Step 10: Commit**

```bash
git add tools/sim/run_sim.js
git commit -m "[v1] Sim: dread Layer B in headless runner — stun/break gates, exclusions, event tracking"
```

---

## Task 6: Update Results Tab Dread Card

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html` (`renderResults()` ~line 945, specifically the `ch_dreadmet` block)

**Interfaces:**
- Consumes: `series[].layerADrag`, `events[]` with `kind:"dread-stun"` and `kind:"morale-break"`, `avg()` helper

- [ ] **Step 1: Replace the dread mitigation card block in `renderResults()`**

Find:
```js
  // Dread mitigation — Will + Hope counters
  const anyDread=series.some(s=>s.willSaved>0||s.hopeSaved>0||s.dawnBoost>0);
  if(anyDread){
    meterChart("ch_dreadmet",[
      {name:"Saved by Captain Will + Red Dawn", color:"#b8843c", value:captainBoostTotal},
      {name:"Saved by Hope",                   color:"#c9a84c", value:avgHopeSaved},
    ],"dps","Total Dread countered");
  } else {
    $("ch_dreadmet").innerHTML='<div class="empty" style="padding:24px 14px;text-align:center">No Dread in this fight — toggle Dread on the enemy to see how Will and Hope counter it.</div>';
  }
```

Replace:
```js
  // Dread mitigation — Layer A drag + Will/Hope counters + Layer B event counts
  if(P.dread>0){
    const avgLayerALoss=series.reduce((s,f)=>s+(f.raw||0)*(f.layerADrag||0),0)/series.length;
    const stunEvts=events.filter(e=>e.kind==="dread-stun").length;
    const breakEvts=events.filter(e=>e.kind==="morale-break").length;
    meterChart("ch_dreadmet",[
      {name:"Layer A drag (DPS lost to dread floor)",color:"#9a3526",value:avgLayerALoss},
      {name:"Saved by Captain Will + Red Dawn",color:"#b8843c",value:captainBoostTotal},
      {name:"Saved by Hope",color:"#c9a84c",value:avgHopeSaved},
    ],"dps","Dread summary");
    $("ch_dreadmet").innerHTML+=`<div class="meter-footer">Layer B events this fight: ${stunEvts} stun${stunEvts!==1?"s":""}, ${breakEvts} break${breakEvts!==1?"s":""}</div>`;
  } else {
    $("ch_dreadmet").innerHTML='<div class="empty" style="padding:24px 14px;text-align:center">No Dread in this fight — toggle Dread on the enemy to see how Will and Hope counter it.</div>';
  }
```

- [ ] **Step 2: Verify dread card in browser**

Run a fight with Dread 60, Hope 0. Go to Results tab. Expected: "Dread summary" meter card shows three bars (Layer A drag, Will save, Hope save) and a footer line "Layer B events this fight: N stuns, N breaks." Run with Hope 70 — Layer B footer should read "0 stuns, 0 breaks."

- [ ] **Step 3: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[v1] Sim: Results tab dread card shows Layer A drag + Layer B event counts"
```

---

## Task 7: Update `docs/combat-model.md` — Dread Section

**Files:**
- Modify: `docs/combat-model.md` (Dread mechanics section ~lines 170–176, Key Constants ~line 282)

- [ ] **Step 1: Replace the "Dread mechanics" section**

Find:
```markdown
### Dread mechanics

Dread suppresses party DPS. Counter: `effective dread = dread × (1 - min(1, willCut + hopeCut))`
where `willCut = min(1, captainWil / 100)` and `hopeCut = min(1, hopeAntidote / 100)`.
Will and Hope stack additively before capping. Captain will be grievous → no Will cut.
```

Replace:
```markdown
### Dread mechanics — Two-Layer Model

Dread operates on two separate layers. The old single-multiplier model is replaced.

**Effective dread and negation:**
```
effectiveDread = rawDread × (1 - min(1, willCut + hopeCut))
willCut  = min(1, captainWil / 100)   // 0 if Captain is grievous OR stunned
hopeCut  = min(1, hopeAntidote / 100)
```

**Layer A — Floor Drag (always present):**
```
layerADrag = min(1, effectiveDread × DREAD_VAR_COEF + rawDread × DREAD_FLOOR_COEF)
eff = raw × (1 - physAfter) × (1 - layerADrag)
```
The floor term (`rawDread × DREAD_FLOOR_COEF`) cannot be removed by any counter. Even a fully-prepped band fighting a high-dread encounter carries a faint drag. Armor and dread are separate multipliers — distinct code paths.

**Layer B — Action Denial (fires when negation is insufficient):**

Checked every `BREAK_CHECK_INTERVAL` ticks. Two severity gates:

| Gate | Condition | Effect |
|---|---|---|
| Stun | `willCut + hopeCut < T_TEMP` | Random standing member incapacitated for `STUN_TICKS`. Returns at their current HP. No wounds. |
| Break | `willCut + hopeCut < T_PERM` | Random standing member's will breaks — they flee. Gone for the fight. |

Targeting: random from standing, non-grievous, non-stunned, non-broken members. Captain has reduced targeting weight (`CAPTAIN_STUN_WEIGHT` for stuns, `CAPTAIN_BREAK_WEIGHT` for breaks). A stunned Captain's willCut = 0 that tick.

**Three zones:**

| Zone | Condition | Experience |
|---|---|---|
| Safe | negation ≥ T_TEMP | Layer A floor only. Faint drag. No action denial. |
| Danger | T_PERM ≤ negation < T_TEMP | Layer A full drag + stuns possible. Visible pressure. |
| Critical | negation < T_PERM | Layer A full drag + stuns + permanent breaks. Fight in serious danger. |
```

- [ ] **Step 2: Update the Key Constants table**

Find:
```markdown
```
morale            = round(30 + Vit × 16)
stat(L)           = start + grow × (L - 1)
PEN_SCALE         = 80
RESCUE_CAP        = 5      // Keeper rescues per fight
WARD_CAP          = 3      // Warden lethal-spike guards per fight
GRIEVOUS          = 5      // wounds → grievous
rescue burst      = 40 + Wil × 4  (× burstmul)
food heal (sink)  = overflow above max morale → reserve pool (cap RMAX=50); reserve absorbs spikes before morale
RMAX              = 50     // reserve pool cap per member
SHADOW_FLOOR      = 0.55   // base floor with no Radiance
SHADOW_RATE       = 0.0011 // per-tick stat drain per point of shadow severity
Fate insp coef    = 0.003  // per Fate point added to Inspiration base rate (cap 0.25)
Fate evade coef   = 0.004  // per Fate point chance to slip a spike entirely
JITTER            = 0.10   // per-tick DPS variance ±10%; U(−J, +J) multiplied onto effective DPS
tick              = 1 second
```
```

Replace:
```markdown
```
morale            = round(30 + Vit × 16)
stat(L)           = start + grow × (L - 1)
PEN_SCALE         = 80
RESCUE_CAP        = 5      // Keeper rescues per fight
WARD_CAP          = 3      // Warden lethal-spike guards per fight
GRIEVOUS          = 5      // wounds → grievous
rescue burst      = 40 + Wil × 4  (× burstmul)
food heal (sink)  = overflow above max morale → reserve pool (cap RMAX=50); reserve absorbs spikes before morale
RMAX              = 50     // reserve pool cap per member
SHADOW_FLOOR      = 0.55   // base floor with no Radiance
SHADOW_RATE       = 0.0011 // per-tick stat drain per point of shadow severity
Fate insp coef    = 0.003  // per Fate point added to Inspiration base rate (cap 0.25)
Fate evade coef   = 0.004  // per Fate point chance to slip a spike entirely
JITTER            = 0.10   // per-tick DPS variance ±10%; U(−J, +J) multiplied onto effective DPS
tick              = 1 second
// Dread two-layer model (all placeholder — validate in sim)
DREAD_VAR_COEF       = 0.008  // Layer A variable term coefficient
DREAD_FLOOR_COEF     = 0.003  // Layer A floor term coefficient (tied to rawDread — cannot be countered)
T_TEMP               = 0.65   // negation threshold below which Gate 1 stuns fire
T_PERM               = 0.35   // negation threshold below which Gate 2 breaks fire
STUN_TICKS           = 5      // duration of temporary incapacitation
BREAK_CHECK_INTERVAL = 30     // ticks between Layer B gate checks
CAPTAIN_STUN_WEIGHT  = 0.3×   // Captain's relative targeting weight in stun pool
CAPTAIN_BREAK_WEIGHT = 0.15×  // Captain's relative targeting weight in break pool
```
```

- [ ] **Step 3: Verify no stale references to `dreadAfter` remain in the doc**

```bash
grep -n "dreadAfter" docs/combat-model.md
```

Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add docs/combat-model.md
git commit -m "[v1] Docs: combat-model.md — dread two-layer model replaces single multiplier"
```
