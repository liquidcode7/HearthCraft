package com.liquidcode7.hearthcraft

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.repository.isRecipeVisible
import org.junit.Assert.*
import org.junit.Test

class RecipeAvailabilityTest {

    private fun recipe(id: String, tier: Int, recipeClass: String, cookLevel: Int = 1) = Recipe(
        id = id,
        name = id,
        tier = tier,
        recipeClass = recipeClass,
        cookLevel = cookLevel
    )

    // --- T1 food/draught: always visible ---

    @Test
    fun `T1 food recipe is visible with no grimoires`() {
        val r = recipe("stew_basic", tier = 1, recipeClass = "food")
        assertTrue(isRecipeVisible(r, emptySet(), emptySet()))
    }

    @Test
    fun `T1 draught recipe is visible with no grimoires`() {
        val r = recipe("draught_basic", tier = 1, recipeClass = "draught")
        assertTrue(isRecipeVisible(r, emptySet(), emptySet()))
    }

    // --- HoH T1: requires grimoire even at tier 1 ---

    @Test
    fun `HoH T1 recipe is NOT visible without hoh_t1 grimoire`() {
        val r = recipe("athelas_prep", tier = 1, recipeClass = "hoh")
        assertFalse(isRecipeVisible(r, emptySet(), emptySet()))
    }

    @Test
    fun `HoH T1 recipe IS visible with hoh_t1 grimoire`() {
        val r = recipe("athelas_prep", tier = 1, recipeClass = "hoh")
        assertTrue(isRecipeVisible(r, setOf("hoh_t1"), emptySet()))
    }

    // --- T2 food: requires cooking_t2 grimoire ---

    @Test
    fun `T2 food recipe is NOT visible without cooking_t2 grimoire`() {
        val r = recipe("pottage_rich", tier = 2, recipeClass = "food")
        assertFalse(isRecipeVisible(r, emptySet(), emptySet()))
    }

    @Test
    fun `T2 food recipe IS visible with cooking_t2 grimoire`() {
        val r = recipe("pottage_rich", tier = 2, recipeClass = "food")
        assertTrue(isRecipeVisible(r, setOf("cooking_t2"), emptySet()))
    }

    // --- Individual discovery bypasses grimoire for draughts ---

    @Test
    fun `T2 draught visible if individually discovered even without grimoire`() {
        val r = recipe("potency_draught", tier = 2, recipeClass = "draught")
        assertTrue(isRecipeVisible(r, emptySet(), setOf("potency_draught")))
    }

    @Test
    fun `T2 draught NOT visible without grimoire or individual discovery`() {
        val r = recipe("potency_draught", tier = 2, recipeClass = "draught")
        assertFalse(isRecipeVisible(r, emptySet(), emptySet()))
    }

    // --- Wrong grimoire class doesn't unlock ---

    @Test
    fun `cooking_t2 grimoire does NOT unlock draught_t2 recipes`() {
        val r = recipe("potency_draught", tier = 2, recipeClass = "draught")
        assertFalse(isRecipeVisible(r, setOf("cooking_t2"), emptySet()))
    }

    // --- hasVisibleRecipeOfClass: used by the HoH-availability softlock check ---

    @Test
    fun `hasVisibleRecipeOfClass is false when no recipes of that class exist`() {
        val recipes = listOf(recipe("stew_basic", tier = 1, recipeClass = "food"))
        assertFalse(com.liquidcode7.hearthcraft.data.repository.hasVisibleRecipeOfClass(
            recipes, "hoh", emptySet(), emptySet()
        ))
    }

    @Test
    fun `hasVisibleRecipeOfClass is false when hoh recipe exists but is not visible`() {
        val recipes = listOf(recipe("athelas_prep", tier = 1, recipeClass = "hoh"))
        assertFalse(com.liquidcode7.hearthcraft.data.repository.hasVisibleRecipeOfClass(
            recipes, "hoh", emptySet(), emptySet()
        ))
    }

    @Test
    fun `hasVisibleRecipeOfClass is true once a hoh recipe is visible via grimoire`() {
        val recipes = listOf(recipe("athelas_prep", tier = 1, recipeClass = "hoh"))
        assertTrue(com.liquidcode7.hearthcraft.data.repository.hasVisibleRecipeOfClass(
            recipes, "hoh", setOf("hoh_t1"), emptySet()
        ))
    }
}
