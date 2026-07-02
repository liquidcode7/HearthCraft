package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GrowthCurve(
    val role: String,
    val migGrowth: Float,
    val agiGrowth: Float,
    val vitGrowth: Float,
    val wilGrowth: Float,
    val fatGrowth: Float
)
