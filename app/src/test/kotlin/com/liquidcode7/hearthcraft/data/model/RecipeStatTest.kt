package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeStatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `recipe deserializes primaryBoost and secondaryBoost`() {
        val raw = """
            {"id":"test","name":"Test","primaryStat":"mig","primaryBoost":3,
             "secondaryStat":"vit","secondaryBoost":1}
        """.trimIndent()
        val recipe = json.decodeFromString<Recipe>(raw)
        assertEquals(3, recipe.primaryBoost)
        assertEquals(1, recipe.secondaryBoost)
    }

    @Test
    fun `recipe defaults boosts to zero when absent`() {
        val raw = """{"id":"test","name":"Test"}"""
        val recipe = json.decodeFromString<Recipe>(raw)
        assertEquals(0, recipe.primaryBoost)
        assertEquals(0, recipe.secondaryBoost)
    }
}
