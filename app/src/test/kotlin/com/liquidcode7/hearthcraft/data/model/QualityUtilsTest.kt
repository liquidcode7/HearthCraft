package com.liquidcode7.hearthcraft.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QualityUtilsTest {

    // --- Grade enum ---

    @Test
    fun `Grade ordinals are 0 through 4 in ascending order`() {
        assertEquals(0, Grade.CRUDE.ordinal)
        assertEquals(1, Grade.COMMON.ordinal)
        assertEquals(2, Grade.FINE.ordinal)
        assertEquals(3, Grade.SUPERB.ordinal)
        assertEquals(4, Grade.PRISTINE.ordinal)
    }

    @Test
    fun `fromOrdinal round-trips all valid values`() {
        Grade.entries.forEach { grade ->
            assertEquals(grade, Grade.fromOrdinal(grade.ordinal))
        }
    }

    @Test
    fun `fromOrdinal clamps below zero to CRUDE`() {
        assertEquals(Grade.CRUDE, Grade.fromOrdinal(-1))
    }

    @Test
    fun `fromOrdinal clamps above 4 to PRISTINE`() {
        assertEquals(Grade.PRISTINE, Grade.fromOrdinal(99))
    }

    // --- gradeMultiplier ---

    @Test
    fun `gradeMultiplier for CRUDE softly reduces the authored boost, never to zero`() {
        val multiplier = gradeMultiplier(Grade.CRUDE)
        assertTrue("Crude multiplier must be positive", multiplier > 0f)
        assertTrue("Crude multiplier must be below the Fine baseline of 1.0", multiplier < 1.0f)
    }

    @Test
    fun `gradeMultiplier at FINE is exactly the baseline 1x`() {
        assertEquals(1.0f, gradeMultiplier(Grade.FINE), 0.0001f)
    }

    @Test
    fun `gradeMultiplier increases monotonically with grade`() {
        val multipliers = Grade.entries.map { gradeMultiplier(it) }
        for (i in 1..multipliers.lastIndex) {
            assertTrue(
                "gradeMultiplier(${Grade.entries[i]}) should exceed gradeMultiplier(${Grade.entries[i - 1]})",
                multipliers[i] > multipliers[i - 1]
            )
        }
    }

    @Test
    fun `gradeMultiplier is never negative`() {
        Grade.entries.forEach { grade ->
            assertTrue("gradeMultiplier($grade) must not be negative", gradeMultiplier(grade) >= 0f)
        }
    }

    // --- cookCeiling ---

    @Test
    fun `cookCeiling at unlock level does not allow PRISTINE`() {
        val ceiling = cookCeiling(cookLevel = 5, recipeUnlockLevel = 5) // delta = 0
        assertTrue("At unlock level, ceiling must be below PRISTINE", ceiling < Grade.PRISTINE)
    }

    @Test
    fun `cookCeiling rises as cook level exceeds unlock level`() {
        val ceiling0 = cookCeiling(cookLevel = 5, recipeUnlockLevel = 5)
        val ceiling1 = cookCeiling(cookLevel = 6, recipeUnlockLevel = 5)
        val ceiling2 = cookCeiling(cookLevel = 7, recipeUnlockLevel = 5)
        assertTrue("ceiling should not decrease as cook level rises", ceiling1 >= ceiling0)
        assertTrue("ceiling should not decrease as cook level rises", ceiling2 >= ceiling1)
    }

    @Test
    fun `cookCeiling eventually reaches PRISTINE with high cook level`() {
        val ceiling = cookCeiling(cookLevel = 100, recipeUnlockLevel = 0)
        assertEquals(Grade.PRISTINE, ceiling)
    }

    // --- resolveDishGrade ---

    @Test
    fun `all CRUDE ingredients resolve to CRUDE regardless of cook level`() {
        val result = resolveDishGrade(
            heroGrade = Grade.CRUDE,
            supportingGrades = listOf(Grade.CRUDE, Grade.CRUDE),
            cookLevel = 99,
            recipeUnlockLevel = 0
        )
        assertEquals(Grade.CRUDE, result)
    }

    @Test
    fun `hero grade counts double in weighted average`() {
        // hero=PRISTINE(4)*2=8, two supports=CRUDE(0)*2=0 → sum=8, divisor=4 → raw=2 (FINE)
        // With high cook level, ceiling doesn't clamp
        val result = resolveDishGrade(
            heroGrade = Grade.PRISTINE,
            supportingGrades = listOf(Grade.CRUDE, Grade.CRUDE),
            cookLevel = 99,
            recipeUnlockLevel = 0
        )
        assertEquals(Grade.FINE, result)
    }

    @Test
    fun `cook ceiling clamps a high-grade dish when cook level is low`() {
        // All PRISTINE ingredients should resolve high, but cook level = unlock level clamps it
        val result = resolveDishGrade(
            heroGrade = Grade.PRISTINE,
            supportingGrades = listOf(Grade.PRISTINE, Grade.PRISTINE),
            cookLevel = 5,
            recipeUnlockLevel = 5 // delta = 0, ceiling = COMMON
        )
        assertTrue("Ceiling must clamp high-grade ingredients when cook level is at unlock", result < Grade.FINE)
    }

    @Test
    fun `no ingredients below CRUDE — result is never below CRUDE`() {
        val result = resolveDishGrade(
            heroGrade = Grade.CRUDE,
            supportingGrades = emptyList(),
            cookLevel = 99,
            recipeUnlockLevel = 0
        )
        assertTrue("Dish grade must never be negative", result.ordinal >= 0)
    }

    @Test
    fun `single hero ingredient with no supports resolves correctly`() {
        // hero=SUPERB(3)*2=6, no supports → sum=6, divisor=2 → raw=3 (SUPERB)
        val result = resolveDishGrade(
            heroGrade = Grade.SUPERB,
            supportingGrades = emptyList(),
            cookLevel = 99,
            recipeUnlockLevel = 0
        )
        assertEquals(Grade.SUPERB, result)
    }

    // --- rollGrade ---

    @Test
    fun `rollGrade always returns a valid Grade`() {
        repeat(1000) {
            val grade = rollGrade(gatheringLevel = 0)
            assertTrue(grade.ordinal in 0..4)
        }
    }

    @Test
    fun `rollGrade with high level skews toward higher grades`() {
        // Run many rolls at level 0 and level 7; high level should average higher
        val lowAvg = (1..500).map { rollGrade(0, random = Random(it.toLong())).ordinal }.average()
        val highAvg = (1..500).map { rollGrade(7, random = Random(it.toLong())).ordinal }.average()
        assertTrue("High gathering level should produce higher average grade", highAvg > lowAvg)
    }

    @Test
    fun `rollGrade with aid uplift produces same or higher grades than without`() {
        val noAid = (1..500).map { rollGrade(2, aidUplift = 0, random = Random(it.toLong())).ordinal }.average()
        val withAid = (1..500).map { rollGrade(2, aidUplift = 2, random = Random(it.toLong())).ordinal }.average()
        assertTrue("Aid uplift should raise average grade", withAid > noAid)
    }
}
