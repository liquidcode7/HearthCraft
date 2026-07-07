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
    fun `physicalFraction is 1 for warden and fighter, 0 for keeper`() {
        val warden = MemberInput("w", "warden", 4f, 2f, 5f, 3f, 2f)
        val fighter = MemberInput("f", "fighter", 3f, 5f, 3f, 4f, 4f)
        val keeper = MemberInput("k", "keeper", 2f, 3f, 3f, 5f, 4f)
        assert(EncounterEngine.physicalFraction(warden) == 1f)
        assert(EncounterEngine.physicalFraction(fighter) == 1f)
        assert(EncounterEngine.physicalFraction(keeper) == 0f)
    }

    @Test
    fun `physicalFraction for captain is stat-weighted between might and will terms`() {
        val captain = MemberInput("c", "captain", 3f, 2f, 4f, 5f, 4f)
        // CAPTAIN_MIGHT_COEF = CAPTAIN_WILL_COEF = 2.0f: physTerm = 3*2.0 = 6.0, magicTerm = 5*2.0 = 10.0, total = 16.0
        val expected = (3f * 2.0f) / (3f * 2.0f + 5f * 2.0f)
        val actual = EncounterEngine.physicalFraction(captain)
        assert(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
    }

    @Test
    fun `captain rawDps is symmetric between might and will`() {
        val mightHeavy = MemberInput("c1", "captain", 10f, 2f, 4f, 3f, 4f)
        val willHeavy  = MemberInput("c2", "captain", 3f, 2f, 4f, 10f, 4f)
        assert(kotlin.math.abs(EncounterEngine.rawDps(mightHeavy) - EncounterEngine.rawDps(willHeavy)) < 0.0001f) {
            "Expected might=10/will=3 and might=3/will=10 captains to deal equal DPS (symmetric coefficients)"
        }
    }

    @Test
    fun `captain rawDps uses the buffed symmetric coefficients`() {
        val captain = MemberInput("c", "captain", 3f, 2f, 4f, 5f, 4f)
        val expected = 3f * 2.0f + 5f * 2.0f  // CAPTAIN_MIGHT_COEF = CAPTAIN_WILL_COEF = 2.0f
        val actual = EncounterEngine.rawDps(captain)
        assert(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
    }

    @Test
    fun `fighter rawDps is identical for mirror-image might-agility builds`() {
        val ranged = MemberInput("f1", "fighter", 10f, 20f, 3f, 4f, 4f) // might=10, agility=20
        val melee  = MemberInput("f2", "fighter", 20f, 10f, 3f, 4f, 4f) // swapped
        assert(kotlin.math.abs(EncounterEngine.rawDps(ranged) - EncounterEngine.rawDps(melee)) < 0.0001f) {
            "Expected mirror-image might/agility builds to deal equal DPS (symmetric coefficient)"
        }
    }

    @Test
    fun `fighter rawDps uses the resynced FIGHTER_COEF`() {
        val fighter = MemberInput("f", "fighter", 3f, 5f, 3f, 4f, 4f) // matches party()'s fighter
        val expected = 3f * 2.33f + 5f * 2.33f  // FIGHTER_COEF = 2.33f
        val actual = EncounterEngine.rawDps(fighter)
        assert(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
    }

    @Test
    fun `physFractionByMember is 1 for warden and fighter, 0 for keeper, regardless of armor`() {
        val armoredStage = stage(resolve = 50000, drain = 4f, spike = 30f, spikeIv = 20, phys = 40f)
        val r = EncounterEngine.resolve(armoredStage, party(), seed = 1L)
        assert(r.physFractionByMember["warden"] == 1f)
        assert(r.physFractionByMember["fighter"] == 1f)
        assert(r.physFractionByMember["keeper"] == 0f)
    }

    @Test
    fun `physFractionByMember for captain is reduced by armor relative to the raw physicalFraction`() {
        val captain = MemberInput("c", "captain", 3f, 2f, 4f, 5f, 4f)
        val warden = MemberInput("w", "warden", 4f, 2f, 5f, 3f, 2f)
        val partyOf = listOf(captain, warden)
        val noArmorStage = stage(resolve = 50000, drain = 4f, spike = 30f, spikeIv = 20, phys = 0f)
        val armoredStage = stage(resolve = 50000, drain = 4f, spike = 30f, spikeIv = 20, phys = 40f)
        val rNoArmor = EncounterEngine.resolve(noArmorStage, partyOf, seed = 1L)
        val rArmored = EncounterEngine.resolve(armoredStage, partyOf, seed = 1L)
        val rawFraction = EncounterEngine.physicalFraction(captain)
        val noArmorFraction = rNoArmor.physFractionByMember["c"] ?: 0f
        val armoredFraction = rArmored.physFractionByMember["c"] ?: 0f
        assert(kotlin.math.abs(noArmorFraction - rawFraction) < 0.0001f) {
            "Expected no-armor fraction $noArmorFraction to equal raw fraction $rawFraction"
        }
        assert(armoredFraction < rawFraction) {
            "Expected armored fraction $armoredFraction to be less than raw fraction $rawFraction"
        }
    }

    @Test
    fun `final tick snapshot's cumulative damage and heal match the result totals`() {
        val midStage = stage(resolve = 40000, drain = 4f, spike = 25f, spikeIv = 12)
        val r = EncounterEngine.resolve(midStage, party(), seed = 42L)
        val last = r.snapshots.last()
        r.damageByMember.forEach { (id, total) ->
            val fromSnapshot = last.cumDamage[id] ?: 0f
            assert(kotlin.math.abs(total - fromSnapshot) < 0.001f) {
                "cumDamage mismatch for $id: result=$total snapshot=$fromSnapshot"
            }
        }
        r.healingByMember.forEach { (id, total) ->
            val fromSnapshot = last.cumHeal[id] ?: 0f
            assert(kotlin.math.abs(total - fromSnapshot) < 0.001f) {
                "cumHeal mismatch for $id: result=$total snapshot=$fromSnapshot"
            }
        }
    }

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
        // Inspiration Fate effects show real differentiation. resolve bumped from
        // 70000 -> 89000 after Captain's DPS buff (0.9/0.6 -> 2.0/2.0 coefficients)
        // raised overall party DPS enough that 70000 no longer discriminated
        // (both fate bands hit the win-rate ceiling).
        val midStage = stage(resolve = 89000, drain = 4f, spike = 25f, spikeIv = 12)
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
        // Marginal encounter where stat boosts make a measurable difference. resolve
        // bumped from 78000 -> 100000 after Captain's DPS buff (0.9/0.6 -> 2.0/2.0
        // coefficients) raised overall party DPS enough that 78000 no longer
        // discriminated (both the boosted and unboosted band hit the win-rate ceiling).
        val midStage = stage(resolve = 100000, drain = 4f, spike = 25f, spikeIv = 12)
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
