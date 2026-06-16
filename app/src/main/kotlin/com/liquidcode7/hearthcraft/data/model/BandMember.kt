package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BandMember(
    val id: String,
    val bandId: String,
    val name: String,
    val personality: String,
    val foodPreference: String,
    val quirkNote: String,
    val role: String = "",
    val startingMight: Int = 2,
    val startingAgility: Int = 2,
    val startingVitality: Int = 3,
    val startingWill: Int = 3,
    val startingFate: Int = 2
)
