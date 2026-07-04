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
