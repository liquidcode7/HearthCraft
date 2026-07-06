package com.liquidcode7.hearthcraft.worker

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.repository.isRecipeVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regression coverage for the level-up auto-discover hole: CookingWorker.doWork() used to
// bulk-discover every recipe within cookLevel range with no grimoire check at all, so a
// tier-4 recipe (e.g. Heartflame Broth, cookLevel 6) became visible the instant cook level
// crossed 6, even with zero matching grimoires found — and even for classes with no
// grimoire in the game yet (draught_t4 isn't in grimoires.json). CookingWorker now runs the
// same isRecipeVisible() gate that KitchenViewModel already applies to its own recipe lists.
class CookingWorkerDiscoveryTest {

    private fun recipe(id: String, tier: Int, recipeClass: String, cookLevel: Int, band: String = "all") = Recipe(
        id = id,
        name = id,
        band = band,
        recipeClass = recipeClass,
        tier = tier,
        cookLevel = cookLevel
    )

    // Extracted logic matching CookingWorker's level-up "toDiscover" filter.
    private fun toDiscover(
        recipes: List<Recipe>,
        newLevel: Int,
        bandId: String,
        currentDiscovered: Set<String>,
        foundGrimoires: Set<String>
    ): List<String> = recipes.filter { recipe ->
        recipe.cookLevel <= newLevel
            && recipe.recipeClass != "hoh"
            && recipe.id !in currentDiscovered
            && (recipe.band == bandId || recipe.band == "all")
            && isRecipeVisible(recipe, foundGrimoires, currentDiscovered)
    }.map { it.id }

    @Test
    fun `tier-4 recipe is NOT auto-discovered on level-up without its grimoire`() {
        // Player at cook level 5 levels up to 6; heartflame_broth (t4, cookLevel 6) exists
        // in the data but no grimoire has been found for it.
        val heartflameBroth = recipe("heartflame_broth", tier = 4, recipeClass = "food", cookLevel = 6)

        val result = toDiscover(
            recipes = listOf(heartflameBroth),
            newLevel = 6,
            bandId = "men",
            currentDiscovered = emptySet(),
            foundGrimoires = emptySet()
        )

        assertFalse(result.contains("heartflame_broth"))
    }

    @Test
    fun `tier-2 recipe IS auto-discovered on level-up once its grimoire is found`() {
        // Same level-up, but cooking_t2 has already been found and a tier-2 food recipe
        // shares the same cookLevel threshold — that one should still surface normally.
        val tier2Food = recipe("pottage_rich", tier = 2, recipeClass = "food", cookLevel = 6)

        val result = toDiscover(
            recipes = listOf(tier2Food),
            newLevel = 6,
            bandId = "men",
            currentDiscovered = emptySet(),
            foundGrimoires = setOf("cooking_t2")
        )

        assertTrue(result.contains("pottage_rich"))
    }
}
