package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.BandMemberState
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
import com.liquidcode7.hearthcraft.data.db.dao.HohSessionDao
import com.liquidcode7.hearthcraft.data.model.gradeStep
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.GrowthCurve
import com.liquidcode7.hearthcraft.data.model.growthCurveKeyForRole
import com.liquidcode7.hearthcraft.data.model.levelForCombatXp
import com.liquidcode7.hearthcraft.data.model.statAtLevel
import com.liquidcode7.hearthcraft.data.model.BandMember
import com.liquidcode7.hearthcraft.engine.EncounterEngine
import com.liquidcode7.hearthcraft.engine.MemberInput
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedFoodDetail
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BandRepository @Inject constructor(
    private val dao: BandMemberStateDao,
    private val hohSessionDao: HohSessionDao,
    private val gameData: GameDataRepository,
    private val player: PlayerRepository
) {
    fun observeMemberStates(): Flow<List<BandMemberState>> = dao.observeAll()

    suspend fun initMembers(bandId: String) {
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .forEach { member ->
                dao.upsert(BandMemberState(memberId = member.id))
            }
    }

    suspend fun woundMember(
        memberId: String,
        grievous: Boolean,
        durationMs: Long,
        woundTypes: List<String> = emptyList()
    ) {
        val existing = dao.get(memberId) ?: BandMemberState(memberId = memberId)
        val status = if (grievous) "grievously_wounded" else "wounded"
        dao.upsert(existing.copy(
            woundStatus = status,
            woundedSinceMs = System.currentTimeMillis(),
            woundedDurationMs = durationMs
        ))
        if (grievous && woundTypes.isNotEmpty()) {
            dao.setWoundTypes(memberId, woundTypes.joinToString(","))
        }
    }

    suspend fun healWound(memberId: String) = dao.healWound(memberId)

    suspend fun completeHohRecovery(memberId: String) {
        dao.healWound(memberId)
        dao.clearHohState(memberId)
        hohSessionDao.delete(memberId)
    }

    // Clears the pending recovery buff for all members of a band after an encounter.
    // The buff is one-shot — it fires on the first mission after HoH recovery and is
    // then consumed regardless of encounter outcome.
    suspend fun consumeRecoveryBuffs(bandId: String) {
        aliveMemberIds(bandId).forEach { id ->
            val state = dao.get(id) ?: return@forEach
            if (state.recoveryBuffPending) dao.consumeRecoveryBuff(id)
        }
    }

    suspend fun aliveMemberIds(bandId: String): List<String> =
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .filter { dao.get(it.id)?.isAlive != false }
            .map { it.id }

    suspend fun grantCombatXp(bandId: String, xp: Int) {
        aliveMemberIds(bandId).forEach { id ->
            dao.addCombatXp(memberId = id, xp = xp)
        }
    }

    private suspend fun growthCurveFor(member: BandMember): GrowthCurve? {
        val fighterBuild = player.get()?.fighterBuild ?: "ranged"
        val key = growthCurveKeyForRole(member.role, fighterBuild)
        return gameData.growthCurves.find { it.role == key }
    }

    private suspend fun currentStats(member: BandMember, state: BandMemberState?): FloatArray {
        val curve = growthCurveFor(member)
        val level = levelForCombatXp(state?.combatXp ?: 0)
        return floatArrayOf(
            curve?.let { statAtLevel(member.startingMight,    it.migGrowth, level) } ?: member.startingMight.toFloat(),
            curve?.let { statAtLevel(member.startingAgility,  it.agiGrowth, level) } ?: member.startingAgility.toFloat(),
            curve?.let { statAtLevel(member.startingVitality, it.vitGrowth, level) } ?: member.startingVitality.toFloat(),
            curve?.let { statAtLevel(member.startingWill,     it.wilGrowth, level) } ?: member.startingWill.toFloat(),
            curve?.let { statAtLevel(member.startingFate,     it.fatGrowth, level) } ?: member.startingFate.toFloat()
        )
    }

    suspend fun maxVitality(bandId: String): Int =
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .mapNotNull { member ->
                val state = dao.get(member.id)
                if (state?.isAlive == false) null else currentStats(member, state)[2].toInt()
            }
            .maxOrNull() ?: 0

    suspend fun memberInputsForBand(
        bandId: String,
        draughtPotency: Float,
        memberFood: Map<String, PreparedFoodDetail?>   // memberId → food detail (null = no food)
    ): List<MemberInput> {
        val roleOrder = listOf("warden", "fighter", "keeper", "captain")
        return gameData.bandMembers
            .filter { it.bandId == bandId }
            .sortedBy { roleOrder.indexOf(it.role.lowercase()) }
            .map { member ->
                val state  = dao.get(member.id)
                val stats  = currentStats(member, state)
                val food   = memberFood[member.id]
                val bonus  = { stat: String ->
                    if (food != null) {
                        val base = statBonusFor(stat, food.primaryStat, food.primaryBoost,
                                                food.secondaryStat, food.secondaryBoost)
                        // Grade adds a flat step on top of the authored boost, on matching stats only.
                        val step = if (base > 0f) gradeStep(Grade.fromOrdinal(food.grade)) else 0f
                        base + step
                    } else 0f
                }
                MemberInput(
                    id             = member.id,
                    role           = member.role.lowercase(),
                    might          = stats[0] + bonus("mig"),
                    agility        = stats[1] + bonus("agi"),
                    vitality       = stats[2] + bonus("vit"),
                    will           = stats[3] + bonus("wil"),
                    fate           = stats[4],
                    draughtPotency = draughtPotency,
                    recoveryBuffMult = if (state?.recoveryBuffPending == true)
                        EncounterEngine.recoveryBuffMultiplier(state.recoveryBuffGrade, state.recoveryBuffTier)
                    else 1.0f
                )
            }
    }

    companion object {
        fun statBonusFor(
            stat: String,
            primaryStat: String?,
            primaryBoost: Int,
            secondaryStat: String?,
            secondaryBoost: Int
        ): Float = when (stat) {
            primaryStat   -> primaryBoost.toFloat()
            secondaryStat -> secondaryBoost.toFloat()
            else          -> 0f
        }
    }
}
