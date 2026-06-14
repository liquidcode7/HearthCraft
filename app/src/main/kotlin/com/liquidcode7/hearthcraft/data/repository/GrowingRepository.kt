package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.db.dao.GrowingSlotDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrowingRepository @Inject constructor(private val dao: GrowingSlotDao) {

    fun observeFarmPlot(): Flow<GrowingSlot?> = dao.observe("farm_0")
    fun observeGardenSlots(): Flow<List<GrowingSlot>> = dao.observeByType("garden")

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
}
