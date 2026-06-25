package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encounter_session")
data class EncounterSession(
    @PrimaryKey val bandId: String,
    val encounterId: String,
    val draughtPotency: Float = 0f,
    val startedAtMs: Long,
    val durationMs: Long,
    val workRequestId: String
)
