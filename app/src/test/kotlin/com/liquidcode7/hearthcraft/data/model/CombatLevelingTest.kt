package com.liquidcode7.hearthcraft.data.model

import org.junit.Assert.*
import org.junit.Test

class CombatLevelingTest {

    @Test
    fun `statAtLevel at level 1 returns the starting stat unchanged`() {
        assertEquals(5.0f, statAtLevel(startingStat = 5, growthRate = 0.20f, level = 1), 0.0001f)
    }

    @Test
    fun `statAtLevel grows linearly with level`() {
        // level 11: 10 levels above 1, growth 0.20/level -> +2.0
        assertEquals(7.0f, statAtLevel(startingStat = 5, growthRate = 0.20f, level = 11), 0.0001f)
    }

    @Test
    fun `growthCurveKeyForRole passes non-fighter roles through as lowercase`() {
        assertEquals("warden", growthCurveKeyForRole(role = "Warden", fighterBuild = "ranged"))
        assertEquals("keeper", growthCurveKeyForRole(role = "keeper", fighterBuild = "melee"))
    }

    @Test
    fun `growthCurveKeyForRole appends fighter build for the fighter role`() {
        assertEquals("fighter_ranged", growthCurveKeyForRole(role = "fighter", fighterBuild = "ranged"))
        assertEquals("fighter_melee", growthCurveKeyForRole(role = "Fighter", fighterBuild = "melee"))
    }

    @Test
    fun `levelForCombatXp is 1 at zero xp`() {
        assertEquals(1, levelForCombatXp(0))
    }

    @Test
    fun `levelForCombatXp increases as xp increases`() {
        val lowXpLevel = levelForCombatXp(0)
        val highXpLevel = levelForCombatXp(50_000)
        assertTrue(highXpLevel > lowXpLevel)
    }

    @Test
    fun `levelForCombatXp never exceeds the level cap`() {
        assertEquals(COMBAT_MAX_LEVEL, levelForCombatXp(Int.MAX_VALUE / 2))
    }

    @Test
    fun `xpToNextCombatLevel returns a positive amount below the cap`() {
        assertTrue(xpToNextCombatLevel(1) > 0)
        assertTrue(xpToNextCombatLevel(25) > 0)
    }
}
