package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.db.dao.GrowingSlotDao
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrowingRepository @Inject constructor(private val dao: GrowingSlotDao) {

    fun observeFarmPlot(): Flow<GrowingSlot?> = dao.observe("farm_0")
    fun observeGardenSlots(): Flow<List<GrowingSlot>> = dao.observeByType("garden")
    fun observeSlot(id: String): Flow<GrowingSlot?> = dao.observe(id)

    suspend fun plantSlot(
        id: String,
        type: String,
        ingredientId: String,
        plantedAtMs: Long,
        durationMs: Long,
        workRequestId: String
    ) {
        dao.upsert(GrowingSlot(id, type, ingredientId, plantedAtMs, durationMs, workRequestId))
    }

    suspend fun clearSlot(id: String) = dao.delete(id)
    suspend fun getSlot(id: String): GrowingSlot? = dao.get(id)

    suspend fun setPendingResult(id: String, json: String) = dao.setPendingResult(id, json)

    suspend fun collectAndClearSlot(id: String): List<HarvestItem> {
        val slot = dao.get(id) ?: return emptyList()
        val json = slot.pendingResultJson ?: return emptyList()
        val items = Json.decodeFromString<List<HarvestItem>>(json)
        dao.delete(id)
        return items
    }
}
