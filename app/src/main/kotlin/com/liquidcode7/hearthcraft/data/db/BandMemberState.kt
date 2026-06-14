package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "band_member_state")
data class BandMemberState(
    @PrimaryKey val memberId: String,
    val isAlive: Boolean = true,
    // "healthy", "wounded", "grievously_wounded"
    val woundStatus: String = "healthy",
    val woundedSinceMs: Long = 0L
)
