// HearthCraft — shared food and recipe model
// Used by both run_sim.js (Node) and hearthcraft_fight_sim.html (browser).
// Node:    const FM = require('./food_model');
// Browser: <script src="food_model.js">  → window.FOOD_MODEL

const FOOD_MODEL = (function () {

  // ── Ingredient quality grades ────────────────────────────────────────────────
  // Mirrors Grade.kt ordinals. CRUDE is the zero-point baseline — no bonus, no penalty.
  // gradeStep values are TODO:TUNE placeholders matching QualityUtils.kt.
  const GRADE_NAMES  = ["Crude", "Common", "Fine", "Superb", "Pristine"];
  const GRADE_STEPS  = [0, 0.5, 1.0, 1.75, 2.5];  // TODO:TUNE — additive stat bonus per grade

  // Apply grade bonus to an authored stat boost.
  // Only adds a step when the stat is a matching primary or secondary (base > 0).
  function effectiveStatBoost(authoredBoost, gradeOrdinal) {
    if (authoredBoost === 0) return 0;
    return authoredBoost + (GRADE_STEPS[gradeOrdinal] || 0);
  }

  function gradeName(ordinal) { return GRADE_NAMES[ordinal] || "Crude"; }

  // ── HP/s tier table ─────────────────────────────────────────────────────────
  const TIER_TABLE = [
    { tier:1, title:"Hearthkeeper", lo:1,  hi:4,  hpsLo:5.0, hpsHi:5.6  },
    { tier:2, title:"Initiate",     lo:5,  hi:9,  hpsLo:6.0, hpsHi:9.0  },
    { tier:3, title:"Apprentice",   lo:10, hi:15, hpsLo:10.0, hpsHi:17.0 },
    { tier:4, title:"Journeyman",   lo:16, hi:22, hpsLo:18.0, hpsHi:30.0 },
    { tier:5, title:"Adept",        lo:23, hi:30, hpsLo:31.0, hpsHi:46.0 },
    { tier:6, title:"Master",       lo:31, hi:40, hpsLo:47.0, hpsHi:76.0 },
    { tier:7, title:"Grandmaster",  lo:41, hi:50, hpsLo:77.0, hpsHi:120.0},
  ];
  // Linear (1.0) gives evenly-spaced HP/s steps within each tier.
  // T1 (Hearthkeeper) CL1-4 = 5.0/5.2/5.4/5.6 HP/s. Each tier boundary is +1 HP/s above
  // the previous tier's ceiling (T1→T2 is +0.4, intentionally tight — T1 was compressed by design).
  const SCURVE_P = 1.0;

  function recipeLevelToHps(rl) {
    const c = Math.max(1, Math.min(50, Math.round(rl)));
    for (const t of TIER_TABLE) {
      if (c >= t.lo && c <= t.hi) {
        const frac = (c - t.lo) / Math.max(1, t.hi - t.lo);
        return +(t.hpsLo + Math.pow(frac, SCURVE_P) * (t.hpsHi - t.hpsLo)).toFixed(1);
      }
    }
    return 5;
  }

  function recipeLevelLabel(rl) {
    const c = Math.max(1, Math.min(50, Math.round(rl)));
    for (const t of TIER_TABLE) {
      if (c >= t.lo && c <= t.hi) return `Lv${c} ${t.title}`;
    }
    return `Lv${c}`;
  }

  // ── Recipe definitions ───────────────────────────────────────────────────────
  // Mirrors app/src/main/assets/data/recipes.json exactly.
  // primaryBoost / secondaryBoost are the authored stat values (flat, not FL-scaled).
  // hazardEffect = string key for draught recipes (no stat bonus).
  const RECIPES = [
    // ── Greycloaks ──────────────────────────────────────────────────────────
    { id:"ploughmans_plate",       name:"Ploughman's Plate",       cookLevel:1,  primaryStat:"mig", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"hedgerow_hand_pie",      name:"Hedgerow Hand-pie",        cookLevel:1,  primaryStat:"agi", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"goodmans_stew",          name:"Goodman's Stew",           cookLevel:1,  primaryStat:"vit", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"brookcress_bannock",     name:"Brookcress Bannock",       cookLevel:1,  primaryStat:"wil", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"hearthbread",            name:"Hearthbread",              cookLevel:1,  primaryStat:"vit", primaryBoost:2,  secondaryStat:"mig", secondaryBoost:1 },
    { id:"wanderers_supper",       name:"Wanderer's Supper",        cookLevel:1,  primaryStat:"agi", primaryBoost:2,  secondaryStat:"mig", secondaryBoost:1 },
    { id:"fernys_treacle",         name:"Ferny's Treacle",          cookLevel:1,  primaryStat:"vit", primaryBoost:-2, secondaryStat:null,  secondaryBoost:0 },
    { id:"sloe_bitters",           name:"Sloe Bitters",             cookLevel:5,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"potency" },
    { id:"hearth_and_hops",        name:"Hearth and Hops",          cookLevel:5,  primaryStat:"mig", primaryBoost:3,  secondaryStat:"vit", secondaryBoost:1 },
    { id:"chetwood_rabbit_roast",  name:"Chetwood Rabbit Roast",    cookLevel:5,  primaryStat:"agi", primaryBoost:3,  secondaryStat:"mig", secondaryBoost:1 },
    { id:"field_and_fen_potage",   name:"Field and Fen Potage",     cookLevel:5,  primaryStat:"vit", primaryBoost:3,  secondaryStat:"wil", secondaryBoost:1 },
    { id:"bilberry_tonic",         name:"Bilberry Tonic",           cookLevel:3,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"alert" },
    { id:"yarrow_draught",         name:"Yarrow Draught",           cookLevel:4,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"hale" },
    { id:"honey_oat_cake",         name:"Honey Oat Cake",           cookLevel:6,  primaryStat:"wil", primaryBoost:3,  secondaryStat:"agi", secondaryBoost:1 },
    { id:"ember_porridge",         name:"Ember Porridge",           cookLevel:3,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"warmth" },
    { id:"heartflame_broth",       name:"Heartflame Broth",         cookLevel:6,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"warmth" },
    { id:"trout_and_mushroom_pan", name:"Trout and Mushroom Pan",   cookLevel:10, primaryStat:"mig", primaryBoost:4,  secondaryStat:"agi", secondaryBoost:2 },
    { id:"rangers_fare",           name:"Ranger's Fare",            cookLevel:10, primaryStat:"mig", primaryBoost:4,  secondaryStat:"vit", secondaryBoost:2 },
    { id:"restorative_broth",      name:"Restorative Broth",        cookLevel:8,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"hale" },
    { id:"athelas_infusion",       name:"Athelas Infusion",         cookLevel:12, primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"hale" },

    // ── Mithlost ────────────────────────────────────────────────────────────
    { id:"hale_loaf",              name:"Hale Loaf",                cookLevel:1,  primaryStat:"mig", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"leafwater_salad",        name:"Leafwater Salad",          cookLevel:1,  primaryStat:"agi", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"springwater_broth",      name:"Springwater Broth",        cookLevel:1,  primaryStat:"vit", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"starflower_wafer",       name:"Starflower Wafer",         cookLevel:1,  primaryStat:"wil", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"contemplative_tea",      name:"Contemplative Tea",        cookLevel:1,  primaryStat:"wil", primaryBoost:2,  secondaryStat:"agi", secondaryBoost:1 },
    { id:"keenwater_tincture",     name:"Keenwater Tincture",       cookLevel:5,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"potency" },
    { id:"saltfish_and_kelp",      name:"Saltfish and Kelp",        cookLevel:5,  primaryStat:"mig", primaryBoost:3,  secondaryStat:"vit", secondaryBoost:1 },
    { id:"silver_leek_omelette",   name:"Silver Leek Omelette",     cookLevel:5,  primaryStat:"agi", primaryBoost:3,  secondaryStat:"wil", secondaryBoost:1 },
    { id:"fennel_and_cheese_bake", name:"Fennel and Cheese Bake",   cookLevel:6,  primaryStat:"vit", primaryBoost:3,  secondaryStat:"wil", secondaryBoost:1 },
    { id:"moonpetal_tisane",       name:"Moonpetal Tisane",         cookLevel:6,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"radiance" },
    { id:"silvern_wake_draught",   name:"Silvern Wake Draught",     cookLevel:4,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"alert" },

    // ── Undermarch ──────────────────────────────────────────────────────────
    { id:"delvers_hash",           name:"Delver's Hash",            cookLevel:1,  primaryStat:"mig", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"quickfoot_skewer",       name:"Quickfoot Skewer",         cookLevel:1,  primaryStat:"agi", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"stoneroot_stew",         name:"Stoneroot Stew",           cookLevel:1,  primaryStat:"vit", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"rockmoss_crispbread",    name:"Rockmoss Crispbread",      cookLevel:1,  primaryStat:"wil", primaryBoost:2,  secondaryStat:null,  secondaryBoost:0 },
    { id:"ironbite_stout",         name:"Ironbite Stout",           cookLevel:5,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"potency" },
    { id:"marrow_and_malt_broth",  name:"Marrow and Malt Broth",    cookLevel:5,  primaryStat:"mig", primaryBoost:3,  secondaryStat:"vit", secondaryBoost:1 },
    { id:"goat_and_ironfoot",      name:"Goat and Ironfoot",        cookLevel:5,  primaryStat:"agi", primaryBoost:3,  secondaryStat:"mig", secondaryBoost:1 },
    { id:"deepcap_and_malt_loaf",  name:"Deepcap and Malt Loaf",    cookLevel:6,  primaryStat:"vit", primaryBoost:3,  secondaryStat:"mig", secondaryBoost:1 },
    { id:"stonewort_cure",         name:"Stonewort Cure",           cookLevel:4,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"hale" },
    { id:"coldwort_warming_ale",   name:"Coldwort Warming Ale",     cookLevel:5,  primaryStat:null,  primaryBoost:0,  secondaryStat:null,  secondaryBoost:0, hazardEffect:"warmth" },
  ];

  // ── Stat bonus helpers ───────────────────────────────────────────────────────
  // Bonuses are flat authored values from recipes.json — no FL scaling.
  // Call statBonusesFor(recipeId) to get { mig:3, vit:1 } etc.

  function statBonusesFor(recipeId) {
    const r = RECIPES.find(r => r.id === recipeId);
    if (!r || !r.primaryStat || r.primaryBoost === 0) return {};
    const bonuses = {};
    if (r.primaryStat  && r.primaryBoost  !== 0) bonuses[r.primaryStat]  = r.primaryBoost;
    if (r.secondaryStat && r.secondaryBoost !== 0) bonuses[r.secondaryStat] = (bonuses[r.secondaryStat] || 0) + r.secondaryBoost;
    return bonuses;
  }

  // Legacy FL-based helpers kept for backwards compatibility with old sim scripts.
  // New code should use statBonusesFor() instead.
  function flFromCookLevel(recipeId, cookLevel) {
    const r = RECIPES.find(r => r.id === recipeId);
    if (!r || cookLevel < r.cookLevel) return 0;
    return cookLevel - r.cookLevel + 1;
  }

  function statBonusesAt(recipeId, fl) {
    if (fl < 1) return {};
    return statBonusesFor(recipeId);
  }

  function statBonusesForCookLevel(recipeId, cookLevel) {
    return statBonusesAt(recipeId, flFromCookLevel(recipeId, cookLevel));
  }

  // HP/s for a recipe at a given cooking level.
  function hpsAt(recipeId, cookLevel) {
    const r = RECIPES.find(r => r.id === recipeId);
    if (!r || cookLevel < r.levelRequired) return 0;
    return recipeLevelToHps(cookLevel);
  }

  // Human-readable summary of bonuses for display.
  function bonusSummary(recipeId, cookLevel) {
    const fl = flFromCookLevel(recipeId, cookLevel);
    if (fl < 1) return "(not yet unlocked)";
    const bonuses = statBonusesAt(recipeId, fl);
    const parts = Object.entries(bonuses).map(([s, n]) => `+${n} ${s.toUpperCase()}`);
    return parts.length ? `FL${fl}: ${parts.join(", ")}` : `FL${fl}: HP/s only`;
  }

  const FM = {
    TIER_TABLE, SCURVE_P, RECIPES,
    GRADE_NAMES, GRADE_STEPS,
    recipeLevelToHps, recipeLevelLabel,
    flFromCookLevel, statBonusesAt, statBonusesForCookLevel, statBonusesFor,
    hpsAt, bonusSummary,
    effectiveStatBoost, gradeName,
  };

  if (typeof module !== "undefined" && module.exports) module.exports = FM;
  return FM;
})();
