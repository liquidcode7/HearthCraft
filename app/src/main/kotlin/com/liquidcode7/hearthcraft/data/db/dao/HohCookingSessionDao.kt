package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.HohCookingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface HohCookingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun start(session: HohCookingSession)

    @Query("SELECT * FROM hoh_cooking_session WHERE id = 0 LIMIT 1")
    fun observe(): Flow<HohCookingSession?>

    @Query("DELETE FROM hoh_cooking_session WHERE id = 0")
    suspend fun clear()
}
