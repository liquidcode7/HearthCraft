package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Grimoire(
    val id: String,
    @SerialName("class") val grimoireClass: String,
    val tier: Int,
    val name: String,
    val description: String = ""
)
