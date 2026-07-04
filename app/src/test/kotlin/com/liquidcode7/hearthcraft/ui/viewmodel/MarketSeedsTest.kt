package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.data.model.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketSeedsTest {

    private fun ingredient(id: String, name: String, cultivatable: Boolean) = Ingredient(
        id = id,
        name = name,
        cultivatable = cultivatable
    )

    @Test
    fun `only cultivatable and discovered ingredients become seeds for sale`() {
        val ingredients = listOf(
            ingredient("wanderer_fig", "Wanderer Fig", cultivatable = true),   // discovered, cultivatable -> included
            ingredient("athelas", "Athelas", cultivatable = true),            // cultivatable but not discovered -> excluded
            ingredient("river_trout", "River Trout", cultivatable = false)    // discovered but not cultivatable -> excluded
        )
        val discoveredIds = setOf("wanderer_fig", "river_trout")

        val result = availableSeeds(ingredients, discoveredIds)

        assertEquals(listOf("wanderer_fig_seed" to "Wanderer Fig Seed"), result)
    }

    @Test
    fun `results are sorted by ingredient name`() {
        val ingredients = listOf(
            ingredient("z_herb", "Zinnia Herb", cultivatable = true),
            ingredient("a_herb", "Ashroot", cultivatable = true)
        )
        val discoveredIds = setOf("z_herb", "a_herb")

        val result = availableSeeds(ingredients, discoveredIds)

        assertEquals(listOf("a_herb_seed" to "Ashroot Seed", "z_herb_seed" to "Zinnia Herb Seed"), result)
    }
}
