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
