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

  const regionToBandIds = new Map();
  for (const band of bands) {
    const region = shortRegion(band.region);
    const ids = regionToBandIds.get(region) || [];
    ids.push(band.id);
    regionToBandIds.set(region, ids);
  }
  const orderedRegions = [...regionToBandIds.keys()].sort((a, b) => a.localeCompare(b));

  for (const region of orderedRegions) {
    const bandIds = regionToBandIds.get(region);
    for (const cls of CLASS_ORDER) {
      const rows = recipes.filter(r => bandIds.includes(r.band) && r.class === cls.key);
      if (rows.length > 0) {
        tabs.push({ tabName: `${region} — ${cls.label}`, recipes: rows });
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
