package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// id is "farm_0", "garden_0".."garden_3" — type derived from id prefix
@Entity(tableName = "growing_slots")
data class GrowingSlot(
    @PrimaryKey val id: String,
    val type: String,
    val ingredientId: String? = null,
    val plantedAtMs: Long = 0L,
    val durationMs: Long = 0L,
    val workRequestId: String? = null
)
