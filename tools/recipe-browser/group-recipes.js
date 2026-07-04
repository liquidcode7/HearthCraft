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
