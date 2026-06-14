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

    suspend fun addIngredient(id: String, qty: Int) {
        if (inventoryDao.get(id) == null) {
            inventoryDao.upsert(InventoryItem(ingredientId = id, quantity = qty))
        } else {
            inventoryDao.addQuantity(id, qty)
        }
    }

    suspend fun removeIngredient(id: String, qty: Int) = inventoryDao.removeQuantity(id, qty)

    suspend fun ingredientQty(id: String): Int = inventoryDao.get(id)?.quantity ?: 0

    suspend fun addPreparedFood(recipeId: String) {
        if (preparedFoodDao.get(recipeId) == null) {
            preparedFoodDao.upsert(PreparedFood(recipeId = recipeId, quantity = 1))
        } else {
            preparedFoodDao.addOne(recipeId)
        }
    }

    suspend fun removePreparedFood(recipeId: String) = preparedFoodDao.removeOne(recipeId)

    suspend fun preparedFoodQty(recipeId: String): Int = preparedFoodDao.get(recipeId)?.quantity ?: 0

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
