package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.GatheringSession
import kotlinx.coroutines.flow.Flow

@Dao
interface GatheringSessionDao {

    @Query("SELECT * FROM gathering_session WHERE id = 0")
    fun observe(): Flow<GatheringSession?>

    @Query("SELECT * FROM gathering_session WHERE id = 0")
    suspend fun get(): GatheringSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun start(session: GatheringSession)

    @Query("DELETE FROM gathering_session WHERE id = 0")
    suspend fun clear()
}
