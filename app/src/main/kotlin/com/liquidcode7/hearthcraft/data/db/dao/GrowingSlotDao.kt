package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import kotlinx.coroutines.flow.Flow

@Dao
interface GrowingSlotDao {

    @Query("SELECT * FROM growing_slots WHERE id = :id")
    fun observe(id: String): Flow<GrowingSlot?>

    @Query("SELECT * FROM growing_slots WHERE type = :type")
    fun observeByType(type: String): Flow<List<GrowingSlot>>

    @Query("SELECT * FROM growing_slots WHERE id = :id")
    suspend fun get(id: String): GrowingSlot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: GrowingSlot)

    @Query("DELETE FROM growing_slots WHERE id = :id")
    suspend fun delete(id: String)
}
