# HearthCraft — Master Design Document
**Redesign: 30 June 2026**
**Status: Active. All prior design docs archived in `legacy/`.**

> This document is the single source of truth. Everything that contradicts it
> is legacy. Everything that contradicts this and is newer belongs in this document.
> Update here first, then update the code.

---

## 1. Vision

An offline Android game set in high fantasy. The player is the indispensable
provisioner behind a roving band of fighters — the one who gathers, grows,
cooks, and sustains them. The player never fights. Without the player's craft,
the band is nothing.

The player's title is **[PLACEHOLDER — not yet decided]**. Do not use
"Hearthwright" or "warlock-culinarian" — both deprecated. Use "the player"
or "the provisioner" in code and UI until the title is locked.

This is not a cozy cooking sim. It is a specialist identity fantasy rooted in
craft, deep knowledge, and a war that needs winning. The long-term destination
is a full raid RPG fought across named battlegrounds from Middle-earth's history,
with the cooking game as its indispensable foundation.

One game. No version gates. F-Droid target.

---

## 2. What This Game Is Not — Ever

- No multiplayer, trading, or leaderboards
- No cloud saves or accounts
- No ads or IAP of any kind
- No internet requirement
- No real-money transactions

---

## 3. The Three Peoples and Their Bands

Three playable peoples, each with one active 4-member band. Men are designed
first and serve as the template. Elves (Mithlost) and Dwarves (Undermarch) are
parked pending redesign — their old data remains in the codebase for reference
but is not being built toward.

| People | Band Name | Home Region |
|--------|-----------|-------------|
| Men | Greycloaks | Bree-land |
| Elves | Mithlost | Ered Luin / Celondim — *pending redesign* |
| Dwarves | Undermarch | Thorin's Halls — *pending redesign* |

Each band has exactly four members, one per role:

| Role | Function |
|------|----------|
| Captain | Hybrid damage, Dread anchor, jack of all trades |
| Warden | Primary damage/tank (physical) |
| Keeper | Arcane damage + sole healer |
| Fighter | Pure DPS |

---

## 4. Stats

Five stats apply to all band members. They grow over time and are boosted by food.

| Stat | Code | What It Drives |
|------|------|----------------|
| Might | mig | Physical damage, physical defence |
| Agility | agi | Speed-based damage, evasion |
| Vitality | vit | Morale pool (health), resilience |
| Will | wil | Magical damage, healing magnitude, Dread resistance |
| Fate | fat | Streak trigger rate, Inspiration odds |

**Fate food does not exist and will never exist.** Fate is a character stat only.
Feeding Fate would make the streak system untunable. This is a hard rule.

### 4.1 Leveling

Each band member has their own combat level (cap **50**), driven by combat XP
earned per mission: Victory grants XP, Stalemate grants a smaller amount,
Defeat grants none — the same "no reward on defeat" shape already used for
mission rewards.

A member's stat at their current level is `startingStat × (1 + growthRate) ^
(level − 1)` — compound growth, not flat-linear, so a level-50 veteran is
dramatically stronger than a level-1 recruit rather than only modestly so.
Growth rates are **per role**, not per named individual, and follow each
role's documented primary/secondary stats above: the primary stat grows
fastest (3.5%/level), a single secondary stat grows at roughly half that
(2.0%/level), other stats grow slowly (1.0%/level). The Captain is a special
case: its two secondaries (Fate and Vitality) each grow at 1.0%/level so
their combined elevated growth matches every other role's single-secondary
budget, rather than each getting the full secondary rate. These are
placeholder magnitudes pending balance validation via the harness, not
locked numbers.

---

## 5. Band Roles — Mechanical Design

### 5.1 Captain

- **Primary stat:** Will
- **Secondary stats:** Fate, Vitality *(two secondaries — unique to Captain)*
- **Damage type:** Hybrid physical + magical. Partial armor immunity because
  neither damage type alone is fully blocked by armor or by Will resistance.
- **Special:** The only role whose Will negates Dread.
  `willCut = captainWil / 100`. Higher Captain Will = less Dread suppression
  on the party.
- **Identity:** Jack of all trades and indispensable. Hits hard — harder than
  you'd expect — but not a full DPS like Keeper or Fighter. The band cannot
  function without them.

### 5.2 Warden

- **Primary stat:** Vitality
- **Secondary stat:** Might
- **Damage type:** Physical
- **Identity:** The wall. Takes hits, deals reliable physical damage. High
  Vitality ceiling — harder to kill than anyone else.

### 5.3 Keeper

- **Primary stat:** Will
- **Secondary stat:** Vitality
- **Damage type:** Magical (arcane)
- **Identity:** The arcane team member. Does slightly less damage than Fighter
  if completely uninterrupted — this is by design. When healing is needed,
  they heal. When it isn't, they hit.

**Keeper healing mechanics:**

| Mechanic | Rule |
|----------|------|
| HoT slot 1 | Independent HoT on any member including self |
| HoT slot 2 | Second independent HoT on any member including self |
| HoT reapply | Can only reapply a HoT slot when that slot's effect has expired, not both |
| Triage heal | Single-target burst when a member drops critically low |
| Group heal | AoE heal, lower per-member than triage |
| Rescue burst | Emergency heal, capped at 5 uses per fight |
| Self-heal | Eligible for all of the above |
| DPS | Fires in every gap where no heal action is needed |
| Streak crit-heal | Can crit-heal during a streak (healing counts as an action) |

The Keeper never stops contributing. Every tick is either a heal action or a
damage action. There are no idle ticks.

### 5.4 Fighter

Two builds, same secondary stat:

- **Ranged** — Primary: Agility. Secondary: Fate.
- **Melee** — Primary: Might. Secondary: Fate.
- **Damage type:** Physical (both builds)
- **Identity:** Pure DPS. High Fate secondary means they trigger streaks often —
  but so would any member with equivalent Fate. High Fate is a character trait,
  not a role privilege.

For Men (Greycloaks), the player chooses melee or ranged once, at character
creation — permanent for that save. Elves and Dwarves are pending redesign
(§3); their Fighter build, if any, isn't decided yet.

---

## 6. Combat Model — Model B

**Food provides stat boosts only. Food does not provide HP/s. Ever.**

The old HP/s model produced fight-or-wipe cliffs with no interesting middle
ground. Model B removes this entirely. The Keeper is the sole source of
in-combat healing. Food makes the party stronger. The Keeper keeps them alive.

### 6.1 Stat Boosts from Food

Food boosts Might, Agility, Vitality, or Will — raising the relevant combat
outputs for whoever eats it. Higher Vitality = bigger morale pool = more
survivable. Higher Will = Keeper heals more and hits harder. Etc.

There is no correct food for a given character or fight. The player reads the
fight, reads failure, and adjusts. A Keeper fed Vitality food survives longer;
a Keeper fed Will food heals harder and hits harder. Both are valid. Neither
is the answer.

### 6.2 The Streak System (Universal Crits)

Every role participates equally. No naming, no role-specific treatment.

| Property | Value |
|----------|-------|
| Trigger rate | `Fate × k` per tick, after a ~20-second refractory period |
| Duration | 5 ticks |
| Multiplier | 1.5× on all actions during the streak |
| Individual crits | Do not exist. The streak IS the crit mechanic. |
| Expected bonus DPS | ~4–5% over a full fight at high Fate. Meaningful, not decisive. |

A Warden with high Fate food would streak as often as a Fighter with high Fate.
Fate is the lever; the role is not.

### 6.3 The Inspiration System

Separate from Streak (§6.2) and role-specific. Where Streak is a small,
universal, no-narrative crit, Inspiration is rare, powerful, and named per
role — the moment a fight turns. Trigger chance is Fate-driven and low by
design; these should feel earned, not routine.

**Shared trigger formula:** `min(0.35, base + ownFate × 0.003)`, evaluated
per-tick against the role's own trigger condition (below). Each member's own
Fate feeds their own Inspiration only. The cap is raised from an earlier
0.25 now that Fate cannot be boosted by food (§6.1, §7.2) — a high-Fate
veteran earning a better shot via leveling is a legitimate payoff, not a
food-stacking exploit, so the cap can sit a little higher without the
mechanic feeling scripted. All base rates and this cap are placeholders
pending balance-harness validation, same as everything else in this
document.

**Warden — Horn of Gondor.** Trigger: a "crisis edge" — the tick where the
count of standing members below 35% HP rises from under 2 to 2 or more.
Effect: for an 8-tick window, all incoming spike damage redirects to the
Warden regardless of normal targeting, and the Warden's HP is floored at 1
for the duration — he cannot be downed or take a wound from it.

**Fighter — Black Arrow (ranged build) / Bullroarer's Five-Iron (melee
build).** Same mechanical effect, different flavor per build. Trigger is
the literal embodiment of "forecast of defeat, not accumulated death":
evaluated only after 40% of the fight's duration has elapsed, chance rises
linearly from 0 toward a low cap as the fight drags on, only while the
party's current DPS pace would not kill the boss before time runs out.
Effect: burns 18% of the boss's remaining resolve directly against a
killable target; against an unkillable target, burns 15% off the survival
clock instead. (Originally 35% resolve burn — deliberately cut to 18%
because 35% was rescuing fights that should have been lost to bad
provisioning, which breaks the "preparation must matter" rule.)

**Keeper — Hands of Healing.** Trigger: fires when a member's wound count
reaches the grievous threshold (5 wounds). Effect: fully resets that
member's wounds and restores them to 40% of max HP. Capped at **2 uses per
fight** — a hard resource limit, not "uncapped but rare."

**Captain — Wrath, Ruin, and the Red Dawn.** Trigger: the same crisis-edge
condition as Horn of Gondor. Two effects, both active every time it fires:

1. Direct: heals all active members 15% of max HP (can revive someone
   downed) and grants the whole party a ×1.5 DPS multiplier for 10 ticks.
   This is the Captain's core identity — making the whole party better at
   what they already do, not doing it for them.
2. Unlocks each other role's use-condition for a window, rather than
   raising their trigger chance: the Warden's horn can fire without needing
   its own crisis-edge to be true; the Fighter's inspiration can fire the
   moment its roll succeeds without waiting past the 40%-elapsed gate; the
   Keeper's heal can target whichever standing member has the most wounds,
   even if they haven't reached grievous yet, instead of requiring someone
   already at the brink. A minor, deliberately weak boost to the other
   three's raw trigger chance also applies — kept small because making
   defensive/healing tools fire *more often* would undercut the tension
   these mechanics exist to create; loosening *when* they're allowed to
   fire is the real lever.

**Status:** documented and implemented (see `EncounterEngine.kt`). This
mechanic existed in an earlier form before the 30 June 2026 redesign
(different name conventions, different numeric scale) and was rebuilt fresh
for the current stat scale and four-role structure rather than ported
as-is.

### 6.4 Dread

Dread suppresses all healing by a percentage (multiplicative). Raw healing food
cannot outmuscle it — 45% of extra healing is still lost. Only Hope preparations
(which remove the Dread multiplier) fully counter it.

The Captain's Will reduces Dread's effect on the party: `willCut = captainWil / 100`.
The Captain is the sole anchor for this. A band without a functioning Captain
in a Dread encounter is fighting uphill.

### 6.5 Despair (formerly Shadow)

Shadow is renamed **Despair**. Drains Will and Fate over time. Radiance
preparations counter it.

### 6.6 Maladies

| Malady | Effect | Counter |
|--------|--------|---------|
| Cold | Reduces DPS rate AND healing rate simultaneously | Warmth preparations |
| Disease | Reduces healing AND Inspiration odds | Hale preparations |

Cold is a double-bind: it punishes both the damage dealers and the Keeper at
the same time. It cannot be outrun by raw stat investment alone. Warmth is the
answer.

### 6.7 Fight Structure

Three fight shapes:

| Shape | Win condition |
|-------|---------------|
| Kill fight | Boss has resolve pool; burn it down before timer |
| Survival fight | No kill condition; hold out until timer |
| Escape / outlast | Narratively different from survival, mechanically identical |

Encounter JSON tags which shape applies.

### 6.8 Wound Severity & Recovery

A lost encounter (DEFEAT) assigns each member a wound severity based on how
many times they went down during the fight:

| Down-count | Severity | Recovery |
|------------|----------|----------|
| 1-2 | Wounded (light) | Auto-heals after 18 minutes — time only, no food or prep involved |
| 3-4 | Wounded (heavy) | Auto-heals after 30 minutes — time only |
| 5+  | Grievously wounded | See §9.1 — requires Houses of Healing, does not auto-heal |

**Wounds never kill.** There is no death-by-combat-wound mechanic. Permadeath,
if designed later, is a separate system (see roadmap Phase 2B).

**HoH-availability safety net:** while the player has no visible/craftable HoH
recipe at all, a 5+ down-count result is capped at "Wounded (heavy)" with an
extended 2-hour recovery instead of becoming a genuine grievous wound. This is
deliberately harsh — a real penalty for under-provisioning, not a soft nudge —
since starting areas have no HoH recipe available at all and this is the only
consequence a new player faces for this outcome. The safety net switches off
automatically the moment a real HoH recipe becomes available to the player —
it is not a permanent difficulty reduction.

---

## 7. The Provisioning System

### 7.1 Slots

Each band of 4 members has **8 provisioning slots**:

- 4 food slots — one per member
- 4 draught slots — one per member

The player assigns food and a draught to each member individually before a
mission. These are not party-wide. The Captain's food is the Captain's food.

### 7.2 Food

- Provides stat boosts only (see §6.1)
- Tier → breadth: higher tiers cover more stats per dish
- Quality → depth: higher quality = larger magnitude per stat boosted
- Any recipe can be given to any member — no food is locked to a role
- No Fate food. Ever.

Quality **multiplies** a dish's authored stat boost — Crude softly reduces
it below the authored value, Fine is the 1.0x baseline ("tier-appropriate"
reference point), Pristine amplifies it well beyond authored. Placeholder
values, tuned via the balance harness:

| Grade | Multiplier |
|-------|-----------|
| Crude | 0.7x |
| Common | 0.85x |
| Fine | 1.0x (baseline) |
| Superb | 1.3x |
| Pristine | 1.7x |

No food at all is worse than any grade of food — going into an on-level
fight unprovisioned should be a clear loss.

### 7.3 Draughts

Per-member. Not party-wide.

| Type | Effect |
|------|--------|
| Potency | Armor penetration — applies only to that member's physical attacks |
| Warmth | Counters cold malady |
| Hale | Counters disease malady |
| Alert | Counters torpor / fatigue |
| Radiance | Counters Despair |
| Hope | Counters Dread (later tiers) |

A potency draught on the Warden affects only the Warden's attacks. A potency
draught on the Keeper affects only the Keeper's attacks (which are magical —
potency may have no effect, or reduced effect, depending on encounter armor
type). This creates genuine per-member prep decisions.

---

## 8. The Craft System

### 8.1 The Kitchen — One Interface, All Crafting

Everything the player crafts happens in the kitchen. There is no separate prep
station. The distinction between "preparing an ingredient" and "cooking a dish"
is time and complexity, not location.

**Prepared ingredients** (butter, cheese, rendered fat, smoked fish, bone broth,
salted pork, etc.) are crafted in the kitchen first, placed in the pantry, and
then used as ingredients in higher-tier recipes. A player who keeps a well-stocked
pantry of prepared goods cooks faster. A player who doesn't must wait for prep
before they can cook.

This is intentional. The production chain — render fat today, use it in tomorrow's
campaign ration — is part of the provisioner identity.

### 8.2 Recipe Tiers

Tiers represent breadth of stat coverage:

| Tier | Stat coverage | Notes |
|------|---------------|-------|
| T1 | 1 stat | Starter dishes. Core region ingredients only. |
| T2 | 1 primary + 1 secondary | Uses region ingredients + first prepared goods |
| T3 | Wider coverage | May require prepared ingredients from sub-regions |
| T4+ | Multi-stat | High-prep, high-reward |

T1 recipes **never** require ingredients from a sub-region the player hasn't
reached. This is a hard rule.

The number of cooking tiers required for the full campaign is an **open design
question** — the breadth model may run out around T5 if tiers are too narrowly
spaced. Design the Men's arc first, then extrapolate.

### 8.3 Grimoires

Three grimoire classes:

| Class | Source | Unlocks |
|-------|--------|---------|
| Cooking grimoire | Region boss drops | Food recipes at that tier |
| Draught grimoire | Miniboss and boss drops | Draught recipes at that tier |
| HoH grimoire | Miniboss and boss drops | Houses of Healing preparations |

Grimoires are not guaranteed drops — they gate meaningful progression. A player
who hasn't found the Tier 2 cooking grimoire cannot cook Tier 2 food, regardless
of Cook level or Band level.

**Individual draught recipe discovery:** Individual draught recipes can also be
found as drops from minibosses or expedition rewards — bypassing the grimoire
requirement for that specific recipe only. A found recipe is permanently unlocked.

**T1 recipes never require a grimoire.** Cooking T1 food and T1 draughts are
starter knowledge, always available from the beginning. Only T2+ requires a grimoire.

**Drop sources for Bree-land arc:**

| Encounter | Drop |
|-----------|------|
| Wolf-Master (miniboss, band level 7) | Draught grimoire T2, HoH grimoire T1 |
| Rhudaur Men (region boss, band level 9) | Cooking grimoire T2 |

**Implementation note — class naming:** In the data layer the "Cooking grimoire" class identifier is `"cooking"` (IDs: `cooking_t2`, etc.). Food recipes carry `recipeClass = "food"`. The `isRecipeVisible()` function maps `"food"` → `"cooking"` when building the grimoire lookup key. Draught and HoH class names are consistent (`"draught"`, `"hoh"`) between recipes and grimoires and require no mapping.

### 8.4 Three-Lock Progression

Advancing to a new tier requires **all three** (for T2+ only):

1. Band level — the band must have reached the threshold
2. Cook level — the provisioner's skill must be sufficient
3. Grimoire — the recipe book for that class and tier must have been found

Missing any one of the three means the tier is locked. This creates meaningful
pacing and prevents over-preparation.

Individual recipe discovery (draught only) bypasses the grimoire lock for that
specific recipe, but cook level and band level still apply.

---

## 9. Houses of Healing

A third, fully independent craft track alongside cooking and draught-making.

### 9.1 What It Does

Houses of Healing is the **only mechanic capable of clearing grievous wounds**.
A grievous wound cannot be walked off or outcooked. It requires a Houses of
Healing preparation and time.

It does not instantly heal. It accelerates recovery and, at higher quality,
applies a **recovery buff**: elevated incoming healing for the first fight after
the member returns. The buff magnitude and duration scale with preparation quality.

The recovery buff is elevated incoming healing only — **not** damage reduction.
Damage reduction was judged too powerful and excluded.

See §6.8 for the current wound severity thresholds and the pre-HoH softlock
safety net that applies before any HoH recipe exists.

### 9.2 Separate System

| Property | Value |
|----------|-------|
| Leveling | Independent from cooking level |
| Recipes | Own recipe list — healing preparations, not food |
| Grimoires | HoH grimoires, dropped from minibosses and bosses |
| Interface | Kitchen (same space, distinct track) |

### 9.3 First Recipe

**Athelas preparation** — the first discoverable HoH recipe. Requires athelas
(forageable in Bree-land) and a HoH grimoire. This is the player's introduction
to the system and the Keeper's most important tool in the early game.

---

## 10. Ingredient System

### 10.1 Regional Separation

Ingredients are hard-separated by region. A Bree-land ingredient does not appear
in Thorin's Halls content. Ironroot is a Dwarven ingredient — it should never
appear in Greycloaks recipes. This is enforced at the data level.

Cross-region ingredients become available when the band **travels to that region**.
Reaching the Lone-Lands unlocks Lone-Lands ingredients. Going back to Bree-land
does not re-enable Lone-Lands foraging.

### 10.2 Source Rules

| Source type | Rule |
|-------------|------|
| Forage | Location-locked. You find what grows where you are. |
| Hunt / Fish | Location-locked. You catch what lives where you are. |
| Farm / Cultivate | Portable once seeded. Initial seeds require being in-region. After first acquisition, can be grown at home regardless of where the band is. |
| Husbandry | Portable. Your animals stay with you. |
| Water / Draw | Local. You draw from wherever you are. |
| Processed / Prepared | Produced in the kitchen from other ingredients. Follow their source inputs. |

**All plants are farmable once acquired.** There is no such thing as a permanently
forage-only plant. If you find sloe in the wild, you now have sloe-stock to
cultivate. The `cultivatable: true` flag on plant ingredients signals this to
the engine.

### 10.3 Prepared Ingredients

Intermediate kitchen products that feed into higher-tier recipes:

| Prepared ingredient | Source | Method |
|--------------------|--------|--------|
| Butter | Milk × 2 | Churn |
| Hard Cheese | Milk × 2 | Press |
| Rendered Fat | Rabbit or Hog Meat × 2 | Render |
| Smoked River Trout | River Trout × 1 | Smoke |
| Salted Pork | Hog Meat × 1 | Cure |
| Bone Broth | Marrow bones (future) | Long simmer |
| Deer Haunch (dried) | Deer Haunch × 1 — Lone-Lands | Cure/dry |

---

## 11. Men — Ingredient Roster (Bree-land)

### 11.1 Bree-land Core — Available from Start

**Grains — Farm/Cultivate**

| ID | Name | Primary | Secondary | Notes |
|----|------|---------|-----------|-------|
| `hearthgrain` | Hearthgrain | vit | mig | Short-stalked kitchen grain. The foundation. |
| `barleycorn` | Barleycorn | vit | mig | Heavier. Ales and breads. |
| `rye_stalk` | Rye Stalk | vit | — | Dark grain. Dense flat loaves. |
| `oat_sheaf` | Oat-sheaf | vit | wil | Steady. Porridge, bannock, field rations. |

**Root Vegetables & Alliums — Farm/Cultivate**

| ID | Name | Primary | Secondary | Notes |
|----|------|---------|-----------|-------|
| `goodmans_taters` | Goodman's Taters | vit | — | Potatoes. |
| `parsnip` | Parsnip | vit | — | Sweet when roasted. |
| `turnip` | Turnip | vit | — | Humble. Filling. Keeps well. |
| `leek` | Leek | agi | wil | Sharp, clean. Goes in everything. |
| `onion` | Onion | vit | — | The base of half the cooking in Bree-land. |
| `brackenroot` | Brackenroot | mig | vit | Dense fern-root. Bitter raw, purplish roasted. What rangers carry. *Replaces `ironroot` — name was too Dwarven.* |

**Herbs — Farm/Cultivate**

| ID | Name | Primary | Secondary | Hazard | Notes |
|----|------|---------|-----------|--------|-------|
| `common_thyme` | Common Thyme | vit | wil | — | Kitchen herb. Preserves and broths. |
| `pipeweed_leaf` | Pipeweed Leaf | wil | — | — | Infusion only. A particular calm. Uncommon. |

**Mushrooms — Farm/Cultivate**

| ID | Name | Primary | Notes |
|----|------|---------|-------|
| `pale_cap` | Pale Cap | vit | Cultivated. Mild, reliable. |

**Berries — Farm/Cultivate**

| ID | Name | Primary | Notes |
|----|------|---------|-------|
| `duskberry` | Duskberry | agi | Small dark berries, tart. |

**Berries & Fruits — Wild Forage (cultivatable once found)**

| ID | Name | Primary | Secondary | Hazard | Rarity | Notes |
|----|------|---------|-----------|--------|--------|-------|
| `hedgerow_berry` | Hedgerow Berry | agi | mig | — | c | Mixed dark fruits of the hedgerow. |
| `sloe` | Sloe | agi | — | potency | c | Bitter wild plum. Bitters, draughts. |
| `crabapple` | Crabapple | agi | vit | — | c | Sharp. Best cooked down. |
| `wanderer_fig` | Wanderer Fig | agi | — | — | c | Small wild fig along old roads. |
| `rosehip` | Rosehip | vit | wil | — | u | High hips from the briar. Recovery preparations. |
| `bilberry` | Bilberry | wil | — | alert | u | Bitter moorland berry. Fog resistance. |

**Greens & Herbs — Wild Forage (cultivatable once found)**

| ID | Name | Primary | Secondary | Hazard | Rarity | Notes |
|----|------|---------|-----------|--------|--------|-------|
| `brookcress` | Brookcress | agi | wil | potency | c | Peppery streamside green. |
| `sorrel` | Sorrel | agi | wil | — | c | Sour leaf. Wakes the mind. |
| `meadowsweet` | Meadowsweet | wil | — | — | c | Sweet herb of riverbanks. Infusions. |
| `willowherb` | Willowherb | vit | wil | — | c | Tall pink herb of riverbanks and clearings. |
| `nettleleaf` | Nettleleaf | mig | vit | — | c | Stinging nettle. Blanched: rich and green. *Moved from Lone-Lands — grows anywhere.* |
| `yarrow` | Yarrow | vit | — | hale | u | Old healing herb. Bitter. Recovery draughts. |
| `feverfew` | Feverfew | — | — | hale | u | Bitter headache herb. Disease counter. |
| `heartflame_pepper` | Heartflame Pepper | — | — | warmth | c | Intense delayed heat. Used carefully. |
| `athelas` | Athelas | — | — | hale | u | Looks like a weed. In the right hands, extraordinary. |

**Mushrooms — Wild Forage (cultivatable once found)**

| ID | Name | Primary | Secondary | Rarity | Notes |
|----|------|---------|-----------|--------|-------|
| `stormcap` | Stormcap | agi | — | c | Springs up after rain. Timing matters. |
| `chetwood_browncap` | Chetwood Browncap | mig | vit | c | Common woodland mushroom. Earthy and dense. |
| `blushcap` | Blushcap | agi | — | u | Faintly pink. Delicate; quick to cook. |

**Hunt & Fish**

| ID | Name | Primary | Secondary | Rarity | Notes |
|----|------|---------|-----------|--------|-------|
| `rabbit` | Rabbit | agi | vit | c | Lean. Quick to cook. |
| `hog_meat` | Hog Meat | mig | vit | c | Wild pig from Chetwood margins. |
| `river_trout` | River Trout | — | — | c | Chetwood rivers. Raw input for smoking. |

**Husbandry**

| ID | Name | Primary | Secondary | Notes |
|----|------|---------|-----------|-------|
| `hens_egg` | Hen's Egg | vit | — | From a kept coop. |
| `milk` | Milk | vit | — | Dairy. Base for butter and cheese. |
| `forest_honey` | Forest Honey | vit | wil | Dark honey. Managed hives or wild. |
| `field_honey` | Field Honey | vit | — | Lighter honey, clover-fed. |

**Water — Draw**

| ID | Name | Rarity | Notes |
|----|------|--------|-------|
| `bree_well_water` | Bree Well-water | c | Common well. Base for soups and draughts. |
| `brandywine_water` | Brandywine Water | c | River water. Mild, faintly earthy. |
| `chetwood_spring` | Chetwood Spring Water | u | Cold spring. Cleaner than the well. |

**Prepared Ingredients — Kitchen**

| ID | Name | Primary | Secondary | From | Method |
|----|------|---------|-----------|------|--------|
| `butter` | Butter | vit | mig | milk × 2 | Churn |
| `hard_cheese` | Hard Cheese | vit | wil | milk × 2 | Press |
| `rendered_fat` | Rendered Fat | mig | vit | rabbit or hog_meat × 2 | Render |
| `smoked_river_trout` | Smoked River Trout | mig | agi | river_trout × 1 | Smoke |
| `salted_pork` | Salted Pork | mig | vit | hog_meat × 1 | Cure |

---

### 11.2 Bree Wildlands / Lone-Lands — Unlocks on Arrival

The band reaches the Lone-Lands by advancing past the Bree-land arc. All
Lone-Lands ingredients unlock at that point. Returning to Bree-land does not
re-enable Lone-Lands foraging, but any plant seeds already acquired can continue
to be farmed at the home base.

**Wild Forage (cultivatable once found)**

| ID | Name | Primary | Secondary | Hazard | Rarity | Notes |
|----|------|---------|-----------|--------|--------|-------|
| `thornberry` | Thornberry | agi | mig | — | c | Tough wild berry with thorned canes. |
| `old_forest_mushroom` | Old Forest Mushroom | wil | — | — | u | From the Old Forest fringe. Unusual. |
| `marshwort` | Marshwort | — | — | hale | u | Swamp-edge herb. Disease resistance. |
| `pale_fen_cap` | Pale Fen-cap | wil | — | — | u | Ghostly mushroom of the Midgewater Marshes. |
| `road_herb` | Road-herb | agi | vit | — | c | Tough low herb along the old roads. |
| `chetwood_acorn` | Chetwood Acorn | vit | mig | — | c | Ground into flour. Bitter until treated. |
| `wolf_moss` | Wolf-moss | — | — | alert | r | Dark lichen from deep forest floors. Rare. |

**Hunt & Fish**

| ID | Name | Primary | Secondary | Rarity | Notes |
|----|------|---------|-----------|--------|-------|
| `eel` | Eel | — | — | c | Bree Wildlands rivers. |
| `deer_haunch` | Deer Haunch | mig | vit | u | Old forest hunting. |

**Prepared Ingredients — Kitchen**

| ID | Name | Primary | Secondary | From | Method |
|----|------|---------|-----------|------|--------|
| `deer_haunch_dried` | Deer Haunch (dried) | mig | vit | deer_haunch × 1 | Cure/dry |

---

## 12. Men — Campaign Structure (Bree-land Arc)

| Band Level | Encounter | Notes |
|------------|-----------|-------|
| 1–3 | Neekerbreekers (Midgewater Marshes) | Tutorial-tier. Introduces the drain mechanic. |
| 3–5 | Wolves (Chetwood) | First threat-type variety. |
| 5–7 | Goblins | Armor introduced. Potency becomes relevant. |
| 7 | Wolf-Master | Miniboss. Drops HoH grimoire (first). |
| 9 | Rhudaur Men | Region boss. Drops cooking grimoire. Dread introduced. |
| — | Barrow-wight | Return vault encounter. Optional. High difficulty. |

Starter regions cap at band level 10. The Lone-Lands arc follows.

Encounters also carry a **difficulty** tag — easy, medium, hard, or
**boss** — independent of the campaign table above. Difficulty scales how
much a dish's Quality matters: easy encounters are winnable on Common (and
forgiving even on Crude, so a new player is never hard-blocked by lack of
access to rare ingredients); boss encounters are the one category where
even Pristine, tier-appropriate food doesn't guarantee a win — they're
meant to be a real test. (Barrow-wight, above, is a natural fit for this
category.)

### 12.1 Progression Locks

Three locks gate advancement. All three must be satisfied:

1. **Band level** — earned through missions
2. **Cook level** — earned through crafting
3. **Grimoire** — dropped from bosses and minibosses

Missing any one = the next tier is locked. This is intentional pacing.

---

## 13. Recipe Changes from Ingredient Redesign

Four existing Greycloaks recipes referenced `ironroot` — now `brackenroot`:

| Recipe ID | Change |
|-----------|--------|
| `hearth_and_hops` | `ironroot` → `brackenroot` |
| `rangers_fare` | `ironroot` → `brackenroot` |
| `heartflame_broth` | `ironroot` → `brackenroot` |
| `restorative_broth` | `ironroot` → `brackenroot` |

`field_and_fen_potage` used `willowherb` (formerly Wildwood/Cardolan) — clean
now that willowherb is a core Bree-land ingredient.

---

## 14. Open Design Questions

These are real open threads, not oversights. Answer them before implementing
the affected systems.

| Question | Why it matters |
|----------|----------------|
| Player title | Every string in the UI waits on this |
| Warden's primary and secondary stats | ~~Locked: primary Vitality, secondary Might~~ |
| How many cooking tiers for full campaign | Breadth model may run out at T5 — needs projection |
| Exact Lone-Lands unlock trigger | On arrival at first Lone-Lands encounter? On completing Bree-land boss? |
| North Downs / Weather Hills as Men sub-region 3 | Needed before designing that arc |
| What happens at band convergence (all three peoples in same area) | Cross-band provisioning implications |
| Elves redesign (Mithlost) | After Men arc is built |
| Dwarves redesign (Undermarch) | After Men arc is built |
| HoH leveling curve | Before implementing HoH |
| Recipe tier structure beyond T2 for Men | Before writing T3+ recipes |

---

## 15. Voice and Tone

*(Preserved from legacy `voice-tone.md` — still authoritative.)*

The provisioner is not a cook who stumbled into a war. They are an expert in
hostile conditions whose craft is the difference between the band surviving and
not. The writing reflects this: no whimsy, no cozy, no self-deprecating humor
about "just cooking." The recipes are terse and honest. The after-action text
is specific and direct. The world is hard and the food is what keeps it manageable.

Characters have weight. The Greycloaks are not a fantasy party. They are
people in a bad situation being kept alive by one very capable provisioner.

---

*End of document. Update here before updating code.*
