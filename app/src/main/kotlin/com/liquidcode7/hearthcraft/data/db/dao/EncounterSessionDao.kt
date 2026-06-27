package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.EncounterSession
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: EncounterSession)

    @Query("SELECT * FROM encounter_session WHERE bandId = :bandId LIMIT 1")
    fun observe(bandId: String): Flow<EncounterSession?>

    @Query("SELECT * FROM encounter_session WHERE bandId = :bandId LIMIT 1")
    suspend fun get(bandId: String): EncounterSession?

    @Query("DELETE FROM encounter_session WHERE bandId = :bandId")
    suspend fun clear(bandId: String)
}
