package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDiscoveryEngineTest {

    private val gruel = Recipe(
        id = "simple_gruel",
        name = "Simple Gruel",
        method = "simmer",
        tier = 1,
        cookLevel = 1,
        ingredients = listOf(
            RecipeIngredient("oats", 2),
            RecipeIngredient("water", 1)
        )
    )

    private val allRecipes = listOf(gruel)

    @Test
    fun `exact match with no prior knowledge returns Discovered`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water"),
            quantities = mapOf("oats" to 2, "water" to 1),
            method = "simmer"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Discovered)
        assertEquals("simple_gruel", (result as ExperimentResult.Discovered).recipe.id)
    }

    @Test
    fun `exact match when already known returns AlreadyKnown`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water"),
            quantities = mapOf("oats" to 2, "water" to 1),
            method = "simmer"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, setOf("simple_gruel"))
        assertTrue(result is ExperimentResult.AlreadyKnown)
    }

    @Test
    fun `right ingredients and qty but wrong method returns NEAR_MISS`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water"),
            quantities = mapOf("oats" to 2, "water" to 1),
            method = "bake"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.NEAR_MISS, (result as ExperimentResult.Failure).proximity)
    }

    @Test
    fun `right ingredients and method but wrong qty returns NEAR_MISS`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water"),
            quantities = mapOf("oats" to 1, "water" to 1),
            method = "simmer"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.NEAR_MISS, (result as ExperimentResult.Failure).proximity)
    }

    @Test
    fun `all recipe ingredients present with an extra and right method returns CLOSE`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "water", "salt"),
            quantities = mapOf("oats" to 2, "water" to 1, "salt" to 1),
            method = "simmer"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.CLOSE, (result as ExperimentResult.Failure).proximity)
    }

    @Test
    fun `partial ingredient overlap returns SOME`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("oats", "milk"),
            quantities = mapOf("oats" to 2, "milk" to 1),
            method = "brew"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.SOME, (result as ExperimentResult.Failure).proximity)
    }

    @Test
    fun `completely unrelated ingredients returns NONE`() {
        val attempt = ExperimentAttempt(
            ingredientIds = listOf("gold_dust", "silver_bark"),
            quantities = mapOf("gold_dust" to 1, "silver_bark" to 1),
            method = "brew"
        )
        val result = RecipeDiscoveryEngine.evaluate(attempt, allRecipes, emptySet())
        assertTrue(result is ExperimentResult.Failure)
        assertEquals(ProximityTier.NONE, (result as ExperimentResult.Failure).proximity)
    }
}
