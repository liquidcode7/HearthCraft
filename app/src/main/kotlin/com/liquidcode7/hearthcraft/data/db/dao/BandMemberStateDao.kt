package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.BandMemberState
import kotlinx.coroutines.flow.Flow

@Dao
interface BandMemberStateDao {

    @Query("SELECT * FROM band_member_state")
    fun observeAll(): Flow<List<BandMemberState>>

    @Query("SELECT * FROM band_member_state WHERE memberId = :id")
    suspend fun get(id: String): BandMemberState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: BandMemberState)

    @Query("UPDATE band_member_state SET isAlive = 0 WHERE memberId = :id")
    suspend fun kill(id: String)
}
