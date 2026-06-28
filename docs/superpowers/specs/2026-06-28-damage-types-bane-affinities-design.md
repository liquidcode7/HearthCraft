# Damage Types & Bane Affinities — Design Spec

> Design agreed with Wes. Partially specified — incoming damage types and bane
> multiplier implementation deferred pending further design sessions.

---

## Summary

Two related concepts, at different levels of design maturity:

1. **Damage Types** (ready) — outgoing DPS is split into physical vs. magic channels. Magic damage has a named type drawn from a shared taxonomy. The specific type is determined by the *source* (character's innate nature in V1; weapon-driven in V2). Armor only applies to physical.

2. **Bane Affinities** (V2 — weapon-gated) — legendary weapons carry a damage type. When a weapon's type matches an enemy's category vulnerability, a bane multiplier fires. No bane can fire without a weapon.

3. **Incoming Damage Types** (design pending) — placeholder only. Enemy damage has types too (shadow, wraith damage, etc.); the mitigation model is not yet designed.

---

## Damage Type Taxonomy

Magic damage can be any of the following named types. These are not exhaustive — the list can grow with new content. The same taxonomy applies to both player-side magic and (eventually) enemy-side damage.

| Type | Lore character |
|---|---|
| **Beleriand** | The ancient enmity — elvish and dwarven power forged in the First Age wars against Morgoth |
| **Mormegil** | The doom of the Black Sword; Gurthang's willing stroke against dragon-kind |
| **Light** | Grace of the Valar; the fire of Aman and Elbereth's starlight |
| **Shadow** | The Unlight of Ungoliant; the darkness that unmakes light *(appears on enemy side; TBD on player side)* |
| **Westernesse** | The strength of Númenorean heritage; ancient craftsmanship and the lineage of kings |
| **Wraith** | *(design pending — appears on enemy side; TBD on player side)* |

Physical damage is not in this list. Physical is the absence of a magic type — it is steel, strength, and skill, and it is what armor is built to stop.

---

## Outgoing Damage Types (V1)

### Physical vs. magic

Might and Agility drive physical damage. Will drives magic damage — but *what kind* of magic depends on the character and their weapon.

| Source | Channel | Armor? |
|---|---|---|
| Might (Warden, Hunter, Captain) | Physical | Yes — `physMit` applies |
| Agility (Hunter) | Physical | Yes |
| Will (Keeper) | Magic — **Light** (V1 default) | No |
| Will (Captain) | Magic — **Westernesse** (V1 default) | No |

In V1, with no weapons, the Keeper's magic is Light and the Captain's is Westernesse because of who they are — healer and captain of Númenorean lineage respectively. These are not locked facts; a weapon with a different type would change the character's magic output type.

### DPS formulas (unchanged, type labels added)

- **Warden:** `Mig × 0.5` — Physical
- **Hunter (Agi build):** `Agi + Mig × 0.4` — Physical
- **Hunter (Mig build):** `Mig + Agi × 0.4` — Physical
- **Keeper:** `Wil × 0.9` — Magic / **Light** *(zero on any tick spent on a rescue)*
- **Captain:** `Mig × 0.3` (Physical) + `Wil × 0.2` (Magic / **Westernesse**)

### Armor application (revised)

Physical sub-totals from all active members are summed first; armor is applied. Magic damage (all types, all members) is summed separately and added after:

```
physDps     = Σ physical contributions from all active members
magicDps    = Σ magic contributions from all active members (any type)
effArmor    = physMit × (1 - min(1, draughtPotency / PEN_SCALE))
effectiveDps = physDps × (1 - effArmor) + magicDps
```

This replaces the current single-`raw` path in `dpsBreakdown()`.

---

## Bane Affinities (V2 — weapon-gated)

### Concept

Enemy encounters carry category tags that describe what they are. Legendary weapons carry a magic damage type. When a character is dealing magic damage of a type that the enemy is vulnerable to, a bane multiplier fires on that character's damage contribution.

The bane is the *weapon's* property meeting the *enemy's* vulnerability — not a role perk.

### Enemy categories and their vulnerabilities

| Enemy category | Vulnerable to | Notes |
|---|---|---|
| `orc` | Beleriand | Orcs, Uruk-hai, goblins — ancient enemies of the First Age races |
| `dragon` | Mormegil | Cold-drakes and fire-drakes; Glaurung, Smaug |
| `shadow` | Light | Shadow-creatures; Balrog, great spiders, Ungoliant-spawn |
| `wraith` | Westernesse | Ringwraiths, Barrow-wights, Dead Men of Dunharrow |

Other categories (`beast`, `huorn`, etc.) have no defined bane vulnerability yet. They may receive one in future design.

### How it fires

A bane multiplier applies when the member's weapon damage type matches the enemy's vulnerability:

```
if member.weapon.type == enemy.vulnerableTo:
    memberEffDps *= BANE_MULTIPLIER
```

Applies after armor reduction. A member with a Beleriand weapon fighting an `orc`-tagged encounter gets the multiplier on their full effective DPS contribution. A member with a Westernesse weapon fighting orcs gets nothing extra.

`BANE_MULTIPLIER = 1.15` (placeholder — validate in sim when bane weapons are designed)

### V2 scope

No bane fires without a weapon. V1 has no weapons — the system does not exist in V1 gameplay. Encounter category tags can be authored now for forward-compatibility.

---

## Incoming Damage Types — DESIGN PENDING

> **Placeholder.** This section needs a dedicated design session before it is specified.

Enemies deal damage too, and some of it is clearly typed — a Ringwraith's Black Breath is not the same as a goblin's spear. The taxonomy above includes Shadow and Wraith as types that appear on the enemy side.

Open questions:
- Is incoming shadow/wraith damage a separate per-tick channel alongside drain, or is it attached to spike events?
- What is the mitigation model? (Will for shadow? Westernesse affinity for wraith damage?)
- How does incoming shadow damage interact with the Shadow *status effect* (which drains Will + Fate over time)? Same thing expressed differently, or two distinct mechanics that stack?
- Can player characters ever deal Shadow or Wraith damage? (Corrupted weapons? The Morgul blade?) Or are those permanently enemy-side types?

Do not implement incoming typed damage until these are answered.

---

## sim implementation scope (V1 — damage type split only)

Changes to `tools/sim/run_sim.js`:

1. **`dpsBreakdown()`** — track `physDps` and `magicDps` separately per member; apply armor only to `physDps` total; sum both after. Return sub-totals for logging.
2. **No bane multiplier** — enemy category tags can be parsed/stored but nothing fires in V1.
3. **No incoming typed damage** — pending design.

Changes to `docs/combat-model.md`:

1. Update DPS formulas table with Physical / Magic type labels and V1 defaults.
2. Add a "Damage Type Taxonomy" section with the full type list.
3. Add "Armor application" section with the revised physDps/magicDps split formula.
4. Add a "Bane Affinities" section — concept, enemy vulnerability table, V2 weapon-gating.
5. Add a stub "Incoming Damage Types" section marked design-pending.
6. Update the constants block with `BANE_MULTIPLIER = 1.15` (V2 placeholder).

---

## What this is NOT

- No innate role banes — banes come from weapons.
- No mechanical bonus from damage type alone in V1 — magic bypasses armor, that is all.
- No incoming typed damage yet — pending design session.
- No player-facing damage type UI in V1.
