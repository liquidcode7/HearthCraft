package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BandMember(
    val id: String,
    val bandId: String,
    val name: String,
    val personality: String,
    val foodPreference: String,
    val quirkNote: String
)
