package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class HarvestItem(
    val ingredientId: String,
    val name: String,
    val quantity: Int,
    val rarity: String,  // "common", "uncommon", "bonus"
    val grade: Int = 0,  // ordinal of Grade enum; 0=Crude default for seeds/bonus items
    @Transient val isNew: Boolean = false
)
