package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mission_session")
data class MissionSession(
    @PrimaryKey val id: Int = 0,
    val missionId: String,
    val buffStrength: Int,
    val startedAtMs: Long,
    val durationMs: Long,
    val workRequestId: String
)
