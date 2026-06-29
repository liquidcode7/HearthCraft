package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.db.dao.GrowingSlotDao
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
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
    suspend fun updatePlantedAt(id: String, ms: Long) = dao.updatePlantedAt(id, ms)

    // Returns true if items were added, false if at cap.
    // Cap check and write use the same DB read — no TOCTOU with player collect.
    suspend fun addToPendingResult(
        id: String,
        newItems: List<HarvestItem>,
        maxQty: Int = Int.MAX_VALUE
    ): Boolean {
        val existing = dao.get(id)?.pendingResultJson
        val current = if (existing != null) {
            runCatching { Json.decodeFromString<List<HarvestItem>>(existing) }.getOrNull() ?: emptyList()
        } else emptyList()
        if (current.sumOf { it.quantity } >= maxQty) return false
        val merged = (current + newItems)
            .groupBy { it.ingredientId }
            .map { (_, group) -> group.first().copy(quantity = group.sumOf { it.quantity }) }
        dao.setPendingResult(id, Json.encodeToString(merged))
        return true
    }

    suspend fun collectAndClearSlot(id: String): List<HarvestItem> {
        val slot = dao.get(id) ?: return emptyList()
        val json = slot.pendingResultJson ?: return emptyList()
        val items = runCatching { Json.decodeFromString<List<HarvestItem>>(json) }.getOrNull() ?: emptyList()
        dao.delete(id)
        return items
    }

    // For producer slots (hive/coop/dairy): keeps the slot row alive so workers always
    // have a valid row to write into on their next cycle.
    suspend fun collectAndClearPendingOnly(id: String): List<HarvestItem> {
        val slot = dao.get(id) ?: return emptyList()
        val json = slot.pendingResultJson ?: return emptyList()
        val items = runCatching { Json.decodeFromString<List<HarvestItem>>(json) }.getOrNull() ?: emptyList()
        dao.clearPendingResult(id)
        return items
    }
}
