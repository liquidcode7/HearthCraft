package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Band(
    val id: String,
    val name: String,
    val region: String,
    val description: String,
    val flavorNote: String
)
