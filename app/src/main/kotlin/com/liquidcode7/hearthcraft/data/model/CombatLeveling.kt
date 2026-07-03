package com.liquidcode7.hearthcraft.data.model

import kotlin.math.pow
import kotlin.math.roundToInt

const val COMBAT_MAX_LEVEL = 50

// A member's stat at a given level: starting value compounding by a fixed
// percentage each level (not flat-linear) — this is what makes a level-50
// veteran dramatically stronger than a fresh recruit, per the stat-growth-
// and-food-power design doc. Growth rates are per-role/per-stat placeholders
// (see growth_curves.json) — not final-balanced; tuned via the balance harness.
fun statAtLevel(startingStat: Int, growthRate: Float, level: Int): Float =
    startingStat * (1f + growthRate).pow(level - 1)

// Fighter has two growth-curve variants (melee/ranged); every other role has one.
fun growthCurveKeyForRole(role: String, fighterBuild: String): String {
    val lower = role.lowercase()
    return if (lower == "fighter") "fighter_$fighterBuild" else lower
}

// Combat XP curve — same shape as Gathering's (base = A * level^P), a placeholder
// pending validation via the sim rewire. Mirrors PlayerRepository.Companion's
// xpToNext/levelForTotalXp pattern, scoped separately since combat XP lives on
// BandMemberState (per member), not PlayerState.
fun xpToNextCombatLevel(level: Int): Int {
    if (level >= COMBAT_MAX_LEVEL) return Int.MAX_VALUE
    return maxOf(1, (20.0 * level.toDouble().pow(1.35)).roundToInt())
}

private val COMBAT_XP_THRESHOLDS: IntArray = IntArray(COMBAT_MAX_LEVEL) { i ->
    var total = 0; for (l in 1..i) total += xpToNextCombatLevel(l); total
}

fun levelForCombatXp(xp: Int): Int {
    var lo = 0; var hi = COMBAT_MAX_LEVEL - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (COMBAT_XP_THRESHOLDS[mid] <= xp) lo = mid else hi = mid - 1
    }
    return lo + 1
}
