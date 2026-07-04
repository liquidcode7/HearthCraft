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
