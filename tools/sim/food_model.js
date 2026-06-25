// HearthCraft — shared food and recipe model
// Used by both run_sim.js (Node) and hearthcraft_fight_sim.html (browser).
// Node:    const FM = require('./food_model');
// Browser: <script src="food_model.js">  → window.FOOD_MODEL

const FOOD_MODEL = (function () {

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
  // Mirrors app/src/main/assets/data/recipes.json stat fields.
  // primaryStat / secondaryStat null = antidote/healing recipe (no stat bonus yet).
  const RECIPES = [
    { id:"hearthbread",       name:"Hearthbread",        levelRequired:1,  primaryStat:"vit", secondaryStat:"mig" },
    { id:"wanderers_supper",  name:"Wanderer's Supper",  levelRequired:1,  primaryStat:"agi", secondaryStat:"mig" },
    { id:"contemplative_tea", name:"Contemplative Tea",  levelRequired:1,  primaryStat:"wil", secondaryStat:"agi" },
    { id:"rangers_fare",      name:"Ranger's Fare",      levelRequired:1,  primaryStat:"mig", secondaryStat:"vit" },
    { id:"ember_porridge",    name:"Ember Porridge",     levelRequired:1,  primaryStat:null,  secondaryStat:null  },
    { id:"bilberry_tea",      name:"Bilberry Tea",       levelRequired:3,  primaryStat:null,  secondaryStat:null  },
    { id:"iron_stew",         name:"Iron Stew",          levelRequired:6,  primaryStat:"vit", secondaryStat:null  },
    { id:"stormcap_saute",    name:"Stormcap Sauté",     levelRequired:6,  primaryStat:"agi", secondaryStat:null  },
    { id:"moonpetal_tisane",  name:"Moonpetal Tisane",   levelRequired:6,  primaryStat:"wil", secondaryStat:null  },
    { id:"heartflame_broth",  name:"Heartflame Broth",   levelRequired:6,  primaryStat:null,  secondaryStat:null  },
    { id:"restorative_broth", name:"Restorative Broth",  levelRequired:8,  primaryStat:null,  secondaryStat:null  },
    { id:"athelas_infusion",  name:"Athelas Infusion",   levelRequired:12, primaryStat:null,  secondaryStat:null  },
  ];

  // ── FL helpers ───────────────────────────────────────────────────────────────

  // Cooking level relative to recipe: FL1 = first cook (levelRequired), FL2 = +1, etc.
  function flFromCookLevel(recipeId, cookLevel) {
    const r = RECIPES.find(r => r.id === recipeId);
    if (!r || cookLevel < r.levelRequired) return 0; // not yet unlocked
    return cookLevel - r.levelRequired + 1;
  }

  // Stat bonuses at a given food level (FL 1–4+). Returns object like { vit:2, fat:1 }.
  // FL3+ unlocks +2 primary; FL4+ unlocks +1 secondary.
  function statBonusesAt(recipeId, fl) {
    const r = RECIPES.find(r => r.id === recipeId);
    if (!r || !r.primaryStat || fl < 1) return {};
    const bonuses = {};
    bonuses[r.primaryStat] = fl >= 3 ? 2 : 1;
    if (fl >= 4 && r.secondaryStat) {
      bonuses[r.secondaryStat] = (bonuses[r.secondaryStat] || 0) + 1;
    }
    return bonuses;
  }

  // Convenience: bonuses derived directly from cooking level.
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
    recipeLevelToHps, recipeLevelLabel,
    flFromCookLevel, statBonusesAt, statBonusesForCookLevel, hpsAt, bonusSummary,
  };

  if (typeof module !== "undefined" && module.exports) module.exports = FM;
  return FM;
})();
