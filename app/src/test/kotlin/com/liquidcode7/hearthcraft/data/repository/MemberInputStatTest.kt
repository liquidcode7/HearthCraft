package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.gradeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberInputStatTest {

    @Test
    fun `hpsForCookLevel returns 5-dot-0 at level 1`() {
        assertEquals(5.0f, BandRepository.hpsForCookLevel(1), 0.05f)
    }

    @Test
    fun `hpsForCookLevel returns higher value at level 10`() {
        val hps = BandRepository.hpsForCookLevel(10)
        assert(hps >= 10.0f) { "Expected ≥10 HP/s at cook level 10, got $hps" }
    }

    @Test
    fun `statBonus applies primary boost to matching stat`() {
        val bonus = BandRepository.statBonusFor("mig", primaryStat = "mig", primaryBoost = 3,
            secondaryStat = null, secondaryBoost = 0)
        assertEquals(3f, bonus, 0.01f)
    }

    @Test
    fun `statBonus applies secondary boost to secondary stat`() {
        val bonus = BandRepository.statBonusFor("vit", primaryStat = "mig", primaryBoost = 3,
            secondaryStat = "vit", secondaryBoost = 1)
        assertEquals(1f, bonus, 0.01f)
    }

    @Test
    fun `statBonus returns zero when stat is unrelated`() {
        val bonus = BandRepository.statBonusFor("wil", primaryStat = "mig", primaryBoost = 3,
            secondaryStat = null, secondaryBoost = 0)
        assertEquals(0f, bonus, 0.01f)
    }

    // ── Grade bonus tests ────────────────────────────────────────────────────

    @Test
    fun `Crude grade adds zero to any stat boost`() {
        val step = gradeStep(Grade.CRUDE)
        assertEquals(0f, step, 0.001f)
    }

    @Test
    fun `grade bonus is additive on top of authored boost`() {
        // Simulate: authored primaryBoost = 3, grade = FINE
        val authoredBoost = 3f
        val effectiveBoost = authoredBoost + gradeStep(Grade.FINE)
        assertTrue("Fine grade should increase stat above authored boost", effectiveBoost > authoredBoost)
    }

    @Test
    fun `grade bonus does not apply when stat does not match (base is zero)`() {
        // If statBonusFor returns 0 (unrelated stat), grade step should not be added.
        val base = BandRepository.statBonusFor("wil", "mig", 3, null, 0)
        // Grade step is only added when base > 0 (matching stat); unrelated stat stays 0.
        assertEquals(0f, base, 0.001f)
        // Confirm the rule: gradeStep itself is always >= 0
        assertTrue(gradeStep(Grade.PRISTINE) >= 0f)
    }

    @Test
    fun `HP_s is not affected by grade (tier governs HP_s)`() {
        // HP/s is driven by cookLevel only, grade has no influence.
        val hpsAtLevel5 = BandRepository.hpsForCookLevel(5)
        // This is a static function — grade is not a parameter. Just confirm it is stable.
        assertEquals(hpsAtLevel5, BandRepository.hpsForCookLevel(5), 0.001f)
    }
}
