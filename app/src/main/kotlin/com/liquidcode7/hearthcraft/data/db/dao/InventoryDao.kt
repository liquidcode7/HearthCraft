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

    @Query("SELECT * FROM inventory_items WHERE ingredientId = :id")
    suspend fun get(id: String): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventoryItem)

    @Query("UPDATE inventory_items SET quantity = quantity + :amount WHERE ingredientId = :id")
    suspend fun addQuantity(id: String, amount: Int)

    @Query("UPDATE inventory_items SET quantity = quantity - :amount WHERE ingredientId = :id")
    suspend fun removeQuantity(id: String, amount: Int)
}
