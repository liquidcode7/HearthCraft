# Parked Topics

> Deferred design threads. Each is parked deliberately, not forgotten. Recover
> when the gating context exists.

---

## Cooking / Quality

- **Dynamic crafting** — goal locked ("more interesting than dump-and-wait — a
  decision with real depth"); framing locked (front-loaded, idle-safe, a matching
  problem between cook levers and combat demands). Menu on the table:
  method-as-trade-off, optional/substitute ingredient slots, hero emphasis,
  grimoire techniques. NO lever chosen. For now cooking stays
  pick-ingredients-follow-grimoire-and-wait. Revisit AFTER ingredient quality
  ships, so depth is designed on something real.

- **Process-as-quality-upgrade** — initial quality build has Process *preserve*
  grade (gather is sole source). A later-game mechanic may let Process *lift*
  grade. Reintroduces a ceiling-clamp problem (grade climbing at both gather and
  process). Phase-2 deferred per `ingredient_quality_spec.md`.

- **Qty-weighting at cook** — currently each recipe ingredient is one "voice"
  (hero=2, supports=1) regardless of quantity. Revisit only if a design reason
  appears.

---

## Discovery / Grimoires / Bosses

- **Lone-Lands poison decision** — is spider poison a NEW hazard (Venom +
  Antivenom counter — gives region bosses a genuine capability-unlock to drop,
  makes the Lone-Lands "the venom country", costs a new hazard + draught category
  + recipes + sim wiring) OR a disease/Hale reskin (cheap, no new mechanics, but
  Hale already exists so the region boss must drop something else)? Gates BOTH the
  Rhudaur men's grimoire contents AND the Mithlost spider miniboss's answer.

- **Starter-region boss grimoire contents** — what the Rhudaur Men / Drakeling /
  Huorn each drop. Pending each band's SECOND region design (a region boss drops
  the *next* region's toolkit, so we need to know the next region).

- **Return-vault creatures for Undermarch + Mithlost** — Greycloaks has the
  Barrow-wight. Each starter region should get one return vault (structural
  mirror). Creatures TBD.

- **Barrow-wight final reward** — the planted/forgotten grimoire's actual contents
  (rare apex recipe / Barrow-blade-flavored thing). Pin when the late-game economy
  is clearer.

- **Drakeling fire** — flat Heat hazard (buildable today) vs escalating ramp
  (build-after-engine).

---

## Combat Engine Dependencies (blocking some bosses)

The current encounter model runs flat single-stage stats with monotonically
decreasing resolve. These bosses are design-ready but NOT buildable until the V2
engine gains the noted behavior:

- **Self-heal race** (Rhudaur Men) — needs enemy resolve to RISE (self-heal).
- **Escalator** (Huorn) — needs time-varying stage pressure (drain/spike ramps).
- **Two-front / numeric potency** — needs draught potency as a NUMBER, not the
  current string flag.

Bosses using only {hazard exam, race, fragility/spike, endurance grind} are
buildable on today's engine.

---

## Infra (separate track)

- **Caddy reverse proxy + DNS for odysseus.tetrafuranose.com** — deferred,
  separate task.
