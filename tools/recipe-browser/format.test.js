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

test('formatEffect formats a food-class recipe whose actual data is a hazard effect, not a stat', () => {
  const recipe = { class: 'food', hazardEffect: 'warmth' };
  assert.equal(formatEffect(recipe), 'Warmth');
});

test('formatEffect formats a draught-class recipe whose actual data is stats, not a hazard effect', () => {
  const recipe = { class: 'draught', primaryStat: 'wil', primaryBoost: 2, secondaryStat: 'agi', secondaryBoost: 1 };
  assert.equal(formatEffect(recipe), '+2 Will, +1 Agility');
});
