package com.liquidcode7.hearthcraft.data.quality

import kotlin.random.Random

object QualityRoll {

    // TODO(tuning): These weights are PLACEHOLDERS. Wes / the sim must supply
    // the real per-level distribution curve. Shape: low level → heavy
    // Crude/Common with thin Pristine tail; high level → curve shifts up.
    // Format: weights[grade_ordinal] = relative weight (must be > 0).
    private val WEIGHTS = intArrayOf(60, 25, 10, 4, 1) // Crude,Common,Fine,Superb,Pristine

    // TODO(tuning): Aid uplift shift is a PLACEHOLDER.
    // This flat +20 applied to Common..Pristine is a stub. Real value from sim.
    // TUNING HAZARD: AID_SHIFT is applied per-element with coerceAtMost(100).
    // If AID_SHIFT rises to ~90, Common and Fine both hit the 100 cap and become
    // equally probable — collapsing the intended gradient. Use a proportional
    // shift or apply the cap to the total, not individual weights.
    private const val AID_SHIFT = 20

    fun roll(gatheringLevel: Int, aidActive: Boolean, rng: Random = Random): Int {
        val w = WEIGHTS.copyOf()
        // TODO(tuning): gatheringLevel should slide the curve center; not implemented yet.
        if (aidActive) {
            for (i in Grade.COMMON..Grade.PRISTINE) w[i] = (w[i] + AID_SHIFT).coerceAtMost(100)
        }
        val total = w.sum()
        var pick = rng.nextInt(total)
        for (i in w.indices) {
            pick -= w[i]
            if (pick < 0) return i
        }
        error("QualityRoll: weights must be positive and sum > 0")
    }
}
