# Recipe & Ingredient Data Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Node.js script that generates a static, tabbed HTML page from `recipes.json`, `ingredients.json`, and `bands.json`, so Wes can browse recipes by region/class and spot ingredients no recipe uses.

**Architecture:** A small set of pure, testable CommonJS modules (data loading, tab grouping, ingredient-usage counting, display formatting, HTML rendering) wired together by one entry-point script (`generate.js`). No npm dependencies, no build step — matches the existing `tools/sim/` convention of plain Node scripts run manually with `node <script>.js`.

**Tech Stack:** Node.js (CommonJS `require`/`module.exports`), Node's built-in test runner (`node --test`, no external test framework), vanilla HTML/CSS/JS for the generated output (no client-side dependencies).

## Global Constraints

- Read-only: this tool never writes to `recipes.json`, `ingredients.json`, or `bands.json` — only reads them.
- No npm dependencies — plain Node built-ins only (`fs`, `path`, `node:test`, `node:assert/strict`).
- No file watcher, build hook, or CI wiring — the script is run manually.
- Generated HTML must be a single self-contained file (inline CSS/JS, no external asset references) so it's portable on its own.
- Tab order: regions sorted alphabetically by their short name (before any comma in the full region string), each region showing Food then Draughts (only if recipes exist for that combination), followed by "Houses of Healing", followed by "Ingredient Usage" last.

---

### Task 1: Data loader

**Files:**
- Create: `tools/recipe-browser/load-data.js`
- Test: `tools/recipe-browser/load-data.test.js`

**Interfaces:**
- Produces: `loadData(dataDir: string) -> { recipes: object[], ingredients: object[], bands: object[] }`; `regionForBand(bandId: string, bands: object[]) -> string | null`

- [ ] **Step 1: Write the failing test**

```js
// tools/recipe-browser/load-data.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { loadData, regionForBand } = require('./load-data');

function makeFixtureDir() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'recipe-browser-test-'));
  fs.writeFileSync(path.join(dir, 'recipes.json'), JSON.stringify([{ id: 'r1', band: 'greycloaks', class: 'food' }]));
  fs.writeFileSync(path.join(dir, 'ingredients.json'), JSON.stringify([{ id: 'i1', name: 'Test Grain', region: 'Bree-land' }]));
  fs.writeFileSync(path.join(dir, 'bands.json'), JSON.stringify([{ id: 'greycloaks', name: 'The Greycloaks', region: 'Bree-land' }]));
  return dir;
}

test('loadData reads and parses all three JSON files', () => {
  const dir = makeFixtureDir();
  const { recipes, ingredients, bands } = loadData(dir);
  assert.equal(recipes.length, 1);
  assert.equal(ingredients.length, 1);
  assert.equal(bands.length, 1);
  assert.equal(recipes[0].id, 'r1');
});

test('regionForBand returns the region for a known band id', () => {
  const dir = makeFixtureDir();
  const { bands } = loadData(dir);
  assert.equal(regionForBand('greycloaks', bands), 'Bree-land');
});

test('regionForBand returns null for an unknown band id', () => {
  const dir = makeFixtureDir();
  const { bands } = loadData(dir);
  assert.equal(regionForBand('nonexistent', bands), null);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tools/recipe-browser/load-data.test.js`
Expected: FAIL with "Cannot find module './load-data'"

- [ ] **Step 3: Write minimal implementation**

```js
// tools/recipe-browser/load-data.js
// Reads the game's recipe/ingredient/band JSON. Read-only — never writes back.
const fs = require('fs');
const path = require('path');

function loadData(dataDir) {
  const recipes = JSON.parse(fs.readFileSync(path.join(dataDir, 'recipes.json'), 'utf8'));
  const ingredients = JSON.parse(fs.readFileSync(path.join(dataDir, 'ingredients.json'), 'utf8'));
  const bands = JSON.parse(fs.readFileSync(path.join(dataDir, 'bands.json'), 'utf8'));
  return { recipes, ingredients, bands };
}

function regionForBand(bandId, bands) {
  const band = bands.find(b => b.id === bandId);
  return band ? band.region : null;
}

module.exports = { loadData, regionForBand };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tools/recipe-browser/load-data.test.js`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add tools/recipe-browser/load-data.js tools/recipe-browser/load-data.test.js
git commit -m "[hc] Recipe browser: data loader"
```

---

### Task 2: Tab grouping

**Files:**
- Create: `tools/recipe-browser/group-recipes.js`
- Test: `tools/recipe-browser/group-recipes.test.js`

**Interfaces:**
- Consumes: nothing from Task 1 (takes plain arrays, kept independently testable)
- Produces: `groupRecipesIntoTabs(recipes: object[], bands: object[]) -> { tabName: string, recipes: object[] }[]`; `shortRegion(region: string) -> string`

- [ ] **Step 1: Write the failing test**

```js
// tools/recipe-browser/group-recipes.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const { groupRecipesIntoTabs, shortRegion } = require('./group-recipes');

const bands = [
  { id: 'greycloaks', name: 'The Greycloaks', region: 'Bree-land' },
  { id: 'mithlost', name: 'The Mithlost', region: 'Celondim, western Ered Luin' },
  { id: 'undermarch', name: 'The Undermarch', region: "Thorin's Halls, Blue Mountains" },
];

const recipes = [
  { id: 'a', band: 'greycloaks', class: 'food' },
  { id: 'b', band: 'greycloaks', class: 'draught' },
  { id: 'c', band: 'mithlost', class: 'food' },
  { id: 'd', band: 'all', class: 'hoh' },
];

test('shortRegion trims verbose region strings to the place name before the first comma', () => {
  assert.equal(shortRegion('Bree-land'), 'Bree-land');
  assert.equal(shortRegion('Celondim, western Ered Luin'), 'Celondim');
  assert.equal(shortRegion("Thorin's Halls, Blue Mountains"), "Thorin's Halls");
});

test('groups recipes into region/class tabs ordered alphabetically by short region, HoH last', () => {
  const tabs = groupRecipesIntoTabs(recipes, bands);
  const names = tabs.map(t => t.tabName);
  assert.deepEqual(names, [
    'Bree-land — Food',
    'Bree-land — Draughts',
    'Celondim — Food',
    'Houses of Healing',
  ]);
});

test('omits tabs with zero matching recipes', () => {
  const tabs = groupRecipesIntoTabs(recipes, bands);
  const undermarchTabs = tabs.filter(t => t.tabName.startsWith("Thorin's Halls"));
  assert.equal(undermarchTabs.length, 0);
});

test('each tab contains only its matching recipes', () => {
  const tabs = groupRecipesIntoTabs(recipes, bands);
  const breeFood = tabs.find(t => t.tabName === 'Bree-land — Food');
  assert.equal(breeFood.recipes.length, 1);
  assert.equal(breeFood.recipes[0].id, 'a');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tools/recipe-browser/group-recipes.test.js`
Expected: FAIL with "Cannot find module './group-recipes'"

- [ ] **Step 3: Write minimal implementation**

```js
// tools/recipe-browser/group-recipes.js
const CLASS_ORDER = [
  { key: 'food', label: 'Food' },
  { key: 'draught', label: 'Draughts' },
];

// bands.json regions are verbose ("Celondim, western Ered Luin"); tabs use
// just the short place name before the first comma.
function shortRegion(region) {
  return region.split(',')[0].trim();
}

function groupRecipesIntoTabs(recipes, bands) {
  const tabs = [];

  const orderedBands = [...bands].sort((a, b) => shortRegion(a.region).localeCompare(shortRegion(b.region)));
  for (const band of orderedBands) {
    for (const cls of CLASS_ORDER) {
      const rows = recipes.filter(r => r.band === band.id && r.class === cls.key);
      if (rows.length > 0) {
        tabs.push({ tabName: `${shortRegion(band.region)} — ${cls.label}`, recipes: rows });
      }
    }
  }

  const hohRows = recipes.filter(r => r.band === 'all' && r.class === 'hoh');
  if (hohRows.length > 0) {
    tabs.push({ tabName: 'Houses of Healing', recipes: hohRows });
  }

  return tabs;
}

module.exports = { groupRecipesIntoTabs, shortRegion };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tools/recipe-browser/group-recipes.test.js`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add tools/recipe-browser/group-recipes.js tools/recipe-browser/group-recipes.test.js
git commit -m "[hc] Recipe browser: tab grouping by region/class"
```

---

### Task 3: Ingredient usage counting

**Files:**
- Create: `tools/recipe-browser/ingredient-usage.js`
- Test: `tools/recipe-browser/ingredient-usage.test.js`

**Interfaces:**
- Produces: `computeIngredientUsage(ingredients: object[], recipes: object[]) -> { id: string, name: string, region: string, source: string, recipeCount: number }[]`, sorted ascending by `recipeCount` then by `name`

- [ ] **Step 1: Write the failing test**

```js
// tools/recipe-browser/ingredient-usage.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const { computeIngredientUsage } = require('./ingredient-usage');

const ingredients = [
  { id: 'grain', name: 'Hearthgrain', region: 'Bree-land', source: 'farm' },
  { id: 'unused', name: 'Forgotten Root', region: 'Bree-land', source: 'forage' },
  { id: 'sloe', name: 'Sloe', region: 'Bree-land', source: 'forage' },
];

const recipes = [
  { heroIngredient: 'grain', ingredients: [{ id: 'grain', qty: 2 }, { id: 'sloe', qty: 1 }] },
  { heroIngredient: 'sloe', ingredients: [{ id: 'sloe', qty: 2 }] },
];

test('counts each recipe once per ingredient even if it appears as both hero and in the list', () => {
  const usage = computeIngredientUsage(ingredients, recipes);
  const grain = usage.find(u => u.id === 'grain');
  assert.equal(grain.recipeCount, 1);
});

test('sorts ascending by recipe count, unused ingredients first', () => {
  const usage = computeIngredientUsage(ingredients, recipes);
  assert.deepEqual(usage.map(u => u.id), ['unused', 'grain', 'sloe']);
});

test('ingredients never referenced by any recipe get a count of zero', () => {
  const usage = computeIngredientUsage(ingredients, recipes);
  const unused = usage.find(u => u.id === 'unused');
  assert.equal(unused.recipeCount, 0);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tools/recipe-browser/ingredient-usage.test.js`
Expected: FAIL with "Cannot find module './ingredient-usage'"

- [ ] **Step 3: Write minimal implementation**

```js
// tools/recipe-browser/ingredient-usage.js
function computeIngredientUsage(ingredients, recipes) {
  const counts = new Map();
  for (const ing of ingredients) counts.set(ing.id, 0);

  for (const recipe of recipes) {
    // Dedupe per recipe so hero-ingredient-also-in-list doesn't double count.
    const ids = new Set();
    if (recipe.heroIngredient) ids.add(recipe.heroIngredient);
    for (const line of recipe.ingredients || []) ids.add(line.id);
    for (const id of ids) {
      if (counts.has(id)) counts.set(id, counts.get(id) + 1);
    }
  }

  return ingredients
    .map(ing => ({
      id: ing.id,
      name: ing.name,
      region: ing.region,
      source: ing.source,
      recipeCount: counts.get(ing.id) || 0,
    }))
    .sort((a, b) => a.recipeCount - b.recipeCount || a.name.localeCompare(b.name));
}

module.exports = { computeIngredientUsage };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tools/recipe-browser/ingredient-usage.test.js`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add tools/recipe-browser/ingredient-usage.js tools/recipe-browser/ingredient-usage.test.js
git commit -m "[hc] Recipe browser: ingredient usage counting"
```

---

### Task 4: Display formatting helpers

**Files:**
- Create: `tools/recipe-browser/format.js`
- Test: `tools/recipe-browser/format.test.js`

**Interfaces:**
- Produces: `formatEffect(recipe: object) -> string`; `formatIngredientList(recipe: object, ingredientsById: Map<string, object>) -> string`; `ingredientsById(ingredients: object[]) -> Map<string, object>`

- [ ] **Step 1: Write the failing test**

```js
// tools/recipe-browser/format.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const { formatEffect, formatIngredientList, ingredientsById } = require('./format');

test('formatEffect formats a food recipe with only a primary stat', () => {
  const recipe = { class: 'food', primaryStat: 'vit', primaryBoost: 2 };
  assert.equal(formatEffect(recipe), '+2 Vitality');
});

test('formatEffect formats a food recipe with primary and secondary stats', () => {
  const recipe = { class: 'food', primaryStat: 'mig', primaryBoost: 3, secondaryStat: 'vit', secondaryBoost: 1 };
  assert.equal(formatEffect(recipe), '+3 Might, +1 Vitality');
});

test('formatEffect formats a draught recipe by its hazard effect', () => {
  const recipe = { class: 'draught', hazardEffect: 'potency' };
  assert.equal(formatEffect(recipe), 'Potency');
});

test('formatEffect formats a HoH recipe by its treated wound types', () => {
  const recipe = { class: 'hoh', treatsWoundTypes: ['physical', 'disease'] };
  assert.equal(formatEffect(recipe), 'Physical, Disease/Wasting');
});

test('formatIngredientList looks up ingredient names by id and shows quantities', () => {
  const ingredients = [{ id: 'sloe', name: 'Sloe' }, { id: 'honey', name: 'Forest Honey' }];
  const byId = ingredientsById(ingredients);
  const recipe = { ingredients: [{ id: 'sloe', qty: 2 }, { id: 'honey', qty: 1 }] };
  assert.equal(formatIngredientList(recipe, byId), 'Sloe ×2, Forest Honey ×1');
});

test('formatIngredientList falls back to the raw id if an ingredient is not found', () => {
  const byId = ingredientsById([]);
  const recipe = { ingredients: [{ id: 'mystery_root', qty: 1 }] };
  assert.equal(formatIngredientList(recipe, byId), 'mystery_root ×1');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tools/recipe-browser/format.test.js`
Expected: FAIL with "Cannot find module './format'"

- [ ] **Step 3: Write minimal implementation**

```js
// tools/recipe-browser/format.js
const STAT_LABELS = { mig: 'Might', agi: 'Agility', vit: 'Vitality', wil: 'Will', fat: 'Fate' };
const HAZARD_LABELS = { warmth: 'Warmth', alert: 'Alert', hale: 'Hale', potency: 'Potency', radiance: 'Radiance' };
const WOUND_LABELS = { physical: 'Physical', will: 'Will', corruption: 'Corruption', poison: 'Poison', disease: 'Disease/Wasting' };

function formatEffect(recipe) {
  if (recipe.class === 'food') {
    let effect = `+${recipe.primaryBoost} ${STAT_LABELS[recipe.primaryStat] || recipe.primaryStat}`;
    if (recipe.secondaryStat) {
      effect += `, +${recipe.secondaryBoost} ${STAT_LABELS[recipe.secondaryStat] || recipe.secondaryStat}`;
    }
    return effect;
  }
  if (recipe.class === 'draught') {
    return HAZARD_LABELS[recipe.hazardEffect] || recipe.hazardEffect || '';
  }
  if (recipe.class === 'hoh') {
    return (recipe.treatsWoundTypes || []).map(t => WOUND_LABELS[t] || t).join(', ');
  }
  return '';
}

function formatIngredientList(recipe, byId) {
  return (recipe.ingredients || [])
    .map(line => `${byId.get(line.id)?.name || line.id} ×${line.qty}`)
    .join(', ');
}

function ingredientsById(ingredients) {
  return new Map(ingredients.map(i => [i.id, i]));
}

module.exports = { formatEffect, formatIngredientList, ingredientsById };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tools/recipe-browser/format.test.js`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add tools/recipe-browser/format.js tools/recipe-browser/format.test.js
git commit -m "[hc] Recipe browser: effect and ingredient-list formatting"
```

---

### Task 5: HTML renderer

**Files:**
- Create: `tools/recipe-browser/render-html.js`
- Test: `tools/recipe-browser/render-html.test.js`

**Interfaces:**
- Consumes: nothing from earlier tasks — takes a generic shape so it has no knowledge of recipes/ingredients specifically
- Produces: `renderHtml(sections: { title: string, columns: string[], rows: string[][] }[]) -> string` (full HTML document); `escapeHtml(value: any) -> string`

- [ ] **Step 1: Write the failing test**

```js
// tools/recipe-browser/render-html.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const { renderHtml, escapeHtml } = require('./render-html');

test('escapeHtml escapes special characters', () => {
  assert.equal(escapeHtml('Salt & <Pepper> "Stew"'), 'Salt &amp; &lt;Pepper&gt; &quot;Stew&quot;');
});

test('renderHtml produces a valid-looking HTML document', () => {
  const html = renderHtml([{ title: 'Test Tab', columns: ['Name'], rows: [['Hearthgrain']] }]);
  assert.match(html, /^<!DOCTYPE html>/);
  assert.match(html, /<\/html>$/);
});

test('renderHtml includes one tab button per section', () => {
  const html = renderHtml([
    { title: 'Bree-land — Food', columns: ['Name'], rows: [] },
    { title: 'Houses of Healing', columns: ['Name'], rows: [] },
  ]);
  assert.match(html, /Bree-land — Food/);
  assert.match(html, /Houses of Healing/);
  assert.equal((html.match(/class="tab-button/g) || []).length, 2);
});

test('renderHtml shows the first table and hides the rest', () => {
  const html = renderHtml([
    { title: 'A', columns: ['X'], rows: [['1']] },
    { title: 'B', columns: ['X'], rows: [['2']] },
  ]);
  assert.match(html, /id="table-0" class="data-table" style="display:table"/);
  assert.match(html, /id="table-1" class="data-table" style="display:none"/);
});

test('renderHtml renders every row and column value', () => {
  const html = renderHtml([{ title: 'A', columns: ['Name', 'Qty'], rows: [['Sloe', '2'], ['Honey', '1']] }]);
  assert.match(html, /<td>Sloe<\/td>/);
  assert.match(html, /<td>2<\/td>/);
  assert.match(html, /<td>Honey<\/td>/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tools/recipe-browser/render-html.test.js`
Expected: FAIL with "Cannot find module './render-html'"

- [ ] **Step 3: Write minimal implementation**

```js
// tools/recipe-browser/render-html.js
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function renderTable(section, index) {
  const headerCells = section.columns
    .map((col, colIndex) => `<th onclick="sortTable(${index}, ${colIndex})">${escapeHtml(col)}</th>`)
    .join('');
  const bodyRows = section.rows
    .map(row => `<tr>${row.map(cell => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`)
    .join('');
  return `<table id="table-${index}" class="data-table" style="display:${index === 0 ? 'table' : 'none'}">
      <thead><tr>${headerCells}</tr></thead>
      <tbody>${bodyRows}</tbody>
    </table>`;
}

function renderHtml(sections) {
  const tabButtons = sections
    .map((s, i) => `<button class="tab-button${i === 0 ? ' active' : ''}" onclick="showTab(${i})">${escapeHtml(s.title)}</button>`)
    .join('');
  const tables = sections.map((s, i) => renderTable(s, i)).join('\n');

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>HearthCraft — Recipe &amp; Ingredient Browser</title>
<style>
  body { font-family: system-ui, sans-serif; margin: 0; padding: 16px; background: #1e1e1e; color: #e6e6e6; }
  h1 { font-size: 18px; font-weight: 600; }
  .tab-bar { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 12px; }
  .tab-button { background: #333; color: #e6e6e6; border: none; padding: 8px 12px; cursor: pointer; border-radius: 4px 4px 0 0; }
  .tab-button.active { background: #555; font-weight: 600; }
  table.data-table { border-collapse: collapse; width: 100%; }
  table.data-table th, table.data-table td { border: 1px solid #444; padding: 6px 10px; text-align: left; font-size: 13px; }
  table.data-table th { cursor: pointer; background: #2a2a2a; user-select: none; }
  table.data-table tr:nth-child(even) { background: #262626; }
</style>
</head>
<body>
<h1>HearthCraft — Recipe &amp; Ingredient Browser</h1>
<div class="tab-bar">${tabButtons}</div>
${tables}
<script>
function showTab(index) {
  document.querySelectorAll('.data-table').forEach((el, i) => {
    el.style.display = i === index ? 'table' : 'none';
  });
  document.querySelectorAll('.tab-button').forEach((el, i) => {
    el.classList.toggle('active', i === index);
  });
}

function sortTable(tableIndex, colIndex) {
  const table = document.getElementById('table-' + tableIndex);
  const tbody = table.querySelector('tbody');
  const rows = Array.from(tbody.querySelectorAll('tr'));
  const ascending = !(table.dataset.sortCol == colIndex && table.dataset.sortDir === 'asc');

  rows.sort((a, b) => {
    const aText = a.children[colIndex].textContent.trim();
    const bText = b.children[colIndex].textContent.trim();
    const aNum = parseFloat(aText);
    const bNum = parseFloat(bText);
    const bothNumeric = !isNaN(aNum) && !isNaN(bNum);
    const cmp = bothNumeric ? aNum - bNum : aText.localeCompare(bText);
    return ascending ? cmp : -cmp;
  });

  rows.forEach(row => tbody.appendChild(row));
  table.dataset.sortCol = colIndex;
  table.dataset.sortDir = ascending ? 'asc' : 'desc';
}
</script>
</body>
</html>`;
}

module.exports = { renderHtml, escapeHtml };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tools/recipe-browser/render-html.test.js`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add tools/recipe-browser/render-html.js tools/recipe-browser/render-html.test.js
git commit -m "[hc] Recipe browser: tabbed HTML renderer with sortable columns"
```

---

### Task 6: Entry point wiring + real-data generation

**Files:**
- Create: `tools/recipe-browser/generate.js`
- Test: `tools/recipe-browser/generate.test.js`
- Create (generated artifact, committed): `tools/recipe-browser/recipe-browser.html`

**Interfaces:**
- Consumes: `loadData` (Task 1), `groupRecipesIntoTabs` (Task 2), `computeIngredientUsage` (Task 3), `formatEffect`/`formatIngredientList`/`ingredientsById` (Task 4), `renderHtml` (Task 5)
- Produces: `generate(dataDir: string, outputPath: string) -> { recipeCount: number, ingredientCount: number, tabCount: number }`

- [ ] **Step 1: Write the failing test**

```js
// tools/recipe-browser/generate.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { generate } = require('./generate');

function makeFixtureDir() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'recipe-browser-gen-test-'));
  fs.writeFileSync(path.join(dir, 'bands.json'), JSON.stringify([
    { id: 'greycloaks', name: 'The Greycloaks', region: 'Bree-land' },
  ]));
  fs.writeFileSync(path.join(dir, 'ingredients.json'), JSON.stringify([
    { id: 'hearthgrain', name: 'Hearthgrain', region: 'Bree-land', source: 'farm' },
    { id: 'unused_root', name: 'Unused Root', region: 'Bree-land', source: 'forage' },
  ]));
  fs.writeFileSync(path.join(dir, 'recipes.json'), JSON.stringify([
    {
      id: 'hearthbread', name: 'Hearthbread', band: 'greycloaks', class: 'food',
      tier: 1, cookLevel: 1, heroIngredient: 'hearthgrain',
      ingredients: [{ id: 'hearthgrain', qty: 2 }],
      primaryStat: 'vit', primaryBoost: 2,
      description: 'Simple daily bread.',
    },
  ]));
  return dir;
}

test('generate writes an HTML file containing the expected tab and ingredient data', () => {
  const dir = makeFixtureDir();
  const outputPath = path.join(dir, 'out.html');
  const result = generate(dir, outputPath);

  assert.equal(result.recipeCount, 1);
  assert.equal(result.ingredientCount, 2);

  const html = fs.readFileSync(outputPath, 'utf8');
  assert.match(html, /Bree-land — Food/);
  assert.match(html, /Ingredient Usage/);
  assert.match(html, /<td>Hearthbread<\/td>/);
  assert.match(html, /<td>Unused Root<\/td>/);
});

test('generate against the real repo game data produces every expected tab', () => {
  const realDataDir = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'data');
  const outputPath = path.join(os.tmpdir(), 'recipe-browser-real-data-test.html');
  const result = generate(realDataDir, outputPath);

  assert.ok(result.recipeCount > 0);
  const html = fs.readFileSync(outputPath, 'utf8');
  assert.match(html, /Houses of Healing/);
  assert.match(html, /Ingredient Usage/);
  fs.unlinkSync(outputPath);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tools/recipe-browser/generate.test.js`
Expected: FAIL with "Cannot find module './generate'"

- [ ] **Step 3: Write minimal implementation**

```js
#!/usr/bin/env node
// HearthCraft recipe & ingredient data browser — generates a static HTML
// file from the game's JSON data. Run manually after editing recipe,
// ingredient, or band data. Never writes back to the JSON.
//
// Usage: node tools/recipe-browser/generate.js

const fs = require('fs');
const path = require('path');
const { loadData } = require('./load-data');
const { groupRecipesIntoTabs } = require('./group-recipes');
const { computeIngredientUsage } = require('./ingredient-usage');
const { formatEffect, formatIngredientList, ingredientsById } = require('./format');
const { renderHtml } = require('./render-html');

const DEFAULT_DATA_DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'data');
const DEFAULT_OUTPUT_PATH = path.join(__dirname, 'recipe-browser.html');

const RECIPE_COLUMNS = ['Name', 'Tier', 'Level', 'Hero Ingredient', 'Ingredients', 'Effect', 'Description'];
const INGREDIENT_COLUMNS = ['Name', 'Region', 'Source', 'Recipe Count'];

function levelFor(recipe) {
  return recipe.class === 'hoh' ? recipe.hohLevel : recipe.cookLevel;
}

function recipeRow(recipe, byId) {
  return [
    recipe.name,
    recipe.tier,
    levelFor(recipe),
    byId.get(recipe.heroIngredient)?.name || recipe.heroIngredient,
    formatIngredientList(recipe, byId),
    formatEffect(recipe),
    recipe.description,
  ];
}

function ingredientRow(row) {
  return [row.name, row.region, row.source, row.recipeCount];
}

function generate(dataDir, outputPath) {
  const { recipes, ingredients, bands } = loadData(dataDir);
  const byId = ingredientsById(ingredients);

  const recipeTabs = groupRecipesIntoTabs(recipes, bands).map(tab => ({
    title: tab.tabName,
    columns: RECIPE_COLUMNS,
    rows: [...tab.recipes]
      .sort((a, b) => a.tier - b.tier || levelFor(a) - levelFor(b))
      .map(r => recipeRow(r, byId)),
  }));

  const usageRows = computeIngredientUsage(ingredients, recipes);
  const usageTab = {
    title: 'Ingredient Usage',
    columns: INGREDIENT_COLUMNS,
    rows: usageRows.map(ingredientRow),
  };

  const html = renderHtml([...recipeTabs, usageTab]);
  fs.writeFileSync(outputPath, html);

  return { recipeCount: recipes.length, ingredientCount: ingredients.length, tabCount: recipeTabs.length + 1 };
}

if (require.main === module) {
  const result = generate(DEFAULT_DATA_DIR, DEFAULT_OUTPUT_PATH);
  console.log(`Wrote ${result.ingredientCount} ingredients and ${result.recipeCount} recipes across ${result.tabCount} tabs to ${DEFAULT_OUTPUT_PATH}`);
}

module.exports = { generate };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tools/recipe-browser/generate.test.js`
Expected: PASS (2 tests)

- [ ] **Step 5: Generate the real output file and commit everything**

```bash
node tools/recipe-browser/generate.js
git add tools/recipe-browser/generate.js tools/recipe-browser/generate.test.js tools/recipe-browser/recipe-browser.html
git commit -m "[hc] Recipe browser: wire entry point, generate initial output"
```

- [ ] **Step 6: Full test suite sanity check**

Run: `node --test tools/recipe-browser/*.test.js`
Expected: PASS (all tests across all six files, 26 total)

(Note: a bare `node --test tools/recipe-browser/` fails on Node v26 — it treats the directory as a module path instead of discovering `*.test.js` files within it. The glob form above is the one that actually works.)

---

## Manual Verification

Open `tools/recipe-browser/recipe-browser.html` directly in a browser (double-click or `xdg-open`). Confirm:
- Tab buttons appear for Bree-land — Food, Bree-land — Draughts, Celondim — Food, Celondim — Draughts, Thorin's Halls — Food, Thorin's Halls — Draughts, Houses of Healing, Ingredient Usage (only tabs with data appear)
- Clicking a tab switches the visible table
- Clicking a column header sorts that table by that column, toggling ascending/descending on repeated clicks
- Ingredient Usage tab shows zero-count ingredients at the top by default
