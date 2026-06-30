package com.liquidcode7.hearthcraft.data.model

import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------
// TUNING TABLES — set by Wes / the sim. Placeholders only; do not ship these
// values as final. Each constant is marked TODO:TUNE.
// ---------------------------------------------------------------------------

/**
 * Weighted gather distribution per gathering level.
 * Each inner array is [Crude, Common, Fine, Superb, Pristine] weights (must sum to 100).
 * Index = gatheringLevel (0-based). Add more rows as levels expand.
 * TODO:TUNE — all values below are placeholder.
 */
private val GATHER_DISTRIBUTION: Array<IntArray> = arrayOf(
    intArrayOf(50, 30, 15, 4, 1),  // level 0
    intArrayOf(40, 32, 20, 6, 2),  // level 1
    intArrayOf(28, 32, 26, 10, 4), // level 2
    intArrayOf(18, 28, 30, 17, 7), // level 3
    intArrayOf(10, 22, 32, 24, 12),// level 4
    intArrayOf(5,  18, 30, 30, 17),// level 5
    intArrayOf(2,  13, 28, 35, 22),// level 6
    intArrayOf(1,  9,  25, 38, 27),// level 7
)

/**
 * Grade-step additive bonus applied to a dish's/item's authored stat boost.
 * CRUDE = 0 (baseline, no bonus, no penalty). Values are FRACTIONS of a stat point.
 * TODO:TUNE — placeholder values; Wes/sim to calibrate against encounter curve.
 */
private val GRADE_STEP = floatArrayOf(
    0f,    // CRUDE   (ordinal 0)
    0.5f,  // COMMON  (ordinal 1)
    1.0f,  // FINE    (ordinal 2)
    1.75f, // SUPERB  (ordinal 3)
    2.5f,  // PRISTINE(ordinal 4)
)

/**
 * Cook ceiling: the maximum dish grade (ordinal) achievable at a given
 * (cookLevel - recipeUnlockLevel) delta. Delta 0 = just unlocked.
 * TODO:TUNE — placeholder values; tune so Pristine requires meaningful cook investment.
 */
private val COOK_CEILING_BY_DELTA = intArrayOf(
    1, // delta 0 → cap at COMMON
    2, // delta 1 → cap at FINE
    3, // delta 2 → cap at SUPERB
    4, // delta 3+ → cap at PRISTINE
)

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Roll one grade for a gather session.
 *
 * @param gatheringLevel  The player's current gathering level (0-based).
 * @param aidUplift       If a gathering aid is active, shift the distribution
 *                        this many levels upward (clamps to table bounds).
 *                        TODO: hook up to real gathering-aid data once the
 *                        aid acquisition design is complete.
 * @param random          Injectable for testing.
 */
fun rollGrade(
    gatheringLevel: Int,
    aidUplift: Int = 0,
    random: Random = Random.Default
): Grade {
    val effectiveLevel = (gatheringLevel + aidUplift).coerceIn(0, GATHER_DISTRIBUTION.lastIndex)
    val weights = GATHER_DISTRIBUTION[effectiveLevel]
    val roll = random.nextInt(100)
    var cumulative = 0
    for (i in weights.indices) {
        cumulative += weights[i]
        if (roll < cumulative) return Grade.fromOrdinal(i)
    }
    return Grade.PRISTINE // unreachable if weights sum to 100
}

/**
 * Additive stat-boost step for a given grade.
 * CRUDE = 0f (no bonus, no penalty). Add this to the recipe's authored boost.
 */
fun gradeStep(grade: Grade): Float = GRADE_STEP[grade.ordinal]

/**
 * The highest grade a cooked dish can resolve to, given how far the cook's
 * level exceeds the recipe's unlock level.
 *
 * @param cookLevel          Player's current cook level.
 * @param recipeUnlockLevel  The recipe's minimum cook level to access it.
 */
fun cookCeiling(cookLevel: Int, recipeUnlockLevel: Int): Grade {
    val delta = (cookLevel - recipeUnlockLevel).coerceAtLeast(0)
    val ceilingOrdinal = COOK_CEILING_BY_DELTA[delta.coerceAtMost(COOK_CEILING_BY_DELTA.lastIndex)]
    return Grade.fromOrdinal(ceilingOrdinal)
}

/**
 * Resolve a dish's grade from its ingredient grades.
 *
 * The hero ingredient counts double; every supporting ingredient counts once.
 * Result is rounded half-up, then clamped by the cook ceiling.
 *
 * @param heroGrade        Grade of the recipe's designated hero ingredient.
 * @param supportingGrades Grades of all other (non-hero) ingredients.
 * @param cookLevel        Player's current cook level.
 * @param recipeUnlockLevel The recipe's minimum cook level.
 */
fun resolveDishGrade(
    heroGrade: Grade,
    supportingGrades: List<Grade>,
    cookLevel: Int,
    recipeUnlockLevel: Int
): Grade {
    val weightedSum = heroGrade.ordinal * 2 + supportingGrades.sumOf { it.ordinal }
    val weightDivisor = 2 + supportingGrades.size
    val rawOrdinal = (weightedSum.toFloat() / weightDivisor).roundToInt()
    val ceiling = cookCeiling(cookLevel, recipeUnlockLevel)
    return Grade.fromOrdinal(rawOrdinal.coerceAtMost(ceiling.ordinal))
}
