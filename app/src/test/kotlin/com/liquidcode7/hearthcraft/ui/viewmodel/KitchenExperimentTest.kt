package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.engine.ExperimentAttempt
import com.liquidcode7.hearthcraft.engine.ExperimentResult
import com.liquidcode7.hearthcraft.engine.RecipeDiscoveryEngine
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.RecipeIngredient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenExperimentTest {

    private val hearthbread = Recipe(
        id = "hearthbread",
        name = "Hearthbread",
        method = "bake",
        ingredients = listOf(
            RecipeIngredient("flour", 2),
            RecipeIngredient("water", 1)
        )
    )

    @Test
    fun `exact match is Discovered, not Failure`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("flour", "water"),
            quantities    = mapOf("flour" to 2, "water" to 1),
            method        = "bake"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, listOf(hearthbread), emptySet())
        assertTrue(result is ExperimentResult.Discovered)
    }

    @Test
    fun `wrong method is Failure, not Discovered`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("flour", "water"),
            quantities    = mapOf("flour" to 2, "water" to 1),
            method        = "simmer"   // wrong method
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, listOf(hearthbread), emptySet())
        assertFalse(result is ExperimentResult.Discovered)
    }
}
