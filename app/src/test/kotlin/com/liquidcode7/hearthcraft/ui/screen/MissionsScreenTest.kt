package com.liquidcode7.hearthcraft.ui.screen

import com.liquidcode7.hearthcraft.data.db.CombatReport
import org.junit.Assert.assertEquals
import org.junit.Test

class MissionsScreenTest {

    private fun report(
        outcome: String,
        endedAtSec: Int,
        durationSec: Int = 1000,
        rescuesUsed: Int = 0,
        wardGuardsUsed: Int = 0,
        resolveRemainingFraction: Float = 0f
    ) = CombatReport(
        bandId = "b",
        encounterId = "e",
        encounterName = "Goblin Assault on the Outer Gate",
        outcome = outcome,
        endedAtSec = endedAtSec,
        durationSec = durationSec,
        woundsJson = "",
        rescuesUsed = rescuesUsed,
        wardGuardsUsed = wardGuardsUsed,
        resolveRemainingFraction = resolveRemainingFraction,
        createdAtMs = 0L
    )

    @Test
    fun `clean fast victory`() {
        val r = report(outcome = "VICTORY", endedAtSec = 100, durationSec = 1000)
        assertEquals(
            "The fight was swift — victory, and hardly tested.",
            buildCombatNarrative(r, totalWounds = 0)
        )
    }

    @Test
    fun `clean hard-fought victory`() {
        val r = report(outcome = "VICTORY", endedAtSec = 600, durationSec = 1000)
        assertEquals(
            "A hard-fought victory. The band held until the enemy could hold no longer.",
            buildCombatNarrative(r, totalWounds = 0)
        )
    }

    @Test
    fun `close call victory via rescues overrides fast timing`() {
        val r = report(outcome = "VICTORY", endedAtSec = 100, durationSec = 1000, rescuesUsed = 1)
        assertEquals(
            "A close call — the band nearly broke before the enemy did. Victory, but a near thing.",
            buildCombatNarrative(r, totalWounds = 0)
        )
    }

    @Test
    fun `close call victory via ward guards`() {
        val r = report(outcome = "VICTORY", endedAtSec = 600, durationSec = 1000, wardGuardsUsed = 1)
        assertEquals(
            "A close call — the band nearly broke before the enemy did. Victory, but a near thing.",
            buildCombatNarrative(r, totalWounds = 0)
        )
    }

    @Test
    fun `close call victory via heavy wounds`() {
        val r = report(outcome = "VICTORY", endedAtSec = 100, durationSec = 1000)
        assertEquals(
            "A close call — the band nearly broke before the enemy did. Victory, but a near thing.",
            buildCombatNarrative(r, totalWounds = 3)
        )
    }

    @Test
    fun `quick collapse defeat`() {
        val r = report(outcome = "DEFEAT", endedAtSec = 100, durationSec = 1000)
        assertEquals(
            "The band fell quickly, overwhelmed before they found their footing.",
            buildCombatNarrative(r, totalWounds = 5)
        )
    }

    @Test
    fun `worn-down defeat with rescue note`() {
        val r = report(outcome = "DEFEAT", endedAtSec = 600, durationSec = 1000, rescuesUsed = 2)
        assertEquals(
            "Wound by wound, the band was worn down until none remained standing. The band fell. The Keeper fought to the last, pulling 2 from the edge — but it was not enough.",
            buildCombatNarrative(r, totalWounds = 5)
        )
    }
}
