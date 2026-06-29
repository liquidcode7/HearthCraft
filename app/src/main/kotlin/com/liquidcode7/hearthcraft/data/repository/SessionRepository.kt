package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.EncounterSession
import com.liquidcode7.hearthcraft.data.db.GatheringSession
import com.liquidcode7.hearthcraft.data.db.MissionSession
import com.liquidcode7.hearthcraft.data.db.dao.CookingSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.EncounterSessionDao
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
    private val missionDao: MissionSessionDao,
    private val encounterDao: EncounterSessionDao
) {
    fun observeGathering(): Flow<GatheringSession?> = gatheringDao.observe()
    fun observeMission(bandId: String): Flow<MissionSession?> = missionDao.observe(bandId)
    fun observeEncounter(bandId: String): Flow<EncounterSession?> = encounterDao.observe(bandId)

    suspend fun activeGathering(): GatheringSession? = gatheringDao.get()
    suspend fun activeMission(bandId: String): MissionSession? = missionDao.get(bandId)
    suspend fun activeEncounter(bandId: String): EncounterSession? = encounterDao.get(bandId)

    suspend fun startGathering(session: GatheringSession) = gatheringDao.start(session)
    suspend fun clearGathering() = gatheringDao.clear()

    fun observeCookingSlot(slot: Int): Flow<CookingSession?> = cookingDao.observeSlot(slot)
    suspend fun activeCookingSlot(slot: Int): CookingSession? = cookingDao.getSlot(slot)
    suspend fun startCookingInSlot(session: CookingSession) = cookingDao.start(session)
    suspend fun clearCookingSlot(slot: Int) = cookingDao.clearSlot(slot)

    suspend fun startMission(session: MissionSession) = missionDao.start(session)
    suspend fun clearMission(bandId: String) = missionDao.clear(bandId)

    suspend fun startEncounter(session: EncounterSession) = encounterDao.upsert(session)
    suspend fun clearEncounter(bandId: String) = encounterDao.clear(bandId)

    suspend fun setPendingForageResult(json: String) = gatheringDao.setPendingResult(json)

    suspend fun collectForage(): List<HarvestItem> {
        val session = gatheringDao.get() ?: return emptyList()
        val json = session.pendingResultJson ?: return emptyList()
        val items = Json.decodeFromString<List<HarvestItem>>(json)
        gatheringDao.clear()
        return items
    }
}
