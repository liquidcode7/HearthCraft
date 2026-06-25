package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.BandMemberState
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
import com.liquidcode7.hearthcraft.engine.MemberInput
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

    suspend fun killMember(memberId: String) {
        val existing = dao.get(memberId) ?: BandMemberState(memberId = memberId)
        dao.upsert(existing.copy(isAlive = false))
    }

    suspend fun woundMember(memberId: String, grievous: Boolean) {
        val existing = dao.get(memberId) ?: BandMemberState(memberId = memberId)
        val status = if (grievous) "grievously_wounded" else "wounded"
        dao.upsert(existing.copy(woundStatus = status, woundedSinceMs = System.currentTimeMillis()))
    }

    suspend fun healWound(memberId: String) = dao.healWound(memberId)

    suspend fun aliveMemberIds(bandId: String): List<String> =
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .filter { dao.get(it.id)?.isAlive != false }
            .map { it.id }

    suspend fun woundableMemberIds(bandId: String): List<String> =
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .filter {
                val state = dao.get(it.id)
                state?.isAlive != false && state?.woundStatus != "grievously_wounded"
            }
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
        hpsList: List<Float>  // ordered: warden, hunter, keeper, captain
    ): List<MemberInput> {
        val roleOrder = listOf("warden", "hunter", "keeper", "captain")
        return gameData.bandMembers
            .filter { it.bandId == bandId }
            .sortedBy { roleOrder.indexOf(it.role.lowercase()) }
            .mapIndexed { i, member ->
                val state = dao.get(member.id)
                MemberInput(
                    id             = member.id,
                    role           = member.role.lowercase(),
                    might          = (state?.might    ?: member.startingMight).toFloat(),
                    agility        = (state?.agility  ?: member.startingAgility).toFloat(),
                    vitality       = (state?.vitality ?: member.startingVitality).toFloat(),
                    will           = (state?.will     ?: member.startingWill).toFloat(),
                    fate           = (state?.fate     ?: member.startingFate).toFloat(),
                    hps            = hpsList.getOrElse(i) { 5f },
                    draughtPotency = draughtPotency
                )
            }
    }
}
