package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.*
import com.liquidcode7.hearthcraft.data.db.HohSession
import kotlinx.coroutines.flow.Flow

@Dao
interface HohSessionDao {
    @Query("SELECT * FROM hoh_sessions")
    fun observeAll(): Flow<List<HohSession>>

    @Query("SELECT * FROM hoh_sessions WHERE memberId = :id")
    suspend fun get(id: String): HohSession?

    @Upsert
    suspend fun upsert(session: HohSession)

    @Query("DELETE FROM hoh_sessions WHERE memberId = :id")
    suspend fun delete(id: String)
}
