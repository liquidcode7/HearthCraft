package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.EncounterTicks
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterTicksDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ticks: EncounterTicks)

    @Query("SELECT * FROM encounter_ticks WHERE bandId = :bandId LIMIT 1")
    fun observe(bandId: String): Flow<EncounterTicks?>

    @Query("SELECT * FROM encounter_ticks WHERE bandId = :bandId LIMIT 1")
    suspend fun get(bandId: String): EncounterTicks?

    @Query("DELETE FROM encounter_ticks WHERE bandId = :bandId")
    suspend fun delete(bandId: String)
}
