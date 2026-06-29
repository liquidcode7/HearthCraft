package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.CookingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface CookingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun start(session: CookingSession)

    @Query("SELECT * FROM cooking_session WHERE id = :slot LIMIT 1")
    fun observeSlot(slot: Int): Flow<CookingSession?>

    @Query("SELECT * FROM cooking_session WHERE id = :slot LIMIT 1")
    suspend fun getSlot(slot: Int): CookingSession?

    @Query("DELETE FROM cooking_session WHERE id = :slot")
    suspend fun clearSlot(slot: Int)
}
