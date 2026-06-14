package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Mission(
    val id: String,
    val bandId: String,
    val name: String,
    val description: String,
    val difficulty: String,
    val flavorLine: String,
    val requiredBuffType: String,
    val requiredBuffStrength: Int,
    val rewardMultiplier: Int,
    val durationMs: Long,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardTable: List<String>
)
