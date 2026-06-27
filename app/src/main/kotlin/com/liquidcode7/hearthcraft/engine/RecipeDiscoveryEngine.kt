package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Recipe

data class ExperimentAttempt(
    val ingredientIds: List<String>,
    val quantities: Map<String, Int>,
    val method: String
)

sealed class ExperimentResult {
    data class Discovered(val recipe: Recipe) : ExperimentResult()
    data class AlreadyKnown(val recipe: Recipe) : ExperimentResult()
    data class Failure(val proximity: ProximityTier) : ExperimentResult()
}

enum class ProximityTier { NONE, SOME, CLOSE, NEAR_MISS }

object RecipeDiscoveryEngine {

    fun evaluate(
        attempt: ExperimentAttempt,
        allRecipes: List<Recipe>,
        discoveredIds: Set<String>
    ): ExperimentResult {
        for (recipe in allRecipes) {
            if (isExactMatch(attempt, recipe)) {
                return if (recipe.id in discoveredIds) {
                    ExperimentResult.AlreadyKnown(recipe)
                } else {
                    ExperimentResult.Discovered(recipe)
                }
            }
        }
        val best = allRecipes.maxOfOrNull { proximityScore(attempt, it) } ?: 0
        val tier = when (best) {
            4 -> ProximityTier.NEAR_MISS
            3 -> ProximityTier.CLOSE
            2 -> ProximityTier.SOME
            else -> ProximityTier.NONE
        }
        return ExperimentResult.Failure(tier)
    }

    private fun isExactMatch(attempt: ExperimentAttempt, recipe: Recipe): Boolean {
        val recipeIds = recipe.ingredients.map { it.id }.toSet()
        if (attempt.ingredientIds.toSet() != recipeIds) return false
        if (attempt.method != recipe.method) return false
        return recipe.ingredients.all { needed -> attempt.quantities[needed.id] == needed.qty }
    }

    private fun proximityScore(attempt: ExperimentAttempt, recipe: Recipe): Int {
        val recipeIds = recipe.ingredients.map { it.id }.toSet()
        val attemptIds = attempt.ingredientIds.toSet()
        val methodMatches = attempt.method == recipe.method
        val allIngredientsMatch = recipeIds == attemptIds
        val quantitiesMatch = recipe.ingredients.all { needed ->
            attempt.quantities[needed.id] == needed.qty
        }
        if (allIngredientsMatch && quantitiesMatch && !methodMatches) return 4
        if (allIngredientsMatch && methodMatches && !quantitiesMatch) return 4
        val allPresent = recipeIds.all { it in attemptIds }
        if (allPresent && methodMatches) return 3
        val overlap = attemptIds.intersect(recipeIds).size
        val minSize = minOf(attemptIds.size, recipeIds.size)
        if (minSize > 0 && overlap.toFloat() / minSize >= 0.5f) return 2
        return 0
    }
}
