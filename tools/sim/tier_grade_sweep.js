#!/usr/bin/env node
/* tier_grade_sweep.js — HearthCraft tier-vs-grade balance sweep.
 *
 * Answers the "stats barely increase with next tier vs. grade quality" question:
 * for every food recipe with a primaryBoost/secondaryBoost, print the effective
 * stat value at all five grades (Crude/Common/Fine/Superb/Pristine) using
 * food_model.js's effectiveStatBoost (now the correct multiplicative formula,
 * matching QualityUtils.kt's GRADE_MULTIPLIER exactly — see commit c374a7f).
 *
 * Then, for every band+stat lineage across tiers, flag any case where a LOWER
 * tier's Pristine result meets or beats a HIGHER tier's Common/Fine result at
 * the same stat. That is the exact comparison item #3 of the audit flagged,
 * now reproduced with numbers that actually match the shipped app.
 *
 * This script does NOT decide or apply a balance fix. It only produces data
 * for Wes to look at (widen tier deltas? narrow grade spread? leave it?).
 *
 * Recipe tier/band data is read straight from the shipped
 * app/src/main/assets/data/recipes.json (source of truth for tier/band —
 * food_model.js's embedded RECIPES mirror omits those two fields). Grade
 * math (GRADE_MULTIPLIER, effectiveStatBoost) comes from food_model.js so it
 * can never drift from the app's QualityUtils.kt.
 *
 * Run: node tools/sim/tier_grade_sweep.js
 */

'use strict';
const fs = require('fs');
const path = require('path');
const FM = require('./food_model');

const RECIPES_JSON_PATH = path.join(__dirname, '../../app/src/main/assets/data/recipes.json');
const recipes = JSON.parse(fs.readFileSync(RECIPES_JSON_PATH, 'utf8'));

const GRADE_NAMES = FM.GRADE_NAMES; // ["Crude","Common","Fine","Superb","Pristine"]
const NUM_GRADES = GRADE_NAMES.length;

// ── Formatting helpers (conventions borrowed from xp_lab.js) ─────────────────
const pL = (s, n) => String(s).padStart(n);
const pR = (s, n) => String(s).padEnd(n);
const fmt = (n) => n.toFixed(2);

// ── Gather stat contributions ────────────────────────────────────────────────
// Each recipe can contribute to up to two stats: primary and secondary.
// A "contribution" = one (band, stat, tier, recipe, role, authoredBoost) row.
const contributions = [];
for (const r of recipes) {
  if (r.primaryStat && r.primaryBoost) {
    contributions.push({
      band: r.band, tier: r.tier, id: r.id, name: r.name,
      stat: r.primaryStat, boost: r.primaryBoost, role: 'primary',
      penalty: !!r.penalty,
    });
  }
  if (r.secondaryStat && r.secondaryBoost) {
    contributions.push({
      band: r.band, tier: r.tier, id: r.id, name: r.name,
      stat: r.secondaryStat, boost: r.secondaryBoost, role: 'secondary',
      penalty: !!r.penalty,
    });
  }
}

// Precompute effective values at all 5 grades for each contribution.
for (const c of contributions) {
  c.effective = [];
  for (let g = 0; g < NUM_GRADES; g++) {
    c.effective.push(FM.effectiveStatBoost(c.boost, g));
  }
}

// ── Group by band+stat, sorted by tier ───────────────────────────────────────
const groups = new Map(); // key "band|stat" -> [contributions...]
for (const c of contributions) {
  const key = `${c.band}|${c.stat}`;
  if (!groups.has(key)) groups.set(key, []);
  groups.get(key).push(c);
}
for (const list of groups.values()) {
  list.sort((a, b) => a.tier - b.tier || a.id.localeCompare(b.id));
}

// ── Output 1: full tier x grade table, grouped by band + stat ────────────────
console.log('\n══ 1. EFFECTIVE STAT VALUE BY TIER x GRADE ═══════════════════════════════════════════════');
console.log('   (authored boost x GRADE_MULTIPLIER [0.7, 0.85, 1.0, 1.3, 1.7])\n');

const NAME_COL = 30;
const GRADE_COL = 9;

const sortedKeys = [...groups.keys()].sort();
for (const key of sortedKeys) {
  const [band, stat] = key.split('|');
  const list = groups.get(key);
  console.log(`  ── ${band} / ${stat} ${'─'.repeat(Math.max(0, 60 - band.length - stat.length))}`);
  console.log(
    '     ' + pR('recipe (tier, role)', NAME_COL) +
    GRADE_NAMES.map(g => pL(g, GRADE_COL)).join('')
  );
  for (const c of list) {
    const label = `${c.name} (T${c.tier}, ${c.role}${c.penalty ? ', PENALTY' : ''})`;
    console.log(
      '     ' + pR(label, NAME_COL) +
      c.effective.map(v => pL(fmt(v), GRADE_COL)).join('')
    );
  }
  console.log('');
}

// ── Output 2: cross-tier flag — lower tier Pristine >= higher tier Common/Fine ─
console.log('\n══ 2. TIER-INVERSION FLAGS (lower tier Pristine >= higher tier Common/Fine) ══════════════\n');

const COMMON = 1; // grade ordinal
const FINE = 2;
const PRISTINE = 4;

let flagCount = 0;
for (const key of sortedKeys) {
  const [band, stat] = key.split('|');
  const list = groups.get(key).filter(c => !c.penalty); // penalties invert "beats" semantics; skip
  for (let i = 0; i < list.length; i++) {
    for (let j = 0; j < list.length; j++) {
      const lower = list[i];
      const higher = list[j];
      if (lower.tier >= higher.tier) continue; // only strictly lower tier vs strictly higher tier
      const lowerPristine = lower.effective[PRISTINE];
      const higherCommon = higher.effective[COMMON];
      const higherFine = higher.effective[FINE];
      if (lowerPristine >= higherCommon || lowerPristine >= higherFine) {
        flagCount++;
        const vsCommon = lowerPristine >= higherCommon ? 'meets/beats Common' : 'below Common';
        const vsFine = lowerPristine >= higherFine ? 'meets/beats Fine' : 'below Fine';
        console.log(
          `  [${band}/${stat}] T${lower.tier} ${lower.name} (${lower.role}) Pristine=${fmt(lowerPristine)}` +
          `  vs  T${higher.tier} ${higher.name} (${higher.role}) Common=${fmt(higherCommon)} Fine=${fmt(higherFine)}` +
          `   -> ${vsCommon}, ${vsFine}`
        );
      }
    }
  }
}

console.log(`\n  Total flagged pairs: ${flagCount}`);
console.log('\n  (Not a bug in itself — this only reports the numeric fact. Whether tier deltas should');
console.log('   widen, grade spread should narrow, or this is the intended feel is Wes\'s call.)\n');
