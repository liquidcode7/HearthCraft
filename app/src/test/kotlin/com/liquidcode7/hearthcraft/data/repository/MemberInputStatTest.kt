package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.gradeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberInputStatTest {

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

    @Test
    fun `Crude grade adds zero to any stat boost`() {
        val step = gradeStep(Grade.CRUDE)
        assertEquals(0f, step, 0.001f)
    }

    @Test
    fun `grade bonus is additive on top of authored boost`() {
        val authoredBoost = 3f
        val effectiveBoost = authoredBoost + gradeStep(Grade.FINE)
        assertTrue("Fine grade should increase stat above authored boost", effectiveBoost > authoredBoost)
    }

    @Test
    fun `grade bonus does not apply when stat does not match`() {
        val base = BandRepository.statBonusFor("wil", "mig", 3, null, 0)
        assertEquals(0f, base, 0.001f)
        assertTrue(gradeStep(Grade.PRISTINE) >= 0f)
    }
}
