package com.liquidcode7.hearthcraft.data.db

import androidx.room.ColumnInfo
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
    val money: Int = 0,
    @ColumnInfo(defaultValue = "")
    val discoveredRecipeIds: String = "",
    @ColumnInfo(defaultValue = "0")
    val hasSeenFoodStructureHints: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val discoveredIngredientIds: String = "",
    @ColumnInfo(defaultValue = "0")
    val hasSeenExperimentHint: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val hasSeenPostForageNudge: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val hasReceivedStarterPantry: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val foundGrimoireIds: String = "",
)
