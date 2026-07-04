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
