# Draughts, Potency & Encounter 3 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace stat-based armor penetration with a party-wide draught potency system, then design and validate Encounter 3 (Goblin-town Gate, recLevel 5) against it.

**Architecture:** Potency moves from a derived stat sum (Hunter Agi × 0.90 dominant) to a single draught control value. Both the browser sim and headless runner carry parallel implementations of the pen formula — both must change. Encounter 3 is tuned in the headless runner then locked in JSON. Doc updates close the loop.

**Tech Stack:** Vanilla JS in a single HTML file (`hearthcraft_fight_sim.html`), Node.js headless runner (`run_sim.js`), JSON encounter files, Markdown docs.

## Global Constraints

- All sim changes stay in `tools/sim/` — no other directories touched
- `PEN_SCALE` must be the same constant value in both `hearthcraft_fight_sim.html` and `run_sim.js`
- Encounter 3 `physMitPct` must be 20–25 (mailed goblins, not plate)
- Encounter 3 `durationCap` = 1500 sec (matches existing encounters)
- Target: no potency draught → timeout loss at lv5 with FL4 food; entry potency draught (potency 45) → win
- Do not touch `docs/v1-plan.md` or any Kotlin source

---

## File Map

| File | What changes |
|---|---|
| `tools/sim/hearthcraft_fight_sim.html` | Remove `PEN_COEF` + `penBreakdown()`; add draught UI + `draughtPotency()`; retune `PEN_SCALE`; update formula + Live display + gnotes |
| `tools/sim/run_sim.js` | Remove `PEN_COEF`; add `--potency` flag; retune `PEN_SCALE`; update formula |
| `tools/sim/goblintown_gate.json` | New — locked Encounter 3 values |
| `docs/combat-model.md` | Update "Physical Mitigation and Penetration" section; add "Draughts" section; add Encounter 3 to "Tuned Encounters" |

---

## Task 1: Browser Sim — Draught UI + Formula

**Files:**
- Modify: `tools/sim/hearthcraft_fight_sim.html`

**Interfaces:**
- Produces: `draughtPotency()` → `number` (0–100), read by `dpsBreakdown()`

- [ ] **Step 1: Remove `PEN_COEF` and retune `PEN_SCALE`**

Find lines (around line 518–519):
```javascript
const PEN_COEF={warden:{stat:"mig",k:0.25},hunter:{stat:"agi",k:0.9},keeper:{stat:"agi",k:0.15},captain:{stat:"mig",k:0.2}};
const PEN_SCALE=110; // tuning: higher = armor harder to fully strip (retuned from validation)
```

Replace with:
```javascript
const PEN_SCALE=80; // armor penetration scale — draught potency / PEN_SCALE = armor strip fraction
```

- [ ] **Step 2: Remove `penBreakdown()` function**

Find and delete this entire function (around line 701–707):
```javascript
// party penetration, decomposed by member (each via their pen stat)
function penBreakdown(){
  let total=0, parts=[];
  ORDER.forEach(k=>{ if(M[k].grievous)return; const c=PEN_COEF[k]; if(!c)return;
    const contrib=M[k][c.stat]*c.k; total+=contrib; parts.push({who:k,name:M[k].tpl.name,stat:c.stat,val:contrib});});
  return {total,parts};
}
```

- [ ] **Step 3: Add `draughtPotency()` helper directly after the antidoteOn() function**

Find:
```javascript
function antidoteOn(a){const c=$("an_"+a);return c&&c.checked?(+$("al_"+a).value):0;}
```

Add immediately after:
```javascript
function draughtPotency(){return +($("draught_potency")||{value:0}).value||0;}
```

- [ ] **Step 4: Update `dpsBreakdown()` — replace pen with draught potency**

Find in `dpsBreakdown()`:
```javascript
  if(P.unkill)return {raw:0,physAfter:0,dreadAfter:0,eff:0,penSaved:0,willSaved:0,hopeSaved:0,pen:penBreakdown(),effBy:{warden:0,hunter:0,keeper:0,captain:0,captain_phys:0,captain_magic:0},physMitTotal:0,magicDmg:0,dawnBoost:0};
```

Replace with:
```javascript
  if(P.unkill)return {raw:0,physAfter:0,dreadAfter:0,eff:0,penSaved:0,willSaved:0,hopeSaved:0,potency:0,effBy:{warden:0,hunter:0,keeper:0,captain:0,captain_phys:0,captain_magic:0},physMitTotal:0,magicDmg:0,dawnBoost:0};
```

Then find:
```javascript
  // PHYSICAL: computed party penetration reduces armor
  const pen=penBreakdown();
  const physRaw=P.phys;
  const physAfter=Math.max(0,physRaw*(1-Math.min(1,pen.total/PEN_SCALE)));
```

Replace with:
```javascript
  // PHYSICAL: draught potency reduces armor
  const potency=draughtPotency();
  const physRaw=P.phys;
  const physAfter=Math.max(0,physRaw*(1-Math.min(1,potency/PEN_SCALE)));
```

Then find the return statement at the end of `dpsBreakdown()`:
```javascript
  return {raw,physRaw,physAfter,dreadRaw,dreadAfter,eff:Math.max(0,eff),
    penSaved:Math.max(0,eff-effNoPen), willSaved:Math.max(0,eff-effNoWill), hopeSaved:Math.max(0,eff-effNoHope),
    pen, effBy, physMitTotal:Math.max(0,physMitTotal), magicDmg:Math.max(0,magicDmg), dawnBoost};
```

Replace with:
```javascript
  return {raw,physRaw,physAfter,dreadRaw,dreadAfter,eff:Math.max(0,eff),
    penSaved:Math.max(0,eff-effNoPen), willSaved:Math.max(0,eff-effNoWill), hopeSaved:Math.max(0,eff-effNoHope),
    potency, effBy, physMitTotal:Math.max(0,physMitTotal), magicDmg:Math.max(0,magicDmg), dawnBoost};
```

- [ ] **Step 5: Update Live tab `g_pendetail` display**

Find:
```javascript
  if(showPhys){$("g_phys").textContent="−"+Math.round(bd.physAfter*100)+"%";$("g_pensave").textContent="+"+Math.round(bd.penSaved)+" dps";
    $("g_pendetail").innerHTML="&nbsp;&nbsp;"+bd.pen.parts.filter(p=>p.val>=1).map(p=>`${p.name} +${Math.round(p.val)} (${STAT_LABEL[p.stat]})`).join(", ")+` &#8594; pen ${Math.round(bd.pen.total)}`;}
```

Replace with:
```javascript
  if(showPhys){$("g_phys").textContent="−"+Math.round(bd.physAfter*100)+"%";$("g_pensave").textContent="+"+Math.round(bd.penSaved)+" dps";
    $("g_pendetail").innerHTML=`&nbsp;&nbsp;Draught potency ${Math.round(bd.potency||0)} / ${PEN_SCALE} &#8594; strips ${Math.round((1-Math.min(1,(bd.potency||0)/PEN_SCALE))*100)}% of armor`;}
```

- [ ] **Step 6: Update armor section gnote**

Find:
```html
          <div class="gnote">Armor eats martial damage. Penetration is computed from the PARTY's stats (Agility for strikers, Might for bruisers) &#8212; not a buff. The Live/Results tabs decompose exactly who punches through and by how much.</div>
```

Replace with:
```html
          <div class="gnote">Armor eats martial damage. Penetration comes from the party&#8217;s draught potency (set in Provisioning below) &#8212; not from character stats. Magic damage (Keeper, Captain&#8217;s Will half) bypasses armor entirely.</div>
```

- [ ] **Step 7: Update `updEffects()` armor effect text**

Find:
```javascript
  $("e_phys").textContent=+$("phys").value>0?`armor cut by the party's computed penetration (see Live tab)`:``;
```

Replace with:
```javascript
  $("e_phys").textContent=+$("phys").value>0?`armor cut by party draught potency (see Provisioning &#8212; draught below)`:``;
```

- [ ] **Step 8: Add draught potency UI section to Setup tab**

Find the "Provisioning — antidotes" section:
```html
        <div class="ctl-group">
          <h3 onclick="this.parentElement.classList.toggle('open')">Provisioning &#8212; antidotes <span>&#9432;</span></h3>
          <div class="gnote">Check the hazard answers you provisioned, and set their strength (stands in for food tier). Each answers its matching status.</div>
          <div id="antidote_rows"></div>
        </div>
```

Insert this new section immediately BEFORE that block:
```html
        <div class="ctl-group open">
          <h3 onclick="this.parentElement.classList.toggle('open')">Provisioning &#8212; draught <span>&#9432;</span></h3>
          <div class="gnote">One draught for the whole party. Potency strips armor from physical damage &#8212; the only way to crack armored encounters. Leave at 0 if no potency draught equipped. Entry-tier potency draught: ~45. Mid-tier: ~65.</div>
          <div class="ctl"><label>Party draught potency <span class="val" id="v_draught_potency">0</span></label><input type="range" id="draught_potency" min="0" max="100" value="0" oninput="document.getElementById('v_draught_potency').textContent=this.value;if(!running)refreshRead();"></div>
        </div>
```

Also update the antidotes section gnote to say "draught antidotes" — find:
```html
          <div class="gnote">Check the hazard answers you provisioned, and set their strength (stands in for food tier). Each answers its matching status.</div>
```

Replace with:
```html
          <div class="gnote">Check the draught antidotes you provisioned. Each answers its matching hazard status. Set strength to represent the draught&#8217;s tier.</div>
```

- [ ] **Step 9: Wire draught_potency into `refreshRead()` if needed**

Search for `refreshRead` in the file. If it reads UI controls to build `P`, check whether `draught_potency` needs to be included. The `draughtPotency()` function reads the DOM directly each tick, so no change to `P` is needed — but confirm `draught_potency` slider changes trigger a live refresh by verifying the `oninput` handler calls `refreshRead()` (already included in Step 8 above).

- [ ] **Step 10: Open in browser and verify**

Open `tools/sim/hearthcraft_fight_sim.html` in a browser.

Expected:
- "Provisioning — draught" section appears in Setup tab with a potency slider (0–100)
- Set physMit to 30%, draught potency to 0 → Live tab shows "− 30% armor" and "Draught potency 0 / 80 → strips 100% of armor" — **wait, this should be 0%**. Confirm: `1 - min(1, 0/80) = 1 - 0 = 1`, so `physAfter = 0.30 × 1.0 = 0.30`. Correct — full armor applies.
- Set draught potency to 80 → `physAfter = 0.30 × (1 - 1.0) = 0`. Full strip. Live tab should show 0% armor reduction.
- Set draught potency to 40 → `physAfter = 0.30 × (1 - 0.5) = 0.15`. Live tab shows −15%.
- No console errors.

- [ ] **Step 11: Commit**

```bash
git add tools/sim/hearthcraft_fight_sim.html
git commit -m "[v1] Sim: replace stat-based armor pen with party draught potency"
```

---

## Task 2: Headless Runner — Mirror Draught System

**Files:**
- Modify: `tools/sim/run_sim.js`

**Interfaces:**
- Consumes: `--potency N` CLI flag (default 0)
- `PEN_SCALE` must match browser sim (80)

- [ ] **Step 1: Remove `PEN_COEF` and retune `PEN_SCALE`**

Find (around line 35–36):
```javascript
const PEN_COEF     = { warden:{stat:"mig",k:0.25}, hunter:{stat:"agi",k:0.90}, keeper:{stat:"agi",k:0.15}, captain:{stat:"mig",k:0.20} };
const PEN_SCALE    = 110;
```

Replace with:
```javascript
const PEN_SCALE    = 80;  // must match hearthcraft_fight_sim.html
```

- [ ] **Step 2: Add `--potency` to cfg parsing**

Find the `cfg` object build (around line 119 where `phys` is set):
```javascript
    phys:      num("--phys",    0) / 100,
```

Add immediately after:
```javascript
    potency:   num("--potency", 0),
```

- [ ] **Step 3: Replace stat-based pen calculation with draught potency**

Find (around line 188–191):
```javascript
    // penetration
    let penTotal = 0;
    ORDER.forEach(k => { if (M[k].grievous) return; const c=PEN_COEF[k]; penTotal += M[k][c.stat]*c.k; });
    const physAfter = Math.max(0, cfg.phys * (1 - Math.min(1, penTotal/PEN_SCALE)));
```

Replace with:
```javascript
    // penetration via draught potency
    const physAfter = Math.max(0, cfg.phys * (1 - Math.min(1, cfg.potency/PEN_SCALE)));
```

- [ ] **Step 4: Update the dps return statement**

Find (around line 197):
```javascript
    return { raw, eff, physAfter, dreadAfter, penTotal };
```

Replace with:
```javascript
    return { raw, eff, physAfter, dreadAfter, potency: cfg.potency };
```

- [ ] **Step 5: Update summary output**

Find (around line 415):
```javascript
  if (cfg.phys>0) console.log(`Armor ${Math.round(cfg.phys*100)}%`);
```

Replace with:
```javascript
  if (cfg.phys>0) console.log(`Armor ${Math.round(cfg.phys*100)}% | Draught potency ${cfg.potency} / ${PEN_SCALE}`);
```

- [ ] **Step 6: Verify headless runner works**

Run a quick sanity check — no physMit, should produce the same output as before (pen was 0 effect anyway at phys=0):

```bash
node tools/sim/run_sim.js --level 5 --runs 100 --boss 50000 --drain 12 --spike 75 --spikeiv 13 --phys 0
```

Expected: runs complete, no crash, win rate output shown.

Then verify potency flag works:
```bash
node tools/sim/run_sim.js --level 5 --runs 100 --boss 50000 --drain 12 --spike 75 --spikeiv 13 --phys 22 --potency 0
```

Expected: summary line shows `Armor 22% | Draught potency 0 / 80`. Win rate lower than without armor.

```bash
node tools/sim/run_sim.js --level 5 --runs 100 --boss 50000 --drain 12 --spike 75 --spikeiv 13 --phys 22 --potency 45
```

Expected: win rate higher than with potency 0.

- [ ] **Step 7: Commit**

```bash
git add tools/sim/run_sim.js
git commit -m "[v1] Sim: headless runner — draught potency replaces stat pen"
```

---

## Task 3: Design and Validate Encounter 3

**Goal:** Find the resolve value where lv5 + FL4 food + no potency draught = consistent timeout loss, and lv5 + FL4 food + potency 45 = consistent win (~50–70%). Lock the numbers.

**Files:**
- Create: `tools/sim/goblintown_gate.json`

### Starting parameters

```
recLevel:    5
physMitPct:  22
drain:       20
spike:       60
spikeiv:     14
durationCap: 1500
resolve:     72000  ← starting point, tune this
```

### Tuning protocol

- [ ] **Step 1: Establish the no-potency baseline**

Run 5000 trials, no potency draught, lv5, FL4 food (role-matched recipes):

```bash
node tools/sim/run_sim.js \
  --level 5 --runs 5000 \
  --boss 72000 --drain 20 --spike 60 --spikeiv 14 \
  --phys 22 --potency 0 \
  --duration 1500 \
  --recipes wanderers_supper,wanderers_supper,contemplative_tea,rangers_fare \
  --rlevel 4
```

Expected: win rate 0–15%. If win rate is above 20%, increase resolve by 5000 and re-run. If win rate is 0% and the verbose output shows the party is dying (not timing out), reduce drain slightly. The target failure mode is **timeout, not wipe** — the party should be alive when the clock expires.

Check verbose output on a sample run to confirm:
```bash
node tools/sim/run_sim.js \
  --level 5 --runs 1 --verbose \
  --boss 72000 --drain 20 --spike 60 --spikeiv 14 \
  --phys 22 --potency 0 \
  --duration 1500 \
  --recipes wanderers_supper,wanderers_supper,contemplative_tea,rangers_fare \
  --rlevel 4
```

Expected in output: fight ends at ~1500s with boss still alive (not at full HP — should be low, say under 10%).

- [ ] **Step 2: Validate entry potency draught (potency 45)**

Using the locked resolve from Step 1:

```bash
node tools/sim/run_sim.js \
  --level 5 --runs 5000 \
  --boss <LOCKED_RESOLVE> --drain 20 --spike 60 --spikeiv 14 \
  --phys 22 --potency 45 \
  --duration 1500 \
  --recipes wanderers_supper,wanderers_supper,contemplative_tea,rangers_fare \
  --rlevel 4
```

Expected: win rate 45–70%. If below 45%, either lower resolve by 3000 and re-check Step 1, or raise potency ceiling slightly (to 50). The goal: entry draught feels like it makes a real difference, not a guaranteed win.

- [ ] **Step 3: Lock values and write encounter JSON**

Once both targets are met, write `tools/sim/goblintown_gate.json`:

```json
{
  "id": "goblintown_gate",
  "name": "Goblin-town Gate",
  "region": "Misty Mountains",
  "tier": "Rabble",
  "recLevel": 5,
  "resupply": "none",
  "rewards": "Goblin-forged scraps, mountain ore, coin",
  "flavorIntro": "The passage narrows here. Mailed sentinels hold the gate — not many, but their armour turns your blades. You need more than strength. You need the right preparation.",
  "designNote": "Rung 1 encounter — teaches potency. Without a potency draught the party cannot drain resolve fast enough despite surviving fine. physMit is deliberately modest (22%) so the lesson lands: even light armor walls you without penetration.",
  "stages": [
    {
      "stageId": "gate",
      "label": "The Mailed Guard",
      "type": "attrition",
      "objective": "kill",
      "durationSec": 1500,
      "resolve": <LOCKED_RESOLVE>,
      "drain": 20,
      "spike": 60,
      "spikeIntervalSec": 14,
      "physMitPct": 22,
      "dread": 0,
      "shadow": 0,
      "disease": 0,
      "cold": 0,
      "heat": 0,
      "wakefulness": 0,
      "stageFlavor": "The goblins hold their ground. Their mail deflects every blow that would have ended them in the Chetwood."
    }
  ]
}
```

Replace `<LOCKED_RESOLVE>` with the value found in Step 1.

- [ ] **Step 4: Commit**

```bash
git add tools/sim/goblintown_gate.json
git commit -m "[v1] Encounter 3: Goblin-town Gate locked — recLevel 5, physMit 22%, potency lesson"
```

---

## Task 4: Update Docs

**Files:**
- Modify: `docs/combat-model.md`

- [ ] **Step 1: Replace the "Physical Mitigation and Penetration" section**

Find:
```markdown
## Physical Mitigation and Penetration

**effArmor** = `physMit × (1 - min(1, partyPen / PEN_SCALE))`

`PEN_SCALE = 110`. Penetration is not a buff — it is computed from party stats:

| Member | Pen stat | Coefficient |
|---|---|---|
| Warden | Mig | 0.25 |
| Hunter | Agi | 0.90 (dominant) |
| Keeper | Agi | 0.15 |
| Captain | Mig | 0.20 |

Total penetration = Σ (standing, non-grievous) `penStat × coef`. Higher Agility
from food raises the Hunter's contribution and is the primary armor-counter.
```

Replace with:
```markdown
## Physical Mitigation and Penetration

**effArmor** = `physMit × (1 - min(1, draughtPotency / PEN_SCALE))`

`PEN_SCALE = 80`. Penetration comes from the party's **draught** — a single
party-wide choice, not from character stats. Magic damage (Keeper, Captain's
Will half) bypasses armor entirely and is unaffected by physMit.

`draughtPotency` = the potency value of the equipped draught (0 if none). Entry-tier
potency draughts carry ~45 potency. Mid-tier ~65. No draught → full armor applies.

Stat-based penetration coefficients (the old PEN_COEF system) are removed.
```

- [ ] **Step 2: Add "Draughts" section after Food/Stats/Hazards**

After the "### Six hazards, six antidotes" subsection, add:

```markdown
### Draughts (party-wide provisioning)

One draught choice per encounter, applied to all party members. Draughts carry
**potency** and/or **antidote** effects — distinct from food, which handles stats
and HP/s only.

- **Low-tier draughts:** one effect only (potency OR one antidote)
- **High-tier draughts:** up to two effects; secondary effect weaker than a
  dedicated single-effect draught. No draught ever carries three effects.

Antidotes are draught properties. Food no longer carries antidote effects.

In regions with both armor and a hazard, the player chooses: potency draught or
antidote draught. High-tier draughts can cover both at reduced efficiency.
```

- [ ] **Step 3: Add Encounter 3 to "Tuned Encounters" section**

After the Wolves in the Chetwood entry, add:

```markdown
### Goblin-town Gate — recLevel 5
`resolve <LOCKED> · drain 20 · spike 60 · spikeInterval 14 · physMit 22% · no statuses · durationCap 1500`

First armored encounter. Rung 1 — teaches potency. Drain is moderate; the party
survives without difficulty. The failure mode is **timeout**: without a potency
draught the resolve pool does not die in time (boss near-dead at clock expiry).
Validated at band Lv5, 5000 runs, FL4 role-matched food:
no potency: <WIN%>%, entry potency draught (45): <WIN%>%.
```

Replace `<LOCKED>` and `<WIN%>` placeholders with the actual values from Task 3.

- [ ] **Step 4: Update `docs/design.md` — Combat Roles table**

In `docs/design.md`, find the Combat Roles table. The Hunter row currently reads:

```
| Hunter (DPS) | Red `#c0392b` | Physical (armor-affected) | Agility + Might | Primary armor penetrator |
```

Replace the Notes cell:
```
| Hunter (DPS) | Red `#c0392b` | Physical (armor-affected) | Agility + Might | Dominant physical DPS; armor countered by party draught potency |
```

- [ ] **Step 5: Commit**

```bash
git add docs/combat-model.md docs/design.md
git commit -m "[v1] Docs: draught system + Encounter 3 added to combat-model.md; design.md Hunter note updated"
```
