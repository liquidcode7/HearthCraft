package com.liquidcode7.hearthcraft.data.repository

import org.junit.Assert.assertEquals
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
}
