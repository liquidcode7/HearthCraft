package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage
import org.junit.Test

class EncounterEngineTest {

    private fun stage(resolve: Int = 40000, drain: Float = 16f, spike: Float = 75f,
                      spikeIv: Int = 13, phys: Float = 0f) = Stage(
        stageId = "main", label = "test", type = "attrition",
        objective = "kill", durationSec = 1500, resolve = resolve,
        drain = drain, spike = spike, spikeIntervalSec = spikeIv, physMitPct = phys
    )

    // Band stats — matches real Greycloaks level-1 starting stats (see
    // band_members.json), the current small stat scale post-redesign. These
    // used to be legacy-scale (Might 13, Vitality 15, etc.) — rescaled down
    // after Task 1/2's compound growth + multiplicative grade curve and the
    // subsequent DPS/morale/healing coefficient retuning made the old bigger
    // synthetic stats trivialize every scenario in this file.
    private fun party() = listOf(
        MemberInput("warden",  "warden",  4f, 2f, 5f, 3f, 2f),
        MemberInput("fighter", "fighter", 3f, 5f, 3f, 4f, 4f),
        MemberInput("keeper",  "keeper",  2f, 3f, 3f, 5f, 4f),
        MemberInput("captain", "captain", 3f, 2f, 4f, 5f, 4f)
    )

    @Test
    fun `keeper heals party — band survives easy encounter`() {
        // Soft encounter: low drain so Keeper healing keeps party standing
        val easyStage = stage(resolve = 15000, drain = 2f, spike = 30f, spikeIv = 20)
        var wins = 0
        repeat(200) { seed ->
            val r = EncounterEngine.resolve(easyStage, party(), seed.toLong())
            if (r.outcome == Outcome.VICTORY) wins++
        }
        assert(wins > 150) { "Expected >75% wins on easy encounter with Keeper healing, got $wins/200" }
    }

    @Test
    fun `defeat happens when encounter is overwhelming`() {
        // Extreme encounter: drain so high Keeper cannot keep up
        val brutalStage = stage(resolve = 200000, drain = 120f, spike = 300f, spikeIv = 5)
        var defeats = 0
        repeat(50) { seed ->
            val r = EncounterEngine.resolve(brutalStage, party(), seed.toLong())
            if (r.outcome == Outcome.DEFEAT) defeats++
        }
        assert(defeats > 40) { "Expected >80% defeats on brutal encounter, got $defeats/50" }
    }

    @Test
    fun `armor without draught causes stalemate or defeat`() {
        // resolve=59000 is reachable with draught penetration but not without
        val armoredStage = stage(resolve = 59000, drain = 20f, spike = 60f, spikeIv = 14, phys = 35f)
        var victories = 0
        repeat(100) { seed ->
            val r = EncounterEngine.resolve(armoredStage, party(), seed.toLong())
            if (r.outcome == Outcome.VICTORY) victories++
        }
        assert(victories < 10) { "Expected almost no victories without draught vs armored, got $victories/100" }
    }

    @Test
    fun `draught penetration improves armored encounter`() {
        // Marginal without draught, reachable with draught=60 penetrating physMitPct=35
        val armoredStage = stage(resolve = 15000, drain = 2f, spike = 30f, spikeIv = 20, phys = 35f)
        val partyWithDraught = party().map { it.copy(draughtPotency = 60f) }
        var victories = 0
        repeat(100) { seed ->
            val r = EncounterEngine.resolve(armoredStage, partyWithDraught, seed.toLong())
            if (r.outcome == Outcome.VICTORY) victories++
        }
        assert(victories > 20) { "Expected >20% wins with draught penetration, got $victories/100" }
    }

    @Test
    fun `warden guard count never exceeds WARD_CAP`() {
        val easyStage = stage(resolve = 20000, drain = 8f, spike = 40f, spikeIv = 5)
        repeat(50) { seed ->
            val r = EncounterEngine.resolve(easyStage, party(), seed.toLong())
            assert(r.wardGuardsUsed <= 3) { "wardGuardsUsed ${r.wardGuardsUsed} exceeded WARD_CAP 3" }
        }
    }

    @Test
    fun `keeper triage fires when member is below 25 percent health`() {
        // Run with a stage where spike brings someone low quickly — Keeper must triage
        val spikeHeavy = stage(resolve = 100000, drain = 5f, spike = 180f, spikeIv = 3)
        var rescuesOrWards = 0
        repeat(100) { seed ->
            val r = EncounterEngine.resolve(spikeHeavy, party(), seed.toLong())
            rescuesOrWards += r.rescuesUsed + r.wardGuardsUsed
        }
        assert(rescuesOrWards > 0) { "Expected Keeper to rescue or Warden to guard during heavy-spike encounter" }
    }

    @Test
    fun `high fate band wins more often than low fate band on same encounter`() {
        // Marginal encounter at the current small stat scale, tuned so streak/
        // Inspiration Fate effects show real differentiation.
        val midStage = stage(resolve = 70000, drain = 4f, spike = 25f, spikeIv = 12)
        // Low fate: all members fate=2
        val lowFate = party().map { it.copy(fate = 2f) }
        // High fate: all members fate=20
        val highFate = party().map { it.copy(fate = 20f) }

        var winsLow = 0; var winsHigh = 0
        repeat(300) { seed ->
            if (EncounterEngine.resolve(midStage, lowFate, seed.toLong()).outcome == Outcome.VICTORY) winsLow++
            if (EncounterEngine.resolve(midStage, highFate, seed.toLong()).outcome == Outcome.VICTORY) winsHigh++
        }
        assert(winsHigh > winsLow) {
            "Expected high-fate band to win more: low=$winsLow, high=$winsHigh (out of 300)"
        }
    }

    @Test
    fun `food stat boost increases DPS and improves outcome`() {
        // Marginal encounter where stat boosts make a measurable difference
        val midStage = stage(resolve = 78000, drain = 4f, spike = 25f, spikeIv = 12)
        // No food bonus
        var winsNoFood = 0
        repeat(200) { seed ->
            val r = EncounterEngine.resolve(midStage, party(), seed.toLong())
            if (r.outcome == Outcome.VICTORY) winsNoFood++
        }
        // Might +4 on warden and fighter — expect meaningfully higher win rate
        val boosted = party().map { m ->
            when (m.role) {
                "warden"  -> m.copy(might = m.might + 4f)
                "fighter" -> m.copy(might = m.might + 4f)
                else      -> m
            }
        }
        var winsBoosted = 0
        repeat(200) { seed ->
            val r = EncounterEngine.resolve(midStage, boosted, seed.toLong())
            if (r.outcome == Outcome.VICTORY) winsBoosted++
        }
        assert(winsBoosted > winsNoFood) {
            "Expected food-boosted band to win more: no-food=$winsNoFood, boosted=$winsBoosted (out of 200)"
        }
    }
}
