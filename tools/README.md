# HearthCraft Tools

## Data generator

`generate_data.py` reads the master spreadsheets and regenerates the game data JSON files.

```
python3 tools/generate_data.py
```

Requires: `pip install openpyxl`

### Source spreadsheets (edit these)

| File | Drives |
|------|--------|
| `tools/data/hearthcraft_ingredients.xlsx` | `app/…/data/ingredients.json` |
| `tools/data/hearthcraft_food_master.xlsx` | `app/…/data/recipes.json` |

### Before running the generator — required spreadsheet updates

These changes were made directly to the JSON and need to be back-ported
to the spreadsheets before the generator is used:

**hearthcraft_food_master.xlsx → JSON - Recipes sheet:**
- `ember_porridge` : set `tier` = 1  (was 3)
- `rangers_fare`   : set `cookLevel` = 10  (was 1)

**hearthcraft_ingredients.xlsx → JSON Schema sheet:**
- Add row for `willowherb` (used in Field and Fen Potage):
  `willowherb | Willowherb | Wildwood / Cardolan | c | forage | vit | wil | | forage | false | Tall pink herb of riverbanks...`

### Adding ingredient categories (for the recipe discovery system)

Add a `category` column to the JSON Schema sheet in `hearthcraft_ingredients.xlsx`.
Valid values: `grain  liquid  protein  vegetable  mushroom  herb  spice  fat  sweetener  binder  forage  special`

The generator picks it up automatically once the column exists.

### Sim tools

`tools/sim/` contains the encounter simulator (HTML + JS) and reference workbooks.
