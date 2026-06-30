package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity

@Entity(tableName = "inventory_items", primaryKeys = ["ingredientId", "grade"])
data class InventoryItem(
    val ingredientId: String,
    val grade: Int = 0,        // ordinal of Grade enum: 0=Crude … 4=Pristine
    val quantity: Int = 0
)
