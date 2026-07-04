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
