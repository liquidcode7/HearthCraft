package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.db.dao.PlayerStateDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepository @Inject constructor(
    private val dao: PlayerStateDao
) {
    fun observe(): Flow<PlayerState?> = dao.observe()

    suspend fun get(): PlayerState? = dao.get()

    suspend fun init(bandId: String) {
        dao.upsert(PlayerState(chosenBandId = bandId))
    }

    suspend fun addMoney(amount: Int) = dao.addMoney(amount)
    suspend fun addGatheringXp(xp: Int) = dao.addGatheringXp(xp)
    suspend fun addCookingXp(xp: Int) = dao.addCookingXp(xp)

    suspend fun setGatheringLevel(level: Int) {
        val current = dao.get() ?: return
        dao.upsert(current.copy(gatheringLevel = level))
    }

    suspend fun setCookingLevel(level: Int) {
        val current = dao.get() ?: return
        dao.upsert(current.copy(cookingLevel = level))
    }
}
