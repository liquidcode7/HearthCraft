package com.liquidcode7.hearthcraft.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingLevelCurveTest {

    @Test
    fun `MAX_LEVEL is 100`() {
        assertEquals(100, PlayerRepository.MAX_LEVEL)
    }

    @Test
    fun `cooking XP required strictly increases across a tier boundary`() {
        // Levels 20, 40, 60, 80 are tier-transition levels; the XP to climb OUT of
        // one of those levels should cost noticeably more than a same-tier level
        // just below it, thanks to the 1.8x wall multiplier.
        val withinTierCost = PlayerRepository.xpToNext(19, PlayerRepository.Track.COOKING)
        val wallCost = PlayerRepository.xpToNext(20, PlayerRepository.Track.COOKING)
        assertTrue(
            "wall cost ($wallCost) should exceed a same-tier cost near it ($withinTierCost)",
            wallCost > withinTierCost
        )
    }

    @Test
    fun `totalXpForLevel is monotonically increasing up to MAX_LEVEL`() {
        var previous = -1
        for (level in 1..PlayerRepository.MAX_LEVEL) {
            val total = PlayerRepository.totalXpForLevel(level, PlayerRepository.Track.COOKING)
            assertTrue(
                "level $level total XP ($total) should exceed level ${level - 1} ($previous)",
                total > previous
            )
            previous = total
        }
    }

    @Test
    fun `levelForTotalXp round-trips with totalXpForLevel at tier-boundary levels`() {
        for (level in listOf(1, 20, 21, 40, 41, 60, 80, 100)) {
            val xp = PlayerRepository.totalXpForLevel(level, PlayerRepository.Track.COOKING)
            assertEquals(level, PlayerRepository.levelForTotalXp(xp, PlayerRepository.Track.COOKING))
        }
    }

    @Test
    fun `levelForTotalXp never exceeds MAX_LEVEL`() {
        val level = PlayerRepository.levelForTotalXp(Int.MAX_VALUE / 2, PlayerRepository.Track.COOKING)
        assertEquals(PlayerRepository.MAX_LEVEL, level)
    }
}
