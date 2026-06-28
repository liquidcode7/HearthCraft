# Damage Types & Bane Affinities — Design Spec

> Design agreed with Wes. Partially specified — shadow incoming damage and bane
> multiplier implementation deferred pending further design sessions.

---

## Summary

Two related concepts, at different levels of design maturity:

1. **Damage Types** (ready) — outgoing DPS is split into physical vs. named affinity channels. The affinity types (Light, Westernesse) are inherent to the *nature of each role's magic damage*, not role-level bane unlocks. Armor only applies to physical.

2. **Bane Affinities** (V2 — weapon-gated) — when characters acquire named legendary weapons, those weapons carry bane affinities that multiply damage against matching enemy categories. No innate role banes exist. The encounter enemy-category tag system supports this eventually.

3. **Incoming Shadow Damage** (design pending) — placeholder only. More design needed before specifying.

---

## Outgoing Damage Types

### The Rule

Might and Agility are physical by nature. Will is non-physical — its character depends on whose Will it is:

| Source | Damage type | Armor? |
|---|---|---|
| Might (Warden, Hunter, Captain) | Physical | Yes — `physMit` applies |
| Agility (Hunter) | Physical | Yes |
| **Will (Keeper)** | **Light** | No — bypasses armor |
| **Will (Captain)** | **Westernesse** | No — bypasses armor |

The Keeper and Captain's magic is named because it matters lore-specifically which kind of power it is — not because it grants a bonus on its own. Light is the grace of the Valar; Westernesse is the strength of Númenorean heritage. These are not mechanical boons yet. They become mechanically relevant when legendary weapons add bane affinities (V2).

### DPS formulas (unchanged, relabeled)

- **Warden:** `Mig × 0.5` — Physical
- **Hunter (Agi build):** `Agi + Mig × 0.4` — Physical
- **Hunter (Mig build):** `Mig + Agi × 0.4` — Physical
- **Keeper:** `Wil × 0.9` — **Light** *(zero on any tick spent on a rescue)*
- **Captain:** `Mig × 0.3` (Physical) + `Wil × 0.2` (**Westernesse**)

Captain's Will is Westernesse because she is of that lineage — it is not a bane toggle. If she fights a wraith, the Westernesse damage type is present, but there is no multiplier attached to it until she carries a Westernesse weapon.

### Armor application (revised)

Physical sub-totals from all active members are summed first; armor is applied to that total. Affinity damage (Light + Westernesse) is summed separately and added after:

```
physDps     = Σ physical contributions (Warden, Hunter, Captain Mig portion)
affinityDps = Σ Light (Keeper) + Σ Westernesse (Captain Wil portion)
effArmor    = physMit × (1 - min(1, draughtPotency / PEN_SCALE))
effectiveDps = physDps × (1 - effArmor) + affinityDps
```

This replaces the current single-`raw` path in `dpsBreakdown()`.

---

## Bane Affinities (V2 — weapon-gated)

### Concept

Enemy encounters carry category tags (`orc`, `wraith`, `shadow`, `dragon`, etc.). Named legendary weapons carry bane affinities that match one or more of these categories. When a member carries a bane weapon that matches the enemy category, their DPS is multiplied.

| Bane name | Enemy category | Lore anchor |
|---|---|---|
| Beleriand | `orc` | Elvish/dwarven steel from the First Age wars against Morgoth |
| Westernesse | `wraith` | Númenórean steel; Barrow-blades and Dúnedain weapons that wound the undead |
| Light | `shadow` | Grace of the Valar; Elbereth's light that shadow-creatures cannot endure |
| Mormegil | `dragon` | The doom of the Black Sword; Gurthang's killing blow against Glaurung |

### V2 scope

Weapon bane system is entirely V2+. No innate role banes exist — the multiplier is always weapon-sourced. The damage *type* on magic output (Light, Westernesse) is what makes a weapon's bane affinity relevant for that role: a Westernesse weapon in the Captain's hands multiplies her Westernesse output against wraiths.

`BANE_MULTIPLIER = 1.15` (placeholder — validate in sim when bane weapons are designed)

### V1 note

Encounter enemy-category tags can be authored now. V1 encounters may carry them for forward-compatibility. No bane fires in V1 (no weapons). When bane weapons land in V2, already-tagged encounters will work without reauthoring.

---

## Incoming Shadow Damage — DESIGN PENDING

> **Placeholder.** This section needs a dedicated design session before it is specified.

Open questions:
- Is shadow damage a separate per-tick channel (like drain), or is it attached to spike events?
- What is the mitigation model — individual Will, party-wide radiance, both?
- How does it interact with the existing Shadow status effect (which drains Will + Fate over time)? Are they additive, or does the status effect gate the damage channel?
- Which enemy categories deal shadow damage? Ringwraiths obviously — what else?
- Is there a design reason to separate "the Black Breath" (undead-flavored shadow wound) from generic shadow damage?

Do not implement shadow incoming damage until these are answered. Keep the encounter schema field as a TBD stub.

---

## sim implementation scope (damage types only)

Changes to `tools/sim/run_sim.js`:

1. **`dpsBreakdown()`** — split into `physDps` and `affinityDps`; apply armor only to `physDps`; sum after. Return both sub-totals for logging.
2. **No bane multiplier in V1 sim** — enemy category tags can be parsed but nothing fires.
3. **No shadow damage channel** — pending design.

Changes to `docs/combat-model.md`:

1. Update DPS formulas table with damage type labels (Physical / Light / Westernesse).
2. Add a "Damage Types" section with the armor-split formula.
3. Add a "Bane Affinities" section — concept and V2 weapon-gating, four-bane table.
4. Add a stub "Incoming Shadow Damage" section marked design-pending.
5. Update the constants block with `BANE_MULTIPLIER = 1.15` (V2 placeholder).

---

## What this is NOT

- No innate role banes — banes come from weapons, not from being a Keeper or a Warden.
- No mechanical bonus from damage types alone in V1 — Light and Westernesse bypass armor, that is all.
- No shadow damage channel yet — pending design session.
- No player-facing damage type UI in V1 — the system is backend only for now.
