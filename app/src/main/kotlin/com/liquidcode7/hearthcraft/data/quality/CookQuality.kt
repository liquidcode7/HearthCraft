package com.liquidcode7.hearthcraft.data.quality

import com.liquidcode7.hearthcraft.data.model.Recipe
import kotlin.math.roundToInt

object CookQuality {

    // TODO(tuning): gradeStep magnitudes s1..s4 are PLACEHOLDERS.
    // Wes / the sim set the real additive steps. Crude = 0 is invariant.
    private val GRADE_STEPS = floatArrayOf(0f, 1f, 2f, 3f, 5f)

    fun gradeStep(grade: Int): Float = GRADE_STEPS.getOrElse(grade) { 0f }

    // TODO(tuning): cook-ceiling table is a PLACEHOLDER.
    // The exact delta thresholds (levels above unlock needed per grade step) are for Wes/sim.
    fun cookCeiling(cookLevel: Int, unlockLevel: Int): Int {
        val delta = cookLevel - unlockLevel
        return when {
            delta <= 0  -> Grade.CRUDE
            delta <= 2  -> Grade.COMMON
            delta <= 5  -> Grade.FINE
            delta <= 9  -> Grade.SUPERB
            else        -> Grade.PRISTINE
        }
    }

    // Hero-weighted average of ingredient grades, then clamped by cook ceiling.
    // If heroIngredient is blank or not found in the recipe's ingredient list,
    // all ingredients count equally (flat average).
    // playerCookLevel is the player's current cooking skill — distinct from recipe.cookLevel
    // (the recipe's minimum unlock level). Never pass recipe.cookLevel here.
    // overrideUnlockLevel: when non-null, used instead of recipe.cookLevel for the ceiling
    // calculation. Pass recipe.hohLevel for HoH recipes so the ceiling is relative to the
    // correct skill, not cookLevel (which is always 1 on HoH recipes).
    fun resolveDishGrade(
        recipe: Recipe,
        ingredientGrades: Map<String, Int>,
        playerCookLevel: Int,
        overrideUnlockLevel: Int? = null
    ): Int {
        val heroId = recipe.heroIngredient
        val ingredientIds = recipe.ingredients.map { it.id }.toSet()
        val useHero = heroId.isNotBlank() && heroId in ingredientIds
        val (weightedSum, divisor) = if (!useHero) {
            val sum = recipe.ingredients.sumOf { ingredientGrades[it.id] ?: Grade.CRUDE }
            sum to recipe.ingredients.size.coerceAtLeast(1)
        } else {
            val heroGrade = ingredientGrades[heroId] ?: Grade.CRUDE
            val supportSum = recipe.ingredients.sumOf {
                if (it.id == heroId) 0 else ingredientGrades[it.id] ?: Grade.CRUDE
            }
            val supportCount = recipe.ingredients.count { it.id != heroId }
            (heroGrade * 2 + supportSum) to (2 + supportCount)
        }
        val raw = (weightedSum.toDouble() / divisor)
            .roundToInt()
            .coerceIn(Grade.CRUDE, Grade.PRISTINE)
        val unlockLevel = overrideUnlockLevel ?: recipe.cookLevel
        return minOf(raw, cookCeiling(playerCookLevel, unlockLevel))
    }
}
