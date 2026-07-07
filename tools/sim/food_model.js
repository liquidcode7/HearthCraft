// HearthCraft — food model: grade constants and stat bonus helpers
// HP/s removed in redesign (Model B). Food gives stat boosts only.
// Node:    const FM = require('./food_model');
// Browser: <script src="food_model.js">  → window.FOOD_MODEL

const FOOD_MODEL = (function () {

  // Grade ordinals mirror Grade.kt. CRUDE = 0.
  const GRADE_NAMES = ["Crude", "Common", "Fine", "Superb", "Pristine"];
  // Multiplicative grade bonus — mirrors app/.../data/model/QualityUtils.kt's
  // GRADE_MULTIPLIER exactly (still marked TODO:TUNE there; keep in sync).
  const GRADE_MULTIPLIER = [0.8, 0.9, 1.0, 1.15, 1.3];

  function effectiveStatBoost(authoredBoost, gradeOrdinal) {
    if (authoredBoost === 0) return 0;
    return authoredBoost * (GRADE_MULTIPLIER[gradeOrdinal] ?? 1.0);
  }

  function gradeName(ordinal) { return GRADE_NAMES[ordinal] || "Crude"; }

  // Recipe definitions — mirrors app/src/main/assets/data/recipes.json
  // primaryBoost / secondaryBoost: flat authored stat values.
  // hazardEffect: string key for draught recipes.
  const RECIPES = [
    // ── Greycloaks ──────────────────────────────────────────────────────────
    { id:"ploughmans_plate",       name:"Ploughman's Plate",       cookLevel:1,  primaryStat:"mig", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"hedgerow_hand_pie",      name:"Hedgerow Hand-pie",        cookLevel:1,  primaryStat:"agi", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"goodmans_stew",          name:"Goodman's Stew",           cookLevel:1,  primaryStat:"vit", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"brookcress_bannock",     name:"Brookcress Bannock",       cookLevel:1,  primaryStat:"wil", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"hearthbread",            name:"Hearthbread",              cookLevel:1,  primaryStat:"vit", primaryBoost:2 },
    { id:"wanderers_supper",       name:"Wanderer's Supper",        cookLevel:1,  primaryStat:"agi", primaryBoost:2 },
    { id:"fernys_treacle",         name:"Ferny's Treacle",          cookLevel:1,  primaryStat:"vit", primaryBoost:-2, secondaryStat:null,  secondaryBoost:0 },
    { id:"sloe_bitters",           name:"Sloe Bitters",             cookLevel:5,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"potency" },
    { id:"hearth_and_hops",        name:"Hearth and Hops",          cookLevel:5,  primaryStat:"mig", primaryBoost:4,  secondaryStat:"vit", secondaryBoost:2 },
    { id:"chetwood_rabbit_roast",  name:"Chetwood Rabbit Roast",    cookLevel:5,  primaryStat:"agi", primaryBoost:4,  secondaryStat:"mig", secondaryBoost:2 },
    { id:"field_and_fen_potage",   name:"Field and Fen Potage",     cookLevel:5,  primaryStat:"vit", primaryBoost:4,  secondaryStat:"wil", secondaryBoost:2 },
    { id:"bilberry_tonic",         name:"Bilberry Tonic",           cookLevel:3,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"alert" },
    { id:"yarrow_draught",         name:"Yarrow Draught",           cookLevel:4,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"hale" },
    { id:"honey_oat_cake",         name:"Honey Oat Cake",           cookLevel:6,  primaryStat:"wil", primaryBoost:4,  secondaryStat:"agi", secondaryBoost:2 },
    { id:"ember_porridge",         name:"Ember Porridge",           cookLevel:3,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"warmth" },
    { id:"heartflame_broth",       name:"Heartflame Broth",         cookLevel:6,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"warmth" },
    { id:"trout_and_mushroom_pan", name:"Trout and Mushroom Pan",   cookLevel:10, primaryStat:"mig", primaryBoost:6,  secondaryStat:"agi", secondaryBoost:3 },
    { id:"rangers_fare",           name:"Ranger's Fare",            cookLevel:10, primaryStat:"mig", primaryBoost:8,  secondaryStat:"vit", secondaryBoost:4 },
    { id:"restorative_broth",      name:"Restorative Broth",        cookLevel:8,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"hale" },
    { id:"athelas_infusion",       name:"Athelas Infusion",         cookLevel:12, primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"hale" },
    // ── Mithlost ──────────────────────────────────────────────────────────
    { id:"hale_loaf",              name:"Hale Loaf",                cookLevel:1,  primaryStat:"mig", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"leafwater_salad",        name:"Leafwater Salad",          cookLevel:1,  primaryStat:"agi", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"springwater_broth",      name:"Springwater Broth",        cookLevel:1,  primaryStat:"vit", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"starflower_wafer",       name:"Starflower Wafer",         cookLevel:1,  primaryStat:"wil", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"contemplative_tea",      name:"Contemplative Tea",        cookLevel:1,  primaryStat:"wil", primaryBoost:2,  secondaryStat:"agi", secondaryBoost:1 },
    { id:"keenwater_tincture",     name:"Keenwater Tincture",       cookLevel:5,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"potency" },
    { id:"saltfish_and_kelp",      name:"Saltfish and Kelp",        cookLevel:5,  primaryStat:"mig", primaryBoost:4,  secondaryStat:"vit", secondaryBoost:2 },
    { id:"silver_leek_omelette",   name:"Silver Leek Omelette",     cookLevel:5,  primaryStat:"agi", primaryBoost:4,  secondaryStat:"wil", secondaryBoost:2 },
    { id:"fennel_and_cheese_bake", name:"Fennel and Cheese Bake",   cookLevel:6,  primaryStat:"vit", primaryBoost:4,  secondaryStat:"wil", secondaryBoost:2 },
    { id:"moonpetal_tisane",       name:"Moonpetal Tisane",         cookLevel:6,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"radiance" },
    { id:"silvern_wake_draught",   name:"Silvern Wake Draught",     cookLevel:4,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"alert" },
    // ── Undermarch ────────────────────────────────────────────────────────
    { id:"delvers_hash",           name:"Delver's Hash",            cookLevel:1,  primaryStat:"mig", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"quickfoot_skewer",       name:"Quickfoot Skewer",         cookLevel:1,  primaryStat:"agi", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"stoneroot_stew",         name:"Stoneroot Stew",           cookLevel:1,  primaryStat:"vit", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"rockmoss_crispbread",    name:"Rockmoss Crispbread",      cookLevel:1,  primaryStat:"wil", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"ironbite_stout",         name:"Ironbite Stout",           cookLevel:5,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"potency" },
    { id:"marrow_and_malt_broth",  name:"Marrow and Malt Broth",    cookLevel:5,  primaryStat:"mig", primaryBoost:4,  secondaryStat:"vit", secondaryBoost:2 },
    { id:"goat_and_ironfoot",      name:"Goat and Ironfoot",        cookLevel:5,  primaryStat:"agi", primaryBoost:4,  secondaryStat:"mig", secondaryBoost:2 },
    { id:"deepcap_and_malt_loaf",  name:"Deepcap and Malt Loaf",    cookLevel:6,  primaryStat:"vit", primaryBoost:4,  secondaryStat:"mig", secondaryBoost:2 },
    { id:"stonewort_cure",         name:"Stonewort Cure",           cookLevel:4,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"hale" },
    { id:"coldwort_warming_ale",   name:"Coldwort Warming Ale",     cookLevel:5,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"warmth" },
  ];

  function statBonusesFor(recipeId) {
    const r = RECIPES.find(r => r.id === recipeId);
    if (!r || !r.primaryStat || r.primaryBoost === 0) return {};
    const bonuses = {};
    if (r.primaryStat  && r.primaryBoost  !== 0) bonuses[r.primaryStat]  = r.primaryBoost;
    if (r.secondaryStat && r.secondaryBoost !== 0) bonuses[r.secondaryStat] = (bonuses[r.secondaryStat] || 0) + r.secondaryBoost;
    return bonuses;
  }

  const FM = {
    RECIPES, GRADE_NAMES, GRADE_MULTIPLIER,
    statBonusesFor, effectiveStatBoost, gradeName,
  };

  if (typeof module !== "undefined" && module.exports) module.exports = FM;
  return FM;
})();
