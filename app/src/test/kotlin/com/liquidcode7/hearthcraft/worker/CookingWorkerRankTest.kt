package com.liquidcode7.hearthcraft.worker

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.RecipeRank
import com.liquidcode7.hearthcraft.data.model.resolvedRankOrdinal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CookingWorker resolves a food recipe's rank at cook-completion time via
 * Recipe.resolvedRankOrdinal(playerCookLevel) — this test exercises that exact call
 * shape against realistic recipe/cook-level combinations, mirroring how the worker
 * itself calls it (see doWork()'s `recipe.resolvedRankOrdinal(oldLevel)`).
 */
class CookingWorkerRankTest {

    private val rankedRecipe = Recipe(
        id = "brookcress_bannock", name = "Brookcress Bannock", cookLevel = 1,
        primaryStat = "wil", primaryBoost = 2,
        ranks = listOf(
            RecipeRank(cookLevelOffset = 0, primaryBoost = 2),
            RecipeRank(cookLevelOffset = 7, primaryBoost = 2),
            RecipeRank(cookLevelOffset = 14, primaryBoost = 3),
            RecipeRank(cookLevelOffset = 20, primaryBoost = 3)
        )
    )

    @Test
    fun `cooking at the recipe's own unlock level resolves to Base rank`() {
        assertEquals(0, rankedRecipe.resolvedRankOrdinal(playerCookLevel = 1))
    }

    @Test
    fun `cooking at a higher cook level resolves to the matching higher rank`() {
        assertEquals(2, rankedRecipe.resolvedRankOrdinal(playerCookLevel = 15))
    }

    @Test
    fun `a recipe with no authored ranks always resolves to ordinal 0`() {
        val unranked = Recipe(id = "ember_porridge", name = "Ember Porridge", cookLevel = 1)
        assertEquals(0, unranked.resolvedRankOrdinal(playerCookLevel = 50))
    }
}
