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
