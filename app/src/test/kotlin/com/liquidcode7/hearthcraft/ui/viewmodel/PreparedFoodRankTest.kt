package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.RecipeRank
import com.liquidcode7.hearthcraft.data.model.rankAt
import com.liquidcode7.hearthcraft.data.model.rankedDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparedFoodRankTest {

    private val recipe = Recipe(
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
    fun `a Base-rank prepared food batch shows the plain name and base boost`() {
        val rank = recipe.rankAt(0)
        assertEquals("Brookcress Bannock", recipe.rankedDisplayName(0))
        assertEquals(2, rank.primaryBoost)
    }

    @Test
    fun `an Inspired-rank prepared food batch shows the prefixed name and its own boost`() {
        val rank = recipe.rankAt(2)
        assertEquals("Inspired Brookcress Bannock", recipe.rankedDisplayName(2))
        assertEquals(3, rank.primaryBoost)
    }

    @Test
    fun `a recipe with no authored ranks always shows the plain name and base boost`() {
        val unranked = Recipe(id = "ember_porridge", name = "Ember Porridge", cookLevel = 1, primaryBoost = 0)
        assertEquals("Ember Porridge", unranked.rankedDisplayName(0))
        assertEquals(0, unranked.rankAt(0).primaryBoost)
    }
}
