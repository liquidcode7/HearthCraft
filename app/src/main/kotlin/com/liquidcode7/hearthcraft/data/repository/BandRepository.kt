package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.BandMemberState
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
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
            .forEach { dao.upsert(BandMemberState(memberId = it.id)) }
    }

    suspend fun killMember(memberId: String) {
        val existing = dao.get(memberId) ?: BandMemberState(memberId = memberId)
        dao.upsert(existing.copy(isAlive = false))
    }

    suspend fun aliveMemberIds(bandId: String): List<String> =
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .filter { dao.get(it.id)?.isAlive != false }
            .map { it.id }
}
