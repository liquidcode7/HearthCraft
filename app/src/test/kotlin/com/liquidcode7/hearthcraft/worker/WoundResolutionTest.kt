package com.liquidcode7.hearthcraft.worker

import org.junit.Assert.*
import org.junit.Test

class WoundResolutionTest {

    @Test
    fun `0 wounds resolves to no outcome`() {
        assertNull(resolveWoundOutcome(wounds = 0, hohAvailable = false))
        assertNull(resolveWoundOutcome(wounds = 0, hohAvailable = true))
    }

    @Test
    fun `1-2 wounds is light wounded regardless of HoH availability`() {
        val a = resolveWoundOutcome(wounds = 1, hohAvailable = false)!!
        val b = resolveWoundOutcome(wounds = 2, hohAvailable = true)!!
        assertFalse(a.grievous)
        assertFalse(b.grievous)
        assertEquals(a.durationMs, b.durationMs)
    }

    @Test
    fun `3-4 wounds is heavy wounded with a longer duration than light`() {
        val light = resolveWoundOutcome(wounds = 1, hohAvailable = false)!!
        val heavy = resolveWoundOutcome(wounds = 3, hohAvailable = false)!!
        assertFalse(heavy.grievous)
        assertTrue(heavy.durationMs > light.durationMs)
    }

    @Test
    fun `5 plus wounds with HoH available is genuinely grievous`() {
        val outcome = resolveWoundOutcome(wounds = 5, hohAvailable = true)!!
        assertTrue(outcome.grievous)
    }

    @Test
    fun `5 plus wounds without HoH available is capped at heavy wounded with the safety-net duration`() {
        val heavy = resolveWoundOutcome(wounds = 3, hohAvailable = false)!!
        val safetyNet = resolveWoundOutcome(wounds = 5, hohAvailable = false)!!
        assertFalse(safetyNet.grievous)
        assertTrue(safetyNet.durationMs > heavy.durationMs)
    }

}
