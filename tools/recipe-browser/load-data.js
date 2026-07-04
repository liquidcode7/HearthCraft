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
