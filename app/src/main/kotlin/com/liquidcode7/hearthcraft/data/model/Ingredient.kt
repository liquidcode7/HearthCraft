package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(
    val id: String,
    val name: String,
    val region: String = "",
    val rarity: String = "c",
    val source: String = "forage",
    val gatheringMode: String = "forage",
    val primaryStat: String? = null,
    val secondaryStat: String? = null,
    val hazardTendency: String? = null,
    val notes: String = ""
)
