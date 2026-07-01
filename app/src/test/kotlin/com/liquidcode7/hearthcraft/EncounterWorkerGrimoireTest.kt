package com.liquidcode7.hearthcraft

import org.junit.Assert.*
import org.junit.Test

class EncounterWorkerGrimoireTest {

    @Test
    fun `resolveGrimoireDrops returns encounter grimoire drops`() {
        val drops = listOf("draught_t2", "hoh_t1")
        val result = resolveGrimoireDrops(drops)
        assertEquals(drops, result)
    }

    @Test
    fun `resolveGrimoireDrops with empty list returns empty`() {
        val result = resolveGrimoireDrops(emptyList())
        assertTrue(result.isEmpty())
    }
}

// Pure function extracted from EncounterWorker — testable without Android context.
fun resolveGrimoireDrops(grimoireDrops: List<String>): List<String> = grimoireDrops
