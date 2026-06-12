package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.MissionSession
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionSessionDao {

    @Query("SELECT * FROM mission_session WHERE id = 0")
    fun observe(): Flow<MissionSession?>

    @Query("SELECT * FROM mission_session WHERE id = 0")
    suspend fun get(): MissionSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun start(session: MissionSession)

    @Query("DELETE FROM mission_session WHERE id = 0")
    suspend fun clear()
}
