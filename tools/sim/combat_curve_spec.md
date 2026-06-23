# Combat Curve Redesign — Findings & Spec

**For:** Claude Code (implementation)
**From:** design/sim session (claude.ai)
**Reference implementation:** `curve_lab.js` (abstract Monte Carlo, runnable: `node curve_lab.js`)

> This is a **design proposal backed by sim evidence**, not an instruction to auto-rebuild.
> Wes decides which levers go in. Treat the numbers as *directional* — the lab is an
> abstract model, so constants do **not** transfer to the real combat model (see §5).

---

## 1. The problem, precisely

A pure survival fight is a **single sustain check** — "does food HP/s beat the enemy's drain?"
That is one threshold, so the win-rate is a **step function (cliff)**, not a curve. Two things
make it worse:

- **Positive feedback (the cascade):** when a member falls, their drain redistributes to the
  survivors, accelerating collapse. Positive feedback *self-sharpens* the cliff.
- **Tiers are close together:** the three food tiers sit within a tiny HP/s range, so they all
  flip across the same narrow band.

"Less cliffy" has a precise meaning: **widen the HP/s window between ~25% and ~90% win rate**,
so three distinct recipe tiers can sit on clearly different parts of the slope.

---

## 2. Two separate goals — do not conflate them

| Goal | What it means | What fixes it |
|------|---------------|---------------|
| **Texture / feel** | clutch "down a member but holding" fights instead of clean-win-or-wipe | cascade-flip OR member decoupling (cheap, big effect) |
| **Win-rate spread** | three tiers landing at ~25 / 60 / 90% | stacked variance sources **+ tier spacing** (no single fix) |

The texture fixes barely widen the win band; they open a metastable *casualty* middle. The
spread problem is mostly solved by **spacing the recipe tiers further apart in HP/s**.

---

## 3. The levers (math)

Let `n` = members currently alive (of 4), `D` = global drain, `H` = food HP/s.

**Lever 1 — feedback exponent β (cascade control).** One knob spans the whole spectrum:
```
drain_per_member = (D / 4) * (4 / n) ** beta
  beta = +1  -> drain = D/n      full redistribution  (positive feedback / cascade)  [CURRENT]
  beta =  0  -> drain = D/4      no redistribution    (neutral)
  beta <  0  -> survivors take LESS drain as members fall  (negative feedback / last-stand)
```
Tested at β = −0.6. Effect: clutch texture 2 → 61 points. Win-band: ~unchanged.

**Lever 2 — member decoupling.** Replace global drain + global spike-target with per-member
independent processes: `drain_i = D/4`; each living member rolls its own spike at rate
`1/(interval*4)`. Win = `(survivors >= k)`. Survival becomes Binomial(4, p) → smoother by
construction, and per-member food finally matters. Effect: clutch texture 2 → 61; small band widening.

**Lever 3 — softcap → sink.** Replace the 40%-efficiency overshoot penalty with a reserve buffer:
```
CURRENT: eff = (H <= 1.5*drain) ? H : 1.5*drain + 0.4*(H - 1.5*drain)
SINK:    eff = H;  overflow above MMAX -> reserve_i (cap RMAX);  spikes drain reserve first
```
Keeps higher tiers differentiated (no wasted overshoot) and converts spike *timing* into graded
outcomes. Weak alone; meaningful stacked with decoupling.

**Lever 4 — second axis (DPS race). ⚠ TRAP if deterministic.**
```
boss_hp -= sum over living of (dpsBase + dpsK * H);  win = killed before timer AND not wiped
```
A deterministic kill threshold is just a **second cliff** — proven (band stayed ~0.6 HP/s on every
topology). Only smooths if the DPS carries real variance (Inspiration procs, crit rolls) or is
**anti-correlated with sustain via a tradeoff**. Do not add a plain deterministic race.

**Lever 5 — multiplicative counters (the prep-rewarding one).**
```
Dread (heal suppression):  eff_heal = H * (1 - dread * (1 - hope))
Armor   (already in design): incoming_phys * (1 - physMit * (1 - potency_pierce))
```
**Additive counters (flat ward, warmth) are substitutable by raw sustain → weak, no real choice.**
**Multiplicative counters cannot be out-stacked by sustain → they create genuine prep decisions.**
Demo: enemy Dread 0.45 → "stack max HP/s, ignore Dread" wins **0%**; "spend a third of the budget to
cancel Dread" wins **99%**. This is the structural reason potency↔armor is a *good* pair — `physMitPct`
is already a multiplier; keep potency's pierce multiplicative too (reduce effective armor %, never flat-subtract).

---

## 4. Measured results (abstract lab; each config calibrated so FL2 ≈ 60% — comparing *spread*, not difficulty)

```
config                              FL1   FL2   FL3   clutch   20→80 band
A baseline    (beta=+1)              0%   59%  100%    2 pts    0.60 HP/s
B cascade-flip(beta=-0.6)            0%   61%  100%   61 pts    0.59 HP/s
C decoupled   (beta=0, indep spikes) 0%   61%  100%   61 pts    0.59 HP/s
D sink        (softcap->reserve)     0%   60%  100%    3 pts    0.59 HP/s
E combined    (flip+decouple+sink)   1%   57%   92%   57 pts    1.37 HP/s   <-- only band that widened
F two-axis    (DPS race, beta=+1)    0%   58%  100%    —        0.61 HP/s   <-- trap (second cliff)
G two-axis    (DPS race, combined)   0%   61%  100%    —        0.58 HP/s   <-- trap
```

**Tier-spacing payoff (smoothest config E):** the 25%→90% window opens from **0.50 HP/s** (current)
to **1.82 HP/s** (smoothed: 25% at 5.5, 60% at 6.1, 90% at 7.3). ~3.6× wider — enough that normal
recipe-strength gaps land the tiers at risky/confident/assured.

---

## 5. What transfers vs. what must be re-tuned

**Transfers (structural, model-independent):**
- The five levers above and their *shapes* (β form, decoupling, sink, multiplicative counters).
- The trap warning (no deterministic second axis).
- The substitutability principle (additive = weak, multiplicative = real choice).
- "Stack variance + space the tiers" as the recipe for a tunable curve.

**Does NOT transfer — re-derive against the real model:**
- Every constant: `D`, spike size/interval, boss resolve, `dpsK`, `RMAX`, β magnitude.
- The FL HP/s values — those come from the real `TIER_TABLE` / S-curve in `food_model.js`,
  not the lab's 5/6/7.5 placeholders.
- The win threshold `k` and what "win" means for a band (wipe vs lose-any-member) — design call.

---

## 6. Suggested repo touch-points (for when Wes approves specific levers)

- `tools/sim/run_sim.js` and `tools/sim/hearthcraft_fight_sim.html`: add a `beta` term to the
  per-member drain; add a per-member-independent mode; add a sink-reserve option in place of the softcap.
- **Armor/potency:** `run_sim.js` still uses the old stat-sum `PEN_COEF` model (line ~35) for armor
  penetration. That contradicts the locked "potency = single-source, food-only" direction. Replacing it
  with a multiplicative potency pierce (Lever 5) does double duty: implements the redesign *and* makes
  armor a non-substitutable counter.
- `docs/combat-model.md` / `docs/mechanics-math-reference.md`: document β, the sink, and the
  additive-vs-multiplicative rule once a direction is chosen.
- Recipe tiers: re-derive the HP/s spacing that lands 25/60/90 against the real `TIER_TABLE`.

---

## 7. Open decisions for Wes (consult before implementing — per standing rule)

1. Texture lever: **β<0**, **member decoupling**, or **both**? (decoupling also makes per-member food matter)
2. Keep the healing **softcap**, or move to the **sink/reserve**?
3. Win condition for a survival fight: **no-wipe** (≥1 alive) vs **flawless** (no member lost) vs **≥k alive**?
4. How many **threat↔counter pairs** should be live per encounter, and on which axis does Encounter 3 sit?
5. Tier spacing: confirm target window (~1.8 HP/s) and re-derive exact cooking-level targets.

---

*Reference: run `node curve_lab.js` to reproduce all of §4. Every lever is a toggle in that file.*
