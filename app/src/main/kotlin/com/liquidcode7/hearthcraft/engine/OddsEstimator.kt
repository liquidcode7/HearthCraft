// app/src/main/kotlin/com/liquidcode7/hearthcraft/engine/OddsEstimator.kt
package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage

enum class OddsLabel { OUTMATCHED, EVEN_FIGHT, FAVORED, CRUSHING }

// Live pre-battle odds estimate: samples the real encounter simulation many
// times with varying seeds and buckets the resulting win rate into a
// plain-language label. Cutoffs are starting points, tunable later via the
// balance harness — not locked.
object OddsEstimator {
    fun estimate(stage: Stage, members: List<MemberInput>, trials: Int = 75): OddsLabel {
        if (members.isEmpty()) return OddsLabel.OUTMATCHED
        var wins = 0
        for (i in 0 until trials) {
            val result = EncounterEngine.resolve(stage, members, seed = i.toLong())
            if (result.outcome == Outcome.VICTORY) wins++
        }
        val winRate = wins.toFloat() / trials
        return when {
            winRate < 0.25f -> OddsLabel.OUTMATCHED
            winRate < 0.60f -> OddsLabel.EVEN_FIGHT
            winRate < 0.85f -> OddsLabel.FAVORED
            else -> OddsLabel.CRUSHING
        }
    }
}
