package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.db.PreparedFood
import com.liquidcode7.hearthcraft.data.db.SeedStock
import com.liquidcode7.hearthcraft.data.db.dao.InventoryDao
import com.liquidcode7.hearthcraft.data.db.dao.PreparedFoodDao
import com.liquidcode7.hearthcraft.data.db.dao.SeedStockDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val preparedFoodDao: PreparedFoodDao,
    private val seedDao: SeedStockDao
) {
    fun observeIngredients(): Flow<List<InventoryItem>> = inventoryDao.observeAll()
    fun observePreparedFood(): Flow<List<PreparedFood>> = preparedFoodDao.observeAll()

    // ── Ingredient: grade-explicit (used by workers in Phase 3+) ─────────────

    suspend fun addIngredient(id: String, grade: Int, qty: Int) {
        val existing = inventoryDao.get(id, grade)
        if (existing == null) {
            inventoryDao.upsert(InventoryItem(ingredientId = id, grade = grade, quantity = qty))
        } else {
            inventoryDao.addQuantity(id, grade, qty)
        }
    }

    /**
     * Remove [qty] of [id] at the explicit [grade].
     * Used by the cook path, which always knows exactly which grade it's consuming.
     */
    suspend fun removeIngredient(id: String, grade: Int, qty: Int) {
        inventoryDao.removeQuantity(id, grade, qty)
        inventoryDao.deleteIfEmpty(id, grade)
    }

    // ── Ingredient: grade-agnostic (existing callers — consumes lowest grade first) ──

    /** Adds at grade=0 (Crude). Used by callers that don't yet know about grade (pre-Phase 3). */
    suspend fun addIngredient(id: String, qty: Int) = addIngredient(id, grade = 0, qty = qty)

    /**
     * Removes [qty] of [id], consuming the lowest grade first.
     * This protects the player's high-grade stock from being silently burned.
     */
    suspend fun removeIngredient(id: String, qty: Int) {
        var remaining = qty
        for (row in inventoryDao.getAllGradesOf(id)) {
            if (remaining <= 0) break
            val consume = minOf(remaining, row.quantity)
            inventoryDao.removeQuantity(row.ingredientId, row.grade, consume)
            inventoryDao.deleteIfEmpty(row.ingredientId, row.grade)
            remaining -= consume
        }
    }

    /** Total quantity of [id] across all grades. */
    suspend fun ingredientQty(id: String): Int = inventoryDao.totalQuantity(id)

    /** Quantity at a specific grade. */
    suspend fun ingredientQtyAtGrade(id: String, grade: Int): Int =
        inventoryDao.get(id, grade)?.quantity ?: 0

    // ── Prepared food ─────────────────────────────────────────────────────────

    suspend fun addPreparedFood(recipeId: String, grade: Int = 0) {
        val existing = preparedFoodDao.get(recipeId, grade)
        if (existing == null) {
            preparedFoodDao.upsert(PreparedFood(recipeId = recipeId, grade = grade, quantity = 1))
        } else {
            preparedFoodDao.addOne(recipeId, grade)
        }
    }

    /** Removes one serving, consuming lowest grade first. */
    suspend fun removePreparedFood(recipeId: String) {
        val rows = preparedFoodDao.getAllGradesOf(recipeId)
        val target = rows.firstOrNull { it.quantity > 0 } ?: return
        preparedFoodDao.removeOne(target.recipeId, target.grade)
        preparedFoodDao.deleteIfEmpty(target.recipeId, target.grade)
    }

    /** Total quantity of [recipeId] across all grades. */
    suspend fun preparedFoodQty(recipeId: String): Int = preparedFoodDao.totalQuantity(recipeId)

    // ── Seeds (unchanged) ─────────────────────────────────────────────────────

    fun observeSeeds(): Flow<List<SeedStock>> = seedDao.observeAll()

    suspend fun addSeed(seedId: String, qty: Int) {
        if (seedDao.get(seedId) == null) {
            seedDao.upsert(SeedStock(seedId = seedId, quantity = qty))
        } else {
            seedDao.addQuantity(seedId, qty)
        }
    }

    suspend fun removeSeed(seedId: String, qty: Int) = seedDao.removeQuantity(seedId, qty)
    suspend fun seedQty(seedId: String): Int = seedDao.get(seedId)?.quantity ?: 0
}
