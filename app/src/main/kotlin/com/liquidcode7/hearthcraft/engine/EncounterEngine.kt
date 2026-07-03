package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.GrievousWoundSpec
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

// Keeper healing constants — must match tools/sim/run_sim.js exactly
private const val HOT_DURATION    = 8      // ticks per HoT application
private const val HOT_HEAL_MUL    = 0.15f  // healPerTick = keeper.will × HOT_HEAL_MUL
private const val TRIAGE_HP       = 0.25f  // triage fires when target hp < maxHp × TRIAGE_HP
private const val TRIAGE_MUL      = 2.0f   // triageHeal = keeper.will × TRIAGE_MUL
private const val TRIAGE_COOLDOWN = 2      // 1-tick minimum gap between triage uses (set to 2 → decrements before check)
private const val GROUP_HEAL_IV   = 20     // ticks between group heals
private const val GROUP_HEAL_MUL  = 0.5f   // groupHeal per member = keeper.will × GROUP_HEAL_MUL

// Streak constants — must match tools/sim/run_sim.js exactly
private const val STREAK_K          = 0.002f // trigger prob per tick = fate × STREAK_K
private const val STREAK_REFRACTORY = 20     // ticks before streak can re-trigger
private const val STREAK_DURATION   = 5      // streak active ticks
private const val STREAK_MULT       = 1.5f   // DPS/heal multiplier during streak

enum class Outcome { VICTORY, DEFEAT, STALEMATE }

data class MemberInput(
    val id: String,
    val role: String,           // "warden"|"fighter"|"keeper"|"captain"
    val might: Float,
    val agility: Float,
    val vitality: Float,
    val will: Float,
    val fate: Float,
    val draughtPotency: Float = 0f,
    val recoveryBuffMult: Float = 1.0f  // post-recovery incoming heal multiplier; 1.0 = no buff
)

data class EncounterResult(
    val outcome: Outcome,
    val woundsByMember: Map<String, Int>,
    val rescuesUsed: Int,
    val wardGuardsUsed: Int,
    val resolveRemainingFraction: Float,  // 0.0 = killed, 1.0 = untouched
    val endedAtSec: Int,                  // game-time tick when fight concluded
    val grievousWoundTypes: Map<String, List<String>> = emptyMap(), // memberId → resolved wound type ids
    val damageByMember: Map<String, Float> = emptyMap(), // total damage dealt to boss per member
    val healingByMember: Map<String, Float> = emptyMap(), // total healing output per member (keeper)
    val keeperHealTicks: Int = 0,  // ticks keeper spent on a healing action
    val keeperDpsTicks: Int = 0,   // ticks keeper spent on DPS
    val memberMaxHp: Map<String, Float> = emptyMap(), // memberId → maxHp (static; used by replay UI)
    val snapshots: List<TickSnapshot> = emptyList()   // per-tick state for in-progress replay
)

object EncounterEngine {

    fun resolve(stage: Stage, members: List<MemberInput>, seed: Long = System.nanoTime(), grievousWoundSpecs: List<GrievousWoundSpec> = emptyList()): EncounterResult {
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
            var grievous: Boolean = false,
            val recoveryBuffMult: Float = input.recoveryBuffMult
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

        // Per-member combat accumulators — surfaced on EncounterResult for the recap UI.
        val damageAcc   = party.associate { it.input.id to 0f }.toMutableMap()
        val healingAcc  = party.associate { it.input.id to 0f }.toMutableMap()
        var keeperHealTicksCount = 0
        var keeperDpsTicksCount  = 0
        val snapshots = mutableListOf<TickSnapshot>()
        var nextSpike = (stage.spikeIntervalSec * rng.nextDouble(0.5, 1.5)).toFloat()
        var nextSiphon = if (stage.siphonIntervalSec > 0)
            (stage.siphonIntervalSec * rng.nextDouble(0.5, 1.5)).toFloat() else Float.MAX_VALUE
        val warden = party.find { it.input.role == "warden" }
        val keeper = party.find { it.input.role == "keeper" }

        // Keeper HoT state — two independent slots
        data class HoT(val targetId: String, var ticksLeft: Int, val healPerTick: Float)
        var hot1: HoT? = null
        var hot2: HoT? = null
        var triageCooldown = 0
        var groupHealTimer = GROUP_HEAL_IV

        // Per-member streak state
        data class Streak(var refractory: Int = 0, var active: Int = 0)
        val streaks = party.associate { it.input.id to Streak() }.toMutableMap()

        for (t in 1..stage.durationSec) {
            // ── Keeper HoT ticks ──────────────────────────────────────────────────
            if (keeper != null && !keeper.grievous && keeper.hp > 0) {
                val keepStreak = streaks[keeper.input.id]
                val healMult = if (keepStreak != null && keepStreak.active > 0) STREAK_MULT else 1f
                for (hot in listOfNotNull(hot1, hot2)) {
                    val target = party.find { it.input.id == hot.targetId }
                    if (target != null && !target.grievous && target.hp > 0) {
                        val before = target.hp
                        target.hp = min(target.maxHp, target.hp + hot.healPerTick * healMult * target.recoveryBuffMult)
                        healingAcc[keeper.input.id] = (healingAcc[keeper.input.id] ?: 0f) + (target.hp - before)
                    }
                    hot.ticksLeft--
                }
                if (hot1?.ticksLeft == 0) hot1 = null
                if (hot2?.ticksLeft == 0) hot2 = null
            }

            // ── Streak updates ────────────────────────────────────────────────────
            for (m in active()) {
                val s = streaks[m.input.id] ?: continue
                when {
                    s.active > 0     -> s.active--
                    s.refractory > 0 -> s.refractory--
                    else -> if (rng.nextFloat() < m.input.fate * STREAK_K) {
                        s.active = STREAK_DURATION
                        s.refractory = STREAK_REFRACTORY
                    }
                }
            }

            // ── DPS against boss (non-keeper) ────────────────────────────────────
            val jMul = 1f + rng.nextFloat() * 2 * JITTER - JITTER
            var nonKeeperDps = 0f
            for (m in standing().filter { it.input.role != "keeper" }) {
                val s = streaks[m.input.id]
                val mult = if (s != null && s.active > 0) STREAK_MULT else 1f
                val dmg = rawDps(m.input).toFloat() * mult * (1f - effArmor) * jMul
                nonKeeperDps += dmg
                damageAcc[m.input.id] = (damageAcc[m.input.id] ?: 0f) + dmg
            }
            boss -= nonKeeperDps
            if (boss <= 0f) {
                snapshots.add(TickSnapshot(tick = t, bossResolve = 0f, memberHp = party.associate { it.input.id to maxOf(0f, it.hp) }))
                return EncounterResult(
                    outcome = Outcome.VICTORY,
                    woundsByMember = party.associate { it.input.id to it.wounds },
                    rescuesUsed = rescues, wardGuardsUsed = wardGuards,
                    resolveRemainingFraction = 0f, endedAtSec = t,
                    grievousWoundTypes = buildGrievousWoundMap(party.filter { it.grievous }.map { it.input.id }, grievousWoundSpecs, rng),
                    damageByMember = damageAcc.toMap(),
                    healingByMember = healingAcc.toMap(),
                    keeperHealTicks = keeperHealTicksCount,
                    keeperDpsTicks = keeperDpsTicksCount,
                    memberMaxHp = party.associate { it.input.id to it.maxHp },
                    snapshots = snapshots
                )
            }

            // ── Drain ─────────────────────────────────────────────────────────
            // Each member always soaks drain/4. Losing a member never increases
            // pressure on survivors — the cascade was removed by design.
            val drainPerMember = stage.drain / 4f
            for (m in standing()) {
                applyDamage(m, drainPerMember)
            }
            // TODO(v2): shadow drain tick — apply SHADOW_RATE drain to Will/Fate toward SHADOW_FLOOR per stage.shadow severity

            // ── Siphon ────────────────────────────────────────────────────────
            // Blood-speaker drains a random member (siphonDamage) and independently
            // refills boss resolve (siphonRefill). Decoupled so party damage and
            // resolve refill can be tuned separately.
            if (t >= nextSiphon && stage.siphonIntervalSec > 0) {
                val standingList = standing()
                if (standingList.isNotEmpty()) {
                    if (stage.siphonDamage > 0f) {
                        val target = standingList.random(rng)
                        val siphonRoll = stage.siphonDamage * rng.nextFloat(0.8f, 1.2f)
                        applyDamage(target, siphonRoll)
                    }
                    if (stage.siphonRefill > 0f) {
                        val refillRoll = stage.siphonRefill * rng.nextFloat(0.8f, 1.2f)
                        boss = min(stage.resolve.toFloat(), boss + refillRoll)
                    }
                }
                nextSiphon = t + (stage.siphonIntervalSec * rng.nextDouble(0.5, 1.5)).toFloat()
            }

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
                    rescued.hp = (40f + keeper.input.will * 4f) * rescued.recoveryBuffMult
                    rescues++
                }
            }

            // ── Keeper action ─────────────────────────────────────────────────────
            if (keeper != null && !keeper.grievous && keeper.hp > 0) {
                val healPerTick = keeper.input.will * HOT_HEAL_MUL
                val keepStreak = streaks[keeper.input.id]
                val healMult = if (keepStreak != null && keepStreak.active > 0) STREAK_MULT else 1f
                if (triageCooldown > 0) triageCooldown--
                groupHealTimer--

                val actionTaken: Boolean
                when {
                    groupHealTimer <= 0 -> {
                        val groupHeal = keeper.input.will * GROUP_HEAL_MUL * healMult
                        var totalGroupHeal = 0f
                        standing().forEach { m ->
                            val h = min(m.maxHp, m.hp + groupHeal * m.recoveryBuffMult) - m.hp
                            m.hp += h
                            totalGroupHeal += h
                        }
                        healingAcc[keeper.input.id] = (healingAcc[keeper.input.id] ?: 0f) + totalGroupHeal
                        groupHealTimer = GROUP_HEAL_IV
                        actionTaken = true
                        keeperHealTicksCount++
                    }
                    else -> {
                        val triageTarget = standing()
                            .filter { it.input.id != keeper.input.id }
                            .firstOrNull { it.hp < TRIAGE_HP * it.maxHp }
                        if (triageTarget != null && triageCooldown == 0) {
                            val before = triageTarget.hp
                            triageTarget.hp = min(triageTarget.maxHp,
                                triageTarget.hp + keeper.input.will * TRIAGE_MUL * healMult * triageTarget.recoveryBuffMult)
                            healingAcc[keeper.input.id] = (healingAcc[keeper.input.id] ?: 0f) + (triageTarget.hp - before)
                            triageCooldown = TRIAGE_COOLDOWN
                            actionTaken = true
                            keeperHealTicksCount++
                        } else {
                            if (hot1 == null || hot2 == null) {
                                val covered = setOfNotNull(hot1?.targetId, hot2?.targetId)
                                val hotTarget = standing()
                                    .filter { it.input.id !in covered }
                                    .minByOrNull { it.hp / it.maxHp }
                                if (hotTarget != null) {
                                    val h = HoT(hotTarget.input.id, HOT_DURATION, healPerTick)
                                    if (hot1 == null) hot1 = h else hot2 = h
                                }
                            }
                            actionTaken = false
                        }
                    }
                }
                // Keeper DPS only when no consuming action taken
                if (!actionTaken) {
                    val keeperDmg = rawDps(keeper.input) * healMult * (1f - effArmor) * jMul
                    boss -= keeperDmg
                    damageAcc[keeper.input.id] = (damageAcc[keeper.input.id] ?: 0f) + keeperDmg
                    keeperDpsTicksCount++
                }
            }

            // Victory check after keeper DPS
            if (boss <= 0f) {
                snapshots.add(TickSnapshot(tick = t, bossResolve = 0f, memberHp = party.associate { it.input.id to maxOf(0f, it.hp) }))
                return EncounterResult(
                    outcome = Outcome.VICTORY,
                    woundsByMember = party.associate { it.input.id to it.wounds },
                    rescuesUsed = rescues, wardGuardsUsed = wardGuards,
                    resolveRemainingFraction = 0f, endedAtSec = t,
                    grievousWoundTypes = buildGrievousWoundMap(party.filter { it.grievous }.map { it.input.id }, grievousWoundSpecs, rng),
                    damageByMember = damageAcc.toMap(),
                    healingByMember = healingAcc.toMap(),
                    keeperHealTicks = keeperHealTicksCount,
                    keeperDpsTicks = keeperDpsTicksCount,
                    memberMaxHp = party.associate { it.input.id to it.maxHp },
                    snapshots = snapshots
                )
            }

            // ── Tick snapshot for in-progress replay ─────────────────────────
            snapshots.add(TickSnapshot(
                tick = t,
                bossResolve = maxOf(0f, boss),
                memberHp = party.associate { it.input.id to maxOf(0f, it.hp) }
            ))

            // ── Check defeat ──────────────────────────────────────────────────
            if (standing().isEmpty()) {
                return EncounterResult(
                    outcome = Outcome.DEFEAT,
                    woundsByMember = party.associate { it.input.id to it.wounds },
                    rescuesUsed = rescues, wardGuardsUsed = wardGuards,
                    resolveRemainingFraction = boss / stage.resolve, endedAtSec = t,
                    grievousWoundTypes = buildGrievousWoundMap(party.filter { it.grievous }.map { it.input.id }, grievousWoundSpecs, rng),
                    damageByMember = damageAcc.toMap(),
                    healingByMember = healingAcc.toMap(),
                    keeperHealTicks = keeperHealTicksCount,
                    keeperDpsTicks = keeperDpsTicksCount,
                    memberMaxHp = party.associate { it.input.id to it.maxHp },
                    snapshots = snapshots
                )
            }
        }

        // Duration expired
        val finalOutcome = if (boss <= 0f) Outcome.VICTORY else Outcome.STALEMATE
        return EncounterResult(
            outcome = finalOutcome,
            woundsByMember = party.associate { it.input.id to it.wounds },
            rescuesUsed = rescues, wardGuardsUsed = wardGuards,
            resolveRemainingFraction = max(0f, boss / stage.resolve), endedAtSec = stage.durationSec,
            grievousWoundTypes = buildGrievousWoundMap(party.filter { it.grievous }.map { it.input.id }, grievousWoundSpecs, rng),
            damageByMember = damageAcc.toMap(),
            healingByMember = healingAcc.toMap(),
            keeperHealTicks = keeperHealTicksCount,
            keeperDpsTicks = keeperDpsTicksCount,
            memberMaxHp = party.associate { it.input.id to it.maxHp },
            snapshots = snapshots
        )
    }

    // ── Wound type rolling ────────────────────────────────────────────────────

    // Rolls which wound types apply when a member reaches the grievous threshold.
    // Guaranteed types always apply; chance types are rolled independently.
    // Safety net: if all chance rolls miss and specs is non-empty, the first spec
    // is used as a fallback so the result is never empty.
    internal fun rollWoundTypes(specs: List<GrievousWoundSpec>, rng: Random): List<String> {
        val result = mutableListOf<String>()
        for (spec in specs) {
            if (spec.guaranteed || rng.nextFloat() < spec.chance) {
                result += spec.woundType
            }
        }
        if (result.isEmpty() && specs.isNotEmpty()) result += specs.first().woundType
        return result
    }

    // Builds the per-member wound-type map from the list of grievous member ids.
    // All grievous members share the same encounter-level specs (one roll per member).
    private fun buildGrievousWoundMap(
        grievousIds: List<String>,
        specs: List<GrievousWoundSpec>,
        rng: Random
    ): Map<String, List<String>> {
        if (specs.isEmpty()) return emptyMap()
        return grievousIds.associateWith { rollWoundTypes(specs, rng) }
    }

    // ── Top-level helpers (no MS dependency) ─────────────────────────────────

    private fun morale(m: MemberInput): Float = (30f + m.vitality * 16f).roundToInt().toFloat()

    private fun rawDps(m: MemberInput): Float = when (m.role) {
        "warden"  -> m.might * 0.5f
        "fighter" -> m.agility + m.might * 0.4f
        "keeper"  -> m.will * 0.9f
        "captain" -> m.might * 0.3f + m.will * 0.2f
        else      -> 0f
    }

    private fun Random.nextFloat(lo: Float, hi: Float): Float =
        lo + nextFloat() * (hi - lo)

    // Computes the incoming heal multiplier for a member who has a pending recovery buff.
    // grade × buffStep(tier) added on top of the 1.0 base.
    fun recoveryBuffMultiplier(grade: Int, tier: Int): Float {
        val step = when (tier) {
            1    -> 0.05f
            2    -> 0.07f
            3    -> 0.10f
            4    -> 0.14f
            else -> 0.05f
        }
        return 1.0f + grade * step
    }
}
