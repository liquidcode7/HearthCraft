# Parked Topics

> Deferred design threads. Each is parked deliberately, not forgotten. Recover
> when the gating context exists.

---

## Cooking / Quality

- **Tuning tables** — gather distribution per level, gathering-aid uplift magnitude,
  cook-ceiling function, gradeStep magnitudes (s1..s4) for food/draughts/poultices.
  All currently placeholder in `QualityUtils.kt` and `food_model.js`, marked
  `TODO:TUNE`. Wes + sim to calibrate against encounter curve.

- **Gathering-aid acquisition** — the aid data shape and consumption hook are wired;
  the aid's recipe/acquisition source is not designed. Feeds into the discovery session.

- **Dynamic crafting** — goal locked ("more interesting than dump-and-wait");
  framing locked (front-loaded, idle-safe, matching problem). NO lever chosen yet.
  Revisit AFTER ingredient quality ships and is tuned on real data.

- **Process-as-quality-upgrade** — Process currently preserves grade. A later-game
  mechanic may let Process *lift* grade. Reintroduces ceiling-clamp problem. Deferred.

- **Qty-weighting at cook** — each ingredient is one voice (hero=2, supports=1)
  regardless of quantity. Revisit only if a design reason appears.

---

## Discovery / Grimoires / Bosses

- **Expedition mechanic** — minibosses are currently on the encounter list as
  optional fights. A future "expedition" system (time-gated, discoverable,
  lore/exposition delivery) would make them feel earned rather than listed.
  Parked until grimoire system is built; expeditions would tie naturally into
  lore delivery for grimoires too.

- **Starter-region boss grimoire contents** — what the Rhudaur Men / Drakeling /
  Huorn each drop. Pending each band's SECOND region design. The boss drops the
  *next* region's toolkit; contents can't be pinned without knowing the next region.

- **Basic vs grimoire recipe split** — data pass needed: which recipes are
  level-gated (auto-unlock at cookLevel) vs grimoire-gated (need a book)?
  Default: T1 food + entry draughts = level-gated; T2+ = grimoire-gated.
  Ambiguous cases need Wes review before grimoire code spec is written.

- **Return-vault creatures for Undermarch + Mithlost** — Greycloaks has the
  Barrow-wight. Each starter region gets one return vault (structural mirror).
  Candidates: dwarves — deep-dark Ered Luin hall-guardian behind Shadow or Heat/forge;
  elves — drowned-west / old-forest thing behind Shadow or Cold. TBD.

- **Barrow-wight final reward** — the planted/forgotten grimoire's actual contents
  (rare apex recipe / Barrow-blade-flavored thing). Pin when late-game economy is clearer.

- **Miniboss tricks for Dourhand and Large Spider** — currently both are endurance
  grind. Need distinct tricks. Dourhand: already armored (high physMit) so the
  puzzle is sustain + potency — works as a race variant. Large Spider: needs its
  own gimmick distinct from Wolf-Master (spike) and Dourhand. Candidates: fragility
  trap (web-slows targeting the fragile member), or a poison-flavored endurance
  that is mechanically just disease/Hale. TBD next design session.

---

## Combat Engine Dependencies (blocking some bosses)

The current encounter model runs flat single-stage stats with monotonically
decreasing resolve. These bosses are in `encounters.json` but their full tricks
are NOT active until the V2 engine gains the noted behavior:

- **Self-heal race** (Rhudaur Men) — needs enemy resolve to RISE (self-heal).
  Currently runs as a high-resolve wall fight.
- **Escalator** (Huorn) — needs time-varying stage pressure (drain/spike ramps).
  Currently runs as a high-drain endurance fight.
- **Two-front / numeric potency** — needs draught potency as a NUMBER, not the
  current string flag.

Bosses using only {hazard exam, race, fragility/spike, endurance grind} are
buildable on today's engine. Drakeling (flat Heat race) is fully functional now.

---

## Infra (separate track)

- **Caddy reverse proxy + DNS for odysseus.tetrafuranose.com** — deferred,
  separate task.
