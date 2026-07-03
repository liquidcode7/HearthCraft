package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// Stores the per-tick snapshots emitted by EncounterEngine for the in-progress replay UI.
// One row per active band; written when sendOnEncounter() fires and cleared when the
// combat report is dismissed (or when a new encounter starts for this band).
@Entity(tableName = "encounter_ticks")
data class EncounterTicks(
    @PrimaryKey val bandId: String,
    val ticksJson: String,       // serialized List<TickSnapshot>
    val totalTicks: Int,
    val bossMaxResolve: Float,
    val memberMaxHpJson: String  // "memberId:maxHp,..." — static per fight
)
