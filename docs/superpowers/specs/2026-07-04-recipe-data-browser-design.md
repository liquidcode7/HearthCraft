# Recipe & Ingredient Data Browser (HTML tool)

**Date:** 2026-07-04
**Status:** Approved

---

## 1. Overview

A generated, read-only HTML page for browsing recipe and ingredient data —
grouped into spreadsheet-style tabs by region and class — so Wes can review
the recipe roster and spot gaps (e.g. ingredients with no recipe using them)
without reading raw JSON.

This is a dev tool, not part of the shipped app. It lives alongside the
existing `tools/sim/` combat simulator and follows the same pattern: a plain
Node.js script reads the game's JSON data and writes a static HTML file.
No server, no account, no build step — open the file in a browser.

**Source of truth stays the JSON files** (`recipes.json`, `ingredients.json`,
`bands.json`). This tool never writes back to them. Whenever a session edits
recipe/ingredient/band data, I (Claude) re-run the generator as part of that
same task — there is no file watcher, build hook, or live sync.

---

## 2. Tabs

One table per region × class combination that actually has recipes, derived
from `recipe.band` (mapped to region via `bands.json`) and `recipe.class`:

| Tab | Filter |
|-----|--------|
| Bree-land — Food | `band: "greycloaks"`, `class: "food"` |
| Bree-land — Draughts | `band: "greycloaks"`, `class: "draught"` |
| Celondim — Food | `band: "mithlost"`, `class: "food"` |
| Celondim — Draughts | `band: "mithlost"`, `class: "draught"` |
| Thorin's Halls — Food | `band: "undermarch"`, `class: "food"` |
| Thorin's Halls — Draughts | `band: "undermarch"`, `class: "draught"` |
| Houses of Healing | `band: "all"`, `class: "hoh"` (one tab, not split by region) |
| Ingredient Usage | all ingredients, see §4 |

Tabs with zero matching recipes are omitted rather than shown empty.

---

## 3. Recipe Row Columns

Each recipe tab is a sortable table with one row per recipe:

- Name
- Tier
- Level requirement (`cookLevel` for food/draught, `hohLevel` for HoH)
- Hero ingredient
- Full ingredient list with quantities (e.g. "Goodman's Taters ×2, Hearthgrain ×1, Chetwood Browncap ×1")
- Effect — whichever applies to that recipe's class:
  - Food: primary stat + boost, secondary stat + boost if present
  - Draught: hazard effect (e.g. Potency, Warmth)
  - HoH: treated wound type(s)
- Description

Rows sorted by tier, then level requirement, matching the grouping style
already used in `JournalScreen.kt`'s discovered-recipes list.

---

## 4. Ingredient Usage Tab

One row per ingredient in `ingredients.json`:

- Name
- Region
- Source type (forage/farm/husbandry/etc., per design doc §10.2)
- **Recipe count** — how many recipes across all classes reference this
  ingredient (as hero ingredient or in the ingredient list)
- Sorted ascending by recipe count by default, so zero- and low-usage
  ingredients surface at the top

This directly targets the "too many ingredients that go nowhere" concern —
it's a diagnostic view, not a fix. Whether specific ingredients need new
recipes, or should be cut, is a separate balance/content decision made after
looking at this tab, not something this tool decides.

Known data hygiene note surfaced during research: `ingredients.json` has one
inconsistent region string (`"lone-lands"` lowercase vs. `"Lone-Lands"`
elsewhere) — the generator will treat these as the same region for grouping
purposes (case-insensitive match) rather than silently splitting them into
two rows.

---

## 5. Implementation Shape

- New `tools/recipe-browser/` directory, mirroring `tools/sim/`
- `generate.js` — plain Node script (no npm dependencies), reads
  `app/src/main/assets/data/{recipes,ingredients,bands}.json` via `fs`,
  builds the tab groupings described above, writes a single static
  `recipe-browser.html` (inline CSS/JS for tab switching and column sort —
  no external assets, so the file is portable on its own)
- Run manually: `node tools/recipe-browser/generate.js`
- Not wired into any build step, CI, or git hook — purely run-on-demand by me
  during sessions that touch the relevant data

---

## 6. Out of Scope

- Editing recipe/ingredient data through this tool (§1 — read-only)
- Any automatic/watched regeneration
- Deciding which ingredients need new recipes or should be removed — this
  tool surfaces the data, the actual balance decision is separate
- Encounter/combat data (already covered by `tools/sim/`)
