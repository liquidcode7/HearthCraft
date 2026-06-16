package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_state")
data class PlayerState(
    @PrimaryKey val id: Int = 0,
    val chosenBandId: String = "",
    val secondBandId: String = "",
    val gatheringLevel: Int = 1,
    val gatheringXp: Int = 0,
    val cookingLevel: Int = 1,
    val cookingXp: Int = 0,
    val money: Int = 0
)
