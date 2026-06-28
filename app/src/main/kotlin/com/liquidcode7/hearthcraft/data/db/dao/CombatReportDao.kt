package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.liquidcode7.hearthcraft.data.db.CombatReport
import kotlinx.coroutines.flow.Flow

@Dao
interface CombatReportDao {
    @Upsert
    suspend fun upsert(report: CombatReport)

    @Query("SELECT * FROM combat_reports WHERE bandId = :bandId LIMIT 1")
    suspend fun get(bandId: String): CombatReport?

    @Query("SELECT * FROM combat_reports WHERE bandId = :bandId LIMIT 1")
    fun observe(bandId: String): Flow<CombatReport?>

    @Query("DELETE FROM combat_reports WHERE bandId = :bandId")
    suspend fun clear(bandId: String)
}
