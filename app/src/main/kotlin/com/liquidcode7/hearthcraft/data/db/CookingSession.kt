package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cooking_session")
data class CookingSession(
    @PrimaryKey val id: Int = 0,
    val recipeId: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val workRequestId: String
)
