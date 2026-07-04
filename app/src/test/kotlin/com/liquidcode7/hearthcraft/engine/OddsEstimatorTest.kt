// app/src/test/kotlin/com/liquidcode7/hearthcraft/engine/OddsEstimatorTest.kt
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage
import org.junit.Test
import org.junit.Assert.assertEquals

class OddsEstimatorTest {

    private fun stage(resolve: Int, drain: Float, spike: Float, spikeIv: Int) = Stage(
        stageId = "main", label = "test", type = "attrition",
        objective = "kill", durationSec = 1500, resolve = resolve,
        drain = drain, spike = spike, spikeIntervalSec = spikeIv, physMitPct = 0f
    )

    private fun party() = listOf(
        MemberInput("warden",  "warden",  4f, 2f, 5f, 3f, 2f),
        MemberInput("fighter", "fighter", 3f, 5f, 3f, 4f, 4f),
        MemberInput("keeper",  "keeper",  2f, 3f, 3f, 5f, 4f),
        MemberInput("captain", "captain", 3f, 2f, 4f, 5f, 4f)
    )

    @Test
    fun `an overwhelming encounter is labeled Outmatched`() {
        val brutalStage = stage(resolve = 200000, drain = 120f, spike = 300f, spikeIv = 5)
        assertEquals(OddsLabel.OUTMATCHED, OddsEstimator.estimate(brutalStage, party()))
    }

    @Test
    fun `an easy encounter is labeled Favored or Crushing`() {
        val easyStage = stage(resolve = 15000, drain = 2f, spike = 30f, spikeIv = 20)
        val label = OddsEstimator.estimate(easyStage, party())
        assert(label == OddsLabel.FAVORED || label == OddsLabel.CRUSHING) {
            "Expected Favored or Crushing, got $label"
        }
    }

    @Test
    fun `an empty band is always Outmatched`() {
        val anyStage = stage(resolve = 100, drain = 1f, spike = 1f, spikeIv = 20)
        assertEquals(OddsLabel.OUTMATCHED, OddsEstimator.estimate(anyStage, emptyList()))
    }
}
