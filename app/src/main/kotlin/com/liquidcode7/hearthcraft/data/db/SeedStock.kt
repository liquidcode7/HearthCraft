package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seed_stock")
data class SeedStock(
    @PrimaryKey val seedId: String,
    val quantity: Int = 0
)
