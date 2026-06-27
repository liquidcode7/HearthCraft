package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

class EncounterEngineTest {

    private val neekerbreekerStage = Stage(
        stageId = "main", label = "test", type = "attrition",
        objective = "kill", durationSec = 1500, resolve = 40000,
        drain = 16f, spike = 75f, spikeIntervalSec = 13, physMitPct = 0f
    )

    private fun party(hps: Float) = listOf(
        MemberInput("warden",  "warden",  13f, 6f,  15f, 7f,  6f,  hps),
        MemberInput("hunter",  "hunter",  9f,  15f, 7f,  5f,  9f,  hps),
        MemberInput("keeper",  "keeper",  5f,  7f,  9f,  15f, 12f, hps),
        MemberInput("captain", "captain", 8f,  7f,  12f, 13f, 13f, hps)
    )

    @Test
    fun `high food guarantees victory in neekerbreekers`() {
        // FL4 food (5.6 HP/s) should win ~91% — run 200 times, expect majority wins
        var wins = 0
        repeat(200) { seed ->
            val result = EncounterEngine.resolve(neekerbreekerStage, party(5.6f), seed.toLong())
            if (result.outcome == Outcome.VICTORY) wins++
        }
        assert(wins > 150) { "Expected >75% wins at FL4, got ${wins}/200" }
    }

    @Test
    fun `no food results in defeat in neekerbreekers`() {
        // 0 HP/s — band bleeds out every time
        var defeats = 0
        repeat(50) { seed ->
            val result = EncounterEngine.resolve(neekerbreekerStage, party(0f), seed.toLong())
            if (result.outcome == Outcome.DEFEAT) defeats++
        }
        assertEquals("Expected all defeats with no food", 50, defeats)
    }

    @Test
    fun `goblin armor blocks kill without draught`() {
        val goblinStage = Stage(
            stageId = "main", label = "test", type = "attrition",
            objective = "kill", durationSec = 1500, resolve = 68000,
            drain = 20f, spike = 60f, spikeIntervalSec = 14, physMitPct = 35f
        )
        // FL5 food (6.0 HP/s), no draught — expect stalemate or defeat, never victory
        var victories = 0
        repeat(100) { seed ->
            val result = EncounterEngine.resolve(goblinStage, party(6.0f), seed.toLong())
            if (result.outcome == Outcome.VICTORY) victories++
        }
        assert(victories < 10) { "Expected almost no victories without draught, got $victories/100" }
    }
}
