package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prepared_food")
data class PreparedFood(
    @PrimaryKey val recipeId: String,
    val quantity: Int = 0
)
