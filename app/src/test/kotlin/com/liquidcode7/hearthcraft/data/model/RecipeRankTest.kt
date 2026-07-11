package com.liquidcode7.hearthcraft.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeRankTest {

    private fun recipe(cookLevel: Int = 1, ranks: List<RecipeRank> = emptyList()) = Recipe(
        id = "test", name = "Test Dish", cookLevel = cookLevel,
        primaryStat = "mig", primaryBoost = 2, ranks = ranks
    )

    private val fourRanks = listOf(
        RecipeRank(cookLevelOffset = 0, primaryBoost = 2),
        RecipeRank(cookLevelOffset = 7, primaryBoost = 2),
        RecipeRank(cookLevelOffset = 14, primaryBoost = 3),
        RecipeRank(cookLevelOffset = 20, primaryBoost = 3)
    )

    @Test
    fun `resolvedRankOrdinal returns 0 when ranks is empty`() {
        val r = recipe(cookLevel = 1, ranks = emptyList())
        assertEquals(0, r.resolvedRankOrdinal(playerCookLevel = 50))
    }

    @Test
    fun `resolvedRankOrdinal returns 0 below the first breakpoint`() {
        val r = recipe(cookLevel = 1, ranks = fourRanks)
        assertEquals(0, r.resolvedRankOrdinal(playerCookLevel = 5))
    }

    @Test
    fun `resolvedRankOrdinal returns the matching ordinal exactly at a breakpoint`() {
        val r = recipe(cookLevel = 1, ranks = fourRanks)
        assertEquals(1, r.resolvedRankOrdinal(playerCookLevel = 8))  // 1 + 7
        assertEquals(2, r.resolvedRankOrdinal(playerCookLevel = 15)) // 1 + 14
        assertEquals(3, r.resolvedRankOrdinal(playerCookLevel = 21)) // 1 + 20
    }

    @Test
    fun `resolvedRankOrdinal stays at the last ordinal above the final breakpoint`() {
        val r = recipe(cookLevel = 1, ranks = fourRanks)
        assertEquals(3, r.resolvedRankOrdinal(playerCookLevel = 99))
    }

    @Test
    fun `rankAt returns the matching RecipeRank for a valid ordinal`() {
        val r = recipe(cookLevel = 1, ranks = fourRanks)
        assertEquals(fourRanks[2], r.rankAt(2))
    }

    @Test
    fun `rankAt falls back to a synthesized Base rank when ranks is empty`() {
        val r = recipe(cookLevel = 1, ranks = emptyList())
        val fallback = r.rankAt(0)
        assertEquals(0, fallback.cookLevelOffset)
        assertEquals(2, fallback.primaryBoost)
    }

    @Test
    fun `rankedDisplayName returns the plain name at ordinal 0`() {
        val r = recipe(ranks = fourRanks)
        assertEquals("Test Dish", r.rankedDisplayName(0))
    }

    @Test
    fun `rankedDisplayName prefixes the name at higher ordinals`() {
        val r = recipe(ranks = fourRanks)
        assertEquals("Insightful Test Dish", r.rankedDisplayName(1))
        assertEquals("Inspired Test Dish", r.rankedDisplayName(2))
        assertEquals("Inimitable Test Dish", r.rankedDisplayName(3))
    }
}
