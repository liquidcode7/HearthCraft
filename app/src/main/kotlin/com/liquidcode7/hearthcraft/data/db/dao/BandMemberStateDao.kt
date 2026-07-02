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

    @Query("UPDATE band_member_state SET woundStatus = :status, woundedSinceMs = :sinceMs WHERE memberId = :id")
    suspend fun updateWound(id: String, status: String, sinceMs: Long)

    @Query("UPDATE band_member_state SET woundStatus = 'healthy', woundedSinceMs = 0, woundedDurationMs = 0 WHERE memberId = :id")
    suspend fun healWound(id: String)

    @Query("UPDATE band_member_state SET vitality = vitality + :vitality, might = might + :might WHERE memberId = :memberId AND isAlive = 1")
    suspend fun grantStats(memberId: String, vitality: Int, might: Int)

    @Query("UPDATE band_member_state SET woundTypes = :types WHERE memberId = :id")
    suspend fun setWoundTypes(id: String, types: String)

    @Query("UPDATE band_member_state SET hohTimerStartMs = :startMs, hohTimerDurationMs = :durationMs WHERE memberId = :id")
    suspend fun setHohTimer(id: String, startMs: Long, durationMs: Long)

    @Query("UPDATE band_member_state SET woundTypes = '', hohTimerStartMs = 0, hohTimerDurationMs = 0 WHERE memberId = :id")
    suspend fun clearHohState(id: String)

    @Query("UPDATE band_member_state SET recoveryBuffGrade = :grade, recoveryBuffTier = :tier, recoveryBuffPending = :pending WHERE memberId = :id")
    suspend fun setRecoveryBuff(id: String, grade: Int, tier: Int, pending: Boolean)

    @Query("UPDATE band_member_state SET recoveryBuffPending = 0 WHERE memberId = :id")
    suspend fun consumeRecoveryBuff(id: String)
}
