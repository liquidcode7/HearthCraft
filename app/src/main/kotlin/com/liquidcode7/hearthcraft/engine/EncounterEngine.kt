package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.Stage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// All constants must match tools/sim/run_sim.js exactly
private const val PEN_SCALE    = 80f
private const val RESCUE_CAP   = 5
private const val WARD_CAP     = 3
private const val GRIEVOUS     = 5
private const val RMAX         = 50f
private const val JITTER       = 0.10f
private const val SHADOW_FLOOR = 0.55f   // matches run_sim.js — used when shadow encounters are added (V2)
private const val SHADOW_RATE  = 0.0011f // per-tick stat drain per point of shadow severity (V2)

enum class Outcome { VICTORY, DEFEAT, STALEMATE }

data class MemberInput(
    val id: String,
    val role: String,       // "warden"|"hunter"|"keeper"|"captain"
    val might: Float,
    val agility: Float,
    val vitality: Float,
    val will: Float,
    val fate: Float,
    val hps: Float,         // food HP/s for this member
    val draughtPotency: Float = 0f
)

data class EncounterResult(
    val outcome: Outcome,
    val woundsByMember: Map<String, Int>,
    val rescuesUsed: Int,
    val wardGuardsUsed: Int,
    val resolveRemainingFraction: Float  // 0.0 = killed, 1.0 = untouched
)

object EncounterEngine {

    fun resolve(stage: Stage, members: List<MemberInput>, seed: Long = System.nanoTime()): EncounterResult {
        val rng = Random(seed)
        val physMit = stage.physMitPct / 100f
        // Draught is party-wide (docs/combat-model.md: "one draught choice per encounter, applied to all party members").
        // All members receive the same value from BandRepository.memberInputsForBand — reading first() is safe.
        val draughtPotency = members.firstOrNull()?.draughtPotency ?: 0f
        val effArmor = physMit * (1f - min(1f, draughtPotency / PEN_SCALE))

        // Member state — MS is intentionally defined inside resolve()
        data class MS(
            val input: MemberInput,
            var hp: Float = morale(input),
            val maxHp: Float = morale(input),
            var reserve: Float = 0f,
            var wounds: Int = 0,
            var grievous: Boolean = false
        )

        // Local helpers that operate on the inner MS class
        fun wouldDown(m: MS, damage: Float): Boolean =
            (m.hp - max(0f, damage - m.reserve)) <= 0f

        fun applyDamage(m: MS, damage: Float) {
            val absorbedByReserve = min(m.reserve, damage)
            m.reserve -= absorbedByReserve
            val remainder = damage - absorbedByReserve
            if (remainder > 0f && m.hp > 0f) {
                val wasAbove = m.hp > 0f
                m.hp -= remainder
                if (wasAbove && m.hp <= 0f) {
                    m.wounds++
                    if (m.wounds >= GRIEVOUS) m.grievous = true
                }
            }
        }

        val party = members.map { MS(it) }.toMutableList()
        val standing  = { party.filter { !it.grievous && it.hp > 0 } }
        val active    = { party.filter { !it.grievous } }

        var boss = stage.resolve.toFloat()
        var rescues = 0
        var wardGuards = 0
        var nextSpike = (stage.spikeIntervalSec * rng.nextDouble(0.5, 1.5)).toFloat()
        val warden = party.find { it.input.role == "warden" }
        val keeper = party.find { it.input.role == "keeper" }

        for (t in 1..stage.durationSec) {
            // ── Food healing ──────────────────────────────────────────────────
            for (m in active()) {
                val heal = m.input.hps
                val overflow = max(0f, (m.hp + heal) - m.maxHp)
                m.hp = min(m.maxHp, m.hp + heal)
                m.reserve = min(RMAX, m.reserve + overflow)
            }

            // ── DPS against boss ──────────────────────────────────────────────
            val rawDps = standing().sumOf { rawDps(it.input).toDouble() }.toFloat()
            val jMul = 1f + rng.nextFloat() * 2 * JITTER - JITTER
            val effDps = rawDps * (1f - effArmor) * jMul
            boss -= effDps
            if (boss <= 0f) {
                return EncounterResult(
                    Outcome.VICTORY,
                    party.associate { it.input.id to it.wounds },
                    rescues, wardGuards, 0f
                )
            }

            // ── Drain ─────────────────────────────────────────────────────────
            val standingCount = max(1, standing().size)
            val drainPerMember = stage.drain / standingCount.toFloat()
            for (m in standing()) {
                applyDamage(m, drainPerMember)
            }
            // TODO(v2): shadow drain tick — apply SHADOW_RATE drain to Will/Fate toward SHADOW_FLOOR per stage.shadow severity

            // ── Spike ─────────────────────────────────────────────────────────
            if (t >= nextSpike) {
                val spikeRoll = stage.spike * rng.nextFloat(0.7f, 1.3f)
                val standingList = standing()
                if (standingList.isNotEmpty()) {
                    val target = standingList.random(rng)
                    val isKeeperTarget = target.input.role == "keeper"
                    val wardenCanGuard = warden != null && !warden.grievous && warden.hp > 0 &&
                        warden.input.id != target.input.id && wardGuards < WARD_CAP

                    if (isKeeperTarget && wardenCanGuard && wouldDown(target, spikeRoll)) {
                        // Warden intercepts killing blow on Keeper
                        applyDamage(warden!!, spikeRoll)
                        wardGuards++
                    } else {
                        applyDamage(target, spikeRoll)
                    }
                }
                nextSpike = t + (stage.spikeIntervalSec * rng.nextDouble(0.5, 1.5)).toFloat()
            }

            // ── Keeper rescue ─────────────────────────────────────────────────
            if (keeper != null && !keeper.grievous && keeper.hp > 0 && rescues < RESCUE_CAP) {
                val downed = active().filter { it.hp <= 0 && it.input.id != keeper.input.id }
                if (downed.isNotEmpty()) {
                    val rescued = downed.first()
                    rescued.hp = 40f + keeper.input.will * 4f
                    rescues++
                }
            }

            // ── Check defeat ──────────────────────────────────────────────────
            if (standing().isEmpty()) {
                return EncounterResult(
                    Outcome.DEFEAT,
                    party.associate { it.input.id to it.wounds },
                    rescues, wardGuards,
                    boss / stage.resolve
                )
            }
        }

        // Duration expired
        val finalOutcome = if (boss <= 0f) Outcome.VICTORY else Outcome.STALEMATE
        return EncounterResult(
            finalOutcome,
            party.associate { it.input.id to it.wounds },
            rescues, wardGuards,
            max(0f, boss / stage.resolve)
        )
    }

    // ── Top-level helpers (no MS dependency) ─────────────────────────────────

    private fun morale(m: MemberInput): Float = (30f + m.vitality * 16f).roundToInt().toFloat()

    private fun rawDps(m: MemberInput): Float = when (m.role) {
        "warden"  -> m.might * 0.5f
        "hunter"  -> m.agility + m.might * 0.4f
        "keeper"  -> m.will * 0.9f
        "captain" -> m.might * 0.3f + m.will * 0.2f
        else      -> 0f
    }

    private fun Random.nextFloat(lo: Float, hi: Float): Float =
        lo + nextFloat() * (hi - lo)
}
