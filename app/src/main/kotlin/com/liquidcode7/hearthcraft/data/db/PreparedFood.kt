package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity

@Entity(tableName = "prepared_food", primaryKeys = ["recipeId", "grade"])
data class PreparedFood(
    val recipeId: String,
    val grade: Int = 0,        // ordinal of Grade enum: 0=Crude … 4=Pristine
    val quantity: Int = 0
)
