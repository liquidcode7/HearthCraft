package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(
    val id: String,
    val name: String,
    val type: String,
    val gatheringMode: String,
    val flavor: String,
    val rarity: String = "common"
)
