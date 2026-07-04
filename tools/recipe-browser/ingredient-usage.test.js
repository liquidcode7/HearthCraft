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
