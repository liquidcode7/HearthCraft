# Damage Types & Bane Affinities — Design Spec

> Design agreed with Wes. Mechanism specified.
> Formalized from the design session notes for implementation in `tools/sim/run_sim.js` and `docs/combat-model.md`.
> Scope: simulator + design doc only. Android code is not touched by this spec.

---

## Summary

Two complementary systems that reward reading your enemy:

1. **Damage Types** — split outgoing DPS into physical vs. affinity channels. Only physical is reduced by armor. Keeper and Captain's Will-driven output bypasses it entirely, under named affinity types.
2. **Bane Affinities** — each role carries an innate bane that multiplies damage against the matching enemy category. Provisioning a role with its role-matched food activates the bane at full power; feeding a role food that boosts the wrong stat wastes it.

Neither system adds new numbers to learn. They explain why role composition matters and why you provision each member differently.

---

## Outgoing Damage Types

### The Rule

| Source stat | Damage type | Armor? |
|---|---|---|
| Might (Warden, Hunter) | Physical | Yes — `physMit` applies |
| Agility (Hunter) | Physical | Yes |
| Will (Keeper) | **Light** | No — bypasses armor entirely |
| Will (Captain) | **Westernesse** | No — bypasses armor entirely |
| Might (Captain) | Physical | Yes |

Captain deals hybrid damage every tick: her Mig×0.3 is physical and sits behind the armor wall; her Wil×0.2 is Westernesse and bypasses it. Feeding a Captain food that boosts Might makes her hit harder — but the Might half is still blocked by armor. Boosting Will gives armor-bypassing output that compounds with her bane against wraiths.

### Updated DPS formulas

Unchanged from current — only the *labeling* of damage types is new:

- **Warden:** `Mig × 0.5` — Physical
- **Hunter (Agi build):** `Agi + Mig × 0.4` — Physical
- **Hunter (Mig build):** `Mig + Agi × 0.4` — Physical
- **Keeper:** `Wil × 0.9` — Light *(zero on any tick it spends a rescue)*
- **Captain:** `Mig × 0.3` (Physical) + `Wil × 0.2` (Westernesse)

### Armor application (revised)

Physical sub-totals across all living, non-stunned, non-broken members are summed first; armor (`physMit`, reduced by draught penetration) is then applied to that total. Affinity damage (Light + Westernesse) is summed separately and added after — no armor applies to the affinity total.

```
physDps    = Σ physical contributions from all active members
affinityDps = Σ Light (Keeper) + Σ Westernesse (Captain Wil portion)
effArmor   = physMit × (1 - min(1, draughtPotency / PEN_SCALE))
effectiveDps = physDps × (1 - effArmor) + affinityDps
```

This replaces the current single-`raw` calculation in `dpsBreakdown()`.

---

## Bane Affinities

### The Four Banes

| Bane name | Enemy category tag | Lore anchor |
|---|---|---|
| **Beleriand** | `orc` | The ancient enmity — elvish steel and dwarven hammers forged in the First Age wars against Morgoth's armies |
| **Westernesse** | `wraith` | Númenórean steel; the Barrow-blades and the weapons of the Dúnedain that can wound the undead |
| **Light** | `shadow` | The grace of the Valar; Elbereth's light and the fire of Aman that shadow-creatures cannot endure |
| **Mormegil** | `dragon` | The doom of the Black Sword; Gurthang's willing strike that killed Glaurung |

### Role assignments (innate in V1)

Each role carries one bane. No items required — it is in their nature.

| Role | Innate bane | Reasoning |
|---|---|---|
| Warden | **Beleriand** | The ancient warrior; dwarvish lineage, iron memory of the First Age wars; orcs are the fundamental enemy |
| Hunter | **Mormegil** | The lone blade; Túrin's curse passed to all hunters who walk alone; dragon-kind is their doom and their glory |
| Keeper | **Light** | The healer is the light-bearer; shadow flees from care and warmth; natural enemy of all shadow-creatures |
| Captain | **Westernesse** | The Aragorn archetype; heir of Númenor, Strider's steel; wraiths are what Westernesse stands against |

Legendary weapons in V2 may unlock additional banes or amplify the innate one. For now: one bane per role, innate, always active when targeting a matching enemy.

### Multiplier and application

A bane multiplier applies when the target enemy has the matching category tag:

```
baneMultiplier = 1.15  (a 15% DPS boost vs. matching enemy category)
```

The multiplier applies to that role's entire DPS contribution (physical + affinity), not just one damage type. It fires after armor reduction:

```
memberEffDps = (physDps_member × (1 - effArmor)) + affinityDps_member
if enemy.categoryTags includes memberBane:
    memberEffDps *= BANE_MULTIPLIER
```

The bane multiplier is modest by design. It is not the difference between winning and losing — it is the reward for reading an encounter and bringing the right composition. A party with all four members bane-matched to the enemy category type gets a meaningful edge; a single bane match is a small bonus.

**The enemy category is a property of the encounter, not of individual fight stages.** All stages of a multi-stage encounter share the same category (e.g., a goblin raid is `orc` from approach to boss).

### Enemy category tags (current)

| Tag | Foes | Notes |
|---|---|---|
| `orc` | Orcs, Uruk-hai, goblins, half-orcs | Most of Rung 0–2 content |
| `wraith` | Ringwraiths, Barrow-wights, Dead Men, wights | Rung 5+ content; no V1 encounters |
| `shadow` | Great spiders, Balrog, shadow-monsters | Rung 6+ content; no V1 encounters |
| `dragon` | Glaurung, Smaug, cold-drakes | Late campaign; no V1 encounters |
| `beast` | Wolves, wargs, spiders (small), bats | No bane assigned — natural creatures, no ancient enmity |
| `huorn` | Huorns, Old Man Willow | No bane — requires a separate nature-affinity system (future) |

V1 Rung 0 encounters (Neekerbreekers, Wolves, Midges, Cave Bats, Mountain Wolves) are all `beast`. No bane fires in V1. This is intentional: bane rewards come later, once the player has the composition to use them.

The **Goblin Incursion** (Rung 1) is `orc` — the first fight where Beleriand fires. Warden becomes noticeably more effective.

---

## Incoming Shadow Damage (new channel)

### Separate from the Shadow status effect

Two distinct mechanics share the word "shadow":

| System | What it does | When it fires |
|---|---|---|
| **Shadow status** (existing) | Drains Will + Fate over time toward a floor | Enemies with `shadow` severity > 0 |
| **Shadow damage** (new) | A direct damage channel that hits member HP | Enemies with `shadowDmg` > 0 |

Shadow status is a slow deterioration. Shadow damage is an immediate wound — it can down a member like a spike. They coexist and stack; fighting a Nazgûl means both.

### Will as shadow mitigation

Each member mitigates shadow damage individually based on their current Will:

```
shadowMitFraction = min(0.60, memberWil × 0.008)
effectiveShadowHit = shadowDmgBase × (1 - shadowMitFraction)
```

Cap at 60% mitigation — no member can become fully immune. Will-boosting food (Contemplative Tea, Ranger's Fare) directly reduces the shadow damage those members take. The Keeper and Captain have the highest base Will and survive shadow encounters better unfed; Warden and Hunter are more exposed.

### Encounter schema addition

A new field `shadowDmg` joins `drain` and `spike` in the encounter/stage schema:

| Field | Type | Description |
|---|---|---|
| `shadowDmg` | float | Per-tick shadow damage per member (0 = none); reduced by each member's Will individually |

Shadow damage fires every tick (same as drain) but is mitigated individually rather than spread evenly. It does not benefit from the Warden's soak role — the Warden cannot absorb spiritual damage on behalf of the Keeper.

All current V1 and Rung 0–1 encounters have `shadowDmg: 0`. The Neekerbreekers and Goblin Incursion are physical fights.

---

## Impact on provisioning puzzle

This spec creates three new provisioning axes:

1. **Stat alignment for bane activation** — you don't unlock banes with items; you activate them by giving each role food that feeds their primary stat. A Keeper with bad food still has the Light bane, but their Will is low, so their Light output is low, so the bane multiplier doesn't do much. The puzzle is the same puzzle — provision correctly for role — but the combat-model doc now explains *why* it matters structurally.

2. **Anti-armor vs. Will trade-off for Captain** — in armored encounters, you want Captain's Might high (physical output, helped by penetration). In wraith/shadow encounters, you want Will high (Westernesse output, plus Westernesse bane + shadow mitigation). Ranger's Fare (Will-primary) is a different choice than Hearthmeat (Might-primary) and this spec makes that legible to the player.

3. **Shadow damage → Will food becomes defensive** — provisioning the Keeper and Captain with Will food is already good for healer output and Dread resistance. Shadow damage adds a third reason: it's the damage type they mitigate best. Provisioning Will food in a shadow encounter is the correct defensive play even for Warden if he's going to get hit.

---

## sim implementation scope

Changes to `tools/sim/run_sim.js`:

1. **`dpsBreakdown()`** — split into `physDps` and `affinityDps` sub-totals; apply armor only to `physDps`; sum after.
2. **Bane multiplier** — add `cfg.enemyCategoryTags` (default `[]`); each member checks their bane against the tag list and multiplies their contribution.
3. **Shadow damage channel** — add `cfg.shadowDmg` (default 0); per-tick per-member hit mitigated by `memberWil × 0.008`, capped at 60%.
4. **CLI flag** — `--shadow-dmg N` for shadow damage; `--tags orc,wraith` for enemy category.

Changes to `docs/combat-model.md`:

1. Update DPS formulas table with damage type labels (Physical / Light / Westernesse).
2. Add "Bane Affinities" section with the four-bane table and BANE_MULTIPLIER constant.
3. Add "Shadow Damage" section distinguishing it from Shadow status.
4. Update the constant block with `BANE_MULTIPLIER = 1.15` and the Will shadow mitigation formula.
5. Update enemy category tags in the Encounter JSON schema.

---

## Constants

```
BANE_MULTIPLIER        = 1.15     // DPS multiplier vs. matching enemy category tag
WILL_SHADOW_MIT_COEF   = 0.008    // per Will point of shadow mitigation (cap 0.60)
WILL_SHADOW_MIT_CAP    = 0.60     // no member can mitigate more than 60% of shadow dmg
```

---

## What this is NOT

- No damage types on incoming physical drain/spikes — the existing model handles those.
- No player-accessible damage type info screen in V1 — the player learns by reading enemy flavor and experimenting with food.
- No bane items in V1 — banes are innate and always active. Legendary weapons that add or amplify banes are V2.
- No `beast` or `huorn` bane — natural creatures have no ancient enmity to exploit.
