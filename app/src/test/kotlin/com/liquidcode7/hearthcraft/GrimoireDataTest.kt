package com.liquidcode7.hearthcraft

import com.liquidcode7.hearthcraft.data.model.Grimoire
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GrimoireDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `grimoires json has three entries with correct ids`() {
        val raw = javaClass.classLoader!!.getResourceAsStream("data/grimoires.json")!!
            .bufferedReader().readText()
        val grimoires: List<Grimoire> = json.decodeFromString(raw)
        assertEquals(3, grimoires.size)
        val ids = grimoires.map { it.id }.toSet()
        assertTrue(ids.contains("cooking_t2"))
        assertTrue(ids.contains("draught_t2"))
        assertTrue(ids.contains("hoh_t1"))
    }

    @Test
    fun `grimoire ids follow class_tTier convention`() {
        val raw = javaClass.classLoader!!.getResourceAsStream("data/grimoires.json")!!
            .bufferedReader().readText()
        val grimoires: List<Grimoire> = json.decodeFromString(raw)
        grimoires.forEach { g ->
            assertEquals("${g.grimoireClass}_t${g.tier}", g.id)
        }
    }

    @Test
    fun `wolf master has correct recLevel and grimoireDrops`() {
        val raw = javaClass.classLoader!!.getResourceAsStream("data/encounters.json")!!
            .bufferedReader().readText()
        // Use raw JSON check to avoid needing Encounter.kt changes to be in this module
        assertTrue(raw.contains("\"draught_t2\""))
        assertTrue(raw.contains("\"hoh_t1\""))
        // recLevel 7 for wolf_master
        assertTrue(raw.contains("greycloaks_wolf_master"))
        val wolfIdx = raw.indexOf("greycloaks_wolf_master")
        val snippet = raw.substring(wolfIdx, wolfIdx + 200)
        assertTrue("recLevel should be 7 for wolf_master", snippet.contains("\"recLevel\": 7") || snippet.contains("\"recLevel\":7"))
    }

    @Test
    fun `rhudaur men has correct recLevel and grimoire drop`() {
        val raw = javaClass.classLoader!!.getResourceAsStream("data/encounters.json")!!
            .bufferedReader().readText()
        val rhIdx = raw.indexOf("greycloaks_rhudaur_men")
        val snippet = raw.substring(rhIdx, rhIdx + 200)
        assertTrue("recLevel should be 9 for rhudaur_men", snippet.contains("\"recLevel\": 9") || snippet.contains("\"recLevel\":9"))
        assertTrue(raw.contains("\"cooking_t2\""))
    }
}
