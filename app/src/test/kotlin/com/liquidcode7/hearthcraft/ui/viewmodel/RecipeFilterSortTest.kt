package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.data.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFilterSortTest {

    private fun recipe(
        id: String,
        name: String = id,
        recipeClass: String = "food",
        primaryStat: String? = "mig",
        secondaryStat: String? = null,
        cookLevel: Int = 1
    ) = Recipe(
        id = id, name = name, recipeClass = recipeClass,
        primaryStat = primaryStat, secondaryStat = secondaryStat, cookLevel = cookLevel
    )

    // ── matchesRecipeFilters ─────────────────────────────────────────────

    @Test
    fun `no active filters matches every recipe`() {
        val r = recipe("a")
        assertTrue(matchesRecipeFilters(r, RecipeFilterState(), isCookable = false))
    }

    @Test
    fun `cookableOnly filter excludes recipes the player can't cook`() {
        val r = recipe("a")
        val filters = RecipeFilterState(cookableOnly = true)
        assertFalse(matchesRecipeFilters(r, filters, isCookable = false))
        assertTrue(matchesRecipeFilters(r, filters, isCookable = true))
    }

    @Test
    fun `class filter matches only recipes in the selected classes`() {
        val food = recipe("a", recipeClass = "food")
        val draught = recipe("b", recipeClass = "draught")
        val filters = RecipeFilterState(classFilter = setOf("draught"))
        assertFalse(matchesRecipeFilters(food, filters, isCookable = true))
        assertTrue(matchesRecipeFilters(draught, filters, isCookable = true))
    }

    @Test
    fun `stat filter matches on either primary or secondary stat`() {
        val r = recipe("a", primaryStat = "mig", secondaryStat = "vit")
        assertTrue(matchesRecipeFilters(r, RecipeFilterState(statFilter = setOf("vit")), isCookable = true))
        assertFalse(matchesRecipeFilters(r, RecipeFilterState(statFilter = setOf("wil")), isCookable = true))
    }

    @Test
    fun `multiple active filter groups combine with AND`() {
        val r = recipe("a", recipeClass = "food", primaryStat = "mig")
        val filters = RecipeFilterState(classFilter = setOf("draught"), statFilter = setOf("mig"))
        assertFalse(matchesRecipeFilters(r, filters, isCookable = true))
    }

    // ── sortRecipesForDisplay ────────────────────────────────────────────

    @Test
    fun `TIER mode returns the input order unchanged`() {
        val input = listOf(recipe("z", name = "Zed"), recipe("a", name = "Ace"))
        assertEquals(input, sortRecipesForDisplay(input, RecipeSortMode.TIER))
    }

    @Test
    fun `ALPHABETICAL mode sorts by name`() {
        val input = listOf(recipe("z", name = "Zed"), recipe("a", name = "Ace"))
        val sorted = sortRecipesForDisplay(input, RecipeSortMode.ALPHABETICAL)
        assertEquals(listOf("Ace", "Zed"), sorted.map { it.name })
    }

    @Test
    fun `LEVEL mode sorts by cookLevel ascending`() {
        val input = listOf(recipe("hi", cookLevel = 9), recipe("lo", cookLevel = 1))
        val sorted = sortRecipesForDisplay(input, RecipeSortMode.LEVEL)
        assertEquals(listOf("lo", "hi"), sorted.map { it.id })
    }

    // ── tier expansion helpers ───────────────────────────────────────────

    @Test
    fun `expandedStateFor defaults to isUnlocked when no override exists`() {
        assertTrue(expandedStateFor(emptyMap(), "T1", isUnlocked = true))
        assertFalse(expandedStateFor(emptyMap(), "T1", isUnlocked = false))
    }

    @Test
    fun `expandedStateFor honors an explicit override`() {
        val overrides = mapOf("T1" to false)
        assertFalse(expandedStateFor(overrides, "T1", isUnlocked = true))
    }

    @Test
    fun `withToggledTier flips the current effective state`() {
        val result = withToggledTier(emptyMap(), "T1", isUnlocked = true)
        assertEquals(mapOf("T1" to false), result)
    }

    @Test
    fun `withOnlyTierExpanded expands the target tier and collapses the rest`() {
        val result = withOnlyTierExpanded("T2", listOf("T1", "T2", "T3"))
        assertEquals(mapOf("T1" to false, "T2" to true, "T3" to false), result)
    }
}
