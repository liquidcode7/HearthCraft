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

    suspend fun addGatheringXp(xp: Int) {
        dao.addGatheringXp(xp)
        val state = dao.get() ?: return
        val newLevel = levelForXp(state.gatheringXp)
        if (newLevel != state.gatheringLevel) dao.upsert(state.copy(gatheringLevel = newLevel))
    }

    suspend fun addCookingXp(xp: Int) {
        dao.addCookingXp(xp)
        val state = dao.get() ?: return
        val newLevel = levelForXp(state.cookingXp)
        if (newLevel != state.cookingLevel) dao.upsert(state.copy(cookingLevel = newLevel))
    }

    suspend fun spendMoney(amount: Int): Boolean {
        val rowsAffected = dao.spendMoney(amount)
        return rowsAffected > 0
    }

    companion object {
        // XP required per level increases by 100 each level: 100 to reach 2, 200 to reach 3, etc.
        fun levelForXp(xp: Int): Int {
            var level = 1
            var threshold = 0
            while (xp >= threshold + level * 100) {
                threshold += level * 100
                level++
            }
            return level
        }
    }
}
