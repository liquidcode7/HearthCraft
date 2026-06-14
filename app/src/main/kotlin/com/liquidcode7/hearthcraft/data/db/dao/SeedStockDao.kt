package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.SeedStock
import kotlinx.coroutines.flow.Flow

@Dao
interface SeedStockDao {

    @Query("SELECT * FROM seed_stock")
    fun observeAll(): Flow<List<SeedStock>>

    @Query("SELECT * FROM seed_stock WHERE seedId = :seedId")
    suspend fun get(seedId: String): SeedStock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(seed: SeedStock)

    @Query("UPDATE seed_stock SET quantity = quantity + :amount WHERE seedId = :seedId")
    suspend fun addQuantity(seedId: String, amount: Int)

    @Query("UPDATE seed_stock SET quantity = MAX(0, quantity - :amount) WHERE seedId = :seedId")
    suspend fun removeQuantity(seedId: String, amount: Int)
}
