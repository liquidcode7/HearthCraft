package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gathering_session")
data class GatheringSession(
    @PrimaryKey val id: Int = 0,
    val mode: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val workRequestId: String,
    val pendingResultJson: String? = null
)
