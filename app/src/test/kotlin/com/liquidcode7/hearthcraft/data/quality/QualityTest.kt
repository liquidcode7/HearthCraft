package com.liquidcode7.hearthcraft.data.quality

import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QualityTest {

    // ── Grade constants ───────────────────────────────────────────────────────

    @Test fun `grade ordinals are 0 through 4`() {
        assertEquals(0, Grade.CRUDE)
        assertEquals(1, Grade.COMMON)
        assertEquals(2, Grade.FINE)
        assertEquals(3, Grade.SUPERB)
        assertEquals(4, Grade.PRISTINE)
    }

    @Test fun `grade name returns correct label`() {
        assertEquals("Crude",   Grade.name(Grade.CRUDE))
        assertEquals("Common",  Grade.name(Grade.COMMON))
        assertEquals("Fine",    Grade.name(Grade.FINE))
        assertEquals("Superb",  Grade.name(Grade.SUPERB))
        assertEquals("Pristine",Grade.name(Grade.PRISTINE))
    }

    // ── QualityRoll ───────────────────────────────────────────────────────────

    @Test fun `roll always returns a value in 0-4`() {
        val rng = Random(42)
        repeat(500) {
            val g = QualityRoll.roll(gatheringLevel = 1, aidActive = false, rng = rng)
            assertTrue("grade $g out of range", g in Grade.CRUDE..Grade.PRISTINE)
        }
    }

    @Test fun `roll with aid still returns 0-4`() {
        val rng = Random(99)
        repeat(500) {
            val g = QualityRoll.roll(gatheringLevel = 10, aidActive = true, rng = rng)
            assertTrue("grade $g out of range", g in Grade.CRUDE..Grade.PRISTINE)
        }
    }

    @Test fun `low level roll is dominated by Crude and Common`() {
        val rng = Random(1)
        val counts = IntArray(5)
        repeat(1000) { counts[QualityRoll.roll(1, false, rng)]++ }
        val lowGradeShare = (counts[0] + counts[1]).toDouble() / 1000
        assertTrue("expected >70% Crude+Common at lv1, got $lowGradeShare", lowGradeShare > 0.70)
    }

    // ── gradeStep ────────────────────────────────────────────────────────────

    @Test fun `gradeStep Crude is exactly zero`() {
        assertEquals(0f, CookQuality.gradeStep(Grade.CRUDE))
    }

    @Test fun `gradeStep is non-negative for all grades`() {
        for (g in Grade.CRUDE..Grade.PRISTINE) {
            assertTrue("gradeStep($g) was negative", CookQuality.gradeStep(g) >= 0f)
        }
    }

    @Test fun `gradeStep is monotonically non-decreasing`() {
        for (g in Grade.CRUDE until Grade.PRISTINE) {
            assertTrue(CookQuality.gradeStep(g + 1) >= CookQuality.gradeStep(g))
        }
    }

    // ── cookCeiling ──────────────────────────────────────────────────────────

    @Test fun `cookCeiling returns value in grade range`() {
        for (cl in 1..50) {
            val ceil = CookQuality.cookCeiling(cl, unlockLevel = 1)
            assertTrue(ceil in Grade.CRUDE..Grade.PRISTINE)
        }
    }

    @Test fun `cookCeiling at unlock level is not Pristine`() {
        val ceiling = CookQuality.cookCeiling(cookLevel = 5, unlockLevel = 5)
        assertTrue("ceiling at unlock should not be Pristine, was $ceiling", ceiling < Grade.PRISTINE)
    }

    @Test fun `cookCeiling well above unlock level reaches Pristine`() {
        val ceiling = CookQuality.cookCeiling(cookLevel = 50, unlockLevel = 1)
        assertEquals(Grade.PRISTINE, ceiling)
    }

    // ── resolveDishGrade ─────────────────────────────────────────────────────

    private fun recipe(hero: String, vararg supports: String) = Recipe(
        id = "test_recipe",
        name = "Test",
        heroIngredient = hero,
        ingredients = (listOf(hero) + supports.toList()).map { RecipeIngredient(it, 1) }
    )

    @Test fun `all Crude inputs resolve to Crude dish`() {
        val r = recipe("herb", "root", "honey")
        val grades = mapOf("herb" to Grade.CRUDE, "root" to Grade.CRUDE, "honey" to Grade.CRUDE)
        val dish = CookQuality.resolveDishGrade(r, grades, cookLevel = 50)
        assertEquals(Grade.CRUDE, dish)
    }

    @Test fun `Pristine hero with Crude supports is hero-weighted`() {
        // hero=Pristine(4)*2 + Common(1) + Common(1) = 10; divisor=4; raw=2.5→3=Fine
        val r = recipe("hero", "support1", "support2")
        val grades = mapOf("hero" to Grade.PRISTINE, "support1" to Grade.COMMON, "support2" to Grade.COMMON)
        val raw = CookQuality.resolveDishGrade(r, grades, cookLevel = 50)
        assertTrue("expected Fine or Superb, got ${Grade.name(raw)}", raw in Grade.FINE..Grade.SUPERB)
    }

    @Test fun `cook ceiling clamps Pristine hero down`() {
        val r = recipe("hero")
        val grades = mapOf("hero" to Grade.PRISTINE)
        // At unlock level, ceiling is low — dish should be clamped
        val ceiling = CookQuality.cookCeiling(cookLevel = 1, unlockLevel = 1)
        val dish = CookQuality.resolveDishGrade(r, grades, cookLevel = 1)
        assertEquals(ceiling, dish)
    }

    @Test fun `single ingredient recipe uses hero grade directly`() {
        val r = recipe("only_ing")
        val grades = mapOf("only_ing" to Grade.FINE)
        // heroGrade*2 + 0 supports / 2 = Fine; ceiling at high cook level should allow it
        val dish = CookQuality.resolveDishGrade(r, grades, cookLevel = 50)
        assertEquals(Grade.FINE, dish)
    }
}
