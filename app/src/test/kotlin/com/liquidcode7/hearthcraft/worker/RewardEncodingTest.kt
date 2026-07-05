package com.liquidcode7.hearthcraft.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class RewardEncodingTest {

    @Test
    fun `encodes distinct ingredient ids with a count of 1 each`() {
        val result = encodeIngredientCounts(listOf("hearthgrain", "wanderer_fig"))
        assertEquals("hearthgrain:1,wanderer_fig:1", result)
    }

    @Test
    fun `aggregates duplicate ingredient ids into one entry with a summed count`() {
        val result = encodeIngredientCounts(listOf("hearthgrain", "hearthgrain", "wanderer_fig"))
        assertEquals("hearthgrain:2,wanderer_fig:1", result)
    }

    @Test
    fun `empty list encodes to an empty string`() {
        val result = encodeIngredientCounts(emptyList())
        assertEquals("", result)
    }
}
