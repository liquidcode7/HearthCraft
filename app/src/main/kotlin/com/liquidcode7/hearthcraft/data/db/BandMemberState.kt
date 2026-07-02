package com.liquidcode7.hearthcraft.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "band_member_state")
data class BandMemberState(
    @PrimaryKey val memberId: String,
    val isAlive: Boolean = true,
    val woundStatus: String = "healthy",
    val woundedSinceMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val woundedDurationMs: Long = 0L,
    val might: Int = 0,
    val agility: Int = 0,
    val vitality: Int = 0,
    val will: Int = 0,
    val fate: Int = 0,
    @ColumnInfo(defaultValue = "")
    val woundTypes: String = "",
    @ColumnInfo(defaultValue = "0")
    val hohTimerStartMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val hohTimerDurationMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val recoveryBuffGrade: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val recoveryBuffTier: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val recoveryBuffPending: Boolean = false
)
