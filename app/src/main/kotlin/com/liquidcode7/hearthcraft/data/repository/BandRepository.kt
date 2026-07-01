package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.BandMemberState
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
import com.liquidcode7.hearthcraft.data.model.gradeStep
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.engine.MemberInput
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedFoodDetail
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BandRepository @Inject constructor(
    private val dao: BandMemberStateDao,
    private val gameData: GameDataRepository
) {
    fun observeMemberStates(): Flow<List<BandMemberState>> = dao.observeAll()

    suspend fun initMembers(bandId: String) {
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .forEach { member ->
                dao.upsert(BandMemberState(
                    memberId = member.id,
                    might = member.startingMight,
                    agility = member.startingAgility,
                    vitality = member.startingVitality,
                    will = member.startingWill,
                    fate = member.startingFate
                ))
            }
    }

    suspend fun woundMember(memberId: String, grievous: Boolean, durationMs: Long) {
        val existing = dao.get(memberId) ?: BandMemberState(memberId = memberId)
        val status = if (grievous) "grievously_wounded" else "wounded"
        dao.upsert(existing.copy(
            woundStatus = status,
            woundedSinceMs = System.currentTimeMillis(),
            woundedDurationMs = durationMs
        ))
    }

    suspend fun healWound(memberId: String) = dao.healWound(memberId)

    suspend fun aliveMemberIds(bandId: String): List<String> =
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .filter { dao.get(it.id)?.isAlive != false }
            .map { it.id }

    suspend fun grantMissionStats(bandId: String, succeeded: Boolean) {
        aliveMemberIds(bandId).forEach { id ->
            dao.grantStats(
                memberId = id,
                vitality = 1,
                might = if (succeeded) 1 else 0
            )
        }
    }

    suspend fun maxVitality(bandId: String): Int =
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .mapNotNull { dao.get(it.id) }
            .filter { it.isAlive }
            .maxOfOrNull { it.vitality } ?: 0

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
                    might          = (state?.might    ?: member.startingMight).toFloat()    + bonus("mig"),
                    agility        = (state?.agility  ?: member.startingAgility).toFloat()  + bonus("agi"),
                    vitality       = (state?.vitality ?: member.startingVitality).toFloat() + bonus("vit"),
                    will           = (state?.will     ?: member.startingWill).toFloat()     + bonus("wil"),
                    fate           = (state?.fate     ?: member.startingFate).toFloat(),
                    draughtPotency = draughtPotency
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
