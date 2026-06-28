package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class HarvestItem(
    val ingredientId: String,
    val name: String,
    val quantity: Int,
    val rarity: String,  // "common", "uncommon", "bonus"
    @Transient val isNew: Boolean = false
)
