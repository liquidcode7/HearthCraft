package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey val ingredientId: String,
    val quantity: Int = 0
)
