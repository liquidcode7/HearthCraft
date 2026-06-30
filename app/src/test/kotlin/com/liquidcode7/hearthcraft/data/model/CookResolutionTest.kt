package com.liquidcode7.hearthcraft.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookResolutionTest {

    // ── Hero assignment ──────────────────────────────────────────────────────

    @Test
    fun `recipe deserializes heroIngredient`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val raw = """{"id":"test","name":"Test","heroIngredient":"wheat"}"""
        val recipe = json.decodeFromString<Recipe>(raw)
        assertEquals("wheat", recipe.heroIngredient)
    }

    @Test
    fun `heroIngredient defaults to empty string when absent`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val raw = """{"id":"test","name":"Test"}"""
        val recipe = json.decodeFromString<Recipe>(raw)
        assertEquals("", recipe.heroIngredient)
    }

    // ── resolveDishGrade: hero weighting ────────────────────────────────────

    @Test
    fun `all Crude ingredients resolve to Crude at any cook level`() {
        val result = resolveDishGrade(
            heroGrade = Grade.CRUDE,
            supportingGrades = listOf(Grade.CRUDE, Grade.CRUDE),
            cookLevel = 99,
            recipeUnlockLevel = 1
        )
        assertEquals(Grade.CRUDE, result)
    }

    @Test
    fun `Pristine hero with Crude supports resolves to Fine (hero counts double)`() {
        // hero=4*2=8, supports=0+0=0 → sum=8, divisor=4 → raw=2.0 → FINE
        val result = resolveDishGrade(
            heroGrade = Grade.PRISTINE,
            supportingGrades = listOf(Grade.CRUDE, Grade.CRUDE),
            cookLevel = 99,
            recipeUnlockLevel = 1
        )
        assertEquals(Grade.FINE, result)
    }

    @Test
    fun `all Pristine ingredients with high cook level resolves to Pristine`() {
        val result = resolveDishGrade(
            heroGrade = Grade.PRISTINE,
            supportingGrades = listOf(Grade.PRISTINE, Grade.PRISTINE),
            cookLevel = 99,
            recipeUnlockLevel = 1
        )
        assertEquals(Grade.PRISTINE, result)
    }

    @Test
    fun `cook ceiling clamps a Pristine dish when cook level equals unlock level`() {
        // delta=0 → ceiling=COMMON; should clamp to COMMON or below
        val result = resolveDishGrade(
            heroGrade = Grade.PRISTINE,
            supportingGrades = listOf(Grade.PRISTINE, Grade.PRISTINE),
            cookLevel = 5,
            recipeUnlockLevel = 5
        )
        assertTrue("Ceiling should clamp to COMMON or below at delta=0", result.ordinal <= Grade.COMMON.ordinal)
    }

    @Test
    fun `single ingredient recipe resolves hero only`() {
        // hero=SUPERB(3)*2=6, no supports → divisor=2 → raw=3 → SUPERB
        val result = resolveDishGrade(
            heroGrade = Grade.SUPERB,
            supportingGrades = emptyList(),
            cookLevel = 99,
            recipeUnlockLevel = 1
        )
        assertEquals(Grade.SUPERB, result)
    }

    @Test
    fun `result is never below CRUDE`() {
        val result = resolveDishGrade(
            heroGrade = Grade.CRUDE,
            supportingGrades = emptyList(),
            cookLevel = 99,
            recipeUnlockLevel = 1
        )
        assertTrue(result.ordinal >= Grade.CRUDE.ordinal)
    }

    // ── gradeStep: additive scaling ─────────────────────────────────────────

    @Test
    fun `Crude adds zero bonus to stat boost`() {
        val authoredBoost = 3f
        val effectiveBoost = authoredBoost + gradeStep(Grade.CRUDE)
        assertEquals(3f, effectiveBoost)
    }

    @Test
    fun `higher grades always add a positive bonus`() {
        val authoredBoost = 3f
        listOf(Grade.COMMON, Grade.FINE, Grade.SUPERB, Grade.PRISTINE).forEach { grade ->
            val bonus = gradeStep(grade)
            assertTrue("gradeStep($grade) should be positive", bonus > 0f)
            assertTrue("effective boost should exceed authored", authoredBoost + bonus > authoredBoost)
        }
    }
}
