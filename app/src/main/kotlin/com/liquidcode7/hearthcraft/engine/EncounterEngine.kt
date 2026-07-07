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
private const val SHIELD_CAP_MUL = 10f   // shield cap = vitality × SHIELD_CAP_MUL — scales with Vitality like morale, so it stays meaningful at high level rather than a flat cap trivialized by growth
private const val JITTER       = 0.10f
private const val SHADOW_FLOOR = 0.55f   // matches run_sim.js — used when shadow encounters are added (V2)
private const val SHADOW_RATE  = 0.0011f // per-tick stat drain per point of shadow severity (V2)

// Keeper healing constants — must match tools/sim/run_sim.js exactly
private const val HOT_DURATION    = 8      // ticks per HoT application
private const val HOT_HEAL_MUL    = 0.15f  // healPerTick = keeper.will × HOT_HEAL_MUL
private const val TRIAGE_HP       = 0.25f  // triage fires when target hp < maxHp × TRIAGE_HP
private const val TRIAGE_MUL      = 2.0f   // triageHeal = keeper.will × TRIAGE_MUL
private const val TRIAGE_COOLDOWN = 2      // 1-tick minimum gap between triage uses (set to 2 → decrements before check)
private const val GROUP_HEAL_IV   = 5      // ticks between group heals
private const val GROUP_HEAL_MUL  = 1.5f   // groupHeal per member = keeper.will × GROUP_HEAL_MUL

// Streak constants — must match tools/sim/run_sim.js exactly
private const val STREAK_K          = 0.002f // trigger prob per tick = fate × STREAK_K
private const val STREAK_REFRACTORY = 20     // ticks before streak can re-trigger
private const val STREAK_DURATION   = 5      // streak active ticks
private const val STREAK_MULT       = 1.5f   // DPS/heal multiplier during streak

// Inspiration constants — see master-design.md §6.3. Rare, powerful, per-role.
// Base rates are deliberately low; the shared Fate formula and 0.35 cap apply
// to all four. Placeholders pending balance-harness validation.
private const val FATE_INSP_COEF   = 0.003f // trigger chance += ownFate × this
private const val FATE_INSP_CAP    = 0.35f  // raised from 0.25 now that Fate is un-cookable
private const val HORN_BASE        = 0.15f  // crisisEdge is a single-shot trigger (fires once per crisis onset,
                                             // not every tick during it) -- rates need to be much higher than a
                                             // per-tick roll to actually fire more than once in a rare while.
private const val HORN_DURATION    = 20     // ticks: spike redirect + Warden invulnerability (long enough to reliably eat a spike, not just whiff)
private const val DAWN_BASE        = 0.15f
private const val DAWN_DURATION    = 15     // ticks: party DPS x2 + unlocks other roles' conditions
private const val DAWN_HEAL_FRAC   = 0.30f  // fraction of max HP healed to all active members
private const val DAWN_DPS_MULT    = 2.0f
private const val DAWN_INSP_BOOST  = 0.03f  // weak: Captain unlocks conditions, doesn't mainly raise odds
private const val DAWN_BOOST_DECAY = 0.005f // per tick
private const val GRACE_BASE       = 0.15f  // same single-shot-per-onset reasoning as Horn/Dawn
private const val GRACE_MAX_USES   = 2      // hard cap per fight, not "uncapped but rare"
private const val GRACE_REVIVE_FRAC = 0.40f // HP restored, as a fraction of max
private const val BLACK_ARROW_GATE = 0.4f   // fraction of fight elapsed before eligible
private const val BLACK_ARROW_CAP  = 0.03f  // ceiling the rising chance approaches at elapsed=1.0
private const val BLACK_ARROW_RESOLVE_BURN = 0.18f // fraction of boss resolve burned (killable)
private const val BLACK_ARROW_CLOCK_BURN   = 0.15f // fraction of remaining time burned (unkillable)
private const val CRISIS_HP_FRAC   = 0.35f  // "crisis edge": 2+ standing members below this % HP

// Live-combat-visibility flash durations. Black Arrow and Grace are one-shot effects with
// no natural ongoing window (unlike Horn/Dawn), so a live UI would only see them for a
// single tick — often imperceptible at normal playback speed. These give the UI badge a
// few ticks of visibility instead.
private const val BLACK_ARROW_FLASH_TICKS = 5
private const val GRACE_FLASH_TICKS       = 5

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
    val snapshots: List<TickSnapshot> = emptyList(),  // per-tick state for in-progress replay
    val inspirationsFired: Map<String, Int> = emptyMap(), // "horn"|"dawn"|"blackArrow"|"grace" → times fired
    val physFractionByMember: Map<String, Float> = emptyMap() // memberId → post-mitigation physical fraction of damage output (static per fight; complement is the magical fraction)
)

object EncounterEngine {

    fun resolve(stage: Stage, members: List<MemberInput>, seed: Long = System.nanoTime(), grievousWoundSpecs: List<GrievousWoundSpec> = emptyList()): EncounterResult {
        val rng = Random(seed)
        val physMit = stage.physMitPct / 100f
        // Draught is party-wide (docs/combat-model.md: "one draught choice per encounter, applied to all party members").
        // All members receive the same value from BandRepository.memberInputsForBand — reading first() is safe.
        val draughtPotency = members.firstOrNull()?.draughtPotency ?: 0f
        val effArmor = physMit * (1f - min(1f, draughtPotency / PEN_SCALE))

        // Physical/magical output split, per member — static for the whole fight since
        // effArmor and stats don't change mid-encounter. Complement of this value is the
        // magical fraction. Consumed by the live-combat-visibility UI to color DPS bars.
        val physFractionByMember: Map<String, Float> = members.associate { m ->
            val physFrac = physicalFraction(m)
            val physTerm = physFrac * (1f - effArmor)
            val magicTerm = 1f - physFrac
            val total = physTerm + magicTerm
            m.id to (if (total > 0f) physTerm / total else 1f)
        }

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

        // newlyGrievousThisTick is populated by applyDamage whenever a member crosses
        // the grievous threshold — Hands of Healing's reactive trigger reads this list.
        // Cleared at the top of each tick.
        val newlyGrievousThisTick = mutableListOf<String>()

        // invulnerable is true only for the Warden while Horn of Gondor's window is
        // active — his HP floors at 1 instead of going down or wounding him.
        fun applyDamage(m: MS, damage: Float, invulnerable: Boolean = false) {
            val absorbedByReserve = min(m.reserve, damage)
            m.reserve -= absorbedByReserve
            val remainder = damage - absorbedByReserve
            if (remainder > 0f && m.hp > 0f) {
                val wasAbove = m.hp > 0f
                m.hp -= remainder
                if (invulnerable && m.hp < 1f) {
                    m.hp = 1f
                } else if (wasAbove && m.hp <= 0f) {
                    m.wounds++
                    if (m.wounds >= GRIEVOUS && !m.grievous) {
                        m.grievous = true
                        newlyGrievousThisTick.add(m.input.id)
                    }
                }
            }
        }

        // Heals a member for `amount`. Any portion that would overheal past maxHp
        // converts into reserve (a passive shield, capped per-member by their own
        // Vitality) instead of being wasted — this is what lets HoTs/group-heal
        // generate real defensive value on an already-topped-off target, rather
        // than only mattering when someone is actively low. Returns the total
        // value delivered (heal + shield granted), for the recap UI's healing
        // accumulator.
        fun applyHeal(m: MS, amount: Float): Float {
            val toHp = min(m.maxHp - m.hp, amount)
            m.hp += toHp
            val overheal = amount - toHp
            val shieldCap = m.input.vitality * SHIELD_CAP_MUL
            val toReserve = min(shieldCap - m.reserve, max(0f, overheal))
            m.reserve += toReserve
            return toHp + toReserve
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
        val fighter = party.find { it.input.role == "fighter" }
        val captain = party.find { it.input.role == "captain" }

        // Keeper HoT state — two independent slots
        data class HoT(val targetId: String, var ticksLeft: Int, val healPerTick: Float)
        var hot1: HoT? = null
        var hot2: HoT? = null
        var triageCooldown = 0
        var groupHealTimer = GROUP_HEAL_IV

        // Per-member streak state
        data class Streak(var refractory: Int = 0, var active: Int = 0)
        val streaks = party.associate { it.input.id to Streak() }.toMutableMap()

        // Inspiration state — see master-design.md §6.3.
        var hornFired = false
        var hornWindow = 0          // ticks remaining: spike redirect + Warden invulnerability
        var dawnFired = false
        var dawnWindow = 0          // ticks remaining: party DPS x1.5 + unlocks other roles' conditions
        var dawnInspBoost = 0f      // decaying bonus added to the other three's trigger chance
        var blackArrowFired = false
        var graceUses = 0
        var wasCrisis = false       // previous tick's crisis state, for edge detection
        var survivalClockBurnSec = 0 // ticks shaved off a non-"kill" objective's effective duration
        val inspirationCounts = mutableMapOf("horn" to 0, "dawn" to 0, "blackArrow" to 0, "grace" to 0)

        // Live-combat-visibility state.
        var blackArrowFlashTicks = 0
        var graceFlashTicks = 0
        var keeperHealingThisTick = false  // set once the Keeper action block below resolves for the current tick; false by default on any snapshot taken earlier in the same tick (moot — the fight already ended on those)

        // Builds a TickSnapshot from current mutable state — used at every snapshot site
        // below so new fields only ever need to be threaded through once.
        fun buildSnapshot(t: Int, bossVal: Float): TickSnapshot = TickSnapshot(
            tick = t,
            bossResolve = maxOf(0f, bossVal),
            memberHp = party.associate { it.input.id to maxOf(0f, it.hp) },
            cumDamage = damageAcc.toMap(),
            cumHeal = healingAcc.toMap(),
            memberReserve = party.associate { it.input.id to it.reserve },
            streakActive = party.filter { (streaks[it.input.id]?.active ?: 0) > 0 }.map { it.input.id }.toSet(),
            hotTargets = setOfNotNull(hot1?.targetId, hot2?.targetId),
            keeperHealing = keeperHealingThisTick,
            hornActive = hornWindow > 0,
            dawnActive = dawnWindow > 0,
            blackArrowFlash = blackArrowFlashTicks > 0,
            graceFlash = graceFlashTicks > 0
        )

        // Builds an EncounterResult from current mutable state — used at every return site
        // below so new fields only ever need to be threaded through once.
        fun buildResult(outcome: Outcome, endedAtSec: Int, resolveRemainingFraction: Float): EncounterResult = EncounterResult(
            outcome = outcome,
            woundsByMember = party.associate { it.input.id to it.wounds },
            rescuesUsed = rescues, wardGuardsUsed = wardGuards,
            resolveRemainingFraction = resolveRemainingFraction, endedAtSec = endedAtSec,
            grievousWoundTypes = buildGrievousWoundMap(party.filter { it.grievous }.map { it.input.id }, grievousWoundSpecs, rng),
            damageByMember = damageAcc.toMap(),
            healingByMember = healingAcc.toMap(),
            keeperHealTicks = keeperHealTicksCount,
            keeperDpsTicks = keeperDpsTicksCount,
            memberMaxHp = party.associate { it.input.id to it.maxHp },
            snapshots = snapshots,
            inspirationsFired = inspirationCounts.toMap(),
            physFractionByMember = physFractionByMember
        )

        for (t in 1..stage.durationSec) {
            newlyGrievousThisTick.clear()
            if (hornWindow > 0) hornWindow--
            if (dawnWindow > 0) dawnWindow--
            if (blackArrowFlashTicks > 0) blackArrowFlashTicks--
            if (graceFlashTicks > 0) graceFlashTicks--
            keeperHealingThisTick = false
            if (dawnInspBoost > 0f) dawnInspBoost = max(0f, dawnInspBoost - DAWN_BOOST_DECAY)

            // ── Keeper HoT ticks ──────────────────────────────────────────────────
            if (keeper != null && !keeper.grievous && keeper.hp > 0) {
                val keepStreak = streaks[keeper.input.id]
                val healMult = if (keepStreak != null && keepStreak.active > 0) STREAK_MULT else 1f
                for (hot in listOfNotNull(hot1, hot2)) {
                    val target = party.find { it.input.id == hot.targetId }
                    if (target != null && !target.grievous && target.hp > 0) {
                        val delivered = applyHeal(target, hot.healPerTick * healMult * target.recoveryBuffMult)
                        healingAcc[keeper.input.id] = (healingAcc[keeper.input.id] ?: 0f) + delivered
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
            val dawnDpsMult = if (dawnWindow > 0) DAWN_DPS_MULT else 1f
            var nonKeeperDps = 0f
            for (m in standing().filter { it.input.role != "keeper" }) {
                val s = streaks[m.input.id]
                val mult = (if (s != null && s.active > 0) STREAK_MULT else 1f) * dawnDpsMult
                val raw = rawDps(m.input).toFloat() * mult * jMul
                val physFrac = physicalFraction(m.input)
                val dmg = raw * physFrac * (1f - effArmor) + raw * (1f - physFrac)
                nonKeeperDps += dmg
                damageAcc[m.input.id] = (damageAcc[m.input.id] ?: 0f) + dmg
            }
            boss -= nonKeeperDps
            if (boss <= 0f) {
                snapshots.add(buildSnapshot(t, boss))
                return buildResult(Outcome.VICTORY, t, 0f)
            }

            // ── Drain ─────────────────────────────────────────────────────────
            // Each member always soaks drain/4. Losing a member never increases
            // pressure on survivors — the cascade was removed by design.
            val drainPerMember = stage.drain / 4f
            for (m in standing()) {
                applyDamage(m, drainPerMember, invulnerable = (hornWindow > 0 && m === warden))
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
                        applyDamage(target, siphonRoll, invulnerable = (hornWindow > 0 && target === warden))
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
                val hornActive = hornWindow > 0 && warden != null && !warden.grievous && warden.hp > 0
                if (hornActive) {
                    // Horn of Gondor: every spike redirects to the Warden regardless of
                    // normal targeting, and he cannot be downed by it during the window.
                    applyDamage(warden!!, spikeRoll, invulnerable = true)
                } else {
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

            // ── Inspiration ───────────────────────────────────────────────────
            // Rare, powerful, per-role. See master-design.md §6.3. Low trigger
            // chance by design — these should feel earned, not routine.
            val criticalCount = standing().count { it.hp < CRISIS_HP_FRAC * it.maxHp }
            val isCrisis = criticalCount >= 2
            val crisisEdge = isCrisis && !wasCrisis
            wasCrisis = isCrisis
            val dawnUnlocked = dawnWindow > 0

            // Warden — Horn of Gondor. Captain's Red Dawn unlocks it without crisisEdge.
            if (warden != null && !warden.grievous && warden.hp > 0 && !hornFired && (crisisEdge || dawnUnlocked)) {
                val chance = min(FATE_INSP_CAP, HORN_BASE + warden.input.fate * FATE_INSP_COEF + dawnInspBoost)
                if (rng.nextFloat() < chance) {
                    hornFired = true
                    hornWindow = HORN_DURATION
                    inspirationCounts["horn"] = 1
                }
            }

            // Captain — Wrath, Ruin, and the Red Dawn.
            if (captain != null && !captain.grievous && captain.hp > 0 && !dawnFired && crisisEdge) {
                val chance = min(FATE_INSP_CAP, DAWN_BASE + captain.input.fate * FATE_INSP_COEF)
                if (rng.nextFloat() < chance) {
                    dawnFired = true
                    dawnWindow = DAWN_DURATION
                    dawnInspBoost = DAWN_INSP_BOOST
                    active().forEach { m -> applyHeal(m, DAWN_HEAL_FRAC * m.maxHp * m.recoveryBuffMult) }
                    inspirationCounts["dawn"] = 1
                }
            }

            // Fighter — Black Arrow / Bullroarer's Five-Iron. Forecast-of-defeat trigger:
            // only eligible past the 40%-elapsed gate (or unlocked by Red Dawn), and only
            // while the party's current DPS pace would not kill the boss in time.
            if (fighter != null && !fighter.grievous && fighter.hp > 0 && !blackArrowFired) {
                val elapsedFrac = t.toFloat() / stage.durationSec
                if (elapsedFrac >= BLACK_ARROW_GATE || dawnUnlocked) {
                    val avgDps = if (t > 0) damageAcc.values.sum() / t else 0f
                    val timeRemaining = stage.durationSec - t
                    val timeToKill = if (avgDps > 0f) boss / avgDps else Float.MAX_VALUE
                    if (timeToKill > timeRemaining) {
                        val rise = min(BLACK_ARROW_CAP, max(0f, elapsedFrac - BLACK_ARROW_GATE) * BLACK_ARROW_CAP * 2.5f)
                        val chance = min(FATE_INSP_CAP, rise + fighter.input.fate * FATE_INSP_COEF + dawnInspBoost)
                        if (rng.nextFloat() < chance) {
                            blackArrowFired = true
                            inspirationCounts["blackArrow"] = 1
                            blackArrowFlashTicks = BLACK_ARROW_FLASH_TICKS
                            if (stage.objective == "kill") {
                                boss -= boss * BLACK_ARROW_RESOLVE_BURN
                            } else {
                                survivalClockBurnSec += (stage.durationSec * BLACK_ARROW_CLOCK_BURN).roundToInt()
                            }
                        }
                    }
                }
            }
            if (boss <= 0f) {
                snapshots.add(buildSnapshot(t, boss))
                return buildResult(Outcome.VICTORY, t, 0f)
            }

            // Keeper — Hands of Healing. Fires reactively when a member hits grievous;
            // Red Dawn unlocks proactive targeting of whoever's most wounded instead.
            if (keeper != null && !keeper.grievous && keeper.hp > 0 && graceUses < GRACE_MAX_USES) {
                val graceTarget = if (dawnUnlocked) {
                    standing().filter { it.input.id != keeper.input.id && it.wounds > 0 }.maxByOrNull { it.wounds }
                } else {
                    newlyGrievousThisTick.firstOrNull()?.let { id -> party.find { it.input.id == id } }
                }
                if (graceTarget != null) {
                    val chance = min(FATE_INSP_CAP, GRACE_BASE + keeper.input.fate * FATE_INSP_COEF + dawnInspBoost)
                    if (rng.nextFloat() < chance) {
                        graceTarget.wounds = 0
                        graceTarget.grievous = false
                        val healed = GRACE_REVIVE_FRAC * graceTarget.maxHp * graceTarget.recoveryBuffMult
                        graceTarget.hp = healed
                        graceUses++
                        inspirationCounts["grace"] = graceUses
                        graceFlashTicks = GRACE_FLASH_TICKS
                        healingAcc[keeper.input.id] = (healingAcc[keeper.input.id] ?: 0f) + healed
                    }
                }
            }

            // Non-"kill" objectives (survive/retrieve): Black Arrow's clock-burn effect
            // shortens the effective duration rather than dealing resolve damage.
            if (stage.objective != "kill" && survivalClockBurnSec > 0 && t >= stage.durationSec - survivalClockBurnSec) {
                return buildResult(if (boss <= 0f) Outcome.VICTORY else Outcome.STALEMATE, t, max(0f, boss / stage.resolve))
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
                            totalGroupHeal += applyHeal(m, groupHeal * m.recoveryBuffMult)
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
                            val delivered = applyHeal(triageTarget, keeper.input.will * TRIAGE_MUL * healMult * triageTarget.recoveryBuffMult)
                            healingAcc[keeper.input.id] = (healingAcc[keeper.input.id] ?: 0f) + delivered
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
                keeperHealingThisTick = actionTaken
                // Keeper DPS only when no consuming action taken. Keeper is pure magical
                // (physicalFraction == 0), so this is unconditionally unmitigated by armor —
                // written directly rather than routed through physicalFraction since the
                // role is fixed here.
                if (!actionTaken) {
                    val keeperDmg = rawDps(keeper.input) * healMult * dawnDpsMult * jMul
                    boss -= keeperDmg
                    damageAcc[keeper.input.id] = (damageAcc[keeper.input.id] ?: 0f) + keeperDmg
                    keeperDpsTicksCount++
                }
            }

            // Victory check after keeper DPS
            if (boss <= 0f) {
                snapshots.add(buildSnapshot(t, boss))
                return buildResult(Outcome.VICTORY, t, 0f)
            }

            // ── Tick snapshot for in-progress replay ─────────────────────────
            snapshots.add(buildSnapshot(t, boss))

            // ── Check defeat ──────────────────────────────────────────────────
            if (standing().isEmpty()) {
                return buildResult(Outcome.DEFEAT, t, boss / stage.resolve)
            }
        }

        // Duration expired
        val finalOutcome = if (boss <= 0f) Outcome.VICTORY else Outcome.STALEMATE
        return buildResult(finalOutcome, stage.durationSec, max(0f, boss / stage.resolve))
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

    private fun morale(m: MemberInput): Float = (30f + m.vitality * 32f).roundToInt().toFloat()

    // Captain damage-split constants — equal by design so the physical/magical split
    // (physicalFraction, below) is purely stat-driven with no fixed lean. Must match
    // tools/sim/run_sim.js exactly.
    private const val CAPTAIN_MIGHT_COEF = 2.0f
    private const val CAPTAIN_WILL_COEF  = 2.0f

    internal fun rawDps(m: MemberInput): Float = when (m.role) {
        "warden"  -> m.might * 1.5f
        "fighter" -> m.agility * 3f + m.might * 1.2f
        "keeper"  -> m.will * 2.7f
        "captain" -> m.might * CAPTAIN_MIGHT_COEF + m.will * CAPTAIN_WILL_COEF
        else      -> 0f
    }

    // Fraction of a member's raw damage that is physical (armor-mitigated) vs. magical
    // (bypasses armor). See master-design.md §6.9. Warden/Fighter are pure physical,
    // Keeper is pure magical, Captain splits proportionally between the might-driven and
    // will-driven terms of their own rawDps formula.
    internal fun physicalFraction(m: MemberInput): Float = when (m.role) {
        "warden", "fighter" -> 1f
        "keeper" -> 0f
        "captain" -> {
            val physTerm = m.might * CAPTAIN_MIGHT_COEF
            val magicTerm = m.will * CAPTAIN_WILL_COEF
            val total = physTerm + magicTerm
            if (total > 0f) physTerm / total else 1f
        }
        else -> 1f
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
