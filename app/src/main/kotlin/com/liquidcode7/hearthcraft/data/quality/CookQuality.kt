package com.liquidcode7.hearthcraft.data.quality

import com.liquidcode7.hearthcraft.data.model.Recipe

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
    // If heroIngredient is blank (unassigned recipe), all ingredients count equally.
    fun resolveDishGrade(
        recipe: Recipe,
        ingredientGrades: Map<String, Int>,
        cookLevel: Int
    ): Int {
        val heroId = recipe.heroIngredient
        val weightedSum: Int
        val divisor: Int
        if (heroId.isBlank()) {
            val grades = recipe.ingredients.map { ingredientGrades[it.id] ?: Grade.CRUDE }
            weightedSum = grades.sum()
            divisor = grades.size.coerceAtLeast(1)
        } else {
            val heroGrade = ingredientGrades[heroId] ?: Grade.CRUDE
            val supports = recipe.ingredients.filter { it.id != heroId }
            weightedSum = heroGrade * 2 + supports.sumOf { ingredientGrades[it.id] ?: Grade.CRUDE }
            divisor = 2 + supports.size
        }
        val raw = (weightedSum.toDouble() / divisor)
            .let { if (it - it.toInt() >= 0.5) it.toInt() + 1 else it.toInt() }
            .coerceIn(Grade.CRUDE, Grade.PRISTINE)
        return minOf(raw, cookCeiling(cookLevel, recipe.cookLevel))
    }
}
