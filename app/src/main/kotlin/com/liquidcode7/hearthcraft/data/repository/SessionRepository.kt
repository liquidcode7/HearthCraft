package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.GatheringSession
import com.liquidcode7.hearthcraft.data.db.MissionSession
import com.liquidcode7.hearthcraft.data.db.dao.CookingSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.GatheringSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.MissionSessionDao
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val gatheringDao: GatheringSessionDao,
    private val cookingDao: CookingSessionDao,
    private val missionDao: MissionSessionDao
) {
    fun observeGathering(): Flow<GatheringSession?> = gatheringDao.observe()
    fun observeCooking(): Flow<CookingSession?> = cookingDao.observe()
    fun observeMission(): Flow<MissionSession?> = missionDao.observe()

    suspend fun activeGathering(): GatheringSession? = gatheringDao.get()
    suspend fun activeCooking(): CookingSession? = cookingDao.get()
    suspend fun activeMission(): MissionSession? = missionDao.get()

    suspend fun startGathering(session: GatheringSession) = gatheringDao.start(session)
    suspend fun clearGathering() = gatheringDao.clear()

    suspend fun startCooking(session: CookingSession) = cookingDao.start(session)
    suspend fun clearCooking() = cookingDao.clear()

    suspend fun startMission(session: MissionSession) = missionDao.start(session)
    suspend fun clearMission() = missionDao.clear()

    suspend fun setPendingForageResult(json: String) = gatheringDao.setPendingResult(json)

    suspend fun collectForage(): List<HarvestItem> {
        val session = gatheringDao.get() ?: return emptyList()
        val json = session.pendingResultJson ?: return emptyList()
        val items = Json.decodeFromString<List<HarvestItem>>(json)
        gatheringDao.clear()
        return items
    }
}
