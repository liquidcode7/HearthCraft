package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_items")
    fun observeAll(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE ingredientId = :id AND grade = :grade")
    suspend fun get(id: String, grade: Int): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE ingredientId = :id ORDER BY grade ASC")
    suspend fun getAllGradesOf(id: String): List<InventoryItem>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM inventory_items WHERE ingredientId = :id")
    suspend fun totalQuantity(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventoryItem)

    @Query("UPDATE inventory_items SET quantity = quantity + :amount WHERE ingredientId = :id AND grade = :grade")
    suspend fun addQuantity(id: String, grade: Int, amount: Int)

    @Query("UPDATE inventory_items SET quantity = quantity - :amount WHERE ingredientId = :id AND grade = :grade")
    suspend fun removeQuantity(id: String, grade: Int, amount: Int)

    @Query("DELETE FROM inventory_items WHERE ingredientId = :id AND grade = :grade AND quantity <= 0")
    suspend fun deleteIfEmpty(id: String, grade: Int)
}
