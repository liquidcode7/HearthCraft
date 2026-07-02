# Houses of Healing — Full Mechanics Design
**Date:** 2026-07-02
**Status:** Approved

---

## 1. Overview

Houses of Healing (HoH) is the third independent craft track alongside cooking and draught-making. It is the **only mechanic capable of clearing grievous wounds**. A grievously wounded member does not auto-recover — they are benched indefinitely until the provisioner crafts and applies a HoH preparation.

The system mirrors the cooking system structurally: a two-lock gate (grimoire + HoH level), ingredient quality drives output grade, and HoH level sets the grade ceiling.

---

## 2. Wound Types

Five wound types can stack on a single band member. Physical is the most common (most enemies deal physical damage) but any combination of any types is possible — a Barrow-wight could inflict a pure Will wound, a Cargul a pure Corruption wound, etc. No type is required to be present.

| Type | Typical sources | Base floor duration |
|------|----------------|---------------------|
| Physical | Any combat grievous wound | 4h |
| Will | Barrow-wights, wraith-adjacent enemies, Old Forest | 3h |
| Corruption | Cargul, Nazgûl, Morgul weapons | 6h |
| Poison | Orcs, spiders, venomous creatures | 4h |
| Disease/Wasting | Dark places, cursed wounds, lingering illness | 5h |

**Encounter infliction:** Each encounter specifies its wound types in two buckets, validated by Wes per encounter:

- **Guaranteed:** wound types that always apply when a grievous wound result occurs. Defines the encounter's identity. At least one type must be guaranteed — this ensures a grievous wound always has a type.
- **Chance:** wound types that roll independently at a specified probability (0–100%). These add variance and texture on top of the guaranteed types.

Physical is not automatically guaranteed on any grievous wound — it is just another type that can be guaranteed or chance-rolled like the rest. A Morgul blade encounter might guarantee Will and Corruption with Physical as a low-probability chance roll. A Barrow-wight might guarantee Will with no Physical at all.

Example encounter definitions:
| Encounter | Guaranteed | Chance |
|-----------|-----------|--------|
| Cargul | Corruption | Physical 60%, Will 40% |
| Barrow-wight | Will | Disease 30% |
| Spider | Poison | Physical 50% |
| Witchking | Will, Corruption | Physical 20% |

**Stacking:** All types that resolve (guaranteed + successful chance rolls) are present simultaneously on the member. They combine into one recovery timer. A member with Physical + Corruption has a combined floor of 10h before treatment.

**Wounds never kill.** No permadeath from wounds. Permadeath, if designed later, is a separate system.

---

## 3. Recovery Timer

**Timer does not start until treated.** An untreated grievous wound leaves the member benched with no countdown. The provisioner must craft and apply at least one HoH preparation to begin recovery.

**One combined timer per member.** There are never parallel timers. All wound types and all applied preparations resolve into a single countdown.

**Timer formula:**
- Base = sum of floor durations for all wound types present
- Applied recipe(s) reduce the timer based on grade and whether the recipe is a single-type or compound preparation
- Multiple separate recipes applied to address different wound types combine their reductions — but the result is always worse than a single tier-appropriate compound recipe that covers the same types

**Grade-to-timer table (Physical wound only, for reference):**

| Grade | Timer |
|-------|-------|
| Crude | 4h |
| Common | 3h |
| Fine | 2h |
| Superb | 1h 30min |
| Pristine | 1h |

**Regular wound timers (non-grievous, auto-heal, no treatment required):**

| Severity | Timer |
|----------|-------|
| Light (1-2 downs) | 20 min |
| Heavy (3-4 downs) | 40 min |

Regular wounds must always resolve faster than any treated grievous wound — these timers are set accordingly.

**Selected grievous combination reference timers:**

| Wound types | Crude separate | Pristine separate | Pristine compound |
|-------------|---------------|-------------------|-------------------|
| Physical only | 4h | 1h | 50min |
| Physical + Will | 7h | 1h 45min | 1h 25min |
| Physical + Corruption | 10h | 2h 30min | 2h |
| Physical + Poison | 8h | 2h | 1h 35min |
| Physical + Disease | 9h | 2h 15min | 1h 50min |
| Physical + Will + Corruption | 13h | 3h 15min | 2h 35min |
| Physical + Corruption + Poison | 14h | 3h 30min | 2h 50min |
| Physical + Corruption + Disease | 15h | 3h 45min | 3h |
| All five types | 22h | 5h 30min | 4h 25min |

The full 31-combination space (any combination of the 5 types, no anchor type required) falls out of the underlying math. The table above is a reference sample; all other combinations are derived the same way.

---

## 4. Recipe Structure

### 4.1 Two-Lock Gate

1. **Grimoire** — the HoH grimoire for that tier must have been found. Without it the recipe is not visible.
2. **HoH level** — the provisioner's HoH skill must meet the recipe's `hohLevel` minimum. Without it the recipe is visible but cannot be crafted.

Both locks must be open to craft.

### 4.2 Tiers and Wound Coverage

Tier reflects overall potency and ingredient rarity, not a strict wound-type rule. Within a tier, individual recipes address different combinations. A T2 recipe might treat Physical+Poison while another T2 treats Physical+Will. Collect the right recipe for what you encounter.

| Tier | Typical wound coverage | Grimoire source |
|------|----------------------|-----------------|
| T1 | Single wound type | Miniboss drops |
| T2 | Physical + one other, or any single type at higher potency | Miniboss and boss drops |
| T3 | Two wound types in one preparation | Boss drops |
| T4+ | Three or more types in one compound preparation | Late boss drops |

### 4.3 Grade Resolution

Identical to the cooking system (`CookQuality.resolveDishGrade`):
- Ingredient grades are averaged, with the hero ingredient double-weighted if present
- The raw grade is clamped by a ceiling determined by `(playerHoHLevel - recipe.hohLevel)`
- Higher HoH level relative to the recipe minimum = higher grade ceiling

A newly unlocked recipe (HoH level exactly at minimum) produces Crude output. Mastery over time pushes toward Pristine.

### 4.4 Applying a Preparation

- Starts the recovery timer
- Clears the wound types that recipe addresses from the member's wound state
- Remaining wound types stay until treated separately or a compound recipe covers them
- A second preparation applied to the same member replaces the current timer with a newly calculated one based on all wound types treated so far — it does not stack on top of the existing countdown but recalculates from the full wound state
- Pre-crafted preparations can be stockpiled and applied when needed

### 4.5 Recovery Buff

Applying a preparation also sets a recovery buff that activates when the member returns to the band:
- **Effect:** elevated incoming healing for the first fight after return
- **Not** damage reduction (ruled out as too powerful)
- Buff magnitude and duration scale with the preparation grade

---

## 5. Athelas

Athelas is the most recognizable HoH ingredient from Tolkien's writing. It is important and players will recognize it — but it is not present in every recipe.

**Early game:** Wild-foraged athelas is available in Bree-land. Low grade, limited ceiling. Used in T1 physical wound preparations and early Physical+Will recipes. This is the player's introduction to the system.

**Late game:** Athelas becomes cultivatable at home once seeds are acquired. Cultivated athelas produces higher grades. A story-gated unlock (specific lore discovery or region progression — TBD) makes a higher-potency cultivated variant available. This is what the most powerful Physical+Will compound recipes require.

Athelas is the **hero ingredient** on recipes it appears in — its grade is double-weighted in `resolveDishGrade`. The ingredient uses the same item ID across grades; grade is tracked separately per-unit as with all ingredients.

---

## 6. Ingredient List

| Ingredient | Source | Wound affinity | Notes |
|------------|--------|---------------|-------|
| Athelas | Forage (Bree-land+), cultivate later | Physical, Will | Hero ingredient on Physical+Will recipes. Low-tier and high-tier use. |
| Yarrow | Forage | Physical | Common workhorse for early physical recipes |
| Kingsfoil | Forage | Physical | Support ingredient |
| Wormwood | Forage | Poison | Classic antitoxin |
| Rue | Forage | Poison | |
| Blackthorn berry | Forage | Poison, Disease | |
| Willow bark | Forage | Disease | |
| Feverfew | Farm | Disease | |
| Elderflower | Forage | Disease, Will | |
| Pipeweed | Farm | Will | Calming properties — recognizable ingredient |
| Linden blossom | Forage | Will | |
| Starflower | Forage (late regions) | Corruption | Rare, Elvish-adjacent lore |
| Shadowbane moss | Forage (dark places) | Corruption | Region-locked to late areas |
| Silver-thread lichen | Forage (caves, late) | Corruption, Will | HoH-exclusive |
| Bloodmoss | Forage | Physical, Poison | HoH-exclusive, rare early forage |
| Healing clay | Draw/dig | Physical | HoH-exclusive poultice base |
| Rendered beeswax | Husbandry | Any (carrier) | HoH-exclusive salve carrier/binder |
| Honey | Husbandry | Physical, Disease | Antiseptic binder |
| Spring water | Draw | Any (carrier) | Purifying agent, common support |
| Miruvor (dilute) | Story-gated/crafted | Will, Corruption | Very rare, late-game compound ingredient |

Preparation forms (poultice, tincture, salve, etc.) are flavor and creative space — not a mechanical constraint. Any form can address any wound type depending on recipe design.

---

## 7. HoH Leveling

- **Independent XP pool** from cooking and draught
- **XP sources:** crafting a preparation (first-craft bonus, less on repeat), applying a preparation that treats a grievous wound
- **Level gates recipes** via `hohLevel` field on each recipe — identical to `cookLevel` in the cooking system
- **Level sets grade ceiling** — same `cookCeiling` logic as cooking
- Leveling cooking does not affect HoH level and vice versa

---

## 8. Open Questions / TBD

- Exact story trigger for high-potency athelas cultivar unlock
- Per-encounter percentage chances for non-Physical wound type infliction
- Full recipe list (recipes are data, designed separately from this spec)
- Exact XP values for HoH crafting actions
- Exact `hohLevel` minimums per recipe tier
- Recovery buff magnitude and duration formula (grade → buff table)
