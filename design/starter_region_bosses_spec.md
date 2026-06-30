# Starter Region Bosses — Design Spec

**Date:** 2026-06-29 (updated 2026-06-30)
**Status:** Structure locked; miniboss tricks for Dourhand/Spider parked; grimoire contents parked.
**Companion:** `discovery_grimoire_spec.md`, `ingredient_quality_spec.md`.

---

## 1. The Five-Slot Structure (revised)

All three starter regions share an **identical five-slot structure** on the
critical path. The **structure is mirrored; the flavor and tricks are band-distinct.**

### The five slots (every starter region)

1. **Ladder fight 1** (recLv1) — intro, no malady, no gimmick.
2. **Ladder fight 2** (recLv3) — harder, no malady, no gimmick.
3. **Miniboss** (recLv4) — tricky fight, less raw power than a region boss.
   Drops **XP reward** for now. Potency draught recipe is **level-gated**
   (auto-unlocks at cookLevel 5) — no grimoire needed. The miniboss is a
   discoverable encounter; a future **expedition mechanic** will make it feel
   earned rather than listed. Parked — see `docs/parked_topics.md`.
4. **Malady fight** (recLv5) — first malady ever = **armor** (all three bands),
   answered by the band's potency draught (level-gated, should be available by now).
5. **Region boss** (recLv6) — self-contained trick; drops the **next region's**
   toolkit grimoire (contents parked — needs second-region design).
6. **Return vault** — early-visible, late-beatable, planted reward.
   Greycloaks: Barrow-wight (Cold+Dread). Undermarch/Mithlost: TBD.

> Note: the original spec had six slots with the miniboss before the malady fight
> and a separate malady slot. This is now collapsed: miniboss (slot 3) → malady
> fight (slot 4) → region boss (slot 5). The malady fight is the recLv5 goblin
> encounter that already exists in `encounters.json`.

---

## 2. Band Fills

| Slot | Greycloaks (Men, Bree-land) | Undermarch (Dwarves, Ered Luin) | Mithlost (Elves, Celondim) |
|---|---|---|---|
| Ladder 1 (recLv1) | Neekerbreekers | Cave Bats | Midges |
| Ladder 2 (recLv3) | Wolves of the Chetwood | Mountain Wolves | Wargs |
| **Miniboss** (recLv4) | **Wolf-Master** — spike trap | **Dourhand dwarves** — armor race | **Large Spider** — fragility trap |
| Malady (recLv5) | Armored Goblin Raiders — armor | Goblin Assault — armor | Goblin Scouts — armor |
| **Region boss** (recLv6) | **Rhudaur Men** — self-heal race* | **Drakeling** — dragon-fire race | **Huorn** — escalator* |
| **Return vault** | **Barrow-wight** — Cold+Dread | *TBD* | *TBD* |

*engine-blocked — runs as a high-resolve/high-drain wall fight until V2 engine.

### Malady unification decision (2026-06-30)
All three bands face **armor** as their first malady (recLv5 goblins). Simple,
consistent, and avoids the complexity of band-specific hazard counters at the
starter level. Poison for the Mithlost spider was considered; decided it is
just a flavor of disease (Hale reskin) and the spider miniboss has no malady
at all (it is pre-encounter-4). The Lone-Lands poison question is likewise
resolved: poison = disease/Hale, no new Venom hazard.


## 3. Miniboss Detail

### 3.1 Wolf-Master (Greycloaks) — spike trap
- **Trick:** high, frequent, **random-targeted** spikes. Per the combat rule,
  spikes hit one random standing member — so the pack will, by the dice,
  eventually land on the fragile Hunter/Keeper.
- **Answer:** **even Vitality food across the whole band** (Vit → morale pool,
  `morale = 30 + Vit×16`). Cannot be solved by feeding only the DPS — the random
  target means *everyone* needs an HP floor. Inverts the normal "feed each member
  their primary stat" habit — that's the puzzle.
- **Reward:** XP dump (no grimoire).
- **Tuning make-or-break (Wes/sim):** spike must **one-shot a fragile member on
  wrong/base food** but **leave them low-but-alive on heavy Vit food**. Too high =
  unfair random death; too low = no puzzle.
- **Engine:** buildable today (spike, random targeting, Vit→morale all exist).
- **The cringe-nod is intentional** (LotRO "Wolf-Master"). The Master himself is
  the resolve pool; the pack is atmosphere.

### 3.2 Dourhand dwarves (Undermarch) — armor
- **Trick:** an **armored** Dourhand war-band (canon Ered Luin antagonists).
  Physical mitigation wall — you can't chip them down without penetration.
- **Answer:** potency draught.
- **Reward:** XP dump.
- **Engine:** buildable today (physMit exists).

### 3.3 Large Spider (Mithlost) — poison
- **Trick:** **poison** — depends on the parked poison decision (new Venom hazard
  vs disease/Hale reskin). This miniboss is a **second data point** for that
  decision: the elf line may be where poison is *introduced*.
- **Answer:** the poison counter (TBD with the poison decision).
- **Reward:** XP dump.
- **Engine:** buildable today **if** poison = disease/Hale; **needs a new hazard**
  if Venom is its own thing.
- **BLOCKED on the poison decision** — see Parked.

---

## 4. Region Boss Detail

### 4.1 Rhudaur Men (Greycloaks) — self-heal race
- **Identity:** a Rhudaur hillman war-band, **blood-magic** led (Witch-realm
  hill-men ranging west to the Bree-land border). The gate east into the
  Lone-Lands.
- **Trick:** **self-heal race** — the blood-speaker **drains the band to refill the
  war-band's resolve pool.** Under-provisioning doesn't just slow your kill, it
  *extends the fight by feeding him.*
- **Answer:** out-damage the self-heal **and** deny the drain (sustain to stop him
  feeding + output to outpace the heal).
- **Grimoire drop:** the **Lone-Lands toolkit** — **PARKED**, pending the poison
  decision (what the Lone-Lands' first malady is).
- **Engine:** **build-after-engine** — self-heal (resolve rising) is new behavior.

### 4.2 Drakeling (Undermarch) — dragon-fire race
- **Identity:** a young dragon-kin come to **steal gold** (dwarven-greed / Smaug
  echo). The gate out of Ered Luin.
- **Trick:** the **race** — armored scales + fat resolve + time pressure; likely a
  **Heat** (dragonfire) angle. Burn it down before it torches the band.
- **Answer:** potency (cut the scales) + output (stat food) + possibly Heat-ease
  if dragonfire is a hazard.
- **Grimoire drop:** the next-region toolkit — **PARKED**.
- **Engine:** a **pure race is buildable today**; if dragonfire is an *escalating
  Heat hazard*, that part is build-after-engine. Decide whether the drake's fire is
  a flat hazard (buildable) or a ramp (deferred).

### 4.3 Huorn (Mithlost) — escalator
- **Identity:** a half-woken tree of the old western woods near Celondim. Slow,
  ancient, patient — closes in and crushes. The gate out of the elf starter zone.
- **Trick:** **escalator** — starts slow; the longer the fight runs, the worse the
  pressure (roots creep, drain ramps, walls close). Easy early, lethal if you're
  too slow.
- **Answer:** a **race against a rising clock** — enough output to fell it before
  the entanglement matures, enough sustain to weather the ramp if slow.
- **Grimoire drop:** the next-region toolkit — **PARKED**.
- **Engine:** **build-after-engine** — escalator needs time-varying stage pressure.
  (Fallback if a today-buildable elf boss is wanted: **endurance grind** — fat
  resolve + heavy flat drain, no escalation — runs on the current single-stage
  model. Escalator is the chosen design; endurance is the buildable-now fallback.)

---

## 5. Return Vault Detail

### 5.1 Barrow-wight (Greycloaks) — defined
- **Identity:** a Westernesse-undead in the Barrow-downs on Bree-land's edge.
  Early-visible, **un-passable on first visit.**
- **Trick:** imposes **Cold + Dread** — counters the player has not earned at first
  encounter. Player retreats; returns later (after gaining warmth + Dread resist
  from other regions) to win.
- **Reward:** the **planted/forgotten grimoire** (see `discovery_grimoire_spec.md`
  §6): an inert book added to inventory on the first visit, which **wakes up** on
  defeat. Justifies a big/rare reward — earned across the whole arc, not one fight.
- **Engine:** Cold + Dread hazards exist (buildable today, given those hazard
  fields); the *planted-reward* unlock-on-defeat is the new wiring.

### 5.2 Undermarch & Mithlost return vaults — PARKED
- Each starter region should get one return vault (structural mirror). Creatures
  TBD. Candidates: dwarves — a deep-dark Ered Luin hall-guardian behind a hazard
  they lack early (Shadow? Heat/forge?); elves — a drowned-west / old-forest thing
  behind Shadow or Cold. Park.

---

## Parked

- **Lone-Lands poison decision** — gates the Rhudaur men's grimoire AND the elf
  spider miniboss's answer. New Venom hazard vs disease/Hale reskin.
- **All three region bosses' grimoire contents** — pending each band's second
  region.
- **Undermarch + Mithlost return-vault creatures.**
- **Drakeling fire** — flat Heat hazard (buildable) vs escalating ramp (deferred).
- **Engine work** for: self-heal (Rhudaur), escalator (Huorn), numeric draught
  potency (two-front trickie generally).
